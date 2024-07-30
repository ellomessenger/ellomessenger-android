/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024
 */
package org.telegram.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.util.SparseIntArray
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.collection.LongSparseArray
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog.e
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserObject
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC.ChannelParticipant
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.ChatParticipant
import org.telegram.tgnet.TLRPC.TL_channelFull
import org.telegram.tgnet.TLRPC.TL_channelParticipant
import org.telegram.tgnet.TLRPC.TL_channelParticipantAdmin
import org.telegram.tgnet.TLRPC.TL_channelParticipantBanned
import org.telegram.tgnet.TLRPC.TL_channelParticipantCreator
import org.telegram.tgnet.TLRPC.TL_channelParticipantSelf
import org.telegram.tgnet.TLRPC.TL_channelParticipantsAdmins
import org.telegram.tgnet.TLRPC.TL_channelParticipantsBanned
import org.telegram.tgnet.TLRPC.TL_channelParticipantsBots
import org.telegram.tgnet.TLRPC.TL_channelParticipantsContacts
import org.telegram.tgnet.TLRPC.TL_channelParticipantsKicked
import org.telegram.tgnet.TLRPC.TL_channelParticipantsRecent
import org.telegram.tgnet.TLRPC.TL_channels_getParticipants
import org.telegram.tgnet.TLRPC.TL_chatAdminRights
import org.telegram.tgnet.TLRPC.TL_chatParticipant
import org.telegram.tgnet.TLRPC.TL_chatParticipantAdmin
import org.telegram.tgnet.TLRPC.TL_chatParticipantCreator
import org.telegram.tgnet.TLRPC.TL_groupCallParticipant
import org.telegram.tgnet.TLRPC.TL_peerChannel
import org.telegram.tgnet.TLRPC.TL_peerUser
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_channels_channelParticipants
import org.telegram.tgnet.tlrpc.TL_channels_editBanned
import org.telegram.tgnet.tlrpc.TL_chatBannedRights
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.SearchAdapterHelper
import org.telegram.ui.Adapters.SearchAdapterHelper.HashtagObject
import org.telegram.ui.Adapters.SearchAdapterHelper.SearchAdapterHelperDelegate
import org.telegram.ui.Cells.GraySectionCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.LoadingCell
import org.telegram.ui.Cells.ManageChatTextCell
import org.telegram.ui.Cells.ManageChatUserCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextCheckCell2
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.ChatRightsEditActivity.ChatRightsEditActivityDelegate
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.GigagroupConvertAlert
import org.telegram.ui.Components.IntSeekBarAccessibilityDelegate
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.Components.SeekBarAccessibilityDelegate
import org.telegram.ui.Components.StickerEmptyView
import org.telegram.ui.Components.UndoView
import org.telegram.ui.channel.ChannelAdminLogActivity
import org.telegram.ui.group.GroupCreateActivity
import org.telegram.ui.group.GroupCreateActivity.ContactsAddActivityDelegate
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
class ChatUsersActivity(args: Bundle) : BaseFragment(args), NotificationCenterDelegate {
	private val isChannel: Boolean
	private val initialBannedRights: String
	private val defaultBannedRights = TL_chatBannedRights()
	private val participants = ArrayList<TLObject>()
	private val bots = ArrayList<TLObject>()
	private val contacts = ArrayList<TLObject>()
	private val participantsMap = LongSparseArray<TLObject?>()
	private val botsMap = LongSparseArray<TLObject?>()
	private val contactsMap = LongSparseArray<TLObject?>()
	private val type: Int
	val selectType: Int
	private val needOpenSearch: Boolean
	private var listViewAdapter: ListAdapter? = null
	private var emptyView: StickerEmptyView? = null
	private var listView: RecyclerListView? = null
	private var layoutManager: LinearLayoutManager? = null
	private var searchListViewAdapter: SearchAdapter? = null
	private var searchItem: ActionBarMenuItem? = null
	private var doneItem: ActionBarMenuItem? = null
	private var undoView: UndoView? = null
	private var currentChat: Chat?
	private var info: ChatFull? = null
	private var botsEndReached = false
	private var contactsEndReached = false
	private var chatId: Long
	private var loadingUsers = false
	private var firstLoaded = false
	private var ignoredUsers: LongSparseArray<TL_groupCallParticipant>? = null
	private var permissionsSectionRow = 0
	private var sendMessagesRow = 0
	private var sendMediaRow = 0
	private var sendStickersRow = 0
	private var sendPollsRow = 0
	private var embedLinksRow = 0
	private var changeInfoRow = 0
	private var addUsersRow = 0
	private var pinMessagesRow = 0
	private var gigaHeaderRow = 0
	private var gigaConvertRow = 0
	private var gigaInfoRow = 0
	private var recentActionsRow = 0
	private var addNewRow = 0
	private var addNew2Row = 0
	private var removedUsersRow = 0
	private var addNewSectionRow = 0
	private var restricted1SectionRow = 0
	private var participantsStartRow = 0
	private var participantsEndRow = 0
	private var participantsDividerRow = 0
	private var participantsDivider2Row = 0
	private var slowmodeRow = 0
	private var slowmodeSelectRow = 0
	private var slowmodeInfoRow = 0
	private var contactsHeaderRow = 0
	private var contactsStartRow = 0
	private var contactsEndRow = 0
	private var botHeaderRow = 0
	private var botStartRow = 0
	private var botEndRow = 0
	private var membersHeaderRow = 0
	private var loadingProgressRow = 0
	private var participantsInfoRow = 0
	private var blockedEmptyRow = 0
	private var rowCount = 0
	private var loadingUserCellRow = 0
	private var loadingHeaderRow = 0
	private var delayResults = 0
	private var delegate: ChatUsersActivityDelegate? = null
	private var searching = false
	private var selectedSlowMode = 0
	private var initialSlowMode = 0
	private var openTransitionStarted = false
	private var flickerLoadingView: FlickerLoadingView? = null
	private var progressBar: View? = null

	init {
		chatId = arguments?.getLong("chat_id") ?: 0L
		type = arguments?.getInt("type") ?: 0
		needOpenSearch = arguments?.getBoolean("open_search") ?: false
		selectType = arguments?.getInt("selectType") ?: 0
		currentChat = messagesController.getChat(chatId)

		currentChat?.let {
			if (it.default_banned_rights != null) {
				defaultBannedRights.view_messages = it.default_banned_rights.view_messages
				defaultBannedRights.send_stickers = it.default_banned_rights.send_stickers
				defaultBannedRights.send_media = it.default_banned_rights.send_media
				defaultBannedRights.embed_links = it.default_banned_rights.embed_links
				defaultBannedRights.send_messages = it.default_banned_rights.send_messages
				defaultBannedRights.send_games = it.default_banned_rights.send_games
				defaultBannedRights.send_inline = it.default_banned_rights.send_inline
				defaultBannedRights.send_gifs = it.default_banned_rights.send_gifs
				defaultBannedRights.pin_messages = it.default_banned_rights.pin_messages
				defaultBannedRights.send_polls = it.default_banned_rights.send_polls
				defaultBannedRights.invite_users = it.default_banned_rights.invite_users
				defaultBannedRights.change_info = it.default_banned_rights.change_info
			}
		}

		initialBannedRights = ChatObject.getBannedRightsString(defaultBannedRights)
		isChannel = ChatObject.isChannel(currentChat) && currentChat?.megagroup != true
	}

