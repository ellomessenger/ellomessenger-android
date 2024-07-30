/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ContactsController
import org.telegram.messenger.ContactsController.Contact
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject.getUserName
import org.telegram.messenger.Utilities
import org.telegram.messenger.support.LongSparseIntArray
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ChatAttachAlert.AttachAlertLayout
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RecyclerListView.SectionsAdapter
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import java.util.Locale

@SuppressLint("NotifyDataSetChanged", "ViewConstructor")
class ChatAttachAlertContactsLayout(alert: ChatAttachAlert, context: Context) : AttachAlertLayout(alert, context), NotificationCenterDelegate {
	private val frameLayout = FrameLayout(context)
	private val listAdapter = ShareAdapter(context)
	private val searchAdapter = ShareSearchAdapter(context)
	private val emptyView = EmptyTextProgressView(context, null)
	private val shadow = View(context)
	private val searchField: SearchField
	private var shadowAnimation: AnimatorSet? = null
	private var ignoreLayout = false
	var delegate: PhonebookShareAlertDelegate? = null

	private val listView = object : RecyclerListView(context) {
		override fun allowSelectChildAtPosition(x: Float, y: Float): Boolean {
			return y >= parentAlert.scrollOffsetY[0] + AndroidUtilities.dp(30f) + if (!parentAlert.inBubbleMode) AndroidUtilities.statusBarHeight else 0
		}
	}

	private val layoutManager = object : FillLastLinearLayoutManager(getContext(), VERTICAL, false, AndroidUtilities.dp(9f), listView) {
		override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
			val linearSmoothScroller: LinearSmoothScroller = object : LinearSmoothScroller(recyclerView.context) {
				override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
					var dy = super.calculateDyToMakeVisible(view, snapPreference)
					dy -= listView.paddingTop - AndroidUtilities.dp(8f)
					return dy
				}

				override fun calculateTimeForDeceleration(dx: Int): Int {
					return super.calculateTimeForDeceleration(dx) * 2
				}
			}

