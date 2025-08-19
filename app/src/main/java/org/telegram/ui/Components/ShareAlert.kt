/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.collection.LongSparseArray
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isNotEmpty
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ChatObject.hasAdminRights
import org.telegram.messenger.ChatObject.isActionBannedByDefault
import org.telegram.messenger.ChatObject.isCanWriteToChannel
import org.telegram.messenger.ChatObject.isChannel
import org.telegram.messenger.ChatObject.isNotInChat
import org.telegram.messenger.ContactsController.Companion.formatName
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject.getFirstName
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.EncryptedChat
import org.telegram.tgnet.TLRPC.TLChannelsExportMessageLink
import org.telegram.tgnet.TLRPC.TLDialog
import org.telegram.tgnet.TLRPC.TLEncryptedChat
import org.telegram.tgnet.TLRPC.TLExportedMessageLink
import org.telegram.tgnet.TLRPC.TLGroupCallParticipant
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.channelId
import org.telegram.tgnet.chatId
import org.telegram.tgnet.forwards
import org.telegram.tgnet.setStatusFromExpires
import org.telegram.tgnet.userId
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.DialogsSearchAdapter
import org.telegram.ui.Adapters.DialogsSearchAdapter.Companion.loadRecentSearch
import org.telegram.ui.Adapters.DialogsSearchAdapter.OnRecentSearchLoaded
import org.telegram.ui.Adapters.DialogsSearchAdapter.RecentSearchObject
import org.telegram.ui.Adapters.SearchAdapterHelper
import org.telegram.ui.Adapters.SearchAdapterHelper.HashtagObject
import org.telegram.ui.Adapters.SearchAdapterHelper.SearchAdapterHelperDelegate
import org.telegram.ui.Cells.GraySectionCell
import org.telegram.ui.Cells.HintDialogCell
import org.telegram.ui.Cells.ShareDialogCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.DialogsActivity.Companion.loadDialogs
import org.telegram.ui.LaunchActivity
import org.telegram.ui.statistics.MessageStatisticActivity
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
open class ShareAlert(context: Context, fragment: ChatActivity?, messages: List<MessageObject>?, text: String?, text2: String?, channel: Boolean, copyLink: String?, copyLink2: String?, fullScreen: Boolean, forCall: Boolean) : BottomSheet(context, true), NotificationCenterDelegate {
	private lateinit var frameLayout: FrameLayout
	private lateinit var frameLayout2: FrameLayout
	private lateinit var commentTextView: EditTextEmoji
	private lateinit var writeButtonContainer: FrameLayout
	private lateinit var selectedCountView: View

	private val gridView = object : RecyclerListView(context) {
		override fun allowSelectChildAtPosition(x: Float, y: Float): Boolean {
			return y >= AndroidUtilities.dp((if (darkTheme && linkToCopy[1] != null) 111 else 58).toFloat()) + AndroidUtilities.statusBarHeight
		}
	}

	private lateinit var searchGridView: RecyclerListView
	private val layoutManager = GridLayoutManager(context, 4)
	private var searchLayoutManager: FillLastGridLayoutManager? = null
	private val listAdapter = ShareDialogsAdapter(context)
	private val searchAdapter: ShareSearchAdapter?
	private val sendingText = arrayOfNulls<String>(2)
	private lateinit var searchEmptyView: StickerEmptyView
	private val shadow = arrayOfNulls<View>(2)
	private val shadowAnimation = arrayOfNulls<AnimatorSet>(2)
	private val parentFragment: ChatActivity?
	private val darkTheme: Boolean
	private val rect = RectF()
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val isChannel: Boolean
	private val linkToCopy = arrayOfNulls<String>(2)
	private var sendingMessageObjects: List<MessageObject>?
	protected var selectedDialogs = LongSparseArray<TLRPC.Dialog>()
	var recyclerItemsEnterAnimator: RecyclerItemsEnterAnimator
	private var searchView: SearchField
	private var lastOffset = Int.MAX_VALUE
	private var pickerBottomLayout: TextView? = null
	private var sharesCountLayout: LinearLayout? = null
	private var animatorSet: AnimatorSet? = null
	private var hasPoll = 0
	private var switchView: SwitchView? = null
	private var containerViewTop = -1
	private var fullyShown = false
	private var parentActivity: Activity? = null
	private var exportedMessageLink: TLExportedMessageLink? = null
	private var loadingLink = false
	private var copyLinkOnEnd = false
	private var scrollOffsetY = 0
	private var previousScrollOffsetY = 0
	private var panTranslationMoveLayout = false
	private var shareAlertDelegate: ShareAlertDelegate? = null

	fun setDelegate(delegate: ShareAlertDelegate?) {
		this.shareAlertDelegate = delegate
	}

	private var currentPanTranslationY = 0f
	private var captionEditTextTopOffset = 0f
	private var chatActivityEnterViewAnimateFromTop = 0f
	private var topBackgroundAnimator: ValueAnimator? = null
	private var updateSearchAdapter = false
	private var recentSearchObjects = ArrayList<RecentSearchObject>()
	private var showSendersName = true
	private var sendPopupWindow: ActionBarPopupWindow? = null
	private var searchIsVisible = false

	constructor(context: Context, messages: ArrayList<MessageObject>?, text: String?, channel: Boolean, copyLink: String?, fullScreen: Boolean) : this(context, null, messages, text, null, channel, copyLink, null, fullScreen, false)

	init {
		if (context is Activity) {
			parentActivity = context
		}

		shadowDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.sheet_shadow_round, null)!!.mutate()

		darkTheme = forCall
		parentFragment = fragment
		behindKeyboardColor = if (darkTheme) context.getColor(R.color.dark_background) else context.getColor(R.color.background)

		val backgroundColor = behindKeyboardColor