	private fun updateRows() {
		val currentChat = messagesController.getChat(chatId).also {
			this.currentChat = it
		} ?: return

		recentActionsRow = -1
		addNewRow = -1
		addNew2Row = -1
		addNewSectionRow = -1
		restricted1SectionRow = -1
		participantsStartRow = -1
		participantsDividerRow = -1
		participantsDivider2Row = -1
		gigaInfoRow = -1
		gigaConvertRow = -1
		gigaHeaderRow = -1
		participantsEndRow = -1
		participantsInfoRow = -1
		blockedEmptyRow = -1
		permissionsSectionRow = -1
		sendMessagesRow = -1
		sendMediaRow = -1
		sendStickersRow = -1
		sendPollsRow = -1
		embedLinksRow = -1
		addUsersRow = -1
		pinMessagesRow = -1
		changeInfoRow = -1
		removedUsersRow = -1
		contactsHeaderRow = -1
		contactsStartRow = -1
		contactsEndRow = -1
		botHeaderRow = -1
		botStartRow = -1
		botEndRow = -1
		membersHeaderRow = -1
		slowmodeRow = -1
		slowmodeSelectRow = -1
		slowmodeInfoRow = -1
		loadingProgressRow = -1
		loadingUserCellRow = -1
		loadingHeaderRow = -1
		rowCount = 0

		if (type == TYPE_KICKED) {
			permissionsSectionRow = rowCount++
			sendMessagesRow = rowCount++
			sendMediaRow = rowCount++
			// sendStickersRow = rowCount++
			// sendPollsRow = rowCount++
			embedLinksRow = rowCount++
			addUsersRow = rowCount++
			// MARK: uncomment to enable pinned messages
			// pinMessagesRow = rowCount++
			changeInfoRow = rowCount++

			if (ChatObject.isChannel(currentChat) && currentChat.creator && currentChat.megagroup && !currentChat.gigagroup) {
				val count = max(currentChat.participants_count, info?.participants_count ?: 0)

				if (count >= messagesController.maxMegagroupCount - 1000) {
					participantsDivider2Row = rowCount++
					gigaHeaderRow = rowCount++
					gigaConvertRow = rowCount++
					gigaInfoRow = rowCount++
				}
			}

			if (!ChatObject.isChannel(currentChat) && currentChat.creator || currentChat.megagroup && !currentChat.gigagroup && ChatObject.canBlockUsers(currentChat)) {
				if (participantsDivider2Row == -1) {
					participantsDivider2Row = rowCount++
				}

//				slowmodeRow = rowCount++
//				slowmodeSelectRow = rowCount++
//				slowmodeInfoRow = rowCount++
			}

			if (ChatObject.isChannel(currentChat)) {
				if (participantsDivider2Row == -1) {
					participantsDivider2Row = rowCount++
				}

				removedUsersRow = rowCount++
			}

			if (slowmodeInfoRow == -1 && gigaHeaderRow == -1 || removedUsersRow != -1) {
				participantsDividerRow = rowCount++
			}

			if (ChatObject.canBlockUsers(currentChat) && (ChatObject.isChannel(currentChat) || currentChat.creator)) {
				addNewRow = rowCount++
			}

			if (loadingUsers && !firstLoaded) {
				if ((info?.banned_count ?: 0) > 0) {
					loadingUserCellRow = rowCount++
				}
			}
			else {
				if (participants.isNotEmpty()) {
					participantsStartRow = rowCount
					rowCount += participants.size
					participantsEndRow = rowCount
				}

				if (addNewRow != -1 || participantsStartRow != -1) {
					addNewSectionRow = rowCount++
				}
			}
		}
		else if (type == TYPE_BANNED) {
			if (ChatObject.canBlockUsers(currentChat)) {

				addNewRow = rowCount++

				if (participants.isNotEmpty() || loadingUsers && !firstLoaded && (info?.kicked_count ?: 0) > 0) {
					participantsInfoRow = rowCount++
				}
			}

			if (!(loadingUsers && !firstLoaded)) {
				if (participants.isNotEmpty()) {
					restricted1SectionRow = rowCount++
					participantsStartRow = rowCount
					rowCount += participants.size
					participantsEndRow = rowCount
				}
				if (participantsStartRow != -1) {
					if (participantsInfoRow == -1) {
						participantsInfoRow = rowCount++
					}
					else {
						addNewSectionRow = rowCount++
					}
				}
				else {
					//restricted1SectionRow = rowCount++;
					blockedEmptyRow = rowCount++
				}
			}
			else {
				restricted1SectionRow = rowCount++
				loadingUserCellRow = rowCount++
			}
		}
		else if (type == TYPE_ADMIN) {
			if (ChatObject.isChannel(currentChat) && currentChat.megagroup && !currentChat.gigagroup && (info == null || (info?.participants_count ?: 0) <= 200 || !isChannel && info?.can_set_stickers == true)) {
				// MARK: uncomment to enable admin log screen
				// recentActionsRow = rowCount++
				// addNewSectionRow = rowCount++
			}

			if (ChatObject.canAddAdmins(currentChat)) {
				addNewRow = rowCount++
			}

			if (!(loadingUsers && !firstLoaded)) {
				if (participants.isNotEmpty()) {
					participantsStartRow = rowCount
					rowCount += participants.size
					participantsEndRow = rowCount
				}

				participantsInfoRow = rowCount++
			}
			else {
				loadingUserCellRow = rowCount++
			}
		}
		else if (type == TYPE_USERS) {
			if (selectType == SELECT_TYPE_MEMBERS && ChatObject.canAddUsers(currentChat) && !ChatObject.isPaidChannel(currentChat)) {
				addNewRow = rowCount++
			}

			if (selectType == SELECT_TYPE_MEMBERS && ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE)) {
				// MARK: remove this check to enable invite links for paid channels and courses
				if (!ChatObject.isPaidChannel(currentChat) && !ChatObject.isOnlineCourse(currentChat) && !ChatObject.isSubscriptionChannel(currentChat)) {
					addNew2Row = rowCount++
				}
			}

			if (!(loadingUsers && !firstLoaded)) {
				var hasAnyOther = false

				if (contacts.isNotEmpty()) {
					contactsHeaderRow = rowCount++
					contactsStartRow = rowCount
					rowCount += contacts.size
					contactsEndRow = rowCount
					hasAnyOther = true
				}

				if (bots.isNotEmpty()) {
					botHeaderRow = rowCount++
					botStartRow = rowCount
					rowCount += bots.size
					botEndRow = rowCount
					hasAnyOther = true
				}

				if (participants.isNotEmpty()) {
					if (hasAnyOther) {
						membersHeaderRow = rowCount++
					}

					participantsStartRow = rowCount
					rowCount += participants.size
					participantsEndRow = rowCount
				}

				if (rowCount != 0) {
					participantsInfoRow = rowCount++
				}
			}
			else {
				if (selectType == SELECT_TYPE_MEMBERS) {
					loadingHeaderRow = rowCount++
				}

				loadingUserCellRow = rowCount++
			}
		}
	}

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()
		notificationCenter.addObserver(this, NotificationCenter.chatInfoDidLoad)
		loadChatParticipants(0, 200)
		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		notificationCenter.removeObserver(this, NotificationCenter.chatInfoDidLoad)
	}

	override fun createView(context: Context): View? {
		searching = false
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		if (type == TYPE_KICKED) {
			actionBar?.setTitle(context.getString(R.string.ChannelPermissions))
		}
		else if (type == TYPE_BANNED) {
			actionBar?.setTitle(context.getString(R.string.ChannelBlacklist))
		}
		else if (type == TYPE_ADMIN) {
			actionBar?.setTitle(context.getString(R.string.ChannelAdministrators))
		}
		else if (type == TYPE_USERS) {
			if (selectType == SELECT_TYPE_MEMBERS) {
				if (isChannel) {
					actionBar?.setTitle(context.getString(R.string.ChannelSubscribers))
				}
				else {
					actionBar?.setTitle(context.getString(R.string.ChannelMembers))
				}
			}
			else {
				when (selectType) {
					SELECT_TYPE_ADMIN -> {
						actionBar?.setTitle(context.getString(R.string.ChannelAddAdmin))
					}

					SELECT_TYPE_BLOCK -> {
						actionBar?.setTitle(context.getString(R.string.ChannelBlockUser))
					}

					SELECT_TYPE_EXCEPTION -> {
						actionBar?.setTitle(context.getString(R.string.ChannelAddException))
					}
				}
			}
		}

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						if (checkDiscard()) {
							finishFragment()
						}
					}

					DONE_BUTTON -> {
						processDone()
					}
				}
			}
		})

		if (selectType != SELECT_TYPE_MEMBERS || type == TYPE_USERS || type == TYPE_BANNED || type == TYPE_KICKED) {
			searchListViewAdapter = SearchAdapter(context)

			val menu = actionBar?.createMenu()

			searchItem = menu?.addItem(SEARCH_BUTTON, R.drawable.ic_search_menu)?.setIsSearchField(true)?.setActionBarMenuItemSearchListener(object : ActionBarMenuItemSearchListener() {
				override fun onSearchExpand() {
					searching = true
					doneItem?.gone()
				}

				override fun onSearchCollapse() {
					searchListViewAdapter?.searchUsers(null)

					searching = false

					listView?.setAnimateEmptyView(false, 0)
					listView?.adapter = listViewAdapter

					listViewAdapter?.notifyDataSetChanged()

					listView?.setFastScrollVisible(true)
					listView?.isVerticalScrollBarEnabled = false

					doneItem?.visible()
				}

				override fun onTextChanged(editText: EditText) {
					if (searchListViewAdapter == null) {
						return
					}

					val text = editText.text?.toString()
					val oldItemsCount = listView?.adapter?.itemCount ?: 0

					searchListViewAdapter?.searchUsers(text)

					if (text.isNullOrEmpty() && listView != null && listView?.adapter !== listViewAdapter) {
						listView?.setAnimateEmptyView(false, 0)
						listView?.adapter = listViewAdapter

						if (oldItemsCount == 0) {
							showItemsAnimated(0)
						}
					}

					progressBar?.gone()
					flickerLoadingView?.visible()
				}
			})

			if (type == TYPE_BANNED && !firstLoaded) {
				searchItem?.gone()
			}

			if (type == TYPE_KICKED) {
				searchItem?.setSearchFieldHint(context.getString(R.string.ChannelSearchException))
			}
			else {
				searchItem?.setSearchFieldHint(context.getString(R.string.Search))
			}

			if (!(ChatObject.isChannel(currentChat) || currentChat?.creator == true)) {
				searchItem?.gone()
			}

			if (type == TYPE_KICKED) {
				doneItem = menu?.addItemWithWidth(DONE_BUTTON, R.drawable.ic_ab_done, AndroidUtilities.dp(56f), context.getString(R.string.Done))
			}
		}

		fragmentView = object : FrameLayout(context) {
			override fun dispatchDraw(canvas: Canvas) {
				canvas.drawColor(if (listView?.adapter === searchListViewAdapter) context.getColor(R.color.background) else context.getColor(R.color.light_background))
				super.dispatchDraw(canvas)
			}
		}

		val frameLayout = fragmentView as FrameLayout
		val progressLayout = FrameLayout(context)

		flickerLoadingView = FlickerLoadingView(context)
		flickerLoadingView?.setViewType(FlickerLoadingView.USERS_TYPE)
		flickerLoadingView?.showDate(false)
		flickerLoadingView?.setUseHeaderOffset(true)

		progressLayout.addView(flickerLoadingView)

		progressBar = RadialProgressView(context)

		progressLayout.addView(progressBar, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

		flickerLoadingView?.gone()

		progressBar?.gone()

		emptyView = StickerEmptyView(context, progressLayout, StickerEmptyView.STICKER_TYPE_SEARCH, animationResource = R.raw.panda_chat_list_no_results)
		emptyView?.title?.text = context.getString(R.string.NoResult)
		emptyView?.gone()
		emptyView?.setAnimateLayoutChange(true)
		emptyView?.showProgress(show = true, animated = false)

		frameLayout.addView(emptyView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		emptyView?.addView(progressLayout, 0)

		listView = object : RecyclerListView(context) {
			override fun invalidate() {
				super.invalidate()
				fragmentView?.invalidate()
			}
		}

		listView?.layoutManager = object : LinearLayoutManager(context, VERTICAL, false) {
			override fun scrollVerticallyBy(dy: Int, recycler: Recycler, state: RecyclerView.State): Int {
				return if (!firstLoaded && type == TYPE_BANNED && participants.size == 0) {
					0
				}
				else {
					super.scrollVerticallyBy(dy, recycler, state)
				}
			}
		}.also { layoutManager = it }

		val itemAnimator: DefaultItemAnimator = object : DefaultItemAnimator() {
			var animationIndex = -1

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

			override fun onAllAnimationsDone() {
				super.onAllAnimationsDone()
				notificationCenter.onAnimationFinish(animationIndex)
			}

			override fun runPendingAnimations() {
				val removalsPending = mPendingRemovals.isNotEmpty()
				val movesPending = mPendingMoves.isNotEmpty()
				val changesPending = mPendingChanges.isNotEmpty()
				val additionsPending = mPendingAdditions.isNotEmpty()

				if (removalsPending || movesPending || additionsPending || changesPending) {
					animationIndex = notificationCenter.setAnimationInProgress(animationIndex, null)
				}

				super.runPendingAnimations()
			}
		}

		listView?.itemAnimator = itemAnimator

		itemAnimator.supportsChangeAnimations = false

		listView?.setAnimateEmptyView(true, 0)
		listView?.adapter = ListAdapter(context).also { listViewAdapter = it }

		listView?.verticalScrollbarPosition = if (LocaleController.isRTL) View.SCROLLBAR_POSITION_LEFT else View.SCROLLBAR_POSITION_RIGHT

		frameLayout.addView(listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView?.setOnItemClickListener { view, position ->
			val listAdapter = listView?.adapter === listViewAdapter

			if (listAdapter) {
				when (position) {
					addNewRow -> {
						when (type) {
							TYPE_BANNED, TYPE_KICKED -> {
								val bundle = Bundle()
								bundle.putLong("chat_id", chatId)
								bundle.putInt("type", TYPE_USERS)
								bundle.putInt("selectType", if (type == TYPE_BANNED) SELECT_TYPE_BLOCK else SELECT_TYPE_EXCEPTION)

								val fragment = ChatUsersActivity(bundle)

								fragment.setInfo(info)

								fragment.setDelegate(object : ChatUsersActivityDelegate {
									override fun didAddParticipantToList(uid: Long, participant: TLObject) {
										if (participantsMap[uid] == null) {
											val diffCallback = saveState()

											participants.add(participant)
											participantsMap.put(uid, participant)

											sortUsers(participants)
											updateListAnimated(diffCallback)
										}
									}

									override fun didKickParticipant(userId: Long) {
										if (participantsMap[userId] == null) {
											val diffCallback = saveState()
											val chatParticipant = TL_channelParticipantBanned()

											if (userId > 0) {
												chatParticipant.peer = TL_peerUser()
												chatParticipant.peer.user_id = userId
											}
											else {
												chatParticipant.peer = TL_peerChannel()
												chatParticipant.peer.channel_id = -userId
											}

											chatParticipant.date = connectionsManager.currentTime
											chatParticipant.kicked_by = accountInstance.userConfig.clientUserId

											info?.let {
												it.kicked_count++
											}

											participants.add(chatParticipant)
											participantsMap.put(userId, chatParticipant)

											sortUsers(participants)
											updateListAnimated(diffCallback)
										}
									}
								})

								presentFragment(fragment)
							}

							TYPE_ADMIN -> {
								val bundle = Bundle()
								bundle.putLong("chat_id", chatId)
								bundle.putInt("type", TYPE_USERS)
								bundle.putInt("selectType", SELECT_TYPE_ADMIN)

								val fragment = ChatUsersActivity(bundle)

								fragment.setDelegate(object : ChatUsersActivityDelegate {
									override fun didAddParticipantToList(uid: Long, participant: TLObject) {
										if (participantsMap[uid] == null) {
											val diffCallback = saveState()

											participants.add(participant)
											participantsMap.put(uid, participant)

											sortAdmins(participants)
											updateListAnimated(diffCallback)
										}
									}

									override fun didChangeOwner(user: User) {
										onOwnerChanged(user)
									}

									override fun didSelectUser(uid: Long) {
										val user = messagesController.getUser(uid)

										if (user != null) {
											AndroidUtilities.runOnUIThread({
												if (BulletinFactory.canShowBulletin(this@ChatUsersActivity)) {
													BulletinFactory.createPromoteToAdminBulletin(this@ChatUsersActivity, user.first_name).show()
												}
											}, 200)
										}
										else {
											return
										}

										if (participantsMap[uid] == null) {
											val diffCallback = saveState()

											val chatParticipant = TL_channelParticipantAdmin()
											chatParticipant.peer = TL_peerUser()
											chatParticipant.peer.user_id = user.id
											chatParticipant.date = connectionsManager.currentTime
											chatParticipant.promoted_by = accountInstance.userConfig.clientUserId

											participants.add(chatParticipant)
											participantsMap.put(user.id, chatParticipant)

											sortAdmins(participants)
											updateListAnimated(diffCallback)
										}
									}
								})

								fragment.setInfo(info)
								presentFragment(fragment)
							}

							TYPE_USERS -> {
								val args = Bundle()
								args.putBoolean("addToGroup", true)
								args.putLong(if (isChannel) "channelId" else "chatId", currentChat!!.id)

								val fragment = GroupCreateActivity(args)

								fragment.info = info
								fragment.ignoreUsers = if (contactsMap.size() != 0) contactsMap else participantsMap

								fragment.setDelegate(object : ContactsAddActivityDelegate {
									override fun didSelectUsers(users: List<User>, fwdCount: Int) {
										val count = users.size
										val processed = IntArray(1)
										val userRestrictedPrivacy = ArrayList<User>()

										processed[0] = 0

										val showUserRestrictedPrivacyAlert = Runnable {
											val title: CharSequence
											val description: CharSequence

											when (userRestrictedPrivacy.size) {
												1 -> {
													title = if (count > 1) {
														context.getString(R.string.InviteToGroupErrorTitleAUser)
													}
													else {
														context.getString(R.string.InviteToGroupErrorTitleThisUser)
													}

													description = AndroidUtilities.replaceTags(LocaleController.formatString("InviteToGroupErrorMessageSingle", R.string.InviteToGroupErrorMessageSingle, UserObject.getFirstName(userRestrictedPrivacy[0])))
												}

												2 -> {
													title = context.getString(R.string.InviteToGroupErrorTitleSomeUsers)
													description = AndroidUtilities.replaceTags(LocaleController.formatString("InviteToGroupErrorMessageDouble", R.string.InviteToGroupErrorMessageDouble, UserObject.getFirstName(userRestrictedPrivacy[0]), UserObject.getFirstName(userRestrictedPrivacy[1])))
												}

												count -> {
													title = context.getString(R.string.InviteToGroupErrorTitleTheseUsers)
													description = context.getString(R.string.InviteToGroupErrorMessageMultipleAll)
												}

												else -> {
													title = context.getString(R.string.InviteToGroupErrorTitleSomeUsers)
													description = context.getString(R.string.InviteToGroupErrorMessageMultipleSome)
												}
											}

											AlertDialog.Builder(context).setTitle(title).setMessage(description).setPositiveButton(context.getString(R.string.OK), null).show()
										}

										for (a in 0 until count) {
											val user = users[a]

											messagesController.addUserToChat(chatId, user, fwdCount, null, this@ChatUsersActivity, false, {
												processed[0]++

												if (processed[0] >= count && userRestrictedPrivacy.size > 0) {
													showUserRestrictedPrivacyAlert.run()
												}

												val savedState = saveState()
												val array = if (contactsMap.size() != 0) contacts else participants
												val map = if (contactsMap.size() != 0) contactsMap else participantsMap

												if (map[user.id] == null) {
													if (ChatObject.isChannel(currentChat)) {
														val channelParticipant1 = TL_channelParticipant()
														channelParticipant1.inviter_id = userConfig.getClientUserId()
														channelParticipant1.peer = TL_peerUser()
														channelParticipant1.peer.user_id = user.id
														channelParticipant1.date = connectionsManager.currentTime

														array.add(0, channelParticipant1)

														map.put(user.id, channelParticipant1)
													}
													else {
														val participant: ChatParticipant = TL_chatParticipant()
														participant.user_id = user.id
														participant.inviter_id = userConfig.getClientUserId()

														array.add(0, participant)

														map.put(user.id, participant)
													}
												}

												if (array === participants) {
													sortAdmins(participants)
												}

												updateListAnimated(savedState)
											}) { err ->
												processed[0]++

												var privacyRestricted = false

												if (err != null && ("USER_PRIVACY_RESTRICTED" == err.text).also { privacyRestricted = it }) {
													userRestrictedPrivacy.add(user)
												}

												if (processed[0] >= count && userRestrictedPrivacy.size > 0) {
													showUserRestrictedPrivacyAlert.run()
												}

												!privacyRestricted
											}

											messagesController.putUser(user, false)
										}
									}

									override fun needAddBot(user: User) {
										openRightsEdit(user.id, null, null, null, "", true, ChatRightsEditActivity.TYPE_ADMIN, false)
									}
								})

								presentFragment(fragment)
							}
						}

						return@setOnItemClickListener
					}

					recentActionsRow -> {
						presentFragment(ChannelAdminLogActivity(currentChat))
						return@setOnItemClickListener
					}

					removedUsersRow -> {
						val args = Bundle()
						args.putLong("chat_id", chatId)
						args.putInt("type", TYPE_BANNED)

						val fragment = ChatUsersActivity(args)

						fragment.setInfo(info)

						presentFragment(fragment)

						return@setOnItemClickListener
					}

					gigaConvertRow -> {
						showDialog(object : GigagroupConvertAlert(parentActivity, this@ChatUsersActivity) {
							override fun onCovert() {
								messagesController.convertToGigaGroup(parentActivity, currentChat, this@ChatUsersActivity) { result: Boolean ->
									val parentLayout = parentLayout ?: return@convertToGigaGroup

									if (result) {
										val editActivity = parentLayout.fragmentsStack?.get(parentLayout.fragmentsStack.size - 2)

										if (editActivity is ChatEditActivity) {
											editActivity.removeSelfFromStack()

											val args = Bundle()
											args.putLong("chat_id", chatId)

											val fragment = ChatEditActivity(args)
											fragment.setInfo(info)

											parentLayout.addFragmentToStack(fragment, parentLayout.fragmentsStack.size - 1)

											finishFragment()

											fragment.showConvertTooltip()
										}
										else {
											finishFragment()
										}
									}
								}
							}

							override fun onCancel() {
								// unused
							}
						})
					}

					addNew2Row -> {
						if (info != null) {
							val fragment = ManageLinksActivity(chatId, 0, 0)
							fragment.setInfo(info, info?.exported_invite)
							presentFragment(fragment)
						}

						return@setOnItemClickListener
					}

					in (permissionsSectionRow + 1)..changeInfoRow -> {
						val checkCell = view as TextCheckCell2

						if (!checkCell.isEnabled) {
							return@setOnItemClickListener
						}

						if (checkCell.hasIcon()) {
							if (!currentChat?.username.isNullOrEmpty() && (position == pinMessagesRow || position == changeInfoRow)) {
								BulletinFactory.of(this).createErrorBulletin(context.getString(R.string.EditCantEditPermissionsPublic)).show()
							}
							else {
								BulletinFactory.of(this).createErrorBulletin(context.getString(R.string.EditCantEditPermissions)).show()
							}

							return@setOnItemClickListener
						}

						checkCell.isChecked = !checkCell.isChecked

						if (position == changeInfoRow) {
							defaultBannedRights.change_info = !defaultBannedRights.change_info
						}
						else if (position == addUsersRow) {
							defaultBannedRights.invite_users = !defaultBannedRights.invite_users
						}
						else if (position == pinMessagesRow) {
							defaultBannedRights.pin_messages = !defaultBannedRights.pin_messages
						}
						else {
							val disabled = !checkCell.isChecked

							when (position) {
								sendMessagesRow -> {
									defaultBannedRights.send_messages = !defaultBannedRights.send_messages
								}

								sendMediaRow -> {
									defaultBannedRights.send_media = !defaultBannedRights.send_media
								}

								sendStickersRow -> {
									defaultBannedRights.send_inline = !defaultBannedRights.send_stickers
									defaultBannedRights.send_gifs = defaultBannedRights.send_inline
									defaultBannedRights.send_games = defaultBannedRights.send_gifs
									defaultBannedRights.send_stickers = defaultBannedRights.send_games
								}

								embedLinksRow -> {
									defaultBannedRights.embed_links = !defaultBannedRights.embed_links
								}

								sendPollsRow -> {
									defaultBannedRights.send_polls = !defaultBannedRights.send_polls
								}
							}

							if (disabled) {
								if (defaultBannedRights.view_messages && !defaultBannedRights.send_messages) {
									defaultBannedRights.send_messages = true

									val holder = listView?.findViewHolderForAdapterPosition(sendMessagesRow)

									if (holder != null) {
										(holder.itemView as TextCheckCell2).isChecked = false
									}
								}

								if ((defaultBannedRights.view_messages || defaultBannedRights.send_messages) && !defaultBannedRights.send_media) {
									defaultBannedRights.send_media = true

									val holder = listView?.findViewHolderForAdapterPosition(sendMediaRow)

									if (holder != null) {
										(holder.itemView as TextCheckCell2).isChecked = false
									}
								}

								if ((defaultBannedRights.view_messages || defaultBannedRights.send_messages) && !defaultBannedRights.send_polls) {
									defaultBannedRights.send_polls = true

									val holder = listView?.findViewHolderForAdapterPosition(sendPollsRow)

									if (holder != null) {
										(holder.itemView as TextCheckCell2).isChecked = false
									}
								}

								if ((defaultBannedRights.view_messages || defaultBannedRights.send_messages) && !defaultBannedRights.send_stickers) {
									defaultBannedRights.send_inline = true
									defaultBannedRights.send_gifs = defaultBannedRights.send_inline
									defaultBannedRights.send_games = defaultBannedRights.send_gifs
									defaultBannedRights.send_stickers = defaultBannedRights.send_games

									val holder = listView?.findViewHolderForAdapterPosition(sendStickersRow)

									if (holder != null) {
										(holder.itemView as TextCheckCell2).isChecked = false
									}
								}

								if ((defaultBannedRights.view_messages || defaultBannedRights.send_messages) && !defaultBannedRights.embed_links) {
									defaultBannedRights.embed_links = true

									val holder = listView?.findViewHolderForAdapterPosition(embedLinksRow)

									if (holder != null) {
										(holder.itemView as TextCheckCell2).isChecked = false
									}
								}
							}
							else {
								if ((!defaultBannedRights.embed_links || !defaultBannedRights.send_inline || !defaultBannedRights.send_media || !defaultBannedRights.send_polls) && defaultBannedRights.send_messages) {
									defaultBannedRights.send_messages = false

									val holder = listView?.findViewHolderForAdapterPosition(sendMessagesRow)

									if (holder != null) {
										(holder.itemView as TextCheckCell2).isChecked = true
									}
								}
							}
						}

						return@setOnItemClickListener
					}
				}
			}

			var bannedRights: TL_chatBannedRights? = null
			var adminRights: TL_chatAdminRights? = null
			var rank: String? = ""
			val participant: TLObject?
			var peerId: Long = 0
			var canEditAdmin = false

			if (listAdapter) {
				participant = listViewAdapter?.getItem(position)

				if (participant is ChannelParticipant) {
					peerId = MessageObject.getPeerId(participant.peer)
					bannedRights = participant.banned_rights
					adminRights = participant.admin_rights
					rank = participant.rank
					canEditAdmin = !(participant is TL_channelParticipantAdmin || participant is TL_channelParticipantCreator) || participant.can_edit

					if (participant is TL_channelParticipantCreator) {
						adminRights = participant.admin_rights

						if (adminRights == null) {
							adminRights = TL_chatAdminRights()
							adminRights.add_admins = true
							adminRights.pin_messages = adminRights.add_admins
							adminRights.invite_users = adminRights.pin_messages
							adminRights.ban_users = adminRights.invite_users
							adminRights.delete_messages = adminRights.ban_users
							adminRights.edit_messages = adminRights.delete_messages
							adminRights.post_messages = adminRights.edit_messages
							adminRights.change_info = adminRights.post_messages

							if (!isChannel) {
								adminRights.manage_call = true
							}
						}
					}
				}
				else if (participant is ChatParticipant) {
					peerId = participant.user_id
					canEditAdmin = currentChat?.creator ?: false

					if (participant is TL_chatParticipantCreator) {
						adminRights = TL_chatAdminRights()
						adminRights.add_admins = true
						adminRights.pin_messages = adminRights.add_admins
						adminRights.invite_users = adminRights.pin_messages
						adminRights.ban_users = adminRights.invite_users
						adminRights.delete_messages = adminRights.ban_users
						adminRights.edit_messages = adminRights.delete_messages
						adminRights.post_messages = adminRights.edit_messages
						adminRights.change_info = adminRights.post_messages

						if (!isChannel) {
							adminRights.manage_call = true
						}
					}
				}
			}
			else {
				when (val `object` = searchListViewAdapter?.getItem(position)) {
					is User -> {
						messagesController.putUser(`object`, false)
						participant = getAnyParticipant(`object`.id.also { peerId = it })
					}

					is ChannelParticipant, is ChatParticipant -> {
						participant = `object`
					}

					else -> {
						participant = null
					}
				}

				when (participant) {
					is ChannelParticipant -> {
						peerId = MessageObject.getPeerId(participant.peer)
						canEditAdmin = !(participant is TL_channelParticipantAdmin || participant is TL_channelParticipantCreator) || participant.can_edit
						bannedRights = participant.banned_rights
						adminRights = participant.admin_rights
						rank = participant.rank
					}

					is ChatParticipant -> {
						peerId = participant.user_id
						canEditAdmin = currentChat!!.creator
						bannedRights = null
						adminRights = null
					}

					null -> {
						canEditAdmin = true
					}
				}
			}

			if (peerId != 0L) {
				if (selectType != SELECT_TYPE_MEMBERS) {
					if (selectType == SELECT_TYPE_EXCEPTION || selectType == SELECT_TYPE_ADMIN) {
						if (selectType != SELECT_TYPE_ADMIN && canEditAdmin && (participant is TL_channelParticipantAdmin || participant is TL_chatParticipantAdmin)) {
							val user = messagesController.getUser(peerId) ?: return@setOnItemClickListener
							val br = bannedRights
							val ar = adminRights
							val rankFinal = rank

							val builder = AlertDialog.Builder(context)
							builder.setTitle(context.getString(R.string.AppName))
							builder.setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, UserObject.getUserName(user)))

							builder.setPositiveButton(context.getString(R.string.OK)) { _, _ ->
								openRightsEdit(user.id, participant, ar, br, rankFinal, true, if (selectType == SELECT_TYPE_ADMIN) 0 else 1, false)
							}

							builder.setNegativeButton(context.getString(R.string.Cancel), null)

							showDialog(builder.create())
						}
						else {
							openRightsEdit(peerId, participant, adminRights, bannedRights, rank, canEditAdmin, if (selectType == SELECT_TYPE_ADMIN) 0 else 1, true)
						}
					}
					else {
						removeParticipant(peerId)
					}
				}
				else {
					var canEdit = false

					if (type == TYPE_ADMIN) {
						canEdit = peerId != userConfig.getClientUserId() && (currentChat?.admin_rights != null && currentChat?.admin_rights?.add_admins == true || currentChat?.creator == true || canEditAdmin) && participant !is TL_channelParticipantCreator
					}
					else if (type == TYPE_BANNED || type == TYPE_KICKED) {
						canEdit = ChatObject.canBlockUsers(currentChat)
					}

					if (type == TYPE_BANNED || type != TYPE_ADMIN && isChannel || type == TYPE_USERS) {
						if (peerId == userConfig.getClientUserId()) {
							return@setOnItemClickListener
						}

						val args = Bundle()

						if (peerId > 0) {
							args.putLong("user_id", peerId)
						}
						else {
							args.putLong("chat_id", -peerId)
						}

						presentFragment(ProfileActivity(args))
					}
					else {
						if (bannedRights == null) {
							bannedRights = TL_chatBannedRights()
							bannedRights.view_messages = true
							bannedRights.send_stickers = true
							bannedRights.send_media = true
							bannedRights.embed_links = true
							bannedRights.send_messages = true
							bannedRights.send_games = true
							bannedRights.send_inline = true
							bannedRights.send_gifs = true
							bannedRights.pin_messages = true
							bannedRights.send_polls = true
							bannedRights.invite_users = true
							bannedRights.change_info = true
						}

						val fragment = ChatRightsEditActivity(peerId, chatId, adminRights, defaultBannedRights, bannedRights, rank, if (type == TYPE_ADMIN) ChatRightsEditActivity.TYPE_ADMIN else ChatRightsEditActivity.TYPE_BANNED, canEdit, participant == null, null)

						fragment.setDelegate(object : ChatRightsEditActivityDelegate {
							override fun didSetRights(rights: Int, rightsAdmin: TL_chatAdminRights?, rightsBanned: TL_chatBannedRights?, rank: String?) {
								if (participant is ChannelParticipant) {
									participant.admin_rights = rightsAdmin
									participant.banned_rights = rightsBanned
									participant.rank = rank
									updateParticipantWithRights(participant, rightsAdmin, rightsBanned, 0, false)
								}
							}

							override fun didChangeOwner(user: User) {
								onOwnerChanged(user)
							}
						})

						presentFragment(fragment)
					}
				}
			}
		}

		listView?.setOnItemLongClickListener { _, position ->
			!(parentActivity == null || listView?.adapter !== listViewAdapter) && createMenuForParticipant(listViewAdapter?.getItem(position), false)
		}

		if (searchItem != null) {
			listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
					if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
						AndroidUtilities.hideKeyboard(parentActivity!!.currentFocus)
					}
				}

				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					// unused
				}
			})
		}

		undoView = UndoView(context)

		frameLayout.addView(undoView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM or Gravity.LEFT, 8f, 0f, 8f, 8f))

		updateRows()

		listView?.setEmptyView(emptyView)
		listView?.setAnimateEmptyView(false, 0)

		if (needOpenSearch) {
			searchItem?.openSearch(false)
		}

		return fragmentView
	}

	private fun sortAdmins(participants: ArrayList<TLObject>) {
		participants.sortWith { lhs, rhs ->
			val type1 = getChannelAdminParticipantType(lhs)
			val type2 = getChannelAdminParticipantType(rhs)

			if (type1 > type2) {
				return@sortWith 1
			}
			else if (type1 < type2) {
				return@sortWith -1
			}
			if (lhs is ChannelParticipant && rhs is ChannelParticipant) {
				return@sortWith (MessageObject.getPeerId(lhs.peer) - MessageObject.getPeerId(rhs.peer)).toInt()
			}

			0
		}
	}

	private fun showItemsAnimated(from: Int) {
		@Suppress("NAME_SHADOWING") var from = from

		if (isPaused || !openTransitionStarted || listView!!.adapter === listViewAdapter && firstLoaded) {
			return
		}

		val progressView = listView?.children?.find { it is FlickerLoadingView }

		if (progressView != null) {
			listView?.removeView(progressView)
			from--
		}

		val finalFrom = from

		listView?.doOnPreDraw {
			val listView = listView ?: return@doOnPreDraw
			val n = listView.childCount

			val animatorSet = AnimatorSet()

			for (i in 0 until n) {
				val child = listView.getChildAt(i)

				if (child === progressView || listView.getChildAdapterPosition(child) < finalFrom) {
					continue
				}

				child.alpha = 0f

				val s = min(listView.measuredHeight, max(0, child.top))
				val delay = (s / listView.measuredHeight.toFloat() * 100).toInt()

				val a = ObjectAnimator.ofFloat(child, View.ALPHA, 0f, 1f)
				a.startDelay = delay.toLong()
				a.duration = 200

				animatorSet.playTogether(a)
			}

			if (progressView != null && progressView.parent == null) {
				listView.addView(progressView)

				val layoutManager = listView.layoutManager

				if (layoutManager != null) {
					layoutManager.ignoreView(progressView)

					val animator: Animator = ObjectAnimator.ofFloat(progressView, View.ALPHA, progressView.alpha, 0f)

					animator.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							progressView.alpha = 1f
							layoutManager.stopIgnoringView(progressView)
							listView.removeView(progressView)
						}
					})

					animator.start()
				}
			}
			animatorSet.start()
		}
	}

	private fun onOwnerChanged(user: User) {
		undoView?.showWithAction(-chatId, if (isChannel) UndoView.ACTION_OWNER_TRANSFERED_CHANNEL else UndoView.ACTION_OWNER_TRANSFERED_GROUP, user)

		var foundAny = false

		currentChat?.creator = false

		for (a in 0..2) {
			var map: LongSparseArray<TLObject?>
			var arrayList: ArrayList<TLObject>
			var found = false

			when (a) {
				0 -> {
					map = contactsMap
					arrayList = contacts
				}

				1 -> {
					map = botsMap
					arrayList = bots
				}

				else -> {
					map = participantsMap
					arrayList = participants
				}
			}

			var `object` = map[user.id]

			if (`object` is ChannelParticipant) {
				val creator = TL_channelParticipantCreator()
				creator.peer = TL_peerUser()
				creator.peer.user_id = user.id
				map.put(user.id, creator)

				val index = arrayList.indexOf(`object`)

				if (index >= 0) {
					arrayList[index] = creator
				}

				found = true
				foundAny = true
			}

			val selfUserId = userConfig.getClientUserId()

			`object` = map[selfUserId]

			if (`object` is ChannelParticipant) {
				val admin = TL_channelParticipantAdmin()
				admin.peer = TL_peerUser()
				admin.peer.user_id = selfUserId
				admin.self = true
				admin.inviter_id = selfUserId
				admin.promoted_by = selfUserId
				admin.date = (System.currentTimeMillis() / 1000).toInt()
				admin.admin_rights = TL_chatAdminRights()
				admin.admin_rights.add_admins = true
				admin.admin_rights.pin_messages = admin.admin_rights.add_admins
				admin.admin_rights.invite_users = admin.admin_rights.pin_messages
				admin.admin_rights.ban_users = admin.admin_rights.invite_users
				admin.admin_rights.delete_messages = admin.admin_rights.ban_users
				admin.admin_rights.edit_messages = admin.admin_rights.delete_messages
				admin.admin_rights.post_messages = admin.admin_rights.edit_messages
				admin.admin_rights.change_info = admin.admin_rights.post_messages

				if (!isChannel) {
					admin.admin_rights.manage_call = true
				}

				map.put(selfUserId, admin)

				val index = arrayList.indexOf(`object`)

				if (index >= 0) {
					arrayList[index] = admin
				}

				found = true
			}

			if (found) {
				arrayList.sortWith { lhs, rhs ->
					val type1 = getChannelAdminParticipantType(lhs)
					val type2 = getChannelAdminParticipantType(rhs)

					if (type1 > type2) {
						return@sortWith 1
					}
					else if (type1 < type2) {
						return@sortWith -1
					}

					0
				}
			}
		}

		if (!foundAny) {
			val creator = TL_channelParticipantCreator()
			creator.peer = TL_peerUser()
			creator.peer.user_id = user.id

			participantsMap.put(user.id, creator)
			participants.add(creator)

			sortAdmins(participants)
			updateRows()
		}

		listViewAdapter?.notifyDataSetChanged()

		delegate?.didChangeOwner(user)
	}

	private fun openRightsEdit2(peerId: Long, date: Int, participant: TLObject, adminRights: TL_chatAdminRights?, bannedRights: TL_chatBannedRights?, rank: String?, type: Int) {
		val needShowBulletin = BooleanArray(1)
		val isAdmin = participant is TL_channelParticipantAdmin || participant is TL_chatParticipantAdmin

		val fragment = object : ChatRightsEditActivity(peerId, chatId, adminRights, defaultBannedRights, bannedRights, rank, type, true, false, null) {
			override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
				if (!isOpen && backward && needShowBulletin[0] && BulletinFactory.canShowBulletin(this@ChatUsersActivity)) {
					if (peerId > 0) {
						val user = messagesController.getUser(peerId) ?: return
						BulletinFactory.createPromoteToAdminBulletin(this@ChatUsersActivity, user.first_name).show()
					}
					else {
						val chat = messagesController.getChat(-peerId) ?: return
						BulletinFactory.createPromoteToAdminBulletin(this@ChatUsersActivity, chat.title).show()
					}
				}
			}
		}

		fragment.setDelegate(object : ChatRightsEditActivityDelegate {
			override fun didSetRights(rights: Int, rightsAdmin: TL_chatAdminRights?, rightsBanned: TL_chatBannedRights?, rank: String?) {
				if (type == 0) {
					for (a in participants.indices) {
						val p = participants[a]

						if (p is ChannelParticipant) {
							if (MessageObject.getPeerId(p.peer) == peerId) {
								val newPart = if (rights == 1) {
									TL_channelParticipantAdmin()
								}
								else {
									TL_channelParticipant()
								}

								newPart.admin_rights = rightsAdmin
								newPart.banned_rights = rightsBanned
								newPart.inviter_id = userConfig.getClientUserId()

								if (peerId > 0) {
									newPart.peer = TL_peerUser()
									newPart.peer.user_id = peerId
								}
								else {
									newPart.peer = TL_peerChannel()
									newPart.peer.channel_id = -peerId
								}

								newPart.date = date
								newPart.flags = newPart.flags or 4
								newPart.rank = rank

								participants[a] = newPart

								break
							}
						}
						else if (p is ChatParticipant) {
							val newParticipant = if (rights == 1) {
								TL_chatParticipantAdmin()
							}
							else {
								TL_chatParticipant()
							}

							newParticipant.user_id = p.user_id
							newParticipant.date = p.date
							newParticipant.inviter_id = p.inviter_id

							val index = info?.participants?.participants?.indexOf(p)

							if (index != null && index >= 0) {
								info?.participants?.participants?.set(index, newParticipant)
							}

							loadChatParticipants(0, 200)
						}
					}

					if (rights == 1 && !isAdmin) {
						needShowBulletin[0] = true
					}
				}
				else if (type == 1) {
					if (rights == 0) {
						removeParticipants(peerId)
					}
				}
			}

			override fun didChangeOwner(user: User) {
				onOwnerChanged(user)
			}
		})

		presentFragment(fragment)
	}

	override fun canBeginSlide(): Boolean {
		return checkDiscard()
	}

	private fun openRightsEdit(userId: Long, participant: TLObject?, adminRights: TL_chatAdminRights?, bannedRights: TL_chatBannedRights?, rank: String?, canEditAdmin: Boolean, type: Int, removeFragment: Boolean) {
		val fragment = ChatRightsEditActivity(userId, chatId, adminRights, defaultBannedRights, bannedRights, rank, type, canEditAdmin, participant == null, null)

		fragment.setDelegate(object : ChatRightsEditActivityDelegate {
			override fun didSetRights(rights: Int, rightsAdmin: TL_chatAdminRights?, rightsBanned: TL_chatBannedRights?, rank: String?) {
				if (participant is ChannelParticipant) {
					participant.admin_rights = rightsAdmin
					participant.banned_rights = rightsBanned
					participant.rank = rank
				}

				if (rights == 1) {
					delegate?.didSelectUser(userId)
				}
				else {
					participant?.let {
						delegate?.didAddParticipantToList(userId, it)
					}
				}

				if (removeFragment) {
					removeSelfFromStack()
				}
			}

			override fun didChangeOwner(user: User) {
				onOwnerChanged(user)
			}
		})

		presentFragment(fragment, removeFragment)
	}

	private fun removeParticipant(userId: Long) {
		if (!ChatObject.isChannel(currentChat)) {
			return
		}

		val user = messagesController.getUser(userId)

		messagesController.deleteParticipantFromChat(chatId, user)

		delegate?.didKickParticipant(userId)

		finishFragment()
	}

	private fun getAnyParticipant(userId: Long): TLObject? {
		for (a in 0..2) {
			val map = when (a) {
				0 -> contactsMap
				1 -> botsMap
				else -> participantsMap
			}

			val p = map[userId]

			if (p != null) {
				return p
			}
		}

		return null
	}

	private fun removeParticipants(`object`: TLObject) {
		if (`object` is ChatParticipant) {
			removeParticipants(`object`.user_id)
		}
		else if (`object` is ChannelParticipant) {
			removeParticipants(MessageObject.getPeerId(`object`.peer))
		}
	}

	private fun removeParticipants(peerId: Long) {
		var updated = false
		val savedState = saveState()
		for (a in 0..2) {
			var map: LongSparseArray<TLObject?>
			var arrayList: ArrayList<TLObject>

			when (a) {
				0 -> {
					map = contactsMap
					arrayList = contacts
				}

				1 -> {
					map = botsMap
					arrayList = bots
				}

				else -> {
					map = participantsMap
					arrayList = participants
				}
			}

			val p = map[peerId]

			if (p != null) {
				map.remove(peerId)
				arrayList.remove(p)
				updated = true

				if (type == TYPE_BANNED) {
					info?.let {
						it.kicked_count--
					}
				}
			}
		}

		if (updated) {
			updateListAnimated(savedState)
		}

		if (listView?.adapter === searchListViewAdapter) {
			searchListViewAdapter?.removeUserId(peerId)
		}
	}

	private fun updateParticipantWithRights(channelParticipant: ChannelParticipant, rightsAdmin: TL_chatAdminRights?, rightsBanned: TL_chatBannedRights?, userId: Long, withDelegate: Boolean) {
		@Suppress("NAME_SHADOWING") var channelParticipant = channelParticipant
		var delegateCalled = false

		for (a in 0..2) {
			val map = when (a) {
				0 -> contactsMap
				1 -> botsMap
				else -> participantsMap
			}

			val p = map[MessageObject.getPeerId(channelParticipant.peer)]

			if (p is ChannelParticipant) {
				channelParticipant = p
				channelParticipant.admin_rights = rightsAdmin
				channelParticipant.banned_rights = rightsBanned

				if (withDelegate) {
					channelParticipant.promoted_by = userConfig.getClientUserId()
				}
			}

			if (withDelegate && p != null && !delegateCalled && delegate != null) {
				delegateCalled = true
				delegate?.didAddParticipantToList(userId, p)
			}
		}
	}

	private fun createMenuForParticipant(participant: TLObject?, resultOnly: Boolean): Boolean {
		val context = context ?: return false

		if (participant == null || selectType != SELECT_TYPE_MEMBERS) {
			return false
		}

		val peerId: Long
		val canEdit: Boolean
		val date: Int
		val bannedRights: TL_chatBannedRights?
		val adminRights: TL_chatAdminRights?
		val rank: String?

		when (participant) {
			is ChannelParticipant -> {
				peerId = MessageObject.getPeerId(participant.peer)
				canEdit = participant.can_edit
				bannedRights = participant.banned_rights
				adminRights = participant.admin_rights
				date = participant.date
				rank = participant.rank
			}

			is ChatParticipant -> {
				peerId = participant.user_id
				date = participant.date
				canEdit = ChatObject.canAddAdmins(currentChat)
				bannedRights = null
				adminRights = null
				rank = ""
			}

			else -> {
				peerId = 0
				canEdit = false
				bannedRights = null
				adminRights = null
				date = 0
				rank = null
			}
		}

		if (peerId == 0L || peerId == userConfig.getClientUserId()) {
			return false
		}

		if (type == TYPE_USERS) {
			val user = messagesController.getUser(peerId)
			var allowSetAdmin = ChatObject.canAddAdmins(currentChat) && (participant is TL_channelParticipant || participant is TL_channelParticipantBanned || participant is TL_chatParticipant || canEdit)
			val canEditAdmin = !(participant is TL_channelParticipantAdmin || participant is TL_channelParticipantCreator || participant is TL_chatParticipantCreator || participant is TL_chatParticipantAdmin) || canEdit
			val editingAdmin = participant is TL_channelParticipantAdmin || participant is TL_chatParticipantAdmin

			allowSetAdmin = allowSetAdmin and !UserObject.isDeleted(user)

			val items: ArrayList<String?>?
			val actions: ArrayList<Int>?
			val icons: ArrayList<Int>?

			if (!resultOnly) {
				items = ArrayList()
				actions = ArrayList()
				icons = ArrayList()
			}
			else {
				items = null
				actions = null
				icons = null
			}

			if (allowSetAdmin) {
				if (resultOnly) {
					return true
				}

				items?.add(if (editingAdmin) context.getString(R.string.EditAdminRights) else context.getString(R.string.SetAsAdmin))
				icons?.add(R.drawable.msg_admins)

				actions?.add(0)
			}

			var hasRemove = false

			if (ChatObject.canBlockUsers(currentChat) && canEditAdmin) {
				if (resultOnly) {
					return true
				}

				if (!isChannel) {
					if (ChatObject.isChannel(currentChat) && currentChat?.gigagroup != true && currentChat?.megagroup != true) {
						items?.add(context.getString(R.string.ChangePermissions))
						icons?.add(R.drawable.msg_permissions)

						actions?.add(1)
					}

					items?.add(context.getString(R.string.KickFromGroup))
				}
				else {
					items?.add(context.getString(R.string.ChannelRemoveUser))
				}

				icons?.add(R.drawable.msg_remove)
				actions?.add(2)

				hasRemove = true
			}

			if (actions.isNullOrEmpty()) {
				return false
			}

			val builder = AlertDialog.Builder(context)

			builder.setItems(items?.toTypedArray(), AndroidUtilities.toIntArray(icons)) { _, i ->
				if (actions[i] == 2) {
					val builder2 = AlertDialog.Builder(context)
					builder2.setTitle(context.getString(R.string.AppName))
					builder2.setMessage(LocaleController.formatString("UserWillBeRemoved", R.string.UserWillBeRemoved, UserObject.getUserName(user)))

					builder2.setPositiveButton(context.getString(R.string.OK)) { _, _ ->
						messagesController.deleteParticipantFromChat(chatId, user)

						removeParticipants(peerId)

						if (currentChat != null && user != null && BulletinFactory.canShowBulletin(this)) {
							BulletinFactory.createRemoveFromChatBulletin(this, user, currentChat?.title).show()
						}
					}

					builder2.setNegativeButton(context.getString(R.string.Cancel), null)

					showDialog(builder2.create())
				}
				else {
					if (actions[i] == 1 && canEditAdmin && (participant is TL_channelParticipantAdmin || participant is TL_chatParticipantAdmin)) {
						val builder2 = AlertDialog.Builder(context)
						builder2.setTitle(context.getString(R.string.AppName))
						builder2.setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, UserObject.getUserName(user)))

						builder2.setPositiveButton(context.getString(R.string.OK)) { _, _ ->
							openRightsEdit2(peerId, date, participant, adminRights, bannedRights, rank, actions[i])
						}

						builder2.setNegativeButton(context.getString(R.string.Cancel), null)

						showDialog(builder2.create())
					}
					else {
						openRightsEdit2(peerId, date, participant, adminRights, bannedRights, rank, actions[i])
					}
				}
			}

			val alertDialog = builder.create()

			showDialog(alertDialog)

			if (hasRemove) {
				alertDialog.setItemColor((items?.size ?: 0) - 1, context.getColor(R.color.purple), context.getColor(R.color.purple))
			}
		}
		else {
			val items: Array<CharSequence?>?
			val icons: IntArray?

			if (type == TYPE_KICKED && ChatObject.canBlockUsers(currentChat)) {
				if (resultOnly) {
					return true
				}

				items = arrayOf(context.getString(R.string.ChannelEditPermissions), context.getString(R.string.ChannelDeleteFromList))
				icons = intArrayOf(R.drawable.msg_permissions, R.drawable.msg_delete)
			}
			else if (type == TYPE_BANNED && ChatObject.canBlockUsers(currentChat)) {
				if (resultOnly) {
					return true
				}

				// MARK: uncomment to put back "Add to channel" option
				// items = arrayOf(if (ChatObject.canAddUsers(currentChat) && peerId > 0) (if (isChannel) context.getString(R.string.ChannelAddToChannel) else context.getString(R.string.ChannelAddToGroup)) else null, context.getString(R.string.ChannelDeleteFromList))
				// icons = intArrayOf(R.drawable.msg_contact_add, R.drawable.msg_delete)

				// MARK: and delete this
				items = arrayOf(context.getString(R.string.ChannelDeleteFromList))
				icons = intArrayOf(R.drawable.msg_delete)
			}
			else if (type == TYPE_ADMIN && ChatObject.canAddAdmins(currentChat) && canEdit) {
				if (resultOnly) {
					return true
				}

				if (currentChat?.creator == true || participant !is TL_channelParticipantCreator) {
					items = arrayOf(context.getString(R.string.EditAdminRights), context.getString(R.string.ChannelRemoveUserAdmin))
					icons = intArrayOf(R.drawable.msg_admins, R.drawable.msg_remove)
				}
				else {
					items = arrayOf(context.getString(R.string.ChannelRemoveUserAdmin))
					icons = intArrayOf(R.drawable.msg_remove)
				}
			}
			else {
				items = null
				icons = null
			}

			if (items == null) {
				return false
			}

			val builder = AlertDialog.Builder(context)

			builder.setItems(items, icons) { _, i ->
				if (type == TYPE_ADMIN) {
					if (i == 0 && items.size == 2) {
						val fragment = ChatRightsEditActivity(peerId, chatId, adminRights, null, null, rank, ChatRightsEditActivity.TYPE_ADMIN, edit = true, addingNew = false, addingNewBotHash = null)

						fragment.setDelegate(object : ChatRightsEditActivityDelegate {
							override fun didSetRights(rights: Int, rightsAdmin: TL_chatAdminRights?, rightsBanned: TL_chatBannedRights?, rank: String?) {
								if (participant is ChannelParticipant) {
									participant.admin_rights = rightsAdmin
									participant.banned_rights = rightsBanned
									participant.rank = rank

									updateParticipantWithRights(participant, rightsAdmin, rightsBanned, 0, false)
								}
							}

							override fun didChangeOwner(user: User) {
								onOwnerChanged(user)
							}
						})

						presentFragment(fragment)
					}
					else {
						messagesController.setUserAdminRole(chatId, messagesController.getUser(peerId), TL_chatAdminRights(), "", !isChannel, this@ChatUsersActivity, addingNew = false, forceAdmin = false, botHash = null, onSuccess = null)
						removeParticipants(peerId)
					}
				}
				else if (type == TYPE_BANNED || type == TYPE_KICKED) {
					if (i == 0) {
						if (type == TYPE_KICKED) {
							val fragment = ChatRightsEditActivity(peerId, chatId, null, defaultBannedRights, bannedRights, rank, ChatRightsEditActivity.TYPE_BANNED, edit = true, addingNew = false, addingNewBotHash = null)

							fragment.setDelegate(object : ChatRightsEditActivityDelegate {
								override fun didSetRights(rights: Int, rightsAdmin: TL_chatAdminRights?, rightsBanned: TL_chatBannedRights?, rank: String?) {
									if (participant is ChannelParticipant) {
										participant.admin_rights = rightsAdmin
										participant.banned_rights = rightsBanned
										participant.rank = rank

										updateParticipantWithRights(participant, rightsAdmin, rightsBanned, 0, false)
									}
								}

								override fun didChangeOwner(user: User) {
									onOwnerChanged(user)
								}
							})

							presentFragment(fragment)
						}
						else if (type == TYPE_BANNED) {
							if (peerId > 0) {
								val user = messagesController.getUser(peerId)
								messagesController.addUserToChat(chatId, user, 0, null, this@ChatUsersActivity, null)
							}
						}
					}
					else if (i == 1) {
						val req = TL_channels_editBanned()
						req.participant = messagesController.getInputPeer(peerId)
						req.channel = messagesController.getInputChannel(chatId)
						req.banned_rights = TL_chatBannedRights()

						connectionsManager.sendRequest(req) { response, _ ->
							if (response != null) {
								val updates = response as Updates

								messagesController.processUpdates(updates, false)

								if (updates.chats.isNotEmpty()) {
									AndroidUtilities.runOnUIThread({
										val chat = updates.chats[0]
										messagesController.loadFullChat(chat.id, 0, true)
									}, 1000)
								}
							}
						}
					}

					if (i == 0 && type == TYPE_BANNED || i == 1) {
						removeParticipants(participant)
					}
				}
				else {
					if (i == 0) {
						val user: User?
						val chat: Chat?

						if (peerId > 0) {
							user = messagesController.getUser(peerId)
							chat = null
						}
						else {
							user = null
							chat = messagesController.getChat(-peerId)
						}

						messagesController.deleteParticipantFromChat(chatId, user, chat, forceDelete = false, revoke = false)
					}
				}
			}

			val alertDialog = builder.create()

			showDialog(alertDialog)

			if (type == TYPE_ADMIN) {
				alertDialog.setItemColor(items.size - 1, context.getColor(R.color.purple), context.getColor(R.color.purple))
			}
		}

		return true
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.chatInfoDidLoad -> {
				val chatFull = args[0] as ChatFull
				val byChannelUsers = args[2] as Boolean

				if (chatFull.id == chatId && (!byChannelUsers || !ChatObject.isChannel(currentChat))) {
					val hadInfo = info != null
					info = chatFull

					if (!hadInfo) {
						initialSlowMode = currentSlowMode
						selectedSlowMode = initialSlowMode
					}

					AndroidUtilities.runOnUIThread {
						loadChatParticipants(0, 200)
					}
				}
			}
		}
	}

	override fun onBackPressed(): Boolean {
		return checkDiscard()
	}

	fun setDelegate(chatUsersActivityDelegate: ChatUsersActivityDelegate?) {
		delegate = chatUsersActivityDelegate
	}

	private val currentSlowMode: Int
		get() {
			return when (info?.slowmode_seconds) {
				10 -> 1
				30 -> 2
				60 -> 3
				5 * 60 -> 4
				15 * 60 -> 5
				60 * 60 -> 6
				else -> 0
			}
		}

	private fun getSecondsForIndex(index: Int): Int {
		return when (index) {
			1 -> 10
			2 -> 30
			3 -> 60
			4 -> 5 * 60
			5 -> 15 * 60
			6 -> 60 * 60
			else -> 0
		}
	}

	private fun formatSeconds(seconds: Int): String {
		return if (seconds < 60) {
			LocaleController.formatPluralString("Seconds", seconds)
		}
		else if (seconds < 60 * 60) {
			LocaleController.formatPluralString("Minutes", seconds / 60)
		}
		else {
			LocaleController.formatPluralString("Hours", seconds / 60 / 60)
		}
	}

	private fun checkDiscard(): Boolean {
		val context = parentActivity ?: return false

		val newBannedRights = ChatObject.getBannedRightsString(defaultBannedRights)

		if (newBannedRights != initialBannedRights || initialSlowMode != selectedSlowMode) {
			val builder = AlertDialog.Builder(context)
			builder.setTitle(context.getString(R.string.UserRestrictionsApplyChanges))

			if (isChannel) {
				builder.setMessage(context.getString(R.string.ChannelSettingsChangedAlert))
			}
			else {
				builder.setMessage(context.getString(R.string.GroupSettingsChangedAlert))
			}

			builder.setPositiveButton(context.getString(R.string.ApplyTheme)) { _, _ ->
				processDone()
			}

			builder.setNegativeButton(context.getString(R.string.PassportDiscard)) { _, _ ->
				finishFragment()
			}

			showDialog(builder.create())

			return false
		}

		return true
	}

	fun hasSelectType(): Boolean {
		return selectType != SELECT_TYPE_MEMBERS
	}

	private fun formatUserPermissions(rights: TL_chatBannedRights?): String {
		val context = context

		if (rights == null || context == null) {
			return ""
		}

		val builder = StringBuilder()

		if (rights.view_messages && defaultBannedRights.view_messages != rights.view_messages) {
			builder.append(context.getString(R.string.UserRestrictionsNoRead))
		}

		if (rights.send_messages && defaultBannedRights.send_messages != rights.send_messages) {
			if (builder.isNotEmpty()) {
				builder.append(", ")
			}

			builder.append(context.getString(R.string.UserRestrictionsNoSend))
		}

		if (rights.send_media && defaultBannedRights.send_media != rights.send_media) {
			if (builder.isNotEmpty()) {
				builder.append(", ")
			}

			builder.append(context.getString(R.string.UserRestrictionsNoSendMedia))
		}

		if (rights.send_stickers && defaultBannedRights.send_stickers != rights.send_stickers) {
			if (builder.isNotEmpty()) {
				builder.append(", ")
			}

			builder.append(context.getString(R.string.UserRestrictionsNoSendStickers))
		}

		if (rights.send_polls && defaultBannedRights.send_polls != rights.send_polls) {
			if (builder.isNotEmpty()) {
				builder.append(", ")
			}

			builder.append(context.getString(R.string.UserRestrictionsNoSendPolls))
		}

		if (rights.embed_links && defaultBannedRights.embed_links != rights.embed_links) {
			if (builder.isNotEmpty()) {
				builder.append(", ")
			}

			builder.append(context.getString(R.string.UserRestrictionsNoEmbedLinks))
		}

		if (rights.invite_users && defaultBannedRights.invite_users != rights.invite_users) {
			if (builder.isNotEmpty()) {
				builder.append(", ")
			}

			builder.append(context.getString(R.string.UserRestrictionsNoInviteUsers))
		}

		if (rights.pin_messages && defaultBannedRights.pin_messages != rights.pin_messages) {
			if (builder.isNotEmpty()) {
				builder.append(", ")
			}

			builder.append(context.getString(R.string.UserRestrictionsNoPinMessages))
		}

		if (rights.change_info && defaultBannedRights.change_info != rights.change_info) {
			if (builder.isNotEmpty()) {
				builder.append(", ")
			}

			builder.append(context.getString(R.string.UserRestrictionsNoChangeInfo))
		}

		if (builder.isNotEmpty()) {
			builder.replace(0, 1, builder.substring(0, 1).uppercase())
			builder.append('.')
		}

		return builder.toString()
	}

	private fun processDone() {
		if (type != TYPE_KICKED) {
			return
		}

		if (currentChat?.creator == true && !ChatObject.isChannel(currentChat) && selectedSlowMode != initialSlowMode && info != null) {
			messagesController.convertToMegaGroup(parentActivity, chatId, this, {
				if (it != 0L) {
					chatId = it
					currentChat = messagesController.getChat(it)
					processDone()
				}
			}, null)

			return
		}

		val newBannedRights = ChatObject.getBannedRightsString(defaultBannedRights)

		if (newBannedRights != initialBannedRights) {
			messagesController.setDefaultBannedRole(chatId, defaultBannedRights, ChatObject.isChannel(currentChat), this)
			val chat = messagesController.getChat(chatId)
			chat?.default_banned_rights = defaultBannedRights
		}

		if (selectedSlowMode != initialSlowMode) {
			info?.let { info ->
				info.slowmode_seconds = getSecondsForIndex(selectedSlowMode)
				info.flags = info.flags or 131072
				messagesController.setChannelSlowMode(chatId, info.slowmode_seconds)
			}
		}

		finishFragment()
	}

	fun setInfo(chatFull: ChatFull?) {
		info = chatFull

		if (info != null) {
			initialSlowMode = currentSlowMode
			selectedSlowMode = initialSlowMode
		}
	}

	override fun needDelayOpenAnimation(): Boolean {
		return true
	}

	private fun getChannelAdminParticipantType(participant: TLObject?): Int {
		return when (participant) {
			is TL_channelParticipantCreator, is TL_channelParticipantSelf -> 0
			is TL_channelParticipantAdmin, is TL_channelParticipant -> 1
			else -> 2
		}
	}

	private fun loadChatParticipantsRequests(offset: Int, count: Int): ArrayList<TL_channels_getParticipants> {
		val req = TL_channels_getParticipants()

		val requests = ArrayList<TL_channels_getParticipants>()
		requests.add(req)

		req.channel = messagesController.getInputChannel(chatId)

		if (type == TYPE_BANNED) {
			req.filter = TL_channelParticipantsKicked()
		}
		else if (type == TYPE_ADMIN) {
			req.filter = TL_channelParticipantsAdmins()
		}
		else if (type == TYPE_USERS) {
			if (info != null && (info?.participants_count ?: 0) <= 200 && currentChat?.megagroup == true) {
				req.filter = TL_channelParticipantsRecent()
			}
			else {
				if (selectType == SELECT_TYPE_ADMIN) {
					if (!contactsEndReached) {
						delayResults = 2
						req.filter = TL_channelParticipantsContacts()
						contactsEndReached = true
						requests.addAll(loadChatParticipantsRequests(0, 200))
					}
					else {
						req.filter = TL_channelParticipantsRecent()
					}
				}
				else {
					if (!contactsEndReached) {
						delayResults = 3
						req.filter = TL_channelParticipantsContacts()
						contactsEndReached = true
						requests.addAll(loadChatParticipantsRequests(0, 200))
					}
					else if (!botsEndReached) {
						req.filter = TL_channelParticipantsBots()
						botsEndReached = true
						requests.addAll(loadChatParticipantsRequests(0, 200))
					}
					else {
						req.filter = TL_channelParticipantsRecent()
					}
				}
			}
		}
		else if (type == TYPE_KICKED) {
			req.filter = TL_channelParticipantsBanned()
		}

		req.filter.q = ""
		req.offset = offset
		req.limit = count

		return requests
	}

	private fun loadChatParticipants(offset: Int, count: Int) {
		if (loadingUsers) {
			return
		}

		contactsEndReached = false
		botsEndReached = false

		if (!ChatObject.isChannel(currentChat)) {
			loadingUsers = false
			participants.clear()
			bots.clear()
			contacts.clear()
			participantsMap.clear()
			contactsMap.clear()
			botsMap.clear()

			if (type == TYPE_ADMIN) {
				if (info != null) {
					var a = 0
					val size = info!!.participants.participants.size

					while (a < size) {
						val participant = info?.participants?.participants?.get(a)

						if (participant is TL_chatParticipantCreator || participant is TL_chatParticipantAdmin) {
							participants.add(participant)
						}

						participant?.let {
							participantsMap.put(it.user_id, it)
						}

						a++
					}
				}
			}
			else if (type == TYPE_USERS) {
				if (info != null) {
					val selfUserId = userConfig.clientUserId

					info?.participants?.participants?.forEach { participant ->
						if (selectType != SELECT_TYPE_MEMBERS && participant.user_id == selfUserId) {
							return@forEach
						}

						if ((ignoredUsers?.indexOfKey(participant.user_id) ?: -1) >= 0) {
							return@forEach
						}

						if (selectType == SELECT_TYPE_ADMIN) {
							if (contactsController.isContact(participant.user_id)) {
								contacts.add(participant)
								contactsMap.put(participant.user_id, participant)
							}
							else if (!UserObject.isDeleted(messagesController.getUser(participant.user_id))) {
								participants.add(participant)
								participantsMap.put(participant.user_id, participant)
							}
						}
						else {
							if (contactsController.isContact(participant.user_id)) {
								contacts.add(participant)
								contactsMap.put(participant.user_id, participant)
							}
							else {
								val user = messagesController.getUser(participant.user_id)
								if (user != null && user.bot) {
									bots.add(participant)
									botsMap.put(participant.user_id, participant)
								}
								else {
									participants.add(participant)
									participantsMap.put(participant.user_id, participant)
								}
							}
						}
					}
				}
			}

			listViewAdapter?.notifyDataSetChanged()

			updateRows()

			listViewAdapter?.notifyDataSetChanged()
		}
		else {
			loadingUsers = true

			emptyView?.showProgress(show = true, animated = false)

			listViewAdapter?.notifyDataSetChanged()

			val requests = loadChatParticipantsRequests(offset, count)
			val responses = ArrayList<TL_channels_channelParticipants?>()

			val onRequestsEnd = Runnable {
				var objectsCount = 0
				for (i in requests.indices) {
					val req = requests[i]
					val res = responses[i] ?: continue

					if (type == TYPE_ADMIN) {
						messagesController.processLoadedAdminsResponse(chatId, res)
					}

					messagesController.putUsers(res.users, false)
					messagesController.putChats(res.chats, false)

					val selfId = userConfig.getClientUserId()

					if (selectType != SELECT_TYPE_MEMBERS) {
						for (a in res.participants.indices) {
							if (MessageObject.getPeerId(res.participants[a].peer) == selfId) {
								res.participants.removeAt(a)
								break
							}
						}
					}

					var objects: ArrayList<TLObject>
					var map: LongSparseArray<TLObject?>

					if (type == TYPE_USERS) {
						delayResults--

						when (req.filter) {
							is TL_channelParticipantsContacts -> {
								objects = contacts
								map = contactsMap
							}

							is TL_channelParticipantsBots -> {
								objects = bots
								map = botsMap
							}

							else -> {
								objects = participants
								map = participantsMap
							}
						}
					}
					else {
						objects = participants
						map = participantsMap
						participantsMap.clear()
					}

					objects.clear()
					objects.addAll(res.participants)

					var a = 0
					val size = res.participants.size

					while (a < size) {
						val participant = res.participants[a]

						if (participant.user_id == selfId) {
							objects.remove(participant)
						}
						else {
							map.put(MessageObject.getPeerId(participant.peer), participant)
						}

						a++
					}

					objectsCount += objects.size

					if (type == TYPE_USERS) {
						@Suppress("NAME_SHADOWING") var a = 0
						var n = participants.size

						while (a < n) {
							val `object` = participants[a]

							if (`object` !is ChannelParticipant) {
								participants.removeAt(a)
								a--
								n--
								a++

								continue
							}

							val peerId = MessageObject.getPeerId(`object`.peer)
							var remove = false

							if (contactsMap[peerId] != null || botsMap[peerId] != null) {
								remove = true
							}
							else if (selectType == SELECT_TYPE_ADMIN && peerId > 0 && UserObject.isDeleted(messagesController.getUser(peerId))) {
								remove = true
							}
							else if (ignoredUsers != null && ignoredUsers!!.indexOfKey(peerId) >= 0) {
								remove = true
							}

							if (remove) {
								participants.removeAt(a)
								participantsMap.remove(peerId)
								a--
								n--
							}

							a++
						}
					}

					try {
						if ((type == TYPE_BANNED || type == TYPE_KICKED || type == TYPE_USERS) && currentChat != null && currentChat!!.megagroup && info is TL_channelFull && (info?.participants_count ?: 0) <= 200) {
							sortUsers(objects)
						}
						else if (type == TYPE_ADMIN) {
							sortAdmins(participants)
						}
					}
					catch (e: Exception) {
						e(e)
					}
				}

				if (type != TYPE_USERS || delayResults <= 0) {
					showItemsAnimated(listViewAdapter?.itemCount ?: 0)
					loadingUsers = false
					firstLoaded = true
					searchItem?.visibility = if (type != TYPE_BANNED || firstLoaded && objectsCount > 5) View.VISIBLE else View.GONE
				}

				updateRows()

				if (listViewAdapter != null) {
					listView?.setAnimateEmptyView(openTransitionStarted, 0)
					listViewAdapter?.notifyDataSetChanged()

					if (listViewAdapter?.itemCount == 0 && firstLoaded) {
						emptyView?.showProgress(show = false, animated = true)
					}
				}

				resumeDelayedFragmentAnimation()
			}

			val responsesReceived = AtomicInteger(0)

			for (i in requests.indices) {
				responses.add(null)

				val reqId = connectionsManager.sendRequest(requests[i]) { response, error ->
					AndroidUtilities.runOnUIThread {
						if (error == null && response is TL_channels_channelParticipants) {
							responses[i] = response as TL_channels_channelParticipants?
						}
						responsesReceived.getAndIncrement()
						if (responsesReceived.get() == requests.size) {
							onRequestsEnd.run()
						}
					}
				}
				connectionsManager.bindRequestToGuid(reqId, classGuid)
			}
		}
	}

	private fun sortUsers(objects: ArrayList<TLObject>) {
		val currentTime = connectionsManager.currentTime

		objects.sortWith { lhs, rhs ->
			val p1 = lhs as ChannelParticipant?
			val p2 = rhs as ChannelParticipant?
			val peer1 = MessageObject.getPeerId(p1!!.peer)
			val peer2 = MessageObject.getPeerId(p2!!.peer)
			var status1 = 0

			if (peer1 > 0) {
				val user1 = messagesController.getUser(MessageObject.getPeerId(p1.peer))

				if (user1?.status != null) {
					status1 = if (user1.self) {
						currentTime + 50000
					}
					else {
						user1.status?.expires ?: 0
					}
				}
			}
			else {
				status1 = -100
			}

			var status2 = 0

			if (peer2 > 0) {
				val user2 = messagesController.getUser(MessageObject.getPeerId(p2.peer))

				if (user2?.status != null) {
					status2 = if (user2.self) {
						currentTime + 50000
					}
					else {
						user2.status?.expires ?: 0
					}
				}
			}
			else {
				status2 = -100
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

	override fun onResume() {
		super.onResume()
		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
		listViewAdapter?.notifyDataSetChanged()
		emptyView?.requestLayout()
	}

	override fun onPause() {
		super.onPause()
		undoView?.hide(true, 0)
	}

	override fun onBecomeFullyHidden() {
		undoView?.hide(true, 0)
	}

	override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
		if (isOpen) {
			openTransitionStarted = true
		}

		if (isOpen && !backward && needOpenSearch) {
			searchItem?.searchField?.requestFocus()
			AndroidUtilities.showKeyboard(searchItem?.searchField)
			searchItem?.visibility = View.GONE
		}
	}

	fun saveState(): DiffCallback {
		val diffCallback = DiffCallback()
		diffCallback.oldRowCount = rowCount
		diffCallback.oldBotStartRow = botStartRow
		diffCallback.oldBotEndRow = botEndRow
		diffCallback.oldBots.clear()
		diffCallback.oldBots.addAll(bots)
		diffCallback.oldContactsEndRow = contactsEndRow
		diffCallback.oldContactsStartRow = contactsStartRow
		diffCallback.oldContacts.clear()
		diffCallback.oldContacts.addAll(contacts)
		diffCallback.oldParticipantsStartRow = participantsStartRow
		diffCallback.oldParticipantsEndRow = participantsEndRow
		diffCallback.oldParticipants.clear()
		diffCallback.oldParticipants.addAll(participants)
		diffCallback.fillPositions(diffCallback.oldPositionToItem)

		return diffCallback
	}

	fun updateListAnimated(savedState: DiffCallback) {
		if (listViewAdapter == null) {
			updateRows()
			return
		}

		updateRows()

		savedState.fillPositions(savedState.newPositionToItem)

		DiffUtil.calculateDiff(savedState).dispatchUpdatesTo(listViewAdapter!!)

		val listView = listView

		if (listView != null && layoutManager != null && listView.childCount > 0) {
			var view: View? = null
			var position = -1

			for (i in 0 until listView.childCount) {
				position = listView.getChildAdapterPosition(listView.getChildAt(i))

				if (position != RecyclerView.NO_POSITION) {
					view = listView.getChildAt(i)
					break
				}
			}

			if (view != null) {
				layoutManager?.scrollToPositionWithOffset(position, view.top - listView.paddingTop)
			}
		}
	}

	interface ChatUsersActivityDelegate {
		fun didAddParticipantToList(uid: Long, participant: TLObject) {}
		fun didChangeOwner(user: User) {}
		fun didSelectUser(uid: Long) {}
		fun didKickParticipant(userId: Long) {}
	}

	private inner class ChooseView(context: Context) : View(context) {
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		private val accessibilityDelegate: SeekBarAccessibilityDelegate
		private val strings = ArrayList<String>()
		private val sizes = ArrayList<Int>()
		private var circleSize = 0
		private var gapSize = 0
		private var sideSide = 0
		private var lineSize = 0
		private var moving = false
		private var startMoving = false
		private var startX = 0f
		private var startMovingItem = 0

		init {
			textPaint.textSize = AndroidUtilities.dp(13f).toFloat()
			textPaint.typeface = Theme.TYPEFACE_DEFAULT

			for (a in 0..6) {
				val string = when (a) {
					0 -> context.getString(R.string.SlowmodeOff)
					1 -> LocaleController.formatString("SlowmodeSeconds", R.string.SlowmodeSeconds, 10)
					2 -> LocaleController.formatString("SlowmodeSeconds", R.string.SlowmodeSeconds, 30)
					3 -> LocaleController.formatString("SlowmodeMinutes", R.string.SlowmodeMinutes, 1)
					4 -> LocaleController.formatString("SlowmodeMinutes", R.string.SlowmodeMinutes, 5)
					5 -> LocaleController.formatString("SlowmodeMinutes", R.string.SlowmodeMinutes, 15)
					6 -> LocaleController.formatString("SlowmodeHours", R.string.SlowmodeHours, 1)
					else -> LocaleController.formatString("SlowmodeHours", R.string.SlowmodeHours, 1)
				}

				strings.add(string)
				sizes.add(ceil(textPaint.measureText(string).toDouble()).toInt())
			}

			accessibilityDelegate = object : IntSeekBarAccessibilityDelegate() {
				public override fun getProgress(): Int {
					return selectedSlowMode
				}

				public override fun setProgress(progress: Int) {
					setItem(progress)
				}

				public override fun getMaxValue(): Int {
					return strings.size - 1
				}

				override fun getContentDescription(host: View?): CharSequence {
					return if (selectedSlowMode == 0) {
						context.getString(R.string.SlowmodeOff)
					}
					else {
						formatSeconds(getSecondsForIndex(selectedSlowMode))
					}
				}
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			accessibilityDelegate.onInitializeAccessibilityNodeInfoInternal(this, info)
		}

		override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
			return super.performAccessibilityAction(action, arguments) || accessibilityDelegate.performAccessibilityActionInternal(this, action, arguments)
		}

		@SuppressLint("ClickableViewAccessibility")
		override fun onTouchEvent(event: MotionEvent): Boolean {
			val x = event.x

			if (event.action == MotionEvent.ACTION_DOWN) {
				parent.requestDisallowInterceptTouchEvent(true)

				for (a in strings.indices) {
					val cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2

					if (x > cx - AndroidUtilities.dp(15f) && x < cx + AndroidUtilities.dp(15f)) {
						startMoving = a == selectedSlowMode
						startX = x
						startMovingItem = selectedSlowMode
						break
					}
				}
			}
			else if (event.action == MotionEvent.ACTION_MOVE) {
				if (startMoving) {
					if (abs(startX - x) >= AndroidUtilities.getPixelsInCM(0.5f, true)) {
						moving = true
						startMoving = false
					}
				}
				else if (moving) {
					for (a in strings.indices) {
						val cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2
						val diff = lineSize / 2 + circleSize / 2 + gapSize

						if (x > cx - diff && x < cx + diff) {
							if (selectedSlowMode != a) {
								setItem(a)
							}

							break
						}
					}
				}
			}
			else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
				if (!moving) {
					for (a in strings.indices) {
						val cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2

						if (x > cx - AndroidUtilities.dp(15f) && x < cx + AndroidUtilities.dp(15f)) {
							if (selectedSlowMode != a) {
								setItem(a)
							}

							break
						}
					}
				}
				else {
					if (selectedSlowMode != startMovingItem) {
						setItem(selectedSlowMode)
					}
				}

				startMoving = false
				moving = false
			}

			return true
		}

		private fun setItem(index: Int) {
			if (info == null) {
				return
			}

			selectedSlowMode = index
			listViewAdapter?.notifyItemChanged(slowmodeInfoRow)
			invalidate()
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(74f), MeasureSpec.EXACTLY))
			circleSize = AndroidUtilities.dp(6f)
			gapSize = AndroidUtilities.dp(2f)
			sideSide = AndroidUtilities.dp(22f)
			lineSize = (measuredWidth - circleSize * strings.size - gapSize * 2 * (strings.size - 1) - sideSide * 2) / (strings.size - 1)
		}

		override fun onDraw(canvas: Canvas) {
			textPaint.color = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)

			val cy = measuredHeight / 2 + AndroidUtilities.dp(11f)

			for (a in strings.indices) {
				val cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2

				if (a <= selectedSlowMode) {
					paint.color = ResourcesCompat.getColor(context.resources, R.color.brand, null)
				}
				else {
					paint.color = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
				}

				canvas.drawCircle(cx.toFloat(), cy.toFloat(), (if (a == selectedSlowMode) AndroidUtilities.dp(6f) else circleSize / 2).toFloat(), paint)

				if (a != 0) {
					var x = cx - circleSize / 2 - gapSize - lineSize
					var width = lineSize

					if (a == selectedSlowMode || a == selectedSlowMode + 1) {
						width -= AndroidUtilities.dp(3f)
					}

					if (a == selectedSlowMode + 1) {
						x += AndroidUtilities.dp(3f)
					}

					canvas.drawRect(x.toFloat(), (cy - AndroidUtilities.dp(1f)).toFloat(), (x + width).toFloat(), (cy + AndroidUtilities.dp(1f)).toFloat(), paint)
				}

				val size = sizes[a]
				val text = strings[a]

				when (a) {
					0 -> {
						canvas.drawText(text, AndroidUtilities.dp(22f).toFloat(), AndroidUtilities.dp(28f).toFloat(), textPaint)
					}

					strings.size - 1 -> {
						canvas.drawText(text, (measuredWidth - size - AndroidUtilities.dp(22f)).toFloat(), AndroidUtilities.dp(28f).toFloat(), textPaint)
					}

					else -> {
						canvas.drawText(text, (cx - size / 2).toFloat(), AndroidUtilities.dp(28f).toFloat(), textPaint)
					}
				}
			}
		}
	}

	private inner class SearchAdapter(private val mContext: Context) : SelectionAdapter() {
		private val searchAdapterHelper = SearchAdapterHelper(true)
		private var searchResult = ArrayList<Any>()
		private var searchResultMap = LongSparseArray<TLObject>()
		private var searchResultNames = ArrayList<CharSequence>()
		private var searchRunnable: Runnable? = null
		private var totalCount = 0
		private var searchInProgress = false
		private var groupStartRow = 0
		private var contactsStartRow = 0
		private var globalStartRow = 0

		init {
			searchAdapterHelper.setDelegate(object : SearchAdapterHelperDelegate {
				override fun canApplySearchResults(searchId: Int): Boolean {
					return true
				}

				override val excludeCallParticipants: LongSparseArray<TL_groupCallParticipant>?
					get() = null

				override val excludeUsers: LongSparseArray<User>?
					get() = null

				override fun onSetHashtags(arrayList: ArrayList<HashtagObject>?, hashMap: HashMap<String, HashtagObject>?) {
					// unused
				}

				override fun onDataSetChanged(searchId: Int) {
					if (!searchAdapterHelper.isSearchInProgress) {
						val oldItemCount = itemCount

						notifyDataSetChanged()

						if (itemCount > oldItemCount) {
							showItemsAnimated(oldItemCount)
						}

						if (!searchInProgress) {
							if (itemCount == 0 && searchId != 0) {
								emptyView?.showProgress(show = false, animated = true)
							}
						}
					}
				}
			})
		}

		fun searchUsers(query: String?) {
			if (searchRunnable != null) {
				Utilities.searchQueue.cancelRunnable(searchRunnable)
				searchRunnable = null
			}

			searchResult.clear()
			searchResultMap.clear()
			searchResultNames.clear()
			searchAdapterHelper.mergeResults(null)
			searchAdapterHelper.queryServerSearch(null, type != 0, allowChats = false, allowBots = true, allowSelf = false, canAddGroupsOnly = false, channelId = if (ChatObject.isChannel(currentChat)) chatId else 0, type = type, searchId = 0, onEnd = null)

			notifyDataSetChanged()

			if (!query.isNullOrEmpty()) {
				searchInProgress = true
				emptyView?.showProgress(show = true, animated = true)
				Utilities.searchQueue.postRunnable(Runnable { processSearch(query) }.also { searchRunnable = it }, 300)
			}
		}

		private fun processSearch(query: String?) {
			AndroidUtilities.runOnUIThread(Runnable {
				searchRunnable = null

				val participantsCopy = if (!ChatObject.isChannel(currentChat) && info != null) ArrayList<TLObject>(info!!.participants.participants) else null
				val contactsCopy = if (selectType == SELECT_TYPE_ADMIN) ArrayList(contactsController.contacts) else null
				var addContacts: Runnable? = null

				if (participantsCopy != null || contactsCopy != null) {
					addContacts = Runnable {
						val search1 = query?.trim()?.lowercase()

						if (search1.isNullOrEmpty()) {
							updateSearchResults(ArrayList(), LongSparseArray(), ArrayList(), ArrayList())
							return@Runnable
						}

						var search2 = LocaleController.getInstance().getTranslitString(search1)

						if (search1 == search2 || search2.isNullOrEmpty()) {
							search2 = null
						}

						val search = arrayOfNulls<String>(1 + if (search2 != null) 1 else 0)

						search[0] = search1

						if (search2 != null) {
							search[1] = search2
						}

						val resultArray = ArrayList<Any>()
						val resultMap = LongSparseArray<TLObject>()
						val resultArrayNames = ArrayList<CharSequence>()
						val resultArray2 = ArrayList<TLObject>()

						if (participantsCopy != null) {
							var a = 0
							val n = participantsCopy.size

							while (a < n) {
								var peerId: Long
								val o = participantsCopy[a]

								peerId = if (o is ChatParticipant) {
									o.user_id
								}
								else if (o is ChannelParticipant) {
									MessageObject.getPeerId(o.peer)
								}
								else {
									a++
									continue
								}

								var name: String?
								var username: String?
								var firstName: String?
								var lastName: String?

								if (peerId > 0) {
									val user = messagesController.getUser(peerId)

									if (user?.id == userConfig.getClientUserId()) {
										a++
										continue
									}

									name = UserObject.getUserName(user).lowercase(Locale.getDefault())
									username = user?.username
									firstName = user?.first_name
									lastName = user?.last_name
								}
								else {
									val chat = messagesController.getChat(-peerId)
									name = chat?.title?.lowercase()
									username = chat?.username
									firstName = chat?.title
									lastName = null
								}

								var tName = LocaleController.getInstance().getTranslitString(name)

								if (name == tName) {
									tName = null
								}

								var found = 0

								for (q in search) {
									if (q.isNullOrEmpty()) {
										continue
									}

									if (name?.startsWith(q) == true || name?.contains(" $q") == true || tName != null && (tName.startsWith(q) || tName.contains(" $q"))) {
										found = 1
									}
									else if (username != null && username.startsWith(q)) {
										found = 2
									}

									if (found != 0) {
										if (found == 1) {
											resultArrayNames.add(AndroidUtilities.generateSearchName(firstName, lastName, q))
										}
										else {
											resultArrayNames.add(AndroidUtilities.generateSearchName("@$username", null, "@$q"))
										}

										resultArray2.add(o)

										break
									}
								}

								a++
							}
						}

						if (contactsCopy != null) {
							for (a in contactsCopy.indices) {
								val contact = contactsCopy[a]
								val user = messagesController.getUser(contact.user_id)

								if (user?.id == userConfig.getClientUserId()) {
									continue
								}

								val name = UserObject.getUserName(user).lowercase()
								var tName = LocaleController.getInstance().getTranslitString(name)

								if (name == tName) {
									tName = null
								}

								var found = 0

								for (q in search) {
									if (name.startsWith(q!!) || name.contains(" $q") || tName != null && (tName.startsWith(q) || tName.contains(" $q"))) {
										found = 1
									}
									else if (user?.username?.startsWith(q) == true) {
										found = 2
									}

									if (found != 0 && user != null) {
										if (found == 1) {
											resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q))
										}
										else {
											resultArrayNames.add(AndroidUtilities.generateSearchName("@" + user.username, null, "@$q"))
										}

										resultArray.add(user)
										resultMap.put(user.id, user)

										break
									}
								}
							}
						}

						updateSearchResults(resultArray, resultMap, resultArrayNames, resultArray2)
					}
				}
				else {
					searchInProgress = false
				}

				searchAdapterHelper.queryServerSearch(query, selectType != SELECT_TYPE_MEMBERS, allowChats = false, allowBots = true, allowSelf = false, canAddGroupsOnly = false, channelId = if (ChatObject.isChannel(currentChat)) chatId else 0, type = type, searchId = 1, onEnd = addContacts)
			})
		}

		private fun updateSearchResults(users: ArrayList<Any>, usersMap: LongSparseArray<TLObject>, names: ArrayList<CharSequence>, participants: ArrayList<TLObject>) {
			AndroidUtilities.runOnUIThread {
				if (!searching) {
					return@runOnUIThread
				}

				searchInProgress = false
				searchResult = users
				searchResultMap = usersMap
				searchResultNames = names
				searchAdapterHelper.mergeResults(searchResult)

				if (!ChatObject.isChannel(currentChat)) {
					val search = searchAdapterHelper.groupSearch
					search.clear()
					search.addAll(participants)
				}

				val oldItemCount = itemCount

				notifyDataSetChanged()

				if (itemCount > oldItemCount) {
					showItemsAnimated(oldItemCount)
				}

				if (!searchAdapterHelper.isSearchInProgress) {
					if (itemCount == 0) {
						emptyView?.showProgress(show = false, animated = true)
					}
				}
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType != 1
		}

		override fun getItemCount(): Int {
			return totalCount
		}

		override fun notifyDataSetChanged() {
			totalCount = 0

			var count = searchAdapterHelper.groupSearch.size

			if (count != 0) {
				groupStartRow = 0
				totalCount += count + 1
			}
			else {
				groupStartRow = -1
			}

			count = searchResult.size

			if (count != 0) {
				contactsStartRow = totalCount
				totalCount += count + 1
			}
			else {
				contactsStartRow = -1
			}

			count = searchAdapterHelper.globalSearch.size

			if (count != 0) {
				globalStartRow = totalCount
				totalCount += count + 1
			}
			else {
				globalStartRow = -1
			}

			if (searching && listView != null && listView?.adapter !== searchListViewAdapter) {
				listView?.setAnimateEmptyView(true, 0)
				listView?.adapter = searchListViewAdapter
				listView?.setFastScrollVisible(false)
				listView?.isVerticalScrollBarEnabled = true
			}

			super.notifyDataSetChanged()
		}

		fun removeUserId(userId: Long) {
			searchAdapterHelper.removeUserId(userId)

			val `object`: Any? = searchResultMap[userId]

			if (`object` != null) {
				searchResult.remove(`object`)
			}

			notifyDataSetChanged()
		}

		fun getItem(i: Int): TLObject? {
			@Suppress("NAME_SHADOWING") var i = i
			var count = searchAdapterHelper.groupSearch.size

			if (count != 0) {
				i -= if (count + 1 > i) {
					return if (i == 0) {
						null
					}
					else {
						searchAdapterHelper.groupSearch[i - 1]
					}
				}
				else {
					count + 1
				}
			}

			count = searchResult.size

			if (count != 0) {
				i -= if (count + 1 > i) {
					return if (i == 0) {
						null
					}
					else {
						searchResult[i - 1] as? TLObject
					}
				}
				else {
					count + 1
				}
			}

			count = searchAdapterHelper.globalSearch.size

			if (count != 0) {
				if (count + 1 > i) {
					return if (i == 0) {
						null
					}
					else {
						searchAdapterHelper.globalSearch[i - 1]
					}
				}
			}

			return null
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = when (viewType) {
				0 -> {
					val manageChatUserCell = ManageChatUserCell(mContext, 2, 2, selectType == SELECT_TYPE_MEMBERS)
					manageChatUserCell.setBackgroundColor(mContext.getColor(R.color.background))

					manageChatUserCell.setDelegate { cell, click ->
						val `object` = getItem(cell.tag as Int)

						if (`object` is ChannelParticipant) {
							return@setDelegate createMenuForParticipant(`object`, !click)
						}
						else {
							return@setDelegate false
						}
					}

					manageChatUserCell
				}

				1 -> {
					GraySectionCell(mContext)
				}

				else -> {
					GraySectionCell(mContext)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			@Suppress("NAME_SHADOWING") var position = position

			when (holder.itemViewType) {
				0 -> {
					val `object` = getItem(position)
					val peerObject: TLObject?
					var un: String? = null

					if (`object` is User) {
						peerObject = `object`
					}
					else if (`object` is ChannelParticipant) {
						val peerId = MessageObject.getPeerId(`object`.peer)

						if (peerId >= 0) {
							val user = messagesController.getUser(peerId)

							if (user != null) {
								un = user.username
							}

							peerObject = user
						}
						else {
							val chat = messagesController.getChat(-peerId)

							if (chat != null) {
								un = chat.username
							}

							peerObject = chat
						}
					}
					else if (`object` is ChatParticipant) {
						peerObject = messagesController.getUser(`object`.user_id)
					}
					else {
						return
					}

					var username: CharSequence? = null
					var name: CharSequence? = null
					var count = searchAdapterHelper.groupSearch.size
					var ok = false
					var nameSearch: String? = null

					if (count != 0) {
						if (count + 1 > position) {
							nameSearch = searchAdapterHelper.lastFoundChannel
							ok = true
						}
						else {
							position -= count + 1
						}
					}

					if (!ok) {
						count = searchResult.size

						if (count != 0) {
							if (count + 1 > position) {
								ok = true
								name = searchResultNames[position - 1]

								if (!un.isNullOrEmpty()) {
									if (name.toString().startsWith("@$un")) {
										username = name
										name = null
									}
								}
							}
							else {
								position -= count + 1
							}
						}
					}

					if (!ok && un != null) {
						count = searchAdapterHelper.globalSearch.size

						if (count != 0) {
							if (count + 1 > position) {
								var foundUserName = searchAdapterHelper.lastFoundUsername

								if (foundUserName?.startsWith("@") == true) {
									foundUserName = foundUserName.substring(1)
								}
								try {
									var index: Int

									val spannableStringBuilder = SpannableStringBuilder()
									spannableStringBuilder.append("@")
									spannableStringBuilder.append(un)

									if (AndroidUtilities.indexOfIgnoreCase(un, foundUserName).also { index = it } != -1) {
										var len = foundUserName?.length ?: 0

										if (index == 0) {
											len++
										}
										else {
											index++
										}

										spannableStringBuilder.setSpan(ForegroundColorSpan(mContext.getColor(R.color.brand)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
									}

									username = spannableStringBuilder
								}
								catch (e: Exception) {
									username = un
									e(e)
								}
							}
						}
					}

					if (nameSearch != null && un != null) {
						name = SpannableStringBuilder(un)

						val idx = AndroidUtilities.indexOfIgnoreCase(un, nameSearch)

						if (idx != -1) {
							name.setSpan(ForegroundColorSpan(mContext.getColor(R.color.brand)), idx, idx + nameSearch.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
						}
					}

					val userCell = holder.itemView as ManageChatUserCell
					userCell.tag = position
					userCell.setData(peerObject, name, username, false)
				}

				1 -> {
					val sectionCell = holder.itemView as GraySectionCell

					if (position == groupStartRow) {
						if (type == TYPE_BANNED) {
							sectionCell.setText(mContext.getString(R.string.ChannelBlockedUsers))
						}
						else if (type == TYPE_KICKED) {
							sectionCell.setText(mContext.getString(R.string.ChannelRestrictedUsers))
						}
						else {
							if (isChannel) {
								sectionCell.setText(mContext.getString(R.string.ChannelSubscribers))
							}
							else {
								sectionCell.setText(mContext.getString(R.string.ChannelMembers))
							}
						}
					}
					else if (position == globalStartRow) {
						sectionCell.setText(mContext.getString(R.string.GlobalSearch))
					}
					else if (position == contactsStartRow) {
						sectionCell.setText(mContext.getString(R.string.Contacts))
					}
				}
			}
		}

		override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
			if (holder.itemView is ManageChatUserCell) {
				holder.itemView.recycle()
			}
		}

		override fun getItemViewType(i: Int): Int {
			return if (i == globalStartRow || i == groupStartRow || i == contactsStartRow) {
				1
			}
			else {
				0
			}
		}
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val viewType = holder.itemViewType

			if (viewType == 7) {
				return ChatObject.canBlockUsers(currentChat)
			}
			else if (viewType == 0) {
				val cell = holder.itemView as ManageChatUserCell
				val `object` = cell.currentObject

				if (type != TYPE_ADMIN && `object` is User) {
					if (`object`.self) {
						return false
					}
				}

				return true
			}

			return viewType == 2 || viewType == 6
		}

		override fun getItemCount(): Int {
//			if (type == TYPE_KICKED && loadingUsers && !firstLoaded) {
//                return 0
//            }

			return rowCount
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					val manageChatUserCell = ManageChatUserCell(mContext, if (type == TYPE_BANNED || type == TYPE_KICKED) 7 else 6, if (type == TYPE_BANNED || type == TYPE_KICKED) 6 else 2, selectType == SELECT_TYPE_MEMBERS)
					manageChatUserCell.setBackgroundResource(R.color.background)

					manageChatUserCell.setDelegate { cell, click ->
						val participant = listViewAdapter?.getItem(cell.tag as Int)
						createMenuForParticipant(participant, !click)
					}

					view = manageChatUserCell
				}

				1 -> {
					view = TextInfoPrivacyCell(mContext)
				}

				2 -> {
					view = ManageChatTextCell(mContext)
					view.setBackgroundColor(ResourcesCompat.getColor(context!!.resources, R.color.background, null))
				}

				3 -> {
					view = ShadowSectionCell(mContext)
				}

				4 -> {
					view = TextInfoPrivacyCell(mContext)

					if (isChannel) {
						view.setText(mContext.getString(R.string.NoBlockedChannel2))
					}
					else {
						view.setText(mContext.getString(R.string.NoBlockedGroup2))
					}

					view.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.shadow))
				}

				5 -> {
					val headerCell = HeaderCell(mContext, 21, 11, 11, false)
					headerCell.setBackgroundResource(R.color.background)
					headerCell.height = 43
					view = headerCell
				}

				6 -> {
					view = TextSettingsCell(mContext)
					view.setBackgroundResource(R.color.background)
				}

				7 -> {
					view = TextCheckCell2(mContext)
					view.setBackgroundResource(R.color.background)
				}

				8 -> {
					view = GraySectionCell(mContext)
					view.setBackground(null)
				}

				10 -> {
					view = LoadingCell(mContext, AndroidUtilities.dp(40f), AndroidUtilities.dp(120f))
				}

				11 -> {
					val flickerLoadingView = FlickerLoadingView(mContext)
					flickerLoadingView.setIsSingleCell(true)
					flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE)
					flickerLoadingView.showDate(false)
					flickerLoadingView.paddingLeft = AndroidUtilities.dp(5f)
					flickerLoadingView.setBackgroundResource(R.color.background)
					flickerLoadingView.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

					view = flickerLoadingView
				}

				9 -> {
					view = ChooseView(mContext)
					view.setBackgroundResource(R.color.background)
				}

				else -> {
					view = ChooseView(mContext)
					view.setBackgroundResource(R.color.background)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				0 -> {
					val userCell = holder.itemView as ManageChatUserCell
					userCell.tag = position

					val item = getItem(position)
					val lastRow: Int
					var showJoined = false

					when (position) {
						in participantsStartRow..<participantsEndRow -> {
							lastRow = participantsEndRow
							showJoined = ChatObject.isChannel(currentChat) && currentChat?.megagroup != true
						}

						in contactsStartRow..<contactsEndRow -> {
							lastRow = contactsEndRow
							showJoined = ChatObject.isChannel(currentChat) && currentChat?.megagroup != true
						}

						else -> {
							lastRow = botEndRow
						}
					}

					val peerId: Long
					val kickedBy: Long
					val promotedBy: Long
					val bannedRights: TL_chatBannedRights?
					val banned: Boolean
					val creator: Boolean
					val admin: Boolean
					val joined: Int

					when (item) {
						is ChannelParticipant -> {
							peerId = MessageObject.getPeerId(item.peer)
							kickedBy = item.kicked_by
							promotedBy = item.promoted_by
							bannedRights = item.banned_rights
							joined = item.date
							banned = item is TL_channelParticipantBanned
							creator = item is TL_channelParticipantCreator
							admin = item is TL_channelParticipantAdmin
						}

						is ChatParticipant -> {
							peerId = item.user_id
							joined = item.date
							kickedBy = 0
							promotedBy = 0
							bannedRights = null
							banned = false
							creator = item is TL_chatParticipantCreator
							admin = item is TL_chatParticipantAdmin
						}

						else -> {
							return
						}
					}

					val `object` = if (peerId > 0) {
						messagesController.getUser(peerId)
					}
					else {
						messagesController.getChat(-peerId)
					}

					if (`object` != null) {
						when (type) {
							TYPE_KICKED -> {
								userCell.setData(`object`, null, formatUserPermissions(bannedRights), position != lastRow - 1)
							}

							TYPE_BANNED -> {
								var role: String? = null

								if (banned) {
									val user1 = messagesController.getUser(kickedBy)

									if (user1 != null) {
										role = LocaleController.formatString("UserRemovedBy", R.string.UserRemovedBy, UserObject.getUserName(user1))
									}
								}

								userCell.setData(`object`, null, role, position != lastRow - 1)
							}

							TYPE_ADMIN -> {
								var role: String? = null

								if (creator) {
									role = mContext.getString(R.string.ChannelCreator)
								}
								else if (admin) {
									val user1 = messagesController.getUser(promotedBy)

									if (user1 != null) {
										role = if (user1.id == peerId) {
											mContext.getString(R.string.ChannelAdministrator)
										}
										else {
											LocaleController.formatString("EditAdminPromotedBy", R.string.EditAdminPromotedBy, UserObject.getUserName(user1))
										}
									}
								}

								userCell.setData(`object`, null, role, position != lastRow - 1)
							}

							TYPE_USERS -> {
								val status = if (showJoined && joined != 0) {
									LocaleController.formatJoined(joined.toLong())
								}
								else {
									null
								}

								userCell.setData(`object`, null, status, position != lastRow - 1)
							}
						}
					}
				}

				1 -> {
					val privacyCell = holder.itemView as TextInfoPrivacyCell

					if (position == participantsInfoRow) {
						when (type) {
							TYPE_BANNED, TYPE_KICKED -> {
								if (isChannel) {
									privacyCell.setText(mContext.getString(R.string.NoBlockedChannel2))
								}
								else {
									privacyCell.setText(mContext.getString(R.string.NoBlockedGroup2))
								}

								privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.shadow))
							}

							TYPE_ADMIN -> {
								if (addNewRow != -1) {
									if (isChannel) {
										privacyCell.setText(mContext.getString(R.string.ChannelAdminsInfo))
									}
									else {
										privacyCell.setText(mContext.getString(R.string.MegaAdminsInfo))
									}
								}
								else {
									privacyCell.setText("")
								}

								privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.shadow))
							}

							TYPE_USERS -> {
								if (!isChannel || selectType != SELECT_TYPE_MEMBERS) {
									privacyCell.setText("")
								}
								else {
									privacyCell.setText(mContext.getString(R.string.ChannelMembersInfo))
								}

								privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.shadow))
							}
						}
					}
					else if (position == slowmodeInfoRow) {
						privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, mContext.getColor(R.color.shadow))

						val seconds = getSecondsForIndex(selectedSlowMode)

						if (info == null || seconds == 0) {
							privacyCell.setText(mContext.getString(R.string.SlowmodeInfoOff))
						}
						else {
							privacyCell.setText(LocaleController.formatString("SlowmodeInfoSelected", R.string.SlowmodeInfoSelected, formatSeconds(seconds)))
						}
					}
					else if (position == gigaInfoRow) {
						privacyCell.setText(mContext.getString(R.string.BroadcastGroupConvertInfo))
					}
				}

				2 -> {
					val actionCell = holder.itemView as ManageChatTextCell
					actionCell.setColors(mContext.getColor(R.color.dark_gray), mContext.getColor(R.color.text))

					if (position == addNewRow) {
						if (type == TYPE_KICKED) {
							actionCell.setColors(mContext.getColor(R.color.brand), mContext.getColor(R.color.brand))
							actionCell.setText(mContext.getString(R.string.ChannelAddException), null, R.drawable.msg_contact_add, participantsStartRow != -1)
						}
						else if (type == TYPE_BANNED) {
							actionCell.setText(mContext.getString(R.string.ChannelBlockUser), null, R.drawable.ic_removed_users, false)
						}
						else if (type == TYPE_ADMIN) {
							actionCell.setColors(mContext.getColor(R.color.brand), mContext.getColor(R.color.brand))
							val showDivider = !(loadingUsers && !firstLoaded)
							actionCell.setText(mContext.getString(R.string.ChannelAddAdmin), null, R.drawable.msg_admin_add, showDivider)
						}
						else if (type == TYPE_USERS) {
							actionCell.setColors(mContext.getColor(R.color.brand), mContext.getColor(R.color.brand))

							val showDivider = addNew2Row != -1 || !(loadingUsers && !firstLoaded) && membersHeaderRow == -1 && participants.isNotEmpty()

							if (isChannel) {
								actionCell.setText(mContext.getString(R.string.AddSubscriber), null, R.drawable.msg_contact_add, showDivider)
							}
							else {
								actionCell.setText(mContext.getString(R.string.AddMember), null, R.drawable.msg_contact_add, showDivider)
							}
						}
					}
					else if (position == recentActionsRow) {
						actionCell.setText(mContext.getString(R.string.EventLog), null, R.drawable.msg_log, false)
					}
					else if (position == addNew2Row) {
						actionCell.setColors(mContext.getColor(R.color.brand), mContext.getColor(R.color.brand))
						val showDivider = !(loadingUsers && !firstLoaded) && membersHeaderRow == -1 && participants.isNotEmpty()
						actionCell.setText(mContext.getString(R.string.ChannelInviteViaLink), null, R.drawable.msg_link2, showDivider)
					}
					else if (position == gigaConvertRow) {
						actionCell.setColors(mContext.getColor(R.color.brand), mContext.getColor(R.color.brand))
						actionCell.setText(mContext.getString(R.string.BroadcastGroupConvert), null, R.drawable.msg_channel, false)
					}
				}

				3 -> if (position == addNewSectionRow || type == TYPE_KICKED && position == participantsDividerRow && addNewRow == -1 && participantsStartRow == -1) {
					holder.itemView.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.shadow))
				}
				else {
					holder.itemView.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, mContext.getColor(R.color.shadow))
				}

				5 -> {
					val headerCell = holder.itemView as HeaderCell

					if (position == restricted1SectionRow) {
						if (type == TYPE_BANNED) {
							val count = if (info != null) info!!.kicked_count else participants.size

							if (count != 0) {
								headerCell.setText(LocaleController.formatPluralString("RemovedUser", count))
							}
							else {
								headerCell.setText(mContext.getString(R.string.ChannelBlockedUsers))
							}
						}
						else {
							headerCell.setText(mContext.getString(R.string.ChannelRestrictedUsers))
						}
					}
					else if (position == permissionsSectionRow) {
						headerCell.setText(mContext.getString(R.string.ChannelPermissionsHeader))
					}
					else if (position == slowmodeRow) {
						headerCell.setText(mContext.getString(R.string.Slowmode))
					}
					else if (position == gigaHeaderRow) {
						headerCell.setText(mContext.getString(R.string.BroadcastGroup))
					}
				}

				6 -> {
					val settingsCell = holder.itemView as TextSettingsCell
					settingsCell.setTextAndValue(mContext.getString(R.string.ChannelBlacklist), String.format(Locale.getDefault(), "%d", info?.kicked_count ?: 0), false)
				}

				7 -> {
					val checkCell = holder.itemView as TextCheckCell2

					when (position) {
						changeInfoRow -> {
							checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsChangeInfo), !defaultBannedRights.change_info && currentChat?.username.isNullOrEmpty(), false)
						}

						addUsersRow -> {
							checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsInviteUsers), !defaultBannedRights.invite_users, true)
						}

						pinMessagesRow -> {
							checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsPinMessages), !defaultBannedRights.pin_messages && currentChat?.username.isNullOrEmpty(), true)
						}

						sendMessagesRow -> {
							checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsSend), !defaultBannedRights.send_messages, true)
						}

						sendMediaRow -> {
							checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsSendMedia), !defaultBannedRights.send_media, true)
						}

						sendStickersRow -> {
							checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsSendStickers), !defaultBannedRights.send_stickers, true)
						}

						embedLinksRow -> {
							checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsEmbedLinks), !defaultBannedRights.embed_links, true)
						}

						sendPollsRow -> {
							checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsSendPolls), !defaultBannedRights.send_polls, true)
						}
					}

					if (position == sendMediaRow || position == sendStickersRow || position == embedLinksRow || position == sendPollsRow) {
						checkCell.isEnabled = !defaultBannedRights.send_messages && !defaultBannedRights.view_messages
					}
					else if (position == sendMessagesRow) {
						checkCell.isEnabled = !defaultBannedRights.view_messages
					}
					if (ChatObject.canBlockUsers(currentChat)) {
						if (position == addUsersRow && !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE) || position == pinMessagesRow && !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_PIN) || position == changeInfoRow && !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_CHANGE_INFO) || !currentChat?.username.isNullOrEmpty() && (position == pinMessagesRow || position == changeInfoRow)) {
							checkCell.setIcon(R.drawable.permission_locked)
						}
						else {
							checkCell.setIcon(0)
						}
					}
					else {
						checkCell.setIcon(0)
					}
				}

				8 -> {
					val sectionCell = holder.itemView as GraySectionCell

					if (position == membersHeaderRow) {
						if (ChatObject.isChannel(currentChat) && currentChat?.megagroup != true) {
							sectionCell.setText(mContext.getString(R.string.ChannelOtherSubscribers))
						}
						else {
							sectionCell.setText(mContext.getString(R.string.ChannelOtherMembers))
						}
					}
					else if (position == botHeaderRow) {
						sectionCell.setText(mContext.getString(R.string.ChannelBots))
					}
					else if (position == contactsHeaderRow) {
						if (ChatObject.isChannel(currentChat) && currentChat?.megagroup != true) {
							sectionCell.setText(mContext.getString(R.string.ChannelContacts))
						}
						else {
							sectionCell.setText(mContext.getString(R.string.GroupContacts))
						}
					}
					else if (position == loadingHeaderRow) {
						sectionCell.setText("")
					}
				}

				11 -> {
					val flickerLoadingView = holder.itemView as FlickerLoadingView

					if (type == TYPE_BANNED) {
						flickerLoadingView.setItemsCount(info?.kicked_count ?: 1)
					}
					else {
						flickerLoadingView.setItemsCount(1)
					}
				}
			}
		}

		override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
			if (holder.itemView is ManageChatUserCell) {
				holder.itemView.recycle()
			}
		}

		override fun getItemViewType(position: Int): Int {
			return when (position) {
				addNewRow, addNew2Row, recentActionsRow, gigaConvertRow -> 2
				in participantsStartRow..<participantsEndRow, in botStartRow..<botEndRow, in contactsStartRow..<contactsEndRow -> 0
				addNewSectionRow, participantsDividerRow, participantsDivider2Row -> 3
				restricted1SectionRow, permissionsSectionRow, slowmodeRow, gigaHeaderRow -> 5
				participantsInfoRow, slowmodeInfoRow, gigaInfoRow -> 1
				blockedEmptyRow -> 4
				removedUsersRow -> 6
				changeInfoRow, addUsersRow, pinMessagesRow, sendMessagesRow, sendMediaRow, sendStickersRow, embedLinksRow, sendPollsRow -> 7
				membersHeaderRow, contactsHeaderRow, botHeaderRow, loadingHeaderRow -> 8
				slowmodeSelectRow -> 9
				loadingProgressRow -> 10
				loadingUserCellRow -> 11
				else -> 0
			}
		}

		fun getItem(position: Int): TLObject? {
			return when (position) {
				in participantsStartRow..<participantsEndRow -> {
					participants[position - participantsStartRow]
				}

				in contactsStartRow..<contactsEndRow -> {
					contacts[position - contactsStartRow]
				}

				in botStartRow..<botEndRow -> {
					bots[position - botStartRow]
				}

				else -> {
					null
				}
			}
		}
	}

	inner class DiffCallback : DiffUtil.Callback() {
		val oldParticipants = ArrayList<TLObject>()
		val oldBots = ArrayList<TLObject>()
		val oldContacts = ArrayList<TLObject>()
		var oldRowCount = 0
		var oldPositionToItem = SparseIntArray()
		var newPositionToItem = SparseIntArray()
		var oldParticipantsStartRow = 0
		var oldParticipantsEndRow = 0
		var oldContactsStartRow = 0
		var oldContactsEndRow = 0
		var oldBotStartRow = 0
		var oldBotEndRow = 0

		override fun getOldListSize(): Int {
			return oldRowCount
		}

		override fun getNewListSize(): Int {
			return rowCount
		}

		override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
			return if (oldItemPosition in oldBotStartRow..<oldBotEndRow && newItemPosition >= botStartRow && newItemPosition < botEndRow) {
				oldBots[oldItemPosition - oldBotStartRow] == bots[newItemPosition - botStartRow]
			}
			else if (oldItemPosition in oldContactsStartRow..<oldContactsEndRow && newItemPosition >= contactsStartRow && newItemPosition < contactsEndRow) {
				oldContacts[oldItemPosition - oldContactsStartRow] == contacts[newItemPosition - contactsStartRow]
			}
			else if (oldItemPosition in oldParticipantsStartRow..<oldParticipantsEndRow && newItemPosition >= participantsStartRow && newItemPosition < participantsEndRow) {
				oldParticipants[oldItemPosition - oldParticipantsStartRow] == participants[newItemPosition - participantsStartRow]
			}
			else {
				oldPositionToItem[oldItemPosition] == newPositionToItem[newItemPosition]
			}
		}

		override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
			return if (areItemsTheSame(oldItemPosition, newItemPosition)) {
				restricted1SectionRow != newItemPosition
			}
			else {
				false
			}
		}

		fun fillPositions(sparseIntArray: SparseIntArray) {
			sparseIntArray.clear()

			var pointer = 0

			put(++pointer, recentActionsRow, sparseIntArray)
			put(++pointer, addNewRow, sparseIntArray)
			put(++pointer, addNew2Row, sparseIntArray)
			put(++pointer, addNewSectionRow, sparseIntArray)
			put(++pointer, restricted1SectionRow, sparseIntArray)
			put(++pointer, participantsDividerRow, sparseIntArray)
			put(++pointer, participantsDivider2Row, sparseIntArray)
			put(++pointer, gigaHeaderRow, sparseIntArray)
			put(++pointer, gigaConvertRow, sparseIntArray)
			put(++pointer, gigaInfoRow, sparseIntArray)
			put(++pointer, participantsInfoRow, sparseIntArray)
			put(++pointer, blockedEmptyRow, sparseIntArray)
			put(++pointer, permissionsSectionRow, sparseIntArray)
			put(++pointer, sendMessagesRow, sparseIntArray)
			put(++pointer, sendMediaRow, sparseIntArray)
			put(++pointer, sendStickersRow, sparseIntArray)
			put(++pointer, sendPollsRow, sparseIntArray)
			put(++pointer, embedLinksRow, sparseIntArray)
			put(++pointer, addUsersRow, sparseIntArray)
			put(++pointer, pinMessagesRow, sparseIntArray)
			put(++pointer, changeInfoRow, sparseIntArray)
			put(++pointer, removedUsersRow, sparseIntArray)
			put(++pointer, contactsHeaderRow, sparseIntArray)
			put(++pointer, botHeaderRow, sparseIntArray)
			put(++pointer, membersHeaderRow, sparseIntArray)
			put(++pointer, slowmodeRow, sparseIntArray)
			put(++pointer, slowmodeSelectRow, sparseIntArray)
			put(++pointer, slowmodeInfoRow, sparseIntArray)
			put(++pointer, loadingProgressRow, sparseIntArray)
			put(++pointer, loadingUserCellRow, sparseIntArray)
			put(++pointer, loadingHeaderRow, sparseIntArray)
		}

		private fun put(id: Int, position: Int, sparseIntArray: SparseIntArray) {
			if (position >= 0) {
				sparseIntArray.put(position, id)
			}
		}
	}

	companion object {
		const val TYPE_BANNED = 0
		const val TYPE_ADMIN = 1
		const val TYPE_USERS = 2
		const val TYPE_KICKED = 3
		const val SELECT_TYPE_MEMBERS = 0 // "Subscribers" / "Members"
		const val SELECT_TYPE_ADMIN = 1 // "Add Admin"
		const val SELECT_TYPE_BLOCK = 2 // "Remove User"
		const val SELECT_TYPE_EXCEPTION = 3 // "Add Exception"
		private const val SEARCH_BUTTON = 0
		private const val DONE_BUTTON = 1
	}
}
