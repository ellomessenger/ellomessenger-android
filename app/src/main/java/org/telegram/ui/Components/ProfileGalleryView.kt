/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.text.TextUtils
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.ImageReceiver.ImageReceiverDelegate
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.dcId
import org.telegram.tgnet.videoSizes
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.PinchToZoomHelper
import kotlin.math.abs
import kotlin.math.min

open class ProfileGalleryView : CircularViewPager, NotificationCenterDelegate {
	private val downPoint = PointF()
	private val touchSlop: Int
	private val parentActionBar: ActionBar
	private val parentListView: RecyclerListView
	private val parentClassGuid: Int
	private val callback: Callback?
	private val isProfileFragment: Boolean
	private val radialProgresses = SparseArray<RadialProgress2>()
	private val path = Path()
	private val rect = RectF()
	private val radii = FloatArray(8)
	var pinchToZoomHelper: PinchToZoomHelper? = null
	private var currentUploadingImageLocation: ImageLocation? = null
	private var currentUploadingThumbLocation: ImageLocation? = null
	private var isScrollingListView = true
	private var isSwipingViewPager = true
	private var adapter: ViewPagerAdapter? = null
	private var dialogId: Long = 0
	private var chatInfo: ChatFull? = null
	private var scrolledByUser = false
	private var isDownReleased = false
	private var uploadingImageLocation: ImageLocation? = null
	private val currentAccount = UserConfig.selectedAccount
	private var prevImageLocation: ImageLocation? = null
	private val videoFileNames = mutableListOf<String?>()
	private val thumbsFileNames = mutableListOf<String?>()
	private val photos = mutableListOf<TLRPC.Photo?>()
	private val videoLocations = mutableListOf<ImageLocation?>()
	private val imagesLocations = mutableListOf<ImageLocation?>()
	private val thumbsLocations = mutableListOf<ImageLocation?>()
	private val imagesLocationsSizes = mutableListOf<Int>()
	private val imagesUploadProgress = mutableListOf<Float?>()
	private var settingMainPhoto = 0
	var createThumbFromParent = true
	private var forceResetPosition = false
	var invalidateWithParent = false
	private var hasActiveVideo = false
	private var imagesLayerNum = 0
	private var roundTopRadius = 0
	private var roundBottomRadius = 0

	constructor(context: Context, parentActionBar: ActionBar, parentListView: RecyclerListView, callback: Callback?) : super(context) {
		offscreenPageLimit = 2
		isProfileFragment = false
		this.parentListView = parentListView
		parentClassGuid = ConnectionsManager.generateClassGuid()
		this.parentActionBar = parentActionBar
		touchSlop = ViewConfiguration.get(context).scaledTouchSlop
		this.callback = callback

		addOnPageChangeListener(object : OnPageChangeListener {
			override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
				val adapter = adapter ?: return
				var innerPosition = position

				if (positionOffsetPixels == 0) {
					innerPosition = adapter.getRealPosition(innerPosition)

					if (hasActiveVideo) {
						innerPosition--
					}

					val count = childCount

					for (a in 0 until count) {
						val child = getChildAt(a) as? BackupImageView ?: continue
						var p = adapter.getRealPosition(adapter.imageViews.indexOf(child))

						if (hasActiveVideo) {
							p--
						}

						val imageReceiver = child.imageReceiver
						val currentAllow = imageReceiver.allowStartAnimation

						if (p == innerPosition) {
							if (!currentAllow) {
								imageReceiver.allowStartAnimation = true
								imageReceiver.startAnimation()
							}

							val location = videoLocations[p]

							if (location != null) {
								FileLoader.getInstance(currentAccount).setForceStreamLoadingFile(location.location, "mp4")
							}
						}
						else {
							if (currentAllow) {
								val fileDrawable = imageReceiver.animation

								if (fileDrawable != null) {
									val location = videoLocations[p]

									if (location != null) {
										fileDrawable.seekTo(location.videoSeekTo, false, true)
									}
								}

								imageReceiver.allowStartAnimation = false
								imageReceiver.stopAnimation()
							}
						}
					}
				}
			}

			override fun onPageSelected(position: Int) {
				// unused
			}

			override fun onPageScrollStateChanged(state: Int) {
				// unused
			}
		})

		setAdapter(ViewPagerAdapter(getContext(), null).also { adapter = it })

