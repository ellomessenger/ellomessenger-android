/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.Adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import androidx.viewpager.widget.ViewPager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject.isChannel
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesController.MessagesLoadedCallback
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.RecentMeUrl
import org.telegram.tgnet.TLRPC.TL_contact
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ArchiveHintCell
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Cells.DialogMeUrlCell
import org.telegram.ui.Cells.DialogsEmptyCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.ProfileSearchCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Cells.UserCell
import org.telegram.ui.Components.BlurredRecyclerView
import org.telegram.ui.Components.CombinedDrawable
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.PullForegroundDrawable
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.DialogsActivity
import org.telegram.ui.DialogsActivity.DialogsHeader
import java.util.Collections

@SuppressLint("NotifyDataSetChanged")
open class DialogsAdapter(private val parentFragment: DialogsActivity, private var dialogsType: Int, private val folderId: Int, private val isOnlySelect: Boolean, private val selectedDialogs: ArrayList<Long>, private val currentAccount: Int) : SelectionAdapter() {
	private var archiveHintCell: ArchiveHintCell? = null
	private var arrowDrawable: Drawable? = null
	private var dialogsListFrozen = false
	private var forceShowEmptyCell = false
	private var forceUpdatingContacts = false
	private var hasHints: Boolean
	private var isReordering = false
	private var lastSortTime: Long = 0
	private var onlineContacts: ArrayList<TL_contact>? = null
	private var openedDialogId: Long = 0
	private var preloader: DialogsPreloader? = null
	private var prevContactsCount = 0
	private var prevDialogsCount = 0
	private var pullForegroundDrawable: PullForegroundDrawable? = null
	private var showArchiveHint = false
	var lastDialogsEmptyType = -1

	var currentCount = 0
		private set

	var dialogsCount = 0
		private set

	init {
		hasHints = folderId == 0 && dialogsType == 0 && !isOnlySelect

		if (folderId == 1) {
			val preferences = MessagesController.getGlobalMainSettings()
			showArchiveHint = preferences.getBoolean("archivehint", true)
			preferences.edit().putBoolean("archivehint", false).commit()
		}
		if (folderId == 0) {
			preloader = DialogsPreloader()
		}
	}

	fun setOpenedDialogId(id: Long) {
		openedDialogId = id
	}

	fun onReorderStateChanged(reordering: Boolean) {
		isReordering = reordering
	}

	fun fixPosition(position: Int): Int {
		@Suppress("NAME_SHADOWING") var position = position

		if (hasHints) {
			position -= 2 + MessagesController.getInstance(currentAccount).hintDialogs.size
		}

		if (showArchiveHint) {
			position -= 2
		}
		else if (dialogsType == 11 || dialogsType == 13) {
			position -= 2
		}
		else if (dialogsType == 12) {
			position -= 1
		}

		return position
	}

	val isDataSetChanged: Boolean
		get() {
			val current = currentCount
			return current != itemCount || current == 1
		}

	fun getDialogsType(): Int {
		return dialogsType
	}

	fun setDialogsType(type: Int) {
		dialogsType = type
		notifyDataSetChanged()
	}

	override fun getItemCount(): Int {
		val messagesController = MessagesController.getInstance(currentAccount)
		val array = parentFragment.getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen)

		dialogsCount = array.size

		if (!forceUpdatingContacts && !forceShowEmptyCell && dialogsType != 7 && dialogsType != 8 && dialogsType != 11 && dialogsCount == 0 && (folderId != 0 || messagesController.isLoadingDialogs(folderId) || !MessagesController.getInstance(currentAccount).isDialogsEndReached(folderId))) {
			onlineContacts = null

			FileLog.d("DialogsAdapter dialogsCount=" + dialogsCount + " dialogsType=" + dialogsType + " isLoadingDialogs=" + messagesController.isLoadingDialogs(folderId) + " isDialogsEndReached=" + MessagesController.getInstance(currentAccount).isDialogsEndReached(folderId))

			currentCount = if (folderId == 1 && showArchiveHint) {
				2
			}
			else {
				0
			}

			return currentCount
		}

		if (dialogsCount == 0 && messagesController.isLoadingDialogs(folderId)) {
			currentCount = 0
			return currentCount
		}

		var count = dialogsCount

		if (dialogsType == 7 || dialogsType == 8) {
			if (dialogsCount == 0) {
				count++
			}
		}
		else {
			if (!messagesController.isDialogsEndReached(folderId) || dialogsCount == 0) {
				count++
			}
		}

		var hasContacts = false

