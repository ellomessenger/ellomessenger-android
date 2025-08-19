/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.collection.LongSparseArray
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.LEFT
import androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.UserObject
import org.telegram.messenger.Utilities
import org.telegram.messenger.utils.dp
import org.telegram.messenger.utils.invisible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TLUserFull
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.bot
import org.telegram.tgnet.botNochats
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.ContactsAdapter
import org.telegram.ui.Adapters.SearchAdapter
import org.telegram.ui.Cells.DividerCell
import org.telegram.ui.Cells.UserCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ContactsEmptyView
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.StickerEmptyView
import org.telegram.ui.Components.UndoView
import org.telegram.ui.Components.UndoView.ACTION_CONTACT_DELETED
import org.telegram.ui.Components.UndoView.ACTION_DELETE
import org.telegram.ui.channel.ChannelTypeFragment
import org.telegram.ui.group.GroupCreateActivity
import org.telegram.ui.group.GroupInviteActivity
import kotlin.math.max
import kotlin.math.min

class ContactsActivity(args: Bundle?) : BaseFragment(args), NotificationCenterDelegate {
	private lateinit var undoView: UndoView
	private var listViewAdapter: ContactsAdapter? = null
	private var searchEmptyView: StickerEmptyView? = null
	private var contactsEmptyView: ContactsEmptyView? = null

	var listView: RecyclerListView? = null
		private set

	private var layoutManager: LinearLayoutManager? = null
	private var searchListViewAdapter: SearchAdapter? = null
	private var sortByName = true
	private var hasGps = false
	private var searchWas = false
	private var searching = false
	private var onlyUsers = false
	private var needPhonebook = false
	private var destroyAfterSelect = false
	private var returnAsResult = false
	private var createSecretChat = false
	private var creatingChat = false
	private var allowSelf = true
	private var allowBots = true
	private var needForwardCount = true
	private var needFinishFragment = true
	private var resetDelegate = true
	private var channelId: Long = 0
	private var chatId: Long = 0
	private var selectAlertString: String? = null
	private var ignoreUsers: LongSparseArray<User>? = null
	private var allowUsernameSearch = true
	private var delegate: ContactsActivityDelegate? = null
	private var initialSearchString: String? = null
	private var disableSections = false
	private var swipedListItem = -1

	private val topLevel: Boolean
		get() = arguments?.getBoolean("topLevel") == true

	var userId: Long = 0

	var userInfo: TLUserFull? = null

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()

		with(NotificationCenter.getInstance(currentAccount)) {
			addObserver(this@ContactsActivity, NotificationCenter.contactsDidLoad)
			addObserver(this@ContactsActivity, NotificationCenter.updateInterfaces)
			addObserver(this@ContactsActivity, NotificationCenter.encryptedChatCreated)
			addObserver(this@ContactsActivity, NotificationCenter.closeChats)
			addObserver(this@ContactsActivity, NotificationCenter.needDeleteDialog)
		}

		arguments?.let { arguments ->
			onlyUsers = arguments.getBoolean("onlyUsers", false)
			destroyAfterSelect = arguments.getBoolean("destroyAfterSelect", false)
			returnAsResult = arguments.getBoolean("returnAsResult", false)
			createSecretChat = arguments.getBoolean("createSecretChat", false)
			selectAlertString = arguments.getString("selectAlertString")
			allowUsernameSearch = arguments.getBoolean("allowUsernameSearch", true)
			needForwardCount = arguments.getBoolean("needForwardCount", true)
			allowBots = arguments.getBoolean("allowBots", true)
			allowSelf = arguments.getBoolean("allowSelf", true)
			channelId = arguments.getLong("channelId", 0)
			needFinishFragment = arguments.getBoolean("needFinishFragment", true)
			chatId = arguments.getLong("chat_id", 0)
			disableSections = arguments.getBoolean("disableSections", false)
			resetDelegate = arguments.getBoolean("resetDelegate", false)
		} ?: run {
			needPhonebook = true
		}

		if (!createSecretChat && !returnAsResult) {
			sortByName = SharedConfig.sortContactsByName
		}

		contactsController.checkInviteText()
		contactsController.reloadContactsStatusesMaybe()

		userId = getInstance(UserConfig.selectedAccount).getClientUserId()

		if (userId != 0L) {
			userInfo = messagesController.getUserFull(userId)

			if (userInfo == null) {
				messagesController.loadUserInfo(userConfig.getCurrentUser(), true, classGuid)
			}
		}

