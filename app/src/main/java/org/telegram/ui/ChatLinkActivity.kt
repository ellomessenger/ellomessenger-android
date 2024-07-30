/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ChatObject.isChannel
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.utils.gone
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.TL_channels_getGroupsForDiscussion
import org.telegram.tgnet.TLRPC.TL_channels_setDiscussionGroup
import org.telegram.tgnet.TLRPC.TL_inputChannelEmpty
import org.telegram.tgnet.TLRPC.messages_Chats
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ManageChatTextCell
import org.telegram.ui.Cells.ManageChatUserCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.JoinToSendSettingsView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.group.GroupCreateFinalActivity
import org.telegram.ui.group.GroupCreateFinalActivity.GroupCreateFinalActivityDelegate
import java.util.Locale

class ChatLinkActivity(private var currentChatId: Long) : BaseFragment(), NotificationCenterDelegate {
	private var listViewAdapter: ListAdapter? = null
	private var listView: RecyclerListView? = null
	private var searchItem: ActionBarMenuItem? = null
	private var emptyView: EmptyTextProgressView? = null
	private var searchAdapter: SearchAdapter? = null
	private var currentChat: Chat?
	private var info: ChatFull? = null
	private var waitingForFullChat: Chat? = null
	private var waitingForFullChatProgressAlert: AlertDialog? = null
	private val isChannel: Boolean
	private var chats = ArrayList<Chat>()
	private var loadingChats = false
	private var chatsLoaded = false
	private var joinToSendSettings: JoinToSendSettingsView? = null
	private var helpRow = 0
	private var createChatRow = 0
	private var chatStartRow = 0
	private var chatEndRow = 0
	private var removeChatRow = 0
	private var detailRow = 0
	private var joinToSendRow = 0
	private var rowCount = 0
	private var searchWas = false
	private var searching = false

	private class EmptyView(context: Context) : LinearLayout(context), NotificationCenterDelegate {
		private val stickerView: BackupImageView
		private val drawable: RLottieDrawable
		private val currentAccount = UserConfig.selectedAccount

		init {
			setPadding(0, AndroidUtilities.dp(12f), 0, AndroidUtilities.dp(12f))

			orientation = VERTICAL

			drawable = RLottieDrawable(R.raw.panda_discussion_group_setup, "panda_discussion_group_setup", AndroidUtilities.dp(104f), AndroidUtilities.dp(104f))
			drawable.setAutoRepeat(1)

			stickerView = BackupImageView(context)
			stickerView.setImageDrawable(drawable)

			addView(stickerView, createLinear(104, 104, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 2, 0, 0))
		}

