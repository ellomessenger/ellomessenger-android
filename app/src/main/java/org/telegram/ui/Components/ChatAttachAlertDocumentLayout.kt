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
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.SparseArray
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.MimeTypeMap
import android.widget.EditText
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.children
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildVars
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaController.PhotoEntry
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper.SendingMediaInfo
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.ringtone.RingtoneDataStore
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.channelId
import org.telegram.tgnet.chatId
import org.telegram.tgnet.userId
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.FiltersView
import org.telegram.ui.Adapters.FiltersView.DateData
import org.telegram.ui.Adapters.FiltersView.MediaFilterData
import org.telegram.ui.Cells.GraySectionCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.SharedDocumentCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatAttachAlert.AttachAlertLayout
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RecyclerListView.SectionsAdapter
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.FilteredSearchView.MessageHashId
import org.telegram.ui.PhotoPickerActivity
import org.telegram.ui.PhotoPickerActivity.PhotoPickerActivityDelegate
import org.telegram.ui.sales.CreateMediaSaleFragment
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Locale
import java.util.StringTokenizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
class ChatAttachAlertDocumentLayout(alert: ChatAttachAlert, context: Context, type: Int) : AttachAlertLayout(alert, context) {
	private val listView = object : RecyclerListView(context) {
		val paint = Paint()

		override fun dispatchDraw(canvas: Canvas) {
			if (currentAnimationType == ANIMATION_FORWARD && isNotEmpty()) {
				var top = Int.MAX_VALUE.toFloat()

				for (i in 0 until childCount) {
					if (getChildAt(i).y < top) {
						top = getChildAt(i).y
					}
				}

				paint.color = ResourcesCompat.getColor(resources, R.color.background, null)
			}

			super.dispatchDraw(canvas)
		}
	}

	private val backgroundListView: RecyclerListView
	private val listAdapter = ListAdapter(context)
	private val backgroundListAdapter = ListAdapter(context)
	private var backgroundLayoutManager: LinearLayoutManager? = null
	private val searchAdapter = SearchAdapter(context)
	private val layoutManager: LinearLayoutManager
	private var searchItem: ActionBarMenuItem? = null
	private var sortItem: ActionBarMenuItem? = null
	private val filtersView = FiltersView(context)
	private val loadingView = FlickerLoadingView(context)
	private val emptyView: StickerEmptyView
	private val selectedFiles = mutableMapOf<String, ListItem>()
	private val selectedFilesOrder = mutableListOf<String>()
	private val selectedMessages = mutableMapOf<MessageHashId, MessageObject>()
	private val allowMusic = type == TYPE_MUSIC
	var isSoundPicker = type == TYPE_RINGTONE
	private var listAnimation: ValueAnimator? = null
	private var currentAnimationType = 0
	private var filtersViewAnimator: AnimatorSet? = null
	private var sendPressed = false
	private var ignoreLayout = false
	private var additionalTranslationY = 0f
	private var hasFiles = false
	private var currentDir: File? = null
	private var receiverRegistered = false
	private var delegate: DocumentSelectActivityDelegate? = null
	private var scrolling = false
	private var maxSelectedFiles = -1
	private var canSelectOnlyImageFiles = false
	private var searching = false
	private var sortByName = SharedConfig.sortFilesByName

	private val receiver = object : BroadcastReceiver() {
		override fun onReceive(arg0: Context, intent: Intent) {
			val r = Runnable {
				try {
					if (currentDir == null) {
						listRoots()
					}
					else {
						listFiles(currentDir!!)
					}

					updateSearchButton()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			if (Intent.ACTION_MEDIA_UNMOUNTED == intent.action) {
				listView.postDelayed(r, 1000)
			}
			else {
				r.run()
			}
		}
	}

	init {
		loadRecentFiles()

		if (!receiverRegistered) {
			receiverRegistered = true

			val filter = IntentFilter()
			filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
			filter.addAction(Intent.ACTION_MEDIA_CHECKING)
			filter.addAction(Intent.ACTION_MEDIA_EJECT)
			filter.addAction(Intent.ACTION_MEDIA_MOUNTED)
			filter.addAction(Intent.ACTION_MEDIA_NOFS)
			filter.addAction(Intent.ACTION_MEDIA_REMOVED)
			filter.addAction(Intent.ACTION_MEDIA_SHARED)
			filter.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE)
			filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED)
			filter.addDataScheme("file")

			ApplicationLoader.applicationContext.registerReceiver(receiver, filter)
		}

		val menu = parentAlert.actionBar.createMenu()

		searchItem = menu.addItem(SEARCH_BUTTON, R.drawable.ic_search_menu).setIsSearchField(true).setActionBarMenuItemSearchListener(object : ActionBarMenuItemSearchListener() {
			override fun onSearchExpand() {
				searching = true
				sortItem?.visibility = GONE
				parentAlert.makeFocusable(searchItem?.searchField, true)
			}

			override fun onSearchCollapse() {
				searching = false
				sortItem?.visibility = VISIBLE

				if (listView.adapter !== listAdapter) {
					listView.adapter = listAdapter
				}

				listAdapter.notifyDataSetChanged()

				searchAdapter.search(null, true)
			}

			override fun onTextChanged(editText: EditText) {
				searchAdapter.search(editText.text?.toString(), false)
			}

			override fun onSearchFilterCleared(filterData: MediaFilterData) {
				searchAdapter.removeSearchFilter(filterData)
				searchAdapter.search(searchItem?.searchField?.text?.toString(), false)
				searchAdapter.updateFiltersView(true, null, null, true)
			}
		})

		searchItem?.setSearchFieldHint(context.getString(R.string.Search))
		searchItem?.contentDescription = context.getString(R.string.Search)

		val editText = searchItem?.searchField
		editText?.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
		editText?.setCursorColor(ResourcesCompat.getColor(resources, R.color.text, null))
		editText?.setHintTextColor(ResourcesCompat.getColor(resources, R.color.hint, null))

		sortItem = menu.addItem(SORT_BUTTON, if (sortByName) R.drawable.msg_contacts_time else R.drawable.msg_contacts_name)
		sortItem?.contentDescription = context.getString(R.string.AccDescrContactSorting)

		addView(loadingView)

		emptyView = object : StickerEmptyView(context, loadingView, STICKER_TYPE_SEARCH) {
			override fun getTranslationY(): Float {
				return super.getTranslationY() - additionalTranslationY
			}

			override fun setTranslationY(translationY: Float) {
				super.setTranslationY(translationY + additionalTranslationY)
			}
		}

		addView(emptyView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		emptyView.setVisibility(GONE)
		emptyView.setOnTouchListener { _, _ -> true }

		backgroundListView = object : RecyclerListView(context) {
			val paint = Paint()

			override fun dispatchDraw(canvas: Canvas) {
				if (currentAnimationType == ANIMATION_BACKWARD && isNotEmpty()) {
					var top = Int.MAX_VALUE.toFloat()

					for (i in 0 until childCount) {
						if (getChildAt(i).y < top) {
							top = getChildAt(i).y
						}
					}

					paint.color = ResourcesCompat.getColor(resources, R.color.background, null)
				}

				super.dispatchDraw(canvas)
			}

			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(e: MotionEvent): Boolean {
				return if (currentAnimationType != ANIMATION_NONE) {
					false
				}
				else {
					super.onTouchEvent(e)
				}
			}
		}

		backgroundListView.setSectionsType(RecyclerListView.SECTIONS_TYPE_DATE)
		backgroundListView.setVerticalScrollBarEnabled(false)
		backgroundListView.setLayoutManager(FillLastLinearLayoutManager(context, LinearLayoutManager.VERTICAL, false, AndroidUtilities.dp(56f), backgroundListView).also { backgroundLayoutManager = it })
		backgroundListView.setClipToPadding(false)
		backgroundListView.setAdapter(backgroundListAdapter)
		backgroundListView.setPadding(0, 0, 0, AndroidUtilities.dp(48f))

		addView(backgroundListView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		backgroundListView.setVisibility(GONE)

		listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_DATE)
		listView.isVerticalScrollBarEnabled = false

		listView.layoutManager = object : FillLastLinearLayoutManager(context, VERTICAL, false, AndroidUtilities.dp(56f), listView) {
			override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
				val linearSmoothScroller: LinearSmoothScroller = object : LinearSmoothScroller(recyclerView.context) {
					override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
						var dy = super.calculateDyToMakeVisible(view, snapPreference)
						dy -= listView.paddingTop - AndroidUtilities.dp(56f)
						return dy
					}

					override fun calculateTimeForDeceleration(dx: Int): Int {
						return super.calculateTimeForDeceleration(dx) * 2
					}
				}
				linearSmoothScroller.targetPosition = position
				startSmoothScroll(linearSmoothScroller)
			}
		}.also { layoutManager = it }

