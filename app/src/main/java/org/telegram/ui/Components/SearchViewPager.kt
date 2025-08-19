/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 * Copyright Shamil Afandiyev, Ello 2025.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.withTranslation
import androidx.core.util.size
import androidx.core.util.valueIterator
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.DialogObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.migratedTo
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.MenuDrawable
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.DialogsSearchAdapter
import org.telegram.ui.Adapters.FiltersView
import org.telegram.ui.Adapters.FiltersView.MediaFilterData
import org.telegram.ui.Cells.ContextLinkCell
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Cells.SharedAudioCell
import org.telegram.ui.Cells.SharedDocumentCell
import org.telegram.ui.Cells.SharedLinkCell
import org.telegram.ui.Cells.SharedPhotoVideoCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.DialogsActivity
import org.telegram.ui.FilteredSearchView
import org.telegram.ui.FilteredSearchView.MessageHashId
import org.telegram.ui.FilteredSearchView.UiCallback
import kotlin.math.abs

class SearchViewPager(context: Context, private val parent: DialogsActivity, type: Int, initialDialogsType: Int, private val folderId: Int, var chatPreviewDelegate: ChatPreviewDelegate) : ViewPagerFixed(context), UiCallback {
	lateinit var emptyView: StickerEmptyView
	lateinit var searchListView: RecyclerListView
	private val itemAnimator = DefaultItemAnimator()
	private val itemsEnterAnimator: RecyclerItemsEnterAnimator
	private val noMediaFiltersSearchView: FilteredSearchView
	private val searchLayoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
	private val selectedFiles = mutableMapOf<MessageHashId, MessageObject>()
	private val viewPagerAdapter = ViewPagerAdapter()
	private var animateFromCount = 0
	private var attached = false
	private var deleteItem: ActionBarMenuItem? = null
	private var filteredSearchViewDelegate: FilteredSearchView.Delegate? = null
	private var forwardItem: ActionBarMenuItem? = null
	private var gotoItem: ActionBarMenuItem? = null
	private var isActionModeShowed = false
	private var keyboardSize = 0
	private var lastSearchScrolledToTop = false
	private var selectedMessagesCountTextView: NumberTextView? = null
	private var showOnlyDialogsAdapter = false
	val currentSearchFilters = mutableListOf<MediaFilterData>()
	val fragmentView: SizeNotifierFrameLayout?
	var currentAccount = UserConfig.selectedAccount
	var dialogsSearchAdapter: DialogsSearchAdapter
	var lastSearchString: String? = null
	var searchContainer: FrameLayout

	init {
		itemAnimator.addDuration = 150
		itemAnimator.moveDuration = 350
		itemAnimator.changeDuration = 0
		itemAnimator.removeDuration = 0
		itemAnimator.moveInterpolator = OvershootInterpolator(1.1f)
		itemAnimator.setTranslationInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)

		dialogsSearchAdapter = object : DialogsSearchAdapter(context, type, initialDialogsType, itemAnimator) {
			override fun notifyDataSetChanged() {
				val itemCount = currentItemCount

				super.notifyDataSetChanged()

				if (!lastSearchScrolledToTop) {
					searchListView.scrollToPosition(0)
					lastSearchScrolledToTop = true
				}

				if (getItemCount() == 0 && itemCount != 0 && !isSearching) {
					emptyView.showProgress(show = false, animated = false)
				}
			}
		}

		fragmentView = parent.fragmentView as? SizeNotifierFrameLayout

		searchListView = object : BlurredRecyclerView(context) {
			override fun dispatchDraw(canvas: Canvas) {
				if (dialogsSearchAdapter.showMoreAnimation) {
					canvas.save()
					invalidate()

					val lastItemIndex = dialogsSearchAdapter.getItemCount() - 1

					for (i in 0 until childCount) {
						val child = getChildAt(i)

						if (getChildAdapterPosition(child) == lastItemIndex) {
							canvas.clipRect(0f, 0f, width.toFloat(), child.bottom + child.translationY)
							break
						}
					}
				}

				super.dispatchDraw(canvas)

				if (dialogsSearchAdapter.showMoreAnimation) {
					canvas.restore()
				}

				dialogsSearchAdapter.showMoreHeader?.let {
					canvas.withTranslation(it.left.toFloat(), it.top + it.translationY) {
						it.draw(this)
					}
				}
			}
		}

