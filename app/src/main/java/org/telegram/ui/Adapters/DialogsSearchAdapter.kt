/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Adapters

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.collection.LongSparseArray
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.PhoneFormat.PhoneFormat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.UserObject.getFirstName
import org.telegram.messenger.UserObject.getUserName
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.tgnet.tlrpc.messages_Messages
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.FiltersView.DateData
import org.telegram.ui.Adapters.SearchAdapterHelper.HashtagObject
import org.telegram.ui.Adapters.SearchAdapterHelper.SearchAdapterHelperDelegate
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Cells.GraySectionCell
import org.telegram.ui.Cells.HashtagSearchCell
import org.telegram.ui.Cells.HintDialogCell
import org.telegram.ui.Cells.ProfileSearchCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.FilteredSearchView
import java.util.Locale
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
open class DialogsSearchAdapter(private val context: Context, messagesSearch: Int, type: Int, private val itemAnimator: DefaultItemAnimator?) : SelectionAdapter() {
	private var searchRunnable: Runnable? = null
	private var searchRunnable2: Runnable? = null
	private var searchResult = ArrayList<Any>()
	private var searchResultNames = ArrayList<CharSequence>()
	private val searchResultMessages = ArrayList<MessageObject>()
	private val searchResultHashtags = ArrayList<String>()
	private var lastSearchText: String? = null
	var ignoredIds: LongArray? = null

	var isSearchWas = false
		private set

	private var reqId = 0
	private var lastReqId = 0
	private var delegate: DialogsSearchAdapterDelegate? = null
	private val needMessagesSearch: Int

	var isMessagesSearchEndReached = false
		private set

	var lastSearchString: String? = null
		private set

	private var nextSearchRate = 0
	private var lastSearchId = 0
	private var lastGlobalSearchId = 0
	private var lastLocalSearchId = 0
	private var lastMessagesSearchId = 0
	private val dialogsType: Int
	private val searchAdapterHelper = SearchAdapterHelper(false)

	var innerListView: RecyclerListView? = null
		private set

	private val selfUserId: Long

	@JvmField
	var showMoreAnimation = false

	private var lastShowMoreUpdate: Long = 0

	@JvmField
	var showMoreHeader: View? = null

	private var cancelShowMoreAnimation: Runnable? = null
	private val currentAccount = UserConfig.selectedAccount
	private var recentSearchObjects = ArrayList<RecentSearchObject>()
	private val filteredRecentSearchObjects = ArrayList<RecentSearchObject>()
	private var filteredRecentQuery: String? = null
	private var recentSearchObjectsById = LongSparseArray<RecentSearchObject>()
	private val localTipDates = ArrayList<DateData>()
	private var localTipArchive = false
	private var filtersDelegate: FilteredSearchView.Delegate? = null

	var currentItemCount = 0
		private set

	private var folderId = 0

	val isSearching: Boolean
		get() = waitingResponseCount > 0

	class DialogSearchResult {
		@JvmField
		var `object`: TLObject? = null

		@JvmField
		var date = 0

		@JvmField
		var name: CharSequence? = null
	}

	class RecentSearchObject {
		@JvmField
		var `object`: TLObject? = null
		var date = 0
		var did: Long = 0
	}

	interface DialogsSearchAdapterDelegate {
		fun searchStateChanged(searching: Boolean, animated: Boolean)
		fun didPressedOnSubDialog(did: Long)
		fun needRemoveHint(did: Long)
		fun needClearList()
		fun runResultsEnterAnimation()
		fun isSelected(dialogId: Long): Boolean
	}

	open class CategoryAdapterRecycler(private val context: Context, private val currentAccount: Int, private val drawChecked: Boolean) : SelectionAdapter() {
		fun setIndex(value: Int) {
			notifyDataSetChanged()
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val cell = HintDialogCell(context, drawChecked)
			cell.layoutParams = RecyclerView.LayoutParams(AndroidUtilities.dp(80f), AndroidUtilities.dp(86f))
			return RecyclerListView.Holder(cell)
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return true
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val cell = holder.itemView as HintDialogCell
			val peer = MediaDataController.getInstance(currentAccount).hints[position]
			// val dialog = TLRPC.TL_dialog()
			var chat: TLRPC.Chat? = null
			var user: User? = null
			var did: Long = 0
			if (peer.peer.user_id != 0L) {
				did = peer.peer.user_id
				user = MessagesController.getInstance(currentAccount).getUser(peer.peer.user_id)
			}
			else if (peer.peer.channel_id != 0L) {
				did = -peer.peer.channel_id
				chat = MessagesController.getInstance(currentAccount).getChat(peer.peer.channel_id)
			}
			else if (peer.peer.chat_id != 0L) {
				did = -peer.peer.chat_id
				chat = MessagesController.getInstance(currentAccount).getChat(peer.peer.chat_id)
			}
			cell.tag = did
			var name: String? = ""
			if (user != null) {
				name = getFirstName(user)
			}
			else if (chat != null) {
				name = chat.title
			}
			cell.setDialog(did, true, name)
		}

		override fun getItemCount(): Int {
			return MediaDataController.getInstance(currentAccount).hints.size
		}
	}

	fun setDelegate(delegate: DialogsSearchAdapterDelegate?) {
		this.delegate = delegate
	}

	fun loadMoreSearchMessages() {
		if (reqId != 0) {
			return
		}
		searchMessagesInternal(lastSearchString, lastMessagesSearchId)
	}

	private fun searchMessagesInternal(query: String?, searchId: Int) {
		if (needMessagesSearch == 0 || lastSearchString.isNullOrEmpty() && query.isNullOrEmpty()) {
			return
		}

		if (reqId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true)
			reqId = 0
		}

		if (query.isNullOrEmpty()) {
			filteredRecentQuery = null
			searchResultMessages.clear()
			lastReqId = 0
			lastSearchString = null
			isSearchWas = false
			notifyDataSetChanged()
			return
		}
		else {
			filterRecent(query)
			searchAdapterHelper.mergeResults(searchResult, filteredRecentSearchObjects)
		}

		val req = TLRPC.TL_messages_searchGlobal()
		req.limit = 20
		req.q = query
		req.filter = TLRPC.TL_inputMessagesFilterEmpty()
		req.flags = req.flags or 1
		req.folder_id = folderId

