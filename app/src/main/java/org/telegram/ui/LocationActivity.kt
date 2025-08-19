/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationManager
import android.opengl.GLES20
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
import androidx.collection.LongSparseArray
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.core.net.toUri
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ChatObject.isChannel
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.IMapsProvider
import org.telegram.messenger.IMapsProvider.ICameraUpdate
import org.telegram.messenger.IMapsProvider.ICircle
import org.telegram.messenger.IMapsProvider.ILatLngBoundsBuilder
import org.telegram.messenger.IMapsProvider.IMap
import org.telegram.messenger.IMapsProvider.IMapView
import org.telegram.messenger.IMapsProvider.IMarker
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocationController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserObject
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.Message
import org.telegram.tgnet.TLRPC.MessageMedia
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.heading
import org.telegram.tgnet.lat
import org.telegram.tgnet.lon
import org.telegram.tgnet.media
import org.telegram.tgnet.period
import org.telegram.tgnet.photoSmall
import org.telegram.tgnet.userId
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.LocationActivityAdapter
import org.telegram.ui.Adapters.LocationActivitySearchAdapter
import org.telegram.ui.Cells.LocationCell
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.ChatAttachAlert
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.HintView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.MapPlaceholderDrawable
import org.telegram.ui.Components.ProximitySheet
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UndoView
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@SuppressLint("NotifyDataSetChanged")
class LocationActivity(private val locationType: Int) : BaseFragment(), NotificationCenter.NotificationCenterDelegate {
	private var locationButton: ImageView? = null
	private var proximityButton: ImageView? = null
	private var mapTypeButton: ActionBarMenuItem? = null
	private var searchAreaButton: SearchButton? = null
	private var emptyView: LinearLayout? = null
	private var emptyImageView: ImageView? = null
	private var emptyTitleTextView: TextView? = null
	private var emptySubtitleTextView: TextView? = null
	private var shadowDrawable: Drawable? = null
	private var shadow: View? = null
	private var searchItem: ActionBarMenuItem? = null
	private var overlayView: MapOverlayView? = null
	private var hintView: HintView? = null
	private val undoView = arrayOfNulls<UndoView>(2)
	private var canUndo = false
	private var proximityAnimationInProgress = false
	private var map: IMap? = null
	private var moveToBounds: ICameraUpdate? = null
	private var mapView: IMapView? = null
	private var forceUpdate: ICameraUpdate? = null
	private var hasScreenshot = false
	private var yOffset = 0f
	private var proximityCircle: ICircle? = null
	private var previousRadius = 0.0
	private var scrolling = false
	private var proximitySheet: ProximitySheet? = null
	private var mapViewClip: FrameLayout? = null
	private var adapter: LocationActivityAdapter? = null
	private var listView: RecyclerListView? = null
	private var searchListView: RecyclerListView? = null
	private var searchAdapter: LocationActivitySearchAdapter? = null
	private var markerImageView: View? = null
	private var layoutManager: LinearLayoutManager? = null
	private var otherItem: ActionBarMenuItem? = null
	private var parentFragment: ChatActivity? = null
	private var currentMapStyleDark = false
	private var checkGpsEnabled = true
	private var locationDenied = false
	private var isFirstLocation = true
	private var dialogId = 0L
	private var firstFocus = true
	private var updateRunnable: Runnable? = null
	private val markers = mutableListOf<LiveLocation>()
	private val markersMap = LongSparseArray<LiveLocation>()
	private val placeMarkers = mutableListOf<VenueLocation>()
	private var animatorSet: AnimatorSet? = null
	private var lastPressedMarker: IMarker? = null
	private var lastPressedVenue: VenueLocation? = null
	private var lastPressedMarkerView: FrameLayout? = null
	private var checkPermission = true
	private var checkBackgroundPermission = true
	private var askWithRadius = 0
	private var searching = false
	private var searchWas = false
	private var searchInProgress = false
	private var mapsInitialized = false
	private var onResumeCalled = false
	private var myLocation: Location? = null
	private var userLocation: Location? = null
	private var markerTop = 0
	private var chatLocation: TLRPC.TLChannelLocation? = null
	private var initialLocation: TLRPC.TLChannelLocation? = null
	private var messageObject: MessageObject? = null
	private var userLocationMoved = false
	private var searchedForCustomLocations = false
	private var firstWas = false
	private var delegate: LocationActivityDelegate? = null
	private var overScrollHeight = AndroidUtilities.displaySize.x - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(66f)
	private var markAsReadRunnable: Runnable? = null

	class VenueLocation {
		var num: Int = 0
		var marker: IMarker? = null
		var venue: TLRPC.TLMessageMediaVenue? = null
	}

	class LiveLocation {
		var user: User? = null
		var chat: Chat? = null
		var directionMarker: IMarker? = null
		var hasRotation: Boolean = false

		@JvmField
		var id: Long = 0

		@JvmField
		var `object`: Message? = null

		@JvmField
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
			val location = marker.tag as? VenueLocation

			if (location == null || lastPressedVenue === location) {
				return
			}

			showSearchPlacesButton(false)

			lastPressedMarker?.let {
				removeInfoView(it)
			}

			lastPressedMarker = null

			lastPressedVenue = location
			lastPressedMarker = marker

			val context = context

