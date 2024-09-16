/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2022-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.DataSetObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannedString
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.WebStorage
import android.widget.*
import androidx.annotation.Keep
import androidx.collection.LongSparseArray
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.BuildVars
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ChatThemeController
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.DocumentObject
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LanguageDetector
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesController.FaqSearchResult
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.UserObject
import org.telegram.messenger.Utilities
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.databinding.AccountsLimitReachedSheetBinding
import org.telegram.messenger.databinding.AddProfilePhotoLayoutBinding
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.LinkClickListener
import org.telegram.messenger.utils.createCombinedChatPropertiesDrawable
import org.telegram.messenger.utils.dp
import org.telegram.messenger.utils.fillElloCoinLogos
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TL_messages_exportChatInvite
import org.telegram.tgnet.TLRPC.TL_user
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_channels_channelParticipants
import org.telegram.tgnet.tlrpc.TL_chatBannedRights
import org.telegram.tgnet.tlrpc.TL_photo
import org.telegram.tgnet.tlrpc.TL_photos_photo
import org.telegram.tgnet.tlrpc.TL_photos_updateProfilePhoto
import org.telegram.tgnet.tlrpc.TL_userProfilePhotoEmpty
import org.telegram.tgnet.tlrpc.User
import org.telegram.tgnet.tlrpc.UserFull
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.AboutLinkCell
import org.telegram.ui.Cells.CheckBoxCell
import org.telegram.ui.Cells.DividerCell
import org.telegram.ui.Cells.DrawerProfileCell.AnimatedStatusView
import org.telegram.ui.Cells.GraySectionCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.NotificationsCheckCell
import org.telegram.ui.Cells.ProfileAccountCell
import org.telegram.ui.Cells.ProfileLogoutCell
import org.telegram.ui.Cells.ProfileSectionCell
import org.telegram.ui.Cells.ProfileSupportCell
import org.telegram.ui.Cells.SettingsSearchCell
import org.telegram.ui.Cells.SettingsSuggestionCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.TextDetailCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Cells.UserCell
import org.telegram.ui.ChatRightsEditActivity.ChatRightsEditActivityDelegate
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedFileDrawable
import org.telegram.ui.Components.AnimationProperties
import org.telegram.ui.Components.AudioPlayerAlert
import org.telegram.ui.Components.AutoDeletePopupWrapper
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackButtonMenu
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.BottomNavigationPanel
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ChatNotificationsPopupWrapper
import org.telegram.ui.Components.CrossfadeDrawable
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.HintView
import org.telegram.ui.Components.IdenticonDrawable
import org.telegram.ui.Components.ImageUpdater
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Premium.GiftPremiumBottomSheet
import org.telegram.ui.Components.Premium.PremiumGradient
import org.telegram.ui.Components.Premium.PremiumPreviewBottomSheet
import org.telegram.ui.Components.Premium.ProfilePremiumCell
import org.telegram.ui.Components.ProfileGalleryView
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.Reactions.VisibleReaction
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScamDrawable
import org.telegram.ui.Components.ShareAlert
import org.telegram.ui.Components.SharedMediaLayout
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.StickerEmptyView
import org.telegram.ui.Components.TimerDrawable
import org.telegram.ui.Components.TranslateAlert
import org.telegram.ui.Components.UndoView
import org.telegram.ui.Components.voip.VoIPHelper
import org.telegram.ui.DialogsActivity.DialogsActivityDelegate
import org.telegram.ui.SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow
import org.telegram.ui.aispace.AiSpaceFragment
import org.telegram.ui.group.GroupCreateActivity
import org.telegram.ui.information.InformationFragment
import org.telegram.ui.profile.referral.ReferralProgressFragment
import org.telegram.ui.profile.subscriptions.CurrentSubscriptionsFragment
import org.telegram.ui.profile.wallet.WalletFragment
import org.telegram.ui.statistics.StatisticActivity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess
import org.telegram.tgnet.TLRPC.TL_userProfilePhoto

@SuppressLint("NotifyDataSetChanged")
class ProfileActivity @JvmOverloads constructor(args: Bundle?, private var sharedMediaPreloader: SharedMediaLayout.SharedMediaPreloader? = null) : BaseFragment(args), NotificationCenterDelegate, DialogsActivityDelegate, SharedMediaLayout.SharedMediaPreloaderDelegate, ImageUpdater.ImageUpdaterDelegate, SharedMediaLayout.Delegate, EditProfileFragment.ChangeBigAvatarCallback {
	private var subscriptionExpireAt: Long = 0L
	private val visibleChatParticipants = mutableListOf<TLRPC.ChatParticipant>()
	private val visibleSortedUsers = mutableListOf<Int>()
	private val nameTextView = arrayOfNulls<SimpleTextView>(2)
	private val onlineTextView = arrayOfNulls<SimpleTextView>(2)
	private val emojiStatusDrawable = arrayOfNulls<AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable>(2)
	private var shouldOpenBot = false
	private var paidSubscriptions: List<ElloRpc.SubscriptionItem>? = null
	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	var invite: TLRPC.TL_chatInviteExported? = null

	private val scrimPaint = object : Paint(ANTI_ALIAS_FLAG) {
		override fun setAlpha(a: Int) {
			super.setAlpha(a)
			fragmentView?.invalidate()
		}
	}

	private val actionBarBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val isOnline = BooleanArray(1)
	private val positionToOffset = mutableMapOf<Int, Int>()
	private val expandAnimatorValues = floatArrayOf(0f, 1f)
	private val whitePaint = Paint()
	private val rect = Rect()
	override var isFragmentOpened = false
	private var headerShadowAlpha = 1.0f
	private var autoDeleteItemDrawable: TimerDrawable? = null
	var autoDeletePopupWrapper: AutoDeletePopupWrapper? = null
	var pinchToZoomHelper: PinchToZoomHelper? = null
	var previousTransitionFragment: ChatActivity? = null
	var profileTransitionInProgress = false
	var savedScrollPosition = -1
	var savedScrollOffset = 0
	override var listView: RecyclerListView? = null
	private var searchListView: RecyclerListView? = null
	private var layoutManager: LinearLayoutManager? = null
	private var listAdapter: ListAdapter? = null
	private var searchAdapter: SearchAdapter? = null
	private var nameTextViewRightDrawableContentDescription: String? = null
	private var mediaCounterTextView: AudioPlayerAlert.ClippingTextViewSwitcher? = null
	private var writeButton: RLottieImageView? = null
	private var writeButtonAnimation: AnimatorSet? = null
	private var qrItemAnimation: AnimatorSet? = null
	private var lockIconDrawable: Drawable? = null
	private var verifiedDrawable: Drawable? = null
	private var premiumStarDrawable: Drawable? = null
	private var verifiedCheckDrawable: Drawable? = null
	private var verifiedCrossfadeDrawable: CrossfadeDrawable? = null
	private var premiumCrossfadeDrawable: CrossfadeDrawable? = null
	private var scamDrawable: ScamDrawable? = null

	var undoView: UndoView? = null
		private set

	private var overlaysView: OverlaysView? = null
	private var sharedMediaLayout: SharedMediaLayout? = null
	private var emptyView: StickerEmptyView? = null
	private var sharedMediaLayoutAttached = false
	private var cameraDrawable: RLottieDrawable? = null
	private var cellCameraDrawable: RLottieDrawable? = null
	private var fwdRestrictedHint: HintView? = null
	private var avatarContainer: FrameLayout? = null
	private var avatarContainer2: FrameLayout? = null
	private var animatedStatusView: AnimatedStatusView? = null
	private var avatarImage: AvatarImageView? = null
	private var avatarAnimation: AnimatorSet? = null
	private var avatarProgressView: RadialProgressView? = null
	private var timeItem: ImageView? = null
	private var timerDrawable: TimerDrawable? = null
	private var avatarsViewPager: ProfileGalleryView? = null
	private var avatarsViewPagerIndicatorView: PagerIndicatorView? = null
	private var avatarDrawable: AvatarDrawable? = null
	private var imageUpdater: ImageUpdater? = null
	private var avatarColor = 0
	private var scrimView: View? = null
	private var overlayCountVisible = 0
	private var prevLoadedImageLocation: ImageLocation? = null
	private var lastMeasuredContentWidth = 0
	private var lastMeasuredContentHeight = 0
	private var listContentHeight = 0
	private var openingAvatar = false
	private var doNotSetForeground = false
	private var callItemVisible = false
	private var videoCallItemVisible = false
	private var editItemVisible = false
	private var animatingItem: ActionBarMenuItem? = null
	private var callItem: ActionBarMenuItem? = null
	private var videoCallItem: ActionBarMenuItem? = null
	private var editItem: ActionBarMenuItem? = null
	private var otherItem: ActionBarMenuItem? = null
	private var searchItem: ActionBarMenuItem? = null
	private var ttlIconView: ImageView? = null
	private var qrItem: ActionBarMenuItem? = null
	private var autoDeleteItem: ActionBarMenuSubItem? = null
	private var topView: TopView? = null

	private val headerShadow = object : AnimationProperties.FloatProperty<ProfileActivity>("headerShadow") {
		override fun setValue(`object`: ProfileActivity, value: Float) {
			headerShadowAlpha = value
			topView?.invalidate()
		}

		override operator fun get(`object`: ProfileActivity): Float {
			return headerShadowAlpha
		}
	}

	private var userId: Long = 0
	private var chatId: Long = 0

	private val provider: PhotoViewer.PhotoViewerProvider = object : PhotoViewer.EmptyPhotoViewerProvider() {
		override fun getPlaceForPhoto(messageObject: MessageObject?, fileLocation: TLRPC.FileLocation?, index: Int, needPreview: Boolean): PhotoViewer.PlaceProviderObject? {
			if (fileLocation == null) {
				return null
			}

			var photoBig: TLRPC.FileLocation? = null

			if (userId != 0L) {
				val user = messagesController.getUser(userId)

				if (user?.photo?.photo_big != null) {
					photoBig = user.photo?.photo_big
				}
			}
			else if (chatId != 0L) {
				val chat = messagesController.getChat(chatId)

				if (chat?.photo != null && chat.photo.photo_big != null) {
					photoBig = chat.photo.photo_big
				}
			}

			if (photoBig != null && photoBig.local_id == fileLocation.local_id && photoBig.volume_id == fileLocation.volume_id && photoBig.dc_id == fileLocation.dc_id) {
				val coordinates = IntArray(2)

				avatarImage?.getLocationInWindow(coordinates)

				val placeProviderObject = PhotoViewer.PlaceProviderObject()
				placeProviderObject.viewX = coordinates[0]
				placeProviderObject.viewY = coordinates[1]
				placeProviderObject.parentView = avatarImage
				placeProviderObject.imageReceiver = avatarImage?.imageReceiver

				if (userId != 0L) {
					placeProviderObject.dialogId = userId
				}
				else if (chatId != 0L) {
					placeProviderObject.dialogId = -chatId
				}

				placeProviderObject.thumb = placeProviderObject.imageReceiver.bitmapSafe
				placeProviderObject.size = -1
				placeProviderObject.radius = avatarImage?.imageReceiver?.getRoundRadius() ?: intArrayOf(0, 0, 0, 0)
				placeProviderObject.scale = avatarContainer?.scaleX ?: 1f
				placeProviderObject.canEdit = userId == userConfig.clientUserId

				return placeProviderObject
			}

			return null
		}

		override fun willHidePhotoViewer() {
			avatarImage?.imageReceiver?.setVisible(value = true, invalidate = true)
		}

		override fun openPhotoForEdit(file: String, thumb: String, isVideo: Boolean) {
			imageUpdater?.openPhotoForEdit(file, thumb, 0, isVideo)
		}
	}

	private var dialogId: Long = 0
	private var creatingChat = false
	private var userBlocked = false
	private var reportSpam = false
	private var mergeDialogId: Long = 0
	private var expandPhoto = false
	private var needSendMessage = false
	private var hasVoiceChatItem = false
	private var scrolling = false
	private var canSearchMembers = false
	private var loadingUsers = false
	private var participantsMap: LongSparseArray<TLRPC.ChatParticipant>? = LongSparseArray()
	private var usersEndReached = false
	private var banFromGroup: Long = 0
	private var openAnimationInProgress = false
	private var transitionAnimationInProgress = false
	private var recreateMenuAfterAnimation = false
	private var playProfileAnimation = 0
	private var needTimerImage = false
	private var allowProfileAnimation = true
	private var disableProfileAnimation = false
	private var extraHeight = 0f
	private var initialAnimationExtraHeight = 0f
	private var animationProgress = 0f
	private var searchTransitionOffset = 0
	private var searchTransitionProgress = 0f
	private var searchMode = false
	private var avatarX = 0f
	private var avatarY = 0f
	private var avatarScale = 0f
	private var nameX = 0f
	private var nameY = 0f
	private var onlineX = 0f
	private var onlineY = 0f
	private var expandProgress = 0f
	private var listViewVelocityY = 0f
	private var expandAnimator: ValueAnimator? = null
	private var currentExpandAnimatorValue = 0f
	private var currentExpandAnimatorFracture = 0f
	private var isInLandscapeMode = false
	private var allowPullingDown = false
	private var isPulledDown = false
	private var isBot = false
	private var chatInfo: TLRPC.ChatFull? = null
	var userInfo: UserFull? = null
	private var currentBio: CharSequence? = null
	private var selectedUser: Long = 0
	private var onlineCount = -1
	private var sortedUsers: ArrayList<Int>? = null
	private var currentEncryptedChat: TLRPC.EncryptedChat? = null
	override var currentChat: TLRPC.Chat? = null
	private var botInfo: TLRPC.BotInfo? = null
	private var currentChannelParticipant: TLRPC.ChannelParticipant? = null
	private var currentPassword: TLRPC.TL_account_password? = null
	private var avatar: TLRPC.FileLocation? = null
	private var avatarBig: TLRPC.FileLocation? = null
	private var uploadingImageLocation: ImageLocation? = null
	private var setAvatarCell: TextCell? = null
	private var rowCount = 0
	private var addAccountRow = 0
	private var setProfilePhotoRow = 0
	private var setProfilePhotoDividerRow = 0
	private val accountsRows = IntArray(UserConfig.MAX_ACCOUNT_COUNT) { -1 }
	private var shareLinkRow = 0
	private var publicLinkRow = 0
	private var publicLinkSectionRow = 0
	private var appearanceRow = 0
	private var appearanceBottomDividerRow = 0
	private var myNotificationsBottomDividerRow = 0
	private var subscriptionsBottomDividerRow = 0
	private var settingsBottomDividerRow = 0
	private var walletRow = 0
	private var myNotificationsRow = 0
	private var subscriptionsRow = 0
	private var aiChatBotRow = 0
	private var referralRow = 0
	private var foldersRow = 0
	private var purchasesRow = 0
	private var inviteRow = 0
	private var myCloudRow = 0
	private var settingsRow = 0
	private var supportRow = 0
	private var infoRow = 0
	private var supportDividerRow = 0
	private var logoutRow = 0
	private var logoutDividerRow = 0
	private var setAvatarRow = 0
	private var setAvatarSectionRow = 0
	private var setUsernameRow = 0
	private var bioRow = 0
	private var phoneSuggestionSectionRow = 0
	private var phoneSuggestionRow = 0
	private var passwordSuggestionSectionRow = 0
	private var passwordSuggestionRow = 0
	private var settingsSectionRow = 0
	private var settingsSectionRow2 = 0
	private var notificationRow = 0
	private var languageRow = 0
	private var privacyRow = 0
	private var dataRow = 0
	private var chatRow = 0
	private var filtersRow = 0
	private var stickersRow = 0
	private var devicesRow = 0
	private var devicesSectionRow = 0
	private var helpHeaderRow = 0
	private var questionRow = 0
	private var faqRow = 0
	private var policyRow = 0
	private var helpSectionCell = 0
	private var debugHeaderRow = 0
	private var sendLogsRow = 0
	private var sendLastLogsRow = 0
	private var clearLogsRow = 0
	private var versionRow = 0
	private var emptyRow = 0
	private var bottomPaddingRow = 0
	private var locationRow = 0
	private var userInfoRow = 0
	private var channelInfoRow = 0
	private var userAboutHeaderRow = 0
	private var usernameRow = 0
	private var notificationsDividerRow = 0
	private var myProfileTopDividerRow = 0
	private var myAccountsDividerRow = 0
	private var myProfileBioDividerRow = 0
	private var inviteRowDividerRow = 0
	private var notificationsRow = 0
	private var infoSectionRow = 0
	private var sendMessageRow = 0
	private var reportRow = 0
	private var reportReactionRow = 0
	private var reportDividerRow = 0
	private var addToGroupButtonRow = 0
	private var addToGroupInfoRow = 0
	private var premiumRow = 0
	private var premiumSectionsRow = 0
	private var settingsTimerRow = 0
	private var settingsKeyRow = 0
	private var secretSettingsSectionRow = 0
	private var membersHeaderRow = 0
	private var membersStartRow = 0
	private var membersEndRow = 0
	private var addMemberRow = 0
	private var subscriptionCostRow = 0
	private var subscriptionBeginRow = 0
	private var subscriptionExpireRow = 0
	private var subscriptionCostSectionRow = 0
	private var subscribersRow = 0
	private var subscribersRequestsRow = 0
	private var administratorsRow = 0
	private var blockedUsersRow = 0
	private var membersSectionRow = 0
	private var sharedMediaRow = 0
	private var unblockRow = 0
	private var joinRow = 0
	private var lastSectionRow = 0
	private var transitionIndex = 0
	private var usersForceShowingIn = 0
	private var firstLayout = true
	private var invalidateScroll = true
	private var isQrItemVisible = true
	private var transitionOnlineText: View? = null
	private var actionBarAnimationColorFrom = 0
	private var navigationBarAnimationColorFrom = 0
	private var reportReactionMessageId = 0
	private var reportReactionFromDialogId: Long = 0
	private var fragmentOpened = false
	private var contentView: NestedFrameLayout? = null
	private var selectAnimatedEmojiDialog: SelectAnimatedEmojiDialogWindow? = null
	private var headerAnimatorSet: AnimatorSet? = null
	private var headerShadowAnimatorSet: AnimatorSet? = null
	private var mediaHeaderAnimationProgress = 0f
	private var mediaHeaderVisible = false
	private var aboutLinkCell: AboutLinkCell? = null
	private var lastEmojiStatusProgress = 0f
	private var invitesCount = 0
	private var inviteLink = ""

	private val actionBarHeaderProgress = object : AnimationProperties.FloatProperty<ActionBar>("animationProgress") {
		override fun setValue(`object`: ActionBar, value: Float) {
			mediaHeaderAnimationProgress = value
			topView?.invalidate()

			val context = context ?: return

			var color1 = ResourcesCompat.getColor(context.resources, R.color.text, null)
			var color2 = ResourcesCompat.getColor(context.resources, R.color.undead_dark, null)
			val c = AndroidUtilities.getOffsetColor(color1, color2, value, 1.0f)
			nameTextView[1]?.textColor = c

			lockIconDrawable?.colorFilter = PorterDuffColorFilter(c, PorterDuff.Mode.MULTIPLY)

			color1 = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
			scamDrawable?.setColor(AndroidUtilities.getOffsetColor(color1, color2, value, 1.0f))

			color1 = ResourcesCompat.getColor(context.resources, R.color.text, null)
			color2 = ResourcesCompat.getColor(context.resources, R.color.action_bar_item, null)

			val offsetColor = AndroidUtilities.getOffsetColor(color1, color2, value, 1.0f)

			actionBar?.setItemsColor(offsetColor, false)

			color1 = ResourcesCompat.getColor(context.resources, R.color.action_bar_item, null)
			color2 = ResourcesCompat.getColor(context.resources, R.color.light_gray, null)

			actionBar?.setItemsBackgroundColor(AndroidUtilities.getOffsetColor(color1, color2, value, 1.0f), false)

			topView?.invalidate()

			otherItem?.setIconColor(offsetColor)
			callItem?.setIconColor(offsetColor)
			videoCallItem?.setIconColor(offsetColor)
			editItem?.setIconColor(offsetColor)

			color1 = ResourcesCompat.getColor(context.resources, R.color.brand, null)
			color2 = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
			verifiedDrawable?.colorFilter = PorterDuffColorFilter(AndroidUtilities.getOffsetColor(color1, color2, value, 1.0f), PorterDuff.Mode.MULTIPLY)

			color1 = ResourcesCompat.getColor(context.resources, R.color.brand, null)
			color2 = ResourcesCompat.getColor(context.resources, R.color.background, null)
			verifiedCheckDrawable?.colorFilter = PorterDuffColorFilter(AndroidUtilities.getOffsetColor(color1, color2, value, 1.0f), PorterDuff.Mode.MULTIPLY)

			color1 = ResourcesCompat.getColor(context.resources, R.color.brand, null)
			color2 = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
			premiumStarDrawable?.colorFilter = PorterDuffColorFilter(AndroidUtilities.getOffsetColor(color1, color2, value, 1.0f), PorterDuff.Mode.MULTIPLY)

			updateEmojiStatusDrawableColor()

			if (avatarsViewPagerIndicatorView?.secondaryMenuItem != null && (videoCallItemVisible || editItemVisible || callItemVisible)) {
				needLayoutText(min(1f, extraHeight / AndroidUtilities.dp(88f)))
			}
		}

		override operator fun get(`object`: ActionBar): Float {
			return mediaHeaderAnimationProgress
		}
	}

	private var scrimAnimatorSet: AnimatorSet? = null

	override fun shouldShowBottomNavigationPanel(): Boolean {
		if (userId == 0L) {
			return false
		}

		return isSelf()
	}

	private fun isSelf(): Boolean {
		if (chatId != 0L || userId == 0L) {
			return false
		}

		val user = messagesController.getUser(userId)
		return UserObject.isUserSelf(user) || user?.id == getInstance(currentAccount).getClientUserId()
	}

	override fun onFragmentCreate(): Boolean {
		userId = arguments?.getLong("user_id", 0) ?: 0L
		chatId = arguments?.getLong("chat_id", 0) ?: 0L
		banFromGroup = arguments?.getLong("ban_chat_id", 0) ?: 0L
		reportReactionMessageId = arguments?.getInt("report_reaction_message_id", 0) ?: 0
		reportReactionFromDialogId = arguments?.getLong("report_reaction_from_dialog_id", 0) ?: 0

		reportSpam = arguments?.getBoolean("reportSpam", false) ?: false

		if (!expandPhoto) {
			expandPhoto = arguments?.getBoolean("expandPhoto", false) ?: false

			if (expandPhoto) {
				needSendMessage = true
			}
		}

		if (userId != 0L) {
			dialogId = arguments?.getLong("dialog_id", 0) ?: 0L

			if (dialogId != 0L) {
				currentEncryptedChat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))
			}

			val user = messagesController.getUser(userId) ?: return false

			notificationCenter.let {
				it.addObserver(this, NotificationCenter.contactsDidLoad)
				it.addObserver(this, NotificationCenter.newSuggestionsAvailable)
				it.addObserver(this, NotificationCenter.encryptedChatCreated)
				it.addObserver(this, NotificationCenter.encryptedChatUpdated)
				it.addObserver(this, NotificationCenter.blockedUsersDidLoad)
				it.addObserver(this, NotificationCenter.botInfoDidLoad)
				it.addObserver(this, NotificationCenter.userInfoDidLoad)
				it.addObserver(this, NotificationCenter.privacyRulesUpdated)
				it.addObserver(this, NotificationCenter.updateUnreadBadge)
			}

			NotificationCenter.globalInstance.let {
				it.addObserver(this, NotificationCenter.reloadInterface)
				it.addObserver(this, NotificationCenter.updateUnreadBadge)
			}

			userBlocked = messagesController.blockedPeers.indexOfKey(userId) >= 0

			if (user.bot) {
				isBot = true
				mediaDataController.loadBotInfo(user.id, user.id, true, classGuid)
			}

			userInfo = messagesController.getUserFull(userId)

			ioScope.launch {
				messagesController.loadUser(userId, classGuid, true) ?: return@launch

				mainScope.launch {
					updateProfileData(true)
				}
			}

			// messagesController.loadFullUser(messagesController.getUser(userId), classGuid, true)

			participantsMap = null

			if (isSelf()) {
				imageUpdater = ImageUpdater(false)
				imageUpdater?.setOpenWithFrontfaceCamera(true)
				imageUpdater?.parentFragment = this
				imageUpdater?.setDelegate(this)

				mediaDataController.checkFeaturedStickers()

				messagesController.loadSuggestedFilters()
				messagesController.loadUserInfo(userConfig.getCurrentUser(), true, classGuid)
			}

			actionBarAnimationColorFrom = arguments?.getInt("actionBarColor", 0) ?: 0
			loadInviteLinks()
		}
		else if (chatId != 0L) {
			currentChat = messagesController.getChat(chatId)

			if (currentChat == null) {
				val countDownLatch = CountDownLatch(1)

				messagesStorage.storageQueue.postRunnable {
					currentChat = messagesStorage.getChat(chatId)
					countDownLatch.countDown()
				}

				try {
					countDownLatch.await()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				if (currentChat != null) {
					messagesController.putChat(currentChat, true)
				}
				else {
					return false
				}
			}

			if (currentChat?.megagroup == true) {
				getChannelParticipants(true)
			}
			else {
				participantsMap = null
			}

			notificationCenter.addObserver(this, NotificationCenter.chatInfoDidLoad)
			notificationCenter.addObserver(this, NotificationCenter.chatOnlineCountDidLoad)
			notificationCenter.addObserver(this, NotificationCenter.groupCallUpdated)

			sortedUsers = ArrayList()

			updateOnlineCount(true)

			if (chatInfo == null) {
				chatInfo = messagesController.getChatFull(chatId)
			}

			if (ChatObject.isChannel(currentChat)) {
				messagesController.loadFullChat(chatId, classGuid, true)
			}
			else if (chatInfo == null) {
				chatInfo = messagesStorage.loadChatInfo(chatId, false, null, false, false)
			}

			if (ChatObject.isSubscriptionChannel(currentChat) && currentChat?.creator == false) {
				val request = ElloRpc.getSubscriptionsRequest(ElloRpc.SubscriptionType.ACTIVE_CHANNELS)

				connectionsManager.sendRequest(request) { response, _ ->
					if (response is TLRPC.TL_biz_dataRaw) {
						val subscriptions = response.readData<ElloRpc.Subscriptions>()
						val currentSubscription = subscriptions?.items?.find { it.channelId == chatId }

						if (currentSubscription != null) {
							subscriptionExpireAt = currentSubscription.expireAt

							AndroidUtilities.runOnUIThread {
								listAdapter?.notifyItemChanged(subscriptionExpireRow)
							}
						}
					}
				}
			}
		}
		else {
			return false
		}

		if (sharedMediaPreloader == null) {
			sharedMediaPreloader = SharedMediaLayout.SharedMediaPreloader(this)
		}

		sharedMediaPreloader?.addDelegate(this)

		notificationCenter.addObserver(this, NotificationCenter.updateInterfaces)
		notificationCenter.addObserver(this, NotificationCenter.didReceiveNewMessages)
		notificationCenter.addObserver(this, NotificationCenter.closeChats)

		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.emojiLoaded)

		updateRowsIds()

		listAdapter?.notifyDataSetChanged()

		if (arguments?.containsKey("preload_messages") == true) {
			messagesController.ensureMessagesLoaded(userId, 0, null)
		}

		if (userId != 0L) {
			if (isSelf()) {
				val req = TLRPC.TL_account_getPassword()

				connectionsManager.sendRequest(req) { response, _ ->
					if (response is TLRPC.TL_account_password) {
						currentPassword = response
					}
				}
			}
		}

		return true
	}

	private fun loadInviteLinks() {
		val req = TLRPC.TL_messages_getExportedChatInvites()
		req.peer = messagesController.getInputPeer(userId)
		req.admin_id = messagesController.getInputUser(userConfig.clientUserId)

		connectionsManager.sendRequest(req) { resp, err ->
			if (err == null) {
				val invites: TLRPC.TL_messages_exportedChatInvites = resp as TLRPC.TL_messages_exportedChatInvites

				invitesCount = invites.count

				if (invites.count > 0) {
					val chatInvite: TLRPC.ExportedChatInvite? = invites.invites?.get(0)

					if (chatInvite != null) {
						inviteLink = (chatInvite as TLRPC.TL_chatInviteExported).link

						AndroidUtilities.runOnUIThread {
							listAdapter?.notifyItemChanged(shareLinkRow)
						}
					}
				}
				else {
					@Suppress("NAME_SHADOWING") val req = TL_messages_exportChatInvite()
					req.legacy_revoke_permanent = true
					req.peer = MessagesController.getInstance(currentAccount).getInputPeer(userId)

					connectionsManager.sendRequest(req) { response, error ->
						if (error == null && response != null) {
							invite = response as TLRPC.TL_chatInviteExported
							inviteLink = invite?.link ?: ""

							AndroidUtilities.runOnUIThread {
								listAdapter?.let {
									it.notifyItemChanged(shareLinkRow)
									it.notifyItemChanged(inviteRow)
								}
							}
						}
					}
				}

				AndroidUtilities.runOnUIThread {
					listAdapter?.notifyItemChanged(inviteRow)
				}
			}
		}
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}

		shouldOpenBot = false

		Bulletin.removeDelegate(this)

		sharedMediaLayout?.onDestroy()
		sharedMediaPreloader?.onDestroy(this)
		sharedMediaPreloader?.removeDelegate(this)

		notificationCenter.let {
			it.removeObserver(this, NotificationCenter.updateInterfaces)
			it.removeObserver(this, NotificationCenter.closeChats)
			it.removeObserver(this, NotificationCenter.didReceiveNewMessages)
		}

		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.emojiLoaded)

		avatarsViewPager?.onDestroy()

		if (userId != 0L) {
			notificationCenter.let {
				it.removeObserver(this, NotificationCenter.newSuggestionsAvailable)
				it.removeObserver(this, NotificationCenter.contactsDidLoad)
				it.removeObserver(this, NotificationCenter.encryptedChatCreated)
				it.removeObserver(this, NotificationCenter.encryptedChatUpdated)
				it.removeObserver(this, NotificationCenter.blockedUsersDidLoad)
				it.removeObserver(this, NotificationCenter.botInfoDidLoad)
				it.removeObserver(this, NotificationCenter.userInfoDidLoad)
				it.removeObserver(this, NotificationCenter.privacyRulesUpdated)
				it.removeObserver(this, NotificationCenter.updateUnreadBadge)
			}

			NotificationCenter.globalInstance.let {
				it.removeObserver(this, NotificationCenter.reloadInterface)
				it.removeObserver(this, NotificationCenter.updateUnreadBadge)
			}

			messagesController.cancelLoadFullUser(userId)
		}
		else if (chatId != 0L) {
			notificationCenter.let {
				it.removeObserver(this, NotificationCenter.chatInfoDidLoad)
				it.removeObserver(this, NotificationCenter.chatOnlineCountDidLoad)
				it.removeObserver(this, NotificationCenter.groupCallUpdated)
			}
		}

		avatarImage?.setImageDrawable(null)
		imageUpdater?.clear()
		pinchToZoomHelper?.clear()

		for (a in 0..1) {
			emojiStatusDrawable[a]?.detach()
		}
	}

	override fun createActionBar(context: Context): ActionBar {
		val actionBar = object : ActionBar(context) {
			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(event: MotionEvent): Boolean {
				avatarContainer?.getHitRect(rect)

				return if (rect.contains(event.x.toInt(), event.y.toInt())) {
					false
				}
				else {
					super.onTouchEvent(event)
				}
			}

			override fun setItemsColor(color: Int, isActionMode: Boolean) {
				super.setItemsColor(color, isActionMode)

				if (!isActionMode) {
					ttlIconView?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
				}
			}
		}

		actionBar.setForceSkipTouches(true)
		actionBar.setBackgroundColor(Color.TRANSPARENT)
		actionBar.setItemsBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.action_bar_item, null), false)
		actionBar.setItemsColor(ResourcesCompat.getColor(context.resources, R.color.brand, null), false)

		var shouldShowBackButton = true

		if (userId != 0L) {
			shouldShowBackButton = !isSelf()
		}

		if (shouldShowBackButton) {
			actionBar.backButtonDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_back_arrow, null)
		}
		else {
			actionBar.shouldDestroyBackButtonOnCollapse = true
		}

		actionBar.castShadows = false
		actionBar.setAddToContainer(false)
		actionBar.setClipContent(true)
		actionBar.occupyStatusBar = !AndroidUtilities.isTablet() && !isInBubbleMode

		val backButton = actionBar.backButton

		backButton?.setOnLongClickListener {
			val menu = BackButtonMenu.show(this, backButton, getDialogId())

			if (menu != null) {
				menu.setOnDismissListener {
					dimBehindView(false)
				}

				dimBehindView(backButton, 0.3f)
				undoView?.hide(true, 1)
				return@setOnLongClickListener true
			}
			else {
				return@setOnLongClickListener false
			}
		}

		return actionBar
	}

	override fun createView(context: Context): View? {
		searchTransitionOffset = 0
		searchTransitionProgress = 1f
		searchMode = false
		hasOwnBackground = true
		extraHeight = AndroidUtilities.dp(88f).toFloat()

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				val parentActivity = parentActivity ?: return

				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}

					block_contact -> {
						val user = messagesController.getUser(userId) ?: return

						if (!isBot || MessagesController.isSupportUser(user)) {
							if (userBlocked) {
								messagesController.unblockPeer(userId)

								if (BulletinFactory.canShowBulletin(this@ProfileActivity)) {
									BulletinFactory.createBanBulletin(this@ProfileActivity, false).show()
								}
							}
							else {
								if (reportSpam) {
									AlertsCreator.showBlockReportSpamAlert(this@ProfileActivity, userId, user, null, currentEncryptedChat, false, null) { param ->
										if (param == 1) {
											notificationCenter.removeObserver(this@ProfileActivity, NotificationCenter.closeChats)
											notificationCenter.postNotificationName(NotificationCenter.closeChats)
											playProfileAnimation = 0
											finishFragment()
										}
										else {
											notificationCenter.postNotificationName(NotificationCenter.peerSettingsDidLoad, userId)
										}
									}
								}
								else {
									val builder = AlertDialog.Builder(parentActivity)
									builder.setTitle(parentActivity.getString(R.string.BlockUser))
									builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureBlockContact2", R.string.AreYouSureBlockContact2, ContactsController.formatName(user.first_name, user.last_name))))

									builder.setPositiveButton(parentActivity.getString(R.string.BlockContact)) { _, _ ->
										messagesController.blockPeer(userId)
										if (BulletinFactory.canShowBulletin(this@ProfileActivity)) {
											BulletinFactory.createBanBulletin(this@ProfileActivity, true).show()
										}
									}

									builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

									val dialog = builder.create()

									showDialog(dialog)

									val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
									button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
								}
							}
						}
						else {
							if (!userBlocked) {
								messagesController.blockPeer(userId)
							}
							else {
								messagesController.unblockPeer(userId)
								sendMessagesHelper.sendMessage("/start", userId, null, null, null, false, null, null, null, true, 0, null, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)
								finishFragment()
							}
						}
					}

					add_contact -> {
						val user = messagesController.getUser(userId)

						if (user != null) {
							val args = Bundle()
							args.putLong("user_id", user.id)
							args.putBoolean("addContact", true)
							presentFragment(ContactAddActivity(args))
						}
					}

					share_contact -> {
						val args = Bundle()
						args.putBoolean("onlySelect", true)
						args.putInt("dialogsType", 3)
						args.putString("selectAlertString", context.getString(R.string.SendContactToText))
						args.putString("selectAlertStringGroup", context.getString(R.string.SendContactToGroupText))
						val fragment = DialogsActivity(args)
						fragment.setDelegate(this@ProfileActivity)
						presentFragment(fragment)
					}

					edit_contact -> {
						val args = Bundle()
						args.putLong("user_id", userId)
						presentFragment(ContactAddActivity(args))
					}

					delete_contact -> {
						val user = messagesController.getUser(userId) ?: return

						val builder = AlertDialog.Builder(parentActivity)
						builder.setTitle(parentActivity.getString(R.string.DeleteContact))
						builder.setMessage(parentActivity.getString(R.string.AreYouSureDeleteContact))

						builder.setPositiveButton(parentActivity.getString(R.string.Delete)) { _, _ ->
							val arrayList = ArrayList<User>()
							arrayList.add(user)
							contactsController.deleteContact(arrayList, true)
						}

						builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

						val dialog = builder.create()

						showDialog(dialog)

						val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
						button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
					}

					leave_group -> {
						leaveChatPressed()
					}

					edit_channel -> {
						val self = isSelf()

						if (self) {
							val user = userInfo?.user ?: messagesController.getUser(userInfo?.id)
							val args = Bundle()
							args.putLong("user_id", userId)
							args.putString("username", user?.username ?: "")
							val fragment = EditProfileFragment(args)
							fragment.setChangeBigAvatarCallback(this@ProfileActivity)
							presentFragment(fragment)
						}
						else {
							val args = Bundle()
							args.putLong("chat_id", chatId)
							val fragment = ChatEditActivity(args)
							fragment.setChangeBigAvatarCallback(this@ProfileActivity)
							fragment.setInfo(chatInfo)
							presentFragment(fragment)
						}
					}

					invite_to_group -> {
						val user = messagesController.getUser(userId) ?: return
						val args = Bundle()
						args.putBoolean("onlySelect", true)
						args.putInt("dialogsType", 2)
						args.putBoolean("resetDelegate", false)
						args.putBoolean("closeFragment", false)

						val fragment = DialogsActivity(args)

						fragment.setDelegate { fragment1, dids, _, _ ->
							val did = dids[0]
							val chat = MessagesController.getInstance(currentAccount).getChat(-did)

							if (chat != null && (chat.creator || chat.admin_rights != null && chat.admin_rights.add_admins)) {
								messagesController.checkIsInChat(chat, user) { isInChatAlready, rightsAdmin, currentRank ->
									AndroidUtilities.runOnUIThread {
										val editRightsActivity = ChatRightsEditActivity(userId, -did, rightsAdmin, null, null, currentRank, ChatRightsEditActivity.TYPE_ADD_BOT, true, !isInChatAlready, null)

										editRightsActivity.setDelegate(object : ChatRightsEditActivityDelegate {
											override fun didSetRights(rights: Int, rightsAdmin: TLRPC.TL_chatAdminRights?, rightsBanned: TL_chatBannedRights?, rank: String?) {
												disableProfileAnimation = true
												fragment.removeSelfFromStack()
												notificationCenter.removeObserver(this@ProfileActivity, NotificationCenter.closeChats)
												notificationCenter.postNotificationName(NotificationCenter.closeChats)
											}

											override fun didChangeOwner(user: User) {
												// unused
											}
										})

										presentFragment(editRightsActivity)
									}
								}
							}
							else {
								val builder = AlertDialog.Builder(parentActivity)
								builder.setTitle(parentActivity.getString(R.string.AddBot))

								val chatName = if (chat == null) "" else chat.title
								builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("AddMembersAlertNamesText", R.string.AddMembersAlertNamesText, UserObject.getUserName(user), chatName)))
								builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

								builder.setPositiveButton(parentActivity.getString(R.string.AddBot)) { _, _ ->
									disableProfileAnimation = true

									val args1 = Bundle()
									args1.putBoolean("scrollToTopOnResume", true)
									args1.putLong("chat_id", -did)

									if (!messagesController.checkCanOpenChat(args1, fragment1)) {
										return@setPositiveButton
									}

									val chatActivity = ChatActivity(args1)

									notificationCenter.removeObserver(this@ProfileActivity, NotificationCenter.closeChats)
									notificationCenter.postNotificationName(NotificationCenter.closeChats)
									messagesController.addUserToChat(-did, user, 0, null, chatActivity, true, null, null)

									presentFragment(chatActivity, true)
								}
								showDialog(builder.create())
							}
						}
						presentFragment(fragment)
					}

					share -> {
						try {
							var text: String? = null

							if (userId != 0L) {
								val user = messagesController.getUser(userId) ?: return

								text = String.format("https://" + messagesController.linkPrefix + "/%s", user.username)

								//MARK: When sharing a user or bot, the message is sent along with a description, if you need to in the future, uncomment this code
//								text = if (botInfo != null && userInfo != null && !TextUtils.isEmpty(userInfo!!.about)) {
//									String.format("%s https://" + messagesController.linkPrefix + "/%s", userInfo!!.about, user.username)
//								}
//								else {
//									String.format("https://" + messagesController.linkPrefix + "/%s", user.username)
//								}
							}
							else if (chatId != 0L) {
								val chat = messagesController.getChat(chatId) ?: return

								text = String.format("https://" + messagesController.linkPrefix + "/%s", chat.username)

								//MARK: When sharing a channel or group, the message is sent along with a description, if you need to in the future, uncomment this code
//								text = if (chatInfo != null && !TextUtils.isEmpty(chatInfo!!.about)) {
//									String.format("%s\nhttps://" + messagesController.linkPrefix + "/%s", chatInfo!!.about, chat.username)
//								}
//								else {
//									String.format("https://" + messagesController.linkPrefix + "/%s", chat.username)
//								}
							}

							if (TextUtils.isEmpty(text)) {
								return
							}

							val intent = Intent(Intent.ACTION_SEND)
							intent.type = "text/plain"
							intent.putExtra(Intent.EXTRA_TEXT, text)

							startActivityForResult(Intent.createChooser(intent, parentActivity.getString(R.string.BotShare)), 500)
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					add_shortcut -> {
						try {
							val did = if (currentEncryptedChat != null) {
								DialogObject.makeEncryptedDialogId(currentEncryptedChat!!.id.toLong())
							}
							else if (userId != 0L) {
								userId
							}
							else if (chatId != 0L) {
								-chatId
							}
							else {
								return
							}

							mediaDataController.installShortcut(did)
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					call_item, video_call_item -> {
						if (userId != 0L) {
							val user = messagesController.getUser(userId)

							if (user != null) {
								VoIPHelper.startCall(user, id == video_call_item, userInfo != null && userInfo!!.video_calls_available, parentActivity, userInfo, accountInstance)
							}
						}
						else if (chatId != 0L) {
							val call = messagesController.getGroupCall(chatId, false)

							if (call == null) {
								VoIPHelper.showGroupCallAlert(this@ProfileActivity, currentChat!!, accountInstance)
							}
							else {
								VoIPHelper.startCall(currentChat!!, null, false, parentActivity, this@ProfileActivity, accountInstance)
							}
						}
					}

					search_members -> {
						val args = Bundle()
						args.putLong("chat_id", chatId)
						args.putInt("type", ChatUsersActivity.TYPE_USERS)
						args.putBoolean("open_search", true)

						val fragment = ChatUsersActivity(args)
						fragment.setInfo(chatInfo)

						presentFragment(fragment)
					}

					add_member -> {
						openAddMember()
					}

					statistics -> {
						val chat = messagesController.getChat(chatId)
						val args = Bundle()
						args.putLong("chat_id", chatId)
						args.putBoolean("is_megagroup", chat?.megagroup == true)

						val fragment = StatisticActivity(args)

						presentFragment(fragment)
					}

					view_discussion -> {
						openDiscussion()
					}

					gift_premium -> {
						showDialog(GiftPremiumBottomSheet(this@ProfileActivity, messagesController.getUser(userId)))
					}

					start_secret_chat -> {
						val builder = AlertDialog.Builder(parentActivity)
						builder.setTitle(parentActivity.getString(R.string.AreYouSureSecretChatTitle))
						builder.setMessage(parentActivity.getString(R.string.AreYouSureSecretChat))

						builder.setPositiveButton(parentActivity.getString(R.string.Start)) { _, _ ->
							creatingChat = true
							secretChatHelper.startSecretChat(parentActivity, messagesController.getUser(userId))
						}

						builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

						showDialog(builder.create())
					}

					gallery_menu_save -> {
						if ((Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && parentActivity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
							parentActivity.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE)
							return
						}

						val location = avatarsViewPager?.getImageLocation(avatarsViewPager!!.realPosition) ?: return
						val isVideo = location.imageType == FileLoader.IMAGE_TYPE_ANIMATION
						val f = FileLoader.getInstance(currentAccount).getPathToAttach(location.location, if (isVideo) "mp4" else null, true)

						if (f.exists()) {
							MediaController.saveFile(f.toString(), parentActivity, 0, null, null) {
								Bulletin.addDelegate(this@ProfileActivity, object : Bulletin.Delegate {
									override fun onHide(bulletin: Bulletin) {
										Bulletin.removeDelegate(this@ProfileActivity)
									}

									override fun getBottomOffset(tag: Int): Int {
										val self = isSelf()

										return if (self) {
											AndroidUtilities.dp(BottomNavigationPanel.height.toFloat())
										}
										else {
											0
										}
									}
								})

								BulletinFactory.createSaveToGalleryBulletin(this@ProfileActivity, isVideo).show()
							}
						}
					}

					edit_name -> {
						presentFragment(ChangeNameActivity())
					}

					logout -> {
						androidx.appcompat.app.AlertDialog.Builder(context).setTitle(R.string.logout).setMessage(R.string.logout_confirm).setPositiveButton(R.string.log_out) { _, _ ->
							MessagesController.getInstance(currentAccount).performLogout(1)
						}.setNegativeButton(R.string.cancel) { _, _ ->
							// just dismiss the dialog
						}.show()
					}

					set_as_main -> {
						val position = avatarsViewPager?.realPosition ?: return
						val photo = avatarsViewPager?.getPhoto(position) ?: return

						avatarsViewPager?.startMovePhotoToBegin(position)

						val req = TL_photos_updateProfilePhoto()
						req.id = TLRPC.TL_inputPhoto()
						req.id?.id = photo.id
						req.id?.access_hash = photo.access_hash
						req.id?.file_reference = photo.file_reference

						val userConfig = userConfig

						connectionsManager.sendRequest(req) { response, _ ->
							AndroidUtilities.runOnUIThread {
								avatarsViewPager?.finishSettingMainPhoto()

								if (response is TL_photos_photo) {
									messagesController.putUsers(response.users, false)

									val user = messagesController.getUser(userConfig.clientUserId)

									if (response.photo is TL_photo) {
										avatarsViewPager?.replaceFirstPhoto(photo, response.photo)

										if (user != null) {
											user.photo?.photo_id = response.photo?.id
											userConfig.setCurrentUser(user)
											userConfig.saveConfig(true)
										}
									}
								}
							}
						}

						undoView?.showWithAction(userId, UndoView.ACTION_PROFILE_PHOTO_CHANGED, if (photo.video_sizes.isEmpty()) null else 1)

						val user = messagesController.getUser(userConfig.clientUserId)
						val bigSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 800)

						if (user != null) {
							val smallSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 90)

							user.photo?.photo_id = photo.id
							user.photo?.photo_small = smallSize?.location
							user.photo?.photo_big = bigSize?.location

							userConfig.setCurrentUser(user)
							userConfig.saveConfig(true)

							NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged)

							updateProfileData(true)
						}

						avatarsViewPager?.commitMoveToBegin()
					}

					edit_avatar -> {
						val position = avatarsViewPager?.realPosition ?: return
						val location = avatarsViewPager?.getImageLocation(position) ?: return
						val f = FileLoader.getInstance(currentAccount).getPathToAttach(PhotoViewer.getFileLocation(location), PhotoViewer.getFileLocationExt(location), true)
						val isVideo = location.imageType == FileLoader.IMAGE_TYPE_ANIMATION

						val thumb = if (isVideo) {
							val imageLocation = avatarsViewPager!!.getRealImageLocation(position)
							FileLoader.getInstance(currentAccount).getPathToAttach(PhotoViewer.getFileLocation(imageLocation), PhotoViewer.getFileLocationExt(imageLocation), true).absolutePath
						}
						else {
							null
						}

						imageUpdater?.openPhotoForEdit(f.absolutePath, thumb, 0, isVideo)
					}

					delete_avatar -> {
						val builder = AlertDialog.Builder(parentActivity)
						val location = avatarsViewPager?.getImageLocation(avatarsViewPager?.realPosition) ?: return

						if (location.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
							builder.setTitle(context.getString(R.string.AreYouSureDeleteVideoTitle))
							builder.setMessage(LocaleController.formatString("AreYouSureDeleteVideo", R.string.AreYouSureDeleteVideo))
						}
						else {
							builder.setTitle(context.getString(R.string.AreYouSureDeletePhotoTitle))
							builder.setMessage(LocaleController.formatString("AreYouSureDeletePhoto", R.string.AreYouSureDeletePhoto))
						}

						builder.setPositiveButton(context.getString(R.string.Delete)) { _, _ ->
							val avatarsViewPager = avatarsViewPager ?: return@setPositiveButton
							val position = avatarsViewPager.realPosition
							val photo = avatarsViewPager.getPhoto(position)

							if (avatarsViewPager.realCount == 1) {
								setForegroundImage(true)
							}

							if (photo == null || position == 0) {
								val nextPhoto = avatarsViewPager.getPhoto(1)

								if (nextPhoto != null) {
									userConfig.getCurrentUser()?.photo = TL_userProfilePhoto()

									val smallSize = FileLoader.getClosestPhotoSizeWithSize(nextPhoto.sizes, 90)
									val bigSize = FileLoader.getClosestPhotoSizeWithSize(nextPhoto.sizes, 1000)

									if (smallSize != null && bigSize != null) {
										userConfig.getCurrentUser()?.photo?.photo_small = smallSize.location
										userConfig.getCurrentUser()?.photo?.photo_big = bigSize.location
									}
								}
								else {
									userConfig.getCurrentUser()?.photo = TL_userProfilePhotoEmpty()
								}

								if (position == 0 && photo != null && nextPhoto == null) {
									val inputPhoto = TLRPC.TL_inputPhoto()
									inputPhoto.id = photo.id
									inputPhoto.access_hash = photo.access_hash
									inputPhoto.file_reference = photo.file_reference ?: ByteArray(0)

									messagesController.deleteUserPhoto(inputPhoto, isLastPhoto = avatarsViewPager.realCount == 1)
								}
								else {
									messagesController.deleteUserPhoto(null, isLastPhoto = avatarsViewPager.realCount == 1)
								}
							}
							else {
								val inputPhoto = TLRPC.TL_inputPhoto()
								inputPhoto.id = photo.id
								inputPhoto.access_hash = photo.access_hash
								inputPhoto.file_reference = photo.file_reference ?: ByteArray(0)

								messagesController.deleteUserPhoto(inputPhoto)

								messagesStorage.clearUserPhoto(userId, photo.id)
							}

							if (avatarsViewPager.removePhotoAtIndex(position)) {
								avatarsViewPager.visibility = View.GONE
								avatarImage?.setForegroundAlpha(1f)
								avatarContainer?.visibility = View.VISIBLE
								doNotSetForeground = true

								val view = layoutManager?.findViewByPosition(0)

								if (view != null) {
									listView?.smoothScrollBy(0, view.top - AndroidUtilities.dp(88f), CubicBezierInterpolator.EASE_OUT_QUINT)
								}
							}
						}

						builder.setNegativeButton(context.getString(R.string.Cancel), null)

						val alertDialog = builder.create()

						showDialog(alertDialog)

						val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
						button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
					}

					add_photo -> {
						onWriteButtonClick()
					}

					qr_button -> {
						qrItem?.let {
							if (it.alpha > 0) {
								openQrFragment()
							}
						}
					}

					clear_history -> {
						val builder = AlertDialog.Builder(parentActivity)
						builder.setTitle(parentActivity.getString(R.string.ClearHistory))
						builder.setMessage(parentActivity.getString(R.string.AreYouSureClearHistory))

						builder.setPositiveButton(parentActivity.getString(R.string.ClearHistory)) { _, _ ->
							messagesController.deleteDialog(dialogId, 0, false)
							finishFragment()
						}

						builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

						val dialog = builder.create()

						showDialog(dialog)

						val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
						button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
					}
				}
			}
		})

		sharedMediaLayout?.onDestroy()

		val did = if (dialogId != 0L) {
			dialogId
		}
		else if (userId != 0L) {
			userId
		}
		else {
			-chatId
		}

		fragmentView = object : NestedFrameLayout(context) {
			private val sortedChildren = ArrayList<View>()
			private val viewComparator = Comparator { view: View, view2: View -> (view.y - view2.y).toInt() }
			private val grayPaint = Paint()
			private var ignoreLayout = false
			private var wasPortrait = false

			override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
				if (pinchToZoomHelper?.isInOverlayMode == true) {
					return pinchToZoomHelper?.onTouchEvent(ev) ?: false
				}

				if (sharedMediaLayout != null && sharedMediaLayout!!.isInFastScroll && sharedMediaLayout!!.isPinnedToTop) {
					return sharedMediaLayout!!.dispatchFastScrollEvent(ev)
				}

				return if (sharedMediaLayout != null && sharedMediaLayout!!.checkPinchToZoom(ev)) {
					true
				}
				else {
					super.dispatchTouchEvent(ev)
				}
			}

			override fun hasOverlappingRendering(): Boolean {
				return false
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val actionBarHeight = ActionBar.getCurrentActionBarHeight() + if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0

				if (listView != null) {
					val layoutParams = listView?.layoutParams as? LayoutParams

					if (layoutParams?.topMargin != actionBarHeight) {
						layoutParams?.topMargin = actionBarHeight
					}
				}

				if (searchListView != null) {
					val layoutParams = searchListView?.layoutParams as? LayoutParams

					if (layoutParams?.topMargin != actionBarHeight) {
						layoutParams?.topMargin = actionBarHeight
					}
				}

				val height = MeasureSpec.getSize(heightMeasureSpec)

				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))

				var changed = false

				if (lastMeasuredContentWidth != measuredWidth || lastMeasuredContentHeight != measuredHeight) {
					changed = lastMeasuredContentWidth != 0 && lastMeasuredContentWidth != measuredWidth
					listContentHeight = 0

					val count = listAdapter?.itemCount ?: 0

					lastMeasuredContentWidth = measuredWidth
					lastMeasuredContentHeight = measuredHeight
					val ws = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
					val hs = MeasureSpec.makeMeasureSpec(listView!!.measuredHeight, MeasureSpec.UNSPECIFIED)

					positionToOffset.clear()

					for (i in 0 until count) {
						val type = listAdapter!!.getItemViewType(i)
						positionToOffset[i] = listContentHeight

						listContentHeight += if (type == VIEW_TYPE_SHARED_MEDIA) {
							listView?.measuredHeight ?: 0
						}
						else {
							val holder = listAdapter?.createViewHolder(listView!!, type)?.also {
								listAdapter?.onBindViewHolder(it, i)
								it.itemView.measure(ws, hs)
							}

							holder?.itemView?.measuredHeight ?: 0
						}
					}

					(emptyView?.layoutParams as? LayoutParams)?.topMargin = AndroidUtilities.dp(88f) + AndroidUtilities.statusBarHeight
				}

				if (!fragmentOpened && (expandPhoto || openAnimationInProgress && playProfileAnimation == 2)) {
					ignoreLayout = true

					if (expandPhoto) {
						searchItem?.alpha = 0.0f
						searchItem?.isEnabled = false
						searchItem?.visibility = GONE

						nameTextView[1]?.textColor = ResourcesCompat.getColor(context.resources, R.color.white, null)
						nameTextView[1]?.leftDrawable?.apply { setTint(context.getColor(R.color.white)) }
						onlineTextView[1]?.textColor = ResourcesCompat.getColor(context.resources, R.color.white, null)
						actionBar?.setItemsBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.white, null), false)
						actionBar?.setItemsColor(ResourcesCompat.getColor(context.resources, R.color.white, null), false)
						overlaysView?.setOverlaysVisible()
						overlaysView?.setAlphaValue(1.0f, false)
						avatarImage?.setForegroundAlpha(1.0f)
						avatarContainer?.visibility = GONE
						avatarsViewPager?.resetCurrentItem()
						avatarsViewPager?.visibility = VISIBLE
						expandPhoto = false
					}

					allowPullingDown = true
					isPulledDown = true

					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.needCheckSystemBarColors, true)

					if (!messagesController.isChatNoForwards(currentChat)) {
						otherItem?.showSubItem(gallery_menu_save)
					}
					else {
						otherItem?.hideSubItem(gallery_menu_save)
					}

					if (imageUpdater != null) {
						otherItem?.showSubItem(edit_avatar)
						otherItem?.showSubItem(delete_avatar)
						otherItem?.hideSubItem(logout)
					}

					currentExpandAnimatorFracture = 1.0f

					val paddingTop: Int
					var paddingBottom: Int

					if (isInLandscapeMode) {
						paddingTop = AndroidUtilities.dp(88f)
						paddingBottom = 0
					}
					else {
						paddingTop = listView!!.measuredWidth
						paddingBottom = max(0, measuredHeight - (listContentHeight + AndroidUtilities.dp(88f) + actionBarHeight))
					}

					if (banFromGroup != 0L) {
						paddingBottom += AndroidUtilities.dp(48f)
						listView?.bottomGlowOffset = AndroidUtilities.dp(48f)
					}
					else {
						listView?.bottomGlowOffset = 0
					}

					initialAnimationExtraHeight = (paddingTop - actionBarHeight).toFloat()
					layoutManager?.scrollToPositionWithOffset(0, -actionBarHeight)
					listView?.setPadding(0, paddingTop, 0, paddingBottom)
					measureChildWithMargins(listView, widthMeasureSpec, 0, heightMeasureSpec, 0)
					listView?.layout(0, actionBarHeight, listView?.measuredWidth ?: 0, actionBarHeight + (listView?.measuredHeight ?: 0))
					ignoreLayout = false
				}
				else if (fragmentOpened && !openAnimationInProgress && !firstLayout) {
					ignoreLayout = true
					val paddingTop: Int
					var paddingBottom: Int

					if (isInLandscapeMode || AndroidUtilities.isTablet()) {
						paddingTop = AndroidUtilities.dp(88f)
						paddingBottom = 0
					}
					else {
						paddingTop = listView!!.measuredWidth
						paddingBottom = max(0, measuredHeight - (listContentHeight + AndroidUtilities.dp(88f) + actionBarHeight))
					}

					if (banFromGroup != 0L) {
						paddingBottom += AndroidUtilities.dp(48f)
						listView?.bottomGlowOffset = AndroidUtilities.dp(48f)
					}
					else {
						listView?.bottomGlowOffset = 0
					}

					val currentPaddingTop = listView?.paddingTop ?: 0
					var view: View? = null
					var pos = RecyclerView.NO_POSITION

					for (i in 0 until listView!!.childCount) {
						val p = listView!!.getChildAdapterPosition(listView!!.getChildAt(i))

						if (p != RecyclerView.NO_POSITION) {
							view = listView?.getChildAt(i)
							pos = p
							break
						}
					}

					if (view == null) {
						view = listView?.getChildAt(0)

						if (view != null) {
							val holder = listView?.findContainingViewHolder(view)

							if (holder != null) {
								pos = holder.adapterPosition

								if (pos == RecyclerView.NO_POSITION) {
									pos = holder.adapterPosition
								}
							}
						}
					}

					var top = paddingTop

					if (view != null) {
						top = view.top
					}

					var layout = false

					if (actionBar!!.isSearchFieldVisible && sharedMediaRow >= 0) {
						layoutManager?.scrollToPositionWithOffset(sharedMediaRow, -paddingTop)
						layout = true
					}
					else if (invalidateScroll || currentPaddingTop != paddingTop) {
						if (savedScrollPosition >= 0) {
							layoutManager?.scrollToPositionWithOffset(savedScrollPosition, savedScrollOffset - paddingTop)
						}
						else if ((!changed || !allowPullingDown) && view != null) {
							if (pos == 0 && !allowPullingDown && top > AndroidUtilities.dp(88f)) {
								top = AndroidUtilities.dp(88f)
							}

							layoutManager?.scrollToPositionWithOffset(pos, top - paddingTop)

							layout = true
						}
						else {
							layoutManager?.scrollToPositionWithOffset(0, AndroidUtilities.dp(88f) - paddingTop)
						}
					}

					if (currentPaddingTop != paddingTop || listView!!.paddingBottom != paddingBottom) {
						listView?.setPadding(0, paddingTop, 0, paddingBottom)
						layout = true
					}

					if (layout) {
						measureChildWithMargins(listView, widthMeasureSpec, 0, heightMeasureSpec, 0)

						try {
							listView?.layout(0, actionBarHeight, listView!!.measuredWidth, actionBarHeight + listView!!.measuredHeight)
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					ignoreLayout = false
				}

				val portrait = height > MeasureSpec.getSize(widthMeasureSpec)

				if (portrait != wasPortrait) {
					post {
						selectAnimatedEmojiDialog?.dismiss()
						selectAnimatedEmojiDialog = null
					}

					wasPortrait = portrait
				}
			}

			override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
				super.onLayout(changed, left, top, right, bottom)
				savedScrollPosition = -1
				firstLayout = false
				invalidateScroll = false
				checkListViewScroll()
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}

			override fun dispatchDraw(canvas: Canvas) {
				whitePaint.color = ResourcesCompat.getColor(context.resources, R.color.background, null)

				if (listView!!.visibility == VISIBLE) {
					grayPaint.color = ResourcesCompat.getColor(context.resources, R.color.light_background, null)

					if (transitionAnimationInProgress) {
						whitePaint.alpha = (255 * listView!!.alpha).toInt()
					}

					if (transitionAnimationInProgress) {
						grayPaint.alpha = (255 * listView!!.alpha).toInt()
					}

					var count = listView!!.childCount

					sortedChildren.clear()

					var hasRemovingItems = false

					for (i in 0 until count) {
						listView?.getChildAt(i)?.let { child ->
							if (listView?.getChildAdapterPosition(child) != RecyclerView.NO_POSITION) {
								sortedChildren.add(listView!!.getChildAt(i))
							}
							else {
								hasRemovingItems = true
							}
						}
					}

					Collections.sort(sortedChildren, viewComparator)

					var hasBackground = false
					var lastY = listView!!.y

					count = sortedChildren.size

					if (!openAnimationInProgress && count > 0 && !hasRemovingItems) {
						lastY += sortedChildren[0].y
					}

					var alpha = 1f

					for (i in 0 until count) {
						val child = sortedChildren[i]
						val currentHasBackground = child.background != null
						val currentY = (listView!!.y + child.y).toInt()

						if (hasBackground == currentHasBackground) {
							if (child.alpha == 1f) {
								alpha = 1f
							}

							continue
						}

						if (hasBackground) {
							canvas.drawRect(listView!!.x, lastY, listView!!.x + listView!!.measuredWidth, currentY.toFloat(), grayPaint)
						}
						else {
							if (alpha != 1f) {
								canvas.drawRect(listView!!.x, lastY, listView!!.x + listView!!.measuredWidth, currentY.toFloat(), grayPaint)
								whitePaint.alpha = (255 * alpha).toInt()
								canvas.drawRect(listView!!.x, lastY, listView!!.x + listView!!.measuredWidth, currentY.toFloat(), whitePaint)
								whitePaint.alpha = 255
							}
							else {
								canvas.drawRect(listView!!.x, lastY, listView!!.x + listView!!.measuredWidth, currentY.toFloat(), whitePaint)
							}
						}

						hasBackground = currentHasBackground
						lastY = currentY.toFloat()
						alpha = child.alpha
					}

					if (hasBackground) {
						canvas.drawRect(listView!!.x, lastY, listView!!.x + listView!!.measuredWidth, listView!!.bottom.toFloat(), grayPaint)
					}
					else {
						if (alpha != 1f) {
							canvas.drawRect(listView!!.x, lastY, listView!!.x + listView!!.measuredWidth, listView!!.bottom.toFloat(), grayPaint)
							whitePaint.alpha = (255 * alpha).toInt()
							canvas.drawRect(listView!!.x, lastY, listView!!.x + listView!!.measuredWidth, listView!!.bottom.toFloat(), whitePaint)
							whitePaint.alpha = 255
						}
						else {
							canvas.drawRect(listView!!.x, lastY, listView!!.x + listView!!.measuredWidth, listView!!.bottom.toFloat(), whitePaint)
						}
					}
				}
				else {
					val top = searchListView!!.top
					canvas.drawRect(0f, top + extraHeight + searchTransitionOffset, measuredWidth.toFloat(), (top + measuredHeight).toFloat(), whitePaint)
				}

				super.dispatchDraw(canvas)

				if (profileTransitionInProgress && parentLayout!!.fragmentsStack.size > 1) {
					val fragment = parentLayout!!.fragmentsStack[parentLayout!!.fragmentsStack.size - 2]

					if (fragment is ChatActivity) {
						val fragmentContextView = fragment.fragmentContextView

						if (fragmentContextView != null && fragmentContextView.isCallStyle) {
							var progress = extraHeight / AndroidUtilities.dpf2(fragmentContextView.styleHeight.toFloat())

							if (progress > 1f) {
								progress = 1f
							}

							canvas.save()
							canvas.translate(fragmentContextView.x, fragmentContextView.y)

							fragmentContextView.setDrawOverlay(true)
							fragmentContextView.setCollapseTransition(true, extraHeight, progress)
							fragmentContextView.draw(canvas)
							fragmentContextView.setCollapseTransition(false, extraHeight, progress)
							fragmentContextView.setDrawOverlay(false)

							canvas.restore()
						}
					}
				}

				if (scrimPaint.alpha > 0) {
					canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
				}

				if (scrimView != null) {
					val c = canvas.save()

					canvas.translate(scrimView!!.left.toFloat(), scrimView!!.top.toFloat())

					if (scrimView === actionBar?.backButton) {
						val r = max(scrimView!!.measuredWidth, scrimView!!.measuredHeight) / 2
						val wasAlpha = actionBarBackgroundPaint.alpha
						actionBarBackgroundPaint.alpha = (wasAlpha * (scrimPaint.alpha / 255f) / 0.3f).toInt()
						canvas.drawCircle(r.toFloat(), r.toFloat(), r * 0.7f, actionBarBackgroundPaint)
						actionBarBackgroundPaint.alpha = wasAlpha
					}

					scrimView?.draw(canvas)

					canvas.restoreToCount(c)
				}
			}

			override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
				return if (pinchToZoomHelper!!.isInOverlayMode && ((child === avatarContainer2) || (child === actionBar) || (child === writeButton))) {
					true
				}
				else {
					super.drawChild(canvas, child, drawingTime)
				}
			}
		}

		val users = if ((chatInfo?.participants?.participants?.size ?: 0) > 5) sortedUsers else null

		sharedMediaLayout = object : SharedMediaLayout(context, did, sharedMediaPreloader, userInfo?.common_chats_count ?: 0, sortedUsers, chatInfo, users != null, this@ProfileActivity, this@ProfileActivity, VIEW_TYPE_PROFILE_ACTIVITY) {
			override fun onSelectedTabChanged() {
				updateSelectedMediaTabText()
			}

			override fun canShowSearchItem(): Boolean {
				return mediaHeaderVisible
			}

			override fun onSearchStateChanged(expanded: Boolean) {
				if (SharedConfig.smoothKeyboard) {
					AndroidUtilities.removeAdjustResize(parentActivity, classGuid)
				}

				listView?.stopScroll()

				avatarContainer2?.pivotY = avatarContainer!!.pivotY + avatarContainer!!.measuredHeight / 2f
				avatarContainer2?.pivotX = avatarContainer2!!.measuredWidth / 2f

				AndroidUtilities.updateViewVisibilityAnimated(avatarContainer2, !expanded, 0.95f, true)

				callItem?.visibility = if (expanded || !callItemVisible) GONE else INVISIBLE
				videoCallItem?.visibility = if (expanded || !videoCallItemVisible) GONE else INVISIBLE
				editItem?.visibility = if (expanded || !editItemVisible) GONE else INVISIBLE
				otherItem?.visibility = if (expanded) GONE else INVISIBLE
				qrItem?.visibility = if (expanded) GONE else INVISIBLE
			}

			override fun onMemberClick(participant: TLRPC.ChatParticipant?, isLong: Boolean): Boolean {
				if (participant == null) {
					return false
				}

				return this@ProfileActivity.onMemberClick(participant, isLong)
			}

			override fun drawBackgroundWithBlur(canvas: Canvas, y: Float, rectTmp2: Rect?, backgroundPaint: Paint?) {
				contentView?.drawBlurRect(canvas, listView!!.y + getY() + y, rectTmp2, backgroundPaint, true)
			}

			override fun invalidateBlur() {
				contentView?.invalidateBlur()
			}
		}

		sharedMediaLayout?.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT)

		val menu = actionBar?.createMenu()

		if (userId == userConfig.clientUserId) {
//			qrItem = menu?.addItem(qr_button, R.drawable.msg_qr_mini)
//			qrItem?.contentDescription = context.getString(R.string.GetQRCode)
//
//			updateQrItemVisibility(false)
//
			if (ContactsController.getInstance(currentAccount).getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_ADDED_BY_PHONE) == null) {
				ContactsController.getInstance(currentAccount).loadPrivacySettings()
			}
		}

//		if (imageUpdater != null) {
//			val searchDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_search_menu, null)!!.mutate()
//			searchDrawable.setTintList(ColorStateList.valueOf(ResourcesCompat.getColor(context.resources, R.color.white, null)))
//			searchDrawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.SRC_IN)
//
//			searchItem = menu?.addItem(search_button, searchDrawable)?.setIsSearchField(true)?.setActionBarMenuItemSearchListener(object : ActionBarMenuItemSearchListener() {
//				override fun getCustomToggleTransition(): Animator {
//					searchMode = !searchMode
//
//					if (!searchMode) {
//						searchItem?.clearFocusOnSearchView()
//					}
//
//					if (searchMode) {
//						searchItem?.searchField?.setText("")
//					}
//
//					return searchExpandTransition(searchMode)
//				}
//
//				override fun onTextChanged(editText: EditText) {
//					searchAdapter?.search(editText.text.toString().lowercase())
//				}
//			})
//
//			searchItem?.contentDescription = context.getString(R.string.SearchInSettings)
//			searchItem?.setSearchFieldHint(context.getString(R.string.SearchInSettings))
//
//			sharedMediaLayout?.searchItem?.visibility = View.GONE
//
//			if (expandPhoto) {
//				searchItem?.visibility = View.GONE
//			}
//		}

		videoCallItem = menu?.addItem(video_call_item, R.drawable.chat_calls_video)
		videoCallItem?.contentDescription = context.getString(R.string.VideoCall)

		if (chatId != 0L) {
			callItem = menu?.addItem(call_item, R.drawable.msg_voicechat2)

			if (ChatObject.isChannelOrGiga(currentChat)) {
				callItem?.contentDescription = context.getString(R.string.VoipChannelVoiceChat)
			}
			else {
				callItem?.contentDescription = context.getString(R.string.VoipGroupVoiceChat)
			}
		}
		else {
			callItem = menu?.addItem(call_item, R.drawable.chat_calls_voice)
			callItem?.contentDescription = context.getString(R.string.Call)
		}

		editItem = menu?.addItem(edit_channel, R.drawable.msg_edit)
		editItem?.contentDescription = context.getString(R.string.Edit)

		otherItem = menu?.addItem(10, R.drawable.overflow_menu)

		ttlIconView = ImageView(context)
		ttlIconView?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.MULTIPLY)

		AndroidUtilities.updateViewVisibilityAnimated(ttlIconView, false, 0.8f, false)

		ttlIconView?.setImageResource(R.drawable.msg_mini_autodelete_timer)

		otherItem?.addView(ttlIconView, LayoutHelper.createFrame(12, 12f, Gravity.CENTER_VERTICAL or Gravity.LEFT, 8f, 2f, 0f, 0f))
		otherItem?.contentDescription = context.getString(R.string.AccDescrMoreOptions)

		var scrollTo: Int
		var writeButtonTag: Any? = null

		if (listView != null && imageUpdater != null) {
			scrollTo = layoutManager?.findFirstVisibleItemPosition() ?: -1

			val topView = layoutManager?.findViewByPosition(scrollTo)

			if (topView == null) {
				scrollTo = -1
			}

			writeButtonTag = writeButton?.tag
		}
		else {
			scrollTo = -1
		}

		createActionBarMenu(false)

		listAdapter = ListAdapter(context)
		searchAdapter = SearchAdapter(context)
		avatarDrawable = AvatarDrawable()
		fragmentView?.setWillNotDraw(false)
		contentView = fragmentView as? NestedFrameLayout
		contentView?.needBlur = true

		val frameLayout = fragmentView as FrameLayout

		listView = object : RecyclerListView(context) {
			private var velocityTracker: VelocityTracker? = null

			override fun canHighlightChildAt(child: View?, x: Float, y: Float): Boolean {
				return child !is AboutLinkCell && child?.tag != VIEW_TYPE_SHARE_LINK && child?.tag != VIEW_TYPE_PUBLIC_LINK
			}

			override fun allowSelectChildAtPosition(child: View?): Boolean {
				return child !== sharedMediaLayout
			}

			override fun hasOverlappingRendering(): Boolean {
				return false
			}

			override fun requestChildOnScreen(child: View, focused: View?) {
				// unused
			}

			override fun invalidate() {
				super.invalidate()
				fragmentView?.invalidate()
			}

			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(e: MotionEvent): Boolean {
				val action = e.action

				if (action == MotionEvent.ACTION_DOWN) {
					if (velocityTracker == null) {
						velocityTracker = VelocityTracker.obtain()
					}
					else {
						velocityTracker?.clear()
					}

					velocityTracker?.addMovement(e)
				}
				else if (action == MotionEvent.ACTION_MOVE) {
					if (velocityTracker != null) {
						velocityTracker?.addMovement(e)
						velocityTracker?.computeCurrentVelocity(1000)

						listViewVelocityY = velocityTracker?.getYVelocity(e.getPointerId(e.actionIndex)) ?: 0f
					}
				}
				else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
					velocityTracker?.recycle()
					velocityTracker = null
				}

				val result = super.onTouchEvent(e)

				if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
					if (allowPullingDown) {
						val view = layoutManager?.findViewByPosition(0)

						if (view != null) {
							if (isPulledDown) {
								val actionBarHeight = ActionBar.getCurrentActionBarHeight() + if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0
								listView?.smoothScrollBy(0, view.top - listView!!.measuredWidth + actionBarHeight, CubicBezierInterpolator.EASE_OUT_QUINT)
							}
							else {
								listView?.smoothScrollBy(0, view.top - AndroidUtilities.dp(88f), CubicBezierInterpolator.EASE_OUT_QUINT)
							}
						}
					}
				}
				return result
			}

			override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
				if (itemAnimator!!.isRunning && child.background == null && child.translationY != 0f) {
					val useAlpha = listView?.getChildAdapterPosition(child) == sharedMediaRow && child.alpha != 1f

					if (useAlpha) {
						whitePaint.alpha = (255 * listView!!.alpha * child.alpha).toInt()
					}

					canvas.drawRect(listView!!.x, child.y, listView!!.x + listView!!.measuredWidth, child.y + child.height, whitePaint)

					if (useAlpha) {
						whitePaint.alpha = (255 * listView!!.alpha).toInt()
					}
				}

				return super.drawChild(canvas, child, drawingTime)
			}
		}

		listView?.isVerticalScrollBarEnabled = false

		val defaultItemAnimator: DefaultItemAnimator = object : DefaultItemAnimator() {
			var animationIndex = -1

			override fun onAllAnimationsDone() {
				super.onAllAnimationsDone()

				AndroidUtilities.runOnUIThread {
					notificationCenter.onAnimationFinish(animationIndex)
				}
			}

			override fun runPendingAnimations() {
				val removalsPending = mPendingRemovals.isNotEmpty()
				val movesPending = mPendingMoves.isNotEmpty()
				val changesPending = mPendingChanges.isNotEmpty()
				val additionsPending = mPendingAdditions.isNotEmpty()

				if (removalsPending || movesPending || additionsPending || changesPending) {
					val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

					valueAnimator.addUpdateListener {
						listView?.invalidate()
					}

					valueAnimator.duration = moveDuration
					valueAnimator.start()

					animationIndex = notificationCenter.setAnimationInProgress(animationIndex, null)
				}

				super.runPendingAnimations()
			}

			override fun getAddAnimationDelay(removeDuration: Long, moveDuration: Long, changeDuration: Long): Long {
				return 0
			}

			override fun getMoveAnimationDelay(): Long {
				return 0
			}

			override fun getMoveDuration(): Long {
				return 220
			}

			override fun getRemoveDuration(): Long {
				return 220
			}

			override fun getAddDuration(): Long {
				return 220
			}
		}

		listView?.itemAnimator = defaultItemAnimator

		defaultItemAnimator.supportsChangeAnimations = false
		defaultItemAnimator.setDelayAnimations(false)

		val touchCallBack: ItemTouchHelper.SimpleCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
			val bgColor = context.getColor(R.color.purple)

			val bgPaint = Paint().apply {
				color = bgColor
				style = Paint.Style.FILL
			}

			val text = context.getString(R.string.log_out)
			val textColor = context.getColor(R.color.white)

			val textPaint = Paint().apply {
				color = textColor
				style = Paint.Style.FILL
				textSize = context.resources.getDimensionPixelSize(R.dimen.common_size_16dp).toFloat()
			}

			val textOffsetX = 24.dp
			val textHeight: Int
			val textWidth: Int

			init {
				val bounds = Rect()
				textPaint.getTextBounds(text, 0, text.length, bounds)

				textHeight = bounds.height()
				textWidth = bounds.width()
			}

			override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
				val adapterPosition = viewHolder.adapterPosition

				if (adapterPosition in getFilteredAccountsRows()) {
					return ItemTouchHelper.LEFT
				}

				return 0
			}

			override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
				return false
			}

			override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
				val position = viewHolder.adapterPosition
				val row = position - getFilteredAccountsRows().min()
				val account = getAccountIndex(row)

				androidx.appcompat.app.AlertDialog.Builder(context).setTitle(R.string.logout).setMessage(R.string.logout_confirm).setPositiveButton(R.string.log_out) { _, _ ->
					MessagesController.getInstance(account).performLogout(1)

					if (account != currentAccount) {
						updateRowsIds()
						listAdapter?.notifyDataSetChanged()
					}
				}.setNegativeButton(R.string.cancel, null).show()
			}

			override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
				val left = viewHolder.itemView.right.toFloat() + dX
				val top = viewHolder.itemView.top.toFloat()
				val right = viewHolder.itemView.right.toFloat()
				val bottom = viewHolder.itemView.bottom.toFloat()

				c.drawRect(left, top, right, bottom, bgPaint)
				c.drawText(text, max(left + textOffsetX, left + (right - left) / 2 - textWidth / 2), bottom - (bottom - top) / 2 + textHeight / 2, textPaint)

				super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
			}
		}

		val touchHelper = ItemTouchHelper(touchCallBack, true)
		touchHelper.attachToRecyclerView(listView)

		listView?.clipToPadding = false
		listView?.setHideIfEmpty(false)

		layoutManager = object : LinearLayoutManager(context) {
			override fun supportsPredictiveItemAnimations(): Boolean {
				return imageUpdater != null
			}

			override fun scrollVerticallyBy(dy: Int, recycler: Recycler, state: RecyclerView.State): Int {
				@Suppress("NAME_SHADOWING") var dy = dy
				val view = layoutManager?.findViewByPosition(0)

				if (view != null && !openingAvatar) {
					val canScroll = view.top - AndroidUtilities.dp(88f)

					if (!allowPullingDown && canScroll > dy) {
						dy = canScroll

						if (avatarsViewPager!!.hasImages() && avatarImage!!.imageReceiver.hasNotThumb() && !AndroidUtilities.isAccessibilityScreenReaderEnabled() && !isInLandscapeMode && !AndroidUtilities.isTablet()) {
							allowPullingDown = avatarBig == null
						}
					}
					else if (allowPullingDown) {
						if (dy >= canScroll) {
							dy = canScroll
							allowPullingDown = false
						}
						else if (listView?.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
							if (!isPulledDown) {
								dy /= 2
							}
						}
					}
				}

				return super.scrollVerticallyBy(dy, recycler, state)
			}
		}

		layoutManager?.orientation = LinearLayoutManager.VERTICAL
		layoutManager?.mIgnoreTopPadding = false
		listView?.layoutManager = layoutManager
		listView?.setGlowColor(0)
		listView?.adapter = listAdapter

		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

		listView?.setOnItemClickListener(object : RecyclerListView.OnItemClickListenerExtended {
			override fun onItemClick(view: View, position: Int, x: Float, y: Float) {
				val parentActivity = parentActivity ?: return

				listView?.stopScroll()

				if (position == reportReactionRow) {
					val builder = AlertDialog.Builder(parentActivity)
					builder.setTitle(parentActivity.getString(R.string.ReportReaction))
					builder.setMessage(parentActivity.getString(R.string.ReportAlertReaction))

					val chat = messagesController.getChat(-reportReactionFromDialogId)
					val cells = arrayOfNulls<CheckBoxCell>(1)

					if (chat != null && ChatObject.canBlockUsers(chat)) {
						val linearLayout = LinearLayout(parentActivity)
						linearLayout.orientation = LinearLayout.VERTICAL

						cells[0] = CheckBoxCell(parentActivity, 1)
						cells[0]?.background = Theme.getSelectorDrawable(false)
						cells[0]?.setText(parentActivity.getString(R.string.BanUser), "", checked = true, divider = false)

						cells[0]?.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(16f)
						else AndroidUtilities.dp(8f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(8f)
						else AndroidUtilities.dp(16f), 0)

						linearLayout.addView(cells[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

						cells[0]?.setOnClickListener {
							cells[0]?.setChecked(!cells[0]!!.isChecked, true)
						}

						builder.setCustomViewOffset(12)
						builder.setView(linearLayout)
					}

					builder.setPositiveButton(parentActivity.getString(R.string.ReportChat)) { _, _ ->
						val req = TLRPC.TL_messages_reportReaction()
						req.user_id = messagesController.getInputUser(userId)
						req.peer = messagesController.getInputPeer(reportReactionFromDialogId)
						req.id = reportReactionMessageId

						connectionsManager.sendRequest(req) { _, _ ->
							// unused
						}

						if (cells[0]?.isChecked == true) {
							val user = messagesController.getUser(userId)
							messagesController.deleteParticipantFromChat(-reportReactionFromDialogId, user)
						}

						reportReactionMessageId = 0

						updateListAnimated(false)

						BulletinFactory.of(this@ProfileActivity).createReportSent().show()
					}

					builder.setNegativeButton(context.getString(R.string.Cancel)) { dialog, _ ->
						dialog.dismiss()
					}

					val dialog = builder.show()

					val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
					button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
				}

				if (position == settingsKeyRow) {
					val args = Bundle()
					args.putInt("chat_id", DialogObject.getEncryptedChatId(dialogId))
					presentFragment(IdenticonActivity(args))
				}
				else if (position == settingsTimerRow) {
					currentEncryptedChat?.let { chat ->
						showDialog(AlertsCreator.createTTLAlert(parentActivity, chat).create())
					}
				}
				else if (position == notificationsRow) {
					if (LocaleController.isRTL && x <= AndroidUtilities.dp(76f) || !LocaleController.isRTL && x >= view.measuredWidth - AndroidUtilities.dp(76f)) {
						val checkCell = view as NotificationsCheckCell
						val checked = !checkCell.isChecked
						val defaultEnabled = notificationsController.isGlobalNotificationsEnabled(did)

						if (checked) {
							val preferences = MessagesController.getNotificationsSettings(currentAccount)
							val editor = preferences.edit()

							if (defaultEnabled) {
								editor.remove("notify2_$did")
							}
							else {
								editor.putInt("notify2_$did", 0)
							}

							messagesStorage.setDialogFlags(did, 0)

							editor.commit()

							val dialog = messagesController.dialogs_dict[did]
							dialog?.notify_settings = TLRPC.TL_peerNotifySettings()
						}
						else {
							val untilTime = Int.MAX_VALUE
							val preferences = MessagesController.getNotificationsSettings(currentAccount)
							val editor = preferences.edit()

							val flags = if (!defaultEnabled) {
								editor.remove("notify2_$did")
								0L
							}
							else {
								editor.putInt("notify2_$did", 2)
								1L
							}

							notificationsController.removeNotificationsForDialog(did)
							messagesStorage.setDialogFlags(did, flags)

							editor.commit()

							val dialog = messagesController.dialogs_dict[did]

							if (dialog != null) {
								dialog.notify_settings = TLRPC.TL_peerNotifySettings()

								if (defaultEnabled) {
									dialog.notify_settings.mute_until = untilTime
								}
							}
						}

						notificationsController.updateServerNotificationsSettings(did)

						checkCell.isChecked = checked

						val holder = listView?.findViewHolderForAdapterPosition(notificationsRow) as? RecyclerListView.Holder

						if (holder != null) {
							listAdapter?.onBindViewHolder(holder, notificationsRow)
						}

						return
					}

					val chatNotificationsPopupWrapper = ChatNotificationsPopupWrapper(context, currentAccount, null, true, object : ChatNotificationsPopupWrapper.Callback {
						override fun toggleSound() {
							val preferences = MessagesController.getNotificationsSettings(currentAccount)
							val enabled = !preferences.getBoolean("sound_enabled_$did", true)
							preferences.edit().putBoolean("sound_enabled_$did", enabled).commit()

							if (BulletinFactory.canShowBulletin(this@ProfileActivity)) {
								BulletinFactory.createSoundEnabledBulletin(this@ProfileActivity, if (enabled) NotificationsController.SETTING_SOUND_ON else NotificationsController.SETTING_SOUND_OFF).show()
							}
						}

						override fun muteFor(timeInSeconds: Int) {
							if (timeInSeconds == 0) {
								if (messagesController.isDialogMuted(did)) {
									toggleMute()
								}
								if (BulletinFactory.canShowBulletin(this@ProfileActivity)) {
									BulletinFactory.createMuteBulletin(this@ProfileActivity, NotificationsController.SETTING_MUTE_UNMUTE, timeInSeconds).show()
								}
							}
							else {
								notificationsController.muteUntil(did, timeInSeconds)
								if (BulletinFactory.canShowBulletin(this@ProfileActivity)) {
									BulletinFactory.createMuteBulletin(this@ProfileActivity, NotificationsController.SETTING_MUTE_CUSTOM, timeInSeconds).show()
								}
								if (notificationsRow >= 0) {
									listAdapter!!.notifyItemChanged(notificationsRow)
								}
							}
						}

						override fun showCustomize() {
							if (did != 0L) {
								val args = Bundle()
								args.putLong("dialog_id", did)
								presentFragment(ProfileNotificationsActivity(args))
							}
						}

						override fun toggleMute() {
							val muted = messagesController.isDialogMuted(did)
							notificationsController.muteDialog(did, !muted)
							BulletinFactory.createMuteBulletin(this@ProfileActivity, !muted).show()

							if (notificationsRow >= 0) {
								listAdapter!!.notifyItemChanged(notificationsRow)
							}
						}
					})

					chatNotificationsPopupWrapper.update(did)
					chatNotificationsPopupWrapper.showAsOptions(this@ProfileActivity, view, x, y)
				}
				else if (position == unblockRow) {
					messagesController.unblockPeer(userId)

					if (BulletinFactory.canShowBulletin(this@ProfileActivity)) {
						BulletinFactory.createBanBulletin(this@ProfileActivity, false).show()
					}
				}
				else if (position == addToGroupButtonRow) {
					try {
						actionBar!!.getActionBarMenuOnItemClick().onItemClick(invite_to_group)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				else if (position == sendMessageRow) {
					onWriteButtonClick()
				}
				else if (position == reportRow) {
					AlertsCreator.createReportAlert(parentActivity, getDialogId(), 0, this@ProfileActivity, null)
				}
				else if (position in membersStartRow until membersEndRow) {
					val participant = if (sortedUsers.isNullOrEmpty()) {
						chatInfo!!.participants.participants[position - membersStartRow]
					}
					else {
						chatInfo!!.participants.participants[sortedUsers!![position - membersStartRow]]
					}

					onMemberClick(participant, false)
				}
				else if (position == addMemberRow) {
					openAddMember()
				}
				else if (position == usernameRow) {
					processOnClickOrPress(position, view)
				}
				else if (position == locationRow) {
					if (chatInfo!!.location is TLRPC.TL_channelLocation) {
						val fragment = LocationActivity(LocationActivity.LOCATION_TYPE_GROUP_VIEW)
						fragment.setChatLocation(chatId, chatInfo!!.location as TLRPC.TL_channelLocation)
						presentFragment(fragment)
					}
				}
				else if (position == joinRow) {
					messagesController.addUserToChat(currentChat!!.id, userConfig.getCurrentUser(), 0, null, this@ProfileActivity, null)
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.closeSearchByActiveAction)
				}
				else if (position == subscribersRow) {
					val args = Bundle()
					args.putLong("chat_id", chatId)
					args.putInt("type", ChatUsersActivity.TYPE_USERS)
					val fragment = ChatUsersActivity(args)
					fragment.setInfo(chatInfo)
					presentFragment(fragment)
				}
				else if (position == subscribersRequestsRow) {
					val activity = MemberRequestsActivity(chatId)
					presentFragment(activity)
				}
				else if (position == administratorsRow) {
					val args = Bundle()
					args.putLong("chat_id", chatId)
					args.putInt("type", ChatUsersActivity.TYPE_ADMIN)
					val fragment = ChatUsersActivity(args)
					fragment.setInfo(chatInfo)
					presentFragment(fragment)
				}
				else if (position == blockedUsersRow) {
					val args = Bundle()
					args.putLong("chat_id", chatId)
					args.putInt("type", ChatUsersActivity.TYPE_BANNED)
					val fragment = ChatUsersActivity(args)
					fragment.setInfo(chatInfo)
					presentFragment(fragment)
				}
				else if (position == notificationRow) {
					presentFragment(NotificationsSettingsActivity())
				}
				else if (position == privacyRow) {
					presentFragment(PrivacySettingsActivity().setCurrentPassword(currentPassword))
				}
				else if (position == dataRow) {
					presentFragment(DataSettingsActivity())
				}
				else if (position == chatRow) {
					presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))
				}
				else if (position == filtersRow) {
					presentFragment(FiltersSetupActivity())
				}
				else if (position == stickersRow) {
					presentFragment(StickersActivity(MediaDataController.TYPE_IMAGE, null))
				}
				else if (position == devicesRow) {
					presentFragment(SessionsActivity(SessionsActivity.ALL_SESSIONS))
				}
				else if (position == questionRow) {
					showDialog(AlertsCreator.createSupportAlert(this@ProfileActivity))
				}
				else if (position == faqRow) {
					Browser.openUrl(parentActivity, context.getString(R.string.TelegramFaqUrl))
				}
				else if (position == policyRow) {
					Browser.openUrl(parentActivity, context.getString(R.string.PrivacyPolicyUrl))
				}
				else if (position == sendLogsRow) {
					sendLogs(false)
				}
				else if (position == sendLastLogsRow) {
					sendLogs(true)
				}
				else if (position == clearLogsRow) {
					FileLog.cleanupLogs()
				}
				else if (position == languageRow) {
					presentFragment(LanguageSelectActivity())
				}
				else if (position == setUsernameRow) {
					presentFragment(ChangeUsernameActivity())
				}
				else if (position == bioRow) {
					if (userInfo != null) {
						presentFragment(ChangeBioActivity())
					}
				}
				else if (position == setAvatarRow) {
					onWriteButtonClick()
				}
				else if (position == premiumRow) {
					presentFragment(PremiumPreviewFragment("settings"))
				}
				else if (position == appearanceRow) {
					Toast.makeText(parentActivity, "TODO: show Appearance screen", Toast.LENGTH_SHORT).show()
				}
				else if (position == walletRow) {
					presentFragment(WalletFragment())
				}
				else if (position == subscriptionsRow) {
					presentFragment(CurrentSubscriptionsFragment())
				}
				else if (position == myNotificationsRow) {
					loadBotUser(777000L) {
						openBot(777000L)
					}
				}
				else if (position == aiChatBotRow) {
//					loadBotUser { openBot() }
					presentFragment(AiSpaceFragment())
				}
				else if (position == referralRow) {
					presentFragment(ReferralProgressFragment())
				}
				else if (position == foldersRow) {
					// TODO: open folders
				}
				else if (position == purchasesRow) {
					// TODO: open purchases
				}
				else if (position == inviteRow) {
					if (userInfo != null) {
						val fragment = ManageLinksActivity(userId, invite?.admin_id ?: 0L)
						fragment.setInfo(userInfo!!, invite)
						presentFragment(fragment)
					}
					else {
						Toast.makeText(parentActivity, R.string.user_info_is_loading, Toast.LENGTH_SHORT).show()
					}
				}
				else if (position == myCloudRow) {
					val me = getInstance(currentAccount).getCurrentUser()

					if (me != null) {
						val args = Bundle()
						args.putLong("user_id", me.id)

						presentFragment(ChatActivity(args))
					}
				}
				else if (position == settingsRow) {
					val fragment = ProfileSettingsFragment()
					presentFragment(fragment)
				}
				else if (position == supportRow) {
					loadBotUser(BuildConfig.SUPPORT_BOT_ID) {
						openBot(BuildConfig.SUPPORT_BOT_ID)
					}
					//composeEmail(address = context.getString(R.string.support_email), subject = "[${context.getString(R.string.AppName)}] ${context.getString(R.string.general_support)}", context = context)
				}
				else if (position == infoRow) {
					val fragment = InformationFragment()
					presentFragment(fragment)
				}
				else if (position == logoutRow) {
					androidx.appcompat.app.AlertDialog.Builder(context).setTitle(R.string.logout).setMessage(R.string.logout_confirm).setPositiveButton(R.string.log_out) { _, _ ->
						MessagesController.getInstance(currentAccount).performLogout(1)
					}.setNegativeButton(R.string.cancel) { _, _ ->
						// just dismiss the dialog
					}.show()
				}
				else if (position == addAccountRow) {
					var freeAccounts = 0
					var availableAccount: Int? = null

					for (a in UserConfig.MAX_ACCOUNT_COUNT - 1 downTo 0) {
						if (!getInstance(a).isClientActivated) {
							freeAccounts++

							if (availableAccount == null) {
								availableAccount = a
							}
						}
					}

					if (freeAccounts > 0 && availableAccount != null) {
						presentFragment(LoginActivity(availableAccount))
					}
					else {
						val binding = AccountsLimitReachedSheetBinding.inflate(LayoutInflater.from(context))
						binding.limitLabel.text = UserConfig.MAX_ACCOUNT_COUNT.toString()
						binding.description.text = context.getString(R.string.accounts_limit_reached, UserConfig.MAX_ACCOUNT_COUNT)

						val builder = BottomSheet.Builder(context)
						builder.customView = binding.root
						builder.setUseFullWidth(true)

						val dialog = builder.create()
						dialog.setCanDismissWithSwipe(true)

						binding.closeButton.setOnClickListener {
							dialog.dismiss()
						}

						binding.okButton.setOnClickListener {
							dialog.dismiss()
						}

						showDialog(dialog)
					}
				}
				else {
					val filteredAccountsRows = getFilteredAccountsRows()

					if (filteredAccountsRows.isNotEmpty() && position in filteredAccountsRows.min()..filteredAccountsRows.max()) {
						switchAccount(position - filteredAccountsRows.min())
					}
					else {
						processOnClickOrPress(position, view)
					}
				}
			}
		})

		listView?.setOnItemLongClickListener(object : RecyclerListView.OnItemLongClickListener {
			private var pressCount = 0

			override fun onItemClick(view: View, position: Int): Boolean {
				val parentActivity = parentActivity ?: return false

				return when (position) {
					versionRow -> {
						pressCount++

						if (pressCount >= 2 || BuildConfig.DEBUG_PRIVATE_VERSION) {
							val builder = AlertDialog.Builder(parentActivity)
							builder.setTitle(parentActivity.getString(R.string.DebugMenu))
							val items = arrayOf<CharSequence?>(
									parentActivity.getString(R.string.DebugMenuReloadContacts),
									parentActivity.getString(R.string.DebugMenuResetContacts),
									parentActivity.getString(R.string.DebugMenuResetDialogs),
									if (BuildConfig.DEBUG) null else if (BuildVars.logsEnabled) parentActivity.getString(R.string.DebugMenuDisableLogs) else parentActivity.getString(R.string.DebugMenuEnableLogs),
									if (SharedConfig.inappCamera) parentActivity.getString(R.string.DebugMenuDisableCamera) else parentActivity.getString(R.string.DebugMenuEnableCamera),
									parentActivity.getString(R.string.DebugMenuClearMediaCache),
									parentActivity.getString(R.string.DebugMenuCallSettings),
									null,
									parentActivity.getString(R.string.DebugMenuReadAllDialogs),
									if (SharedConfig.pauseMusicOnRecord) parentActivity.getString(R.string.DebugMenuDisablePauseMusic) else parentActivity.getString(R.string.DebugMenuEnablePauseMusic),
									if (BuildConfig.DEBUG && !AndroidUtilities.isTablet()) (if (SharedConfig.smoothKeyboard) parentActivity.getString(R.string.DebugMenuDisableSmoothKeyboard) else parentActivity.getString(R.string.DebugMenuEnableSmoothKeyboard)) else null,
									if (BuildConfig.DEBUG_PRIVATE_VERSION) (if (SharedConfig.disableVoiceAudioEffects) "Enable voip audio effects" else "Disable voip audio effects") else null,
									(if (SharedConfig.noStatusBar) "Show status bar background" else "Hide status bar background"),
									if (BuildConfig.DEBUG_PRIVATE_VERSION) "Reset suggestions" else null,
									if (BuildConfig.DEBUG_PRIVATE_VERSION) parentActivity.getString(if (SharedConfig.forceRtmpStream) R.string.DebugMenuDisableForceRtmpStreamFlag else R.string.DebugMenuEnableForceRtmpStreamFlag) else null,
									if (BuildConfig.DEBUG_PRIVATE_VERSION) parentActivity.getString(R.string.DebugMenuClearWebViewCache) else null,
									parentActivity.getString(if (SharedConfig.debugWebView) R.string.DebugMenuDisableWebViewDebug else R.string.DebugMenuEnableWebViewDebug),
									if (AndroidUtilities.isTabletInternal() && BuildConfig.DEBUG_PRIVATE_VERSION) (if (SharedConfig.forceDisableTabletMode) "Enable tablet mode" else "Disable tablet mode") else null,
							)

							builder.setItems(items) { _, which ->
								when (which) {
									0 -> {
										contactsController.loadContacts(false, 0)
									}

									1 -> {
										contactsController.resetImportedContacts()
									}

									2 -> {
										messagesController.forceResetDialogs()
									}

									3 -> {
										BuildVars.logsEnabled = !BuildVars.logsEnabled
										val sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE)
										sharedPreferences.edit().putBoolean("logsEnabled", BuildVars.logsEnabled).commit()
										updateRowsIds()
										listAdapter?.notifyDataSetChanged()
									}

									4 -> {
										SharedConfig.toggleInappCamera()
									}

									5 -> {
										messagesStorage.clearSentMedia()
										SharedConfig.setNoSoundHintShowed(false)
										val editor = MessagesController.getGlobalMainSettings().edit()
										editor.remove("archivehint").remove("proximityhint").remove("archivehint_l").remove("gifhint").remove("reminderhint").remove("soundHint").remove("themehint").remove("bganimationhint").remove("filterhint").commit()
										MessagesController.getEmojiSettings(currentAccount).edit().remove("featured_hidden").commit()
										SharedConfig.textSelectionHintShows = 0
										SharedConfig.lockRecordAudioVideoHint = 0
										SharedConfig.stickersReorderingHintUsed = false
										SharedConfig.forwardingOptionsHintShown = false
										SharedConfig.messageSeenHintCount = 3
										SharedConfig.emojiInteractionsHintCount = 3
										SharedConfig.dayNightThemeSwitchHintCount = 3
										SharedConfig.fastScrollHintCount = 3
										ChatThemeController.getInstance(currentAccount).clearCache()
									}

									6 -> {
										VoIPHelper.showCallDebugSettings(parentActivity)
									}

									7 -> {
										SharedConfig.toggleRoundCamera16to9()
									}

									8 -> {
										messagesStorage.readAllDialogs(-1)
									}

									9 -> {
										SharedConfig.togglePauseMusicOnRecord()
									}

									10 -> {
										SharedConfig.toggleSmoothKeyboard()

										if (SharedConfig.smoothKeyboard) {
											parentActivity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
										}
									}

									11 -> {
										SharedConfig.toggleDisableVoiceAudioEffects()
									}

									12 -> {
										SharedConfig.toggleNoStatusBar()

										if (SharedConfig.noStatusBar) {
											parentActivity.window.statusBarColor = 0
										}
										else {
											parentActivity.window.statusBarColor = 0x33000000
										}
									}

									13 -> {
										val suggestions = messagesController.pendingSuggestions
										suggestions?.add("VALIDATE_PHONE_NUMBER")
										suggestions?.add("VALIDATE_PASSWORD")

										notificationCenter.postNotificationName(NotificationCenter.newSuggestionsAvailable)
									}

									14 -> {
										SharedConfig.toggleForceRTMPStream()
									}

									15 -> {
										ApplicationLoader.applicationContext.deleteDatabase("webview.db")
										ApplicationLoader.applicationContext.deleteDatabase("webviewCache.db")
										WebStorage.getInstance().deleteAllData()
									}

									16 -> {
										SharedConfig.toggleDebugWebView()
										Toast.makeText(parentActivity, parentActivity.getString(if (SharedConfig.debugWebView) R.string.DebugMenuWebViewDebugEnabled else R.string.DebugMenuWebViewDebugDisabled), Toast.LENGTH_SHORT).show()
									}

									17 -> {
										SharedConfig.toggleForceDisableTabletMode()
										val activity = AndroidUtilities.findActivity(context)
										val pm = activity.packageManager
										val intent = pm.getLaunchIntentForPackage(activity.packageName)
										activity.finishAffinity() // Finishes all activities.
										activity.startActivity(intent) // Start the launch activity
										exitProcess(0)
									}
								}
							}

							builder.setNegativeButton(context.getString(R.string.Cancel), null)

							showDialog(builder.create())
						}
						else {
							try {
								Toast.makeText(parentActivity, "¯\\_(ツ)_/¯", Toast.LENGTH_SHORT).show()
							}
							catch (e: Exception) {
								FileLog.e(e)
							}
						}

						true
					}

					in membersStartRow until membersEndRow -> {
						val participant = if (sortedUsers!!.isNotEmpty()) {
							visibleChatParticipants[sortedUsers!![position - membersStartRow]]
						}
						else {
							visibleChatParticipants[position - membersStartRow]
						}

						onMemberClick(participant, true)
					}

					else -> {
						processOnClickOrPress(position, view)
					}
				}
			}
		})

		if (searchItem != null) {
			searchListView = RecyclerListView(context)
			searchListView?.isVerticalScrollBarEnabled = false
			searchListView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
			searchListView?.setGlowColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))
			searchListView?.adapter = searchAdapter
			searchListView?.itemAnimator = null
			searchListView?.visibility = View.GONE
			searchListView?.layoutAnimation = null
			searchListView?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

			frameLayout.addView(searchListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

			searchListView?.setOnItemClickListener(RecyclerListView.OnItemClickListener { _, position ->
				@Suppress("NAME_SHADOWING") var position = position

				if (position < 0) {
					return@OnItemClickListener
				}

				var `object`: Any? = null

				var add = true

				if (searchAdapter!!.isSearchWas) {
					if (position < searchAdapter!!.searchResults.size) {
						`object` = searchAdapter!!.searchResults[position]
					}
					else {
						position -= searchAdapter!!.searchResults.size + 1
						if (position >= 0 && position < searchAdapter!!.faqSearchResults.size) {
							`object` = searchAdapter!!.faqSearchResults[position]
						}
					}
				}
				else {
					if (searchAdapter!!.recentSearches.isNotEmpty()) {
						position--
					}

					if (position >= 0 && position < searchAdapter!!.recentSearches.size) {
						`object` = searchAdapter!!.recentSearches[position]
					}
					else {
						position -= searchAdapter!!.recentSearches.size + 1

						if (position >= 0 && position < searchAdapter!!.faqSearchArray.size) {
							`object` = searchAdapter!!.faqSearchArray[position]
							add = false
						}
					}
				}

				if (`object` is SearchAdapter.SearchResult) {
					`object`.open()
				}
				else if (`object` is FaqSearchResult) {
					NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.openArticle, searchAdapter?.faqWebPage, `object`.url)
				}

				if (add && `object` != null) {
					searchAdapter?.addRecent(`object`)
				}
			})

			searchListView?.setOnItemLongClickListener { _, _ ->
				if (searchAdapter!!.isSearchWas || searchAdapter!!.recentSearches.isEmpty()) {
					return@setOnItemLongClickListener false
				}

				val parentActivity = parentActivity ?: return@setOnItemLongClickListener false

				val builder = AlertDialog.Builder(parentActivity)
				builder.setTitle(parentActivity.getString(R.string.AppName))
				builder.setMessage(parentActivity.getString(R.string.ClearSearch))

				builder.setPositiveButton(parentActivity.getString(R.string.ClearButton).uppercase()) { _, _ ->
					searchAdapter?.clearRecent()
				}

				builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

				showDialog(builder.create())

				true
			}

			searchListView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
					if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
						parentActivity?.currentFocus?.let {
							AndroidUtilities.hideKeyboard(it)
						}
					}
				}
			})

			searchListView?.setAnimateEmptyView(true, 1)

			emptyView = StickerEmptyView(context, null, 1)
			emptyView?.setAnimateLayoutChange(true)
			emptyView?.subtitle?.visibility = View.GONE
			emptyView?.visibility = View.GONE

			frameLayout.addView(emptyView)

			searchAdapter?.loadFaqWebPage()
		}

		if (banFromGroup != 0L) {
			val chat = messagesController.getChat(banFromGroup)

			if (currentChannelParticipant == null) {
				val req = TLRPC.TL_channels_getParticipant()
				req.channel = MessagesController.getInputChannel(chat)
				req.participant = messagesController.getInputPeer(userId)

				connectionsManager.sendRequest(req) { response, _ ->
					if (response != null) {
						AndroidUtilities.runOnUIThread {
							currentChannelParticipant = (response as TLRPC.TL_channels_channelParticipant).participant
						}
					}
				}
			}

			val frameLayout1 = object : FrameLayout(context) {
				override fun onDraw(canvas: Canvas) {
					val bottom = Theme.chat_composeShadowDrawable.intrinsicHeight
					Theme.chat_composeShadowDrawable.setBounds(0, 0, measuredWidth, bottom)
					Theme.chat_composeShadowDrawable.draw(canvas)
					canvas.drawRect(0f, bottom.toFloat(), measuredWidth.toFloat(), measuredHeight.toFloat(), Theme.chat_composeBackgroundPaint)
				}
			}

			frameLayout1.setWillNotDraw(false)

			frameLayout.addView(frameLayout1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.LEFT or Gravity.BOTTOM))

			frameLayout1.setOnClickListener {
				val fragment = ChatRightsEditActivity(userId, banFromGroup, null, chat?.default_banned_rights, currentChannelParticipant?.banned_rights, "", ChatRightsEditActivity.TYPE_BANNED, edit = true, addingNew = false, addingNewBotHash = null)
				fragment.setDelegate(object : ChatRightsEditActivityDelegate {
					override fun didSetRights(rights: Int, rightsAdmin: TLRPC.TL_chatAdminRights?, rightsBanned: TL_chatBannedRights?, rank: String?) {
						removeSelfFromStack()
					}

					override fun didChangeOwner(user: User) {
						undoView!!.showWithAction(-chatId, if (currentChat!!.megagroup) UndoView.ACTION_OWNER_TRANSFERED_GROUP else UndoView.ACTION_OWNER_TRANSFERED_CHANNEL, user)
					}
				})
				presentFragment(fragment)
			}

			val textView = TextView(context)
			textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			textView.gravity = Gravity.CENTER
			textView.typeface = Theme.TYPEFACE_BOLD
			textView.text = context.getString(R.string.BanFromTheGroup)

			frameLayout1.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, 1f, 0f, 0f))

			listView?.setPadding(0, AndroidUtilities.dp(88f), 0, AndroidUtilities.dp(48f))
			listView?.bottomGlowOffset = AndroidUtilities.dp(48f)
		}
		else {
			listView?.setPadding(0, AndroidUtilities.dp(88f), 0, 0)
		}

		topView = TopView(context)
		topView?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.avatar_background, null))

		frameLayout.addView(topView)

		contentView?.blurBehindViews?.add(topView)

		animatedStatusView = AnimatedStatusView(context, 20, 60)
		animatedStatusView?.pivotX = AndroidUtilities.dp(30f).toFloat()
		animatedStatusView?.pivotY = AndroidUtilities.dp(30f).toFloat()

		avatarContainer = FrameLayout(context)

		avatarContainer2 = object : FrameLayout(context) {
			override fun dispatchDraw(canvas: Canvas) {
				super.dispatchDraw(canvas)

				if (transitionOnlineText != null) {
					canvas.save()
					canvas.translate(onlineTextView[0]!!.x, onlineTextView[0]!!.y)
					canvas.saveLayerAlpha(0f, 0f, transitionOnlineText!!.measuredWidth.toFloat(), transitionOnlineText!!.measuredHeight.toFloat(), (255 * (1f - animationProgress)).toInt())

					transitionOnlineText?.draw(canvas)

					canvas.restore()
					canvas.restore()

					invalidate()
				}
			}
		}

		AndroidUtilities.updateViewVisibilityAnimated(avatarContainer2, true, 1f, false)

		frameLayout.addView(avatarContainer2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.START, 0f, 0f, 0f, 0f))

		avatarContainer?.pivotX = 0f
		avatarContainer?.pivotY = 0f

		avatarContainer2?.addView(avatarContainer, LayoutHelper.createFrame(avatarSide.toInt(), avatarSide, Gravity.TOP or Gravity.LEFT, 64f, 0f, 0f, 0f))

		avatarImage = object : AvatarImageView(context) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)

				if (imageReceiver.hasNotThumb()) {
					info.text = context.getString(R.string.AccDescrProfilePicture)
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.Open)))
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, context.getString(R.string.AccDescrOpenInPhotoViewer)))
				}
				else {
					info.isVisibleToUser = false
				}
			}
		}

		avatarImage?.imageReceiver?.setAllowDecodeSingleFrame(true)
		avatarImage?.setRoundRadius(AndroidUtilities.dp(avatarSide * 0.45f))
		avatarImage?.pivotX = 0f
		avatarImage?.pivotY = 0f

		avatarContainer?.addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		avatarImage?.setOnClickListener(View.OnClickListener {
			if (avatarBig != null) {
				return@OnClickListener
			}

			if (!AndroidUtilities.isTablet() && !isInLandscapeMode && avatarImage!!.imageReceiver.hasNotThumb() && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
				openingAvatar = true
				allowPullingDown = true

				var child: View? = null

				for (i in 0 until listView!!.childCount) {
					if (listView!!.getChildAdapterPosition(listView!!.getChildAt(i)) == 0) {
						child = listView!!.getChildAt(i)
						break
					}
				}

				if (child != null) {
					val holder = listView?.findContainingViewHolder(child)

					if (holder != null) {
						val offset = positionToOffset[holder.adapterPosition]

						if (offset != null) {
							listView?.smoothScrollBy(0, -(offset + (listView!!.paddingTop - child.top - actionBar!!.measuredHeight)), CubicBezierInterpolator.EASE_OUT_QUINT)
							return@OnClickListener
						}
					}
				}
			}

			openAvatar()
		})

		avatarImage?.setOnLongClickListener(OnLongClickListener {
			if (avatarBig != null) {
				return@OnLongClickListener false
			}

			openAvatar()

			false
		})

		avatarProgressView = object : RadialProgressView(context) {
			private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

			init {
				paint.color = 0x55000000
			}

			override fun onDraw(canvas: Canvas) {
				if (avatarImage != null && avatarImage?.imageReceiver?.hasNotThumb() == true) {
					paint.alpha = (0x55 * avatarImage!!.imageReceiver.currentAlpha).toInt()
					canvas.drawCircle(measuredWidth / 2.0f, measuredHeight / 2.0f, measuredWidth / 2.0f, paint)
				}

				super.onDraw(canvas)
			}
		}

		avatarProgressView?.setSize(AndroidUtilities.dp(26f))
		avatarProgressView?.setProgressColor(-0x1)
		avatarProgressView?.setNoProgress(false)

		avatarContainer?.addView(avatarProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		timeItem = ImageView(context)
		timeItem?.setPadding(AndroidUtilities.dp(10f), AndroidUtilities.dp(10f), AndroidUtilities.dp(5f), AndroidUtilities.dp(5f))
		timeItem?.scaleType = ImageView.ScaleType.CENTER
		timeItem?.alpha = 0.0f
		timeItem?.setImageDrawable(TimerDrawable(context, null).also { timerDrawable = it })
		timeItem?.translationY = -1f

		frameLayout.addView(timeItem, LayoutHelper.createFrame(34, 34, Gravity.TOP or Gravity.LEFT))

		showAvatarProgress(show = false, animated = false)

		avatarsViewPager?.onDestroy()

		overlaysView = OverlaysView(context)

		avatarsViewPager = ProfileGalleryView(context, if (userId != 0L) userId else -chatId, actionBar!!, listView!!, avatarImage, classGuid, overlaysView)
		avatarsViewPager?.setChatInfo(chatInfo)

		avatarContainer2?.addView(avatarsViewPager)
		avatarContainer2?.addView(overlaysView)

		avatarImage?.avatarsViewPager = avatarsViewPager

		avatarsViewPagerIndicatorView = PagerIndicatorView(context)

		avatarContainer2?.addView(avatarsViewPagerIndicatorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		frameLayout.addView(actionBar)

		var rightMargin = (54 + if (callItemVisible && userId != 0L) 54 else 0).toFloat()
		var hasTitleExpanded = false
		val initialTitleWidth = LayoutHelper.WRAP_CONTENT

		(parentLayout?.lastFragment as? ChatActivity)?.avatarContainer?.let {
			hasTitleExpanded = it.titleTextView.paddingRight != 0

			if (it.layoutParams != null) {
				rightMargin = ((it.layoutParams as MarginLayoutParams).rightMargin + (it.width - it.titleTextView.right)) / AndroidUtilities.density
			}
		}

		for (a in nameTextView.indices) {
			if (playProfileAnimation == 0 && a == 0) {
				continue
			}

			nameTextView[a] = object : SimpleTextView(context) {
				override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
					super.onInitializeAccessibilityNodeInfo(info)

					if (isFocusable && nameTextViewRightDrawableContentDescription != null) {
						info.text = "${getText()}, $nameTextViewRightDrawableContentDescription"
					}
				}
			}

			nameTextView[a]!!.textColor = ResourcesCompat.getColor(context.resources, R.color.dark, null)
			nameTextView[a]!!.setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(if (a == 0) 12f else 4f))
			nameTextView[a]!!.setTextSize(18)
			nameTextView[a]!!.setGravity(Gravity.LEFT)
			nameTextView[a]!!.setTypeface(Theme.TYPEFACE_BOLD)
			nameTextView[a]!!.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f))
			nameTextView[a]!!.pivotX = 0f
			nameTextView[a]!!.pivotY = 0f
			nameTextView[a]!!.alpha = if (a == 0) 0.0f else 1.0f

			if (a == 1) {
				nameTextView[a]!!.setScrollNonFitText(true)
				nameTextView[a]!!.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
			}

			nameTextView[a]!!.isFocusable = a == 0
			nameTextView[a]!!.setEllipsizeByGradient(true)
			nameTextView[a]!!.rightDrawableOutside = a == 0

			avatarContainer2?.addView(nameTextView[a], LayoutHelper.createFrame(if (a == 0) initialTitleWidth else LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 118f, -6f, if (a == 0) rightMargin - (if (hasTitleExpanded) 10f else 0f) else 0f, 0f))
		}

		for (a in onlineTextView.indices) {
			onlineTextView[a] = SimpleTextView(context)
			onlineTextView[a]!!.setEllipsizeByGradient(true)

			if (a == 0) {
				onlineTextView[a]!!.textColor = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
			}
			else {
				onlineTextView[a]!!.textColor = ResourcesCompat.getColor(context.resources, R.color.disabled_text, null)
			}

			onlineTextView[a]!!.setTextSize(14)
			onlineTextView[a]!!.setGravity(Gravity.LEFT)
			onlineTextView[a]!!.alpha = if (a == 0 || a == 2) 0.0f else 1.0f

			if (a > 0) {
				onlineTextView[a]!!.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
			}

			onlineTextView[a]!!.isFocusable = a == 0

			avatarContainer2?.addView(onlineTextView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 118f, 0f, if (a == 0) rightMargin - 12f else 8f, 0f))
		}

		avatarContainer2?.addView(animatedStatusView)

		mediaCounterTextView = object : AudioPlayerAlert.ClippingTextViewSwitcher(context) {
			override fun createTextView(): TextView {
				val textView = TextView(context)
				textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
				textView.isSingleLine = true
				textView.ellipsize = TextUtils.TruncateAt.END
				textView.gravity = Gravity.LEFT
				return textView
			}
		}

		mediaCounterTextView?.alpha = 0.0f

		avatarContainer2?.addView(mediaCounterTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 118f, 0f, 8f, 0f))

		updateProfileData(true)

		writeButton = RLottieImageView(context)
		writeButton?.background = createWriteButtonBackground()

		if (userId != 0L) {
			if (imageUpdater != null) {
				cameraDrawable = RLottieDrawable(R.raw.camera_outline, R.raw.camera_outline.toString(), AndroidUtilities.dp(42f), AndroidUtilities.dp(42f), false, null)

				cellCameraDrawable = RLottieDrawable(R.raw.camera_outline, R.raw.camera_outline.toString() + "_cell", AndroidUtilities.dp(20f), AndroidUtilities.dp(20f), false, null)

				writeButton?.setAnimation(cameraDrawable!!)
				writeButton?.contentDescription = context.getString(R.string.AccDescrChangeProfilePicture)
				writeButton?.setPadding(AndroidUtilities.dp(2f), 0, 0, AndroidUtilities.dp(2f))

				//MARK: Hidden implementation of the gallery open button that was in frameLayout, instead the implementation has been moved to a separate container: VIEW_TYPE_SET_PROFILE_PHOTO
				writeButton?.gone()
			}
			else {
				writeButton?.setImageResource(R.drawable.message_circle)
				writeButton?.contentDescription = context.getString(R.string.AccDescrOpenChat)
			}
		}
		else {
			writeButton?.setImageResource(R.drawable.message_circle)
			writeButton?.contentDescription = context.getString(R.string.ViewDiscussion)
		}

		writeButton?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_fixed, null), PorterDuff.Mode.SRC_IN)
		writeButton?.scaleType = ImageView.ScaleType.CENTER

		writeButton?.outlineProvider = object : ViewOutlineProvider() {
			private val radius = AndroidUtilities.dp(18f).toFloat()

			override fun getOutline(view: View, outline: Outline) {
				outline.setRoundRect(0, 0, view.measuredWidth, view.measuredHeight, radius)
			}
		}

		val self = isSelf()

		if (!self) {
			ViewCompat.setElevation(writeButton!!, AndroidUtilities.dp(4f).toFloat())
		}

		val side = if (self) 42 else 60

		frameLayout.addView(writeButton, LayoutHelper.createFrame(side, side.toFloat(), Gravity.RIGHT or Gravity.TOP, 0f, 0f, 16f, 0f))

		writeButton?.setOnClickListener {
			if (writeButton?.tag != null) {
				return@setOnClickListener
			}

			onWriteButtonClick()
		}

		needLayout(false)

		if (scrollTo != -1) {
			if (writeButtonTag != null) {
				writeButton?.tag = 0
				writeButton?.scaleX = 0.2f
				writeButton?.scaleY = 0.2f
				writeButton?.alpha = 0.0f
			}
		}

		listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					parentActivity?.currentFocus?.let {
						AndroidUtilities.hideKeyboard(it)
					}
				}

				if (openingAvatar && newState != RecyclerView.SCROLL_STATE_SETTLING) {
					openingAvatar = false
				}

				if (searchItem != null) {
					scrolling = newState != RecyclerView.SCROLL_STATE_IDLE
					searchItem?.isEnabled = !scrolling && !isPulledDown
				}

				sharedMediaLayout?.scrollingByUser = listView?.scrollingByUser ?: false
			}

			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				fwdRestrictedHint?.hide()

				checkListViewScroll()

				if (participantsMap != null && !usersEndReached && layoutManager!!.findLastVisibleItemPosition() > membersEndRow - 8) {
					getChannelParticipants(false)
				}

				sharedMediaLayout?.isPinnedToTop = sharedMediaLayout!!.y == 0f
			}
		})

		undoView = UndoView(context, null, false)

		frameLayout.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM or Gravity.LEFT, 8f, 0f, 8f, 8f))

		expandAnimator = ValueAnimator.ofFloat(0f, 1f)

		expandAnimator?.addUpdateListener { anim ->
			val newTop = ActionBar.getCurrentActionBarHeight() + (if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0)

			currentExpandAnimatorValue = AndroidUtilities.lerp(expandAnimatorValues, anim.animatedFraction.also { currentExpandAnimatorFracture = it })

			val value = currentExpandAnimatorValue

			avatarContainer?.scaleX = avatarScale
			avatarContainer?.scaleY = avatarScale
			avatarContainer?.translationX = AndroidUtilities.lerp(avatarX, 0f, value)
			avatarContainer?.translationY = AndroidUtilities.lerp(ceil(avatarY.toDouble()).toFloat(), 0f, value)

			avatarImage?.setRoundRadius(AndroidUtilities.lerp(AndroidUtilities.dpf2(avatarSide * 0.45f), 0f, value).toInt())

			if (searchItem != null) {
				searchItem?.alpha = 1.0f - value
				searchItem?.scaleY = 1.0f - value
				searchItem?.visibility = View.VISIBLE
				searchItem?.isClickable = searchItem!!.alpha > .5f

				if (qrItem != null) {
					val translation = AndroidUtilities.dp(48f) * value
					qrItem?.translationX = translation
					avatarsViewPagerIndicatorView?.translationX = translation - AndroidUtilities.dp(48f)
				}
			}

			if (extraHeight > AndroidUtilities.dp(88f) && expandProgress < 0.33f) {
				refreshNameAndOnlineXY()
			}

			scamDrawable?.setColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))

			lockIconDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.MULTIPLY)

			verifiedCrossfadeDrawable?.progress = value

			premiumCrossfadeDrawable?.progress = value

			updateEmojiStatusDrawableColor(value)

			val k = AndroidUtilities.dpf2(8f)
			val nameTextViewXEnd = AndroidUtilities.dpf2(16f) - nameTextView[1]!!.left
			val nameTextViewYEnd = (newTop + extraHeight) - AndroidUtilities.dpf2(38f) - nameTextView[1]!!.bottom
			val nameTextViewCx = k + nameX + ((nameTextViewXEnd - nameX) / 2f)
			val nameTextViewCy = k + nameY + ((nameTextViewYEnd - nameY) / 2f)
			val nameTextViewX = ((1 - value) * (1 - value) * nameX) + (2 * (1 - value) * value * nameTextViewCx) + (value * value * nameTextViewXEnd)
			val nameTextViewY = ((1 - value) * (1 - value) * nameY) + (2 * (1 - value) * value * nameTextViewCy) + (value * value * nameTextViewYEnd)
			val onlineTextViewXEnd = AndroidUtilities.dpf2(16f) - onlineTextView[1]!!.left
			val onlineTextViewYEnd = (newTop + extraHeight) - AndroidUtilities.dpf2(18f) - onlineTextView[1]!!.bottom
			val onlineTextViewCx = k + onlineX + ((onlineTextViewXEnd - onlineX) / 2f)
			val onlineTextViewCy = k + onlineY + ((onlineTextViewYEnd - onlineY) / 2f)
			val onlineTextViewX = ((1 - value) * (1 - value) * onlineX) + (2 * (1 - value) * value * onlineTextViewCx) + (value * value * onlineTextViewXEnd)
			val onlineTextViewY = ((1 - value) * (1 - value) * onlineY) + (2 * (1 - value) * value * onlineTextViewCy) + (value * value * onlineTextViewYEnd)

			nameTextView[1]?.translationX = nameTextViewX
			nameTextView[1]?.translationY = nameTextViewY
			onlineTextView[1]?.translationX = onlineTextViewX
			onlineTextView[1]?.translationY = onlineTextViewY

			mediaCounterTextView?.translationX = onlineTextViewX
			mediaCounterTextView?.translationY = onlineTextViewY

			onlineTextView[1]?.textColor = ResourcesCompat.getColor(context.resources, R.color.white, null)

			if (extraHeight > AndroidUtilities.dp(88f)) {
				nameTextView[1]?.pivotY = AndroidUtilities.lerp(0, nameTextView[1]!!.measuredHeight, value).toFloat()
				nameTextView[1]?.scaleX = AndroidUtilities.lerp(1.12f, 1.67f, value)
				nameTextView[1]?.scaleY = AndroidUtilities.lerp(1.12f, 1.67f, value)
			}

			needLayoutText(min(1f, extraHeight / AndroidUtilities.dp(88f)))
			nameTextView[1]?.textColor = ResourcesCompat.getColor(context.resources, R.color.white, null)
			nameTextView[1]?.leftDrawable?.apply { setTint(context.getColor(R.color.white)) }
			actionBar?.setItemsColor(ResourcesCompat.getColor(context.resources, R.color.white, null), false)
			avatarImage?.setForegroundAlpha(value)

			val params = avatarContainer?.layoutParams as? FrameLayout.LayoutParams
			params?.width = AndroidUtilities.lerp(AndroidUtilities.dpf2(avatarSide), listView!!.measuredWidth / avatarScale, value).toInt()
			params?.height = AndroidUtilities.lerp(AndroidUtilities.dpf2(avatarSide), (extraHeight + newTop) / avatarScale, value).toInt()
			params?.leftMargin = AndroidUtilities.lerp(AndroidUtilities.dpf2(64f), 0f, value).toInt()

			avatarContainer?.requestLayout()
		}

		expandAnimator?.interpolator = CubicBezierInterpolator.EASE_BOTH

		expandAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				avatarImage?.clearForeground()

				doNotSetForeground = false

				if (isPulledDown) {
					actionBar?.setItemsBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.white, null), false)
					actionBar?.setItemsColor(ResourcesCompat.getColor(context.resources, R.color.white, null), false)
					nameTextView[1]?.textColor = ResourcesCompat.getColor(context.resources, R.color.white, null)
					nameTextView[1]?.leftDrawable?.apply { setTint(context.getColor(R.color.white)) }
					onlineTextView[1]?.textColor = ResourcesCompat.getColor(context.resources, R.color.white, null)

					if (messagesController.getUser(userId)?.id == userConfig.getClientUserId() && chatId == 0L) {
						onlineTextView[1]?.setText(getUserAccountTypeString())
					}
				}
				else {
					actionBar?.setItemsBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.action_bar_item, null), false)
					actionBar?.setItemsColor(ResourcesCompat.getColor(context.resources, R.color.brand, null), false)
					nameTextView[1]?.textColor = ResourcesCompat.getColor(context.resources, R.color.dark, null)
					nameTextView[1]?.leftDrawable?.apply { setTint(context.getColor(R.color.dark)) }
					onlineTextView[1]?.textColor = ResourcesCompat.getColor(context.resources, R.color.dark, null)

					if (messagesController.getUser(userId)?.id == userConfig.getClientUserId() && chatId == 0L) {
						onlineTextView[1]?.setText(getUserAccountTypeString())
					}
				}
			}
		})

		updateRowsIds()
		updateSelectedMediaTabText()

		fwdRestrictedHint = HintView(parentActivity, 9)
		fwdRestrictedHint?.alpha = 0f

		frameLayout.addView(fwdRestrictedHint, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 12f, 0f, 12f, 0f))

		sharedMediaLayout?.setForwardRestrictedHint(fwdRestrictedHint)

		val decorView = parentActivity?.window?.decorView as? ViewGroup

		pinchToZoomHelper = object : PinchToZoomHelper(decorView, frameLayout) {
			var statusBarPaint: Paint? = null

			override fun invalidateViews() {
				super.invalidateViews()

				fragmentView?.invalidate()

				for (i in 0 until avatarsViewPager!!.childCount) {
					avatarsViewPager?.getChildAt(i)?.invalidate()
				}

				writeButton?.invalidate()
			}

			override fun drawOverlays(canvas: Canvas, alpha: Float, parentOffsetX: Float, parentOffsetY: Float, clipTop: Float, clipBottom: Float) {
				if (alpha > 0) {
					AndroidUtilities.rectTmp.set(0f, 0f, avatarsViewPager!!.measuredWidth.toFloat(), (avatarsViewPager!!.measuredHeight + AndroidUtilities.dp(30f)).toFloat())
					canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (255 * alpha).toInt())

					avatarContainer2?.draw(canvas)

					if (actionBar!!.occupyStatusBar && !SharedConfig.noStatusBar) {
						if (statusBarPaint == null) {
							statusBarPaint = Paint()
							statusBarPaint?.color = ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.2f).toInt())
						}

						canvas.drawRect(actionBar!!.x, actionBar!!.y, actionBar!!.x + actionBar!!.measuredWidth, actionBar!!.y + AndroidUtilities.statusBarHeight, statusBarPaint!!)
					}

					canvas.save()
					canvas.translate(actionBar!!.x, actionBar!!.y)

					actionBar?.draw(canvas)

					canvas.restore()

					if (writeButton != null && writeButton!!.visibility == View.VISIBLE && writeButton!!.alpha > 0) {
						canvas.save()
						val s = 0.5f + 0.5f * alpha
						canvas.scale(s, s, writeButton!!.x + writeButton!!.measuredWidth / 2f, writeButton!!.y + writeButton!!.measuredHeight / 2f)
						canvas.translate(writeButton!!.x, writeButton!!.y)
						writeButton?.draw(canvas)
						canvas.restore()
					}

					canvas.restore()
				}
			}

			override fun zoomEnabled(child: View, receiver: ImageReceiver): Boolean {
				return if (!super.zoomEnabled(child, receiver)) {
					false
				}
				else {
					listView?.scrollState != RecyclerView.SCROLL_STATE_DRAGGING
				}
			}
		}

		pinchToZoomHelper?.setCallback(object : PinchToZoomHelper.Callback {
			override fun onZoomStarted(messageObject: MessageObject?) {
				listView?.cancelClickRunnables(true)

				sharedMediaLayout?.currentListView?.cancelClickRunnables(true)

				val bitmap = if (pinchToZoomHelper?.photoImage == null) null else pinchToZoomHelper?.photoImage?.bitmap

				if (bitmap != null) {
					topView?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.avatar_background, null))
				}
			}
		})

		avatarsViewPager?.pinchToZoomHelper = pinchToZoomHelper

		scrimPaint.alpha = 0

		actionBarBackgroundPaint.color = ResourcesCompat.getColor(context.resources, R.color.action_bar_item, null) // TODO: check color, was transparent black

		contentView?.blurBehindViews?.add(sharedMediaLayout)

		updateTtlIcon()

		return fragmentView
	}

	private fun loadBotUser(botId: Long = BuildConfig.AI_BOT_ID, onBotReady: () -> Unit) {
		val user = messagesController.getUser(botId)

		if (user != null) {
			shouldOpenBot = false
			onBotReady()
		}
		else {
			shouldOpenBot = true

			val botUser = TL_user()
			botUser.id = botId

			if (botId == BuildConfig.SUPPORT_BOT_ID) {
				botUser.username = "ElloSupport"
			}

			messagesController.loadFullUser(botUser, classGuid, true)
		}
	}

	private fun updateTtlIcon() {
		if (ttlIconView == null) {
			return
		}

		var visible = false

		if (currentEncryptedChat == null) {
			if (userInfo != null && userInfo!!.ttl_period > 0) {
				visible = true
			}
			else if (chatInfo != null && ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_DELETE_MESSAGES) && chatInfo!!.ttl_period > 0) {
				visible = true
			}
		}

		AndroidUtilities.updateViewVisibilityAnimated(ttlIconView, visible, 0.8f, fragmentOpened)
	}

	fun getDialogId(): Long {
		return if (dialogId != 0L) {
			dialogId
		}
		else if (userId != 0L) {
			userId
		}
		else {
			-chatId
		}
	}

	private fun getEmojiStatusLocation(rect: Rect) {
		val rightDrawable = nameTextView[1]?.rightDrawable

		if (rightDrawable == null) {
			rect.set(nameTextView[1]!!.width - 1, nameTextView[1]!!.height / 2 - 1, nameTextView[1]!!.width + 1, nameTextView[1]!!.height / 2 + 1)
			return
		}

		rect.set(rightDrawable.bounds)

		rect.offset((rect.centerX() * (nameTextView[1]!!.scaleX - 1f)).toInt(), 0)
		rect.offset(nameTextView[1]!!.x.toInt(), nameTextView[1]!!.y.toInt())
	}

	private fun showStatusSelect() {
		if (selectAnimatedEmojiDialog != null) {
			return
		}

		val popup = arrayOfNulls<SelectAnimatedEmojiDialogWindow>(1)
		val xoff: Int
		val yoff: Int

		getEmojiStatusLocation(AndroidUtilities.rectTmp2)

		val topMarginDp = if (nameTextView[1]!!.scaleX < 1.5f) 16 else 32
		yoff = -(avatarContainer2!!.height - AndroidUtilities.rectTmp2.centerY()) - AndroidUtilities.dp(topMarginDp.toFloat())

		val popupWidth = min(AndroidUtilities.dp((340 - 16).toFloat()).toFloat(), AndroidUtilities.displaySize.x * .95f).toInt()
		var ecenter = AndroidUtilities.rectTmp2.centerX()
		xoff = MathUtils.clamp(ecenter - popupWidth / 2, 0, AndroidUtilities.displaySize.x - popupWidth)
		ecenter -= xoff

		val popupLayout = object : SelectAnimatedEmojiDialog(this@ProfileActivity, context!!, true, max(0, ecenter), TYPE_EMOJI_STATUS, topMarginDp) {
			override fun onEmojiSelected(view: View, documentId: Long?, document: TLRPC.Document?, until: Int?) {
				val req = TLRPC.TL_account_updateEmojiStatus()

				if (documentId == null) {
					req.emoji_status = TLRPC.TL_emojiStatusEmpty()
				}
				else if (until != null) {
					req.emoji_status = TLRPC.TL_emojiStatusUntil()
					(req.emoji_status as TLRPC.TL_emojiStatusUntil).document_id = documentId
					(req.emoji_status as TLRPC.TL_emojiStatusUntil).until = until
				}
				else {
					req.emoji_status = TLRPC.TL_emojiStatus()
					(req.emoji_status as TLRPC.TL_emojiStatus).document_id = documentId
				}

				val user = MessagesController.getInstance(currentAccount).getUser(getInstance(currentAccount).getClientUserId())

				if (user != null) {
					user.emoji_status = req.emoji_status
					MessagesController.getInstance(currentAccount).updateEmojiStatusUntilUpdate(user.id, user.emoji_status)
					NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userEmojiStatusUpdated, user)
				}

				for (a in 0..1) {
					if (emojiStatusDrawable[a] != null) {
						if (documentId == null) {
							emojiStatusDrawable[a]!![getPremiumCrossfadeDrawable()] = true
						}
						else {
							emojiStatusDrawable[a]!![documentId] = true
						}
					}
				}

				if (documentId != null) {
					animatedStatusView?.animateChange(VisibleReaction.fromCustomEmoji(documentId))
				}

				updateEmojiStatusDrawableColor()
				updateEmojiStatusEffectPosition()

				connectionsManager.sendRequest(req) { res, _ ->
					if (res !is TLRPC.TL_boolTrue) {
						// TODO: reject
					}
				}

				if (popup[0] != null) {
					selectAnimatedEmojiDialog = null
					popup[0]?.dismiss()
				}
			}
		}

		val user = messagesController.getUser(userId)

		if (user != null && user.emoji_status is TLRPC.TL_emojiStatusUntil && (user.emoji_status as TLRPC.TL_emojiStatusUntil).until > (System.currentTimeMillis() / 1000).toInt()) {
			popupLayout.setExpireDateHint((user.emoji_status as TLRPC.TL_emojiStatusUntil).until)
		}

		popupLayout.setSelected(if (emojiStatusDrawable[1] != null && emojiStatusDrawable[1]!!.drawable is AnimatedEmojiDrawable) (emojiStatusDrawable[1]!!.drawable as AnimatedEmojiDrawable).documentId else null)
		popupLayout.setSaveState(3)
		popupLayout.setScrimDrawable(emojiStatusDrawable[1], nameTextView[1])

		selectAnimatedEmojiDialog = object : SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
			override fun dismiss() {
				super.dismiss()
				selectAnimatedEmojiDialog = null
			}
		}

		popup[0] = selectAnimatedEmojiDialog

		val loc = IntArray(2)

		nameTextView[1]?.getLocationOnScreen(loc)

		popup[0]?.showAsDropDown(fragmentView!!, xoff, yoff, Gravity.TOP or Gravity.LEFT)
		popup[0]?.dimBehind()
	}

	private fun openAvatar() {
		if (listView?.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
			return
		}

		if (userId != 0L) {
			val user = messagesController.getUser(userId)

			if (user?.photo != null && user.photo?.photo_big != null) {
				PhotoViewer.getInstance().setParentActivity(this@ProfileActivity)

				if (user.photo?.dc_id != 0) {
					user.photo?.photo_big?.dc_id = user.photo?.dc_id ?: 0
				}

				PhotoViewer.getInstance().openPhoto(user.photo?.photo_big, provider)
			}
		}
		else if (chatId != 0L) {
			val chat = messagesController.getChat(chatId)

			if (chat?.photo?.photo_big != null) {
				PhotoViewer.getInstance().setParentActivity(this@ProfileActivity)

				if (chat.photo.dc_id != 0) {
					chat.photo.photo_big.dc_id = chat.photo.dc_id
				}

				val videoLocation = if (chatInfo != null && chatInfo!!.chat_photo is TL_photo && chatInfo!!.chat_photo.video_sizes.isNotEmpty()) {
					ImageLocation.getForPhoto(chatInfo!!.chat_photo.video_sizes[0], chatInfo!!.chat_photo)
				}
				else {
					null
				}

				PhotoViewer.getInstance().openPhotoWithVideo(chat.photo.photo_big, videoLocation, provider)
			}
		}
	}

	private fun onWriteButtonClick() {
		if (userId != 0L) {
			if (imageUpdater != null) {
				var user = MessagesController.getInstance(currentAccount).getUser(getInstance(currentAccount).getClientUserId())

				if (user == null) {
					user = getInstance(currentAccount).getCurrentUser()
				}

				if (user == null) {
					return
				}

				imageUpdater?.openMenu(user.photo?.photo_big != null && user.photo !is TL_userProfilePhotoEmpty, {
					MessagesController.getInstance(currentAccount).deleteUserPhoto(null)
					cameraDrawable?.currentFrame = 0
					cellCameraDrawable?.currentFrame = 0
				}) {
					if (!imageUpdater!!.isUploadingImage) {
						cameraDrawable?.customEndFrame = 86
						cellCameraDrawable?.customEndFrame = 86
						writeButton?.playAnimation()
						setAvatarCell?.imageView?.playAnimation()
					}
					else {
						cameraDrawable!!.setCurrentFrame(0, false)
						cellCameraDrawable!!.setCurrentFrame(0, false)
					}
				}

				cameraDrawable?.currentFrame = 0
				cameraDrawable?.customEndFrame = 43

				cellCameraDrawable?.currentFrame = 0
				cellCameraDrawable?.customEndFrame = 43

				writeButton?.playAnimation()

				setAvatarCell?.imageView?.playAnimation()
			}
			else {
				if (playProfileAnimation != 0 && parentLayout!!.fragmentsStack[parentLayout!!.fragmentsStack.size - 2] is ChatActivity) {
					finishFragment()
				}
				else {
					val user = messagesController.getUser(userId)

					if (user == null || user is TLRPC.TL_userEmpty) {
						return
					}

					val args = Bundle()
					args.putLong("user_id", userId)

					if (!messagesController.checkCanOpenChat(args, this@ProfileActivity)) {
						return
					}

					val removeFragment = arguments!!.getBoolean("removeFragmentOnChatOpen", true)

					if (!AndroidUtilities.isTablet() && removeFragment) {
						notificationCenter.removeObserver(this@ProfileActivity, NotificationCenter.closeChats)
						notificationCenter.postNotificationName(NotificationCenter.closeChats)
					}

					val distance = arguments!!.getInt("nearby_distance", -1)

					if (distance >= 0) {
						args.putInt("nearby_distance", distance)
					}

					val chatActivity = ChatActivity(args)
					chatActivity.setPreloadedSticker(mediaDataController.getGreetingsSticker(), false)

					presentFragment(chatActivity, removeFragment)

					if (AndroidUtilities.isTablet()) {
						finishFragment()
					}
				}
			}
		}
		else {
			openDiscussion()
		}
	}

	private fun openDiscussion() {
		if (chatInfo == null || chatInfo!!.linked_chat_id == 0L) {
			return
		}

		val args = Bundle()
		args.putLong("chat_id", chatInfo!!.linked_chat_id)

		if (!messagesController.checkCanOpenChat(args, this@ProfileActivity)) {
			return
		}

		presentFragment(ChatActivity(args))
	}

	fun onMemberClick(participant: TLRPC.ChatParticipant?, isLong: Boolean): Boolean {
		return onMemberClick(participant, isLong, false)
	}

	override fun onMemberClick(participant: TLRPC.ChatParticipant?, b: Boolean, resultOnly: Boolean): Boolean {
		if (participant == null) {
			return false
		}

		val parentActivity = parentActivity ?: return false

		if (b) {
			val user = messagesController.getUser(participant.user_id)

			if (user == null || participant.user_id == userConfig.getClientUserId()) {
				return false
			}

			selectedUser = participant.user_id

			val allowKick: Boolean
			var canEditAdmin: Boolean
			var canRestrict: Boolean
			val editingAdmin: Boolean
			val channelParticipant: TLRPC.ChannelParticipant?

			if (ChatObject.isChannel(currentChat)) {
				channelParticipant = (participant as TLRPC.TL_chatChannelParticipant).channelParticipant

				canEditAdmin = ChatObject.canAddAdmins(currentChat)

				if (canEditAdmin && (channelParticipant is TLRPC.TL_channelParticipantCreator || channelParticipant is TLRPC.TL_channelParticipantAdmin && !channelParticipant.can_edit)) {
					canEditAdmin = false
				}

				canRestrict = ChatObject.canBlockUsers(currentChat) && (!(channelParticipant is TLRPC.TL_channelParticipantAdmin || channelParticipant is TLRPC.TL_channelParticipantCreator) || channelParticipant.can_edit)
				allowKick = canRestrict

				if (currentChat!!.gigagroup) {
					canRestrict = false
				}

				editingAdmin = channelParticipant is TLRPC.TL_channelParticipantAdmin
			}
			else {
				channelParticipant = null
				allowKick = currentChat!!.creator || participant is TLRPC.TL_chatParticipant && (ChatObject.canBlockUsers(currentChat) || participant.inviter_id == userConfig.getClientUserId())
				canEditAdmin = currentChat!!.creator
				canRestrict = currentChat!!.creator
				editingAdmin = participant is TLRPC.TL_chatParticipantAdmin
			}

			val items = if (resultOnly) null else ArrayList<String>()
			val icons = if (resultOnly) null else ArrayList<Int>()
			val actions = if (resultOnly) null else ArrayList<Int>()
			var hasRemove = false

			if (canEditAdmin) {
				if (resultOnly) {
					return true
				}

				items!!.add(if (editingAdmin) parentActivity.getString(R.string.EditAdminRights) else parentActivity.getString(R.string.SetAsAdmin))
				icons!!.add(R.drawable.msg_admins)
				actions!!.add(0)
			}

			// MARK: remove `isMegagroup` check to enable changing permissions
			if (canRestrict && !ChatObject.isMegagroup(currentChat)) {
				if (resultOnly) {
					return true
				}

				items!!.add(parentActivity.getString(R.string.ChangePermissions))
				icons!!.add(R.drawable.msg_permissions)
				actions!!.add(1)
			}

			if (allowKick) {
				if (resultOnly) {
					return true
				}

				items!!.add(parentActivity.getString(R.string.KickFromGroup))
				icons!!.add(R.drawable.msg_remove)
				actions!!.add(2)
				hasRemove = true
			}

			if (resultOnly) {
				return false
			}

			if (items!!.isEmpty()) {
				return false
			}

			val builder = AlertDialog.Builder(parentActivity)

			builder.setItems(items.toTypedArray(), AndroidUtilities.toIntArray(icons)) { _, i ->
				if (actions!![i] == 2) {
					kickUser(selectedUser, participant)
				}
				else {
					val action = actions[i]

					if (action == 1 && (channelParticipant is TLRPC.TL_channelParticipantAdmin || participant is TLRPC.TL_chatParticipantAdmin)) {
						val builder2 = AlertDialog.Builder(parentActivity)
						builder2.setTitle(parentActivity.getString(R.string.AppName))
						builder2.setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, ContactsController.formatName(user.first_name, user.last_name)))

						builder2.setPositiveButton(parentActivity.getString(R.string.OK)) { _, _ ->
							if (channelParticipant != null) {
								openRightsEdit(action, user, participant, channelParticipant.admin_rights, channelParticipant.banned_rights, channelParticipant.rank, editingAdmin)
							}
							else {
								openRightsEdit(action, user, participant, null, null, "", editingAdmin)
							}
						}

						builder2.setNegativeButton(parentActivity.getString(R.string.Cancel), null)
						showDialog(builder2.create())
					}
					else {
						if (channelParticipant != null) {
							openRightsEdit(action, user, participant, channelParticipant.admin_rights, channelParticipant.banned_rights, channelParticipant.rank, editingAdmin)
						}
						else {
							openRightsEdit(action, user, participant, null, null, "", editingAdmin)
						}
					}
				}
			}

			val alertDialog = builder.create()
			showDialog(alertDialog)

			if (hasRemove) {
				alertDialog.setItemColor(items.size - 1, ResourcesCompat.getColor(parentActivity.resources, R.color.purple, null), ResourcesCompat.getColor(parentActivity.resources, R.color.purple, null))
			}
		}
		else {
			if (participant.user_id == userConfig.getClientUserId()) {
				return false
			}

			val user = messagesController.getUser(participant.user_id)

			if (user != null && !user.is_public) {
				val builder2 = AlertDialog.Builder(parentActivity)
				builder2.setTitle(parentActivity.getString(R.string.AppName))
				builder2.setMessage(parentActivity.getString(R.string.user_is_private))
				builder2.setPositiveButton(parentActivity.getString(R.string.OK), null)
				builder2.show()
				return false
			}

			if (user == null) {
				ioScope.launch {
					val userInner = messagesController.loadUser(participant.user_id, classGuid, true)

					mainScope.launch {
						if (userInner != null && !userInner.is_public) {
							val builder2 = AlertDialog.Builder(parentActivity)
							builder2.setTitle(parentActivity.getString(R.string.AppName))
							builder2.setMessage(parentActivity.getString(R.string.user_is_private))
							builder2.setPositiveButton(parentActivity.getString(R.string.OK), null)
							builder2.show()
						}
						else {
							val args = Bundle()
							args.putLong("user_id", userInner?.id ?: participant.user_id)
							args.putBoolean("preload_messages", true)
							presentFragment(ProfileActivity(args))
						}
					}
				}

				return false
			}
			else {
				val args = Bundle()
				args.putLong("user_id", participant.user_id)
				args.putBoolean("preload_messages", true)
				presentFragment(ProfileActivity(args))
			}
		}

		return true
	}

	private fun openRightsEdit(action: Int, user: User, participant: TLRPC.ChatParticipant?, adminRights: TLRPC.TL_chatAdminRights?, bannedRights: TL_chatBannedRights?, rank: String?, editingAdmin: Boolean) {
		val needShowBulletin = BooleanArray(1)

		val fragment = object : ChatRightsEditActivity(user.id, chatId, adminRights, currentChat?.default_banned_rights, bannedRights, rank, action, true, false, null) {
			override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
				if (!isOpen && backward && needShowBulletin[0] && BulletinFactory.canShowBulletin(this@ProfileActivity)) {
					BulletinFactory.createPromoteToAdminBulletin(this@ProfileActivity, user.first_name).show()
				}
			}
		}

		fragment.setDelegate(object : ChatRightsEditActivityDelegate {
			override fun didSetRights(rights: Int, rightsAdmin: TLRPC.TL_chatAdminRights?, rightsBanned: TL_chatBannedRights?, rank: String?) {
				if (action == 0) {
					if (participant is TLRPC.TL_chatChannelParticipant) {
						if (rights == 1) {
							participant.channelParticipant = TLRPC.TL_channelParticipantAdmin()
							participant.channelParticipant.flags = participant.channelParticipant.flags or 4
						}
						else {
							participant.channelParticipant = TLRPC.TL_channelParticipant()
						}

						participant.channelParticipant.inviter_id = userConfig.getClientUserId()
						participant.channelParticipant.peer = TLRPC.TL_peerUser()
						participant.channelParticipant.peer.user_id = participant.user_id
						participant.channelParticipant.date = participant.date
						participant.channelParticipant.banned_rights = rightsBanned
						participant.channelParticipant.admin_rights = rightsAdmin
						participant.channelParticipant.rank = rank
					}
					else if (participant != null) {
						val newParticipant: TLRPC.ChatParticipant = if (rights == 1) {
							TLRPC.TL_chatParticipantAdmin()
						}
						else {
							TLRPC.TL_chatParticipant()
						}

						newParticipant.user_id = participant.user_id
						newParticipant.date = participant.date
						newParticipant.inviter_id = participant.inviter_id

						val index = chatInfo?.participants?.participants?.indexOf(participant) ?: -1

						if (index >= 0) {
							chatInfo!!.participants.participants[index] = newParticipant
						}
					}

					if (rights == 1 && !editingAdmin) {
						needShowBulletin[0] = true
					}
				}
				else if (action == 1) {
					if (rights == 0) {
						if (currentChat!!.megagroup && chatInfo != null && chatInfo!!.participants != null) {
							var changed = false

							for (a in chatInfo!!.participants.participants.indices) {
								val p = (chatInfo!!.participants.participants[a] as TLRPC.TL_chatChannelParticipant).channelParticipant

								if (MessageObject.getPeerId(p.peer) == participant!!.user_id) {
									chatInfo!!.participants_count--
									chatInfo!!.participants.participants.removeAt(a)
									changed = true
									break
								}
							}

							if (chatInfo != null && chatInfo!!.participants != null) {
								for (a in chatInfo!!.participants.participants.indices) {
									val p = chatInfo!!.participants.participants[a]

									if (p.user_id == participant!!.user_id) {
										chatInfo!!.participants.participants.removeAt(a)
										changed = true
										break
									}
								}
							}

							if (changed) {
								updateOnlineCount(true)
								updateRowsIds()
								listAdapter?.notifyDataSetChanged()
							}
						}
					}
				}
			}

			override fun didChangeOwner(user: User) {
				undoView?.showWithAction(-chatId, if (currentChat!!.megagroup) UndoView.ACTION_OWNER_TRANSFERED_GROUP else UndoView.ACTION_OWNER_TRANSFERED_CHANNEL, user)
			}
		})

		presentFragment(fragment)
	}

	private fun processOnClickOrPress(position: Int, view: View): Boolean {
		when (position) {
			usernameRow, setUsernameRow -> {
				val username = if (userId != 0L) {
					val user = messagesController.getUser(userId)

					if (user?.username == null) {
						return false
					}

					user.username
				}
				else if (chatId != 0L) {
					val chat = messagesController.getChat(chatId)

					if (chat?.username == null) {
						return false
					}

					chat.username
				}
				else {
					return false
				}

				if (userId == 0L) {
					val link = "https://" + MessagesController.getInstance(UserConfig.selectedAccount).linkPrefix + "/" + username

					showDialog(object : ShareAlert(parentActivity!!, null, link, false, link, false) {
						override fun onSend(dids: LongSparseArray<TLRPC.Dialog>, count: Int) {
							AndroidUtilities.runOnUIThread({
								contentView?.let {
									BulletinFactory.createInviteSentBulletin(parentActivity!!, it, dids.size(), if (dids.size() == 1) dids.valueAt(0).id else 0, ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.white, null)).show()
								}
							}, 250)
						}
					})
				}
				else {
					try {
						val clipboard = ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
						val text = "@$username"
						BulletinFactory.of(this).createCopyBulletin(view.context.getString(R.string.UsernameCopied)).show()
						val clip = ClipData.newPlainText("label", text)
						clipboard.setPrimaryClip(clip)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				return true
			}

			channelInfoRow, userInfoRow, locationRow, bioRow -> {
				if (position == bioRow && userInfo?.about.isNullOrBlank() && messagesController.getUser(userId)?.bot_description.isNullOrBlank()) {
					return false
				}

				if (view is AboutLinkCell) {
					return false
				}

				val text = if (position == locationRow) {
					if (chatInfo != null && chatInfo!!.location is TLRPC.TL_channelLocation) (chatInfo!!.location as TLRPC.TL_channelLocation).address else null
				}
				else if (position == channelInfoRow) {
					if (chatInfo != null) chatInfo!!.about else null
				}
				else {
					if (userInfo != null) userInfo!!.about else null
				}

				if (text.isNullOrEmpty()) {
					return false
				}

				val fromLanguage = arrayOfNulls<String>(1)
				fromLanguage[0] = "und"

				val translateButtonEnabled = MessagesController.getGlobalMainSettings().getBoolean("translate_button", false)

				val withTranslate = BooleanArray(1)
				withTranslate[0] = position == bioRow || position == channelInfoRow || position == userInfoRow

				val toLang = LocaleController.getInstance().currentLocale.language

				val showMenu = Runnable {
					val parentActivity = parentActivity ?: return@Runnable

					val builder = AlertDialog.Builder(parentActivity)

					builder.setItems(if (withTranslate[0]) arrayOf(parentActivity.getString(R.string.Copy), parentActivity.getString(R.string.TranslateMessage)) else arrayOf(parentActivity.getString(R.string.Copy)), if (withTranslate[0]) intArrayOf(R.drawable.msg_copy, R.drawable.msg_translate) else intArrayOf(R.drawable.msg_copy)) { _, i ->
						try {
							if (i == 0) {
								AndroidUtilities.addToClipboard(text)

								if (position == bioRow) {
									BulletinFactory.of(this).createCopyBulletin(parentActivity.getString(R.string.BioCopied)).show()
								}
								else {
									BulletinFactory.of(this).createCopyBulletin(parentActivity.getString(R.string.TextCopied)).show()
								}
							}
							else if (i == 1) {
								TranslateAlert.showAlert(fragmentView!!.context, this, fromLanguage[0], toLang, text, false, { span: URLSpan? ->
									if (span != null) {
										openUrl(span.url)
										return@showAlert true
									}
									false
								}, null)
							}
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					showDialog(builder.create())
				}

				if (withTranslate[0]) {
					if (LanguageDetector.hasSupport()) {
						LanguageDetector.detectLanguage(text, { fromLang: String? ->
							fromLanguage[0] = fromLang
							withTranslate[0] = (fromLang != null) && (fromLang != toLang || (fromLang == "und")) && (translateButtonEnabled && !RestrictedLanguagesSelectActivity.getRestrictedLanguages().contains(fromLang) || (currentChat != null && (currentChat!!.has_link || currentChat!!.username != null)) && (("uk" == fromLang) || ("ru" == fromLang)))
							showMenu.run()
						}) { error ->
							FileLog.e("mlkit: failed to detect language in selection", error)
							showMenu.run()
						}
					}
					else {
						showMenu.run()
					}
				}
				else {
					showMenu.run()
				}

				return view !is AboutLinkCell
			}

			else -> {
				return false
			}
		}
	}

	private fun leaveChatPressed() {
		AlertsCreator.createClearOrDeleteDialogAlert(this@ProfileActivity, false, currentChat, null, secret = false, checkDeleteForAll = false, canDeleteHistory = true) { param ->
			playProfileAnimation = 0
			notificationCenter.removeObserver(this@ProfileActivity, NotificationCenter.closeChats)
			notificationCenter.postNotificationName(NotificationCenter.closeChats)
			finishFragment()
			notificationCenter.postNotificationName(NotificationCenter.needDeleteDialog, -currentChat!!.id, null, currentChat, param)
		}
	}

	private fun getChannelParticipants(reload: Boolean) {
		if (loadingUsers || participantsMap == null || chatInfo == null) {
			return
		}

		loadingUsers = true

		val delay = if (participantsMap!!.size() != 0 && reload) 300 else 0

		val req = TLRPC.TL_channels_getParticipants()
		req.channel = messagesController.getInputChannel(chatId)
		req.filter = TLRPC.TL_channelParticipantsRecent()
		req.offset = if (reload) 0 else participantsMap!!.size()
		req.limit = 200

		val reqId = connectionsManager.sendRequest(req) { response, error ->
			AndroidUtilities.runOnUIThread({
				if (error == null) {
					val res = response as TL_channels_channelParticipants
					messagesController.putUsers(res.users, false)
					messagesController.putChats(res.chats, false)

					if (res.users.size < 200) {
						usersEndReached = true
					}

					if (req.offset == 0) {
						participantsMap!!.clear()
						chatInfo!!.participants = TLRPC.TL_chatParticipants()
						messagesStorage.putUsersAndChats(res.users, res.chats, true, true)
						messagesStorage.updateChannelUsers(chatId, res.participants)
					}

					for (a in res.participants.indices) {
						val participant = TLRPC.TL_chatChannelParticipant()
						participant.channelParticipant = res.participants[a]
						participant.inviter_id = participant.channelParticipant.inviter_id
						participant.user_id = MessageObject.getPeerId(participant.channelParticipant.peer)
						participant.date = participant.channelParticipant.date

						if (participantsMap!!.indexOfKey(participant.user_id) < 0) {
							if (chatInfo?.participants == null) {
								chatInfo?.participants = TLRPC.TL_chatParticipants()
							}

							chatInfo?.participants?.participants?.add(participant)

							participantsMap?.put(participant.user_id, participant)
						}
					}
				}

				loadingUsers = false

				updateListAnimated(true)
			}, delay.toLong())
		}

		connectionsManager.bindRequestToGuid(reqId, classGuid)
	}

	private fun setMediaHeaderVisible(visible: Boolean) {
		if (mediaHeaderVisible == visible) {
			return
		}

		mediaHeaderVisible = visible
		headerAnimatorSet?.cancel()
		headerShadowAnimatorSet?.cancel()

		val mediaSearchItem = sharedMediaLayout!!.searchItem

		if (!mediaHeaderVisible) {
			if (callItemVisible) {
				callItem?.visibility = View.VISIBLE
			}

			if (videoCallItemVisible) {
				videoCallItem?.visibility = View.VISIBLE
			}

			if (editItemVisible) {
				editItem?.visibility = View.VISIBLE
			}

			otherItem?.visibility = View.VISIBLE
		}
		else {
			if (sharedMediaLayout!!.isSearchItemVisible) {
				mediaSearchItem?.visible()
			}

			if (sharedMediaLayout!!.isCalendarItemVisible) {
				sharedMediaLayout!!.photoVideoOptionsItem.visibility = View.VISIBLE
			}
			else {
				sharedMediaLayout!!.photoVideoOptionsItem.visibility = View.INVISIBLE
			}
		}

		actionBar?.createMenu()?.requestLayout()

		val animators = ArrayList<Animator>()
		animators.add(ObjectAnimator.ofFloat(callItem, View.ALPHA, if (visible) 0.0f else 1.0f))
		animators.add(ObjectAnimator.ofFloat(videoCallItem, View.ALPHA, if (visible) 0.0f else 1.0f))
		animators.add(ObjectAnimator.ofFloat(otherItem, View.ALPHA, if (visible) 0.0f else 1.0f))
		animators.add(ObjectAnimator.ofFloat(editItem, View.ALPHA, if (visible) 0.0f else 1.0f))
		animators.add(ObjectAnimator.ofFloat(callItem, View.TRANSLATION_Y, if (visible) -AndroidUtilities.dp(10f).toFloat() else 0.0f))
		animators.add(ObjectAnimator.ofFloat(videoCallItem, View.TRANSLATION_Y, if (visible) -AndroidUtilities.dp(10f).toFloat() else 0.0f))
		animators.add(ObjectAnimator.ofFloat(otherItem, View.TRANSLATION_Y, if (visible) -AndroidUtilities.dp(10f).toFloat() else 0.0f))
		animators.add(ObjectAnimator.ofFloat(editItem, View.TRANSLATION_Y, if (visible) -AndroidUtilities.dp(10f).toFloat() else 0.0f))
		animators.add(ObjectAnimator.ofFloat(mediaSearchItem, View.ALPHA, if (visible) 1.0f else 0.0f))
		animators.add(ObjectAnimator.ofFloat(mediaSearchItem, View.TRANSLATION_Y, if (visible) 0.0f else AndroidUtilities.dp(10f).toFloat()))
		animators.add(ObjectAnimator.ofFloat(sharedMediaLayout!!.photoVideoOptionsItem, View.ALPHA, if (visible) 1.0f else 0.0f))
		animators.add(ObjectAnimator.ofFloat(sharedMediaLayout!!.photoVideoOptionsItem, View.TRANSLATION_Y, if (visible) 0.0f else AndroidUtilities.dp(10f).toFloat()))
		animators.add(ObjectAnimator.ofFloat(actionBar, actionBarHeaderProgress, if (visible) 1.0f else 0.0f))
		animators.add(ObjectAnimator.ofFloat(onlineTextView[1], View.ALPHA, if (visible) 0.0f else 1.0f))
		animators.add(ObjectAnimator.ofFloat(mediaCounterTextView, View.ALPHA, if (visible) 1.0f else 0.0f))

		if (visible) {
			animators.add(ObjectAnimator.ofFloat(this, headerShadow, 0.0f))
		}

		headerAnimatorSet = AnimatorSet()
		headerAnimatorSet?.playTogether(animators)
		headerAnimatorSet?.interpolator = CubicBezierInterpolator.DEFAULT

		headerAnimatorSet?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				if (headerAnimatorSet != null) {
					if (mediaHeaderVisible) {
						if (callItemVisible) {
							callItem?.visibility = View.GONE
						}

						if (videoCallItemVisible) {
							videoCallItem?.visibility = View.GONE
						}

						if (editItemVisible) {
							editItem?.visibility = View.GONE
						}

						otherItem?.visibility = View.GONE
					}
					else {
						if (sharedMediaLayout!!.isSearchItemVisible) {
							mediaSearchItem?.visible()
						}

						sharedMediaLayout?.photoVideoOptionsItem?.visibility = View.INVISIBLE

						headerShadowAnimatorSet = AnimatorSet()
						headerShadowAnimatorSet?.playTogether(ObjectAnimator.ofFloat(this@ProfileActivity, headerShadow, 1.0f))
						headerShadowAnimatorSet?.duration = 100

						headerShadowAnimatorSet?.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								headerShadowAnimatorSet = null
							}
						})

						headerShadowAnimatorSet?.start()
					}
				}

				headerAnimatorSet = null
			}

			override fun onAnimationCancel(animation: Animator) {
				headerAnimatorSet = null
			}
		})

		headerAnimatorSet?.duration = 150
		headerAnimatorSet?.start()

		NotificationCenter.globalInstance.postNotificationName(NotificationCenter.needCheckSystemBarColors, true)
	}

	private fun openAddMember() {
		val args = Bundle()
		args.putBoolean("addToGroup", true)
		args.putLong("chatId", currentChat!!.id)

		val fragment = GroupCreateActivity(args)
		fragment.info = chatInfo

		if (chatInfo?.participants != null) {
			val users = LongSparseArray<TLObject?>()

			chatInfo?.participants?.participants?.forEach {
				users.put(it.user_id, null)
			}

			fragment.ignoreUsers = users
		}

		fragment.setDelegate(object : GroupCreateActivity.ContactsAddActivityDelegate {
			override fun didSelectUsers(users: List<User>, fwdCount: Int) {
				val currentParticipants = HashSet<Long>()

				if (chatInfo?.participants?.participants != null) {
					for (i in chatInfo!!.participants.participants.indices) {
						currentParticipants.add(chatInfo!!.participants.participants[i].user_id)
					}
				}

				var a = 0
				val n = users.size

				while (a < n) {
					val user = users[a]

					messagesController.addUserToChat(chatId, user, fwdCount, null, this@ProfileActivity, null)

					if (!currentParticipants.contains(user.id)) {
						if (chatInfo?.participants == null) {
							chatInfo?.participants = TLRPC.TL_chatParticipants()
						}

						if (ChatObject.isChannel(currentChat)) {
							val channelParticipant1 = TLRPC.TL_chatChannelParticipant()
							channelParticipant1.channelParticipant = TLRPC.TL_channelParticipant()
							channelParticipant1.channelParticipant.inviter_id = userConfig.getClientUserId()
							channelParticipant1.channelParticipant.peer = TLRPC.TL_peerUser()
							channelParticipant1.channelParticipant.peer.user_id = user.id
							channelParticipant1.channelParticipant.date = connectionsManager.currentTime
							channelParticipant1.user_id = user.id

							chatInfo?.participants?.participants?.add(channelParticipant1)
						}
						else {
							val participant = TLRPC.TL_chatParticipant()
							participant.user_id = user.id
							participant.inviter_id = accountInstance.userConfig.clientUserId
							chatInfo?.participants?.participants?.add(participant)
						}

						chatInfo!!.participants_count++

						messagesController.putUser(user, false)
					}

					a++
				}

				updateListAnimated(true)
			}
		})

		presentFragment(fragment)
	}

	private fun checkListViewScroll() {
		if (listView?.visibility != View.VISIBLE) {
			return
		}

		if (sharedMediaLayoutAttached) {
			sharedMediaLayout?.setVisibleHeight(listView!!.measuredHeight - sharedMediaLayout!!.top)
		}

		if (listView!!.childCount <= 0 || openAnimationInProgress) {
			return
		}

		var newOffset = 0
		var child: View? = null

		for (i in 0 until listView!!.childCount) {
			if (listView!!.getChildAdapterPosition(listView!!.getChildAt(i)) == 0) {
				child = listView!!.getChildAt(i)
				break
			}
		}

		var holder = if (child == null) null else listView!!.findContainingViewHolder(child) as RecyclerListView.Holder?
		val top = child?.top ?: 0
		val adapterPosition = holder?.adapterPosition ?: RecyclerView.NO_POSITION

		if (top >= 0 && adapterPosition == 0) {
			newOffset = top
		}

		val mediaHeaderVisible: Boolean
		val searchVisible = imageUpdater == null && actionBar!!.isSearchFieldVisible

		if (sharedMediaRow != -1 && !searchVisible) {
			holder = listView?.findViewHolderForAdapterPosition(sharedMediaRow) as? RecyclerListView.Holder
			mediaHeaderVisible = holder != null && holder.itemView.top <= 0
		}
		else {
			mediaHeaderVisible = searchVisible
		}

		setMediaHeaderVisible(mediaHeaderVisible)

		if (extraHeight != newOffset.toFloat()) {
			extraHeight = newOffset.toFloat()

			topView?.invalidate()

			if (playProfileAnimation != 0) {
				allowProfileAnimation = extraHeight != 0f
			}

			needLayout(true)
		}
	}

	override fun updateSelectedMediaTabText() {
		if (sharedMediaLayout == null || mediaCounterTextView == null) {
			return
		}

		val id = sharedMediaLayout!!.closestTab
		val mediaCount = sharedMediaPreloader!!.lastMediaCount

		if (id == 0) {
			if (mediaCount[MediaDataController.MEDIA_VIDEOS_ONLY] == 0 && mediaCount[MediaDataController.MEDIA_PHOTOS_ONLY] == 0) {
				mediaCounterTextView!!.setText(LocaleController.formatPluralString("Media", mediaCount[MediaDataController.MEDIA_PHOTOVIDEO]))
			}
			else if (sharedMediaLayout!!.getPhotosVideosTypeFilter() == SharedMediaLayout.FILTER_PHOTOS_ONLY || mediaCount[MediaDataController.MEDIA_VIDEOS_ONLY] == 0) {
				mediaCounterTextView!!.setText(LocaleController.formatPluralString("Photos", mediaCount[MediaDataController.MEDIA_PHOTOS_ONLY]))
			}
			else if (sharedMediaLayout!!.getPhotosVideosTypeFilter() == SharedMediaLayout.FILTER_VIDEOS_ONLY || mediaCount[MediaDataController.MEDIA_PHOTOS_ONLY] == 0) {
				mediaCounterTextView!!.setText(LocaleController.formatPluralString("Videos", mediaCount[MediaDataController.MEDIA_VIDEOS_ONLY]))
			}
			else {
				val str = String.format("%s, %s", LocaleController.formatPluralString("Photos", mediaCount[MediaDataController.MEDIA_PHOTOS_ONLY]), LocaleController.formatPluralString("Videos", mediaCount[MediaDataController.MEDIA_VIDEOS_ONLY]))
				mediaCounterTextView!!.setText(str)
			}
		}
		else if (id == 1) {
			mediaCounterTextView!!.setText(LocaleController.formatPluralString("Files", mediaCount[MediaDataController.MEDIA_FILE]))
		}
		else if (id == 2) {
			mediaCounterTextView!!.setText(LocaleController.formatPluralString("Voice", mediaCount[MediaDataController.MEDIA_AUDIO]))
		}
		else if (id == 3) {
			mediaCounterTextView!!.setText(LocaleController.formatPluralString("Links", mediaCount[MediaDataController.MEDIA_URL]))
		}
		else if (id == 4) {
			mediaCounterTextView!!.setText(LocaleController.formatPluralString("MusicFiles", mediaCount[MediaDataController.MEDIA_MUSIC]))
		}
		else if (id == 5) {
			mediaCounterTextView!!.setText(LocaleController.formatPluralString("GIFs", mediaCount[MediaDataController.MEDIA_GIF]))
		}
		else if (id == 6) {
			mediaCounterTextView!!.setText(LocaleController.formatPluralString("CommonGroups", userInfo!!.common_chats_count))
		}
		else if (id == 7) {
			mediaCounterTextView!!.setText(onlineTextView[1]!!.getText())
		}
	}

	private fun needLayout(animated: Boolean) {
		val parentActivity = parentActivity ?: return
		val newTop = (if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0) + ActionBar.getCurrentActionBarHeight()
		val layoutParams: FrameLayout.LayoutParams

		if (listView != null && !openAnimationInProgress) {
			layoutParams = listView!!.layoutParams as FrameLayout.LayoutParams

			if (layoutParams.topMargin != newTop) {
				layoutParams.topMargin = newTop
				listView!!.layoutParams = layoutParams
			}
		}

		if (avatarContainer != null) {
			val diff = min(1f, extraHeight / AndroidUtilities.dp(88f))

			listView!!.topGlowOffset = extraHeight.toInt()
			listView!!.overScrollMode = if (extraHeight > AndroidUtilities.dp(88f) && extraHeight < listView!!.measuredWidth - newTop) View.OVER_SCROLL_NEVER else View.OVER_SCROLL_ALWAYS

			if (writeButton != null) {
				writeButton?.translationY = (if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0) + ActionBar.getCurrentActionBarHeight() + extraHeight + searchTransitionOffset - AndroidUtilities.dp(29.5f)

				val self = isSelf()

				if (self) {
					writeButton!!.translationY -= AndroidUtilities.dp(32f)
				}

				if (!openAnimationInProgress) {
					var setVisible = diff > 0.2f && !searchMode && (imageUpdater == null || setAvatarRow == -1)

					if (setVisible && chatId != 0L) {
						setVisible = ChatObject.isChannel(currentChat) && !currentChat!!.megagroup && chatInfo != null && chatInfo!!.linked_chat_id != 0L
					}

					val currentVisible = writeButton!!.tag == null

					if (setVisible != currentVisible) {
						if (setVisible) {
							writeButton?.tag = null
						}
						else {
							writeButton?.tag = 0
						}

						if (writeButtonAnimation != null) {
							val old = writeButtonAnimation
							writeButtonAnimation = null
							old?.cancel()
						}

						if (animated) {
							writeButtonAnimation = AnimatorSet()

							if (setVisible) {
								writeButtonAnimation!!.interpolator = DecelerateInterpolator()
								writeButtonAnimation!!.playTogether(ObjectAnimator.ofFloat(writeButton, View.SCALE_X, 1.0f), ObjectAnimator.ofFloat(writeButton, View.SCALE_Y, 1.0f), ObjectAnimator.ofFloat(writeButton, View.ALPHA, 1.0f))
							}
							else {
								writeButtonAnimation!!.interpolator = AccelerateInterpolator()
								writeButtonAnimation!!.playTogether(ObjectAnimator.ofFloat(writeButton, View.SCALE_X, 0.2f), ObjectAnimator.ofFloat(writeButton, View.SCALE_Y, 0.2f), ObjectAnimator.ofFloat(writeButton, View.ALPHA, 0.0f))
							}

							writeButtonAnimation!!.duration = 150
							writeButtonAnimation!!.addListener(object : AnimatorListenerAdapter() {
								override fun onAnimationEnd(animation: Animator) {
									if (writeButtonAnimation != null && writeButtonAnimation == animation) {
										writeButtonAnimation = null
									}
								}
							})

							writeButtonAnimation!!.start()
						}
						else {
							writeButton!!.scaleX = if (setVisible) 1.0f else 0.2f
							writeButton!!.scaleY = if (setVisible) 1.0f else 0.2f
							writeButton!!.alpha = if (setVisible) 1.0f else 0.0f
						}
					}

					if (qrItem != null) {
						updateQrItemVisibility(animated)

						if (!animated) {
							val translation = AndroidUtilities.dp(48f) * qrItem!!.alpha
							qrItem?.translationX = translation
							avatarsViewPagerIndicatorView?.translationX = translation - AndroidUtilities.dp(48f)
						}
					}
				}
			}

			avatarX = -AndroidUtilities.dpf2(47f) * diff
			avatarY = (if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0) + ActionBar.getCurrentActionBarHeight() / 2.0f * (1.0f + diff) - 21 * AndroidUtilities.density + 27 * AndroidUtilities.density * diff + actionBar!!.translationY

			val h = if (openAnimationInProgress) initialAnimationExtraHeight else extraHeight

			if (h > AndroidUtilities.dp(88f) || isPulledDown) {
				expandProgress = max(0f, min(1f, (h - AndroidUtilities.dp(88f)) / (listView!!.measuredWidth - newTop - AndroidUtilities.dp(88f))))
				avatarScale = AndroidUtilities.lerp((avatarSide + 18f) / avatarSide, (avatarSide + avatarSide + 18f) / avatarSide, min(1f, expandProgress * 3f))

				val durationFactor = min(AndroidUtilities.dpf2(2000f), max(AndroidUtilities.dpf2(1100f), abs(listViewVelocityY))) / AndroidUtilities.dpf2(1100f)

				if (allowPullingDown && (openingAvatar || expandProgress >= 0.33f)) {
					if (!isPulledDown) {
						if (otherItem != null) {
							if (!messagesController.isChatNoForwards(currentChat)) {
								otherItem?.showSubItem(gallery_menu_save)
							}
							else {
								otherItem?.hideSubItem(gallery_menu_save)
							}

							if (imageUpdater != null) {
								otherItem?.showSubItem(add_photo)
								otherItem?.showSubItem(edit_avatar)
								otherItem?.showSubItem(delete_avatar)
								otherItem?.hideSubItem(set_as_main)
								otherItem?.hideSubItem(logout)
							}
						}

						searchItem?.isEnabled = false

						isPulledDown = true

						NotificationCenter.globalInstance.postNotificationName(NotificationCenter.needCheckSystemBarColors, true)

						overlaysView!!.setOverlaysVisible(true, durationFactor)

						avatarsViewPagerIndicatorView!!.refreshVisibility(durationFactor)

						avatarsViewPager?.createThumbFromParent = true
						avatarsViewPager?.adapter?.notifyDataSetChanged()

						expandAnimator!!.cancel()

						val value = AndroidUtilities.lerp(expandAnimatorValues, currentExpandAnimatorFracture)
						expandAnimatorValues[0] = value
						expandAnimatorValues[1] = 1f

						expandAnimator!!.duration = ((1f - value) * 250f / durationFactor).toLong()

						expandAnimator!!.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationStart(animation: Animator) {
								setForegroundImage(false)
								avatarsViewPager!!.setAnimatedFileMaybe(avatarImage!!.imageReceiver.animation)
								avatarsViewPager!!.resetCurrentItem()
							}

							override fun onAnimationEnd(animation: Animator) {
								expandAnimator!!.removeListener(this)
								topView!!.setBackgroundColor(Color.BLACK)
								avatarContainer!!.visibility = View.GONE
								avatarsViewPager!!.visibility = View.VISIBLE
							}
						})
						expandAnimator!!.start()
					}

					val params = avatarsViewPager!!.layoutParams
					params.width = listView!!.measuredWidth
					params.height = (h + newTop).toInt()

					avatarsViewPager!!.requestLayout()

					if (!expandAnimator!!.isRunning) {
						var additionalTranslationY = 0f
						if (openAnimationInProgress && playProfileAnimation == 2) {
							additionalTranslationY = -(1.0f - animationProgress) * AndroidUtilities.dp(50f)
						}

						nameTextView[1]!!.translationX = AndroidUtilities.dpf2(16f) - nameTextView[1]!!.left
						nameTextView[1]!!.translationY = newTop + h - AndroidUtilities.dpf2(38f) - nameTextView[1]!!.bottom + additionalTranslationY

						onlineTextView[1]!!.translationX = AndroidUtilities.dpf2(16f) - onlineTextView[1]!!.left
						onlineTextView[1]!!.translationY = newTop + h - AndroidUtilities.dpf2(18f) - onlineTextView[1]!!.bottom + additionalTranslationY

						mediaCounterTextView!!.translationX = onlineTextView[1]!!.translationX
						mediaCounterTextView!!.translationY = onlineTextView[1]!!.translationY
					}
				}
				else {
					if (isPulledDown) {
						isPulledDown = false
						NotificationCenter.globalInstance.postNotificationName(NotificationCenter.needCheckSystemBarColors, true)

						if (otherItem != null) {
							otherItem?.hideSubItem(gallery_menu_save)

							if (imageUpdater != null) {
								otherItem?.hideSubItem(set_as_main)
								otherItem?.hideSubItem(edit_avatar)
								otherItem?.hideSubItem(delete_avatar)
								otherItem?.showSubItem(add_photo)
								otherItem?.hideSubItem(logout)
								otherItem?.showSubItem(edit_name)
							}
						}

						searchItem?.isEnabled = !scrolling

						overlaysView!!.setOverlaysVisible(false, durationFactor)
						avatarsViewPagerIndicatorView!!.refreshVisibility(durationFactor)
						expandAnimator!!.cancel()
						avatarImage!!.imageReceiver.allowStartAnimation = true
						avatarImage!!.imageReceiver.startAnimation()

						val value = AndroidUtilities.lerp(expandAnimatorValues, currentExpandAnimatorFracture)

						expandAnimatorValues[0] = value
						expandAnimatorValues[1] = 0f

						if (!isInLandscapeMode) {
							expandAnimator!!.duration = (value * 250f / durationFactor).toLong()
						}
						else {
							expandAnimator!!.duration = 0
						}

						topView?.setBackgroundColor(ResourcesCompat.getColor(parentActivity.resources, R.color.avatar_background, null))

						if (!doNotSetForeground) {
							val imageView = avatarsViewPager?.currentItemView

							if (imageView != null) {
								avatarImage?.setForegroundImageDrawable(imageView.imageReceiver.drawableSafe)
							}
						}

						avatarImage?.setForegroundAlpha(1f)
						avatarContainer?.visible()
						avatarsViewPager?.gone()
						expandAnimator!!.start()
					}

					avatarContainer?.scaleX = avatarScale
					avatarContainer?.scaleY = avatarScale

					if (expandAnimator == null || !expandAnimator!!.isRunning) {
						refreshNameAndOnlineXY()
						nameTextView[1]!!.translationX = nameX
						nameTextView[1]!!.translationY = nameY
						onlineTextView[1]!!.translationX = onlineX
						onlineTextView[1]!!.translationY = onlineY
						mediaCounterTextView!!.translationX = onlineX
						mediaCounterTextView!!.translationY = onlineY
					}
				}
			}

			if (openAnimationInProgress && playProfileAnimation == 2) {
				val avX = 0f
				val avY = (if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0) + ActionBar.getCurrentActionBarHeight() / 2.0f - 21 * AndroidUtilities.density + actionBar!!.translationY

				nameTextView[0]?.translationX = 0f
				nameTextView[0]?.translationY = floor(avY.toDouble()).toFloat() + AndroidUtilities.dp(1.3f)

				onlineTextView[0]?.translationX = 0f
				onlineTextView[0]?.translationY = floor(avY.toDouble()).toFloat() + AndroidUtilities.dp(24f)

				nameTextView[0]?.scaleX = 1.0f
				nameTextView[0]?.scaleY = 1.0f
				nameTextView[1]?.pivotY = nameTextView[1]!!.measuredHeight.toFloat()
				nameTextView[1]?.scaleX = 1.67f
				nameTextView[1]?.scaleY = 1.67f

				avatarScale = AndroidUtilities.lerp(1.0f, (avatarSide + avatarSide + 18f) / avatarSide, animationProgress)

				avatarImage?.setRoundRadius(AndroidUtilities.lerp(AndroidUtilities.dpf2(avatarSide * 0.45f), 0f, animationProgress).toInt())

				avatarContainer?.translationX = AndroidUtilities.lerp(avX, 0f, animationProgress)
				avatarContainer?.translationY = AndroidUtilities.lerp(ceil(avY.toDouble()).toFloat(), 0f, animationProgress)

				val extra = (avatarContainer!!.measuredWidth - AndroidUtilities.dp(avatarSide)) * avatarScale

				timeItem?.translationX = avatarContainer!!.x + AndroidUtilities.dp(16f) + extra
				timeItem?.translationY = avatarContainer!!.y + AndroidUtilities.dp(15f) + extra

				avatarContainer?.scaleX = avatarScale
				avatarContainer?.scaleY = avatarScale

				overlaysView?.setAlphaValue(animationProgress, false)

				actionBar?.setItemsColor(ResourcesCompat.getColor(parentActivity.resources, R.color.white, null), false)

				scamDrawable?.setColor(ResourcesCompat.getColor(parentActivity.resources, R.color.brand, null))

				lockIconDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(parentActivity.resources, R.color.white, null), PorterDuff.Mode.MULTIPLY)

				if (verifiedCrossfadeDrawable != null) {
					verifiedCrossfadeDrawable?.progress = animationProgress
					nameTextView[1]?.invalidate()
				}

				if (premiumCrossfadeDrawable != null) {
					premiumCrossfadeDrawable?.progress = animationProgress
					nameTextView[1]?.invalidate()
				}

				updateEmojiStatusDrawableColor(animationProgress)

				val params = avatarContainer!!.layoutParams as FrameLayout.LayoutParams
				params.height = AndroidUtilities.lerp(AndroidUtilities.dpf2(avatarSide), (extraHeight + newTop) / avatarScale, animationProgress).toInt()
				params.width = params.height
				params.leftMargin = AndroidUtilities.lerp(AndroidUtilities.dpf2(64f), 0f, animationProgress).toInt()

				avatarContainer?.requestLayout()
			}
			else if (extraHeight <= AndroidUtilities.dp(88f)) {
				avatarScale = (42 + 18 * diff) / 42.0f

				val nameScale = 1.0f + 0.12f * diff

				if (expandAnimator == null || !expandAnimator!!.isRunning) {
					avatarContainer!!.scaleX = avatarScale
					avatarContainer!!.scaleY = avatarScale
					avatarContainer!!.translationX = avatarX
					avatarContainer!!.translationY = ceil(avatarY.toDouble()).toFloat()
					val extra = AndroidUtilities.dp(avatarSide) * avatarScale - AndroidUtilities.dp(avatarSide)
					timeItem!!.translationX = avatarContainer!!.x + AndroidUtilities.dp(16f) + extra
					timeItem!!.translationY = avatarContainer!!.y + AndroidUtilities.dp(15f) + extra
				}

				nameX = -21 * AndroidUtilities.density * diff
				nameY = floor(avatarY.toDouble()).toFloat() + AndroidUtilities.dp(1.3f) + AndroidUtilities.dp(7f) * diff

				onlineX = -21 * AndroidUtilities.density * diff
				onlineY = floor(avatarY.toDouble()).toFloat() + AndroidUtilities.dp(24f) + floor((11 * AndroidUtilities.density).toDouble()).toFloat() * diff

				for (a in nameTextView.indices) {
					if (nameTextView[a] == null) {
						continue
					}

					if (expandAnimator == null || !expandAnimator!!.isRunning) {
						nameTextView[a]!!.translationX = nameX
						nameTextView[a]!!.translationY = nameY

						onlineTextView[a]!!.translationX = onlineX
						onlineTextView[a]!!.translationY = onlineY

						if (a == 1) {
							mediaCounterTextView!!.translationX = onlineX
							mediaCounterTextView!!.translationY = onlineY
						}
					}

					nameTextView[a]!!.scaleX = nameScale
					nameTextView[a]!!.scaleY = nameScale
				}
			}

			if (!openAnimationInProgress && (expandAnimator == null || !expandAnimator!!.isRunning)) {
				needLayoutText(diff)
			}
		}

		if (isPulledDown || overlaysView != null && overlaysView!!.animator != null && overlaysView!!.animator!!.isRunning) {
			val overlaysLp = overlaysView!!.layoutParams
			overlaysLp.width = listView!!.measuredWidth
			overlaysLp.height = (extraHeight + newTop).toInt()
			overlaysView!!.requestLayout()
		}

		updateEmojiStatusEffectPosition()
	}

	private fun updateQrItemVisibility(animated: Boolean) {
		val qrItem = qrItem ?: return
		val setQrVisible = isQrNeedVisible && min(1f, extraHeight / AndroidUtilities.dp(88f)) > .5f && searchTransitionProgress > .5f

		if (animated) {
			if (setQrVisible != isQrItemVisible) {
				isQrItemVisible = setQrVisible

				qrItemAnimation?.cancel()
				qrItemAnimation = null

				qrItem.isClickable = isQrItemVisible

				qrItemAnimation = AnimatorSet()

				if (!(qrItem.visibility == View.GONE && !setQrVisible)) {
					qrItem.visibility = View.VISIBLE
				}

				if (setQrVisible) {
					qrItemAnimation?.interpolator = DecelerateInterpolator()
					qrItemAnimation?.playTogether(ObjectAnimator.ofFloat(qrItem, View.ALPHA, 1.0f), ObjectAnimator.ofFloat(qrItem, View.SCALE_Y, 1f), ObjectAnimator.ofFloat(avatarsViewPagerIndicatorView, View.TRANSLATION_X, -AndroidUtilities.dp(48f).toFloat()))
				}
				else {
					qrItemAnimation?.interpolator = AccelerateInterpolator()
					qrItemAnimation?.playTogether(ObjectAnimator.ofFloat(qrItem, View.ALPHA, 0.0f), ObjectAnimator.ofFloat(qrItem, View.SCALE_Y, 0f), ObjectAnimator.ofFloat(avatarsViewPagerIndicatorView, View.TRANSLATION_X, 0f))
				}

				qrItemAnimation?.duration = 150

				qrItemAnimation?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						qrItemAnimation = null
					}
				})

				qrItemAnimation?.start()
			}
		}
		else {
			qrItemAnimation?.cancel()
			qrItemAnimation = null

			isQrItemVisible = setQrVisible

			qrItem.isClickable = isQrItemVisible
			qrItem.alpha = if (setQrVisible) 1.0f else 0.0f
			qrItem.visibility = if (setQrVisible) View.VISIBLE else View.GONE
		}
	}

	private fun setForegroundImage(secondParent: Boolean) {
		val drawable = avatarImage?.imageReceiver?.drawable

		if (drawable is AnimatedFileDrawable) {
			avatarImage?.setForegroundImage(null, null, drawable)

			if (secondParent) {
				drawable.addSecondParentView(avatarImage)
			}
		}
		else {
			val location = avatarsViewPager?.getImageLocation(0)

			val filter = if (location != null && location.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
				"avatar"
			}
			else {
				null
			}

			avatarImage?.setForegroundImage(location, filter, drawable)
		}
	}

	private fun refreshNameAndOnlineXY() {
		nameX = AndroidUtilities.dp(-21f) + avatarContainer!!.measuredWidth * (avatarScale - (avatarSide + 18f) / avatarSide)
		nameY = floor(avatarY.toDouble()).toFloat() + AndroidUtilities.dp(1.3f) + AndroidUtilities.dp(7f) + avatarContainer!!.measuredHeight * (avatarScale - (avatarSide + 18f) / avatarSide) / 2f
		onlineX = AndroidUtilities.dp(-21f) + avatarContainer!!.measuredWidth * (avatarScale - (avatarSide + 18f) / avatarSide)
		onlineY = floor(avatarY.toDouble()).toFloat() + AndroidUtilities.dp(24f) + floor((11 * AndroidUtilities.density).toDouble()).toFloat() + avatarContainer!!.measuredHeight * (avatarScale - (avatarSide + 18f) / avatarSide) / 2f
	}

	private fun needLayoutText(diff: Float) {
		val scale = nameTextView[1]!!.scaleX
		val maxScale = if (extraHeight > AndroidUtilities.dp(88f)) 1.67f else 1.12f

		if (extraHeight > AndroidUtilities.dp(88f) && scale != maxScale) {
			return
		}

		val viewWidth = if (AndroidUtilities.isTablet()) AndroidUtilities.dp(490f) else AndroidUtilities.displaySize.x
		var extra = 0

		if (editItemVisible) {
			extra += 48
		}

		if (callItemVisible) {
			extra += 48
		}

		if (videoCallItemVisible) {
			extra += 48
		}

		if (searchItem != null) {
			extra += 48
		}

		val buttonsWidth = AndroidUtilities.dp(118 + 8 + (40 + extra * (1.0f - mediaHeaderAnimationProgress)))
		val minWidth = viewWidth - buttonsWidth
		val width = (viewWidth - buttonsWidth * max(0.0f, 1.0f - if (diff != 1.0f) diff * 0.15f / (1.0f - diff) else 1.0f) - nameTextView[1]!!.translationX).toInt()
		var width2 = nameTextView[1]!!.paint.measureText(nameTextView[1]!!.getText().toString()) * scale + nameTextView[1]!!.sideDrawablesSize
		var layoutParams: FrameLayout.LayoutParams = nameTextView[1]!!.layoutParams as FrameLayout.LayoutParams
		var prevWidth = layoutParams.width

		if (width < width2) {
			layoutParams.width = max(minWidth, ceil(((width - AndroidUtilities.dp(24f)) / (scale + (maxScale - scale) * 7.0f)).toDouble()).toInt())
		}
		else {
			layoutParams.width = ceil(width2.toDouble()).toInt()
		}

		layoutParams.width = min((viewWidth - nameTextView[1]!!.x) / scale - AndroidUtilities.dp(8f), layoutParams.width.toFloat()).toInt()

		if (layoutParams.width != prevWidth) {
			nameTextView[1]!!.requestLayout()
		}

		width2 = onlineTextView[1]!!.paint.measureText(onlineTextView[1]!!.getText().toString())
		layoutParams = onlineTextView[1]!!.layoutParams as FrameLayout.LayoutParams

		val layoutParams2 = mediaCounterTextView!!.layoutParams as FrameLayout.LayoutParams

		prevWidth = layoutParams.width
		layoutParams.rightMargin = ceil((onlineTextView[1]!!.translationX + AndroidUtilities.dp(8f) + AndroidUtilities.dp(40f) * (1.0f - diff)).toDouble()).toInt()
		layoutParams2.rightMargin = layoutParams.rightMargin

		if (width < width2) {
			layoutParams.width = ceil(width.toDouble()).toInt()
			layoutParams2.width = layoutParams.width
		}
		else {
			layoutParams.width = LayoutHelper.WRAP_CONTENT
			layoutParams2.width = layoutParams.width
		}

		if (prevWidth != layoutParams.width) {
			onlineTextView[1]!!.requestLayout()
			mediaCounterTextView!!.requestLayout()
		}
	}

	private fun fixLayout() {
		fragmentView?.doOnPreDraw {
			checkListViewScroll()
			needLayout(true)
		}
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)

		sharedMediaLayout?.onConfigurationChanged(newConfig)

		invalidateIsInLandscapeMode()

		if (isInLandscapeMode && isPulledDown) {
			val view = layoutManager?.findViewByPosition(0)

			if (view != null) {
				listView?.scrollBy(0, view.top - AndroidUtilities.dp(88f))
			}
		}

		fixLayout()
	}

	private fun openBot(botId: Long = BuildConfig.AI_BOT_ID) {
		val args = Bundle()
		args.putInt("dialog_folder_id", 0)
		args.putInt("dialog_filter_id", 0)
		args.putLong("user_id", botId)

		if (!messagesController.checkCanOpenChat(args, this@ProfileActivity)) {
			return
		}

		presentFragment(ChatActivity(args))
	}

	private fun invalidateIsInLandscapeMode() {
		val parentActivity = parentActivity ?: return
		val size = Point()
		val display = parentActivity.windowManager.defaultDisplay
		display.getSize(size)
		isInLandscapeMode = size.x > size.y
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.updateInterfaces -> {
				val mask = args[0] as Int
				val infoChanged = mask and MessagesController.UPDATE_MASK_ALL != 0 || mask and MessagesController.UPDATE_MASK_AVATAR != 0 || mask and MessagesController.UPDATE_MASK_NAME != 0 || mask and MessagesController.UPDATE_MASK_STATUS != 0 || mask and MessagesController.UPDATE_MASK_EMOJI_STATUS != 0

				if (userId != 0L) {
					if (infoChanged) {
						updateProfileData(true)
					}

//					if (mask and MessagesController.UPDATE_MASK_PHONE != 0) {
//						if (listView != null) {
//							val holder = listView?.findViewHolderForPosition(phoneRow) as? RecyclerListView.Holder
//
//							if (holder != null) {
//								listAdapter?.onBindViewHolder(holder, phoneRow)
//							}
//						}
//					}
				}
				else if (chatId != 0L) {
					if (mask and MessagesController.UPDATE_MASK_ALL != 0 || mask and MessagesController.UPDATE_MASK_CHAT != 0 || mask and MessagesController.UPDATE_MASK_CHAT_AVATAR != 0 || mask and MessagesController.UPDATE_MASK_CHAT_NAME != 0 || mask and MessagesController.UPDATE_MASK_CHAT_MEMBERS != 0 || mask and MessagesController.UPDATE_MASK_STATUS != 0) {
						if (mask and MessagesController.UPDATE_MASK_CHAT != 0) {
							updateListAnimated(true)
						}
						else {
							updateOnlineCount(true)
						}

						updateProfileData(true)
					}

					if (infoChanged) {
						listView?.children?.forEach {
							if (it is UserCell) {
								it.update(mask)
							}
						}
					}
				}
			}

			NotificationCenter.chatOnlineCountDidLoad -> {
				val chatId = args[0] as Long

				if (chatInfo == null || currentChat == null || currentChat!!.id != chatId) {
					return
				}

				chatInfo?.online_count = (args[1] as Int)

				updateOnlineCount(true)
				updateProfileData(false)
			}

			NotificationCenter.contactsDidLoad -> {
				createActionBarMenu(true)
			}

			NotificationCenter.encryptedChatCreated -> {
				if (creatingChat) {
					AndroidUtilities.runOnUIThread {
						notificationCenter.removeObserver(this@ProfileActivity, NotificationCenter.closeChats)
						notificationCenter.postNotificationName(NotificationCenter.closeChats)
						val encryptedChat = args[0] as TLRPC.EncryptedChat
						val args2 = Bundle()
						args2.putInt("enc_id", encryptedChat.id)
						presentFragment(ChatActivity(args2), true)
					}
				}
			}

			NotificationCenter.encryptedChatUpdated -> {
				val chat = args[0] as TLRPC.EncryptedChat

				if (currentEncryptedChat != null && chat.id == currentEncryptedChat!!.id) {
					currentEncryptedChat = chat
					updateListAnimated(false)
				}
			}

			NotificationCenter.blockedUsersDidLoad -> {
				val oldValue = userBlocked

				userBlocked = messagesController.blockedPeers.indexOfKey(userId) >= 0

				if (oldValue != userBlocked) {
					createActionBarMenu(true)
					updateListAnimated(false)
				}
			}

			NotificationCenter.groupCallUpdated -> {
				val chatId = args[0] as Long

				if (currentChat != null && chatId == currentChat!!.id && ChatObject.canManageCalls(currentChat)) {
					val chatFull = MessagesController.getInstance(currentAccount).getChatFull(chatId)

					if (chatFull != null) {
						if (chatInfo != null) {
							chatFull.participants = chatInfo!!.participants
						}

						chatInfo = chatFull
					}

					if (chatInfo != null && (chatInfo!!.call == null && !hasVoiceChatItem || chatInfo!!.call != null && hasVoiceChatItem)) {
						createActionBarMenu(false)
					}
				}
			}

			NotificationCenter.chatInfoDidLoad -> {
				val chatFull = args[0] as TLRPC.ChatFull

				if (chatFull.id == chatId) {
					val byChannelUsers = args[2] as Boolean

					if (chatInfo is TLRPC.TL_channelFull) {
						if (chatFull.participants == null) {
							chatFull.participants = chatInfo?.participants
						}
					}

					val loadChannelParticipants = chatInfo == null && chatFull is TLRPC.TL_channelFull

					chatInfo = chatFull

					if (mergeDialogId == 0L && chatInfo!!.migrated_from_chat_id != 0L) {
						mergeDialogId = -chatInfo!!.migrated_from_chat_id
						mediaDataController.getMediaCount(mergeDialogId, MediaDataController.MEDIA_PHOTOVIDEO, classGuid, true)
					}

					fetchUsersFromChannelInfo()

					avatarsViewPager?.setChatInfo(chatInfo)

					updateListAnimated(true)

					val newChat = messagesController.getChat(chatId)

					if (newChat != null) {
						currentChat = newChat
						createActionBarMenu(true)
					}

					if (currentChat!!.megagroup && (loadChannelParticipants || !byChannelUsers)) {
						getChannelParticipants(true)
					}

					updateAutoDeleteItem()
					updateTtlIcon()
				}
			}

			NotificationCenter.closeChats -> {
				if (!isSelf()) {
					removeSelfFromStack()
				}
			}

			NotificationCenter.botInfoDidLoad -> {
				val info = args[0] as TLRPC.BotInfo

				if (info.user_id == userId) {
					botInfo = info
					updateListAnimated(false)
				}
			}

			NotificationCenter.userInfoDidLoad -> {
				val uid = args[0] as Long

				if (uid == userId) {
					userInfo = args[1] as UserFull

					if (imageUpdater != null) {
						if (userInfo?.about != currentBio || messagesController.getUser(userId)?.bot_description != currentBio) {
							listAdapter?.notifyItemChanged(bioRow)
						}
					}
					else {
						if (!openAnimationInProgress && !callItemVisible) {
							createActionBarMenu(true)
						}
						else {
							recreateMenuAfterAnimation = true
						}

						updateListAnimated(false)

						sharedMediaLayout?.setCommonGroupsCount(userInfo?.common_chats_count ?: 0)

						updateSelectedMediaTabText()

						if (sharedMediaPreloader == null || sharedMediaPreloader?.isMediaWasLoaded == true) {
							resumeDelayedFragmentAnimation()
							needLayout(true)
						}
					}

					updateAutoDeleteItem()
					updateTtlIcon()
				}
				else if ((uid == BuildConfig.SUPPORT_BOT_ID || uid == BuildConfig.AI_BOT_ID) && shouldOpenBot) {
					shouldOpenBot = false
					openBot(uid)
				}
			}

			NotificationCenter.privacyRulesUpdated -> {
				if (qrItem != null) {
					updateQrItemVisibility(true)
				}
			}

			NotificationCenter.updateUnreadBadge -> {
				val count = args[0] as Int

				if (count == -1) {
					val filteredRows = getFilteredAccountsRows()

					filteredRows.forEach {
						listAdapter?.notifyItemChanged(it)
					}
				}
			}

			NotificationCenter.didReceiveNewMessages -> {
				val scheduled = args[2] as Boolean

				if (scheduled) {
					return
				}

				val did = getDialogId()

				if (did == args[0] as Long) {
					val arr = args[1] as List<MessageObject>

					for (a in arr.indices) {
						val obj = arr[a]

						if (currentEncryptedChat != null && obj.messageOwner?.action is TLRPC.TL_messageEncryptedAction && obj.messageOwner?.action?.encryptedAction is TLRPC.TL_decryptedMessageActionSetMessageTTL) {
							listAdapter?.notifyDataSetChanged()
						}
					}
				}
			}

			NotificationCenter.emojiLoaded -> {
				listView?.invalidateViews()
			}

			NotificationCenter.reloadInterface -> {
				updateListAnimated(false)
			}

			NotificationCenter.newSuggestionsAvailable -> {
				val prevRow1 = passwordSuggestionRow
				val prevRow2 = phoneSuggestionRow

				updateRowsIds()

				if (prevRow1 != passwordSuggestionRow || prevRow2 != phoneSuggestionRow) {
					listAdapter?.notifyDataSetChanged()
				}
			}
		}
	}

	private fun updateAutoDeleteItem() {
		if (autoDeleteItem == null || autoDeletePopupWrapper == null) {
			return
		}

		val ttl = userInfo?.ttl_period ?: chatInfo?.ttl_period ?: 0

		autoDeleteItemDrawable?.setTime(ttl)
		autoDeletePopupWrapper?.updateItems(ttl)
	}

	private fun updateTimeItem() {
		if (timerDrawable == null) {
			return
		}

		if (currentEncryptedChat != null) {
			timerDrawable?.setTime(currentEncryptedChat!!.ttl)
			timeItem?.tag = 1
			timeItem?.visibility = View.VISIBLE
		}
		else if (userInfo != null) {
			timerDrawable?.setTime(userInfo!!.ttl_period)

			if (needTimerImage && userInfo!!.ttl_period != 0) {
				timeItem?.tag = 1
				timeItem?.visibility = View.VISIBLE
			}
			else {
				timeItem?.tag = null
				timeItem?.visibility = View.GONE
			}
		}
		else if (chatInfo != null) {
			timerDrawable?.setTime(chatInfo!!.ttl_period)

			if (needTimerImage && chatInfo!!.ttl_period != 0) {
				timeItem?.tag = 1
				timeItem?.visibility = View.VISIBLE
			}
			else {
				timeItem?.tag = null
				timeItem?.visibility = View.GONE
			}
		}
		else {
			timeItem?.tag = null
			timeItem?.visibility = View.GONE
		}
	}

	override fun needDelayOpenAnimation(): Boolean {
		return playProfileAnimation == 0
	}

	override fun mediaCountUpdated() {
		if (sharedMediaLayout != null && sharedMediaPreloader != null) {
			sharedMediaLayout?.setNewMediaCounts(sharedMediaPreloader!!.lastMediaCount)
		}

		updateSharedMediaRows()
		updateSelectedMediaTabText()

		if (userInfo != null) {
			resumeDelayedFragmentAnimation()
		}
	}

	override fun onResume() {
		super.onResume()

		sharedMediaLayout?.onResume()

		invalidateIsInLandscapeMode()

		if (imageUpdater != null) {
			imageUpdater?.onResume()
			setParentActivityTitle(context?.getString(R.string.Settings))
		}

		updateProfileData(true)

		fixLayout()

		nameTextView[1]?.getText()?.let {
			setParentActivityTitle(it)
		}

		//MARK: the layout broke after changing the channel type to private
//		if (listAdapter != null) {
//			firstLayout = true
//			listAdapter?.notifyDataSetChanged()
//		}

		updateRowsIds()

		listView?.let {
			it.postDelayed({
				it.scrollBy(0, 1)
			}, 100)
		}
	}

	override fun onPause() {
		super.onPause()
		shouldOpenBot = false
		undoView?.hide(true, 0)
		imageUpdater?.onPause()
	}

	override fun isSwipeBackEnabled(event: MotionEvent): Boolean {
		if (avatarsViewPager != null && avatarsViewPager!!.visibility == View.VISIBLE && avatarsViewPager!!.realCount > 1) {
			avatarsViewPager?.getHitRect(rect)

			if (rect.contains(event.x.toInt(), event.y.toInt() - actionBar!!.measuredHeight)) {
				return false
			}
		}

		if (sharedMediaRow == -1 || sharedMediaLayout == null) {
			return true
		}

		if (!sharedMediaLayout!!.isSwipeBackEnabled) {
			return false
		}

		sharedMediaLayout!!.getHitRect(rect)

		return if (!rect.contains(event.x.toInt(), event.y.toInt() - actionBar!!.measuredHeight)) {
			true
		}
		else {
			sharedMediaLayout!!.isCurrentTabFirst
		}
	}

	override fun canBeginSlide(): Boolean {
		return if (!sharedMediaLayout!!.isSwipeBackEnabled) {
			false
		}
		else {
			super.canBeginSlide()
		}
	}

	override fun onBackPressed(): Boolean {
		return actionBar!!.isEnabled && (sharedMediaRow == -1 || sharedMediaLayout == null || !sharedMediaLayout!!.closeActionMode())
	}

	val isSettings: Boolean
		get() = imageUpdater != null

	override fun onBecomeFullyHidden() {
		undoView?.hide(true, 0)
	}

	fun setPlayProfileAnimation(type: Int) {
		val preferences = MessagesController.getGlobalMainSettings()

		if (!AndroidUtilities.isTablet()) {
			needTimerImage = type != 0

			if (preferences.getBoolean("view_animations", true)) {
				playProfileAnimation = type
			}
			else if (type == 2) {
				expandPhoto = true
			}
		}
	}

	private fun updateSharedMediaRows() {
		if (listAdapter == null) {
			return
		}

		updateListAnimated(false)
	}

	override fun onTransitionAnimationStart(isOpen: Boolean, backward: Boolean) {
		isFragmentOpened = isOpen

		if ((!isOpen && backward || isOpen && !backward) && playProfileAnimation != 0 && allowProfileAnimation && !isPulledDown) {
			openAnimationInProgress = true
		}

		if (isOpen) {
			transitionIndex = if (imageUpdater != null) {
				notificationCenter.setAnimationInProgress(transitionIndex, intArrayOf(NotificationCenter.dialogsNeedReload, NotificationCenter.closeChats, NotificationCenter.mediaCountDidLoad, NotificationCenter.mediaCountsDidLoad, NotificationCenter.userInfoDidLoad))
			}
			else {
				notificationCenter.setAnimationInProgress(transitionIndex, intArrayOf(NotificationCenter.dialogsNeedReload, NotificationCenter.closeChats, NotificationCenter.mediaCountDidLoad, NotificationCenter.mediaCountsDidLoad))
			}

			val parentActivity = parentActivity

			if (!backward && parentActivity != null) {
				navigationBarAnimationColorFrom = parentActivity.window.navigationBarColor
			}
		}

		transitionAnimationInProgress = true
	}

	override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
		if (isOpen) {
			if (!backward) {
				if (playProfileAnimation != 0 && allowProfileAnimation) {
					openAnimationInProgress = false

					checkListViewScroll()

					if (recreateMenuAfterAnimation) {
						createActionBarMenu(true)
					}
				}

				if (!fragmentOpened) {
					fragmentOpened = true
					invalidateScroll = true
					fragmentView?.requestLayout()
				}
			}

			notificationCenter.onAnimationFinish(transitionIndex)
		}

		transitionAnimationInProgress = false
	}

	@Keep
	fun getAnimationProgress(): Float {
		return animationProgress
	}

	@Keep
	fun setAnimationProgress(progress: Float) {
		val context = context ?: return
		currentExpandAnimatorValue = progress
		animationProgress = currentExpandAnimatorValue
		listView?.alpha = progress
		listView?.translationX = AndroidUtilities.dp(48f) - AndroidUtilities.dp(48f) * progress

//		var color = if (playProfileAnimation == 2 && avatarColor != 0) {
//			avatarColor
//		}
//		else {
//			AvatarDrawable.getProfileBackColorForId()
//		}

		var color = ResourcesCompat.getColor(context.resources, R.color.avatar_background, null)

//		var actionBarColor = if (actionBarAnimationColorFrom != 0) {
//			actionBarAnimationColorFrom
//		}
//		else {
//			ResourcesCompat.getColor(context.resources, R.color.background, null)
//		}

		var actionBarColor = ResourcesCompat.getColor(context.resources, R.color.background, null)

		val actionBarColor2 = actionBarColor

		if (SharedConfig.chatBlurEnabled()) {
			actionBarColor = ColorUtils.setAlphaComponent(actionBarColor, 0)
		}

		topView?.setBackgroundColor(ColorUtils.blendARGB(actionBarColor, color, progress))
		timerDrawable?.setBackgroundColor(ColorUtils.blendARGB(actionBarColor2, color, progress))

		actionBar?.setItemsColor(ResourcesCompat.getColor(context.resources, R.color.brand_day_night, null), false)

		color = ResourcesCompat.getColor(context.resources, R.color.dark, null)

		val titleColor = ResourcesCompat.getColor(context.resources, R.color.dark, null)

		for (i in 0..1) {
			if (nameTextView[i] == null || i == 1 && playProfileAnimation == 2) {
				continue
			}

			nameTextView[i]?.textColor = ColorUtils.blendARGB(titleColor, color, progress)
		}

//		color = if (isOnline[0]) ResourcesCompat.getColor(context.resources, R.color.dark_gray, null) else AvatarDrawable.getProfileTextColorForId()
//
//		val subtitleColor = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
//
//		for (i in 0..1) {
//			if (onlineTextView[i] == null || i == 1 && playProfileAnimation == 2) {
//				continue
//			}
//
//			 onlineTextView[i]?.textColor = ColorUtils.blendARGB(subtitleColor, color, progress)
//		}

		extraHeight = initialAnimationExtraHeight * progress

		color = AvatarDrawable.getProfileColorForId(if (userId != 0L) userId else chatId)

		val color2 = AvatarDrawable.getColorForId(if (userId != 0L) userId else chatId)

		if (color != color2) {
			avatarDrawable?.color = ColorUtils.blendARGB(color2, color, progress)
			avatarImage?.invalidate()
		}

		if (navigationBarAnimationColorFrom != 0) {
			color = ColorUtils.blendARGB(navigationBarAnimationColorFrom, navigationBarColor, progress)
			navigationBarColor = color
		}

		topView?.invalidate()

		needLayout(true)

		fragmentView?.invalidate()
		aboutLinkCell?.invalidate()
	}

	override fun onCustomTransitionAnimation(isOpen: Boolean, callback: Runnable): AnimatorSet? {
		val context = context ?: return null

		if (playProfileAnimation != 0 && allowProfileAnimation && !isPulledDown && !disableProfileAnimation) {
			timeItem?.alpha = 1.0f

			if (parentLayout != null && parentLayout!!.fragmentsStack.size >= 2) {
				val fragment = parentLayout!!.fragmentsStack[parentLayout!!.fragmentsStack.size - 2]

				if (fragment is ChatActivity) {
					previousTransitionFragment = fragment
				}
			}

			if (previousTransitionFragment != null) {
				updateTimeItem()
			}

			val animatorSet = AnimatorSet()
			animatorSet.duration = if (playProfileAnimation == 2) 250 else 180.toLong()

			listView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)

			val menu = actionBar?.createMenu()

			if (menu?.getItem(10) == null) {
				if (animatingItem == null) {
					animatingItem = menu?.addItem(10, R.drawable.overflow_menu)
				}
			}

			if (isOpen) {
				var layoutParams = onlineTextView[1]!!.layoutParams as FrameLayout.LayoutParams
				layoutParams.rightMargin = (-21 * AndroidUtilities.density + AndroidUtilities.dp(8f)).toInt()

				onlineTextView[1]?.layoutParams = layoutParams

				if (playProfileAnimation != 2) {
					val width = ceil((AndroidUtilities.displaySize.x - AndroidUtilities.dp((118 + 8).toFloat()) + 21 * AndroidUtilities.density).toDouble()).toInt()
					val width2 = nameTextView[1]!!.paint.measureText(nameTextView[1]!!.getText().toString()) * 1.12f + nameTextView[1]!!.sideDrawablesSize

					layoutParams = nameTextView[1]!!.layoutParams as FrameLayout.LayoutParams

					if (width < width2) {
						layoutParams.width = ceil((width / 1.12f).toDouble()).toInt()
					}
					else {
						layoutParams.width = LayoutHelper.WRAP_CONTENT
					}

					nameTextView[1]?.layoutParams = layoutParams

					initialAnimationExtraHeight = AndroidUtilities.dp(88f).toFloat()
				}
				else {
					layoutParams = nameTextView[1]?.layoutParams as FrameLayout.LayoutParams
					layoutParams.width = ((AndroidUtilities.displaySize.x - AndroidUtilities.dp(32f)) / 1.67f).toInt()

					nameTextView[1]?.layoutParams = layoutParams
				}

				fragmentView?.setBackgroundColor(0)

				setAnimationProgress(0f)

				val animators = ArrayList<Animator>()
				animators.add(ObjectAnimator.ofFloat(this, "animationProgress", 0.0f, 1.0f))

				if (writeButton != null && writeButton!!.tag == null) {
					writeButton?.scaleX = 0.2f
					writeButton?.scaleY = 0.2f
					writeButton?.alpha = 0.0f

					animators.add(ObjectAnimator.ofFloat(writeButton, View.SCALE_X, 1.0f))
					animators.add(ObjectAnimator.ofFloat(writeButton, View.SCALE_Y, 1.0f))
					animators.add(ObjectAnimator.ofFloat(writeButton, View.ALPHA, 1.0f))
				}

				if (playProfileAnimation == 2) {
					avatarColor = AndroidUtilities.calcBitmapColor(avatarImage!!.imageReceiver.bitmap)
					nameTextView[1]?.textColor = ResourcesCompat.getColor(context.resources, R.color.white, null)
					nameTextView[1]?.leftDrawable?.apply { setTint(context.getColor(R.color.white)) }
					onlineTextView[1]?.textColor = ResourcesCompat.getColor(context.resources, R.color.white, null)
					actionBar?.setItemsBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.white, null), false)
					overlaysView?.setOverlaysVisible()
				}

				for (a in 0..1) {
					nameTextView[a]!!.alpha = if (a == 0) 1.0f else 0.0f
					animators.add(ObjectAnimator.ofFloat(nameTextView[a], View.ALPHA, if (a == 0) 0.0f else 1.0f))
				}

				if (timeItem?.tag != null) {
					animators.add(ObjectAnimator.ofFloat(timeItem, View.ALPHA, 1.0f, 0.0f))
					animators.add(ObjectAnimator.ofFloat(timeItem, View.SCALE_X, 1.0f, 0.0f))
					animators.add(ObjectAnimator.ofFloat(timeItem, View.SCALE_Y, 1.0f, 0.0f))
				}

				if (animatingItem != null) {
					animatingItem?.alpha = 1.0f
					animators.add(ObjectAnimator.ofFloat(animatingItem, View.ALPHA, 0.0f))
				}

				if (callItemVisible && chatId != 0L) {
					callItem?.alpha = 0.0f
					animators.add(ObjectAnimator.ofFloat(callItem, View.ALPHA, 1.0f))
				}

				if (videoCallItemVisible) {
					videoCallItem?.alpha = 0.0f
					animators.add(ObjectAnimator.ofFloat(videoCallItem, View.ALPHA, 1.0f))
				}

				if (editItemVisible) {
					editItem?.alpha = 0.0f
					animators.add(ObjectAnimator.ofFloat(editItem, View.ALPHA, 1.0f))
				}

				if (ttlIconView?.tag != null) {
					ttlIconView?.alpha = 0f
					animators.add(ObjectAnimator.ofFloat(ttlIconView, View.ALPHA, 1.0f))
				}

				var onlineTextCrossfade = false
				val previousFragment = if (parentLayout!!.fragmentsStack.size > 1) parentLayout!!.fragmentsStack[parentLayout!!.fragmentsStack.size - 2] else null

				if (previousFragment is ChatActivity) {
					val avatarContainer = previousFragment.avatarContainer

					if (avatarContainer?.subtitleTextView?.leftDrawable != null || avatarContainer?.statusMadeShorter?.get(0) == true) {
						transitionOnlineText = avatarContainer.subtitleTextView
						avatarContainer2?.invalidate()
						onlineTextCrossfade = true
						onlineTextView[0]?.alpha = 0f
						onlineTextView[1]?.alpha = 0f
						animators.add(ObjectAnimator.ofFloat(onlineTextView[1], View.ALPHA, 1.0f))
					}
				}

				if (!onlineTextCrossfade) {
					for (a in 0..1) {
						onlineTextView[a]?.alpha = if (a == 0) 1.0f else 0.0f
						animators.add(ObjectAnimator.ofFloat(onlineTextView[a], View.ALPHA, if (a == 0) 0.0f else 1.0f))
					}
				}

				animatorSet.playTogether(animators)
			}
			else {
				initialAnimationExtraHeight = extraHeight

				val animators = mutableListOf<Animator>()
				animators.add(ObjectAnimator.ofFloat(this, "animationProgress", 1.0f, 0.0f))

				if (writeButton != null) {
					animators.add(ObjectAnimator.ofFloat(writeButton, View.SCALE_X, 0.2f))
					animators.add(ObjectAnimator.ofFloat(writeButton, View.SCALE_Y, 0.2f))
					animators.add(ObjectAnimator.ofFloat(writeButton, View.ALPHA, 0.0f))
				}

				for (a in 0..1) {
					animators.add(ObjectAnimator.ofFloat(nameTextView[a], View.ALPHA, if (a == 0) 1.0f else 0.0f))
				}

				if (timeItem?.tag != null) {
					timeItem?.alpha = 0f
					animators.add(ObjectAnimator.ofFloat(timeItem, View.ALPHA, 0.0f, 1.0f))
					animators.add(ObjectAnimator.ofFloat(timeItem, View.SCALE_X, 0.0f, 1.0f))
					animators.add(ObjectAnimator.ofFloat(timeItem, View.SCALE_Y, 0.0f, 1.0f))
				}

				if (animatingItem != null) {
					animatingItem?.alpha = 0.0f
					animators.add(ObjectAnimator.ofFloat(animatingItem, View.ALPHA, 1.0f))
				}

				if (callItemVisible && chatId != 0L) {
					callItem?.alpha = 1.0f
					animators.add(ObjectAnimator.ofFloat(callItem, View.ALPHA, 0.0f))
				}

				if (videoCallItemVisible) {
					videoCallItem?.alpha = 1.0f
					animators.add(ObjectAnimator.ofFloat(videoCallItem, View.ALPHA, 0.0f))
				}

				if (editItemVisible) {
					editItem?.alpha = 1.0f
					animators.add(ObjectAnimator.ofFloat(editItem, View.ALPHA, 0.0f))
				}

				if (ttlIconView != null) {
					animators.add(ObjectAnimator.ofFloat(ttlIconView, View.ALPHA, ttlIconView!!.alpha, 0.0f))
				}

				var crossfadeOnlineText = false
				val previousFragment = if (parentLayout!!.fragmentsStack.size > 1) parentLayout!!.fragmentsStack[parentLayout!!.fragmentsStack.size - 2] else null

				if (previousFragment is ChatActivity) {
					val avatarContainer = previousFragment.avatarContainer

					if (avatarContainer?.subtitleTextView?.leftDrawable != null || avatarContainer?.statusMadeShorter?.get(0) == true) {
						transitionOnlineText = avatarContainer.subtitleTextView
						avatarContainer2?.invalidate()
						crossfadeOnlineText = true
						animators.add(ObjectAnimator.ofFloat(onlineTextView[0], View.ALPHA, 0.0f))
						animators.add(ObjectAnimator.ofFloat(onlineTextView[1], View.ALPHA, 0.0f))
					}
				}

				if (!crossfadeOnlineText) {
					for (a in 0..1) {
						animators.add(ObjectAnimator.ofFloat(onlineTextView[a], View.ALPHA, if (a == 0) 1.0f else 0.0f))
					}
				}

				animatorSet.playTogether(animators)
			}

			profileTransitionInProgress = true

			val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

			valueAnimator.addUpdateListener {
				fragmentView?.invalidate()
			}

			animatorSet.playTogether(valueAnimator)

			animatorSet.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					listView?.setLayerType(View.LAYER_TYPE_NONE, null)

					if (animatingItem != null) {
						actionBar?.createMenu()?.run {
							clearItems()
						}

						animatingItem = null
					}

					callback.run()

					if (playProfileAnimation == 2) {
						playProfileAnimation = 1
						avatarImage?.setForegroundAlpha(1.0f)
						avatarContainer?.visibility = View.GONE
						avatarsViewPager?.resetCurrentItem()
						avatarsViewPager?.visibility = View.VISIBLE
					}

					transitionOnlineText = null

					avatarContainer2?.invalidate()

					profileTransitionInProgress = false
					previousTransitionFragment = null

					fragmentView?.invalidate()
				}
			})

			animatorSet.interpolator = if (playProfileAnimation == 2) CubicBezierInterpolator.DEFAULT else DecelerateInterpolator()

			AndroidUtilities.runOnUIThread({
				animatorSet.start()
			}, 50)

			return animatorSet
		}

		return null
	}

	private fun updateOnlineCount(notify: Boolean) {
		onlineCount = 0

		val currentTime = connectionsManager.currentTime

		sortedUsers?.clear()

		if (chatInfo is TLRPC.TL_chatFull || chatInfo is TLRPC.TL_channelFull && chatInfo!!.participants_count <= 200 && chatInfo?.participants != null) {
			for (a in chatInfo!!.participants.participants.indices) {
				val participant = chatInfo!!.participants.participants[a]
				val user = messagesController.getUser(participant.user_id)

				user?.status?.let {
					if ((it.expires > currentTime || user.id == userConfig.getClientUserId()) && it.expires > 10000) {
						onlineCount++
					}
				}

				sortedUsers?.add(a)
			}
			try {
				sortedUsers?.sortWith { lhs, rhs ->
					val user1 = messagesController.getUser(chatInfo!!.participants.participants[(rhs)!!].user_id)
					val user2 = messagesController.getUser(chatInfo!!.participants.participants[(lhs)!!].user_id)
					var status1 = 0
					var status2 = 0

					if (user1 != null) {
						if (user1.bot) {
							status1 = -110
						}
						else if (user1.self) {
							status1 = currentTime + 50000
						}
						else if (user1.status != null) {
							status1 = user1.status?.expires ?: 0
						}
					}

					if (user2 != null) {
						if (user2.bot) {
							status2 = -110
						}
						else if (user2.self) {
							status2 = currentTime + 50000
						}
						else if (user2.status != null) {
							status2 = user2.status?.expires ?: 0
						}
					}

					if (status1 > 0 && status2 > 0) {
						if (status1 > status2) {
							return@sortWith 1
						}
						else if (status1 < status2) {
							return@sortWith -1
						}
						return@sortWith 0
					}
					else if (status1 < 0 && status2 < 0) {
						if (status1 > status2) {
							return@sortWith 1
						}
						else if (status1 < status2) {
							return@sortWith -1
						}
						return@sortWith 0
					}
					else if (status1 < 0 && status2 > 0 || status1 == 0 && status2 != 0) {
						return@sortWith -1
					}
					else if (status2 < 0 && status1 > 0 || status2 == 0 && status1 != 0) {
						return@sortWith 1
					}

					0
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			if (notify && listAdapter != null && membersStartRow > 0) {
				AndroidUtilities.updateVisibleRows(listView)
			}

			if (sharedMediaLayout != null && sharedMediaRow != -1 && (sortedUsers!!.size > 5 || usersForceShowingIn == 2) && usersForceShowingIn != 1) {
				sharedMediaLayout?.setChatUsers(sortedUsers, chatInfo)
			}
		}
		else if (chatInfo is TLRPC.TL_channelFull && chatInfo!!.participants_count > 200) {
			onlineCount = chatInfo!!.online_count
		}
	}

	fun setChatInfo(value: TLRPC.ChatFull?) {
		chatInfo = value

		if (chatInfo != null && chatInfo!!.migrated_from_chat_id != 0L && mergeDialogId == 0L) {
			mergeDialogId = -chatInfo!!.migrated_from_chat_id
			mediaDataController.getMediaCounts(mergeDialogId, classGuid)
		}

		sharedMediaLayout?.setChatInfo(chatInfo)
		avatarsViewPager?.setChatInfo(chatInfo)

		fetchUsersFromChannelInfo()
	}

	override fun canSearchMembers(): Boolean {
		return canSearchMembers
	}

	private fun fetchUsersFromChannelInfo() {
		if (currentChat == null || !currentChat!!.megagroup) {
			return
		}

		if (chatInfo is TLRPC.TL_channelFull && chatInfo?.participants != null) {
			for (a in chatInfo!!.participants.participants.indices) {
				val chatParticipant = chatInfo!!.participants.participants[a]
				participantsMap?.put(chatParticipant.user_id, chatParticipant)
			}
		}
	}

	private fun kickUser(uid: Long, participant: TLRPC.ChatParticipant) {
		if (uid != 0L) {
			val user = messagesController.getUser(uid)
			messagesController.deleteParticipantFromChat(chatId, user)

			if (currentChat != null && user != null && BulletinFactory.canShowBulletin(this)) {
				BulletinFactory.createRemoveFromChatBulletin(this, user, currentChat!!.title).show()
			}

			if (chatInfo!!.participants.participants.remove(participant)) {
				updateListAnimated(true)
			}
		}
		else {
			notificationCenter.removeObserver(this, NotificationCenter.closeChats)

			if (AndroidUtilities.isTablet()) {
				notificationCenter.postNotificationName(NotificationCenter.closeChats, -chatId)
			}
			else {
				notificationCenter.postNotificationName(NotificationCenter.closeChats)
			}

			messagesController.deleteParticipantFromChat(chatId, messagesController.getUser(userConfig.getClientUserId()))

			playProfileAnimation = 0

			finishFragment()
		}
	}

	val isChat: Boolean
		get() = chatId != 0L

	private fun updateRowsIds() {
		val prevRowsCount = rowCount

		rowCount = 0

		for (i in accountsRows.indices) {
			accountsRows[i] = -1
		}

		setProfilePhotoRow = -1
		addAccountRow = -1
		myAccountsDividerRow = -1
		shareLinkRow = -1
		publicLinkRow = -1
		publicLinkSectionRow = -1
		walletRow = -1
		myNotificationsRow = -1
		appearanceRow = -1
		appearanceBottomDividerRow = -1
		myNotificationsBottomDividerRow = -1
		setProfilePhotoDividerRow = -1
		subscriptionsBottomDividerRow = -1
		settingsBottomDividerRow = -1
		subscriptionsRow = -1
		aiChatBotRow = -1
		referralRow = -1
		foldersRow = -1
		purchasesRow = -1
		inviteRow = -1
		myCloudRow = -1
		settingsRow = -1
		infoRow = -1
		supportDividerRow = -1
		supportRow = -1
		logoutRow = -1
		logoutDividerRow = -1
		setAvatarRow = -1
		setAvatarSectionRow = -1
		setUsernameRow = -1
		bioRow = -1
		phoneSuggestionSectionRow = -1
		phoneSuggestionRow = -1
		passwordSuggestionSectionRow = -1
		passwordSuggestionRow = -1
		settingsSectionRow = -1
		settingsSectionRow2 = -1
		notificationRow = -1
		languageRow = -1
		premiumRow = -1
		premiumSectionsRow = -1
		privacyRow = -1
		dataRow = -1
		chatRow = -1
		filtersRow = -1
		stickersRow = -1
		devicesRow = -1
		devicesSectionRow = -1
		helpHeaderRow = -1
		questionRow = -1
		faqRow = -1
		policyRow = -1
		helpSectionCell = -1
		debugHeaderRow = -1
		sendLogsRow = -1
		sendLastLogsRow = -1
		clearLogsRow = -1
		versionRow = -1
		sendMessageRow = -1
		reportRow = -1
		reportReactionRow = -1
		emptyRow = -1
		userInfoRow = -1
		locationRow = -1
		channelInfoRow = -1
		userAboutHeaderRow = -1
		usernameRow = -1
		settingsTimerRow = -1
		settingsKeyRow = -1
		notificationsDividerRow = -1
		myProfileTopDividerRow = -1
		myProfileBioDividerRow = -1
		inviteRowDividerRow = -1
		reportDividerRow = -1
		notificationsRow = -1
		infoSectionRow = -1
		secretSettingsSectionRow = -1
		subscriptionCostSectionRow = -1
		bottomPaddingRow = -1
		addToGroupButtonRow = -1
		addToGroupInfoRow = -1
		membersHeaderRow = -1
		membersStartRow = -1
		membersEndRow = -1
		addMemberRow = -1
		subscribersRow = -1
		subscribersRequestsRow = -1
		subscriptionBeginRow = -1
		subscriptionExpireRow = -1
		subscriptionCostRow = -1
		administratorsRow = -1
		blockedUsersRow = -1
		membersSectionRow = -1
		sharedMediaRow = -1
		unblockRow = -1
		joinRow = -1
		lastSectionRow = -1
		visibleChatParticipants.clear()
		visibleSortedUsers.clear()

		val hasMedia = sharedMediaPreloader?.lastMediaCount?.find { it > 0 } != null

		if (userId != 0L) {
			val user = messagesController.getUser(userId)

			if (isSelf()) {
				setProfilePhotoRow = rowCount++
				setProfilePhotoDividerRow = rowCount++

				for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
					val u = AccountInstance.getInstance(a).userConfig.getCurrentUser()

					if (u != null) {
						accountsRows[a] = rowCount++
					}
				}

				addAccountRow = rowCount++

				myAccountsDividerRow = rowCount++
			}

			if (LocaleController.isRTL) {
				emptyRow = rowCount++
			}

			if (isSelf()) {
//				if (avatarBig == null && (user!!.photo == null || user.photo.photo_big !is TLRPC.TL_fileLocation_layer97 && user.photo.photo_big !is TLRPC.TL_fileLocationToBeDeprecated) && (avatarsViewPager == null || avatarsViewPager!!.realCount == 0)) {
//					setAvatarRow = rowCount++
//					setAvatarSectionRow = rowCount++
//				}

//				numberSectionRow = rowCount++
//				numberRow = rowCount++
// 				setUsernameRow = rowCount++

				if (isPublicProfile()) {
					shareLinkRow = rowCount++
				}

				myProfileTopDividerRow = rowCount++
				bioRow = rowCount++
				myProfileBioDividerRow = rowCount++

				if (!isPublicProfile()) {
					inviteRow = rowCount++
					inviteRowDividerRow = rowCount++
				}

//				#ROW_OFF
//				appearanceRow = rowCount++
//				appearanceBottomDividerRow = rowCount++
//
				referralRow = rowCount++
				aiChatBotRow = rowCount++
				myCloudRow = rowCount++
				myNotificationsRow = rowCount++
				myNotificationsBottomDividerRow = rowCount++
				walletRow = rowCount++
//				#ROW_OFF
//				purchasesRow = rowCount++
//
				subscriptionsRow = rowCount++
				subscriptionsBottomDividerRow = rowCount++
				if (isPublicProfile()) {
					inviteRow = rowCount++
				}

//				#ROW_OFF
//				foldersRow = rowCount++

				settingsRow = rowCount++
				settingsBottomDividerRow = rowCount++
//				devicesRow = rowCount++
//				supportDividerRow = rowCount++
				infoRow = rowCount++
				supportRow = rowCount++
				logoutDividerRow = rowCount++
				logoutRow = rowCount++

//				settingsSectionRow = rowCount++

//				val suggestions = messagesController.pendingSuggestions
//
//				if (suggestions.contains("VALIDATE_PHONE_NUMBER")) {
//					phoneSuggestionRow = rowCount++
//					phoneSuggestionSectionRow = rowCount++
//				}
//
//				if (suggestions.contains("VALIDATE_PASSWORD")) {
//					passwordSuggestionRow = rowCount++
//					passwordSuggestionSectionRow = rowCount++
//				}
//
//				settingsSectionRow2 = rowCount++
//				notificationRow = rowCount++
//				privacyRow = rowCount++
//				dataRow = rowCount++
//				chatRow = rowCount++
//				stickersRow = rowCount++
//
//				if (messagesController.filtersEnabled || messagesController.dialogFilters.isNotEmpty()) {
//					filtersRow = rowCount++
//				}
//
//				devicesRow = rowCount++
//				languageRow = rowCount++
//				devicesSectionRow = rowCount++
//
//				if (!messagesController.premiumLocked) {
//					premiumRow = rowCount++
//					premiumSectionsRow = rowCount++
//				}
//
//				helpHeaderRow = rowCount++
//				questionRow = rowCount++
//				faqRow = rowCount++
//				policyRow = rowCount++
//
//				if (BuildConfig.DEBUG || BuildVars.DEBUG_PRIVATE_VERSION) {
//					helpSectionCell = rowCount++
//					debugHeaderRow = rowCount++
//				}
//
//				if (BuildConfig.DEBUG) {
//					sendLogsRow = rowCount++
//					sendLastLogsRow = rowCount++
//					clearLogsRow = rowCount++
//				}
//
//				versionRow = rowCount++
			}
			else {
				// val hasInfo = userInfo != null && !TextUtils.isEmpty(userInfo!!.about) || user != null && !TextUtils.isEmpty(user.username)

				if (user != null && !TextUtils.isEmpty(user.username)) {
					userAboutHeaderRow = rowCount++

					if (messagesController.getUser(userId)?.is_public == true) {
						usernameRow = rowCount++
					}
				}

				if ((userInfo != null && !TextUtils.isEmpty(userInfo?.about)) || (user?.bot == true && user.id == BuildConfig.AI_BOT_ID || user?.id == BuildConfig.SUPPORT_BOT_ID/* && !user.bot_description.isNullOrEmpty()*/)) {
					userInfoRow = rowCount++
				}

				if (userInfoRow != -1 || usernameRow != -1) {
					notificationsDividerRow = rowCount++
				}

				if (userId != userConfig.getClientUserId()) {
					notificationsRow = rowCount++
				}

				infoSectionRow = rowCount++

				if (currentEncryptedChat is TLRPC.TL_encryptedChat) {
					settingsTimerRow = rowCount++
					settingsKeyRow = rowCount++
					secretSettingsSectionRow = rowCount++
				}

				if (user != null && !isBot && currentEncryptedChat == null && user.id != userConfig.getClientUserId()) {
					if (userBlocked) {
						unblockRow = rowCount++
						lastSectionRow = rowCount++
					}
				}

				if (user != null && isBot && !user.bot_nochats) {
					addToGroupButtonRow = rowCount++
					addToGroupInfoRow = rowCount++
				}

				if (reportReactionMessageId != 0 && !ContactsController.getInstance(currentAccount).isContact(userId)) {
					reportReactionRow = rowCount++
					reportDividerRow = rowCount++
				}

				if (hasMedia || userInfo != null && userInfo!!.common_chats_count != 0) {
					sharedMediaRow = rowCount++
				}
				else if (lastSectionRow == -1 && needSendMessage) {
					sendMessageRow = rowCount++
					reportRow = rowCount++
					lastSectionRow = rowCount++
				}
			}
		}
		else if (chatId != 0L) {
			if (chatInfo != null && (!chatInfo?.about.isNullOrEmpty() || chatInfo?.location is TLRPC.TL_channelLocation) || !currentChat?.username.isNullOrEmpty()) {
				if (LocaleController.isRTL && ChatObject.isChannel(currentChat) && chatInfo != null && currentChat?.megagroup != true && chatInfo?.linked_chat_id != 0L) {
					emptyRow = rowCount++
				}
				if (chatInfo != null && !ChatObject.isChannel(currentChat) && !TextUtils.isEmpty(currentChat?.username)) {
					notificationsRow = rowCount++
					publicLinkSectionRow = rowCount++
					publicLinkRow = rowCount++
				}
				else if (!currentChat?.username.isNullOrEmpty()) {
					userAboutHeaderRow = rowCount++

					if (messagesController.getUser(userId)?.is_public == true) {
						usernameRow = rowCount++
					}
				}

				if (publicLinkRow == -1 && !chatInfo?.about.isNullOrEmpty()) {
					channelInfoRow = rowCount++
				}

				if (publicLinkRow == -1 && chatInfo?.location is TLRPC.TL_channelLocation) {
					locationRow = rowCount++
				}
				if (publicLinkRow == -1) {
					notificationsDividerRow = rowCount++
				}
			}

			if (publicLinkRow == -1) {
				notificationsRow = rowCount++
			}

			infoSectionRow = rowCount++

			if (ChatObject.isChannel(currentChat) && !currentChat!!.megagroup) {
				if (ChatObject.isSubscriptionChannel(currentChat) && !currentChat!!.creator) {
					subscriptionExpireRow = rowCount++
				}

				if (ChatObject.isOnlineCourse(currentChat)) {
					subscriptionBeginRow = rowCount++
					subscriptionExpireRow = rowCount++
				}

				if (ChatObject.isPaidChannel(currentChat)) {
					subscriptionCostRow = rowCount++
					subscriptionCostSectionRow = rowCount++
				}

				if (chatInfo != null && (currentChat!!.creator || chatInfo!!.can_view_participants)) {
					// membersHeaderRow = rowCount++
					subscribersRow = rowCount++

					if (chatInfo!!.requests_pending > 0) {
						subscribersRequestsRow = rowCount++
					}

					administratorsRow = rowCount++

					if (chatInfo!!.banned_count != 0 || chatInfo!!.kicked_count != 0) {
						blockedUsersRow = rowCount++
					}

					membersSectionRow = rowCount++
				}
			}

			if (ChatObject.isChannel(currentChat)) {
				if (chatInfo != null && currentChat!!.megagroup && chatInfo!!.participants != null && chatInfo!!.participants.participants.isNotEmpty()) {
					if (!ChatObject.isNotInChat(currentChat) && ChatObject.canAddUsers(currentChat) && chatInfo!!.participants_count < messagesController.maxMegagroupCount) {
						addMemberRow = rowCount++
					}

					val count = chatInfo!!.participants.participants.size

					if ((count <= 5 || !hasMedia || usersForceShowingIn == 1) && usersForceShowingIn != 2) {
						if (addMemberRow == -1) {
							membersHeaderRow = rowCount++
						}

						membersStartRow = rowCount
						rowCount += count
						membersEndRow = rowCount
						membersSectionRow = rowCount++
						visibleChatParticipants.addAll(chatInfo!!.participants.participants)

						if (sortedUsers != null) {
							visibleSortedUsers.addAll(sortedUsers!!)
						}

						usersForceShowingIn = 1

						sharedMediaLayout?.setChatUsers(null, null)
					}
					else {
						if (addMemberRow != -1) {
							membersSectionRow = rowCount++
						}

						if (sharedMediaLayout != null) {
							if (!sortedUsers.isNullOrEmpty()) {
								usersForceShowingIn = 2
							}

							sharedMediaLayout?.setChatUsers(sortedUsers, chatInfo)
						}
					}
				}

				if (lastSectionRow == -1 && currentChat!!.left && !currentChat!!.kicked) {
					joinRow = rowCount++
					lastSectionRow = rowCount++
				}
			}
			else if (chatInfo != null) {
				if (chatInfo!!.participants !is TLRPC.TL_chatParticipantsForbidden) {

					if (ChatObject.canAddUsers(currentChat) || currentChat!!.default_banned_rights == null || !currentChat!!.default_banned_rights.invite_users) {
						addMemberRow = rowCount++
					}

					val count = chatInfo!!.participants.participants.size

					if (count <= 5 || !hasMedia) {
						if (addMemberRow == -1) {
							membersHeaderRow = rowCount++
						}

						membersStartRow = rowCount
						rowCount += chatInfo!!.participants.participants.size
						membersEndRow = rowCount
						membersSectionRow = rowCount++
						visibleChatParticipants.addAll(chatInfo!!.participants.participants)

						if (sortedUsers != null) {
							visibleSortedUsers.addAll(sortedUsers!!)
						}

						sharedMediaLayout?.setChatUsers(null, null)
					}
					else {
						if (addMemberRow != -1) {
							membersSectionRow = rowCount++
						}

						sharedMediaLayout?.setChatUsers(sortedUsers, chatInfo)
					}
				}
			}

			if (hasMedia) {
				sharedMediaRow = rowCount++
			}
		}

		if (sharedMediaRow == -1) {
			bottomPaddingRow = rowCount++
		}

		val actionBarHeight = if (actionBar != null) ActionBar.getCurrentActionBarHeight() + (if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0) else 0

		if (listView == null || prevRowsCount > rowCount || listContentHeight != 0 && listContentHeight + actionBarHeight + AndroidUtilities.dp(88f) < listView!!.measuredHeight) {
			lastMeasuredContentWidth = 0
		}
	}

	private fun getScamDrawable(type: Int): Drawable {
		return scamDrawable ?: ScamDrawable(11, type).apply {
			setColor(ResourcesCompat.getColor(context!!.resources, R.color.brand, null))
		}.also {
			scamDrawable = it
		}
	}

	private fun getLockIconDrawable(): Drawable {
		return lockIconDrawable ?: Theme.chat_lockIconDrawable.constantState!!.newDrawable().mutate().also {
			lockIconDrawable = it
		}
	}

//	private fun getVerifiedCrossfadeDrawable(): Drawable? {
//		if (verifiedCrossfadeDrawable == null) {
//			verifiedDrawable = Theme.profile_verifiedDrawable?.constantState?.newDrawable()?.mutate()
//			verifiedCheckDrawable = Theme.profile_verifiedCheckDrawable?.constantState?.newDrawable()?.mutate()
//
//			if (verifiedDrawable != null && verifiedCheckDrawable != null) {
//				verifiedDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand, null), PorterDuff.Mode.MULTIPLY)
//
//				verifiedCrossfadeDrawable = CrossfadeDrawable(CombinedDrawable(verifiedDrawable, verifiedCheckDrawable), ContextCompat.getDrawable(parentActivity, R.drawable.verified_profile))
//			}
//		}
//
//		return verifiedCrossfadeDrawable
//	}

	private fun getPremiumCrossfadeDrawable(): Drawable? {
		val parentActivity = parentActivity ?: return null

		if (premiumCrossfadeDrawable == null) {
			premiumStarDrawable = ContextCompat.getDrawable(parentActivity, R.drawable.msg_premium_liststar)?.mutate()
			premiumStarDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(parentActivity.resources, R.color.brand, null), PorterDuff.Mode.MULTIPLY)

			if (premiumStarDrawable != null) {
				premiumCrossfadeDrawable = CrossfadeDrawable(premiumStarDrawable, ContextCompat.getDrawable(parentActivity, R.drawable.msg_premium_prolfilestar)!!.mutate())
			}
		}

		return premiumCrossfadeDrawable
	}

	private fun getEmojiStatusDrawable(emojiStatus: TLRPC.EmojiStatus?, animated: Boolean, a: Int): Drawable? {
		if (emojiStatusDrawable[a] == null) {
			emojiStatusDrawable[a] = AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameTextView[1], AndroidUtilities.dp(24f), if (a == 0) AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS else AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD)
		}

		val reportSpam = MessagesController.getNotificationsSettings(currentAccount).getBoolean("dialog_bar_report$dialogId", false)

		if (emojiStatus is TLRPC.TL_emojiStatus && !reportSpam) {
			emojiStatusDrawable[a]!![emojiStatus.document_id] = animated
		}
		else if (emojiStatus is TLRPC.TL_emojiStatusUntil && emojiStatus.until > (System.currentTimeMillis() / 1000).toInt() && !reportSpam) {
			emojiStatusDrawable[a]!![emojiStatus.document_id] = animated
		}
		else {
			emojiStatusDrawable[a]!![getPremiumCrossfadeDrawable()] = animated
		}

		updateEmojiStatusDrawableColor()

		return emojiStatusDrawable[a]
	}

	private fun updateEmojiStatusDrawableColor(progress: Float = lastEmojiStatusProgress) {
		val context = context ?: return
		val color = ColorUtils.blendARGB(AndroidUtilities.getOffsetColor(ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), mediaHeaderAnimationProgress, 1.0f), -0x1, progress)

		for (a in 0..1) {
			emojiStatusDrawable[a]?.color = color
		}

		animatedStatusView?.setColor(color)

		lastEmojiStatusProgress = progress
	}

	private fun updateEmojiStatusEffectPosition() {
		animatedStatusView?.scaleX = nameTextView[1]!!.scaleX
		animatedStatusView?.scaleY = nameTextView[1]!!.scaleY
		animatedStatusView?.translate(nameTextView[1]!!.x + nameTextView[1]!!.rightDrawableX * nameTextView[1]!!.scaleX, nameTextView[1]!!.y + (nameTextView[1]!!.height - (nameTextView[1]!!.height - nameTextView[1]!!.rightDrawableY) * nameTextView[1]!!.scaleY))
	}

	private fun getUserAccountTypeString(): SpannedString {
		val context = context ?: return SpannedString("")
		val user = messagesController.getUser(userId) ?: return SpannedString("")
		val privacyType = if (user.is_public) context.getString(R.string.public_account) else context.getString(R.string.private_account)
		val personalType = if (user.is_business) context.getString(R.string.business) else context.getString(R.string.personal)
		val username = user.username?.run { "@$this" }

		return buildSpannedString {
			inSpans(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, if (isPulledDown) R.color.white else R.color.dark, null))) {
				append("$personalType (${privacyType})")
			}

			if (!username.isNullOrEmpty()) {
				inSpans(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, if (isPulledDown) R.color.gray_border else R.color.disabled_text, null))) {
					append(" • $username")
				}
			}
		}
	}

	private fun isPublicProfile(): Boolean {
		val user = messagesController.getUser(userId)

		return user?.is_public == true
	}

	private fun updateProfileData(reload: Boolean) {
		val parentActivity = parentActivity ?: return

//		// MARK: check avatars loading here
//		if (avatarContainer == null) {
//			return
//		}

		val onlineTextOverride: String?
		val currentConnectionState = connectionsManager.getConnectionState()

		onlineTextOverride = when (currentConnectionState) {
			ConnectionsManager.ConnectionStateWaitingForNetwork -> parentActivity.getString(R.string.WaitingForNetwork)
			ConnectionsManager.ConnectionStateConnecting -> parentActivity.getString(R.string.Connecting)
			ConnectionsManager.ConnectionStateUpdating -> parentActivity.getString(R.string.Updating)
			ConnectionsManager.ConnectionStateConnectingToProxy -> parentActivity.getString(R.string.ConnectingToProxy)
			else -> null
		}

		if (userId != 0L) {
			val user = messagesController.getUser(userId)

			if (user == null) {
				messagesController.loadFullUser(TL_user().apply { id = userId }, classGuid, true)
				return
			}

			val photoBig = user.photo?.photo_big

			avatarDrawable?.setInfo(user)

			val imageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_BIG)
			val thumbLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL)
			val videoThumbLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_VIDEO_THUMB)

			val videoLocation = if (imageLocation != null) {
				avatarsViewPager?.getCurrentVideoLocation(thumbLocation, imageLocation)
			}
			else {
				null
			}

			avatarsViewPager?.initIfEmpty(imageLocation, thumbLocation, reload)

			if (avatarBig == null) {
				if (videoThumbLocation != null) {
					avatarImage?.imageReceiver?.setVideoThumbIsSame(true)
					avatarImage?.setImage(videoLocation, ImageLoader.AUTOPLAY_FILTER, videoThumbLocation, "avatar", avatarDrawable, user)
				}
				else {
					avatarImage?.setImage(videoLocation, ImageLoader.AUTOPLAY_FILTER, thumbLocation, "50_50", avatarDrawable, user)
				}
			}

			if (thumbLocation != null && setAvatarRow != -1 || thumbLocation == null && setAvatarRow == -1) {
				updateListAnimated(false)
				needLayout(true)
			}

			if (imageLocation != null && (prevLoadedImageLocation == null || imageLocation.photoId != prevLoadedImageLocation!!.photoId)) {
				prevLoadedImageLocation = imageLocation
				fileLoader.loadFile(imageLocation, user, null, FileLoader.PRIORITY_LOW, 1)
			}

			if (user.self) {
				for (row in getFilteredAccountsRows()) {
					listAdapter?.notifyItemChanged(row)
				}
			}

			var newString: CharSequence? = UserObject.getUserName(user)
			val newString2: String

			if (user.id == 333000L || user.id == 777000L || user.id == 42777L) {
				newString2 = parentActivity.getString(R.string.ServiceNotifications)
			}
			else if (MessagesController.isSupportUser(user)) {
				newString2 = parentActivity.getString(R.string.SupportStatus)
			}
			else if (isBot) {
				newString2 = if (user.id == BuildConfig.SUPPORT_BOT_ID) parentActivity.getString(R.string.customer_service) else parentActivity.getString(R.string.Bot)
			}
			else {
				isOnline[0] = false
				newString2 = LocaleController.formatUserStatus(currentAccount, user, isOnline)
			}

			if (onlineTextView[1] != null && !mediaHeaderVisible) {
				if (!isPulledDown) {
					onlineTextView[1]?.textColor = ResourcesCompat.getColor(parentActivity.resources, R.color.dark_gray, null)
				}
			}

			runCatching {
				newString = Emoji.replaceEmoji(newString, nameTextView[1]!!.paint.fontMetricsInt, false)
			}

			for (a in 0..1) {
				if (nameTextView[a] == null) {
					continue
				}

				nameTextView[a]?.setText(newString)

				if (a == 0 && onlineTextOverride != null) {
					onlineTextView[a]?.setText(onlineTextOverride)
				}
				else {
					onlineTextView[a]?.setText(if (user.id == userConfig.getClientUserId()) getUserAccountTypeString() else newString2)
				}

				val leftIcon = createCombinedChatPropertiesDrawable(currentChat, context!!)
				var rightIcon: Drawable? = null
				var rightIconIsPremium = false
				var rightIconIsStatus = false

				nameTextView[a]?.rightDrawableOutside = a == 0

				if (a == 0) {
					if (user.scam || user.fake) {
						rightIcon = getScamDrawable(if (user.scam) 0 else 1)
						nameTextViewRightDrawableContentDescription = parentActivity.getString(R.string.ScamMessage)
					}
					else if (user.verified) {
						rightIcon = ResourcesCompat.getDrawable(parentActivity.resources, R.drawable.verified_icon, null)
						nameTextViewRightDrawableContentDescription = parentActivity.getString(R.string.AccDescrVerified)
					}
					else if (user.emoji_status is TLRPC.TL_emojiStatus || user.emoji_status is TLRPC.TL_emojiStatusUntil && (user.emoji_status as TLRPC.TL_emojiStatusUntil).until > (System.currentTimeMillis() / 1000).toInt()) {
						rightIconIsStatus = true
						rightIconIsPremium = false
						rightIcon = getEmojiStatusDrawable(user.emoji_status, false, a)
						nameTextViewRightDrawableContentDescription = parentActivity.getString(R.string.AccDescrPremium)
					}
					else if (messagesController.isPremiumUser(user)) {
						rightIconIsStatus = false
						rightIconIsPremium = true
						rightIcon = getEmojiStatusDrawable(null, false, a)
						nameTextViewRightDrawableContentDescription = parentActivity.getString(R.string.AccDescrPremium)
					}
					else if (messagesController.isDialogMuted(if (dialogId != 0L) dialogId else userId)) {
						rightIcon = ResourcesCompat.getDrawable(parentActivity.resources, R.drawable.msg_mute, null)
						nameTextViewRightDrawableContentDescription = parentActivity.getString(R.string.NotificationsMuted)
					}
					else {
						rightIcon = null
						nameTextViewRightDrawableContentDescription = null
					}
				}
				else if (a == 1) {
					if (user.scam || user.fake) {
						rightIcon = getScamDrawable(if (user.scam) 0 else 1)
					}
					else if (user.verified) {
						rightIcon = ResourcesCompat.getDrawable(parentActivity.resources, R.drawable.verified_icon, null)
					}
					else if (user.emoji_status is TLRPC.TL_emojiStatus || user.emoji_status is TLRPC.TL_emojiStatusUntil && (user.emoji_status as TLRPC.TL_emojiStatusUntil).until > (System.currentTimeMillis() / 1000).toInt()) {
						rightIconIsStatus = true
						rightIconIsPremium = false
						rightIcon = getEmojiStatusDrawable(user.emoji_status, true, a)
					}
					else if (messagesController.isPremiumUser(user)) {
						rightIconIsStatus = false
						rightIconIsPremium = true
						rightIcon = getEmojiStatusDrawable(null, true, a)
					}
				}

				nameTextView[a]?.leftDrawable = leftIcon
				nameTextView[a]?.rightDrawable = rightIcon
				nameTextView[a]?.setDrawablePadding(AndroidUtilities.dp(4f))

				if (a == 1 && (rightIconIsStatus || rightIconIsPremium)) {
					nameTextView[a]!!.rightDrawableOutside = true
				}

				if (user.self) {
					val request = ElloRpc.getSubscriptionsRequest(ElloRpc.SubscriptionType.ACTIVE_CHANNELS)

					connectionsManager.sendRequest(request) { response, _ ->
						if (response is TLRPC.TL_biz_dataRaw) {
							val subscriptions = response.readData<ElloRpc.Subscriptions>()

							paidSubscriptions = subscriptions?.items

							AndroidUtilities.runOnUIThread {
								listAdapter?.notifyItemChanged(subscriptionsRow)
							}
						}
					}
				}

				if (user.self && messagesController.isPremiumUser(user)) {
					nameTextView[a]!!.setRightDrawableOnClick {
						showStatusSelect()
					}
				}

				if (!user.self && messagesController.isPremiumUser(user)) {
					val textView = nameTextView[a]

					textView?.setRightDrawableOnClick {
						val premiumPreviewBottomSheet = PremiumPreviewBottomSheet(this@ProfileActivity, currentAccount, user, null)
						val coordinates = IntArray(2)

						textView.getLocationOnScreen(coordinates)

						premiumPreviewBottomSheet.startEnterFromX = textView.rightDrawableX.toFloat()
						premiumPreviewBottomSheet.startEnterFromY = textView.rightDrawableY.toFloat()
						premiumPreviewBottomSheet.startEnterFromScale = textView.scaleX
						premiumPreviewBottomSheet.startEnterFromX1 = textView.left.toFloat()
						premiumPreviewBottomSheet.startEnterFromY1 = textView.top.toFloat()
						premiumPreviewBottomSheet.startEnterFromView = textView

						if ((textView.rightDrawable === emojiStatusDrawable[1]) && (emojiStatusDrawable[1] != null) && emojiStatusDrawable[1]!!.drawable is AnimatedEmojiDrawable) {
							premiumPreviewBottomSheet.startEnterFromScale *= 0.98f

							val document = (emojiStatusDrawable[1]!!.drawable as AnimatedEmojiDrawable).document

							if (document != null) {
								val icon = BackupImageView(parentActivity)
								val filter = "160_160"
								val mediaLocation: ImageLocation?
								val mediaFilter: String
								val thumbDrawable = DocumentObject.getSvgThumb(document.thumbs, ResourcesCompat.getColor(it.context.resources, R.color.light_background, null), 0.2f)
								val thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)

								if (("video/webm" == document.mime_type)) {
									mediaLocation = ImageLocation.getForDocument(document)
									mediaFilter = filter + "_" + ImageLoader.AUTOPLAY_FILTER
									thumbDrawable?.overrideWidthAndHeight(512, 512)
								}
								else {
									if (thumbDrawable != null && MessageObject.isAnimatedStickerDocument(document, false)) {
										thumbDrawable.overrideWidthAndHeight(512, 512)
									}

									mediaLocation = ImageLocation.getForDocument(document)
									mediaFilter = filter
								}

								icon.setLayerNum(7)
								icon.setRoundRadius(AndroidUtilities.dp(4f))
								icon.setImage(mediaLocation, mediaFilter, ImageLocation.getForDocument(thumb, document), "140_140", thumbDrawable, document)

								if ((emojiStatusDrawable[1]!!.drawable as AnimatedEmojiDrawable).canOverrideColor()) {
									icon.setColorFilter(PorterDuffColorFilter(ResourcesCompat.getColor(parentActivity.resources, R.color.brand, null), PorterDuff.Mode.MULTIPLY))
								}
								else {
									premiumPreviewBottomSheet.statusStickerSet = MessageObject.getInputStickerSet(document)
								}

								premiumPreviewBottomSheet.overrideTitleIcon = icon
								premiumPreviewBottomSheet.isEmojiStatus = true
							}
						}

						showDialog(premiumPreviewBottomSheet)
					}
				}
			}

			previousTransitionFragment?.checkAndUpdateAvatar()

			avatarImage?.imageReceiver?.setVisible(!PhotoViewer.isShowingImage(photoBig), false)

			loadInviteLinks()
		}
		else if (chatId != 0L) {
			var chat = messagesController.getChat(chatId)

			if (chat != null) {
				currentChat = chat
			}
			else {
				chat = currentChat
			}

			val statusString: String
			val profileStatusString: String

			if (ChatObject.isChannel(chat)) {
				if (chatInfo == null || !currentChat!!.megagroup && (chatInfo!!.participants_count == 0 || ChatObject.hasAdminRights(currentChat) || chatInfo!!.can_view_participants)) {
					if (currentChat!!.megagroup) {
						profileStatusString = parentActivity.getString(R.string.Loading).lowercase()
						statusString = profileStatusString
					}
					else {
						if (ChatObject.isOnlineCourse(chat)) {
							profileStatusString = parentActivity.getString(R.string.online_course).lowercase()
							statusString = profileStatusString
						}
						else if (ChatObject.isSubscriptionChannel(chat) || ChatObject.isPaidChannel(chat)) {
							profileStatusString = parentActivity.getString(R.string.paid_channel).lowercase()
							statusString = profileStatusString
						}
						else if (chat.flags and TLRPC.CHAT_FLAG_IS_PUBLIC != 0) {
							profileStatusString = parentActivity.getString(R.string.ChannelPublic).lowercase()
							statusString = profileStatusString
						}
						else {
							profileStatusString = parentActivity.getString(R.string.ChannelPrivate).lowercase()
							statusString = profileStatusString
						}
					}
				}
				else {
					if (currentChat!!.megagroup) {
						if (onlineCount > 1 && chatInfo!!.participants_count != 0) {
							statusString = String.format("%s, %s", LocaleController.formatPluralString("Members", chatInfo!!.participants_count), LocaleController.formatPluralString("OnlineCount", min(onlineCount, chatInfo!!.participants_count)))
							profileStatusString = String.format("%s, %s", LocaleController.formatPluralStringComma("Members", chatInfo!!.participants_count), LocaleController.formatPluralStringComma("OnlineCount", min(onlineCount, chatInfo!!.participants_count)))
						}
						else {
							if (chatInfo!!.participants_count == 0) {
								if (chat.has_geo) {
									profileStatusString = parentActivity.getString(R.string.MegaLocation).lowercase()
									statusString = profileStatusString
								}
								else if (!TextUtils.isEmpty(chat.username)) {
									profileStatusString = parentActivity.getString(R.string.MegaPublic).lowercase()
									statusString = profileStatusString
								}
								else {
									profileStatusString = parentActivity.getString(R.string.MegaPrivate).lowercase()
									statusString = profileStatusString
								}
							}
							else {
								statusString = LocaleController.formatPluralString("Members", chatInfo!!.participants_count)
								profileStatusString = LocaleController.formatPluralStringComma("Members", chatInfo!!.participants_count)
							}
						}
					}
					else {
						statusString = LocaleController.formatPluralString("Subscribers", chatInfo!!.participants_count)
						profileStatusString = LocaleController.formatPluralStringComma("Subscribers", chatInfo!!.participants_count)
					}
				}
			}
			else {
				if (ChatObject.isKickedFromChat(chat)) {
					profileStatusString = parentActivity.getString(R.string.YouWereKicked)
					statusString = profileStatusString
				}
				else if (ChatObject.isLeftFromChat(chat)) {
					profileStatusString = parentActivity.getString(R.string.YouLeft)
					statusString = profileStatusString
				}
				else {
					var count = chat!!.participants_count

					if (chatInfo != null) {
						count = chatInfo!!.participants.participants.size
					}

					if (count != 0 && onlineCount > 1) {
						profileStatusString = String.format("%s, %s", LocaleController.formatPluralString("Members", count), LocaleController.formatPluralString("OnlineCount", onlineCount))
						statusString = profileStatusString
					}
					else {
						profileStatusString = LocaleController.formatPluralString("Members", count)
						statusString = profileStatusString
					}
				}
			}

			var changed = false

			for (a in 0..1) {
				if (nameTextView[a] == null) {
					continue
				}

				if (chat?.title != null) {
					var title: CharSequence? = chat.title

					try {
						title = Emoji.replaceEmoji(title, nameTextView[a]!!.paint.fontMetricsInt, false)
					}
					catch (ignore: Exception) {
					}

					if (nameTextView[a]!!.setText(title)) {
						changed = true
					}
				}

				nameTextView[a]?.rightDrawableOutside = (a == 0)
				nameTextView[a]?.setLeftDrawableTopPadding(0)
				nameTextView[a]?.leftDrawable = createCombinedChatPropertiesDrawable(chat, context!!)

				if (isPulledDown) {
					nameTextView[a]?.leftDrawable?.setTint(context!!.getColor(R.color.white))
				}
				else {
					nameTextView[a]?.leftDrawable?.setTint(context!!.getColor(R.color.dark))
				}

				nameTextView[a]?.rightDrawable = null

				nameTextViewRightDrawableContentDescription = null

				if (a != 0) {
					if (chat?.scam == true || chat?.fake == true) {
						nameTextView[a]?.rightDrawable = getScamDrawable(if (chat.scam) 0 else 1)
						nameTextViewRightDrawableContentDescription = parentActivity.getString(R.string.ScamMessage)
					}
					else if (chat?.verified == true) {
						nameTextView[a]?.rightDrawable = ResourcesCompat.getDrawable(parentActivity.resources, R.drawable.verified_icon, null)
						nameTextViewRightDrawableContentDescription = parentActivity.getString(R.string.AccDescrVerified)
					}
				}
				else {
					if (chat?.scam == true || chat?.fake == true) {
						nameTextView[a]?.rightDrawable = getScamDrawable(if (chat.scam) 0 else 1)
					}
					else if (chat?.verified == true) {
						nameTextView[a]?.rightDrawable = ResourcesCompat.getDrawable(parentActivity.resources, R.drawable.verified_icon, null)
					}
					else if (messagesController.isDialogMuted(-chatId)) {
						nameTextView[a]?.rightDrawable = ResourcesCompat.getDrawable(parentActivity.resources, R.drawable.msg_mute, null)
					}
				}

				if (a == 0 && onlineTextOverride != null) {
					onlineTextView[a]!!.setText(onlineTextOverride)
				}
				else {
					if (currentChat!!.megagroup && chatInfo != null && onlineCount > 0) {
						onlineTextView[a]!!.setText(if (a == 0) statusString else profileStatusString)
					}
					else if (a == 0 && ChatObject.isChannel(currentChat) && chatInfo != null && chatInfo!!.participants_count != 0 && (currentChat!!.megagroup || currentChat!!.broadcast)) {
						val result = IntArray(1)
						val shortNumber = LocaleController.formatShortNumber(chatInfo!!.participants_count, result)

						if (currentChat!!.megagroup) {
							if (chatInfo!!.participants_count == 0) {
								if (chat?.has_geo == true) {
									onlineTextView[a]!!.setText(parentActivity.getString(R.string.MegaLocation).lowercase())
								}
								else if (!chat?.username.isNullOrEmpty()) {
									onlineTextView[a]!!.setText(parentActivity.getString(R.string.MegaPublic).lowercase())
								}
								else {
									onlineTextView[a]!!.setText(parentActivity.getString(R.string.MegaPrivate).lowercase())
								}
							}
							else {
								onlineTextView[a]!!.setText(LocaleController.formatPluralString("Members", result[0]).replace(String.format("%d", result[0]), shortNumber))
							}
						}
						else {
							onlineTextView[a]!!.setText(LocaleController.formatPluralString("Subscribers", result[0]).replace(String.format("%d", result[0]), shortNumber))
						}
					}
					else {
						onlineTextView[a]!!.setText(if (a == 0) statusString else profileStatusString)
					}
				}
			}

			if (changed) {
				needLayout(true)
			}

			var photoBig: TLRPC.FileLocation? = null

			if (chat?.photo != null) {
				photoBig = chat.photo.photo_big
			}

			avatarDrawable?.setInfo(chat)

			val imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_BIG)
			val thumbLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL)
			val videoLocation = avatarsViewPager?.getCurrentVideoLocation(thumbLocation, imageLocation)
			val initialized = avatarsViewPager?.initIfEmpty(imageLocation, thumbLocation, reload) ?: false

			if ((imageLocation == null || initialized) && isPulledDown) {
				val view = layoutManager!!.findViewByPosition(0)

				if (view != null) {
					listView?.smoothScrollBy(0, view.top - AndroidUtilities.dp(88f), CubicBezierInterpolator.EASE_OUT_QUINT)
				}
			}

			val filter = if (videoLocation != null && videoLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
				ImageLoader.AUTOPLAY_FILTER
			}
			else {
				null
			}

			if (avatarBig == null) {
				avatarImage?.setImage(videoLocation, filter, thumbLocation, "50_50", avatarDrawable, chat)
			}

			if (imageLocation != null && (prevLoadedImageLocation == null || imageLocation.photoId != prevLoadedImageLocation!!.photoId)) {
				prevLoadedImageLocation = imageLocation
				fileLoader.loadFile(imageLocation, chat, null, FileLoader.PRIORITY_LOW, 1)
			}

			avatarImage?.imageReceiver?.setVisible(!PhotoViewer.isShowingImage(photoBig), false)
		}

		if (qrItem != null) {
			updateQrItemVisibility(true)
		}

		AndroidUtilities.runOnUIThread {
			updateEmojiStatusEffectPosition()
		}
	}

	private fun createActionBarMenu(animated: Boolean) {
		if (actionBar == null || otherItem == null) {
			return
		}

		val context = actionBar?.context ?: return

		otherItem?.removeAllSubItems()
		animatingItem = null
		editItemVisible = false
		callItemVisible = false
		videoCallItemVisible = false
		canSearchMembers = false

		var selfUser = false

		if (userId != 0L) {
			val user = messagesController.getUser(userId) ?: return

			if (isSelf()) {
				// otherItem?.addSubItem(edit_name, R.drawable.msg_edit, context.getString(R.string.EditName))
				editItemVisible = true
				selfUser = true
			}
			else {
				if (userInfo?.phone_calls_available == true && userInfo?.id != 333000L && userInfo?.id != 777000L && userInfo?.id != 42777L) {
					callItemVisible = true
					// MARK: uncomment to enable video calls
					// videoCallItemVisible = userInfo!!.video_calls_available
					videoCallItemVisible = false
				}

				if (isBot || contactsController.contactsDict[userId] == null) {
					if (MessagesController.isSupportUser(user)) {
						if (userBlocked) {
							val item = otherItem?.addSubItem(block_contact, R.drawable.msg_block, context.getString(R.string.Unblock))
							item?.setColors(ResourcesCompat.getColor(context.resources, R.color.purple, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))
						}
					}
					else {
//						if (currentEncryptedChat == null) {
//							createAutoDeleteItem(context)
//						}

						if (isBot) {
							otherItem?.addSubItem(share, R.drawable.msg_share, context.getString(R.string.BotShare))
						}
						else {
							otherItem?.addSubItem(add_contact, R.drawable.msg_addcontact, context.getString(R.string.AddContact))
						}

						if (user.is_public) {
							otherItem?.addSubItem(share_contact, R.drawable.msg_share, context.getString(R.string.ShareContact))
						}

						if (isBot) {
							otherItem?.addSubItem(clear_history, R.drawable.msg_clear, context.getString(R.string.ClearHistory))
						}

						val item = if (isBot) {
							otherItem?.addSubItem(block_contact, if (!userBlocked) R.drawable.msg_block else R.drawable.msg_retry, if (!userBlocked) context.getString(R.string.BotStop) else context.getString(R.string.BotRestart))
						}
						else {
							otherItem?.addSubItem(block_contact, if (!userBlocked) R.drawable.msg_block else R.drawable.msg_block, if (!userBlocked) context.getString(R.string.BlockContact) else context.getString(R.string.Unblock))
						}

						item?.setColors(ResourcesCompat.getColor(context.resources, R.color.purple, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))

					}
				}
				else {
//					if (currentEncryptedChat == null) {
//						createAutoDeleteItem(context)
//					}

					if (user.is_public) {
						otherItem?.addSubItem(share_contact, R.drawable.msg_share, context.getString(R.string.ShareContact))
					}

					val item = otherItem?.addSubItem(block_contact, if (!userBlocked) R.drawable.msg_block else R.drawable.msg_block, if (!userBlocked) context.getString(R.string.BlockContact) else context.getString(R.string.Unblock))
					item?.setColors(ResourcesCompat.getColor(context.resources, R.color.purple, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))

					otherItem?.addSubItem(edit_contact, R.drawable.msg_edit, context.getString(R.string.EditContact))
					otherItem?.addSubItem(delete_contact, R.drawable.msg_delete, context.getString(R.string.DeleteContact))
				}

				// TODO: uncomment to enable "Gift Premium" and "Start secret chat" actions
//				if (!UserObject.isDeleted(user) && !isBot && currentEncryptedChat == null && !userBlocked && userId != 333000L && userId != 777000L && userId != 42777L) {
//					if (!user.premium && !BuildVars.IS_BILLING_UNAVAILABLE && !user.self && userInfo != null && !messagesController.premiumLocked && userInfo!!.premium_gifts.isNotEmpty()) {
//						otherItem?.addSubItem(gift_premium, R.drawable.msg_gift_premium, LocaleController.getString(R.string.GiftPremium))
//					}
//
//					otherItem?.addSubItem(start_secret_chat, R.drawable.msg_secret, context.getString(R.string.StartEncryptedChat))
//				}

				// TODO: uncomment to enable "Add to Home screen" action
				// otherItem?.addSubItem(add_shortcut, R.drawable.msg_home, context.getString(R.string.AddShortcut))
			}
		}
		else if (chatId != 0L) {
			val chat = messagesController.getChat(chatId)

			hasVoiceChatItem = false

//			if (ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_DELETE_MESSAGES)) {
//				createAutoDeleteItem(context)
//			}

			if (ChatObject.isChannel(chat)) {
				if (ChatObject.hasAdminRights(chat) || (chat.megagroup && ChatObject.canChangeChatInfo(chat))) {
					editItemVisible = true
				}

				if (chatInfo != null) {
					// MARK: enable voice call for channel
//					if (ChatObject.canManageCalls(chat) && chatInfo!!.call == null) {
//						otherItem?.addSubItem(call_item, R.drawable.msg_voicechat, if (chat.megagroup && !chat.gigagroup) context.getString(R.string.StartVoipChat) else context.getString(R.string.StartVoipChannel))
//						hasVoiceChatItem = true
//					}

					if (chatInfo?.can_view_stats == true && !chat.megagroup && (chat.creator || chat.admin_rights != null)) {
						otherItem?.addSubItem(statistics, R.drawable.msg_stats, context.getString(R.string.ViewAnalytics))
					}

					val call = messagesController.getGroupCall(chatId, false)

					callItemVisible = call != null
				}

				if (chat.megagroup) {
					canSearchMembers = true
					otherItem?.addSubItem(search_members, R.drawable.msg_search, context.getString(R.string.SearchMembers))

					if (!chat.creator && !chat.left && !chat.kicked) {
						val item = otherItem?.addSubItem(leave_group, R.drawable.msg_leave, context.getString(R.string.LeaveMegaMenu))
						item?.setColors(ResourcesCompat.getColor(context.resources, R.color.purple, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))
					}
				}
				else {
					if (!TextUtils.isEmpty(chat.username)) {
						otherItem?.addSubItem(share, R.drawable.msg_share, context.getString(R.string.BotShare))
					}
					if (chatInfo != null && chatInfo!!.linked_chat_id != 0L) {
						otherItem?.addSubItem(view_discussion, R.drawable.msg_discussion, context.getString(R.string.ViewDiscussion))
					}
					if (!currentChat!!.creator && !currentChat!!.left && !currentChat!!.kicked) {
						val item = otherItem?.addSubItem(leave_group, R.drawable.msg_leave, context.getString(R.string.LeaveChannelMenu))
						item?.setColors(ResourcesCompat.getColor(context.resources, R.color.purple, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))
					}
				}
			}
			else {
				// MARK: uncomment to enable calls in groups
//				if (chatInfo != null) {
//					if (ChatObject.canManageCalls(chat) && chatInfo!!.call == null) {
//						otherItem?.addSubItem(call_item, R.drawable.msg_voicechat, context.getString(R.string.StartVoipChat))
//						hasVoiceChatItem = true
//					}
//
//					val call = messagesController.getGroupCall(chatId, false)
//
//					callItemVisible = call != null
//				}

				if (ChatObject.canChangeChatInfo(chat)) {
					editItemVisible = true
				}

				if (!ChatObject.isKickedFromChat(chat) && !ChatObject.isLeftFromChat(chat)) {
					canSearchMembers = true
					otherItem?.addSubItem(search_members, R.drawable.msg_search, context.getString(R.string.SearchMembers))
				}

				val item = otherItem?.addSubItem(leave_group, R.drawable.msg_leave, context.getString(R.string.DeleteAndExit))
				item?.setColors(ResourcesCompat.getColor(context.resources, R.color.purple, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))
			}

			// TODO: uncomment to enable "Add to Home screen" action
			// otherItem?.addSubItem(add_shortcut, R.drawable.msg_home, context.getString(R.string.AddShortcut))
		}

		if (imageUpdater != null) {
			// otherItem?.addSubItem(add_photo, R.drawable.msg_addphoto, context.getString(R.string.AddPhoto))
			otherItem?.addSubItem(set_as_main, R.drawable.msg_openprofile, context.getString(R.string.SetAsMain))
			otherItem?.addSubItem(gallery_menu_save, R.drawable.msg_gallery, context.getString(R.string.SaveToGallery))
			//otherItem?.addSubItem(edit_avatar, R.drawable.photo_paint, context.getString(R.string.EditPhoto));
			otherItem?.addSubItem(delete_avatar, R.drawable.msg_delete, context.getString(R.string.Delete))
		}
		else {
			otherItem?.addSubItem(gallery_menu_save, R.drawable.msg_gallery, context.getString(R.string.SaveToGallery))
		}

		if (messagesController.isChatNoForwards(currentChat)) {
			otherItem?.hideSubItem(gallery_menu_save)
		}

		if (selfUser) {
			val item = otherItem?.addSubItem(logout, R.drawable.msg_leave, context.getString(R.string.LogOut))
			item?.setColors(ResourcesCompat.getColor(context.resources, R.color.purple, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))
			// otherItem?.visibility = View.GONE
		}

		if (userInfo?.id == 777000L) { // hide overflow menu for service notifications
			otherItem?.gone()
		}

		if (!isPulledDown) {
			otherItem?.hideSubItem(gallery_menu_save)
			otherItem?.hideSubItem(set_as_main)
			otherItem?.showSubItem(add_photo)
			otherItem?.hideSubItem(edit_avatar)
			otherItem?.hideSubItem(delete_avatar)
		}

		if (!mediaHeaderVisible) {
			if (callItemVisible) {
				if (callItem?.visibility != View.VISIBLE) {
					callItem?.visibility = View.VISIBLE

					if (animated) {
						callItem?.alpha = 0f
						callItem?.animate()?.alpha(1f)?.setDuration(150)?.start()
					}
				}
			}
			else {
				callItem?.visibility = View.GONE
			}

			if (videoCallItemVisible) {
				if (videoCallItem?.visibility != View.VISIBLE) {
					videoCallItem?.visibility = View.VISIBLE

					if (animated) {
						videoCallItem?.alpha = 0f
						videoCallItem?.animate()?.alpha(1f)?.setDuration(150)?.start()
					}
				}
			}
			else {
				videoCallItem?.visibility = View.GONE
			}
			if (editItemVisible) {
				if (editItem?.visibility != View.VISIBLE) {
					editItem?.visibility = View.VISIBLE

					if (animated) {
						editItem?.alpha = 0f
						editItem?.animate()?.alpha(1f)?.setDuration(150)?.start()
					}
				}
			}
			else {
				editItem?.visibility = View.GONE
			}
		}

		if (avatarsViewPagerIndicatorView != null) {
			if (avatarsViewPagerIndicatorView!!.isIndicatorFullyVisible) {
				if (editItemVisible) {
					editItem?.visibility = View.GONE
					editItem?.animate()?.cancel()
					editItem?.alpha = 1f
				}

				if (callItemVisible) {
					callItem?.visibility = View.GONE
					callItem?.animate()?.cancel()
					callItem?.alpha = 1f
				}

				if (videoCallItemVisible) {
					videoCallItem?.visibility = View.GONE
					videoCallItem?.animate()?.cancel()
					videoCallItem?.alpha = 1f
				}
			}
		}

		sharedMediaLayout?.searchItem?.requestLayout()
	}

//	private fun createAutoDeleteItem(context: Context) {
//		autoDeletePopupWrapper = AutoDeletePopupWrapper(context, otherItem!!.popupLayout.swipeBack, object : AutoDeletePopupWrapper.Callback {
//			override fun dismiss() {
//				otherItem?.toggleSubMenu()
//			}
//
//			override fun setAutoDeleteHistory(time: Int, action: Int) {
//				this@ProfileActivity.setAutoDeleteHistory(time, action)
//			}
//		}, false)
//
//		var ttl = 0
//
//		if (userInfo != null || chatInfo != null) {
//			ttl = if (userInfo != null) userInfo!!.ttl_period else chatInfo!!.ttl_period
//		}
//
//		autoDeleteItemDrawable = TimerDrawable.getTtlIcon(ttl)
//
//		autoDeleteItem = otherItem?.addSwipeBackItem(0, autoDeleteItemDrawable, context.getString(R.string.AutoDeletePopupTitle), autoDeletePopupWrapper!!.windowLayout)
//
//		otherItem?.addColoredGap()
//
//		updateAutoDeleteItem()
//	}

//	private fun setAutoDeleteHistory(time: Int, action: Int) {
//		val did = getDialogId()
//
//		messagesController.setDialogHistoryTTL(did, time)
//
//		if (userInfo != null || chatInfo != null) {
//			undoView?.showWithAction(did, action, messagesController.getUser(did), if (userInfo != null) userInfo!!.ttl_period else chatInfo!!.ttl_period, null, null)
//		}
//	}

	override fun onDialogDismiss(dialog: Dialog) {
		listView?.invalidateViews()
	}

	override fun didSelectDialogs(dialogsFragment: DialogsActivity?, dids: List<Long>, message: CharSequence?, param: Boolean) {
		val did = dids[0]
		val args = Bundle()

		args.putBoolean("scrollToTopOnResume", true)

		if (DialogObject.isEncryptedDialog(did)) {
			args.putInt("enc_id", DialogObject.getEncryptedChatId(did))
		}
		else if (DialogObject.isUserDialog(did)) {
			args.putLong("user_id", did)
		}
		else if (DialogObject.isChatDialog(did)) {
			args.putLong("chat_id", -did)
		}

		if (!messagesController.checkCanOpenChat(args, dialogsFragment)) {
			return
		}

		notificationCenter.removeObserver(this, NotificationCenter.closeChats)
		notificationCenter.postNotificationName(NotificationCenter.closeChats)

		presentFragment(ChatActivity(args), true)

		removeSelfFromStack()

		val user = messagesController.getUser(userId)

		sendMessagesHelper.sendMessage(user, did, null, null, null, null, true, 0, false, null)

		if (!message.isNullOrEmpty()) {
			val accountInstance = AccountInstance.getInstance(currentAccount)
			SendMessagesHelper.prepareSendingText(accountInstance, message.toString(), did, true, 0)
		}
	}

	override fun onRequestPermissionsResultFragment(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		val parentActivity = parentActivity ?: return

		imageUpdater?.onRequestPermissionsResultFragment(requestCode, permissions, grantResults)

		if (requestCode == 101 || requestCode == 102) {
			val user = messagesController.getUser(userId) ?: return
			var allGranted = true

			for (a in grantResults.indices) {
				if (grantResults[a] != PackageManager.PERMISSION_GRANTED) {
					allGranted = false
					break
				}
			}

			if (grantResults.isNotEmpty() && allGranted) {
				VoIPHelper.startCall(user, requestCode == 102, userInfo?.video_calls_available == true, parentActivity, userInfo, accountInstance)
			}
			else {
				VoIPHelper.permissionDenied(parentActivity, null, requestCode)
			}
		}
		else if (requestCode == 103) {
			if (currentChat == null) {
				return
			}

			var allGranted = true

			for (a in grantResults.indices) {
				if (grantResults[a] != PackageManager.PERMISSION_GRANTED) {
					allGranted = false
					break
				}
			}

			if (grantResults.isNotEmpty() && allGranted) {
				val call = messagesController.getGroupCall(chatId, false)
				VoIPHelper.startCall(currentChat!!, null, call == null, parentActivity, this@ProfileActivity, accountInstance)
			}
			else {
				VoIPHelper.permissionDenied(parentActivity, null, requestCode)
			}
		}
	}

	override fun dismissCurrentDialog() {
		if (imageUpdater?.dismissCurrentDialog(visibleDialog) == true) {
			return
		}

		super.dismissCurrentDialog()
	}

	override fun dismissDialogOnPause(dialog: Dialog): Boolean {
		return (imageUpdater == null || imageUpdater!!.dismissDialogOnPause(dialog)) && super.dismissDialogOnPause(dialog)
	}

//	private fun searchExpandTransition(enter: Boolean): Animator {
//		if (enter) {
//			AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
//			AndroidUtilities.setAdjustResizeToNothing(parentActivity, classGuid)
//		}
//
//		searchViewTransition?.removeAllListeners()
//		searchViewTransition?.cancel()
//
//		val valueAnimator = ValueAnimator.ofFloat(searchTransitionProgress, if (enter) 0f else 1f)
//		val offset = extraHeight
//
//		searchListView?.translationY = offset
//		searchListView?.visibility = View.VISIBLE
//		searchItem?.visibility = View.VISIBLE
//		listView?.visibility = View.VISIBLE
//
//		needLayout(true)
//
//		avatarContainer?.visibility = View.VISIBLE
//		nameTextView[1]?.visibility = View.VISIBLE
//		onlineTextView[1]?.visibility = View.VISIBLE
//		actionBar?.onSearchFieldVisibilityChanged(searchTransitionProgress > 0.5f)
//
//		val itemVisibility = if (searchTransitionProgress > 0.5f) View.VISIBLE else View.GONE
//
//		otherItem?.visibility = itemVisibility
//
//		if (qrItem != null) {
//			updateQrItemVisibility(false)
//		}
//
//		searchItem?.visibility = itemVisibility
//		searchItem?.searchContainer?.visibility = if (searchTransitionProgress > 0.5f) View.GONE else View.VISIBLE
//		searchListView?.setEmptyView(emptyView)
//		avatarContainer?.isClickable = false
//
//		valueAnimator.addUpdateListener {
//			searchTransitionProgress = valueAnimator.animatedValue as Float
//
//			var progressHalf = (searchTransitionProgress - 0.5f) / 0.5f
//			var progressHalfEnd = (0.5f - searchTransitionProgress) / 0.5f
//
//			if (progressHalf < 0) {
//				progressHalf = 0f
//			}
//			if (progressHalfEnd < 0) {
//				progressHalfEnd = 0f
//			}
//
//			searchTransitionOffset = (-offset * (1f - searchTransitionProgress)).toInt()
//			searchListView?.translationY = offset * searchTransitionProgress
//			emptyView?.translationY = offset * searchTransitionProgress
//			listView?.translationY = -offset * (1f - searchTransitionProgress)
//			listView?.scaleX = 1f - 0.01f * (1f - searchTransitionProgress)
//			listView?.scaleY = 1f - 0.01f * (1f - searchTransitionProgress)
//			listView?.alpha = searchTransitionProgress
//
//			needLayout(true)
//
//			listView?.alpha = progressHalf
//			searchListView?.alpha = 1f - searchTransitionProgress
//			searchListView?.scaleX = 1f + 0.05f * searchTransitionProgress
//			searchListView?.scaleY = 1f + 0.05f * searchTransitionProgress
//			emptyView?.alpha = 1f - progressHalf
//			avatarContainer?.alpha = progressHalf
//			nameTextView[1]?.alpha = progressHalf
//			onlineTextView[1]?.alpha = progressHalf
//			searchItem?.searchField?.alpha = progressHalfEnd
//
//			if (enter && searchTransitionProgress < 0.7f) {
//				searchItem?.requestFocusOnSearchView()
//			}
//
//			searchItem?.searchContainer?.visibility = if (searchTransitionProgress < 0.5f) View.VISIBLE else View.GONE
//
//			val visibility = if (searchTransitionProgress > 0.5f) View.VISIBLE else View.GONE
//
//			otherItem?.visibility = visibility
//			otherItem?.alpha = progressHalf
//
//			if (qrItem != null) {
//				qrItem?.alpha = progressHalf
//				updateQrItemVisibility(false)
//			}
//
//			searchItem?.visibility = visibility
//			actionBar?.onSearchFieldVisibilityChanged(searchTransitionProgress < 0.5f)
//
//			otherItem?.alpha = progressHalf
//			searchItem?.alpha = progressHalf
//			topView?.invalidate()
//			fragmentView?.invalidate()
//		}
//
//		valueAnimator.addListener(object : AnimatorListenerAdapter() {
//			override fun onAnimationEnd(animation: Animator) {
//				updateSearchViewState(enter)
//
//				avatarContainer?.isClickable = true
//
//				if (enter) {
//					searchItem?.requestFocusOnSearchView()
//				}
//
//				needLayout(true)
//
//				searchViewTransition = null
//
//				fragmentView?.invalidate()
//
//				if (enter) {
//					invalidateScroll = true
//					saveScrollPosition()
//					AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
//					emptyView?.preventMoving = false
//				}
//			}
//		})
//
//		if (!enter) {
//			invalidateScroll = true
//			saveScrollPosition()
//			AndroidUtilities.requestAdjustNothing(parentActivity, classGuid)
//			emptyView?.preventMoving = true
//		}
//
//		valueAnimator.duration = 220
//		valueAnimator.interpolator = CubicBezierInterpolator.DEFAULT
//
//		searchViewTransition = valueAnimator
//
//		return valueAnimator
//	}

	private fun updateSearchViewState(enter: Boolean) {
		val hide = if (enter) View.GONE else View.VISIBLE
		listView?.visibility = hide
		searchListView?.visibility = if (enter) View.VISIBLE else View.GONE
		searchItem?.searchContainer?.visibility = if (enter) View.VISIBLE else View.GONE
		actionBar?.onSearchFieldVisibilityChanged(enter)
		avatarContainer?.visibility = hide
		nameTextView[1]?.visibility = hide
		onlineTextView[1]?.visibility = hide

		otherItem?.alpha = 1f
		otherItem?.visibility = hide

		qrItem?.alpha = 1f
		qrItem?.visibility = if (enter || !isQrNeedVisible) View.GONE else View.VISIBLE

		searchItem?.visibility = hide
		avatarContainer?.alpha = 1f
		nameTextView[1]?.alpha = 1f
		onlineTextView[1]?.alpha = 1f
		searchItem?.alpha = 1f
		listView?.alpha = 1f
		searchListView?.alpha = 1f
		emptyView?.alpha = 1f

		if (enter) {
			searchListView?.setEmptyView(emptyView)
		}
		else {
			emptyView?.visibility = View.GONE
		}
	}

	override fun onUploadProgressChanged(progress: Float) {
		if (avatarProgressView == null) {
			return
		}

		avatarProgressView?.setProgress(progress)
		avatarsViewPager?.setUploadProgress(uploadingImageLocation, progress)
	}

	override fun didStartUpload(isVideo: Boolean) {
		if (avatarProgressView == null) {
			return
		}

		avatarProgressView?.setProgress(0.0f)
	}

	override fun didUploadPhoto(photo: TLRPC.InputFile?, video: TLRPC.InputFile?, videoStartTimestamp: Double, videoPath: String?, bigSize: TLRPC.PhotoSize, smallSize: TLRPC.PhotoSize) {
		AndroidUtilities.runOnUIThread {
			if (photo != null || video != null) {
				val req = TLRPC.TL_photos_uploadProfilePhoto()

				if (photo != null) {
					req.file = photo
					req.flags = req.flags or 1
				}

				if (video != null) {
					req.video = video
					req.flags = req.flags or 2
					req.video_start_ts = videoStartTimestamp
					req.flags = req.flags or 4
				}

				connectionsManager.sendRequest(req) { response, error ->
					AndroidUtilities.runOnUIThread inner@{
						avatarsViewPager?.removeUploadingImage(uploadingImageLocation)

						if (error == null) {
							var user = messagesController.getUser(userConfig.getClientUserId())

							if (user == null) {
								user = userConfig.getCurrentUser()

								if (user == null) {
									return@inner
								}

								messagesController.putUser(user, false)
							}
							else {
								userConfig.setCurrentUser(user)
							}

							val photosPhoto = response as TL_photos_photo
							val sizes = photosPhoto.photo?.sizes
							val small = FileLoader.getClosestPhotoSizeWithSize(sizes, 150)
							val big = FileLoader.getClosestPhotoSizeWithSize(sizes, 800)
							val videoSize = photosPhoto.photo?.video_sizes?.firstOrNull()

							user.photo = TLRPC.TL_userProfilePhoto()
							user.photo?.photo_id = photosPhoto.photo?.id

							if (small != null) {
								user.photo?.photo_small = small.location
							}

							if (big != null) {
								user.photo?.photo_big = big.location
							}

							if (small != null && avatar != null) {
								val destFile = FileLoader.getInstance(currentAccount).getPathToAttach(small, true)
								val src = FileLoader.getInstance(currentAccount).getPathToAttach(avatar, true)

								src.renameTo(destFile)

								val oldKey = avatar?.volume_id?.toString() + "_" + avatar?.local_id + "@50_50"
								val newKey = small.location.volume_id.toString() + "_" + small.location.local_id + "@50_50"

								ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL)?.let {
									ImageLoader.instance.replaceImageInCache(oldKey, newKey, it, false)
								}
							}

							if (big != null && avatarBig != null) {
								val destFile = FileLoader.getInstance(currentAccount).getPathToAttach(big, true)
								val src = FileLoader.getInstance(currentAccount).getPathToAttach(avatarBig, true)
								src.renameTo(destFile)
							}

							if (videoSize != null && videoPath != null) {
								val destFile = FileLoader.getInstance(currentAccount).getPathToAttach(videoSize, "mp4", true)
								val src = File(videoPath)
								src.renameTo(destFile)
							}

							messagesStorage.clearUserPhotos(user.id)

							val users = ArrayList<User>()
							users.add(user)

							messagesStorage.putUsersAndChats(users, null, false, true)
						}

						allowPullingDown = !AndroidUtilities.isTablet() && !isInLandscapeMode && avatarImage!!.imageReceiver.hasNotThumb() && !AndroidUtilities.isAccessibilityScreenReaderEnabled()
						avatar = null
						avatarBig = null

						avatarsViewPager?.createThumbFromParent = false

						showAvatarProgress(show = false, animated = true)

						notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL)
						notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)

						userConfig.saveConfig(true)

						updateProfileData(true)
					}
				}
			}
			else {
				avatar = smallSize.location
				avatarBig = bigSize.location
				avatarImage?.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, null)

				if (setAvatarRow != -1) {
					updateRowsIds()
					listAdapter?.notifyDataSetChanged()
					needLayout(true)
				}

				avatarsViewPager?.addUploadingImage(ImageLocation.getForLocal(avatarBig).also { uploadingImageLocation = it }, ImageLocation.getForLocal(avatar))

				showAvatarProgress(show = true, animated = false)
			}

			actionBar?.createMenu()?.requestLayout()
		}
	}

	private fun showAvatarProgress(show: Boolean, animated: Boolean) {
		if (avatarProgressView == null) {
			return
		}

		avatarAnimation?.cancel()
		avatarAnimation = null

		if (animated) {
			avatarAnimation = AnimatorSet()

			if (show) {
				avatarProgressView?.visibility = View.VISIBLE
				avatarAnimation?.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f))
			}
			else {
				avatarAnimation?.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f))
			}

			avatarAnimation?.duration = 180

			avatarAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (avatarAnimation == null || avatarProgressView == null) {
						return
					}

					if (!show) {
						avatarProgressView?.visibility = View.INVISIBLE
					}

					avatarAnimation = null
				}

				override fun onAnimationCancel(animation: Animator) {
					avatarAnimation = null
				}
			})

			avatarAnimation?.start()
		}
		else {
			if (show) {
				avatarProgressView?.alpha = 1.0f
				avatarProgressView?.visibility = View.VISIBLE
			}
			else {
				avatarProgressView?.alpha = 0.0f
				avatarProgressView?.visibility = View.INVISIBLE
			}
		}
	}

	override fun onActivityResultFragment(requestCode: Int, resultCode: Int, data: Intent?) {
		imageUpdater?.onActivityResult(requestCode, resultCode, data)
	}

	override fun saveSelfArgs(args: Bundle) {
		if (imageUpdater != null && imageUpdater?.currentPicturePath != null) {
			args.putString("path", imageUpdater!!.currentPicturePath)
		}
	}

	override fun restoreSelfArgs(args: Bundle) {
		imageUpdater?.currentPicturePath = args.getString("path")
	}

	private fun sendLogs(last: Boolean) {
		val parentActivity = parentActivity ?: return

		val progressDialog = AlertDialog(parentActivity, 3)
		progressDialog.setCanCancel(false)
		progressDialog.show()

		Utilities.globalQueue.postRunnable {
			try {
				val sdCard = ApplicationLoader.applicationContext.getExternalFilesDir(null)
				val dir = File(sdCard!!.absolutePath + "/logs")
				val zipFile = File(dir, "logs.zip")

				if (zipFile.exists()) {
					zipFile.delete()
				}

				val files = dir.listFiles() ?: arrayOf()
				val finished = BooleanArray(1)
				val currentDate = System.currentTimeMillis()
				var origin: BufferedInputStream? = null
				var out: ZipOutputStream? = null

				try {
					val dest = FileOutputStream(zipFile)
					out = ZipOutputStream(BufferedOutputStream(dest))

					val data = ByteArray(1024 * 64)

					for (i in files.indices) {
						if (last && (currentDate - files[i].lastModified()) > 24 * 60 * 60 * 1000) {
							continue
						}

						val fi = FileInputStream(files[i])

						origin = BufferedInputStream(fi, data.size)

						val entry = ZipEntry(files[i].name)

						out.putNextEntry(entry)

						var count: Int

						while ((origin.read(data, 0, data.size).also { count = it }) != -1) {
							out.write(data, 0, count)
						}

						origin.close()

						origin = null
					}

					finished[0] = true
				}
				catch (e: Exception) {
					e.printStackTrace()
				}
				finally {
					origin?.close()
					out?.close()
				}

				AndroidUtilities.runOnUIThread {
					try {
						progressDialog.dismiss()
					}
					catch (ignore: Exception) {
					}

					if (finished[0]) {
						val uri = if (Build.VERSION.SDK_INT >= 24) {
							FileProvider.getUriForFile(parentActivity, ApplicationLoader.applicationId + ".provider", zipFile)
						}
						else {
							Uri.fromFile(zipFile)
						}

						val i = Intent(Intent.ACTION_SEND)

						if (Build.VERSION.SDK_INT >= 24) {
							i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
						}

						i.type = "message/rfc822"
						i.putExtra(Intent.EXTRA_EMAIL, "")
						i.putExtra(Intent.EXTRA_SUBJECT, "Logs from " + LocaleController.getInstance().formatterStats.format(System.currentTimeMillis()))
						i.putExtra(Intent.EXTRA_STREAM, uri)

						try {
							parentActivity.startActivityForResult(Intent.createChooser(i, "Select email application."), 500)
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
					else {
						Toast.makeText(parentActivity, R.string.ErrorOccurred, Toast.LENGTH_SHORT).show()
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun openUrl(url: String) {
		if (url.startsWith("@")) {
			messagesController.openByUserName(url.substring(1), this@ProfileActivity, 0)
		}
		else if (url.startsWith("#")) {
			val fragment = DialogsActivity(null)
			fragment.setSearchString(url)
			presentFragment(fragment)
		}
		else if (url.startsWith("/")) {
			if (parentLayout!!.fragmentsStack.size > 1) {
				val previousFragment = parentLayout!!.fragmentsStack[parentLayout!!.fragmentsStack.size - 2]

				if (previousFragment is ChatActivity) {
					finishFragment()
					previousFragment.chatActivityEnterView?.setCommand(null, url, longPress = false, username = false)
				}
			}
		}
		else {
			Browser.openUrl(parentActivity, url)
		}
	}

//	private fun dimBehindView(view: View, enable: Boolean) {
//		scrimView = view
//		dimBehindView(enable)
//	}

	private fun dimBehindView(view: View, value: Float) {
		scrimView = view
		dimBehindView(value)
	}

	private fun dimBehindView(enable: Boolean) {
		dimBehindView(if (enable) 0.2f else 0f)
	}

	private fun dimBehindView(value: Float) {
		val enable = value > 0
		fragmentView?.invalidate()

		scrimAnimatorSet?.cancel()

		scrimAnimatorSet = AnimatorSet()

		val animators = ArrayList<Animator>()
		var scrimPaintAlphaAnimator: ValueAnimator

		if (enable) {
			animators.add(ValueAnimator.ofFloat(0f, value).also { scrimPaintAlphaAnimator = it })
		}
		else {
			animators.add(ValueAnimator.ofFloat(scrimPaint.alpha / 255f, 0f).also { scrimPaintAlphaAnimator = it })
		}

		scrimPaintAlphaAnimator.addUpdateListener {
			scrimPaint.alpha = (255 * it.animatedValue as Float).toInt()
		}

		scrimAnimatorSet?.playTogether(animators)
		scrimAnimatorSet?.duration = if (enable) 150 else 220.toLong()

		if (!enable) {
			scrimAnimatorSet?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					scrimView = null
					fragmentView?.invalidate()
				}
			})
		}

		scrimAnimatorSet?.start()
	}

	fun updateListAnimated(updateOnlineCount: Boolean) {
		if (listAdapter == null) {
			if (updateOnlineCount) {
				updateOnlineCount(false)
			}

			updateRowsIds()

			return
		}

		val diffCallback = DiffCallback()
		diffCallback.oldRowCount = rowCount
		diffCallback.fillPositions(diffCallback.oldPositionToItem)
		diffCallback.oldChatParticipant.clear()
		diffCallback.oldChatParticipantSorted.clear()
		diffCallback.oldChatParticipant.addAll(visibleChatParticipants)
		diffCallback.oldChatParticipantSorted.addAll(visibleSortedUsers)
		diffCallback.oldMembersStartRow = membersStartRow
		diffCallback.oldMembersEndRow = membersEndRow

		if (updateOnlineCount) {
			updateOnlineCount(false)
		}

		saveScrollPosition()

		updateRowsIds()

		diffCallback.fillPositions(diffCallback.newPositionToItem)

		listAdapter?.notifyDataSetChanged()

		if (savedScrollPosition >= 0) {
			layoutManager?.scrollToPositionWithOffset(savedScrollPosition, savedScrollOffset - listView!!.paddingTop)
		}

		AndroidUtilities.updateVisibleRows(listView)
	}

	private fun saveScrollPosition() {
		val listView = listView ?: return

		if (layoutManager != null && listView.childCount > 0) {
			var view: View? = null
			var position = -1
			var top = Int.MAX_VALUE

			for (i in 0 until listView.childCount) {
				val childPosition = listView.getChildAdapterPosition(listView.getChildAt(i))
				val child = listView.getChildAt(i)

				if (childPosition != RecyclerView.NO_POSITION && child.top < top) {
					view = child
					position = childPosition
					top = child.top
				}
			}

			if (view != null) {
				savedScrollPosition = position
				savedScrollOffset = view.top

				if (savedScrollPosition == 0 && !allowPullingDown && savedScrollOffset > AndroidUtilities.dp(88f)) {
					savedScrollOffset = AndroidUtilities.dp(88f)
				}

				layoutManager?.scrollToPositionWithOffset(position, view.top - listView.paddingTop)
			}
		}
	}

	override fun scrollToSharedMedia() {
		layoutManager?.scrollToPositionWithOffset(sharedMediaRow, -listView!!.paddingTop)
	}

	private fun onTextDetailCellImageClicked(view: View) {
		val parent = view.parent as? View

		if (parent?.tag as? Int == usernameRow) {
			openQrFragment()
		}
	}

	private fun openQrFragment() {
		val args = Bundle()
		args.putLong(QrFragment.CHAT_ID, chatId)
		args.putLong(QrFragment.USER_ID, userId)

		inviteLink().takeIf { link ->
			link.isNotEmpty()
		}?.let { link ->
			args.putString(QrFragment.LINK, link)
		}

		if (userId != 0L) {
			args.putBoolean(QrFragment.IS_PUBLIC, userInfo?.is_public == true)
		}
		else if (chatId != 0L) {
			val isPrivate = currentChat?.let { currentChat ->
				if (currentChat.megagroup) {
					currentChat.username.isNullOrEmpty()
				}
				else {
					currentChat.flags and TLRPC.CHAT_FLAG_IS_PUBLIC == 0
				}
			} ?: false

			args.putBoolean(QrFragment.IS_PUBLIC, !isPrivate)
		}

		presentFragment(QrFragment(args))
	}

	override fun onBecomeFullyVisible() {
		super.onBecomeFullyVisible()
		writeButton?.background = createWriteButtonBackground()
	}

	private fun createWriteButtonBackground(): Drawable {
		return ResourcesCompat.getDrawable(context!!.resources, R.drawable.camera_selector, null)!!
	}

	private val isQrNeedVisible: Boolean
		get() {
			if (!TextUtils.isEmpty(userConfig.getCurrentUser()!!.username)) {
				return true
			}

			val privacyRules = ContactsController.getInstance(currentAccount).getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_PHONE) ?: return false
			var type = 2

			for (i in privacyRules.indices) {
				val rule = privacyRules[i]

				if (rule is TLRPC.TL_privacyValueAllowAll) {
					type = 0
					break
				}
				else if (rule is TLRPC.TL_privacyValueDisallowAll) {
					type = 2
					break
				}
				else if (rule is TLRPC.TL_privacyValueAllowContacts) {
					type = 1
					break
				}
			}

			return type == 0 || type == 1
		}

	override fun isLightStatusBar(): Boolean {
		val context = context ?: return false

		if (isPulledDown) {
			return false
		}

		val color = if (actionBar!!.isActionModeShowed) {
			ResourcesCompat.getColor(context.resources, R.color.white, null)
		}
		else if (mediaHeaderVisible) {
			ResourcesCompat.getColor(context.resources, R.color.background, null)
		}
		else {
			ResourcesCompat.getColor(context.resources, R.color.background, null)
		}

		return ColorUtils.calculateLuminance(color) > 0.7f
	}

	private inner class TopView(context: Context) : View(context) {
		private val paint = Paint()
		private var currentColor = 0

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(widthMeasureSpec) + AndroidUtilities.dp(3f))
		}

		override fun setBackgroundColor(color: Int) {
			if (color != currentColor) {
				currentColor = color
				paint.color = color
				invalidate()
			}
		}

		override fun onDraw(canvas: Canvas) {
			val height = ActionBar.getCurrentActionBarHeight() + if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0
			val v = extraHeight + height + searchTransitionOffset
			val y1 = (v * (1.0f - mediaHeaderAnimationProgress)).toInt()

			if (y1 != 0) {
				if (previousTransitionFragment != null) {
					AndroidUtilities.rectTmp2[0, 0, measuredWidth] = y1
					previousTransitionFragment?.contentView?.drawBlurRect(canvas, y, AndroidUtilities.rectTmp2, previousTransitionFragment!!.actionBar!!.blurScrimPaint, true)
				}

				paint.color = currentColor

				canvas.drawRect(0f, 0f, measuredWidth.toFloat(), y1.toFloat(), paint)
			}

			if (y1.toFloat() != v) {
				val color = ResourcesCompat.getColor(context.resources, R.color.background, null)
				paint.color = color
				AndroidUtilities.rectTmp2[0, y1, measuredWidth] = v.toInt()
				contentView?.drawBlurRect(canvas, y, AndroidUtilities.rectTmp2, paint, true)
			}

			parentLayout?.drawHeaderShadow(canvas, (headerShadowAlpha * 255).toInt(), v.toInt())
		}
	}

	private inner class OverlaysView(context: Context) : View(context), ProfileGalleryView.Callback {
		private val statusBarHeight = if (actionBar!!.occupyStatusBar && !isInBubbleMode) AndroidUtilities.statusBarHeight else 0
		private val topOverlayRect = Rect()
		private val bottomOverlayRect = Rect()
		private val rect = RectF()
		private val topOverlayGradient: GradientDrawable
		private val bottomOverlayGradient: GradientDrawable
		val animator: ValueAnimator?
		private val animatorValues = floatArrayOf(0f, 1f)
		private val backgroundPaint: Paint
		private val barPaint: Paint
		private val selectedBarPaint: Paint
		private val pressedOverlayGradient = arrayOfNulls<GradientDrawable>(2)
		private val pressedOverlayVisible = BooleanArray(2)
		private val pressedOverlayAlpha = FloatArray(2)

		var isOverlaysVisible = false
			private set

		private var currentAnimationValue = 0f

		// private var alpha = 0f
		private var alphas: FloatArray? = null
		private var lastTime: Long = 0
		private var previousSelectedProgress = 0f
		private var previousSelectedPotision = -1
		private var currentProgress = 0f
		private var selectedPosition = 0
		private var currentLoadingAnimationProgress = 0f
		private var currentLoadingAnimationDirection = 1

		init {
			visibility = GONE

			barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
			barPaint.color = 0x55ffffff

			selectedBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
			selectedBarPaint.color = -0x1

			topOverlayGradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x42000000, 0))
			topOverlayGradient.shape = GradientDrawable.RECTANGLE

			bottomOverlayGradient = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0x42000000, 0))
			bottomOverlayGradient.shape = GradientDrawable.RECTANGLE

			for (i in 0..1) {
				val orientation = if (i == 0) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.RIGHT_LEFT
				pressedOverlayGradient[i] = GradientDrawable(orientation, intArrayOf(0x32000000, 0))
				pressedOverlayGradient[i]?.shape = GradientDrawable.RECTANGLE
			}

			backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
			backgroundPaint.color = Color.BLACK
			backgroundPaint.alpha = 66

			animator = ValueAnimator.ofFloat(0f, 1f)
			animator.duration = 250
			animator.interpolator = CubicBezierInterpolator.EASE_BOTH

			animator.addUpdateListener(AnimatorUpdateListener { animator ->
				val value = AndroidUtilities.lerp(animatorValues, animator.animatedFraction.also { currentAnimationValue = it })
				setAlphaValue(value, true)
			})

			animator.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (!isOverlaysVisible) {
						visibility = GONE
					}
				}

				override fun onAnimationStart(animation: Animator) {
					visibility = VISIBLE
				}
			})
		}

		fun saveCurrentPageProgress() {
			previousSelectedProgress = currentProgress
			previousSelectedPotision = selectedPosition
			currentLoadingAnimationProgress = 0.0f
			currentLoadingAnimationDirection = 1
		}

		fun setAlphaValue(value: Float, self: Boolean) {
			val alpha = (255 * value).toInt()

			topOverlayGradient.alpha = alpha
			bottomOverlayGradient.alpha = alpha
			backgroundPaint.alpha = (66 * value).toInt()
			barPaint.alpha = (0x55 * value).toInt()
			selectedBarPaint.alpha = alpha

			this.alpha = value

			if (!self) {
				currentAnimationValue = value
			}

			invalidate()
		}

		fun setOverlaysVisible() {
			isOverlaysVisible = true
			visibility = VISIBLE
		}

		fun setOverlaysVisible(overlaysVisible: Boolean, durationFactor: Float) {
			if (overlaysVisible != isOverlaysVisible) {
				isOverlaysVisible = overlaysVisible

				animator?.cancel()

				val value = AndroidUtilities.lerp(animatorValues, currentAnimationValue)

				if (overlaysVisible) {
					animator?.duration = ((1f - value) * 250f / durationFactor).toLong()
				}
				else {
					animator?.duration = (value * 250f / durationFactor).toLong()
				}

				animatorValues[0] = value
				animatorValues[1] = if (overlaysVisible) 1f else 0f

				animator?.start()
			}
		}

		override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
			val actionBarHeight = statusBarHeight + ActionBar.getCurrentActionBarHeight()
			val k = 0.5f

			topOverlayRect[0, 0, w] = (actionBarHeight * k).toInt()
			bottomOverlayRect[0, (h - AndroidUtilities.dp(72f) * k).toInt(), w] = h
			topOverlayGradient.setBounds(0, topOverlayRect.bottom, w, actionBarHeight + AndroidUtilities.dp(16f))
			bottomOverlayGradient.setBounds(0, h - AndroidUtilities.dp(72f) - AndroidUtilities.dp(24f), w, bottomOverlayRect.top)
			pressedOverlayGradient[0]!!.setBounds(0, 0, w / 5, h)
			pressedOverlayGradient[1]!!.setBounds(w - w / 5, 0, w, h)
		}

		override fun onDraw(canvas: Canvas) {
			for (i in 0..1) {
				if (pressedOverlayAlpha[i] > 0f) {
					pressedOverlayGradient[i]?.alpha = (pressedOverlayAlpha[i] * 255).toInt()
					pressedOverlayGradient[i]?.draw(canvas)
				}
			}

			topOverlayGradient.draw(canvas)
			bottomOverlayGradient.draw(canvas)

			canvas.drawRect(topOverlayRect, backgroundPaint)
			canvas.drawRect(bottomOverlayRect, backgroundPaint)

			val count = avatarsViewPager!!.realCount

			selectedPosition = avatarsViewPager!!.realPosition

			if (alphas == null || alphas?.size != count) {
				alphas = FloatArray(count).also {
					Arrays.fill(it, 0.0f)
				}
			}

			var invalidate = false
			val newTime = SystemClock.elapsedRealtime()
			var dt = newTime - lastTime

			if (dt < 0 || dt > 20) {
				dt = 17
			}

			lastTime = newTime

			if (count in 2..20) {
				if (overlayCountVisible == 0) {
					alpha = 0.0f
					overlayCountVisible = 3
				}
				else if (overlayCountVisible == 1) {
					alpha = 0.0f
					overlayCountVisible = 2
				}

				if (overlayCountVisible == 2) {
					barPaint.alpha = (0x55 * alpha).toInt()
					selectedBarPaint.alpha = (0xff * alpha).toInt()
				}

				val width = (measuredWidth - AndroidUtilities.dp((5 * 2).toFloat()) - AndroidUtilities.dp((2 * (count - 1)).toFloat())) / count
				val y = AndroidUtilities.dp(4f) + if (!isInBubbleMode) AndroidUtilities.statusBarHeight else 0

				for (a in 0 until count) {
					val x = AndroidUtilities.dp((5 + a * 2).toFloat()) + width * a
					var progress: Float
					var baseAlpha = 0x55

					if (a == previousSelectedPotision && abs(previousSelectedProgress - 1.0f) > 0.0001f) {
						progress = previousSelectedProgress
						canvas.save()
						canvas.clipRect(x + width * progress, y.toFloat(), (x + width).toFloat(), (y + AndroidUtilities.dp(2f)).toFloat())
						rect[x.toFloat(), y.toFloat(), (x + width).toFloat()] = (y + AndroidUtilities.dp(2f)).toFloat()
						barPaint.alpha = (0x55 * alpha).toInt()
						canvas.drawRoundRect(rect, AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat(), barPaint)
						baseAlpha = 0x50
						canvas.restore()
						invalidate = true
					}
					else if (a == selectedPosition) {
						if (avatarsViewPager!!.isCurrentItemVideo) {
							currentProgress = avatarsViewPager!!.currentItemProgress

							progress = currentProgress

							if (progress <= 0 && avatarsViewPager!!.isLoadingCurrentVideo || currentLoadingAnimationProgress > 0.0f) {
								currentLoadingAnimationProgress += currentLoadingAnimationDirection * dt / 500.0f

								if (currentLoadingAnimationProgress > 1.0f) {
									currentLoadingAnimationProgress = 1.0f
									currentLoadingAnimationDirection *= -1
								}
								else if (currentLoadingAnimationProgress <= 0) {
									currentLoadingAnimationProgress = 0.0f
									currentLoadingAnimationDirection *= -1
								}
							}

							rect[x.toFloat(), y.toFloat(), (x + width).toFloat()] = (y + AndroidUtilities.dp(2f)).toFloat()
							barPaint.alpha = ((0x55 + 0x30 * currentLoadingAnimationProgress) * alpha).toInt()

							canvas.drawRoundRect(rect, AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat(), barPaint)

							invalidate = true

							baseAlpha = 0x50
						}
						else {
							currentProgress = 1.0f
							progress = currentProgress
						}
					}
					else {
						progress = 1.0f
					}

					rect[x.toFloat(), y.toFloat(), x + width * progress] = (y + AndroidUtilities.dp(2f)).toFloat()

					if (a != selectedPosition) {
						if (overlayCountVisible == 3) {
							barPaint.alpha = (AndroidUtilities.lerp(baseAlpha, 0xff, CubicBezierInterpolator.EASE_BOTH.getInterpolation(alphas!![a])) * alpha).toInt()
						}
					}
					else {
						alphas!![a] = 0.75f
					}

					canvas.drawRoundRect(rect, AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat(), if (a == selectedPosition) selectedBarPaint else barPaint)
				}

				if (overlayCountVisible == 2) {
					if (alpha < 1.0f) {
						alpha += dt / 180.0f
						alpha = alpha.coerceAtMost(1.0f)
						invalidate = true
					}
					else {
						overlayCountVisible = 3
					}
				}
				else if (overlayCountVisible == 3) {
					for (i in alphas!!.indices) {
						if (i != selectedPosition && alphas!![i] > 0.0f) {
							alphas!![i] -= dt / 500.0f

							if (alphas!![i] <= 0.0f) {
								alphas!![i] = 0.0f

								if (i == previousSelectedPotision) {
									previousSelectedPotision = -1
								}
							}

							invalidate = true
						}
						else if (i == previousSelectedPotision) {
							previousSelectedPotision = -1
						}
					}
				}
			}

			for (i in 0..1) {
				if (pressedOverlayVisible[i]) {
					if (pressedOverlayAlpha[i] < 1f) {
						pressedOverlayAlpha[i] += dt / 180.0f

						if (pressedOverlayAlpha[i] > 1f) {
							pressedOverlayAlpha[i] = 1f
						}

						invalidate = true
					}
				}
				else {
					if (pressedOverlayAlpha[i] > 0f) {
						pressedOverlayAlpha[i] -= dt / 180.0f

						if (pressedOverlayAlpha[i] < 0f) {
							pressedOverlayAlpha[i] = 0f
						}

						invalidate = true
					}
				}
			}

			if (invalidate) {
				postInvalidateOnAnimation()
			}
		}

		override fun onDown(left: Boolean) {
			pressedOverlayVisible[if (left) 0 else 1] = true
			postInvalidateOnAnimation()
		}

		override fun onRelease() {
			Arrays.fill(pressedOverlayVisible, false)
			postInvalidateOnAnimation()
		}

		override fun onPhotosLoaded() {
			updateProfileData(false)
		}

		override fun onVideoSet() {
			invalidate()
		}
	}

	private open inner class NestedFrameLayout(context: Context) : SizeNotifierFrameLayout(context), NestedScrollingParent3 {
		private val nestedScrollingParentHelper: NestedScrollingParentHelper = NestedScrollingParentHelper(this)

		override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int, consumed: IntArray) {
			if (target === listView && sharedMediaLayoutAttached) {
				val innerListView = sharedMediaLayout!!.currentListView
				val top = sharedMediaLayout!!.top

				if (top == 0) {
					consumed[1] = dyUnconsumed
					innerListView?.scrollBy(0, dyUnconsumed)
				}
			}
		}

		override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int) {
			// unused
		}

		override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
			if (target === listView && sharedMediaRow != -1 && sharedMediaLayoutAttached) {
				val searchVisible = actionBar!!.isSearchFieldVisible
				val t = sharedMediaLayout!!.top

				if (dy < 0) {
					var scrolledInner = false

					if (t <= 0) {
						val innerListView = sharedMediaLayout?.currentListView
						val linearLayoutManager = innerListView?.layoutManager as? LinearLayoutManager
						val pos = linearLayoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION

						if (pos != RecyclerView.NO_POSITION) {
							val holder = innerListView?.findViewHolderForAdapterPosition(pos)
							val top = holder?.itemView?.top ?: -1
							val paddingTop = innerListView?.paddingTop ?: 0

							if (top != paddingTop || pos != 0) {
								consumed[1] = if (pos != 0) dy else max(dy, top - paddingTop)
								innerListView?.scrollBy(0, dy)
								scrolledInner = true
							}
						}
					}

					if (searchVisible) {
						if (!scrolledInner && t < 0) {
							consumed[1] = dy - max(t, dy)
						}
						else {
							consumed[1] = dy
						}
					}
				}
				else {
					if (searchVisible) {
						val innerListView = sharedMediaLayout!!.currentListView

						consumed[1] = dy

						if (t > 0) {
							consumed[1] -= dy
						}

						if (consumed[1] > 0) {
							innerListView?.scrollBy(0, consumed[1])
						}
					}
				}
			}
		}

		override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
			return sharedMediaRow != -1 && axes == ViewCompat.SCROLL_AXIS_VERTICAL
		}

		override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
			nestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes)
		}

		override fun onStopNestedScroll(target: View, type: Int) {
			nestedScrollingParentHelper.onStopNestedScroll(target)
		}

		override fun onStopNestedScroll(child: View) {
			// unused
		}

		override fun drawList(blurCanvas: Canvas, top: Boolean) {
			super.drawList(blurCanvas, top)
			blurCanvas.save()
			blurCanvas.translate(0f, listView!!.y)
			sharedMediaLayout?.drawListForBlur(blurCanvas)
			blurCanvas.restore()
		}
	}

	private inner class PagerIndicatorView(context: Context) : View(context) {
		private val indicatorRect = RectF()
		private val textPaint: TextPaint
		private val backgroundPaint: Paint
		private val animator: ValueAnimator
		private val animatorValues = floatArrayOf(0f, 1f)
		private val adapter = avatarsViewPager!!.adapter

		var isIndicatorVisible = false
			private set

		init {
			visibility = GONE

			textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
			textPaint.color = Color.WHITE
			textPaint.typeface = Theme.TYPEFACE_DEFAULT
			textPaint.textAlign = Paint.Align.CENTER
			textPaint.textSize = AndroidUtilities.dpf2(15f)

			backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
			backgroundPaint.color = 0x26000000

			animator = ValueAnimator.ofFloat(0f, 1f)
			animator.interpolator = CubicBezierInterpolator.EASE_BOTH

			animator.addUpdateListener {
				val value = AndroidUtilities.lerp(animatorValues, it.animatedFraction)

				if (searchItem != null && !isPulledDown) {
					searchItem?.scaleX = 1f - value
					searchItem?.scaleY = 1f - value
					searchItem?.alpha = 1f - value
				}

				if (editItemVisible) {
					editItem?.scaleX = 1f - value
					editItem?.scaleY = 1f - value
					editItem?.alpha = 1f - value
				}

				if (callItemVisible) {
					callItem?.scaleX = 1f - value
					callItem?.scaleY = 1f - value
					callItem?.alpha = 1f - value
				}

				if (videoCallItemVisible) {
					videoCallItem?.scaleX = 1f - value
					videoCallItem?.scaleY = 1f - value
					videoCallItem?.alpha = 1f - value
				}

				scaleX = value
				scaleY = value
				alpha = value
			}

			val expanded = expandPhoto

			animator.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (isIndicatorVisible) {
						searchItem?.isClickable = false

						if (editItemVisible) {
							editItem?.visibility = GONE
						}

						if (callItemVisible) {
							callItem?.visibility = GONE
						}

						if (videoCallItemVisible) {
							videoCallItem?.visibility = GONE
						}
					}
					else {
						visibility = GONE
					}
				}

				override fun onAnimationStart(animation: Animator) {
					if (searchItem != null && !expanded) {
						searchItem?.isClickable = true
					}

					if (editItemVisible) {
						editItem?.visibility = VISIBLE
					}

					if (callItemVisible) {
						callItem?.visibility = VISIBLE
					}

					if (videoCallItemVisible) {
						videoCallItem?.visibility = VISIBLE
					}

					visibility = VISIBLE
				}
			})

			avatarsViewPager?.addOnPageChangeListener(object : OnPageChangeListener {
				private var prevPage = 0

				override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
					// unused
				}

				override fun onPageSelected(position: Int) {
					val realPosition = avatarsViewPager!!.getRealPosition(position)
					invalidateIndicatorRect(prevPage != realPosition)
					prevPage = realPosition
					updateAvatarItems()
				}

				override fun onPageScrollStateChanged(state: Int) {
					// unused
				}
			})

			adapter?.registerDataSetObserver(object : DataSetObserver() {
				override fun onChanged() {
					val count = avatarsViewPager!!.realCount

					if (overlayCountVisible == 0 && count > 1 && count <= 20 && overlaysView!!.isOverlaysVisible) {
						overlayCountVisible = 1
					}

					invalidateIndicatorRect(false)
					refreshVisibility(1f)
					updateAvatarItems()
				}
			})
		}

		private fun updateAvatarItemsInternal() {
			if (otherItem == null || avatarsViewPager == null) {
				return
			}

			if (isPulledDown) {
				val position = avatarsViewPager?.realPosition ?: return

				if (position == 0) {
					otherItem?.hideSubItem(set_as_main)
					otherItem?.showSubItem(add_photo)
				}
				else {
					otherItem?.showSubItem(set_as_main)
					otherItem?.hideSubItem(add_photo)
				}
			}
		}

		private fun updateAvatarItems() {
			if (imageUpdater == null) {
				return
			}

			if (otherItem!!.isSubMenuShowing) {
				AndroidUtilities.runOnUIThread({ updateAvatarItemsInternal() }, 500)
			}
			else {
				updateAvatarItemsInternal()
			}
		}

		val isIndicatorFullyVisible: Boolean
			get() = isIndicatorVisible && !animator.isRunning

		fun setIndicatorVisible(indicatorVisible: Boolean, durationFactor: Float) {
			if (indicatorVisible != isIndicatorVisible) {
				isIndicatorVisible = indicatorVisible
				animator.cancel()

				val value = AndroidUtilities.lerp(animatorValues, animator.animatedFraction)

				if (durationFactor <= 0f) {
					animator.duration = 0
				}
				else if (indicatorVisible) {
					animator.duration = ((1f - value) * 250f / durationFactor).toLong()
				}
				else {
					animator.duration = (value * 250f / durationFactor).toLong()
				}

				animatorValues[0] = value
				animatorValues[1] = if (indicatorVisible) 1f else 0f

				animator.start()
			}
		}

		fun refreshVisibility(durationFactor: Float) {
			setIndicatorVisible(isPulledDown && avatarsViewPager!!.realCount > 20, durationFactor)
		}

		override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
			invalidateIndicatorRect(false)
		}

		private fun invalidateIndicatorRect(pageChanged: Boolean) {
			if (pageChanged) {
				overlaysView?.saveCurrentPageProgress()
			}

			overlaysView?.invalidate()

			val textWidth = textPaint.measureText(currentTitle)

			indicatorRect.right = (measuredWidth - AndroidUtilities.dp(54f) - if (qrItem != null) AndroidUtilities.dp(48f) else 0).toFloat()
			indicatorRect.left = indicatorRect.right - (textWidth + AndroidUtilities.dpf2(16f))
			indicatorRect.top = ((if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0) + AndroidUtilities.dp(15f)).toFloat()
			indicatorRect.bottom = indicatorRect.top + AndroidUtilities.dp(26f)

			pivotX = indicatorRect.centerX()
			pivotY = indicatorRect.centerY()

			invalidate()
		}

		override fun onDraw(canvas: Canvas) {
			val radius = AndroidUtilities.dpf2(12f)
			canvas.drawRoundRect(indicatorRect, radius, radius, backgroundPaint)
			canvas.drawText(currentTitle, indicatorRect.centerX(), indicatorRect.top + AndroidUtilities.dpf2(18.5f), textPaint)
		}

		private val currentTitle: String
			get() = adapter?.getPageTitle(avatarsViewPager!!.currentItem)?.toString() ?: ""

		val secondaryMenuItem: ActionBarMenuItem?
			get() = if (callItemVisible) {
				callItem
			}
			else if (editItemVisible) {
				editItem
			}
			else if (searchItem != null) {
				searchItem
			}
			else {
				null
			}
	}

	private inner class ListAdapter(private val mContext: Context) : RecyclerListView.SelectionAdapter() {
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				VIEW_TYPE_HEADER -> {
					view = HeaderCell(mContext, 16)
				}

				VIEW_TYPE_USER_ACCOUNT -> {
					view = ProfileAccountCell(mContext)
				}

				VIEW_TYPE_ADD_ACCOUNT -> {
					view = ProfileSectionCell(mContext, iconSize = 14, cellHeight = 48f).also {
						it.setIconColor(ResourcesCompat.getColor(mContext.resources, R.color.brand, null))
					}
				}

				VIEW_TYPE_SET_PROFILE_PHOTO  -> {
					view = AddProfilePhotoLayoutBinding.inflate(LayoutInflater.from(mContext)).root
				}

				VIEW_TYPE_TEXT_DETAIL -> {
					val textDetailCell = TextDetailCell(mContext)

					textDetailCell.setContentDescriptionValueFirst(true)

					textDetailCell.setImageClickListener {
						onTextDetailCellImageClicked(it)
					}

					view = textDetailCell
				}

				VIEW_TYPE_ABOUT_LINK -> {
					aboutLinkCell = AboutLinkCell(mContext)

					aboutLinkCell?.listener = object : LinkClickListener {
						override fun onClick(route: String) {
							openUrl(route)
						}

						override fun onLongClick(route: String) {
							val context = context ?: return

							runCatching {
								aboutLinkCell?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
							}

							val builder = BottomSheet.Builder(parentActivity)
							builder.setTitle(route)

							builder.setItems(arrayOf<CharSequence>(context.getString(R.string.Open), context.getString(R.string.Copy))) { _, which ->
								if (which == 0) {
									openUrl(route)
								}
								else if (which == 1) {
									AndroidUtilities.addToClipboard(route)

									if (AndroidUtilities.shouldShowClipboardToast()) {
										if (route.startsWith("@")) {
											BulletinFactory.of(this@ProfileActivity).createSimpleBulletin(R.raw.copy, context.getString(R.string.UsernameCopied)).show()
										}
										else if (route.startsWith("#") || route.startsWith("$")) {
											BulletinFactory.of(this@ProfileActivity).createSimpleBulletin(R.raw.copy, context.getString(R.string.HashtagCopied)).show()
										}
										else {
											BulletinFactory.of(this@ProfileActivity).createSimpleBulletin(R.raw.copy, context.getString(R.string.LinkCopied)).show()
										}
									}
								}
							}

							builder.show()
						}
					}

					view = aboutLinkCell!!
				}

				VIEW_TYPE_TEXT -> {
					// MARK: uncomment following to make cells' text bold
//					var bold = false
//
//					if (chatInfo != null) {
//						if (ChatObject.isChannel(currentChat) && !currentChat!!.megagroup) {
//							bold = true
//						}
//					}

					view = TextCell(mContext, fullDivider = true, large = true, boldText = false)
				}

				VIEW_TYPE_DIVIDER -> {
					view = DividerCell(mContext)
					view.setPadding(0, AndroidUtilities.dp(4f), 0, 0)
				}

				VIEW_TYPE_NOTIFICATIONS_CHECK -> {
					view = NotificationsCheckCell(mContext, height = 52, reorder = false).apply {
						setDrawLine(true)
					}
				}

				VIEW_TYPE_SHADOW -> {
					view = ShadowSectionCell(mContext)
				}

				VIEW_TYPE_USER -> {
					view = UserCell(mContext, if (addMemberRow == -1) 9 else 6, 0, true)
				}

				VIEW_TYPE_EMPTY -> {
					view = object : View(mContext) {
						override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
							super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32f), MeasureSpec.EXACTLY))
						}
					}
				}

				VIEW_TYPE_SMALL_EMPTY -> {
					view = object : View(mContext) {
						override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
							super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(12f), MeasureSpec.EXACTLY))
						}
					}
					view.setBackgroundResource(R.color.light_background)
				}

				VIEW_TYPE_BOTTOM_PADDING -> {
					view = object : View(mContext) {
						private var lastPaddingHeight = 0
						private var lastListViewHeight = 0

						override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
							if (lastListViewHeight != listView!!.measuredHeight) {
								lastPaddingHeight = 0
							}

							lastListViewHeight = listView!!.measuredHeight

							val n = listView!!.childCount

							if (n == listAdapter!!.itemCount) {
								var totalHeight = 0
								var i = 0

								while (i < n) {
									@Suppress("NAME_SHADOWING") val view = listView!!.getChildAt(i)
									val p = listView!!.getChildAdapterPosition(view)

									if (p >= 0 && p != bottomPaddingRow) {
										totalHeight += listView!!.getChildAt(i).measuredHeight
									}

									i++
								}

								var paddingHeight = fragmentView!!.measuredHeight - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight - totalHeight

								if (paddingHeight > AndroidUtilities.dp(88f)) {
									paddingHeight = 0
								}

								if (paddingHeight <= 0) {
									paddingHeight = 0
								}

								setMeasuredDimension(listView!!.measuredWidth, paddingHeight.also { lastPaddingHeight = it })
							}
							else {
								setMeasuredDimension(listView!!.measuredWidth, lastPaddingHeight)
							}
						}
					}

					view.setBackground(ColorDrawable(Color.TRANSPARENT))
				}

				VIEW_TYPE_SHARED_MEDIA -> {
					if (sharedMediaLayout?.parent != null) {
						(sharedMediaLayout?.parent as? ViewGroup)?.removeView(sharedMediaLayout)
					}

					view = sharedMediaLayout!!
				}

				VIEW_TYPE_ADDTOGROUP_INFO -> {
					view = TextInfoPrivacyCell(mContext)
				}

				VIEW_TYPE_VERSION -> {
					val cell = TextInfoPrivacyCell(mContext, 10)
					cell.textView.gravity = Gravity.CENTER_HORIZONTAL
					cell.textView.setTextColor(ResourcesCompat.getColor(mContext.resources, R.color.dark_gray, null))
					cell.textView.movementMethod = null

					try {
						val pInfo = ApplicationLoader.applicationContext.packageManager.getPackageInfo(ApplicationLoader.applicationContext.packageName, 0)
						val code = pInfo.versionCode / 10

						val abi = when (pInfo.versionCode % 10) {
							1, 2 -> "store bundled " + Build.SUPPORTED_ABIS.joinToString(separator = " ")
							9 -> "universal " + Build.SUPPORTED_ABIS.joinToString(separator = " ")
							else -> "universal " + Build.SUPPORTED_ABIS.joinToString(separator = " ")
						}

						cell.setText(LocaleController.formatString("ElloVersion", R.string.ElloVersion, String.format(Locale.US, "v%s (%d) %s", pInfo.versionName, code, abi)))
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					cell.textView.setPadding(0, AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f))

					view = cell
					view.setBackgroundResource(R.drawable.greydivider_bottom)
				}

				VIEW_TYPE_SUGGESTION -> {
					view = object : SettingsSuggestionCell(mContext) {
						override fun onYesClick(type: Int) {
							notificationCenter.removeObserver(this@ProfileActivity, NotificationCenter.newSuggestionsAvailable)
							messagesController.removeSuggestion(0, if (type == TYPE_PHONE) "VALIDATE_PHONE_NUMBER" else "VALIDATE_PASSWORD")
							notificationCenter.addObserver(this@ProfileActivity, NotificationCenter.newSuggestionsAvailable)
							updateListAnimated(false)
						}

						override fun onNoClick(type: Int) {
							if (type == TYPE_PHONE) {
								presentFragment(ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER))
							}
							else {
								presentFragment(TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_VERIFY, null))
							}
						}
					}
				}

				VIEW_TYPE_PREMIUM_TEXT_CELL -> {
					view = ProfilePremiumCell(mContext)
				}

				VIEW_TYPE_PROFILE_SECTION -> {
					view = ProfileSectionCell(mContext)
				}

				VIEW_TYPE_SUPPORT -> {
					view = ProfileSupportCell(mContext)
				}

				VIEW_TYPE_LOGOUT -> {
					view = ProfileLogoutCell(mContext)
				}

				VIEW_TYPE_SHARE_LINK -> {
					view = LayoutInflater.from(mContext).inflate(R.layout.profile_section_share, parent, false)
					view.tag = VIEW_TYPE_SHARE_LINK
				}

				VIEW_TYPE_PUBLIC_LINK -> {
					view = LayoutInflater.from(mContext).inflate(R.layout.group_public_link, parent, false)
					view.tag = VIEW_TYPE_PUBLIC_LINK
				}

				else -> {
					val cell = TextInfoPrivacyCell(mContext, 10)
					cell.textView.gravity = Gravity.CENTER_HORIZONTAL
					cell.textView.setTextColor(ResourcesCompat.getColor(mContext.resources, R.color.dark_gray, null))
					cell.textView.movementMethod = null

					try {
						val pInfo = ApplicationLoader.applicationContext.packageManager.getPackageInfo(ApplicationLoader.applicationContext.packageName, 0)
						val code = pInfo.versionCode / 10

						val abi = when (pInfo.versionCode % 10) {
							1, 2 -> "store bundled " + Build.SUPPORTED_ABIS.joinToString(separator = " ")
							9 -> "universal " + Build.SUPPORTED_ABIS.joinToString(separator = " ")
							else -> "universal " + Build.SUPPORTED_ABIS.joinToString(separator = " ")
						}

						cell.setText(LocaleController.formatString("ElloVersion", R.string.ElloVersion, String.format(Locale.US, "v%s (%d) %s", pInfo.versionName, code, abi)))
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					cell.textView.setPadding(0, AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f))

					view = cell
					view.setBackgroundResource(R.drawable.greydivider_bottom)
				}
			}

			if (viewType != VIEW_TYPE_SHARED_MEDIA) {
				view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
			}

			return RecyclerListView.Holder(view)
		}

		override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
			if (holder.itemView === sharedMediaLayout) {
				sharedMediaLayoutAttached = true
			}
		}

		override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
			if (holder.itemView === sharedMediaLayout) {
				sharedMediaLayoutAttached = false
			}
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val parentActivity = parentActivity ?: return

			when (holder.itemViewType) {
				VIEW_TYPE_HEADER -> {
					val headerCell = holder.itemView as HeaderCell

					when (position) {
						userAboutHeaderRow -> {
							if (messagesController.getUser(userId)?.is_public == true) {
								headerCell.setText(parentActivity.getString(R.string.about))
							}
						}

						membersHeaderRow -> {
							headerCell.setText(parentActivity.getString(R.string.ChannelMembers))
						}

						settingsSectionRow2 -> {
							headerCell.setText(parentActivity.getString(R.string.SETTINGS))
						}

						helpHeaderRow -> {
							headerCell.setText(parentActivity.getString(R.string.SettingsHelp))
						}

						debugHeaderRow -> {
							headerCell.setText(parentActivity.getString(R.string.SettingsDebug))
						}
					}
				}

				VIEW_TYPE_TEXT_DETAIL -> {
					val detailCell = holder.itemView as TextDetailCell

					if (position == usernameRow) {
						val drawable = ResourcesCompat.getDrawable(mContext.resources, R.drawable.msg_qr_mini, null)
						drawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(mContext.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)
						detailCell.setImage(drawable, mContext.getString(R.string.GetQRCode))
					}
					else {
						detailCell.setImage(null)
					}

					if (position == usernameRow) {
						if (userId != 0L) {
							val user = messagesController.getUser(userId)
							val username = user?.username

							val text = if (username.isNullOrEmpty()) {
								"-"
							}
							else {
								"@" + user.username
							}

							detailCell.setTextAndValue(detailCell.context.getString(R.string.Username), text, true)
						}
						else if (currentChat != null) {
							val chat = messagesController.getChat(chatId)
							var linkHeader = mContext.getString(R.string.InviteLink)
							var usernameOnly = false

							if (ChatObject.isChannel(chat) && !chat.megagroup) {
								linkHeader = mContext.getString(R.string.channel_link)
								usernameOnly = (chat.flags and TLRPC.CHAT_FLAG_IS_PUBLIC) != 0
							}

							val link = if (usernameOnly) {
								"@${chat?.username}"
							}
							else {
								"https://${messagesController.linkPrefix}/${chat?.username}"
							}

							detailCell.setTextAndValue(linkHeader, link, true)
						}

						//MARK: sets text parameters for the user profile
						detailCell.initTextParams()
					}
					else if (position == locationRow) {
						if (chatInfo != null && chatInfo!!.location is TLRPC.TL_channelLocation) {
							val location = chatInfo!!.location as TLRPC.TL_channelLocation
							detailCell.setTextAndValue(location.address, parentActivity.getString(R.string.AttachLocation), false)
						}
					}
					else if (position == setUsernameRow) {
						val user = getInstance(currentAccount).getCurrentUser()
						val value = user?.username?.takeIf { it.isNotEmpty() }?.let { "@$it" } ?: parentActivity.getString(R.string.UsernameEmpty)

						detailCell.setTextAndValue(value, parentActivity.getString(R.string.Username), true)
						detailCell.setContentDescriptionValueFirst(true)
					}

					detailCell.tag = position
				}

				VIEW_TYPE_ABOUT_LINK -> {
					val aboutLinkCell = holder.itemView as AboutLinkCell

					when (position) {
						userInfoRow -> {
							val user = userInfo?.user ?: messagesController.getUser(userInfo?.id)
							val addLinks = isBot || user != null && userInfo?.about != null
							var description: String? = null

							val bio = when {
								isBot && user?.id == BuildConfig.AI_BOT_ID /* && !user.bot_description.isNullOrEmpty()*/ -> {
									description = aboutLinkCell.context.getString(R.string.UserBio)

									val info = context?.getString(R.string.bot_description)
									info
								}

								isBot && user?.id == BuildConfig.SUPPORT_BOT_ID -> {
									description = aboutLinkCell.context.getString(R.string.description)

									val info = context?.getString(R.string.bot_support_description)
									info
								}

								else -> {
									userInfo?.about
								}
							}

							aboutLinkCell.setTextAndValue(bio, description, addLinks)
						}

						channelInfoRow -> {
							var text = chatInfo!!.about

							while (text.contains("\n\n\n")) {
								text = text.replace("\n\n\n", "\n\n")
							}

							aboutLinkCell.setTextAndValue(text, parentActivity.getString(R.string.description), ChatObject.isChannel(currentChat) || currentChat!!.megagroup)
						}

						bioRow -> {
							val user = userInfo?.user ?: messagesController.getUser(userInfo?.id)

							currentBio = if (user?.id == BuildConfig.AI_BOT_ID) {
								val value = user.bot_description
								aboutLinkCell.setTextAndValue(value, parentActivity.getString(R.string.UserBio), true)
								value
							}
							else if (userInfo == null || !userInfo?.about.isNullOrEmpty()) {
								val value = userInfo?.about ?: parentActivity.getString(R.string.Loading)
								aboutLinkCell.setTextAndValue(value, parentActivity.getString(R.string.UserBio), true)
								userInfo?.about
							}
							else {
								aboutLinkCell.setTextAndValue(parentActivity.getString(R.string.UserBioDetail), parentActivity.getString(R.string.UserBio), true)
								null
							}
						}
					}

					if (position == bioRow) {
						aboutLinkCell.setOnClickListener {
							val user = userInfo?.user ?: messagesController.getUser(userInfo?.id)

							if (user?.self == true) {
								val args = Bundle()
								args.putLong("user_id", userId)
								args.putString("username", user.username)
								val fragment = EditProfileFragment(args)
								fragment.setChangeBigAvatarCallback(this@ProfileActivity)
								presentFragment(fragment)
							}
						}
					}
					else {
						aboutLinkCell.setOnClickListener {
							processOnClickOrPress(position, aboutLinkCell)
						}
					}
				}

				VIEW_TYPE_ADD_ACCOUNT -> {
					val cell = holder.itemView as ProfileSectionCell
					cell.setRoundedType(roundTop = false, roundBottom = false)
					cell.setShouldDrawDivider(false)
					cell.setTextValueIcon(parentActivity.getString(R.string.AddAccount), ProfileSectionCell.NO_VALUE, R.drawable.ic_add)
				}

				VIEW_TYPE_SET_PROFILE_PHOTO  -> {
					val binding = AddProfilePhotoLayoutBinding.bind(holder.itemView)

					val writeButton = RLottieImageView(mContext)
					val cameraDrawable = RLottieDrawable(R.raw.camera_outline, R.raw.camera_outline.toString(), AndroidUtilities.dp(42f), AndroidUtilities.dp(42f), false, null)
					val cellCameraDrawable = RLottieDrawable(R.raw.camera_outline, R.raw.camera_outline.toString() + "_cell", AndroidUtilities.dp(20f), AndroidUtilities.dp(20f), false, null)

					writeButton.setAnimation(cameraDrawable)
					writeButton.contentDescription = mContext.getString(R.string.AccDescrChangeProfilePicture)
					writeButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(mContext.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)
					writeButton.scaleType = ImageView.ScaleType.CENTER

					binding.cameraIconContainer.removeAllViews()
					binding.cameraIconContainer.addView(writeButton)

					binding.root.setOnClickListener {
						if (userId != 0L) {
							if (imageUpdater != null) {
								var user = MessagesController.getInstance(currentAccount).getUser(getInstance(currentAccount).getClientUserId())

								if (user == null) {
									user = getInstance(currentAccount).getCurrentUser()
								}

								if (user == null) {
									return@setOnClickListener
								}

								imageUpdater?.openMenu(user.photo?.photo_big != null && user.photo !is TL_userProfilePhotoEmpty, {
									MessagesController.getInstance(currentAccount).deleteUserPhoto(null)
									cameraDrawable.currentFrame = 0
									cellCameraDrawable.currentFrame = 0
								}) {
									if (!imageUpdater!!.isUploadingImage) {
										cameraDrawable.customEndFrame = 86
										cellCameraDrawable.customEndFrame = 86
										writeButton.playAnimation()
										setAvatarCell?.imageView?.playAnimation()
									}
									else {
										cameraDrawable.setCurrentFrame(0, false)
										cellCameraDrawable.setCurrentFrame(0, false)
									}
								}

								cameraDrawable.currentFrame = 0
								cameraDrawable.customEndFrame = 43

								cellCameraDrawable.currentFrame = 0
								cellCameraDrawable.customEndFrame = 43

								writeButton.playAnimation()

								setAvatarCell?.imageView?.playAnimation()
							}
							else {
								if (playProfileAnimation != 0 && parentLayout!!.fragmentsStack[parentLayout!!.fragmentsStack.size - 2] is ChatActivity) {
									finishFragment()
								}
								else {
									val user = messagesController.getUser(userId)

									if (user == null || user is TLRPC.TL_userEmpty) {
										return@setOnClickListener
									}

									val args = Bundle()
									args.putLong("user_id", userId)

									if (!messagesController.checkCanOpenChat(args, this@ProfileActivity)) {
										return@setOnClickListener
									}

									val removeFragment = arguments!!.getBoolean("removeFragmentOnChatOpen", true)

									if (!AndroidUtilities.isTablet() && removeFragment) {
										notificationCenter.removeObserver(this@ProfileActivity, NotificationCenter.closeChats)
										notificationCenter.postNotificationName(NotificationCenter.closeChats)
									}

									val distance = arguments!!.getInt("nearby_distance", -1)

									if (distance >= 0) {
										args.putInt("nearby_distance", distance)
									}

									val chatActivity = ChatActivity(args)
									chatActivity.setPreloadedSticker(mediaDataController.getGreetingsSticker(), false)

									presentFragment(chatActivity, removeFragment)

									if (AndroidUtilities.isTablet()) {
										finishFragment()
									}
								}
							}
						}
					}
				}

				VIEW_TYPE_PROFILE_SECTION -> {
					val cell = holder.itemView as ProfileSectionCell
					cell.setRoundedType(roundTop = false, roundBottom = false)

					when (position) {
						appearanceRow -> {
							cell.apply {
								setTextValueIcon(parentActivity.getString(R.string.appearance), ProfileSectionCell.NO_VALUE, R.drawable.ic_appearance)
								setShouldDrawDivider(false)
							}
						}

						myNotificationsRow -> {
							cell.apply {
								setTextValueIcon(parentActivity.getString(R.string.system_alerts), ProfileSectionCell.NO_VALUE, R.drawable.my_notifications)
								setShouldDrawDivider(false)
							}
						}

						walletRow -> {
							cell.setTextValueIcon(parentActivity.getString(R.string.wallet), ProfileSectionCell.NO_VALUE, R.drawable.wallet)
						}

						subscriptionsRow -> {
							cell.apply {
								setTextValueIcon(parentActivity.getString(R.string.subscriptions), (paidSubscriptions?.size ?: 0), R.drawable.subscriptions)
								setShouldDrawDivider(false)
							}
						}

						referralRow -> {
							cell.setTextValueIcon(parentActivity.getString(R.string.referral_program), ProfileSectionCell.NO_VALUE, R.drawable.referral)
						}

						aiChatBotRow -> {
							cell.setTextValueIcon(parentActivity.getString(R.string.ai_space), ProfileSectionCell.NO_VALUE, R.drawable.ai_bot)
						}

						foldersRow -> {
							cell.setTextValueIcon(parentActivity.getString(R.string.folders), ProfileSectionCell.NO_VALUE, R.drawable.folders) // TODO: replace icon
						}

						purchasesRow -> {
							cell.setTextValueIcon(parentActivity.getString(R.string.purchases), ProfileSectionCell.NO_VALUE, R.drawable.purchases)
						}

						inviteRow -> {
							val inviteText = if (isPublicProfile()) R.string.invite else R.string.invite_friends
							cell.apply {
								setTextValueIcon(parentActivity.getString(inviteText), invitesCount, R.drawable.invite)
								if (!isPublicProfile()) setShouldDrawDivider(false)
							}
						}

						myCloudRow -> {
							cell.setTextValueIcon(parentActivity.getString(R.string.my_cloud), ProfileSectionCell.NO_VALUE, R.drawable.my_cloud)
						}

						settingsRow -> {
							cell.apply {
								setTextValueIcon(parentActivity.getString(R.string.administration), ProfileSectionCell.NO_VALUE, R.drawable.settings)
								setShouldDrawDivider(false)
							}
						}

						infoRow -> {
							cell.setTextValueIcon(parentActivity.getString(R.string.information), ProfileSectionCell.NO_VALUE, R.drawable.ic_info)
						}

						supportRow -> {
							cell.apply {
								setTextValueIcon(parentActivity.getString(R.string.Support), ProfileSectionCell.NO_VALUE, R.drawable.ic_support)
								setShouldDrawDivider(false)
							}
						}

						devicesRow -> {
							cell.setShouldDrawDivider(false)
							cell.setTextValueIcon(parentActivity.getString(R.string.Devices), ProfileSectionCell.NO_VALUE, R.drawable.settings)
						}
					}
				}

				VIEW_TYPE_SUPPORT -> {
					// nothing to bind
				}

				VIEW_TYPE_LOGOUT -> {
					// nothing to bind
				}

				VIEW_TYPE_PUBLIC_LINK -> {
					holder.itemView.findViewById<View>(R.id.copy).setOnClickListener {
						copyLink()
					}
					holder.itemView.findViewById<View>(R.id.share).setOnClickListener {
						shareLink()
					}

					holder.itemView.findViewById<TextView>(R.id.public_link_value)?.text = inviteLink()
				}

				VIEW_TYPE_SHARE_LINK -> {
					holder.itemView.findViewById<View>(R.id.qr_code_button)?.setOnClickListener {
						openQrFragment()
					}

					holder.itemView.findViewById<TextView>(R.id.invite_link)?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
					holder.itemView.findViewById<TextView>(R.id.invite_link_value)?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)

					holder.itemView.findViewById<TextView>(R.id.invite_link_value)?.text = inviteLink()

					holder.itemView.findViewById<View>(R.id.copy_link).setOnClickListener {
						copyLink()
					}

					holder.itemView.findViewById<View>(R.id.share).setOnClickListener {
						shareLink()
					}
				}

				VIEW_TYPE_USER_ACCOUNT -> {
					val accountSelectCell = holder.itemView as ProfileAccountCell
					val row = position - getFilteredAccountsRows().min()

					val account = getAccountIndex(row)
					accountSelectCell.setAccount(account, UserConfig.selectedAccount == account)

					val unreadCount = notificationsController.getTotalUnreadCount(account)

					accountSelectCell.setUnreadCount(unreadCount)
				}

				VIEW_TYPE_PREMIUM_TEXT_CELL, VIEW_TYPE_TEXT -> {
					val textCell = holder.itemView as TextCell

					var textColor = ResourcesCompat.getColor(mContext.resources, R.color.brand, null)

					if (chatInfo != null) {
						if (ChatObject.isChannel(currentChat) && !currentChat!!.megagroup) {
							textColor = ResourcesCompat.getColor(mContext.resources, R.color.text, null)
						}
					}

					textCell.setColors(icon = ResourcesCompat.getColor(mContext.resources, R.color.brand, null), text = textColor)
					textCell.tag = ResourcesCompat.getColor(mContext.resources, R.color.text, null)

					if (position == settingsTimerRow) {
						val encryptedChat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))

						val value = if (encryptedChat?.ttl == 0) {
							mContext.getString(R.string.ShortMessageLifetimeForever)
						}
						else {
							LocaleController.formatTTLString(encryptedChat?.ttl ?: 0)
						}

						textCell.setTextAndValue(mContext.getString(R.string.MessageLifetime), value, false)
					}
					else if (position == unblockRow) {
						textCell.setText(mContext.getString(R.string.Unblock), false)
						textCell.setColors(null, ResourcesCompat.getColor(mContext.resources, R.color.purple, null))
					}
					else if (position == settingsKeyRow) {
						val identiconDrawable = IdenticonDrawable()
						val encryptedChat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))
						identiconDrawable.setEncryptedChat(encryptedChat)
						textCell.setTextAndValueDrawable(mContext.getString(R.string.EncryptionKey), identiconDrawable, false)
					}
					else if (position == joinRow) {
						textCell.setColors(null, ResourcesCompat.getColor(mContext.resources, R.color.brand, null))

						if (currentChat?.megagroup == true) {
							textCell.setText(mContext.getString(R.string.ProfileJoinGroup), false)
						}
						else {
							textCell.setText(mContext.getString(R.string.ProfileJoinChannel), false)
						}
					}
					else if (position == subscribersRow) {
						if (chatInfo != null) {
							if (ChatObject.isChannel(currentChat) && !currentChat!!.megagroup) {
								textCell.setTextAndValueAndIcon(mContext.getString(R.string.ChannelSubscribers), String.format("%d", chatInfo!!.participants_count), R.drawable.subscribers, position != membersSectionRow - 1)
							}
							else {
								textCell.setTextAndValueAndIcon(mContext.getString(R.string.ChannelMembers), String.format("%d", chatInfo!!.participants_count), R.drawable.subscribers, position != membersSectionRow - 1)
							}
						}
						else {
							if (ChatObject.isChannel(currentChat) && !currentChat!!.megagroup) {
								textCell.setTextAndIcon(mContext.getString(R.string.ChannelSubscribers), R.drawable.subscribers, position != membersSectionRow - 1)
							}
							else {
								textCell.setTextAndIcon(mContext.getString(R.string.ChannelMembers), R.drawable.subscribers, position != membersSectionRow - 1)
							}
						}
					}
					else if (position == subscribersRequestsRow) {
						if (chatInfo != null) {
							textCell.setTextAndValueAndIcon(mContext.getString(R.string.SubscribeRequests), String.format("%d", chatInfo!!.requests_pending), R.drawable.msg_requests, position != membersSectionRow - 1)
						}
					}
					else if (position == administratorsRow) {
						if (chatInfo != null) {
							textCell.setTextAndValueAndIcon(mContext.getString(R.string.ChannelAdministrators), String.format("%d", chatInfo!!.admins_count), R.drawable.administrators, position != membersSectionRow - 1)
						}
						else {
							textCell.setTextAndIcon(mContext.getString(R.string.ChannelAdministrators), R.drawable.administrators, position != membersSectionRow - 1)
						}
					}
					else if (position == blockedUsersRow) {
						if (chatInfo != null) {
							textCell.setTextAndValueAndIcon(mContext.getString(R.string.ChannelBlacklist), String.format("%d", max(chatInfo!!.banned_count, chatInfo!!.kicked_count)), R.drawable.demote, position != membersSectionRow - 1)
						}
						else {
							textCell.setTextAndIcon(mContext.getString(R.string.ChannelBlacklist), R.drawable.user_add, position != membersSectionRow - 1)
						}
					}
					else if (position == addMemberRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.AddMember), R.drawable.user_add, membersSectionRow == -1)
					}
					else if (position == sendMessageRow) {
						textCell.setText(mContext.getString(R.string.SendMessageLocation), true)
					}
					else if (position == reportReactionRow) {
						val chat = messagesController.getChat(-reportReactionFromDialogId)

						if (chat != null && ChatObject.canBlockUsers(chat)) {
							textCell.setText(mContext.getString(R.string.ReportReactionAndBan), false)
						}
						else {
							textCell.setText(mContext.getString(R.string.ReportReaction), false)
						}

						textCell.setColors(null, ResourcesCompat.getColor(mContext.resources, R.color.purple, null))
					}
					else if (position == reportRow) {
						textCell.setText(mContext.getString(R.string.ReportUserLocation), false)
						textCell.setColors(null, ResourcesCompat.getColor(mContext.resources, R.color.purple, null))
					}
					else if (position == languageRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.Language), R.drawable.msg_language, false)
						textCell.imageLeft = 23
					}
					else if (position == notificationRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.NotificationsAndSounds), R.drawable.msg_notifications, true)
					}
					else if (position == privacyRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret, true)
					}
					else if (position == dataRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.DataSettings), R.drawable.msg_data, true)
					}
					else if (position == chatRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3, true)
					}
					else if (position == filtersRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.Filters), R.drawable.msg_folders, true)
					}
					else if (position == stickersRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.StickersName), R.drawable.msg_sticker, true)
					}
					else if (position == questionRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.AskAQuestion), R.drawable.msg_ask_question, true)
					}
					else if (position == faqRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.TelegramFAQ), R.drawable.msg_help, true)
					}
					else if (position == policyRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.PrivacyPolicy), R.drawable.msg_policy, false)
					}
					else if (position == sendLogsRow) {
						textCell.setText(mContext.getString(R.string.DebugSendLogs), true)
					}
					else if (position == sendLastLogsRow) {
						textCell.setText(mContext.getString(R.string.DebugSendLastLogs), true)
					}
					else if (position == clearLogsRow) {
						textCell.setText(mContext.getString(R.string.DebugClearLogs), true)
					}
					else if (position == setAvatarRow) {
						cellCameraDrawable?.customEndFrame = 86
						cellCameraDrawable?.setCurrentFrame(85, false)

						textCell.setTextAndIcon(mContext.getString(R.string.SetProfilePhoto), cellCameraDrawable, false)
						textCell.setColors(ResourcesCompat.getColor(mContext.resources, R.color.brand, null), ResourcesCompat.getColor(mContext.resources, R.color.brand, null))

						textCell.imageView.setPadding(0, 0, 0, AndroidUtilities.dp(8f))
						textCell.imageLeft = 12

						setAvatarCell = textCell
					}
					else if (position == addToGroupButtonRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.AddToGroupOrChannel), R.drawable.msg_groups_create, false)
						textCell.setColors(ResourcesCompat.getColor(mContext.resources, R.color.brand, null), ResourcesCompat.getColor(mContext.resources, R.color.brand, null))
					}
					else if (position == premiumRow) {
						textCell.setTextAndIcon(mContext.getString(R.string.TelegramPremium), AnimatedEmojiDrawable.WrapSizeDrawable(PremiumGradient.getInstance().premiumStarMenuDrawable, AndroidUtilities.dp(24f), AndroidUtilities.dp(24f)), false)
						textCell.imageLeft = 23
					}
					else if (position == subscriptionCostRow) {
						val txtResId = if (ChatObject.isOnlineCourse(currentChat)) {
							R.string.online_course_fee
						}
						else {
							R.string.subscription_cost
						}

						textCell.setTextAndValueAndIcon(mContext.getString(txtResId), mContext.getString(R.string.simple_coin_format, currentChat!!.cost).fillElloCoinLogos(topIconPadding = 4, size = 16f, tintColor = ApplicationLoader.applicationContext.getColor(R.color.dark)), R.drawable.ic_withdraw, true)
						textCell.valueTextView.textColor = ResourcesCompat.getColor(mContext.resources, R.color.text, null)
					}
					else if (position == subscriptionExpireRow) {
						if (ChatObject.isSubscriptionChannel(currentChat)) {
							textCell.setTextAndValueAndIcon(mContext.getString(R.string.next_due_date), String.format("%s", LocaleController.getInstance().chatFullDate.format(subscriptionExpireAt * 1000L)), R.drawable.ic_calendar_brand, true)
						}
						else {
							textCell.setTextAndValueAndIcon(mContext.getString(R.string.end_date), String.format("%s", LocaleController.getInstance().chatFullDate.format(currentChat?.end_date ?: 0L)), R.drawable.ic_calendar_brand, true)
						}

						textCell.valueTextView.textColor = ResourcesCompat.getColor(mContext.resources, R.color.text, null)
					}
					else if (position == subscriptionBeginRow) {
						textCell.setTextAndValueAndIcon(mContext.getString(R.string.start_date), String.format("%s", LocaleController.getInstance().chatFullDate.format(currentChat?.start_date ?: 0L)), R.drawable.ic_calendar_brand, true)
						textCell.valueTextView.textColor = ResourcesCompat.getColor(mContext.resources, R.color.text, null)
					}
				}

				VIEW_TYPE_NOTIFICATIONS_CHECK -> {
					val checkCell = holder.itemView as NotificationsCheckCell

					if (position == notificationsRow) {
						val preferences = MessagesController.getNotificationsSettings(currentAccount)

						val did = if (dialogId != 0L) {
							dialogId
						}
						else if (userId != 0L) {
							userId
						}
						else {
							-chatId
						}

						var enabled = false
						// val custom = preferences.getBoolean("custom_$did", false)
						val hasOverride = preferences.contains("notify2_$did")
						val value = preferences.getInt("notify2_$did", 0)
						var delta = preferences.getInt("notifyuntil_$did", 0)
//						var `val`: String?

						if (value == 3 && delta != Int.MAX_VALUE) {
							delta -= connectionsManager.currentTime

							if (delta <= 0) {
//								`val` = if (custom) {
//									mContext.getString(R.string.NotificationsCustom)
//								}
//								else {
//									mContext.getString(R.string.NotificationsOn)
//								}

								enabled = true
							}
							else if (delta < 60 * 60) {
								// `val` = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Minutes", delta / 60))
							}
							else if (delta < 60 * 60 * 24) {
								// `val` = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Hours", ceil((delta / 60.0f / 60).toDouble()).toInt()))
							}
							else if (delta < 60 * 60 * 24 * 365) {
								// `val` = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Days", ceil((delta / 60.0f / 60 / 24).toDouble()).toInt()))
							}
							else {
								// `val` = null
							}
						}
						else {
							if (value == 0) {
								enabled = if (hasOverride) {
									true
								}
								else {
									notificationsController.isGlobalNotificationsEnabled(did)
								}
							}
							else if (value == 1) {
								enabled = true
							}

//							`val` = if (enabled && custom) {
//								mContext.getString(R.string.NotificationsCustom)
//							}
//							else {
//								if (enabled) mContext.getString(R.string.NotificationsOn) else mContext.getString(R.string.NotificationsOff)
//							}
						}

//						if (`val` == null) {
//							`val` = mContext.getString(R.string.NotificationsOff)
//						}

						checkCell.setAnimationsEnabled(fragmentOpened)
						checkCell.setTextAndValueAndCheck(mContext.getString(R.string.Notifications), null, enabled, false)
					}
				}

				VIEW_TYPE_SHADOW -> {
					val sectionCell = holder.itemView
					sectionCell.tag = position

					if (position == infoSectionRow && lastSectionRow == -1 && secretSettingsSectionRow == -1 && sharedMediaRow == -1 && membersSectionRow == -1 || position == secretSettingsSectionRow || position == lastSectionRow || position == membersSectionRow && lastSectionRow == -1 && sharedMediaRow == -1) {
						sectionCell.setBackgroundResource(R.drawable.greydivider_bottom)
					}
					else {
						sectionCell.setBackgroundResource(R.drawable.greydivider)
					}
				}

				VIEW_TYPE_USER -> {
					val userCell = holder.itemView as UserCell
					var part: TLRPC.ChatParticipant?

					try {
						part = if (visibleSortedUsers.isNotEmpty()) {
							visibleChatParticipants[visibleSortedUsers[position - membersStartRow]]
						}
						else {
							visibleChatParticipants[position - membersStartRow]
						}
					}
					catch (e: Exception) {
						part = null
						FileLog.e(e)
					}

					if (part != null) {
						val role = if (part is TLRPC.TL_chatChannelParticipant) {
							val channelParticipant = part.channelParticipant

							if (!TextUtils.isEmpty(channelParticipant.rank)) {
								channelParticipant.rank
							}
							else {
								when (channelParticipant) {
									is TLRPC.TL_channelParticipantCreator -> mContext.getString(R.string.ChannelCreator)
									is TLRPC.TL_channelParticipantAdmin -> mContext.getString(R.string.ChannelAdmin)
									else -> null
								}
							}
						}
						else {
							when (part) {
								is TLRPC.TL_chatParticipantCreator -> mContext.getString(R.string.ChannelCreator)
								is TLRPC.TL_chatParticipantAdmin -> mContext.getString(R.string.ChannelAdmin)
								else -> null
							}
						}

						userCell.setAdminRole(role)
						userCell.setData(messagesController.getUser(part.user_id), null, null, 0, position != membersEndRow - 1)
					}
				}

				VIEW_TYPE_BOTTOM_PADDING -> {
					holder.itemView.requestLayout()
				}

				VIEW_TYPE_SUGGESTION -> {
					val suggestionCell = holder.itemView as SettingsSuggestionCell
					suggestionCell.setType(if (position == passwordSuggestionRow) SettingsSuggestionCell.TYPE_PASSWORD else SettingsSuggestionCell.TYPE_PHONE)
				}

				VIEW_TYPE_ADDTOGROUP_INFO -> {
					val addToGroupInfo = holder.itemView as TextInfoPrivacyCell
					addToGroupInfo.setBackgroundResource(R.drawable.greydivider)
					addToGroupInfo.setText(mContext.getString(R.string.BotAddToGroupOrChannelInfo))
				}
			}
		}

		override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
			if (holder.adapterPosition == setAvatarRow) {
				setAvatarCell = null
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			if (notificationRow != -1) {
				val position = holder.adapterPosition
				return position == notificationRow || position == privacyRow || position == languageRow || position == setUsernameRow || position == bioRow || position == versionRow || position == dataRow || position == chatRow || position == questionRow || position == devicesRow || position == filtersRow || position == stickersRow || position == faqRow || position == policyRow || position == sendLogsRow || position == sendLastLogsRow || position == clearLogsRow || position == setAvatarRow || position == addToGroupButtonRow || position == premiumRow
			}

			if (holder.itemView is UserCell) {
				val `object` = holder.itemView.currentObject

				if (`object` is User) {
					if (UserObject.isUserSelf(`object`)) {
						return false
					}
				}
			}

			val type = holder.itemViewType

			return type != VIEW_TYPE_HEADER && type != VIEW_TYPE_DIVIDER && type != VIEW_TYPE_SHADOW && type != VIEW_TYPE_EMPTY && type != VIEW_TYPE_SMALL_EMPTY && type != VIEW_TYPE_BOTTOM_PADDING && type != VIEW_TYPE_SHARED_MEDIA && type != 9 && type != 10 // These are legacy ones, left for compatibility
		}

		override fun getItemCount(): Int {
			return rowCount
		}

		override fun getItemViewType(position: Int): Int {
			when (position) {
				in getFilteredAccountsRows() -> {
					return VIEW_TYPE_USER_ACCOUNT
				}

				addAccountRow -> {
					return VIEW_TYPE_ADD_ACCOUNT
				}

				setProfilePhotoRow -> {
					return VIEW_TYPE_SET_PROFILE_PHOTO
				}

				logoutRow -> {
					return VIEW_TYPE_LOGOUT
				}

				appearanceRow, myNotificationsRow, walletRow, subscriptionsRow, referralRow, aiChatBotRow, foldersRow, purchasesRow, inviteRow, myCloudRow, settingsRow, supportRow, infoRow, devicesRow -> {
					return VIEW_TYPE_PROFILE_SECTION
				}

				membersHeaderRow, settingsSectionRow2, helpHeaderRow, debugHeaderRow, userAboutHeaderRow -> {
					return VIEW_TYPE_HEADER
				}

				usernameRow, locationRow, setUsernameRow -> {
					return VIEW_TYPE_TEXT_DETAIL
				}

				userInfoRow, channelInfoRow, bioRow -> {
					return VIEW_TYPE_ABOUT_LINK
				}

				settingsTimerRow, settingsKeyRow, reportRow, reportReactionRow, subscribersRow, subscribersRequestsRow, administratorsRow, blockedUsersRow, addMemberRow, joinRow, unblockRow, sendMessageRow, notificationRow, privacyRow, languageRow, dataRow, chatRow, questionRow, filtersRow, stickersRow, faqRow, policyRow, sendLogsRow, sendLastLogsRow, clearLogsRow, setAvatarRow, addToGroupButtonRow, subscriptionCostRow, subscriptionBeginRow, subscriptionExpireRow -> {
					return VIEW_TYPE_TEXT
				}

				notificationsDividerRow -> {
					return VIEW_TYPE_SHADOW
				}

				settingsBottomDividerRow, subscriptionsBottomDividerRow, myNotificationsBottomDividerRow, appearanceBottomDividerRow, myAccountsDividerRow, myProfileTopDividerRow, myProfileBioDividerRow, inviteRowDividerRow, supportDividerRow, logoutDividerRow, setProfilePhotoDividerRow -> {
					return VIEW_TYPE_SMALL_EMPTY
				}

				notificationsRow -> {
					return VIEW_TYPE_NOTIFICATIONS_CHECK
				}

				infoSectionRow, lastSectionRow, membersSectionRow, secretSettingsSectionRow, settingsSectionRow, devicesSectionRow, helpSectionCell, setAvatarSectionRow, passwordSuggestionSectionRow, phoneSuggestionSectionRow, premiumSectionsRow, reportDividerRow, publicLinkSectionRow, subscriptionCostSectionRow -> {
					return VIEW_TYPE_SHADOW
				}

				in membersStartRow until membersEndRow -> {
					return VIEW_TYPE_USER
				}

				emptyRow -> {
					return VIEW_TYPE_EMPTY
				}

				bottomPaddingRow -> {
					return VIEW_TYPE_BOTTOM_PADDING
				}

				sharedMediaRow -> {
					return VIEW_TYPE_SHARED_MEDIA
				}

				versionRow -> {
					return VIEW_TYPE_VERSION
				}

				passwordSuggestionRow, phoneSuggestionRow -> {
					return VIEW_TYPE_SUGGESTION
				}

				addToGroupInfoRow -> {
					return VIEW_TYPE_ADDTOGROUP_INFO
				}

				premiumRow -> {
					return VIEW_TYPE_PREMIUM_TEXT_CELL
				}

				shareLinkRow -> {
					return VIEW_TYPE_SHARE_LINK
				}

				publicLinkRow -> {
					return VIEW_TYPE_PUBLIC_LINK
				}

				else -> {
					return 0
				}
			}
		}
	}

	private fun shareLink() {
		val context = context ?: return

		val link = inviteLink()
		val intent = Intent(Intent.ACTION_SEND)
		intent.type = "text/plain"
		intent.putExtra(Intent.EXTRA_TEXT, link)

		startActivity(context, Intent.createChooser(intent, context.getString(R.string.BotShare)), null)
	}

	fun inviteLink(): String {
		val username = if (currentChat != null) {
			val chat = messagesController.getChat(chatId)
			chat?.username ?: ""
		}
		else {
			val user = messagesController.getUser(userId)
			user?.username ?: ""
		}

		if (userInfo?.is_public == false && inviteLink.isNotBlank()) {
			return inviteLink
		}

		return "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + username
	}

	private fun copyLink() {
		val link = inviteLink()

		try {
			val clipboard = ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
			val clip = ClipData.newPlainText("label", link)

			clipboard.setPrimaryClip(clip)

			if (AndroidUtilities.shouldShowClipboardToast()) {
				Bulletin.addDelegate(this@ProfileActivity, object : Bulletin.Delegate {
					override fun onHide(bulletin: Bulletin) {
						Bulletin.removeDelegate(this@ProfileActivity)
					}

					override fun getBottomOffset(tag: Int) = AndroidUtilities.dp(BottomNavigationPanel.height.toFloat())
				})

				BulletinFactory.of(this@ProfileActivity).createCopyBulletin(ApplicationLoader.applicationContext.getString(R.string.LinkCopied)).show()
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private inner class SearchAdapter(private val mContext: Context) : RecyclerListView.SelectionAdapter() {
		private val searchArray = arrayOf(SearchResult(500, mContext.getString(R.string.EditName), 0) { presentFragment(ChangeNameActivity()) }, SearchResult(501, mContext.getString(R.string.ChangePhoneNumber), 0) { presentFragment(ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER)) }, SearchResult(502, mContext.getString(R.string.AddAnotherAccount), 0) {
			var freeAccount = -1

			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				if (!getInstance(a).isClientActivated) {
					freeAccount = a
					break
				}
			}

			if (freeAccount >= 0) {
				presentFragment(LoginActivity(freeAccount))
			}
		}, SearchResult(503, mContext.getString(R.string.UserBio), 0) {
			if (userInfo != null) {
				presentFragment(ChangeBioActivity())
			}
		}, SearchResult(1, mContext.getString(R.string.NotificationsAndSounds), R.drawable.msg_notifications) { presentFragment(NotificationsSettingsActivity()) }, SearchResult(2, mContext.getString(R.string.NotificationsPrivateChats), mContext.getString(R.string.NotificationsAndSounds), R.drawable.msg_notifications) {
			presentFragment(NotificationsCustomSettingsActivity(NotificationsController.TYPE_PRIVATE, ArrayList(), true))
		}, SearchResult(3, mContext.getString(R.string.NotificationsGroups), mContext.getString(R.string.NotificationsAndSounds), R.drawable.msg_notifications) {
			presentFragment(NotificationsCustomSettingsActivity(NotificationsController.TYPE_GROUP, ArrayList(), true))
		}, SearchResult(4, mContext.getString(R.string.NotificationsChannels), mContext.getString(R.string.NotificationsAndSounds), R.drawable.msg_notifications) {
			presentFragment(NotificationsCustomSettingsActivity(NotificationsController.TYPE_CHANNEL, ArrayList(), true))
		}, SearchResult(5, mContext.getString(R.string.VoipNotificationSettings), "callsSectionRow", mContext.getString(R.string.NotificationsAndSounds), R.drawable.msg_notifications) { presentFragment(NotificationsSettingsActivity()) }, SearchResult(6, mContext.getString(R.string.BadgeNumber), "badgeNumberSection", mContext.getString(R.string.NotificationsAndSounds), R.drawable.msg_notifications) { presentFragment(NotificationsSettingsActivity()) }, SearchResult(7, mContext.getString(R.string.InAppNotifications), "inappSectionRow", mContext.getString(R.string.NotificationsAndSounds), R.drawable.msg_notifications) { presentFragment(NotificationsSettingsActivity()) }, SearchResult(8, mContext.getString(R.string.ContactJoined), "contactJoinedRow", mContext.getString(R.string.NotificationsAndSounds), R.drawable.msg_notifications) { presentFragment(NotificationsSettingsActivity()) }, SearchResult(9, mContext.getString(R.string.PinnedMessages), "pinnedMessageRow", mContext.getString(R.string.NotificationsAndSounds), R.drawable.msg_notifications) { presentFragment(NotificationsSettingsActivity()) }, SearchResult(10, mContext.getString(R.string.ResetAllNotifications), "resetNotificationsRow", mContext.getString(R.string.NotificationsAndSounds), R.drawable.msg_notifications) { presentFragment(NotificationsSettingsActivity()) }, SearchResult(100, mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(PrivacySettingsActivity()) }, SearchResult(101, mContext.getString(R.string.BlockedUsers), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(PrivacyUsersActivity()) }, SearchResult(105, mContext.getString(R.string.PrivacyPhone), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) {
			presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHONE, true))
		}, SearchResult(102, mContext.getString(R.string.PrivacyLastSeen), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) {
			presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_LAST_SEEN, true))
		}, SearchResult(103, mContext.getString(R.string.PrivacyProfilePhoto), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) {
			presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHOTO, true))
		}, SearchResult(104, mContext.getString(R.string.PrivacyForwards), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) {
			presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_FORWARDS, true))
		}, SearchResult(122, mContext.getString(R.string.PrivacyP2P), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) {
			presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_P2P, true))
		}, SearchResult(106, mContext.getString(R.string.Calls), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) {
			presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_CALLS, true))
		}, SearchResult(107, mContext.getString(R.string.GroupsAndChannels), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) {
			presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_INVITE, true))
		}, SearchResult(123, mContext.getString(R.string.PrivacyVoiceMessages), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret, Runnable {
			if (!userConfig.isPremium) {
				try {
					fragmentView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
				BulletinFactory.of(this@ProfileActivity).createRestrictVoiceMessagesPremiumBulletin().show()
				return@Runnable
			}
			presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_VOICE_MESSAGES, true))
		}), SearchResult(108, mContext.getString(R.string.Passcode), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(PasscodeActivity.determineOpenFragment()) }, SearchResult(109, mContext.getString(R.string.TwoStepVerification), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(TwoStepVerificationActivity()) }, SearchResult(110, mContext.getString(R.string.SessionsTitle), R.drawable.msg_secret) { presentFragment(SessionsActivity(SessionsActivity.ALL_SESSIONS)) }, if (messagesController.autoarchiveAvailable) SearchResult(121, mContext.getString(R.string.ArchiveAndMute), "newChatsRow", mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(PrivacySettingsActivity()) } else null, SearchResult(112, mContext.getString(R.string.DeleteAccountIfAwayFor2), "deleteAccountRow", mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(PrivacySettingsActivity()) }, SearchResult(113, mContext.getString(R.string.PrivacyPaymentsClear), "paymentsClearRow", mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(PrivacySettingsActivity()) }, SearchResult(114, mContext.getString(R.string.WebSessionsTitle), mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(SessionsActivity(SessionsActivity.WEB_SESSIONS)) }, SearchResult(115, mContext.getString(R.string.SyncContactsDelete), "contactsDeleteRow", mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(PrivacySettingsActivity()) }, SearchResult(116, mContext.getString(R.string.SyncContacts), "contactsSyncRow", mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(PrivacySettingsActivity()) }, SearchResult(117, mContext.getString(R.string.SuggestContacts), "contactsSuggestRow", mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(PrivacySettingsActivity()) }, SearchResult(118, mContext.getString(R.string.MapPreviewProvider), "secretMapRow", mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(PrivacySettingsActivity()) }, SearchResult(119, mContext.getString(R.string.SecretWebPage), "secretWebpageRow", mContext.getString(R.string.PrivacySettings), R.drawable.msg_secret) { presentFragment(PrivacySettingsActivity()) }, SearchResult(120, mContext.getString(R.string.Devices), R.drawable.msg_secret) { presentFragment(SessionsActivity(SessionsActivity.ALL_SESSIONS)) }, SearchResult(200, mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(201, mContext.getString(R.string.DataUsage), "usageSectionRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(202, mContext.getString(R.string.StorageUsage), mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(CacheControlActivity()) }, SearchResult(203, mContext.getString(R.string.KeepMedia), "keepMediaRow", mContext.getString(R.string.DataSettings), mContext.getString(R.string.StorageUsage), R.drawable.msg_data) { presentFragment(CacheControlActivity()) }, SearchResult(204, mContext.getString(R.string.ClearMediaCache), "cacheRow", mContext.getString(R.string.DataSettings), mContext.getString(R.string.StorageUsage), R.drawable.msg_data) { presentFragment(CacheControlActivity()) }, SearchResult(205, mContext.getString(R.string.LocalDatabase), "databaseRow", mContext.getString(R.string.DataSettings), mContext.getString(R.string.StorageUsage), R.drawable.msg_data) { presentFragment(CacheControlActivity()) }, SearchResult(206, mContext.getString(R.string.NetworkUsage), mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataUsageActivity()) }, SearchResult(207, mContext.getString(R.string.AutomaticMediaDownload), "mediaDownloadSectionRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(208, mContext.getString(R.string.WhenUsingMobileData), mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataAutoDownloadActivity(0)) }, SearchResult(209, mContext.getString(R.string.WhenConnectedOnWiFi), mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataAutoDownloadActivity(1)) }, SearchResult(210, mContext.getString(R.string.WhenRoaming), mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataAutoDownloadActivity(2)) }, SearchResult(211, mContext.getString(R.string.ResetAutomaticMediaDownload), "resetDownloadRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(212, mContext.getString(R.string.AutoplayMedia), "autoplayHeaderRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(213, mContext.getString(R.string.AutoplayGIF), "autoplayGifsRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(214, mContext.getString(R.string.AutoplayVideo), "autoplayVideoRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(215, mContext.getString(R.string.Streaming), "streamSectionRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(216, mContext.getString(R.string.EnableStreaming), "enableStreamRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(217, mContext.getString(R.string.Calls), "callsSectionRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(218, mContext.getString(R.string.VoipUseLessData), "useLessDataForCallsRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(219, mContext.getString(R.string.VoipQuickReplies), "quickRepliesRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(220, mContext.getString(R.string.ProxySettings), mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(ProxyListActivity()) }, SearchResult(221, mContext.getString(R.string.UseProxyForCalls), "callsRow", mContext.getString(R.string.DataSettings), mContext.getString(R.string.ProxySettings), R.drawable.msg_data) { presentFragment(ProxyListActivity()) }, SearchResult(111, mContext.getString(R.string.PrivacyDeleteCloudDrafts), "clearDraftsRow", mContext.getString(R.string.DataSettings), R.drawable.msg_data) { presentFragment(DataSettingsActivity()) }, SearchResult(300, mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)) }, SearchResult(301, mContext.getString(R.string.TextSizeHeader), "textSizeHeaderRow", mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)) }, SearchResult(302, mContext.getString(R.string.ChatBackground), mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(WallpapersListActivity(WallpapersListActivity.TYPE_ALL)) }, SearchResult(303, mContext.getString(R.string.SetColor), null, mContext.getString(R.string.ChatSettings), mContext.getString(R.string.ChatBackground), R.drawable.msg_msgbubble3) { presentFragment(WallpapersListActivity(WallpapersListActivity.TYPE_COLOR)) }, SearchResult(304, mContext.getString(R.string.ResetChatBackgrounds), "resetRow", mContext.getString(R.string.ChatSettings), mContext.getString(R.string.ChatBackground), R.drawable.msg_msgbubble3) { presentFragment(WallpapersListActivity(WallpapersListActivity.TYPE_ALL)) }, SearchResult(305, mContext.getString(R.string.AutoNightTheme), mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT)) }, SearchResult(306, mContext.getString(R.string.ColorTheme), "themeHeaderRow", mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)) }, SearchResult(307, mContext.getString(R.string.ChromeCustomTabs), "customTabsRow", mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)) }, SearchResult(308, mContext.getString(R.string.DirectShare), "directShareRow", mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)) }, SearchResult(309, mContext.getString(R.string.EnableAnimations), "enableAnimationsRow", mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)) }, SearchResult(310, mContext.getString(R.string.RaiseToSpeak), "raiseToSpeakRow", mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)) }, SearchResult(311, mContext.getString(R.string.SendByEnter), "sendByEnterRow", mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)) }, SearchResult(312, mContext.getString(R.string.SaveToGallerySettings), "saveToGalleryRow", mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)) }, SearchResult(318, mContext.getString(R.string.DistanceUnits), "distanceRow", mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) { presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)) }, SearchResult(313, mContext.getString(R.string.StickersAndMasks), mContext.getString(R.string.ChatSettings), R.drawable.msg_msgbubble3) {
			presentFragment(StickersActivity(MediaDataController.TYPE_IMAGE, null))
		}, SearchResult(314, mContext.getString(R.string.SuggestStickers), "suggestRow", mContext.getString(R.string.ChatSettings), mContext.getString(R.string.StickersAndMasks), R.drawable.msg_msgbubble3) {
			presentFragment(StickersActivity(MediaDataController.TYPE_IMAGE, null))
		}, SearchResult(315, mContext.getString(R.string.FeaturedStickers), null, mContext.getString(R.string.ChatSettings), mContext.getString(R.string.StickersAndMasks), R.drawable.msg_msgbubble3) { presentFragment(FeaturedStickersActivity()) }, SearchResult(316, mContext.getString(R.string.Masks), null, mContext.getString(R.string.ChatSettings), mContext.getString(R.string.StickersAndMasks), R.drawable.msg_msgbubble3) {
			presentFragment(StickersActivity(MediaDataController.TYPE_MASK, null))
		}, SearchResult(317, mContext.getString(R.string.ArchivedStickers), null, mContext.getString(R.string.ChatSettings), mContext.getString(R.string.StickersAndMasks), R.drawable.msg_msgbubble3) { presentFragment(ArchivedStickersActivity(MediaDataController.TYPE_IMAGE)) }, SearchResult(317, mContext.getString(R.string.ArchivedMasks), null, mContext.getString(R.string.ChatSettings), mContext.getString(R.string.StickersAndMasks), R.drawable.msg_msgbubble3) { presentFragment(ArchivedStickersActivity(MediaDataController.TYPE_MASK)) }, SearchResult(400, mContext.getString(R.string.Language), R.drawable.msg_language) { presentFragment(LanguageSelectActivity()) }, SearchResult(402, mContext.getString(R.string.AskAQuestion), mContext.getString(R.string.SettingsHelp), R.drawable.msg_help) {
			showDialog(AlertsCreator.createSupportAlert(this@ProfileActivity))
		}, SearchResult(403, mContext.getString(R.string.TelegramFAQ), mContext.getString(R.string.SettingsHelp), R.drawable.msg_help) {
			Browser.openUrl(parentActivity, mContext.getString(R.string.TelegramFaqUrl))
		}, SearchResult(404, mContext.getString(R.string.PrivacyPolicy), mContext.getString(R.string.SettingsHelp), R.drawable.msg_help) {
			Browser.openUrl(parentActivity, mContext.getString(R.string.PrivacyPolicyUrl))
		})

		val faqSearchArray = ArrayList<FaqSearchResult>()
		val recentSearches = ArrayList<Any>()
		private var resultNames = ArrayList<CharSequence>()
		var searchResults = ArrayList<SearchResult>()
		var faqSearchResults = ArrayList<FaqSearchResult>()

		var isSearchWas = false
			private set

		var faqWebPage: TLRPC.WebPage? = null
		private var loadingFaqPage = false

		init {
			val resultHashMap = mutableMapOf<Int, SearchResult?>()

			for (a in searchArray.indices) {
				if (searchArray[a] == null) {
					continue
				}

				resultHashMap[searchArray[a]!!.guid] = searchArray[a]
			}

			val set = MessagesController.getGlobalMainSettings().getStringSet("settingsSearchRecent2", null)

			if (set != null) {
				for (value in set) {
					try {
						val data = SerializedData(Utilities.hexToBytes(value))
						val num = data.readInt32(false)
						val type = data.readInt32(false)

						if (type == 0) {
							val title = data.readString(false)
							val count = data.readInt32(false)
							var path: Array<String?>? = null

							if (count > 0) {
								path = arrayOfNulls(count)

								for (a in 0 until count) {
									path[a] = data.readString(false)
								}
							}

							val url = data.readString(false)

							if (title != null && path != null && url != null) {
								val result = FaqSearchResult(title, path.map { it ?: "" }.toTypedArray(), url)
								result.num = num

								recentSearches.add(result)
							}
						}
						else if (type == 1) {
							val result = resultHashMap[data.readInt32(false)]

							if (result != null) {
								result.num = num
								recentSearches.add(result)
							}
						}
					}
					catch (ignore: Exception) {
					}
				}
			}

			recentSearches.sortWith { o1, o2 ->
				val n1 = getNum(o1)
				val n2 = getNum(o2)

				if (n1 < n2) {
					return@sortWith -1
				}
				else if (n1 > n2) {
					return@sortWith 1
				}
				0
			}
		}

		fun loadFaqWebPage() {
			val parentActivity = parentActivity ?: return

			faqWebPage = messagesController.faqWebPage

			if (faqWebPage != null) {
				faqSearchArray.addAll(messagesController.faqSearchArray)
			}

			if (faqWebPage != null || loadingFaqPage) {
				return
			}

			loadingFaqPage = true

			val req2 = TLRPC.TL_messages_getWebPage()
			req2.url = parentActivity.getString(R.string.TelegramFaqUrl)
			req2.hash = 0

			connectionsManager.sendRequest(req2) { response2, _ ->
				if (response2 is TLRPC.WebPage) {
					val arrayList = ArrayList<FaqSearchResult>()

					if (response2.cached_page != null) {
						var a = 0
						val n = response2.cached_page.blocks.size

						while (a < n) {
							val block = response2.cached_page.blocks[a]

							if (block is TLRPC.TL_pageBlockList) {
								var paragraph: String? = null

								if (a != 0) {
									val prevBlock = response2.cached_page.blocks[a - 1]

									if (prevBlock is TLRPC.TL_pageBlockParagraph) {
										paragraph = ArticleViewer.getPlainText(prevBlock.text).toString()
									}
								}

								var b = 0
								val n2 = block.items.size

								while (b < n2) {
									val item = block.items[b]

									if (item is TLRPC.TL_pageListItemText) {
										val url = ArticleViewer.getUrl(item.text)
										val text = ArticleViewer.getPlainText(item.text).toString()

										if (TextUtils.isEmpty(url) || TextUtils.isEmpty(text)) {
											b++
											continue
										}

										val path = if (paragraph != null) {
											arrayOf(parentActivity.getString(R.string.SettingsSearchFaq), paragraph)
										}
										else {
											arrayOf(parentActivity.getString(R.string.SettingsSearchFaq))
										}

										arrayList.add(FaqSearchResult(text, path, url))
									}

									b++
								}
							}
							else if (block is TLRPC.TL_pageBlockAnchor) {
								break
							}

							a++
						}

						faqWebPage = response2
					}

					AndroidUtilities.runOnUIThread {
						faqSearchArray.addAll(arrayList)
						messagesController.faqSearchArray = arrayList
						messagesController.faqWebPage = faqWebPage

						if (!isSearchWas) {
							notifyDataSetChanged()
						}
					}
				}

				loadingFaqPage = false
			}
		}

		override fun getItemCount(): Int {
			return if (isSearchWas) {
				searchResults.size + if (faqSearchResults.isEmpty()) 0 else 1 + faqSearchResults.size
			}
			else (if (recentSearches.isEmpty()) 0 else recentSearches.size + 1) + if (faqSearchArray.isEmpty()) 0 else faqSearchArray.size + 1
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType == 0
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			@Suppress("NAME_SHADOWING") var position = position

			when (holder.itemViewType) {
				0 -> {
					val searchCell = holder.itemView as SettingsSearchCell

					if (isSearchWas) {
						if (position < searchResults.size) {
							val result = searchResults[position]
							val prevResult = if (position > 0) searchResults[position - 1] else null

							val icon = if (prevResult != null && prevResult.iconResId == result.iconResId) {
								0
							}
							else {
								result.iconResId
							}

							searchCell.setTextAndValueAndIcon(resultNames[position], result.path, icon, position < searchResults.size - 1)
						}
						else {
							position -= searchResults.size + 1

							val result = faqSearchResults[position]

							searchCell.setTextAndValue(resultNames[position + searchResults.size], result.path, true, position < searchResults.size - 1)
						}
					}
					else {
						if (recentSearches.isNotEmpty()) {
							position--
						}

						if (position < recentSearches.size) {
							val `object` = recentSearches[position]

							if (`object` is SearchResult) {
								searchCell.setTextAndValue(`object`.searchTitle, `object`.path, false, position < recentSearches.size - 1)
							}
							else if (`object` is FaqSearchResult) {
								searchCell.setTextAndValue(`object`.title, `object`.path, true, position < recentSearches.size - 1)
							}
						}
						else {
							position -= recentSearches.size + 1

							val result = faqSearchArray[position]

							searchCell.setTextAndValue(result.title, result.path, true, position < recentSearches.size - 1)
						}
					}
				}

				1 -> {
					val sectionCell = holder.itemView as GraySectionCell
					sectionCell.setText(sectionCell.context.getString(R.string.SettingsFaqSearchTitle))
				}

				2 -> {
					val headerCell = holder.itemView as HeaderCell
					headerCell.setText(headerCell.context.getString(R.string.SettingsRecent))
				}
			}
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = when (viewType) {
				0 -> SettingsSearchCell(mContext)
				1 -> GraySectionCell(mContext)
				2 -> HeaderCell(mContext, 16)
				else -> HeaderCell(mContext, 16)
			}

			view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)

			return RecyclerListView.Holder(view)
		}

		override fun getItemViewType(position: Int): Int {
			if (isSearchWas) {
				if (position < searchResults.size) {
					return 0
				}
				else if (position == searchResults.size) {
					return 1
				}
			}
			else {
				if (position == 0) {
					return if (recentSearches.isNotEmpty()) {
						2
					}
					else {
						1
					}
				}
				else if (recentSearches.isNotEmpty() && position == recentSearches.size + 1) {
					return 1
				}
			}

			return 0
		}

		fun addRecent(`object`: Any) {
			val index = recentSearches.indexOf(`object`)

			if (index >= 0) {
				recentSearches.removeAt(index)
			}

			recentSearches.add(0, `object`)

			if (!isSearchWas) {
				notifyDataSetChanged()
			}

			if (recentSearches.size > 20) {
				recentSearches.removeAt(recentSearches.size - 1)
			}

			val toSave = mutableSetOf<String>()
			var a = 0
			val n = recentSearches.size

			while (a < n) {
				val o = recentSearches[a]

				if (o is SearchResult) {
					o.num = a
				}
				else if (o is FaqSearchResult) {
					o.num = a
				}

				toSave.add(o.toString())

				a++
			}

			MessagesController.getGlobalMainSettings().edit().putStringSet("settingsSearchRecent2", toSave).commit()
		}

		fun clearRecent() {
			recentSearches.clear()
			MessagesController.getGlobalMainSettings().edit().remove("settingsSearchRecent2").commit()
			notifyDataSetChanged()
		}

		private fun getNum(o: Any): Int {
			if (o is SearchResult) {
				return o.num
			}
			else if (o is FaqSearchResult) {
				return o.num
			}

			return 0
		}

//		fun search(text: String) {
//			lastSearchString = text
//
//			if (searchRunnable != null) {
//				Utilities.searchQueue.cancelRunnable(searchRunnable)
//				searchRunnable = null
//			}
//
//			if (TextUtils.isEmpty(text)) {
//				isSearchWas = false
//				searchResults.clear()
//				faqSearchResults.clear()
//				resultNames.clear()
//				emptyView?.stickerView?.imageReceiver?.startAnimation()
//				emptyView?.title?.text = emptyView?.context?.getString(R.string.SettingsNoRecent)
//				notifyDataSetChanged()
//				return
//			}
//
//			Utilities.searchQueue.postRunnable(Runnable {
//				val results = ArrayList<SearchResult>()
//				val faqResults = ArrayList<FaqSearchResult>()
//				val names = ArrayList<CharSequence>()
//				val searchArgs = text.split(" ").toTypedArray()
//				val translitArgs = arrayOfNulls<String>(searchArgs.size)
//
//				for (a in searchArgs.indices) {
//					translitArgs[a] = LocaleController.getInstance().getTranslitString(searchArgs[a])
//
//					if ((translitArgs[a] == searchArgs[a])) {
//						translitArgs[a] = null
//					}
//				}
//
//				for (a in searchArray.indices) {
//					val result = searchArray[a] ?: continue
//					val title = " " + result.searchTitle.lowercase()
//					var stringBuilder: SpannableStringBuilder? = null
//
//					for (i in searchArgs.indices) {
//						if (searchArgs[i].isNotEmpty()) {
//							var searchString: String? = searchArgs[i]
//							var index = title.indexOf(" $searchString")
//
//							if (index < 0 && translitArgs[i] != null) {
//								searchString = translitArgs[i]
//								index = title.indexOf(" $searchString")
//							}
//
//							if (index >= 0) {
//								if (stringBuilder == null) {
//									stringBuilder = SpannableStringBuilder(result.searchTitle)
//								}
//
//								val context = context
//
//								if (context != null) {
//									stringBuilder.setSpan(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, R.color.brand, null)), index, index + searchString!!.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
//								}
//							}
//							else {
//								break
//							}
//						}
//
//						if (stringBuilder != null && i == searchArgs.size - 1) {
//							if (result.guid == 502) {
//								var freeAccount = -1
//
//								for (b in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
//									if (!getInstance(a).isClientActivated) {
//										freeAccount = b
//										break
//									}
//								}
//
//								if (freeAccount < 0) {
//									continue
//								}
//							}
//
//							results.add(result)
//
//							names.add(stringBuilder)
//						}
//					}
//				}
//
//				if (faqWebPage != null) {
//					var a = 0
//					val n = faqSearchArray.size
//
//					while (a < n) {
//						val result = faqSearchArray[a]
//						val title = " " + result.title.lowercase()
//						var stringBuilder: SpannableStringBuilder? = null
//
//						for (i in searchArgs.indices) {
//							if (searchArgs[i].isNotEmpty()) {
//								var searchString: String? = searchArgs[i]
//								var index = title.indexOf(" $searchString")
//
//								if (index < 0 && translitArgs[i] != null) {
//									searchString = translitArgs[i]
//									index = title.indexOf(" $searchString")
//								}
//
//								if (index >= 0) {
//									if (stringBuilder == null) {
//										stringBuilder = SpannableStringBuilder(result.title)
//									}
//
//									val context = context
//
//									if (context != null) {
//										stringBuilder.setSpan(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, R.color.brand, null)), index, index + searchString!!.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
//									}
//								}
//								else {
//									break
//								}
//							}
//
//							if (stringBuilder != null && i == searchArgs.size - 1) {
//								faqResults.add(result)
//								names.add(stringBuilder)
//							}
//						}
//
//						a++
//					}
//				}
//
//				AndroidUtilities.runOnUIThread {
//					if (text != lastSearchString) {
//						return@runOnUIThread
//					}
//
//					if (!isSearchWas) {
//						emptyView?.stickerView?.imageReceiver?.startAnimation()
//						emptyView?.title?.text = emptyView?.context?.getString(R.string.SettingsNoResults)
//					}
//
//					isSearchWas = true
//					searchResults = results
//					faqSearchResults = faqResults
//					resultNames = names
//					notifyDataSetChanged()
//					emptyView?.stickerView?.imageReceiver?.startAnimation()
//				}
//			}.also {
//				searchRunnable = it
//			}, 300)
//		}

		inner class SearchResult(val guid: Int, val searchTitle: String, private val rowName: String?, pathArg1: String?, pathArg2: String?, val iconResId: Int, private val openRunnable: Runnable) {
			val path = if (pathArg1 != null && pathArg2 != null) {
				arrayOf(pathArg1, pathArg2)
			}
			else if (pathArg1 != null) {
				arrayOf(pathArg1)
			}
			else {
				arrayOf()
			}

			var num = 0

			constructor(g: Int, search: String, icon: Int, open: Runnable) : this(g, search, null, null, null, icon, open)
			constructor(g: Int, search: String, pathArg1: String?, icon: Int, open: Runnable) : this(g, search, null, pathArg1, null, icon, open)
			constructor(g: Int, search: String, row: String?, pathArg1: String?, icon: Int, open: Runnable) : this(g, search, row, pathArg1, null, icon, open)

			override fun equals(other: Any?): Boolean {
				if (other !is SearchResult) {
					return false
				}
				return guid == other.guid
			}

			override fun toString(): String {
				val data = SerializedData()
				data.writeInt32(num)
				data.writeInt32(1)
				data.writeInt32(guid)
				return Utilities.bytesToHex(data.toByteArray())
			}

			fun open() {
				openRunnable.run()
				AndroidUtilities.scrollToFragmentRow(parentLayout, rowName)
			}

			override fun hashCode(): Int {
				return guid.hashCode()
			}
		}
	}

	private inner class DiffCallback : DiffUtil.Callback() {
		var oldRowCount = 0
		var oldPositionToItem = SparseIntArray()
		var newPositionToItem = SparseIntArray()
		var oldChatParticipant = ArrayList<TLRPC.ChatParticipant>()
		var oldChatParticipantSorted = ArrayList<Int>()
		var oldMembersStartRow = 0
		var oldMembersEndRow = 0

		override fun getOldListSize(): Int {
			return oldRowCount
		}

		override fun getNewListSize(): Int {
			return rowCount
		}

		override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
			if (newItemPosition in membersStartRow until membersEndRow) {
				if (oldItemPosition in oldMembersStartRow until oldMembersEndRow) {
					val oldItem = if (oldChatParticipantSorted.isNotEmpty()) {
						oldChatParticipant[oldChatParticipantSorted[oldItemPosition - oldMembersStartRow]]
					}
					else {
						oldChatParticipant[oldItemPosition - oldMembersStartRow]
					}

					val newItem = if (sortedUsers!!.isNotEmpty()) {
						visibleChatParticipants[visibleSortedUsers[newItemPosition - membersStartRow]]
					}
					else {
						visibleChatParticipants[newItemPosition - membersStartRow]
					}

					return oldItem.user_id == newItem.user_id
				}
			}

			val oldIndex = oldPositionToItem[oldItemPosition, -1]
			val newIndex = newPositionToItem[newItemPosition, -1]

			return oldIndex == newIndex && oldIndex >= 0
		}

		override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
			return areItemsTheSame(oldItemPosition, newItemPosition)
		}

		fun fillPositions(sparseIntArray: SparseIntArray) {
			sparseIntArray.clear()
			var pointer = 0


			for (i in accountsRows.indices) {
				if (accountsRows[i] != -1) {
					put(++pointer, accountsRows[i], sparseIntArray)
				}
			}

			put(++pointer, setProfilePhotoRow, sparseIntArray)
			put(++pointer, addAccountRow, sparseIntArray)
			put(++pointer, myAccountsDividerRow, sparseIntArray)
			put(++pointer, setAvatarRow, sparseIntArray)
			put(++pointer, setAvatarSectionRow, sparseIntArray)
			put(++pointer, setUsernameRow, sparseIntArray)
			put(++pointer, shareLinkRow, sparseIntArray)
			put(++pointer, publicLinkRow, sparseIntArray)
			put(++pointer, publicLinkSectionRow, sparseIntArray)
			put(++pointer, myProfileTopDividerRow, sparseIntArray)
			put(++pointer, bioRow, sparseIntArray)
			put(++pointer, myProfileBioDividerRow, sparseIntArray)
			put(++pointer, inviteRowDividerRow, sparseIntArray)
			put(++pointer, appearanceRow, sparseIntArray)
			put(++pointer, appearanceBottomDividerRow, sparseIntArray)
			put(++pointer, myNotificationsBottomDividerRow, sparseIntArray)
			put(++pointer, setProfilePhotoDividerRow, sparseIntArray)
			put(++pointer, subscriptionsBottomDividerRow, sparseIntArray)
			put(++pointer, settingsBottomDividerRow, sparseIntArray)
			put(++pointer, referralRow, sparseIntArray)
			put(++pointer, aiChatBotRow, sparseIntArray)
			put(++pointer, myCloudRow, sparseIntArray)
			put(++pointer, walletRow, sparseIntArray)
			put(++pointer, myNotificationsRow, sparseIntArray)
			put(++pointer, purchasesRow, sparseIntArray)
			put(++pointer, subscriptionsRow, sparseIntArray)
			put(++pointer, inviteRow, sparseIntArray)
			put(++pointer, foldersRow, sparseIntArray)
			put(++pointer, settingsRow, sparseIntArray)
			put(++pointer, infoRow, sparseIntArray)
			put(++pointer, supportRow, sparseIntArray)
			put(++pointer, logoutRow, sparseIntArray)
			put(++pointer, phoneSuggestionRow, sparseIntArray)
			put(++pointer, phoneSuggestionSectionRow, sparseIntArray)
			put(++pointer, passwordSuggestionRow, sparseIntArray)
			put(++pointer, passwordSuggestionSectionRow, sparseIntArray)
			put(++pointer, settingsSectionRow, sparseIntArray)
			put(++pointer, settingsSectionRow2, sparseIntArray)
			put(++pointer, notificationRow, sparseIntArray)
			put(++pointer, languageRow, sparseIntArray)
			put(++pointer, premiumRow, sparseIntArray)
			put(++pointer, premiumSectionsRow, sparseIntArray)
			put(++pointer, privacyRow, sparseIntArray)
			put(++pointer, dataRow, sparseIntArray)
			put(++pointer, chatRow, sparseIntArray)
			put(++pointer, filtersRow, sparseIntArray)
			put(++pointer, stickersRow, sparseIntArray)
//			put(++pointer, devicesRow, sparseIntArray)
			put(++pointer, devicesSectionRow, sparseIntArray)
			put(++pointer, helpHeaderRow, sparseIntArray)
			put(++pointer, questionRow, sparseIntArray)
			put(++pointer, faqRow, sparseIntArray)
			put(++pointer, policyRow, sparseIntArray)
			put(++pointer, helpSectionCell, sparseIntArray)
			put(++pointer, debugHeaderRow, sparseIntArray)
			put(++pointer, sendLogsRow, sparseIntArray)
			put(++pointer, sendLastLogsRow, sparseIntArray)
			put(++pointer, clearLogsRow, sparseIntArray)
			put(++pointer, versionRow, sparseIntArray)
			put(++pointer, emptyRow, sparseIntArray)
			put(++pointer, bottomPaddingRow, sparseIntArray)
			put(++pointer, locationRow, sparseIntArray)
			put(++pointer, userInfoRow, sparseIntArray)
			put(++pointer, channelInfoRow, sparseIntArray)
			put(++pointer, userAboutHeaderRow, sparseIntArray)
			put(++pointer, usernameRow, sparseIntArray)
			put(++pointer, notificationsDividerRow, sparseIntArray)
			put(++pointer, reportDividerRow, sparseIntArray)
			put(++pointer, notificationsRow, sparseIntArray)
			put(++pointer, infoSectionRow, sparseIntArray)
			put(++pointer, sendMessageRow, sparseIntArray)
			put(++pointer, reportRow, sparseIntArray)
			put(++pointer, reportReactionRow, sparseIntArray)
			put(++pointer, settingsTimerRow, sparseIntArray)
			put(++pointer, settingsKeyRow, sparseIntArray)
			put(++pointer, secretSettingsSectionRow, sparseIntArray)
			put(++pointer, membersHeaderRow, sparseIntArray)
			put(++pointer, addMemberRow, sparseIntArray)
			put(++pointer, subscriptionBeginRow, sparseIntArray)
			put(++pointer, subscriptionExpireRow, sparseIntArray)
			put(++pointer, subscriptionCostRow, sparseIntArray)
			put(++pointer, subscriptionCostSectionRow, sparseIntArray)
			put(++pointer, subscribersRow, sparseIntArray)
			put(++pointer, subscribersRequestsRow, sparseIntArray)
			put(++pointer, administratorsRow, sparseIntArray)
			put(++pointer, blockedUsersRow, sparseIntArray)
			put(++pointer, membersSectionRow, sparseIntArray)
			put(++pointer, sharedMediaRow, sparseIntArray)
			put(++pointer, unblockRow, sparseIntArray)
			put(++pointer, addToGroupButtonRow, sparseIntArray)
			put(++pointer, addToGroupInfoRow, sparseIntArray)
			put(++pointer, joinRow, sparseIntArray)
			put(++pointer, lastSectionRow, sparseIntArray)
		}

		private fun put(id: Int, position: Int, sparseIntArray: SparseIntArray) {
			if (position >= 0) {
				sparseIntArray.put(position, id)
			}
		}
	}

	companion object {
		private const val VIEW_TYPE_HEADER = 1
		private const val VIEW_TYPE_TEXT_DETAIL = 2
		private const val VIEW_TYPE_ABOUT_LINK = 3
		private const val VIEW_TYPE_TEXT = 4
		private const val VIEW_TYPE_DIVIDER = 5
		private const val VIEW_TYPE_NOTIFICATIONS_CHECK = 6
		private const val VIEW_TYPE_SHADOW = 7
		private const val VIEW_TYPE_USER = 8
		private const val VIEW_TYPE_EMPTY = 11
		private const val VIEW_TYPE_BOTTOM_PADDING = 12
		const val VIEW_TYPE_SHARED_MEDIA = 13
		private const val VIEW_TYPE_VERSION = 14
		private const val VIEW_TYPE_SUGGESTION = 15
		private const val VIEW_TYPE_ADDTOGROUP_INFO = 17
		private const val VIEW_TYPE_PREMIUM_TEXT_CELL = 18
		private const val VIEW_TYPE_PROFILE_SECTION = 19
		private const val VIEW_TYPE_SMALL_EMPTY = 20
		private const val VIEW_TYPE_SUPPORT = 21
		private const val VIEW_TYPE_LOGOUT = 22
		private const val VIEW_TYPE_SHARE_LINK = 23
		private const val VIEW_TYPE_PUBLIC_LINK = 24
		private const val VIEW_TYPE_USER_ACCOUNT = 25
		private const val VIEW_TYPE_ADD_ACCOUNT = 26
		private const val VIEW_TYPE_SET_PROFILE_PHOTO = 27

		// private const val PHONE_OPTION_CALL = 0
		// private const val PHONE_OPTION_COPY = 1
		// private const val PHONE_OPTION_TELEGRAM_CALL = 2
		// private const val PHONE_OPTION_TELEGRAM_VIDEO_CALL = 3
		private const val add_contact = 1
		private const val block_contact = 2
		private const val share_contact = 3
		private const val edit_contact = 4
		private const val delete_contact = 5
		private const val leave_group = 7
		private const val invite_to_group = 9
		private const val share = 10
		private const val edit_channel = 12
		private const val add_shortcut = 14
		private const val call_item = 15
		private const val video_call_item = 16
		private const val search_members = 17
		private const val add_member = 18
		private const val statistics = 19
		private const val start_secret_chat = 20
		private const val gallery_menu_save = 21
		private const val view_discussion = 22
		private const val edit_name = 30
		private const val logout = 31

		// private const val search_button = 32
		private const val set_as_main = 33
		private const val edit_avatar = 34
		private const val delete_avatar = 35
		private const val add_photo = 36
		private const val qr_button = 37
		private const val gift_premium = 38

		private const val clear_history = 39

		private const val avatarSide = 42f
	}

	private fun getFilteredAccountsRows(): List<Int> {
		return accountsRows.filterNot { it == -1 }
	}

	private fun getAccountIndex(row: Int): Int {
		val filteredRows = getFilteredAccountsRows()
		val accounts = mutableListOf<Int>()

		for (i in filteredRows.min()..filteredRows.max()) {
			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				val u = AccountInstance.getInstance(a).userConfig.getCurrentUser()

				if (u != null) {
					accounts.add(a)
				}
			}
		}

		return accounts[row]
	}

	private fun switchAccount(row: Int) {
		val account = getAccountIndex(row)
		(parentActivity as? LaunchActivity)?.switchToAccount(account)
	}

	override fun changeBigAvatar() {
		avatarsViewPager?.addUploadingImage(ImageLocation.getForLocal(avatarBig).also { uploadingImageLocation = it }, ImageLocation.getForLocal(avatar))
	}

}
