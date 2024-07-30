/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.statistics

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.collection.ArraySet
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.json.JSONException
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject.isChannel
import org.telegram.messenger.DialogObject
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LruCache
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.TL_inputPeerEmpty
import org.telegram.tgnet.TLRPC.TL_statsGraph
import org.telegram.tgnet.TLRPC.TL_statsGraphError
import org.telegram.tgnet.TLRPC.TL_stats_getMessagePublicForwards
import org.telegram.tgnet.TLRPC.TL_stats_getMessageStats
import org.telegram.tgnet.TLRPC.TL_stats_loadAsyncGraph
import org.telegram.tgnet.TLRPC.TL_stats_messageStats
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_messages_messagesSlice
import org.telegram.tgnet.tlrpc.messages_Messages
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.EmptyCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.LoadingCell
import org.telegram.ui.Cells.ManageChatUserCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Charts.BaseChartCell
import org.telegram.ui.Charts.BaseChartView.SharedUiComponents
import org.telegram.ui.Charts.data.ChartData
import org.telegram.ui.Charts.data.StackLinearChartData
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatAvatarContainer
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.FixedWidthSpan
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import kotlin.math.abs

@SuppressLint("NotifyDataSetChanged")
class MessageStatisticActivity(private val messageObject: MessageObject) : BaseFragment(), NotificationCenterDelegate {
	private var chatId: Long = 0
	private var messageId = 0
	var shadowDivideCells = ArraySet<Int>()
	var thumbImage: ImageReceiver? = null
	var drawPlay = false
	private var chat: ChatFull?
	private var listViewAdapter: ListAdapter? = null
	private var emptyView: EmptyTextProgressView? = null
	private var listView: RecyclerListView? = null
	private var layoutManager: LinearLayoutManager? = null
	private var interactionsViewData: ChartViewData? = null
	private val childDataCache = LruCache<ChartData>(15)
	private var lastCancelable: ZoomCancelable? = null
	private val messages = ArrayList<Message>()
	private var statsLoaded = false
	private var loading = false
	private var firstLoaded = false
	private var headerRow = 0
	private var startRow = 0
	private var endRow = 0
	private var loadingRow = 0
	private var interactionsChartRow = 0
	private var overviewRow = 0
	private var overviewHeaderRow = 0
	private var emptyRow = 0
	private var rowCount = 0
	private var imageView: RLottieImageView? = null
	private var progressLayout: LinearLayout? = null
	private var nextRate = 0
	private var publicChats = 0
	private var endReached = false
	private var listContainer: FrameLayout? = null
	private var avatarContainer: ChatAvatarContainer? = null
	private var sharedUi: SharedUiComponents? = null

	private val showProgressbar = Runnable {
		progressLayout?.animate()?.alpha(1f)?.duration = 230
	}

	init {
		if (messageObject.messageOwner?.fwd_from == null) {
			chatId = messageObject.chatId
			messageId = messageObject.id
		}
		else {
			chatId = -messageObject.fromChatId
			messageId = messageObject.messageOwner?.fwd_msg_id ?: 0
		}

		chat = messagesController.getChatFull(chatId)
	}

	private fun updateRows() {
		shadowDivideCells.clear()
		headerRow = -1
		startRow = -1
		endRow = -1
		loadingRow = -1
		interactionsChartRow = -1
		overviewHeaderRow = -1
		overviewRow = -1
		rowCount = 0

		if (firstLoaded && statsLoaded) {
			AndroidUtilities.cancelRunOnUIThread(showProgressbar)

			if (listContainer?.visibility == View.GONE) {
				progressLayout?.animate()?.alpha(0f)?.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						progressLayout?.gone()
					}
				})