		shadowDrawable.colorFilter = PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY)

		fixNavigationBar(backgroundColor)

		isFullscreen = fullScreen

		linkToCopy[0] = copyLink
		linkToCopy[1] = copyLink2

		sendingMessageObjects = messages

		searchAdapter = ShareSearchAdapter(context)

		isChannel = channel

		sendingText[0] = text
		sendingText[1] = text2

		useSmoothKeyboard = true

		super.setDelegate(object : BottomSheetDelegate() {
			override fun onOpenAnimationEnd() {
				fullyShown = true
			}
		})

		sendingMessageObjects?.let { sendingMessageObjects ->
			for (messageObject in sendingMessageObjects) {
				hasPoll = if (messageObject.isPublicPoll) 2 else 1

				if (hasPoll == 2) {
					break
				}
			}
		}

		if (channel) {
			loadingLink = true

			val req = TLChannelsExportMessageLink()
			req.id = messages?.firstOrNull()?.id ?: 0
			req.channel = MessagesController.getInstance(currentAccount).getInputChannel(messages?.firstOrNull()?.messageOwner?.peerId?.channelId ?: 0)

			ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					if (response != null) {
						exportedMessageLink = response as TLExportedMessageLink?

						if (copyLinkOnEnd) {
							copyLink()
						}
					}

					loadingLink = false
				}
			}
		}

		val sizeNotifierFrameLayout = object : SizeNotifierFrameLayout(context) {
			private val rect1 = RectF()
			private val lightStatusBar = AndroidUtilities.computePerceivedBrightness(if (darkTheme) context.getColor(R.color.dark_background) else context.getColor(R.color.background)) > .721f
			private var ignoreLayout = false
			private var fullHeight = false
			private var topOffset = 0
			private var previousTopOffset = 0
			private var fromScrollY = 0
			private var toScrollY = 0
			private var fromOffsetTop = 0
			private var toOffsetTop = 0

			override fun createAdjustPanLayoutHelper(): AdjustPanLayoutHelper {
				return object : AdjustPanLayoutHelper(this) {
					override fun onTransitionStart(keyboardVisible: Boolean, contentHeight: Int) {
						super.onTransitionStart(keyboardVisible, contentHeight)
						if (previousScrollOffsetY != scrollOffsetY) {
							fromScrollY = previousScrollOffsetY
							toScrollY = scrollOffsetY
							panTranslationMoveLayout = true
							scrollOffsetY = fromScrollY
						}
						else {
							fromScrollY = -1
						}

						if (topOffset != previousTopOffset) {
							fromOffsetTop = 0
							toOffsetTop = 0
							panTranslationMoveLayout = true

							if (!keyboardVisible) {
								toOffsetTop -= topOffset - previousTopOffset
							}
							else {
								toOffsetTop += topOffset - previousTopOffset
							}

							scrollOffsetY = if (keyboardVisible) fromScrollY else toScrollY
						}
						else {
							fromOffsetTop = -1
						}

						gridView.topGlowOffset = (currentPanTranslationY + scrollOffsetY).toInt()
						frameLayout.translationY = currentPanTranslationY + scrollOffsetY
						searchEmptyView.translationY = currentPanTranslationY + scrollOffsetY

						invalidate()
					}

					override fun onTransitionEnd() {
						super.onTransitionEnd()

						panTranslationMoveLayout = false
						previousScrollOffsetY = scrollOffsetY
						gridView.topGlowOffset = scrollOffsetY
						frameLayout.translationY = scrollOffsetY.toFloat()
						searchEmptyView.translationY = scrollOffsetY.toFloat()
						gridView.translationY = 0f
						searchGridView.translationY = 0f
					}

					override fun onPanTranslationUpdate(y: Float, progress: Float, keyboardVisible: Boolean) {
						super.onPanTranslationUpdate(y, progress, keyboardVisible)

						for (i in 0 until containerView.childCount) {
							if (containerView.getChildAt(i) !== pickerBottomLayout && containerView.getChildAt(i) !== shadow[1] && containerView.getChildAt(i) !== sharesCountLayout && containerView.getChildAt(i) !== frameLayout2 && containerView.getChildAt(i) !== writeButtonContainer && containerView.getChildAt(i) !== selectedCountView) {
								containerView.getChildAt(i).translationY = y
							}
						}

						currentPanTranslationY = y

						if (fromScrollY != -1) {
							val p = if (keyboardVisible) progress else 1f - progress

							scrollOffsetY = (fromScrollY * (1f - p) + toScrollY * p).toInt()

							val translationY = currentPanTranslationY + (fromScrollY - toScrollY) * (1f - p)

							gridView.translationY = translationY

							if (keyboardVisible) {
								searchGridView.translationY = translationY
							}
							else {
								searchGridView.translationY = translationY + gridView.paddingTop
							}
						}
						else if (fromOffsetTop != -1) {
							scrollOffsetY = (fromOffsetTop * (1f - progress) + toOffsetTop * progress).toInt()

							val p = if (keyboardVisible) 1f - progress else progress

							if (keyboardVisible) {
								gridView.translationY = currentPanTranslationY - (fromOffsetTop - toOffsetTop) * progress
							}
							else {
								gridView.translationY = currentPanTranslationY + (toOffsetTop - fromOffsetTop) * p
							}
						}

						gridView.topGlowOffset = (scrollOffsetY + currentPanTranslationY).toInt()
						frameLayout.translationY = scrollOffsetY + currentPanTranslationY
						searchEmptyView.translationY = scrollOffsetY + currentPanTranslationY

						frameLayout2.invalidate()

						setCurrentPanTranslationY(currentPanTranslationY)

						invalidate()
					}

					override fun heightAnimationEnabled(): Boolean {
						return if (isDismissed || !fullyShown) {
							false
						}
						else {
							!commentTextView.isPopupVisible
						}
					}
				}
			}

			override fun onAttachedToWindow() {
				super.onAttachedToWindow()
				adjustPanLayoutHelper?.setResizableView(this)
				adjustPanLayoutHelper?.onAttach()
			}

			override fun onDetachedFromWindow() {
				super.onDetachedFromWindow()
				adjustPanLayoutHelper?.onDetach()
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val totalHeight = if (layoutParams.height > 0) {
					layoutParams.height
				}
				else {
					MeasureSpec.getSize(heightMeasureSpec)
				}

				layoutManager.setNeedFixGap(layoutParams.height <= 0)
				searchLayoutManager?.setNeedFixGap(layoutParams.height <= 0)

				if (!isFullscreen) {
					ignoreLayout = true

					setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0)

					ignoreLayout = false
				}

				val availableHeight = totalHeight - paddingTop
				val size = max(searchAdapter.itemCount, listAdapter.itemCount - 1)
				val contentSize = AndroidUtilities.dp(103f) + AndroidUtilities.dp(48f) + max(2, ceil((size / 4.0f).toDouble()).toInt()) * AndroidUtilities.dp(103f) + backgroundPaddingTop
				val padding = (if (contentSize < availableHeight) 0 else availableHeight - availableHeight / 5 * 3) + AndroidUtilities.dp(8f)

				if (gridView.paddingTop != padding) {
					ignoreLayout = true
					gridView.setPadding(0, padding, 0, AndroidUtilities.dp(48f))
					ignoreLayout = false
				}

				if (keyboardVisible && layoutParams.height <= 0 && searchGridView.paddingTop != padding) {
					ignoreLayout = true
					searchGridView.setPadding(0, 0, 0, AndroidUtilities.dp(48f))
				}

				fullHeight = contentSize >= totalHeight
				topOffset = if (fullHeight || !SharedConfig.smoothKeyboard) 0 else totalHeight - contentSize
				ignoreLayout = true

				checkCurrentList(false)

				ignoreLayout = false

				setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight)
				onMeasureInternal(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY))
			}

			private fun onMeasureInternal(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				@Suppress("NAME_SHADOWING") var heightMeasureSpec = heightMeasureSpec
				var widthSize = MeasureSpec.getSize(widthMeasureSpec)
				var heightSize = MeasureSpec.getSize(heightMeasureSpec)
				widthSize -= backgroundPaddingLeft * 2
				val keyboardSize = if (SharedConfig.smoothKeyboard) 0 else measureKeyboardHeight()

				if (!commentTextView.isWaitingForKeyboardOpen && keyboardSize <= AndroidUtilities.dp(20f) && !commentTextView.isPopupShowing && !commentTextView.isAnimatePopupClosing) {
					ignoreLayout = true
					commentTextView.hideEmojiView()
				}

				ignoreLayout = true

				if (keyboardSize <= AndroidUtilities.dp(20f)) {
					if (!AndroidUtilities.isInMultiwindow) {
						val paddingBottom = if (SharedConfig.smoothKeyboard && keyboardVisible) {
							0
						}
						else {
							commentTextView.emojiPadding
						}

						heightSize -= paddingBottom
						heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY)
					}

					val visibility = if (commentTextView.isPopupShowing) GONE else VISIBLE

					pickerBottomLayout?.visibility = visibility
					sharesCountLayout?.visibility = visibility
				}
				else {
					commentTextView.hideEmojiView()
					pickerBottomLayout?.visibility = GONE
					sharesCountLayout?.visibility = GONE
				}

				ignoreLayout = false

				val childCount = childCount

				for (i in 0 until childCount) {
					val child = getChildAt(i)

					if (child == null || child.isGone) {
						continue
					}

					if (commentTextView.isPopupView(child)) {
						if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
							if (AndroidUtilities.isTablet()) {
								child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(min(AndroidUtilities.dp((if (AndroidUtilities.isTablet()) 200 else 320).toFloat()), heightSize - AndroidUtilities.statusBarHeight + paddingTop), MeasureSpec.EXACTLY))
							}
							else {
								child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + paddingTop, MeasureSpec.EXACTLY))
							}
						}
						else {
							child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.layoutParams.height, MeasureSpec.EXACTLY))
						}
					}
					else {
						measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
					}
				}
			}

			override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
				val count = childCount
				val keyboardSize = measureKeyboardHeight()

				val paddingBottom = if (SharedConfig.smoothKeyboard && keyboardVisible) {
					0
				}
				else {
					if (keyboardSize <= AndroidUtilities.dp(20f) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) commentTextView.emojiPadding else 0
				}

				setBottomClip(paddingBottom)

				for (i in 0 until count) {
					val child = getChildAt(i)

					if (child.isGone) {
						continue
					}

					val lp = child.layoutParams as LayoutParams
					val width = child.measuredWidth
					val height = child.measuredHeight
					var childLeft: Int
					var childTop: Int
					var gravity = lp.gravity

					if (gravity == -1) {
						gravity = Gravity.TOP or Gravity.LEFT
					}

					val absoluteGravity = gravity and Gravity.HORIZONTAL_GRAVITY_MASK
					val verticalGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK

					childLeft = when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
						Gravity.CENTER_HORIZONTAL -> (r - l - width) / 2 + lp.leftMargin - lp.rightMargin
						Gravity.RIGHT -> r - l - width - lp.rightMargin - paddingRight - backgroundPaddingLeft
						Gravity.LEFT -> lp.leftMargin + paddingLeft
						else -> lp.leftMargin + paddingLeft
					}
					childTop = when (verticalGravity) {
						Gravity.TOP -> lp.topMargin + paddingTop + topOffset
						Gravity.CENTER_VERTICAL -> (b - paddingBottom - (t + topOffset) - height) / 2 + lp.topMargin - lp.bottomMargin
						Gravity.BOTTOM -> b - paddingBottom - t - height - lp.bottomMargin
						else -> lp.topMargin
					}

					if (commentTextView.isPopupView(child)) {
						childTop = if (AndroidUtilities.isTablet()) {
							measuredHeight - child.measuredHeight
						}
						else {
							measuredHeight + keyboardSize - child.measuredHeight
						}
					}

					child.layout(childLeft, childTop, childLeft + width, childTop + height)
				}

				notifyHeightChanged()
				updateLayout()
			}

			override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
				if (!fullHeight) {
					if (ev.action == MotionEvent.ACTION_DOWN && ev.y < topOffset - AndroidUtilities.dp(30f)) {
						dismiss()
						return true
					}
				}
				else {
					if (ev.action == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.y < scrollOffsetY - AndroidUtilities.dp(30f)) {
						dismiss()
						return true
					}
				}

				return super.onInterceptTouchEvent(ev)
			}

			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(e: MotionEvent): Boolean {
				return !isDismissed && super.onTouchEvent(e)
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}

			override fun onDraw(canvas: Canvas) {
				canvas.withTranslation(0f, currentPanTranslationY) {
					var y = scrollOffsetY - backgroundPaddingTop + AndroidUtilities.dp(6f) + topOffset

					containerViewTop = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(13f) + topOffset

					var top = containerViewTop
					var height = measuredHeight + AndroidUtilities.dp((30 + 30).toFloat()) + backgroundPaddingTop
					var statusBarHeight = 0
					var radProgress = 1.0f

					if (!isFullscreen) {
						top += AndroidUtilities.statusBarHeight
						y += AndroidUtilities.statusBarHeight
						height -= AndroidUtilities.statusBarHeight

						if (fullHeight) {
							if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight * 2) {
								val diff = min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop)
								top -= diff
								height += diff
								radProgress = 1.0f - min(1.0f, diff * 2 / AndroidUtilities.statusBarHeight.toFloat())
							}

							if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
								statusBarHeight = min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop)
							}
						}
					}

					shadowDrawable.setBounds(0, top, measuredWidth, height)
					shadowDrawable.draw(this)

					if (radProgress != 1.0f) {
						Theme.dialogs_onlineCirclePaint.color = if (darkTheme) context.getColor(R.color.dark_background) else context.getColor(R.color.background)
						rect1.set(backgroundPaddingLeft.toFloat(), (backgroundPaddingTop + top).toFloat(), (measuredWidth - backgroundPaddingLeft).toFloat(), (backgroundPaddingTop + top + AndroidUtilities.dp(24f)).toFloat())
						drawRoundRect(rect1, AndroidUtilities.dp(12f) * radProgress, AndroidUtilities.dp(12f) * radProgress, Theme.dialogs_onlineCirclePaint)
					}

					val w = AndroidUtilities.dp(36f)

					rect1.set(((measuredWidth - w) / 2).toFloat(), y.toFloat(), ((measuredWidth + w) / 2).toFloat(), (y + AndroidUtilities.dp(4f)).toFloat())

					Theme.dialogs_onlineCirclePaint.color = context.getColor(R.color.light_background)

					drawRoundRect(rect1, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), Theme.dialogs_onlineCirclePaint)

					var flags = systemUiVisibility
					val shouldBeLightStatusBar = lightStatusBar && statusBarHeight > AndroidUtilities.statusBarHeight * .5f
					val isLightStatusBar = flags and SYSTEM_UI_FLAG_LIGHT_STATUS_BAR > 0

					if (shouldBeLightStatusBar != isLightStatusBar) {
						flags = if (shouldBeLightStatusBar) {
							flags or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
						}
						else {
							flags and SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
						}

						systemUiVisibility = flags
					}
				}

				previousTopOffset = topOffset
			}

			override fun dispatchDraw(canvas: Canvas) {
				canvas.withClip(0f, paddingTop + currentPanTranslationY, measuredWidth.toFloat(), measuredHeight + currentPanTranslationY + AndroidUtilities.dp(50f)) {
					super.dispatchDraw(this)
				}
			}
		}

		containerView = sizeNotifierFrameLayout
		containerView.setWillNotDraw(false)
		containerView.clipChildren = false
		containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0)

		frameLayout = FrameLayout(context)
		frameLayout.setBackgroundColor(if (darkTheme) context.getColor(R.color.dark_background) else context.getColor(R.color.background))

		if (darkTheme && linkToCopy[1] != null) {
			switchView = object : SwitchView(context) {
				override fun onTabSwitch(num: Int) {
					if (num == 0) {
						pickerBottomLayout?.text = context.getString(R.string.VoipGroupCopySpeakerLink).uppercase()
					}
					else {
						pickerBottomLayout?.text = context.getString(R.string.VoipGroupCopyListenLink).uppercase()
					}
				}
			}

			frameLayout.addView(switchView, createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.TOP or Gravity.LEFT, 0f, 11f, 0f, 0f))
		}

		searchView = SearchField(context)

		frameLayout.addView(searchView, createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.BOTTOM or Gravity.LEFT))

		gridView.setSelectorDrawableColor(0)
		gridView.setPadding(0, 0, 0, AndroidUtilities.dp(48f))
		gridView.clipToPadding = false
		gridView.layoutManager = layoutManager

		layoutManager.spanSizeLookup = object : SpanSizeLookup() {
			override fun getSpanSize(position: Int): Int {
				return if (position == 0) {
					layoutManager.spanCount
				}
				else {
					1
				}
			}
		}

		gridView.isHorizontalScrollBarEnabled = false
		gridView.isVerticalScrollBarEnabled = false

		gridView.addItemDecoration(object : RecyclerView.ItemDecoration() {
			override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
				val holder = parent.getChildViewHolder(view) as? RecyclerListView.Holder

				if (holder != null) {
					val pos = holder.adapterPosition

					outRect.left = if (pos % 4 == 0) 0 else AndroidUtilities.dp(4f)
					outRect.right = if (pos % 4 == 3) 0 else AndroidUtilities.dp(4f)
				}
				else {
					outRect.left = AndroidUtilities.dp(4f)
					outRect.right = AndroidUtilities.dp(4f)
				}
			}
		})

		containerView.addView(gridView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f))

		gridView.adapter = listAdapter
		gridView.setGlowColor(context.getColor(R.color.light_background))

		gridView.setOnItemClickListener { view, position ->
			if (position < 0) {
				return@setOnItemClickListener
			}

			val dialog = listAdapter.getItem(position) ?: return@setOnItemClickListener

			selectDialog(view as ShareDialogCell?, dialog)
		}

		gridView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				if (dy != 0) {
					updateLayout()
					previousScrollOffsetY = scrollOffsetY
				}
			}
		})

		searchGridView = object : RecyclerListView(context) {
			override fun allowSelectChildAtPosition(x: Float, y: Float): Boolean {
				return y >= AndroidUtilities.dp((if (darkTheme && linkToCopy[1] != null) 111 else 58).toFloat()) + AndroidUtilities.statusBarHeight
			}
		}

		searchGridView.setSelectorDrawableColor(0)
		searchGridView.setPadding(0, 0, 0, AndroidUtilities.dp(48f))
		searchGridView.clipToPadding = false
		searchGridView.layoutManager = FillLastGridLayoutManager(getContext(), 4, 0, searchGridView).also { searchLayoutManager = it }

		searchLayoutManager?.spanSizeLookup = object : SpanSizeLookup() {
			override fun getSpanSize(position: Int): Int {
				return searchAdapter.getSpanSize(4, position)
			}
		}

		searchGridView.setOnItemClickListener { view, position ->
			if (position < 0) {
				return@setOnItemClickListener
			}

			val dialog = searchAdapter.getItem(position) ?: return@setOnItemClickListener

			selectDialog(view as? ShareDialogCell, dialog)
		}

		searchGridView.setHasFixedSize(true)
		searchGridView.itemAnimator = null
		searchGridView.isHorizontalScrollBarEnabled = false
		searchGridView.isVerticalScrollBarEnabled = false

		searchGridView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				if (dy != 0) {
					updateLayout()
					previousScrollOffsetY = scrollOffsetY
				}
			}
		})

		searchGridView.addItemDecoration(object : RecyclerView.ItemDecoration() {
			override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
				val holder = parent.getChildViewHolder(view) as? RecyclerListView.Holder

				if (holder != null) {
					val pos = holder.adapterPosition

					outRect.left = if (pos % 4 == 0) 0 else AndroidUtilities.dp(4f)
					outRect.right = if (pos % 4 == 3) 0 else AndroidUtilities.dp(4f)
				}
				else {
					outRect.left = AndroidUtilities.dp(4f)
					outRect.right = AndroidUtilities.dp(4f)
				}
			}
		})

		searchGridView.adapter = searchAdapter
		searchGridView.setGlowColor(context.getColor(R.color.light_background))

		recyclerItemsEnterAnimator = RecyclerItemsEnterAnimator(searchGridView, true)

		val flickerLoadingView = FlickerLoadingView(context)
		flickerLoadingView.setViewType(FlickerLoadingView.SHARE_ALERT_TYPE)

		if (darkTheme) {
			flickerLoadingView.setColors(ResourcesCompat.getColor(getContext().resources, R.color.dark_background, null), ResourcesCompat.getColor(getContext().resources, R.color.light_background, null), 0)
		}

		searchEmptyView = StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH)
		searchEmptyView.addView(flickerLoadingView, 0)
		searchEmptyView.setAnimateLayoutChange(true)
		searchEmptyView.showProgress(show = false, animated = false)

		if (darkTheme) {
			searchEmptyView.title.setTextColor(context.getColor(R.color.white))
		}

		searchEmptyView.title.text = context.getString(R.string.NoResult)

		searchGridView.setEmptyView(searchEmptyView)
		searchGridView.setHideIfEmpty(false)
		searchGridView.setAnimateEmptyView(true, 0)

		containerView.addView(searchEmptyView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 52f, 0f, 0f))
		containerView.addView(searchGridView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f))

		var frameLayoutParams = FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP or Gravity.LEFT)
		frameLayoutParams.topMargin = AndroidUtilities.dp((if (darkTheme && linkToCopy[1] != null) 111 else 58).toFloat())

		shadow[0] = View(context)
		shadow[0]?.setBackgroundColor(context.getColor(R.color.shadow))
		shadow[0]?.alpha = 0.0f
		shadow[0]?.tag = 1

		containerView.addView(shadow[0], frameLayoutParams)
		containerView.addView(frameLayout, createFrame(LayoutHelper.MATCH_PARENT, if (darkTheme && linkToCopy[1] != null) 111 else 58, Gravity.LEFT or Gravity.TOP))

		frameLayoutParams = FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM or Gravity.LEFT)
		frameLayoutParams.bottomMargin = AndroidUtilities.dp(48f)

		shadow[1] = View(context)
		shadow[1]?.setBackgroundColor(context.getColor(R.color.shadow))

		containerView.addView(shadow[1], frameLayoutParams)

		if (isChannel || linkToCopy[0] != null) {
			pickerBottomLayout = TextView(context)
			pickerBottomLayout?.background = Theme.createSelectorWithBackgroundDrawable(if (darkTheme) context.getColor(R.color.dark_background) else context.getColor(R.color.background), context.getColor(R.color.light_background))
			pickerBottomLayout?.setTextColor(ResourcesCompat.getColor(getContext().resources, R.color.brand_day_night, null))
			pickerBottomLayout?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			pickerBottomLayout?.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
			pickerBottomLayout?.typeface = Theme.TYPEFACE_BOLD
			pickerBottomLayout?.gravity = Gravity.CENTER

			if (darkTheme && linkToCopy[1] != null) {
				pickerBottomLayout?.text = context.getString(R.string.VoipGroupCopySpeakerLink).uppercase()
			}
			else {
				pickerBottomLayout?.text = context.getString(R.string.CopyLink).uppercase()
			}

			pickerBottomLayout?.setOnClickListener {
				if (selectedDialogs.size() == 0 && (isChannel || linkToCopy[0] != null)) {
					dismiss()

					if (linkToCopy[0] == null && loadingLink) {
						copyLinkOnEnd = true
						Toast.makeText(context, context.getString(R.string.Loading), Toast.LENGTH_SHORT).show()
					}
					else {
						copyLink()
					}
				}
			}

			containerView.addView(pickerBottomLayout, createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.BOTTOM))

			if (parentFragment != null && hasAdminRights(parentFragment.currentChat) && ((sendingMessageObjects?.firstOrNull()?.messageOwner?.forwards ?: 0) > 0)) {
				val messageObject = sendingMessageObjects!![0]

				if (!messageObject.isForwarded) {
					sharesCountLayout = LinearLayout(context)
					sharesCountLayout?.orientation = LinearLayout.HORIZONTAL
					sharesCountLayout?.gravity = Gravity.CENTER_VERTICAL
					sharesCountLayout?.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background), 2)

					containerView.addView(sharesCountLayout, createFrame(LayoutHelper.WRAP_CONTENT, 48f, Gravity.RIGHT or Gravity.BOTTOM, 6f, 0f, -6f, 0f))

					sharesCountLayout?.setOnClickListener {
						parentFragment.presentFragment(MessageStatisticActivity(messageObject))
					}

					val imageView = ImageView(context)
					imageView.setImageResource(R.drawable.share_arrow)
					imageView.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.SRC_IN)

					sharesCountLayout?.addView(imageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 20, 0, 0, 0))

					val textView = TextView(context)
					textView.text = String.format(Locale.getDefault(), "%d", messageObject.messageOwner!!.forwards)
					textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
					textView.setTextColor(ResourcesCompat.getColor(getContext().resources, R.color.brand_day_night, null))
					textView.gravity = Gravity.CENTER_VERTICAL
					textView.typeface = Theme.TYPEFACE_BOLD

					sharesCountLayout?.addView(textView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 8, 0, 20, 0))
				}
			}
		}
		else {
			shadow[1]?.alpha = 0.0f
		}

		frameLayout2 = object : FrameLayout(context) {
			override fun setVisibility(visibility: Int) {
				super.setVisibility(visibility)

				if (visibility != VISIBLE) {
					shadow[1]?.translationY = 0f
				}
			}

			override fun setAlpha(alpha: Float) {
				super.setAlpha(alpha)
				invalidate()
			}

			override fun onDraw(canvas: Canvas) {
				if (chatActivityEnterViewAnimateFromTop != 0f && chatActivityEnterViewAnimateFromTop != frameLayout2.top + chatActivityEnterViewAnimateFromTop) {
					topBackgroundAnimator?.cancel()

					captionEditTextTopOffset = chatActivityEnterViewAnimateFromTop - (frameLayout2.top + captionEditTextTopOffset)

					topBackgroundAnimator = ValueAnimator.ofFloat(captionEditTextTopOffset, 0f)

					topBackgroundAnimator?.addUpdateListener {
						captionEditTextTopOffset = it.animatedValue as Float
						frameLayout2.invalidate()
						invalidate()
					}

					topBackgroundAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
					topBackgroundAnimator?.duration = 200
					topBackgroundAnimator?.start()

					chatActivityEnterViewAnimateFromTop = 0f
				}

				val alphaOffset = (frameLayout2.measuredHeight - AndroidUtilities.dp(48f)) * (1f - alpha)

				shadow[1]?.translationY = -(frameLayout2.measuredHeight - AndroidUtilities.dp(48f)) + captionEditTextTopOffset + currentPanTranslationY + alphaOffset

//                int newColor = getThemedColor(darkTheme ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_dialogBackground);
//                if (color != newColor) {
//                    color = newColor;
//                    p.setColor(color);
//                }
//                canvas.drawRect(0, captionEditTextTopOffset + alphaOffset, getMeasuredWidth(), getMeasuredHeight(), p);
			}

			override fun dispatchDraw(canvas: Canvas) {
				canvas.withClip(0f, captionEditTextTopOffset, measuredWidth.toFloat(), measuredHeight.toFloat()) {
					super.dispatchDraw(this)
				}
			}
		}

		frameLayout2.setWillNotDraw(false)
		frameLayout2.alpha = 0.0f
		frameLayout2.invisible()

		containerView.addView(frameLayout2, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.BOTTOM))

		frameLayout2.setOnTouchListener { _, _ -> true }

		commentTextView = object : EditTextEmoji(context, sizeNotifierFrameLayout, null, STYLE_DIALOG, true) {
			private var shouldAnimateEditTextWithBounds = false
			private var messageEditTextPredrawHeigth = 0
			private var messageEditTextPredrawScrollY = 0
			private var messageEditTextAnimator: ValueAnimator? = null

			override fun dispatchDraw(canvas: Canvas) {
				if (shouldAnimateEditTextWithBounds) {
					val editText = commentTextView.editText
					val dy = (messageEditTextPredrawHeigth - editText.measuredHeight + (messageEditTextPredrawScrollY - editText.scrollY)).toFloat()

					editText.offsetY -= dy

					val a = ValueAnimator.ofFloat(editText.offsetY, 0f)

					a.addUpdateListener {
						editText.offsetY = it.animatedValue as Float
					}

					messageEditTextAnimator?.cancel()

					messageEditTextAnimator = a

					a.duration = 200
					a.interpolator = CubicBezierInterpolator.DEFAULT
					a.start()

					shouldAnimateEditTextWithBounds = false
				}

				super.dispatchDraw(canvas)
			}

			override fun onLineCountChanged(oldLineCount: Int, newLineCount: Int) {
				if (!TextUtils.isEmpty(editText.text)) {
					shouldAnimateEditTextWithBounds = true
					messageEditTextPredrawHeigth = editText.measuredHeight
					messageEditTextPredrawScrollY = editText.scrollY
					invalidate()
				}
				else {
					editText.animate().cancel()
					editText.offsetY = 0f
					shouldAnimateEditTextWithBounds = false
				}

				chatActivityEnterViewAnimateFromTop = frameLayout2.top + captionEditTextTopOffset

				frameLayout2.invalidate()
			}

			override fun showPopup(show: Int) {
				super.showPopup(show)

				if (darkTheme) {
					navBarColor = 0
					AndroidUtilities.setNavigationBarColor(this@ShareAlert.window, context.getColor(R.color.light_background), true) { color ->
						setOverlayNavBarColor(color.also {
							navBarColor = it
						})
					}
				}
			}

			override fun hidePopup(byBackButton: Boolean) {
				super.hidePopup(byBackButton)

				if (darkTheme) {
					navBarColor = 0
					AndroidUtilities.setNavigationBarColor(this@ShareAlert.window, context.getColor(R.color.dark_background), true) { color ->
						setOverlayNavBarColor(color.also {
							navBarColor = it
						})
					}
				}
			}
		}

		if (darkTheme) {
			commentTextView.editText.setTextColor(context.getColor(R.color.white))
			commentTextView.editText.setCursorColor(context.getColor(R.color.white))
		}

		commentTextView.setBackgroundColor(backgroundColor)
		commentTextView.setHint(context.getString(R.string.ShareComment))
		commentTextView.onResume()
		commentTextView.setPadding(0, 0, AndroidUtilities.dp(84f), 0)

		frameLayout2.addView(commentTextView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT))
		frameLayout2.clipChildren = false
		frameLayout2.clipToPadding = false

		commentTextView.clipChildren = false

		writeButtonContainer = object : FrameLayout(context) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)
				info.text = LocaleController.formatPluralString("AccDescrShareInChats", selectedDialogs.size())
				info.className = Button::class.java.name
				info.isLongClickable = true
				info.isClickable = true
			}
		}

		writeButtonContainer.isFocusable = true
		writeButtonContainer.isFocusableInTouchMode = true
		writeButtonContainer.visibility = View.INVISIBLE
		writeButtonContainer.scaleX = 0.2f
		writeButtonContainer.scaleY = 0.2f
		writeButtonContainer.alpha = 0.0f

		containerView.addView(writeButtonContainer, createFrame(60, 60f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, 6f, 10f))

		val writeButton = ImageView(context)

		val drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56f), context.getColor(R.color.brand), context.getColor(R.color.darker_brand))

		writeButton.background = drawable
		writeButton.setImageResource(R.drawable.arrow_up)
		writeButton.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		writeButton.scaleType = ImageView.ScaleType.CENTER

		writeButton.outlineProvider = object : ViewOutlineProvider() {
			@SuppressLint("NewApi")
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, AndroidUtilities.dp(56f), AndroidUtilities.dp(56f))
			}
		}

		writeButtonContainer.addView(writeButton, createFrame(56, 56f, Gravity.LEFT or Gravity.TOP, 2f, 0f, 0f, 0f))

		writeButton.setOnClickListener {
			sendInternal(true)
		}

		writeButton.setOnLongClickListener {
			onSendLongClick(writeButton)
		}

		textPaint.textSize = AndroidUtilities.dp(12f).toFloat()
		textPaint.typeface = Theme.TYPEFACE_BOLD

		selectedCountView = object : View(context) {
			override fun onDraw(canvas: Canvas) {
				@Suppress("NAME_SHADOWING") val text = String.format(Locale.getDefault(), "%d", max(1, selectedDialogs.size()))
				val textSize = ceil(textPaint.measureText(text).toDouble()).toInt()
				val size = max(AndroidUtilities.dp(16f) + textSize, AndroidUtilities.dp(24f))
				val cx = measuredWidth / 2

				textPaint.color = context.getColor(R.color.white)
				paint.color = if (darkTheme) context.getColor(R.color.dark_background) else context.getColor(R.color.background)

				rect.set((cx - size / 2).toFloat(), 0f, (cx + size / 2).toFloat(), measuredHeight.toFloat())

				canvas.drawRoundRect(rect, AndroidUtilities.dp(12f).toFloat(), AndroidUtilities.dp(12f).toFloat(), paint)

				paint.color = context.getColor(R.color.brand)

				rect.set((cx - size / 2 + AndroidUtilities.dp(2f)).toFloat(), AndroidUtilities.dp(2f).toFloat(), (cx + size / 2 - AndroidUtilities.dp(2f)).toFloat(), (measuredHeight - AndroidUtilities.dp(2f)).toFloat())

				canvas.drawRoundRect(rect, AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(), paint)
				canvas.drawText(text, (cx - textSize / 2).toFloat(), AndroidUtilities.dp(16.2f).toFloat(), textPaint)
			}
		}

		selectedCountView.alpha = 0.0f
		selectedCountView.scaleX = 0.2f
		selectedCountView.scaleY = 0.2f

		containerView.addView(selectedCountView, createFrame(42, 24f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, -8f, 9f))

		updateSelectedCount(0)

		loadDialogs(AccountInstance.getInstance(currentAccount))

		if (listAdapter.dialogs.isEmpty()) {
			NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogsNeedReload)
		}

		loadRecentSearch(currentAccount, 0, OnRecentSearchLoaded { arrayList, _ ->
			recentSearchObjects = arrayList

			for (a in recentSearchObjects.indices) {
				val recentSearchObject = recentSearchObjects[a]

				when (recentSearchObject.`object`) {
					is User -> {
						MessagesController.getInstance(currentAccount).putUser(recentSearchObject.`object` as User?, true)
					}

					is Chat -> {
						MessagesController.getInstance(currentAccount).putChat(recentSearchObject.`object` as Chat?, true)
					}

					is EncryptedChat -> {
						MessagesController.getInstance(currentAccount).putEncryptedChat(recentSearchObject.`object` as EncryptedChat?, true)
					}
				}
			}

			searchAdapter.notifyDataSetChanged()
		})

		MediaDataController.getInstance(currentAccount).loadHints(true)

		AndroidUtilities.updateViewVisibilityAnimated(gridView, true, 1f, false)
		AndroidUtilities.updateViewVisibilityAnimated(searchGridView, false, 1f, false)
	}

	private fun selectDialog(cell: ShareDialogCell?, dialog: TLRPC.Dialog) {
		val parentActivity = parentActivity ?: return

		if (DialogObject.isChatDialog(dialog.id)) {
			val chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id)

			if (isChannel(chat) && !chat.megagroup && (!isCanWriteToChannel(-dialog.id, currentAccount) || hasPoll == 2)) {
				val builder = AlertDialog.Builder(parentActivity)
				builder.setTitle(context.getString(R.string.SendMessageTitle))

				if (hasPoll == 2) {
					if (isChannel) {
						builder.setMessage(context.getString(R.string.PublicPollCantForward))
					}
					else if (isActionBannedByDefault(chat, ChatObject.ACTION_SEND_POLLS)) {
						builder.setMessage(context.getString(R.string.ErrorSendRestrictedPollsAll))
					}
					else {
						builder.setMessage(context.getString(R.string.ErrorSendRestrictedPolls))
					}
				}
				else {
					builder.setMessage(context.getString(R.string.ChannelCantSendMessage))
				}

				builder.setNegativeButton(context.getString(R.string.OK), null)
				builder.show()

				return
			}
		}
		else if (DialogObject.isEncryptedDialog(dialog.id) && hasPoll != 0) {
			val builder = AlertDialog.Builder(parentActivity)
			builder.setTitle(context.getString(R.string.SendMessageTitle))

			if (hasPoll != 0) {
				builder.setMessage(context.getString(R.string.PollCantForwardSecretChat))
			}
			else {
				builder.setMessage(context.getString(R.string.InvoiceCantForwardSecretChat))
			}

			builder.setNegativeButton(context.getString(R.string.OK), null)
			builder.show()

			return
		}

		if (selectedDialogs.indexOfKey(dialog.id) >= 0) {
			selectedDialogs.remove(dialog.id)
			cell?.setChecked(false, true)
			updateSelectedCount(1)
		}
		else {
			selectedDialogs.put(dialog.id, dialog)

			cell?.setChecked(true, true)

			updateSelectedCount(2)

			val selfUserId = UserConfig.getInstance(currentAccount).clientUserId

			if (searchIsVisible) {
				val existingDialog = listAdapter.dialogsMap[dialog.id]

				if (existingDialog == null) {
					listAdapter.dialogsMap.put(dialog.id, dialog)
					listAdapter.dialogs.add(if (listAdapter.dialogs.isEmpty()) 0 else 1, dialog)
				}
				else if (existingDialog.id != selfUserId) {
					listAdapter.dialogs.remove(existingDialog)
					listAdapter.dialogs.add(if (listAdapter.dialogs.isEmpty()) 0 else 1, existingDialog)
				}

				listAdapter.notifyDataSetChanged()
				updateSearchAdapter = false
				searchView.searchEditText.setText("")
				checkCurrentList(false)
				searchView.hideKeyboard()
			}
		}

		searchAdapter?.categoryAdapter?.notifyItemRangeChanged(0, searchAdapter.categoryAdapter?.itemCount ?: 0)
	}

	override fun getContainerViewHeight(): Int {
		return containerView.measuredHeight - containerViewTop
	}

	private fun onSendLongClick(view: View): Boolean {
		val parentActivity = parentActivity ?: return false

		val layout = LinearLayout(context)
		layout.orientation = LinearLayout.VERTICAL

		if (sendingMessageObjects != null) {
			val sendPopupLayout1 = ActionBarPopupWindowLayout(parentActivity)

			if (darkTheme) {
				sendPopupLayout1.setBackgroundColor(context.getColor(R.color.dark_background))
			}

			sendPopupLayout1.setAnimationEnabled(false)

			sendPopupLayout1.setOnTouchListener(object : OnTouchListener {
				private val popupRect = Rect()

				override fun onTouch(v: View, event: MotionEvent): Boolean {
					if (event.actionMasked == MotionEvent.ACTION_DOWN) {
						if (sendPopupWindow?.isShowing == true) {
							v.getHitRect(popupRect)

							if (!popupRect.contains(event.x.toInt(), event.y.toInt())) {
								sendPopupWindow?.dismiss()
							}
						}
					}

					return false
				}
			})

			sendPopupLayout1.setDispatchKeyEventListener { keyEvent ->
				if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK && keyEvent.repeatCount == 0 && sendPopupWindow?.isShowing == true) {
					sendPopupWindow?.dismiss()
				}
			}

			sendPopupLayout1.setShownFromBottom(false)

			val showSendersNameView = ActionBarMenuSubItem(context, needCheck = true, top = true, bottom = false)

			if (darkTheme) {
				showSendersNameView.setTextColor(context.getColor(R.color.white))
			}

			sendPopupLayout1.addView(showSendersNameView, createLinear(LayoutHelper.MATCH_PARENT, 48))

			showSendersNameView.setTextAndIcon(context.getString(R.string.ShowSendersName), 0)
			showSendersNameView.setChecked(true.also { showSendersName = it })

			val hideSendersNameView = ActionBarMenuSubItem(context, needCheck = true, top = false, bottom = true)

			if (darkTheme) {
				hideSendersNameView.setTextColor(context.getColor(R.color.white))
			}

			sendPopupLayout1.addView(hideSendersNameView, createLinear(LayoutHelper.MATCH_PARENT, 48))

			hideSendersNameView.setTextAndIcon(context.getString(R.string.HideSendersName), 0)
			hideSendersNameView.setChecked(!showSendersName)

			showSendersNameView.setOnClickListener {
				showSendersNameView.setChecked(true.also { showSendersName = it })
				hideSendersNameView.setChecked(!showSendersName)
			}

			hideSendersNameView.setOnClickListener {
				showSendersNameView.setChecked(false.also { showSendersName = it })
				hideSendersNameView.setChecked(!showSendersName)
			}

			sendPopupLayout1.setupRadialSelectors(context.getColor(R.color.light_background))

			layout.addView(sendPopupLayout1, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0f, 0f, -8f))
		}

		val sendPopupLayout2 = ActionBarPopupWindowLayout(parentActivity)

		if (darkTheme) {
			sendPopupLayout2.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_inviteMembersBackground))
		}

		sendPopupLayout2.setAnimationEnabled(false)

		sendPopupLayout2.setOnTouchListener(object : OnTouchListener {
			private val popupRect = Rect()
			override fun onTouch(v: View, event: MotionEvent): Boolean {
				if (event.actionMasked == MotionEvent.ACTION_DOWN) {
					if (sendPopupWindow?.isShowing == true) {
						v.getHitRect(popupRect)

						if (!popupRect.contains(event.x.toInt(), event.y.toInt())) {
							sendPopupWindow?.dismiss()
						}
					}
				}
				return false
			}
		})

		sendPopupLayout2.setDispatchKeyEventListener { keyEvent ->
			if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK && keyEvent.repeatCount == 0 && sendPopupWindow?.isShowing == true) {
				sendPopupWindow?.dismiss()
			}
		}

		sendPopupLayout2.setShownFromBottom(false)

		val sendWithoutSound = ActionBarMenuSubItem(context, top = true, bottom = true)

		if (darkTheme) {
			sendWithoutSound.setTextColor(context.getColor(R.color.white))
			sendWithoutSound.setIconColor(context.getColor(R.color.white))
		}

		sendWithoutSound.setTextAndIcon(context.getString(R.string.SendWithoutSound), R.drawable.input_notify_off)
		sendWithoutSound.minimumWidth = AndroidUtilities.dp(196f)

		sendPopupLayout2.addView(sendWithoutSound, createLinear(LayoutHelper.MATCH_PARENT, 48))

		sendWithoutSound.setOnClickListener {
			if (sendPopupWindow?.isShowing == true) {
				sendPopupWindow?.dismiss()
			}

			sendInternal(false)
		}

		val sendMessage = ActionBarMenuSubItem(context, top = true, bottom = true)

		if (darkTheme) {
			sendMessage.setTextColor(context.getColor(R.color.white))
			sendMessage.setIconColor(context.getColor(R.color.white))
		}

		sendMessage.setTextAndIcon(context.getString(R.string.SendMessage), R.drawable.msg_send)
		sendMessage.minimumWidth = AndroidUtilities.dp(196f)

		sendPopupLayout2.addView(sendMessage, createLinear(LayoutHelper.MATCH_PARENT, 48))

		sendMessage.setOnClickListener {
			if (sendPopupWindow?.isShowing == true) {
				sendPopupWindow?.dismiss()
			}

			sendInternal(true)
		}

		sendPopupLayout2.setupRadialSelectors(context.getColor(R.color.light_background))

		layout.addView(sendPopupLayout2, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		sendPopupWindow = ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
		sendPopupWindow?.setAnimationEnabled(false)
		sendPopupWindow?.animationStyle = R.style.PopupContextAnimation2
		sendPopupWindow?.isOutsideTouchable = true
		sendPopupWindow?.isClippingEnabled = true
		sendPopupWindow?.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
		sendPopupWindow?.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
		sendPopupWindow?.contentView?.isFocusableInTouchMode = true

		SharedConfig.removeScheduledOrNoSoundHint()

		layout.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST))

		sendPopupWindow?.isFocusable = true

		val location = IntArray(2)

		view.getLocationInWindow(location)

		val y = if (keyboardVisible && parentFragment != null && parentFragment.contentView!!.measuredHeight > AndroidUtilities.dp(58f)) {
			location[1] + view.measuredHeight
		}
		else {
			location[1] - layout.measuredHeight - AndroidUtilities.dp(2f)
		}

		sendPopupWindow?.showAtLocation(view, Gravity.LEFT or Gravity.TOP, location[0] + view.measuredWidth - layout.measuredWidth + AndroidUtilities.dp(8f), y)
		sendPopupWindow?.dimBehind()

		view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

		return true
	}

	private fun sendInternal(withSound: Boolean) {
		for (a in 0 until selectedDialogs.size()) {
			val key = selectedDialogs.keyAt(a)

			if (AlertsCreator.checkSlowMode(context, currentAccount, key, frameLayout2.tag != null && commentTextView.length() > 0)) {
				return
			}
		}

		val text = arrayOf(commentTextView.text)
		val entities = MediaDataController.getInstance(currentAccount).getEntities(text, true)

		if (sendingMessageObjects != null) {
			val removeKeys: MutableList<Long> = ArrayList()

			for (a in 0 until selectedDialogs.size()) {
				val key = selectedDialogs.keyAt(a)

				if (frameLayout2.tag != null && commentTextView.length() > 0) {
					SendMessagesHelper.getInstance(currentAccount).sendMessage(text.firstOrNull()?.toString(), key, null, null, null, true, entities, null, null, withSound, 0, null, updateStickersOrder = false)
				}

				val result = SendMessagesHelper.getInstance(currentAccount).sendMessage(sendingMessageObjects, key, !showSendersName, false, withSound, 0)

				if (result != 0) {
					removeKeys.add(key)
				}
				if (selectedDialogs.size() == 1) {
					AlertsCreator.showSendMediaAlert(result, parentFragment)

					if (result != 0) {
						break
					}
				}
			}

			for (key in removeKeys) {
				selectedDialogs.remove(key)
			}

			if (!selectedDialogs.isEmpty()) {
				onSend(selectedDialogs, sendingMessageObjects!!.size)
			}
		}
		else {
			val num = switchView?.currentTab ?: 0

			if (sendingText[num] != null) {
				for (a in 0 until selectedDialogs.size()) {
					val key = selectedDialogs.keyAt(a)

					if (frameLayout2.tag != null && commentTextView.length() > 0) {
						SendMessagesHelper.getInstance(currentAccount).sendMessage(text.firstOrNull()?.toString(), key, null, null, null, true, entities, null, null, withSound, 0, null, updateStickersOrder = false)
					}

					SendMessagesHelper.getInstance(currentAccount).sendMessage(sendingText[num], key, null, null, null, true, null, null, null, withSound, 0, null, updateStickersOrder = false)
				}
			}

			onSend(selectedDialogs, 1)
		}

		shareAlertDelegate?.didShare()

		dismiss()
	}

	protected open fun onSend(dids: LongSparseArray<TLRPC.Dialog>, count: Int) {
		// stub
	}

	private val currentTop: Int
		get() {
			if (gridView.isNotEmpty()) {
				val child = gridView.getChildAt(0)
				val holder = gridView.findContainingViewHolder(child) as RecyclerListView.Holder?

				if (holder != null) {
					return gridView.paddingTop - if (holder.layoutPosition == 0 && child.top >= 0) child.top else 0
				}
			}

			return -1000
		}

	override fun dismissInternal() {
		super.dismissInternal()
		commentTextView.onDestroy()
	}

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		if (commentTextView.isPopupShowing) {
			commentTextView.hidePopup(true)
			return
		}

		super.onBackPressed()
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.dialogsNeedReload) {
			listAdapter.fetchDialogs()
			NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload)
		}
	}

	override fun canDismissWithSwipe(): Boolean {
		return false
	}

	@SuppressLint("NewApi")
	private fun updateLayout() {
		if (panTranslationMoveLayout) {
			return
		}

		var child: View
		val holder: RecyclerListView.Holder?
		val listView = if (searchIsVisible) searchGridView else gridView

		if (listView.childCount <= 0) {
			return
		}

		child = listView.getChildAt(0)

		for (i in 0 until listView.childCount) {
			if (listView.getChildAt(i).top < child.top) {
				child = listView.getChildAt(i)
			}
		}

		holder = listView.findContainingViewHolder(child) as? RecyclerListView.Holder

		val top = child.top - AndroidUtilities.dp(8f)
		var newOffset = if (top > 0 && holder != null && holder.adapterPosition == 0) top else 0

		if (top >= 0 && holder != null && holder.adapterPosition == 0) {
			lastOffset = child.top
			newOffset = top
			runShadowAnimation(0, false)
		}
		else {
			lastOffset = Int.MAX_VALUE
			runShadowAnimation(0, true)
		}

		if (scrollOffsetY != newOffset) {
			previousScrollOffsetY = scrollOffsetY
			gridView.topGlowOffset = (newOffset + currentPanTranslationY).toInt().also { scrollOffsetY = it }
			searchGridView.topGlowOffset = (newOffset + currentPanTranslationY).toInt().also { scrollOffsetY = it }
			frameLayout.translationY = scrollOffsetY + currentPanTranslationY
			searchEmptyView.translationY = scrollOffsetY + currentPanTranslationY
			containerView.invalidate()
		}
	}

	private fun runShadowAnimation(num: Int, show: Boolean) {
		if (show && shadow[num]?.tag != null || !show && shadow[num]?.tag == null) {
			shadow[num]?.tag = if (show) null else 1

			if (show) {
				shadow[num]?.visible()
			}

			shadowAnimation[num]?.cancel()

			shadowAnimation[num] = AnimatorSet()
			shadowAnimation[num]?.playTogether(ObjectAnimator.ofFloat(shadow[num], View.ALPHA, if (show) 1.0f else 0.0f))
			shadowAnimation[num]?.duration = 150

			shadowAnimation[num]?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (shadowAnimation[num] != null && shadowAnimation[num] == animation) {
						if (!show) {
							shadow[num]?.invisible()
						}

						shadowAnimation[num] = null
					}
				}

				override fun onAnimationCancel(animation: Animator) {
					if (shadowAnimation[num] != null && shadowAnimation[num] == animation) {
						shadowAnimation[num] = null
					}
				}
			})

			shadowAnimation[num]?.start()
		}
	}

	private fun copyLink() {
		if (exportedMessageLink == null && linkToCopy[0] == null) {
			return
		}

		try {
			val link = linkToCopy[switchView?.currentTab ?: 0]
			val clipboard = ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
			val clip = ClipData.newPlainText("label", link ?: exportedMessageLink?.link)

			clipboard.setPrimaryClip(clip)

			if ((shareAlertDelegate?.didCopy() != true)) {
				val isPrivate = exportedMessageLink?.link?.contains("/c/") == true

				(parentActivity as? LaunchActivity)?.showBulletin {
					it.createCopyLinkBulletin(isPrivate)
				}
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private fun showCommentTextView(show: Boolean): Boolean {
		if (show == (frameLayout2.tag != null)) {
			return false
		}

		animatorSet?.cancel()

		frameLayout2.tag = if (show) 1 else null

		if (commentTextView.editText.isFocused) {
			AndroidUtilities.hideKeyboard(commentTextView.editText)
		}

		commentTextView.hidePopup(true)

		if (show) {
			frameLayout2.visible()
			writeButtonContainer.visible()
		}

		pickerBottomLayout?.let {
			ViewCompat.setImportantForAccessibility(it, if (show) ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)
		}

		sharesCountLayout?.let {
			ViewCompat.setImportantForAccessibility(it, if (show) ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)
		}

		animatorSet = AnimatorSet()

		val animators = ArrayList<Animator>()
		animators.add(ObjectAnimator.ofFloat(frameLayout2, View.ALPHA, if (show) 1.0f else 0.0f))
		animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_X, if (show) 1.0f else 0.2f))
		animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_Y, if (show) 1.0f else 0.2f))
		animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.ALPHA, if (show) 1.0f else 0.0f))
		animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, if (show) 1.0f else 0.2f))
		animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, if (show) 1.0f else 0.2f))

		animators.add(ObjectAnimator.ofFloat(selectedCountView, View.ALPHA, if (show) 1.0f else 0.0f))

		if (pickerBottomLayout == null || pickerBottomLayout?.visibility != View.VISIBLE) {
			animators.add(ObjectAnimator.ofFloat(shadow[1], View.ALPHA, if (show) 1.0f else 0.0f))
		}

		animatorSet?.playTogether(animators)
		animatorSet?.interpolator = DecelerateInterpolator()
		animatorSet?.duration = 180

		animatorSet?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				if (animation == animatorSet) {
					if (!show) {
						frameLayout2.invisible()
						writeButtonContainer.invisible()
					}

					animatorSet = null
				}
			}

			override fun onAnimationCancel(animation: Animator) {
				if (animation == animatorSet) {
					animatorSet = null
				}
			}
		})

		animatorSet?.start()

		return true
	}

	fun updateSelectedCount(animated: Int) {
		if (selectedDialogs.size() == 0) {
			selectedCountView.pivotX = 0f
			selectedCountView.pivotY = 0f

			showCommentTextView(false)
		}
		else {
			selectedCountView.invalidate()

			if (!showCommentTextView(true) && animated != 0) {
				selectedCountView.pivotX = AndroidUtilities.dp(21f).toFloat()
				selectedCountView.pivotY = AndroidUtilities.dp(12f).toFloat()

				val animatorSet = AnimatorSet()
				animatorSet.playTogether(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, if (animated == 1) 1.1f else 0.9f, 1.0f), ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, if (animated == 1) 1.1f else 0.9f, 1.0f))
				animatorSet.interpolator = OvershootInterpolator()
				animatorSet.duration = 180
				animatorSet.start()
			}
			else {
				selectedCountView.pivotX = 0f
				selectedCountView.pivotY = 0f
			}
		}
	}

	override fun dismiss() {
		AndroidUtilities.hideKeyboard(commentTextView.editText)
		fullyShown = false
		super.dismiss()
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload)
	}

	private fun checkCurrentList(force: Boolean) {
		var searchVisibleLocal = false

		if (!searchView.searchEditText.text.isNullOrEmpty() || keyboardVisible && searchView.searchEditText.hasFocus()) {
			searchVisibleLocal = true
			updateSearchAdapter = true
			AndroidUtilities.updateViewVisibilityAnimated(gridView, false, 0.98f, true)
			AndroidUtilities.updateViewVisibilityAnimated(searchGridView, true)
		}
		else {
			AndroidUtilities.updateViewVisibilityAnimated(gridView, true, 0.98f, true)
			AndroidUtilities.updateViewVisibilityAnimated(searchGridView, false)
		}

		if (searchIsVisible != searchVisibleLocal || force) {
			searchIsVisible = searchVisibleLocal

			searchAdapter?.notifyDataSetChanged()

			listAdapter.notifyDataSetChanged()

			if (searchIsVisible) {
				if (lastOffset == Int.MAX_VALUE) {
					(searchGridView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, -searchGridView.paddingTop)
				}
				else {
					(searchGridView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, lastOffset - searchGridView.paddingTop)
				}

				searchAdapter?.searchDialogs(searchView.searchEditText.text.toString())
			}
			else {
				if (lastOffset == Int.MAX_VALUE) {
					layoutManager.scrollToPositionWithOffset(0, 0)
				}
				else {
					layoutManager.scrollToPositionWithOffset(0, 0)
				}
			}
		}
	}

	interface ShareAlertDelegate {
		fun didShare() {
			// default stub
		}

		fun didCopy(): Boolean {
			return false
		}
	}

	class DialogSearchResult {
		var dialog: TLRPC.Dialog = TLDialog()
		var `object`: TLObject? = null
		var date = 0
		var name: CharSequence? = null
	}

	private open inner class SwitchView(context: Context) : FrameLayout(context) {
		private val searchBackground = View(context)
		private val rightTab: SimpleTextView
		private val leftTab: SimpleTextView
		private val slidingView: View
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val rect = RectF()
		private var animator: AnimatorSet? = null
		private var linearGradient: LinearGradient? = null
		var currentTab = 0

		init {
			searchBackground.background = Theme.createRoundRectDrawable(AndroidUtilities.dp(18f), if (darkTheme) getContext().getColor(R.color.dark_background) else getContext().getColor(R.color.background))

			addView(searchBackground, createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.LEFT or Gravity.TOP, 14f, 0f, 14f, 0f))

			slidingView = object : View(context) {
				override fun setTranslationX(translationX: Float) {
					super.setTranslationX(translationX)
					invalidate()
				}

				override fun onDraw(canvas: Canvas) {
					super.onDraw(canvas)

					val color01 = -0x8a3495
					val color02 = -0xb05042
					val color11 = -0xa06b0b
					val color12 = -0x46a56f
					val color0 = AndroidUtilities.getOffsetColor(color01, color11, translationX / measuredWidth, 1.0f)
					val color1 = AndroidUtilities.getOffsetColor(color02, color12, translationX / measuredWidth, 1.0f)

					linearGradient = LinearGradient(0f, 0f, measuredWidth.toFloat(), 0f, intArrayOf(color0, color1), null, Shader.TileMode.CLAMP)
					paint.shader = linearGradient

					rect.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())

					canvas.drawRoundRect(rect, AndroidUtilities.dp(18f).toFloat(), AndroidUtilities.dp(18f).toFloat(), paint)
				}
			}

			addView(slidingView, createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.LEFT or Gravity.TOP, 14f, 0f, 14f, 0f))

			leftTab = SimpleTextView(context)
			leftTab.textColor = context.getColor(R.color.text)
			leftTab.setTextSize(13)
			leftTab.setLeftDrawable(R.drawable.msg_tabs_mic1)
			leftTab.setText(context.getString(R.string.VoipGroupInviteCanSpeak))
			leftTab.setGravity(Gravity.CENTER)

			leftTab.setOnClickListener {
				switchToTab(0)
			}

			addView(leftTab, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT or Gravity.TOP, 14f, 0f, 0f, 0f))

			rightTab = SimpleTextView(context)
			rightTab.textColor = context.getColor(R.color.text)
			rightTab.setTextSize(13)
			rightTab.setLeftDrawable(R.drawable.msg_tabs_mic2)
			rightTab.setText(context.getString(R.string.VoipGroupInviteListenOnly))
			rightTab.setGravity(Gravity.CENTER)

			rightTab.setOnClickListener {
				switchToTab(1)
			}

			addView(rightTab, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 0f, 14f, 0f))
		}

		protected open fun onTabSwitch(num: Int) {
			// stub
		}

		private fun switchToTab(tab: Int) {
			if (currentTab == tab) {
				return
			}

			currentTab = tab

			animator?.cancel()

			animator = AnimatorSet()
			animator?.playTogether(ObjectAnimator.ofFloat(slidingView, TRANSLATION_X, (if (currentTab == 0) 0 else slidingView.measuredWidth).toFloat()))
			animator?.duration = 180
			animator?.interpolator = CubicBezierInterpolator.EASE_OUT

			animator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					animator = null
				}
			})

			animator?.start()

			onTabSwitch(currentTab)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(28f)

			var layoutParams = leftTab.layoutParams as LayoutParams
			layoutParams.width = width / 2

			layoutParams = rightTab.layoutParams as LayoutParams
			layoutParams.width = width / 2
			layoutParams.leftMargin = width / 2 + AndroidUtilities.dp(14f)

			layoutParams = slidingView.layoutParams as LayoutParams
			layoutParams.width = width / 2

			animator?.cancel()

			slidingView.translationX = (if (currentTab == 0) 0 else layoutParams.width).toFloat()

			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
	}

	inner class SearchField(context: Context) : FrameLayout(context) {
		private val searchBackground = View(context)
		private val searchIconImageView: ImageView
		private val clearSearchImageView: ImageView
		val searchEditText = EditTextBoldCursor(context)

		private val progressDrawable = object : CloseProgressDrawable2() {
			override fun getCurrentColor(): Int {
				return context.getColor(R.color.brand)
			}
		}

		init {
			searchBackground.background = Theme.createRoundRectDrawable(AndroidUtilities.dp(18f), if (darkTheme) getContext().getColor(R.color.dark_background) else getContext().getColor(R.color.background))

			addView(searchBackground, createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.LEFT or Gravity.TOP, 14f, 11f, 14f, 0f))

			searchIconImageView = ImageView(context)
			searchIconImageView.scaleType = ImageView.ScaleType.CENTER
			searchIconImageView.setImageResource(R.drawable.smiles_inputsearch)
			searchIconImageView.colorFilter = PorterDuffColorFilter(getContext().getColor(R.color.brand), PorterDuff.Mode.MULTIPLY)

			addView(searchIconImageView, createFrame(36, 36f, Gravity.LEFT or Gravity.TOP, 16f, 11f, 0f, 0f))

			clearSearchImageView = ImageView(context)
			clearSearchImageView.scaleType = ImageView.ScaleType.CENTER

			clearSearchImageView.setImageDrawable(progressDrawable)

			progressDrawable.setSide(AndroidUtilities.dp(7f))

			clearSearchImageView.scaleX = 0.1f
			clearSearchImageView.scaleY = 0.1f
			clearSearchImageView.alpha = 0.0f

			addView(clearSearchImageView, createFrame(36, 36f, Gravity.RIGHT or Gravity.TOP, 14f, 11f, 14f, 0f))

			clearSearchImageView.setOnClickListener {
				updateSearchAdapter = true
				searchEditText.setText("")
				AndroidUtilities.showKeyboard(searchEditText)
			}

			searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			searchEditText.setHintTextColor(if (darkTheme) context.getColor(R.color.light_gray) else context.getColor(R.color.hint))
			searchEditText.setTextColor(if (darkTheme) context.getColor(R.color.white) else context.getColor(R.color.text))
			searchEditText.background = null
			searchEditText.setPadding(0, 0, 0, 0)
			searchEditText.maxLines = 1
			searchEditText.setLines(1)
			searchEditText.isSingleLine = true
			searchEditText.imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
			searchEditText.hint = context.getString(R.string.ShareSendTo)
			searchEditText.setCursorColor(if (darkTheme) context.getColor(R.color.light_gray) else context.getColor(R.color.text))
			searchEditText.setCursorSize(AndroidUtilities.dp(20f))
			searchEditText.setCursorWidth(1.5f)

			addView(searchEditText, createFrame(LayoutHelper.MATCH_PARENT, 40f, Gravity.LEFT or Gravity.TOP, (16 + 38).toFloat(), 9f, (16 + 30).toFloat(), 0f))

			searchEditText.doAfterTextChanged {
				val show = searchEditText.length() > 0
				val showed = clearSearchImageView.alpha != 0f

				if (show != showed) {
					clearSearchImageView.animate().alpha(if (show) 1.0f else 0.0f).setDuration(150).scaleX(if (show) 1.0f else 0.1f).scaleY(if (show) 1.0f else 0.1f).start()
				}

				if (!searchEditText.text.isNullOrEmpty()) {
					checkCurrentList(false)
				}

				if (!updateSearchAdapter) {
					return@doAfterTextChanged
				}

				val text = searchEditText.text.toString()

				if (text.isNotEmpty()) {
					searchEmptyView.title.text = context.getString(R.string.NoResult)
				}
				else {
					if (gridView.adapter !== listAdapter) {
						val top: Int = currentTop

						searchEmptyView.title.text = context.getString(R.string.NoResult)
						searchEmptyView.showProgress(show = false, animated = true)

						checkCurrentList(false)

						listAdapter.notifyDataSetChanged()

						if (top > 0) {
							layoutManager.scrollToPositionWithOffset(0, -top)
						}
					}
				}
				searchAdapter?.searchDialogs(text)
			}

			searchEditText.setOnEditorActionListener { _, _, event ->
				if (event != null && (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_SEARCH || event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
					AndroidUtilities.hideKeyboard(searchEditText)
				}

				false
			}
		}

		fun hideKeyboard() {
			AndroidUtilities.hideKeyboard(searchEditText)
		}
	}

	private inner class ShareDialogsAdapter(private val context: Context) : SelectionAdapter() {
		val dialogs = ArrayList<TLRPC.Dialog>()
		val dialogsMap = LongSparseArray<TLRPC.Dialog>()

		init {
			fetchDialogs()
		}

		fun fetchDialogs() {
			dialogs.clear()
			dialogsMap.clear()

			val selfUserId = UserConfig.getInstance(currentAccount).clientUserId

			if (MessagesController.getInstance(currentAccount).dialogsForward.isNotEmpty()) {
				val dialog = MessagesController.getInstance(currentAccount).dialogsForward[0]
				dialogs.add(dialog)
				dialogsMap.put(dialog.id, dialog)
			}

			val archivedDialogs = ArrayList<TLRPC.Dialog>()
			val allDialogs = MessagesController.getInstance(currentAccount).allDialogs

			for (a in allDialogs.indices) {
				val dialog = allDialogs[a] as? TLDialog ?: continue

				if (dialog.id == selfUserId) {
					continue
				}

				if (!DialogObject.isEncryptedDialog(dialog.id)) {
					if (DialogObject.isUserDialog(dialog.id)) {
						if (dialog.folderId == 1) {
							archivedDialogs.add(dialog)
						}
						else {
							dialogs.add(dialog)
						}

						dialogsMap.put(dialog.id, dialog)
					}
					else {
						val chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id)

						if (!(chat == null || isNotInChat(chat) || chat.gigagroup && !hasAdminRights(chat) || isChannel(chat) && !chat.creator && (chat.adminRights == null || chat.adminRights?.postMessages != true) && !chat.megagroup)) {
							if (dialog.folderId == 1) {
								archivedDialogs.add(dialog)
							}
							else {
								dialogs.add(dialog)
							}

							dialogsMap.put(dialog.id, dialog)
						}
					}
				}
			}

			dialogs.addAll(archivedDialogs)

			notifyDataSetChanged()
		}

		override fun getItemCount(): Int {
			var count = dialogs.size

			if (count != 0) {
				count++
			}

			return count
		}

		fun getItem(position: Int): TLRPC.Dialog? {
			@Suppress("NAME_SHADOWING") var position = position

			position--

			return if (position < 0 || position >= dialogs.size) {
				null
			}
			else {
				dialogs[position]
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType != 1
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = ShareDialogCell(context, if (darkTheme) ShareDialogCell.TYPE_CALL else ShareDialogCell.TYPE_SHARE)
					view.setLayoutParams(RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(100f)))
				}

				1 -> {
					view = View(context)
					view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp((if (darkTheme && linkToCopy[1] != null) 109 else 56).toFloat()))
				}

				else -> {
					view = View(context)
					view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp((if (darkTheme && linkToCopy[1] != null) 109 else 56).toFloat()))
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			if (holder.itemViewType == 0) {
				val cell = holder.itemView as ShareDialogCell
				val dialog = getItem(position)
				cell.setDialog(dialog?.id ?: 0, selectedDialogs.indexOfKey(dialog?.id ?: Long.MIN_VALUE) >= 0, null)
			}
		}

		override fun getItemViewType(position: Int): Int {
			return if (position == 0) {
				1
			}
			else {
				0
			}
		}
	}

	inner class ShareSearchAdapter(private val context: Context) : SelectionAdapter() {
		private val searchAdapterHelper = SearchAdapterHelper(false)
		private var categoryListView: RecyclerView? = null
		private var firstEmptyViewCell = -1
		private var hintsCell = -1
		private var lastFilledItem = -1
		private var lastGlobalSearchId = 0
		private var lastLocalSearchId = 0
		private var lastSearchId = 0
		private var lastSearchText: String? = null
		private var recentDialogsStartRow = -1
		private var resentTitleCell = -1
		private var searchResult = ArrayList<Any>()
		private var searchResultsStartRow = -1
		private var searchRunnable2: Runnable? = null
		private var searchRunnable: Runnable? = null
		var categoryAdapter: DialogsSearchAdapter.CategoryAdapterRecycler? = null
		var internalDialogsIsSearching = false
		var itemsCount = 0
		var lastItemCont = 0

		init {
			searchAdapterHelper.setDelegate(object : SearchAdapterHelperDelegate {
				override val excludeCallParticipants: LongSparseArray<TLGroupCallParticipant>?
					get() = null

				override val excludeUsersIds: LongArray?
					get() = null

				override val excludeUsers: LongSparseArray<User>?
					get() = null

				override fun onSetHashtags(arrayList: ArrayList<HashtagObject>?, hashMap: HashMap<String, HashtagObject>?) {
					// unused
				}

				override fun onDataSetChanged(searchId: Int) {
					lastGlobalSearchId = searchId

					if (lastLocalSearchId != searchId) {
						searchResult.clear()
					}

					val oldItemsCount = lastItemCont

					if (itemCount == 0 && !searchAdapterHelper.isSearchInProgress && !internalDialogsIsSearching) {
						searchEmptyView.showProgress(show = false, animated = true)
					}
					else {
						recyclerItemsEnterAnimator.showItemsAnimated(oldItemsCount)
					}

					notifyDataSetChanged()

					checkCurrentList(true)
				}

				override fun canApplySearchResults(searchId: Int): Boolean {
					return searchId == lastSearchId
				}
			})
		}

		private fun searchDialogsInternal(query: String?, searchId: Int) {
			MessagesStorage.getInstance(currentAccount).storageQueue.postRunnable {
				try {
					val search1 = query?.trim()?.lowercase()

					if (search1.isNullOrEmpty()) {
						lastSearchId = -1
						updateSearchResults(ArrayList(), lastSearchId)
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

					val usersToLoad = ArrayList<Long?>()
					val chatsToLoad = ArrayList<Long?>()
					var resultCount = 0
					val dialogsResult = LongSparseArray<DialogSearchResult>()
					var cursor = MessagesStorage.getInstance(currentAccount).database.queryFinalized("SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 400")

					while (cursor.next()) {
						val id = cursor.longValue(0)

						val dialogSearchResult = DialogSearchResult()
						dialogSearchResult.date = cursor.intValue(1)

						dialogsResult.put(id, dialogSearchResult)

						if (DialogObject.isUserDialog(id)) {
							if (!usersToLoad.contains(id)) {
								usersToLoad.add(id)
							}
						}
						else if (DialogObject.isChatDialog(id)) {
							if (!chatsToLoad.contains(-id)) {
								chatsToLoad.add(-id)
							}
						}
					}

					cursor.dispose()

					if (usersToLoad.isNotEmpty()) {
						cursor = MessagesStorage.getInstance(currentAccount).database.queryFinalized(String.format(Locale.US, "SELECT data, status, name FROM users WHERE uid IN(%s)", TextUtils.join(",", usersToLoad)))

						while (cursor.next()) {
							val name = cursor.stringValue(2)
							var tName = LocaleController.getInstance().getTranslitString(name)

							if (name == tName) {
								tName = null
							}

							var username: String? = null
							val usernamePos = name.lastIndexOf(";;;")

							if (usernamePos != -1) {
								username = name.substring(usernamePos + 3)
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
									val data = cursor.byteBufferValue(0)

									if (data != null) {
										val user = User.deserialize(data, data.readInt32(false), false)

										data.reuse()

										val dialogSearchResult = dialogsResult[user!!.id]

										user.setStatusFromExpires(cursor.intValue(1))

										if (found == 1) {
											dialogSearchResult?.name = AndroidUtilities.generateSearchName(user.firstName, user.lastName, q)
										}
										else {
											dialogSearchResult?.name = AndroidUtilities.generateSearchName("@" + user.username, null, "@$q")
										}

										dialogSearchResult?.`object` = user
										dialogSearchResult?.dialog?.id = user.id

										resultCount++
									}

									break
								}
							}
						}

						cursor.dispose()
					}

					if (chatsToLoad.isNotEmpty()) {
						cursor = MessagesStorage.getInstance(currentAccount).database.queryFinalized(String.format(Locale.US, "SELECT data, name FROM chats WHERE uid IN(%s)", TextUtils.join(",", chatsToLoad)))

						while (cursor.next()) {
							val name = cursor.stringValue(1)
							var tName = LocaleController.getInstance().getTranslitString(name)

							if (name == tName) {
								tName = null
							}

							for (q in search) {
								if (q.isNullOrEmpty()) {
									continue
								}

								if (name.startsWith(q) || name.contains(" $q") || tName != null && (tName.startsWith(q) || tName.contains(" $q"))) {
									val data = cursor.byteBufferValue(0)

									if (data != null) {
										val chat = Chat.deserialize(data, data.readInt32(false), false)

										data.reuse()

										if (!(chat == null || isNotInChat(chat) || isChannel(chat) && !chat.creator && (chat.adminRights == null || chat.adminRights?.postMessages != true) && !chat.megagroup)) {
											val dialogSearchResult = dialogsResult[-chat.id]
											dialogSearchResult?.name = AndroidUtilities.generateSearchName(chat.title, null, q)
											dialogSearchResult?.`object` = chat
											dialogSearchResult?.dialog?.id = -chat.id

											resultCount++
										}
									}

									break
								}
							}
						}

						cursor.dispose()
					}

					val searchResults = ArrayList<Any>(resultCount)

					for (a in 0 until dialogsResult.size()) {
						val dialogSearchResult = dialogsResult.valueAt(a)

						if (dialogSearchResult.`object` != null && dialogSearchResult.name != null) {
							searchResults.add(dialogSearchResult)
						}
					}

					cursor = MessagesStorage.getInstance(currentAccount).database.queryFinalized("SELECT u.data, u.status, u.name, u.uid FROM users as u INNER JOIN contacts as c ON u.uid = c.uid")

					while (cursor.next()) {
						val uid = cursor.longValue(3)

						if (dialogsResult.indexOfKey(uid) >= 0) {
							continue
						}

						val name = cursor.stringValue(2)
						var tName = LocaleController.getInstance().getTranslitString(name)

						if (name == tName) {
							tName = null
						}

						var username: String? = null
						val usernamePos = name.lastIndexOf(";;;")

						if (usernamePos != -1) {
							username = name.substring(usernamePos + 3)
						}

						var found = 0

						for (q in search) {
							if (q.isNullOrEmpty()) {
								continue
							}

							if (name.startsWith(q) || name.contains(" $q") || tName != null && (tName.startsWith(q) || tName.contains(" $q"))) {
								found = 1
							}
							else if (username != null && username.startsWith(q)) {
								found = 2
							}

							if (found != 0) {
								val data = cursor.byteBufferValue(0)

								if (data != null) {
									val user = User.deserialize(data, data.readInt32(false), false)

									data.reuse()

									if (user != null) {
										val dialogSearchResult = DialogSearchResult()

										user.setStatusFromExpires(cursor.intValue(1))

										dialogSearchResult.dialog.id = user.id
										dialogSearchResult.`object` = user

										if (found == 1) {
											dialogSearchResult.name = AndroidUtilities.generateSearchName(user.firstName, user.lastName, q)
										}
										else {
											dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + user.username, null, "@$q")
										}

										searchResults.add(dialogSearchResult)
									}
								}

								break
							}
						}
					}

					cursor.dispose()

					searchResults.sortWith { lhs, rhs ->
						val res1 = lhs as DialogSearchResult
						val res2 = rhs as DialogSearchResult

						if (res1.date < res2.date) {
							return@sortWith 1
						}
						else if (res1.date > res2.date) {
							return@sortWith -1
						}
						else {
							return@sortWith 0
						}
					}

					updateSearchResults(searchResults, searchId)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		private fun updateSearchResults(result: ArrayList<Any>, searchId: Int) {
			AndroidUtilities.runOnUIThread {
				if (searchId != lastSearchId) {
					return@runOnUIThread
				}

				internalDialogsIsSearching = false
				lastLocalSearchId = searchId

				if (lastGlobalSearchId != searchId) {
					searchAdapterHelper.clear()
				}

				if (gridView.adapter !== searchAdapter) {
					searchAdapter?.notifyDataSetChanged()
				}

				for (a in result.indices) {
					val obj = result[a] as DialogSearchResult

					if (obj.`object` is User) {
						val user = obj.`object` as User?
						MessagesController.getInstance(currentAccount).putUser(user, true)
					}
					else if (obj.`object` is Chat) {
						val chat = obj.`object` as Chat?
						MessagesController.getInstance(currentAccount).putChat(chat, true)
					}
				}

				searchResult = result

				searchAdapterHelper.mergeResults(searchResult, null)

				val oldItemsCount = lastItemCont

				if (itemCount == 0 && !searchAdapterHelper.isSearchInProgress && !internalDialogsIsSearching) {
					searchEmptyView.showProgress(show = false, animated = true)
				}
				else {
					recyclerItemsEnterAnimator.showItemsAnimated(oldItemsCount)
				}

				notifyDataSetChanged()

				checkCurrentList(true)
			}
		}

		fun searchDialogs(query: String?) {
			if (query != null && query == lastSearchText) {
				return
			}

			lastSearchText = query

			if (searchRunnable != null) {
				Utilities.searchQueue.cancelRunnable(searchRunnable)
				searchRunnable = null
			}
			if (searchRunnable2 != null) {
				AndroidUtilities.cancelRunOnUIThread(searchRunnable2)
				searchRunnable2 = null
			}

			searchResult.clear()

			searchAdapterHelper.mergeResults(null)
			searchAdapterHelper.queryServerSearch(null, allowUsername = true, allowChats = true, allowBots = true, allowSelf = true, canAddGroupsOnly = false, channelId = 0, type = 0, searchId = 0, onEnd = null)

			notifyDataSetChanged()

			checkCurrentList(true)

			if (query.isNullOrEmpty()) {
				lastSearchId = -1
				internalDialogsIsSearching = false
			}
			else {
				internalDialogsIsSearching = true

				val searchId = ++lastSearchId

				searchEmptyView.showProgress(show = true, animated = true)

				Utilities.searchQueue.postRunnable(Runnable {
					searchRunnable = null

					searchDialogsInternal(query, searchId)

					AndroidUtilities.runOnUIThread(Runnable {
						searchRunnable2 = null

						if (searchId != lastSearchId) {
							return@Runnable
						}

						searchAdapterHelper.queryServerSearch(query, allowUsername = true, allowChats = true, allowBots = true, allowSelf = true, canAddGroupsOnly = false, channelId = 0, type = 0, searchId = searchId, onEnd = null)
					}.also { searchRunnable2 = it })
				}.also { searchRunnable = it }, 300)
			}

			checkCurrentList(false)
		}

		override fun getItemCount(): Int {
			itemsCount = 0
			hintsCell = -1
			resentTitleCell = -1
			recentDialogsStartRow = -1
			searchResultsStartRow = -1
			lastFilledItem = -1

			if (lastSearchText.isNullOrEmpty()) {
				firstEmptyViewCell = itemsCount++
				hintsCell = itemsCount++

				if (recentSearchObjects.size > 0) {
					resentTitleCell = itemsCount++
					recentDialogsStartRow = itemsCount
					itemsCount += recentSearchObjects.size
				}

				lastFilledItem = itemsCount++

				return itemsCount.also { lastItemCont = it }
			}
			else {
				firstEmptyViewCell = itemsCount++
				searchResultsStartRow = itemsCount
				itemsCount += searchResult.size + searchAdapterHelper.localServerSearch.size

				if (itemsCount == 1) {
					firstEmptyViewCell = -1
					return 0.also { itemsCount = it }.also { lastItemCont = it }
				}

				lastFilledItem = itemsCount++
			}

			return itemsCount.also { lastItemCont = it }
		}

		fun getItem(position: Int): TLRPC.Dialog? {
			@Suppress("NAME_SHADOWING") var position = position

			if (recentDialogsStartRow in 0..position) {
				val index = position - recentDialogsStartRow

				if (index < 0 || index >= recentSearchObjects.size) {
					return null
				}

				val recentSearchObject = recentSearchObjects[index]
				val `object` = recentSearchObject.`object`
				val dialog: TLRPC.Dialog = TLDialog()

				if (`object` is User) {
					dialog.id = `object`.id
				}
				else {
					dialog.id = -(`object` as Chat?)!!.id
				}

				return dialog
			}

			position--

			if (position < 0) {
				return null
			}

			position -= if (position < searchResult.size) {
				return (searchResult[position] as DialogSearchResult).dialog
			}
			else {
				searchResult.size
			}

			val arrayList = searchAdapterHelper.localServerSearch

			if (position < arrayList.size) {
				val `object` = arrayList[position]
				val dialog: TLRPC.Dialog = TLDialog()

				if (`object` is User) {
					dialog.id = `object`.id
				}
				else {
					dialog.id = -(`object` as Chat).id
				}

				return dialog
			}

			return null
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType != 1 && holder.itemViewType != 4
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = ShareDialogCell(context, if (darkTheme) ShareDialogCell.TYPE_CALL else ShareDialogCell.TYPE_SHARE)
					view.setLayoutParams(RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(100f)))
				}

				1 -> {
					view = View(context)
					view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp((if (darkTheme && linkToCopy[1] != null) 109 else 56).toFloat()))
				}

				2 -> {
					val horizontalListView: RecyclerListView = object : RecyclerListView(context) {
						override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
							getParent()?.parent?.requestDisallowInterceptTouchEvent(canScrollHorizontally(-1) || canScrollHorizontally(1))
							return super.onInterceptTouchEvent(e)
						}
					}

					categoryListView = horizontalListView

					horizontalListView.itemAnimator = null
					horizontalListView.layoutAnimation = null

					val layoutManager = object : LinearLayoutManager(context) {
						override fun supportsPredictiveItemAnimations(): Boolean {
							return false
						}
					}

					layoutManager.orientation = LinearLayoutManager.HORIZONTAL

					horizontalListView.layoutManager = layoutManager

					horizontalListView.adapter = object : DialogsSearchAdapter.CategoryAdapterRecycler(context, currentAccount, true) {
						override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
							val cell = holder.itemView as HintDialogCell

							if (darkTheme) {
								cell.setColors(context.getColor(R.color.white), context.getColor(R.color.light_background))
							}

							val peer = MediaDataController.getInstance(currentAccount).hints[position]
							var chat: Chat? = null
							var user: User? = null
							var did: Long = 0

							if (peer.peer.userId != 0L) {
								did = peer.peer.userId
								user = MessagesController.getInstance(currentAccount).getUser(peer.peer.userId)
							}
							else if (peer.peer.channelId != 0L) {
								did = -peer.peer.channelId
								chat = MessagesController.getInstance(currentAccount).getChat(peer.peer.channelId)
							}
							else if (peer.peer.chatId != 0L) {
								did = -peer.peer.chatId
								chat = MessagesController.getInstance(currentAccount).getChat(peer.peer.chatId)
							}

							val animated = did == cell.dialogId

							cell.tag = did

							var name: String? = ""

							if (user != null) {
								name = getFirstName(user)
							}
							else if (chat != null) {
								name = chat.title
							}

							cell.setDialog(did, true, name)
							cell.setChecked(selectedDialogs.indexOfKey(did) >= 0, animated)
						}
					}.also { categoryAdapter = it }

					horizontalListView.setOnItemClickListener { view1, position ->
						val peer = MediaDataController.getInstance(currentAccount).hints[position]
						val dialog: TLRPC.Dialog = TLDialog()
						var did: Long = 0

						if (peer.peer.userId != 0L) {
							did = peer.peer.userId
						}
						else if (peer.peer.channelId != 0L) {
							did = -peer.peer.channelId
						}
						else if (peer.peer.chatId != 0L) {
							did = -peer.peer.chatId
						}

						dialog.id = did

						selectDialog(null, dialog)

						val cell = view1 as HintDialogCell
						cell.setChecked(selectedDialogs.indexOfKey(did) >= 0, true)
					}

					view = horizontalListView
				}

				3 -> {
					val graySectionCell = GraySectionCell(context)
					graySectionCell.setBackgroundColor(if (darkTheme) context.getColor(R.color.dark_background) else context.getColor(R.color.light_background))
					graySectionCell.setText(context.getString(R.string.Recent))

					view = graySectionCell
				}

				4 -> {
					view = object : View(context) {
						override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
							super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(searchLayoutManager?.lastItemHeight ?: 0, MeasureSpec.EXACTLY))
						}
					}
				}

				else -> {
					view = View(context)
					view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp((if (darkTheme && linkToCopy[1] != null) 109 else 56).toFloat()))
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			@Suppress("NAME_SHADOWING") var position = position

			if (holder.itemViewType == 0) {
				val cell = holder.itemView as ShareDialogCell
				var name: CharSequence? = null
				var id: Long = 0

				if (lastSearchText.isNullOrEmpty()) {
					if (recentDialogsStartRow in 0..position) {
						val p = position - recentDialogsStartRow
						val recentSearchObject = recentSearchObjects[p]
						val `object` = recentSearchObject.`object`

						if (`object` is User) {
							id = `object`.id
							name = formatName(`object`.firstName, `object`.lastName)
						}
						else if (`object` is Chat) {
							id = -`object`.id
							name = `object`.title
						}
						else if (`object` is TLEncryptedChat) {
							val user = MessagesController.getInstance(currentAccount).getUser(`object`.userId)

							if (user != null) {
								id = user.id
								name = formatName(user.firstName, user.lastName)
							}
						}

						val foundUserName = searchAdapterHelper.lastFoundUsername

						if (!foundUserName.isNullOrEmpty()) {
							var index = 0

							if (name != null && AndroidUtilities.indexOfIgnoreCase(name.toString(), foundUserName).also { index = it } != -1) {
								val spannableStringBuilder = SpannableStringBuilder(name)
								spannableStringBuilder.setSpan(ForegroundColorSpanThemable(context.getColor(R.color.brand)), index, index + foundUserName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

								name = spannableStringBuilder
							}
						}
					}

					cell.setDialog(id.toInt().toLong(), selectedDialogs.indexOfKey(id) >= 0, name)

					return
				}

				position--

				if (position < searchResult.size) {
					val result = searchResult[position] as DialogSearchResult
					id = result.dialog.id
					name = result.name
				}
				else {
					position -= searchResult.size

					val arrayList = searchAdapterHelper.localServerSearch
					val `object` = arrayList[position]

					if (`object` is User) {
						id = `object`.id
						name = formatName(`object`.firstName, `object`.lastName)
					}
					else {
						val chat = `object` as Chat
						id = -chat.id
						name = chat.title
					}

					val foundUserName = searchAdapterHelper.lastFoundUsername

					if (!foundUserName.isNullOrEmpty()) {
						var index = 0

						if (name != null && AndroidUtilities.indexOfIgnoreCase(name.toString(), foundUserName).also { index = it } != -1) {
							val spannableStringBuilder = SpannableStringBuilder(name)
							spannableStringBuilder.setSpan(ForegroundColorSpanThemable(context.getColor(R.color.brand)), index, index + foundUserName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

							name = spannableStringBuilder
						}
					}
				}

				cell.setDialog(id, selectedDialogs.indexOfKey(id) >= 0, name)
			}
			else if (holder.itemViewType == 2) {
				(holder.itemView as? RecyclerListView)?.adapter?.notifyDataSetChanged()
			}
		}

		override fun getItemViewType(position: Int): Int {
			return when (position) {
				lastFilledItem -> 4
				firstEmptyViewCell -> 1
				hintsCell -> 2
				resentTitleCell -> 3
				else -> 0
			}
		}

//		val isSearching: Boolean
//			get() = !TextUtils.isEmpty(lastSearchText)

		fun getSpanSize(spanCount: Int, position: Int): Int {
			return if (position == hintsCell || position == resentTitleCell || position == firstEmptyViewCell || position == lastFilledItem) {
				spanCount
			}
			else {
				1
			}
		}
	}

	companion object {
		@JvmStatic
		fun createShareAlert(context: Context, messageObject: MessageObject?, text: String?, channel: Boolean, copyLink: String?, fullScreen: Boolean): ShareAlert {
			val arrayList = if (messageObject != null) {
				ArrayList<MessageObject>().also {
					it.add(messageObject)
				}
			}
			else {
				null
			}

			return ShareAlert(context, null, arrayList, text, null, channel, copyLink, null, fullScreen, false)
		}
	}
}
