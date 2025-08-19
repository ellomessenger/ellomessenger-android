/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui.Components.sharedmedia

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.transition.TransitionValues
import android.transition.Visibility
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import androidx.core.util.isEmpty
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import androidx.recyclerview.widget.RecyclerView.Recycler
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ChatObject
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserObject
import org.telegram.messenger.Utilities
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLChatChannelParticipant
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.ChannelParticipant
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.ChatParticipant
import org.telegram.tgnet.TLRPC.EncryptedChat
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.TLChannelParticipantAdmin
import org.telegram.tgnet.TLRPC.TLChannelParticipantCreator
import org.telegram.tgnet.TLRPC.TLChatParticipantAdmin
import org.telegram.tgnet.TLRPC.TLChatParticipantCreator
import org.telegram.tgnet.TLRPC.TLDocumentAttributeAudio
import org.telegram.tgnet.TLRPC.TLDocumentAttributeImageSize
import org.telegram.tgnet.TLRPC.TLDocumentAttributeVideo
import org.telegram.tgnet.TLRPC.TLInputMessagesFilterDocument
import org.telegram.tgnet.TLRPC.TLInputMessagesFilterMusic
import org.telegram.tgnet.TLRPC.TLInputMessagesFilterPhotoVideo
import org.telegram.tgnet.TLRPC.TLInputMessagesFilterPhotos
import org.telegram.tgnet.TLRPC.TLInputMessagesFilterRoundVoice
import org.telegram.tgnet.TLRPC.TLInputMessagesFilterUrl
import org.telegram.tgnet.TLRPC.TLInputMessagesFilterVideo
import org.telegram.tgnet.TLRPC.TLInputUserEmpty
import org.telegram.tgnet.TLRPC.TLMessagesGetCommonChats
import org.telegram.tgnet.TLRPC.TLMessagesGetSearchResultsPositions
import org.telegram.tgnet.TLRPC.TLMessagesSearch
import org.telegram.tgnet.TLRPC.TLMessagesSearchResultsPositions
import org.telegram.tgnet.TLRPC.TLWebPageEmpty
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.TLRPC.WebPage
import org.telegram.tgnet.cachedPage
import org.telegram.tgnet.description
import org.telegram.tgnet.document
import org.telegram.tgnet.embedHeight
import org.telegram.tgnet.embedUrl
import org.telegram.tgnet.embedWidth
import org.telegram.tgnet.migratedFromChatId
import org.telegram.tgnet.migratedFromMaxId
import org.telegram.tgnet.migratedTo
import org.telegram.tgnet.noforwards
import org.telegram.tgnet.participants
import org.telegram.tgnet.siteName
import org.telegram.tgnet.webpage
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.SearchAdapterHelper
import org.telegram.ui.ArticleViewer
import org.telegram.ui.CalendarActivity
import org.telegram.ui.Cells.ChatActionCell
import org.telegram.ui.Cells.ContextLinkCell
import org.telegram.ui.Cells.DividerCell
import org.telegram.ui.Cells.GraySectionCell
import org.telegram.ui.Cells.ManageChatUserCell
import org.telegram.ui.Cells.ProfileSearchCell
import org.telegram.ui.Cells.SharedAudioCell
import org.telegram.ui.Cells.SharedDocumentCell
import org.telegram.ui.Cells.SharedLinkCell
import org.telegram.ui.Cells.SharedLinkCell.SharedLinkCellDelegate
import org.telegram.ui.Cells.SharedPhotoVideoCell
import org.telegram.ui.Cells.SharedPhotoVideoCell2
import org.telegram.ui.Cells.SharedPhotoVideoCell2.SharedResources
import org.telegram.ui.Cells.UserCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.BlurredLinearLayout
import org.telegram.ui.Components.BlurredRecyclerView
import org.telegram.ui.Components.ClippingImageView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EmbedBottomSheet
import org.telegram.ui.Components.ExtendedGridLayoutManager
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.FragmentContextView
import org.telegram.ui.Components.HideViewAfterAnimation
import org.telegram.ui.Components.HintView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.NumberTextView
import org.telegram.ui.Components.RecyclerAnimationScrollHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.FastScrollAdapter
import org.telegram.ui.Components.RecyclerListView.SectionsAdapter
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.Components.ScrollSlidingTextTabStrip
import org.telegram.ui.Components.Size
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.StickerEmptyView
import org.telegram.ui.Components.UndoView
import org.telegram.ui.DialogsActivity
import org.telegram.ui.PhotoViewer
import org.telegram.ui.PhotoViewer.EmptyPhotoViewerProvider
import org.telegram.ui.PhotoViewer.PhotoViewerProvider
import org.telegram.ui.PhotoViewer.PlaceProviderObject
import org.telegram.ui.ProfileActivity
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@SuppressLint("NotifyDataSetChanged", "ViewConstructor")
open class SharedMediaLayout(context: Context, private val dialogId: Long, private val sharedMediaPreloader: SharedMediaPreloader?, commonGroupsCount: Int, sortedUsers: List<Int>?, private var info: ChatFull?, membersFirst: Boolean, private val profileActivity: BaseFragment, private val delegate: Delegate, private val viewType: Int) : FrameLayout(context), NotificationCenterDelegate {
	var photoVideoOptionsItem: ImageView
	var scrollingByUser = false
	private var isInPinchToZoomTouchMode = false
	private var maybePinchToZoomTouchMode = false
	private var maybePinchToZoomTouchMode2 = false

	var isPinnedToTop = false
		set(value) {
			if (field != value) {
				field = value

				for (mediaPage in mediaPages) {
					updateFastScrollVisibility(mediaPage, true)
				}
			}
		}

	var pinchStartDistance = 0f
	var pinchScale = 0f
	private var pinchScaleUp = false
	var pinchCenterPosition = 0
	var pinchCenterOffset = 0
	var pinchCenterX = 0
	var pinchCenterY = 0
	var rect = Rect()
	private var optionsWindow: ActionBarPopupWindow? = null
	private val globalGradientView = FlickerLoadingView(context)
	var animationIndex = 0
	private var jumpToRunnable: Runnable? = null
	var topPadding = 0
	var lastMeasuredTopPadding = 0
	var messageAlphaEnter = SparseArray<Float>()
	private var pointerId1 = 0
	private var pointerId2 = 0
	private val actionBar: ActionBar?

	private val photoVideoAdapter = object : SharedPhotoVideoAdapter(context) {
		override fun notifyDataSetChanged() {
			super.notifyDataSetChanged()

			val mediaPage = getMediaPage(0)

			if (mediaPage?.animationSupportingListView?.isVisible == true) {
				animationSupportingPhotoVideoAdapter.notifyDataSetChanged()
			}
		}
	}

	private val animationSupportingPhotoVideoAdapter = SharedPhotoVideoAdapter(context)
	private val linksAdapter: SharedLinksAdapter
	private val documentsAdapter: SharedDocumentsAdapter
	private val voiceAdapter: SharedDocumentsAdapter
	private val audioAdapter: SharedDocumentsAdapter
	private val gifAdapter: GifAdapter
	private val commonGroupsAdapter: CommonGroupsAdapter
	private val chatUsersAdapter: ChatUsersAdapter
	private val documentsSearchAdapter = MediaSearchAdapter(context, 1)
	private val audioSearchAdapter = MediaSearchAdapter(context, 4)
	private val linksSearchAdapter = MediaSearchAdapter(context, 3)
	private val groupUsersSearchAdapter = GroupUsersSearchAdapter(context)
	private lateinit var mediaPages: Array<MediaPage>
	private val deleteItem: ActionBarMenuItem
	var searchItem: ActionBarMenuItem? = null
	private var forwardItem: ActionBarMenuItem? = null
	private var gotoItem: ActionBarMenuItem? = null
	private var searchItemState: Int
	private val pinnedHeaderShadowDrawable: Drawable
	private var ignoreSearchCollapse = false
	private val selectedMessagesCountTextView: NumberTextView
	private val actionModeLayout: LinearLayout
	private val closeButton: ImageView
	private var backDrawable: BackDrawable? = null
	private val cellCache = mutableListOf<SharedPhotoVideoCell>()
	private val cache = mutableListOf<SharedPhotoVideoCell>()
	private val audioCellCache = mutableListOf<SharedAudioCell>()
	private val audioCache = mutableListOf<SharedAudioCell>()

	private val scrollSlidingTextTabStrip by lazy {
		createScrollingTextTabStrip(context)
	}

	private val shadowLine: View
	private val floatingDateView: ChatActionCell
	private var floatingDateAnimation: AnimatorSet? = null
	private val actionModeViews = mutableListOf<View>()
	private var additionalFloatingTranslation = 0f
	private var fragmentContextView: FragmentContextView? = null
	private val maximumVelocity: Int
	private val backgroundPaint = Paint()
	private var searchWas: Boolean
	private val hideFloatingDateRunnable = Runnable { hideFloatingDateView(true) }
	private var searching: Boolean
	private val hasMedia: IntArray
	private var initialTab = 0
	private val selectedFiles = arrayOf(SparseArray<MessageObject>(), SparseArray<MessageObject>())
	private var cantDeleteMessagesCount: Int
	private var scrolling = false
	private var mergeDialogId = 0L
	private var tabsAnimation: AnimatorSet? = null
	private var tabsAnimationInProgress = false
	private var animatingForward = false
	private var backAnimation = false
	private var mediaColumnsCount = 3
	private var photoVideoChangeColumnsProgress = 0f
	private var photoVideoChangeColumnsAnimation = false
	private val animationSupportingSortedCells = mutableListOf<SharedPhotoVideoCell2>()
	private var animateToColumnsCount = 0
	private lateinit var sharedMediaData: Array<SharedMediaData>

	private val provider: PhotoViewerProvider = object : EmptyPhotoViewerProvider() {
		override fun getPlaceForPhoto(messageObject: MessageObject?, fileLocation: FileLocation?, index: Int, needPreview: Boolean): PlaceProviderObject? {
			if (messageObject == null || mediaPages[0].selectedType != 0 && mediaPages[0].selectedType != 1 && mediaPages[0].selectedType != 3 && mediaPages[0].selectedType != 5) {
				return null
			}

			val listView = mediaPages[0].listView
			var firstVisiblePosition = -1
			var lastVisiblePosition = -1
			var a = 0
			val count = listView!!.childCount

			while (a < count) {
				val view = listView.getChildAt(a)
				var visibleHeight = mediaPages[0].listView!!.measuredHeight
				val parent = parent as? View

				if (parent != null) {
					if (y + measuredHeight > parent.measuredHeight) {
						visibleHeight -= bottom - parent.measuredHeight
					}
				}

				if (view.top >= visibleHeight) {
					a++
					continue
				}

				val adapterPosition = listView.getChildAdapterPosition(view)

				if (adapterPosition < firstVisiblePosition || firstVisiblePosition == -1) {
					firstVisiblePosition = adapterPosition
				}

				if (adapterPosition > lastVisiblePosition || lastVisiblePosition == -1) {
					lastVisiblePosition = adapterPosition
				}

				val coordinates = IntArray(2)
				var imageReceiver: ImageReceiver? = null

				if (view is SharedPhotoVideoCell2) {
					val message = view.messageObject

					if (message == null) {
						a++
						continue
					}

					if (message.id == messageObject.id) {
						imageReceiver = view.imageReceiver
						view.getLocationInWindow(coordinates)
						coordinates[0] += view.imageReceiver.imageX.roundToInt()
						coordinates[1] += view.imageReceiver.imageY.roundToInt()
					}
				}
				else if (view is SharedDocumentCell) {
					val message = view.message

					if (message.id == messageObject.id) {
						val imageView = view.imageView
						imageReceiver = imageView.imageReceiver
						imageView.getLocationInWindow(coordinates)
					}
				}
				else if (view is ContextLinkCell) {
					val message = view.parentObject as? MessageObject

					if (message != null && message.id == messageObject.id) {
						imageReceiver = view.photoImage
						view.getLocationInWindow(coordinates)
					}
				}
				else if (view is SharedLinkCell) {
					val message = view.message

					if (message != null && message.id == messageObject.id) {
						imageReceiver = view.linkImageView
						view.getLocationInWindow(coordinates)
					}
				}

				if (imageReceiver != null) {
					val placeProviderObject = PlaceProviderObject()
					placeProviderObject.viewX = coordinates[0]
					placeProviderObject.viewY = coordinates[1]
					placeProviderObject.parentView = listView
					placeProviderObject.animatingImageView = mediaPages[0].animatingImageView

					mediaPages[0].listView!!.getLocationInWindow(coordinates)

					placeProviderObject.animatingImageViewYOffset = -coordinates[1]
					placeProviderObject.imageReceiver = imageReceiver
					placeProviderObject.allowTakeAnimation = false
					placeProviderObject.radius = placeProviderObject.imageReceiver.getRoundRadius()
					placeProviderObject.thumb = placeProviderObject.imageReceiver.bitmapSafe
					placeProviderObject.parentView.getLocationInWindow(coordinates)
					placeProviderObject.clipTopAddition = 0
					placeProviderObject.starOffset = sharedMediaData[0].startOffset

					if (fragmentContextView?.isVisible == true) {
						placeProviderObject.clipTopAddition += AndroidUtilities.dp(36f)
					}

					if (PhotoViewer.isShowingImage(messageObject)) {
						val pinnedHeader = listView.pinnedHeader

						if (pinnedHeader != null) {
							var top = 0

							if (fragmentContextView?.isVisible == true) {
								top += fragmentContextView!!.height - AndroidUtilities.dp(2.5f)
							}

							if (view is SharedDocumentCell) {
								top += AndroidUtilities.dp(8f)
							}

							val topOffset = top - placeProviderObject.viewY

							if (topOffset > view.height) {
								listView.scrollBy(0, -(topOffset + pinnedHeader.height))
							}
							else {
								var bottomOffset = placeProviderObject.viewY - listView.height

								if (view is SharedDocumentCell) {
									bottomOffset -= AndroidUtilities.dp(8f)
								}

								if (bottomOffset >= 0) {
									listView.scrollBy(0, bottomOffset + view.height)
								}
							}
						}
					}

					return placeProviderObject
				}

				a++
			}

			if (mediaPages[0].selectedType == 0 && firstVisiblePosition >= 0 && lastVisiblePosition >= 0) {
				val position = photoVideoAdapter.getPositionForIndex(index)

				if (position <= firstVisiblePosition) {
					mediaPages[0].layoutManager!!.scrollToPositionWithOffset(position, 0)
					delegate.scrollToSharedMedia()
				}
				else if (lastVisiblePosition in 0..position) {
					mediaPages[0].layoutManager!!.scrollToPositionWithOffset(position, 0, true)
					delegate.scrollToSharedMedia()
				}
			}

			return null
		}
	}

	private var startedTrackingPointerId = 0
	private var startedTracking = false
	private var maybeStartTracking = false
	private var startedTrackingX = 0
	private var startedTrackingY = 0
	private var velocityTracker: VelocityTracker? = null
	private var isActionModeShowed = false

	var sharedLinkCellDelegate: SharedLinkCellDelegate = object : SharedLinkCellDelegate {
		override fun needOpenWebView(webPage: WebPage, messageObject: MessageObject) {
			openWebView(webPage, messageObject)
		}

		override fun canPerformActions(): Boolean {
			return !isActionModeShowed
		}

		override fun onLinkPress(urlFinal: String, longPress: Boolean) {
			if (longPress) {
				val builder = BottomSheet.Builder(profileActivity.parentActivity)
				builder.setTitle(urlFinal)

				builder.setItems(arrayOf<CharSequence>(context.getString(R.string.Open), context.getString(R.string.Copy))) { _, which ->
					if (which == 0) {
						openUrl(urlFinal)
					}
					else if (which == 1) {
						var url = urlFinal

						if (url.startsWith("mailto:")) {
							url = url.substring(7)
						}
						else if (url.startsWith("tel:")) {
							url = url.substring(4)
						}

						AndroidUtilities.addToClipboard(url)
					}
				}

				profileActivity.showDialog(builder.create())
			}
			else {
				openUrl(urlFinal)
			}
		}
	}

	private var fwdRestrictedHint: HintView? = null
	private var changeTypeAnimation = false
	private var actionModeAnimation: AnimatorSet? = null

