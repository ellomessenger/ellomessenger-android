/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.LruCache
import android.util.SparseIntArray
import androidx.annotation.FloatRange
import androidx.collection.LongSparseArray
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.NativeByteBuffer
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.TL_messages_editMessage
import org.telegram.tgnet.tlrpc.User
import org.telegram.tgnet.tlrpc.messages_Messages
import kotlin.math.abs

@SuppressLint("MissingPermission")
class LocationController(instance: Int) : BaseController(instance), NotificationCenterDelegate, ILocationServiceProvider.IAPIConnectionCallbacks, ILocationServiceProvider.IAPIOnConnectionFailedListener {
	private val sharingLocationsMap = LongSparseArray<SharingLocationInfo>()
	private val sharingLocations = ArrayList<SharingLocationInfo>()
	private val lastReadLocationTime = LongSparseArray<Int>()
	private val locationManager = ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
	private val gpsLocationListener = GpsLocationListener()
	private val networkLocationListener = GpsLocationListener()
	private val passiveLocationListener = GpsLocationListener()
	private val fusedLocationListener = FusedLocationListener()
	private var lastKnownLocation: Location? = null
	private var lastLocationSendTime = 0L
	private var locationSentSinceLastMapUpdate = true
	private var lastLocationStartTime = 0L
	private var started = false
	private var lastLocationByMaps = false
	private val requests = SparseIntArray()
	private val cacheRequests = LongSparseArray<Boolean>()
	private var locationEndWatchTime = 0L
	private var shareMyCurrentLocation = false
	private var lookingForPeopleNearby = false
	private val sharingLocationsMapUI = LongSparseArray<SharingLocationInfo>()
	private var servicesAvailable: Boolean? = null
	private var wasConnectedToPlayServices = false
	private val apiClient = ApplicationLoader.locationServiceProvider.onCreateLocationServicesAPI(ApplicationLoader.applicationContext, this, this)
	private val locationRequest: ILocationServiceProvider.ILocationRequest = ApplicationLoader.locationServiceProvider.onCreateLocationRequest()

	@JvmField
	val locationsCache = LongSparseArray<ArrayList<Message>>()

	@JvmField
	val sharingLocationsUI = ArrayList<SharingLocationInfo>()

	var cachedNearbyUsers = ArrayList<TLRPC.TL_peerLocated>()
		private set

	var cachedNearbyChats = ArrayList<TLRPC.TL_peerLocated>()
		private set

	class SharingLocationInfo {
		@JvmField
		var did: Long = 0

		@JvmField
		var mid = 0

		@JvmField
		var stopTime = 0

		@JvmField
		var period = 0

		@JvmField
		var account = 0

		@JvmField
		var proximityMeters = 0
		var lastSentProximityMeters = 0

		@JvmField
		var messageObject: MessageObject? = null
	}

	private inner class GpsLocationListener : LocationListener {
		override fun onLocationChanged(location: Location) {
			val lastKnownLocation = lastKnownLocation

			if (lastKnownLocation != null && (this === networkLocationListener || this === passiveLocationListener)) {
				if (!started && location.distanceTo(lastKnownLocation) > 20) {
					setLastKnownLocation(location)
					lastLocationSendTime = SystemClock.elapsedRealtime() - BACKGROUND_UPDATE_TIME + 5000
				}
			}
			else {
				setLastKnownLocation(location)
			}
		}

		@Deprecated("Deprecated in Java")
		override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
			// stub
		}

		override fun onProviderEnabled(provider: String) {
			// stub
		}

