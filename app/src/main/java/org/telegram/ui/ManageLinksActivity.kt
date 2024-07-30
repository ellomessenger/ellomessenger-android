/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ContactsController
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLog
import org.telegram.messenger.FileLog.e
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.ExportedChatInvite
import org.telegram.tgnet.TLRPC.TL_chatAdminWithInvites
import org.telegram.tgnet.TLRPC.TL_chatInviteExported
import org.telegram.tgnet.TLRPC.TL_messages_chatAdminsWithInvites
import org.telegram.tgnet.TLRPC.TL_messages_deleteExportedChatInvite
import org.telegram.tgnet.TLRPC.TL_messages_deleteRevokedExportedChatInvites
import org.telegram.tgnet.TLRPC.TL_messages_editExportedChatInvite
import org.telegram.tgnet.TLRPC.TL_messages_exportChatInvite
import org.telegram.tgnet.TLRPC.TL_messages_exportedChatInvite
import org.telegram.tgnet.TLRPC.TL_messages_exportedChatInviteReplaced
import org.telegram.tgnet.TLRPC.TL_messages_exportedChatInvites
import org.telegram.tgnet.TLRPC.TL_messages_getAdminsWithInvites
import org.telegram.tgnet.TLRPC.TL_messages_getExportedChatInvites
import org.telegram.tgnet.tlrpc.UserFull
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.CreationTextCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.ManageChatUserCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.DotDividerSpan
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.InviteLinkBottomSheet
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LinkActionView
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.RecyclerItemsEnterAnimator
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.Components.TimerParticles
import java.util.Locale
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
class ManageLinksActivity @JvmOverloads constructor(private val peerId: Long, adminId: Long, private val invitesCount: Int, private val isUser: Boolean = false) : BaseFragment() {
	private val adminId: Long
	private val admins = mutableListOf<TL_chatAdminWithInvites>()
	private val invites = mutableListOf<TL_chatInviteExported>()
	private val revokedInvites = mutableListOf<TL_chatInviteExported>()
	private val users = HashMap<Long, User>()
	private var adminsDividerRow = 0
	private var adminsEndRow = 0
	private var adminsHeaderRow = 0
	private var adminsLoaded = false
	private var adminsStartRow = 0
	private var canEdit = false
	private var createLinkHelpRow = 0
	private var createNewLinkRow = 0
	private var creatorDividerRow = 0
	private var creatorRow = 0
	private var currentChat: Chat? = null
	private var currentUser: User? = null
	private var deletingRevokedLinks = false
	private var dividerRow = 0
	private var helpRow = 0
	private var info: ChatFull? = null
	private var invite: TL_chatInviteExported? = null
	private var inviteLinkBottomSheet: InviteLinkBottomSheet? = null
	private var isChannel = false
	private var isOpened = false
	private var isPublic = false
	private var lastDivider = 0
	private var linksEndRow = 0
	private var linksHeaderRow = 0
	private var linksLoadingRow = 0
	private var linksStartRow = 0
	private var listView: RecyclerListView? = null
	private var listViewAdapter: ListAdapter? = null
	private var loadAdmins = false
	private var loadRevoked = false
	private var permanentLinkHeaderRow = 0
	private var permanentLinkRow = 0
	private var recyclerItemsEnterAnimator: RecyclerItemsEnterAnimator? = null
	private var revokeAllDivider = 0
	private var revokeAllRow = 0
	private var revokedDivider = 0
	private var revokedHeader = 0
	private var revokedLinksEndRow = 0
	private var revokedLinksStartRow = 0
	private var rowCount = 0
	private var userInfo: UserFull? = null
	var hasMore = false
	var linkIcon: Drawable? = null
	var linkIconRevoked: Drawable? = null
	var linksLoading = false
	var timeDiff: Long = 0

	var updateTimerRunnable = object : Runnable {
		override fun run() {
			val listView = listView ?: return

			for (i in 0 until listView.childCount) {
				val child = listView.getChildAt(i)

				if (child is LinkCell) {
					if (child.timerRunning) {
						child.setLink(child.invite, child.position)
					}
				}
			}

			AndroidUtilities.runOnUIThread(this, 500)
		}
	}

	private class EmptyView(context: Context) : LinearLayout(context) {
		private val stickerView: RLottieImageView

		init {
			setPadding(0, AndroidUtilities.dp(12f), 0, AndroidUtilities.dp(12f))

			orientation = VERTICAL

			stickerView = RLottieImageView(context)
			stickerView.setAutoRepeat(true)

			addView(stickerView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 2, 0, 0))
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()

