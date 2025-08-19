/*
 * This is the source code of Telegram for Android v. 6.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui.Components

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.hardware.Camera
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewPropertyAnimator
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.Keep
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.scale
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ChatObject.hasAdminRights
import org.telegram.messenger.ChatObject.isActionBannedByDefault
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageReceiver.BitmapHolder
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaController.AlbumEntry
import org.telegram.messenger.MediaController.PhotoEntry
import org.telegram.messenger.MediaController.SearchImage
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper.Companion.createVideoThumbnail
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.VideoEditedInfo
import org.telegram.messenger.camera.CameraController
import org.telegram.messenger.camera.CameraView
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.BasePermissionsActivity
import org.telegram.ui.Cells.PhotoAttachCameraCell
import org.telegram.ui.Cells.PhotoAttachPermissionCell
import org.telegram.ui.Cells.PhotoAttachPhotoCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatAttachAlert.AttachAlertLayout
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.Components.RecyclerViewItemRangeSelector.RecyclerViewItemRangeSelectorDelegate
import org.telegram.ui.Components.ShutterButton.ShutterButtonDelegate
import org.telegram.ui.PhotoViewer
import org.telegram.ui.PhotoViewer.EmptyPhotoViewerProvider
import org.telegram.ui.PhotoViewer.PhotoViewerProvider
import org.telegram.ui.PhotoViewer.PlaceProviderObject
import org.telegram.ui.sales.CreateMediaSaleFragment
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
class ChatAttachAlertPhotoLayout(alert: ChatAttachAlert, context: Context, private var forceDarkTheme: Boolean) : AttachAlertLayout(alert, context), NotificationCenterDelegate {
	private val cameraPhotoRecyclerView: RecyclerListView
	private val cameraPhotoLayoutManager: LinearLayoutManager
	private val cameraAttachAdapter: PhotoAttachAdapter
	private val dropDownContainer: ActionBarMenuItem
	private val dropDownDrawable: Drawable
	private val layoutManager: GridLayoutManager
	private val adapter = PhotoAttachAdapter(context, true)
	private val progressView = EmptyTextProgressView(context, null)
	private val cameraDrawable: Drawable

	private val recordTime by lazy {
		val recordPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		recordPaint.color = -0x25a9b3

		@SuppressLint("AppCompatCustomView") object : TextView(context) {
			private var alpha = 0f
			var isIncr = false

			override fun onDraw(canvas: Canvas) {
				recordPaint.alpha = (125 + 130 * alpha).toInt()

				if (!isIncr) {
					alpha -= 16 / 600.0f

					if (alpha <= 0) {
						alpha = 0f
						isIncr = true
					}
				}
				else {
					alpha += 16 / 600.0f

					if (alpha >= 1) {
						alpha = 1f
						isIncr = false
					}
				}

				super.onDraw(canvas)

				canvas.drawCircle(AndroidUtilities.dp(14f).toFloat(), (measuredHeight / 2).toFloat(), AndroidUtilities.dp(4f).toFloat(), recordPaint)

				invalidate()
			}
		}
	}

	private val flashModeButton = arrayOfNulls<ImageView>(2)
	private val cameraViewLocation = FloatArray(2)
	private val viewPosition = IntArray(2)
	private val animateCameraValues = IntArray(5)
	private val interpolator = DecelerateInterpolator(1.5f)
	private val cameraPanel: FrameLayout
	private val shutterButton = ShutterButton(context)
	private val zoomControlView = ZoomControlView(context)
	private val counterTextView = TextView(context)
	private val tooltipTextView = TextView(context)
	private val switchCameraButton = ImageView(context)
	private val hitRect = Rect()
	val dropDown = TextView(context)

	val gridView: RecyclerListView by lazy {
		object : RecyclerListView(context) {
			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(e: MotionEvent): Boolean {
				return if (e.action == MotionEvent.ACTION_DOWN && e.y < parentAlert.scrollOffsetY[0] - AndroidUtilities.dp(80f)) {
					false
				}
				else {
					super.onTouchEvent(e)
				}
			}

			override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
				return if (e.action == MotionEvent.ACTION_DOWN && e.y < parentAlert.scrollOffsetY[0] - AndroidUtilities.dp(80f)) {
					false
				}
				else {
					super.onInterceptTouchEvent(e)
				}
			}

			override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
				super.onLayout(changed, l, t, r, b)
				PhotoViewer.getInstance().checkCurrentImageVisibility()
			}
		}
	}

	var cameraView: CameraView? = null
		private set

	var cameraIcon: FrameLayout? = null
		private set

	var cameraCell: PhotoAttachCameraCell? = null
		private set

	var cameraExpanded = false
	private var additionCloseCameraY = 0f
	var animationClipTop = 0f
	var animationClipBottom = 0f
	var animationClipRight = 0f
	var animationClipLeft = 0f

	private val itemRangeSelector: RecyclerViewItemRangeSelector by lazy {
		RecyclerViewItemRangeSelector(object : RecyclerViewItemRangeSelectorDelegate {
			override fun getItemCount(): Int {
				return adapter.itemCount
			}

			override fun setSelected(view: View, index: Int, selected: Boolean) {
				if (selected != shouldSelect || view !is PhotoAttachPhotoCell) {
					return
				}

				view.callDelegate()
			}

			override fun isSelected(index: Int): Boolean {
				val entry = adapter.getPhoto(index)
				return entry != null && Companion.selectedPhotos.containsKey(entry.imageId)
			}

			override fun isIndexSelectable(index: Int): Boolean {
				return adapter.getItemViewType(index) == 0
			}

			override fun onStartStopSelection(start: Boolean) {
				alertOnlyOnce = if (start) 1 else 0
				gridView.hideSelector(true)
			}
		})
	}

	private var gridExtraSpace = 0
	private var shouldSelect = false
	private var alertOnlyOnce = 0
	private var currentSelectedCount = 0
	private var isHidden = false
	private var cameraInitAnimation: AnimatorSet? = null
	private var flashAnimationInProgress = false
	private var cameraViewOffsetX = 0f
	private var cameraViewOffsetY = 0f
	private var cameraViewOffsetBottomY = 0f
	private var cameraOpened = false
	private var canSaveCameraPreview = false
	private var cameraAnimationInProgress = false
	private var cameraOpenProgress = 0f
	private var videoRecordTime = 0
	private var videoRecordRunnable: Runnable? = null
	private var zoomControlAnimation: AnimatorSet? = null
	private var zoomControlHideRunnable: Runnable? = null
	private var takingPhoto = false
	private var cancelTakingPhotos = false
	private var checkCameraWhenShown = false
	private var mediaEnabled = false
	private var pinchStartDistance = 0f
	private var cameraZoom = 0f
	private var zooming = false
	private var zoomWas = false
	private var lastY = 0f
	private var pressed = false
	private var maybeStartDragging = false
	private var dragging = false
	private var cameraPhotoRecyclerViewIgnoreLayout = false
	private var itemSize = AndroidUtilities.dp(80f)
	private var lastItemSize = itemSize
	private var itemsPerRow = 3
	private var deviceHasGoodCamera = false
	private var noCameraPermissions = false
	private var noGalleryPermissions = false
	private var requestingPermissions = false
	private var ignoreLayout = false
	private var lastNotifyWidth = 0
	private var selectedAlbumEntry: AlbumEntry? = null
	private var galleryAlbumEntry: AlbumEntry? = null
	private var dropDownAlbums: ArrayList<AlbumEntry>? = null
	private var currentPanTranslationY = 0f
	private var loading = true
	private var animationIndex = -1
	private var headerAnimator: ViewPropertyAnimator? = null

	private fun updateCheckedPhotoIndices() {
		if (parentAlert.baseFragment !is ChatActivity) {
			return
		}

		var count = gridView.childCount

		for (a in 0 until count) {
			val view = gridView.getChildAt(a)

			if (view is PhotoAttachPhotoCell) {
				val photoEntry = getPhotoEntryAtPosition(view.tag as Int)

				if (photoEntry != null) {
					view.setNum(Companion.selectedPhotosOrder.indexOf(photoEntry.imageId))
				}
			}
		}

		count = cameraPhotoRecyclerView.childCount

		for (a in 0 until count) {
			val view = cameraPhotoRecyclerView.getChildAt(a)

			if (view is PhotoAttachPhotoCell) {
				val photoEntry = getPhotoEntryAtPosition(view.tag as Int)

				if (photoEntry != null) {
					view.setNum(Companion.selectedPhotosOrder.indexOf(photoEntry.imageId))
				}
			}
		}
	}

	private fun getPhotoEntryAtPosition(position: Int): PhotoEntry? {
		@Suppress("NAME_SHADOWING") var position = position

		if (position < 0) {
			return null
		}

		val cameraCount = cameraPhotos.size

		if (position < cameraCount) {
			return cameraPhotos[position] as? PhotoEntry
		}

		position -= cameraCount

		return selectedAlbumEntry?.photos?.getOrNull(position)
	}

	private val photoViewerProvider: PhotoViewerProvider = object : BasePhotoProvider() {
		override fun onOpen() {
			pauseCameraPreview()
		}

		override fun onClose() {
			resumeCameraPreview()
		}

		override fun getPlaceForPhoto(messageObject: MessageObject?, fileLocation: FileLocation?, index: Int, needPreview: Boolean): PlaceProviderObject? {
			val cell = getCellForIndex(index)

			if (cell != null) {
				val coordinates = IntArray(2)

				cell.imageView.getLocationInWindow(coordinates)

				if (Build.VERSION.SDK_INT < 26) {
					coordinates[0] -= parentAlert.leftInset
				}

				val `object` = PlaceProviderObject()
				`object`.viewX = coordinates[0]
				`object`.viewY = coordinates[1]
				`object`.parentView = gridView
				`object`.imageReceiver = cell.imageView.imageReceiver
				`object`.thumb = `object`.imageReceiver.bitmapSafe
				`object`.scale = cell.scale
				`object`.clipBottomAddition = parentAlert.clipLayoutBottom.toInt()

				cell.showCheck(false)

				return `object`
			}

			return null
		}

		override fun updatePhotoAtIndex(index: Int) {
			val cell = getCellForIndex(index)

			if (cell != null) {
				cell.imageView.setOrientation(0, true)

				val photoEntry = getPhotoEntryAtPosition(index) ?: return

				if (photoEntry.thumbPath != null) {
					cell.imageView.setImage(photoEntry.thumbPath, null, Theme.chat_attachEmptyDrawable)
				}
				else if (photoEntry.path != null) {
					cell.imageView.setOrientation(photoEntry.orientation, true)

					if (photoEntry.isVideo) {
						cell.imageView.setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable)
					}
					else {
						cell.imageView.setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable)
					}
				}
				else {
					cell.imageView.setImageDrawable(Theme.chat_attachEmptyDrawable)
				}
			}
		}

		override fun getThumbForPhoto(messageObject: MessageObject?, fileLocation: FileLocation?, index: Int): BitmapHolder? {
			val cell = getCellForIndex(index)
			return cell?.imageView?.imageReceiver?.bitmapSafe
		}

		override fun willSwitchFromPhoto(messageObject: MessageObject?, fileLocation: FileLocation?, index: Int) {
			val cell = getCellForIndex(index)
			cell?.showCheck(true)
		}

		override fun willHidePhotoViewer() {
			val count = gridView.childCount

			for (a in 0 until count) {
				val view = gridView.getChildAt(a)

				if (view is PhotoAttachPhotoCell) {
					view.showCheck(true)
				}
			}
		}

		override fun onApplyCaption(caption: CharSequence) {
			if (Companion.selectedPhotos.size > 0 && Companion.selectedPhotosOrder.size > 0) {
				val o = Companion.selectedPhotos[Companion.selectedPhotosOrder[0]]
				var firstPhotoCaption: CharSequence? = null

				if (o is PhotoEntry) {
					firstPhotoCaption = o.caption
				}

				if (o is SearchImage) {
					firstPhotoCaption = o.caption
				}

				parentAlert.commentTextView.text = firstPhotoCaption
			}
		}

		override fun cancelButtonPressed(): Boolean {
			return false
		}

		override fun sendButtonPressed(index: Int, videoEditedInfo: VideoEditedInfo?, notify: Boolean, scheduleDate: Int, forceDocument: Boolean) {
			val photoEntry = getPhotoEntryAtPosition(index)

			if (photoEntry != null) {
				photoEntry.editedInfo = videoEditedInfo
			}

			if (Companion.selectedPhotos.isEmpty() && photoEntry != null) {
				addToSelectedPhotos(photoEntry, -1)
			}

			if (parentAlert.checkCaption(parentAlert.commentTextView.text)) {
				return
			}

			parentAlert.applyCaption()

			if (PhotoViewer.getInstance().hasCaptionForAllMedia) {
				val selectedPhotos = selectedPhotos
				val selectedPhotosOrder = selectedPhotosOrder

				if (selectedPhotos.isNotEmpty()) {
					for (a in selectedPhotosOrder.indices) {
						val o = selectedPhotos[selectedPhotosOrder[a]]

						if (o is PhotoEntry) {
							if (a == 0) {
								o.caption = PhotoViewer.getInstance().captionForAllMedia

								if (parentAlert.checkCaption(o.caption)) {
									return
								}
							}
							else {
								o.caption = null
							}
						}
					}
				}
			}

			parentAlert.chatAttachViewDelegate?.didPressedButton(7, true, notify, scheduleDate, forceDocument)
		}
	}

	init {
		NotificationCenter.globalInstance.let {
			it.addObserver(this, NotificationCenter.albumsDidLoad)
			it.addObserver(this, NotificationCenter.cameraInitied)
		}

		val container = alert.container

		cameraDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.instant_camera, null)!!.mutate()

		val menu = parentAlert.actionBar.createMenu()

		dropDownContainer = object : ActionBarMenuItem(context, menu, 0, 0) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)
				info.text = dropDown.text
			}
		}

		dropDownContainer.setSubMenuOpenSide(1)

		parentAlert.actionBar.addView(dropDownContainer, 0, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, (if (AndroidUtilities.isTablet()) 64 else 56).toFloat(), 0f, 40f, 0f))

		dropDownContainer.setOnClickListener {
			dropDownContainer.toggleSubMenu()
		}

		dropDown.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		dropDown.gravity = Gravity.LEFT
		dropDown.isSingleLine = true
		dropDown.setLines(1)
		dropDown.maxLines = 1
		dropDown.ellipsize = TextUtils.TruncateAt.END
		dropDown.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		dropDown.text = context.getString(R.string.ChatGallery)
		dropDown.typeface = Theme.TYPEFACE_BOLD

		dropDownDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_arrow_drop_down, null)!!.mutate()
		dropDownDrawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.text, null), PorterDuff.Mode.SRC_IN)

		dropDown.compoundDrawablePadding = AndroidUtilities.dp(4f)
		dropDown.setPadding(0, 0, AndroidUtilities.dp(10f), 0)

		dropDownContainer.addView(dropDown, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_VERTICAL, 16f, 0f, 0f, 0f))

		checkCamera(false)

		parentAlert.selectedMenuItem.addSubItem(group, context.getString(R.string.SendWithoutGrouping))
		parentAlert.selectedMenuItem.addSubItem(compress, context.getString(R.string.SendWithoutCompression))
		parentAlert.selectedMenuItem.addSubItem(open_in, R.drawable.msg_openin, context.getString(R.string.OpenInExternalApp))
		parentAlert.selectedMenuItem.addSubItem(preview, context.getString(R.string.AttachMediaPreviewButton))

		gridView.adapter = adapter

		adapter.createCache()

		gridView.clipToPadding = false
		gridView.itemAnimator = null
		gridView.layoutAnimation = null
		gridView.isVerticalScrollBarEnabled = false
		gridView.setGlowColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))

		addView(gridView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		gridView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				if (gridView.childCount <= 0) {
					return
				}

				parentAlert.updateLayout(this@ChatAttachAlertPhotoLayout, true, dy)

				if (dy != 0) {
					checkCameraViewPosition()
				}
			}

			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				if (newState == RecyclerView.SCROLL_STATE_IDLE) {
					val offset = AndroidUtilities.dp(13f) + AndroidUtilities.dp(parentAlert.selectedMenuItem.alpha * 26)
					val backgroundPaddingTop = parentAlert.backgroundPaddingTop
					val top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset

					if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
						val holder = gridView.findViewHolderForAdapterPosition(0) as? RecyclerListView.Holder

						if (holder != null && holder.itemView.top > AndroidUtilities.dp(7f)) {
							gridView.smoothScrollBy(0, holder.itemView.top - AndroidUtilities.dp(7f))
						}
					}
				}
			}
		})

		layoutManager = object : GridLayoutManager(context, itemSize) {
			override fun supportsPredictiveItemAnimations(): Boolean {
				return false
			}

			override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
				val linearSmoothScroller: LinearSmoothScroller = object : LinearSmoothScroller(recyclerView.context) {
					override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
						var dy = super.calculateDyToMakeVisible(view, snapPreference)
						dy -= gridView.paddingTop - AndroidUtilities.dp(7f)
						return dy
					}

					override fun calculateTimeForDeceleration(dx: Int): Int {
						return super.calculateTimeForDeceleration(dx) * 2
					}
				}

				linearSmoothScroller.targetPosition = position

				startSmoothScroll(linearSmoothScroller)
			}
		}

		layoutManager.setSpanSizeLookup(object : SpanSizeLookup() {
			override fun getSpanSize(position: Int): Int {
				return if (position == adapter.itemsCount - 1) {
					layoutManager.getSpanCount()
				}
				else {
					itemSize + if (position % itemsPerRow != itemsPerRow - 1) AndroidUtilities.dp(5f) else 0
				}
			}
		})

		gridView.layoutManager = layoutManager

		gridView.setOnItemClickListener { _, position ->
			@Suppress("NAME_SHADOWING") var position = position

			if (!mediaEnabled || parentAlert.baseFragment.parentActivity == null) {
				return@setOnItemClickListener
			}

			if (adapter.needCamera && selectedAlbumEntry === galleryAlbumEntry && position == 0 && noCameraPermissions) {
				runCatching {
					parentAlert.baseFragment.parentActivity?.requestPermissions(arrayOf(Manifest.permission.CAMERA), 18)
				}

				return@setOnItemClickListener
			}
			else if (noGalleryPermissions) {
				val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
					Manifest.permission.READ_MEDIA_IMAGES
				}
				else {
					Manifest.permission.READ_EXTERNAL_STORAGE
				}

				runCatching {
					parentAlert.baseFragment.parentActivity!!.requestPermissions(arrayOf(permission), BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE)
				}

				return@setOnItemClickListener
			}

			if (position != 0 || selectedAlbumEntry !== galleryAlbumEntry) {
				if (selectedAlbumEntry === galleryAlbumEntry) {
					position--
				}

				val arrayList = allPhotosArray

				if (position < 0 || position >= arrayList.size) {
					return@setOnItemClickListener
				}

				PhotoViewer.getInstance().let {
					it.setParentActivity(parentAlert.baseFragment)
					it.setParentAlert(parentAlert)
					it.setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder)
				}

				val chatActivity: ChatActivity?
				val type: Int

				if (parentAlert.avatarPicker != 0) {
					chatActivity = null
					type = PhotoViewer.SELECT_TYPE_AVATAR
				}
				else if (parentAlert.baseFragment is ChatActivity) {
					chatActivity = parentAlert.baseFragment
					type = 0
				}
				else {
					chatActivity = null
					type = 4
				}

				if (parentAlert.chatAttachViewDelegate?.needEnterComment() != true) {
					AndroidUtilities.hideKeyboard(parentAlert.baseFragment.fragmentView!!.findFocus())
					AndroidUtilities.hideKeyboard(parentAlert.container.findFocus())
				}

				if (Companion.selectedPhotos.size > 0 && Companion.selectedPhotosOrder.size > 0) {
					val o = Companion.selectedPhotos[Companion.selectedPhotosOrder[0]]

					if (o is PhotoEntry) {
						o.caption = parentAlert.commentTextView.text
					}

					if (o is SearchImage) {
						o.caption = parentAlert.commentTextView.text
					}
				}

				PhotoViewer.getInstance().openPhotoForSelect(arrayList, position, type, false, photoViewerProvider, chatActivity, parentAlert.baseFragment is CreateMediaSaleFragment)

				if (captionForAllMedia()) {
					PhotoViewer.getInstance().setCaption(parentAlert.commentTextView.text)
				}
			}
			else {
				if (SharedConfig.inappCamera) {
					openCamera(true)
				}
				else {
					parentAlert.chatAttachViewDelegate?.didPressedButton(0, arg = false, notify = true, scheduleDate = 0, forceDocument = false)
				}
			}
		}

		gridView.setOnItemLongClickListener { view, position ->
			if (position == 0 && selectedAlbumEntry === galleryAlbumEntry) {
				parentAlert.chatAttachViewDelegate?.didPressedButton(0, arg = false, notify = true, scheduleDate = 0, forceDocument = false)
				return@setOnItemLongClickListener true
			}
			else if (view is PhotoAttachPhotoCell) {
				itemRangeSelector.setIsActive(view, true, position, !view.isChecked.also { shouldSelect = it })
			}

			false
		}

		gridView.addOnItemTouchListener(itemRangeSelector)

		progressView.setText(context.getString(R.string.NoPhotos))
		progressView.setOnTouchListener(null)
		progressView.setTextSize(16)

		addView(progressView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

		if (loading) {
			progressView.showProgress()
		}
		else {
			progressView.showTextView()
		}

		AndroidUtilities.updateViewVisibilityAnimated(recordTime, false, 1f, false)

		recordTime.setBackgroundResource(R.drawable.system)
		recordTime.background.colorFilter = PorterDuffColorFilter(0x66000000, PorterDuff.Mode.MULTIPLY)
		recordTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		recordTime.typeface = Theme.TYPEFACE_BOLD
		recordTime.alpha = 0.0f
		recordTime.setTextColor(-0x1)
		recordTime.setPadding(AndroidUtilities.dp(24f), AndroidUtilities.dp(5f), AndroidUtilities.dp(10f), AndroidUtilities.dp(5f))

		container.addView(recordTime, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0f, 16f, 0f, 0f))

		cameraPanel = object : FrameLayout(context) {
			override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
				val cx: Int
				val cy: Int
				val cx2: Int
				val cy2: Int
				val cx3: Int
				val cy3: Int

				if (measuredWidth == AndroidUtilities.dp(126f)) {
					cx = measuredWidth / 2
					cy = measuredHeight / 2
					cx2 = measuredWidth / 2
					cx3 = cx2
					cy2 = cy + cy / 2 + AndroidUtilities.dp(17f)
					cy3 = cy / 2 - AndroidUtilities.dp(17f)
				}
				else {
					cx = measuredWidth / 2
					cy = measuredHeight / 2 - AndroidUtilities.dp(13f)
					cx2 = cx + cx / 2 + AndroidUtilities.dp(17f)
					cx3 = cx / 2 - AndroidUtilities.dp(17f)
					cy2 = measuredHeight / 2 - AndroidUtilities.dp(13f)
					cy3 = cy2
				}

				val y = measuredHeight - tooltipTextView.measuredHeight - AndroidUtilities.dp(12f)

				if (measuredWidth == AndroidUtilities.dp(126f)) {
					tooltipTextView.layout(cx - tooltipTextView.measuredWidth / 2, measuredHeight, cx + tooltipTextView.measuredWidth / 2, measuredHeight + tooltipTextView.measuredHeight)
				}
				else {
					tooltipTextView.layout(cx - tooltipTextView.measuredWidth / 2, y, cx + tooltipTextView.measuredWidth / 2, y + tooltipTextView.measuredHeight)
				}

				shutterButton.layout(cx - shutterButton.measuredWidth / 2, cy - shutterButton.measuredHeight / 2, cx + shutterButton.measuredWidth / 2, cy + shutterButton.measuredHeight / 2)
				switchCameraButton.layout(cx2 - switchCameraButton.measuredWidth / 2, cy2 - switchCameraButton.measuredHeight / 2, cx2 + switchCameraButton.measuredWidth / 2, cy2 + switchCameraButton.measuredHeight / 2)

				for (a in 0..1) {
					flashModeButton[a]!!.layout(cx3 - flashModeButton[a]!!.measuredWidth / 2, cy3 - flashModeButton[a]!!.measuredHeight / 2, cx3 + flashModeButton[a]!!.measuredWidth / 2, cy3 + flashModeButton[a]!!.measuredHeight / 2)
				}
			}
		}

		cameraPanel.setVisibility(GONE)
		cameraPanel.setAlpha(0.0f)

		container.addView(cameraPanel, createFrame(LayoutHelper.MATCH_PARENT, 126, Gravity.LEFT or Gravity.BOTTOM))

		counterTextView.setBackgroundResource(R.drawable.photos_rounded)
		counterTextView.visibility = GONE
		counterTextView.setTextColor(-0x1)
		counterTextView.gravity = Gravity.CENTER
		counterTextView.pivotX = 0f
		counterTextView.pivotY = 0f
		counterTextView.typeface = Theme.TYPEFACE_BOLD
		counterTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.photos_arrow, 0)
		counterTextView.compoundDrawablePadding = AndroidUtilities.dp(4f)
		counterTextView.setPadding(AndroidUtilities.dp(16f), 0, AndroidUtilities.dp(16f), 0)

		container.addView(counterTextView, createFrame(LayoutHelper.WRAP_CONTENT, 38f, Gravity.LEFT or Gravity.TOP, 0f, 0f, 0f, (100 + 16).toFloat()))

		counterTextView.setOnClickListener {
			if (cameraView == null) {
				return@setOnClickListener
			}

			openPhotoViewer(null, sameTakePictureOrientation = false, external = false)

			CameraController.getInstance().stopPreview(cameraView!!.cameraSession)
		}

		zoomControlView.visibility = GONE
		zoomControlView.alpha = 0.0f

		container.addView(zoomControlView, createFrame(LayoutHelper.WRAP_CONTENT, 50f, Gravity.LEFT or Gravity.TOP, 0f, 0f, 0f, (100 + 16).toFloat()))

		zoomControlView.setDelegate {
			cameraView?.setZoom(it.also { cameraZoom = it })
			showZoomControls(show = true)
		}

		cameraPanel.addView(shutterButton, createFrame(84, 84, Gravity.CENTER))

		shutterButton.delegate = object : ShutterButtonDelegate {
			private var outputFile: File? = null
			private var zoomingWas = false

			override fun shutterLongPressed(): Boolean {
				if (parentAlert.avatarPicker != 2 && parentAlert.baseFragment !is ChatActivity || takingPhoto || parentAlert.baseFragment.parentActivity == null || cameraView == null) {
					return false
				}

				if (parentAlert.baseFragment.parentActivity?.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
					requestingPermissions = true
					parentAlert.baseFragment.parentActivity?.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 21)
					return false
				}

				for (a in 0..1) {
					flashModeButton[a]?.animate()?.alpha(0f)?.translationX(AndroidUtilities.dp(30f).toFloat())?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
				}

				switchCameraButton.animate().alpha(0f).translationX(-AndroidUtilities.dp(30f).toFloat()).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start()

				tooltipTextView.animate().alpha(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start()

				outputFile = AndroidUtilities.generateVideoPath(parentAlert.baseFragment is ChatActivity && parentAlert.baseFragment.isSecretChat)

				AndroidUtilities.updateViewVisibilityAnimated(recordTime, true)

				recordTime.text = AndroidUtilities.formatLongDuration(0)

				videoRecordTime = 0

				videoRecordRunnable = Runnable {
					if (videoRecordRunnable == null) {
						return@Runnable
					}

					videoRecordTime++

					recordTime.text = AndroidUtilities.formatLongDuration(videoRecordTime)

					AndroidUtilities.runOnUIThread(videoRecordRunnable, 1000)
				}

				AndroidUtilities.lockOrientation(parentAlert.baseFragment.parentActivity)

				CameraController.getInstance().recordVideo(cameraView!!.cameraSession, outputFile, parentAlert.avatarPicker != 0, { thumbPath, duration ->
					val outputFile = outputFile ?: return@recordVideo
					val cameraView = cameraView ?: return@recordVideo

					mediaFromExternalCamera = false

					var width = 0
					var height = 0

					runCatching {
						val options = BitmapFactory.Options()
						options.inJustDecodeBounds = true
						BitmapFactory.decodeFile(File(thumbPath).absolutePath, options)
						width = options.outWidth
						height = options.outHeight
					}

					val photoEntry = PhotoEntry(0, lastImageId--, 0, outputFile.absolutePath, 0, true, width, height, 0)
					photoEntry.duration = duration.toInt()
					photoEntry.thumbPath = thumbPath

					if (parentAlert.avatarPicker != 0 && cameraView.isFrontface) {
						photoEntry.cropState = MediaController.CropState()
						photoEntry.cropState.mirrored = true
						photoEntry.cropState.freeform = false
						photoEntry.cropState.lockedAspectRatio = 1.0f
					}

					openPhotoViewer(photoEntry, sameTakePictureOrientation = false, external = false)
				}, {
					AndroidUtilities.runOnUIThread(videoRecordRunnable, 1000)
				}, cameraView)

				shutterButton.setState(ShutterButton.State.RECORDING, true)

				cameraView?.runHaptic()

				return true
			}

			override fun shutterCancel() {
				outputFile?.delete()
				outputFile = null
				resetRecordState()
				CameraController.getInstance().stopVideoRecording(cameraView?.cameraSession, true)
			}

			override fun shutterReleased() {
				if (takingPhoto || cameraView == null || cameraView?.cameraSession == null) {
					return
				}

				if (shutterButton.state == ShutterButton.State.RECORDING) {
					resetRecordState()
					CameraController.getInstance().stopVideoRecording(cameraView?.cameraSession, false)
					shutterButton.setState(ShutterButton.State.DEFAULT, true)
					return
				}

				val cameraFile = AndroidUtilities.generatePicturePath(parentAlert.baseFragment is ChatActivity && parentAlert.baseFragment.isSecretChat, null)
				val sameTakePictureOrientation = cameraView!!.cameraSession.isSameTakePictureOrientation

				cameraView?.cameraSession?.isFlipFront = parentAlert.baseFragment is ChatActivity || parentAlert.avatarPicker == 2

				takingPhoto = CameraController.getInstance().takePicture(cameraFile, cameraView!!.cameraSession) {
					takingPhoto = false

					if (cameraFile == null) {
						return@takePicture
					}

					var orientation = 0

					try {
						val ei = ExifInterface(cameraFile.absolutePath)

						when (ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
							ExifInterface.ORIENTATION_ROTATE_90 -> orientation = 90
							ExifInterface.ORIENTATION_ROTATE_180 -> orientation = 180
							ExifInterface.ORIENTATION_ROTATE_270 -> orientation = 270
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					mediaFromExternalCamera = false

					var width = 0
					var height = 0

					runCatching {
						val options = BitmapFactory.Options()
						options.inJustDecodeBounds = true
						BitmapFactory.decodeFile(File(cameraFile.absolutePath).absolutePath, options)
						width = options.outWidth
						height = options.outHeight
					}

					val photoEntry = PhotoEntry(0, lastImageId--, 0, cameraFile.absolutePath, orientation, false, width, height, 0)
					photoEntry.canDeleteAfter = true

					openPhotoViewer(photoEntry, sameTakePictureOrientation, false)
				}

				cameraView?.startTakePictureAnimation()
			}

			override fun onTranslationChanged(x: Float, y: Float): Boolean {
				val isPortrait = container.width < container.height
				val val1 = if (isPortrait) x else y
				val val2 = if (isPortrait) y else x

				if (!zoomingWas && abs(val1) > abs(val2)) {
					return zoomControlView.tag == null
				}

				if (val2 < 0) {
					showZoomControls(show = true)
					zoomControlView.setZoom(-val2 / AndroidUtilities.dp(200f), true)
					zoomingWas = true
					return false
				}

				if (zoomingWas) {
					zoomControlView.setZoom(0f, true)
				}

				if (x == 0f && y == 0f) {
					zoomingWas = false
				}

				return !zoomingWas && (x != 0f || y != 0f)
			}
		}

		shutterButton.isFocusable = true
		shutterButton.contentDescription = context.getString(R.string.AccDescrShutter)

		switchCameraButton.scaleType = ImageView.ScaleType.CENTER

		cameraPanel.addView(switchCameraButton, createFrame(48, 48, Gravity.RIGHT or Gravity.CENTER_VERTICAL))

		switchCameraButton.setOnClickListener {
			if (takingPhoto || cameraView == null || cameraView?.isInited != true) {
				return@setOnClickListener
			}

			canSaveCameraPreview = false

			cameraView?.switchCamera()
			cameraView?.startSwitchingAnimation()

			val animator = ObjectAnimator.ofFloat(switchCameraButton, SCALE_X, 0.0f).setDuration(100)

			animator.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animator: Animator) {
					switchCameraButton.setImageResource(if (cameraView?.isFrontface == true) R.drawable.camera_revert1 else R.drawable.camera_revert2)
					ObjectAnimator.ofFloat(switchCameraButton, SCALE_X, 1.0f).setDuration(100).start()
				}
			})

			animator.start()
		}

		switchCameraButton.contentDescription = context.getString(R.string.AccDescrSwitchCamera)

		for (a in 0..1) {
			flashModeButton[a] = ImageView(context)
			flashModeButton[a]?.scaleType = ImageView.ScaleType.CENTER
			flashModeButton[a]?.visibility = INVISIBLE

			cameraPanel.addView(flashModeButton[a], createFrame(48, 48, Gravity.LEFT or Gravity.TOP))

			flashModeButton[a]?.setOnClickListener { currentImage ->
				val cameraView = cameraView ?: return@setOnClickListener
				if (flashAnimationInProgress || !cameraView.isInited || !cameraOpened) {
					return@setOnClickListener
				}

				val current = cameraView.cameraSession.currentFlashMode
				val next = cameraView.cameraSession.nextFlashMode

				if (current == next) {
					return@setOnClickListener
				}

				cameraView.cameraSession.currentFlashMode = next

				flashAnimationInProgress = true

				val nextImage = if (flashModeButton[0] === currentImage) flashModeButton[1] else flashModeButton[0]
				nextImage?.visibility = VISIBLE

				setCameraFlashModeIcon(nextImage, next)

				val animatorSet = AnimatorSet()
				animatorSet.playTogether(ObjectAnimator.ofFloat(currentImage, TRANSLATION_Y, 0f, AndroidUtilities.dp(48f).toFloat()), ObjectAnimator.ofFloat(nextImage, TRANSLATION_Y, -AndroidUtilities.dp(48f).toFloat(), 0f), ObjectAnimator.ofFloat(currentImage, ALPHA, 1.0f, 0.0f), ObjectAnimator.ofFloat(nextImage, ALPHA, 0.0f, 1.0f))
				animatorSet.duration = 220
				animatorSet.interpolator = CubicBezierInterpolator.DEFAULT

				animatorSet.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animator: Animator) {
						flashAnimationInProgress = false
						currentImage.visibility = INVISIBLE
						nextImage?.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
					}
				})

				animatorSet.start()
			}

			flashModeButton[a]?.contentDescription = context.getString(R.string.flash_mode_format, a)
		}

		tooltipTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		tooltipTextView.setTextColor(-0x1)
		tooltipTextView.text = context.getString(R.string.TapForVideo)
		tooltipTextView.setShadowLayer(AndroidUtilities.dp(3.33333f).toFloat(), 0f, AndroidUtilities.dp(0.666f).toFloat(), 0x4c000000)
		tooltipTextView.setPadding(AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(6f), 0)

		cameraPanel.addView(tooltipTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0f, 0f, 0f, 16f))

		cameraPhotoRecyclerView = object : RecyclerListView(context) {
			override fun requestLayout() {
				if (cameraPhotoRecyclerViewIgnoreLayout) {
					return
				}

				super.requestLayout()
			}
		}


		cameraAttachAdapter = PhotoAttachAdapter(context, false)

		cameraPhotoRecyclerView.setVerticalScrollBarEnabled(true)
		cameraPhotoRecyclerView.setAdapter(cameraAttachAdapter)

		cameraAttachAdapter.createCache()

		cameraPhotoRecyclerView.setClipToPadding(false)
		cameraPhotoRecyclerView.setPadding(AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f), 0)
		cameraPhotoRecyclerView.setItemAnimator(null)
		cameraPhotoRecyclerView.setLayoutAnimation(null)
		cameraPhotoRecyclerView.setOverScrollMode(OVER_SCROLL_NEVER)
		cameraPhotoRecyclerView.setVisibility(INVISIBLE)
		cameraPhotoRecyclerView.setAlpha(0.0f)

		container.addView(cameraPhotoRecyclerView, createFrame(LayoutHelper.MATCH_PARENT, 80f))

		cameraPhotoLayoutManager = object : LinearLayoutManager(context, HORIZONTAL, false) {
			override fun supportsPredictiveItemAnimations(): Boolean {
				return false
			}
		}

		cameraPhotoRecyclerView.setLayoutManager(cameraPhotoLayoutManager)

		cameraPhotoRecyclerView.setOnItemClickListener { view, _ ->
			if (view is PhotoAttachPhotoCell) {
				view.callDelegate()
			}
		}
	}

	private val allPhotosArray: ArrayList<Any?>
		get() {
			val arrayList: ArrayList<Any?>
			val selectedAlbumEntry = selectedAlbumEntry

			if (selectedAlbumEntry != null) {
				if (cameraPhotos.isNotEmpty()) {
					arrayList = ArrayList(selectedAlbumEntry.photos.size + cameraPhotos.size)
					arrayList.addAll(cameraPhotos)
					arrayList.addAll(selectedAlbumEntry.photos)
				}
				else {
					arrayList = selectedAlbumEntry.photos as ArrayList<Any?>
				}
			}
			else if (cameraPhotos.isNotEmpty()) {
				arrayList = cameraPhotos
			}
			else {
				arrayList = ArrayList(0)
			}

			return arrayList
		}

	private fun addToSelectedPhotos(`object`: PhotoEntry, index: Int): Int {
		val key: Any = `object`.imageId

		return if (Companion.selectedPhotos.containsKey(key)) {
			Companion.selectedPhotos.remove(key)

			val position = Companion.selectedPhotosOrder.indexOf(key)

			if (position >= 0) {
				Companion.selectedPhotosOrder.removeAt(position)
			}

			updatePhotosCounter(false)
			updateCheckedPhotoIndices()

			if (index >= 0) {
				`object`.reset()
				photoViewerProvider.updatePhotoAtIndex(index)
			}

			position
		}
		else {
			Companion.selectedPhotos[key] = `object`
			Companion.selectedPhotosOrder.add(key)
			updatePhotosCounter(true)
			-1
		}
	}

	private fun clearSelectedPhotos() {
		if (Companion.selectedPhotos.isNotEmpty()) {
			for ((_, value) in Companion.selectedPhotos) {
				val photoEntry = value as PhotoEntry
				photoEntry.reset()
			}

			Companion.selectedPhotos.clear()
			Companion.selectedPhotosOrder.clear()
		}

		for (photoEntry in cameraPhotos) {
			if (photoEntry !is PhotoEntry) {
				continue
			}

			File(photoEntry.path).delete()

			if (photoEntry.imagePath != null) {
				File(photoEntry.imagePath).delete()
			}

			if (photoEntry.thumbPath != null) {
				File(photoEntry.thumbPath).delete()
			}
		}

		cameraPhotos.clear()

		adapter.notifyDataSetChanged()
		cameraAttachAdapter.notifyDataSetChanged()
	}

	private fun updateAlbumsDropDown() {
		dropDownContainer.removeAllSubItems()

		if (mediaEnabled) {
			val albums = if (parentAlert.baseFragment is ChatActivity || parentAlert.avatarPicker == 2) {
				MediaController.allMediaAlbums
			}
			else {
				MediaController.allPhotoAlbums
			}

			dropDownAlbums = ArrayList(albums)

			dropDownAlbums?.sortWith { o1, o2 ->
				if (o1.bucketId == 0 && o2.bucketId != 0) {
					return@sortWith -1
				}
				else if (o1.bucketId != 0 && o2.bucketId == 0) {
					return@sortWith 1
				}

				val index1 = albums.indexOf(o1)
				val index2 = albums.indexOf(o2)

				if (index1 > index2) {
					return@sortWith 1
				}
				else if (index1 < index2) {
					return@sortWith -1
				}
				else {
					return@sortWith 0
				}
			}
		}
		else {
			dropDownAlbums = ArrayList()
		}

		if (dropDownAlbums.isNullOrEmpty()) {
			dropDown.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
		}
		else {
			dropDown.setCompoundDrawablesWithIntrinsicBounds(null, null, dropDownDrawable, null)

			dropDownAlbums?.forEachIndexed { index, albumEntry ->
				dropDownContainer.addSubItem(10 + index, albumEntry.bucketName)
			}
		}
	}

	private fun processTouchEvent(event: MotionEvent?): Boolean {
		if (event == null) {
			return false
		}

		if (!pressed && event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
			zoomControlView.getHitRect(hitRect)

			if (zoomControlView.tag != null && hitRect.contains(event.x.toInt(), event.y.toInt())) {
				return false
			}

			if (!takingPhoto && !dragging) {
				if (event.pointerCount == 2) {
					pinchStartDistance = hypot((event.getX(1) - event.getX(0)).toDouble(), (event.getY(1) - event.getY(0)).toDouble()).toFloat()
					zooming = true
				}
				else {
					maybeStartDragging = true
					lastY = event.y
					zooming = false
				}
				zoomWas = false
				pressed = true
			}
		}
		else if (pressed) {
			if (event.actionMasked == MotionEvent.ACTION_MOVE) {
				if (zooming && event.pointerCount == 2 && !dragging) {
					val newDistance = hypot((event.getX(1) - event.getX(0)).toDouble(), (event.getY(1) - event.getY(0)).toDouble()).toFloat()

					if (!zoomWas) {
						if (abs(newDistance - pinchStartDistance) >= AndroidUtilities.getPixelsInCM(0.4f, false)) {
							pinchStartDistance = newDistance
							zoomWas = true
						}
					}
					else {
						if (cameraView != null) {
							val diff = (newDistance - pinchStartDistance) / AndroidUtilities.dp(100f)

							pinchStartDistance = newDistance
							cameraZoom += diff

							if (cameraZoom < 0.0f) {
								cameraZoom = 0.0f
							}
							else if (cameraZoom > 1.0f) {
								cameraZoom = 1.0f
							}

							zoomControlView.setZoom(cameraZoom, false)
							parentAlert.sheetContainer.invalidate()
							cameraView?.setZoom(cameraZoom)
							showZoomControls(show = true)
						}
					}
				}
				else {
					val newY = event.y
					val dy = newY - lastY

					if (maybeStartDragging) {
						if (abs(dy) > AndroidUtilities.getPixelsInCM(0.4f, false)) {
							maybeStartDragging = false
							dragging = true
						}
					}
					else if (dragging) {
						cameraView?.let { cameraView ->
							cameraView.translationY += dy
							lastY = newY
							zoomControlView.tag = null

							if (zoomControlHideRunnable != null) {
								AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable)
								zoomControlHideRunnable = null
							}
							if (cameraPanel.tag == null) {
								cameraPanel.tag = 1

								val animatorSet = AnimatorSet()
								animatorSet.playTogether(ObjectAnimator.ofFloat(cameraPanel, ALPHA, 0.0f), ObjectAnimator.ofFloat(zoomControlView, ALPHA, 0.0f), ObjectAnimator.ofFloat(counterTextView, ALPHA, 0.0f), ObjectAnimator.ofFloat(flashModeButton[0], ALPHA, 0.0f), ObjectAnimator.ofFloat(flashModeButton[1], ALPHA, 0.0f), ObjectAnimator.ofFloat(cameraPhotoRecyclerView, ALPHA, 0.0f))
								animatorSet.duration = 220
								animatorSet.interpolator = CubicBezierInterpolator.DEFAULT
								animatorSet.start()
							}
						}
					}
				}
			}
			else if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
				pressed = false
				// zooming = false

				if (zooming) {
					zooming = false
				}
				else if (dragging) {
					dragging = false

					cameraView?.let { cameraView ->
						if (abs(cameraView.translationY) > cameraView.measuredHeight / 6.0f) {
							closeCamera(true)
						}
						else {
							val animatorSet = AnimatorSet()
							animatorSet.playTogether(ObjectAnimator.ofFloat(cameraView, TRANSLATION_Y, 0.0f), ObjectAnimator.ofFloat(cameraPanel, ALPHA, 1.0f), ObjectAnimator.ofFloat(counterTextView, ALPHA, 1.0f), ObjectAnimator.ofFloat(flashModeButton[0], ALPHA, 1.0f), ObjectAnimator.ofFloat(flashModeButton[1], ALPHA, 1.0f), ObjectAnimator.ofFloat(cameraPhotoRecyclerView, ALPHA, 1.0f))
							animatorSet.duration = 250
							animatorSet.interpolator = interpolator
							animatorSet.start()

							cameraPanel.tag = null
						}
					}
				}
				else if (cameraView != null && !zoomWas) {
					cameraView?.getLocationOnScreen(viewPosition)
					val viewX = event.rawX - viewPosition[0]
					val viewY = event.rawY - viewPosition[1]
					cameraView?.focusToPoint(viewX.toInt(), viewY.toInt())
				}
			}
		}

		return true
	}

	private fun resetRecordState() {
		for (a in 0..1) {
			flashModeButton[a]!!.animate().alpha(1f).translationX(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start()
		}

		switchCameraButton.animate().alpha(1f).translationX(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start()

		tooltipTextView.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start()

		AndroidUtilities.updateViewVisibilityAnimated(recordTime, false)
		AndroidUtilities.cancelRunOnUIThread(videoRecordRunnable)

		videoRecordRunnable = null

		AndroidUtilities.unlockOrientation(parentAlert.baseFragment.parentActivity)
	}

	private fun openPhotoViewer(entry: PhotoEntry?, sameTakePictureOrientation: Boolean, external: Boolean) {
		if (entry != null) {
			cameraPhotos.add(entry)

			Companion.selectedPhotos[entry.imageId] = entry
			Companion.selectedPhotosOrder.add(entry.imageId)

			parentAlert.updateCountButton(0)

			adapter.notifyDataSetChanged()
			cameraAttachAdapter.notifyDataSetChanged()
		}

		if (entry != null && !external && cameraPhotos.size > 1) {
			updatePhotosCounter(false)

			if (cameraView != null) {
				zoomControlView.setZoom(0.0f, false)
				cameraZoom = 0.0f
				cameraView?.setZoom(0.0f)
				CameraController.getInstance().startPreview(cameraView?.cameraSession)
			}

			return
		}

		if (cameraPhotos.isEmpty()) {
			return
		}

		cancelTakingPhotos = true

		PhotoViewer.getInstance().setParentActivity(parentAlert.baseFragment.parentActivity)
		PhotoViewer.getInstance().setParentAlert(parentAlert)
		PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder)

		val chatActivity: ChatActivity?
		val type: Int

		if (parentAlert.avatarPicker != 0) {
			type = PhotoViewer.SELECT_TYPE_AVATAR
			chatActivity = null
		}
		else if (parentAlert.baseFragment is ChatActivity) {
			chatActivity = parentAlert.baseFragment
			type = 2
		}
		else {
			chatActivity = null
			type = 5
		}

		val arrayList: ArrayList<Any?>
		val index: Int

		if (parentAlert.avatarPicker != 0) {
			arrayList = ArrayList()
			arrayList.add(entry)
			index = 0
		}
		else {
			arrayList = allPhotosArray
			index = cameraPhotos.size - 1
		}

		PhotoViewer.getInstance().openPhotoForSelect(arrayList, index, type, false, object : BasePhotoProvider() {
			override fun onOpen() {
				pauseCameraPreview()
			}

			override fun onClose() {
				resumeCameraPreview()
			}

			override fun getThumbForPhoto(messageObject: MessageObject?, fileLocation: FileLocation?, index: Int): BitmapHolder? {
				return null
			}

			override fun cancelButtonPressed(): Boolean {
				if (cameraOpened && cameraView != null) {
					AndroidUtilities.runOnUIThread({
						if (cameraView != null && !parentAlert.isDismissed) {
							cameraView?.systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_FULLSCREEN
						}
					}, 1000)

					zoomControlView.setZoom(0.0f, false)

					cameraZoom = 0.0f

					cameraView?.setZoom(0.0f)

					CameraController.getInstance().startPreview(cameraView!!.cameraSession)
				}

				if (cancelTakingPhotos && cameraPhotos.size == 1) {
					cameraPhotos.forEach {
						val photoEntry = it as? PhotoEntry ?: return@forEach

						File(photoEntry.path).delete()

						if (photoEntry.imagePath != null) {
							File(photoEntry.imagePath).delete()
						}
						if (photoEntry.thumbPath != null) {
							File(photoEntry.thumbPath).delete()
						}
					}

					cameraPhotos.clear()

					Companion.selectedPhotosOrder.clear()
					Companion.selectedPhotos.clear()

					counterTextView.visibility = INVISIBLE
					cameraPhotoRecyclerView.visibility = GONE

					adapter.notifyDataSetChanged()
					cameraAttachAdapter.notifyDataSetChanged()

					parentAlert.updateCountButton(0)
				}

				return true
			}

			override fun needAddMorePhotos() {
				cancelTakingPhotos = false

				if (mediaFromExternalCamera) {
					parentAlert.chatAttachViewDelegate?.didPressedButton(0, arg = true, notify = true, scheduleDate = 0, forceDocument = false)
					return
				}

				if (!cameraOpened) {
					openCamera(false)
				}

				counterTextView.visibility = VISIBLE
				cameraPhotoRecyclerView.visibility = VISIBLE
				counterTextView.alpha = 1.0f

				updatePhotosCounter(false)
			}

			override fun sendButtonPressed(index: Int, videoEditedInfo: VideoEditedInfo?, notify: Boolean, scheduleDate: Int, forceDocument: Boolean) {
				if (cameraPhotos.isEmpty()) {
					return
				}

				if (videoEditedInfo != null && index >= 0 && index < cameraPhotos.size) {
					val photoEntry = cameraPhotos[index] as PhotoEntry?
					photoEntry?.editedInfo = videoEditedInfo
				}

				if (parentAlert.baseFragment !is ChatActivity || !parentAlert.baseFragment.isSecretChat) {
					cameraPhotos.forEach {
						val photoEntry = it as? PhotoEntry ?: return@forEach
						AndroidUtilities.addMediaToGallery(photoEntry.path)

					}
				}

				parentAlert.applyCaption()

				closeCamera(false)

				parentAlert.chatAttachViewDelegate?.didPressedButton(if (forceDocument) 4 else 8, true, notify, scheduleDate, forceDocument)

				cameraPhotos.clear()

				Companion.selectedPhotosOrder.clear()
				Companion.selectedPhotos.clear()

				adapter.notifyDataSetChanged()
				cameraAttachAdapter.notifyDataSetChanged()
				parentAlert.dismiss(true)
			}

			override fun scaleToFill(): Boolean {
				if (parentAlert.baseFragment.parentActivity == null) {
					return false
				}

				val locked = Settings.System.getInt(parentAlert.baseFragment.parentActivity!!.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
				return sameTakePictureOrientation || locked == 1
			}

			override fun willHidePhotoViewer() {
				val count = gridView.childCount

				for (a in 0 until count) {
					val view = gridView.getChildAt(a)

					if (view is PhotoAttachPhotoCell) {
						view.showImage()
						view.showCheck(true)
					}
				}
			}

			override fun canScrollAway(): Boolean {
				return false
			}

			override fun canCaptureMorePhotos(): Boolean {
				return parentAlert.maxSelectedPhotos != 1
			}
		}, chatActivity, parentAlert.baseFragment is CreateMediaSaleFragment)
	}

	private fun showZoomControls(show: Boolean) {
		if (zoomControlView.tag != null && show || zoomControlView.tag == null && !show) {
			if (show) {
				if (zoomControlHideRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable)
				}

				AndroidUtilities.runOnUIThread(Runnable {
					showZoomControls(show = false)
					zoomControlHideRunnable = null
				}.also { zoomControlHideRunnable = it }, 2000)
			}
			return
		}

		zoomControlAnimation?.cancel()

		zoomControlView.tag = if (show) 1 else null

		zoomControlAnimation = AnimatorSet()
		zoomControlAnimation?.duration = 180
		zoomControlAnimation?.playTogether(ObjectAnimator.ofFloat(zoomControlView, ALPHA, if (show) 1.0f else 0.0f))

		zoomControlAnimation?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				zoomControlAnimation = null
			}
		})

		zoomControlAnimation?.start()

		if (show) {
			AndroidUtilities.runOnUIThread(Runnable {
				showZoomControls(show = false)
				zoomControlHideRunnable = null
			}.also { zoomControlHideRunnable = it }, 2000)
		}
	}

	private fun updatePhotosCounter(added: Boolean) {
		if (parentAlert.avatarPicker != 0) {
			return
		}

		var hasVideo = false
		var hasPhotos = false

		for ((_, value) in Companion.selectedPhotos) {
			val photoEntry = value as? PhotoEntry ?: continue

			if (photoEntry.isVideo) {
				hasVideo = true
			}
			else {
				hasPhotos = true
			}

			if (hasVideo && hasPhotos) {
				break
			}
		}

		val newSelectedCount = max(1, Companion.selectedPhotos.size)

		if (hasVideo && hasPhotos) {
			counterTextView.text = LocaleController.formatPluralString("Media", Companion.selectedPhotos.size).uppercase()

			if (newSelectedCount != currentSelectedCount || added) {
				parentAlert.selectedTextView.text = LocaleController.formatPluralString("MediaSelected", newSelectedCount)
			}
		}
		else if (hasVideo) {
			counterTextView.text = LocaleController.formatPluralString("Videos", Companion.selectedPhotos.size).uppercase()

			if (newSelectedCount != currentSelectedCount || added) {
				parentAlert.selectedTextView.text = LocaleController.formatPluralString("VideosSelected", newSelectedCount)
			}
		}
		else {
			counterTextView.text = LocaleController.formatPluralString("Photos", Companion.selectedPhotos.size).uppercase()

			if (newSelectedCount != currentSelectedCount || added) {
				parentAlert.selectedTextView.text = LocaleController.formatPluralString("PhotosSelected", newSelectedCount)
			}
		}

		parentAlert.setCanOpenPreview(newSelectedCount > 1)
		currentSelectedCount = newSelectedCount
	}

	private fun getCellForIndex(index: Int): PhotoAttachPhotoCell? {
		val count = gridView.childCount

		for (a in 0 until count) {
			val view = gridView.getChildAt(a)

			if (view.top >= gridView.measuredHeight - parentAlert.clipLayoutBottom) {
				continue
			}

			if (view is PhotoAttachPhotoCell) {
				if (view.imageView.tag as Int == index) {
					return view
				}
			}
		}

		return null
	}

	private fun setCameraFlashModeIcon(imageView: ImageView?, mode: String?) {
		when (mode) {
			Camera.Parameters.FLASH_MODE_OFF -> {
				imageView?.setImageResource(R.drawable.flash_off)
				imageView?.contentDescription = context.getString(R.string.AccDescrCameraFlashOff)
			}

			Camera.Parameters.FLASH_MODE_ON -> {
				imageView?.setImageResource(R.drawable.flash_on)
				imageView?.contentDescription = context.getString(R.string.AccDescrCameraFlashOn)
			}

			Camera.Parameters.FLASH_MODE_AUTO -> {
				imageView?.setImageResource(R.drawable.flash_auto)
				imageView?.contentDescription = context.getString(R.string.AccDescrCameraFlashAuto)
			}
		}
	}

	fun checkCamera(request: Boolean) {
		if (parentAlert.baseFragment.parentActivity == null) {
			return
		}

		val old = deviceHasGoodCamera
		val old2 = noCameraPermissions

		if (!SharedConfig.inappCamera) {
			deviceHasGoodCamera = false
		}
		else {
			noCameraPermissions = parentAlert.baseFragment.parentActivity!!.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED

			if (noCameraPermissions) {
				if (request) {
					val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
						arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
					}
					else {
						arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
					}

					runCatching {
						parentAlert.baseFragment.parentActivity?.requestPermissions(permissions, 17)
					}
				}

				deviceHasGoodCamera = false
			}
			else {
				if (request || SharedConfig.hasCameraCache) {
					CameraController.getInstance().initCamera(null)
				}

				deviceHasGoodCamera = CameraController.getInstance().isCameraInitied
			}
		}

		if (old != deviceHasGoodCamera || old2 != noCameraPermissions) {
			adapter.notifyDataSetChanged()
		}

		if (parentAlert.isShowing && deviceHasGoodCamera && parentAlert.backDrawable.alpha != 0 && !cameraOpened) {
			showCamera()
		}
	}

	private fun openCamera(animated: Boolean) {
		if (cameraView == null || cameraInitAnimation != null || !cameraView!!.isInited || parentAlert.isDismissed) {
			return
		}

		if (parentAlert.avatarPicker == 2 || parentAlert.baseFragment is ChatActivity) {
			tooltipTextView.visibility = VISIBLE
		}
		else {
			tooltipTextView.visibility = GONE
		}

		if (cameraPhotos.isEmpty()) {
			counterTextView.visibility = INVISIBLE
			cameraPhotoRecyclerView.visibility = GONE
		}
		else {
			counterTextView.visibility = VISIBLE
			cameraPhotoRecyclerView.visibility = VISIBLE
		}

		if (parentAlert.commentTextView.isKeyboardVisible && isFocusable) {
			parentAlert.commentTextView.closeKeyboard()
		}

		zoomControlView.visibility = VISIBLE
		zoomControlView.alpha = 0.0f

		cameraPanel.visibility = VISIBLE
		cameraPanel.tag = null

		animateCameraValues[0] = 0
		animateCameraValues[1] = itemSize
		animateCameraValues[2] = itemSize

		additionCloseCameraY = 0f
		cameraExpanded = true

		cameraView?.setFpsLimit(-1)

		AndroidUtilities.hideKeyboard(this)
		AndroidUtilities.setLightNavigationBar(parentAlert.window, false)

		if (animated) {
			setCameraOpenProgress(0f)

			cameraAnimationInProgress = true
			animationIndex = NotificationCenter.getInstance(parentAlert.currentAccount).setAnimationInProgress(animationIndex, null)

			val animators = ArrayList<Animator>()
			animators.add(ObjectAnimator.ofFloat(this, "cameraOpenProgress", 0.0f, 1.0f))
			animators.add(ObjectAnimator.ofFloat(cameraPanel, ALPHA, 1.0f))
			animators.add(ObjectAnimator.ofFloat(counterTextView, ALPHA, 1.0f))
			animators.add(ObjectAnimator.ofFloat(cameraPhotoRecyclerView, ALPHA, 1.0f))

			for (a in 0..1) {
				if (flashModeButton[a]?.isVisible == true) {
					animators.add(ObjectAnimator.ofFloat(flashModeButton[a], ALPHA, 1.0f))
					break
				}
			}

			val animatorSet = AnimatorSet()
			animatorSet.playTogether(animators)
			animatorSet.duration = 350
			animatorSet.interpolator = CubicBezierInterpolator.DEFAULT

			animatorSet.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animator: Animator) {
					NotificationCenter.getInstance(parentAlert.currentAccount).onAnimationFinish(animationIndex)

					cameraAnimationInProgress = false

					cameraView?.invalidateOutline()
					cameraIcon?.invalidate()

					if (cameraOpened) {
						parentAlert.chatAttachViewDelegate?.onCameraOpened()
					}

					cameraView?.systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_FULLSCREEN
				}
			})

			animatorSet.start()
		}
		else {
			setCameraOpenProgress(1.0f)

			cameraPanel.alpha = 1.0f
			counterTextView.alpha = 1.0f
			cameraPhotoRecyclerView.alpha = 1.0f

			for (a in 0..1) {
				if (flashModeButton[a]?.isVisible == true) {
					flashModeButton[a]?.alpha = 1.0f
					break
				}
			}

			parentAlert.chatAttachViewDelegate?.onCameraOpened()
			cameraView?.systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_FULLSCREEN
		}

		cameraOpened = true

		cameraView?.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		gridView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
	}

	fun loadGalleryPhotos() {
		val albumEntry = if (parentAlert.baseFragment is ChatActivity || parentAlert.avatarPicker == 2) {
			MediaController.allMediaAlbumEntry
		}
		else {
			MediaController.allPhotosAlbumEntry
		}

		if (albumEntry == null) {
			MediaController.loadGalleryPhotosAlbums(0)
		}
	}

	fun showCamera() {
		if (parentAlert.paused || !mediaEnabled) {
			return
		}

		if (cameraView == null) {
			cameraView = CameraView(parentAlert.baseFragment.parentActivity, parentAlert.openWithFrontFaceCamera)
			cameraView?.setRecordFile(AndroidUtilities.generateVideoPath(parentAlert.baseFragment is ChatActivity && parentAlert.baseFragment.isSecretChat))
			cameraView?.isFocusable = true
			cameraView?.setFpsLimit(30)

			cameraView?.outlineProvider = object : ViewOutlineProvider() {
				override fun getOutline(view: View, outline: Outline) {
					if (cameraAnimationInProgress) {
						AndroidUtilities.rectTmp[animationClipLeft + cameraViewOffsetX * (1f - cameraOpenProgress), animationClipTop + cameraViewOffsetY * (1f - cameraOpenProgress), animationClipRight] = animationClipBottom
						outline.setRect(AndroidUtilities.rectTmp.left.toInt(), AndroidUtilities.rectTmp.top.toInt(), AndroidUtilities.rectTmp.right.toInt(), AndroidUtilities.rectTmp.bottom.toInt())
					}
					else if (!cameraOpened) {
						val rad = AndroidUtilities.dp(8 * parentAlert.cornerRadius)
						outline.setRoundRect(cameraViewOffsetX.toInt(), cameraViewOffsetY.toInt(), view.measuredWidth + rad, view.measuredHeight + rad, rad.toFloat())
					}
					else {
						outline.setRect(0, 0, view.measuredWidth, view.measuredHeight)
					}
				}
			}

			cameraView?.clipToOutline = true
			cameraView?.contentDescription = context.getString(R.string.AccDescrInstantCamera)

			parentAlert.container.addView(cameraView, 1, LayoutParams(itemSize, itemSize))

			cameraView?.setDelegate {
				val current = cameraView?.cameraSession?.currentFlashMode
				val next = cameraView?.cameraSession?.nextFlashMode

				if (current == next) {
					for (a in 0..1) {
						flashModeButton[a]?.visibility = INVISIBLE
						flashModeButton[a]?.alpha = 0.0f
						flashModeButton[a]?.translationY = 0.0f
					}
				}
				else {
					setCameraFlashModeIcon(flashModeButton[0], cameraView?.cameraSession?.currentFlashMode)

					for (a in 0..1) {
						flashModeButton[a]?.visibility = if (a == 0) VISIBLE else INVISIBLE
						flashModeButton[a]?.alpha = if (a == 0 && cameraOpened) 1.0f else 0.0f
						flashModeButton[a]?.translationY = 0.0f
					}
				}

				switchCameraButton.setImageResource(if (cameraView?.isFrontface == true) R.drawable.camera_revert1 else R.drawable.camera_revert2)
				switchCameraButton.visibility = if (cameraView?.hasFrontFaceCamera() == true) VISIBLE else INVISIBLE

				if (!cameraOpened) {
					cameraInitAnimation = AnimatorSet()
					cameraInitAnimation?.playTogether(ObjectAnimator.ofFloat(cameraView, ALPHA, 0.0f, 1.0f), ObjectAnimator.ofFloat(cameraIcon, ALPHA, 0.0f, 1.0f))
					cameraInitAnimation?.duration = 180

					cameraInitAnimation?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							if (animation == cameraInitAnimation) {
								canSaveCameraPreview = true
								cameraInitAnimation = null

								if (!isHidden) {
									val count = gridView.childCount

									for (a in 0 until count) {
										val child = gridView.getChildAt(a)

										if (child is PhotoAttachCameraCell) {
											child.setVisibility(INVISIBLE)
											break
										}
									}
								}
							}
						}

						override fun onAnimationCancel(animation: Animator) {
							cameraInitAnimation = null
						}
					})

					cameraInitAnimation?.start()
				}
			}

			if (cameraIcon == null) {
				cameraIcon = object : FrameLayout(parentAlert.baseFragment.parentActivity!!) {
					override fun onDraw(canvas: Canvas) {
						val w = cameraDrawable.intrinsicWidth
						val h = cameraDrawable.intrinsicHeight
						val x = (itemSize - w) / 2
						var y = (itemSize - h) / 2

						if (cameraViewOffsetY != 0f) {
							y -= cameraViewOffsetY.toInt()
						}

						cameraDrawable.setBounds(x, y, x + w, y + h)
						cameraDrawable.draw(canvas)
					}
				}

				cameraIcon?.setWillNotDraw(false)
				cameraIcon?.clipChildren = true
			}

			parentAlert.container.addView(cameraIcon, 2, LayoutParams(itemSize, itemSize))

			cameraView?.alpha = if (mediaEnabled) 1.0f else 0.2f
			cameraView?.isEnabled = mediaEnabled
			cameraIcon?.alpha = if (mediaEnabled) 1.0f else 0.2f
			cameraIcon?.isEnabled = mediaEnabled

			if (isHidden) {
				cameraView?.visibility = GONE
				cameraIcon?.visibility = GONE
			}

			checkCameraViewPosition()
			invalidate()
		}

		zoomControlView.setZoom(0.0f, false)

		cameraZoom = 0.0f

		cameraView?.translationX = cameraViewLocation[0]
		cameraView?.translationY = cameraViewLocation[1] + currentPanTranslationY
		cameraIcon?.translationX = cameraViewLocation[0]
		cameraIcon?.translationY = cameraViewLocation[1] + cameraViewOffsetY + currentPanTranslationY
	}

	fun hideCamera(async: Boolean) {
		val cameraView = cameraView ?: return

		if (!deviceHasGoodCamera) {
			return
		}

		saveLastCameraBitmap()

		val count = gridView.childCount

		for (a in 0 until count) {
			val child = gridView.getChildAt(a)

			if (child is PhotoAttachCameraCell) {
				child.setVisibility(VISIBLE)
				child.updateBitmap()
				break
			}
		}

		cameraView.destroy(async, null)

		cameraInitAnimation?.cancel()
		cameraInitAnimation = null

		AndroidUtilities.runOnUIThread({
			parentAlert.container.removeView(cameraView)
			parentAlert.container.removeView(cameraIcon)

			this.cameraView = null
			cameraIcon = null
		}, 300)

		canSaveCameraPreview = false
	}

	private fun saveLastCameraBitmap() {
		if (!canSaveCameraPreview) {
			return
		}

		runCatching {
			val textureView = cameraView?.textureView
			var bitmap = textureView?.bitmap ?: return@runCatching

			val newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, cameraView!!.matrix, true)
			bitmap.recycle()

			bitmap = newBitmap

			val lastBitmap = bitmap.scale(80, (bitmap.height / (bitmap.width / 80.0f)).toInt())

			if (lastBitmap != bitmap) {
				bitmap.recycle()
			}

			Utilities.blurBitmap(lastBitmap, 7, 1, lastBitmap.width, lastBitmap.height, lastBitmap.rowBytes)

			val file = File(ApplicationLoader.filesDirFixed, "cthumb.jpg")

			FileOutputStream(file).use {
				lastBitmap.compress(Bitmap.CompressFormat.JPEG, 87, it)
				lastBitmap.recycle()
			}
		}
	}

	fun onActivityResultFragment(requestCode: Int, data: Intent?, currentPicturePath: String?) {
		@Suppress("NAME_SHADOWING") var data = data
		@Suppress("NAME_SHADOWING") var currentPicturePath = currentPicturePath

		if (parentAlert.baseFragment.parentActivity == null) {
			return
		}

		mediaFromExternalCamera = true

		if (requestCode == 0) {
			if (!currentPicturePath.isNullOrEmpty()) {
				PhotoViewer.getInstance().setParentActivity(parentAlert.baseFragment.parentActivity)
				PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder)

				var orientation = 0

				try {
					val ei = ExifInterface(currentPicturePath)

					when (ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
						ExifInterface.ORIENTATION_ROTATE_90 -> orientation = 90
						ExifInterface.ORIENTATION_ROTATE_180 -> orientation = 180
						ExifInterface.ORIENTATION_ROTATE_270 -> orientation = 270
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				var width = 0
				var height = 0

				try {
					val options = BitmapFactory.Options()
					options.inJustDecodeBounds = true
					BitmapFactory.decodeFile(File(currentPicturePath).absolutePath, options)
					width = options.outWidth
					height = options.outHeight
				}
				catch (e: Exception) {
					// ignore
				}

				val photoEntry = PhotoEntry(0, lastImageId--, 0, currentPicturePath, orientation, false, width, height, 0)
				photoEntry.canDeleteAfter = true

				openPhotoViewer(photoEntry, sameTakePictureOrientation = false, external = true)
			}
		}
		else if (requestCode == 2) {
			var videoPath: String? = null

			FileLog.d("pic path $currentPicturePath")

			if (data != null && currentPicturePath != null) {
				if (File(currentPicturePath).exists()) {
					data = null
				}
			}

			if (data != null) {
				val uri = data.data

				if (uri != null) {
					FileLog.d("video record uri $uri")

					videoPath = AndroidUtilities.getPath(uri)

					FileLog.d("resolved path = $videoPath")

					if (videoPath == null || !File(videoPath).exists()) {
						videoPath = currentPicturePath
					}
				}
				else {
					videoPath = currentPicturePath
				}

				if (parentAlert.baseFragment !is ChatActivity || !parentAlert.baseFragment.isSecretChat) {
					AndroidUtilities.addMediaToGallery(currentPicturePath)
				}

				currentPicturePath = null
			}

			if (videoPath == null && currentPicturePath != null) {
				val f = File(currentPicturePath)

				if (f.exists()) {
					videoPath = currentPicturePath
				}
			}

			var duration: Long = 0

			runCatching {
				MediaMetadataRetriever().use {
					it.setDataSource(videoPath)

					val d = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

					if (d != null) {
						duration = ceil((d.toLong() / 1000.0f).toDouble()).toInt().toLong()
					}
				}
			}.onFailure {
				FileLog.e(it)
			}

			val bitmap = createVideoThumbnail(videoPath!!, MediaStore.Video.Thumbnails.MINI_KIND)
			val fileName = Int.MIN_VALUE.toString() + "_" + SharedConfig.getLastLocalId() + ".jpg"
			val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName)

			runCatching {
				FileOutputStream(cacheFile).use {
					bitmap?.compress(Bitmap.CompressFormat.JPEG, 55, it)
				}
			}.onFailure {
				FileLog.e(it)
			}

			SharedConfig.saveConfig()

			val entry = PhotoEntry(0, lastImageId--, 0, videoPath, 0, true, bitmap!!.width, bitmap.height, 0)
			entry.duration = duration.toInt()
			entry.thumbPath = cacheFile.absolutePath

			openPhotoViewer(entry, sameTakePictureOrientation = false, external = true)
		}
	}

	fun closeCamera(animated: Boolean) {
		if (takingPhoto) {
			return
		}

		val cameraView = cameraView ?: return

		animateCameraValues[1] = itemSize
		animateCameraValues[2] = itemSize

		if (zoomControlHideRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable)
			zoomControlHideRunnable = null
		}

		AndroidUtilities.setLightNavigationBar(parentAlert.window, AndroidUtilities.computePerceivedBrightness(ResourcesCompat.getColor(resources, R.color.light_background, null)) > 0.721)

		if (animated) {
			additionCloseCameraY = cameraView.translationY
			cameraAnimationInProgress = true

			val animators = ArrayList<Animator>()
			animators.add(ObjectAnimator.ofFloat(this, "cameraOpenProgress", 0.0f))
			animators.add(ObjectAnimator.ofFloat(cameraPanel, ALPHA, 0.0f))
			animators.add(ObjectAnimator.ofFloat(zoomControlView, ALPHA, 0.0f))
			animators.add(ObjectAnimator.ofFloat(counterTextView, ALPHA, 0.0f))
			animators.add(ObjectAnimator.ofFloat(cameraPhotoRecyclerView, ALPHA, 0.0f))

			for (a in 0..1) {
				if (flashModeButton[a]?.visibility == VISIBLE) {
					animators.add(ObjectAnimator.ofFloat(flashModeButton[a], ALPHA, 0.0f))
					break
				}
			}

			animationIndex = NotificationCenter.getInstance(parentAlert.currentAccount).setAnimationInProgress(animationIndex, null)

			val animatorSet = AnimatorSet()
			animatorSet.playTogether(animators)
			animatorSet.duration = 220
			animatorSet.interpolator = CubicBezierInterpolator.DEFAULT

			animatorSet.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animator: Animator) {
					NotificationCenter.getInstance(parentAlert.currentAccount).onAnimationFinish(animationIndex)

					cameraExpanded = false

					setCameraOpenProgress(0f)

					cameraAnimationInProgress = false

					cameraView.invalidateOutline()

					cameraOpened = false

					cameraPanel.visibility = GONE

					zoomControlView.visibility = GONE
					zoomControlView.tag = null

					cameraPhotoRecyclerView.visibility = GONE

					cameraView.setFpsLimit(30)
					cameraView.systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				}
			})

			animatorSet.start()
		}
		else {
			cameraExpanded = false

			setCameraOpenProgress(0f)

			animateCameraValues[0] = 0

			setCameraOpenProgress(0f)

			cameraPanel.alpha = 0f
			cameraPanel.visibility = GONE

			zoomControlView.alpha = 0f
			zoomControlView.tag = null
			zoomControlView.visibility = GONE

			cameraPhotoRecyclerView.alpha = 0f

			counterTextView.alpha = 0f

			cameraPhotoRecyclerView.visibility = GONE

			for (a in 0..1) {
				if (flashModeButton[a]?.visibility == VISIBLE) {
					flashModeButton[a]?.alpha = 0.0f
					break
				}
			}

			cameraOpened = false

			cameraView.setFpsLimit(30)
			cameraView.systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
		}

		cameraView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO
		gridView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO
	}

	@Keep
	fun getCameraOpenProgress(): Float {
		return cameraOpenProgress
	}

	@Keep
	fun setCameraOpenProgress(value: Float) {
		val cameraView = cameraView ?: return

		cameraOpenProgress = value

		val startWidth = animateCameraValues[1].toFloat()
		val startHeight = animateCameraValues[2].toFloat()
		// val isPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y
		val endWidth = (parentAlert.container.width - parentAlert.leftInset - parentAlert.rightInset).toFloat()
		val endHeight = parentAlert.container.height.toFloat()
		val fromX = cameraViewLocation[0]
		val fromY = cameraViewLocation[1]
		val toX = 0f
		val toY = additionCloseCameraY

		if (value == 0f) {
			cameraIcon?.translationX = cameraViewLocation[0]
			cameraIcon?.translationY = cameraViewLocation[1] + cameraViewOffsetY
		}

		val cameraViewW: Int
		val cameraViewH: Int

		val layoutParams = cameraView.layoutParams as LayoutParams
		val textureStartHeight = cameraView.getTextureHeight(startWidth, startHeight)
		val textureEndHeight = cameraView.getTextureHeight(endWidth, endHeight)
		val fromScale = textureStartHeight / textureEndHeight
		val fromScaleY = startHeight / endHeight
		val fromScaleX = startWidth / endWidth
		val scaleOffsetX: Float
		val scaleOffsetY: Float

		if (cameraExpanded) {
			cameraViewW = endWidth.toInt()
			cameraViewH = endHeight.toInt()

			val s = fromScale * (1f - value) + value

			cameraView.textureView.scaleX = s
			cameraView.textureView.scaleY = s

			val sX = fromScaleX * (1f - value) + value
			val sY = fromScaleY * (1f - value) + value

			scaleOffsetY = (1 - sY) * endHeight / 2
			scaleOffsetX = (1 - sX) * endWidth / 2

			cameraView.translationX = fromX * (1f - value) + toX * value - scaleOffsetX
			cameraView.translationY = fromY * (1f - value) + toY * value - scaleOffsetY

			animationClipTop = fromY * (1f - value) - cameraView.translationY
			animationClipBottom = (fromY + startHeight) * (1f - value) - cameraView.translationY + endHeight * value
			animationClipLeft = fromX * (1f - value) - cameraView.translationX
			animationClipRight = (fromX + startWidth) * (1f - value) - cameraView.translationX + endWidth * value
		}
		else {
			cameraViewW = startWidth.toInt()
			cameraViewH = startHeight.toInt()

			cameraView.textureView.scaleX = 1f
			cameraView.textureView.scaleY = 1f

			animationClipTop = 0f
			animationClipBottom = endHeight
			animationClipLeft = 0f
			animationClipRight = endWidth

			cameraView.translationX = fromX
			cameraView.translationY = fromY
		}

		if (value <= 0.5f) {
			cameraIcon?.alpha = 1.0f - value / 0.5f
		}
		else {
			cameraIcon?.alpha = 0.0f
		}

		if (layoutParams.width != cameraViewW || layoutParams.height != cameraViewH) {
			layoutParams.width = cameraViewW
			layoutParams.height = cameraViewH

			cameraView.requestLayout()
		}

		cameraView.invalidateOutline()
	}

	private fun checkCameraViewPosition() {
		cameraView?.invalidateOutline()

		var holder = gridView.findViewHolderForAdapterPosition(itemsPerRow - 1)
		holder?.itemView?.invalidateOutline()

		if (!adapter.needCamera || !deviceHasGoodCamera || selectedAlbumEntry !== galleryAlbumEntry) {
			holder = gridView.findViewHolderForAdapterPosition(0)
			holder?.itemView?.invalidateOutline()
		}

		cameraIcon?.invalidate() // MARK: was cameraView?.invalidate()

		val params = recordTime.layoutParams as MarginLayoutParams
		params.topMargin = if (rootWindowInsets == null) AndroidUtilities.dp(16f) else rootWindowInsets.systemWindowInsetTop + AndroidUtilities.dp(2f)

		if (!deviceHasGoodCamera) {
			return
		}

		val count = gridView.childCount

		for (a in 0 until count) {
			val child = gridView.getChildAt(a)

			if (child is PhotoAttachCameraCell) {
				if (!child.isAttachedToWindow()) {
					break
				}

				val topLocal = child.getY() + gridView.y + y
				val top = topLocal + parentAlert.sheetContainer.y
				var left = child.getX() + gridView.x + x + parentAlert.sheetContainer.x

				left -= rootWindowInsets.systemWindowInsetLeft.toFloat()

				var maxY = ((if (!parentAlert.inBubbleMode) AndroidUtilities.statusBarHeight else 0) + ActionBar.getCurrentActionBarHeight()).toFloat()

				val newCameraViewOffsetY = if (topLocal < maxY) {
					maxY - topLocal
				}
				else {
					0f
				}

				if (newCameraViewOffsetY != cameraViewOffsetY) {
					cameraViewOffsetY = newCameraViewOffsetY
					cameraView?.invalidateOutline()
					cameraIcon?.invalidate()
				}

				val containerHeight = parentAlert.sheetContainer.measuredHeight

				maxY = (containerHeight - parentAlert.buttonsRecyclerView.measuredHeight + parentAlert.buttonsRecyclerView.translationY).toInt().toFloat()

				cameraViewOffsetBottomY = if (topLocal + child.getMeasuredHeight() > maxY) {
					topLocal + child.getMeasuredHeight() - maxY
				}
				else {
					0f
				}

				cameraViewLocation[0] = left
				cameraViewLocation[1] = top

				applyCameraViewPosition()

				return
			}
		}

		if (cameraViewOffsetY != 0f || cameraViewOffsetX != 0f) {
			cameraViewOffsetX = 0f
			cameraViewOffsetY = 0f

			cameraView?.invalidateOutline()
			cameraIcon?.invalidate()
		}

		cameraViewLocation[0] = AndroidUtilities.dp(-400f).toFloat()
		cameraViewLocation[1] = 0f

		applyCameraViewPosition()
	}

	private fun applyCameraViewPosition() {
		val cameraView = cameraView ?: return

		if (!cameraOpened) {
			cameraView.translationX = cameraViewLocation[0]
			cameraView.translationY = cameraViewLocation[1] + currentPanTranslationY
		}

		cameraIcon?.translationX = cameraViewLocation[0]
		cameraIcon?.translationY = cameraViewLocation[1] + cameraViewOffsetY + currentPanTranslationY

		var finalWidth = itemSize
		var finalHeight = itemSize
		var layoutParams: LayoutParams

		if (!cameraOpened) {
			cameraView.setClipTop()
			cameraView.setClipBottom()

			layoutParams = cameraView.layoutParams as LayoutParams

			if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
				layoutParams.width = finalWidth
				layoutParams.height = finalHeight
				cameraView.layoutParams = layoutParams

				val layoutParamsFinal = layoutParams

				AndroidUtilities.runOnUIThread {
					cameraView.layoutParams = layoutParamsFinal
				}
			}
		}

		finalWidth = (itemSize - cameraViewOffsetX).toInt()
		finalHeight = (itemSize - cameraViewOffsetY - cameraViewOffsetBottomY).toInt()
		layoutParams = cameraIcon?.layoutParams as? LayoutParams ?: return

		if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
			layoutParams.width = finalWidth
			layoutParams.height = finalHeight

			cameraIcon?.layoutParams = layoutParams

			AndroidUtilities.runOnUIThread {
				cameraIcon?.layoutParams = layoutParams
			}
		}
	}

	val selectedPhotos: HashMap<Any, Any>
		get() = Companion.selectedPhotos

	val selectedPhotosOrder: ArrayList<Any>
		get() = Companion.selectedPhotosOrder

	fun updateSelected(newSelectedPhotos: HashMap<Any, Any>?, newPhotosOrder: ArrayList<Any>?, updateLayout: Boolean) {
		Companion.selectedPhotos.clear()
		Companion.selectedPhotos.putAll(newSelectedPhotos!!)
		Companion.selectedPhotosOrder.clear()
		Companion.selectedPhotosOrder.addAll(newPhotosOrder!!)

		if (updateLayout) {
			updatePhotosCounter(false)
			updateCheckedPhotoIndices()

			val count = gridView.childCount

			for (i in 0 until count) {
				val child = gridView.getChildAt(i)

				if (child is PhotoAttachPhotoCell) {
					var position = gridView.getChildAdapterPosition(child)

					if (adapter.needCamera && selectedAlbumEntry === galleryAlbumEntry) {
						position--
					}

					if (parentAlert.avatarPicker != 0) {
						child.checkBox.visibility = GONE
					}

					val photoEntry = getPhotoEntryAtPosition(position)

					if (photoEntry != null) {
						child.setPhotoEntry(photoEntry, adapter.needCamera && selectedAlbumEntry === galleryAlbumEntry, position == adapter.itemCount - 1)

						if (parentAlert.baseFragment is ChatActivity && parentAlert.allowOrder) {
							child.setChecked(Companion.selectedPhotosOrder.indexOf(photoEntry.imageId), Companion.selectedPhotos.containsKey(photoEntry.imageId), false)
						}
						else {
							child.setChecked(-1, Companion.selectedPhotos.containsKey(photoEntry.imageId), false)
						}
					}
				}
			}
		}
	}

	fun checkStorage() {
		if (noGalleryPermissions) {
			noGalleryPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				parentAlert.baseFragment.parentActivity?.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED && parentAlert.baseFragment.parentActivity?.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED
			}
			else {
				parentAlert.baseFragment.parentActivity?.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
			}

			if (!noGalleryPermissions) {
				loadGalleryPhotos()
			}

			adapter.notifyDataSetChanged()
			cameraAttachAdapter.notifyDataSetChanged()
		}
	}

	override fun scrollToTop() {
		gridView.smoothScrollToPosition(0)
	}

	override fun needsActionBar(): Int {
		return 1
	}

	override fun onMenuItemClick(id: Int) {
		if (id == group || id == compress) {
			if (parentAlert.maxSelectedPhotos > 0 && Companion.selectedPhotosOrder.size > 1 && parentAlert.baseFragment is ChatActivity) {
				val chatActivity = parentAlert.baseFragment
				val chat = chatActivity.currentChat

				if (chat != null && !hasAdminRights(chat) && chat.slowmodeEnabled) {
					AlertsCreator.createSimpleAlert(context, context.getString(R.string.Slowmode), context.getString(R.string.SlowmodeSendError))?.show()
					return
				}
			}
		}

		if (id == group) {
			if (parentAlert.editingMessageObject == null && (parentAlert.baseFragment as? ChatActivity)?.isInScheduleMode == true) {
				AlertsCreator.createScheduleDatePickerDialog(context, parentAlert.baseFragment.dialogId) { notify, scheduleDate ->
					parentAlert.applyCaption()
					parentAlert.chatAttachViewDelegate?.didPressedButton(7, false, notify, scheduleDate, false)
				}
			}
			else {
				parentAlert.applyCaption()
				parentAlert.chatAttachViewDelegate?.didPressedButton(7, arg = false, notify = true, scheduleDate = 0, forceDocument = false)
			}
		}
		else if (id == compress) {
			if (parentAlert.editingMessageObject == null && (parentAlert.baseFragment as? ChatActivity)?.isInScheduleMode == true) {
				AlertsCreator.createScheduleDatePickerDialog(context, parentAlert.baseFragment.dialogId) { notify, scheduleDate ->
					parentAlert.applyCaption()
					parentAlert.chatAttachViewDelegate?.didPressedButton(4, true, notify, scheduleDate, false)
				}
			}
			else {
				parentAlert.applyCaption()
				parentAlert.chatAttachViewDelegate?.didPressedButton(4, arg = true, notify = true, scheduleDate = 0, forceDocument = false)
			}
		}
		else if (id == open_in) {
			try {
				if (parentAlert.baseFragment is ChatActivity || parentAlert.avatarPicker == 2) {
					val videoPickerIntent = Intent()
					videoPickerIntent.type = "video/*"
					videoPickerIntent.action = Intent.ACTION_GET_CONTENT
					videoPickerIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, FileLoader.DEFAULT_MAX_FILE_SIZE)

					val photoPickerIntent = Intent(Intent.ACTION_PICK)
					photoPickerIntent.type = "image/*"

					val chooserIntent = Intent.createChooser(photoPickerIntent, null)
					chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(videoPickerIntent))

					if (parentAlert.avatarPicker != 0) {
						parentAlert.baseFragment.startActivityForResult(chooserIntent, 14)
					}
					else {
						parentAlert.baseFragment.startActivityForResult(chooserIntent, 1)
					}
				}
				else {
					val photoPickerIntent = Intent(Intent.ACTION_PICK)
					photoPickerIntent.type = "image/*"

					if (parentAlert.avatarPicker != 0) {
						parentAlert.baseFragment.startActivityForResult(photoPickerIntent, 14)
					}
					else {
						parentAlert.baseFragment.startActivityForResult(photoPickerIntent, 1)
					}
				}

				parentAlert.dismiss(true)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
		else if (id == preview) {
			parentAlert.updatePhotoPreview(true)
		}
		else if (id >= 10) {
			selectedAlbumEntry = dropDownAlbums!![id - 10]

			if (selectedAlbumEntry === galleryAlbumEntry) {
				dropDown.text = context.getString(R.string.ChatGallery)
			}
			else {
				dropDown.text = selectedAlbumEntry!!.bucketName
			}

			adapter.notifyDataSetChanged()
			cameraAttachAdapter.notifyDataSetChanged()

			layoutManager.scrollToPositionWithOffset(0, -gridView.paddingTop + AndroidUtilities.dp(7f))
		}
	}

	override val selectedItemsCount: Int
		get() = Companion.selectedPhotosOrder.size

	override fun onSelectedItemsCountChanged(count: Int) {
		if (count <= 1 || parentAlert.editingMessageObject != null) {
			parentAlert.selectedMenuItem.hideSubItem(group)

			if (count == 0) {
				parentAlert.selectedMenuItem.showSubItem(open_in)
				parentAlert.selectedMenuItem.hideSubItem(compress)
			}
			else {
				parentAlert.selectedMenuItem.showSubItem(compress)
			}
		}
		else {
			parentAlert.selectedMenuItem.showSubItem(group)
		}

		if (count != 0) {
			parentAlert.selectedMenuItem.hideSubItem(open_in)
		}

		if (count > 1) {
			parentAlert.selectedMenuItem.showSubItem(preview)
		}
		else {
			parentAlert.selectedMenuItem.hideSubItem(preview)
		}
	}

	override fun applyCaption(text: CharSequence?) {
		for (a in Companion.selectedPhotosOrder.indices) {
			if (a == 0) {
				val o = Companion.selectedPhotos[Companion.selectedPhotosOrder[a]]

				if (o is PhotoEntry) {
					o.caption = text
					o.entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(arrayOf(text), false)
				}
				else if (o is SearchImage) {
					o.caption = text
					o.entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(arrayOf(text), false)
				}
			}
		}
	}

	fun captionForAllMedia(): Boolean {
		var captionCount = 0

		for (a in Companion.selectedPhotosOrder.indices) {
			val o = Companion.selectedPhotos[Companion.selectedPhotosOrder[a]]
			var caption: CharSequence? = null

			if (o is PhotoEntry) {
				caption = o.caption
			}
			else if (o is SearchImage) {
				caption = o.caption
			}

			if (!caption.isNullOrEmpty()) {
				captionCount++
			}
		}

		return captionCount <= 1
	}

	override fun onDestroy() {
		NotificationCenter.globalInstance.let {
			it.removeObserver(this, NotificationCenter.albumsDidLoad)
			it.removeObserver(this, NotificationCenter.cameraInitied)
		}
	}

	override fun onPause() {
		if (!requestingPermissions) {
			if (cameraView != null && shutterButton.state == ShutterButton.State.RECORDING) {
				resetRecordState()
				CameraController.getInstance().stopVideoRecording(cameraView?.cameraSession, false)
				shutterButton.setState(ShutterButton.State.DEFAULT, true)
			}

			if (cameraOpened) {
				closeCamera(false)
			}

			hideCamera(true)
		}
		else {
			if (cameraView != null && shutterButton.state == ShutterButton.State.RECORDING) {
				shutterButton.setState(ShutterButton.State.DEFAULT, true)
			}

			requestingPermissions = false
		}
	}

	override fun onResume() {
		if (parentAlert.isShowing && !parentAlert.isDismissed) {
			checkCamera(false)
		}
	}

	override val listTopPadding: Int
		get() = gridView.paddingTop

	override var currentItemTop: Int = 0
		get() {
			if (gridView.childCount <= 0) {
				gridView.topGlowOffset = gridView.paddingTop.also { field = it }
				progressView.translationY = 0f
				return Int.MAX_VALUE
			}

			val child = gridView.getChildAt(0)
			val holder = gridView.findContainingViewHolder(child) as RecyclerListView.Holder?
			val top = child.top
			var newOffset = AndroidUtilities.dp(7f)

			if (top >= AndroidUtilities.dp(7f) && holder != null && holder.adapterPosition == 0) {
				newOffset = top
			}

			progressView.translationY = (newOffset + (measuredHeight - newOffset - AndroidUtilities.dp(50f) - progressView.measuredHeight) / 2).toFloat()
			gridView.topGlowOffset = newOffset

			return newOffset.also { field = it }
		}
		set(value) {
			field = value
			super.currentItemTop = value
		}

	override val firstOffset: Int
		get() = listTopPadding + AndroidUtilities.dp(56f)

	override fun checkColors() {
		cameraIcon?.invalidate()

		val textColor = ResourcesCompat.getColor(context.resources, if (forceDarkTheme) R.color.white else R.color.text, null)

		Theme.setDrawableColor(cameraDrawable, ResourcesCompat.getColor(context.resources, R.color.white, null))

		progressView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))

		gridView.setGlowColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))

		val holder = gridView.findViewHolderForAdapterPosition(0)

		if (holder != null && holder.itemView is PhotoAttachCameraCell) {
			holder.itemView.imageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.SRC_IN)
		}

		dropDown.setTextColor(textColor)

		dropDownContainer.setPopupItemsColor(textColor, false)
		dropDownContainer.setPopupItemsColor(textColor, true)
		dropDownContainer.redrawPopup(ResourcesCompat.getColor(context.resources, R.color.background, null))

		Theme.setDrawableColor(dropDownDrawable, textColor)
	}

	override fun onInit(mediaEnabled: Boolean) {
		this.mediaEnabled = mediaEnabled

		cameraView?.alpha = if (this.mediaEnabled) 1.0f else 0.2f
		cameraView?.isEnabled = this.mediaEnabled

		cameraIcon?.alpha = if (this.mediaEnabled) 1.0f else 0.2f
		cameraIcon?.isEnabled = this.mediaEnabled

		if (parentAlert.baseFragment is ChatActivity && parentAlert.avatarPicker == 0) {
			galleryAlbumEntry = MediaController.allMediaAlbumEntry

			if (this.mediaEnabled) {
				progressView.setText(context.getString(R.string.NoPhotos))
				progressView.setLottie(0, 0, 0)
			}
			else {
				val chat = parentAlert.baseFragment.currentChat

				progressView.setLottie(R.raw.media_forbidden, 150, 150)

				if (isActionBannedByDefault(chat, ChatObject.ACTION_SEND_MEDIA)) {
					progressView.setText(context.getString(R.string.GlobalAttachMediaRestricted))
				}
				else if (AndroidUtilities.isBannedForever(chat?.bannedRights)) {
					progressView.setText(LocaleController.formatString("AttachMediaRestrictedForever", R.string.AttachMediaRestrictedForever))
				}
				else {
					progressView.setText(LocaleController.formatString("AttachMediaRestricted", R.string.AttachMediaRestricted, LocaleController.formatDateForBan(chat?.bannedRights?.untilDate?.toLong())))
				}
			}
		}
		else {
			galleryAlbumEntry = if (parentAlert.avatarPicker == 2) {
				MediaController.allMediaAlbumEntry
			}
			else {
				MediaController.allPhotosAlbumEntry
			}
		}

		noGalleryPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			parentAlert.baseFragment.parentActivity?.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED && parentAlert.baseFragment.parentActivity?.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED
		}
		else {
			parentAlert.baseFragment.parentActivity?.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
		}

		galleryAlbumEntry?.let {
			for (a in 0 until min(100, it.photos.size)) {
				val photoEntry = it.photos[a]
				photoEntry.reset()
			}
		}

		clearSelectedPhotos()
		updatePhotosCounter(false)

		cameraPhotoLayoutManager.scrollToPositionWithOffset(0, 1000000)
		layoutManager.scrollToPositionWithOffset(0, 1000000)
		dropDown.text = context.getString(R.string.ChatGallery)

		selectedAlbumEntry = galleryAlbumEntry

		if (selectedAlbumEntry != null) {
			loading = false
			progressView.showTextView()
		}

		updateAlbumsDropDown()
	}

	override fun canScheduleMessages(): Boolean {
		var hasTtl = false

		for ((_, `object`) in Companion.selectedPhotos) {
			if (`object` is PhotoEntry) {
				if (`object`.ttl != 0) {
					hasTtl = true
					break
				}
			}
			else if (`object` is SearchImage) {
				if (`object`.ttl != 0) {
					hasTtl = true
					break
				}
			}
		}

		return !hasTtl
	}

	override fun onButtonsTranslationYUpdated() {
		checkCameraViewPosition()
		invalidate()
	}

	override fun setTranslationY(translationY: Float) {
		if (parentAlert.sheetAnimationType == 1) {
			val scale = -0.1f * (translationY / 40.0f)

			for (child in gridView.children) {
				if (child is PhotoAttachCameraCell) {
					child.imageView.scaleX = 1.0f + scale
					child.imageView.scaleY = 1.0f + scale
				}
				else if (child is PhotoAttachPhotoCell) {
					child.checkBox.scaleX = 1.0f + scale
					child.checkBox.scaleY = 1.0f + scale
				}
			}
		}

		super.setTranslationY(translationY)

		parentAlert.sheetContainer.invalidate()

		invalidate()
	}

	override fun requestLayout() {
		if (ignoreLayout) {
			return
		}

		super.requestLayout()
	}

	override fun onShow(previousLayout: AttachAlertLayout?) {
		headerAnimator?.cancel()
		dropDownContainer.visibility = VISIBLE

		if (previousLayout !is ChatAttachAlertPhotoLayoutPreview) {
			clearSelectedPhotos()
			dropDown.alpha = 1f
		}
		else {
			headerAnimator = dropDown.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.EASE_BOTH)
			headerAnimator?.start()
		}

		parentAlert.actionBar.setTitle("")

		layoutManager.scrollToPositionWithOffset(0, 0)

		if (previousLayout is ChatAttachAlertPhotoLayoutPreview) {
			val setScrollY = Runnable {
				val currentItemTop = previousLayout.currentItemTop
				val paddingTop = previousLayout.listTopPadding
				gridView.scrollBy(0, if (currentItemTop > AndroidUtilities.dp(8f)) paddingTop - currentItemTop else paddingTop)
			}

			gridView.post(setScrollY)
		}

		checkCameraViewPosition()
		resumeCameraPreview()
	}

	override fun onShown() {
		isHidden = false

		cameraView?.visibility = VISIBLE
		cameraIcon?.visibility = VISIBLE

		if (cameraView != null) {
			for (child in gridView.children) {
				if (child is PhotoAttachCameraCell) {
					child.setVisibility(INVISIBLE)
					break
				}
			}
		}

		if (checkCameraWhenShown) {
			checkCameraWhenShown = false
			checkCamera(true)
		}
	}

	fun setCheckCameraWhenShown(checkCameraWhenShown: Boolean) {
		this.checkCameraWhenShown = checkCameraWhenShown
	}

	override fun onHideShowProgress(progress: Float) {
		val cameraView = cameraView ?: return
		cameraView.alpha = progress
		cameraIcon?.alpha = progress

		if (progress != 0f && cameraView.visibility != VISIBLE) {
			cameraView.visibility = VISIBLE
			cameraIcon?.visibility = VISIBLE
		}
		else if (progress == 0f && cameraView.visibility != INVISIBLE) {
			cameraView.visibility = INVISIBLE
			cameraIcon?.visibility = INVISIBLE
		}
	}

	override fun onHide() {
		isHidden = true

		for (child in gridView.children) {
			if (child is PhotoAttachCameraCell) {
				child.setVisibility(VISIBLE)
				saveLastCameraBitmap()
				child.updateBitmap()
				break
			}
		}

		headerAnimator?.cancel()

		headerAnimator = dropDown.animate().alpha(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.EASE_BOTH).withEndAction { dropDownContainer.visibility = GONE }
		headerAnimator?.start()

		pauseCameraPreview()
	}

	private fun pauseCameraPreview() {
		try {
			val cameraSession = cameraView?.cameraSession

			if (cameraSession != null) {
				CameraController.getInstance().stopPreview(cameraSession)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private fun resumeCameraPreview() {
		try {
			val cameraSession = cameraView?.cameraSession

			if (cameraSession != null) {
				CameraController.getInstance().startPreview(cameraSession)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	override fun onHidden() {
		cameraView?.visibility = GONE
		cameraIcon?.visibility = GONE
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		if (lastNotifyWidth != right - left) {
			lastNotifyWidth = right - left
			adapter.notifyDataSetChanged()
		}

		super.onLayout(changed, left, top, right, bottom)

		checkCameraViewPosition()
	}

	override fun onPreMeasure(availableWidth: Int, availableHeight: Int) {
		ignoreLayout = true

		itemsPerRow = if (AndroidUtilities.isTablet()) {
			4
		}
		else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
			4
		}
		else {
			3
		}

		val layoutParams = layoutParams as LayoutParams
		layoutParams.topMargin = ActionBar.getCurrentActionBarHeight()

		itemSize = (availableWidth - AndroidUtilities.dp((6 * 2).toFloat()) - AndroidUtilities.dp((5 * 2).toFloat())) / itemsPerRow

		if (lastItemSize != itemSize) {
			lastItemSize = itemSize

			AndroidUtilities.runOnUIThread {
				adapter.notifyDataSetChanged()
			}
		}

		layoutManager.spanCount = max(1, itemSize * itemsPerRow + AndroidUtilities.dp(5f) * (itemsPerRow - 1))

		val rows = ceil(((adapter.itemCount - 1) / itemsPerRow.toFloat()).toDouble()).toInt()
		val contentSize = rows * itemSize + (rows - 1) * AndroidUtilities.dp(5f)
		val newSize = max(0, availableHeight - contentSize - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp((48 + 12).toFloat()))

		if (gridExtraSpace != newSize) {
			gridExtraSpace = newSize
			adapter.notifyDataSetChanged()
		}

		var paddingTop = if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
			(availableHeight / 3.5f).toInt()
		}
		else {
			availableHeight / 5 * 2
		}

		paddingTop -= AndroidUtilities.dp(52f)

		if (paddingTop < 0) {
			paddingTop = 0
		}

		if (gridView.paddingTop != paddingTop) {
			gridView.setPadding(AndroidUtilities.dp(6f), paddingTop, AndroidUtilities.dp(6f), AndroidUtilities.dp(48f))
		}

		dropDown.textSize = (if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) 18 else 20).toFloat()

		ignoreLayout = false
	}

	override fun canDismissWithTouchOutside(): Boolean {
		return !cameraOpened
	}

	override fun onContainerTranslationUpdated(currentPanTranslationY: Float) {
		this.currentPanTranslationY = currentPanTranslationY
		checkCameraViewPosition()
		invalidate()
	}

	override fun onOpenAnimationEnd() {
		checkCamera(true)
	}

	override fun onDismissWithButtonClick(item: Int) {
		hideCamera(item != 0 && item != 2)
	}

	override fun onDismiss(): Boolean {
		if (cameraAnimationInProgress) {
			return true
		}

		if (cameraOpened) {
			closeCamera(true)
			return true
		}

		hideCamera(true)

		return false
	}

	override fun onSheetKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		if (cameraOpened && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
			shutterButton.delegate.shutterReleased()
			return true
		}

		return false
	}

	override fun onContainerViewTouchEvent(event: MotionEvent?): Boolean {
		if (cameraAnimationInProgress) {
			return true
		}
		else if (cameraOpened) {
			return processTouchEvent(event)
		}

		return false
	}

	override fun onCustomMeasure(view: View?, width: Int, height: Int): Boolean {
		val isPortrait = width < height

		if (view === cameraIcon) {
			cameraIcon?.measure(MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((itemSize - cameraViewOffsetBottomY - cameraViewOffsetY).toInt(), MeasureSpec.EXACTLY))
			return true
		}
		else if (view === cameraView) {
			if (cameraOpened && !cameraAnimationInProgress) {
				cameraView?.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height + parentAlert.bottomInset, MeasureSpec.EXACTLY))
				return true
			}
		}
		else if (view === cameraPanel) {
			if (isPortrait) {
				cameraPanel.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(126f), MeasureSpec.EXACTLY))
			}
			else {
				cameraPanel.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(126f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
			}
			return true
		}
		else if (view === zoomControlView) {
			if (isPortrait) {
				zoomControlView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50f), MeasureSpec.EXACTLY))
			}
			else {
				zoomControlView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
			}
			return true
		}
		else if (view === cameraPhotoRecyclerView) {
			cameraPhotoRecyclerViewIgnoreLayout = true

			if (isPortrait) {
				cameraPhotoRecyclerView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80f), MeasureSpec.EXACTLY))

				if (cameraPhotoLayoutManager.orientation != LinearLayoutManager.HORIZONTAL) {
					cameraPhotoRecyclerView.setPadding(AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f), 0)
					cameraPhotoLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
					cameraAttachAdapter.notifyDataSetChanged()
				}
			}
			else {
				cameraPhotoRecyclerView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))

				if (cameraPhotoLayoutManager.orientation != LinearLayoutManager.VERTICAL) {
					cameraPhotoRecyclerView.setPadding(0, AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f))
					cameraPhotoLayoutManager.orientation = LinearLayoutManager.VERTICAL
					cameraAttachAdapter.notifyDataSetChanged()
				}
			}

			cameraPhotoRecyclerViewIgnoreLayout = false

			return true
		}

		return false
	}

	override fun onCustomLayout(view: View?, left: Int, top: Int, right: Int, bottom: Int): Boolean {
		val width = right - left
		val height = bottom - top
		val isPortrait = width < height

		if (view === cameraPanel) {
			if (isPortrait) {
				if (cameraPhotoRecyclerView.isVisible) {
					cameraPanel.layout(0, bottom - AndroidUtilities.dp((126 + 96).toFloat()), width, bottom - AndroidUtilities.dp(96f))
				}
				else {
					cameraPanel.layout(0, bottom - AndroidUtilities.dp(126f), width, bottom)
				}
			}
			else {
				if (cameraPhotoRecyclerView.isVisible) {
					cameraPanel.layout(right - AndroidUtilities.dp((126 + 96).toFloat()), 0, right - AndroidUtilities.dp(96f), height)
				}
				else {
					cameraPanel.layout(right - AndroidUtilities.dp(126f), 0, right, height)
				}
			}

			return true
		}
		else if (view === zoomControlView) {
			if (isPortrait) {
				if (cameraPhotoRecyclerView.isVisible) {
					zoomControlView.layout(0, bottom - AndroidUtilities.dp((126 + 96 + 38 + 50).toFloat()), width, bottom - AndroidUtilities.dp((126 + 96 + 38).toFloat()))
				}
				else {
					zoomControlView.layout(0, bottom - AndroidUtilities.dp((126 + 50).toFloat()), width, bottom - AndroidUtilities.dp(126f))
				}
			}
			else {
				if (cameraPhotoRecyclerView.isVisible) {
					zoomControlView.layout(right - AndroidUtilities.dp((126 + 96 + 38 + 50).toFloat()), 0, right - AndroidUtilities.dp((126 + 96 + 38).toFloat()), height)
				}
				else {
					zoomControlView.layout(right - AndroidUtilities.dp((126 + 50).toFloat()), 0, right - AndroidUtilities.dp(126f), height)
				}
			}

			return true
		}
		else if (view === counterTextView) {
			var cx: Int
			var cy: Int

			if (isPortrait) {
				cx = (width - counterTextView.measuredWidth) / 2
				cy = bottom - AndroidUtilities.dp((113 + 16 + 38).toFloat())

				counterTextView.rotation = 0f

				if (cameraPhotoRecyclerView.isVisible) {
					cy -= AndroidUtilities.dp(96f)
				}
			}
			else {
				cx = right - AndroidUtilities.dp((113 + 16 + 38).toFloat())
				cy = height / 2 + counterTextView.measuredWidth / 2

				counterTextView.rotation = -90f

				if (cameraPhotoRecyclerView.isVisible) {
					cx -= AndroidUtilities.dp(96f)
				}
			}

			counterTextView.layout(cx, cy, cx + counterTextView.measuredWidth, cy + counterTextView.measuredHeight)

			return true
		}
		else if (view === cameraPhotoRecyclerView) {
			if (isPortrait) {
				val cy = height - AndroidUtilities.dp(88f)
				view.layout(0, cy, view.measuredWidth, cy + view.measuredHeight)
			}
			else {
				val cx = left + width - AndroidUtilities.dp(88f)
				view.layout(cx, 0, cx + view.measuredWidth, view.measuredHeight)
			}

			return true
		}

		return false
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.albumsDidLoad -> {
				galleryAlbumEntry = if (parentAlert.baseFragment is ChatActivity || parentAlert.avatarPicker == 2) {
					MediaController.allMediaAlbumEntry
				}
				else {
					MediaController.allPhotosAlbumEntry
				}

				if (selectedAlbumEntry == null) {
					selectedAlbumEntry = galleryAlbumEntry
				}
				else {
					for (a in MediaController.allMediaAlbums.indices) {
						val entry = MediaController.allMediaAlbums[a]

						if (entry.bucketId == selectedAlbumEntry!!.bucketId && entry.videoOnly == selectedAlbumEntry!!.videoOnly) {
							selectedAlbumEntry = entry
							break
						}
					}
				}

				loading = false

				progressView.showTextView()

				adapter.notifyDataSetChanged()
				cameraAttachAdapter.notifyDataSetChanged()

				if (Companion.selectedPhotosOrder.isNotEmpty() && galleryAlbumEntry != null) {
					var a = 0
					val n = Companion.selectedPhotosOrder.size

					while (a < n) {
						val imageId = Companion.selectedPhotosOrder[a] as Int
						val currentEntry = Companion.selectedPhotos[imageId]
						val entry = galleryAlbumEntry!!.photosByIds[imageId]

						if (entry != null) {
							if (currentEntry is PhotoEntry) {
								entry.copyFrom(currentEntry)
							}

							Companion.selectedPhotos[imageId] = entry
						}

						a++
					}
				}

				updateAlbumsDropDown()
			}

			NotificationCenter.cameraInitied -> {
				checkCamera(false)
			}
		}
	}

	private open inner class BasePhotoProvider : EmptyPhotoViewerProvider() {
		override fun isPhotoChecked(index: Int): Boolean {
			val photoEntry = getPhotoEntryAtPosition(index)
			return photoEntry != null && Companion.selectedPhotos.containsKey(photoEntry.imageId)
		}

		override fun setPhotoChecked(index: Int, videoEditedInfo: VideoEditedInfo?): Int {
			if (parentAlert.maxSelectedPhotos >= 0 && Companion.selectedPhotos.size >= parentAlert.maxSelectedPhotos && !isPhotoChecked(index)) {
				return -1
			}

			val photoEntry = getPhotoEntryAtPosition(index) ?: return -1
			var add = true
			var num: Int

			if (addToSelectedPhotos(photoEntry, -1).also { num = it } == -1) {
				num = Companion.selectedPhotosOrder.indexOf(photoEntry.imageId)
			}
			else {
				add = false
				photoEntry.editedInfo = null
			}

			photoEntry.editedInfo = videoEditedInfo

			for (view in gridView.children) {
				if (view is PhotoAttachPhotoCell) {
					val tag = view.getTag() as Int

					if (tag == index) {
						if (parentAlert.baseFragment is ChatActivity && parentAlert.allowOrder) {
							view.setChecked(num, add, false)
						}
						else {
							view.setChecked(-1, add, false)
						}

						break
					}
				}
			}

			for (view in cameraPhotoRecyclerView.children) {
				if (view is PhotoAttachPhotoCell) {
					val tag = view.getTag() as Int

					if (tag == index) {
						if (parentAlert.baseFragment is ChatActivity && parentAlert.allowOrder) {
							view.setChecked(num, add, false)
						}
						else {
							view.setChecked(-1, add, false)
						}

						break
					}
				}
			}

			parentAlert.updateCountButton(if (add) 1 else 2)

			return num
		}

		override fun getSelectedCount(): Int {
			return Companion.selectedPhotos.size
		}

		override fun getSelectedPhotosOrder(): ArrayList<Any> {
			return Companion.selectedPhotosOrder
		}

		override fun getSelectedPhotos(): HashMap<Any, Any> {
			return Companion.selectedPhotos
		}

		override fun getPhotoIndex(index: Int): Int {
			val photoEntry = getPhotoEntryAtPosition(index) ?: return -1
			return Companion.selectedPhotosOrder.indexOf(photoEntry.imageId)
		}
	}

	private inner class PhotoAttachAdapter(private val mContext: Context, val needCamera: Boolean) : SelectionAdapter() {
		private val viewsCache = ArrayList<RecyclerListView.Holder>(8)
		var itemsCount = 0

		fun createCache() {
			for (a in 0..7) {
				viewsCache.add(createHolder())
			}
		}

		fun createHolder(): RecyclerListView.Holder {
			val cell = PhotoAttachPhotoCell(mContext)

			if (this === adapter) {
				cell.outlineProvider = object : ViewOutlineProvider() {
					override fun getOutline(view: View, outline: Outline) {
						val photoCell = view as PhotoAttachPhotoCell
						var position = photoCell.tag as Int

						if (needCamera && selectedAlbumEntry === galleryAlbumEntry) {
							position++
						}

						when (position) {
							0 -> {
								val rad = AndroidUtilities.dp(8 * parentAlert.cornerRadius)
								outline.setRoundRect(0, 0, view.getMeasuredWidth() + rad, view.getMeasuredHeight() + rad, rad.toFloat())
							}

							itemsPerRow - 1 -> {
								val rad = AndroidUtilities.dp(8 * parentAlert.cornerRadius)
								outline.setRoundRect(-rad, 0, view.getMeasuredWidth(), view.getMeasuredHeight() + rad, rad.toFloat())
							}

							else -> {
								outline.setRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight())
							}
						}
					}
				}
				cell.clipToOutline = true
			}
			cell.setDelegate {
				if (!mediaEnabled || parentAlert.avatarPicker != 0) {
					return@setDelegate
				}

				val index = it.tag as Int
				val photoEntry = it.photoEntry ?: return@setDelegate
				val added = !Companion.selectedPhotos.containsKey(photoEntry.imageId)

				if (added && parentAlert.maxSelectedPhotos >= 0 && Companion.selectedPhotos.size >= parentAlert.maxSelectedPhotos) {
					if (parentAlert.allowOrder && parentAlert.baseFragment is ChatActivity) {
						val chatActivity = parentAlert.baseFragment
						val chat = chatActivity.currentChat

						if (chat != null && !hasAdminRights(chat) && chat.slowmodeEnabled) {
							if (alertOnlyOnce != 2) {
								AlertsCreator.createSimpleAlert(context, context.getString(R.string.Slowmode), context.getString(R.string.SlowmodeSelectSendError))?.show()

								if (alertOnlyOnce == 1) {
									alertOnlyOnce = 2
								}
							}
						}
					}

					return@setDelegate
				}

				val num = if (added) Companion.selectedPhotosOrder.size else -1

				if (parentAlert.baseFragment is ChatActivity && parentAlert.allowOrder) {
					it.setChecked(num, added, true)
				}
				else {
					it.setChecked(-1, added, true)
				}

				addToSelectedPhotos(photoEntry, index)

				var updateIndex = index

				if (this@PhotoAttachAdapter === cameraAttachAdapter) {
					if (adapter.needCamera && selectedAlbumEntry === galleryAlbumEntry) {
						updateIndex++
					}

					adapter.notifyItemChanged(updateIndex)
				}
				else {
					cameraAttachAdapter.notifyItemChanged(updateIndex)
				}

				parentAlert.updateCountButton(if (added) 1 else 2)
			}

			return RecyclerListView.Holder(cell)
		}

		fun getPhoto(position: Int): PhotoEntry? {
			@Suppress("NAME_SHADOWING") var position = position

			if (needCamera && selectedAlbumEntry === galleryAlbumEntry) {
				position--
			}

			return getPhotoEntryAtPosition(position)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			@Suppress("NAME_SHADOWING") var position = position

			when (holder.itemViewType) {
				0 -> {
					if (needCamera && selectedAlbumEntry === galleryAlbumEntry) {
						position--
					}

					val cell = holder.itemView as PhotoAttachPhotoCell

					if (this === adapter) {
						cell.setItemSize(itemSize)
					}
					else {
						cell.setIsVertical(cameraPhotoLayoutManager.orientation == LinearLayoutManager.VERTICAL)
					}

					if (parentAlert.avatarPicker != 0) {
						cell.checkBox.visibility = GONE
					}

					val photoEntry = getPhotoEntryAtPosition(position)

					if (photoEntry != null) {
						cell.setPhotoEntry(photoEntry, needCamera && selectedAlbumEntry === galleryAlbumEntry, position == itemCount - 1)

						if (parentAlert.baseFragment is ChatActivity && parentAlert.allowOrder) {
							cell.setChecked(Companion.selectedPhotosOrder.indexOf(photoEntry.imageId), Companion.selectedPhotos.containsKey(photoEntry.imageId), false)
						}
						else {
							cell.setChecked(-1, Companion.selectedPhotos.containsKey(photoEntry.imageId), false)
						}
					}

					cell.imageView.tag = position

					cell.tag = position
				}

				1 -> {
					cameraCell = holder.itemView as PhotoAttachCameraCell

					if (cameraView?.isInited == true && !isHidden) {
						cameraCell?.visibility = INVISIBLE
					}
					else {
						cameraCell?.visibility = VISIBLE
					}

					cameraCell?.setItemSize(itemSize)
				}

				3 -> {
					val cell = holder.itemView as PhotoAttachPermissionCell
					cell.setItemSize(itemSize)
					cell.setType(if (needCamera && noCameraPermissions && position == 0) 0 else 1)
				}
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return false
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val holder: RecyclerListView.Holder

			when (viewType) {
				0 -> if (viewsCache.isNotEmpty()) {
					holder = viewsCache[0]
					viewsCache.removeAt(0)
				}
				else {
					holder = createHolder()
				}

				1 -> {
					val cameraCell = PhotoAttachCameraCell(mContext)

					cameraCell.outlineProvider = object : ViewOutlineProvider() {
						override fun getOutline(view: View, outline: Outline) {
							val rad = AndroidUtilities.dp(8 * parentAlert.cornerRadius)
							outline.setRoundRect(0, 0, view.measuredWidth + rad, view.measuredHeight + rad, rad.toFloat())
						}
					}

					cameraCell.clipToOutline = true

					holder = RecyclerListView.Holder(cameraCell.also { this@ChatAttachAlertPhotoLayout.cameraCell = cameraCell })
				}

				2 -> holder = RecyclerListView.Holder(object : View(mContext) {
					override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
						super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(gridExtraSpace, MeasureSpec.EXACTLY))
					}
				})

				3 -> {
					holder = RecyclerListView.Holder(PhotoAttachPermissionCell(mContext))
				}

				else -> {
					holder = RecyclerListView.Holder(PhotoAttachPermissionCell(mContext))
				}
			}

			return holder
		}

		override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
			if (holder.itemView is PhotoAttachCameraCell) {
				holder.itemView.updateBitmap()
			}
		}

		override fun getItemCount(): Int {
			if (!mediaEnabled) {
				return 1
			}

			var count = 0

			if (needCamera && selectedAlbumEntry === galleryAlbumEntry) {
				count++
			}

			if (noGalleryPermissions && this === adapter) {
				count++
			}

			count += cameraPhotos.size
			count += selectedAlbumEntry?.photos?.size ?: 0

			if (this === adapter) {
				count++
			}

			return count.also { itemsCount = it }
		}

		override fun getItemViewType(position: Int): Int {
			if (!mediaEnabled) {
				return 2
			}

			if (needCamera && position == 0 && selectedAlbumEntry === galleryAlbumEntry) {
				return if (noCameraPermissions) {
					3
				}
				else {
					1
				}
			}

			if (this === adapter && position == itemsCount - 1) {
				return 2
			}
			else if (noGalleryPermissions) {
				return 3
			}

			return 0
		}

		override fun notifyDataSetChanged() {
			super.notifyDataSetChanged()

			if (this === adapter) {
				progressView.visibility = if (itemCount == 1 && selectedAlbumEntry == null || !mediaEnabled) VISIBLE else INVISIBLE
			}
		}
	}

	companion object {
		const val group = 0
		const val compress = 1
		const val open_in = 2
		const val preview = 3
		private val cameraPhotos = ArrayList<Any?>()
		private val selectedPhotos = HashMap<Any, Any>()
		private val selectedPhotosOrder = ArrayList<Any>()
		private var mediaFromExternalCamera = false
		private var lastImageId = -1
	}
}
