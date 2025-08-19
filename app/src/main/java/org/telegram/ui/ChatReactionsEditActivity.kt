/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.ChatObject
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.TLAvailableReaction
import org.telegram.tgnet.TLRPC.TLChatReactionsAll
import org.telegram.tgnet.TLRPC.TLChatReactionsNone
import org.telegram.tgnet.TLRPC.TLChatReactionsSome
import org.telegram.tgnet.TLRPC.TLReactionEmoji
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.AvailableReactionCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.RadioCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import java.util.concurrent.CountDownLatch

class ChatReactionsEditActivity(args: Bundle) : BaseFragment(args), NotificationCenterDelegate {
	private var currentChat: Chat? = null
	private var info: ChatFull? = null
	private val chatId = args.getLong(KEY_CHAT_ID, 0)
	private val chatReactions = mutableListOf<String>()
	private var contentView: LinearLayout? = null
	private var listView: RecyclerListView? = null
	private var listAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = null
	private var enableReactionsCell: TextCheckCell? = null
	private val availableReactions = mutableListOf<TLAvailableReaction>()
	private var allReactions: RadioCell? = null
	private var someReactions: RadioCell? = null
	private var disableReactions: RadioCell? = null
	private val radioCells = mutableListOf<RadioCell>()
	private var startFromType = SELECT_TYPE_NONE
	private var isChannel = false
	private var controlsLayout: LinearLayout? = null
	private var selectedType = -1

	override fun onFragmentCreate(): Boolean {
		currentChat = messagesController.getChat(chatId)

		if (currentChat == null) {
			currentChat = MessagesStorage.getInstance(currentAccount).getChatSync(chatId)

			if (currentChat != null) {
				messagesController.putChat(currentChat, true)
			}
			else {
				return false
			}

			if (info == null) {
				info = MessagesStorage.getInstance(currentAccount).loadChatInfo(chatId, ChatObject.isChannel(currentChat), CountDownLatch(1), false, false)

				if (info == null) {
					return false
				}
			}
		}

		notificationCenter.addObserver(this, NotificationCenter.reactionsDidLoad)

		return super.onFragmentCreate()
	}