		override fun onProviderDisabled(provider: String) {
			// stub
		}
	}

	private inner class FusedLocationListener : ILocationServiceProvider.ILocationListener {
		override fun onLocationChanged(location: Location?) {
			if (location == null) {
				return
			}

			setLastKnownLocation(location)
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.didReceiveNewMessages -> {
				val scheduled = args[2] as Boolean

				if (scheduled) {
					return
				}

				val did = args[0] as Long

				if (!isSharingLocation(did)) {
					return
				}

				val messages = locationsCache[did] ?: return
				val arr = args[1] as List<MessageObject>
				var added = false

				for (a in arr.indices) {
					val messageObject = arr[a]

					if (messageObject.isLiveLocation) {
						added = true

						var replaced = false

						for (b in messages.indices) {
							if (MessageObject.getFromChatId(messages[b]) == messageObject.fromChatId) {
								replaced = true
								messages[b] = messageObject.messageOwner!!
								break
							}
						}

						if (!replaced) {
							messages.add(messageObject.messageOwner!!)
						}
					}
					else if (messageObject.messageOwner?.action is TLRPC.TL_messageActionGeoProximityReached) {
						val dialogId = messageObject.dialogId

						if (DialogObject.isUserDialog(dialogId)) {
							setProximityLocation(dialogId, 0, false)
						}
					}
				}

				if (added) {
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.liveLocationsCacheChanged, did, currentAccount)
				}
			}

			NotificationCenter.messagesDeleted -> {
				val scheduled = args[2] as Boolean

				if (scheduled) {
					return
				}

				if (sharingLocationsUI.isNotEmpty()) {
					val markAsDeletedMessages = args[0] as List<Int>
					val channelId = args[1] as Long
					var toRemove: ArrayList<Long>? = null

					for (a in sharingLocationsUI.indices) {
						val info = sharingLocationsUI[a]
						val messageChannelId = info.messageObject?.channelId ?: 0

						if (channelId != messageChannelId) {
							continue
						}

						if (markAsDeletedMessages.contains(info.mid)) {
							if (toRemove == null) {
								toRemove = ArrayList()
							}

							toRemove.add(info.did)
						}
					}

					if (toRemove != null) {
						for (a in toRemove.indices) {
							removeSharingLocation(toRemove[a])
						}
					}
				}
			}

			NotificationCenter.replaceMessagesObjects -> {
				val did = args[0] as Long

				if (!isSharingLocation(did)) {
					return
				}

				val messages = locationsCache[did] ?: return
				var updated = false
				val messageObjects = args[1] as List<MessageObject>

				for (a in messageObjects.indices) {
					val messageObject = messageObjects[a]

					for (b in messages.indices) {
						if (MessageObject.getFromChatId(messages[b]) == messageObject.fromChatId) {
							if (!messageObject.isLiveLocation) {
								messages.removeAt(b)
							}
							else {
								messages[b] = messageObject.messageOwner!!
							}

							updated = true

							break
						}
					}
				}

				if (updated) {
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.liveLocationsCacheChanged, did, currentAccount)
				}
			}
		}
	}

	override fun onConnected(bundle: Bundle?) {
		wasConnectedToPlayServices = true

		try {
			ApplicationLoader.locationServiceProvider.checkLocationSettings(locationRequest) { status ->
				when (status) {
					ILocationServiceProvider.STATUS_SUCCESS -> {
						startFusedLocationRequest(true)
					}

					ILocationServiceProvider.STATUS_RESOLUTION_REQUIRED -> Utilities.stageQueue.postRunnable {
						if (lookingForPeopleNearby || sharingLocations.isNotEmpty()) {
							AndroidUtilities.runOnUIThread {
								notificationCenter.postNotificationName(NotificationCenter.needShowPlayServicesAlert, status)
							}
						}
					}

					ILocationServiceProvider.STATUS_SETTINGS_CHANGE_UNAVAILABLE -> Utilities.stageQueue.postRunnable {
						servicesAvailable = false

						try {
							apiClient.disconnect()
							start()
						}
						catch (e: Throwable) {
							// ignored
						}
					}
				}
			}
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}
	}

	fun startFusedLocationRequest(permissionsGranted: Boolean) {
		Utilities.stageQueue.postRunnable {
			if (!permissionsGranted) {
				servicesAvailable = false
			}

			if (shareMyCurrentLocation || lookingForPeopleNearby || sharingLocations.isNotEmpty()) {
				if (permissionsGranted) {
					try {
						ApplicationLoader.locationServiceProvider.let {
							it.getLastLocation { location -> setLastKnownLocation(location) }
							it.requestLocationUpdates(locationRequest, fusedLocationListener)
						}
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}
				else {
					start()
				}
			}
		}
	}

	override fun onConnectionSuspended(i: Int) {
		// unused
	}

	override fun onConnectionFailed() {
		if (wasConnectedToPlayServices) {
			return
		}

		servicesAvailable = false

		if (started) {
			started = false
			start()
		}
	}

	private fun checkServices(): Boolean {
		return servicesAvailable ?: ApplicationLoader.locationServiceProvider.checkServices().also { servicesAvailable = it }
	}

	private fun broadcastLastKnownLocation(cancelCurrent: Boolean) {
		val lastKnownLocation = lastKnownLocation ?: return

		if (requests.size() != 0) {
			if (cancelCurrent) {
				for (a in 0 until requests.size()) {
					connectionsManager.cancelRequest(requests.keyAt(a), false)
				}
			}

			requests.clear()
		}

		if (sharingLocations.isNotEmpty()) {
			val date = connectionsManager.currentTime
			val result = FloatArray(1)

			for (a in sharingLocations.indices) {
				val info = sharingLocations[a]

				if (info.messageObject?.messageOwner?.media?.geo != null && info.lastSentProximityMeters == info.proximityMeters) {
					val messageDate = (if (info.messageObject?.messageOwner?.edit_date != 0) info.messageObject?.messageOwner?.edit_date else info.messageObject?.messageOwner?.date) ?: 0
					val point = info.messageObject!!.messageOwner?.media?.geo

					if (point != null) {
						if (abs(date - messageDate) < 10) {
							Location.distanceBetween(point.lat, point._long, lastKnownLocation.latitude, lastKnownLocation.longitude, result)

							if (result[0] < 1.0f) {
								continue
							}
						}
					}
				}

				val req = TL_messages_editMessage()
				req.peer = messagesController.getInputPeer(info.did)
				req.id = info.mid
				req.flags = req.flags or 16384
				req.media = TLRPC.TL_inputMediaGeoLive()
				req.media?.stopped = false
				req.media?.geo_point = TLRPC.TL_inputGeoPoint()
				req.media?.geo_point?.lat = AndroidUtilities.fixLocationCoordinate(lastKnownLocation.latitude)
				req.media?.geo_point?._long = AndroidUtilities.fixLocationCoordinate(lastKnownLocation.longitude)
				req.media?.geo_point?.accuracy_radius = lastKnownLocation.accuracy.toInt()

				if (req.media?.geo_point?.accuracy_radius != 0) {
					req.media?.geo_point?.flags = req.media!!.geo_point.flags or 1
				}

				if (info.lastSentProximityMeters != info.proximityMeters) {
					req.media?.proximity_notification_radius = info.proximityMeters
					req.media?.flags = req.media!!.flags or 8
				}

				req.media?.heading = getHeading(lastKnownLocation)
				req.media?.flags = req.media!!.flags or 4

				val reqId = IntArray(1)

				reqId[0] = connectionsManager.sendRequest(req) { response, error ->
					if (error != null) {
						if (error.text == "MESSAGE_ID_INVALID") {
							sharingLocations.remove(info)
							sharingLocationsMap.remove(info.did)

							saveSharingLocation(info, 1)

							requests.delete(reqId[0])

							AndroidUtilities.runOnUIThread {
								sharingLocationsUI.remove(info)
								sharingLocationsMapUI.remove(info.did)

								if (sharingLocationsUI.isEmpty()) {
									stopService()
								}

								NotificationCenter.globalInstance.postNotificationName(NotificationCenter.liveLocationsChanged)
							}
						}
						return@sendRequest
					}

					if (req.flags and 8 != 0) {
						info.lastSentProximityMeters = req.media?.proximity_notification_radius ?: 0
					}

					val updates = response as TLRPC.Updates
					var updated = false

					for (a1 in updates.updates.indices) {
						val update = updates.updates[a1]

						if (update is TLRPC.TL_updateEditMessage) {
							updated = true
							info.messageObject?.messageOwner = update.message
						}
						else if (update is TLRPC.TL_updateEditChannelMessage) {
							updated = true
							info.messageObject?.messageOwner = update.message
						}
					}

					if (updated) {
						saveSharingLocation(info, 0)
					}

					messagesController.processUpdates(updates, false)
				}

				requests.put(reqId[0], 0)
			}
		}

		if (shareMyCurrentLocation) {
			val userConfig = userConfig
			userConfig.lastMyLocationShareTime = (System.currentTimeMillis() / 1000).toInt()
			userConfig.saveConfig(false)

			val req = TLRPC.TL_contacts_getLocated()
			req.geo_point = TLRPC.TL_inputGeoPoint()
			req.geo_point.lat = lastKnownLocation.latitude
			req.geo_point._long = lastKnownLocation.longitude
			req.background = true

			connectionsManager.sendRequest(req) { _, _ -> }
		}

		connectionsManager.resumeNetworkMaybe()

		if (shouldStopGps() || shareMyCurrentLocation) {
			shareMyCurrentLocation = false

			stop(false)
		}
	}

	private fun shouldStopGps(): Boolean {
		return SystemClock.elapsedRealtime() > locationEndWatchTime
	}

	fun setNewLocationEndWatchTime() {
		if (sharingLocations.isEmpty()) {
			return
		}

		locationEndWatchTime = SystemClock.elapsedRealtime() + WATCH_LOCATION_TIMEOUT

		start()
	}

	fun update() {
		val userConfig = userConfig

		if (ApplicationLoader.isScreenOn && !ApplicationLoader.mainInterfacePaused && !shareMyCurrentLocation && userConfig.isClientActivated && userConfig.isConfigLoaded && userConfig.sharingMyLocationUntil != 0 && abs(System.currentTimeMillis() / 1000 - userConfig.lastMyLocationShareTime) >= 60 * 60) {
			shareMyCurrentLocation = true
		}

		if (sharingLocations.isNotEmpty()) {
			var a = 0

			while (a < sharingLocations.size) {
				val info = sharingLocations[a]
				val currentTime = connectionsManager.currentTime

				if (info.stopTime <= currentTime) {
					sharingLocations.removeAt(a)
					sharingLocationsMap.remove(info.did)

					saveSharingLocation(info, 1)

					AndroidUtilities.runOnUIThread {
						sharingLocationsUI.remove(info)
						sharingLocationsMapUI.remove(info.did)

						if (sharingLocationsUI.isEmpty()) {
							stopService()
						}

						NotificationCenter.globalInstance.postNotificationName(NotificationCenter.liveLocationsChanged)
					}

					a--
				}

				a++
			}
		}

		if (started) {
			val newTime = SystemClock.elapsedRealtime()

			if (lastLocationByMaps || abs(lastLocationStartTime - newTime) > LOCATION_ACQUIRE_TIME || shouldSendLocationNow()) {
				lastLocationByMaps = false
				locationSentSinceLastMapUpdate = true

				val cancelAll = SystemClock.elapsedRealtime() - lastLocationSendTime > 2 * 1000

				lastLocationStartTime = newTime
				lastLocationSendTime = SystemClock.elapsedRealtime()

				broadcastLastKnownLocation(cancelAll)
			}
		}
		else if (sharingLocations.isNotEmpty() || shareMyCurrentLocation) {
			if (shareMyCurrentLocation || abs(lastLocationSendTime - SystemClock.elapsedRealtime()) > BACKGROUND_UPDATE_TIME) {
				lastLocationStartTime = SystemClock.elapsedRealtime()
				start()
			}
		}
	}

	private fun shouldSendLocationNow(): Boolean {
		return if (!shouldStopGps()) {
			false
		}
		else {
			abs(lastLocationSendTime - SystemClock.elapsedRealtime()) >= SEND_NEW_LOCATION_TIME
		}
	}

	fun cleanup() {
		sharingLocationsUI.clear()
		sharingLocationsMapUI.clear()
		locationsCache.clear()
		cacheRequests.clear()
		cachedNearbyUsers.clear()
		cachedNearbyChats.clear()
		lastReadLocationTime.clear()

		stopService()

		Utilities.stageQueue.postRunnable {
			locationEndWatchTime = 0
			requests.clear()
			sharingLocationsMap.clear()
			sharingLocations.clear()
			setLastKnownLocation(null)
			stop(true)
		}
	}

	private fun setLastKnownLocation(location: Location?) {
		if (location != null && (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1000000000 > 60 * 5) {
			return
		}

		lastKnownLocation = location

		if (lastKnownLocation != null) {
			AndroidUtilities.runOnUIThread {
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.newLocationAvailable)
			}
		}
	}

	fun setCachedNearbyUsersAndChats(u: ArrayList<TLRPC.TL_peerLocated>?, c: ArrayList<TLRPC.TL_peerLocated>?) {
		cachedNearbyUsers = ArrayList(u ?: emptyList())
		cachedNearbyChats = ArrayList(c ?: emptyList())
	}

	fun addSharingLocation(message: Message) {
		val info = SharingLocationInfo()
		info.did = message.dialog_id
		info.mid = message.id
		info.period = message.media?.period ?: 0
		info.proximityMeters = message.media?.proximity_notification_radius ?: 0
		info.lastSentProximityMeters = info.proximityMeters
		info.account = currentAccount
		info.messageObject = MessageObject(currentAccount, message, generateLayout = false, checkMediaExists = false)
		info.stopTime = connectionsManager.currentTime + info.period

		val old = sharingLocationsMap[info.did]

		sharingLocationsMap.put(info.did, info)

		if (old != null) {
			sharingLocations.remove(old)
		}

		sharingLocations.add(info)

		saveSharingLocation(info, 0)

		lastLocationSendTime = SystemClock.elapsedRealtime() - BACKGROUND_UPDATE_TIME + 5000

		AndroidUtilities.runOnUIThread {
			if (old != null) {
				sharingLocationsUI.remove(old)
			}

			sharingLocationsUI.add(info)
			sharingLocationsMapUI.put(info.did, info)

			startService()

			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.liveLocationsChanged)
		}
	}

	fun isSharingLocation(did: Long): Boolean {
		return sharingLocationsMapUI.indexOfKey(did) >= 0
	}

	fun getSharingLocationInfo(did: Long): SharingLocationInfo? {
		return sharingLocationsMapUI[did]
	}

	fun setProximityLocation(did: Long, meters: Int, broadcast: Boolean) {
		val info = sharingLocationsMapUI[did]

		if (info != null) {
			info.proximityMeters = meters
		}

		messagesStorage.storageQueue.postRunnable {
			try {
				val state = messagesStorage.database.executeFast("UPDATE sharing_locations SET proximity = ? WHERE uid = ?")
				state.requery()
				state.bindInteger(1, meters)
				state.bindLong(2, did)
				state.step()
				state.dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		if (broadcast) {
			Utilities.stageQueue.postRunnable {
				broadcastLastKnownLocation(true)
			}
		}
	}

	private fun loadSharingLocations() {
		messagesStorage.storageQueue.postRunnable {
			val result = ArrayList<SharingLocationInfo>()
			val users = ArrayList<User>()
			val chats = ArrayList<TLRPC.Chat>()

			try {
				val usersToLoad = ArrayList<Long?>()
				val chatsToLoad = ArrayList<Long?>()
				val cursor = messagesStorage.database.queryFinalized("SELECT uid, mid, date, period, message, proximity FROM sharing_locations WHERE 1")

				while (cursor.next()) {
					val info = SharingLocationInfo()
					info.did = cursor.longValue(0)
					info.mid = cursor.intValue(1)
					info.stopTime = cursor.intValue(2)
					info.period = cursor.intValue(3)
					info.proximityMeters = cursor.intValue(5)
					info.account = currentAccount

					val data = cursor.byteBufferValue(4)

					if (data != null) {
						val msg = Message.TLdeserialize(data, data.readInt32(false), false)

						if (msg != null) {
							info.messageObject = MessageObject(currentAccount, msg, generateLayout = false, checkMediaExists = false)
						}

						MessagesStorage.addUsersAndChatsFromMessage(info.messageObject?.messageOwner, usersToLoad, chatsToLoad, null)

						data.reuse()
					}

					result.add(info)

					if (DialogObject.isChatDialog(info.did)) {
						if (!chatsToLoad.contains(-info.did)) {
							chatsToLoad.add(-info.did)
						}
					}
					else if (DialogObject.isUserDialog(info.did)) {
						if (!usersToLoad.contains(info.did)) {
							usersToLoad.add(info.did)
						}
					}
				}

				cursor.dispose()

				if (chatsToLoad.isNotEmpty()) {
					messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), chats)
				}

				if (usersToLoad.isNotEmpty()) {
					messagesStorage.getUsersInternal(TextUtils.join(",", usersToLoad), users)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			if (result.isNotEmpty()) {
				AndroidUtilities.runOnUIThread {
					messagesController.putUsers(users, true)
					messagesController.putChats(chats, true)

					Utilities.stageQueue.postRunnable {
						sharingLocations.addAll(result)

						for (a in sharingLocations.indices) {
							val info = sharingLocations[a]
							sharingLocationsMap.put(info.did, info)
						}

						AndroidUtilities.runOnUIThread {
							sharingLocationsUI.addAll(result)

							for (a in result.indices) {
								val info = result[a]
								sharingLocationsMapUI.put(info.did, info)
							}

							startService()

							NotificationCenter.globalInstance.postNotificationName(NotificationCenter.liveLocationsChanged)
						}
					}
				}
			}
		}
	}

	private fun saveSharingLocation(info: SharingLocationInfo?, remove: Int) {
		messagesStorage.storageQueue.postRunnable {
			try {
				when (remove) {
					2 -> {
						messagesStorage.database.executeFast("DELETE FROM sharing_locations WHERE 1").stepThis().dispose()
					}

					1 -> {
						if (info == null) {
							return@postRunnable
						}

						messagesStorage.database.executeFast("DELETE FROM sharing_locations WHERE uid = " + info.did).stepThis().dispose()
					}

					else -> {
						if (info == null) {
							return@postRunnable
						}

						val state = messagesStorage.database.executeFast("REPLACE INTO sharing_locations VALUES(?, ?, ?, ?, ?, ?)")
						state.requery()

						val data = NativeByteBuffer(info.messageObject!!.messageOwner!!.objectSize)

						info.messageObject?.messageOwner?.serializeToStream(data)

						state.bindLong(1, info.did)
						state.bindInteger(2, info.mid)
						state.bindInteger(3, info.stopTime)
						state.bindInteger(4, info.period)
						state.bindByteBuffer(5, data)
						state.bindInteger(6, info.proximityMeters)
						state.step()
						state.dispose()

						data.reuse()
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun removeSharingLocation(did: Long?) {
		if (did == null) {
			return
		}

		Utilities.stageQueue.postRunnable {
			val info = sharingLocationsMap[did]

			sharingLocationsMap.remove(did)

			if (info != null) {
				val req = TL_messages_editMessage()
				req.peer = messagesController.getInputPeer(info.did)
				req.id = info.mid
				req.flags = req.flags or 16384
				req.media = TLRPC.TL_inputMediaGeoLive()
				req.media?.stopped = true
				req.media?.geo_point = TLRPC.TL_inputGeoPointEmpty()

				connectionsManager.sendRequest(req) { response, error ->
					if (error != null) {
						return@sendRequest
					}

					messagesController.processUpdates(response as TLRPC.Updates?, false)
				}

				sharingLocations.remove(info)

				saveSharingLocation(info, 1)

				AndroidUtilities.runOnUIThread {
					sharingLocationsUI.remove(info)
					sharingLocationsMapUI.remove(info.did)

					if (sharingLocationsUI.isEmpty()) {
						stopService()
					}

					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.liveLocationsChanged)
				}

				if (sharingLocations.isEmpty()) {
					stop(true)
				}
			}
		}
	}

	private fun startService() {
		try {            /*if (Build.VERSION.SDK_INT >= 26) {
				ApplicationLoader.applicationContext.startForegroundService(new Intent(ApplicationLoader.applicationContext, LocationSharingService.class));
			} else {*/
			ApplicationLoader.applicationContext.startService(Intent(ApplicationLoader.applicationContext, LocationSharingService::class.java))
			//}
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}
	}

	private fun stopService() {
		ApplicationLoader.applicationContext.stopService(Intent(ApplicationLoader.applicationContext, LocationSharingService::class.java))
	}

	fun removeAllLocationShares() {
		Utilities.stageQueue.postRunnable {
			for (a in sharingLocations.indices) {
				val info = sharingLocations[a]

				val req = TL_messages_editMessage()
				req.peer = messagesController.getInputPeer(info.did)
				req.id = info.mid
				req.flags = req.flags or 16384
				req.media = TLRPC.TL_inputMediaGeoLive()
				req.media?.stopped = true
				req.media?.geo_point = TLRPC.TL_inputGeoPointEmpty()

				connectionsManager.sendRequest(req) { response, error ->
					if (error != null) {
						return@sendRequest
					}

					messagesController.processUpdates(response as TLRPC.Updates?, false)
				}
			}

			sharingLocations.clear()
			sharingLocationsMap.clear()

			saveSharingLocation(null, 2)

			stop(true)

			AndroidUtilities.runOnUIThread {
				sharingLocationsUI.clear()
				sharingLocationsMapUI.clear()

				stopService()

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.liveLocationsChanged)
			}
		}
	}

	fun setMapLocation(location: Location?, first: Boolean) {
		if (location == null) {
			return
		}

		lastLocationByMaps = true

		if (first || lastKnownLocation != null && lastKnownLocation!!.distanceTo(location) >= 20) {
			lastLocationSendTime = SystemClock.elapsedRealtime() - BACKGROUND_UPDATE_TIME
			locationSentSinceLastMapUpdate = false
		}
		else if (locationSentSinceLastMapUpdate) {
			lastLocationSendTime = SystemClock.elapsedRealtime() - BACKGROUND_UPDATE_TIME + FOREGROUND_UPDATE_TIME
			locationSentSinceLastMapUpdate = false
		}

		setLastKnownLocation(location)
	}

	private fun start() {
		if (started) {
			return
		}

		lastLocationStartTime = SystemClock.elapsedRealtime()
		started = true

		var ok = false

		if (checkServices()) {
			try {
				apiClient.connect()
				ok = true
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		if (!ok) {
			try {
				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0f, gpsLocationListener)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			try {
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1, 0f, networkLocationListener)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			try {
				locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1, 0f, passiveLocationListener)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			if (lastKnownLocation == null) {
				try {
					setLastKnownLocation(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER))

					if (lastKnownLocation == null) {
						setLastKnownLocation(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
	}

	private fun stop(empty: Boolean) {
		if (lookingForPeopleNearby || shareMyCurrentLocation) {
			return
		}

		started = false

		if (checkServices()) {
			try {
				ApplicationLoader.locationServiceProvider.removeLocationUpdates(fusedLocationListener)
				apiClient.disconnect()
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		locationManager.removeUpdates(gpsLocationListener)

		if (empty) {
			locationManager.removeUpdates(networkLocationListener)
			locationManager.removeUpdates(passiveLocationListener)
		}
	}

	fun startLocationLookupForPeopleNearby(stop: Boolean) {
		Utilities.stageQueue.postRunnable {
			lookingForPeopleNearby = !stop

			if (lookingForPeopleNearby) {
				start()
			}
			else if (sharingLocations.isEmpty()) {
				stop(true)
			}
		}
	}

	fun getLastKnownLocation(): Location? {
		return lastKnownLocation
	}

	fun loadLiveLocations(did: Long) {
		if (cacheRequests.indexOfKey(did) >= 0) {
			return
		}

		cacheRequests.put(did, true)

		val req = TLRPC.TL_messages_getRecentLocations()
		req.peer = messagesController.getInputPeer(did)
		req.limit = 100

		connectionsManager.sendRequest(req) { response, error ->
			if (error != null) {
				return@sendRequest
			}

			AndroidUtilities.runOnUIThread {
				cacheRequests.remove(did)

				val res = response as messages_Messages

				var a = 0

				while (a < res.messages.size) {
					if (res.messages[a].media !is TLRPC.TL_messageMediaGeoLive) {
						res.messages.removeAt(a)
						a--
					}

					a++
				}

				messagesStorage.putUsersAndChats(res.users, res.chats, true, true)

				messagesController.putUsers(res.users, false)
				messagesController.putChats(res.chats, false)

				locationsCache.put(did, res.messages)

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.liveLocationsCacheChanged, did, currentAccount)
			}
		}
	}

	fun markLiveLocationsAsRead(dialogId: Long) {
		if (DialogObject.isEncryptedDialog(dialogId)) {
			return
		}

		val messages = locationsCache[dialogId]

		if (messages.isNullOrEmpty()) {
			return
		}

		val date = lastReadLocationTime[dialogId]
		val currentDate = (SystemClock.elapsedRealtime() / 1000).toInt()

		if (date != null && date + 60 > currentDate) {
			return
		}

		lastReadLocationTime.put(dialogId, currentDate)

		val request = if (DialogObject.isChatDialog(dialogId) && ChatObject.isChannel(-dialogId, currentAccount)) {
			val req = TLRPC.TL_channels_readMessageContents()
			req.id.addAll(messages.map { it.id })
			req.channel = messagesController.getInputChannel(-dialogId)
			req
		}
		else {
			val req = TLRPC.TL_messages_readMessageContents()
			req.id.addAll(messages.map { it.id })
			req
		}

		connectionsManager.sendRequest(request) { response, _ ->
			if (response is TLRPC.TL_messages_affectedMessages) {
				messagesController.processNewDifferenceParams(-1, response.pts, -1, response.pts_count)
			}
		}
	}

	fun interface LocationFetchCallback {
		fun onLocationAddressAvailable(address: String?, displayAddress: String?, location: Location?)
	}

	fun interface CoordinatesFetchCallback {
		fun onCoordinatesAddressAvailable(address: String?, displayAddress: String?, latitude: Double, longitude: Double)
	}

	init {
		locationRequest.setPriority(ILocationServiceProvider.PRIORITY_HIGH_ACCURACY)
		locationRequest.setInterval(UPDATE_INTERVAL)
		locationRequest.setFastestInterval(FASTEST_INTERVAL)

		AndroidUtilities.runOnUIThread {
			val locationController = accountInstance.locationController

			notificationCenter.addObserver(locationController, NotificationCenter.didReceiveNewMessages)
			notificationCenter.addObserver(locationController, NotificationCenter.messagesDeleted)
			notificationCenter.addObserver(locationController, NotificationCenter.replaceMessagesObjects)
		}

		loadSharingLocations()
	}

	companion object {
		private const val UPDATE_INTERVAL: Long = 1000
		private const val FASTEST_INTERVAL: Long = 1000
		private const val BACKGROUND_UPDATE_TIME = 30 * 1000
		private const val LOCATION_ACQUIRE_TIME = 10 * 1000
		private const val FOREGROUND_UPDATE_TIME = 20 * 1000
		private const val WATCH_LOCATION_TIMEOUT = 65 * 1000
		private const val SEND_NEW_LOCATION_TIME = 2 * 1000

		private val instance = arrayOfNulls<LocationController>(UserConfig.MAX_ACCOUNT_COUNT)

		@JvmStatic
		fun getInstance(num: Int): LocationController {
			var localInstance = instance[num]

			if (localInstance == null) {
				synchronized(LocationController::class.java) {
					localInstance = instance[num]

					if (localInstance == null) {
						localInstance = LocationController(num)
						instance[num] = localInstance
					}
				}
			}

			return localInstance!!
		}

		@JvmStatic
		fun getHeading(location: Location): Int {
			val `val` = location.bearing

			return if (`val` > 0 && `val` < 1.0f) {
				if (`val` < 0.5f) {
					360
				}
				else {
					1
				}
			}
			else {
				`val`.toInt()
			}
		}

		@JvmStatic
		val locationsCount: Int
			get() {
				var count = 0

				for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
					count += getInstance(a).sharingLocationsUI.size
				}

				return count
			}

		private val callbacks = HashMap<LocationFetchCallback, Runnable>()
		private val addressCache = LruCache<Pair<Double, Double>, String>(10) // let's keep 10 addresses in cache

		/**
		 * Fetches the address of the given location
		 * @param latitude The latitude of the location (between -90 and 90)
		 * @param longitude The longitude of the location (between -180 and 180)
		 * @param callback The callback to be called when the address is fetched (called on the UI thread)
		 */
		fun fetchCoordinatesAddress(@FloatRange(from = -90.0, to = 90.0) latitude: Double, @FloatRange(from = -180.0, to = 180.0) longitude: Double, callback: CoordinatesFetchCallback) {
			val cachedAddress = addressCache.get(Pair(latitude, longitude))

			if (cachedAddress != null) {
				AndroidUtilities.runOnUIThread {
					callback.onCoordinatesAddressAvailable(cachedAddress, cachedAddress, latitude, longitude)
				}

				return
			}

			Utilities.globalQueue.postRunnable({
				var name: String?
				var displayName: String?

				try {
					val gcd = Geocoder(ApplicationLoader.applicationContext, LocaleController.getInstance().systemDefaultLocale)
					val addresses = gcd.getFromLocation(latitude, longitude, 1)

					if (!addresses.isNullOrEmpty()) {
						val address = addresses.first()
						var hasAny = false
						val nameBuilder = StringBuilder()
						val displayNameBuilder = StringBuilder()

						var arg = address.subThoroughfare

						if (!arg.isNullOrEmpty()) {
							nameBuilder.append(arg)
							hasAny = true
						}

						arg = address.thoroughfare

						if (!arg.isNullOrEmpty()) {
							if (nameBuilder.isNotEmpty()) {
								nameBuilder.append(" ")
							}

							nameBuilder.append(arg)

							hasAny = true
						}

						if (!hasAny) {
							arg = address.adminArea

							if (!arg.isNullOrEmpty()) {
								if (nameBuilder.isNotEmpty()) {
									nameBuilder.append(", ")
								}

								nameBuilder.append(arg)
							}

							arg = address.subAdminArea

							if (!arg.isNullOrEmpty()) {
								if (nameBuilder.isNotEmpty()) {
									nameBuilder.append(", ")
								}

								nameBuilder.append(arg)
							}
						}

						arg = address.locality

						if (!arg.isNullOrEmpty()) {
							if (nameBuilder.isNotEmpty()) {
								nameBuilder.append(", ")
							}

							nameBuilder.append(arg)
						}

						arg = address.countryName

						if (!arg.isNullOrEmpty()) {
							if (nameBuilder.isNotEmpty()) {
								nameBuilder.append(", ")
							}

							nameBuilder.append(arg)
						}

						arg = address.countryName

						if (!arg.isNullOrEmpty()) {
							if (displayNameBuilder.isNotEmpty()) {
								displayNameBuilder.append(", ")
							}

							displayNameBuilder.append(arg)
						}

						arg = address.locality

						if (!arg.isNullOrEmpty()) {
							if (displayNameBuilder.isNotEmpty()) {
								displayNameBuilder.append(", ")
							}

							displayNameBuilder.append(arg)
						}

						if (!hasAny) {
							arg = address.adminArea

							if (!arg.isNullOrEmpty()) {
								if (displayNameBuilder.isNotEmpty()) {
									displayNameBuilder.append(", ")
								}

								displayNameBuilder.append(arg)
							}

							arg = address.subAdminArea

							if (!arg.isNullOrEmpty()) {
								if (displayNameBuilder.isNotEmpty()) {
									displayNameBuilder.append(", ")
								}

								displayNameBuilder.append(arg)
							}
						}

						name = nameBuilder.toString()
						displayName = displayNameBuilder.toString()
					}
					else {
						displayName = null
						name = null
					}
				}
				catch (ignore: Exception) {
					displayName = null
					name = null
				}

				if (!displayName.isNullOrEmpty()) {
					addressCache.put(Pair(latitude, longitude), displayName)
				}

				AndroidUtilities.runOnUIThread {
					callback.onCoordinatesAddressAvailable(name, displayName, latitude, longitude)
				}
			}, 300)
		}

		@JvmStatic
		fun fetchLocationAddress(location: Location?, callback: LocationFetchCallback?) {
			if (callback == null) {
				return
			}

			var fetchLocationRunnable = callbacks[callback]

			if (fetchLocationRunnable != null) {
				Utilities.globalQueue.cancelRunnable(fetchLocationRunnable)
				callbacks.remove(callback)
			}

			if (location == null) {
				callback.onLocationAddressAvailable(null, null, null)
				return
			}

			Utilities.globalQueue.postRunnable(Runnable {
				var name: String
				var displayName: String

				try {
					val gcd = Geocoder(ApplicationLoader.applicationContext, LocaleController.getInstance().systemDefaultLocale)
					val addresses = gcd.getFromLocation(location.latitude, location.longitude, 1)

					if (!addresses.isNullOrEmpty()) {
						val address = addresses.first()
						var hasAny = false
						val nameBuilder = StringBuilder()
						val displayNameBuilder = StringBuilder()

						var arg = address.subThoroughfare

						if (!arg.isNullOrEmpty()) {
							nameBuilder.append(arg)
							hasAny = true
						}

						arg = address.thoroughfare

						if (!arg.isNullOrEmpty()) {
							if (nameBuilder.isNotEmpty()) {
								nameBuilder.append(" ")
							}

							nameBuilder.append(arg)

							hasAny = true
						}

						if (!hasAny) {
							arg = address.adminArea

							if (!arg.isNullOrEmpty()) {
								if (nameBuilder.isNotEmpty()) {
									nameBuilder.append(", ")
								}

								nameBuilder.append(arg)
							}

							arg = address.subAdminArea

							if (!arg.isNullOrEmpty()) {
								if (nameBuilder.isNotEmpty()) {
									nameBuilder.append(", ")
								}

								nameBuilder.append(arg)
							}
						}

						arg = address.locality

						if (!arg.isNullOrEmpty()) {
							if (nameBuilder.isNotEmpty()) {
								nameBuilder.append(", ")
							}

							nameBuilder.append(arg)
						}

						arg = address.countryName

						if (!arg.isNullOrEmpty()) {
							if (nameBuilder.isNotEmpty()) {
								nameBuilder.append(", ")
							}

							nameBuilder.append(arg)
						}

						arg = address.countryName

						if (!arg.isNullOrEmpty()) {
							if (displayNameBuilder.isNotEmpty()) {
								displayNameBuilder.append(", ")
							}

							displayNameBuilder.append(arg)
						}

						arg = address.locality

						if (!arg.isNullOrEmpty()) {
							if (displayNameBuilder.isNotEmpty()) {
								displayNameBuilder.append(", ")
							}

							displayNameBuilder.append(arg)
						}

						if (!hasAny) {
							arg = address.adminArea

							if (!arg.isNullOrEmpty()) {
								if (displayNameBuilder.isNotEmpty()) {
									displayNameBuilder.append(", ")
								}

								displayNameBuilder.append(arg)
							}

							arg = address.subAdminArea

							if (!arg.isNullOrEmpty()) {
								if (displayNameBuilder.isNotEmpty()) {
									displayNameBuilder.append(", ")
								}

								displayNameBuilder.append(arg)
							}
						}

						name = nameBuilder.toString()
						displayName = displayNameBuilder.toString()
					}
					else {
						displayName = ApplicationLoader.applicationContext.getString(R.string.unknown_address_format, location.latitude, location.longitude)
						name = displayName
					}
				}
				catch (ignore: Exception) {
					displayName = ApplicationLoader.applicationContext.getString(R.string.unknown_address_format, location.latitude, location.longitude)
					name = displayName
				}

				val nameFinal = name
				val displayNameFinal = displayName

				AndroidUtilities.runOnUIThread {
					callbacks.remove(callback)
					callback.onLocationAddressAvailable(nameFinal, displayNameFinal, location)
				}
			}.also { fetchLocationRunnable = it }, 300)

			callbacks[callback] = fetchLocationRunnable!!
		}
	}
}
