/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextPaint
import android.text.TextUtils
import android.util.Property
import android.util.StateSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScrollerCustom
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.BuildVars
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ChatObject.isSubscriptionChannel
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.FilesMigrationService
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject.getUserName
import org.telegram.messenger.UserObject.isUserSelf
import org.telegram.messenger.Utilities
import org.telegram.messenger.XiaomiUtilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.unsubscribeRequest
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.DialogsAdapter
import org.telegram.ui.Adapters.DialogsAdapter.LastEmptyView
import org.telegram.ui.Adapters.DialogsSearchAdapter.DialogsSearchAdapterDelegate
import org.telegram.ui.Adapters.FiltersView
import org.telegram.ui.Adapters.FiltersView.DateData
import org.telegram.ui.Adapters.FiltersView.MediaFilterData
import org.telegram.ui.Cells.AccountSelectCell
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Cells.DrawerProfileCell.AnimatedStatusView
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.HintDialogCell
import org.telegram.ui.Cells.ProfileSearchCell
import org.telegram.ui.Cells.UserCell
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiDrawable.WrapSizeDrawable
import org.telegram.ui.Components.AnimationProperties
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.BlurredRecyclerView
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.ChatActivityEnterView.ChatActivityEnterViewDelegate
import org.telegram.ui.Components.ChatAvatarContainer
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.DialogsItemAnimator
import org.telegram.ui.Components.FilterTabsView
import org.telegram.ui.Components.FilterTabsView.FilterTabsViewDelegate
import org.telegram.ui.Components.FiltersListBottomSheet
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.FragmentContextView
import org.telegram.ui.Components.JoinGroupAlert
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.LayoutHelper.createScroll
import org.telegram.ui.Components.NumberTextView
import org.telegram.ui.Components.PacmanAnimation
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet
import org.telegram.ui.Components.ProxyDrawable
import org.telegram.ui.Components.PullForegroundDrawable
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.Reactions.VisibleReaction
import org.telegram.ui.Components.RecyclerAnimationScrollHelper
import org.telegram.ui.Components.RecyclerItemsEnterAnimator
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.OnItemLongClickListenerExtended
import org.telegram.ui.Components.SearchViewPager
import org.telegram.ui.Components.SearchViewPager.ChatPreviewDelegate
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.StickersAlert
import org.telegram.ui.Components.SwipeGestureSettingsView
import org.telegram.ui.Components.UndoView
import org.telegram.ui.Components.ViewPagerFixed.TabsView
import org.telegram.ui.SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow
import org.telegram.ui.aispace.AiSpaceFragment
import org.telegram.ui.group.GroupCreateFinalActivity
import org.telegram.ui.group.GroupCreateFinalActivity.GroupCreateFinalActivityDelegate
import org.telegram.ui.quicklinks.QuickLinksFragment
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@SuppressLint("NotifyDataSetChanged")
open class DialogsActivity(args: Bundle?) : BaseFragment(args), NotificationCenterDelegate {
	private val undoView = arrayOfNulls<UndoView>(2)
	private val scrimViewLocation = IntArray(2)
	private val movingDialogFilters = ArrayList<MessagesController.DialogFilter>()
	private val actionBarDefaultPaint = Paint()
	private val actionModeViews = ArrayList<View?>()
	private val commentViewAnimated = false
	private val rect = RectF()
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val floatingInterpolator = AccelerateDecelerateInterpolator()
	private val selectedDialogs = ArrayList<Long>()
	private var forYouTab = 0

	@JvmField
	var notify = true

	var databaseMigrationHint: View? = null
	var slideFragmentProgress = 1f
	var isSlideBackTransition = false
	var isDrawerTransition = false
	private var slideBackTransitionAnimator: ValueAnimator? = null
	private var canShowFilterTabsView = false
	private var filterTabsViewIsVisible = false
	private var initialSearchType = -1
	private var searchTabsView: TabsView? = null
	private var contactsAlpha = 1f
	private var contactsAlphaAnimator: ValueAnimator? = null
	private var viewPages: Array<ViewPage?>? = null
	private var filtersView: FiltersView? = null
	private var passcodeItem: ActionBarMenuItem? = null
	private var downloadsItem: ActionBarMenuItem? = null
	private var passcodeItemVisible = false
	private var downloadsItemVisible = false
	private var proxyItem: ActionBarMenuItem? = null
	private var proxyItemVisible = false
	private var searchItem: ActionBarMenuItem? = null
	private var quickLinks: ActionBarMenuItem? = null
	private var aiBot: ActionBarMenuItem? = null
	private var doneItem: ActionBarMenuItem? = null
	private var proxyDrawable: ProxyDrawable? = null
	private var floatingButton: ImageView? = null
	private var floatingProgressView: RadialProgressView? = null
	private var floatingButtonContainer: FrameLayout? = null
	private var avatarContainer: ChatAvatarContainer? = null
	private var filterTabsView: FilterTabsView? = null
	private var askingForPermissions = false
	private var passcodeDrawable: RLottieDrawable? = null
	private var searchViewPager: SearchViewPager? = null
	private var blurredView: View? = null
	private var scrimPaint: Paint? = null
	private var scrimViewBackground: Drawable? = null
	private var scrimView: View? = null
	private var scrimViewSelected = false
	private var scrimViewAppearing = false
	private var scrimAnimatorSet: AnimatorSet? = null
	private var scrimPopupWindow: ActionBarPopupWindow? = null
	private var scrimPopupWindowItems: Array<ActionBarMenuSubItem?>? = null
	private var selectAnimatedEmojiDialog: SelectAnimatedEmojiDialogWindow? = null
	private var initialDialogsType = 0
	private var checkingImportDialog = false
	private var messagesCount = 0
	private var hasPoll = 0
	private var hasInvoice = false
	private var pacmanAnimation: PacmanAnimation? = null
	private var slidingView: DialogCell? = null
	private var movingView: DialogCell? = null
	private var allowMoving = false
	private var movingWas = false
	private var waitingForScrollFinished = false
	private var allowSwipeDuringCurrentTouch = false
	private var updatePullAfterScroll = false
	private var backDrawable: BackDrawable? = null
	private var selectedDialogsCountTextView: NumberTextView? = null
	private var deleteItem: ActionBarMenuItem? = null
	private var pinItem: ActionBarMenuItem? = null
	private var muteItem: ActionBarMenuItem? = null
	private var archive2Item: ActionBarMenuItem? = null
	private var pin2Item: ActionBarMenuSubItem? = null
	private var addToFolderItem: ActionBarMenuSubItem? = null
	private var removeFromFolderItem: ActionBarMenuSubItem? = null
	private var archiveItem: ActionBarMenuSubItem? = null
	private var clearItem: ActionBarMenuSubItem? = null
	private var readItem: ActionBarMenuSubItem? = null
	private var blockItem: ActionBarMenuSubItem? = null
	private var additionalFloatingTranslation = 0f
	private var floatingButtonTranslation = 0f
	private var floatingButtonHideProgress = 0f
	private var searchAnimator: AnimatorSet? = null
	private var tabsAlphaAnimator: Animator? = null
	private var searchAnimationProgress = 0f
	private var searchAnimationTabsDelayedCrossfade = false
	private var commentView: ChatActivityEnterView? = null
	private var commentViewBg: View? = null
	private var writeButton: Array<ImageView?>? = null
	private var writeButtonContainer: FrameLayout? = null
	private var selectedCountView: View? = null
	private var switchItem: ActionBarMenuItem? = null
	private var fragmentLocationContextView: FragmentContextView? = null
	private var fragmentContextView: FragmentContextView? = null
	private var frozenDialogsList: ArrayList<TLRPC.Dialog>? = null
	private var dialogsListFrozen = false
	private var dialogRemoveFinished = 0
	private var dialogInsertFinished = 0
	private var dialogChangeFinished = 0
	private var permissionDialog: AlertDialog? = null
	private var closeSearchFieldOnHide = false
	private var searchDialogId: Long = 0
	private var searchObject: TLObject? = null
	private var prevPosition = 0
	private var prevTop = 0
	private var scrollUpdated = false
	private var floatingHidden = false
	private var floatingForceVisible = false
	private var floatingProgressVisible = false
	private var floatingProgressAnimator: AnimatorSet? = null
	private var checkPermission = true
	private var currentConnectionState = 0
	private var disableActionBarScrolling = false
	private var selectAlertString: String? = null
	private var selectAlertStringGroup: String? = null
	private var addToGroupAlertString: String? = null
	private var resetDelegate = true
	private var searching = false
	private var searchWas = false
	private var onlySelect = false
	private var searchString: String? = null
	private var initialSearchString: String? = null
	private var openedDialogId: Long = 0
	private var cantSendToChannels = false
	private var allowSwitchAccount = false
	private var checkCanWrite = false
	private var afterSignup = false
	private var showSetPasswordConfirm = false
	private var allowGroups = false
	private var allowChannels = false
	private var allowUsers = false
	private var allowBots = false
	private var closeFragment = false
	private var delegate: DialogsActivityDelegate? = null
	private var canReadCount = 0
	private var canPinCount = 0
	private var canMuteCount = 0
	private var canUnmuteCount = 0
	private var canClearCacheCount = 0
	private var canReportSpamCount = 0
	private var canUnarchiveCount = 0
	private var canDeletePsaSelected = false
	private var topPadding = 0
	private var lastMeasuredTopPadding = 0
	private var folderId = 0
	private var startArchivePullingTime: Long = 0
	private var scrollingManually = false
	private var canShowHiddenArchive = false
	private var tabsAnimation: AnimatorSet? = null
	private var tabsAnimationInProgress = false
	private var animatingForward = false
	private var additionalOffset = 0f
	private var backAnimation = false
	private var maximumVelocity = 0
	private var startedTracking = false
	private var maybeStartTracking = false
	private var animationIndex = -1
	private var searchIsShowed = false
	private var searchWasFullyShowed = false
	private var whiteActionBar = false
	private var searchFiltersWasShowed = false
	private var progressToActionMode = 0f
	private var actionBarColorAnimator: ValueAnimator? = null
	private var filtersTabAnimator: ValueAnimator? = null
	private var filterTabsProgress = 0f
	private var filterTabsMoveFrom = 0f
	private var tabsYOffset = 0f
	private var scrollAdditionalOffset = 0f
	private var debugLastUpdateAction = -1
	private var slowedReloadAfterDialogClick = false
	private var statusDrawable: SwapAnimatedEmojiDrawable? = null
	private var animatedStatusView: AnimatedStatusView? = null
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	private val SCROLL_Y: Property<DialogsActivity, Float> = object : AnimationProperties.FloatProperty<DialogsActivity>("animationValue") {
		override fun setValue(`object`: DialogsActivity, value: Float) {
			`object`.setScrollY(value)
		}

		override operator fun get(`object`: DialogsActivity): Float {
			return actionBar?.translationY ?: 0f
		}
	}

	private var premiumStar: Drawable? = null
	private var commentViewPreviousTop = -1
	private var keyboardAnimator: ValueAnimator? = null
	private var commentViewIgnoreTopUpdate = false
	private var scrollBarVisible = true
	private var doneItemAnimator: AnimatorSet? = null
	private var isNextButton = false
	private var commentViewAnimator: AnimatorSet? = null
	private var showingSuggestion: String? = null
	private var sendPopupWindow: ActionBarPopupWindow? = null
	private var shouldShowBottomNavigationPanel = false

	init {
		shouldShowBottomNavigationPanel = args?.getBoolean(shouldShowBottomNavigationPanelKey, true) ?: true
	}

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()

		getArguments()?.let { arguments ->
			onlySelect = arguments.getBoolean("onlySelect", false)
			cantSendToChannels = arguments.getBoolean("cantSendToChannels", false)
			initialDialogsType = arguments.getInt("dialogsType", 0)
			selectAlertString = arguments.getString("selectAlertString")
			selectAlertStringGroup = arguments.getString("selectAlertStringGroup")
			addToGroupAlertString = arguments.getString("addToGroupAlertString")
			allowSwitchAccount = arguments.getBoolean("allowSwitchAccount")
			checkCanWrite = arguments.getBoolean("checkCanWrite", true)
			afterSignup = arguments.getBoolean("afterSignup", false)
			folderId = arguments.getInt("folderId", 0)
			resetDelegate = arguments.getBoolean("resetDelegate", true)
			messagesCount = arguments.getInt("messagesCount", 0)
			hasPoll = arguments.getInt("hasPoll", 0)
			hasInvoice = arguments.getBoolean("hasInvoice", false)
			showSetPasswordConfirm = arguments.getBoolean("showSetPasswordConfirm", showSetPasswordConfirm)
			allowGroups = arguments.getBoolean("allowGroups", true)
			allowChannels = arguments.getBoolean("allowChannels", true)
			allowUsers = arguments.getBoolean("allowUsers", true)
			allowBots = arguments.getBoolean("allowBots", true)
			closeFragment = arguments.getBoolean("closeFragment", true)
		}

		if (initialDialogsType == 0) {
			SharedConfig.loadProxyList()
		}

		if (searchString == null) {
			currentConnectionState = connectionsManager.getConnectionState()

			notificationCenter.addObserver(this, NotificationCenter.dialogsNeedReload)

			NotificationCenter.globalInstance.addObserver(this, NotificationCenter.emojiLoaded)

			if (!onlySelect) {
				NotificationCenter.globalInstance.let {
					it.addObserver(this, NotificationCenter.closeSearchByActiveAction)
					it.addObserver(this, NotificationCenter.proxySettingsChanged)
				}

				notificationCenter.addObserver(this, NotificationCenter.filterSettingsUpdated)
				notificationCenter.addObserver(this, NotificationCenter.dialogFiltersUpdated)
				notificationCenter.addObserver(this, NotificationCenter.dialogsUnreadCounterChanged)
			}

			notificationCenter.addObserver(this, NotificationCenter.updateInterfaces)
			notificationCenter.addObserver(this, NotificationCenter.encryptedChatUpdated)
			notificationCenter.addObserver(this, NotificationCenter.contactsDidLoad)
			notificationCenter.addObserver(this, NotificationCenter.appDidLogout)
			notificationCenter.addObserver(this, NotificationCenter.chatDidCreated)
			notificationCenter.addObserver(this, NotificationCenter.openedChatChanged)
			notificationCenter.addObserver(this, NotificationCenter.notificationsSettingsUpdated)
			notificationCenter.addObserver(this, NotificationCenter.messageReceivedByAck)
			notificationCenter.addObserver(this, NotificationCenter.messageReceivedByServer)
			notificationCenter.addObserver(this, NotificationCenter.messageSendError)
			notificationCenter.addObserver(this, NotificationCenter.needReloadRecentDialogsSearch)
			notificationCenter.addObserver(this, NotificationCenter.replyMessagesDidLoad)
			notificationCenter.addObserver(this, NotificationCenter.reloadHints)
			notificationCenter.addObserver(this, NotificationCenter.didUpdateConnectionState)
			notificationCenter.addObserver(this, NotificationCenter.onDownloadingFilesChanged)
			notificationCenter.addObserver(this, NotificationCenter.needDeleteDialog)
			notificationCenter.addObserver(this, NotificationCenter.folderBecomeEmpty)
			notificationCenter.addObserver(this, NotificationCenter.newSuggestionsAvailable)
			notificationCenter.addObserver(this, NotificationCenter.fileLoaded)
			notificationCenter.addObserver(this, NotificationCenter.fileLoadFailed)
			notificationCenter.addObserver(this, NotificationCenter.fileLoadProgressChanged)
			notificationCenter.addObserver(this, NotificationCenter.dialogsUnreadReactionsCounterChanged)
			notificationCenter.addObserver(this, NotificationCenter.forceImportContactsStart)
			notificationCenter.addObserver(this, NotificationCenter.userEmojiStatusUpdated)
			notificationCenter.addObserver(this, NotificationCenter.currentUserPremiumStatusChanged)

			NotificationCenter.globalInstance.addObserver(this, NotificationCenter.didSetPasscode)
		}

		notificationCenter.addObserver(this, NotificationCenter.messagesDeleted)
		notificationCenter.addObserver(this, NotificationCenter.onDatabaseMigration)
		notificationCenter.addObserver(this, NotificationCenter.onDatabaseOpened)
		notificationCenter.addObserver(this, NotificationCenter.didClearDatabase)

		if (folderId == 0) {
			loadDialogs(accountInstance, force = true)
		}

		messagesController.loadPinnedDialogs(folderId)

		if (databaseMigrationHint != null && !messagesStorage.isDatabaseMigrationInProgress) {
			val localView = databaseMigrationHint
			(localView?.parent as? ViewGroup)?.removeView(localView)
			databaseMigrationHint = null
		}

		loadInviteLinks()