		searchListView.setItemAnimator(itemAnimator)
		searchListView.pivotY = 0f
		searchListView.setAdapter(dialogsSearchAdapter)
		searchListView.isVerticalScrollBarEnabled = true
		searchListView.setInstantClick(true)
		searchListView.verticalScrollbarPosition = if (LocaleController.isRTL) View.SCROLLBAR_POSITION_LEFT else View.SCROLLBAR_POSITION_RIGHT
		searchListView.setLayoutManager(searchLayoutManager)
		searchListView.setAnimateEmptyView(true, 0)

		searchListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					AndroidUtilities.hideKeyboard(parent.parentActivity?.currentFocus)
				}
			}

			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				val firstVisibleItem = searchLayoutManager.findFirstVisibleItemPosition()
				val visibleItemCount = (abs((searchLayoutManager.findLastVisibleItemPosition() - firstVisibleItem).toDouble()) + 1).toInt()
				val totalItemCount = recyclerView.adapter?.itemCount ?: 0

				if (visibleItemCount > 0 && searchLayoutManager.findLastVisibleItemPosition() == totalItemCount - 1 && !dialogsSearchAdapter.isMessagesSearchEndReached) {
					dialogsSearchAdapter.loadMoreSearchMessages()
				}

				fragmentView?.invalidateBlur()
			}
		})

		noMediaFiltersSearchView = FilteredSearchView(parent)
		noMediaFiltersSearchView.setUiCallback(this@SearchViewPager)
		noMediaFiltersSearchView.visibility = GONE
		noMediaFiltersSearchView.setChatPreviewDelegate(chatPreviewDelegate)

		searchContainer = FrameLayout(context)

		val loadingView = FlickerLoadingView(context)
		loadingView.setViewType(1)

		emptyView = object : StickerEmptyView(context, loadingView, STICKER_TYPE_SEARCH, animationResource = R.raw.panda_chat_list_no_results) {
			override fun setVisibility(visibility: Int) {
				if (noMediaFiltersSearchView.tag != null) {
					super.setVisibility(GONE)
					return
				}

				super.setVisibility(visibility)
			}
		}

		emptyView.title.text = context.getString(R.string.NoResult)
		emptyView.subtitle.text = context.getString(R.string.SearchEmptyViewFilteredSubtitle2)
		emptyView.visibility = GONE
		emptyView.addView(loadingView, 0)
		emptyView.showProgress(show = true, animated = false)

		searchContainer.addView(emptyView)
		searchContainer.addView(searchListView)
		searchContainer.addView(noMediaFiltersSearchView)

		searchListView.setEmptyView(emptyView)

		searchListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				super.onScrolled(recyclerView, dx, dy)
				fragmentView?.invalidateBlur()
			}
		})

		itemsEnterAnimator = RecyclerItemsEnterAnimator(searchListView, true)

		setAdapter(viewPagerAdapter)
	}

	fun onTextChanged(text: String?) {
		val view = currentView
		var reset = !attached
		if (TextUtils.isEmpty(lastSearchString)) {
			reset = true
		}
		lastSearchString = text
		search(view, currentPosition, text, reset)
	}

	private fun search(view: View?, position: Int, query: String?, reset: Boolean) {
		@Suppress("NAME_SHADOWING") var reset = reset
		var dialogId: Long = 0
		var minDate: Long = 0
		var maxDate: Long = 0
		var includeFolder = false

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
			else if (data.filterType == FiltersView.FILTER_TYPE_ARCHIVE) {
				includeFolder = true
			}
		}

		if (view === searchContainer) {
			if (dialogId == 0L && minDate == 0L && maxDate == 0L) {
				lastSearchScrolledToTop = false

				dialogsSearchAdapter.searchDialogs(query, if (includeFolder) 1 else 0)
				dialogsSearchAdapter.setFiltersDelegate(filteredSearchViewDelegate, false)

				noMediaFiltersSearchView.animate().setListener(null).cancel()
				noMediaFiltersSearchView.setDelegate(null, false)

				if (reset) {
					emptyView.showProgress(!dialogsSearchAdapter.isSearching, false)
					emptyView.showProgress(dialogsSearchAdapter.isSearching, false)
				}
				else {
					if (!dialogsSearchAdapter.hasRecentSearch()) {
						emptyView.showProgress(dialogsSearchAdapter.isSearching, true)
					}
				}

				if (reset) {
					noMediaFiltersSearchView.visibility = GONE
				}
				else {
					if (noMediaFiltersSearchView.visibility != GONE) {
						noMediaFiltersSearchView.animate().alpha(0f).setListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								noMediaFiltersSearchView.visibility = GONE
							}
						}).setDuration(150).start()
					}
				}

				noMediaFiltersSearchView.tag = null
			}
			else {
				noMediaFiltersSearchView.tag = 1
				noMediaFiltersSearchView.setDelegate(filteredSearchViewDelegate, false)
				noMediaFiltersSearchView.animate().setListener(null).cancel()

				if (reset) {
					noMediaFiltersSearchView.visibility = VISIBLE
					noMediaFiltersSearchView.alpha = 1f
				}
				else {
					if (noMediaFiltersSearchView.visibility != VISIBLE) {
						noMediaFiltersSearchView.visibility = VISIBLE
						noMediaFiltersSearchView.alpha = 0f

						reset = true
					}

					noMediaFiltersSearchView.animate().alpha(1f).setDuration(150).start()
				}

				noMediaFiltersSearchView.search(dialogId, minDate, maxDate, null, includeFolder, query, reset)

				emptyView.visibility = GONE
			}

			emptyView.setKeyboardHeight(keyboardSize, false)

			noMediaFiltersSearchView.setKeyboardHeight(keyboardSize, false)
		}
		else if (view is FilteredSearchView) {
			view.setKeyboardHeight(keyboardSize, false)
			val item = viewPagerAdapter.items[position]
			view.search(dialogId, minDate, maxDate, FiltersView.filters[item.filterIndex], includeFolder, query, reset)
		}
		else if (view is SearchDownloadsContainer) {
			view.setKeyboardHeight(keyboardSize, false)
			view.search(query)
		}
	}

	fun onResume() {
		dialogsSearchAdapter.notifyDataSetChanged()
	}

	fun removeSearchFilter(filterData: MediaFilterData) {
		currentSearchFilters.remove(filterData)
	}

	fun clear() {
		currentSearchFilters.clear()
	}

	fun setFilteredSearchViewDelegate(filteredSearchViewDelegate: FilteredSearchView.Delegate?) {
		this.filteredSearchViewDelegate = filteredSearchViewDelegate
	}

	private fun showActionMode(show: Boolean) {
		if (isActionModeShowed == show) {
			return
		}

		if (show && parent.actionBar?.isActionModeShowed == true) {
			return
		}

		if (show && parent.actionBar?.actionModeIsExist(ACTION_MODE_TAG) != true) {
			val actionMode = parent.actionBar!!.createActionMode(ACTION_MODE_TAG)

			selectedMessagesCountTextView = NumberTextView(actionMode.context)
			selectedMessagesCountTextView?.setTextSize(18)
			selectedMessagesCountTextView?.setTypeface(Theme.TYPEFACE_BOLD)
			selectedMessagesCountTextView?.setTextColor(context.getColor(R.color.text))

			actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0))

			selectedMessagesCountTextView?.setOnTouchListener { _, _ -> true }

			gotoItem = actionMode.addItemWithWidth(GOTO_ITEM_ID, R.drawable.msg_message, AndroidUtilities.dp(54f), context.getString(R.string.AccDescrGoToMessage))
			forwardItem = actionMode.addItemWithWidth(FORWARD_ITEM_ID, R.drawable.msg_forward, AndroidUtilities.dp(54f), context.getString(R.string.Forward))
			deleteItem = actionMode.addItemWithWidth(DELETE_ITEM_ID, R.drawable.msg_delete, AndroidUtilities.dp(54f), context.getString(R.string.Delete))
		}

		if (parent.actionBar?.backButton?.drawable is MenuDrawable) {
			parent.actionBar?.backButtonDrawable = BackDrawable(false)
		}

		isActionModeShowed = show

		if (show) {
			AndroidUtilities.hideKeyboard(parent.parentActivity!!.currentFocus)
			parent.actionBar?.showActionMode()
			selectedMessagesCountTextView?.setNumber(selectedFiles.size, false)
			gotoItem?.visibility = VISIBLE
			forwardItem?.visibility = VISIBLE
			deleteItem?.visibility = VISIBLE
		}
		else {
			parent.actionBar?.hideActionMode()

			selectedFiles.clear()

			for (i in 0 until childCount) {
				if (getChildAt(i) is FilteredSearchView) {
					(getChildAt(i) as FilteredSearchView).update()
				}

				if (getChildAt(i) is SearchDownloadsContainer) {
					(getChildAt(i) as SearchDownloadsContainer).update(true)
				}
			}

			noMediaFiltersSearchView.update()

			val n = viewsByType.size

			for (i in 0 until n) {
				val v = viewsByType.valueAt(i)!!

				if (v is FilteredSearchView) {
					v.update()
				}
			}
		}
	}

	fun onActionBarItemClick(id: Int) {
		when (id) {
			DELETE_ITEM_ID -> {
				if (parent.parentActivity == null) {
					return
				}

				val messageObjects = ArrayList(selectedFiles.values)
				val builder = AlertDialog.Builder(parent.parentActivity!!)
				builder.setTitle(LocaleController.formatPluralString("RemoveDocumentsTitle", selectedFiles.size))

				val spannableStringBuilder = SpannableStringBuilder()
				spannableStringBuilder.append(AndroidUtilities.replaceTags(LocaleController.formatPluralString("RemoveDocumentsMessage", selectedFiles.size))).append("\n\n").append(context.getString(R.string.RemoveDocumentsAlertMessage))

				builder.setMessage(spannableStringBuilder)

				builder.setNegativeButton(context.getString(R.string.Cancel)) { dialogInterface, _ ->
					dialogInterface.dismiss()
				}

				builder.setPositiveButton(context.getString(R.string.Delete)) { dialogInterface, _ ->
					dialogInterface.dismiss()
					parent.downloadController.deleteRecentFiles(messageObjects)
					hideActionMode()
				}

				val alertDialog = builder.show()

				val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
				button?.setTextColor(context.getColor(R.color.purple))
			}

			GOTO_ITEM_ID -> {
				if (selectedFiles.size != 1) {
					return
				}

				val messageObject = selectedFiles.values.iterator().next()

				goToMessage(messageObject)
			}

			FORWARD_ITEM_ID -> {
				val args = Bundle()
				args.putBoolean("onlySelect", true)
				args.putInt("dialogsType", 3)

				val fragment = DialogsActivity(args)

				fragment.setDelegate { fragment1, dids, message, _ ->
					val fmessages = mutableListOf<MessageObject>()

					for (hashId in selectedFiles.keys) {
						fmessages.add(selectedFiles[hashId]!!)
					}

					selectedFiles.clear()

					showActionMode(false)

					if (dids.size > 1 || dids[0] == AccountInstance.getInstance(currentAccount).userConfig.getClientUserId() || message != null) {
						for (a in dids.indices) {
							val did = dids[a]

							if (message != null) {
								AccountInstance.getInstance(currentAccount).sendMessagesHelper.sendMessage(message.toString(), did, null, null, null, true, null, null, null, true, 0, null, updateStickersOrder = false)
							}

							AccountInstance.getInstance(currentAccount).sendMessagesHelper.sendMessage(fmessages, did, forwardFromMyName = false, hideCaption = false, notify = true, scheduleDate = 0)
						}

						fragment1?.finishFragment()
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

							if (!AccountInstance.getInstance(currentAccount).messagesController.checkCanOpenChat(args1, fragment1)) {
								return@setDelegate
							}
						}

						val chatActivity = ChatActivity(args1)

						fragment1?.presentFragment(chatActivity, true)

						chatActivity.showFieldPanelForForward(true, fmessages)
					}
				}

				parent.presentFragment(fragment)
			}
		}
	}

	override fun goToMessage(messageObject: MessageObject) {
		val args = Bundle()
		var dialogId = messageObject.dialogId

		if (DialogObject.isEncryptedDialog(dialogId)) {
			args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId))
		}
		else if (DialogObject.isUserDialog(dialogId)) {
			args.putLong("user_id", dialogId)
		}
		else {
			val chat = AccountInstance.getInstance(currentAccount).messagesController.getChat(-dialogId)

			chat?.migratedTo?.let {
				args.putLong("migrated_to", dialogId)
				dialogId = -it.channelId
			}

			args.putLong("chat_id", -dialogId)
		}

		args.putInt("message_id", messageObject.id)

		parent.presentFragment(ChatActivity(args))

		showActionMode(false)
	}

	override fun getFolderId(): Int {
		return folderId
	}

	override fun actionModeShowing(): Boolean {
		return isActionModeShowed
	}

	fun hideActionMode() {
		showActionMode(false)
	}

	override fun toggleItemSelection(message: MessageObject, view: View, a: Int) {
		val hashId = MessageHashId(message.id, message.dialogId)

		if (selectedFiles.containsKey(hashId)) {
			selectedFiles.remove(hashId)
		}
		else {
			if (selectedFiles.size >= 100) {
				return
			}

			selectedFiles[hashId] = message
		}

		if (selectedFiles.isEmpty()) {
			showActionMode(false)
		}
		else {
			selectedMessagesCountTextView?.setNumber(selectedFiles.size, true)
			gotoItem?.visibility = if (selectedFiles.size == 1) VISIBLE else GONE

			if (deleteItem != null) {
				var canShowDelete = true
				val keySet: Set<MessageHashId> = selectedFiles.keys

				for (key in keySet) {
					if (!selectedFiles[key]!!.isDownloadingFile) {
						canShowDelete = false
						break
					}
				}

				deleteItem?.visibility = if (canShowDelete) VISIBLE else GONE
			}
		}

		when (view) {
			is SharedDocumentCell -> view.setChecked(selectedFiles.containsKey(hashId), true)
			is SharedPhotoVideoCell -> view.setChecked(a, selectedFiles.containsKey(hashId), true)
			is SharedLinkCell -> view.setChecked(selectedFiles.containsKey(hashId), true)
			is SharedAudioCell -> view.setChecked(selectedFiles.containsKey(hashId), true)
			is ContextLinkCell -> view.setChecked(selectedFiles.containsKey(hashId), true)
			is DialogCell -> view.setChecked(selectedFiles.containsKey(hashId), true)
		}
	}

	override fun isSelected(messageHashId: MessageHashId): Boolean {
		return selectedFiles.containsKey(messageHashId)
	}

	override fun showActionMode() {
		showActionMode(true)
	}

	override fun onItemSelected(currentPage: View?, oldPage: View?, position: Int, oldPosition: Int) {
		if (position == 0) {
			if (noMediaFiltersSearchView.isVisible) {
				noMediaFiltersSearchView.setDelegate(filteredSearchViewDelegate, false)
				dialogsSearchAdapter.setFiltersDelegate(null, false)
			}
			else {
				noMediaFiltersSearchView.setDelegate(null, false)
				dialogsSearchAdapter.setFiltersDelegate(filteredSearchViewDelegate, true)
			}
		}
		else if (currentPage is FilteredSearchView) {
			val update = oldPosition == 0 && noMediaFiltersSearchView.visibility != VISIBLE
			currentPage.setDelegate(filteredSearchViewDelegate, update)
		}

		if (oldPage is FilteredSearchView) {
			oldPage.setDelegate(null, false)
		}
		else {
			dialogsSearchAdapter.setFiltersDelegate(null, false)
			noMediaFiltersSearchView.setDelegate(null, false)
		}
	}

	fun updateColors() {
		for (child in children) {
			if (child is FilteredSearchView) {
				for (subchild in child.recyclerListView.children) {
					(subchild as? DialogCell)?.update(0)
				}
			}
		}

		for (v in viewsByType.valueIterator()) {
			if (v is FilteredSearchView) {
				for (child in v.recyclerListView.children) {
					if (child is DialogCell) {
						child.update(0)
					}
				}
			}
		}

		for (child in noMediaFiltersSearchView.recyclerListView.children) {
			if (child is DialogCell) {
				child.update(0)
			}
		}
	}

	fun reset() {
		setPosition(0)

		if (dialogsSearchAdapter.itemCount > 0) {
			searchLayoutManager.scrollToPositionWithOffset(0, 0)
		}

		viewsByType.clear()
	}

	override fun setPosition(position: Int) {
		if (position < 0) {
			return
		}

		super.setPosition(position)

		viewsByType.clear()
		tabsView?.selectTabWithId(position, 1f)
		invalidate()
	}

	fun setKeyboardHeight(keyboardSize: Int) {
		this.keyboardSize = keyboardSize

		val animated = isVisible && alpha > 0

		for (child in children) {
			if (child is FilteredSearchView) {
				child.setKeyboardHeight(keyboardSize, animated)
			}
			else if (child === searchContainer) {
				emptyView.setKeyboardHeight(keyboardSize, animated)
				noMediaFiltersSearchView.setKeyboardHeight(keyboardSize, animated)
			}
			else if (child is SearchDownloadsContainer) {
				child.setKeyboardHeight(keyboardSize, animated)
			}
		}
	}

	fun showOnlyDialogsAdapter(showOnlyDialogsAdapter: Boolean) {
		this.showOnlyDialogsAdapter = showOnlyDialogsAdapter
	}

	fun messagesDeleted(channelId: Long, markAsDeletedMessages: List<Int>) {
		for (v in viewsByType.valueIterator()) {
			if (v is FilteredSearchView) {
				v.messagesDeleted(channelId, markAsDeletedMessages)
			}
		}

		for (child in children) {
			(child as? FilteredSearchView)?.messagesDeleted(channelId, markAsDeletedMessages)
		}

		noMediaFiltersSearchView.messagesDeleted(channelId, markAsDeletedMessages)

		if (selectedFiles.isNotEmpty()) {
			var toRemove: MutableList<MessageHashId>? = null
			val arrayList = selectedFiles.keys.toList()

			for (k in arrayList.indices) {
				val hashId = arrayList[k]
				val messageObject = selectedFiles[hashId]

				if (messageObject != null) {
					val dialogId = messageObject.dialogId
					val currentChannelId = if (dialogId < 0 && ChatObject.isChannel((-dialogId.toInt()).toLong(), currentAccount)) -dialogId.toInt() else 0

					if (currentChannelId.toLong() == channelId) {
						for (i in markAsDeletedMessages.indices) {
							if (messageObject.id == markAsDeletedMessages[i]) {
								toRemove = mutableListOf()
								toRemove.add(hashId)
							}
						}
					}
				}
			}

			if (toRemove != null) {
				var a = 0
				val n = toRemove.size

				while (a < n) {
					selectedFiles.remove(toRemove[a])
					a++
				}

				selectedMessagesCountTextView?.setNumber(selectedFiles.size, true)
				gotoItem?.visibility = if (selectedFiles.size == 1) VISIBLE else GONE
			}
		}
	}

	fun runResultsEnterAnimation() {
		itemsEnterAnimator.showItemsAnimated(if (animateFromCount > 0) animateFromCount + 1 else 0)
		animateFromCount = dialogsSearchAdapter.itemCount
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		attached = true
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		attached = false
	}

	override fun invalidateBlur() {
		fragmentView?.invalidateBlur()
	}

	fun cancelEnterAnimation() {
		itemsEnterAnimator.cancel()
		searchListView.invalidate()
		animateFromCount = 0
	}

	fun showDownloads() {
		setPosition(2)
	}

	fun getPositionForType(initialSearchType: Int): Int {
		for (i in viewPagerAdapter.items.indices) {
			if (viewPagerAdapter.items[i].type == FILTER_TYPE && viewPagerAdapter.items[i].filterIndex == initialSearchType) {
				return i
			}
		}

		return -1
	}

	private inner class ViewPagerAdapter : Adapter() {
		val items = listOf(
				Item(DIALOGS_TYPE),
				Item(FILTER_TYPE).also { it.filterIndex = 0 },
				Item(DOWNLOADS_TYPE),
				Item(FILTER_TYPE).also { it.filterIndex = 1 },
				Item(FILTER_TYPE).also { it.filterIndex = 2 },
				Item(FILTER_TYPE).also { it.filterIndex = 3 },
				Item(FILTER_TYPE).also { it.filterIndex = 4 },
		)

		override fun getItemTitle(position: Int): String? {
			return when (items[position].type) {
				DIALOGS_TYPE -> context.getString(R.string.SearchAllChatsShort)
				DOWNLOADS_TYPE -> context.getString(R.string.DownloadsTabs)
				else -> FiltersView.filters[items[position].filterIndex].title
			}
		}

		override val itemCount: Int
			get() {
				if (showOnlyDialogsAdapter) {
					return 1
				}

				return items.size
			}

		override fun createView(viewType: Int): View {
			when (viewType) {
				1 -> {
					return searchContainer
				}

				2 -> {
					val downloadsContainer = SearchDownloadsContainer(parent, currentAccount)

					downloadsContainer.recyclerListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
						override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
							super.onScrolled(recyclerView, dx, dy)
							fragmentView?.invalidateBlur()
						}
					})

					downloadsContainer.setUiCallback(this@SearchViewPager)

					return downloadsContainer
				}

				else -> {
					val filteredSearchView = FilteredSearchView(parent)
					filteredSearchView.setChatPreviewDelegate(chatPreviewDelegate)
					filteredSearchView.setUiCallback(this@SearchViewPager)

					filteredSearchView.recyclerListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
						override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
							super.onScrolled(recyclerView, dx, dy)
							fragmentView?.invalidateBlur()
						}
					})

					return filteredSearchView
				}
			}
		}

		override fun getItemViewType(position: Int): Int {
			if (items[position].type == DIALOGS_TYPE) {
				return 1
			}

			if (items[position].type == DOWNLOADS_TYPE) {
				return 2
			}

			return items[position].type + position
		}

		override fun bindView(view: View?, position: Int, viewType: Int) {
			search(view, position, lastSearchString, true)
		}
	}

	private class Item(val type: Int) {
		var filterIndex = 0
	}

	interface ChatPreviewDelegate {
		fun startChatPreview(listView: RecyclerListView, cell: DialogCell)

		fun move(dy: Float)

		fun finish()
	}

	companion object {
		private const val ACTION_MODE_TAG = "search_view_pager"
		const val GOTO_ITEM_ID = 200
		const val FORWARD_ITEM_ID = 201
		const val DELETE_ITEM_ID = 202
		private const val DIALOGS_TYPE = 0
		private const val DOWNLOADS_TYPE = 1
		const val FILTER_TYPE = 2
	}
}
