/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui

import android.content.Context
import android.graphics.Rect
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.vibrate
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.TL_boolTrue
import org.telegram.tgnet.TLRPC.TL_channels_checkUsername
import org.telegram.tgnet.TLRPC.TL_channels_getAdminedPublicChannels
import org.telegram.tgnet.TLRPC.TL_channels_updateUsername
import org.telegram.tgnet.TLRPC.TL_chatInviteExported
import org.telegram.tgnet.TLRPC.TL_inputChannelEmpty
import org.telegram.tgnet.TLRPC.TL_messages_chats
import org.telegram.tgnet.TLRPC.TL_messages_exportChatInvite
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.AdministeredChannelCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.LoadingCell
import org.telegram.ui.Cells.RadioButtonCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.InviteLinkBottomSheet
import org.telegram.ui.Components.JoinToSendSettingsView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LinkActionView
import org.telegram.ui.Components.LinkActionView.LinkActionType
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet
import java.util.Locale
import java.util.concurrent.CountDownLatch

class ChatEditTypeActivity(private var chatId: Long, private val isForcePublic: Boolean) : BaseFragment(), NotificationCenterDelegate {
	var usersMap = HashMap<Long, User>()
	private var usernameTextView: EditTextBoldCursor? = null
	private var editText: EditTextBoldCursor? = null
	private var typeInfoCell: TextInfoPrivacyCell? = null
	private var headerCell: HeaderCell? = null
	private var headerCell2: HeaderCell? = null
	private var checkTextView: TextInfoPrivacyCell? = null
	private var linearLayout: LinearLayout? = null
	private var doneButton: ActionBarMenuItem? = null
	private var linearLayoutTypeContainer: LinearLayout? = null
	private var radioButtonCell1: RadioButtonCell? = null
	private var radioButtonCell2: RadioButtonCell? = null
	private var administeredChannelsLayout: LinearLayout? = null
	private var linkContainer: LinearLayout? = null
	private var publicContainer: LinearLayout? = null
	private var privateContainer: LinearLayout? = null
	private var permanentLinkView: LinkActionView? = null
	private var manageLinksTextView: TextCell? = null
	private var manageLinksInfoCell: TextInfoPrivacyCell? = null
	private var sectionCell2: ShadowSectionCell? = null

	// private val infoCell: TextInfoPrivacyCell? = null
	// private val textCell: TextSettingsCell? = null
	private val textCell2: TextSettingsCell? = null

	// Saving content restrictions block
	private var saveContainer: LinearLayout? = null
	private var saveHeaderCell: HeaderCell? = null
	private var saveRestrictCell: TextCheckCell? = null
	private var saveRestrictInfoCell: TextInfoPrivacyCell? = null
	private var joinContainer: JoinToSendSettingsView? = null
	private var isPrivate = false
	private var currentChat: Chat? = null
	private var info: ChatFull? = null
	private var isChannel = false
	private var isSaveRestricted = false
	private var canCreatePublic = true
	private var loadingAdministeredChannels = false
	private var administeredInfoCell: ShadowSectionCell? = null
	private val administeredChannelCells = ArrayList<AdministeredChannelCell>()
	private var loadingAdministeredCell: LoadingCell? = null
	private var checkReqId = 0
	private var lastCheckName: String? = null
	private var checkRunnable: Runnable? = null
	private var lastNameAvailable = false
	private var loadingInvite = false
	private var invite: TL_chatInviteExported? = null
	private var ignoreTextChanges = false
	private var inviteLinkBottomSheet: InviteLinkBottomSheet? = null

