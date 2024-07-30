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
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.TaskDescription
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.StatFs
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Settings
import android.text.TextUtils
import android.util.Base64
import android.util.SparseIntArray
import android.view.ActionMode
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.arch.core.util.Function
import androidx.core.app.ActivityCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.google.android.gms.common.api.Status
import com.google.firebase.appindexing.Action
import com.google.firebase.appindexing.FirebaseUserActions
import com.google.firebase.appindexing.builders.AssistActionBuilder
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildVars
import org.telegram.messenger.ChatObject.isLeftFromChat
import org.telegram.messenger.ContactsController
import org.telegram.messenger.ContactsLoadingObserver
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.FingerprintController
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocationController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesController.MessagesLoadedCallback
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.PushListenerController
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.SendMessagesHelper.SendingMediaInfo
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.messenger.Utilities
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.messenger.voip.VideoCapturerDevice
import org.telegram.messenger.voip.VoIPPendingCall
import org.telegram.messenger.voip.VoIPService
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatInvite
import org.telegram.tgnet.TLRPC.InputStickerSet
import org.telegram.tgnet.TLRPC.TL_account_authorizationForm
import org.telegram.tgnet.TLRPC.TL_account_getAuthorizationForm
import org.telegram.tgnet.TLRPC.TL_account_getPassword
import org.telegram.tgnet.TLRPC.TL_account_getWallPaper
import org.telegram.tgnet.TLRPC.TL_account_sendConfirmPhoneCode
import org.telegram.tgnet.TLRPC.TL_account_updateEmojiStatus
import org.telegram.tgnet.TLRPC.TL_attachMenuBotsBot
import org.telegram.tgnet.TLRPC.TL_auth_acceptLoginToken
import org.telegram.tgnet.TLRPC.TL_authorization
import org.telegram.tgnet.TLRPC.TL_boolTrue
import org.telegram.tgnet.TLRPC.TL_channels_getChannels
import org.telegram.tgnet.TLRPC.TL_chatAdminRights
import org.telegram.tgnet.tlrpc.TL_chatBannedRights
import org.telegram.tgnet.TLRPC.TL_chatInvitePeek
import org.telegram.tgnet.TLRPC.TL_codeSettings
import org.telegram.tgnet.TLRPC.TL_contact
import org.telegram.tgnet.TLRPC.TL_contacts_resolvePhone
import org.telegram.tgnet.TLRPC.TL_contacts_resolveUsername
import org.telegram.tgnet.TLRPC.TL_contacts_resolvedPeer
import org.telegram.tgnet.TLRPC.TL_emojiStatus
import org.telegram.tgnet.TLRPC.TL_emojiStatusEmpty
import org.telegram.tgnet.TLRPC.TL_emojiStatusUntil
import org.telegram.tgnet.TLRPC.TL_error
import org.telegram.tgnet.TLRPC.TL_help_deepLinkInfo
import org.telegram.tgnet.TLRPC.TL_help_getDeepLinkInfo
import org.telegram.tgnet.TLRPC.TL_help_termsOfService
import org.telegram.tgnet.TLRPC.TL_inputChannel
import org.telegram.tgnet.TLRPC.TL_inputGameShortName
import org.telegram.tgnet.TLRPC.TL_inputInvoiceSlug
import org.telegram.tgnet.TLRPC.TL_inputMediaGame
import org.telegram.tgnet.TLRPC.TL_inputStickerSetShortName
import org.telegram.tgnet.TLRPC.TL_inputWallPaperSlug
import org.telegram.tgnet.TLRPC.TL_langPackLanguage
import org.telegram.tgnet.TLRPC.TL_langpack_getLanguage
import org.telegram.tgnet.TLRPC.TL_messages_chats
import org.telegram.tgnet.TLRPC.TL_messages_checkChatInvite
import org.telegram.tgnet.TLRPC.TL_messages_checkHistoryImport
import org.telegram.tgnet.TLRPC.TL_messages_discussionMessage
import org.telegram.tgnet.TLRPC.TL_messages_getAttachMenuBot
import org.telegram.tgnet.TLRPC.TL_messages_getDiscussionMessage
import org.telegram.tgnet.TLRPC.TL_messages_historyImportParsed
import org.telegram.tgnet.TLRPC.TL_messages_importChatInvite
import org.telegram.tgnet.TLRPC.TL_messages_toggleBotInAttachMenu
import org.telegram.tgnet.TLRPC.TL_payments_getPaymentForm
import org.telegram.tgnet.TLRPC.TL_payments_paymentForm
import org.telegram.tgnet.TLRPC.TL_payments_paymentReceipt
import org.telegram.tgnet.TLRPC.TL_wallPaper
import org.telegram.tgnet.TLRPC.TL_wallPaperSettings
import org.telegram.tgnet.TLRPC.TL_webPage
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.tgnet.TLRPC.account_Password
import org.telegram.tgnet.WalletHelper
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBarLayout
import org.telegram.ui.ActionBar.ActionBarLayout.ActionBarLayoutDelegate
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.DrawerLayoutContainer
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ChatRightsEditActivity.ChatRightsEditActivityDelegate
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.Components.AppIconBulletinLayout
import org.telegram.ui.Components.AttachBotIntroTopView
import org.telegram.ui.Components.AudioPlayerAlert
import org.telegram.ui.Components.BottomNavigationPanel
import org.telegram.ui.Components.BottomNavigationPanel.BottomNavigationListener
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.EmbedBottomSheet
import org.telegram.ui.Components.EmojiPacksAlert
import org.telegram.ui.Components.FireworksOverlay
import org.telegram.ui.Components.GroupCallPip
import org.telegram.ui.Components.JoinGroupAlert
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createRelative
import org.telegram.ui.Components.PasscodeView
import org.telegram.ui.Components.PhonebookShareAlert
import org.telegram.ui.Components.PipRoundVideoView
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.SharingLocationsAlert
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.StickerSetBulletinLayout
import org.telegram.ui.Components.StickersAlert
import org.telegram.ui.Components.TermsOfServiceView
import org.telegram.ui.Components.TermsOfServiceView.TermsOfServiceViewDelegate
import org.telegram.ui.Components.ThemeEditorView
import org.telegram.ui.Components.UndoView
import org.telegram.ui.Components.VerticalPositionAutoAnimator
import org.telegram.ui.Components.voip.VoIPHelper.startCall
import org.telegram.ui.DialogsActivity.DialogsActivityDelegate
import org.telegram.ui.LauncherIconController.LauncherIcon
import org.telegram.ui.PaymentFormActivity.InvoiceStatus
import org.telegram.ui.PaymentFormActivity.PaymentFormCallback
import org.telegram.ui.SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow
import org.telegram.ui.WallpapersListActivity.ColorWallpaper
import org.telegram.ui.channel.ChannelCreateActivity
import org.telegram.ui.feed.FeedFragment
import org.telegram.ui.group.GroupCallActivity
import org.telegram.ui.group.GroupCreateFinalActivity
import org.webrtc.voiceengine.WebRtcAudioTrack
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LaunchActivity : BasePermissionsActivity(), ActionBarLayoutDelegate, NotificationCenterDelegate, DialogsActivityDelegate {
	private val onUserLeaveHintListeners = mutableListOf<Runnable>()
	private val requestedPermissions = SparseIntArray()
	private var backgroundTablet: SizeNotifierFrameLayout? = null
	private var contactsToSend: MutableList<User>? = null
	private var contactsToSendUri: Uri? = null
	private var currentConnectionState = 0
	private var documentsMimeType: String? = null
	private var documentsOriginalPathsArray: MutableList<String>? = null
	private var documentsPathsArray: MutableList<String>? = null
	private var documentsUrisArray: MutableList<Uri>? = null
	private var exportingChatUri: Uri? = null
	private var finished = false
	private var frameLayout: FrameLayout? = null
	private var importingStickers: MutableList<Parcelable>? = null
	private var importingStickersEmoji: MutableList<String>? = null
	private var importingStickersSoftware: String? = null
	private var isNavigationBarColorFrozen = false
	private var launchLayout: RelativeLayout? = null
	private var localeDialog: AlertDialog? = null
	private var lockRunnable: Runnable? = null
	private var navigateToPremiumBot = false
	private var navigateToPremiumGiftCallback: Runnable? = null
	private var onGlobalLayoutListener: OnGlobalLayoutListener? = null
	private var passcodeSaveIntent: Intent? = null
	private var passcodeSaveIntentIsNew = false
	private var passcodeSaveIntentIsRestore = false
	private var passcodeView: PasscodeView? = null
	private var photoPathsArray: MutableList<SendingMediaInfo>? = null
	private var proxyErrorDialog: AlertDialog? = null
	private var requestPermissionsPointer = 5934
	private var rippleAbove: View? = null
	private var selectAnimatedEmojiDialog: SelectAnimatedEmojiDialogWindow? = null
	private var sendingText: String? = null
	private var shadowTablet: FrameLayout? = null
	private var shadowTabletSide: FrameLayout? = null
	private var tabletFullSize = false
	private var tempLocation: IntArray? = null
	private var termsOfServiceView: TermsOfServiceView? = null
	private var themeSwitchImageView: ImageView? = null
	private var themeSwitchSunDrawable: RLottieDrawable? = null
	private var themeSwitchSunView: View? = null
	private var videoPath: String? = null
	private var visibleActionMode: ActionMode? = null
	private var visibleDialog: AlertDialog? = null
	private var wasMutedByAdminRaisedHand = false
	private var bottomNavigationPanel: BottomNavigationPanel? = null
	private var forYouTab = 0

	@JvmField
	var drawerLayoutContainer: DrawerLayoutContainer? = null

	var actionBarLayout: ActionBarLayout? = null
		private set

	var layersActionBarLayout: ActionBarLayout? = null
		private set

	var rightActionBarLayout: ActionBarLayout? = null
		private set

	var fireworksOverlay: FireworksOverlay? = null
		private set

	override fun onCreate(savedInstanceState: Bundle?) {
		StrictMode.setVmPolicy(VmPolicy.Builder(StrictMode.getVmPolicy()).detectLeakedClosableObjects().build())

		ApplicationLoader.postInitApplication()

		AndroidUtilities.checkDisplaySize(this, resources.configuration)

		currentAccount = UserConfig.selectedAccount

		if (!UserConfig.getInstance(currentAccount).isClientActivated) {
			val intent = intent

			if (intent != null && intent.action != null) {
				if (Intent.ACTION_SEND == intent.action || Intent.ACTION_SEND_MULTIPLE == intent.action) {
					super.onCreate(savedInstanceState)
					finish()
					return
				}
//				else if (Intent.ACTION_VIEW == intent.action) {
//					val uri = intent.data
//
//					if (uri != null) {
//						val url = uri.toString().lowercase()
//					}
//				}
			}
		}

		requestWindowFeature(Window.FEATURE_NO_TITLE)

		setTheme(R.style.Theme_TMessages)

		runCatching {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				setTaskDescription(TaskDescription.Builder().setPrimaryColor(getColor(R.color.brand) or -0x1000000).build())
			}
			else {
				@Suppress("DEPRECATION") setTaskDescription(TaskDescription(null, null, getColor(R.color.brand) or -0x1000000))
			}
		}

		runCatching {
			window.navigationBarColor = -0x1000000
		}

		window.setBackgroundDrawableResource(R.drawable.transparent)

		if (SharedConfig.passcodeHash.isNotEmpty() && !SharedConfig.allowScreenCapture) {
			try {
				window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		super.onCreate(savedInstanceState)

		if (Build.VERSION.SDK_INT >= 24) {
			AndroidUtilities.isInMultiwindow = isInMultiWindowMode
		}

		Theme.createCommonChatResources()
		Theme.createDialogsResources(this)

		if (SharedConfig.passcodeHash.isNotEmpty() && SharedConfig.appLocked) {
			SharedConfig.lastPauseTime = (SystemClock.elapsedRealtime() / 1000).toInt()
		}

		AndroidUtilities.fillStatusBarHeight(this)

		val actionBarLayout = ActionBarLayout(this).also {
			this.actionBarLayout = it
		}

		frameLayout = object : FrameLayout(this) {
			override fun dispatchDraw(canvas: Canvas) {
				super.dispatchDraw(canvas)
				drawRippleAbove(canvas, this)
			}
		}

		frameLayout?.setBackgroundResource(R.color.background)

		setContentView(frameLayout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

		themeSwitchImageView = ImageView(this)
		themeSwitchImageView?.gone()

		drawerLayoutContainer = object : DrawerLayoutContainer(this) {
			private var wasPortrait = false

			override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
				super.onLayout(changed, l, t, r, b)

				drawerPosition = drawerPosition

				val portrait = b - t > r - l

				if (portrait != wasPortrait) {
					post {
						selectAnimatedEmojiDialog?.dismiss()
						selectAnimatedEmojiDialog = null
					}

					wasPortrait = portrait
				}
			}

			override fun closeDrawer() {
				super.closeDrawer()
				selectAnimatedEmojiDialog?.dismiss()
				selectAnimatedEmojiDialog = null
			}

			override fun closeDrawer(fast: Boolean) {
				super.closeDrawer(fast)
				selectAnimatedEmojiDialog?.dismiss()
				selectAnimatedEmojiDialog = null
			}
		}

		drawerLayoutContainer?.setBehindKeyboardColor(getColor(R.color.background))

		frameLayout?.addView(drawerLayoutContainer, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		themeSwitchSunView = object : View(this) {
			override fun onDraw(canvas: Canvas) {
				themeSwitchSunDrawable?.let {
					it.draw(canvas)
					invalidate()
				}
			}
		}

		frameLayout?.addView(themeSwitchSunView, createFrame(48, 48f))

		themeSwitchSunView?.gone()

		frameLayout?.addView(FireworksOverlay(this).also { fireworksOverlay = it })

		val preferences = MessagesController.getGlobalMainSettings()
		val s = preferences.getString("bottom_selected_section", null)
		var section = BottomNavigationPanel.Item.CHATS

		if (s != null) {
			try {
				section = BottomNavigationPanel.Item.valueOf(s)
			}
			catch (e: IllegalArgumentException) {
				FileLog.e(e)
			}
		}

		bottomNavigationPanel = BottomNavigationPanel(this, section)

		bottomNavigationPanel?.listener = BottomNavigationListener {
			when (it) {
				BottomNavigationPanel.Item.CALLS -> {
					val args = Bundle()
					args.putBoolean("topLevel", true)
					val fragment = CallLogActivity(args)
					presentFragment(fragment, removeLast = true, forceWithoutAnimation = true)
				}

				BottomNavigationPanel.Item.CONTACTS -> {
					val args = Bundle()
					args.putBoolean("topLevel", true)
					args.putBoolean("onlyUsers", true)
					args.putBoolean("destroyAfterSelect", false)
					args.putBoolean("createSecretChat", false)
					args.putBoolean("allowBots", true)
					args.putBoolean("allowSelf", false)
					args.putBoolean("disableSections", true)
					presentFragment(ContactsActivity(args), removeLast = true, forceWithoutAnimation = true)
				}

				BottomNavigationPanel.Item.CHATS -> {
					val fragment = DialogsActivity(null)
					presentFragment(fragment, removeLast = true, forceWithoutAnimation = true)
				}

				BottomNavigationPanel.Item.FEED -> {
					val args = Bundle()
					args.putInt("forYou", forYouTab)

					val fragment = FeedFragment(args)
					presentFragment(fragment, removeLast = true, forceWithoutAnimation = true)

					forYouTab = 0
				}

				BottomNavigationPanel.Item.SETTINGS -> {
					val args = Bundle()
					args.putLong("user_id", UserConfig.getInstance(currentAccount).clientUserId)
					val fragment = ProfileActivity(args)
					presentFragment(fragment, removeLast = true, forceWithoutAnimation = true)
				}
			}

			val editor = MessagesController.getGlobalMainSettings().edit()
			editor.putString("bottom_selected_section", it.name)
			editor.commit()
		}

		setupActionBarLayout()

		actionBarLayout.setBottomNavigationPanel(bottomNavigationPanel)

		drawerLayoutContainer?.setParentActionBarLayout(actionBarLayout)

		actionBarLayout.init(mainFragmentsStack)

		actionBarLayout.setFragmentStackChangedListener {
			checkSystemBarColors(checkStatusBar = true, checkNavigationBar = false)
		}

		actionBarLayout.setDelegate(this)

		checkCurrentAccount()

		updateCurrentConnectionState()

		NotificationCenter.globalInstance.postNotificationName(NotificationCenter.closeOtherAppActivities, this)

		currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState()

		NotificationCenter.globalInstance.let {
			it.addObserver(this, NotificationCenter.needShowAlert)
			it.addObserver(this, NotificationCenter.reloadInterface)
			it.addObserver(this, NotificationCenter.didSetNewTheme)
			it.addObserver(this, NotificationCenter.needCheckSystemBarColors)
			it.addObserver(this, NotificationCenter.closeOtherAppActivities)
			it.addObserver(this, NotificationCenter.didSetPasscode)
			it.addObserver(this, NotificationCenter.notificationsCountUpdated)
			it.addObserver(this, NotificationCenter.screenStateChanged)
			it.addObserver(this, NotificationCenter.showBulletin)
			it.addObserver(this, NotificationCenter.requestPermissions)
		}

		if (actionBarLayout.fragmentsStack.isEmpty()) {
			if (!UserConfig.getInstance(currentAccount).isClientActivated) {
				// actionBarLayout.addFragmentToStack(clientNotActivatedFragment)
				actionBarLayout.addFragmentToStack(LoginActivity())
				drawerLayoutContainer?.setAllowOpenDrawer(false, false)
			}
			else {
				bottomNavigationPanel?.setCurrentItem(BottomNavigationPanel.Item.CHATS, true)
				drawerLayoutContainer?.setAllowOpenDrawer(true, false)
			}
			try {
				if (savedInstanceState != null) {
					val bottomNavPanelItemName = savedInstanceState.getString("bottomNavigationPanel")

					if (bottomNavPanelItemName != null) {
						runCatching {
							val item = BottomNavigationPanel.Item.valueOf(bottomNavPanelItemName)
							bottomNavigationPanel?.setCurrentItem(item, false)
						}
					}

					val fragmentName = savedInstanceState.getString("fragment")

					if (fragmentName != null) {
						val args = savedInstanceState.getBundle("args")

						when (fragmentName) {
							"chat" -> if (args != null) {
								val chat = ChatActivity(args)

								if (actionBarLayout.addFragmentToStack(chat)) {
									chat.restoreSelfArgs(savedInstanceState)
								}
							}

							"settings" -> {
								args?.putLong("user_id", UserConfig.getInstance(currentAccount).clientUserId)
								val settings = ProfileActivity(args)
								actionBarLayout.addFragmentToStack(settings)
								settings.restoreSelfArgs(savedInstanceState)
							}

							"group" -> if (args != null) {
								val group = GroupCreateFinalActivity(args)

								if (actionBarLayout.addFragmentToStack(group)) {
									group.restoreSelfArgs(savedInstanceState)
								}
							}

							"channel" -> if (args != null) {
								val channel = ChannelCreateActivity(args)

								if (actionBarLayout.addFragmentToStack(channel)) {
									channel.restoreSelfArgs(savedInstanceState)
								}
							}

							"chat_profile" -> if (args != null) {
								val profile = ProfileActivity(args)
								if (actionBarLayout.addFragmentToStack(profile)) {
									profile.restoreSelfArgs(savedInstanceState)
								}
							}

							"wallpapers" -> {
								val settings = WallpapersListActivity(WallpapersListActivity.TYPE_ALL)
								actionBarLayout.addFragmentToStack(settings)
								settings.restoreSelfArgs(savedInstanceState)
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
			var allowOpen = true

			if (AndroidUtilities.isTablet()) {
				allowOpen = actionBarLayout.fragmentsStack.size <= 1 && layersActionBarLayout!!.fragmentsStack.isEmpty()

				if (layersActionBarLayout!!.fragmentsStack.size == 1 && (layersActionBarLayout!!.fragmentsStack[0] is LoginActivity || layersActionBarLayout!!.fragmentsStack[0] is IntroActivity)) {
					allowOpen = false
				}
			}

			if (actionBarLayout.fragmentsStack.size == 1 && (actionBarLayout.fragmentsStack[0] is LoginActivity || actionBarLayout.fragmentsStack[0] is IntroActivity)) {
				allowOpen = false
			}

			drawerLayoutContainer?.setAllowOpenDrawer(allowOpen, false)
		}

		checkLayout()
		checkSystemBarColors()
		handleIntent(intent, false, savedInstanceState != null, false)

		try {
			var os1 = Build.DISPLAY
			var os2 = Build.USER

			os1 = os1?.lowercase(Locale.getDefault()) ?: ""

			os2 = if (os2 != null) {
				os1.lowercase(Locale.getDefault())
			}
			else {
				""
			}

			FileLog.d("OS name $os1 $os2")

			if ((os1.contains("flyme") || os2.contains("flyme")) && Build.VERSION.SDK_INT <= 24) {
				AndroidUtilities.incorrectDisplaySizeFix = true

				val view = window.decorView.rootView

				view.viewTreeObserver.addOnGlobalLayoutListener(OnGlobalLayoutListener {
					var height = view.measuredHeight

					FileLog.d("height = " + height + " displayHeight = " + AndroidUtilities.displaySize.y)

					height -= AndroidUtilities.statusBarHeight

					if (height > AndroidUtilities.dp(100f) && height < AndroidUtilities.displaySize.y && height + AndroidUtilities.dp(100f) > AndroidUtilities.displaySize.y) {
						AndroidUtilities.displaySize.y = height
						FileLog.d("fix display size y to " + AndroidUtilities.displaySize.y)
					}
				}.also {
					onGlobalLayoutListener = it
				})
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		MediaController.getInstance().setBaseActivity(this, true)
		FingerprintController.checkKeyReady()

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager

			if (am.isBackgroundRestricted && System.currentTimeMillis() - SharedConfig.BackgroundActivityPrefs.getLastCheckedBackgroundActivity() >= 86400000L) {
				AlertsCreator.createBackgroundActivityDialog(this).show()
				SharedConfig.BackgroundActivityPrefs.setLastCheckedBackgroundActivity(System.currentTimeMillis())
			}
		}
	}

	private fun setupActionBarLayout() {
		val i = if (drawerLayoutContainer!!.indexOfChild(launchLayout) != -1) drawerLayoutContainer!!.indexOfChild(launchLayout) else drawerLayoutContainer!!.indexOfChild(actionBarLayout)

		if (i != -1) {
			drawerLayoutContainer!!.removeViewAt(i)
		}

		if (AndroidUtilities.isTablet()) {
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

			launchLayout = object : RelativeLayout(this) {
				private var inLayout = false

				override fun requestLayout() {
					if (inLayout) {
						return
					}

					super.requestLayout()
				}

				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					inLayout = true

					val width = MeasureSpec.getSize(widthMeasureSpec)
					val height = MeasureSpec.getSize(heightMeasureSpec)

					setMeasuredDimension(width, height)

					if (!AndroidUtilities.isInMultiwindow && (!AndroidUtilities.isSmallTablet() || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)) {
						tabletFullSize = false

						var leftWidth = width / 100 * 35

						if (leftWidth < AndroidUtilities.dp(320f)) {
							leftWidth = AndroidUtilities.dp(320f)
						}

						actionBarLayout?.measure(MeasureSpec.makeMeasureSpec(leftWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
						shadowTabletSide?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
						rightActionBarLayout?.measure(MeasureSpec.makeMeasureSpec(width - leftWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
					}
					else {
						tabletFullSize = true
						actionBarLayout?.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
					}

					backgroundTablet?.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
					shadowTablet?.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
					layersActionBarLayout?.measure(MeasureSpec.makeMeasureSpec(min(AndroidUtilities.dp(530f), width), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(min(AndroidUtilities.dp(528f), height), MeasureSpec.EXACTLY))

					inLayout = false
				}

				override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
					val width = r - l
					val height = b - t

					if (!AndroidUtilities.isInMultiwindow && (!AndroidUtilities.isSmallTablet() || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)) {
						var leftWidth = width / 100 * 35
						if (leftWidth < AndroidUtilities.dp(320f)) {
							leftWidth = AndroidUtilities.dp(320f)
						}

						shadowTabletSide!!.layout(leftWidth, 0, leftWidth + shadowTabletSide!!.measuredWidth, shadowTabletSide!!.measuredHeight)
						actionBarLayout!!.layout(0, 0, actionBarLayout!!.measuredWidth, actionBarLayout!!.measuredHeight)
						rightActionBarLayout!!.layout(leftWidth, 0, leftWidth + rightActionBarLayout!!.measuredWidth, rightActionBarLayout!!.measuredHeight)
					}
					else {
						actionBarLayout!!.layout(0, 0, actionBarLayout!!.measuredWidth, actionBarLayout!!.measuredHeight)
					}

					val x = (width - layersActionBarLayout!!.measuredWidth) / 2
					val y = (height - layersActionBarLayout!!.measuredHeight) / 2

					layersActionBarLayout!!.layout(x, y, x + layersActionBarLayout!!.measuredWidth, y + layersActionBarLayout!!.measuredHeight)
					backgroundTablet!!.layout(0, 0, backgroundTablet!!.measuredWidth, backgroundTablet!!.measuredHeight)
					shadowTablet!!.layout(0, 0, shadowTablet!!.measuredWidth, shadowTablet!!.measuredHeight)
				}
			}

			if (i != -1) {
				drawerLayoutContainer?.addView(launchLayout, i, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
			}
			else {
				drawerLayoutContainer?.addView(launchLayout, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
			}

			backgroundTablet = object : SizeNotifierFrameLayout(this) {
				override fun isActionBarVisible(): Boolean {
					return false
				}
			}

			backgroundTablet?.setOccupyStatusBar(false)
			backgroundTablet?.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion())

			launchLayout?.addView(backgroundTablet, createRelative(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))

			val parent = actionBarLayout?.parent as? ViewGroup
			parent?.removeView(actionBarLayout)

			launchLayout?.addView(actionBarLayout)

			rightActionBarLayout = ActionBarLayout(this)
			rightActionBarLayout?.init(rightFragmentsStack)
			rightActionBarLayout?.setDelegate(this)

			launchLayout?.addView(rightActionBarLayout)

			shadowTabletSide = FrameLayout(this)
			shadowTabletSide?.setBackgroundColor(0x40295274)

			launchLayout?.addView(shadowTabletSide)

			shadowTablet = FrameLayout(this)
			shadowTablet?.visibility = if (layerFragmentsStack.isEmpty()) View.GONE else View.VISIBLE
			shadowTablet?.setBackgroundColor(0x7f000000)

			launchLayout?.addView(shadowTablet)

			shadowTablet?.setOnTouchListener { _, event ->
				if (actionBarLayout!!.fragmentsStack.isNotEmpty() && event.action == MotionEvent.ACTION_UP) {
					val x = event.x
					val y = event.y
					val location = IntArray(2)

					layersActionBarLayout!!.getLocationOnScreen(location)

					val viewX = location[0]
					val viewY = location[1]

					if (layersActionBarLayout!!.checkTransitionAnimation() || x > viewX && x < viewX + layersActionBarLayout!!.width && y > viewY && y < viewY + layersActionBarLayout!!.height) {
						return@setOnTouchListener false
					}
					else {
						if (layersActionBarLayout!!.fragmentsStack.isNotEmpty()) {
							val a = 0

							while (a < layersActionBarLayout!!.fragmentsStack.size - 1) {
								layersActionBarLayout!!.removeFragmentFromStack(layersActionBarLayout!!.fragmentsStack[0])
							}

							layersActionBarLayout?.closeLastFragment(true)
						}

						return@setOnTouchListener true
					}
				}

				false
			}

			shadowTablet?.setOnClickListener { _ -> }

			layersActionBarLayout = ActionBarLayout(this)
			layersActionBarLayout?.setRemoveActionBarExtraHeight(true)
			layersActionBarLayout?.setBackgroundView(shadowTablet)
			layersActionBarLayout?.setUseAlphaAnimations(true)
			layersActionBarLayout?.setBackgroundResource(R.drawable.boxshadow)
			layersActionBarLayout?.init(layerFragmentsStack)
			layersActionBarLayout?.setDelegate(this)
			layersActionBarLayout?.visibility = if (layerFragmentsStack.isEmpty()) View.GONE else View.VISIBLE

			VerticalPositionAutoAnimator.attach(layersActionBarLayout)

			launchLayout?.addView(layersActionBarLayout)
		}
		else {
			val parent = actionBarLayout?.parent as? ViewGroup
			parent?.removeView(actionBarLayout)

			actionBarLayout?.init(mainFragmentsStack)

			if (i != -1) {
				drawerLayoutContainer?.addView(actionBarLayout, i, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
			}
			else {
				drawerLayoutContainer?.addView(actionBarLayout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
			}
		}
	}

	fun addOnUserLeaveHintListener(callback: Runnable) {
		onUserLeaveHintListeners.add(callback)
	}

	fun removeOnUserLeaveHintListener(callback: Runnable) {
		onUserLeaveHintListeners.remove(callback)
	}

//	private val clientNotActivatedFragment: BaseFragment
//		get() = IntroActivity()

	fun showSelectStatusDialog() {
		if (selectAnimatedEmojiDialog != null || SharedConfig.appLocked) {
			return
		}

		val fragment = actionBarLayout?.lastFragment ?: return
		val popup = arrayOfNulls<SelectAnimatedEmojiDialogWindow>(1)
		val user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId())
		val xoff = 0
		val scrimDrawable: SwapAnimatedEmojiDrawable? = null
		val scrimDrawableParent: View? = null

		val popupLayout = object : SelectAnimatedEmojiDialog(fragment, this@LaunchActivity, true, xoff, TYPE_EMOJI_STATUS) {
			override fun onEmojiSelected(view: View, documentId: Long?, document: TLRPC.Document?, until: Int?) {
				val req = TL_account_updateEmojiStatus()

				if (documentId == null) {
					req.emoji_status = TL_emojiStatusEmpty()
				}
				else if (until != null) {
					req.emoji_status = TL_emojiStatusUntil()
					(req.emoji_status as TL_emojiStatusUntil).document_id = documentId
					(req.emoji_status as TL_emojiStatusUntil).until = until
				}
				else {
					req.emoji_status = TL_emojiStatus()
					(req.emoji_status as TL_emojiStatus).document_id = documentId
				}

				@Suppress("NAME_SHADOWING") val user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId())

				if (user != null) {
					user.emoji_status = req.emoji_status

					NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userEmojiStatusUpdated, user)
					MessagesController.getInstance(currentAccount).updateEmojiStatusUntilUpdate(user.id, user.emoji_status)
				}

				ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
					if (response !is TL_boolTrue) {
						// TODO: reject
					}
				}

				if (popup[0] != null) {
					selectAnimatedEmojiDialog = null
					popup[0]?.dismiss()
				}
			}
		}

		if (user != null && user.emoji_status is TL_emojiStatusUntil && (user.emoji_status as TL_emojiStatusUntil).until > (System.currentTimeMillis() / 1000).toInt()) {
			popupLayout.setExpireDateHint((user.emoji_status as TL_emojiStatusUntil).until)
		}

		popupLayout.setSelected(if (scrimDrawable != null && scrimDrawable.drawable is AnimatedEmojiDrawable) (scrimDrawable.drawable as AnimatedEmojiDrawable).documentId else null)
		popupLayout.setSaveState(2)
		popupLayout.setScrimDrawable(scrimDrawable, scrimDrawableParent)

		selectAnimatedEmojiDialog = object : SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
			override fun dismiss() {
				super.dismiss()
				selectAnimatedEmojiDialog = null
			}
		}

		popup[0] = selectAnimatedEmojiDialog
		popup[0]?.dimBehind()
	}

	private fun checkSystemBarColors(checkStatusBar: Boolean, checkNavigationBar: Boolean) {
		checkSystemBarColors(false, checkStatusBar, checkNavigationBar)
	}

	private fun checkSystemBarColors(useCurrentFragment: Boolean = false, checkStatusBar: Boolean = true, checkNavigationBar: Boolean = !isNavigationBarColorFrozen) {
		var currentFragment = mainFragmentsStack.lastOrNull()

		if (currentFragment != null && (currentFragment.isRemovingFromStack || currentFragment.isInPreviewMode)) {
			currentFragment = if (mainFragmentsStack.size > 1) mainFragmentsStack[mainFragmentsStack.size - 2] else null
		}

		val forceLightStatusBar = currentFragment != null && currentFragment.hasForceLightStatusBar()

		if (checkStatusBar) {
			val enable = if (currentFragment != null) {
				currentFragment.isLightStatusBar
			}
			else {
				val color = ApplicationLoader.applicationContext.getColor(R.color.background)
				ColorUtils.calculateLuminance(color) > 0.7f
			}

			AndroidUtilities.setLightStatusBar(window, enable, forceLightStatusBar)
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && checkNavigationBar && (!useCurrentFragment || currentFragment == null || !currentFragment.isInPreviewMode)) {
			val window = window
			val color = if (currentFragment != null && useCurrentFragment) currentFragment.navigationBarColor else getColor(R.color.light_background)

			if (window.navigationBarColor != color) {
				window.navigationBarColor = color
				val brightness = AndroidUtilities.computePerceivedBrightness(color)
				AndroidUtilities.setLightNavigationBar(getWindow(), brightness >= 0.721f)
			}
		}

		if ((SharedConfig.noStatusBar || forceLightStatusBar) && checkStatusBar) {
			window.statusBarColor = 0
		}
	}

	fun switchToAccount(account: Int) {
		if (account == UserConfig.selectedAccount || !UserConfig.isValidAccount(account)) {
			return
		}

		ConnectionsManager.getInstance(currentAccount).setAppPaused(value = true, byScreenState = false)

		UserConfig.selectedAccount = account
		UserConfig.getInstance(currentAccount).saveConfig(false)

		checkCurrentAccount()

		if (AndroidUtilities.isTablet()) {
			layersActionBarLayout?.removeAllFragments()
			rightActionBarLayout?.removeAllFragments()

			if (!tabletFullSize) {
				shadowTabletSide?.visible()

				if (rightActionBarLayout?.fragmentsStack.isNullOrEmpty()) {
					backgroundTablet?.visible()
				}

				rightActionBarLayout?.gone()
			}

			layersActionBarLayout?.gone()
		}

		bottomNavigationPanel?.reset(BottomNavigationPanel.Item.SETTINGS, true)

		if (!ApplicationLoader.mainInterfacePaused) {
			ConnectionsManager.getInstance(currentAccount).setAppPaused(value = false, byScreenState = false)
		}

		if (UserConfig.getInstance(account).unacceptedTermsOfService != null) {
			showTosActivity(account, UserConfig.getInstance(account).unacceptedTermsOfService)
		}

		updateCurrentConnectionState()
		WalletHelper.getInstance(currentAccount).reload()

		DialogsActivity.loadDialogs(AccountInstance.getInstance(currentAccount))
	}

	private fun switchToAvailableAccountOrLogout() {
		var account = -1

		for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
			if (UserConfig.getInstance(a).isClientActivated) {
				account = a
				break
			}
		}

		termsOfServiceView?.gone()

		if (account != -1) {
			switchToAccount(account)
		}
		else {
			clearFragments()

			actionBarLayout?.rebuildLogout()

			if (AndroidUtilities.isTablet()) {
				layersActionBarLayout?.rebuildLogout()
				rightActionBarLayout?.rebuildLogout()
			}

			val editor = MessagesController.getGlobalMainSettings().edit()
			editor.putString("bottom_selected_section", BottomNavigationPanel.Item.CHATS.name)
			editor.commit()

			presentFragment(LoginActivity())

			// presentFragment(IntroActivity().setOnLogout())

			bottomNavigationPanel?.reset(BottomNavigationPanel.Item.CHATS)
		}
	}

	val mainFragmentsCount: Int
		get() = mainFragmentsStack.size

	private fun checkCurrentAccount() {
		if (currentAccount != UserConfig.selectedAccount) {
			NotificationCenter.getInstance(currentAccount).let {
				it.removeObserver(this, NotificationCenter.appDidLogout)
				it.removeObserver(this, NotificationCenter.didUpdateConnectionState)
				it.removeObserver(this, NotificationCenter.needShowAlert)
				it.removeObserver(this, NotificationCenter.wasUnableToFindCurrentLocation)
				it.removeObserver(this, NotificationCenter.openArticle)
				it.removeObserver(this, NotificationCenter.needShowPlayServicesAlert)
				it.removeObserver(this, NotificationCenter.historyImportProgressChanged)
				it.removeObserver(this, NotificationCenter.groupCallUpdated)
				it.removeObserver(this, NotificationCenter.stickersImportComplete)
				it.removeObserver(this, NotificationCenter.updateUnreadBadge)
			}
		}

		currentAccount = UserConfig.selectedAccount

		NotificationCenter.getInstance(currentAccount).let {
			it.addObserver(this, NotificationCenter.appDidLogout)
			it.addObserver(this, NotificationCenter.didUpdateConnectionState)
			it.addObserver(this, NotificationCenter.needShowAlert)
			it.addObserver(this, NotificationCenter.wasUnableToFindCurrentLocation)
			it.addObserver(this, NotificationCenter.openArticle)
			it.addObserver(this, NotificationCenter.needShowPlayServicesAlert)
			it.addObserver(this, NotificationCenter.historyImportProgressChanged)
			it.addObserver(this, NotificationCenter.groupCallUpdated)
			it.addObserver(this, NotificationCenter.stickersImportComplete)
			it.addObserver(this, NotificationCenter.currentUserShowLimitReachedDialog)
			it.addObserver(this, NotificationCenter.updateUnreadBadge)
		}

		bottomNavigationPanel?.setUnreadBadge(NotificationsController.getInstance(currentAccount).getUnreadCount(currentAccount, true))
	}

	private fun checkLayout() {
		if (!AndroidUtilities.isTablet() || rightActionBarLayout == null) {
			return
		}

		if (!AndroidUtilities.isInMultiwindow && (!AndroidUtilities.isSmallTablet() || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)) {
			tabletFullSize = false

			if (actionBarLayout!!.fragmentsStack.size >= 2) {
				val a = 1

				while (a < actionBarLayout!!.fragmentsStack.size) {
					val chatFragment = actionBarLayout!!.fragmentsStack[a]

					if (chatFragment is ChatActivity) {
						chatFragment.setIgnoreAttachOnPause(true)
					}

					chatFragment.onPause()

					actionBarLayout?.fragmentsStack?.removeAt(a)
					rightActionBarLayout?.fragmentsStack?.add(chatFragment)
				}

				if (passcodeView?.visibility != View.VISIBLE) {
					actionBarLayout?.showLastFragment()
					rightActionBarLayout?.showLastFragment()
				}
			}

			rightActionBarLayout?.visibility = if (rightActionBarLayout!!.fragmentsStack.isEmpty()) View.GONE else View.VISIBLE
			backgroundTablet?.visibility = if (rightActionBarLayout!!.fragmentsStack.isEmpty()) View.VISIBLE else View.GONE
			shadowTabletSide?.visibility = if (actionBarLayout!!.fragmentsStack.isNotEmpty()) View.VISIBLE else View.GONE
		}
		else {
			tabletFullSize = true

			if (rightActionBarLayout!!.fragmentsStack.isNotEmpty()) {
				val a = 0

				while (a < rightActionBarLayout!!.fragmentsStack.size) {
					val chatFragment = rightActionBarLayout!!.fragmentsStack[a]

					if (chatFragment is ChatActivity) {
						chatFragment.setIgnoreAttachOnPause(true)
					}

					chatFragment.onPause()

					rightActionBarLayout?.fragmentsStack?.removeAt(a)
					actionBarLayout?.fragmentsStack?.add(chatFragment)
				}

				if (passcodeView?.visibility != View.VISIBLE) {
					actionBarLayout?.showLastFragment()
				}
			}

			shadowTabletSide?.gone()
			rightActionBarLayout?.gone()
			backgroundTablet?.visibility = if (actionBarLayout!!.fragmentsStack.isNotEmpty()) View.GONE else View.VISIBLE
		}
	}

	private fun showTosActivity(account: Int, tos: TL_help_termsOfService?) {
		if (termsOfServiceView == null) {
			termsOfServiceView = TermsOfServiceView(this)
			termsOfServiceView?.alpha = 0f

			drawerLayoutContainer?.addView(termsOfServiceView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			termsOfServiceView?.setDelegate(object : TermsOfServiceViewDelegate {
				override fun onAcceptTerms(account: Int) {
					UserConfig.getInstance(account).unacceptedTermsOfService = null
					UserConfig.getInstance(account).saveConfig(false)

					drawerLayoutContainer?.setAllowOpenDrawer(true, false)

					if (mainFragmentsStack.size > 0) {
						mainFragmentsStack[mainFragmentsStack.size - 1].onResume()
					}

					termsOfServiceView?.animate()?.alpha(0f)?.setDuration(150)?.setInterpolator(AndroidUtilities.accelerateInterpolator)?.withEndAction {
						termsOfServiceView?.gone()
					}?.start()
				}

				override fun onDeclineTerms(account: Int) {
					drawerLayoutContainer?.setAllowOpenDrawer(true, false)
					termsOfServiceView?.gone()
				}
			})
		}

		val currentTos = UserConfig.getInstance(account).unacceptedTermsOfService

		if (currentTos !== tos && (currentTos == null || currentTos.id?.data != tos?.id?.data)) {
			UserConfig.getInstance(account).let {
				it.unacceptedTermsOfService = tos
				it.saveConfig(false)
			}
		}

		termsOfServiceView?.show(account, tos)
		drawerLayoutContainer?.setAllowOpenDrawer(false, false)
		termsOfServiceView?.animate()?.alpha(1f)?.setDuration(150)?.setInterpolator(AndroidUtilities.decelerateInterpolator)?.setListener(null)?.start()
	}

	fun showPasscodeActivity(fingerprint: Boolean, animated: Boolean, x: Int, y: Int, onShow: Runnable?, onStart: Runnable?) {
		val drawerLayoutContainer = drawerLayoutContainer ?: return

		if (passcodeView == null) {
			passcodeView = PasscodeView(this)

			drawerLayoutContainer.addView(passcodeView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		}

		selectAnimatedEmojiDialog?.dismiss()
		selectAnimatedEmojiDialog = null

		SharedConfig.appLocked = true

		if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible) {
			SecretMediaViewer.getInstance().closePhoto(false, false)
		}
		else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
			PhotoViewer.getInstance().closePhoto(false, true)
		}
		else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible) {
			ArticleViewer.getInstance().close(false, true)
		}

		val messageObject = MediaController.getInstance().playingMessageObject

		if (messageObject != null && messageObject.isRoundVideo) {
			MediaController.getInstance().cleanupPlayer(true, true)
		}

		passcodeView?.onShow(fingerprint, animated, x, y, {
			actionBarLayout?.invisible()

			if (AndroidUtilities.isTablet()) {
				if (layersActionBarLayout?.visibility == View.VISIBLE) {
					layersActionBarLayout?.invisible()
				}

				rightActionBarLayout?.invisible()
			}

			onShow?.run()
		}, onStart)

		SharedConfig.isWaitingForPasscodeEnter = true

		drawerLayoutContainer.setAllowOpenDrawer(false, false)

		passcodeView?.setDelegate {
			SharedConfig.isWaitingForPasscodeEnter = false

			if (passcodeSaveIntent != null) {
				handleIntent(passcodeSaveIntent, passcodeSaveIntentIsNew, passcodeSaveIntentIsRestore, true)
				passcodeSaveIntent = null
			}

			drawerLayoutContainer.setAllowOpenDrawer(true, false)

			actionBarLayout?.visible()
			actionBarLayout?.showLastFragment()

			if (AndroidUtilities.isTablet()) {
				layersActionBarLayout?.showLastFragment()
				rightActionBarLayout?.showLastFragment()

				if (layersActionBarLayout?.visibility == View.INVISIBLE) {
					layersActionBarLayout?.visible()
				}

				rightActionBarLayout?.visible()
			}
		}
	}

	@SuppressLint("Range")
	private fun handleIntent(intent: Intent?, isNew: Boolean, restore: Boolean, fromPassword: Boolean): Boolean {
		@Suppress("NAME_SHADOWING") var isNew = isNew

		if (AndroidUtilities.handleProxyIntent(this, intent)) {
			actionBarLayout?.showLastFragment()

			if (AndroidUtilities.isTablet()) {
				layersActionBarLayout?.showLastFragment()
				rightActionBarLayout?.showLastFragment()
			}

			return true
		}

		if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
			if (intent == null || Intent.ACTION_MAIN != intent.action) {
				PhotoViewer.getInstance().closePhoto(false, true)
			}
		}

		val flags = intent?.flags ?: 0
		val action = intent?.action
		val intentAccount = intArrayOf(intent?.getIntExtra("currentAccount", UserConfig.selectedAccount) ?: UserConfig.selectedAccount)

		switchToAccount(intentAccount[0])

		val isVoipIntent = action != null && action == "voip"

		if (!fromPassword && (AndroidUtilities.needShowPasscode(true) || SharedConfig.isWaitingForPasscodeEnter)) {
			showPasscodeActivity(fingerprint = true, animated = false, x = -1, y = -1, onShow = null, onStart = null)
			UserConfig.getInstance(currentAccount).saveConfig(false)

			if (!isVoipIntent) {
				passcodeSaveIntent = intent
				passcodeSaveIntentIsNew = isNew
				passcodeSaveIntentIsRestore = restore
				return false
			}
		}

		var pushOpened = false
		var push_user_id: Long = 0
		var push_chat_id: Long = 0
		var push_enc_id = 0
		var push_msg_id = 0
		var open_settings = 0
		var open_widget_edit = -1
		var open_widget_edit_type = -1
		var open_new_dialog = 0
		var dialogId: Long = 0
		var showDialogsList = false
		var showPlayer = false
		var showLocations = false
		var showGroupVoip = false
		var showCallLog = false
		var audioCallUser = false
		var videoCallUser = false
		var needCallAlert = false
		var newContact = false
		var newContactAlert = false
		var scanQr = false
		var searchQuery: String? = null
		var callSearchQuery: String? = null
		var newContactName: String? = null

		photoPathsArray = null
		videoPath = null
		sendingText = null
		documentsPathsArray = null
		documentsOriginalPathsArray = null
		documentsMimeType = null
		documentsUrisArray = null
		exportingChatUri = null
		contactsToSend = null
		contactsToSendUri = null
		importingStickers = null
		importingStickersEmoji = null
		importingStickersSoftware = null

		if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
			if (intent != null && intent.action != null && !restore) {
				if (Intent.ACTION_SEND == intent.action) {
					if (SharedConfig.directShare && intent.extras != null) {
						dialogId = intent.extras?.getLong("dialogId", 0) ?: 0

						var hash: String? = null

						if (dialogId == 0L) {
							try {
								val id = intent.extras?.getString(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)

								if (id != null) {
									val list = ShortcutManagerCompat.getDynamicShortcuts(ApplicationLoader.applicationContext)

									for (info in list) {
										if (id == info.id) {
											val extras = info.intent.extras
											dialogId = extras?.getLong("dialogId", 0) ?: 0
											hash = extras?.getString("hash", null)
											break
										}
									}
								}
							}
							catch (e: Throwable) {
								FileLog.e(e)
							}
						}
						else {
							hash = intent.extras?.getString("hash", null)
						}

						if (SharedConfig.directShareHash == null || SharedConfig.directShareHash != hash) {
							dialogId = 0
						}
					}

					var error = false
					val type = intent.type

					if (type != null && type == ContactsContract.Contacts.CONTENT_VCARD_TYPE) {
						try {
							val uri = intent.extras?.get(Intent.EXTRA_STREAM) as? Uri

							if (uri != null) {
								contactsToSend = AndroidUtilities.loadVCardFromStream(uri, currentAccount, false, null, null)

								if ((contactsToSend?.size ?: 0) > 5) {
									contactsToSend = null

									documentsUrisArray = mutableListOf()
									documentsUrisArray?.add(uri)

									documentsMimeType = type
								}
								else {
									contactsToSendUri = uri
								}
							}
							else {
								error = true
							}
						}
						catch (e: Exception) {
							FileLog.e(e)
							error = true
						}
					}
					else {
						var text = intent.getStringExtra(Intent.EXTRA_TEXT)

						if (text == null) {
							val textSequence = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)

							if (textSequence != null) {
								text = textSequence.toString()
							}
						}

						val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

						if (!text.isNullOrEmpty()) {
							if ((text.startsWith("http://") || text.startsWith("https://")) && !subject.isNullOrEmpty()) {
								text = """
                                    $subject
                                    $text
                                    """.trimIndent()
							}

							sendingText = text
						}
						else if (!subject.isNullOrEmpty()) {
							sendingText = subject
						}

						var parcelable = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM)

						if (parcelable != null) {
							var path: String?

							if (parcelable !is Uri) {
								parcelable = Uri.parse(parcelable.toString())
							}

							val uri = parcelable as Uri?

							if (uri != null) {
								if (AndroidUtilities.isInternalUri(uri)) {
									error = true
								}
							}

							if (!error && uri != null) {
								if (type != null && type.startsWith("image/") || uri.toString().lowercase().endsWith(".jpg")) {
									if (photoPathsArray == null) {
										photoPathsArray = mutableListOf()
									}

									val info = SendingMediaInfo()
									info.uri = uri

									photoPathsArray?.add(info)
								}
								else {
									val originalPath = uri.toString()
									if (dialogId == 0L) {
										FileLog.d("export path = $originalPath")

										val exportUris = MessagesController.getInstance(intentAccount[0]).exportUri ?: emptySet()
										val fileName = FileLoader.fixFileName(MediaController.getFileName(uri))

										for (u in exportUris) {
											try {
												val pattern = Pattern.compile(u)

												if (pattern.matcher(originalPath).find() || pattern.matcher(fileName).find()) {
													exportingChatUri = uri
													break
												}
											}
											catch (e: Exception) {
												FileLog.e(e)
											}
										}

										if (exportingChatUri == null) {
											if (originalPath.startsWith("content://com.kakao.talk") && originalPath.endsWith("KakaoTalkChats.txt")) {
												exportingChatUri = uri
											}
										}
									}

									if (exportingChatUri == null) {
										path = AndroidUtilities.getPath(uri)

										if (!BuildVars.NO_SCOPED_STORAGE) {
											path = MediaController.copyFileToCache(uri, "file")
										}

										if (path != null) {
											if (path.startsWith("file:")) {
												path = path.replace("file://", "")
											}

											if (type != null && type.startsWith("video/")) {
												videoPath = path
											}
											else {
												if (documentsPathsArray == null) {
													documentsPathsArray = mutableListOf()
													documentsOriginalPathsArray = mutableListOf()
												}

												documentsPathsArray?.add(path)
												documentsOriginalPathsArray?.add(uri.toString())
											}
										}
										else {
											if (documentsUrisArray == null) {
												documentsUrisArray = mutableListOf()
											}

											documentsUrisArray?.add(uri)
											documentsMimeType = type
										}
									}
								}
							}
						}
						else if (sendingText == null) {
							error = true
						}
					}

					if (error) {
						Toast.makeText(this, R.string.unsupported_content, Toast.LENGTH_SHORT).show()
					}
				}
				else if ("org.telegram.messenger.CREATE_STICKER_PACK" == intent.action) {
					try {
						importingStickers = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
						importingStickersEmoji = intent.getStringArrayListExtra("STICKER_EMOJIS")
						importingStickersSoftware = intent.getStringExtra("IMPORTER")
					}
					catch (e: Throwable) {
						FileLog.e(e)
						importingStickers = null
						importingStickersEmoji = null
						importingStickersSoftware = null
					}
				}
				else if (Intent.ACTION_SEND_MULTIPLE == intent.action) {
					var error = false

					try {
						var uris = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
						val type = intent.type

						if (uris != null) {
							var a = 0

							while (a < uris.size) {
								var parcelable = uris[a]

								if (parcelable !is Uri) {
									parcelable = Uri.parse(parcelable.toString())
								}

								val uri = parcelable as? Uri

								if (uri != null) {
									if (AndroidUtilities.isInternalUri(uri)) {
										uris.removeAt(a)
										a--
									}
								}

								a++
							}

							if (uris.isEmpty()) {
								uris = null
							}
						}

						if (uris != null) {
							if (type != null && type.startsWith("image/")) {
								for (a in uris.indices) {
									var parcelable = uris[a]

									if (parcelable !is Uri) {
										parcelable = Uri.parse(parcelable.toString())
									}

									val uri = parcelable as Uri

									if (photoPathsArray == null) {
										photoPathsArray = mutableListOf()
									}

									val info = SendingMediaInfo()
									info.uri = uri

									photoPathsArray?.add(info)
								}
							}
							else {
								val exportUris = MessagesController.getInstance(intentAccount[0]).exportUri ?: emptySet()

								for (a in uris.indices) {
									var parcelable = uris[a]

									if (parcelable !is Uri) {
										parcelable = Uri.parse(parcelable.toString())
									}

									val uri = parcelable as Uri
									var path = AndroidUtilities.getPath(uri)
									var originalPath: String? = parcelable.toString()

									if (originalPath == null) {
										originalPath = path
									}

									FileLog.d("export path = $originalPath")

									if (dialogId == 0L && originalPath != null && exportingChatUri == null) {
										var ok = false
										val fileName = FileLoader.fixFileName(MediaController.getFileName(uri))

										for (u in exportUris) {
											try {
												val pattern = Pattern.compile(u)

												if (pattern.matcher(originalPath).find() || pattern.matcher(fileName).find()) {
													exportingChatUri = uri
													ok = true
													break
												}
											}
											catch (e: Exception) {
												FileLog.e(e)
											}
										}

										if (ok) {
											continue
										}
										else if (originalPath.startsWith("content://com.kakao.talk") && originalPath.endsWith("KakaoTalkChats.txt")) {
											exportingChatUri = uri
											continue
										}
									}

									if (path != null) {
										if (path.startsWith("file:")) {
											path = path.replace("file://", "")
										}

										if (documentsPathsArray == null) {
											documentsPathsArray = mutableListOf()
											documentsOriginalPathsArray = mutableListOf()
										}

										documentsPathsArray?.add(path)
										documentsOriginalPathsArray?.add(originalPath ?: "")
									}
									else {
										if (documentsUrisArray == null) {
											documentsUrisArray = mutableListOf()
										}

										documentsUrisArray?.add(uri)
										documentsMimeType = type
									}
								}
							}
						}
						else {
							error = true
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
						error = true
					}

					if (error) {
						Toast.makeText(this, R.string.unsupported_content, Toast.LENGTH_SHORT).show()
					}
				}
				else if (Intent.ACTION_VIEW == intent.action) {
					var data = intent.data

					if (data != null) {
						var username: String? = null
						var login: String? = null
						var group: String? = null
						var sticker: String? = null
						var emoji: String? = null
						var auth: HashMap<String?, String?>? = null
						var unsupportedUrl: String? = null
						var botUser: String? = null
						var botChat: String? = null
						var botChannel: String? = null
						var botChatAdminParams: String? = null
						var message: String? = null
						var phone: String? = null
						var game: String? = null
						var voicechat: String? = null
						var livestream: String? = null
						var phoneHash: String? = null
						var lang: String? = null
						var theme: String? = null
						var code: String? = null
						var wallPaper: TL_wallPaper? = null
						var inputInvoiceSlug: String? = null
						var messageId: Int? = null
						var channelId: Long? = null
						var threadId: Int? = null
						var commentId: Int? = null
						var videoTimestamp = -1
						var hasUrl = false
						var setAsAttachBot: String? = null
						var attachMenuBotToOpen: String? = null
						var attachMenuBotChoose: String? = null
						val scheme = data.scheme

						if (scheme != null) {
							when (scheme) {
								"http", "https" -> {
									val host = data.host?.lowercase()
									val prefixMatcher = PREFIX_ELLOAPP_PATTERN.matcher(host)
									val isPrefix = prefixMatcher.find()

									if (host == getString(R.string.domain) || isPrefix) {
										if (isPrefix) {
											data = Uri.parse(String.format(Locale.getDefault(), "https://%s/", getString(R.string.domain)) + prefixMatcher.group(1) + (if (TextUtils.isEmpty(data.path)) "" else data.path) + if (data.query.isNullOrEmpty()) "" else "?" + data.query)
										}

										var path = data?.path

										if (path != null && path.length > 1) {
											path = path.substring(1)

											if (path.startsWith("$")) {
												inputInvoiceSlug = path.substring(1)
											}
											else if (path.startsWith("invoice/")) {
												inputInvoiceSlug = path.substring(path.indexOf('/') + 1)
											}
											else if (path.startsWith("bg/")) {
												wallPaper = TL_wallPaper()
												wallPaper.settings = TL_wallPaperSettings()
												wallPaper.slug = path.replace("bg/", "")

												var ok = false

												if (wallPaper.slug != null && wallPaper.slug.length == 6) {
													try {
														wallPaper.settings.background_color = wallPaper.slug.toInt(16) or -0x1000000
														wallPaper.slug = null
														ok = true
													}
													catch (e: Exception) {
														// ignored
													}
												}
												else if (wallPaper.slug != null && wallPaper.slug.length >= 13 && AndroidUtilities.isValidWallChar(wallPaper.slug[6])) {
													try {
														wallPaper.settings.background_color = wallPaper.slug.substring(0, 6).toInt(16) or -0x1000000
														wallPaper.settings.second_background_color = wallPaper.slug.substring(7, 13).toInt(16) or -0x1000000

														if (wallPaper.slug.length >= 20 && AndroidUtilities.isValidWallChar(wallPaper.slug[13])) {
															wallPaper.settings.third_background_color = wallPaper.slug.substring(14, 20).toInt(16) or -0x1000000
														}

														if (wallPaper.slug.length == 27 && AndroidUtilities.isValidWallChar(wallPaper.slug[20])) {
															wallPaper.settings.fourth_background_color = wallPaper.slug.substring(21).toInt(16) or -0x1000000
														}

														try {
															val rotation = data?.getQueryParameter("rotation")

															if (!rotation.isNullOrEmpty()) {
																wallPaper.settings.rotation = Utilities.parseInt(rotation)
															}
														}
														catch (e: Exception) {
															// ignored
														}

														wallPaper.slug = null

														ok = true
													}
													catch (e: Exception) {
														// ignored
													}
												}

												if (!ok) {
													var mode = data?.getQueryParameter("mode")

													if (mode != null) {
														mode = mode.lowercase(Locale.getDefault())

														val modes = mode.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

														if (modes.isNotEmpty()) {
															for (s in modes) {
																if ("blur" == s) {
																	wallPaper.settings.blur = true
																}
																else if ("motion" == s) {
																	wallPaper.settings.motion = true
																}
															}
														}
													}

													val intensity = data?.getQueryParameter("intensity")

													if (!intensity.isNullOrEmpty()) {
														wallPaper.settings.intensity = Utilities.parseInt(intensity)
													}
													else {
														wallPaper.settings.intensity = 50
													}

													try {
														val bgColor = data?.getQueryParameter("bg_color")

														if (!bgColor.isNullOrEmpty()) {
															wallPaper.settings.background_color = bgColor.substring(0, 6).toInt(16) or -0x1000000

															if (bgColor.length >= 13) {
																wallPaper.settings.second_background_color = bgColor.substring(7, 13).toInt(16) or -0x1000000

																if (bgColor.length >= 20 && AndroidUtilities.isValidWallChar(bgColor[13])) {
																	wallPaper.settings.third_background_color = bgColor.substring(14, 20).toInt(16) or -0x1000000
																}

																if (bgColor.length == 27 && AndroidUtilities.isValidWallChar(bgColor[20])) {
																	wallPaper.settings.fourth_background_color = bgColor.substring(21).toInt(16) or -0x1000000
																}
															}
														}
														else {
															wallPaper.settings.background_color = -0x1
														}
													}
													catch (e: Exception) {
														// ignored
													}

													try {
														val rotation = data?.getQueryParameter("rotation")

														if (!rotation.isNullOrEmpty()) {
															wallPaper.settings.rotation = Utilities.parseInt(rotation)
														}
													}
													catch (e: Exception) {
														// ignored
													}
												}
											}
											else if (path.startsWith("login/")) {
												val intCode = Utilities.parseInt(path.replace("login/", ""))

												if (intCode != 0) {
													code = "" + intCode
												}
											}
											else if (path.startsWith("joinchat/")) {
												group = path.replace("joinchat/", "")
											}
											else if (path.startsWith("+")) {
												group = path.replace("+", "")

												if (AndroidUtilities.isNumeric(group)) {
													username = group
													group = null
												}
											}
											else if (path.startsWith("addstickers/")) {
												sticker = path.replace("addstickers/", "")
											}
											else if (path.startsWith("addemoji/")) {
												emoji = path.replace("addemoji/", "")
											}
											else if (path.startsWith("msg/") || path.startsWith("share/")) {
												message = data?.getQueryParameter("url") ?: ""

												if (data?.getQueryParameter("text") != null) {
													if (message.isNotEmpty()) {
														hasUrl = true
														message += "\n"
													}

													message += (data.getQueryParameter("text") ?: "")
												}

												if (message.length > 4096 * 4) {
													message = message.substring(0, 4096 * 4)
												}

												while (message?.endsWith("\n") == true) {
													message = message.substring(0, message.length - 1)
												}
											}
											else if (path.startsWith("confirmphone")) {
												phone = data?.getQueryParameter("phone")
												phoneHash = data?.getQueryParameter("hash")
											}
											else if (path.startsWith("setlanguage/")) {
												lang = path.substring(12)
											}
											else if (path.startsWith("addtheme/")) {
												theme = path.substring(9)
											}
											else if (path.startsWith("c/")) {
												val segments = data?.pathSegments ?: emptyList()

												if (segments.size == 3) {
													channelId = Utilities.parseLong(segments[1])
													messageId = Utilities.parseInt(segments[2])

													if (messageId == 0 || channelId == 0L) {
														messageId = null
														channelId = null
													}

													threadId = Utilities.parseInt(data?.getQueryParameter("thread"))

													if (threadId == 0) {
														threadId = null
													}
												}
											}
											else if (path.startsWith("invite_referral")) {
												val referralCode = data?.getQueryParameter("code")

												if (!referralCode.isNullOrEmpty()) {
													actionBarLayout?.fragmentsStack?.find { it is LoginActivity }?.let {
														if (it is LoginActivity) {
															REFERRAL_CODE = referralCode
															it.setReferralCode(referralCode)
														}
													}
												}
											}
											else if (path.isNotEmpty()) {
												val segments = (data?.pathSegments ?: emptyList()).toMutableList()

												if (segments.size > 0 && segments[0] == "s") {
													segments.removeAt(0)
												}

												if (segments.size > 0) {
													username = segments[0]

													if (segments.size > 1) {
														messageId = Utilities.parseInt(segments[1])

														if (messageId == 0) {
															messageId = null
														}
													}
												}

												if (messageId != null) {
													videoTimestamp = getTimestampFromLink(data)
												}

												botUser = data?.getQueryParameter("start")
												botChat = data?.getQueryParameter("startgroup")
												botChannel = data?.getQueryParameter("startchannel")
												botChatAdminParams = data?.getQueryParameter("admin")
												game = data?.getQueryParameter("game")
												voicechat = data?.getQueryParameter("voicechat")
												livestream = data?.getQueryParameter("livestream")
												setAsAttachBot = data?.getQueryParameter("startattach")
												attachMenuBotChoose = data?.getQueryParameter("choose")
												attachMenuBotToOpen = data?.getQueryParameter("attach")
												threadId = Utilities.parseInt(data?.getQueryParameter("thread"))

												if (threadId == 0) {
													threadId = null
												}

												commentId = Utilities.parseInt(data?.getQueryParameter("comment"))

												if (commentId == 0) {
													commentId = null
												}
											}
										}
									}
								}

								"elloapp" -> {
									var url = data.toString()

									if (url.startsWith("elloapp:premium_offer") || url.startsWith("elloapp://premium_offer")) {
										val finalUrl = url

										AndroidUtilities.runOnUIThread {
											if (actionBarLayout!!.fragmentsStack.isNotEmpty()) {
												val fragment = actionBarLayout!!.fragmentsStack[0]
												val uri = Uri.parse(finalUrl)
												fragment.presentFragment(PremiumPreviewFragment(uri.getQueryParameter("ref")))
											}
										}
									}
									else if (url.startsWith("elloapp:resolve") || url.startsWith("elloapp://resolve")) {
										url = url.replace("elloapp:resolve", "elloapp://ello.team").replace("elloapp://resolve", "elloapp://ello.team")
										data = Uri.parse(url)
										username = data.getQueryParameter("domain")

										if (username == null) {
											username = data.getQueryParameter("phone")

											if (username != null && username.startsWith("+")) {
												username = username.substring(1)
											}
										}

										if ("elloapppassport" == username) {
											username = null
											auth = HashMap()

											val scope = data.getQueryParameter("scope")

											if (!scope.isNullOrEmpty() && scope.startsWith("{") && scope.endsWith("}")) {
												auth["nonce"] = data.getQueryParameter("nonce")
											}
											else {
												auth["payload"] = data.getQueryParameter("payload")
											}

											auth["bot_id"] = data.getQueryParameter("bot_id")
											auth["scope"] = scope
											auth["public_key"] = data.getQueryParameter("public_key")
											auth["callback_url"] = data.getQueryParameter("callback_url")
										}
										else {
											botUser = data.getQueryParameter("start")
											botChat = data.getQueryParameter("startgroup")
											botChannel = data.getQueryParameter("startchannel")
											botChatAdminParams = data.getQueryParameter("admin")
											game = data.getQueryParameter("game")
											voicechat = data.getQueryParameter("voicechat")
											livestream = data.getQueryParameter("livestream")
											setAsAttachBot = data.getQueryParameter("startattach")
											attachMenuBotChoose = data.getQueryParameter("choose")
											attachMenuBotToOpen = data.getQueryParameter("attach")
											messageId = Utilities.parseInt(data.getQueryParameter("post"))

											if (messageId == 0) {
												messageId = null
											}

											threadId = Utilities.parseInt(data.getQueryParameter("thread"))

											if (threadId == 0) {
												threadId = null
											}

											commentId = Utilities.parseInt(data.getQueryParameter("comment"))

											if (commentId == 0) {
												commentId = null
											}
										}
									}
									else if (url.startsWith("elloapp:invoice") || url.startsWith("elloapp://invoice")) {
										url = url.replace("elloapp:invoice", "elloapp://invoice")
										data = Uri.parse(url)
										inputInvoiceSlug = data.getQueryParameter("slug")
									}
									else if (url.startsWith("elloapp:privatepost") || url.startsWith("elloapp://privatepost")) {
										url = url.replace("elloapp:privatepost", "elloapp://ello.team").replace("elloapp://privatepost", "elloapp://ello.team")
										data = Uri.parse(url)
										messageId = Utilities.parseInt(data.getQueryParameter("post"))
										channelId = Utilities.parseLong(data.getQueryParameter("channel"))

										if (messageId == 0 || channelId == 0L) {
											messageId = null
											channelId = null
										}

										threadId = Utilities.parseInt(data.getQueryParameter("thread"))

										if (threadId == 0) {
											threadId = null
										}

										commentId = Utilities.parseInt(data.getQueryParameter("comment"))

										if (commentId == 0) {
											commentId = null
										}
									}
									else if (url.startsWith("elloapp:bg") || url.startsWith("elloapp://bg")) {
										url = url.replace("elloapp:bg", "elloapp://ello.team").replace("elloapp://bg", "elloapp://ello.team")
										data = Uri.parse(url)

										wallPaper = TL_wallPaper()
										wallPaper.settings = TL_wallPaperSettings()
										wallPaper.slug = data.getQueryParameter("slug")

										if (wallPaper.slug == null) {
											wallPaper.slug = data.getQueryParameter("color")
										}

										var ok = false

										if (wallPaper.slug != null && wallPaper.slug.length == 6) {
											try {
												wallPaper.settings.background_color = wallPaper.slug.toInt(16) or -0x1000000
												wallPaper.slug = null
												ok = true
											}
											catch (e: Exception) {
												// ignored
											}
										}
										else if (wallPaper.slug != null && wallPaper.slug.length >= 13 && AndroidUtilities.isValidWallChar(wallPaper.slug[6])) {
											try {
												wallPaper.settings.background_color = wallPaper.slug.substring(0, 6).toInt(16) or -0x1000000
												wallPaper.settings.second_background_color = wallPaper.slug.substring(7, 13).toInt(16) or -0x1000000

												if (wallPaper.slug.length >= 20 && AndroidUtilities.isValidWallChar(wallPaper.slug[13])) {
													wallPaper.settings.third_background_color = wallPaper.slug.substring(14, 20).toInt(16) or -0x1000000
												}

												if (wallPaper.slug.length == 27 && AndroidUtilities.isValidWallChar(wallPaper.slug[20])) {
													wallPaper.settings.fourth_background_color = wallPaper.slug.substring(21).toInt(16) or -0x1000000
												}

												try {
													val rotation = data.getQueryParameter("rotation")

													if (!rotation.isNullOrEmpty()) {
														wallPaper.settings.rotation = Utilities.parseInt(rotation)
													}
												}
												catch (e: Exception) {
													// ignored
												}

												wallPaper.slug = null

												ok = true
											}
											catch (e: Exception) {
												// ignored
											}
										}

										if (!ok) {
											var mode = data.getQueryParameter("mode")

											if (mode != null) {
												mode = mode.lowercase(Locale.getDefault())

												val modes = mode.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

												if (modes.isNotEmpty()) {
													for (s in modes) {
														if ("blur" == s) {
															wallPaper.settings.blur = true
														}
														else if ("motion" == s) {
															wallPaper.settings.motion = true
														}
													}
												}
											}

											wallPaper.settings.intensity = Utilities.parseInt(data.getQueryParameter("intensity"))

											try {
												val bgColor = data.getQueryParameter("bg_color")

												if (!bgColor.isNullOrEmpty()) {
													wallPaper.settings.background_color = bgColor.substring(0, 6).toInt(16) or -0x1000000

													if (bgColor.length >= 13) {
														wallPaper.settings.second_background_color = bgColor.substring(8, 13).toInt(16) or -0x1000000

														if (bgColor.length >= 20 && AndroidUtilities.isValidWallChar(bgColor[13])) {
															wallPaper.settings.third_background_color = bgColor.substring(14, 20).toInt(16) or -0x1000000
														}

														if (bgColor.length == 27 && AndroidUtilities.isValidWallChar(bgColor[20])) {
															wallPaper.settings.fourth_background_color = bgColor.substring(21).toInt(16) or -0x1000000
														}
													}
												}
											}
											catch (e: Exception) {
												// ignored
											}

											try {
												val rotation = data.getQueryParameter("rotation")

												if (!rotation.isNullOrEmpty()) {
													wallPaper.settings.rotation = Utilities.parseInt(rotation)
												}
											}
											catch (e: Exception) {
												// ignored
											}
										}
									}
									else if (url.startsWith("elloapp:join") || url.startsWith("elloapp://join")) {
										url = url.replace("elloapp:join", "elloapp://ello.team").replace("elloapp://join", "elloapp://ello.team")
										data = Uri.parse(url)
										group = data.getQueryParameter("invite")
									}
									else if (url.startsWith("elloapp:addstickers") || url.startsWith("elloapp://addstickers")) {
										url = url.replace("elloapp:addstickers", "elloapp://ello.team").replace("elloapp://addstickers", "elloapp://ello.team")
										data = Uri.parse(url)
										sticker = data.getQueryParameter("set")
									}
									else if (url.startsWith("elloapp:addemoji") || url.startsWith("elloapp://addemoji")) {
										url = url.replace("elloapp:addemoji", "elloapp://ello.team").replace("elloapp://addemoji", "elloapp://ello.team")
										data = Uri.parse(url)
										emoji = data.getQueryParameter("set")
									}
									else if (url.startsWith("elloapp:msg") || url.startsWith("elloapp://msg") || url.startsWith("elloapp://share") || url.startsWith("elloapp:share")) {
										url = url.replace("elloapp:msg", "elloapp://ello.team").replace("elloapp://msg", "elloapp://ello.team").replace("elloapp://share", "elloapp://ello.team").replace("elloapp:share", "elloapp://ello.team")
										data = Uri.parse(url)
										message = data.getQueryParameter("url") ?: ""

										if (data.getQueryParameter("text") != null) {
											if (message.isNotEmpty()) {
												hasUrl = true
												message += "\n"
											}

											message += data.getQueryParameter("text")
										}

										if (message.length > 4096 * 4) {
											message = message.substring(0, 4096 * 4)
										}

										while (message?.endsWith("\n") == true) {
											message = message.substring(0, message.length - 1)
										}
									}
									else if (url.startsWith("elloapp:confirmphone") || url.startsWith("elloapp://confirmphone")) {
										url = url.replace("elloapp:confirmphone", "elloapp://ello.team").replace("elloapp://confirmphone", "elloapp://ello.team")
										data = Uri.parse(url)
										phone = data.getQueryParameter("phone")
										phoneHash = data.getQueryParameter("hash")
									}
									else if (url.startsWith("elloapp:login") || url.startsWith("elloapp://login")) {
										url = url.replace("elloapp:login", "elloapp://ello.team").replace("elloapp://login", "elloapp://ello.team")
										data = Uri.parse(url)
										login = data.getQueryParameter("token")

										val intCode = Utilities.parseInt(data.getQueryParameter("code"))

										if (intCode != 0) {
											code = "" + intCode
										}
									}
									else if (url.startsWith("elloapp:openmessage") || url.startsWith("elloapp://openmessage")) {
										url = url.replace("elloapp:openmessage", "elloapp://ello.team").replace("elloapp://openmessage", "elloapp://ello.team")
										data = Uri.parse(url)

										val userID = data.getQueryParameter("user_id")
										val chatID = data.getQueryParameter("chat_id")
										val msgID = data.getQueryParameter("message_id")

										if (userID != null) {
											try {
												push_user_id = userID.toLong()
											}
											catch (e: NumberFormatException) {
												// ignored
											}
										}
										else if (chatID != null) {
											try {
												push_chat_id = chatID.toLong()
											}
											catch (e: NumberFormatException) {
												// ignored
											}
										}

										if (msgID != null) {
											try {
												push_msg_id = msgID.toInt()
											}
											catch (e: NumberFormatException) {
												// ignored
											}
										}
									}
									else if (url.startsWith("elloapp:passport") || url.startsWith("elloapp://passport") || url.startsWith("elloapp:secureid")) {
										url = url.replace("elloapp:passport", "elloapp://ello.team").replace("elloapp://passport", "elloapp://ello.team").replace("elloapp:secureid", "elloapp://ello.team")
										data = Uri.parse(url)
										auth = HashMap()

										val scope = data.getQueryParameter("scope")

										if (!scope.isNullOrEmpty() && scope.startsWith("{") && scope.endsWith("}")) {
											auth["nonce"] = data.getQueryParameter("nonce")
										}
										else {
											auth["payload"] = data.getQueryParameter("payload")
										}

										auth["bot_id"] = data.getQueryParameter("bot_id")
										auth["scope"] = scope
										auth["public_key"] = data.getQueryParameter("public_key")
										auth["callback_url"] = data.getQueryParameter("callback_url")
									}
									else if (url.startsWith("elloapp:setlanguage") || url.startsWith("elloapp://setlanguage")) {
										url = url.replace("elloapp:setlanguage", "elloapp://ello.team").replace("elloapp://setlanguage", "elloapp://ello.team")
										data = Uri.parse(url)
										lang = data.getQueryParameter("lang")
									}
									else if (url.startsWith("elloapp:addtheme") || url.startsWith("elloapp://addtheme")) {
										url = url.replace("elloapp:addtheme", "elloapp://ello.team").replace("elloapp://addtheme", "elloapp://ello.team")
										data = Uri.parse(url)
										theme = data.getQueryParameter("slug")
									}
									else if (url.startsWith("elloapp:settings") || url.startsWith("elloapp://settings")) {
										open_settings = if (url.contains("themes")) {
											2
										}
										else if (url.contains("devices")) {
											3
										}
										else if (url.contains("folders")) {
											4
										}
										else if (url.contains("change_number")) {
											5
										}
										else {
											1
										}
									}
									else if (url.startsWith("elloapp:search") || url.startsWith("elloapp://search")) {
										url = url.replace("elloapp:search", "elloapp://ello.team").replace("elloapp://search", "elloapp://ello.team")
										data = Uri.parse(url)
										searchQuery = data.getQueryParameter("query")
										searchQuery = searchQuery?.trim { it <= ' ' } ?: ""
									}
									else if (url.startsWith("elloapp:calllog") || url.startsWith("elloapp://calllog")) {
										showCallLog = true
									}
									else if (url.startsWith("elloapp:call") || url.startsWith("elloapp://call")) {
										if (UserConfig.getInstance(currentAccount).isClientActivated) {
											val extraForceCall = "extra_force_call"

											if (ContactsController.getInstance(currentAccount).contactsLoaded || intent.hasExtra(extraForceCall)) {
												val callFormat = data.getQueryParameter("format")
												val callUserName = data.getQueryParameter("name")
												val contacts = findContacts(callUserName)

												if (contacts.isEmpty()) {
													newContactName = callUserName
													newContactAlert = true
												}
												else {
													if (contacts.size == 1) {
														push_user_id = contacts[0].user_id
													}

													if (push_user_id == 0L) {
														callSearchQuery = callUserName ?: ""
													}

													if ("video".equals(callFormat, ignoreCase = true)) {
														videoCallUser = true
													}
													else {
														audioCallUser = true
													}

													needCallAlert = true
												}
											}
											else {
												val copyIntent = Intent(intent)
												copyIntent.removeExtra(EXTRA_ACTION_TOKEN)
												copyIntent.putExtra(extraForceCall, true)

												ContactsLoadingObserver.observe({
													handleIntent(copyIntent, isNew = true, restore = false, fromPassword = false)
												}, 1000)
											}
										}
									}
									else if (url.startsWith("elloapp:scanqr") || url.startsWith("elloapp://scanqr")) {
										scanQr = true
									}
									else if (url.startsWith("elloapp:addcontact") || url.startsWith("elloapp://addcontact")) {
										url = url.replace("elloapp:addcontact", "elloapp://ello.team").replace("elloapp://addcontact", "elloapp://ello.team")
										data = Uri.parse(url)
										newContactName = data.getQueryParameter("name")
										newContact = true
									}
									else {
										unsupportedUrl = url.replace("elloapp://", "").replace("elloapp:", "")

										var index: Int

										if (unsupportedUrl.indexOf('?').also { index = it } >= 0) {
											unsupportedUrl = unsupportedUrl.substring(0, index)
										}
									}
								}
							}
						}

						if (intent.hasExtra(EXTRA_ACTION_TOKEN)) {
							val success = UserConfig.getInstance(currentAccount).isClientActivated && "elloapp" == scheme && unsupportedUrl == null
							val assistAction = AssistActionBuilder().setActionToken(intent.getStringExtra(EXTRA_ACTION_TOKEN) ?: "").setActionStatus(if (success) Action.Builder.STATUS_TYPE_COMPLETED else Action.Builder.STATUS_TYPE_FAILED).build()
							FirebaseUserActions.getInstance(this).end(assistAction)
							intent.removeExtra(EXTRA_ACTION_TOKEN)
						}

						if (code != null || UserConfig.getInstance(currentAccount).isClientActivated) {
							if (phone != null || phoneHash != null) {
								val cancelDeleteProgressDialog = AlertDialog(this@LaunchActivity, 3)
								cancelDeleteProgressDialog.setCanCancel(false)
								cancelDeleteProgressDialog.show()

								val req = TL_account_sendConfirmPhoneCode()
								req.hash = phoneHash
								req.settings = TL_codeSettings()
								req.settings.allow_flashcall = false
								req.settings.allow_app_hash = PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices()

								val params = Bundle()
								params.putString("phone", phone)

								ConnectionsManager.getInstance(currentAccount).sendRequest(req, { _, error ->
									AndroidUtilities.runOnUIThread {
										cancelDeleteProgressDialog.dismiss()

										if (error == null) {
											// TODO: check
											// presentFragment(new LoginActivity().cancelAccountDeletion(finalPhone, params, (TLRPC.TL_auth_sentCode)response));
										}
										else {
											AlertsCreator.processError(currentAccount, error, actionBarLayout!!.lastFragment, req)
										}
									}
								}, ConnectionsManager.RequestFlagFailOnServerErrors)
							}
							else if (username != null || group != null || sticker != null || emoji != null || message != null || game != null || voicechat != null || auth != null || unsupportedUrl != null || lang != null || code != null || wallPaper != null || inputInvoiceSlug != null || channelId != null || theme != null || login != null) {
								if (message != null && message.startsWith("@")) {
									message = " $message"
								}

								runLinkRequest(intentAccount[0], username, group, sticker, emoji, botUser, botChat, botChannel, botChatAdminParams, message, hasUrl, messageId, channelId, threadId, commentId, game, auth, lang, unsupportedUrl, code, login, wallPaper, inputInvoiceSlug, theme, voicechat, livestream, 0, videoTimestamp, setAsAttachBot, attachMenuBotToOpen, attachMenuBotChoose)
							}
						}
					}
				}
				else if (intent.action == "new_dialog") {
					open_new_dialog = 1
				}
				else if (intent.action?.startsWith("com.tmessages.openchat") == true) {
					val chatId = intent.getLongExtra("chatId", 0)
					val userId = intent.getLongExtra("userId", 0)
					val encId = intent.getIntExtra("encId", 0)
					val widgetId = intent.getIntExtra("appWidgetId", 0)

					if (widgetId != 0) {
						open_settings = 6
						open_widget_edit = widgetId
						open_widget_edit_type = intent.getIntExtra("appWidgetType", 0)
					}
					else {
						if (push_msg_id == 0) {
							push_msg_id = intent.getIntExtra("message_id", 0)
						}

						if (chatId != 0L) {
							NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats)
							push_chat_id = chatId
						}
						else if (userId != 0L) {
							NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats)
							push_user_id = userId
						}
						else if (encId != 0) {
							NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats)
							push_enc_id = encId
						}
						else {
							showDialogsList = true
						}
					}
				}
				else if (intent.action == "com.tmessages.openplayer") {
					showPlayer = true
				}
				else if (intent.action == "org.tmessages.openlocations") {
					showLocations = true
				}
				else if (action == "voip_chat") {
					showGroupVoip = true
				}
			}
		}

		if (UserConfig.getInstance(currentAccount).isClientActivated) {
			if (searchQuery != null) {
				val lastFragment = actionBarLayout?.lastFragment

				if (lastFragment is DialogsActivity) {
					if (lastFragment.isMainDialogList) {
						if (lastFragment.fragmentView != null) {
							lastFragment.search(searchQuery, true)
						}
						else {
							lastFragment.setInitialSearchString(searchQuery)
						}
					}
				}
				else {
					showDialogsList = true
				}
			}

			if (push_user_id != 0L) {
				if (audioCallUser || videoCallUser) {
					if (needCallAlert) {
						val lastFragment = actionBarLayout?.lastFragment

						if (lastFragment != null) {
							AlertsCreator.createCallDialogAlert(lastFragment, lastFragment.messagesController.getUser(push_user_id), videoCallUser)
						}
					}
					else {
						VoIPPendingCall.startOrSchedule(this, push_user_id, videoCallUser, AccountInstance.getInstance(intentAccount[0]))
					}
				}
				else {
					val args = Bundle()
					args.putLong("user_id", push_user_id)

					if (push_msg_id != 0) {
						args.putInt("message_id", push_msg_id)
					}

					if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount[0]).checkCanOpenChat(args, mainFragmentsStack[mainFragmentsStack.size - 1])) {
						val fragment = ChatActivity(args)

						if (actionBarLayout?.presentFragment(fragment, false, true, true, false) == true) {
							pushOpened = true
							drawerLayoutContainer?.closeDrawer()
						}
					}
				}
			}
			else if (push_chat_id != 0L) {
				val args = Bundle()
				args.putLong("chat_id", push_chat_id)

				if (push_msg_id != 0) {
					args.putInt("message_id", push_msg_id)
				}

				if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount[0]).checkCanOpenChat(args, mainFragmentsStack[mainFragmentsStack.size - 1])) {
					val fragment = ChatActivity(args)

					if (actionBarLayout?.presentFragment(fragment, false, true, true, false) == true) {
						pushOpened = true
						drawerLayoutContainer?.closeDrawer()
					}
				}
			}
			else if (push_enc_id != 0) {
				val args = Bundle()
				args.putInt("enc_id", push_enc_id)

				val fragment = ChatActivity(args)

				if (actionBarLayout?.presentFragment(fragment, false, true, true, false) == true) {
					pushOpened = true
					drawerLayoutContainer?.closeDrawer()
				}
			}
			else if (showDialogsList) {
				if (!AndroidUtilities.isTablet()) {
					actionBarLayout?.removeAllFragments()
				}
				else {
					if (layersActionBarLayout!!.fragmentsStack.isNotEmpty()) {
						while (0 < layersActionBarLayout!!.fragmentsStack.size - 1) {
							layersActionBarLayout?.removeFragmentFromStack(layersActionBarLayout!!.fragmentsStack[0])
						}

						layersActionBarLayout?.closeLastFragment(false)
					}
				}

				pushOpened = false
				isNew = false
			}
			else if (showPlayer) {
				if (actionBarLayout!!.fragmentsStack.isNotEmpty()) {
					val fragment = actionBarLayout!!.fragmentsStack[0]
					fragment.showDialog(AudioPlayerAlert(this))
				}

				pushOpened = false
			}
			else if (showLocations) {
				if (actionBarLayout!!.fragmentsStack.isNotEmpty()) {
					val fragment = actionBarLayout!!.fragmentsStack[0]

					fragment.showDialog(SharingLocationsAlert(this) {
						intentAccount[0] = it.messageObject!!.currentAccount

						switchToAccount(intentAccount[0])

						val locationActivity = LocationActivity(2)
						locationActivity.setMessageObject(it.messageObject)

						val dialog_id = it.messageObject!!.dialogId

						locationActivity.setDelegate { location, _, notify, scheduleDate ->
							SendMessagesHelper.getInstance(intentAccount[0]).sendMessage(location, dialog_id, null, null, null, null, notify, scheduleDate, false, null)
						}

						presentFragment(locationActivity)
					})
				}

				pushOpened = false
			}
			else if (exportingChatUri != null) {
				runImportRequest(exportingChatUri!!)
			}
			else if (importingStickers != null) {
				AndroidUtilities.runOnUIThread {
					if (actionBarLayout!!.fragmentsStack.isNotEmpty()) {
						val fragment = actionBarLayout!!.fragmentsStack[0]
						fragment.showDialog(StickersAlert(this, importingStickersSoftware, importingStickers, importingStickersEmoji))
					}
				}

				pushOpened = false
			}
			else if (videoPath != null || photoPathsArray != null || sendingText != null || documentsPathsArray != null || contactsToSend != null || documentsUrisArray != null) {
				if (dialogId == 0L) {
					openDialogsToSend(false)
					pushOpened = true
				}
				else {
					val dids = listOf(dialogId)

					didSelectDialogs(null, dids, null, false)
				}
			}
			else if (open_settings != 0) {
				val fragment: BaseFragment?
				var closePrevious = false

				when (open_settings) {
					1 -> {
						val args = Bundle()
						args.putLong("user_id", UserConfig.getInstance(currentAccount).clientUserId)
						fragment = ProfileActivity(args)
					}

					2 -> {
						fragment = ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)
					}

					3 -> {
						fragment = SessionsActivity(SessionsActivity.ALL_SESSIONS)
					}

					4 -> {
						fragment = FiltersSetupActivity()
					}

					5 -> {
						fragment = ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER)
						closePrevious = true
					}

					6 -> {
						fragment = EditWidgetActivity(open_widget_edit_type, open_widget_edit)
					}

					else -> {
						fragment = null
					}
				}

				val closePreviousFinal = closePrevious

				if (open_settings == 6) {
					actionBarLayout?.presentFragment(fragment, false, true, true, false)
				}
				else {
					AndroidUtilities.runOnUIThread {
						presentFragment(fragment, closePreviousFinal, false)
					}
				}

				if (AndroidUtilities.isTablet()) {
					actionBarLayout?.showLastFragment()
					rightActionBarLayout?.showLastFragment()
					drawerLayoutContainer?.setAllowOpenDrawer(false, false)
				}
				else {
					drawerLayoutContainer?.setAllowOpenDrawer(true, false)
				}

				pushOpened = true
			}
			else if (open_new_dialog != 0) {
				val args = Bundle()
				args.putBoolean("destroyAfterSelect", true)

				actionBarLayout?.presentFragment(ContactsActivity(args), false, true, true, false)

				if (AndroidUtilities.isTablet()) {
					actionBarLayout?.showLastFragment()
					rightActionBarLayout?.showLastFragment()
					drawerLayoutContainer?.setAllowOpenDrawer(false, false)
				}
				else {
					drawerLayoutContainer?.setAllowOpenDrawer(true, false)
				}

				pushOpened = true
			}
			else if (callSearchQuery != null) {
				val args = Bundle()
				args.putBoolean("destroyAfterSelect", true)
				args.putBoolean("returnAsResult", true)
				args.putBoolean("onlyUsers", true)
				args.putBoolean("allowSelf", false)

				val contactsFragment = ContactsActivity(args)
				contactsFragment.setInitialSearchString(callSearchQuery)

				val videoCall = videoCallUser

				contactsFragment.setDelegate { user, _, _ ->
					val userFull = MessagesController.getInstance(currentAccount).getUserFull(user!!.id)
					startCall(user, videoCall, userFull != null && userFull.video_calls_available, this@LaunchActivity, userFull, AccountInstance.getInstance(intentAccount[0]))
				}

				actionBarLayout?.presentFragment(contactsFragment, actionBarLayout?.lastFragment is ContactsActivity, true, true, false)

				if (AndroidUtilities.isTablet()) {
					actionBarLayout?.showLastFragment()
					rightActionBarLayout?.showLastFragment()
					drawerLayoutContainer?.setAllowOpenDrawer(false, false)
				}
				else {
					drawerLayoutContainer?.setAllowOpenDrawer(true, false)
				}

				pushOpened = true
			}
			else if (scanQr) {
				val fragment = ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_QR_LOGIN)

				fragment.setQrLoginDelegate {
					val progressDialog = AlertDialog(this@LaunchActivity, 3)
					progressDialog.setCanCancel(false)
					progressDialog.show()

					val token = Base64.decode(it.substring("elloapp://login?token=".length), Base64.URL_SAFE)

					val req = TL_auth_acceptLoginToken()
					req.token = token

					ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
						AndroidUtilities.runOnUIThread {
							runCatching {
								progressDialog.dismiss()
							}

							if (response !is TL_authorization) {
								AndroidUtilities.runOnUIThread {
									AlertsCreator.showSimpleAlert(fragment, getString(R.string.AuthAnotherClient), "${getString(R.string.ErrorOccurred)}\n${error?.text}")
								}
							}
						}
					}
				}

				actionBarLayout?.presentFragment(fragment, false, true, true, false)

				if (AndroidUtilities.isTablet()) {
					actionBarLayout?.showLastFragment()
					rightActionBarLayout?.showLastFragment()
					drawerLayoutContainer?.setAllowOpenDrawer(false, false)
				}
				else {
					drawerLayoutContainer?.setAllowOpenDrawer(true, false)
				}

				pushOpened = true
			}
			else if (newContact) {
				val fragment = NewContactActivity()

				if (newContactName != null) {
					val names = newContactName.split(" ".toRegex(), limit = 2).toTypedArray()
					fragment.setInitialName(names[0], if (names.size > 1) names[1] else null)
				}

				actionBarLayout?.presentFragment(fragment, false, true, true, false)

				if (AndroidUtilities.isTablet()) {
					actionBarLayout?.showLastFragment()
					rightActionBarLayout?.showLastFragment()
					drawerLayoutContainer?.setAllowOpenDrawer(false, false)
				}
				else {
					drawerLayoutContainer?.setAllowOpenDrawer(true, false)
				}

				pushOpened = true
			}
			else if (showGroupVoip) {
				GroupCallActivity.create(this, AccountInstance.getInstance(currentAccount), null, null, false, null)

				if (GroupCallActivity.groupCallInstance != null) {
					GroupCallActivity.groupCallUiVisible = true
				}
			}
			else if (newContactAlert) {
				val lastFragment = actionBarLayout!!.lastFragment

				if (lastFragment != null && lastFragment.parentActivity != null) {
					val finalNewContactName = newContactName

					val newContactAlertDialog = AlertDialog.Builder(lastFragment.parentActivity!!).setTitle(getString(R.string.NewContactAlertTitle)).setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("NewContactAlertMessage", R.string.NewContactAlertMessage, finalNewContactName))).setPositiveButton(getString(R.string.NewContactAlertButton)) { _, _ ->
						val fragment = NewContactActivity()

						if (finalNewContactName != null) {
							val names = finalNewContactName.split(" ".toRegex(), limit = 2).toTypedArray()
							fragment.setInitialName(names[0], if (names.size > 1) names[1] else null)
						}

						lastFragment.presentFragment(fragment)
					}.setNegativeButton(getString(R.string.Cancel), null).create()

					lastFragment.showDialog(newContactAlertDialog)

					pushOpened = true
				}
			}
			else if (showCallLog) {
				actionBarLayout?.presentFragment(CallLogActivity(), false, true, true, false)

				if (AndroidUtilities.isTablet()) {
					actionBarLayout?.showLastFragment()
					rightActionBarLayout?.showLastFragment()
					drawerLayoutContainer?.setAllowOpenDrawer(false, false)
				}
				else {
					drawerLayoutContainer?.setAllowOpenDrawer(true, false)
				}

				pushOpened = true
			}
		}
		if (!pushOpened && !isNew) {
			if (AndroidUtilities.isTablet()) {
				if (!UserConfig.getInstance(currentAccount).isClientActivated) {
					if (layersActionBarLayout!!.fragmentsStack.isEmpty()) {
						// layersActionBarLayout?.addFragmentToStack(clientNotActivatedFragment)
						layersActionBarLayout?.addFragmentToStack(LoginActivity())
						drawerLayoutContainer?.setAllowOpenDrawer(false, false)
					}
				}
				else {
					if (actionBarLayout!!.fragmentsStack.isEmpty()) {
						val dialogsActivity = DialogsActivity(null)

						if (searchQuery != null) {
							dialogsActivity.setInitialSearchString(searchQuery)
						}

						actionBarLayout?.addFragmentToStack(dialogsActivity)
						drawerLayoutContainer?.setAllowOpenDrawer(true, false)
					}
				}
			}
			else {
				if (actionBarLayout!!.fragmentsStack.isEmpty()) {
					if (!UserConfig.getInstance(currentAccount).isClientActivated) {
						// actionBarLayout?.addFragmentToStack(clientNotActivatedFragment)
						actionBarLayout?.addFragmentToStack(LoginActivity())
						drawerLayoutContainer?.setAllowOpenDrawer(false, false)
					}
					else {
						val dialogsActivity = DialogsActivity(null)
						if (searchQuery != null) {
							dialogsActivity.setInitialSearchString(searchQuery)
						}

						actionBarLayout?.addFragmentToStack(dialogsActivity)
						drawerLayoutContainer?.setAllowOpenDrawer(true, false)
					}
				}
			}

			actionBarLayout?.showLastFragment()

			if (AndroidUtilities.isTablet()) {
				layersActionBarLayout?.showLastFragment()
				rightActionBarLayout?.showLastFragment()
			}
		}

		if (isVoipIntent) {
			VoIPFragment.show(this, intentAccount[0])
		}

		if (!showGroupVoip && (intent == null || Intent.ACTION_MAIN != intent.action)) {
			GroupCallActivity.groupCallInstance?.dismiss()
		}

		intent?.action = null

		return pushOpened
	}

	private fun openDialogsToSend(animated: Boolean) {
		val args = Bundle()
		args.putBoolean("onlySelect", true)
		args.putInt("dialogsType", 3)
		args.putBoolean("allowSwitchAccount", true)

		if (contactsToSend != null) {
			if (contactsToSend!!.size != 1) {
				args.putString("selectAlertString", getString(R.string.SendMessagesToText))
				args.putString("selectAlertStringGroup", getString(R.string.SendContactToGroupText))
			}
		}
		else {
			args.putString("selectAlertString", getString(R.string.SendMessagesToText))
			args.putString("selectAlertStringGroup", getString(R.string.SendMessagesToGroupText))
		}

		val fragment = object : DialogsActivity(args) {
			override fun shouldShowNextButton(fragment: DialogsActivity?, dids: ArrayList<Long>?, message: CharSequence?, param: Boolean): Boolean {
				if (exportingChatUri != null) {
					return false
				}
				else {
					if (contactsToSend?.size == 1 && mainFragmentsStack.isNotEmpty()) {
						return true
					}
					else {
						if ((dids?.size ?: 0) <= 1) {
							if (videoPath != null) {
								return true
							}
							else if (!photoPathsArray.isNullOrEmpty()) {
								return true
							}
						}
					}
				}

				return false
			}
		}

		fragment.setDelegate(this)

		val removeLast = if (AndroidUtilities.isTablet()) {
			layersActionBarLayout!!.fragmentsStack.size > 0 && layersActionBarLayout!!.fragmentsStack[layersActionBarLayout!!.fragmentsStack.size - 1] is DialogsActivity
		}
		else {
			actionBarLayout!!.fragmentsStack.size > 1 && actionBarLayout!!.fragmentsStack[actionBarLayout!!.fragmentsStack.size - 1] is DialogsActivity
		}

		actionBarLayout!!.presentFragment(fragment, removeLast, !animated, true, false)

		if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible) {
			SecretMediaViewer.getInstance().closePhoto(false, false)
		}
		else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
			PhotoViewer.getInstance().closePhoto(false, true)
		}
		else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible) {
			ArticleViewer.getInstance().close(false, true)
		}

		GroupCallActivity.groupCallInstance?.dismiss()

		if (!animated) {
			drawerLayoutContainer?.setAllowOpenDrawer(false, false)

			if (AndroidUtilities.isTablet()) {
				actionBarLayout?.showLastFragment()
				rightActionBarLayout?.showLastFragment()
			}
			else {
				drawerLayoutContainer?.setAllowOpenDrawer(true, false)
			}
		}
	}

	private fun runCommentRequest(intentAccount: Int, progressDialog: AlertDialog, messageId: Int, commentId: Int?, threadId: Int?, chat: Chat?): Int {
		if (chat == null) {
			return 0
		}

		val req = TL_messages_getDiscussionMessage()
		req.peer = MessagesController.getInputPeer(chat)
		req.msg_id = if (commentId != null) messageId else (threadId ?: 0)

		return ConnectionsManager.getInstance(intentAccount).sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				var chatOpened = false

				if (response is TL_messages_discussionMessage) {
					MessagesController.getInstance(intentAccount).putUsers(response.users, false)
					MessagesController.getInstance(intentAccount).putChats(response.chats, false)

					val arrayList = ArrayList<MessageObject>()
					var a = 0
					val n = response.messages.size

					while (a < n) {
						arrayList.add(MessageObject(UserConfig.selectedAccount, response.messages[a], generateLayout = true, checkMediaExists = true))
						a++
					}

					if (arrayList.isNotEmpty()) {
						val args = Bundle()
						args.putLong("chat_id", -arrayList[0].dialogId)
						args.putInt("message_id", max(1, messageId))

						val chatActivity = ChatActivity(args)
						chatActivity.setThreadMessages(arrayList, chat, req.msg_id, response.read_inbox_max_id, response.read_outbox_max_id)

						if (commentId != null) {
							chatActivity.setHighlightMessageId(commentId)
						}
						else if (threadId != null) {
							chatActivity.setHighlightMessageId(messageId)
						}

						presentFragment(chatActivity)

						chatOpened = true
					}
				}

				if (!chatOpened) {
					try {
						if (mainFragmentsStack.isNotEmpty()) {
							BulletinFactory.of(mainFragmentsStack[mainFragmentsStack.size - 1]).createErrorBulletin(getString(R.string.ChannelPostDeleted)).show()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				try {
					progressDialog.dismiss()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
	}

	private fun runImportRequest(importUri: Uri) {
		val intentAccount = UserConfig.selectedAccount
		val progressDialog = AlertDialog(this, 3)
		val requestId = intArrayOf(0)
		val cancelRunnable: Runnable? = null
		var content: String? = null

		try {
			contentResolver.openInputStream(importUri)?.use { inputStream ->
				var linesCount = 0

				val r = BufferedReader(InputStreamReader(inputStream))
				val total = StringBuilder()
				var line: String?

				while (r.readLine().also { line = it } != null && linesCount < 100) {
					total.append(line).append('\n')
					linesCount++
				}

				content = total.toString()
			}

		}
		catch (e: Exception) {
			FileLog.e(e)
			return
		}

		if (content.isNullOrEmpty()) {
			return
		}

		val req = TL_messages_checkHistoryImport()
		req.import_head = content

		requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread({
				if (!this@LaunchActivity.isFinishing) {
					if (response != null && actionBarLayout != null) {
						val res = response as TL_messages_historyImportParsed

						val args = Bundle()
						args.putBoolean("onlySelect", true)
						args.putString("importTitle", res.title)
						args.putBoolean("allowSwitchAccount", true)

						if (res.pm) {
							args.putInt("dialogsType", 12)
						}
						else if (res.group) {
							args.putInt("dialogsType", 11)
						}
						else {
							val uri = importUri.toString()
							var uris = MessagesController.getInstance(intentAccount).exportPrivateUri ?: emptySet()
							var ok = false

							for (u in uris) {
								if (uri.contains(u)) {
									args.putInt("dialogsType", 12)
									ok = true
									break
								}
							}

							if (!ok) {
								uris = MessagesController.getInstance(intentAccount).exportGroupUri ?: emptySet()

								for (u in uris) {
									if (uri.contains(u)) {
										args.putInt("dialogsType", 11)
										ok = true
										break
									}
								}

								if (!ok) {
									args.putInt("dialogsType", 13)
								}
							}
						}

						if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible) {
							SecretMediaViewer.getInstance().closePhoto(false, false)
						}
						else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
							PhotoViewer.getInstance().closePhoto(false, true)
						}
						else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible) {
							ArticleViewer.getInstance().close(false, true)
						}


						GroupCallActivity.groupCallInstance?.dismiss()

						drawerLayoutContainer?.setAllowOpenDrawer(false, false)

						if (AndroidUtilities.isTablet()) {
							actionBarLayout?.showLastFragment()
							rightActionBarLayout?.showLastFragment()
						}
						else {
							drawerLayoutContainer?.setAllowOpenDrawer(true, false)
						}

						val fragment = DialogsActivity(args)
						fragment.setDelegate(this)

						val removeLast = if (AndroidUtilities.isTablet()) {
							layersActionBarLayout!!.fragmentsStack.size > 0 && layersActionBarLayout!!.fragmentsStack[layersActionBarLayout!!.fragmentsStack.size - 1] is DialogsActivity
						}
						else {
							actionBarLayout!!.fragmentsStack.size > 1 && actionBarLayout!!.fragmentsStack[actionBarLayout!!.fragmentsStack.size - 1] is DialogsActivity
						}

						actionBarLayout!!.presentFragment(fragment, removeLast, false, true, false)
					}
					else {
						if (documentsUrisArray == null) {
							documentsUrisArray = mutableListOf()
						}

						documentsUrisArray?.add(0, exportingChatUri ?: Uri.EMPTY)
						exportingChatUri = null

						openDialogsToSend(true)
					}

					try {
						progressDialog.dismiss()
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors.toLong())
		}

		progressDialog.setOnCancelListener {
			ConnectionsManager.getInstance(intentAccount).cancelRequest(requestId[0], true)
			cancelRunnable?.run()
		}

		try {
			progressDialog.showDelayed(300)
		}
		catch (ignore: Exception) {
			// ignored
		}
	}

	private fun openGroupCall(accountInstance: AccountInstance, chat: Chat, hash: String) {
		startCall(chat, hash, false, this, mainFragmentsStack[mainFragmentsStack.size - 1], accountInstance)
	}

	fun runLinkRequest(intentAccount: Int, username: String?) {
		runLinkRequest(intentAccount, username, null, null, null, null, null, null, null, null, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 1, 0, null, null, null)
	}

	fun switchToFeedFragment(forYouTab: Int) {
		this.forYouTab = forYouTab

		bottomNavigationPanel?.setCurrentItem(BottomNavigationPanel.Item.FEED)
	}

	fun switchToContactsFragment() {
		bottomNavigationPanel?.setCurrentItem(BottomNavigationPanel.Item.CONTACTS)
	}

	private fun runLinkRequest(intentAccount: Int, username: String?, group: String?, sticker: String?, emoji: String?, botUser: String?, botChat: String?, botChannel: String?, botChatAdminParams: String?, message: String?, hasUrl: Boolean, messageId: Int?, channelId: Long?, threadId: Int?, commentId: Int?, game: String?, auth: HashMap<String?, String?>?, lang: String?, unsupportedUrl: String?, code: String?, loginToken: String?, wallPaper: TL_wallPaper?, inputInvoiceSlug: String?, theme: String?, voicechat: String?, livestream: String?, state: Int, videoTimestamp: Int, setAsAttachBot: String?, attachMenuBotToOpen: String?, attachMenuBotChoose: String?) {
		if (state == 0 && UserConfig.activatedAccountsCount >= 2 && auth != null) {
			AlertsCreator.createAccountSelectDialog(this) {
				if (it != intentAccount) {
					switchToAccount(it)
				}

				runLinkRequest(it, username, group, sticker, emoji, botUser, botChat, botChannel, botChatAdminParams, message, hasUrl, messageId, channelId, threadId, commentId, game, auth, lang, unsupportedUrl, code, loginToken, wallPaper, inputInvoiceSlug, theme, voicechat, livestream, 1, videoTimestamp, setAsAttachBot, attachMenuBotToOpen, attachMenuBotChoose)
			}?.show()

			return
		}
		else if (code != null) {
			if (NotificationCenter.globalInstance.hasObservers(NotificationCenter.didReceiveSmsCode)) {
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.didReceiveSmsCode, code)
			}
			else {
				val builder = AlertDialog.Builder(this@LaunchActivity)
				builder.setTitle(getString(R.string.AppName))
				builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("OtherLoginCode", R.string.OtherLoginCode, code)))
				builder.setPositiveButton(getString(R.string.OK), null)

				showAlertDialog(builder)
			}
			return
		}
		else if (loginToken != null) {
			val builder = AlertDialog.Builder(this@LaunchActivity)
			builder.setTitle(getString(R.string.AuthAnotherClient))
			builder.setMessage(getString(R.string.AuthAnotherClientUrl))
			builder.setPositiveButton(getString(R.string.OK), null)

			showAlertDialog(builder)

			return
		}

		val progressDialog = AlertDialog(this, 3)
		val requestId = intArrayOf(0)

		if (inputInvoiceSlug != null) {
			val req = TL_payments_getPaymentForm()

			val invoiceSlug = TL_inputInvoiceSlug()
			invoiceSlug.slug = inputInvoiceSlug
			req.invoice = invoiceSlug

			requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					if (error != null) {
						BulletinFactory.of(mainFragmentsStack[mainFragmentsStack.size - 1]).createErrorBulletin(getString(R.string.PaymentInvoiceLinkInvalid)).show()
					}
					else if (!this@LaunchActivity.isFinishing) {
						var paymentFormActivity: PaymentFormActivity? = null

						if (response is TL_payments_paymentForm) {
							MessagesController.getInstance(intentAccount).putUsers(response.users, false)
							paymentFormActivity = PaymentFormActivity(response, inputInvoiceSlug, actionBarLayout!!.lastFragment)
						}
						else if (response is TL_payments_paymentReceipt) {
							paymentFormActivity = PaymentFormActivity(response as TL_payments_paymentReceipt?)
						}

						if (paymentFormActivity != null) {
							if (navigateToPremiumGiftCallback != null) {
								val callback = navigateToPremiumGiftCallback

								navigateToPremiumGiftCallback = null

								paymentFormActivity.setPaymentFormCallback(PaymentFormCallback {
									if (it == InvoiceStatus.PAID) {
										callback?.run()
									}
								})
							}

							presentFragment(paymentFormActivity)
						}
					}

					try {
						progressDialog.dismiss()
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}
		}
		else if (username != null) {
			val req = if (AndroidUtilities.isNumeric(username)) {
				val resolvePhone = TL_contacts_resolvePhone()
				resolvePhone.phone = username
				resolvePhone
			}
			else {
				val resolveUsername = TL_contacts_resolveUsername()
				resolveUsername.username = username
				resolveUsername
			}

			requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread(Runnable {
					if (!this@LaunchActivity.isFinishing) {
						var hideProgressDialog = true
						val res = response as TL_contacts_resolvedPeer?

						if (error == null && actionBarLayout != null && (game == null && voicechat == null || game != null && res!!.users.isNotEmpty() || voicechat != null && res!!.chats.isNotEmpty() || livestream != null && res!!.chats.isNotEmpty())) {
							MessagesController.getInstance(intentAccount).putUsers(res!!.users, false)
							MessagesController.getInstance(intentAccount).putChats(res.chats, false)
							MessagesStorage.getInstance(intentAccount).putUsersAndChats(res.users, res.chats, false, true)

							if (setAsAttachBot != null && attachMenuBotToOpen == null) {
								val user = MessagesController.getInstance(intentAccount).getUser(res.peer.user_id)

								if (user != null && user.bot) {
									if (user.bot_attach_menu) {
										val getAttachMenuBot = TL_messages_getAttachMenuBot()
										getAttachMenuBot.bot = MessagesController.getInstance(intentAccount).getInputUser(res.peer.user_id)

										ConnectionsManager.getInstance(intentAccount).sendRequest(getAttachMenuBot) { response1, _ ->
											AndroidUtilities.runOnUIThread {
												if (response1 is TL_attachMenuBotsBot) {
													MessagesController.getInstance(intentAccount).putUsers(response1.users, false)

													val attachMenuBot = response1.bot
													val lastFragment = mainFragmentsStack[mainFragmentsStack.size - 1]
													val chooserTargets: MutableList<String> = ArrayList()

													if (!attachMenuBotChoose.isNullOrEmpty()) {
														for (target in attachMenuBotChoose.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
															if (MediaDataController.canShowAttachMenuBotForTarget(attachMenuBot, target)) {
																chooserTargets.add(target)
															}
														}
													}

													val dialogsActivity: DialogsActivity?

													if (chooserTargets.isNotEmpty()) {
														val args = Bundle()
														args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_START_ATTACH_BOT)
														args.putBoolean("onlySelect", true)
														args.putBoolean("allowGroups", chooserTargets.contains("groups"))
														args.putBoolean("allowUsers", chooserTargets.contains("users"))
														args.putBoolean("allowChannels", chooserTargets.contains("channels"))
														args.putBoolean("allowBots", chooserTargets.contains("bots"))

														dialogsActivity = DialogsActivity(args)

														dialogsActivity.setDelegate(DialogsActivityDelegate { fragment, dids, _, _ ->
															val did = dids[0]

															val args1 = Bundle()
															args1.putBoolean("scrollToTopOnResume", true)

															if (DialogObject.isEncryptedDialog(did)) {
																args1.putInt("enc_id", DialogObject.getEncryptedChatId(did))
															}
															else if (DialogObject.isUserDialog(did)) {
																args1.putLong("user_id", did)
															}
															else {
																args1.putLong("chat_id", -did)
															}

															args1.putString("attach_bot", user.username)

															if (setAsAttachBot != null) {
																args1.putString("attach_bot_start_command", setAsAttachBot)
															}

															if (MessagesController.getInstance(intentAccount).checkCanOpenChat(args1, fragment)) {
																NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats)
																actionBarLayout?.presentFragment(ChatActivity(args1), true, false, true, false)
															}
														})
													}
													else {
														dialogsActivity = null
													}

													if (!attachMenuBot.inactive) {
														dialogsActivity?.let {
															presentFragment(it)
														} ?: if (lastFragment is ChatActivity) {
															if (!MediaDataController.canShowAttachMenuBot(attachMenuBot, lastFragment.currentUser ?: lastFragment.currentChat)) {
																BulletinFactory.of(lastFragment).createErrorBulletin(getString(R.string.BotAlreadyAddedToAttachMenu)).show()
																return@runOnUIThread
															}

															lastFragment.openAttachBotLayout(user.id, setAsAttachBot)
														}
														else {
															BulletinFactory.of(lastFragment).createErrorBulletin(getString(R.string.BotAlreadyAddedToAttachMenu)).show()
														}
													}
													else {
														val introTopView = AttachBotIntroTopView(this@LaunchActivity)
														introTopView.setColor(ResourcesCompat.getColor(resources, R.color.white, null))
														introTopView.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.brand, null))
														introTopView.setAttachBot(attachMenuBot)

														AlertDialog.Builder(this@LaunchActivity).setTopView(introTopView).setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BotRequestAttachPermission", R.string.BotRequestAttachPermission, UserObject.getUserName(user)))).setPositiveButton(getString(R.string.BotAddToMenu)) { _, _ ->
															val botRequest = TL_messages_toggleBotInAttachMenu()
															botRequest.bot = MessagesController.getInstance(intentAccount).getInputUser(res.peer.user_id)
															botRequest.enabled = true

															ConnectionsManager.getInstance(intentAccount).sendRequest(botRequest, { response2, _ ->
																AndroidUtilities.runOnUIThread {
																	if (response2 is TL_boolTrue) {
																		MediaDataController.getInstance(intentAccount).loadAttachMenuBots(cache = false, force = true)

																		if (dialogsActivity != null) {
																			presentFragment(dialogsActivity)
																		}
																		else if (lastFragment is ChatActivity) {
																			lastFragment.openAttachBotLayout(user.id, setAsAttachBot)
																		}
																	}
																}
															}, ConnectionsManager.RequestFlagInvokeAfter or ConnectionsManager.RequestFlagFailOnServerErrors)
														}.setNegativeButton(getString(R.string.Cancel), null).show()
													}
												}
												else {
													BulletinFactory.of(mainFragmentsStack[mainFragmentsStack.size - 1]).createErrorBulletin(getString(R.string.BotCantAddToAttachMenu)).show()
												}
											}
										}
									}
									else {
										BulletinFactory.of(mainFragmentsStack[mainFragmentsStack.size - 1]).createErrorBulletin(getString(R.string.BotCantAddToAttachMenu)).show()
									}
								}
								else {
									BulletinFactory.of(mainFragmentsStack[mainFragmentsStack.size - 1]).createErrorBulletin(getString(R.string.BotSetAttachLinkNotBot)).show()
								}
							}
							else if (messageId != null && (commentId != null || threadId != null) && res.chats.isNotEmpty()) {
								requestId[0] = runCommentRequest(intentAccount, progressDialog, messageId, commentId, threadId, res.chats[0])

								if (requestId[0] != 0) {
									hideProgressDialog = false
								}
							}
							else if (game != null) {
								val args = Bundle()
								args.putBoolean("onlySelect", true)
								args.putBoolean("cantSendToChannels", true)
								args.putInt("dialogsType", 1)
								args.putString("selectAlertString", getString(R.string.SendGameToText))
								args.putString("selectAlertStringGroup", getString(R.string.SendGameToGroupText))

								val fragment = DialogsActivity(args)

								fragment.setDelegate { fragment1, dids, _, _ ->
									val did = dids[0]

									val inputMediaGame = TL_inputMediaGame()
									inputMediaGame.id = TL_inputGameShortName()
									inputMediaGame.id.short_name = game
									inputMediaGame.id.bot_id = MessagesController.getInstance(intentAccount).getInputUser(res.users[0])

									SendMessagesHelper.getInstance(intentAccount).sendGame(MessagesController.getInstance(intentAccount).getInputPeer(did), inputMediaGame, 0, 0)

									val args1 = Bundle()
									args1.putBoolean("scrollToTopOnResume", true)

									if (DialogObject.isEncryptedDialog(did)) {
										args1.putInt("enc_id", DialogObject.getEncryptedChatId(did))
									}
									else if (DialogObject.isUserDialog(did)) {
										args1.putLong("user_id", did)
									}
									else {
										args1.putLong("chat_id", -did)
									}

									if (MessagesController.getInstance(intentAccount).checkCanOpenChat(args1, fragment1)) {
										NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats)
										actionBarLayout?.presentFragment(ChatActivity(args1), true, false, true, false)
									}
								}

								val removeLast = if (AndroidUtilities.isTablet()) {
									layersActionBarLayout!!.fragmentsStack.size > 0 && layersActionBarLayout!!.fragmentsStack[layersActionBarLayout!!.fragmentsStack.size - 1] is DialogsActivity
								}
								else {
									actionBarLayout!!.fragmentsStack.size > 1 && actionBarLayout!!.fragmentsStack[actionBarLayout!!.fragmentsStack.size - 1] is DialogsActivity
								}

								actionBarLayout?.presentFragment(fragment, removeLast, true, true, false)

								if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible) {
									SecretMediaViewer.getInstance().closePhoto(false, false)
								}
								else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
									PhotoViewer.getInstance().closePhoto(false, true)
								}
								else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible) {
									ArticleViewer.getInstance().close(false, true)
								}

								GroupCallActivity.groupCallInstance?.dismiss()

								drawerLayoutContainer?.setAllowOpenDrawer(false, false)

								if (AndroidUtilities.isTablet()) {
									actionBarLayout?.showLastFragment()
									rightActionBarLayout?.showLastFragment()
								}
								else {
									drawerLayoutContainer?.setAllowOpenDrawer(true, false)
								}
							}
							else if (botChat != null || botChannel != null) {
								val user = if (res.users.isNotEmpty()) res.users[0] else null

								if (user == null || user.bot && user.bot_nochats) {
									try {
										if (mainFragmentsStack.isNotEmpty()) {
											BulletinFactory.of(mainFragmentsStack[mainFragmentsStack.size - 1]).createErrorBulletin(getString(R.string.BotCantJoinGroups)).show()
										}
									}
									catch (e: Exception) {
										FileLog.e(e)
									}

									return@Runnable
								}

								val args = Bundle()
								args.putBoolean("onlySelect", true)
								args.putInt("dialogsType", 2)
								args.putBoolean("resetDelegate", false)
								args.putBoolean("closeFragment", false)
								args.putBoolean("allowGroups", botChat != null)
								args.putBoolean("allowChannels", botChannel != null)

								val botHash = if (TextUtils.isEmpty(botChat)) (if (TextUtils.isEmpty(botChannel)) null else botChannel) else botChat

								val fragment = DialogsActivity(args)

								fragment.setDelegate { _, dids, _, _ ->
									val did = dids[0]
									val chat = MessagesController.getInstance(currentAccount).getChat(-did)

									if (chat != null && (chat.creator || chat.admin_rights != null && chat.admin_rights.add_admins)) {
										MessagesController.getInstance(intentAccount).checkIsInChat(chat, user) { isInChatAlready, currentRights, currentRank ->
											AndroidUtilities.runOnUIThread {
												var requestingRights: TL_chatAdminRights? = null

												if (botChatAdminParams != null) {
													val adminParams = botChatAdminParams.split("[+ ]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
													requestingRights = TL_chatAdminRights()

													for (adminParam in adminParams) {
														when (adminParam) {
															"change_info" -> requestingRights.change_info = true
															"post_messages" -> requestingRights.post_messages = true
															"edit_messages" -> requestingRights.edit_messages = true
															"add_admins", "promote_members" -> requestingRights.add_admins = true
															"delete_messages" -> requestingRights.delete_messages = true
															"ban_users", "restrict_members" -> requestingRights.ban_users = true
															"invite_users" -> requestingRights.invite_users = true
															"pin_messages" -> requestingRights.pin_messages = true
															"manage_video_chats", "manage_call" -> requestingRights.manage_call = true
															"manage_chat", "other" -> requestingRights.other = true
															"anonymous" -> requestingRights.anonymous = true
														}
													}
												}

												var editRights: TL_chatAdminRights? = null

												if (requestingRights != null || currentRights != null) {
													if (requestingRights == null) {
														editRights = currentRights
													}
													else if (currentRights == null) {
														editRights = requestingRights
													}
													else {
														editRights = currentRights
														editRights.change_info = requestingRights.change_info || editRights.change_info
														editRights.post_messages = requestingRights.post_messages || editRights.post_messages
														editRights.edit_messages = requestingRights.edit_messages || editRights.edit_messages
														editRights.add_admins = requestingRights.add_admins || editRights.add_admins
														editRights.delete_messages = requestingRights.delete_messages || editRights.delete_messages
														editRights.ban_users = requestingRights.ban_users || editRights.ban_users
														editRights.invite_users = requestingRights.invite_users || editRights.invite_users
														editRights.pin_messages = requestingRights.pin_messages || editRights.pin_messages
														editRights.manage_call = requestingRights.manage_call || editRights.manage_call
														editRights.anonymous = requestingRights.anonymous || editRights.anonymous
														editRights.other = requestingRights.other || editRights.other
													}
												}

												if (isInChatAlready && requestingRights == null && !botHash.isNullOrEmpty()) {
													val onFinish = Runnable onFinish@{
														NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats)

														val args1 = Bundle()
														args1.putBoolean("scrollToTopOnResume", true)
														args1.putLong("chat_id", chat.id)

														if (!MessagesController.getInstance(currentAccount).checkCanOpenChat(args1, fragment)) {
															return@onFinish
														}

														val chatActivity = ChatActivity(args1)

														presentFragment(chatActivity, removeLast = true, forceWithoutAnimation = false)
													}

													MessagesController.getInstance(currentAccount).addUserToChat(chat.id, user, 0, botHash, fragment, true, onFinish, null)
												}
												else {
													val editRightsActivity = ChatRightsEditActivity(user.id, -did, editRights, null, null, currentRank, ChatRightsEditActivity.TYPE_ADD_BOT, true, !isInChatAlready, botHash)

													editRightsActivity.setDelegate(object : ChatRightsEditActivityDelegate {
														override fun didSetRights(rights: Int, rightsAdmin: TL_chatAdminRights?, rightsBanned: TL_chatBannedRights?, rank: String?) {
															fragment.removeSelfFromStack()
															NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats)
														}

														override fun didChangeOwner(user: User) {
															// unused
														}
													})

													actionBarLayout?.presentFragment(editRightsActivity, false)
												}
											}
										}
									}
									else {
										val builder = AlertDialog.Builder(this)
										builder.setTitle(getString(R.string.AddBot))

										val chatName = chat?.title ?: ""

										builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("AddMembersAlertNamesText", R.string.AddMembersAlertNamesText, UserObject.getUserName(user), chatName)))
										builder.setNegativeButton(getString(R.string.Cancel), null)

										builder.setPositiveButton(getString(R.string.AddBot)) { _, _ ->
											val args12 = Bundle()
											args12.putBoolean("scrollToTopOnResume", true)
											args12.putLong("chat_id", -did)

											val chatActivity = ChatActivity(args12)

											NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats)
											MessagesController.getInstance(intentAccount).addUserToChat(-did, user, 0, botHash, chatActivity, null)

											actionBarLayout?.presentFragment(chatActivity, true, false, true, false)
										}

										builder.show()
									}
								}

								presentFragment(fragment)
							}
							else {
								val dialogId: Long
								var isBot = false
								val args = Bundle()

								dialogId = if (res.chats.isNotEmpty()) {
									args.putLong("chat_id", res.chats[0].id)
									-res.chats[0].id
								}
								else if (res.users.isNotEmpty()) {
									args.putLong("user_id", res.users[0].id)
									res.users[0].id
								}
								else {
									0
								}

								if (botUser != null && res.users.size > 0 && res.users[0].bot) {
									args.putString("botUser", botUser)
									isBot = true
								}

								if (navigateToPremiumBot) {
									navigateToPremiumBot = false
									args.putBoolean("premium_bot", true)
								}

								if (messageId != null) {
									args.putInt("message_id", messageId)
								}

								if (voicechat != null) {
									args.putString("voicechat", voicechat)
								}

								if (livestream != null) {
									args.putString("livestream", livestream)
								}

								if (videoTimestamp >= 0) {
									args.putInt("video_timestamp", videoTimestamp)
								}

								if (attachMenuBotToOpen != null) {
									args.putString("attach_bot", attachMenuBotToOpen)
								}

								if (setAsAttachBot != null) {
									args.putString("attach_bot_start_command", setAsAttachBot)
								}

								val lastFragment = if (mainFragmentsStack.isNotEmpty() && voicechat == null) mainFragmentsStack[mainFragmentsStack.size - 1] else null

								if (lastFragment == null || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, lastFragment)) {
									if (isBot && lastFragment is ChatActivity && lastFragment.dialogId == dialogId) {
										lastFragment.setBotUser(botUser)
									}
									else {
										MessagesController.getInstance(intentAccount).ensureMessagesLoaded(dialogId, messageId ?: 0, object : MessagesLoadedCallback {
											override fun onMessagesLoaded(fromCache: Boolean) {
												try {
													progressDialog.dismiss()
												}
												catch (e: Exception) {
													FileLog.e(e)
												}

												if (!this@LaunchActivity.isFinishing) {
													val voipLastFragment = if (livestream == null || lastFragment !is ChatActivity || lastFragment.dialogId != dialogId) {
														val fragment = ChatActivity(args)
														actionBarLayout?.presentFragment(fragment)
														fragment
													}
													else {
														lastFragment
													}

													AndroidUtilities.runOnUIThread({
														if (livestream != null) {
															val accountInstance = AccountInstance.getInstance(currentAccount)
															val cachedCall = accountInstance.messagesController.getGroupCall(-dialogId, false)

															if (cachedCall != null) {
																startCall(accountInstance.messagesController.getChat(-dialogId)!!, null, false, cachedCall.call?.rtmp_stream != true, this@LaunchActivity, voipLastFragment, accountInstance)
															}
															else {
																val chatFull = accountInstance.messagesController.getChatFull(-dialogId)

																if (chatFull != null) {
																	if (chatFull.call == null) {
																		if (voipLastFragment.parentActivity != null) {
																			BulletinFactory.of(voipLastFragment).createSimpleBulletin(R.raw.linkbroken, getString(R.string.InviteExpired)).show()
																		}
																	}
																	else {
																		accountInstance.messagesController.getGroupCall(-dialogId, true) {
																			AndroidUtilities.runOnUIThread {
																				val call = accountInstance.messagesController.getGroupCall(-dialogId, false)
																				startCall(accountInstance.messagesController.getChat(-dialogId)!!, null, false, call == null || !call.call!!.rtmp_stream, this@LaunchActivity, voipLastFragment, accountInstance)
																			}
																		}
																	}
																}
															}
														}
													}, 150)
												}
											}

											override fun onError() {
												if (!this@LaunchActivity.isFinishing) {
													val fragment = mainFragmentsStack[mainFragmentsStack.size - 1]
													AlertsCreator.showSimpleAlert(fragment, getString(R.string.JoinToGroupErrorNotExist))
												}

												try {
													progressDialog.dismiss()
												}
												catch (e: Exception) {
													FileLog.e(e)
												}
											}
										})

										hideProgressDialog = false
									}
								}
							}
						}
						else {
							try {
								if (mainFragmentsStack.isNotEmpty()) {
									val fragment = mainFragmentsStack[mainFragmentsStack.size - 1]

									if (error?.text != null && error.text.startsWith("FLOOD_WAIT")) {
										BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.FloodWait)).show()
									}
									else if (AndroidUtilities.isNumeric(username)) {
										BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.NoPhoneFound)).show()
									}
									else {
										BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.NoUsernameFound)).show()
									}
								}
							}
							catch (e: Exception) {
								FileLog.e(e)
							}
						}

						if (hideProgressDialog) {
							try {
								progressDialog.dismiss()
							}
							catch (e: Exception) {
								FileLog.e(e)
							}
						}
					}
				}, ConnectionsManager.RequestFlagFailOnServerErrors.toLong())
			}
		}
		else if (group != null) {
			if (state == 0) {
				val req = TL_messages_checkChatInvite()
				req.hash = group

				requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req, { response, error ->
					AndroidUtilities.runOnUIThread {
						if (!this@LaunchActivity.isFinishing) {
							var hideProgressDialog = true

							if (error == null && actionBarLayout != null) {
								val invite = response as ChatInvite?

								if (invite?.chat != null && (!isLeftFromChat(invite.chat) || !invite.chat.kicked && (!invite.chat?.username.isNullOrEmpty() || invite is TL_chatInvitePeek || invite.chat.has_geo))) {
									MessagesController.getInstance(intentAccount).putChat(invite.chat, false)

									val chats = ArrayList<Chat>()
									chats.add(invite.chat)

									MessagesStorage.getInstance(intentAccount).putUsersAndChats(null, chats, false, true)

									val args = Bundle()
									args.putLong("chat_id", invite.chat.id)

									if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, mainFragmentsStack[mainFragmentsStack.size - 1])) {
										val canceled = BooleanArray(1)

										progressDialog.setOnCancelListener {
											canceled[0] = true
										}

										MessagesController.getInstance(intentAccount).ensureMessagesLoaded(-invite.chat.id, 0, object : MessagesLoadedCallback {
											override fun onMessagesLoaded(fromCache: Boolean) {
												try {
													progressDialog.dismiss()
												}
												catch (e: Exception) {
													FileLog.e(e)
												}

												if (canceled[0]) {
													return
												}

												val fragment = ChatActivity(args)

												if (invite is TL_chatInvitePeek) {
													fragment.setChatInvite(invite)
												}

												actionBarLayout?.presentFragment(fragment)
											}

											override fun onError() {
												if (!this@LaunchActivity.isFinishing) {
													val fragment = mainFragmentsStack[mainFragmentsStack.size - 1]
													AlertsCreator.showSimpleAlert(fragment, getString(R.string.JoinToGroupErrorNotExist))
												}

												try {
													progressDialog.dismiss()
												}
												catch (e: Exception) {
													FileLog.e(e)
												}
											}
										})

										hideProgressDialog = false
									}
								}
								else {
									val inviteUser = invite?.user

									if (inviteUser != null) {
										MessagesController.getInstance(intentAccount).putUser(inviteUser, false)

										val args = Bundle()
										args.putLong("user_id", inviteUser.id)

										val chatActivity = ChatActivity(args)

										presentFragment(chatActivity)
									}
									else {
										val fragment = mainFragmentsStack[mainFragmentsStack.size - 1]
										fragment.showDialog(JoinGroupAlert(this@LaunchActivity, invite, group, fragment, null))
									}
								}
							}
							else {
								val builder = AlertDialog.Builder(this@LaunchActivity)
								builder.setTitle(getString(R.string.AppName))

								if (error!!.text.startsWith("FLOOD_WAIT")) {
									builder.setMessage(getString(R.string.FloodWait))
								}
								else if (error.text.startsWith("INVITE_HASH_EXPIRED")) {
									builder.setTitle(getString(R.string.ExpiredLink))
									builder.setMessage(getString(R.string.InviteExpired))
								}
								else {
									builder.setMessage(getString(R.string.JoinToGroupErrorNotExist))
								}

								builder.setPositiveButton(getString(R.string.OK), null)

								showAlertDialog(builder)
							}

							try {
								if (hideProgressDialog) {
									progressDialog.dismiss()
								}
							}
							catch (e: Exception) {
								FileLog.e(e)
							}
						}
					}
				}, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
			else if (state == 1) {
				val req = TL_messages_importChatInvite()
				req.hash = group

				ConnectionsManager.getInstance(intentAccount).sendRequest(req, { response, error ->
					if (error == null) {
						val updates = response as Updates?
						MessagesController.getInstance(intentAccount).processUpdates(updates, false)
					}

					AndroidUtilities.runOnUIThread {
						if (!this@LaunchActivity.isFinishing) {
							try {
								progressDialog.dismiss()
							}
							catch (e: Exception) {
								FileLog.e(e)
							}

							if (error == null) {
								if (actionBarLayout != null) {
									val updates = response as? Updates

									updates?.chats?.firstOrNull()?.let { chat ->
										chat.left = false
										chat.kicked = false

										MessagesController.getInstance(intentAccount).putUsers(updates.users, false)
										MessagesController.getInstance(intentAccount).putChats(updates.chats, false)

										val args = Bundle()
										args.putLong("chat_id", chat.id)

										if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, mainFragmentsStack[mainFragmentsStack.size - 1])) {
											val fragment = ChatActivity(args)
											NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats)
											actionBarLayout?.presentFragment(fragment, false, true, true, false)
										}
									}
								}
							}
							else {
								val builder = AlertDialog.Builder(this@LaunchActivity)
								builder.setTitle(getString(R.string.AppName))

								if (error.text.startsWith("FLOOD_WAIT")) {
									builder.setMessage(getString(R.string.FloodWait))
								}
								else if (error.text == "USERS_TOO_MUCH") {
									builder.setMessage(getString(R.string.JoinToGroupErrorFull))
								}
								else {
									builder.setMessage(getString(R.string.JoinToGroupErrorNotExist))
								}

								builder.setPositiveButton(getString(R.string.OK), null)

								showAlertDialog(builder)
							}
						}
					}
				}, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
		}
		else if (sticker != null) {
			if (mainFragmentsStack.isNotEmpty()) {
				val stickerset = TL_inputStickerSetShortName()
				stickerset.short_name = sticker

				val fragment = mainFragmentsStack[mainFragmentsStack.size - 1]
				val alert: StickersAlert

				if (fragment is ChatActivity) {
					alert = StickersAlert(this@LaunchActivity, fragment, stickerset, null, fragment.chatActivityEnterViewForStickers)
					alert.setCalcMandatoryInsets(fragment.isKeyboardVisible)
				}
				else {
					alert = StickersAlert(this@LaunchActivity, fragment, stickerset, null, null)
				}

				alert.probablyEmojis = emoji != null

				fragment.showDialog(alert)
			}

			return
		}
		else if (emoji != null) {
			if (mainFragmentsStack.isNotEmpty()) {
				val stickerset = TL_inputStickerSetShortName()

				stickerset.short_name = sticker ?: emoji

				val sets = ArrayList<InputStickerSet>(1)
				sets.add(stickerset)

				val fragment = mainFragmentsStack[mainFragmentsStack.size - 1]
				val alert: EmojiPacksAlert

				if (fragment is ChatActivity) {
					alert = EmojiPacksAlert(fragment, this@LaunchActivity, sets)
					alert.setCalcMandatoryInsets(fragment.isKeyboardVisible)
				}
				else {
					alert = EmojiPacksAlert(fragment, this@LaunchActivity, sets)
				}

				fragment.showDialog(alert)
			}

			return
		}
		else if (message != null) {
			val args = Bundle()
			args.putBoolean("onlySelect", true)
			args.putInt("dialogsType", 3)

			val fragment = DialogsActivity(args)

			fragment.setDelegate { fragment13, dids, _, _ ->
				val did = dids[0]

				val args13 = Bundle()
				args13.putBoolean("scrollToTopOnResume", true)
				args13.putBoolean("hasUrl", hasUrl)

				if (DialogObject.isEncryptedDialog(did)) {
					args13.putInt("enc_id", DialogObject.getEncryptedChatId(did))
				}
				else if (DialogObject.isUserDialog(did)) {
					args13.putLong("user_id", did)
				}
				else {
					args13.putLong("chat_id", -did)
				}

				if (MessagesController.getInstance(intentAccount).checkCanOpenChat(args13, fragment13)) {
					NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats)
					MediaDataController.getInstance(intentAccount).saveDraft(did, 0, message, null, null, false)
					actionBarLayout!!.presentFragment(ChatActivity(args13), true, false, true, false)
				}
			}

			presentFragment(fragment, removeLast = false, forceWithoutAnimation = true)
		}
		else if (auth != null) {
			val bot_id = Utilities.parseInt(auth["bot_id"])

			if (bot_id == 0) {
				return
			}

			val payload = auth["payload"]
			val nonce = auth["nonce"]
			val callbackUrl = auth["callback_url"]

			val req = TL_account_getAuthorizationForm()
			req.bot_id = bot_id.toLong()
			req.scope = auth["scope"]
			req.public_key = auth["public_key"]

			requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req) { response, error ->
				val authorizationForm = response as TL_account_authorizationForm?

				if (authorizationForm != null) {
					val req2 = TL_account_getPassword()

					requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req2) { response1, _ ->
						AndroidUtilities.runOnUIThread {
							try {
								progressDialog.dismiss()
							}
							catch (e: Exception) {
								FileLog.e(e)
							}

							if (response1 != null) {
								val accountPassword = response1 as account_Password
								MessagesController.getInstance(intentAccount).putUsers(authorizationForm.users, false)
								presentFragment(PassportActivity(PassportActivity.TYPE_PASSWORD, req.bot_id, req.scope, req.public_key, payload, nonce, callbackUrl, authorizationForm, accountPassword))
							}
						}
					}
				}
				else {
					AndroidUtilities.runOnUIThread {
						try {
							progressDialog.dismiss()

							if ("APP_VERSION_OUTDATED" == error?.text) {
								AlertsCreator.showUpdateAppAlert(this@LaunchActivity, getString(R.string.UpdateAppAlert), true)
							}
							else {
								AlertsCreator.createSimpleAlert(this@LaunchActivity, "${getString(R.string.ErrorOccurred)}\n${error?.text}")?.let { dialog ->
									showAlertDialog(dialog)
								}
							}
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}
			}
		}
		else if (unsupportedUrl != null) {
			val req = TL_help_getDeepLinkInfo()
			req.path = unsupportedUrl

			requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					try {
						progressDialog.dismiss()
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					if (response is TL_help_deepLinkInfo) {
						AlertsCreator.showUpdateAppAlert(this@LaunchActivity, response.message, response.update_app)
					}
				}
			}
		}
		else if (lang != null) {
			val req = TL_langpack_getLanguage()
			req.lang_code = lang
			req.lang_pack = "android"

			requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					try {
						progressDialog.dismiss()
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					if (response is TL_langPackLanguage) {
						showAlertDialog(AlertsCreator.createLanguageAlert(this@LaunchActivity, response))
					}
					else if (error != null) {
						if ("LANG_CODE_NOT_SUPPORTED" == error.text) {
							showAlertDialog(AlertsCreator.createSimpleAlert(this@LaunchActivity, getString(R.string.LanguageUnsupportedError)))
						}
						else {
							showAlertDialog(AlertsCreator.createSimpleAlert(this@LaunchActivity, "${getString(R.string.ErrorOccurred)}\n${error.text}"))
						}
					}
				}
			}
		}
		else if (wallPaper != null) {
			var ok = false

			if (wallPaper.slug.isNullOrEmpty()) {
				try {
					val colorWallpaper = if (wallPaper.settings.third_background_color != 0) {
						ColorWallpaper(Theme.COLOR_BACKGROUND_SLUG, wallPaper.settings.background_color, wallPaper.settings.second_background_color, wallPaper.settings.third_background_color, wallPaper.settings.fourth_background_color)
					}
					else {
						ColorWallpaper(Theme.COLOR_BACKGROUND_SLUG, wallPaper.settings.background_color, wallPaper.settings.second_background_color, AndroidUtilities.getWallpaperRotation(wallPaper.settings.rotation, false))
					}

					val wallpaperActivity = ThemePreviewActivity(colorWallpaper, null, true, false)

					AndroidUtilities.runOnUIThread {
						presentFragment(wallpaperActivity)
					}

					ok = true
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			if (!ok) {
				val req = TL_account_getWallPaper()

				val inputWallPaperSlug = TL_inputWallPaperSlug()
				inputWallPaperSlug.slug = wallPaper.slug

				req.wallpaper = inputWallPaperSlug

				requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
					AndroidUtilities.runOnUIThread {
						try {
							progressDialog.dismiss()
						}
						catch (e: Exception) {
							FileLog.e(e)
						}

						if (response is TL_wallPaper) {
							val `object`: Any = if (response.pattern) {
								val colorWallpaper = ColorWallpaper(response.slug, wallPaper.settings.background_color, wallPaper.settings.second_background_color, wallPaper.settings.third_background_color, wallPaper.settings.fourth_background_color, AndroidUtilities.getWallpaperRotation(wallPaper.settings.rotation, false), wallPaper.settings.intensity / 100.0f, wallPaper.settings.motion, null)
								colorWallpaper.pattern = response
								colorWallpaper
							}
							else {
								response
							}

							val wallpaperActivity = ThemePreviewActivity(`object`, null, true, false)
							wallpaperActivity.setInitialModes(wallPaper.settings.blur, wallPaper.settings.motion)

							presentFragment(wallpaperActivity)
						}
						else {
							showAlertDialog(AlertsCreator.createSimpleAlert(this@LaunchActivity, "${getString(R.string.ErrorOccurred)}\n${error?.text}"))
						}
					}
				}
			}
		}
		else if (channelId != null && messageId != null) {
			if (threadId != null) {
				val chat = MessagesController.getInstance(intentAccount).getChat(channelId)

				if (chat != null) {
					requestId[0] = runCommentRequest(intentAccount, progressDialog, messageId, commentId, threadId, chat)
				}
				else {
					val req = TL_channels_getChannels()

					val inputChannel = TL_inputChannel()
					inputChannel.channel_id = channelId

					req.id.add(inputChannel)

					requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
						AndroidUtilities.runOnUIThread {
							var notFound = true

							if (response is TL_messages_chats) {
								if (response.chats.isNotEmpty()) {
									notFound = false
									MessagesController.getInstance(currentAccount).putChats(response.chats, false)
									requestId[0] = runCommentRequest(intentAccount, progressDialog, messageId, commentId, threadId, response.chats[0])
								}
							}

							if (notFound) {
								try {
									progressDialog.dismiss()
								}
								catch (e: Exception) {
									FileLog.e(e)
								}

								showAlertDialog(AlertsCreator.createSimpleAlert(this@LaunchActivity, getString(R.string.LinkNotFound)))
							}
						}
					}
				}
			}
			else {
				val args = Bundle()
				args.putLong("chat_id", channelId)
				args.putInt("message_id", messageId)

				val lastFragment = if (mainFragmentsStack.isNotEmpty()) mainFragmentsStack[mainFragmentsStack.size - 1] else null

				if (lastFragment == null || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, lastFragment)) {
					AndroidUtilities.runOnUIThread {
						if (!actionBarLayout!!.presentFragment(ChatActivity(args))) {
							val req = TL_channels_getChannels()

							val inputChannel = TL_inputChannel()
							inputChannel.channel_id = channelId

							req.id.add(inputChannel)

							requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
								AndroidUtilities.runOnUIThread {
									try {
										progressDialog.dismiss()
									}
									catch (e: Exception) {
										FileLog.e(e)
									}

									var notFound = true

									if (response is TL_messages_chats) {
										if (response.chats.isNotEmpty()) {
											notFound = false

											MessagesController.getInstance(currentAccount).putChats(response.chats, false)

											if (lastFragment == null || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, lastFragment)) {
												actionBarLayout?.presentFragment(ChatActivity(args))
											}
										}
									}

									if (notFound) {
										showAlertDialog(AlertsCreator.createSimpleAlert(this@LaunchActivity, getString(R.string.LinkNotFound)))
									}
								}
							}
						}
					}
				}
			}
		}

		if (requestId[0] != 0) {
			progressDialog.setOnCancelListener {
				ConnectionsManager.getInstance(intentAccount).cancelRequest(requestId[0], true)
			}

			runCatching {
				progressDialog.showDelayed(300)
			}
		}
	}

	private fun findContacts(userName: String?): List<TL_contact> {
		val messagesController = MessagesController.getInstance(currentAccount)
		val contactsController = ContactsController.getInstance(currentAccount)
		val contacts: List<TL_contact> = ArrayList(contactsController.contacts)
		val foundContacts: MutableList<TL_contact> = ArrayList()

		if (userName != null) {
			val query1 = userName.trim().lowercase()

			if (query1.isNotEmpty()) {
				var query2 = LocaleController.getInstance().getTranslitString(query1)

				if (query1 == query2 || query2.isNullOrEmpty()) {
					query2 = null
				}

				val queries = arrayOf<String?>(query1, query2)

				for (contact in contacts) {
					val user = messagesController.getUser(contact.user_id)

					if (user != null) {
						if (user.self) {
							continue
						}

						val names = arrayOfNulls<String>(3)
						names[0] = ContactsController.formatName(user.first_name, user.last_name).lowercase(Locale.getDefault())
						names[1] = LocaleController.getInstance().getTranslitString(names[0])

						if (names[0] == names[1]) {
							names[1] = null
						}

						if (UserObject.isReplyUser(user)) {
							names[2] = getString(R.string.RepliesTitle).lowercase()
						}
						else if (user.self) {
							names[2] = getString(R.string.SavedMessages).lowercase()
						}

						var found = false

						for (q in queries) {
							if (q == null) {
								continue
							}

							for (name in names) {
								if (name != null && (name.startsWith(q) || name.contains(" $q"))) {
									found = true
									break
								}
							}

							if (!found && user.username?.startsWith(q) == true) {
								found = true
							}

							if (found) {
								foundContacts.add(contact)
								break
							}
						}
					}
				}
			}
		}

		return foundContacts
	}

	fun showAlertDialog(builder: AlertDialog.Builder?): AlertDialog? {
		if (builder == null) {
			return null
		}

		try {
			visibleDialog?.dismiss()
			visibleDialog = null
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		try {
			visibleDialog = builder.show()
			visibleDialog?.setCanceledOnTouchOutside(true)

			visibleDialog?.setOnDismissListener {
				if (visibleDialog != null) {
					if (visibleDialog === localeDialog) {
						try {
							Toast.makeText(this@LaunchActivity, R.string.ChangeLanguageLater, Toast.LENGTH_LONG).show()
						}
						catch (e: Exception) {
							FileLog.e(e)
						}

						localeDialog = null
					}
					else if (visibleDialog === proxyErrorDialog) {
						val editor = MessagesController.getGlobalMainSettings().edit()
						editor.putBoolean("proxy_enabled", false)
						editor.putBoolean("proxy_enabled_calls", false)
						editor.commit()

						ConnectionsManager.setProxySettings(false, "", 1080, "", "", "")

						NotificationCenter.globalInstance.postNotificationName(NotificationCenter.proxySettingsChanged)

						proxyErrorDialog = null
					}
				}

				visibleDialog = null
			}

			return visibleDialog
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		return null
	}

	fun showBulletin(createBulletin: Function<BulletinFactory, Bulletin>) {
		var topFragment: BaseFragment? = null

		if (layerFragmentsStack.isNotEmpty()) {
			topFragment = layerFragmentsStack[layerFragmentsStack.size - 1]
		}
		else if (rightFragmentsStack.isNotEmpty()) {
			topFragment = rightFragmentsStack[rightFragmentsStack.size - 1]
		}
		else if (mainFragmentsStack.isNotEmpty()) {
			topFragment = mainFragmentsStack[mainFragmentsStack.size - 1]
		}

		if (BulletinFactory.canShowBulletin(topFragment)) {
			createBulletin.apply(BulletinFactory.of(topFragment)).show()
		}
	}

	fun setNavigateToPremiumBot(`val`: Boolean) {
		navigateToPremiumBot = `val`
	}

	fun setNavigateToPremiumGiftCallback(`val`: Runnable?) {
		navigateToPremiumGiftCallback = `val`
	}

	public override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleIntent(intent, isNew = true, restore = false, fromPassword = false)
	}

	override fun didSelectDialogs(dialogsFragment: DialogsActivity?, dids: List<Long>, message: CharSequence?, param: Boolean) {
		val account = dialogsFragment?.currentAccount ?: currentAccount
		val uri = exportingChatUri

		if (uri != null) {
			val documentsUris = documentsUrisArray?.let { ArrayList(it) }
			val progressDialog = AlertDialog(this, 3)

			SendMessagesHelper.getInstance(account).prepareImportHistory(dids[0], exportingChatUri, documentsUrisArray?.toList()?.let { ArrayList(it) }) { result ->
				if (result != 0L) {
					val args = Bundle()
					args.putBoolean("scrollToTopOnResume", true)

					if (DialogObject.isUserDialog(result)) {
						args.putLong("user_id", result)
					}
					else {
						args.putLong("chat_id", -result)
					}

					val fragment = ChatActivity(args)

					fragment.setOpenImport()

					actionBarLayout?.presentFragment(fragment, dialogsFragment != null || param, dialogsFragment == null, true, false)
				}
				else {
					documentsUrisArray = documentsUris

					if (documentsUrisArray == null) {
						documentsUrisArray = ArrayList()
					}

					documentsUrisArray?.add(0, uri)

					openDialogsToSend(true)
				}

				try {
					progressDialog.dismiss()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			try {
				progressDialog.showDelayed(300)
			}
			catch (e: Exception) {
				// ignored
			}
		}
		else {
			val notify = dialogsFragment == null || dialogsFragment.notify

			val fragment = if (dids.size <= 1) {
				val did = dids[0]

				val args = Bundle()
				args.putBoolean("scrollToTopOnResume", true)

				if (DialogObject.isEncryptedDialog(did)) {
					args.putInt("enc_id", DialogObject.getEncryptedChatId(did))
				}
				else if (DialogObject.isUserDialog(did)) {
					args.putLong("user_id", did)
				}
				else {
					args.putLong("chat_id", -did)
				}

				if (!MessagesController.getInstance(account).checkCanOpenChat(args, dialogsFragment)) {
					return
				}

				ChatActivity(args)
			}
			else {
				null
			}

			var attachesCount = 0

			attachesCount += contactsToSend?.size ?: 0

			if (videoPath != null) {
				attachesCount++
			}

			attachesCount += photoPathsArray?.size ?: 0

			attachesCount += documentsPathsArray?.size ?: 0

			attachesCount += documentsUrisArray?.size ?: 0

			if (videoPath == null && photoPathsArray == null && documentsPathsArray == null && documentsUrisArray == null && sendingText != null) {
				attachesCount++
			}

			for (i in dids.indices) {
				val did = dids[i]

				if (AlertsCreator.checkSlowMode(this, currentAccount, did, attachesCount > 1)) {
					return
				}
			}

			if (contactsToSend?.size == 1 && mainFragmentsStack.isNotEmpty()) {
				val alert = PhonebookShareAlert(mainFragmentsStack[mainFragmentsStack.size - 1], null, null, contactsToSendUri, null, null, null)

				alert.setDelegate { user, notify2, scheduleDate ->
					if (fragment != null) {
						actionBarLayout!!.presentFragment(fragment, true, false, true, false)
					}

					val accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount)

					for (i in dids.indices) {
						val did = dids[i]
						SendMessagesHelper.getInstance(account).sendMessage(user, did, null, null, null, null, notify2, scheduleDate, false, null)

						if (!message.isNullOrEmpty()) {
							SendMessagesHelper.prepareSendingText(accountInstance, message.toString(), did, notify, 0)
						}
					}
				}

				mainFragmentsStack[mainFragmentsStack.size - 1].showDialog(alert)
			}
			else {
				var captionToSend: String? = null

				for (i in dids.indices) {
					val did = dids[i]
					val accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount)
					var photosEditorOpened = false
					var videoEditorOpened = false

					if (fragment != null) {
						val withoutAnimation = dialogsFragment == null || videoPath != null || photoPathsArray != null && photoPathsArray!!.size > 0

						actionBarLayout?.presentFragment(fragment, dialogsFragment != null, withoutAnimation, true, false)

						if (videoPath != null) {
							fragment.openVideoEditor(videoPath, sendingText)
							videoEditorOpened = true
							sendingText = null
						}
						else if (photoPathsArray != null && photoPathsArray!!.size > 0) {
							photosEditorOpened = fragment.openPhotosEditor(photoPathsArray!!, if (message.isNullOrEmpty()) sendingText else message)

							if (photosEditorOpened) {
								sendingText = null
							}
						}
					}
					else {
						if (videoPath != null) {
							if (sendingText != null && sendingText!!.length <= 1024) {
								captionToSend = sendingText
								sendingText = null
							}

							val arrayList = listOf(videoPath!!)

							SendMessagesHelper.prepareSendingDocuments(accountInstance, arrayList, arrayList, null, captionToSend, null, did, null, null, null, null, notify, 0, false, null)
						}
					}
					if (photoPathsArray != null && !photosEditorOpened) {
						if (sendingText != null && sendingText!!.length <= 1024 && photoPathsArray?.size == 1) {
							photoPathsArray!![0].caption = sendingText
							sendingText = null
						}

						SendMessagesHelper.prepareSendingMedia(accountInstance, photoPathsArray!!, did, null, null, null, forceDocument = false, groupMedia = false, editingMessageObject = null, notify = notify, scheduleDate = 0, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)
					}

					if (documentsPathsArray != null || documentsUrisArray != null) {
						if (sendingText != null && sendingText!!.length <= 1024 && (documentsPathsArray?.size ?: 0) + (documentsUrisArray?.size ?: 0) == 1) {
							captionToSend = sendingText
							sendingText = null
						}

						SendMessagesHelper.prepareSendingDocuments(accountInstance, documentsPathsArray, documentsOriginalPathsArray, documentsUrisArray?.toList()?.let { ArrayList(it) }, captionToSend, documentsMimeType, did, null, null, null, null, notify, 0, false, null)
					}

					if (sendingText != null) {
						SendMessagesHelper.prepareSendingText(accountInstance, sendingText!!, did, true, 0)
					}

					if (!contactsToSend.isNullOrEmpty()) {
						for (a in contactsToSend!!.indices) {
							val user = contactsToSend!![a]
							SendMessagesHelper.getInstance(account).sendMessage(user, did, null, null, null, null, notify, 0, false, null)
						}
					}

					if (!message.isNullOrEmpty() && !videoEditorOpened && !photosEditorOpened) {
						SendMessagesHelper.prepareSendingText(accountInstance, message.toString(), did, notify, 0)
					}
				}
			}

			if (dialogsFragment != null && fragment == null) {
				dialogsFragment.finishFragment()
			}
		}

		photoPathsArray = null
		videoPath = null
		sendingText = null
		documentsPathsArray = null
		documentsOriginalPathsArray = null
		contactsToSend = null
		contactsToSendUri = null
		exportingChatUri = null
	}

	private fun onFinish() {
		if (lockRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(lockRunnable)
			lockRunnable = null
		}

		if (finished) {
			return
		}

		finished = true

		if (currentAccount != -1) {
			NotificationCenter.getInstance(currentAccount).let {
				it.removeObserver(this, NotificationCenter.appDidLogout)
				it.removeObserver(this, NotificationCenter.didUpdateConnectionState)
				it.removeObserver(this, NotificationCenter.needShowAlert)
				it.removeObserver(this, NotificationCenter.wasUnableToFindCurrentLocation)
				it.removeObserver(this, NotificationCenter.openArticle)
				it.removeObserver(this, NotificationCenter.needShowPlayServicesAlert)
				it.removeObserver(this, NotificationCenter.historyImportProgressChanged)
				it.removeObserver(this, NotificationCenter.groupCallUpdated)
				it.removeObserver(this, NotificationCenter.stickersImportComplete)
				it.removeObserver(this, NotificationCenter.currentUserShowLimitReachedDialog)
				it.removeObserver(this, NotificationCenter.updateUnreadBadge)
			}
		}

		NotificationCenter.globalInstance.let {
			it.removeObserver(this, NotificationCenter.needShowAlert)
			it.removeObserver(this, NotificationCenter.reloadInterface)
			it.removeObserver(this, NotificationCenter.didSetNewTheme)
			it.removeObserver(this, NotificationCenter.needCheckSystemBarColors)
			it.removeObserver(this, NotificationCenter.closeOtherAppActivities)
			it.removeObserver(this, NotificationCenter.didSetPasscode)
			it.removeObserver(this, NotificationCenter.notificationsCountUpdated)
			it.removeObserver(this, NotificationCenter.screenStateChanged)
			it.removeObserver(this, NotificationCenter.showBulletin)
			it.removeObserver(this, NotificationCenter.requestPermissions)
		}
	}

	fun presentFragment(fragment: BaseFragment?) {
		actionBarLayout?.presentFragment(fragment)
	}

	fun presentFragment(fragment: BaseFragment?, removeLast: Boolean, forceWithoutAnimation: Boolean): Boolean {
		return actionBarLayout?.presentFragment(fragment, removeLast, forceWithoutAnimation, true, false) == true
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (SharedConfig.passcodeHash.isNotEmpty() && SharedConfig.lastPauseTime != 0) {
			SharedConfig.lastPauseTime = 0
			FileLog.d("reset lastPauseTime onActivityResult")
			UserConfig.getInstance(currentAccount).saveConfig(false)
		}

		if (requestCode == 105) {
			if (Settings.canDrawOverlays(this).also { ApplicationLoader.canDrawOverlays = it }) {
				GroupCallActivity.groupCallInstance?.dismissInternal()

				AndroidUtilities.runOnUIThread({
					GroupCallPip.clearForce()
					GroupCallPip.updateVisibility(this@LaunchActivity)
				}, 200)
			}
			return
		}

		super.onActivityResult(requestCode, resultCode, data)

		if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
			if (resultCode == RESULT_OK) {
				val service = VoIPService.sharedInstance

				if (service != null) {
					VideoCapturerDevice.mediaProjectionPermissionResultData = data
					service.createCaptureDevice(true)
				}
			}
		}
		else if (requestCode == PLAY_SERVICES_REQUEST_CHECK_SETTINGS) {
			LocationController.getInstance(currentAccount).startFusedLocationRequest(resultCode == RESULT_OK)
		}
		else {
			val editorView = ThemeEditorView.getInstance()
			editorView?.onActivityResult(requestCode, resultCode, data)

			if (actionBarLayout?.fragmentsStack?.size != 0) {
				val fragment = actionBarLayout!!.fragmentsStack[actionBarLayout!!.fragmentsStack.size - 1]
				fragment?.onActivityResultFragment(requestCode, resultCode, data)
			}

			if (AndroidUtilities.isTablet()) {
				if (rightActionBarLayout?.fragmentsStack?.size != 0) {
					val fragment = rightActionBarLayout!!.fragmentsStack[rightActionBarLayout!!.fragmentsStack.size - 1]
					fragment?.onActivityResultFragment(requestCode, resultCode, data)
				}

				if (layersActionBarLayout?.fragmentsStack?.size != 0) {
					val fragment = layersActionBarLayout!!.fragmentsStack[layersActionBarLayout!!.fragmentsStack.size - 1]
					fragment?.onActivityResultFragment(requestCode, resultCode, data)
				}
			}

			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.onActivityResultReceived, requestCode, resultCode, data)
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)

		if (!checkPermissionsResult(requestCode, permissions, grantResults)) {
			return
		}

		if (actionBarLayout!!.fragmentsStack.size != 0) {
			val fragment = actionBarLayout!!.fragmentsStack[actionBarLayout!!.fragmentsStack.size - 1]
			fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults)
		}

		if (AndroidUtilities.isTablet()) {
			if (rightActionBarLayout!!.fragmentsStack.size != 0) {
				val fragment = rightActionBarLayout!!.fragmentsStack[rightActionBarLayout!!.fragmentsStack.size - 1]
				fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults)
			}

			if (layersActionBarLayout!!.fragmentsStack.size != 0) {
				val fragment = layersActionBarLayout!!.fragmentsStack[layersActionBarLayout!!.fragmentsStack.size - 1]
				fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults)
			}
		}

		VoIPFragment.onRequestPermissionsResult(requestCode, grantResults)

		NotificationCenter.globalInstance.postNotificationName(NotificationCenter.onRequestPermissionResultReceived, requestCode, permissions, grantResults)

		if (requestedPermissions[requestCode, -1] >= 0) {
			val type = requestedPermissions[requestCode, -1]

			requestedPermissions.delete(requestCode)

			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.permissionsGranted, type)
		}
	}

	override fun onPause() {
		super.onPause()

		isResumed = false

		NotificationCenter.globalInstance.postNotificationName(NotificationCenter.stopAllHeavyOperations, 4096)
		ApplicationLoader.mainInterfacePaused = true

		val account = currentAccount

		Utilities.stageQueue.postRunnable {
			ApplicationLoader.mainInterfacePausedStageQueue = true
			ApplicationLoader.mainInterfacePausedStageQueueTime = 0

			if (VoIPService.sharedInstance == null) {
				MessagesController.getInstance(account).ignoreSetOnline = false
			}
		}

		onPasscodePause()

		actionBarLayout?.onPause()

		if (AndroidUtilities.isTablet()) {
			rightActionBarLayout?.onPause()
			layersActionBarLayout?.onPause()
		}

		passcodeView?.onPause()

		ConnectionsManager.getInstance(currentAccount).setAppPaused(value = true, byScreenState = false)

		if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
			PhotoViewer.getInstance().onPause()
		}

		if (VoIPFragment.instance != null) {
			VoIPFragment.onPause()
		}
	}

	override fun onStart() {
		super.onStart()

		Browser.bindCustomTabsService(this)
		ApplicationLoader.mainInterfaceStopped = false
		GroupCallPip.updateVisibility(this)
		GroupCallActivity.groupCallInstance?.onResume()
	}

	override fun onStop() {
		super.onStop()
		Browser.unbindCustomTabsService(this)
		ApplicationLoader.mainInterfaceStopped = true
		GroupCallPip.updateVisibility(this)
		GroupCallActivity.groupCallInstance?.onPause()
	}

	override fun onDestroy() {
		PhotoViewer.getPipInstance()?.destroyPhotoViewer()

		if (PhotoViewer.hasInstance()) {
			PhotoViewer.getInstance().destroyPhotoViewer()
		}

		if (SecretMediaViewer.hasInstance()) {
			SecretMediaViewer.getInstance()?.destroyPhotoViewer()
		}

		if (ArticleViewer.hasInstance()) {
			ArticleViewer.getInstance()?.destroyArticleViewer()
		}

		if (ContentPreviewViewer.hasInstance()) {
			ContentPreviewViewer.getInstance().destroy()
		}


		GroupCallActivity.groupCallInstance?.dismissInternal()

		val pipRoundVideoView = PipRoundVideoView.getInstance()

		MediaController.getInstance().setBaseActivity(this, false)
		MediaController.getInstance().setFeedbackView(actionBarLayout, false)

		pipRoundVideoView?.close(false)

		Theme.destroyResources()

		val embedBottomSheet = EmbedBottomSheet.getInstance()
		embedBottomSheet?.destroy()

		val editorView = ThemeEditorView.getInstance()
		editorView?.destroy()

		try {
			visibleDialog?.dismiss()
			visibleDialog = null
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		try {
			onGlobalLayoutListener?.let {
				val view = window.decorView.rootView
				view.viewTreeObserver.removeOnGlobalLayoutListener(it)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		super.onDestroy()

		onFinish()
	}

	override fun onUserLeaveHint() {
		for (callback in onUserLeaveHintListeners) {
			callback.run()
		}

		actionBarLayout?.onUserLeaveHint()
	}

	override fun onResume() {
		super.onResume()

		isResumed = true

		onResumeStaticCallback?.run()
		onResumeStaticCallback = null

		checkWasMutedByAdmin(true)

		NotificationCenter.globalInstance.postNotificationName(NotificationCenter.startAllHeavyOperations, 4096)

		MediaController.getInstance().setFeedbackView(actionBarLayout, true)

		ApplicationLoader.mainInterfacePaused = false

		Utilities.stageQueue.postRunnable {
			ApplicationLoader.mainInterfacePausedStageQueue = false
			ApplicationLoader.mainInterfacePausedStageQueueTime = System.currentTimeMillis()
		}

		checkFreeDiscSpace()

		MediaController.checkGallery()

		onPasscodeResume()

		if (passcodeView?.visibility != View.VISIBLE) {
			actionBarLayout?.onResume()

			if (AndroidUtilities.isTablet()) {
				rightActionBarLayout?.onResume()
				layersActionBarLayout?.onResume()
			}
		}
		else {
			actionBarLayout?.dismissDialogs()

			if (AndroidUtilities.isTablet()) {
				rightActionBarLayout?.dismissDialogs()
				layersActionBarLayout?.dismissDialogs()
			}

			passcodeView?.onResume()
		}

		ConnectionsManager.getInstance(currentAccount).setAppPaused(value = false, byScreenState = false)

		updateCurrentConnectionState()

		if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
			PhotoViewer.getInstance().onResume()
		}

		val pipRoundVideoView = PipRoundVideoView.getInstance()

		if (pipRoundVideoView != null && MediaController.getInstance().isMessagePaused) {
			val messageObject = MediaController.getInstance().playingMessageObject

			if (messageObject != null) {
				MediaController.getInstance().seekToProgress(messageObject, messageObject.audioProgress)
			}
		}

		if (UserConfig.getInstance(UserConfig.selectedAccount).unacceptedTermsOfService != null) {
			showTosActivity(UserConfig.selectedAccount, UserConfig.getInstance(UserConfig.selectedAccount).unacceptedTermsOfService)
		}

		ApplicationLoader.canDrawOverlays = Settings.canDrawOverlays(this)

		if (VoIPFragment.instance != null) {
			VoIPFragment.onResume()
		}

		invalidateTabletMode()

		WalletHelper.getInstance(currentAccount).loadWallet()
	}

	private fun invalidateTabletMode() {
		val wasTablet = AndroidUtilities.getWasTablet() ?: return

		AndroidUtilities.resetWasTabletFlag()

		if (wasTablet != AndroidUtilities.isTablet()) {
			var dialogId: Long = 0

			if (wasTablet) {
				mainFragmentsStack.addAll(rightFragmentsStack)
				mainFragmentsStack.addAll(layerFragmentsStack)
				rightFragmentsStack.clear()
				layerFragmentsStack.clear()
			}
			else if (rightFragmentsStack.isEmpty()) {
				val fragments = ArrayList(mainFragmentsStack)

				mainFragmentsStack.clear()
				rightFragmentsStack.clear()
				layerFragmentsStack.clear()

				for (fragment in fragments) {
					if (fragment is DialogsActivity && fragment.isMainDialogList) {
						mainFragmentsStack.add(fragment)
					}
					else if (fragment is ChatActivity && !fragment.isInScheduleMode) {
						rightFragmentsStack.add(fragment)

						if (dialogId == 0L) {
							dialogId = fragment.dialogId
						}
					}
					else {
						layerFragmentsStack.add(fragment)
					}
				}
			}
			else {
				for (fragment in rightFragmentsStack) {
					if (fragment is ChatActivity && !fragment.isInScheduleMode) {
						if (dialogId == 0L) {
							dialogId = fragment.dialogId
						}
					}
				}
			}

			setupActionBarLayout()

			actionBarLayout?.showLastFragment()

			if (AndroidUtilities.isTablet()) {
				rightActionBarLayout?.showLastFragment()
				layersActionBarLayout?.showLastFragment()

				for (fragment in mainFragmentsStack) {
					if (fragment is DialogsActivity && fragment.isMainDialogList) {
						fragment.setOpenedDialogId(dialogId)
					}
				}
			}
		}
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		AndroidUtilities.checkDisplaySize(this, newConfig)

		super.onConfigurationChanged(newConfig)

		checkLayout()

		val pipRoundVideoView = PipRoundVideoView.getInstance()
		pipRoundVideoView?.onConfigurationChanged()

		val embedBottomSheet = EmbedBottomSheet.getInstance()
		embedBottomSheet?.onConfigurationChanged(newConfig)

		val photoViewer = PhotoViewer.getPipInstance()
		photoViewer?.onConfigurationChanged(newConfig)

		val editorView = ThemeEditorView.getInstance()
		editorView?.onConfigurationChanged()
	}

	@Deprecated("Deprecated in Java")
	override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
		AndroidUtilities.isInMultiwindow = isInMultiWindowMode
		checkLayout()
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.appDidLogout -> {
				switchToAvailableAccountOrLogout()
			}

			NotificationCenter.closeOtherAppActivities -> {
				if (args[0] !== this) {
					onFinish()
					finish()
				}
			}

			NotificationCenter.didUpdateConnectionState -> {
				val state = ConnectionsManager.getInstance(account).getConnectionState()

				if (currentConnectionState != state) {
					FileLog.d("switch to state $state")
					currentConnectionState = state
					updateCurrentConnectionState()
				}
			}

			NotificationCenter.needShowAlert -> {
				val reason = args[0] as Int

				if (reason == 6 || reason == 3 && proxyErrorDialog != null) {
					return
				}
				else if (reason == 4) {
					showTosActivity(account, args[1] as TL_help_termsOfService)
					return
				}

				val builder = AlertDialog.Builder(this)
				builder.setTitle(getString(R.string.AppName))

				if (reason != 2 && reason != 3) {
					builder.setNegativeButton(getString(R.string.MoreInfo)) { _, _ ->
						if (mainFragmentsStack.isNotEmpty()) {
							MessagesController.getInstance(account).openByUserName("spambot", mainFragmentsStack[mainFragmentsStack.size - 1], 1)
						}
					}
				}

				if (reason == 5) {
					builder.setMessage(getString(R.string.NobodyLikesSpam3))
					builder.setPositiveButton(getString(R.string.OK), null)
				}
				else if (reason == 0) {
					builder.setMessage(getString(R.string.NobodyLikesSpam1))
					builder.setPositiveButton(getString(R.string.OK), null)
				}
				else if (reason == 1) {
					builder.setMessage(getString(R.string.NobodyLikesSpam2))
					builder.setPositiveButton(getString(R.string.OK), null)
				}
				else if (reason == 2) {
					builder.setMessage(args[1] as String)

					val type = args[2] as String

					if (type.startsWith("AUTH_KEY_DROP_")) {
						builder.setPositiveButton(getString(R.string.Cancel), null)

						builder.setNegativeButton(getString(R.string.LogOut)) { _, _ ->
							MessagesController.getInstance(currentAccount).performLogout(2)
						}
					}
					else {
						builder.setPositiveButton(getString(R.string.OK), null)
					}
				}
				else if (reason == 3) {
					builder.setTitle(getString(R.string.Proxy))
					builder.setMessage(getString(R.string.UseProxyTelegramError))
					builder.setPositiveButton(getString(R.string.OK), null)

					proxyErrorDialog = showAlertDialog(builder)

					return
				}

				if (mainFragmentsStack.isNotEmpty()) {
					mainFragmentsStack[mainFragmentsStack.size - 1].showDialog(builder.create())
				}
			}

			NotificationCenter.wasUnableToFindCurrentLocation -> {
				val waitingForLocation = args[0] as Map<String, MessageObject>

				val builder = AlertDialog.Builder(this)
				builder.setTitle(getString(R.string.AppName))
				builder.setPositiveButton(getString(R.string.OK), null)

				builder.setNegativeButton(getString(R.string.ShareYouLocationUnableManually)) { _, _ ->
					val lastFragment = mainFragmentsStack.lastOrNull() ?: return@setNegativeButton

					if (!AndroidUtilities.isMapsInstalled(lastFragment)) {
						return@setNegativeButton
					}

					val fragment = LocationActivity(0)

					fragment.setDelegate { location, _, notify, scheduleDate ->
						for ((_, messageObject) in waitingForLocation) {
							SendMessagesHelper.getInstance(account).sendMessage(location, messageObject.dialogId, messageObject, null, null, null, notify, scheduleDate, false, null)
						}
					}

					presentFragment(fragment)
				}

				builder.setMessage(getString(R.string.ShareYouLocationUnable))

				mainFragmentsStack.lastOrNull()?.showDialog(builder.create())
			}

			NotificationCenter.didSetPasscode -> {
				if (SharedConfig.passcodeHash.isNotEmpty() && !SharedConfig.allowScreenCapture) {
					try {
						window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				else if (!AndroidUtilities.hasFlagSecureFragment()) {
					try {
						window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			NotificationCenter.reloadInterface -> {
				val profileActivity = mainFragmentsStack.lastOrNull() as? ProfileActivity
				var last = mainFragmentsStack.size > 1 && profileActivity != null

				if (last) {
					if (profileActivity?.isSettings != true) {
						last = false
					}
				}

				rebuildAllFragments(last)
			}

			NotificationCenter.openArticle -> {
				if (mainFragmentsStack.isEmpty()) {
					return
				}

				ArticleViewer.getInstance().setParentActivity(this, mainFragmentsStack[mainFragmentsStack.size - 1])
				ArticleViewer.getInstance().open(args[0] as TL_webPage, args[1] as String)
			}

			NotificationCenter.didSetNewTheme -> {
				val nightTheme = args[0] as Boolean

				if (!nightTheme) {
					try {
						setTaskDescription(TaskDescription(null, null, getColor(R.color.background) or -0x1000000))
					}
					catch (e: Exception) {
						// ignored
					}
				}

				drawerLayoutContainer?.setBehindKeyboardColor(getColor(R.color.background))

				var checkNavigationBarColor = true

				if (args.size > 1) {
					checkNavigationBarColor = args[1] as Boolean
				}

				checkSystemBarColors(args.size > 2 && args[2] as Boolean, true, checkNavigationBarColor && !isNavigationBarColorFrozen && !actionBarLayout!!.isTransitionAnimationInProgress)
			}

			NotificationCenter.notificationsCountUpdated -> {
				// TODO: can show notifications count on chats tab
			}

			NotificationCenter.needShowPlayServicesAlert -> {
				runCatching {
					val status = args[0] as Status
					status.startResolutionForResult(this, PLAY_SERVICES_REQUEST_CHECK_SETTINGS)
				}
			}

			NotificationCenter.screenStateChanged -> {
				if (ApplicationLoader.mainInterfacePaused) {
					return
				}

				if (ApplicationLoader.isScreenOn) {
					onPasscodeResume()
				}
				else {
					onPasscodePause()
				}
			}

			NotificationCenter.needCheckSystemBarColors -> {
				val useCurrentFragment = args.isNotEmpty() && args[0] as Boolean
				checkSystemBarColors(useCurrentFragment)
			}

			NotificationCenter.historyImportProgressChanged -> {
				if (args.size > 1 && mainFragmentsStack.isNotEmpty()) {
					AlertsCreator.processError(currentAccount, args[2] as TL_error, mainFragmentsStack[mainFragmentsStack.size - 1], args[1] as TLObject)
				}
			}

			NotificationCenter.stickersImportComplete -> {
				MediaDataController.getInstance(account).toggleStickerSet(this, args[0] as TLObject, 2, if (mainFragmentsStack.isNotEmpty()) mainFragmentsStack[mainFragmentsStack.size - 1] else null, showSettings = false, showTooltip = true)
			}

			NotificationCenter.showBulletin -> {
				if (mainFragmentsStack.isNotEmpty()) {
					val type = args[0] as Int
					var container: FrameLayout? = null
					var fragment: BaseFragment? = null

					if (GroupCallActivity.groupCallUiVisible && GroupCallActivity.groupCallInstance != null) {
						container = GroupCallActivity.groupCallInstance?.container
					}

					if (container == null) {
						fragment = mainFragmentsStack[mainFragmentsStack.size - 1]
					}

					when (type) {
						Bulletin.TYPE_NAME_CHANGED -> {
							val peerId = args[1] as Long
							val text = if (peerId > 0) getString(R.string.YourNameChanged) else getString(R.string.ChannelTitleChanged)

							if (container != null) {
								BulletinFactory.of(container)
							}
							else if (fragment != null) {
								BulletinFactory.of(fragment)
							}
							else {
								null
							}?.createErrorBulletin(text)?.show()
						}

						Bulletin.TYPE_BIO_CHANGED -> {
							val peerId = args[1] as Long
							val text = if (peerId > 0) getString(R.string.YourBioChanged) else getString(R.string.ChannelDescriptionChanged)

							if (container != null) {
								BulletinFactory.of(container)
							}
							else if (fragment != null) {
								BulletinFactory.of(fragment)
							}
							else {
								null
							}?.createErrorBulletin(text)?.show()
						}

						Bulletin.TYPE_STICKER -> {
							val sticker = args[1] as TLRPC.Document
							val bulletinType = args[2] as Int
							val layout = StickerSetBulletinLayout(this, null, bulletinType, sticker)
							var duration = Bulletin.DURATION_SHORT

							if (bulletinType == StickerSetBulletinLayout.TYPE_REPLACED_TO_FAVORITES || bulletinType == StickerSetBulletinLayout.TYPE_REPLACED_TO_FAVORITES_GIFS) {
								duration = 3500
							}

							if (fragment != null) {
								Bulletin.make(fragment, layout, duration).show()
							}
							else if (container != null) {
								Bulletin.make(container, layout, duration).show()
							}
						}

						Bulletin.TYPE_ERROR -> {
							if (fragment != null) {
								BulletinFactory.of(fragment).createErrorBulletin(args[1] as String).show()
							}
							else if (container != null) {
								BulletinFactory.of(container).createErrorBulletin(args[1] as String).show()
							}
						}

						Bulletin.TYPE_ERROR_SUBTITLE -> {
							if (fragment != null) {
								BulletinFactory.of(fragment).createErrorBulletinSubtitle(args[1] as String, args[2] as String).show()
							}
							else if (container != null) {
								BulletinFactory.of(container).createErrorBulletinSubtitle(args[1] as String, args[2] as String).show()
							}
						}

						Bulletin.TYPE_APP_ICON -> {
							val icon = args[1] as LauncherIcon
							val layout = AppIconBulletinLayout(this, icon)
							val duration = Bulletin.DURATION_SHORT

							if (fragment != null) {
								Bulletin.make(fragment, layout, duration).show()
							}
							else if (container != null) {
								Bulletin.make(container, layout, duration).show()
							}
						}
					}
				}
			}

			NotificationCenter.groupCallUpdated -> {
				checkWasMutedByAdmin(false)
			}

			NotificationCenter.currentUserShowLimitReachedDialog -> {
				AlertsCreator.createTooLargeFileDialog(this, 0).show()

//				val fragment = mainFragmentsStack.lastOrNull()
//
//				if (fragment?.parentActivity != null) {
//					fragment.showDialog(LimitReachedBottomSheet(fragment, args[0] as Int, currentAccount))
//				}
			}

			NotificationCenter.requestPermissions -> {
				val type = args[0] as Int
				var permissions: Array<String?>? = null

				if (type == BLUETOOTH_CONNECT_TYPE) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
						permissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
					}
				}

				if (permissions != null) {
					requestPermissionsPointer++
					requestedPermissions.put(requestPermissionsPointer, type)
					ActivityCompat.requestPermissions(this, permissions, requestPermissionsPointer)
				}
			}

			NotificationCenter.updateUnreadBadge -> {
				val count = args[0] as Int
				bottomNavigationPanel?.setUnreadBadge(count)
			}
		}
	}

	private fun invalidateCachedViews(parent: View?) {
		val layerType = parent?.layerType

		if (layerType != View.LAYER_TYPE_NONE) {
			parent?.invalidate()
		}

		if (parent is ViewGroup) {
			for (i in 0 until parent.childCount) {
				invalidateCachedViews(parent.getChildAt(i))
			}
		}
	}

	private fun checkWasMutedByAdmin(checkOnly: Boolean) {
		val voIPService = VoIPService.sharedInstance

		if (voIPService?.groupCall != null) {
			val wasMuted = wasMutedByAdminRaisedHand
			val call = voIPService.groupCall
			val peer = voIPService.getGroupCallPeer()

			val did = if (peer != null) {
				if (peer.user_id != 0L) {
					peer.user_id
				}
				else if (peer.chat_id != 0L) {
					-peer.chat_id
				}
				else {
					-peer.channel_id
				}
			}
			else {
				UserConfig.getInstance(currentAccount).clientUserId
			}

			val participant = call?.participants?.get(did)
			val mutedByAdmin = participant != null && !participant.can_self_unmute && participant.muted

			wasMutedByAdminRaisedHand = mutedByAdmin && participant!!.raise_hand_rating != 0L

			if (!checkOnly && wasMuted && !wasMutedByAdminRaisedHand && !mutedByAdmin && GroupCallActivity.groupCallInstance == null) {
				showVoiceChatTooltip(UndoView.ACTION_VOIP_CAN_NOW_SPEAK)
			}
		}
		else {
			wasMutedByAdminRaisedHand = false
		}
	}

	private fun showVoiceChatTooltip(action: Int) {
		val voIPService = VoIPService.sharedInstance

		if (voIPService == null || mainFragmentsStack.isEmpty() || voIPService.groupCall == null) {
			return
		}

		if (mainFragmentsStack.isNotEmpty()) {
			var chat = voIPService.getChat()

			when (val fragment = actionBarLayout?.fragmentsStack?.lastOrNull()) {
				is ChatActivity -> {
					if (fragment.dialogId == -chat!!.id) {
						chat = null
					}

					fragment.undoView?.showWithAction(0, action, chat)
				}

				is DialogsActivity -> {
					fragment.getUndoView()?.showWithAction(0, action, chat)
				}

				is ProfileActivity -> {
					fragment.undoView?.showWithAction(0, action, chat)
				}
			}

			if (action == UndoView.ACTION_VOIP_CAN_NOW_SPEAK) {
				VoIPService.sharedInstance?.playAllowTalkSound()
			}
		}
	}

	private fun checkFreeDiscSpace() {
		SharedConfig.checkKeepMedia()
		SharedConfig.checkLogsToDelete()

		if (Build.VERSION.SDK_INT >= 26) {
			return
		}

		Utilities.globalQueue.postRunnable({
			if (!UserConfig.getInstance(currentAccount).isClientActivated) {
				return@postRunnable
			}

			runCatching {
				val preferences = MessagesController.getGlobalMainSettings()

				if (abs(preferences.getLong("last_space_check", 0) - System.currentTimeMillis()) >= 3 * 24 * 3600 * 1000) {
					val path = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) ?: return@postRunnable
					val statFs = StatFs(path.absolutePath)
					val freeSpace = statFs.availableBlocksLong * statFs.blockSizeLong

					if (freeSpace < 1024 * 1024 * 100) {
						preferences.edit().putLong("last_space_check", System.currentTimeMillis()).commit()

						AndroidUtilities.runOnUIThread {
							runCatching {
								AlertsCreator.createFreeSpaceDialog(this@LaunchActivity).show()
							}
						}
					}
				}
			}
		}, 2000)
	}

	private fun drawRippleAbove(canvas: Canvas, parent: View?) {
		if (parent == null || rippleAbove?.background == null) {
			return
		}

		if (tempLocation == null) {
			tempLocation = IntArray(2)
		}

		rippleAbove?.getLocationInWindow(tempLocation)

		var x = tempLocation!![0]
		var y = tempLocation!![1]

		parent.getLocationInWindow(tempLocation)

		x -= tempLocation!![0]
		y -= tempLocation!![1]

		canvas.save()
		canvas.translate(x.toFloat(), y.toFloat())

		rippleAbove?.background?.draw(canvas)

		canvas.restore()
	}

	private fun onPasscodePause() {
		if (lockRunnable != null) {
			FileLog.d("cancel lockRunnable onPasscodePause")
			AndroidUtilities.cancelRunOnUIThread(lockRunnable)
			lockRunnable = null
		}

		if (SharedConfig.passcodeHash.isNotEmpty()) {
			SharedConfig.lastPauseTime = (SystemClock.elapsedRealtime() / 1000).toInt()

			lockRunnable = object : Runnable {
				override fun run() {
					if (lockRunnable === this) {
						if (AndroidUtilities.needShowPasscode(true)) {
							FileLog.d("lock app")
							showPasscodeActivity(fingerprint = true, animated = false, x = -1, y = -1, onShow = null, onStart = null)
						}
						else {
							FileLog.d("didn't pass lock check")
						}

						lockRunnable = null
					}
				}
			}

			if (SharedConfig.appLocked) {
				AndroidUtilities.runOnUIThread(lockRunnable, 1000)
				FileLog.d("schedule app lock in " + 1000)
			}
			else if (SharedConfig.autoLockIn != 0) {
				FileLog.d("schedule app lock in " + (SharedConfig.autoLockIn.toLong() * 1000 + 1000))
				AndroidUtilities.runOnUIThread(lockRunnable, SharedConfig.autoLockIn.toLong() * 1000 + 1000)
			}
		}
		else {
			SharedConfig.lastPauseTime = 0
		}

		SharedConfig.saveConfig()
	}

	private fun onPasscodeResume() {
		if (lockRunnable != null) {
			FileLog.d("cancel lockRunnable onPasscodeResume")
			AndroidUtilities.cancelRunOnUIThread(lockRunnable)
			lockRunnable = null
		}

		if (AndroidUtilities.needShowPasscode(true)) {
			showPasscodeActivity(fingerprint = true, animated = false, x = -1, y = -1, onShow = null, onStart = null)
		}

		if (SharedConfig.lastPauseTime != 0) {
			SharedConfig.lastPauseTime = 0
			SharedConfig.saveConfig()

			FileLog.d("reset lastPauseTime onPasscodeResume")
		}
	}

	private fun updateCurrentConnectionState() {
		if (actionBarLayout == null) {
			return
		}

		var title: String? = null
		var titleId = 0
		var action: Runnable? = null

		currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState()

		when (currentConnectionState) {
			ConnectionsManager.ConnectionStateWaitingForNetwork -> {
				title = "WaitingForNetwork"
				titleId = R.string.WaitingForNetwork
			}

			ConnectionsManager.ConnectionStateUpdating -> {
				title = "Updating"
				titleId = R.string.Updating
			}

			ConnectionsManager.ConnectionStateConnectingToProxy -> {
				title = "ConnectingToProxy"
				titleId = R.string.ConnectingToProxy
			}

			ConnectionsManager.ConnectionStateConnecting -> {
				title = "Connecting"
				titleId = R.string.Connecting
			}
		}

		if (currentConnectionState == ConnectionsManager.ConnectionStateConnecting || currentConnectionState == ConnectionsManager.ConnectionStateConnectingToProxy) {
			action = Runnable {
				val lastFragment = if (AndroidUtilities.isTablet()) {
					layerFragmentsStack.lastOrNull()
				}
				else {
					mainFragmentsStack.lastOrNull()
				}

				if (lastFragment is ProxyListActivity || lastFragment is ProxySettingsActivity) {
					return@Runnable
				}

				presentFragment(ProxyListActivity())
			}
		}

		mainFragmentsStack.find { it is CallLogActivity }?.let { (it as CallLogActivity).actionbarShowingTitle(!title.isNullOrEmpty()) }

		actionBarLayout?.setTitleOverlayText(title, titleId, action)
	}

	fun hideVisibleActionMode() {
		visibleActionMode?.finish()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		try {
			super.onSaveInstanceState(outState)

			val lastFragment = if (AndroidUtilities.isTablet()) {
				layersActionBarLayout?.fragmentsStack?.lastOrNull() ?: rightActionBarLayout?.fragmentsStack?.lastOrNull() ?: actionBarLayout?.fragmentsStack?.lastOrNull()
			}
			else {
				actionBarLayout?.fragmentsStack?.lastOrNull()
			}

			if (lastFragment != null) {
				val args = lastFragment.arguments

				if (lastFragment is ChatActivity && args != null) {
					outState.putBundle("args", args)
					outState.putString("fragment", "chat")
				}
				else if (lastFragment is GroupCreateFinalActivity && args != null) {
					outState.putBundle("args", args)
					outState.putString("fragment", "group")
				}
				else if (lastFragment is WallpapersListActivity) {
					outState.putString("fragment", "wallpapers")
				}
				else if (lastFragment is ProfileActivity) {
					if (lastFragment.isSettings) {
						outState.putString("fragment", "settings")
					}
					else if (lastFragment.isChat && args != null) {
						outState.putBundle("args", args)
						outState.putString("fragment", "chat_profile")
					}
				}
				else if (lastFragment is ChannelCreateActivity && args != null && args.getInt("step") == 0) {
					outState.putBundle("args", args)
					outState.putString("fragment", "channel")
				}

				lastFragment.saveSelfArgs(outState)
			}

			bottomNavigationPanel?.let {
				outState.putString("bottomNavigationPanel", it.getCurrentItem().name)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		if (passcodeView != null && passcodeView?.visibility == View.VISIBLE) {
			finish()
			return
		}

		if (ContentPreviewViewer.hasInstance() && ContentPreviewViewer.getInstance().isVisible) {
			ContentPreviewViewer.getInstance().closeWithMenu()
		}

		if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance()?.isVisible == true) {
			SecretMediaViewer.getInstance()?.closePhoto(true, false)
		}
		else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
			PhotoViewer.getInstance().closePhoto(true, false)
		}
		else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance()?.isVisible == true) {
			ArticleViewer.getInstance()?.close(true, false)
		}
		else if (drawerLayoutContainer?.isDrawerOpened == true) {
			drawerLayoutContainer?.closeDrawer(false)
		}
		else if (AndroidUtilities.isTablet()) {
			if (layersActionBarLayout?.visibility == View.VISIBLE) {
				layersActionBarLayout?.onBackPressed()
			}
			else {
				if (rightActionBarLayout?.visibility == View.VISIBLE && rightActionBarLayout!!.fragmentsStack.isNotEmpty()) {
					val lastFragment = rightActionBarLayout!!.fragmentsStack[rightActionBarLayout!!.fragmentsStack.size - 1]

					if (lastFragment.onBackPressed()) {
						lastFragment.finishFragment()
					}
				}
				else {
					actionBarLayout?.onBackPressed()
				}
			}
		}
		else {
			actionBarLayout?.onBackPressed()
		}
	}

	override fun onLowMemory() {
		super.onLowMemory()

		if (actionBarLayout != null) {
			actionBarLayout?.onLowMemory()

			if (AndroidUtilities.isTablet()) {
				rightActionBarLayout?.onLowMemory()
				layersActionBarLayout?.onLowMemory()
			}
		}
	}

	override fun onActionModeStarted(mode: ActionMode) {
		super.onActionModeStarted(mode)

		visibleActionMode = mode

		try {
			val menu = mode.menu

			if (menu != null) {
				var extended = actionBarLayout?.extendActionMode(menu) == true

				if (!extended && AndroidUtilities.isTablet()) {
					extended = rightActionBarLayout?.extendActionMode(menu) == true

					if (!extended) {
						layersActionBarLayout?.extendActionMode(menu)
					}
				}
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		if (mode.type == ActionMode.TYPE_FLOATING) {
			return
		}

		actionBarLayout?.onActionModeStarted()

		if (AndroidUtilities.isTablet()) {
			rightActionBarLayout?.onActionModeStarted()
			layersActionBarLayout?.onActionModeStarted()
		}
	}

	override fun onActionModeFinished(mode: ActionMode) {
		super.onActionModeFinished(mode)

		if (visibleActionMode === mode) {
			visibleActionMode = null
		}

		if (mode.type == ActionMode.TYPE_FLOATING) {
			return
		}

		actionBarLayout?.onActionModeFinished()

		if (AndroidUtilities.isTablet()) {
			rightActionBarLayout?.onActionModeFinished()
			layersActionBarLayout?.onActionModeFinished()
		}
	}

	override fun onPreIme(): Boolean {
		if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance()?.isVisible == true) {
			SecretMediaViewer.getInstance()?.closePhoto(true, false)
			return true
		}
		else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
			PhotoViewer.getInstance().closePhoto(true, false)
			return true
		}
		else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance()?.isVisible == true) {
			ArticleViewer.getInstance()?.close(true, false)
			return true
		}

		return false
	}

	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		if (event.action == KeyEvent.ACTION_DOWN && (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
			if (VoIPService.sharedInstance != null) {
				if (Build.VERSION.SDK_INT >= 32) {
					val oldValue = WebRtcAudioTrack.isSpeakerMuted()
					val am = getSystemService(AUDIO_SERVICE) as AudioManager
					val minVolume = am.getStreamMinVolume(AudioManager.STREAM_VOICE_CALL)
					val mute = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL) == minVolume && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

					WebRtcAudioTrack.setSpeakerMute(mute)

					if (oldValue != WebRtcAudioTrack.isSpeakerMuted()) {
						showVoiceChatTooltip(if (mute) UndoView.ACTION_VOIP_SOUND_MUTED else UndoView.ACTION_VOIP_SOUND_UNMUTED)
					}
				}
			}
			else if (mainFragmentsStack.isNotEmpty() && (!PhotoViewer.hasInstance() || !PhotoViewer.getInstance().isVisible) && event.repeatCount == 0) {
				var fragment = mainFragmentsStack[mainFragmentsStack.size - 1]

				if (fragment is ChatActivity) {
					if (fragment.maybePlayVisibleVideo()) {
						return true
					}
				}

				if (AndroidUtilities.isTablet() && rightFragmentsStack.isNotEmpty()) {
					fragment = rightFragmentsStack[rightFragmentsStack.size - 1]

					if (fragment is ChatActivity) {
						if (fragment.maybePlayVisibleVideo()) {
							return true
						}
					}
				}
			}
		}

		try {
			super.dispatchKeyEvent(event)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		return false
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
		if (keyCode == KeyEvent.KEYCODE_MENU && !SharedConfig.isWaitingForPasscodeEnter) {
			if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
				return super.onKeyUp(keyCode, event)
			}
			else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance()?.isVisible == true) {
				return super.onKeyUp(keyCode, event)
			}

			if (AndroidUtilities.isTablet()) {
				if (layersActionBarLayout?.visibility == View.VISIBLE && layersActionBarLayout!!.fragmentsStack.isNotEmpty()) {
					layersActionBarLayout?.onKeyUp(keyCode, event)
				}
				else if (rightActionBarLayout?.visibility == View.VISIBLE && rightActionBarLayout!!.fragmentsStack.isNotEmpty()) {
					rightActionBarLayout?.onKeyUp(keyCode, event)
				}
				else {
					actionBarLayout?.onKeyUp(keyCode, event)
				}
			}
			else {
				if (actionBarLayout?.fragmentsStack?.size == 1) {
					if (!drawerLayoutContainer!!.isDrawerOpened) {
						if (currentFocus != null) {
							AndroidUtilities.hideKeyboard(currentFocus)
						}

						drawerLayoutContainer?.openDrawer(false)
					}
					else {
						drawerLayoutContainer?.closeDrawer(false)
					}
				}
				else {
					actionBarLayout?.onKeyUp(keyCode, event)
				}
			}
		}

		return super.onKeyUp(keyCode, event)
	}

	override fun needPresentFragment(fragment: BaseFragment, removeLast: Boolean, forceWithoutAnimation: Boolean, layout: ActionBarLayout): Boolean {
		if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible) {
			ArticleViewer.getInstance().close(false, true)
		}

		if (AndroidUtilities.isTablet()) {
			drawerLayoutContainer!!.setAllowOpenDrawer(!(fragment is LoginActivity || fragment is IntroActivity || fragment is CountrySelectActivity) && layersActionBarLayout!!.visibility != View.VISIBLE, true)

			if (fragment is DialogsActivity) {
				if (fragment.isMainDialogList && layout !== actionBarLayout) {
					actionBarLayout?.removeAllFragments()
					actionBarLayout?.presentFragment(fragment, removeLast, forceWithoutAnimation, false, false)

					layersActionBarLayout?.removeAllFragments()
					layersActionBarLayout?.gone()

					drawerLayoutContainer?.setAllowOpenDrawer(true, false)

					if (!tabletFullSize) {
						shadowTabletSide?.visible()

						if (rightActionBarLayout!!.fragmentsStack.isEmpty()) {
							backgroundTablet?.visible()
						}
					}

					return false
				}
			}

			if (fragment is ChatActivity && !fragment.isInScheduleMode) {
				return if (!tabletFullSize && layout === rightActionBarLayout || tabletFullSize && layout === actionBarLayout) {
					val result = !(tabletFullSize && layout === actionBarLayout && actionBarLayout!!.fragmentsStack.size == 1)

					if (layersActionBarLayout!!.fragmentsStack.isNotEmpty()) {
						val a = 0

						while (a < layersActionBarLayout!!.fragmentsStack.size - 1) {
							layersActionBarLayout!!.removeFragmentFromStack(layersActionBarLayout!!.fragmentsStack[0])
						}

						layersActionBarLayout?.closeLastFragment(!forceWithoutAnimation)
					}

					if (!result) {
						actionBarLayout?.presentFragment(fragment, false, forceWithoutAnimation, false, false)
					}

					result
				}
				else if (!tabletFullSize && layout !== rightActionBarLayout) {
					rightActionBarLayout?.visible()
					backgroundTablet?.gone()

					rightActionBarLayout?.removeAllFragments()
					rightActionBarLayout?.presentFragment(fragment, removeLast, true, false, false)

					if (layersActionBarLayout!!.fragmentsStack.isNotEmpty()) {
						val a = 0

						while (a < layersActionBarLayout!!.fragmentsStack.size - 1) {
							layersActionBarLayout?.removeFragmentFromStack(layersActionBarLayout!!.fragmentsStack[0])
						}

						layersActionBarLayout?.closeLastFragment(!forceWithoutAnimation)
					}

					false
				}
				else if (tabletFullSize && layout !== actionBarLayout) {
					actionBarLayout?.presentFragment(fragment, actionBarLayout!!.fragmentsStack.size > 1, forceWithoutAnimation, false, false)

					if (layersActionBarLayout!!.fragmentsStack.isNotEmpty()) {
						val a = 0

						while (a < layersActionBarLayout!!.fragmentsStack.size - 1) {
							layersActionBarLayout?.removeFragmentFromStack(layersActionBarLayout!!.fragmentsStack[0])
						}

						layersActionBarLayout?.closeLastFragment(!forceWithoutAnimation)
					}

					false
				}
				else {
					if (layersActionBarLayout!!.fragmentsStack.isNotEmpty()) {
						val a = 0

						while (a < layersActionBarLayout!!.fragmentsStack.size - 1) {
							layersActionBarLayout?.removeFragmentFromStack(layersActionBarLayout!!.fragmentsStack[0])
						}

						layersActionBarLayout?.closeLastFragment(!forceWithoutAnimation)
					}

					actionBarLayout?.presentFragment(fragment, actionBarLayout!!.fragmentsStack.size > 1, forceWithoutAnimation, false, false)

					false
				}
			}
			else if (layout !== layersActionBarLayout) {
				layersActionBarLayout?.visible()

				drawerLayoutContainer?.setAllowOpenDrawer(false, true)

				if (fragment is LoginActivity) {
					backgroundTablet?.visible()
					shadowTabletSide?.gone()
					shadowTablet?.setBackgroundColor(0x00000000)
				}
				else {
					shadowTablet?.setBackgroundColor(0x7f000000)
				}

				layersActionBarLayout?.presentFragment(fragment, removeLast, forceWithoutAnimation, false, false)

				return false
			}
		}
		else {
			var allow = true // TODO: Make it a flag inside fragment itself, maybe BaseFragment#isDrawerOpenAllowed()?

			if (fragment is LoginActivity || fragment is IntroActivity) {
				if (mainFragmentsStack.size == 0 || mainFragmentsStack[0] is IntroActivity) {
					allow = false
				}
			}
			else if (fragment is CountrySelectActivity) {
				if (mainFragmentsStack.size == 1) {
					allow = false
				}
			}

			drawerLayoutContainer?.setAllowOpenDrawer(allow, false)
		}

		return true
	}

	override fun needAddFragmentToStack(fragment: BaseFragment, layout: ActionBarLayout): Boolean {
		if (AndroidUtilities.isTablet()) {
			drawerLayoutContainer?.setAllowOpenDrawer(!(fragment is LoginActivity || fragment is IntroActivity || fragment is CountrySelectActivity) && layersActionBarLayout!!.visibility != View.VISIBLE, true)

			if (fragment is DialogsActivity) {
				if (fragment.isMainDialogList && layout !== actionBarLayout) {
					actionBarLayout?.removeAllFragments()
					actionBarLayout?.addFragmentToStack(fragment)
					layersActionBarLayout?.removeAllFragments()
					layersActionBarLayout?.gone()
					drawerLayoutContainer?.setAllowOpenDrawer(true, false)

					if (!tabletFullSize) {
						shadowTabletSide?.visible()

						if (rightActionBarLayout!!.fragmentsStack.isEmpty()) {
							backgroundTablet?.visible()
						}
					}

					return false
				}
			}
			else if (fragment is ChatActivity && !fragment.isInScheduleMode) {
				if (!tabletFullSize && layout !== rightActionBarLayout) {
					rightActionBarLayout?.visible()
					backgroundTablet?.gone()

					rightActionBarLayout?.removeAllFragments()
					rightActionBarLayout?.addFragmentToStack(fragment)

					if (layersActionBarLayout!!.fragmentsStack.isNotEmpty()) {
						val a = 0

						while (a < layersActionBarLayout!!.fragmentsStack.size - 1) {
							layersActionBarLayout?.removeFragmentFromStack(layersActionBarLayout!!.fragmentsStack[0])
						}

						layersActionBarLayout?.closeLastFragment(true)
					}
					return false
				}
				else if (tabletFullSize && layout !== actionBarLayout) {
					actionBarLayout?.addFragmentToStack(fragment)

					if (layersActionBarLayout!!.fragmentsStack.isNotEmpty()) {
						val a = 0

						while (a < layersActionBarLayout!!.fragmentsStack.size - 1) {
							layersActionBarLayout!!.removeFragmentFromStack(layersActionBarLayout!!.fragmentsStack[0])
						}

						layersActionBarLayout?.closeLastFragment(true)
					}
					return false
				}
			}
			else if (layout !== layersActionBarLayout) {
				layersActionBarLayout?.visible()

				drawerLayoutContainer?.setAllowOpenDrawer(false, true)

				if (fragment is LoginActivity) {
					backgroundTablet?.visible()
					shadowTabletSide?.gone()
					shadowTablet?.setBackgroundColor(0x00000000)
				}
				else {
					shadowTablet?.setBackgroundColor(0x7f000000)
				}

				layersActionBarLayout?.addFragmentToStack(fragment)

				return false
			}
		}
		else {
			var allow = true

			if (fragment is LoginActivity || fragment is IntroActivity) {
				if (mainFragmentsStack.size == 0 || mainFragmentsStack[0] is IntroActivity) {
					allow = false
				}
			}
			else if (fragment is CountrySelectActivity) {
				if (mainFragmentsStack.size == 1) {
					allow = false
				}
			}

			drawerLayoutContainer?.setAllowOpenDrawer(allow, false)
		}

		return true
	}

	override fun needCloseLastFragment(layout: ActionBarLayout): Boolean {
		if (AndroidUtilities.isTablet()) {
			if (layout === actionBarLayout && layout.fragmentsStack.size <= 1) {
				onFinish()
				finish()
				return false
			}
			else if (layout === rightActionBarLayout) {
				if (!tabletFullSize) {
					backgroundTablet?.visible()
				}
			}
			else if (layout === layersActionBarLayout && actionBarLayout!!.fragmentsStack.isEmpty() && layersActionBarLayout!!.fragmentsStack.size == 1) {
				onFinish()
				finish()
				return false
			}
		}
		else {
			if (layout.fragmentsStack.size <= 1) {
				onFinish()
				finish()
				return false
			}

			if (layout.fragmentsStack.size >= 2 && layout.fragmentsStack[0] !is LoginActivity) {
				drawerLayoutContainer?.setAllowOpenDrawer(true, false)
			}
		}

		return true
	}

	fun rebuildAllFragments(last: Boolean) {
		layersActionBarLayout?.rebuildAllFragmentViews(last, last) ?: actionBarLayout?.rebuildAllFragmentViews(last, last)
	}

	override fun onRebuildAllFragments(layout: ActionBarLayout, last: Boolean) {
		if (AndroidUtilities.isTablet()) {
			if (layout === layersActionBarLayout) {
				rightActionBarLayout?.rebuildAllFragmentViews(last, last)
				actionBarLayout?.rebuildAllFragmentViews(last, last)
			}
		}
	}

	companion object {
		@JvmField
		val PREFIX_ELLOAPP_PATTERN: Pattern = Pattern.compile("^(?:http(?:s|)://|)([A-z0-9-]+?)\\.elloapp\\.me")

		var REFERRAL_CODE: String? = null
		const val SCREEN_CAPTURE_REQUEST_CODE = 520
		const val BLUETOOTH_CONNECT_TYPE = 0
		private const val EXTRA_ACTION_TOKEN = "actions.fulfillment.extra.ACTION_TOKEN"
		private val mainFragmentsStack = ArrayList<BaseFragment>()
		private val layerFragmentsStack = ArrayList<BaseFragment>()
		private val rightFragmentsStack = ArrayList<BaseFragment>()
		private const val PLAY_SERVICES_REQUEST_CHECK_SETTINGS = 140

		@JvmField
		var isResumed = false

		@JvmField
		var onResumeStaticCallback: Runnable? = null

		@JvmStatic
		fun clearFragments() {
			for (fragment in mainFragmentsStack) {
				fragment.onFragmentDestroy()
			}

			mainFragmentsStack.clear()

			if (AndroidUtilities.isTablet()) {
				for (fragment in layerFragmentsStack) {
					fragment.onFragmentDestroy()
				}

				layerFragmentsStack.clear()

				for (fragment in rightFragmentsStack) {
					fragment.onFragmentDestroy()
				}

				rightFragmentsStack.clear()
			}
		}

		@JvmStatic
		fun getTimestampFromLink(data: Uri?): Int {
			if (data == null) {
				return -1
			}

			val segments = data.pathSegments
			var timestampStr: String? = null

			if (segments.contains("video")) {
				timestampStr = data.query
			}
			else if (data.getQueryParameter("t") != null) {
				timestampStr = data.getQueryParameter("t")
			}

			var videoTimestamp = -1

			if (timestampStr != null) {
				try {
					videoTimestamp = timestampStr.toInt()
				}
				catch (e: Throwable) {
					// ignored
				}

				if (videoTimestamp == -1) {
					val dateFormat = SimpleDateFormat("mm:ss", Locale.getDefault())

					try {
						val reference = dateFormat.parse("00:00")
						val date = dateFormat.parse(timestampStr)

						if (reference != null && date != null) {
							videoTimestamp = ((date.time - reference.time) / 1000L).toInt()
						}
					}
					catch (e: ParseException) {
						FileLog.e(e)
					}
				}
			}

			return videoTimestamp
		}
	}
}
