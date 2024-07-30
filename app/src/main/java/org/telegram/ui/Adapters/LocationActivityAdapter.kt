/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Adapters

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocationController
import org.telegram.messenger.LocationController.Companion.fetchLocationAddress
import org.telegram.messenger.LocationController.LocationFetchCallback
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC.TL_channelLocation
import org.telegram.tgnet.TLRPC.TL_geoPoint
import org.telegram.tgnet.TLRPC.TL_messageMediaVenue
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.LocationCell
import org.telegram.ui.Cells.LocationDirectionCell
import org.telegram.ui.Cells.LocationLoadingCell
import org.telegram.ui.Cells.LocationPoweredCell
import org.telegram.ui.Cells.SendLocationCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.SharingLiveLocationCell
import org.telegram.ui.Components.CombinedDrawable
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.LocationActivity
import java.util.Locale

open class LocationActivityAdapter(private val mContext: Context, private val locationType: Int, private val dialogId: Long, private val needEmptyView: Boolean) : BaseLocationAdapter(), LocationFetchCallback {
	private val currentAccount = UserConfig.selectedAccount
	private val globalGradientView = FlickerLoadingView(mContext)
	private var overScrollHeight = 0
	private var sendLocationCell: SendLocationCell? = null
	private var gpsLocation: Location? = null
	private var customLocation: Location? = null
	private var addressName: String? = null
	private var previousFetchedLocation: Location? = null
	private var shareLiveLocationPosition = -1
	private var currentMessageObject: MessageObject? = null
	private var chatLocation: TL_channelLocation? = null
	private var currentLiveLocations = ArrayList<LocationActivity.LiveLocation>()
	private var fetchingLocation = false
	private var updateRunnable: Runnable? = null
	private var myLocationDenied = false
	private var emptyCell: FrameLayout? = null

	init {
		globalGradientView.setIsSingleCell(true)
	}

	fun setMyLocationDenied(myLocationDenied: Boolean) {
		if (this.myLocationDenied == myLocationDenied) {
			return
		}

		this.myLocationDenied = myLocationDenied

		notifyDataSetChanged()
	}