		return true
	}

	fun updateStatus(user: User?, animated: Boolean) {
		val context = context ?: return

		if (statusDrawable == null || actionBar == null) {
			return
		}

		if (user != null && user.emoji_status is TLRPC.TL_emojiStatusUntil && (user.emoji_status as TLRPC.TL_emojiStatusUntil).until > (System.currentTimeMillis() / 1000).toInt()) {
			statusDrawable?.set((user.emoji_status as TLRPC.TL_emojiStatusUntil).document_id, animated)

			actionBar?.setRightDrawableOnClick {
				showSelectStatusDialog()
			}

			SelectAnimatedEmojiDialog.preload(currentAccount)
		}
		else if (user != null && user.emoji_status is TLRPC.TL_emojiStatus) {
			statusDrawable?.set((user.emoji_status as TLRPC.TL_emojiStatus).document_id, animated)

			actionBar?.setRightDrawableOnClick {
				showSelectStatusDialog()
			}

			SelectAnimatedEmojiDialog.preload(currentAccount)
		}
		else if (user != null && messagesController.isPremiumUser(user)) {
			if (premiumStar == null) {
				premiumStar = ResourcesCompat.getDrawable(context.resources, R.drawable.msg_premium_liststar, null)?.mutate()

				premiumStar = object : WrapSizeDrawable(premiumStar, AndroidUtilities.dp(18f), AndroidUtilities.dp(18f)) {
					override fun draw(canvas: Canvas) {
						canvas.save()
						canvas.translate(AndroidUtilities.dp(-2f).toFloat(), AndroidUtilities.dp(1f).toFloat())
						super.draw(canvas)
						canvas.restore()
					}
				}
			}

			premiumStar?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.totals_blue_text), PorterDuff.Mode.SRC_IN)
			statusDrawable?.set(premiumStar, animated)

			actionBar?.setRightDrawableOnClick {
				showSelectStatusDialog()
			}

			SelectAnimatedEmojiDialog.preload(currentAccount)
		}
		else {
			statusDrawable?.set(null as Drawable?, animated)
			actionBar?.setRightDrawableOnClick(null)
		}

		statusDrawable?.color = context.getColor(R.color.totals_blue_text)
		animatedStatusView?.setColor(context.getColor(R.color.totals_blue_text))

		if (selectAnimatedEmojiDialog?.contentView is SelectAnimatedEmojiDialog) {
			val textView = actionBar?.titleTextView
			(selectAnimatedEmojiDialog?.contentView as? SelectAnimatedEmojiDialog)?.setScrimDrawable(if (textView != null && textView.rightDrawable === statusDrawable) statusDrawable else null, textView)
		}
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		if (searchString == null) {
			notificationCenter.removeObserver(this, NotificationCenter.dialogsNeedReload)

			NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.emojiLoaded)

			if (!onlySelect) {
				NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.closeSearchByActiveAction)
				NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.proxySettingsChanged)

				notificationCenter.removeObserver(this, NotificationCenter.filterSettingsUpdated)
				notificationCenter.removeObserver(this, NotificationCenter.dialogFiltersUpdated)
				notificationCenter.removeObserver(this, NotificationCenter.dialogsUnreadCounterChanged)
			}

			if (ioScope.isActive) {
				ioScope.cancel()
			}

			if (mainScope.isActive) {
				mainScope.cancel()
			}

			notificationCenter.removeObserver(this, NotificationCenter.updateInterfaces)
			notificationCenter.removeObserver(this, NotificationCenter.encryptedChatUpdated)
			notificationCenter.removeObserver(this, NotificationCenter.contactsDidLoad)
			notificationCenter.removeObserver(this, NotificationCenter.appDidLogout)
			notificationCenter.removeObserver(this, NotificationCenter.chatDidCreated)
			notificationCenter.removeObserver(this, NotificationCenter.openedChatChanged)
			notificationCenter.removeObserver(this, NotificationCenter.notificationsSettingsUpdated)
			notificationCenter.removeObserver(this, NotificationCenter.messageReceivedByAck)
			notificationCenter.removeObserver(this, NotificationCenter.messageReceivedByServer)
			notificationCenter.removeObserver(this, NotificationCenter.messageSendError)
			notificationCenter.removeObserver(this, NotificationCenter.needReloadRecentDialogsSearch)
			notificationCenter.removeObserver(this, NotificationCenter.replyMessagesDidLoad)
			notificationCenter.removeObserver(this, NotificationCenter.reloadHints)
			notificationCenter.removeObserver(this, NotificationCenter.didUpdateConnectionState)
			notificationCenter.removeObserver(this, NotificationCenter.onDownloadingFilesChanged)
			notificationCenter.removeObserver(this, NotificationCenter.needDeleteDialog)
			notificationCenter.removeObserver(this, NotificationCenter.folderBecomeEmpty)
			notificationCenter.removeObserver(this, NotificationCenter.newSuggestionsAvailable)
			notificationCenter.removeObserver(this, NotificationCenter.fileLoaded)
			notificationCenter.removeObserver(this, NotificationCenter.fileLoadFailed)
			notificationCenter.removeObserver(this, NotificationCenter.fileLoadProgressChanged)
			notificationCenter.removeObserver(this, NotificationCenter.dialogsUnreadReactionsCounterChanged)
			notificationCenter.removeObserver(this, NotificationCenter.forceImportContactsStart)
			notificationCenter.removeObserver(this, NotificationCenter.userEmojiStatusUpdated)
			notificationCenter.removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged)

			NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.didSetPasscode)
		}

		notificationCenter.removeObserver(this, NotificationCenter.userInfoDidLoad)
		notificationCenter.removeObserver(this, NotificationCenter.messagesDeleted)
		notificationCenter.removeObserver(this, NotificationCenter.onDatabaseMigration)
		notificationCenter.removeObserver(this, NotificationCenter.onDatabaseOpened)
		notificationCenter.removeObserver(this, NotificationCenter.didClearDatabase)

		commentView?.onDestroy()

		undoView[0]?.hide(true, 0)

		notificationCenter.onAnimationFinish(animationIndex)

		delegate = null

		SuggestClearDatabaseBottomSheet.dismissDialog()
	}

	override fun createActionBar(context: Context): ActionBar {
		val actionBar = object : ActionBar(context) {
			override fun setTranslationY(translationY: Float) {
				if (translationY != getTranslationY() && fragmentView != null) {
					fragmentView?.invalidate()
				}

				super.setTranslationY(translationY)
			}

			override fun shouldClipChild(child: View): Boolean {
				return super.shouldClipChild(child) || child === doneItem
			}

			override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
				return if (inPreviewMode && avatarContainer != null && child !== avatarContainer) {
					false
				}
				else {
					super.drawChild(canvas, child, drawingTime)
				}
			}

			override fun setTitleOverlayText(title: String?, titleId: Int, action: Runnable?) {
				super.setTitleOverlayText(title, titleId, action)

				if (selectAnimatedEmojiDialog?.contentView is SelectAnimatedEmojiDialog) {
					val textView = titleTextView
					(selectAnimatedEmojiDialog?.contentView as? SelectAnimatedEmojiDialog)?.setScrimDrawable(if (textView != null && textView.rightDrawable === statusDrawable) statusDrawable else null, textView)
				}
			}
		}

		if (inPreviewMode || AndroidUtilities.isTablet() && folderId != 0) {
			actionBar.occupyStatusBar = false
		}

		return actionBar
	}

	override fun createView(context: Context): View? {
		searching = false
		searchWas = false
		pacmanAnimation = null
		selectedDialogs.clear()
		actionBar?.shouldDestroyBackButtonOnCollapse = true
		maximumVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

		AndroidUtilities.runOnUIThread {
			Theme.createChatResources(context, false)
		}

		val menu = actionBar!!.createMenu()

		if (!onlySelect && searchString == null && folderId == 0) {
			doneItem = ActionBarMenuItem(context, null, context.getColor(R.color.light_background), context.getColor(R.color.brand), true)
			doneItem?.setText(context.getString(R.string.Done).uppercase())

			actionBar?.addView(doneItem, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.RIGHT, 0f, 0f, 10f, 0f))

			doneItem?.setOnClickListener {
				filterTabsView?.setIsEditing(false)
				showDoneItem(false)
			}

			doneItem?.alpha = 0.0f
			doneItem?.visibility = View.GONE

			proxyDrawable = ProxyDrawable(context)

			proxyItem = menu.addItem(2, proxyDrawable)
			proxyItem?.contentDescription = context.getString(R.string.ProxySettings)

			passcodeDrawable = RLottieDrawable(R.raw.passcode_lock_close, "passcode_lock_close", AndroidUtilities.dp(28f), AndroidUtilities.dp(28f), true, null)

			passcodeItem = menu.addItem(1, passcodeDrawable)
			passcodeItem?.contentDescription = context.getString(R.string.AccDescrPasscodeLock)

			downloadsItem = menu.addItem(3, ColorDrawable(Color.TRANSPARENT))
			downloadsItem?.addView(DownloadProgressIcon(currentAccount, context))
			downloadsItem?.contentDescription = context.getString(R.string.DownloadsTabs)
			downloadsItem?.visibility = View.GONE

			updatePasscodeButton()

			updateProxyButton(animated = false, force = false)
		}

		searchItem = menu.addItem(0, R.drawable.icon_search_menu).setIsSearchField(value = true, wrapInScrollView = true).setActionBarMenuItemSearchListener(object : ActionBarMenuItemSearchListener() {
			override fun onSearchExpand() {
				searching = true

				switchItem?.visibility = View.GONE

				if (proxyItemVisible) {
					proxyItem?.visibility = View.GONE
				}

				if (downloadsItemVisible) {
					downloadsItem?.visibility = View.GONE
				}

				quickLinks?.visibility = View.GONE
				aiBot?.visibility = View.GONE

				viewPages?.firstOrNull()?.let {
					if (searchString != null) {
						it.listView?.hide()
						searchViewPager?.searchListView?.show()
					}

					if (!onlySelect) {
						floatingButtonContainer?.visibility = View.GONE
					}
				}

				setScrollY(0f)
				updatePasscodeButton()
				updateProxyButton(animated = false, force = false)
				actionBar?.setBackButtonContentDescription(context.getString(R.string.AccDescrGoBack))

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.needCheckSystemBarColors)

				(fragmentView as? SizeNotifierFrameLayout)?.invalidateBlur()

				showSearch()
			}

			override fun canCollapseSearch(): Boolean {
				switchItem?.visibility = View.VISIBLE

				if (proxyItemVisible) {
					proxyItem?.visibility = View.VISIBLE
				}

				if (downloadsItemVisible) {
					downloadsItem?.visibility = View.VISIBLE
				}

				quickLinks?.visibility = View.VISIBLE
				aiBot?.visibility = View.VISIBLE

				if (searchString != null) {
					finishFragment()
					return false
				}

				return true
			}

			override fun onSearchCollapse() {
				searching = false
				searchWas = false

				viewPages?.firstOrNull()?.let {
					it.listView?.setEmptyView(if (folderId == 0) it.progressView else null)

					if (!onlySelect) {
						floatingButtonContainer?.visibility = View.VISIBLE
						floatingHidden = true
						floatingButtonTranslation = AndroidUtilities.dp(100f).toFloat()
						floatingButtonHideProgress = 1f
						updateFloatingButtonOffset()
					}

					showSearch(show = false, startFromDownloads = false, animated = true)
				}

				updateProxyButton(animated = false, force = false)
				updatePasscodeButton()

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.needCheckSystemBarColors, true)

				(fragmentView as? SizeNotifierFrameLayout)?.invalidateBlur()
			}

			private fun showSearch() {
				searchWas = true

				if (!searchIsShowed) {
					showSearch(show = true, startFromDownloads = false, animated = true)
				}
			}

			override fun onTextChanged(editText: EditText) {
				val text = editText.text?.toString()

				if (!text.isNullOrEmpty() || searchViewPager?.dialogsSearchAdapter != null && searchViewPager?.dialogsSearchAdapter?.hasRecentSearch() == true || searchFiltersWasShowed) {
					showSearch()
				}

				searchViewPager?.onTextChanged(text)
			}

			override fun onSearchFilterCleared(filterData: MediaFilterData) {
				if (!searchIsShowed) {
					return
				}

				searchViewPager?.removeSearchFilter(filterData)
				searchViewPager?.onTextChanged(searchItem?.searchField?.text?.toString())
				updateFiltersView(null, null, archive = false, animated = true)
			}

			override fun canToggleSearch(): Boolean {
				return !actionBar!!.isActionModeShowed && databaseMigrationHint == null
			}
		})

		quickLinks = menu.addItem(4, ResourcesCompat.getDrawable(context.resources, R.drawable.ic_quick_links, null))

		quickLinks?.setOnClickListener {
			presentFragment(QuickLinksFragment())
		}

		aiBot = menu.addItem(5, ResourcesCompat.getDrawable(context.resources, R.drawable.ic_avatar_ai_bot, null))

		aiBot?.setOnClickListener {
			presentFragment(AiSpaceFragment())
		}

		if (initialDialogsType == 2 || initialDialogsType == DIALOGS_TYPE_START_ATTACH_BOT) {
			searchItem?.visibility = View.GONE
		}

		searchItem?.setSearchFieldHint(context.getString(R.string.ChatsSearchHint))
		searchItem?.contentDescription = context.getString(R.string.ChatsSearchHint)

		if (onlySelect) {
			actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
			actionBar?.setBackButtonContentDescription(context.getString(R.string.back))

			if (initialDialogsType == 3 && selectAlertString == null) {
				actionBar?.setTitle(context.getString(R.string.ForwardTo))
			}
			else if (initialDialogsType == 10) {
				actionBar?.setTitle(context.getString(R.string.SelectChats))
			}
			else {
				actionBar?.setTitle(context.getString(R.string.SelectChat))
			}

			actionBar?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		}
		else {
			if (searchString != null || folderId != 0) {
				backDrawable = BackDrawable(false)
			}
			else {
				actionBar?.setBackButtonContentDescription(null)
			}

			if (folderId != 0) {
				actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
				actionBar?.setBackButtonContentDescription(context.getString(R.string.back))
				actionBar?.setTitle(context.getString(R.string.ArchivedChats))
				actionBar?.shouldDestroyBackButtonOnCollapse = false
			}
			else {
				statusDrawable = SwapAnimatedEmojiDrawable(null, AndroidUtilities.dp(26f))
				statusDrawable?.center = true

				actionBar?.setTitle(context.getString(R.string.chats), statusDrawable)

				updateStatus(userConfig.getCurrentUser(), false)
			}

			if (folderId == 0) {
				actionBar?.setSupportsHolidayImage(true)
			}
		}

		if (!onlySelect) {
			actionBar?.setAddToContainer(false)
			actionBar?.castShadows = false
			actionBar?.setClipContent(true)
		}

		actionBar?.setTitleActionRunnable {
			if (initialDialogsType != 10) {
				hideFloatingButton(false)
			}

			scrollToTop()
		}

		if (initialDialogsType == 0 && folderId == 0 && !onlySelect && TextUtils.isEmpty(searchString)) {
			scrimPaint = object : Paint() {
				override fun setAlpha(a: Int) {
					super.setAlpha(a)
					fragmentView?.invalidate()
				}
			}

			filterTabsView = object : FilterTabsView(context) {
				override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
					parent.requestDisallowInterceptTouchEvent(true)
					maybeStartTracking = false
					return super.onInterceptTouchEvent(ev)
				}

				override fun setTranslationY(translationY: Float) {
					if (getTranslationY() != translationY) {
						super.setTranslationY(translationY)
						updateContextViewPosition()
						fragmentView?.invalidate()
					}
				}

				override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
					super.onLayout(changed, l, t, r, b)

					if (scrimView != null) {
						scrimView?.getLocationInWindow(scrimViewLocation)
						fragmentView?.invalidate()
					}
				}
			}

			filterTabsView?.visibility = View.GONE

			canShowFilterTabsView = false

			filterTabsView?.setDelegate(object : FilterTabsViewDelegate {
				private fun showDeleteAlert(dialogFilter: MessagesController.DialogFilter?) {
					val parentActivity = parentActivity ?: return

					val builder = AlertDialog.Builder(parentActivity)
					builder.setTitle(context.getString(R.string.FilterDelete))
					builder.setMessage(context.getString(R.string.FilterDeleteAlert))
					builder.setNegativeButton(context.getString(R.string.Cancel), null)

					builder.setPositiveButton(context.getString(R.string.Delete)) { _, _ ->
						val req = TLRPC.TL_messages_updateDialogFilter()
						req.id = dialogFilter!!.id

						connectionsManager.sendRequest(req) { _, _ ->
							// unused
						}

						messagesController.removeFilter(dialogFilter)
						messagesStorage.deleteDialogFilter(dialogFilter)
					}

					val alertDialog = builder.create()

					showDialog(alertDialog)

					val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
					button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
				}

				override fun onSamePageSelected() {
					scrollToTop()
				}

				override fun onPageReorder(fromId: Int, toId: Int) {
					viewPages?.forEach { viewPage ->
						if (viewPage?.selectedType == fromId) {
							viewPage.selectedType = toId
						}
						else if (viewPage?.selectedType == toId) {
							viewPage.selectedType = fromId
						}
					}
				}

				override fun onPageSelected(tab: FilterTabsView.Tab, forward: Boolean) {
					if (viewPages?.firstOrNull()?.selectedType == tab.id) {
						return
					}

					if (tab.isLocked) {
						filterTabsView?.shakeLock(tab.id)
						showDialog(LimitReachedBottomSheet(this@DialogsActivity, LimitReachedBottomSheet.TYPE_FOLDERS, currentAccount))
						return
					}

					val dialogFilters = messagesController.dialogFilters

					if (!tab.isDefault && (tab.id < 0 || tab.id >= dialogFilters.size)) {
						return
					}

					viewPages?.get(1)?.let {
						it.selectedType = tab.id
						it.visibility = View.VISIBLE
						it.translationX = viewPages!![0]!!.measuredWidth.toFloat()
					}

					showScrollbars(false)
					switchToCurrentSelectedMode(true)
					animatingForward = forward
				}

				override fun canPerformActions(): Boolean {
					return !searching
				}

				override fun onPageScrolled(progress: Float) {
					if (progress == 1f && viewPages!![1]?.visibility != View.VISIBLE && !searching) {
						return
					}

					if (animatingForward) {
						viewPages!![0]?.translationX = -progress * viewPages!![0]!!.measuredWidth
						viewPages!![1]?.translationX = viewPages!![0]!!.measuredWidth - progress * viewPages!![0]!!.measuredWidth
					}
					else {
						viewPages!![0]?.translationX = progress * viewPages!![0]!!.measuredWidth
						viewPages!![1]?.translationX = progress * viewPages!![0]!!.measuredWidth - viewPages!![0]!!.measuredWidth
					}

					if (progress == 1f) {
						val tempPage = viewPages!![0]
						viewPages!![0] = viewPages!![1]
						viewPages!![1] = tempPage
						viewPages!![1]?.visibility = View.GONE

						showScrollbars(true)
						updateCounters(false)
						checkListLoad(viewPages!![0])

						viewPages!![0]?.dialogsAdapter?.resume()
						viewPages!![1]?.dialogsAdapter?.pause()
					}
				}

				override fun getTabCounter(tabId: Int): Int {
					if (tabId == filterTabsView?.defaultTabId) {
						return messagesStorage.mainUnreadCount
					}

					val dialogFilters = messagesController.dialogFilters

					return if (tabId < 0 || tabId >= dialogFilters.size) {
						0
					}
					else {
						messagesController.dialogFilters[tabId].unreadCount
					}
				}

				@SuppressLint("ClickableViewAccessibility")
				override fun didSelectTab(tabView: FilterTabsView.TabView, selected: Boolean): Boolean {
					val parentActivity = parentActivity ?: return false

					if (actionBar!!.isActionModeShowed) {
						return false
					}

					if (scrimPopupWindow != null) {
						scrimPopupWindow?.dismiss()
						scrimPopupWindow = null
						scrimPopupWindowItems = null
						return false
					}

					val rect = Rect()

					val dialogFilter = if (tabView.id == filterTabsView?.defaultTabId) {
						null
					}
					else {
						messagesController.dialogFilters[tabView.id]
					}

					val popupLayout = ActionBarPopupWindowLayout(parentActivity)

					popupLayout.setOnTouchListener(object : OnTouchListener {
						private val pos = IntArray(2)

						@SuppressLint("ClickableViewAccessibility")
						override fun onTouch(v: View, event: MotionEvent): Boolean {
							if (event.actionMasked == MotionEvent.ACTION_DOWN) {
								if (scrimPopupWindow?.isShowing == true) {
									val contentView = scrimPopupWindow!!.contentView
									contentView.getLocationInWindow(pos)
									rect.set(pos[0], pos[1], pos[0] + contentView.measuredWidth, pos[1] + contentView.measuredHeight)

									if (!rect.contains(event.x.toInt(), event.y.toInt())) {
										scrimPopupWindow?.dismiss()
									}
								}
							}
							else if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
								if (scrimPopupWindow?.isShowing == true) {
									scrimPopupWindow?.dismiss()
								}
							}

							return false
						}
					})

					popupLayout.setDispatchKeyEventListener { keyEvent ->
						if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK && keyEvent.repeatCount == 0 && scrimPopupWindow?.isShowing == true) {
							scrimPopupWindow?.dismiss()
						}
					}

					val backgroundPaddings = Rect()

					val shadowDrawable = ResourcesCompat.getDrawable(parentActivity.resources, R.drawable.popup_fixed_alert, null)!!.mutate()

					shadowDrawable.getPadding(backgroundPaddings)

					popupLayout.background = shadowDrawable
					popupLayout.setBackgroundColor(context.getColor(R.color.background))

					val linearLayout = LinearLayout(parentActivity)

					val scrollView = object : ScrollView(parentActivity, null, 0, R.style.scrollbarShapeStyle) {
						override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
							super.onMeasure(widthMeasureSpec, heightMeasureSpec)
							setMeasuredDimension(linearLayout.measuredWidth, measuredHeight)
						}
					}

					scrollView.clipToPadding = false

					popupLayout.addView(scrollView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat()))

					linearLayout.minimumWidth = AndroidUtilities.dp(200f)
					linearLayout.orientation = LinearLayout.VERTICAL

					scrimPopupWindowItems = arrayOfNulls(3)

					var a = 0
					val n = if (tabView.id == filterTabsView?.defaultTabId) 2 else 3

					while (a < n) {
						val cell = ActionBarMenuSubItem(parentActivity, a == 0, a == n - 1)

						if (a == 0) {
							if (messagesController.dialogFilters.size <= 1) {
								a++
								continue
							}

							cell.setTextAndIcon(context.getString(R.string.FilterReorder), R.drawable.tabs_reorder)
						}
						else if (a == 1) {
							if (n == 2) {
								cell.setTextAndIcon(context.getString(R.string.FilterEditAll), R.drawable.msg_edit)
							}
							else {
								cell.setTextAndIcon(context.getString(R.string.FilterEdit), R.drawable.msg_edit)
							}
						}
						else {
							cell.setTextAndIcon(context.getString(R.string.FilterDeleteItem), R.drawable.msg_delete)
						}

						scrimPopupWindowItems!![a] = cell

						linearLayout.addView(cell)

						val i = a

						cell.setOnClickListener {
							if (i == 0) {
								resetScroll()
								filterTabsView?.setIsEditing(true)
								showDoneItem(true)
							}
							else if (i == 1) {
								if (n == 2) {
									presentFragment(FiltersSetupActivity())
								}
								else {
									presentFragment(FilterCreateActivity(dialogFilter))
								}
							}
							else if (i == 2) {
								showDeleteAlert(dialogFilter)
							}

							scrimPopupWindow?.dismiss()
						}

						a++
					}

					scrollView.addView(linearLayout, createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP))

					scrimPopupWindow = object : ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
						override fun dismiss() {
							super.dismiss()

							if (scrimPopupWindow !== this) {
								return
							}

							scrimPopupWindow = null
							scrimPopupWindowItems = null

							scrimAnimatorSet?.cancel()
							scrimAnimatorSet = null

							scrimAnimatorSet = AnimatorSet()
							scrimViewAppearing = false

							val animators = ArrayList<Animator>()

							animators.add(ObjectAnimator.ofInt(scrimPaint, AnimationProperties.PAINT_ALPHA, 0))

							scrimAnimatorSet?.playTogether(animators)
							scrimAnimatorSet?.duration = 220

							scrimAnimatorSet?.addListener(object : AnimatorListenerAdapter() {
								override fun onAnimationEnd(animation: Animator) {
									scrimView?.background = null
									scrimView = null

									fragmentView?.invalidate()
								}
							})

							scrimAnimatorSet?.start()

							parentActivity.window.decorView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
						}
					}

					scrimViewBackground = Theme.createRoundRectDrawable(AndroidUtilities.dp(6f), 0, context.getColor(R.color.background))

					scrimPopupWindow?.setDismissAnimationDuration(220)
					scrimPopupWindow?.isOutsideTouchable = true
					scrimPopupWindow?.isClippingEnabled = true
					scrimPopupWindow?.animationStyle = R.style.PopupContextAnimation
					scrimPopupWindow?.isFocusable = true

					popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), View.MeasureSpec.AT_MOST))

					scrimPopupWindow?.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
					scrimPopupWindow?.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
					scrimPopupWindow?.contentView?.isFocusableInTouchMode = true

					tabView.getLocationInWindow(scrimViewLocation)
					var popupX = scrimViewLocation[0] + backgroundPaddings.left - AndroidUtilities.dp(16f)

					if (popupX < AndroidUtilities.dp(6f)) {
						popupX = AndroidUtilities.dp(6f)
					}
					else if (popupX > fragmentView!!.measuredWidth - AndroidUtilities.dp(6f) - popupLayout.measuredWidth) {
						popupX = fragmentView!!.measuredWidth - AndroidUtilities.dp(6f) - popupLayout.measuredWidth
					}

					val popupY = scrimViewLocation[1] + tabView.measuredHeight - AndroidUtilities.dp(12f)

					scrimPopupWindow?.showAtLocation(fragmentView!!, Gravity.LEFT or Gravity.TOP, popupX, popupY)

					scrimView = tabView

					scrimViewSelected = selected

					fragmentView?.invalidate()

					scrimAnimatorSet?.cancel()

					scrimAnimatorSet = AnimatorSet()

					scrimViewAppearing = true

					val animators = ArrayList<Animator>()
					animators.add(ObjectAnimator.ofInt(scrimPaint, AnimationProperties.PAINT_ALPHA, 0, 50))

					scrimAnimatorSet?.playTogether(animators)
					scrimAnimatorSet?.duration = 150
					scrimAnimatorSet?.start()

					return true
				}

				override fun isTabMenuVisible(): Boolean {
					return scrimPopupWindow?.isShowing == true
				}

				override fun onDeletePressed(id: Int) {
					showDeleteAlert(messagesController.dialogFilters[id])
				}
			})
		}

		if (allowSwitchAccount && UserConfig.activatedAccountsCount > 1) {
			switchItem = menu.addItemWithWidth(1, 0, AndroidUtilities.dp(56f))

			val avatarDrawable = AvatarDrawable()
			avatarDrawable.setTextSize(AndroidUtilities.dp(12f))

			val imageView = BackupImageView(context)
			imageView.setRoundRadius(AndroidUtilities.dp(18f))

			switchItem?.addView(imageView, createFrame(36, 36, Gravity.CENTER))

			val user = userConfig.getCurrentUser()

			avatarDrawable.setInfo(user)

			imageView.imageReceiver.currentAccount = currentAccount

			val thumb = user?.photo?.strippedBitmap ?: avatarDrawable

			imageView.setImage(ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_STRIPPED), "50_50", thumb, user)

			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				val u = AccountInstance.getInstance(a).userConfig.getCurrentUser()

				if (u != null) {
					val cell = AccountSelectCell(context, false)
					cell.setAccount(a, true)
					switchItem?.addSubItem(10 + a, cell, AndroidUtilities.dp(230f), AndroidUtilities.dp(48f))
				}
			}
		}

		actionBar?.setAllowOverlayTitle(true)

		createActionMode(null)

		val contentView = ContentView(context)

		fragmentView = contentView

		val pagesCount = if (folderId == 0 && initialDialogsType == 0 && !onlySelect) 2 else 1

		viewPages = arrayOfNulls(pagesCount)

		for (a in 0 until pagesCount) {
			val viewPage = object : ViewPage(context) {
				override fun setTranslationX(translationX: Float) {
					if (getTranslationX() != translationX) {
						super.setTranslationX(translationX)

						if (tabsAnimationInProgress) {
							viewPages?.firstOrNull()?.takeIf { it === this }?.let {
								val scrollProgress = abs(it.translationX) / it.measuredWidth.toFloat()
								filterTabsView?.selectTabWithId(it.selectedType, scrollProgress)
							}
						}

						contentView.invalidateBlur()
					}
				}
			}

			contentView.addView(viewPage, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
			viewPage.dialogsType = initialDialogsType

			viewPages!![a] = viewPage

			viewPage.progressView = FlickerLoadingView(context)
			viewPage.progressView?.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE)
			viewPage.progressView?.gone()

			viewPage.addView(viewPage.progressView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

			viewPage.listView = DialogsRecyclerView(context, viewPage)
			viewPage.listView?.setAccessibilityEnabled(false)
			viewPage.listView?.setAnimateEmptyView(true, 0)
			viewPage.listView?.clipToPadding = false
			viewPage.listView?.pivotY = 0f

			viewPage.dialogsItemAnimator = object : DialogsItemAnimator(viewPage.listView) {
				override fun onRemoveStarting(item: RecyclerView.ViewHolder) {
					super.onRemoveStarting(item)

					if (viewPage.layoutManager?.findFirstVisibleItemPosition() == 0) {
						val v = viewPage.layoutManager!!.findViewByPosition(0)
						v?.invalidate()

						if (viewPage.archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN) {
							viewPage.archivePullViewState = ARCHIVE_ITEM_STATE_SHOWED
						}

						viewPage.pullForegroundDrawable?.doNotShow()
					}
				}

				override fun onRemoveFinished(item: RecyclerView.ViewHolder) {
					if (dialogRemoveFinished == 2) {
						dialogRemoveFinished = 1
					}
				}

				override fun onAddFinished(item: RecyclerView.ViewHolder) {
					if (dialogInsertFinished == 2) {
						dialogInsertFinished = 1
					}
				}

				override fun onChangeFinished(item: RecyclerView.ViewHolder, oldItem: Boolean) {
					if (dialogChangeFinished == 2) {
						dialogChangeFinished = 1
					}
				}

				override fun onAllAnimationsDone() {
					if (dialogRemoveFinished == 1 || dialogInsertFinished == 1 || dialogChangeFinished == 1) {
						onDialogAnimationFinished()
					}
				}
			}

			viewPage.listView?.itemAnimator = viewPage.dialogsItemAnimator
			viewPage.listView?.isVerticalScrollBarEnabled = true
			viewPage.listView?.setInstantClick(true)

			viewPage.layoutManager = object : LinearLayoutManager(context) {
				private var fixOffset = false

				override fun scrollToPositionWithOffset(position: Int, offset: Int) {
					@Suppress("NAME_SHADOWING") var offset = offset
					if (fixOffset) {

						offset -= viewPage.listView!!.paddingTop
					}

					super.scrollToPositionWithOffset(position, offset)
				}

				override fun prepareForDrop(view: View, target: View, x: Int, y: Int) {
					fixOffset = true
					super.prepareForDrop(view, target, x, y)
					fixOffset = false
				}

				override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
					if (hasHiddenArchive() && position == 1) {
						super.smoothScrollToPosition(recyclerView, state, position)
					}
					else {
						val linearSmoothScroller = LinearSmoothScrollerCustom(recyclerView.context, LinearSmoothScrollerCustom.POSITION_MIDDLE)
						linearSmoothScroller.targetPosition = position
						startSmoothScroll(linearSmoothScroller)
					}
				}

				override fun scrollVerticallyBy(dy: Int, recycler: Recycler, state: RecyclerView.State): Int {
					if (viewPage.listView?.isFastScrollAnimationRunning == true) {
						return 0
					}

					val isDragging = viewPage.listView?.scrollState == RecyclerView.SCROLL_STATE_DRAGGING
					var measuredDy = dy
					val pTop = viewPage.listView?.paddingTop ?: 0

					if (viewPage.dialogsType == 0 && !onlySelect && folderId == 0 && dy < 0 && messagesController.hasHiddenArchive() && viewPage.archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN) {
						viewPage.listView?.overScrollMode = View.OVER_SCROLL_ALWAYS

						var currentPosition = viewPage.layoutManager!!.findFirstVisibleItemPosition()

						if (currentPosition == 0) {
							val view = viewPage.layoutManager?.findViewByPosition(currentPosition)

							if (view != null && view.bottom - pTop <= AndroidUtilities.dp(1f)) {
								currentPosition = 1
							}
						}

						if (!isDragging) {
							val view = viewPage.layoutManager?.findViewByPosition(currentPosition)

							if (view != null) {
								val dialogHeight = AndroidUtilities.dp(if (SharedConfig.useThreeLinesLayout) 78f else 72f) + 1
								val canScrollDy = -(view.top - pTop) + (currentPosition - 1) * dialogHeight
								val positiveDy = abs(dy)

								if (canScrollDy < positiveDy) {
									measuredDy = -canScrollDy
								}
							}
						}
						else if (currentPosition == 0) {
							val v = viewPage.layoutManager!!.findViewByPosition(currentPosition)
							var k = 1f + (v!!.top - pTop) / v.measuredHeight.toFloat()

							if (k > 1f) {
								k = 1f
							}

							viewPage.listView?.overScrollMode = View.OVER_SCROLL_NEVER

							measuredDy *= (PullForegroundDrawable.startPullParallax - PullForegroundDrawable.endPullParallax * k).toInt()

							if (measuredDy > -1) {
								measuredDy = -1
							}

							if (undoView.firstOrNull()?.visibility == View.VISIBLE) {
								undoView.firstOrNull()?.hide(true, 1)
							}
						}
					}

					if (viewPage.dialogsType == 0 && viewPage.listView?.viewOffset != 0f && dy > 0 && isDragging) {
						var ty = (viewPage.listView!!.viewOffset.toInt() - dy).toFloat()

						if (ty < 0) {
							measuredDy = ty.toInt()
							ty = 0f
						}
						else {
							measuredDy = 0
						}

						viewPage.listView?.setViewsOffset(ty)
					}

					if (viewPage.dialogsType == 0 && viewPage.archivePullViewState != ARCHIVE_ITEM_STATE_PINNED && hasHiddenArchive()) {
						val usedDy = super.scrollVerticallyBy(measuredDy, recycler, state)

						viewPage.pullForegroundDrawable?.scrollDy = usedDy

						val currentPosition = viewPage.layoutManager!!.findFirstVisibleItemPosition()
						var firstView: View? = null

						if (currentPosition == 0) {
							firstView = viewPage.layoutManager?.findViewByPosition(currentPosition)
						}

						if (currentPosition == 0 && firstView != null && firstView.bottom - pTop >= AndroidUtilities.dp(4f)) {
							if (startArchivePullingTime == 0L) {
								startArchivePullingTime = System.currentTimeMillis()
							}

							if (viewPage.archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN) {
								viewPage.pullForegroundDrawable?.showHidden()
							}

							var k = 1f + (firstView.top - pTop) / firstView.measuredHeight.toFloat()

							if (k > 1f) {
								k = 1f
							}

							val pullingTime = System.currentTimeMillis() - startArchivePullingTime
							val canShowInternal = k > PullForegroundDrawable.SNAP_HEIGHT && pullingTime > PullForegroundDrawable.minPullingTime + 20

							if (canShowHiddenArchive != canShowInternal) {
								canShowHiddenArchive = canShowInternal

								if (viewPage.archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN) {
									viewPage.listView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
									viewPage.pullForegroundDrawable?.colorize(canShowInternal)
								}
							}

							if (viewPage.archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN && measuredDy - usedDy != 0 && dy < 0 && isDragging) {
								val tk = 1f - viewPage.listView!!.viewOffset / PullForegroundDrawable.maxOverscroll
								val ty = viewPage.listView!!.viewOffset - dy * PullForegroundDrawable.startPullOverScroll * tk
								viewPage.listView?.setViewsOffset(ty)
							}

							viewPage.pullForegroundDrawable?.pullProgress = k
							viewPage.pullForegroundDrawable?.setListView(viewPage.listView)
						}
						else {
							startArchivePullingTime = 0
							canShowHiddenArchive = false
							viewPage.archivePullViewState = ARCHIVE_ITEM_STATE_HIDDEN

							viewPage.pullForegroundDrawable?.let {
								it.resetText()
								it.pullProgress = 0f
								it.setListView(viewPage.listView)
							}
						}

						firstView?.invalidate()

						return usedDy
					}

					return super.scrollVerticallyBy(measuredDy, recycler, state)
				}

				override fun onLayoutChildren(recycler: Recycler, state: RecyclerView.State) {
					if (BuildConfig.DEBUG_PRIVATE_VERSION) {
						try {
							super.onLayoutChildren(recycler, state)
						}
						catch (e: IndexOutOfBoundsException) {
							throw RuntimeException("Inconsistency detected. dialogsListIsFrozen=$dialogsListFrozen lastUpdateAction=$debugLastUpdateAction")
						}
					}
					else {
						try {
							super.onLayoutChildren(recycler, state)
						}
						catch (e: IndexOutOfBoundsException) {
							FileLog.e(e)

							AndroidUtilities.runOnUIThread {
								viewPage.dialogsAdapter?.notifyDataSetChanged()
							}
						}
					}
				}
			}

			viewPage.layoutManager?.orientation = LinearLayoutManager.VERTICAL

			viewPage.listView?.layoutManager = viewPage.layoutManager
			viewPage.listView?.verticalScrollbarPosition = if (LocaleController.isRTL) RecyclerView.SCROLLBAR_POSITION_LEFT else RecyclerView.SCROLLBAR_POSITION_RIGHT

			viewPage.addView(viewPage.listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			viewPage.listView?.setOnItemClickListener(RecyclerListView.OnItemClickListener { view, position ->
				if (initialDialogsType == 10) {
					onItemLongClick(view, position, 0f, 0f, viewPage.dialogsType, viewPage.dialogsAdapter)
					return@OnItemClickListener
				}
				else if ((initialDialogsType == 11 || initialDialogsType == 13) && position == 1) {
					val args = Bundle()
					args.putBoolean("forImport", true)

					val array = longArrayOf(userConfig.getClientUserId())
					args.putLongArray("result", array)
					args.putInt("chatType", ChatObject.CHAT_TYPE_MEGAGROUP)

					val title = arguments?.getString("importTitle")

					if (!title.isNullOrEmpty()) {
						args.putString("title", title)
					}

					val activity = GroupCreateFinalActivity(args)

					activity.setDelegate(object : GroupCreateFinalActivityDelegate {
						override fun didStartChatCreation() {
							// unused
						}

						override fun didFinishChatCreation(fragment: GroupCreateFinalActivity?, chatId: Long) {
							val arrayList = ArrayList<Long>()
							arrayList.add(-chatId)

							val dialogsActivityDelegate = delegate

							if (closeFragment) {
								removeSelfFromStack()
							}

							dialogsActivityDelegate?.didSelectDialogs(this@DialogsActivity, arrayList, null, true)
						}

						override fun didFailChatCreation() {
							// unused
						}
					})

					presentFragment(activity)

					return@OnItemClickListener
				}

				onItemClick(view, position, viewPage.dialogsAdapter)
			})

			viewPage.listView?.setOnItemLongClickListener(object : OnItemLongClickListenerExtended {
				override fun onItemClick(view: View, position: Int, x: Float, y: Float): Boolean {
					return if (filterTabsView?.visibility == View.VISIBLE && filterTabsView?.isEditing == true) {
						false
					}
					else {
						onItemLongClick(view, position, x, y, viewPage.dialogsType, viewPage.dialogsAdapter)
					}
				}

				override fun onMove(dx: Float, dy: Float) {
					if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
						movePreviewFragment(dy)
					}
				}

				override fun onLongClickRelease() {
					if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
						finishPreviewFragment()
					}
				}
			})

			viewPage.swipeController = SwipeController(viewPage)

			viewPage.recyclerItemsEnterAnimator = RecyclerItemsEnterAnimator(viewPage.listView, false)

			viewPage.itemTouchHelper = ItemTouchHelper(viewPage.swipeController!!)
			viewPage.itemTouchHelper?.attachToRecyclerView(viewPage.listView)

			viewPage.listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
				private var wasManualScroll = false

				override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
					if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
						wasManualScroll = true
						scrollingManually = true
					}
					else {
						scrollingManually = false
					}

					if (newState == RecyclerView.SCROLL_STATE_IDLE) {
						wasManualScroll = false
						disableActionBarScrolling = false

						if (waitingForScrollFinished) {
							waitingForScrollFinished = false

							if (updatePullAfterScroll) {
								viewPage.listView?.updatePullState()
								updatePullAfterScroll = false
							}

							viewPage.dialogsAdapter?.notifyDataSetChanged()
						}

						if (filterTabsView?.visibility == View.VISIBLE && viewPages!![0]?.listView === recyclerView) {
							val scrollY = -actionBar!!.translationY.toInt()
							val actionBarHeight = ActionBar.getCurrentActionBarHeight()

							if (scrollY != 0 && scrollY != actionBarHeight) {
								if (scrollY < actionBarHeight / 2) {
									recyclerView.smoothScrollBy(0, -scrollY)
								}
								else if (viewPages!![0]!!.listView!!.canScrollVertically(1)) {
									recyclerView.smoothScrollBy(0, actionBarHeight - scrollY)
								}
							}
						}
					}
				}

				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					@Suppress("NAME_SHADOWING") var dy = dy
					viewPage.dialogsItemAnimator?.onListScroll(-dy)

					checkListLoad(viewPage)

					if (initialDialogsType != 10 && wasManualScroll && floatingButtonContainer!!.visibility != View.GONE && recyclerView.childCount > 0) {
						val firstVisibleItem = viewPage.layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION

						if (firstVisibleItem != RecyclerView.NO_POSITION) {
							val holder = recyclerView.findViewHolderForAdapterPosition(firstVisibleItem)

							if (!hasHiddenArchive() || holder != null && holder.adapterPosition != 0) {
								var firstViewTop = 0

								if (holder != null) {
									firstViewTop = holder.itemView.top
								}

								val goingDown: Boolean
								var changed = true

								if (prevPosition == firstVisibleItem) {
									val topDelta = prevTop - firstViewTop
									goingDown = firstViewTop < prevTop
									changed = abs(topDelta) > 1
								}
								else {
									goingDown = firstVisibleItem > prevPosition
								}

								if (changed && scrollUpdated && (goingDown || scrollingManually)) {
									hideFloatingButton(goingDown)
								}

								prevPosition = firstVisibleItem
								prevTop = firstViewTop
								scrollUpdated = true
							}
						}
					}

					if (filterTabsView?.visibility == View.VISIBLE && recyclerView === viewPages!![0]?.listView && !searching && !actionBar!!.isActionModeShowed && !disableActionBarScrolling && filterTabsViewIsVisible) {
						if (dy > 0 && hasHiddenArchive() && viewPages!![0]?.dialogsType == 0) {
							val child = recyclerView.getChildAt(0)

							if (child != null) {
								val holder = recyclerView.getChildViewHolder(child)

								if (holder.adapterPosition == 0) {
									val visiblePartAfterScroll = child.measuredHeight + (child.top - recyclerView.paddingTop)

									if (visiblePartAfterScroll + dy > 0) {
										dy = if (visiblePartAfterScroll < 0) {
											-visiblePartAfterScroll
										}
										else {
											return
										}
									}
								}
							}
						}

						val currentTranslation = actionBar!!.translationY
						var newTranslation = currentTranslation - dy

						if (newTranslation < -ActionBar.getCurrentActionBarHeight()) {
							newTranslation = -ActionBar.getCurrentActionBarHeight().toFloat()
						}
						else if (newTranslation > 0) {
							newTranslation = 0f
						}

						if (newTranslation != currentTranslation) {
							setScrollY(newTranslation)
						}
					}

					(fragmentView as? SizeNotifierFrameLayout)?.invalidateBlur()
				}
			})

			viewPage.archivePullViewState = if (SharedConfig.archiveHidden) ARCHIVE_ITEM_STATE_HIDDEN else ARCHIVE_ITEM_STATE_PINNED

			if (viewPage.pullForegroundDrawable == null && folderId == 0) {
				viewPage.pullForegroundDrawable = object : PullForegroundDrawable(context.getString(R.string.AccSwipeForArchive), context.getString(R.string.AccReleaseForArchive)) {
					override val viewOffset: Float
						get() = viewPage.listView?.viewOffset ?: 0f
				}

				if (hasHiddenArchive()) {
					viewPage.pullForegroundDrawable?.showHidden()
				}
				else {
					viewPage.pullForegroundDrawable?.doNotShow()
				}

				viewPage.pullForegroundDrawable?.setWillDraw(viewPage.archivePullViewState != ARCHIVE_ITEM_STATE_PINNED)
			}

			viewPage.dialogsAdapter = object : DialogsAdapter(this@DialogsActivity, viewPage.dialogsType, folderId, onlySelect, selectedDialogs, currentAccount) {
				override fun notifyDataSetChanged() {
					viewPage.lastItemsCount = itemCount

					try {
						super.notifyDataSetChanged()
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			viewPage.dialogsAdapter?.setForceShowEmptyCell(afterSignup)

			if (AndroidUtilities.isTablet() && openedDialogId != 0L) {
				viewPage.dialogsAdapter?.setOpenedDialogId(openedDialogId)
			}

			viewPage.dialogsAdapter?.setArchivedPullDrawable(viewPage.pullForegroundDrawable)

			viewPage.listView?.adapter = viewPage.dialogsAdapter
			viewPage.listView?.setEmptyView(if (folderId == 0) viewPage.progressView else null)

			viewPage.scrollHelper = RecyclerAnimationScrollHelper(viewPage.listView, viewPage.layoutManager)

			if (a != 0) {
				viewPages!![a]?.visibility = View.GONE
			}
		}

		var type = 0

		if (searchString != null) {
			type = 2
		}
		else if (!onlySelect) {
			type = 1
		}

		searchViewPager = SearchViewPager(context, this, type, initialDialogsType, folderId, object : ChatPreviewDelegate {
			override fun startChatPreview(listView: RecyclerListView, cell: DialogCell) {
				showChatPreview(cell)
			}

			override fun move(dy: Float) {
				if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					movePreviewFragment(dy)
				}
			}

			override fun finish() {
				if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					finishPreviewFragment()
				}
			}
		})

		contentView.addView(searchViewPager)

		if (onlySelect) {
			if (initialDialogsType == 3 && selectAlertString == null) {
				searchViewPager?.dialogsSearchAdapter?.ignoredIds = longArrayOf(BuildConfig.AI_BOT_ID, 333000L, 42777L, 777000L)
			}
		}

		searchViewPager?.dialogsSearchAdapter?.setDelegate(object : DialogsSearchAdapterDelegate {
			override fun searchStateChanged(searching: Boolean, animated: Boolean) {
				@Suppress("NAME_SHADOWING") var animated = animated

				if (searchViewPager?.emptyView?.visibility == View.VISIBLE) {
					animated = true
				}

				if (this@DialogsActivity.searching && searchWas && searchViewPager?.emptyView != null) {
					searchViewPager?.emptyView?.showProgress(searching || searchViewPager!!.dialogsSearchAdapter.itemCount != 0, animated)
				}

				if (searching && searchViewPager?.dialogsSearchAdapter?.itemCount == 0) {
					searchViewPager?.cancelEnterAnimation()
				}
			}

			override fun didPressedOnSubDialog(did: Long) {
				if (onlySelect) {
					if (!validateSlowModeDialog(did)) {
						return
					}

					if (selectedDialogs.isNotEmpty()) {
						val checked = addOrRemoveSelectedDialog(did, null)
						findAndUpdateCheckBox(did, checked)
						updateSelectedCount()
						actionBar?.closeSearchField()
					}
					else {
						didSelectResult(did, useAlert = true, param = false)
					}
				}
				else {
					val args = Bundle()

					if (DialogObject.isUserDialog(did)) {
						args.putLong("user_id", did)
					}
					else {
						args.putLong("chat_id", -did)
					}

					closeSearch()

					if (AndroidUtilities.isTablet() && viewPages != null) {
						for (viewPage in viewPages!!) {
							viewPage?.dialogsAdapter?.setOpenedDialogId(did.also {
								openedDialogId = it
							})
						}

						updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG)
					}

					if (searchString != null) {
						if (messagesController.checkCanOpenChat(args, this@DialogsActivity)) {
							notificationCenter.postNotificationName(NotificationCenter.closeChats)
							presentFragment(ChatActivity(args))
						}
					}
					else {
						if (messagesController.checkCanOpenChat(args, this@DialogsActivity)) {
							presentFragment(ChatActivity(args))
						}
					}
				}
			}

			override fun needRemoveHint(did: Long) {
				val parentActivity = parentActivity ?: return

				val user = messagesController.getUser(did) ?: return

				val builder = AlertDialog.Builder(parentActivity)
				builder.setTitle(context.getString(R.string.ChatHintsDeleteAlertTitle))
				builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChatHintsDeleteAlert", R.string.ChatHintsDeleteAlert, ContactsController.formatName(user.first_name, user.last_name))))
				builder.setPositiveButton(context.getString(R.string.StickersRemove)) { _, _ -> mediaDataController.removePeer(did) }
				builder.setNegativeButton(context.getString(R.string.Cancel), null)

				val dialog = builder.create()

				showDialog(dialog)

				val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView

				button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
			}

			override fun needClearList() {
				val parentActivity = parentActivity ?: return
				val builder = AlertDialog.Builder(parentActivity)

				if (searchViewPager!!.dialogsSearchAdapter.isSearchWas && searchViewPager!!.dialogsSearchAdapter.isRecentSearchDisplayed) {
					builder.setTitle(context.getString(R.string.ClearSearchAlertPartialTitle))
					builder.setMessage(LocaleController.formatPluralString("ClearSearchAlertPartial", searchViewPager!!.dialogsSearchAdapter.recentResultsCount))
					builder.setPositiveButton(context.getString(R.string.Clear).uppercase()) { _, _ -> searchViewPager?.dialogsSearchAdapter?.clearRecentSearch() }
				}
				else {
					builder.setTitle(context.getString(R.string.ClearSearchAlertTitle))
					builder.setMessage(context.getString(R.string.ClearSearchAlert))

					builder.setPositiveButton(context.getString(R.string.ClearButton).uppercase()) { _, _ ->
						searchViewPager?.dialogsSearchAdapter?.let {
							if (it.isRecentSearchDisplayed) {
								it.clearRecentSearch()
							}
							else {
								it.clearRecentHashtags()
							}
						}
					}
				}

				builder.setNegativeButton(context.getString(R.string.Cancel), null)

				val dialog = builder.create()

				showDialog(dialog)

				val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
				button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
			}

			override fun runResultsEnterAnimation() {
				searchViewPager?.runResultsEnterAnimation()
			}

			override fun isSelected(dialogId: Long): Boolean {
				return selectedDialogs.contains(dialogId)
			}
		})

		searchViewPager?.searchListView?.setOnItemClickListener { view, position ->
			if (initialDialogsType == 10) {
				onItemLongClick(view, position, 0f, 0f, -1, searchViewPager!!.dialogsSearchAdapter)
				return@setOnItemClickListener
			}

			onItemClick(view, position, searchViewPager!!.dialogsSearchAdapter)
		}

		searchViewPager?.searchListView?.setOnItemLongClickListener(object : OnItemLongClickListenerExtended {
			override fun onItemClick(view: View, position: Int, x: Float, y: Float): Boolean {
				return onItemLongClick(view, position, x, y, -1, searchViewPager!!.dialogsSearchAdapter)
			}

			override fun onMove(dx: Float, dy: Float) {
				if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					movePreviewFragment(dy)
				}
			}

			override fun onLongClickRelease() {
				if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					finishPreviewFragment()
				}
			}
		})

		searchViewPager?.setFilteredSearchViewDelegate { _, users, dates, archive ->
			updateFiltersView(users, dates, archive, true)
		}

		searchViewPager?.visibility = View.GONE

		filtersView = FiltersView(parentActivity)

		filtersView?.setOnItemClickListener { _, position ->
			filtersView?.cancelClickRunnables(true)
			addSearchFilter(filtersView!!.getFilterAt(position))
		}

		contentView.addView(filtersView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP))

		filtersView?.visibility = View.GONE

		floatingButtonContainer = FrameLayout(context)
		floatingButtonContainer?.visibility = if (onlySelect && initialDialogsType != 10 || folderId != 0) View.GONE else View.VISIBLE

		val bottomMargin = 14

		contentView.addView(floatingButtonContainer, createFrame(56, 56f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.BOTTOM, if (LocaleController.isRTL) 14f else 0f, 0f, if (LocaleController.isRTL) 0f else 14f, bottomMargin.toFloat()))

		floatingButtonContainer?.setOnClickListener {
			if (parentLayout != null && parentLayout?.isInPreviewMode == true) {
				finishPreviewFragment()
				return@setOnClickListener
			}

			if (initialDialogsType == 10) {
				if (delegate == null || selectedDialogs.isEmpty()) {
					return@setOnClickListener
				}

				delegate?.didSelectDialogs(this@DialogsActivity, selectedDialogs, null, false)
			}
			else {
				if (floatingButton?.visibility != View.VISIBLE) {
					return@setOnClickListener
				}

				val args = Bundle()
				args.putBoolean("destroyAfterSelect", true)
				args.putBoolean("disableSections", true)
				presentFragment(ContactsActivity(args))
			}
		}

		floatingButton = ImageView(context)
		floatingButton?.scaleType = ImageView.ScaleType.CENTER
		floatingButton?.imageTintList = ColorStateList.valueOf(ResourcesCompat.getColor(context.resources, R.color.white, null))

		if (initialDialogsType == 10) {
			floatingButton?.setImageResource(R.drawable.floating_check)
			floatingButtonContainer?.contentDescription = context.getString(R.string.Done)
		}
		else {
			floatingButton?.setImageResource(R.drawable.ic_add)
			floatingButtonContainer?.contentDescription = context.getString(R.string.NewMessageTitle)
		}

		val animator = StateListAnimator()
		animator.addState(intArrayOf(android.R.attr.state_pressed), ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Z, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(4f).toFloat()).setDuration(200))
		animator.addState(intArrayOf(), ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Z, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(2f).toFloat()).setDuration(200))

		floatingButtonContainer?.stateListAnimator = animator

		floatingButtonContainer?.outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, AndroidUtilities.dp(56f), AndroidUtilities.dp(56f))
				// outline.setRoundRect(0, 0, AndroidUtilities.dp(56f), AndroidUtilities.dp(56f), AndroidUtilities.dp(18f).toFloat())
			}
		}

		updateFloatingButtonColor()

		floatingButtonContainer?.addView(floatingButton, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		floatingProgressView = RadialProgressView(context)
		floatingProgressView?.setProgressColor(context.getColor(R.color.white))
		floatingProgressView?.scaleX = 0.1f
		floatingProgressView?.scaleY = 0.1f
		floatingProgressView?.alpha = 0f
		floatingProgressView?.visibility = View.GONE
		floatingProgressView?.setSize(AndroidUtilities.dp(22f))

		floatingButtonContainer?.addView(floatingProgressView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		searchTabsView = null

		if (!onlySelect && initialDialogsType == 0) {
			fragmentLocationContextView = FragmentContextView(context, this, true)
			fragmentLocationContextView?.layoutParams = createFrame(LayoutHelper.MATCH_PARENT, 38f, Gravity.TOP or Gravity.LEFT, 0f, -36f, 0f, 0f)

			contentView.addView(fragmentLocationContextView)

			fragmentContextView = object : FragmentContextView(context, this@DialogsActivity, false) {
				override fun playbackSpeedChanged(value: Float) {
					if (abs(value - 1.0f) > 0.001f || abs(value - 1.8f) > 0.001f) {
						getUndoView()?.showWithAction(0, if (abs(value - 1.0f) > 0.001f) UndoView.ACTION_PLAYBACK_SPEED_ENABLED else UndoView.ACTION_PLAYBACK_SPEED_DISABLED, value, null, null)
					}
				}
			}

			fragmentContextView?.layoutParams = createFrame(LayoutHelper.MATCH_PARENT, 38f, Gravity.TOP or Gravity.LEFT, 0f, -36f, 0f, 0f)

			contentView.addView(fragmentContextView)

			fragmentContextView?.setAdditionalContextView(fragmentLocationContextView)

			fragmentLocationContextView?.setAdditionalContextView(fragmentContextView)
		}
		else if (initialDialogsType == 3) {
			commentView?.onDestroy()

			commentView = object : ChatActivityEnterView(parentActivity!!, contentView, null, false, false) {
				override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
					if (ev.action == MotionEvent.ACTION_DOWN) {
						AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
					}

					return super.dispatchTouchEvent(ev)
				}

				override fun setTranslationY(translationY: Float) {
					super.setTranslationY(translationY)

					if (!commentViewAnimated) {
						return
					}

					commentViewBg?.translationY = translationY

					if (writeButtonContainer != null) {
						writeButtonContainer!!.translationY = translationY
					}
					if (selectedCountView != null) {
						selectedCountView!!.translationY = translationY
					}
				}
			}

			contentView.clipChildren = false
			contentView.clipToPadding = false

			commentView?.allowBlur = false
			commentView?.forceSmoothKeyboard(true)
			commentView?.setAllowStickersAndGifs(needAnimatedEmoji = true, needStickers = false, needGifs = false)
			commentView?.setForceShowSendButton(value = true, animated = false)
			commentView?.setPadding(0, 0, AndroidUtilities.dp(20f), 0)
			commentView?.visibility = View.GONE
			commentView?.getSendButton()?.alpha = 0f

			commentViewBg = View(parentActivity)
			commentViewBg?.setBackgroundColor(getThemedColor(Theme.key_chat_messagePanelBackground))

			contentView.addView(commentViewBg, createFrame(LayoutHelper.MATCH_PARENT, 1600f, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL, 0f, 0f, 0f, -1600f))
			contentView.addView(commentView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.BOTTOM))

			commentView?.setDelegate(object : ChatActivityEnterViewDelegate {
				override val sendAsPeers: TLRPC.TL_channels_sendAsPeers?
					get() = null

				override fun measureKeyboardHeight(): Int {
					return 0
				}

				override val contentViewHeight: Int
					get() = 0

				override fun hasForwardingMessages(): Boolean {
					return false
				}

				override fun onTrendingStickersShowed(show: Boolean) {
					// unused
				}

				override fun prepareMessageSending() {
					// unused
				}

				override fun hasScheduledMessages(): Boolean {
					return true
				}

				override fun openScheduledMessages() {
					// unused
				}

				override fun scrollToSendingMessage() {
					// unused
				}

				override fun onContextMenuClose() {
					// unused
				}

				override fun onContextMenuOpen() {
					// unused
				}

				override fun onEditTextScroll() {
					// unused
				}

				override fun onMessageSend(message: CharSequence?, notify: Boolean, scheduleDate: Int) {
					if (delegate == null || selectedDialogs.isEmpty()) {
						return
					}

					delegate?.didSelectDialogs(this@DialogsActivity, selectedDialogs, message, false)
				}

				override fun onSwitchRecordMode(video: Boolean) {
					// unused
				}

				override fun onTextSelectionChanged(start: Int, end: Int) {
					// unused
				}

				override fun bottomPanelTranslationYChanged(translation: Float) {
					if (commentViewAnimated) {
						keyboardAnimator?.cancel()
						keyboardAnimator = null

						if (commentView != null) {
							commentView?.translationY = translation
							commentViewIgnoreTopUpdate = true
						}
					}
				}

				override fun onStickersExpandedChange() {}
				override fun onPreAudioVideoRecord() {}
				override fun onTextChanged(text: CharSequence?, bigChange: Boolean) {}
				override fun onTextSpansChanged(text: CharSequence?) {}
				override fun needSendTyping() {}
				override fun onAttachButtonHidden() {}
				override fun onAttachButtonShow() {}
				override fun onMessageEditEnd(loading: Boolean) {}
				override fun onWindowSizeChanged(size: Int) {}
				override fun onStickersTab(opened: Boolean) {}
				override fun didPressAttachButton() {}
				override fun needStartRecordVideo(state: Int, notify: Boolean, scheduleDate: Int) {}
				override fun needChangeVideoPreviewState(state: Int, seekProgress: Float) {}
				override fun needStartRecordAudio(state: Int) {}
				override fun needShowMediaBanHint() {}
				override fun onUpdateSlowModeButton(button: View?, show: Boolean, time: CharSequence?) {}
				override fun onSendLongClick() {}
				override fun onAudioVideoInterfaceUpdated() {}
			})

			writeButtonContainer = object : FrameLayout(context) {
				override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
					super.onInitializeAccessibilityNodeInfo(info)
					info.text = LocaleController.formatPluralString("AccDescrShareInChats", selectedDialogs.size)
					info.className = Button::class.java.name
					info.isLongClickable = true
					info.isClickable = true
				}
			}

			writeButtonContainer?.isFocusable = true
			writeButtonContainer?.isFocusableInTouchMode = true
			writeButtonContainer?.visibility = View.INVISIBLE
			writeButtonContainer?.scaleX = 0.2f
			writeButtonContainer?.scaleY = 0.2f
			writeButtonContainer?.alpha = 0.0f

			contentView.addView(writeButtonContainer, createFrame(60, 60f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, 6f, 10f))

			textPaint.textSize = AndroidUtilities.dp(12f).toFloat()
			textPaint.typeface = Theme.TYPEFACE_BOLD

			selectedCountView = object : View(context) {
				override fun onDraw(canvas: Canvas) {
					val text = String.format(Locale.getDefault(), "%d", max(1, selectedDialogs.size))
					val textSize = ceil(textPaint.measureText(text).toDouble()).toInt()
					val size = max(AndroidUtilities.dp(16f) + textSize, AndroidUtilities.dp(24f))
					val cx = measuredWidth / 2

					textPaint.color = getThemedColor(Theme.key_dialogRoundCheckBoxCheck)
					paint.color = getThemedColor(if (Theme.isCurrentThemeDark()) Theme.key_voipgroup_inviteMembersBackground else Theme.key_dialogBackground)
					rect.set((cx - size / 2).toFloat(), 0f, (cx + size / 2).toFloat(), measuredHeight.toFloat())

					canvas.drawRoundRect(rect, AndroidUtilities.dp(12f).toFloat(), AndroidUtilities.dp(12f).toFloat(), paint)

					paint.color = getThemedColor(Theme.key_dialogRoundCheckBox)

					rect.set((cx - size / 2 + AndroidUtilities.dp(2f)).toFloat(), AndroidUtilities.dp(2f).toFloat(), (cx + size / 2 - AndroidUtilities.dp(2f)).toFloat(), (measuredHeight - AndroidUtilities.dp(2f)).toFloat())

					canvas.drawRoundRect(rect, AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(), paint)
					canvas.drawText(text, (cx - textSize / 2).toFloat(), AndroidUtilities.dp(16.2f).toFloat(), textPaint)
				}
			}

			selectedCountView?.alpha = 0.0f
			selectedCountView?.scaleX = 0.2f
			selectedCountView?.scaleY = 0.2f

			contentView.addView(selectedCountView, createFrame(42, 24f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, -8f, 9f))

			val writeButtonBackground = FrameLayout(context)

			val writeButtonDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56f), ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.darker_brand, null))

			writeButtonBackground.background = writeButtonDrawable
			writeButtonBackground.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

			writeButtonBackground.outlineProvider = object : ViewOutlineProvider() {
				@SuppressLint("NewApi")
				override fun getOutline(view: View, outline: Outline) {
					outline.setOval(0, 0, AndroidUtilities.dp(56f), AndroidUtilities.dp(56f))
				}
			}

			writeButtonBackground.setOnClickListener {
				if (delegate == null || selectedDialogs.isEmpty()) {
					return@setOnClickListener
				}

				delegate?.didSelectDialogs(this@DialogsActivity, selectedDialogs, commentView?.fieldText, false)
			}

			writeButtonBackground.setOnLongClickListener {
				if (isNextButton) {
					return@setOnLongClickListener false
				}

				onSendLongClick(writeButtonBackground)

				true
			}

			writeButton = arrayOfNulls(2)

			for (a in 0..1) {
				writeButton!![a] = ImageView(context)
				writeButton!![a]?.setImageResource(if (a == 1) R.drawable.msg_arrow_forward else R.drawable.arrow_up)
				writeButton!![a]?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.SRC_IN)
				writeButton!![a]?.scaleType = ImageView.ScaleType.CENTER
				writeButtonBackground.addView(writeButton!![a], createFrame(56, 56, Gravity.CENTER))
			}

			AndroidUtilities.updateViewVisibilityAnimated(writeButton!![0], true, 0.5f, false)
			AndroidUtilities.updateViewVisibilityAnimated(writeButton!![1], false, 0.5f, false)

			writeButtonContainer?.addView(writeButtonBackground, createFrame(56, 56f, Gravity.LEFT or Gravity.TOP, 2f, 0f, 0f, 0f))
		}

		if (filterTabsView != null) {
			contentView.addView(filterTabsView, createFrame(LayoutHelper.MATCH_PARENT, 44f))
		}

		if (!onlySelect) {
			val layoutParams = createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat())

			if (inPreviewMode) {
				layoutParams.topMargin = AndroidUtilities.statusBarHeight
			}

			contentView.addView(actionBar, layoutParams)

			animatedStatusView = AnimatedStatusView(context, 20, 60)

			contentView.addView(animatedStatusView, createFrame(20, 20, Gravity.LEFT or Gravity.TOP))
		}

		for (a in 0..1) {
			undoView[a] = object : UndoView(context) {
				override fun setTranslationY(translationY: Float) {
					super.setTranslationY(translationY)

					if (this == undoView[0] && undoView[1]?.visibility != VISIBLE) {
						additionalFloatingTranslation = measuredHeight + AndroidUtilities.dp(8f) - translationY

						if (additionalFloatingTranslation < 0) {
							additionalFloatingTranslation = 0f
						}

						if (!floatingHidden) {
							updateFloatingButtonOffset()
						}
					}
				}

				override fun canUndo(): Boolean {
					viewPages?.forEach {
						if (it?.dialogsItemAnimator?.isRunning == true) {
							return false
						}
					}

					return true
				}

				override fun onRemoveDialogAction(currentDialogId: Long, action: Int) {
					if (action == ACTION_DELETE || action == ACTION_DELETE_FEW) {
						debugLastUpdateAction = 1

						setDialogsListFrozen(true)

						if (frozenDialogsList != null) {
							var selectedIndex = -1

							for (i in frozenDialogsList!!.indices) {
								if (frozenDialogsList!![i].id == currentDialogId) {
									selectedIndex = i
									break
								}
							}

							if (selectedIndex >= 0) {
								val dialog = frozenDialogsList!!.removeAt(selectedIndex)

								viewPages?.firstOrNull()?.dialogsAdapter?.notifyDataSetChanged()

								val finalSelectedIndex = selectedIndex

								AndroidUtilities.runOnUIThread {
									if (frozenDialogsList != null) {
										frozenDialogsList?.add(finalSelectedIndex, dialog)
										viewPages?.firstOrNull()?.dialogsAdapter?.notifyItemInserted(finalSelectedIndex)
										dialogInsertFinished = 2
									}
								}
							}
							else {
								setDialogsListFrozen(false)
							}
						}
					}
				}
			}

			contentView.addView(undoView[a], createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM or Gravity.LEFT, 8f, 0f, 8f, 8f))
		}

		if (folderId != 0) {
			viewPages?.firstOrNull()?.listView?.setGlowColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))
		}

		if (!onlySelect && initialDialogsType == 0) {
			blurredView = object : View(context) {
				override fun setAlpha(alpha: Float) {
					super.setAlpha(alpha)
					fragmentView?.invalidate()
				}
			}

			blurredView?.foreground = ColorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhite), 100))
			blurredView?.isFocusable = false
			blurredView?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

			blurredView?.setOnClickListener {
				finishPreviewFragment()
			}

			blurredView?.visibility = View.GONE
			blurredView?.fitsSystemWindows = true

			contentView.addView(blurredView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		}

		actionBarDefaultPaint.color = ResourcesCompat.getColor(context.resources, R.color.background, null)

		if (inPreviewMode) {
			val currentUser = userConfig.getCurrentUser()

			avatarContainer = ChatAvatarContainer(actionBar!!.context, null, false)
			avatarContainer?.setTitle(getUserName(currentUser))
			avatarContainer?.setSubtitle(LocaleController.formatUserStatus(currentAccount, currentUser))
			avatarContainer?.setUserAvatar(currentUser, true)
			avatarContainer?.setOccupyStatusBar(false)
			avatarContainer?.setLeftPadding(AndroidUtilities.dp(10f))

			actionBar?.addView(avatarContainer, 0, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 40f, 0f))

			floatingButton?.visibility = View.INVISIBLE

			actionBar?.occupyStatusBar = false
			actionBar?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

			if (fragmentContextView != null) {
				contentView.removeView(fragmentContextView)
			}

			if (fragmentLocationContextView != null) {
				contentView.removeView(fragmentLocationContextView)
			}
		}

		searchIsShowed = false

		updateFilterTabs(force = false, animated = false)

		if (searchString != null) {
			showSearch(show = true, startFromDownloads = false, animated = false)
			actionBar?.openSearchField(searchString, false)
		}
		else if (initialSearchString != null) {
			showSearch(show = true, startFromDownloads = false, animated = false)
			actionBar?.openSearchField(initialSearchString, false)
			initialSearchString = null
			filterTabsView?.translationY = -AndroidUtilities.dp(44f).toFloat()
		}
		else {
			showSearch(show = false, startFromDownloads = false, animated = false)
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			FilesMigrationService.checkBottomSheet(this)
		}

		actionBar?.setDrawBlurBackground(contentView)

		return fragmentView
	}

	fun showSelectStatusDialog() {
		if (selectAnimatedEmojiDialog != null || SharedConfig.appLocked) {
			return
		}

		val popup = arrayOfNulls<SelectAnimatedEmojiDialogWindow>(1)
		val user = userConfig.getCurrentUser()
		var xoff = 0
		var yoff = 0
		val actionBarTitle = actionBar!!.titleTextView

		if (actionBarTitle?.rightDrawable != null) {
			statusDrawable?.play()

			AndroidUtilities.rectTmp2.set(actionBarTitle.rightDrawable!!.bounds)
			AndroidUtilities.rectTmp2.offset(actionBarTitle.x.toInt(), actionBarTitle.y.toInt())

			yoff = -(actionBar!!.height - AndroidUtilities.rectTmp2.centerY()) - AndroidUtilities.dp(16f)
			xoff = AndroidUtilities.rectTmp2.centerX() - AndroidUtilities.dp(16f)

			animatedStatusView?.translate(AndroidUtilities.rectTmp2.centerX().toFloat(), AndroidUtilities.rectTmp2.centerY().toFloat())
		}

		val popupLayout = object : SelectAnimatedEmojiDialog(this@DialogsActivity, context!!, true, xoff, TYPE_EMOJI_STATUS) {
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

				@Suppress("NAME_SHADOWING") val user = messagesController.getUser(userConfig.getClientUserId())

				if (user != null) {
					user.emoji_status = req.emoji_status

					notificationCenter.postNotificationName(NotificationCenter.userEmojiStatusUpdated, user)

					messagesController.updateEmojiStatusUntilUpdate(user.id, user.emoji_status)
				}

				if (documentId != null) {
					animatedStatusView!!.animateChange(VisibleReaction.fromCustomEmoji(documentId))
				}

				connectionsManager.sendRequest(req)

				if (popup[0] != null) {
					selectAnimatedEmojiDialog = null
					popup[0]?.dismiss()
				}
			}
		}

		if (user != null && user.emoji_status is TLRPC.TL_emojiStatusUntil && (user.emoji_status as TLRPC.TL_emojiStatusUntil).until > (System.currentTimeMillis() / 1000).toInt()) {
			popupLayout.setExpireDateHint((user.emoji_status as TLRPC.TL_emojiStatusUntil).until)
		}

		popupLayout.setSelected((statusDrawable?.drawable as? AnimatedEmojiDrawable)?.documentId)
		popupLayout.setSaveState(1)
		popupLayout.setScrimDrawable(statusDrawable, actionBarTitle)

		selectAnimatedEmojiDialog = object : SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
			override fun dismiss() {
				super.dismiss()
				selectAnimatedEmojiDialog = null
			}
		}

		popup[0] = selectAnimatedEmojiDialog
		popup[0]?.showAsDropDown(actionBar!!, AndroidUtilities.dp(16f), yoff, Gravity.TOP)
		popup[0]?.dimBehind()
	}

	private fun updateCommentView() {
		if (!commentViewAnimated || commentView == null) {
			return
		}

		val top = commentView!!.top

		if (commentViewPreviousTop > 0 && abs(top - commentViewPreviousTop) > AndroidUtilities.dp(20f) && !commentView!!.isPopupShowing) {
			if (commentViewIgnoreTopUpdate) {
				commentViewIgnoreTopUpdate = false
				commentViewPreviousTop = top
				return
			}

			keyboardAnimator?.cancel()

			keyboardAnimator = ValueAnimator.ofFloat((commentViewPreviousTop - top).toFloat(), 0f)

			keyboardAnimator?.addUpdateListener {
				commentView?.translationY = it.animatedValue as Float
			}

			keyboardAnimator?.duration = AdjustPanLayoutHelper.keyboardDuration
			keyboardAnimator?.interpolator = AdjustPanLayoutHelper.keyboardInterpolator
			keyboardAnimator?.start()
		}

		commentViewPreviousTop = top
	}

	private fun updateContextViewPosition() {
		var filtersTabsHeight = 0f

		if (filterTabsView != null && filterTabsView?.visibility != View.GONE) {
			filtersTabsHeight = filterTabsView!!.measuredHeight.toFloat()
		}

		var searchTabsHeight = 0f

		if (searchTabsView != null && searchTabsView?.visibility != View.GONE) {
			searchTabsHeight = searchTabsView!!.measuredHeight.toFloat()
		}

		if (fragmentContextView != null) {
			var from = 0f

			if (fragmentLocationContextView != null && fragmentLocationContextView!!.visibility == View.VISIBLE) {
				from += AndroidUtilities.dp(36f).toFloat()
			}

			fragmentContextView?.translationY = from + fragmentContextView!!.getTopPadding() + actionBar!!.translationY + filtersTabsHeight * (1f - searchAnimationProgress) + searchTabsHeight * searchAnimationProgress + tabsYOffset
		}

		if (fragmentLocationContextView != null) {
			var from = 0f

			if (fragmentContextView != null && fragmentContextView?.visibility == View.VISIBLE) {
				from += AndroidUtilities.dp(fragmentContextView!!.styleHeight.toFloat()) + fragmentContextView!!.getTopPadding()
			}

			fragmentLocationContextView?.translationY = from + fragmentLocationContextView!!.getTopPadding() + actionBar!!.translationY + filtersTabsHeight * (1f - searchAnimationProgress) + searchTabsHeight * searchAnimationProgress + tabsYOffset
		}
	}

	private fun updateFiltersView(users: ArrayList<Any>?, dates: ArrayList<DateData>?, archive: Boolean, animated: Boolean) {
		if (!searchIsShowed || onlySelect) {
			return
		}

		@Suppress("NAME_SHADOWING") var archive = archive
		// var hasMediaFilter = false
		var hasUserFilter = false
		var hasDateFilter = false
		var hasArchiveFilter = false
		val currentSearchFilters = searchViewPager!!.currentSearchFilters

		for (i in currentSearchFilters.indices) {
			if (currentSearchFilters[i].isMedia) {
				// unused
				// hasMediaFilter = true
			}
			else if (currentSearchFilters[i].filterType == FiltersView.FILTER_TYPE_CHAT) {
				hasUserFilter = true
			}
			else if (currentSearchFilters[i].filterType == FiltersView.FILTER_TYPE_DATE) {
				hasDateFilter = true
			}
			else if (currentSearchFilters[i].filterType == FiltersView.FILTER_TYPE_ARCHIVE) {
				hasArchiveFilter = true
			}
		}

		if (hasArchiveFilter) {
			archive = false
		}

		var visible = false
		val hasUsersOrDates = !users.isNullOrEmpty() || !dates.isNullOrEmpty() || archive

		if (hasUsersOrDates) {
			val finalUsers = if (!users.isNullOrEmpty() && !hasUserFilter) users else null
			val finalDates = if (!dates.isNullOrEmpty() && !hasDateFilter) dates else null

			if (finalUsers != null || finalDates != null || archive) {
				visible = true
				filtersView?.setUsersAndDates(finalUsers, finalDates, archive)
			}
		}

		if (!visible) {
			filtersView?.setUsersAndDates(null, null, false)
		}

		if (!animated) {
			filtersView?.adapter?.notifyDataSetChanged()
		}

		searchTabsView?.hide(visible, true)

		filtersView?.isEnabled = visible
		filtersView?.visibility = View.VISIBLE
	}

	private fun addSearchFilter(filter: MediaFilterData) {
		if (!searchIsShowed) {
			return
		}

		val currentSearchFilters = searchViewPager!!.currentSearchFilters

		if (currentSearchFilters.isNotEmpty()) {
			for (i in currentSearchFilters.indices) {
				if (filter.isSameType(currentSearchFilters[i])) {
					return
				}
			}
		}

		currentSearchFilters.add(filter)

		actionBar?.setSearchFilter(filter)
		actionBar?.setSearchFieldText("")

		updateFiltersView(null, null, archive = false, animated = true)
	}

	private fun createActionMode(tag: String?) {
		val context = context ?: return

		if (actionBar?.actionModeIsExist(tag) == true) {
			return
		}

		val actionMode = actionBar!!.createActionMode(tag)
		actionMode.setBackgroundColor(Color.TRANSPARENT)
		actionMode.drawBlur = false

		selectedDialogsCountTextView = NumberTextView(actionMode.context)
		selectedDialogsCountTextView?.setTextSize(18)
		selectedDialogsCountTextView?.setTypeface(Theme.TYPEFACE_BOLD)
		selectedDialogsCountTextView?.setTextColor(context.getColor(R.color.brand))

		actionMode.addView(selectedDialogsCountTextView, createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0))

		selectedDialogsCountTextView?.setOnTouchListener { _, _ ->
			true
		}

		pinItem = actionMode.addItemWithWidth(pin, R.drawable.msg_pin, AndroidUtilities.dp(54f))
		muteItem = actionMode.addItemWithWidth(mute, R.drawable.msg_mute, AndroidUtilities.dp(54f))
		archive2Item = actionMode.addItemWithWidth(archive2, R.drawable.msg_archive, AndroidUtilities.dp(54f))
		deleteItem = actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54f), context.getString(R.string.Delete))

		val otherItem = actionMode.addItemWithWidth(0, R.drawable.overflow_menu, AndroidUtilities.dp(54f), context.getString(R.string.AccDescrMoreOptions))

		// archiveItem = otherItem.addSubItem(archive, R.drawable.msg_archive, context.getString(R.string.Archive))
		pin2Item = otherItem.addSubItem(pin2, R.drawable.msg_pin, context.getString(R.string.DialogPin))
		addToFolderItem = otherItem.addSubItem(add_to_folder, R.drawable.msg_addfolder, context.getString(R.string.FilterAddTo))
		removeFromFolderItem = otherItem.addSubItem(remove_from_folder, R.drawable.msg_removefolder, context.getString(R.string.FilterRemoveFrom))
		readItem = otherItem.addSubItem(read, R.drawable.msg_markread, context.getString(R.string.MarkAsRead))
		clearItem = otherItem.addSubItem(clear, R.drawable.msg_clear, context.getString(R.string.ClearHistory))
		blockItem = otherItem.addSubItem(block, R.drawable.msg_block, context.getString(R.string.BlockUser))

		actionModeViews.add(pinItem)
		actionModeViews.add(archive2Item)
		actionModeViews.add(muteItem)
		actionModeViews.add(deleteItem)
		actionModeViews.add(otherItem)

		if (tag == null) {
			actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
				override fun onItemClick(id: Int) {
					if (id == SearchViewPager.forwardItemId || id == SearchViewPager.gotoItemId || id == SearchViewPager.deleteItemId && searchViewPager != null) {
						searchViewPager?.onActionBarItemClick(id)
						return
					}

					if (id == ActionBar.BACK_BUTTON) {
						if (filterTabsView != null && filterTabsView!!.isEditing) {
							filterTabsView?.setIsEditing(false)
							showDoneItem(false)
						}
						else if (actionBar!!.isActionModeShowed) {
							if (searchViewPager != null && searchViewPager!!.visibility == View.VISIBLE && searchViewPager!!.actionModeShowing()) {
								searchViewPager?.hideActionMode()
							}
							else {
								hideActionMode(true)
							}
						}
						else if (onlySelect || folderId != 0) {
							finishFragment()
						}
					}
					else if (id == 1) {
						if (parentActivity == null) {
							return
						}

						SharedConfig.appLocked = true
						SharedConfig.saveConfig()

						val position = IntArray(2)

						passcodeItem?.getLocationInWindow(position)

						(parentActivity as LaunchActivity).showPasscodeActivity(fingerprint = false, animated = true, x = position[0] + passcodeItem!!.measuredWidth / 2, y = position[1] + passcodeItem!!.measuredHeight / 2, onShow = { passcodeItem!!.alpha = 1.0f }) { passcodeItem!!.alpha = 0.0f }

						updatePasscodeButton()
					}
					else if (id == 2) {
						presentFragment(ProxyListActivity())
					}
					else if (id == 3) {
						showSearch(show = true, startFromDownloads = true, animated = true)
						actionBar?.openSearchField(true)
					}
					else if (id >= 10 && id < 10 + UserConfig.MAX_ACCOUNT_COUNT) {
						if (parentActivity == null) {
							return
						}

						val oldDelegate = delegate

						val launchActivity = parentActivity as LaunchActivity
						launchActivity.switchToAccount(id - 10)

						val dialogsActivity = DialogsActivity(arguments)
						dialogsActivity.setDelegate(oldDelegate)

						launchActivity.presentFragment(dialogsActivity, removeLast = false, forceWithoutAnimation = true)
					}
					else if (id == add_to_folder) {
						val sheet = FiltersListBottomSheet(this@DialogsActivity, selectedDialogs)

						sheet.setDelegate { filter: MessagesController.DialogFilter? ->
							val alwaysShow = FiltersListBottomSheet.getDialogsCount(this@DialogsActivity, filter, selectedDialogs, true, false)
							val currentCount = filter?.alwaysShow?.size ?: 0
							val totalCount = currentCount + alwaysShow.size

							if (totalCount > messagesController.dialogFiltersChatsLimitDefault && !userConfig.isPremium || totalCount > messagesController.dialogFiltersChatsLimitPremium) {
								showDialog(LimitReachedBottomSheet(this@DialogsActivity, LimitReachedBottomSheet.TYPE_CHATS_IN_FOLDER, currentAccount))
								return@setDelegate
							}

							if (filter != null) {
								if (alwaysShow.isNotEmpty()) {
									for (a in alwaysShow.indices) {
										filter.neverShow.remove(alwaysShow[a])
									}

									filter.alwaysShow.addAll(alwaysShow)
									FilterCreateActivity.saveFilterToServer(filter, filter.flags, filter.name, filter.alwaysShow, filter.neverShow, filter.pinnedDialogs, false, false, true, true, false, this@DialogsActivity, null)
								}

								val did = if (alwaysShow.size == 1) {
									alwaysShow[0]
								}
								else {
									0
								}

								getUndoView()?.showWithAction(did, UndoView.ACTION_ADDED_TO_FOLDER, alwaysShow.size, filter, null, null)
							}
							else {
								presentFragment(FilterCreateActivity(null, alwaysShow))
							}

							hideActionMode(true)
						}

						showDialog(sheet)
					}
					else if (id == remove_from_folder) {
						val filter = messagesController.dialogFilters[viewPages!![0]!!.selectedType]
						val neverShow = FiltersListBottomSheet.getDialogsCount(this@DialogsActivity, filter, selectedDialogs, false, false)
						val currentCount = filter.neverShow.size

						if (currentCount + neverShow.size > 100) {
							val alert = AlertsCreator.createSimpleAlert(parentActivity, context.getString(R.string.FilterAddToAlertFullTitle), context.getString(R.string.FilterAddToAlertFullText))?.create()

							if (alert != null) {
								showDialog(alert)
							}

							return
						}

						if (neverShow.isNotEmpty()) {
							filter.neverShow.addAll(neverShow)

							for (a in neverShow.indices) {
								val did = neverShow[a]
								filter.alwaysShow.remove(did)
								filter.pinnedDialogs.delete(did)
							}

							FilterCreateActivity.saveFilterToServer(filter, filter.flags, filter.name, filter.alwaysShow, filter.neverShow, filter.pinnedDialogs, false, false, true, false, false, this@DialogsActivity, null)
						}

						val did = if (neverShow.size == 1) {
							neverShow[0]
						}
						else {
							0
						}

						getUndoView()?.showWithAction(did, UndoView.ACTION_REMOVED_FROM_FOLDER, neverShow.size, filter, null, null)

						hideActionMode(false)
					}
					else if (id == pin || id == read || id == delete || id == clear || id == mute || id == archive || id == block || id == archive2 || id == pin2) {
						performSelectedDialogsAction(selectedDialogs, id, true)
					}
				}
			})
		}
	}

	private fun switchToCurrentSelectedMode(animated: Boolean) {
		viewPages?.forEach {
			it?.listView?.stopScroll()
		}

		val a = if (animated) 1 else 0
		val filter = messagesController.dialogFilters[viewPages!![a]!!.selectedType]

		if (filter.isDefault) {
			viewPages!![a]?.dialogsType = 0
			viewPages!![a]?.listView?.updatePullState()
		}
		else {
			if (viewPages!![if (a == 0) 1 else 0]?.dialogsType == 7) {
				viewPages!![a]?.dialogsType = 8
			}
			else {
				viewPages!![a]?.dialogsType = 7
			}

			viewPages!![a]?.listView?.setScrollEnabled(true)

			messagesController.selectDialogFilter(filter, if (viewPages!![a]?.dialogsType == 8) 1 else 0)
		}

		viewPages!![1]?.isLocked = filter.locked
		viewPages!![a]?.dialogsAdapter?.setDialogsType(viewPages!![a]!!.dialogsType)
		viewPages!![a]?.layoutManager?.scrollToPositionWithOffset(if (viewPages!![a]?.dialogsType == 0 && hasHiddenArchive()) 1 else 0, actionBar!!.translationY.toInt())

		checkListLoad(viewPages!![a])
	}

	private fun showScrollbars(show: Boolean) {
		if (viewPages == null || scrollBarVisible == show) {
			return
		}

		scrollBarVisible = show

		viewPages?.forEach {
			if (show) {
				it?.listView?.isScrollbarFadingEnabled = false
			}

			it?.listView?.isVerticalScrollBarEnabled = show

			if (show) {
				it?.listView?.isScrollbarFadingEnabled = true
			}
		}
	}