	override fun onFragmentCreate(): Boolean {
		currentChat = messagesController.getChat(chatId)

		if (currentChat == null) {
			currentChat = messagesStorage.getChatSync(chatId)

			if (currentChat != null) {
				messagesController.putChat(currentChat, true)
			}
			else {
				return false
			}

			if (info == null) {
				info = messagesStorage.loadChatInfo(chatId, ChatObject.isChannel(currentChat), CountDownLatch(1), false, false)

				if (info == null) {
					return false
				}
			}
		}

		isPrivate = !isForcePublic && currentChat?.username.isNullOrEmpty()
		isChannel = ChatObject.isChannel(currentChat) && !currentChat!!.megagroup
		isSaveRestricted = currentChat!!.noforwards

		if (isForcePublic && currentChat?.username.isNullOrEmpty() || isPrivate && currentChat?.creator == true) {
			val req = TL_channels_checkUsername()
			req.username = "1"
			req.channel = TL_inputChannelEmpty()

			connectionsManager.sendRequest(req) { _, error ->
				AndroidUtilities.runOnUIThread {
					canCreatePublic = error == null || error.text != "CHANNELS_ADMIN_PUBLIC_TOO_MUCH"

					if (!canCreatePublic && userConfig.isPremium) {
						loadAdministeredChannels()
					}
				}
			}
		}

		if (isPrivate && info != null) {
			messagesController.loadFullChat(chatId, classGuid, true)
		}

		notificationCenter.addObserver(this, NotificationCenter.chatInfoDidLoad)

		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		notificationCenter.removeObserver(this, NotificationCenter.chatInfoDidLoad)
		AndroidUtilities.removeAdjustResize(parentActivity, classGuid)
	}

	override fun onResume() {
		super.onResume()

		val parentActivity = parentActivity ?: return

		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)

		val info = info ?: return

		if (textCell2 != null) {
			if (info.stickerset != null) {
				textCell2.setTextAndValue(parentActivity.getString(R.string.GroupStickers), info.stickerset?.title, false)
			}
			else {
				textCell2.setText(parentActivity.getString(R.string.GroupStickers), false)
			}
		}

		invite = info.exported_invite