	init {
		globalGradientView.setIsSingleCell(true)

		val mediaCount = sharedMediaPreloader?.lastMediaCount ?: intArrayOf(-1, -1, -1, -1, -1, -1, -1, -1)

		hasMedia = intArrayOf(mediaCount[0], mediaCount[1], mediaCount[2], mediaCount[3], mediaCount[4], mediaCount[5], commonGroupsCount)

		if (membersFirst) {
			initialTab = 7
		}
		else {
			for (a in hasMedia.indices) {
				if (hasMedia[a] == -1 || hasMedia[a] > 0) {
					initialTab = a
					break
				}
			}
		}

		if (info != null) {
			mergeDialogId = -info!!.migratedFromChatId
		}

		sharedMediaData = (0..<6).map {
			val sharedMediaData = SharedMediaData()
			sharedMediaData.maxId[0] = if (DialogObject.isEncryptedDialog(dialogId)) Int.MIN_VALUE else Int.MAX_VALUE

			if (mergeDialogId != 0L && info != null) {
				sharedMediaData.maxId[1] = info!!.migratedFromMaxId
				sharedMediaData.endReached[1] = false
			}

			sharedMediaData
		}.toTypedArray()

		sharedMediaData.forEachIndexed { index, _ ->
			fillMediaData(index)
		}

		actionBar = profileActivity.actionBar

		mediaColumnsCount = SharedConfig.mediaColumnsCount

		profileActivity.notificationCenter.let {
			it.addObserver(this, NotificationCenter.mediaDidLoad)
			it.addObserver(this, NotificationCenter.messagesDeleted)
			it.addObserver(this, NotificationCenter.didReceiveNewMessages)
			it.addObserver(this, NotificationCenter.messageReceivedByServer)
			it.addObserver(this, NotificationCenter.messagePlayingDidReset)
			it.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
			it.addObserver(this, NotificationCenter.messagePlayingDidStart)
		}

		for (a in 0..9) {
			if (initialTab == MediaDataController.MEDIA_MUSIC) {
				val cell: SharedAudioCell = object : SharedAudioCell(context) {
					public override fun needPlayMessage(messageObject: MessageObject): Boolean {
						if (messageObject.isVoice || messageObject.isRoundVideo) {
							val result = MediaController.getInstance().playMessage(messageObject)
							MediaController.getInstance().setVoiceMessagesPlaylist(if (result) sharedMediaData[MediaDataController.MEDIA_MUSIC].messages else null, false)
							return result
						}
						else if (messageObject.isMusic) {
							return MediaController.getInstance().setPlaylist(sharedMediaData[MediaDataController.MEDIA_MUSIC].messages, messageObject, mergeDialogId)
						}

						return false
					}
				}

				cell.initStreamingIcons()

				audioCellCache.add(cell)
			}
		}

		val configuration = ViewConfiguration.get(context)

		maximumVelocity = configuration.scaledMaximumFlingVelocity
		searching = false
		searchWas = false

		pinnedHeaderShadowDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.photos_header_shadow, null)!!
		pinnedHeaderShadowDrawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.light_background, null), PorterDuff.Mode.MULTIPLY)

		initialTab = scrollSlidingTextTabStrip.currentTabId

		for (a in 1 downTo 0) {
			selectedFiles[a].clear()
		}

		cantDeleteMessagesCount = 0

		actionModeViews.clear()

		val menu = actionBar!!.createMenu()

		menu.addOnLayoutChangeListener(OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
			val searchItem = searchItem ?: return@OnLayoutChangeListener
			val parent = searchItem.parent as? View ?: return@OnLayoutChangeListener
			searchItem.translationX = (parent.measuredWidth - searchItem.right).toFloat()
		})

		searchItem = menu.addItem(0, R.drawable.ic_search_menu).setIsSearchField(true).setActionBarMenuItemSearchListener(object : ActionBarMenuItemSearchListener() {
			override fun onSearchExpand() {
				searching = true
				onSearchStateChanged(true)
			}

			override fun onSearchCollapse() {
				searching = false
				searchWas = false

				documentsSearchAdapter.search(null, true)
				linksSearchAdapter.search(null, true)
				audioSearchAdapter.search(null, true)
				groupUsersSearchAdapter.search(null, true)

				onSearchStateChanged(false)

				if (ignoreSearchCollapse) {
					ignoreSearchCollapse = false
					return
				}

				switchToCurrentSelectedMode(false)
			}

			override fun onTextChanged(editText: EditText) {
				val text = editText.text?.toString()
				searchWas = !text.isNullOrEmpty()

				switchToCurrentSelectedMode(false)

				when (mediaPages[0].selectedType) {
					1 -> documentsSearchAdapter.search(text, true)
					3 -> linksSearchAdapter.search(text, true)
					4 -> audioSearchAdapter.search(text, true)
					7 -> groupUsersSearchAdapter.search(text, true)
				}
			}

			override fun onLayout(l: Int, t: Int, r: Int, b: Int) {
				val searchItem = searchItem ?: return
				val parent = searchItem.parent as? View ?: return
				searchItem.translationX = (parent.measuredWidth - searchItem.right).toFloat()
			}
		})

		searchItem?.translationY = AndroidUtilities.dp(10f).toFloat()
		searchItem?.setSearchFieldHint(context.getString(R.string.Search))
		searchItem?.contentDescription = context.getString(R.string.Search)
		searchItem?.visibility = INVISIBLE

		photoVideoOptionsItem = ImageView(context)
		photoVideoOptionsItem.contentDescription = context.getString(R.string.AccDescrMoreOptions)
		photoVideoOptionsItem.translationY = AndroidUtilities.dp(10f).toFloat()
		photoVideoOptionsItem.visibility = INVISIBLE

		val calendarDrawable = ContextCompat.getDrawable(context, R.drawable.overflow_menu)!!.mutate()
		calendarDrawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.action_bar_item, null), PorterDuff.Mode.SRC_IN)

		photoVideoOptionsItem.setImageDrawable(calendarDrawable)
		photoVideoOptionsItem.scaleType = ImageView.ScaleType.CENTER_INSIDE

		actionBar.addView(photoVideoOptionsItem, createFrame(48, 56, Gravity.RIGHT or Gravity.BOTTOM))

		photoVideoOptionsItem.setOnClickListener {
			val dividerView = DividerCell(context)

			val popupLayout: ActionBarPopupWindowLayout = object : ActionBarPopupWindowLayout(context) {
				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					if (dividerView.parent != null) {
						dividerView.visibility = GONE

						super.onMeasure(widthMeasureSpec, heightMeasureSpec)

						dividerView.layoutParams.width = measuredWidth - AndroidUtilities.dp(16f)
						dividerView.visibility = VISIBLE

						super.onMeasure(widthMeasureSpec, heightMeasureSpec)
					}
					else {
						super.onMeasure(widthMeasureSpec, heightMeasureSpec)
					}
				}
			}

			val mediaZoomInItem = ActionBarMenuSubItem(context, top = true, bottom = false)
			val mediaZoomOutItem = ActionBarMenuSubItem(context, top = false, bottom = false)
			mediaZoomInItem.setTextAndIcon(context.getString(R.string.MediaZoomIn), R.drawable.msg_zoomin)

			mediaZoomInItem.setOnClickListener {
				if (photoVideoChangeColumnsAnimation) {
					return@setOnClickListener
				}

				val newColumnsCount = getNextMediaColumnsCount(mediaColumnsCount, true)

				if (newColumnsCount == getNextMediaColumnsCount(newColumnsCount, true)) {
					mediaZoomInItem.isEnabled = false
					mediaZoomInItem.animate().alpha(0.5f).start()
				}

				if (mediaColumnsCount != newColumnsCount) {
					if (!mediaZoomOutItem.isEnabled) {
						mediaZoomOutItem.isEnabled = true
						mediaZoomOutItem.animate().alpha(1f).start()
					}

					SharedConfig.setMediaColumnsCount(newColumnsCount)
					animateToMediaColumnsCount(newColumnsCount)
				}
			}

			popupLayout.addView(mediaZoomInItem)

			mediaZoomOutItem.setTextAndIcon(context.getString(R.string.MediaZoomOut), R.drawable.msg_zoomout)

			mediaZoomOutItem.setOnClickListener {
				if (photoVideoChangeColumnsAnimation) {
					return@setOnClickListener
				}

				val newColumnsCount = getNextMediaColumnsCount(mediaColumnsCount, false)

				if (newColumnsCount == getNextMediaColumnsCount(newColumnsCount, false)) {
					mediaZoomOutItem.isEnabled = false
					mediaZoomOutItem.animate().alpha(0.5f).start()
				}

				if (mediaColumnsCount != newColumnsCount) {
					if (!mediaZoomInItem.isEnabled) {
						mediaZoomInItem.isEnabled = true
						mediaZoomInItem.animate().alpha(1f).start()
					}

					SharedConfig.setMediaColumnsCount(newColumnsCount)
					animateToMediaColumnsCount(newColumnsCount)
				}
			}

			if (mediaColumnsCount == 2) {
				mediaZoomInItem.isEnabled = false
				mediaZoomInItem.alpha = 0.5f
			}
			else if (mediaColumnsCount == 9) {
				mediaZoomOutItem.isEnabled = false
				mediaZoomOutItem.alpha = 0.5f
			}

			popupLayout.addView(mediaZoomOutItem)

			val hasDifferentTypes = sharedMediaData[0].hasPhotos && sharedMediaData[0].hasVideos || !sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1] || !sharedMediaData[0].startReached

			if (!DialogObject.isEncryptedDialog(dialogId)) {
				val calendarItem = ActionBarMenuSubItem(context, top = false, bottom = false)
				calendarItem.setTextAndIcon(context.getString(R.string.Calendar), R.drawable.msg_calendar2)
				popupLayout.addView(calendarItem)

				calendarItem.setOnClickListener {
					showMediaCalendar(false)
					optionsWindow?.dismiss()
				}

				if (hasDifferentTypes) {
					popupLayout.addView(dividerView)

					val showPhotosItem = ActionBarMenuSubItem(context, needCheck = true, top = false, bottom = false)
					val showVideosItem = ActionBarMenuSubItem(context, needCheck = true, top = false, bottom = true)

					showPhotosItem.setTextAndIcon(context.getString(R.string.MediaShowPhotos), 0)
					showPhotosItem.setChecked(sharedMediaData[0].filterType == FILTER_PHOTOS_AND_VIDEOS || sharedMediaData[0].filterType == FILTER_PHOTOS_ONLY)

					showPhotosItem.setOnClickListener {
						if (changeTypeAnimation) {
							return@setOnClickListener
						}

						if (showVideosItem.checkView?.isChecked == false && showPhotosItem.checkView?.isChecked == true) {
							return@setOnClickListener
						}

						showPhotosItem.setChecked(showPhotosItem.checkView?.isChecked == false)

						if (showPhotosItem.checkView?.isChecked == true && showVideosItem.checkView?.isChecked == true) {
							sharedMediaData[0].filterType = FILTER_PHOTOS_AND_VIDEOS
						}
						else {
							sharedMediaData[0].filterType = FILTER_VIDEOS_ONLY
						}

						changeMediaFilterType()
					}

					popupLayout.addView(showPhotosItem)

					showVideosItem.setTextAndIcon(context.getString(R.string.MediaShowVideos), 0)
					showVideosItem.setChecked(sharedMediaData[0].filterType == FILTER_PHOTOS_AND_VIDEOS || sharedMediaData[0].filterType == FILTER_VIDEOS_ONLY)

					showVideosItem.setOnClickListener {
						if (changeTypeAnimation) {
							return@setOnClickListener
						}

						if (showPhotosItem.checkView?.isChecked == false && showVideosItem.checkView?.isChecked == true) {
							return@setOnClickListener
						}

						showVideosItem.setChecked(showVideosItem.checkView?.isChecked == false)

						if (showPhotosItem.checkView?.isChecked == true && showVideosItem.checkView?.isChecked == true) {
							sharedMediaData[0].filterType = FILTER_PHOTOS_AND_VIDEOS
						}
						else {
							sharedMediaData[0].filterType = FILTER_PHOTOS_ONLY
						}

						changeMediaFilterType()
					}

					popupLayout.addView(showVideosItem)
				}
			}

			optionsWindow = AlertsCreator.showPopupMenu(popupLayout, photoVideoOptionsItem, 0, -AndroidUtilities.dp(56f))
		}

		val editText = searchItem?.searchField
		editText?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		editText?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
		editText?.setCursorColor(ResourcesCompat.getColor(context.resources, R.color.text, null))

		searchItemState = 0

		var sizeNotifierFrameLayout: SizeNotifierFrameLayout? = null

		if (profileActivity.fragmentView is SizeNotifierFrameLayout) {
			sizeNotifierFrameLayout = profileActivity.fragmentView as SizeNotifierFrameLayout?
		}

		actionModeLayout = BlurredLinearLayout(context, sizeNotifierFrameLayout)
		actionModeLayout.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		actionModeLayout.setAlpha(0.0f)
		actionModeLayout.setClickable(true)
		actionModeLayout.setVisibility(INVISIBLE)

		closeButton = ImageView(context)
		closeButton.scaleType = ImageView.ScaleType.CENTER
		closeButton.setImageDrawable(BackDrawable(true).also { backDrawable = it })

		backDrawable?.setColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))

		closeButton.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.action_bar_item, null), 1)
		closeButton.contentDescription = context.getString(R.string.Close)

		actionModeLayout.addView(closeButton, LinearLayout.LayoutParams(AndroidUtilities.dp(54f), ViewGroup.LayoutParams.MATCH_PARENT))

		actionModeViews.add(closeButton)

		closeButton.setOnClickListener {
			closeActionMode()
		}

		selectedMessagesCountTextView = NumberTextView(context)
		selectedMessagesCountTextView.setTextSize(18)
		selectedMessagesCountTextView.setTypeface(Theme.TYPEFACE_BOLD)
		selectedMessagesCountTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))

		actionModeLayout.addView(selectedMessagesCountTextView, createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 18, 0, 0, 0))

		actionModeViews.add(selectedMessagesCountTextView)

		if (!DialogObject.isEncryptedDialog(dialogId)) {
			gotoItem = ActionBarMenuItem(context, null, ResourcesCompat.getColor(context.resources, R.color.action_bar_item, null), ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), false)
			gotoItem?.setIcon(R.drawable.msg_message)
			gotoItem?.contentDescription = context.getString(R.string.AccDescrGoToMessage)
			gotoItem?.isDuplicateParentStateEnabled = false

			actionModeLayout.addView(gotoItem, LinearLayout.LayoutParams(AndroidUtilities.dp(54f), ViewGroup.LayoutParams.MATCH_PARENT))
			actionModeViews.add(gotoItem!!)

			gotoItem?.setOnClickListener {
				onActionBarItemClick(it, GO_TO_CHAT)
			}

			forwardItem = ActionBarMenuItem(context, null, ResourcesCompat.getColor(context.resources, R.color.action_bar_item, null), ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), false)
			forwardItem?.setIcon(R.drawable.msg_forward)
			forwardItem?.contentDescription = context.getString(R.string.Forward)
			forwardItem?.isDuplicateParentStateEnabled = false

			actionModeLayout.addView(forwardItem, LinearLayout.LayoutParams(AndroidUtilities.dp(54f), ViewGroup.LayoutParams.MATCH_PARENT))
			actionModeViews.add(forwardItem!!)

			forwardItem?.setOnClickListener {
				onActionBarItemClick(it, FORWARD)
			}

			updateForwardItem()
		}

		deleteItem = ActionBarMenuItem(context, null, ResourcesCompat.getColor(context.resources, R.color.action_bar_item, null), ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), false)
		deleteItem.setIcon(R.drawable.msg_delete)
		deleteItem.contentDescription = context.getString(R.string.Delete)
		deleteItem.isDuplicateParentStateEnabled = false

		actionModeLayout.addView(deleteItem, LinearLayout.LayoutParams(AndroidUtilities.dp(54f), ViewGroup.LayoutParams.MATCH_PARENT))

		actionModeViews.add(deleteItem)

		deleteItem.setOnClickListener { v ->
			onActionBarItemClick(v, DELETE)
		}

		documentsAdapter = SharedDocumentsAdapter(context, 1)
		voiceAdapter = SharedDocumentsAdapter(context, 2)
		audioAdapter = SharedDocumentsAdapter(context, 4)
		gifAdapter = GifAdapter(context)

		commonGroupsAdapter = CommonGroupsAdapter(context)

		chatUsersAdapter = ChatUsersAdapter(context)
		chatUsersAdapter.sortedUsers = sortedUsers
		chatUsersAdapter.chatInfo = if (membersFirst) info else null

		linksAdapter = SharedLinksAdapter(context)

		setWillNotDraw(false)

		mediaPages = (0..<2).map {
			val mediaPage = object : MediaPage(context) {
				override fun setTranslationX(translationX: Float) {
					super.setTranslationX(translationX)

					if (tabsAnimationInProgress) {
						if (mediaPages[0] === this) {
							val scrollProgress = abs(mediaPages[0].translationX) / mediaPages[0].measuredWidth.toFloat()

							scrollSlidingTextTabStrip.selectTabWithId(mediaPages[1].selectedType, scrollProgress)

							if (canShowSearchItem()) {
								if (searchItemState == 2) {
									searchItem?.alpha = 1.0f - scrollProgress
								}
								else if (searchItemState == 1) {
									searchItem?.alpha = scrollProgress
								}

								var photoVideoOptionsAlpha = 0f

								if (mediaPages[1].selectedType == 0) {
									photoVideoOptionsAlpha = scrollProgress
								}

								if (mediaPages[0].selectedType == 0) {
									photoVideoOptionsAlpha = 1f - scrollProgress
								}

								photoVideoOptionsItem.alpha = photoVideoOptionsAlpha
								photoVideoOptionsItem.visibility = if (photoVideoOptionsAlpha == 0f || !canShowSearchItem()) INVISIBLE else VISIBLE
							}
							else {
								searchItem?.alpha = 0.0f
							}
						}
					}

					invalidateBlur()
				}
			}

			addView(mediaPage, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 48f, 0f, 0f))

			val layoutManager = object : ExtendedGridLayoutManager(context, 100) {
				private val size = Size()

				override fun supportsPredictiveItemAnimations(): Boolean {
					return false
				}

				override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
					super.calculateExtraLayoutSpace(state, extraLayoutSpace)
					if (mediaPage.selectedType == 0) {
						extraLayoutSpace[1] = max(extraLayoutSpace[1], SharedPhotoVideoCell.getItemSize(1) * 2)
					}
					else if (mediaPage.selectedType == 1) {
						extraLayoutSpace[1] = max(extraLayoutSpace[1], AndroidUtilities.dp(56f) * 2)
					}
				}

				override fun getSizeForItem(i: Int): Size {
					val document = if (mediaPage.listView!!.adapter === gifAdapter && sharedMediaData[5].messages.isNotEmpty()) {
						sharedMediaData[5].messages[i].document
					}
					else {
						null
					}

					size.height = 100f
					size.width = size.height

					if (document is TLRPC.TLDocument) {
						val thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)

						if (thumb != null && thumb.w != 0 && thumb.h != 0) {
							size.width = thumb.w.toFloat()
							size.height = thumb.h.toFloat()
						}

						val attributes = document.attributes

						for (b in attributes.indices) {
							val attribute = attributes[b]

							if (attribute is TLDocumentAttributeImageSize || attribute is TLDocumentAttributeVideo) {
								size.width = attribute.w.toFloat()
								size.height = attribute.h.toFloat()
								break
							}
						}
					}

					return size
				}

				override val flowItemCount: Int
					get() {
						return if (mediaPage.listView?.adapter !== gifAdapter) {
							0
						}
						else {
							itemCount
						}
					}

				override fun onInitializeAccessibilityNodeInfoForItem(recycler: Recycler, state: RecyclerView.State, host: View, info: AccessibilityNodeInfoCompat) {
					super.onInitializeAccessibilityNodeInfoForItem(recycler, state, host, info)

					val itemInfo = info.collectionItemInfo

					if (itemInfo != null && itemInfo.isHeading) {
						info.setCollectionItemInfo(CollectionItemInfoCompat.obtain(itemInfo.rowIndex, itemInfo.rowSpan, itemInfo.columnIndex, itemInfo.columnSpan, false))
					}
				}
			}.also { lm ->
				mediaPage.layoutManager = lm
			}

			layoutManager.spanSizeLookup = object : SpanSizeLookup() {
				override fun getSpanSize(position: Int): Int {
					if (mediaPage.listView?.adapter === photoVideoAdapter) {
						return if (photoVideoAdapter.getItemViewType(position) == 2) {
							mediaColumnsCount
						}
						else {
							1
						}
					}

					if (mediaPage.listView!!.adapter !== gifAdapter) {
						return mediaPage.layoutManager!!.spanCount
					}

					return if (mediaPage.listView!!.adapter === gifAdapter && sharedMediaData[5].messages.isEmpty()) {
						mediaPage.layoutManager!!.spanCount
					}
					else {
						mediaPage.layoutManager!!.getSpanSizeForItem(position)
					}
				}
			}

			mediaPage.listView = object : BlurredRecyclerView(context) {
				val excludeDrawViews = HashSet<SharedPhotoVideoCell2>()
				val drawingViews = mutableListOf<SharedPhotoVideoCell2>()
				val drawingViews2 = mutableListOf<SharedPhotoVideoCell2>()
				val drawingViews3 = mutableListOf<SharedPhotoVideoCell2>()

				override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
					super.onLayout(changed, l, t, r, b)

					checkLoadMoreScroll(mediaPage, mediaPage.listView, layoutManager)

					if (mediaPage.selectedType == 0) {
						PhotoViewer.getInstance().checkCurrentImageVisibility()
					}
				}

				override fun dispatchDraw(canvas: Canvas) {
					if (adapter === photoVideoAdapter) {
						var firstVisibleItemPosition = 0
						var firstVisibleItemPosition2 = 0
						val lastVisibleItemPosition: Int
						val lastVisibleItemPosition2: Int
						var rowsOffset = 0
						var columnsOffset = 0
						var minY = measuredHeight.toFloat()

						if (photoVideoChangeColumnsAnimation) {
							var max = -1
							var min = -1

							for (i in 0 until mediaPage.listView!!.childCount) {
								val p = mediaPage.listView!!.getChildAdapterPosition(mediaPage.listView!!.getChildAt(i))

								if (p >= 0 && (p > max || max == -1)) {
									max = p
								}

								if (p >= 0 && (p < min || min == -1)) {
									min = p
								}
							}

							firstVisibleItemPosition = min
							lastVisibleItemPosition = max
							max = -1
							min = -1

							for (i in 0 until mediaPage.animationSupportingListView!!.childCount) {
								val p = mediaPage.animationSupportingListView!!.getChildAdapterPosition(mediaPage.animationSupportingListView!!.getChildAt(i))

								if (p >= 0 && (p > max || max == -1)) {
									max = p
								}

								if (p >= 0 && (p < min || min == -1)) {
									min = p
								}
							}

							firstVisibleItemPosition2 = min
							lastVisibleItemPosition2 = max

							if (firstVisibleItemPosition >= 0 && firstVisibleItemPosition2 >= 0 && pinchCenterPosition >= 0) {
								val rowsCount1 = ceil((photoVideoAdapter.itemCount / mediaColumnsCount.toFloat()).toDouble()).toInt()
								val rowsCount2 = ceil((photoVideoAdapter.itemCount / animateToColumnsCount.toFloat()).toDouble()).toInt()

								rowsOffset = pinchCenterPosition / animateToColumnsCount - firstVisibleItemPosition2 / animateToColumnsCount - (pinchCenterPosition / mediaColumnsCount - firstVisibleItemPosition / mediaColumnsCount)

								if (firstVisibleItemPosition / mediaColumnsCount - rowsOffset < 0 && animateToColumnsCount < mediaColumnsCount || firstVisibleItemPosition2 / animateToColumnsCount + rowsOffset < 0 && animateToColumnsCount > mediaColumnsCount) {
									rowsOffset = 0
								}

								if (lastVisibleItemPosition2 / mediaColumnsCount + rowsOffset >= rowsCount1 && animateToColumnsCount > mediaColumnsCount || lastVisibleItemPosition / animateToColumnsCount - rowsOffset >= rowsCount2 && animateToColumnsCount < mediaColumnsCount) {
									rowsOffset = 0
								}

								val k = pinchCenterPosition % mediaColumnsCount / (mediaColumnsCount - 1).toFloat()

								columnsOffset = ((animateToColumnsCount - mediaColumnsCount) * k).toInt()
							}

							animationSupportingSortedCells.clear()
							excludeDrawViews.clear()
							drawingViews.clear()
							drawingViews2.clear()
							drawingViews3.clear()

							for (i in 0 until mediaPage.animationSupportingListView!!.childCount) {
								val child = mediaPage.animationSupportingListView!!.getChildAt(i)

								if (child.top > measuredHeight || child.bottom < 0) {
									continue
								}

								if (child is SharedPhotoVideoCell2) {
									animationSupportingSortedCells.add(child)
								}
							}

							drawingViews.addAll(animationSupportingSortedCells)

							val fastScroll = fastScroll

							if (fastScroll != null && fastScroll.tag != null) {
								val p1 = photoVideoAdapter.getScrollProgress(mediaPage.listView!!)
								val p2 = animationSupportingPhotoVideoAdapter.getScrollProgress(mediaPage.animationSupportingListView!!)
								val a1 = if (photoVideoAdapter.fastScrollIsVisible(mediaPage.listView!!)) 1f else 0f
								val a2 = if (animationSupportingPhotoVideoAdapter.fastScrollIsVisible(mediaPage.animationSupportingListView!!)) 1f else 0f

								fastScroll.setProgress(p1 * (1f - photoVideoChangeColumnsProgress) + p2 * photoVideoChangeColumnsProgress)
								fastScroll.setVisibilityAlpha(a1 * (1f - photoVideoChangeColumnsProgress) + a2 * photoVideoChangeColumnsProgress)
							}
						}

						for (i in 0 until childCount) {
							val child = getChildAt(i)

							if (child.top > measuredHeight || child.bottom < 0) {
								if (child is SharedPhotoVideoCell2) {
									val cell = getChildAt(i) as SharedPhotoVideoCell2
									cell.setCrossfadeView(null, 0f, 0)
									cell.translationX = 0f
									cell.translationY = 0f
									cell.setImageScale(1f, !photoVideoChangeColumnsAnimation)
								}

								continue
							}

							if (child is SharedPhotoVideoCell2) {
								val cell = getChildAt(i) as SharedPhotoVideoCell2

								if (cell.messageId == mediaPage.highlightMessageId && cell.imageReceiver.hasBitmapImage()) {
									if (!mediaPage.highlightAnimation) {
										mediaPage.highlightProgress = 0f
										mediaPage.highlightAnimation = true
									}

									var p = 1f

									if (mediaPage.highlightProgress < 0.3f) {
										p = mediaPage.highlightProgress / 0.3f
									}
									else if (mediaPage.highlightProgress > 0.7f) {
										p = (1f - mediaPage.highlightProgress) / 0.3f
									}

									cell.setHighlightProgress(p)
								}
								else {
									cell.setHighlightProgress(0f)
								}

								val messageObject = cell.messageObject
								var alpha = 1f

								if (messageObject != null && messageAlphaEnter[messageObject.id, null] != null) {
									alpha = messageAlphaEnter[messageObject.id, 1f]
								}

								cell.setImageAlpha(alpha, !photoVideoChangeColumnsAnimation)

								var inAnimation = false

								if (photoVideoChangeColumnsAnimation) {
									val fromScale = 1f
									val currentColumn = (cell.layoutParams as GridLayoutManager.LayoutParams).viewAdapterPosition % mediaColumnsCount + columnsOffset
									val currentRow = ((cell.layoutParams as GridLayoutManager.LayoutParams).viewAdapterPosition - firstVisibleItemPosition) / mediaColumnsCount + rowsOffset
									val toIndex = currentRow * animateToColumnsCount + currentColumn

									if (currentColumn in 0 until animateToColumnsCount && toIndex >= 0 && toIndex < animationSupportingSortedCells.size) {
										inAnimation = true

										val toScale = (animationSupportingSortedCells[toIndex].measuredWidth - AndroidUtilities.dpf2(2f)) / (cell.measuredWidth - AndroidUtilities.dpf2(2f))
										val scale = fromScale * (1f - photoVideoChangeColumnsProgress) + toScale * photoVideoChangeColumnsProgress
										val fromX = cell.left.toFloat()
										val fromY = cell.top.toFloat()
										val toX = animationSupportingSortedCells[toIndex].left.toFloat()
										val toY = animationSupportingSortedCells[toIndex].top.toFloat()

										cell.pivotX = 0f
										cell.pivotY = 0f
										cell.setImageScale(scale, !photoVideoChangeColumnsAnimation)
										cell.translationX = (toX - fromX) * photoVideoChangeColumnsProgress
										cell.translationY = (toY - fromY) * photoVideoChangeColumnsProgress
										cell.setCrossfadeView(animationSupportingSortedCells[toIndex], photoVideoChangeColumnsProgress, animateToColumnsCount)

										excludeDrawViews.add(animationSupportingSortedCells[toIndex])
										drawingViews3.add(cell)

										canvas.withTranslation(cell.x, cell.y) {
											cell.draw(this)
										}

										if (cell.y < minY) {
											minY = cell.y
										}
									}
								}

								if (!inAnimation) {
									if (photoVideoChangeColumnsAnimation) {
										drawingViews2.add(cell)
									}

									cell.setCrossfadeView(null, 0f, 0)
									cell.translationX = 0f
									cell.translationY = 0f
									cell.setImageScale(1f, !photoVideoChangeColumnsAnimation)
								}
							}
						}

						if (photoVideoChangeColumnsAnimation && drawingViews.isNotEmpty()) {
							val toScale = animateToColumnsCount / mediaColumnsCount.toFloat()
							val scale = toScale * (1f - photoVideoChangeColumnsProgress) + photoVideoChangeColumnsProgress
							val sizeToScale = (measuredWidth / mediaColumnsCount.toFloat() - AndroidUtilities.dpf2(2f)) / (measuredWidth / animateToColumnsCount.toFloat() - AndroidUtilities.dpf2(2f))
							val scaleSize = sizeToScale * (1f - photoVideoChangeColumnsProgress) + photoVideoChangeColumnsProgress
							val fromSize = measuredWidth / mediaColumnsCount.toFloat()
							val toSize = measuredWidth / animateToColumnsCount.toFloat()
							val size1 = ((ceil((measuredWidth / animateToColumnsCount.toFloat()).toDouble()) - AndroidUtilities.dpf2(2f)) * scaleSize + AndroidUtilities.dpf2(2f)).toFloat()

							for (i in drawingViews.indices) {
								val view = drawingViews[i]

								if (excludeDrawViews.contains(view)) {
									continue
								}

								view.setCrossfadeView(null, 0f, 0)

								val fromColumn = (view.layoutParams as GridLayoutManager.LayoutParams).viewAdapterPosition % animateToColumnsCount
								val toColumn = fromColumn - columnsOffset

								var currentRow = ((view.layoutParams as GridLayoutManager.LayoutParams).viewAdapterPosition - firstVisibleItemPosition2) / animateToColumnsCount
								currentRow -= rowsOffset

								canvas.withTranslation(toColumn * fromSize * (1f - photoVideoChangeColumnsProgress) + toSize * fromColumn * photoVideoChangeColumnsProgress, minY + size1 * currentRow) {
									view.setImageScale(scaleSize, !photoVideoChangeColumnsAnimation)

									if (toColumn < mediaColumnsCount) {
										saveLayerAlpha(0f, 0f, view.measuredWidth * scale, view.measuredWidth * scale, (photoVideoChangeColumnsProgress * 255).toInt())
										view.draw(this)
										restore()
									}
									else {
										view.draw(this)
									}

								}
							}
						}

						super.dispatchDraw(canvas)

						if (photoVideoChangeColumnsAnimation) {
							val toScale = mediaColumnsCount / animateToColumnsCount.toFloat()
							val scale = toScale * photoVideoChangeColumnsProgress + (1f - photoVideoChangeColumnsProgress)
							val sizeToScale = (measuredWidth / animateToColumnsCount.toFloat() - AndroidUtilities.dpf2(2f)) / (measuredWidth / mediaColumnsCount.toFloat() - AndroidUtilities.dpf2(2f))
							val scaleSize = sizeToScale * photoVideoChangeColumnsProgress + (1f - photoVideoChangeColumnsProgress)
							val size1 = ((ceil((measuredWidth / mediaColumnsCount.toFloat()).toDouble()) - AndroidUtilities.dpf2(2f)) * scaleSize + AndroidUtilities.dpf2(2f)).toFloat()
							val fromSize = measuredWidth / mediaColumnsCount.toFloat()
							val toSize = measuredWidth / animateToColumnsCount.toFloat()

							for (i in drawingViews2.indices) {
								val view = drawingViews2[i]
								val fromColumn = (view.layoutParams as GridLayoutManager.LayoutParams).viewAdapterPosition % mediaColumnsCount

								var currentRow = ((view.layoutParams as GridLayoutManager.LayoutParams).viewAdapterPosition - firstVisibleItemPosition) / mediaColumnsCount
								currentRow += rowsOffset

								val toColumn = fromColumn + columnsOffset

								canvas.withSave {
									view.setImageScale(scaleSize, !photoVideoChangeColumnsAnimation)

									translate(fromColumn * fromSize * (1f - photoVideoChangeColumnsProgress) + toSize * toColumn * photoVideoChangeColumnsProgress, minY + size1 * currentRow)

									if (toColumn < animateToColumnsCount) {
										saveLayerAlpha(0f, 0f, view.measuredWidth * scale, view.measuredWidth * scale, ((1f - photoVideoChangeColumnsProgress) * 255).toInt())
										view.draw(this)
										restore()
									}
									else {
										view.draw(this)
									}
								}
							}

							if (drawingViews3.isNotEmpty()) {
								canvas.saveLayerAlpha(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), (255 * photoVideoChangeColumnsProgress).toInt())

								for (i in drawingViews3.indices) {
									drawingViews3[i].drawCrossafadeImage(canvas)
								}

								canvas.restore()
							}
						}
					}
					else {
						for (i in 0 until childCount) {
							val child = getChildAt(i)
							val messageId = getMessageId(child)
							var alpha = 1f

							if (messageId != 0 && messageAlphaEnter[messageId, null] != null) {
								alpha = messageAlphaEnter[messageId, 1f]
							}

							if (child is SharedDocumentCell) {
								child.setEnterAnimationAlpha(alpha)
							}
							else if (child is SharedAudioCell) {
								child.setEnterAnimationAlpha(alpha)
							}
						}

						super.dispatchDraw(canvas)
					}

					if (mediaPage.highlightAnimation) {
						mediaPage.highlightProgress += 16f / 1500f

						if (mediaPage.highlightProgress >= 1) {
							mediaPage.highlightProgress = 0f
							mediaPage.highlightAnimation = false
							mediaPage.highlightMessageId = 0
						}

						invalidate()
					}
				}

				override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
					if (adapter === photoVideoAdapter) {
						if (photoVideoChangeColumnsAnimation && child is SharedPhotoVideoCell2) {
							return true
						}
					}

					return super.drawChild(canvas, child, drawingTime)
				}
			}

			mediaPage.listView?.setFastScrollEnabled(RecyclerListView.DATE_TYPE)
			mediaPage.listView?.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING)
			mediaPage.listView?.setPinnedSectionOffsetY(-AndroidUtilities.dp(2f))
			mediaPage.listView?.setPadding(0, AndroidUtilities.dp(2f), 0, 0)
			mediaPage.listView?.itemAnimator = null
			mediaPage.listView?.clipToPadding = false
			mediaPage.listView?.setSectionsType(RecyclerListView.SECTIONS_TYPE_DATE)
			mediaPage.listView?.layoutManager = layoutManager

			mediaPage.addView(mediaPage.listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			mediaPage.animationSupportingListView = BlurredRecyclerView(context)

			mediaPage.animationSupportingListView?.layoutManager = object : GridLayoutManager(context, 3) {
				override fun supportsPredictiveItemAnimations(): Boolean {
					return false
				}

				override fun scrollVerticallyBy(dy: Int, recycler: Recycler, state: RecyclerView.State): Int {
					var dy = dy

					if (photoVideoChangeColumnsAnimation) {
						dy = 0
					}

					return super.scrollVerticallyBy(dy, recycler, state)
				}
			}.also { gridLayoutManager ->
				mediaPage.animationSupportingLayoutManager = gridLayoutManager
			}

			mediaPage.addView(mediaPage.animationSupportingListView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
			mediaPage.animationSupportingListView?.visibility = GONE

			mediaPage.listView?.addItemDecoration(object : RecyclerView.ItemDecoration() {
				override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
					if (mediaPage.listView?.adapter === gifAdapter) {
						val position = parent.getChildAdapterPosition(view)

						outRect.left = 0
						outRect.bottom = 0

						if (!mediaPage.layoutManager!!.isFirstRow(position)) {
							outRect.top = AndroidUtilities.dp(2f)
						}
						else {
							outRect.top = 0
						}

						outRect.right = if (mediaPage.layoutManager!!.isLastInRow(position)) {
							0
						}
						else {
							AndroidUtilities.dp(2f)
						}
					}
					else {
						outRect.left = 0
						outRect.top = 0
						outRect.bottom = 0
						outRect.right = 0
					}
				}
			})

			mediaPage.listView?.setOnItemClickListener(object : RecyclerListView.OnItemClickListener {
				override fun onItemClick(view: View, position: Int) {
					if (mediaPage.selectedType == 7) {
						if (view is UserCell) {
							val participant = if (chatUsersAdapter.sortedUsers!!.isNotEmpty()) {
								chatUsersAdapter.chatInfo?.participants?.participants?.get(chatUsersAdapter.sortedUsers!![position])
							}
							else {
								chatUsersAdapter.chatInfo?.participants?.participants?.get(position)
							}

							onMemberClick(participant, false)
						}
						else if (mediaPage.listView!!.adapter === groupUsersSearchAdapter) {
							val userId = when (val `object` = groupUsersSearchAdapter.getItem(position)) {
								is ChannelParticipant -> MessageObject.getPeerId(`object`.peer).takeIf { it != 0L } ?: `object`.userId
								is ChatParticipant -> `object`.userId
								else -> return
							}

							if (userId == 0L || userId == profileActivity.userConfig.getClientUserId()) {
								return
							}

							val args = Bundle()
							args.putLong("user_id", userId)

							profileActivity.presentFragment(ProfileActivity(args))
						}
					}
					else if (mediaPage.selectedType == 6 && view is ProfileSearchCell) {
						val chat = view.chat!!

						val args = Bundle()
						args.putLong("chat_id", chat.id)

						if (!profileActivity.messagesController.checkCanOpenChat(args, profileActivity)) {
							return
						}

						profileActivity.presentFragment(ChatActivity(args))
					}
					else if (mediaPage.selectedType == 1 && view is SharedDocumentCell) {
						onItemClick(position, view, view.message, 0, mediaPage.selectedType)
					}
					else if (mediaPage.selectedType == 3 && view is SharedLinkCell) {
						onItemClick(position, view, view.message, 0, mediaPage.selectedType)
					}
					else if ((mediaPage.selectedType == 2 || mediaPage.selectedType == 4) && view is SharedAudioCell) {
						onItemClick(position, view, view.message, 0, mediaPage.selectedType)
					}
					else if (mediaPage.selectedType == 5 && view is ContextLinkCell) {
						onItemClick(position, view, view.parentObject as MessageObject, 0, mediaPage.selectedType)
					}
					else if (mediaPage.selectedType == 0 && view is SharedPhotoVideoCell2) {
						val messageObject = view.messageObject

						if (messageObject != null) {
							onItemClick(position, view, messageObject, 0, mediaPage.selectedType)
						}
					}
				}
			})

			mediaPage.listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
					scrolling = newState != RecyclerView.SCROLL_STATE_IDLE
				}

				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					checkLoadMoreScroll(mediaPage, recyclerView as RecyclerListView, layoutManager)

					if (dy != 0 && (mediaPages[0].selectedType == 0 || mediaPages[0].selectedType == 5) && sharedMediaData[0].messages.isNotEmpty()) {
						showFloatingDateView()
					}

					if (dy != 0 && mediaPage.selectedType == 0) {
						showFastScrollHint(mediaPage, sharedMediaData, true)
					}

					mediaPage.listView!!.checkSection(true)

					if (mediaPage.fastScrollHintView != null) {
						mediaPage.invalidate()
					}

					invalidateBlur()
				}
			})

			mediaPage.listView?.setOnItemLongClickListener(RecyclerListView.OnItemLongClickListener { view, position ->
				if (photoVideoChangeColumnsAnimation) {
					return@OnItemLongClickListener false
				}

				if (isActionModeShowed) {
					mediaPage.listView?.onItemClickListener?.onItemClick(view, position)
					return@OnItemLongClickListener true
				}

				if (mediaPage.selectedType == 7 && view is UserCell) {
					val participant = if (chatUsersAdapter.sortedUsers!!.isNotEmpty()) {
						chatUsersAdapter.chatInfo?.participants?.participants?.get(chatUsersAdapter.sortedUsers!![position])
					}
					else {
						chatUsersAdapter.chatInfo?.participants?.participants?.get(position)
					}

					return@OnItemLongClickListener onMemberClick(participant, true)
				}
				else if (mediaPage.selectedType == 1 && view is SharedDocumentCell) {
					return@OnItemLongClickListener onItemLongClick(view.message, view, 0)
				}
				else if (mediaPage.selectedType == 3 && view is SharedLinkCell) {
					return@OnItemLongClickListener onItemLongClick(view.message, view, 0)
				}
				else if ((mediaPage.selectedType == 2 || mediaPage.selectedType == 4) && view is SharedAudioCell) {
					return@OnItemLongClickListener onItemLongClick(view.message, view, 0)
				}
				else if (mediaPage.selectedType == 5 && view is ContextLinkCell) {
					return@OnItemLongClickListener onItemLongClick(view.parentObject as MessageObject, view, 0)
				}
				else if (mediaPage.selectedType == 0 && view is SharedPhotoVideoCell2) {
					val messageObject = view.messageObject

					if (messageObject != null) {
						return@OnItemLongClickListener onItemLongClick(messageObject, view, 0)
					}
				}

				false
			})