//	private fun scrollToFilterTab(index: Int) {
//		if (filterTabsView == null || viewPages!![0]?.selectedType == index) {
//			return
//		}
//
//		filterTabsView!!.selectTabWithId(index, 1.0f)
//
//		viewPages!![1]!!.selectedType = viewPages!![0]!!.selectedType
//		viewPages!![0]!!.selectedType = index
//
//		switchToCurrentSelectedMode(false)
//		switchToCurrentSelectedMode(true)
//		updateCounters(false)
//	}

	private fun updateFilterTabs(force: Boolean, animated: Boolean) {
		val context = context ?: return

		if (filterTabsView == null || inPreviewMode || searchIsShowed) {
			return
		}

		scrimPopupWindow?.dismiss()
		scrimPopupWindow = null

		val filters = messagesController.dialogFilters

		if (filters.size > 1) {
			if (force || filterTabsView!!.visibility != View.VISIBLE) {
				var animatedUpdateItems = animated

				if (filterTabsView!!.visibility != View.VISIBLE) {
					animatedUpdateItems = false
				}

				canShowFilterTabsView = true

				var updateCurrentTab = filterTabsView!!.isEmpty

				updateFilterTabsVisibility(animated)

				val id = filterTabsView!!.currentTabId
				val stableId = filterTabsView!!.currentTabStableId
				var selectWithStableId = false

				if (id != filterTabsView!!.defaultTabId && id >= filters.size) {
					filterTabsView!!.resetTabId()
					selectWithStableId = true
				}

				filterTabsView?.removeTabs()

				run {
					var a = 0
					val n = filters.size

					while (a < n) {
						if (filters[a].isDefault) {
							filterTabsView?.addTab(a, 0, context.getString(R.string.FilterAllChats), true, filters[a].locked)
						}
						else {
							filterTabsView?.addTab(a, filters[a].localId, filters[a].name, false, filters[a].locked)
						}

						a++
					}
				}

				if (stableId >= 0) {
					if (filterTabsView!!.getStableId(viewPages!![0]!!.selectedType) != stableId) {
						updateCurrentTab = true
						viewPages!![0]!!.selectedType = id
					}

					if (selectWithStableId) {
						filterTabsView!!.selectTabWithStableId(stableId)
					}
				}

				for (a in viewPages!!.indices) {
					if (viewPages!![a]!!.selectedType >= filters.size) {
						viewPages!![a]!!.selectedType = filters.size - 1
					}

					viewPages!![a]!!.listView!!.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING)
				}

				filterTabsView!!.finishAddingTabs(animatedUpdateItems)

				if (updateCurrentTab) {
					switchToCurrentSelectedMode(false)
				}

				if (filterTabsView!!.isLocked(filterTabsView!!.currentTabId)) {
					filterTabsView!!.selectFirstTab()
				}
			}
		}
		else {
			if (filterTabsView!!.visibility != View.GONE) {
				filterTabsView!!.setIsEditing(false)

				showDoneItem(false)

				maybeStartTracking = false

				if (startedTracking) {
					startedTracking = false

					viewPages!![0]?.translationX = 0f
					viewPages!![1]?.translationX = viewPages!![0]!!.measuredWidth.toFloat()
				}

				if (viewPages!![0]!!.selectedType != filterTabsView!!.defaultTabId) {
					viewPages!![0]!!.selectedType = filterTabsView!!.defaultTabId
					viewPages!![0]!!.dialogsAdapter!!.setDialogsType(0)
					viewPages!![0]!!.dialogsType = 0
					viewPages!![0]!!.dialogsAdapter?.notifyDataSetChanged()
				}

				viewPages!![1]!!.visibility = View.GONE
				viewPages!![1]!!.selectedType = 0
				viewPages!![1]!!.dialogsAdapter!!.setDialogsType(0)
				viewPages!![1]!!.dialogsType = 0
				viewPages!![1]!!.dialogsAdapter?.notifyDataSetChanged()

				canShowFilterTabsView = false

				updateFilterTabsVisibility(animated)

				viewPages?.forEach { viewPage ->
					if (viewPage?.dialogsType == 0 && viewPage.archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN && hasHiddenArchive()) {
						val p = viewPage.layoutManager?.findFirstVisibleItemPosition() ?: Int.MIN_VALUE

						if (p == 0 || p == 1) {
							viewPage.layoutManager!!.scrollToPositionWithOffset(1, 0)
						}
					}

					viewPage?.listView?.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_DEFAULT)
					viewPage?.listView?.requestLayout()
					viewPage?.requestLayout()
				}
			}
		}

		updateCounters(false)
	}

	override fun onPanTranslationUpdate(y: Float) {
		if (viewPages == null) {
			return
		}

		if (commentView != null && commentView!!.isPopupShowing) {
			fragmentView!!.translationY = y

			for (viewPage in viewPages!!) {
				viewPage?.translationY = 0f
			}

			if (!onlySelect) {
				actionBar?.translationY = 0f
			}

			searchViewPager?.translationY = 0f
		}
		else {
			for (viewPage in viewPages!!) {
				viewPage?.translationY = y
			}

			if (!onlySelect) {
				actionBar?.translationY = y
			}

			searchViewPager?.translationY = y
		}
	}

	override fun finishFragment() {
		super.finishFragment()
		scrimPopupWindow?.dismiss()
	}

	override fun onResume() {
		super.onResume()

		if (!parentLayout!!.isInPreviewMode && blurredView != null && blurredView!!.visibility == View.VISIBLE) {
			blurredView?.visibility = View.GONE
			blurredView?.background = null
		}

		if (viewPages != null) {
			for (viewPage in viewPages!!) {
				viewPage?.dialogsAdapter?.notifyDataSetChanged()
			}
		}

		commentView?.onResume()

		if (!onlySelect && folderId == 0) {
			mediaDataController.checkStickers(MediaDataController.TYPE_EMOJI)
		}

		searchViewPager?.onResume()

		val tosAccepted = if (!afterSignup) {
			userConfig.unacceptedTermsOfService == null
		}
		else {
			true
		}

		if (tosAccepted && checkPermission && !onlySelect) {
			val activity = parentActivity

			if (activity != null) {
				checkPermission = false

				val hasNotStoragePermission = (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
				val hasNotReadPhoneStatePermission = activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
				val hasNotNotificationPermission = Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

				AndroidUtilities.runOnUIThread({
					afterSignup = false

					if (hasNotStoragePermission || hasNotReadPhoneStatePermission || hasNotNotificationPermission) {
						askingForPermissions = true

						if (hasNotStoragePermission && activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
							if (activity is BasePermissionsActivity) {
								showDialog(activity.createPermissionErrorAlert(R.raw.permission_request_folder, activity.getString(R.string.PermissionStorageWithHint)).also { permissionDialog = it })
							}
						}
						else {
							askForPermissions()
						}
					}
				}, if (afterSignup) 4000L else 0L)
			}
		}
		else if (!onlySelect && XiaomiUtilities.isMIUI() && !XiaomiUtilities.isCustomPermissionGranted(XiaomiUtilities.OP_SHOW_WHEN_LOCKED)) {
			val parentActivity = parentActivity ?: return

			if (MessagesController.getGlobalNotificationsSettings().getBoolean("askedAboutMiuiLockscreen", false)) {
				return
			}

			showDialog(AlertDialog.Builder(parentActivity).setTopAnimation(R.raw.permission_request_apk, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, ResourcesCompat.getColor(parentActivity.resources, R.color.brand, null)).setMessage(parentActivity.getString(R.string.PermissionXiaomiLockscreen)).setPositiveButton(parentActivity.getString(R.string.PermissionOpenSettings)) { _, _ ->
				var intent = XiaomiUtilities.getPermissionManagerIntent()

				try {
					parentActivity.startActivity(intent)
				}
				catch (x: Exception) {
					try {
						intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
						intent.data = Uri.parse("package:" + ApplicationLoader.applicationContext.packageName)
						parentActivity.startActivity(intent)
					}
					catch (xx: Exception) {
						FileLog.e(xx)
					}
				}
			}.setNegativeButton(parentActivity.getString(R.string.ContactsPermissionAlertNotNow)) { _, _ ->
				MessagesController.getGlobalNotificationsSettings().edit().putBoolean("askedAboutMiuiLockscreen", true).commit()
			}.create())
		}

		showFiltersHint()

		if (viewPages != null) {
			for (a in viewPages!!.indices) {
				if (viewPages!![a]?.dialogsType == 0 && viewPages!![a]?.archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN && viewPages!![a]?.layoutManager?.findFirstVisibleItemPosition() == 0 && hasHiddenArchive()) {
					viewPages!![a]?.layoutManager?.scrollToPositionWithOffset(1, 0)
				}

				if (a == 0) {
					viewPages!![a]?.dialogsAdapter?.resume()
				}
				else {
					viewPages!![a]?.dialogsAdapter?.pause()
				}
			}
		}

		showNextSupportedSuggestion()

		Bulletin.addDelegate(this, object : Bulletin.Delegate {
			override fun onOffsetChange(offset: Float) {
				if (undoView[0] != null && undoView[0]?.visibility == View.VISIBLE) {
					return
				}

				additionalFloatingTranslation = offset

				if (additionalFloatingTranslation < 0) {
					additionalFloatingTranslation = 0f
				}

				if (!floatingHidden) {
					updateFloatingButtonOffset()
				}
			}

			override fun onShow(bulletin: Bulletin) {
				if (undoView[0] != null && undoView[0]?.visibility == View.VISIBLE) {
					undoView[0]?.hide(true, 2)
				}
			}
		})

		if (searchIsShowed) {
			AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
		}

		updateVisibleRows(0, false)
		updateProxyButton(animated = false, force = true)
		checkSuggestClearDatabase()
	}

	override fun presentFragment(fragment: BaseFragment): Boolean {
		val b = super.presentFragment(fragment)

		if (b) {
			viewPages?.forEach {
				it?.dialogsAdapter?.pause()
			}
		}

		return b
	}

	override fun onPause() {
		super.onPause()

		scrimPopupWindow?.dismiss()
		commentView?.onResume()
		undoView[0]?.hide(true, 0)

		Bulletin.removeDelegate(this)

		viewPages?.forEach {
			it?.dialogsAdapter?.pause()
		}
	}

	override fun onBackPressed(): Boolean {
		if (scrimPopupWindow != null) {
			scrimPopupWindow?.dismiss()
			return false
		}
		else if (filterTabsView != null && filterTabsView!!.isEditing) {
			filterTabsView?.setIsEditing(false)
			showDoneItem(false)
			return false
		}
		else if (actionBar != null && actionBar!!.isActionModeShowed) {
			if (searchViewPager?.visibility == View.VISIBLE) {
				searchViewPager?.hideActionMode()
				hideActionMode(true)
			}
			else {
				hideActionMode(true)
			}

			return false
		}
		else if (filterTabsView != null && filterTabsView!!.visibility == View.VISIBLE && !tabsAnimationInProgress && !filterTabsView!!.isAnimatingIndicator && !startedTracking && !filterTabsView!!.isFirstTabSelected) {
			filterTabsView?.selectFirstTab()
			return false
		}
		else if (commentView != null && commentView!!.isPopupShowing) {
			commentView?.hidePopup(true)
			return false
		}

		return super.onBackPressed()
	}

	override fun onBecomeFullyHidden() {
		if (closeSearchFieldOnHide) {
			actionBar?.closeSearchField()

			if (searchObject != null) {
				searchViewPager?.dialogsSearchAdapter?.putRecentSearch(searchDialogId, searchObject)
				searchObject = null
			}

			closeSearchFieldOnHide = false
		}

		if (filterTabsView?.visibility == View.VISIBLE && filterTabsViewIsVisible) {
			val scrollY = -actionBar!!.translationY.toInt()
			val actionBarHeight = ActionBar.getCurrentActionBarHeight()

			if (scrollY != 0 && scrollY != actionBarHeight) {
				if (scrollY < actionBarHeight / 2) {
					setScrollY(0f)
				}
				else if (viewPages!![0]!!.listView!!.canScrollVertically(1)) {
					setScrollY(-actionBarHeight.toFloat())
				}
			}
		}

		undoView[0]?.hide(true, 0)
	}

	override fun setInPreviewMode(value: Boolean) {
		super.setInPreviewMode(value)

		if (!value && avatarContainer != null) {
			actionBar?.background = null
			(actionBar?.layoutParams as? MarginLayoutParams)?.topMargin = 0
			actionBar?.removeView(avatarContainer)
			avatarContainer = null

			updateFilterTabs(force = false, animated = false)

			floatingButton?.visibility = View.VISIBLE

			val contentView = fragmentView as ContentView

			if (fragmentContextView != null) {
				contentView.addView(fragmentContextView)
			}

			if (fragmentLocationContextView != null) {
				contentView.addView(fragmentLocationContextView)
			}
		}
	}

	fun addOrRemoveSelectedDialog(did: Long, cell: View?): Boolean {
		return if (selectedDialogs.contains(did)) {
			selectedDialogs.remove(did)

			if (cell is DialogCell) {
				cell.setChecked(checked = false, animated = true)
			}
			else if (cell is ProfileSearchCell) {
				cell.setChecked(checked = false, animated = true)
			}

			false
		}
		else {
			selectedDialogs.add(did)

			if (cell is DialogCell) {
				cell.setChecked(checked = true, animated = true)
			}
			else if (cell is ProfileSearchCell) {
				cell.setChecked(checked = true, animated = true)
			}
			true
		}
	}

	fun search(query: String?, animated: Boolean) {
		showSearch(show = true, startFromDownloads = false, animated = animated)
		actionBar?.openSearchField(query, false)
	}

	private fun showSearch(show: Boolean, startFromDownloads: Boolean, animated: Boolean) {
		val context = context ?: return

		@Suppress("NAME_SHADOWING") var animated = animated

		if (initialDialogsType != 0 && initialDialogsType != 3) {
			animated = false
		}

		searchAnimator?.cancel()
		searchAnimator = null

		tabsAlphaAnimator?.cancel()
		tabsAlphaAnimator = null

		searchIsShowed = show

		(fragmentView as? SizeNotifierFrameLayout)?.invalidateBlur()

		if (show) {
			val onlyDialogsAdapter = if (searchFiltersWasShowed) {
				false
			}
			else {
				onlyDialogsAdapter()
			}

			searchViewPager?.showOnlyDialogsAdapter(onlyDialogsAdapter)

			whiteActionBar = !onlyDialogsAdapter

			if (whiteActionBar) {
				searchFiltersWasShowed = true
			}

			val contentView = fragmentView as ContentView

			if (searchTabsView == null && !onlyDialogsAdapter) {
				searchTabsView = searchViewPager?.createTabsView()

				var filtersViewPosition = -1

				if (filtersView != null) {
					for (i in 0 until contentView.childCount) {
						if (contentView.getChildAt(i) === filtersView) {
							filtersViewPosition = i
							break
						}
					}
				}

				if (filtersViewPosition > 0) {
					contentView.addView(searchTabsView, filtersViewPosition, createFrame(LayoutHelper.MATCH_PARENT, 44f))
				}
				else {
					contentView.addView(searchTabsView, createFrame(LayoutHelper.MATCH_PARENT, 44f))
				}
			}
			else if (searchTabsView != null && onlyDialogsAdapter) {
				(searchTabsView?.parent as? ViewGroup)?.removeView(searchTabsView)
				searchTabsView = null
			}

			val editText = searchItem?.searchField
			editText?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			editText?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
			editText?.setCursorColor(ResourcesCompat.getColor(context.resources, R.color.text, null))

			searchViewPager?.setKeyboardHeight((fragmentView as ContentView).keyboardHeight)
			searchViewPager?.clear()

			if (folderId != 0) {
				val filterData = MediaFilterData(R.drawable.chats_archive, context.getString(R.string.ArchiveSearchFilter), null, FiltersView.FILTER_TYPE_ARCHIVE)
				addSearchFilter(filterData)
			}
		}

		if (animated && searchViewPager!!.dialogsSearchAdapter.hasRecentSearch()) {
			AndroidUtilities.setAdjustResizeToNothing(parentActivity, classGuid)
		}
		else {
			AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
		}

		if (!show && filterTabsView != null && canShowFilterTabsView) {
			filterTabsView!!.visibility = View.VISIBLE
		}

		if (animated) {
			if (show) {
				searchViewPager?.visibility = View.VISIBLE
				searchViewPager?.reset()

				updateFiltersView(null, null, archive = false, animated = false)

				searchTabsView?.hide(hide = false, animated = false)
				searchTabsView?.visibility = View.VISIBLE
			}
			else {
				viewPages?.firstOrNull()?.listView?.visibility = View.VISIBLE
				viewPages?.firstOrNull()?.visibility = View.VISIBLE
			}


			setDialogsListFrozen(true)

			viewPages!![0]?.listView?.isVerticalScrollBarEnabled = false

			searchViewPager?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

			searchAnimator = AnimatorSet()

			val animators = ArrayList<Animator>()
			animators.add(ObjectAnimator.ofFloat(viewPages!![0], View.ALPHA, if (show) 0.0f else 1.0f))
			animators.add(ObjectAnimator.ofFloat(viewPages!![0], View.SCALE_X, if (show) 0.9f else 1.0f))
			animators.add(ObjectAnimator.ofFloat(viewPages!![0], View.SCALE_Y, if (show) 0.9f else 1.0f))
			animators.add(ObjectAnimator.ofFloat(searchViewPager, View.ALPHA, if (show) 1.0f else 0.0f))
			animators.add(ObjectAnimator.ofFloat(searchViewPager, View.SCALE_X, if (show) 1.0f else 1.05f))
			animators.add(ObjectAnimator.ofFloat(searchViewPager, View.SCALE_Y, if (show) 1.0f else 1.05f))

			if (passcodeItem != null) {
				animators.add(ObjectAnimator.ofFloat(passcodeItem!!.iconView, View.ALPHA, if (show) 0f else 1f))
			}

			if (downloadsItem != null) {
				if (show) {
					downloadsItem?.alpha = 0f
				}
				else {
					animators.add(ObjectAnimator.ofFloat(downloadsItem, View.ALPHA, 1f))
				}

				updateProxyButton(animated = false, force = false)
			}

			if (filterTabsView != null && filterTabsView!!.visibility == View.VISIBLE) {
				tabsAlphaAnimator = ObjectAnimator.ofFloat(filterTabsView!!.tabsContainer, View.ALPHA, if (show) 0f else 1f).setDuration(100)

				tabsAlphaAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						tabsAlphaAnimator = null
					}
				})
			}

			val valueAnimator = ValueAnimator.ofFloat(searchAnimationProgress, if (show) 1f else 0f)

			valueAnimator.addUpdateListener {
				setSearchAnimationProgress(it.animatedValue as Float)
			}

			animators.add(valueAnimator)

			searchAnimator?.playTogether(animators)
			searchAnimator?.duration = if (show) 200 else 180.toLong()
			searchAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT

			searchAnimationTabsDelayedCrossfade = if (filterTabsViewIsVisible) {
				val backgroundColor1 = ResourcesCompat.getColor(context.resources, R.color.background, null)
				val backgroundColor2 = ResourcesCompat.getColor(context.resources, R.color.background, null)
				val sum = abs(Color.red(backgroundColor1) - Color.red(backgroundColor2)) + abs(Color.green(backgroundColor1) - Color.green(backgroundColor2)) + abs(Color.blue(backgroundColor1) - Color.blue(backgroundColor2))
				sum / 255f > 0.3f
			}
			else {
				true
			}

			if (!show) {
				searchAnimator?.startDelay = 20

				if (tabsAlphaAnimator != null) {
					if (searchAnimationTabsDelayedCrossfade) {
						tabsAlphaAnimator?.startDelay = 80
						tabsAlphaAnimator?.duration = 100
					}
					else {
						tabsAlphaAnimator!!.duration = 180
					}
				}
			}

			searchAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					notificationCenter.onAnimationFinish(animationIndex)

					if (searchAnimator != animation) {
						return
					}

					setDialogsListFrozen(false)

					if (show) {
						viewPages!![0]?.listView?.hide()

						filterTabsView?.visibility = View.GONE
						searchWasFullyShowed = true
						AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
						searchItem?.visibility = View.GONE
					}
					else {
						searchItem?.collapseSearchFilters()
						whiteActionBar = false
						searchViewPager?.visibility = View.GONE
						searchTabsView?.visibility = View.GONE
						searchItem?.clearSearchFilters()
						searchViewPager?.clear()
						filtersView?.visibility = View.GONE
						viewPages!![0]?.listView?.show()

						if (!onlySelect) {
							hideFloatingButton(false)
						}

						searchWasFullyShowed = false
					}

					fragmentView?.requestLayout()
					setSearchAnimationProgress(if (show) 1f else 0f)

					viewPages!![0]?.listView?.isVerticalScrollBarEnabled = true
					searchViewPager?.background = null
					searchAnimator = null

					downloadsItem?.alpha = if (show) 0f else 1f
				}

				override fun onAnimationCancel(animation: Animator) {
					notificationCenter.onAnimationFinish(animationIndex)

					if (searchAnimator == animation) {
						if (show) {
							viewPages?.firstOrNull()?.listView?.hide()
						}
						else {
							viewPages?.firstOrNull()?.listView?.show()
						}

						searchAnimator = null
					}
				}
			})

			animationIndex = notificationCenter.setAnimationInProgress(animationIndex, null)

			searchAnimator?.start()

			tabsAlphaAnimator?.start()
		}
		else {
			setDialogsListFrozen(false)

			viewPages?.firstOrNull()?.let {
				if (show) {
					it.listView?.hide()
				}
				else {
					it.listView?.show()
				}

				it.alpha = if (show) 0.0f else 1.0f
				it.scaleX = if (show) 0.9f else 1.0f
				it.scaleY = if (show) 0.9f else 1.0f
			}

			searchViewPager?.alpha = if (show) 1.0f else 0.0f
			filtersView?.alpha = if (show) 1.0f else 0.0f
			searchViewPager?.scaleX = if (show) 1.0f else 1.1f
			searchViewPager?.scaleY = if (show) 1.0f else 1.1f

			if (filterTabsView?.visibility == View.VISIBLE) {
				filterTabsView?.translationY = if (show) -AndroidUtilities.dp(44f).toFloat() else 0f
				filterTabsView?.tabsContainer?.alpha = if (show) 0f else 1f
			}

			if (filterTabsView != null) {
				if (canShowFilterTabsView && !show) {
					filterTabsView?.visibility = View.VISIBLE
				}
				else {
					filterTabsView?.visibility = View.GONE
				}
			}

			searchViewPager?.visibility = if (show) View.VISIBLE else View.GONE
			setSearchAnimationProgress(if (show) 1f else 0f)
			fragmentView?.invalidate()
			downloadsItem?.alpha = if (show) 0f else 1f
		}

		if (initialSearchType >= 0) {
			searchViewPager?.setPosition(searchViewPager!!.getPositionForType(initialSearchType))
		}

		if (!show) {
			initialSearchType = -1
		}

		if (show && startFromDownloads) {
			searchViewPager?.showDownloads()
		}
	}

	fun onlyDialogsAdapter(): Boolean {
		return shouldShowBottomNavigationPanel

		// MARK: uncommenting following lines will enable panel with "Chats", "Media", etc tabs
//		int dialogsCount = getMessagesController().getTotalDialogsCount();
//		return onlySelect || !searchViewPager.dialogsSearchAdapter.hasRecentSearch() || dialogsCount <= 10;
	}

	private fun updateFilterTabsVisibility(animated: Boolean) {
		@Suppress("NAME_SHADOWING") var animated = animated

		if (isPaused || databaseMigrationHint != null) {
			animated = false
		}

		if (searchIsShowed) {
			filtersTabAnimator?.cancel()
			filterTabsViewIsVisible = canShowFilterTabsView
			filterTabsProgress = if (filterTabsViewIsVisible) 1f else 0f
			return
		}

		val visible = canShowFilterTabsView

		if (filterTabsViewIsVisible != visible) {
			filtersTabAnimator?.cancel()

			filterTabsViewIsVisible = visible

			if (animated) {
				if (visible) {
					if (filterTabsView?.visibility != View.VISIBLE) {
						filterTabsView?.visibility = View.VISIBLE
					}

					filtersTabAnimator = ValueAnimator.ofFloat(0f, 1f)
					filterTabsMoveFrom = AndroidUtilities.dp(44f).toFloat()
				}
				else {
					filtersTabAnimator = ValueAnimator.ofFloat(1f, 0f)
					filterTabsMoveFrom = max(0f, AndroidUtilities.dp(44f) + actionBar!!.translationY)
				}

				val animateFromScrollY = actionBar!!.translationY

				filtersTabAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						filtersTabAnimator = null
						scrollAdditionalOffset = AndroidUtilities.dp(44f) - filterTabsMoveFrom

						if (!visible) {
							filterTabsView?.visibility = View.GONE
						}

						fragmentView?.requestLayout()

						notificationCenter.onAnimationFinish(animationIndex)
					}
				})

				filtersTabAnimator?.addUpdateListener {
					filterTabsProgress = it.animatedValue as Float

					if (!visible) {
						setScrollY(animateFromScrollY * filterTabsProgress)
					}

					fragmentView?.invalidate()
				}

				filtersTabAnimator?.duration = 220
				filtersTabAnimator?.interpolator = CubicBezierInterpolator.DEFAULT

				animationIndex = notificationCenter.setAnimationInProgress(animationIndex, null)

				filtersTabAnimator?.start()

				fragmentView?.requestLayout()
			}
			else {
				filterTabsProgress = if (visible) 1f else 0f
				filterTabsView?.visibility = if (visible) View.VISIBLE else View.GONE
				fragmentView?.invalidate()
			}
		}
	}

	private fun setSearchAnimationProgress(progress: Float) {
		searchAnimationProgress = progress

		if (whiteActionBar) {
			var color1 = ResourcesCompat.getColor(context!!.resources, R.color.brand, null)
			actionBar?.setItemsColor(ColorUtils.blendARGB(color1, color1, searchAnimationProgress), false)
			actionBar?.setItemsColor(ColorUtils.blendARGB(color1, color1, searchAnimationProgress), true)

			color1 = ResourcesCompat.getColor(context!!.resources, R.color.divider, null)

			val color2 = ResourcesCompat.getColor(context!!.resources, R.color.divider, null)

			actionBar?.setItemsBackgroundColor(ColorUtils.blendARGB(color1, color2, searchAnimationProgress), false)
		}

		fragmentView?.invalidate()

		updateContextViewPosition()
	}

	private fun findAndUpdateCheckBox(dialogId: Long, checked: Boolean) {
		val viewPages = viewPages ?: return

		for (viewPage in viewPages) {
			val count = viewPage?.listView?.childCount ?: continue

			for (a in 0 until count) {
				val child = viewPage.listView?.getChildAt(a)

				if (child is DialogCell) {
					if (child.dialogId == dialogId) {
						child.setChecked(checked, true)
						break
					}
				}
			}
		}
	}

	private fun checkListLoad(viewPage: ViewPage?) {
		if (viewPage == null || tabsAnimationInProgress || startedTracking || filterTabsView != null && filterTabsView?.visibility == View.VISIBLE && filterTabsView?.isAnimatingIndicator == true) {
			return
		}

		val firstVisibleItem = viewPage.layoutManager!!.findFirstVisibleItemPosition()
		val lastVisibleItem = viewPage.layoutManager!!.findLastVisibleItemPosition()
		val visibleItemCount = abs(viewPage.layoutManager!!.findLastVisibleItemPosition() - firstVisibleItem) + 1

		if (lastVisibleItem != RecyclerView.NO_POSITION) {
			val holder = viewPage.listView?.findViewHolderForAdapterPosition(lastVisibleItem)

			if ((holder?.itemViewType == 11).also { floatingForceVisible = it }) {
				hideFloatingButton(false)
			}
		}
		else {
			floatingForceVisible = false
		}

		var loadArchived = false
		var loadArchivedFromCache = false
		var load = false
		var loadFromCache = false

		if (viewPage.dialogsType == 7 || viewPage.dialogsType == 8) {
			val dialogFilters = messagesController.dialogFilters

			if (viewPage.selectedType >= 0 && viewPage.selectedType < dialogFilters.size) {
				val filter = messagesController.dialogFilters[viewPage.selectedType]

				if (filter.flags and MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED == 0) {
					if (visibleItemCount > 0 && lastVisibleItem >= getDialogsArray(currentAccount, viewPage.dialogsType, 1, dialogsListFrozen).size - 10 || visibleItemCount == 0 && !messagesController.isDialogsEndReached(1)) {
						loadArchivedFromCache = !messagesController.isDialogsEndReached(1)

						if (loadArchivedFromCache || !messagesController.isServerDialogsEndReached(1)) {
							loadArchived = true
						}
					}
				}
			}
		}
		if (visibleItemCount > 0 && lastVisibleItem >= getDialogsArray(currentAccount, viewPage.dialogsType, folderId, dialogsListFrozen).size - 10 || visibleItemCount == 0 && (viewPage.dialogsType == 7 || viewPage.dialogsType == 8) && !messagesController.isDialogsEndReached(folderId)) {
			loadFromCache = !messagesController.isDialogsEndReached(folderId)

			if (loadFromCache || !messagesController.isServerDialogsEndReached(folderId)) {
				load = true
			}
		}

		if (load || loadArchived) {
			AndroidUtilities.runOnUIThread {
				if (load) {
					messagesController.loadDialogs(folderId, -1, 100, loadFromCache)
				}

				if (loadArchived) {
					messagesController.loadDialogs(1, -1, 100, loadArchivedFromCache)
				}
			}
		}
	}

	private fun onItemClick(view: View, position: Int, adapter: RecyclerView.Adapter<*>?) {
		if (parentActivity == null) {
			return
		}

		var dialogId: Long = 0
		var messageId = 0
		var isGlobalSearch = false
		var folderId = 0
		var filterId = 0

		if (adapter is DialogsAdapter) {
			val dialogsType = adapter.getDialogsType()

			if (dialogsType == 7 || dialogsType == 8) {
				val dialogFilter = messagesController.selectedDialogFilter[if (dialogsType == 7) 0 else 1]
				filterId = dialogFilter?.id ?: 0
			}

			val `object` = adapter.getItem(position)

			if (`object` is User) {
				dialogId = `object`.id
			}
			else if (`object` is TLRPC.Dialog) {
				folderId = `object`.folder_id

				if (`object` is TLRPC.TL_dialogFolder) {
					if (actionBar?.isActionModeShowed(null) == true) {
						return
					}

					val args = Bundle()
					args.putInt("folderId", `object`.folder.id)

					presentFragment(DialogsActivity(args))

					return
				}

				dialogId = `object`.id

				if (actionBar?.isActionModeShowed(null) == true) {
					showOrUpdateActionMode(dialogId, view)
					return
				}
			}
			else if (`object` is TLRPC.TL_recentMeUrlChat) {
				dialogId = -`object`.chat_id
			}
			else if (`object` is TLRPC.TL_recentMeUrlUser) {
				dialogId = `object`.user_id
			}
			else if (`object` is TLRPC.TL_recentMeUrlChatInvite) {
				val invite = `object`.chat_invite

				if (invite.chat == null && (!invite.channel || invite.megagroup) || invite.chat != null && (!ChatObject.isChannel(invite.chat) || invite.chat.megagroup)) {
					var hash = `object`.url
					val index = hash.indexOf('/')

					if (index > 0) {
						hash = hash.substring(index + 1)
					}

					parentActivity?.let {
						showDialog(JoinGroupAlert(it, invite, hash, this@DialogsActivity, null))
					}

					return
				}
				else {
					dialogId = if (invite.chat != null) {
						-invite.chat.id
					}
					else {
						return
					}
				}
			}
			else if (`object` is TLRPC.TL_recentMeUrlStickerSet) {
				val stickerSet = `object`.set.set

				val set = TLRPC.TL_inputStickerSetID()
				set.id = stickerSet.id
				set.access_hash = stickerSet.access_hash

				showDialog(StickersAlert(parentActivity, this@DialogsActivity, set, null, null))

				return
			}
			else if (`object` is TLRPC.TL_recentMeUrlUnknown) {
				return
			}
			else {
				return
			}
		}
		else if (adapter === searchViewPager!!.dialogsSearchAdapter) {
			val obj = searchViewPager!!.dialogsSearchAdapter.getItem(position)

			isGlobalSearch = searchViewPager!!.dialogsSearchAdapter.isGlobalSearch(position)

			if (obj is User) {
				dialogId = obj.id

				if (!onlySelect) {
					searchDialogId = dialogId
					searchObject = obj
				}
			}
			else if (obj is TLRPC.Chat) {
				dialogId = -obj.id
				if (!onlySelect) {
					searchDialogId = dialogId
					searchObject = obj
				}
			}
			else if (obj is TLRPC.EncryptedChat) {
				dialogId = DialogObject.makeEncryptedDialogId(obj.id.toLong())

				if (!onlySelect) {
					searchDialogId = dialogId
					searchObject = obj
				}
			}
			else if (obj is MessageObject) {
				dialogId = obj.dialogId
				messageId = obj.id
				searchViewPager?.dialogsSearchAdapter?.addHashtagsFromMessage(searchViewPager!!.dialogsSearchAdapter.lastSearchString)
			}
			else if (obj is String) {
				if (searchViewPager?.dialogsSearchAdapter?.isHashtagSearch == true) {
					actionBar?.openSearchField(obj, false)
				}
				else if (obj != "section") {
					val activity = NewContactActivity()
					activity.setInitialPhoneNumber(obj, true)
					presentFragment(activity)
				}
			}

			if (dialogId != 0L && actionBar!!.isActionModeShowed) {
				if (actionBar?.isActionModeShowed(ACTION_MODE_SEARCH_DIALOGS_TAG) == true && messageId == 0 && !isGlobalSearch) {
					showOrUpdateActionMode(dialogId, view)
				}

				return
			}
		}

		if (dialogId == 0L) {
			return
		}

		if (onlySelect) {
			if (!validateSlowModeDialog(dialogId)) {
				return
			}

			if ((selectedDialogs.isNotEmpty() || initialDialogsType == 3) && selectAlertString != null) {
				if (!selectedDialogs.contains(dialogId) && !checkCanWrite(dialogId)) {
					return
				}

				val checked = addOrRemoveSelectedDialog(dialogId, view)

				if (adapter === searchViewPager?.dialogsSearchAdapter) {
					actionBar?.closeSearchField()
					findAndUpdateCheckBox(dialogId, checked)
				}

				updateSelectedCount()
			}
			else {
				didSelectResult(dialogId, useAlert = true, param = false)
			}
		}
		else {
			val args = Bundle()

			if (DialogObject.isEncryptedDialog(dialogId)) {
				args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId))
			}
			else if (DialogObject.isUserDialog(dialogId)) {
				args.putLong("user_id", dialogId)
			}
			else {
				var did = dialogId

				if (messageId != 0) {
					val chat = messagesController.getChat(-did)
					if (chat?.migrated_to != null) {
						args.putLong("migrated_to", did)
						did = -chat.migrated_to.channel_id
					}
				}

				args.putLong("chat_id", -did)
			}

			if (messageId != 0) {
				args.putInt("message_id", messageId)
			}
			else if (!isGlobalSearch) {
				closeSearch()
			}
			else {
				if (searchObject != null) {
					searchViewPager?.dialogsSearchAdapter?.putRecentSearch(searchDialogId, searchObject)
					searchObject = null
				}
			}

			args.putInt("dialog_folder_id", folderId)
			args.putInt("dialog_filter_id", filterId)

			if (AndroidUtilities.isTablet()) {
				if (openedDialogId == dialogId && adapter !== searchViewPager?.dialogsSearchAdapter) {
					return
				}

				viewPages?.forEach {
					it?.dialogsAdapter?.setOpenedDialogId(dialogId.also { did -> openedDialogId = did })
				}

				updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG)
			}

			if (searchViewPager?.actionModeShowing() == true) {
				searchViewPager?.hideActionMode()
			}

			if (searchString != null) {
				if (messagesController.checkCanOpenChat(args, this@DialogsActivity)) {
					notificationCenter.postNotificationName(NotificationCenter.closeChats)
					presentFragment(ChatActivity(args))
				}
			}
			else {
				slowedReloadAfterDialogClick = true

				if (messagesController.checkCanOpenChat(args, this@DialogsActivity)) {
					val chatActivity = ChatActivity(args)

					if (adapter is DialogsAdapter && DialogObject.isUserDialog(dialogId) && messagesController.dialogs_dict[dialogId] == null) {
						val sticker = mediaDataController.getGreetingsSticker()

						if (sticker != null) {
							chatActivity.setPreloadedSticker(sticker, true)
						}
					}

					presentFragment(chatActivity)
				}
			}
		}
	}

	fun setOpenedDialogId(dialogId: Long) {
		openedDialogId = dialogId

		if (viewPages == null) {
			return
		}

		viewPages?.forEach {
			if (it?.isDefaultDialogType == true && AndroidUtilities.isTablet()) {
				it.dialogsAdapter?.setOpenedDialogId(openedDialogId)
			}
		}

		updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG)
	}

	private fun onItemLongClick(view: View, position: Int, x: Float, y: Float, dialogsType: Int, adapter: RecyclerView.Adapter<*>?): Boolean {
		val parentActivity = parentActivity ?: return false

		@Suppress("NAME_SHADOWING") var position = position

		if (!actionBar!!.isActionModeShowed && !AndroidUtilities.isTablet() && !onlySelect && view is DialogCell) {
			if (view.isPointInsideAvatar(x, y)) {
				return showChatPreview(view)
			}
		}

		if (adapter === searchViewPager?.dialogsSearchAdapter) {
			val item = searchViewPager?.dialogsSearchAdapter?.getItem(position)

			if (!searchViewPager!!.dialogsSearchAdapter.isSearchWas) {
				val builder = AlertDialog.Builder(parentActivity)
				builder.setTitle(parentActivity.getString(R.string.ClearSearchSingleAlertTitle))

				val did = when (item) {
					is TLRPC.Chat -> {
						builder.setMessage(LocaleController.formatString("ClearSearchSingleChatAlertText", R.string.ClearSearchSingleChatAlertText, item.title))
						-item.id
					}

					is User -> {
						if (item.id == userConfig.clientUserId) {
							builder.setMessage(LocaleController.formatString("ClearSearchSingleChatAlertText", R.string.ClearSearchSingleChatAlertText, parentActivity.getString(R.string.SavedMessages)))
						}
						else {
							builder.setMessage(LocaleController.formatString("ClearSearchSingleUserAlertText", R.string.ClearSearchSingleUserAlertText, ContactsController.formatName(item.first_name, item.last_name)))
						}

						item.id
					}

					is TLRPC.EncryptedChat -> {
						val user = messagesController.getUser(item.user_id)
						builder.setMessage(LocaleController.formatString("ClearSearchSingleUserAlertText", R.string.ClearSearchSingleUserAlertText, ContactsController.formatName(user!!.first_name, user.last_name)))
						DialogObject.makeEncryptedDialogId(item.id.toLong())
					}

					else -> {
						return false
					}
				}

				builder.setPositiveButton(parentActivity.getString(R.string.ClearSearchRemove).uppercase()) { _, _ ->
					searchViewPager?.dialogsSearchAdapter?.removeRecentSearch(did)
				}

				builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

				val alertDialog = builder.create()

				showDialog(alertDialog)

				val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
				button?.setTextColor(ResourcesCompat.getColor(parentActivity.resources, R.color.purple, null))

				return true
			}
		}

		val dialog: TLRPC.Dialog?

		if (adapter === searchViewPager?.dialogsSearchAdapter) {
			if (onlySelect) {
				onItemClick(view, position, adapter)
				return false
			}

			var dialogId: Long = 0

			if (view is ProfileSearchCell && !searchViewPager!!.dialogsSearchAdapter.isGlobalSearch(position)) {
				dialogId = view.dialogId
			}

			if (dialogId != 0L) {
				showOrUpdateActionMode(dialogId, view)
				return true
			}

			return false
		}
		else {
			val dialogsAdapter = adapter as DialogsAdapter
			val dialogs = getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen)

			position = dialogsAdapter.fixPosition(position)

			if (position < 0 || position >= dialogs.size) {
				return false
			}

			dialog = dialogs[position]
		}

		if (dialog == null) {
			return false
		}

		return if (onlySelect) {
			if (initialDialogsType != 3 && initialDialogsType != 10) {
				return false
			}

			if (!validateSlowModeDialog(dialog.id)) {
				return false
			}

			addOrRemoveSelectedDialog(dialog.id, view)
			updateSelectedCount()

			true
		}
		else {
			if (dialog is TLRPC.TL_dialogFolder) {
				onArchiveLongPress(view)
				return false
			}

			if (actionBar?.isActionModeShowed == true && isDialogPinned(dialog)) {
				return false
			}

			showOrUpdateActionMode(dialog.id, view)

			true
		}
	}

	private fun onArchiveLongPress(view: View) {
		val parentActivity = parentActivity ?: return

		view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

		val builder = BottomSheet.Builder(parentActivity)
		val hasUnread = messagesStorage.archiveUnreadCount != 0
		val icons = intArrayOf(if (hasUnread) R.drawable.msg_markread else 0, if (SharedConfig.archiveHidden) R.drawable.chats_pin else R.drawable.chats_unpin)
		val items = arrayOf(if (hasUnread) parentActivity.getString(R.string.MarkAllAsRead) else null, if (SharedConfig.archiveHidden) parentActivity.getString(R.string.PinInTheList) else parentActivity.getString(R.string.HideAboveTheList))

		builder.setItems(items, icons) { _, which ->
			if (which == 0) {
				messagesStorage.readAllDialogs(1)
			}
			else if (which == 1) {
				viewPages?.forEach {
					if (it == null || it.dialogsType != 0 || it.visibility != View.VISIBLE) {
						return@forEach
					}

					val child = it.listView?.getChildAt(0)
					var dialogCell: DialogCell? = null

					if (child is DialogCell && child.isFolderCell) {
						dialogCell = child
					}

					it.listView?.toggleArchiveHidden(true, dialogCell)
				}
			}
		}

		showDialog(builder.create())
	}

	fun showChatPreview(cell: DialogCell): Boolean {
		val parentActivity = parentActivity ?: return false

		if (cell.isDialogFolder) {
			if (cell.currentDialogFolderId == 1) {
				onArchiveLongPress(cell)
			}

			return false
		}

		val dialogId = cell.dialogId
		val args = Bundle()
		val messageId = cell.messageId

		if (DialogObject.isEncryptedDialog(dialogId)) {
			return false
		}
		else {
			if (DialogObject.isUserDialog(dialogId)) {
				args.putLong("user_id", dialogId)
			}
			else {
				var did = dialogId

				if (messageId != 0) {
					val chat = messagesController.getChat(-did)

					if (chat?.migrated_to != null) {
						args.putLong("migrated_to", did)
						did = -chat.migrated_to.channel_id
					}
				}

				args.putLong("chat_id", -did)
			}
		}

		if (messageId != 0) {
			args.putInt("message_id", messageId)
		}

		val dialogIdArray = ArrayList<Long>()
		dialogIdArray.add(dialogId)

//        boolean hasFolders = getMessagesController().filtersEnabled && getMessagesController().dialogFiltersLoaded && getMessagesController().dialogFilters != null && getMessagesController().dialogFilters.size() > 0;
		val previewMenu = arrayOfNulls<ActionBarPopupWindowLayout>(1)
		//
//        LinearLayout foldersMenuView = null;
//        int[] foldersMenu = new int[1];
//        if (hasFolders) {
//            foldersMenuView = new LinearLayout(getParentActivity());
//            foldersMenuView.setOrientation(LinearLayout.VERTICAL);
//
//            ScrollView scrollView = new ScrollView(getParentActivity()) {
//                @Override
//                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
//                            (int) Math.min(
//                                    MeasureSpec.getSize(heightMeasureSpec),
//                                    Math.min(AndroidUtilities.displaySize.y * 0.35f, AndroidUtilities.dp(400))
//                            ),
//                            MeasureSpec.getMode(heightMeasureSpec)
//                    ));
//                }
//            };
//            LinearLayout linearLayout = new LinearLayout(getParentActivity());
//            linearLayout.setOrientation(LinearLayout.VERTICAL);
//            scrollView.addView(linearLayout);
//            final boolean backButtonAtTop = true;
//
//            final int foldersCount = getMessagesController().dialogFilters.size();
//            ActionBarMenuSubItem lastItem = null;
//            for (int i = 0; i < foldersCount; ++i) {
//                MessagesController.DialogFilter folder = getMessagesController().dialogFilters.get(i);
//                if (folder.includesDialog(AccountInstance.getInstance(currentAccount), dialogId)) {
//                    continue;
//                }
//                final ArrayList<Long> alwaysShow = FiltersListBottomSheet.getDialogsCount(DialogsActivity.this, folder, dialogIdArray, true, false);
//                int currentCount = folder.alwaysShow.size();
//                if (currentCount + alwaysShow.size() > 100) {
//                    continue;
//                }
//                ActionBarMenuSubItem folderItem = lastItem = new ActionBarMenuSubItem(getParentActivity(), !backButtonAtTop && linearLayout.getChildCount() == 0, false);
//                folderItem.setTextAndIcon(folder.name, R.drawable.msg_folders);
//                folderItem.setMinimumWidth(160);
//                folderItem.setOnClickListener(e -> {
//                    if (!alwaysShow.isEmpty()) {
//                        for (int a = 0; a < alwaysShow.size(); a++) {
//                            folder.neverShow.remove(alwaysShow.get(a));
//                        }
//                        folder.alwaysShow.addAll(alwaysShow);
//                        FilterCreateActivity.saveFilterToServer(folder, folder.flags, folder.name, folder.alwaysShow, folder.neverShow, folder.pinnedDialogs, false, false, true, true, false, DialogsActivity.this, null);
//                    }
//                    long did;
//                    if (alwaysShow.size() == 1) {
//                        did = alwaysShow.get(0);
//                    } else {
//                        did = 0;
//                    }
//                    getUndoView().showWithAction(did, UndoView.ACTION_ADDED_TO_FOLDER, alwaysShow.size(), folder, null, null);
//                    hideActionMode(true);
//                    finishPreviewFragment();
//                });
//                linearLayout.addView(folderItem);
//            }
//            if (lastItem != null && backButtonAtTop) {
//                lastItem.updateSelectorBackground(false, true);
//            }
//            if (linearLayout.getChildCount() <= 0) {
//                hasFolders = false;
//            } else {
//                ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(getParentActivity(), getResourceProvider(), Theme.key_actionBarDefaultSubmenuSeparator);
//                gap.setTag(R.id.fit_width_tag, 1);
//                ActionBarMenuSubItem backItem = new ActionBarMenuSubItem(getParentActivity(), backButtonAtTop, !backButtonAtTop);
//                backItem.setTextAndIcon(LocaleController.getString("Back", R.string.Back), R.drawable.ic_back_arrow);
//                backItem.setMinimumWidth(160);
//                backItem.setOnClickListener(e -> {
//                    if (previewMenu[0] != null) {
//                        previewMenu[0].getSwipeBack().closeForeground();
//                    }
//                });
//                if (backButtonAtTop) {
//                    foldersMenuView.addView(backItem);
//                    foldersMenuView.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
//                    foldersMenuView.addView(scrollView);
//                } else {
//                    foldersMenuView.addView(scrollView);
//                    foldersMenuView.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
//                    foldersMenuView.addView(backItem);
//                }
//            }
//        }
		val flags = ActionBarPopupWindowLayout.FLAG_SHOWN_FROM_BOTTOM
		//        if (hasFolders) {
//            flags |= ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK;
//        }
		val chatActivity = arrayOfNulls<ChatActivity>(1)
		previewMenu[0] = ActionBarPopupWindowLayout(parentActivity, R.drawable.popup_fixed_alert, flags)

//        if (hasFolders) {
//            foldersMenu[0] = previewMenu[0].addViewToSwipeBack(foldersMenuView);
//            ActionBarMenuSubItem addToFolderItem = new ActionBarMenuSubItem(getParentActivity(), true, false);
//            addToFolderItem.setTextAndIcon(LocaleController.getString("FilterAddTo", R.string.FilterAddTo), R.drawable.msg_addfolder);
//            addToFolderItem.setMinimumWidth(160);
//            addToFolderItem.setOnClickListener(e ->
//                    previewMenu[0].getSwipeBack().openForeground(foldersMenu[0])
//            );
//            previewMenu[0].addView(addToFolderItem);
//            previewMenu[0].getSwipeBack().setOnHeightUpdateListener(height -> {
//                if (chatActivity[0] == null || chatActivity[0].getFragmentView() == null) {
//                    return;
//                }
//                ViewGroup.LayoutParams lp = chatActivity[0].getFragmentView().getLayoutParams();
//                if (lp instanceof ViewGroup.MarginLayoutParams) {
//                    ((ViewGroup.MarginLayoutParams) lp).bottomMargin = AndroidUtilities.dp(24 + 16 + 8) + height;
//                    chatActivity[0].getFragmentView().setLayoutParams(lp);
//                }
//            });
//        }

		val markAsUnreadItem = ActionBarMenuSubItem(parentActivity, top = true, bottom = false)

		if (cell.hasUnread) {
			markAsUnreadItem.setTextAndIcon(parentActivity.getString(R.string.MarkAsRead), R.drawable.msg_markread)
		}
		else {
			markAsUnreadItem.setTextAndIcon(parentActivity.getString(R.string.MarkAsUnread), R.drawable.msg_markunread)
		}

		markAsUnreadItem.minimumWidth = 160

		markAsUnreadItem.setOnClickListener {
			if (cell.hasUnread) {
				markAsRead(dialogId)
			}
			else {
				markAsUnread(dialogId)
			}

			finishPreviewFragment()
		}

		previewMenu[0]?.addView(markAsUnreadItem)

		val hasPinAction = BooleanArray(1)
		hasPinAction[0] = true

		val dialog = messagesController.dialogs_dict[dialogId]
		var containsFilter = false
		val filter = if ((viewPages!![0]?.dialogsType == 7 || viewPages!![0]?.dialogsType == 8) && (!actionBar!!.isActionModeShowed || actionBar!!.isActionModeShowed(null)).also { containsFilter = it }) messagesController.selectedDialogFilter[if (viewPages!![0]?.dialogsType == 8) 1 else 0] else null

		if (!isDialogPinned(dialog)) {
			var pinnedCount = 0
			var pinnedSecretCount = 0
			var newPinnedCount = 0
			var newPinnedSecretCount = 0
			val dialogs = messagesController.getDialogs(folderId)
			var a = 0
			val n = dialogs.size

			while (a < n) {
				val dialog1 = dialogs[a]

				if (dialog1 is TLRPC.TL_dialogFolder) {
					a++
					continue
				}

				if (isDialogPinned(dialog1)) {
					if (DialogObject.isEncryptedDialog(dialog1.id)) {
						pinnedSecretCount++
					}
					else {
						pinnedCount++
					}
				}
				else if (!messagesController.isPromoDialog(dialog1.id, false)) {
					break
				}

				a++
			}

			var alreadyAdded = 0

			if (dialog != null && !isDialogPinned(dialog)) {
				if (DialogObject.isEncryptedDialog(dialogId)) {
					newPinnedSecretCount++
				}
				else {
					newPinnedCount++
				}

				if (filter != null && filter.alwaysShow.contains(dialogId)) {
					alreadyAdded++
				}
			}

			val maxPinnedCount = if (containsFilter && filter != null) {
				100 - filter.alwaysShow.size
			}
			else if (folderId != 0 || filter != null) {
				messagesController.maxFolderPinnedDialogsCount
			}
			else {
				messagesController.maxPinnedDialogsCount
			}

			hasPinAction[0] = !(newPinnedSecretCount + pinnedSecretCount > maxPinnedCount || newPinnedCount + pinnedCount - alreadyAdded > maxPinnedCount)
		}

		if (hasPinAction[0]) {
			val unpinItem = ActionBarMenuSubItem(parentActivity, top = false, bottom = false)

			if (isDialogPinned(dialog)) {
				unpinItem.setTextAndIcon(parentActivity.getString(R.string.UnpinMessage), R.drawable.msg_unpin)
			}
			else {
				unpinItem.setTextAndIcon(parentActivity.getString(R.string.PinMessage), R.drawable.msg_pin)
			}

			unpinItem.minimumWidth = 160

			unpinItem.setOnClickListener {
				finishPreviewFragment()

				AndroidUtilities.runOnUIThread({
					var minPinnedNum = Int.MAX_VALUE

					if (filter != null && isDialogPinned(dialog)) {
						var c = 0
						val n = filter.pinnedDialogs.size()

						while (c < n) {
							minPinnedNum = min(minPinnedNum, filter.pinnedDialogs.valueAt(c))
							c++
						}

						minPinnedNum -= canPinCount
					}

					var encryptedChat: TLRPC.EncryptedChat? = null

					if (DialogObject.isEncryptedDialog(dialogId)) {
						encryptedChat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))
					}

					if (!isDialogPinned(dialog)) {
						pinDialog(dialogId, true, filter, minPinnedNum, true)
						getUndoView()?.showWithAction(0, UndoView.ACTION_PIN_DIALOGS, 1, 1600, null, null)

						if (filter != null) {
							if (encryptedChat != null) {
								if (!filter.alwaysShow.contains(encryptedChat.user_id)) {
									filter.alwaysShow.add(encryptedChat.user_id)
								}
							}
							else {
								if (!filter.alwaysShow.contains(dialogId)) {
									filter.alwaysShow.add(dialogId)
								}
							}
						}
					}
					else {
						pinDialog(dialogId, false, filter, minPinnedNum, true)
						getUndoView()?.showWithAction(0, UndoView.ACTION_UNPIN_DIALOGS, 1, 1600, null, null)
					}

					if (filter != null) {
						FilterCreateActivity.saveFilterToServer(filter, filter.flags, filter.name, filter.alwaysShow, filter.neverShow, filter.pinnedDialogs, false, false, true, true, false, this@DialogsActivity, null)
					}

					messagesController.reorderPinnedDialogs(folderId, null, 0)
					updateCounters(true)

					viewPages?.forEach {
						it?.dialogsAdapter?.onReorderStateChanged(false)
					}

					updateVisibleRows(MessagesController.UPDATE_MASK_REORDER or MessagesController.UPDATE_MASK_CHECK)
				}, 100)
			}

			previewMenu[0]?.addView(unpinItem)
		}

		if (!DialogObject.isUserDialog(dialogId) || !isUserSelf(messagesController.getUser(dialogId))) {
			val muteItem = ActionBarMenuSubItem(parentActivity, top = false, bottom = false)

			if (!messagesController.isDialogMuted(dialogId)) {
				muteItem.setTextAndIcon(parentActivity.getString(R.string.Mute), R.drawable.msg_mute)
			}
			else {
				muteItem.setTextAndIcon(parentActivity.getString(R.string.Unmute), R.drawable.msg_unmute)
			}

			muteItem.minimumWidth = 160

			muteItem.setOnClickListener {
				val isMuted = messagesController.isDialogMuted(dialogId)

				if (!isMuted) {
					notificationsController.setDialogNotificationsSettings(dialogId, NotificationsController.SETTING_MUTE_FOREVER)
				}
				else {
					notificationsController.setDialogNotificationsSettings(dialogId, NotificationsController.SETTING_MUTE_UNMUTE)
				}

				BulletinFactory.createMuteBulletin(this, !isMuted).show()

				finishPreviewFragment()
			}

			previewMenu[0]?.addView(muteItem)
		}

		val deleteItem = ActionBarMenuSubItem(parentActivity, top = false, bottom = true)
		deleteItem.setIconColor(ResourcesCompat.getColor(parentActivity.resources, R.color.purple, null))
		deleteItem.setTextColor(ResourcesCompat.getColor(parentActivity.resources, R.color.purple, null))
		deleteItem.setTextAndIcon(parentActivity.getString(R.string.Delete), R.drawable.msg_delete)
		deleteItem.minimumWidth = 160

		deleteItem.setOnClickListener {
			performSelectedDialogsAction(dialogIdArray, delete, false)
			finishPreviewFragment()
		}

		previewMenu[0]?.addView(deleteItem)

		if (messagesController.checkCanOpenChat(args, this@DialogsActivity)) {
			if (searchString != null) {
				notificationCenter.postNotificationName(NotificationCenter.closeChats)
			}

			prepareBlurBitmap()

			parentLayout?.highlightActionButtons = true

			if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
				presentFragmentAsPreview(ChatActivity(args).also { chatActivity[0] = it })
			}
			else {
				presentFragmentAsPreviewWithMenu(ChatActivity(args).also { chatActivity[0] = it }, previewMenu[0])

				if (chatActivity[0] != null) {
					chatActivity[0]?.allowExpandPreviewByClick = true

					try {
						chatActivity[0]?.avatarContainer?.avatarImageView?.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
					}
					catch (e: Exception) {
						// ignored
					}
				}
			}

			return true
		}

		return false
	}

	private fun updateFloatingButtonOffset() {
		floatingButtonContainer?.translationY = floatingButtonTranslation - max(additionalFloatingTranslation, 0f) * (1f - floatingButtonHideProgress)
	}

	private fun hasHiddenArchive(): Boolean {
		return !onlySelect && initialDialogsType == 0 && folderId == 0 && messagesController.hasHiddenArchive()
	}

	private fun waitingForDialogsAnimationEnd(viewPage: ViewPage): Boolean {
		return viewPage.dialogsItemAnimator?.isRunning == true || dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0
	}

	private fun onDialogAnimationFinished() {
		dialogRemoveFinished = 0
		dialogInsertFinished = 0
		dialogChangeFinished = 0

		AndroidUtilities.runOnUIThread {
			if (viewPages != null && folderId != 0 && frozenDialogsList.isNullOrEmpty()) {
				viewPages?.forEach {
					it?.listView?.setEmptyView(null)
					it?.progressView?.visibility = View.INVISIBLE
				}

				finishFragment()
			}

			setDialogsListFrozen(false)
			updateDialogIndices()
		}
	}

	private fun setScrollY(value: Float) {
		scrimView?.getLocationInWindow(scrimViewLocation)
		actionBar?.translationY = value
		filterTabsView?.translationY = value

		if (animatedStatusView != null) {
			animatedStatusView?.translateY2(value.toInt().toFloat())
			animatedStatusView?.alpha = 1f - -value / ActionBar.getCurrentActionBarHeight()
		}

		updateContextViewPosition()

		viewPages?.forEach {
			it?.listView?.topGlowOffset = it!!.listView!!.paddingTop + value.toInt()
		}

		fragmentView?.invalidate()
	}

	private fun prepareBlurBitmap() {
		val context = context ?: return

		val fragmentView = fragmentView ?: return
		val blurredView = blurredView ?: return

		val w = (fragmentView.measuredWidth / 6.0f).toInt()
		val h = (fragmentView.measuredHeight / 6.0f).toInt()
		val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bitmap)

		canvas.scale(1.0f / 6.0f, 1.0f / 6.0f)

		fragmentView.draw(canvas)

		Utilities.stackBlurBitmap(bitmap, max(7, max(w, h) / 180))

		blurredView.background = BitmapDrawable(context.resources, bitmap)
		blurredView.alpha = 0.0f
		blurredView.visibility = View.VISIBLE
	}

	override fun onTransitionAnimationProgress(isOpen: Boolean, progress: Float) {
		if (blurredView?.visibility == View.VISIBLE) {
			if (isOpen) {
				blurredView?.alpha = 1.0f - progress
			}
			else {
				blurredView?.alpha = progress
			}
		}
	}

	override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
		if (isOpen && blurredView?.visibility == View.VISIBLE) {
			blurredView?.visibility = View.GONE
			blurredView?.background = null
		}

		if (isOpen && afterSignup) {
			try {
				fragmentView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
			}
			catch (e: Exception) {
				// ignored
			}

			(parentActivity as? LaunchActivity)?.fireworksOverlay?.start()
		}
	}

	private fun resetScroll() {
		if (actionBar?.translationY == 0f) {
			return
		}

		val animatorSet = AnimatorSet()
		animatorSet.playTogether(ObjectAnimator.ofFloat(this, SCROLL_Y, 0f))
		animatorSet.interpolator = DecelerateInterpolator()
		animatorSet.duration = 180
		animatorSet.start()
	}

	private fun hideActionMode(animateCheck: Boolean) {
		actionBar?.hideActionMode()
		//		if (menuDrawable != null) {
//			actionBar.setBackButtonContentDescription(LocaleController.getString("AccDescrOpenMenu", R.string.AccDescrOpenMenu));
//		}
		selectedDialogs.clear()

//		if (menuDrawable != null) {
//			menuDrawable.setRotation(0, true);
//		}
//		else
		backDrawable?.setRotation(0f, true)
		filterTabsView?.animateColorsTo(Theme.key_actionBarTabLine, Theme.key_actionBarTabActiveText, Theme.key_actionBarTabUnactiveText, Theme.key_actionBarTabSelector, Theme.key_actionBarDefault)

		actionBarColorAnimator?.cancel()

		actionBarColorAnimator = ValueAnimator.ofFloat(progressToActionMode, 0f)

		actionBarColorAnimator?.addUpdateListener {
			progressToActionMode = it.animatedValue as Float

			actionBar?.let { actionBar ->
				for (i in 0 until actionBar.childCount) {
					if (actionBar.getChildAt(i).visibility == View.VISIBLE && actionBar.getChildAt(i) !== actionBar.actionMode && actionBar.getChildAt(i) !== actionBar.backButton) {
						actionBar.getChildAt(i).alpha = 1f - progressToActionMode
					}
				}
			}

			fragmentView?.invalidate()
		}

		actionBarColorAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
		actionBarColorAnimator?.duration = 200
		actionBarColorAnimator?.start()

		allowMoving = false

		if (movingDialogFilters.isNotEmpty()) {
			movingDialogFilters.forEach { filter ->
				FilterCreateActivity.saveFilterToServer(filter, filter.flags, filter.name, filter.alwaysShow, filter.neverShow, filter.pinnedDialogs, false, false, true, true, false, this@DialogsActivity, null)
			}

			movingDialogFilters.clear()
		}

		if (movingWas) {
			messagesController.reorderPinnedDialogs(folderId, null, 0)
			movingWas = false
		}

		updateCounters(true)

		viewPages?.forEach {
			it?.dialogsAdapter?.onReorderStateChanged(false)
		}

		updateVisibleRows(MessagesController.UPDATE_MASK_REORDER or MessagesController.UPDATE_MASK_CHECK or if (animateCheck) MessagesController.UPDATE_MASK_CHAT else 0)
	}

	private val pinnedCount: Int
		get() {
			var pinnedCount = 0
			val dialogs: ArrayList<TLRPC.Dialog>
			val containsFilter = (viewPages!![0]?.dialogsType == 7 || viewPages!![0]?.dialogsType == 8) && (!actionBar!!.isActionModeShowed || actionBar!!.isActionModeShowed(null))

			dialogs = if (containsFilter) {
				getDialogsArray(currentAccount, viewPages!![0]!!.dialogsType, folderId, dialogsListFrozen)
			}
			else {
				messagesController.getDialogs(folderId)
			}

			var a = 0
			val n = dialogs.size

			while (a < n) {
				val dialog = dialogs[a]

				if (dialog is TLRPC.TL_dialogFolder) {
					a++
					continue
				}

				if (isDialogPinned(dialog)) {
					pinnedCount++
				}
				else if (!messagesController.isPromoDialog(dialog.id, false)) {
					break
				}

				a++
			}

			return pinnedCount
		}

	private fun isDialogPinned(dialog: TLRPC.Dialog?): Boolean {
		val containsFilter = (viewPages!![0]?.dialogsType == 7 || viewPages!![0]?.dialogsType == 8) && (!actionBar!!.isActionModeShowed || actionBar!!.isActionModeShowed(null))

		val filter = if (containsFilter) {
			messagesController.selectedDialogFilter[if (viewPages!![0]?.dialogsType == 8) 1 else 0]
		}
		else {
			null
		}

		return if (filter != null) {
			filter.pinnedDialogs.indexOfKey(dialog!!.id) >= 0
		}
		else {
			dialog?.pinned ?: false
		}
	}

	private fun performSelectedDialogsAction(selectedDialogs: ArrayList<Long>?, action: Int, alert: Boolean) {
		val parentActivity = parentActivity ?: return

		val containsFilter = (viewPages!![0]?.dialogsType == 7 || viewPages!![0]?.dialogsType == 8) && (!actionBar!!.isActionModeShowed || actionBar!!.isActionModeShowed(null))

		val filter = if (containsFilter) {
			messagesController.selectedDialogFilter[if (viewPages!![0]?.dialogsType == 8) 1 else 0]
		}
		else {
			null
		}

		val count = selectedDialogs!!.size
		var pinnedActionCount = 0

		if (action == archive || action == archive2) {
			val copy = ArrayList(selectedDialogs)

			messagesController.addDialogToFolder(copy, if (canUnarchiveCount == 0) 1 else 0, -1, null, 0)

			if (canUnarchiveCount == 0) {
				val preferences = MessagesController.getGlobalMainSettings()
				val hintShowed = preferences.getBoolean("archivehint_l", false) || SharedConfig.archiveHidden

				if (!hintShowed) {
					preferences.edit().putBoolean("archivehint_l", true).commit()
				}

				val undoAction = if (hintShowed) {
					if (copy.size > 1) UndoView.ACTION_ARCHIVE_FEW else UndoView.ACTION_ARCHIVE
				}
				else {
					if (copy.size > 1) UndoView.ACTION_ARCHIVE_FEW_HINT else UndoView.ACTION_ARCHIVE_HINT
				}

				getUndoView()?.showWithAction(0, undoAction, null) {
					messagesController.addDialogToFolder(copy, if (folderId == 0) 0 else 1, -1, null, 0)
				}
			}
			else {
				val dialogs = messagesController.getDialogs(folderId)

				if (viewPages != null && dialogs.isEmpty()) {
					viewPages?.firstOrNull()?.let {
						it.listView?.setEmptyView(null)
						it.progressView?.visibility = View.INVISIBLE
					}

					finishFragment()
				}
			}

			hideActionMode(false)

			return
		}
		else if ((action == pin || action == pin2) && canPinCount != 0) {
			var pinnedCount = 0
			var pinnedSecretCount = 0
			var newPinnedCount = 0
			var newPinnedSecretCount = 0
			val dialogs = messagesController.getDialogs(folderId)

			var a = 0
			val n = dialogs.size

			while (a < n) {
				val dialog = dialogs[a]

				if (dialog is TLRPC.TL_dialogFolder) {
					a++
					continue
				}

				if (isDialogPinned(dialog)) {
					if (DialogObject.isEncryptedDialog(dialog.id)) {
						pinnedSecretCount++
					}
					else {
						pinnedCount++
					}
				}
				else if (!messagesController.isPromoDialog(dialog.id, false)) {
					break
				}

				a++
			}

			var alreadyAdded = 0

			for (i in 0 until count) {
				val selectedDialog = selectedDialogs[i]
				val dialog = messagesController.dialogs_dict[selectedDialog]

				if (dialog == null || isDialogPinned(dialog)) {
					continue
				}

				if (DialogObject.isEncryptedDialog(selectedDialog)) {
					newPinnedSecretCount++
				}
				else {
					newPinnedCount++
				}

				if (filter != null && filter.alwaysShow.contains(selectedDialog)) {
					alreadyAdded++
				}
			}

			val maxPinnedCount = if (containsFilter) {
				100 - (filter?.alwaysShow?.size ?: 0)
			}
			else if (folderId != 0 || filter != null) {
				messagesController.maxFolderPinnedDialogsCount
			}
			else {
				if (userConfig.isPremium) messagesController.dialogFiltersPinnedLimitPremium else messagesController.dialogFiltersPinnedLimitDefault
			}

			if (newPinnedSecretCount + pinnedSecretCount > maxPinnedCount || newPinnedCount + pinnedCount - alreadyAdded > maxPinnedCount) {
				if (folderId != 0 || filter != null) {
					AlertsCreator.showSimpleAlert(this@DialogsActivity, LocaleController.formatString("PinFolderLimitReached", R.string.PinFolderLimitReached, LocaleController.formatPluralString("Chats", maxPinnedCount)))
				}
				else {
					val limitReachedBottomSheet = LimitReachedBottomSheet(this, LimitReachedBottomSheet.TYPE_PIN_DIALOGS, currentAccount)
					showDialog(limitReachedBottomSheet)
				}

				return
			}
		}
		else if ((action == delete || action == clear) && count > 1 && alert) {
			val builder = AlertDialog.Builder(parentActivity)

			if (action == delete) {
				builder.setTitle(LocaleController.formatString("DeleteFewChatsTitle", R.string.DeleteFewChatsTitle, LocaleController.formatPluralString("ChatsSelected", count)))
				builder.setMessage(parentActivity.getString(R.string.AreYouSureDeleteFewChats))
			}
			else {
				if (canClearCacheCount != 0) {
					builder.setTitle(LocaleController.formatString("ClearCacheFewChatsTitle", R.string.ClearCacheFewChatsTitle, LocaleController.formatPluralString("ChatsSelectedClearCache", count)))
					builder.setMessage(parentActivity.getString(R.string.AreYouSureClearHistoryCacheFewChats))
				}
				else {
					builder.setTitle(LocaleController.formatString("ClearFewChatsTitle", R.string.ClearFewChatsTitle, LocaleController.formatPluralString("ChatsSelectedClear", count)))
					builder.setMessage(parentActivity.getString(R.string.AreYouSureClearHistoryFewChats))
				}
			}

			builder.setPositiveButton(if (action == delete) parentActivity.getString(R.string.Delete) else if (canClearCacheCount != 0) parentActivity.getString(R.string.ClearHistoryCache) else parentActivity.getString(R.string.ClearHistory)) { _, _ ->
				if (selectedDialogs.isEmpty()) {
					return@setPositiveButton
				}

				val didsCopy = ArrayList(selectedDialogs)

				getUndoView()?.showWithAction(didsCopy, if (action == delete) UndoView.ACTION_DELETE_FEW else UndoView.ACTION_CLEAR_FEW, null, null, {
					if (action == delete) {
						messagesController.setDialogsInTransaction(true)

						performSelectedDialogsAction(didsCopy, action, false)

						messagesController.setDialogsInTransaction(false)
						messagesController.checkIfFolderEmpty(folderId)

						if (folderId != 0 && getDialogsArray(currentAccount, viewPages!![0]!!.dialogsType, folderId, false).size == 0) {
							viewPages!![0]?.listView?.setEmptyView(null)
							viewPages!![0]?.progressView?.visibility = View.INVISIBLE

							finishFragment()
						}
					}
					else {
						performSelectedDialogsAction(didsCopy, action, false)
					}
				}, null)

				hideActionMode(action == clear)
			}

			builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

			val alertDialog = builder.create()

			showDialog(alertDialog)

			val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
			button?.setTextColor(ResourcesCompat.getColor(parentActivity.resources, R.color.purple, null))
			return
		}
		else if (action == block && alert) {
			val user = if (count == 1) {
				val did = selectedDialogs[0]
				messagesController.getUser(did)
			}
			else {
				null
			}

			AlertsCreator.createBlockDialogAlert(this@DialogsActivity, count, canReportSpamCount != 0, user) { report, delete ->
				var a = 0
				val n = selectedDialogs.size

				while (a < n) {
					val did = selectedDialogs[a]

					if (report) {
						val u = messagesController.getUser(did)
						messagesController.reportSpam(did, u, null, null, false)
					}

					if (delete) {
						messagesController.deleteDialog(did, 0, true)
					}

					messagesController.blockPeer(did)

					a++
				}

				hideActionMode(false)
			}

			return
		}

		var minPinnedNum = Int.MAX_VALUE

		if (filter != null && (action == pin || action == pin2) && canPinCount != 0) {
			var c = 0
			val n = filter.pinnedDialogs.size()

			while (c < n) {
				minPinnedNum = min(minPinnedNum, filter.pinnedDialogs.valueAt(c))
				c++
			}

			minPinnedNum -= canPinCount
		}

		val scrollToTop = false

		for (a in 0 until count) {
			val selectedDialog = selectedDialogs[a]
			val dialog = messagesController.dialogs_dict[selectedDialog] ?: continue
			var chat: TLRPC.Chat?
			var user: User? = null
			var encryptedChat: TLRPC.EncryptedChat? = null

			if (DialogObject.isEncryptedDialog(selectedDialog)) {
				encryptedChat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(selectedDialog))
				chat = null

				user = if (encryptedChat != null) {
					messagesController.getUser(encryptedChat.user_id)
				}
				else {
					TLRPC.TL_userEmpty()
				}
			}
			else if (DialogObject.isUserDialog(selectedDialog)) {
				user = messagesController.getUser(selectedDialog)
				chat = null
			}
			else {
				chat = messagesController.getChat(-selectedDialog)
			}

			if (chat == null && user == null) {
				continue
			}

			val isBot = user != null && user.bot && !MessagesController.isSupportUser(user)

			if (action == pin || action == pin2) {
				if (canPinCount != 0) {
					if (isDialogPinned(dialog)) {
						continue
					}

					pinnedActionCount++
					pinDialog(selectedDialog, true, filter, minPinnedNum, count == 1)

					if (filter != null) {
						minPinnedNum++

						if (encryptedChat != null) {
							if (!filter.alwaysShow.contains(encryptedChat.user_id)) {
								filter.alwaysShow.add(encryptedChat.user_id)
							}
						}
						else {
							if (!filter.alwaysShow.contains(dialog.id)) {
								filter.alwaysShow.add(dialog.id)
							}
						}
					}
				}
				else {
					if (!isDialogPinned(dialog)) {
						continue
					}

					pinnedActionCount++
					pinDialog(selectedDialog, false, filter, minPinnedNum, count == 1)
				}
			}
			else if (action == read) {
				if (canReadCount != 0) {
					markAsRead(selectedDialog)
				}
				else {
					markAsUnread(selectedDialog)
				}
			}
			else if (action == delete || action == clear) {
				if (count == 1) {
					if (action == delete && canDeletePsaSelected) {
						val builder = AlertDialog.Builder(parentActivity)
						builder.setTitle(parentActivity.getString(R.string.PsaHideChatAlertTitle))
						builder.setMessage(parentActivity.getString(R.string.PsaHideChatAlertText))

						builder.setPositiveButton(parentActivity.getString(R.string.PsaHide)) { _, _ ->
							messagesController.hidePromoDialog()
							hideActionMode(false)
						}

						builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

						showDialog(builder.create())
					}
					else {
						AlertsCreator.createClearOrDeleteDialogAlert(this@DialogsActivity, action == clear, chat, user, DialogObject.isEncryptedDialog(dialog.id), action == delete) { param ->
							hideActionMode(false)

							if (action == clear && ChatObject.isChannel(chat) && (!chat.megagroup || !chat.username.isNullOrEmpty())) {
								messagesController.deleteDialog(selectedDialog, 2, param)
							}
							else {
								if (action == delete && folderId != 0 && getDialogsArray(currentAccount, viewPages!![0]!!.dialogsType, folderId, false).size == 1) {
									viewPages!![0]?.progressView?.visibility = View.INVISIBLE
								}

								debugLastUpdateAction = 3

								var selectedDialogIndex = -1

								if (action == delete) {
									setDialogsListFrozen(true)

									frozenDialogsList?.let { frozenDialogsList ->
										for (i in frozenDialogsList.indices) {
											if (frozenDialogsList[i].id == selectedDialog) {
												selectedDialogIndex = i
												break
											}
										}
									}
								}

								getUndoView()?.showWithAction(selectedDialog, if (action == clear) UndoView.ACTION_CLEAR else UndoView.ACTION_DELETE) {
									performDeleteOrClearDialogAction(action, selectedDialog, chat, isBot, param)
								}

								val currentDialogs = ArrayList(getDialogsArray(currentAccount, viewPages!![0]!!.dialogsType, folderId, false))
								var currentDialogIndex = -1

								for (i in currentDialogs.indices) {
									if (currentDialogs[i].id == selectedDialog) {
										currentDialogIndex = i
										break
									}
								}

								if (action == delete) {
									if (selectedDialogIndex >= 0 && currentDialogIndex < 0 && frozenDialogsList != null) {
										frozenDialogsList!!.removeAt(selectedDialogIndex)
										viewPages!![0]?.dialogsItemAnimator?.prepareForRemove()
										viewPages!![0]?.dialogsAdapter?.notifyItemRemoved(selectedDialogIndex)
										dialogRemoveFinished = 2
									}
									else {
										setDialogsListFrozen(false)
									}
								}
							}
						}
					}

					return
				}
				else {
					if (messagesController.isPromoDialog(selectedDialog, true)) {
						messagesController.hidePromoDialog()
					}
					else {
						if (action == clear && canClearCacheCount != 0) {
							messagesController.deleteDialog(selectedDialog, 2, false)
						}
						else {
							performDeleteOrClearDialogAction(action, selectedDialog, chat, isBot, false)
						}
					}
				}
			}
			else if (action == mute) {
				if (count == 1 && canMuteCount == 1) {
					showDialog(AlertsCreator.createMuteAlert(this, selectedDialog)) {
						hideActionMode(true)
					}

					return
				}
				else {
					if (canUnmuteCount != 0) {
						if (!messagesController.isDialogMuted(selectedDialog)) {
							continue
						}

						notificationsController.setDialogNotificationsSettings(selectedDialog, NotificationsController.SETTING_MUTE_UNMUTE)
					}
					else {
						if (messagesController.isDialogMuted(selectedDialog)) {
							continue
						}

						notificationsController.setDialogNotificationsSettings(selectedDialog, NotificationsController.SETTING_MUTE_FOREVER)
					}
				}
			}
		}

		if (action == mute && !(count == 1 && canMuteCount == 1)) {
			BulletinFactory.createMuteBulletin(this, canUnmuteCount == 0).show()
		}

		if (action == pin || action == pin2) {
			if (filter != null) {
				FilterCreateActivity.saveFilterToServer(filter, filter.flags, filter.name, filter.alwaysShow, filter.neverShow, filter.pinnedDialogs, false, false, true, true, false, this@DialogsActivity, null)
			}
			else {
				messagesController.reorderPinnedDialogs(folderId, null, 0)
			}

			if (searchIsShowed) {
				getUndoView()?.showWithAction(0, if (canPinCount != 0) UndoView.ACTION_PIN_DIALOGS else UndoView.ACTION_UNPIN_DIALOGS, pinnedActionCount)
			}
		}

		if (scrollToTop) {
			if (initialDialogsType != 10) {
				hideFloatingButton(false)
			}

			scrollToTop()
		}

		hideActionMode(action != pin2 && action != pin && action != delete)
	}

	private fun markAsRead(did: Long) {
		val dialog = messagesController.dialogs_dict[did]
		val containsFilter = (viewPages!![0]?.dialogsType == 7 || viewPages!![0]?.dialogsType == 8) && (!actionBar!!.isActionModeShowed || actionBar!!.isActionModeShowed(null))

		val filter = if (containsFilter) {
			messagesController.selectedDialogFilter[if (viewPages!![0]?.dialogsType == 8) 1 else 0]
		}
		else {
			null
		}

		debugLastUpdateAction = 2

		var selectedDialogIndex = -1

		if (filter != null && filter.flags and MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ != 0 && !filter.alwaysShow(currentAccount, dialog)) {
			setDialogsListFrozen(true)

			frozenDialogsList?.let { frozenDialogsList ->
				for (i in frozenDialogsList.indices) {
					if (frozenDialogsList[i].id == did) {
						selectedDialogIndex = i
						break
					}
				}

				if (selectedDialogIndex < 0) {
					setDialogsListFrozen(frozen = false, notify = false)
				}
			}
		}

		messagesController.markMentionsAsRead(did)
		messagesController.markDialogAsRead(did, dialog!!.top_message, dialog.top_message, dialog.last_message_date, false, 0, 0, true, 0)

		if (selectedDialogIndex >= 0) {
			frozenDialogsList?.removeAt(selectedDialogIndex)

			viewPages!![0]?.dialogsItemAnimator?.prepareForRemove()
			viewPages!![0]?.dialogsAdapter?.notifyItemRemoved(selectedDialogIndex)

			dialogRemoveFinished = 2
		}
	}

	private fun markAsUnread(did: Long) {
		messagesController.markDialogAsUnread(did, null, 0)
	}

	private fun performDeleteOrClearDialogAction(action: Int, selectedDialog: Long, chat: TLRPC.Chat?, isBot: Boolean, revoke: Boolean) {
		if (action == clear) {
			messagesController.deleteDialog(selectedDialog, 1, revoke)
		}
		else {
			if (chat != null) {
				if (ChatObject.isNotInChat(chat)) {
					messagesController.deleteDialog(selectedDialog, 0, revoke)
				}
				else {
					val currentUser = messagesController.getUser(userConfig.getClientUserId())
					messagesController.deleteParticipantFromChat(-selectedDialog.toInt().toLong(), currentUser, null, revoke, false)
				}
			}
			else {
				messagesController.deleteDialog(selectedDialog, 0, revoke)

				if (isBot) {
					messagesController.blockPeer(selectedDialog.toInt().toLong())
				}
			}

			if (AndroidUtilities.isTablet()) {
				notificationCenter.postNotificationName(NotificationCenter.closeChats, selectedDialog)
			}

			messagesController.checkIfFolderEmpty(folderId)
		}
	}

	private fun pinDialog(selectedDialog: Long, pin: Boolean, filter: MessagesController.DialogFilter?, minPinnedNum: Int, animated: Boolean) {
		var selectedDialogIndex = -1
		var currentDialogIndex = -1
		val scrollToPosition = if (viewPages!![0]?.dialogsType == 0 && hasHiddenArchive()) 1 else 0
		val currentPosition = viewPages!![0]!!.layoutManager!!.findFirstVisibleItemPosition()

		if (filter != null) {
			val index = filter.pinnedDialogs[selectedDialog, Int.MIN_VALUE]

			if (!pin && index == Int.MIN_VALUE) {
				return
			}
		}

		debugLastUpdateAction = if (pin) 4 else 5

		var needScroll = false

		if (currentPosition > scrollToPosition || !animated) {
			needScroll = true
		}
		else {
			setDialogsListFrozen(true)

			frozenDialogsList?.let { frozenDialogsList ->
				for (i in frozenDialogsList.indices) {
					if (frozenDialogsList[i].id == selectedDialog) {
						selectedDialogIndex = i
						break
					}
				}
			}
		}

		val updated = if (filter != null) {
			if (pin) {
				filter.pinnedDialogs.put(selectedDialog, minPinnedNum)
			}
			else {
				filter.pinnedDialogs.delete(selectedDialog)
			}

			if (animated) {
				messagesController.onFilterUpdate(filter)
			}

			true
		}
		else {
			messagesController.pinDialog(selectedDialog, pin, null, -1)
		}

		if (updated) {
			if (needScroll) {
				if (initialDialogsType != 10) {
					hideFloatingButton(false)
				}

				scrollToTop()
			}
			else {
				val currentDialogs = getDialogsArray(currentAccount, viewPages!![0]!!.dialogsType, folderId, false)

				for (i in currentDialogs.indices) {
					if (currentDialogs[i].id == selectedDialog) {
						currentDialogIndex = i
						break
					}
				}
			}
		}

		if (!needScroll) {
			var animate = false

			if (selectedDialogIndex >= 0) {
				if (frozenDialogsList != null && currentDialogIndex >= 0 && selectedDialogIndex != currentDialogIndex) {
					frozenDialogsList?.add(currentDialogIndex, frozenDialogsList!!.removeAt(selectedDialogIndex))
					viewPages!![0]?.dialogsItemAnimator?.prepareForRemove()
					viewPages!![0]?.dialogsAdapter?.notifyItemRemoved(selectedDialogIndex)
					viewPages!![0]?.dialogsAdapter?.notifyItemInserted(currentDialogIndex)

					dialogRemoveFinished = 2
					dialogInsertFinished = 2

					viewPages!![0]?.layoutManager?.scrollToPositionWithOffset(if (viewPages!![0]?.dialogsType == 0 && hasHiddenArchive()) 1 else 0, actionBar!!.translationY.toInt())

					animate = true
				}
				else if (currentDialogIndex >= 0 && selectedDialogIndex == currentDialogIndex) {
					animate = true

					AndroidUtilities.runOnUIThread({
						setDialogsListFrozen(false)
					}, 200)
				}
			}

			if (!animate) {
				setDialogsListFrozen(false)
			}
		}
	}

	private fun scrollToTop() {
		val scrollDistance = viewPages!![0]!!.layoutManager!!.findFirstVisibleItemPosition() * AndroidUtilities.dp(if (SharedConfig.useThreeLinesLayout) 78f else 72f)
		val position = if (viewPages!![0]?.dialogsType == 0 && hasHiddenArchive()) 1 else 0

		// val animator = viewPages!![0].listView!!.itemAnimator
		//        if (animator != null) {
//            animator.endAnimations();
//        }

		if (scrollDistance >= viewPages!![0]!!.listView!!.measuredHeight * 1.2f) {
			viewPages!![0]?.scrollHelper?.scrollDirection = RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP
			viewPages!![0]?.scrollHelper?.scrollToPosition(position, 0, false, true)

			resetScroll()
		}
		else {
			viewPages!![0]?.listView?.smoothScrollToPosition(position)
		}
	}

	private fun updateCounters(hide: Boolean) {
		val context = context ?: return

		var canClearHistoryCount = 0
		var canDeleteCount = 0
		var canUnpinCount = 0
		var canArchiveCount = 0

		canDeletePsaSelected = false
		canUnarchiveCount = 0
		canUnmuteCount = 0
		canMuteCount = 0
		canPinCount = 0
		canReadCount = 0
		canClearCacheCount = 0

		var cantBlockCount = 0

		canReportSpamCount = 0

		if (hide) {
			return
		}

		val count = selectedDialogs.size
		val selfUserId = userConfig.getClientUserId()
		val preferences = notificationsSettings

		for (a in 0 until count) {
			val dialog = messagesController.dialogs_dict[selectedDialogs[a]] ?: continue
			val selectedDialog = dialog.id
			val pinned = isDialogPinned(dialog)
			val hasUnread = dialog.unread_count != 0 || dialog.unread_mark

			if (messagesController.isDialogMuted(selectedDialog)) {
				canUnmuteCount++
			}
			else {
				canMuteCount++
			}

			if (hasUnread) {
				canReadCount++
			}

			if (folderId == 1 || dialog.folder_id == 1) {
				canUnarchiveCount++
			}
			else if (selectedDialog != selfUserId && selectedDialog != 777000L && !messagesController.isPromoDialog(selectedDialog, false)) {
				canArchiveCount++
			}

			if (!DialogObject.isUserDialog(selectedDialog) || selectedDialog == selfUserId) {
				cantBlockCount++
			}
			else {
				val user = messagesController.getUser(selectedDialog)

				if (MessagesController.isSupportUser(user)) {
					cantBlockCount++
				}
				else {
					if (preferences.getBoolean("dialog_bar_report$selectedDialog", true)) {
						canReportSpamCount++
					}
				}
			}

			if (DialogObject.isChannel(dialog)) {
				val chat = messagesController.getChat(-selectedDialog)

				if (messagesController.isPromoDialog(dialog.id, true)) {
					canClearCacheCount++

					if (messagesController.promoDialogType == MessagesController.PROMO_TYPE_PSA) {
						canDeleteCount++
						canDeletePsaSelected = true
					}
				}
				else {
					if (pinned) {
						canUnpinCount++
					}
					else {
						canPinCount++
					}

					if (chat != null && chat.megagroup) {
						if (TextUtils.isEmpty(chat.username)) {
							canClearHistoryCount++
						}
						else {
							canClearCacheCount++
						}
					}
					else {
						canClearCacheCount++
					}

					canDeleteCount++
				}
			}
			else {
				if (pinned) {
					canUnpinCount++
				}
				else {
					canPinCount++
				}

				canClearHistoryCount++
				canDeleteCount++
			}
		}

		//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            TransitionSet transition = new TransitionSet();
//            transition.addTransition(new Visibility() {
//                @Override
//                public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
//                    AnimatorSet set = new AnimatorSet();
//                    set.playTogether(
//                            ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1f),
//                            ObjectAnimator.ofFloat(view, View.SCALE_X, 0.5f, 1f),
//                            ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.5f, 1f)
//                    );
//                    set.setInterpolator(CubicBezierInterpolator.DEFAULT);
//                    return set;
//                }
//
//                @Override
//                public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
//                    AnimatorSet set = new AnimatorSet();
//                    set.playTogether(
//                            ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 0f),
//                            ObjectAnimator.ofFloat(view, View.SCALE_X, view.getScaleX(), 0.5f),
//                            ObjectAnimator.ofFloat(view, View.SCALE_Y, view.getScaleX(), 0.5f)
//                    );
//                    set.setInterpolator(CubicBezierInterpolator.DEFAULT);
//                    return set;
//                }
//            }).addTransition(new ChangeBounds());
//            transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
//            transition.setInterpolator(CubicBezierInterpolator.EASE_OUT);
//            transition.setDuration(150);
//            TransitionManager.beginDelayedTransition(actionBar.getActionMode(), transition);
//        }

		if (canDeleteCount != count) {
			deleteItem?.visibility = View.GONE
		}
		else {
			deleteItem?.visibility = View.VISIBLE
		}

		if (canClearCacheCount != 0 && canClearCacheCount != count || canClearHistoryCount != 0 && canClearHistoryCount != count) {
			clearItem?.visibility = View.GONE
		}
		else {
			clearItem?.visibility = View.VISIBLE

			if (canClearCacheCount != 0) {
				clearItem?.setText(context.getString(R.string.ClearHistoryCache))
			}
			else {
				clearItem?.setText(context.getString(R.string.ClearHistory))
			}
		}

		if (canUnarchiveCount != 0) {
			val contentDescription = context.getString(R.string.Unarchive)

			archiveItem?.setTextAndIcon(contentDescription, R.drawable.msg_unarchive)

			archive2Item?.setIcon(R.drawable.msg_unarchive)
			archive2Item?.contentDescription = contentDescription

			if (filterTabsView?.visibility == View.VISIBLE) {
				archive2Item?.visibility = View.VISIBLE
				archiveItem?.visibility = View.GONE
			}
			else {
				archiveItem?.visibility = View.VISIBLE
				archive2Item?.visibility = View.GONE
			}
		}
		else if (canArchiveCount != 0) {
			val contentDescription = context.getString(R.string.Archive)

			archiveItem?.setTextAndIcon(contentDescription, R.drawable.msg_archive)

			archive2Item?.setIcon(R.drawable.msg_archive)
			archive2Item?.contentDescription = contentDescription

			if (filterTabsView?.visibility == View.VISIBLE) {
				archive2Item?.visibility = View.VISIBLE
				archiveItem?.visibility = View.GONE
			}
			else {
				archiveItem?.visibility = View.VISIBLE
				archive2Item?.visibility = View.GONE
			}
		}
		else {
			archiveItem?.visibility = View.GONE
			archive2Item?.visibility = View.GONE
		}

		if (canPinCount + canUnpinCount != count) {
			pinItem?.visibility = View.GONE
			pin2Item?.visibility = View.GONE
		}
		else {
			if (filterTabsView?.visibility == View.VISIBLE) {
				pin2Item?.visibility = View.VISIBLE
				pinItem?.visibility = View.GONE
			}
			else {
				pinItem?.visibility = View.VISIBLE
				pin2Item?.visibility = View.GONE
			}
		}

		if (cantBlockCount != 0) {
			blockItem?.visibility = View.GONE
		}
		else {
			blockItem?.visibility = View.VISIBLE
		}

		if (filterTabsView == null || filterTabsView?.visibility != View.VISIBLE || filterTabsView?.currentTabIsDefault() == true) {
			removeFromFolderItem?.visibility = View.GONE
		}
		else {
			removeFromFolderItem?.visibility = View.VISIBLE
		}

		if (filterTabsView?.visibility == View.VISIBLE && filterTabsView?.currentTabIsDefault() == true && FiltersListBottomSheet.getCanAddDialogFilters(this, selectedDialogs).isNotEmpty()) {
			addToFolderItem?.visibility = View.VISIBLE
		}
		else {
			addToFolderItem?.visibility = View.GONE
		}

		if (canUnmuteCount != 0) {
			muteItem?.setIcon(R.drawable.msg_unmute)
			muteItem?.contentDescription = context.getString(R.string.ChatsUnmute)
		}
		else {
			muteItem?.setIcon(R.drawable.msg_mute)
			muteItem?.contentDescription = context.getString(R.string.ChatsMute)
		}

		if (canReadCount != 0) {
			readItem?.setTextAndIcon(context.getString(R.string.MarkAsRead), R.drawable.msg_markread)
		}
		else {
			readItem?.setTextAndIcon(context.getString(R.string.MarkAsUnread), R.drawable.msg_markunread)
		}

		if (canPinCount != 0) {
			pinItem?.setIcon(R.drawable.msg_pin)
			pinItem?.contentDescription = context.getString(R.string.PinToTop)

			pin2Item?.setText(context.getString(R.string.DialogPin))
		}
		else {
			pinItem?.setIcon(R.drawable.msg_unpin)
			pinItem?.contentDescription = context.getString(R.string.UnpinFromTop)

			pin2Item?.setText(context.getString(R.string.DialogUnpin))
		}
	}

	private fun validateSlowModeDialog(dialogId: Long): Boolean {
		if (messagesCount <= 1 && (commentView == null || commentView?.visibility != View.VISIBLE || commentView?.fieldText.isNullOrEmpty())) {
			return true
		}

		if (!DialogObject.isChatDialog(dialogId)) {
			return true
		}

		val chat = messagesController.getChat(-dialogId)

		if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
			context?.let {
				AlertsCreator.showSimpleAlert(this@DialogsActivity, it.getString(R.string.Slowmode), it.getString(R.string.SlowmodeSendError))
			}

			return false
		}

		return true
	}

	private fun showOrUpdateActionMode(dialogId: Long, cell: View) {
		addOrRemoveSelectedDialog(dialogId, cell)

		var updateAnimated = false

		if (actionBar?.isActionModeShowed == true) {
			if (selectedDialogs.isEmpty()) {
				hideActionMode(true)
				return
			}

			updateAnimated = true
		}
		else {
			if (searchIsShowed) {
				createActionMode(ACTION_MODE_SEARCH_DIALOGS_TAG)
			}
			else {
				createActionMode(null)
			}

			AndroidUtilities.hideKeyboard(fragmentView!!.findFocus())

			actionBar?.showActionMode()

			resetScroll()

			if (pinnedCount > 1) {
				viewPages?.forEach {
					it?.dialogsAdapter?.onReorderStateChanged(true)
				}

				updateVisibleRows(MessagesController.UPDATE_MASK_REORDER)
			}

			if (!searchIsShowed) {
				val animatorSet = AnimatorSet()

				val animators = actionModeViews.mapNotNull {
					it?.let { view ->
						view.pivotY = (ActionBar.getCurrentActionBarHeight() / 2).toFloat()
						AndroidUtilities.clearDrawableAnimation(view)
						ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.1f, 1.0f)
					}
				}

				animatorSet.playTogether(animators)
				animatorSet.duration = 200
				animatorSet.start()
			}

			actionBarColorAnimator?.cancel()

			actionBarColorAnimator = ValueAnimator.ofFloat(progressToActionMode, 1f)

			actionBarColorAnimator?.addUpdateListener {
				progressToActionMode = it.animatedValue as Float

				actionBar?.let { actionBar ->
					for (i in 0 until actionBar.childCount) {
						if (actionBar.getChildAt(i).visibility == View.VISIBLE && actionBar.getChildAt(i) !== actionBar.actionMode && actionBar.getChildAt(i) !== actionBar.backButton) {
							actionBar.getChildAt(i).alpha = 1f - progressToActionMode
						}
					}
				}

				fragmentView?.invalidate()
			}

			actionBarColorAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
			actionBarColorAnimator?.duration = 200
			actionBarColorAnimator?.start()

			filterTabsView?.animateColorsTo(Theme.key_profile_tabSelectedLine, Theme.key_profile_tabSelectedText, Theme.key_profile_tabText, Theme.key_profile_tabSelector, Theme.key_actionBarActionModeDefault)

			backDrawable?.setRotation(1f, true)
		}

		updateCounters(false)

		selectedDialogsCountTextView?.setNumber(selectedDialogs.size, updateAnimated)
	}

	private fun closeSearch() {
		if (AndroidUtilities.isTablet()) {
			actionBar?.closeSearchField()

			if (searchObject != null) {
				searchViewPager?.dialogsSearchAdapter?.putRecentSearch(searchDialogId, searchObject)
				searchObject = null
			}
		}
		else {
			closeSearchFieldOnHide = true
		}
	}

	val listView: RecyclerListView?
		get() = viewPages?.firstOrNull()?.listView

	val searchListView: RecyclerListView?
		get() = searchViewPager?.searchListView

	fun getUndoView(): UndoView? {
		if (undoView[0]?.visibility == View.VISIBLE) {
			val old = undoView[0]
			undoView[0] = undoView[1]
			undoView[1] = old

			old?.hide(true, 2)

			val contentView = fragmentView as? ContentView
			contentView?.removeView(undoView[0])
			contentView?.addView(undoView[0])
		}

		return undoView[0]
	}

	private fun updateProxyButton(animated: Boolean, force: Boolean) {
		if (proxyDrawable == null || doneItem?.visibility == View.VISIBLE) {
			return
		}

		var showDownloads = false

		for (i in downloadController.downloadingFiles.indices) {
			if (fileLoader.isLoadingFile(downloadController.downloadingFiles[i].fileName)) {
				showDownloads = true
				break
			}
		}

		if (!searching && downloadController.hasUnviewedDownloads() || showDownloads || downloadsItem?.visibility == View.VISIBLE && downloadsItem?.alpha == 1f && !force) {
			downloadsItemVisible = true
			downloadsItem?.visibility = View.VISIBLE
		}
		else {
			downloadsItem?.visibility = View.GONE
			downloadsItemVisible = false
		}

		val preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
		val proxyAddress = preferences.getString("proxy_ip", "")

		val proxyEnabled = preferences.getBoolean("proxy_enabled", false)

		if (!downloadsItemVisible && !searching && proxyEnabled && !TextUtils.isEmpty(proxyAddress) || messagesController.blockedCountry && SharedConfig.proxyList.isNotEmpty()) {
			if (!actionBar!!.isSearchFieldVisible && (doneItem == null || doneItem!!.visibility != View.VISIBLE)) {
				proxyItem?.visibility = View.VISIBLE
			}

			proxyItemVisible = true
			proxyDrawable?.setConnected(proxyEnabled, currentConnectionState == ConnectionsManager.ConnectionStateConnected || currentConnectionState == ConnectionsManager.ConnectionStateUpdating, animated)
		}
		else {
			proxyItemVisible = false
			proxyItem?.visibility = View.GONE
		}
	}

	private fun showDoneItem(show: Boolean) {
		if (doneItem == null) {
			return
		}

		doneItemAnimator?.cancel()
		doneItemAnimator = null

		doneItemAnimator = AnimatorSet()
		doneItemAnimator?.duration = 180

		if (show) {
			doneItem?.visibility = View.VISIBLE
		}
		else {
			doneItem?.isSelected = false

			val background = doneItem?.background

			if (background != null) {
				background.state = StateSet.NOTHING
				background.jumpToCurrentState()
			}

			searchItem?.visibility = View.VISIBLE

			if (proxyItemVisible) {
				proxyItem?.visibility = View.VISIBLE
			}

			if (passcodeItemVisible) {
				passcodeItem?.visibility = View.VISIBLE
			}

			if (downloadsItemVisible) {
				downloadsItem?.visibility = View.VISIBLE
			}
		}

		val arrayList = ArrayList<Animator>()
		arrayList.add(ObjectAnimator.ofFloat(doneItem, View.ALPHA, if (show) 1.0f else 0.0f))

		if (proxyItemVisible) {
			arrayList.add(ObjectAnimator.ofFloat(proxyItem, View.ALPHA, if (show) 0.0f else 1.0f))
		}

		if (passcodeItemVisible) {
			arrayList.add(ObjectAnimator.ofFloat(passcodeItem, View.ALPHA, if (show) 0.0f else 1.0f))
		}

		arrayList.add(ObjectAnimator.ofFloat(searchItem, View.ALPHA, if (show) 0.0f else 1.0f))

		doneItemAnimator?.playTogether(arrayList)

		doneItemAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				doneItemAnimator = null

				if (show) {
					searchItem?.visibility = View.INVISIBLE

					if (proxyItemVisible) {
						proxyItem?.visibility = View.INVISIBLE
					}

					if (passcodeItemVisible) {
						passcodeItem?.visibility = View.INVISIBLE
					}

					if (downloadsItemVisible) {
						downloadsItem?.visibility = View.INVISIBLE
					}
				}
				else {
					doneItem?.visibility = View.GONE
				}
			}
		})

		doneItemAnimator?.start()
	}

	private fun updateSelectedCount() {
		if (commentView != null) {
			if (selectedDialogs.isEmpty()) {
				if (initialDialogsType == 3 && selectAlertString == null) {
					actionBar?.setTitle(context?.getString(R.string.ForwardTo))
				}
				else {
					actionBar?.setTitle(context?.getString(R.string.SelectChat))
				}

				if (commentView?.tag != null) {
					commentView?.hidePopup(false)
					commentView?.closeKeyboard()

					commentViewAnimator?.cancel()

					commentViewAnimator = AnimatorSet()

					commentView?.translationY = 0f

					commentViewAnimator?.playTogether(ObjectAnimator.ofFloat(commentView, View.TRANSLATION_Y, commentView!!.measuredHeight.toFloat()), ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_X, .2f), ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_Y, .2f), ObjectAnimator.ofFloat(writeButtonContainer, View.ALPHA, 0f), ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, 0.2f), ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, 0.2f), ObjectAnimator.ofFloat(selectedCountView, View.ALPHA, 0.0f))
					commentViewAnimator?.duration = 180
					commentViewAnimator?.interpolator = DecelerateInterpolator()

					commentViewAnimator?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							commentView?.visibility = View.GONE
							writeButtonContainer?.visibility = View.GONE
						}
					})

					commentViewAnimator?.start()
					commentView?.tag = null
					fragmentView?.requestLayout()
				}
			}
			else {
				selectedCountView?.invalidate()

				if (commentView?.tag == null) {
					commentView?.fieldText = ""

					commentViewAnimator?.cancel()

					commentView?.visibility = View.VISIBLE

					writeButtonContainer?.visibility = View.VISIBLE

					commentViewAnimator = AnimatorSet()
					commentViewAnimator?.playTogether(ObjectAnimator.ofFloat(commentView, View.TRANSLATION_Y, commentView!!.measuredHeight.toFloat(), 0f), ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_X, 1f), ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_Y, 1f), ObjectAnimator.ofFloat(writeButtonContainer, View.ALPHA, 1f), ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, 1f), ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, 1f), ObjectAnimator.ofFloat(selectedCountView, View.ALPHA, 1f))
					commentViewAnimator?.duration = 180
					commentViewAnimator?.interpolator = DecelerateInterpolator()

					commentViewAnimator?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							commentView?.tag = 2
							commentView?.requestLayout()
						}
					})

					commentViewAnimator?.start()

					commentView?.tag = 1
				}

				actionBar?.setTitle(LocaleController.formatPluralString("Recipient", selectedDialogs.size))
			}
		}
		else if (initialDialogsType == 10) {
			hideFloatingButton(selectedDialogs.isEmpty())
		}

		isNextButton = shouldShowNextButton(this, selectedDialogs, if (commentView != null) commentView!!.fieldText else "", false)

		AndroidUtilities.updateViewVisibilityAnimated(writeButton?.get(0), !isNextButton, 0.5f, true)
		AndroidUtilities.updateViewVisibilityAnimated(writeButton?.get(1), isNextButton, 0.5f, true)
	}

	private fun askForPermissions() {
		val activity = parentActivity ?: return
		val permissions = ArrayList<String>()

		if ((Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
			permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
		}

		if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.READ_PHONE_STATE)
		}
		if (Build.VERSION.SDK_INT >= 33) {
			if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
				permissions.add(Manifest.permission.POST_NOTIFICATIONS)
			}
		}

		if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.RECORD_AUDIO)
		}

		if (permissions.isEmpty()) {
			if (askingForPermissions) {
				askingForPermissions = false
				showFiltersHint()
			}

			return
		}

		val items = permissions.toTypedArray()

		try {
			activity.requestPermissions(items, 1)
		}
		catch (e: Exception) {
			// ignored
		}
	}

	override fun onDialogDismiss(dialog: Dialog) {
		super.onDialogDismiss(dialog)

		if (permissionDialog != null && dialog === permissionDialog && parentActivity != null) {
			askForPermissions()
		}
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)

		scrimPopupWindow?.dismiss()

		if (!onlySelect && floatingButtonContainer != null) {
			floatingButtonContainer?.viewTreeObserver?.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
				override fun onGlobalLayout() {
					floatingButtonTranslation = if (floatingHidden) AndroidUtilities.dp(100f).toFloat() else 0f

					updateFloatingButtonOffset()

					floatingButtonContainer?.isClickable = !floatingHidden
					floatingButtonContainer?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
				}
			})
		}
	}

	override fun onRequestPermissionsResultFragment(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		if (requestCode == 1) {
			for (a in permissions.indices) {
				if (grantResults.size <= a) {
					continue
				}

				when (permissions[a]) {
					Manifest.permission.WRITE_EXTERNAL_STORAGE -> if (grantResults[a] == PackageManager.PERMISSION_GRANTED) {
						ImageLoader.instance.checkMediaPaths()
					}
				}
			}

			if (askingForPermissions) {
				askingForPermissions = false
				showFiltersHint()
			}
		}
		else if (requestCode == 4) {
			var allGranted = true

			for (grantResult in grantResults) {
				if (grantResult != PackageManager.PERMISSION_GRANTED) {
					allGranted = false
					break
				}
			}

			if (allGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && FilesMigrationService.filesMigrationBottomSheet != null) {
				FilesMigrationService.filesMigrationBottomSheet.migrateOldFolder()
			}
		}
	}

	private fun reloadViewPageDialogs(viewPage: ViewPage?, newMessage: Boolean) {
		if (viewPage?.visibility != View.VISIBLE) {
			return
		}

		val oldItemCount = viewPage.dialogsAdapter!!.currentCount

		if (viewPage.dialogsType == 0 && hasHiddenArchive() && viewPage.listView?.childCount == 0) {
			val layoutManager = viewPage.listView?.layoutManager as? LinearLayoutManager
			layoutManager?.scrollToPositionWithOffset(1, 0)
		}

		if (viewPage.dialogsAdapter?.isDataSetChanged == true || newMessage) {
			viewPage.dialogsAdapter?.updateHasHints()

			val newItemCount = viewPage.dialogsAdapter?.itemCount ?: 0

			if (newItemCount == 1 && oldItemCount == 1 && viewPage.dialogsAdapter?.getItemViewType(0) == 5) {
				if (viewPage.dialogsAdapter?.lastDialogsEmptyType != viewPage.dialogsAdapter?.dialogsEmptyType()) {
					viewPage.dialogsAdapter?.notifyItemChanged(0)
				}
			}
			else {
				viewPage.dialogsAdapter?.notifyDataSetChanged()

				if (newItemCount > oldItemCount && initialDialogsType != 11 && initialDialogsType != 12 && initialDialogsType != 13) {
					viewPage.recyclerItemsEnterAnimator?.showItemsAnimated(oldItemCount)
				}
			}
		}
		else {
			updateVisibleRows(MessagesController.UPDATE_MASK_NEW_MESSAGE)

			val newItemCount = viewPage.dialogsAdapter?.itemCount ?: 0

			if (newItemCount > oldItemCount && initialDialogsType != 11 && initialDialogsType != 12 && initialDialogsType != 13) {
				viewPage.recyclerItemsEnterAnimator?.showItemsAnimated(oldItemCount)
			}
		}

		try {
			viewPage.listView?.setEmptyView(if (folderId == 0) viewPage.progressView else null)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		checkListLoad(viewPage)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.dialogsNeedReload -> {
				if (viewPages == null || dialogsListFrozen) {
					return
				}

				viewPages?.let { viewPages ->
					for (viewPage in viewPages) {
						var filter: MessagesController.DialogFilter? = null

						if (viewPages[0]?.dialogsType == 7 || viewPages[0]?.dialogsType == 8) {
							filter = messagesController.selectedDialogFilter[if (viewPages[0]?.dialogsType == 8) 1 else 0]
						}

						val isUnread = filter != null && filter.flags and MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ != 0

						if (slowedReloadAfterDialogClick && isUnread) {
							// in unread tab dialogs reload too instantly removes dialog from folder after clicking on it
							AndroidUtilities.runOnUIThread({
								reloadViewPageDialogs(viewPage, args.isNotEmpty())

								if (filterTabsView?.visibility == View.VISIBLE) {
									filterTabsView?.checkTabsCounter()
								}
							}, 160)
						}
						else {
							reloadViewPageDialogs(viewPage, args.isNotEmpty())
						}
					}
				}

				if (filterTabsView?.visibility == View.VISIBLE) {
					filterTabsView?.checkTabsCounter()
				}

				slowedReloadAfterDialogClick = false
			}

			NotificationCenter.dialogsUnreadCounterChanged -> {
				if (filterTabsView?.visibility == View.VISIBLE) {
					filterTabsView?.notifyTabCounterChanged(filterTabsView!!.defaultTabId)
				}
			}

			NotificationCenter.dialogsUnreadReactionsCounterChanged -> {
				updateVisibleRows(0)
			}

			NotificationCenter.emojiLoaded -> {
				updateVisibleRows(0)
				filterTabsView?.tabsContainer?.invalidateViews()
			}

			NotificationCenter.closeSearchByActiveAction -> {
				actionBar?.closeSearchField()
			}

			NotificationCenter.proxySettingsChanged -> {
				updateProxyButton(animated = false, force = false)
			}

			NotificationCenter.updateInterfaces -> {
				val mask = args[0] as Int

				updateVisibleRows(mask)

				if (filterTabsView?.visibility == View.VISIBLE && mask and MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE != 0) {
					filterTabsView?.checkTabsCounter()
				}

				viewPages?.let { viewPages ->
					for (viewPage in viewPages) {
						if (mask and MessagesController.UPDATE_MASK_STATUS != 0) {
							viewPage?.dialogsAdapter?.sortOnlineContacts(true)
						}
					}
				}

				updateStatus(UserConfig.getInstance(account).getCurrentUser(), true)
			}

			NotificationCenter.appDidLogout -> {
				dialogsLoaded[currentAccount] = false
			}

			NotificationCenter.chatDidCreated -> {
				loadDialogs(accountInstance, force = true)
			}

			NotificationCenter.encryptedChatUpdated -> {
				updateVisibleRows(0)
			}

			NotificationCenter.contactsDidLoad -> {
				if (viewPages == null || dialogsListFrozen) {
					return
				}

				val wasVisible = floatingProgressVisible

				setFloatingProgressVisible(visible = false, animate = true)

				viewPages?.forEach {
					it?.dialogsAdapter?.setForceUpdatingContacts(false)
				}

				if (wasVisible) {
					setContactsAlpha(0f)
					animateContactsAlpha(1f)
				}

				var updateVisibleRows = false

				viewPages?.forEach {
					if (it?.isDefaultDialogType == true && messagesController.allFoldersDialogsCount <= 10) {
						it.dialogsAdapter?.notifyDataSetChanged()
					}
					else {
						updateVisibleRows = true
					}
				}

				if (updateVisibleRows) {
					updateVisibleRows(0)
				}
			}

			NotificationCenter.openedChatChanged -> {
				if (viewPages == null) {
					return
				}

				viewPages?.forEach {
					if (it?.isDefaultDialogType == true && AndroidUtilities.isTablet()) {
						val close = args[1] as Boolean
						val dialogId = args[0] as Long

						if (close) {
							if (dialogId == openedDialogId) {
								openedDialogId = 0
							}
						}
						else {
							openedDialogId = dialogId
						}

						it.dialogsAdapter?.setOpenedDialogId(openedDialogId)
					}
				}

				updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG)
			}

			NotificationCenter.notificationsSettingsUpdated -> {
				updateVisibleRows(0)
			}

			NotificationCenter.messageReceivedByAck, NotificationCenter.messageReceivedByServer, NotificationCenter.messageSendError -> {
				updateVisibleRows(MessagesController.UPDATE_MASK_SEND_STATE)
			}

			NotificationCenter.didSetPasscode -> {
				updatePasscodeButton()
			}

			NotificationCenter.needReloadRecentDialogsSearch -> {
				searchViewPager?.dialogsSearchAdapter?.loadRecentSearch()
			}

			NotificationCenter.replyMessagesDidLoad -> {
				updateVisibleRows(MessagesController.UPDATE_MASK_MESSAGE_TEXT)
			}

			NotificationCenter.reloadHints -> {
				searchViewPager?.dialogsSearchAdapter?.notifyDataSetChanged()
			}

			NotificationCenter.didUpdateConnectionState -> {
				val state = AccountInstance.getInstance(account).connectionsManager.getConnectionState()

				if (currentConnectionState != state) {
					currentConnectionState = state
					updateProxyButton(animated = true, force = false)
				}
			}

			NotificationCenter.onDownloadingFilesChanged -> {
				updateProxyButton(animated = true, force = false)
			}

			NotificationCenter.needDeleteDialog -> {
				if (fragmentView == null || isPaused) {
					return
				}

				val dialogId = args[0] as Long
				val user = args[1] as? User
				val chat = args[2] as? TLRPC.Chat
				val revoke = (args[3] as? Boolean) ?: false

				val deleteRunnable = Runnable {
					if (chat != null) {
						if (ChatObject.isNotInChat(chat)) {
							messagesController.deleteDialog(dialogId, 0, revoke)
						}
						else {
							messagesController.deleteParticipantFromChat(-dialogId, messagesController.getUser(userConfig.getClientUserId()), null, revoke, revoke)
						}
						if (isSubscriptionChannel(chat)) {
							val request = unsubscribeRequest(chat.id, ElloRpc.PEER_TYPE_CHANNEL)

							connectionsManager.sendRequest(request) { _, error ->
								AndroidUtilities.runOnUIThread {
									if (error != null) {
										FileLog.e("unsubscribe(" + chat.id + ") error(" + error.code + "): " + error.text)
									}
									else {
										FileLog.d("unsubscribe(success)")
									}
								}
							}
						}
					}
					else {
						messagesController.deleteDialog(dialogId, 0, revoke)

						if (user?.bot == true) {
							messagesController.blockPeer(user.id)
						}
					}

					messagesController.checkIfFolderEmpty(folderId)
				}

				if (undoView[0] != null) {
					getUndoView()?.showWithAction(dialogId, UndoView.ACTION_DELETE, deleteRunnable)
				}
				else {
					deleteRunnable.run()
				}
			}

			NotificationCenter.folderBecomeEmpty -> {
				val fid = args[0] as Int

				if (folderId == fid && folderId != 0) {
					finishFragment()
				}
			}

			NotificationCenter.dialogFiltersUpdated -> {
				updateFilterTabs(force = true, animated = true)
			}

			NotificationCenter.filterSettingsUpdated -> {
				showFiltersHint()
			}

			NotificationCenter.newSuggestionsAvailable -> {
				showNextSupportedSuggestion()
			}

			NotificationCenter.forceImportContactsStart -> {
				setFloatingProgressVisible(visible = true, animate = true)

				viewPages?.forEach {
					it?.dialogsAdapter?.let { adapter ->
						adapter.setForceShowEmptyCell(false)
						adapter.setForceUpdatingContacts(true)
						adapter.notifyDataSetChanged()
					}
				}
			}

			NotificationCenter.messagesDeleted -> {
				if (searchIsShowed && searchViewPager != null) {
					val markAsDeletedMessages = args[0] as List<Int>
					val channelId = args[1] as Long
					searchViewPager?.messagesDeleted(channelId, markAsDeletedMessages)
				}
			}

			NotificationCenter.didClearDatabase -> {
				viewPages?.forEach {
					it?.dialogsAdapter?.didDatabaseCleared()
				}

				SuggestClearDatabaseBottomSheet.dismissDialog()
			}

			NotificationCenter.onDatabaseMigration -> {
				val startMigration = args[0] as Boolean

				fragmentView?.let { fragmentView ->
					if (startMigration) {
						if (databaseMigrationHint == null) {
							databaseMigrationHint = DatabaseMigrationHint(fragmentView.context)
							databaseMigrationHint?.alpha = 0f
							(fragmentView as? ContentView)?.addView(databaseMigrationHint)
							databaseMigrationHint?.animate()?.alpha(1f)?.setDuration(300)?.setStartDelay(1000)?.start()
						}

						databaseMigrationHint?.tag = 1
					}
					else {
						databaseMigrationHint?.takeIf { it.tag != null }?.let { localView ->
							localView.animate().setListener(null).cancel()

							localView.animate().setListener(object : AnimatorListenerAdapter() {
								override fun onAnimationEnd(animation: Animator) {
									(localView.parent as? ViewGroup)?.removeView(localView)
									databaseMigrationHint = null
								}
							}).alpha(0f).setStartDelay(0).setDuration(150).start()

							databaseMigrationHint?.tag = null
						}
					}
				}
			}

			NotificationCenter.onDatabaseOpened -> {
				checkSuggestClearDatabase()
			}

			NotificationCenter.userEmojiStatusUpdated -> {
				updateStatus(args[0] as User, true)
			}

			NotificationCenter.currentUserPremiumStatusChanged -> {
				updateStatus(UserConfig.getInstance(account).getCurrentUser(), true)
			}
		}
	}

	private fun checkSuggestClearDatabase() {
		if (messagesStorage.showClearDatabaseAlert) {
			messagesStorage.showClearDatabaseAlert = false
			SuggestClearDatabaseBottomSheet.show(this)
		}
	}

	private fun showNextSupportedSuggestion() {
		if (showingSuggestion != null) {
			return
		}

		for (suggestion in (messagesController.pendingSuggestions ?: emptySet())) {
			if (showSuggestion(suggestion)) {
				showingSuggestion = suggestion
				return
			}
		}
	}

	private fun onSuggestionDismiss() {
		if (showingSuggestion == null) {
			return
		}

		messagesController.removeSuggestion(0, showingSuggestion)
		showingSuggestion = null
		showNextSupportedSuggestion()
	}

	private fun showSuggestion(suggestion: String): Boolean {
		val parentActivity = parentActivity ?: return false

		if ("AUTOARCHIVE_POPULAR" == suggestion) {
			val builder = AlertDialog.Builder(parentActivity)
			builder.setTitle(parentActivity.getString(R.string.HideNewChatsAlertTitle))
			builder.setMessage(AndroidUtilities.replaceTags(parentActivity.getString(R.string.HideNewChatsAlertText)))
			builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

			builder.setPositiveButton(parentActivity.getString(R.string.GoToSettings)) { _, _ ->
				presentFragment(PrivacySettingsActivity())
				AndroidUtilities.scrollToFragmentRow(parentLayout, "newChatsRow")
			}

			showDialog(builder.create()) {
				onSuggestionDismiss()
			}

			return true
		}

		return false
	}

	private fun showFiltersHint() {
		if (askingForPermissions || !messagesController.dialogFiltersLoaded || !messagesController.showFiltersTooltip || filterTabsView == null || messagesController.dialogFilters.isNotEmpty() || isPaused || !userConfig.filtersLoaded || inPreviewMode) {
			return
		}

		val preferences = MessagesController.getGlobalMainSettings()

		if (preferences.getBoolean("filterhint", false)) {
			return
		}

		preferences.edit().putBoolean("filterhint", true).commit()

		AndroidUtilities.runOnUIThread({
			getUndoView()?.showWithAction(0, UndoView.ACTION_FILTERS_AVAILABLE, null) {
				presentFragment(FiltersSetupActivity())
			}
		}, 1000)
	}

	private fun setDialogsListFrozen(frozen: Boolean, notify: Boolean) {
		if (viewPages == null || dialogsListFrozen == frozen) {
			return
		}

		frozenDialogsList = if (frozen) {
			ArrayList(getDialogsArray(currentAccount, viewPages!![0]!!.dialogsType, folderId, false))
		}
		else {
			null
		}

		dialogsListFrozen = frozen

		viewPages?.firstOrNull()?.dialogsAdapter?.setDialogsListFrozen(frozen)

		if (!frozen && notify) {
			viewPages?.firstOrNull()?.dialogsAdapter?.notifyDataSetChanged()
		}
	}

	private fun setDialogsListFrozen(frozen: Boolean) {
		setDialogsListFrozen(frozen, true)
	}

	fun getDialogsArray(currentAccount: Int, dialogsType: Int, folderId: Int, frozen: Boolean): ArrayList<TLRPC.Dialog> {
		val frozenDialogsList = frozenDialogsList

		if (frozen && frozenDialogsList != null) {
			return frozenDialogsList
		}

		val messagesController = AccountInstance.getInstance(currentAccount).messagesController

		when (dialogsType) {
			0 -> {
				return messagesController.getDialogs(folderId)
			}

			1, 10, 13 -> {
				return messagesController.dialogsServerOnly
			}

			2 -> {
				val dialogs = ArrayList<TLRPC.Dialog>(messagesController.dialogsCanAddUsers.size + messagesController.dialogsMyChannels.size + messagesController.dialogsMyGroups.size + 2)

				if (messagesController.dialogsMyChannels.size > 0 && allowChannels) {
					dialogs.add(DialogsHeader(DialogsHeader.HEADER_TYPE_MY_CHANNELS))
					dialogs.addAll(messagesController.dialogsMyChannels)
				}
				if (messagesController.dialogsMyGroups.size > 0 && allowGroups) {
					dialogs.add(DialogsHeader(DialogsHeader.HEADER_TYPE_MY_GROUPS))
					dialogs.addAll(messagesController.dialogsMyGroups)
				}

				if (messagesController.dialogsCanAddUsers.size > 0) {
					val count = messagesController.dialogsCanAddUsers.size
					var first = true

					for (i in 0 until count) {
						val dialog = messagesController.dialogsCanAddUsers[i]

						if (allowChannels && ChatObject.isChannelAndNotMegaGroup(-dialog.id, currentAccount) || allowGroups && (ChatObject.isMegagroup(currentAccount, -dialog.id) || !ChatObject.isChannel(-dialog.id, currentAccount))) {
							if (first) {
								dialogs.add(DialogsHeader(DialogsHeader.HEADER_TYPE_GROUPS))
								first = false
							}

							dialogs.add(dialog)
						}
					}
				}

				return dialogs
			}

			3 -> {
				return messagesController.dialogsForward
			}

			4, 12 -> {
				return messagesController.dialogsUsersOnly
			}

			5 -> {
				return messagesController.dialogsChannelsOnly
			}

			6, 11 -> {
				return messagesController.dialogsGroupsOnly
			}

			7, 8 -> {
				val dialogFilter = messagesController.selectedDialogFilter[if (dialogsType == 7) 0 else 1]
				return dialogFilter?.dialogs ?: messagesController.getDialogs(folderId)
			}

			9 -> {
				return messagesController.dialogsForBlock
			}

			DIALOGS_TYPE_START_ATTACH_BOT -> {
				val dialogs = ArrayList<TLRPC.Dialog>()

				if (allowUsers || allowBots) {
					for (d in messagesController.dialogsUsersOnly) {
						if (if (messagesController.getUser(d.id)!!.bot) allowBots else allowUsers) {
							dialogs.add(d)
						}
					}
				}

				if (allowGroups) {
					dialogs.addAll(messagesController.dialogsGroupsOnly)
				}

				if (allowChannels) {
					dialogs.addAll(messagesController.dialogsChannelsOnly)
				}

				return dialogs
			}

			else -> {
				return ArrayList()
			}
		}
	}

	private fun updatePasscodeButton() {
		if (passcodeItem == null) {
			return
		}

		if (SharedConfig.passcodeHash.isNotEmpty() && !searching) {
			if (doneItem == null || doneItem?.visibility != View.VISIBLE) {
				passcodeItem?.visibility = View.VISIBLE
			}

			passcodeItem?.setIcon(passcodeDrawable)
			passcodeItemVisible = true
		}
		else {
			passcodeItem?.visibility = View.GONE
			passcodeItemVisible = false
		}
	}

	private fun setFloatingProgressVisible(visible: Boolean, animate: Boolean) {
		if (floatingButton == null || floatingProgressView == null) {
			return
		}

		if (animate) {
			if (visible == floatingProgressVisible) {
				return
			}

			floatingProgressAnimator?.cancel()

			floatingProgressVisible = visible

			floatingProgressAnimator = AnimatorSet()
			floatingProgressAnimator?.playTogether(ObjectAnimator.ofFloat(floatingButton, View.ALPHA, if (visible) 0f else 1f), ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, if (visible) 0.1f else 1f), ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, if (visible) 0.1f else 1f), ObjectAnimator.ofFloat(floatingProgressView, View.ALPHA, if (visible) 1f else 0f), ObjectAnimator.ofFloat(floatingProgressView, View.SCALE_X, if (visible) 1f else 0.1f), ObjectAnimator.ofFloat(floatingProgressView, View.SCALE_Y, if (visible) 1f else 0.1f))

			floatingProgressAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationStart(animation: Animator) {
					floatingProgressView?.visibility = View.VISIBLE
					floatingButton?.visibility = View.VISIBLE
				}

				override fun onAnimationEnd(animation: Animator) {
					if (animation === floatingProgressAnimator) {
						if (visible) {
							floatingButton?.visibility = View.GONE
						}
						else {
							if (floatingButton != null) {
								floatingProgressView?.visibility = View.GONE
							}
						}

						floatingProgressAnimator = null
					}
				}
			})

			floatingProgressAnimator?.duration = 150
			floatingProgressAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
			floatingProgressAnimator?.start()
		}
		else {
			floatingProgressAnimator?.cancel()

			floatingProgressVisible = visible

			if (visible) {
				floatingButton?.alpha = 0f
				floatingButton?.scaleX = 0.1f
				floatingButton?.scaleY = 0.1f
				floatingButton?.visibility = View.GONE

				floatingProgressView?.alpha = 1f
				floatingProgressView?.scaleX = 1f
				floatingProgressView?.scaleY = 1f
				floatingProgressView?.visibility = View.VISIBLE
			}
			else {
				floatingButton?.alpha = 1f
				floatingButton?.scaleX = 1f
				floatingButton?.scaleY = 1f
				floatingButton?.visibility = View.VISIBLE

				floatingProgressView?.alpha = 0f
				floatingProgressView?.scaleX = 0.1f
				floatingProgressView?.scaleY = 0.1f
				floatingProgressView?.visibility = View.GONE
			}
		}
	}

	private fun hideFloatingButton(hide: Boolean) {
		if (floatingHidden == hide || hide && floatingForceVisible) {
			return
		}

		floatingHidden = hide

		val animatorSet = AnimatorSet()
		val valueAnimator = ValueAnimator.ofFloat(floatingButtonHideProgress, if (floatingHidden) 1f else 0f)

		valueAnimator.addUpdateListener {
			floatingButtonHideProgress = it.animatedValue as Float
			floatingButtonTranslation = AndroidUtilities.dp(100f) * floatingButtonHideProgress
			updateFloatingButtonOffset()
		}

		animatorSet.playTogether(valueAnimator)
		animatorSet.duration = 300
		animatorSet.interpolator = floatingInterpolator

		floatingButtonContainer?.isClickable = !hide

		animatorSet.start()
	}

	fun getContactsAlpha(): Float {
		return contactsAlpha
	}

	fun setContactsAlpha(alpha: Float) {
		contactsAlpha = alpha

		viewPages?.forEach { p ->
			val listView = p?.listView ?: return@forEach

			for (i in 0 until listView.childCount) {
				val v = listView.getChildAt(i)

				if (listView.getChildAdapterPosition(v) >= p.dialogsAdapter!!.dialogsCount + 1) {
					v.alpha = alpha
				}
			}
		}
	}

	fun animateContactsAlpha(alpha: Float) {
		contactsAlphaAnimator?.cancel()

		contactsAlphaAnimator = ValueAnimator.ofFloat(contactsAlpha, alpha).setDuration(250)
		contactsAlphaAnimator?.interpolator = CubicBezierInterpolator.DEFAULT

		contactsAlphaAnimator?.addUpdateListener {
			setContactsAlpha(it.animatedValue as Float)
		}

		contactsAlphaAnimator?.start()
	}