		listView.clipToPadding = false
		listView.adapter = listAdapter
		listView.setPadding(0, 0, 0, AndroidUtilities.dp(48f))

		addView(listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				parentAlert.updateLayout(this@ChatAttachAlertDocumentLayout, true, dy)

				updateEmptyViewPosition()

				if (listView.adapter === searchAdapter) {
					val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
					val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
					val visibleItemCount = abs(lastVisibleItem - firstVisibleItem) + 1
					val totalItemCount = recyclerView.adapter?.itemCount ?: 0

					if (visibleItemCount > 0 && lastVisibleItem >= totalItemCount - 10) {
						searchAdapter.loadMore()
					}
				}
			}

			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				if (newState == RecyclerView.SCROLL_STATE_IDLE) {
					val offset = AndroidUtilities.dp(13f)
					val backgroundPaddingTop = parentAlert.backgroundPaddingTop
					val top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset

					if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
						val holder = listView.findViewHolderForAdapterPosition(0) as? RecyclerListView.Holder

						if (holder != null && holder.itemView.top > AndroidUtilities.dp(56f)) {
							listView.smoothScrollBy(0, holder.itemView.top - AndroidUtilities.dp(56f))
						}
					}
				}

				if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searching && listView.adapter === searchAdapter) {
					AndroidUtilities.hideKeyboard(parentAlert.currentFocus)
				}

				scrolling = newState != RecyclerView.SCROLL_STATE_IDLE
			}
		})

		listView.setOnItemClickListener { view, position ->
			val `object` = if (listView.adapter === listAdapter) {
				listAdapter.getItem(position)
			}
			else {
				searchAdapter.getItem(position)
			}

			if (`object` is ListItem) {
				val file = `object`.file

				if (!BuildVars.NO_SCOPED_STORAGE && (`object`.icon == R.drawable.files_storage || `object`.icon == R.drawable.files_internal)) {
					delegate?.startDocumentSelectActivity()
				}
				else if (file == null) {
					when (`object`.icon) {
						R.drawable.files_gallery -> {
							val selectedPhotos = mutableMapOf<Any, Any>()
							val selectedPhotosOrder = mutableListOf<Any>()
							val chatActivity = parentAlert.baseFragment as? ChatActivity

							val fragment = PhotoPickerActivity(0, MediaController.allMediaAlbumEntry, selectedPhotos, selectedPhotosOrder, 0, chatActivity != null, chatActivity, false, parentAlert.baseFragment is CreateMediaSaleFragment)
							fragment.setDocumentsPicker(true)

							fragment.setDelegate(object : PhotoPickerActivityDelegate {
								override fun selectedPhotosChanged() {
									// unused
								}

								override fun actionButtonPressed(canceled: Boolean, notify: Boolean, scheduleDate: Int) {
									if (!canceled) {
										sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, notify, scheduleDate)
									}
								}

								override fun onCaptionChanged(text: CharSequence) {
									// unused
								}

								override fun onOpenInPressed() {
									delegate?.startDocumentSelectActivity()
								}
							})

							fragment.setMaxSelectedPhotos(maxSelectedFiles, false)

							parentAlert.baseFragment.presentFragment(fragment)
							parentAlert.dismiss(true)
						}

						R.drawable.files_music -> {
							delegate?.startMusicSelectActivity()
						}

						else -> {
							val top = topForScroll

							prepareAnimation()

							val he = listAdapter.history.removeAt(listAdapter.history.size - 1)

							parentAlert.actionBar.setTitle(he.title)

							if (he.dir != null) {
								listFiles(he.dir!!)
							}
							else {
								listRoots()
							}

							updateSearchButton()

							layoutManager.scrollToPositionWithOffset(0, top)

							runAnimation(ANIMATION_BACKWARD)
						}
					}
				}
				else if (file.isDirectory) {
					val he = HistoryEntry()
					val child = listView.getChildAt(0)
					val holder = listView.findContainingViewHolder(child)

					if (holder != null) {
						he.scrollItem = holder.adapterPosition
						he.scrollOffset = child.top
						he.dir = currentDir
						he.title = parentAlert.actionBar.title

						prepareAnimation()

						listAdapter.history.add(he)

						if (!listFiles(file)) {
							listAdapter.history.remove(he)
							return@setOnItemClickListener
						}
						else {
							runAnimation(ANIMATION_FORWARD)
						}

						parentAlert.actionBar.setTitle(`object`.title)
					}
				}
				else {
					onItemClick(view, `object`)
				}
			}
			else {
				onItemClick(view, `object`)
			}
		}

		listView.setOnItemLongClickListener { view, position ->
			val `object` = if (listView.adapter === listAdapter) {
				listAdapter.getItem(position)
			}
			else {
				searchAdapter.getItem(position)
			}

			onItemClick(view, `object`)
		}

		filtersView.setOnItemClickListener { _, position ->
			filtersView.cancelClickRunnables(true)
			searchAdapter.addSearchFilter(filtersView.getFilterAt(position))
		}

		filtersView.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.background, null))

		addView(filtersView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP))

		filtersView.translationY = -AndroidUtilities.dp(44f).toFloat()
		filtersView.visibility = INVISIBLE

		listRoots()
		updateSearchButton()
		updateEmptyView()
	}

	private fun runAnimation(animationType: Int) {
		listAnimation?.cancel()

		currentAnimationType = animationType

		var listViewChildIndex = 0

		for (i in 0 until childCount) {
			if (getChildAt(i) === listView) {
				listViewChildIndex = i
				break
			}
		}

		val xTranslate: Float

		if (animationType == ANIMATION_FORWARD) {
			xTranslate = AndroidUtilities.dp(150f).toFloat()

			backgroundListView.alpha = 1f
			backgroundListView.scaleX = 1f
			backgroundListView.scaleY = 1f
			backgroundListView.translationX = 0f

			removeView(backgroundListView)

			addView(backgroundListView, listViewChildIndex)

			backgroundListView.visibility = VISIBLE

			listView.translationX = xTranslate
			listView.alpha = 0f

			listAnimation = ValueAnimator.ofFloat(1f, 0f)
		}
		else {
			xTranslate = AndroidUtilities.dp(150f).toFloat()

			listView.alpha = 0f
			listView.scaleX = 0.95f
			listView.scaleY = 0.95f

			backgroundListView.scaleX = 1f
			backgroundListView.scaleY = 1f
			backgroundListView.translationX = 0f
			backgroundListView.alpha = 1f

			removeView(backgroundListView)

			addView(backgroundListView, listViewChildIndex + 1)

			backgroundListView.visibility = VISIBLE

			listAnimation = ValueAnimator.ofFloat(0f, 1f)
		}

		listAnimation?.addUpdateListener {
			val value = it.animatedValue as Float

			if (animationType == ANIMATION_FORWARD) {
				listView.translationX = xTranslate * value
				listView.alpha = 1f - value
				listView.invalidate()

				backgroundListView.alpha = value

				val s = 0.95f + value * 0.05f

				backgroundListView.scaleX = s
				backgroundListView.scaleY = s
			}
			else {
				backgroundListView.translationX = xTranslate * value
				backgroundListView.alpha = max(0f, 1f - value)
				backgroundListView.invalidate()

				listView.alpha = value

				val s = 0.95f + value * 0.05f

				listView.scaleX = s
				listView.scaleY = s

				backgroundListView.invalidate()
			}
		}

		listAnimation?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				super.onAnimationEnd(animation)
				backgroundListView.visibility = GONE
				currentAnimationType = ANIMATION_NONE
				listView.alpha = 1f
				listView.scaleX = 1f
				listView.scaleY = 1f
				listView.translationX = 0f
				listView.invalidate()
			}
		})

		if (animationType == ANIMATION_FORWARD) {
			listAnimation?.duration = 220
		}
		else {
			listAnimation?.duration = 200
		}

		listAnimation?.interpolator = CubicBezierInterpolator.DEFAULT
		listAnimation?.start()
	}

	private fun prepareAnimation() {
		backgroundListAdapter.history.clear()
		backgroundListAdapter.history.addAll(listAdapter.history)
		backgroundListAdapter.items.clear()
		backgroundListAdapter.items.addAll(listAdapter.items)
		backgroundListAdapter.recentItems.clear()
		backgroundListAdapter.recentItems.addAll(listAdapter.recentItems)
		backgroundListAdapter.notifyDataSetChanged()

		backgroundListView.visibility = VISIBLE
		backgroundListView.setPadding(listView.paddingLeft, listView.paddingTop, listView.paddingRight, listView.paddingBottom)

		val p = layoutManager.findFirstVisibleItemPosition()

		if (p >= 0) {
			val childView = layoutManager.findViewByPosition(p)

			if (childView != null) {
				backgroundLayoutManager?.scrollToPositionWithOffset(p, childView.top - backgroundListView.paddingTop)
			}
		}
	}

	override fun onDestroy() {
		try {
			if (receiverRegistered) {
				ApplicationLoader.applicationContext.unregisterReceiver(receiver)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		parentAlert.actionBar.closeSearchField()

		val menu = parentAlert.actionBar.createMenu()
		menu.removeView(sortItem)
		menu.removeView(searchItem)
	}

	override fun onMenuItemClick(id: Int) {
		if (id == SORT_BUTTON) {
			SharedConfig.toggleSortFilesByName()
			sortByName = SharedConfig.sortFilesByName
			sortRecentItems()
			sortFileItems()
			listAdapter.notifyDataSetChanged()
			sortItem?.setIcon(if (sortByName) R.drawable.msg_contacts_time else R.drawable.msg_contacts_name)
		}
	}

	override fun needsActionBar(): Int {
		return 1
	}

	override var currentItemTop: Int
		get() {
			if (listView.childCount <= 0) {
				return Int.MAX_VALUE
			}

			val child = listView.getChildAt(0)
			val holder = listView.findContainingViewHolder(child) as? RecyclerListView.Holder
			val top = child.y.toInt() - AndroidUtilities.dp(8f)
			var newOffset = if (top > 0 && holder != null && holder.adapterPosition == 0) top else 0

			if (top >= 0 && holder != null && holder.adapterPosition == 0) {
				newOffset = top
			}

			return newOffset + AndroidUtilities.dp(13f)
		}
		set(currentItemTop) {
			super.currentItemTop = currentItemTop
		}

	override fun setTranslationY(translationY: Float) {
		super.setTranslationY(translationY)
		parentAlert.sheetContainer.invalidate()
	}

	override val listTopPadding: Int
		get() = listView.paddingTop

	override val firstOffset: Int
		get() = listTopPadding + AndroidUtilities.dp(5f)

	override fun onPreMeasure(availableWidth: Int, availableHeight: Int) {
		var padding: Int

		if (parentAlert.actionBar.isSearchFieldVisible || parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20f)) {
			padding = AndroidUtilities.dp(56f)
			parentAlert.setAllowNestedScroll(false)
		}
		else {
			padding = if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
				(availableHeight / 3.5f).toInt()
			}
			else {
				availableHeight / 5 * 2
			}

			padding -= AndroidUtilities.dp(1f)

			if (padding < 0) {
				padding = 0
			}

			parentAlert.setAllowNestedScroll(true)
		}

		if (listView.paddingTop != padding) {
			ignoreLayout = true
			listView.setPadding(0, padding, 0, AndroidUtilities.dp(48f))
			ignoreLayout = false
		}

		val layoutParams = filtersView.layoutParams as? LayoutParams
		layoutParams?.topMargin = ActionBar.getCurrentActionBarHeight()
	}

	override val buttonsHideOffset: Int
		get() = AndroidUtilities.dp(62f)

	override fun requestLayout() {
		if (ignoreLayout) {
			return
		}

		super.requestLayout()
	}

	override fun scrollToTop() {
		listView.smoothScrollToPosition(0)
	}

	override val selectedItemsCount: Int
		get() = selectedFiles.size + selectedMessages.size

	override fun sendSelectedItems(notify: Boolean, scheduleDate: Int) {
		if (selectedFiles.isEmpty() && selectedMessages.isEmpty() || delegate == null || sendPressed) {
			return
		}

		sendPressed = true

		val fmessages = mutableListOf<MessageObject>()
		val idIterator: Iterator<MessageHashId> = selectedMessages.keys.iterator()

		while (idIterator.hasNext()) {
			val hashId = idIterator.next()

			selectedMessages[hashId]?.let {
				fmessages.add(it)
			}
		}

		val files = selectedFilesOrder.toList()

		delegate?.didSelectFiles(files, parentAlert.commentTextView.text?.toString(), fmessages, notify, scheduleDate)

		parentAlert.dismiss(true)
	}

	private fun onItemClick(view: View, `object`: Any?): Boolean {
		val add: Boolean

		if (`object` is ListItem) {
			val file = `object`.file ?: return false

			if (file.isDirectory) {
				return false
			}

			val path = file.absolutePath

			if (selectedFiles.containsKey(path)) {
				selectedFiles.remove(path)
				selectedFilesOrder.remove(path)
				add = false
			}
			else {
				if (!file.canRead()) {
					showErrorBox(context.getString(R.string.AccessError))
					return false
				}

				if (canSelectOnlyImageFiles && `object`.thumb == null) {
					showErrorBox(LocaleController.formatString("PassportUploadNotImage", R.string.PassportUploadNotImage))
					return false
				}

				// if (file.length() > FileLoader.DEFAULT_MAX_FILE_SIZE && !UserConfig.getInstance(UserConfig.selectedAccount).isPremium || file.length() > FileLoader.DEFAULT_MAX_FILE_SIZE_PREMIUM) {
				//	val limitReachedBottomSheet = LimitReachedBottomSheet(parentAlert.baseFragment, LimitReachedBottomSheet.TYPE_LARGE_FILE, UserConfig.selectedAccount)
				//	limitReachedBottomSheet.setVeryLargeFile(true)
				//	limitReachedBottomSheet.show()
				//	return false
				//}

				if (file.length() > FileLoader.DEFAULT_MAX_FILE_SIZE) {
					parentAlert.baseFragment.parentActivity?.let {
						AlertsCreator.createTooLargeFileDialog(it, file.length()).show()
					}

					return false
				}

				if (maxSelectedFiles >= 0 && selectedFiles.size >= maxSelectedFiles) {
					showErrorBox(LocaleController.formatString("PassportUploadMaxReached", R.string.PassportUploadMaxReached, LocaleController.formatPluralString("Files", maxSelectedFiles)))
					return false
				}

				if (isSoundPicker && !isRingtone(file)) {
					return false
				}

				if (file.length() == 0L) {
					return false
				}

				selectedFiles[path] = `object`
				selectedFilesOrder.add(path)

				add = true
			}

			scrolling = false
		}
		else if (`object` is MessageObject) {
			val hashId = MessageHashId(`object`.id, `object`.dialogId)

			if (selectedMessages.containsKey(hashId)) {
				selectedMessages.remove(hashId)
				add = false
			}
			else {
				if (selectedMessages.size >= 100) {
					return false
				}

				selectedMessages[hashId] = `object`

				add = true
			}
		}
		else {
			return false
		}

		if (view is SharedDocumentCell) {
			view.setChecked(add, true)
		}

		parentAlert.updateCountButton(if (add) 1 else 2)

		return true
	}

	fun isRingtone(file: File): Boolean {
		var mimeType: String? = null
		val extension = FileLoader.getFileExtension(file)

		if (extension.isNotEmpty()) {
			mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
		}

		if (file.length() == 0L || mimeType == null || !RingtoneDataStore.ringtoneSupportedMimeType.contains(mimeType)) {
			BulletinFactory.of(parentAlert.container).createErrorBulletinSubtitle(LocaleController.formatString("InvalidFormatError", R.string.InvalidFormatError), LocaleController.formatString("ErrorInvalidRingtone", R.string.ErrorRingtoneInvalidFormat)).show()
			return false
		}
		if (file.length() > MessagesController.getInstance(UserConfig.selectedAccount).ringtoneSizeMax) {
			BulletinFactory.of(parentAlert.container).createErrorBulletinSubtitle(LocaleController.formatString("TooLargeError", R.string.TooLargeError), LocaleController.formatString("ErrorRingtoneSizeTooBig", R.string.ErrorRingtoneSizeTooBig, MessagesController.getInstance(UserConfig.selectedAccount).ringtoneSizeMax / 1024)).show()
			return false
		}

		val millSecond = try {
			val mmr = MediaMetadataRetriever()
			mmr.setDataSource(ApplicationLoader.applicationContext, Uri.fromFile(file))
			val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
			durationStr?.toInt()
		}
		catch (e: Exception) {
			Int.MAX_VALUE
		} ?: Int.MAX_VALUE

		if (millSecond > MessagesController.getInstance(UserConfig.selectedAccount).ringtoneDurationMax * 1000) {
			BulletinFactory.of(parentAlert.container).createErrorBulletinSubtitle(LocaleController.formatString("TooLongError", R.string.TooLongError), LocaleController.formatString("ErrorRingtoneDurationTooLong", R.string.ErrorRingtoneDurationTooLong, MessagesController.getInstance(UserConfig.selectedAccount).ringtoneDurationMax)).show()
			return false
		}

		return true
	}

	fun setMaxSelectedFiles(value: Int) {
		maxSelectedFiles = value
	}

	fun setCanSelectOnlyImageFiles(value: Boolean) {
		canSelectOnlyImageFiles = value
	}

	private fun sendSelectedPhotos(photos: Map<Any, Any>, order: List<Any>, notify: Boolean, scheduleDate: Int) {
		if (photos.isEmpty() || delegate == null || sendPressed) {
			return
		}

		sendPressed = true

		val media = mutableListOf<SendingMediaInfo>()

		for (a in order.indices) {
			val `object` = photos[order[a]]
			val info = SendingMediaInfo()

			media.add(info)

			if (`object` is PhotoEntry) {
				if (`object`.imagePath != null) {
					info.path = `object`.imagePath
				}
				else {
					info.path = `object`.path
				}

				info.thumbPath = `object`.thumbPath
				info.videoEditedInfo = `object`.editedInfo
				info.isVideo = `object`.isVideo
				info.caption = `object`.caption?.toString()
				info.entities = `object`.entities
				info.masks = `object`.stickers
				info.ttl = `object`.ttl
			}
		}

		delegate?.didSelectPhotos(media, notify, scheduleDate)
	}

	private fun loadRecentFiles() {
		try {
			if (isSoundPicker) {
				val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE)

				try {
					ApplicationLoader.applicationContext.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.DATE_ADDED + " DESC")?.use { cursor ->
						while (cursor.moveToNext()) {
							val file = File(cursor.getString(1))
							val duration = cursor.getLong(2)
							val fileSize = cursor.getLong(3)
							val mimeType = cursor.getString(4)

							if (duration > MessagesController.getInstance(UserConfig.selectedAccount).ringtoneDurationMax * 1000 || fileSize > MessagesController.getInstance(UserConfig.selectedAccount).ringtoneSizeMax || !mimeType.isNullOrEmpty() && !("audio/mpeg" == mimeType || "audio/mpeg4" != mimeType)) {
								continue
							}

							val item = ListItem()
							item.title = file.name
							item.file = file

							var fname = file.name

							val sp = fname.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

							item.ext = if (sp.size > 1) sp[sp.size - 1] else "?"
							item.subtitle = AndroidUtilities.formatFileSize(file.length())

							fname = fname.lowercase()

							if (fname.endsWith(".jpg") || fname.endsWith(".png") || fname.endsWith(".gif") || fname.endsWith(".jpeg")) {
								item.thumb = file.absolutePath
							}

							listAdapter.recentItems.add(item)
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
			else {
				checkDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
				sortRecentItems()
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private fun checkDirectory(rootDir: File) {
		val files = rootDir.listFiles()

		if (files.isNullOrEmpty()) {
			return
		}

		for (a in files.indices) {
			val file = files[a]

			if (file.isDirectory && file.name == "Ello") {
				checkDirectory(file)
				continue
			}

			val item = ListItem()
			item.title = file.name
			item.file = file

			var fname = file.name
			val sp = fname.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

			item.ext = if (sp.size > 1) sp[sp.size - 1] else "?"
			item.subtitle = AndroidUtilities.formatFileSize(file.length())
			fname = fname.lowercase()

			if (fname.endsWith(".jpg") || fname.endsWith(".png") || fname.endsWith(".gif") || fname.endsWith(".jpeg")) {
				item.thumb = file.absolutePath
			}

			listAdapter.recentItems.add(item)
		}
	}

	private fun sortRecentItems() {
		listAdapter.recentItems.sortWith { o1, o2 ->
			if (sortByName) {
				val lm = o1.file?.name
				val rm = o2.file?.name
				return@sortWith lm?.compareTo(rm ?: "", ignoreCase = true) ?: 0
			}
			else {
				val lm = o1.file?.lastModified() ?: 0L
				val rm = o2.file?.lastModified() ?: 0L

				if (lm == rm) {
					return@sortWith 0
				}
				else if (lm > rm) {
					return@sortWith -1
				}
				else {
					return@sortWith 1
				}
			}
		}
	}

	private fun sortFileItems() {
		if (currentDir == null) {
			return
		}

		listAdapter.items.sortWith { lhs, rhs ->
			if (lhs.file == null) {
				return@sortWith -1
			}
			else if (rhs.file == null) {
				return@sortWith 1
			}

			val isDir1 = lhs.file?.isDirectory ?: false
			val isDir2 = rhs.file?.isDirectory ?: false

			if (isDir1 != isDir2) {
				return@sortWith if (isDir1) -1 else 1
			}
			else if (isDir1 || sortByName) {
				return@sortWith lhs.file?.name?.compareTo(rhs.file?.name ?: "", ignoreCase = true) ?: 0
			}
			else {
				val lm = lhs.file?.lastModified() ?: 0L
				val rm = rhs.file?.lastModified() ?: 0L

				if (lm == rm) {
					return@sortWith 0
				}
				else if (lm > rm) {
					return@sortWith -1
				}
				else {
					return@sortWith 1
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		listAdapter.notifyDataSetChanged()
		searchAdapter.notifyDataSetChanged()
	}

	override fun onShow(previousLayout: AttachAlertLayout?) {
		selectedFiles.clear()
		selectedMessages.clear()
		searchAdapter.currentSearchFilters.clear()
		selectedFilesOrder.clear()
		listAdapter.history.clear()
		listRoots()
		updateSearchButton()
		updateEmptyView()
		parentAlert.actionBar.setTitle(context.getString(R.string.SelectFile))
		sortItem?.visibility = VISIBLE
		layoutManager.scrollToPositionWithOffset(0, 0)
	}

	override fun onHide() {
		sortItem?.visibility = GONE
		searchItem?.visibility = GONE
	}

	private fun updateEmptyViewPosition() {
		if (emptyView.visibility != VISIBLE) {
			return
		}

		val child = listView.getChildAt(0) ?: return
		val oldTranslation = emptyView.translationY

		additionalTranslationY = ((emptyView.measuredHeight - measuredHeight + child.top) / 2).toFloat()
		emptyView.translationY = oldTranslation
	}

	private fun updateEmptyView() {
		val visible = if (listView.adapter === searchAdapter) {
			searchAdapter.searchResult.isEmpty() && searchAdapter.sections.isEmpty()
		}
		else {
			listAdapter.itemCount == 1
		}

		emptyView.visibility = if (visible) VISIBLE else GONE

		updateEmptyViewPosition()
	}

	private fun updateSearchButton() {
		val searchItem = searchItem ?: return

		if (!searchItem.isSearchFieldVisible) {
			searchItem.visibility = if (hasFiles || listAdapter.history.isEmpty()) VISIBLE else GONE
		}
	}

	private val topForScroll: Int
		get() {
			val child = listView.getChildAt(0)
			val holder = listView.findContainingViewHolder(child)
			var top = -listView.paddingTop

			if (holder != null && holder.adapterPosition == 0) {
				top += child.top
			}

			return top
		}

	private fun canClosePicker(): Boolean {
		if (listAdapter.history.size > 0) {
			prepareAnimation()

			val he = listAdapter.history.removeAt(listAdapter.history.size - 1)

			parentAlert.actionBar.setTitle(he.title)

			val top = topForScroll

			if (he.dir != null) {
				listFiles(he.dir!!)
			}
			else {
				listRoots()
			}

			updateSearchButton()
			layoutManager.scrollToPositionWithOffset(0, top)
			runAnimation(ANIMATION_BACKWARD)

			return false
		}

		return true
	}

	override fun onBackPressed(): Boolean {
		return if (!canClosePicker()) {
			true
		}
		else {
			super.onBackPressed()
		}
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		updateEmptyViewPosition()
	}

	fun setDelegate(documentSelectActivityDelegate: DocumentSelectActivityDelegate?) {
		delegate = documentSelectActivityDelegate
	}

	private fun listFiles(dir: File): Boolean {
		hasFiles = false

		if (!dir.canRead()) {
			if (dir.absolutePath.startsWith(Environment.getExternalStorageDirectory().toString()) || dir.absolutePath.startsWith("/sdcard") || dir.absolutePath.startsWith("/mnt/sdcard")) {
				if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED && Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED_READ_ONLY) {
					currentDir = dir
					listAdapter.items.clear()
					AndroidUtilities.clearDrawableAnimation(listView)
					scrolling = true
					listAdapter.notifyDataSetChanged()
					return true
				}
			}

			showErrorBox(context.getString(R.string.AccessError))

			return false
		}

		val files = try {
			dir.listFiles()
		}
		catch (e: Exception) {
			showErrorBox(e.localizedMessage)
			return false
		}

		if (files == null) {
			showErrorBox(context.getString(R.string.UnknownError))
			return false
		}

		currentDir = dir
		listAdapter.items.clear()

		for (a in files.indices) {
			val file = files[a]

			if (file.name.indexOf('.') == 0) {
				continue
			}

			val item = ListItem()
			item.title = file.name
			item.file = file

			if (file.isDirectory) {
				item.icon = R.drawable.files_folder
				item.subtitle = context.getString(R.string.Folder)
			}
			else {
				hasFiles = true

				var fname = file.name
				val sp = fname.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

				item.ext = if (sp.size > 1) sp[sp.size - 1] else "?"
				item.subtitle = AndroidUtilities.formatFileSize(file.length())

				fname = fname.lowercase()

				if (fname.endsWith(".jpg") || fname.endsWith(".png") || fname.endsWith(".gif") || fname.endsWith(".jpeg")) {
					item.thumb = file.absolutePath
				}
			}

			listAdapter.items.add(item)
		}

		val item = ListItem()
		item.title = ".."

		if (listAdapter.history.size > 0) {
			val entry = listAdapter.history[listAdapter.history.size - 1]

			if (entry.dir == null) {
				item.subtitle = context.getString(R.string.Folder)
			}
			else {
				item.subtitle = entry.dir.toString()
			}
		}
		else {
			item.subtitle = context.getString(R.string.Folder)
		}

		item.icon = R.drawable.files_folder
		item.file = null

		listAdapter.items.add(0, item)

		sortFileItems()
		updateSearchButton()
		AndroidUtilities.clearDrawableAnimation(listView)

		scrolling = true

		val top = topForScroll

		listAdapter.notifyDataSetChanged()

		layoutManager.scrollToPositionWithOffset(0, top)

		return true
	}

	private fun showErrorBox(error: String?) {
		AlertDialog.Builder(context).setTitle(context.getString(R.string.AppName)).setMessage(error).setPositiveButton(context.getString(R.string.OK), null).show()
	}

	private fun listRoots() {
		currentDir = null
		hasFiles = false
		listAdapter.items.clear()

		val paths = mutableSetOf<String>()
		val defaultPath = Environment.getExternalStorageDirectory().path
		val defaultPathState = Environment.getExternalStorageState()

		if (defaultPathState == Environment.MEDIA_MOUNTED || defaultPathState == Environment.MEDIA_MOUNTED_READ_ONLY) {
			val ext = ListItem()

			if (Environment.isExternalStorageRemovable()) {
				ext.title = context.getString(R.string.SdCard)
				ext.icon = R.drawable.files_internal
				ext.subtitle = context.getString(R.string.ExternalFolderInfo)
			}
			else {
				ext.title = context.getString(R.string.InternalStorage)
				ext.icon = R.drawable.files_storage
				ext.subtitle = context.getString(R.string.InternalFolderInfo)
			}

			ext.file = Environment.getExternalStorageDirectory()
			listAdapter.items.add(ext)
			paths.add(defaultPath)
		}

		runCatching {
			BufferedReader(FileReader("/proc/mounts")).use { bufferedReader ->
				var line: String

				while ((bufferedReader.readLine().also { line = it }) != null) {
					if (line.contains("vfat") || line.contains("/mnt")) {
						FileLog.d(line)

						val tokens = StringTokenizer(line, " ")
						val unused = tokens.nextToken()
						var path = tokens.nextToken()

						if (paths.contains(path)) {
							continue
						}

						if (line.contains("/dev/block/vold")) {
							if (!line.contains("/mnt/secure") && !line.contains("/mnt/asec") && !line.contains("/mnt/obb") && !line.contains("/dev/mapper") && !line.contains("tmpfs")) {
								if (!File(path).isDirectory) {
									val index = path.lastIndexOf('/')

									if (index != -1) {
										val newPath = "/storage/" + path.substring(index + 1)

										if (File(newPath).isDirectory) {
											path = newPath
										}
									}
								}

								paths.add(path)

								runCatching {
									val item = ListItem()

									if (path.lowercase().contains("sd")) {
										item.title = context.getString(R.string.SdCard)
									}
									else {
										item.title = context.getString(R.string.ExternalStorage)
									}

									item.subtitle = context.getString(R.string.ExternalFolderInfo)
									item.icon = R.drawable.files_internal
									item.file = File(path)

									listAdapter.items.add(item)
								}.onFailure {
									FileLog.e(it)
								}
							}
						}
					}
				}
			}
		}.onFailure {
			FileLog.e(it)
		}

		var fs: ListItem

		runCatching {
			val telegramPath = File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "Ello")

			if (telegramPath.exists()) {
				fs = ListItem()
				fs.title = "Ello"
				fs.subtitle = context.getString(R.string.AppFolderInfo)
				fs.icon = R.drawable.files_folder
				fs.file = telegramPath

				listAdapter.items.add(fs)
			}
		}.onFailure {
			FileLog.e(it)
		}

		if (!isSoundPicker) {
			fs = ListItem()
			fs.title = context.getString(R.string.Gallery)
			fs.subtitle = context.getString(R.string.GalleryInfo)
			fs.icon = R.drawable.files_gallery
			fs.file = null

			listAdapter.items.add(fs)
		}

		if (allowMusic) {
			fs = ListItem()
			fs.title = context.getString(R.string.AttachMusic)
			fs.subtitle = context.getString(R.string.MusicInfo)
			fs.icon = R.drawable.files_music
			fs.file = null

			listAdapter.items.add(fs)
		}

		if (listAdapter.recentItems.isNotEmpty()) {
			hasFiles = true
		}

		AndroidUtilities.clearDrawableAnimation(listView)

		scrolling = true

		listAdapter.notifyDataSetChanged()
	}

//	private fun getRootSubtitle(path: String): String {
//		try {
//			val stat = StatFs(path)
//			val total = stat.blockCountLong * stat.blockSizeLong
//			val free = stat.availableBlocksLong * stat.blockSizeLong
//
//			if (total == 0L) {
//				return ""
//			}
//
//			return LocaleController.formatString("FreeOfTotal", R.string.FreeOfTotal, AndroidUtilities.formatFileSize(free), AndroidUtilities.formatFileSize(total))
//		}
//		catch (e: Exception) {
//			FileLog.e(e)
//		}
//
//		return path
//	}

	interface DocumentSelectActivityDelegate {
		fun didSelectFiles(files: List<String>?, caption: String?, fmessages: List<MessageObject>?, notify: Boolean, scheduleDate: Int)
		fun didSelectPhotos(photos: List<SendingMediaInfo>?, notify: Boolean, scheduleDate: Int) {}
		fun startDocumentSelectActivity() {}
		fun startMusicSelectActivity() {}
	}

	class ListItem {
		var icon = 0
		var title: String? = null
		var subtitle = ""
		var ext = ""
		var thumb: String? = null
		var file: File? = null
	}

	private class HistoryEntry {
		var scrollItem = 0
		var scrollOffset = 0
		var dir: File? = null
		var title: String? = null
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		val items = mutableListOf<ListItem>()
		val history = mutableListOf<HistoryEntry>()
		val recentItems = mutableListOf<ListItem>()

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType == 1
		}

		override fun getItemCount(): Int {
			var count = items.size

			if (history.isEmpty() && recentItems.isNotEmpty()) {
				count += recentItems.size + 2
			}

			return count + 1
		}

		fun getItem(position: Int): ListItem? {
			@Suppress("NAME_SHADOWING") var position = position

			val itemsSize = items.size

			if (position < itemsSize) {
				return items[position]
			}
			else if (history.isEmpty() && recentItems.isNotEmpty() && position != itemsSize && position != itemsSize + 1) {
				position -= items.size + 2

				if (position < recentItems.size) {
					return recentItems[position]
				}
			}

			return null
		}

		override fun getItemViewType(position: Int): Int {
			if (position == itemCount - 1) {
				return 3
			}
			else {
				val itemsSize = items.size

				if (position == itemsSize) {
					return 2
				}
				else if (position == itemsSize + 1) {
					return 0
				}
			}

			return 1
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = HeaderCell(mContext)
				}

				1 -> {
					view = SharedDocumentCell(mContext, SharedDocumentCell.VIEW_TYPE_PICKER)
				}

				2 -> {
					view = ShadowSectionCell(mContext)
					val drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow)
					val combinedDrawable = CombinedDrawable(ResourcesCompat.getColor(resources, R.color.light_background, null).toDrawable(), drawable)
					combinedDrawable.setFullSize(true)
					view.setBackground(combinedDrawable)
				}

				3 -> {
					view = View(mContext)
				}

				else -> {
					view = View(mContext)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				0 -> {
					val headerCell = holder.itemView as HeaderCell

					if (sortByName) {
						headerCell.setText(context.getString(R.string.RecentFilesAZ))
					}
					else {
						headerCell.setText(context.getString(R.string.RecentFiles))
					}
				}

				1 -> {
					val item = getItem(position)
					val documentCell = holder.itemView as SharedDocumentCell

					if (item?.icon != 0) {
						documentCell.setTextAndValueAndTypeAndThumb(item?.title, item?.subtitle, null, null, item?.icon ?: 0, position != items.size - 1)
					}
					else {
						val type = item.ext.uppercase().substring(0, min(item.ext.length, 4))
						documentCell.setTextAndValueAndTypeAndThumb(item.title, item.subtitle, type, item.thumb, 0, false)
					}

					if (item?.file != null) {
						documentCell.setChecked(selectedFiles.containsKey(item.file.toString()), !scrolling)
					}
					else {
						documentCell.setChecked(false, !scrolling)
					}
				}
			}
		}

		override fun notifyDataSetChanged() {
			super.notifyDataSetChanged()
			updateEmptyView()
		}
	}

	inner class SearchAdapter(private val mContext: Context) : SectionsAdapter() {
		private val localTipChats = mutableListOf<Any>()
		private val localTipDates = mutableListOf<DateData>()
		private val messageHashIdTmp = MessageHashId(0, 0)
		private var animationIndex = -1
		private var currentDataQuery: String? = null
		private var currentSearchDialogId: Long = 0
		private var currentSearchFilter: MediaFilterData? = null
		private var currentSearchMaxDate: Long = 0
		private var currentSearchMinDate: Long = 0
		private var firstLoading = true
		private var lastMessagesSearchString: String? = null
		private var lastSearchFilterQueryString: String? = null
		private var localSearchRunnable: Runnable? = null
		private var messagesById = SparseArray<MessageObject>()
		private var nextSearchRate = 0
		private var requestIndex = 0
		private var searchRunnable: Runnable? = null
		private var sectionArrays = mutableMapOf<String, MutableList<MessageObject>>()
		val currentSearchFilters = mutableListOf<MediaFilterData>()
		var endReached = false
		var isLoading = false
		var messages = mutableListOf<MessageObject>()
		var searchResult = mutableListOf<ListItem>()
		var sections = mutableListOf<String>()

		private val clearCurrentResultsRunnable = Runnable {
			if (isLoading) {
				messages.clear()
				sections.clear()
				sectionArrays.clear()
				notifyDataSetChanged()
			}
		}

		fun search(query: String?, reset: Boolean) {
			if (localSearchRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(localSearchRunnable)
				localSearchRunnable = null
			}

			if (query.isNullOrEmpty()) {
				if (searchResult.isNotEmpty()) {
					searchResult.clear()
				}

				if (listView.adapter !== listAdapter) {
					listView.adapter = listAdapter
				}

				notifyDataSetChanged()
			}
			else {
				AndroidUtilities.runOnUIThread(Runnable {
					val copy = listAdapter.items.toMutableList()

					if (listAdapter.history.isEmpty()) {
						copy.addAll(0, listAdapter.recentItems)
					}

					val hasFilters = currentSearchFilters.isNotEmpty()

					Utilities.searchQueue.postRunnable {
						val search1 = query.trim().lowercase()

						if (search1.isEmpty()) {
							updateSearchResults(mutableListOf())
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

						val resultArray = mutableListOf<ListItem>()

						if (!hasFilters) {
							for (a in copy.indices) {
								val entry = copy[a]

								if (entry.file == null || entry.file?.isDirectory == true) {
									continue
								}

								for (b in search.indices) {
									val q = search[b]

									if (!q.isNullOrEmpty()) {
										val ok = entry.title?.lowercase()?.contains(q) == true

										if (ok) {
											resultArray.add(entry)
											break
										}
									}
								}
							}
						}
						updateSearchResults(resultArray)
					}
				}.also { localSearchRunnable = it }, 300)
			}

			if (!canSelectOnlyImageFiles && listAdapter.history.isEmpty()) {
				var dialogId: Long = 0
				var minDate: Long = 0
				var maxDate: Long = 0

				for (i in currentSearchFilters.indices) {
					val data = currentSearchFilters[i]

					if (data.filterType == FiltersView.FILTER_TYPE_CHAT) {
						if (data.chat is User) {
							dialogId = (data.chat as User).id
						}
						else if (data.chat is Chat) {
							dialogId = -(data.chat as Chat).id
						}
					}
					else if (data.filterType == FiltersView.FILTER_TYPE_DATE) {
						minDate = data.dateData.minDate
						maxDate = data.dateData.maxDate
					}
				}

				searchGlobal(dialogId, minDate, maxDate, FiltersView.filters[2], query, reset)
			}
		}

		fun loadMore() {
			if (searchAdapter.isLoading || searchAdapter.endReached || currentSearchFilter == null) {
				return
			}

			searchGlobal(currentSearchDialogId, currentSearchMinDate, currentSearchMaxDate, currentSearchFilter!!, lastMessagesSearchString, false)
		}

		fun removeSearchFilter(filterData: MediaFilterData) {
			currentSearchFilters.remove(filterData)
		}

		fun clear() {
			currentSearchFilters.clear()
		}

		fun addSearchFilter(filter: MediaFilterData) {
			if (currentSearchFilters.isNotEmpty()) {
				for (i in currentSearchFilters.indices) {
					if (filter.isSameType(currentSearchFilters[i])) {
						return
					}
				}
			}

			currentSearchFilters.add(filter)

			parentAlert.actionBar.setSearchFilter(filter)
			parentAlert.actionBar.setSearchFieldText("")

			updateFiltersView(true, null, null, true)
		}

		fun updateFiltersView(showMediaFilters: Boolean, users: List<Any>?, dates: List<DateData>?, animated: Boolean) {
			var hasMediaFilter = false
			var hasUserFilter = false
			var hasDataFilter = false

			for (i in currentSearchFilters.indices) {
				if (currentSearchFilters[i].isMedia) {
					hasMediaFilter = true
				}
				else if (currentSearchFilters[i].filterType == FiltersView.FILTER_TYPE_CHAT) {
					hasUserFilter = true
				}
				else if (currentSearchFilters[i].filterType == FiltersView.FILTER_TYPE_DATE) {
					hasDataFilter = true
				}
			}

			var visible = false
			val hasUsersOrDates = !users.isNullOrEmpty() || !dates.isNullOrEmpty()

			if (!hasMediaFilter && !hasUsersOrDates && showMediaFilters) {
				// unused
			}
			else if (hasUsersOrDates) {
				val finalUsers = if (!users.isNullOrEmpty() && !hasUserFilter) users else null
				val finalDates = if (!dates.isNullOrEmpty() && !hasDataFilter) dates else null

				if (finalUsers != null || finalDates != null) {
					visible = true
					filtersView.setUsersAndDates(finalUsers, finalDates, false)
				}
			}

			if (!visible) {
				filtersView.setUsersAndDates(null, null, false)
			}

			filtersView.isEnabled = visible

			if (visible && filtersView.tag != null || !visible && filtersView.tag == null) {
				return
			}

			filtersView.tag = if (visible) 1 else null

			filtersViewAnimator?.cancel()

			if (animated) {
				if (visible) {
					filtersView.visibility = VISIBLE
				}

				filtersViewAnimator = AnimatorSet()
				filtersViewAnimator?.playTogether(ObjectAnimator.ofFloat(listView, TRANSLATION_Y, (if (visible) AndroidUtilities.dp(44f) else 0).toFloat()), ObjectAnimator.ofFloat(filtersView, TRANSLATION_Y, (if (visible) 0 else -AndroidUtilities.dp(44f)).toFloat()), ObjectAnimator.ofFloat(loadingView, TRANSLATION_Y, (if (visible) AndroidUtilities.dp(44f) else 0).toFloat()), ObjectAnimator.ofFloat(emptyView, TRANSLATION_Y, (if (visible) AndroidUtilities.dp(44f) else 0).toFloat()))

				filtersViewAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (filtersView.tag == null) {
							filtersView.visibility = INVISIBLE
						}

						filtersViewAnimator = null
					}
				})

				filtersViewAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT
				filtersViewAnimator?.duration = 180
				filtersViewAnimator?.start()
			}
			else {
				filtersView.adapter?.notifyDataSetChanged()
				listView.translationY = (if (visible) AndroidUtilities.dp(44f) else 0).toFloat()
				filtersView.translationY = (if (visible) 0 else -AndroidUtilities.dp(44f)).toFloat()
				loadingView.translationY = (if (visible) AndroidUtilities.dp(44f) else 0).toFloat()
				emptyView.translationY = (if (visible) AndroidUtilities.dp(44f) else 0).toFloat()
				filtersView.visibility = if (visible) VISIBLE else INVISIBLE
			}
		}

		private fun searchGlobal(dialogId: Long, minDate: Long, maxDate: Long, searchFilter: MediaFilterData, query: String?, clearOldResults: Boolean) {
			val currentSearchFilterQueryString = String.format(Locale.ENGLISH, "%d%d%d%d%s", dialogId, minDate, maxDate, searchFilter.filterType, query)
			val filterAndQueryIsSame = lastSearchFilterQueryString != null && lastSearchFilterQueryString == currentSearchFilterQueryString
			val forceClear = !filterAndQueryIsSame && clearOldResults
			// val filterIsSame = dialogId == currentSearchDialogId && currentSearchMinDate == minDate && currentSearchMaxDate == maxDate

			currentSearchFilter = searchFilter
			currentSearchDialogId = dialogId
			currentSearchMinDate = minDate
			currentSearchMaxDate = maxDate

			if (searchRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(searchRunnable)
			}

			AndroidUtilities.cancelRunOnUIThread(clearCurrentResultsRunnable)

			if (filterAndQueryIsSame && clearOldResults) {
				return
			}

			if (forceClear) {
				messages.clear()
				sections.clear()
				sectionArrays.clear()
				isLoading = true
				emptyView.visibility = VISIBLE
				notifyDataSetChanged()
				requestIndex++
				firstLoading = true
				listView.pinnedHeader?.alpha = 0f

				localTipChats.clear()
				localTipDates.clear()
			}

			isLoading = true
			notifyDataSetChanged()

			if (!filterAndQueryIsSame) {
				clearCurrentResultsRunnable.run()
				emptyView.showProgress(true, !clearOldResults)
			}

			if (query.isNullOrEmpty()) {
				localTipDates.clear()
				localTipChats.clear()
				updateFiltersView(false, null, null, true)
				return
			}

			requestIndex++

			val requestId = requestIndex
			val accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount)

			AndroidUtilities.runOnUIThread(Runnable {
				val request: TLObject
				var resultArray: ArrayList<Any>? = null

				if (dialogId != 0L) {
					val req = TLRPC.TLMessagesSearch()
					req.q = query
					req.limit = 20
					req.filter = currentSearchFilter?.filter
					req.peer = accountInstance.messagesController.getInputPeer(dialogId)

					if (minDate > 0) {
						req.minDate = (minDate / 1000).toInt()
					}

					if (maxDate > 0) {
						req.maxDate = (maxDate / 1000).toInt()
					}

					if (filterAndQueryIsSame && query == lastMessagesSearchString && messages.isNotEmpty()) {
						val lastMessage = messages[messages.size - 1]
						req.offsetId = lastMessage.id
					}
					else {
						req.offsetId = 0
					}

					request = req
				}
				else {
					if (query.isNotEmpty()) {
						resultArray = ArrayList()
						val resultArrayNames = ArrayList<CharSequence>()
						val encUsers = ArrayList<User>()
						accountInstance.messagesStorage.localSearch(0, query, resultArray, resultArrayNames, encUsers, -1)
					}

					val req = TLRPC.TLMessagesSearchGlobal()
					req.limit = 20
					req.q = query
					req.filter = currentSearchFilter?.filter

					if (minDate > 0) {
						req.minDate = (minDate / 1000).toInt()
					}

					if (maxDate > 0) {
						req.maxDate = (maxDate / 1000).toInt()
					}

					if (filterAndQueryIsSame && query == lastMessagesSearchString && messages.isNotEmpty()) {
						val lastMessage = messages[messages.size - 1]
						req.offsetId = lastMessage.id
						req.offsetRate = nextSearchRate

						val id = if (lastMessage.messageOwner?.peerId?.channelId != 0L) {
							-lastMessage.messageOwner!!.peerId!!.channelId
						}
						else if (lastMessage.messageOwner?.peerId?.chatId != 0L) {
							-lastMessage.messageOwner!!.peerId!!.chatId
						}
						else {
							lastMessage.messageOwner!!.peerId!!.userId
						}

						req.offsetPeer = accountInstance.messagesController.getInputPeer(id)
					}
					else {
						req.offsetRate = 0
						req.offsetId = 0
						req.offsetPeer = TLRPC.TLInputPeerEmpty()
					}

					request = req
				}

				lastMessagesSearchString = query
				lastSearchFilterQueryString = currentSearchFilterQueryString

				val finalResultArray = resultArray
				val dateData = ArrayList<DateData>()

				FiltersView.fillTipDates(lastMessagesSearchString, dateData)

				accountInstance.connectionsManager.sendRequest(request) { response, error ->
					val messageObjects = mutableListOf<MessageObject>()

					if (error == null) {
						val res = response as? TLRPC.MessagesMessages
						val n = res?.messages?.size ?: 0

						for (i in 0 until n) {
							val message = res?.messages?.get(i)

							if (message != null) {
								val messageObject = MessageObject(accountInstance.currentAccount, message, generateLayout = false, checkMediaExists = true)
								messageObject.setQuery(query)
								messageObjects.add(messageObject)
							}
						}
					}

					AndroidUtilities.runOnUIThread {
						if (requestId != requestIndex) {
							return@runOnUIThread
						}

						isLoading = false

						if (error != null) {
							emptyView.title.text = context.getString(R.string.NoResult)
							emptyView.subtitle.visibility = VISIBLE
							emptyView.subtitle.text = context.getString(R.string.SearchEmptyViewFilteredSubtitle2)
							emptyView.showProgress(show = false, animated = true)
							return@runOnUIThread
						}

						emptyView.showProgress(false)

						val res = response as? TLRPC.MessagesMessages

						nextSearchRate = (res as? TLRPC.TLMessagesMessagesSlice)?.nextRate ?: 0

						accountInstance.messagesStorage.putUsersAndChats(res?.users, res?.chats, true, true)
						accountInstance.messagesController.putUsers(res?.users, false)
						accountInstance.messagesController.putChats(res?.chats, false)

						if (!filterAndQueryIsSame) {
							messages.clear()
							messagesById.clear()
							sections.clear()
							sectionArrays.clear()
						}

						var totalCount = res?.count ?: 0

						currentDataQuery = query

						val n = messageObjects.size

						for (i in 0 until n) {
							val messageObject = messageObjects[i]
							var messageObjectsByDate = sectionArrays[messageObject.monthKey]

							if (messageObjectsByDate == null) {
								messageObjectsByDate = mutableListOf()
								sectionArrays[messageObject.monthKey!!] = messageObjectsByDate
								sections.add(messageObject.monthKey!!)
							}

							messageObjectsByDate.add(messageObject)
							messages.add(messageObject)
							messagesById.put(messageObject.id, messageObject)
						}

						if (messages.size > totalCount) {
							totalCount = messages.size
						}

						endReached = messages.size >= totalCount

						if (messages.isEmpty()) {
							if (currentDataQuery.isNullOrEmpty() && dialogId == 0L && minDate == 0L) {
								emptyView.title.text = context.getString(R.string.NoResult)
								emptyView.subtitle.visibility = VISIBLE
								emptyView.subtitle.text = context.getString(R.string.SearchEmptyViewFilteredSubtitleFiles)
							}
							else {
								emptyView.title.text = context.getString(R.string.NoResult)
								emptyView.subtitle.visibility = VISIBLE
								emptyView.subtitle.text = context.getString(R.string.SearchEmptyViewFilteredSubtitle2)
							}
						}

						if (!filterAndQueryIsSame) {
							localTipChats.clear()

							if (finalResultArray != null) {
								localTipChats.addAll(finalResultArray)
							}

							if (query.length >= 3 && (context.getString(R.string.SavedMessages).lowercase().startsWith(query) || "saved messages".startsWith(query))) {
								var found = false

								for (i in localTipChats.indices) {
									if (localTipChats[i] is User) {
										if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser()?.id == (localTipChats[i] as? User)?.id) {
											found = true
											break
										}
									}
								}

								if (!found) {
									UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser()?.let {
										localTipChats.add(0, it)
									}
								}
							}

							localTipDates.clear()
							localTipDates.addAll(dateData)

							updateFiltersView(TextUtils.isEmpty(currentDataQuery), localTipChats, localTipDates, true)
						}

						firstLoading = false

						var progressView: View? = null
						var progressViewPosition = -1

						for (i in 0 until n) {
							val child = listView.getChildAt(i)

							if (child is FlickerLoadingView) {
								progressView = child
								progressViewPosition = listView.getChildAdapterPosition(child)
							}
						}

						val finalProgressView = progressView

						if (progressView != null) {
							listView.removeView(progressView)
						}

						if (loadingView.isVisible && listView.childCount <= 1 || progressView != null) {
							val finalProgressViewPosition = progressViewPosition

							viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
								override fun onPreDraw(): Boolean {
									viewTreeObserver.removeOnPreDrawListener(this)

									val animatorSet = AnimatorSet()

									for (child in listView.children) {
										if (finalProgressView != null) {
											if (listView.getChildAdapterPosition(child) < finalProgressViewPosition) {
												continue
											}
										}

										child.alpha = 0f

										val s = min(listView.measuredHeight, max(0, child.top))
										val delay = (s / listView.measuredHeight.toFloat() * 100).toInt()
										val a = ObjectAnimator.ofFloat(child, ALPHA, 0f, 1f)

										a.startDelay = delay.toLong()
										a.duration = 200

										animatorSet.playTogether(a)
									}

									animatorSet.addListener(object : AnimatorListenerAdapter() {
										override fun onAnimationEnd(animation: Animator) {
											accountInstance.notificationCenter.onAnimationFinish(animationIndex)
										}
									})

									animationIndex = accountInstance.notificationCenter.setAnimationInProgress(animationIndex, null)

									animatorSet.start()

									if (finalProgressView != null && finalProgressView.parent == null) {
										listView.addView(finalProgressView)

										val layoutManager = listView.layoutManager

										if (layoutManager != null) {
											layoutManager.ignoreView(finalProgressView)

											val animator: Animator = ObjectAnimator.ofFloat(finalProgressView, ALPHA, finalProgressView.alpha, 0f)

											animator.addListener(object : AnimatorListenerAdapter() {
												override fun onAnimationEnd(animation: Animator) {
													finalProgressView.alpha = 1f
													layoutManager.stopIgnoringView(finalProgressView)
													listView.removeView(finalProgressView)
												}
											})

											animator.start()
										}
									}

									return true
								}
							})
						}

						notifyDataSetChanged()
					}
				}
			}.also { searchRunnable = it }, (if (filterAndQueryIsSame && messages.isNotEmpty()) 0 else 350).toLong())

			loadingView.setViewType(FlickerLoadingView.FILES_TYPE)
		}

		private fun updateSearchResults(result: MutableList<ListItem>) {
			AndroidUtilities.runOnUIThread {
				if (searching) {
					if (listView.adapter !== searchAdapter) {
						listView.adapter = searchAdapter
					}
				}

				searchResult = result

				notifyDataSetChanged()
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder, section: Int, row: Int): Boolean {
			val type = holder.itemViewType
			return type == 1 || type == 4
		}

		override fun getSectionCount(): Int {
			var count = 2

			if (sections.isNotEmpty()) {
				count += sections.size + if (endReached) 0 else 1
			}

			return count
		}

		override fun getItem(section: Int, position: Int): Any? {
			@Suppress("NAME_SHADOWING") var section = section

			if (section == 0) {
				if (position < searchResult.size) {
					return searchResult[position]
				}
			}
			else {
				section--

				if (section < sections.size) {
					val arrayList = sectionArrays[sections[section]]

					if (arrayList != null) {
						return arrayList[position - (if (section == 0 && searchResult.isEmpty()) 0 else 1)]
					}
				}
			}

			return null
		}

		override fun getCountForSection(section: Int): Int {
			@Suppress("NAME_SHADOWING") var section = section

			if (section == 0) {
				return searchResult.size
			}

			section--

			if (section < sections.size) {
				val arrayList = sectionArrays[sections[section]]

				return if (arrayList != null) {
					arrayList.size + if (section == 0 && searchResult.isEmpty()) 0 else 1
				}
				else {
					0
				}
			}
			return 1
		}

		override fun getSectionHeaderView(section: Int, view: View?): View? {
			@Suppress("NAME_SHADOWING") var section = section

			var sectionCell = view as GraySectionCell?

			if (sectionCell == null) {
				sectionCell = GraySectionCell(mContext)
				sectionCell.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.light_background, null) and -0xd000001)
			}

			if (section == 0 || section == 1 && searchResult.isEmpty()) {
				sectionCell.alpha = 0f
				return sectionCell
			}

			section--

			if (section < sections.size) {
				sectionCell.alpha = 1.0f

				val name = sections[section]
				val messageObjects = sectionArrays[name]

				if (messageObjects != null) {
					val messageObject = messageObjects[0]

					val str = if (section == 0 && searchResult.isNotEmpty()) {
						context.getString(R.string.GlobalSearch)
					}
					else {
						LocaleController.formatSectionDate(messageObject.messageOwner!!.date.toLong())
					}

					sectionCell.setText(str)
				}
			}

			return view
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = GraySectionCell(mContext)
				}

				1, 4 -> {
					val documentCell = SharedDocumentCell(mContext, if (viewType == 1) SharedDocumentCell.VIEW_TYPE_PICKER else SharedDocumentCell.VIEW_TYPE_GLOBAL_SEARCH)
					documentCell.setDrawDownloadIcon(false)
					view = documentCell
				}

				2 -> {
					val flickerLoadingView = FlickerLoadingView(mContext)
					flickerLoadingView.setViewType(FlickerLoadingView.FILES_TYPE)
					flickerLoadingView.setIsSingleCell(true)
					view = flickerLoadingView
				}

				3 -> {
					view = View(mContext)
				}

				else -> {
					view = View(mContext)
				}
			}

			view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(section: Int, position: Int, holder: RecyclerView.ViewHolder) {
			@Suppress("NAME_SHADOWING") var section = section
			@Suppress("NAME_SHADOWING") var position = position

			val viewType = holder.itemViewType

			if (viewType == 2 || viewType == 3) {
				return
			}
			when (viewType) {
				0 -> {
					section--

					val name = sections[section]
					val messageObjects = sectionArrays[name] ?: return
					val messageObject = messageObjects[0]

					val str = if (section == 0 && searchResult.isNotEmpty()) {
						context.getString(R.string.GlobalSearch)
					}
					else {
						LocaleController.formatSectionDate(messageObject.messageOwner!!.date.toLong())
					}

					(holder.itemView as GraySectionCell).setText(str)
				}

				1, 4 -> {
					val sharedDocumentCell = holder.itemView as SharedDocumentCell

					if (section == 0) {
						val item = getItem(position) as ListItem
						val documentCell = holder.itemView

						if (item.icon != 0) {
							documentCell.setTextAndValueAndTypeAndThumb(item.title, item.subtitle, null, null, item.icon, false)
						}
						else {
							val type = item.ext.uppercase().substring(0, min(item.ext.length, 4))
							documentCell.setTextAndValueAndTypeAndThumb(item.title, item.subtitle, type, item.thumb, 0, false)
						}
						if (item.file != null) {
							documentCell.setChecked(selectedFiles.containsKey(item.file.toString()), !scrolling)
						}
						else {
							documentCell.setChecked(false, !scrolling)
						}
					}
					else {
						section--

						if (section != 0 || searchResult.isNotEmpty()) {
							position--
						}

						val name = sections[section]
						val messageObjects = sectionArrays[name] ?: return
						val messageObject = messageObjects[position]
						val animated = sharedDocumentCell.message != null && sharedDocumentCell.message.id == messageObject.id

						sharedDocumentCell.setDocument(messageObject, position != messageObjects.size - 1 || section == sections.size - 1 && isLoading)

						sharedDocumentCell.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
							override fun onPreDraw(): Boolean {
								sharedDocumentCell.viewTreeObserver.removeOnPreDrawListener(this)

								if (parentAlert.actionBar.isActionModeShowed) {
									messageHashIdTmp[messageObject.id] = messageObject.dialogId
									sharedDocumentCell.setChecked(selectedMessages.containsKey(messageHashIdTmp), animated)
								}
								else {
									sharedDocumentCell.setChecked(false, animated)
								}
								return true
							}
						})
					}
				}
			}
		}

		override fun getItemViewType(section: Int, position: Int): Int {
			@Suppress("NAME_SHADOWING") var section = section

			if (section == 0) {
				return 1
			}
			else if (section == getSectionCount() - 1) {
				return 3
			}

			section--

			return if (section < sections.size) {
				if ((section != 0 || searchResult.isNotEmpty()) && position == 0) {
					0
				}
				else {
					4
				}
			}
			else {
				2
			}
		}

		override fun notifyDataSetChanged() {
			super.notifyDataSetChanged()
			updateEmptyView()
		}

		override fun getLetter(position: Int): String? {
			return null
		}

		override fun getPositionForScrollProgress(listView: RecyclerListView, progress: Float, position: IntArray) {
			position[0] = 0
			position[1] = 0
		}
	}

	companion object {
		const val TYPE_DEFAULT = 0
		const val TYPE_MUSIC = 1
		const val TYPE_RINGTONE = 2
		const val ANIMATION_NONE = 0
		const val ANIMATION_FORWARD = 1
		const val ANIMATION_BACKWARD = 2
		const val SEARCH_BUTTON = 0
		const val SORT_BUTTON = 6
	}
}
