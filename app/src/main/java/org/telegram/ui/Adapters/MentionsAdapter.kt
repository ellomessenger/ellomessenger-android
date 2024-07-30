/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Adapters

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.text.Spanned
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.collection.LongSparseArray
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MediaDataController.KeywordResult
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.RequestDelegate
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.BotInfo
import org.telegram.tgnet.TLRPC.BotInlineResult
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.TL_botInlineMessageMediaAuto
import org.telegram.tgnet.TLRPC.TL_channelFull
import org.telegram.tgnet.TLRPC.TL_channelParticipantsMentions
import org.telegram.tgnet.TLRPC.TL_channels_getParticipants
import org.telegram.tgnet.TLRPC.TL_contacts_resolveUsername
import org.telegram.tgnet.TLRPC.TL_contacts_resolvedPeer
import org.telegram.tgnet.TLRPC.TL_document
import org.telegram.tgnet.TLRPC.TL_documentAttributeSticker
import org.telegram.tgnet.TLRPC.TL_groupCallParticipant
import org.telegram.tgnet.TLRPC.TL_inlineBotSwitchPM
import org.telegram.tgnet.TLRPC.TL_inputGeoPoint
import org.telegram.tgnet.TLRPC.TL_inputPeerEmpty
import org.telegram.tgnet.TLRPC.TL_messages_botResults
import org.telegram.tgnet.TLRPC.TL_messages_getInlineBotResults
import org.telegram.tgnet.TLRPC.TL_messages_getStickers
import org.telegram.tgnet.TLRPC.TL_messages_stickers
import org.telegram.tgnet.tlrpc.TL_photo
import org.telegram.tgnet.TLRPC.TL_photoSize
import org.telegram.tgnet.TLRPC.TL_photoSizeProgressive
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_channels_channelParticipants
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Adapters.SearchAdapterHelper.HashtagObject
import org.telegram.ui.Adapters.SearchAdapterHelper.SearchAdapterHelperDelegate
import org.telegram.ui.Cells.BotSwitchCell
import org.telegram.ui.Cells.ContextLinkCell
import org.telegram.ui.Cells.MentionCell
import org.telegram.ui.Cells.StickerCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.EmojiView.ChooseStickerActionTracker
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import java.util.Collections
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
class MentionsAdapter(private val mContext: Context, private val dialogId: Long, private val threadMessageId: Int, private val delegate: MentionsAdapterDelegate?) : SelectionAdapter(), NotificationCenterDelegate {
	private val useDividers = false
	private val searchAdapterHelper = SearchAdapterHelper(true)
	private val stickersToLoad = ArrayList<String>()
	private var currentAccount = UserConfig.selectedAccount
	private var info: ChatFull? = null
	private var searchResultUsernames: ArrayList<TLObject>? = null
	private var searchResultUsernamesMap: LongSparseArray<TLObject>? = null
	private var searchGlobalRunnable: Runnable? = null
	private var searchResultHashtags: ArrayList<String?>? = null
	private var searchResultCommands: ArrayList<String>? = null
	private var searchResultCommandsHelp: ArrayList<String>? = null
	private var searchResultSuggestions: ArrayList<KeywordResult>? = null
	private var lastSearchKeyboardLanguage: Array<String>? = null
	private var searchResultCommandsUsers: ArrayList<User?>? = null
	private var botInfo: LongSparseArray<BotInfo>? = null
	private var lastText: String? = null
	private var lastForSearch = false
	private var lastUsernameOnly = false
	private var lastPosition = 0
	private var messages: List<MessageObject>? = null
	private var needUsernames = true
	private var needBotContext = true
	private var botsCount = 0
	private var inlineMediaEnabled = true
	private var channelLastReqId = 0
	private var channelReqId = 0
	private var isSearchingMentions = false
	private var mentionsStickersActionTracker: ChooseStickerActionTracker? = null
	private var visibleByStickersSearch = false
	private var cancelDelayRunnable: Runnable? = null
	private var searchingContextUsername: String? = null
	private var searchingContextQuery: String? = null
	private var nextQueryOffset: String? = null
	private var contextUsernameRequestId = 0
	private var contextQueryRequestId = 0
	private var noUserName = false
	private var contextMedia = false
	private var contextQueryRunnable: Runnable? = null
	private var lastKnownLocation: Location? = null
	private var stickers: ArrayList<StickerResult>? = null
	private var stickersMap: HashMap<String, TLRPC.Document>? = null
	private var lastSticker: String? = null
	private var lastReqId = 0
	private var delayLocalResults = false
	private var parentFragment: ChatActivity? = null
	private var lastData: Array<Any?>? = null
	private var isReversed = false

	var searchResultBotContext: ArrayList<BotInlineResult>? = null
		private set

	var botContextSwitch: TL_inlineBotSwitchPM? = null
		private set

	var resultStartPosition = 0
		private set

	var resultLength = 0
		private set

	var contextBotUser: User? = null
		private set

	var lastItemCount = -1
		private set

	private val locationProvider = object : SendMessagesHelper.LocationProvider(object : LocationProviderDelegate {
		override fun onLocationAcquired(location: Location) {
			if (contextBotUser?.bot_inline_geo == true) {
				lastKnownLocation = location
				searchForContextBotResults(true, contextBotUser, searchingContextQuery, "")
			}
		}

		override fun onUnableLocationAcquire() {
			onLocationUnavailable()
		}
	}) {
		override fun stop() {
			super.stop()
			lastKnownLocation = null
		}
	}

	init {
		searchAdapterHelper.setDelegate(object : SearchAdapterHelperDelegate {
			override fun canApplySearchResults(searchId: Int): Boolean {
				return true
			}

			override val excludeCallParticipants: LongSparseArray<TL_groupCallParticipant>?
				get() = null

			override val excludeUsers: LongSparseArray<User>?
				get() = null

			override fun onDataSetChanged(searchId: Int) {
				notifyDataSetChanged()
			}

			override fun onSetHashtags(arrayList: ArrayList<HashtagObject>?, hashMap: HashMap<String, HashtagObject>?) {
				if (lastText != null) {
					searchUsernameOrHashtag(lastText, lastPosition, messages, lastUsernameOnly, lastForSearch)
				}
			}
		})

		NotificationCenter.getInstance(currentAccount).let {
			it.addObserver(this, NotificationCenter.fileLoaded)
			it.addObserver(this, NotificationCenter.fileLoadFailed)
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.fileLoaded, NotificationCenter.fileLoadFailed -> {
				if (!stickers.isNullOrEmpty() && stickersToLoad.isNotEmpty() && visibleByStickersSearch) {
					val fileName = args[0] as String

					stickersToLoad.remove(fileName)

					if (stickersToLoad.isEmpty()) {
						delegate?.needChangePanelVisibility(itemCountInternal > 0)
					}
				}
			}
		}
	}

