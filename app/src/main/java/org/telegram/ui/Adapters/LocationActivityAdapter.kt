/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Adapters

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocationController
import org.telegram.messenger.LocationController.Companion.fetchLocationAddress
import org.telegram.messenger.LocationController.LocationFetchCallback
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC
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

@SuppressLint("NotifyDataSetChanged")
open class LocationActivityAdapter(private val context: Context, private val locationType: Int, private val dialogId: Long, private val needEmptyView: Boolean, private val showVenues: Boolean = false) : BaseLocationAdapter(), LocationFetchCallback {
	private val currentAccount = UserConfig.selectedAccount
	private val globalGradientView = FlickerLoadingView(context)
	private var overScrollHeight = 0
	private var sendLocationCell: SendLocationCell? = null
	private var gpsLocation: Location? = null
	private var customLocation: Location? = null
	private var addressName: String? = null
	private var previousFetchedLocation: Location? = null
	private var shareLiveLocationPosition = -1
	private var currentMessageObject: MessageObject? = null
	private var chatLocation: TLRPC.TLChannelLocation? = null
	private val currentLiveLocations = mutableListOf<LocationActivity.LiveLocation>()
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

	fun setLiveLocations(liveLocations: List<LocationActivity.LiveLocation>?) {
		currentLiveLocations.clear()

		liveLocations?.let {
			currentLiveLocations.addAll(it)
		}

		val uid = UserConfig.getInstance(currentAccount).getClientUserId()

		for (a in currentLiveLocations.indices) {
			if (currentLiveLocations[a].id == uid || currentLiveLocations[a].`object`?.out == true) {
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

	fun setChatLocation(location: TLRPC.TLChannelLocation?) {
		chatLocation = location
	}

	private fun updateCell() {
		val sendLocationCell = sendLocationCell ?: return
		val customLocation = customLocation
		val gpsLocation = gpsLocation

		if (locationType == LocationActivity.LOCATION_TYPE_GROUP || customLocation != null) {
			val address = if (!addressName.isNullOrEmpty()) {
				addressName
			}
			else if (customLocation == null && gpsLocation == null || fetchingLocation) {
				if (myLocationDenied) {
					context.getString(R.string.unknown_address)
				}
				else {
					context.getString(R.string.Loading)
				}
			}
			else if (customLocation != null) {
				String.format(Locale.US, "(%f,%f)", customLocation.latitude, customLocation.longitude)
			}
			else if (gpsLocation != null) {
				String.format(Locale.US, "(%f,%f)", gpsLocation.latitude, gpsLocation.longitude)
			}
			else if (!myLocationDenied) {
				context.getString(R.string.Loading)
			}
			else {
				context.getString(R.string.unknown_address)
			}

			if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
				sendLocationCell.setText(context.getString(R.string.ChatSetThisLocation), address)
			}
			else {
				sendLocationCell.setText(context.getString(R.string.SendSelectedLocation), address)
			}

			sendLocationCell.setHasLocation(true)
		}
		else {
			if (gpsLocation != null) {
				sendLocationCell.setText(context.getString(R.string.SendLocation), LocaleController.formatString("AccurateTo", R.string.AccurateTo, LocaleController.formatPluralString("Meters", gpsLocation.accuracy.toInt())))
				sendLocationCell.setHasLocation(true)
			}
			else {
				sendLocationCell.setText(context.getString(R.string.SendLocation), if (myLocationDenied) context.getString(R.string.unknown_address) else context.getString(R.string.Loading))
				sendLocationCell.setHasLocation(true)
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
			var count = 0

			if (searching || !searched || places.isEmpty()) {
				count += if (locationType != LocationActivity.LOCATION_TYPE_SEND) {
					6
				}
				else {
					5
				}

				if (searching || !searched) {
					count += 2
				}

				if (needEmptyView) {
					count += 1
				}
			}
			else {
				count += if (locationType == LocationActivity.LOCATION_TYPE_SEND_WITH_LIVE) {
					6
				}
				else {
					5
				}

				if (showVenues) {
					count += places.size
				}

				if (needEmptyView) {
					count += 1
				}
			}

			if (!showVenues) {
				count -= 5
			}

			count
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val view: View

		when (viewType) {
			ITEM_VIEW_EMPTY_CELL -> {
				view = FrameLayout(context).also {
					it.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overScrollHeight)
					emptyCell = it
				}
			}

			ITEM_VIEW_SEND_LOCATION_CELL -> {
				view = SendLocationCell(context, false)
			}

			ITEM_VIEW_HEADER -> {
				view = HeaderCell(context)
			}

			ITEM_VIEW_LOCATION_CELL -> {
				val locationCell = LocationCell(context, false)
				view = locationCell
			}

			ITEM_VIEW_LOCATION_LOADING_CELL -> {
				view = LocationLoadingCell(context)
			}

			ITEM_VIEW_LOCATION_POWERED_CELL -> {
				view = LocationPoweredCell(context)
			}

			ITEM_VIEW_SEND_LOCATION_DIALOG_CELL -> {
				val cell = SendLocationCell(context, true)
				cell.setDialogId(dialogId)
				view = cell
			}

			ITEM_VIEW_SHARING_LIVE_LOCATION_CELL -> {
				view = SharingLiveLocationCell(context, true, if (locationType == LocationActivity.LOCATION_TYPE_GROUP || locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW) 16 else 54)
			}

			ITEM_VIEW_LOCATION_DIRECTION_CELL -> {
				val cell = LocationDirectionCell(context)

				cell.setOnButtonClick {
					onDirectionClick()
				}

				view = cell
			}

			ITEM_VIEW_SHADOW_CELL -> {
				view = ShadowSectionCell(context)
				val drawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)
				val combinedDrawable = CombinedDrawable(ResourcesCompat.getColor(context.resources, R.color.light_background, null).toDrawable(), drawable)
				combinedDrawable.setFullSize(true)
				view.setBackground(combinedDrawable)
			}

			ITEM_VIEW_EMPTY_VIEW_CELL -> {
				view = View(context)
			}

			else -> {
				view = View(context)
			}
		}

		return RecyclerListView.Holder(view)
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		@Suppress("NAME_SHADOWING") var position = position

		when (holder.itemViewType) {
			ITEM_VIEW_EMPTY_CELL -> {
				var lp = holder.itemView.layoutParams as? RecyclerView.LayoutParams

				if (lp == null) {
					lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overScrollHeight)
				}
				else {
					lp.height = overScrollHeight
				}

				holder.itemView.layoutParams = lp
			}

			ITEM_VIEW_SEND_LOCATION_CELL -> {
				sendLocationCell = holder.itemView as SendLocationCell
				updateCell()
			}

			ITEM_VIEW_HEADER -> {
				val cell = holder.itemView as HeaderCell

				if (currentMessageObject != null) {
					cell.setText(context.getString(R.string.LiveLocations))
				}
				else {
					cell.setText(context.getString(R.string.NearbyVenue))
				}
			}

			ITEM_VIEW_LOCATION_CELL -> {
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

			ITEM_VIEW_LOCATION_LOADING_CELL -> {
				(holder.itemView as LocationLoadingCell).setLoading(searching)
			}

			ITEM_VIEW_LOCATION_POWERED_CELL -> {
				(holder.itemView as SendLocationCell).setHasLocation(gpsLocation != null)
			}

			ITEM_VIEW_SHARING_LIVE_LOCATION_CELL -> {
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

			ITEM_VIEW_EMPTY_VIEW_CELL -> {
				val emptyView = holder.itemView
				emptyView.setBackgroundColor(ResourcesCompat.getColor(context.resources, if (myLocationDenied) R.color.light_background else R.color.background, null))
			}
		}
	}

	fun getItem(i: Int): Any? {
		if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
			return if (addressName == null) {
				null
			}
			else {
				val venue = TLRPC.TLMessageMediaVenue()
				venue.address = addressName

				venue.geo = TLRPC.TLGeoPoint().also {
					if (customLocation != null) {
						it.lat = customLocation!!.latitude
						it.lon = customLocation!!.longitude
					}
					else if (gpsLocation != null) {
						it.lat = gpsLocation!!.latitude
						it.lon = gpsLocation!!.longitude
					}
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
			return ITEM_VIEW_EMPTY_CELL
		}

		if (locationType == LocationActivity.LOCATION_TYPE_LIVE_VIEW) {
			return ITEM_VIEW_SHARING_LIVE_LOCATION_CELL
		}

		if (needEmptyView && position == itemCount - 1) {
			return ITEM_VIEW_EMPTY_VIEW_CELL
		}

		if (locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW) {
			return ITEM_VIEW_SHARING_LIVE_LOCATION_CELL
		}

		if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
			return ITEM_VIEW_SEND_LOCATION_CELL
		}

		if (currentMessageObject != null) {
			if (currentLiveLocations.isEmpty()) {
				if (position == 2) {
					return ITEM_VIEW_LOCATION_DIRECTION_CELL
				}
			}
			else {
				when (position) {
					2 -> {
						return ITEM_VIEW_SHADOW_CELL
					}

					3 -> {
						return ITEM_VIEW_HEADER
					}

					4 -> {
						shareLiveLocationPosition = position
						return ITEM_VIEW_SEND_LOCATION_DIALOG_CELL
					}
				}
			}

			return ITEM_VIEW_SHARING_LIVE_LOCATION_CELL
		}

		if (locationType == 2) {
			return if (position == 1) {
				shareLiveLocationPosition = position
				ITEM_VIEW_SEND_LOCATION_DIALOG_CELL
			}
			else {
				ITEM_VIEW_SHARING_LIVE_LOCATION_CELL
			}
		}

		if (locationType == LocationActivity.LOCATION_TYPE_SEND_WITH_LIVE) {
			if (position == 1) {
				return ITEM_VIEW_SEND_LOCATION_CELL
			}
			else if (position == 2) {
				shareLiveLocationPosition = position
				return ITEM_VIEW_SEND_LOCATION_DIALOG_CELL
			}
			else if (position == 3) {
				return ITEM_VIEW_SHADOW_CELL
			}
			else if (position == 4) {
				return ITEM_VIEW_HEADER
			}
			else if (searching || places.isEmpty() || !searched) {
				return if (position <= 4 + 3 && (searching || !searched)) {
					ITEM_VIEW_LOCATION_CELL
				}
				else {
					ITEM_VIEW_LOCATION_LOADING_CELL
				}
			}
			else if (position == places.size + 5) {
				return ITEM_VIEW_LOCATION_POWERED_CELL
			}
		}
		else {
			if (position == 1) {
				return ITEM_VIEW_SEND_LOCATION_CELL
			}
			else if (position == 2) {
				return ITEM_VIEW_SHADOW_CELL
			}
			else if (position == 3) {
				return ITEM_VIEW_HEADER
			}
			else if (searching || places.isEmpty()) {
				return if (position <= 3 + 3 && (searching || !searched)) {
					ITEM_VIEW_LOCATION_CELL
				}
				else {
					ITEM_VIEW_LOCATION_LOADING_CELL
				}
			}
			else if (position == places.size + 4) {
				return ITEM_VIEW_LOCATION_POWERED_CELL
			}
		}

		return ITEM_VIEW_LOCATION_CELL
	}

	override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
		val viewType = holder.itemViewType

		return if (viewType == ITEM_VIEW_SEND_LOCATION_DIALOG_CELL) {
			!(LocationController.getInstance(currentAccount).getSharingLocationInfo(dialogId) == null && gpsLocation == null)
		}
		else {
			viewType == ITEM_VIEW_SEND_LOCATION_CELL || viewType == ITEM_VIEW_LOCATION_CELL || viewType == ITEM_VIEW_SHARING_LIVE_LOCATION_CELL
		}
	}

	companion object {
		private const val ITEM_VIEW_EMPTY_CELL = 0
		private const val ITEM_VIEW_SEND_LOCATION_CELL = 1
		private const val ITEM_VIEW_HEADER = 2
		private const val ITEM_VIEW_LOCATION_CELL = 3
		private const val ITEM_VIEW_LOCATION_LOADING_CELL = 4
		private const val ITEM_VIEW_LOCATION_POWERED_CELL = 5
		private const val ITEM_VIEW_SEND_LOCATION_DIALOG_CELL = 6
		private const val ITEM_VIEW_SHARING_LIVE_LOCATION_CELL = 7
		private const val ITEM_VIEW_LOCATION_DIRECTION_CELL = 8
		private const val ITEM_VIEW_SHADOW_CELL = 9
		private const val ITEM_VIEW_EMPTY_VIEW_CELL = 10
	}
}
