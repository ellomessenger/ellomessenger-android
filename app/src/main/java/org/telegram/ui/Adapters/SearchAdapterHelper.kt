/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Adapters

import android.util.Pair
import androidx.collection.LongSparseArray
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.UserConfig
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.RequestDelegate
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.channelId
import org.telegram.tgnet.chatId
import org.telegram.tgnet.userId
import org.telegram.ui.Adapters.DialogsSearchAdapter.RecentSearchObject
import org.telegram.ui.ChatUsersActivity
import org.telegram.ui.Components.ShareAlert
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

class SearchAdapterHelper(private val allResultsAreGlobal: Boolean) {
	class HashtagObject {
		var hashtag: String? = null
		var date = 0
	}

	interface SearchAdapterHelperDelegate {
		fun onDataSetChanged(searchId: Int)

		fun onSetHashtags(arrayList: ArrayList<HashtagObject>?, hashMap: HashMap<String, HashtagObject>?) {
		}

		val excludeUsersIds: LongArray?
			get() = null

		val excludeUsers: LongSparseArray<User>?
			get() = null

		val excludeCallParticipants: LongSparseArray<TLRPC.TLGroupCallParticipant>?
			get() = null

		fun canApplySearchResults(searchId: Int): Boolean {
			return true
		}
	}

	private var delegate: SearchAdapterHelperDelegate? = null
	private val pendingRequestIds = ArrayList<Int>()

	var lastFoundUsername: String? = null
		private set

	val localServerSearch = ArrayList<TLObject>()
	val globalSearch = ArrayList<TLObject>()
	private val globalSearchMap = LongSparseArray<TLObject>()
	val groupSearch = ArrayList<TLObject>()
	private val groupSearchMap = LongSparseArray<TLObject>()
	private var localSearchResults: ArrayList<Any>? = null
	private var localRecentResults: ArrayList<RecentSearchObject>? = null
	private val currentAccount = UserConfig.selectedAccount

	var lastFoundChannel: String? = null
		private set

	private var allowGlobalResults = true

	var hashtags: ArrayList<HashtagObject>? = null
		private set

	private var hashtagsByText: HashMap<String, HashtagObject>? = null
	private var hashtagsLoadedFromDb = false

	class DialogSearchResult {
		var `object`: TLObject? = null
		var date = 0
		var name: CharSequence? = null
	}

	fun setAllowGlobalResults(value: Boolean) {
		allowGlobalResults = value
	}

	val isSearchInProgress: Boolean
		get() = pendingRequestIds.size > 0