	fun setOverScrollHeight(value: Int) {
		overScrollHeight = value

		if (emptyCell != null) {
			var lp = emptyCell?.layoutParams as? RecyclerView.LayoutParams

			if (lp == null) {
				lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overScrollHeight)
			}
			else {
				lp.height = overScrollHeight
			}

			emptyCell?.layoutParams = lp
			emptyCell?.forceLayout()
		}
	}

	fun setUpdateRunnable(runnable: Runnable?) {
		updateRunnable = runnable
	}

	fun setGpsLocation(location: Location?) {
		val notSet = gpsLocation == null

		gpsLocation = location

		if (customLocation == null) {
			fetchLocationAddress()
		}

		if (notSet && shareLiveLocationPosition > 0) {
			notifyItemChanged(shareLiveLocationPosition)
		}

		if (currentMessageObject != null) {
			notifyItemChanged(1, Any())
			updateLiveLocations()
		}
		else if (locationType != 2) {
			updateCell()
		}
		else {
			updateLiveLocations()
		}
	}

	fun updateLiveLocationCell() {
		if (shareLiveLocationPosition > 0) {
			notifyItemChanged(shareLiveLocationPosition)
		}
	}

	fun updateLiveLocations() {
		if (currentLiveLocations.isNotEmpty()) {
			notifyItemRangeChanged(2, currentLiveLocations.size, Any())
		}
	}

	fun setCustomLocation(location: Location?) {
		customLocation = location
		fetchLocationAddress()
		updateCell()
	}

	fun setLiveLocations(liveLocations: ArrayList<LocationActivity.LiveLocation>?) {
		currentLiveLocations = ArrayList(liveLocations ?: emptyList())

		val uid = UserConfig.getInstance(currentAccount).getClientUserId()

		for (a in currentLiveLocations.indices) {
			if (currentLiveLocations[a].id == uid || currentLiveLocations[a].`object`.out) {
				currentLiveLocations.removeAt(a)
				break
			}
		}

		notifyDataSetChanged()
	}

	fun setMessageObject(messageObject: MessageObject?) {
		currentMessageObject = messageObject
		notifyDataSetChanged()
	}

	fun setChatLocation(location: TL_channelLocation?) {
		chatLocation = location
	}

	private fun updateCell() {
		val sendLocationCell = sendLocationCell ?: return
		if (locationType == LocationActivity.LOCATION_TYPE_GROUP || customLocation != null) {
			var address: String? = ""

			if (!addressName.isNullOrEmpty()) {
				address = addressName
			}
			else if (customLocation == null && gpsLocation == null || fetchingLocation) {
				address = mContext.getString(R.string.Loading)
			}
			else if (customLocation != null) {
				address = String.format(Locale.US, "(%f,%f)", customLocation!!.latitude, customLocation!!.longitude)
			}
			else if (gpsLocation != null) {
				address = String.format(Locale.US, "(%f,%f)", gpsLocation!!.latitude, gpsLocation!!.longitude)
			}
			else if (!myLocationDenied) {
				address = mContext.getString(R.string.Loading)
			}

			if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
				sendLocationCell.setText(mContext.getString(R.string.ChatSetThisLocation), address)
			}
			else {
				sendLocationCell.setText(mContext.getString(R.string.SendSelectedLocation), address)
			}

			sendLocationCell.setHasLocation(true)
		}
		else {
			if (gpsLocation != null) {
				sendLocationCell.setText(mContext.getString(R.string.SendLocation), LocaleController.formatString("AccurateTo", R.string.AccurateTo, LocaleController.formatPluralString("Meters", gpsLocation!!.accuracy.toInt())))
				sendLocationCell.setHasLocation(true)
			}
			else {
				sendLocationCell.setText(mContext.getString(R.string.SendLocation), if (myLocationDenied) "" else mContext.getString(R.string.Loading))
				sendLocationCell.setHasLocation(!myLocationDenied)
			}
		}
	}

	override fun onLocationAddressAvailable(address: String?, displayAddress: String?, location: Location?) {
		fetchingLocation = false
		previousFetchedLocation = location
		addressName = address
		updateCell()
	}

	protected open fun onDirectionClick() {
		// stub
	}

	fun fetchLocationAddress() {
		if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
			val location = customLocation ?: gpsLocation ?: return

			if (previousFetchedLocation == null || ((previousFetchedLocation?.distanceTo(location) ?: 0f) > 100)) {
				addressName = null
			}

			fetchingLocation = true

			updateCell()

			fetchLocationAddress(location, this)
		}
		else {
			val location = customLocation ?: return

			if (previousFetchedLocation == null || (previousFetchedLocation?.distanceTo(location) ?: 0f) > 20) {
				addressName = null
			}

			fetchingLocation = true

			updateCell()
			fetchLocationAddress(location, this)
		}
	}

	override fun getItemCount(): Int {
		return if (locationType == LocationActivity.LOCATION_TYPE_LIVE_VIEW) {
			2
		}
		else if (locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW) {
			2
		}
		else if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
			2
		}
		else if (currentMessageObject != null) {
			2 + if (currentLiveLocations.isEmpty()) 1 else currentLiveLocations.size + 3
		}
		else if (locationType == 2) {
			2 + currentLiveLocations.size
		}
		else {
			if (searching || !searched || places.isEmpty()) {
				(if (locationType != LocationActivity.LOCATION_TYPE_SEND) 6 else 5) + (if (!myLocationDenied && (searching || !searched)) 2 else 0) + (if (needEmptyView) 1 else 0) - if (myLocationDenied) 2 else 0
			}
			else (if (locationType == LocationActivity.LOCATION_TYPE_SEND_WITH_LIVE) 6 else 5) + places.size + if (needEmptyView) 1 else 0
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val view: View

		when (viewType) {
			0 -> {
				view = FrameLayout(mContext).also {
					it.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overScrollHeight)
					emptyCell = it
				}
			}

			1 -> {
				view = SendLocationCell(mContext, false)
			}

			2 -> {
				view = HeaderCell(mContext)
			}

			3 -> {
				val locationCell = LocationCell(mContext, false)
				view = locationCell
			}

			4 -> {
				view = LocationLoadingCell(mContext)
			}

			5 -> {
				view = LocationPoweredCell(mContext)
			}

			6 -> {
				val cell = SendLocationCell(mContext, true)
				cell.setDialogId(dialogId)
				view = cell
			}

			7 -> {
				view = SharingLiveLocationCell(mContext, true, if (locationType == LocationActivity.LOCATION_TYPE_GROUP || locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW) 16 else 54)
			}

			8 -> {
				val cell = LocationDirectionCell(mContext)

				cell.setOnButtonClick {
					onDirectionClick()
				}

				view = cell
			}

			9 -> {
				view = ShadowSectionCell(mContext)
				val drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)
				val combinedDrawable = CombinedDrawable(ColorDrawable(ResourcesCompat.getColor(mContext.resources, R.color.light_background, null)), drawable)
				combinedDrawable.setFullSize(true)
				view.setBackground(combinedDrawable)
			}

			10 -> {
				view = View(mContext)
			}

			else -> {
				view = View(mContext)
			}
		}
		return RecyclerListView.Holder(view)
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		@Suppress("NAME_SHADOWING") var position = position

		when (holder.itemViewType) {
			0 -> {
				var lp = holder.itemView.layoutParams as? RecyclerView.LayoutParams

				if (lp == null) {
					lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overScrollHeight)
				}
				else {
					lp.height = overScrollHeight
				}

				holder.itemView.layoutParams = lp
			}

			1 -> {
				sendLocationCell = holder.itemView as SendLocationCell
				updateCell()
			}

			2 -> {
				val cell = holder.itemView as HeaderCell

				if (currentMessageObject != null) {
					cell.setText(mContext.getString(R.string.LiveLocations))
				}
				else {
					cell.setText(mContext.getString(R.string.NearbyVenue))
				}
			}

			3 -> {
				val cell = holder.itemView as LocationCell

				position -= if (locationType == 0) {
					4
				}
				else {
					5
				}

				val place = if (position < 0 || position >= places.size || !searched) null else places[position]
				val iconUrl = if (position < 0 || position >= iconUrls.size || !searched) null else iconUrls[position]

				cell.setLocation(place, iconUrl, position, true)
			}

			4 -> {
				(holder.itemView as LocationLoadingCell).setLoading(searching)
			}

			6 -> {
				(holder.itemView as SendLocationCell).setHasLocation(gpsLocation != null)
			}

			7 -> {
				val locationCell = holder.itemView as SharingLiveLocationCell

				if (locationType == LocationActivity.LOCATION_TYPE_LIVE_VIEW) {
					locationCell.setDialog(currentMessageObject, gpsLocation, myLocationDenied)
				}
				else if (chatLocation != null) {
					locationCell.setDialog(dialogId, chatLocation)
				}
				else if (currentMessageObject != null && position == 1) {
					locationCell.setDialog(currentMessageObject, gpsLocation, myLocationDenied)
				}
				else {
					locationCell.setDialog(currentLiveLocations[position - (if (currentMessageObject != null) 5 else 2)], gpsLocation)
				}
			}

			10 -> {
				val emptyView = holder.itemView
				emptyView.setBackgroundColor(ResourcesCompat.getColor(mContext.resources, if (myLocationDenied) R.color.light_background else R.color.background, null))
			}
		}
	}

	fun getItem(i: Int): Any? {
		if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
			return if (addressName == null) {
				null
			}
			else {
				val venue = TL_messageMediaVenue()
				venue.address = addressName
				venue.geo = TL_geoPoint()

				if (customLocation != null) {
					venue.geo.lat = customLocation!!.latitude
					venue.geo._long = customLocation!!.longitude
				}
				else if (gpsLocation != null) {
					venue.geo.lat = gpsLocation!!.latitude
					venue.geo._long = gpsLocation!!.longitude
				}

				venue
			}
		}
		else if (currentMessageObject != null) {
			if (i == 1) {
				return currentMessageObject
			}
			else if (i > 4 && i < places.size + 4) {
				return currentLiveLocations[i - 5]
			}
		}
		else if (locationType == 2) {
			return if (i >= 2) {
				currentLiveLocations[i - 2]
			}
			else {
				null
			}
		}
		else if (locationType == 1) {
			if (i > 4 && i < places.size + 5) {
				return places[i - 5]
			}
		}
		else {
			if (i > 3 && i < places.size + 4) {
				return places[i - 4]
			}
		}

		return null
	}

	override fun getItemViewType(position: Int): Int {
		if (position == 0) {
			return 0
		}

		if (locationType == LocationActivity.LOCATION_TYPE_LIVE_VIEW) {
			return 7
		}

		if (needEmptyView && position == itemCount - 1) {
			return 10
		}

		if (locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW) {
			return 7
		}

		if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
			return 1
		}

		if (currentMessageObject != null) {
			if (currentLiveLocations.isEmpty()) {
				if (position == 2) {
					return 8
				}
			}
			else {
				when (position) {
					2 -> {
						return 9
					}

					3 -> {
						return 2
					}

					4 -> {
						shareLiveLocationPosition = position
						return 6
					}
				}
			}

			return 7
		}

		if (locationType == 2) {
			return if (position == 1) {
				shareLiveLocationPosition = position
				6
			}
			else {
				7
			}
		}

		if (locationType == LocationActivity.LOCATION_TYPE_SEND_WITH_LIVE) {
			if (position == 1) {
				return 1
			}
			else if (position == 2) {
				shareLiveLocationPosition = position
				return 6
			}
			else if (position == 3) {
				return 9
			}
			else if (position == 4) {
				return 2
			}
			else if (searching || places.isEmpty() || !searched) {
				return if (position <= 4 + 3 && (searching || !searched) && !myLocationDenied) {
					3
				}
				else {
					4
				}
			}
			else if (position == places.size + 5) {
				return 5
			}
		}
		else {
			if (position == 1) {
				return 1
			}
			else if (position == 2) {
				return 9
			}
			else if (position == 3) {
				return 2
			}
			else if (searching || places.isEmpty()) {
				return if (position <= 3 + 3 && (searching || !searched) && !myLocationDenied) {
					3
				}
				else {
					4
				}
			}
			else if (position == places.size + 4) {
				return 5
			}
		}

		return 3
	}

	override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
		val viewType = holder.itemViewType

		return if (viewType == 6) {
			!(LocationController.getInstance(currentAccount).getSharingLocationInfo(dialogId) == null && gpsLocation == null)
		}
		else {
			viewType == 1 || viewType == 3 || viewType == 7
		}
	}
}