		permanentLinkView?.setLink(invite?.link)
		permanentLinkView?.loadUsers(invite, chatId)
	}

	override fun onBecomeFullyVisible() {
		super.onBecomeFullyVisible()

		if (isForcePublic && usernameTextView != null) {
			usernameTextView?.requestFocus()
			AndroidUtilities.showKeyboard(usernameTextView)
		}
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}

					DONE_BUTTON -> {
						processDone()
					}
				}
			}
		})

		val menu = actionBar?.createMenu()

		doneButton = menu?.addItemWithWidth(DONE_BUTTON, R.drawable.ic_ab_done, AndroidUtilities.dp(56f), context.getString(R.string.Done))

		fragmentView = object : ScrollView(context) {
			override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
				rectangle.bottom += AndroidUtilities.dp(60f)
				return super.requestChildRectangleOnScreen(child, rectangle, immediate)
			}
		}

		fragmentView?.setBackgroundResource(R.color.light_background)

		val scrollView = fragmentView as ScrollView
		scrollView.isFillViewport = true

		linearLayout = LinearLayout(context)

		scrollView.addView(linearLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

		linearLayout?.orientation = LinearLayout.VERTICAL

		if (isForcePublic) {
			actionBar?.setTitle(context.getString(R.string.TypeLocationGroup))
		}
		else if (isChannel) {
			actionBar?.setTitle(context.getString(R.string.ChannelSettingsTitle))
		}
		else {
			actionBar?.setTitle(context.getString(R.string.GroupSettingsTitle))
		}

		linearLayoutTypeContainer = LinearLayout(context)
		linearLayoutTypeContainer?.orientation = LinearLayout.VERTICAL
		linearLayoutTypeContainer?.setBackgroundResource(R.color.background)

		linearLayout?.addView(linearLayoutTypeContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		headerCell2 = HeaderCell(context, 23)
		headerCell2?.height = 46

		if (isChannel) {
			headerCell2?.setText(context.getString(R.string.ChannelTypeHeader))
		}
		else {
			headerCell2?.setText(context.getString(R.string.GroupTypeHeader))
		}

		linearLayoutTypeContainer?.addView(headerCell2)

		radioButtonCell2 = RadioButtonCell(context)

		if (isChannel) {
			radioButtonCell2?.setTextAndValue(context.getString(R.string.ChannelPrivate), context.getString(R.string.ChannelPrivateInfo), false, isPrivate)
		}
		else {
			radioButtonCell2?.setTextAndValue(context.getString(R.string.MegaPrivate), context.getString(R.string.MegaPrivateInfo), false, isPrivate)
		}

		linearLayoutTypeContainer?.addView(radioButtonCell2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		radioButtonCell2?.setOnClickListener {
			if (isPrivate) {
				return@setOnClickListener
			}

			isPrivate = true

			updatePrivatePublic()
		}

		radioButtonCell1 = RadioButtonCell(context)
		radioButtonCell1?.background = Theme.getSelectorDrawable(false)

		if (isChannel) {
			if (ChatObject.isPaidChannel(currentChat)) {
				radioButtonCell1?.setTextAndValue(if (ChatObject.isSubscriptionChannel(currentChat)) context.getString(R.string.subscription_channel) else context.getString(R.string.online_course), context.getString(R.string.info_settings_subscription_channel), divider = false, checked = true)
				radioButtonCell2?.gone()
			}
			else {
				radioButtonCell1?.setTextAndValue(context.getString(R.string.ChannelPublic), context.getString(R.string.ChannelPublicInfo), false, !isPrivate)
			}
		}
		else {
			radioButtonCell1?.setTextAndValue(context.getString(R.string.MegaPublic), context.getString(R.string.MegaPublicInfo), false, !isPrivate)
		}

		linearLayoutTypeContainer?.addView(radioButtonCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		radioButtonCell1?.setOnClickListener {
			if (!isPrivate) {
				return@setOnClickListener
			}

			if (!canCreatePublic) {
				showPremiumIncreaseLimitDialog()
				return@setOnClickListener
			}

			isPrivate = false

			updatePrivatePublic()
		}

		sectionCell2 = ShadowSectionCell(context)

		linearLayout?.addView(sectionCell2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		if (isForcePublic) {
			radioButtonCell2?.gone()
			radioButtonCell1?.gone()
			sectionCell2?.gone()
			headerCell2?.gone()
		}

		linkContainer = LinearLayout(context)
		linkContainer?.orientation = LinearLayout.VERTICAL
		linkContainer?.setBackgroundResource(R.color.background)

		linearLayout?.addView(linkContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		headerCell = HeaderCell(context, 23)

		linkContainer?.addView(headerCell)

		publicContainer = LinearLayout(context)
		publicContainer?.orientation = LinearLayout.HORIZONTAL

		linkContainer?.addView(publicContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 23f, 7f, 23f, 0f))

		editText = EditTextBoldCursor(context)
		editText?.setText("@")
		editText?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
		editText?.setHintTextColor(context.getColor(R.color.hint))
		editText?.setTextColor(context.getColor(R.color.hint))
		editText?.maxLines = 1
		editText?.setLines(1)
		editText?.isEnabled = false
		editText?.background = null
		editText?.setPadding(0, 0, 0, 0)
		editText?.isSingleLine = true
		editText?.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
		editText?.imeOptions = EditorInfo.IME_ACTION_DONE

		publicContainer?.addView(editText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36))

		usernameTextView = object : EditTextBoldCursor(context) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)

				info.text = buildString {
					append(text)

					checkTextView?.textView?.text?.takeIf { it.isNotEmpty() }?.let {
						append("\n")
						append(it)
					}
				}
			}
		}

		usernameTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
		usernameTextView?.setHintTextColor(context.getColor(R.color.hint))
		usernameTextView?.setTextColor(context.getColor(R.color.text))
		usernameTextView?.maxLines = 1
		usernameTextView?.setLines(1)
		usernameTextView?.background = null
		usernameTextView?.setPadding(0, 0, 0, 0)
		usernameTextView?.isSingleLine = true
		usernameTextView?.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
		usernameTextView?.imeOptions = EditorInfo.IME_ACTION_DONE
		usernameTextView?.hint = context.getString(R.string.ChannelUsernamePlaceholder)
		usernameTextView?.setCursorColor(context.getColor(R.color.text))
		usernameTextView?.setCursorSize(AndroidUtilities.dp(20f))
		usernameTextView?.setCursorWidth(1.5f)

		usernameTextView?.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
			source.toString().filter { ('0'..'9').contains(it) || ('a'..'z').contains(it) }
		}, InputFilter.LengthFilter(MAX_LINK_LENGTH))

		publicContainer?.addView(usernameTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36))

		usernameTextView?.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
				// unused
			}

			override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
				if (ignoreTextChanges) {
					return
				}

				checkUserName(usernameTextView?.text?.toString())
			}

			override fun afterTextChanged(editable: Editable) {
				checkDoneButton()
			}
		})

		privateContainer = LinearLayout(context)
		privateContainer?.orientation = LinearLayout.VERTICAL

		linkContainer?.addView(privateContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		val type = if (ChatObject.isChannel(currentChat)) {
			LinkActionType.CHANNEL
		}
		else {
			LinkActionType.GROUP
		}

		permanentLinkView = LinkActionView(context, this, null, true)

		permanentLinkView?.delegate = object : LinkActionView.Delegate {
			override fun showQr() {
				// unused
			}

			override fun editLink() {
				// unused
			}

			override fun removeLink() {
				// unused
			}

			override fun revokeLink() {
				generateLink(true)
			}

			override fun showUsersForPermanentLink() {
				inviteLinkBottomSheet = InviteLinkBottomSheet(context, invite, info, usersMap, this@ChatEditTypeActivity, chatId, true, ChatObject.isChannel(currentChat))
				inviteLinkBottomSheet?.show()
			}
		}

		permanentLinkView?.setUsers(0, null)
		privateContainer?.addView(permanentLinkView)

		checkTextView = TextInfoPrivacyCell(context)
		checkTextView?.background = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, context.getColor(R.color.shadow))
		checkTextView?.setBottomPadding(6)

		linearLayout?.addView(checkTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

		typeInfoCell = TextInfoPrivacyCell(context)
		typeInfoCell?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

		linearLayout?.addView(typeInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		loadingAdministeredCell = LoadingCell(context)

		linearLayout?.addView(loadingAdministeredCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		administeredChannelsLayout = LinearLayout(context)
		administeredChannelsLayout?.setBackgroundResource(R.color.background)
		administeredChannelsLayout?.orientation = LinearLayout.VERTICAL

		linearLayout?.addView(administeredChannelsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		administeredInfoCell = ShadowSectionCell(context)

		linearLayout?.addView(administeredInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		manageLinksTextView = TextCell(context)
		manageLinksTextView?.background = Theme.getSelectorDrawable(true)
		manageLinksTextView?.setTextAndIcon(context.getString(R.string.ManageInviteLinks), R.drawable.msg_link2, false)

		manageLinksTextView?.setOnClickListener {
			val fragment = ManageLinksActivity(chatId, 0, 0)
			fragment.setInfo(info, invite)
			presentFragment(fragment)
		}

		linearLayout?.addView(manageLinksTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		manageLinksInfoCell = TextInfoPrivacyCell(context)

		linearLayout?.addView(manageLinksInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		joinContainer = JoinToSendSettingsView(context, currentChat)
		joinContainer?.showJoinToSend(info != null && info?.linked_chat_id != 0L)

		linearLayout?.addView(joinContainer)

		saveContainer = LinearLayout(context)
		saveContainer?.orientation = LinearLayout.VERTICAL

		linearLayout?.addView(saveContainer)

		saveHeaderCell = HeaderCell(context, 23)
		saveHeaderCell?.height = 46
		saveHeaderCell?.setText(context.getString(R.string.SavingContentTitle, (if (isChannel) context.getString(R.string.channel) else context.getString(R.string.group)).lowercase(Locale.getDefault())))
		saveHeaderCell?.background = Theme.getSelectorDrawable(true)

		saveContainer?.addView(saveHeaderCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		saveRestrictCell = TextCheckCell(context)
		saveRestrictCell?.background = Theme.getSelectorDrawable(true)
		saveRestrictCell?.setTextAndCheck(context.getString(R.string.RestrictSavingContent), isSaveRestricted, false)

		saveRestrictCell?.setOnClickListener {
			isSaveRestricted = !isSaveRestricted
			(it as TextCheckCell).isChecked = isSaveRestricted
		}

		saveContainer?.addView(saveRestrictCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		saveRestrictInfoCell = TextInfoPrivacyCell(context)

		if (isChannel && !ChatObject.isMegagroup(currentChat)) {
			saveRestrictInfoCell?.setText(context.getString(R.string.RestrictSavingContentInfoChannel))
		}
		else {
			saveRestrictInfoCell?.setText(context.getString(R.string.RestrictSavingContentInfoGroup))
		}

		saveContainer?.addView(saveRestrictInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		if (!isPrivate && currentChat!!.username != null) {
			ignoreTextChanges = true

			usernameTextView?.setText(currentChat?.username)

			usernameTextView?.setSelection(currentChat?.username?.length ?: 0)

			ignoreTextChanges = false
		}

		updatePrivatePublic()

		return fragmentView
	}

	private fun showPremiumIncreaseLimitDialog() {
		if (parentActivity == null) {
			return
		}

		val limitReachedBottomSheet = LimitReachedBottomSheet(this, LimitReachedBottomSheet.TYPE_PUBLIC_LINKS, currentAccount)
		limitReachedBottomSheet.parentIsChannel = isChannel

		limitReachedBottomSheet.onSuccessRunnable = Runnable {
			canCreatePublic = true
			updatePrivatePublic()
		}

		showDialog(limitReachedBottomSheet)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.chatInfoDidLoad) {
			val chatFull = args[0] as ChatFull

			if (chatFull.id == chatId) {
				info = chatFull
				invite = chatFull.exported_invite
				updatePrivatePublic()
			}
		}
	}

	fun setInfo(chatFull: ChatFull?) {
		info = chatFull

		if (chatFull != null) {
			if (chatFull.exported_invite != null) {
				invite = chatFull.exported_invite
			}
			else {
				generateLink(false)
			}
		}
	}

	private fun processDone() {
		if (currentChat?.noforwards != isSaveRestricted) {
			messagesController.toggleChatNoForwards(chatId, isSaveRestricted.also { currentChat?.noforwards = it })
		}

		if (trySetUsername()) { // && tryUpdateJoinSettings()
			finishFragment()
		}
	}

//	private fun tryUpdateJoinSettings(): Boolean {
//		if (isChannel || joinContainer == null || !isPrivate) {
//			return true
//		}
//
//		if (parentActivity == null) {
//			return false
//		}
//
//		val needToMigrate = !ChatObject.isChannel(currentChat) && (joinContainer!!.isJoinToSend || joinContainer!!.isJoinRequest)
//
//		return if (needToMigrate) {
//			messagesController.convertToMegaGroup(parentActivity, chatId, this) {
//				if (it != 0L) {
//					chatId = it
//					currentChat = messagesController.getChat(it)
//					processDone()
//				}
//			}
//
//			false
//		}
//		else {
//			if (currentChat?.join_to_send != joinContainer?.isJoinToSend) {
//				messagesController.toggleChatJoinToSend(chatId, joinContainer!!.isJoinToSend.also { currentChat!!.join_to_send = it }, null, null)
//			}
//
//			if (currentChat?.join_request != joinContainer?.isJoinRequest) {
//				messagesController.toggleChatJoinRequest(chatId, joinContainer!!.isJoinRequest.also { currentChat!!.join_request = it }, null, null)
//			}
//
//			true
//		}
//	}

	private fun trySetUsername(): Boolean {
		val parentActivity = parentActivity ?: return false
		val currentChat = currentChat ?: return false

		if (!isPrivate && (currentChat.username == null && usernameTextView?.length() != 0 || currentChat.username != null && !currentChat.username.equals(usernameTextView?.text?.toString(), ignoreCase = true))) {
			if (usernameTextView?.length() != 0 && !lastNameAvailable) {
				parentActivity.vibrate()
				AndroidUtilities.shakeView(checkTextView, 2f, 0)
				return false
			}
		}

		val oldUserName = currentChat.username ?: ""
		val newUserName = if (isPrivate) "" else (usernameTextView?.text?.toString() ?: "")

		if (oldUserName != newUserName) {
			messagesController.updateChannelUserName(chatId, newUserName)
			currentChat.username = newUserName
		}

		return true
	}

	private fun loadAdministeredChannels() {
		if (loadingAdministeredChannels || administeredChannelsLayout == null) {
			return
		}

		loadingAdministeredChannels = true

		updatePrivatePublic()

		val req = TL_channels_getAdminedPublicChannels()

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				loadingAdministeredChannels = false

				if (response != null) {
					val parentActivity = parentActivity ?: return@runOnUIThread

					administeredChannelCells.forEach {
						linearLayout?.removeView(it)
					}

					administeredChannelCells.clear()

					val res = response as TL_messages_chats

					for (a in res.chats.indices) {
						val administeredChannelCell = AdministeredChannelCell(parentActivity, false, 0) { view: View ->
							val cell = view.parent as AdministeredChannelCell
							val channel = cell.currentChannel

							val builder = AlertDialog.Builder(parentActivity)
							builder.setTitle(parentActivity.getString(R.string.AppName))

							if (isChannel) {
								builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlertChannel", R.string.RevokeLinkAlertChannel, messagesController.linkPrefix + "/" + channel?.username, channel?.title)))
							}
							else {
								builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlert", R.string.RevokeLinkAlert, messagesController.linkPrefix + "/" + channel?.username, channel?.title)))
							}

							builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

							builder.setPositiveButton(parentActivity.getString(R.string.RevokeButton)) { _, _ ->
								val req1 = TL_channels_updateUsername()
								req1.channel = MessagesController.getInputChannel(channel)
								req1.username = ""

								connectionsManager.sendRequest(req1, { response1, _ ->
									if (response1 is TL_boolTrue) {
										AndroidUtilities.runOnUIThread {
											canCreatePublic = true

											if ((usernameTextView?.length() ?: 0) > 0) {
												checkUserName(usernameTextView?.text?.toString())
											}

											updatePrivatePublic()
										}
									}
								}, ConnectionsManager.RequestFlagInvokeAfter)
							}

							showDialog(builder.create())
						}

						administeredChannelCell.setChannel(res.chats[a], a == res.chats.size - 1)

						administeredChannelCells.add(administeredChannelCell)

						administeredChannelsLayout?.addView(administeredChannelCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 72))
					}

					updatePrivatePublic()
				}
			}
		}
	}

	private fun updatePrivatePublic() {
		val context = context ?: return
		val sectionCell2 = sectionCell2 ?: return

		if (ChatObject.isPaidChannel(currentChat)) {
			manageLinksInfoCell?.gone()
			manageLinksTextView?.gone()
		}
		else {
			manageLinksInfoCell?.visible()
			manageLinksTextView?.visible()
		}

		if (!isPrivate && !canCreatePublic && userConfig.isPremium) {
			typeInfoCell?.setText(context.getString(R.string.ChangePublicLimitReached))
			typeInfoCell?.setTextColor(context.getColor(R.color.purple))

			linkContainer?.gone()
			checkTextView?.gone()
			sectionCell2.gone()

			administeredInfoCell?.visible()

			if (loadingAdministeredChannels) {
				loadingAdministeredCell?.visible()
				administeredChannelsLayout?.gone()

				typeInfoCell?.background = if (checkTextView?.visibility == View.VISIBLE) null else Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, context.getColor(R.color.shadow))
				administeredInfoCell?.background = null
			}
			else {
				administeredInfoCell?.background = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, context.getColor(R.color.shadow))
				typeInfoCell?.background = Theme.getThemedDrawable(context, R.drawable.greydivider_top, context.getColor(R.color.shadow))

				loadingAdministeredCell?.gone()
				administeredChannelsLayout?.visible()
			}
		}
		else {
			typeInfoCell?.setTextColor(context.getColor(R.color.dark_gray))

			if (isForcePublic) {
				sectionCell2.gone()
			}
			else {
				sectionCell2.visible()
			}

			administeredInfoCell?.gone()

			typeInfoCell?.background = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, context.getColor(R.color.shadow))
			administeredChannelsLayout?.gone()

			linkContainer?.visible()

			loadingAdministeredCell?.gone()

			if (isChannel) {
				typeInfoCell?.setText(if (isPrivate) context.getString(R.string.ChannelPrivateLinkHelp) else context.getString(R.string.ChannelUsernameHelp))
				headerCell?.setText(if (isPrivate) context.getString(R.string.ChannelInviteLinkTitle) else context.getString(R.string.ChannelLinkTitle))
			}
			else {
				typeInfoCell?.setText(if (isPrivate) context.getString(R.string.MegaPrivateLinkHelp) else context.getString(R.string.MegaUsernameHelp))
				headerCell?.setText(if (isPrivate) context.getString(R.string.ChannelInviteLinkTitle) else context.getString(R.string.ChannelLinkTitle))
			}

			publicContainer?.visibility = if (isPrivate) View.GONE else View.VISIBLE
			privateContainer?.visibility = if (isPrivate) View.VISIBLE else View.GONE
			saveContainer?.visible()

			linkContainer?.setPadding(0, 0, 0, if (isPrivate) 0 else AndroidUtilities.dp(7f))

			permanentLinkView?.setLink(invite?.link)
			permanentLinkView?.loadUsers(invite, chatId)

			checkTextView?.visibility = if (!isPrivate && checkTextView!!.length() != 0) View.VISIBLE else View.GONE

			manageLinksInfoCell?.setText(context.getString(R.string.ManageLinksInfoHelp))

			if (isPrivate) {
				typeInfoCell?.background = Theme.getThemedDrawable(context, R.drawable.greydivider, context.getColor(R.color.shadow))
				manageLinksInfoCell?.background = Theme.getThemedDrawable(typeInfoCell!!.context, R.drawable.greydivider_bottom, context.getColor(R.color.shadow))
			}
			else {
				typeInfoCell?.background = if (checkTextView?.visibility == View.VISIBLE) null else Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, context.getColor(R.color.shadow))
			}
		}

		radioButtonCell1?.setChecked(!isPrivate)
		radioButtonCell2?.setChecked(isPrivate)
		usernameTextView?.clearFocus()

		joinContainer?.gone()
		joinContainer?.showJoinToSend(info != null && info?.linked_chat_id != 0L)

		checkDoneButton()
	}

	private fun checkDoneButton() {
		if (isPrivate || (usernameTextView?.length() ?: 0) > 0) {
			doneButton?.isEnabled = true
			doneButton?.alpha = 1.0f
		}
		else {
			doneButton?.isEnabled = false
			doneButton?.alpha = 0.5f
		}
	}

	private fun checkUserName(name: String?): Boolean {
		val context = context ?: return false

		if (!name.isNullOrEmpty()) {
			checkTextView?.visible()
		}
		else {
			checkTextView?.gone()
		}

		typeInfoCell?.background = if (checkTextView?.visibility == View.VISIBLE) null else Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, context.getColor(R.color.shadow))

		if (checkRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(checkRunnable)

			checkRunnable = null
			lastCheckName = null

			if (checkReqId != 0) {
				connectionsManager.cancelRequest(checkReqId, true)
			}
		}

		lastNameAvailable = false

		if (name != null) {
			if (name.startsWith("_") || name.endsWith("_")) {
				checkTextView?.setText(context.getString(R.string.LinkInvalid))
				checkTextView?.setTextColor(context.getColor(R.color.purple))
				return false
			}

			for (a in name.indices) {
				val ch = name[a]

				if (a == 0 && ch >= '0' && ch <= '9') {
					if (isChannel) {
						checkTextView?.setText(context.getString(R.string.LinkInvalidStartNumber))
					}
					else {
						checkTextView?.setText(context.getString(R.string.LinkInvalidStartNumberMega))
					}

					checkTextView?.setTextColor(context.getColor(R.color.purple))

					return false
				}

				if (!(ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z' || ch == '_')) {
					checkTextView?.setText(context.getString(R.string.LinkInvalid))
					checkTextView?.setTextColor(context.getColor(R.color.purple))
					return false
				}
			}
		}

		if (name == null || name.length < 5) {
			if (isChannel) {
				checkTextView?.setText(context.getString(R.string.LinkInvalidShort))
			}
			else {
				checkTextView?.setText(context.getString(R.string.LinkInvalidShortMega))
			}

			checkTextView?.setTextColor(context.getColor(R.color.purple))

			return false
		}

		if (name.length > 32) {
			checkTextView?.setText(context.getString(R.string.LinkInvalidLong))
			checkTextView?.setTextColor(context.getColor(R.color.purple))
			return false
		}

		checkTextView?.setText(context.getString(R.string.LinkChecking))
		checkTextView?.setTextColor(context.getColor(R.color.light_gray))

		lastCheckName = name

		checkRunnable = Runnable {
			val req = TL_channels_checkUsername()
			req.username = name
			req.channel = messagesController.getInputChannel(chatId)

			checkReqId = connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					checkReqId = 0

					if (lastCheckName != null && lastCheckName == name) {
						if (error == null && response is TL_boolTrue) {
							checkTextView?.setText(LocaleController.formatString("LinkAvailable", R.string.LinkAvailable))
							checkTextView?.setTextColor(context.getColor(R.color.green))
							lastNameAvailable = true
						}
						else {
							if (error != null && error.text == "CHANNELS_ADMIN_PUBLIC_TOO_MUCH") {
								canCreatePublic = false
								showPremiumIncreaseLimitDialog()
							}
							else {
								checkTextView?.setText(context.getString(R.string.LinkInUse))
							}

							checkTextView?.setTextColor(context.getColor(R.color.purple))

							lastNameAvailable = false
						}
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}

		AndroidUtilities.runOnUIThread(checkRunnable, 300)

		return true
	}

	private fun generateLink(newRequest: Boolean) {
		loadingInvite = true

		val req = TL_messages_exportChatInvite()
		req.legacy_revoke_permanent = true
		req.peer = messagesController.getInputPeer(-chatId)

		val reqId = connectionsManager.sendRequest(req) { response, error ->
			AndroidUtilities.runOnUIThread {
				if (error == null) {
					invite = response as TL_chatInviteExported?

					info?.exported_invite = invite

					if (newRequest) {
						val parentActivity = parentActivity ?: return@runOnUIThread

						val builder = AlertDialog.Builder(parentActivity)
						builder.setMessage(parentActivity.getString(R.string.RevokeAlertNewLink))
						builder.setTitle(parentActivity.getString(R.string.RevokeLink))
						builder.setNegativeButton(parentActivity.getString(R.string.OK), null)

						showDialog(builder.create())
					}
				}

				loadingInvite = false

				if (permanentLinkView != null) {
					permanentLinkView?.setLink(invite?.link)
					permanentLinkView?.loadUsers(invite, chatId)
				}
			}
		}

		connectionsManager.bindRequestToGuid(reqId, classGuid)
	}

	companion object {
		private const val DONE_BUTTON = 1
		private const val MAX_LINK_LENGTH = 32
	}
}