	@JvmOverloads
	fun queryServerSearch(query: String?, allowUsername: Boolean, allowChats: Boolean, allowBots: Boolean, allowSelf: Boolean, canAddGroupsOnly: Boolean, channelId: Long, type: Int, searchId: Int, onEnd: Runnable? = null) {
		for (reqId in pendingRequestIds) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true)
		}

		pendingRequestIds.clear()

		if (query == null) {
			groupSearch.clear()
			groupSearchMap.clear()
			globalSearch.clear()
			globalSearchMap.clear()
			localServerSearch.clear()
			delegate?.onDataSetChanged(searchId)
			return
		}

		var hasChanged = false
		val requests = ArrayList<Pair<TLObject, RequestDelegate>>()

		if (query.isNotEmpty()) {
			if (channelId != 0L) {
				val req = TLRPC.TLChannelsGetParticipants()

				when (type) {
					ChatUsersActivity.TYPE_ADMIN -> {
						req.filter = TLRPC.TLChannelParticipantsAdmins()
					}

					ChatUsersActivity.TYPE_KICKED -> {
						req.filter = TLRPC.TLChannelParticipantsBanned()
					}

					ChatUsersActivity.TYPE_BANNED -> {
						req.filter = TLRPC.TLChannelParticipantsKicked()
					}

					else -> {
						req.filter = TLRPC.TLChannelParticipantsSearch()
					}
				}

				req.filter?.q = query
				req.limit = 50
				req.offset = 0
				req.channel = MessagesController.getInstance(currentAccount).getInputChannel(channelId)

				requests.add(Pair(req, RequestDelegate { response, error ->
					if (error == null) {
						val res = response as TLRPC.TLChannelsChannelParticipants

						lastFoundChannel = query.lowercase()

						MessagesController.getInstance(currentAccount).putUsers(res.users, false)
						MessagesController.getInstance(currentAccount).putChats(res.chats, false)

						groupSearch.clear()
						groupSearchMap.clear()
						groupSearch.addAll(res.participants)

						val currentUserId = UserConfig.getInstance(currentAccount).getClientUserId()

						var a = 0
						val n = res.participants.size

						while (a < n) {
							val participant = res.participants[a]

							var peerId = MessageObject.getPeerId(participant.peer)

							if (peerId == 0L) {
								peerId = participant.userId
							}

							if (!allowSelf && peerId == currentUserId) {
								groupSearch.remove(participant)
								a++
								continue
							}

							groupSearchMap.put(peerId, participant)

							a++
						}
					}
				}))
			}
			else {
				lastFoundChannel = query.lowercase()
			}
		}
		else {
			groupSearch.clear()
			groupSearchMap.clear()
			hasChanged = true
		}

		if (allowUsername) {
			if (query.isNotEmpty()) {
				val req = TLRPC.TLContactsSearch()
				req.q = query
				req.limit = 20

				requests.add(Pair(req, RequestDelegate { response, error ->
					if (delegate?.canApplySearchResults(searchId) == true) {
						if (error == null) {
							val res = response as TLRPC.TLContactsFound
							globalSearch.clear()
							globalSearchMap.clear()
							localServerSearch.clear()

							MessagesController.getInstance(currentAccount).putChats(res.chats, false)
							MessagesController.getInstance(currentAccount).putUsers(res.users, false)

							MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true)

							val chatsMap = LongSparseArray<TLRPC.Chat>()
							val usersMap = LongSparseArray<User>()

							for (a in res.chats.indices) {
								val chat = res.chats[a]
								chatsMap.put(chat.id, chat)
							}

							for (a in res.users.indices) {
								val user = res.users[a]
								usersMap.put(user.id, user)
							}

							for (b in 0..1) {
								val arrayList = if (b == 0) {
									if (!allResultsAreGlobal) {
										continue
									}

									res.myResults
								}
								else {
									res.results
								}

								for (a in arrayList.indices) {
									val peer = arrayList[a]
									var user: User? = null
									var chat: TLRPC.Chat? = null

									if (peer.userId != 0L) {
										user = usersMap[peer.userId]
									}
									else if (peer.chatId != 0L) {
										chat = chatsMap[peer.chatId]
									}
									else if (peer.channelId != 0L) {
										chat = chatsMap[peer.channelId]
									}

									if (chat != null) {
										if (!allowChats || canAddGroupsOnly && !ChatObject.canAddBotsToChat(chat) || !allowGlobalResults && ChatObject.isNotInChat(chat)) {
											continue
										}

										globalSearch.add(chat)
										globalSearchMap.put(-chat.id, chat)
									}
									else if (user != null && user is TLRPC.TLUser) {
										if (canAddGroupsOnly || !allowBots && user.bot || !allowSelf && user.isSelf || !allowGlobalResults && b == 1 && !user.contact) {
											continue
										}

										globalSearch.add(user)
										globalSearchMap.put(user.id, user)
									}
								}
							}

							if (!allResultsAreGlobal) {
								for (a in res.myResults.indices) {
									val peer = res.myResults[a]
									var user: User? = null
									var chat: TLRPC.Chat? = null

									if (peer.userId != 0L) {
										user = usersMap[peer.userId]
									}
									else if (peer.chatId != 0L) {
										chat = chatsMap[peer.chatId]
									}
									else if (peer.channelId != 0L) {
										chat = chatsMap[peer.channelId]
									}

									if (chat != null) {
										if (!allowChats || canAddGroupsOnly && !ChatObject.canAddBotsToChat(chat)) {
											continue
										}

										localServerSearch.add(chat)
										globalSearchMap.put(-chat.id, chat)
									}
									else if (user != null && user is TLRPC.TLUser) {
										if (canAddGroupsOnly || !allowBots && user.bot || !allowSelf && user.isSelf) {
											continue
										}

										localServerSearch.add(user)
										globalSearchMap.put(user.id, user)
									}
								}
							}

							lastFoundUsername = query.lowercase()
						}
					}
				}))
			}
			else {
				globalSearch.clear()
				globalSearchMap.clear()
				localServerSearch.clear()
				hasChanged = false
			}
		}

		if (hasChanged) {
			delegate?.onDataSetChanged(searchId)
		}

		val gotResponses = AtomicInteger(0)
		val responses = ArrayList<Pair<TLObject, TLRPC.TLError>?>()

		for (i in requests.indices) {
			val r = requests[i]
			val req = r.first

			responses.add(null)

			val reqId = AtomicInteger()

			reqId.set(ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					responses[i] = Pair(response, error)

					val reqIdValue = reqId.get()

					if (!pendingRequestIds.contains(reqIdValue)) {
						return@runOnUIThread
					}

					pendingRequestIds.remove(reqIdValue)

					if (gotResponses.incrementAndGet() == requests.size) {
						for (j in requests.indices) {
							val callback = requests[j].second
							val res = responses[j] ?: continue
							callback.run(res.first, res.second)
						}

						removeGroupSearchFromGlobal()

						if (localSearchResults != null) {
							mergeResults(localSearchResults, localRecentResults)
						}

						mergeExcludeResults()

						delegate?.onDataSetChanged(searchId)

						onEnd?.run()
					}
				}
			})

			pendingRequestIds.add(reqId.get())
		}
	}

	private fun removeGroupSearchFromGlobal() {
		if (globalSearchMap.size() == 0) {
			return
		}

		var a = 0
		val n = groupSearchMap.size()

		while (a < n) {
			val uid = groupSearchMap.keyAt(a)
			val u = globalSearchMap[uid] as? User

			if (u != null) {
				globalSearch.remove(u)
				localServerSearch.remove(u)
				globalSearchMap.remove(u.id)
			}

			a++
		}
	}

	fun clear() {
		globalSearch.clear()
		globalSearchMap.clear()
		localServerSearch.clear()
	}

	fun unloadRecentHashtags() {
		hashtagsLoadedFromDb = false
	}

	fun loadRecentHashtags(): Boolean {
		if (hashtagsLoadedFromDb) {
			return true
		}

		MessagesStorage.getInstance(currentAccount).storageQueue.postRunnable {
			try {
				val cursor = MessagesStorage.getInstance(currentAccount).database.queryFinalized("SELECT id, date FROM hashtag_recent_v2 WHERE 1")
				val arrayList = ArrayList<HashtagObject>()
				val hashMap = HashMap<String, HashtagObject>()

				while (cursor.next()) {
					val hashtagObject = HashtagObject()
					hashtagObject.hashtag = cursor.stringValue(0)
					hashtagObject.date = cursor.intValue(1)

					arrayList.add(hashtagObject)

					hashtagObject.hashtag?.let {
						hashMap[it] = hashtagObject
					}
				}

				cursor.dispose()

				arrayList.sortWith { lhs, rhs ->
					rhs.date.compareTo(lhs.date)
				}

				AndroidUtilities.runOnUIThread {
					setHashtags(arrayList, hashMap)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		return false
	}

	fun addGroupMembers(participants: ArrayList<TLObject>) {
		groupSearch.clear()
		groupSearch.addAll(participants)

		var a = 0
		val n = participants.size

		while (a < n) {
			val `object` = participants[a]

			if (`object` is TLRPC.ChatParticipant) {
				groupSearchMap.put(`object`.userId, `object`)
			}
			else if (`object` is TLRPC.ChannelParticipant) {
				groupSearchMap.put(`object`.userId, `object`)
			}

			a++
		}

		removeGroupSearchFromGlobal()
	}

	@JvmOverloads
	fun mergeResults(localResults: ArrayList<Any>?, recentResults: ArrayList<RecentSearchObject>? = null) {
		localSearchResults = localResults
		localRecentResults = recentResults

		if (globalSearchMap.size() == 0 || localResults == null && recentResults == null) {
			return
		}

		val localResultsCount = localResults?.size ?: 0
		val recentResultsCount = recentResults?.size ?: 0
		val count = localResultsCount + recentResultsCount

		for (a in 0 until count) {
			var obj: Any? = if (a < localResultsCount) localResults?.get(a) else recentResults?.get(a - localResultsCount)

			if (obj is RecentSearchObject) {
				obj = obj.`object`
			}

			if (obj is ShareAlert.DialogSearchResult) {
				obj = obj.`object`
			}

			if (obj is User) {
				val user = obj
				val u = globalSearchMap[user.id] as? User

				if (u != null) {
					globalSearch.remove(u)
					localServerSearch.remove(u)
					globalSearchMap.remove(u.id)
				}

				val participant = groupSearchMap[user.id]

				if (participant != null) {
					groupSearch.remove(participant)
					groupSearchMap.remove(user.id)
				}
			}
			else if (obj is TLRPC.Chat) {
				val c = globalSearchMap[-obj.id] as? TLRPC.Chat

				if (c != null) {
					globalSearch.remove(c)
					localServerSearch.remove(c)
					globalSearchMap.remove(-c.id)
				}
			}
		}
	}

	private fun mergeExcludeResults() {
		val delegate = delegate ?: return
		val ignoreUsers = delegate.excludeUsers

		if (ignoreUsers != null) {
			var a = 0
			val size = ignoreUsers.size()

			while (a < size) {
				val u = globalSearchMap[ignoreUsers.keyAt(a)] as? User

				if (u != null) {
					globalSearch.remove(u)
					localServerSearch.remove(u)
					globalSearchMap.remove(u.id)
				}

				a++
			}
		}

		val ignoreUsersIds = delegate.excludeUsersIds

		if (ignoreUsersIds != null) {
			for (id in ignoreUsersIds) {
				val u = globalSearchMap[id] as? User

				if (u != null) {
					globalSearch.remove(u)
					localServerSearch.remove(u)
					globalSearchMap.remove(u.id)
				}

				localSearchResults?.removeAll { it is User && it.id == id }
			}
		}

		val ignoreParticipants = delegate.excludeCallParticipants

		if (ignoreParticipants != null) {
			var a = 0
			val size = ignoreParticipants.size()

			while (a < size) {
				val u = globalSearchMap[ignoreParticipants.keyAt(a)] as? User

				if (u != null) {
					globalSearch.remove(u)
					localServerSearch.remove(u)
					globalSearchMap.remove(u.id)
				}

				a++
			}
		}
	}

	fun setDelegate(searchAdapterHelperDelegate: SearchAdapterHelperDelegate?) {
		delegate = searchAdapterHelperDelegate
	}

	fun addHashtagsFromMessage(message: CharSequence?) {
		if (message == null) {
			return
		}

		var changed = false
		val pattern = Pattern.compile("(^|\\s)#\\D[\\w@.]+")
		val matcher = pattern.matcher(message)

		while (matcher.find()) {
			var start = matcher.start()
			val end = matcher.end()

			if (message[start] != '@' && message[start] != '#') {
				start++
			}

			val hashtag = message.subSequence(start, end).toString()

			if (hashtagsByText == null) {
				hashtagsByText = HashMap()
				hashtags = ArrayList()
			}

			var hashtagObject = hashtagsByText?.get(hashtag)

			if (hashtagObject == null) {
				hashtagObject = HashtagObject()
				hashtagObject.hashtag = hashtag
				hashtagsByText?.put(hashtag, hashtagObject)
			}
			else {
				hashtags?.remove(hashtagObject)
			}

			hashtagObject.date = (System.currentTimeMillis() / 1000).toInt()

			hashtags?.add(0, hashtagObject)

			changed = true
		}

		if (changed) {
			hashtags?.let {
				putRecentHashtags(it)
			}
		}
	}

	private fun putRecentHashtags(arrayList: ArrayList<HashtagObject>) {
		MessagesStorage.getInstance(currentAccount).storageQueue.postRunnable {
			try {
				MessagesStorage.getInstance(currentAccount).database.beginTransaction()

				var state = MessagesStorage.getInstance(currentAccount).database.executeFast("REPLACE INTO hashtag_recent_v2 VALUES(?, ?)")

				for (a in arrayList.indices) {
					if (a == 100) {
						break
					}

					val hashtagObject = arrayList[a]

					state.requery()
					state.bindString(1, hashtagObject.hashtag)
					state.bindInteger(2, hashtagObject.date)
					state.step()
				}

				state.dispose()

				if (arrayList.size > 100) {
					state = MessagesStorage.getInstance(currentAccount).database.executeFast("DELETE FROM hashtag_recent_v2 WHERE id = ?")

					for (a in 100 until arrayList.size) {
						state.requery()
						state.bindString(1, arrayList[a].hashtag)
						state.step()
					}

					state.dispose()
				}

				MessagesStorage.getInstance(currentAccount).database.commitTransaction()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun removeUserId(userId: Long) {
		var `object`: Any? = globalSearchMap[userId]

		if (`object` != null) {
			globalSearch.remove(`object`)
		}

		`object` = groupSearchMap[userId]

		if (`object` != null) {
			groupSearch.remove(`object`)
		}
	}

	fun clearRecentHashtags() {
		hashtags = ArrayList()
		hashtagsByText = HashMap()

		MessagesStorage.getInstance(currentAccount).storageQueue.postRunnable {
			try {
				MessagesStorage.getInstance(currentAccount).database.executeFast("DELETE FROM hashtag_recent_v2 WHERE 1").stepThis().dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun setHashtags(arrayList: ArrayList<HashtagObject>?, hashMap: HashMap<String, HashtagObject>?) {
		hashtags = arrayList
		hashtagsByText = hashMap
		hashtagsLoadedFromDb = true
		delegate?.onSetHashtags(arrayList, hashMap)
	}
}
