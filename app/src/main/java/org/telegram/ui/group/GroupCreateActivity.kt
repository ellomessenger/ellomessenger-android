/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.group

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.collection.LongSparseArray
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.buildSpannedString
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserObject.getFirstName
import org.telegram.messenger.Utilities
import org.telegram.messenger.utils.dp
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.bot
import org.telegram.tgnet.botNochats
import org.telegram.tgnet.deleted
import org.telegram.tgnet.isSelf
import org.telegram.tgnet.migratedTo
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.SearchAdapterHelper
import org.telegram.ui.Adapters.SearchAdapterHelper.HashtagObject
import org.telegram.ui.Adapters.SearchAdapterHelper.SearchAdapterHelperDelegate
import org.telegram.ui.Cells.CheckBoxCell
import org.telegram.ui.Cells.GroupCreateSectionCell
import org.telegram.ui.Cells.GroupCreateUserCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.GroupCreateDividerItemDecoration
import org.telegram.ui.Components.GroupCreateSpan
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.PermanentLinkBottomSheet
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.FastScrollAdapter
import org.telegram.ui.Components.StickerEmptyView
import org.telegram.ui.Components.TypefaceSpan
import org.telegram.ui.channel.ChannelPriceFragment
import org.telegram.ui.channel.NewChannelSetupFragment
import java.util.Collections
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
class GroupCreateActivity(args: Bundle) : BaseFragment(args), NotificationCenterDelegate, View.OnClickListener {
	private var scrollView: ScrollView? = null
	private var spansContainer: SpansContainer? = null
	private var editText: EditTextBoldCursor? = null
	private var listView: RecyclerListView? = null
	private var emptyView: StickerEmptyView? = null
	private var adapter: GroupCreateAdapter? = null
	private var delegate: GroupCreateActivityDelegate? = null
	private var delegate2: ContactsAddActivityDelegate? = null
	private var itemDecoration: GroupCreateDividerItemDecoration? = null
	private var doneItem: ActionBarMenuItem? = null
	private var doneButtonVisible = false
	private var ignoreScrollEvent = false
	private var measuredContainerHeight = 0
	private var containerHeight = 0
	private var chatId: Long = 0
	private var channelId: Long = 0
	private var maxCount = messagesController.maxMegagroupCount
	private var chatType = ChatObject.CHAT_TYPE_CHAT
	private var forImport = false
	private var isPublic = false
	private var isPaid = false
	private var isAlwaysShare = false
	private var isNeverShare = false
	private var addToGroup = false
	private var searchWas = false
	private var searching = false
	private var chatAddType = 0
	private var inviteLink: String? = null
	private val selectedContacts = LongSparseArray<GroupCreateSpan>()
	private val allSpans = ArrayList<GroupCreateSpan?>()
	private var currentDeletingSpan: GroupCreateSpan? = null
	private var fieldY = 0
	private var currentAnimation: AnimatorSet? = null
	private var sharedLinkBottomSheet: PermanentLinkBottomSheet? = null
	var ignoreUsers: LongSparseArray<TLObject?>? = null
	var info: TLRPC.ChatFull? = null
	var maxSize = 0

	fun interface GroupCreateActivityDelegate {
		fun didSelectUsers(ids: ArrayList<Long>)
	}

	interface ContactsAddActivityDelegate {
		fun didSelectUsers(users: List<User>, fwdCount: Int)
		fun needAddBot(user: User) {}
	}