	private fun addStickerToResult(document: TLRPC.Document?, parent: Any) {
		if (document == null) {
			return
		}

		val key = document.dc_id.toString() + "_" + document.id

		if (stickersMap?.containsKey(key) == true) {
			return
		}

		if (!UserConfig.getInstance(currentAccount).isPremium && MessageObject.isPremiumSticker(document)) {
			return
		}

		if (stickers == null) {
			stickers = ArrayList()
			stickersMap = HashMap()
		}

		stickers?.add(StickerResult(document, parent))
		stickersMap?.put(key, document)

		mentionsStickersActionTracker?.checkVisibility()
	}

	private fun addStickersToResult(documents: List<TLRPC.Document>?, parent: Any?) {
		@Suppress("NAME_SHADOWING") var parent = parent

		if (documents.isNullOrEmpty()) {
			return
		}

		var a = 0
		val size = documents.size

		while (a < size) {
			val document = documents[a]
			val key = document.dc_id.toString() + "_" + document.id

			if (stickersMap != null && stickersMap!!.containsKey(key)) {
				a++
				continue
			}

			if (!UserConfig.getInstance(currentAccount).isPremium && MessageObject.isPremiumSticker(document)) {
				a++
				continue
			}

			var b = 0
			val size2 = document.attributes.size

			while (b < size2) {
				val attribute = document.attributes[b]

				if (attribute is TL_documentAttributeSticker) {
					parent = attribute.stickerset
					break
				}

				b++
			}

			if (stickers == null) {
				stickers = ArrayList()
				stickersMap = HashMap()
			}

			stickers?.add(StickerResult(document, parent))
			stickersMap?.put(key, document)

			a++
		}
	}

	private fun checkStickerFilesExistAndDownload(): Boolean {
		val stickers = stickers ?: return false

		stickersToLoad.clear()

		val size = min(6, stickers.size)

		for (a in 0 until size) {
			val result = stickers[a]
			val thumb = FileLoader.getClosestPhotoSizeWithSize(result.sticker.thumbs, 90)

			if (thumb is TL_photoSize || thumb is TL_photoSizeProgressive) {
				val f = FileLoader.getInstance(currentAccount).getPathToAttach(thumb, "webp", true)

				if (!f.exists()) {
					stickersToLoad.add(FileLoader.getAttachFileName(thumb, "webp"))
					FileLoader.getInstance(currentAccount).loadFile(ImageLocation.getForDocument(thumb, result.sticker), result.parent, "webp", FileLoader.PRIORITY_NORMAL, 1)
				}
			}
		}

		return stickersToLoad.isEmpty()
	}

	private fun isValidSticker(document: TLRPC.Document, emoji: String?): Boolean {
		var b = 0
		val size2 = document.attributes.size

		while (b < size2) {
			val attribute = document.attributes[b]

			if (attribute is TL_documentAttributeSticker) {
				if (attribute.alt != null && emoji != null && attribute.alt.contains(emoji)) {
					return true
				}

				break
			}

			b++
		}

		return false
	}

