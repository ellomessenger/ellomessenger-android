/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Components

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.IMapsProvider
import org.telegram.messenger.IMapsProvider.ICameraUpdate
import org.telegram.messenger.IMapsProvider.IMap
import org.telegram.messenger.IMapsProvider.IMapView
import org.telegram.messenger.IMapsProvider.IMarker
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocationController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject.isUserSelf
import org.telegram.messenger.utils.gone
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.MessageMedia
import org.telegram.tgnet.TLRPC.TL_geoPoint
import org.telegram.tgnet.TLRPC.TL_messageMediaGeo
import org.telegram.tgnet.TLRPC.TL_messageMediaGeoLive
import org.telegram.tgnet.TLRPC.TL_messageMediaVenue
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.LocationActivityAdapter
import org.telegram.ui.Adapters.LocationActivitySearchAdapter
import org.telegram.ui.Cells.LocationCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatAttachAlert.AttachAlertLayout
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ChatAttachAlertLocationLayout(alert: ChatAttachAlert, context: Context) : AttachAlertLayout(alert, context), NotificationCenterDelegate {
	private val locationButton: ImageView
	private val mapTypeButton: ActionBarMenuItem
	private val searchAreaButton = SearchButton(context)
	private val emptyView = LinearLayout(context)
	private val emptyImageView: ImageView
	private val emptyTitleTextView: TextView
	private val emptySubtitleTextView = TextView(context)
	private var searchItem: ActionBarMenuItem? = null
	private val overlayView = MapOverlayView(context)
	private var map: IMap? = null
	private var mapView: IMapView?
	private var forceUpdate: ICameraUpdate? = null
	private var yOffset = 0f
	private var ignoreLayout = false
	private var scrolling = false
	private val loadingMapView: View

	private val mapViewClip = object : FrameLayout(context) {
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			overlayView.updatePositions()
		}

		override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
			canvas.save()
			canvas.clipRect(0, 0, measuredWidth, measuredHeight - clipSize)
			val result = super.drawChild(canvas, child, drawingTime)
			canvas.restore()
			return result
		}

		override fun onDraw(canvas: Canvas) {
			backgroundPaint.color = ResourcesCompat.getColor(context.resources, R.color.background, null)
			canvas.drawRect(0f, 0f, measuredWidth.toFloat(), (measuredHeight - clipSize).toFloat(), backgroundPaint)
		}

		override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
			return if (ev.y > measuredHeight - clipSize) {
				false
			}
			else {
				super.onInterceptTouchEvent(ev)
			}
		}

		override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
			return if (ev.y > measuredHeight - clipSize) {
				false
			}
			else {
				super.dispatchTouchEvent(ev)
			}
		}
	}

	private val adapter by lazy {
		// MARK: pass showVenues=true in order to allow venues search
		LocationActivityAdapter(context, locationType, dialogId, needEmptyView = true, showVenues = false)
	}

	private val listView = object : RecyclerListView(context) {
		override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
			super.onLayout(changed, l, t, r, b)
			updateClipView()
		}
	}

	private val searchListView = RecyclerListView(context)

	private val searchAdapter: LocationActivitySearchAdapter by lazy {
		object : LocationActivitySearchAdapter(context) {
			override fun notifyDataSetChanged() {
				searchItem?.setShowSearchProgress(searchAdapter.isSearching())
				emptySubtitleTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("NoPlacesFoundInfo", R.string.NoPlacesFoundInfo, searchAdapter.lastSearchString))
				super.notifyDataSetChanged()
			}
		}
	}

	private val markerImageView = ImageView(context)
	private val layoutManager: FillLastLinearLayoutManager

	// private val avatarDrawable: AvatarDrawable? = null
	private val otherItem: ActionBarMenuItem? = null
	private var currentMapStyleDark = false
	private var checkGpsEnabled = true
	private var locationDenied = false
	private var isFirstLocation = true
	private val dialogId: Long

	// private val firstFocus = true
	private val backgroundPaint = Paint()
	private val placeMarkers = ArrayList<VenueLocation>()
	private var animatorSet: AnimatorSet? = null
	private var lastPressedMarker: IMarker? = null
	private var lastPressedVenue: VenueLocation? = null
	private var lastPressedMarkerView: FrameLayout? = null
	private var checkPermission = true
	private var checkBackgroundPermission = true
	private var searching: Boolean
	private var searchWas: Boolean
	private var searchInProgress: Boolean

	// private val wasResults = false
	private var mapsInitialized = false
	private var onResumeCalled = false
	private var myLocation: Location? = null
	private var userLocation: Location? = null
	private var markerTop = 0
	private var userLocationMoved = false
	private var searchedForCustomLocations = false
	private var firstWas = false
	private var delegate: LocationActivityDelegate? = null
	private var locationType = 0
	private var overScrollHeight = AndroidUtilities.displaySize.x - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(66f)
	private var mapHeight = overScrollHeight
	private var clipSize = 0
	private var nonClipSize = 0
	private var first = true
	private val bitmapCache = arrayOfNulls<Bitmap>(7)

	init {
		AndroidUtilities.fixGoogleMapsBug()
		val chatActivity = parentAlert.baseFragment as ChatActivity

		dialogId = chatActivity.dialogId

		locationType = if (chatActivity.currentEncryptedChat == null && !chatActivity.isInScheduleMode && !isUserSelf(chatActivity.currentUser)) {
			LOCATION_TYPE_SEND_WITH_LIVE
		}
		else {
			LOCATION_TYPE_SEND
		}

		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.locationPermissionGranted)
		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.locationPermissionDenied)

		searchWas = false
		searching = false
		searchInProgress = false

		adapter.destroy()
		searchAdapter.destroy()
		locationDenied = parentActivity?.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED

		val menu = parentAlert.actionBar.createMenu()

		searchItem = menu.addItem(0, R.drawable.ic_search_menu).setIsSearchField(true).setActionBarMenuItemSearchListener(object : ActionBarMenuItemSearchListener() {
			override fun onSearchExpand() {
				searching = true
				parentAlert.makeFocusable(searchItem!!.searchField, true)
			}

			override fun onSearchCollapse() {
				searching = false
				searchWas = false
				searchAdapter.searchDelayed(null, null)

				updateEmptyView()

				otherItem?.visibility = VISIBLE
				listView.visibility = VISIBLE

				mapViewClip.visibility = VISIBLE
				searchListView.visibility = GONE
				emptyView.visibility = GONE
			}

			override fun onTextChanged(editText: EditText) {
				val text = editText.text?.toString()

				if (!text.isNullOrEmpty()) {
					searchWas = true
					searchItem?.setShowSearchProgress(true)
					otherItem?.visibility = GONE
					listView.visibility = GONE
					mapViewClip.visibility = GONE

					if (searchListView.adapter !== searchAdapter) {
						searchListView.adapter = searchAdapter
					}

					searchListView.visibility = VISIBLE
					searchInProgress = searchAdapter.isEmpty

					updateEmptyView()
				}
				else {
					otherItem?.visibility = VISIBLE
					listView.visibility = VISIBLE
					mapViewClip.visibility = VISIBLE
					searchListView.adapter = null
					searchListView.visibility = GONE
					emptyView.visibility = GONE
				}

				searchAdapter.searchDelayed(text, userLocation)
			}
		})

		searchItem?.visibility = if (locationDenied) GONE else VISIBLE
		searchItem?.setSearchFieldHint(context.getString(R.string.Search))
		searchItem?.contentDescription = context.getString(R.string.Search)

		val editText = searchItem?.searchField
		editText?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		editText?.setCursorColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		editText?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))

		val layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(21f))
		layoutParams.gravity = Gravity.LEFT or Gravity.BOTTOM

		mapViewClip.setWillNotDraw(false)
		loadingMapView = View(context)
		loadingMapView.background = MapPlaceholderDrawable()

		searchAreaButton.translationX = -AndroidUtilities.dp(80f).toFloat()
		searchAreaButton.visibility = INVISIBLE

		var drawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(40f), ResourcesCompat.getColor(context.resources, R.color.background, null), ResourcesCompat.getColor(context.resources, R.color.light_background, null))

		var animator = StateListAnimator()
		animator.addState(intArrayOf(android.R.attr.state_pressed), ObjectAnimator.ofFloat(searchAreaButton, TRANSLATION_Z, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(4f).toFloat()).setDuration(200))
		animator.addState(intArrayOf(), ObjectAnimator.ofFloat(searchAreaButton, TRANSLATION_Z, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(2f).toFloat()).setDuration(200))

		searchAreaButton.stateListAnimator = animator

		searchAreaButton.outlineProvider = object : ViewOutlineProvider() {
			@SuppressLint("NewApi")
			override fun getOutline(view: View, outline: Outline) {
				outline.setRoundRect(0, 0, view.measuredWidth, view.measuredHeight, (view.measuredHeight / 2).toFloat())
			}
		}

		searchAreaButton.background = drawable
		searchAreaButton.setTextColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))
		searchAreaButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		searchAreaButton.typeface = Theme.TYPEFACE_BOLD
		searchAreaButton.text = context.getString(R.string.PlacesInThisArea)
		searchAreaButton.gravity = Gravity.CENTER
		searchAreaButton.setPadding(AndroidUtilities.dp(20f), 0, AndroidUtilities.dp(20f), 0)

		mapViewClip.addView(searchAreaButton, createFrame(LayoutHelper.WRAP_CONTENT, 40f, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 80f, 12f, 80f, 0f))

		searchAreaButton.setOnClickListener {
			showSearchPlacesButton(false)
			adapter.searchPlacesWithQuery(null, userLocation, true)
			searchedForCustomLocations = true
			showResults()
		}

		mapTypeButton = ActionBarMenuItem(context, null, 0, ResourcesCompat.getColor(resources, R.color.brand, null))
		mapTypeButton.isClickable = true
		mapTypeButton.setSubMenuOpenSide(2)
		mapTypeButton.setAdditionalXOffset(AndroidUtilities.dp(10f))
		mapTypeButton.setAdditionalYOffset(-AndroidUtilities.dp(10f))
		mapTypeButton.addSubItem(map_list_menu_map, R.drawable.msg_map, context.getString(R.string.Map))
		mapTypeButton.addSubItem(map_list_menu_satellite, R.drawable.msg_satellite, context.getString(R.string.Satellite))
		mapTypeButton.addSubItem(map_list_menu_hybrid, R.drawable.msg_hybrid, context.getString(R.string.Hybrid))
		mapTypeButton.contentDescription = context.getString(R.string.AccDescrMoreOptions)

		drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(40f), ResourcesCompat.getColor(context.resources, R.color.background, null), ResourcesCompat.getColor(context.resources, R.color.light_background, null))

		animator = StateListAnimator()
		animator.addState(intArrayOf(android.R.attr.state_pressed), ObjectAnimator.ofFloat(mapTypeButton, TRANSLATION_Z, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(4f).toFloat()).setDuration(200))
		animator.addState(intArrayOf(), ObjectAnimator.ofFloat(mapTypeButton, TRANSLATION_Z, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(2f).toFloat()).setDuration(200))

		mapTypeButton.stateListAnimator = animator

		mapTypeButton.outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, AndroidUtilities.dp(40f), AndroidUtilities.dp(40f))
			}
		}

		mapTypeButton.background = drawable
		mapTypeButton.setIcon(R.drawable.msg_map_type)

		mapViewClip.addView(mapTypeButton, createFrame(40, 40f, Gravity.RIGHT or Gravity.TOP, 0f, 12f, 12f, 0f))

		mapTypeButton.setOnClickListener {
			mapTypeButton.toggleSubMenu()
		}

		mapTypeButton.setDelegate {
			if (map == null) {
				return@setDelegate
			}

			when (it) {
				map_list_menu_map -> {
					map?.setMapType(IMapsProvider.MAP_TYPE_NORMAL)
				}

				map_list_menu_satellite -> {
					map?.setMapType(IMapsProvider.MAP_TYPE_SATELLITE)
				}

				map_list_menu_hybrid -> {
					map?.setMapType(IMapsProvider.MAP_TYPE_HYBRID)
				}
			}
		}

		locationButton = ImageView(context)

		drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(40f), ResourcesCompat.getColor(context.resources, R.color.background, null), ResourcesCompat.getColor(context.resources, R.color.light_background, null))

		animator = StateListAnimator()
		animator.addState(intArrayOf(android.R.attr.state_pressed), ObjectAnimator.ofFloat(locationButton, TRANSLATION_Z, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(4f).toFloat()).setDuration(200))
		animator.addState(intArrayOf(), ObjectAnimator.ofFloat(locationButton, TRANSLATION_Z, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(2f).toFloat()).setDuration(200))

		locationButton.stateListAnimator = animator

		locationButton.outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, AndroidUtilities.dp(40f), AndroidUtilities.dp(40f))
			}
		}

		locationButton.background = drawable
		locationButton.setImageResource(R.drawable.msg_current_location)
		locationButton.scaleType = ImageView.ScaleType.CENTER
		locationButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)
		locationButton.contentDescription = context.getString(R.string.AccDescrMyLocation)

		val layoutParams1 = createFrame(40, 40f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, 12f, 12f)

		mapViewClip.addView(locationButton, layoutParams1)

		locationButton.setOnClickListener {
			val activity = parentActivity

			if (activity != null) {
				if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
					AlertsCreator.createLocationRequiredDialog(activity, true).show()
					return@setOnClickListener
				}
			}

			val myLocation = myLocation

			if (myLocation != null && map != null) {
				locationButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)

				adapter.setCustomLocation(null)
				userLocationMoved = false
				showSearchPlacesButton(false)

				map?.animateCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLng(IMapsProvider.LatLng(myLocation.latitude, myLocation.longitude)))

				if (searchedForCustomLocations) {
					adapter.searchPlacesWithQuery(null, myLocation, true)
					searchedForCustomLocations = false
					showResults()
				}
			}

			removeInfoView()
		}

		emptyView.orientation = LinearLayout.VERTICAL
		emptyView.gravity = Gravity.CENTER_HORIZONTAL
		emptyView.setPadding(0, AndroidUtilities.dp((60 + 100).toFloat()), 0, 0)
		emptyView.visibility = GONE

		addView(emptyView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		emptyView.setOnTouchListener { _, _ -> true }

		emptyImageView = ImageView(context)
		emptyImageView.setImageResource(R.drawable.location_empty)
		emptyImageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)

		emptyView.addView(emptyImageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

		emptyTitleTextView = TextView(context)
		emptyTitleTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		emptyTitleTextView.gravity = Gravity.CENTER
		emptyTitleTextView.typeface = Theme.TYPEFACE_BOLD
		emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
		emptyTitleTextView.text = context.getString(R.string.NoPlacesFound)

		emptyView.addView(emptyTitleTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0))

		emptySubtitleTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		emptySubtitleTextView.gravity = Gravity.CENTER
		emptySubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		emptySubtitleTextView.setPadding(AndroidUtilities.dp(40f), 0, AndroidUtilities.dp(40f), 0)

		emptyView.addView(emptySubtitleTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0))

		listView.clipToPadding = false
		listView.adapter = adapter

		adapter.setUpdateRunnable {
			updateClipView()
		}

		adapter.setMyLocationDenied(locationDenied)

		listView.isVerticalScrollBarEnabled = false

		listView.layoutManager = object : FillLastLinearLayoutManager(context, VERTICAL, false, 0, listView) {
			override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
				val linearSmoothScroller: LinearSmoothScroller = object : LinearSmoothScroller(recyclerView.context) {
					override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
						var dy = super.calculateDyToMakeVisible(view, snapPreference)
						dy -= listView.paddingTop - (mapHeight - overScrollHeight)
						return dy
					}

					override fun calculateTimeForDeceleration(dx: Int): Int {
						return super.calculateTimeForDeceleration(dx) * 4
					}
				}

				linearSmoothScroller.targetPosition = position
				startSmoothScroll(linearSmoothScroller)
			}
		}.also {
			layoutManager = it
		}

		addView(listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT or Gravity.TOP))

		listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				scrolling = newState != RecyclerView.SCROLL_STATE_IDLE

				if (!scrolling && forceUpdate != null) {
					forceUpdate = null
				}

				if (newState == RecyclerView.SCROLL_STATE_IDLE) {
					val offset = AndroidUtilities.dp(13f)
					val backgroundPaddingTop = parentAlert.backgroundPaddingTop
					val top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset

					if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
						val holder = listView.findViewHolderForAdapterPosition(0) as? RecyclerListView.Holder

						if (holder != null && holder.itemView.top > mapHeight - overScrollHeight) {
							listView.smoothScrollBy(0, holder.itemView.top - (mapHeight - overScrollHeight))
						}
					}
				}
			}

			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				updateClipView()

				if (forceUpdate != null) {
					yOffset += dy.toFloat()
				}

				parentAlert.updateLayout(this@ChatAttachAlertLocationLayout, true, dy)
			}
		})

		listView.setOnItemClickListener { _, position ->
			if (position == 1) {
				if (delegate != null && userLocation != null) {
					if (lastPressedMarkerView != null) {
						lastPressedMarkerView?.callOnClick()
					}
					else {
						val location = TL_messageMediaGeo()
						location.geo = TL_geoPoint()
						location.geo.lat = AndroidUtilities.fixLocationCoordinate(userLocation!!.latitude)
						location.geo._long = AndroidUtilities.fixLocationCoordinate(userLocation!!.longitude)

						if (chatActivity.isInScheduleMode) {
							AlertsCreator.createScheduleDatePickerDialog(parentActivity, chatActivity.dialogId) { notify, scheduleDate ->
								delegate?.didSelectLocation(location, locationType, notify, scheduleDate)
								parentAlert.dismiss(true)
							}
						}
						else {
							delegate?.didSelectLocation(location, locationType, true, 0)
							parentAlert.dismiss(true)
						}
					}
				}
				else if (locationDenied) {
					parentActivity?.let {
						AlertsCreator.createLocationRequiredDialog(it, true).show()
					}
				}
			}
			else if (position == 2 && locationType == LOCATION_TYPE_SEND_WITH_LIVE) {
				if (locationController.isSharingLocation(dialogId)) {
					locationController.removeSharingLocation(dialogId)
					parentAlert.dismiss(true)
				}
				else {
					if (myLocation == null && locationDenied) {
						parentActivity?.let {
							AlertsCreator.createLocationRequiredDialog(it, true).show()
						}
					}
					else {
						openShareLiveLocation()
					}
				}
			}
			else {
				val `object` = adapter.getItem(position)

				if (`object` is TL_messageMediaVenue) {
					if (chatActivity.isInScheduleMode) {
						AlertsCreator.createScheduleDatePickerDialog(parentActivity, chatActivity.dialogId) { notify, scheduleDate ->
							delegate?.didSelectLocation(`object`, locationType, notify, scheduleDate)
							parentAlert.dismiss(true)
						}
					}
					else {
						delegate?.didSelectLocation(`object`, locationType, true, 0)
						parentAlert.dismiss(true)
					}
				}
				else if (`object` is LiveLocation) {
					map?.animateCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(IMapsProvider.LatLng(`object`.marker!!.position.latitude, `object`.marker!!.position.longitude), (map?.maxZoomLevel ?: 0f) - 4))
				}
			}
		}

		adapter.setDelegate(dialogId) { places ->
			updatePlacesMarkers(places)
		}

		adapter.setOverScrollHeight(overScrollHeight)

		addView(mapViewClip, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT or Gravity.TOP))

		mapView = ApplicationLoader.mapsProvider.onCreateMapView(context)

		mapView?.setOnDispatchTouchEventInterceptor { ev, origMethod ->
			@Suppress("NAME_SHADOWING") var ev = ev
			var eventToRecycle: MotionEvent? = null

			if (yOffset != 0f) {
				eventToRecycle = MotionEvent.obtain(ev)
				ev = eventToRecycle
				eventToRecycle.offsetLocation(0f, -yOffset / 2)
			}

			val result = origMethod.call(ev)
			eventToRecycle?.recycle()
			result
		}

		mapView?.setOnInterceptTouchEventInterceptor { ev, origMethod ->
			if (ev.action == MotionEvent.ACTION_DOWN) {
				animatorSet?.cancel()

				animatorSet = AnimatorSet()
				animatorSet?.duration = 200
				animatorSet?.playTogether(ObjectAnimator.ofFloat(markerImageView, TRANSLATION_Y, (markerTop - AndroidUtilities.dp(10f)).toFloat()))
				animatorSet?.start()
			}
			else if (ev.action == MotionEvent.ACTION_UP) {
				animatorSet?.cancel()

				yOffset = 0f

				animatorSet = AnimatorSet()
				animatorSet?.duration = 200
				animatorSet?.playTogether(ObjectAnimator.ofFloat(markerImageView, TRANSLATION_Y, markerTop.toFloat()))
				animatorSet?.start()

				adapter.fetchLocationAddress()
			}

			if (ev.action == MotionEvent.ACTION_MOVE) {
				if (!userLocationMoved) {
					locationButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.text, null), PorterDuff.Mode.SRC_IN)
					userLocationMoved = true
				}

				map?.let { map ->
					userLocation?.latitude = map.cameraPosition.target.latitude
					userLocation?.longitude = map.cameraPosition.target.longitude
				}

				adapter.setCustomLocation(userLocation)
			}
			origMethod.call(ev)!!
		}

		val map = mapView

		thread {
			try {
				map?.onCreate(null)
			}
			catch (e: Exception) {
				//this will cause exception, but will preload google maps?
			}

			AndroidUtilities.runOnUIThread {
				if (mapView != null && parentActivity != null) {
					try {
						map?.onCreate(null)

						ApplicationLoader.mapsProvider.initializeMaps(ApplicationLoader.applicationContext)

						mapView?.getMapAsync { map1 ->
							this.map = map1

							this.map?.setOnMapLoadedCallback {
								AndroidUtilities.runOnUIThread {
									loadingMapView.tag = 1
									loadingMapView.animate().alpha(0.0f).setDuration(180).start()
								}
							}

							if (isActiveThemeDark) {
								currentMapStyleDark = true
								val style = ApplicationLoader.mapsProvider.loadRawResourceStyle(ApplicationLoader.applicationContext, R.raw.mapstyle_night)
								this.map?.setMapStyle(style)
							}

							onMapInit()
						}

						mapsInitialized = true

						if (onResumeCalled) {
							mapView?.onResume()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}
		}

		markerImageView.setImageResource(R.drawable.map_pin2)

		mapViewClip.addView(markerImageView, createFrame(28, 48, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

		searchListView.visibility = GONE
		searchListView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

		searchAdapter.setDelegate(0) {
			searchInProgress = false
			updateEmptyView()
		}

		searchListView.itemAnimator = null

		addView(searchListView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT or Gravity.TOP))

		searchListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searching && searchWas) {
					AndroidUtilities.hideKeyboard(parentAlert.currentFocus)
				}
			}
		})

		searchListView.setOnItemClickListener { _, position ->
			val `object` = searchAdapter.getItem(position)

			if (`object` != null && delegate != null) {
				if (chatActivity.isInScheduleMode) {
					AlertsCreator.createScheduleDatePickerDialog(parentActivity, chatActivity.dialogId) { notify, scheduleDate ->
						delegate?.didSelectLocation(`object`, locationType, notify, scheduleDate)
						parentAlert.dismiss(true)
					}
				}
				else {
					delegate?.didSelectLocation(`object`, locationType, true, 0)
					parentAlert.dismiss(true)
				}
			}
		}

		updateEmptyView()
	}

	override fun shouldHideBottomButtons(): Boolean {
		return !locationDenied
	}

	override fun onPause() {
		if (mapsInitialized) {
			try {
				mapView?.onPause()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		onResumeCalled = false
	}

	override fun onDestroy() {
		NotificationCenter.globalInstance.let {
			it.removeObserver(this, NotificationCenter.locationPermissionGranted)
			it.removeObserver(this, NotificationCenter.locationPermissionDenied)
		}

		try {
			map?.setMyLocationEnabled(false)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		mapView?.view?.translationY = (-AndroidUtilities.displaySize.y * 3).toFloat()

		runCatching {
			mapView?.onPause()
		}

		runCatching {
			mapView?.onDestroy()
		}

		adapter.destroy()

		searchAdapter.destroy()
		parentAlert.actionBar.closeSearchField()

		val menu = parentAlert.actionBar.createMenu()
		menu.removeView(searchItem)
	}

	override fun onHide() {
		searchItem?.gone()
	}

	override fun needsActionBar(): Int {
		return 1
	}

	override fun onDismiss(): Boolean {
		onDestroy()
		return false
	}

	override var currentItemTop: Int
		get() {
			if (listView.childCount <= 0) {
				return Int.MAX_VALUE
			}

			val holder = listView.findViewHolderForAdapterPosition(0) as? RecyclerListView.Holder

			var newOffset = 0

			if (holder != null) {
				val top = holder.itemView.y.toInt() - nonClipSize
				newOffset = max(top, 0)
			}

			return newOffset + AndroidUtilities.dp(56f)
		}
		set(currentItemTop) {
			super.currentItemTop = currentItemTop
		}

	override fun setTranslationY(translationY: Float) {
		super.setTranslationY(translationY)
		parentAlert.sheetContainer.invalidate()
		updateClipView()
	}

	override val listTopPadding: Int
		get() = listView.paddingTop

	override val firstOffset: Int
		get() = listTopPadding + AndroidUtilities.dp(56f)

	override fun onPreMeasure(availableWidth: Int, availableHeight: Int) {
		var padding: Int

		if (parentAlert.actionBar.isSearchFieldVisible || parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20f)) {
			padding = mapHeight - overScrollHeight
			parentAlert.setAllowNestedScroll(false)
		}
		else {
			padding = if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
				(availableHeight / 3.5f).toInt()
			}
			else {
				availableHeight / 5 * 2
			}

			padding -= AndroidUtilities.dp(52f)

			if (padding < 0) {
				padding = 0
			}

			parentAlert.setAllowNestedScroll(true)
		}

		if (listView.paddingTop != padding) {
			ignoreLayout = true
			listView.setPadding(0, padding, 0, 0)
			ignoreLayout = false
		}
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)

		if (changed) {
			fixLayoutInternal()
			first = false
		}
	}

	override val buttonsHideOffset: Int
		get() = AndroidUtilities.dp(56f)

	override fun requestLayout() {
		if (ignoreLayout) {
			return
		}

		super.requestLayout()
	}

	override fun scrollToTop() {
		listView.smoothScrollToPosition(0)
	}

	private val isActiveThemeDark: Boolean
		get() {
			val info = Theme.getActiveTheme()

			if (info.isDark) {
				return true
			}

			val color = ResourcesCompat.getColor(context.resources, R.color.background, null)
			return AndroidUtilities.computePerceivedBrightness(color) < 0.721f
		}

	private fun updateEmptyView() {
		if (searching) {
			if (searchInProgress) {
				searchListView.setEmptyView(null)
				emptyView.visibility = GONE
			}
			else {
				searchListView.setEmptyView(emptyView)
			}
		}
		else {
			emptyView.visibility = GONE
		}
	}

	private fun showSearchPlacesButton(show: Boolean) {
		@Suppress("NAME_SHADOWING") var show = show

		if (show && searchAreaButton.tag == null) {
			if (myLocation == null || userLocation == null || userLocation!!.distanceTo(myLocation!!) < 300) {
				show = false
			}
		}

		if (show && searchAreaButton.tag != null || !show && searchAreaButton.tag == null) {
			return
		}

		searchAreaButton.visibility = if (show) VISIBLE else INVISIBLE
		searchAreaButton.tag = if (show) 1 else null

		val animatorSet = AnimatorSet()
		animatorSet.playTogether(ObjectAnimator.ofFloat(searchAreaButton, TRANSLATION_X, (if (show) 0 else -AndroidUtilities.dp(80f)).toFloat()))
		animatorSet.duration = 180
		animatorSet.interpolator = CubicBezierInterpolator.EASE_OUT
		animatorSet.start()
	}

	fun openShareLiveLocation() {
		val delegate = delegate ?: return
		val parentActivity = parentActivity ?: return
		val myLocation = myLocation ?: return

		if (checkBackgroundPermission && Build.VERSION.SDK_INT >= 29) {
			checkBackgroundPermission = false

			val preferences = MessagesController.getGlobalMainSettings()
			val lastTime = preferences.getInt("backgroundloc", 0)

			if (abs(System.currentTimeMillis() / 1000 - lastTime) > 24 * 60 * 60 && parentActivity.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				preferences.edit().putInt("backgroundloc", (System.currentTimeMillis() / 1000).toInt()).commit()
				AlertsCreator.createBackgroundLocationPermissionDialog(parentActivity, messagesController.getUser(userConfig.getClientUserId())) { openShareLiveLocation() }?.show()
				return
			}
		}

		var user: User? = null

		if (DialogObject.isUserDialog(dialogId)) {
			user = parentAlert.baseFragment.messagesController.getUser(dialogId)
		}

		AlertsCreator.createLocationUpdateDialog(parentActivity, user) { param ->
			val location = TL_messageMediaGeoLive()
			location.geo = TL_geoPoint()
			location.geo.lat = AndroidUtilities.fixLocationCoordinate(myLocation.latitude)
			location.geo._long = AndroidUtilities.fixLocationCoordinate(myLocation.longitude)
			location.period = param

			delegate.didSelectLocation(location, locationType, true, 0)

			parentAlert.dismiss(true)
		}.show()
	}

	private fun createPlaceBitmap(num: Int): Bitmap? {
		if (bitmapCache[num % 7] != null) {
			return bitmapCache[num % 7]
		}

		try {
			val paint = Paint(Paint.ANTI_ALIAS_FLAG)
			paint.color = -0x1

			val bitmap = Bitmap.createBitmap(AndroidUtilities.dp(12f), AndroidUtilities.dp(12f), Bitmap.Config.ARGB_8888)

			val canvas = Canvas(bitmap)
			canvas.drawCircle(AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), paint)

			paint.color = LocationCell.getColorForIndex(num)

			canvas.drawCircle(AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(5f).toFloat(), paint)
			canvas.setBitmap(null)

			return bitmap.also { bitmapCache[num % 7] = it }
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}

		return null
	}

	private fun updatePlacesMarkers(places: List<TL_messageMediaVenue>?) {
		if (places == null) {
			return
		}

		for (venueLocation in placeMarkers) {
			venueLocation.marker?.remove()
		}

		placeMarkers.clear()

		places.forEachIndexed { index, venue ->
			try {
				val options = ApplicationLoader.mapsProvider.onCreateMarkerOptions().position(IMapsProvider.LatLng(venue.geo.lat, venue.geo._long))
				options.icon(createPlaceBitmap(index))
				options.anchor(0.5f, 0.5f)
				options.title(venue.title)
				options.snippet(venue.address)
				val venueLocation = VenueLocation()
				venueLocation.num = index
				venueLocation.marker = map?.addMarker(options)
				venueLocation.venue = venue
				venueLocation.marker?.tag = venueLocation
				placeMarkers.add(venueLocation)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private val messagesController: MessagesController
		get() = parentAlert.baseFragment.messagesController

	private val locationController: LocationController
		get() = parentAlert.baseFragment.locationController

	private val userConfig: UserConfig
		get() = parentAlert.baseFragment.userConfig

	private val parentActivity: Activity?
		get() = parentAlert.baseFragment.parentActivity

	private fun onMapInit() {
		val map = map ?: return

		userLocation = Location("network")
		userLocation?.latitude = 20.659322
		userLocation?.longitude = -11.406250

		try {
			map.setMyLocationEnabled(true)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		map.uiSettings.setMyLocationButtonEnabled(false)
		map.uiSettings.setZoomControlsEnabled(false)
		map.uiSettings.setCompassEnabled(false)

		map.setOnCameraMoveStartedListener { reason ->
			if (reason == IMapsProvider.OnCameraMoveStartedListener.REASON_GESTURE) {
				showSearchPlacesButton(true)
				removeInfoView()

				if (!scrolling && listView.childCount > 0) {
					val view = listView.getChildAt(0)

					if (view != null) {
						val holder = listView.findContainingViewHolder(view)

						if (holder != null && holder.adapterPosition == 0) {
							val min = if (locationType == LOCATION_TYPE_SEND) 0 else AndroidUtilities.dp(66f)
							val top = view.top

							if (top < -min) {
								val cameraPosition = map.cameraPosition
								forceUpdate = ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(cameraPosition.target, cameraPosition.zoom)
								listView.smoothScrollBy(0, top + min)
							}
						}
					}
				}
			}
		}

		map.setOnMyLocationChangeListener { location ->
			positionMarker(location)
			locationController.setMapLocation(location, isFirstLocation)
			isFirstLocation = false
		}

		map.setOnMarkerClickListener { marker ->
			if (marker.tag !is VenueLocation) {
				return@setOnMarkerClickListener true
			}

			markerImageView.visibility = INVISIBLE

			if (!userLocationMoved) {
				locationButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.text, null), PorterDuff.Mode.SRC_IN)
				userLocationMoved = true
			}

			overlayView.addInfoView(marker)

			true
		}

		map.setOnCameraMoveListener {
			overlayView.updatePositions()
		}

		AndroidUtilities.runOnUIThread({
			if (loadingMapView.tag == null) {
				loadingMapView.animate().alpha(0.0f).setDuration(180).start()
			}
		}, 200)

		positionMarker(lastLocation.also { myLocation = it })

		val parentActivity = parentActivity

		if (checkGpsEnabled && parentActivity != null) {
			checkGpsEnabled = false

			if (parentActivity.packageManager?.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) != true) {
				return
			}

			try {
				val lm = ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

				if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
					val builder = AlertDialog.Builder(parentActivity)
					builder.setTopAnimation(R.raw.permission_request_location, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, ResourcesCompat.getColor(resources, R.color.brand, null))
					builder.setMessage(context.getString(R.string.GpsDisabledAlertText))

					builder.setPositiveButton(context.getString(R.string.ConnectingToProxyEnable)) { _, _ ->
						runCatching {
							parentActivity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
						}
					}

					builder.setNegativeButton(context.getString(R.string.Cancel), null)
					builder.show()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		updateClipView()
	}

	private fun removeInfoView() {
		val lastPressedMarker = lastPressedMarker

		if (lastPressedMarker != null) {
			markerImageView.visibility = VISIBLE
			overlayView.removeInfoView(lastPressedMarker)
			this.lastPressedMarker = null
			lastPressedVenue = null
			lastPressedMarkerView = null
		}
	}

	private fun showResults() {
		if (adapter.itemCount == 0) {
			return
		}

		val position = layoutManager.findFirstVisibleItemPosition()

		if (position != 0) {
			return
		}

		val child = listView.getChildAt(0)
		val offset = AndroidUtilities.dp(258f) + child.top

		if (offset < 0 || offset > AndroidUtilities.dp(258f)) {
			return
		}

		listView.smoothScrollBy(0, offset)
	}

	private fun updateClipView() {
		if (mapView == null) {
			return
		}

		val height: Int
		val top: Int
		var holder = listView.findViewHolderForAdapterPosition(0)

		if (holder != null) {
			top = holder.itemView.y.toInt()
			height = overScrollHeight + min(top, 0)
		}
		else {
			top = -mapViewClip.measuredHeight
			height = 0
		}

		val layoutParams = mapViewClip.layoutParams as? LayoutParams

		if (layoutParams != null) {
			if (height <= 0) {
				if (mapView?.view?.visibility == VISIBLE) {
					mapView?.view?.visibility = INVISIBLE
					mapViewClip.visibility = INVISIBLE
					overlayView.visibility = INVISIBLE
				}
				mapView?.view?.translationY = top.toFloat()

				return
			}

			if (mapView?.view?.visibility == INVISIBLE) {
				mapView?.view?.visibility = VISIBLE
				mapViewClip.visibility = VISIBLE
				overlayView.visibility = VISIBLE
			}

			val trY = max(0, -(top - mapHeight + overScrollHeight) / 2)
			var maxClipSize = mapHeight - overScrollHeight
			val totalToMove = listView.paddingTop - maxClipSize
			val moveProgress = 1.0f - max(0.0f, min(1.0f, (listView.paddingTop - top) / totalToMove.toFloat()))
			val prevClipSize = clipSize

			if (locationDenied && isTypeSend) {
				maxClipSize += min(top, listView.paddingTop)
			}

			clipSize = (maxClipSize * moveProgress).toInt()
			mapView?.view?.translationY = trY.toFloat()
			nonClipSize = maxClipSize - clipSize
			mapViewClip.invalidate()
			mapViewClip.translationY = (top - nonClipSize).toFloat()
			map?.setPadding(0, AndroidUtilities.dp(6f), 0, clipSize + AndroidUtilities.dp(6f))
			overlayView.translationY = trY.toFloat()

			val translationY = min(max(nonClipSize - top, 0), mapHeight - mapTypeButton.measuredHeight - AndroidUtilities.dp((64 + 16).toFloat())).toFloat()
			mapTypeButton.translationY = translationY
			searchAreaButton.setTranslation(translationY)
			locationButton.translationY = -clipSize.toFloat()
			markerImageView.translationY = (mapHeight - clipSize) / 2 - AndroidUtilities.dp(48f) + trY.also { markerTop = it }.toFloat()

			if (prevClipSize != clipSize) {
				val location = if (lastPressedMarker != null) {
					IMapsProvider.LatLng(lastPressedMarker!!.position.latitude, lastPressedMarker!!.position.longitude)
				}
				else if (userLocationMoved) {
					IMapsProvider.LatLng(userLocation!!.latitude, userLocation!!.longitude)
				}
				else if (myLocation != null) {
					IMapsProvider.LatLng(myLocation!!.latitude, myLocation!!.longitude)
				}
				else {
					null
				}

				if (location != null) {
					map?.moveCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLng(location))
				}
			}

			if (locationDenied && isTypeSend) {
//                adapter.setOverScrollHeight(overScrollHeight + top);
//                // TODO(dkaraush): fix ripple effect on buttons
				val count = adapter.itemCount

				for (i in 1 until count) {
					holder = listView.findViewHolderForAdapterPosition(i)

					if (holder != null) {
						holder.itemView.translationY = (listView.paddingTop - top).toFloat()
					}
				}
			}
		}
	}

	private val isTypeSend: Boolean
		get() = locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE

	private fun buttonsHeight(): Int {
		var buttonsHeight = AndroidUtilities.dp(66f)

		if (locationType == LOCATION_TYPE_SEND_WITH_LIVE) {
			buttonsHeight += AndroidUtilities.dp(66f)
		}

		return buttonsHeight
	}

	private fun fixLayoutInternal() {
		val viewHeight = measuredHeight

		if (viewHeight == 0 || mapView == null) {
			return
		}

		val height = ActionBar.getCurrentActionBarHeight()
		val maxMapHeight = AndroidUtilities.displaySize.y - height - buttonsHeight() - AndroidUtilities.dp(90f)

		overScrollHeight = AndroidUtilities.dp(189f)
		mapHeight = max(overScrollHeight, if (locationDenied && isTypeSend) maxMapHeight else min(AndroidUtilities.dp(310f), maxMapHeight))

		if (locationDenied && isTypeSend) {
			overScrollHeight = mapHeight
		}

		var layoutParams = listView.layoutParams as? LayoutParams
		layoutParams?.topMargin = height

		listView.layoutParams = layoutParams

		layoutParams = mapViewClip.layoutParams as? LayoutParams
		layoutParams?.topMargin = height
		layoutParams?.height = mapHeight

		mapViewClip.layoutParams = layoutParams

		layoutParams = searchListView.layoutParams as? LayoutParams
		layoutParams?.topMargin = height

		searchListView.layoutParams = layoutParams

		adapter.setOverScrollHeight(if (locationDenied && isTypeSend) overScrollHeight - listView.paddingTop else overScrollHeight)

		layoutParams = mapView?.view?.layoutParams as? LayoutParams

		if (layoutParams != null) {
			layoutParams.height = mapHeight + AndroidUtilities.dp(10f)
			mapView?.view?.layoutParams = layoutParams
		}

		layoutParams = overlayView.layoutParams as? LayoutParams

		if (layoutParams != null) {
			layoutParams.height = mapHeight + AndroidUtilities.dp(10f)
			overlayView.layoutParams = layoutParams
		}

		adapter.notifyDataSetChanged()

		updateClipView()
	}

	private val lastLocation: Location?
		get() {
			if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				return null
			}

			val lm = ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
			val providers = lm.getProviders(true)
			var l: Location? = null

			for (i in providers.indices.reversed()) {
				l = lm.getLastKnownLocation(providers[i])

				if (l != null) {
					break
				}
			}

			return l
		}

	private fun positionMarker(location: Location?) {
		if (location == null) {
			return
		}

		myLocation = Location(location)

		val map = map

		if (map != null) {
			val latLng = IMapsProvider.LatLng(location.latitude, location.longitude)

			if (!searchedForCustomLocations) {
				adapter.searchPlacesWithQuery(null, myLocation, true)
			}

			adapter.setGpsLocation(myLocation)

			if (!userLocationMoved) {
				userLocation = Location(location)

				if (firstWas) {
					val position = ApplicationLoader.mapsProvider.newCameraUpdateLatLng(latLng)
					map.animateCamera(position)
				}
				else {
					firstWas = true
					val position = ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(latLng, map.maxZoomLevel - 4)
					map.moveCamera(position)
				}
			}
		}
		else {
			adapter.setGpsLocation(myLocation)
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.locationPermissionGranted -> {
				locationDenied = false

				adapter.setMyLocationDenied(locationDenied)

				try {
					map?.setMyLocationEnabled(true)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			NotificationCenter.locationPermissionDenied -> {
				locationDenied = true
				adapter.setMyLocationDenied(locationDenied)
			}
		}

		fixLayoutInternal()

		searchItem?.visibility = if (locationDenied) GONE else VISIBLE
	}

	override fun onResume() {
		if (mapsInitialized) {
			try {
				mapView?.onResume()
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		onResumeCalled = true
	}

	override fun onShow(previousLayout: AttachAlertLayout?) {
		parentAlert.actionBar.setTitle(context.getString(R.string.ShareLocation))

		if (mapView?.view?.parent == null) {
			mapViewClip.addView(mapView?.view, 0, createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + AndroidUtilities.dp(10f), Gravity.TOP or Gravity.LEFT))
			mapViewClip.addView(overlayView, 1, createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + AndroidUtilities.dp(10f), Gravity.TOP or Gravity.LEFT))
			mapViewClip.addView(loadingMapView, 2, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		}

		searchItem?.visibility = VISIBLE

		if (mapsInitialized) {
			try {
				mapView?.onResume()
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		onResumeCalled = true

		try {
			map?.setMyLocationEnabled(true)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		fixLayoutInternal()

		val keyboardVisible = parentAlert.chatAttachViewDelegate?.needEnterComment() ?: false

		AndroidUtilities.runOnUIThread({
			if (checkPermission) {
				val activity = parentActivity

				if (activity != null) {
					checkPermission = false

					if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
						activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 2)
					}
				}
			}
		}, (if (keyboardVisible) 200 else 0).toLong())

		layoutManager.scrollToPositionWithOffset(0, 0)

		updateClipView()
	}

	fun setDelegate(delegate: LocationActivityDelegate?) {
		this.delegate = delegate
	}

	fun interface LocationActivityDelegate {
		fun didSelectLocation(location: MessageMedia, live: Int, notify: Boolean, scheduleDate: Int)
	}

	class VenueLocation {
		var num = 0
		var marker: IMarker? = null
		var venue: TL_messageMediaVenue? = null
	}

	class LiveLocation {
		var id = 0
		var `object`: Message? = null
		var user: User? = null
		var chat: Chat? = null
		var marker: IMarker? = null
	}

	@SuppressLint("AppCompatCustomView")
	private class SearchButton(context: Context) : TextView(context) {
		private var additionalTranslationY = 0f
		private var currentTranslationY = 0f

		override fun getTranslationX(): Float {
			return additionalTranslationY
		}

		override fun setTranslationX(translationX: Float) {
			additionalTranslationY = translationX
			updateTranslationY()
		}

		fun setTranslation(value: Float) {
			currentTranslationY = value
			updateTranslationY()
		}

		private fun updateTranslationY() {
			translationY = currentTranslationY + additionalTranslationY
		}
	}

	inner class MapOverlayView(context: Context) : FrameLayout(context) {
		private val views = HashMap<IMarker, View>()

		fun addInfoView(marker: IMarker) {
			val location = marker.tag as VenueLocation

			if (lastPressedVenue === location) {
				return
			}

			showSearchPlacesButton(false)

			if (lastPressedMarker != null) {
				removeInfoView(lastPressedMarker!!)
				lastPressedMarker = null
			}

			lastPressedVenue = location
			lastPressedMarker = marker

			val context = context
			val frameLayout = FrameLayout(context)

			addView(frameLayout, createFrame(LayoutHelper.WRAP_CONTENT, 114f))

			lastPressedMarkerView = FrameLayout(context)
			lastPressedMarkerView?.setBackgroundResource(R.drawable.venue_tooltip)
			lastPressedMarkerView?.background?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.background, null), PorterDuff.Mode.SRC_IN)

			frameLayout.addView(lastPressedMarkerView, createFrame(LayoutHelper.WRAP_CONTENT, 71f))

			lastPressedMarkerView?.alpha = 0.0f

			lastPressedMarkerView?.setOnClickListener {
				val chatActivity = parentAlert.baseFragment as? ChatActivity

				if (chatActivity?.isInScheduleMode == true) {
					AlertsCreator.createScheduleDatePickerDialog(parentActivity, chatActivity.dialogId) { notify, scheduleDate ->
						location.venue?.let {
							delegate?.didSelectLocation(it, locationType, notify, scheduleDate)
						}

						parentAlert.dismiss(true)
					}
				}
				else {
					location.venue?.let {
						delegate?.didSelectLocation(it, locationType, true, 0)
					}

					parentAlert.dismiss(true)
				}
			}

			val nameTextView = TextView(context)
			nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			nameTextView.maxLines = 1
			nameTextView.ellipsize = TextUtils.TruncateAt.END
			nameTextView.isSingleLine = true
			nameTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			nameTextView.typeface = Theme.TYPEFACE_BOLD
			nameTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

			lastPressedMarkerView?.addView(nameTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 18f, 10f, 18f, 0f))

			val addressTextView = TextView(context)
			addressTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			addressTextView.maxLines = 1
			addressTextView.ellipsize = TextUtils.TruncateAt.END
			addressTextView.isSingleLine = true
			addressTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
			addressTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

			lastPressedMarkerView?.addView(addressTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 18f, 32f, 18f, 0f))

			nameTextView.text = location.venue?.title

			addressTextView.text = context.getString(R.string.TapToSendLocation)

			val iconLayout = FrameLayout(context)
			iconLayout.background = Theme.createCircleDrawable(AndroidUtilities.dp(36f), LocationCell.getColorForIndex(location.num))

			frameLayout.addView(iconLayout, createFrame(36, 36f, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0f, 0f, 0f, 4f))

			val imageView = BackupImageView(context)
			imageView.setImage("https://ss3.4sqi.net/img/categories_v2/" + location.venue!!.venue_type + "_64.png", null, null)

			iconLayout.addView(imageView, createFrame(30, 30, Gravity.CENTER))

			val animator = ValueAnimator.ofFloat(0.0f, 1.0f)

			animator.addUpdateListener(object : AnimatorUpdateListener {
				private val animatorValues = floatArrayOf(0.0f, 1.0f)
				private var startedInner = false

				override fun onAnimationUpdate(animation: ValueAnimator) {
					var value = AndroidUtilities.lerp(animatorValues, animation.animatedFraction)

					if (value >= 0.7f && !startedInner && lastPressedMarkerView != null) {
						val animatorSet1 = AnimatorSet()
						animatorSet1.playTogether(ObjectAnimator.ofFloat(lastPressedMarkerView, SCALE_X, 0.0f, 1.0f), ObjectAnimator.ofFloat(lastPressedMarkerView, SCALE_Y, 0.0f, 1.0f), ObjectAnimator.ofFloat(lastPressedMarkerView, ALPHA, 0.0f, 1.0f))
						animatorSet1.interpolator = OvershootInterpolator(1.02f)
						animatorSet1.duration = 250
						animatorSet1.start()

						startedInner = true
					}

					val scale: Float

					if (value <= 0.5f) {
						scale = 1.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(value / 0.5f)
					}
					else if (value <= 0.75f) {
						value -= 0.5f
						scale = 1.1f - 0.2f * CubicBezierInterpolator.EASE_OUT.getInterpolation(value / 0.25f)
					}
					else {
						value -= 0.75f
						scale = 0.9f + 0.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(value / 0.25f)
					}

					iconLayout.scaleX = scale
					iconLayout.scaleY = scale
				}
			})

			animator.duration = 360
			animator.start()

			views[marker] = frameLayout

			map?.animateCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLng(marker.position), 300, null)
		}

		fun removeInfoView(marker: IMarker) {
			val view = views[marker]

			if (view != null) {
				removeView(view)
				views.remove(marker)
			}
		}

		fun updatePositions() {
			val map = map ?: return
			val projection = map.projection

			for ((marker, view) in views) {
				val point = projection.toScreenLocation(marker.position)
				view.translationX = (point.x - view.measuredWidth / 2).toFloat()
				view.translationY = (point.y - view.measuredHeight + AndroidUtilities.dp(22f)).toFloat()
			}
		}
	}

	companion object {
		const val LOCATION_TYPE_SEND = 0
		const val LOCATION_TYPE_SEND_WITH_LIVE = 1
		private const val map_list_menu_map = 2
		private const val map_list_menu_satellite = 3
		private const val map_list_menu_hybrid = 4
	}
}
