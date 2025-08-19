/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.DialogObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.bot
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.ManageChatTextCell
import org.telegram.ui.Cells.ManageChatUserCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.ContactsActivity.ContactsActivityDelegate
import org.telegram.ui.group.GroupCreateActivity
import kotlin.math.abs

class PrivacyUsersActivity : BaseFragment, NotificationCenterDelegate, ContactsActivityDelegate {
	private val blockedUsersActivity: Boolean
	private val currentType: Int
	private var listView: RecyclerListView? = null

	private val layoutManager by lazy {
		LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
	}

	private var listViewAdapter: ListAdapter? = null
	private var emptyView: EmptyTextProgressView? = null
	private var rowCount = 0
	private var blockUserRow = 0
	private var blockUserDetailRow = 0
	private var usersHeaderRow = 0
	private var usersStartRow = 0
	private var usersEndRow = 0
	private var usersDetailRow = 0
	private var isGroup = false
	private var uidArray: ArrayList<Long>? = null
	private var isAlwaysShare = false
	private var delegate: PrivacyActivityDelegate? = null

	constructor() : super() {
		currentType = TYPE_BLOCKED
		blockedUsersActivity = true
	}

	constructor(type: Int, users: ArrayList<Long>?, group: Boolean, always: Boolean) : super() {
		uidArray = users
		isAlwaysShare = always
		isGroup = group
		blockedUsersActivity = false
		currentType = type
	}

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()

		notificationCenter.addObserver(this, NotificationCenter.updateInterfaces)

		if (currentType == TYPE_BLOCKED) {
			notificationCenter.addObserver(this, NotificationCenter.blockedUsersDidLoad)
		}

		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		notificationCenter.removeObserver(this, NotificationCenter.updateInterfaces)

		if (currentType == TYPE_BLOCKED) {
			notificationCenter.removeObserver(this, NotificationCenter.blockedUsersDidLoad)
		}
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		if (currentType == TYPE_BLOCKED) {
			actionBar?.setTitle(context.getString(R.string.BlockedUsers))
		}
		else if (currentType == TYPE_FILTER) {
			if (isAlwaysShare) {
				actionBar?.setTitle(context.getString(R.string.FilterAlwaysShow))
			}
			else {
				actionBar?.setTitle(context.getString(R.string.FilterNeverShow))
			}
		}
		else {
			if (isGroup) {
				if (isAlwaysShare) {
					actionBar?.setTitle(context.getString(R.string.AlwaysAllow))
				}
				else {
					actionBar?.setTitle(context.getString(R.string.NeverAllow))
				}
			}
			else {
				if (isAlwaysShare) {
					actionBar?.setTitle(context.getString(R.string.AlwaysShareWithTitle))
				}
				else {
					actionBar?.setTitle(context.getString(R.string.NeverShareWithTitle))
				}
			}
		}

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		fragmentView = FrameLayout(context)

		val frameLayout = fragmentView as FrameLayout
		frameLayout.setBackgroundResource(R.color.light_background)

		emptyView = EmptyTextProgressView(context)

		if (currentType == TYPE_BLOCKED) {
			emptyView?.setText(context.getString(R.string.NoBlocked))
		}
		else {
			emptyView?.setText(context.getString(R.string.NoContacts))
		}

		frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView = RecyclerListView(context)
		listView?.setEmptyView(emptyView)

		listView?.layoutManager = layoutManager
		listView?.isVerticalScrollBarEnabled = false
		listView?.setAdapter(ListAdapter(context).also { listViewAdapter = it })
		listView?.verticalScrollbarPosition = if (LocaleController.isRTL) View.SCROLLBAR_POSITION_LEFT else View.SCROLLBAR_POSITION_RIGHT

		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView?.setOnItemClickListener { _, position ->
			if (position == blockUserRow) {
				if (currentType == TYPE_BLOCKED) {
					presentFragment(DialogOrContactPickerActivity())
				}
				else {
					val args = Bundle()
					args.putBoolean(if (isAlwaysShare) "isAlwaysShare" else "isNeverShare", true)

					if (isGroup) {
						args.putInt("chatAddType", 1)
					}
					else if (currentType == TYPE_FILTER) {
						args.putInt("chatAddType", 2)
					}

					val fragment = GroupCreateActivity(args)

					fragment.setDelegate { ids ->
						for (id1 in ids) {
							if (uidArray?.contains(id1) == true) {
								continue
							}

							uidArray?.add(id1)
						}

						updateRows()

						delegate?.didUpdateUserList(uidArray, true)
					}

					presentFragment(fragment)
				}
			}
			else if (position in usersStartRow until usersEndRow) {
				if (currentType == TYPE_BLOCKED) {
					val args = Bundle()
					args.putLong("user_id", messagesController.blockedPeers.keyAt(position - usersStartRow))
					presentFragment(ProfileActivity(args))
				}
				else {
					val args = Bundle()
					val uid = uidArray!![position - usersStartRow]

					if (DialogObject.isUserDialog(uid)) {
						args.putLong("user_id", uid)
					}
					else {
						args.putLong("chat_id", -uid)
					}

					presentFragment(ProfileActivity(args))
				}
			}
		}