		contactsController.loadContacts(fromCache = false, hash = 0)

		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		NotificationCenter.getInstance(currentAccount).let {
			it.removeObserver(this, NotificationCenter.contactsDidLoad)
			it.removeObserver(this, NotificationCenter.updateInterfaces)
			it.removeObserver(this, NotificationCenter.encryptedChatCreated)
			it.removeObserver(this, NotificationCenter.closeChats)
			it.removeObserver(this, NotificationCenter.needDeleteDialog)
		}

		delegate = null

		AndroidUtilities.removeAdjustResize(parentActivity, classGuid)

		val animationIndex = -1

		notificationCenter.onAnimationFinish(animationIndex)

		messagesController.cancelLoadFullUser(userId)
	}

	override fun onTransitionAnimationProgress(isOpen: Boolean, progress: Float) {
		super.onTransitionAnimationProgress(isOpen, progress)
		fragmentView?.invalidate()
	}

	override fun createView(context: Context): View? {
		searching = false
		searchWas = false

		if (topLevel) {
			actionBar?.shouldDestroyBackButtonOnCollapse = true
		}
		else {
			actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		}

		actionBar?.setAllowOverlayTitle(true)

		if (destroyAfterSelect) {
			if (returnAsResult) {
				actionBar?.setTitle(context.getString(R.string.SelectContact))
			}
			else {
				if (createSecretChat) {
					actionBar?.setTitle(context.getString(R.string.NewSecretChat))
				}
				else {
					actionBar?.setTitle(context.getString(R.string.NewMessageTitle))
				}
			}
		}
		else {
			actionBar?.setTitle(context.getString(R.string.Contacts))
		}

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == -1) {
					finishFragment()
				}
			}
		})

		val menu = actionBar?.createMenu()

		val item = menu?.addItem(SEARCH_BUTTON, R.drawable.ic_search_menu)?.setIsSearchField(true)?.setActionBarMenuItemSearchListener(object : ActionBarMenuItemSearchListener() {
			override fun onSearchExpand() {
				searching = true
			}

			@SuppressLint("NotifyDataSetChanged")
			override fun onSearchCollapse() {
				searchListViewAdapter?.searchDialogs(null)

				searching = false
				searchWas = false

				if (topLevel) {
					listView?.setEmptyView(contactsEmptyView)
					searchEmptyView?.invisible()
				}

				listView?.adapter = listViewAdapter
				listView?.setSectionsType(RecyclerListView.SECTIONS_TYPE_STICKY_HEADERS)
				listViewAdapter?.notifyDataSetChanged()
				listView?.setFastScrollVisible(true)
				listView?.isVerticalScrollBarEnabled = false
			}

			@SuppressLint("NotifyDataSetChanged")
			override fun onTextChanged(editText: EditText) {
				if (searchListViewAdapter == null) {
					return
				}

				val text = editText.text?.toString()

				if (!text.isNullOrEmpty()) {
					searchWas = true

					contactsEmptyView?.invisible()

					listView?.setEmptyView(searchEmptyView)

					listView?.adapter = searchListViewAdapter
					listView?.setSectionsType(RecyclerListView.SECTIONS_TYPE_SIMPLE)
					searchListViewAdapter?.notifyDataSetChanged()
					listView?.setFastScrollVisible(false)
					listView?.isVerticalScrollBarEnabled = true

					searchEmptyView?.showProgress(show = true, animated = true)

					searchListViewAdapter?.searchDialogs(text)
				}
				else {
					if (topLevel) {
						listView?.setEmptyView(contactsEmptyView)
						searchEmptyView?.invisible()
					}

					listView?.adapter = listViewAdapter
					listView?.setSectionsType(RecyclerListView.SECTIONS_TYPE_STICKY_HEADERS)
				}
			}
		})

		item?.setSearchFieldHint(context.getString(R.string.Search))
		item?.contentDescription = context.getString(R.string.Search)

		searchListViewAdapter = object : SearchAdapter(context, ignoreUsers, allowUsernameSearch, false, false, allowBots, allowSelf, 0) {
			override fun onSearchProgressChanged() {
				if (!searchInProgress() && itemCount == 0) {
					searchEmptyView?.showProgress(show = false, animated = true)
				}

				showItemsAnimated()
			}
		}

		val inviteViaLink = if (chatId != 0L) {
			val chat = messagesController.getChat(chatId)
			if (ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE)) 1 else 0
		}
		else if (channelId != 0L) {
			val chat = messagesController.getChat(channelId)
			if (ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE) && TextUtils.isEmpty(chat.username)) 2 else 0
		}
		else {
			0
		}

		hasGps = try {
			ApplicationLoader.applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
		}
		catch (e: Throwable) {
			false
		}

		listViewAdapter = object : ContactsAdapter(context, if (onlyUsers) 1 else 0, ignoreUsers, inviteViaLink, hasGps) {
			val SWIPED_OUT_VIEW_TYPE = 100

			@SuppressLint("NotifyDataSetChanged")
			override fun notifyDataSetChanged() {
				super.notifyDataSetChanged()

				if (listView != null && listView?.adapter === this) {
					val count = super.getItemCount()

					if (needPhonebook) {
						listView?.setFastScrollVisible(count != 2)
					}
					else {
						listView?.setFastScrollVisible(count != 0)
					}
				}
			}

			override fun getItemViewType(section: Int, position: Int): Int {
				if (position == swipedListItem) {
					return SWIPED_OUT_VIEW_TYPE
				}
				return super.getItemViewType(section, position)
			}

			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
				if (SWIPED_OUT_VIEW_TYPE == viewType) {
					return RecyclerListView.Holder(DividerCell(context, 0f, false))
				}

				return super.onCreateViewHolder(parent, viewType)
			}
		}

		listViewAdapter?.setSortType(ContactsAdapter.SORT_TYPE_DEFAULT, false)
		listViewAdapter?.setDisableSections(disableSections)

		fragmentView = FrameLayout(context)

		val frameLayout = fragmentView as FrameLayout

		val flickerLoadingView = FlickerLoadingView(context)
		flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE)
		flickerLoadingView.showDate(false)

		searchEmptyView = StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH, animationResource = R.raw.panda_chat_list_no_results)
		searchEmptyView?.addView(flickerLoadingView, 0)
		searchEmptyView?.setAnimateLayoutChange(true)
		searchEmptyView?.showProgress(show = true, animated = false)
		searchEmptyView?.title?.text = context.getString(R.string.NoResult)
		searchEmptyView?.subtitle?.text = context.getString(R.string.SearchEmptyViewFilteredSubtitle2)

		frameLayout.addView(searchEmptyView!!, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		if (topLevel) {
			searchEmptyView?.invisible()

			contactsEmptyView = ContactsEmptyView(context)
			frameLayout.addView(contactsEmptyView!!, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		}

		listView = object : RecyclerListView(context) {
			override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
				super.setPadding(left, top, right, bottom)
				emptyView?.setPadding(left, top, right, bottom)
				contactsEmptyView?.setPadding(left, top, right, bottom)
			}
		}

		listView?.setSectionsType(RecyclerListView.SECTIONS_TYPE_STICKY_HEADERS)
		listView?.isVerticalScrollBarEnabled = true // MARK: was `false`
		// listView?.setFastScrollEnabled(RecyclerListView.LETTER_TYPE) // MARK: uncomment to enable fast scroll
		listView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false).also { layoutManager = it }

		frameLayout.addView(listView!!, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView?.setAnimateEmptyView(true, 0)

		if (topLevel) {
			listView?.setEmptyView(contactsEmptyView)
		}
		else {
			listView?.setEmptyView(searchEmptyView)
		}

		undoView = UndoView(context, false)
		undoView.setAdditionalTranslationY(51.dp.toFloat())

		frameLayout.addView(undoView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM or Gravity.LEFT, 8f, 0f, 8f, 8f))

		listView?.adapter = listViewAdapter

		val userInfo = userInfo

		listViewAdapter?.setOnClickListener {
			if (userInfo != null) {
				val fragment = ManageLinksActivity(userId, 0L)
				fragment.setInfo(userInfo, null)
				presentFragment(fragment)
			}
			else {
				Toast.makeText(parentActivity, R.string.user_info_is_loading, Toast.LENGTH_SHORT).show()
			}
		}

		if (!destroyAfterSelect) {
			val touchCallBack = object : SimpleCallback(0, LEFT) {
				var didSwipe = false

				val bgColor = context.getColor(R.color.purple)

				val bgPaint = Paint().apply {
					color = bgColor
					style = Paint.Style.FILL
				}

				val text = context.getString(R.string.Delete)
				val textColor = context.getColor(R.color.white)

				val textPaint = Paint().apply {
					color = textColor
					style = Paint.Style.FILL
					textSize = context.resources.getDimensionPixelSize(R.dimen.common_size_16dp).toFloat()
				}

				val textOffsetX = 24.dp
				val textHeight: Int
				val textWidth: Int

				init {
					val bounds = Rect()
					textPaint.getTextBounds(text, 0, text.length, bounds)

					textHeight = bounds.height()
					textWidth = bounds.width()
				}

				override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
					return false
				}

				override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
					val adapterPosition = viewHolder.adapterPosition
					val contact = listViewAdapter?.getItem(adapterPosition) as? User ?: return

					didSwipe = true

					swipedListItem = adapterPosition
					listViewAdapter?.notifyItemRemoved(adapterPosition)

					undoView.showWithAction(0, ACTION_CONTACT_DELETED, {
						didSwipe = false
						swipedListItem = -1
						contactsController.deleteContact(arrayListOf(contact), false)
					}) {
						didSwipe = false
						val restoreViewPosition = swipedListItem
						swipedListItem = -1
						listViewAdapter?.notifyItemChanged(restoreViewPosition)
					}

					undoView.setInfoText(context.getString(R.string.contact_deleted))
				}

				override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
					val left = viewHolder.itemView.right.toFloat() + dX
					val top = viewHolder.itemView.top.toFloat()
					val right = viewHolder.itemView.right.toFloat()
					val bottom = viewHolder.itemView.bottom.toFloat()

					if (didSwipe) {
						bgPaint.alpha = 0
					}
					else {
						bgPaint.alpha = 255
					}

					c.drawRect(left, top, right, bottom, bgPaint)
					c.drawText(text, max(left + textOffsetX, left + (right - left) / 2 - textWidth / 2), bottom - (bottom - top) / 2 + textHeight / 2, textPaint)

					super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
				}
			}

			val touchHelper = ItemTouchHelper(touchCallBack)
			touchHelper.attachToRecyclerView(listView)
		}

		if (topLevel) {
			listView?.setOnItemLongClickListener(RecyclerListView.OnItemLongClickListener { _, _ ->
				if (listView?.adapter === searchListViewAdapter) {
					return@OnItemLongClickListener false
				}

				// TODO: maybe process long click somehow?

				true
			})
		}

		listView?.setOnItemClickListener(RecyclerListView.OnItemClickListener { _, position ->
			if (listView?.adapter === searchListViewAdapter) {
				val `object` = searchListViewAdapter?.getItem(position)

				if (`object` is User) {
					if (searchListViewAdapter?.isGlobalSearch(position) == true) {
						val users = ArrayList<User>()
						users.add(`object`)
						messagesController.putUsers(users, false)
						MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, false, true)
					}

					if (returnAsResult) {
						if (ignoreUsers != null && ignoreUsers!!.indexOfKey(`object`.id) >= 0) {
							return@OnItemClickListener
						}

						didSelectResult(`object`, true, null)
					}
					else {
						if (createSecretChat) {
							// MARK: uncomment this whole block to enable secret chats
//							if (`object`.id == getInstance(currentAccount).getClientUserId()) {
//								return@OnItemClickListener
//							}
//
//							creatingChat = true

							// SecretChatHelper.getInstance(currentAccount).startSecretChat(parentActivity, `object`)
						}
						else {
							val args = Bundle()
							args.putLong("user_id", `object`.id)

							if (messagesController.checkCanOpenChat(args, this@ContactsActivity)) {
								presentFragment(ChatActivity(args), !topLevel)
							}
						}
					}
				}
				else if (`object` is String) {
					if (`object` != "section") {
						val activity = NewContactActivity()
						activity.setInitialPhoneNumber(`object`, true)
						presentFragment(activity)
					}
				}
			}
			else {
				val section = listViewAdapter?.getSectionForPosition(position) ?: -1
				val row = listViewAdapter?.getPositionInSectionForPosition(position) ?: -1

				if (row < 0 || section < 0) {
					return@OnItemClickListener
				}

				if ((!onlyUsers || inviteViaLink != 0) && section == 0) {
					if (needPhonebook) {
						if (row == 0) {
							presentFragment(InviteContactsActivity())
						}
						else if (row == 1 && hasGps) {
//							val activity = parentActivity
//
//							if (activity != null) {
//								if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//									presentFragment(ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_NEARBY_LOCATION_ACCESS))
//									return@OnItemClickListener
//								}
//							}
//
//							var enabled = true
//
//							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//								val lm = ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
//								enabled = lm.isLocationEnabled
//							}
//							else {
//								try {
//									@Suppress("DEPRECATION") val mode = Settings.Secure.getInt(ApplicationLoader.applicationContext.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
//									enabled = mode != Settings.Secure.LOCATION_MODE_OFF
//								}
//								catch (e: Throwable) {
//									FileLog.e(e)
//								}
//							}
//
//							if (!enabled) {
//								presentFragment(ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_NEARBY_LOCATION_ENABLED))
//								return@OnItemClickListener
//							}
//
//							presentFragment(PeopleNearbyActivity())
						}
					}
					else if (inviteViaLink != 0) {
						if (row == 0) {
							presentFragment(GroupInviteActivity(if (chatId != 0L) chatId else channelId))
						}
					}
					else {
						when (row) {
							0 -> {
								val args = Bundle()
								args.putInt("chatType", ChatObject.CHAT_TYPE_MEGAGROUP)
								presentFragment(GroupCreateActivity(args), false)
							}

							1 -> {
								val args = Bundle()
								args.putInt("step", 0)

								val fragment = ChannelTypeFragment(args)
								presentFragment(fragment, false)

								// val fragment = ChannelCreateActivity(args)
								// presentFragment(fragment)

//								// TODO: open `create channel` screen
//
//								Toast.makeText(getParentActivity(), "TODO: create channel", Toast.LENGTH_SHORT).show();
//
//								SharedPreferences preferences = MessagesController.getGlobalMainSettings();
//								if (!BuildConfig.DEBUG && preferences.getBoolean("channel_intro", false)) {
//									Bundle args = new Bundle();
//									args.putInt("step", 0);
//									presentFragment(new ChannelCreateActivity(args));
//								}
//								else {
//									presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
//									preferences.edit().putBoolean("channel_intro", true).commit();
//								}

								// TODO: this was the original code, with secret chat creation
//								Bundle args = new Bundle();
//								args.putBoolean("onlyUsers", true);
//								args.putBoolean("destroyAfterSelect", true);
//								args.putBoolean("createSecretChat", true);
//								args.putBoolean("allowBots", false);
//								args.putBoolean("allowSelf", false);
//								presentFragment(new ContactsActivity(args), false);
							}

							2 -> {
								val args = Bundle()
								args.putInt("step", 0)
								args.putBoolean("isCourse", true)
								val fragment = ChannelTypeFragment(args)
								presentFragment(fragment, false)
							}
						}
					}
				}
				else {
					val item1 = listViewAdapter?.getItem(section, row)

					if (item1 is User) {
						if (returnAsResult) {
							if (ignoreUsers != null && ignoreUsers!!.indexOfKey(item1.id) >= 0) {
								return@OnItemClickListener
							}

							didSelectResult(item1, true, null)
						}
						else {
							if (createSecretChat) {
								// MARK: uncomment to enable secret chats
//								creatingChat = true
//								SecretChatHelper.getInstance(currentAccount).startSecretChat(parentActivity, item1)
							}
							else {
								val args = Bundle()
								args.putLong("user_id", item1.id)

								if (messagesController.checkCanOpenChat(args, this@ContactsActivity)) {
									presentFragment(ChatActivity(args), !topLevel)
								}
							}
						}
					}
//					else if (item1 is Contact) {
//						val usePhone = item1.phones.firstOrNull()
//						val parentActivity = parentActivity
//
//						if (usePhone == null || parentActivity == null) {
//							return@OnItemClickListener
//						}
//
//						val builder = AlertDialog.Builder(parentActivity)
//						builder.setMessage(parentActivity.getString(R.string.InviteUser))
//						builder.setTitle(parentActivity.getString(R.string.AppName))
//
//						val arg1: String = usePhone
//
//						builder.setPositiveButton(parentActivity.getString(R.string.OK)) { _, _ ->
//							try {
//								val intent = Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", arg1, null))
//								intent.putExtra("sms_body", ContactsController.getInstance(currentAccount).getInviteText(1))
//								parentActivity.startActivityForResult(intent, 500)
//							}
//							catch (e: Exception) {
//								FileLog.e(e)
//							}
//						}
//
//						builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)
//
//						showDialog(builder.create())
//					}
				}
			}
		})

		listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					if (searching && searchWas) {
						parentActivity?.currentFocus?.let {
							AndroidUtilities.hideKeyboard(it)
						}
					}
				}
			}
		})

		if (initialSearchString != null) {
			actionBar?.openSearchField(initialSearchString, false)
			initialSearchString = null
		}

		return fragmentView
	}

	private fun didSelectResult(user: User, useAlert: Boolean, param: String?) {
		if (useAlert && selectAlertString != null) {
			val parentActivity = parentActivity ?: return

			if (user.bot) {
				if (user.botNochats) {
					try {
						BulletinFactory.of(this).createErrorBulletin(parentActivity.getString(R.string.BotCantJoinGroups)).show()
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					return
				}

				if (channelId != 0L) {
					val chat = messagesController.getChat(channelId)
					val builder = AlertDialog.Builder(parentActivity)

					if (ChatObject.canAddAdmins(chat)) {
						builder.setTitle(parentActivity.getString(R.string.AppName))
						builder.setMessage(parentActivity.getString(R.string.AddBotAsAdmin))

						builder.setPositiveButton(parentActivity.getString(R.string.MakeAdmin)) { _, _ ->
							delegate?.didSelectContact(user, param, this)
							delegate = null
						}

						builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)
					}
					else {
						builder.setMessage(parentActivity.getString(R.string.CantAddBotAsAdmin))
						builder.setPositiveButton(parentActivity.getString(R.string.OK), null)
					}

					showDialog(builder.create())

					return
				}
			}

			val builder = AlertDialog.Builder(parentActivity)
			builder.setTitle(parentActivity.getString(R.string.AppName))

			var message = LocaleController.formatStringSimple(selectAlertString, UserObject.getUserName(user))
			var editText: EditTextBoldCursor? = null

			if (!user.bot && needForwardCount) {
				message = String.format("%s\n\n%s", message, parentActivity.getString(R.string.AddToTheGroupForwardCount))

				editText = EditTextBoldCursor(parentActivity)
				editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
				editText.setText("50")
				editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
				editText.gravity = Gravity.CENTER
				editText.inputType = InputType.TYPE_CLASS_NUMBER
				editText.imeOptions = EditorInfo.IME_ACTION_DONE
				editText.background = Theme.createEditTextDrawable(parentActivity, true)

				val editTextFinal: EditText = editText

				editText.addTextChangedListener {
					try {
						val str = it?.toString()

						if (!str.isNullOrEmpty()) {
							val value = Utilities.parseInt(str)

							if (value < 0) {
								editTextFinal.setText("0")
								editTextFinal.setSelection(editTextFinal.length())
							}
							else if (value > 300) {
								editTextFinal.setText("300")
								editTextFinal.setSelection(editTextFinal.length())
							}
							else if (str != "" + value) {
								editTextFinal.setText(value.toString())
								editTextFinal.setSelection(editTextFinal.length())
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				builder.setView(editText)
			}

			builder.setMessage(message)

			val finalEditText: EditText? = editText

			builder.setPositiveButton(parentActivity.getString(R.string.OK)) { _, _ ->
				didSelectResult(user, false, finalEditText?.text?.toString() ?: "0")
			}

			builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

			showDialog(builder.create())

			editText?.updateLayoutParams<MarginLayoutParams> {
				if (this is FrameLayout.LayoutParams) {
					gravity = Gravity.CENTER_HORIZONTAL
				}

				leftMargin = AndroidUtilities.dp(24f)
				rightMargin = leftMargin
				height = AndroidUtilities.dp(36f)
			}

			editText?.setSelection(editText.text.length)
		}
		else {
			delegate?.didSelectContact(user, param, this)

			if (resetDelegate) {
				delegate = null
			}

			if (needFinishFragment) {
				finishFragment()
			}
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun onResume() {
		super.onResume()
		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
		listViewAdapter?.notifyDataSetChanged()
	}

	override fun onPause() {
		super.onPause()
		actionBar?.closeSearchField()
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.needDeleteDialog -> {
				if (fragmentView == null || isPaused) {
					return
				}

				val dialogId = args[0] as Long
				val user = args[1] as? User
				val chat = args[2] as? TLRPC.Chat
				val revoke = (args[3] as? Boolean) ?: false

				undoView.showWithAction(dialogId, ACTION_DELETE) {
					if (chat != null) {
						if (ChatObject.isNotInChat(chat)) {
							messagesController.deleteDialog(dialogId, 0, revoke)
						}
						else {
							messagesController.deleteParticipantFromChat(-dialogId, messagesController.getUser(userConfig.getClientUserId()), null, revoke, revoke)
						}

						if (ChatObject.isSubscriptionChannel(chat)) {
							val request = ElloRpc.unsubscribeRequest(chat.id, ElloRpc.PEER_TYPE_CHANNEL)

							ConnectionsManager.getInstance(currentAccount).sendRequest(request) { _, error ->
								AndroidUtilities.runOnUIThread {
									if (error != null) {
										FileLog.e("unsubscribe(" + chat.id + ") error(" + error.code + "): " + error.text)
									}
									else {
										FileLog.d("unsubscribe(success)")
									}
								}
							}
						}
					}
					else {
						messagesController.deleteDialog(dialogId, 0, revoke)

						if (user?.bot == true) {
							messagesController.blockPeer(user.id)
						}
					}

					messagesController.checkIfFolderEmpty(0)
				}
			}

			NotificationCenter.contactsDidLoad -> {
				if (!sortByName) {
					listViewAdapter?.setSortType(ContactsAdapter.SORT_TYPE_LAST_SEEN, true)
				}

				listViewAdapter?.notifyDataSetChanged()
			}

			NotificationCenter.updateInterfaces -> {
				val mask = args[0] as Int

				if (mask and MessagesController.UPDATE_MASK_AVATAR != 0 || mask and MessagesController.UPDATE_MASK_NAME != 0 || mask and MessagesController.UPDATE_MASK_STATUS != 0) {
					updateVisibleRows(mask)
				}

				if (mask and MessagesController.UPDATE_MASK_STATUS != 0 && !sortByName && listViewAdapter != null) {
					listViewAdapter?.sortOnlineContacts()
				}
			}

			NotificationCenter.encryptedChatCreated -> {
				if (createSecretChat && creatingChat) {
					val encryptedChat = args[0] as TLRPC.EncryptedChat
					val args2 = Bundle()
					args2.putInt("enc_id", encryptedChat.id)
					NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats)
					presentFragment(ChatActivity(args2), !topLevel)
				}
			}

			NotificationCenter.closeChats -> {
				if (!creatingChat && !topLevel) {
					removeSelfFromStack()
				}
			}
		}
	}

	private fun updateVisibleRows(mask: Int) {
		listView?.children?.forEach {
			(it as? UserCell)?.update(mask)
		}
	}

	fun setDelegate(delegate: ContactsActivityDelegate?) {
		this.delegate = delegate
	}

//	fun setIgnoreUsers(users: LongSparseArray<User>?) {
//		ignoreUsers = users
//	}

	fun setInitialSearchString(initialSearchString: String?) {
		this.initialSearchString = initialSearchString
	}

	private fun showItemsAnimated() {
		val from = if (layoutManager == null) 0 else (layoutManager?.findLastVisibleItemPosition() ?: 0)

		listView?.invalidate()

		listView?.viewTreeObserver?.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
			override fun onPreDraw(): Boolean {
				listView?.viewTreeObserver?.removeOnPreDrawListener(this)

				val n = listView?.childCount ?: 0
				val animatorSet = AnimatorSet()

				for (i in 0 until n) {
					val child = listView?.getChildAt(i) ?: continue

					if ((listView?.getChildAdapterPosition(child) ?: -1) <= from) {
						continue
					}

					child.alpha = 0f
					val s = min(listView!!.measuredHeight, max(0, child.top))
					val delay = (s / listView!!.measuredHeight.toFloat() * 100).toInt()

					val a = ObjectAnimator.ofFloat(child, View.ALPHA, 0f, 1f)
					a.startDelay = delay.toLong()
					a.duration = 200

					animatorSet.playTogether(a)
				}

				animatorSet.start()

				return true
			}
		})
	}

	override fun shouldShowBottomNavigationPanel(): Boolean {
		return topLevel
	}

	fun interface ContactsActivityDelegate {
		fun didSelectContact(user: User?, param: String?, activity: ContactsActivity?)
	}

	companion object {
		private const val SEARCH_BUTTON = 0
	}
}
