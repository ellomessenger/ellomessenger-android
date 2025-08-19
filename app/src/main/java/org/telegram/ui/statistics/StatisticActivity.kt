/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.statistics

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.collection.ArraySet
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONException
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LruCache
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.StatsGraph
import org.telegram.tgnet.TLRPC.TLChannelsGetMessages
import org.telegram.tgnet.TLRPC.TLStatsBroadcastStats
import org.telegram.tgnet.TLRPC.TLStatsGetBroadcastStats
import org.telegram.tgnet.TLRPC.TLStatsGetMegagroupStats
import org.telegram.tgnet.TLRPC.TLStatsGraph
import org.telegram.tgnet.TLRPC.TLStatsGraphAsync
import org.telegram.tgnet.TLRPC.TLStatsGraphError
import org.telegram.tgnet.TLRPC.TLStatsLoadAsyncGraph
import org.telegram.tgnet.TLRPC.TLStatsMegagroupStats
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.EmptyCell
import org.telegram.ui.Cells.LoadingCell
import org.telegram.ui.Cells.ManageChatTextCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.StatisticPostInfoCell
import org.telegram.ui.Charts.BaseChartCell
import org.telegram.ui.Charts.BaseChartView.SharedUiComponents
import org.telegram.ui.Charts.DiffUtilsCallback
import org.telegram.ui.Charts.data.ChartData
import org.telegram.ui.Charts.data.DoubleLinearChartData
import org.telegram.ui.Charts.data.StackBarChartData
import org.telegram.ui.Charts.data.StackLinearChartData
import org.telegram.ui.Charts.view_data.ChartHeaderView
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatAvatarContainer
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("NotifyDataSetChanged")
class StatisticActivity(args: Bundle) : BaseFragment(args), NotificationCenterDelegate {
	private val chat: ChatFull?

	//mutual
	private var growthData: ChartViewData? = null
	private var topHoursData: ChartViewData? = null

	//channels
	private var overviewChannelData: OverviewChannelData? = null
	private var followersData: ChartViewData? = null
	private var interactionsData: ChartViewData? = null
	private var ivInteractionsData: ChartViewData? = null
	private var viewsBySourceData: ChartViewData? = null
	private var newFollowersBySourceData: ChartViewData? = null
	private var languagesData: ChartViewData? = null
	private var notificationsData: ChartViewData? = null

	//chats
	private var overviewChatData: OverviewChatData? = null
	private var groupMembersData: ChartViewData? = null
	private var newMembersBySourceData: ChartViewData? = null
	private var membersLanguageData: ChartViewData? = null
	private var messagesData: ChartViewData? = null
	private var actionsData: ChartViewData? = null
	private var topDayOfWeeksData: ChartViewData? = null
	private val topMembersAll = mutableListOf<MemberData>()
	private val topMembersVisible = mutableListOf<MemberData>()
	private val topInviters = mutableListOf<MemberData>()
	private val topAdmins = mutableListOf<MemberData>()

	private var recyclerListView: RecyclerListView? = null
	private val layoutManager = LinearLayoutManager(context)
	private val childDataCache = LruCache<ChartData>(50)
	private var adapter: Adapter? = null
	private var animator: RecyclerView.ItemAnimator? = null
	private var lastCancelable: ZoomCancelable? = null
	private var sharedUi: SharedUiComponents? = null
	private var progressLayout: LinearLayout? = null
	private val isMegaGroup: Boolean
	private var maxDateOverview: Long = 0
	private var minDateOverview: Long = 0
	private val progressDialog = arrayOfNulls<AlertDialog>(1)
	private var loadFromId = -1
	private val recentPostIdToIndexMap = SparseIntArray()
	private val recentPostsAll = mutableListOf<RecentPostInfo>()
	private val recentPostsLoaded = mutableListOf<RecentPostInfo>()
	private var messagesIsLoading = false
	private var initialLoading = true
	private var diffUtilsCallback: DiffUtilsCallback? = null
	var avatarContainer: ChatAvatarContainer? = null

	private var hasLoadedPosts = AtomicBoolean(false)

	private val showProgressbar = Runnable {
		progressLayout?.animate()?.alpha(1f)?.duration = 230
	}

	init {
		val chatId = args.getLong("chat_id")
		isMegaGroup = args.getBoolean("is_megagroup", false)
		chat = messagesController.getChatFull(chatId)
	}