		if (hasHints) {
			count += 2 + messagesController.hintDialogs.size
		}
		else if (dialogsType == 0 && folderId == 0 && messagesController.isDialogsEndReached(folderId)) {
			if (ContactsController.getInstance(currentAccount).contacts.isEmpty() && !ContactsController.getInstance(currentAccount).doneLoadingContacts && !forceUpdatingContacts) {
				onlineContacts = null

				FileLog.d("DialogsAdapter loadingContacts=" + (ContactsController.getInstance(currentAccount).contacts.isEmpty() && !ContactsController.getInstance(currentAccount).doneLoadingContacts) + "dialogsCount=" + dialogsCount + " dialogsType=" + dialogsType)

				currentCount = 0
				return currentCount
			}

			if (messagesController.allFoldersDialogsCount <= 10 && ContactsController.getInstance(currentAccount).doneLoadingContacts && ContactsController.getInstance(currentAccount).contacts.isNotEmpty()) {
				if (onlineContacts == null || prevDialogsCount != dialogsCount || prevContactsCount != ContactsController.getInstance(currentAccount).contacts.size) {
					onlineContacts = ArrayList(ContactsController.getInstance(currentAccount).contacts)
					prevContactsCount = onlineContacts?.size ?: 0
					prevDialogsCount = messagesController.dialogs_dict.size()

					val selfId = UserConfig.getInstance(currentAccount).clientUserId
					var a = 0
					var n = onlineContacts?.size ?: 0

					while (a < n) {
						val userId = onlineContacts?.getOrNull(a)?.user_id ?: 0L

						if (userId == selfId || messagesController.dialogs_dict[userId] != null) {
							onlineContacts?.removeAt(a)
							a--
							n--
						}

						a++
					}

					if (onlineContacts.isNullOrEmpty()) {
						onlineContacts = null
					}

					sortOnlineContacts(false)

					if (parentFragment.getContactsAlpha() == 0f) {
						registerAdapterDataObserver(object : AdapterDataObserver() {
							override fun onChanged() {
								parentFragment.setContactsAlpha(0f)
								parentFragment.animateContactsAlpha(1f)
								unregisterAdapterDataObserver(this)
							}
						})
					}
				}

				onlineContacts?.let {
					count += it.size + 2
					hasContacts = true
				}
			}
		}

		if (folderId == 0 && !hasContacts && dialogsCount == 0 && forceUpdatingContacts) {
			count += 3
		}

		if (folderId == 0 && onlineContacts != null) {
			if (!hasContacts) {
				onlineContacts = null
			}
		}

		if (folderId == 1 && showArchiveHint) {
			count += 2
		}

		if (folderId == 0 && dialogsCount != 0) {
			count++

			if (dialogsCount > 10 && dialogsType == 0) {
				count++
			}
		}

		if (dialogsType == 11 || dialogsType == 13) {
			count += 2
		}
		else if (dialogsType == 12) {
			count += 1
		}

		currentCount = count