			stickerView.setAnimation(R.raw.panda_invite, 160, 160)
			stickerView.playAnimation()
		}
	}

	constructor(userId: Long, adminId: Long) : this(userId, adminId, 0, true)

	private fun loadLinks(notify: Boolean) {
		if (loadAdmins && !adminsLoaded) {
			linksLoading = true

			val req = TL_messages_getAdminsWithInvites()
			req.peer = messagesController.getInputPeer(-peerId)

			val reqId = connectionsManager.sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					notificationCenter.doOnIdle {
						linksLoading = false

						if (error == null && response is TL_messages_chatAdminsWithInvites) {
							response.admins?.forEach {
								if (it.admin_id != accountInstance.userConfig.clientUserId) {
									admins.add(it)
								}
							}

							response.users?.forEach {
								users[it.id] = it
							}
						}

						val oldRowsCount = rowCount

						adminsLoaded = true
						hasMore = false

						if (admins.size > 0) {
							if (!isPaused && isOpened) {
								recyclerItemsEnterAnimator?.showItemsAnimated(oldRowsCount + 1)
							}
						}

						if (!hasMore || invites.size + revokedInvites.size + admins.size >= 5) {
							resumeDelayedFragmentAnimation()
						}

						if (!hasMore && !loadRevoked) {
							hasMore = true
							loadRevoked = true
							loadLinks(false)
						}

						updateRows(true)
					}
				}
			}

			connectionsManager.bindRequestToGuid(reqId, getClassGuid())
		}
		else {
			val req = TL_messages_getExportedChatInvites()
			req.peer = messagesController.getInputPeer(if (isUser) peerId else -peerId)

			if (adminId == userConfig.getClientUserId()) {
				req.admin_id = messagesController.getInputUser(userConfig.getCurrentUser())
			}
			else {
				req.admin_id = messagesController.getInputUser(adminId)
			}

			val revoked = loadRevoked

			if (loadRevoked) {
				req.revoked = true

				if (revokedInvites.isNotEmpty()) {
					req.flags = req.flags or 4
					req.offset_link = revokedInvites.last().link
					req.offset_date = revokedInvites.last().date
				}
			}
			else {
				if (invites.isNotEmpty()) {
					req.flags = req.flags or 4
					req.offset_link = invites.last().link
					req.offset_date = invites.last().date
				}
			}

			linksLoading = true

			val inviteFinal = if (isPublic) null else invite

			val reqId = connectionsManager.sendRequest(req) { response, error ->
				var permanentLink: TL_chatInviteExported? = null

				if (error == null && response is TL_messages_exportedChatInvites) {
					if (!response.invites.isNullOrEmpty() && inviteFinal != null) {
						for (i in response.invites.indices) {
							if ((response.invites[i] as TL_chatInviteExported).link == inviteFinal.link) {
								permanentLink = response.invites.removeAt(i) as TL_chatInviteExported
								break
							}
						}
					}
				}

				val finalPermanentLink = permanentLink

				AndroidUtilities.runOnUIThread {
					notificationCenter.doOnIdle {
						val callback = saveListState()

						linksLoading = false
						hasMore = false

						if (finalPermanentLink != null) {
							invite = finalPermanentLink
							info?.exported_invite = finalPermanentLink
						}

						var updateByDiffUtils = false

						if (error == null && response is TL_messages_exportedChatInvites) {
							if (revoked) {
								for (i in response.invites.indices) {
									val `in` = response.invites[i] as TL_chatInviteExported
									fixDate(`in`)
									revokedInvites.add(`in`)
								}
							}
							else {
								if (adminId != accountInstance.userConfig.clientUserId && this.invites.size == 0 && response.invites.size > 0) {
									invite = response.invites[0] as TL_chatInviteExported
									response.invites.removeAt(0)
								}
								for (i in response.invites.indices) {
									val `in` = response.invites[i] as TL_chatInviteExported
									fixDate(`in`)
									this.invites.add(`in`)
								}
							}

							for (i in response.users.indices) {
								users[response.users[i].id] = response.users[i]
							}

							val oldRowsCount = rowCount

							hasMore = if (response.invites.size == 0) {
								false
							}
							else if (revoked) {
								revokedInvites.size + 1 < response.count
							}
							else {
								this.invites.size + 1 < response.count
							}

							if (response.invites.size > 0 && isOpened) {
								if (!isPaused) {
									recyclerItemsEnterAnimator?.showItemsAnimated(oldRowsCount + 1)
								}
							}
							else {
								updateByDiffUtils = true
							}

							if (info != null && !revoked) {
								info?.invitesCount = response.count
								messagesStorage.saveChatLinksCount(peerId, info?.invitesCount ?: 0)
							}
						}
						else {
							hasMore = false
						}

						var loadNext = false

						if (!hasMore && !loadRevoked && adminId == accountInstance.userConfig.clientUserId) {
							hasMore = true
							loadAdmins = true
							loadNext = true
						}
						else if (!hasMore && !loadRevoked) {
							hasMore = true
							loadRevoked = true
							loadNext = true
						}

						if (!hasMore || invites.size + revokedInvites.size + admins.size >= 5) {
							resumeDelayedFragmentAnimation()
						}

						if (loadNext) {
							loadLinks(false)
						}

						if (updateByDiffUtils && listViewAdapter != null && (listView?.childCount ?: 0) > 0) {
							updateRecyclerViewAnimated(callback)
						}
						else {
							updateRows(true)
						}
					}
				}
			}

			connectionsManager.bindRequestToGuid(reqId, getClassGuid())
		}

		if (notify) {
			updateRows(true)
		}
	}

	private fun updateRows(notify: Boolean) {
		if (!isUser) {
			currentChat = MessagesController.getInstance(currentAccount).getChat(peerId)

			if (currentChat == null) {
				return
			}
		}
		else {
			currentUser = MessagesController.getInstance(currentAccount).getUser(peerId)

			if (currentUser == null) {
				return
			}
		}

		creatorRow = -1
		creatorDividerRow = -1
		linksStartRow = -1
		linksEndRow = -1
		linksLoadingRow = -1
		revokedLinksStartRow = -1
		revokedLinksEndRow = -1
		revokedHeader = -1
		revokedDivider = -1
		lastDivider = -1
		revokeAllRow = -1
		revokeAllDivider = -1
		createLinkHelpRow = -1
		helpRow = -1
		createNewLinkRow = -1
		adminsEndRow = -1
		adminsStartRow = -1
		adminsDividerRow = -1
		adminsHeaderRow = -1
		linksHeaderRow = -1
		dividerRow = -1
		rowCount = 0

		val otherAdmin = !isUser && adminId != accountInstance.userConfig.clientUserId

		if (otherAdmin) {
			creatorRow = rowCount++
			creatorDividerRow = rowCount++
		}
		else {
			helpRow = rowCount++
		}

		permanentLinkHeaderRow = rowCount++
		permanentLinkRow = rowCount++

		if (!otherAdmin) {
			dividerRow = rowCount++
			createNewLinkRow = rowCount++
		}
		else if (invites.isNotEmpty()) {
			dividerRow = rowCount++
			linksHeaderRow = rowCount++
		}

		if (invites.isNotEmpty()) {
			linksStartRow = rowCount
			rowCount += invites.size
			linksEndRow = rowCount
		}

		if (!otherAdmin && invites.isEmpty() && createNewLinkRow >= 0 && (!linksLoading || loadAdmins || loadRevoked)) {
			createLinkHelpRow = rowCount++
		}

		if (!otherAdmin && admins.size > 0) {
			if ((invites.isNotEmpty() || createNewLinkRow >= 0) && createLinkHelpRow == -1) {
				adminsDividerRow = rowCount++
			}

			adminsHeaderRow = rowCount++
			adminsStartRow = rowCount
			rowCount += admins.size
			adminsEndRow = rowCount
		}

		if (revokedInvites.isNotEmpty()) {
			if (adminsStartRow >= 0) {
				revokedDivider = rowCount++
			}
			else if ((invites.isNotEmpty() || createNewLinkRow >= 0) && createLinkHelpRow == -1) {
				revokedDivider = rowCount++
			}
			else if (otherAdmin && linksStartRow == -1) {
				revokedDivider = rowCount++
			}

			revokedHeader = rowCount++
			revokedLinksStartRow = rowCount
			rowCount += revokedInvites.size
			revokedLinksEndRow = rowCount
			revokeAllDivider = rowCount++
			revokeAllRow = rowCount++
		}

		if (!loadAdmins && !loadRevoked && (linksLoading || hasMore) && !otherAdmin) {
			linksLoadingRow = rowCount++
		}

		if (invites.isNotEmpty() || revokedInvites.isNotEmpty()) {
			lastDivider = rowCount++
		}

		if (notify) {
			listViewAdapter?.notifyDataSetChanged()
		}
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)
		actionBar?.setTitle(context.getString(R.string.InviteLinks))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		fragmentView = object : FrameLayout(context) {
			override fun onAttachedToWindow() {
				super.onAttachedToWindow()
				AndroidUtilities.runOnUIThread(updateTimerRunnable, 500)
			}

			override fun onDetachedFromWindow() {
				super.onDetachedFromWindow()
				AndroidUtilities.cancelRunOnUIThread(updateTimerRunnable)
			}
		}

		fragmentView?.setBackgroundResource(R.color.light_background)

		val frameLayout = fragmentView as FrameLayout

		listView = RecyclerListView(context)

		val layoutManager: LinearLayoutManager = object : LinearLayoutManager(context, VERTICAL, false) {
			override fun supportsPredictiveItemAnimations(): Boolean {
				return false
			}
		}

		listView?.layoutManager = layoutManager
		listView?.adapter = ListAdapter(context).also { listViewAdapter = it }

		listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				super.onScrolled(recyclerView, dx, dy)

				if (hasMore && !linksLoading) {
					val lastPosition = layoutManager.findLastVisibleItemPosition()

					if (rowCount - lastPosition < 10) {
						loadLinks(true)
					}
				}
			}
		})

		recyclerItemsEnterAnimator = RecyclerItemsEnterAnimator(listView, false)

		val defaultItemAnimator = DefaultItemAnimator()
		defaultItemAnimator.setDelayAnimations(false)
		defaultItemAnimator.supportsChangeAnimations = false

		listView?.itemAnimator = defaultItemAnimator

		listView?.verticalScrollbarPosition = if (LocaleController.isRTL) View.SCROLLBAR_POSITION_LEFT else View.SCROLLBAR_POSITION_RIGHT

		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView?.setOnItemClickListener { _, position ->
			when (position) {
				creatorRow -> {
					invite?.admin_id?.let { users[it] }?.let {
						val bundle = Bundle()
						bundle.putLong("user_id", it.id)
						MessagesController.getInstance(UserConfig.selectedAccount).putUser(it, false)
						val profileActivity = ProfileActivity(bundle)
						presentFragment(profileActivity)
					}
				}

				createNewLinkRow -> {
					val linkEditActivity = LinkEditActivity(LinkEditActivity.CREATE_TYPE, if (isUser) -peerId else peerId, isUser)
					linkEditActivity.setCallback(linkEditActivityCallback)
					presentFragment(linkEditActivity)
				}

				in linksStartRow until linksEndRow -> {
					val invite = invites[position - linksStartRow]

					inviteLinkBottomSheet = if (isUser) {
						InviteLinkBottomSheet(context, invite, info, users, this, peerId, false, isChannel, true, userInfo)
					}
					else {
						InviteLinkBottomSheet(context, invite, info, users, this, peerId, false, isChannel)
					}

					inviteLinkBottomSheet?.setCanEdit(canEdit)
					inviteLinkBottomSheet?.show()
				}

				in revokedLinksStartRow until revokedLinksEndRow -> {
					val invite = revokedInvites[position - revokedLinksStartRow]

					inviteLinkBottomSheet = if (isUser) {
						InviteLinkBottomSheet(context, invite, info, users, this, peerId, false, isChannel, true, userInfo)
					}
					else {
						InviteLinkBottomSheet(context, invite, info, users, this, peerId, false, isChannel)
					}

					inviteLinkBottomSheet?.show()
				}

				revokeAllRow -> {
					if (deletingRevokedLinks) {
						return@setOnItemClickListener
					}

					val builder = AlertDialog.Builder(context)
					builder.setTitle(context.getString(R.string.DeleteAllRevokedLinks))
					builder.setMessage(context.getString(R.string.DeleteAllRevokedLinkHelp))

					builder.setPositiveButton(context.getString(R.string.Delete)) { _, _ ->
						val req = TL_messages_deleteRevokedExportedChatInvites()
						req.peer = messagesController.getInputPeer(-peerId)

						if (adminId == userConfig.getClientUserId()) {
							req.admin_id = messagesController.getInputUser(userConfig.getCurrentUser())
						}
						else {
							req.admin_id = messagesController.getInputUser(adminId)
						}

						deletingRevokedLinks = true

						connectionsManager.sendRequest(req) { _, error ->
							AndroidUtilities.runOnUIThread {
								deletingRevokedLinks = false

								if (error == null) {
									val callback = saveListState()
									revokedInvites.clear()
									updateRecyclerViewAnimated(callback)
								}
							}
						}
					}

					builder.setNegativeButton(context.getString(R.string.Cancel), null)

					showDialog(builder.create())
				}

				in adminsStartRow until adminsEndRow -> {
					val p = position - adminsStartRow
					val admin = admins[p]

					if (users.containsKey(admin.admin_id)) {
						messagesController.putUser(users[admin.admin_id], false)
					}

					val fragment = ManageLinksActivity(peerId, admin.admin_id, admin.invites_count)

					fragment.setInfo(info, null)

					presentFragment(fragment)
				}
			}
		}

		listView?.setOnItemLongClickListener { view, position ->
			if (position in linksStartRow until linksEndRow || position in revokedLinksStartRow until revokedLinksEndRow) {
				val cell = view as LinkCell
				cell.optionsView.callOnClick()
				view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
				return@setOnItemLongClickListener true
			}

			false
		}

		linkIcon = ContextCompat.getDrawable(context, R.drawable.icon_msg_link)

		linkIconRevoked = ContextCompat.getDrawable(context, R.drawable.icon_msg_link)

		linkIcon?.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY)

		updateRows(true)

		timeDiff = connectionsManager.currentTime - System.currentTimeMillis() / 1000L

		return fragmentView
	}

	fun setInfo(chatFull: ChatFull?, invite: ExportedChatInvite?) {
		info = chatFull
		this.invite = invite as TL_chatInviteExported?
		isPublic = !currentChat?.username.isNullOrEmpty()
		loadLinks(true)
	}

	fun setInfo(userFull: UserFull, invite: ExportedChatInvite?) {
		this.invite = invite as TL_chatInviteExported?
		userInfo = userFull
		isPublic = userFull.is_public
		loadLinks(true)
	}

	override fun onResume() {
		super.onResume()
		listViewAdapter?.notifyDataSetChanged()
	}

	inner class HintInnerCell(context: Context) : FrameLayout(context) {
		init {
			val emptyView = EmptyView(context)

			addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0f, 10f, 0f, 0f))

			val messageTextView = TextView(context)
			messageTextView.setTextColor(context.getColor(R.color.text))
			messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			messageTextView.gravity = Gravity.CENTER
			messageTextView.text = if (isUser) context.getString(R.string.PrimaryLinkHelpUser) else if (isChannel) context.getString(R.string.PrimaryLinkHelpChannel) else context.getString(R.string.PrimaryLinkHelp)

			addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.LEFT, 52f, 203f, 52f, 18f))
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec)
		}
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val position = holder.adapterPosition

			if (creatorRow == position) {
				return true
			}
			else if (createNewLinkRow == position) {
				return true
			}
			else if (position in linksStartRow until linksEndRow) {
				return true
			}
			else if (position in revokedLinksStartRow until revokedLinksEndRow) {
				return true
			}
			else if (position == revokeAllRow) {
				return true
			}
			else if (position in adminsStartRow until adminsEndRow) {
				return true
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
					view = HintInnerCell(mContext)
					view.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.background))
				}

				1 -> {
					view = HeaderCell(mContext, 23)
					view.setBackgroundResource(R.color.background)
				}

				2 -> {
					val linkActionView = LinkActionView(mContext, this@ManageLinksActivity, null, true)
					linkActionView.permanent = true

					linkActionView.delegate = object : LinkActionView.Delegate {
						override fun showQr() {
							val args = Bundle()

							if (isUser) {
								args.putLong(QrFragment.USER_ID, peerId)
							}
							else {
								args.putLong(QrFragment.CHAT_ID, peerId)
							}

							args.putBoolean(QrFragment.IS_PUBLIC, isPublic)

							invite?.link?.let {
								args.putString(QrFragment.LINK, it)
							}

							presentFragment(QrFragment(args))
						}

						override fun removeLink() {
							// unused
						}

						override fun editLink() {
							// unused
						}

						override fun revokeLink() {
							revokePermanent()
						}

						override fun showUsersForPermanentLink() {
							inviteLinkBottomSheet = InviteLinkBottomSheet(linkActionView.context, invite, info, users, this@ManageLinksActivity, peerId, true, isChannel)
							inviteLinkBottomSheet?.show()
						}
					}

					view = linkActionView

					view.setBackgroundResource(R.color.background)
				}

				3 -> {
					view = CreationTextCell(mContext)
					view.setBackgroundResource(R.color.background)
				}

				4 -> {
					view = ShadowSectionCell(mContext)
				}

				5 -> {
					view = LinkCell(mContext)
				}

				6 -> {
					val flickerLoadingView = FlickerLoadingView(mContext)
					flickerLoadingView.setIsSingleCell(true)
					flickerLoadingView.setViewType(FlickerLoadingView.INVITE_LINKS_TYPE)
					flickerLoadingView.showDate(false)

					view = flickerLoadingView
					view.setBackgroundResource(R.color.background)
				}

				7 -> {
					view = ShadowSectionCell(mContext)
					view.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.shadow)))
				}

				8 -> {
					val revokeAll = TextSettingsCell(mContext)
					revokeAll.setBackgroundColor(ResourcesCompat.getColor(revokeAll.resources, R.color.background, null))
					revokeAll.setText(mContext.getString(R.string.DeleteAllRevokedLinks), false)
					revokeAll.setTextColor(ResourcesCompat.getColor(revokeAll.resources, R.color.purple, null))

					view = revokeAll
				}

				9 -> {
					val cell = TextInfoPrivacyCell(mContext)
					cell.setText(mContext.getString(R.string.CreateNewLinkHelp))
					cell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.shadow))
					view = cell
				}

				10 -> {
					val userCell = ManageChatUserCell(mContext, 8, 6, false)
					userCell.setBackgroundResource(R.color.background)
					view = userCell
				}

				else -> {
					view = HintInnerCell(mContext)
					view.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, mContext.getColor(R.color.background))
				}
			}

			view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				2 -> {
					val linkActionView = holder.itemView as LinkActionView
					linkActionView.canEdit = adminId == accountInstance.userConfig.clientUserId

					if (isPublic && (isUser || adminId == accountInstance.userConfig.clientUserId)) {
						if (info != null || isUser && userInfo != null) {
							linkActionView.setLink(String.format(Locale.getDefault(), "https://%s/", ApplicationLoader.applicationContext.getString(R.string.domain)) + if (isUser) currentUser?.username else currentChat?.username)
							linkActionView.setUsers(0, null)
							linkActionView.hideRevokeOption(true)
						}
					}
					else {
						linkActionView.hideRevokeOption(!canEdit)

						if (invite != null) {
							val inviteExported: TL_chatInviteExported = invite!!
							linkActionView.setLink(inviteExported.link)
							linkActionView.loadUsers(inviteExported, peerId)
						}
						else {
							linkActionView.setLink(null)
							linkActionView.loadUsers(null, peerId)
						}
					}
				}

				1 -> {
					val headerCell = holder.itemView as HeaderCell

					when (position) {
						permanentLinkHeaderRow -> {
							// MARK: there were different headers for different situations
//							if (isPublic && (isUser || adminId == accountInstance.userConfig.clientUserId)) {
//								headerCell.setText(mContext.getString(R.string.PublicLink))
//							}
//							else if (adminId == accountInstance.userConfig.clientUserId) {
//								headerCell.setText(mContext.getString(R.string.ChannelInviteLinkTitle))
//							}
//							else {
//								headerCell.setText(mContext.getString(R.string.PermanentLinkForThisAdmin))
//							}

							headerCell.setText(mContext.getString(R.string.ChannelInviteLinkTitle))
						}

						revokedHeader -> {
							headerCell.setText(mContext.getString(R.string.RevokedLinks))
						}

						linksHeaderRow -> {
							headerCell.setText(mContext.getString(R.string.LinksCreatedByThisAdmin))
						}

						adminsHeaderRow -> {
							headerCell.setText(mContext.getString(R.string.LinksCreatedByOtherAdmins))
						}
					}
				}

				3 -> {
					val textCell = holder.itemView as CreationTextCell

					val drawable = ResourcesCompat.getDrawable(mContext.resources, R.drawable.ic_poll_add_plus, null)
					drawable?.colorFilter = PorterDuffColorFilter(mContext.getColor(R.color.brand), PorterDuff.Mode.SRC_IN)

					textCell.setTextAndIcon(mContext.getString(R.string.CreateNewLink), drawable, invites.isNotEmpty())
				}

				5 -> {
					val invite: TL_chatInviteExported?
					var drawDivider = true

					if (position in linksStartRow until linksEndRow) {
						invite = invites[position - linksStartRow]

						if (position == linksEndRow - 1) {
							drawDivider = false
						}
					}
					else {
						invite = revokedInvites[position - revokedLinksStartRow]

						if (position == revokedLinksEndRow - 1) {
							drawDivider = false
						}
					}

					val cell = holder.itemView as LinkCell
					cell.setLink(invite, position - linksStartRow)
					cell.drawDivider = drawDivider
				}

				10 -> {
					val userCell = holder.itemView as ManageChatUserCell
					val user: User?
					val count: Int
					var drawDivider = true

					if (position == creatorRow) {
						user = messagesController.getUser(adminId)
						count = invitesCount
						drawDivider = false
					}
					else {
						val p = position - adminsStartRow
						val admin = admins[p]
						user = users[admin.admin_id]
						count = admin.invites_count

						if (position == adminsEndRow - 1) {
							drawDivider = false
						}
					}

					if (user != null) {
						userCell.setData(user, ContactsController.formatName(user.first_name, user.last_name), LocaleController.formatPluralString("InviteLinkCount", count), drawDivider)
					}
				}
			}
		}

		override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
			if (holder.itemView is ManageChatUserCell) {
				holder.itemView.recycle()
			}
		}

		override fun getItemViewType(position: Int): Int {
			val type = when (position) {
				helpRow -> 0
				permanentLinkHeaderRow, revokedHeader, adminsHeaderRow, linksHeaderRow -> 1
				permanentLinkRow -> 2
				createNewLinkRow -> 3
				dividerRow, revokedDivider, revokeAllDivider, creatorDividerRow, adminsDividerRow -> 4
				in linksStartRow until linksEndRow, in revokedLinksStartRow until revokedLinksEndRow -> 5
				linksLoadingRow -> 6
				lastDivider -> 7
				revokeAllRow -> 8
				createLinkHelpRow -> 9
				creatorRow, in adminsStartRow until adminsEndRow -> 10
				else -> 1
			}

			FileLog.d("Position $position, type $type")

			return type
		}
	}

	private fun revokePermanent() {
		if (adminId == accountInstance.userConfig.clientUserId) {
			val req = TL_messages_exportChatInvite()
			req.peer = messagesController.getInputPeer(-peerId)
			req.legacy_revoke_permanent = true

			val oldInvite = invite

			invite = null

			info?.exported_invite = null

			val reqId = connectionsManager.sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					if (error == null) {
						invite = response as TL_chatInviteExported?
						info?.exported_invite = invite

						if (parentActivity == null) {
							return@runOnUIThread
						}

						oldInvite?.revoked = true

						val callback = saveListState()

						oldInvite?.let {
							revokedInvites.add(0, it)
						}

						updateRecyclerViewAnimated(callback)

						BulletinFactory.of(this).createSimpleBulletin(R.raw.linkbroken, context?.getString(R.string.InviteRevokedHint)).show()
					}
				}
			}

			AndroidUtilities.updateVisibleRows(listView)

			connectionsManager.bindRequestToGuid(reqId, classGuid)
		}
		else {
			revokeLink(invite)
		}
	}

	private inner class LinkCell(context: Context) : FrameLayout(context) {
		var lastDrawingState = 0
		var titleView: TextView
		var subtitleView: TextView
		var invite: TL_chatInviteExported? = null
		var position = 0
		var paint = Paint(Paint.ANTI_ALIAS_FLAG)
		var paint2 = Paint(Paint.ANTI_ALIAS_FLAG)
		var rectF = RectF()
		var optionsView: ImageView
		var animateFromState = 0
		var animateToStateProgress = 1f
		var lastDrawExpiringProgress = 0f
		var animateHideExpiring = false
		var drawDivider = false
		var timerRunning = false

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64f), MeasureSpec.EXACTLY))
			paint2.strokeWidth = AndroidUtilities.dp(2f).toFloat()
		}

		private val timerParticles = TimerParticles()

		init {
			paint2.style = Paint.Style.STROKE
			paint2.strokeCap = Paint.Cap.ROUND

			val linearLayout = LinearLayout(context)
			linearLayout.orientation = LinearLayout.VERTICAL

			addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_VERTICAL, 70f, 0f, 30f, 0f))

			titleView = TextView(context)
			titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			titleView.setTextColor(context.getColor(R.color.text))
			titleView.setLines(1)
			titleView.ellipsize = TextUtils.TruncateAt.END

			subtitleView = TextView(context)
			subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
			subtitleView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))

			linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
			linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 6f, 0f, 0f))

			optionsView = ImageView(context)
			optionsView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.overflow_menu))
			optionsView.scaleType = ImageView.ScaleType.CENTER
			optionsView.setColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))

			optionsView.setOnClickListener {
				if (invite == null) {
					return@setOnClickListener
				}

				val items = ArrayList<String>()
				val icons = ArrayList<Int>()
				val actions = ArrayList<Int>()
				var redLastItem = false

				if (invite?.revoked == true) {
					items.add(context.getString(R.string.Delete))
					icons.add(R.drawable.msg_delete)
					actions.add(4)
					redLastItem = true
				}
				else {
					items.add(context.getString(R.string.CopyLink))
					icons.add(R.drawable.msg_copy_white)
					actions.add(0)
					items.add(context.getString(R.string.ShareLink))
					icons.add(R.drawable.msg_share_white)
					actions.add(1)

					if (invite?.permanent != true && canEdit) {
						items.add(context.getString(R.string.EditLink))
						icons.add(R.drawable.msg_edit)
						actions.add(2)
					}

					if (canEdit) {
						items.add(context.getString(R.string.RevokeLink))
						icons.add(R.drawable.msg_delete)
						actions.add(3)
						redLastItem = true
					}
				}

				val builder = AlertDialog.Builder(context)

				builder.setItems(items.toTypedArray(), AndroidUtilities.toIntArray(icons)) { _, i ->
					when (actions[i]) {
						0 -> {
							try {
								val link = invite?.link ?: return@setItems
								val clipboard = ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
								val clip = ClipData.newPlainText("label", link)
								clipboard.setPrimaryClip(clip)
								BulletinFactory.createCopyLinkBulletin(this@ManageLinksActivity).show()
							}
							catch (e: Exception) {
								e(e)
							}
						}

						1 -> {
							try {
								val link = invite?.link ?: return@setItems
								val intent = Intent(Intent.ACTION_SEND)
								intent.type = "text/plain"
								intent.putExtra(Intent.EXTRA_TEXT, link)
								startActivityForResult(Intent.createChooser(intent, context.getString(R.string.InviteToGroupByLink)), 500)
							}
							catch (e: Exception) {
								e(e)
							}
						}

						2 -> {
							editLink(invite)
						}

						3 -> {
							val inviteFinal: TL_chatInviteExported = invite ?: return@setItems
							val builder2 = AlertDialog.Builder(context)
							builder2.setMessage(context.getString(R.string.RevokeAlert))

							builder2.setPositiveButton(context.getString(R.string.RevokeButton)) { _, _ ->
								revokeLink(inviteFinal)
							}

							builder2.setNegativeButton(context.getString(R.string.Cancel), null)

							val dialog = builder2.create()
							showDialog(dialog)
						}

						4 -> {
							val inviteFinal = invite ?: return@setItems

							val builder2 = AlertDialog.Builder(context)
							builder2.setTitle(context.getString(R.string.DeleteLink))
							builder2.setMessage(context.getString(R.string.DeleteLinkHelp))

							builder2.setPositiveButtonColor(context.getColor(R.color.purple))
							builder2.setNegativeButtonColor(context.getColor(R.color.brand))

							builder2.setPositiveButton(context.getString(R.string.Delete)) { _, _ ->
								deleteLink(inviteFinal)
							}

							builder2.setNegativeButton(context.getString(R.string.Cancel), null)

							showDialog(builder2.create())
						}
					}
				}

				builder.setTitle(context.getString(R.string.InviteLink))

				val alert = builder.create()
				builder.show()

				if (redLastItem) {
					alert.setItemColor(items.size - 1, context.getColor(R.color.purple), context.getColor(R.color.purple))
				}
			}

			optionsView.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background), 1)

			addView(optionsView, LayoutHelper.createFrame(40, 48, Gravity.RIGHT or Gravity.CENTER_VERTICAL))

			setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

			setWillNotDraw(false)
		}

		override fun onDraw(canvas: Canvas) {
			val invite = invite ?: return
			val cX = AndroidUtilities.dp(36f)
			val cY = measuredHeight / 2
			val drawState: Int
			var progress = 0f
			var timeProgress = 1f

			if (invite.expired || invite.revoked) {
				drawState = if (invite.revoked) LINK_STATE_GRAY else LINK_STATE_RED
			}
			else if (invite.expire_date > 0 || invite.usage_limit > 0) {
				var usageProgress = 1f

				if (invite.expire_date > 0) {
					val currentTime = System.currentTimeMillis() + timeDiff * 1000L
					val expireTime = invite.expire_date * 1000L
					val date = (if (invite.start_date <= 0) invite.date else invite.start_date) * 1000L
					val from = currentTime - date
					val to = expireTime - date

					timeProgress = 1f - from / to.toFloat()
				}

				if (invite.usage_limit > 0) {
					usageProgress = (invite.usage_limit - invite.usage) / invite.usage_limit.toFloat()
				}

				progress = min(timeProgress, usageProgress)

				if (progress <= 0) {
					invite.expired = true
					drawState = LINK_STATE_RED
					AndroidUtilities.updateVisibleRows(listView)
				}
				else {
					drawState = LINK_STATE_GREEN
				}
			}
			else {
				drawState = LINK_STATE_BLUE
			}

			if (drawState != lastDrawingState && lastDrawingState >= 0) {
				animateFromState = lastDrawingState
				animateToStateProgress = 0f
				animateHideExpiring = hasProgress(animateFromState) && !hasProgress(drawState)
			}

			lastDrawingState = drawState

			if (animateToStateProgress != 1f) {
				animateToStateProgress += 16f / 250f

				if (animateToStateProgress >= 1f) {
					animateToStateProgress = 1f
					animateHideExpiring = false
				}
				else {
					invalidate()
				}
			}

			val color = if (animateToStateProgress != 1f) {
				ColorUtils.blendARGB(getColor(animateFromState, progress), getColor(drawState, progress), animateToStateProgress)
			}
			else {
				getColor(drawState, progress)
			}

			paint.color = color

			canvas.drawCircle(cX.toFloat(), cY.toFloat(), AndroidUtilities.dp(32f) / 2f, paint)

			if (animateHideExpiring || !invite.expired && invite.expire_date > 0 && !invite.revoked) {
				if (animateHideExpiring) {
					timeProgress = lastDrawExpiringProgress
				}

				paint2.color = color

				rectF.set((cX - AndroidUtilities.dp(20f)).toFloat(), (cY - AndroidUtilities.dp(20f)).toFloat(), (cX + AndroidUtilities.dp(20f)).toFloat(), (cY + AndroidUtilities.dp(20f)).toFloat())

				if (animateToStateProgress != 1f && (!hasProgress(animateFromState) || animateHideExpiring)) {
					canvas.save()
					val a = if (animateHideExpiring) 1f - animateToStateProgress else animateToStateProgress
					val s = (0.7 + 0.3f * a).toFloat()
					canvas.scale(s, s, rectF.centerX(), rectF.centerY())
					canvas.drawArc(rectF, -90f, -timeProgress * 360, false, paint2)
					timerParticles.draw(canvas, paint2, rectF, -timeProgress * 360, a)
					canvas.restore()
				}
				else {
					canvas.drawArc(rectF, -90f, -timeProgress * 360, false, paint2)
					timerParticles.draw(canvas, paint2, rectF, -timeProgress * 360, 1f)
				}

				if (!isPaused) {
					invalidate()
				}

				lastDrawExpiringProgress = timeProgress
			}

			if (invite.revoked) {
				linkIconRevoked?.setBounds(cX - AndroidUtilities.dp(12f), cY - AndroidUtilities.dp(12f), cX + AndroidUtilities.dp(12f), cY + AndroidUtilities.dp(12f))
				linkIconRevoked?.draw(canvas)
			}
			else {
				linkIcon?.setBounds(cX - AndroidUtilities.dp(12f), cY - AndroidUtilities.dp(12f), cX + AndroidUtilities.dp(12f), cY + AndroidUtilities.dp(12f))
				linkIcon?.draw(canvas)
			}

			if (drawDivider) {
				canvas.drawLine(AndroidUtilities.dp(70f).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth + AndroidUtilities.dp(23f)).toFloat(), measuredHeight.toFloat(), Theme.dividerPaint)
			}
		}

		private fun hasProgress(state: Int): Boolean {
			return state == LINK_STATE_YELLOW || state == LINK_STATE_GREEN
		}

		private fun getColor(state: Int, progress: Float): Int {
			return if (state == LINK_STATE_RED) {
				context.getColor(R.color.purple)
			}
			else if (state == LINK_STATE_GREEN) {
				if (progress > 0.5f) {
					val p = (progress - 0.5f) / 0.5f
					ColorUtils.blendARGB(context.getColor(R.color.online), context.getColor(R.color.yellow), 1f - p)
				}
				else {
					val p = progress / 0.5f
					ColorUtils.blendARGB(context.getColor(R.color.yellow), context.getColor(R.color.purple), 1f - p)
				}
			}
			else if (state == LINK_STATE_YELLOW) {
				context.getColor(R.color.yellow)
			}
			else if (state == LINK_STATE_GRAY) {
				context.getColor(R.color.dark_gray)
			}
			else {
				context.getColor(R.color.avatar_light_blue)
			}
		}

		fun setLink(invite: TL_chatInviteExported?, position: Int) {
			timerRunning = false

			if (this.invite == null || invite == null || this.invite!!.link != invite.link) {
				lastDrawingState = -1
				animateToStateProgress = 1f
			}

			this.invite = invite
			this.position = position

			if (invite == null) {
				return
			}

			if (!invite.title.isNullOrEmpty()) {
				val builder = SpannableStringBuilder(invite.title)
				Emoji.replaceEmoji(builder, titleView.paint.fontMetricsInt, false)
				titleView.text = builder
			}
			else if (invite.link.startsWith(String.format(Locale.getDefault(), "https://%s/+", ApplicationLoader.applicationContext.getString(R.string.domain)))) {
				titleView.text = invite.link.substring(String.format(Locale.getDefault(), "https://%s/+", ApplicationLoader.applicationContext.getString(R.string.domain)).length)
			}
			else if (invite.link.startsWith(String.format(Locale.getDefault(), "https://%s/joinchat/", ApplicationLoader.applicationContext.getString(R.string.domain)))) {
				titleView.text = invite.link.substring(String.format(Locale.getDefault(), "https://%s/joinchat/", ApplicationLoader.applicationContext.getString(R.string.domain)).length)
			}
			else if (invite.link.startsWith("https://")) {
				titleView.text = invite.link.substring("https://".length)
			}
			else {
				titleView.text = invite.link
			}

			var joinedString = ""

			if (invite.usage == 0 && invite.usage_limit == 0 && invite.requested == 0) {
				joinedString = context.getString(R.string.NoOneJoinedYet)
			}
			else {
				if (invite.usage_limit > 0 && invite.usage == 0 && !invite.expired && !invite.revoked) {
					joinedString = LocaleController.formatPluralString("CanJoin", invite.usage_limit)
				}
				else if (invite.usage_limit > 0 && invite.expired && invite.revoked) {
					joinedString = LocaleController.formatPluralString("PeopleJoined", invite.usage) + ", " + LocaleController.formatPluralString("PeopleJoinedRemaining", invite.usage_limit - invite.usage)
				}
				else {
					if (invite.usage > 0) {
						joinedString = LocaleController.formatPluralString("PeopleJoined", invite.usage)
					}
					if (invite.requested > 0) {
						if (invite.usage > 0) {
							joinedString = "$joinedString, "
						}

						joinedString += LocaleController.formatPluralString("JoinRequests", invite.requested)
					}
				}
			}

			if (invite.permanent && !invite.revoked) {
				val spannableStringBuilder = SpannableStringBuilder(joinedString)

				val dotDividerSpan = DotDividerSpan()
				dotDividerSpan.setTopPadding(AndroidUtilities.dp(1.5f))

				spannableStringBuilder.append("  .  ").setSpan(dotDividerSpan, spannableStringBuilder.length - 3, spannableStringBuilder.length - 2, 0)
				spannableStringBuilder.append(context.getString(R.string.Permanent))

				subtitleView.text = spannableStringBuilder
			}
			else if (invite.expired || invite.revoked) {
				if (invite.revoked && invite.usage == 0) {
					joinedString = context.getString(R.string.NoOneJoined)
				}

				val spannableStringBuilder = SpannableStringBuilder(joinedString)

				val dotDividerSpan = DotDividerSpan()
				dotDividerSpan.setTopPadding(AndroidUtilities.dp(1.5f))

				spannableStringBuilder.append("  .  ").setSpan(dotDividerSpan, spannableStringBuilder.length - 3, spannableStringBuilder.length - 2, 0)

				if (!invite.revoked && invite.usage_limit > 0 && invite.usage >= invite.usage_limit) {
					spannableStringBuilder.append(context.getString(R.string.LinkLimitReached))
				}
				else {
					spannableStringBuilder.append(if (invite.revoked) context.getString(R.string.Revoked) else context.getString(R.string.Expired))
				}

				subtitleView.text = spannableStringBuilder
			}
			else if (invite.expire_date > 0) {
				val spannableStringBuilder = SpannableStringBuilder(joinedString)

				val dotDividerSpan = DotDividerSpan()
				dotDividerSpan.setTopPadding(AndroidUtilities.dp(1.5f))

				spannableStringBuilder.append("  .  ").setSpan(dotDividerSpan, spannableStringBuilder.length - 3, spannableStringBuilder.length - 2, 0)

				val currentTime = System.currentTimeMillis() + timeDiff * 1000L
				val expireTime = invite.expire_date * 1000L
				var timeLeft = expireTime - currentTime

				if (timeLeft < 0) {
					timeLeft = 0
				}

				if (timeLeft > 86400000L) {
					spannableStringBuilder.append(LocaleController.formatPluralString("DaysLeft", (timeLeft / 86400000L).toInt()))
				}
				else {
					val s = (timeLeft / 1000 % 60).toInt()
					val m = (timeLeft / 1000 / 60 % 60).toInt()
					val h = (timeLeft / 1000 / 60 / 60).toInt()

					spannableStringBuilder.append(String.format(Locale.ENGLISH, "%02d", h)).append(String.format(Locale.ENGLISH, ":%02d", m)).append(String.format(Locale.ENGLISH, ":%02d", s))

					timerRunning = true
				}

				subtitleView.text = spannableStringBuilder
			}
			else {
				subtitleView.text = joinedString
			}
		}
	}

	fun deleteLink(invite: TL_chatInviteExported) {
		val req = TL_messages_deleteExportedChatInvite()
		req.link = invite.link
		req.peer = messagesController.getInputPeer(-peerId)

		connectionsManager.sendRequest(req) { _, error ->
			AndroidUtilities.runOnUIThread {
				if (error == null) {
					linkEditActivityCallback.onLinkRemoved(invite)
				}
			}
		}
	}

	fun editLink(invite: TL_chatInviteExported?) {
		val activity = LinkEditActivity(LinkEditActivity.EDIT_TYPE, if (isUser) -peerId else peerId, isUser)
		activity.setCallback(linkEditActivityCallback)
		activity.setInviteToEdit(invite)
		presentFragment(activity)
	}

	fun revokeLink(invite: TL_chatInviteExported?) {
		val req = TL_messages_editExportedChatInvite()
		req.link = invite!!.link
		req.revoked = true
		req.peer = messagesController.getInputPeer(-peerId)

		connectionsManager.sendRequest(req) { response, error ->
			AndroidUtilities.runOnUIThread {
				if (error == null) {
					if (response is TL_messages_exportedChatInviteReplaced) {
						if (!isPublic) {
							this@ManageLinksActivity.invite = response.new_invite as TL_chatInviteExported
						}

						invite.revoked = true

						val callback = saveListState()

						if (isPublic && adminId == accountInstance.userConfig.getClientUserId()) {
							invites.remove(invite)
							invites.add(0, response.new_invite as TL_chatInviteExported)
						}
						else if (this.invite != null) {
							this.invite = response.new_invite as TL_chatInviteExported
						}

						revokedInvites.add(0, invite)

						updateRecyclerViewAnimated(callback)
					}
					else {
						linkEditActivityCallback.onLinkEdited(invite, response)

						info?.let { info ->
							info.invitesCount--

							if (info.invitesCount < 0) {
								info.invitesCount = 0
							}

							messagesStorage.saveChatLinksCount(peerId, info.invitesCount)
						}
					}

					if (parentActivity != null) {
						BulletinFactory.of(this).createSimpleBulletin(R.raw.linkbroken, context?.getString(R.string.InviteRevokedHint)).show()
					}
				}
			}
		}
	}

	private val linkEditActivityCallback = object : LinkEditActivity.Callback {
		override fun onLinkCreated(response: TLObject?) {
			if (response is TL_chatInviteExported) {
				AndroidUtilities.runOnUIThread({
					val callback = saveListState()

					invites.add(0, response)

					info?.let { info ->
						info.invitesCount++
						messagesStorage.saveChatLinksCount(peerId, info.invitesCount)
					}

					updateRecyclerViewAnimated(callback)
				}, 200)
			}
		}

		override fun onLinkEdited(inviteToEdit: TL_chatInviteExported?, response: TLObject?) {
			if (response is TL_messages_exportedChatInvite) {
				val edited = response.invite as TL_chatInviteExported

				fixDate(edited)

				for (i in invites.indices) {
					if (invites[i].link == inviteToEdit?.link) {
						if (edited.revoked) {
							val callback = saveListState()
							invites.removeAt(i)
							revokedInvites.add(0, edited)
							updateRecyclerViewAnimated(callback)
						}
						else {
							invites[i] = edited
							updateRows(true)
						}

						return
					}
				}
			}
		}

		override fun onLinkRemoved(removedInvite: TL_chatInviteExported?) {
			for (i in revokedInvites.indices) {
				if (revokedInvites[i].link == removedInvite?.link) {
					val callback = saveListState()
					revokedInvites.removeAt(i)
					updateRecyclerViewAnimated(callback)
					return
				}
			}
		}

		override fun revokeLink(inviteFinal: TL_chatInviteExported?) {
			this@ManageLinksActivity.revokeLink(inviteFinal)
		}
	}

	private fun updateRecyclerViewAnimated(callback: DiffCallback) {
		val listViewAdapter = listViewAdapter
		val listView = listView

		if (isPaused || listViewAdapter == null || listView == null) {
			updateRows(true)
			return
		}

		updateRows(false)
		callback.fillPositions(callback.newPositionToItem)
		DiffUtil.calculateDiff(callback).dispatchUpdatesTo(listViewAdapter)
		AndroidUtilities.updateVisibleRows(listView)
	}

	private inner class DiffCallback : DiffUtil.Callback() {
		var oldRowCount = 0
		var oldLinksStartRow = 0
		var oldLinksEndRow = 0
		var oldRevokedLinksStartRow = 0
		var oldRevokedLinksEndRow = 0
		var oldAdminsStartRow = 0
		var oldAdminsEndRow = 0
		var oldPositionToItem = SparseIntArray()
		var newPositionToItem = SparseIntArray()
		var oldLinks = mutableListOf<TL_chatInviteExported>()
		var oldRevokedLinks = mutableListOf<TL_chatInviteExported>()

		override fun getOldListSize(): Int {
			return oldRowCount
		}

		override fun getNewListSize(): Int {
			return rowCount
		}

		override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
			if (oldItemPosition in oldLinksStartRow until oldLinksEndRow || oldItemPosition in oldRevokedLinksStartRow until oldRevokedLinksEndRow) {
				if (newItemPosition in linksStartRow until linksEndRow || newItemPosition in revokedLinksStartRow until revokedLinksEndRow) {
					val newItem = if (newItemPosition in linksStartRow until linksEndRow) {
						invites[newItemPosition - linksStartRow]
					}
					else {
						revokedInvites[newItemPosition - revokedLinksStartRow]
					}

					val oldItem = if (oldItemPosition in oldLinksStartRow until oldLinksEndRow) {
						oldLinks[oldItemPosition - oldLinksStartRow]
					}
					else {
						oldRevokedLinks[oldItemPosition - oldRevokedLinksStartRow]
					}

					return oldItem.link == newItem.link
				}
			}

			if (oldItemPosition in oldAdminsStartRow until oldAdminsEndRow && newItemPosition >= adminsStartRow && newItemPosition < adminsEndRow) {
				return oldItemPosition - oldAdminsStartRow == newItemPosition - adminsStartRow
			}

			val oldItem = oldPositionToItem[oldItemPosition, -1]
			val newItem = newPositionToItem[newItemPosition, -1]

			return oldItem >= 0 && oldItem == newItem
		}

		override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
			return areItemsTheSame(oldItemPosition, newItemPosition)
		}

		fun fillPositions(sparseIntArray: SparseIntArray) {
			sparseIntArray.clear()

			var pointer = 0

			put(++pointer, creatorRow, sparseIntArray)
			put(++pointer, creatorDividerRow, sparseIntArray)
			put(++pointer, linksStartRow, sparseIntArray)
			put(++pointer, linksEndRow, sparseIntArray)
			put(++pointer, linksLoadingRow, sparseIntArray)
			put(++pointer, revokedLinksStartRow, sparseIntArray)
			put(++pointer, revokedLinksEndRow, sparseIntArray)
			put(++pointer, revokedHeader, sparseIntArray)
			put(++pointer, revokedDivider, sparseIntArray)
			put(++pointer, lastDivider, sparseIntArray)
			put(++pointer, revokeAllRow, sparseIntArray)
			put(++pointer, revokeAllDivider, sparseIntArray)
			put(++pointer, createLinkHelpRow, sparseIntArray)
			put(++pointer, helpRow, sparseIntArray)
			put(++pointer, createNewLinkRow, sparseIntArray)
			put(++pointer, adminsEndRow, sparseIntArray)
			put(++pointer, adminsStartRow, sparseIntArray)
			put(++pointer, adminsDividerRow, sparseIntArray)
			put(++pointer, adminsHeaderRow, sparseIntArray)
			put(++pointer, linksHeaderRow, sparseIntArray)
			put(++pointer, dividerRow, sparseIntArray)
		}

		private fun put(id: Int, position: Int, sparseIntArray: SparseIntArray) {
			if (position >= 0) {
				sparseIntArray.put(position, id)
			}
		}
	}

	private fun saveListState(): DiffCallback {
		val callback = DiffCallback()
		callback.fillPositions(callback.oldPositionToItem)
		callback.oldLinksStartRow = linksStartRow
		callback.oldLinksEndRow = linksEndRow
		callback.oldRevokedLinksStartRow = revokedLinksStartRow
		callback.oldRevokedLinksEndRow = revokedLinksEndRow
		callback.oldAdminsStartRow = adminsStartRow
		callback.oldAdminsEndRow = adminsEndRow
		callback.oldRowCount = rowCount
		callback.oldLinks.clear()
		callback.oldLinks.addAll(invites)
		callback.oldRevokedLinks.clear()
		callback.oldRevokedLinks.addAll(revokedInvites)
		return callback
	}

	fun fixDate(edited: TL_chatInviteExported) {
		if (edited.expire_date > 0) {
			edited.expired = connectionsManager.currentTime >= edited.expire_date
		}
		else if (edited.usage_limit > 0) {
			edited.expired = edited.usage >= edited.usage_limit
		}
	}

	override fun needDelayOpenAnimation(): Boolean {
		return true
	}

	var animationIndex = -1

	init {
		if (!isUser) {
			currentChat = MessagesController.getInstance(currentAccount).getChat(peerId)
		}
		else {
			currentUser = MessagesController.getInstance(currentAccount).getUser(peerId)
		}

		isChannel = if (!isUser) {
			ChatObject.isChannel(currentChat) && currentChat?.megagroup != true
		}
		else {
			false
		}

		if (!isUser) {
			if (adminId == 0L) {
				this.adminId = accountInstance.userConfig.clientUserId
			}
			else {
				this.adminId = adminId
			}
		}
		else {
			this.adminId = adminId
		}

		canEdit = if (!isUser) {
			val user = messagesController.getUser(this.adminId)
			this.adminId == accountInstance.userConfig.clientUserId || user != null && !user.bot
		}
		else {
			true
		}
	}

	override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
		super.onTransitionAnimationEnd(isOpen, backward)

		if (isOpen) {
			isOpened = true

			if (backward && inviteLinkBottomSheet?.isNeedReopen == true) {
				inviteLinkBottomSheet?.show()
			}
		}

		NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex)
	}

	override fun onTransitionAnimationStart(isOpen: Boolean, backward: Boolean) {
		super.onTransitionAnimationStart(isOpen, backward)
		animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null)
	}

	companion object {
		private const val LINK_STATE_BLUE = 0
		private const val LINK_STATE_GREEN = 1
		private const val LINK_STATE_YELLOW = 2
		private const val LINK_STATE_RED = 3
		private const val LINK_STATE_GRAY = 4
	}
}