	private fun searchServerStickers(emoji: String?, originalEmoji: String?) {
		val req = TL_messages_getStickers()
		req.emoticon = originalEmoji
		req.hash = 0

		lastReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				lastReqId = 0

				if (emoji != lastSticker || response !is TL_messages_stickers) {
					return@runOnUIThread
				}

				delayLocalResults = false

				val oldCount = stickers?.size ?: 0

				addStickersToResult(response.stickers, "sticker_search_$emoji")

				val newCount = stickers?.size ?: 0

				if (!visibleByStickersSearch && !stickers.isNullOrEmpty()) {
					checkStickerFilesExistAndDownload()
					delegate?.needChangePanelVisibility(itemCountInternal > 0)
					visibleByStickersSearch = true
				}

				if (oldCount != newCount) {
					notifyDataSetChanged()
				}
			}
		}
	}

	override fun notifyDataSetChanged() {
		if (lastItemCount == -1 || lastData == null) {
			delegate?.onItemCountUpdate(0, itemCount)

			super.notifyDataSetChanged()

			lastData = arrayOfNulls<Any?>(itemCount).also {
				for (i in 0 until itemCount) {
					it[i] = getItem(i)
				}
			}
		}
		else {
			val oldCount = lastItemCount
			val newCount = itemCount
			var hadChanges = oldCount != newCount
			val min = min(oldCount, newCount)
			val newData = arrayOfNulls<Any>(newCount)

			for (i in 0 until newCount) {
				newData[i] = getItem(i)
			}

			for (i in 0 until min) {
				if (i >= (lastData?.size ?: 0) || i >= newData.size || !itemsEqual(lastData?.getOrNull(i), newData[i])) {
					notifyItemChanged(i)
					hadChanges = true
				}
				else if (i == oldCount - 1 != (i == newCount - 1) && useDividers) {
					notifyItemChanged(i) // divider update
				}
			}

			notifyItemRangeRemoved(min, oldCount - min)
			notifyItemRangeInserted(min, newCount - min)

			if (hadChanges) {
				delegate?.onItemCountUpdate(oldCount, newCount)
			}

			lastData = newData
		}
	}

	private fun itemsEqual(a: Any?, b: Any?): Boolean {
		if (a === b) {
			return true
		}

		if (a is StickerResult && b is StickerResult && a.sticker === b.sticker) {
			return true
		}

		if (a is User && b is User && a.id == b.id) {
			return true
		}

		if (a is Chat && b is Chat && a.id == b.id) {
			return true
		}

		if (a is String && b is String && a == b) {
			return true
		}

		return a is KeywordResult && b is KeywordResult && a.keyword != null && a.keyword == b.keyword && a.emoji != null && a.emoji == b.emoji
	}

	private fun clearStickers() {
		lastSticker = null
		stickers = null
		stickersMap = null
		notifyDataSetChanged()

		if (lastReqId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(lastReqId, true)
			lastReqId = 0
		}

		if (mentionsStickersActionTracker != null) {
			mentionsStickersActionTracker!!.checkVisibility()
		}
	}

	fun onDestroy() {
		locationProvider.stop()

		if (contextQueryRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(contextQueryRunnable)
			contextQueryRunnable = null
		}

		if (contextUsernameRequestId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(contextUsernameRequestId, true)
			contextUsernameRequestId = 0
		}

		if (contextQueryRequestId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(contextQueryRequestId, true)
			contextQueryRequestId = 0
		}

		contextBotUser = null
		inlineMediaEnabled = true
		searchingContextUsername = null
		searchingContextQuery = null
		noUserName = false

		NotificationCenter.getInstance(currentAccount).let {
			it.removeObserver(this, NotificationCenter.fileLoaded)
			it.removeObserver(this, NotificationCenter.fileLoadFailed)
		}
	}

	fun setParentFragment(fragment: ChatActivity?) {
		parentFragment = fragment
	}

	fun setChatInfo(chatInfo: ChatFull?) {
		currentAccount = UserConfig.selectedAccount
		info = chatInfo

		if (!inlineMediaEnabled && contextBotUser != null && parentFragment != null) {
			val chat = parentFragment?.currentChat

			if (chat != null) {
				inlineMediaEnabled = ChatObject.canSendStickers(chat)

				if (inlineMediaEnabled) {
					searchResultUsernames = null
					notifyDataSetChanged()
					delegate?.needChangePanelVisibility(false)
					processFoundUser(contextBotUser)
				}
			}
		}

		if (lastText != null) {
			searchUsernameOrHashtag(lastText, lastPosition, messages, lastUsernameOnly, lastForSearch)
		}
	}

	fun setNeedUsernames(value: Boolean) {
		needUsernames = value
	}

	fun setNeedBotContext(value: Boolean) {
		needBotContext = value
	}

	fun setBotInfo(info: LongSparseArray<BotInfo>?) {
		botInfo = info
	}

	fun setBotsCount(count: Int) {
		botsCount = count
	}

	fun clearRecentHashtags() {
		searchAdapterHelper.clearRecentHashtags()
		searchResultHashtags?.clear()
		notifyDataSetChanged()
		delegate?.needChangePanelVisibility(false)
	}

	val contextBotId: Long
		get() = contextBotUser?.id ?: 0

	val contextBotName: String
		get() = contextBotUser?.username ?: ""

	private fun processFoundUser(user: User?) {
		contextUsernameRequestId = 0

		locationProvider.stop()

		if (user != null && user.bot && user.bot_inline_placeholder != null) {
			contextBotUser = user

			if (parentFragment != null) {
				val chat = parentFragment?.currentChat
				if (chat != null) {
					inlineMediaEnabled = ChatObject.canSendStickers(chat)

					if (!inlineMediaEnabled) {
						notifyDataSetChanged()
						delegate?.needChangePanelVisibility(true)
						return
					}
				}
			}

			if (contextBotUser?.bot_inline_geo == true) {
				val preferences = MessagesController.getNotificationsSettings(currentAccount)
				val allowGeo = preferences.getBoolean("inlinegeo_" + contextBotUser?.id, false)

				val context = parentFragment?.parentActivity

				if (!allowGeo && context != null) {
					val foundContextBotFinal = contextBotUser
					val builder = AlertDialog.Builder(context)
					builder.setTitle(context.getString(R.string.ShareYouLocationTitle))
					builder.setMessage(context.getString(R.string.ShareYouLocationInline))

					val buttonClicked = BooleanArray(1)

					builder.setPositiveButton(context.getString(R.string.OK)) { _, _ ->
						buttonClicked[0] = true

						if (foundContextBotFinal != null) {
							val preferences1 = MessagesController.getNotificationsSettings(currentAccount)
							preferences1.edit().putBoolean("inlinegeo_" + foundContextBotFinal.id, true).commit()
							checkLocationPermissionsOrStart()
						}
					}

					builder.setNegativeButton(context.getString(R.string.Cancel)) { _, _ ->
						buttonClicked[0] = true
						onLocationUnavailable()

					}
					parentFragment?.showDialog(builder.create()) {
						if (!buttonClicked[0]) {
							onLocationUnavailable()
						}
					}
				}
				else {
					checkLocationPermissionsOrStart()
				}
			}
		}
		else {
			contextBotUser = null
			inlineMediaEnabled = true
		}

		if (contextBotUser == null) {
			noUserName = true
		}
		else {
			delegate?.onContextSearch(true)
			searchForContextBotResults(true, contextBotUser, searchingContextQuery, "")
		}
	}

	private fun searchForContextBot(username: String?, query: String?) {
		if (contextBotUser?.username != null && contextBotUser?.username == username && searchingContextQuery != null && searchingContextQuery == query) {
			return
		}

		if (contextBotUser != null) {
			if (!inlineMediaEnabled && username != null && query != null) {
				return
			}

			delegate?.needChangePanelVisibility(false)
		}

		if (contextQueryRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(contextQueryRunnable)
			contextQueryRunnable = null
		}

		if (username.isNullOrEmpty() || searchingContextUsername != null && searchingContextUsername != username) {
			if (contextUsernameRequestId != 0) {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(contextUsernameRequestId, true)
				contextUsernameRequestId = 0
			}

			if (contextQueryRequestId != 0) {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(contextQueryRequestId, true)
				contextQueryRequestId = 0
			}

			contextBotUser = null
			inlineMediaEnabled = true
			searchingContextUsername = null
			searchingContextQuery = null
			locationProvider.stop()
			noUserName = false

			delegate?.onContextSearch(false)

			if (username.isNullOrEmpty()) {
				return
			}
		}

		if (query == null) {
			if (contextQueryRequestId != 0) {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(contextQueryRequestId, true)
				contextQueryRequestId = 0
			}

			searchingContextQuery = null

			delegate?.onContextSearch(false)

			return
		}

		if (delegate != null) {
			if (contextBotUser != null) {
				delegate.onContextSearch(true)
			}
			else if (username == "gif") {
				searchingContextUsername = "gif"
				delegate.onContextSearch(false)
			}
		}

		val messagesController = MessagesController.getInstance(currentAccount)
		val messagesStorage = MessagesStorage.getInstance(currentAccount)

		searchingContextQuery = query

		contextQueryRunnable = object : Runnable {
			override fun run() {
				if (contextQueryRunnable !== this) {
					return
				}

				contextQueryRunnable = null

				if (contextBotUser != null || noUserName) {
					if (noUserName) {
						return
					}

					searchForContextBotResults(true, contextBotUser, query, "")
				}
				else {
					searchingContextUsername = username

					val `object` = messagesController.getUserOrChat(searchingContextUsername)

					if (`object` is User) {
						processFoundUser(`object`)
					}
					else {
						val req = TL_contacts_resolveUsername()
						req.username = searchingContextUsername

						contextUsernameRequestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
							AndroidUtilities.runOnUIThread {
								if (searchingContextUsername == null || searchingContextUsername != username) {
									return@runOnUIThread
								}

								var user: User? = null

								if (error == null) {
									val res = response as? TL_contacts_resolvedPeer

									if (res != null && !res.users.isNullOrEmpty()) {
										user = res.users[0]
										messagesController.putUser(user, false)
										messagesStorage.putUsersAndChats(res.users, null, true, true)
									}
								}

								processFoundUser(user)

								contextUsernameRequestId = 0
							}
						}
					}
				}
			}
		}

		AndroidUtilities.runOnUIThread(contextQueryRunnable, 400)
	}

	@SuppressLint("Range")
	private fun onLocationUnavailable() {
		if (contextBotUser?.bot_inline_geo == true) {
			lastKnownLocation = Location("network")
			lastKnownLocation?.latitude = -1000.0
			lastKnownLocation?.longitude = -1000.0

			searchForContextBotResults(true, contextBotUser, searchingContextQuery, "")
		}
	}

	private fun checkLocationPermissionsOrStart() {
		val activity = parentFragment?.parentActivity ?: return

		if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 2)
			return
		}

		if (contextBotUser?.bot_inline_geo == true) {
			locationProvider.start()
		}
	}

	fun setSearchingMentions(value: Boolean) {
		isSearchingMentions = value
	}

	val botCaption: String?
		get() {
			if (contextBotUser != null) {
				return contextBotUser?.bot_inline_placeholder
			}
			else if (searchingContextUsername != null && searchingContextUsername == "gif") {
				return "Search GIFs"
			}

			return null
		}

	fun searchForContextBotForNextOffset() {
		if (contextQueryRequestId != 0 || nextQueryOffset.isNullOrEmpty() || contextBotUser == null || searchingContextQuery == null) {
			return
		}

		searchForContextBotResults(true, contextBotUser, searchingContextQuery, nextQueryOffset!!)
	}

	private fun searchForContextBotResults(cache: Boolean, user: User?, query: String?, offset: String) {
		if (contextQueryRequestId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(contextQueryRequestId, true)
			contextQueryRequestId = 0
		}

		if (!inlineMediaEnabled) {
			delegate?.onContextSearch(false)
			return
		}

		if (query == null || user == null) {
			searchingContextQuery = null
			return
		}

		if (user.bot_inline_geo && lastKnownLocation == null) {
			return
		}

		val key = dialogId.toString() + "_" + query + "_" + offset + "_" + dialogId + "_" + user.id + "_" + if (user.bot_inline_geo && lastKnownLocation?.latitude != -1000.0) (lastKnownLocation?.latitude ?: 0.0) + (lastKnownLocation?.longitude ?: 0.0) else ""
		val messagesStorage = MessagesStorage.getInstance(currentAccount)

		val requestDelegate = RequestDelegate { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (query != searchingContextQuery) {
					return@runOnUIThread
				}

				contextQueryRequestId = 0

				if (cache && response == null) {
					searchForContextBotResults(false, user, query, offset)
				}
				else {
					delegate?.onContextSearch(false)
				}

				if (response is TL_messages_botResults) {
					if (!cache && response.cache_time != 0) {
						messagesStorage.saveBotCache(key, response)
					}

					nextQueryOffset = response.next_offset

					if (botContextSwitch == null) {
						botContextSwitch = response.switch_pm
					}

					var a = 0

					while (a < response.results.size) {
						val result = response.results[a]

						if (result.document !is TL_document && result.photo !is TL_photo && "game" != result.type && result.content == null && result.send_message is TL_botInlineMessageMediaAuto) {
							response.results.removeAt(a)
							a--
						}

						result.query_id = response.query_id

						a++
					}

					var added = false

					if (searchResultBotContext == null || offset.isEmpty()) {
						searchResultBotContext = response.results
						contextMedia = response.gallery
					}
					else {
						added = true

						searchResultBotContext?.addAll(response.results)

						if (response.results.isEmpty()) {
							nextQueryOffset = ""
						}
					}

					if (cancelDelayRunnable != null) {
						AndroidUtilities.cancelRunOnUIThread(cancelDelayRunnable)
						cancelDelayRunnable = null
					}

					searchResultHashtags = null
					stickers = null
					searchResultUsernames = null
					searchResultUsernamesMap = null
					searchResultCommands = null
					searchResultSuggestions = null
					searchResultCommandsHelp = null
					searchResultCommandsUsers = null

					if (added) {
						val hasTop = botContextSwitch != null
						notifyItemChanged((searchResultBotContext?.size ?: 0) - response.results.size + (if (hasTop) 1 else 0) - 1)
						notifyItemRangeInserted((searchResultBotContext?.size ?: 0) - response.results.size + if (hasTop) 1 else 0, response.results.size)
					}
					else {
						notifyDataSetChanged()
					}

					delegate?.needChangePanelVisibility(!searchResultBotContext.isNullOrEmpty() || botContextSwitch != null)
				}
			}
		}

		if (cache) {
			messagesStorage.getBotCache(key, requestDelegate)
		}
		else {
			val req = TL_messages_getInlineBotResults()
			req.bot = MessagesController.getInstance(currentAccount).getInputUser(user)
			req.query = query
			req.offset = offset

			if (user.bot_inline_geo) {
				lastKnownLocation?.let {
					if (it.latitude != -1000.0) {
						req.flags = req.flags or 1
						req.geo_point = TL_inputGeoPoint()
						req.geo_point.lat = AndroidUtilities.fixLocationCoordinate(it.latitude)
						req.geo_point._long = AndroidUtilities.fixLocationCoordinate(it.longitude)
					}
				}
			}

			if (DialogObject.isEncryptedDialog(dialogId)) {
				req.peer = TL_inputPeerEmpty()
			}
			else {
				req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId)
			}

			contextQueryRequestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors)
		}
	}

	fun searchUsernameOrHashtag(charSequence: CharSequence?, position: Int, messageObjects: List<MessageObject>?, usernameOnly: Boolean, forSearch: Boolean) {
		val text = charSequence?.toString()

		if (cancelDelayRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(cancelDelayRunnable)
			cancelDelayRunnable = null
		}

		if (channelReqId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(channelReqId, true)
			channelReqId = 0
		}

		if (searchGlobalRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(searchGlobalRunnable)
			searchGlobalRunnable = null
		}

		if (text.isNullOrEmpty() || text.length > MessagesController.getInstance(currentAccount).maxMessageLength) {
			searchForContextBot(null, null)
			delegate?.needChangePanelVisibility(false)
			lastText = null
			clearStickers()
			return
		}

		var searchPosition = position

		if (text.isNotEmpty()) {
			searchPosition--
		}

		lastText = null
		lastUsernameOnly = usernameOnly
		lastForSearch = forSearch

		val result = StringBuilder()
		var foundType = -1
		val searchEmoji = !usernameOnly && text.isNotEmpty() && text.length <= 14
		var originalEmoji = ""

		if (searchEmoji) {
			originalEmoji = text
			var emoji: CharSequence = originalEmoji
			var length = emoji.length
			var a = 0

			while (a < length) {
				val ch = emoji[a]
				val nch: Char = if (a < length - 1) emoji[a + 1] else Char(0)

				if (a < length - 1 && ch.code == 0xD83C && nch.code >= 0xDFFB && nch.code <= 0xDFFF) {
					emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 2, emoji.length))
					length -= 2
					a--
				}
				else if (ch.code == 0xfe0f) {
					emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 1, emoji.length))
					length--
					a--
				}

				a++
			}

			lastSticker = emoji.toString().trim()
		}

		var isValidEmoji = searchEmoji && (Emoji.isValidEmoji(originalEmoji) || Emoji.isValidEmoji(lastSticker))

		if (isValidEmoji && charSequence is Spanned) {
			val spans = charSequence.getSpans(0, charSequence.length, AnimatedEmojiSpan::class.java)
			isValidEmoji = spans.isNullOrEmpty()
		}

		if (isValidEmoji && parentFragment != null && (parentFragment?.currentChat == null || ChatObject.canSendStickers(parentFragment?.currentChat))) {
			stickersToLoad.clear()

			if (SharedConfig.suggestStickers == 2) {
				if (visibleByStickersSearch && SharedConfig.suggestStickers == 2) {
					visibleByStickersSearch = false
					delegate?.needChangePanelVisibility(false)
					notifyDataSetChanged()
				}

				return
			}

			stickers = null
			stickersMap = null

			foundType = 4

			if (lastReqId != 0) {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(lastReqId, true)
				lastReqId = 0
			}

			val serverStickersOnly = MessagesController.getInstance(currentAccount).suggestStickersApiOnly

			delayLocalResults = false

			if (!serverStickersOnly) {
				val recentStickers = MediaDataController.getInstance(currentAccount).getRecentStickersNoCopy(MediaDataController.TYPE_IMAGE)
				val favsStickers = MediaDataController.getInstance(currentAccount).getRecentStickersNoCopy(MediaDataController.TYPE_FAVE)
				var recentsAdded = 0

				run {
					var a = 0
					val size = min(20, recentStickers.size)

					while (a < size) {
						val document = recentStickers[a]

						if (isValidSticker(document, lastSticker)) {
							addStickerToResult(document, "recent")
							recentsAdded++

							if (recentsAdded >= 5) {
								break
							}
						}

						a++
					}
				}

				for (document in favsStickers) {
					if (isValidSticker(document, lastSticker)) {
						addStickerToResult(document, "fav")
					}
				}

				val allStickers = MediaDataController.getInstance(currentAccount).allStickers
				val newStickers = allStickers[lastSticker]

				if (!newStickers.isNullOrEmpty()) {
					addStickersToResult(newStickers, null)
				}

				stickers?.let { stickers ->
					Collections.sort(stickers, object : Comparator<StickerResult> {
						private fun getIndex(result: StickerResult): Int {
							for (a in favsStickers.indices) {
								if (favsStickers[a].id == result.sticker.id) {
									return a + 2000000
								}
							}

							for (a in 0 until min(20, recentStickers.size)) {
								if (recentStickers[a].id == result.sticker.id) {
									return recentStickers.size - a + 1000000
								}
							}

							return -1
						}

						override fun compare(lhs: StickerResult, rhs: StickerResult): Int {
							val isAnimated1 = MessageObject.isAnimatedStickerDocument(lhs.sticker, true)
							val isAnimated2 = MessageObject.isAnimatedStickerDocument(rhs.sticker, true)

							return if (isAnimated1 == isAnimated2) {
								val idx1 = getIndex(lhs)
								val idx2 = getIndex(rhs)

								if (idx1 > idx2) {
									return -1
								}
								else if (idx1 < idx2) {
									return 1
								}

								0
							}
							else {
								if (isAnimated1) {
									-1
								}
								else {
									1
								}
							}
						}
					})
				}
			}

			if (SharedConfig.suggestStickers == 0 || serverStickersOnly) {
				searchServerStickers(lastSticker, originalEmoji)
			}

			if (!stickers.isNullOrEmpty()) {
				if (SharedConfig.suggestStickers == 0 && (stickers?.size ?: 0) < 5) {
					delayLocalResults = true
					delegate?.needChangePanelVisibility(false)
					visibleByStickersSearch = false
				}
				else {
					checkStickerFilesExistAndDownload()
					val show = stickersToLoad.isEmpty()
					delegate?.needChangePanelVisibility(show)
					visibleByStickersSearch = true
				}

				notifyDataSetChanged()
			}
			else if (visibleByStickersSearch) {
				delegate?.needChangePanelVisibility(false)
				visibleByStickersSearch = false
			}
		}
		else if (!usernameOnly && needBotContext && text[0] == '@') {
			val index = text.indexOf(' ')
			val len = text.length
			var username: String? = null
			var query: String? = null

			if (index > 0) {
				username = text.substring(1, index)
				query = text.substring(index + 1)
			}
			else if (text[len - 1] == 't' && text[len - 2] == 'o' && text[len - 3] == 'b') {
				username = text.substring(1)
				query = ""
			}
			else {
				searchForContextBot(null, null)
			}

			username?.takeIf { it.isNotEmpty() }?.let {
				for (a in 1 until it.length) {
					val ch = it[a]

					if (!(ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z' || ch == '_')) {
						username = ""
						break
					}
				}
			} ?: run {
				username = ""
			}

			searchForContextBot(username, query)
		}
		else {
			searchForContextBot(null, null)
		}

		if (contextBotUser != null) {
			return
		}

		val messagesController = MessagesController.getInstance(currentAccount)
		var dogPosition = -1

		if (usernameOnly) {
			result.append(text.substring(1))
			resultStartPosition = 0
			resultLength = result.length
			foundType = 0
		}
		else {
			for (a in searchPosition downTo 0) {
				if (a >= text.length) {
					continue
				}

				val ch = text[a]

				if (a == 0 || text[a - 1] == ' ' || text[a - 1] == '\n' || ch == ':') {
					if (ch == '@') {
						if (needUsernames || needBotContext && a == 0) {
							if (info == null && a != 0) {
								lastText = text
								lastPosition = position
								messages = messageObjects
								delegate?.needChangePanelVisibility(false)
								return
							}

							dogPosition = a
							foundType = 0
							resultStartPosition = a
							resultLength = result.length + 1

							break
						}
					}
					else if (ch == '#') {
						if (searchAdapterHelper.loadRecentHashtags()) {
							foundType = 1
							resultStartPosition = a
							resultLength = result.length + 1
							result.insert(0, ch)
							break
						}
						else {
							lastText = text
							lastPosition = position
							messages = messageObjects
							// delegate.needChangePanelVisibility(false);
							return
						}
					}
					else if (a == 0 && botInfo != null && ch == '/') {
						foundType = 2
						resultStartPosition = 0
						resultLength = result.length + 1
						break
					}
					else if (ch == ':' && result.isNotEmpty()) {
						val isNextPunctuationChar = PUNCTUATIONS_CHARS.indexOf(result[0]) >= 0

						if (!isNextPunctuationChar || result.length > 1) {
							foundType = 3
							resultStartPosition = a
							resultLength = result.length + 1
							break
						}
					}
				}

				result.insert(0, ch)
			}
		}

		if (foundType == -1) {
			delegate?.needChangePanelVisibility(false)
			return
		}

		if (foundType == 0) {
			val users = ArrayList<Long>()

			for (a in 0 until min(100, (messageObjects?.size ?: 0))) {
				val fromId = messageObjects?.get(a)?.fromChatId ?: 0

				if (fromId > 0 && !users.contains(fromId)) {
					users.add(fromId)
				}
			}

			val usernameString = result.toString().lowercase()
			val hasSpace = usernameString.indexOf(' ') >= 0
			val newResult = ArrayList<TLObject>()
			val newResultsHashMap = LongSparseArray<User>()
			val newMap = LongSparseArray<TLObject>()
			val inlineBots = MediaDataController.getInstance(currentAccount).inlineBots

			if (!usernameOnly && needBotContext && dogPosition == 0 && inlineBots.isNotEmpty()) {
				var count = 0

				for (a in inlineBots.indices) {
					val user = messagesController.getUser(inlineBots[a].peer.user_id) ?: continue

					if (!user.username.isNullOrEmpty() && (usernameString.isEmpty() || user.username?.lowercase()?.startsWith(usernameString) == true)) {
						newResult.add(user)
						newResultsHashMap.put(user.id, user)
						newMap.put(user.id, user)
						count++
					}

					if (count == 5) {
						break
					}
				}
			}

			val chat: Chat?
			val threadId: Int

			if (parentFragment != null) {
				chat = parentFragment?.currentChat
				threadId = parentFragment?.threadId ?: 0
			}
			else if (info != null) {
				chat = messagesController.getChat(info?.id)
				threadId = 0
			}
			else {
				chat = null
				threadId = 0
			}

			if (chat != null && info?.participants != null && (!ChatObject.isChannel(chat) || chat.megagroup)) {
				for (a in (if (forSearch) -1 else 0) until (info?.participants?.participants?.size ?: 0)) {
					var username: String
					var firstName: String
					var lastName: String?
					var `object`: TLObject?
					var id: Long

					if (a == -1) {
						if (usernameString.isEmpty()) {
							newResult.add(chat)
							continue
						}

						firstName = chat.title
						lastName = null
						username = chat.username
						`object` = chat
						id = -chat.id
					}
					else {
						val chatParticipant = info!!.participants.participants[a]
						val user = messagesController.getUser(chatParticipant.user_id)

						if (user == null || !usernameOnly && UserObject.isUserSelf(user) || newResultsHashMap.indexOfKey(user.id) >= 0) {
							continue
						}

						if (usernameString.isEmpty()) {
							if (!user.deleted) {
								newResult.add(user)
								continue
							}
						}

						firstName = user.first_name ?: ""
						lastName = user.last_name ?: ""
						username = user.username ?: ""
						`object` = user
						id = user.id
					}

					if (username.isNotEmpty() && username.lowercase().startsWith(usernameString) || firstName.isNotEmpty() && firstName.lowercase().startsWith(usernameString) || !lastName.isNullOrEmpty() && lastName.lowercase().startsWith(usernameString) || hasSpace && ContactsController.formatName(firstName, lastName).lowercase().startsWith(usernameString)) {
						newResult.add(`object`)
						newMap.put(id, `object`)
					}
				}
			}

			Collections.sort(newResult, object : Comparator<TLObject> {
				private fun getId(`object`: TLObject): Long {
					return if (`object` is User) {
						`object`.id
					}
					else {
						-(`object` as Chat).id
					}
				}

				override fun compare(lhs: TLObject, rhs: TLObject): Int {
					val id1 = getId(lhs)
					val id2 = getId(rhs)

					if (newMap.indexOfKey(id1) >= 0 && newMap.indexOfKey(id2) >= 0) {
						return 0
					}
					else if (newMap.indexOfKey(id1) >= 0) {
						return -1
					}
					else if (newMap.indexOfKey(id2) >= 0) {
						return 1
					}

					val lhsNum = users.indexOf(id1)
					val rhsNum = users.indexOf(id2)

					if (lhsNum != -1 && rhsNum != -1) {
						return if (lhsNum < rhsNum) -1 else if (lhsNum == rhsNum) 0 else 1
					}
					else if (lhsNum != -1 && rhsNum == -1) {
						return -1
					}
					else if (lhsNum == -1 && rhsNum != -1) {
						return 1
					}

					return 0
				}
			})

			searchResultHashtags = null
			stickers = null
			searchResultCommands = null
			searchResultCommandsHelp = null
			searchResultCommandsUsers = null
			searchResultSuggestions = null

			if (chat != null && chat.megagroup && usernameString.isNotEmpty()) {
				if (newResult.size < 5) {
					AndroidUtilities.runOnUIThread(Runnable {
						cancelDelayRunnable = null
						showUsersResult(newResult, newMap, true)
					}.also { cancelDelayRunnable = it }, 1000)
				}
				else {
					showUsersResult(newResult, newMap, true)
				}

				AndroidUtilities.runOnUIThread(object : Runnable {
					override fun run() {
						if (searchGlobalRunnable !== this) {
							return
						}

						val req = TL_channels_getParticipants()
						req.channel = MessagesController.getInputChannel(chat)
						req.limit = 20
						req.offset = 0

						val channelParticipantsMentions = TL_channelParticipantsMentions()
						channelParticipantsMentions.flags = channelParticipantsMentions.flags or 1
						channelParticipantsMentions.q = usernameString

						if (threadId != 0) {
							channelParticipantsMentions.flags = channelParticipantsMentions.flags or 2
							channelParticipantsMentions.top_msg_id = threadId
						}

						req.filter = channelParticipantsMentions

						val currentReqId = ++channelLastReqId

						channelReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
							AndroidUtilities.runOnUIThread {
								if (channelReqId != 0 && currentReqId == channelLastReqId && searchResultUsernamesMap != null && searchResultUsernames != null) {
									showUsersResult(newResult, newMap, false)

									if (error == null) {
										if (response is TL_channels_channelParticipants) {
											messagesController.putUsers(response.users, false)
											messagesController.putChats(response.chats, false)

											if (response.participants.isNotEmpty()) {

												val currentUserId = UserConfig.getInstance(currentAccount).getClientUserId()
												for (a in response.participants.indices) {
													val participant = response.participants[a]
													val peerId = MessageObject.getPeerId(participant.peer)

													if ((searchResultUsernamesMap?.indexOfKey(peerId) ?: -1) >= 0 || !isSearchingMentions && peerId == currentUserId) {
														continue
													}

													if (peerId >= 0) {
														val user = messagesController.getUser(peerId) ?: return@runOnUIThread
														searchResultUsernames?.add(user)
													}
													else {
														@Suppress("NAME_SHADOWING") val chat = messagesController.getChat(-peerId) ?: return@runOnUIThread
														searchResultUsernames?.add(chat)
													}
												}
											}
										}
									}

									notifyDataSetChanged()

									delegate?.needChangePanelVisibility(!searchResultUsernames.isNullOrEmpty())
								}
								channelReqId = 0
							}
						}
					}
				}.also { searchGlobalRunnable = it }, 200)
			}
			else {
				showUsersResult(newResult, newMap, true)
			}
		}
		else if (foundType == 1) {
			val newResult = ArrayList<String?>()
			val hashtagString = result.toString().lowercase()
			val hashtags = searchAdapterHelper.hashtags

			hashtags?.forEach { hashtagObject ->
				if (hashtagObject.hashtag?.startsWith(hashtagString) == true) {
					newResult.add(hashtagObject.hashtag)
				}
			}

			searchResultHashtags = newResult
			stickers = null
			searchResultUsernames = null
			searchResultUsernamesMap = null
			searchResultCommands = null
			searchResultCommandsHelp = null
			searchResultCommandsUsers = null
			searchResultSuggestions = null

			notifyDataSetChanged()

			delegate?.needChangePanelVisibility(!searchResultHashtags.isNullOrEmpty())
		}
		else if (foundType == 2) {
			val newResult = ArrayList<String>()
			val newResultHelp = ArrayList<String>()
			val newResultUsers = ArrayList<User?>()
			val command = result.toString().lowercase()

			botInfo?.let { botInfo ->
				for (b in 0 until botInfo.size()) {
					val info = botInfo.valueAt(b)

					for (a in info.commands.indices) {
						val botCommand = info.commands[a]

						if (botCommand.command?.startsWith(command) == true) {
							newResult.add("/" + botCommand.command)
							newResultHelp.add(botCommand.description)
							newResultUsers.add(messagesController.getUser(info.user_id))
						}
					}
				}
			}

			searchResultHashtags = null
			stickers = null
			searchResultUsernames = null
			searchResultUsernamesMap = null
			searchResultSuggestions = null
			searchResultCommands = newResult
			searchResultCommandsHelp = newResultHelp
			searchResultCommandsUsers = newResultUsers

			notifyDataSetChanged()

			delegate?.needChangePanelVisibility(newResult.isNotEmpty())
		}
		else if (foundType == 3) {
			val newLanguage = AndroidUtilities.getCurrentKeyboardLanguage()

			if (!newLanguage.contentEquals(lastSearchKeyboardLanguage)) {
				MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(newLanguage)
			}

			lastSearchKeyboardLanguage = newLanguage

			MediaDataController.getInstance(currentAccount).getEmojiSuggestions(lastSearchKeyboardLanguage, result.toString(), false, { param, _ ->
				searchResultSuggestions = param?.let { ArrayList(it) }
				searchResultHashtags = null
				stickers = null
				searchResultUsernames = null
				searchResultUsernamesMap = null
				searchResultCommands = null
				searchResultCommandsHelp = null
				searchResultCommandsUsers = null

				notifyDataSetChanged()

				delegate?.needChangePanelVisibility(!searchResultSuggestions.isNullOrEmpty())
			}, true)
		}
		else if (foundType == 4) {
			searchResultHashtags = null
			searchResultUsernames = null
			searchResultUsernamesMap = null
			searchResultSuggestions = null
			searchResultCommands = null
			searchResultCommandsHelp = null
			searchResultCommandsUsers = null
		}
	}

	fun setIsReversed(isReversed: Boolean) {
		if (this.isReversed != isReversed) {
			this.isReversed = isReversed

			val itemCount = lastItemCount

			if (itemCount > 0) {
				notifyItemChanged(0)
			}

			if (itemCount > 1) {
				notifyItemChanged(itemCount - 1)
			}
		}
	}

	private fun showUsersResult(newResult: ArrayList<TLObject>, newMap: LongSparseArray<TLObject>, notify: Boolean) {
		searchResultUsernames = newResult
		searchResultUsernamesMap = newMap

		if (cancelDelayRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(cancelDelayRunnable)
			cancelDelayRunnable = null
		}

		searchResultBotContext = null
		stickers = null

		if (notify) {
			notifyDataSetChanged()
			delegate?.needChangePanelVisibility(!searchResultUsernames.isNullOrEmpty())
		}
	}

	override fun getItemCount(): Int {
		return itemCountInternal.also { lastItemCount = it }
	}

	val itemCountInternal: Int
		get() {
			if (contextBotUser != null && !inlineMediaEnabled) {
				return 1
			}

			if (stickers != null) {
				return stickers?.size ?: 0
			}
			else if (searchResultBotContext != null) {
				return (searchResultBotContext?.size ?: 0) + if (botContextSwitch != null) 1 else 0
			}
			else if (searchResultUsernames != null) {
				return (searchResultUsernames?.size ?: 0)
			}
			else if (searchResultHashtags != null) {
				return (searchResultHashtags?.size ?: 0)
			}
			else if (searchResultCommands != null) {
				return (searchResultCommands?.size ?: 0)
			}
			else if (searchResultSuggestions != null) {
				return (searchResultSuggestions?.size ?: 0)
			}

			return 0
		}

	fun clear(safe: Boolean) {
		if (safe && (channelReqId != 0 || contextQueryRequestId != 0 || contextUsernameRequestId != 0 || lastReqId != 0)) {
			return
		}

		contextBotUser = null
		stickers?.clear()
		searchResultBotContext?.clear()
		botContextSwitch = null
		searchResultUsernames?.clear()
		searchResultHashtags?.clear()
		searchResultCommands?.clear()
		searchResultSuggestions?.clear()

		notifyDataSetChanged()
	}

	override fun getItemViewType(position: Int): Int {
		return if (stickers != null) {
			4
		}
		else if (contextBotUser != null && !inlineMediaEnabled) {
			3
		}
		else if (searchResultBotContext != null) {
			if (position == 0 && botContextSwitch != null) {
				2
			}
			else 1
		}
		else {
			0
		}
	}

	fun addHashtagsFromMessage(message: CharSequence?) {
		searchAdapterHelper.addHashtagsFromMessage(message)
	}

	fun getItemPosition(i: Int): Int {
		if (searchResultBotContext != null && botContextSwitch != null) {
			return i - 1
		}

		return i
	}

	fun getItemParent(i: Int): Any? {
		return stickers?.getOrNull(i)?.parent
	}

	fun getItem(i: Int): Any? {
		@Suppress("NAME_SHADOWING") var i = i

		if (stickers != null) {
			return stickers?.getOrNull(i)?.sticker
		}
		else if (searchResultBotContext != null) {
			if (botContextSwitch != null) {
				if (i == 0) {
					return botContextSwitch
				}
				else {
					i--
				}
			}

			return searchResultBotContext?.getOrNull(i)
		}
		else if (searchResultUsernames != null) {
			return searchResultUsernames?.getOrNull(i)
		}
		else if (searchResultHashtags != null) {
			return searchResultHashtags?.getOrNull(i)
		}
		else if (searchResultSuggestions != null) {
			return searchResultSuggestions?.getOrNull(i)
		}
		else if (searchResultCommands != null) {
			val command = searchResultCommands?.getOrNull(i) ?: return null

			return if (searchResultCommandsUsers != null && (botsCount != 1 || info is TL_channelFull)) {
				val user = searchResultCommandsUsers?.getOrNull(i)

				if (user != null) {
					String.format("%s@%s", command, user.username)
				}
				else {
					String.format("%s", command)
				}
			}
			else {
				command
			}
		}

		return null
	}

	val isLongClickEnabled: Boolean
		get() = searchResultHashtags != null || searchResultCommands != null

	val isBotCommands: Boolean
		get() = searchResultCommands != null

	fun isStickers(): Boolean {
		return stickers != null
	}

	val isBotContext: Boolean
		get() = searchResultBotContext != null

	val isBannedInline: Boolean
		get() = contextBotUser != null && !inlineMediaEnabled

	val isMediaLayout: Boolean
		get() = contextMedia || stickers != null

	override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
		return (contextBotUser == null || inlineMediaEnabled) && stickers == null
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val view: View

		when (viewType) {
			0 -> {
				view = MentionCell(mContext)
			}

			1 -> {
				view = ContextLinkCell(mContext)

				view.setDelegate {
					delegate?.onContextClick(it.result)
				}
			}

			2 -> {
				view = BotSwitchCell(mContext)
			}

			3 -> {
				val textView = TextView(mContext)
				textView.setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
				textView.setTextColor(mContext.getColor(R.color.dark_gray))
				view = textView
			}

			4 -> {
				view = StickerCell(mContext)
			}

			else -> {
				view = StickerCell(mContext)
			}
		}

		return RecyclerListView.Holder(view)
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		@Suppress("NAME_SHADOWING") var position = position
		val type = holder.itemViewType

		if (type == 4) {
			val stickerCell = holder.itemView as StickerCell
			val result = stickers?.getOrNull(position)
			stickerCell.setSticker(result?.sticker, result?.parent)
			stickerCell.isClearsInputField = true
		}
		else if (type == 3) {
			val textView = holder.itemView as TextView
			val chat = parentFragment?.currentChat

			if (chat != null) {
				if (!ChatObject.hasAdminRights(chat) && chat.default_banned_rights != null && chat.default_banned_rights.send_inline) {
					textView.text = mContext.getString(R.string.GlobalAttachInlineRestricted)
				}
				else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
					textView.text = mContext.getString(R.string.AttachInlineRestrictedForever)
				}
				else {
					textView.text = LocaleController.formatString("AttachInlineRestricted", R.string.AttachInlineRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date.toLong()))
				}
			}
		}
		else if (searchResultBotContext != null) {
			val hasTop = botContextSwitch != null

			if (holder.itemViewType == 2) {
				if (hasTop) {
					(holder.itemView as BotSwitchCell).setText(botContextSwitch?.text)
				}
			}
			else {
				if (hasTop) {
					position--
				}

				(holder.itemView as ContextLinkCell).setLink(searchResultBotContext!![position], contextBotUser, contextMedia, position != searchResultBotContext!!.size - 1, hasTop && position == 0, "gif" == searchingContextUsername)
			}
		}
		else {
			if (searchResultUsernames != null) {
				val `object` = searchResultUsernames!![position]

				if (`object` is User) {
					(holder.itemView as MentionCell).setUser(`object`)
				}
				else if (`object` is Chat) {
					(holder.itemView as MentionCell).setChat(`object`)
				}
			}
			else if (searchResultHashtags != null) {
				(holder.itemView as MentionCell).setText(searchResultHashtags!![position])
			}
			else if (searchResultSuggestions != null) {
				(holder.itemView as MentionCell).setEmojiSuggestion(searchResultSuggestions!![position])
			}
			else if (searchResultCommands != null) {
				(holder.itemView as MentionCell).setBotCommand(searchResultCommands!![position], searchResultCommandsHelp!![position], if (searchResultCommandsUsers != null) searchResultCommandsUsers!![position] else null)
			}

			(holder.itemView as MentionCell).setDivider(useDividers && if (isReversed) position > 0 else position < itemCount - 1)
		}
	}

	fun onRequestPermissionsResultFragment(requestCode: Int, grantResults: IntArray?) {
		if (requestCode == 2) {
			if (contextBotUser?.bot_inline_geo == true) {
				if (grantResults?.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
					locationProvider.start()
				}
				else {
					onLocationUnavailable()
				}
			}
		}
	}

	fun doSomeStickersAction() {
		if (isStickers()) {
			if (mentionsStickersActionTracker == null) {
				mentionsStickersActionTracker = object : ChooseStickerActionTracker(currentAccount, dialogId, threadMessageId) {
					override fun isShown(): Boolean {
						return isStickers()
					}
				}

				mentionsStickersActionTracker?.checkVisibility()
			}

			mentionsStickersActionTracker?.doSomeAction()
		}
	}

	interface MentionsAdapterDelegate {
		fun needChangePanelVisibility(show: Boolean)
		fun onItemCountUpdate(oldCount: Int, newCount: Int)
		fun onContextSearch(searching: Boolean)
		fun onContextClick(result: BotInlineResult?)
	}

	private data class StickerResult(var sticker: TLRPC.Document, var parent: Any?)

	companion object {
		private const val PUNCTUATIONS_CHARS = " !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~\n"
	}
}
