/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.feed

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScrollerCustom
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.databinding.FeedFilterViewHolderBinding
import org.telegram.messenger.databinding.FragmentFeedBinding
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.getChannel
import org.telegram.messenger.utils.getChat
import org.telegram.messenger.utils.getUser
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.showMenu
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.CountriesDataSource
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.TL_contacts_found
import org.telegram.tgnet.tlrpc.TL_contacts_search
import org.telegram.tgnet.tlrpc.TL_feeds_historyMessages
import org.telegram.tgnet.tlrpc.TL_feeds_readHistory
import org.telegram.tgnet.tlrpc.User
import org.telegram.tgnet.tlrpc.messages_Messages
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Adapters.CountriesAdapter
import org.telegram.ui.AvatarImageView
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.ContactAddActivity
import org.telegram.ui.Country
import org.telegram.ui.DialogsActivity
import org.telegram.ui.PhotoViewer
import org.telegram.ui.PhotoViewer.EmptyPhotoViewerProvider
import org.telegram.ui.profile.wallet.GridSpaceItemDecoration
import kotlin.math.max

class FeedFragment(args: Bundle?) : BaseFragment(args), FeedViewHolder.Delegate, NotificationCenter.NotificationCenterDelegate, FeedRecommendationsAdapter.FeedRecommendationsAdapterDelegate, ExploreAdapter.FeedExploreAdapterDelegate {
	private var binding: FragmentFeedBinding? = null
	private val feedAdapter = FeedAdapter()
	private val recommendationsAdapter = FeedRecommendationsAdapter()
	private val exploreAdapter = ExploreAdapter()
	private var settingsItem: ActionBarMenuItem? = null
	private var searchItem: ActionBarMenuItem? = null
	private var lastReadId = 0
	private var commentRequestId = -1
	private var commentMessagesRequestId = -1
	private var unregisterFlagSecure: Runnable? = null
	private var currentFeedPage = 0
	private var isLastFeedPage = false
	private var currentRecommendationsPage = 1
	private var isLastRecommendationsPage = false
	private var currentExplorePage = 0
	private var isLastExplorePage = false
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	private var limit = DEFAULT_FETCH_LIMIT
	private var feedMode = FeedMode.FOLLOWING
	private var countries: List<Country>? = null
	private var categories: List<String>? = null
	private var genres: List<String>? = null
	private val recommendationsFilter = RecommendationsFilter()
	private var emptyRecommendationsView: LinearLayout? = null
	private var query: String? = null
	private var savedScrollPositions = mutableMapOf<FeedMode, Pair<Int, Int>>()
	private var wasPaused = false
	private var feedLoadingJob: Job? = null
	private var recommendationsLoadingJob: Job? = null
	private var exploreLoadingJob: Job? = null
	private val exploreSpaceItemDecoration = ExploreGridSpaceItemDecoration(AndroidUtilities.dp(3f))
	private var forYouTab = 0

	private enum class FeedMode {
		FOLLOWING, EXPLORE, RECOMMENDED
	}

	private val isMusicFilter: Boolean
		get() = recommendationsFilter.category?.trim()?.lowercase() == "music"

	private val scrollListener = object : RecyclerView.OnScrollListener() {
		override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
			if (dy < 0) {
				hideScrollToTopButton()
			}
		}

