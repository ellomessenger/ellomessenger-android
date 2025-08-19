/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserObject
import org.telegram.messenger.utils.vibrate
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.InputCheckPasswordSRP
import org.telegram.tgnet.TLRPC.TLAccountGetPassword
import org.telegram.tgnet.TLRPC.TLAccountPassword
import org.telegram.tgnet.TLRPC.TLChannelsEditCreator
import org.telegram.tgnet.TLRPC.TLChatAdminRights
import org.telegram.tgnet.TLRPC.TLChatBannedRights
import org.telegram.tgnet.TLRPC.TLInputChannel
import org.telegram.tgnet.TLRPC.TLInputChannelEmpty
import org.telegram.tgnet.TLRPC.TLInputCheckPasswordEmpty
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.bot
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.PollEditTextCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextCheckCell2
import org.telegram.ui.Cells.TextDetailCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Cells.UserCell2
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.AnimatedTextView
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.CircularProgressDrawable
import org.telegram.ui.Components.CrossfadeDrawable
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
open class ChatRightsEditActivity(userId: Long, channelId: Long, rightsAdmin: TLChatAdminRights?, rightsBannedDefault: TLChatBannedRights?, rightsBanned: TLChatBannedRights?, rank: String?, type: Int, edit: Boolean, addingNew: Boolean, addingNewBotHash: String?) : BaseFragment() {
	private val currentUser: User?
	private val currentType: Int
	private val canEdit: Boolean
	private val initialRank: String
	private val botHash: String?
	private val isAddingNew: Boolean
	private var listViewAdapter: ListAdapter? = null
	private var listView: RecyclerListView? = null
	private var addBotButton: FrameLayout? = null
	private var addBotButtonText: AnimatedTextView? = null
	private var doneDrawable: CrossfadeDrawable? = null
	private var chatId: Long
	private var currentChat: Chat?
	private var isChannel = false
	private var loading = false
	private var asAdminT = 0f
	private var asAdmin = false
	private var initialAsAdmin = false
	private var adminRights: TLChatAdminRights? = null
	private var myAdminRights: TLChatAdminRights? = null
	private var bannedRights: TLChatBannedRights? = null
	private var defaultBannedRights: TLChatBannedRights? = null
	private var currentBannedRights = ""
	private var currentRank: String?
	private var rowCount = 0
	private var manageRow = 0
	private var changeInfoRow = 0
	private var postMessagesRow = 0
	private var editMessagesRow = 0
	private var deleteMessagesRow = 0
	private var addAdminsRow = 0
	private var anonymousRow = 0
	private var banUsersRow = 0
	private var addUsersRow = 0
	private var pinMessagesRow = 0
	private var rightsShadowRow = 0
	private var removeAdminRow = 0
	private var removeAdminShadowRow = 0
	private var cantEditInfoRow = 0
	private var transferOwnerShadowRow = 0
	private var transferOwnerRow = 0
	private var rankHeaderRow = 0
	private var rankRow = 0
	private var rankInfoRow = 0
	private var addBotButtonRow = 0
	private var sendMessagesRow = 0
	private var sendMediaRow = 0
	private var sendStickersRow = 0
	private var sendPollsRow = 0
	private var embedLinksRow = 0
	private var startVoiceChatRow = 0
	private var untilSectionRow = 0
	private var untilDateRow = 0
	private var delegate: ChatRightsEditActivityDelegate? = null
	private var initialIsSet = false
	private var closingKeyboardAfterFinish = false
	private var doneDrawableAnimator: ValueAnimator? = null
	private var asAdminAnimator: ValueAnimator? = null

	init {
		@Suppress("NAME_SHADOWING") var rightsAdmin = rightsAdmin
		@Suppress("NAME_SHADOWING") var rank = rank

		isAddingNew = addingNew
		chatId = channelId
		currentUser = messagesController.getUser(userId)
		currentType = type
		canEdit = edit
		botHash = addingNewBotHash
		currentChat = messagesController.getChat(chatId)

		if (rank == null) {
			rank = ""
		}

		currentRank = rank
		initialRank = currentRank!!

		currentChat?.let {
			isChannel = ChatObject.isChannel(it) && !it.megagroup
			myAdminRights = it.adminRights
		}

		if (myAdminRights == null) {
			myAdminRights = emptyAdminRights(currentType != TYPE_ADD_BOT || currentChat?.creator == true)
		}

		if (type == TYPE_ADMIN || type == TYPE_ADD_BOT) {
			if (type == TYPE_ADD_BOT) {
				val userFull = messagesController.getUserFull(userId)

				if (userFull != null) {
					val botDefaultRights = if (isChannel) userFull.botBroadcastAdminRights else userFull.botGroupAdminRights

					if (botDefaultRights != null) {
						if (rightsAdmin == null) {
							rightsAdmin = botDefaultRights
						}
						else {
							rightsAdmin.banUsers = rightsAdmin.banUsers || botDefaultRights.banUsers
							rightsAdmin.addAdmins = rightsAdmin.addAdmins || botDefaultRights.addAdmins
							rightsAdmin.postMessages = rightsAdmin.postMessages || botDefaultRights.postMessages
							rightsAdmin.pinMessages = rightsAdmin.pinMessages || botDefaultRights.pinMessages
							rightsAdmin.deleteMessages = rightsAdmin.deleteMessages || botDefaultRights.deleteMessages
							rightsAdmin.changeInfo = rightsAdmin.changeInfo || botDefaultRights.changeInfo
							rightsAdmin.anonymous = rightsAdmin.anonymous || botDefaultRights.anonymous
							rightsAdmin.editMessages = rightsAdmin.editMessages || botDefaultRights.editMessages
							rightsAdmin.manageCall = rightsAdmin.manageCall || botDefaultRights.manageCall
							rightsAdmin.other = rightsAdmin.other || botDefaultRights.other
						}
					}
				}
			}

			if (rightsAdmin == null) {
				initialAsAdmin = false

				if (type == TYPE_ADD_BOT) {
					adminRights = emptyAdminRights(false)
					asAdmin = isChannel
					asAdminT = (if (asAdmin) 1 else 0).toFloat()
					initialIsSet = false
				}
				else {
					adminRights = TLChatAdminRights()
					adminRights?.changeInfo = myAdminRights!!.changeInfo
					adminRights?.postMessages = myAdminRights!!.postMessages
					adminRights?.editMessages = myAdminRights!!.editMessages
					adminRights?.deleteMessages = myAdminRights!!.deleteMessages
					adminRights?.manageCall = myAdminRights!!.manageCall
					adminRights?.banUsers = myAdminRights!!.banUsers
					adminRights?.inviteUsers = myAdminRights!!.inviteUsers
					adminRights?.pinMessages = myAdminRights!!.pinMessages
					adminRights?.other = myAdminRights!!.other

					initialIsSet = false
				}
			}
			else {
				initialAsAdmin = true

				adminRights = TLChatAdminRights()
				adminRights?.changeInfo = rightsAdmin.changeInfo
				adminRights?.postMessages = rightsAdmin.postMessages
				adminRights?.editMessages = rightsAdmin.editMessages
				adminRights?.deleteMessages = rightsAdmin.deleteMessages
				adminRights?.manageCall = rightsAdmin.manageCall
				adminRights?.banUsers = rightsAdmin.banUsers
				adminRights?.inviteUsers = rightsAdmin.inviteUsers
				adminRights?.pinMessages = rightsAdmin.pinMessages
				adminRights?.addAdmins = rightsAdmin.addAdmins
				adminRights?.anonymous = rightsAdmin.anonymous
				adminRights?.other = rightsAdmin.other

				initialIsSet = adminRights!!.changeInfo || adminRights!!.postMessages || adminRights!!.editMessages || adminRights!!.deleteMessages || adminRights!!.banUsers || adminRights!!.inviteUsers || adminRights!!.pinMessages || adminRights!!.addAdmins || adminRights!!.manageCall || adminRights!!.anonymous || adminRights!!.other

				if (type == TYPE_ADD_BOT) {
					asAdmin = isChannel || initialIsSet
					asAdminT = if (asAdmin) 1f else 0f
					initialIsSet = false
				}
			}

			currentChat?.let {
				defaultBannedRights = it.defaultBannedRights
			}

			if (defaultBannedRights == null) {
				defaultBannedRights = TLChatBannedRights()
				defaultBannedRights?.pinMessages = true
				defaultBannedRights?.changeInfo = defaultBannedRights!!.pinMessages
				defaultBannedRights?.inviteUsers = defaultBannedRights!!.changeInfo
				defaultBannedRights?.sendPolls = defaultBannedRights!!.inviteUsers
				defaultBannedRights?.sendInline = defaultBannedRights!!.sendPolls
				defaultBannedRights?.sendGames = defaultBannedRights!!.sendInline
				defaultBannedRights?.sendGifs = defaultBannedRights!!.sendGames
				defaultBannedRights?.sendStickers = defaultBannedRights!!.sendGifs
				defaultBannedRights?.embedLinks = defaultBannedRights!!.sendStickers
				defaultBannedRights?.sendMessages = defaultBannedRights!!.embedLinks
				defaultBannedRights?.sendMedia = defaultBannedRights!!.sendMessages
				defaultBannedRights?.viewMessages = defaultBannedRights!!.sendMedia
			}

			if (defaultBannedRights?.changeInfo != true) {
				adminRights?.changeInfo = true
			}

			if (defaultBannedRights?.pinMessages != true) {
				adminRights?.pinMessages = true
			}
		}
		else if (type == TYPE_BANNED) {
			defaultBannedRights = rightsBannedDefault

			if (defaultBannedRights == null) {
				defaultBannedRights = TLChatBannedRights()
				defaultBannedRights?.pinMessages = false
				defaultBannedRights?.changeInfo = defaultBannedRights!!.pinMessages
				defaultBannedRights?.inviteUsers = defaultBannedRights!!.changeInfo
				defaultBannedRights?.sendPolls = defaultBannedRights!!.inviteUsers
				defaultBannedRights?.sendInline = defaultBannedRights!!.sendPolls
				defaultBannedRights?.sendGames = defaultBannedRights!!.sendInline
				defaultBannedRights?.sendGifs = defaultBannedRights!!.sendGames
				defaultBannedRights?.sendStickers = defaultBannedRights!!.sendGifs
				defaultBannedRights?.embedLinks = defaultBannedRights!!.sendStickers
				defaultBannedRights?.sendMessages = defaultBannedRights!!.embedLinks
				defaultBannedRights?.sendMedia = defaultBannedRights!!.sendMessages
				defaultBannedRights?.viewMessages = defaultBannedRights!!.sendMedia
			}

			bannedRights = TLChatBannedRights()

			if (rightsBanned == null) {
				bannedRights!!.pinMessages = false
				bannedRights!!.changeInfo = bannedRights!!.pinMessages
				bannedRights!!.inviteUsers = bannedRights!!.changeInfo
				bannedRights!!.sendPolls = bannedRights!!.inviteUsers
				bannedRights!!.sendInline = bannedRights!!.sendPolls
				bannedRights!!.sendGames = bannedRights!!.sendInline
				bannedRights!!.sendGifs = bannedRights!!.sendGames
				bannedRights!!.sendStickers = bannedRights!!.sendGifs
				bannedRights!!.embedLinks = bannedRights!!.sendStickers
				bannedRights!!.sendMessages = bannedRights!!.embedLinks
				bannedRights!!.sendMedia = bannedRights!!.sendMessages
				bannedRights!!.viewMessages = bannedRights!!.sendMedia
			}
			else {
				bannedRights!!.viewMessages = rightsBanned.viewMessages
				bannedRights!!.sendMessages = rightsBanned.sendMessages
				bannedRights!!.sendMedia = rightsBanned.sendMedia
				bannedRights!!.sendStickers = rightsBanned.sendStickers
				bannedRights!!.sendGifs = rightsBanned.sendGifs
				bannedRights!!.sendGames = rightsBanned.sendGames
				bannedRights!!.sendInline = rightsBanned.sendInline
				bannedRights!!.embedLinks = rightsBanned.embedLinks
				bannedRights!!.sendPolls = rightsBanned.sendPolls
				bannedRights!!.inviteUsers = rightsBanned.inviteUsers
				bannedRights!!.changeInfo = rightsBanned.changeInfo
				bannedRights!!.pinMessages = rightsBanned.pinMessages
				bannedRights!!.untilDate = rightsBanned.untilDate
			}

			if (defaultBannedRights!!.viewMessages) {
				bannedRights!!.viewMessages = true
			}

			if (defaultBannedRights!!.sendMessages) {
				bannedRights!!.sendMessages = true
			}

			if (defaultBannedRights!!.sendMedia) {
				bannedRights!!.sendMedia = true
			}

			if (defaultBannedRights!!.sendStickers) {
				bannedRights!!.sendStickers = true
			}

			if (defaultBannedRights!!.sendGifs) {
				bannedRights!!.sendGifs = true
			}

			if (defaultBannedRights!!.sendGames) {
				bannedRights!!.sendGames = true
			}

			if (defaultBannedRights!!.sendInline) {
				bannedRights!!.sendInline = true
			}

			if (defaultBannedRights!!.embedLinks) {
				bannedRights!!.embedLinks = true
			}

			if (defaultBannedRights!!.sendPolls) {
				bannedRights!!.sendPolls = true
			}

			if (defaultBannedRights!!.inviteUsers) {
				bannedRights!!.inviteUsers = true
			}

			if (defaultBannedRights!!.changeInfo) {
				bannedRights!!.changeInfo = true
			}

			if (defaultBannedRights!!.pinMessages) {
				bannedRights!!.pinMessages = true
			}

			currentBannedRights = ChatObject.getBannedRightsString(bannedRights)
			initialIsSet = rightsBanned == null || !rightsBanned.viewMessages
		}

		updateRows(false)
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		when (currentType) {
			TYPE_ADMIN -> {
				actionBar?.setTitle(context.getString(R.string.EditAdmin))
			}

			TYPE_ADD_BOT -> {
				actionBar?.setTitle(context.getString(R.string.AddBot))
			}

			else -> {
				actionBar?.setTitle(context.getString(R.string.UserRestrictions))
			}
		}

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						if (checkDiscard()) {
							finishFragment()
						}
					}