//			if (a == 0 && scrollToPositionOnRecreate != -1) {
//				layoutManager?.scrollToPositionWithOffset(scrollToPositionOnRecreate, scrollToOffsetOnRecreate)
//			}

			val listView: RecyclerListView? = mediaPage.listView

			mediaPage.animatingImageView = object : ClippingImageView(context) {
				override fun invalidate() {
					super.invalidate()
					listView?.invalidate()
				}
			}

			mediaPage.animatingImageView?.visibility = GONE
			mediaPage.listView?.addOverlayView(mediaPage.animatingImageView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			mediaPage.progressView = object : FlickerLoadingView(context) {
				override val columnsCount: Int
					get() = mediaColumnsCount

				override fun getViewType(): Int {
					setIsSingleCell(false)

					when (mediaPage.selectedType) {
						0, 5 -> {
							return 2
						}

						1 -> {
							return 3
						}

						2, 4 -> {
							return 4
						}

						3 -> {
							return 5
						}

						7 -> {
							return USERS_TYPE
						}

						6 -> {
							if (scrollSlidingTextTabStrip.tabsCount == 1) {
								setIsSingleCell(true)
							}

							return 1
						}

						else -> {
							return 1
						}
					}
				}

				override fun onDraw(canvas: Canvas) {
					backgroundPaint.color = ResourcesCompat.getColor(context.resources, R.color.background, null)
					canvas.drawRect(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), backgroundPaint)
					super.onDraw(canvas)
				}
			}

			mediaPage.progressView?.showDate(false)

			if (it != 0) {
				mediaPage.gone()
			}

			mediaPage.emptyView = StickerEmptyView(context, mediaPage.progressView, StickerEmptyView.STICKER_TYPE_SEARCH)
			mediaPage.emptyView?.visibility = GONE
			mediaPage.emptyView?.setAnimateLayoutChange(true)

			mediaPage.addView(mediaPage.emptyView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			mediaPage.emptyView?.setOnTouchListener { _, _ ->
				true
			}

			mediaPage.emptyView?.showProgress(show = true, animated = false)
			mediaPage.emptyView?.title?.text = context.getString(R.string.NoResult)
			mediaPage.emptyView?.subtitle?.text = context.getString(R.string.SearchEmptyViewFilteredSubtitle2)
			mediaPage.emptyView?.addView(mediaPage.progressView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			mediaPage.listView?.setEmptyView(mediaPage.emptyView)
			mediaPage.listView?.setAnimateEmptyView(true, 0)

			mediaPage.scrollHelper = RecyclerAnimationScrollHelper(mediaPage.listView, mediaPage.layoutManager)

			mediaPage
		}.toTypedArray()

		floatingDateView = ChatActionCell(context)
		floatingDateView.setCustomDate((System.currentTimeMillis() / 1000).toInt(), scheduled = false, inLayout = false)
		floatingDateView.alpha = 0.0f
		floatingDateView.setOverrideColor(context.getColor(R.color.light_background), context.getColor(R.color.white))
		floatingDateView.translationY = -AndroidUtilities.dp(48f).toFloat()

		addView(floatingDateView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0f, (48 + 4).toFloat(), 0f, 0f))

		addView(FragmentContextView(context, profileActivity, this, false).also { fragmentContextView = it }, createFrame(LayoutHelper.MATCH_PARENT, 38f, Gravity.TOP or Gravity.LEFT, 0f, 48f, 0f, 0f))

		fragmentContextView?.setDelegate { start, _ ->
			if (!start) {
				requestLayout()
			}
		}

		addView(scrollSlidingTextTabStrip, createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.TOP))

		addView(actionModeLayout, createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.TOP))

		shadowLine = View(context)
		shadowLine.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.divider, null))

		val layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
		layoutParams.topMargin = AndroidUtilities.dp(48f) - 1

		addView(shadowLine, layoutParams)

		updateTabs(false)

		switchToCurrentSelectedMode(false)

		if (hasMedia[0] >= 0) {
			loadFastScrollData(false)
		}
	}

	val isInFastScroll: Boolean
		get() = mediaPages[0].listView?.fastScroll?.isPressed == true

	fun dispatchFastScrollEvent(ev: MotionEvent): Boolean {
		val view = parent as View
		ev.offsetLocation(-view.x - x - mediaPages[0].listView!!.fastScroll!!.x, -view.y - y - mediaPages[0].y - mediaPages[0].listView!!.fastScroll!!.y)
		return mediaPages[0].listView!!.fastScroll!!.dispatchTouchEvent(ev)
	}

	fun checkPinchToZoom(ev: MotionEvent): Boolean {
		if (mediaPages[0].selectedType != 0 || parent == null) {
			return false
		}

		if (photoVideoChangeColumnsAnimation && !isInPinchToZoomTouchMode) {
			return true
		}

		if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
			if (maybePinchToZoomTouchMode && !isInPinchToZoomTouchMode && ev.pointerCount == 2 /*&& finishZoomTransition == null*/) {
				pinchStartDistance = hypot((ev.getX(1) - ev.getX(0)).toDouble(), (ev.getY(1) - ev.getY(0)).toDouble()).toFloat()

				pinchScale = 1f

				pointerId1 = ev.getPointerId(0)
				pointerId2 = ev.getPointerId(1)

				mediaPages[0].listView!!.cancelClickRunnables(false)
				mediaPages[0].listView!!.cancelLongPress()
				mediaPages[0].listView!!.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0))

				val view = parent as View

				pinchCenterX = (((ev.getX(0) + ev.getX(1)) / 2.0f).toInt() - view.x - x - mediaPages[0].x).toInt()
				pinchCenterY = (((ev.getY(0) + ev.getY(1)) / 2.0f).toInt() - view.y - y - mediaPages[0].y).toInt()

				selectPinchPosition(pinchCenterX, pinchCenterY)

				maybePinchToZoomTouchMode2 = true
			}

			if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
				val view = parent as View
				val y = ev.y - view.y - y - mediaPages[0].y

				if (y > 0) {
					maybePinchToZoomTouchMode = true
				}
			}
		}
		else if (ev.actionMasked == MotionEvent.ACTION_MOVE && (isInPinchToZoomTouchMode || maybePinchToZoomTouchMode2)) {
			var index1 = -1
			var index2 = -1

			for (i in 0 until ev.pointerCount) {
				if (pointerId1 == ev.getPointerId(i)) {
					index1 = i
				}

				if (pointerId2 == ev.getPointerId(i)) {
					index2 = i
				}
			}

			if (index1 == -1 || index2 == -1) {
				maybePinchToZoomTouchMode = false
				maybePinchToZoomTouchMode2 = false
				isInPinchToZoomTouchMode = false
				finishPinchToMediaColumnsCount()
				return false
			}

			pinchScale = hypot((ev.getX(index2) - ev.getX(index1)).toDouble(), (ev.getY(index2) - ev.getY(index1)).toDouble()).toFloat() / pinchStartDistance

			if (!isInPinchToZoomTouchMode && (pinchScale > 1.01f || pinchScale < 0.99f)) {
				isInPinchToZoomTouchMode = true
				pinchScaleUp = pinchScale > 1f
				startPinchToMediaColumnsCount(pinchScaleUp)
			}

			if (isInPinchToZoomTouchMode) {
				photoVideoChangeColumnsProgress = if (pinchScaleUp && pinchScale < 1f || !pinchScaleUp && pinchScale > 1f) {
					0f
				}
				else {
					max(0f, min(1f, if (pinchScaleUp) 1f - (2f - pinchScale) / 1f else (1f - pinchScale) / 0.5f))
				}

				if (photoVideoChangeColumnsProgress == 1f || photoVideoChangeColumnsProgress == 0f) {
					if (photoVideoChangeColumnsProgress == 1f) {
						val newRow = ceil((pinchCenterPosition / animateToColumnsCount.toFloat()).toDouble()).toInt()
						val columnWidth = (mediaPages[0].listView!!.measuredWidth / animateToColumnsCount.toFloat()).toInt()
						val newColumn = (startedTrackingX / (mediaPages[0].listView!!.measuredWidth - columnWidth).toFloat() * (animateToColumnsCount - 1)).toInt()
						var newPosition = newRow * animateToColumnsCount + newColumn

						if (newPosition >= photoVideoAdapter.itemCount) {
							newPosition = photoVideoAdapter.itemCount - 1
						}

						pinchCenterPosition = newPosition
					}

					finishPinchToMediaColumnsCount()

					if (photoVideoChangeColumnsProgress == 0f) {
						pinchScaleUp = !pinchScaleUp
					}

					startPinchToMediaColumnsCount(pinchScaleUp)

					pinchStartDistance = hypot((ev.getX(1) - ev.getX(0)).toDouble(), (ev.getY(1) - ev.getY(0)).toDouble()).toFloat()
				}

				mediaPages[0].listView!!.invalidate()

				if (mediaPages[0].fastScrollHintView != null) {
					mediaPages[0].invalidate()
				}
			}
		}
		else if ((ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev) || ev.actionMasked == MotionEvent.ACTION_CANCEL) && isInPinchToZoomTouchMode) {
			maybePinchToZoomTouchMode2 = false
			maybePinchToZoomTouchMode = false
			isInPinchToZoomTouchMode = false
			finishPinchToMediaColumnsCount()
		}

		return isInPinchToZoomTouchMode
	}

	private fun selectPinchPosition(pinchCenterX: Int, pinchCenterY: Int) {
		pinchCenterPosition = -1

		var y = pinchCenterY + mediaPages[0].listView!!.blurTopPadding

		if (getY() != 0f && viewType == VIEW_TYPE_PROFILE_ACTIVITY) {
			y = 0
		}

		for (i in 0 until mediaPages[0].listView!!.childCount) {
			val child = mediaPages[0].listView!!.getChildAt(i)
			child.getHitRect(rect)

			if (rect.contains(pinchCenterX, y)) {
				pinchCenterPosition = mediaPages[0].listView!!.getChildLayoutPosition(child)
				pinchCenterOffset = child.top
			}
		}

		if (delegate.canSearchMembers()) {
			if (pinchCenterPosition == -1) {
				val x = min(1f, max(pinchCenterX / mediaPages[0].listView!!.measuredWidth.toFloat(), 0f))
				pinchCenterPosition = (mediaPages[0].layoutManager!!.findFirstVisibleItemPosition() + (mediaColumnsCount - 1) * x).toInt()
				pinchCenterOffset = 0
			}
		}
	}

	private fun checkPointerIds(ev: MotionEvent): Boolean {
		if (ev.pointerCount < 2) {
			return false
		}

		return if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
			true
		}
		else {
			pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)
		}
	}

	val isSwipeBackEnabled: Boolean
		get() = !photoVideoChangeColumnsAnimation && !tabsAnimationInProgress

	fun getPhotosVideosTypeFilter(): Int {
		return sharedMediaData[0].filterType
	}

	fun drawListForBlur(blurCanvas: Canvas) {
		for (mediaPage in mediaPages) {
			if (mediaPage.isVisible) {
				for (j in 0 until mediaPage.listView!!.childCount) {
					val child = mediaPage.listView!!.getChildAt(j)

					if (child.y < mediaPage.listView!!.blurTopPadding + AndroidUtilities.dp(100f)) {
						blurCanvas.withTranslation(mediaPage.x + child.x, y + mediaPage.y + mediaPage.listView!!.y + child.y) {
							child.draw(blurCanvas)
						}
					}
				}
			}
		}
	}

	private fun updateFastScrollVisibility(mediaPage: MediaPage?, animated: Boolean) {
		val show = mediaPage!!.fastScrollEnabled && isPinnedToTop
		val view = mediaPage.listView!!.fastScroll ?: return

		mediaPage.fastScrollAnimator?.removeAllListeners()
		mediaPage.fastScrollAnimator?.cancel()

		if (!animated) {
			view.animate().setListener(null).cancel()
			view.visibility = if (show) VISIBLE else GONE
			view.tag = if (show) 1 else null
			view.alpha = 1f
			view.scaleX = 1f
			view.scaleY = 1f
		}
		else if (show && view.tag == null) {
			view.animate().setListener(null).cancel()

			if (view.visibility != VISIBLE) {
				view.visibility = VISIBLE
				view.alpha = 0f
			}

			val objectAnimator = ObjectAnimator.ofFloat(view, ALPHA, view.alpha, 1f)
			mediaPage.fastScrollAnimator = objectAnimator
			objectAnimator.setDuration(150).start()
			view.tag = 1
		}
		else if (!show && view.tag != null) {
			val objectAnimator = ObjectAnimator.ofFloat(view, ALPHA, view.alpha, 0f)
			objectAnimator.addListener(HideViewAfterAnimation(view))
			mediaPage.fastScrollAnimator = objectAnimator
			objectAnimator.setDuration(150).start()
			view.animate().setListener(null).cancel()
			view.tag = null
		}
	}

	protected open fun invalidateBlur() {
		// stub
	}

	fun setForwardRestrictedHint(hintView: HintView?) {
		fwdRestrictedHint = hintView
	}

	private fun getMessageId(child: View): Int {
		return when (child) {
			is SharedPhotoVideoCell2 -> child.messageId
			is SharedDocumentCell -> child.message.id
			is SharedAudioCell -> child.message?.id ?: 0
			else -> 0
		}
	}

	private fun updateForwardItem() {
		val forwardItem = forwardItem ?: return

		val noforwards = profileActivity.messagesController.isChatNoForwards(-dialogId) || hasNoforwardsMessage()

		forwardItem.alpha = if (noforwards) 0.5f else 1f

		if (noforwards && forwardItem.background != null) {
			forwardItem.background = null
		}
		else if (!noforwards && forwardItem.background == null) {
			forwardItem.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.action_bar_item, null), 5)
		}
	}

	private fun hasNoforwardsMessage(): Boolean {
		var hasNoforwardsMessage = false

		for (a in 1 downTo 0) {
			val ids = mutableListOf<Int>()

			for (b in 0 until selectedFiles[a].size()) {
				ids.add(selectedFiles[a].keyAt(b))
			}

			for (id1 in ids) {
				if (id1 > 0) {
					val msg = selectedFiles[a][id1]

					if (msg.messageOwner?.noforwards == true) {
						hasNoforwardsMessage = true
						break
					}
				}
			}

			if (hasNoforwardsMessage) {
				break
			}
		}

		return hasNoforwardsMessage
	}

	private fun changeMediaFilterType() {
		val mediaPage = getMediaPage(0)

		if (mediaPage != null && mediaPage.measuredHeight > 0 && mediaPage.measuredWidth > 0) {
			var bitmap: Bitmap? = null

			try {
				bitmap = createBitmap(mediaPage.measuredWidth, mediaPage.measuredHeight)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			if (bitmap != null) {
				changeTypeAnimation = true

				val canvas = Canvas(bitmap)

				mediaPage.listView?.draw(canvas)

				val view = View(mediaPage.context)
				view.background = bitmap.toDrawable(context.resources)

				mediaPage.addView(view)

				val finalBitmap: Bitmap = bitmap

				view.animate().alpha(0f).setDuration(200).setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						changeTypeAnimation = false

						if (view.parent != null) {
							mediaPage.removeView(view)
							finalBitmap.recycle()
						}
					}
				}).start()

				mediaPage.listView?.alpha = 0f
				mediaPage.listView?.animate()?.alpha(1f)?.setDuration(200)?.start()
			}
		}

		val counts = sharedMediaPreloader!!.lastMediaCount
		val messages = sharedMediaPreloader.sharedMediaData[0].messages

		when (sharedMediaData[0].filterType) {
			FILTER_PHOTOS_AND_VIDEOS -> sharedMediaData[0].totalCount = counts[0]
			FILTER_PHOTOS_ONLY -> sharedMediaData[0].totalCount = counts[6]
			else -> sharedMediaData[0].totalCount = counts[7]
		}

		sharedMediaData[0].fastScrollDataLoaded = false

		jumpToDate(0, if (DialogObject.isEncryptedDialog(dialogId)) Int.MIN_VALUE else Int.MAX_VALUE, 0, true)

		loadFastScrollData(false)

		delegate.updateSelectedMediaTabText()

		val enc = DialogObject.isEncryptedDialog(dialogId)

		for (i in messages.indices) {
			val messageObject = messages[i]

			if (sharedMediaData[0].filterType == FILTER_PHOTOS_AND_VIDEOS) {
				sharedMediaData[0].addMessage(messageObject, 0, false, enc)
			}
			else if (sharedMediaData[0].filterType == FILTER_PHOTOS_ONLY) {
				if (messageObject.isPhoto) {
					sharedMediaData[0].addMessage(messageObject, 0, false, enc)
				}
			}
			else {
				if (!messageObject.isPhoto) {
					sharedMediaData[0].addMessage(messageObject, 0, false, enc)
				}
			}
		}
	}

	private fun getMediaPage(type: Int): MediaPage? {
		for (mediaPage in mediaPages) {
			if (mediaPage.selectedType == type) { // was == 0
				return mediaPage
			}
		}

		return null
	}

	private fun showMediaCalendar(fromFastScroll: Boolean) {
		if (fromFastScroll && this@SharedMediaLayout.y != 0f && viewType == VIEW_TYPE_PROFILE_ACTIVITY) {
			return
		}

		val bundle = Bundle()
		bundle.putLong("dialog_id", dialogId)

		var date = 0

		if (fromFastScroll) {
			val mediaPage = getMediaPage(0)

			if (mediaPage != null) {
				val periods = sharedMediaData[0].fastScrollPeriods
				var period: Period? = null
				val position = mediaPage.layoutManager!!.findFirstVisibleItemPosition()

				if (position >= 0) {
					for (i in periods.indices) {
						if (position <= periods[i].startOffset) {
							period = periods[i]
							break
						}
					}

					if (period == null) {
						period = periods[periods.size - 1]
					}

					date = period.date
				}
			}
		}

		bundle.putInt("type", CalendarActivity.TYPE_MEDIA_CALENDAR)

		val calendarActivity = CalendarActivity(bundle, sharedMediaData[0].filterType, date)

		calendarActivity.setCallback { messageId, startOffset ->
			var index = -1

			for (i in sharedMediaData[0].messages.indices) {
				if (sharedMediaData[0].messages[i].id == messageId) {
					index = i
				}
			}

			val mediaPage = getMediaPage(0)

			if (index >= 0 && mediaPage != null) {
				mediaPage.layoutManager!!.scrollToPositionWithOffset(index, 0)
			}
			else {
				jumpToDate(0, messageId, startOffset, true)
			}

			if (mediaPage != null) {
				mediaPage.highlightMessageId = messageId
				mediaPage.highlightAnimation = false
			}
		}

		profileActivity.presentFragment(calendarActivity)
	}

	private fun startPinchToMediaColumnsCount(pinchScaleUp: Boolean) {
		if (photoVideoChangeColumnsAnimation) {
			return
		}

		var mediaPage: MediaPage? = null

		for (value in mediaPages) {
			if (value.selectedType == 0) {
				mediaPage = value
				break
			}
		}

		if (mediaPage != null) {
			val newColumnsCount = getNextMediaColumnsCount(mediaColumnsCount, pinchScaleUp)

			animateToColumnsCount = newColumnsCount

			if (animateToColumnsCount == mediaColumnsCount) {
				return
			}

			mediaPage.animationSupportingListView?.visibility = VISIBLE
			mediaPage.animationSupportingListView?.adapter = animationSupportingPhotoVideoAdapter
			mediaPage.animationSupportingLayoutManager?.spanCount = newColumnsCount

			AndroidUtilities.updateVisibleRows(mediaPage.listView)

			photoVideoChangeColumnsAnimation = true
			sharedMediaData[0].setListFrozen(true)

			photoVideoChangeColumnsProgress = 0f

			if (pinchCenterPosition >= 0) {
				for (page in mediaPages) {
					if (page.selectedType == 0) {
						page.animationSupportingLayoutManager?.scrollToPositionWithOffset(pinchCenterPosition, pinchCenterOffset - page.animationSupportingListView!!.paddingTop)
					}
				}
			}
			else {
				saveScrollPosition()
			}
		}
	}

	private fun finishPinchToMediaColumnsCount() {
		if (photoVideoChangeColumnsAnimation) {
			var mediaPage: MediaPage? = null

			for (value in mediaPages) {
				if (value.selectedType == 0) {
					mediaPage = value
					break
				}
			}

			if (mediaPage != null) {
				if (photoVideoChangeColumnsProgress == 1f) {
					val oldItemCount = photoVideoAdapter.itemCount

					photoVideoChangeColumnsAnimation = false

					sharedMediaData[0].setListFrozen(false)

					mediaPage.animationSupportingListView?.gone()

					mediaColumnsCount = animateToColumnsCount

					SharedConfig.setMediaColumnsCount(animateToColumnsCount)

					mediaPage.layoutManager?.spanCount = mediaColumnsCount
					mediaPage.listView?.invalidate()

					if (photoVideoAdapter.itemCount == oldItemCount) {
						AndroidUtilities.updateVisibleRows(mediaPage.listView)
					}
					else {
						photoVideoAdapter.notifyDataSetChanged()
					}

					if (pinchCenterPosition >= 0) {
						for (page in mediaPages) {
							if (page.selectedType == 0) {
								val view = page.animationSupportingLayoutManager?.findViewByPosition(pinchCenterPosition)

								if (view != null) {
									pinchCenterOffset = view.top
								}

								page.layoutManager?.scrollToPositionWithOffset(pinchCenterPosition, -page.listView!!.paddingTop + pinchCenterOffset)
							}
						}
					}
					else {
						saveScrollPosition()
					}

					return
				}

				if (photoVideoChangeColumnsProgress == 0f) {
					photoVideoChangeColumnsAnimation = false
					sharedMediaData[0].setListFrozen(false)
					mediaPage.animationSupportingListView?.gone()
					mediaPage.listView?.invalidate()
					return
				}

				val forward = photoVideoChangeColumnsProgress > 0.2f
				val animator = ValueAnimator.ofFloat(photoVideoChangeColumnsProgress, if (forward) 1f else 0f)

				val finalMediaPage: MediaPage = mediaPage

				animator.addUpdateListener {
					photoVideoChangeColumnsProgress = it.animatedValue as Float
					finalMediaPage.listView?.invalidate()
				}

				animator.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						val oldItemCount = photoVideoAdapter.itemCount

						photoVideoChangeColumnsAnimation = false

						sharedMediaData[0].setListFrozen(false)

						if (forward) {
							mediaColumnsCount = animateToColumnsCount
							SharedConfig.setMediaColumnsCount(animateToColumnsCount)
							finalMediaPage.layoutManager?.spanCount = mediaColumnsCount
						}

						if (forward) {
							if (photoVideoAdapter.itemCount == oldItemCount) {
								AndroidUtilities.updateVisibleRows(finalMediaPage.listView)
							}
							else {
								photoVideoAdapter.notifyDataSetChanged()
							}
						}

						finalMediaPage.animationSupportingListView?.gone()

						if (pinchCenterPosition >= 0) {
							for (page in mediaPages) {
								if (page.selectedType == 0) {
									if (forward) {
										val view = page.animationSupportingLayoutManager?.findViewByPosition(pinchCenterPosition)

										if (view != null) {
											pinchCenterOffset = view.top
										}
									}

									page.layoutManager?.scrollToPositionWithOffset(pinchCenterPosition, -page.listView!!.paddingTop + pinchCenterOffset)
								}
							}
						}
						else {
							saveScrollPosition()
						}

						super.onAnimationEnd(animation)
					}
				})

				animator.interpolator = CubicBezierInterpolator.DEFAULT
				animator.duration = 200
				animator.start()
			}
		}
	}

	private fun animateToMediaColumnsCount(newColumnsCount: Int) {
		val mediaPage = getMediaPage(0)

		pinchCenterPosition = -1

		if (mediaPage != null) {
			mediaPage.listView?.stopScroll()

			animateToColumnsCount = newColumnsCount

			mediaPage.animationSupportingListView?.visible()
			mediaPage.animationSupportingListView?.adapter = animationSupportingPhotoVideoAdapter
			mediaPage.animationSupportingLayoutManager?.spanCount = newColumnsCount

			AndroidUtilities.updateVisibleRows(mediaPage.listView)

			photoVideoChangeColumnsAnimation = true

			sharedMediaData[0].setListFrozen(true)

			photoVideoChangeColumnsProgress = 0f

			saveScrollPosition()

			val animator = ValueAnimator.ofFloat(0f, 1f)
			val finalMediaPage: MediaPage = mediaPage

			animationIndex = NotificationCenter.getInstance(profileActivity.currentAccount).setAnimationInProgress(animationIndex, null)

			animator.addUpdateListener {
				photoVideoChangeColumnsProgress = it.animatedValue as Float
				finalMediaPage.listView?.invalidate()
			}

			animator.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					NotificationCenter.getInstance(profileActivity.currentAccount).onAnimationFinish(animationIndex)

					val oldItemCount = photoVideoAdapter.itemCount

					photoVideoChangeColumnsAnimation = false

					sharedMediaData[0].setListFrozen(false)

					mediaColumnsCount = newColumnsCount

					finalMediaPage.layoutManager?.spanCount = mediaColumnsCount

					if (photoVideoAdapter.itemCount == oldItemCount) {
						AndroidUtilities.updateVisibleRows(finalMediaPage.listView)
					}
					else {
						photoVideoAdapter.notifyDataSetChanged()
					}

					finalMediaPage.animationSupportingListView?.gone()

					saveScrollPosition()
				}
			})

			animator.interpolator = CubicBezierInterpolator.DEFAULT
			animator.startDelay = 100
			animator.duration = 350
			animator.start()
		}
	}

	override fun dispatchDraw(canvas: Canvas) {
		canvas.withTranslation(scrollSlidingTextTabStrip.x, scrollSlidingTextTabStrip.y) {
			scrollSlidingTextTabStrip.drawBackground(this)
		}

		super.dispatchDraw(canvas)

		val fragmentContextView = fragmentContextView ?: return

		if (fragmentContextView.isCallStyle) {
			canvas.withTranslation(fragmentContextView.x, fragmentContextView.y) {
				fragmentContextView.setDrawOverlay(true)
				fragmentContextView.draw(this)
				fragmentContextView.setDrawOverlay(false)
			}
		}
	}

	private fun createScrollingTextTabStrip(context: Context): ScrollSlidingTextTabStripInner {
		val scrollSlidingTextTabStrip = ScrollSlidingTextTabStripInner(context)

		if (initialTab != -1) {
			scrollSlidingTextTabStrip.setInitialTabId(initialTab)
			initialTab = -1
		}

		scrollSlidingTextTabStrip.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))

		scrollSlidingTextTabStrip.setDelegate(object : ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate {
			override fun onPageSelected(page: Int, forward: Boolean) {
				if (mediaPages[0].selectedType == page) {
					return
				}

				mediaPages[1].selectedType = page
				mediaPages[1].visible()
				hideFloatingDateView(true)
				switchToCurrentSelectedMode(true)
				animatingForward = forward
				onSelectedTabChanged()
			}

			override fun onSamePageSelected() {
				scrollToTop()
			}

			override fun onPageScrolled(progress: Float) {
				if (progress == 1f && mediaPages[1].visibility != VISIBLE) {
					return
				}

				if (animatingForward) {
					mediaPages[0].translationX = -progress * mediaPages[0].measuredWidth
					mediaPages[1].translationX = mediaPages[0].measuredWidth - progress * mediaPages[0].measuredWidth
				}
				else {
					mediaPages[0].translationX = progress * mediaPages[0].measuredWidth
					mediaPages[1].translationX = progress * mediaPages[0].measuredWidth - mediaPages[0].measuredWidth
				}

				var photoVideoOptionsAlpha = 0f

				if (mediaPages[0].selectedType == 0) {
					photoVideoOptionsAlpha = 1f - progress
				}

				if (mediaPages[1].selectedType == 0) {
					photoVideoOptionsAlpha = progress
				}

				photoVideoOptionsItem.alpha = photoVideoOptionsAlpha
				photoVideoOptionsItem.visibility = if (photoVideoOptionsAlpha == 0f || !canShowSearchItem()) INVISIBLE else VISIBLE

				if (canShowSearchItem()) {
					if (searchItemState == 1) {
						searchItem?.alpha = progress
					}
					else if (searchItemState == 2) {
						searchItem?.alpha = 1.0f - progress
					}
				}
				else {
					searchItem?.invisible()
					searchItem?.alpha = 0.0f
				}

				if (progress == 1f) {
					val tempPage = mediaPages[0]

					mediaPages[0] = mediaPages[1]
					mediaPages[1] = tempPage

					mediaPages[1].gone()

					if (searchItemState == 2) {
						searchItem?.invisible()
					}

					searchItemState = 0

					startStopVisibleGifs()
				}
			}
		})

		return scrollSlidingTextTabStrip
	}

	protected open fun drawBackgroundWithBlur(canvas: Canvas, y: Float, rectTmp2: Rect, backgroundPaint: Paint) {
		canvas.drawRect(rectTmp2, backgroundPaint)
	}

	private fun fillMediaData(type: Int): Boolean {
		val mediaData = sharedMediaPreloader?.sharedMediaData ?: return false

		if (type == 0) {
			if (!sharedMediaData[type].fastScrollDataLoaded) {
				sharedMediaData[type].totalCount = mediaData[type].totalCount
			}
		}
		else {
			sharedMediaData[type].totalCount = mediaData[type].totalCount
		}

		sharedMediaData[type].messages.addAll(mediaData[type].messages)
		sharedMediaData[type].sections.addAll(mediaData[type].sections)

		for ((key, value) in mediaData[type].sectionArrays) {
			sharedMediaData[type].sectionArrays[key] = value.toMutableList()
		}

		for (i in 0..1) {
			sharedMediaData[type].messagesDict[i] = mediaData[type].messagesDict[i].clone()
			sharedMediaData[type].maxId[i] = mediaData[type].maxId[i]
			sharedMediaData[type].endReached[i] = mediaData[type].endReached[i]
		}

		sharedMediaData[type].fastScrollPeriods.addAll(mediaData[type].fastScrollPeriods)

		return mediaData[type].messages.isNotEmpty()
	}

	private fun showFloatingDateView() {
		// stub
	}

	private fun hideFloatingDateView(animated: Boolean) {
		AndroidUtilities.cancelRunOnUIThread(hideFloatingDateRunnable)

		if (floatingDateView.tag == null) {
			return
		}

		floatingDateView.tag = null

		floatingDateAnimation?.cancel()
		floatingDateAnimation = null

		if (animated) {
			floatingDateAnimation = AnimatorSet()
			floatingDateAnimation?.duration = 180
			floatingDateAnimation?.playTogether(ObjectAnimator.ofFloat(floatingDateView, ALPHA, 0.0f), ObjectAnimator.ofFloat(floatingDateView, TRANSLATION_Y, -AndroidUtilities.dp(48f) + additionalFloatingTranslation))
			floatingDateAnimation?.interpolator = CubicBezierInterpolator.EASE_OUT

			floatingDateAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					floatingDateAnimation = null
				}
			})

			floatingDateAnimation?.start()
		}
		else {
			floatingDateView.alpha = 0.0f
		}
	}

	private fun scrollToTop() {
		val height = when (mediaPages[0].selectedType) {
			0 -> SharedPhotoVideoCell.getItemSize(1)
			1, 2, 4 -> AndroidUtilities.dp(56f)
			3 -> AndroidUtilities.dp(100f)
			5 -> AndroidUtilities.dp(60f)
			6 -> AndroidUtilities.dp(58f)
			else -> AndroidUtilities.dp(58f)
		}

		val scrollDistance = if (mediaPages[0].selectedType == 0) {
			mediaPages[0].layoutManager!!.findFirstVisibleItemPosition() / mediaColumnsCount * height
		}
		else {
			mediaPages[0].layoutManager!!.findFirstVisibleItemPosition() * height
		}

		if (scrollDistance >= mediaPages[0].listView!!.measuredHeight * 1.2f) {
			mediaPages[0].scrollHelper!!.scrollDirection = RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP
			mediaPages[0].scrollHelper!!.scrollToPosition(0, 0, false, true)
		}
		else {
			mediaPages[0].listView!!.smoothScrollToPosition(0)
		}
	}

	private fun checkLoadMoreScroll(mediaPage: MediaPage, recyclerView: RecyclerListView?, layoutManager: LinearLayoutManager?) {
		if (photoVideoChangeColumnsAnimation || jumpToRunnable != null) {
			return
		}

		val currentTime = System.currentTimeMillis()

		if (recyclerView?.fastScroll != null && recyclerView.fastScroll!!.isPressed && currentTime - mediaPage.lastCheckScrollTime < 300) {
			return
		}

		mediaPage.lastCheckScrollTime = currentTime

		if (searching && searchWas || mediaPage.selectedType == 7) {
			return
		}

		val firstVisibleItem = layoutManager!!.findFirstVisibleItemPosition()
		val visibleItemCount = if (firstVisibleItem == RecyclerView.NO_POSITION) 0 else abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1
		var totalItemCount = recyclerView!!.adapter!!.itemCount

		if (mediaPage.selectedType == 0 || mediaPage.selectedType == 1 || mediaPage.selectedType == 2 || mediaPage.selectedType == 4) {
			val type = mediaPage.selectedType

			totalItemCount = sharedMediaData[type].startOffset + sharedMediaData[type].messages.size

			if (sharedMediaData[type].fastScrollDataLoaded && sharedMediaData[type].fastScrollPeriods.size > 2 && mediaPage.selectedType == 0 && sharedMediaData[type].messages.size != 0) {
				var columnsCount = 1

				if (type == 0) {
					columnsCount = mediaColumnsCount
				}

				var jumpToThreshold = (recyclerView.measuredHeight / (recyclerView.measuredWidth / columnsCount.toFloat()) * columnsCount * 1.5f).toInt()

				if (jumpToThreshold < 100) {
					jumpToThreshold = 100
				}

				if (jumpToThreshold < sharedMediaData[type].fastScrollPeriods[1].startOffset) {
					jumpToThreshold = sharedMediaData[type].fastScrollPeriods[1].startOffset
				}

				if (firstVisibleItem > totalItemCount && firstVisibleItem - totalItemCount > jumpToThreshold || firstVisibleItem + visibleItemCount < sharedMediaData[type].startOffset && sharedMediaData[0].startOffset - (firstVisibleItem + visibleItemCount) > jumpToThreshold) {
					AndroidUtilities.runOnUIThread(Runnable {
						findPeriodAndJumpToDate(type, recyclerView, false)
						jumpToRunnable = null
					}.also {
						jumpToRunnable = it
					})

					return
				}
			}
		}

		if (mediaPage.selectedType == 7) {
			// TODO: maybe remove this branch?
		}
		else if (mediaPage.selectedType == 6) {
			if (visibleItemCount > 0) {
				if (!commonGroupsAdapter.endReached && !commonGroupsAdapter.loading && commonGroupsAdapter.chats.isNotEmpty() && firstVisibleItem + visibleItemCount >= totalItemCount - 5) {
					commonGroupsAdapter.getChats(commonGroupsAdapter.chats[commonGroupsAdapter.chats.size - 1].id, 100)
				}
			}
		}
		else {
			val threshold = when (mediaPage.selectedType) {
				0 -> 3
				5 -> 10
				else -> 6
			}

			if ((firstVisibleItem + visibleItemCount > totalItemCount - threshold || sharedMediaData[mediaPage.selectedType].loadingAfterFastScroll) && !sharedMediaData[mediaPage.selectedType].loading) {
				val type = when (mediaPage.selectedType) {
					0 -> {
						when (sharedMediaData[0].filterType) {
							FILTER_PHOTOS_ONLY -> MediaDataController.MEDIA_PHOTOS_ONLY
							FILTER_VIDEOS_ONLY -> MediaDataController.MEDIA_VIDEOS_ONLY
							else -> MediaDataController.MEDIA_PHOTOVIDEO
						}
					}

					1 -> MediaDataController.MEDIA_FILE
					2 -> MediaDataController.MEDIA_AUDIO
					4 -> MediaDataController.MEDIA_MUSIC
					5 -> MediaDataController.MEDIA_GIF
					else -> MediaDataController.MEDIA_URL
				}

				if (!sharedMediaData[mediaPage.selectedType].endReached[0]) {
					sharedMediaData[mediaPage.selectedType].loading = true
					profileActivity.mediaDataController.loadMedia(dialogId, 50, sharedMediaData[mediaPage.selectedType].maxId[0], 0, type, 1, profileActivity.classGuid, sharedMediaData[mediaPage.selectedType].requestIndex)
				}
				else if (mergeDialogId != 0L && !sharedMediaData[mediaPage.selectedType].endReached[1]) {
					sharedMediaData[mediaPage.selectedType].loading = true
					profileActivity.mediaDataController.loadMedia(mergeDialogId, 50, sharedMediaData[mediaPage.selectedType].maxId[1], 0, type, 1, profileActivity.classGuid, sharedMediaData[mediaPage.selectedType].requestIndex)
				}
			}

			var startOffset = sharedMediaData[mediaPage.selectedType].startOffset

			if (mediaPage.selectedType == 0) {
				startOffset = photoVideoAdapter.getPositionForIndex(0)
			}

			if (firstVisibleItem - startOffset < threshold + 1 && !sharedMediaData[mediaPage.selectedType].loading && !sharedMediaData[mediaPage.selectedType].startReached && !sharedMediaData[mediaPage.selectedType].loadingAfterFastScroll) {
				loadFromStart(mediaPage.selectedType)
			}

			if (mediaPages[0].listView === recyclerView && (mediaPages[0].selectedType == 0 || mediaPages[0].selectedType == 5) && firstVisibleItem != RecyclerView.NO_POSITION) {
				val holder = recyclerView.findViewHolderForAdapterPosition(firstVisibleItem)

				if (holder != null && holder.itemViewType == 0) {
					if (holder.itemView is SharedPhotoVideoCell) {
						val messageObject = holder.itemView.getMessageObject(0)

						if (messageObject != null) {
							floatingDateView.setCustomDate(messageObject.messageOwner!!.date, scheduled = false, inLayout = true)
						}
					}
					else if (holder.itemView is ContextLinkCell) {
						floatingDateView.setCustomDate(holder.itemView.date, scheduled = false, inLayout = true)
					}
				}
			}
		}
	}

	private fun loadFromStart(selectedType: Int) {
		val type = when (selectedType) {
			0 -> {
				when (sharedMediaData[0].filterType) {
					FILTER_PHOTOS_ONLY -> MediaDataController.MEDIA_PHOTOS_ONLY
					FILTER_VIDEOS_ONLY -> MediaDataController.MEDIA_VIDEOS_ONLY
					else -> MediaDataController.MEDIA_PHOTOVIDEO
				}
			}

			1 -> MediaDataController.MEDIA_FILE
			2 -> MediaDataController.MEDIA_AUDIO
			4 -> MediaDataController.MEDIA_MUSIC
			5 -> MediaDataController.MEDIA_GIF
			else -> MediaDataController.MEDIA_URL
		}

		sharedMediaData[selectedType].loading = true

		profileActivity.mediaDataController.loadMedia(dialogId, 50, 0, sharedMediaData[selectedType].minId, type, 1, profileActivity.classGuid, sharedMediaData[selectedType].requestIndex)
	}

	val isSearchItemVisible: Boolean
		get() = if (mediaPages[0].selectedType == 7) {
			delegate.canSearchMembers()
		}
		else {
			mediaPages[0].selectedType != 0 && mediaPages[0].selectedType != 2 && mediaPages[0].selectedType != 5 && mediaPages[0].selectedType != 6
		}

	val isCalendarItemVisible: Boolean
		get() = mediaPages[0].selectedType == 0

	val closestTab: Int
		get() {
			if (mediaPages[1].isVisible) {
				if (tabsAnimationInProgress && !backAnimation) {
					return mediaPages[1].selectedType
				}
				else if (abs(mediaPages[1].translationX) < mediaPages[1].measuredWidth / 2f) {
					return mediaPages[1].selectedType
				}
			}

			return scrollSlidingTextTabStrip.currentTabId
		}

	protected open fun onSelectedTabChanged() {
		// stub
	}

	protected open fun canShowSearchItem(): Boolean {
		return true
	}

	protected open fun onSearchStateChanged(expanded: Boolean) {
		// stub
	}

	protected open fun onMemberClick(participant: ChatParticipant?, isLong: Boolean): Boolean {
		return false
	}

	fun onDestroy() {
		profileActivity.notificationCenter.let {
			it.removeObserver(this, NotificationCenter.mediaDidLoad)
			it.removeObserver(this, NotificationCenter.didReceiveNewMessages)
			it.removeObserver(this, NotificationCenter.messagesDeleted)
			it.removeObserver(this, NotificationCenter.messageReceivedByServer)
			it.removeObserver(this, NotificationCenter.messagePlayingDidReset)
			it.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
			it.removeObserver(this, NotificationCenter.messagePlayingDidStart)
		}
	}

	private fun checkCurrentTabValid() {
		var id = scrollSlidingTextTabStrip.currentTabId

		if (!scrollSlidingTextTabStrip.hasTab(id)) {
			id = scrollSlidingTextTabStrip.firstTabId
			scrollSlidingTextTabStrip.setInitialTabId(id)
			mediaPages[0].selectedType = id
			switchToCurrentSelectedMode(false)
		}
	}

	fun setNewMediaCounts(mediaCounts: IntArray?) {
		var hadMedia = false

		for (a in 0..5) {
			if (hasMedia[a] >= 0) {
				hadMedia = true
				break
			}
		}

		if (mediaCounts != null) {
			System.arraycopy(mediaCounts, 0, hasMedia, 0, 6)
		}

		updateTabs(true)

		if (!hadMedia && scrollSlidingTextTabStrip.currentTabId == 6) {
			scrollSlidingTextTabStrip.resetTab()
		}

		checkCurrentTabValid()

		if (hasMedia[0] >= 0) {
			loadFastScrollData(false)
		}
	}

	private fun loadFastScrollData(force: Boolean) {
		for (type in supportedFastScrollTypes) {
			if (sharedMediaData[type].fastScrollDataLoaded && !force || DialogObject.isEncryptedDialog(dialogId)) {
				return
			}

			sharedMediaData[type].fastScrollDataLoaded = false

			val req = TLMessagesGetSearchResultsPositions()

			when (type) {
				0 -> {
					when (sharedMediaData[type].filterType) {
						FILTER_PHOTOS_ONLY -> req.filter = TLInputMessagesFilterPhotos()
						FILTER_VIDEOS_ONLY -> req.filter = TLInputMessagesFilterVideo()
						else -> req.filter = TLInputMessagesFilterPhotoVideo()
					}
				}

				MediaDataController.MEDIA_FILE -> req.filter = TLInputMessagesFilterDocument()
				MediaDataController.MEDIA_AUDIO -> req.filter = TLInputMessagesFilterRoundVoice()
				else -> req.filter = TLInputMessagesFilterMusic()
			}

			req.limit = 100
			req.peer = MessagesController.getInstance(profileActivity.currentAccount).getInputPeer(dialogId)

			val reqIndex = sharedMediaData[type].requestIndex

			val reqId = ConnectionsManager.getInstance(profileActivity.currentAccount).sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					if (error != null) {
						return@runOnUIThread
					}

					if (reqIndex != sharedMediaData[type].requestIndex) {
						return@runOnUIThread
					}

					val res = response as TLMessagesSearchResultsPositions

					sharedMediaData[type].fastScrollPeriods.clear()

					var i = 0
					val n = res.positions.size

					while (i < n) {
						val serverPeriod = res.positions[i]

						if (serverPeriod.date != 0) {
							val period = Period(serverPeriod)
							sharedMediaData[type].fastScrollPeriods.add(period)
						}

						i++
					}

					sharedMediaData[type].fastScrollPeriods.sortWith { period, period2 ->
						period2.date - period.date
					}

					sharedMediaData[type].totalCount = res.count
					sharedMediaData[type].fastScrollDataLoaded = true

					if (sharedMediaData[type].fastScrollPeriods.isNotEmpty()) {
						for (mediaPage in mediaPages) {
							if (mediaPage.selectedType == type) {
								mediaPage.fastScrollEnabled = true
								updateFastScrollVisibility(mediaPage, true)
							}
						}
					}

					photoVideoAdapter.notifyDataSetChanged()
				}
			}

			ConnectionsManager.getInstance(profileActivity.currentAccount).bindRequestToGuid(reqId, profileActivity.classGuid)
		}
	}

	fun setCommonGroupsCount(count: Int) {
		hasMedia[6] = count
		updateTabs(true)
		checkCurrentTabValid()
	}

	fun onActionBarItemClick(v: View?, id: Int) {
		when (id) {
			DELETE -> {
				var currentChat: Chat? = null
				var currentUser: User? = null
				var currentEncryptedChat: EncryptedChat? = null

				if (DialogObject.isEncryptedDialog(dialogId)) {
					currentEncryptedChat = profileActivity.messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))
				}
				else if (DialogObject.isUserDialog(dialogId)) {
					currentUser = profileActivity.messagesController.getUser(dialogId)
				}
				else {
					currentChat = profileActivity.messagesController.getChat(-dialogId)
				}

				AlertsCreator.createDeleteMessagesAlert(profileActivity, currentUser, currentChat, currentEncryptedChat, mergeDialogId, null, selectedFiles, null, false, 1, {
					showActionMode(false)
					actionBar?.closeSearchField()
					cantDeleteMessagesCount = 0
				}, null)
			}

			FORWARD -> {
				if (info != null) {
					val chat = profileActivity.messagesController.getChat(info!!.id)

					if (profileActivity.messagesController.isChatNoForwards(chat)) {
						fwdRestrictedHint?.setText(if (ChatObject.isChannel(chat) && !chat.megagroup) context.getString(R.string.ForwardsRestrictedInfoChannel) else context.getString(R.string.ForwardsRestrictedInfoGroup))
						fwdRestrictedHint?.showForView(v, true)
						return
					}
				}

				if (hasNoforwardsMessage()) {
					fwdRestrictedHint?.setText(context.getString(R.string.ForwardsRestrictedInfoBot))
					fwdRestrictedHint?.showForView(v, true)

					return
				}

				val args = Bundle()
				args.putBoolean("onlySelect", true)
				args.putInt("dialogsType", 3)

				val fragment = DialogsActivity(args)

				fragment.setDelegate { fragment1, dids, message, _ ->
					val fmessages = mutableListOf<MessageObject>()

					for (a in 1 downTo 0) {
						val ids = mutableListOf<Int>()

						for (b in 0 until selectedFiles[a].size()) {
							ids.add(selectedFiles[a].keyAt(b))
						}

						ids.sort()

						for (id1 in ids) {
							if (id1 > 0) {
								fmessages.add(selectedFiles[a][id1])
							}
						}

						selectedFiles[a].clear()
					}

					cantDeleteMessagesCount = 0

					showActionMode(false)

					if (dids.size > 1 || dids[0] == profileActivity.userConfig.getClientUserId() || message != null) {
						updateRowsSelection()

						for (a in dids.indices) {
							val did = dids[a]

							if (message != null) {
								profileActivity.sendMessagesHelper.sendMessage(message.toString(), did, null, null, null, true, null, null, null, true, 0, null, updateStickersOrder = false)
							}

							profileActivity.sendMessagesHelper.sendMessage(fmessages, did, forwardFromMyName = false, hideCaption = false, notify = true, scheduleDate = 0)
						}

						fragment1?.finishFragment()

						var undoView: UndoView? = null

						if (profileActivity is ProfileActivity) {
							undoView = profileActivity.undoView
						}

						if (undoView != null) {
							if (dids.size == 1) {
								undoView.showWithAction(dids[0], UndoView.ACTION_FWD_MESSAGES, fmessages.size)
							}
							else {
								undoView.showWithAction(0, UndoView.ACTION_FWD_MESSAGES, fmessages.size, dids.size, null, null)
							}
						}
					}
					else {
						val did = dids[0]

						val args1 = Bundle()
						args1.putBoolean("scrollToTopOnResume", true)

						if (DialogObject.isEncryptedDialog(did)) {
							args1.putInt("enc_id", DialogObject.getEncryptedChatId(did))
						}
						else {
							if (DialogObject.isUserDialog(did)) {
								args1.putLong("user_id", did)
							}
							else {
								args1.putLong("chat_id", -did)
							}

							if (!profileActivity.messagesController.checkCanOpenChat(args1, fragment1)) {
								return@setDelegate
							}
						}

						profileActivity.notificationCenter.postNotificationName(NotificationCenter.closeChats)

						val chatActivity = ChatActivity(args1)

						fragment1?.presentFragment(chatActivity, true)

						chatActivity.showFieldPanelForForward(true, fmessages)
					}
				}

				profileActivity.presentFragment(fragment)
			}

			GO_TO_CHAT -> {
				if (selectedFiles[0].size() + selectedFiles[1].size() != 1) {
					return
				}

				val messageObject = selectedFiles[if (selectedFiles[0].size() == 1) 0 else 1].valueAt(0)
				val args = Bundle()

				var dialogId = messageObject.dialogId

				if (DialogObject.isEncryptedDialog(dialogId)) {
					args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId))
				}
				else if (DialogObject.isUserDialog(dialogId)) {
					args.putLong("user_id", dialogId)
				}
				else {
					val chat = profileActivity.messagesController.getChat(-dialogId)

					chat?.migratedTo?.let {
						args.putLong("migrated_to", dialogId)
						dialogId = -it.channelId
					}

					args.putLong("chat_id", -dialogId)
				}

				args.putInt("message_id", messageObject.id)
				args.putBoolean("need_remove_previous_same_chat_activity", false)

				profileActivity.presentFragment(ChatActivity(args), false)
			}
		}
	}

	private fun prepareForMoving(ev: MotionEvent, forward: Boolean): Boolean {
		val id = scrollSlidingTextTabStrip.getNextPageId(forward)

		if (id < 0) {
			return false
		}

		if (canShowSearchItem()) {
			if (searchItemState != 0) {
				if (searchItemState == 2) {
					searchItem?.alpha = 1.0f
				}
				else if (searchItemState == 1) {
					searchItem?.alpha = 0.0f
					searchItem?.invisible()
				}

				searchItemState = 0
			}
		}
		else {
			searchItem?.invisible()
			searchItem?.alpha = 0.0f
		}

		parent.requestDisallowInterceptTouchEvent(true)

		hideFloatingDateView(true)

		maybeStartTracking = false
		startedTracking = true
		startedTrackingX = ev.x.toInt()
		actionBar?.isEnabled = false
		scrollSlidingTextTabStrip.isEnabled = false
		mediaPages[1].selectedType = id
		mediaPages[1].visible()
		animatingForward = forward

		switchToCurrentSelectedMode(true)

		if (forward) {
			mediaPages[1].translationX = mediaPages[0].measuredWidth.toFloat()
		}
		else {
			mediaPages[1].translationX = -mediaPages[0].measuredWidth.toFloat()
		}

		return true
	}

	override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
		topPadding = top

		for (mediaPage in mediaPages) {
			mediaPage.translationY = (topPadding - lastMeasuredTopPadding).toFloat()
		}

		fragmentContextView?.translationY = (AndroidUtilities.dp(48f) + top).toFloat()
		additionalFloatingTranslation = top.toFloat()
		floatingDateView.translationY = (if (floatingDateView.tag == null) -AndroidUtilities.dp(48f) else 0) + additionalFloatingTranslation
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val widthSize = MeasureSpec.getSize(widthMeasureSpec)
		var heightSize = if (delegate.listView != null) delegate.listView!!.height else 0

		if (heightSize == 0) {
			heightSize = MeasureSpec.getSize(heightMeasureSpec)
		}

		setMeasuredDimension(widthSize, heightSize)

		val childCount = childCount

		for (i in 0 until childCount) {
			val child = getChildAt(i)

			if (child == null || child.isGone) {
				continue
			}

			if (child is MediaPage) {
				measureChildWithMargins(child, widthMeasureSpec, 0, MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY), 0)
				child.listView!!.setPadding(0, 0, 0, topPadding)
			}
			else {
				measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
			}
		}
	}

	fun checkTabsAnimationInProgress(): Boolean {
		if (tabsAnimationInProgress) {
			var cancel = false

			if (backAnimation) {
				if (abs(mediaPages[0].translationX) < 1) {
					mediaPages[0].translationX = 0f
					mediaPages[1].translationX = (mediaPages[0].measuredWidth * if (animatingForward) 1 else -1).toFloat()
					cancel = true
				}
			}
			else if (abs(mediaPages[1].translationX) < 1) {
				mediaPages[0].translationX = (mediaPages[0].measuredWidth * if (animatingForward) -1 else 1).toFloat()
				mediaPages[1].translationX = 0f
				cancel = true
			}

			if (cancel) {
				tabsAnimation?.cancel()
				tabsAnimation = null

				tabsAnimationInProgress = false
			}

			return tabsAnimationInProgress
		}

		return false
	}

	override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
		return checkTabsAnimationInProgress() || scrollSlidingTextTabStrip.isAnimatingIndicator || onTouchEvent(ev)
	}

	val isCurrentTabFirst: Boolean
		get() = scrollSlidingTextTabStrip.currentTabId == scrollSlidingTextTabStrip.firstTabId

	val currentListView: RecyclerListView?
		get() = mediaPages[0].listView

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(ev: MotionEvent?): Boolean {
		if (profileActivity.parentLayout != null && !profileActivity.parentLayout!!.checkTransitionAnimation() && !checkTabsAnimationInProgress() && !isInPinchToZoomTouchMode) {
			if (ev != null) {
				if (velocityTracker == null) {
					velocityTracker = VelocityTracker.obtain()
				}

				velocityTracker?.addMovement(ev)

				fwdRestrictedHint?.hide()

			}

			if (ev != null && ev.action == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking && ev.y >= AndroidUtilities.dp(48f)) {
				startedTrackingPointerId = ev.getPointerId(0)
				maybeStartTracking = true
				startedTrackingX = ev.x.toInt()
				startedTrackingY = ev.y.toInt()
				velocityTracker?.clear()
			}
			else if (ev != null && ev.action == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
				val dx = (ev.x - startedTrackingX).toInt()
				val dy = abs(ev.y.toInt() - startedTrackingY)

				if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
					if (!prepareForMoving(ev, dx < 0)) {
						maybeStartTracking = true
						startedTracking = false
						mediaPages[0].translationX = 0f
						mediaPages[1].translationX = if (animatingForward) mediaPages[0].measuredWidth.toFloat() else -mediaPages[0].measuredWidth.toFloat()
						scrollSlidingTextTabStrip.selectTabWithId(mediaPages[1].selectedType, 0f)
					}
				}

				if (maybeStartTracking && !startedTracking) {
					val touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true)

					if (abs(dx) >= touchSlop && abs(dx) > dy) {
						prepareForMoving(ev, dx < 0)
					}
				}
				else if (startedTracking) {
					mediaPages[0].translationX = dx.toFloat()

					if (animatingForward) {
						mediaPages[1].translationX = (mediaPages[0].measuredWidth + dx).toFloat()
					}
					else {
						mediaPages[1].translationX = (dx - mediaPages[0].measuredWidth).toFloat()
					}

					val scrollProgress = abs(dx) / mediaPages[0].measuredWidth.toFloat()

					if (canShowSearchItem()) {
						if (searchItemState == 2) {
							searchItem?.alpha = 1.0f - scrollProgress
						}
						else if (searchItemState == 1) {
							searchItem?.alpha = scrollProgress
						}

						var photoVideoOptionsAlpha = 0f

						if (mediaPages[1].selectedType == 0) {
							photoVideoOptionsAlpha = scrollProgress
						}

						if (mediaPages[0].selectedType == 0) {
							photoVideoOptionsAlpha = 1f - scrollProgress
						}

						photoVideoOptionsItem.alpha = photoVideoOptionsAlpha
						photoVideoOptionsItem.visibility = if (photoVideoOptionsAlpha == 0f || !canShowSearchItem()) INVISIBLE else VISIBLE
					}
					else {
						searchItem?.alpha = 0.0f
					}

					scrollSlidingTextTabStrip.selectTabWithId(mediaPages[1].selectedType, scrollProgress)

					onSelectedTabChanged()
				}
			}
			else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.action == MotionEvent.ACTION_CANCEL || ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_POINTER_UP)) {
				velocityTracker!!.computeCurrentVelocity(1000, maximumVelocity.toFloat())

				var velX: Float
				val velY: Float

				if (ev != null && ev.action != MotionEvent.ACTION_CANCEL) {
					velX = velocityTracker!!.xVelocity
					velY = velocityTracker!!.yVelocity

					if (!startedTracking) {
						if (abs(velX) >= 3000 && abs(velX) > abs(velY)) {
							prepareForMoving(ev, velX < 0)
						}
					}
				}
				else {
					velX = 0f
					velY = 0f
				}

				if (startedTracking) {
					val x = mediaPages[0].x

					tabsAnimation = AnimatorSet()
					backAnimation = abs(x) < mediaPages[0].measuredWidth / 3.0f && (abs(velX) < 3500 || abs(velX) < abs(velY))

					val dx: Float

					if (backAnimation) {
						dx = abs(x)

						if (animatingForward) {
							tabsAnimation?.playTogether(ObjectAnimator.ofFloat(mediaPages[0], TRANSLATION_X, 0f), ObjectAnimator.ofFloat(mediaPages[1], TRANSLATION_X, mediaPages[1].measuredWidth.toFloat()))
						}
						else {
							tabsAnimation?.playTogether(ObjectAnimator.ofFloat(mediaPages[0], TRANSLATION_X, 0f), ObjectAnimator.ofFloat(mediaPages[1], TRANSLATION_X, -mediaPages[1].measuredWidth.toFloat()))
						}
					}
					else {
						dx = mediaPages[0].measuredWidth - abs(x)

						if (animatingForward) {
							tabsAnimation?.playTogether(ObjectAnimator.ofFloat(mediaPages[0], TRANSLATION_X, -mediaPages[0].measuredWidth.toFloat()), ObjectAnimator.ofFloat(mediaPages[1], TRANSLATION_X, 0f))
						}
						else {
							tabsAnimation?.playTogether(ObjectAnimator.ofFloat(mediaPages[0], TRANSLATION_X, mediaPages[0].measuredWidth.toFloat()), ObjectAnimator.ofFloat(mediaPages[1], TRANSLATION_X, 0f))
						}
					}

					tabsAnimation?.interpolator = interpolator

					val width = measuredWidth
					val halfWidth = width / 2
					val distanceRatio = min(1.0f, 1.0f * dx / width.toFloat())
					val distance = halfWidth.toFloat() + halfWidth.toFloat() * AndroidUtilities.distanceInfluenceForSnapDuration(distanceRatio)
					velX = abs(velX)

					var duration = if (velX > 0) {
						4 * (1000.0f * abs(distance / velX)).roundToLong().toInt()
					}
					else {
						val pageDelta = dx / measuredWidth
						((pageDelta + 1.0f) * 100.0f).toInt()
					}

					duration = max(150, min(duration, 600))

					tabsAnimation?.duration = duration.toLong()

					tabsAnimation?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animator: Animator) {
							tabsAnimation = null

							if (backAnimation) {
								mediaPages[1].gone()

								if (canShowSearchItem()) {
									if (searchItemState == 2) {
										searchItem?.alpha = 1.0f
									}
									else if (searchItemState == 1) {
										searchItem?.alpha = 0.0f
										searchItem?.invisible()
									}
								}
								else {
									searchItem?.invisible()
									searchItem?.alpha = 0.0f
								}

								searchItemState = 0
							}
							else {
								val tempPage = mediaPages[0]

								mediaPages[0] = mediaPages[1]
								mediaPages[1] = tempPage
								mediaPages[1].gone()

								if (searchItemState == 2) {
									searchItem?.invisible()
								}

								searchItemState = 0

								scrollSlidingTextTabStrip.selectTabWithId(mediaPages[0].selectedType, 1.0f)

								onSelectedTabChanged()
								startStopVisibleGifs()
							}

							tabsAnimationInProgress = false
							maybeStartTracking = false
							startedTracking = false
							actionBar?.isEnabled = true
							scrollSlidingTextTabStrip.isEnabled = true
						}
					})

					tabsAnimation?.start()

					tabsAnimationInProgress = true
					startedTracking = false

					onSelectedTabChanged()
				}
				else {
					maybeStartTracking = false
					actionBar?.isEnabled = true
					scrollSlidingTextTabStrip.isEnabled = true
				}

				velocityTracker?.recycle()
				velocityTracker = null
			}

			return startedTracking
		}

		return false
	}

	fun closeActionMode(): Boolean {
		return if (isActionModeShowed) {
			for (a in 1 downTo 0) {
				selectedFiles[a].clear()
			}

			cantDeleteMessagesCount = 0

			showActionMode(false)
			updateRowsSelection()

			true
		}
		else {
			false
		}
	}

	fun setVisibleHeight(height: Int) {
		val h = max(height, AndroidUtilities.dp(120f))

		for (mediaPage in mediaPages) {
			val t = -(measuredHeight - h) / 2f
			mediaPage.emptyView?.translationY = t
			mediaPage.progressView?.translationY = -t
		}
	}

	private fun showActionMode(show: Boolean) {
		if (isActionModeShowed == show) {
			return
		}

		isActionModeShowed = show

		actionModeAnimation?.cancel()

		if (show) {
			actionModeLayout.visible()
		}

		actionModeAnimation = AnimatorSet()
		actionModeAnimation?.playTogether(ObjectAnimator.ofFloat(actionModeLayout, ALPHA, if (show) 1.0f else 0.0f))
		actionModeAnimation?.duration = 180

		actionModeAnimation?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationCancel(animation: Animator) {
				actionModeAnimation = null
			}

			override fun onAnimationEnd(animation: Animator) {
				if (actionModeAnimation == null) {
					return
				}

				actionModeAnimation = null

				if (!show) {
					actionModeLayout.invisible()
				}
			}
		})

		actionModeAnimation?.start()
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.mediaDidLoad -> {
				val uid = args[0] as Long
				val guid = args[3] as Int
				val requestIndex = args[7] as Int
				var type = args[4] as Int
				val fromStart = args[6] as Boolean

				if (type == 6 || type == 7) {
					type = 0
				}

				if (guid == profileActivity.classGuid && requestIndex == sharedMediaData[type].requestIndex) {
					if (type != 0 && type != 1 && type != 2 && type != 4) {
						sharedMediaData[type].totalCount = args[1] as Int
					}

					val arr = args[2] as List<MessageObject>
					val enc = DialogObject.isEncryptedDialog(dialogId)
					val loadIndex = if (uid == dialogId) 0 else 1

					val adapter = when (type) {
						0 -> photoVideoAdapter
						1 -> documentsAdapter
						2 -> voiceAdapter
						3 -> linksAdapter
						4 -> audioAdapter
						5 -> gifAdapter
						else -> null
					}

					val oldItemCount: Int
					val oldMessagesCount = sharedMediaData[type].messages.size

					if (adapter != null) {
						oldItemCount = adapter.itemCount

						if (adapter is SectionsAdapter) {
							adapter.notifySectionsChanged()
						}
					}
					else {
						oldItemCount = 0
					}

					sharedMediaData[type].loading = false

					val addedMessages = SparseBooleanArray()

					if (fromStart) {
						for (a in arr.indices.reversed()) {
							val message = arr[a]
							val added = sharedMediaData[type].addMessage(message, loadIndex, true, enc)

							if (added) {
								addedMessages.put(message.id, true)
								sharedMediaData[type].startOffset--

								if (sharedMediaData[type].startOffset < 0) {
									sharedMediaData[type].startOffset = 0
								}
							}
						}

						sharedMediaData[type].startReached = args[5] as Boolean

						if (sharedMediaData[type].startReached) {
							sharedMediaData[type].startOffset = 0
						}
					}
					else {
						for (a in arr.indices) {
							val message = arr[a]

							if (sharedMediaData[type].addMessage(message, loadIndex, false, enc)) {

								addedMessages.put(message.id, true)
								sharedMediaData[type].endLoadingStubs--

								if (sharedMediaData[type].endLoadingStubs < 0) {
									sharedMediaData[type].endLoadingStubs = 0
								}
							}
						}

						if (sharedMediaData[type].loadingAfterFastScroll && sharedMediaData[type].messages.size > 0) {
							sharedMediaData[type].minId = sharedMediaData[type].messages[0].id
						}

						sharedMediaData[type].endReached[loadIndex] = args[5] as Boolean

						if (sharedMediaData[type].endReached[loadIndex]) {
							sharedMediaData[type].totalCount = sharedMediaData[type].messages.size + sharedMediaData[type].startOffset
						}
					}

					if (!fromStart && loadIndex == 0 && sharedMediaData[type].endReached[loadIndex] && mergeDialogId != 0L) {
						sharedMediaData[type].loading = true
						profileActivity.mediaDataController.loadMedia(mergeDialogId, 50, sharedMediaData[type].maxId[1], 0, type, 1, profileActivity.classGuid, sharedMediaData[type].requestIndex)
					}

					if (adapter != null) {
						var listView: RecyclerListView? = null

						for (mediaPage in mediaPages) {
							if (mediaPage.listView!!.adapter === adapter) {
								listView = mediaPage.listView
								mediaPage.listView?.stopScroll()
							}
						}

						val newItemCount = adapter.itemCount

						if (adapter === photoVideoAdapter) {
							if (photoVideoAdapter.itemCount == oldItemCount) {
								AndroidUtilities.updateVisibleRows(listView)
							}
							else {
								photoVideoAdapter.notifyDataSetChanged()
							}
						}
						else {
							adapter.notifyDataSetChanged()
						}

						if (sharedMediaData[type].messages.isEmpty() && !sharedMediaData[type].loading) {
							listView?.let {
								animateItemsEnter(it, oldItemCount, addedMessages)
							}
						}
						else {
							if (listView != null && (adapter === photoVideoAdapter || newItemCount >= oldItemCount)) {
								animateItemsEnter(listView, oldItemCount, addedMessages)
							}
						}

						if (listView != null && !sharedMediaData[type].loadingAfterFastScroll) {
							if (oldMessagesCount == 0) {
								for (k in 0..1) {
									if (mediaPages[k].selectedType == 0) {
										val position = photoVideoAdapter.getPositionForIndex(0)
										(listView.layoutManager as LinearLayoutManager?)!!.scrollToPositionWithOffset(position, 0)
									}
								}
							}
							else {
								saveScrollPosition()
							}
						}
					}

					if (sharedMediaData[type].loadingAfterFastScroll) {
						if (sharedMediaData[type].messages.size == 0) {
							loadFromStart(type)
						}
						else {
							sharedMediaData[type].loadingAfterFastScroll = false
						}
					}

					scrolling = true
				}
				else if (sharedMediaPreloader != null && sharedMediaData[type].messages.isEmpty() && !sharedMediaData[type].loadingAfterFastScroll) {
					if (fillMediaData(type)) {
						val adapter = when (type) {
							0 -> photoVideoAdapter
							1 -> documentsAdapter
							2 -> voiceAdapter
							3 -> linksAdapter
							4 -> audioAdapter
							5 -> gifAdapter
							else -> null
						}

						if (adapter != null) {
							for (mediaPage in mediaPages) {
								if (mediaPage.listView?.adapter === adapter) {
									mediaPage.listView?.stopScroll()
								}
							}

							adapter.notifyDataSetChanged()
						}

						scrolling = true
					}
				}
			}

			NotificationCenter.messagesDeleted -> {
				val scheduled = args[2] as Boolean

				if (scheduled) {
					return
				}

				var currentChat: Chat? = null

				if (DialogObject.isChatDialog(dialogId)) {
					currentChat = profileActivity.messagesController.getChat(-dialogId)
				}

				val channelId = args[1] as Long
				var loadIndex = 0

				if (ChatObject.isChannel(currentChat)) {
					loadIndex = if (channelId == 0L && mergeDialogId != 0L) {
						1
					}
					else if (channelId == currentChat.id) {
						0
					}
					else {
						return
					}
				}
				else if (channelId != 0L) {
					return
				}

				val markAsDeletedMessages = args[0] as List<Int>
				var updated = false
				var type = -1
				var a = 0
				val n = markAsDeletedMessages.size

				while (a < n) {
					for (b in sharedMediaData.indices) {
						if (sharedMediaData[b].deleteMessage(markAsDeletedMessages[a], loadIndex) != null) {
							type = b
							updated = true
						}
					}

					a++
				}

				if (updated) {
					scrolling = true

					photoVideoAdapter.notifyDataSetChanged()
					documentsAdapter.notifyDataSetChanged()
					voiceAdapter.notifyDataSetChanged()
					linksAdapter.notifyDataSetChanged()
					audioAdapter.notifyDataSetChanged()
					gifAdapter.notifyDataSetChanged()

					if (type == 0 || type == 1 || type == 2 || type == 4) {
						loadFastScrollData(true)
					}
				}
			}

			NotificationCenter.didReceiveNewMessages -> {
				val scheduled = args[2] as Boolean

				if (scheduled) {
					return
				}

				val uid = args[0] as Long

				if (uid == dialogId) {
					val arr = args[1] as List<MessageObject>
					val enc = DialogObject.isEncryptedDialog(dialogId)
					var updated = false

					for (a in arr.indices) {
						val obj = arr[a]

						if (MessageObject.getMedia(obj.messageOwner) == null || obj.needDrawBluredPreview()) {
							continue
						}

						val type = MediaDataController.getMediaType(obj.messageOwner)

						if (type == -1) {
							return
						}

						if (sharedMediaData[type].startReached && sharedMediaData[type].addMessage(obj, if (obj.dialogId == dialogId) 0 else 1, true, enc)) {
							updated = true
							hasMedia[type] = 1
						}
					}

					if (updated) {
						scrolling = true

						for (mediaPage in mediaPages) {
							val adapter = when (mediaPage.selectedType) {
								0 -> photoVideoAdapter
								1 -> documentsAdapter
								2 -> voiceAdapter
								3 -> linksAdapter
								4 -> audioAdapter
								5 -> gifAdapter
								else -> null
							}

							if (adapter != null) {
								photoVideoAdapter.notifyDataSetChanged()
								documentsAdapter.notifyDataSetChanged()
								voiceAdapter.notifyDataSetChanged()
								linksAdapter.notifyDataSetChanged()
								audioAdapter.notifyDataSetChanged()
								gifAdapter.notifyDataSetChanged()
							}
						}

						updateTabs(true)
					}
				}
			}

			NotificationCenter.messageReceivedByServer -> {
				val scheduled = args[6] as Boolean

				if (scheduled) {
					return
				}

				val msgId = args[0] as Int
				val newMsgId = args[1] as Int

				for (sharedMediaDatum in sharedMediaData) {
					sharedMediaDatum.replaceMid(msgId, newMsgId)
				}
			}

			NotificationCenter.messagePlayingDidStart, NotificationCenter.messagePlayingPlayStateChanged, NotificationCenter.messagePlayingDidReset -> {
				if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
					for (mediaPage in mediaPages) {
						mediaPage.listView?.children?.forEach {
							if (it is SharedAudioCell) {
								val messageObject = it.message

								if (messageObject != null) {
									it.updateButtonState(ifSame = false, animated = true)
								}
							}
						}
					}
				}
				else {
					val messageObject = args[0] as MessageObject

					if (messageObject.eventId != 0L) {
						return
					}

					for (mediaPage in mediaPages) {
						mediaPage.listView?.children?.forEach {
							if (it is SharedAudioCell) {
								val messageObject1 = it.message

								if (messageObject1 != null) {
									it.updateButtonState(ifSame = false, animated = true)
								}
							}
						}
					}
				}
			}
		}
	}

	private fun saveScrollPosition() {
		for (mediaPage in mediaPages) {
			val listView = mediaPage.listView

			if (listView != null) {
				var messageId = 0
				var offset = 0

				for (i in 0 until listView.childCount) {
					val child = listView.getChildAt(i)

					if (child is SharedPhotoVideoCell2) {
						messageId = child.messageId
						offset = child.top
					}

					if (child is SharedDocumentCell) {
						messageId = child.message.id
						offset = child.top
					}

					if (child is SharedAudioCell) {
						messageId = child.message?.id ?: 0
						offset = child.top
					}

					if (messageId != 0) {
						break
					}
				}
				if (messageId != 0) {
					var index = -1

					if (mediaPage.selectedType < 0 || mediaPage.selectedType >= sharedMediaData.size) {
						continue
					}

					for (i in sharedMediaData[mediaPage.selectedType].messages.indices) {
						if (messageId == sharedMediaData[mediaPage.selectedType].messages[i].id) {
							index = i
							break
						}
					}

					val position = sharedMediaData[mediaPage.selectedType].startOffset + index

					if (index >= 0) {
						(listView.layoutManager as LinearLayoutManager?)!!.scrollToPositionWithOffset(position, -mediaPage.listView!!.paddingTop + offset)

						if (photoVideoChangeColumnsAnimation) {
							mediaPage.animationSupportingLayoutManager!!.scrollToPositionWithOffset(position, -mediaPage.listView!!.paddingTop + offset)
						}
					}
				}
			}
		}
	}

	private fun animateItemsEnter(finalListView: RecyclerListView?, oldItemCount: Int, addedMessages: SparseBooleanArray?) {
		if (finalListView == null) {
			return
		}

		val progressView = finalListView.children.firstOrNull { it is FlickerLoadingView }

		if (progressView != null) {
			finalListView.removeView(progressView)
		}

		doOnPreDraw {
			val adapter = finalListView.adapter

			if (adapter === photoVideoAdapter || adapter === documentsAdapter || adapter === audioAdapter || adapter === voiceAdapter) {
				if (addedMessages != null) {
					val n = finalListView.childCount

					for (i in 0 until n) {
						val child = finalListView.getChildAt(i)
						val messageId = getMessageId(child)

						if (messageId != 0 && addedMessages[messageId, false]) {
							messageAlphaEnter.put(messageId, 0f)

							val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

							valueAnimator.addUpdateListener {
								messageAlphaEnter.put(messageId, it.animatedValue as Float)
								finalListView.invalidate()
							}

							valueAnimator.addListener(object : AnimatorListenerAdapter() {
								override fun onAnimationEnd(animation: Animator) {
									messageAlphaEnter.remove(messageId)
									finalListView.invalidate()
								}
							})

							val s = min(finalListView.measuredHeight, max(0, child.top))
							val delay = (s / finalListView.measuredHeight.toFloat() * 100).toInt()

							valueAnimator.startDelay = delay.toLong()
							valueAnimator.duration = 250
							valueAnimator.start()
						}

						finalListView.invalidate()
					}
				}
			}
			else {
				val n = finalListView.childCount
				val animatorSet = AnimatorSet()

				for (i in 0 until n) {
					val child = finalListView.getChildAt(i)

					if (child !== progressView && finalListView.getChildAdapterPosition(child) >= oldItemCount - 1) {
						child.alpha = 0f

						val s = min(finalListView.measuredHeight, max(0, child.top))
						val delay = (s / finalListView.measuredHeight.toFloat() * 100).toInt()

						val a = ObjectAnimator.ofFloat(child, ALPHA, 0f, 1f)
						a.startDelay = delay.toLong()
						a.duration = 200

						animatorSet.playTogether(a)
					}

					if (progressView != null && progressView.parent == null) {
						finalListView.addView(progressView)

						val layoutManager = finalListView.layoutManager

						if (layoutManager != null) {
							layoutManager.ignoreView(progressView)

							val animator = ObjectAnimator.ofFloat(progressView, ALPHA, progressView.alpha, 0f)

							animator.addListener(object : AnimatorListenerAdapter() {
								override fun onAnimationEnd(animation: Animator) {
									progressView.alpha = 1f
									layoutManager.stopIgnoringView(progressView)
									finalListView.removeView(progressView)
								}
							})

							animator.start()
						}
					}
				}

				animatorSet.start()
			}
		}
	}

	fun onResume() {
		scrolling = true

		photoVideoAdapter.notifyDataSetChanged()
		documentsAdapter.notifyDataSetChanged()
		linksAdapter.notifyDataSetChanged()

		for (a in mediaPages.indices) {
			fixLayoutInternal(a)
		}
	}

	public override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)

		mediaPages.forEachIndexed { index, mediaPage ->
			mediaPage.listView?.doOnPreDraw {
				fixLayoutInternal(index)
			}
		}
	}

	fun setChatInfo(chatInfo: ChatFull?) {
		info = chatInfo

		if (info != null && info!!.migratedFromChatId != 0L && mergeDialogId == 0L) {
			mergeDialogId = -info!!.migratedFromChatId

			for (sharedMediaDatum in sharedMediaData) {
				sharedMediaDatum.maxId[1] = info!!.migratedFromMaxId
				sharedMediaDatum.endReached[1] = false
			}
		}
	}

	fun setChatUsers(sortedUsers: List<Int>?, chatInfo: ChatFull?) {
		chatUsersAdapter.chatInfo = chatInfo
		chatUsersAdapter.sortedUsers = sortedUsers

		updateTabs(true)

		for (mediaPage in mediaPages) {
			if (mediaPage.selectedType == 7) {
				mediaPage.listView?.adapter?.notifyDataSetChanged()
			}
		}
	}