			val frameLayout = FrameLayout(context)
			addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 114f))

			lastPressedMarkerView = FrameLayout(context)
			lastPressedMarkerView?.setBackgroundResource(R.drawable.venue_tooltip)
			lastPressedMarkerView?.background?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.background), PorterDuff.Mode.MULTIPLY)

			frameLayout.addView(lastPressedMarkerView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 71f))

			lastPressedMarkerView?.alpha = 0.0f

			lastPressedMarkerView?.setOnClickListener {
				if (parentFragment?.isInScheduleMode == true) {
					AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment!!.dialogId) { notify, scheduleDate ->
						location.venue?.let {
							delegate?.didSelectLocation(it, locationType, notify, scheduleDate)
						}

						finishFragment()
					}
				}
				else {
					location.venue?.let {
						delegate?.didSelectLocation(it, locationType, true, 0)
					}

					finishFragment()
				}
			}

			val nameTextView = TextView(context)
			nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			nameTextView.maxLines = 1
			nameTextView.ellipsize = TextUtils.TruncateAt.END
			nameTextView.isSingleLine = true
			nameTextView.setTextColor(context.getColor(R.color.text))
			nameTextView.typeface = Theme.TYPEFACE_BOLD
			nameTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

			lastPressedMarkerView?.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT), 18f, 10f, 18f, 0f))

			val addressTextView = TextView(context)
			addressTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			addressTextView.maxLines = 1
			addressTextView.ellipsize = TextUtils.TruncateAt.END
			addressTextView.isSingleLine = true
			addressTextView.setTextColor(context.getColor(R.color.dark_gray))
			addressTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

			lastPressedMarkerView?.addView(addressTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT), 18f, 32f, 18f, 0f))

			nameTextView.text = location.venue?.title
			addressTextView.text = context.getString(R.string.TapToSendLocation)

			val iconLayout = FrameLayout(context)
			iconLayout.background = Theme.createCircleDrawable(AndroidUtilities.dp(36f), LocationCell.getColorForIndex(location.num))

			frameLayout.addView(iconLayout, LayoutHelper.createFrame(36, 36f, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0f, 0f, 0f, 4f))

			val imageView = BackupImageView(context)
			imageView.setImage("https://ss3.4sqi.net/img/categories_v2/" + location.venue?.venueType + "_64.png", null, null)

			iconLayout.addView(imageView, LayoutHelper.createFrame(30, 30, Gravity.CENTER))

			val animator = ValueAnimator.ofFloat(0.0f, 1.0f)

			animator.addUpdateListener(object : AnimatorUpdateListener {
				private var startedInner = false
				private val animatorValues = floatArrayOf(0.0f, 1.0f)

				override fun onAnimationUpdate(animation: ValueAnimator) {
					var value = AndroidUtilities.lerp(animatorValues, animation.animatedFraction)

					if (value >= 0.7f && !startedInner && lastPressedMarkerView != null) {
						val animatorSet1 = AnimatorSet()
						animatorSet1.playTogether(ObjectAnimator.ofFloat(lastPressedMarkerView, SCALE_X, 0.0f, 1.0f), ObjectAnimator.ofFloat(lastPressedMarkerView, SCALE_Y, 0.0f, 1.0f), ObjectAnimator.ofFloat(lastPressedMarkerView, ALPHA, 0.0f, 1.0f))
						animatorSet1.interpolator = OvershootInterpolator(1.02f)
						animatorSet1.setDuration(250)
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

			animator.setDuration(360)
			animator.start()

			views[marker] = frameLayout

			map?.animateCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLng(marker.position), 300, null)
		}

		fun removeInfoView(marker: IMarker) {
			val view = views[marker] ?: return
			removeView(view)
			views.remove(marker)
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

	fun interface LocationActivityDelegate {
		fun didSelectLocation(location: MessageMedia, live: Int, notify: Boolean, scheduleDate: Int)
	}

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()

		notificationCenter.addObserver(this, NotificationCenter.closeChats)

		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.locationPermissionGranted)
		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.locationPermissionDenied)
		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.liveLocationsChanged)

		if (messageObject?.isLiveLocation == true) {
			notificationCenter.addObserver(this, NotificationCenter.didReceiveNewMessages)
			notificationCenter.addObserver(this, NotificationCenter.replaceMessagesObjects)
		}

		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.locationPermissionGranted)
		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.locationPermissionDenied)
		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.liveLocationsChanged)

		notificationCenter.removeObserver(this, NotificationCenter.closeChats)
		notificationCenter.removeObserver(this, NotificationCenter.didReceiveNewMessages)
		notificationCenter.removeObserver(this, NotificationCenter.replaceMessagesObjects)

		try {
			map?.setMyLocationEnabled(false)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		try {
			mapView?.onDestroy()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		undoView[0]?.hide(true, 0)

		adapter?.destroy()
		searchAdapter?.destroy()

		updateRunnable?.let {
			AndroidUtilities.cancelRunOnUIThread(it)
		}

		updateRunnable = null

		markAsReadRunnable?.let {
			AndroidUtilities.cancelRunOnUIThread(it)
		}

		markAsReadRunnable = null
	}

	private fun getUndoView(): UndoView? {
		if (undoView[0]?.visibility == View.VISIBLE) {
			val old = undoView[0]

			undoView[0] = undoView[1]
			undoView[1] = old

			old?.hide(true, 2)

			mapViewClip?.removeView(undoView[0])
			mapViewClip?.addView(undoView[0])
		}

		return undoView[0]
	}

	override fun isSwipeBackEnabled(event: MotionEvent): Boolean {
		return false
	}

	override fun createView(context: Context): View? {
		searchWas = false
		searching = false
		searchInProgress = false

		adapter?.destroy()

		searchAdapter?.destroy()

		if (chatLocation != null) {
			userLocation = Location("network")
			userLocation?.latitude = chatLocation?.geoPoint?.lat ?: 0.0
			userLocation?.longitude = chatLocation?.geoPoint?.lon ?: 0.0
		}
		else if (messageObject != null) {
			userLocation = Location("network")
			userLocation?.latitude = messageObject?.messageOwner?.media?.geo?.lat ?: 0.0
			userLocation?.longitude = messageObject?.messageOwner?.media?.geo?.lon ?: 0.0
		}

		locationDenied = parentActivity?.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED

		actionBar?.setBackgroundColor(context.getColor(R.color.background))
		actionBar?.setTitleColor(context.getColor(R.color.text))
		actionBar?.setItemsColor(context.getColor(R.color.text), false)
		actionBar?.setItemsBackgroundColor(context.getColor(R.color.brand), false)
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		if (AndroidUtilities.isTablet()) {
			actionBar?.occupyStatusBar = false
		}

		actionBar?.setAddToContainer(false)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}

					open_in -> {
						try {
							val lat = messageObject?.messageOwner?.media?.geo?.lat ?: 0.0
							val lon = messageObject?.messageOwner?.media?.geo?.lon ?: 0.0

							parentActivity?.startActivity(Intent(Intent.ACTION_VIEW, "geo:$lat,$lon?q=$lat,$lon".toUri()))
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					share_live_location -> {
						openShareLiveLocation(0)
					}
				}
			}
		})

		val menu = actionBar?.createMenu()

		if (chatLocation != null) {
			actionBar?.setTitle(context.getString(R.string.ChatLocation))
		}
		else if (messageObject != null) {
			if (messageObject?.isLiveLocation == true) {
				actionBar?.setTitle(context.getString(R.string.AttachLiveLocation))
			}
			else {
				if (!messageObject?.messageOwner?.media?.title.isNullOrEmpty()) {
					actionBar?.setTitle(context.getString(R.string.SharedPlace))
				}
				else {
					actionBar?.setTitle(context.getString(R.string.ChatLocation))
				}

				otherItem = menu?.addItem(0, R.drawable.overflow_menu)
				otherItem?.addSubItem(open_in, R.drawable.msg_openin, context.getString(R.string.OpenInExternalApp))

				if (!locationController.isSharingLocation(dialogId)) {
					otherItem?.addSubItem(share_live_location, R.drawable.msg_location, context.getString(R.string.SendLiveLocationMenu))
				}

				otherItem?.contentDescription = context.getString(R.string.AccDescrMoreOptions)
			}
		}
		else {
			actionBar?.setTitle(context.getString(R.string.ShareLocation))

			if (locationType != LOCATION_TYPE_GROUP) {
				overlayView = MapOverlayView(context)

				searchItem = menu?.addItem(0, R.drawable.ic_search_menu)?.setIsSearchField(true)?.setActionBarMenuItemSearchListener(object : ActionBarMenuItemSearchListener() {
					override fun onSearchExpand() {
						searching = true
					}

					override fun onSearchCollapse() {
						searching = false
						searchWas = false
						searchAdapter?.searchDelayed(null, null)
						updateEmptyView()
					}

					override fun onTextChanged(editText: EditText) {
						if (searchAdapter == null) {
							return
						}

						val text = editText.text?.toString()

						if (!text.isNullOrEmpty()) {
							searchWas = true
							searchItem?.setShowSearchProgress(true)

							otherItem?.gone()
							listView?.gone()
							mapViewClip?.gone()

							if (searchListView?.adapter !== searchAdapter) {
								searchListView?.setAdapter(searchAdapter)
							}

							searchListView?.visible()

							searchInProgress = searchAdapter?.itemCount == 0
						}
						else {
							otherItem?.visible()
							listView?.visible()
							mapViewClip?.visible()

							searchListView?.setAdapter(null)
							searchListView?.gone()
						}

						updateEmptyView()

						searchAdapter?.searchDelayed(text, userLocation)
					}
				})

				searchItem?.setSearchFieldHint(context.getString(R.string.Search))
				searchItem?.contentDescription = context.getString(R.string.Search)

				val editText = searchItem?.searchField
				editText?.setTextColor(context.getColor(R.color.text))
				editText?.setCursorColor(context.getColor(R.color.text))
				editText?.setHintTextColor(context.getColor(R.color.hint))
			}
		}

		fragmentView = object : FrameLayout(context) {
			private var first = true

			override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
				super.onLayout(changed, left, top, right, bottom)

				if (changed) {
					fixLayoutInternal(first)
					first = false
				}
				else {
					updateClipView(true)
				}
			}

			override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
				val result = super.drawChild(canvas, child, drawingTime)

				if (child === actionBar && parentLayout != null) {
					parentLayout?.drawHeaderShadow(canvas, actionBar?.measuredHeight ?: 0)
				}

				return result
			}
		}

		val frameLayout = fragmentView as FrameLayout

		fragmentView?.setBackgroundColor(context.getColor(R.color.background))

		shadowDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.sheet_shadow_round, null)?.mutate()
		shadowDrawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.background), PorterDuff.Mode.MULTIPLY)

		val padding = Rect()

		shadowDrawable?.getPadding(padding)

		val layoutParams = if (locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE) {
			FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(21f) + padding.top)
		}
		else {
			FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(6f) + padding.top)
		}

		layoutParams.gravity = Gravity.LEFT or Gravity.BOTTOM

		mapViewClip = object : FrameLayout(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
				overlayView?.updatePositions()
			}
		}

		mapViewClip?.background = MapPlaceholderDrawable()

		if (messageObject == null && (locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE)) {
			searchAreaButton = SearchButton(context)
			searchAreaButton?.translationX = -AndroidUtilities.dp(80f).toFloat()

			val drawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(40f), context.getColor(R.color.background), context.getColor(R.color.light_background))

			val animator = StateListAnimator()
			animator.addState(intArrayOf(android.R.attr.state_pressed), ObjectAnimator.ofFloat(searchAreaButton, View.TRANSLATION_Z, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(4f).toFloat()).setDuration(200))
			animator.addState(intArrayOf(), ObjectAnimator.ofFloat(searchAreaButton, View.TRANSLATION_Z, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(2f).toFloat()).setDuration(200))

			searchAreaButton?.stateListAnimator = animator

			searchAreaButton?.outlineProvider = object : ViewOutlineProvider() {
				@SuppressLint("NewApi")
				override fun getOutline(view: View, outline: Outline) {
					outline.setRoundRect(0, 0, view.measuredWidth, view.measuredHeight, (view.measuredHeight / 2).toFloat())
				}
			}

			searchAreaButton?.background = drawable
			searchAreaButton?.setTextColor(context.getColor(R.color.brand))
			searchAreaButton?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			searchAreaButton?.setTypeface(Theme.TYPEFACE_BOLD)
			searchAreaButton?.text = context.getString(R.string.PlacesInThisArea)
			searchAreaButton?.gravity = Gravity.CENTER
			searchAreaButton?.setPadding(AndroidUtilities.dp(20f), 0, AndroidUtilities.dp(20f), 0)

			mapViewClip?.addView(searchAreaButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40f, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 80f, 12f, 80f, 0f))

			searchAreaButton?.setOnClickListener {
				showSearchPlacesButton(false)
				adapter?.searchPlacesWithQuery(null, userLocation, true)
				searchedForCustomLocations = true
				showResults()
			}
		}

		mapTypeButton = ActionBarMenuItem(context, null, 0, context.getColor(R.color.text))
		mapTypeButton?.isClickable = true
		mapTypeButton?.setSubMenuOpenSide(2)
		mapTypeButton?.setAdditionalXOffset(AndroidUtilities.dp(10f))
		mapTypeButton?.setAdditionalYOffset(-AndroidUtilities.dp(10f))
		mapTypeButton?.addSubItem(map_list_menu_map, R.drawable.msg_map, context.getString(R.string.Map))
		mapTypeButton?.addSubItem(map_list_menu_satellite, R.drawable.msg_satellite, context.getString(R.string.Satellite))
		mapTypeButton?.addSubItem(map_list_menu_hybrid, R.drawable.msg_hybrid, context.getString(R.string.Hybrid))
		mapTypeButton?.contentDescription = context.getString(R.string.AccDescrMoreOptions)

		var drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(40f), context.getColor(R.color.background), context.getColor(R.color.light_background))

		var animator = StateListAnimator()
		animator.addState(intArrayOf(android.R.attr.state_pressed), ObjectAnimator.ofFloat(mapTypeButton, View.TRANSLATION_Z, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(4f).toFloat()).setDuration(200))
		animator.addState(intArrayOf(), ObjectAnimator.ofFloat(mapTypeButton, View.TRANSLATION_Z, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(2f).toFloat()).setDuration(200))

		mapTypeButton?.stateListAnimator = animator

		mapTypeButton?.outlineProvider = object : ViewOutlineProvider() {
			@SuppressLint("NewApi")
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, AndroidUtilities.dp(40f), AndroidUtilities.dp(40f))
			}
		}

		mapTypeButton?.background = drawable
		mapTypeButton?.setIcon(R.drawable.msg_map_type)

		mapViewClip?.addView(mapTypeButton, LayoutHelper.createFrame(40, 40f, Gravity.RIGHT or Gravity.TOP, 0f, 12f, 12f, 0f))

		mapTypeButton?.setOnClickListener {
			mapTypeButton?.toggleSubMenu()
		}

		mapTypeButton?.setDelegate {
			if (map == null) {
				return@setDelegate
			}

			when (it) {
				map_list_menu_map -> map?.setMapType(IMapsProvider.MAP_TYPE_NORMAL)
				map_list_menu_satellite -> map?.setMapType(IMapsProvider.MAP_TYPE_SATELLITE)
				map_list_menu_hybrid -> map?.setMapType(IMapsProvider.MAP_TYPE_HYBRID)
			}
		}

		locationButton = ImageView(context)

		drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(40f), context.getColor(R.color.background), context.getColor(R.color.light_background))

		animator = StateListAnimator()
		animator.addState(intArrayOf(android.R.attr.state_pressed), ObjectAnimator.ofFloat(locationButton, View.TRANSLATION_Z, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(4f).toFloat()).setDuration(200))
		animator.addState(intArrayOf(), ObjectAnimator.ofFloat(locationButton, View.TRANSLATION_Z, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(2f).toFloat()).setDuration(200))

		locationButton?.stateListAnimator = animator

		locationButton?.outlineProvider = object : ViewOutlineProvider() {
			@SuppressLint("NewApi")
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, AndroidUtilities.dp(40f), AndroidUtilities.dp(40f))
			}
		}

		locationButton?.background = drawable
		locationButton?.setImageResource(R.drawable.msg_current_location)
		locationButton?.scaleType = ImageView.ScaleType.CENTER
		locationButton?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.MULTIPLY)
		locationButton?.contentDescription = context.getString(R.string.AccDescrMyLocation)

		val layoutParams1 = LayoutHelper.createFrame(40, 40f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, 12f, 12f)
		layoutParams1.bottomMargin += layoutParams.height - padding.top

		mapViewClip?.addView(locationButton, layoutParams1)

		locationButton?.setOnClickListener {
			val activity = parentActivity

			if (activity != null) {
				if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
					showPermissionAlert(false)
					return@setOnClickListener
				}
			}

			if (!checkGpsEnabled()) {
				return@setOnClickListener
			}

			if (messageObject != null || chatLocation != null) {
				if (myLocation != null && map != null) {
					map?.animateCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(IMapsProvider.LatLng(myLocation!!.latitude, myLocation!!.longitude), map!!.maxZoomLevel - 4))
				}
			}
			else {
				if (myLocation != null && map != null) {
					locationButton?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.MULTIPLY)

					adapter?.setCustomLocation(null)

					userLocationMoved = false

					showSearchPlacesButton(false)

					map?.animateCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLng(IMapsProvider.LatLng(myLocation!!.latitude, myLocation!!.longitude)))

					if (searchedForCustomLocations) {
						if (myLocation != null) {
							adapter?.searchPlacesWithQuery(null, myLocation, true)
						}

						searchedForCustomLocations = false

						showResults()
					}
				}
			}

			removeInfoView()
		}

		proximityButton = ImageView(context)

		drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(40f), context.getColor(R.color.background), context.getColor(R.color.light_background))

		animator = StateListAnimator()
		animator.addState(intArrayOf(android.R.attr.state_pressed), ObjectAnimator.ofFloat(proximityButton, View.TRANSLATION_Z, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(4f).toFloat()).setDuration(200))
		animator.addState(intArrayOf(), ObjectAnimator.ofFloat(proximityButton, View.TRANSLATION_Z, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(2f).toFloat()).setDuration(200))

		proximityButton?.stateListAnimator = animator

		proximityButton?.outlineProvider = object : ViewOutlineProvider() {
			@SuppressLint("NewApi")
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, AndroidUtilities.dp(40f), AndroidUtilities.dp(40f))
			}
		}

		proximityButton?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.MULTIPLY)
		proximityButton?.background = drawable
		proximityButton?.scaleType = ImageView.ScaleType.CENTER
		proximityButton?.contentDescription = context.getString(R.string.AccDescrLocationNotify)

		mapViewClip?.addView(proximityButton, LayoutHelper.createFrame(40, 40f, Gravity.RIGHT or Gravity.TOP, 0f, (12 + 50).toFloat(), 12f, 0f))

		proximityButton?.setOnClickListener {
			if (parentActivity == null || myLocation == null || !checkGpsEnabled() || map == null) {
				return@setOnClickListener
			}

			hintView?.hide()

			MessagesController.getGlobalMainSettings().edit { putInt("proximityhint", 3) }

			val info = locationController.getSharingLocationInfo(dialogId)

			if (canUndo) {
				undoView[0]?.hide(true, 1)
			}

			if (info != null && info.proximityMeters > 0) {
				proximityButton?.setImageResource(R.drawable.msg_location_alert)

				proximityCircle?.remove()
				proximityCircle = null

				canUndo = true

				getUndoView()?.showWithAction(0, UndoView.ACTION_PROXIMITY_REMOVED, 0, null, {
					locationController.setProximityLocation(dialogId, 0, true)
					canUndo = false
				}, {
					proximityButton?.setImageResource(R.drawable.msg_location_alert2)
					createCircle(info.proximityMeters)
					canUndo = false
				})

				return@setOnClickListener
			}

			openProximityAlert()
		}

		var chat: Chat? = null

		if (DialogObject.isChatDialog(dialogId)) {
			chat = messagesController.getChat(-dialogId)
		}

		if (messageObject == null || !messageObject!!.isLiveLocation || messageObject!!.isExpiredLiveLocation(connectionsManager.currentTime) || isChannel(chat) && !chat.megagroup) {
			proximityButton?.gone()
			proximityButton?.setImageResource(R.drawable.msg_location_alert)
		}
		else {
			val myInfo = locationController.getSharingLocationInfo(dialogId)

			if (myInfo != null && myInfo.proximityMeters > 0) {
				proximityButton?.setImageResource(R.drawable.msg_location_alert2)
			}
			else {
				if (DialogObject.isUserDialog(dialogId) && messageObject?.fromChatId == userConfig.getClientUserId()) {
					proximityButton?.invisible()
					proximityButton?.alpha = 0.0f
					proximityButton?.scaleX = 0.4f
					proximityButton?.scaleY = 0.4f
				}

				proximityButton?.setImageResource(R.drawable.msg_location_alert)
			}
		}

		hintView = HintView(context, 6, true)
		hintView?.visibility = View.INVISIBLE
		hintView?.setShowingDuration(4000)

		mapViewClip?.addView(hintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 10f, 0f, 10f, 0f))

		emptyView = LinearLayout(context)
		emptyView?.orientation = LinearLayout.VERTICAL
		emptyView?.gravity = Gravity.CENTER_HORIZONTAL
		emptyView?.setPadding(0, AndroidUtilities.dp((60 + 100).toFloat()), 0, 0)
		emptyView?.gone()

		frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		emptyView?.setOnTouchListener { _, _ -> true }

		emptyImageView = ImageView(context)
		emptyImageView?.setImageResource(R.drawable.location_empty)
		emptyImageView?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.dark_gray), PorterDuff.Mode.MULTIPLY)

		emptyView?.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

		emptyTitleTextView = TextView(context)
		emptyTitleTextView?.setTextColor(context.getColor(R.color.dark_gray))
		emptyTitleTextView?.gravity = Gravity.CENTER
		emptyTitleTextView?.setTypeface(Theme.TYPEFACE_BOLD)
		emptyTitleTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
		emptyTitleTextView?.text = context.getString(R.string.NoPlacesFound)

		emptyView?.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0))

		emptySubtitleTextView = TextView(context)
		emptySubtitleTextView?.setTextColor(context.getColor(R.color.dark_gray))
		emptySubtitleTextView?.gravity = Gravity.CENTER
		emptySubtitleTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		emptySubtitleTextView?.setPadding(AndroidUtilities.dp(40f), 0, AndroidUtilities.dp(40f), 0)

		emptyView?.addView(emptySubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0))

		listView = RecyclerListView(context)

		listView?.setAdapter(object : LocationActivityAdapter(context, locationType, dialogId, false, false) {
			override fun onDirectionClick() {
				val activity = parentActivity

				if (activity != null) {
					if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
						showPermissionAlert(true)
						return
					}
				}

				if (myLocation != null) {
					try {
						val intent = if (messageObject != null) {
							Intent(Intent.ACTION_VIEW, String.format(Locale.US, "http://maps.google.com/maps?saddr=%f,%f&daddr=%f,%f", myLocation!!.latitude, myLocation!!.longitude, messageObject!!.messageOwner!!.media!!.geo?.lat, messageObject!!.messageOwner!!.media!!.geo?.lon).toUri())
						}
						else {
							Intent(Intent.ACTION_VIEW, String.format(Locale.US, "http://maps.google.com/maps?saddr=%f,%f&daddr=%f,%f", myLocation!!.latitude, myLocation!!.longitude, chatLocation?.geoPoint?.lat ?: 0.0, chatLocation?.geoPoint?.lon ?: 0.0).toUri())
						}

						parentActivity?.startActivity(intent)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}
		}.also { adapter = it })

		adapter?.setMyLocationDenied(locationDenied)
		adapter?.setUpdateRunnable { updateClipView(false) }

		listView?.isVerticalScrollBarEnabled = false
		listView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false).also { layoutManager = it }

		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT or Gravity.TOP))

		listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				scrolling = newState != RecyclerView.SCROLL_STATE_IDLE

				if (!scrolling && forceUpdate != null) {
					forceUpdate = null
				}
			}

			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				updateClipView(false)

				if (forceUpdate != null) {
					yOffset += dy.toFloat()
				}
			}
		})

		(listView?.itemAnimator as? DefaultItemAnimator)?.setDelayAnimations(false)

		listView?.setOnItemClickListener { _, position ->
			if (locationType == LOCATION_TYPE_GROUP) {
				if (position == 1) {
					val venue = adapter?.getItem(position) as? TLRPC.TLMessageMediaVenue ?: return@setOnItemClickListener

					if (dialogId == 0L) {
						delegate?.didSelectLocation(venue, LOCATION_TYPE_GROUP, true, 0)
						finishFragment()
					}
					else {
						var progressDialog: AlertDialog? = AlertDialog(parentActivity!!, 3)

						val req = TLRPC.TLChannelsEditLocation()
						req.address = venue.address
						req.channel = messagesController.getInputChannel(-dialogId)

						req.geoPoint = TLRPC.TLInputGeoPoint().also {
							it.lat = venue.geo?.lat ?: 0.0
							it.lon = venue.geo?.lon ?: 0.0
						}

						val requestId = connectionsManager.sendRequest(req) { _, _ ->
							AndroidUtilities.runOnUIThread {
								runCatching {
									progressDialog?.dismiss()
								}

								progressDialog = null

								delegate?.didSelectLocation(venue, LOCATION_TYPE_GROUP, true, 0)

								finishFragment()
							}
						}

						progressDialog?.setOnCancelListener {
							connectionsManager.cancelRequest(requestId, true)
						}

						showDialog(progressDialog)
					}
				}
			}
			else if (locationType == LOCATION_TYPE_GROUP_VIEW) {
				map?.animateCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(IMapsProvider.LatLng(chatLocation?.geoPoint?.lat ?: 0.0, chatLocation?.geoPoint?.lon ?: 0.0), map!!.maxZoomLevel - 4))
			}
			else if (position == 1 && messageObject != null && (!messageObject!!.isLiveLocation || locationType == LOCATION_TYPE_LIVE_VIEW)) {
				map?.animateCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(IMapsProvider.LatLng(messageObject?.messageOwner?.media?.geo?.lat ?: 0.0, messageObject?.messageOwner?.media?.geo?.lon ?: 0.0), map!!.maxZoomLevel - 4))
			}
			else if (position == 1 && locationType != 2) {
				if (delegate != null && userLocation != null) {
					if (lastPressedMarkerView != null) {
						lastPressedMarkerView?.callOnClick()
					}
					else {
						val location = TLRPC.TLMessageMediaGeo()
						location.geo = TLRPC.TLGeoPoint().also {
							it.lat = AndroidUtilities.fixLocationCoordinate(userLocation!!.latitude)
							it.lon = AndroidUtilities.fixLocationCoordinate(userLocation!!.longitude)
						}

						if (parentFragment?.isInScheduleMode == true) {
							AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment!!.dialogId) { notify, scheduleDate ->
								delegate?.didSelectLocation(location, locationType, notify, scheduleDate)
								finishFragment()
							}
						}
						else {
							delegate?.didSelectLocation(location, locationType, true, 0)
							finishFragment()
						}
					}
				}
			}
			else if (position == 2 && locationType == 1 || position == 1 && locationType == 2 || position == 3 && locationType == 3) {
				if (locationController.isSharingLocation(dialogId)) {
					locationController.removeSharingLocation(dialogId)
					finishFragment()
				}
				else {
					openShareLiveLocation(0)
				}
			}
			else {
				val `object` = adapter?.getItem(position)

				if (`object` is TLRPC.TLMessageMediaVenue) {
					if (parentFragment?.isInScheduleMode == true) {
						AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment!!.dialogId) { notify, scheduleDate ->
							delegate?.didSelectLocation(`object`, locationType, notify, scheduleDate)
							finishFragment()
						}
					}
					else {
						delegate?.didSelectLocation(`object`, locationType, true, 0)
						finishFragment()
					}
				}
				else if (`object` is LiveLocation) {
					map?.animateCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(`object`.marker!!.position, map!!.maxZoomLevel - 4))
				}
			}
		}

		adapter?.setDelegate(dialogId) {
			this.updatePlacesMarkers(it)
		}

		adapter?.setOverScrollHeight(overScrollHeight)

		frameLayout.addView(mapViewClip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT or Gravity.TOP))

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
			if (messageObject == null && chatLocation == null) {
				if (ev.action == MotionEvent.ACTION_DOWN) {
					animatorSet?.cancel()

					animatorSet = AnimatorSet()
					animatorSet?.setDuration(200)
					animatorSet?.playTogether(ObjectAnimator.ofFloat(markerImageView, View.TRANSLATION_Y, (markerTop - AndroidUtilities.dp(10f)).toFloat()))
					animatorSet?.start()
				}
				else if (ev.action == MotionEvent.ACTION_UP) {
					animatorSet?.cancel()

					yOffset = 0f

					animatorSet = AnimatorSet()
					animatorSet?.setDuration(200)
					animatorSet?.playTogether(ObjectAnimator.ofFloat(markerImageView, View.TRANSLATION_Y, markerTop.toFloat()))
					animatorSet?.start()

					adapter?.fetchLocationAddress()
				}

				if (ev.action == MotionEvent.ACTION_MOVE) {
					if (!userLocationMoved) {
						locationButton?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.MULTIPLY)

						userLocationMoved = true
					}

					if (map != null) {
						if (userLocation != null) {
							userLocation?.latitude = map!!.cameraPosition.target.latitude
							userLocation?.longitude = map!!.cameraPosition.target.longitude
						}
					}

					adapter?.setCustomLocation(userLocation)
				}
			}

			origMethod.call(ev)
		}

		mapView?.setOnLayoutListener {
			AndroidUtilities.runOnUIThread {
				if (moveToBounds != null) {
					map?.moveCamera(moveToBounds)
					moveToBounds = null
				}
			}
		}

		val map = mapView

		Thread {
			try {
				map?.onCreate(null)
			}
			catch (e: Exception) {
				// this will cause exception, but will preload google maps?
			}

			AndroidUtilities.runOnUIThread {
				if (mapView != null && parentActivity != null) {
					try {
						map?.onCreate(null)

						ApplicationLoader.mapsProvider.initializeMaps(ApplicationLoader.applicationContext)

						mapView?.getMapAsync { map1 ->
							this.map = map1

							if (isActiveThemeDark) {
								currentMapStyleDark = true

								val style = ApplicationLoader.mapsProvider.loadRawResourceStyle(ApplicationLoader.applicationContext, R.raw.mapstyle_night)

								this.map?.setMapStyle(style)
							}

							this.map?.setPadding(AndroidUtilities.dp(70f), 0, AndroidUtilities.dp(70f), AndroidUtilities.dp(10f))

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
		}.start()

		if (messageObject == null && chatLocation == null) {
			if (chat != null && locationType == LOCATION_TYPE_GROUP && dialogId != 0L) {
				val frameLayout1 = FrameLayout(context)
				frameLayout1.setBackgroundResource(R.drawable.livepin)

				mapViewClip?.addView(frameLayout1, LayoutHelper.createFrame(62, 76, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

				val backupImageView = BackupImageView(context)
				backupImageView.setRoundRadius(AndroidUtilities.dp(26f))
				backupImageView.setForUserOrChat(chat, AvatarDrawable(chat))

				frameLayout1.addView(backupImageView, LayoutHelper.createFrame(52, 52f, Gravity.LEFT or Gravity.TOP, 5f, 5f, 0f, 0f))

				markerImageView = frameLayout1
				markerImageView?.tag = 1
			}

			if (markerImageView == null) {
				val imageView = ImageView(context)
				imageView.setImageResource(R.drawable.map_pin2)

				mapViewClip?.addView(imageView, LayoutHelper.createFrame(28, 48, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

				markerImageView = imageView
			}

			searchListView = RecyclerListView(context)
			searchListView?.gone()
			searchListView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

			searchAdapter = object : LocationActivitySearchAdapter(context) {
				override fun notifyDataSetChanged() {
					searchItem?.setShowSearchProgress(searchAdapter?.isSearching() == true)

					emptySubtitleTextView?.text = AndroidUtilities.replaceTags(LocaleController.formatString("NoPlacesFoundInfo", R.string.NoPlacesFoundInfo, searchAdapter?.lastSearchString))

					super.notifyDataSetChanged()
				}
			}

			searchAdapter?.setDelegate(0) {
				searchInProgress = false
				updateEmptyView()
			}

			frameLayout.addView(searchListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT or Gravity.TOP))

			searchListView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
					if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searching && searchWas) {
						AndroidUtilities.hideKeyboard(parentActivity?.currentFocus)
					}
				}
			})

			searchListView?.setOnItemClickListener { _, position ->
				val `object` = searchAdapter?.getItem(position)

				if (`object` != null && delegate != null) {
					if (parentFragment?.isInScheduleMode == true) {
						AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment!!.dialogId) { notify, scheduleDate ->
							delegate?.didSelectLocation(`object`, locationType, notify, scheduleDate)
							finishFragment()
						}
					}
					else {
						delegate?.didSelectLocation(`object`, locationType, true, 0)
						finishFragment()
					}
				}
			}
		}
		else if (messageObject != null && !messageObject!!.isLiveLocation || chatLocation != null) {
			if (chatLocation != null) {
				adapter?.setChatLocation(chatLocation)
			}
			else if (messageObject != null) {
				adapter?.setMessageObject(messageObject)
			}
		}
		if (messageObject != null && locationType == LOCATION_TYPE_LIVE_VIEW) {
			adapter?.setMessageObject(messageObject)
		}


		for (a in 0..1) {
			undoView[a] = UndoView(context)
			undoView[a]?.setAdditionalTranslationY(AndroidUtilities.dp(10f).toFloat())
			undoView[a]?.translationZ = AndroidUtilities.dp(5f).toFloat()

			mapViewClip?.addView(undoView[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM or Gravity.LEFT, 8f, 0f, 8f, 8f))
		}

		shadow = object : View(context) {
			private val rect = RectF()

			override fun onDraw(canvas: Canvas) {
				shadowDrawable?.setBounds(-padding.left, 0, measuredWidth + padding.right, measuredHeight)
				shadowDrawable?.draw(canvas)

				if (locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE) {
					val w = AndroidUtilities.dp(36f)
					val y = padding.top + AndroidUtilities.dp(10f)

					rect.set(((measuredWidth - w) / 2).toFloat(), y.toFloat(), ((measuredWidth + w) / 2).toFloat(), (y + AndroidUtilities.dp(4f)).toFloat())

					val color = getContext().getColor(R.color.light_background)

					Theme.dialogs_onlineCirclePaint.color = color

					canvas.drawRoundRect(rect, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), Theme.dialogs_onlineCirclePaint)
				}
			}
		}

		shadow?.translationZ = AndroidUtilities.dp(6f).toFloat()

		mapViewClip?.addView(shadow, layoutParams)

		if (messageObject == null && chatLocation == null && initialLocation != null) {
			userLocationMoved = true

			locationButton?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.MULTIPLY)
		}

		frameLayout.addView(actionBar)

		updateEmptyView()

		return fragmentView
	}

	private val isActiveThemeDark: Boolean
		get() = AndroidUtilities.isDarkTheme()

	private fun updateEmptyView() {
		if (searching) {
			if (searchInProgress) {
				searchListView?.setEmptyView(null)
				emptyView?.gone()
				searchListView?.gone()
			}
			else {
				searchListView?.setEmptyView(emptyView)
			}
		}
		else {
			emptyView?.gone()
		}
	}

	private fun showSearchPlacesButton(show: Boolean) {
		@Suppress("NAME_SHADOWING") var show = show

		if (show && searchAreaButton != null && searchAreaButton?.tag == null) {
			if (myLocation == null || userLocation == null || userLocation!!.distanceTo(myLocation!!) < 300) {
				show = false
			}
		}

		if (searchAreaButton == null || show && searchAreaButton?.tag != null || !show && searchAreaButton?.tag == null) {
			return
		}

		searchAreaButton?.tag = if (show) 1 else null

		val animatorSet = AnimatorSet()
		animatorSet.playTogether(ObjectAnimator.ofFloat(searchAreaButton, View.TRANSLATION_X, (if (show) 0 else -AndroidUtilities.dp(80f)).toFloat()))
		animatorSet.setDuration(180)
		animatorSet.interpolator = CubicBezierInterpolator.EASE_OUT
		animatorSet.start()
	}

	private fun createUserBitmap(liveLocation: LiveLocation?): Bitmap? {
		if (liveLocation == null) {
			return null
		}

		var result: Bitmap? = null

		try {
			val photo = (liveLocation.user as? TLRPC.TLUser)?.photo?.photoSmall ?: liveLocation.chat?.photo?.photoSmall

			result = createBitmap(AndroidUtilities.dp(62f), AndroidUtilities.dp(85f))
			result.eraseColor(Color.TRANSPARENT)

			val canvas = Canvas(result)

			val drawable = ResourcesCompat.getDrawable(ApplicationLoader.applicationContext.resources, R.drawable.map_pin_photo, null)
			drawable?.setBounds(0, 0, AndroidUtilities.dp(62f), AndroidUtilities.dp(85f))
			drawable?.draw(canvas)

			val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
			val bitmapRect = RectF()

			canvas.withSave {
				if (photo != null) {
					val path = fileLoader.getPathToAttach(photo, true)
					val bitmap = BitmapFactory.decodeFile(path.toString())

					if (bitmap != null) {
						val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
						val matrix = Matrix()
						val scale = AndroidUtilities.dp(50f) / bitmap.width.toFloat()

						matrix.postTranslate(AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat())
						matrix.postScale(scale, scale)

						roundPaint.setShader(shader)

						shader.setLocalMatrix(matrix)

						bitmapRect.set(AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp((50 + 6).toFloat()).toFloat(), AndroidUtilities.dp((50 + 6).toFloat()).toFloat())

						drawRoundRect(bitmapRect, AndroidUtilities.dp(25f).toFloat(), AndroidUtilities.dp(25f).toFloat(), roundPaint)
					}
				}
				else {
					val avatarDrawable = AvatarDrawable()

					if (liveLocation.user != null) {
						avatarDrawable.setInfo(liveLocation.user)
					}
					else if (liveLocation.chat != null) {
						avatarDrawable.setInfo(liveLocation.chat)
					}

					translate(AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat())

					avatarDrawable.setBounds(0, 0, AndroidUtilities.dp(50f), AndroidUtilities.dp(50f))
					avatarDrawable.draw(this)
				}

			}

			try {
				canvas.setBitmap(null)
			}
			catch (e: Exception) {
				// don't prompt, this will crash on 2.x
			}
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}

		return result
	}

	private fun getMessageId(message: Message?): Long {
		return if (message?.fromId != null) {
			MessageObject.getFromChatId(message)
		}
		else {
			MessageObject.getDialogId(message)
		}
	}

	private fun openProximityAlert() {
		if (proximityCircle == null) {
			createCircle(500)
		}
		else {
			previousRadius = proximityCircle?.radius ?: 0.0
		}

		val user = if (DialogObject.isUserDialog(dialogId)) {
			messagesController.getUser(dialogId)
		}
		else {
			null
		}

		proximitySheet = ProximitySheet(parentActivity, user, ProximitySheet.onRadiusPickerChange { move, radius ->
			if (proximityCircle != null) {
				proximityCircle?.radius = radius.toDouble()

				if (move) {
					moveToBounds(radius, self = true, animated = true)
				}
			}

			if (DialogObject.isChatDialog(dialogId)) {
				return@onRadiusPickerChange true
			}

			for (location in markers) {
				if (location.`object` == null || UserObject.isUserSelf(location.user)) {
					continue
				}

				val point = location.`object`?.media?.geo

				val loc = Location("network")
				loc.latitude = point?.lat ?: 0.0
				loc.longitude = point?.lon ?: 0.0

				if (myLocation!!.distanceTo(loc) > radius) {
					return@onRadiusPickerChange true
				}
			}

			false
		}, ProximitySheet.onRadiusPickerChange { _, radius ->
			val context = context ?: return@onRadiusPickerChange false
			val info = locationController.getSharingLocationInfo(dialogId)

			if (info == null) {
				val builder = AlertDialog.Builder(context)
				builder.setTitle(context.getString(R.string.ShareLocationAlertTitle))
				builder.setMessage(context.getString(R.string.ShareLocationAlertText))
				builder.setPositiveButton(context.getString(R.string.ShareLocationAlertButton)) { _, _ -> shareLiveLocation(user, 900, radius) }
				builder.setNegativeButton(context.getString(R.string.Cancel), null)

				showDialog(builder.create())

				return@onRadiusPickerChange false
			}

			proximitySheet?.setRadiusSet()

			proximityButton?.setImageResource(R.drawable.msg_location_alert2)

			getUndoView()?.showWithAction(0, UndoView.ACTION_PROXIMITY_SET, radius, user, null, null)

			locationController.setProximityLocation(dialogId, radius, true)

			true
		}) {
			map?.setPadding(AndroidUtilities.dp(70f), 0, AndroidUtilities.dp(70f), AndroidUtilities.dp(10f))

			if (proximitySheet?.radiusSet != true) {
				if (previousRadius > 0) {
					proximityCircle?.radius = previousRadius
				}
				else if (proximityCircle != null) {
					proximityCircle?.remove()
					proximityCircle = null
				}
			}

			proximitySheet = null
		}

		val frameLayout = fragmentView as? FrameLayout
		frameLayout?.addView(proximitySheet, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		proximitySheet?.show()
	}

	private fun openShareLiveLocation(proximityRadius: Int) {
		val parentActivity = parentActivity

		if (delegate == null || parentActivity == null || myLocation == null || !checkGpsEnabled()) {
			return
		}

		if (checkBackgroundPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			askWithRadius = proximityRadius
			checkBackgroundPermission = false

			val preferences = MessagesController.getGlobalMainSettings()
			val lastTime = preferences.getInt("backgroundloc", 0)

			if (abs((System.currentTimeMillis() / 1000 - lastTime).toDouble()) > 24 * 60 * 60 && parentActivity.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				preferences.edit { putInt("backgroundloc", (System.currentTimeMillis() / 1000).toInt()) }

				AlertsCreator.createBackgroundLocationPermissionDialog(parentActivity, messagesController.getUser(userConfig.getClientUserId())) {
					openShareLiveLocation(askWithRadius)
				}?.show()

				return
			}
		}

		val user = if (DialogObject.isUserDialog(dialogId)) {
			messagesController.getUser(dialogId)
		}
		else {
			null
		}

		showDialog(AlertsCreator.createLocationUpdateDialog(parentActivity, user) { shareLiveLocation(user, it, proximityRadius) })
	}

	private fun shareLiveLocation(user: User?, period: Int, radius: Int) {
		val myLocation = myLocation ?: return

		val location = TLRPC.TLMessageMediaGeoLive()

		location.geo = TLRPC.TLGeoPoint().also {
			it.lat = AndroidUtilities.fixLocationCoordinate(myLocation.latitude)
			it.lon = AndroidUtilities.fixLocationCoordinate(myLocation.longitude)
		}

		location.heading = LocationController.getHeading(myLocation)
		location.flags = location.flags or 1
		location.period = period
		location.proximityNotificationRadius = radius
		location.flags = location.flags or 8

		delegate?.didSelectLocation(location, locationType, true, 0)

		if (radius > 0) {
			proximitySheet?.setRadiusSet()
			proximityButton?.setImageResource(R.drawable.msg_location_alert2)
			proximitySheet?.dismiss()
			getUndoView()?.showWithAction(0, UndoView.ACTION_PROXIMITY_SET, radius, user, null, null)
		}
		else {
			finishFragment()
		}
	}

	private val bitmapCache = arrayOfNulls<Bitmap>(7)

	private fun createPlaceBitmap(num: Int): Bitmap? {
		if (bitmapCache[num % 7] != null) {
			return bitmapCache[num % 7]
		}
		try {
			val paint = Paint(Paint.ANTI_ALIAS_FLAG)
			paint.color = -0x1

			val bitmap = createBitmap(AndroidUtilities.dp(12f), AndroidUtilities.dp(12f))

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

	private fun updatePlacesMarkers(places: List<TLRPC.TLMessageMediaVenue>?) {
		if (places == null) {
			return
		}

		placeMarkers.forEach { it.marker?.remove() }
		placeMarkers.clear()

		places.forEachIndexed { index, venue ->
			try {
				val options = ApplicationLoader.mapsProvider.onCreateMarkerOptions().position(IMapsProvider.LatLng(venue.geo?.lat ?: 0.0, venue.geo?.lon ?: 0.0))
				options.icon(createPlaceBitmap(index))
				options.anchor(0.5f, 0.5f)
				options.title(venue.title)
				options.snippet(venue.address)

				val venueLocation = VenueLocation()
				venueLocation.num = index
				venueLocation.marker = map?.addMarker(options)
				venueLocation.venue = venue
				venueLocation.marker?.setTag(venueLocation)

				placeMarkers.add(venueLocation)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun addUserMarker(message: Message): LiveLocation {
		var liveLocation = markersMap[MessageObject.getFromChatId(message)]
		val latLng = IMapsProvider.LatLng(message.media?.geo?.lat, message.media?.geo?.lon)

		if (liveLocation == null) {
			liveLocation = LiveLocation()
			liveLocation.`object` = message

			if (liveLocation.`object`?.fromId is TLRPC.TLPeerUser) {
				liveLocation.user = messagesController.getUser(liveLocation.`object`?.fromId?.userId)
				liveLocation.id = liveLocation.`object`?.fromId?.userId ?: 0
			}
			else {
				val did = MessageObject.getDialogId(message)

				if (DialogObject.isUserDialog(did)) {
					liveLocation.user = messagesController.getUser(did)
				}
				else {
					liveLocation.chat = messagesController.getChat(-did)
				}

				liveLocation.id = did
			}

			try {
				val options = ApplicationLoader.mapsProvider.onCreateMarkerOptions().position(latLng)
				val bitmap = createUserBitmap(liveLocation)

				if (bitmap != null) {
					options.icon(bitmap)
					options.anchor(0.5f, 0.907f)

					liveLocation.marker = map?.addMarker(options)

					if (!UserObject.isUserSelf(liveLocation.user)) {
						val dirOptions = ApplicationLoader.mapsProvider.onCreateMarkerOptions().position(latLng).flat(true)
						dirOptions.anchor(0.5f, 0.5f)

						liveLocation.directionMarker = map?.addMarker(dirOptions)

						if (message.media?.heading != 0) {
							liveLocation.directionMarker?.setRotation(message.media?.heading ?: 0)
							liveLocation.directionMarker?.setIcon(R.drawable.map_pin_cone2)
							liveLocation.hasRotation = true
						}
						else {
							liveLocation.directionMarker?.setRotation(0)
							liveLocation.directionMarker?.setIcon(R.drawable.map_pin_circle)
							liveLocation.hasRotation = false
						}
					}

					markers.add(liveLocation)
					markersMap.put(liveLocation.id, liveLocation)

					val myInfo = locationController.getSharingLocationInfo(dialogId)

					if (liveLocation.id == userConfig.getClientUserId() && myInfo != null && liveLocation.`object`!!.id == myInfo.mid && myLocation != null) {
						val latLng1 = IMapsProvider.LatLng(myLocation!!.latitude, myLocation!!.longitude)
						liveLocation.marker?.setPosition(latLng1)
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
		else {
			liveLocation.`object` = message
			liveLocation.marker?.position = latLng
		}

		proximitySheet?.updateText(true, true)

		return liveLocation
	}

	private fun addUserMarker(location: TLRPC.TLChannelLocation): LiveLocation {
		val latLng = IMapsProvider.LatLng(location.geoPoint?.lat, location.geoPoint?.lon)
		val liveLocation = LiveLocation()

		if (DialogObject.isUserDialog(dialogId)) {
			liveLocation.user = messagesController.getUser(dialogId)
		}
		else {
			liveLocation.chat = messagesController.getChat(-dialogId)
		}

		liveLocation.id = dialogId

		try {
			val options = ApplicationLoader.mapsProvider.onCreateMarkerOptions().position(latLng)
			val bitmap = createUserBitmap(liveLocation)

			if (bitmap != null) {
				options.icon(bitmap)
				options.anchor(0.5f, 0.907f)

				liveLocation.marker = map?.addMarker(options)

				if (!UserObject.isUserSelf(liveLocation.user)) {
					val dirOptions = ApplicationLoader.mapsProvider.onCreateMarkerOptions().position(latLng).flat(true)
					dirOptions.icon(R.drawable.map_pin_circle)
					dirOptions.anchor(0.5f, 0.5f)

					liveLocation.directionMarker = map?.addMarker(dirOptions)
				}

				markers.add(liveLocation)
				markersMap.put(liveLocation.id, liveLocation)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		return liveLocation
	}

	private fun onMapInit() {
		val map = map ?: return

		if (chatLocation != null) {
			val liveLocation = addUserMarker(chatLocation!!)
			map.moveCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(liveLocation.marker!!.position, map.maxZoomLevel - 4))
		}
		else if (messageObject != null) {
			if (messageObject!!.isLiveLocation) {
				val liveLocation = addUserMarker(messageObject!!.messageOwner!!)

				if (!getRecentLocations()) {
					map.moveCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(liveLocation.marker!!.position, map.maxZoomLevel - 4))
				}
			}
			else {
				val latLng = IMapsProvider.LatLng(userLocation!!.latitude, userLocation!!.longitude)

				try {
					map.addMarker(ApplicationLoader.mapsProvider.onCreateMarkerOptions().position(latLng).icon(R.drawable.map_pin2))
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				val position = ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(latLng, map.maxZoomLevel - 4)

				map.moveCamera(position)

				firstFocus = false

				getRecentLocations()
			}
		}
		else {
			userLocation = Location("network")

			if (initialLocation != null) {
				val latLng = IMapsProvider.LatLng(initialLocation?.geoPoint?.lat, initialLocation?.geoPoint?.lon)

				map.moveCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(latLng, map.maxZoomLevel - 4))

				userLocation?.latitude = initialLocation?.geoPoint?.lat ?: 0.0
				userLocation?.longitude = initialLocation?.geoPoint?.lon ?: 0.0

				adapter?.setCustomLocation(userLocation)
			}
			else {
				userLocation?.latitude = 20.659322
				userLocation?.longitude = -11.406250
			}
		}

		try {
			map.setMyLocationEnabled(true)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		map.uiSettings.setMyLocationButtonEnabled(false)
		map.uiSettings.setZoomControlsEnabled(false)
		map.uiSettings.setCompassEnabled(false)

		map.setOnCameraMoveStartedListener {
			if (it == IMapsProvider.OnCameraMoveStartedListener.REASON_GESTURE) {
				showSearchPlacesButton(true)
				removeInfoView()

				if (!scrolling && (locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE) && !listView!!.isNotEmpty()) {
					val view = listView?.getChildAt(0)

					if (view != null) {
						val holder = listView?.findContainingViewHolder(view)

						if (holder != null && holder.adapterPosition == 0) {
							val min = if (locationType == LOCATION_TYPE_SEND) 0 else AndroidUtilities.dp(66f)
							val top = view.top

							if (top < -min) {
								val cameraPosition = map.cameraPosition
								forceUpdate = ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(cameraPosition.target, cameraPosition.zoom)
								listView?.smoothScrollBy(0, top + min)
							}
						}
					}
				}
			}
		}

		map.setOnMyLocationChangeListener {
			positionMarker(it)
			locationController.setMapLocation(it, isFirstLocation)
			isFirstLocation = false
		}

		map.setOnMarkerClickListener {
			val context = context ?: return@setOnMarkerClickListener false

			if (it.tag !is VenueLocation) {
				return@setOnMarkerClickListener true
			}

			markerImageView?.invisible()

			if (!userLocationMoved) {
				locationButton?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.MULTIPLY)
				userLocationMoved = true
			}

			overlayView?.addInfoView(it)

			true
		}

		map.setOnCameraMoveListener {
			overlayView?.updatePositions()
		}

		positionMarker(lastLocation.also { myLocation = it })

		if (checkGpsEnabled && parentActivity != null) {
			checkGpsEnabled = false
			checkGpsEnabled()
		}

		if (proximityButton?.visibility == View.VISIBLE) {
			val myInfo = locationController.getSharingLocationInfo(dialogId)

			if (myInfo != null && myInfo.proximityMeters > 0) {
				createCircle(myInfo.proximityMeters)
			}
		}
	}

	private fun checkGpsEnabled(): Boolean {
		val parentActivity = parentActivity ?: return false

		if (!parentActivity.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
			return true
		}
		try {
			val lm = ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

			if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				val builder = AlertDialog.Builder(parentActivity)
				builder.setTopAnimation(R.raw.permission_request_location, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, ResourcesCompat.getColor(parentActivity.resources, R.color.brand, null))
				builder.setMessage(parentActivity.getString(R.string.GpsDisabledAlertText))

				builder.setPositiveButton(parentActivity.getString(R.string.ConnectingToProxyEnable)) { _, _ ->
					runCatching {
						parentActivity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
					}
				}

				builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

				showDialog(builder.create())

				return false
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		return true
	}

	private fun createCircle(meters: Int) {
		if (map == null) {
			return
		}

		val patternPolygonAlpha = listOf(IMapsProvider.PatternItem.Gap(20), IMapsProvider.PatternItem.Dash(20))

		val circleOptions = ApplicationLoader.mapsProvider.onCreateCircleOptions()
		circleOptions.center(IMapsProvider.LatLng(myLocation!!.latitude, myLocation!!.longitude))
		circleOptions.radius(meters.toDouble())

		if (isActiveThemeDark) {
			circleOptions.strokeColor(-0x69995c29)
			circleOptions.fillColor(0x1c66A3D7)
		}
		else {
			circleOptions.strokeColor(-0x69bd790b)
			circleOptions.fillColor(0x1c4286F5)
		}

		circleOptions.strokePattern(patternPolygonAlpha)
		circleOptions.strokeWidth(2)

		proximityCircle = map?.addCircle(circleOptions)
	}

	private fun removeInfoView() {
		if (lastPressedMarker != null) {
			markerImageView?.visible()
			overlayView?.removeInfoView(lastPressedMarker!!)
			lastPressedMarker = null
			lastPressedVenue = null
			lastPressedMarkerView = null
		}
	}

	private fun showPermissionAlert(byButton: Boolean) {
		val parentActivity = parentActivity ?: return

		val builder = AlertDialog.Builder(parentActivity)
		builder.setTopAnimation(R.raw.permission_request_location, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, ResourcesCompat.getColor(parentActivity.resources, R.color.brand, null))

		if (byButton) {
			builder.setMessage(AndroidUtilities.replaceTags(parentActivity.getString(R.string.PermissionNoLocationNavigation)))
		}
		else {
			builder.setMessage(AndroidUtilities.replaceTags(parentActivity.getString(R.string.PermissionNoLocationFriends)))
		}

		builder.setNegativeButton(parentActivity.getString(R.string.PermissionOpenSettings)) { _, _ ->
			try {
				val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
				intent.setData(("package:" + ApplicationLoader.applicationContext.packageName).toUri())
				parentActivity.startActivity(intent)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		builder.setPositiveButton(parentActivity.getString(R.string.OK), null)

		showDialog(builder.create())
	}

	public override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
		if (isOpen && !backward) {
			runCatching {
				(mapView?.view?.parent as? ViewGroup)?.removeView(mapView?.view)
			}

			if (mapViewClip != null) {
				mapViewClip?.addView(mapView?.view, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + AndroidUtilities.dp(10f), Gravity.TOP or Gravity.LEFT))

				if (overlayView != null) {
					runCatching {
						(overlayView?.parent as? ViewGroup)?.removeView(overlayView)
					}

					mapViewClip?.addView(overlayView, 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + AndroidUtilities.dp(10f), Gravity.TOP or Gravity.LEFT))
				}

				updateClipView(false)

				maybeShowProximityHint()
			}
			else if (fragmentView != null) {
				(fragmentView as? FrameLayout)?.addView(mapView?.view, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))
			}
		}
	}

	private fun maybeShowProximityHint() {
		if (proximityButton?.visibility != View.VISIBLE || proximityAnimationInProgress) {
			return
		}

		val preferences = MessagesController.getGlobalMainSettings()
		var `val` = preferences.getInt("proximityhint", 0)

		if (`val` < 3) {
			preferences.edit { putInt("proximityhint", ++`val`) }

			if (DialogObject.isUserDialog(dialogId)) {
				val user = messagesController.getUser(dialogId)
				hintView?.setOverrideText(LocaleController.formatString("ProximityTooltioUser", R.string.ProximityTooltioUser, UserObject.getFirstName(user)))
			}
			else {
				hintView?.setOverrideText(context!!.getString(R.string.ProximityTooltioGroup))
			}

			hintView?.showForView(proximityButton, true)
		}
	}

	private fun showResults() {
		if (adapter?.itemCount == 0) {
			return
		}

		val position = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION

		if (position != 0) {
			return
		}

		val child = listView?.getChildAt(0)
		val offset = AndroidUtilities.dp(258f) + (child?.top ?: 0)

		if (offset < 0 || offset > AndroidUtilities.dp(258f)) {
			return
		}

		listView?.smoothScrollBy(0, offset)
	}

	private fun updateClipView(fromLayout: Boolean) {
		var height = 0
		val top: Int
		val holder = listView?.findViewHolderForAdapterPosition(0)

		if (holder != null) {
			top = holder.itemView.y.toInt()
			height = (overScrollHeight + (min(top.toDouble(), 0.0))).toInt()
		}
		else {
			top = -mapViewClip!!.measuredHeight
		}

		var layoutParams = mapViewClip?.layoutParams as? FrameLayout.LayoutParams

		if (layoutParams != null) {
			if (height <= 0) {
				if (mapView?.view?.visibility == View.VISIBLE) {
					mapView?.view?.invisible()
					mapViewClip?.invisible()
					overlayView?.invisible()
				}
			}
			else {
				if (mapView?.view?.visibility == View.INVISIBLE) {
					mapView?.view?.visible()
					mapViewClip?.visible()
					overlayView?.visible()
				}
			}

			mapViewClip?.translationY = min(0.0, top.toDouble()).toFloat()
			mapView?.view?.translationY = max(0.0, (-top / 2).toDouble()).toFloat()
			overlayView?.translationY = max(0.0, (-top / 2).toDouble()).toFloat()

			val translationY = min((overScrollHeight - mapTypeButton!!.measuredHeight - AndroidUtilities.dp((64 + (if (locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE) 30 else 10)).toFloat())).toDouble(), -top.toDouble()).toFloat()
			mapTypeButton?.translationY = translationY
			proximityButton?.translationY = translationY
			hintView?.setExtraTranslationY(translationY)
			searchAreaButton?.setTranslation(translationY)

			if (markerImageView != null) {
				markerImageView?.translationY = (-top - AndroidUtilities.dp((if (markerImageView?.tag == null) 48 else 69).toFloat()) + height / 2).also {
					markerTop = it
				}.toFloat()
			}

			if (!fromLayout) {
				layoutParams = mapView?.view?.layoutParams as? FrameLayout.LayoutParams

				if (layoutParams != null && layoutParams.height != overScrollHeight + AndroidUtilities.dp(10f)) {
					layoutParams.height = overScrollHeight + AndroidUtilities.dp(10f)
					map?.setPadding(AndroidUtilities.dp(70f), 0, AndroidUtilities.dp(70f), AndroidUtilities.dp(10f))
					mapView?.view?.layoutParams = layoutParams
				}

				if (overlayView != null) {
					layoutParams = overlayView?.layoutParams as? FrameLayout.LayoutParams

					if (layoutParams != null && layoutParams.height != overScrollHeight + AndroidUtilities.dp(10f)) {
						layoutParams.height = overScrollHeight + AndroidUtilities.dp(10f)
						overlayView?.layoutParams = layoutParams
					}
				}
			}
		}
	}

	private fun fixLayoutInternal(resume: Boolean) {
		val listView = listView ?: return
		val height = (if (actionBar!!.occupyStatusBar) AndroidUtilities.statusBarHeight else 0) + ActionBar.getCurrentActionBarHeight()
		val viewHeight = fragmentView?.measuredHeight ?: 0

		if (viewHeight == 0) {
			return
		}

		overScrollHeight = when (locationType) {
			LOCATION_TYPE_LIVE_VIEW -> {
				viewHeight - AndroidUtilities.dp(66f) - height
			}

			2 -> {
				viewHeight - AndroidUtilities.dp((66 + 7).toFloat()) - height
			}

			else -> {
				viewHeight - AndroidUtilities.dp(66f) - height
			}
		}

		var layoutParams = listView.layoutParams as? FrameLayout.LayoutParams
		layoutParams?.topMargin = height

		listView.layoutParams = layoutParams

		layoutParams = mapViewClip?.layoutParams as? FrameLayout.LayoutParams
		layoutParams?.topMargin = height
		layoutParams?.height = overScrollHeight

		mapViewClip?.layoutParams = layoutParams

		if (searchListView != null) {
			layoutParams = searchListView?.layoutParams as? FrameLayout.LayoutParams
			layoutParams?.topMargin = height

			searchListView?.layoutParams = layoutParams
		}

		adapter?.setOverScrollHeight(overScrollHeight)

		layoutParams = mapView?.view?.layoutParams as? FrameLayout.LayoutParams

		if (layoutParams != null) {
			layoutParams.height = overScrollHeight + AndroidUtilities.dp(10f)
			map?.setPadding(AndroidUtilities.dp(70f), 0, AndroidUtilities.dp(70f), AndroidUtilities.dp(10f))
			mapView?.view?.layoutParams = layoutParams
		}

		if (overlayView != null) {
			layoutParams = overlayView?.layoutParams as? FrameLayout.LayoutParams

			if (layoutParams != null) {
				layoutParams.height = overScrollHeight + AndroidUtilities.dp(10f)
				overlayView?.layoutParams = layoutParams
			}
		}

		adapter?.notifyDataSetChanged()

		if (resume) {
			val top = when (locationType) {
				3 -> 73
				1, 2 -> 66
				else -> 0
			}

			layoutManager?.scrollToPositionWithOffset(0, -AndroidUtilities.dp(top.toFloat()))

			updateClipView(false)

			listView.post {
				layoutManager?.scrollToPositionWithOffset(0, -AndroidUtilities.dp(top.toFloat()))
				updateClipView(false)
			}
		}
		else {
			updateClipView(false)
		}
	}

	@get:SuppressLint("MissingPermission")
	private val lastLocation: Location?
		get() {
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

		val liveLocation = markersMap[userConfig.getClientUserId()]
		val myInfo = locationController.getSharingLocationInfo(dialogId)

		if (liveLocation != null && myInfo != null && liveLocation.`object`?.id == myInfo.mid) {
			val latLng = IMapsProvider.LatLng(location.latitude, location.longitude)

			liveLocation.marker?.position = latLng
			liveLocation.directionMarker?.position = latLng
		}

		if (messageObject == null && chatLocation == null && map != null) {
			val latLng = IMapsProvider.LatLng(location.latitude, location.longitude)

			if (!searchedForCustomLocations && locationType != LOCATION_TYPE_GROUP) {
				adapter?.searchPlacesWithQuery(null, myLocation, true)
			}

			adapter?.setGpsLocation(myLocation)

			if (!userLocationMoved) {
				userLocation = Location(location)

				if (firstWas) {
					val position = ApplicationLoader.mapsProvider.newCameraUpdateLatLng(latLng)
					map?.animateCamera(position)
				}
				else {
					firstWas = true
					val position = ApplicationLoader.mapsProvider.newCameraUpdateLatLngZoom(latLng, map!!.maxZoomLevel - 4)
					map?.moveCamera(position)
				}
			}
		}
		else {
			adapter?.setGpsLocation(myLocation)
		}

		proximitySheet?.updateText(true, true)
		proximityCircle?.setCenter(IMapsProvider.LatLng(myLocation!!.latitude, myLocation!!.longitude))
	}

	fun setMessageObject(message: MessageObject?) {
		messageObject = message
		dialogId = messageObject?.dialogId ?: 0
	}

	fun setChatLocation(chatId: Long, location: TLRPC.TLChannelLocation?) {
		dialogId = -chatId
		chatLocation = location
	}

	fun setDialogId(did: Long) {
		dialogId = did
	}

	fun setInitialLocation(location: TLRPC.TLChannelLocation?) {
		initialLocation = location
	}

	init {
		AndroidUtilities.fixGoogleMapsBug()
	}

	private fun fetchRecentLocations(messages: List<Message>) {
		var builder: ILatLngBoundsBuilder? = null

		if (firstFocus) {
			builder = ApplicationLoader.mapsProvider.onCreateLatLngBoundsBuilder()
		}

		val date = connectionsManager.currentTime

		for (a in messages.indices) {
			val message = messages[a]

			if (message.date + (message.media?.period ?: 0) > date) {
				if (builder != null) {
					val latLng = IMapsProvider.LatLng(message.media?.geo?.lat, message.media?.geo?.lon)
					builder.include(latLng)
				}

				addUserMarker(message)

				if (proximityButton?.visibility != View.GONE && MessageObject.getFromChatId(message) != userConfig.getClientUserId()) {
					proximityButton?.visible()

					proximityAnimationInProgress = true

					proximityButton?.animate()?.alpha(1.0f)?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(180)?.setListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							proximityAnimationInProgress = false
							maybeShowProximityHint()
						}
					})?.start()
				}
			}
		}

		if (builder != null) {
			if (firstFocus) {
				listView?.smoothScrollBy(0, AndroidUtilities.dp(66 * 1.5f))
			}

			firstFocus = false

			adapter?.setLiveLocations(markers)

			if (messageObject?.isLiveLocation == true) {
				runCatching {
					var bounds = builder.build()
					val center = bounds.center
					val northEast = move(center, 100.0, 100.0)
					val southWest = move(center, -100.0, -100.0)

					builder.include(southWest)
					builder.include(northEast)

					bounds = builder.build()

					if (messages.size > 1) {
						try {
							moveToBounds = ApplicationLoader.mapsProvider.newCameraUpdateLatLngBounds(bounds, AndroidUtilities.dp((80 + 33).toFloat()))
							map?.moveCamera(moveToBounds)
							moveToBounds = null
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}
			}
		}
	}

	private fun moveToBounds(radius: Int, self: Boolean, animated: Boolean) {
		@Suppress("NAME_SHADOWING") var radius = radius

		val builder = ApplicationLoader.mapsProvider.onCreateLatLngBoundsBuilder()
		builder.include(IMapsProvider.LatLng(myLocation!!.latitude, myLocation!!.longitude))

		if (self) {
			runCatching {
				radius = max(radius.toDouble(), 250.0).toInt()

				var bounds = builder.build()
				val center = bounds.center
				val northEast = move(center, radius.toDouble(), radius.toDouble())
				val southWest = move(center, -radius.toDouble(), -radius.toDouble())

				builder.include(southWest)
				builder.include(northEast)

				bounds = builder.build()

				try {
					val height = (proximitySheet!!.customView.measuredHeight - AndroidUtilities.dp(40f) + mapViewClip!!.translationY).toInt()

					map?.setPadding(AndroidUtilities.dp(70f), 0, AndroidUtilities.dp(70f), height)

					if (animated) {
						map?.animateCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLngBounds(bounds, 0), 500, null)
					}
					else {
						map?.moveCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLngBounds(bounds, 0))
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
		else {
			val date = connectionsManager.currentTime

			for (marker in markers) {
				val message = marker.`object` ?: continue

				if (message.date + message.media!!.period > date) {
					val latLng = IMapsProvider.LatLng(message.media?.geo?.lat, message.media?.geo?.lon)
					builder.include(latLng)
				}
			}

			runCatching {
				var bounds = builder.build()
				val center = bounds.center
				val northEast = move(center, 100.0, 100.0)
				val southWest = move(center, -100.0, -100.0)

				builder.include(southWest)
				builder.include(northEast)

				bounds = builder.build()

				try {
					val height = proximitySheet!!.customView.measuredHeight - AndroidUtilities.dp(100f)

					map?.setPadding(AndroidUtilities.dp(70f), 0, AndroidUtilities.dp(70f), height)
					map?.moveCamera(ApplicationLoader.mapsProvider.newCameraUpdateLatLngBounds(bounds, 0))
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
	}

	private fun getRecentLocations(): Boolean {
		var messages = locationController.locationsCache[messageObject!!.dialogId]

		if (messages != null && messages.isEmpty()) {
			fetchRecentLocations(messages)
		}
		else {
			messages = null
		}

		if (DialogObject.isChatDialog(dialogId)) {
			val chat = messagesController.getChat(-dialogId)

			if (isChannel(chat) && !chat.megagroup) {
				return false
			}
		}

		val req = TLRPC.TLMessagesGetRecentLocations()
		val id = messageObject!!.dialogId
		req.peer = messagesController.getInputPeer(id)
		req.limit = 100

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TLRPC.MessagesMessages) {
				AndroidUtilities.runOnUIThread(Runnable {
					if (map == null) {
						return@Runnable
					}

					var a = 0

					while (a < response.messages.size) {
						if (response.messages[a].media !is TLRPC.TLMessageMediaGeoLive) {
							response.messages.removeAt(a)
							a--
						}

						a++
					}

					messagesStorage.putUsersAndChats(response.users, response.chats, true, true)

					messagesController.putUsers(response.users, false)
					messagesController.putChats(response.chats, false)

					locationController.locationsCache.put(id, response.messages)

					notificationCenter.postNotificationName(NotificationCenter.liveLocationsCacheChanged, id)

					fetchRecentLocations(response.messages)

					locationController.markLiveLocationsAsRead(dialogId)

					if (markAsReadRunnable == null) {
						markAsReadRunnable = Runnable {
							locationController.markLiveLocationsAsRead(dialogId)

							if (isPaused || markAsReadRunnable == null) {
								return@Runnable
							}

							AndroidUtilities.runOnUIThread(markAsReadRunnable, 5000)
						}

						AndroidUtilities.runOnUIThread(markAsReadRunnable, 5000)
					}
				})
			}
		}

		return messages != null
	}

	private fun bearingBetweenLocations(latLng1: IMapsProvider.LatLng, latLng2: IMapsProvider.LatLng): Double {
		val lat1 = latLng1.latitude * Math.PI / 180
		val long1 = latLng1.longitude * Math.PI / 180
		val lat2 = latLng2.latitude * Math.PI / 180
		val long2 = latLng2.longitude * Math.PI / 180
		val dLon = (long2 - long1)

		val y = sin(dLon) * cos(lat2)
		val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

		var brng = atan2(y, x)

		brng = Math.toDegrees(brng)
		brng = (brng + 360) % 360

		return brng
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.closeChats -> {
				removeSelfFromStack()
			}

			NotificationCenter.locationPermissionGranted -> {
				locationDenied = false

				adapter?.setMyLocationDenied(locationDenied)

				try {
					map?.setMyLocationEnabled(true)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			NotificationCenter.locationPermissionDenied -> {
				locationDenied = true
				adapter?.setMyLocationDenied(locationDenied)
			}

			NotificationCenter.liveLocationsChanged -> {
				adapter?.updateLiveLocationCell()
			}

			NotificationCenter.didReceiveNewMessages -> {
				val scheduled = args[2] as Boolean

				if (scheduled) {
					return
				}

				val did = args[0] as Long

				if (did != dialogId || messageObject == null) {
					return
				}

				val arr = args[1] as List<MessageObject>
				var added = false

				for (a in arr.indices) {
					val messageObject = arr[a]

					if (messageObject.isLiveLocation) {
						addUserMarker(messageObject.messageOwner!!)
						added = true
					}
					else if ((messageObject.messageOwner as? TLRPC.TLMessageService)?.action is TLRPC.TLMessageActionGeoProximityReached) {
						if (DialogObject.isUserDialog(messageObject.dialogId)) {
							proximityButton?.setImageResource(R.drawable.msg_location_alert)

							proximityCircle?.remove()
							proximityCircle = null
						}
					}
				}
				if (added) {
					adapter?.setLiveLocations(markers)
				}
			}

			NotificationCenter.replaceMessagesObjects -> {
				val did = args[0] as Long

				if (did != dialogId || messageObject == null) {
					return
				}

				var updated = false
				val messageObjects = args[1] as List<MessageObject>

				for (a in messageObjects.indices) {
					val messageObject = messageObjects[a]

					if (!messageObject.isLiveLocation) {
						continue
					}

					val liveLocation = markersMap[getMessageId(messageObject.messageOwner)]

					if (liveLocation != null) {
						val myInfo = locationController.getSharingLocationInfo(did)

						if (myInfo == null || myInfo.mid != messageObject.id) {
							liveLocation.`object` = messageObject.messageOwner

							val latLng = IMapsProvider.LatLng(messageObject.messageOwner?.media?.geo?.lat, messageObject.messageOwner?.media?.geo?.lon)

							liveLocation.marker!!.position = latLng

							if (liveLocation.directionMarker != null) {
								liveLocation.directionMarker!!.position = latLng

								if (messageObject.messageOwner!!.media!!.heading != 0) {
									liveLocation.directionMarker!!.setRotation(messageObject.messageOwner!!.media!!.heading)

									if (!liveLocation.hasRotation) {
										liveLocation.directionMarker!!.setIcon(R.drawable.map_pin_cone2)
										liveLocation.hasRotation = true
									}
								}
								else {
									if (liveLocation.hasRotation) {
										liveLocation.directionMarker!!.setRotation(0)
										liveLocation.directionMarker!!.setIcon(R.drawable.map_pin_circle)
										liveLocation.hasRotation = false
									}
								}
							}
						}

						updated = true
					}
				}

				if (updated) {
					adapter?.updateLiveLocations()
					proximitySheet?.updateText(true, true)
				}
			}
		}
	}

	override fun onPause() {
		super.onPause()

		if (mapView != null && mapsInitialized) {
			try {
				mapView?.onPause()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		undoView[0]?.hide(true, 0)

		onResumeCalled = false
	}

	override fun onBackPressed(): Boolean {
		if (proximitySheet != null) {
			proximitySheet?.dismiss()
			return false
		}

		if (onCheckGlScreenshot()) {
			return false
		}

		return super.onBackPressed()
	}

	override fun finishFragment(animated: Boolean) {
		if (onCheckGlScreenshot()) {
			return
		}

		super.finishFragment(animated)
	}

	private fun onCheckGlScreenshot(): Boolean {
		val glSurfaceView = mapView?.glSurfaceView

		if (glSurfaceView != null && !hasScreenshot) {
			glSurfaceView.queueEvent {
				if (glSurfaceView.width == 0 || glSurfaceView.height == 0) {
					return@queueEvent
				}

				val buffer = ByteBuffer.allocateDirect(glSurfaceView.width * glSurfaceView.height * 4)

				GLES20.glReadPixels(0, 0, glSurfaceView.width, glSurfaceView.height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)

				val bitmap = createBitmap(glSurfaceView.width, glSurfaceView.height)
				bitmap.copyPixelsFromBuffer(buffer)

				val flipVertically = Matrix()
				flipVertically.preScale(1f, -1f)

				val flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, flipVertically, false)

				bitmap.recycle()

				AndroidUtilities.runOnUIThread {
					val snapshotView = ImageView(context)
					snapshotView.setImageBitmap(flippedBitmap)

					val parent = glSurfaceView.parent as? ViewGroup

					if (parent != null) {
						try {
							parent.addView(snapshotView, parent.indexOfChild(glSurfaceView))
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					AndroidUtilities.runOnUIThread({
						try {
							parent?.removeView(glSurfaceView)
						}
						catch (e: Exception) {
							FileLog.e(e)
						}

						hasScreenshot = true

						finishFragment()
					}, 100)
				}
			}

			return true
		}

		return false
	}

	override fun onBecomeFullyHidden() {
		undoView[0]?.hide(true, 0)
	}

	override fun onResume() {
		super.onResume()

		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
		AndroidUtilities.removeAdjustResize(parentActivity, classGuid)

		if (mapView != null && mapsInitialized) {
			try {
				mapView?.onResume()
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		onResumeCalled = true

		if (map != null) {
			try {
				map?.setMyLocationEnabled(true)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		fixLayoutInternal(true)

		if (checkPermission) {
			val activity = parentActivity

			if (activity != null) {
				checkPermission = false

				if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
					activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 2)
				}
			}
		}

		if (markAsReadRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(markAsReadRunnable)
			AndroidUtilities.runOnUIThread(markAsReadRunnable, 5000)
		}
	}

	override fun onRequestPermissionsResultFragment(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		if (requestCode == ChatAttachAlert.REQUEST_CODE_LIVE_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
			openShareLiveLocation(askWithRadius)
		}
	}

	override fun hasForceLightStatusBar(): Boolean {
		return true
	}

	override fun onLowMemory() {
		super.onLowMemory()

		if (mapsInitialized) {
			mapView?.onLowMemory()
		}
	}

	fun setDelegate(delegate: LocationActivityDelegate?) {
		this.delegate = delegate
	}

	fun setChatActivity(chatActivity: ChatActivity?) {
		parentFragment = chatActivity
	}

	private fun updateSearchInterface() {
		adapter?.notifyDataSetChanged()
	}

	companion object {
		private const val open_in = 1
		private const val share_live_location = 5
		private const val map_list_menu_map = 2
		private const val map_list_menu_satellite = 3
		private const val map_list_menu_hybrid = 4

		const val LOCATION_TYPE_SEND = 0
		const val LOCATION_TYPE_SEND_WITH_LIVE = 1
		const val LOCATION_TYPE_GROUP = 4
		const val LOCATION_TYPE_GROUP_VIEW = 5
		const val LOCATION_TYPE_LIVE_VIEW = 6

		private const val EARTH_RADIUS = 6366198.0

		private fun move(startLL: IMapsProvider.LatLng, toNorth: Double, toEast: Double): IMapsProvider.LatLng {
			val lonDiff = meterToLongitude(toEast, startLL.latitude)
			val latDiff = meterToLatitude(toNorth)
			return IMapsProvider.LatLng(startLL.latitude + latDiff, startLL.longitude + lonDiff)
		}

		private fun meterToLongitude(meterToEast: Double, latitude: Double): Double {
			val latArc = Math.toRadians(latitude)
			val radius = cos(latArc) * EARTH_RADIUS
			val rad = meterToEast / radius
			return Math.toDegrees(rad)
		}

		private fun meterToLatitude(meterToNorth: Double): Double {
			val rad = meterToNorth / EARTH_RADIUS
			return Math.toDegrees(rad)
		}
	}
}
