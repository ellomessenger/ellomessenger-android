/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.collection.LongSparseArray
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.databinding.TabsHeaderBinding
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.Message
import org.telegram.tgnet.TLRPC.TLInputMessagesFilterPhoneCalls
import org.telegram.tgnet.TLRPC.TLInputPeerEmpty
import org.telegram.tgnet.TLRPC.TLMessageActionHistoryClear
import org.telegram.tgnet.TLRPC.TLMessageActionPhoneCall
import org.telegram.tgnet.TLRPC.TLMessagesAffectedFoundMessages
import org.telegram.tgnet.TLRPC.TLMessagesDeletePhoneCallHistory
import org.telegram.tgnet.TLRPC.TLMessagesSearch
import org.telegram.tgnet.TLRPC.TLPhoneCallDiscardReasonBusy
import org.telegram.tgnet.TLRPC.TLPhoneCallDiscardReasonMissed
import org.telegram.tgnet.TLRPC.TLUpdateDeleteMessages
import org.telegram.tgnet.TLRPC.TLUpdates
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.action
import org.telegram.tgnet.userId
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.CheckBoxCell
import org.telegram.ui.Cells.DialogsEmptyCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.ProfileSearchCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.NumberTextView
import org.telegram.ui.Components.ProgressButton
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.Components.voip.VoIPHelper
import java.util.stream.Collectors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
class CallLogActivity @JvmOverloads constructor(args: Bundle? = null) : BaseFragment(args), NotificationCenterDelegate {
	private val floatingInterpolator = AccelerateDecelerateInterpolator()
	private val actionModeViews = mutableListOf<View>()
	private val calls = mutableListOf<CallLogRow>()
	private val selectedIds = mutableListOf<Int>()
	private var listViewAdapter: ListAdapter? = null
	private var emptyView: EmptyTextProgressView? = null
	private var layoutManager: LinearLayoutManager? = null
	private var listView: RecyclerListView? = null
	private var floatingButton: ImageView? = null
	private var flickerLoadingView: FlickerLoadingView? = null
	private var selectedDialogsCountTextView: NumberTextView? = null
	private var otherItem: ActionBarMenuItem? = null
	private var loading = false
	private var firstLoaded = false
	private var endReached = false
	private var activeGroupCalls: List<Long>? = null
	private var prevPosition = 0
	private var prevTop = 0
	private var scrollUpdated = false
	private var floatingHidden = false
	private var greenDrawable: Drawable? = null
	private var greenDrawable2: Drawable? = null
	private var redDrawable: Drawable? = null
	private var iconOut: ImageSpan? = null
	private var iconIn: ImageSpan? = null
	private var iconMissed: ImageSpan? = null
	private var lastCallUser: User? = null
	private var lastCallChat: Chat? = null
	private var waitingForCallChatId: Long? = null
	private var openTransitionStarted = false
	private var selectedTab = ALL_CALLS_TAB
	private var tabsView: View? = null

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.didReceiveNewMessages -> {
				if (!firstLoaded) {
					return
				}

				val scheduled = args[2] as Boolean

				if (scheduled) {
					return
				}

				val arr = args[1] as List<MessageObject>

				for (msg in arr) {
					if (msg.messageOwner?.action is TLMessageActionPhoneCall) {
						val fromId = msg.fromChatId
						val userID = if (fromId == userConfig.getClientUserId()) msg.messageOwner?.peerId?.userId else fromId
						var callType = if (fromId == userConfig.getClientUserId()) TYPE_OUT else TYPE_IN
						val reason = (msg.messageOwner?.action as? TLMessageActionPhoneCall)?.reason

						if (callType == TYPE_IN && (reason is TLPhoneCallDiscardReasonMissed || reason is TLPhoneCallDiscardReasonBusy)) {
							callType = TYPE_MISSED
						}
						if (calls.size > 0) {
							val topRow = calls[0]

							if (topRow.user?.id == userID && topRow.type == callType) {
								topRow.calls?.add(0, msg.messageOwner!!)
								listViewAdapter?.notifyItemChanged(0)
								continue
							}
						}

						val row = CallLogRow()
						row.calls = ArrayList()
						row.calls?.add(msg.messageOwner!!)
						row.user = messagesController.getUser(userID)
						row.type = callType
						row.video = msg.isVideoCall

						calls.add(0, row)

						listViewAdapter?.notifyItemInserted(0)
					}
				}

				otherItem?.visibility = if (calls.isEmpty()) View.GONE else View.VISIBLE
			}

			NotificationCenter.messagesDeleted -> {
				if (!firstLoaded) {
					return
				}

				val scheduled = args[2] as Boolean

				if (scheduled) {
					return
				}

				var didChange = false
				val ids = args[0] as List<Int>
				val itrtr = calls.iterator()

				while (itrtr.hasNext()) {
					val row = itrtr.next()
					val msgs = row.calls!!.iterator()

					while (msgs.hasNext()) {
						val msg = msgs.next()

						if (ids.contains(msg.id)) {
							didChange = true
							msgs.remove()
						}
					}

					if (row.calls?.size == 0) {
						itrtr.remove()
					}
				}

				if (didChange) {
					listViewAdapter?.notifyDataSetChanged()
				}
			}

			NotificationCenter.activeGroupCallsUpdated -> {
				activeGroupCalls = messagesController.activeGroupCalls
				listViewAdapter?.notifyDataSetChanged()
			}