					DONE_BUTTON -> {
						onDonePressed()
					}
				}
			}
		})

		if (canEdit || !isChannel && currentChat?.creator == true && UserObject.isUserSelf(currentUser)) {
			val menu = actionBar?.createMenu()

			val checkmark = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_ab_done, null)?.mutate()
			checkmark?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.SRC_IN)

			doneDrawable = CrossfadeDrawable(checkmark, CircularProgressDrawable(context.getColor(R.color.brand)))

			menu?.addItemWithWidth(DONE_BUTTON, 0, AndroidUtilities.dp(56f), context.getString(R.string.Done))
			menu?.getItem(DONE_BUTTON)?.setIcon(doneDrawable)
		}

		fragmentView = object : FrameLayout(context) {
			private var previousHeight = -1

			override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
				super.onLayout(changed, left, top, right, bottom)

				val height = bottom - top

				if (previousHeight != -1 && abs(previousHeight - height) > AndroidUtilities.dp(20f)) {
					listView?.smoothScrollToPosition(rowCount - 1)
				}

				previousHeight = height
			}
		}

		fragmentView?.setBackgroundResource(R.color.light_background)

		val frameLayout = fragmentView as FrameLayout

		fragmentView?.isFocusableInTouchMode = true

		listView = object : RecyclerListView(context) {
			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(e: MotionEvent): Boolean {
				return if (loading) {
					false
				}
				else {
					super.onTouchEvent(e)
				}
			}

			override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
				return if (loading) {
					false
				}
				else {
					super.onInterceptTouchEvent(e)
				}
			}
		}

		listView?.clipChildren = currentType != TYPE_ADD_BOT

		val linearLayoutManager: LinearLayoutManager = object : LinearLayoutManager(context, VERTICAL, false) {
			@Deprecated("Deprecated in Java", ReplaceWith("5000"))
			override fun getExtraLayoutSpace(state: RecyclerView.State): Int {
				return 5000
			}
		}

		linearLayoutManager.initialPrefetchItemCount = 100

		listView?.layoutManager = linearLayoutManager
		listView?.adapter = ListAdapter(context).also { listViewAdapter = it }

		val itemAnimator = DefaultItemAnimator()

		if (currentType == TYPE_ADD_BOT) {
			listView?.setResetSelectorOnChanged(false)
		}

		itemAnimator.setDelayAnimations(false)

		listView?.itemAnimator = itemAnimator

		listView?.verticalScrollbarPosition = if (LocaleController.isRTL) View.SCROLLBAR_POSITION_LEFT else View.SCROLLBAR_POSITION_RIGHT

		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					AndroidUtilities.hideKeyboard(parentActivity!!.currentFocus)
				}
			}
		})

		listView?.setOnItemClickListener { view, position ->
			if (!canEdit && (!currentChat!!.creator || currentType != TYPE_ADMIN || position != anonymousRow)) {
				return@setOnItemClickListener
			}

			if (position == 0) {
				val args = Bundle()
				args.putLong("user_id", currentUser!!.id)
				presentFragment(ProfileActivity(args))
			}
			else if (position == removeAdminRow) {
				if (currentType == TYPE_ADMIN) {
					messagesController.setUserAdminRole(chatId, currentUser, TLChatAdminRights(), currentRank, isChannel, getFragmentForAlert(0), isAddingNew, false, null, null)
					delegate?.didSetRights(0, adminRights, bannedRights, currentRank)
					finishFragment()
				}
				else if (currentType == TYPE_BANNED) {
					bannedRights = TLChatBannedRights()
					bannedRights!!.viewMessages = true
					bannedRights!!.sendMedia = true
					bannedRights!!.sendMessages = true
					bannedRights!!.sendStickers = true
					bannedRights!!.sendGifs = true
					bannedRights!!.sendGames = true
					bannedRights!!.sendInline = true
					bannedRights!!.embedLinks = true
					bannedRights!!.pinMessages = true
					bannedRights!!.sendPolls = true
					bannedRights!!.inviteUsers = true
					bannedRights!!.changeInfo = true
					bannedRights!!.untilDate = 0

					onDonePressed()
				}
			}
			else if (position == transferOwnerRow) {
				initTransfer(null, null, true)
			}
			else if (position == untilDateRow) {
				if (parentActivity == null) {
					return@setOnItemClickListener
				}

				val builder = BottomSheet.Builder(context)
				builder.setApplyTopPadding(false)

				val linearLayout = LinearLayout(context)
				linearLayout.orientation = LinearLayout.VERTICAL

				val headerCell = HeaderCell(context, 23, 15, 15, false)
				headerCell.height = 47
				headerCell.setText(context.getString(R.string.UserRestrictionsDuration))

				linearLayout.addView(headerCell)

				val linearLayoutInviteContainer = LinearLayout(context)
				linearLayoutInviteContainer.orientation = LinearLayout.VERTICAL
				linearLayout.addView(linearLayoutInviteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

				val buttons = Array(5) { BottomSheet.BottomSheetCell(context, 0) }

				for (a in buttons.indices) {
					buttons[a].setPadding(AndroidUtilities.dp(7f), 0, AndroidUtilities.dp(7f), 0)
					buttons[a].tag = a
					buttons[a].background = Theme.getSelectorDrawable(false)

					val text = when (a) {
						0 -> context.getString(R.string.UserRestrictionsUntilForever)
						1 -> LocaleController.formatPluralString("Days", 1)
						2 -> LocaleController.formatPluralString("Weeks", 1)
						3 -> LocaleController.formatPluralString("Months", 1)
						4 -> context.getString(R.string.UserRestrictionsCustom)
						else -> context.getString(R.string.UserRestrictionsCustom)
					}

					buttons[a].setTextAndIcon(text, 0)

					linearLayoutInviteContainer.addView(buttons[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

					buttons[a].setOnClickListener { v2: View ->
						when (v2.tag as Int) {
							0 -> {
								bannedRights?.untilDate = 0
								listViewAdapter?.notifyItemChanged(untilDateRow)
							}

							1 -> {
								bannedRights?.untilDate = connectionsManager.currentTime + 60 * 60 * 24
								listViewAdapter?.notifyItemChanged(untilDateRow)
							}

							2 -> {
								bannedRights?.untilDate = connectionsManager.currentTime + 60 * 60 * 24 * 7
								listViewAdapter?.notifyItemChanged(untilDateRow)
							}

							3 -> {
								bannedRights?.untilDate = connectionsManager.currentTime + 60 * 60 * 24 * 30
								listViewAdapter?.notifyItemChanged(untilDateRow)
							}

							4 -> {
								val calendar = Calendar.getInstance()
								val year = calendar[Calendar.YEAR]
								val monthOfYear = calendar[Calendar.MONTH]
								val dayOfMonth = calendar[Calendar.DAY_OF_MONTH]

								try {
									val dialog = DatePickerDialog(parentActivity!!, { _, year1, month, dayOfMonth1 ->
										val calendar1 = Calendar.getInstance()
										calendar1.clear()
										calendar1[year1, month] = dayOfMonth1
										val time = (calendar1.time.time / 1000).toInt()

										try {
											val dialog13 = TimePickerDialog(parentActivity, { _, hourOfDay, minute ->
												bannedRights?.untilDate = time + hourOfDay * 3600 + minute * 60
												listViewAdapter?.notifyItemChanged(untilDateRow)
											}, 0, 0, true)

											dialog13.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.Set), dialog13)
											dialog13.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.Cancel)) { _, _ -> }

											showDialog(dialog13)
										}
										catch (e: Exception) {
											FileLog.e(e)
										}
									}, year, monthOfYear, dayOfMonth)

									val datePicker = dialog.datePicker

									val date = Calendar.getInstance()
									date.timeInMillis = System.currentTimeMillis()
									date[Calendar.HOUR_OF_DAY] = date.getMinimum(Calendar.HOUR_OF_DAY)
									date[Calendar.MINUTE] = date.getMinimum(Calendar.MINUTE)
									date[Calendar.SECOND] = date.getMinimum(Calendar.SECOND)
									date[Calendar.MILLISECOND] = date.getMinimum(Calendar.MILLISECOND)

									datePicker.minDate = date.timeInMillis

									date.timeInMillis = System.currentTimeMillis() + 31536000000L

									date[Calendar.HOUR_OF_DAY] = date.getMaximum(Calendar.HOUR_OF_DAY)
									date[Calendar.MINUTE] = date.getMaximum(Calendar.MINUTE)
									date[Calendar.SECOND] = date.getMaximum(Calendar.SECOND)
									date[Calendar.MILLISECOND] = date.getMaximum(Calendar.MILLISECOND)

									datePicker.maxDate = date.timeInMillis
									dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.Set), dialog)
									dialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.Cancel)) { _, _ -> }

									dialog.setOnShowListener {
										val count = datePicker.childCount
										var b = 0

										while (b < count) {
											val child = datePicker.getChildAt(b)
											val layoutParams = child.layoutParams
											layoutParams.width = LayoutHelper.MATCH_PARENT
											child.layoutParams = layoutParams
											b++
										}
									}

									showDialog(dialog)
								}
								catch (e: Exception) {
									FileLog.e(e)
								}
							}
						}

						builder.dismissRunnable.run()
					}
				}

				builder.customView = linearLayout

				showDialog(builder.create())
			}
			else if (view is TextCheckCell2) {
				if (view.hasIcon()) {
					if (currentType != TYPE_ADD_BOT) {
						AlertDialog.Builder(context).setTitle(context.getString(R.string.UserRestrictionsCantModify)).setMessage(context.getString(R.string.UserRestrictionsCantModifyDisabled)).setPositiveButton(context.getString(R.string.OK), null).create().show()
					}

					return@setOnItemClickListener
				}
				if (!view.isEnabled) {
					if ((currentType == TYPE_ADD_BOT || currentType == TYPE_ADMIN) && (position == changeInfoRow && defaultBannedRights != null && defaultBannedRights?.changeInfo != true || position == pinMessagesRow && defaultBannedRights != null && defaultBannedRights?.pinMessages != true)) {
						AlertDialog.Builder(context).setTitle(context.getString(R.string.UserRestrictionsCantModify)).setMessage(context.getString(R.string.UserRestrictionsCantModifyEnabled)).setPositiveButton(context.getString(R.string.OK), null).create().show()
					}

					return@setOnItemClickListener
				}

				if (currentType != TYPE_ADD_BOT) {
					view.isChecked = !view.isChecked
				}

				var value = view.isChecked

				if (position == manageRow) {
					asAdmin = !asAdmin
					value = asAdmin
					updateAsAdmin(true)
				}
				else if (position == changeInfoRow) {
					if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
						adminRights!!.changeInfo = !adminRights!!.changeInfo
						value = adminRights!!.changeInfo
					}
					else {
						bannedRights!!.changeInfo = !bannedRights!!.changeInfo
						value = bannedRights!!.changeInfo
					}
				}
				else if (position == postMessagesRow) {
					adminRights!!.postMessages = !adminRights!!.postMessages
					value = adminRights!!.postMessages
				}
				else if (position == editMessagesRow) {
					adminRights!!.editMessages = !adminRights!!.editMessages
					value = adminRights!!.editMessages
				}
				else if (position == deleteMessagesRow) {
					adminRights!!.deleteMessages = !adminRights!!.deleteMessages
					value = adminRights!!.deleteMessages
				}
				else if (position == addAdminsRow) {
					adminRights!!.addAdmins = !adminRights!!.addAdmins
					value = adminRights!!.addAdmins
				}
				else if (position == anonymousRow) {
					adminRights!!.anonymous = !adminRights!!.anonymous
					value = adminRights!!.anonymous
				}
				else if (position == banUsersRow) {
					adminRights!!.banUsers = !adminRights!!.banUsers
					value = adminRights!!.banUsers
				}
				else if (position == startVoiceChatRow) {
					adminRights!!.manageCall = !adminRights!!.manageCall
					value = adminRights!!.manageCall
				}
				else if (position == addUsersRow) {
					if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
						adminRights!!.inviteUsers = !adminRights!!.inviteUsers
						value = adminRights!!.inviteUsers
					}
					else {
						bannedRights!!.inviteUsers = !bannedRights!!.inviteUsers
						value = bannedRights!!.inviteUsers
					}
				}
				else if (position == pinMessagesRow) {
					if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
						adminRights!!.pinMessages = !adminRights!!.pinMessages
						value = adminRights!!.pinMessages
					}
					else {
						bannedRights!!.pinMessages = !bannedRights!!.pinMessages
						value = bannedRights!!.pinMessages
					}
				}
				else if (currentType == TYPE_BANNED && bannedRights != null) {
					val disabled = !view.isChecked

					when (position) {
						sendMessagesRow -> {
							bannedRights!!.sendMessages = !bannedRights!!.sendMessages
							value = bannedRights!!.sendMessages
						}

						sendMediaRow -> {
							bannedRights!!.sendMedia = !bannedRights!!.sendMedia
							value = bannedRights!!.sendMedia
						}

						sendStickersRow -> {
							bannedRights!!.sendInline = !bannedRights!!.sendStickers
							bannedRights!!.sendGifs = bannedRights!!.sendInline
							bannedRights!!.sendGames = bannedRights!!.sendGifs
							bannedRights!!.sendStickers = bannedRights!!.sendGames
							value = bannedRights!!.sendStickers
						}

						embedLinksRow -> {
							bannedRights!!.embedLinks = !bannedRights!!.embedLinks
							value = bannedRights!!.embedLinks
						}

						sendPollsRow -> {
							bannedRights!!.sendPolls = !bannedRights!!.sendPolls
							value = bannedRights!!.sendPolls
						}
					}

					if (disabled) {
						if (bannedRights!!.viewMessages && !bannedRights!!.sendMessages) {
							bannedRights!!.sendMessages = true

							val holder = listView?.findViewHolderForAdapterPosition(sendMessagesRow)

							if (holder != null) {
								(holder.itemView as TextCheckCell2).isChecked = false
							}
						}

						if ((bannedRights!!.viewMessages || bannedRights!!.sendMessages) && !bannedRights!!.sendMedia) {
							bannedRights!!.sendMedia = true

							val holder = listView?.findViewHolderForAdapterPosition(sendMediaRow)

							if (holder != null) {
								(holder.itemView as TextCheckCell2).isChecked = false
							}
						}

						if ((bannedRights!!.viewMessages || bannedRights!!.sendMessages) && !bannedRights!!.sendPolls) {
							bannedRights!!.sendPolls = true

							val holder = listView?.findViewHolderForAdapterPosition(sendPollsRow)

							if (holder != null) {
								(holder.itemView as TextCheckCell2).isChecked = false
							}
						}

						if ((bannedRights!!.viewMessages || bannedRights!!.sendMessages) && !bannedRights!!.sendStickers) {
							bannedRights!!.sendInline = true
							bannedRights!!.sendGifs = bannedRights!!.sendInline
							bannedRights!!.sendGames = bannedRights!!.sendGifs
							bannedRights!!.sendStickers = bannedRights!!.sendGames

							val holder = listView?.findViewHolderForAdapterPosition(sendStickersRow)

							if (holder != null) {
								(holder.itemView as TextCheckCell2).isChecked = false
							}
						}

						if ((bannedRights!!.viewMessages || bannedRights!!.sendMessages) && !bannedRights!!.embedLinks) {
							bannedRights!!.embedLinks = true

							val holder = listView?.findViewHolderForAdapterPosition(embedLinksRow)

							if (holder != null) {
								(holder.itemView as TextCheckCell2).isChecked = false
							}
						}
					}
					else {
						if ((!bannedRights!!.sendMessages || !bannedRights!!.embedLinks || !bannedRights!!.sendInline || !bannedRights!!.sendMedia || !bannedRights!!.sendPolls) && bannedRights!!.viewMessages) {
							bannedRights!!.viewMessages = false
						}

						if ((!bannedRights!!.embedLinks || !bannedRights!!.sendInline || !bannedRights!!.sendMedia || !bannedRights!!.sendPolls) && bannedRights!!.sendMessages) {
							bannedRights!!.sendMessages = false

							val holder = listView?.findViewHolderForAdapterPosition(sendMessagesRow)

							if (holder != null) {
								(holder.itemView as TextCheckCell2).isChecked = true
							}
						}
					}
				}

				if (currentType == TYPE_ADD_BOT) {
					view.isChecked = asAdmin && value
				}

				updateRows(true)
			}
		}

		return fragmentView
	}

	override fun onResume() {
		super.onResume()
		listViewAdapter?.notifyDataSetChanged()
		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
	}

	private val isDefaultAdminRights: Boolean
		get() {
			val adminRights = adminRights ?: return false
			return adminRights.changeInfo && adminRights.deleteMessages && adminRights.banUsers && adminRights.inviteUsers && adminRights.pinMessages && adminRights.manageCall && !adminRights.addAdmins && !adminRights.anonymous || !adminRights.changeInfo && !adminRights.deleteMessages && !adminRights.banUsers && !adminRights.inviteUsers && !adminRights.pinMessages && !adminRights.manageCall && !adminRights.addAdmins && !adminRights.anonymous
		}

	private fun hasAllAdminRights(): Boolean {
		val adminRights = adminRights ?: return false

		return if (isChannel) {
			adminRights.changeInfo && adminRights.postMessages && adminRights.editMessages && adminRights.deleteMessages && adminRights.inviteUsers && adminRights.addAdmins && adminRights.manageCall
		}
		else {
			adminRights.changeInfo && adminRights.deleteMessages && adminRights.banUsers && adminRights.inviteUsers && adminRights.pinMessages && adminRights.addAdmins && adminRights.manageCall
		}
	}

	private fun initTransferClean(srp: InputCheckPasswordSRP?, passwordFragment: TwoStepVerificationActivity?) {
		val parentActivity = parentActivity ?: return

		if (srp != null && !ChatObject.isChannel(currentChat)) {
			messagesController.convertToMegaGroup(parentActivity, chatId, this, {
				if (it != 0L) {
					chatId = it
					currentChat = messagesController.getChat(it)
					initTransferClean(srp, passwordFragment)
				}
			}, null)

			return
		}

		val req = TLChannelsEditCreator()

		if (ChatObject.isChannel(currentChat)) {
			req.channel = TLInputChannel().also {
				it.channelId = currentChat!!.id
				it.accessHash = currentChat!!.accessHash
			}
		}
		else {
			req.channel = TLInputChannelEmpty()
		}

		req.password = srp ?: TLInputCheckPasswordEmpty()
		req.userId = messagesController.getInputUser(currentUser)

		connectionsManager.sendRequest(req, { response, error ->
			FileLog.d("Transfer response is $response")

			AndroidUtilities.runOnUIThread {
				if (error != null) {
					if ("PASSWORD_HASH_INVALID" == error.text) {
						if (srp == null) {
							val fragment = TwoStepVerificationActivity()

							fragment.setDelegate {
								initTransferClean(it, fragment)
							}

							presentFragment(fragment)
						}
					}
					else if ("PASSWORD_MISSING" == error.text || error.text?.startsWith("PASSWORD_TOO_FRESH_") == true || error.text?.startsWith("SESSION_TOO_FRESH_") == true) {
						passwordFragment?.needHideProgress()

						val builder = AlertDialog.Builder(parentActivity)
						builder.setTitle(parentActivity.getString(R.string.EditAdminTransferAlertTitle))

						val linearLayout = LinearLayout(parentActivity)
						linearLayout.setPadding(AndroidUtilities.dp(24f), AndroidUtilities.dp(2f), AndroidUtilities.dp(24f), 0)
						linearLayout.orientation = LinearLayout.VERTICAL

						builder.setView(linearLayout)

						var messageTextView = TextView(parentActivity)
						messageTextView.setTextColor(parentActivity.getColor(R.color.text))
						messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
						messageTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP

						if (isChannel) {
							messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("EditChannelAdminTransferAlertText", R.string.EditChannelAdminTransferAlertText, UserObject.getFirstName(currentUser)))
						}
						else {
							messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("EditAdminTransferAlertText", R.string.EditAdminTransferAlertText, UserObject.getFirstName(currentUser)))
						}

						linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

						var linearLayout2 = LinearLayout(parentActivity)
						linearLayout2.orientation = LinearLayout.HORIZONTAL

						linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 11f, 0f, 0f))

						var dotImageView = ImageView(parentActivity)
						dotImageView.setImageResource(R.drawable.list_circle)
						dotImageView.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(11f) else 0, AndroidUtilities.dp(9f), if (LocaleController.isRTL) 0 else AndroidUtilities.dp(11f), 0)
						dotImageView.colorFilter = PorterDuffColorFilter(parentActivity.getColor(R.color.text), PorterDuff.Mode.SRC_IN)

						messageTextView = TextView(parentActivity)
						messageTextView.setTextColor(parentActivity.getColor(R.color.text))
						messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
						messageTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
						messageTextView.text = AndroidUtilities.replaceTags(parentActivity.getString(R.string.EditAdminTransferAlertText1))

						if (LocaleController.isRTL) {
							linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
							linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT))
						}
						else {
							linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
							linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
						}

						linearLayout2 = LinearLayout(parentActivity)
						linearLayout2.orientation = LinearLayout.HORIZONTAL

						linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 11f, 0f, 0f))

						dotImageView = ImageView(parentActivity)
						dotImageView.setImageResource(R.drawable.list_circle)
						dotImageView.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(11f) else 0, AndroidUtilities.dp(9f), if (LocaleController.isRTL) 0 else AndroidUtilities.dp(11f), 0)
						dotImageView.colorFilter = PorterDuffColorFilter(parentActivity.getColor(R.color.text), PorterDuff.Mode.SRC_IN)

						messageTextView = TextView(parentActivity)
						messageTextView.setTextColor(parentActivity.getColor(R.color.text))
						messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
						messageTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
						messageTextView.text = AndroidUtilities.replaceTags(parentActivity.getString(R.string.EditAdminTransferAlertText2))

						if (LocaleController.isRTL) {
							linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
							linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT))
						}
						else {
							linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
							linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
						}

						if ("PASSWORD_MISSING" == error.text) {
							builder.setPositiveButton(parentActivity.getString(R.string.EditAdminTransferSetPassword)) { _, _ ->
								presentFragment(TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null))
							}

							builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)
						}
						else {
							messageTextView = TextView(parentActivity)
							messageTextView.setTextColor(parentActivity.getColor(R.color.text))
							messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
							messageTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
							messageTextView.text = parentActivity.getString(R.string.EditAdminTransferAlertText3)

							linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 11f, 0f, 0f))

							builder.setNegativeButton(parentActivity.getString(R.string.OK), null)
						}

						showDialog(builder.create())
					}
					else if ("SRP_ID_INVALID" == error.text) {
						val getPasswordReq = TLAccountGetPassword()

						connectionsManager.sendRequest(getPasswordReq, { response2, _ ->
							AndroidUtilities.runOnUIThread {
								if (response2 is TLAccountPassword) {
									passwordFragment?.setCurrentPasswordInfo(null, response2)

									TwoStepVerificationActivity.initPasswordNewAlgo(response2)

									initTransferClean(passwordFragment?.newSrpPassword, passwordFragment)
								}
							}
						}, ConnectionsManager.RequestFlagWithoutLogin)
					}
					else if (error.text == "CHANNELS_TOO_MUCH") {
						if (!accountInstance.userConfig.isPremium) {
							showDialog(LimitReachedBottomSheet(this, LimitReachedBottomSheet.TYPE_TO_MANY_COMMUNITIES, currentAccount))
						}
						else {
							presentFragment(TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_EDIT))
						}
					}
					else {
						if (passwordFragment != null) {
							passwordFragment.needHideProgress()
							passwordFragment.finishFragment()
						}

						AlertsCreator.showAddUserAlert(error.text, this@ChatRightsEditActivity, isChannel, req)
					}
				}
				else {
					// MARK: originally there was this check
					// if (srp != null) {
					delegate?.didChangeOwner(currentUser!!)

					if (passwordFragment != null) {
						removeSelfFromStack()

						passwordFragment.needHideProgress()
						passwordFragment.finishFragment()
					}
					else {
						actionBar?.backButton?.performClick()
					}
					// }
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun initTransfer(srp: InputCheckPasswordSRP?, passwordFragment: TwoStepVerificationActivity?, showDialog: Boolean) {
		if (showDialog) {
			val context = context ?: return
			val builder = AlertDialog.Builder(context)

			if (isChannel) {
				builder.setTitle(context.getString(R.string.EditAdminChannelTransfer))
			}
			else {
				builder.setTitle(context.getString(R.string.EditAdminGroupTransfer))
			}

			builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("EditAdminTransferReadyAlertText", R.string.EditAdminTransferReadyAlertText, currentChat?.title, UserObject.getFirstName(currentUser))))

			builder.setPositiveButton(context.getString(R.string.EditAdminTransferChangeOwner)) { _, _ ->
				initTransferClean(srp, passwordFragment)
			}

			builder.setNegativeButton(context.getString(R.string.Cancel), null)

			val dialog = builder.create()

			showDialog(dialog)

			val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
			button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
		}
		else {
			initTransferClean(srp, passwordFragment)
		}
	}

	private fun updateRows(update: Boolean) {
		val currentChat = currentChat

		val transferOwnerShadowRowPrev = min(transferOwnerShadowRow, transferOwnerRow)

		manageRow = -1
		changeInfoRow = -1
		postMessagesRow = -1
		editMessagesRow = -1
		deleteMessagesRow = -1
		addAdminsRow = -1
		anonymousRow = -1
		banUsersRow = -1
		addUsersRow = -1
		pinMessagesRow = -1
		rightsShadowRow = -1
		removeAdminRow = -1
		removeAdminShadowRow = -1
		cantEditInfoRow = -1
		transferOwnerShadowRow = -1
		transferOwnerRow = -1
		rankHeaderRow = -1
		rankRow = -1
		rankInfoRow = -1
		sendMessagesRow = -1
		sendMediaRow = -1
		sendStickersRow = -1
		sendPollsRow = -1
		embedLinksRow = -1
		startVoiceChatRow = -1
		untilSectionRow = -1
		untilDateRow = -1
		addBotButtonRow = -1
		rowCount = 3

		// val permissionsStartRow = rowCount

		if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
			if (isChannel) {
				changeInfoRow = rowCount++
				postMessagesRow = rowCount++
				editMessagesRow = rowCount++
				deleteMessagesRow = rowCount++
				addUsersRow = rowCount++
				// MARK: uncomment to enable live streams
				// startVoiceChatRow = rowCount++
				addAdminsRow = rowCount++
			}
			else {
				if (currentType == TYPE_ADD_BOT) {
					manageRow = rowCount++
				}

				changeInfoRow = rowCount++
				deleteMessagesRow = rowCount++
				banUsersRow = rowCount++
				addUsersRow = rowCount++
				// MARK: uncomment to enable pinned messages
				// pinMessagesRow = rowCount++
				// MARK: uncomment to enable live streams
				// startVoiceChatRow = rowCount++
				addAdminsRow = rowCount++
				anonymousRow = rowCount++
			}
		}
		else if (currentType == TYPE_BANNED) {
			sendMessagesRow = rowCount++
			sendMediaRow = rowCount++
			// sendStickersRow = rowCount++
			// sendPollsRow = rowCount++
			embedLinksRow = rowCount++
			addUsersRow = rowCount++
			// MARK: uncomment to enable pinned messages
			//pinMessagesRow = rowCount++
			changeInfoRow = rowCount++
			untilSectionRow = rowCount++
			untilDateRow = rowCount++
		}

		// val permissionsEndRow = rowCount

		if (canEdit) {
			if (!isChannel && (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT && asAdmin)) {
				rightsShadowRow = rowCount++
				rankHeaderRow = rowCount++
				rankRow = rowCount++
				rankInfoRow = rowCount++
			}

			if (currentChat != null && currentChat.creator && currentType == TYPE_ADMIN && hasAllAdminRights() && !currentUser!!.bot) {
				if (rightsShadowRow == -1) {
					transferOwnerShadowRow = rowCount++
				}

				transferOwnerRow = rowCount++

				if (rightsShadowRow != -1) {
					transferOwnerShadowRow = rowCount++
				}
			}

			if (initialIsSet) {
				if (rightsShadowRow == -1) {
					rightsShadowRow = rowCount++
				}

				removeAdminRow = rowCount++
				removeAdminShadowRow = rowCount++
			}
		}
		else {
			if (currentType == TYPE_ADMIN) {
				if (!isChannel && (!currentRank.isNullOrEmpty() || currentChat?.creator == true && UserObject.isUserSelf(currentUser))) {
					rightsShadowRow = rowCount++
					rankHeaderRow = rowCount++
					rankRow = rowCount++

					if (currentChat?.creator == true && UserObject.isUserSelf(currentUser)) {
						rankInfoRow = rowCount++
					}
					else {
						cantEditInfoRow = rowCount++
					}
				}
				else {
					cantEditInfoRow = rowCount++
				}
			}
			else {
				rightsShadowRow = rowCount++
			}
		}

		if (currentType == TYPE_ADD_BOT) {
			addBotButtonRow = rowCount++
		}

		if (update) {
			if (transferOwnerShadowRowPrev == -1 && transferOwnerShadowRow != -1) {
				listViewAdapter?.notifyItemRangeInserted(min(transferOwnerShadowRow, transferOwnerRow), 2)
			}
			else if (transferOwnerShadowRowPrev != -1 && transferOwnerShadowRow == -1) {
				listViewAdapter?.notifyItemRangeRemoved(transferOwnerShadowRowPrev, 2)
			}
		}
	}

	private fun onDonePressed() {
		if (loading) {
			return
		}

		//		if (!ChatObject.isChannel(currentChat) && (currentType == TYPE_BANNED || currentType == TYPE_ADMIN && (!isDefaultAdminRights() || rankRow != -1 && currentRank.codePointCount(0, currentRank.length()) > MAX_RANK_LENGTH) || currentType == TYPE_ADD_BOT && (currentRank != null || !isDefaultAdminRights()))) {
//			messagesController.convertToMegaGroup(getParentActivity(), chatId, this, param -> {
//				if (param != 0) {
//					chatId = param;
//					currentChat = messagesController.getChat(param);
//					onDonePressed();
//				}
//			});
//			return;
//		}

		if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
			if (rankRow != -1 && currentRank!!.codePointCount(0, currentRank!!.length) > MAX_RANK_LENGTH) {
				listView?.smoothScrollToPosition(rankRow)

				parentActivity?.vibrate(200L)

				val holder = listView?.findViewHolderForAdapterPosition(rankHeaderRow)

				if (holder != null) {
					AndroidUtilities.shakeView(holder.itemView, 2f, 0)
				}

				return
			}

			if (isChannel) {
				adminRights!!.banUsers = false
				adminRights!!.pinMessages = adminRights!!.banUsers
			}
			else {
				adminRights!!.editMessages = false
				adminRights!!.postMessages = adminRights!!.editMessages
			}

			adminRights!!.other = !adminRights!!.changeInfo && !adminRights!!.postMessages && !adminRights!!.editMessages && !adminRights!!.deleteMessages && !adminRights!!.banUsers && !adminRights!!.inviteUsers && !adminRights!!.pinMessages && !adminRights!!.addAdmins && !adminRights!!.anonymous && !adminRights!!.manageCall
		}

		var finishFragment = true

		when (currentType) {
			TYPE_ADMIN -> {
				finishFragment = delegate == null
				setLoading(true)

				messagesController.setUserAdminRole(chatId, currentUser, adminRights, currentRank, isChannel, this, isAddingNew, false, null, {
					if (delegate != null) {
						delegate?.didSetRights(if (adminRights!!.changeInfo || adminRights!!.postMessages || adminRights!!.editMessages || adminRights!!.deleteMessages || adminRights!!.banUsers || adminRights!!.inviteUsers || adminRights!!.pinMessages || adminRights!!.addAdmins || adminRights!!.anonymous || adminRights!!.manageCall || adminRights!!.other) 1 else 0, adminRights, bannedRights, currentRank)
						finishFragment()
					}
				}) {
					setLoading(false)
					true
				}
			}

			TYPE_BANNED -> {
				messagesController.setParticipantBannedRole(chatId, currentUser, null, bannedRights, isChannel, getFragmentForAlert(1))

				val rights: Int

				if (bannedRights!!.sendMessages || bannedRights!!.sendStickers || bannedRights!!.embedLinks || bannedRights!!.sendMedia || bannedRights!!.sendGifs || bannedRights!!.sendGames || bannedRights!!.sendInline) {
					rights = 1
				}
				else {
					bannedRights!!.untilDate = 0
					rights = 2
				}

				delegate?.didSetRights(rights, adminRights, bannedRights, currentRank)
			}

			TYPE_ADD_BOT -> {
				val parentActivity = parentActivity

				if (parentActivity != null) {
					val builder = AlertDialog.Builder(parentActivity)
					builder.setTitle(if (asAdmin) parentActivity.getString(R.string.AddBotAdmin) else parentActivity.getString(R.string.AddBot))

					val isChannel = ChatObject.isChannel(currentChat) && !currentChat!!.megagroup
					val chatName = if (currentChat == null) "" else currentChat!!.title

					builder.setMessage(AndroidUtilities.replaceTags(if (asAdmin) (if (isChannel) LocaleController.formatString("AddBotMessageAdminChannel", R.string.AddBotMessageAdminChannel, chatName) else LocaleController.formatString("AddBotMessageAdminGroup", R.string.AddBotMessageAdminGroup, chatName)) else LocaleController.formatString("AddMembersAlertNamesText", R.string.AddMembersAlertNamesText, UserObject.getUserName(currentUser), chatName)))
					builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

					builder.setPositiveButton(if (asAdmin) parentActivity.getString(R.string.AddAsAdmin) else parentActivity.getString(R.string.AddBot)) { _, _ ->
						setLoading(true)

						val onFinish = Runnable {
							delegate?.didSetRights(0, if (asAdmin) adminRights else null, null, currentRank)

							closingKeyboardAfterFinish = true

							val args1 = Bundle()
							args1.putBoolean("scrollToTopOnResume", true)
							args1.putLong("chat_id", currentChat!!.id)

							if (!messagesController.checkCanOpenChat(args1, this)) {
								setLoading(false)
								return@Runnable
							}

							val chatActivity = ChatActivity(args1)

							presentFragment(chatActivity, true)

							if (BulletinFactory.canShowBulletin(chatActivity)) {
								if (isAddingNew && asAdmin) {
									BulletinFactory.createAddedAsAdminBulletin(chatActivity, currentUser?.firstName).show()
								}
								else if (!isAddingNew && !initialAsAdmin && asAdmin) {
									BulletinFactory.createPromoteToAdminBulletin(chatActivity, currentUser?.firstName).show()
								}
							}
						}
						if (asAdmin || initialAsAdmin) {
							messagesController.setUserAdminRole(currentChat!!.id, currentUser, if (asAdmin) adminRights else emptyAdminRights(false), currentRank, false, this, isAddingNew, asAdmin, botHash, onFinish) {
								setLoading(false)
								true
							}
						}
						else {
							messagesController.addUserToChat(currentChat!!.id, currentUser, 0, botHash, this, true, onFinish) {
								setLoading(false)
								true
							}
						}
					}

					showDialog(builder.create())
				}

				finishFragment = false
			}
		}

		if (finishFragment) {
			finishFragment()
		}
	}

	fun setLoading(enable: Boolean) {
		doneDrawableAnimator?.cancel()

		loading = !enable

		actionBar?.backButton?.isEnabled = !enable

		doneDrawable?.let {
			doneDrawableAnimator = ValueAnimator.ofFloat(it.progress, if (enable) 1f else 0f)

			doneDrawableAnimator?.addUpdateListener { a ->
				it.progress = a.animatedValue as Float
				it.invalidateSelf()
			}

			doneDrawableAnimator?.duration = (150 * abs(it.progress - if (enable) 1 else 0)).toLong()
			doneDrawableAnimator?.start()
		}
	}

	fun setDelegate(channelRightsEditActivityDelegate: ChatRightsEditActivityDelegate?) {
		delegate = channelRightsEditActivityDelegate
	}

	private fun checkDiscard(): Boolean {
		if (currentType == TYPE_ADD_BOT) {
			return true
		}

		val changed = if (currentType == TYPE_BANNED) {
			val newBannedRights = ChatObject.getBannedRightsString(bannedRights)
			currentBannedRights != newBannedRights
		}
		else {
			initialRank != currentRank
		}

		if (changed) {
			val parentActivity = parentActivity

			if (parentActivity != null) {
				val builder = AlertDialog.Builder(parentActivity)
				builder.setTitle(parentActivity.getString(R.string.UserRestrictionsApplyChanges))

				val chat = messagesController.getChat(chatId)

				builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("UserRestrictionsApplyChangesText", R.string.UserRestrictionsApplyChangesText, chat?.title)))

				builder.setPositiveButton(parentActivity.getString(R.string.ApplyTheme)) { _, _ ->
					onDonePressed()
				}

				builder.setNegativeButton(parentActivity.getString(R.string.PassportDiscard)) { _, _ ->
					finishFragment()
				}

				showDialog(builder.create())
			}

			return false
		}

		return true
	}

	private fun setTextLeft(cell: View) {
		if (cell is HeaderCell) {
			val currentRank = currentRank

			val left = MAX_RANK_LENGTH - (currentRank?.codePointCount(0, currentRank.length) ?: 0)

			if (left <= MAX_RANK_LENGTH - MAX_RANK_LENGTH * 0.7f) {
				cell.setText2(String.format(Locale.getDefault(), "%d", left))
				val textView = cell.textView2
				textView?.textColor = if (left < 0) cell.context.getColor(R.color.purple) else cell.context.getColor(R.color.dark_gray)
			}
			else {
				cell.setText2("")
			}
		}
	}

	override fun onBackPressed(): Boolean {
		return checkDiscard()
	}

	@Suppress("SameParameterValue")
	private fun updateAsAdmin(animated: Boolean) {
		val context = context ?: return

		addBotButton?.invalidate()

		listView?.children?.forEach { child ->
			val childPosition = listView?.getChildAdapterPosition(child)

			if (child is TextCheckCell2) {
				if (!asAdmin) {
					if (childPosition == changeInfoRow && !defaultBannedRights!!.changeInfo || childPosition == pinMessagesRow && !defaultBannedRights!!.pinMessages) {
						child.isChecked = true
						child.setEnabled(value = false, animated = false)
					}
					else {
						child.isChecked = false
						child.setEnabled(childPosition == manageRow, animated)
					}
				}
				else {
					var childValue = false
					var childEnabled = false

					when (childPosition) {
						manageRow -> {
							childValue = true
							childEnabled = myAdminRights!!.addAdmins || currentChat != null && currentChat!!.creator
						}

						changeInfoRow -> {
							childValue = adminRights!!.changeInfo
							childEnabled = myAdminRights!!.changeInfo && defaultBannedRights!!.changeInfo
						}

						postMessagesRow -> {
							childValue = adminRights!!.postMessages
							childEnabled = myAdminRights!!.postMessages
						}

						editMessagesRow -> {
							childValue = adminRights!!.editMessages
							childEnabled = myAdminRights!!.editMessages
						}

						deleteMessagesRow -> {
							childValue = adminRights!!.deleteMessages
							childEnabled = myAdminRights!!.deleteMessages
						}

						banUsersRow -> {
							childValue = adminRights!!.banUsers
							childEnabled = myAdminRights!!.banUsers
						}

						addUsersRow -> {
							childValue = adminRights!!.inviteUsers
							childEnabled = myAdminRights!!.inviteUsers
						}

						pinMessagesRow -> {
							childValue = adminRights!!.pinMessages
							childEnabled = myAdminRights!!.pinMessages && defaultBannedRights!!.pinMessages
						}

						startVoiceChatRow -> {
							childValue = adminRights!!.manageCall
							childEnabled = myAdminRights!!.manageCall
						}

						addAdminsRow -> {
							childValue = adminRights!!.addAdmins
							childEnabled = myAdminRights!!.addAdmins
						}

						anonymousRow -> {
							childValue = adminRights!!.anonymous
							childEnabled = myAdminRights!!.anonymous || currentChat != null && currentChat!!.creator
						}
					}

					child.isChecked = childValue
					child.setEnabled(childEnabled, animated)
				}
			}
		}

		listViewAdapter?.notifyDataSetChanged()

		addBotButtonText?.setText(context.getString(R.string.AddBotButton) + " " + if (asAdmin) context.getString(R.string.AddBotButtonAsAdmin) else context.getString(R.string.AddBotButtonAsMember), animated, asAdmin)


		asAdminAnimator?.cancel()
		asAdminAnimator = null

		if (animated) {
			asAdminAnimator = ValueAnimator.ofFloat(asAdminT, if (asAdmin) 1f else 0f)

			asAdminAnimator?.addUpdateListener {
				asAdminT = it.animatedValue as Float
				addBotButton?.invalidate()
			}

			asAdminAnimator?.duration = (abs(asAdminT - if (asAdmin) 1f else 0f) * 200).toLong()
			asAdminAnimator?.start()
		}
		else {
			asAdminT = if (asAdmin) 1f else 0f
			addBotButton?.invalidate()
		}
	}

	interface ChatRightsEditActivityDelegate {
		fun didSetRights(rights: Int, rightsAdmin: TLChatAdminRights?, rightsBanned: TLChatBannedRights?, rank: String?)
		fun didChangeOwner(user: User)
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		private var ignoreTextChange = false

		init {
			if (currentType == TYPE_ADD_BOT) {
				setHasStableIds(true)
			}
		}

		override fun getItemId(position: Int): Long {
			return if (currentType == TYPE_ADD_BOT) {
				when (position) {
					manageRow -> 1
					changeInfoRow -> 2
					postMessagesRow -> 3
					editMessagesRow -> 4
					deleteMessagesRow -> 5
					addAdminsRow -> 6
					anonymousRow -> 7
					banUsersRow -> 8
					addUsersRow -> 9
					pinMessagesRow -> 10
					rightsShadowRow -> 11
					removeAdminRow -> 12
					removeAdminShadowRow -> 13
					cantEditInfoRow -> 14
					transferOwnerShadowRow -> 15
					transferOwnerRow -> 16
					rankHeaderRow -> 17
					rankRow -> 18
					rankInfoRow -> 19
					sendMessagesRow -> 20
					sendMediaRow -> 21
					sendStickersRow -> 22
					sendPollsRow -> 23
					embedLinksRow -> 24
					startVoiceChatRow -> 25
					untilSectionRow -> 26
					untilDateRow -> 27
					addBotButtonRow -> 28
					else -> 0
				}
			}
			else {
				super.getItemId(position)
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val currentChat = currentChat ?: return false

			val type = holder.itemViewType

			if (currentChat.creator && (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT && asAdmin) && type == VIEW_TYPE_SWITCH_CELL && holder.adapterPosition == anonymousRow) {
				return true
			}

			if (!canEdit) {
				return false
			}

			if ((currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) && type == VIEW_TYPE_SWITCH_CELL) {
				val position = holder.adapterPosition

				if (position == manageRow) {
					return myAdminRights!!.addAdmins || currentChat.creator
				}
				else {
					if (currentType == TYPE_ADD_BOT && !asAdmin) {
						return false
					}

					when (position) {
						changeInfoRow -> {
							return myAdminRights!!.changeInfo && (defaultBannedRights == null || defaultBannedRights?.changeInfo == true)
						}

						postMessagesRow -> {
							return myAdminRights!!.postMessages
						}

						editMessagesRow -> {
							return myAdminRights!!.editMessages
						}

						deleteMessagesRow -> {
							return myAdminRights!!.deleteMessages
						}

						startVoiceChatRow -> {
							return myAdminRights!!.manageCall
						}

						addAdminsRow -> {
							return myAdminRights!!.addAdmins
						}

						anonymousRow -> {
							return myAdminRights!!.anonymous
						}

						banUsersRow -> {
							return myAdminRights!!.banUsers
						}

						addUsersRow -> {
							return myAdminRights!!.inviteUsers
						}

						pinMessagesRow -> {
							return myAdminRights!!.pinMessages && (defaultBannedRights == null || defaultBannedRights?.pinMessages == true)
						}
					}
				}
			}

			return type != VIEW_TYPE_HEADER_CELL && type != VIEW_TYPE_INFO_CELL && type != VIEW_TYPE_SHADOW_CELL && type != VIEW_TYPE_ADD_BOT_CELL
		}

		override fun getItemCount(): Int {
			return rowCount
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				VIEW_TYPE_USER_CELL -> {
					view = UserCell2(mContext, 4, 0)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}

				VIEW_TYPE_INFO_CELL -> {
					view = TextInfoPrivacyCell(mContext)
					view.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.shadow)))
				}

				VIEW_TYPE_TRANSFER_CELL -> {
					view = TextSettingsCell(mContext)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}

				VIEW_TYPE_HEADER_CELL -> {
					view = HeaderCell(mContext, 21, 15, 15, true)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}

				VIEW_TYPE_SWITCH_CELL -> {
					view = TextCheckCell2(mContext)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}

				VIEW_TYPE_SHADOW_CELL -> view = ShadowSectionCell(mContext)
				VIEW_TYPE_UNTIL_DATE_CELL -> {
					view = TextDetailCell(mContext)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}

				VIEW_TYPE_ADD_BOT_CELL -> {
					val addBotButtonContainer = FrameLayout(mContext)
					addBotButtonContainer.setBackgroundColor(mContext.getColor(R.color.light_background))

					addBotButton = FrameLayout(mContext)

					addBotButtonText = AnimatedTextView(mContext, true, false, false)
					addBotButtonText?.setTypeface(Theme.TYPEFACE_BOLD)
					addBotButtonText?.setTextColor(-0x1)
					addBotButtonText?.setTextSize(AndroidUtilities.dp(14f).toFloat())
					addBotButtonText?.setGravity(Gravity.CENTER)
					addBotButtonText?.text = mContext.getString(R.string.AddBotButton) + " " + if (asAdmin) mContext.getString(R.string.AddBotButtonAsAdmin) else mContext.getString(R.string.AddBotButtonAsMember)

					addBotButton?.addView(addBotButtonText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))
					addBotButton?.background = Theme.AdaptiveRipple.filledRect(mContext.getColor(R.color.brand), 4f)

					addBotButton?.setOnClickListener {
						onDonePressed()
					}

					addBotButtonContainer.addView(addBotButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.FILL, 14f, 28f, 14f, 14f))
					addBotButtonContainer.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

					val bg = View(mContext)
					bg.setBackgroundColor(mContext.getColor(R.color.light_background))

					addBotButtonContainer.clipChildren = false
					addBotButtonContainer.clipToPadding = false
					addBotButtonContainer.addView(bg, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 800f, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL, 0f, 0f, 0f, -800f))

					view = addBotButtonContainer
				}

				VIEW_TYPE_RANK_CELL -> {
					val cell = PollEditTextCell(mContext, null)
					cell.setBackgroundColor(mContext.getColor(R.color.background))

					cell.addTextWatcher(object : TextWatcher {
						override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
							// unused
						}

						override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
							// unused
						}

						override fun afterTextChanged(s: Editable) {
							if (ignoreTextChange) {
								return
							}

							currentRank = s.toString()

							val holder = listView?.findViewHolderForAdapterPosition(rankHeaderRow)

							if (holder != null) {
								setTextLeft(holder.itemView)
							}
						}
					})

					view = cell
				}

				else -> {
					view = TextSettingsCell(mContext)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				VIEW_TYPE_USER_CELL -> {
					val userCell2 = holder.itemView as UserCell2
					var status: String? = null

					if (currentType == TYPE_ADD_BOT) {
						status = mContext.getString(R.string.Bot)
					}

					userCell2.setData(currentUser, null, status, 0)
				}

				VIEW_TYPE_INFO_CELL -> {
					val privacyCell = holder.itemView as TextInfoPrivacyCell

					if (position == cantEditInfoRow) {
						privacyCell.setText(mContext.getString(R.string.EditAdminCantEdit))
					}
					else if (position == rankInfoRow) {
						val hint = if (UserObject.isUserSelf(currentUser) && currentChat!!.creator) {
							mContext.getString(R.string.ChannelCreator)
						}
						else {
							mContext.getString(R.string.ChannelAdmin)
						}

						privacyCell.setText(LocaleController.formatString("EditAdminRankInfo", R.string.EditAdminRankInfo, hint))
					}
				}

				VIEW_TYPE_TRANSFER_CELL -> {
					val actionCell = holder.itemView as TextSettingsCell

					if (position == removeAdminRow) {
						actionCell.setTextColor(mContext.getColor(R.color.purple))

						if (currentType == TYPE_ADMIN) {
							actionCell.setText(mContext.getString(R.string.EditAdminRemoveAdmin), false)
						}
						else if (currentType == TYPE_BANNED) {
							actionCell.setText(mContext.getString(R.string.UserRestrictionsBlock), false)
						}
					}
					else if (position == transferOwnerRow) {
						actionCell.setTextColor(mContext.getColor(R.color.text))

						if (isChannel) {
							actionCell.setText(mContext.getString(R.string.EditAdminChannelTransfer), false)
						}
						else {
							actionCell.setText(mContext.getString(R.string.EditAdminGroupTransfer), false)
						}
					}
				}

				VIEW_TYPE_HEADER_CELL -> {
					val headerCell = holder.itemView as HeaderCell

					if (position == 2) {
						if (currentType == TYPE_ADD_BOT || currentUser != null && currentUser.bot) {
							headerCell.setText(mContext.getString(R.string.BotRestrictionsCanDo))
						}
						else if (currentType == TYPE_ADMIN) {
							headerCell.setText(mContext.getString(R.string.EditAdminWhatCanDo))
						}
						else if (currentType == TYPE_BANNED) {
							headerCell.setText(mContext.getString(R.string.UserRestrictionsCanDo))
						}
					}
					else if (position == rankHeaderRow) {
						headerCell.setText(mContext.getString(R.string.EditAdminRank))
					}
				}

				VIEW_TYPE_SWITCH_CELL -> {
					val checkCell = holder.itemView as TextCheckCell2
					val asAdminValue = currentType != TYPE_ADD_BOT || asAdmin
					val isCreator = currentChat != null && currentChat!!.creator

					if (position == manageRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.ManageGroup), asAdmin, true)
						checkCell.setIcon(if (myAdminRights!!.addAdmins || isCreator) 0 else R.drawable.permission_locked)
					}
					else if (position == changeInfoRow) {
						if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
							if (isChannel) {
								checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminChangeChannelInfo), asAdminValue && adminRights!!.changeInfo || !defaultBannedRights!!.changeInfo, true)
							}
							else {
								checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminChangeGroupInfo), asAdminValue && adminRights!!.changeInfo || !defaultBannedRights!!.changeInfo, true)
							}

							if (currentType == TYPE_ADD_BOT) {
								checkCell.setIcon(if (myAdminRights!!.changeInfo || isCreator) 0 else R.drawable.permission_locked)
							}
						}
						else if (currentType == TYPE_BANNED) {
							checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsChangeInfo), !bannedRights!!.changeInfo && !defaultBannedRights!!.changeInfo, false)
							checkCell.setIcon(if (defaultBannedRights!!.changeInfo) R.drawable.permission_locked else 0)
						}
					}
					else if (position == postMessagesRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminPostMessages), asAdminValue && adminRights!!.postMessages, true)

						if (currentType == TYPE_ADD_BOT) {
							checkCell.setIcon(if (myAdminRights!!.postMessages || isCreator) 0 else R.drawable.permission_locked)
						}
					}
					else if (position == editMessagesRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminEditMessages), asAdminValue && adminRights!!.editMessages, true)

						if (currentType == TYPE_ADD_BOT) {
							checkCell.setIcon(if (myAdminRights!!.editMessages || isCreator) 0 else R.drawable.permission_locked)
						}
					}
					else if (position == deleteMessagesRow) {
						if (isChannel) {
							checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminDeleteMessages), asAdminValue && adminRights!!.deleteMessages, true)
						}
						else {
							checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminGroupDeleteMessages), asAdminValue && adminRights!!.deleteMessages, true)
						}

						if (currentType == TYPE_ADD_BOT) {
							checkCell.setIcon(if (myAdminRights!!.deleteMessages || isCreator) 0 else R.drawable.permission_locked)
						}
					}
					else if (position == addAdminsRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminAddAdmins), asAdminValue && adminRights!!.addAdmins, anonymousRow != -1)

						if (currentType == TYPE_ADD_BOT) {
							checkCell.setIcon(if (myAdminRights!!.addAdmins || isCreator) 0 else R.drawable.permission_locked)
						}
					}
					else if (position == anonymousRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminSendAnonymously), asAdminValue && adminRights!!.anonymous, false)

						if (currentType == TYPE_ADD_BOT) {
							checkCell.setIcon(if (myAdminRights!!.anonymous || isCreator) 0 else R.drawable.permission_locked)
						}
					}
					else if (position == banUsersRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminBanUsers), asAdminValue && adminRights!!.banUsers, true)

						if (currentType == TYPE_ADD_BOT) {
							checkCell.setIcon(if (myAdminRights!!.banUsers || isCreator) 0 else R.drawable.permission_locked)
						}
					}
					else if (position == startVoiceChatRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.StartVoipChatPermission), asAdminValue && adminRights!!.manageCall, true)

						if (currentType == TYPE_ADD_BOT) {
							checkCell.setIcon(if (myAdminRights!!.manageCall || isCreator) 0 else R.drawable.permission_locked)
						}
					}
					else if (position == addUsersRow) {
						if (currentType == TYPE_ADMIN) {
							if (ChatObject.isActionBannedByDefault(currentChat, ChatObject.ACTION_INVITE)) {
								checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminAddUsers), adminRights!!.inviteUsers, true)
							}
							else {
								checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminAddUsersViaLink), adminRights!!.inviteUsers, true)
							}
						}
						else if (currentType == TYPE_BANNED) {
							checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsInviteUsers), !bannedRights!!.inviteUsers && !defaultBannedRights!!.inviteUsers, true)
							checkCell.setIcon(if (defaultBannedRights!!.inviteUsers) R.drawable.permission_locked else 0)
						}
						else if (currentType == TYPE_ADD_BOT) {
							checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminAddUsersViaLink), asAdminValue && adminRights!!.inviteUsers, true)
							checkCell.setIcon(if (myAdminRights!!.inviteUsers || isCreator) 0 else R.drawable.permission_locked)
						}
					}
					else if (position == pinMessagesRow) {
						if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
							checkCell.setTextAndCheck(mContext.getString(R.string.EditAdminPinMessages), asAdminValue && adminRights!!.pinMessages || !defaultBannedRights!!.pinMessages, true)

							if (currentType == TYPE_ADD_BOT) {
								checkCell.setIcon(if (myAdminRights!!.pinMessages || isCreator) 0 else R.drawable.permission_locked)
							}
						}
						else if (currentType == TYPE_BANNED) {
							checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsPinMessages), !bannedRights!!.pinMessages && !defaultBannedRights!!.pinMessages, true)
							checkCell.setIcon(if (defaultBannedRights!!.pinMessages) R.drawable.permission_locked else 0)
						}
					}
					else if (position == sendMessagesRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsSend), !bannedRights!!.sendMessages && !defaultBannedRights!!.sendMessages, true)
						checkCell.setIcon(if (defaultBannedRights!!.sendMessages) R.drawable.permission_locked else 0)
					}
					else if (position == sendMediaRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsSendMedia), !bannedRights!!.sendMedia && !defaultBannedRights!!.sendMedia, true)
						checkCell.setIcon(if (defaultBannedRights!!.sendMedia) R.drawable.permission_locked else 0)
					}
					else if (position == sendStickersRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsSendStickers), !bannedRights!!.sendStickers && !defaultBannedRights!!.sendStickers, true)
						checkCell.setIcon(if (defaultBannedRights!!.sendStickers) R.drawable.permission_locked else 0)
					}
					else if (position == embedLinksRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsEmbedLinks), !bannedRights!!.embedLinks && !defaultBannedRights!!.embedLinks, true)
						checkCell.setIcon(if (defaultBannedRights!!.embedLinks) R.drawable.permission_locked else 0)
					}
					else if (position == sendPollsRow) {
						checkCell.setTextAndCheck(mContext.getString(R.string.UserRestrictionsSendPolls), !bannedRights!!.sendPolls && !defaultBannedRights!!.sendPolls, true)
						checkCell.setIcon(if (defaultBannedRights!!.sendPolls) R.drawable.permission_locked else 0)
					}

					if (currentType == TYPE_ADD_BOT) {
//                        checkCell.setEnabled((asAdmin || position == manageRow) && !checkCell.hasIcon(), false);
					}
					else {
						if (position == sendMediaRow || position == sendStickersRow || position == embedLinksRow || position == sendPollsRow) {
							checkCell.isEnabled = !bannedRights!!.sendMessages && !bannedRights!!.viewMessages && !defaultBannedRights!!.sendMessages && !defaultBannedRights!!.viewMessages
						}
						else if (position == sendMessagesRow) {
							checkCell.isEnabled = !bannedRights!!.viewMessages && !defaultBannedRights!!.viewMessages
						}
					}
				}

				VIEW_TYPE_SHADOW_CELL -> {
					val shadowCell = holder.itemView as ShadowSectionCell

					if (currentType == TYPE_ADD_BOT && (position == rightsShadowRow || position == rankInfoRow)) {
						shadowCell.alpha = asAdminT
					}
					else {
						shadowCell.alpha = 1f
					}

					when (position) {
						rightsShadowRow -> {
							shadowCell.background = Theme.getThemedDrawable(mContext, if (removeAdminRow == -1 && rankRow == -1) R.drawable.greydivider_bottom else R.drawable.greydivider, mContext.getColor(R.color.shadow))
						}

						removeAdminShadowRow -> {
							shadowCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.shadow))
						}

						rankInfoRow -> {
							shadowCell.background = Theme.getThemedDrawable(mContext, if (canEdit) R.drawable.greydivider else R.drawable.greydivider_bottom, mContext.getColor(R.color.shadow))
						}

						else -> {
							shadowCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, mContext.getColor(R.color.shadow))
						}
					}
				}

				VIEW_TYPE_UNTIL_DATE_CELL -> {
					val detailCell = holder.itemView as TextDetailCell

					if (position == untilDateRow) {
						val value = if (bannedRights!!.untilDate == 0 || abs(bannedRights!!.untilDate - System.currentTimeMillis() / 1000) > 10 * 365 * 24 * 60 * 60) {
							mContext.getString(R.string.UserRestrictionsUntilForever)
						}
						else {
							LocaleController.formatDateForBan(bannedRights!!.untilDate.toLong())
						}

						detailCell.setTextAndValue(mContext.getString(R.string.UserRestrictionsDuration), value, false)
					}
				}

				VIEW_TYPE_RANK_CELL -> {
					val textCell = holder.itemView as PollEditTextCell

					val hint = if (UserObject.isUserSelf(currentUser) && currentChat!!.creator) {
						mContext.getString(R.string.ChannelCreator)
					}
					else {
						mContext.getString(R.string.ChannelAdmin)
					}

					ignoreTextChange = true

					textCell.textView.isEnabled = canEdit || currentChat!!.creator
					textCell.textView.isSingleLine = true
					textCell.textView.imeOptions = EditorInfo.IME_ACTION_DONE
					textCell.setTextAndHint(currentRank, hint, false)

					ignoreTextChange = false
				}
			}
		}

		override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
			if (holder.adapterPosition == rankHeaderRow) {
				setTextLeft(holder.itemView)
			}
		}

		override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
			if (holder.adapterPosition == rankRow && parentActivity != null) {
				AndroidUtilities.hideKeyboard(parentActivity!!.currentFocus)
			}
		}

		override fun getItemViewType(position: Int): Int {
			return when (position) {
				0 -> {
					VIEW_TYPE_USER_CELL
				}

				1, rightsShadowRow, removeAdminShadowRow, untilSectionRow, transferOwnerShadowRow -> {
					VIEW_TYPE_SHADOW_CELL
				}

				2, rankHeaderRow -> {
					VIEW_TYPE_HEADER_CELL
				}

				changeInfoRow, postMessagesRow, editMessagesRow, deleteMessagesRow, addAdminsRow, banUsersRow, addUsersRow, pinMessagesRow, sendMessagesRow, sendMediaRow, sendStickersRow, embedLinksRow, sendPollsRow, anonymousRow, startVoiceChatRow, manageRow -> {
					VIEW_TYPE_SWITCH_CELL
				}

				cantEditInfoRow, rankInfoRow -> {
					VIEW_TYPE_INFO_CELL
				}

				untilDateRow -> {
					VIEW_TYPE_UNTIL_DATE_CELL
				}

				rankRow -> {
					VIEW_TYPE_RANK_CELL
				}

				addBotButtonRow -> {
					VIEW_TYPE_ADD_BOT_CELL
				}

				else -> {
					VIEW_TYPE_TRANSFER_CELL
				}
			}
		}
	}

	companion object {
		const val TYPE_ADMIN = 0
		const val TYPE_BANNED = 1
		const val TYPE_ADD_BOT = 2
		private const val DONE_BUTTON = 1
		private const val MAX_RANK_LENGTH = 16

		private const val VIEW_TYPE_USER_CELL = 0
		private const val VIEW_TYPE_INFO_CELL = 1
		private const val VIEW_TYPE_TRANSFER_CELL = 2
		private const val VIEW_TYPE_HEADER_CELL = 3
		private const val VIEW_TYPE_SWITCH_CELL = 4
		private const val VIEW_TYPE_SHADOW_CELL = 5
		private const val VIEW_TYPE_UNTIL_DATE_CELL = 6
		private const val VIEW_TYPE_RANK_CELL = 7
		private const val VIEW_TYPE_ADD_BOT_CELL = 8

		@JvmStatic
		fun emptyAdminRights(value: Boolean): TLChatAdminRights {
			val adminRights = TLChatAdminRights()
			adminRights.manageCall = value
			adminRights.addAdmins = adminRights.manageCall
			adminRights.pinMessages = adminRights.addAdmins
			adminRights.inviteUsers = adminRights.pinMessages
			adminRights.banUsers = adminRights.inviteUsers
			adminRights.deleteMessages = adminRights.banUsers
			adminRights.editMessages = adminRights.deleteMessages
			adminRights.postMessages = adminRights.editMessages
			adminRights.changeInfo = adminRights.postMessages
			return adminRights
		}
	}
}