	override fun createView(context: Context): View? {
		isChannel = ChatObject.isChannelAndNotMegaGroup(chatId, currentAccount)

		actionBar?.setTitle(context.getString(R.string.Reactions))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		val ll = LinearLayout(context)
		ll.orientation = LinearLayout.VERTICAL

		availableReactions.addAll(mediaDataController.enabledReactionsList)

		if (isChannel) {
			enableReactionsCell = TextCheckCell(context)
			enableReactionsCell?.height = 56
			enableReactionsCell?.setTextAndCheck(context.getString(R.string.EnableReactions), chatReactions.isNotEmpty(), false)
			enableReactionsCell?.setBackgroundColor(if (enableReactionsCell?.isChecked == true) context.getColor(R.color.brand_transparent) else context.getColor(R.color.medium_gray))
			enableReactionsCell?.setTypeface(Theme.TYPEFACE_BOLD)

			enableReactionsCell?.setOnClickListener {
				setCheckedEnableReactionCell(if (enableReactionsCell?.isChecked == true) SELECT_TYPE_NONE else SELECT_TYPE_SOME, true)
			}

			ll.addView(enableReactionsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		val headerCell = HeaderCell(context)
		headerCell.setText(context.getString(R.string.AvailableReactions))

		controlsLayout = LinearLayout(context)
		controlsLayout?.orientation = LinearLayout.VERTICAL

		allReactions = RadioCell(context)
		allReactions?.setText(context.getString(R.string.AllReactions), checked = false, divider = true)

		someReactions = RadioCell(context)
		someReactions?.setText(context.getString(R.string.SomeReactions), checked = false, divider = true)

		disableReactions = RadioCell(context)
		disableReactions?.setText(context.getString(R.string.NoReactions), checked = false, divider = false)

		controlsLayout?.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		controlsLayout?.addView(allReactions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		controlsLayout?.addView(someReactions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		controlsLayout?.addView(disableReactions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		radioCells.add(allReactions!!)
		radioCells.add(someReactions!!)
		radioCells.add(disableReactions!!)

		allReactions?.setOnClickListener {
			setCheckedEnableReactionCell(SELECT_TYPE_ALL, true)
		}

		someReactions?.setOnClickListener {
			setCheckedEnableReactionCell(SELECT_TYPE_SOME, true)
		}

		disableReactions?.setOnClickListener {
			setCheckedEnableReactionCell(SELECT_TYPE_NONE, true)
		}

		headerCell.setBackgroundColor(context.getColor(R.color.background))

		allReactions?.background = Theme.createSelectorWithBackgroundDrawable(context.getColor(R.color.background), context.getColor(R.color.light_background))
		someReactions?.background = Theme.createSelectorWithBackgroundDrawable(context.getColor(R.color.background), context.getColor(R.color.light_background))
		disableReactions?.background = Theme.createSelectorWithBackgroundDrawable(context.getColor(R.color.background), context.getColor(R.color.light_background))

		setCheckedEnableReactionCell(startFromType, false)

		listView = RecyclerListView(context)
		listView?.setLayoutManager(LinearLayoutManager(context))

		listView?.setAdapter(object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
				return when (viewType) {
					TYPE_REACTION -> {
						RecyclerListView.Holder(AvailableReactionCell(context, checkbox = false, canLock = false))
					}

					TYPE_INFO -> {
						val infoCell = TextInfoPrivacyCell(context)
						RecyclerListView.Holder(infoCell)
					}

					TYPE_HEADER -> {
						RecyclerListView.Holder(HeaderCell(context, 23))
					}

					TYPE_CONTROLS_CONTAINER -> {
						val frameLayout = FrameLayout(context)

						(controlsLayout?.parent as? ViewGroup)?.removeView(controlsLayout)

						frameLayout.addView(controlsLayout)
						frameLayout.setLayoutParams(RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
						RecyclerListView.Holder(frameLayout)
					}

					else -> {
						RecyclerListView.Holder(AvailableReactionCell(context, checkbox = false, canLock = false))
					}
				}
			}

			override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
				when (getItemViewType(position)) {
					TYPE_INFO -> {
						val infoCell = holder.itemView as TextInfoPrivacyCell
						infoCell.setTextColor(context.getColor(R.color.dark_gray))

						if (isChannel) {
							infoCell.setText(if (ChatObject.isChannelAndNotMegaGroup(currentChat)) context.getString(R.string.EnableReactionsChannelInfo) else context.getString(R.string.EnableReactionsGroupInfo))
						}
						else {
							when (selectedType) {
								SELECT_TYPE_SOME -> {
									infoCell.setText(context.getString(R.string.EnableSomeReactionsInfo))
								}

								SELECT_TYPE_ALL -> {
									infoCell.setText(context.getString(R.string.EnableAllReactionsInfo))
								}

								SELECT_TYPE_NONE -> {
									infoCell.setText(context.getString(R.string.DisableReactionsInfo))
								}
							}
						}
					}

					TYPE_HEADER -> {
						val headerCell1 = holder.itemView as HeaderCell
						headerCell1.setText(context.getString(R.string.OnlyAllowThisReactions))
						headerCell1.setBackgroundColor(context.getColor(R.color.background))
					}

					TYPE_REACTION -> {
						val reactionCell = holder.itemView as AvailableReactionCell
						val react = availableReactions[position - (if (isChannel) 2 else 3)]
						reactionCell.bind(react, chatReactions.contains(react.reaction), currentAccount)
					}
				}
			}

			override fun getItemCount(): Int {
				return if (isChannel) {
					1 + if (chatReactions.isNotEmpty()) 1 + availableReactions.size else 0
				}
				else {
					1 + 1 + if (chatReactions.isNotEmpty()) 1 + availableReactions.size else 0
				}
			}

			override fun getItemViewType(position: Int): Int {
				if (isChannel) {
					return if (position == 0) TYPE_INFO else if (position == 1) TYPE_HEADER else TYPE_REACTION
				}
				if (position == 0) {
					return TYPE_CONTROLS_CONTAINER
				}

				return if (position == 1) TYPE_INFO else if (position == 2) TYPE_HEADER else TYPE_REACTION
			}
		}.also { listAdapter = it })

		listView?.setOnItemClickListener { view, position ->
			if (position <= (if (isChannel) 1 else 2)) {
				return@setOnItemClickListener
			}

			val cell = view as AvailableReactionCell
			val react = availableReactions[position - (if (isChannel) 2 else 3)]
			val nc = !chatReactions.contains(react.reaction)

			if (nc) {
				react.reaction?.let {
					chatReactions.add(it)
				}
			}
			else {
				chatReactions.remove(react.reaction)

				if (chatReactions.isEmpty()) {
					setCheckedEnableReactionCell(SELECT_TYPE_NONE, true)
				}
			}

			cell.setChecked(nc, true)
		}

		ll.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))

		contentView = ll
		fragmentView = contentView

		updateColors()

		return contentView
	}

	@SuppressLint("NotifyDataSetChanged")
	private fun setCheckedEnableReactionCell(selectType: Int, animated: Boolean) {
		val context = context ?: return

		if (selectedType == selectType) {
			return
		}

		if (enableReactionsCell != null) {
			val checked = (selectType == SELECT_TYPE_SOME)

			enableReactionsCell?.isChecked = checked

			val clr = if (checked) context.getColor(R.color.brand_transparent) else context.getColor(R.color.dark_gray)

			if (checked) {
				enableReactionsCell?.setBackgroundColorAnimated(true, clr)
			}
			else {
				enableReactionsCell?.setBackgroundColorAnimatedReverse(clr)
			}
		}

		selectedType = selectType

		for (i in radioCells.indices) {
			radioCells[i].setChecked(selectType == i, animated)
		}

		if (selectType == SELECT_TYPE_SOME) {
			if (animated) {
				chatReactions.clear()

				for (a in availableReactions) {
					if (a.reaction == "\uD83D\uDC4D" || a.reaction == "\uD83D\uDC4E") {
						chatReactions.add(a.reaction!!)
					}
				}

				if (chatReactions.isEmpty() && availableReactions.size >= 2) {
					chatReactions.add(availableReactions[0].reaction!!)
					chatReactions.add(availableReactions[1].reaction!!)
				}
			}

			if (animated) {
				listAdapter?.notifyItemRangeInserted(if (isChannel) 1 else 2, 1 + availableReactions.size)
			}
		}
		else {
			if (chatReactions.isNotEmpty()) {
				chatReactions.clear()

				if (animated) {
					listAdapter?.notifyItemRangeRemoved(if (isChannel) 1 else 2, 1 + availableReactions.size)
				}
			}
		}

		if (!isChannel && animated) {
			listAdapter?.notifyItemChanged(1)
		}

		if (!animated) {
			listAdapter?.notifyDataSetChanged()
		}
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		messagesController.setChatReactions(chatId, selectedType, chatReactions)
		notificationCenter.removeObserver(this, NotificationCenter.reactionsDidLoad)
	}

	fun setInfo(info: ChatFull?) {
		this.info = info
		if (info != null) {
			if (currentChat == null) {
				currentChat = messagesController.getChat(chatId)
			}

			chatReactions.clear()

			when (val reactionsSome = info.availableReactions) {
				is TLChatReactionsAll -> {
					startFromType = SELECT_TYPE_ALL
				}

				is TLChatReactionsNone -> {
					startFromType = SELECT_TYPE_NONE
				}

				is TLChatReactionsSome -> {
					for (reaction in reactionsSome.reactions) {
						if (reaction is TLReactionEmoji) {
							reaction.emoticon?.let {
								chatReactions.add(it)
							}
						}
					}

					startFromType = if (chatReactions.isEmpty()) {
						SELECT_TYPE_NONE
					}
					else {
						SELECT_TYPE_SOME
					}
				}

				else -> {
					startFromType = SELECT_TYPE_NONE
				}
			}
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	private fun updateColors() {
		val context = context ?: return
		contentView?.setBackgroundColor(context.getColor(R.color.light_background))
		listAdapter?.notifyDataSetChanged()
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (account != currentAccount) {
			return
		}

		if (id == NotificationCenter.reactionsDidLoad) {
			availableReactions.clear()
			availableReactions.addAll(mediaDataController.enabledReactionsList)

			listAdapter?.notifyDataSetChanged()
		}
	}

	companion object {
		private const val TYPE_INFO = 0
		private const val TYPE_HEADER = 1
		private const val TYPE_REACTION = 2
		private const val TYPE_CONTROLS_CONTAINER = 3
		const val KEY_CHAT_ID = "chat_id"
		const val SELECT_TYPE_NONE = 2
		const val SELECT_TYPE_SOME = 1
		const val SELECT_TYPE_ALL = 0
	}
}