			linearSmoothScroller.targetPosition = position
			startSmoothScroll(linearSmoothScroller)
		}
	}

	init {
		frameLayout.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.background, null))

		searchField = object : SearchField(context, false) {
			override fun onTextChange(text: String) {
				if (text.isNotEmpty()) {
					emptyView.setText(context.getString(R.string.NoResult))
				}
				else {
					if (listView.adapter !== listAdapter) {
						val top = currentTop
						emptyView.setText(context.getString(R.string.NoContacts))
						emptyView.showTextView()
						listView.adapter = listAdapter
						listAdapter.notifyDataSetChanged()

						if (top > 0) {
							layoutManager.scrollToPositionWithOffset(0, -top)
						}
					}
				}

				searchAdapter.search(text)
			}

			override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
				parentAlert.makeFocusable(searchEditText, true)
				return super.onInterceptTouchEvent(ev)
			}

			override fun processTouchEvent(event: MotionEvent) {
				val e = MotionEvent.obtain(event)
				e.setLocation(e.rawX, e.rawY - parentAlert.sheetContainer.translationY - AndroidUtilities.dp(58f))
				listView.dispatchTouchEvent(e)
				e.recycle()
			}

			override fun onFieldTouchUp(editText: EditTextBoldCursor) {
				parentAlert.makeFocusable(editText, true)
			}
		}

		searchField.setHint(context.getString(R.string.SearchFriends))

		frameLayout.addView(searchField, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

		emptyView.showTextView()
		emptyView.setText(context.getString(R.string.NoContacts))

		addView(emptyView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 52f, 0f, 0f))

		listView.clipToPadding = false

		listView.layoutManager = layoutManager

		layoutManager.setBind(false)

		listView.isHorizontalScrollBarEnabled = false
		listView.isVerticalScrollBarEnabled = false

		addView(listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f))

		listView.adapter = listAdapter
		listView.setGlowColor(ResourcesCompat.getColor(resources, R.color.feed_audio_background, null))

		listView.setOnItemClickListener { _, position ->
			val `object` = if (listView.adapter === searchAdapter) {
				searchAdapter.getItem(position)
			}
			else {
				val section = listAdapter.getSectionForPosition(position)
				val row = listAdapter.getPositionInSectionForPosition(position)

				if (row < 0 || section < 0) {
					return@setOnItemClickListener
				}

				listAdapter.getItem(section, row)
			}

			if (`object` != null) {
				val contact: Contact?
				val firstName: String?
				val lastName: String?

				when (`object`) {
					is Contact -> {
						contact = `object`

						if (contact.user != null) {
							firstName = contact.user?.first_name
							lastName = contact.user?.last_name
						}
						else {
							firstName = contact.firstName
							lastName = contact.lastName
						}
					}

					is TLRPC.TL_contact -> {
						val userId = `object`.user_id
						val user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId)

						contact = Contact()
						contact.firstName = user?.first_name
						contact.lastName = user?.last_name
						contact.user = user

						firstName = contact.firstName
						lastName = contact.lastName
					}

					is TLRPC.TL_user -> {
						val userId = `object`.id
						val user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId)

						contact = Contact()
						contact.firstName = user?.first_name
						contact.lastName = user?.last_name
						contact.user = user

						firstName = contact.firstName
						lastName = contact.lastName
					}

					else -> {
						contact = null
						firstName = null
						lastName = null
					}
				}

				if (contact != null) {
					val phonebookShareAlert = PhonebookShareAlert(parentAlert.baseFragment, contact, null, null, null, firstName, lastName)

					phonebookShareAlert.setDelegate { user, notify, scheduleDate ->
						parentAlert.dismiss(true)
						delegate?.didSelectContact(user, notify, scheduleDate)
					}

					phonebookShareAlert.show()
				}
			}
		}

		listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				parentAlert.updateLayout(this@ChatAttachAlertContactsLayout, true, dy)
				updateEmptyViewPosition()
			}
		})

		val frameLayoutParams = LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP or Gravity.LEFT)
		frameLayoutParams.topMargin = AndroidUtilities.dp(58f)

		shadow.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.shadow, null))
		shadow.alpha = 0.0f
		shadow.tag = 1

		addView(shadow, frameLayoutParams)
		addView(frameLayout, createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT or Gravity.TOP))

		NotificationCenter.getInstance(parentAlert.currentAccount).addObserver(this, NotificationCenter.contactsDidLoad)

		updateEmptyView()
	}

	override fun scrollToTop() {
		listView.smoothScrollToPosition(0)
	}

	override var currentItemTop: Int
		get() {
			if (listView.childCount <= 0) {
				return Int.MAX_VALUE
			}

			val child = listView.getChildAt(0)
			val holder = listView.findContainingViewHolder(child) as? RecyclerListView.Holder
			val top = child.top - AndroidUtilities.dp(8f)
			var newOffset = if (top > 0 && holder != null && holder.adapterPosition == 0) top else 0

			if (top >= 0 && holder != null && holder.adapterPosition == 0) {
				newOffset = top
				runShadowAnimation(false)
			}
			else {
				runShadowAnimation(true)
			}

			frameLayout.translationY = newOffset.toFloat()

			return newOffset + AndroidUtilities.dp(12f)
		}
		set(value) {
			super.currentItemTop = value
		}

	override val firstOffset: Int
		get() = listTopPadding + AndroidUtilities.dp(4f)

	override val listTopPadding: Int
		get() = listView.paddingTop

	override fun setTranslationY(translationY: Float) {
		super.setTranslationY(translationY)
		parentAlert.sheetContainer.invalidate()
	}

	override fun onPreMeasure(availableWidth: Int, availableHeight: Int) {
		val padding: Int

		if (parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20f)) {
			padding = AndroidUtilities.dp(8f)
			parentAlert.setAllowNestedScroll(false)
		}
		else {
			padding = if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
				(availableHeight / 3.5f).toInt()
			}
			else {
				availableHeight / 5 * 2
			}

			parentAlert.setAllowNestedScroll(true)
		}

		if (listView.paddingTop != padding) {
			ignoreLayout = true
			listView.setPadding(0, padding, 0, 0)
			ignoreLayout = false
		}
	}

	override fun requestLayout() {
		if (!ignoreLayout) {
			super.requestLayout()
		}
	}

	private fun runShadowAnimation(show: Boolean) {
		if (show && shadow.tag != null || !show && shadow.tag == null) {
			shadow.tag = if (show) null else 1

			if (show) {
				shadow.visibility = VISIBLE
			}

			shadowAnimation?.cancel()

			shadowAnimation = AnimatorSet()
			shadowAnimation?.playTogether(ObjectAnimator.ofFloat(shadow, ALPHA, if (show) 1.0f else 0.0f))
			shadowAnimation?.duration = 150
			shadowAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (shadowAnimation == animation) {
						if (!show) {
							shadow.visibility = INVISIBLE
						}

						shadowAnimation = null
					}
				}

				override fun onAnimationCancel(animation: Animator) {
					if (shadowAnimation == animation) {
						shadowAnimation = null
					}
				}
			})

			shadowAnimation?.start()
		}
	}

	private val currentTop: Int
		get() {
			if (listView.childCount != 0) {
				val child = listView.getChildAt(0)
				val holder = listView.findContainingViewHolder(child) as? RecyclerListView.Holder

				if (holder != null) {
					return listView.paddingTop - if (holder.adapterPosition == 0 && child.top >= 0) child.top else 0
				}
			}

			return -1000
		}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.contactsDidLoad) {
			listAdapter.notifyDataSetChanged()
		}
	}

	override fun onDestroy() {
		NotificationCenter.getInstance(parentAlert.currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad)
	}

	override fun onShow(previousLayout: AttachAlertLayout?) {
		layoutManager.scrollToPositionWithOffset(0, 0)
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		updateEmptyViewPosition()
	}

	private fun updateEmptyViewPosition() {
		if (emptyView.visibility != VISIBLE) {
			return
		}

		val child = listView.getChildAt(0) ?: return
		emptyView.translationY = ((emptyView.measuredHeight - measuredHeight + child.top) / 2).toFloat()
	}

	private fun updateEmptyView() {
		val visible = listView.adapter!!.itemCount == 2
		emptyView.visibility = if (visible) VISIBLE else GONE
		updateEmptyViewPosition()
	}

	fun interface PhonebookShareAlertDelegate {
		fun didSelectContact(user: User?, notify: Boolean, scheduleDate: Int)
	}

	class UserCell(context: Context) : FrameLayout(context) {
		private val avatarImageView = BackupImageView(context)
		private val nameTextView = SimpleTextView(context)
		private val statusTextView = SimpleTextView(context)
		private val avatarDrawable: AvatarDrawable = AvatarDrawable()
		private var currentUser: User? = null
		private var currentId = 0
		private var currentName: CharSequence? = null
		private var currentStatus: CharSequence? = null
		private var lastName: String? = null
		private var lastStatus = 0
		private var lastAvatar: FileLocation? = null
		private var needDivider = false

		init {
			avatarImageView.setRoundRadius(AndroidUtilities.dp(23f))

			addView(avatarImageView, createFrame(46, 46f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 14).toFloat(), 9f, (if (LocaleController.isRTL) 14 else 0).toFloat(), 0f))

			nameTextView.textColor = ResourcesCompat.getColor(resources, R.color.text, null)
			nameTextView.setTypeface(Theme.TYPEFACE_BOLD)
			nameTextView.setTextSize(16)
			nameTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

			addView(nameTextView, createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 28 else 72).toFloat(), 12f, (if (LocaleController.isRTL) 72 else 28).toFloat(), 0f))

			statusTextView.setTextSize(13)
			statusTextView.textColor = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
			statusTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

			addView(statusTextView, createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 28 else 72).toFloat(), 36f, (if (LocaleController.isRTL) 72 else 28).toFloat(), 0f))
		}

		fun setCurrentId(id: Int) {
			currentId = id
		}

		fun setData(user: User?, name: CharSequence?, status: CharSequence?, divider: Boolean) {
			if (user == null && name == null && status == null) {
				currentStatus = null
				currentName = null
				nameTextView.setText("")
				statusTextView.setText("")
				avatarImageView.setImageDrawable(null)
				return
			}

			currentStatus = status ?: user?.username?.let { "@$it" }
			currentName = name
			currentUser = user
			needDivider = divider

			setWillNotDraw(!needDivider)

			update(0)
		}

		fun setData(user: User?, name: CharSequence?, status: CharSequenceCallback, divider: Boolean) {
			setData(user, name, null as CharSequence?, divider)

			Utilities.globalQueue.postRunnable {
				val newCurrentStatus = status.run()

				AndroidUtilities.runOnUIThread {
					setStatus(newCurrentStatus)
				}
			}
		}

		private fun setStatus(status: CharSequence?) {
			currentStatus = status

			if (!currentStatus.isNullOrEmpty()) {
				statusTextView.setText(currentStatus)
			}
			else if (currentUser != null && currentUser?.is_public == true) {
				statusTextView.setText(currentUser?.username?.let { "@$it" } ?: "")
			}
			else {
				statusTextView.setText("")
			}
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64f) + if (needDivider) 1 else 0, MeasureSpec.EXACTLY))
		}

		fun update(mask: Int) {
			val photo = currentUser?.photo?.photo_small
			var newName: String? = null

			if (mask != 0) {
				var continueUpdate = false

				if (mask and MessagesController.UPDATE_MASK_AVATAR != 0) {
					if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar!!.volume_id != photo.volume_id || lastAvatar!!.local_id != photo.local_id)) {
						continueUpdate = true
					}
				}

				if (currentUser != null && !continueUpdate && mask and MessagesController.UPDATE_MASK_STATUS != 0) {
					val newStatus = currentUser?.status?.expires ?: 0

					if (newStatus != lastStatus) {
						continueUpdate = true
					}
				}

				if (!continueUpdate && currentName == null && lastName != null && mask and MessagesController.UPDATE_MASK_NAME != 0) {
					if (currentUser != null) {
						newName = getUserName(currentUser)
					}

					if (newName != lastName) {
						continueUpdate = true
					}
				}

				if (!continueUpdate) {
					return
				}
			}

			if (currentUser != null) {
				avatarDrawable.setInfo(currentUser)
				lastStatus = currentUser?.status?.expires ?: 0
			}
			else if (currentName != null) {
				avatarDrawable.setInfo(currentName.toString(), null)
			}
			else {
				avatarDrawable.setInfo("#", null)
			}

			if (currentName != null) {
				lastName = null
				nameTextView.setText(currentName)
			}
			else {
				lastName = if (currentUser != null) {
					newName ?: getUserName(currentUser)
				}
				else {
					""
				}

				nameTextView.setText(lastName)
			}

			setStatus(currentStatus)

			lastAvatar = photo

			if (currentUser != null) {
				avatarImageView.setForUserOrChat(currentUser, avatarDrawable)
			}
			else {
				avatarImageView.setImageDrawable(avatarDrawable)
			}
		}

		override fun hasOverlappingRendering(): Boolean {
			return false
		}

		override fun onDraw(canvas: Canvas) {
			if (needDivider) {
				canvas.drawLine((if (LocaleController.isRTL) 0 else AndroidUtilities.dp(70f)).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(70f) else 0).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
		}

		fun interface CharSequenceCallback {
			fun run(): CharSequence
		}
	}

	inner class ShareAdapter(private val mContext: Context) : SectionsAdapter() {
		private val currentAccount = UserConfig.selectedAccount

		override fun getItem(section: Int, position: Int): Any? {
			@Suppress("NAME_SHADOWING") var section = section

			if (section == 0) {
				return null
			}

			section--

			val usersSectionsDict = ContactsController.getInstance(currentAccount).usersSectionsDict
			val sortedUsersSectionsArray = ContactsController.getInstance(currentAccount).sortedUsersSectionsArray

			if (section < sortedUsersSectionsArray.size) {
				val arr = usersSectionsDict[sortedUsersSectionsArray[section]]!!

				if (position < arr.size) {
					return arr[position]
				}
			}

			return null
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder, section: Int, row: Int): Boolean {
			@Suppress("NAME_SHADOWING") var section = section

			if (section == 0 || section == getSectionCount() - 1) {
				return false
			}

			section--

			val usersSectionsDict = ContactsController.getInstance(currentAccount).usersSectionsDict
			val sortedUsersSectionsArray = ContactsController.getInstance(currentAccount).sortedUsersSectionsArray
			return row < (usersSectionsDict[sortedUsersSectionsArray[section]]?.size ?: 0)
		}

		override fun getSectionCount(): Int {
			val sortedUsersSectionsArray = ContactsController.getInstance(currentAccount).sortedUsersSectionsArray
			return sortedUsersSectionsArray.size + 2
		}

		override fun getCountForSection(section: Int): Int {
			@Suppress("NAME_SHADOWING") var section = section

			if (section == 0 || section == getSectionCount() - 1) {
				return 1
			}

			section--

			val usersSectionsDict = ContactsController.getInstance(currentAccount).usersSectionsDict
			val sortedUsersSectionsArray = ContactsController.getInstance(currentAccount).sortedUsersSectionsArray

			return if (section < sortedUsersSectionsArray.size) {
				usersSectionsDict[sortedUsersSectionsArray[section]]?.size ?: 0
			}
			else {
				0
			}
		}

		override fun getSectionHeaderView(section: Int, view: View?): View? {
			return null
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = when (viewType) {
				0 -> {
					UserCell(mContext)
				}

				1 -> {
					View(mContext).also {
						it.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56f))
					}
				}

				2 -> {
					View(mContext)
				}

				else -> {
					View(mContext)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(section: Int, position: Int, holder: RecyclerView.ViewHolder) {
			if (holder.itemViewType == 0) {
				val userCell = holder.itemView as UserCell
				val `object` = getItem(section, position)
				var user: User? = null
				val divider = section != getSectionCount() - 2 || position != getCountForSection(section) - 1

				when (`object`) {
					is Contact -> {
						if (`object`.user != null) {
							user = `object`.user
						}
						else {
							userCell.setCurrentId(`object`.contactId)
							userCell.setData(null, ContactsController.formatName(`object`.firstName, `object`.lastName), { "" }, divider)
						}
					}

					is TLRPC.TL_contact -> {
						val userId = `object`.user_id
						user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId)
					}

					is TLRPC.TL_user -> {
						val userId = `object`.id
						user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId)
					}
				}

				if (user != null) {
					userCell.setData(user, null, { "" }, divider)
				}
			}
		}

		override fun getItemViewType(section: Int, position: Int): Int {
			return when (section) {
				0 -> 1
				getSectionCount() - 1 -> 2
				else -> 0
			}
		}

		override fun getLetter(position: Int): String? {
			return null
		}

		override fun getPositionForScrollProgress(listView: RecyclerListView, progress: Float, position: IntArray) {
			position[0] = 0
			position[1] = 0
		}

		override fun notifyDataSetChanged() {
			super.notifyDataSetChanged()
			updateEmptyView()
		}
	}

	inner class ShareSearchAdapter(private val mContext: Context) : SelectionAdapter() {
		private var searchResult = mutableListOf<Any>()
		private var searchResultNames = mutableListOf<CharSequence>()
		private var searchRunnable: Runnable? = null
		private var lastSearchId = 0

		fun search(query: String?) {
			if (searchRunnable != null) {
				Utilities.searchQueue.cancelRunnable(searchRunnable)
				searchRunnable = null
			}

			if (query.isNullOrEmpty()) {
				searchResult.clear()
				searchResultNames.clear()
				notifyDataSetChanged()
			}
			else {
				val searchId = ++lastSearchId

				Utilities.searchQueue.postRunnable(Runnable {
					processSearch(query, searchId)
				}.also {
					searchRunnable = it
				}, 300)
			}
		}

		private fun processSearch(query: String, searchId: Int) {
			AndroidUtilities.runOnUIThread {
				val currentAccount = UserConfig.selectedAccount
				val contactsCopy = ContactsController.getInstance(currentAccount).contacts.toList()

				Utilities.searchQueue.postRunnable {
					val search1 = query.trim().lowercase()

					if (search1.isEmpty()) {
						lastSearchId = -1
						updateSearchResults(listOf(), listOf(), lastSearchId)
						return@postRunnable
					}

					var search2 = LocaleController.getInstance().getTranslitString(search1)

					if (search1 == search2 || search2.isNullOrEmpty()) {
						search2 = null
					}

					val search = arrayOfNulls<String>(1 + if (search2 != null) 1 else 0)
					search[0] = search1

					if (search2 != null) {
						search[1] = search2
					}

					val resultArray = mutableListOf<Any>()
					val resultArrayNames = mutableListOf<CharSequence>()
					val foundUids = LongSparseIntArray()

					for (a in contactsCopy.indices) {
						val contact = contactsCopy[a]

						if (foundUids.indexOfKey(contact.user_id) >= 0) {
							continue
						}

						val user = MessagesController.getInstance(currentAccount).getUser(contact.user_id) ?: continue
						val name = ContactsController.formatName(user.first_name, user.last_name).lowercase(Locale.getDefault())
						var tName = LocaleController.getInstance().getTranslitString(name)

						if (name == tName) {
							tName = null
						}

						var found = 0

						for (q in search) {
							if (q.isNullOrEmpty()) {
								continue
							}

							if (name.startsWith(q) || name.contains(" $q") || tName != null && (tName.startsWith(q) || tName.contains(" $q"))) {
								found = 1
							}
							else if (user.username?.contains(q) == true) {
								found = 2
							}

							if (found != 0) {
								if (found == 1) {
									resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q))
								}
								else {
									resultArrayNames.add(AndroidUtilities.generateSearchName("@" + user.username, null, "@$q"))
								}

								resultArray.add(user)

								break
							}
						}
					}

					updateSearchResults(resultArray, resultArrayNames, searchId)
				}
			}
		}

		private fun updateSearchResults(users: List<Any>, names: List<CharSequence>, searchId: Int) {
			AndroidUtilities.runOnUIThread {
				if (searchId != lastSearchId) {
					return@runOnUIThread
				}

				if (searchId != -1 && listView.adapter !== searchAdapter) {
					listView.adapter = searchAdapter
				}

				searchResult = users.toMutableList()
				searchResultNames = names.toMutableList()

				notifyDataSetChanged()
			}
		}

		override fun getItemCount(): Int {
			return searchResult.size + 2
		}

		fun getItem(position: Int): Any? {
			return searchResult.getOrNull(position - 1)
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = when (viewType) {
				0 -> {
					UserCell(mContext)
				}

				1 -> {
					View(mContext).also {
						it.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56f))
					}
				}

				2 -> {
					View(mContext)
				}

				else -> {
					View(mContext)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			if (holder.itemViewType == 0) {
				val userCell = holder.itemView as UserCell
				val divider = position != itemCount - 2
				val `object` = getItem(position)
				var user: User? = null

				when (`object`) {
					is Contact -> {
						if (`object`.user != null) {
							user = `object`.user
						}
						else {
							userCell.setCurrentId(`object`.contactId)
							userCell.setData(null, searchResultNames[position - 1], { "" }, divider)
						}
					}

					is TLRPC.TL_contact -> {
						val userId = `object`.user_id
						user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId)
					}

					is TLRPC.TL_user -> {
						val userId = `object`.id
						user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId)
					}
				}

				if (user != null) {
					userCell.setData(user, searchResultNames[position - 1], { "" }, divider)
				}
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType == 0
		}

		override fun getItemViewType(position: Int): Int {
			if (position == 0) {
				return 1
			}
			else if (position == itemCount - 1) {
				return 2
			}

			return 0
		}

		override fun notifyDataSetChanged() {
			super.notifyDataSetChanged()
			updateEmptyView()
		}
	}
}