	private inner class SpansContainer(context: Context) : ViewGroup(context) {
		private var animationStarted = false
		private val animators = ArrayList<Animator>()
		private var addingSpan: View? = null
		private var removingSpan: View? = null
		private var animationIndex = -1

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val count = childCount
			val width = MeasureSpec.getSize(widthMeasureSpec)
			val maxWidth = width - AndroidUtilities.dp(26f)
			var currentLineWidth = 0
			var y = AndroidUtilities.dp(10f)
			var allCurrentLineWidth = 0
			var allY = AndroidUtilities.dp(10f)
			var x: Int

			for (a in 0 until count) {
				val child = getChildAt(a) as? GroupCreateSpan ?: continue
				child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32f), MeasureSpec.EXACTLY))

				if (child !== removingSpan && currentLineWidth + child.measuredWidth > maxWidth) {
					y += child.measuredHeight + AndroidUtilities.dp(8f)
					currentLineWidth = 0
				}

				if (allCurrentLineWidth + child.measuredWidth > maxWidth) {
					allY += child.measuredHeight + AndroidUtilities.dp(8f)
					allCurrentLineWidth = 0
				}

				x = AndroidUtilities.dp(13f) + currentLineWidth

				if (!animationStarted) {
					if (child === removingSpan) {
						child.translationX = (AndroidUtilities.dp(13f) + allCurrentLineWidth).toFloat()
						child.translationY = allY.toFloat()
					}
					else if (removingSpan != null) {
						if (child.translationX != x.toFloat()) {
							animators.add(ObjectAnimator.ofFloat(child, "translationX", x.toFloat()))
						}

						if (child.translationY != y.toFloat()) {
							animators.add(ObjectAnimator.ofFloat(child, "translationY", y.toFloat()))
						}
					}
					else {
						child.translationX = x.toFloat()
						child.translationY = y.toFloat()
					}
				}

				if (child !== removingSpan) {
					currentLineWidth += child.measuredWidth + AndroidUtilities.dp(9f)
				}

				allCurrentLineWidth += child.measuredWidth + AndroidUtilities.dp(9f)
			}

			val minWidth = if (AndroidUtilities.isTablet()) {
				AndroidUtilities.dp((530 - 26 - 18 - 57 * 2).toFloat()) / 3
			}
			else {
				(min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp((26 + 18 + 57 * 2).toFloat())) / 3
			}

			if (maxWidth - currentLineWidth < minWidth) {
				currentLineWidth = 0
				y += AndroidUtilities.dp((32 + 8).toFloat())
			}

			if (maxWidth - allCurrentLineWidth < minWidth) {
				allY += AndroidUtilities.dp((32 + 8).toFloat())
			}

			editText?.measure(MeasureSpec.makeMeasureSpec(maxWidth - currentLineWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32f), MeasureSpec.EXACTLY))

			if (!animationStarted) {
				val currentHeight = allY + AndroidUtilities.dp((32 + 10).toFloat())
				val fieldX = currentLineWidth + AndroidUtilities.dp(16f)

				fieldY = y

				if (currentAnimation != null) {
					val resultHeight = y + AndroidUtilities.dp((32 + 10).toFloat())

					if (containerHeight != resultHeight) {
						animators.add(ObjectAnimator.ofInt(this@GroupCreateActivity, "containerHeight", resultHeight))
					}

					measuredContainerHeight = max(containerHeight, resultHeight)

					editText?.let {
						if (it.translationX != fieldX.toFloat()) {
							animators.add(ObjectAnimator.ofFloat(it, "translationX", fieldX.toFloat()))
						}

						if (it.translationY != fieldY.toFloat()) {
							animators.add(ObjectAnimator.ofFloat(it, "translationY", fieldY.toFloat()))
						}

						it.setAllowDrawCursor(false)
					}

					currentAnimation?.playTogether(animators)

					currentAnimation?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							notificationCenter.onAnimationFinish(animationIndex)
							requestLayout()
						}
					})

					animationIndex = notificationCenter.setAnimationInProgress(animationIndex, null)

					currentAnimation?.start()

					animationStarted = true
				}
				else {
					containerHeight = currentHeight
					measuredContainerHeight = containerHeight
					editText?.translationX = fieldX.toFloat()
					editText?.translationY = fieldY.toFloat()
				}
			}
			else if (currentAnimation != null) {
				if (!ignoreScrollEvent && removingSpan == null) {
					editText?.bringPointIntoView(editText!!.selectionStart)
				}
			}

			setMeasuredDimension(width, measuredContainerHeight)

			listView?.translationY = 0f
		}

		override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
			val count = childCount

			for (a in 0 until count) {
				val child = getChildAt(a)
				child.layout(0, 0, child.measuredWidth, child.measuredHeight)
			}
		}

		fun addSpan(span: GroupCreateSpan) {
			allSpans.add(span)
			selectedContacts.put(span.uid, span)
			editText?.setHintVisible(false)

			currentAnimation?.setupEndValues()
			currentAnimation?.cancel()

			animationStarted = false

			currentAnimation = AnimatorSet()

			currentAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animator: Animator) {
					addingSpan = null
					currentAnimation = null
					animationStarted = false
					editText?.setAllowDrawCursor(true)
				}
			})

			currentAnimation?.duration = 150

			addingSpan = span

			animators.clear()
			animators.add(ObjectAnimator.ofFloat(addingSpan, SCALE_X, 0.01f, 1.0f))
			animators.add(ObjectAnimator.ofFloat(addingSpan, SCALE_Y, 0.01f, 1.0f))
			animators.add(ObjectAnimator.ofFloat(addingSpan, ALPHA, 0.0f, 1.0f))

			addView(span)
		}

		fun removeSpan(span: GroupCreateSpan) {
			ignoreScrollEvent = true

			selectedContacts.remove(span.uid)
			allSpans.remove(span)

			span.setOnClickListener(null)

			currentAnimation?.setupEndValues()
			currentAnimation?.cancel()

			animationStarted = false

			currentAnimation = AnimatorSet()

			currentAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animator: Animator) {
					removeView(span)
					removingSpan = null
					currentAnimation = null
					animationStarted = false
					editText?.setAllowDrawCursor(true)

					if (allSpans.isEmpty()) {
						editText?.setHintVisible(true)
					}
				}
			})

			currentAnimation?.duration = 150
			removingSpan = span

			animators.clear()
			animators.add(ObjectAnimator.ofFloat(removingSpan, SCALE_X, 1.0f, 0.01f))
			animators.add(ObjectAnimator.ofFloat(removingSpan, SCALE_Y, 1.0f, 0.01f))
			animators.add(ObjectAnimator.ofFloat(removingSpan, ALPHA, 1.0f, 0.0f))

			requestLayout()
		}
	}

	init {
		chatType = args.getInt("chatType", ChatObject.CHAT_TYPE_CHAT)
		forImport = args.getBoolean("forImport", false)
		isPublic = args.getBoolean("isPublic", false)
		isPaid = args.getBoolean("isPaid", false)
		isAlwaysShare = args.getBoolean("isAlwaysShare", false)
		isNeverShare = args.getBoolean("isNeverShare", false)
		addToGroup = args.getBoolean("addToGroup", false)
		chatAddType = args.getInt("chatAddType", 0)
		chatId = args.getLong("chatId")
		channelId = args.getLong("channelId")
		inviteLink = args.getString("inviteLink")

		maxCount = if (isAlwaysShare || isNeverShare || addToGroup) {
			0
		}
		else {
			if (chatType == ChatObject.CHAT_TYPE_CHAT || chatType == ChatObject.CHAT_TYPE_MEGAGROUP) {
				messagesController.maxMegagroupCount
			}
			else {
				messagesController.maxBroadcastCount
			}
		}
	}

	override fun onFragmentCreate(): Boolean {
		notificationCenter.addObserver(this, NotificationCenter.contactsDidLoad)
		notificationCenter.addObserver(this, NotificationCenter.updateInterfaces)
		notificationCenter.addObserver(this, NotificationCenter.chatDidCreated)
		return super.onFragmentCreate()
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		notificationCenter.removeObserver(this, NotificationCenter.contactsDidLoad)
		notificationCenter.removeObserver(this, NotificationCenter.updateInterfaces)
		notificationCenter.removeObserver(this, NotificationCenter.chatDidCreated)
	}

	override fun onClick(v: View) {
		val span = v as GroupCreateSpan

		if (span.isDeleting) {
			currentDeletingSpan = null
			spansContainer?.removeSpan(span)
			updateHint()
			checkVisibleRows()
		}
		else {
			currentDeletingSpan?.cancelDeleteAnimation()
			currentDeletingSpan = span
			span.startDeleteAnimation()
		}
	}

	override fun createView(context: Context): View? {
		searching = false
		searchWas = false
		allSpans.clear()
		selectedContacts.clear()
		currentDeletingSpan = null
		doneButtonVisible = (chatType == ChatObject.CHAT_TYPE_CHANNEL)

		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
			actionBar?.setTitle(context.getString(R.string.add_subscribers))
		}
		else {
			if (addToGroup) {
				if (channelId != 0L) {
					actionBar?.setTitle(context.getString(R.string.ChannelAddSubscribers))
				}
				else {
					actionBar?.setTitle(context.getString(R.string.GroupAddMembers))
				}
			}
			else if (isAlwaysShare) {
				when (chatAddType) {
					2 -> {
						actionBar?.setTitle(context.getString(R.string.FilterAlwaysShow))
					}

					1 -> {
						actionBar?.setTitle(context.getString(R.string.AlwaysAllow))
					}

					else -> {
						actionBar?.setTitle(context.getString(R.string.AlwaysShareWithTitle))
					}
				}
			}
			else if (isNeverShare) {
				when (chatAddType) {
					2 -> {
						actionBar?.setTitle(context.getString(R.string.FilterNeverShow))
					}

					1 -> {
						actionBar?.setTitle(context.getString(R.string.NeverAllow))
					}

					else -> {
						actionBar?.setTitle(context.getString(R.string.NeverShareWithTitle))
					}
				}
			}
			else {
				if (chatType == ChatObject.CHAT_TYPE_CHAT || chatType == ChatObject.CHAT_TYPE_MEGAGROUP) {
					actionBar?.setTitle(context.getString(R.string.add_members))
				}
				else {
					actionBar?.setTitle(context.getString(R.string.NewBroadcastList))
				}
			}
		}

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> finishFragment()
					BUTTON_DONE -> onDonePressed()
				}
			}
		})

		fragmentView = object : ViewGroup(context) {
//			private var verticalPositionAutoAnimator: VerticalPositionAutoAnimator? = null

//			override fun onViewAdded(child: View) {
//				if (child === floatingButton && verticalPositionAutoAnimator == null) {
//					verticalPositionAutoAnimator = VerticalPositionAutoAnimator.attach(child)
//				}
//			}

//			override fun onAttachedToWindow() {
//				super.onAttachedToWindow()
//				verticalPositionAutoAnimator?.ignoreNextLayout()
//			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val width = MeasureSpec.getSize(widthMeasureSpec)
				val height = MeasureSpec.getSize(heightMeasureSpec)

				setMeasuredDimension(width, height)

				maxSize = if (AndroidUtilities.isTablet() || height > width) {
					AndroidUtilities.dp(144f)
				}
				else {
					AndroidUtilities.dp(56f)
				}

				scrollView?.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.AT_MOST))
				listView?.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView!!.measuredHeight, MeasureSpec.EXACTLY))
				emptyView?.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView!!.measuredHeight, MeasureSpec.EXACTLY))