				listContainer?.visible()
				listContainer?.alpha = 0f
				listContainer?.animate()?.alpha(1f)?.start()
			}

			overviewHeaderRow = rowCount++
			overviewRow = rowCount++
			shadowDivideCells.add(rowCount++)

			if (interactionsViewData != null) {
				interactionsChartRow = rowCount++
				shadowDivideCells.add(rowCount++)
			}

			if (messages.isNotEmpty()) {
				headerRow = rowCount++
				startRow = rowCount
				rowCount += messages.size
				endRow = rowCount
				emptyRow = rowCount++
				shadowDivideCells.add(rowCount++)

				if (!endReached) {
					loadingRow = rowCount++
				}
			}
		}

		listViewAdapter?.notifyDataSetChanged()
	}

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()

		if (chat != null) {
			loadStat()
			loadChats(100)
		}
		else {
			messagesController.loadFullChat(chatId, classGuid, true)
		}

		notificationCenter.addObserver(this, NotificationCenter.chatInfoDidLoad)

		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		notificationCenter.removeObserver(this, NotificationCenter.chatInfoDidLoad)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.chatInfoDidLoad) {
			val chatFull = args[0] as ChatFull

			if (chat == null && chatFull.id == chatId) {
				val chatLocal = messagesController.getChat(chatId)

				if (chatLocal != null) {
					avatarContainer?.setChatAvatar(chatLocal)
					avatarContainer?.setTitle(chatLocal.title)
				}

				chat = chatFull

				loadStat()
				loadChats(100)
				updateMenu()
			}
		}
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		fragmentView = FrameLayout(context)
		fragmentView?.setBackgroundResource(R.color.light_background)

		val frameLayout = fragmentView as FrameLayout

		emptyView = EmptyTextProgressView(context)
		emptyView?.setText(context.getString(R.string.NoResult))
		emptyView?.gone()

		progressLayout = LinearLayout(context)
		progressLayout?.orientation = LinearLayout.VERTICAL

		imageView = RLottieImageView(context)
		imageView?.setAutoRepeat(true)
		imageView?.setAnimation(R.raw.statistic_preload, 120, 120)
		imageView?.playAnimation()

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
		progressLayout?.alpha = 0f

		frameLayout.addView(progressLayout, LayoutHelper.createFrame(240, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, 0f, 0f, 30f))

		listView = RecyclerListView(context)

		listView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false).also {
			layoutManager = it
		}

		(listView?.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

		listView?.adapter = ListAdapter(context).also { listViewAdapter = it }

		listView?.verticalScrollbarPosition = if (LocaleController.isRTL) View.SCROLLBAR_POSITION_LEFT else View.SCROLLBAR_POSITION_RIGHT

		listView?.setOnItemClickListener { _, position ->
			if (position in startRow until endRow) {
				val message = messages[position - startRow]
				val did = MessageObject.getDialogId(message)
				val args = Bundle()

				if (DialogObject.isUserDialog(did)) {
					args.putLong("user_id", did)
				}
				else {
					args.putLong("chat_id", -did)
				}

				args.putInt("message_id", message.id)
				args.putBoolean("need_remove_previous_same_chat_activity", false)

				if (messagesController.checkCanOpenChat(args, this)) {
					presentFragment(ChatActivity(args))
				}
			}
		}

		listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				// unused
			}

			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				val firstVisibleItem = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
				val visibleItemCount = if (firstVisibleItem == RecyclerView.NO_POSITION) 0 else abs(layoutManager!!.findLastVisibleItemPosition() - firstVisibleItem) + 1
				val totalItemCount = recyclerView.adapter!!.itemCount

				if (visibleItemCount > 0) {
					if (!endReached && !loading && messages.isNotEmpty() && firstVisibleItem + visibleItemCount >= totalItemCount - 5 && statsLoaded) {
						loadChats(100)
					}
				}
			}
		})

		emptyView?.showTextView()

		listContainer = FrameLayout(context)
		listContainer?.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		listContainer?.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		listContainer?.gone()

		frameLayout.addView(listContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		AndroidUtilities.runOnUIThread(showProgressbar, 300)
		updateRows()

		listView?.setEmptyView(emptyView)

		avatarContainer = object : ChatAvatarContainer(context, null, false) {
			override fun dispatchDraw(canvas: Canvas) {
				super.dispatchDraw(canvas)

				thumbImage?.setImageCoordinates(avatarContainer!!.subtitleTextView.x, avatarContainer!!.subtitleTextView.y, AndroidUtilities.dp(18f).toFloat(), AndroidUtilities.dp(18f).toFloat())
				thumbImage?.draw(canvas)

				if (drawPlay) {
					val x = (thumbImage!!.centerX - Theme.dialogs_playDrawable.intrinsicWidth / 2).toInt()
					val y = (thumbImage!!.centerY - Theme.dialogs_playDrawable.intrinsicHeight / 2).toInt()
					Theme.dialogs_playDrawable.setBounds(x, y, x + Theme.dialogs_playDrawable.intrinsicWidth, y + Theme.dialogs_playDrawable.intrinsicHeight)
					Theme.dialogs_playDrawable.draw(canvas)
				}
			}

			override fun onAttachedToWindow() {
				super.onAttachedToWindow()
				thumbImage?.onAttachedToWindow()
			}

			override fun onDetachedFromWindow() {
				super.onDetachedFromWindow()
				thumbImage?.onDetachedFromWindow()
			}
		}

		thumbImage = ImageReceiver()
		thumbImage?.setParentView(avatarContainer)
		thumbImage?.setRoundRadius(AndroidUtilities.dp(2f))

		actionBar?.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, (if (!inPreviewMode) 56 else 0).toFloat(), 0f, 40f, 0f))

		val chatLocal = messagesController.getChat(chatId)

		if (chatLocal != null) {
			avatarContainer?.setChatAvatar(chatLocal)
			avatarContainer?.setTitle(chatLocal.title)
		}

		var hasThumb = false

		if (!messageObject.needDrawBluredPreview() && (messageObject.isPhoto || messageObject.isNewGif || messageObject.isVideo)) {
			val type = if (messageObject.isWebpage) messageObject.messageOwner?.media?.webpage?.type else null

			if (!("app" == type || "profile" == type || "article" == type || type != null && type.startsWith("telegram_"))) {
				val smallThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40)
				var bigThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize())

				if (smallThumb === bigThumb) {
					bigThumb = null
				}

				if (smallThumb != null) {
					hasThumb = true
					drawPlay = messageObject.isVideo

					val fileName = FileLoader.getAttachFileName(bigThumb)

					if (messageObject.mediaExists || downloadController.canDownloadMedia(messageObject) || fileLoader.isLoadingFile(fileName)) {
						val size = if (messageObject.type == MessageObject.TYPE_PHOTO) {
							bigThumb?.size ?: 0
						}
						else {
							0
						}

						thumbImage?.setImage(ImageLocation.getForObject(bigThumb, messageObject.photoThumbsObject), "20_20", ImageLocation.getForObject(smallThumb, messageObject.photoThumbsObject), "20_20", size.toLong(), null, messageObject, 0)
					}
					else {
						thumbImage?.setImage(null, null, ImageLocation.getForObject(smallThumb, messageObject.photoThumbsObject), "20_20", null as Drawable?, messageObject, 0)
					}
				}
			}
		}

		var message: CharSequence

		if (!messageObject.caption.isNullOrEmpty()) {
			message = messageObject.caption ?: ""
		}
		else if (!messageObject.messageOwner?.message.isNullOrEmpty()) {
			message = messageObject.messageText ?: ""

			if (message.length > 150) {
				message = message.subSequence(0, 150)
			}

			message = Emoji.replaceEmoji(message, avatarContainer?.subtitleTextView?.textPaint?.fontMetricsInt, false) ?: message
		}
		else {
			message = messageObject.messageText ?: ""
		}

		if (hasThumb) {
			val builder = SpannableStringBuilder(message)
			builder.insert(0, " ")
			builder.setSpan(FixedWidthSpan(AndroidUtilities.dp((18 + 6).toFloat())), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			avatarContainer?.setSubtitle(builder)
		}
		else {
			avatarContainer?.setSubtitle(messageObject.messageText ?: "")
		}

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}

					CHANNEL_STATS -> {
						val args = Bundle()

						if (messageObject.messageOwner?.fwd_from == null) {
							args.putLong("chat_id", messageObject.chatId)
						}
						else {
							args.putLong("chat_id", -messageObject.fromChatId)
						}

						presentFragment(StatisticActivity(args))
					}
				}
			}
		})

		avatarContainer?.setTitleColors(context.getColor(R.color.text), context.getColor(R.color.dark_gray))
		avatarContainer?.subtitleTextView?.setLinkTextColor(context.getColor(R.color.brand))

		avatarContainer?.setOnClickListener {
			val parentLayout = getParentLayout()

			if (parentLayout != null) {
				if (parentLayout.fragmentsStack.size > 1) {
					val previousFragment = parentLayout.fragmentsStack[parentLayout.fragmentsStack.size - 2]

					if (previousFragment is ChatActivity && previousFragment.currentChat?.id == chatId) {
						finishFragment()
						return@setOnClickListener
					}
				}
			}

			val args = Bundle()
			args.putLong("chat_id", chatId)
			args.putInt("message_id", messageId)
			args.putBoolean("need_remove_previous_same_chat_activity", false)

			val a = ChatActivity(args)

			presentFragment(a)
		}

		updateMenu()

		return fragmentView
	}

	private fun updateMenu() {
		if (chat?.can_view_stats == true) {
			val menu = actionBar?.createMenu()
			menu?.clearItems()
			val headerItem = menu?.addItem(0, R.drawable.overflow_menu)
			headerItem?.addSubItem(CHANNEL_STATS, R.drawable.msg_stats, context?.getString(R.string.ViewChannelStats))
		}
	}

	private fun loadChats(count: Int) {
		if (loading) {
			return
		}

		loading = true

		listViewAdapter?.notifyDataSetChanged()

		val req = TL_stats_getMessagePublicForwards()
		req.limit = count

		if (messageObject.messageOwner?.fwd_from != null) {
			req.msg_id = messageObject.messageOwner?.fwd_from?.saved_from_msg_id ?: 0
			req.channel = messagesController.getInputChannel(-messageObject.fromChatId)
		}
		else {
			req.msg_id = messageObject.id
			req.channel = messagesController.getInputChannel(-messageObject.dialogId)
		}

		if (messages.isNotEmpty()) {
			val message = messages[messages.size - 1]
			req.offset_id = message.id
			req.offset_peer = messagesController.getInputPeer(MessageObject.getDialogId(message))
			req.offset_rate = nextRate
		}
		else {
			req.offset_peer = TL_inputPeerEmpty()
		}

		val reqId = connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				if (error == null && response is messages_Messages) {
					if (response.flags and 1 != 0) {
						nextRate = response.next_rate
					}

					if (response.count != 0) {
						publicChats = response.count
					}
					else if (publicChats == 0) {
						publicChats = response.messages.size
					}

					endReached = response !is TL_messages_messagesSlice

					messagesController.putChats(response.chats, false)
					messagesController.putUsers(response.users, false)

					messages.addAll(response.messages)


					emptyView?.showTextView()
				}

				firstLoaded = true
				loading = false

				updateRows()
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)

		connectionsManager.bindRequestToGuid(reqId, classGuid)
	}

	private fun loadStat() {
		val req = TL_stats_getMessageStats()

		if (messageObject.messageOwner?.fwd_from != null) {
			req.msg_id = messageObject.messageOwner?.fwd_from?.saved_from_msg_id ?: 0
			req.channel = messagesController.getInputChannel(-messageObject.fromChatId)
		}
		else {
			req.msg_id = messageObject.id
			req.channel = messagesController.getInputChannel(-messageObject.dialogId)
		}

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				statsLoaded = true

				if (error != null) {
					updateRows()
					return@runOnUIThread
				}

				if (response !is TL_stats_messageStats) {
					updateRows()
					return@runOnUIThread
				}

				val interactionsViewData = StatisticActivity.createViewData(response.views_graph, R.string.InteractionsChartTitle, 1, false).also {
					this.interactionsViewData = it
				}

				if (interactionsViewData != null && interactionsViewData.chartData!!.x.size <= 5) {
					statsLoaded = false

					val request = TL_stats_loadAsyncGraph()
					request.token = interactionsViewData.zoomToken
					request.x = interactionsViewData.chartData!!.x[interactionsViewData.chartData!!.x.size - 1]
					request.flags = request.flags or 1

					val cacheKey = interactionsViewData.zoomToken + "_" + request.x

					val reqId = connectionsManager.sendRequest(request, { response1, error1 ->
						var childData: ChartData? = null
						if (response1 is TL_statsGraph) {
							val json = response1.json?.data

							if (!json.isNullOrEmpty()) {
								try {
									childData = StatisticActivity.createChartData(JSONObject(json), 1, false)
								}
								catch (e: JSONException) {
									FileLog.e(e)
								}
							}
						}
						else if (response1 is TL_statsGraphError) {
							AndroidUtilities.runOnUIThread {
								parentActivity?.let {
									Toast.makeText(it, response1.error, Toast.LENGTH_LONG).show()
								}
							}
						}

						val finalChildData = childData

						AndroidUtilities.runOnUIThread final@{
							statsLoaded = true

							if (error1 != null || finalChildData == null) {
								updateRows()
								return@final
							}

							childDataCache.put(cacheKey, finalChildData)

							interactionsViewData.childChartData = finalChildData
							interactionsViewData.activeZoom = request.x

							updateRows()
						}
					}, ConnectionsManager.RequestFlagFailOnServerErrors)

					connectionsManager.bindRequestToGuid(reqId, classGuid)
				}
				else {
					updateRows()
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	override fun onResume() {
		super.onResume()
		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
		listViewAdapter?.notifyDataSetChanged()
	}

//	private fun recolorRecyclerItem(child: View) {
//		when (child) {
//			is ManageChatUserCell -> {
//				child.update(0)
//			}
//
//			is BaseChartCell -> {
//				child.recolor()
//				child.setBackgroundColor(child.context.getColor(R.color.background))
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
//				child.setBackgroundColor(child.context.getColor(R.color.background))
//			}
//		}
//
//		(child as? EmptyCell)?.setBackgroundColor(child.context.getColor(R.color.background))
//	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val type = holder.itemViewType

			if (type == 0) {
				val cell = holder.itemView as ManageChatUserCell
				return cell.currentObject is TLObject
			}

			return false
		}

		override fun getItemCount(): Int {
			return rowCount
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = ManageChatUserCell(mContext, 6, 2, false)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}

				1 -> {
					view = ShadowSectionCell(mContext)
				}

				2 -> {
					val headerCell = HeaderCell(mContext, 16, 11, 11, false)
					headerCell.setBackgroundColor(mContext.getColor(R.color.background))
					headerCell.height = 43
					view = headerCell
				}

				4 -> {
					view = object : BaseChartCell(mContext, 1, SharedUiComponents().also { sharedUi = it }) {
						override fun onZoomed() {
							if (data!!.activeZoom > 0) {
								return
							}

							performClick()

							if (!chartView.legendSignatureView!!.canGoZoom) {
								return
							}

							val x = chartView.selectedDate

							if (chartType == 4) {
								data!!.childChartData = StackLinearChartData(data!!.chartData!!, x)
								zoomChart(false)
								return
							}

							if (data!!.zoomToken == null) {
								return
							}

							zoomCanceled()

							val cacheKey = data!!.zoomToken + "_" + x
							val dataFromCache = childDataCache[cacheKey]

							if (dataFromCache != null) {
								data!!.childChartData = dataFromCache
								zoomChart(false)
								return
							}

							val request = TL_stats_loadAsyncGraph()
							request.token = data!!.zoomToken

							if (x != 0L) {
								request.x = x
								request.flags = request.flags or 1
							}

							val finalCancelable = ZoomCancelable()

							lastCancelable = finalCancelable

							finalCancelable.adapterPosition = listView!!.getChildAdapterPosition(this)

							chartView.legendSignatureView!!.showProgress(show = true, force = false)

							val reqId = connectionsManager.sendRequest(request, { response, _ ->
								AndroidUtilities.runOnUIThread {
									var childData: ChartData? = null

									if (response is TL_statsGraph) {
										val json = response.json?.data

										if (!json.isNullOrEmpty()) {
											try {
												childData = StatisticActivity.createChartData(JSONObject(json), data!!.graphType, false)
											}
											catch (e: JSONException) {
												FileLog.e(e)
											}
										}
									}
									else if (response is TL_statsGraphError) {
										Toast.makeText(context, response.error, Toast.LENGTH_LONG).show()
									}

									val finalChildData = childData

									if (finalChildData != null) {
										childDataCache.put(cacheKey, finalChildData)
									}

									if (finalChildData != null && !finalCancelable.canceled && finalCancelable.adapterPosition >= 0) {
										@Suppress("NAME_SHADOWING") val view = layoutManager?.findViewByPosition(finalCancelable.adapterPosition)

										if (view is BaseChartCell) {
											data?.childChartData = finalChildData
											view.chartView.legendSignatureView?.showProgress(show = false, force = false)
											view.zoomChart(false)
										}
									}
									zoomCanceled()
								}
							}, ConnectionsManager.RequestFlagFailOnServerErrors)

							connectionsManager.bindRequestToGuid(reqId, classGuid)
						}

						override fun zoomCanceled() {
							lastCancelable?.canceled = true

							listView?.children?.forEach {
								if (it is BaseChartCell) {
									it.chartView.legendSignatureView?.showProgress(show = false, force = true)
								}
							}
						}

						override fun loadData(viewData: ChartViewData) {
							//  viewData.load(currentAccount, classGuid, );
						}
					}

					view.setBackgroundResource(R.color.background)
				}

				5 -> {
					view = OverviewCell(mContext)
					view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
					view.setBackgroundResource(R.color.background)
				}

				6 -> {
					view = EmptyCell(mContext, 16)
					view.setLayoutParams(RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 16))
					view.setBackgroundResource(R.color.background)
				}

				3 -> {
					view = LoadingCell(mContext, AndroidUtilities.dp(40f), AndroidUtilities.dp(120f))
				}

				else -> {
					view = LoadingCell(mContext, AndroidUtilities.dp(40f), AndroidUtilities.dp(120f))
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				0 -> {
					val userCell = holder.itemView as ManageChatUserCell
					val item = getItem(position)
					val did = MessageObject.getDialogId(item)
					val `object`: TLObject?
					var status: String? = null

					if (DialogObject.isUserDialog(did)) {
						`object` = messagesController.getUser(did)
					}
					else {
						`object` = messagesController.getChat(-did)

						if (`object` != null && `object`.participants_count != 0) {
							status = if (isChannel(`object`) && !`object`.megagroup) {
								LocaleController.formatPluralString("Subscribers", `object`.participants_count)
							}
							else {
								LocaleController.formatPluralString("Members", `object`.participants_count)
							}

							status = String.format("%1\$s, %2\$s", status, LocaleController.formatPluralString("Views", item!!.views))
						}
					}

					if (`object` != null) {
						userCell.setData(`object`, null, status, position != endRow - 1)
					}
				}

				1 -> {
					holder.itemView.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.shadow))
				}

				2 -> {
					val headerCell = holder.itemView as HeaderCell

					if (position == overviewHeaderRow) {
						headerCell.setText(LocaleController.formatString("StatisticOverview", R.string.StatisticOverview))
					}
					else {
						headerCell.setText(LocaleController.formatPluralString("PublicSharesCount", publicChats))
					}
				}

				4 -> {
					val chartCell = holder.itemView as BaseChartCell
					chartCell.updateData(interactionsViewData, false)
					chartCell.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
				}

				5 -> {
					val overviewCell = holder.itemView as OverviewCell
					overviewCell.setData()
				}
			}
		}

		override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
			if (holder.itemView is ManageChatUserCell) {
				holder.itemView.recycle()
			}
		}

		override fun getItemViewType(position: Int): Int {
			if (shadowDivideCells.contains(position)) {
				return 1
			}
			else if (position == headerRow || position == overviewHeaderRow) {
				return 2
			}
			else if (position == loadingRow) {
				return 3
			}
			else if (position == interactionsChartRow) {
				return 4
			}
			else if (position == overviewRow) {
				return 5
			}
			else if (position == emptyRow) {
				return 6
			}
			else {
				return 0
			}
		}

		fun getItem(position: Int): Message? {
			return if (position in startRow until endRow) {
				messages[position - startRow]
			}
			else {
				null
			}
		}
	}

	inner class OverviewCell(context: Context) : LinearLayout(context) {
		val primary = Array(3) { TextView(context) }
		val title = Array(3) { TextView(context) }
		val cell = Array(3) { LinearLayout(context) }

		init {
			orientation = VERTICAL

			setPadding(AndroidUtilities.dp(16f), 0, AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))

			val linearLayout = LinearLayout(context)
			linearLayout.orientation = HORIZONTAL

			for (j in 0..2) {
				val contentCell = cell[j]
				contentCell.orientation = VERTICAL

				primary[j].typeface = Theme.TYPEFACE_BOLD
				primary[j].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
				title[j].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)

				contentCell.addView(primary[j])
				contentCell.addView(title[j])

				linearLayout.addView(contentCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f))
			}

			addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
		}

		fun setData() {
			primary[0].text = AndroidUtilities.formatWholeNumber(messageObject.messageOwner?.views ?: 0, 0)
			title[0].text = context.getString(R.string.StatisticViews)

			if (publicChats > 0) {
				cell[1].visible()
				primary[1].text = AndroidUtilities.formatWholeNumber(publicChats, 0)
				title[1].text = LocaleController.formatString("PublicShares", R.string.PublicShares)
			}
			else {
				cell[1].gone()
			}

			val privateChats = (messageObject.messageOwner?.forwards ?: 0) - publicChats

			if (privateChats > 0) {
				cell[2].visible()
				primary[2].text = AndroidUtilities.formatWholeNumber(privateChats, 0)
				title[2].text = LocaleController.formatString("PrivateShares", R.string.PrivateShares)
			}
			else {
				cell[2].gone()
			}

			updateColors()
		}

		fun updateColors() {
			for (i in 0..2) {
				primary[i].setTextColor(context.getColor(R.color.text))
				title[i].setTextColor(context.getColor(R.color.dark_gray))
			}
		}
	}

	companion object {
		private const val CHANNEL_STATS = 1
	}
}