		override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
			if (newState == RecyclerView.SCROLL_STATE_IDLE) {
				val layoutManager = recyclerView.layoutManager as LinearLayoutManager
				val position = layoutManager.findFirstVisibleItemPosition()
				val view = layoutManager.findViewByPosition(position)
				val offset = view?.top ?: 0
				val viewHolder = view?.run { recyclerView.findContainingViewHolder(this) } as? FeedViewHolder
				val lastReadId = viewHolder?.getMessages()?.firstOrNull()?.id

				MessagesController.getMainSettings(currentAccount).edit().run {
					if (lastReadId != null && lastReadId >= this@FeedFragment.lastReadId) {
						this@FeedFragment.lastReadId = lastReadId
						putInt(LAST_READ_ID, lastReadId)
					}

					putInt(FEED_POSITION, position)
					putInt(FEED_OFFSET, offset)
					putInt(CURRENT_PAGE, currentFeedPage)
				}.apply()
			}
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun createView(context: Context): View? {
		actionBar?.setTitle(context.getString(R.string.feed))
		actionBar?.castShadows = false
		actionBar?.shouldDestroyBackButtonOnCollapse = true

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (parentActivity == null) {
					return
				}

				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		val menu = actionBar?.createMenu()

		searchItem = menu?.addItem(0, R.drawable.ic_search_menu)?.setIsSearchField(value = true, wrapInScrollView = true)?.setActionBarMenuItemSearchListener(object : ActionBarMenuItem.ActionBarMenuItemSearchListener() {
			override fun onSearchExpand() {
				settingsItem?.gone()

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.needCheckSystemBarColors)

				(fragmentView as? SizeNotifierFrameLayout)?.invalidateBlur()
			}

			override fun canCollapseSearch(): Boolean {
				settingsItem?.visible()
				return true
			}

			override fun onSearchCollapse() {
				settingsItem?.visible()

				query = null

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.needCheckSystemBarColors, true)

				(fragmentView as? SizeNotifierFrameLayout)?.invalidateBlur()

				if (recommendationsLoadingJob?.isActive == true) {
					recommendationsLoadingJob?.cancel()
				}

				recommendationsLoadingJob = ioScope.launch {
					loadRecommendationsData(force = true)
				}
			}

			override fun onTextChanged(editText: EditText) {
				query = editText.text?.toString()?.trim()

				if (recommendationsLoadingJob?.isActive == true) {
					recommendationsLoadingJob?.cancel()
				}

				recommendationsLoadingJob = ioScope.launch {
					loadRecommendationsData(force = true)
				}
			}

			override fun canToggleSearch(): Boolean {
				return !actionBar!!.isActionModeShowed
			}
		})

		searchItem?.setSearchFieldHint(context.getString(R.string.find_recommended_channels))
		searchItem?.gone()

		settingsItem = menu?.addItem(SETTINGS, R.drawable.ic_settings_menu)
		settingsItem?.contentDescription = context.getString(R.string.feed_settings)

		settingsItem?.setOnClickListener {
			presentFragment(FeedSettingsFragment())
		}

		binding = FragmentFeedBinding.inflate(LayoutInflater.from(context), parentLayout, false)

		binding?.scrollToTopButton?.accessibilityDelegate = object : View.AccessibilityDelegate() {
			override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(host, info)

				info.className = "android.widget.Button"
				info.isClickable = true
				info.isLongClickable = false
				info.contentDescription = context.getString(R.string.scroll_top_top)
			}
		}

		binding?.recyclerView?.layoutManager = createLinearLayoutManager()
		binding?.recyclerView?.addOnScrollListener(scrollListener)

		binding?.tabLayout?.let {
			it.addTab(it.newTab().setText(R.string.following).setId(FeedMode.FOLLOWING.ordinal))
			it.addTab(it.newTab().setText(R.string.explore).setId(FeedMode.EXPLORE.ordinal))
			it.addTab(it.newTab().setText(R.string.for_you).setId(FeedMode.RECOMMENDED.ordinal))

			it.selectTab(it.getTabAt(feedMode.ordinal))

			it.addOnTabSelectedListener(object : OnTabSelectedListener {
				override fun onTabSelected(tab: TabLayout.Tab) {
					val layoutManager = binding?.recyclerView?.layoutManager as? LinearLayoutManager
					val position = layoutManager?.findFirstVisibleItemPosition() ?: 0
					val view = layoutManager?.findViewByPosition(position)
					val offset = view?.top ?: 0

					savedScrollPositions[feedMode] = position to offset

					when (tab.id) {
						FeedMode.FOLLOWING.ordinal -> {
							feedMode = FeedMode.FOLLOWING
						}

						FeedMode.EXPLORE.ordinal -> {
							feedMode = FeedMode.EXPLORE
						}

						FeedMode.RECOMMENDED.ordinal -> {
							feedMode = FeedMode.RECOMMENDED
						}
					}

					reloadState(restorePosition = true)
				}

				override fun onTabUnselected(tab: TabLayout.Tab) {
					// unused
				}

				override fun onTabReselected(tab: TabLayout.Tab) {
					// unused
				}
			})

			if (forYouTab == FOR_YOU_TAB) {
				val tab = it.getTabAt(FOR_YOU_TAB)
				tab?.select()
			}
		}

		binding?.searchParamsRecyclerView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
		binding?.searchParamsRecyclerView?.addItemDecoration(GridSpaceItemDecoration(AndroidUtilities.dp(12f)))

		binding?.searchParamsRecyclerView?.adapter = object : RecyclerView.Adapter<FeedFilterViewHolder>() {
			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedFilterViewHolder {
				val binding = FeedFilterViewHolderBinding.inflate(LayoutInflater.from(context), parent, false)
				return FeedFilterViewHolder(binding)
			}

			override fun onBindViewHolder(holder: FeedFilterViewHolder, position: Int) {
				when (position) {
					0 -> {
						holder.text = context.getString(R.string.all)
						holder.isDropDown = false
						holder.itemView.isSelected = recommendationsFilter.all

						holder.itemView.setOnClickListener {
							recommendationsFilter.reset()
							recommendationsFilter.check()
						}
					}

					1 -> {
						holder.text = context.getString(R.string.new_filter)
						holder.isDropDown = false
						holder.itemView.isSelected = recommendationsFilter.new

						holder.itemView.setOnClickListener {
							recommendationsFilter.new = !recommendationsFilter.new
							recommendationsFilter.check()
						}
					}

					2 -> {
						holder.text = context.getString(R.string.course)
						holder.isDropDown = false
						holder.itemView.isSelected = recommendationsFilter.course

						holder.itemView.setOnClickListener {
							recommendationsFilter.course = !recommendationsFilter.course
							recommendationsFilter.check()
						}
					}

					3 -> {
						holder.text = context.getString(R.string.paid)
						holder.isDropDown = false
						holder.itemView.isSelected = recommendationsFilter.paid

						holder.itemView.setOnClickListener {
							recommendationsFilter.paid = !recommendationsFilter.paid
							recommendationsFilter.check()
						}
					}

					4 -> {
						holder.text = context.getString(R.string.free)
						holder.isDropDown = false
						holder.itemView.isSelected = recommendationsFilter.free

						holder.itemView.setOnClickListener {
							recommendationsFilter.free = !recommendationsFilter.free
							recommendationsFilter.check()
						}
					}

					5 -> {
						holder.text = recommendationsFilter.country?.name ?: context.getString(R.string.all_countries)
						holder.isDropDown = true
						holder.itemView.isSelected = (recommendationsFilter.country != null)

						holder.itemView.setOnClickListener {
							val items = countries?.toMutableList() ?: run {
								loadCountries()
								return@setOnClickListener
							}

							items.add(0, Country().apply {
								name = context.getString(R.string.all_countries)
							})

							val dialog = createCountryChoiceDialog(parentActivity!!, items) { _, index ->
								recommendationsFilter.country = items[index].takeIf { it.code != null }
								holder.text = recommendationsFilter.country?.name ?: context.getString(R.string.all_countries)
								recommendationsFilter.check()
							}

							dialog.show()
						}
					}

					6 -> {
						holder.text = recommendationsFilter.category ?: context.getString(R.string.all_categories)
						holder.isDropDown = true
						holder.itemView.isSelected = (recommendationsFilter.category != null)

						holder.itemView.setOnClickListener {
							val items = categories?.toMutableList() ?: run {
								loadCategories()
								return@setOnClickListener
							}

							var selectedIndex = items.indexOf(recommendationsFilter.category)

							if (selectedIndex == -1) {
								selectedIndex = 0
							}
							else {
								selectedIndex += 1
							}

							items.add(0, context.getString(R.string.all_categories))

							MaterialAlertDialogBuilder(context).setTitle(context.getString(R.string.select_category_no_star)).setPositiveButton(context.getString(R.string.cancel), null).setSingleChoiceItems(items.toTypedArray(), selectedIndex) { dialog, which ->
								recommendationsFilter.category = items[which].takeIf { it != context.getString(R.string.all_categories) }
								holder.text = recommendationsFilter.category ?: context.getString(R.string.all_categories)
								recommendationsFilter.check()
								dialog.dismiss()
							}.show()
						}
					}

					7 -> {
						holder.text = recommendationsFilter.genre ?: context.getString(R.string.all_genres)
						holder.isDropDown = true
						holder.itemView.isSelected = (recommendationsFilter.genre != null)

						holder.itemView.setOnClickListener {
							val items = genres?.toMutableList() ?: run {
								loadGenres()
								return@setOnClickListener
							}

							var selectedIndex = items.indexOf(recommendationsFilter.genre)

							if (selectedIndex == -1) {
								selectedIndex = 0
							}
							else {
								selectedIndex += 1
							}

							items.add(0, context.getString(R.string.all_genres))

							MaterialAlertDialogBuilder(context).setTitle(context.getString(R.string.select_genre)).setPositiveButton(context.getString(R.string.cancel), null).setSingleChoiceItems(items.toTypedArray(), selectedIndex) { dialog, which ->
								recommendationsFilter.genre = items[which].takeIf { it != context.getString(R.string.all_genres) }
								holder.text = recommendationsFilter.genre ?: context.getString(R.string.all_genres)
								recommendationsFilter.check()
								dialog.dismiss()
							}.show()
						}
					}

					else -> {
						throw IllegalArgumentException("Unknown position: $position")
					}
				}
			}

			override fun getItemCount(): Int {
				return 7 + (if (isMusicFilter) 1 else 0)
			}
		}

		feedAdapter.delegate = this
		recommendationsAdapter.delegate = this
		exploreAdapter.delegate = this

		binding?.recyclerView?.adapter = feedAdapter

		fragmentView = binding?.root

		if (feedLoadingJob?.isActive != true) {
			feedLoadingJob = ioScope.launch {
				loadCache()
				loadFeedData(true)
			}
		}

		return binding?.root
	}

	override fun onResume() {
		super.onResume()

		Bulletin.addDelegate(this, object : Bulletin.Delegate {
			override fun getBottomOffset(tag: Int): Int {
				return parentLayout?.bottomNavigationPanelHeight ?: 0
			}
		})

		parentActivity?.window?.let {
			unregisterFlagSecure = AndroidUtilities.registerFlagSecure(it)
		}

		if (!wasPaused) {
			if (feedLoadingJob?.isActive != true) {
				feedLoadingJob = ioScope.launch {
					loadFeedData(force = true)
				}
			}

			if (recommendationsLoadingJob?.isActive != true) {
				recommendationsLoadingJob = ioScope.launch {
					loadRecommendationsData(force = true)
				}
			}

			if (exploreLoadingJob?.isActive != true) {
				exploreLoadingJob = ioScope.launch {
					loadExploreData(force = true)
				}
			}
		}

		wasPaused = false
	}

	override fun onPause() {
		Bulletin.removeDelegate(this)

		wasPaused = true

		super.onPause()

		unregisterFlagSecure?.run()
		unregisterFlagSecure = null
	}

	override fun onFragmentCreate(): Boolean {
		lastReadId = MessagesController.getMainSettings(currentAccount).getInt(LAST_READ_ID, 0)

		notificationCenter.let {
			it.addObserver(this, NotificationCenter.messagePlayingDidReset)
			it.addObserver(this, NotificationCenter.messagePlayingGoingToStop)
			it.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
			it.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged)
			it.addObserver(this, NotificationCenter.messagesDeleted)
			it.addObserver(this, NotificationCenter.updateInterfaces)
			it.addObserver(this, NotificationCenter.didReceiveNewMessages)
			it.addObserver(this, NotificationCenter.dialogsNeedReload)
		}

		DialogsActivity.loadDialogs(accountInstance)

		arguments?.let { arguments ->
			feedMode = FeedMode.entries.getOrNull(arguments.getInt("feedMode", 0)) ?: FeedMode.FOLLOWING
			forYouTab = arguments.getInt("forYou", 0)
		}

		loadCountries()
		loadCategories()
		loadGenres()

		return true
	}

	override fun onFragmentDestroy() {
		if (feedLoadingJob?.isActive == true) {
			feedLoadingJob?.cancel()
		}

		feedLoadingJob = null

		if (recommendationsLoadingJob?.isActive == true) {
			recommendationsLoadingJob?.cancel()
		}

		recommendationsLoadingJob = null

		if (exploreLoadingJob?.isActive == true) {
			exploreLoadingJob?.cancel()
		}

		exploreLoadingJob = null

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}

		if (commentMessagesRequestId != -1) {
			commentMessagesRequestId = -1
			connectionsManager.cancelRequest(commentMessagesRequestId, false)
		}

		if (commentRequestId != -1) {
			commentRequestId = -1
			connectionsManager.cancelRequest(commentRequestId, false)
		}

		binding?.recyclerView?.adapter = null
		binding?.recyclerView?.layoutManager = null
		binding?.recyclerView?.removeOnScrollListener(scrollListener)

		binding = null

		notificationCenter.let {
			it.removeObserver(this, NotificationCenter.messagePlayingDidReset)
			it.removeObserver(this, NotificationCenter.messagePlayingGoingToStop)
			it.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
			it.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged)
			it.removeObserver(this, NotificationCenter.messagesDeleted)
			it.removeObserver(this, NotificationCenter.updateInterfaces)
			it.removeObserver(this, NotificationCenter.didReceiveNewMessages)
			it.removeObserver(this, NotificationCenter.chatInfoDidLoad)
			it.removeObserver(this, NotificationCenter.dialogsNeedReload)
		}

		super.onFragmentDestroy()
	}

	private fun createLinearLayoutManager(): LinearLayoutManager {
		return LinearLayoutManager(context)
	}

	private fun createGridLayoutManager(): GridLayoutManager {
		return GridLayoutManager(context, 3)
	}

	private suspend fun loadCache() {
		val pinnedChannels = mutableSetOf<Long>()

		val feed = messagesStorage.feed.mapNotNull {
			val message = it.id.toLong()
			val channel = -it.channel_id.toLong()

			if (it.pinned_channel) {
				pinnedChannels.add(-channel)
			}

			messagesStorage.getMessage(channel, message)
		}

		if (feed.isNotEmpty()) {
			withContext(mainScope.coroutineContext) {
				feedAdapter.pinned = pinnedChannels.toLongArray()
				feedAdapter.setFeed(feed, append = false)

				reloadState(restorePosition = false)

				MessagesController.getMainSettings(currentAccount).let {
					val position = it.getInt(FEED_POSITION, 0)
					val offset = it.getInt(FEED_OFFSET, 0)

					currentFeedPage = it.getInt(CURRENT_PAGE, 0)

					(binding?.recyclerView?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, offset)
				}
			}
		}

		limit = feedAdapter.feed?.size?.takeIf { it > 0 } ?: DEFAULT_FETCH_LIMIT
	}

	private suspend fun loadNewMessages() {
		val newestMessage = feedAdapter.feed?.flatten()?.maxByOrNull { it.id }?.messageOwner

		if (newestMessage == null) {
			loadFeedData(true)
			return
		}

		val newMessages = mutableListOf<Message>()
		var page = 0

		while (!newMessages.contains(newestMessage)) {
			val req = TL_feeds_readHistory()
			req.page = page
			req.limit = limit

			when (val res = connectionsManager.performRequest(req)) {
				is TL_feeds_historyMessages -> {
					page += 1

					val messages = res.messages

					newMessages.addAll(messages)

					if (messages.size < limit) {
						break
					}
				}

				null -> {
					FileLog.e("TL_feeds_readHistory unknown error")
					break
				}

				else -> {
					FileLog.e("TL_feeds_readHistory error: $res")
					break
				}
			}
		}

		processLoadedMessages(newMessages, force = false)
	}

	private suspend fun loadFeedData(force: Boolean) {
		if (!force && isLastFeedPage) {
			feedAdapter.updateLoadingState(false)
			return
		}

		if (feedAdapter.pinned == null) {
			loadSettings()
		}

		val req = TL_feeds_readHistory()

		if (force) {
			currentFeedPage = 0
			isLastFeedPage = false
		}

		req.page = currentFeedPage
		req.limit = limit

		when (val res = connectionsManager.performRequest(req)) {
			is TL_feeds_historyMessages -> {
				currentFeedPage += 1

				val messages = res.messages

				isLastFeedPage = messages.isEmpty()

				if (isLastFeedPage) {
					feedAdapter.updateLoadingState(false)
				}
				else {
					feedAdapter.updateLoadingState(true)
				}

				feedLoadingJob = null

				processLoadedMessages(messages, force)
			}

			null -> {
				FileLog.e("TL_feeds_readHistory unknown error")
			}

			else -> {
				FileLog.e("TL_feeds_readHistory error: $res")
			}
		}

		withContext(mainScope.coroutineContext) {
			reloadState(restorePosition = false)
		}
	}

	private fun createCountryChoiceDialog(parentActivity: Activity, countries: List<Country>, listener: DialogInterface.OnClickListener): Dialog {
		val countriesAdapter = CountriesAdapter(parentActivity, R.layout.country_view_holder, countries)

		val builder = MaterialAlertDialogBuilder(parentActivity)
		builder.setTitle(parentActivity.getString(R.string.select_country))
		builder.setAdapter(countriesAdapter, listener)
		builder.setPositiveButton(parentActivity.getString(R.string.Cancel), null)

		return builder.create()
	}

	private suspend fun processLoadedMessages(messages: List<Message>?, force: Boolean) {
		val recyclerView = binding?.recyclerView

		if (recyclerView?.adapter == feedAdapter) {
			(recyclerView.layoutManager as? LinearLayoutManager)?.let { layoutManager ->
				val position = layoutManager.findFirstVisibleItemPosition()
				val previousVisibleMessage = feedAdapter.feed?.getOrNull(position)
				val view = layoutManager.findViewByPosition(position)
				val offset = view?.top ?: 0

				val newMessages = withContext(mainScope.coroutineContext) {
					feedAdapter.setFeed(messages, append = !force)
				}

				val newVisiblePosition = feedAdapter.feed?.indexOf(previousVisibleMessage)

				if (newVisiblePosition != null) {
					withContext(mainScope.coroutineContext) {
						layoutManager.scrollToPositionWithOffset(newVisiblePosition, offset)
					}
				}

				if (newMessages.isNotEmpty()) {
					if (lastReadId != 0) {
						val unreadMessages = feedAdapter.feed?.takeWhile { messageObjects -> messageObjects.any { msg -> msg.id > lastReadId } }

						if (!unreadMessages.isNullOrEmpty()) {
							withContext(mainScope.coroutineContext) {
								showScrollToTopButton(unreadMessages)
							}
						}
					}
					else {
						withContext(mainScope.coroutineContext) {
							showScrollToTopButton(newMessages)
						}
					}

					withContext(mainScope.coroutineContext) {
						delay(500)
						scrollListener.onScrollStateChanged(recyclerView, RecyclerView.SCROLL_STATE_IDLE)
					}
				}
			}
		}

		withContext(ioScope.coroutineContext) {
			val completeFeed = feedAdapter.feed?.flatten()?.map { it.messageOwner }

			if (!completeFeed.isNullOrEmpty()) {
				// save cache
				messagesStorage.putFeed(completeFeed, feedAdapter.pinned?.toList() ?: listOf())
			}
		}
	}

	private fun showScrollToTopButton(newMessages: List<List<MessageObject>>) {
		val binding = binding ?: return
		val firstNewMessagePosition = newMessages.size
		val allNewMessages = newMessages.flatten().distinctBy { it.id }

		if ((allNewMessages.maxOfOrNull { it.id } ?: 0) <= lastReadId) {
			return
		}

		val messages = allNewMessages.take(3)

		binding.scrollToTopButton.visible()
		binding.avatarsContainer.removeAllViews()

		messages.forEachIndexed { index, messageObject ->
			val info = messageObject.messageOwner?.getChat() ?: messageObject.messageOwner?.getUser() ?: return@forEachIndexed

			val avatarImage = AvatarImageView(binding.root.context)
			avatarImage.setRoundRadius(AndroidUtilities.dp(10f))
			avatarImage.shouldDrawBorder = true
			avatarImage.translationZ = AndroidUtilities.dp(2f - index.toFloat()).toFloat()
			avatarImage.contentDescription = null

			binding.avatarsContainer.addView(avatarImage, LayoutHelper.createLinear(24, 24, if (index == 0) 0f else -7f, 0f, 0f, 0f))

			val avatarDrawable = AvatarDrawable()
			avatarDrawable.setInfo(info)

			avatarImage.setForUserOrChat(info, avatarDrawable)
		}

		binding.scrollToTopButton.setOnClickListener {
			val linearSmoothScroller = LinearSmoothScrollerCustom(binding.recyclerView.context, LinearSmoothScrollerCustom.POSITION_MIDDLE)
			linearSmoothScroller.targetPosition = firstNewMessagePosition
			binding.recyclerView.layoutManager?.startSmoothScroll(linearSmoothScroller)
			hideScrollToTopButton()
		}

		binding.scrollToTopButton.animate().translationY(AndroidUtilities.dp(SCROLL_TO_TOP_BUTTON_OFFSET).toFloat() + binding.scrollToTopButton.measuredHeight).setDuration(200).start()
	}

	private fun hideScrollToTopButton() {
		binding?.scrollToTopButton?.let {
			if (it.translationY > 0f) {
				it.animate()?.translationY(-AndroidUtilities.dp(SCROLL_TO_TOP_BUTTON_OFFSET).toFloat())?.setDuration(200)?.start()
			}
		}
	}

	private suspend fun loadSettings() {
		val response = connectionsManager.performRequest(ElloRpc.feedSettingsRequest())

		if (response is TLRPC.TL_biz_dataRaw) {
			val data = response.readData<ElloRpc.FeedSettingsResponse>()
			val pinnedChannels = data?.pinnedChannels?.toLongArray()

			if (pinnedChannels != null) {
				feedAdapter.pinned = pinnedChannels
			}
		}
	}

	@Synchronized
	private fun reloadState(restorePosition: Boolean) {
		when (feedMode) {
			FeedMode.FOLLOWING -> {
				hideEmptyRecommendationsView()

				searchItem?.gone()

				if (binding?.recyclerView?.adapter != feedAdapter) {
					binding?.recyclerView?.adapter = feedAdapter
					binding?.recyclerView?.layoutManager = createLinearLayoutManager()
					binding?.recyclerView?.removeItemDecoration(exploreSpaceItemDecoration)
				}

				binding?.scrollToTopButton?.visible()
				binding?.searchParamsRecyclerView?.gone()
				binding?.chatBackground?.visible()
				binding?.exploreProgressBar?.gone()

				if (feedAdapter.itemCount == 0) {
					binding?.recyclerView?.gone()
					binding?.emptyFeedContainer?.visible()
				}
				else {
					binding?.recyclerView?.visible()
					binding?.emptyFeedContainer?.gone()
				}
			}

			FeedMode.EXPLORE -> {
				hideEmptyRecommendationsView()

				if (binding?.recyclerView?.adapter != exploreAdapter) {
					binding?.recyclerView?.adapter = exploreAdapter
					binding?.recyclerView?.layoutManager = createGridLayoutManager()
					binding?.recyclerView?.addItemDecoration(exploreSpaceItemDecoration)
				}

				binding?.scrollToTopButton?.gone()
				binding?.chatBackground?.gone()
				binding?.emptyFeedContainer?.gone()
				binding?.searchParamsRecyclerView?.gone()
				binding?.recyclerView?.visible()
				searchItem?.gone()

				if (exploreLoadingJob?.isActive == true && exploreAdapter.itemCount == 0) {
					binding?.exploreProgressBar?.visible()
				}
				else {
					binding?.exploreProgressBar?.gone()
				}
			}

			FeedMode.RECOMMENDED -> {
				if (binding?.recyclerView?.adapter != recommendationsAdapter) {
					binding?.recyclerView?.adapter = recommendationsAdapter
					binding?.recyclerView?.layoutManager = createLinearLayoutManager()
					binding?.recyclerView?.removeItemDecoration(exploreSpaceItemDecoration)
				}

				binding?.scrollToTopButton?.gone()
				binding?.chatBackground?.gone()
				binding?.emptyFeedContainer?.gone()
				binding?.searchParamsRecyclerView?.visible()
				binding?.recyclerView?.visible()
				binding?.exploreProgressBar?.gone()

				searchItem?.visible()

				if (recommendationsAdapter.itemCount == 0) {
					showEmptyRecommendationsView()
				}
				else {
					hideEmptyRecommendationsView()
				}
			}
		}

		if (restorePosition) {
			val (position, offset) = savedScrollPositions[feedMode] ?: (0 to 0)
			(binding?.recyclerView?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, offset)
		}
	}

	override fun shouldShowBottomNavigationPanel(): Boolean {
		return true
	}

	override fun onFeedItemClick(message: Message) {
		val channel = message.getChannel() ?: return

		val args = Bundle()
		args.putLong("chat_id", channel.id)
		args.putInt("message_id", message.id)

		val fragment = ChatActivity(args)
		presentFragment(fragment)
	}

	override fun onOpenAddToContacts(user: User?) {
		if (user != null) {
			val args = Bundle()
			args.putLong("user_id", user.id)
			args.putBoolean("addContact", true)
			presentFragment(ContactAddActivity(args))
		}
	}

	override fun onRepost(messages: List<MessageObject>) {
		val args = Bundle()
		args.putBoolean("onlySelect", true)
		args.putInt("dialogsType", 3)
		args.putInt("messagesCount", messages.size)
		args.putInt("hasPoll", 0)
		args.putBoolean("hasInvoice", false)

		val dialogsActivity = DialogsActivity(args)

		dialogsActivity.setDelegate { fragment, dids, message, _ ->
			if (dids.isEmpty()) {
				return@setDelegate
			}

			val msg = ArrayList(messages.map {
				MessageObject(currentAccount, it.messageOwner!!, generateLayout = true, checkMediaExists = true).apply {
					messageText = message // MARK: check if this works properly
				}
			})

			sendMessagesHelper.sendMessage(msg, dids.first(), forwardFromMyName = false, hideCaption = false, notify = true, scheduleDate = 0)

			fragment?.finishFragment()
		}

		presentFragment(dialogsActivity)
	}

	override fun onOpenImages(messages: List<MessageObject>, dialogId: Long, message: MessageObject, imageView: ImageView) {
		PhotoViewer.getInstance().setParentActivity(this)

		val playingObject = mediaController.playingMessageObject

		if (playingObject?.isVideo == true) {
			fileLoader.setLoadingVideoForPlayer(playingObject.document, false)
			mediaController.cleanupPlayer(true, true, false, playingObject.equals(message))
		}

		val photoViewerProvider = object : EmptyPhotoViewerProvider() {
			override fun validateGroupId(groupId: Long): Boolean {
				return messages.size > 1
			}

			override fun getTotalImageCount(): Int {
				return messages.size
			}
		}

		if (messages.size > 1) {
			PhotoViewer.getInstance().openPhoto(ArrayList(messages), messages.indexOf(message), dialogId, 0, photoViewerProvider)
		}
		else {
			if (message.isRoundVideo) {
				message.messageOwner?.let {
					onFeedItemClick(it)
				}
			}
			else {
				PhotoViewer.getInstance().openPhoto(message, dialogId, 0, photoViewerProvider, message.isVideo)
			}
		}

		mediaController.resetGoingToShowMessageObject()
	}

	override fun onContextMenuClick(messages: List<MessageObject>, dialogId: Long, view: View) {
		val popUp = context?.showMenu(view, R.menu.feed_channel_menu)

		popUp?.setOnMenuItemClickListener {
			when (it.itemId) {
				R.id.hide -> {
					hideChannel(dialogId)
				}

				R.id.report -> {
					AlertsCreator.createReportAlert(parentActivity, -dialogId, messages.firstOrNull()?.messageOwner?.id ?: 0, this, null)
				}
			}

			true
		}
	}

	private fun hideChannel(channelId: Long) {
		connectionsManager.sendRequest(ElloRpc.feedSettingsRequest(), { response, error ->
			if (error == null && response is TLRPC.TL_biz_dataRaw) {
				val data = response.readData<ElloRpc.FeedSettingsResponse>() ?: return@sendRequest

				val hiddenChannels = data.hiddenChannels?.toMutableSet() ?: mutableSetOf()
				val pinnedChannels = data.pinnedChannels?.toSet()?.toLongArray()

				hiddenChannels.add(channelId)

				val req = ElloRpc.saveFeedSettingsRequest(hidden = hiddenChannels.toSet().toLongArray(), pinned = pinnedChannels, showRecommended = data.showRecommended, showSubscriptionsOnly = data.showSubscriptionsOnly, showAdult = data.showAdult)

				connectionsManager.sendRequest(req, { response1, error1 ->
					if (error1 == null && response1 is TLRPC.TL_biz_dataRaw) {
						if (feedLoadingJob?.isActive != true) {
							feedLoadingJob = ioScope.launch {
								delay(500)
								loadFeedData(force = true)
							}
						}
					}
					else if (error1 != null) {
						mainScope.launch {
							val context = context ?: return@launch
							Toast.makeText(context, error1.text, Toast.LENGTH_SHORT).show()
						}
					}
					else {
						FileLog.e("Unexpected response type: ${response1?.javaClass?.simpleName}")

						mainScope.launch {
							val context = context ?: return@launch
							Toast.makeText(context, context.getString(R.string.unknown_error), Toast.LENGTH_SHORT).show()
						}
					}
				}, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
			else if (error != null) {
				mainScope.launch {
					val context = context ?: return@launch
					Toast.makeText(context, error.text, Toast.LENGTH_SHORT).show()
				}
			}
			else {
				FileLog.e("Unexpected response type: ${response?.javaClass?.simpleName}")

				mainScope.launch {
					val context = context ?: return@launch
					Toast.makeText(context, context.getString(R.string.unknown_error), Toast.LENGTH_SHORT).show()
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	override fun fetchNextFeedPage() {
		if (feedLoadingJob?.isActive != true) {
			feedLoadingJob = ioScope.launch {
				loadFeedData(force = false)
			}
		}
	}

	override fun onOpenComments(messages: List<MessageObject>) {
		val message = messages.firstOrNull() ?: return
		val channel = messages.first().messageOwner?.getChannel() ?: return

		val maxReadId: Int
		val linkedChatId: Long

		if (message.messageOwner?.replies != null) {
			maxReadId = message.messageOwner?.replies?.read_max_id ?: 0
			linkedChatId = message.messageOwner?.replies?.channel_id ?: 0
		}
		else {
			maxReadId = -1
			linkedChatId = 0
		}

		openDiscussionMessageChat(channel.id, message, message.id, linkedChatId.toInt(), maxReadId, message)
	}

	private fun openDiscussionMessageChat(chatId: Long, originalMessage: MessageObject, messageId: Int, maxReadId: Int, highlightMsgId: Int, fallbackMessage: MessageObject?) {
		val chat = messagesController.getChat(chatId) ?: return

		val req = TLRPC.TL_messages_getDiscussionMessage()
		req.peer = MessagesController.getInputPeer(chat)
		req.msg_id = messageId

		FileLog.d("getDiscussionMessage chat = " + chat.id + " msg_id = " + messageId)

		if (commentMessagesRequestId != -1) {
			connectionsManager.cancelRequest(commentMessagesRequestId, false)
		}

		if (commentRequestId != -1) {
			connectionsManager.cancelRequest(commentRequestId, false)
		}

		commentRequestId = connectionsManager.sendRequest(req) { response, _ ->
			commentRequestId = -1

			if (response is TLRPC.TL_messages_discussionMessage) {
				var savedHistory: messages_Messages? = null

				messagesController.putUsers(response.users, false)
				messagesController.putChats(response.chats, false)

				val msgs = response.messages?.mapNotNull { if (it !is TLRPC.TL_messageEmpty) it else null }

				if (!msgs.isNullOrEmpty()) {
					val message = msgs[0]

					val getReplies = TLRPC.TL_messages_getReplies()
					getReplies.peer = messagesController.getInputPeer(message.peer_id!!)
					getReplies.msg_id = message.id
					getReplies.offset_date = 0
					getReplies.limit = 30

					if (highlightMsgId > 0) {
						getReplies.offset_id = highlightMsgId
						getReplies.add_offset = -getReplies.limit / 2
					}
					else {
						getReplies.offset_id = if (maxReadId == 0) 1 else maxReadId
						getReplies.add_offset = -getReplies.limit + 10
					}

					commentMessagesRequestId = connectionsManager.sendRequest(getReplies) inner@{ response2, error2 ->
						commentMessagesRequestId = -1

						if (response2 != null) {
							savedHistory = response2 as messages_Messages
						}
						else {
							if ("CHANNEL_PRIVATE" == error2?.text) {
								mainScope.launch {
									parentActivity?.let {
										val builder = AlertDialog.Builder(it)
										builder.setTitle(context!!.getString(R.string.AppName))
										builder.setMessage(context!!.getString(R.string.JoinByPeekChannelText))
										builder.setPositiveButton(context!!.getString(R.string.OK), null)

										showDialog(builder.create())
									}
								}

								return@inner
							}
						}

						processLoadedDiscussionMessage(response, savedHistory, maxReadId, fallbackMessage, req, chat, highlightMsgId, originalMessage)
					}
				}
				else {
					processLoadedDiscussionMessage(response, savedHistory, maxReadId, fallbackMessage, req, chat, highlightMsgId, originalMessage)
				}
			}
		}
	}

	private fun processLoadedDiscussionMessage(discussionMessage: TLRPC.TL_messages_discussionMessage?, history: messages_Messages?, maxReadId: Int, fallbackMessage: MessageObject?, req: TLRPC.TL_messages_getDiscussionMessage, originalChat: TLRPC.Chat, highlightMsgId: Int, originalMessage: MessageObject?) {
		@Suppress("NAME_SHADOWING") var history: messages_Messages? = history

		if (history != null) {
			if (maxReadId != 1 && maxReadId != 0 && discussionMessage != null && maxReadId != discussionMessage.read_inbox_max_id && highlightMsgId <= 0) {
				history = null
			}
			else if (history.messages.isNotEmpty() && discussionMessage != null && !discussionMessage.messages.isNullOrEmpty()) {
				val message = history.messages[0]
				val replyId = (if (message.reply_to != null) (if (message.reply_to?.reply_to_top_id != 0) message.reply_to?.reply_to_top_id else message.reply_to?.reply_to_msg_id) else 0) ?: 0

				if (replyId != discussionMessage.messages[discussionMessage.messages.size - 1].id) {
					history = null
				}
			}

			FileLog.d("processLoadedDiscussionMessage reset history")
		}

		val arrayList = ArrayList(discussionMessage?.messages?.mapNotNull {
			if (it is TLRPC.TL_messageEmpty) {
				null
			}
			else {
				it.isThreadMessage = true
				MessageObject(UserConfig.selectedAccount, it, generateLayout = true, checkMediaExists = true)
			}
		} ?: emptyList())

		mainScope.launch {
			if (arrayList.isNotEmpty() && discussionMessage != null) {
				val args = Bundle()
				val dialogId = arrayList[0].dialogId
				args.putLong("chat_id", -dialogId)
				args.putInt("message_id", max(1, discussionMessage.read_inbox_max_id))
				args.putInt("unread_count", discussionMessage.unread_count)
				args.putBoolean("historyPreloaded", history != null)

				val chatActivity = ChatActivity(args)
				chatActivity.setThreadMessages(arrayList, originalChat, req.msg_id, discussionMessage.read_inbox_max_id, discussionMessage.read_outbox_max_id)

				if (highlightMsgId != 0) {
					chatActivity.setHighlightMessageId(highlightMsgId)
				}

				if (originalMessage != null && originalMessage.messageOwner?.replies != null && chatActivity.threadMessage?.messageOwner?.replies != null) {
					originalMessage.messageOwner?.replies?.replies = chatActivity.threadMessage?.messageOwner?.replies?.replies
				}

				if (originalMessage != null && originalMessage.messageOwner?.reactions != null) {
					chatActivity.threadMessage?.messageOwner?.reactions = originalMessage.messageOwner?.reactions
				}

				var chatOpened = false

				val openCommentsChat = Runnable {
					if (chatOpened || isFinishing) {
						return@Runnable
					}

					chatOpened = true

					presentFragment(chatActivity)
				}

				if (history != null) {
					var fnid = 0

					if (history.messages.isNotEmpty()) {
						for (a in history.messages.indices.reversed()) {
							val message = history.messages[a]

							if (message.id > maxReadId && !message.out) {
								fnid = message.id
								break
							}
						}
					}

					val historyFinal: messages_Messages = history
					val fnidFinal = fnid
					val commentsClassGuid = chatActivity.classGuid

					val observer = object : NotificationCenter.NotificationCenterDelegate {
						override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
							if (id == NotificationCenter.messagesDidLoad && args[10] as Int == commentsClassGuid) {
								openCommentsChat.run()

								mainScope.launch {
									delay(50)
									chatActivity.didReceivedNotification(id, account, *args)
								}

								notificationCenter.removeObserver(this, NotificationCenter.messagesDidLoad)
							}
						}
					}

					notificationCenter.addObserver(observer, NotificationCenter.messagesDidLoad)

					Utilities.stageQueue.postRunnable {
						messagesController.processLoadedMessages(historyFinal, historyFinal.messages.size, dialogId, 0, 30, if (highlightMsgId > 0) highlightMsgId else maxReadId, 0, false, commentsClassGuid, fnidFinal, 0, 0, 0, if (highlightMsgId > 0) 3 else 2, true, 0, arrayList[arrayList.size - 1].id, 1, false, 0, true)
					}
				}
				else {
					openCommentsChat.run()
				}
			}
			else {
				if (fallbackMessage != null) {
					openOriginalReplyChat(fallbackMessage)
				}
				else {
					if (parentActivity != null) {
						BulletinFactory.of(this@FeedFragment).createErrorBulletin(context!!.getString(R.string.ChannelPostDeleted)).show()
					}
				}
			}
		}
	}

	private fun openOriginalReplyChat(messageObject: MessageObject) {
		val args = Bundle()

		messageObject.messageOwner?.fwd_from?.saved_from_peer?.channel_id?.takeIf { it != 0L }?.let {
			args.putLong("chat_id", it)
		} ?: messageObject.messageOwner?.fwd_from?.saved_from_peer?.chat_id?.takeIf { it != 0L }?.let {
			args.putLong("chat_id", it)
		} ?: messageObject.messageOwner?.fwd_from?.saved_from_peer?.user_id?.takeIf { it != 0L }?.let {
			args.putLong("user_id", it)
		} ?: messageObject.messageOwner?.replies?.channel_id?.takeIf { it != 0L }?.let {
			args.putLong("user_id", it)
		}

		messageObject.messageOwner?.fwd_from?.saved_from_msg_id?.let {
			args.putInt("message_id", it)
		}

		if (args.isEmpty) {
			return
		}

		if (messagesController.checkCanOpenChat(args, this)) {
			presentFragment(ChatActivity(args))
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.messagePlayingProgressDidChanged, NotificationCenter.messagePlayingDidReset, NotificationCenter.messagePlayingGoingToStop, NotificationCenter.messagePlayingPlayStateChanged -> {
				val count = binding?.recyclerView?.childCount ?: return

				for (a in 0 until count) {
					binding?.recyclerView?.getChildAt(a)?.let {
						(binding?.recyclerView?.findContainingViewHolder(it) as? FeedViewHolder)?.notifyAboutPlayback(mediaController.playingMessageObject)
					}
				}
			}

			NotificationCenter.messagesDeleted, NotificationCenter.didReceiveNewMessages, NotificationCenter.dialogsNeedReload -> {
				FileLog.d("Updating feed after notification ${id}…")

				if (feedLoadingJob?.isActive != true) {
					feedLoadingJob = ioScope.launch {
						loadNewMessages()
					}
				}
			}
		}
	}

	private class FeedFilterViewHolder(private val binding: FeedFilterViewHolderBinding) : RecyclerView.ViewHolder(binding.root) {
		var text: String? = null
			set(value) {
				field = value
				binding.filterText.text = value
			}

		var isDropDown = false
			set(value) {
				field = value

				if (value) {
					binding.dropDown.visible()
				}
				else {
					binding.dropDown.gone()
				}
			}
	}

	override fun onRecommendationItemClick(chat: TLRPC.Chat) {
		ioScope.launch {
			var existingChat = messagesController.getChat(chat.id)

			if (existingChat == null) {
				existingChat = messagesController.loadChat(chat.id, 0, true)
			}

			if (existingChat != null) {
				val args = Bundle()
				args.putLong("chat_id", chat.id)

				withContext(mainScope.coroutineContext) {
					if (messagesController.checkCanOpenChat(args, this@FeedFragment)) {
						presentFragment(ChatActivity(args))
					}
				}
			}
		}
	}

	private suspend fun loadRecommendationsData(force: Boolean) {
		if (!force && isLastRecommendationsPage) {
			return
		}

		val req = TL_contacts_search()

		if (force) {
			currentRecommendationsPage = 1
			isLastRecommendationsPage = false
		}

		req.page = currentRecommendationsPage
		req.limit = limit
		req.isRecommended = true
		req.category = recommendationsFilter.category
		req.country = recommendationsFilter.country?.code
		req.genre = recommendationsFilter.genre
		req.isNew = recommendationsFilter.new
		req.isCourse = recommendationsFilter.course
		req.isPaid = recommendationsFilter.paid
		req.isPublic = recommendationsFilter.free

		query?.let {
			req.q = it
		}

		when (val res = connectionsManager.performRequest(req)) {
			is TL_contacts_found -> {
				currentRecommendationsPage += 1

				val chats = res.chats

				messagesController.putChats(chats, false)
				messagesStorage.putUsersAndChats(res.users, chats, true, true)

				isLastRecommendationsPage = chats.size < limit

				recommendationsLoadingJob = null

				withContext(mainScope.coroutineContext) {
					recommendationsAdapter.setRecommendations(chats, append = !force)

					if (binding?.recyclerView?.adapter == recommendationsAdapter) {
						if (recommendationsAdapter.itemCount == 0) {
							showEmptyRecommendationsView()
						}
						else {
							hideEmptyRecommendationsView()
						}
					}
				}
			}

			null -> {
				FileLog.e("TL_contacts_search unknown error")
			}

			else -> {
				FileLog.e("TL_contacts_search error: $res")
			}
		}
	}

	private fun showEmptyRecommendationsView() {
		if (emptyRecommendationsView == null) {
			emptyRecommendationsView = createEmptyRecommendationsView()

			binding?.root?.let {
				val layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
				layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
				layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
				layoutParams.topToBottom = binding?.searchParamsRecyclerView?.id ?: 0
				layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
				layoutParams.verticalBias = 0.4f
				layoutParams.marginStart = AndroidUtilities.dp(16f)
				layoutParams.marginEnd = AndroidUtilities.dp(16f)

				it.addView(emptyRecommendationsView, layoutParams)
			}
		}
	}

	private fun hideEmptyRecommendationsView() {
		emptyRecommendationsView?.let {
			(it.parent as? ViewGroup)?.removeView(it)
		}

		emptyRecommendationsView = null
	}

	override fun fetchNextRecommendationsPage() {
		if (recommendationsLoadingJob?.isActive != true) {
			recommendationsLoadingJob = ioScope.launch {
				loadRecommendationsData(force = false)
			}
		}
	}

	private suspend fun loadExploreData(force: Boolean) {
		if (!force && isLastExplorePage) {
			return
		}

		if (force) {
			currentExplorePage = 0
			isLastExplorePage = false
		}

		val req = TL_feeds_readHistory()
		req.page = currentExplorePage
		req.limit = limit
		req.isExplore = true

		if (feedMode == FeedMode.EXPLORE && exploreAdapter.itemCount == 0) {
			withContext(mainScope.coroutineContext) {
				binding?.exploreProgressBar?.visible()
			}
		}

		val res = connectionsManager.performRequest(req)

		if (res is TL_feeds_historyMessages) {
			currentExplorePage += 1

			val messages = res.messages

			isLastExplorePage = messages.size < limit

			exploreLoadingJob = null

			withContext(mainScope.coroutineContext) {
				exploreAdapter.setItems(items = messages, append = !force, currentAccount = currentAccount)
			}
		}

		withContext(mainScope.coroutineContext) {
			binding?.exploreProgressBar?.gone()
		}
	}

	private fun loadCountries() {
		CountriesDataSource.instance.loadCountries { countries, error ->
			if (error == null && countries != null) {
				this.countries = countries
			}
		}
	}

	private fun loadCategories() {
		val req = ElloRpc.channelCategoriesRequest()

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				if (error == null) {
					if (response is TLRPC.TL_biz_dataRaw) {
						val res = response.readData<ElloRpc.ChannelCategoriesResponse>()

						if (res?.categories != null) {
							categories = res.categories.sorted()
						}
					}
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun loadGenres() {
		val req = ElloRpc.channelMusicGenresRequest()

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				if (error == null) {
					if (response is TLRPC.TL_biz_dataRaw) {
						val res = response.readData<ElloRpc.GenresResponse>()

						if (res?.genres != null) {
							genres = res.genres.map { it.name }.sorted()
						}
					}
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun createEmptyRecommendationsView(): LinearLayout {
		val emptyView = LinearLayout(context)
		emptyView.id = View.generateViewId()
		emptyView.gravity = Gravity.CENTER
		emptyView.setPadding(AndroidUtilities.dp(12f))
		emptyView.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat())
		emptyView.orientation = LinearLayout.VERTICAL

		val emptyImage = RLottieImageView(context!!)
		emptyImage.setAutoRepeat(true)
		emptyImage.setAnimation(R.raw.panda_chat_list_no_results, 160, 160)
		emptyImage.playAnimation()

		emptyView.addView(emptyImage, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

		val textView = TextView(context)
		textView.gravity = Gravity.CENTER
		textView.text = context?.getString(R.string.no_such_recommendations)
		textView.setTextColor(ResourcesCompat.getColor(context!!.resources, R.color.disabled_text, null))

		emptyView.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24f, 6f, 24f, 0f))

		return emptyView
	}

	private inner class RecommendationsFilter {
		var all = true
		var new = false
		var course = false
		var paid = false
		var free = false
		var country: Country? = null

		var category: String? = null
			set(value) {
				field = value

				if (value?.trim()?.lowercase() != "music") {
					genre = null
				}
			}

		var genre: String? = null
			get() {
				if (category?.trim()?.lowercase() == "music") {
					return field
				}

				return null
			}

		fun reset() {
			all = true
			new = false
			course = false
			paid = false
			free = false
			country = null
			category = null
			genre = null
		}

		@SuppressLint("NotifyDataSetChanged")
		fun check() {
			all = !new && !course && !paid && !free && country == null && category == null && genre == null

			binding?.searchParamsRecyclerView?.adapter?.notifyDataSetChanged()

			if (recommendationsLoadingJob?.isActive != true) {
				recommendationsLoadingJob = ioScope.launch {
					loadRecommendationsData(force = true)
				}
			}
		}
	}

	override fun fetchNextExplorePage() {
		if (exploreLoadingJob?.isActive != true) {
			exploreLoadingJob = ioScope.launch {
				loadExploreData(force = false)
			}
		}
	}

	override fun onExploreItemClick(messageObject: MessageObject) {
		val message = messageObject.messageOwner
		val channel = message?.getChannel() ?: return

		val args = Bundle()
		args.putLong("chat_id", channel.id)
		args.putInt("message_id", message.id)

		val fragment = ChatActivity(args)
		presentFragment(fragment)
	}

	companion object {
		private const val SETTINGS = 1
		private const val FEED_POSITION = "feed_position"
		private const val FEED_OFFSET = "feed_offset"
		private const val CURRENT_PAGE = "current_page"
		private const val LAST_READ_ID = "feed_last_read_id"
		private const val DEFAULT_FETCH_LIMIT = 10
		private const val SCROLL_TO_TOP_BUTTON_OFFSET = 54f
		private const val FOR_YOU_TAB = 2
	}
}