		listView?.setOnItemLongClickListener { _, position ->
			if (position in usersStartRow until usersEndRow) {
				if (currentType == TYPE_BLOCKED) {
					showUnblockAlert(messagesController.blockedPeers.keyAt(position - usersStartRow))
				}
				else {
					showUnblockAlert(uidArray!![position - usersStartRow])
				}

				return@setOnItemLongClickListener true
			}

			false
		}

		if (currentType == TYPE_BLOCKED) {
			listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					if (messagesController.blockedEndReached) {
						return
					}

					val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
					val visibleItemCount = abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1
					val totalItemCount = recyclerView.adapter!!.itemCount

					if (visibleItemCount > 0) {
						if (layoutManager.findLastVisibleItemPosition() >= totalItemCount - 10) {
							messagesController.getBlockedPeers(false)
						}
					}
				}
			})

			if (messagesController.totalBlockedCount < 0) {
				emptyView?.showProgress()
			}
			else {
				emptyView?.showTextView()
			}
		}

		updateRows()

		return fragmentView
	}

	fun setDelegate(privacyActivityDelegate: PrivacyActivityDelegate?) {
		delegate = privacyActivityDelegate
	}

	private fun showUnblockAlert(uid: Long) {
		val parentActivity = parentActivity ?: return

		val builder = AlertDialog.Builder(parentActivity)

		val items: Array<CharSequence?> = if (currentType == TYPE_BLOCKED) {
			arrayOf(parentActivity.getString(R.string.Unblock))
		}
		else {
			arrayOf(parentActivity.getString(R.string.Delete))
		}

		builder.setItems(items) { _, i ->
			if (i == 0) {
				if (currentType == TYPE_BLOCKED) {
					messagesController.unblockPeer(uid)
				}
				else {
					uidArray?.remove(uid)

					updateRows()

					delegate?.didUpdateUserList(uidArray, false)

					if (uidArray.isNullOrEmpty()) {
						finishFragment()
					}
				}
			}
		}

		showDialog(builder.create())
	}

	@SuppressLint("NotifyDataSetChanged")
	private fun updateRows() {
		rowCount = 0

		if (!blockedUsersActivity || messagesController.totalBlockedCount >= 0) {
			blockUserRow = rowCount++
			blockUserDetailRow = rowCount++

			val count = if (currentType == TYPE_BLOCKED) {
				messagesController.blockedPeers.size()
			}
			else {
				uidArray?.size ?: 0
			}

			if (count != 0) {
				usersHeaderRow = rowCount++
				usersStartRow = rowCount
				rowCount += count
				usersEndRow = rowCount
				usersDetailRow = rowCount++
			}
			else {
				usersHeaderRow = -1
				usersStartRow = -1
				usersEndRow = -1
				usersDetailRow = -1
			}
		}

		listViewAdapter?.notifyDataSetChanged()
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.updateInterfaces -> {
				val mask = args[0] as Int

				if (mask and MessagesController.UPDATE_MASK_AVATAR != 0 || mask and MessagesController.UPDATE_MASK_NAME != 0) {
					updateVisibleRows(mask)
				}
			}

			NotificationCenter.blockedUsersDidLoad -> {
				emptyView?.showTextView()
				updateRows()
			}
		}
	}

	private fun updateVisibleRows(mask: Int) {
		val listView = listView ?: return
		val count = listView.childCount

		for (a in 0 until count) {
			val child = listView.getChildAt(a)

			if (child is ManageChatUserCell) {
				child.update(mask)
			}
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun onResume() {
		super.onResume()
		listViewAdapter?.notifyDataSetChanged()
	}

	override fun didSelectContact(user: User?, param: String?, activity: ContactsActivity?) {
		if (user == null) {
			return
		}

		messagesController.blockPeer(user.id)
	}

	fun interface PrivacyActivityDelegate {
		fun didUpdateUserList(ids: ArrayList<Long>?, added: Boolean)
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun getItemCount(): Int {
			return rowCount
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val viewType = holder.itemViewType
			return viewType == 0 || viewType == 2
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = ManageChatUserCell(mContext, 7, 6, true)
					view.setBackgroundColor(mContext.getColor(R.color.background))

					view.setDelegate { cell, click ->
						if (click) {
							showUnblockAlert(cell.tag as Long)
						}
						true
					}
				}

				1 -> {
					view = TextInfoPrivacyCell(mContext)
				}

				2 -> {
					view = ManageChatTextCell(mContext)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}

				3 -> {
					val headerCell = HeaderCell(mContext, 21, 11, 11, false)
					headerCell.setBackgroundColor(mContext.getColor(R.color.background))
					headerCell.height = 43
					view = headerCell
				}

				else -> {
					val headerCell = HeaderCell(mContext, 21, 11, 11, false)
					headerCell.setBackgroundColor(mContext.getColor(R.color.background))
					headerCell.height = 43
					view = headerCell
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				0 -> {
					val userCell = holder.itemView as ManageChatUserCell

					val uid = if (currentType == TYPE_BLOCKED) {
						messagesController.blockedPeers.keyAt(position - usersStartRow)
					}
					else {
						uidArray!![position - usersStartRow]
					}

					userCell.tag = uid

					if (uid > 0) {
						val user = messagesController.getUser(uid)

						if (user != null) {
							val number = if (user.bot) {
								userCell.context.getString(R.string.Bot).substring(0, 1).uppercase() + userCell.context.getString(R.string.Bot).substring(1)
							}
							else {
								userCell.context.getString(R.string.NumberUnknown)
							}

							userCell.setData(user, null, number, position != usersEndRow - 1)
						}
					}
					else {
						val chat = messagesController.getChat(-uid)

						if (chat != null) {
							val subtitle = if (chat.participantsCount != 0) {
								LocaleController.formatPluralString("Members", chat.participantsCount)
							}
							else if (chat.hasGeo) {
								userCell.context.getString(R.string.MegaLocation)
							}
							else if (chat.username.isNullOrEmpty()) {
								userCell.context.getString(R.string.MegaPrivate)
							}
							else {
								userCell.context.getString(R.string.MegaPublic)
							}

							userCell.setData(chat, null, subtitle, position != usersEndRow - 1)
						}
					}
				}

				1 -> {
					val privacyCell = holder.itemView as TextInfoPrivacyCell

					if (position == blockUserDetailRow) {
						if (currentType == TYPE_BLOCKED) {
							privacyCell.setText(privacyCell.context.getString(R.string.BlockedUsersInfo))
						}
						else {
							privacyCell.setText(null)
						}

						if (usersStartRow == -1) {
							privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, privacyCell.context.getColor(R.color.shadow))
						}
						else {
							privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, privacyCell.context.getColor(R.color.shadow))
						}
					}
					else if (position == usersDetailRow) {
						privacyCell.setText("")
						privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, privacyCell.context.getColor(R.color.shadow))
					}
				}

				2 -> {
					val actionCell = holder.itemView as ManageChatTextCell
					actionCell.setColors(ResourcesCompat.getColor(actionCell.resources, R.color.brand, null), ResourcesCompat.getColor(actionCell.resources, R.color.brand, null))

					if (currentType == TYPE_BLOCKED) {
						actionCell.setText(actionCell.context.getString(R.string.BlockUser), null, R.drawable.msg_contact_add, false)
					}
					else {
						actionCell.setText(actionCell.context.getString(R.string.PrivacyAddAnException), null, R.drawable.msg_contact_add, false)
					}
				}

				3 -> {
					val headerCell = holder.itemView as HeaderCell

					if (position == usersHeaderRow) {
						if (currentType == TYPE_BLOCKED) {
							headerCell.setText(LocaleController.formatPluralString("BlockedUsersCount", messagesController.totalBlockedCount))
						}
						else {
							headerCell.setText(headerCell.context.getString(R.string.PrivacyExceptions))
						}
					}
				}
			}
		}

		override fun getItemViewType(position: Int): Int {
			return when (position) {
				usersHeaderRow -> 3
				blockUserRow -> 2
				blockUserDetailRow, usersDetailRow -> 1
				else -> 0
			}
		}
	}

	companion object {
		const val TYPE_PRIVACY = 0
		const val TYPE_BLOCKED = 1
		const val TYPE_FILTER = 2
	}
}
