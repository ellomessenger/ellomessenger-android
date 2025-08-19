/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components.Premium

import android.content.Context
import android.graphics.Canvas
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject.isChannel
import org.telegram.messenger.ChatObject.isMegagroup
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig.Companion.activatedAccountsCount
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.AdministeredChannelCell
import org.telegram.ui.Cells.GroupCreateUserCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Components.BottomSheetWithRecyclerListView
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.RecyclerItemsEnterAnimator
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import kotlin.math.max

class LimitReachedBottomSheet(parentFragment: BaseFragment?, private val type: Int, currentAccount: Int) : BottomSheetWithRecyclerListView(parentFragment, false, hasFixedSize(type)) {
	var chats = ArrayList<Chat>()
	var rowCount = 0
	var headerRow = -1
	var dividerRow = -1
	var chatsTitleRow = -1
	var chatStartRow = -1
	var loadingRow = -1
	var limitPreviewView: LimitPreviewView? = null
	var selectedChats = HashSet<Chat>()
	var premiumButtonView: PremiumButtonView? = null
	var divider: View? = null
	private var loadingAdministeredChannels = false
	private var enterAnimator: RecyclerItemsEnterAnimator? = null
	private var currentValue = -1
	private val inactiveChats = ArrayList<Chat>()
	private val inactiveChatsSignatures = ArrayList<String>()
	private var loading = false
	private var isVeryLargeFile = false

	private val limitParams by lazy {
		val limitParams = LimitParams()

		when (type) {
			TYPE_PIN_DIALOGS -> {
				limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersPinnedLimitDefault
				limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersPinnedLimitPremium
				limitParams.icon = R.drawable.msg_limit_pin
				limitParams.descriptionStr = LocaleController.formatString("LimitReachedPinDialogs", R.string.LimitReachedPinDialogs, limitParams.defaultLimit, limitParams.premiumLimit)
				limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedPinDialogsPremium", R.string.LimitReachedPinDialogsPremium, limitParams.premiumLimit)
				limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedPinDialogsLocked", R.string.LimitReachedPinDialogsLocked, limitParams.defaultLimit)
			}

			TYPE_PUBLIC_LINKS -> {
				limitParams.defaultLimit = MessagesController.getInstance(currentAccount).publicLinksLimitDefault
				limitParams.premiumLimit = MessagesController.getInstance(currentAccount).publicLinksLimitPremium
				limitParams.icon = R.drawable.msg_limit_links
				limitParams.descriptionStr = LocaleController.formatString("LimitReachedPublicLinks", R.string.LimitReachedPublicLinks, limitParams.defaultLimit, limitParams.premiumLimit)
				limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedPublicLinksPremium", R.string.LimitReachedPublicLinksPremium, limitParams.premiumLimit)
				limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedPublicLinksLocked", R.string.LimitReachedPublicLinksLocked, limitParams.defaultLimit)
			}

			TYPE_FOLDERS -> {
				limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersLimitDefault
				limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersLimitPremium
				limitParams.icon = R.drawable.msg_limit_folder
				limitParams.descriptionStr = LocaleController.formatString("LimitReachedFolders", R.string.LimitReachedFolders, limitParams.defaultLimit, limitParams.premiumLimit)
				limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedFoldersPremium", R.string.LimitReachedFoldersPremium, limitParams.premiumLimit)
				limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedFoldersLocked", R.string.LimitReachedFoldersLocked, limitParams.defaultLimit)
			}

			TYPE_CHATS_IN_FOLDER -> {
				limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitDefault
				limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitPremium
				limitParams.icon = R.drawable.msg_limit_chats
				limitParams.descriptionStr = LocaleController.formatString("LimitReachedChatInFolders", R.string.LimitReachedChatInFolders, limitParams.defaultLimit, limitParams.premiumLimit)
				limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedChatInFoldersPremium", R.string.LimitReachedChatInFoldersPremium, limitParams.premiumLimit)
				limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedChatInFoldersLocked", R.string.LimitReachedChatInFoldersLocked, limitParams.defaultLimit)
			}

			TYPE_TO_MANY_COMMUNITIES -> {
				limitParams.defaultLimit = MessagesController.getInstance(currentAccount).channelsLimitDefault
				limitParams.premiumLimit = MessagesController.getInstance(currentAccount).channelsLimitPremium
				limitParams.icon = R.drawable.msg_limit_groups
				limitParams.descriptionStr = LocaleController.formatString("LimitReachedCommunities", R.string.LimitReachedCommunities, limitParams.defaultLimit, limitParams.premiumLimit)
				limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedCommunitiesPremium", R.string.LimitReachedCommunitiesPremium, limitParams.premiumLimit)
				limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedCommunitiesLocked", R.string.LimitReachedCommunitiesLocked, limitParams.defaultLimit)
			}

			TYPE_LARGE_FILE -> {
				limitParams.defaultLimit = 100
				limitParams.premiumLimit = 200
				limitParams.icon = R.drawable.msg_limit_folder
				limitParams.descriptionStr = LocaleController.formatString("LimitReachedFileSize", R.string.LimitReachedFileSize, "2 GB", "4 GB")
				limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedFileSizePremium", R.string.LimitReachedFileSizePremium, "4 GB")
				limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedFileSizeLocked", R.string.LimitReachedFileSizeLocked, "2 GB")
			}

			TYPE_ACCOUNTS -> {
				limitParams.defaultLimit = 3
				limitParams.premiumLimit = 4
				limitParams.icon = R.drawable.msg_limit_accounts
				limitParams.descriptionStr = LocaleController.formatString("LimitReachedAccounts", R.string.LimitReachedAccounts, limitParams.defaultLimit, limitParams.premiumLimit)
				limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedAccountsPremium", R.string.LimitReachedAccountsPremium, limitParams.premiumLimit)
				limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedAccountsPremium", R.string.LimitReachedAccountsPremium, limitParams.defaultLimit)
			}
		}

		limitParams
	}