//	fun updateAdapters() {
//		photoVideoAdapter.notifyDataSetChanged()
//		documentsAdapter.notifyDataSetChanged()
//		voiceAdapter.notifyDataSetChanged()
//		linksAdapter.notifyDataSetChanged()
//		audioAdapter.notifyDataSetChanged()
//		gifAdapter.notifyDataSetChanged()
//	}

	private fun updateRowsSelection() {
		for (mediaPage in mediaPages) {
			val count = mediaPage.listView!!.childCount

			for (a in 0 until count) {
				when (val child = mediaPage.listView?.getChildAt(a)) {
					is SharedDocumentCell -> {
						child.setChecked(false, true)
					}

					is SharedPhotoVideoCell2 -> {
						child.setChecked(false, true)
					}

					is SharedLinkCell -> {
						child.setChecked(checked = false, animated = true)
					}

					is SharedAudioCell -> {
						child.setChecked(checked = false, animated = true)
					}

					is ContextLinkCell -> {
						child.setChecked(checked = false, animated = true)
					}
				}
			}
		}
	}

	private fun updateTabs(animated: Boolean) {
		@Suppress("NAME_SHADOWING") var animated = animated

		if (!delegate.isFragmentOpened) {
			animated = false
		}

		var changed = 0

		if (chatUsersAdapter.chatInfo == null == scrollSlidingTextTabStrip.hasTab(7)) {
			changed++
		}

		if (hasMedia[0] <= 0 == scrollSlidingTextTabStrip.hasTab(0)) {
			changed++
		}

		if (hasMedia[1] <= 0 == scrollSlidingTextTabStrip.hasTab(1)) {
			changed++
		}

		if (!DialogObject.isEncryptedDialog(dialogId)) {
			if (hasMedia[3] <= 0 == scrollSlidingTextTabStrip.hasTab(3)) {
				changed++
			}

			if (hasMedia[4] <= 0 == scrollSlidingTextTabStrip.hasTab(4)) {
				changed++
			}
		}
		else {
			if (hasMedia[4] <= 0 == scrollSlidingTextTabStrip.hasTab(4)) {
				changed++
			}
		}

		if (hasMedia[2] <= 0 == scrollSlidingTextTabStrip.hasTab(2)) {
			changed++
		}

		if (hasMedia[5] <= 0 == scrollSlidingTextTabStrip.hasTab(5)) {
			changed++
		}

		if (hasMedia[6] <= 0 == scrollSlidingTextTabStrip.hasTab(6)) {
			changed++
		}

		if (changed > 0) {
			if (animated) {
				val transitionSet = TransitionSet()
				transitionSet.ordering = TransitionSet.ORDERING_TOGETHER
				transitionSet.addTransition(ChangeBounds())

				transitionSet.addTransition(object : Visibility() {
					override fun onAppear(sceneRoot: ViewGroup?, view: View, startValues: TransitionValues?, endValues: TransitionValues?): Animator {
						val set = AnimatorSet()
						set.playTogether(ObjectAnimator.ofFloat(view, ALPHA, 0f, 1f), ObjectAnimator.ofFloat(view, SCALE_X, 0.5f, 1f), ObjectAnimator.ofFloat(view, SCALE_Y, 0.5f, 1f))
						set.interpolator = CubicBezierInterpolator.DEFAULT
						return set
					}

					override fun onDisappear(sceneRoot: ViewGroup?, view: View, startValues: TransitionValues?, endValues: TransitionValues?): Animator {
						val set = AnimatorSet()
						set.playTogether(ObjectAnimator.ofFloat(view, ALPHA, view.alpha, 0f), ObjectAnimator.ofFloat(view, SCALE_X, view.scaleX, 0.5f), ObjectAnimator.ofFloat(view, SCALE_Y, view.scaleX, 0.5f))
						set.interpolator = CubicBezierInterpolator.DEFAULT
						return set
					}
				})

				transitionSet.duration = 200

				TransitionManager.beginDelayedTransition(scrollSlidingTextTabStrip.getTabsContainer(), transitionSet)

				scrollSlidingTextTabStrip.recordIndicatorParams()
			}

			var idToView: SparseArray<View>? = scrollSlidingTextTabStrip.removeTabs()

			if (changed > 3) {
				idToView = null
			}

			if (chatUsersAdapter.chatInfo != null) {
				if (!scrollSlidingTextTabStrip.hasTab(7)) {
					scrollSlidingTextTabStrip.addTextTab(7, context.getString(R.string.GroupMembers), idToView)
				}
			}

			if (hasMedia[0] > 0) {
				if (!scrollSlidingTextTabStrip.hasTab(0)) {
					if (hasMedia[1] == 0 && hasMedia[2] == 0 && hasMedia[3] == 0 && hasMedia[4] == 0 && hasMedia[5] == 0 && hasMedia[6] == 0 && chatUsersAdapter.chatInfo == null) {
						scrollSlidingTextTabStrip.addTextTab(0, context.getString(R.string.SharedMediaTabFull2), idToView)
					}
					else {
						scrollSlidingTextTabStrip.addTextTab(0, context.getString(R.string.SharedMediaTab2), idToView)
					}
				}
			}

			if (hasMedia[1] > 0) {
				if (!scrollSlidingTextTabStrip.hasTab(1)) {
					scrollSlidingTextTabStrip.addTextTab(1, context.getString(R.string.SharedFilesTab2), idToView)
				}
			}

			if (!DialogObject.isEncryptedDialog(dialogId)) {
				if (hasMedia[3] > 0) {
					if (!scrollSlidingTextTabStrip.hasTab(3)) {
						scrollSlidingTextTabStrip.addTextTab(3, context.getString(R.string.SharedLinksTab2), idToView)
					}
				}
				if (hasMedia[4] > 0) {
					if (!scrollSlidingTextTabStrip.hasTab(4)) {
						scrollSlidingTextTabStrip.addTextTab(4, context.getString(R.string.SharedMusicTab2), idToView)
					}
				}
			}
			else {
				if (hasMedia[4] > 0) {
					if (!scrollSlidingTextTabStrip.hasTab(4)) {
						scrollSlidingTextTabStrip.addTextTab(4, context.getString(R.string.SharedMusicTab2), idToView)
					}
				}
			}

			if (hasMedia[2] > 0) {
				if (!scrollSlidingTextTabStrip.hasTab(2)) {
					scrollSlidingTextTabStrip.addTextTab(2, context.getString(R.string.SharedVoiceTab2), idToView)
				}
			}

			if (hasMedia[5] > 0) {
				if (!scrollSlidingTextTabStrip.hasTab(5)) {
					scrollSlidingTextTabStrip.addTextTab(5, context.getString(R.string.SharedGIFsTab2), idToView)
				}
			}

			if (hasMedia[6] > 0) {
				if (!scrollSlidingTextTabStrip.hasTab(6)) {
					scrollSlidingTextTabStrip.addTextTab(6, context.getString(R.string.SharedGroupsTab2), idToView)
				}
			}
		}

		val id = scrollSlidingTextTabStrip.currentTabId

		if (id >= 0) {
			mediaPages[0].selectedType = id
		}

		scrollSlidingTextTabStrip.finishAddingTabs()
	}

	private fun startStopVisibleGifs() {
		for (b in mediaPages.indices) {
			val count = mediaPages[b].listView!!.childCount

			for (a in 0 until count) {
				val child = mediaPages[b].listView?.getChildAt(a)

				if (child is ContextLinkCell) {
					val imageReceiver = child.photoImage

					if (b == 0) {
						imageReceiver.allowStartAnimation = true
						imageReceiver.startAnimation()
					}
					else {
						imageReceiver.allowStartAnimation = false
						imageReceiver.stopAnimation()
					}
				}
			}
		}
	}

	private fun switchToCurrentSelectedMode(animated: Boolean) {
		for (mediaPage in mediaPages) {
			mediaPage.listView?.stopScroll()
		}

		val a = if (animated) 1 else 0
		val layoutParams = mediaPages[a].layoutParams as LayoutParams
		// layoutParams.leftMargin = layoutParams.rightMargin = 0;
		var fastScrollVisible = false
		var spanCount = 100
		val currentAdapter = mediaPages[a].listView!!.adapter
		var viewPool: RecycledViewPool? = null

		if (searching && searchWas) {
			if (animated) {
				if (mediaPages[a].selectedType == 0 || mediaPages[a].selectedType == 2 || mediaPages[a].selectedType == 5 || mediaPages[a].selectedType == 6 || mediaPages[a].selectedType == 7 && !delegate.canSearchMembers()) {
					searching = false
					searchWas = false
					switchToCurrentSelectedMode(true)
					return
				}
				else {
					val text = searchItem?.searchField?.text?.toString()

					if (mediaPages[a].selectedType == 1) {
						documentsSearchAdapter.search(text, false)

						if (currentAdapter !== documentsSearchAdapter) {
							recycleAdapter(currentAdapter)
							mediaPages[a].listView?.adapter = documentsSearchAdapter
						}
					}
					else if (mediaPages[a].selectedType == 3) {
						linksSearchAdapter.search(text, false)

						if (currentAdapter !== linksSearchAdapter) {
							recycleAdapter(currentAdapter)
							mediaPages[a].listView?.adapter = linksSearchAdapter
						}
					}
					else if (mediaPages[a].selectedType == 4) {
						audioSearchAdapter.search(text, false)

						if (currentAdapter !== audioSearchAdapter) {
							recycleAdapter(currentAdapter)
							mediaPages[a].listView?.adapter = audioSearchAdapter
						}
					}
					else if (mediaPages[a].selectedType == 7) {
						groupUsersSearchAdapter.search(text, false)

						if (currentAdapter !== groupUsersSearchAdapter) {
							recycleAdapter(currentAdapter)
							mediaPages[a].listView?.adapter = groupUsersSearchAdapter
						}
					}
				}
			}
			else {
				if (mediaPages[a].listView != null) {
					when (mediaPages[a].selectedType) {
						1 -> {
							if (currentAdapter !== documentsSearchAdapter) {
								recycleAdapter(currentAdapter)
								mediaPages[a].listView?.adapter = documentsSearchAdapter
							}

							documentsSearchAdapter.notifyDataSetChanged()
						}

						3 -> {
							if (currentAdapter !== linksSearchAdapter) {
								recycleAdapter(currentAdapter)
								mediaPages[a].listView?.adapter = linksSearchAdapter
							}

							linksSearchAdapter.notifyDataSetChanged()
						}

						4 -> {
							if (currentAdapter !== audioSearchAdapter) {
								recycleAdapter(currentAdapter)
								mediaPages[a].listView?.adapter = audioSearchAdapter
							}

							audioSearchAdapter.notifyDataSetChanged()
						}

						7 -> {
							if (currentAdapter !== groupUsersSearchAdapter) {
								recycleAdapter(currentAdapter)
								mediaPages[a].listView?.adapter = groupUsersSearchAdapter
							}

							groupUsersSearchAdapter.notifyDataSetChanged()
						}
					}
				}
			}
		}
		else {
			mediaPages[a].listView?.setPinnedHeaderShadowDrawable(null)

			if (mediaPages[a].selectedType == 0) {
				if (currentAdapter !== photoVideoAdapter) {
					recycleAdapter(currentAdapter)
					mediaPages[a].listView?.adapter = photoVideoAdapter
				}

				layoutParams.rightMargin = -AndroidUtilities.dp(1f)
				layoutParams.leftMargin = layoutParams.rightMargin

				if (sharedMediaData[0].fastScrollDataLoaded && sharedMediaData[0].fastScrollPeriods.isNotEmpty()) {
					fastScrollVisible = true
				}

				spanCount = mediaColumnsCount

				mediaPages[a].listView?.setPinnedHeaderShadowDrawable(pinnedHeaderShadowDrawable)

				viewPool = sharedMediaData[0].recycledViewPool
			}
			else if (mediaPages[a].selectedType == 1) {
				if (sharedMediaData[1].fastScrollDataLoaded && sharedMediaData[1].fastScrollPeriods.isNotEmpty()) {
					fastScrollVisible = true
				}

				if (currentAdapter !== documentsAdapter) {
					recycleAdapter(currentAdapter)
					mediaPages[a].listView?.adapter = documentsAdapter
				}
			}
			else if (mediaPages[a].selectedType == 2) {
				if (sharedMediaData[2].fastScrollDataLoaded && sharedMediaData[2].fastScrollPeriods.isNotEmpty()) {
					fastScrollVisible = true
				}

				if (currentAdapter !== voiceAdapter) {
					recycleAdapter(currentAdapter)
					mediaPages[a].listView?.adapter = voiceAdapter
				}
			}
			else if (mediaPages[a].selectedType == 3) {
				if (currentAdapter !== linksAdapter) {
					recycleAdapter(currentAdapter)
					mediaPages[a].listView?.adapter = linksAdapter
				}
			}
			else if (mediaPages[a].selectedType == 4) {
				if (sharedMediaData[4].fastScrollDataLoaded && sharedMediaData[4].fastScrollPeriods.isNotEmpty()) {
					fastScrollVisible = true
				}

				if (currentAdapter !== audioAdapter) {
					recycleAdapter(currentAdapter)
					mediaPages[a].listView?.adapter = audioAdapter
				}
			}
			else if (mediaPages[a].selectedType == 5) {
				if (currentAdapter !== gifAdapter) {
					recycleAdapter(currentAdapter)
					mediaPages[a].listView?.adapter = gifAdapter
				}
			}
			else if (mediaPages[a].selectedType == 6) {
				if (currentAdapter !== commonGroupsAdapter) {
					recycleAdapter(currentAdapter)
					mediaPages[a].listView?.adapter = commonGroupsAdapter
				}
			}
			else if (mediaPages[a].selectedType == 7) {
				if (currentAdapter !== chatUsersAdapter) {
					recycleAdapter(currentAdapter)
					mediaPages[a].listView?.adapter = chatUsersAdapter
				}
			}

			if (mediaPages[a].selectedType == 0 || mediaPages[a].selectedType == 2 || mediaPages[a].selectedType == 5 || mediaPages[a].selectedType == 6 || mediaPages[a].selectedType == 7 && !delegate.canSearchMembers()) {
				if (animated) {
					searchItemState = 2
				}
				else {
					searchItemState = 0
					searchItem?.invisible()
				}
			}
			else {
				if (animated) {
					if (searchItem!!.isInvisible && !actionBar!!.isSearchFieldVisible) {
						if (canShowSearchItem()) {
							searchItemState = 1
							searchItem?.visible()
						}
						else {
							searchItem?.invisible()
						}

						searchItem?.alpha = 0.0f
					}
					else {
						searchItemState = 0
					}
				}
				else if (searchItem?.visibility == INVISIBLE) {
					if (canShowSearchItem()) {
						searchItemState = 0
						searchItem?.alpha = 1.0f
						searchItem?.visible()
					}
					else {
						searchItem?.invisible()
						searchItem?.alpha = 0.0f
					}
				}
			}

			if (mediaPages[a].selectedType == 6) {
				if (!commonGroupsAdapter.loading && !commonGroupsAdapter.endReached && commonGroupsAdapter.chats.isEmpty()) {
					commonGroupsAdapter.getChats(0, 100)
				}
			}
			else if (mediaPages[a].selectedType == 7) {
				// unused
			}
			else {
				if (!sharedMediaData[mediaPages[a].selectedType].loading && !sharedMediaData[mediaPages[a].selectedType].endReached[0] && sharedMediaData[mediaPages[a].selectedType].messages.isEmpty()) {
					sharedMediaData[mediaPages[a].selectedType].loading = true

					documentsAdapter.notifyDataSetChanged()

					var type = mediaPages[a].selectedType

					if (type == 0) {
						if (sharedMediaData[0].filterType == FILTER_PHOTOS_ONLY) {
							type = MediaDataController.MEDIA_PHOTOS_ONLY
						}
						else if (sharedMediaData[0].filterType == FILTER_VIDEOS_ONLY) {
							type = MediaDataController.MEDIA_VIDEOS_ONLY
						}
					}

					profileActivity.mediaDataController.loadMedia(dialogId, 50, 0, 0, type, 1, profileActivity.classGuid, sharedMediaData[mediaPages[a].selectedType].requestIndex)
				}
			}

			mediaPages[a].listView!!.visibility = VISIBLE
		}

		mediaPages[a].fastScrollEnabled = fastScrollVisible

		updateFastScrollVisibility(mediaPages[a], false)

		mediaPages[a].layoutManager!!.spanCount = spanCount
		mediaPages[a].listView!!.setRecycledViewPool(viewPool)
		mediaPages[a].animationSupportingListView!!.setRecycledViewPool(viewPool)

		if (searchItemState == 2 && actionBar?.isSearchFieldVisible == true) {
			ignoreSearchCollapse = true
			actionBar.closeSearchField()
			searchItemState = 0
			searchItem?.alpha = 0.0f
			searchItem?.invisible()
		}
	}

	private fun onItemLongClick(item: MessageObject?, view: View, a: Int): Boolean {
		if (isActionModeShowed || profileActivity.parentActivity == null || item == null) {
			return false
		}

		profileActivity.parentActivity?.currentFocus?.let {
			AndroidUtilities.hideKeyboard(it)
		}

		selectedFiles[if (item.dialogId == dialogId) 0 else 1].put(item.id, item)

		if (!item.canDeleteMessage(false, null)) {
			cantDeleteMessagesCount++
		}

		deleteItem.visibility = if (cantDeleteMessagesCount == 0) VISIBLE else GONE

		gotoItem?.visible()

		selectedMessagesCountTextView.setNumber(1, false)
		val animatorSet = AnimatorSet()
		val animators = mutableListOf<Animator>()

		for (i in actionModeViews.indices) {
			val view2 = actionModeViews[i]

			AndroidUtilities.clearDrawableAnimation(view2)

			animators.add(ObjectAnimator.ofFloat(view2, SCALE_Y, 0.1f, 1.0f))
		}

		animatorSet.playTogether(animators)
		animatorSet.duration = 250
		animatorSet.start()

		scrolling = false

		when (view) {
			is SharedDocumentCell -> {
				view.setChecked(true, true)
			}

			is SharedPhotoVideoCell -> {
				view.setChecked(a, true, true)
			}

			is SharedLinkCell -> {
				view.setChecked(checked = true, animated = true)
			}

			is SharedAudioCell -> {
				view.setChecked(checked = true, animated = true)
			}

			is ContextLinkCell -> {
				view.setChecked(checked = true, animated = true)
			}

			is SharedPhotoVideoCell2 -> {
				view.setChecked(true, true)
			}
		}

		if (!isActionModeShowed) {
			showActionMode(true)
		}

		updateForwardItem()

		return true
	}

	private fun onItemClick(index: Int, view: View, message: MessageObject?, a: Int, selectedMode: Int) {
		@Suppress("NAME_SHADOWING") var index = index

		if (message == null || photoVideoChangeColumnsAnimation) {
			return
		}

		if (isActionModeShowed) {
			val loadIndex = if (message.dialogId == dialogId) 0 else 1

			if (selectedFiles[loadIndex].indexOfKey(message.id) >= 0) {
				selectedFiles[loadIndex].remove(message.id)

				if (!message.canDeleteMessage(false, null)) {
					cantDeleteMessagesCount--
				}
			}
			else {
				if (selectedFiles[0].size() + selectedFiles[1].size() >= 100) {
					return
				}

				selectedFiles[loadIndex].put(message.id, message)

				if (!message.canDeleteMessage(false, null)) {
					cantDeleteMessagesCount++
				}
			}

			if (selectedFiles[0].isEmpty() && selectedFiles[1].isEmpty()) {
				showActionMode(false)
			}
			else {
				selectedMessagesCountTextView.setNumber(selectedFiles[0].size() + selectedFiles[1].size(), true)
				deleteItem.visibility = if (cantDeleteMessagesCount == 0) VISIBLE else GONE
				gotoItem?.visibility = if (selectedFiles[0].size() == 1) VISIBLE else GONE
			}

			scrolling = false

			when (view) {
				is SharedDocumentCell -> {
					view.setChecked(selectedFiles[loadIndex].indexOfKey(message.id) >= 0, true)
				}

				is SharedPhotoVideoCell -> {
					view.setChecked(a, selectedFiles[loadIndex].indexOfKey(message.id) >= 0, true)
				}

				is SharedLinkCell -> {
					view.setChecked(selectedFiles[loadIndex].indexOfKey(message.id) >= 0, true)
				}

				is SharedAudioCell -> {
					view.setChecked(selectedFiles[loadIndex].indexOfKey(message.id) >= 0, true)
				}

				is ContextLinkCell -> {
					view.setChecked(selectedFiles[loadIndex].indexOfKey(message.id) >= 0, true)
				}

				is SharedPhotoVideoCell2 -> {
					view.setChecked(selectedFiles[loadIndex].indexOfKey(message.id) >= 0, true)
				}
			}
		}
		else {
			if (selectedMode == 0) {
				val i = index - sharedMediaData[selectedMode].startOffset

				if (i >= 0 && i < sharedMediaData[selectedMode].messages.size) {
					PhotoViewer.getInstance().setParentActivity(profileActivity)
					PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, i, dialogId, mergeDialogId, provider)
				}
			}
			else if (selectedMode == 2 || selectedMode == 4) {
				if (view is SharedAudioCell) {
					view.didPressedButton()
				}
			}
			else if (selectedMode == 5) {
				PhotoViewer.getInstance().setParentActivity(profileActivity)

				index = sharedMediaData[selectedMode].messages.indexOf(message)

				if (index < 0) {
					PhotoViewer.getInstance().openPhoto(listOf(message), 0, 0, 0, provider)
				}
				else {
					PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, index, dialogId, mergeDialogId, provider)
				}
			}
			else if (selectedMode == 1) {
				if (view is SharedDocumentCell) {
					val document = message.document

					if (view.isLoaded) {
						if (message.canPreviewDocument()) {
							PhotoViewer.getInstance().setParentActivity(profileActivity)

							index = sharedMediaData[selectedMode].messages.indexOf(message)

							if (index < 0) {
								PhotoViewer.getInstance().openPhoto(listOf(message), 0, 0, 0, provider)
							}
							else {
								PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, index, dialogId, mergeDialogId, provider)
							}

							return
						}

						AndroidUtilities.openDocument(message, profileActivity.parentActivity, profileActivity)
					}
					else if (!view.isLoading) {
						val messageObject = view.message
						messageObject.putInDownloadsStore = true
						profileActivity.fileLoader.loadFile(document, messageObject, FileLoader.PRIORITY_LOW, 0)
						view.updateFileExistIcon(true)
					}
					else {
						profileActivity.fileLoader.cancelLoadFile(document)
						view.updateFileExistIcon(true)
					}
				}
			}
			else if (selectedMode == 3) {
				try {
					val webPage = if (MessageObject.getMedia(message.messageOwner) != null) MessageObject.getMedia(message.messageOwner)?.webpage else null
					var link: String? = null

					if (webPage != null && webPage !is TLWebPageEmpty) {
						link = if (webPage.cachedPage != null) {
							profileActivity.parentActivity?.let {
								ArticleViewer.getInstance().setParentActivity(it, profileActivity)
								ArticleViewer.getInstance().open(message)
							}

							return
						}
						else if (!webPage.embedUrl.isNullOrEmpty()) {
							openWebView(webPage, message)
							return
						}
						else {
							webPage.url
						}
					}

					if (link == null) {
						link = (view as SharedLinkCell).getLink(0)
					}

					link?.let {
						openUrl(it)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		updateForwardItem()
	}

	private fun openUrl(link: String) {
		if (AndroidUtilities.shouldShowUrlInAlert(link)) {
			AlertsCreator.showOpenUrlAlert(profileActivity, link, punycode = true, ask = true)
		}
		else {
			Browser.openUrl(profileActivity.parentActivity, link)
		}
	}

	private fun openWebView(webPage: WebPage, message: MessageObject) {
		EmbedBottomSheet.show(profileActivity, message, provider, webPage.siteName, webPage.description, webPage.url, webPage.embedUrl, webPage.embedWidth, webPage.embedHeight, false)
	}

	private fun recycleAdapter(adapter: RecyclerView.Adapter<*>?) {
		if (adapter is SharedPhotoVideoAdapter) {
			cellCache.addAll(cache)
			cache.clear()
		}
		else if (adapter === audioAdapter) {
			audioCellCache.addAll(audioCache)
			audioCache.clear()
		}
	}

	private fun fixLayoutInternal(num: Int) {
		if (num == 0) {
			if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
				selectedMessagesCountTextView.setTextSize(18)
			}
			else {
				selectedMessagesCountTextView.setTextSize(20)
			}
		}

		if (num == 0) {
			photoVideoAdapter.notifyDataSetChanged()
		}
	}

	private fun findPeriodAndJumpToDate(type: Int, listView: RecyclerListView?, scrollToPosition: Boolean) {
		val periods = sharedMediaData[type].fastScrollPeriods
		var period: Period? = null
		val position = (listView!!.layoutManager as LinearLayoutManager?)!!.findFirstVisibleItemPosition()

		if (position >= 0) {
			if (periods != null) {
				for (i in periods.indices) {
					if (position <= periods[i].startOffset) {
						period = periods[i]
						break
					}
				}

				if (period == null) {
					period = periods[periods.size - 1]
				}
			}

			if (period != null) {
				jumpToDate(type, period.maxId, period.startOffset + 1, scrollToPosition)
				return
			}
		}
	}

	private fun jumpToDate(type: Int, messageId: Int, startOffset: Int, scrollToPosition: Boolean) {
		sharedMediaData[type].messages.clear()
		sharedMediaData[type].messagesDict[0].clear()
		sharedMediaData[type].messagesDict[1].clear()
		sharedMediaData[type].setMaxId(0, messageId)
		sharedMediaData[type].setEndReached(0, false)
		sharedMediaData[type].startReached = false
		sharedMediaData[type].startOffset = startOffset
		sharedMediaData[type].endLoadingStubs = sharedMediaData[type].totalCount - startOffset - 1

		if (sharedMediaData[type].endLoadingStubs < 0) {
			sharedMediaData[type].endLoadingStubs = 0
		}

		sharedMediaData[type].minId = messageId
		sharedMediaData[type].loadingAfterFastScroll = true
		sharedMediaData[type].loading = false
		sharedMediaData[type].requestIndex++

		getMediaPage(type)?.listView?.adapter?.notifyDataSetChanged()

		if (scrollToPosition) {
			for (page in mediaPages) {
				if (page.selectedType == type) {
					page.layoutManager?.scrollToPositionWithOffset(min(sharedMediaData[type].totalCount - 1, sharedMediaData[type].startOffset), 0)
				}
			}
		}
	}

	private fun getNextMediaColumnsCount(mediaColumnsCount: Int, up: Boolean): Int {
		var newColumnsCount = mediaColumnsCount

		if (!up) {
			when (mediaColumnsCount) {
				2 -> newColumnsCount = 3
				3 -> newColumnsCount = 4
				4 -> newColumnsCount = 5
				5 -> newColumnsCount = 6
				6 -> newColumnsCount = 9
			}
		}
		else {
			when (mediaColumnsCount) {
				9 -> newColumnsCount = 6
				6 -> newColumnsCount = 5
				5 -> newColumnsCount = 4
				4 -> newColumnsCount = 3
				3 -> newColumnsCount = 2
			}
		}

		return newColumnsCount
	}

	override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
		if (child === fragmentContextView) {
			canvas.save()
			canvas.clipRect(0, mediaPages[0].top, child.getMeasuredWidth(), mediaPages[0].top + child.getMeasuredHeight() + AndroidUtilities.dp(12f))
			val b = super.drawChild(canvas, child, drawingTime)
			canvas.restore()
			return b
		}

		return super.drawChild(canvas, child, drawingTime)
	}

	fun interface SharedMediaPreloaderDelegate {
		fun mediaCountUpdated()
	}

	interface Delegate {
		fun scrollToSharedMedia()
		fun onMemberClick(participant: ChatParticipant?, b: Boolean, resultOnly: Boolean): Boolean
		val currentChat: Chat?
		val isFragmentOpened: Boolean
		val listView: RecyclerListView?
		fun canSearchMembers(): Boolean
		fun updateSelectedMediaTabText()
	}

	private inner class SharedLinksAdapter(private val mContext: Context) : SectionsAdapter() {
		override fun getItem(section: Int, position: Int): Any? {
			return null
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder, section: Int, row: Int): Boolean {
			return if (sharedMediaData[3].sections.size == 0 && !sharedMediaData[3].loading) {
				false
			}
			else {
				section == 0 || row != 0
			}
		}

		override fun getSectionCount(): Int {
			return if (sharedMediaData[3].sections.size == 0 && !sharedMediaData[3].loading) {
				1
			}
			else {
				sharedMediaData[3].sections.size + if (sharedMediaData[3].sections.isEmpty() || sharedMediaData[3].endReached[0] && sharedMediaData[3].endReached[1]) 0 else 1
			}
		}

		override fun getCountForSection(section: Int): Int {
			if (sharedMediaData[3].sections.size == 0 && !sharedMediaData[3].loading) {
				return 1
			}

			return if (section < sharedMediaData[3].sections.size) {
				sharedMediaData[3].sectionArrays[sharedMediaData[3].sections[section]]!!.size + if (section != 0) 1 else 0
			}
			else {
				1
			}
		}

		override fun getSectionHeaderView(section: Int, view: View?): View {
			@Suppress("NAME_SHADOWING") var view: View? = view

			if (view == null) {
				view = GraySectionCell(mContext)
				view.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))
			}

			if (section == 0) {
				view.alpha = 0.0f
			}
			else if (section < sharedMediaData[3].sections.size) {
				view.alpha = 1.0f
				val name = sharedMediaData[3].sections[section]
				val messageObjects = sharedMediaData[3].sectionArrays[name]!!
				val messageObject = messageObjects[0]
				(view as GraySectionCell).setText(LocaleController.formatSectionDate(messageObject.messageOwner!!.date.toLong()))
			}

			return view
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = GraySectionCell(mContext)
				}

				1 -> {
					view = SharedLinkCell(mContext, SharedLinkCell.VIEW_TYPE_DEFAULT)
					view.setDelegate(sharedLinkCellDelegate)
				}

				3 -> {
					val emptyStubView = createEmptyStubView(mContext, 3, dialogId)
					emptyStubView.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
					return RecyclerListView.Holder(emptyStubView)
				}

				2 -> {
					val flickerLoadingView = FlickerLoadingView(mContext)
					flickerLoadingView.setIsSingleCell(true)
					flickerLoadingView.showDate(false)
					flickerLoadingView.setViewType(FlickerLoadingView.LINKS_TYPE)
					view = flickerLoadingView
				}

				else -> {
					val flickerLoadingView = FlickerLoadingView(mContext)
					flickerLoadingView.setIsSingleCell(true)
					flickerLoadingView.showDate(false)
					flickerLoadingView.setViewType(FlickerLoadingView.LINKS_TYPE)
					view = flickerLoadingView
				}
			}

			view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(section: Int, position: Int, holder: RecyclerView.ViewHolder) {
			@Suppress("NAME_SHADOWING") var position = position

			if (holder.itemViewType != 2 && holder.itemViewType != 3) {
				val name = sharedMediaData[3].sections[section]
				val messageObjects = sharedMediaData[3].sectionArrays[name]!!

				when (holder.itemViewType) {
					0 -> {
						val messageObject = messageObjects[0]
						(holder.itemView as GraySectionCell).setText(LocaleController.formatSectionDate(messageObject.messageOwner!!.date.toLong()))
					}

					1 -> {
						if (section != 0) {
							position--
						}

						val sharedLinkCell = holder.itemView as SharedLinkCell
						val messageObject = messageObjects[position]

						sharedLinkCell.setLink(messageObject, position != messageObjects.size - 1 || section == sharedMediaData[3].sections.size - 1 && sharedMediaData[3].loading)

						if (isActionModeShowed) {
							sharedLinkCell.setChecked(selectedFiles[if (messageObject.dialogId == dialogId) 0 else 1].indexOfKey(messageObject.id) >= 0, !scrolling)
						}
						else {
							sharedLinkCell.setChecked(false, !scrolling)
						}
					}
				}
			}
		}

		override fun getItemViewType(section: Int, position: Int): Int {
			if (sharedMediaData[3].sections.size == 0 && !sharedMediaData[3].loading) {
				return 3
			}
			return if (section < sharedMediaData[3].sections.size) {
				if (section != 0 && position == 0) {
					0
				}
				else {
					1
				}
			}
			else {
				2
			}
		}

		override fun getLetter(position: Int): String? {
			return null
		}

		override fun getPositionForScrollProgress(listView: RecyclerListView, progress: Float, position: IntArray) {
			position[0] = 0
			position[1] = 0
		}
	}

	private inner class SharedDocumentsAdapter(private val mContext: Context, private val currentType: Int) : FastScrollAdapter() {
		private var inFastScrollMode = false

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return true
		}

		override fun getItemCount(): Int {
			if (sharedMediaData[currentType].loadingAfterFastScroll) {
				return sharedMediaData[currentType].totalCount
			}

			if (sharedMediaData[currentType].messages.size == 0 && !sharedMediaData[currentType].loading) {
				return 1
			}

			if (sharedMediaData[currentType].messages.size == 0 && (!sharedMediaData[currentType].endReached[0] || !sharedMediaData[currentType].endReached[1]) && sharedMediaData[currentType].startReached) {
				return 0
			}

			return if (sharedMediaData[currentType].totalCount == 0) {
				var count = sharedMediaData[currentType].startOffset + sharedMediaData[currentType].messages.size

				if (count != 0 && (!sharedMediaData[currentType].endReached[0] || !sharedMediaData[currentType].endReached[1])) {
					if (sharedMediaData[currentType].endLoadingStubs != 0) {
						count += sharedMediaData[currentType].endLoadingStubs
					}
					else {
						count++
					}
				}

				count
			}
			else {
				sharedMediaData[currentType].totalCount
			}
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				1 -> {
					val cell = SharedDocumentCell(mContext, SharedDocumentCell.VIEW_TYPE_DEFAULT)
					cell.setGlobalGradientView(globalGradientView)
					view = cell
				}

				2 -> {
					val flickerLoadingView = FlickerLoadingView(mContext)

					view = flickerLoadingView

					if (currentType == 2) {
						flickerLoadingView.setViewType(FlickerLoadingView.AUDIO_TYPE)
					}
					else {
						flickerLoadingView.setViewType(FlickerLoadingView.FILES_TYPE)
					}

					flickerLoadingView.showDate(false)
					flickerLoadingView.setIsSingleCell(true)
					flickerLoadingView.setGlobalGradientView(globalGradientView)
				}

				4 -> {
					val emptyStubView = createEmptyStubView(mContext, currentType, dialogId)
					emptyStubView.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
					return RecyclerListView.Holder(emptyStubView)
				}

				3 -> {
					if (currentType == MediaDataController.MEDIA_MUSIC && audioCellCache.isNotEmpty()) {
						view = audioCellCache[0]
						audioCellCache.removeAt(0)
						(view.parent as? ViewGroup)?.removeView(view)
					}
					else {
						view = object : SharedAudioCell(mContext, VIEW_TYPE_DEFAULT) {
							public override fun needPlayMessage(messageObject: MessageObject): Boolean {
								if (messageObject.isVoice || messageObject.isRoundVideo) {
									val result = MediaController.getInstance().playMessage(messageObject)
									MediaController.getInstance().setVoiceMessagesPlaylist(if (result) sharedMediaData[currentType].messages else null, false)
									return result
								}
								else if (messageObject.isMusic) {
									return MediaController.getInstance().setPlaylist(sharedMediaData[currentType].messages, messageObject, mergeDialogId)
								}
								return false
							}
						}
					}

					val audioCell = view as SharedAudioCell
					audioCell.globalGradientView = globalGradientView

					if (currentType == MediaDataController.MEDIA_MUSIC) {
						audioCache.add(view)
					}
				}

				else -> {
					if (currentType == MediaDataController.MEDIA_MUSIC && audioCellCache.isNotEmpty()) {
						view = audioCellCache[0]
						audioCellCache.removeAt(0)
						(view.parent as? ViewGroup)?.removeView(view)
					}
					else {
						view = object : SharedAudioCell(mContext, VIEW_TYPE_DEFAULT) {
							public override fun needPlayMessage(messageObject: MessageObject): Boolean {
								if (messageObject.isVoice || messageObject.isRoundVideo) {
									val result = MediaController.getInstance().playMessage(messageObject)
									MediaController.getInstance().setVoiceMessagesPlaylist(if (result) sharedMediaData[currentType].messages else null, false)
									return result
								}
								else if (messageObject.isMusic) {
									return MediaController.getInstance().setPlaylist(sharedMediaData[currentType].messages, messageObject, mergeDialogId)
								}

								return false
							}
						}
					}

					val audioCell = view as SharedAudioCell
					audioCell.globalGradientView = globalGradientView

					if (currentType == MediaDataController.MEDIA_MUSIC) {
						audioCache.add(view)
					}
				}
			}

			view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val messageObjects = sharedMediaData[currentType].messages

			when (holder.itemViewType) {
				1 -> {
					val sharedDocumentCell = holder.itemView as SharedDocumentCell
					val messageObject = messageObjects[position - sharedMediaData[currentType].startOffset]

					sharedDocumentCell.setDocument(messageObject, position != messageObjects.size - 1)

					if (isActionModeShowed) {
						sharedDocumentCell.setChecked(selectedFiles[if (messageObject.dialogId == dialogId) 0 else 1].indexOfKey(messageObject.id) >= 0, !scrolling)
					}
					else {
						sharedDocumentCell.setChecked(false, !scrolling)
					}
				}

				3 -> {
					val sharedAudioCell = holder.itemView as SharedAudioCell
					val messageObject = messageObjects[position - sharedMediaData[currentType].startOffset]

					sharedAudioCell.setMessageObject(messageObject, position != messageObjects.size - 1)

					if (isActionModeShowed) {
						sharedAudioCell.setChecked(selectedFiles[if (messageObject.dialogId == dialogId) 0 else 1].indexOfKey(messageObject.id) >= 0, !scrolling)
					}
					else {
						sharedAudioCell.setChecked(false, !scrolling)
					}
				}
			}
		}

		override fun getItemViewType(position: Int): Int {
			if (sharedMediaData[currentType].sections.size == 0 && !sharedMediaData[currentType].loading) {
				return 4
			}

			return if (position >= sharedMediaData[currentType].startOffset && position < sharedMediaData[currentType].startOffset + sharedMediaData[currentType].messages.size) {
				if (currentType == 2 || currentType == 4) {
					3
				}
				else {
					1
				}
			}
			else {
				2
			}
		}

		override fun getLetter(position: Int): String {
			val periods = sharedMediaData[currentType].fastScrollPeriods

			if (periods.isNotEmpty()) {
				for (i in periods.indices) {
					if (position <= periods[i].startOffset) {
						return periods[i].formattedDate
					}
				}

				return periods[periods.size - 1].formattedDate
			}

			return ""
		}

		override fun getPositionForScrollProgress(listView: RecyclerListView, progress: Float, position: IntArray) {
			val viewHeight = listView.getChildAt(0).measuredHeight
			val totalHeight = totalItemsCount * viewHeight
			val listViewHeight = listView.measuredHeight - listView.paddingTop
			position[0] = (progress * (totalHeight - listViewHeight) / viewHeight).toInt()
			position[1] = (progress * (totalHeight - listViewHeight)).toInt() % viewHeight
		}

		override fun onStartFastScroll() {
			inFastScrollMode = true
			val mediaPage = getMediaPage(currentType) ?: return
			showFastScrollHint(mediaPage, null, false)
		}

		override fun onFinishFastScroll(listView: RecyclerListView?) {
			if (inFastScrollMode) {
				inFastScrollMode = false

				if (listView != null) {
					var messageId = 0

					for (i in 0 until listView.childCount) {
						val child = listView.getChildAt(i)

						messageId = getMessageId(child)

						if (messageId != 0) {
							break
						}
					}

					if (messageId == 0) {
						findPeriodAndJumpToDate(currentType, listView, true)
					}
				}
			}
		}

		override val totalItemsCount: Int
			get() = sharedMediaData[currentType].totalCount
	}

	private open inner class SharedPhotoVideoAdapter(private val mContext: Context) : FastScrollAdapter() {
		private var inFastScrollMode = false
		var sharedResources: SharedResources? = null

		fun getPositionForIndex(i: Int): Int {
			return sharedMediaData[0].startOffset + i
		}

		override fun getItemCount(): Int {
			if (DialogObject.isEncryptedDialog(dialogId)) {
				if (sharedMediaData[0].messages.size == 0 && !sharedMediaData[0].loading) {
					return 1
				}

				if (sharedMediaData[0].messages.size == 0 && (!sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1])) {
					return 0
				}

				var count = sharedMediaData[0].startOffset + sharedMediaData[0].messages.size

				if (count != 0 && (!sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1])) {
					count++
				}

				return count
			}

			if (sharedMediaData[0].loadingAfterFastScroll) {
				return sharedMediaData[0].totalCount
			}

			if (sharedMediaData[0].messages.size == 0 && !sharedMediaData[0].loading) {
				return 1
			}

			if (sharedMediaData[0].messages.size == 0 && (!sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1]) && sharedMediaData[0].startReached) {
				return 0
			}

			return if (sharedMediaData[0].totalCount == 0) {
				var count = sharedMediaData[0].startOffset + sharedMediaData[0].messages.size

				if (count != 0 && (!sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1])) {
					if (sharedMediaData[0].endLoadingStubs != 0) {
						count += sharedMediaData[0].endLoadingStubs
					}
					else {
						count++
					}
				}

				count
			}
			else {
				sharedMediaData[0].totalCount
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return false
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					if (sharedResources == null) {
						sharedResources = SharedResources(parent.context)
					}

					view = SharedPhotoVideoCell2(mContext, sharedResources, profileActivity.currentAccount)
					view.setGradientView(globalGradientView)
				}

				2 -> {
					val emptyStubView = createEmptyStubView(mContext, 0, dialogId)
					emptyStubView.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
					return RecyclerListView.Holder(emptyStubView)
				}

				else -> {
					val emptyStubView = createEmptyStubView(mContext, 0, dialogId)
					emptyStubView.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
					return RecyclerListView.Holder(emptyStubView)
				}
			}

			view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			if (holder.itemViewType == 0) {
				val messageObjects = sharedMediaData[0].messages
				val index = position - sharedMediaData[0].startOffset
				val cell = holder.itemView as SharedPhotoVideoCell2
				val oldMessageId = cell.messageId
				val parentCount = if (this === photoVideoAdapter) mediaColumnsCount else animateToColumnsCount

				if (index >= 0 && index < messageObjects.size) {
					val messageObject = messageObjects[index]
					val animated = messageObject.id == oldMessageId

					if (isActionModeShowed) {
						cell.setChecked(selectedFiles[if (messageObject.dialogId == dialogId) 0 else 1].indexOfKey(messageObject.id) >= 0, animated)
					}
					else {
						cell.setChecked(false, animated)
					}

					cell.setMessageObject(messageObject, parentCount)
				}
				else {
					cell.setMessageObject(null, parentCount)
					cell.setChecked(false, false)
				}
			}
		}

		override fun getItemViewType(position: Int): Int {
			if (!inFastScrollMode && sharedMediaData[0].messages.size == 0 && !sharedMediaData[0].loading && sharedMediaData[0].startReached) {
				return 2
			}

			val count = sharedMediaData[0].startOffset + sharedMediaData[0].messages.size

			return if (position - sharedMediaData[0].startOffset >= 0 && position < count) {
				0
			}
			else {
				0
			}
		}

		override fun getLetter(position: Int): String {
			val periods = sharedMediaData[0].fastScrollPeriods

			if (periods.isNotEmpty()) {
				for (i in periods.indices) {
					if (position <= periods[i].startOffset) {
						return periods[i].formattedDate
					}
				}

				return periods[periods.size - 1].formattedDate
			}

			return ""
		}

		override fun getPositionForScrollProgress(listView: RecyclerListView, progress: Float, position: IntArray) {
			val viewHeight = listView.getChildAt(0).measuredHeight
			val totalHeight = (ceil((totalItemsCount / mediaColumnsCount.toFloat()).toDouble()) * viewHeight).toInt()
			val listHeight = listView.measuredHeight - listView.paddingTop
			position[0] = (progress * (totalHeight - listHeight) / viewHeight).toInt() * mediaColumnsCount
			position[1] = (progress * (totalHeight - listHeight)).toInt() % viewHeight
		}

		override fun onStartFastScroll() {
			inFastScrollMode = true
			val mediaPage = getMediaPage(0) ?: return
			showFastScrollHint(mediaPage, null, false)
		}

		override fun onFinishFastScroll(listView: RecyclerListView?) {
			if (inFastScrollMode) {
				inFastScrollMode = false

				if (listView != null) {
					var messageId = 0

					for (i in 0 until listView.childCount) {
						val child = listView.getChildAt(i)

						if (child is SharedPhotoVideoCell2) {
							messageId = child.messageId
						}

						if (messageId != 0) {
							break
						}
					}

					if (messageId == 0) {
						findPeriodAndJumpToDate(0, listView, true)
					}
				}
			}
		}

		override val totalItemsCount: Int
			get() = sharedMediaData[0].totalCount

		override fun getScrollProgress(listView: RecyclerListView): Float {
			val parentCount = if (this === photoVideoAdapter) mediaColumnsCount else animateToColumnsCount
			val cellCount = ceil((totalItemsCount / parentCount.toFloat()).toDouble()).toInt()

			if (listView.childCount == 0) {
				return 0f
			}

			val cellHeight = listView.getChildAt(0).measuredHeight
			val firstChild = listView.getChildAt(0)
			val firstPosition = listView.getChildAdapterPosition(firstChild)

			if (firstPosition < 0) {
				return 0f
			}

			val childTop = (firstChild.top - listView.paddingTop).toFloat()
			val listH = (listView.measuredHeight - listView.paddingTop).toFloat()
			val scrollY = firstPosition / parentCount * cellHeight - childTop

			return scrollY / (cellCount.toFloat() * cellHeight - listH)
		}

		override fun fastScrollIsVisible(listView: RecyclerListView?): Boolean {
			if (listView == null) {
				return false
			}

			val parentCount = if (this === photoVideoAdapter) mediaColumnsCount else animateToColumnsCount
			val cellCount = ceil((totalItemsCount / parentCount.toFloat()).toDouble()).toInt()

			if (listView.childCount == 0) {
				return false
			}

			val cellHeight = listView.getChildAt(0).measuredHeight

			return cellCount * cellHeight > listView.measuredHeight
		}

		override fun onFastScrollSingleTap() {
			showMediaCalendar(true)
		}
	}

	inner class MediaSearchAdapter(private val mContext: Context, private val currentType: Int) : SelectionAdapter() {
		private var globalSearch = mutableListOf<MessageObject>()
		private var searchResult = mutableListOf<MessageObject>()
		private var searchRunnable: Runnable? = null
		private var reqId = 0
		private var lastReqId = 0
		private var searchesInProgress = 0

		fun queryServerSearch(query: String?, maxId: Int, did: Long) {
			if (DialogObject.isEncryptedDialog(did)) {
				return
			}

			if (reqId != 0) {
				profileActivity.connectionsManager.cancelRequest(reqId, true)
				reqId = 0
				searchesInProgress--
			}

			if (query.isNullOrEmpty()) {
				globalSearch.clear()
				lastReqId = 0
				notifyDataSetChanged()
				return
			}

			val req = TLMessagesSearch()
			req.limit = 50
			req.offsetId = maxId

			when (currentType) {
				1 -> req.filter = TLInputMessagesFilterDocument()
				3 -> req.filter = TLInputMessagesFilterUrl()
				4 -> req.filter = TLInputMessagesFilterMusic()
			}

			req.q = query
			req.peer = profileActivity.messagesController.getInputPeer(did)

			if (req.peer is TLRPC.TLInputPeerEmpty) {
				return
			}

			val currentReqId = ++lastReqId

			searchesInProgress++

			reqId = profileActivity.connectionsManager.sendRequest(req, { response, error ->
				val messageObjects = mutableListOf<MessageObject>()

				if (error == null) {
					val res = response as TLRPC.MessagesMessages

					for (a in res.messages.indices) {
						val message = res.messages[a]

						if (maxId != 0 && message.id > maxId) {
							continue
						}

						messageObjects.add(MessageObject(profileActivity.currentAccount, message, generateLayout = false, checkMediaExists = true))
					}
				}

				AndroidUtilities.runOnUIThread {
					if (reqId != 0) {
						if (currentReqId == lastReqId) {
							val oldItemCounts = itemCount
							globalSearch = messageObjects
							searchesInProgress--

							val count = itemCount

							if (searchesInProgress == 0 || count != 0) {
								switchToCurrentSelectedMode(false)
							}

							for (mediaPage in mediaPages) {
								if (mediaPage.selectedType == currentType) {
									if (searchesInProgress == 0 && count == 0) {
										mediaPage.emptyView?.title?.text = LocaleController.formatString("NoResultFoundFor", R.string.NoResultFoundFor, query)
										mediaPage.emptyView?.showProgress(show = false, animated = true)
									}
									else if (oldItemCounts == 0) {
										animateItemsEnter(mediaPage.listView, 0, null)
									}
								}
							}

							notifyDataSetChanged()
						}

						reqId = 0
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)

			profileActivity.connectionsManager.bindRequestToGuid(reqId, profileActivity.classGuid)
		}

		fun search(query: String?, animated: Boolean) {
			if (searchRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(searchRunnable)
				searchRunnable = null
			}

			if (searchResult.isNotEmpty() || globalSearch.isNotEmpty()) {
				searchResult.clear()
				globalSearch.clear()
				notifyDataSetChanged()
			}

			if (query.isNullOrEmpty()) {
				if (searchResult.isNotEmpty() || globalSearch.isNotEmpty() || searchesInProgress != 0) {
					searchResult.clear()
					globalSearch.clear()

					if (reqId != 0) {
						profileActivity.connectionsManager.cancelRequest(reqId, true)
						reqId = 0
						searchesInProgress--
					}
				}
			}
			else {
				for (mediaPage in mediaPages) {
					if (mediaPage.selectedType == currentType) {
						mediaPage.emptyView?.showProgress(true, animated)
					}
				}

				AndroidUtilities.runOnUIThread(Runnable {
					if (sharedMediaData[currentType].messages.isNotEmpty() && (currentType == 1 || currentType == 4)) {
						val messageObject = sharedMediaData[currentType].messages[sharedMediaData[currentType].messages.size - 1]
						queryServerSearch(query, messageObject.id, messageObject.dialogId)
					}
					else if (currentType == 3) {
						queryServerSearch(query, 0, dialogId)
					}

					if (currentType == 1 || currentType == 4) {
						val copy = sharedMediaData[currentType].messages.toMutableList()

						searchesInProgress++

						Utilities.searchQueue.postRunnable {
							val search1 = query.trim().lowercase()

							if (search1.isNullOrEmpty()) {
								updateSearchResults(listOf())
								return@postRunnable
							}

							var search2 = LocaleController.getInstance().getTranslitString(search1)

							if (search1 == search2 || search2.isNullOrEmpty()) {
								search2 = null
							}

							val search = arrayOfNulls<String>(1 + if (search2 != null) 1 else 0)
							search[0] = search1

							if (search2 != null) {
								search[1] = search2
							}

							val resultArray = mutableListOf<MessageObject>()

							for (a in copy.indices) {
								val messageObject = copy[a]

								for (q in search) {
									val name = messageObject.documentName?.lowercase()

									if (name.isNullOrEmpty()) {
										continue
									}

									if (name.contains(q!!)) {
										resultArray.add(messageObject)
										break
									}

									if (currentType == 4) {
										val document = if (messageObject.type == 0) {
											MessageObject.getMedia(messageObject.messageOwner)?.webpage?.document
										}
										else {
											MessageObject.getMedia(messageObject.messageOwner)?.document
										}

										if (document !is TLRPC.TLDocument) {
											continue
										}

										var ok = false

										for (c in document.attributes.indices) {
											val attribute = document.attributes[c]

											if (attribute is TLDocumentAttributeAudio) {
												if (attribute.performer != null) {
													ok = attribute.performer?.lowercase()?.contains(q) == true
												}

												if (!ok && attribute.title != null) {
													ok = attribute.title?.lowercase()?.contains(q) == true
												}

												break
											}
										}

										if (ok) {
											resultArray.add(messageObject)
											break
										}
									}
								}
							}
							updateSearchResults(resultArray)
						}
					}
				}.also {
					searchRunnable = it
				}, 300)
			}
		}

		private fun updateSearchResults(documents: List<MessageObject>) {
			AndroidUtilities.runOnUIThread {
				if (!searching) {
					return@runOnUIThread
				}

				searchesInProgress--

				val oldItemCount = itemCount

				searchResult = documents.toMutableList()

				val count = itemCount

				if (searchesInProgress == 0 || count != 0) {
					switchToCurrentSelectedMode(false)
				}

				for (mediaPage in mediaPages) {
					if (mediaPage.selectedType == currentType) {
						if (searchesInProgress == 0 && count == 0) {
							mediaPage.emptyView?.title?.text = context.getString(R.string.NoResult)
							mediaPage.emptyView?.showProgress(show = false, animated = true)
						}
						else if (oldItemCount == 0) {
							animateItemsEnter(mediaPage.listView, 0, null)
						}
					}
				}

				notifyDataSetChanged()
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType != searchResult.size + globalSearch.size
		}

		override fun getItemCount(): Int {
			var count = searchResult.size
			val globalCount = globalSearch.size

			if (globalCount != 0) {
				count += globalCount
			}

			return count
		}

		fun isGlobalSearch(i: Int): Boolean {
			val localCount = searchResult.size
			val globalCount = globalSearch.size

			return if (i in 0 until localCount) {
				false
			}
			else {
				i > localCount && i <= globalCount + localCount
			}
		}

		fun getItem(i: Int): MessageObject {
			return if (i < searchResult.size) {
				searchResult[i]
			}
			else {
				globalSearch[i - searchResult.size]
			}
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View
			when (currentType) {
				1 -> {
					view = SharedDocumentCell(mContext, SharedDocumentCell.VIEW_TYPE_DEFAULT)
				}

				4 -> {
					view = object : SharedAudioCell(mContext, VIEW_TYPE_DEFAULT) {
						public override fun needPlayMessage(messageObject: MessageObject): Boolean {
							if (messageObject.isVoice || messageObject.isRoundVideo) {
								val result = MediaController.getInstance().playMessage(messageObject)
								MediaController.getInstance().setVoiceMessagesPlaylist(if (result) searchResult else null, false)
								if (messageObject.isRoundVideo) {
									MediaController.getInstance().setCurrentVideoVisible(false)
								}
								return result
							}
							else if (messageObject.isMusic) {
								return MediaController.getInstance().setPlaylist(searchResult, messageObject, mergeDialogId)
							}

							return false
						}
					}
				}

				else -> {
					view = SharedLinkCell(mContext, SharedLinkCell.VIEW_TYPE_DEFAULT)
					view.setDelegate(sharedLinkCellDelegate)
				}
			}

			view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			if (currentType == 1) {
				val sharedDocumentCell = holder.itemView as SharedDocumentCell
				val messageObject = getItem(position)

				sharedDocumentCell.setDocument(messageObject, position != itemCount - 1)

				if (isActionModeShowed) {
					sharedDocumentCell.setChecked(selectedFiles[if (messageObject.dialogId == dialogId) 0 else 1].indexOfKey(messageObject.id) >= 0, !scrolling)
				}
				else {
					sharedDocumentCell.setChecked(false, !scrolling)
				}
			}
			else if (currentType == 3) {
				val sharedLinkCell = holder.itemView as SharedLinkCell
				val messageObject = getItem(position)

				sharedLinkCell.setLink(messageObject, position != itemCount - 1)

				if (isActionModeShowed) {
					sharedLinkCell.setChecked(selectedFiles[if (messageObject.dialogId == dialogId) 0 else 1].indexOfKey(messageObject.id) >= 0, !scrolling)
				}
				else {
					sharedLinkCell.setChecked(false, !scrolling)
				}
			}
			else if (currentType == 4) {
				val sharedAudioCell = holder.itemView as SharedAudioCell
				val messageObject = getItem(position)

				sharedAudioCell.setMessageObject(messageObject, position != itemCount - 1)

				if (isActionModeShowed) {
					sharedAudioCell.setChecked(selectedFiles[if (messageObject.dialogId == dialogId) 0 else 1].indexOfKey(messageObject.id) >= 0, !scrolling)
				}
				else {
					sharedAudioCell.setChecked(false, !scrolling)
				}
			}
		}

		override fun getItemViewType(i: Int): Int {
			return 0
		}
	}

	private inner class GifAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return sharedMediaData[5].messages.size != 0 || sharedMediaData[5].loading
		}

		override fun getItemCount(): Int {
			return if (sharedMediaData[5].messages.size == 0 && !sharedMediaData[5].loading) {
				1
			}
			else {
				sharedMediaData[5].messages.size
			}
		}

		override fun getItemId(i: Int): Long {
			return i.toLong()
		}

		override fun getItemViewType(position: Int): Int {
			return if (sharedMediaData[5].messages.size == 0 && !sharedMediaData[5].loading) {
				1
			}
			else {
				0
			}
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			if (viewType == 1) {
				val emptyStubView = createEmptyStubView(mContext, 5, dialogId)
				emptyStubView.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
				return RecyclerListView.Holder(emptyStubView)
			}

			val cell = ContextLinkCell(mContext, true)
			cell.isCanPreviewGif = true

			return RecyclerListView.Holder(cell)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			if (holder.itemViewType != 1) {
				val messageObject = sharedMediaData[5].messages[position]
				val document = messageObject.document

				if (document != null) {
					val cell = holder.itemView as ContextLinkCell
					cell.setGif(document, messageObject, messageObject.messageOwner!!.date, false)

					if (isActionModeShowed) {
						cell.setChecked(selectedFiles[if (messageObject.dialogId == dialogId) 0 else 1].indexOfKey(messageObject.id) >= 0, !scrolling)
					}
					else {
						cell.setChecked(false, !scrolling)
					}
				}
			}
		}

		override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
			if (holder.itemView is ContextLinkCell) {
				val imageReceiver = holder.itemView.photoImage

				if (mediaPages[0].selectedType == 5) {
					imageReceiver.allowStartAnimation = true
					imageReceiver.startAnimation()
				}
				else {
					imageReceiver.allowStartAnimation = false
					imageReceiver.stopAnimation()
				}
			}
		}
	}

	private inner class CommonGroupsAdapter(private val mContext: Context) : SelectionAdapter() {
		val chats = mutableListOf<Chat>()
		var loading = false
		private var firstLoaded = false
		var endReached = false

		fun getChats(maxId: Long, count: Int) {
			if (loading) {
				return
			}

			val req = TLMessagesGetCommonChats()

			val uid = if (DialogObject.isEncryptedDialog(dialogId)) {
				val encryptedChat = profileActivity.messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))
				encryptedChat?.userId ?: 0
			}
			else {
				dialogId
			}

			req.userId = profileActivity.messagesController.getInputUser(uid)

			if (req.userId is TLInputUserEmpty) {
				return
			}

			req.limit = count
			req.maxId = maxId

			loading = true

			notifyDataSetChanged()

			val reqId = profileActivity.connectionsManager.sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					val oldCount = itemCount

					if (error == null) {
						val res = response as TLRPC.MessagesChats
						profileActivity.messagesController.putChats(res.chats, false)
						endReached = res.chats.isEmpty() || res.chats.size != count
						chats.addAll(res.chats)
					}
					else {
						endReached = true
					}

					for (mediaPage in mediaPages) {
						if (mediaPage.selectedType == 6) {
							if (mediaPage.listView != null) {

								val listView: RecyclerListView? = mediaPage.listView

								if (firstLoaded || oldCount == 0) {
									animateItemsEnter(listView, 0, null)
								}
							}
						}
					}

					loading = false
					firstLoaded = true

					notifyDataSetChanged()
				}
			}

			profileActivity.connectionsManager.bindRequestToGuid(reqId, profileActivity.classGuid)
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.adapterPosition != chats.size
		}

		override fun getItemCount(): Int {
			if (chats.isEmpty() && !loading) {
				return 1
			}

			var count = chats.size

			if (chats.isNotEmpty()) {
				if (!endReached) {
					count++
				}
			}

			return count
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = ProfileSearchCell(mContext)
				}

				2 -> {
					val emptyStubView = createEmptyStubView(mContext, 6, dialogId)
					emptyStubView.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
					return RecyclerListView.Holder(emptyStubView)
				}

				1 -> {
					val flickerLoadingView = FlickerLoadingView(mContext)
					flickerLoadingView.setIsSingleCell(true)
					flickerLoadingView.showDate(false)
					flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_TYPE)
					view = flickerLoadingView
				}

				else -> {
					val flickerLoadingView = FlickerLoadingView(mContext)
					flickerLoadingView.setIsSingleCell(true)
					flickerLoadingView.showDate(false)
					flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_TYPE)
					view = flickerLoadingView
				}
			}

			view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			if (holder.itemViewType == 0) {
				val cell = holder.itemView as ProfileSearchCell
				val chat = chats[position]
				cell.setData(chat, null, null, null, needCount = false, saved = false)
				cell.useSeparator = position != chats.size - 1 || !endReached
			}
		}

		override fun getItemViewType(i: Int): Int {
			if (chats.isEmpty() && !loading) {
				return 2
			}
			return if (i < chats.size) {
				0
			}
			else {
				1
			}
		}
	}

	private inner class ChatUsersAdapter(private val mContext: Context) : SelectionAdapter() {
		var chatInfo: ChatFull? = null
		var sortedUsers: List<Int>? = null

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return true
		}

		override fun getItemCount(): Int {
			return if (chatInfo != null && chatInfo?.participants?.participants.isNullOrEmpty()) {
				1
			}
			else {
				chatInfo?.participants?.participants?.size ?: 0
			}
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			if (viewType == 1) {
				val emptyStubView = createEmptyStubView(mContext, 7, dialogId)
				emptyStubView.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
				return RecyclerListView.Holder(emptyStubView)
			}

			val view: View = UserCell(mContext, 9, 0, admin = true, needAddButton = false)
			view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val userCell = holder.itemView as UserCell
			val part = if (sortedUsers!!.isNotEmpty()) {
				chatInfo?.participants?.participants?.get(sortedUsers!![position])
			}
			else {
				chatInfo?.participants?.participants?.get(position)
			}

			if (part != null) {
				val role = if (part is TLChatChannelParticipant) {
					val channelParticipant = part.channelParticipant

					if (!channelParticipant?.rank.isNullOrEmpty()) {
						channelParticipant?.rank
					}
					else {
						when (channelParticipant) {
							is TLChannelParticipantCreator -> {
								context.getString(R.string.ChannelCreator)
							}

							is TLChannelParticipantAdmin -> {
								context.getString(R.string.ChannelAdmin)
							}

							else -> {
								null
							}
						}
					}
				}
				else {
					when (part) {
						is TLChatParticipantCreator -> {
							context.getString(R.string.ChannelCreator)
						}

						is TLChatParticipantAdmin -> {
							context.getString(R.string.ChannelAdmin)
						}

						else -> {
							null
						}
					}
				}

				userCell.setAdminRole(role)
				userCell.setData(profileActivity.messagesController.getUser(part.userId), null, null, 0, position != (chatInfo?.participants?.participants?.size ?: 0) - 1)
			}
		}

		override fun getItemViewType(i: Int): Int {
			return if (chatInfo != null && chatInfo?.participants?.participants.isNullOrEmpty()) {
				1
			}
			else {
				0
			}
		}
	}

	private inner class GroupUsersSearchAdapter(private val mContext: Context) : SelectionAdapter() {
		var searchCount = 0
		private var searchResultNames = mutableListOf<CharSequence>()
		private val searchAdapterHelper: SearchAdapterHelper = SearchAdapterHelper(true)
		private var searchRunnable: Runnable? = null
		private var totalCount = 0
		private val currentChat: Chat?

		init {
			searchAdapterHelper.setDelegate(object : SearchAdapterHelper.SearchAdapterHelperDelegate {
				override fun onDataSetChanged(searchId: Int) {
					notifyDataSetChanged()

					if (searchId == 1) {
						searchCount--

						if (searchCount == 0) {
							for (mediaPage in mediaPages) {
								if (mediaPage.selectedType == 7) {
									if (itemCount == 0) {
										mediaPage.emptyView?.showProgress(show = false, animated = true)
									}
									else {
										animateItemsEnter(mediaPage.listView, 0, null)
									}
								}
							}
						}
					}
				}
			})

			currentChat = delegate.currentChat
		}

		private fun createMenuForParticipant(participant: TLObject?, resultOnly: Boolean): Boolean {
			@Suppress("NAME_SHADOWING") var participant: TLObject? = participant

			if (participant is ChannelParticipant) {
				val channelParticipant = participant
				val p = TLChatChannelParticipant()
				p.channelParticipant = channelParticipant
				p.userId = MessageObject.getPeerId(channelParticipant.peer).takeIf { it != 0L } ?: channelParticipant.userId
				p.inviterId = channelParticipant.inviterId
				p.date = channelParticipant.date
				participant = p
			}

			return delegate.onMemberClick(participant as ChatParticipant?, true, resultOnly)
		}

		fun search(query: String?, animated: Boolean) {
			if (searchRunnable != null) {
				Utilities.searchQueue.cancelRunnable(searchRunnable)
				searchRunnable = null
			}

			searchResultNames.clear()

			searchAdapterHelper.mergeResults(null)
			searchAdapterHelper.queryServerSearch(null, allowUsername = true, allowChats = false, allowBots = true, allowSelf = false, canAddGroupsOnly = false, channelId = if (ChatObject.isChannel(currentChat)) currentChat.id else 0, type = 2, searchId = 0)

			notifyDataSetChanged()

			for (mediaPage in mediaPages) {
				if (mediaPage.selectedType == 7) {
					if (!query.isNullOrEmpty()) {
						mediaPage.emptyView?.showProgress(true, animated)
					}
				}
			}

			if (!query.isNullOrEmpty()) {
				Utilities.searchQueue.postRunnable(Runnable {
					processSearch(query)
				}.also {
					searchRunnable = it
				}, 300)
			}
		}

		private fun processSearch(query: String?) {
			AndroidUtilities.runOnUIThread {
				searchRunnable = null

				val participantsCopy: List<TLObject>? = if (!ChatObject.isChannel(currentChat)) info?.participants?.participants?.toMutableList() else null

				searchCount = 2

				if (participantsCopy != null) {
					Utilities.searchQueue.postRunnable {
						val search1 = query?.trim()?.lowercase()

						if (search1.isNullOrEmpty()) {
							updateSearchResults(listOf(), listOf())
							return@postRunnable
						}

						var search2 = LocaleController.getInstance().getTranslitString(search1)

						if (search1 == search2 || search2.isNullOrEmpty()) {
							search2 = null
						}

						val search = arrayOfNulls<String>(1 + if (search2 != null) 1 else 0)

						search[0] = search1

						if (search2 != null) {
							search[1] = search2
						}

						val resultArrayNames = mutableListOf<CharSequence>()
						val resultArray2 = mutableListOf<TLObject>()

						for (o in participantsCopy) {
							val userId = if (o is ChatParticipant) {
								o.userId
							}
							else if (o is ChannelParticipant) {
								MessageObject.getPeerId(o.peer).takeIf { it != 0L } ?: o.userId
							}
							else {
								continue
							}

							val user = profileActivity.messagesController.getUser(userId)

							if (user?.id == profileActivity.userConfig.getClientUserId()) {
								continue
							}

							val name = UserObject.getUserName(user).lowercase()
							var tName = LocaleController.getInstance().getTranslitString(name)

							if (name == tName) {
								tName = null
							}

							var found = 0

							for (q in search) {
								if (name.startsWith(q!!) || name.contains(" $q") || tName != null && (tName.startsWith(q) || tName.contains(" $q"))) {
									found = 1
								}
								else if (user?.username?.startsWith(q) == true) {
									found = 2
								}

								if (found != 0) {
									if (found == 1) {
										resultArrayNames.add(AndroidUtilities.generateSearchName(user?.firstName, user?.lastName, q))
									}
									else {
										resultArrayNames.add(AndroidUtilities.generateSearchName("@" + user?.username, null, "@$q"))
									}

									resultArray2.add(o)

									break
								}
							}
						}

						updateSearchResults(resultArrayNames, resultArray2)
					}
				}
				else {
					searchCount--
				}

				searchAdapterHelper.queryServerSearch(query, allowUsername = false, allowChats = false, allowBots = true, allowSelf = false, canAddGroupsOnly = false, channelId = if (ChatObject.isChannel(currentChat)) currentChat.id else 0, type = 2, searchId = 1)
			}
		}

		private fun updateSearchResults(names: List<CharSequence>, participants: List<TLObject>) {
			AndroidUtilities.runOnUIThread {
				if (!searching) {
					return@runOnUIThread
				}

				searchResultNames = names.toMutableList()

				searchCount--

				if (!ChatObject.isChannel(currentChat)) {
					val search = searchAdapterHelper.groupSearch
					search.clear()
					search.addAll(participants)
				}

				if (searchCount == 0) {
					for (mediaPage in mediaPages) {
						if (mediaPage.selectedType == 7) {
							if (itemCount == 0) {
								mediaPage.emptyView?.showProgress(show = false, animated = true)
							}
							else {
								animateItemsEnter(mediaPage.listView, 0, null)
							}
						}
					}
				}

				notifyDataSetChanged()
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType != 1
		}

		override fun getItemCount(): Int {
			return totalCount
		}

		override fun notifyDataSetChanged() {
			totalCount = searchAdapterHelper.groupSearch.size

			if (totalCount > 0 && searching && mediaPages[0].selectedType == 7 && mediaPages[0].listView!!.adapter !== this) {
				switchToCurrentSelectedMode(false)
			}

			super.notifyDataSetChanged()
		}

//		fun removeUserId(userId: Long) {
//			searchAdapterHelper.removeUserId(userId)
//			notifyDataSetChanged()
//		}

		fun getItem(i: Int): TLObject? {
			val count = searchAdapterHelper.groupSearch.size

			return if (i < 0 || i >= count) {
				null
			}
			else {
				searchAdapterHelper.groupSearch[i]
			}
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = ManageChatUserCell(mContext, 9, 5, true)
			view.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

			view.setDelegate { cell, click ->
				val `object` = getItem(cell.tag as Int)

				if (`object` is ChannelParticipant) {
					return@setDelegate createMenuForParticipant(`object`, !click)
				}
				else {
					return@setDelegate false
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val user = when (val `object` = getItem(position)) {
				is ChannelParticipant -> {
					profileActivity.messagesController.getUser(`object`.userId)
				}

				is ChatParticipant -> {
					profileActivity.messagesController.getUser(`object`.userId)
				}

				else -> {
					return
				}
			}

			var name: SpannableStringBuilder? = null
			val nameSearch = searchAdapterHelper.lastFoundChannel

			if (nameSearch != null) {
				val u = UserObject.getUserName(user)

				name = SpannableStringBuilder(u)

				val idx = AndroidUtilities.indexOfIgnoreCase(u, nameSearch)

				if (idx != -1) {
					name.setSpan(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, R.color.brand, null)), idx, idx + nameSearch.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
			}

			val userCell = holder.itemView as ManageChatUserCell
			userCell.tag = position
			userCell.setData(user, name, null, false)
		}

		override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
			if (holder.itemView is ManageChatUserCell) {
				holder.itemView.recycle()
			}
		}

		override fun getItemViewType(i: Int): Int {
			return 0
		}
	}

	private inner class ScrollSlidingTextTabStripInner(context: Context) : ScrollSlidingTextTabStrip(context) {
		private var bgColor = Color.TRANSPARENT
		private val backgroundPaint by lazy { Paint() }

		fun drawBackground(canvas: Canvas) {
			// if (SharedConfig.chatBlurEnabled() && bgColor != Color.TRANSPARENT) {
			if (bgColor != Color.TRANSPARENT) {
				backgroundPaint.color = bgColor
				AndroidUtilities.rectTmp2.set(0, 0, measuredWidth, measuredHeight)
				drawBackgroundWithBlur(canvas, y, AndroidUtilities.rectTmp2, backgroundPaint)
			}
		}

		override fun setBackgroundColor(color: Int) {
			bgColor = color
			invalidate()
		}
	}

	companion object {
		const val FILTER_PHOTOS_AND_VIDEOS = 0
		const val FILTER_PHOTOS_ONLY = 1
		const val FILTER_VIDEOS_ONLY = 2
		const val VIEW_TYPE_MEDIA_ACTIVITY = 0
		const val VIEW_TYPE_PROFILE_ACTIVITY = 1
		private val supportedFastScrollTypes = intArrayOf(MediaDataController.MEDIA_PHOTOVIDEO, MediaDataController.MEDIA_FILE, MediaDataController.MEDIA_AUDIO, MediaDataController.MEDIA_MUSIC)

		private val interpolator = Interpolator { t: Float ->
			var t2 = t
			--t2
			t2 * t2 * t2 * t2 * t2 + 1.0f
		}

		private const val FORWARD = 100
		private const val DELETE = 101
		private const val GO_TO_CHAT = 102

		fun showFastScrollHint(mediaPage: MediaPage, sharedMediaData: Array<SharedMediaData>?, show: Boolean) {
			if (show) {
				if (SharedConfig.fastScrollHintCount <= 0 || mediaPage.fastScrollHintView != null || mediaPage.fastScrollHinWasShown || mediaPage.listView!!.fastScroll == null || mediaPage.listView?.fastScroll?.isVisible == false || mediaPage.listView?.fastScroll?.visibility != VISIBLE || sharedMediaData!![0].totalCount < 50) {
					return
				}

				SharedConfig.setFastScrollHintCount(SharedConfig.fastScrollHintCount - 1)
				mediaPage.fastScrollHinWasShown = true

				val tooltip = SharedMediaFastScrollTooltip(mediaPage.context)

				mediaPage.fastScrollHintView = tooltip

				mediaPage.addView(mediaPage.fastScrollHintView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat()))

				mediaPage.fastScrollHintView?.alpha = 0f
				mediaPage.fastScrollHintView?.scaleX = 0.8f
				mediaPage.fastScrollHintView?.scaleY = 0.8f
				mediaPage.fastScrollHintView?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)?.setDuration(150)?.start()

				mediaPage.invalidate()

				AndroidUtilities.runOnUIThread(Runnable {
					mediaPage.fastScrollHintView = null
					mediaPage.fastScrollHideHintRunnable = null

					tooltip.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(220).setListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							if (tooltip.parent != null) {
								(tooltip.parent as ViewGroup).removeView(tooltip)
							}
						}
					}).start()
				}.also {
					mediaPage.fastScrollHideHintRunnable = it
				}, 4000)
			}
			else {
				if (mediaPage.fastScrollHintView == null || mediaPage.fastScrollHideHintRunnable == null) {
					return
				}

				AndroidUtilities.cancelRunOnUIThread(mediaPage.fastScrollHideHintRunnable)

				mediaPage.fastScrollHideHintRunnable?.run()
				mediaPage.fastScrollHideHintRunnable = null
				mediaPage.fastScrollHintView = null
			}
		}

		fun createEmptyStubView(context: Context, currentType: Int, dialogId: Long): View {
			val emptyStubView = EmptyStubView(context)

			if (currentType == 0) {
				if (DialogObject.isEncryptedDialog(dialogId)) {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoMediaSecret)
				}
				else {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoMedia)
				}
			}
			else if (currentType == 1) {
				if (DialogObject.isEncryptedDialog(dialogId)) {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoSharedFilesSecret)
				}
				else {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoSharedFiles)
				}
			}
			else if (currentType == 2) {
				if (DialogObject.isEncryptedDialog(dialogId)) {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoSharedVoiceSecret)
				}
				else {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoSharedVoice)
				}
			}
			else if (currentType == 3) {
				if (DialogObject.isEncryptedDialog(dialogId)) {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoSharedLinksSecret)
				}
				else {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoSharedLinks)
				}
			}
			else if (currentType == 4) {
				if (DialogObject.isEncryptedDialog(dialogId)) {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoSharedAudioSecret)
				}
				else {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoSharedAudio)
				}
			}
			else if (currentType == 5) {
				if (DialogObject.isEncryptedDialog(dialogId)) {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoSharedGifSecret)
				}
				else {
					emptyStubView.emptyTextView.text = context.getString(R.string.NoGIFs)
				}
			}
			else if (currentType == 6) {
				emptyStubView.emptyImageView.setImageDrawable(null)
				emptyStubView.emptyTextView.text = context.getString(R.string.NoGroupsInCommon)
			}
			else if (currentType == 7) {
				emptyStubView.emptyImageView.setImageDrawable(null)
				emptyStubView.emptyTextView.text = ""
			}

			return emptyStubView
		}
	}
}