		if (query == lastSearchString && searchResultMessages.isNotEmpty()) {
			val lastMessage = searchResultMessages[searchResultMessages.size - 1]
			req.offset_id = lastMessage.id
			req.offset_rate = nextSearchRate

			val id = MessageObject.getPeerId(lastMessage.messageOwner?.peer_id)

			req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(id)
		}
		else {
			req.offset_rate = 0
			req.offset_id = 0
			req.offset_peer = TLRPC.TL_inputPeerEmpty()
		}

		lastSearchString = query

		val currentReqId = ++lastReqId

		reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
			val messageObjects = mutableListOf<MessageObject>()

			if (error == null) {
				val res = response as messages_Messages
				val chatsMap = LongSparseArray<TLRPC.Chat>()
				val usersMap = LongSparseArray<User>()

				for (chat in res.chats) {
					chatsMap.put(chat.id, chat)
				}

				for (user in res.users) {
					usersMap.put(user.id, user)
				}

				for (message in res.messages) {
					val messageObject = MessageObject(currentAccount, message, usersMap, chatsMap, generateLayout = false, checkMediaExists = true)
					messageObjects.add(messageObject)
					messageObject.setQuery(query)
				}
			}

			AndroidUtilities.runOnUIThread {
				if (currentReqId == lastReqId && (searchId <= 0 || searchId == lastSearchId)) {
					waitingResponseCount--

					if (error == null) {
						val res = response as messages_Messages

						MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true)
						MessagesController.getInstance(currentAccount).putUsers(res.users, false)
						MessagesController.getInstance(currentAccount).putChats(res.chats, false)

						if (req.offset_id == 0) {
							searchResultMessages.clear()
						}

						nextSearchRate = res.next_rate

						for (a in res.messages.indices) {
							val message = res.messages[a]
							val did = MessageObject.getDialogId(message)
							val maxId = MessagesController.getInstance(currentAccount).deletedHistory[did]

							if (maxId != 0 && message.id <= maxId) {
								continue
							}

							searchResultMessages.add(messageObjects[a])

							val dialogId = MessageObject.getDialogId(message)
							val readMax = if (message.out) MessagesController.getInstance(currentAccount).dialogs_read_outbox_max else MessagesController.getInstance(currentAccount).dialogs_read_inbox_max
							var value = readMax[dialogId]

							if (value == null) {
								value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(message.out, dialogId)
								readMax[dialogId] = value
							}

							message.unread = value < message.id
						}

						isSearchWas = true
						isMessagesSearchEndReached = res.messages.size != 20

						if (searchId > 0) {
							lastMessagesSearchId = searchId

							if (lastLocalSearchId != searchId) {
								searchResult.clear()
							}

							if (lastGlobalSearchId != searchId) {
								searchAdapterHelper.clear()
							}
						}

						searchAdapterHelper.mergeResults(searchResult, filteredRecentSearchObjects)

						delegate?.searchStateChanged(waitingResponseCount > 0, true)
						delegate?.runResultsEnterAnimation()

						globalSearchCollapsed = true

						notifyDataSetChanged()
					}
				}
				reqId = 0
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	fun hasRecentSearch(): Boolean {
		return dialogsType != 2 && dialogsType != 4 && dialogsType != 5 && dialogsType != 6 && dialogsType != 11 && recentItemsCount > 0
	}

	val isRecentSearchDisplayed: Boolean
		get() = needMessagesSearch != 2 && hasRecentSearch()