		with(NotificationCenter.getInstance(currentAccount)) {
			addObserver(this@ProfileGalleryView, NotificationCenter.dialogPhotosLoaded)
			addObserver(this@ProfileGalleryView, NotificationCenter.fileLoaded)
			addObserver(this@ProfileGalleryView, NotificationCenter.fileLoadProgressChanged)
			addObserver(this@ProfileGalleryView, NotificationCenter.reloadDialogPhotos)
		}
	}

	constructor(context: Context, dialogId: Long, parentActionBar: ActionBar, parentListView: RecyclerListView, parentAvatarImageView: org.telegram.ui.AvatarImageView?, parentClassGuid: Int, callback: Callback?) : super(context) {
		visibility = GONE
		overScrollMode = OVER_SCROLL_NEVER
		offscreenPageLimit = 2
		isProfileFragment = true
		this.dialogId = dialogId
		this.parentListView = parentListView
		this.parentClassGuid = parentClassGuid
		this.parentActionBar = parentActionBar
		setAdapter(ViewPagerAdapter(getContext(), parentAvatarImageView).also { adapter = it })
		touchSlop = ViewConfiguration.get(context).scaledTouchSlop
		this.callback = callback

		addOnPageChangeListener(object : OnPageChangeListener {
			override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
				val adapter = adapter ?: return
				var innerPosition = position

				if (positionOffsetPixels == 0) {
					innerPosition = adapter.getRealPosition(innerPosition)

					val count = childCount

					for (a in 0 until count) {
						val child = getChildAt(a) as? BackupImageView ?: continue
						val p = adapter.getRealPosition(adapter.imageViews.indexOf(child))
						val imageReceiver = child.imageReceiver
						val currentAllow = imageReceiver.allowStartAnimation

						if (p == innerPosition) {
							if (!currentAllow) {
								imageReceiver.allowStartAnimation = true
								imageReceiver.startAnimation()
							}

							val location = videoLocations[p]

							if (location != null) {
								FileLoader.getInstance(currentAccount).setForceStreamLoadingFile(location.location, "mp4")
							}
						}
						else {
							if (currentAllow) {
								val fileDrawable = imageReceiver.animation

								if (fileDrawable != null) {
									val location = videoLocations[p]

									if (location != null) {
										fileDrawable.seekTo(location.videoSeekTo, false, true)
									}
								}

								imageReceiver.allowStartAnimation = false
								imageReceiver.stopAnimation()
							}
						}
					}
				}
			}

			override fun onPageSelected(position: Int) {
				// unused
			}

			override fun onPageScrollStateChanged(state: Int) {
				// unused
			}
		})

		with(NotificationCenter.getInstance(currentAccount)) {
			addObserver(this@ProfileGalleryView, NotificationCenter.dialogPhotosLoaded)
			addObserver(this@ProfileGalleryView, NotificationCenter.fileLoaded)
			addObserver(this@ProfileGalleryView, NotificationCenter.fileLoadProgressChanged)
			addObserver(this@ProfileGalleryView, NotificationCenter.reloadDialogPhotos)
		}

		MessagesController.getInstance(currentAccount).loadDialogPhotos(dialogId, 80, 0, true, parentClassGuid)
	}

	fun setHasActiveVideo(hasActiveVideo: Boolean) {
		this.hasActiveVideo = hasActiveVideo
	}

	fun findVideoActiveView(): View? {
		if (!hasActiveVideo) {
			return null
		}

		for (view in children) {
			if (view is TextureStubView) {
				return view
			}
		}

		return null
	}

	fun setImagesLayerNum(value: Int) {
		imagesLayerNum = value
	}

	fun onDestroy() {
		with(NotificationCenter.getInstance(currentAccount)) {
			removeObserver(this@ProfileGalleryView, NotificationCenter.dialogPhotosLoaded)
			removeObserver(this@ProfileGalleryView, NotificationCenter.fileLoaded)
			removeObserver(this@ProfileGalleryView, NotificationCenter.fileLoadProgressChanged)
			removeObserver(this@ProfileGalleryView, NotificationCenter.reloadDialogPhotos)
		}

		for (child in children) {
			if (child is BackupImageView) {
				if (child.imageReceiver.hasStaticThumb()) {
					val drawable = child.imageReceiver.drawable

					if (drawable is AnimatedFileDrawable) {
						drawable.removeSecondParentView(child)
					}
				}
			}
		}
	}

	fun setAnimatedFileMaybe(drawable: AnimatedFileDrawable?) {
		if (drawable == null) {
			return
		}

		val adapter = adapter ?: return

		for (child in children) {
			if (child !is BackupImageView) {
				continue
			}

			val p = adapter.getRealPosition(adapter.imageViews.indexOf(child))

			if (p != 0) {
				continue
			}

			val imageReceiver = child.imageReceiver
			val currentDrawable = imageReceiver.animation

			if (currentDrawable === drawable) {
				continue
			}

			currentDrawable?.removeSecondParentView(child)

			child.setImageDrawable(drawable)

			drawable.addSecondParentView(this)
			drawable.setInvalidateParentViewWithSecond(true)
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(ev: MotionEvent): Boolean {
		val adapter = adapter ?: return false

		if (parentListView.scrollState != RecyclerView.SCROLL_STATE_IDLE && !isScrollingListView && isSwipingViewPager) {
			isSwipingViewPager = false
			val cancelEvent = MotionEvent.obtain(ev)
			cancelEvent.action = MotionEvent.ACTION_CANCEL
			super.onTouchEvent(cancelEvent)
			cancelEvent.recycle()
			return false
		}

		val action = ev.action

		val pinchToZoomHelper = pinchToZoomHelper

		if (pinchToZoomHelper != null && currentItemView != null) {
			if (action != MotionEvent.ACTION_DOWN && isDownReleased && !pinchToZoomHelper.isInOverlayMode) {
				pinchToZoomHelper.checkPinchToZoom(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0), this, currentItemView?.imageReceiver, null)
			}
			else if (pinchToZoomHelper.checkPinchToZoom(ev, this, currentItemView?.imageReceiver, null)) {
				if (!isDownReleased) {
					isDownReleased = true
					callback?.onRelease()
				}

				return true
			}
		}

		if (action == MotionEvent.ACTION_DOWN) {
			isScrollingListView = true
			isSwipingViewPager = true
			scrolledByUser = true
			downPoint[ev.x] = ev.y

			if (adapter.count > 1) {
				callback?.onDown(ev.x < width / 3f)
			}

			isDownReleased = false
		}
		else if (action == MotionEvent.ACTION_UP) {
			if (!isDownReleased) {
				val itemsCount = adapter.count
				var currentItem = currentItem

				if (itemsCount > 1) {
					if (ev.x > width / 3f) {
						val extraCount = adapter.extraCount

						if (++currentItem >= itemsCount - extraCount) {
							currentItem = extraCount
						}
					}
					else {
						val extraCount = adapter.extraCount

						if (--currentItem < extraCount) {
							currentItem = itemsCount - extraCount - 1
						}
					}

					callback?.onRelease()

					setCurrentItem(currentItem, false)
				}
			}
		}
		else if (action == MotionEvent.ACTION_MOVE) {
			val dx = ev.x - downPoint.x
			val dy = ev.y - downPoint.y
			val move = abs(dy) >= touchSlop || abs(dx) >= touchSlop

			if (move) {
				isDownReleased = true
				callback?.onRelease()
			}

			if (isSwipingViewPager && isScrollingListView) {
				if (move) {
					if (abs(dy) > abs(dx)) {
						isSwipingViewPager = false
						val cancelEvent = MotionEvent.obtain(ev)
						cancelEvent.action = MotionEvent.ACTION_CANCEL
						super.onTouchEvent(cancelEvent)
						cancelEvent.recycle()
					}
					else {
						isScrollingListView = false
						val cancelEvent = MotionEvent.obtain(ev)
						cancelEvent.action = MotionEvent.ACTION_CANCEL
						parentListView.onTouchEvent(cancelEvent)
						cancelEvent.recycle()
					}
				}
			}
			else if (isSwipingViewPager && !canScrollHorizontally(-1) && dx > touchSlop) {
				return false
			}
		}

		var result = false

		if (isScrollingListView) {
			result = parentListView.onTouchEvent(ev)
		}

		if (isSwipingViewPager) {
			result = result or super.onTouchEvent(ev)
		}

		if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
			isScrollingListView = false
			isSwipingViewPager = false
		}

		return result
	}

	fun setChatInfo(chatFull: ChatFull?) {
		chatInfo = chatFull

		if (photos.isNotEmpty() && photos.firstOrNull() == null && chatInfo != null && FileLoader.isSamePhoto(imagesLocations.firstOrNull()?.location, chatInfo?.chatPhoto)) {
			photos[0] = chatInfo?.chatPhoto

			if (!chatInfo?.chatPhoto?.videoSizes.isNullOrEmpty()) {
				val videoSize = chatInfo?.chatPhoto?.videoSizes?.firstOrNull()
				videoLocations[0] = ImageLocation.getForPhoto(videoSize, chatInfo?.chatPhoto)
				videoFileNames[0] = FileLoader.getAttachFileName(videoSize)
				callback?.onPhotosLoaded()
			}
			else {
				videoLocations[0] = null
				videoFileNames.add(0, null)
			}

			imagesUploadProgress[0] = null

			adapter?.notifyDataSetChanged()
		}
	}

	fun initIfEmpty(imageLocation: ImageLocation?, thumbLocation: ImageLocation?, reload: Boolean): Boolean {
		if (imageLocation == null || thumbLocation == null || settingMainPhoto != 0) {
			return false
		}

		if (prevImageLocation == null || prevImageLocation?.location?.localId != imageLocation.location?.localId) {
			if (imagesLocations.isNotEmpty()) {
				prevImageLocation = imageLocation

				if (reload) {
					MessagesController.getInstance(currentAccount).loadDialogPhotos(dialogId, 80, 0, false, parentClassGuid)
				}

				return true
			}
			else {
				if (reload) {
					MessagesController.getInstance(currentAccount).loadDialogPhotos(dialogId, 80, 0, false, parentClassGuid)
				}
			}
		}

		if (imagesLocations.isNotEmpty()) {
			return false
		}

		prevImageLocation = imageLocation
		thumbsFileNames.add(null)
		videoFileNames.add(null)
		imagesLocations.add(imageLocation)
		thumbsLocations.add(thumbLocation)
		videoLocations.add(null)
		photos.add(null)
		imagesLocationsSizes.add(-1)
		imagesUploadProgress.add(null)
		adapter?.notifyDataSetChanged()

		resetCurrentItem()

		return true
	}

	fun addUploadingImage(imageLocation: ImageLocation?, thumbLocation: ImageLocation?) {
		prevImageLocation = imageLocation
		thumbsFileNames.add(0, null)
		videoFileNames.add(0, null)
		imagesLocations.add(0, imageLocation)
		thumbsLocations.add(0, thumbLocation)
		videoLocations.add(0, null)
		photos.add(0, null)
		imagesLocationsSizes.add(0, -1)
		imagesUploadProgress.add(0, 0f)
		adapter?.notifyDataSetChanged()

		resetCurrentItem()

		currentUploadingImageLocation = imageLocation
		currentUploadingThumbLocation = thumbLocation
	}

	fun removeUploadingImage(imageLocation: ImageLocation?) {
		uploadingImageLocation = imageLocation
		currentUploadingImageLocation = null
		currentUploadingThumbLocation = null
	}

	fun getImageLocation(index: Int?): ImageLocation? {
		if (index == null || index < 0 || index >= imagesLocations.size) {
			return null
		}

		val location = videoLocations[index]
		return location ?: imagesLocations[index]
	}

	fun getRealImageLocation(index: Int): ImageLocation? {
		return if (index < 0 || index >= imagesLocations.size) {
			null
		}
		else {
			imagesLocations[index]
		}
	}

	fun hasImages(): Boolean {
		return imagesLocations.isNotEmpty()
	}

	val currentItemView: BackupImageView?
		get() = adapter?.objects?.getOrNull(currentItem)?.imageView

	val isLoadingCurrentVideo: Boolean
		get() {
			if (videoLocations[if (hasActiveVideo) realPosition - 1 else realPosition] == null) {
				return false
			}

			val imageView = currentItemView ?: return false
			val drawable = imageView.imageReceiver.animation

			return drawable == null || !drawable.hasBitmap()
		}

	val currentItemProgress: Float
		get() {
			val imageView = currentItemView ?: return 0.0f
			val drawable = imageView.imageReceiver.animation ?: return 0.0f
			return drawable.currentProgress
		}

	val isCurrentItemVideo: Boolean
		get() {
			var i = realPosition

			if (hasActiveVideo) {
				if (i == 0) {
					return false
				}

				i--
			}

			return videoLocations[i] != null
		}

	fun getCurrentVideoLocation(thumbLocation: ImageLocation?, imageLocation: ImageLocation?): ImageLocation? {
		if (thumbLocation == null) {
			return null
		}

		if (imageLocation == null) {
			return null
		}

		for (b in 0..1) {
			val arrayList = if (b == 0) thumbsLocations else imagesLocations
			var a = 0
			val n = arrayList.size

			while (a < n) {
				val loc = arrayList[a]

				if (loc?.location == null) {
					a++
					continue
				}

				if (loc.dcId == thumbLocation.dcId && loc.location?.localId == thumbLocation.location?.localId && loc.location?.volumeId == thumbLocation.location?.volumeId || loc.dcId == imageLocation.dcId && loc.location?.localId == imageLocation.location?.localId && loc.location?.volumeId == imageLocation.location?.volumeId) {
					return videoLocations[a]
				}

				a++
			}
		}

		return null
	}

	fun resetCurrentItem() {
		setCurrentItem(adapter?.extraCount ?: 0, false)
	}

	val realCount: Int
		get() {
			var size = photos.size

			if (hasActiveVideo) {
				size++
			}

			return size
		}

	fun getRealPosition(position: Int): Int {
		return adapter?.getRealPosition(position) ?: -1
	}

	val realPosition: Int
		get() = adapter?.getRealPosition(currentItem) ?: -1

	fun getPhoto(index: Int): TLRPC.Photo? {
		return photos.getOrNull(index)
	}

	fun replaceFirstPhoto(oldPhoto: TLRPC.Photo?, photo: TLRPC.Photo?) {
		if (photos.isEmpty()) {
			return
		}

		val idx = photos.indexOf(oldPhoto)

		if (idx < 0) {
			return
		}

		photos[idx] = photo
	}

	fun finishSettingMainPhoto() {
		settingMainPhoto--
	}

	fun startMovePhotoToBegin(index: Int) {
		if (index <= 0 || index >= photos.size) {
			return
		}

		settingMainPhoto++

		val photo = photos[index]

		photos.removeAt(index)
		photos.add(0, photo)

		val name = thumbsFileNames[index]

		thumbsFileNames.removeAt(index)
		thumbsFileNames.add(0, name)
		videoFileNames.add(0, videoFileNames.removeAt(index))

		var location = videoLocations[index]

		videoLocations.removeAt(index)
		videoLocations.add(0, location)

		location = imagesLocations[index]

		imagesLocations.removeAt(index)
		imagesLocations.add(0, location)

		location = thumbsLocations[index]

		thumbsLocations.removeAt(index)
		thumbsLocations.add(0, location)

		val size = imagesLocationsSizes[index]

		imagesLocationsSizes.removeAt(index)
		imagesLocationsSizes.add(0, size)

		val uploadProgress = imagesUploadProgress[index]

		imagesUploadProgress.removeAt(index)
		imagesUploadProgress.add(0, uploadProgress)

		prevImageLocation = imagesLocations[0]
	}

	fun commitMoveToBegin() {
		adapter?.notifyDataSetChanged()
		resetCurrentItem()
	}

	fun removePhotoAtIndex(index: Int): Boolean {
		if (index < 0 || index >= photos.size) {
			return false
		}

		photos.removeAt(index)
		thumbsFileNames.removeAt(index)
		videoFileNames.removeAt(index)
		videoLocations.removeAt(index)
		imagesLocations.removeAt(index)
		thumbsLocations.removeAt(index)
		imagesLocationsSizes.removeAt(index)
		radialProgresses.delete(index)
		imagesUploadProgress.removeAt(index)

		if (index == 0 && imagesLocations.isNotEmpty()) {
			prevImageLocation = imagesLocations[0]
		}

		adapter?.notifyDataSetChanged()

		return photos.isEmpty()
	}

	override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
		if (parentListView.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
			return false
		}

		parent?.parent?.requestDisallowInterceptTouchEvent(canScrollHorizontally(-1))

		return super.onInterceptTouchEvent(e)
	}

	private fun loadNeighboringThumbs() {
		val locationsCount = thumbsLocations.size

		if (locationsCount > 1) {
			for (i in 0 until if (locationsCount > 2) 2 else 1) {
				val pos = if (i == 0) 1 else locationsCount - 1
				FileLoader.getInstance(currentAccount).loadFile(thumbsLocations[pos], null, null, FileLoader.PRIORITY_LOW, 1)
			}
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.dialogPhotosLoaded -> {
				val guid = args[3] as Int
				val did = args[0] as Long

				if (did == dialogId && parentClassGuid == guid && adapter != null) {
					val fromCache = args[2] as Boolean
					val arrayList = (args[4] as List<TLRPC.Photo>).reversed()

					thumbsFileNames.clear()
					videoFileNames.clear()
					imagesLocations.clear()
					videoLocations.clear()
					thumbsLocations.clear()
					photos.clear()
					imagesLocationsSizes.clear()
					imagesUploadProgress.clear()

					var currentImageLocation: ImageLocation? = null

					if (DialogObject.isChatDialog(did)) {
						val chat = MessagesController.getInstance(currentAccount).getChat(-did)

						currentImageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_BIG)

						if (currentImageLocation != null) {
							imagesLocations.add(currentImageLocation)
							thumbsLocations.add(ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL))
							thumbsFileNames.add(null)

							if (chatInfo != null && FileLoader.isSamePhoto(currentImageLocation.location, chatInfo?.chatPhoto)) {
								photos.add(chatInfo?.chatPhoto)

								if (!chatInfo?.chatPhoto?.videoSizes.isNullOrEmpty()) {
									val videoSize = chatInfo?.chatPhoto?.videoSizes?.firstOrNull()
									videoLocations.add(ImageLocation.getForPhoto(videoSize, chatInfo?.chatPhoto))
									videoFileNames.add(FileLoader.getAttachFileName(videoSize))
								}
								else {
									videoLocations.add(null)
									videoFileNames.add(null)
								}
							}
							else {
								photos.add(null)
								videoFileNames.add(null)
								videoLocations.add(null)
							}

							imagesLocationsSizes.add(-1)
							imagesUploadProgress.add(null)
						}
					}

					for (photo in arrayList) {
						if (photo !is TLRPC.TLPhoto || photo.sizes.isEmpty()) {
							continue
						}

						var sizeThumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 50)

						for (photoSize in photo.sizes) {
							if (photoSize is TLRPC.TLPhotoStrippedSize) {
								sizeThumb = photoSize
								break
							}
						}

						if (currentImageLocation != null) {
							var cont = false

							for (size in photo.sizes) {
								if (size.location != null && size.location?.localId == currentImageLocation.location?.localId && size.location?.volumeId == currentImageLocation.location?.volumeId) {
									photos[0] = photo

									if (photo.videoSizes.isNotEmpty()) {
										videoLocations[0] = ImageLocation.getForPhoto(photo.videoSizes[0], photo)
									}

									cont = true

									break
								}
							}

							if (cont) {
								continue
							}
						}

						val sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 640)

						if (sizeFull != null) {
							if (photo.dcId != 0) {
								(sizeFull.location as? TLRPC.TLFileLocation)?.let {
									it.dcId = photo.dcId
									// it.fileReference = photo.fileReference
								}
							}

							val location = ImageLocation.getForPhoto(sizeFull, photo)

							if (location != null) {
								imagesLocations.add(location)
								thumbsFileNames.add(FileLoader.getAttachFileName(if (sizeThumb is TLRPC.TLPhotoStrippedSize) sizeFull else sizeThumb))
								thumbsLocations.add(ImageLocation.getForPhoto(sizeThumb, photo))

								if (photo.videoSizes.isNotEmpty()) {
									val videoSize = photo.videoSizes[0]
									videoLocations.add(ImageLocation.getForPhoto(videoSize, photo))
									videoFileNames.add(FileLoader.getAttachFileName(videoSize))
								}
								else {
									videoLocations.add(null)
									videoFileNames.add(null)
								}

								photos.add(photo)
								imagesLocationsSizes.add(sizeFull.size)
								imagesUploadProgress.add(null)
							}
						}
					}

					loadNeighboringThumbs()
					getAdapter()?.notifyDataSetChanged()

					if (isProfileFragment) {
						if (!scrolledByUser || forceResetPosition) {
							resetCurrentItem()
						}
					}
					else {
						if (!scrolledByUser || forceResetPosition) {
							resetCurrentItem()
							getAdapter()?.notifyDataSetChanged()
						}
					}

					forceResetPosition = false

					if (fromCache) {
						MessagesController.getInstance(currentAccount).loadDialogPhotos(did, 80, 0, false, parentClassGuid)
					}

					callback?.onPhotosLoaded()

					if (currentUploadingImageLocation != null) {
						addUploadingImage(currentUploadingImageLocation, currentUploadingThumbLocation)
					}
				}
			}

			NotificationCenter.fileLoaded -> {
				val fileName = args[0] as String

				for (i in thumbsFileNames.indices) {
					var fileName2 = videoFileNames[i]

					if (fileName2 == null) {
						fileName2 = thumbsFileNames[i]
					}

					if (fileName2 != null && TextUtils.equals(fileName, fileName2)) {
						val radialProgress = radialProgresses[i]
						radialProgress?.setProgress(1f, true)
					}
				}
			}

			NotificationCenter.fileLoadProgressChanged -> {
				val fileName = args[0] as String

				for (i in thumbsFileNames.indices) {
					var fileName2 = videoFileNames[i]

					if (fileName2 == null) {
						fileName2 = thumbsFileNames[i]
					}

					if (fileName2 != null && TextUtils.equals(fileName, fileName2)) {
						val radialProgress = radialProgresses[i]

						if (radialProgress != null) {
							val loadedSize = args[1] as Long
							val totalSize = args[2] as Long
							val progress = min(1f, loadedSize / totalSize.toFloat())
							radialProgress.setProgress(progress, true)
						}
					}
				}
			}

			NotificationCenter.reloadDialogPhotos -> {
				if (settingMainPhoto != 0) {
					return
				}

				MessagesController.getInstance(currentAccount).loadDialogPhotos(dialogId, 80, 0, true, parentClassGuid)
			}
		}
	}

	fun setData(dialogId: Long) {
		setData(dialogId, false)
	}

	fun setData(dialogId: Long, forceReset: Boolean) {
		if (this.dialogId == dialogId && !forceReset) {
			resetCurrentItem()
			return
		}

		forceResetPosition = true
		adapter?.notifyDataSetChanged()

		reset()

		this.dialogId = dialogId

		if (dialogId != 0L) {
			MessagesController.getInstance(currentAccount).loadDialogPhotos(dialogId, 80, 0, true, parentClassGuid)
		}
	}

	private fun reset() {
		videoFileNames.clear()
		thumbsFileNames.clear()
		photos.clear()
		videoLocations.clear()
		imagesLocations.clear()
		thumbsLocations.clear()
		imagesLocationsSizes.clear()
		imagesUploadProgress.clear()
		adapter?.notifyDataSetChanged()
		uploadingImageLocation = null
	}

	fun setRoundRadius(topRadius: Int, bottomRadius: Int) {
		roundTopRadius = topRadius
		roundBottomRadius = bottomRadius

		adapter?.objects?.forEach {
			it.imageView?.setRoundRadius(roundTopRadius, roundTopRadius, roundBottomRadius, roundBottomRadius)
		}
	}

	fun setParentAvatarImage(parentImageView: BackupImageView?) {
		adapter?.parentAvatarImageView = parentImageView
	}

	fun setUploadProgress(imageLocation: ImageLocation?, p: Float) {
		if (imageLocation == null) {
			return
		}

		for (i in imagesLocations.indices) {
			if (imagesLocations[i] === imageLocation) {
				imagesUploadProgress[i] = p
				radialProgresses[i]?.setProgress(p, true)
				break
			}
		}

		children.forEach {
			it.invalidate()
		}
	}

	interface Callback {
		fun onDown(left: Boolean)
		fun onRelease()
		fun onPhotosLoaded()
		fun onVideoSet()
	}

	class Item {
		var isActiveVideo = false
		var textureViewStubView: View? = null
		var imageView: AvatarImageView? = null
	}

	inner class ViewPagerAdapter(private val context: Context, parentAvatarImageView: org.telegram.ui.AvatarImageView?) : Adapter() {
		val objects = ArrayList<Item>()
		val imageViews = ArrayList<BackupImageView?>()
		private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		var parentAvatarImageView: BackupImageView? = parentAvatarImageView

		init {
			placeholderPaint.color = Color.BLACK
		}

		override fun getCount(): Int {
			return objects.size
		}

		override fun isViewFromObject(view: View, `object`: Any): Boolean {
			val item = `object` as Item

			return if (item.isActiveVideo) {
				view === item.textureViewStubView
			}
			else {
				view === item.imageView
			}
		}

		override fun getItemPosition(`object`: Any): Int {
			val idx = objects.indexOf(`object` as Item)
			return if (idx == -1) POSITION_NONE else idx
		}

		override fun instantiateItem(container: ViewGroup, position: Int): Item {
			val item = objects[position]
			val realPosition = getRealPosition(position)

			if (hasActiveVideo && realPosition == 0) {
				item.isActiveVideo = true

				if (item.textureViewStubView == null) {
					item.textureViewStubView = TextureStubView(context)
				}

				if (item.textureViewStubView?.parent == null) {
					container.addView(item.textureViewStubView)
				}

				return item
			}
			else {
				item.isActiveVideo = false
			}

			if (item.textureViewStubView?.parent != null) {
				container.removeView(item.textureViewStubView)
			}

			if (item.imageView == null) {
				item.imageView = AvatarImageView(context, position, placeholderPaint)
				imageViews[position] = item.imageView
			}

			if (item.imageView?.parent == null) {
				container.addView(item.imageView)
			}

			item.imageView?.imageReceiver?.setAllowDecodeSingleFrame(true)

			val imageLocationPosition = if (hasActiveVideo) realPosition - 1 else realPosition
			var needProgress = false

			if (imageLocationPosition == 0) {
				val drawable = parentAvatarImageView?.imageReceiver?.drawable

				if (drawable is AnimatedFileDrawable && drawable.hasBitmap()) {
					item.imageView?.setImageDrawable(drawable)

					drawable.addSecondParentView(item.imageView)
					drawable.setInvalidateParentViewWithSecond(true)
				}
				else {
					val videoLocation = videoLocations[imageLocationPosition]
					item.imageView?.isVideo = videoLocation != null
					needProgress = true
					val filter = if (isProfileFragment && videoLocation != null && videoLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
						"avatar"
					}
					else {
						null
					}

					val location = thumbsLocations[imageLocationPosition]
					val thumb = if (parentAvatarImageView == null || !createThumbFromParent) null else parentAvatarImageView?.imageReceiver?.bitmap
					val parent = "avatar_$dialogId"

					if (thumb != null) {
						item.imageView?.setImageMedia(videoLocations[imageLocationPosition], filter, imagesLocations[imageLocationPosition], null, thumb, imagesLocationsSizes[imageLocationPosition], 1, parent)
					}
					else if (uploadingImageLocation != null) {
						item.imageView?.setImageMedia(videoLocations[imageLocationPosition], filter, imagesLocations[imageLocationPosition], null, uploadingImageLocation, null, null, imagesLocationsSizes[imageLocationPosition], 1, parent)
					}
					else {
						val thumbFilter = if (location?.photoSize is TLRPC.TLPhotoStrippedSize) "b" else null
						item.imageView?.setImageMedia(videoLocation, null, imagesLocations[imageLocationPosition], null, thumbsLocations[imageLocationPosition], thumbFilter, null, imagesLocationsSizes[imageLocationPosition], 1, parent)
					}
				}
			}
			else {
				val videoLocation = videoLocations[imageLocationPosition]
				item.imageView?.isVideo = videoLocation != null
				needProgress = true

				val location = thumbsLocations[imageLocationPosition]
				val filter = if (location?.photoSize is TLRPC.TLPhotoStrippedSize) "b" else null
				val parent = "avatar_$dialogId"

				item.imageView?.setImageMedia(videoLocation, null, imagesLocations[imageLocationPosition], null, thumbsLocations[imageLocationPosition], filter, null, imagesLocationsSizes[imageLocationPosition], 1, parent)
			}

			if (imagesUploadProgress[imageLocationPosition] != null) {
				needProgress = true
			}

			if (needProgress) {
				item.imageView?.radialProgress = radialProgresses[imageLocationPosition]

				if (item.imageView?.radialProgress == null) {
					item.imageView?.radialProgress = RadialProgress2(item.imageView).also {
						it.overrideAlpha = 0.0f
						it.setIcon(MediaActionDrawable.ICON_EMPTY, false, false)
						it.setColors(0x42000000, 0x42000000, Color.WHITE, Color.WHITE)
						radialProgresses.append(imageLocationPosition, it)
					}
				}

				if (invalidateWithParent) {
					invalidate()
				}
				else {
					postInvalidateOnAnimation()
				}
			}

			item.imageView?.imageReceiver?.setDelegate(object : ImageReceiverDelegate {
				override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
					// unused
				}

				override fun onAnimationReady(imageReceiver: ImageReceiver) {
					callback?.onVideoSet()
				}
			})

			item.imageView?.imageReceiver?.setCrossfadeAlpha(2.toByte())
			item.imageView?.setRoundRadius(roundTopRadius, roundTopRadius, roundBottomRadius, roundBottomRadius)

			return item
		}

		override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
			val item = `object` as Item

			if (item.textureViewStubView != null) {
				container.removeView(item.textureViewStubView)
			}

			if (item.isActiveVideo) {
				return
			}

			val imageView: BackupImageView? = item.imageView

			if (imageView?.imageReceiver?.hasStaticThumb() == true) {
				val drawable = imageView.imageReceiver.drawable

				if (drawable is AnimatedFileDrawable) {
					drawable.removeSecondParentView(imageView)
				}
			}

			imageView?.setRoundRadius(0)

			container.removeView(imageView)

			imageView?.imageReceiver?.cancelLoadImage()
		}

		override fun getPageTitle(position: Int): CharSequence {
			return (getRealPosition(position) + 1).toString() + "/" + (count - extraCount * 2)
		}

		override fun notifyDataSetChanged() {
			imageViews.forEach {
				it?.imageReceiver?.cancelLoadImage()
			}

			objects.clear()
			imageViews.clear()

			var size = imagesLocations.size

			if (hasActiveVideo) {
				size++
			}

			var a = 0
			val n = size + extraCount * 2

			while (a < n) {
				objects.add(Item())
				imageViews.add(null)
				a++
			}

			super.notifyDataSetChanged()
		}

		override val extraCount: Int
			get() {
				var count = imagesLocations.size

				if (hasActiveVideo) {
					count++
				}
				return if (count >= 2) {
					offscreenPageLimit
				}
				else {
					0
				}
			}
	}

	inner class AvatarImageView(context: Context, private val position: Int, private val placeholderPaint: Paint) : BackupImageView(context) {
		private val radialProgressSize = AndroidUtilities.dp(64f)
		var isVideo = false
		var radialProgress: RadialProgress2? = null
		private var radialProgressHideAnimator: ValueAnimator? = null
		private var radialProgressHideAnimatorStartValue = 0f
		private var firstDrawTime: Long = -1

		init {
			setLayerNum(imagesLayerNum)
		}

		override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
			super.onSizeChanged(w, h, oldw, oldh)

			if (radialProgress != null) {
				val paddingTop = (if (parentActionBar.occupyStatusBar) AndroidUtilities.statusBarHeight else 0) + ActionBar.getCurrentActionBarHeight()
				val paddingBottom = AndroidUtilities.dp2(80f)
				radialProgress?.setProgressRect((w - radialProgressSize) / 2, paddingTop + (h - paddingTop - paddingBottom - radialProgressSize) / 2, (w + radialProgressSize) / 2, paddingTop + (h - paddingTop - paddingBottom + radialProgressSize) / 2)
			}
		}

		override fun onDraw(canvas: Canvas) {
			if (pinchToZoomHelper != null && pinchToZoomHelper?.isInOverlayMode == true) {
				return
			}

			if (radialProgress != null) {
				var realPosition = getRealPosition(position)

				if (hasActiveVideo) {
					realPosition--
				}

				val drawable = imageReceiver.drawable

				val hideProgress = if (realPosition < imagesUploadProgress.size && imagesUploadProgress[realPosition] != null) {
					imagesUploadProgress[realPosition]!! >= 1f
				}
				else {
					drawable != null && (!isVideo || drawable is AnimatedFileDrawable && drawable.durationMs > 0)
				}

				if (hideProgress) {
					if (radialProgressHideAnimator == null) {
						var startDelay: Long = 0

						if ((radialProgress?.progress ?: 0f) < 1f) {
							radialProgress?.setProgress(1f, true)
							startDelay = 100
						}

						radialProgressHideAnimatorStartValue = radialProgress?.overrideAlpha ?: 1.0f

						radialProgressHideAnimator = ValueAnimator.ofFloat(0f, 1f)
						radialProgressHideAnimator?.startDelay = startDelay
						radialProgressHideAnimator?.duration = (radialProgressHideAnimatorStartValue * 250f).toLong()
						radialProgressHideAnimator?.interpolator = CubicBezierInterpolator.DEFAULT

						radialProgressHideAnimator?.addUpdateListener {
							radialProgress?.overrideAlpha = AndroidUtilities.lerp(radialProgressHideAnimatorStartValue, 0f, it.animatedFraction)
						}

						val finalRealPosition = realPosition

						radialProgressHideAnimator?.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								radialProgress = null
								radialProgresses.delete(finalRealPosition)
							}
						})

						radialProgressHideAnimator?.start()
					}
				}
				else {
					if (firstDrawTime < 0) {
						firstDrawTime = System.currentTimeMillis()
					}
					else {
						val elapsedTime = System.currentTimeMillis() - firstDrawTime
						val startDelay = if (isVideo) 250 else 750.toLong()
						val duration: Long = 250

						if (elapsedTime <= startDelay + duration) {
							if (elapsedTime > startDelay) {
								radialProgress?.overrideAlpha = CubicBezierInterpolator.DEFAULT.getInterpolation((elapsedTime - startDelay) / duration.toFloat())
							}
						}
					}

					if (invalidateWithParent) {
						invalidate()
					}
					else {
						postInvalidateOnAnimation()
					}

					invalidate()
				}

				if (roundTopRadius == 0 && roundBottomRadius == 0) {
					canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), placeholderPaint)
				}
				else if (roundTopRadius == roundBottomRadius) {
					rect[0f, 0f, width.toFloat()] = height.toFloat()
					canvas.drawRoundRect(rect, roundTopRadius.toFloat(), roundTopRadius.toFloat(), placeholderPaint)
				}
				else {
					path.reset()
					rect[0f, 0f, width.toFloat()] = height.toFloat()

					for (i in 0..3) {
						radii[i] = roundTopRadius.toFloat()
						radii[4 + i] = roundBottomRadius.toFloat()
					}

					path.addRoundRect(rect, radii, Path.Direction.CW)
					canvas.drawPath(path, placeholderPaint)
				}
			}

			super.onDraw(canvas)

			radialProgress?.run {
				if (overrideAlpha > 0f) {
					draw(canvas)
				}
			}
		}

		override fun invalidate() {
			super.invalidate()

			if (invalidateWithParent) {
				this@ProfileGalleryView.invalidate()
			}
		}
	}

	private class TextureStubView(context: Context) : View(context)
}