		return count
	}

	fun getItem(i: Int): TLObject? {
		@Suppress("NAME_SHADOWING") var i = i

		if (onlineContacts != null && (dialogsCount == 0 || i >= dialogsCount)) {
			i -= if (dialogsCount == 0) {
				3
			}
			else {
				dialogsCount + 2
			}

			return if (i < 0 || i >= (onlineContacts?.size ?: 0)) {
				null
			}
			else {
				MessagesController.getInstance(currentAccount).getUser(onlineContacts?.getOrNull(i)?.user_id)
			}
		}

		if (showArchiveHint) {
			i -= 2
		}
		else if (dialogsType == 11 || dialogsType == 13) {
			i -= 2
		}
		else if (dialogsType == 12) {
			i -= 1
		}

		val arrayList = parentFragment.getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen)

		if (hasHints) {
			val count = MessagesController.getInstance(currentAccount).hintDialogs.size

			i -= if (i < 2 + count) {
				return MessagesController.getInstance(currentAccount).hintDialogs[i - 1]
			}
			else {
				count + 2
			}
		}

		return if (i < 0 || i >= arrayList.size) {
			null
		}
		else {
			arrayList[i]
		}
	}

	fun sortOnlineContacts(notify: Boolean) {
		if (onlineContacts == null || notify && SystemClock.elapsedRealtime() - lastSortTime < 2000) {
			return
		}

		lastSortTime = SystemClock.elapsedRealtime()

		try {
			val currentTime = ConnectionsManager.getInstance(currentAccount).currentTime
			val messagesController = MessagesController.getInstance(currentAccount)

			onlineContacts?.sortWith { o1, o2 ->
				val user1 = messagesController.getUser(o2.user_id)
				val user2 = messagesController.getUser(o1.user_id)

				var status1 = 0
				var status2 = 0

				if (user1 != null) {
					if (user1.self) {
						status1 = currentTime + 50000
					}
					else if (user1.status != null) {
						status1 = user1.status?.expires ?: 0
					}
				}

				if (user2 != null) {
					if (user2.self) {
						status2 = currentTime + 50000
					}
					else if (user2.status != null) {
						status2 = user2.status?.expires ?: 0
					}
				}
				if (status1 > 0 && status2 > 0) {
					if (status1 > status2) {
						return@sortWith 1
					}
					else if (status1 < status2) {
						return@sortWith -1
					}

					return@sortWith 0
				}
				else if (status1 < 0 && status2 < 0) {
					if (status1 > status2) {
						return@sortWith 1
					}
					else if (status1 < status2) {
						return@sortWith -1
					}

					return@sortWith 0
				}
				else if (status1 < 0 && status2 > 0 || status1 == 0 && status2 != 0) {
					return@sortWith -1
				}
				else if (status2 < 0 || status1 != 0) {
					return@sortWith 1
				}

				0
			}

			if (notify) {
				notifyDataSetChanged()
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	fun setDialogsListFrozen(frozen: Boolean) {
		dialogsListFrozen = frozen
	}

	val archiveHintCellPager: ViewPager?
		get() = archiveHintCell?.viewPager

	fun updateHasHints() {
		hasHints = folderId == 0 && dialogsType == 0 && !isOnlySelect && MessagesController.getInstance(currentAccount).hintDialogs.isNotEmpty()
	}

	override fun notifyDataSetChanged() {
		updateHasHints()
		super.notifyDataSetChanged()
	}

	override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
		if (holder.itemView is DialogCell) {
			val dialogCell = holder.itemView

			dialogCell.onReorderStateChanged(isReordering, false)

			val position = fixPosition(holder.adapterPosition)

			dialogCell.dialogIndex = position
			dialogCell.checkCurrentDialogIndex(dialogsListFrozen)
			dialogCell.setChecked(selectedDialogs.contains(dialogCell.dialogId), false)
		}
	}

	override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
		val viewType = holder.itemViewType
		return viewType != VIEW_TYPE_FLICKER && viewType != VIEW_TYPE_EMPTY && viewType != VIEW_TYPE_DIVIDER && viewType != VIEW_TYPE_SHADOW && viewType != VIEW_TYPE_HEADER && viewType != VIEW_TYPE_ARCHIVE && viewType != VIEW_TYPE_LAST_EMPTY && viewType != VIEW_TYPE_NEW_CHAT_HINT && viewType != VIEW_TYPE_CONTACTS_FLICKER
	}

	override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val context = viewGroup.context
		val view: View

		when (viewType) {
			VIEW_TYPE_DIALOG -> view = if (dialogsType == 2) {
				ProfileSearchCell(context)
			}
			else {
				val dialogCell = DialogCell(parentFragment, context, needCheck = true, forceThreeLines = false, currentAccount = currentAccount)
				dialogCell.setArchivedPullAnimation(pullForegroundDrawable)
				dialogCell.setPreloader(preloader)
				dialogCell
			}

			VIEW_TYPE_FLICKER, VIEW_TYPE_CONTACTS_FLICKER -> {
				val flickerLoadingView = FlickerLoadingView(context)
				flickerLoadingView.setIsSingleCell(true)

				val flickerType = if (viewType == VIEW_TYPE_CONTACTS_FLICKER) FlickerLoadingView.CONTACT_TYPE else FlickerLoadingView.DIALOG_CELL_TYPE

				flickerLoadingView.setViewType(flickerType)

				if (flickerType == FlickerLoadingView.CONTACT_TYPE) {
					flickerLoadingView.setIgnoreHeightCheck(true)
				}

				if (viewType == VIEW_TYPE_CONTACTS_FLICKER) {
					flickerLoadingView.setItemsCount((AndroidUtilities.displaySize.y * 0.5f / AndroidUtilities.dp(64f)).toInt())
				}

				view = flickerLoadingView
			}

			VIEW_TYPE_RECENTLY_VIEWED -> {
				val headerCell = HeaderCell(context)
				headerCell.setText(context.getString(R.string.RecentlyViewed))

				val textView = TextView(context)
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
				textView.typeface = Theme.TYPEFACE_BOLD
				textView.setTextColor(context.getColor(R.color.brand))
				textView.text = context.getString(R.string.RecentlyViewedHide)
				textView.gravity = (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL

				headerCell.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP, 17f, 15f, 17f, 0f))

				textView.setOnClickListener {
					MessagesController.getInstance(currentAccount).hintDialogs.clear()
					val preferences = MessagesController.getGlobalMainSettings()
					preferences.edit().remove("installReferer").commit()
					notifyDataSetChanged()
				}

				view = headerCell
			}

			VIEW_TYPE_DIVIDER -> {
				val frameLayout: FrameLayout = object : FrameLayout(context) {
					override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
						super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(12f), MeasureSpec.EXACTLY))
					}
				}

				frameLayout.setBackgroundResource(R.color.light_background)

				val v = View(context)
				v.background = Theme.getThemedDrawable(context, R.drawable.greydivider, context.getColor(R.color.shadow))

				frameLayout.addView(v, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

				view = frameLayout
			}

			VIEW_TYPE_ME_URL -> {
				view = DialogMeUrlCell(context)
			}

			VIEW_TYPE_EMPTY -> {
				if (MessagesController.getGlobalMainSettings().getBoolean("newlyRegistered", false)) {
					val aiSpaceBubblePopup = MaterialAlertDialogBuilder(context, R.style.TransparentDialog2)
							.setView(LayoutInflater.from(context).inflate(R.layout.ai_space_bubble_popup, null))
							.show()

					aiSpaceBubblePopup.window?.setBackgroundDrawableResource(android.R.color.transparent)

					val windowParams = aiSpaceBubblePopup.window?.attributes
					windowParams?.apply {
						gravity = Gravity.TOP
						y = context.resources.getDimensionPixelSize(R.dimen.common_size_58dp)
					}

					aiSpaceBubblePopup.window?.attributes = windowParams

					MessagesController.getGlobalMainSettings().edit().remove("newlyRegistered").commit()
				}

				//MARK: Currently onInviteClick and onTipsClick are not working because the corresponding buttons have been hidden
				view = DialogsEmptyCell(context, onInviteClick = {
					parentFragment.openInviteFragment()
				}, onTipsClick = {
					parentFragment.openTipsFragment()
				}, onRecommendedClick = {
					parentFragment.switchToFeedFragment()
				})
			}

			VIEW_TYPE_USER -> {
				view = UserCell(context, 8, 0, false)
			}

			VIEW_TYPE_HEADER -> {
				view = HeaderCell(context)
				view.setPadding(0, 0, 0, AndroidUtilities.dp(12f))
			}

			VIEW_TYPE_HEADER_2 -> {
				val cell = HeaderCell(context, 16, 0, 0, false)
				cell.height = 32
				view = cell
				view.setClickable(false)
			}

			VIEW_TYPE_SHADOW -> {
				view = ShadowSectionCell(context)
				val drawable = Theme.getThemedDrawable(context, R.drawable.greydivider, context.getColor(R.color.shadow))
				val combinedDrawable = CombinedDrawable(ColorDrawable(context.getColor(R.color.light_background)), drawable)
				combinedDrawable.setFullSize(true)
				view.setBackground(combinedDrawable)
			}

			VIEW_TYPE_ARCHIVE -> {
				view = ArchiveHintCell(context).also {
					archiveHintCell = it
				}
			}

			VIEW_TYPE_LAST_EMPTY -> {
				view = LastEmptyView(context)
			}

			VIEW_TYPE_NEW_CHAT_HINT -> {
				view = object : TextInfoPrivacyCell(context) {
					private var movement = 0
					private var moveProgress = 0f
					private var lastUpdateTime: Long = 0
					private var originalX = 0
					private var originalY = 0

					override fun afterTextDraw() {
						arrowDrawable?.let {
							val bounds = it.bounds
							it.setBounds(originalX, originalY, originalX + bounds.width(), originalY + bounds.height())
						}
					}

					override fun onTextDraw() {
						arrowDrawable?.let {
							val bounds = it.bounds
							val dx = (moveProgress * AndroidUtilities.dp(3f)).toInt()

							originalX = bounds.left
							originalY = bounds.top

							it.setBounds(originalX + dx, originalY + AndroidUtilities.dp(1f), originalX + dx + bounds.width(), originalY + AndroidUtilities.dp(1f) + bounds.height())

							val newUpdateTime = SystemClock.elapsedRealtime()

							var dt = newUpdateTime - lastUpdateTime

							if (dt > 17) {
								dt = 17
							}

							lastUpdateTime = newUpdateTime

							if (movement == 0) {
								moveProgress += dt / 664.0f

								if (moveProgress >= 1.0f) {
									movement = 1
									moveProgress = 1.0f
								}
							}
							else {
								moveProgress -= dt / 664.0f

								if (moveProgress <= 0.0f) {
									movement = 0
									moveProgress = 0.0f
								}
							}

							textView.invalidate()
						}
					}
				}

				val drawable = Theme.getThemedDrawable(context, R.drawable.greydivider, context.getColor(R.color.shadow))
				val combinedDrawable = CombinedDrawable(ColorDrawable(context.getColor(R.color.light_background)), drawable)
				combinedDrawable.setFullSize(true)
				view.setBackground(combinedDrawable)
			}

			VIEW_TYPE_TEXT -> {
				view = TextCell(context)
			}

			else -> {
				view = TextCell(context)
			}
		}

		view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, if (viewType == 5) RecyclerView.LayoutParams.MATCH_PARENT else RecyclerView.LayoutParams.WRAP_CONTENT)

		return RecyclerListView.Holder(view)
	}

	fun dialogsEmptyType(): Int {
		return if (dialogsType == 7 || dialogsType == 8) {
			if (MessagesController.getInstance(currentAccount).isDialogsEndReached(folderId)) {
				DialogsEmptyCell.TYPE_FILTER_NO_CHATS_TO_DISPLAY
			}
			else {
				DialogsEmptyCell.TYPE_FILTER_ADDING_CHATS
			}
		}
		else {
			if (onlineContacts != null) DialogsEmptyCell.TYPE_WELCOME_WITH_CONTACTS else DialogsEmptyCell.TYPE_WELCOME_NO_CONTACTS
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, i: Int) {
		when (holder.itemViewType) {
			VIEW_TYPE_DIALOG -> {
				val dialog = getItem(i) as? TLRPC.Dialog
				val nextDialog = getItem(i + 1) as? TLRPC.Dialog

				if (dialogsType == 2) {
					val cell = holder.itemView as ProfileSearchCell
					val oldDialogId = cell.dialogId
					var chat: Chat? = null
					var title: CharSequence? = null
					val subtitle: CharSequence
					val isRecent = false

					if (dialog != null && dialog.id != 0L) {
						chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id)

						if (chat?.migrated_to != null) {
							val chat2 = MessagesController.getInstance(currentAccount).getChat(chat.migrated_to.channel_id)

							if (chat2 != null) {
								chat = chat2
							}
						}
					}

					if (chat != null) {
						title = chat.title

						subtitle = if (isChannel(chat) && !chat.megagroup) {
							if (chat.participants_count != 0) {
								LocaleController.formatPluralStringComma("Subscribers", chat.participants_count)
							}
							else {
								if (chat.username.isNullOrEmpty()) {
									cell.context.getString(R.string.ChannelPrivate).lowercase()
								}
								else {
									cell.context.getString(R.string.ChannelPublic).lowercase()
								}
							}
						}
						else {
							if (chat.participants_count != 0) {
								LocaleController.formatPluralStringComma("Members", chat.participants_count)
							}
							else {
								if (chat.has_geo) {
									cell.context.getString(R.string.MegaLocation)
								}
								else if (chat.username.isNullOrEmpty()) {
									cell.context.getString(R.string.MegaPrivate).lowercase()
								}
								else {
									cell.context.getString(R.string.MegaPublic).lowercase()
								}
							}
						}
					}
					else {
						subtitle = ""
					}

					cell.useSeparator = nextDialog != null
					cell.setData(chat, null, title, subtitle, isRecent, false)
					cell.setChecked(selectedDialogs.contains(cell.dialogId), oldDialogId == cell.dialogId)
				}
				else {
					val cell = holder.itemView as DialogCell
					cell.useSeparator = nextDialog != null
					cell.fullSeparator = dialog?.pinned == true && nextDialog != null && !nextDialog.pinned

					if (dialogsType == 0) {
						if (AndroidUtilities.isTablet()) {
							cell.setDialogSelected(dialog?.id == openedDialogId)
						}
					}

					cell.setChecked(selectedDialogs.contains(dialog!!.id), false)
					cell.setDialog(dialog, dialogsType, folderId)

					if (i < 10) {
						preloader?.add(dialog.id)
					}
				}
			}

			VIEW_TYPE_EMPTY -> {
				val cell = holder.itemView as DialogsEmptyCell
				cell.setType(dialogsEmptyType().also { lastDialogsEmptyType = it })
			}

			VIEW_TYPE_ME_URL -> {
				val cell = holder.itemView as DialogMeUrlCell
				cell.setRecentMeUrl(getItem(i) as RecentMeUrl?)
			}

			VIEW_TYPE_USER -> {
				val cell = holder.itemView as UserCell

				val position = if (dialogsCount == 0) {
					i - 3
				}
				else {
					i - dialogsCount - 2
				}

				val user = MessagesController.getInstance(currentAccount).getUser(onlineContacts?.getOrNull(position)?.user_id)

				cell.setData(user, null, null, 0)
			}

			VIEW_TYPE_HEADER -> {
				val cell = holder.itemView as HeaderCell

				if (dialogsType == 11 || dialogsType == 12 || dialogsType == 13) {
					if (i == 0) {
						cell.setText(cell.context.getString(R.string.ImportHeader))
					}
					else {
						cell.setText(cell.context.getString(R.string.ImportHeaderContacts))
					}
				}
				else {
					cell.setText(cell.context.getString(if (dialogsCount == 0 && forceUpdatingContacts) R.string.ConnectingYourContacts else R.string.YourContacts))
				}
			}

			VIEW_TYPE_HEADER_2 -> {
				val cell = holder.itemView as HeaderCell
				cell.setTextSize(14f)
				cell.setTextColor(cell.context.getColor(R.color.dark_gray))
				cell.setBackgroundColor(cell.context.getColor(R.color.light_background))

				when ((getItem(i) as? DialogsHeader)?.headerType) {
					DialogsHeader.HEADER_TYPE_MY_CHANNELS -> cell.setText(cell.context.getString(R.string.MyChannels))
					DialogsHeader.HEADER_TYPE_MY_GROUPS -> cell.setText(cell.context.getString(R.string.MyGroups))
					DialogsHeader.HEADER_TYPE_GROUPS -> cell.setText(cell.context.getString(R.string.FilterGroups))
				}
			}

			VIEW_TYPE_NEW_CHAT_HINT -> {
				val cell = holder.itemView as TextInfoPrivacyCell
				cell.setText(cell.context.getString(R.string.TapOnThePencil))

				if (arrowDrawable == null) {
					arrowDrawable = ResourcesCompat.getDrawable(cell.resources, R.drawable.arrow_newchat, null)
					arrowDrawable?.colorFilter = PorterDuffColorFilter(cell.context.getColor(R.color.dark_gray), PorterDuff.Mode.SRC_IN)
				}

				val textView = cell.textView
				textView.compoundDrawablePadding = AndroidUtilities.dp(4f)
				textView.setCompoundDrawablesWithIntrinsicBounds(null, null, arrowDrawable, null)
				textView.layoutParams.width = LayoutHelper.WRAP_CONTENT
			}

			VIEW_TYPE_TEXT -> {
				val cell = holder.itemView as TextCell
				cell.setColors(cell.context.getColor(R.color.brand), cell.context.getColor(R.color.brand))
				cell.setTextAndIcon(cell.context.getString(R.string.CreateGroupForImport), R.drawable.msg_groups_create, dialogsCount != 0)
				cell.setIsInDialogs()
				cell.setOffsetFromImage(75)
			}
		}

		if (i >= dialogsCount + 1) {
			holder.itemView.alpha = 1f
		}
	}

	fun setForceUpdatingContacts(forceUpdatingContacts: Boolean) {
		this.forceUpdatingContacts = forceUpdatingContacts
	}

	override fun getItemViewType(i: Int): Int {
		@Suppress("NAME_SHADOWING") var i = i

		if (dialogsCount == 0 && forceUpdatingContacts) {
			when (i) {
				0 -> return VIEW_TYPE_EMPTY
				1 -> return VIEW_TYPE_SHADOW
				2 -> return VIEW_TYPE_HEADER
				3 -> return VIEW_TYPE_CONTACTS_FLICKER
			}
		}
		else if (onlineContacts != null) {
			if (dialogsCount == 0) {
				when (i) {
					0 -> return VIEW_TYPE_EMPTY
					1 -> return VIEW_TYPE_SHADOW
					2 -> return VIEW_TYPE_HEADER
				}
			}
			else {
				if (i < dialogsCount) {
					return VIEW_TYPE_DIALOG
				}
				else if (i == dialogsCount) {
					return VIEW_TYPE_SHADOW
				}
				else if (i == dialogsCount + 1) {
					return VIEW_TYPE_HEADER
				}
				else if (i == currentCount - 1) {
					return VIEW_TYPE_LAST_EMPTY
				}
			}

			return VIEW_TYPE_USER
		}
		else if (hasHints) {
			val count = MessagesController.getInstance(currentAccount).hintDialogs.size

			i -= if (i < 2 + count) {
				if (i == 0) {
					return VIEW_TYPE_RECENTLY_VIEWED
				}
				else if (i == 1 + count) {
					return VIEW_TYPE_DIVIDER
				}

				return VIEW_TYPE_ME_URL
			}
			else {
				2 + count
			}
		}
		else if (showArchiveHint) {
			i -= when (i) {
				0 -> return VIEW_TYPE_ARCHIVE
				1 -> return VIEW_TYPE_SHADOW
				else -> 2
			}
		}
		else if (dialogsType == 11 || dialogsType == 13) {
			i -= when (i) {
				0 -> return VIEW_TYPE_HEADER
				1 -> return VIEW_TYPE_TEXT
				else -> 2
			}
		}
		else if (dialogsType == 12) {
			i -= if (i == 0) {
				return VIEW_TYPE_HEADER
			}
			else {
				1
			}
		}

		val size = parentFragment.getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen).size

		if (i == size) {
			return if (!forceShowEmptyCell && dialogsType != 7 && dialogsType != 8 && !MessagesController.getInstance(currentAccount).isDialogsEndReached(folderId)) {
				VIEW_TYPE_FLICKER
			}
			else if (size == 0) {
				VIEW_TYPE_EMPTY
			}
			else {
				VIEW_TYPE_LAST_EMPTY
			}
		}
		else if (i > size) {
			return VIEW_TYPE_LAST_EMPTY
		}

		return if (dialogsType == 2 && getItem(i) is DialogsHeader) {
			VIEW_TYPE_HEADER_2
		}
		else {
			VIEW_TYPE_DIALOG
		}
	}

	override fun notifyItemMoved(fromPosition: Int, toPosition: Int) {
		val dialogs = parentFragment.getDialogsArray(currentAccount, dialogsType, folderId, false)
		val fromIndex = fixPosition(fromPosition)
		val toIndex = fixPosition(toPosition)
		val fromDialog = dialogs[fromIndex]
		val toDialog = dialogs[toIndex]

		if (dialogsType == 7 || dialogsType == 8) {
			val filter = MessagesController.getInstance(currentAccount).selectedDialogFilter[if (dialogsType == 8) 1 else 0]

			if (filter != null) {
				val idx1 = filter.pinnedDialogs[fromDialog.id]
				val idx2 = filter.pinnedDialogs[toDialog.id]

				filter.pinnedDialogs.put(fromDialog.id, idx2)
				filter.pinnedDialogs.put(toDialog.id, idx1)
			}
		}
		else {
			val oldNum = fromDialog.pinnedNum
			fromDialog.pinnedNum = toDialog.pinnedNum
			toDialog.pinnedNum = oldNum
		}

		Collections.swap(dialogs, fromIndex, toIndex)

		super.notifyItemMoved(fromPosition, toPosition)
	}

	fun setArchivedPullDrawable(drawable: PullForegroundDrawable?) {
		pullForegroundDrawable = drawable
	}

	fun didDatabaseCleared() {
		preloader?.clear()
	}

	fun resume() {
		preloader?.resume()
	}

	fun pause() {
		preloader?.pause()
	}

	fun setForceShowEmptyCell(forceShowEmptyCell: Boolean) {
		this.forceShowEmptyCell = forceShowEmptyCell
	}

	class DialogsPreloader {
		private var preloadDialogsPool = ArrayList<Long>()
		private var resumed = false
		var currentRequestCount = 0
		var dialogsReadyMap = HashSet<Long>()
		var loadingDialogs = HashSet<Long>()
		var networkRequestCount = 0
		var preloadedErrorMap = HashSet<Long>()

		fun add(dialogId: Long) {
			if (isReady(dialogId) || preloadedErrorMap.contains(dialogId) || loadingDialogs.contains(dialogId) || preloadDialogsPool.contains(dialogId)) {
				return
			}

			preloadDialogsPool.add(dialogId)

			start()
		}

		private fun start() {
			if (!preloadIsAvailable() || !resumed || preloadDialogsPool.isEmpty() || currentRequestCount >= maxRequestCount || networkRequestCount > maxNetworkRequestCount) {
				return
			}

			val dialogId = preloadDialogsPool.removeAt(0)

			currentRequestCount++

			loadingDialogs.add(dialogId)

			MessagesController.getInstance(UserConfig.selectedAccount).ensureMessagesLoaded(dialogId, 0, object : MessagesLoadedCallback {
				override fun onMessagesLoaded(fromCache: Boolean) {
					AndroidUtilities.runOnUIThread {
						if (!fromCache) {
							networkRequestCount++

							if (networkRequestCount >= maxNetworkRequestCount) {
								AndroidUtilities.cancelRunOnUIThread(clearNetworkRequestCount)
								AndroidUtilities.runOnUIThread(clearNetworkRequestCount, networkRequestsResetTime.toLong())
							}
						}

						if (loadingDialogs.remove(dialogId)) {
							dialogsReadyMap.add(dialogId)
							updateList()
							currentRequestCount--
							start()
						}
					}
				}

				override fun onError() {
					AndroidUtilities.runOnUIThread {
						if (loadingDialogs.remove(dialogId)) {
							preloadedErrorMap.add(dialogId)
							currentRequestCount--
							start()
						}
					}
				}
			})
		}

		private fun preloadIsAvailable(): Boolean {
			return false
			// return DownloadController.getInstance(UserConfig.selectedAccount).getCurrentDownloadMask() != 0;
		}

		var clearNetworkRequestCount = Runnable {
			networkRequestCount = 0
			start()
		}

		fun updateList() {
			// unused
		}

		fun isReady(currentDialogId: Long): Boolean {
			return dialogsReadyMap.contains(currentDialogId)
		}

		fun preloadedError(currentDialogId: Long): Boolean {
			return preloadedErrorMap.contains(currentDialogId)
		}

		fun remove(currentDialogId: Long) {
			preloadDialogsPool.remove(currentDialogId)
		}

		fun clear() {
			dialogsReadyMap.clear()
			preloadedErrorMap.clear()
			loadingDialogs.clear()
			preloadDialogsPool.clear()
			currentRequestCount = 0
			networkRequestCount = 0
			AndroidUtilities.cancelRunOnUIThread(clearNetworkRequestCount)
			updateList()
		}

		fun resume() {
			resumed = true
			start()
		}

		fun pause() {
			resumed = false
		}

		companion object {
			private const val maxRequestCount = 4
			private const val maxNetworkRequestCount = 10 - maxRequestCount
			private const val networkRequestsResetTime = 60000
		}
	}

	inner class LastEmptyView(context: Context) : View(context) {
		@JvmField
		var moving = false

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val size = parentFragment.getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen).size
			val hasArchive = dialogsType == 0 && MessagesController.getInstance(currentAccount).dialogs_dict[DialogObject.makeFolderDialogId(1)] != null
			val parent = parent as View
			var height: Int
			var blurOffset = 0

			if (parent is BlurredRecyclerView) {
				blurOffset = parent.blurTopPadding
			}

			var paddingTop = parent.paddingTop

			paddingTop -= blurOffset

			if (size == 0 || paddingTop == 0 && !hasArchive) {
				height = 0
			}
			else {
				height = MeasureSpec.getSize(heightMeasureSpec)

				if (height == 0) {
					height = parent.measuredHeight
				}

				if (height == 0) {
					height = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight
				}

				height -= blurOffset

				val cellHeight = AndroidUtilities.dp((if (SharedConfig.useThreeLinesLayout) 78 else 72).toFloat())
				var dialogsHeight = size * cellHeight + (size - 1)

				onlineContacts?.let {
					dialogsHeight += it.size * AndroidUtilities.dp(58f) + (it.size - 1) + AndroidUtilities.dp(52f)
				}

				val archiveHeight = if (hasArchive) cellHeight + 1 else 0

				if (dialogsHeight < height) {
					height = height - dialogsHeight + archiveHeight

					if (paddingTop != 0) {
						height -= AndroidUtilities.statusBarHeight

						if (height < 0) {
							height = 0
						}
					}
				}
				else if (dialogsHeight - height < archiveHeight) {
					height = archiveHeight - (dialogsHeight - height)

					if (paddingTop != 0) {
						height -= AndroidUtilities.statusBarHeight
					}

					if (height < 0) {
						height = 0
					}
				}
				else {
					height = 0
				}
			}

			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
		}
	}

	companion object {
		const val VIEW_TYPE_DIALOG = 0
		const val VIEW_TYPE_FLICKER = 1
		const val VIEW_TYPE_RECENTLY_VIEWED = 2
		const val VIEW_TYPE_DIVIDER = 3
		const val VIEW_TYPE_ME_URL = 4
		const val VIEW_TYPE_EMPTY = 5
		const val VIEW_TYPE_USER = 6
		const val VIEW_TYPE_HEADER = 7
		const val VIEW_TYPE_SHADOW = 8
		const val VIEW_TYPE_ARCHIVE = 9
		const val VIEW_TYPE_LAST_EMPTY = 10
		const val VIEW_TYPE_NEW_CHAT_HINT = 11
		const val VIEW_TYPE_TEXT = 12
		const val VIEW_TYPE_CONTACTS_FLICKER = 13
		const val VIEW_TYPE_HEADER_2 = 14
	}
}