	@JvmField
	var onSuccessRunnable: Runnable? = null

	@JvmField
	var parentIsChannel = false

	override fun onViewCreated(containerView: FrameLayout) {
		super.onViewCreated(containerView)

		val context = containerView.context
		premiumButtonView = PremiumButtonView(context, true)
		updatePremiumButtonText()

		if (!hasFixedSize) {
			divider = object : View(context) {
				override fun onDraw(canvas: Canvas) {
					super.onDraw(canvas)
					canvas.drawRect(0f, 0f, measuredWidth.toFloat(), 1f, Theme.dividerPaint)
				}
			}

			divider?.setBackgroundResource(R.color.background)

			containerView.addView(divider, createFrame(LayoutHelper.MATCH_PARENT, 72f, Gravity.BOTTOM, 0f, 0f, 0f, 0f))
		}

		containerView.addView(premiumButtonView, createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.BOTTOM, 16f, 0f, 16f, 12f))

		recyclerListView.setPadding(0, 0, 0, AndroidUtilities.dp(72f))

		recyclerListView.setOnItemClickListener { view, _ ->
			if (view is AdministeredChannelCell) {
				val chat = view.currentChannel

				if (chat != null) {
					if (selectedChats.contains(chat)) {
						selectedChats.remove(chat)
					}
					else {
						selectedChats.add(chat)
					}
				}

				view.setChecked(selectedChats.contains(chat), true)

				updateButton()
			}
			else if (view is GroupCreateUserCell) {
				val chat = view.`object` as? Chat

				if (chat != null) {
					if (selectedChats.contains(chat)) {
						selectedChats.remove(chat)
					}
					else {
						selectedChats.add(chat)
					}

					view.setChecked(selectedChats.contains(chat), true)
				}

				updateButton()
			}
		}

		recyclerListView.setOnItemLongClickListener { view, position ->
			recyclerListView.onItemClickListener?.onItemClick(view, position)
			view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
			false
		}

		premiumButtonView?.buttonLayout?.setOnClickListener {
			Toast.makeText(it.context, "TODO: purchase Ello+", Toast.LENGTH_SHORT).show()

			// TODO: uncomment following to enable premium purchases
//			if (getInstance(currentAccount).isPremium || MessagesController.getInstance(currentAccount).premiumLocked || isVeryLargeFile) {
//				dismiss()
//				return@setOnClickListener
//			}
//
//			if (parentFragment == null) {
//				return@setOnClickListener
//			}
//
//			parentFragment.visibleDialog?.dismiss()
//			parentFragment.presentFragment(PremiumPreviewFragment(limitTypeToServerString(type)))
//			onShowPremiumScreenRunnable?.run()
//			dismiss()
		}