	override fun onFragmentCreate(): Boolean {
		if (chat == null) {
			return false
		}

		notificationCenter.addObserver(this, NotificationCenter.messagesDidLoad)

		val req = if (isMegaGroup) {
			val getMegagroupStats = TLStatsGetMegagroupStats()
			getMegagroupStats.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat.id)
			getMegagroupStats
		}
		else {
			val getBroadcastStats = TLStatsGetBroadcastStats()
			getBroadcastStats.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat.id)
			getBroadcastStats
		}

		val reqId = connectionsManager.sendRequest(req, { response, _ ->
			when (response) {
				is TLStatsBroadcastStats -> {
					val chartsViewData = arrayOfNulls<ChartViewData>(9)

					chartsViewData[0] = createViewData(response.ivInteractionsGraph, R.string.IVInteractionsChartTitle, 1)
					chartsViewData[1] = createViewData(response.followersGraph, R.string.FollowersChartTitle, 0)

					chartsViewData[2] = createViewData(response.topHoursGraph, R.string.TopHoursChartTitle, 0)?.apply {
						useHourFormat = true
					}

					chartsViewData[3] = createViewData(response.interactionsGraph, R.string.InteractionsChartTitle, 1)
					chartsViewData[4] = createViewData(response.growthGraph, R.string.GrowthChartTitle, 0)
					chartsViewData[5] = createViewData(response.viewsBySourceGraph, R.string.ViewsBySourceChartTitle, 2)
					chartsViewData[6] = createViewData(response.newFollowersBySourceGraph, R.string.NewFollowersBySourceChartTitle, 2)
					chartsViewData[7] = createViewData(response.languagesGraph, R.string.LanguagesChartTitle, 4, true)
					chartsViewData[8] = createViewData(response.muteGraph, R.string.NotificationsChartTitle, 0)

					overviewChannelData = OverviewChannelData(response)

					maxDateOverview = (response.period?.maxDate ?: 0) * 1000L
					minDateOverview = (response.period?.minDate ?: 0) * 1000L

					recentPostsAll.clear()

					for (i in response.recentMessageInteractions.indices) {
						val recentPostInfo = RecentPostInfo()
						recentPostInfo.counters = response.recentMessageInteractions[i]

						recentPostsAll.add(recentPostInfo)

						recentPostIdToIndexMap.put(recentPostInfo.counters!!.msgId, i)
					}

					if (recentPostsAll.size > 0) {
						val lastPostId = recentPostsAll[0].counters!!.msgId
						val count = recentPostsAll.size

						messagesStorage.getMessages(-chat.id, 0, false, count, lastPostId, 0, 0, classGuid, 0, false, 0, 0, true)
					}

					AndroidUtilities.runOnUIThread {
						ivInteractionsData = chartsViewData[0]
						followersData = chartsViewData[1]
						topHoursData = chartsViewData[2]
						interactionsData = chartsViewData[3]
						growthData = chartsViewData[4]
						viewsBySourceData = chartsViewData[5]
						newFollowersBySourceData = chartsViewData[6]
						languagesData = chartsViewData[7]
						notificationsData = chartsViewData[8]

						dataLoaded(chartsViewData.filterNotNull().toTypedArray())
					}
				}

				is TLStatsMegagroupStats -> {
					val chartsViewData = arrayOfNulls<ChartViewData>(8)
					chartsViewData[0] = createViewData(response.growthGraph, R.string.GrowthChartTitle, 0)
					chartsViewData[1] = createViewData(response.membersGraph, R.string.GroupMembersChartTitle, 0)
					chartsViewData[2] = createViewData(response.newMembersBySourceGraph, R.string.NewMembersBySourceChartTitle, 2)
					chartsViewData[3] = createViewData(response.languagesGraph, R.string.MembersLanguageChartTitle, 4, true)
					chartsViewData[4] = createViewData(response.messagesGraph, R.string.MessagesChartTitle, 2)
					chartsViewData[5] = createViewData(response.actionsGraph, R.string.ActionsChartTitle, 1)

					chartsViewData[6] = createViewData(response.topHoursGraph, R.string.TopHoursChartTitle, 0)?.apply {
						useHourFormat = true
					}

					chartsViewData[7] = createViewData(response.weekdaysGraph, R.string.TopDaysOfWeekChartTitle, 4)?.apply {
						useWeekFormat = true
					}

					overviewChatData = OverviewChatData(response)

					maxDateOverview = (response.period?.maxDate ?: 0) * 1000L
					minDateOverview = (response.period?.minDate ?: 0) * 1000L

					if (response.topPosters.isNotEmpty()) {
						for (i in response.topPosters.indices) {
							val data = MemberData.from(response.topPosters[i], response.users)

							if (topMembersVisible.size < 10) {
								topMembersVisible.add(data)
							}

							topMembersAll.add(data)
						}

						if (topMembersAll.size - topMembersVisible.size < 2) {
							topMembersVisible.clear()
							topMembersVisible.addAll(topMembersAll)
						}
					}

					if (response.topAdmins.isNotEmpty()) {
						for (i in response.topAdmins.indices) {
							topAdmins.add(MemberData.from(response.topAdmins[i], response.users))
						}
					}

					if (response.topInviters.isNotEmpty()) {
						for (i in response.topInviters.indices) {
							topInviters.add(MemberData.from(response.topInviters[i], response.users))
						}
					}

					AndroidUtilities.runOnUIThread {
						growthData = chartsViewData[0]
						groupMembersData = chartsViewData[1]
						newMembersBySourceData = chartsViewData[2]
						membersLanguageData = chartsViewData[3]
						messagesData = chartsViewData[4]
						actionsData = chartsViewData[5]
						topHoursData = chartsViewData[6]
						topDayOfWeeksData = chartsViewData[7]

						dataLoaded(chartsViewData.filterNotNull().toTypedArray())
					}
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)

		connectionsManager.bindRequestToGuid(reqId, classGuid)

		return true
	}

	private fun dataLoaded(chartsViewData: Array<ChartViewData>) {
		adapter?.let {
			it.update()
			recyclerListView?.itemAnimator = null
			it.notifyDataSetChanged()
		}

		initialLoading = false

		if (progressLayout?.visibility == View.VISIBLE) {
			AndroidUtilities.cancelRunOnUIThread(showProgressbar)

			progressLayout?.animate()?.alpha(0f)?.setDuration(230)?.setListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					progressLayout?.gone()
				}
			})

			recyclerListView?.visible()
			recyclerListView?.alpha = 0f
			recyclerListView?.animate()?.alpha(1f)?.setDuration(230)?.start()

			for (data in chartsViewData) {
				if (data.chartData == null && data.token != null) {
					data.load(currentAccount, classGuid, recyclerListView, diffUtilsCallback)
				}
			}
		}
	}

	override fun onFragmentDestroy() {
		notificationCenter.removeObserver(this, NotificationCenter.messagesDidLoad)

		progressDialog[0]?.dismiss()
		progressDialog[0] = null

		super.onFragmentDestroy()
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.messagesDidLoad -> {
				val guid = args[10] as Int

				if (guid == classGuid) {
					val messArr = args[2] as List<MessageObject>
					val deletedMessages = mutableListOf<RecentPostInfo>()
					var n = messArr.size

					for (i in 0 until n) {
						val messageObjectFormCache = messArr[i]
						val index = recentPostIdToIndexMap[messageObjectFormCache.id, -1]

						if (index >= 0 && recentPostsAll[index].counters?.msgId == messageObjectFormCache.id) {
							if (messageObjectFormCache.deleted) {
								deletedMessages.add(recentPostsAll[index])
							}
							else {
								recentPostsAll[index].message = messageObjectFormCache
							}
						}
					}

					recentPostsAll.removeAll(deletedMessages.toSet())

					recentPostsLoaded.clear()

					n = recentPostsAll.size

					for (i in 0 until n) {
						val postInfo = recentPostsAll[i]

						if (postInfo.message == null) {
							loadFromId = postInfo.counters?.msgId ?: -1
							break
						}
						else {
							recentPostsLoaded.add(postInfo)
						}
					}

					if (recentPostsLoaded.size < 20) {
						loadMessages()
					}

					if (adapter != null) {
						recyclerListView?.itemAnimator = null
						diffUtilsCallback?.update()
					}
				}
			}
		}
	}

	override fun createView(context: Context): View? {
		sharedUi = SharedUiComponents()

		val frameLayout = FrameLayout(context)

		fragmentView = frameLayout

		recyclerListView = object : RecyclerListView(context) {
			var lastH = 0
			override fun onMeasure(widthSpec: Int, heightSpec: Int) {
				super.onMeasure(widthSpec, heightSpec)
				if (lastH != measuredHeight) {
					adapter?.notifyDataSetChanged()
				}

				lastH = measuredHeight
			}
		}

		progressLayout = LinearLayout(context)
		progressLayout?.orientation = LinearLayout.VERTICAL

		val imageView = RLottieImageView(context)
		imageView.setAutoRepeat(true)
		imageView.setAnimation(R.raw.statistic_preload, 120, 120)
		imageView.playAnimation()

		val loadingTitle = TextView(context)
		loadingTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		loadingTitle.typeface = Theme.TYPEFACE_BOLD
		loadingTitle.setTextColor(context.getColor(R.color.text))
		loadingTitle.text = context.getString(R.string.LoadingStats)
		loadingTitle.gravity = Gravity.CENTER_HORIZONTAL

		val loadingSubtitle = TextView(context)
		loadingSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		loadingSubtitle.setTextColor(context.getColor(R.color.dark_gray))
		loadingSubtitle.text = context.getString(R.string.LoadingStatsDescription)
		loadingSubtitle.gravity = Gravity.CENTER_HORIZONTAL

		progressLayout?.addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20))
		progressLayout?.addView(loadingTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10))
		progressLayout?.addView(loadingSubtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))

		frameLayout.addView(progressLayout, LayoutHelper.createFrame(240, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, 0f, 0f, 30f))

		if (adapter == null) {
			adapter = Adapter()
		}

		recyclerListView?.adapter = adapter

		recyclerListView?.layoutManager = layoutManager

		animator = object : DefaultItemAnimator() {
			override fun getAddAnimationDelay(removeDuration: Long, moveDuration: Long, changeDuration: Long): Long {
				return removeDuration
			}
		}

		recyclerListView?.itemAnimator = null

		recyclerListView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				if (recentPostsAll.size != recentPostsLoaded.size) {
					val adapter = adapter ?: return

					if (!messagesIsLoading && layoutManager.findLastVisibleItemPosition() > adapter.itemCount - 20) {
						loadMessages()
					}
				}
			}
		})

		recyclerListView?.setOnItemClickListener { _, position ->
			val adapter = adapter ?: return@setOnItemClickListener

			if (position >= adapter.recentPostsStartRow && position <= adapter.recentPostsEndRow) {
				val messageObject = recentPostsLoaded[position - adapter.recentPostsStartRow].message
				val activity = MessageStatisticActivity(messageObject!!)
				presentFragment(activity)
			}
			else if (position >= adapter.topAdminsStartRow && position <= adapter.topAdminsEndRow) {
				val i = position - adapter.topAdminsStartRow
				topAdmins[i].onClick(this)
			}
			else if (position >= adapter.topMembersStartRow && position <= adapter.topMembersEndRow) {
				val i = position - adapter.topMembersStartRow
				topMembersVisible[i].onClick(this)
			}
			else if (position >= adapter.topInviterStartRow && position <= adapter.topInviterEndRow) {
				val i = position - adapter.topInviterStartRow
				topInviters[i].onClick(this)
			}
			else if (position == adapter.expandTopMembersRow) {
				val newCount = topMembersAll.size - topMembersVisible.size
				val p = adapter.expandTopMembersRow
				topMembersVisible.clear()
				topMembersVisible.addAll(topMembersAll)

				adapter.update()

				recyclerListView?.itemAnimator = animator

				adapter.notifyItemRangeInserted(p + 1, newCount)
				adapter.notifyItemRemoved(p)
			}
		}

		recyclerListView?.setOnItemLongClickListener { _, position ->
			val adapter = adapter ?: return@setOnItemLongClickListener false

			if (position >= adapter.recentPostsStartRow && position <= adapter.recentPostsEndRow) {
				val messageObject = recentPostsLoaded[position - adapter.recentPostsStartRow].message
				val items = mutableListOf<String?>()
				val actions = mutableListOf<Int>()
				val icons = mutableListOf<Int>()

				items.add(context.getString(R.string.ViewMessageStatistic))

				actions.add(0)

				icons.add(R.drawable.msg_stats)

				items.add(context.getString(R.string.ViewMessage))

				actions.add(1)

				icons.add(R.drawable.msg_msgbubble3)

				val builder = AlertDialog.Builder(context)

				builder.setItems(items.toTypedArray(), AndroidUtilities.toIntArray(icons)) { _, i ->
					if (i == 0) {
						val activity = MessageStatisticActivity(messageObject!!)
						presentFragment(activity)
					}
					else if (i == 1) {
						val bundle = Bundle()
						bundle.putLong("chat_id", chat?.id ?: 0)
						bundle.putInt("message_id", messageObject?.id ?: 0)
						bundle.putBoolean("need_remove_previous_same_chat_activity", false)

						val chatActivity = ChatActivity(bundle)

						presentFragment(chatActivity, false)
					}
				}

				showDialog(builder.create())

				return@setOnItemLongClickListener false
			}
			else if (position >= adapter.topAdminsStartRow && position <= adapter.topAdminsEndRow) {
				val i = position - adapter.topAdminsStartRow
				topAdmins[i].onLongClick(chat!!, this, progressDialog)
				return@setOnItemLongClickListener true
			}
			else if (position >= adapter.topMembersStartRow && position <= adapter.topMembersEndRow) {
				val i = position - adapter.topMembersStartRow
				topMembersVisible[i].onLongClick(chat!!, this, progressDialog)
				return@setOnItemLongClickListener true
			}
			else if (position >= adapter.topInviterStartRow && position <= adapter.topInviterEndRow) {
				val i = position - adapter.topInviterStartRow
				topInviters[i].onLongClick(chat!!, this, progressDialog)
				return@setOnItemLongClickListener true
			}
			else {
				return@setOnItemLongClickListener false
			}
		}

		frameLayout.addView(recyclerListView)

		avatarContainer = ChatAvatarContainer(context, null, false)
		avatarContainer?.setOccupyStatusBar(!AndroidUtilities.isTablet())

		actionBar?.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, (if (!inPreviewMode) 56 else 0).toFloat(), 0f, 40f, 0f))

		val chatLocal = messagesController.getChat(chat!!.id)

		avatarContainer?.setChatAvatar(chatLocal)
		avatarContainer?.setTitle(chatLocal?.title)
		avatarContainer?.setSubtitle(context.getString(R.string.ViewAnalytics))

		actionBar?.backButtonDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_back_arrow, null)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		avatarContainer?.setTitleColors(context.getColor(R.color.text), context.getColor(R.color.dark_gray))

		if (initialLoading) {
			progressLayout?.alpha = 0f
			AndroidUtilities.runOnUIThread(showProgressbar, 500)
			progressLayout?.visible()
			recyclerListView?.gone()
		}
		else {
			AndroidUtilities.cancelRunOnUIThread(showProgressbar)
			progressLayout?.gone()
			recyclerListView?.visible()
		}

		diffUtilsCallback = DiffUtilsCallback(adapter!!, layoutManager)

		return fragmentView
	}

	inner class Adapter : SelectionAdapter() {
		private var overviewHeaderCell = -1
		private var overviewCell = 0
		var growCell = -1
		private var progressCell = -1

		// channels
		var followersCell = -1
		var topHoursCell = -1
		var interactionsCell = -1
		var ivInteractionsCell = -1
		var viewsBySourceCell = -1
		var newFollowersBySourceCell = -1
		var languagesCell = -1
		var notificationsCell = -1
		private var recentPostsHeaderCell = -1
		var recentPostsStartRow = -1
		var recentPostsEndRow = -1

		//megagroup
		var groupMembersCell = -1
		var newMembersBySourceCell = -1
		var membersLanguageCell = -1
		var messagesCell = -1
		var actionsCell = -1
		var topDayOfWeeksCell = -1
		private var topMembersHeaderCell = -1
		var topMembersStartRow = -1
		var topMembersEndRow = -1
		private var topAdminsHeaderCell = -1
		var topAdminsStartRow = -1
		var topAdminsEndRow = -1
		private var topInviterHeaderCell = -1
		var topInviterStartRow = -1
		var topInviterEndRow = -1
		var expandTopMembersRow = -1
		private val shadowDivideCells = ArraySet<Int>()
		private val emptyCells = ArraySet<Int>()
		var count = 0

		override fun getItemViewType(position: Int): Int {
			return if (position == growCell || position == followersCell || position == topHoursCell || position == notificationsCell || position == actionsCell || position == groupMembersCell) {
				0
			}
			else if (position == interactionsCell || position == ivInteractionsCell) {
				1
			}
			else if (position == viewsBySourceCell || position == newFollowersBySourceCell || position == newMembersBySourceCell || position == messagesCell) {
				2
			}
			else if (position == languagesCell || position == membersLanguageCell || position == topDayOfWeeksCell) {
				4
			}
			else if (position in recentPostsStartRow..recentPostsEndRow) {
				9
			}
			else if (position == progressCell) {
				11
			}
			else if (emptyCells.contains(position)) {
				12
			}
			else if (position == recentPostsHeaderCell || position == overviewHeaderCell || position == topAdminsHeaderCell || position == topMembersHeaderCell || position == topInviterHeaderCell) {
				13
			}
			else if (position == overviewCell) {
				14
			}
			else if (position in topAdminsStartRow..topAdminsEndRow || position in topMembersStartRow..topMembersEndRow || position in topInviterStartRow..topInviterEndRow) {
				9
			}
			else if (position == expandTopMembersRow) {
				15
			}
			else {
				10
			}
		}

		override fun getItemId(position: Int): Long {
			if (position in recentPostsStartRow until recentPostsEndRow) {
				return recentPostsLoaded[position - recentPostsStartRow].counters?.msgId?.toLong() ?: 0L
			}

			return when (position) {
				growCell -> 1
				followersCell -> 2
				topHoursCell -> 3
				interactionsCell -> 4
				notificationsCell -> 5
				ivInteractionsCell -> 6
				viewsBySourceCell -> 7
				newFollowersBySourceCell -> 8
				languagesCell -> 9
				groupMembersCell -> 10
				newMembersBySourceCell -> 11
				membersLanguageCell -> 12
				messagesCell -> 13
				actionsCell -> 14
				topDayOfWeeksCell -> 15
				else -> super.getItemId(position)
			}
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val v: View

			when (viewType) {
				in 0..4 -> {
					v = object : ChartCell(parent.context, viewType, sharedUi) {
						override fun onDraw(canvas: Canvas) {
							if (translationY != 0f) {
								canvas.drawColor(parent.context.getColor(R.color.background))
							}

							super.onDraw(canvas)
						}
					}

					v.setWillNotDraw(false)
				}

				9 -> {
					v = object : StatisticPostInfoCell(parent.context, chat!!) {
						override fun onDraw(canvas: Canvas) {
							if (translationY != 0f) {
								canvas.drawColor(parent.context.getColor(R.color.background))
							}

							super.onDraw(canvas)
						}
					}

					v.setWillNotDraw(false)
				}

				11 -> {
					v = LoadingCell(parent.context)
					v.setBackgroundColor(parent.context.getColor(R.color.background))
				}

				12 -> {
					v = EmptyCell(parent.context, AndroidUtilities.dp(15f))
				}

				13 -> {
					val headerCell: ChartHeaderView = object : ChartHeaderView(parent.context) {
						override fun onDraw(canvas: Canvas) {
							if (translationY != 0f) {
								canvas.drawColor(parent.context.getColor(R.color.background))
							}

							super.onDraw(canvas)
						}
					}

					headerCell.setWillNotDraw(false)
					headerCell.setPadding(headerCell.paddingLeft, AndroidUtilities.dp(16f), headerCell.right, AndroidUtilities.dp(16f))

					v = headerCell
				}

				14 -> {
					v = OverviewCell(parent.context)
				}

				15 -> {
					v = ManageChatTextCell(parent.context)
					v.setBackgroundColor(parent.context.getColor(R.color.background))
					v.setColors(parent.context.getColor(R.color.brand), parent.context.getColor(R.color.brand))
				}

				else -> {
					v = ShadowSectionCell(parent.context, 12, parent.context.getColor(R.color.light_background))
				}
			}

			v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

			return RecyclerListView.Holder(v)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val type = getItemViewType(position)

			if (type in 0..4) {
				val data = when (position) {
					growCell -> growthData
					followersCell -> followersData
					interactionsCell -> interactionsData
					viewsBySourceCell -> viewsBySourceData
					newFollowersBySourceCell -> newFollowersBySourceData
					ivInteractionsCell -> ivInteractionsData
					topHoursCell -> topHoursData
					notificationsCell -> notificationsData
					groupMembersCell -> groupMembersData
					newMembersBySourceCell -> newMembersBySourceData
					membersLanguageCell -> membersLanguageData
					messagesCell -> messagesData
					actionsCell -> actionsData
					topDayOfWeeksCell -> topDayOfWeeksData
					else -> languagesData
				}

				(holder.itemView as ChartCell).updateData(data, false)
			}
			else if (type == 9) {
				if (isMegaGroup) {
					when (position) {
						in topAdminsStartRow..topAdminsEndRow -> {
							val i = position - topAdminsStartRow
							(holder.itemView as StatisticPostInfoCell).setData(topAdmins[i])
						}

						in topMembersStartRow..topMembersEndRow -> {
							val i = position - topMembersStartRow
							(holder.itemView as StatisticPostInfoCell).setData(topMembersVisible[i])
						}

						in topInviterStartRow..topInviterEndRow -> {
							val i = position - topInviterStartRow
							(holder.itemView as StatisticPostInfoCell).setData(topInviters[i])
						}
					}
				}
				else {
					val i = position - recentPostsStartRow
					(holder.itemView as StatisticPostInfoCell).setData(recentPostsLoaded[i])
				}
			}
			else if (type == 13) {
				val headerCell = holder.itemView as ChartHeaderView
				headerCell.setDates(minDateOverview, maxDateOverview)

				when (position) {
					overviewHeaderCell -> {
						headerCell.setTitle(headerCell.context.getString(R.string.StatisticOverview))
					}

					topAdminsHeaderCell -> {
						headerCell.setTitle(headerCell.context.getString(R.string.TopAdmins))
					}

					topInviterHeaderCell -> {
						headerCell.setTitle(headerCell.context.getString(R.string.TopInviters))
					}

					topMembersHeaderCell -> {
						headerCell.setTitle(headerCell.context.getString(R.string.TopMembers))
					}

					else -> {
						headerCell.setTitle(headerCell.context.getString(R.string.RecentPosts))
					}
				}
			}
			else if (type == 14) {
				val overviewCell = holder.itemView as OverviewCell

				if (isMegaGroup) {
					overviewCell.setData(overviewChatData)
				}
				else {
					overviewCell.setData(overviewChannelData)
				}
			}
			else if (type == 15) {
				val manageChatTextCell = holder.itemView as ManageChatTextCell
				manageChatTextCell.setText(LocaleController.formatPluralString("ShowVotes", topMembersAll.size - topMembersVisible.size), null, R.drawable.arrow_more, false)
			}
		}

		override fun getItemCount(): Int {
			return count
		}

		fun update() {
			growCell = -1
			followersCell = -1
			interactionsCell = -1
			viewsBySourceCell = -1
			newFollowersBySourceCell = -1
			languagesCell = -1
			recentPostsStartRow = -1
			recentPostsEndRow = -1
			progressCell = -1
			recentPostsHeaderCell = -1
			ivInteractionsCell = -1
			topHoursCell = -1
			notificationsCell = -1
			groupMembersCell = -1
			newMembersBySourceCell = -1
			membersLanguageCell = -1
			messagesCell = -1
			actionsCell = -1
			topDayOfWeeksCell = -1
			topMembersHeaderCell = -1
			topMembersStartRow = -1
			topMembersEndRow = -1
			topAdminsHeaderCell = -1
			topAdminsStartRow = -1
			topAdminsEndRow = -1
			topInviterHeaderCell = -1
			topInviterStartRow = -1
			topInviterEndRow = -1
			expandTopMembersRow = -1
			count = 0
			emptyCells.clear()
			shadowDivideCells.clear()

			if (isMegaGroup) {
				if (overviewChatData != null) {
					overviewHeaderCell = count++
					overviewCell = count++
				}

				if (growthData?.isEmpty == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					growCell = count++
				}

				if (groupMembersData?.isEmpty == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					groupMembersCell = count++
				}

				if (newMembersBySourceData?.isEmpty == false && newMembersBySourceData?.isError == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					newMembersBySourceCell = count++
				}

				if (membersLanguageData?.isEmpty == false && membersLanguageData?.isError == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					membersLanguageCell = count++
				}

				if (messagesData?.isEmpty == false && messagesData?.isError == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					messagesCell = count++
				}

				if (actionsData?.isEmpty == false && actionsData?.isError == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					actionsCell = count++
				}

				if (topHoursData?.isEmpty == false && topHoursData?.isError == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					topHoursCell = count++
				}

				if (topDayOfWeeksData?.isEmpty == false && topDayOfWeeksData?.isError == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					topDayOfWeeksCell = count++
				}

				if (topMembersVisible.size > 0) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					topMembersHeaderCell = count++
					topMembersStartRow = count++
					topMembersEndRow = topMembersStartRow + topMembersVisible.size - 1
					count = topMembersEndRow
					count++

					if (topMembersVisible.size != topMembersAll.size) {
						expandTopMembersRow = count++
					}
					else {
						emptyCells.add(count++)
					}
				}

				if (topAdmins.size > 0) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					topAdminsHeaderCell = count++
					topAdminsStartRow = count++
					topAdminsEndRow = topAdminsStartRow + topAdmins.size - 1
					count = topAdminsEndRow
					count++
					emptyCells.add(count++)
				}

				if (topInviters.size > 0) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					topInviterHeaderCell = count++
					topInviterStartRow = count++
					topInviterEndRow = topInviterStartRow + topInviters.size - 1
					count = topInviterEndRow
					count++
				}

				if (count > 0) {
					emptyCells.add(count++)
					shadowDivideCells.add(count++)
				}
			}
			else {
				if (overviewChannelData != null) {
					overviewHeaderCell = count++
					overviewCell = count++
				}

				if (growthData?.isEmpty == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					growCell = count++
				}

				if (followersData?.isEmpty == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					followersCell = count++
				}

				if (notificationsData?.isEmpty == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					notificationsCell = count++
				}

				if (topHoursData?.isEmpty == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					topHoursCell = count++
				}

				if (viewsBySourceData?.isEmpty == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					viewsBySourceCell = count++
				}

				if (newFollowersBySourceData?.isEmpty == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					newFollowersBySourceCell = count++
				}

				if (languagesData?.isEmpty == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					languagesCell = count++
				}

				if (interactionsData?.isEmpty == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					interactionsCell = count++
				}

				if (ivInteractionsData?.loading == false && ivInteractionsData?.isError == false) {
					if (count > 0) {
						shadowDivideCells.add(count++)
					}

					ivInteractionsCell = count++
				}

				shadowDivideCells.add(count++)

				// MARK: uncomment to enable recent posts stats
//				if (recentPostsAll.size > 0) {
//					recentPostsHeaderCell = count++
//					recentPostsStartRow = count++
//					recentPostsEndRow = recentPostsStartRow + recentPostsLoaded.size - 1
//					count = recentPostsEndRow
//					count++
//
//					if (recentPostsLoaded.size != recentPostsAll.size) {
//						progressCell = count++
//					}
//					else {
//						emptyCells.add(count++)
//					}
//
//					shadowDivideCells.add(count++)
//				}
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType == 9 || holder.itemViewType == 15
		}
	}

	open inner class ChartCell(context: Context, type: Int, sharedUi: SharedUiComponents?) : BaseChartCell(context, type, sharedUi) {
		override fun zoomCanceled() {
			cancelZoom()
		}

		override fun onZoomed() {
			val data = data ?: return

			if (data.activeZoom > 0) {
				return
			}

			performClick()

			if (chartView.legendSignatureView?.canGoZoom != true) {
				return
			}

			val x = chartView.selectedDate

			if (chartType == 4) {
				data.childChartData = StackLinearChartData(data.chartData!!, x)
				zoomChart(false)
				return
			}

			if (data.zoomToken == null) {
				return
			}

			cancelZoom()

			val cacheKey = data.zoomToken + "_" + x
			val dataFromCache = childDataCache[cacheKey]

			if (dataFromCache != null) {
				data.childChartData = dataFromCache
				zoomChart(false)
				return
			}

			val request = TLStatsLoadAsyncGraph()
			request.token = data.zoomToken

			if (x != 0L) {
				request.x = x
				request.flags = request.flags or 1
			}

			val finalCancelable = ZoomCancelable()

			lastCancelable = finalCancelable

			finalCancelable.adapterPosition = recyclerListView?.getChildAdapterPosition(this@ChartCell) ?: 0

			chartView.legendSignatureView?.showProgress(show = true, force = false)

			val reqId = connectionsManager.sendRequest(request, { response, _ ->
				var childData: ChartData? = null

				if (response is TLStatsGraph) {
					val json = response.json?.data

					if (!json.isNullOrEmpty()) {
						try {
							childData = createChartData(JSONObject(json), data.graphType, data === languagesData)
						}
						catch (e: JSONException) {
							FileLog.e(e)
						}
					}
				}
				else if (response is TLStatsGraphError) {
					Toast.makeText(context, response.error, Toast.LENGTH_LONG).show()
				}

				val finalChildData = childData

				AndroidUtilities.runOnUIThread {
					if (finalChildData != null) {
						childDataCache.put(cacheKey, finalChildData)
					}

					if (finalChildData != null && !finalCancelable.canceled && finalCancelable.adapterPosition >= 0) {
						val view = layoutManager.findViewByPosition(finalCancelable.adapterPosition)

						if (view is ChartCell) {
							data.childChartData = finalChildData
							view.chartView.legendSignatureView?.showProgress(show = false, force = false)
							view.zoomChart(false)
						}
					}

					cancelZoom()
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)

			connectionsManager.bindRequestToGuid(reqId, classGuid)
		}

		override fun loadData(viewData: ChartViewData) {
			viewData.load(currentAccount, classGuid, recyclerListView, diffUtilsCallback)
		}
	}

	private fun cancelZoom() {
		lastCancelable?.canceled = true

		recyclerListView?.children?.forEach {
			if (it is ChartCell) {
				it.chartView.legendSignatureView?.showProgress(show = false, force = true)
			}
		}
	}

	private fun loadMessages() {
		if (!hasLoadedPosts.compareAndSet(false, true)) {
			return
		}

		val req = TLChannelsGetMessages()

		val index = recentPostIdToIndexMap[loadFromId]
		val n = recentPostsAll.size
		var count = 0

		for (i in index until n) {
			if (recentPostsAll[i].message == null) {
				req.id.add(recentPostsAll[i].counters!!.msgId.let { msgId ->
					TLRPC.TLInputMessageID().also { it.id = msgId }
				})

				count++

				if (count > 50) {
					break
				}
			}
		}

		req.channel = messagesController.getInputChannel(chat!!.id)

		messagesIsLoading = true

		connectionsManager.sendRequest(req) { response, error ->
			if (error == null) {
				hasLoadedPosts.set(true)
			}

			val messageObjects = mutableListOf<MessageObject>()

			if (response is TLRPC.MessagesMessages) {
				val messages = response.messages

				for (i in messages.indices) {
					messageObjects.add(MessageObject(currentAccount, messages[i], generateLayout = false, checkMediaExists = true))
				}

				messagesStorage.putMessages(messages, false, true, true, 0, false)
			}

			AndroidUtilities.runOnUIThread {
				messagesIsLoading = false

				if (messageObjects.isNotEmpty()) {
					var size = messageObjects.size

					for (i in 0 until size) {
						val messageObjectFormCache = messageObjects[i]
						val localIndex = recentPostIdToIndexMap[messageObjectFormCache.id, -1]

						if (localIndex >= 0 && recentPostsAll[localIndex].counters!!.msgId == messageObjectFormCache.id) {
							recentPostsAll[localIndex].message = messageObjectFormCache
						}
					}

					recentPostsLoaded.clear()

					size = recentPostsAll.size

					for (i in 0 until size) {
						val postInfo = recentPostsAll[i]

						if (postInfo.message == null) {
							loadFromId = postInfo.counters!!.msgId
							break
						}
						else {
							recentPostsLoaded.add(postInfo)
						}
					}
				}

				recyclerListView?.itemAnimator = null
				diffUtilsCallback?.update()
			}
		}
	}

//	private fun recolorRecyclerItem(child: View) {
//		when (child) {
//			is ChartCell -> {
//				child.recolor()
//			}
//
//			is ShadowSectionCell -> {
//				val shadowDrawable = Theme.getThemedDrawable(child.context, R.drawable.greydivider, child.context.getColor(R.color.shadow))
//				val background: Drawable = ColorDrawable(child.context.getColor(R.color.light_background))
//				val combinedDrawable = CombinedDrawable(background, shadowDrawable, 0, 0)
//				combinedDrawable.setFullSize(true)
//				child.setBackground(combinedDrawable)
//			}
//
//			is ChartHeaderView -> {
//				child.recolor()
//			}
//
//			is OverviewCell -> {
//				child.updateColors()
//			}
//		}
//	}

	override fun isLightStatusBar(): Boolean {
		return !AndroidUtilities.isDarkTheme()
	}

	companion object {
		fun createViewData(graph: StatsGraph?, @StringRes titleResId: Int, graphType: Int, isLanguages: Boolean): ChartViewData? {
			if (graph == null || graph is TLStatsGraphError) {
				return null
			}

			val title = ApplicationLoader.applicationContext.getString(titleResId)

			var viewData: ChartViewData? = null

			if (graph is TLStatsGraph) {
				val json = graph.json?.data

				if (json.isNullOrEmpty()) {
					return null
				}

				try {
					viewData = ChartViewData(title, graphType)
					viewData.isLanguages = isLanguages
					viewData.chartData = createChartData(JSONObject(json), graphType, isLanguages)
					viewData.zoomToken = graph.zoomToken

					if ((viewData.chartData?.x?.size ?: 0) < 2) {
						viewData.isEmpty = true
					}

					if (graphType == 4 && (viewData.chartData?.x?.size ?: 0) > 0) {
						val x = viewData.chartData!!.x[viewData.chartData!!.x.size - 1]
						viewData.childChartData = StackLinearChartData(viewData.chartData!!, x)
						viewData.activeZoom = x
					}
				}
				catch (e: JSONException) {
					FileLog.e(e)
					return null
				}
			}
			else if (graph is TLStatsGraphAsync) {
				viewData = ChartViewData(title, graphType)
				viewData.isLanguages = isLanguages
				viewData.token = graph.token
			}

			return viewData
		}

		private fun createViewData(graph: StatsGraph?, @StringRes titleResId: Int, graphType: Int): ChartViewData? {
			return createViewData(graph, titleResId, graphType, false)
		}

		@Throws(JSONException::class)
		fun createChartData(jsonObject: JSONObject, graphType: Int, isLanguages: Boolean): ChartData? {
			return try {
				when (graphType) {
					0 -> ChartData(jsonObject)
					1 -> DoubleLinearChartData(jsonObject)
					2 -> StackBarChartData(jsonObject)
					4 -> StackLinearChartData(jsonObject, isLanguages)
					else -> null
				}
			}
			catch (e: Throwable) {
				throw e
			}
		}
	}
}