//				if (floatingButton != null) {
//					val w = AndroidUtilities.dp(56f)
//					floatingButton?.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY))
//				}
			}

			override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
				scrollView?.layout(0, 0, scrollView!!.measuredWidth, scrollView!!.measuredHeight)
				listView?.layout(0, scrollView!!.measuredHeight, listView!!.measuredWidth, scrollView!!.measuredHeight + listView!!.measuredHeight)
				emptyView?.layout(0, scrollView!!.measuredHeight, emptyView!!.measuredWidth, scrollView!!.measuredHeight + emptyView!!.measuredHeight)

//				floatingButton?.let {
//					val l = if (LocaleController.isRTL) AndroidUtilities.dp(14f) else right - left - AndroidUtilities.dp(14f) - it.measuredWidth
//					val t = bottom - top - AndroidUtilities.dp(14f) - it.measuredHeight
//					it.layout(l, t, l + it.measuredWidth, t + it.measuredHeight)
//				}
			}

			override fun dispatchDraw(canvas: Canvas) {
				super.dispatchDraw(canvas)
				parentLayout?.drawHeaderShadow(canvas, min(maxSize, measuredContainerHeight + containerHeight - measuredContainerHeight))
			}

			override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
				return if (child === listView) {
					canvas.save()
					canvas.clipRect(child.getLeft(), min(maxSize, measuredContainerHeight + containerHeight - measuredContainerHeight), child.getRight(), child.getBottom())
					val result = super.drawChild(canvas, child, drawingTime)
					canvas.restore()
					result
				}
				else if (child === scrollView) {
					canvas.save()
					canvas.clipRect(child.getLeft(), child.getTop(), child.getRight(), min(maxSize, measuredContainerHeight + containerHeight - measuredContainerHeight))
					val result = super.drawChild(canvas, child, drawingTime)
					canvas.restore()
					result
				}
				else {
					super.drawChild(canvas, child, drawingTime)
				}
			}
		}

		val frameLayout = fragmentView as ViewGroup
		frameLayout.isFocusableInTouchMode = true
		frameLayout.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS

		scrollView = object : ScrollView(context) {
			override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
				if (ignoreScrollEvent) {
					ignoreScrollEvent = false
					return false
				}

				rectangle.offset(child.left - child.scrollX, child.top - child.scrollY)
				rectangle.top += fieldY + AndroidUtilities.dp(20f)
				rectangle.bottom += fieldY + AndroidUtilities.dp(50f)

				return super.requestChildRectangleOnScreen(child, rectangle, immediate)
			}
		}

		scrollView?.clipChildren = false

		frameLayout.clipChildren = false

		scrollView?.isVerticalScrollBarEnabled = false

		AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, context.getColor(R.color.background))

		frameLayout.addView(scrollView)

		spansContainer = SpansContainer(context)

		scrollView?.addView(spansContainer, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

		spansContainer?.setOnClickListener {
			editText?.clearFocus()
			editText?.requestFocus()
			AndroidUtilities.showKeyboard(editText)
		}

		editText = object : EditTextBoldCursor(context) {
			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(event: MotionEvent): Boolean {
				currentDeletingSpan?.cancelDeleteAnimation()
				currentDeletingSpan = null

				if (event.action == MotionEvent.ACTION_DOWN) {
					if (!AndroidUtilities.showKeyboard(this)) {
						clearFocus()
						requestFocus()
					}
				}

				return super.onTouchEvent(event)
			}
		}

		editText?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		editText?.setHintColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
		editText?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		editText?.setCursorColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		editText?.setCursorWidth(1.5f)
		editText?.inputType = InputType.TYPE_TEXT_VARIATION_FILTER or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE
		editText?.isSingleLine = true
		editText?.background = null
		editText?.isVerticalScrollBarEnabled = false
		editText?.isHorizontalScrollBarEnabled = false
		editText?.setTextIsSelectable(false)
		editText?.setPadding(0, 0, 0, 0)
		editText?.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
		editText?.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL

		spansContainer?.addView(editText)

		updateEditTextHint()

		editText?.customSelectionActionModeCallback = object : ActionMode.Callback {
			override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
				return false
			}

			override fun onDestroyActionMode(mode: ActionMode) {

			}

			override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
				return false
			}

			override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
				return false
			}
		}

		editText?.setOnEditorActionListener { _, actionId, _ ->
			actionId == EditorInfo.IME_ACTION_DONE && onDonePressed()
		}

		editText?.setOnKeyListener(object : View.OnKeyListener {
			private var wasEmpty = false

			override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
				if (keyCode == KeyEvent.KEYCODE_DEL) {
					if (event.action == KeyEvent.ACTION_DOWN) {
						wasEmpty = editText?.length() == 0
					}
					else if (event.action == KeyEvent.ACTION_UP && wasEmpty && allSpans.isNotEmpty()) {
						allSpans[allSpans.size - 1]?.let {
							spansContainer?.removeSpan(it)
						}

						updateHint()
						checkVisibleRows()

						return true
					}
				}

				return false
			}
		})

		editText?.doAfterTextChanged {
			if (editText?.length() != 0) {
				if (adapter?.searching != true) {
					searching = true
					searchWas = true
					adapter?.searching = true
					itemDecoration?.setSearching(true)
					listView?.setFastScrollVisible(false)
					listView?.isVerticalScrollBarEnabled = true
				}

				editText?.text?.toString()?.let { query ->
					adapter?.searchDialogs(query)
				}

				emptyView?.showProgress(show = true, animated = false)
			}
			else {
				closeSearch()
			}
		}

		val flickerLoadingView = FlickerLoadingView(context)
		flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE)
		flickerLoadingView.showDate(false)

		emptyView = StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH, animationResource = R.raw.panda_chat_list_no_results)
		emptyView?.addView(flickerLoadingView)
		emptyView?.showProgress(show = true, animated = false)
		emptyView?.title?.text = context.getString(R.string.NoResult)
		emptyView?.subtitle?.text = context.getString(R.string.SearchEmptyViewFilteredSubtitle2)

		frameLayout.addView(emptyView)

		listView = RecyclerListView(context)
		// listView?.setFastScrollEnabled(RecyclerListView.LETTER_TYPE)
		listView?.setEmptyView(emptyView)
		listView?.adapter = GroupCreateAdapter(context).also { adapter = it }
		listView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
		listView?.isVerticalScrollBarEnabled = false
		listView?.verticalScrollbarPosition = if (LocaleController.isRTL) View.SCROLLBAR_POSITION_LEFT else View.SCROLLBAR_POSITION_RIGHT

		listView?.addItemDecoration(GroupCreateDividerItemDecoration().also {
			itemDecoration = it
		})

		frameLayout.addView(listView)

		listView?.setOnItemClickListener { view, position ->
			if (position == 0 && adapter?.inviteViaLink != 0 && adapter?.searching != true) {
				sharedLinkBottomSheet = PermanentLinkBottomSheet(context, false, this, info, chatId, channelId != 0L)
				showDialog(sharedLinkBottomSheet)
			}
			else if (view is GroupCreateUserCell) {
				val `object` = view.`object`

				val id = when (`object`) {
					is User -> `object`.id
					is TLRPC.Chat -> -`object`.id
					else -> return@setOnItemClickListener
				}

				if (ignoreUsers != null && ignoreUsers!!.indexOfKey(id) >= 0) {
					return@setOnItemClickListener
				}

				var exists: Boolean

				if ((selectedContacts.indexOfKey(id) >= 0).also { exists = it }) {
					val span = selectedContacts[id]

					if (span != null) {
						spansContainer?.removeSpan(span)
					}
				}
				else {
					if (maxCount != 0 && selectedContacts.size() == maxCount) {
						return@setOnItemClickListener
					}

					if ((chatType == ChatObject.CHAT_TYPE_CHAT && selectedContacts.size() == messagesController.maxGroupCount) || (chatType == ChatObject.CHAT_TYPE_MEGAGROUP && selectedContacts.size() == messagesController.maxMegagroupCount)) {
						val builder = AlertDialog.Builder(context)
						builder.setTitle(context.getString(R.string.AppName))
						builder.setMessage(context.getString(R.string.SoftUserLimitAlert))
						builder.setPositiveButton(context.getString(R.string.OK), null)
						showDialog(builder.create())
						return@setOnItemClickListener
					}

					if (`object` is User) {
						if (addToGroup && `object`.bot) {
							if (channelId == 0L && `object`.botNochats) {
								try {
									BulletinFactory.of(this).createErrorBulletin(context.getString(R.string.BotCantJoinGroups)).show()
								}
								catch (e: Exception) {
									FileLog.e(e)
								}

								return@setOnItemClickListener
							}

							if (channelId != 0L) {
								val chat = messagesController.getChat(channelId)
								val builder = AlertDialog.Builder(context)

								if (ChatObject.canAddAdmins(chat)) {
									builder.setTitle(context.getString(R.string.AppName))
									builder.setMessage(context.getString(R.string.AddBotAsAdmin))

									builder.setPositiveButton(context.getString(R.string.MakeAdmin)) { _, _ ->
										delegate2?.needAddBot(`object`)

										editText?.let {
											if (it.length() > 0) {
												it.text = null
											}
										}
									}

									builder.setNegativeButton(context.getString(R.string.Cancel), null)
								}
								else {
									builder.setMessage(context.getString(R.string.CantAddBotAsAdmin))
									builder.setPositiveButton(context.getString(R.string.OK), null)
								}

								showDialog(builder.create())

								return@setOnItemClickListener
							}
						}

						messagesController.putUser(`object`, !searching)
					}
					else {
						val chat = `object` as TLRPC.Chat
						messagesController.putChat(chat, !searching)
					}

					editText?.let {
						val span = GroupCreateSpan(it.context, `object`)
						spansContainer?.addSpan(span)
						span.setOnClickListener(this@GroupCreateActivity)
					}
				}

				updateHint()

				if (searching || searchWas) {
					AndroidUtilities.showKeyboard(editText)
				}
				else {
					view.setChecked(!exists, true)
				}

				editText?.let {
					if (it.length() > 0) {
						it.text = null
					}
				}
			}
		}

		listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					editText?.hideActionMode()
					AndroidUtilities.hideKeyboard(editText)
				}
			}
		})

		listView?.setAnimateEmptyView(true, 0)

		val menu = actionBar?.createMenu()

		doneItem = menu?.addItem(BUTTON_DONE, buildSpannedString {
			val buttonText = context.getString(R.string.Next)
			append(buttonText)
			setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), 0, buttonText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.brand)), 0, buttonText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
		})

		doneItem?.setOnClickListener {
			onDonePressed()
		}

		if (chatType != ChatObject.CHAT_TYPE_CHANNEL) {
			doneItem?.gone()
		}

		updateHint()

		return fragmentView
	}

	private fun updateEditTextHint() {
		val editText = editText ?: return
		val context = context ?: return

		if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
			editText.setHintText(context.getString(R.string.search_contacts_and_usernames))
		}
		else {
			if ((addToGroup || adapter != null) && adapter?.noContactsStubRow == 0) {
				editText.setHintText(context.getString(R.string.SearchForPeople))
			}
			else if (isAlwaysShare || isNeverShare) {
				editText.setHintText(context.getString(R.string.SearchForPeopleAndGroups))
			}
			else {
				editText.setHintText(context.getString(R.string.search_contacts_and_usernames))
			}
		}
	}

	private fun showItemsAnimated(from: Int) {
		if (isPaused) {
			return
		}

		listView?.viewTreeObserver?.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
			override fun onPreDraw(): Boolean {
				val listView = listView ?: return true

				listView.viewTreeObserver?.removeOnPreDrawListener(this)

				val n = listView.childCount
				val animatorSet = AnimatorSet()

				for (i in 0 until n) {
					val child = listView.getChildAt(i)

					if (listView.getChildAdapterPosition(child) < from) {
						continue
					}

					child.alpha = 0f

					val s = min(listView.measuredHeight, max(0, child.top))
					val delay = (s / listView.measuredHeight.toFloat() * 100).toInt()

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

	override fun onResume() {
		super.onResume()
		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.contactsDidLoad -> {
				adapter?.notifyDataSetChanged()
			}

			NotificationCenter.updateInterfaces -> {
				val listView = listView ?: return
				val mask = args[0] as Int
				val count = listView.childCount

				if (mask and MessagesController.UPDATE_MASK_AVATAR != 0 || mask and MessagesController.UPDATE_MASK_NAME != 0 || mask and MessagesController.UPDATE_MASK_STATUS != 0) {
					for (a in 0 until count) {
						val child = listView.getChildAt(a)

						if (child is GroupCreateUserCell) {
							child.update(mask)
						}
					}
				}
			}

			NotificationCenter.chatDidCreated -> {
				removeSelfFromStack()
			}
		}
	}

	@Keep
	fun setContainerHeight(value: Int) {
		val dy = containerHeight - value
		containerHeight = value
		val measuredH = min(maxSize, measuredContainerHeight)
		val currentH = min(maxSize, containerHeight)
		scrollView?.scrollTo(0, max(0, scrollView!!.scrollY - dy))
		listView?.translationY = (currentH - measuredH).toFloat()
		fragmentView?.invalidate()
	}

	@Keep
	fun getContainerHeight(): Int {
		return containerHeight
	}

	private fun checkVisibleRows() {
		val listView = listView ?: return
		val count = listView.childCount

		for (a in 0 until count) {
			val child = listView.getChildAt(a)

			if (child is GroupCreateUserCell) {
				val id = when (val `object` = child.`object`) {
					is User -> `object`.id
					is TLRPC.Chat -> -`object`.id
					else -> 0L
				}

				if (id != 0L) {
					if (ignoreUsers != null && ignoreUsers!!.indexOfKey(id) >= 0) {
						child.setChecked(checked = true, animated = false)
						child.setCheckBoxEnabled(false)
					}
					else {
						child.setChecked(selectedContacts.indexOfKey(id) >= 0, true)
						child.setCheckBoxEnabled(true)
					}
				}
			}
		}
	}

	private fun onAddToGroupDone(count: Int) {
		val result = ArrayList<User>()

		for (a in 0 until selectedContacts.size()) {
			messagesController.getUser(selectedContacts.keyAt(a))?.let {
				result.add(it)
			}
		}

		delegate2?.didSelectUsers(result, count)

		finishFragment()
	}

	private fun onDonePressed(): Boolean {
		if (selectedContacts.size() == 0 && chatType != ChatObject.CHAT_TYPE_CHANNEL) {
			return false
		}

		if (addToGroup) {
			val parentActivity = parentActivity ?: return false
			val builder = AlertDialog.Builder(parentActivity)

			if (selectedContacts.size() == 1) {
				builder.setTitle(parentActivity.getString(R.string.AddOneMemberAlertTitle))
			}
			else {
				builder.setTitle(LocaleController.formatString("AddMembersAlertTitle", R.string.AddMembersAlertTitle, LocaleController.formatPluralString("Members", selectedContacts.size())))
			}

			val stringBuilder = StringBuilder()

			for (a in 0 until selectedContacts.size()) {
				val uid = selectedContacts.keyAt(a)
				val user = messagesController.getUser(uid) ?: continue

				if (stringBuilder.isNotEmpty()) {
					stringBuilder.append(", ")
				}

				stringBuilder.append("**").append(ContactsController.formatName(user.firstName, user.lastName)).append("**")
			}

			val chat = messagesController.getChat(if (chatId != 0L) chatId else channelId)

			if (selectedContacts.size() > 5) {
				val spannableStringBuilder = SpannableStringBuilder(AndroidUtilities.replaceTags(LocaleController.formatString("AddMembersAlertNamesText", R.string.AddMembersAlertNamesText, LocaleController.formatPluralString("Members", selectedContacts.size()), if (chat == null) "" else chat.title)))
				val countString = String.format(Locale.getDefault(), "%d", selectedContacts.size())
				val index = TextUtils.indexOf(spannableStringBuilder, countString)

				if (index >= 0) {
					spannableStringBuilder.setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), index, index + countString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}

				builder.setMessage(spannableStringBuilder)
			}
			else {
				builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("AddMembersAlertNamesText", R.string.AddMembersAlertNamesText, stringBuilder, if (chat == null) "" else chat.title)))
			}

			val cells = arrayOfNulls<CheckBoxCell>(1)

			if (!ChatObject.isChannel(chat)) {
				val linearLayout = LinearLayout(parentActivity)
				linearLayout.orientation = LinearLayout.VERTICAL

				cells[0] = CheckBoxCell(parentActivity, 1)
				cells[0]?.background = Theme.getSelectorDrawable(false)
				cells[0]?.setMultiline(true)

				if (selectedContacts.size() == 1) {
					val user = messagesController.getUser(selectedContacts.keyAt(0))
					cells[0]?.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AddOneMemberForwardMessages", R.string.AddOneMemberForwardMessages, getFirstName(user))), "", checked = true, divider = false)
				}
				else {
					cells[0]?.setText(parentActivity.getString(R.string.AddMembersForwardMessages), "", checked = true, divider = false)
				}

				cells[0]?.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(16f) else AndroidUtilities.dp(8f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(8f) else AndroidUtilities.dp(16f), 0)

				linearLayout.addView(cells[0], createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

				cells[0]?.setOnClickListener {
					cells[0]?.setChecked(!cells[0]!!.isChecked, true)
				}

				builder.setCustomViewOffset(12)
				builder.setView(linearLayout)
			}

			builder.setPositiveButton(parentActivity.getString(R.string.Add)) { _, _ ->
				onAddToGroupDone(if (cells[0]?.isChecked == true) 100 else 0)
			}

			builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

			showDialog(builder.create())
		}
		else {
			if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
				val isPublic = arguments?.getBoolean("isPublic") ?: false
				val isPaid = arguments?.getBoolean("isPaid") ?: false
				val inviteLink = arguments?.getString("inviteLink")
				val userIds = mutableSetOf<Long>()

				for (i in 0 until selectedContacts.size()) {
					userIds.add(selectedContacts.keyAt(i))
				}

				val args2 = Bundle()
				args2.putBoolean("isPublic", isPublic)
				args2.putBoolean("isPaid", isPaid)
				args2.putString("inviteLink", inviteLink)
				args2.putLongArray("users", userIds.toLongArray())

				if (isPaid) {
					presentFragment(ChannelPriceFragment(args2))
				}
				else {
					presentFragment(NewChannelSetupFragment(args2))
				}
			}
			else {
				if (!doneButtonVisible || selectedContacts.size() == 0) {
					return false
				}

				if (addToGroup) {
					onAddToGroupDone(0)
				}
				else {
					val result = ArrayList<Long>()

					for (a in 0 until selectedContacts.size()) {
						result.add(selectedContacts.keyAt(a))
					}

					if (isAlwaysShare || isNeverShare) {
						delegate?.didSelectUsers(result)
						finishFragment()
					}
					else {
						val args = Bundle()
						val array = LongArray(result.size)

						for (a in array.indices) {
							array[a] = result[a]
						}

						args.putLongArray("result", array)
						args.putInt("chatType", chatType)
						args.putBoolean("forImport", forImport)

						presentFragment(GroupCreateFinalActivity(args))
					}
				}
			}
		}

		return true
	}

	private fun closeSearch() {
		searching = false
		searchWas = false

		itemDecoration?.setSearching(false)

		adapter?.searching = false
		adapter?.searchDialogs(null)

		listView?.setFastScrollVisible(true)
		listView?.isVerticalScrollBarEnabled = false

		showItemsAnimated(0)
	}

	private fun updateHint() {
		if (!isAlwaysShare && !isNeverShare && !addToGroup) {
			if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
				actionBar?.setSubtitle(context?.getString(R.string.unlimited_members))
//				actionBar?.setSubtitle(LocaleController.formatPluralString("Members", selectedContacts.size()))
			}
			else {
				actionBar?.setSubtitle(context?.getString(R.string.MembersCount))
//				if (selectedContacts.size() == 0) {
//					actionBar?.setSubtitle(LocaleController.formatString("MembersCountZero", R.string.MembersCountZero, LocaleController.formatPluralString("Members", maxCount)))
//				}
//				else {
//					val str = LocaleController.getPluralString("MembersCountSelected", selectedContacts.size())
//					actionBar?.setSubtitle(String.format(str, selectedContacts.size(), maxCount))
//				}
			}
		}

		if (chatType != ChatObject.CHAT_TYPE_CHANNEL) {
			if (doneButtonVisible && allSpans.isEmpty()) {
				doneItem?.gone()
				doneButtonVisible = false
			}
			else if (!doneButtonVisible && allSpans.isNotEmpty()) {
				doneItem?.visible()
				doneButtonVisible = true
			}
		}
	}

	fun setDelegate(groupCreateActivityDelegate: GroupCreateActivityDelegate?) {
		delegate = groupCreateActivityDelegate
	}

	fun setDelegate(contactsAddActivityDelegate: ContactsAddActivityDelegate?) {
		delegate2 = contactsAddActivityDelegate
	}

	inner class GroupCreateAdapter(private val context: Context) : FastScrollAdapter() {
		private var searchResult = ArrayList<Any>()
		private var searchResultNames = ArrayList<CharSequence>()
		private val searchAdapterHelper: SearchAdapterHelper
		private var searchRunnable: Runnable? = null
		private val contacts = ArrayList<TLObject>()
		private var usersStartRow = 0
		private var currentItemsCount = 0
		var inviteViaLink = 0
		var noContactsStubRow = 0

		var searching = false
			set(value) {
				if (field == value) {
					return
				}

				field = value

				notifyDataSetChanged()
			}

		override fun notifyDataSetChanged() {
			super.notifyDataSetChanged()
			updateEditTextHint()
		}

		init {
			val arrayList = contactsController.contacts

			for (a in arrayList.indices) {
				val user = messagesController.getUser(arrayList[a].userId)

				if (user == null || user.isSelf || user.deleted) {
					continue
				}

				contacts.add(user)
			}

			if (isNeverShare || isAlwaysShare) {
				val dialogs = messagesController.allDialogs
				var a = 0
				val n = dialogs.size

				while (a < n) {
					val dialog = dialogs[a]

					if (!DialogObject.isChatDialog(dialog.id)) {
						a++
						continue
					}

					val chat = messagesController.getChat(-dialog.id)

					if (chat == null || chat.migratedTo != null || ChatObject.isChannel(chat) && !chat.megagroup) {
						a++
						continue
					}

					contacts.add(chat)

					a++
				}

				Collections.sort(contacts, object : Comparator<TLObject> {
					private fun getName(`object`: TLObject): String {
						return if (`object` is User) {
							ContactsController.formatName(`object`.firstName, `object`.lastName)
						}
						else {
							val chat = `object` as TLRPC.Chat
							chat.title ?: ""
						}
					}

					override fun compare(o1: TLObject, o2: TLObject): Int {
						return getName(o1).compareTo(getName(o2))
					}
				})
			}

			searchAdapterHelper = SearchAdapterHelper(false)

			searchAdapterHelper.setDelegate(object : SearchAdapterHelperDelegate {
				override fun canApplySearchResults(searchId: Int): Boolean {
					return true
				}

				override val excludeCallParticipants: LongSparseArray<TLRPC.TLGroupCallParticipant>?
					get() = null

				override val excludeUsers: LongSparseArray<User>?
					get() = null

				override fun onSetHashtags(arrayList: ArrayList<HashtagObject>?, hashMap: HashMap<String, HashtagObject>?) {
					// unused
				}

				override fun onDataSetChanged(searchId: Int) {
					showItemsAnimated(currentItemsCount)

					if (searchRunnable == null && !searchAdapterHelper.isSearchInProgress && itemCount == 0) {
						emptyView?.showProgress(show = false, animated = true)
					}

					notifyDataSetChanged()
				}
			})
		}

		override fun getLetter(position: Int): String? {
			if (searching || position < usersStartRow || position >= contacts.size + usersStartRow) {
				return null
			}

			val `object` = contacts[position - usersStartRow]
			val firstName: String?
			val lastName: String?

			if (`object` is User) {
				firstName = `object`.firstName
				lastName = `object`.lastName
			}
			else {
				val chat = `object` as TLRPC.Chat
				firstName = chat.title
				lastName = ""
			}

			if (LocaleController.nameDisplayOrder == 1) {
				if (!firstName.isNullOrEmpty()) {
					return firstName.substring(0, 1).uppercase()
				}
				else if (!lastName.isNullOrEmpty()) {
					return lastName.substring(0, 1).uppercase()
				}
			}
			else {
				if (!lastName.isNullOrEmpty()) {
					return lastName.substring(0, 1).uppercase()
				}
				else if (!firstName.isNullOrEmpty()) {
					return firstName.substring(0, 1).uppercase()
				}
			}

			return ""
		}

		override fun getItemCount(): Int {
			var count: Int

			noContactsStubRow = -1

			if (searching) {
				count = searchResult.size

				val localServerCount = searchAdapterHelper.localServerSearch.size
				val globalCount = searchAdapterHelper.globalSearch.size

				count += localServerCount

				if (globalCount != 0) {
					count += globalCount + 1
				}

				currentItemsCount = count

				return count
			}
			else {
				count = contacts.size

				if (addToGroup) {
					inviteViaLink = if (chatId != 0L) {
						val chat = messagesController.getChat(chatId)
						if (ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE)) 1 else 0
					}
					else if (channelId != 0L) {
						val chat = messagesController.getChat(channelId)
						if (ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE) && chat.username.isNullOrEmpty()) 2 else 0
					}
					else {
						0
					}

					if (inviteViaLink != 0) {
						usersStartRow = 1
						count++
					}
				}

				if (count == 0) {
					noContactsStubRow = 0
					count++
				}
			}

			currentItemsCount = count

			return count
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = GroupCreateSectionCell(context)
				}

				1 -> {
					view = GroupCreateUserCell(context, 1, 0, false)
				}

				3 -> {
					val scrollView = ScrollView(context)

					val buttonContainer = LinearLayout(context)
					buttonContainer.orientation = LinearLayout.VERTICAL

					val stickerEmptyView = object : StickerEmptyView(context, null, STICKER_TYPE_NO_CONTACTS, animationResource = R.raw.panda_no_contacts) {
						override fun onAttachedToWindow() {
							super.onAttachedToWindow()
							stickerView.imageReceiver.startAnimation()
						}
					}
					stickerEmptyView.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
						topMargin = 30.dp
					}
					stickerEmptyView.subtitle.visibility = View.VISIBLE

					when {
						chatType == ChatObject.CHAT_TYPE_CHANNEL && isPublic -> {
							setGroupOrChannelParameters(stickerEmptyView, R.string.ways_to_add_subscribers_description, R.string.adding_subscribers_to_channel)
							initPromoteChannelButton(buttonContainer)
						}

						chatType == ChatObject.CHAT_TYPE_CHANNEL -> {
							setGroupOrChannelParameters(stickerEmptyView, R.string.invite_users_description, R.string.how_to_add_users_to_channel)
						}

						else -> {
							setGroupOrChannelParameters(stickerEmptyView, R.string.new_group_empty_contacts_description, R.string.how_to_add_users_to_group)
						}
					}

					stickerEmptyView.subtitle.gravity = Gravity.START
					stickerEmptyView.setAnimateLayoutChange(true)
					stickerEmptyView.setPadding(12, 0, 12, 0)

					val stickerEmptyContent = LinearLayout(context)
					stickerEmptyContent.orientation = LinearLayout.VERTICAL

					stickerEmptyContent.addView(stickerEmptyView)
					stickerEmptyContent.addView(buttonContainer)

					scrollView.addView(stickerEmptyContent)
					view = scrollView
				}

				2 -> {
					view = TextCell(context)
				}

				else -> {
					view = TextCell(context)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				0 -> {
					val cell = holder.itemView as GroupCreateSectionCell

					if (searching) {
						cell.setText(context.getString(R.string.GlobalSearch))
					}
				}

				1 -> {
					val cell = holder.itemView as GroupCreateUserCell
					val `object`: TLObject?
					var username: CharSequence? = null
					var name: CharSequence? = null

					if (searching) {
						val localCount = searchResult.size
						val globalCount = searchAdapterHelper.globalSearch.size
						val localServerCount = searchAdapterHelper.localServerSearch.size

						`object` = if (position in 0 until localCount) {
							searchResult[position] as TLObject
						}
						else if (position >= localCount && position < localServerCount + localCount) {
							searchAdapterHelper.localServerSearch[position - localCount]
						}
						else if (position > localCount + localServerCount && position <= globalCount + localCount + localServerCount) {
							searchAdapterHelper.globalSearch[position - localCount - localServerCount - 1]
						}
						else {
							null
						}

						if (`object` != null) {
							val objectUserName = if (`object` is User) {
								`object`.username
							}
							else {
								(`object` as TLRPC.Chat).username
							}

							if (position < localCount) {
								name = searchResultNames.getOrNull(position)

								if (name != null && !objectUserName.isNullOrEmpty()) {
									if (name.toString().startsWith("@$objectUserName")) {
										username = name
										name = null
									}
								}
							}
							else if (position > localCount && !objectUserName.isNullOrEmpty()) {
								var foundUserName = searchAdapterHelper.lastFoundUsername

								if (foundUserName?.startsWith("@") == true) {
									foundUserName = foundUserName.substring(1)
								}

								try {
									var index: Int

									val spannableStringBuilder = SpannableStringBuilder()
									spannableStringBuilder.append("@")
									spannableStringBuilder.append(objectUserName)

									if (AndroidUtilities.indexOfIgnoreCase(objectUserName, foundUserName).also { index = it } != -1) {
										var len = foundUserName?.length ?: 0

										if (index == 0) {
											len++
										}
										else {
											index++
										}

										spannableStringBuilder.setSpan(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, R.color.brand, null)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
									}

									username = spannableStringBuilder
								}
								catch (e: Exception) {
									username = objectUserName
								}
							}
						}
					}
					else {
						`object` = contacts[position - usersStartRow]
					}

					cell.setObject(`object`, name, username)

					val id = when (`object`) {
						is User -> `object`.id
						is TLRPC.Chat -> -`object`.id
						else -> 0L
					}

					if (id != 0L) {
						if (ignoreUsers != null && ignoreUsers!!.indexOfKey(id) >= 0) {
							cell.setChecked(checked = true, animated = false)
							cell.setCheckBoxEnabled(false)
						}
						else {
							cell.setChecked(selectedContacts.indexOfKey(id) >= 0, false)
							cell.setCheckBoxEnabled(true)
						}
					}
				}

				2 -> {
					val textCell = holder.itemView as TextCell

					if (inviteViaLink == 2) {
						textCell.setTextAndIcon(context.getString(R.string.ChannelInviteViaLink), R.drawable.msg_link2, false)
					}
					else {
						textCell.setTextAndIcon(context.getString(R.string.InviteToGroupByLink), R.drawable.msg_link2, false)
					}
				}
			}
		}

		private fun setGroupOrChannelParameters(stickerEmptyView: StickerEmptyView, subtitle: Int, title: Int) {
			stickerEmptyView.subtitle.text = context.getString(subtitle)
			stickerEmptyView.title.text = context.getString(title)
		}

		private fun initPromoteChannelButton(buttonContainer: LinearLayout) {
			val button = LayoutInflater.from(context).inflate(R.layout.item_promote_channel_button, buttonContainer, false) as MaterialButton

			button.setOnClickListener {
				inviteLink?.let { link ->
					val intent = Intent(Intent.ACTION_SEND)
					intent.type = "text/plain"

					val fullLink = if (link.contains("https://") || (link.contains("http://"))) {
						link
					}
					else {
						"https://${it.context.getString(R.string.domain)}/$link"
					}

					intent.putExtra(Intent.EXTRA_TEXT, fullLink)

					try {
						context.startActivity(Intent.createChooser(intent, context.getString(R.string.promote_channel)))
					}
					catch (e: ActivityNotFoundException) {
						Toast.makeText(context, context.getString(R.string.error_no_suitable_mail_app), Toast.LENGTH_SHORT).show()
					}
				}
			}

			buttonContainer.addView(button)
		}

		override fun getItemViewType(position: Int): Int {
			return if (searching) {
				if (position == searchResult.size + searchAdapterHelper.localServerSearch.size) {
					0
				}
				else {
					1
				}
			}
			else {
				if (inviteViaLink != 0 && position == 0) {
					2
				}
				else if (noContactsStubRow == position) {
					3
				}
				else {
					1
				}
			}
		}

		override fun getPositionForScrollProgress(listView: RecyclerListView, progress: Float, position: IntArray) {
			position[0] = (itemCount * progress).toInt()
			position[1] = 0
		}

		override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
			if (holder.itemView is GroupCreateUserCell) {
				holder.itemView.recycle()
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			if (ignoreUsers != null && holder.itemView is GroupCreateUserCell) {
				val `object` = holder.itemView.`object`

				if (`object` is User) {
					return ignoreUsers!!.indexOfKey(`object`.id) < 0
				}
			}

			return true
		}

		fun searchDialogs(query: String?) {
			if (searchRunnable != null) {
				Utilities.searchQueue.cancelRunnable(searchRunnable)
				searchRunnable = null
			}

			searchResult.clear()
			searchResultNames.clear()
			searchAdapterHelper.mergeResults(null)
			searchAdapterHelper.queryServerSearch(query = null, allowUsername = true, allowChats = isAlwaysShare || isNeverShare, allowBots = false, allowSelf = false, canAddGroupsOnly = false, channelId = 0, type = 0, searchId = 0)

			notifyDataSetChanged()

			if (!query.isNullOrEmpty()) {
				Utilities.searchQueue.postRunnable(Runnable {
					AndroidUtilities.runOnUIThread {
						searchAdapterHelper.queryServerSearch(query = query, allowUsername = true, allowChats = isAlwaysShare || isNeverShare, allowBots = true, allowSelf = false, canAddGroupsOnly = false, channelId = 0, type = 0, searchId = 0)

						Utilities.searchQueue.postRunnable(Runnable label@{
							val search1 = query.trim { it <= ' ' }.lowercase(Locale.getDefault())

							if (search1.isEmpty()) {
								updateSearchResults(ArrayList(), ArrayList())
								return@label
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

							val resultArray = ArrayList<Any>()
							val resultArrayNames = ArrayList<CharSequence>()
							var a = 0

							while (a < contacts.size) {
								val `object` = contacts[a]
								var name: String
								var username: String?

								if (`object` is User) {
									name = ContactsController.formatName(`object`.firstName, `object`.lastName).lowercase(Locale.getDefault())
									username = `object`.username
								}
								else {
									val chat = `object` as TLRPC.Chat
									name = chat.title ?: ""
									username = chat.username
								}

								var tName = LocaleController.getInstance().getTranslitString(name)

								if (name == tName) {
									tName = null
								}

								var found = 0

								for (q in search) {
									if (name.startsWith(q!!) || name.contains(" $q") || tName != null && (tName.startsWith(q) || tName.contains(" $q"))) {
										found = 1
									}
									else if (username != null && username.startsWith(q)) {
										found = 2
									}

									if (found != 0) {
										if (found == 1) {
											if (`object` is User) {
												resultArrayNames.add(AndroidUtilities.generateSearchName(`object`.firstName, `object`.lastName, q))
											}
											else {
												val chat = `object` as TLRPC.Chat
												resultArrayNames.add(AndroidUtilities.generateSearchName(chat.title, null, q))
											}
										}
										else {
											resultArrayNames.add(AndroidUtilities.generateSearchName("@$username", null, "@$q"))
										}

										resultArray.add(`object`)

										break
									}
								}

								a++
							}

							updateSearchResults(resultArray, resultArrayNames)
						}.also {
							searchRunnable = it
						})
					}
				}.also {
					searchRunnable = it
				}, 300)
			}
		}

		private fun updateSearchResults(users: ArrayList<Any>, names: ArrayList<CharSequence>) {
			AndroidUtilities.runOnUIThread {
				if (!searching) {
					return@runOnUIThread
				}

				searchRunnable = null
				searchResult = users
				searchResultNames = names

				searchAdapterHelper.mergeResults(searchResult)

				showItemsAnimated(currentItemsCount)

				notifyDataSetChanged()

				if (searching && !searchAdapterHelper.isSearchInProgress && itemCount == 0) {
					emptyView?.showProgress(show = false, animated = true)
				}
			}
		}
	}

	companion object {
		private const val BUTTON_DONE = 1
	}
}