		premiumButtonView?.overlayTextView?.setOnClickListener {
			if (selectedChats.isEmpty()) {
				return@setOnClickListener
			}

			if (type == TYPE_PUBLIC_LINKS) {
				revokeSelectedLinks()
			}
			else if (type == TYPE_TO_MANY_COMMUNITIES) {
				leaveFromSelectedGroups()
			}
		}

		enterAnimator = RecyclerItemsEnterAnimator(recyclerListView, true)
	}

	private fun updatePremiumButtonText() {
		if (getInstance(currentAccount).isPremium || MessagesController.getInstance(currentAccount).premiumLocked || isVeryLargeFile) {
			premiumButtonView?.buttonTextView?.text = context.getString(R.string.OK)
			premiumButtonView?.hideIcon()
		}
		else {
			premiumButtonView?.buttonTextView?.text = context.getString(R.string.IncreaseLimit)
			premiumButtonView?.setIcon(if (type == TYPE_ACCOUNTS) R.raw.addone_icon else R.raw.double_icon)
		}
	}

	private fun leaveFromSelectedGroups() {
		val currentUser = MessagesController.getInstance(currentAccount).getUser(getInstance(currentAccount).getClientUserId())
		val chats = ArrayList(selectedChats)

		val builder = AlertDialog.Builder(context)
		builder.setTitle(LocaleController.formatPluralString("LeaveCommunities", chats.size))

		if (chats.size == 1) {
			val channel = chats[0]
			builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChannelLeaveAlertWithName", R.string.ChannelLeaveAlertWithName, channel?.title)))
		}
		else {
			builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChatsLeaveAlert", R.string.ChatsLeaveAlert)))
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		builder.setPositiveButton(context.getString(R.string.RevokeButton)) { _, _ ->
			dismiss()

			for (i in chats.indices) {
				val chat = chats[i]
				MessagesController.getInstance(currentAccount).putChat(chat, false)
				MessagesController.getInstance(currentAccount).deleteParticipantFromChat(chat!!.id, currentUser)
			}
		}

		val alertDialog = builder.create()
		alertDialog.show()

		val button = alertDialog.getButton(BUTTON_POSITIVE) as? TextView
		button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
	}

	private fun updateButton() {
		if (selectedChats.size > 0) {

			val str = when (type) {
				TYPE_PUBLIC_LINKS -> {
					LocaleController.formatPluralString("RevokeLinks", selectedChats.size)
				}

				TYPE_TO_MANY_COMMUNITIES -> {
					LocaleController.formatPluralString("LeaveCommunities", selectedChats.size)
				}

				else -> {
					null
				}
			}

			premiumButtonView?.setOverlayText(str, true, true)
		}
		else {
			premiumButtonView?.clearOverlayText()
		}
	}

	public override fun getTitle(): CharSequence {
		return context.getString(R.string.LimitReached)
	}

	public override fun createAdapter(): SelectionAdapter {
		return object : SelectionAdapter() {
			override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
				return holder.itemViewType == 1 || holder.itemViewType == 4
			}

			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
				val view: View
				val context = parent.context

				when (viewType) {
					0 -> {
						view = HeaderView(context)
					}

					1 -> {
						view = AdministeredChannelCell(context, true, 9) { v ->
							val cell = v.parent as AdministeredChannelCell
							val channels = ArrayList<Chat?>()
							channels.add(cell.currentChannel)
							revokeLinks(channels)
						}
					}

					2 -> {
						view = ShadowSectionCell(context, 12, ResourcesCompat.getColor(context.resources, R.color.light_background, null))
					}

					3 -> {
						view = HeaderCell(context)
						view.setPadding(0, 0, 0, AndroidUtilities.dp(8f))
					}

					4 -> {
						view = GroupCreateUserCell(context, 1, 8, false)
					}

					5 -> {
						val flickerLoadingView = FlickerLoadingView(context)
						flickerLoadingView.setViewType(if (type == TYPE_PUBLIC_LINKS) FlickerLoadingView.LIMIT_REACHED_LINKS else FlickerLoadingView.LIMIT_REACHED_GROUPS)
						flickerLoadingView.setIsSingleCell(true)
						flickerLoadingView.setIgnoreHeightCheck(true)
						flickerLoadingView.setItemsCount(10)
						view = flickerLoadingView
					}

					else -> view = HeaderView(context)
				}

				view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

				return RecyclerListView.Holder(view)
			}

			override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
				if (holder.itemViewType == 4) {
					val chat = inactiveChats[position - chatStartRow]
					val cell = holder.itemView as GroupCreateUserCell
					val signature = inactiveChatsSignatures[position - chatStartRow]
					cell.setObject(chat, chat.title, signature, true)
					cell.setChecked(selectedChats.contains(chat), false)
				}
				else if (holder.itemViewType == 1) {
					val chat = chats[position - chatStartRow]
					val administeredChannelCell = holder.itemView as AdministeredChannelCell
					val oldChat = administeredChannelCell.currentChannel
					administeredChannelCell.setChannel(chat, false)
					administeredChannelCell.setChecked(selectedChats.contains(chat), oldChat === chat)
				}
				else if (holder.itemViewType == 3) {
					val headerCell = holder.itemView as HeaderCell

					if (type == TYPE_PUBLIC_LINKS) {
						headerCell.setText(context.getString(R.string.YourPublicCommunities))
					}
					else {
						headerCell.setText(context.getString(R.string.LastActiveCommunities))
					}
				}
			}

			override fun getItemViewType(position: Int): Int {
				if (headerRow == position) {
					return 0
				}
				else if (dividerRow == position) {
					return 2
				}
				else if (chatsTitleRow == position) {
					return 3
				}
				else if (loadingRow == position) {
					return 5
				}

				return if (type == TYPE_TO_MANY_COMMUNITIES) {
					4
				}
				else {
					1
				}
			}

			override fun getItemCount(): Int {
				return rowCount
			}
		}
	}

	fun setCurrentValue(currentValue: Int) {
		this.currentValue = currentValue
	}

	fun setVeryLargeFile(b: Boolean) {
		isVeryLargeFile = b
		updatePremiumButtonText()
	}

	private inner class HeaderView(context: Context) : LinearLayout(context) {
		init {
			orientation = VERTICAL

			setPadding(AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(6f), 0)

			val icon = limitParams.icon
			val descriptionStr: String?
			val premiumLocked = MessagesController.getInstance(currentAccount).premiumLocked

			descriptionStr = if (premiumLocked) {
				limitParams.descriptionStrLocked
			}
			else {
				if (getInstance(currentAccount).isPremium || isVeryLargeFile) limitParams.descriptionStrPremium else limitParams.descriptionStr
			}

			val defaultLimit = limitParams.defaultLimit
			val premiumLimit = limitParams.premiumLimit
			var currentValue = currentValue
			var position = 0.5f

			if (type == TYPE_FOLDERS) {
				currentValue = MessagesController.getInstance(currentAccount).dialogFilters.size - 1
			}
			else if (type == TYPE_ACCOUNTS) {
				currentValue = activatedAccountsCount
			}

			if (type == TYPE_PIN_DIALOGS) {
				var pinnedCount = 0
				val dialogs = MessagesController.getInstance(currentAccount).getDialogs(0)

				for (dialog in dialogs) {
					if (dialog is TLRPC.TLDialogFolder) {
						continue
					}

					if (dialog.pinned) {
						pinnedCount++
					}
				}

				currentValue = pinnedCount
			}

			if (getInstance(currentAccount).isPremium || isVeryLargeFile) {
				currentValue = premiumLimit
				position = 1f
			}
			else {
				if (currentValue < 0) {
					currentValue = defaultLimit
				}

				if (type == TYPE_ACCOUNTS) {
					if (currentValue > defaultLimit) {
						position = (currentValue - defaultLimit).toFloat() / (premiumLimit - defaultLimit).toFloat()
					}
				}
				else {
					position = currentValue / premiumLimit.toFloat()
				}
			}

			limitPreviewView = LimitPreviewView(context, icon, currentValue, premiumLimit)
			limitPreviewView?.setBagePosition(position)
			limitPreviewView?.setType(type)
			limitPreviewView?.defaultCount?.isGone = true

			if (premiumLocked) {
				limitPreviewView?.setPremiumLocked()
			}
			else {
				if (getInstance(currentAccount).isPremium || isVeryLargeFile) {
					limitPreviewView?.premiumCount?.isGone = true

					if (type == TYPE_LARGE_FILE) {
						limitPreviewView?.defaultCount?.text = context.getString(R.string.two_gb)
					}
					else {
						limitPreviewView?.defaultCount?.text = defaultLimit.toString()
					}

					limitPreviewView?.defaultCount?.isVisible = true
				}
			}

			if (type == TYPE_PUBLIC_LINKS || type == TYPE_TO_MANY_COMMUNITIES) {
				limitPreviewView?.setDelayedAnimation()
			}

			addView(limitPreviewView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 0, 0, 0, 0))

			val title = TextView(context)
			title.typeface = Theme.TYPEFACE_BOLD

			if (type == TYPE_LARGE_FILE) {
				title.text = context.getString(R.string.FileTooLarge)
			}
			else {
				title.text = context.getString(R.string.LimitReached)
			}

			title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
			title.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))

			addView(title, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 22, 0, 10))

			val description = TextView(context)
			description.text = AndroidUtilities.replaceTags(descriptionStr)
			description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			description.gravity = Gravity.CENTER_HORIZONTAL
			description.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))

			addView(description, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 24))
		}
	}

	init {
		this.currentAccount = currentAccount
		fixNavigationBar()
		updateRows()

		if (type == TYPE_PUBLIC_LINKS) {
			loadAdministeredChannels()
		}
		else if (type == TYPE_TO_MANY_COMMUNITIES) {
			loadInactiveChannels()
		}
	}

	private fun loadAdministeredChannels() {
		loadingAdministeredChannels = true
		loading = true

		updateRows()

		val req = TLRPC.TLChannelsGetAdminedPublicChannels()

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				loadingAdministeredChannels = false

				if (response != null) {
					val res = response as TLRPC.TLMessagesChats

					chats.clear()
					chats.addAll(res.chats)
					loading = false
					enterAnimator?.showItemsAnimated(chatsTitleRow + 4)

					var savedTop = 0

					for (i in 0 until recyclerListView.childCount) {
						if (recyclerListView.getChildAt(i) is HeaderView) {
							savedTop = recyclerListView.getChildAt(i).top
							break
						}
					}

					updateRows()

					if (headerRow >= 0 && savedTop != 0) {
						(recyclerListView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(headerRow + 1, savedTop)
					}
				}

				val currentValue = max(chats.size, limitParams.defaultLimit)

				limitPreviewView?.setIconValue(currentValue)
				limitPreviewView?.setBagePosition(currentValue / limitParams.premiumLimit.toFloat())
				limitPreviewView?.startDelayedAnimation()
			}
		}
	}

	private fun updateRows() {
		rowCount = 0
		dividerRow = -1
		chatStartRow = -1
		loadingRow = -1
		headerRow = rowCount++

		if (!hasFixedSize(type)) {
			dividerRow = rowCount++
			chatsTitleRow = rowCount++

			if (loading) {
				loadingRow = rowCount++
			}
			else {
				chatStartRow = rowCount

				rowCount += if (type == TYPE_TO_MANY_COMMUNITIES) {
					inactiveChats.size
				}
				else {
					chats.size
				}
			}
		}

		notifyDataSetChanged()
	}

	private fun revokeSelectedLinks() {
		val channels = ArrayList(selectedChats)
		revokeLinks(channels)
	}

	private fun revokeLinks(channels: ArrayList<Chat?>) {
		val builder = AlertDialog.Builder(context)
		builder.setTitle(LocaleController.formatPluralString("RevokeLinks", channels.size))

		if (channels.size == 1) {
			val channel = channels[0]

			if (parentIsChannel) {
				builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlertChannel", R.string.RevokeLinkAlertChannel, MessagesController.getInstance(currentAccount).linkPrefix + "/" + channel!!.username, channel.title)))
			}
			else {
				builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlert", R.string.RevokeLinkAlert, MessagesController.getInstance(currentAccount).linkPrefix + "/" + channel!!.username, channel.title)))
			}
		}
		else {
			if (parentIsChannel) {
				builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinksAlertChannel", R.string.RevokeLinksAlertChannel)))
			}
			else {
				builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinksAlert", R.string.RevokeLinksAlert)))
			}
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		builder.setPositiveButton(context.getString(R.string.RevokeButton)) { _, _ ->
			dismiss()

			for (i in channels.indices) {
				val req1 = TLRPC.TLChannelsUpdateUsername()
				val channel = channels[i]

				req1.channel = MessagesController.getInputChannel(channel)
				req1.username = ""

				ConnectionsManager.getInstance(currentAccount).sendRequest(req1, { response1, _ ->
					if (response1 is TLRPC.TLBoolTrue) {
						AndroidUtilities.runOnUIThread(onSuccessRunnable)
					}
				}, ConnectionsManager.RequestFlagInvokeAfter)
			}
		}

		val alertDialog = builder.create()
		alertDialog.show()

		val button = alertDialog.getButton(BUTTON_POSITIVE) as? TextView
		button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
	}

	private fun loadInactiveChannels() {
		loading = true

		updateRows()

		val inactiveChannelsRequest = TLRPC.TLChannelsGetInactiveChannels()

		ConnectionsManager.getInstance(currentAccount).sendRequest(inactiveChannelsRequest) { response, _ ->
			val chats = response as? TLRPC.TLMessagesInactiveChats
			val signatures = mutableListOf<String>()

			chats?.chats?.forEachIndexed { index, chat ->
				val currentDate = ConnectionsManager.getInstance(currentAccount).currentTime
				val date = chats.dates[index]
				val daysDif = (currentDate - date) / 86400

				val dateFormat = if (daysDif < 30) {
					LocaleController.formatPluralString("Days", daysDif)
				}
				else if (daysDif < 365) {
					LocaleController.formatPluralString("Months", daysDif / 30)
				}
				else {
					LocaleController.formatPluralString("Years", daysDif / 365)
				}

				if (isMegagroup(chat)) {
					val members = LocaleController.formatPluralString("Members", chat.participantsCount)
					signatures.add(LocaleController.formatString("InactiveChatSignature", R.string.InactiveChatSignature, members, dateFormat))
				}
				else if (isChannel(chat)) {
					signatures.add(LocaleController.formatString("InactiveChannelSignature", R.string.InactiveChannelSignature, dateFormat))
				}
				else {
					val members = LocaleController.formatPluralString("Members", chat.participantsCount)
					signatures.add(LocaleController.formatString("InactiveChatSignature", R.string.InactiveChatSignature, members, dateFormat))
				}
			}

			AndroidUtilities.runOnUIThread({
				inactiveChatsSignatures.clear()
				inactiveChats.clear()
				inactiveChatsSignatures.addAll(signatures)
				inactiveChats.addAll(chats?.chats ?: emptyList())

				loading = false

				enterAnimator?.showItemsAnimated(chatsTitleRow + 4)

				val savedTop = recyclerListView.children.find { it is HeaderView }?.top ?: 0

				updateRows()

				if (headerRow >= 0 && savedTop != 0) {
					(recyclerListView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(headerRow + 1, savedTop)
				}

				val currentValue = max(inactiveChats.size, limitParams.defaultLimit)

				limitPreviewView?.setIconValue(currentValue)
				limitPreviewView?.setBagePosition(currentValue / limitParams.premiumLimit.toFloat())
				limitPreviewView?.startDelayedAnimation()
			}, 1_000)
		}
	}

	class LimitParams {
		var icon = 0
		var descriptionStr: String? = null
		var descriptionStrPremium: String? = null
		var descriptionStrLocked: String? = null
		var defaultLimit = 0
		var premiumLimit = 0
	}

	companion object {
		const val TYPE_PIN_DIALOGS = 0
		const val TYPE_PUBLIC_LINKS = 2
		const val TYPE_FOLDERS = 3
		const val TYPE_CHATS_IN_FOLDER = 4
		const val TYPE_TO_MANY_COMMUNITIES = 5
		const val TYPE_LARGE_FILE = 6
		const val TYPE_ACCOUNTS = 7
		const val TYPE_CAPTION = 8
		const val TYPE_GIFS = 9
		const val TYPE_STICKERS = 10

		@JvmStatic
		fun limitTypeToServerString(type: Int): String? {
			when (type) {
				TYPE_PIN_DIALOGS -> return "double_limits__dialog_pinned"
				TYPE_TO_MANY_COMMUNITIES -> return "double_limits__channels"
				TYPE_PUBLIC_LINKS -> return "double_limits__channels_public"
				TYPE_FOLDERS -> return "double_limits__dialog_filters"
				TYPE_CHATS_IN_FOLDER -> return "double_limits__dialog_filters_chats"
				TYPE_LARGE_FILE -> return "double_limits__upload_max_fileparts"
				TYPE_CAPTION -> return "double_limits__caption_length"
				TYPE_GIFS -> return "double_limits__saved_gifs"
				TYPE_STICKERS -> return "double_limits__stickers_faved"
			}
			return null
		}

		private fun hasFixedSize(type: Int): Boolean {
			return type == TYPE_PIN_DIALOGS || type == TYPE_FOLDERS || type == TYPE_CHATS_IN_FOLDER || type == TYPE_LARGE_FILE || type == TYPE_ACCOUNTS
		}
	}
}