//	fun setScrollDisabled(disable: Boolean) {
//		viewPages?.forEach {
//			(it?.listView?.layoutManager as? LinearLayoutManager)?.setScrollDisabled(disable)
//		}
//	}

	private fun updateDialogIndices() {
		val viewPages = viewPages ?: return

		for (viewPage in viewPages) {
			if (viewPage?.visibility != View.VISIBLE) {
				continue
			}

			val dialogs = getDialogsArray(currentAccount, viewPage.dialogsType, folderId, false)
			val count = viewPage.listView?.childCount ?: 0

			for (a in 0 until count) {
				val child = viewPage.listView?.getChildAt(a)

				if (child is DialogCell) {
					val dialog = messagesController.dialogs_dict[child.dialogId] ?: continue
					val index = dialogs.indexOf(dialog)

					if (index < 0) {
						continue
					}

					child.dialogIndex = index
				}
			}
		}
	}

	private fun updateVisibleRows(mask: Int, animated: Boolean = true) {
		if (dialogsListFrozen && mask and MessagesController.UPDATE_MASK_REORDER == 0 || isPaused) {
			return
		}

		for (c in 0..2) {
			var list: RecyclerListView?

			if (c == 2) {
				list = searchViewPager?.searchListView
			}
			else if (viewPages != null) {
				list = if (c < viewPages!!.size) viewPages!![c]?.listView else null

				if (list != null && viewPages!![c]?.visibility != View.VISIBLE) {
					continue
				}
			}
			else {
				continue
			}

			if (list == null) {
				continue
			}

			for (child in list.children) {
				if (child is DialogCell) {
					if (list.adapter !== searchViewPager?.dialogsSearchAdapter) {
						if (mask and MessagesController.UPDATE_MASK_REORDER != 0) {
							child.onReorderStateChanged(actionBar!!.isActionModeShowed, true)

							if (dialogsListFrozen) {
								continue
							}
						}

						if (mask and MessagesController.UPDATE_MASK_CHECK != 0) {
							child.setChecked(false, mask and MessagesController.UPDATE_MASK_CHAT != 0)
						}
						else {
							if (mask and MessagesController.UPDATE_MASK_NEW_MESSAGE != 0) {
								child.checkCurrentDialogIndex(dialogsListFrozen)

								if (viewPages!![c]?.isDefaultDialogType == true && AndroidUtilities.isTablet()) {
									child.setDialogSelected(child.dialogId == openedDialogId)
								}
							}
							else if (mask and MessagesController.UPDATE_MASK_SELECT_DIALOG != 0) {
								if (viewPages!![c]?.isDefaultDialogType == true && AndroidUtilities.isTablet()) {
									child.setDialogSelected(child.dialogId == openedDialogId)
								}
							}
							else {
								child.update(mask, animated)
							}

							if (selectedDialogs.isNotEmpty()) {
								child.setChecked(selectedDialogs.contains(child.dialogId), false)
							}
						}
					}
				}

				if (child is UserCell) {
					child.update(mask)
				}
				else if (child is ProfileSearchCell) {
					child.update(mask)

					if (selectedDialogs.isNotEmpty()) {
						child.setChecked(selectedDialogs.contains(child.dialogId), false)
					}
				}

				if (dialogsListFrozen) {
					continue
				}

				if (child is RecyclerListView) {
					for (child2 in child.children) {
						(child2 as? HintDialogCell)?.update(mask)
					}
				}
			}
		}
	}

	fun setDelegate(dialogsActivityDelegate: DialogsActivityDelegate?) {
		delegate = dialogsActivityDelegate
	}

	open fun shouldShowNextButton(fragment: DialogsActivity?, dids: ArrayList<Long>?, message: CharSequence?, param: Boolean): Boolean {
		return false
	}

	fun setSearchString(string: String?) {
		searchString = string
	}

	fun setInitialSearchString(initialSearchString: String?) {
		this.initialSearchString = initialSearchString
	}

	val isMainDialogList: Boolean
		get() = delegate == null && searchString == null

	fun setInitialSearchType(type: Int) {
		initialSearchType = type
	}

	private fun checkCanWrite(dialogId: Long): Boolean {
		val parentActivity = parentActivity ?: return false

		if (addToGroupAlertString == null && checkCanWrite) {
			if (DialogObject.isChatDialog(dialogId)) {
				val chat = messagesController.getChat(-dialogId)

				if (ChatObject.isChannel(chat) && !chat.megagroup && (cantSendToChannels || !ChatObject.isCanWriteToChannel(-dialogId, currentAccount) || hasPoll == 2)) {
					val builder = AlertDialog.Builder(parentActivity)
					builder.setTitle(parentActivity.getString(R.string.SendMessageTitle))

					if (hasPoll == 2) {
						builder.setMessage(parentActivity.getString(R.string.PublicPollCantForward))
					}
					else {
						builder.setMessage(parentActivity.getString(R.string.ChannelCantSendMessage))
					}

					builder.setNegativeButton(parentActivity.getString(R.string.OK), null)

					showDialog(builder.create())

					return false
				}
			}
			else if (DialogObject.isEncryptedDialog(dialogId) && (hasPoll != 0 || hasInvoice)) {
				val builder = AlertDialog.Builder(parentActivity)
				builder.setTitle(parentActivity.getString(R.string.SendMessageTitle))

				if (hasPoll != 0) {
					builder.setMessage(parentActivity.getString(R.string.PollCantForwardSecretChat))
				}
				else {
					builder.setMessage(parentActivity.getString(R.string.InvoiceCantForwardSecretChat))
				}
				builder.setNegativeButton(parentActivity.getString(R.string.OK), null)

				showDialog(builder.create())

				return false
			}
		}

		return true
	}

	private fun didSelectResult(dialogId: Long, useAlert: Boolean, param: Boolean) {
		val parentActivity = parentActivity ?: return

		if (!checkCanWrite(dialogId)) {
			return
		}

		if (initialDialogsType == 11 || initialDialogsType == 12 || initialDialogsType == 13) {
			if (checkingImportDialog) {
				return
			}

			val user: User?
			val chat: TLRPC.Chat?

			if (DialogObject.isUserDialog(dialogId)) {
				user = messagesController.getUser(dialogId)
				chat = null

				if (user?.mutual_contact != true) {
					getUndoView()?.showWithAction(dialogId, UndoView.ACTION_IMPORT_NOT_MUTUAL, null)
					return
				}
			}
			else {
				user = null
				chat = messagesController.getChat(-dialogId)

				if (!ChatObject.hasAdminRights(chat) || !ChatObject.canChangeChatInfo(chat)) {
					getUndoView()?.showWithAction(dialogId, UndoView.ACTION_IMPORT_GROUP_NOT_ADMIN, null)
					return
				}
			}

			val progressDialog = AlertDialog(parentActivity, 3)

			val req = TLRPC.TL_messages_checkHistoryImportPeer()
			req.peer = messagesController.getInputPeer(dialogId)

			connectionsManager.sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					try {
						progressDialog.dismiss()
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					checkingImportDialog = false

					if (response != null) {
						val res = response as TLRPC.TL_messages_checkedHistoryImportPeer

						AlertsCreator.createImportDialogAlert(this, res.confirm_text, user, chat) {
							setDialogsListFrozen(true)

							val dids = ArrayList<Long>()
							dids.add(dialogId)

							delegate?.didSelectDialogs(this@DialogsActivity, dids, null, param)
						}
					}
					else {
						AlertsCreator.processError(currentAccount, error, this, req)
						notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId, req, error)
					}
				}
			}
			try {
				progressDialog.showDelayed(300)
			}
			catch (e: Exception) {
				// ignored
			}
		}
		else if (useAlert && (selectAlertString != null && selectAlertStringGroup != null || addToGroupAlertString != null)) {
			if (parentActivity == null) {
				return
			}

			val builder = AlertDialog.Builder(parentActivity)
			val title: String
			val message: String
			val buttonText: String

			if (DialogObject.isEncryptedDialog(dialogId)) {
				val chat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))
				val user = messagesController.getUser(chat?.user_id) ?: return
				title = parentActivity.getString(R.string.SendMessageTitle)
				message = LocaleController.formatStringSimple(selectAlertString, getUserName(user))
				buttonText = parentActivity.getString(R.string.Send)
			}
			else if (DialogObject.isUserDialog(dialogId)) {
				if (dialogId == userConfig.getClientUserId()) {
					title = parentActivity.getString(R.string.SendMessageTitle)
					message = LocaleController.formatStringSimple(selectAlertStringGroup, parentActivity.getString(R.string.SavedMessages))
					buttonText = parentActivity.getString(R.string.Send)
				}
				else {
					val user = messagesController.getUser(dialogId)

					if (user == null || selectAlertString == null) {
						return
					}

					title = parentActivity.getString(R.string.SendMessageTitle)
					message = LocaleController.formatStringSimple(selectAlertString, getUserName(user))
					buttonText = parentActivity.getString(R.string.Send)
				}
			}
			else {
				val chat = messagesController.getChat(-dialogId) ?: return

				if (addToGroupAlertString != null) {
					title = parentActivity.getString(R.string.AddToTheGroupAlertTitle)
					message = LocaleController.formatStringSimple(addToGroupAlertString, chat.title)
					buttonText = parentActivity.getString(R.string.Add)
				}
				else {
					title = parentActivity.getString(R.string.SendMessageTitle)
					message = LocaleController.formatStringSimple(selectAlertStringGroup, chat.title)
					buttonText = parentActivity.getString(R.string.Send)
				}
			}

			builder.setTitle(title)
			builder.setMessage(AndroidUtilities.replaceTags(message))

			builder.setPositiveButton(buttonText) { _, _ ->
				didSelectResult(dialogId, useAlert = false, param = false)
			}

			builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

			showDialog(builder.create())
		}
		else {
			if (delegate != null) {
				val dids = ArrayList<Long>()
				dids.add(dialogId)

				delegate?.didSelectDialogs(this@DialogsActivity, dids, null, param)

				if (resetDelegate) {
					delegate = null
				}
			}
			else {
				finishFragment()
			}
		}
	}

	private fun onSendLongClick(view: View): Boolean {
		val parentActivity = parentActivity ?: return false

		val layout = LinearLayout(parentActivity)
		layout.orientation = LinearLayout.VERTICAL

		val sendPopupLayout2 = ActionBarPopupWindowLayout(parentActivity)
		sendPopupLayout2.setAnimationEnabled(false)

		sendPopupLayout2.setOnTouchListener(object : OnTouchListener {
			private val popupRect = Rect()

			override fun onTouch(v: View, event: MotionEvent): Boolean {
				if (event.actionMasked == MotionEvent.ACTION_DOWN) {
					if (sendPopupWindow?.isShowing == true) {
						v.getHitRect(popupRect)

						if (!popupRect.contains(event.x.toInt(), event.y.toInt())) {
							sendPopupWindow?.dismiss()
						}
					}
				}

				return false
			}
		})

		sendPopupLayout2.setDispatchKeyEventListener {
			if (it.keyCode == KeyEvent.KEYCODE_BACK && it.repeatCount == 0 && sendPopupWindow?.isShowing == true) {
				sendPopupWindow?.dismiss()
			}
		}

		sendPopupLayout2.setShownFromBottom(false)
		sendPopupLayout2.setupRadialSelectors(getThemedColor(Theme.key_dialogButtonSelector))

		val sendWithoutSound = ActionBarMenuSubItem(parentActivity, top = true, bottom = true)
		sendWithoutSound.setTextAndIcon(parentActivity.getString(R.string.SendWithoutSound), R.drawable.input_notify_off)
		sendWithoutSound.minimumWidth = AndroidUtilities.dp(196f)

		sendPopupLayout2.addView(sendWithoutSound, createLinear(LayoutHelper.MATCH_PARENT, 48))

		sendWithoutSound.setOnClickListener {
			if (sendPopupWindow?.isShowing == true) {
				sendPopupWindow?.dismiss()
			}

			notify = false

			if (selectedDialogs.isEmpty()) {
				return@setOnClickListener
			}

			delegate?.didSelectDialogs(this@DialogsActivity, selectedDialogs, commentView?.fieldText, false)
		}

		layout.addView(sendPopupLayout2, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		sendPopupWindow = ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
		sendPopupWindow?.setAnimationEnabled(false)
		sendPopupWindow?.animationStyle = R.style.PopupContextAnimation2
		sendPopupWindow?.isOutsideTouchable = true
		sendPopupWindow?.isClippingEnabled = true
		sendPopupWindow?.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
		sendPopupWindow?.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
		sendPopupWindow?.contentView?.isFocusableInTouchMode = true

		SharedConfig.removeScheduledOrNoSoundHint()

		layout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), View.MeasureSpec.AT_MOST))

		sendPopupWindow?.isFocusable = true

		val location = IntArray(2)

		view.getLocationInWindow(location)

		val y = location[1] - layout.measuredHeight - AndroidUtilities.dp(2f)

		sendPopupWindow?.showAtLocation(view, Gravity.LEFT or Gravity.TOP, location[0] + view.measuredWidth - layout.measuredWidth + AndroidUtilities.dp(8f), y)
		sendPopupWindow?.dimBehind()

		view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

		return false
	}

	private fun updateFloatingButtonColor() {
		val context = context ?: return
		val floatingButtonContainer = floatingButtonContainer ?: return
		val drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56f), ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.darker_brand, null))

		floatingButtonContainer.background = drawable
	}

	override fun getCustomSlideTransition(topFragment: Boolean, backAnimation: Boolean, distanceToMove: Float): Animator {
		if (backAnimation) {
			return ValueAnimator.ofFloat(slideFragmentProgress, 1f).also {
				slideBackTransitionAnimator = it
			}
		}

		val duration = (max((200.0f / layoutContainer.measuredWidth * distanceToMove).toInt(), 80) * 1.2f).toInt()

		return ValueAnimator.ofFloat(slideFragmentProgress, 1f).also {
			it.addUpdateListener { animator ->
				setSlideTransitionProgress(animator.animatedValue as Float)
			}

			it.interpolator = CubicBezierInterpolator.EASE_OUT
			it.duration = duration.toLong()
			it.start()

			slideBackTransitionAnimator = it
		}
	}

	override fun prepareFragmentToSlide(topFragment: Boolean, beginSlide: Boolean) {
		if (!topFragment && beginSlide) {
			isSlideBackTransition = true
			setFragmentIsSliding(true)
		}
		else {
			slideBackTransitionAnimator = null
			isSlideBackTransition = false
			setFragmentIsSliding(false)
			setSlideTransitionProgress(1f)
		}
	}

	private fun setFragmentIsSliding(sliding: Boolean) {
		if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
			return
		}

		if (sliding) {
			viewPages?.firstOrNull()?.let {
				it.setLayerType(View.LAYER_TYPE_HARDWARE, null)
				it.clipChildren = false
				it.clipToPadding = false
				it.listView?.clipChildren = false
			}

			actionBar?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
			filterTabsView?.listView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)

			(fragmentView as? ViewGroup)?.clipChildren = false
			fragmentView?.requestLayout()
		}
		else {
			viewPages?.forEach {
				if (it != null) {
					it.setLayerType(View.LAYER_TYPE_NONE, null)
					it.clipChildren = true
					it.clipToPadding = true
					it.listView?.clipChildren = true
				}
			}

			actionBar?.setLayerType(View.LAYER_TYPE_NONE, null)
			filterTabsView?.listView?.setLayerType(View.LAYER_TYPE_NONE, null)

			(fragmentView as? ViewGroup)?.clipChildren = true
			fragmentView?.requestLayout()
		}
	}

	override fun onSlideProgress(isOpen: Boolean, progress: Float) {
		if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
			return
		}

		if (isSlideBackTransition && slideBackTransitionAnimator == null) {
			setSlideTransitionProgress(progress)
		}
	}

	private fun setSlideTransitionProgress(progress: Float) {
		if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
			return
		}

		slideFragmentProgress = progress

		fragmentView?.invalidate()

		filterTabsView?.let { filterTabsView ->
			val s = 1f - 0.05f * (1f - slideFragmentProgress)

			filterTabsView.listView.scaleX = s
			filterTabsView.listView.scaleY = s
			filterTabsView.listView.translationX = (if (isDrawerTransition) AndroidUtilities.dp(4f) else -AndroidUtilities.dp(4f)) * (1f - slideFragmentProgress)
			filterTabsView.listView.pivotX = (if (isDrawerTransition) filterTabsView.measuredWidth else 0).toFloat()
			filterTabsView.listView.pivotY = 0f
			filterTabsView.invalidate()
		}
	}

	override fun setProgressToDrawerOpened(progress: Float) {
		if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW || isSlideBackTransition) {
			return
		}

		@Suppress("NAME_SHADOWING") var progress = progress
		var drawerTransition = progress > 0

		if (searchIsShowed) {
			drawerTransition = false
			progress = 0f
		}

		if (drawerTransition != isDrawerTransition) {
			isDrawerTransition = drawerTransition
			setFragmentIsSliding(isDrawerTransition)
			fragmentView?.requestLayout()
		}

		setSlideTransitionProgress(1f - progress)
	}

	fun setShowSearch(query: String, i: Int) {
		if (!searching) {
			initialSearchType = i
			actionBar?.openSearchField(query, false)
		}
		else {
			if (searchItem?.searchField?.text?.toString() != query) {
				searchItem?.searchField?.setText(query)
			}

			searchViewPager?.let { searchViewPager ->
				val p = searchViewPager.getPositionForType(i)

				if (p >= 0) {
					if (searchViewPager.tabsView?.currentTabId != p) {
						searchViewPager.tabsView?.scrollToTab(p, p)
					}
				}
			}
		}
	}

	override fun isLightStatusBar(): Boolean {
		return !AndroidUtilities.isDarkTheme()
	}

	override fun shouldShowBottomNavigationPanel(): Boolean {
		return shouldShowBottomNavigationPanel
	}

	var invite: TLRPC.TL_chatInviteExported? = null

	private fun loadInviteLinks() {
		val req = TLRPC.TL_messages_getExportedChatInvites()
		val userId = userConfig.clientUserId
		req.peer = messagesController.getInputPeer(userId)
		req.admin_id = messagesController.getInputUser(userConfig.clientUserId)

		connectionsManager.sendRequest(req) { response, error ->
			if (error == null) {
				val invites: TLRPC.TL_messages_exportedChatInvites = response as TLRPC.TL_messages_exportedChatInvites

				if (invites.count > 0) {
					val chatInvite: TLRPC.ExportedChatInvite? = invites.invites?.get(0)

					if (chatInvite != null) {
						invite = (chatInvite as TLRPC.TL_chatInviteExported)
					}
				}
			}
		}
	}

	private var loadingInviteUser = false

	fun openInviteFragment() {
		val userId = userConfig.clientUserId
		val userFull = messagesController.getUserFull(userId)

		if (userFull != null) {
			val fragment = ManageLinksActivity(userId, invite?.admin_id ?: 0L)
			fragment.setInfo(userFull, invite)
			presentFragment(fragment)
		}
		else {
			Toast.makeText(parentActivity, R.string.user_info_is_loading, Toast.LENGTH_SHORT).show()

			if (loadingInviteUser) {
				return
			}

			loadingInviteUser = true

			notificationCenter.addObserver(object : NotificationCenterDelegate {
				override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
					val innerUserId = args[0] as Long

					if (innerUserId > 0 && innerUserId == userId) {
						loadingInviteUser = false

						val user = messagesController.getUserFull(userId)

						if (user != null) {
							val fragment = ManageLinksActivity(innerUserId, invite?.admin_id ?: 0L)
							fragment.setInfo(user, invite)
							presentFragment(fragment)
						}
					}

					notificationCenter.removeObserver(this, id)
				}
			}, NotificationCenter.userInfoDidLoad)
		}
	}

	fun openTipsFragment() {
		(parentActivity as? LaunchActivity)?.runLinkRequest(currentAccount, BuildConfig.USER_TIPS_USER)
	}

	fun switchToFeedFragment() {
		forYouTab = 2
		val forYouTab = forYouTab

		(parentActivity as? LaunchActivity)?.switchToFeedFragment(forYouTab)
	}

	fun interface DialogsActivityDelegate {
		fun didSelectDialogs(dialogsFragment: DialogsActivity?, dids: List<Long>, message: CharSequence?, param: Boolean)
	}

	open class ViewPage(context: Context) : FrameLayout(context) {
		internal var swipeController: SwipeController? = null
		var listView: DialogsRecyclerView? = null
		var layoutManager: LinearLayoutManager? = null
		var dialogsAdapter: DialogsAdapter? = null
		var itemTouchHelper: ItemTouchHelper? = null
		var selectedType = 0
		var pullForegroundDrawable: PullForegroundDrawable? = null
		var scrollHelper: RecyclerAnimationScrollHelper? = null
		var dialogsType = 0
		var archivePullViewState = 0
		var progressView: FlickerLoadingView? = null
		var lastItemsCount = 0
		var dialogsItemAnimator: DialogsItemAnimator? = null
		var recyclerItemsEnterAnimator: RecyclerItemsEnterAnimator? = null
		var isLocked = false

		val isDefaultDialogType: Boolean
			get() = dialogsType == 0 || dialogsType == 7 || dialogsType == 8
	}

	class DialogsHeader(var headerType: Int) : TLRPC.Dialog() {
		companion object {
			const val HEADER_TYPE_MY_CHANNELS = 0
			const val HEADER_TYPE_MY_GROUPS = 1
			const val HEADER_TYPE_GROUPS = 2
		}
	}

	private inner class ContentView(context: Context) : SizeNotifierFrameLayout(context) {
		private val actionBarSearchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val windowBackgroundPaint = Paint()
		private val pos = IntArray(2)
		private var inputFieldHeight = 0
		private var startedTrackingPointerId = 0
		private var startedTrackingX = 0
		private var startedTrackingY = 0
		private var velocityTracker: VelocityTracker? = null
		private var wasPortrait = false

		init {
			needBlur = true
			blurBehindViews.add(this)
		}

		private fun prepareForMoving(ev: MotionEvent, forward: Boolean): Boolean {
			val id = filterTabsView!!.getNextPageId(forward)

			if (id < 0) {
				return false
			}

			parent.requestDisallowInterceptTouchEvent(true)

			maybeStartTracking = false
			startedTracking = true
			startedTrackingX = (ev.x + additionalOffset).toInt()
			actionBar?.isEnabled = false
			filterTabsView?.isEnabled = false

			viewPages?.getOrNull(1)?.let {
				it.selectedType = id
				it.visibility = VISIBLE
			}

			animatingForward = forward
			showScrollbars(false)
			switchToCurrentSelectedMode(true)

			if (forward) {
				viewPages!![1]?.translationX = viewPages!![0]!!.measuredWidth.toFloat()
			}
			else {
				viewPages!![1]?.translationX = -viewPages!![0]!!.measuredWidth.toFloat()
			}

			return true
		}

		override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
			topPadding = top

			updateContextViewPosition()

			if (whiteActionBar && searchViewPager != null) {
				searchViewPager?.translationY = (topPadding - lastMeasuredTopPadding).toFloat()
			}
			else {
				requestLayout()
			}
		}

		fun checkTabsAnimationInProgress(): Boolean {
			if (tabsAnimationInProgress) {
				var cancel = false

				if (backAnimation) {
					if (abs(viewPages!![0]!!.translationX) < 1) {
						viewPages!![0]?.translationX = 0f
						viewPages!![1]?.translationX = (viewPages!![0]!!.measuredWidth * if (animatingForward) 1 else -1).toFloat()
						cancel = true
					}
				}
				else if (abs(viewPages!![1]!!.translationX) < 1) {
					viewPages!![0]?.translationX = (viewPages!![0]!!.measuredWidth * if (animatingForward) -1 else 1).toFloat()
					viewPages!![1]?.translationX = 0f
					cancel = true
				}
				if (cancel) {
					showScrollbars(true)
					tabsAnimation?.cancel()
					tabsAnimation = null
					tabsAnimationInProgress = false
				}

				return tabsAnimationInProgress
			}

			return false
		}

		val actionBarFullHeight: Int
			get() {
				var h = actionBar!!.height.toFloat()
				var filtersTabsHeight = 0f

				if (filterTabsView != null && filterTabsView?.visibility != GONE) {
					filtersTabsHeight = filterTabsView!!.measuredHeight - (1f - filterTabsProgress) * filterTabsView!!.measuredHeight
				}

				var searchTabsHeight = 0f

				if (searchTabsView != null && searchTabsView?.visibility != GONE) {
					searchTabsHeight = searchTabsView!!.measuredHeight.toFloat()
				}

				h += filtersTabsHeight * (1f - searchAnimationProgress) + searchTabsHeight * searchAnimationProgress

				return h.toInt()
			}

		override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
			if (child === fragmentContextView && fragmentContextView?.isCallStyle == true) {
				return true
			}

			if (child === blurredView) {
				return true
			}

			val result: Boolean

			if (child === viewPages!![0] || viewPages!!.size > 1 && child === viewPages!![1] || child === fragmentContextView || child === fragmentLocationContextView || child === searchViewPager) {
				canvas.save()
				canvas.clipRect(0f, -y + actionBar!!.y + actionBarFullHeight, measuredWidth.toFloat(), measuredHeight.toFloat())

				if (slideFragmentProgress != 1f) {
					val s = 1f - 0.05f * (1f - slideFragmentProgress)
					canvas.translate((if (isDrawerTransition) AndroidUtilities.dp(4f)
					else -AndroidUtilities.dp(4f)) * (1f - slideFragmentProgress), 0f)
					canvas.scale(s, s, if (isDrawerTransition) measuredWidth.toFloat() else 0f, -y + actionBar!!.y + actionBarFullHeight)
				}

				result = super.drawChild(canvas, child, drawingTime)

				canvas.restore()
			}
			else if (child === actionBar && slideFragmentProgress != 1f) {
				canvas.save()

				val s = 1f - 0.05f * (1f - slideFragmentProgress)

				canvas.translate((if (isDrawerTransition) AndroidUtilities.dp(4f) else -AndroidUtilities.dp(4f)) * (1f - slideFragmentProgress), 0f)
				canvas.scale(s, s, if (isDrawerTransition) measuredWidth.toFloat() else 0f, (if (actionBar?.occupyStatusBar == true) AndroidUtilities.statusBarHeight else 0) + ActionBar.getCurrentActionBarHeight() / 2f)

				result = super.drawChild(canvas, child, drawingTime)

				canvas.restore()
			}
			else {
				result = super.drawChild(canvas, child, drawingTime)
			}

			if (child === actionBar && parentLayout != null) {
				val drawShadow = viewPages!![0]!!.dialogsAdapter!!.dialogsCount > 0

				if (drawShadow) {
					val y = (actionBar!!.y + actionBarFullHeight).toInt()

					parentLayout?.drawHeaderShadow(canvas, (255 * (1f - searchAnimationProgress)).toInt(), y)

					if (searchAnimationProgress > 0) {
						if (searchAnimationProgress < 1) {
							val a = Theme.dividerPaint.alpha

							Theme.dividerPaint.alpha = (a * searchAnimationProgress).toInt()

							canvas.drawLine(0f, y.toFloat(), measuredWidth.toFloat(), y.toFloat(), Theme.dividerPaint)

							Theme.dividerPaint.alpha = a
						}
						else {
							canvas.drawLine(0f, y.toFloat(), measuredWidth.toFloat(), y.toFloat(), Theme.dividerPaint)
						}
					}
				}
			}

			return result
		}

		override fun dispatchDraw(canvas: Canvas) {
			val actionBarHeight = actionBarFullHeight

			val top = if (inPreviewMode) {
				AndroidUtilities.statusBarHeight
			}
			else {
				(-y + actionBar!!.y).toInt()
			}

			if (whiteActionBar) {
				if (searchAnimationProgress == 1f) {
					actionBarSearchPaint.color = context.getColor(R.color.background)

					if (searchTabsView != null) {
						searchTabsView?.translationY = 0f
						searchTabsView?.alpha = 1f

						filtersView?.translationY = 0f
						filtersView?.alpha = 1f
					}
				}
				else if (searchAnimationProgress == 0f) {
					if (filterTabsView?.visibility == VISIBLE) {
						filterTabsView?.translationY = actionBar!!.translationY
					}
				}

				AndroidUtilities.rectTmp2[0, top, measuredWidth] = top + actionBarHeight

				drawBlurRect(canvas, 0f, AndroidUtilities.rectTmp2, if (searchAnimationProgress == 1f) actionBarSearchPaint else actionBarDefaultPaint, true)

				if (searchAnimationProgress > 0 && searchAnimationProgress < 1f) {
					actionBarSearchPaint.color = ColorUtils.blendARGB(if (folderId == 0) context.getColor(R.color.background) else context.getColor(R.color.dark_gray), context.getColor(R.color.background), searchAnimationProgress)

					if (searchIsShowed || !searchWasFullyShowed) {
						canvas.save()
						canvas.clipRect(0, top, measuredWidth, top + actionBarHeight)

						val cX = (measuredWidth - AndroidUtilities.dp(24f)).toFloat()
						val statusBarH = if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0
						val cY = statusBarH + (actionBar!!.measuredHeight - statusBarH) / 2f

						drawBlurCircle(canvas, 0f, cX, cY, measuredWidth * 1.3f * searchAnimationProgress, actionBarSearchPaint, true)

						canvas.restore()
					}
					else {
						AndroidUtilities.rectTmp2[0, top, measuredWidth] = top + actionBarHeight
						drawBlurRect(canvas, 0f, AndroidUtilities.rectTmp2, actionBarSearchPaint, true)
					}

					if (filterTabsView?.visibility == VISIBLE) {
						filterTabsView?.translationY = (actionBarHeight - (actionBar!!.height + filterTabsView!!.measuredHeight)).toFloat()
					}

					if (searchTabsView != null) {
						val y = (actionBarHeight - (actionBar!!.height + searchTabsView!!.measuredHeight)).toFloat()

						val alpha = if (searchAnimationTabsDelayedCrossfade) {
							if (searchAnimationProgress < 0.5f) 0f else (searchAnimationProgress - 0.5f) / 0.5f
						}
						else {
							searchAnimationProgress
						}

						searchTabsView?.translationY = y
						searchTabsView?.alpha = alpha

						filtersView?.translationY = y
						filtersView?.alpha = alpha
					}
				}
			}
			else if (!inPreviewMode) {
				if (progressToActionMode > 0) {
					actionBarSearchPaint.color = ColorUtils.blendARGB(if (folderId == 0) context.getColor(R.color.background) else context.getColor(R.color.dark_gray), context.getColor(R.color.background), progressToActionMode)
					AndroidUtilities.rectTmp2.set(0, top, measuredWidth, top + actionBarHeight)
					drawBlurRect(canvas, 0f, AndroidUtilities.rectTmp2, actionBarSearchPaint, true)
				}
				else {
					AndroidUtilities.rectTmp2.set(0, top, measuredWidth, top + actionBarHeight)
					drawBlurRect(canvas, 0f, AndroidUtilities.rectTmp2, actionBarDefaultPaint, true)
				}
			}

			tabsYOffset = 0f

			if (filtersTabAnimator != null && filterTabsView?.visibility == VISIBLE) {
				tabsYOffset = -(1f - filterTabsProgress) * filterTabsView!!.measuredHeight
				filterTabsView?.translationY = actionBar!!.translationY + tabsYOffset
				filterTabsView?.alpha = filterTabsProgress
				viewPages!![0]?.translationY = -(1f - filterTabsProgress) * filterTabsMoveFrom
			}
			else if (filterTabsView?.visibility == VISIBLE) {
				filterTabsView?.translationY = actionBar!!.translationY
				filterTabsView?.alpha = 1f
			}

			updateContextViewPosition()

			super.dispatchDraw(canvas)

			if (whiteActionBar && searchAnimationProgress > 0 && searchAnimationProgress < 1f && searchTabsView != null) {
				windowBackgroundPaint.color = context.getColor(R.color.background)
				windowBackgroundPaint.alpha = (windowBackgroundPaint.alpha * searchAnimationProgress).toInt()

				canvas.drawRect(0f, (top + actionBarHeight).toFloat(), measuredWidth.toFloat(), (top + actionBar!!.measuredHeight + searchTabsView!!.measuredHeight).toFloat(), windowBackgroundPaint)
			}

			if (fragmentContextView?.isCallStyle == true) {
				canvas.save()
				canvas.translate(fragmentContextView!!.x, fragmentContextView!!.y)

				if (slideFragmentProgress != 1f) {
					val s = 1f - 0.05f * (1f - slideFragmentProgress)

					canvas.translate((if (isDrawerTransition) AndroidUtilities.dp(4f) else -AndroidUtilities.dp(4f)) * (1f - slideFragmentProgress), 0f)

					canvas.scale(s, 1f, if (isDrawerTransition) measuredWidth.toFloat() else 0f, fragmentContextView!!.y)
				}

				fragmentContextView?.setDrawOverlay(true)
				fragmentContextView?.draw(canvas)
				fragmentContextView?.setDrawOverlay(false)

				canvas.restore()
			}
			if (blurredView?.visibility == VISIBLE) {
				if (blurredView?.alpha != 1f) {
					if (blurredView?.alpha != 0f) {
						canvas.saveLayerAlpha(blurredView!!.left.toFloat(), blurredView!!.top.toFloat(), blurredView!!.right.toFloat(), blurredView!!.bottom.toFloat(), (255 * blurredView!!.alpha).toInt())
						canvas.translate(blurredView!!.left.toFloat(), blurredView!!.top.toFloat())

						blurredView?.draw(canvas)

						canvas.restore()
					}
				}
				else {
					blurredView?.draw(canvas)
				}
			}

			if (scrimView != null) {
				canvas.drawRect(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), scrimPaint!!)
				canvas.save()

				getLocationInWindow(pos)

				canvas.translate((scrimViewLocation[0] - pos[0]).toFloat(), scrimViewLocation[1].toFloat())

				scrimViewBackground?.let {
					it.alpha = if (scrimViewAppearing) 255 else (scrimPaint!!.alpha / 50f * 255f).toInt()
					it.setBounds(0, 0, scrimView!!.width, scrimView!!.height)
					it.draw(canvas)
				}

				val selectorDrawable = filterTabsView?.listView?.selectorDrawable

				if (scrimViewAppearing && selectorDrawable != null) {
					canvas.save()
					val selectorBounds = selectorDrawable.bounds
					canvas.translate(-selectorBounds.left.toFloat(), -selectorBounds.top.toFloat())
					selectorDrawable.draw(canvas)
					canvas.restore()
				}

				scrimView?.draw(canvas)

				if (scrimViewSelected) {
					filterTabsView?.selectorDrawable?.let {
						canvas.translate(-scrimViewLocation[0].toFloat(), (-it.intrinsicHeight - 1).toFloat())
						it.draw(canvas)
					}
				}

				canvas.restore()
			}
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val widthSize = MeasureSpec.getSize(widthMeasureSpec)
			var heightSize = MeasureSpec.getSize(heightMeasureSpec)
			val portrait = heightSize > widthSize

			setMeasuredDimension(widthSize, heightSize)

			heightSize -= paddingTop

			doneItem?.updateLayoutParams<LayoutParams> {
				topMargin = if (actionBar?.occupyStatusBar == true) AndroidUtilities.statusBarHeight else 0
				height = ActionBar.getCurrentActionBarHeight()
			}

			measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0)

			val keyboardSize = measureKeyboardHeight()
			val childCount = childCount

			commentView?.let { commentView ->
				measureChildWithMargins(commentView, widthMeasureSpec, 0, heightMeasureSpec, 0)
				val tag = commentView.tag

				if (tag == 2) {
					if (keyboardSize <= AndroidUtilities.dp(20f) && !AndroidUtilities.isInMultiwindow) {
						heightSize -= commentView.emojiPadding
					}

					inputFieldHeight = commentView.measuredHeight
				}
				else {
					inputFieldHeight = 0
				}

				if (SharedConfig.smoothKeyboard && commentView.isPopupShowing) {
					fragmentView?.translationY = 0f

					viewPages?.forEach {
						it?.translationY = 0f
					}

					if (!onlySelect) {
						actionBar?.translationY = 0f
					}

					searchViewPager?.translationY = 0f
				}
			}

			for (i in 0 until childCount) {
				val child = getChildAt(i)

				if (child == null || child.visibility == GONE || child === commentView || child === actionBar) {
					continue
				}

				if (child is DatabaseMigrationHint) {
					val contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY)
					val h = MeasureSpec.getSize(heightMeasureSpec) + keyboardSize
					val contentHeightSpec = MeasureSpec.makeMeasureSpec(max(AndroidUtilities.dp(10f), h - inputFieldHeight + AndroidUtilities.dp(2f) - actionBar!!.measuredHeight), MeasureSpec.EXACTLY)
					child.measure(contentWidthSpec, contentHeightSpec)
				}
				else if (child is ViewPage) {
					val contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY)

					var h = if (filterTabsView?.visibility == VISIBLE) {
						heightSize - inputFieldHeight + AndroidUtilities.dp(2f) - AndroidUtilities.dp(44f) - topPadding
					}
					else {
						heightSize - inputFieldHeight + AndroidUtilities.dp(2f) - (if (onlySelect) 0 else actionBar!!.measuredHeight) - topPadding
					}

					if (filtersTabAnimator != null && filterTabsView?.visibility == VISIBLE) {
						h += filterTabsMoveFrom.toInt()
					}
					else {
						child.setTranslationY(0f)
					}

					val transitionPadding = if (isSlideBackTransition || isDrawerTransition) (h * 0.05f).toInt() else 0

					h += transitionPadding

					child.setPadding(child.getPaddingLeft(), child.getPaddingTop(), child.getPaddingRight(), transitionPadding)
					child.measure(contentWidthSpec, MeasureSpec.makeMeasureSpec(max(AndroidUtilities.dp(10f), h), MeasureSpec.EXACTLY))
					child.setPivotX((child.getMeasuredWidth() / 2).toFloat())
				}
				else if (child === searchViewPager) {
					searchViewPager?.translationY = 0f

					val contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY)
					val h = MeasureSpec.getSize(heightMeasureSpec) + keyboardSize
					val contentHeightSpec = MeasureSpec.makeMeasureSpec(max(AndroidUtilities.dp(10f), h - inputFieldHeight + AndroidUtilities.dp(2f) - (if (onlySelect) 0 else actionBar!!.measuredHeight) - topPadding) - if (searchTabsView == null) 0 else AndroidUtilities.dp(44f), MeasureSpec.EXACTLY)

					child.measure(contentWidthSpec, contentHeightSpec)
					child.setPivotX((child.getMeasuredWidth() / 2).toFloat())
				}
				else if (commentView?.isPopupView(child) == true) {
					if (AndroidUtilities.isInMultiwindow) {
						if (AndroidUtilities.isTablet()) {
							child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(min(AndroidUtilities.dp(320f), heightSize - inputFieldHeight - AndroidUtilities.statusBarHeight + paddingTop), MeasureSpec.EXACTLY))
						}
						else {
							child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - inputFieldHeight - AndroidUtilities.statusBarHeight + paddingTop, MeasureSpec.EXACTLY))
						}
					}
					else {
						child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.layoutParams.height, MeasureSpec.EXACTLY))
					}
				}
				else {
					measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
				}
			}

			if (portrait != wasPortrait) {
				post {
					selectAnimatedEmojiDialog?.dismiss()
					selectAnimatedEmojiDialog = null
				}

				wasPortrait = portrait
			}
		}

		override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
			val count = childCount
			val paddingBottom: Int
			val tag = commentView?.tag
			val keyboardSize = measureKeyboardHeight()

			paddingBottom = if (tag != null && tag == 2) {
				if (keyboardSize <= AndroidUtilities.dp(20f) && !AndroidUtilities.isInMultiwindow) (commentView?.emojiPadding ?: 0) else 0
			}
			else {
				0
			}

			setBottomClip(paddingBottom)

			lastMeasuredTopPadding = topPadding

			for (i in -1 until count) {
				val child = if (i == -1) commentView else getChildAt(i)

				if (child == null || child.visibility == GONE) {
					continue
				}

				val lp = child.layoutParams as LayoutParams
				val width = child.measuredWidth
				val height = child.measuredHeight
				var childLeft: Int
				var childTop: Int
				var gravity = lp.gravity

				if (gravity == -1) {
					gravity = Gravity.TOP or Gravity.LEFT
				}

				val absoluteGravity = gravity and Gravity.HORIZONTAL_GRAVITY_MASK
				val verticalGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK

				childLeft = when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
					Gravity.CENTER_HORIZONTAL -> (r - l - width) / 2 + lp.leftMargin - lp.rightMargin
					Gravity.RIGHT -> r - width - lp.rightMargin
					Gravity.LEFT -> lp.leftMargin
					else -> lp.leftMargin
				}

				childTop = when (verticalGravity) {
					Gravity.TOP -> lp.topMargin + paddingTop
					Gravity.CENTER_VERTICAL -> (b - paddingBottom - t - height) / 2 + lp.topMargin - lp.bottomMargin
					Gravity.BOTTOM -> b - paddingBottom - t - height - lp.bottomMargin
					else -> lp.topMargin
				}

				if (commentView?.isPopupView(child) == true) {
					childTop = if (AndroidUtilities.isInMultiwindow) {
						commentView!!.top - child.measuredHeight + AndroidUtilities.dp(1f)
					}
					else {
						commentView!!.bottom
					}
				}
				else if (child === filterTabsView || child === searchTabsView || child === filtersView) {
					// MARK: check if this works properly for all child types
					// childTop = actionBar?.measuredHeight ?: 0
					childTop = 0
				}
				else if (child === searchViewPager) {
					childTop = (if (onlySelect) 0 else (actionBar?.measuredHeight ?: 0)) + topPadding + if (searchTabsView == null) 0 else AndroidUtilities.dp(44f)
				}
				else if (child is DatabaseMigrationHint) {
					childTop = actionBar?.measuredHeight ?: 0
				}
				else if (child is ViewPage) {
					if (!onlySelect) {
						childTop = if (filterTabsView?.visibility == VISIBLE) {
							AndroidUtilities.dp(44f)
						}
						else {
							actionBar?.measuredHeight ?: 0
						}
					}

					childTop += topPadding
				}
				else if (child is FragmentContextView) {
					childTop += (actionBar?.measuredHeight ?: 0)
				}
				else if (child === floatingButtonContainer && selectAnimatedEmojiDialog != null) {
					childTop += keyboardSize
				}

				child.layout(childLeft, childTop, childLeft + width, childTop + height)
			}

			searchViewPager?.setKeyboardHeight(keyboardSize)
			notifyHeightChanged()
			updateContextViewPosition()
			updateCommentView()
		}

		override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
			val action = ev.actionMasked

			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				if (actionBar?.isActionModeShowed == true) {
					allowMoving = true
				}
			}

			return checkTabsAnimationInProgress() || filterTabsView?.isAnimatingIndicator == true || onTouchEvent(ev)
		}

		override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
			if (maybeStartTracking && !startedTracking) {
				onTouchEvent(null)
			}

			super.requestDisallowInterceptTouchEvent(disallowIntercept)
		}

		@SuppressLint("ClickableViewAccessibility")
		override fun onTouchEvent(ev: MotionEvent?): Boolean {
			if (parentLayout != null && filterTabsView != null && !filterTabsView!!.isEditing && !searching && !parentLayout!!.checkTransitionAnimation() && !parentLayout!!.isInPreviewMode && !parentLayout!!.isPreviewOpenAnimationInProgress && (ev == null || startedTracking || ev.y > actionBar!!.measuredHeight + actionBar!!.translationY) && SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_FOLDERS) {
				if (ev != null) {
					if (velocityTracker == null) {
						velocityTracker = VelocityTracker.obtain()
					}

					velocityTracker?.addMovement(ev)
				}

				if (ev != null && ev.action == MotionEvent.ACTION_DOWN && checkTabsAnimationInProgress()) {
					startedTracking = true
					startedTrackingPointerId = ev.getPointerId(0)
					startedTrackingX = ev.x.toInt()

					if (animatingForward) {
						if (startedTrackingX < viewPages!![0]!!.measuredWidth + viewPages!![0]!!.translationX) {
							additionalOffset = viewPages!![0]!!.translationX
						}
						else {
							val page = viewPages!![0]
							viewPages!![0] = viewPages!![1]
							viewPages!![1] = page
							animatingForward = false
							additionalOffset = viewPages!![0]!!.translationX

							filterTabsView!!.selectTabWithId(viewPages!![0]!!.selectedType, 1f)
							filterTabsView!!.selectTabWithId(viewPages!![1]!!.selectedType, additionalOffset / viewPages!![0]!!.measuredWidth)

							switchToCurrentSelectedMode(true)

							viewPages!![0]!!.dialogsAdapter?.resume()
							viewPages!![1]!!.dialogsAdapter?.pause()
						}
					}
					else {
						if (startedTrackingX < viewPages!![1]!!.measuredWidth + viewPages!![1]!!.translationX) {
							val page = viewPages!![0]
							viewPages!![0] = viewPages!![1]
							viewPages!![1] = page
							animatingForward = true
							additionalOffset = viewPages!![0]!!.translationX

							filterTabsView!!.selectTabWithId(viewPages!![0]!!.selectedType, 1f)
							filterTabsView!!.selectTabWithId(viewPages!![1]!!.selectedType, -additionalOffset / viewPages!![0]!!.measuredWidth)

							switchToCurrentSelectedMode(true)

							viewPages!![0]?.dialogsAdapter?.resume()
							viewPages!![1]?.dialogsAdapter?.pause()
						}
						else {
							additionalOffset = viewPages!![0]!!.translationX
						}
					}

					tabsAnimation?.removeAllListeners()
					tabsAnimation?.cancel()

					tabsAnimationInProgress = false
				}
				else if (ev != null && ev.action == MotionEvent.ACTION_DOWN) {
					additionalOffset = 0f
				}

				if (ev != null && ev.action == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking && filterTabsView?.visibility == VISIBLE) {
					startedTrackingPointerId = ev.getPointerId(0)
					maybeStartTracking = true
					startedTrackingX = ev.x.toInt()
					startedTrackingY = ev.y.toInt()
					velocityTracker?.clear()
				}
				else if (ev != null && ev.action == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
					val dx = (ev.x - startedTrackingX + additionalOffset).toInt()
					val dy = abs(ev.y.toInt() - startedTrackingY)

					if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
						if (!prepareForMoving(ev, dx < 0)) {
							maybeStartTracking = true
							startedTracking = false
							viewPages!![0]?.translationX = 0f
							viewPages!![1]?.translationX = if (animatingForward) viewPages!![0]!!.measuredWidth.toFloat() else -viewPages!![0]!!.measuredWidth.toFloat()
							filterTabsView?.selectTabWithId(viewPages!![1]!!.selectedType, 0f)
						}
					}

					if (maybeStartTracking && !startedTracking) {
						val touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true)
						val dxLocal = (ev.x - startedTrackingX).toInt()

						if (abs(dxLocal) >= touchSlop && abs(dxLocal) > dy) {
							prepareForMoving(ev, dx < 0)
						}
					}
					else if (startedTracking) {
						viewPages!![0]?.translationX = dx.toFloat()
						if (animatingForward) {
							viewPages!![1]?.translationX = (viewPages!![0]!!.measuredWidth + dx).toFloat()
						}
						else {
							viewPages!![1]?.translationX = (dx - viewPages!![0]!!.measuredWidth).toFloat()
						}

						val scrollProgress = abs(dx) / viewPages!![0]!!.measuredWidth.toFloat()

						if (viewPages!![1]!!.isLocked && scrollProgress > 0.3f) {
							dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0))

							filterTabsView?.shakeLock(viewPages!![1]!!.selectedType)

							AndroidUtilities.runOnUIThread({
								showDialog(LimitReachedBottomSheet(this@DialogsActivity, LimitReachedBottomSheet.TYPE_FOLDERS, currentAccount))
							}, 200)

							return false
						}
						else {
							filterTabsView?.selectTabWithId(viewPages!![1]!!.selectedType, scrollProgress)
						}
					}
				}
				else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.action == MotionEvent.ACTION_CANCEL || ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_POINTER_UP)) {
					velocityTracker?.computeCurrentVelocity(1000, maximumVelocity.toFloat())

					var velX: Float
					val velY: Float

					if (ev != null && ev.action != MotionEvent.ACTION_CANCEL) {
						velX = velocityTracker?.xVelocity ?: 0f
						velY = velocityTracker?.yVelocity ?: 0f

						if (!startedTracking) {
							if (abs(velX) >= 3000 && abs(velX) > abs(velY)) {
								prepareForMoving(ev, velX < 0)
							}
						}
					}
					else {
						velX = 0f
						velY = 0f
					}

					if (startedTracking) {
						val x = viewPages!![0]!!.x

						tabsAnimation = AnimatorSet()

						if (viewPages!![1]!!.isLocked) {
							backAnimation = true
						}
						else {
							if (additionalOffset != 0f) {
								backAnimation = if (abs(velX) > 1500) {
									if (animatingForward) velX > 0 else velX < 0
								}
								else {
									if (animatingForward) {
										viewPages!![1]!!.x > viewPages!![0]!!.measuredWidth shr 1
									}
									else {
										viewPages!![0]!!.x < viewPages!![0]!!.measuredWidth shr 1
									}
								}
							}
							else {
								backAnimation = abs(x) < viewPages!![0]!!.measuredWidth / 3.0f && (abs(velX) < 3500 || abs(velX) < abs(velY))
							}
						}

						val dx: Float

						if (backAnimation) {
							dx = abs(x)

							if (animatingForward) {
								tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages!![0], TRANSLATION_X, 0f), ObjectAnimator.ofFloat(viewPages!![1], TRANSLATION_X, viewPages!![1]!!.measuredWidth.toFloat()))
							}
							else {
								tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages!![0], TRANSLATION_X, 0f), ObjectAnimator.ofFloat(viewPages!![1], TRANSLATION_X, -viewPages!![1]!!.measuredWidth.toFloat()))
							}
						}
						else {
							dx = viewPages!![0]!!.measuredWidth - abs(x)

							if (animatingForward) {
								tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages!![0], TRANSLATION_X, -viewPages!![0]!!.measuredWidth.toFloat()), ObjectAnimator.ofFloat(viewPages!![1], TRANSLATION_X, 0f))
							}
							else {
								tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages!![0], TRANSLATION_X, viewPages!![0]!!.measuredWidth.toFloat()), ObjectAnimator.ofFloat(viewPages!![1], TRANSLATION_X, 0f))
							}
						}

						tabsAnimation?.interpolator = interpolator

						val width = measuredWidth
						val halfWidth = width / 2
						val distanceRatio = min(1.0f, dx / width.toFloat())
						val distance = halfWidth.toFloat() + halfWidth.toFloat() * AndroidUtilities.distanceInfluenceForSnapDuration(distanceRatio)
						velX = abs(velX)

						var duration = if (velX > 0) {
							4 * (1000.0f * abs(distance / velX)).roundToInt()
						}
						else {
							val pageDelta = dx / measuredWidth
							((pageDelta + 1.0f) * 100.0f).toInt()
						}

						duration = max(150, min(duration, 600))

						tabsAnimation?.duration = duration.toLong()

						tabsAnimation?.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animator: Animator) {
								tabsAnimation = null

								if (!backAnimation) {
									val tempPage = viewPages!![0]
									viewPages!![0] = viewPages!![1]
									viewPages!![1] = tempPage

									filterTabsView?.selectTabWithId(viewPages!![0]!!.selectedType, 1.0f)

									updateCounters(false)

									viewPages!![0]?.dialogsAdapter?.resume()
									viewPages!![1]?.dialogsAdapter?.pause()
								}

								viewPages!![1]?.visibility = GONE

								showScrollbars(true)

								tabsAnimationInProgress = false
								maybeStartTracking = false
								actionBar?.isEnabled = true
								filterTabsView?.isEnabled = true

								checkListLoad(viewPages!![0])
							}
						})

						tabsAnimation?.start()

						tabsAnimationInProgress = true
						startedTracking = false
					}
					else {
						maybeStartTracking = false
						actionBar?.isEnabled = true
						filterTabsView?.isEnabled = true
					}

					velocityTracker?.recycle()
					velocityTracker = null
				}

				return startedTracking
			}

			return false
		}

		override fun hasOverlappingRendering(): Boolean {
			return false
		}

		override fun drawList(blurCanvas: Canvas, top: Boolean) {
			if (searchIsShowed) {
				if (searchViewPager?.visibility == VISIBLE) {
					searchViewPager?.drawForBlur(blurCanvas)
				}
			}
			else {
				viewPages?.forEach { viewPage ->
					if (viewPage != null && viewPage.visibility == VISIBLE) {
						for (j in 0 until viewPage.listView!!.childCount) {
							val child = viewPage.listView?.getChildAt(j)

							if (child != null && child.y < viewPage.listView!!.blurTopPadding + AndroidUtilities.dp(100f)) {
								val restore = blurCanvas.save()

								blurCanvas.translate(viewPage.x, viewPage.y + viewPage.listView!!.y + child.y)

								if (child is DialogCell) {
									child.drawingForBlur = true
									child.draw(blurCanvas)
									child.drawingForBlur = false
								}
								else {
									child.draw(blurCanvas)
								}

								blurCanvas.restoreToCount(restore)
							}
						}
					}
				}
			}
		}
	}

	inner class DialogsRecyclerView(context: Context?, private val parentPage: ViewPage) : BlurredRecyclerView(context) {
		var paint = Paint()
		var rectF = RectF()
		private var firstLayout = true
		private var ignoreLayout = false
		private var lastListPadding = 0

		init {
			additionalClipBottom = AndroidUtilities.dp(200f)
		}

		override fun updateEmptyViewAnimated(): Boolean {
			return true
		}

		fun setViewsOffset(viewOffset: Float) {
			DialogsActivity.viewOffset = viewOffset

			val n = childCount

			for (i in 0 until n) {
				getChildAt(i).translationY = viewOffset
			}

			if (selectorPosition != NO_POSITION) {
				val v = layoutManager?.findViewByPosition(selectorPosition)

				if (v != null) {
					selectorRect.set(v.left, (v.top + viewOffset).toInt(), v.right, (v.bottom + viewOffset).toInt())
					selectorDrawable?.bounds = selectorRect
				}
			}

			invalidate()
		}

		val viewOffset: Float
			get() = DialogsActivity.viewOffset

		override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
			super.addView(child, index, params)
			child.translationY = DialogsActivity.viewOffset
			child.translationX = 0f
			child.alpha = 1f
		}

		override fun removeView(view: View) {
			super.removeView(view)
			view.translationY = 0f
			view.translationX = 0f
			view.alpha = 1f
		}

		override fun onDraw(canvas: Canvas) {
			if (parentPage.pullForegroundDrawable != null && DialogsActivity.viewOffset != 0f) {
				val pTop = paddingTop

				if (pTop != 0) {
					canvas.save()
					canvas.translate(0f, pTop.toFloat())
				}

				parentPage.pullForegroundDrawable?.drawOverScroll(canvas)

				if (pTop != 0) {
					canvas.restore()
				}
			}

			super.onDraw(canvas)
		}

		override fun dispatchDraw(canvas: Canvas) {
			super.dispatchDraw(canvas)

			if (drawMovingViewsOverlayed()) {
				paint.color = ResourcesCompat.getColor(context.resources, R.color.background, null)

				for (i in 0 until childCount) {
					val view = getChildAt(i)

					if (view is DialogCell && view.isMoving || view is LastEmptyView && view.moving) {
						if (view.alpha != 1f) {
							rectF.set(view.x, view.y, view.x + view.measuredWidth, view.y + view.measuredHeight)
							canvas.saveLayerAlpha(rectF, (255 * view.alpha).toInt())
						}
						else {
							canvas.save()
						}

						canvas.translate(view.x, view.y)
						canvas.drawRect(0f, 0f, view.measuredWidth.toFloat(), view.measuredHeight.toFloat(), paint)

						view.draw(canvas)

						canvas.restore()
					}
				}

				invalidate()
			}

			if (slidingView != null) {
				pacmanAnimation?.draw(canvas, slidingView!!.top + slidingView!!.measuredHeight / 2)
			}
		}

		private fun drawMovingViewsOverlayed(): Boolean {
			return itemAnimator != null && itemAnimator!!.isRunning && dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0
		}

		override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
			return if (drawMovingViewsOverlayed() && child is DialogCell && child.isMoving) {
				true
			}
			else {
				super.drawChild(canvas, child, drawingTime)
			}
		}

		override fun setAdapter(adapter: Adapter<*>?) {
			super.setAdapter(adapter)
			firstLayout = true
		}

		private fun checkIfAdapterValid() {
			val adapter = adapter

			if (parentPage.lastItemsCount != adapter?.itemCount && !dialogsListFrozen) {
				ignoreLayout = true
				adapter?.notifyDataSetChanged()
				ignoreLayout = false
			}
		}

		override fun onMeasure(widthSpec: Int, heightSpec: Int) {
			val t: Int

//			if (!onlySelect) {
//				t = if (filterTabsView?.visibility == VISIBLE) {
//					AndroidUtilities.dp(44f)
//				}
//				else {
//					actionBar?.measuredHeight ?: 0
//				}
//			}

			val pos = parentPage.layoutManager?.findFirstVisibleItemPosition() ?: NO_POSITION

			if (pos != NO_POSITION && !dialogsListFrozen && parentPage.itemTouchHelper?.isIdle == true) {
				val holder = parentPage.listView?.findViewHolderForAdapterPosition(pos)

				if (holder != null) {
					val top = holder.itemView.top
					ignoreLayout = true
					parentPage.layoutManager?.scrollToPositionWithOffset(pos, (top - lastListPadding + scrollAdditionalOffset).toInt())
					ignoreLayout = false
				}
			}

			if (!onlySelect) {
				ignoreLayout = true

				t = if (filterTabsView?.visibility == VISIBLE) {
					ActionBar.getCurrentActionBarHeight() + if (actionBar?.occupyStatusBar == true) AndroidUtilities.statusBarHeight else 0
				}
				else {
					if (inPreviewMode) AndroidUtilities.statusBarHeight else 0
				}

				topGlowOffset = t

				setPadding(0, t, 0, 0)

				parentPage.progressView?.paddingTop = t

				ignoreLayout = false
			}

			if (firstLayout && messagesController.dialogsLoaded) {
				if (parentPage.dialogsType == 0 && hasHiddenArchive()) {
					ignoreLayout = true

					val layoutManager = layoutManager as? LinearLayoutManager
					layoutManager?.scrollToPositionWithOffset(1, actionBar?.translationY?.toInt() ?: 0)

					ignoreLayout = false
				}

				firstLayout = false
			}

			checkIfAdapterValid()

			super.onMeasure(widthSpec, heightSpec)

			if (!onlySelect) {
				if (viewPages != null && viewPages!!.size > 1) {
					viewPages!![1]!!.translationX = viewPages!![0]!!.measuredWidth.toFloat()
				}
			}
		}

		override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
			super.onLayout(changed, l, t, r, b)
			lastListPadding = paddingTop
			scrollAdditionalOffset = 0f

			if (dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0 && !parentPage.dialogsItemAnimator!!.isRunning) {
				onDialogAnimationFinished()
			}
		}

		override fun requestLayout() {
			if (ignoreLayout) {
				return
			}

			super.requestLayout()
		}

		fun toggleArchiveHidden(action: Boolean, dialogCell: DialogCell?) {
			SharedConfig.toggleArchiveHidden()

			if (SharedConfig.archiveHidden) {
				if (dialogCell != null) {
					disableActionBarScrolling = true
					waitingForScrollFinished = true

					smoothScrollBy(0, dialogCell.measuredHeight + (dialogCell.top - paddingTop), CubicBezierInterpolator.EASE_OUT)

					if (action) {
						updatePullAfterScroll = true
					}
					else {
						updatePullState()
					}
				}

				getUndoView()?.showWithAction(0, UndoView.ACTION_ARCHIVE_HIDDEN, null, null)
			}
			else {
				getUndoView()?.showWithAction(0, UndoView.ACTION_ARCHIVE_PINNED, null, null)

				updatePullState()

				if (action && dialogCell != null) {
					dialogCell.resetPinnedArchiveState()
					dialogCell.invalidate()
				}
			}
		}

		fun updatePullState() {
			parentPage.archivePullViewState = if (SharedConfig.archiveHidden) ARCHIVE_ITEM_STATE_HIDDEN else ARCHIVE_ITEM_STATE_PINNED
			parentPage.pullForegroundDrawable?.setWillDraw(parentPage.archivePullViewState != ARCHIVE_ITEM_STATE_PINNED)
		}

		@SuppressLint("ClickableViewAccessibility")
		override fun onTouchEvent(e: MotionEvent): Boolean {
			if (isFastScrollAnimationRunning || waitingForScrollFinished || dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0) {
				return false
			}

			val action = e.action

			if (action == MotionEvent.ACTION_DOWN) {
				overScrollMode = OVER_SCROLL_ALWAYS
			}

			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				if (!parentPage.itemTouchHelper!!.isIdle && parentPage.swipeController!!.swipingFolder) {
					parentPage.swipeController?.swipeFolderBack = true
					if (parentPage.itemTouchHelper?.checkHorizontalSwipe(null, ItemTouchHelper.LEFT) != 0) {
						if (parentPage.swipeController?.currentItemViewHolder != null) {
							val viewHolder = parentPage.swipeController?.currentItemViewHolder

							if (viewHolder?.itemView is DialogCell) {
								val dialogCell = viewHolder.itemView as DialogCell
								val dialogId = dialogCell.dialogId

								if (DialogObject.isFolderDialogId(dialogId)) {
									toggleArchiveHidden(false, dialogCell)
								}
								else {
									val dialogs = getDialogsArray(currentAccount, parentPage.dialogsType, folderId, false)
									val dialog = dialogs[dialogCell.dialogIndex]

									if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_READ) {
										val selectedDialogs = ArrayList<Long>()
										selectedDialogs.add(dialogId)
										canReadCount = if (dialog.unread_count > 0 || dialog.unread_mark) 1 else 0
										performSelectedDialogsAction(selectedDialogs, read, true)
									}
									else if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_MUTE) {
										if (!messagesController.isDialogMuted(dialogId)) {
											notificationsController.setDialogNotificationsSettings(dialogId, NotificationsController.SETTING_MUTE_FOREVER)

											if (BulletinFactory.canShowBulletin(this@DialogsActivity)) {
												BulletinFactory.createMuteBulletin(this@DialogsActivity, NotificationsController.SETTING_MUTE_FOREVER).show()
											}
										}
										else {
											val selectedDialogs = ArrayList<Long>()
											selectedDialogs.add(dialogId)
											canMuteCount = if (messagesController.isDialogMuted(dialogId)) 0 else 1
											canUnmuteCount = if (canMuteCount > 0) 0 else 1
											performSelectedDialogsAction(selectedDialogs, mute, true)
										}
									}
									else if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_PIN) {
										val selectedDialogs = ArrayList<Long>()
										selectedDialogs.add(dialogId)
										val pinned = isDialogPinned(dialog)
										canPinCount = if (pinned) 0 else 1
										performSelectedDialogsAction(selectedDialogs, pin, true)
									}
									else if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_DELETE) {
										val selectedDialogs = ArrayList<Long>()
										selectedDialogs.add(dialogId)
										performSelectedDialogsAction(selectedDialogs, delete, true)
									}
								}
							}
						}
					}
				}
			}

			val result = super.onTouchEvent(e)

			if (parentPage.dialogsType == 0 && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && parentPage.archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN && hasHiddenArchive()) {
				val layoutManager = layoutManager as? LinearLayoutManager
				val currentPosition = layoutManager?.findFirstVisibleItemPosition()

				if (currentPosition == 0) {
					val pTop = paddingTop
					val view = layoutManager.findViewByPosition(currentPosition)
					val height = (AndroidUtilities.dp(if (SharedConfig.useThreeLinesLayout) 78f else 72f) * PullForegroundDrawable.SNAP_HEIGHT).toInt()

					if (view != null) {
						val diff = view.top - pTop + view.measuredHeight
						val pullingTime = System.currentTimeMillis() - startArchivePullingTime

						if (diff < height || pullingTime < PullForegroundDrawable.minPullingTime) {
							disableActionBarScrolling = true
							smoothScrollBy(0, diff, CubicBezierInterpolator.EASE_OUT_QUINT)
							parentPage.archivePullViewState = ARCHIVE_ITEM_STATE_HIDDEN
						}
						else {
							if (parentPage.archivePullViewState != ARCHIVE_ITEM_STATE_SHOWED) {
								if (this.viewOffset == 0f) {
									disableActionBarScrolling = true
									smoothScrollBy(0, view.top - pTop, CubicBezierInterpolator.EASE_OUT_QUINT)
								}
								if (!canShowHiddenArchive) {
									canShowHiddenArchive = true
									performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
									parentPage.pullForegroundDrawable?.colorize(true)
								}

								(view as DialogCell).startOutAnimation()

								parentPage.archivePullViewState = ARCHIVE_ITEM_STATE_SHOWED
							}
						}

						if (this.viewOffset != 0f) {
							val valueAnimator = ValueAnimator.ofFloat(this.viewOffset, 0f)

							valueAnimator.addUpdateListener {
								setViewsOffset(it.animatedValue as Float)
							}

							valueAnimator.duration = max(100, (350f - 120f * (this.viewOffset / PullForegroundDrawable.maxOverscroll)).toLong())
							valueAnimator.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT

							setScrollEnabled(false)

							valueAnimator.addListener(object : AnimatorListenerAdapter() {
								override fun onAnimationEnd(animation: Animator) {
									super.onAnimationEnd(animation)
									setScrollEnabled(true)
								}
							})

							valueAnimator.start()
						}
					}
				}
			}

			return result
		}

		override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
			if (isFastScrollAnimationRunning || waitingForScrollFinished || dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0) {
				return false
			}

			if (e.action == MotionEvent.ACTION_DOWN) {
				allowSwipeDuringCurrentTouch = !actionBar!!.isActionModeShowed
				checkIfAdapterValid()
			}

			return super.onInterceptTouchEvent(e)
		}

		override fun allowSelectChildAtPosition(child: View?): Boolean {
			return child !is HeaderCell || child.isClickable()
		}
	}

	internal inner class SwipeController(private val parentPage: ViewPage) : ItemTouchHelper.Callback() {
		var currentItemViewHolder: RecyclerView.ViewHolder? = null
		var swipingFolder = false
		var swipeFolderBack = false

		override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
			if (waitingForDialogsAnimationEnd(parentPage) || parentLayout != null && parentLayout!!.isInPreviewMode) {
				return 0
			}

			if (swipingFolder && swipeFolderBack) {
				if (viewHolder.itemView is DialogCell) {
					viewHolder.itemView.swipeCanceled = true
				}

				swipingFolder = false

				return 0
			}

			if (!onlySelect && parentPage.isDefaultDialogType && slidingView == null && viewHolder.itemView is DialogCell) {
				val dialogCell = viewHolder.itemView
				val dialogId = dialogCell.dialogId

				return if (actionBar?.isActionModeShowed(null) == true) {
					val dialog = messagesController.dialogs_dict[dialogId]

					if (!allowMoving || dialog == null || !isDialogPinned(dialog) || DialogObject.isFolderDialogId(dialogId)) {
						return 0
					}

					movingView = viewHolder.itemView
					movingView?.setBackgroundColor(ResourcesCompat.getColor(viewHolder.itemView.resources, R.color.background, null))

					swipeFolderBack = false

					makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
				}
				else {
					if (filterTabsView?.visibility == View.VISIBLE && SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_FOLDERS || !allowSwipeDuringCurrentTouch || dialogId == userConfig.clientUserId || dialogId == 777000L && SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_ARCHIVE || messagesController.isPromoDialog(dialogId, false) && messagesController.promoDialogType != MessagesController.PROMO_TYPE_PSA) {
						return 0
					}

					var canSwipeBack = folderId == 0 && (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_MUTE || SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_READ || SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_PIN || SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_DELETE)

					if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_READ) {
						var filter: MessagesController.DialogFilter? = null

						if (viewPages!![0]?.dialogsType == 7 || viewPages!![0]?.dialogsType == 8) {
							filter = messagesController.selectedDialogFilter[if (viewPages!![0]?.dialogsType == 8) 1 else 0]
						}

						if (filter != null && filter.flags and MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ != 0) {
							val dialog = messagesController.dialogs_dict[dialogId]

							if (dialog != null && !filter.alwaysShow(currentAccount, dialog) && (dialog.unread_count > 0 || dialog.unread_mark)) {
								canSwipeBack = false
							}
						}
					}

					swipeFolderBack = false
					swipingFolder = canSwipeBack && !DialogObject.isFolderDialogId(dialogCell.dialogId) || SharedConfig.archiveHidden && DialogObject.isFolderDialogId(dialogCell.dialogId)

					dialogCell.setSliding(true)

					makeMovementFlags(0, ItemTouchHelper.LEFT)
				}
			}

			return 0
		}

		override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
			if (target.itemView !is DialogCell) {
				return false
			}

			val dialogId = target.itemView.dialogId
			val dialog = messagesController.dialogs_dict[dialogId]

			if (dialog == null || !isDialogPinned(dialog) || DialogObject.isFolderDialogId(dialogId)) {
				return false
			}

			val fromIndex = source.adapterPosition
			val toIndex = target.adapterPosition

			parentPage.dialogsAdapter?.notifyItemMoved(fromIndex, toIndex)

			updateDialogIndices()

			if (viewPages!![0]?.dialogsType == 7 || viewPages!![0]?.dialogsType == 8) {
				val filter = messagesController.selectedDialogFilter[if (viewPages!![0]?.dialogsType == 8) 1 else 0]

				if (filter != null) {
					if (!movingDialogFilters.contains(filter)) {
						movingDialogFilters.add(filter)
					}
				}
			}
			else {
				movingWas = true
			}

			return true
		}

		override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
			return if (swipeFolderBack) {
				0
			}
			else {
				super.convertToAbsoluteDirection(flags, layoutDirection)
			}
		}

		override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
			val dialogCell = viewHolder.itemView as DialogCell
			val dialogId = dialogCell.dialogId

			if (DialogObject.isFolderDialogId(dialogId)) {
				parentPage.listView?.toggleArchiveHidden(false, dialogCell)
				return
			}

			val dialog = messagesController.dialogs_dict[dialogId] ?: return

			if (!messagesController.isPromoDialog(dialogId, false) && folderId == 0 && SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_READ) {
				val selectedDialogs = ArrayList<Long>()
				selectedDialogs.add(dialogId)
				canReadCount = if (dialog.unread_count > 0 || dialog.unread_mark) 1 else 0
				performSelectedDialogsAction(selectedDialogs, read, true)
				return
			}

			slidingView = dialogCell

			val position = viewHolder.adapterPosition
			val count = parentPage.dialogsAdapter!!.itemCount

			val finishRunnable = Runnable {
				if (frozenDialogsList == null) {
					return@Runnable
				}
				frozenDialogsList?.remove(dialog)

				val pinnedNum = dialog.pinnedNum

				slidingView = null

				parentPage.listView?.invalidate()

				val lastItemPosition = parentPage.layoutManager?.findLastVisibleItemPosition() ?: Int.MIN_VALUE

				if (lastItemPosition == count - 1) {
					parentPage.layoutManager?.findViewByPosition(lastItemPosition)?.requestLayout()
				}

				if (messagesController.isPromoDialog(dialog.id, false)) {
					messagesController.hidePromoDialog()
					parentPage.dialogsItemAnimator?.prepareForRemove()
					parentPage.lastItemsCount--
					parentPage.dialogsAdapter?.notifyItemRemoved(position)
					dialogRemoveFinished = 2
				}
				else {
					val added = messagesController.addDialogToFolder(dialog.id, if (folderId == 0) 1 else 0, -1, 0)

					if (added != 2 || position != 0) {
						parentPage.dialogsItemAnimator?.prepareForRemove()
						parentPage.lastItemsCount--
						parentPage.dialogsAdapter?.notifyItemRemoved(position)
						dialogRemoveFinished = 2
					}

					if (folderId == 0) {
						if (added == 2) {
							parentPage.dialogsItemAnimator?.prepareForRemove()

							if (position == 0) {
								dialogChangeFinished = 2
								setDialogsListFrozen(true)
								parentPage.dialogsAdapter?.notifyItemChanged(0)
							}
							else {
								parentPage.lastItemsCount++
								parentPage.dialogsAdapter?.notifyItemInserted(0)

								if (!SharedConfig.archiveHidden && parentPage.layoutManager?.findFirstVisibleItemPosition() == 0) {
									disableActionBarScrolling = true
									parentPage.listView?.smoothScrollBy(0, -AndroidUtilities.dp(if (SharedConfig.useThreeLinesLayout) 78f else 72f))
								}
							}

							val dialogs = getDialogsArray(currentAccount, parentPage.dialogsType, folderId, false)

							frozenDialogsList?.add(0, dialogs[0])
						}
						else if (added == 1) {
							val holder = parentPage.listView?.findViewHolderForAdapterPosition(0)

							if (holder != null && holder.itemView is DialogCell) {
								val cell = holder.itemView
								cell.checkCurrentDialogIndex(true)
								cell.animateArchiveAvatar()
							}
						}

						val preferences = MessagesController.getGlobalMainSettings()
						val hintShowed = preferences.getBoolean("archivehint_l", false) || SharedConfig.archiveHidden

						if (!hintShowed) {
							preferences.edit().putBoolean("archivehint_l", true).commit()
						}

						getUndoView()?.showWithAction(dialog.id, if (hintShowed) UndoView.ACTION_ARCHIVE else UndoView.ACTION_ARCHIVE_HINT, null) {
							dialogsListFrozen = true

							messagesController.addDialogToFolder(dialog.id, 0, pinnedNum, 0)

							dialogsListFrozen = false

							val dialogs = messagesController.getDialogs(0)
							val index = dialogs.indexOf(dialog)

							if (index >= 0) {
								val archivedDialogs = messagesController.getDialogs(1)

								if (archivedDialogs.isNotEmpty() || index != 1) {
									dialogInsertFinished = 2
									setDialogsListFrozen(true)
									parentPage.dialogsItemAnimator!!.prepareForRemove()
									parentPage.lastItemsCount++
									parentPage.dialogsAdapter?.notifyItemInserted(index)
								}

								if (archivedDialogs.isEmpty()) {
									dialogs.removeAt(0)

									if (index == 1) {
										dialogChangeFinished = 2
										setDialogsListFrozen(true)
										parentPage.dialogsAdapter?.notifyItemChanged(0)
									}
									else {
										frozenDialogsList?.removeAt(0)
										parentPage.dialogsItemAnimator?.prepareForRemove()
										parentPage.lastItemsCount--
										parentPage.dialogsAdapter?.notifyItemRemoved(0)
									}
								}
							}
							else {
								parentPage.dialogsAdapter?.notifyDataSetChanged()
							}
						}
					}
					if (folderId != 0 && frozenDialogsList.isNullOrEmpty()) {
						parentPage.listView?.setEmptyView(null)
						parentPage.progressView?.visibility = View.INVISIBLE
					}
				}
			}

			setDialogsListFrozen(true)

			if (Utilities.random.nextInt(1000) == 1) {
				if (pacmanAnimation == null) {
					pacmanAnimation = PacmanAnimation(parentPage.listView!!)
				}

				pacmanAnimation?.setFinishRunnable(finishRunnable)
				pacmanAnimation?.start()
			}
			else {
				finishRunnable.run()
			}
		}

		override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
			if (viewHolder != null) {
				parentPage.listView?.hideSelector(false)
			}

			currentItemViewHolder = viewHolder

			if (viewHolder != null && viewHolder.itemView is DialogCell) {
				viewHolder.itemView.swipeCanceled = false
			}

			super.onSelectedChanged(viewHolder, actionState)
		}

		override fun getAnimationDuration(recyclerView: RecyclerView, animationType: Int, animateDx: Float, animateDy: Float): Long {
			if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_CANCEL) {
				return 200
			}
			else if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) {
				movingView?.let {
					AndroidUtilities.runOnUIThread({ it.background = null }, parentPage.dialogsItemAnimator!!.moveDuration)
					movingView = null
				}
			}

			return super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy)
		}

		override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
			return 0.45f
		}

		override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
			return 3500f
		}

		override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
			return Float.MAX_VALUE
		}
	}

	companion object {
		private const val ACTION_MODE_SEARCH_DIALOGS_TAG = "search_dialogs_action_mode"
		const val DIALOGS_TYPE_START_ATTACH_BOT = 14
		private const val pin = 100
		private const val read = 101
		private const val delete = 102
		private const val clear = 103
		private const val mute = 104
		private const val archive = 105
		private const val block = 106
		private const val archive2 = 107
		private const val pin2 = 108
		private const val add_to_folder = 109
		private const val remove_from_folder = 110
		private const val ARCHIVE_ITEM_STATE_PINNED = 0
		private const val ARCHIVE_ITEM_STATE_SHOWED = 1
		private const val ARCHIVE_ITEM_STATE_HIDDEN = 2

		private val interpolator = Interpolator { t: Float ->
			(t - 1f).pow(5) + 1f
		}

		@JvmField
		val dialogsLoaded = BooleanArray(UserConfig.MAX_ACCOUNT_COUNT)

		private var viewOffset = 0.0f
		const val shouldShowBottomNavigationPanelKey = "shouldShowBottomNavigationPanelKey"

		@JvmStatic
		@JvmOverloads
		fun loadDialogs(accountInstance: AccountInstance, force: Boolean = false) {
			val currentAccount = accountInstance.currentAccount

			if (force || !dialogsLoaded[currentAccount]) {
				val messagesController = accountInstance.messagesController
				messagesController.loadGlobalNotificationsSettings()

				if (force) {
					messagesController.loadDialogs(0, 0, 100, fromCache = false, force = true)
				}
				else {
					messagesController.loadDialogs(0, 0, 100, true)
				}

				messagesController.loadHintDialogs()
				messagesController.loadUserInfo(accountInstance.userConfig.getCurrentUser(), force, 0)

				accountInstance.contactsController.checkInviteText()
				accountInstance.mediaDataController.checkAllMedia(force)

				AndroidUtilities.runOnUIThread({
					accountInstance.downloadController.loadDownloadingFiles()
				}, 200)

				for (emoji in (messagesController.diceEmojies ?: emptySet())) {
					accountInstance.mediaDataController.loadStickersByEmojiOrName(emoji, true, !force)
				}

				dialogsLoaded[currentAccount] = true
			}
		}
	}
}