			NotificationCenter.chatInfoDidLoad -> {
				if (waitingForCallChatId == null) {
					return
				}

				val chatFull = args[0] as ChatFull

				if (chatFull.id == waitingForCallChatId) {
					val groupCall = messagesController.getGroupCall(waitingForCallChatId!!, true)

					if (groupCall != null) {
						lastCallChat?.let {
							VoIPHelper.startCall(it, null, false, parentActivity, this@CallLogActivity, accountInstance)
						}

						waitingForCallChatId = null
					}
				}
			}

			NotificationCenter.groupCallUpdated -> {
				if (waitingForCallChatId == null) {
					return
				}

				val chatId = args[0] as Long

				if (waitingForCallChatId == chatId) {
					lastCallChat?.let {
						VoIPHelper.startCall(it, null, false, parentActivity, this@CallLogActivity, accountInstance)
					}

					waitingForCallChatId = null
				}
			}
		}
	}

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()

		getCalls(0, 50)

		activeGroupCalls = messagesController.activeGroupCalls

		notificationCenter.let {
			it.addObserver(this, NotificationCenter.didReceiveNewMessages)
			it.addObserver(this, NotificationCenter.messagesDeleted)
			it.addObserver(this, NotificationCenter.activeGroupCallsUpdated)
			it.addObserver(this, NotificationCenter.chatInfoDidLoad)
			it.addObserver(this, NotificationCenter.groupCallUpdated)
		}

		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		notificationCenter.let {
			it.removeObserver(this, NotificationCenter.didReceiveNewMessages)
			it.removeObserver(this, NotificationCenter.messagesDeleted)
			it.removeObserver(this, NotificationCenter.activeGroupCallsUpdated)
			it.removeObserver(this, NotificationCenter.chatInfoDidLoad)
			it.removeObserver(this, NotificationCenter.groupCallUpdated)
		}
	}

	override fun createView(context: Context): View? {
		greenDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_call_made_green_18dp, null)?.mutate()
		greenDrawable?.setBounds(0, 0, greenDrawable!!.intrinsicWidth, greenDrawable!!.intrinsicHeight)
		greenDrawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.online), PorterDuff.Mode.SRC_IN)

		iconOut = ImageSpan(greenDrawable!!, ImageSpan.ALIGN_BOTTOM)

		greenDrawable2 = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_call_received_green_18dp, null)!!.mutate()
		greenDrawable2?.setBounds(0, 0, greenDrawable2!!.intrinsicWidth, greenDrawable2!!.intrinsicHeight)
		greenDrawable2?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.online, null), PorterDuff.Mode.SRC_IN)

		iconIn = ImageSpan(greenDrawable2!!, ImageSpan.ALIGN_BOTTOM)

		redDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_call_received_green_18dp, null)!!.mutate()
		redDrawable?.setBounds(0, 0, redDrawable!!.intrinsicWidth, redDrawable!!.intrinsicHeight)
		redDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.purple, null), PorterDuff.Mode.SRC_IN)

		iconMissed = ImageSpan(redDrawable!!, ImageSpan.ALIGN_BOTTOM)

		if (!shouldShowBottomNavigationPanel()) {
			actionBar?.backButtonDrawable = BackDrawable(false)
		}
		else {
			actionBar?.shouldDestroyBackButtonOnCollapse = true
		}

		actionBar?.setAllowOverlayTitle(true)

		tabsView = createTabsView(context)
		tabsView?.invisible()

		actionBar?.addView(tabsView, LayoutHelper.createFrame(214, 36f, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0f, 0f, 0f, 12f))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						if (actionBar?.isActionModeShowed == true) {
							hideActionMode(true)
						}
						else {
							finishFragment()
						}
					}

					DELETE_ALL_CALLS -> {
						showDeleteAlert(true)
					}

					DELETE -> {
						showDeleteAlert(false)
					}
				}
			}
		})

		val menu = actionBar!!.createMenu()

		otherItem = menu.addItem(10, R.drawable.overflow_menu)
		otherItem?.contentDescription = context.getString(R.string.AccDescrMoreOptions)
		otherItem?.addSubItem(DELETE_ALL_CALLS, R.drawable.msg_delete, context.getString(R.string.DeleteAllCalls))

		fragmentView = FrameLayout(context)

		val frameLayout = fragmentView as FrameLayout

		flickerLoadingView = FlickerLoadingView(context)
		flickerLoadingView?.setViewType(FlickerLoadingView.CALL_LOG_TYPE)
		flickerLoadingView?.setBackgroundResource(R.color.background)
		flickerLoadingView?.showDate(false)

		emptyView = EmptyTextProgressView(context, flickerLoadingView)

		frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView = RecyclerListView(context)
		listView?.setEmptyView(emptyView)

		listView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false).also {
			layoutManager = it
		}

		listView?.adapter = ListAdapter(context).also {
			listViewAdapter = it
		}

		listView?.verticalScrollbarPosition = if (LocaleController.isRTL) View.SCROLLBAR_POSITION_LEFT else View.SCROLLBAR_POSITION_RIGHT

		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView?.setOnItemClickListener { view, position ->
			if (view is CallCell) {
				val row = listViewAdapter?.calls?.get(position - listViewAdapter!!.callsStartRow) ?: return@setOnItemClickListener

				if (actionBar?.isActionModeShowed == true) {
					addOrRemoveSelectedDialog(row.calls, view)
				}
				else {
					val userId = row.user?.id ?: 0L

					if (userId != 0L) {
						val messageId = row.calls?.firstOrNull()?.id ?: 0

						val args = Bundle()
						args.putLong("user_id", userId)
						args.putInt("message_id", messageId)

						presentFragment(ChatActivity(args))
					}
				}
			}
			else if (view is GroupCallCell) {
				val chatId = view.currentChat?.id ?: 0L

				if (chatId != 0L) {
					val args = Bundle()
					args.putLong("chat_id", chatId)

					presentFragment(ChatActivity(args))
				}
			}
		}

		listView?.setOnItemLongClickListener { view, position ->
			if (view is CallCell) {
				val listViewAdapter = listViewAdapter ?: return@setOnItemLongClickListener false
				addOrRemoveSelectedDialog(listViewAdapter.calls[position - listViewAdapter.callsStartRow].calls, view)
				return@setOnItemLongClickListener true
			}

			false
		}

		listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				val layoutManager = layoutManager ?: return
				val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
				val visibleItemCount = if (firstVisibleItem == RecyclerView.NO_POSITION) 0 else abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1

				if (visibleItemCount > 0) {
					listViewAdapter?.let {
						val totalItemCount = it.itemCount

						if (!endReached && !loading && it.calls.isNotEmpty() && firstVisibleItem + visibleItemCount >= totalItemCount - 5) {
							val row = it.calls.last()

							AndroidUtilities.runOnUIThread {
								getCalls(row.calls?.last()?.id ?: 0, 100)
							}
						}
					}
				}

				if (floatingButton?.visibility != View.GONE) {
					val topChild = recyclerView.getChildAt(0)
					var firstViewTop = 0

					if (topChild != null) {
						firstViewTop = topChild.top
					}

					val goingDown: Boolean
					var changed = true

					if (prevPosition == firstVisibleItem) {
						val topDelta = prevTop - firstViewTop
						goingDown = firstViewTop < prevTop
						changed = abs(topDelta) > 1
					}
					else {
						goingDown = firstVisibleItem > prevPosition
					}

					if (changed && scrollUpdated) {
						hideFloatingButton(goingDown)
					}

					prevPosition = firstVisibleItem
					prevTop = firstViewTop
					scrollUpdated = true
				}
			}
		})

		if (loading) {
			emptyView?.showProgress()
		}
		else {
			emptyView?.showTextView()
		}

		floatingButton = ImageView(context)
		floatingButton?.visibility = View.VISIBLE
		floatingButton?.scaleType = ImageView.ScaleType.CENTER

		val drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56f), context.getColor(R.color.brand), context.getColor(R.color.brand_transparent))

		floatingButton?.background = drawable
		floatingButton?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_IN)
		floatingButton?.setImageResource(R.drawable.chat_calls_voice)
		floatingButton?.contentDescription = context.getString(R.string.Call)

		val animator = StateListAnimator()
		animator.addState(intArrayOf(android.R.attr.state_pressed), ObjectAnimator.ofFloat(floatingButton!!, "translationZ", AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(4f).toFloat()).setDuration(200))
		animator.addState(intArrayOf(), ObjectAnimator.ofFloat(floatingButton!!, "translationZ", AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(2f).toFloat()).setDuration(200))

		floatingButton?.stateListAnimator = animator

		floatingButton?.outlineProvider = object : ViewOutlineProvider() {
			@SuppressLint("NewApi")
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, AndroidUtilities.dp(56f), AndroidUtilities.dp(56f))
			}
		}

		frameLayout.addView(floatingButton, LayoutHelper.createFrame(56, 56f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.BOTTOM, (if (LocaleController.isRTL) 14 else 0).toFloat(), 0f, (if (LocaleController.isRTL) 0 else 14).toFloat(), 14f))

		floatingButton?.setOnClickListener {
			val args = Bundle()
			args.putBoolean("destroyAfterSelect", true)
			args.putBoolean("returnAsResult", true)
			args.putBoolean("onlyUsers", true)
			args.putBoolean("allowSelf", false)

			val contactsFragment = ContactsActivity(args)

			contactsFragment.setDelegate { user, _, _ ->
				val userFull = messagesController.getUserFull(user?.id)
				VoIPHelper.startCall(user.also { lastCallUser = it }, false, userFull != null && userFull.videoCallsAvailable, parentActivity, null, accountInstance)
			}

			presentFragment(contactsFragment)
		}

		return fragmentView
	}

	private fun createTabsView(context: Context): View {
		val actionBarTabsLayout = TabsHeaderBinding.inflate(LayoutInflater.from(context))

		actionBarTabsLayout.root.getChildAt(0).setOnClickListener {
			it.isEnabled = false
			actionBarTabsLayout.root.getChildAt(1).isEnabled = true
			selectedTab = ALL_CALLS_TAB
			listViewAdapter?.notifyDataSetChanged()
		}

		actionBarTabsLayout.root.getChildAt(1).setOnClickListener {
			it.isEnabled = false
			actionBarTabsLayout.root.getChildAt(0).isEnabled = true
			selectedTab = MISSED_CALLS_TAB
			listViewAdapter?.notifyDataSetChanged()
		}

		return actionBarTabsLayout.root
	}

	private fun showDeleteAlert(all: Boolean) {
		val parentActivity = parentActivity ?: return

		val builder = AlertDialog.Builder(parentActivity)

		if (all) {
			builder.setTitle(parentActivity.getString(R.string.DeleteAllCalls))
			builder.setMessage(parentActivity.getString(R.string.DeleteAllCallsText))
		}
		else {
			builder.setTitle(parentActivity.getString(R.string.DeleteCalls))
			builder.setMessage(parentActivity.getString(R.string.DeleteSelectedCallsText))
		}

		val checks = booleanArrayOf(false)
		val frameLayout = FrameLayout(parentActivity)

		val cell = CheckBoxCell(parentActivity, 1)
		cell.background = Theme.getSelectorDrawable(false)
		cell.setText(parentActivity.getString(R.string.DeleteCallsForEveryone), "", checked = false, divider = false)
		cell.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(8f) else 0, 0, if (LocaleController.isRTL) 0 else AndroidUtilities.dp(8f), 0)

		frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.TOP or Gravity.LEFT, 8f, 0f, 8f, 0f))

		cell.setOnClickListener {
			val cell1 = it as CheckBoxCell
			checks[0] = !checks[0]
			cell1.setChecked(checks[0], true)
		}

		builder.setView(frameLayout)

		builder.setPositiveButton(parentActivity.getString(R.string.Delete)) { _, _ ->
			if (all) {
				deleteAllMessages(checks[0])

				calls.clear()

				loading = false

				tabsView?.visibility = View.VISIBLE
				endReached = true
				otherItem?.gone()
				listViewAdapter?.notifyDataSetChanged()
			}
			else {
				messagesController.deleteMessages(ArrayList(selectedIds), null, null, 0, checks[0], false)
			}

			hideActionMode(false)
		}

		builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

		val alertDialog = builder.create()

		showDialog(alertDialog)

		val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView

		button?.setTextColor(parentActivity.getColor(R.color.purple))
	}

	private fun deleteAllMessages(revoke: Boolean) {
		val req = TLMessagesDeletePhoneCallHistory()
		req.revoke = revoke

		connectionsManager.sendRequest(req) { response, _ ->
			if (response != null) {
				val res = response as TLMessagesAffectedFoundMessages

				val updateDeleteMessages = TLUpdateDeleteMessages()
				updateDeleteMessages.messages.addAll(res.messages)
				updateDeleteMessages.pts = res.pts
				updateDeleteMessages.ptsCount = res.ptsCount

				val updates = TLUpdates()
				updates.updates.add(updateDeleteMessages)

				messagesController.processUpdates(updates, false)

				if (res.offset != 0) {
					deleteAllMessages(revoke)
				}
			}
		}
	}

	private fun hideActionMode(animated: Boolean) {
		actionBar?.hideActionMode()
		selectedIds.clear()

		listView?.children?.forEach {
			if (it is CallCell) {
				it.setChecked(false, animated)
			}
		}
	}

	private fun isSelected(messages: ArrayList<Message>?): Boolean {
		if (messages.isNullOrEmpty()) {
			return false
		}

		var a = 0
		val n = messages.size

		while (a < n) {
			if (selectedIds.contains(messages[a].id)) {
				return true
			}

			a++
		}

		return false
	}

	private fun createActionMode() {
		if (actionBar?.actionModeIsExist(null) == true) {
			return
		}

		val actionMode = actionBar!!.createActionMode()

		selectedDialogsCountTextView = NumberTextView(actionMode.context)
		selectedDialogsCountTextView?.setTextSize(18)
		selectedDialogsCountTextView?.setTypeface(Theme.TYPEFACE_BOLD)
		selectedDialogsCountTextView?.setTextColor(context!!.getColor(R.color.dark_gray))

		actionMode.addView(selectedDialogsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0))

		selectedDialogsCountTextView?.setOnTouchListener { _, _ -> true }

		actionModeViews.add(actionMode.addItemWithWidth(DELETE, R.drawable.msg_delete, AndroidUtilities.dp(54f), context?.getString(R.string.Delete)))
	}

	private fun addOrRemoveSelectedDialog(messages: ArrayList<Message>?, cell: CallCell): Boolean {
		if (messages.isNullOrEmpty()) {
			return false
		}

		return if (isSelected(messages)) {
			var a = 0
			val n = messages.size

			while (a < n) {
				selectedIds.remove(messages[a].id)
				a++
			}

			cell.setChecked(checked = false, animated = true)

			showOrUpdateActionMode()

			false
		}
		else {
			var a = 0
			val n = messages.size

			while (a < n) {
				val id = messages[a].id

				if (!selectedIds.contains(id)) {
					selectedIds.add(id)
				}

				a++
			}

			cell.setChecked(checked = true, animated = true)

			showOrUpdateActionMode()

			true
		}
	}

	private fun showOrUpdateActionMode() {
		var updateAnimated = false

		if (actionBar?.isActionModeShowed == true) {
			if (selectedIds.isEmpty()) {
				hideActionMode(true)
				return
			}

			updateAnimated = true
		}
		else {
			createActionMode()

			actionBar?.showActionMode()

			val animators = actionModeViews.map {
				it.pivotY = (ActionBar.getCurrentActionBarHeight() / 2).toFloat()
				AndroidUtilities.clearDrawableAnimation(it)
				ObjectAnimator.ofFloat(it, View.SCALE_Y, 0.1f, 1.0f)
			}

			val animatorSet = AnimatorSet()
			animatorSet.playTogether(animators)
			animatorSet.setDuration(200)
			animatorSet.start()
		}

		selectedDialogsCountTextView?.setNumber(selectedIds.size, updateAnimated)
	}

	private fun hideFloatingButton(hide: Boolean) {
		if (floatingHidden == hide) {
			return
		}

		floatingHidden = hide

		val animator = ObjectAnimator.ofFloat(floatingButton!!, "translationY", (if (floatingHidden) AndroidUtilities.dp(100f) else 0).toFloat()).setDuration(300)
		animator.interpolator = floatingInterpolator

		floatingButton?.isClickable = !hide

		animator.start()
	}

	private fun getCalls(maxId: Int, count: Int) {
		if (loading) {
			return
		}

		loading = true

		if (!firstLoaded) {
			emptyView?.showProgress()
		}

		//MARK: commented out for the reason when calling getCalls started blinking tabsView
//		tabsView?.gone()

		listViewAdapter?.notifyDataSetChanged()

		val req = TLMessagesSearch()
		req.limit = count
		req.peer = TLInputPeerEmpty()
		req.filter = TLInputMessagesFilterPhoneCalls()
		req.q = ""
		req.offsetId = maxId

		val reqId = connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				val oldCount = max(listViewAdapter!!.callsStartRow, 0) + calls.size

				if (error == null) {
					val users = LongSparseArray<User>()
					val msgs = response as TLRPC.MessagesMessages

					endReached = msgs.messages.isEmpty()

					for (a in msgs.users.indices) {
						val user = msgs.users[a]
						users.put(user.id, user)
					}

					var currentRow = if (calls.size > 0) calls[calls.size - 1] else null

					for (a in msgs.messages.indices) {
						val msg = msgs.messages[a]

						if (msg.action == null || msg.action is TLMessageActionHistoryClear) {
							continue
						}

						var callType = if (MessageObject.getFromChatId(msg) == userConfig.getClientUserId()) TYPE_OUT else TYPE_IN
						val reason = (msg.action as? TLMessageActionPhoneCall)?.reason

						if (callType == TYPE_IN && (reason is TLPhoneCallDiscardReasonMissed || reason is TLPhoneCallDiscardReasonBusy)) {
							callType = TYPE_MISSED
						}

						val fromId = MessageObject.getFromChatId(msg)
						val userID = if (fromId == userConfig.getClientUserId()) msg.peerId!!.userId else fromId

						if (currentRow == null || currentRow.user != null && currentRow.user!!.id != userID || currentRow.type != callType) {
							if (currentRow != null && !calls.contains(currentRow)) {
								calls.add(currentRow)
							}

							val row = CallLogRow()
							row.calls = ArrayList()
							row.user = users[userID]
							row.type = callType
							row.video = (msg.action as? TLMessageActionPhoneCall)?.video == true

							currentRow = row
						}

						currentRow.calls?.add(msg)
					}

					if (currentRow != null && !currentRow.calls.isNullOrEmpty() && !calls.contains(currentRow)) {
						calls.add(currentRow)
					}
				}
				else {
					endReached = true
				}

				loading = false

				tabsView?.visible()

				showItemsAnimated(oldCount)

				if (!firstLoaded) {
					resumeDelayedFragmentAnimation()
				}

				firstLoaded = true

				otherItem?.visibility = if (calls.isEmpty()) View.GONE else View.VISIBLE

				emptyView?.showTextView()

				listViewAdapter?.notifyDataSetChanged()
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)

		connectionsManager.bindRequestToGuid(reqId, classGuid)
	}

	override fun onResume() {
		super.onResume()
		listViewAdapter?.notifyDataSetChanged()
	}

	override fun onRequestPermissionsResultFragment(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		if (requestCode == VoIPHelper.REQUEST_CODE_RECORD_AUDIO || requestCode == VoIPHelper.REQUEST_CODE_CAMERA || requestCode == 103) {
			var allGranted = true

			for (a in grantResults.indices) {
				if (grantResults[a] != PackageManager.PERMISSION_GRANTED) {
					allGranted = false
					break
				}
			}

			if (grantResults.isNotEmpty() && allGranted) {
				if (requestCode == VoIPHelper.REQUEST_CODE_MERGED) {
					lastCallChat?.let {
						VoIPHelper.startCall(it, null, false, parentActivity, this@CallLogActivity, accountInstance)
					}
				}
				else {
					val userFull = messagesController.getUserFull(lastCallUser?.id)
					VoIPHelper.startCall(lastCallUser, requestCode == VoIPHelper.REQUEST_CODE_CAMERA, requestCode == VoIPHelper.REQUEST_CODE_CAMERA || userFull != null && userFull.videoCallsAvailable, parentActivity, null, accountInstance)
				}
			}
			else {
				VoIPHelper.permissionDenied(parentActivity, null, requestCode)
			}
		}
	}

	override fun onTransitionAnimationStart(isOpen: Boolean, backward: Boolean) {
		super.onTransitionAnimationStart(isOpen, backward)

		if (isOpen) {
			openTransitionStarted = true
		}
	}

	override fun needDelayOpenAnimation(): Boolean {
		return true
	}

	private fun showItemsAnimated(from: Int) {
		if (isPaused || !openTransitionStarted) {
			return
		}

		val progressView = listView?.children?.find { it is FlickerLoadingView }

		if (progressView != null) {
			listView?.removeView(progressView)
		}

		listView?.viewTreeObserver?.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
			override fun onPreDraw(): Boolean {
				val listView = listView ?: return false

				listView.viewTreeObserver?.removeOnPreDrawListener(this)

				val n = listView.childCount
				val animatorSet = AnimatorSet()

				for (i in 0 until n) {
					val child = listView.getChildAt(i)
					val holder = listView.getChildViewHolder(child)

					if (child === progressView || listView.getChildAdapterPosition(child) < from || child is GroupCallCell || child is HeaderCell && holder.adapterPosition == listViewAdapter?.activeHeaderRow) {
						continue
					}

					child.alpha = 0f

					val s = min(listView.measuredHeight, max(0, child.top))
					val delay = (s / listView.measuredHeight.toFloat() * 100).toInt()

					val a = ObjectAnimator.ofFloat(child, View.ALPHA, 0f, 1f)
					a.startDelay = delay.toLong()
					a.setDuration(200)

					animatorSet.playTogether(a)
				}

				if (progressView != null && progressView.parent == null) {
					listView.addView(progressView)

					val layoutManager = listView.layoutManager

					if (layoutManager != null) {
						layoutManager.ignoreView(progressView)

						val animator = ObjectAnimator.ofFloat(progressView, View.ALPHA, progressView.alpha, 0f)

						animator.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								progressView.alpha = 1f
								layoutManager.stopIgnoringView(progressView)
								listView.removeView(progressView)
							}
						})

						animator.start()
					}
				}

				animatorSet.start()

				return true
			}
		})
	}

	private class EmptyTextProgressView(context: Context, progressView: View? = null) : FrameLayout(context) {
		private val emptyTextView1: TextView
		private val emptyTextView2: TextView
		private val progressView: View?
		private val imageView: RLottieImageView

		init {
			if (progressView != null) {
				addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
			}

			this.progressView = progressView

			imageView = RLottieImageView(context)
			imageView.setAutoRepeat(true)
			imageView.setAnimation(R.raw.panda_call, DialogsEmptyCell.ANIMATED_ICON_SIDE, DialogsEmptyCell.ANIMATED_ICON_SIDE)

			addView(imageView, LayoutHelper.createFrame(DialogsEmptyCell.ANIMATED_ICON_SIDE, DialogsEmptyCell.ANIMATED_ICON_SIDE.toFloat(), Gravity.CENTER, 52f, 4f, 52f, 60f))

			imageView.setOnClickListener {
				if (!imageView.isPlaying()) {
					imageView.setProgress(0.0f)
					imageView.playAnimation()
				}
			}

			emptyTextView1 = TextView(context)
			emptyTextView1.setTextColor(context.getColor(R.color.text))
			emptyTextView1.text = context.getString(R.string.NoRecentCalls)
			emptyTextView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
			emptyTextView1.typeface = Theme.TYPEFACE_BOLD
			emptyTextView1.gravity = Gravity.CENTER

			addView(emptyTextView1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 17f, 40f, 17f, 0f))

			emptyTextView2 = TextView(context)

			var help = context.getString(R.string.NoRecentCallsInfo)

			if (AndroidUtilities.isTablet() && !AndroidUtilities.isSmallTablet()) {
				help = help.replace('\n', ' ')
			}

			emptyTextView2.text = help
			emptyTextView2.setTextColor(context.getColor(R.color.dark_gray))
			emptyTextView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			emptyTextView2.gravity = Gravity.CENTER
			emptyTextView2.setLineSpacing(AndroidUtilities.dp(2f).toFloat(), 1f)

			addView(emptyTextView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 17f, 90f, 17f, 0f))

			progressView?.alpha = 0f

			imageView.alpha = 0f
			emptyTextView1.alpha = 0f
			emptyTextView2.alpha = 0f

			setOnTouchListener { _, _ -> true }
		}

		fun showProgress() {
			imageView.animate().alpha(0f).setDuration(150).start()
			emptyTextView1.animate().alpha(0f).setDuration(150).start()
			emptyTextView2.animate().alpha(0f).setDuration(150).start()
			progressView?.animate()?.alpha(1f)?.setDuration(150)?.start()
		}

		fun showTextView() {
			imageView.animate().alpha(1f).setDuration(150).start()
			emptyTextView1.animate().alpha(1f).setDuration(150).start()
			emptyTextView2.animate().alpha(1f).setDuration(150).start()
			progressView?.animate()?.alpha(0f)?.setDuration(150)?.start()
			imageView.playAnimation()
		}

		override fun hasOverlappingRendering(): Boolean {
			return false
		}
	}

	private class CallLogRow {
		var user: User? = null
		var calls: ArrayList<Message>? = null
		var type = 0
		var video = false
	}

	private inner class CallCell(context: Context) : FrameLayout(context) {
		val imageView: ImageView
		val profileSearchCell: ProfileSearchCell
		private val checkBox: CheckBox2

		init {
			setBackgroundColor(context.getColor(R.color.background))

			profileSearchCell = ProfileSearchCell(context)
			profileSearchCell.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(32f) else 0, 0, if (LocaleController.isRTL) 0 else AndroidUtilities.dp(32f), 0)
			profileSearchCell.setSublabelOffset(AndroidUtilities.dp((if (LocaleController.isRTL) 2 else -2).toFloat()), -AndroidUtilities.dp(4f))

			addView(profileSearchCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			imageView = ImageView(context)
			imageView.imageAlpha = 214
			imageView.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.SRC_IN)
			imageView.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background), 1)
			imageView.scaleType = ImageView.ScaleType.CENTER

			imageView.setOnClickListener {
				val row = it.tag as CallLogRow
				val userFull = messagesController.getUserFull(row.user?.id)
				lastCallUser = row.user
				VoIPHelper.startCall(lastCallUser, row.video, row.video || userFull?.videoCallsAvailable == true, parentActivity, userFull, accountInstance)
			}

			imageView.contentDescription = context.getString(R.string.Call)

			addView(imageView, LayoutHelper.createFrame(48, 48f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, 8f, 0f, 8f, 0f))

			checkBox = CheckBox2(context, 21)
			checkBox.setColor(0, context.getColor(R.color.background), context.getColor(R.color.brand))
			checkBox.setDrawUnchecked(false)
			checkBox.setDrawBackgroundAsArc(3)

			addView(checkBox, LayoutHelper.createFrame(24, 24f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 42f, 32f, 42f, 0f))
		}

		fun setChecked(checked: Boolean, animated: Boolean) {
			checkBox.setChecked(checked, animated)
		}
	}

	private inner class GroupCallCell(context: Context) : FrameLayout(context) {
		val profileSearchCell: ProfileSearchCell
		val button: ProgressButton
		var currentChat: Chat? = null

		init {
			setBackgroundResource(R.color.background)

			val text = context.getString(R.string.VoipChatJoin)

			button = ProgressButton(context)

			val width = ceil(button.paint.measureText(text).toDouble()).toInt()

			profileSearchCell = ProfileSearchCell(context)
			profileSearchCell.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp((28 + 16).toFloat()) + width else 0, 0, if (LocaleController.isRTL) 0 else AndroidUtilities.dp((28 + 16).toFloat()) + width, 0)
			profileSearchCell.setSublabelOffset(0, -AndroidUtilities.dp(1f))

			addView(profileSearchCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			button.text = text
			button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			button.setTextColor(context.getColor(R.color.white))
			button.setProgressColor(context.getColor(R.color.brand))
			button.setBackgroundRoundRect(context.getColor(R.color.brand), context.getColor(R.color.darker_brand), 16f)
			button.setPadding(AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f), 0)

			addView(button, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT.toFloat(), 28f, Gravity.TOP or Gravity.END, 0f, 16f, 14f, 0f))

			button.setOnClickListener {
				val tag = it.tag as Long
				val call = messagesController.getGroupCall(tag, false)

				lastCallChat = messagesController.getChat(tag)

				if (call != null) {
					VoIPHelper.startCall(lastCallChat, null, false, parentActivity, this@CallLogActivity, accountInstance)
				}
				else {
					waitingForCallChatId = tag
					messagesController.loadFullChat(tag, 0, true)
				}
			}
		}

		fun setChat(chat: Chat?) {
			currentChat = chat
		}
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		var activeHeaderRow = 0
		private var callsHeaderRow = 0
		private var activeStartRow = 0
		private var activeEndRow = 0
		var callsStartRow = 0
		private var callsEndRow = 0
		private var loadingCallsRow = 0
		private var sectionRow = 0
		private var rowsCount = 0
		var calls: List<CallLogRow> = this@CallLogActivity.calls

		private fun updateRows() {
			activeHeaderRow = -1
			callsHeaderRow = -1
			activeStartRow = -1
			activeEndRow = -1
			callsStartRow = -1
			callsEndRow = -1
			loadingCallsRow = -1
			sectionRow = -1
			rowsCount = 0

			if (!activeGroupCalls.isNullOrEmpty()) {
				activeHeaderRow = rowsCount++
				activeStartRow = rowsCount
				rowsCount += (activeGroupCalls?.size ?: 0)
				activeEndRow = rowsCount
			}

			calls = if (selectedTab == MISSED_CALLS_TAB) {
				this@CallLogActivity.calls.stream().filter { callLogRow: CallLogRow -> callLogRow.type == TYPE_MISSED }.collect(Collectors.toList())
			}
			else {
				this@CallLogActivity.calls
			}

			if (calls.isNotEmpty()) {
				if (activeHeaderRow != -1) {
					sectionRow = rowsCount++
					callsHeaderRow = rowsCount++
				}

				callsStartRow = rowsCount
				rowsCount += calls.size
				callsEndRow = rowsCount

				if (!endReached) {
					loadingCallsRow = rowsCount++
				}
			}
		}

		override fun notifyDataSetChanged() {
			updateRows()
			super.notifyDataSetChanged()
		}

		override fun notifyItemChanged(position: Int) {
			updateRows()
			super.notifyItemChanged(position)
		}

		override fun notifyItemChanged(position: Int, payload: Any?) {
			updateRows()
			super.notifyItemChanged(position, payload)
		}

		override fun notifyItemRangeChanged(positionStart: Int, itemCount: Int) {
			updateRows()
			super.notifyItemRangeChanged(positionStart, itemCount)
		}

		override fun notifyItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
			updateRows()
			super.notifyItemRangeChanged(positionStart, itemCount, payload)
		}

		override fun notifyItemInserted(position: Int) {
			updateRows()
			super.notifyItemInserted(position)
		}

		override fun notifyItemMoved(fromPosition: Int, toPosition: Int) {
			updateRows()
			super.notifyItemMoved(fromPosition, toPosition)
		}

		override fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) {
			updateRows()
			super.notifyItemRangeInserted(positionStart, itemCount)
		}

		override fun notifyItemRemoved(position: Int) {
			updateRows()
			super.notifyItemRemoved(position)
		}

		override fun notifyItemRangeRemoved(positionStart: Int, itemCount: Int) {
			updateRows()
			super.notifyItemRangeRemoved(positionStart, itemCount)
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val type = holder.itemViewType
			return type == 0 || type == 4
		}

		override fun getItemCount(): Int {
			return rowsCount
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = CallCell(mContext)
				}

				1 -> {
					val flickerLoadingView = FlickerLoadingView(mContext)
					flickerLoadingView.setIsSingleCell(true)
					flickerLoadingView.setViewType(FlickerLoadingView.CALL_LOG_TYPE)
					flickerLoadingView.setBackgroundResource(R.color.background)
					flickerLoadingView.showDate(false)
					view = flickerLoadingView
				}

				2 -> {
					view = TextInfoPrivacyCell(mContext)
					view.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow))
				}

				3 -> {
					view = HeaderCell(mContext, 21, 15, 2, false)
					view.setBackgroundResource(R.color.background)
				}

				4 -> {
					view = GroupCallCell(mContext)
				}

				5 -> {
					view = ShadowSectionCell(mContext)
				}

				else -> {
					view = ShadowSectionCell(mContext)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
			if (holder.itemView is CallCell) {
				val row = calls[holder.adapterPosition - callsStartRow]
				holder.itemView.setChecked(isSelected(row.calls), false)
			}
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			@Suppress("NAME_SHADOWING") var position = position

			when (holder.itemViewType) {
				0 -> {
					position -= callsStartRow

					val row = calls[position]

					val cell = holder.itemView as CallCell
					cell.imageView.setImageResource(if (row.video) R.drawable.chat_calls_video else R.drawable.chat_calls_voice)

					val last = row.calls!![0]
					val subtitle: SpannableString
					val ldir = if (LocaleController.isRTL) "\u202b" else ""

					subtitle = if (row.calls!!.size == 1) {
						SpannableString(ldir + "  " + LocaleController.formatDateCallLog(last.date.toLong()))
					}
					else {
						SpannableString(String.format("$ldir  (%d) %s", row.calls!!.size, LocaleController.formatDateCallLog(last.date.toLong())))
					}

					when (row.type) {
						TYPE_OUT -> subtitle.setSpan(iconOut, ldir.length, ldir.length + 1, 0)
						TYPE_IN -> subtitle.setSpan(iconIn, ldir.length, ldir.length + 1, 0)
						TYPE_MISSED -> subtitle.setSpan(iconMissed, ldir.length, ldir.length + 1, 0)
					}

					cell.profileSearchCell.setData(row.user, null, null, subtitle, needCount = false, saved = false)
					cell.profileSearchCell.useSeparator = position != calls.size - 1 || !endReached
					cell.imageView.tag = row
				}

				3 -> {
					val cell = holder.itemView as HeaderCell

					if (position == activeHeaderRow) {
						cell.setText(cell.context.getString(R.string.VoipChatActiveChats))
					}
					else if (position == callsHeaderRow) {
						cell.setText(cell.context.getString(R.string.VoipChatRecentCalls))
					}
				}

				4 -> {
					position -= activeStartRow

					val chatId = activeGroupCalls!![position]
					val chat = messagesController.getChat(chatId)

					val cell = holder.itemView as GroupCallCell
					cell.setChat(chat)
					cell.button.tag = chat!!.id

					val text = if (ChatObject.isChannel(chat) && !chat.megagroup) {
						if (TextUtils.isEmpty(chat.username)) {
							cell.context.getString(R.string.ChannelPrivate).lowercase()
						}
						else {
							cell.context.getString(R.string.ChannelPublic).lowercase()
						}
					}
					else {
						if (chat.hasGeo) {
							cell.context.getString(R.string.MegaLocation)
						}
						else if (chat.username.isNullOrEmpty()) {
							cell.context.getString(R.string.MegaPrivate).lowercase()
						}
						else {
							cell.context.getString(R.string.MegaPublic).lowercase()
						}
					}

					cell.profileSearchCell.useSeparator = position != activeGroupCalls!!.size - 1 && !endReached
					cell.profileSearchCell.setData(chat, null, null, text, needCount = false, saved = false)
				}
			}
		}

		override fun getItemViewType(i: Int): Int {
			return when (i) {
				activeHeaderRow, callsHeaderRow -> 3
				in callsStartRow until callsEndRow -> 0
				in activeStartRow until activeEndRow -> 4
				loadingCallsRow -> 1
				sectionRow -> 5
				else -> 2
			}
		}
	}

	override fun shouldShowBottomNavigationPanel(): Boolean {
		return arguments?.getBoolean("topLevel", false) ?: false
	}

	fun actionbarShowingTitle(hasTitle: Boolean) {
		tabsView?.animate()?.alpha(if (hasTitle) 0f else 1f)?.setDuration(200)?.start()
	}

	companion object {
		private const val TYPE_OUT = 0
		private const val TYPE_IN = 1
		private const val TYPE_MISSED = 2
		private const val DELETE_ALL_CALLS = 1
		private const val DELETE = 2
		private const val ALL_CALLS_TAB = 0
		private const val MISSED_CALLS_TAB = 1
	}
}
