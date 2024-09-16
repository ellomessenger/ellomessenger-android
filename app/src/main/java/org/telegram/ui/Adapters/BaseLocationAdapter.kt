/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Adapters

import android.annotation.SuppressLint
import android.location.Location
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DialogObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.TL_botInlineMessageMediaVenue
import org.telegram.tgnet.TLRPC.TL_contacts_resolveUsername
import org.telegram.tgnet.TLRPC.TL_contacts_resolvedPeer
import org.telegram.tgnet.TLRPC.TL_inputGeoPoint
import org.telegram.tgnet.TLRPC.TL_inputPeerEmpty
import org.telegram.tgnet.TLRPC.TL_messageMediaVenue
import org.telegram.tgnet.TLRPC.TL_messages_getInlineBotResults
import org.telegram.tgnet.TLRPC.messages_BotResults
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import kotlin.math.max

@SuppressLint("NotifyDataSetChanged")
abstract class BaseLocationAdapter : SelectionAdapter() {
	protected var searched: Boolean = false
	protected var searching: Boolean = false
	protected val places = mutableListOf<TL_messageMediaVenue>()
	protected val iconUrls = mutableListOf<String>()
	private var lastSearchLocation: Location? = null
	private var lastSearchQuery: String? = null
	private var delegate: BaseLocationAdapterDelegate? = null
	private var searchRunnable: Runnable? = null
	private var currentRequestNum = 0
	private val currentAccount = UserConfig.selectedAccount
	private var dialogId = 0L
	private var searchingUser = false
	private var searchInProgress = false

	var lastSearchString: String? = null
		private set

	fun destroy() {
		if (currentRequestNum != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(currentRequestNum, true)
			currentRequestNum = 0
		}
	}

	fun setDelegate(did: Long, delegate: BaseLocationAdapterDelegate?) {
		dialogId = did
		this.delegate = delegate
	}

	fun searchDelayed(query: String?, coordinate: Location?) {
		if (query.isNullOrEmpty()) {
			places.clear()
			searchInProgress = false
			notifyDataSetChanged()
		}
		else {
			if (searchRunnable != null) {
				Utilities.searchQueue.cancelRunnable(searchRunnable)
				searchRunnable = null
			}

			searchInProgress = true

			Utilities.searchQueue.postRunnable(Runnable {
				AndroidUtilities.runOnUIThread {
					searchRunnable = null
					lastSearchLocation = null
					searchPlacesWithQuery(query, coordinate, true)
				}
			}.also { searchRunnable = it }, 400)
		}
	}

	private fun searchBotUser() {
		if (searchingUser) {
			return
		}

		searchingUser = true

		val req = TL_contacts_resolveUsername()
		req.username = MessagesController.getInstance(currentAccount).venueSearchBot

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
			if (response != null) {
				AndroidUtilities.runOnUIThread {
					val res = response as TL_contacts_resolvedPeer

					MessagesController.getInstance(currentAccount).putUsers(res.users, false)
					MessagesController.getInstance(currentAccount).putChats(res.chats, false)

					MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true)

					val coordinates = lastSearchLocation

					lastSearchLocation = null

					searchPlacesWithQuery(lastSearchQuery, coordinates, false)
				}
			}
		}
	}

	fun isSearching(): Boolean {
		return searchInProgress
	}

	protected open fun notifyStartSearch(wasSearching: Boolean, oldItemCount: Int, animated: Boolean) {
		if (animated) {
			if (places.isEmpty() || wasSearching) {
				if (!wasSearching) {
					val fromIndex = max(0.0, (itemCount - 4).toDouble()).toInt()
					notifyItemRangeRemoved(fromIndex, itemCount - fromIndex)
				}
			}
			else {
				val placesCount = places.size + 3
				val offset = oldItemCount - placesCount
				notifyItemInserted(offset)
				notifyItemRangeRemoved(offset, placesCount)
			}
		}
		else {
			notifyDataSetChanged()
		}
	}

	fun searchPlacesWithQuery(query: String?, coordinate: Location?, searchUser: Boolean) {
		if (coordinate == null || lastSearchLocation != null && coordinate.distanceTo(lastSearchLocation!!) < 200) {
			return
		}

		lastSearchLocation = Location(coordinate)
		lastSearchQuery = query

		if (searching) {
			searching = false

			if (currentRequestNum != 0) {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(currentRequestNum, true)
				currentRequestNum = 0
			}
		}

		searching = true
		searched = true

		val `object` = MessagesController.getInstance(currentAccount).getUserOrChat(MessagesController.getInstance(currentAccount).venueSearchBot)

		if (`object` !is User) {
			if (searchUser) {
				searchBotUser()
			}

			return
		}

		val req = TL_messages_getInlineBotResults()
		req.query = query ?: ""
		req.bot = MessagesController.getInstance(currentAccount).getInputUser(`object`)
		req.offset = ""
		req.geo_point = TL_inputGeoPoint()
		req.geo_point.lat = AndroidUtilities.fixLocationCoordinate(coordinate.latitude)
		req.geo_point._long = AndroidUtilities.fixLocationCoordinate(coordinate.longitude)
		req.flags = req.flags or 1

		if (DialogObject.isEncryptedDialog(dialogId)) {
			req.peer = TL_inputPeerEmpty()
		}
		else {
			req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId)
		}

		currentRequestNum = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is messages_BotResults) {
					currentRequestNum = 0
					searching = false
					places.clear()
					iconUrls.clear()
					searchInProgress = false
					lastSearchString = query

					var a = 0
					val size = response.results.size

					while (a < size) {
						val result = response.results[a]

						if ("venue" != result.type || result.send_message !is TL_botInlineMessageMediaVenue) {
							a++
							continue
						}

						val mediaVenue = result.send_message as TL_botInlineMessageMediaVenue

						iconUrls.add("https://ss3.4sqi.net/img/categories_v2/" + mediaVenue.venue_type + "_64.png")

						val venue = TL_messageMediaVenue()
						venue.geo = mediaVenue.geo
						venue.address = mediaVenue.address
						venue.title = mediaVenue.title
						venue.venue_type = mediaVenue.venue_type
						venue.venue_id = mediaVenue.venue_id
						venue.provider = mediaVenue.provider

						places.add(venue)

						a++
					}
				}

				delegate?.didLoadSearchResult(places)

				notifyDataSetChanged()
			}
		}

		notifyDataSetChanged()
	}

	fun interface BaseLocationAdapterDelegate {
		fun didLoadSearchResult(places: List<TL_messageMediaVenue>?)
	}
}