		private fun setSticker() {
			var set = MediaDataController.getInstance(currentAccount).getStickerSetByName(stickerSetName)

			if (set == null) {
				set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(stickerSetName)
			}

			if (set != null && set.documents.size >= 3) {
				val document = set.documents[2]
				val imageLocation = ImageLocation.getForDocument(document)

				stickerView.setImage(imageLocation, "104_104", "tgs", drawable, set)
			}
			else {
				MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(stickerSetName, false, set == null)
				stickerView.setImageDrawable(drawable)
			}
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			setSticker()
			NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad)
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()
			NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad)
		}

		override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
			if (id == NotificationCenter.diceStickersDidLoad) {
				val name = args[0] as String

				if (stickerSetName == name) {
					setSticker()
				}
			}
		}

		companion object {
			private const val stickerSetName = AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME
		}
	}

	private fun updateRows() {
		currentChat = messagesController.getChat(currentChatId) ?: return
		rowCount = 0
		helpRow = -1
		createChatRow = -1
		chatStartRow = -1
		chatEndRow = -1
		removeChatRow = -1
		detailRow = -1
		joinToSendRow = -1
		helpRow = rowCount++

		if (isChannel) {
			if (info?.linked_chat_id == 0L) {
				createChatRow = rowCount++
			}

			chatStartRow = rowCount
			rowCount += chats.size
			chatEndRow = rowCount

			if (info?.linked_chat_id != 0L) {
				createChatRow = rowCount++
			}
		}
		else {
			chatStartRow = rowCount
			rowCount += chats.size
			chatEndRow = rowCount
			createChatRow = rowCount++
		}

		detailRow = rowCount++

		if (!isChannel || chats.size > 0 && info?.linked_chat_id != 0L) {
			val chat = if (isChannel) chats[0] else currentChat

			if (chat != null && (chat.username.isNullOrEmpty() || isChannel) && (chat.creator || chat.admin_rights != null && chat.admin_rights.ban_users)) {
				joinToSendRow = rowCount++
			}
		}

		listViewAdapter?.notifyDataSetChanged()

		searchItem?.visibility = if (chats.size > 10) View.VISIBLE else View.GONE
	}

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()

		notificationCenter.let {
			it.addObserver(this, NotificationCenter.chatInfoDidLoad)
			it.addObserver(this, NotificationCenter.updateInterfaces)
		}

		loadChats()

		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		notificationCenter.let {
			it.removeObserver(this, NotificationCenter.chatInfoDidLoad)
			it.removeObserver(this, NotificationCenter.updateInterfaces)
		}
	}

	private var joinToSendProgress = false
	private var joinRequestProgress = false

	init {
		currentChat = messagesController.getChat(currentChatId)
		isChannel = isChannel(currentChat) && currentChat?.megagroup != true
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.chatInfoDidLoad -> {
				val chatFull = args[0] as ChatFull

				if (chatFull.id == currentChatId) {
					info = chatFull
					loadChats()
					updateRows()
				}
				else if (waitingForFullChat?.id == chatFull.id) {
					runCatching {
						waitingForFullChatProgressAlert?.dismiss()
					}

					waitingForFullChatProgressAlert = null

					waitingForFullChat?.let {
						showLinkAlert(it, false)
					}

					waitingForFullChat = null
				}
			}

			NotificationCenter.updateInterfaces -> {
				val updateMask = args[0] as Int

				if (updateMask and MessagesController.UPDATE_MASK_CHAT != 0 && currentChat != null) {
					val newCurrentChat = messagesController.getChat(currentChat?.id)

					if (newCurrentChat != null) {
						currentChat = newCurrentChat
					}

					if (chats.size > 0) {
						val linkedChat = messagesController.getChat(chats[0].id)

						if (linkedChat != null) {
							chats[0] = linkedChat
						}
					}

					val chat = if (isChannel) chats.firstOrNull() else currentChat

					if (chat != null && joinToSendSettings != null) {
						if (!joinRequestProgress) {
							joinToSendSettings?.setJoinRequest(chat.join_request)
						}

						if (!joinToSendProgress) {
							joinToSendSettings?.setJoinToSend(chat.join_to_send)
						}
					}
				}
			}
		}
	}

	override fun createView(context: Context): View? {
		searching = false
		searchWas = false

		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)
		actionBar?.setTitle(context.getString(R.string.Discussion))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		val menu = actionBar?.createMenu()

		searchItem = menu?.addItem(search_button, R.drawable.ic_search_menu)?.setIsSearchField(true)?.setActionBarMenuItemSearchListener(object : ActionBarMenuItemSearchListener() {
			override fun onSearchExpand() {
				searching = true
				emptyView?.setShowAtCenter(true)
			}

			override fun onSearchCollapse() {
				searchAdapter?.searchDialogs(null)
				searching = false
				searchWas = false

				listView?.adapter = listViewAdapter

				listViewAdapter?.notifyDataSetChanged()

				listView?.setFastScrollVisible(true)
				listView?.isVerticalScrollBarEnabled = false

				emptyView?.setShowAtCenter(false)

				fragmentView?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))

				emptyView?.showProgress()
			}

			override fun onTextChanged(editText: EditText) {
				if (searchAdapter == null) {
					return
				}

				val text = editText.text?.toString()

				if (!text.isNullOrEmpty()) {
					searchWas = true

					if (listView != null && listView?.adapter !== searchAdapter) {
						listView?.adapter = searchAdapter
						fragmentView?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
						searchAdapter?.notifyDataSetChanged()
						listView?.setFastScrollVisible(false)
						listView?.isVerticalScrollBarEnabled = true
						emptyView?.showProgress()
					}
				}

				searchAdapter?.searchDialogs(text)
			}
		})

		searchItem?.setSearchFieldHint(context.getString(R.string.Search))

		searchAdapter = SearchAdapter(context)

		val frameLayout = FrameLayout(context)

		fragmentView = frameLayout
		fragmentView?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))

		emptyView = EmptyTextProgressView(context)
		emptyView?.showProgress()
		emptyView?.setText(context.getString(R.string.NoResult))

		frameLayout.addView(emptyView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView = RecyclerListView(context)
		listView?.setEmptyView(emptyView)
		listView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
		listView?.adapter = ListAdapter(context).also { listViewAdapter = it }
		listView?.verticalScrollbarPosition = if (LocaleController.isRTL) View.SCROLLBAR_POSITION_LEFT else View.SCROLLBAR_POSITION_RIGHT

		frameLayout.addView(listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView?.setOnItemClickListener { _, position ->
			val parentActivity = parentActivity ?: return@setOnItemClickListener

			val chat = if (listView?.adapter === searchAdapter) {
				searchAdapter?.getItem(position)
			}
			else if (position in chatStartRow until chatEndRow) {
				chats[position - chatStartRow]
			}
			else {
				null
			}

			if (chat != null) {
				if (isChannel && info?.linked_chat_id == 0L) {
					showLinkAlert(chat, true)
				}
				else {
					val args = Bundle()
					args.putLong("chat_id", chat.id)
					presentFragment(ChatActivity(args))
				}

				return@setOnItemClickListener
			}

			if (position == createChatRow) {
				if (isChannel && info?.linked_chat_id == 0L) {
					val args = Bundle()

					val array = longArrayOf(userConfig.getClientUserId())

					args.putLongArray("result", array)
					args.putInt("chatType", ChatObject.CHAT_TYPE_MEGAGROUP)

					val activity = GroupCreateFinalActivity(args)

					activity.setDelegate(object : GroupCreateFinalActivityDelegate {
						override fun didStartChatCreation() {
							// unused
						}

						override fun didFinishChatCreation(fragment: GroupCreateFinalActivity?, chatId: Long) {
							linkChat(messagesController.getChat(chatId), fragment)
						}

						override fun didFailChatCreation() {
							// unused
						}
					})

					presentFragment(activity)
				}
				else {
					if (chats.isEmpty()) {
						return@setOnItemClickListener
					}

					val c = chats[0]
					val builder = AlertDialog.Builder(parentActivity)
					val title: String
					val message: String

					if (isChannel) {
						title = context.getString(R.string.DiscussionUnlinkGroup)
						message = LocaleController.formatString("DiscussionUnlinkChannelAlert", R.string.DiscussionUnlinkChannelAlert, c.title)
					}
					else {
						title = context.getString(R.string.DiscussionUnlinkChannel)
						message = LocaleController.formatString("DiscussionUnlinkGroupAlert", R.string.DiscussionUnlinkGroupAlert, c.title)
					}

					builder.setTitle(title)
					builder.setMessage(AndroidUtilities.replaceTags(message))

					builder.setPositiveButton(context.getString(R.string.DiscussionUnlink)) { _, _ ->
						if (!isChannel || info?.linked_chat_id != 0L) {
							var progressDialog: AlertDialog? = AlertDialog(parentActivity, 3)
							val req = TL_channels_setDiscussionGroup()

							if (isChannel) {
								req.broadcast = MessagesController.getInputChannel(currentChat)
								req.group = TL_inputChannelEmpty()
							}
							else {
								req.broadcast = TL_inputChannelEmpty()
								req.group = MessagesController.getInputChannel(currentChat)
							}

							val requestId = connectionsManager.sendRequest(req) { _, _ ->
								AndroidUtilities.runOnUIThread {
									runCatching {
										progressDialog?.dismiss()
									}

									progressDialog = null

									info?.linked_chat_id = 0

									NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, false)

									AndroidUtilities.runOnUIThread({
										messagesController.loadFullChat(currentChatId, 0, true)
									}, 1000)

									if (!isChannel) {
										finishFragment()
									}
								}
							}

							AndroidUtilities.runOnUIThread({
								if (progressDialog == null) {
									return@runOnUIThread
								}

								progressDialog?.setOnCancelListener {
									ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true)
								}

								showDialog(progressDialog)

							}, 500)
						}
					}

					builder.setNegativeButton(context.getString(R.string.Cancel), null)

					val dialog = builder.create()

					showDialog(dialog)

					val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
					button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
				}
			}
		}

		updateRows()

		return fragmentView
	}

	private fun showLinkAlert(chat: Chat, query: Boolean) {
		val parentActivity = parentActivity ?: return

		val chatFull = messagesController.getChatFull(chat.id)

		if (chatFull == null) {
			if (query) {
				messagesController.loadFullChat(chat.id, 0, true)

				waitingForFullChat = chat
				waitingForFullChatProgressAlert = AlertDialog(parentActivity, 3)

				AndroidUtilities.runOnUIThread({
					if (waitingForFullChatProgressAlert == null) {
						return@runOnUIThread
					}

					waitingForFullChatProgressAlert?.setOnCancelListener {
						waitingForFullChat = null
					}

					showDialog(waitingForFullChatProgressAlert)
				}, 500)
			}

			return
		}

		val builder = AlertDialog.Builder(parentActivity)

		val messageTextView = TextView(parentActivity)
		messageTextView.setTextColor(ResourcesCompat.getColor(parentActivity.resources, R.color.text, null))
		messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		messageTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP

		var message = if (chat.username.isNullOrEmpty()) {
			LocaleController.formatString("DiscussionLinkGroupPublicPrivateAlert", R.string.DiscussionLinkGroupPublicPrivateAlert, chat.title, currentChat?.title)
		}
		else {
			if (TextUtils.isEmpty(currentChat!!.username)) {
				LocaleController.formatString("DiscussionLinkGroupPrivateAlert", R.string.DiscussionLinkGroupPrivateAlert, chat.title, currentChat?.title)
			}
			else {
				LocaleController.formatString("DiscussionLinkGroupPublicAlert", R.string.DiscussionLinkGroupPublicAlert, chat.title, currentChat?.title)
			}
		}

		if (chatFull.hidden_prehistory) {
			message += "\n\n${parentActivity.getString(R.string.DiscussionLinkGroupAlertHistory)}"
		}

		messageTextView.text = AndroidUtilities.replaceTags(message)

		val frameLayout2 = FrameLayout(parentActivity)

		builder.setView(frameLayout2)

		val avatarDrawable = AvatarDrawable()
		avatarDrawable.setTextSize(AndroidUtilities.dp(12f))

		val imageView = BackupImageView(parentActivity)
		imageView.setRoundRadius(AndroidUtilities.dp(20f))

		frameLayout2.addView(imageView, createFrame(40, 40f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 22f, 5f, 22f, 0f))

		val textView = TextView(parentActivity)
		textView.setTextColor(ResourcesCompat.getColor(parentActivity.resources, R.color.text, null))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		textView.typeface = Theme.TYPEFACE_BOLD
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.text = chat.title

		frameLayout2.addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 21 else 76).toFloat(), 11f, (if (LocaleController.isRTL) 76 else 21).toFloat(), 0f))
		frameLayout2.addView(messageTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24f, 57f, 24f, 9f))

		avatarDrawable.setInfo(chat)

		imageView.setForUserOrChat(chat, avatarDrawable)

		builder.setPositiveButton(parentActivity.getString(R.string.DiscussionLinkGroup)) { _, _ ->
			if (chatFull.hidden_prehistory) {
				messagesController.toggleChannelInvitesHistory(chat.id, false)
			}

			linkChat(chat, null)
		}

		builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

		showDialog(builder.create())
	}

	private fun linkChat(chat: Chat?, createFragment: BaseFragment?) {
		if (chat == null) {
			return
		}

		if (!isChannel(chat)) {
			messagesController.convertToMegaGroup(parentActivity, chat.id, this, {
				if (it != 0L) {
					messagesController.toggleChannelInvitesHistory(it, false)

					linkChat(messagesController.getChat(it), createFragment)
				}
			}, null)

			return
		}

		var progressDialog = if (createFragment != null) null else parentActivity?.let { AlertDialog(it, 3) }

		val req = TL_channels_setDiscussionGroup()
		req.broadcast = MessagesController.getInputChannel(currentChat)
		req.group = MessagesController.getInputChannel(chat)

		val requestId = connectionsManager.sendRequest(req, { _, _ ->
			AndroidUtilities.runOnUIThread {
				runCatching {
					progressDialog?.dismiss()
				}

				progressDialog = null

				info?.linked_chat_id = chat.id

				NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, false)

				AndroidUtilities.runOnUIThread({
					messagesController.loadFullChat(currentChatId, 0, true)
				}, 1000)

				if (createFragment != null) {
					removeSelfFromStack()
					createFragment.finishFragment()
				}
				else {
					finishFragment()
				}
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)

		AndroidUtilities.runOnUIThread({
			if (progressDialog == null) {
				return@runOnUIThread
			}

			progressDialog?.setOnCancelListener {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true)
			}

			showDialog(progressDialog)
		}, 500)
	}

	fun setInfo(chatFull: ChatFull?) {
		info = chatFull
	}

	private fun loadChats() {
		if (info?.linked_chat_id != 0L) {
			chats.clear()

			val chat = messagesController.getChat(info?.linked_chat_id)

			if (chat != null) {
				chats.add(chat)
			}

			searchItem?.gone()
		}

		if (loadingChats || !isChannel || info?.linked_chat_id != 0L) {
			return
		}

		loadingChats = true

		val req = TL_channels_getGroupsForDiscussion()

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is messages_Chats) {
					messagesController.putChats(response.chats, false)
					chats = response.chats
				}

				loadingChats = false
				chatsLoaded = true

				updateRows()
			}
		}
	}

	override fun onResume() {
		super.onResume()
		listViewAdapter?.notifyDataSetChanged()
	}

	inner class HintInnerCell(context: Context) : FrameLayout(context) {
		private val emptyView: EmptyView
		private val messageTextView: TextView

		init {
			emptyView = EmptyView(context)

			addView(emptyView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0f, 10f, 0f, 0f))

			messageTextView = TextView(context)
			messageTextView.setTextColor(ResourcesCompat.getColor(getContext().resources, R.color.text, null))
			messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			messageTextView.gravity = Gravity.CENTER

			if (isChannel) {
				if (info != null && info?.linked_chat_id != 0L) {
					val chat = messagesController.getChat(info?.linked_chat_id)

					if (chat != null) {
						messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("DiscussionChannelGroupSetHelp2", R.string.DiscussionChannelGroupSetHelp2, chat.title))
					}
				}
				else {
					messageTextView.text = context.getString(R.string.DiscussionChannelHelp3)
				}
			}
			else {
				val chat = messagesController.getChat(info?.linked_chat_id)

				if (chat != null) {
					messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("DiscussionGroupHelp", R.string.DiscussionGroupHelp, chat.title))
				}
			}

			addView(messageTextView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.LEFT, 52f, 143f, 52f, 18f))
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec)
		}
	}

	private inner class SearchAdapter(private val mContext: Context) : SelectionAdapter() {
		private var searchResult = ArrayList<Chat>()
		private var searchResultNames = ArrayList<CharSequence>()
		private var searchRunnable: Runnable? = null

		fun searchDialogs(query: String?) {
			if (searchRunnable != null) {
				Utilities.searchQueue.cancelRunnable(searchRunnable)
				searchRunnable = null
			}

			if (query.isNullOrEmpty()) {
				searchResult.clear()
				searchResultNames.clear()
				notifyDataSetChanged()
			}
			else {
				Utilities.searchQueue.postRunnable(Runnable {
					processSearch(query)
				}.also {
					searchRunnable = it
				}, 300)
			}
		}

		private fun processSearch(query: String?) {
			AndroidUtilities.runOnUIThread {
				searchRunnable = null

				val chatsCopy = ArrayList(chats)

				Utilities.searchQueue.postRunnable {
					val search1 = query?.trim()?.lowercase(Locale.getDefault())

					if (search1.isNullOrEmpty()) {
						updateSearchResults(ArrayList(), ArrayList())
						return@postRunnable
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

					val resultArray = ArrayList<Chat>()
					val resultArrayNames = ArrayList<CharSequence>()

					for (a in chatsCopy.indices) {
						val chat = chatsCopy[a]
						val name = chat.title.lowercase(Locale.getDefault())
						var tName = LocaleController.getInstance().getTranslitString(name)

						if (name == tName) {
							tName = null
						}

						var found = 0

						for (q in search) {
							if (name.startsWith(q!!) || name.contains(" $q") || tName != null && (tName.startsWith(q) || tName.contains(" $q"))) {
								found = 1
							}
							else if (chat.username != null && chat.username.startsWith(q)) {
								found = 2
							}

							if (found != 0) {
								if (found == 1) {
									resultArrayNames.add(AndroidUtilities.generateSearchName(chat.title, null, q))
								}
								else {
									resultArrayNames.add(AndroidUtilities.generateSearchName("@${chat.username}", null, "@$q"))
								}

								resultArray.add(chat)

								break
							}
						}
					}

					updateSearchResults(resultArray, resultArrayNames)
				}
			}
		}

		private fun updateSearchResults(chats: ArrayList<Chat>, names: ArrayList<CharSequence>) {
			AndroidUtilities.runOnUIThread {
				if (!searching) {
					return@runOnUIThread
				}

				searchResult = chats
				searchResultNames = names

				if (listView?.adapter === searchAdapter) {
					emptyView?.showTextView()
				}

				notifyDataSetChanged()
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType != 1
		}

		override fun getItemCount(): Int {
			return searchResult.size
		}

		fun getItem(i: Int): Chat {
			return searchResult[i]
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View = ManageChatUserCell(mContext, 6, 2, false)
			view.setBackgroundColor(ResourcesCompat.getColor(view.resources, R.color.background, null))
			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val chat = searchResult[position]
			val un = chat.username
			var username: CharSequence? = null
			var name: CharSequence? = searchResultNames[position]

			if (name != null && !un.isNullOrEmpty()) {
				if (name.toString().startsWith("@$un")) {
					username = name
					name = null
				}
			}

			val userCell = holder.itemView as ManageChatUserCell
			userCell.tag = position
			userCell.setData(chat, name, username, false)
		}

		override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
			if (holder.itemView is ManageChatUserCell) {
				holder.itemView.recycle()
			}
		}

		override fun getItemViewType(i: Int): Int {
			return 0
		}
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val type = holder.itemViewType
			return type == 0 || type == 2
		}

		override fun getItemCount(): Int {
			return if (loadingChats && !chatsLoaded) {
				0
			}
			else {
				rowCount
			}
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			var view: View

			when (viewType) {
				0 -> {
					view = ManageChatUserCell(mContext, 6, 2, false)
					view.setBackgroundColor(ResourcesCompat.getColor(view.getResources(), R.color.background, null))
				}

				1 -> {
					view = TextInfoPrivacyCell(mContext)
					view.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow))
				}

				2 -> {
					view = ManageChatTextCell(mContext)
					view.setBackgroundColor(ResourcesCompat.getColor(view.getResources(), R.color.background, null))
				}

				4 -> {
					val chat = if (isChannel) chats[0] else currentChat!!

					joinToSendSettings = object : JoinToSendSettingsView(mContext, chat) {
						private fun migrateIfNeeded(onError: Runnable, onSuccess: Runnable) {
							if (!isChannel(currentChat)) {
								messagesController.convertToMegaGroup(parentActivity, chat.id, this@ChatLinkActivity, {
									if (it != 0L) {
										if (isChannel) {
											messagesController.getChat(it)?.let { chat ->
												chats[0] = chat
											}
										}
										else {
											currentChatId = it
											currentChat = messagesController.getChat(it)
										}

										onSuccess.run()
									}
								}, onError)
							}
							else {
								onSuccess.run()
							}
						}

						override fun onJoinRequestToggle(newValue: Boolean, cancel: Runnable): Boolean {
							if (joinRequestProgress) {
								return false
							}

							joinRequestProgress = true

							migrateIfNeeded(overrideCancel(cancel)) {
								chat.join_request = newValue

								messagesController.toggleChatJoinRequest(chat.id, newValue, { joinRequestProgress = false }) {
									joinRequestProgress = false
									cancel.run()
								}
							}

							return true
						}

						private fun overrideCancel(cancel: Runnable): Runnable {
							return Runnable {
								joinToSendProgress = false
								joinRequestProgress = false
								cancel.run()
							}
						}

						override fun onJoinToSendToggle(newValue: Boolean, cancel: Runnable): Boolean {
							if (joinToSendProgress) {
								return false
							}

							joinToSendProgress = true

							migrateIfNeeded(overrideCancel(cancel)) {
								chat.join_to_send = newValue

								messagesController.toggleChatJoinToSend(chat.id, newValue, {
									joinToSendProgress = false

									if (!newValue && chat.join_request) {
										chat.join_request = false
										joinRequestProgress = true

										messagesController.toggleChatJoinRequest(chat.id, false, { joinRequestProgress = false }) {
											chat.join_request = true
											isJoinRequest = true
											joinRequestCell.isChecked = true
										}
									}
								}) {
									joinToSendProgress = false
									cancel.run()
								}
							}

							return true
						}
					}.also {
						view = it
					}
				}

				3 -> {
					view = HintInnerCell(mContext)
				}

				else -> {
					view = HintInnerCell(mContext)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				0 -> {
					val userCell = holder.itemView as ManageChatUserCell
					userCell.tag = position

					val chat = chats[position - chatStartRow]

					userCell.setData(chat, null, chat.username?.takeIf { it.isNotEmpty() }?.let { "@$it" }, position != chatEndRow - 1 || info?.linked_chat_id != 0L)
				}

				1 -> {
					val privacyCell = holder.itemView as TextInfoPrivacyCell

					if (position == detailRow) {
						if (isChannel) {
							privacyCell.setText(privacyCell.context.getString(R.string.DiscussionChannelHelp2))
						}
						else {
							privacyCell.setText(privacyCell.context.getString(R.string.DiscussionGroupHelp2))
						}
					}
				}

				2 -> {
					val actionCell = holder.itemView as ManageChatTextCell

					if (isChannel) {
						if (info?.linked_chat_id != 0L) {
							actionCell.setColors(ResourcesCompat.getColor(actionCell.resources, R.color.purple, null), ResourcesCompat.getColor(actionCell.resources, R.color.purple, null))
							actionCell.setText(actionCell.context.getString(R.string.DiscussionUnlinkGroup), null, R.drawable.msg_remove, false)
						}
						else {
							actionCell.setColors(ResourcesCompat.getColor(actionCell.resources, R.color.brand, null), ResourcesCompat.getColor(actionCell.resources, R.color.brand, null))
							actionCell.setText(actionCell.context.getString(R.string.DiscussionCreateGroup), null, R.drawable.msg_groups, true)
						}
					}
					else {
						actionCell.setColors(ResourcesCompat.getColor(actionCell.resources, R.color.purple, null), ResourcesCompat.getColor(actionCell.resources, R.color.purple, null))
						actionCell.setText(actionCell.context.getString(R.string.DiscussionUnlinkChannel), null, R.drawable.msg_remove, false)
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
				helpRow -> 3
				createChatRow, removeChatRow -> 2
				in chatStartRow until chatEndRow -> 0
				joinToSendRow -> 4
				else -> 1
			}
		}
	}

	companion object {
		private const val search_button = 0
	}
}