	fun putRecentSearch(did: Long, `object`: TLObject?) {
		var recentSearchObject = recentSearchObjectsById[did]

		if (recentSearchObject == null) {
			recentSearchObject = RecentSearchObject()
			recentSearchObjectsById.put(did, recentSearchObject)
		}
		else {
			recentSearchObjects.remove(recentSearchObject)
		}

		recentSearchObjects.add(0, recentSearchObject)

		recentSearchObject.did = did
		recentSearchObject.`object` = `object`
		recentSearchObject.date = (System.currentTimeMillis() / 1000).toInt()

		notifyDataSetChanged()

		MessagesStorage.getInstance(currentAccount).storageQueue.postRunnable {
			try {
				val state = MessagesStorage.getInstance(currentAccount).database.executeFast("REPLACE INTO search_recent VALUES(?, ?)")
				state.requery()
				state.bindLong(1, did)
				state.bindInteger(2, (System.currentTimeMillis() / 1000).toInt())
				state.step()
				state.dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun clearRecentSearch() {
		var queryFilter: StringBuilder? = null

		if (isSearchWas) {
			while (filteredRecentSearchObjects.size > 0) {
				val obj = filteredRecentSearchObjects.removeAt(0)
				recentSearchObjects.remove(obj)
				recentSearchObjectsById.remove(obj.did)

				if (queryFilter == null) {
					queryFilter = StringBuilder("did IN (")
					queryFilter.append(obj.did)
				}
				else {
					queryFilter.append(", ").append(obj.did)
				}
			}

			if (queryFilter == null) {
				queryFilter = StringBuilder("1")
			}
			else {
				queryFilter.append(")")
			}
		}
		else {
			filteredRecentSearchObjects.clear()
			recentSearchObjects.clear()
			recentSearchObjectsById.clear()

			queryFilter = StringBuilder("1")
		}

		val finalQueryFilter: StringBuilder = queryFilter

		notifyDataSetChanged()

		MessagesStorage.getInstance(currentAccount).storageQueue.postRunnable {
			try {
				finalQueryFilter.insert(0, "DELETE FROM search_recent WHERE ")
				MessagesStorage.getInstance(currentAccount).database.executeFast(finalQueryFilter.toString()).stepThis().dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun removeRecentSearch(did: Long) {
		val `object` = recentSearchObjectsById[did] ?: return
		recentSearchObjectsById.remove(did)
		recentSearchObjects.remove(`object`)

		notifyDataSetChanged()

		MessagesStorage.getInstance(currentAccount).storageQueue.postRunnable {
			try {
				MessagesStorage.getInstance(currentAccount).database.executeFast("DELETE FROM search_recent WHERE did = $did").stepThis().dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun addHashtagsFromMessage(message: CharSequence?) {
		searchAdapterHelper.addHashtagsFromMessage(message)
	}

	private fun setRecentSearch(arrayList: ArrayList<RecentSearchObject>, hashMap: LongSparseArray<RecentSearchObject>) {
		recentSearchObjects = arrayList
		recentSearchObjectsById = hashMap

		recentSearchObjects.forEach {
			when (val recentSearchObject = it.`object`) {
				is User -> {
					MessagesController.getInstance(currentAccount).putUser(recentSearchObject, true)
				}

				is TLRPC.Chat -> {
					MessagesController.getInstance(currentAccount).putChat(recentSearchObject, true)
				}

				is TLRPC.EncryptedChat -> {
					MessagesController.getInstance(currentAccount).putEncryptedChat(recentSearchObject, true)
				}
			}
		}

		notifyDataSetChanged()
	}

	private fun searchDialogsInternal(query: String?, searchId: Int) {
		if (needMessagesSearch == 2) {
			return
		}

		val q = query?.trim { it <= ' ' }?.lowercase(Locale.getDefault())

		if (q.isNullOrEmpty()) {
			lastSearchId = 0
			updateSearchResults(ArrayList(), ArrayList(), ArrayList(), lastSearchId)
			return
		}

		MessagesStorage.getInstance(currentAccount).storageQueue.postRunnable {
			val resultArray = ArrayList<Any>()
			val resultArrayNames = ArrayList<CharSequence>()
			val encUsers = ArrayList<User>()

			MessagesStorage.getInstance(currentAccount).localSearch(dialogsType, q, resultArray, resultArrayNames, encUsers, -1)

			updateSearchResults(resultArray, resultArrayNames, encUsers, searchId)

			FiltersView.fillTipDates(q, localTipDates)

			localTipArchive = q.length >= 3 && (context.getString(R.string.ArchiveSearchFilter).lowercase(Locale.getDefault()).startsWith(q) || "archive".startsWith(query))

			AndroidUtilities.runOnUIThread {
				filtersDelegate?.updateFiltersView(false, null, localTipDates, localTipArchive)
			}
		}
	}

	private fun updateSearchResults(result: ArrayList<Any>, names: ArrayList<CharSequence>, encUsers: ArrayList<User>, searchId: Int) {
		AndroidUtilities.runOnUIThread {
			waitingResponseCount--

			if (searchId != lastSearchId) {
				return@runOnUIThread
			}

			lastLocalSearchId = searchId

			if (lastGlobalSearchId != searchId) {
				searchAdapterHelper.clear()
			}

			if (lastMessagesSearchId != searchId) {
				searchResultMessages.clear()
			}

			isSearchWas = true

			// val recentCount = filteredRecentSearchObjects.size
			var a = 0

			while (a < result.size) {
				val obj = result[a]
				var dialogId: Long = 0

				when (obj) {
					is User -> {
						MessagesController.getInstance(currentAccount).putUser(obj, true)
						dialogId = obj.id
					}

					is TLRPC.Chat -> {
						MessagesController.getInstance(currentAccount).putChat(obj, true)
						dialogId = -obj.id
					}

					is TLRPC.EncryptedChat -> {
						MessagesController.getInstance(currentAccount).putEncryptedChat(obj, true)
					}
				}

				if (dialogId != 0L) {
					val dialog = MessagesController.getInstance(currentAccount).dialogs_dict[dialogId]

					if (dialog == null) {
						val finalDialogId = dialogId

						MessagesStorage.getInstance(currentAccount).getDialogFolderId(dialogId) { param ->
							if (param != -1) {
								val newDialog: TLRPC.Dialog = TLRPC.TL_dialog()
								newDialog.id = finalDialogId

								if (param != 0) {
									newDialog.folder_id = param
								}

								if (obj is TLRPC.Chat) {
									newDialog.flags = if (ChatObject.isChannel(obj)) 1 else 0
								}

								MessagesController.getInstance(currentAccount).dialogs_dict.put(finalDialogId, newDialog)
								MessagesController.getInstance(currentAccount).allDialogs.add(newDialog)
								MessagesController.getInstance(currentAccount).sortDialogs(null)
							}
						}
					}
				}

				var foundInRecent = false

				for (o in filteredRecentSearchObjects) {
					if (o.did == dialogId) {
						foundInRecent = true
						break
					}
				}

				if (foundInRecent) {
					result.removeAt(a)
					names.removeAt(a)
					a--
				}

				a++
			}

			MessagesController.getInstance(currentAccount).putUsers(encUsers, true)

			searchResult = result
			searchResultNames = names

			searchAdapterHelper.mergeResults(searchResult, filteredRecentSearchObjects)

			notifyDataSetChanged()

			delegate?.searchStateChanged(waitingResponseCount > 0, true)
			delegate?.runResultsEnterAnimation()
		}
	}

	val isHashtagSearch: Boolean
		get() = searchResultHashtags.isNotEmpty()

	fun clearRecentHashtags() {
		searchAdapterHelper.clearRecentHashtags()
		searchResultHashtags.clear()
		notifyDataSetChanged()
	}

	var waitingResponseCount = 0

	fun searchDialogs(text: String?, folderId: Int) {
		if (text != null && text == lastSearchText && (folderId == this.folderId || TextUtils.isEmpty(text))) {
			return
		}

		lastSearchText = text

		this.folderId = folderId

		if (searchRunnable != null) {
			Utilities.searchQueue.cancelRunnable(searchRunnable)
			searchRunnable = null
		}

		if (searchRunnable2 != null) {
			AndroidUtilities.cancelRunOnUIThread(searchRunnable2)
			searchRunnable2 = null
		}

		val query = text?.trim { it <= ' ' }

		if (query.isNullOrEmpty()) {
			filteredRecentQuery = null
			searchAdapterHelper.unloadRecentHashtags()
			searchResult.clear()
			searchResultNames.clear()
			searchResultHashtags.clear()
			searchAdapterHelper.mergeResults(null, null)
			searchAdapterHelper.queryServerSearch(null, allowUsername = true, allowChats = true, allowBots = dialogsType != 11, allowSelf = dialogsType != 11, canAddGroupsOnly = dialogsType == 2 || dialogsType == 11, channelId = 0, type = 0, searchId = 0)

			isSearchWas = false
			lastSearchId = 0
			waitingResponseCount = 0
			globalSearchCollapsed = true

			delegate?.searchStateChanged(searching = false, animated = true)

			searchMessagesInternal(null, 0)
			notifyDataSetChanged()
			localTipDates.clear()

			localTipArchive = false

			filtersDelegate?.updateFiltersView(false, null, localTipDates, false)
		}
		else {
			filterRecent(query)

			searchAdapterHelper.mergeResults(searchResult, filteredRecentSearchObjects)

			if (needMessagesSearch != 2 && query.startsWith("#") && query.length == 1) {
				isMessagesSearchEndReached = true

				if (searchAdapterHelper.loadRecentHashtags()) {
					searchResultMessages.clear()
					searchResultHashtags.clear()

					searchAdapterHelper.hashtags?.forEach { hashtagObject ->
						hashtagObject.hashtag?.let {
							searchResultHashtags.add(it)
						}
					}

					globalSearchCollapsed = true
					waitingResponseCount = 0

					notifyDataSetChanged()

					delegate?.searchStateChanged(searching = false, animated = false)
				}
			}
			else {
				searchResultHashtags.clear()
			}

			val searchId = ++lastSearchId

			waitingResponseCount = 3
			globalSearchCollapsed = true

			notifyDataSetChanged()

			delegate?.searchStateChanged(searching = true, animated = false)

			Utilities.searchQueue.postRunnable(Runnable {
				searchRunnable = null

				searchDialogsInternal(query, searchId)

				AndroidUtilities.runOnUIThread(Runnable ui@{
					searchRunnable2 = null

					if (searchId != lastSearchId) {
						return@ui
					}

					if (needMessagesSearch != 2) {
						searchAdapterHelper.queryServerSearch(query, true, dialogsType != 4, true, dialogsType != 4 && dialogsType != 11, dialogsType == 2 || dialogsType == 1, 0, 0, searchId)
					}
					else {
						waitingResponseCount -= 2
					}

					if (needMessagesSearch == 0) {
						waitingResponseCount--
					}
					else {
						searchMessagesInternal(text, searchId)
					}
				}.also {
					searchRunnable2 = it
				})
			}.also {
				searchRunnable = it
			}, 300)
		}
	}

	private val recentItemsCount: Int
		get() {
			val recent = if (isSearchWas) filteredRecentSearchObjects else recentSearchObjects
			return (if (recent.isNotEmpty()) recent.size + 1 else 0) + if (!isSearchWas && MediaDataController.getInstance(currentAccount).hints.isNotEmpty()) 1 else 0
		}

	val recentResultsCount: Int
		get() {
			val recent = if (isSearchWas) filteredRecentSearchObjects else recentSearchObjects
			return recent.size
		}

	override fun getItemCount(): Int {
		if (waitingResponseCount == 3) {
			return 0
		}

		var count = 0

		if (searchResultHashtags.isNotEmpty()) {
			count += searchResultHashtags.size + 1
			return count
		}

		if (isRecentSearchDisplayed) {
			count += recentItemsCount

			if (!isSearchWas) {
				return count
			}
		}

		val resultsCount = searchResult.size
		count += resultsCount

		val localServerCount = searchAdapterHelper.localServerSearch.size
		count += localServerCount

		var globalCount = searchAdapterHelper.globalSearch.size

		if (globalCount > 3 && globalSearchCollapsed) {
			globalCount = 3
		}

		val messagesCount = searchResultMessages.size

		if (resultsCount + localServerCount > 0 && recentItemsCount > 0) {
			count++
		}

		if (globalCount != 0) {
			count += globalCount + 1
		}

		if (messagesCount != 0) {
			count += messagesCount + 1 + if (isMessagesSearchEndReached) 0 else 1
		}

		return count.also { currentItemCount = it }
	}

	fun getItem(i: Int): Any? {
		@Suppress("NAME_SHADOWING") var i = i
		if (searchResultHashtags.isNotEmpty()) {
			return if (i > 0) {
				searchResultHashtags[i - 1]
			}
			else {
				null
			}
		}

		if (isRecentSearchDisplayed) {
			val offset = if (!isSearchWas && MediaDataController.getInstance(currentAccount).hints.isNotEmpty()) 1 else 0
			val recent = if (isSearchWas) filteredRecentSearchObjects else recentSearchObjects

			if (i > offset && i - 1 - offset < recent.size) {
				var `object` = recent[i - 1 - offset].`object`

				if (`object` is User) {
					val user = MessagesController.getInstance(currentAccount).getUser(`object`.id)
					if (user != null) {
						`object` = user
					}
				}
				else if (`object` is TLRPC.Chat) {
					val chat = MessagesController.getInstance(currentAccount).getChat(`object`.id)

					if (chat != null) {
						`object` = chat
					}
				}

				return `object`
			}
			else {
				i -= recentItemsCount
			}
		}

		val globalSearch = searchAdapterHelper.globalSearch
		val localServerSearch = searchAdapterHelper.localServerSearch
		val localCount = searchResult.size
		val localServerCount = localServerSearch.size

		if (localCount + localServerCount > 0 && recentItemsCount > 0) {
			if (i == 0) {
				return null
			}

			i--
		}

		var globalCount = if (globalSearch.isEmpty()) 0 else globalSearch.size + 1

		if (globalCount > 4 && globalSearchCollapsed) {
			globalCount = 4
		}

		val messagesCount = if (searchResultMessages.isEmpty()) 0 else searchResultMessages.size + 1

		if (i in 0 until localCount) {
			return searchResult[i]
		}

		i -= localCount

		if (i in 0 until localServerCount) {
			return localServerSearch[i]
		}

		i -= localServerCount

		if (i in 1 until globalCount) {
			return globalSearch[i - 1]
		}

		i -= globalCount

		if (i in 1 until messagesCount) {
			return searchResultMessages[i - 1]
		}

		return null
	}

	fun isGlobalSearch(i: Int): Boolean {
		@Suppress("NAME_SHADOWING") var i = i

		if (!isSearchWas) {
			return false
		}

		if (searchResultHashtags.isNotEmpty()) {
			return false
		}

		if (isRecentSearchDisplayed) {
			val offset = if (!isSearchWas && MediaDataController.getInstance(currentAccount).hints.isNotEmpty()) 1 else 0
			val recent = if (isSearchWas) filteredRecentSearchObjects else recentSearchObjects

			i -= if (i > offset && i - 1 - offset < recent.size) {
				return false
			}
			else {
				recentItemsCount
			}
		}

		val globalSearch = searchAdapterHelper.globalSearch
		val localServerSearch = searchAdapterHelper.localServerSearch
		val localCount = searchResult.size
		val localServerCount = localServerSearch.size

		var globalCount = if (globalSearch.isEmpty()) 0 else globalSearch.size + 1

		if (globalCount > 4 && globalSearchCollapsed) {
			globalCount = 4
		}

		val messagesCount = if (searchResultMessages.isEmpty()) 0 else searchResultMessages.size + 1

		if (i in 0 until localCount) {
			return false
		}

		i -= localCount

		if (i in 0 until localServerCount) {
			return false
		}

		i -= localServerCount

		if (i in 1 until globalCount) {
			return true
		}

		i -= globalCount

		if (i in 1 until messagesCount) {
			return false
		}

		return false
	}

	override fun getItemId(i: Int): Long {
		return i.toLong()
	}

	override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
		val type = holder.itemViewType
		return type != 1 && type != 3
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val view: View

		when (viewType) {
			VIEW_TYPE_PROFILE_CELL -> {
				view = ProfileSearchCell(context, leftPadding = 5)
			}

			VIEW_TYPE_GRAY_SECTION -> {
				view = GraySectionCell(context)
			}

			VIEW_TYPE_DIALOG_CELL -> {
				view = DialogCell(null, context, needCheck = false, forceThreeLines = false)
			}

			VIEW_TYPE_LOADING -> {
				val flickerLoadingView = FlickerLoadingView(context)
				flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_TYPE)
				flickerLoadingView.setIsSingleCell(true)
				view = flickerLoadingView
			}

			VIEW_TYPE_HASHTAG_CELL -> {
				view = HashtagSearchCell(context)
			}

			VIEW_TYPE_CATEGORY_LIST -> {
				val horizontalListView = object : RecyclerListView(context) {
					override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
						getParent()?.parent?.requestDisallowInterceptTouchEvent(canScrollHorizontally(-1) || canScrollHorizontally(1))
						return super.onInterceptTouchEvent(e)
					}
				}

				horizontalListView.setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector))
				horizontalListView.tag = 9
				horizontalListView.itemAnimator = null
				horizontalListView.layoutAnimation = null

				val layoutManager = object : LinearLayoutManager(context) {
					override fun supportsPredictiveItemAnimations(): Boolean {
						return false
					}
				}

				layoutManager.orientation = LinearLayoutManager.HORIZONTAL
				horizontalListView.layoutManager = layoutManager
				//horizontalListView.setDisallowInterceptTouchEvents(true);
				horizontalListView.adapter = CategoryAdapterRecycler(context, currentAccount, false)

				horizontalListView.setOnItemClickListener { view1, _ ->
					delegate?.didPressedOnSubDialog(view1.tag as Long)
				}

				horizontalListView.setOnItemLongClickListener { view12, _ ->
					delegate?.needRemoveHint(view12.tag as Long)
					true
				}

				view = horizontalListView

				innerListView = horizontalListView
			}

			VIEW_TYPE_ADD_BY_PHONE -> {
				view = TextCell(context, leftPadding = 16)
			}

			else -> {
				view = TextCell(context, leftPadding = 16)
			}
		}

		if (viewType == VIEW_TYPE_CATEGORY_LIST) {
			view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(86f))
		}
		else {
			view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
		}

		return RecyclerListView.Holder(view)
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		var realPosition = position

		when (holder.itemViewType) {
			VIEW_TYPE_PROFILE_CELL -> {
				val cell = holder.itemView as ProfileSearchCell

				cell.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

				val oldDialogId = cell.dialogId
				var user: User? = null
				var chat: TLRPC.Chat? = null
				var encryptedChat: TLRPC.EncryptedChat? = null
				var username: CharSequence? = null
				var name: CharSequence? = null
				var isRecent = false
				var un: String? = null

				when (val obj = getItem(realPosition)) {
					is User -> {
						user = obj
						un = user.username
					}

					is TLRPC.Chat -> {
						chat = MessagesController.getInstance(currentAccount).getChat(obj.id) ?: obj
						un = chat.username
					}

					is TLRPC.EncryptedChat -> {
						encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(obj.id)
						user = MessagesController.getInstance(currentAccount).getUser(encryptedChat?.user_id)
					}
				}

				if (isRecentSearchDisplayed) {
					if (realPosition < recentItemsCount) {
						cell.useSeparator = realPosition != recentItemsCount - 1
						isRecent = true
					}

					realPosition -= recentItemsCount
				}

				val globalSearch = searchAdapterHelper.globalSearch
				val localCount = searchResult.size
				val localServerCount = searchAdapterHelper.localServerSearch.size

				if (localCount + localServerCount > 0 && recentItemsCount > 0) {
					realPosition--
				}

				var globalCount = if (globalSearch.isEmpty()) 0 else globalSearch.size + 1

				if (globalCount > 4 && globalSearchCollapsed) {
					globalCount = 4
				}

				if (!isRecent) {
					cell.useSeparator = (position != itemCount - recentItemsCount - 1 && position != localCount + localServerCount - 1 && position != localCount + globalCount + localServerCount - 1)
				}

				if (realPosition >= 0 && realPosition < searchResult.size && user == null) {
					name = searchResultNames[realPosition]

					// TODO: check if this is not required
//					if (name != null && user != null && user.username != null && user.username.length > 0) {
//						if (name.toString().startsWith("@" + user.username)) {
//							username = name
//							name = null
//						}
//					}
				}

				if (username == null) {
					var foundUserName = if (isRecent) filteredRecentQuery else searchAdapterHelper.lastFoundUsername

					if (!foundUserName.isNullOrEmpty()) {
						var nameSearch: String? = null
						var index = 0

						if (user != null) {
							nameSearch = ContactsController.formatName(user.first_name, user.last_name)
						}
						else if (chat != null) {
							nameSearch = chat.title
						}

						if (nameSearch != null && AndroidUtilities.indexOfIgnoreCase(nameSearch, foundUserName).also { index = it } != -1) {
							val spannableStringBuilder = SpannableStringBuilder(nameSearch)
							spannableStringBuilder.setSpan(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, R.color.brand, null)), index, index + foundUserName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
							name = spannableStringBuilder
						}

						if (un != null && user == null) {
							if (foundUserName.startsWith("@")) {
								foundUserName = foundUserName.substring(1)
							}
							try {
								val spannableStringBuilder = SpannableStringBuilder()
								spannableStringBuilder.append("@")
								spannableStringBuilder.append(un)

								val hasMatch = AndroidUtilities.indexOfIgnoreCase(un, foundUserName).also { index = it } != -1

								if (hasMatch) {
									var len = foundUserName.length

									if (index == 0) {
										len++
									}
									else {
										index++
									}

									spannableStringBuilder.setSpan(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, R.color.brand, null)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
								}

								username = spannableStringBuilder
							}
							catch (e: Exception) {
								username = un
								FileLog.e(e)
							}
						}
					}
				}

				cell.setChecked(checked = false, animated = false)

				var savedMessages = false

				if (user != null && user.id == selfUserId) {
					name = context.getString(R.string.SavedMessages)
					username = null
					savedMessages = true
				}

				if (chat != null && chat.participants_count != 0) {
					val membersString = if (ChatObject.isChannel(chat) && !chat.megagroup) {
						LocaleController.formatPluralString("Subscribers", chat.participants_count)
					}
					else {
						LocaleController.formatPluralString("Members", chat.participants_count)
					}

					if (username is SpannableStringBuilder) {
						username.append(", ").append(membersString)
					}
					else if (!TextUtils.isEmpty(username)) {
						username = TextUtils.concat(username, ", ", membersString)
					}
					else {
						username = membersString
					}
				}

				cell.setData(user ?: chat, encryptedChat, name, username, true, savedMessages)
				cell.setChecked(delegate?.isSelected(cell.dialogId) ?: false, oldDialogId == cell.dialogId)
			}

			VIEW_TYPE_GRAY_SECTION -> {
				val cell = holder.itemView as GraySectionCell

				if (searchResultHashtags.isNotEmpty()) {
					cell.setText(context.getString(R.string.Hashtags), context.getString(R.string.ClearButton)) {
						delegate?.needClearList()
					}
				}
				else {
					val rawPosition = realPosition

					if (isRecentSearchDisplayed) {
						val offset = if (!isSearchWas && MediaDataController.getInstance(currentAccount).hints.isNotEmpty()) 1 else 0

						realPosition -= if (realPosition < offset) {
							cell.setText(context.getString(R.string.ChatHints))
							return
						}
						else if (realPosition == offset) {
							if (!isSearchWas) {
								cell.setText(context.getString(R.string.Recent), context.getString(R.string.ClearButton)) {
									delegate?.needClearList()
								}
							}
							else {
								cell.setText(context.getString(R.string.Recent), context.getString(R.string.Clear)) {
									delegate?.needClearList()
								}
							}

							return
						}
						else if (realPosition == recentItemsCount) {
							cell.setText(context.getString(R.string.SearchAllChatsShort))
							return
						}
						else {
							recentItemsCount
						}
					}

					val globalSearch = searchAdapterHelper.globalSearch
					val localCount = searchResult.size
					val localServerCount = searchAdapterHelper.localServerSearch.size

					var globalCount = if (globalSearch.isEmpty()) 0 else globalSearch.size + 1

					if (globalCount > 4 && globalSearchCollapsed) {
						globalCount = 4
					}

					// val messagesCount = if (searchResultMessages.isEmpty()) 0 else searchResultMessages.size + 1

					realPosition -= localCount + localServerCount

					val title: String
					var showMore = false
					var onClick: Runnable? = null

					if (realPosition in 0 until globalCount) {
						title = context.getString(R.string.GlobalSearch)

						if (searchAdapterHelper.globalSearch.size > 3) {
							showMore = globalSearchCollapsed

							onClick = Runnable onClick@{
								val now = SystemClock.elapsedRealtime()
								if (now - lastShowMoreUpdate < 300) {
									return@onClick
								}

								lastShowMoreUpdate = now

								val totalGlobalCount = if (globalSearch.isEmpty()) 0 else globalSearch.size
								val disableRemoveAnimation = itemCount > rawPosition + min(totalGlobalCount, if (globalSearchCollapsed) 4 else Int.MAX_VALUE) + 1

								if (itemAnimator != null) {
									itemAnimator.addDuration = if (disableRemoveAnimation) 45 else 200.toLong()
									itemAnimator.removeDuration = if (disableRemoveAnimation) 80 else 200.toLong()
									itemAnimator.removeDelay = if (disableRemoveAnimation) 270 else 0.toLong()
								}

								globalSearchCollapsed = !globalSearchCollapsed

								cell.setRightText(if (globalSearchCollapsed) context.getString(R.string.ShowMore) else context.getString(R.string.ShowLess), globalSearchCollapsed)

								showMoreHeader = null

								val parent = cell.parent as? View

								if (parent is RecyclerView) {
									val nextGraySectionPosition = if (!globalSearchCollapsed) rawPosition + 4 else rawPosition + totalGlobalCount + 1

									var i = 0

									while (i < parent.childCount) {
										val child = parent.getChildAt(i)

										if (parent.getChildAdapterPosition(child) == nextGraySectionPosition) {
											showMoreHeader = child
											break
										}

										++i
									}
								}

								if (!globalSearchCollapsed) {
									notifyItemChanged(rawPosition + 3)
									notifyItemRangeInserted(rawPosition + 4, totalGlobalCount - 3)
								}
								else {
									notifyItemRangeRemoved(rawPosition + 4, totalGlobalCount - 3)

									if (disableRemoveAnimation) {
										AndroidUtilities.runOnUIThread({
											notifyItemChanged(rawPosition + 3)
										}, 350)
									}
									else {
										notifyItemChanged(rawPosition + 3)
									}
								}

								if (cancelShowMoreAnimation != null) {
									AndroidUtilities.cancelRunOnUIThread(cancelShowMoreAnimation)
								}

								if (disableRemoveAnimation) {
									showMoreAnimation = true

									AndroidUtilities.runOnUIThread(Runnable {
										showMoreAnimation = false
										showMoreHeader = null
										parent?.invalidate()
									}.also {
										cancelShowMoreAnimation = it
									}, 400)
								}
								else {
									showMoreAnimation = false
								}
							}
						}
					}
					else {
						title = context.getString(R.string.SearchMessages)
					}

					if (onClick == null) {
						cell.setText(title)
					}
					else {
						cell.setText(title, if (showMore) context.getString(R.string.ShowMore) else context.getString(R.string.ShowLess)) {
							onClick.run()
						}
					}
				}
			}

			VIEW_TYPE_DIALOG_CELL -> {
				val cell = holder.itemView as DialogCell
				cell.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
				cell.useSeparator = realPosition != itemCount - 1
				val messageObject = getItem(realPosition) as MessageObject
				cell.setDialog(messageObject.dialogId, messageObject, messageObject.messageOwner!!.date, false)
			}

			VIEW_TYPE_HASHTAG_CELL -> {
				val cell = holder.itemView as HashtagSearchCell
				cell.setBackgroundColor(context.getColor(R.color.background))
				cell.text = searchResultHashtags[realPosition - 1]
				cell.setNeedDivider(realPosition != searchResultHashtags.size)
			}

			VIEW_TYPE_CATEGORY_LIST -> {
				val recyclerListView = holder.itemView as RecyclerListView
				(recyclerListView.adapter as CategoryAdapterRecycler).setIndex(realPosition / 2)
			}

			VIEW_TYPE_ADD_BY_PHONE -> {
				val str = getItem(realPosition) as String?
				val cell = holder.itemView as TextCell
				// FIXME: set proper colors
				// cell.setColors(null, Theme.key_windowBackgroundWhiteBlueText2);
				cell.setText(LocaleController.formatString("AddContactByPhone", R.string.AddContactByPhone, PhoneFormat.getInstance().format("+$str")), false)
			}
		}
	}

	private var globalSearchCollapsed = true

	init {
		searchAdapterHelper.setDelegate(object : SearchAdapterHelperDelegate {
			override fun onDataSetChanged(searchId: Int) {
				waitingResponseCount--

				lastGlobalSearchId = searchId

				if (lastLocalSearchId != searchId) {
					searchResult.clear()
				}

				if (lastMessagesSearchId != searchId) {
					searchResultMessages.clear()
				}

				isSearchWas = true

				delegate?.searchStateChanged(waitingResponseCount > 0, true)

				notifyDataSetChanged()

				delegate?.runResultsEnterAnimation()
			}

			override fun onSetHashtags(arrayList: ArrayList<HashtagObject>?, hashMap: HashMap<String, HashtagObject>?) {
				arrayList?.forEach { hashtagObject ->
					hashtagObject.hashtag?.let {
						searchResultHashtags.add(it)
					}
				}

				delegate?.searchStateChanged(waitingResponseCount > 0, false)

				notifyDataSetChanged()
			}

			override fun canApplySearchResults(searchId: Int): Boolean {
				return searchId == lastSearchId
			}

			override val excludeUsersIds: LongArray?
				get() = ignoredIds
		})

		needMessagesSearch = messagesSearch
		dialogsType = type
		selfUserId = getInstance(currentAccount).getClientUserId()

		loadRecentSearch()

		MediaDataController.getInstance(currentAccount).loadHints(true)
	}

	override fun getItemViewType(i: Int): Int {
		@Suppress("NAME_SHADOWING") var i = i

		if (searchResultHashtags.isNotEmpty()) {
			return if (i == 0) VIEW_TYPE_GRAY_SECTION else VIEW_TYPE_HASHTAG_CELL
		}

		if (isRecentSearchDisplayed) {
			val offset = if (!isSearchWas && MediaDataController.getInstance(currentAccount).hints.isNotEmpty()) 1 else 0

			if (i < offset) {
				return VIEW_TYPE_CATEGORY_LIST
			}

			if (i == offset) {
				return VIEW_TYPE_GRAY_SECTION
			}

			if (i < recentItemsCount) {
				return VIEW_TYPE_PROFILE_CELL
			}

			i -= recentItemsCount
		}

		val globalSearch = searchAdapterHelper.globalSearch
		val localCount = searchResult.size
		val localServerCount = searchAdapterHelper.localServerSearch.size

		if (localCount + localServerCount > 0 && recentItemsCount > 0) {
			if (i == 0) {
				return VIEW_TYPE_GRAY_SECTION
			}

			i--
		}

		var globalCount = if (globalSearch.isEmpty()) 0 else globalSearch.size + 1

		if (globalCount > 4 && globalSearchCollapsed) {
			globalCount = 4
		}

		val messagesCount = if (searchResultMessages.isEmpty()) 0 else searchResultMessages.size + 1

		if (i in 0 until localCount) {
			return VIEW_TYPE_PROFILE_CELL
		}

		i -= localCount

		if (i in 0 until localServerCount) {
			return VIEW_TYPE_PROFILE_CELL
		}

		i -= localServerCount

		if (i in 0 until globalCount) {
			return if (i == 0) {
				VIEW_TYPE_GRAY_SECTION
			}
			else {
				VIEW_TYPE_PROFILE_CELL
			}
		}

		i -= globalCount

		return if (i in 0 until messagesCount) {
			if (i == 0) {
				VIEW_TYPE_GRAY_SECTION
			}
			else {
				VIEW_TYPE_DIALOG_CELL
			}
		}
		else {
			VIEW_TYPE_LOADING
		}
	}

	fun setFiltersDelegate(filtersDelegate: FilteredSearchView.Delegate?, update: Boolean) {
		this.filtersDelegate = filtersDelegate

		if (filtersDelegate != null && update) {
			filtersDelegate.updateFiltersView(false, null, localTipDates, localTipArchive)
		}
	}

	private fun filterRecent(query: String?) {
		filteredRecentQuery = query

		filteredRecentSearchObjects.clear()

		if (query.isNullOrEmpty()) {
			return
		}

		val lowerCasedQuery = query.lowercase(Locale.getDefault())
		val count = recentSearchObjects.size

		for (i in 0 until count) {
			val o = recentSearchObjects[i]
			val obj = o.`object` ?: continue

			var title: String? = null
			var username: String? = null

			when (obj) {
				is TLRPC.Chat -> {
					title = obj.title
					username = obj.username
				}

				is User -> {
					title = getUserName(obj)
					username = obj.username
				}

				is TLRPC.ChatInvite -> {
					title = obj.title
				}
			}

			if (title != null && wordStartsWith(title.lowercase(Locale.getDefault()), lowerCasedQuery) || username != null && wordStartsWith(username.lowercase(Locale.getDefault()), lowerCasedQuery)) {
				filteredRecentSearchObjects.add(o)
			}

			if (filteredRecentSearchObjects.size >= 5) {
				break
			}
		}
	}

	private fun wordStartsWith(loweredTitle: String?, loweredQuery: String?): Boolean {
		if (loweredQuery == null || loweredTitle == null) {
			return false
		}

		val words = loweredTitle.lowercase(Locale.getDefault()).split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
		var found = false

		for (word in words) {
			if (word.startsWith(loweredQuery) || loweredQuery.startsWith(word)) {
				found = true
				break
			}
		}

		return found
	}

	open fun loadRecentSearch() {
		loadRecentSearch(currentAccount, dialogsType) { arrayList, hashMap ->
			this@DialogsSearchAdapter.setRecentSearch(arrayList, hashMap)
		}
	}

	fun interface OnRecentSearchLoaded {
		fun setRecentSearch(arrayList: ArrayList<RecentSearchObject>, hashMap: LongSparseArray<RecentSearchObject>)
	}

	companion object {
		private const val VIEW_TYPE_PROFILE_CELL = 0
		private const val VIEW_TYPE_GRAY_SECTION = 1
		private const val VIEW_TYPE_DIALOG_CELL = 2
		private const val VIEW_TYPE_LOADING = 3
		private const val VIEW_TYPE_HASHTAG_CELL = 4
		private const val VIEW_TYPE_CATEGORY_LIST = 5
		private const val VIEW_TYPE_ADD_BY_PHONE = 6

		@JvmStatic
		fun loadRecentSearch(currentAccount: Int, dialogsType: Int, callback: OnRecentSearchLoaded) {
			MessagesStorage.getInstance(currentAccount).storageQueue.postRunnable {
				try {
					val cursor = MessagesStorage.getInstance(currentAccount).database.queryFinalized("SELECT did, date FROM search_recent WHERE 1")
					val usersToLoad: ArrayList<Long?> = ArrayList()
					val chatsToLoad: ArrayList<Long?> = ArrayList()
					val encryptedToLoad: ArrayList<Int?> = ArrayList()
					// val encUsers: ArrayList<TLRPC.User> = ArrayList()
					val arrayList: ArrayList<RecentSearchObject> = ArrayList()
					val hashMap = LongSparseArray<RecentSearchObject>()

					while (cursor.next()) {
						val did: Long = cursor.longValue(0)
						var add = false

						if (DialogObject.isEncryptedDialog(did)) {
							if (dialogsType == 0 || dialogsType == 3) {
								val encryptedChatId = DialogObject.getEncryptedChatId(did)

								if (!encryptedToLoad.contains(encryptedChatId)) {
									encryptedToLoad.add(encryptedChatId)
									add = true
								}
							}
						}
						else if (DialogObject.isUserDialog(did)) {
							if (dialogsType != 2 && !usersToLoad.contains(did)) {
								usersToLoad.add(did)
								add = true
							}
						}
						else {
							if (!chatsToLoad.contains(-did)) {
								chatsToLoad.add(-did)
								add = true
							}
						}
						if (add) {
							val recentSearchObject = RecentSearchObject()
							recentSearchObject.did = did
							recentSearchObject.date = cursor.intValue(1)
							arrayList.add(recentSearchObject)
							hashMap.put(recentSearchObject.did, recentSearchObject)
						}
					}

					cursor.dispose()

					val users = ArrayList<User>()

					if (encryptedToLoad.isNotEmpty()) {
						val encryptedChats = ArrayList<TLRPC.EncryptedChat>()

						MessagesStorage.getInstance(currentAccount).getEncryptedChatsInternal(TextUtils.join(",", encryptedToLoad), encryptedChats, usersToLoad)

						for (a in 0 until encryptedChats.size) {
							val recentSearchObject = hashMap[DialogObject.makeEncryptedDialogId(encryptedChats[a].id.toLong())]
							recentSearchObject?.`object` = encryptedChats[a]
						}
					}

					if (chatsToLoad.isNotEmpty()) {
						val chats = ArrayList<TLRPC.Chat>()

						MessagesStorage.getInstance(currentAccount).getChatsInternal(TextUtils.join(",", chatsToLoad), chats)

						for (a in 0 until chats.size) {
							val chat = chats[a]
							val did = -chat.id

							if (chat.migrated_to != null) {
								val recentSearchObject = hashMap[did]

								hashMap.remove(did)

								if (recentSearchObject != null) {
									arrayList.remove(recentSearchObject)
								}
							}
							else {
								val recentSearchObject = hashMap[did]
								recentSearchObject?.`object` = chat
							}
						}
					}

					if (usersToLoad.isNotEmpty()) {
						MessagesStorage.getInstance(currentAccount).getUsersInternal(TextUtils.join(",", usersToLoad), users)

						for (a in 0 until users.size) {
							val user = users[a]
							val recentSearchObject = hashMap[user.id]
							recentSearchObject?.`object` = user
						}
					}

					arrayList.sortWith { lhs, rhs ->
						if (lhs.date < rhs.date) {
							return@sortWith 1
						}
						else if (lhs.date > rhs.date) {
							return@sortWith -1
						}
						else {
							return@sortWith 0
						}
					}

					AndroidUtilities.runOnUIThread {
						callback.setRecentSearch(arrayList, hashMap)
					}
				}
				catch (e: java.lang.Exception) {
					FileLog.e(e)
				}
			}
		}
	}
}
