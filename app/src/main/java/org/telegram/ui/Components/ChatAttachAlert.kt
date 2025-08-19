/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui.Components

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ImageSpan
import android.util.LongSparseArray
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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.core.util.forEach
import androidx.core.util.size
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject.canSendMedia
import org.telegram.messenger.ChatObject.hasAdminRights
import org.telegram.messenger.ChatObject.isChannel
import org.telegram.messenger.ContactsController.Companion.formatName
import org.telegram.messenger.DocumentObject
import org.telegram.messenger.Emoji
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.ImageReceiver.ImageReceiverDelegate
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController.SearchImage
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper.SendingMediaInfo
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject.getUserName
import org.telegram.messenger.UserObject.isUserSelf
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.vibrate
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.bot
import org.telegram.tgnet.userId
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.BottomSheet.BottomSheetDelegateInterface
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.BasePermissionsActivity
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatActivityEnterView.Companion.checkPremiumAnimatedEmoji
import org.telegram.ui.Components.ChatAttachAlertContactsLayout.PhonebookShareAlertDelegate
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.PassportActivity
import org.telegram.ui.PhotoPickerActivity.PhotoPickerActivityDelegate
import org.telegram.ui.PhotoPickerSearchActivity
import org.telegram.ui.sales.CreateMediaSaleFragment
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
open class ChatAttachAlert @SuppressLint("ClickableViewAccessibility") constructor(context: Context, val baseFragment: BaseFragment, private val forceDarkTheme: Boolean, showingFromDialog: Boolean) : BottomSheet(context, false), NotificationCenterDelegate, BottomSheetDelegateInterface {
	private val captionLimitView = NumberTextView(context)
	private val shadow = View(context)
	private val layouts = arrayOfNulls<AttachAlertLayout>(7)
	private val botAttachLayouts = LongSparseArray<ChatAttachAlertBotWebViewLayout>()

	private val frameLayout2: FrameLayout by lazy {
		object : FrameLayout(context) {
			private val p = Paint()
			private var color = 0

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

				val alphaOffset = (frameLayout2.measuredHeight - AndroidUtilities.dp(84f)) * (1f - alpha)

				shadow.translationY = -(frameLayout2.measuredHeight - AndroidUtilities.dp(84f)) + captionEditTextTopOffset + currentPanTranslationY + bottomPanelTranslation + alphaOffset + botMainButtonOffsetY

				val newColor = if (currentAttachLayout?.hasCustomBackground() == true) {
					currentAttachLayout?.customBackground ?: ResourcesCompat.getColor(resources, R.color.background, null)
				}
				else {
					ResourcesCompat.getColor(resources, if (forceDarkTheme) R.color.dark_background else R.color.background, null)
				}

				if (color != newColor) {
					color = newColor
					p.color = color
				}
				canvas.drawRect(0f, captionEditTextTopOffset, measuredWidth.toFloat(), measuredHeight.toFloat(), p)
			}

			override fun dispatchDraw(canvas: Canvas) {
				canvas.withClip(0f, captionEditTextTopOffset, measuredWidth.toFloat(), measuredHeight.toFloat()) {
					super.dispatchDraw(this)
				}
			}
		}
	}

	private val writeButtonContainer: FrameLayout by lazy {
		object : FrameLayout(context) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)

				if (currentAttachLayout === photoLayout) {
					info.text = LocaleController.formatPluralString("AccDescrSendPhotos", photoLayout.selectedItemsCount)
				}
				else if (currentAttachLayout === documentLayout) {
					info.text = LocaleController.formatPluralString("AccDescrSendFiles", documentLayout!!.selectedItemsCount)
				}
				else if (currentAttachLayout === audioLayout) {
					info.text = LocaleController.formatPluralString("AccDescrSendAudio", audioLayout!!.selectedItemsCount)
				}

				info.className = Button::class.java.name
				info.isLongClickable = true
				info.isClickable = true
			}
		}
	}

	private val writeButton = ImageView(context)
	private val writeButtonDrawable: Drawable

	private val selectedCountView: View by lazy {
		object : View(context) {
			override fun onDraw(canvas: Canvas) {
				val text = String.format(Locale.getDefault(), "%d", max(1, currentAttachLayout?.selectedItemsCount ?: 0))
				val textSize = ceil(textPaint.measureText(text).toDouble()).toInt()
				val size = max(AndroidUtilities.dp(16f) + textSize, AndroidUtilities.dp(24f))
				val cx = measuredWidth / 2
				val color = ResourcesCompat.getColor(resources, R.color.white, null)

				textPaint.color = ColorUtils.setAlphaComponent(color, (Color.alpha(color) * (0.58 + 0.42 * sendButtonEnabledProgress)).toInt())
				paint.color = ResourcesCompat.getColor(resources, R.color.background, null)
				rect.set((cx - size / 2).toFloat(), 0f, (cx + size / 2).toFloat(), measuredHeight.toFloat())

				canvas.drawRoundRect(rect, AndroidUtilities.dp(12f).toFloat(), AndroidUtilities.dp(12f).toFloat(), paint)

				paint.color = ResourcesCompat.getColor(resources, R.color.black, null)

				rect.set((cx - size / 2 + AndroidUtilities.dp(2f)).toFloat(), AndroidUtilities.dp(2f).toFloat(), (cx + size / 2 - AndroidUtilities.dp(2f)).toFloat(), (measuredHeight - AndroidUtilities.dp(2f)).toFloat())

				canvas.drawRoundRect(rect, AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(), paint)
				canvas.drawText(text, (cx - textSize / 2).toFloat(), AndroidUtilities.dp(16.2f).toFloat(), textPaint)
			}
		}
	}

	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val rect = RectF()
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val actionBarShadow = View(context)
	private val buttonsLayoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
	private val buttonsAdapter = ButtonsAdapter(context)
	private val botProgressView = RadialProgressView(context)
	private val botMainButtonTextView = TextView(context)
	private val exclusionRects = mutableListOf<Rect>()
	private val exclusionRect = Rect()
	private val pollsEnabled = false // TODO: set to true if polls are required
	private var avatarSearch = false
	protected var typeButtonsAvailable = false
	protected var searchItem = ActionBarMenuItem(context, null, 0, ResourcesCompat.getColor(context.resources, R.color.text, null), false)

	private val headerView: FrameLayout by lazy {
		object : FrameLayout(context) {
			override fun setAlpha(alpha: Float) {
				super.setAlpha(alpha)
				updateSelectedPosition(0)
				containerView.invalidate()
			}

			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(event: MotionEvent): Boolean {
				if (headerView.visibility != VISIBLE) {
					return false
				}

				return super.onTouchEvent(event)
			}

			override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
				if (headerView.visibility != VISIBLE) {
					return false
				}

				return super.onInterceptTouchEvent(event)
			}
		}
	}

	private var selectedView = LinearLayout(context)
	private var selectedArrowImageView = ImageView(context)
	private var mediaPreviewView = LinearLayout(context)
	private var mediaPreviewTextView = TextView(context)
	private var canOpenPreview = false
	private var openTransitionFinished = false
	private var viewChangeAnimator: Any? = null
	private var enterCommentEventSent = false
	private var botButtonProgressWasVisible = false
	private var botButtonWasVisible = false
	private var botMainButtonOffsetY = 0f
	private var buttonPressed = false
	private var mediaEnabled = true
	private var captionEditTextTopOffset = 0f
	private var chatActivityEnterViewAnimateFromTop = 0f
	private var topBackgroundAnimator: ValueAnimator? = null
	private var attachItemSize = AndroidUtilities.dp(78f)
	private var previousScrollOffsetY = 0
	private var bottomPanelTranslation = 0f
	private var appearSpringAnimation: SpringAnimation? = null
	private var buttonsAnimation: AnimatorSet? = null
	private var confirmationAlertShown = false
	val photoLayout by lazy { ChatAttachAlertPhotoLayout(this, context, forceDarkTheme) }
	private var translationProgress = 0f
	private var allowPassConfirmationAlert = false
	private var sendButtonEnabled = true
	private var currentPanTranslationY = 0f
	private var currentLimit: Int
	private var codepointCount = 0
	private var isSoundPicker = false
	private var sendPopupWindow: ActionBarPopupWindow? = null
	private var sendPopupLayout: ActionBarPopupWindowLayout? = null
	private var itemCells: Array<ActionBarMenuSubItem?>? = null
	private var contactsLayout: ChatAttachAlertContactsLayout? = null
	private var audioLayout: ChatAttachAlertAudioLayout? = null
	private var pollLayout: ChatAttachAlertPollLayout? = null
	private var locationLayout: ChatAttachAlertLocationLayout? = null
	private var photoPreviewLayout: ChatAttachAlertPhotoLayoutPreview? = null
	private var currentAttachLayout: AttachAlertLayout? = null
	private var nextAttachLayout: AttachAlertLayout? = null
	private var commentsAnimator: AnimatorSet? = null
	private var sendButtonEnabledProgress = 1f
	private var sendButtonColorAnimator: ValueAnimator? = null
	private var selectedId: Long
	private var actionBarAnimation: AnimatorSet? = null
	private var menuAnimator: AnimatorSet? = null
	private var baseSelectedTextViewTranslationY = 0f
	private var menuShowed = false

	@JvmField
	val buttonsRecyclerView = object : RecyclerListView(context) {
		override fun setTranslationY(translationY: Float) {
			super.setTranslationY(translationY)
			currentAttachLayout?.onButtonsTranslationYUpdated()
		}
	}

	@JvmField
	val inBubbleMode: Boolean = baseFragment is ChatActivity && baseFragment.isInBubbleMode()

	val commentTextView: EditTextEmoji by lazy {
		object : EditTextEmoji(context, sizeNotifierFrameLayout, null, STYLE_DIALOG, true) {
			private var shouldAnimateEditTextWithBounds = false
			private var messageEditTextPreDrawHeight = 0
			private var messageEditTextPreDrawScrollY = 0
			private var messageEditTextAnimator: ValueAnimator? = null

			override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
				if (!enterCommentEventSent) {
					makeFocusable(commentTextView.editText, ev.x > commentTextView.editText.left && ev.x < commentTextView.editText.right && ev.y > commentTextView.editText.top && ev.y < commentTextView.editText.bottom)
				}

				return super.onInterceptTouchEvent(ev)
			}

			override fun dispatchDraw(canvas: Canvas) {
				if (shouldAnimateEditTextWithBounds) {
					val editText = commentTextView.editText
					val dy = (messageEditTextPreDrawHeight - editText.measuredHeight + (messageEditTextPreDrawScrollY - editText.scrollY)).toFloat()

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
					messageEditTextPreDrawHeight = editText.measuredHeight
					messageEditTextPreDrawScrollY = editText.scrollY
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

			override fun bottomPanelTranslationY(translation: Float) {
				bottomPanelTranslation = translation
				//     buttonsRecyclerView.setTranslationY(translation);
				frameLayout2.translationY = translation
				writeButtonContainer.translationY = translation
				selectedCountView.translationY = translation
				frameLayout2.invalidate()
				updateLayout(currentAttachLayout, true, 0)
			}

			override fun closeParent() {
				super@ChatAttachAlert.dismiss()
			}
		}
	}

	@JvmField
	var avatarPicker = 0

	@JvmField
	var cornerRadius = 1.0f

	val actionBar = object : ActionBar(context) {
		override fun setAlpha(alpha: Float) {
			val oldAlpha = getAlpha()

			super.setAlpha(alpha)

			if (oldAlpha != alpha) {
				containerView.invalidate()

				if (frameLayout2.tag == null) {
					if (currentAttachLayout == null || currentAttachLayout?.shouldHideBottomButtons() == true) {
						buttonsRecyclerView.alpha = 1.0f - alpha
						shadow.alpha = 1.0f - alpha
						buttonsRecyclerView.translationY = AndroidUtilities.dp(44f) * alpha
					}

					frameLayout2.translationY = AndroidUtilities.dp(48f) * alpha
					shadow.translationY = AndroidUtilities.dp(84f) * alpha + botMainButtonOffsetY
				}
				else if (currentAttachLayout == null) {
					val value = if (alpha == 0.0f) 1.0f else 0.0f

					if (buttonsRecyclerView.alpha != value) {
						buttonsRecyclerView.alpha = value
					}
				}
			}
		}
	}

	@JvmField
	val selectedMenuItem = ActionBarMenuItem(context, null, 0, ResourcesCompat.getColor(context.resources, R.color.text, null), false)

	val doneItem = ActionBarMenuItem(context, null, 0, ResourcesCompat.getColor(context.resources, R.color.brand, null), true)
	val selectedTextView = TextView(context)

	@JvmField
	var sizeNotifierFrameLayout: SizeNotifierFrameLayout

	private val attachAlertProgress = object : AnimationProperties.FloatProperty<ChatAttachAlert>("openProgress") {
		private val openProgress = 0f

		override fun setValue(`object`: ChatAttachAlert, value: Float) {
			var a = 0
			val n = buttonsRecyclerView.childCount

			while (a < n) {
				val startTime = 32.0f * (3 - a)
				val child = buttonsRecyclerView.getChildAt(a)
				var scale: Float

				if (value > startTime) {
					var elapsedTime = value - startTime

					if (elapsedTime <= 200.0f) {
						scale = 1.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(elapsedTime / 200.0f)
						child.alpha = CubicBezierInterpolator.EASE_BOTH.getInterpolation(elapsedTime / 200.0f)
					}
					else {
						child.alpha = 1.0f
						elapsedTime -= 200.0f

						scale = if (elapsedTime <= 100.0f) {
							1.1f - 0.1f * CubicBezierInterpolator.EASE_IN.getInterpolation(elapsedTime / 100.0f)
						}
						else {
							1.0f
						}
					}
				}
				else {
					scale = 0f
				}

				if (child is AttachBotButton) {
					child.nameTextView.scaleX = scale
					child.nameTextView.scaleY = scale
					child.imageView.scaleX = scale
					child.imageView.scaleY = scale
				}

				a++
			}
		}

		override operator fun get(`object`: ChatAttachAlert): Float {
			return openProgress
		}
	}

	@JvmField
	var editingMessageObject: MessageObject? = null

	@JvmField
	var currentAccount = UserConfig.selectedAccount

	@JvmField
	var maxSelectedPhotos = -1

	@JvmField
	var allowOrder = true

	@JvmField
	var openWithFrontFaceCamera = false

	var chatAttachViewDelegate: ChatAttachViewDelegate? = null
		private set

	@JvmField
	var scrollOffsetY = IntArray(2)

	@JvmField
	var paused = false

	var documentLayout: ChatAttachAlertDocumentLayout? = null
		private set

	private val attachAlertLayoutTranslation = object : AnimationProperties.FloatProperty<AttachAlertLayout>("translation") {
		override fun setValue(`object`: AttachAlertLayout, value: Float) {
			val nextAttachLayout = nextAttachLayout
			val currentAttachLayout = currentAttachLayout

			translationProgress = value

			if (nextAttachLayout is ChatAttachAlertPhotoLayoutPreview || currentAttachLayout is ChatAttachAlertPhotoLayoutPreview) {
				val width = max(nextAttachLayout?.width ?: 0, currentAttachLayout?.width ?: 0)

				if (nextAttachLayout is ChatAttachAlertPhotoLayoutPreview) {
					currentAttachLayout?.translationX = value * -width
					nextAttachLayout.setTranslationX((1f - value) * width)
				}
				else {
					currentAttachLayout?.translationX = value * width
					nextAttachLayout?.translationX = -width * (1f - value)
				}
			}
			else {
				if (value > 0.7f) {
					val alpha = 1.0f - (1.0f - value) / 0.3f
					if (nextAttachLayout === locationLayout) {
						currentAttachLayout?.alpha = 1.0f - alpha
						nextAttachLayout?.alpha = 1.0f
					}
					else {
						nextAttachLayout?.alpha = alpha
						nextAttachLayout?.onHideShowProgress(alpha)
					}
				}
				else {
					if (nextAttachLayout === locationLayout) {
						nextAttachLayout?.alpha = 0.0f
					}
				}
				if (nextAttachLayout === pollLayout || currentAttachLayout === pollLayout) {
					updateSelectedPosition(if (nextAttachLayout === pollLayout) 1 else 0)
				}

				nextAttachLayout?.translationY = AndroidUtilities.dp(78f) * value
				currentAttachLayout?.onHideShowProgress(1.0f - min(1.0f, value / 0.7f))
				currentAttachLayout?.onContainerTranslationUpdated(currentPanTranslationY)
			}

			containerView.invalidate()
		}

		override operator fun get(`object`: AttachAlertLayout): Float {
			return translationProgress
		}
	}

	init {
		drawNavigationBar = true
		openInterpolator = OvershootInterpolator(0.7f)
		useSmoothKeyboard = true
		setDelegate(this)

		NotificationCenter.getInstance(currentAccount).let {
			it.addObserver(this, NotificationCenter.reloadInlineHints)
			it.addObserver(this, NotificationCenter.attachMenuBotsDidLoad)
			it.addObserver(this, NotificationCenter.currentUserPremiumStatusChanged)
		}

		exclusionRects.add(exclusionRect)

		sizeNotifierFrameLayout = object : SizeNotifierFrameLayout(context) {
			private val bulletinDelegate: Bulletin.Delegate = object : Bulletin.Delegate {
				override fun getBottomOffset(tag: Int): Int {
					return AndroidUtilities.dp(52f)
				}
			}

			private val rect = RectF()
			private var lastNotifyWidth = 0
			private var ignoreLayout = false
			private var initialTranslationY = 0f

			override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
				if (currentAttachLayout?.onContainerViewTouchEvent(ev) == true) {
					return true
				}

				if (ev.action == MotionEvent.ACTION_DOWN && scrollOffsetY[0] != 0 && ev.y < currentTop && actionBar.alpha == 0.0f) {
					onDismissWithTouchOutside()
					return true
				}

				return super.onInterceptTouchEvent(ev)
			}

			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(event: MotionEvent): Boolean {
				return if (currentAttachLayout?.onContainerViewTouchEvent(event) == true) {
					true
				}
				else {
					!isDismissed && super.onTouchEvent(event)
				}
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val totalHeight = if (layoutParams.height > 0) {
					layoutParams.height
				}
				else {
					MeasureSpec.getSize(heightMeasureSpec)
				}

				if (!inBubbleMode) {
					ignoreLayout = true
					setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0)
					ignoreLayout = false
				}

				val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - backgroundPaddingLeft * 2

				if (AndroidUtilities.isTablet()) {
					selectedMenuItem.setAdditionalYOffset(-AndroidUtilities.dp(3f))
				}
				else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					selectedMenuItem.setAdditionalYOffset(0)
				}
				else {
					selectedMenuItem.setAdditionalYOffset(-AndroidUtilities.dp(3f))
				}

				var layoutParams = actionBarShadow.layoutParams as LayoutParams
				layoutParams.topMargin = ActionBar.getCurrentActionBarHeight()
				layoutParams = doneItem.layoutParams as LayoutParams
				layoutParams.height = ActionBar.getCurrentActionBarHeight()

				ignoreLayout = true

				val newSize = (availableWidth / min(4.5f, buttonsAdapter.itemCount.toFloat())).toInt()

				if (attachItemSize != newSize) {
					attachItemSize = newSize

					AndroidUtilities.runOnUIThread {
						buttonsAdapter.notifyDataSetChanged()
					}
				}

				ignoreLayout = false

				onMeasureInternal(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY))
			}

			private fun onMeasureInternal(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				@Suppress("NAME_SHADOWING") var heightMeasureSpec = heightMeasureSpec
				var widthSize = MeasureSpec.getSize(widthMeasureSpec)
				var heightSize = MeasureSpec.getSize(heightMeasureSpec)

				setMeasuredDimension(widthSize, heightSize)
				widthSize -= backgroundPaddingLeft * 2

				val keyboardSize = if (SharedConfig.smoothKeyboard) 0 else measureKeyboardHeight()

				if (!commentTextView.isWaitingForKeyboardOpen && keyboardSize <= AndroidUtilities.dp(20f) && !commentTextView.isPopupShowing && !commentTextView.isAnimatePopupClosing) {
					ignoreLayout = true
					commentTextView.hideEmojiView()
					ignoreLayout = false
				}

				if (keyboardSize <= AndroidUtilities.dp(20f)) {
					val paddingBottom = if (SharedConfig.smoothKeyboard && keyboardVisible) {
						0
					}
					else {
						commentTextView.emojiPadding
					}

					if (!AndroidUtilities.isInMultiwindow) {
						heightSize -= paddingBottom
						heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY)
					}

					ignoreLayout = true

					currentAttachLayout?.onPreMeasure(widthSize, heightSize)
					nextAttachLayout?.onPreMeasure(widthSize, heightSize)

					ignoreLayout = false
				}

				val childCount = childCount

				for (i in 0 until childCount) {
					val child = getChildAt(i)

					if (child == null || child.isGone) {
						continue
					}

					if (commentTextView.isPopupView(child)) {
						if (inBubbleMode) {
							child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize + paddingTop, MeasureSpec.EXACTLY))
						}
						else if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
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
				if (lastNotifyWidth != r - l) {
					lastNotifyWidth = r - l

					if (sendPopupWindow?.isShowing == true) {
						sendPopupWindow?.dismiss()
					}
				}

				val count = childCount

				if (Build.VERSION.SDK_INT >= 29) {
					exclusionRect[l, t, r] = b
					systemGestureExclusionRects = exclusionRects
				}

				val keyboardSize = measureKeyboardHeight()
				var paddingBottom = paddingBottom

				paddingBottom += if (SharedConfig.smoothKeyboard && keyboardVisible) {
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
						Gravity.TOP -> lp.topMargin + paddingTop
						Gravity.CENTER_VERTICAL -> (b - paddingBottom - t - height) / 2 + lp.topMargin - lp.bottomMargin
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
				updateLayout(currentAttachLayout, false, 0)
				updateLayout(nextAttachLayout, false, 0)
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}

			private fun drawChildBackground(canvas: Canvas, child: View?) {
				if (child is AttachAlertLayout) {
					canvas.withTranslation(0f, currentPanTranslationY) {
						val viewAlpha = (255 * child.getAlpha()).toInt()
						val actionBarType = child.needsActionBar()
						val offset = AndroidUtilities.dp(13f) + AndroidUtilities.dp(headerView.alpha * 26)
						var top = getScrollOffsetY(0) - backgroundPaddingTop - offset

						if (currentSheetAnimationType == 1 || viewChangeAnimator != null) {
							top += child.getTranslationY().toInt()
						}

						var y = top + AndroidUtilities.dp(20f)
						var height = measuredHeight + AndroidUtilities.dp(45f) + backgroundPaddingTop
						var rad = 1.0f
						val h = if (actionBarType != 0) ActionBar.getCurrentActionBarHeight() else backgroundPaddingTop

						if (actionBarType == 2) {
							if (top < h) {
								rad = max(0f, 1.0f - (h - top) / backgroundPaddingTop.toFloat())
							}
						}
						else if (top + backgroundPaddingTop < h) {
							var toMove = offset.toFloat()

							if (child === locationLayout) {
								toMove += AndroidUtilities.dp(11f).toFloat()
							}
							else if (child === pollLayout) {
								toMove -= AndroidUtilities.dp(3f).toFloat()
							}
							else {
								toMove += AndroidUtilities.dp(4f).toFloat()
							}

							val moveProgress = min(1.0f, (h - top - backgroundPaddingTop) / toMove)
							val availableToMove = h - toMove
							val diff = (availableToMove * moveProgress).toInt()

							top -= diff
							y -= diff
							height += diff
							rad = 1.0f - moveProgress
						}

						if (!inBubbleMode) {
							top += AndroidUtilities.statusBarHeight
							y += AndroidUtilities.statusBarHeight
							height -= AndroidUtilities.statusBarHeight
						}

						val backgroundColor = if (currentAttachLayout?.hasCustomBackground() == true) {
							currentAttachLayout?.customBackground ?: ResourcesCompat.getColor(resources, R.color.background, null)
						}
						else {
							ResourcesCompat.getColor(resources, if (forceDarkTheme) R.color.dark_background else R.color.background, null)
						}

						shadowDrawable.alpha = viewAlpha
						shadowDrawable.setBounds(0, top, measuredWidth, height)
						shadowDrawable.draw(this)

						if (actionBarType == 2) {
							Theme.dialogs_onlineCirclePaint.color = backgroundColor
							Theme.dialogs_onlineCirclePaint.alpha = viewAlpha

							rect.set(backgroundPaddingLeft.toFloat(), (backgroundPaddingTop + top).toFloat(), (measuredWidth - backgroundPaddingLeft).toFloat(), (backgroundPaddingTop + top + AndroidUtilities.dp(24f)).toFloat())

							withClip(rect.left, rect.top, rect.right, rect.top + rect.height() / 2) {
								drawRoundRect(rect, AndroidUtilities.dp(12f) * rad, AndroidUtilities.dp(12f) * rad, Theme.dialogs_onlineCirclePaint)
							}
						}
						if (rad != 1.0f && actionBarType != 2) {
							Theme.dialogs_onlineCirclePaint.color = backgroundColor
							Theme.dialogs_onlineCirclePaint.alpha = viewAlpha

							rect.set(backgroundPaddingLeft.toFloat(), (backgroundPaddingTop + top).toFloat(), (measuredWidth - backgroundPaddingLeft).toFloat(), (backgroundPaddingTop + top + AndroidUtilities.dp(24f)).toFloat())

							withClip(rect.left, rect.top, rect.right, rect.top + rect.height() / 2) {
								drawRoundRect(rect, AndroidUtilities.dp(12f) * rad, AndroidUtilities.dp(12f) * rad, Theme.dialogs_onlineCirclePaint)
							}
						}

						if (headerView.alpha != 1.0f && rad != 0f) {
							val w = AndroidUtilities.dp(36f)

							rect.set(((measuredWidth - w) / 2).toFloat(), y.toFloat(), ((measuredWidth + w) / 2).toFloat(), (y + AndroidUtilities.dp(4f)).toFloat())

							val color: Int
							val alphaProgress: Float

							if (actionBarType == 2) {
								color = 0x20000000
								alphaProgress = rad
							}
							else {
								color = context.getColor(R.color.light_background)
								alphaProgress = 1.0f - headerView.alpha
							}

							val alpha = Color.alpha(color)

							Theme.dialogs_onlineCirclePaint.color = color
							Theme.dialogs_onlineCirclePaint.alpha = (alpha * alphaProgress * rad * child.getAlpha()).toInt()

							drawRoundRect(rect, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), Theme.dialogs_onlineCirclePaint)
						}
					}
				}
			}

			override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
				if (child is AttachAlertLayout && child.getAlpha() > 0.0f) {
					canvas.save()
					canvas.translate(0f, currentPanTranslationY)

					val viewAlpha = (255 * child.getAlpha()).toInt()
					val actionBarType = child.needsActionBar()
					val offset = AndroidUtilities.dp(13f) + AndroidUtilities.dp(headerView.alpha * 26)
					var top = getScrollOffsetY(if (child === currentAttachLayout) 0 else 1) - backgroundPaddingTop - offset

					if (currentSheetAnimationType == 1 || viewChangeAnimator != null) {
						top += child.getTranslationY().toInt()
					}

					var y = top + AndroidUtilities.dp(20f)
					var height = measuredHeight + AndroidUtilities.dp(45f) + backgroundPaddingTop
					var rad = 1.0f
					val h = if (actionBarType != 0) ActionBar.getCurrentActionBarHeight() else backgroundPaddingTop

					if (actionBarType == 2) {
						if (top < h) {
							rad = max(0f, 1.0f - (h - top) / backgroundPaddingTop.toFloat())
						}
					}
					else if (top + backgroundPaddingTop < h) {
						var toMove = offset.toFloat()

						if (child === locationLayout) {
							toMove += AndroidUtilities.dp(11f).toFloat()
						}
						else if (child === pollLayout) {
							toMove -= AndroidUtilities.dp(3f).toFloat()
						}
						else {
							toMove += AndroidUtilities.dp(4f).toFloat()
						}

						val moveProgress = min(1.0f, (h - top - backgroundPaddingTop) / toMove)
						val availableToMove = h - toMove
						val diff = (availableToMove * moveProgress).toInt()

						top -= diff
						y -= diff
						height += diff
						rad = 1.0f - moveProgress
					}

					if (!inBubbleMode) {
						top += AndroidUtilities.statusBarHeight
						y += AndroidUtilities.statusBarHeight
						height -= AndroidUtilities.statusBarHeight
					}

					val backgroundColor = if (currentAttachLayout?.hasCustomBackground() == true) {
						currentAttachLayout?.customBackground ?: ResourcesCompat.getColor(resources, R.color.background, null)
					}
					else {
						ResourcesCompat.getColor(resources, if (forceDarkTheme) R.color.dark_background else R.color.background, null)
					}

					val drawBackground = !(currentAttachLayout === photoPreviewLayout || nextAttachLayout === photoPreviewLayout || currentAttachLayout === photoLayout && nextAttachLayout == null)

					if (drawBackground) {
						shadowDrawable.alpha = viewAlpha
						shadowDrawable.setBounds(0, top, measuredWidth, height)
						shadowDrawable.draw(canvas)

						if (actionBarType == 2) {
							Theme.dialogs_onlineCirclePaint.color = backgroundColor
							Theme.dialogs_onlineCirclePaint.alpha = viewAlpha

							rect.set(backgroundPaddingLeft.toFloat(), (backgroundPaddingTop + top).toFloat(), (measuredWidth - backgroundPaddingLeft).toFloat(), (backgroundPaddingTop + top + AndroidUtilities.dp(24f)).toFloat())

							canvas.withClip(rect.left, rect.top, rect.right, rect.top + rect.height() / 2) {
								drawRoundRect(rect, AndroidUtilities.dp(12f) * rad, AndroidUtilities.dp(12f) * rad, Theme.dialogs_onlineCirclePaint)
							}
						}
					}

					val clip = !drawBackground && headerView.alpha > .9f && (currentAttachLayout is ChatAttachAlertPhotoLayoutPreview || nextAttachLayout is ChatAttachAlertPhotoLayoutPreview) && viewChangeAnimator is SpringAnimation && (viewChangeAnimator as SpringAnimation).isRunning

					if (clip) {
						canvas.save()

						val finalMove = if (AndroidUtilities.isTablet()) {
							16
						}
						else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
							6
						}
						else {
							12
						}

						val clipTop = (baseSelectedTextViewTranslationY + AndroidUtilities.statusBarHeight + headerView.height + AndroidUtilities.dp(finalMove * headerView.alpha)).toInt()

						canvas.clipRect(backgroundPaddingLeft, clipTop, measuredWidth - backgroundPaddingLeft, measuredHeight)
					}

					val result = super.drawChild(canvas, child, drawingTime)

					if (clip) {
						canvas.restore()
					}

					if (drawBackground) {
						if (rad != 1.0f && actionBarType != 2) {
							Theme.dialogs_onlineCirclePaint.color = backgroundColor
							Theme.dialogs_onlineCirclePaint.alpha = viewAlpha

							rect.set(backgroundPaddingLeft.toFloat(), (backgroundPaddingTop + top).toFloat(), (measuredWidth - backgroundPaddingLeft).toFloat(), (backgroundPaddingTop + top + AndroidUtilities.dp(24f)).toFloat())

							canvas.withClip(rect.left, rect.top, rect.right, rect.top + rect.height() / 2) {
								drawRoundRect(rect, AndroidUtilities.dp(12f) * rad, AndroidUtilities.dp(12f) * rad, Theme.dialogs_onlineCirclePaint)
							}
						}

						if (headerView.alpha != 1.0f && rad != 0f) {
							val w = AndroidUtilities.dp(36f)

							rect.set(((measuredWidth - w) / 2).toFloat(), y.toFloat(), ((measuredWidth + w) / 2).toFloat(), (y + AndroidUtilities.dp(4f)).toFloat())

							val color: Int
							val alphaProgress: Float

							if (actionBarType == 2) {
								color = 0x20000000
								alphaProgress = rad
							}
							else {
								color = ResourcesCompat.getColor(resources, R.color.gray_border, null)
								alphaProgress = 1.0f - headerView.alpha
							}

							val alpha = Color.alpha(color)

							Theme.dialogs_onlineCirclePaint.color = color
							Theme.dialogs_onlineCirclePaint.alpha = (alpha * alphaProgress * rad * child.getAlpha()).toInt()

							canvas.drawRoundRect(rect, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), Theme.dialogs_onlineCirclePaint)
						}
					}

					canvas.restore()

					return result
				}

				return super.drawChild(canvas, child, drawingTime)
			}

			override fun onDraw(canvas: Canvas) {
				if (inBubbleMode) {
					return
				}

				val color1 = if (currentAttachLayout?.hasCustomBackground() == true) {
					currentAttachLayout?.customBackground ?: ResourcesCompat.getColor(resources, R.color.background, null)
				}
				else {
					ResourcesCompat.getColor(resources, if (forceDarkTheme) R.color.dark_background else R.color.background, null)
				}

				val finalColor = Color.argb((255 * actionBar.alpha).toInt(), Color.red(color1), Color.green(color1), Color.blue(color1))

				Theme.dialogs_onlineCirclePaint.color = finalColor

				canvas.drawRect(backgroundPaddingLeft.toFloat(), currentPanTranslationY, (measuredWidth - backgroundPaddingLeft).toFloat(), AndroidUtilities.statusBarHeight + currentPanTranslationY, Theme.dialogs_onlineCirclePaint)
			}

			private val currentTop: Int
				get() {
					var y = scrollOffsetY[0] - backgroundPaddingTop * 2 - (AndroidUtilities.dp(13f) + AndroidUtilities.dp(headerView.alpha * 26)) + AndroidUtilities.dp(20f)

					if (!inBubbleMode) {
						y += AndroidUtilities.statusBarHeight
					}

					return y
				}

			override fun dispatchDraw(canvas: Canvas) {
				canvas.withClip(0f, paddingTop + currentPanTranslationY, measuredWidth.toFloat(), measuredHeight + currentPanTranslationY - paddingBottom) {
					if ((currentAttachLayout === photoPreviewLayout || nextAttachLayout === photoPreviewLayout || currentAttachLayout === photoLayout) && nextAttachLayout == null) {
						drawChildBackground(this, currentAttachLayout)
					}

					super.dispatchDraw(this)
				}
			}

			override fun setTranslationY(translationY: Float) {
				@Suppress("NAME_SHADOWING") var translationY = translationY

				translationY += currentPanTranslationY

				if (currentSheetAnimationType == 0) {
					initialTranslationY = translationY
				}

				if (currentSheetAnimationType == 1) {
					if (translationY < 0) {
						currentAttachLayout?.translationY = translationY

						if (avatarPicker != 0) {
							headerView.translationY = baseSelectedTextViewTranslationY + translationY - currentPanTranslationY
						}

						translationY = 0f

						buttonsRecyclerView.translationY = 0f
					}
					else {
						currentAttachLayout?.translationY = 0f
						buttonsRecyclerView.translationY = -translationY + buttonsRecyclerView.measuredHeight * (translationY / initialTranslationY)
					}

					containerView.invalidate()
				}

				super.setTranslationY(translationY - currentPanTranslationY)

				if (currentSheetAnimationType != 1) {
					currentAttachLayout?.onContainerTranslationUpdated(currentPanTranslationY)
				}
			}

			override fun onAttachedToWindow() {
				super.onAttachedToWindow()
				adjustPanLayoutHelper?.setResizableView(this)
				adjustPanLayoutHelper?.onAttach()
				commentTextView.adjustPanLayoutHelper = adjustPanLayoutHelper
				Bulletin.addDelegate(this, bulletinDelegate)
			}

			override fun onDetachedFromWindow() {
				super.onDetachedFromWindow()
				adjustPanLayoutHelper?.onDetach()
				Bulletin.removeDelegate(this)
			}
		}

		sizeNotifierFrameLayout.setDelegate { _, _ ->
			if (currentAttachLayout === photoPreviewLayout) {
				currentAttachLayout?.invalidate()
			}
		}

		containerView = sizeNotifierFrameLayout
		containerView.setWillNotDraw(false)
		containerView.clipChildren = false
		containerView.clipToPadding = false
		containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0)

		actionBar.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		actionBar.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar.setItemsColor(ResourcesCompat.getColor(context.resources, R.color.text, null), false)
		actionBar.setItemsBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.brand, null), false)
		actionBar.setTitleColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		actionBar.occupyStatusBar = false
		actionBar.alpha = 0.0f

		actionBar.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					if (currentAttachLayout?.onBackPressed() == true) {
						return
					}

					dismiss()
				}
				else {
					currentAttachLayout?.onMenuItemClick(id)
				}
			}
		})

		selectedMenuItem.setLongClickEnabled(false)
		selectedMenuItem.setIcon(R.drawable.overflow_menu)
		selectedMenuItem.contentDescription = context.getString(R.string.AccDescrMoreOptions)
		selectedMenuItem.visibility = View.INVISIBLE
		selectedMenuItem.alpha = 0.0f
		selectedMenuItem.setSubMenuOpenSide(2)

		selectedMenuItem.setDelegate {
			actionBar.getActionBarMenuOnItemClick().onItemClick(it)
		}

		selectedMenuItem.setAdditionalYOffset(AndroidUtilities.dp(72f))
		selectedMenuItem.translationX = AndroidUtilities.dp(6f).toFloat()
		selectedMenuItem.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.light_background, null), 6)

		selectedMenuItem.setOnClickListener {
			selectedMenuItem.toggleSubMenu()
		}

		doneItem.setLongClickEnabled(false)
		doneItem.setText(context.getString(R.string.Create).uppercase())
		doneItem.visibility = View.INVISIBLE
		doneItem.alpha = 0.0f
		doneItem.translationX = -AndroidUtilities.dp(12f).toFloat()
		doneItem.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.light_background, null), 3)

		doneItem.setOnClickListener {
			currentAttachLayout?.onMenuItemClick(40)
		}

		searchItem.setLongClickEnabled(false)
		searchItem.setIcon(R.drawable.ic_search_menu)
		searchItem.contentDescription = context.getString(R.string.Search)
		searchItem.visibility = View.INVISIBLE
		searchItem.alpha = 0.0f
		searchItem.translationX = -AndroidUtilities.dp(42f).toFloat()
		searchItem.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.light_background, null), 6)

		searchItem.setOnClickListener {
			if (avatarPicker != 0) {
				chatAttachViewDelegate?.openAvatarsSearch()
				dismiss()
				return@setOnClickListener
			}

			val photos = mutableMapOf<Any, Any>()
			val order = mutableListOf<Any>()

			val fragment = PhotoPickerSearchActivity(photos, order, 0, true, baseFragment as ChatActivity?)

			fragment.setDelegate(object : PhotoPickerActivityDelegate {
				private var sendPressed = false

				override fun selectedPhotosChanged() {
					// unused
				}

				override fun actionButtonPressed(canceled: Boolean, notify: Boolean, scheduleDate: Int) {
					if (canceled) {
						return
					}

					if (photos.isEmpty() || sendPressed) {
						return
					}

					sendPressed = true

					val media = mutableListOf<SendingMediaInfo>()

					for (a in order.indices) {
						val searchImage = photos[order[a]] as? SearchImage ?: continue

						val info = SendingMediaInfo()

						media.add(info)

						if (searchImage.imagePath != null) {
							info.path = searchImage.imagePath
						}
						else {
							info.searchImage = searchImage
						}

						info.thumbPath = searchImage.thumbPath
						info.videoEditedInfo = searchImage.editedInfo
						info.caption = searchImage.caption?.toString()
						info.entities = searchImage.entities
						info.masks = searchImage.stickers
						info.ttl = searchImage.ttl

						if (searchImage.inlineResult != null && searchImage.type == 1) {
							info.inlineResult = searchImage.inlineResult
							info.params = searchImage.params
						}

						searchImage.date = (System.currentTimeMillis() / 1000).toInt()
					}

					(baseFragment as? ChatActivity)?.didSelectSearchPhotos(media, notify, scheduleDate)
				}

				override fun onCaptionChanged(text: CharSequence) {
					// unused
				}
			})

			fragment.setMaxSelectedPhotos(maxSelectedPhotos, allowOrder)

			if (showingFromDialog) {
				baseFragment.showAsSheet(fragment)
			}
			else {
				baseFragment.presentFragment(fragment)
			}

			dismiss()
		}

		headerView.setOnClickListener {
			updatePhotoPreview(currentAttachLayout !== photoPreviewLayout)
		}

		headerView.alpha = 0.0f
		headerView.visibility = View.INVISIBLE

		selectedView.orientation = LinearLayout.HORIZONTAL
		selectedView.gravity = Gravity.CENTER_VERTICAL

		selectedTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		selectedTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		selectedTextView.typeface = Theme.TYPEFACE_BOLD
		selectedTextView.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL

		selectedView.addView(selectedTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL))

		val arrowRight = ResourcesCompat.getDrawable(context.resources, R.drawable.attach_arrow_right, null)?.mutate()
		arrowRight?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.text, null), PorterDuff.Mode.SRC_IN)

		selectedArrowImageView.setImageDrawable(arrowRight)
		selectedArrowImageView.visibility = View.GONE

		selectedView.addView(selectedArrowImageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 1, 0, 0))

		selectedView.alpha = 1f

		headerView.addView(selectedView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat()))

		mediaPreviewView.orientation = LinearLayout.HORIZONTAL
		mediaPreviewView.gravity = Gravity.CENTER_VERTICAL

		val arrowView = ImageView(context)

		val arrowLeft = ResourcesCompat.getDrawable(context.resources, R.drawable.attach_arrow_left, null)?.mutate()
		arrowLeft?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.text, null), PorterDuff.Mode.SRC_IN)

		arrowView.setImageDrawable(arrowLeft)

		mediaPreviewView.addView(arrowView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 1, 4, 0))

		mediaPreviewTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		mediaPreviewTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		mediaPreviewTextView.typeface = Theme.TYPEFACE_BOLD
		mediaPreviewTextView.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
		mediaPreviewTextView.text = context.getString(R.string.AttachMediaPreview)

		mediaPreviewView.alpha = 0f
		mediaPreviewView.addView(mediaPreviewTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL))

		headerView.addView(mediaPreviewView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat()))

		layouts[0] = photoLayout

		photoLayout.translationX = 0f

		currentAttachLayout = photoLayout

		selectedId = 1

		containerView.addView(photoLayout, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		containerView.addView(headerView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.LEFT, 23f, 0f, 48f, 0f))
		containerView.addView(actionBar, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
		containerView.addView(selectedMenuItem, createFrame(48, 48, Gravity.TOP or Gravity.RIGHT))
		containerView.addView(searchItem, createFrame(48, 48, Gravity.TOP or Gravity.RIGHT))
		containerView.addView(doneItem, createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.TOP or Gravity.RIGHT))

		actionBarShadow.alpha = 0.0f
		actionBarShadow.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.shadow, null))

		containerView.addView(actionBarShadow, createFrame(LayoutHelper.MATCH_PARENT, 1f))

		shadow.setBackgroundResource(R.drawable.attach_shadow)

		shadow.background.colorFilter = PorterDuffColorFilter(-0x1000000, PorterDuff.Mode.MULTIPLY)

		containerView.addView(shadow, createFrame(LayoutHelper.MATCH_PARENT, 2f, Gravity.BOTTOM or Gravity.LEFT, 0f, 0f, 0f, 84f))

		buttonsRecyclerView.adapter = buttonsAdapter
		buttonsRecyclerView.layoutManager = buttonsLayoutManager
		buttonsRecyclerView.isVerticalScrollBarEnabled = false
		buttonsRecyclerView.isHorizontalScrollBarEnabled = false
		buttonsRecyclerView.itemAnimator = null
		buttonsRecyclerView.layoutAnimation = null
		buttonsRecyclerView.setGlowColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))
		buttonsRecyclerView.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		buttonsRecyclerView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

		containerView.addView(buttonsRecyclerView, createFrame(LayoutHelper.MATCH_PARENT, 84, Gravity.BOTTOM or Gravity.LEFT))

		buttonsRecyclerView.setOnItemClickListener { view, _ ->
			val parentActivity = baseFragment.parentActivity ?: return@setOnItemClickListener

			if (view is AttachButton) {
				when (view.getTag() as? Int) {
					1 -> {
						showLayout(photoLayout)
					}

					3 -> {
						val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
							Manifest.permission.READ_MEDIA_AUDIO
						}
						else {
							Manifest.permission.READ_EXTERNAL_STORAGE
						}

						if (parentActivity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
							parentActivity.requestPermissions(arrayOf(permission), BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE)
							return@setOnItemClickListener
						}

						openAudioLayout(true)
					}

					4 -> {
						if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
							if (parentActivity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
								parentActivity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE)
								return@setOnItemClickListener
							}
						}

						openDocumentsLayout(true)
					}

					5 -> {
						openContactsLayout()
					}

					6 -> {
						if (!AndroidUtilities.isMapsInstalled(baseFragment)) {
							return@setOnItemClickListener
						}

						if (locationLayout == null) {
							locationLayout = ChatAttachAlertLocationLayout(this, getContext())

							layouts[5] = locationLayout

							locationLayout?.setDelegate { location, live, notify, scheduleDate ->
								(baseFragment as? ChatActivity)?.didSelectLocation(location, live, notify, scheduleDate)
							}
						}

						showLayout(locationLayout!!)
					}

					9 -> {
						if (pollLayout == null) {
							pollLayout = ChatAttachAlertPollLayout(this, getContext())

							layouts[1] = pollLayout

							pollLayout?.setDelegate { poll, params, notify, scheduleDate ->
								(baseFragment as? ChatActivity)?.sendPoll(poll, params, notify, scheduleDate)
							}
						}

						showLayout(pollLayout!!)
					}

					else -> {
						chatAttachViewDelegate?.didPressedButton(view.getTag() as Int, arg = true, notify = true, scheduleDate = 0, forceDocument = false)
					}
				}

				val left = view.getLeft()
				val right = view.getRight()
				val extra = AndroidUtilities.dp(10f)

				if (left - extra < 0) {
					buttonsRecyclerView.smoothScrollBy(left - extra, 0)
				}
				else if (right + extra > buttonsRecyclerView.measuredWidth) {
					buttonsRecyclerView.smoothScrollBy(right + extra - buttonsRecyclerView.measuredWidth, 0)
				}
			}
			else if (view is AttachBotButton) {
				if (view.attachMenuBot != null) {
					showBotLayout(view.attachMenuBot!!.botId)
				}
				else {
					chatAttachViewDelegate?.didSelectBot(view.currentUser)
					dismiss()
				}
			}

			if (view.x + view.width >= buttonsRecyclerView.measuredWidth - AndroidUtilities.dp(32f)) {
				buttonsRecyclerView.smoothScrollBy((view.width * 1.5f).toInt(), 0)
			}
		}
		buttonsRecyclerView.setOnItemLongClickListener { view, _ ->
			if (view is AttachBotButton) {
				if (view.currentUser == null) {
					return@setOnItemLongClickListener false
				}

				onLongClickBotButton(view.attachMenuBot, view.currentUser)

				return@setOnItemLongClickListener true
			}

			false
		}

		botMainButtonTextView.visibility = View.GONE
		botMainButtonTextView.alpha = 0f
		botMainButtonTextView.setSingleLine()
		botMainButtonTextView.gravity = Gravity.CENTER
		botMainButtonTextView.typeface = Theme.TYPEFACE_BOLD

		val padding = AndroidUtilities.dp(16f)

		botMainButtonTextView.setPadding(padding, 0, padding, 0)
		botMainButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)

		botMainButtonTextView.setOnClickListener {
			if (selectedId < 0) {
				val webViewLayout = botAttachLayouts[-selectedId]
				webViewLayout?.webViewContainer?.onMainButtonPressed()
			}
		}

		containerView.addView(botMainButtonTextView, createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM or Gravity.LEFT))

		botProgressView.setSize(AndroidUtilities.dp(18f))
		botProgressView.alpha = 0f
		botProgressView.scaleX = 0.1f
		botProgressView.scaleY = 0.1f
		botProgressView.visibility = View.GONE

		containerView.addView(botProgressView, createFrame(28, 28f, Gravity.BOTTOM or Gravity.RIGHT, 0f, 0f, 10f, 10f))

		frameLayout2.setWillNotDraw(false)
		frameLayout2.visibility = View.INVISIBLE
		frameLayout2.alpha = 0.0f

		containerView.addView(frameLayout2, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.BOTTOM))

		frameLayout2.setOnTouchListener { _, _ ->
			true
		}

		captionLimitView.visibility = View.GONE
		captionLimitView.setTextSize(15)
		captionLimitView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		captionLimitView.setTypeface(Theme.TYPEFACE_BOLD)
		captionLimitView.setCenterAlign(true)

		frameLayout2.addView(captionLimitView, createFrame(56, 20f, Gravity.BOTTOM or Gravity.RIGHT, 3f, 0f, 14f, 78f))

		currentLimit = MessagesController.getInstance(UserConfig.selectedAccount).captionMaxLengthLimit

		commentTextView.setHint(context.getString(R.string.AddCaption))
		commentTextView.onResume()

		commentTextView.editText.addTextChangedListener(object : TextWatcher {
			private var processChange = false

			override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
				// unused
			}

			override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
				if (count - before >= 1) {
					processChange = true
				}
			}

			override fun afterTextChanged(editable: Editable) {
				if (processChange) {
					val spans = editable.getSpans(0, editable.length, ImageSpan::class.java)

					for (span in spans) {
						editable.removeSpan(span)
					}

					Emoji.replaceEmoji(editable, commentTextView.editText.paint.fontMetricsInt, false)

					processChange = false
				}

				var beforeLimit = 0

				codepointCount = Character.codePointCount(editable, 0, editable.length)

				var sendButtonEnabledLocal = true

				if (currentLimit > 0 && currentLimit - codepointCount.also { beforeLimit = it } <= 100) {
					if (beforeLimit < -9999) {
						beforeLimit = -9999
					}

					captionLimitView.setNumber(beforeLimit, captionLimitView.isVisible)

					if (captionLimitView.visibility != View.VISIBLE) {
						captionLimitView.visible()
						captionLimitView.alpha = 0f
						captionLimitView.scaleX = 0.5f
						captionLimitView.scaleY = 0.5f
					}

					captionLimitView.animate().setListener(null).cancel()
					captionLimitView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100).start()

					if (beforeLimit < 0) {
						sendButtonEnabledLocal = false
						captionLimitView.setTextColor(context.getColor(R.color.purple))
					}
					else {
						captionLimitView.setTextColor(context.getColor(R.color.dark_gray))
					}
				}
				else {
					captionLimitView.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(100).setListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							captionLimitView.gone()
						}
					})
				}

				if (sendButtonEnabled != sendButtonEnabledLocal) {
					sendButtonEnabled = sendButtonEnabledLocal

					sendButtonColorAnimator?.cancel()

					sendButtonColorAnimator = ValueAnimator.ofFloat(if (sendButtonEnabled) 0f else 1f, if (sendButtonEnabled) 1f else 0f)

					sendButtonColorAnimator?.addUpdateListener {
						sendButtonEnabledProgress = it.animatedValue as Float

						val color = ResourcesCompat.getColor(context.resources, R.color.brand, null)
						val defaultAlpha = Color.alpha(color)

						writeButton.colorFilter = PorterDuffColorFilter(ColorUtils.setAlphaComponent(color, (defaultAlpha * (0.58f + 0.42f * sendButtonEnabledProgress)).toInt()), PorterDuff.Mode.MULTIPLY)

						selectedCountView.invalidate()
					}

					sendButtonColorAnimator?.setDuration(150)?.start()
				}
			}
		})

		frameLayout2.addView(commentTextView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM or Gravity.LEFT, 0f, 0f, 84f, 0f))
		frameLayout2.clipChildren = false

		commentTextView.clipChildren = false

		writeButtonContainer.isFocusable = true
		writeButtonContainer.isFocusableInTouchMode = true
		writeButtonContainer.visibility = View.INVISIBLE
		writeButtonContainer.scaleX = 0.2f
		writeButtonContainer.scaleY = 0.2f
		writeButtonContainer.alpha = 0.0f

		containerView.addView(writeButtonContainer, createFrame(60, 60f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, 6f, 10f))

		writeButtonDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56f), ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.darker_brand, null))

		writeButton.background = writeButtonDrawable
		writeButton.setImageResource(R.drawable.arrow_up)
		writeButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.SRC_IN)
		writeButton.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		writeButton.scaleType = ImageView.ScaleType.CENTER

		writeButton.outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, AndroidUtilities.dp(56f), AndroidUtilities.dp(56f))
			}
		}

		writeButtonContainer.addView(writeButton, createFrame(56, 56f, Gravity.LEFT or Gravity.TOP, 2f, 0f, 0f, 0f))

		writeButton.setOnClickListener {
			if (currentLimit - codepointCount < 0) {
				AndroidUtilities.shakeView(captionLimitView, 2f, 0)
				it.context.vibrate()
				return@setOnClickListener
			}

			if (editingMessageObject == null && baseFragment is ChatActivity && baseFragment.isInScheduleMode) {
				AlertsCreator.createScheduleDatePickerDialog(getContext(), baseFragment.dialogId) { notify, scheduleDate ->
					if (currentAttachLayout === photoLayout || currentAttachLayout === photoPreviewLayout) {
						sendPressed(notify, scheduleDate)
					}
					else {
						currentAttachLayout?.sendSelectedItems(notify, scheduleDate)
						allowPassConfirmationAlert = true
						dismiss()
					}
				}
			}
			else {
				if (currentAttachLayout === photoLayout || currentAttachLayout === photoPreviewLayout) {
					sendPressed(true, 0)
				}
				else {
					currentAttachLayout?.sendSelectedItems(true, 0)
					allowPassConfirmationAlert = true
					dismiss()
				}
			}
		}

		writeButton.setOnLongClickListener { view ->
			if (baseFragment !is ChatActivity || editingMessageObject != null || currentLimit - codepointCount < 0) {
				return@setOnLongClickListener false
			}

			val chatActivity = baseFragment
			val user = chatActivity.currentUser

			if (chatActivity.isInScheduleMode) {
				return@setOnLongClickListener false
			}

			sendPopupLayout = ActionBarPopupWindowLayout(getContext())
			sendPopupLayout?.setAnimationEnabled(false)

			sendPopupLayout?.setOnTouchListener(object : OnTouchListener {
				private val popupRect = Rect()

				@SuppressLint("ClickableViewAccessibility")
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

			sendPopupLayout?.setDispatchKeyEventListener {
				if (it.keyCode == KeyEvent.KEYCODE_BACK && it.repeatCount == 0 && sendPopupWindow?.isShowing == true) {
					sendPopupWindow?.dismiss()
				}
			}

			sendPopupLayout?.setShownFromBottom(false)

			itemCells = arrayOfNulls(2)

			for (a in 0..1) {
				if (a == 0) {
					if (!chatActivity.canScheduleMessage() || currentAttachLayout?.canScheduleMessages() != true) {
						continue
					}
				}
				else if (isUserSelf(user)) {
					continue
				}

				itemCells!![a] = ActionBarMenuSubItem(getContext(), a == 0, a == 1)

				if (a == 0) {
					if (isUserSelf(user)) {
						itemCells!![a]?.setTextAndIcon(getContext().getString(R.string.SetReminder), R.drawable.msg_calendar2)
					}
					else {
						itemCells!![a]?.setTextAndIcon(getContext().getString(R.string.ScheduleMessage), R.drawable.msg_calendar2)
					}
				}
				else {
					itemCells!![a]?.setTextAndIcon(getContext().getString(R.string.SendWithoutSound), R.drawable.input_notify_off)
				}

				itemCells!![a]?.minimumWidth = AndroidUtilities.dp(196f)

				sendPopupLayout?.addView(itemCells!![a], createLinear(LayoutHelper.MATCH_PARENT, 48))

				itemCells!![a]?.setOnClickListener {
					if (sendPopupWindow?.isShowing == true) {
						sendPopupWindow?.dismiss()
					}

					if (a == 0) {
						AlertsCreator.createScheduleDatePickerDialog(getContext(), chatActivity.dialogId) { notify, scheduleDate ->
							if (currentAttachLayout === photoLayout || currentAttachLayout === photoPreviewLayout) {
								sendPressed(notify, scheduleDate)
							}
							else {
								currentAttachLayout?.sendSelectedItems(notify, scheduleDate)
								dismiss()
							}
						}
					}
					else {
						if (currentAttachLayout === photoLayout || currentAttachLayout === photoPreviewLayout) {
							sendPressed(false, 0)
						}
						else {
							currentAttachLayout?.sendSelectedItems(false, 0)
							dismiss()
						}
					}
				}
			}

			sendPopupLayout?.setupRadialSelectors(context.getColor(R.color.light_background))

			sendPopupWindow = ActionBarPopupWindow(sendPopupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
			sendPopupWindow?.setAnimationEnabled(false)
			sendPopupWindow?.animationStyle = R.style.PopupContextAnimation2
			sendPopupWindow?.isOutsideTouchable = true
			sendPopupWindow?.isClippingEnabled = true
			sendPopupWindow?.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
			sendPopupWindow?.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
			sendPopupWindow?.contentView?.isFocusableInTouchMode = true
			sendPopupLayout?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST))
			sendPopupWindow?.isFocusable = true

			val location = IntArray(2)

			view.getLocationInWindow(location)

			sendPopupWindow?.showAtLocation(view, Gravity.LEFT or Gravity.TOP, location[0] + view.measuredWidth - (sendPopupLayout?.measuredWidth ?: 0) + AndroidUtilities.dp(8f), location[1] - (sendPopupLayout?.measuredHeight ?: 0) - AndroidUtilities.dp(2f))
			sendPopupWindow?.dimBehind()

			view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

			false
		}

		textPaint.textSize = AndroidUtilities.dp(12f).toFloat()
		textPaint.typeface = Theme.TYPEFACE_BOLD

		selectedCountView.alpha = 0.0f
		selectedCountView.scaleX = 0.2f
		selectedCountView.scaleY = 0.2f

		containerView.addView(selectedCountView, createFrame(42, 24f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, -8f, 9f))

		if (forceDarkTheme) {
			checkColors()
		}
	}

	fun setCanOpenPreview(canOpenPreview: Boolean) {
		this.canOpenPreview = canOpenPreview
		selectedArrowImageView.visibility = if (canOpenPreview && avatarPicker != 2) View.VISIBLE else View.GONE
	}

	val clipLayoutBottom: Float
		get() {
			val alphaOffset = (frameLayout2.measuredHeight - AndroidUtilities.dp(84f)) * (1f - frameLayout2.alpha)
			return frameLayout2.measuredHeight - alphaOffset
		}

	@JvmOverloads
	fun showBotLayout(id: Long, startCommand: String? = null) {
		if (botAttachLayouts[id] == null || startCommand != botAttachLayouts[id]!!.startCommand || botAttachLayouts[id]!!.needReload()) {
			if (baseFragment is ChatActivity) {
				val webViewLayout = ChatAttachAlertBotWebViewLayout(this, context)

				botAttachLayouts.put(id, webViewLayout)

				botAttachLayouts[id]?.setDelegate(object : BotWebViewContainer.Delegate {
					private var botButtonAnimator: ValueAnimator? = null

					override fun onWebAppSetupClosingBehavior(needConfirmation: Boolean) {
						webViewLayout.setNeedCloseConfirmation(needConfirmation)
					}

					override fun onCloseRequested(callback: Runnable?) {
						if (currentAttachLayout !== webViewLayout) {
							return
						}

						this@ChatAttachAlert.isFocusable = false
						this@ChatAttachAlert.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

						dismiss()

						AndroidUtilities.runOnUIThread({
							callback?.run()
						}, 150)
					}

					override fun onWebAppSetActionBarColor(color: Int) {
						val from = (actionBar.background as ColorDrawable).color

						val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(200)
						animator.interpolator = CubicBezierInterpolator.DEFAULT

						animator.addUpdateListener {
							actionBar.setBackgroundColor(ColorUtils.blendARGB(from, color, it.animatedValue as Float))
						}

						animator.start()
					}

					override fun onWebAppSetBackgroundColor(color: Int) {
						webViewLayout.customBackground = color
					}

					override fun onWebAppExpand() {
						if (currentAttachLayout !== webViewLayout) {
							return
						}

						if (webViewLayout.canExpandByRequest()) {
							webViewLayout.scrollToTop()
						}
					}

					override fun onSetupMainButton(isVisible: Boolean, isActive: Boolean, text: String, color: Int, textColor: Int, isProgressVisible: Boolean) {
						if (currentAttachLayout !== webViewLayout || !webViewLayout.isBotButtonAvailable) {
							return
						}

						botMainButtonTextView.isClickable = isActive
						botMainButtonTextView.text = text
						botMainButtonTextView.setTextColor(textColor)
						botMainButtonTextView.background = BotWebViewContainer.getMainButtonRippleDrawable(color)

						if (botButtonWasVisible != isVisible) {
							botButtonWasVisible = isVisible

							botButtonAnimator?.cancel()

							botButtonAnimator = ValueAnimator.ofFloat((if (isVisible) 0 else 1).toFloat(), (if (isVisible) 1 else 0).toFloat()).setDuration(250)

							botButtonAnimator?.addUpdateListener {
								val value = it.animatedValue as Float
								buttonsRecyclerView.alpha = 1f - value
								botMainButtonTextView.alpha = value
								botMainButtonOffsetY = value * AndroidUtilities.dp(36f)
								shadow.translationY = botMainButtonOffsetY
								buttonsRecyclerView.translationY = botMainButtonOffsetY
							}

							botButtonAnimator?.addListener(object : AnimatorListenerAdapter() {
								override fun onAnimationStart(animation: Animator) {
									if (isVisible) {
										botMainButtonTextView.alpha = 0f
										botMainButtonTextView.visibility = View.VISIBLE

										val offsetY = AndroidUtilities.dp(36f)

										botAttachLayouts.forEach { _, value ->
											value.setMeasureOffsetY(offsetY)
										}
									}
									else {
										buttonsRecyclerView.alpha = 0f
										buttonsRecyclerView.visibility = View.VISIBLE
									}
								}

								override fun onAnimationEnd(animation: Animator) {
									if (!isVisible) {
										botMainButtonTextView.visibility = View.GONE
									}
									else {
										buttonsRecyclerView.visibility = View.GONE
									}

									val offsetY = if (isVisible) AndroidUtilities.dp(36f) else 0

									botAttachLayouts.forEach { _, value ->
										value.setMeasureOffsetY(offsetY)
									}

									if (botButtonAnimator === animation) {
										botButtonAnimator = null
									}
								}
							})

							botButtonAnimator?.start()
						}

						botProgressView.setProgressColor(textColor)

						if (botButtonProgressWasVisible != isProgressVisible) {
							botProgressView.animate().cancel()

							if (isProgressVisible) {
								botProgressView.alpha = 0f
								botProgressView.visibility = View.VISIBLE
							}

							botProgressView.animate().alpha(if (isProgressVisible) 1f else 0f).scaleX(if (isProgressVisible) 1f else 0.1f).scaleY(if (isProgressVisible) 1f else 0.1f).setDuration(250).setListener(object : AnimatorListenerAdapter() {
								override fun onAnimationEnd(animation: Animator) {
									botButtonProgressWasVisible = isProgressVisible

									if (!isProgressVisible) {
										botProgressView.visibility = View.GONE
									}
								}
							}).start()
						}
					}

					override fun onSetBackButtonVisible(visible: Boolean) {
						AndroidUtilities.updateImageViewImageAnimated(actionBar.backButton, if (visible) R.drawable.ic_back_arrow else R.drawable.ic_close_white)
					}
				})

				val replyingObject = baseFragment.chatActivityEnterView?.getReplyingMessageObject()

				botAttachLayouts[id]!!.requestWebView(currentAccount, baseFragment.dialogId, id, false, replyingObject?.messageOwner?.id ?: 0, startCommand)
			}
		}

		if (botAttachLayouts[id] != null) {
			botAttachLayouts[id]?.disallowSwipeOffsetAnimation()
			showLayout(botAttachLayouts[id], -id)
		}
	}

	fun checkCaption(text: CharSequence?): Boolean {
		return if (baseFragment is ChatActivity) {
			val dialogId = baseFragment.dialogId
			checkPremiumAnimatedEmoji(currentAccount, dialogId, baseFragment, sizeNotifierFrameLayout, text)
		}
		else {
			false
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		AndroidUtilities.setLightStatusBar(window, baseFragment.isLightStatusBar)
	}

	private val isLightStatusBar: Boolean
		get() {
			val color = ResourcesCompat.getColor(context.resources, if (forceDarkTheme) R.color.dark_background else R.color.background, null)
			return ColorUtils.calculateLuminance(color) > 0.7f
		}

	fun onLongClickBotButton(attachMenuBot: TLRPC.TLAttachMenuBot?, currentUser: TLRPC.User?) {
		val botName = if (attachMenuBot != null) attachMenuBot.shortName else getUserName(currentUser)

		AlertDialog.Builder(context).setTitle(context.getString(R.string.BotRemoveFromMenuTitle)).setMessage(AndroidUtilities.replaceTags(if (attachMenuBot != null) LocaleController.formatString("BotRemoveFromMenu", R.string.BotRemoveFromMenu, botName)
		else LocaleController.formatString("BotRemoveInlineFromMenu", R.string.BotRemoveInlineFromMenu, botName))).setPositiveButton(context.getString(R.string.OK)) { _, _ ->
			if (attachMenuBot != null) {
				val req = TLRPC.TLMessagesToggleBotInAttachMenu()
				req.bot = MessagesController.getInstance(currentAccount).getInputUser(currentUser)
				req.enabled = false

				ConnectionsManager.getInstance(currentAccount).sendRequest(req, { _, _ ->
					AndroidUtilities.runOnUIThread {
						MediaDataController.getInstance(currentAccount).loadAttachMenuBots(cache = false, force = true)

						if (currentAttachLayout === botAttachLayouts[attachMenuBot.botId]) {
							showLayout(photoLayout)
						}
					}
				}, ConnectionsManager.RequestFlagInvokeAfter or ConnectionsManager.RequestFlagFailOnServerErrors)
			}
			else {
				MediaDataController.getInstance(currentAccount).removeInline(currentUser!!.id)
			}
		}.setNegativeButton(context.getString(R.string.Cancel), null).show()
	}

	override fun shouldOverlayCameraViewOverNavBar(): Boolean {
		return currentAttachLayout === photoLayout && photoLayout.cameraExpanded
	}

	override fun show() {
		super.show()

		buttonPressed = false

		if (baseFragment is ChatActivity) {
			calcMandatoryInsets = baseFragment.isKeyboardVisible
		}

		openTransitionFinished = false

		if (Build.VERSION.SDK_INT >= 30) {
			navBarColor = ColorUtils.setAlphaComponent(ResourcesCompat.getColor(context.resources, R.color.light_background, null), 0)

			AndroidUtilities.setNavigationBarColor(window, navBarColor, false)
			AndroidUtilities.setLightNavigationBar(window, AndroidUtilities.computePerceivedBrightness(navBarColor) > 0.721)
		}
	}

	fun setEditingMessageObject(messageObject: MessageObject?) {
		if (editingMessageObject === messageObject) {
			return
		}

		editingMessageObject = messageObject

		if (editingMessageObject != null) {
			maxSelectedPhotos = 1
			allowOrder = false
		}
		else {
			maxSelectedPhotos = -1
			allowOrder = true
		}

		buttonsAdapter.notifyDataSetChanged()
	}

	fun applyCaption() {
		if (commentTextView.length() <= 0) {
			return
		}

		currentAttachLayout?.applyCaption(commentTextView.text)
	}

	private fun sendPressed(notify: Boolean, scheduleDate: Int) {
		if (buttonPressed) {
			return
		}

		if (baseFragment is ChatActivity) {
			val chat = baseFragment.currentChat
			val user = baseFragment.currentUser

			if (user != null || isChannel(chat) && chat.megagroup || !isChannel(chat)) {
				MessagesController.getNotificationsSettings(currentAccount).edit { putBoolean("silent_" + baseFragment.dialogId, !notify) }
			}
		}

		if (checkCaption(commentTextView.text)) {
			return
		}

		applyCaption()

		buttonPressed = true

		chatAttachViewDelegate?.didPressedButton(7, true, notify, scheduleDate, false)
	}

	private fun showLayout(layout: AttachAlertLayout) {
		var newId = selectedId

		if (layout === photoLayout) {
			newId = 1
		}
		else if (layout === audioLayout) {
			newId = 3
		}
		else if (layout === documentLayout) {
			newId = 4
		}
		else if (layout === contactsLayout) {
			newId = 5
		}
		else if (layout === locationLayout) {
			newId = 6
		}
		else if (layout === pollLayout) {
			newId = 9
		}

		showLayout(layout, newId)
	}

	private fun showLayout(layout: AttachAlertLayout?, newId: Long) {
		if (viewChangeAnimator != null || commentsAnimator != null) {
			return
		}

		if (currentAttachLayout === layout) {
			currentAttachLayout?.scrollToTop()
			return
		}

		botButtonWasVisible = false
		botButtonProgressWasVisible = false
		botMainButtonOffsetY = 0f
		botMainButtonTextView.visibility = View.GONE
		botProgressView.alpha = 0f
		botProgressView.scaleX = 0.1f
		botProgressView.scaleY = 0.1f
		botProgressView.visibility = View.GONE
		buttonsRecyclerView.alpha = 1f
		buttonsRecyclerView.translationY = botMainButtonOffsetY

		botAttachLayouts.forEach { _, value ->
			value.setMeasureOffsetY(0)
		}

		selectedId = newId

		val count = buttonsRecyclerView.childCount

		for (a in 0 until count) {
			val child = buttonsRecyclerView.getChildAt(a)

			if (child is AttachButton) {
				child.updateCheckedState(true)
			}
			else if (child is AttachBotButton) {
				child.updateCheckedState(true)
			}
		}

		val t = (currentAttachLayout?.firstOffset ?: 0) - AndroidUtilities.dp(11f) - scrollOffsetY[0]

		nextAttachLayout = layout

		container.setLayerType(View.LAYER_TYPE_HARDWARE, null)

		actionBar.visibility = if (nextAttachLayout!!.needsActionBar() != 0) View.VISIBLE else View.INVISIBLE

		actionBarShadow.visibility = actionBar.visibility

		if (actionBar.isSearchFieldVisible) {
			actionBar.closeSearchField()
		}

		currentAttachLayout?.onHide()

		if (nextAttachLayout === photoLayout) {
			photoLayout.setCheckCameraWhenShown(true)
		}

		nextAttachLayout?.onShow(currentAttachLayout)
		nextAttachLayout?.visibility = View.VISIBLE

		if (layout?.parent != null) {
			containerView.removeView(nextAttachLayout)
		}

		val index = containerView.indexOfChild(currentAttachLayout)

		if (nextAttachLayout?.parent !== containerView) {
			containerView.addView(nextAttachLayout, if (nextAttachLayout === locationLayout) index else index + 1, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		}

		val onEnd = Runnable {
			container.setLayerType(View.LAYER_TYPE_NONE, null)

			viewChangeAnimator = null

			if (currentAttachLayout !== photoLayout && nextAttachLayout !== photoPreviewLayout && currentAttachLayout !== nextAttachLayout && currentAttachLayout !== photoPreviewLayout) {
				containerView.removeView(currentAttachLayout)
			}

			currentAttachLayout?.visibility = View.GONE
			currentAttachLayout?.onHidden()

			nextAttachLayout?.onShown()

			currentAttachLayout = nextAttachLayout

			nextAttachLayout = null

			scrollOffsetY[0] = scrollOffsetY[1]
		}

		if (!(currentAttachLayout is ChatAttachAlertPhotoLayoutPreview || nextAttachLayout is ChatAttachAlertPhotoLayoutPreview)) {
			val animator = AnimatorSet()

			nextAttachLayout?.alpha = 0.0f
			nextAttachLayout?.translationY = AndroidUtilities.dp(78f).toFloat()

			animator.playTogether(ObjectAnimator.ofFloat(currentAttachLayout, View.TRANSLATION_Y, (AndroidUtilities.dp(78f) + t).toFloat()), ObjectAnimator.ofFloat(currentAttachLayout, attachAlertLayoutTranslation, 0.0f, 1.0f), ObjectAnimator.ofFloat(actionBar, View.ALPHA, actionBar.alpha, 0f))
			animator.duration = 180
			animator.startDelay = 20
			animator.interpolator = CubicBezierInterpolator.DEFAULT

			animator.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					currentAttachLayout?.alpha = 0.0f

					val springAnimation = SpringAnimation(nextAttachLayout, DynamicAnimation.TRANSLATION_Y, 0f)
					springAnimation.spring.dampingRatio = 0.75f
					springAnimation.spring.stiffness = 500.0f

					springAnimation.addUpdateListener { _, _, _ ->
						if (nextAttachLayout === pollLayout) {
							updateSelectedPosition(1)
						}

						nextAttachLayout?.onContainerTranslationUpdated(currentPanTranslationY)
						containerView.invalidate()
					}

					springAnimation.addEndListener { _, _, _, _ ->
						onEnd.run()
					}

					viewChangeAnimator = springAnimation

					springAnimation.start()
				}
			})

			viewChangeAnimator = animator

			animator.start()
		}
		else {
			val width = max(nextAttachLayout?.width ?: 0, currentAttachLayout?.width ?: 0)

			if (nextAttachLayout is ChatAttachAlertPhotoLayoutPreview) {
				nextAttachLayout?.translationX = width.toFloat()

				if (currentAttachLayout is ChatAttachAlertPhotoLayout) {
					val photoLayout = currentAttachLayout as ChatAttachAlertPhotoLayout

					if (photoLayout.cameraView != null) {
						photoLayout.cameraView?.visibility = View.INVISIBLE
						photoLayout.cameraIcon?.visibility = View.INVISIBLE
						photoLayout.cameraCell?.visibility = View.VISIBLE
					}
				}
			}
			else {
				currentAttachLayout?.translationX = -width.toFloat()

				if (nextAttachLayout === photoLayout) {
					val photoLayout = nextAttachLayout as ChatAttachAlertPhotoLayout?

					if (photoLayout?.cameraView != null) {
						photoLayout.cameraView?.visibility = View.VISIBLE
						photoLayout.cameraIcon?.visibility = View.VISIBLE
					}
				}
			}

			nextAttachLayout?.alpha = 1f
			currentAttachLayout?.alpha = 1f

			attachAlertLayoutTranslation[currentAttachLayout] = 0.0f

			AndroidUtilities.runOnUIThread {
				val fromActionBarAlpha = actionBar.alpha
				val showActionBar = (nextAttachLayout?.currentItemTop ?: 0) <= (layout?.buttonsHideOffset ?: 0)
				val toActionBarAlpha = if (showActionBar) 1f else 0f
				val springAnimation = SpringAnimation(FloatValueHolder(0f))

				springAnimation.addUpdateListener { _, value, _ ->
					val f = value / 500f

					attachAlertLayoutTranslation[currentAttachLayout] = f
					actionBar.alpha = AndroidUtilities.lerp(fromActionBarAlpha, toActionBarAlpha, f)

					updateLayout(currentAttachLayout, false, 0)
					updateLayout(nextAttachLayout, false, 0)

					val mediaPreviewAlpha = if (nextAttachLayout is ChatAttachAlertPhotoLayoutPreview && !showActionBar) f else 1f - f

					mediaPreviewView.alpha = mediaPreviewAlpha
					selectedView.alpha = 1f - mediaPreviewAlpha
					selectedView.translationX = mediaPreviewAlpha * -AndroidUtilities.dp(16f)
					mediaPreviewView.translationX = (1f - mediaPreviewAlpha) * AndroidUtilities.dp(16f)
				}

				springAnimation.addEndListener { _, _, _, _ ->
					currentAttachLayout?.onHideShowProgress(1f)
					nextAttachLayout?.onHideShowProgress(1f)
					currentAttachLayout?.onContainerTranslationUpdated(currentPanTranslationY)
					nextAttachLayout?.onContainerTranslationUpdated(currentPanTranslationY)
					containerView.invalidate()
					actionBar.tag = if (showActionBar) 1 else null
					onEnd.run()
				}

				springAnimation.spring = SpringForce(500f)
				springAnimation.spring.dampingRatio = 1f
				springAnimation.spring.stiffness = 1000.0f
				springAnimation.start()

				viewChangeAnimator = springAnimation
			}
		}
	}

	fun updatePhotoPreview(show: Boolean) {
		if (show) {
			if (!canOpenPreview) {
				return
			}

			if (photoPreviewLayout == null) {
				photoPreviewLayout = ChatAttachAlertPhotoLayoutPreview(this, context)
				photoPreviewLayout?.bringToFront()
			}

			(if (currentAttachLayout === photoPreviewLayout) photoLayout else photoPreviewLayout)?.let {
				showLayout(it)
			}
		}
		else {
			showLayout(photoLayout)
		}
	}

	fun onRequestPermissionsResultFragment(requestCode: Int, granted: Boolean) {
		if (requestCode == REQUEST_CODE_LIVE_LOCATION && granted && locationLayout != null && currentAttachLayout === locationLayout && isShowing) {
			locationLayout?.openShareLiveLocation()
		}
	}

	private fun openContactsLayout() {
		if (contactsLayout == null) {
			contactsLayout = ChatAttachAlertContactsLayout(this, context)

			layouts[2] = contactsLayout

			contactsLayout?.delegate = PhonebookShareAlertDelegate { user, notify, scheduleDate ->
				(baseFragment as? ChatActivity)?.sendContact(user, notify, scheduleDate)
			}
		}

		showLayout(contactsLayout!!)
	}

	private fun openAudioLayout(show: Boolean) {
		if (audioLayout == null) {
			audioLayout = ChatAttachAlertAudioLayout(this, context)

			layouts[3] = audioLayout

			audioLayout?.setDelegate { audios, caption, notify, scheduleDate ->
				when (baseFragment) {
					is ChatActivity -> {
						baseFragment.sendAudio(audios, caption, notify, scheduleDate)
					}

					is CreateMediaSaleFragment -> {
						baseFragment.sendAudio(audios)
					}
				}
			}
		}

		if (baseFragment is ChatActivity) {
			val currentChat = baseFragment.currentChat
			audioLayout?.setMaxSelectedFiles(if (currentChat != null && !hasAdminRights(currentChat) && currentChat.slowmodeEnabled || editingMessageObject != null) 1 else -1)
		}

		if (show) {
			showLayout(audioLayout!!)
		}
	}

	private fun openDocumentsLayout(show: Boolean) {
		if (documentLayout == null) {
			val type = if (isSoundPicker) ChatAttachAlertDocumentLayout.TYPE_RINGTONE else ChatAttachAlertDocumentLayout.TYPE_DEFAULT

			documentLayout = ChatAttachAlertDocumentLayout(this, context, type)

			layouts[4] = documentLayout

			documentLayout?.setDelegate(object : DocumentSelectActivityDelegate {
				override fun didSelectFiles(files: List<String>?, caption: String?, fmessages: List<MessageObject>?, notify: Boolean, scheduleDate: Int) {
					if (baseFragment is DocumentSelectActivityDelegate) {
						(baseFragment as DocumentSelectActivityDelegate).didSelectFiles(files, caption, fmessages, notify, scheduleDate)
					}
					else if (baseFragment is PassportActivity) {
						baseFragment.didSelectFiles(files, caption, notify, scheduleDate)
					}
				}

				override fun didSelectPhotos(photos: List<SendingMediaInfo>?, notify: Boolean, scheduleDate: Int) {
					if (baseFragment is ChatActivity) {
						baseFragment.didSelectPhotos(photos, notify, scheduleDate)
					}
					else if (baseFragment is PassportActivity) {
						baseFragment.didSelectPhotos(photos, notify, scheduleDate)
					}
				}

				override fun startDocumentSelectActivity() {
					if (baseFragment is DocumentSelectActivityDelegate) {
						(baseFragment as DocumentSelectActivityDelegate).startDocumentSelectActivity()
					}
					else if (baseFragment is PassportActivity) {
						baseFragment.startDocumentSelectActivity()
					}
				}

				override fun startMusicSelectActivity() {
					openAudioLayout(true)
				}
			})
		}

		when (baseFragment) {
			is ChatActivity -> {
				val currentChat = baseFragment.currentChat
				documentLayout?.setMaxSelectedFiles(if (currentChat != null && !hasAdminRights(currentChat) && currentChat.slowmodeEnabled || editingMessageObject != null) 1 else -1)
			}

			is CreateMediaSaleFragment -> {
				val currentChat = baseFragment.currentChat
				documentLayout?.setMaxSelectedFiles(if (currentChat != null && !hasAdminRights(currentChat) && currentChat.slowmodeEnabled || editingMessageObject != null) 1 else -1)
			}

			else -> {
				documentLayout?.setMaxSelectedFiles(maxSelectedPhotos)
				documentLayout?.setCanSelectOnlyImageFiles(!isSoundPicker)
			}
		}

		documentLayout?.isSoundPicker = isSoundPicker

		if (show) {
			showLayout(documentLayout!!)
		}
	}

	private fun showCommentTextView(show: Boolean, animated: Boolean): Boolean {
		if (show == (frameLayout2.tag != null)) {
			return false
		}

		commentsAnimator?.cancel()

		frameLayout2.tag = if (show) 1 else null

		if (commentTextView.editText.isFocused) {
			AndroidUtilities.hideKeyboard(commentTextView.editText)
		}

		commentTextView.hidePopup(true)

		if (show) {
			if (!isSoundPicker) {
				frameLayout2.visibility = View.VISIBLE
			}

			writeButtonContainer.visibility = View.VISIBLE

			if (!typeButtonsAvailable && !isSoundPicker) {
				shadow.visibility = View.VISIBLE
			}
		}
		else if (typeButtonsAvailable) {
			buttonsRecyclerView.visibility = View.VISIBLE
		}

		if (animated) {
			commentsAnimator = AnimatorSet()

			val animators = mutableListOf<Animator>()
			animators.add(ObjectAnimator.ofFloat(frameLayout2, View.ALPHA, if (show) 1.0f else 0.0f))
			animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_X, if (show) 1.0f else 0.2f))
			animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_Y, if (show) 1.0f else 0.2f))
			animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.ALPHA, if (show) 1.0f else 0.0f))
			animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, if (show) 1.0f else 0.2f))
			animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, if (show) 1.0f else 0.2f))
			animators.add(ObjectAnimator.ofFloat(selectedCountView, View.ALPHA, if (show) 1.0f else 0.0f))

			if (actionBar.tag != null) {
				animators.add(ObjectAnimator.ofFloat(frameLayout2, View.TRANSLATION_Y, if (show) 0.0f else AndroidUtilities.dp(48f).toFloat()))
				animators.add(ObjectAnimator.ofFloat(shadow, View.TRANSLATION_Y, (if (show) AndroidUtilities.dp(36f) else AndroidUtilities.dp((48 + 36).toFloat())).toFloat()))
				animators.add(ObjectAnimator.ofFloat(shadow, View.ALPHA, if (show && baseFragment !is CreateMediaSaleFragment) 1.0f else 0.0f))
			}
			else if (typeButtonsAvailable) {
				animators.add(ObjectAnimator.ofFloat(buttonsRecyclerView, View.TRANSLATION_Y, (if (show) AndroidUtilities.dp(36f) else 0).toFloat()))
				animators.add(ObjectAnimator.ofFloat(shadow, View.TRANSLATION_Y, (if (show) AndroidUtilities.dp(36f) else 0).toFloat()))
			}
			else if (!isSoundPicker) {
				shadow.translationY = AndroidUtilities.dp(36f) + botMainButtonOffsetY
				animators.add(ObjectAnimator.ofFloat(shadow, View.ALPHA, if (show && baseFragment !is CreateMediaSaleFragment) 1.0f else 0.0f))
			}

			commentsAnimator?.playTogether(animators)
			commentsAnimator?.interpolator = DecelerateInterpolator()
			commentsAnimator?.duration = 180

			commentsAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (animation == commentsAnimator) {
						if (!show) {
							if (!isSoundPicker) {
								frameLayout2.visibility = View.INVISIBLE
							}

							writeButtonContainer.visibility = View.INVISIBLE

							if (!typeButtonsAvailable && !isSoundPicker) {
								shadow.visibility = View.INVISIBLE
							}
						}
						else if (typeButtonsAvailable) {
							if (currentAttachLayout == null || currentAttachLayout?.shouldHideBottomButtons() == true) {
								buttonsRecyclerView.visibility = View.INVISIBLE
							}
						}

						commentsAnimator = null
					}
				}

				override fun onAnimationCancel(animation: Animator) {
					if (animation == commentsAnimator) {
						commentsAnimator = null
					}
				}
			})

			commentsAnimator?.start()
		}
		else {
			frameLayout2.alpha = if (show) 1.0f else 0.0f

			writeButtonContainer.scaleX = if (show) 1.0f else 0.2f
			writeButtonContainer.scaleY = if (show) 1.0f else 0.2f
			writeButtonContainer.alpha = if (show) 1.0f else 0.0f

			selectedCountView.scaleX = if (show) 1.0f else 0.2f
			selectedCountView.scaleY = if (show) 1.0f else 0.2f
			selectedCountView.alpha = if (show) 1.0f else 0.0f

			if (actionBar.tag != null) {
				frameLayout2.translationY = if (show) 0.0f else AndroidUtilities.dp(48f).toFloat()
				shadow.translationY = (if (show) AndroidUtilities.dp(36f) else AndroidUtilities.dp((48 + 36).toFloat())) + botMainButtonOffsetY
				shadow.alpha = if (show && baseFragment !is CreateMediaSaleFragment) 1.0f else 0.0f
			}
			else if (typeButtonsAvailable) {
				if (currentAttachLayout == null || currentAttachLayout?.shouldHideBottomButtons() == true) {
					buttonsRecyclerView.translationY = (if (show) AndroidUtilities.dp(36f) else 0).toFloat()
				}

				shadow.translationY = (if (show) AndroidUtilities.dp(36f) else 0) + botMainButtonOffsetY
			}
			else {
				shadow.translationY = AndroidUtilities.dp(36f) + botMainButtonOffsetY
				shadow.alpha = if (show && baseFragment !is CreateMediaSaleFragment) 1.0f else 0.0f
			}

			if (!show) {
				frameLayout2.visibility = View.INVISIBLE
				writeButtonContainer.visibility = View.INVISIBLE

				if (!typeButtonsAvailable) {
					shadow.visibility = View.INVISIBLE
				}
			}
		}

		return true
	}

	override fun cancelSheetAnimation() {
		if (currentSheetAnimation != null) {
			currentSheetAnimation?.cancel()
			appearSpringAnimation?.cancel()
			buttonsAnimation?.cancel()
			currentSheetAnimation = null
			currentSheetAnimationType = 0
		}
	}

	override fun onCustomOpenAnimation(): Boolean {
		photoLayout.translationX = 0f
		mediaPreviewView.alpha = 0f
		selectedView.alpha = 1f

		val fromTranslationY = super.containerView.measuredHeight

		super.containerView.translationY = fromTranslationY.toFloat()

		buttonsAnimation = AnimatorSet()
		buttonsAnimation?.playTogether(ObjectAnimator.ofFloat(this, attachAlertProgress, 0.0f, 400.0f))
		buttonsAnimation?.duration = 400
		buttonsAnimation?.startDelay = 20

		attachAlertProgress[this] = 0.0f

		buttonsAnimation?.start()

		navigationBarAnimation?.cancel()

		navigationBarAnimation = ValueAnimator.ofFloat(navigationBarAlpha, 1f)

		navigationBarAnimation.addUpdateListener {
			navigationBarAlpha = it.animatedValue as Float
			container.invalidate()
		}

		appearSpringAnimation?.cancel()

		appearSpringAnimation = SpringAnimation(super.containerView, DynamicAnimation.TRANSLATION_Y, 0f)
		appearSpringAnimation?.spring?.dampingRatio = 0.75f
		appearSpringAnimation?.spring?.stiffness = 350.0f
		appearSpringAnimation?.start()

		if (useHardwareLayer) {
			container.setLayerType(View.LAYER_TYPE_HARDWARE, null)
		}

		currentSheetAnimationType = 1

		currentSheetAnimation = AnimatorSet()
		currentSheetAnimation?.playTogether(ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, if (dimBehind) dimBehindAlpha else 0))
		currentSheetAnimation?.duration = 400
		currentSheetAnimation?.startDelay = 20
		currentSheetAnimation?.interpolator = openInterpolator

		val delegate = super.delegate

		val onAnimationEnd = Runnable {
			currentSheetAnimation = null
			appearSpringAnimation = null
			currentSheetAnimationType = 0

			delegate?.onOpenAnimationEnd()

			if (useHardwareLayer) {
				container.setLayerType(View.LAYER_TYPE_NONE, null)
			}

			if (isFullscreen) {
				window?.let {
					val params = it.attributes
					params.flags = params.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
					it.attributes = params
				}
			}

			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.startAllHeavyOperations, 512)
		}

		appearSpringAnimation?.addEndListener { _, _, _, _ ->
			if (currentSheetAnimation != null && currentSheetAnimation?.isRunning != true) {
				onAnimationEnd.run()
			}
		}

		currentSheetAnimation?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				if (currentSheetAnimation != null && currentSheetAnimation == animation) {
					if (appearSpringAnimation != null && !appearSpringAnimation!!.isRunning) {
						onAnimationEnd.run()
					}
				}
			}

			override fun onAnimationCancel(animation: Animator) {
				if (currentSheetAnimation != null && currentSheetAnimation == animation) {
					currentSheetAnimation = null
					currentSheetAnimationType = 0
				}
			}
		})

		NotificationCenter.globalInstance.postNotificationName(NotificationCenter.stopAllHeavyOperations, 512)

		currentSheetAnimation?.start()

		val navigationBarAnimator = ValueAnimator.ofFloat(0f, 1f)

		setNavBarAlpha(0f)

		navigationBarAnimator.addUpdateListener {
			setNavBarAlpha(it.animatedValue as Float)
		}

		navigationBarAnimator.startDelay = 25
		navigationBarAnimator.duration = 200
		navigationBarAnimator.interpolator = CubicBezierInterpolator.DEFAULT
		navigationBarAnimator.start()

		return true
	}

	private fun setNavBarAlpha(alpha: Float) {
		navBarColor = ColorUtils.setAlphaComponent(ResourcesCompat.getColor(context.resources, R.color.light_background, null), min(255, max(0, (255 * alpha).toInt())))

		AndroidUtilities.setNavigationBarColor(window, navBarColor, false)
		AndroidUtilities.setLightNavigationBar(window, AndroidUtilities.computePerceivedBrightness(navBarColor) > 0.721)

		getContainer().invalidate()
	}

	override fun onContainerTouchEvent(event: MotionEvent): Boolean {
		return currentAttachLayout?.onContainerViewTouchEvent(event) ?: false
	}

	fun makeFocusable(editText: EditTextBoldCursor?, showKeyboard: Boolean) {
		if (editText == null) {
			return
		}

		if (chatAttachViewDelegate == null) {
			return
		}

		if (!enterCommentEventSent) {
			val keyboardVisible = chatAttachViewDelegate?.needEnterComment() ?: false

			enterCommentEventSent = true

			AndroidUtilities.runOnUIThread({
				isFocusable = true

				editText.requestFocus()

				if (showKeyboard) {
					AndroidUtilities.runOnUIThread {
						AndroidUtilities.showKeyboard(editText)
					}
				}
			}, (if (keyboardVisible) 200 else 0).toLong())
		}
	}

	private fun applyAttachButtonColors(view: View) {
		if (view is AttachBotButton) {
			view.nameTextView.setTextColor(ColorUtils.blendARGB(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), view.textColor, view.checkedState))
		}
	}

	private fun checkColors() {
		val count = buttonsRecyclerView.childCount

		for (a in 0 until count) {
			applyAttachButtonColors(buttonsRecyclerView.getChildAt(a))
		}

		selectedTextView.setTextColor(if (forceDarkTheme) context.getColor(R.color.white) else context.getColor(R.color.text))
		mediaPreviewTextView.setTextColor(if (forceDarkTheme) context.getColor(R.color.white) else context.getColor(R.color.text))
		doneItem.textView?.setTextColor(context.getColor(R.color.brand))
		selectedMenuItem.setIconColor(if (forceDarkTheme) context.getColor(R.color.white) else context.getColor(R.color.text))

		Theme.setDrawableColor(selectedMenuItem.background, if (forceDarkTheme) context.getColor(R.color.white) else context.getColor(R.color.brand))

		selectedMenuItem.setPopupItemsColor(context.getColor(R.color.text), false)
		selectedMenuItem.setPopupItemsColor(context.getColor(R.color.text), true)
		selectedMenuItem.redrawPopup(context.getColor(R.color.background))

		searchItem.setIconColor(if (forceDarkTheme) context.getColor(R.color.white) else context.getColor(R.color.text))

		Theme.setDrawableColor(searchItem.background, if (forceDarkTheme) context.getColor(R.color.white) else context.getColor(R.color.brand))

		commentTextView.updateColors()

		if (sendPopupLayout != null) {
			itemCells?.forEach {
				if (it != null) {
					it.setColors(context.getColor(R.color.text), context.getColor(R.color.brand))
					it.setSelectorColor(context.getColor(R.color.light_background))
				}
			}

			sendPopupLayout?.setBackgroundColor(context.getColor(R.color.background))

			if (sendPopupWindow?.isShowing == true) {
				sendPopupLayout?.invalidate()
			}
		}

		Theme.setSelectorDrawableColor(writeButtonDrawable, ResourcesCompat.getColor(context.resources, R.color.white, null), false)
		Theme.setSelectorDrawableColor(writeButtonDrawable, ResourcesCompat.getColor(context.resources, R.color.white, null), true)

		writeButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.SRC_IN)

		actionBarShadow.setBackgroundColor(context.getColor(R.color.shadow))
		buttonsRecyclerView.setGlowColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))
		buttonsRecyclerView.setBackgroundColor(ResourcesCompat.getColor(context.resources, if (forceDarkTheme) R.color.dark_background else R.color.background, null))

		frameLayout2.setBackgroundColor(ResourcesCompat.getColor(context.resources, if (forceDarkTheme) R.color.dark_background else R.color.background, null))

		selectedCountView.invalidate()

		actionBar.setBackgroundColor(ResourcesCompat.getColor(context.resources, if (forceDarkTheme) R.color.dark_background else R.color.background, null))
		actionBar.setItemsColor(ResourcesCompat.getColor(context.resources, if (forceDarkTheme) R.color.white else R.color.action_bar_item, null), false)
		actionBar.setItemsBackgroundColor(ResourcesCompat.getColor(context.resources, if (forceDarkTheme) R.color.white else R.color.action_bar_item, null), false)
		actionBar.setTitleColor(ResourcesCompat.getColor(context.resources, if (forceDarkTheme) R.color.white else R.color.text, null))

		Theme.setDrawableColor(shadowDrawable, ResourcesCompat.getColor(context.resources, if (forceDarkTheme) R.color.dark_background else R.color.background, null))

		containerView.invalidate()

		for (layout in layouts) {
			layout?.checkColors()
		}
	}

	override fun onCustomMeasure(view: View, width: Int, height: Int): Boolean {
		return photoLayout.onCustomMeasure(view, width, height)
	}

	override fun onCustomLayout(view: View, left: Int, top: Int, right: Int, bottom: Int): Boolean {
		return photoLayout.onCustomLayout(view, left, top, right, bottom)
	}

	fun onPause() {
		for (layout in layouts) {
			layout?.onPause()
		}

		paused = true
	}

	fun onResume() {
		paused = false

		for (layout in layouts) {
			layout?.onResume()
		}

		if (isShowing) {
			chatAttachViewDelegate?.needEnterComment()
		}
	}

	fun onActivityResultFragment(requestCode: Int, data: Intent?, currentPicturePath: String?) {
		photoLayout.onActivityResultFragment(requestCode, data, currentPicturePath)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.reloadInlineHints, NotificationCenter.attachMenuBotsDidLoad -> {
				buttonsAdapter.notifyDataSetChanged()
			}

			NotificationCenter.currentUserPremiumStatusChanged -> {
				currentLimit = MessagesController.getInstance(UserConfig.selectedAccount).captionMaxLengthLimit
			}
		}
	}

	private fun getScrollOffsetY(idx: Int): Int {
		return if (nextAttachLayout != null && (currentAttachLayout is ChatAttachAlertPhotoLayoutPreview || nextAttachLayout is ChatAttachAlertPhotoLayoutPreview)) {
			AndroidUtilities.lerp(scrollOffsetY[0], scrollOffsetY[1], translationProgress)
		}
		else {
			scrollOffsetY[idx]
		}
	}

	private fun updateSelectedPosition(idx: Int) {
		val moveProgress: Float
		val layout = if (idx == 0) currentAttachLayout else nextAttachLayout
		val scrollOffset = getScrollOffsetY(idx)
		var t = scrollOffset - backgroundPaddingTop
		val toMove: Float

		if (layout === pollLayout) {
			t -= AndroidUtilities.dp(13f)
			toMove = AndroidUtilities.dp(11f).toFloat()
		}
		else {
			t -= AndroidUtilities.dp(39f)
			toMove = AndroidUtilities.dp(43f).toFloat()
		}

		if (t + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
			moveProgress = min(1.0f, (ActionBar.getCurrentActionBarHeight() - t - backgroundPaddingTop) / toMove)
			cornerRadius = 1.0f - moveProgress
		}
		else {
			moveProgress = 0.0f
			cornerRadius = 1.0f
		}

		var finalMove = if (AndroidUtilities.isTablet()) {
			16
		}
		else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
			6
		}
		else {
			12
		}

		val offset = if (actionBar.alpha != 0f) 0.0f else AndroidUtilities.dp(26 * (1.0f - headerView.alpha)).toFloat()

		if (menuShowed && avatarPicker == 0) {
			selectedMenuItem.translationY = scrollOffset - AndroidUtilities.dp(37 + finalMove * moveProgress) + offset + currentPanTranslationY
		}
		else {
			selectedMenuItem.translationY = ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(4f) - AndroidUtilities.dp((37 + finalMove).toFloat()) + currentPanTranslationY
		}

		searchItem.translationY = ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(4f) - AndroidUtilities.dp((37 + finalMove).toFloat()) + currentPanTranslationY

		headerView.translationY = (scrollOffset - AndroidUtilities.dp(25 + finalMove * moveProgress) + offset + currentPanTranslationY).also {
			baseSelectedTextViewTranslationY = it
		}

		if (pollLayout != null && layout === pollLayout) {
			finalMove = if (AndroidUtilities.isTablet()) {
				63
			}
			else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
				53
			}
			else {
				59
			}

			doneItem.translationY = max(0f, pollLayout!!.translationY + scrollOffset - AndroidUtilities.dp(7 + finalMove * moveProgress)) + currentPanTranslationY
		}
	}

	private fun updateActionBarVisibility(show: Boolean, animated: Boolean) {
		if (show && actionBar.tag == null || !show && actionBar.tag != null) {
			actionBar.tag = if (show) 1 else null

			actionBarAnimation?.cancel()
			actionBarAnimation = null

			val needsSearchItem = avatarSearch || currentAttachLayout === photoLayout && !menuShowed && baseFragment is ChatActivity && baseFragment.allowSendGifs()
			val needMoreItem = avatarPicker != 0 || !menuShowed && currentAttachLayout === photoLayout && mediaEnabled

			if (show) {
				if (needsSearchItem) {
					searchItem.visibility = View.VISIBLE
				}

				if (needMoreItem) {
					selectedMenuItem.visibility = View.VISIBLE
				}
			}
			else if (typeButtonsAvailable && frameLayout2.tag == null) {
				buttonsRecyclerView.visibility = View.VISIBLE
			}

			if (window != null) {
				if (show) {
					AndroidUtilities.setLightStatusBar(window, isLightStatusBar)
				}
				else {
					AndroidUtilities.setLightStatusBar(window, baseFragment.isLightStatusBar)
				}
			}

			if (animated) {
				actionBarAnimation = AnimatorSet()
				actionBarAnimation?.duration = (180 * abs((if (show) 1.0f else 0.0f) - actionBar.alpha)).toLong()

				val animators = mutableListOf<Animator>()
				animators.add(ObjectAnimator.ofFloat(actionBar, View.ALPHA, if (show) 1.0f else 0.0f))
				animators.add(ObjectAnimator.ofFloat(actionBarShadow, View.ALPHA, if (show) 1.0f else 0.0f))

				if (needsSearchItem) {
					animators.add(ObjectAnimator.ofFloat(searchItem, View.ALPHA, if (show) 1.0f else 0.0f))
				}

				if (needMoreItem) {
					animators.add(ObjectAnimator.ofFloat(selectedMenuItem, View.ALPHA, if (show) 1.0f else 0.0f))
				}

				actionBarAnimation?.playTogether(animators)

				actionBarAnimation?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (actionBarAnimation != null) {
							if (show) {
								if (typeButtonsAvailable && (currentAttachLayout == null || currentAttachLayout?.shouldHideBottomButtons() == true)) {
									buttonsRecyclerView.visibility = View.INVISIBLE
								}
							}
							else {
								searchItem.visibility = View.INVISIBLE
								if (avatarPicker != 0 || !menuShowed) {
									selectedMenuItem.visibility = View.INVISIBLE
								}
							}
						}
					}

					override fun onAnimationCancel(animation: Animator) {
						actionBarAnimation = null
					}
				})

				actionBarAnimation?.start()
			}
			else {
				if (show) {
					if (typeButtonsAvailable && (currentAttachLayout == null || currentAttachLayout?.shouldHideBottomButtons() == true)) {
						buttonsRecyclerView.visibility = View.INVISIBLE
					}
				}

				actionBar.alpha = if (show) 1.0f else 0.0f
				actionBarShadow.alpha = if (show) 1.0f else 0.0f

				if (needsSearchItem) {
					searchItem.alpha = if (show) 1.0f else 0.0f
				}

				if (needMoreItem) {
					selectedMenuItem.alpha = if (show) 1.0f else 0.0f
				}

				if (!show) {
					searchItem.visibility = View.INVISIBLE

					if (avatarPicker != 0 || !menuShowed) {
						selectedMenuItem.visibility = View.INVISIBLE
					}
				}
			}
		}
	}

	@SuppressLint("NewApi")
	fun updateLayout(layout: AttachAlertLayout?, animated: Boolean, dy: Int) {
		@Suppress("NAME_SHADOWING") var animated = animated

		if (layout == null) {
			return
		}

		var newOffset = layout.currentItemTop

		if (newOffset == Int.MAX_VALUE) {
			return
		}

		val show = layout === currentAttachLayout && newOffset <= layout.buttonsHideOffset

		if (currentAttachLayout !== photoPreviewLayout && keyboardVisible && animated && currentAttachLayout !is ChatAttachAlertBotWebViewLayout) {
			animated = false
		}

		if (layout === currentAttachLayout) {
			updateActionBarVisibility(show, animated)
		}

		val layoutParams = layout.layoutParams as FrameLayout.LayoutParams

		newOffset += layoutParams.topMargin - AndroidUtilities.dp(11f)

		val idx = if (currentAttachLayout === layout) 0 else 1
		val previewAnimationIsRunning = (currentAttachLayout is ChatAttachAlertPhotoLayoutPreview || nextAttachLayout is ChatAttachAlertPhotoLayoutPreview) && viewChangeAnimator is SpringAnimation && (viewChangeAnimator as SpringAnimation).isRunning

		if (scrollOffsetY[idx] != newOffset || previewAnimationIsRunning) {
			previousScrollOffsetY = scrollOffsetY[idx]
			scrollOffsetY[idx] = newOffset
			updateSelectedPosition(idx)
			containerView.invalidate()
		}
		else if (dy != 0) {
			previousScrollOffsetY = scrollOffsetY[idx]
		}
	}

	override fun canDismissWithSwipe(): Boolean {
		return false
	}

	fun updateCountButton(animated: Int) {
		if (viewChangeAnimator != null) {
			return
		}

		val count = currentAttachLayout?.selectedItemsCount ?: 0

		if (count == 0) {
			selectedCountView.pivotX = 0f
			selectedCountView.pivotY = 0f
			showCommentTextView(false, animated != 0)
		}
		else {
			selectedCountView.invalidate()

			if (!showCommentTextView(true, animated != 0) && animated != 0) {
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

		currentAttachLayout?.onSelectedItemsCountChanged(count)

		if (currentAttachLayout === photoLayout && (baseFragment is ChatActivity || avatarPicker != 0) && (count == 0 && menuShowed || (count != 0 || avatarPicker != 0) && !menuShowed)) {
			menuShowed = count != 0 || avatarPicker != 0

			menuAnimator?.cancel()
			menuAnimator = null

			val needsSearchItem = actionBar.tag != null && baseFragment is ChatActivity && baseFragment.allowSendGifs()

			if (menuShowed) {
				if (avatarPicker == 0) {
					selectedMenuItem.visibility = View.VISIBLE
				}

				headerView.visibility = View.VISIBLE
			}
			else {
				if (actionBar.tag != null) {
					searchItem.visibility = View.VISIBLE
				}
			}

			if (animated == 0) {
				if (actionBar.tag == null && avatarPicker == 0) {
					selectedMenuItem.alpha = if (menuShowed) 1.0f else 0.0f
				}

				headerView.alpha = if (menuShowed) 1.0f else 0.0f

				if (needsSearchItem) {
					searchItem.alpha = if (menuShowed) 0.0f else 1.0f
				}

				if (menuShowed) {
					searchItem.visibility = View.INVISIBLE
				}
			}
			else {
				menuAnimator = AnimatorSet()

				val animators = mutableListOf<Animator>()

				if (actionBar.tag == null && avatarPicker == 0) {
					animators.add(ObjectAnimator.ofFloat(selectedMenuItem, View.ALPHA, if (menuShowed) 1.0f else 0.0f))
				}

				animators.add(ObjectAnimator.ofFloat(headerView, View.ALPHA, if (menuShowed) 1.0f else 0.0f))

				if (needsSearchItem) {
					animators.add(ObjectAnimator.ofFloat(searchItem, View.ALPHA, if (menuShowed) 0.0f else 1.0f))
				}

				menuAnimator?.playTogether(animators)

				menuAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						menuAnimator = null

						if (!menuShowed) {
							if (actionBar.tag == null && avatarPicker == 0) {
								selectedMenuItem.visibility = View.INVISIBLE
							}

							headerView.visibility = View.INVISIBLE
						}
						else {
							searchItem.visibility = View.INVISIBLE
						}
					}
				})

				menuAnimator?.duration = 180
				menuAnimator?.start()
			}
		}
	}

	fun setDelegate(chatAttachViewDelegate: ChatAttachViewDelegate) {
		this.chatAttachViewDelegate = chatAttachViewDelegate
	}

	fun init() {
		botButtonWasVisible = false
		botButtonProgressWasVisible = false
		botMainButtonOffsetY = 0f
		botMainButtonTextView.visibility = View.GONE
		botProgressView.alpha = 0f
		botProgressView.scaleX = 0.1f
		botProgressView.scaleY = 0.1f
		botProgressView.visibility = View.GONE
		buttonsRecyclerView.alpha = 1f
		buttonsRecyclerView.translationY = 0f

		for (i in 0 until botAttachLayouts.size) {
			botAttachLayouts.valueAt(i)!!.setMeasureOffsetY(0)
		}

		shadow.alpha = if (baseFragment !is CreateMediaSaleFragment) 1f else 0f
		shadow.translationY = 0f

		if (baseFragment is ChatActivity && avatarPicker != 2) {
			val chat = baseFragment.currentChat
			// val user = baseFragment.currentUser

			if (chat != null) {
				mediaEnabled = canSendMedia(chat)
				// TODO: enable polls if needed
//				pollsEnabled = ChatObject.canSendPolls(chat);
			}
			else {
				// TODO: enable polls if needed
//				pollsEnabled = user != null && user.bot;
			}
		}
		else {
			commentTextView.visibility = View.INVISIBLE
		}

		photoLayout.onInit(mediaEnabled)
		commentTextView.hidePopup(true)
		enterCommentEventSent = false

		isFocusable = false

		val layoutToSet: AttachAlertLayout?

		if (isSoundPicker) {
			openDocumentsLayout(false)
			layoutToSet = documentLayout
			selectedId = 4
		}
		else if (editingMessageObject != null && (editingMessageObject!!.isMusic || editingMessageObject!!.isDocument()) && !editingMessageObject!!.isGif) {
			if (editingMessageObject!!.isMusic) {
				openAudioLayout(false)
				layoutToSet = audioLayout
				selectedId = 3
			}
			else {
				openDocumentsLayout(false)
				layoutToSet = documentLayout
				selectedId = 4
			}
			typeButtonsAvailable = !editingMessageObject!!.hasValidGroupId()
		}
		else {
			layoutToSet = photoLayout
			typeButtonsAvailable = avatarPicker == 0
			selectedId = 1
		}

		buttonsRecyclerView.visibility = if (typeButtonsAvailable) View.VISIBLE else View.GONE

		shadow.visibility = if (typeButtonsAvailable) View.VISIBLE else View.INVISIBLE

		if (currentAttachLayout !== layoutToSet) {
			if (actionBar.isSearchFieldVisible) {
				actionBar.closeSearchField()
			}

			containerView.removeView(currentAttachLayout)

			currentAttachLayout?.onHide()
			currentAttachLayout?.visibility = View.GONE
			currentAttachLayout?.onHidden()
			currentAttachLayout = layoutToSet

			setAllowNestedScroll(true)

			if (currentAttachLayout?.parent == null) {
				containerView.addView(currentAttachLayout, 0, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
			}

			layoutToSet?.alpha = 1.0f
			layoutToSet?.visibility = View.VISIBLE
			layoutToSet?.onShow(null)
			layoutToSet?.onShown()

			actionBar.visibility = if (layoutToSet?.needsActionBar() != 0) View.VISIBLE else View.INVISIBLE

			actionBarShadow.visibility = actionBar.visibility
		}

		if (currentAttachLayout !== photoLayout) {
			photoLayout.setCheckCameraWhenShown(true)
		}

		updateCountButton(0)

		buttonsAdapter.notifyDataSetChanged()

		commentTextView.text = ""

		buttonsLayoutManager.scrollToPositionWithOffset(0, 1000000)
	}

	fun onDestroy() {
		for (layout in layouts) {
			layout?.onDestroy()
		}

		NotificationCenter.getInstance(currentAccount).let {
			it.removeObserver(this, NotificationCenter.reloadInlineHints)
			it.removeObserver(this, NotificationCenter.attachMenuBotsDidLoad)
			it.removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged)
		}

		commentTextView.onDestroy()
	}

	override fun onOpenAnimationEnd() {
		currentAttachLayout?.onOpenAnimationEnd()
		AndroidUtilities.makeAccessibilityAnnouncement(context.getString(R.string.AccDescrAttachButton))
		openTransitionFinished = true
	}

	override fun onOpenAnimationStart() {
		// unused
	}

	override fun canDismiss(): Boolean {
		return true
	}

	override fun setAllowDrawContent(value: Boolean) {
		super.setAllowDrawContent(value)
		currentAttachLayout?.onContainerTranslationUpdated(currentPanTranslationY)
	}

	fun setAvatarPicker(type: Int, search: Boolean) {
		avatarPicker = type
		avatarSearch = search

		if (avatarPicker != 0) {
			typeButtonsAvailable = false

			if (currentAttachLayout == null) {
				buttonsRecyclerView.visibility = View.GONE
				shadow.visibility = View.GONE
			}

			if (avatarPicker == 2) {
				selectedTextView.text = context.getString(R.string.ChoosePhotoOrVideo)
			}
			else {
				selectedTextView.text = context.getString(R.string.ChoosePhoto)
			}
		}
		else {
			typeButtonsAvailable = true
		}
	}

	fun setSoundPicker() {
		isSoundPicker = true
		buttonsRecyclerView.visibility = View.GONE
		shadow.visibility = View.GONE
		selectedTextView.text = context.getString(R.string.ChoosePhotoOrVideo)
	}

	fun setMaxSelectedPhotos(value: Int, order: Boolean) {
		if (editingMessageObject != null) {
			return
		}

		maxSelectedPhotos = value
		allowOrder = order
	}

	fun setOpenWithFrontFaceCamera(value: Boolean) {
		openWithFrontFaceCamera = value
	}

	override fun dismissInternal() {
		if (chatAttachViewDelegate != null) {
			chatAttachViewDelegate?.doOnIdle {
				removeFromRoot()
			}
		}
		else {
			removeFromRoot()
		}
	}

	private fun removeFromRoot() {
		if (containerView != null) {
			containerView.visibility = View.INVISIBLE
		}

		if (actionBar.isSearchFieldVisible) {
			actionBar.closeSearchField()
		}

		contactsLayout = null
		audioLayout = null
		pollLayout = null
		locationLayout = null
		documentLayout = null

		for (a in 1 until layouts.size) {
			if (layouts[a] == null) {
				continue
			}

			layouts[a]?.onDestroy()

			containerView.removeView(layouts[a])

			layouts[a] = null
		}

		updateActionBarVisibility(show = false, animated = false)

		super.dismissInternal()
	}

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		if (actionBar.isSearchFieldVisible) {
			actionBar.closeSearchField()
			return
		}

		if (currentAttachLayout?.onBackPressed() == true) {
			return
		}

		if (commentTextView.isPopupShowing) {
			commentTextView.hidePopup(true)
			return
		}

		super.onBackPressed()
	}

	override fun dismissWithButtonClick(item: Int) {
		super.dismissWithButtonClick(item)
		currentAttachLayout?.onDismissWithButtonClick(item)
	}

	override fun canDismissWithTouchOutside(): Boolean {
		return currentAttachLayout?.canDismissWithTouchOutside() ?: false
	}

	override fun onDismissWithTouchOutside() {
		if (currentAttachLayout?.onDismissWithTouchOutside() == true) {
			dismiss()
		}
	}

	fun dismiss(passConfirmationAlert: Boolean) {
		if (passConfirmationAlert) {
			allowPassConfirmationAlert = true
		}

		this.dismiss()
	}

	override fun dismiss() {
		if (currentAttachLayout?.onDismiss() == true || isDismissed) {
			return
		}

		AndroidUtilities.hideKeyboard(commentTextView.editText)

		botAttachLayouts.clear()

		if (!allowPassConfirmationAlert && (currentAttachLayout?.selectedItemsCount ?: 0) > 0) {
			if (confirmationAlertShown) {
				return
			}

			confirmationAlertShown = true

			val dialog = AlertDialog.Builder(baseFragment.parentActivity!!).setTitle(context.getString(R.string.DiscardSelectionAlertTitle)).setMessage(context.getString(R.string.DiscardSelectionAlertMessage)).setPositiveButton(context.getString(R.string.PassportDiscard)) { _, _ ->
				allowPassConfirmationAlert = true
				dismiss()
			}.setNegativeButton(context.getString(R.string.Cancel), null).setOnCancelListener {
				appearSpringAnimation?.cancel()

				appearSpringAnimation = SpringAnimation(super.containerView, DynamicAnimation.TRANSLATION_Y, 0f)
				appearSpringAnimation?.spring?.dampingRatio = 1.5f
				appearSpringAnimation?.spring?.stiffness = 1500.0f
				appearSpringAnimation?.start()
			}.setOnPreDismissListener {
				confirmationAlertShown = false
			}.create()

			dialog.show()

			val button = dialog.getButton(BUTTON_POSITIVE) as? TextView
			button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))

			return
		}

		for (layout in layouts) {
			if (layout != null && currentAttachLayout !== layout) {
				layout.onDismiss()
			}
		}

		AndroidUtilities.setNavigationBarColor(window, ColorUtils.setAlphaComponent(context.getColor(R.color.light_background), 0), true) {
			navBarColor = it
			containerView.invalidate()
		}

		AndroidUtilities.setLightStatusBar(window, baseFragment.isLightStatusBar)

		super.dismiss()

		allowPassConfirmationAlert = false
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
		if (currentAttachLayout?.onSheetKeyDown(keyCode, event) == true) {
			return true
		}

		return super.onKeyDown(keyCode, event)
	}

	override fun setAllowNestedScroll(allowNestedScroll: Boolean) {
		this.allowNestedScroll = allowNestedScroll
	}

	interface ChatAttachViewDelegate {
		fun didPressedButton(button: Int, arg: Boolean, notify: Boolean, scheduleDate: Int, forceDocument: Boolean)

		fun onCameraOpened()

		val revealView: View?
			get() = null

		fun didSelectBot(user: TLRPC.User?) {}

		fun needEnterComment(): Boolean {
			return false
		}

		fun doOnIdle(runnable: Runnable) {
			runnable.run()
		}

		fun openAvatarsSearch() {}
	}

	open class AttachAlertLayout(val parentAlert: ChatAttachAlert, context: Context) : FrameLayout(context) {
		open fun onPreMeasure(availableWidth: Int, availableHeight: Int) {}
		open fun onMenuItemClick(id: Int) {}
		open fun checkColors() {}
		open fun onPause() {}
		open fun onResume() {}
		open fun onDismissWithButtonClick(item: Int) {}
		open fun onContainerTranslationUpdated(currentPanTranslationY: Float) {}
		open fun onHideShowProgress(progress: Float) {}
		open fun onOpenAnimationEnd() {}
		open fun onInit(mediaEnabled: Boolean) {}
		open fun onSelectedItemsCountChanged(count: Int) {}
		open fun applyCaption(text: CharSequence?) {}
		open fun onDestroy() {}
		open fun onHide() {}
		open fun onHidden() {}
		open fun sendSelectedItems(notify: Boolean, scheduleDate: Int) {}
		open fun onShow(previousLayout: AttachAlertLayout?) {}
		open fun onShown() {}
		open fun scrollToTop() {}
		open fun onPanTransitionStart(keyboardVisible: Boolean, contentHeight: Int) {}
		open fun onPanTransitionEnd() {}
		open fun onButtonsTranslationYUpdated() {}

		open fun onSheetKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
			return false
		}

		open fun onDismiss(): Boolean {
			return false
		}

		open fun onCustomMeasure(view: View?, width: Int, height: Int): Boolean {
			return false
		}

		open fun onCustomLayout(view: View?, left: Int, top: Int, right: Int, bottom: Int): Boolean {
			return false
		}

		open fun onContainerViewTouchEvent(event: MotionEvent?): Boolean {
			return false
		}

		open fun hasCustomBackground(): Boolean {
			return false
		}

		open val customBackground: Int
			get() = 0

		open fun canScheduleMessages(): Boolean {
			return true
		}

		open fun onDismissWithTouchOutside(): Boolean {
			return true
		}

		open fun canDismissWithTouchOutside(): Boolean {
			return true
		}

		open val selectedItemsCount: Int
			get() = 0

		open var currentItemTop: Int = 0

		open val firstOffset: Int
			get() = 0

		open val buttonsHideOffset: Int
			get() = AndroidUtilities.dp((if (needsActionBar() != 0) 12 else 17).toFloat())

		open val listTopPadding: Int
			get() = 0

		open fun needsActionBar(): Int {
			return 0
		}

		open fun onBackPressed(): Boolean {
			return false
		}

		open fun shouldHideBottomButtons(): Boolean {
			return true
		}
	}

	private inner class AttachButton(context: Context) : FrameLayout(context) {
		private val textView: TextView
		private val imageView: ImageView
		private val foregroundImageView: ImageView
		private var checked = false
		private var checkAnimator: Animator? = null
		private var currentId = 0

		@Keep
		var mCheckedState = 0f

		init {
			setWillNotDraw(false)

			isFocusable = true

			imageView = ImageView(context)
			imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE

			addView(imageView, createFrame(46, 46f, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0f, 12f, 0f, 0f))

			foregroundImageView = ImageView(context)
			foregroundImageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
			foregroundImageView.setImageResource(R.drawable.attach_selected_foreground)
			foregroundImageView.gone()

			addView(foregroundImageView, createFrame(42, 42f, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0f, 13.5f, 0f, 0f))

			textView = TextView(context)
			textView.maxLines = 2
			textView.gravity = Gravity.CENTER_HORIZONTAL
			textView.ellipsize = TextUtils.TruncateAt.END
			textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
			textView.setLineSpacing(-AndroidUtilities.dp(2f).toFloat(), 1.0f)
			textView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

			addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 63f, 0f, 0f))
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.text = textView.text
			info.isEnabled = true
			info.isSelected = checked
		}

		fun updateCheckedState(animate: Boolean) {
			if (checked == (currentId.toLong() == selectedId)) {
				return
			}

			checked = currentId.toLong() == selectedId

			checkAnimator?.cancel()

			if (animate) {
				checkAnimator = ObjectAnimator.ofFloat(this, "checkedState", if (checked) 1f else 0f)
				checkAnimator?.duration = 200
				checkAnimator?.start()
			}
			else {
				setCheckedState(if (checked) 1f else 0f)
			}
		}

		@Keep
		fun getCheckedState(): Float {
			return mCheckedState
		}

		@Keep
		fun setCheckedState(state: Float) {
			mCheckedState = state

			if (mCheckedState == 1f) {
				foregroundImageView.visible()
			}
			else {
				foregroundImageView.gone()
			}

			textView.setTextColor(ColorUtils.blendARGB(context.getColor(R.color.dark_gray), context.getColor(R.color.brand), state))
			invalidate()
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			updateCheckedState(false)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(attachItemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(84f), MeasureSpec.EXACTLY))
		}

		fun setTextAndIcon(id: Int, text: CharSequence?, @DrawableRes drawableResId: Int) {
			currentId = id
			textView.text = text

			if (drawableResId != 0) {
				imageView.setImageResource(drawableResId)
			}
			else {
				imageView.setImageDrawable(null)
			}

			textView.setTextColor(ColorUtils.blendARGB(context.getColor(R.color.dark_gray), this.context.getColor(R.color.brand), getCheckedState()))
		}

		override fun hasOverlappingRendering(): Boolean {
			return false
		}
	}

	private inner class AttachBotButton(context: Context) : FrameLayout(context) {
		val imageView: BackupImageView
		val nameTextView: TextView
		private val avatarDrawable = AvatarDrawable()
		private val selector: View?
		var currentUser: TLRPC.User? = null
		var attachMenuBot: TLRPC.TLAttachMenuBot? = null

		var checkedState = 0f
			set(value) {
				field = value
				imageView.scaleX = 1.0f - 0.06f * value
				imageView.scaleY = 1.0f - 0.06f * value
				nameTextView.setTextColor(ColorUtils.blendARGB(ResourcesCompat.getColor(resources, R.color.dark_gray, null), textColor, value))
				invalidate()
			}

		private var checked = false
		private var checkAnimator: ValueAnimator? = null
		var textColor = 0
		private var iconBackgroundColor = 0

		init {
			setWillNotDraw(false)

			isFocusable = true
			isFocusableInTouchMode = true

			imageView = object : BackupImageView(context) {
				init {
					imageReceiver.setDelegate(object : ImageReceiverDelegate {
						override fun onAnimationReady(imageReceiver: ImageReceiver) {
							// unused
						}

						override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
							val drawable = imageReceiver.drawable

							if (drawable is RLottieDrawable) {
								drawable.setCustomEndFrame(0)
								drawable.stop()
								drawable.setProgress(0f, false)
							}
						}
					})
				}

				override fun setScaleX(scaleX: Float) {
					super.setScaleX(scaleX)
					this@AttachBotButton.invalidate()
				}
			}

			imageView.setRoundRadius(AndroidUtilities.dp(25f))

			addView(imageView, createFrame(46, 46f, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0f, 9f, 0f, 0f))

			selector = View(context)
			selector.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background), 1, AndroidUtilities.dp(23f))

			addView(selector, createFrame(46, 46f, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0f, 9f, 0f, 0f))

			nameTextView = TextView(context)
			nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
			nameTextView.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
			nameTextView.setLines(1)
			nameTextView.isSingleLine = true
			nameTextView.ellipsize = TextUtils.TruncateAt.END

			addView(nameTextView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 6f, 60f, 6f, 0f))
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)

			info.isEnabled = true

			if (selector != null && checked) {
				info.isCheckable = true
				info.isChecked = true
			}
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(attachItemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100f), MeasureSpec.EXACTLY))
		}

		private fun updateMargins() {
			var params = nameTextView.layoutParams as MarginLayoutParams
			params.topMargin = AndroidUtilities.dp((if (attachMenuBot != null) 62 else 60).toFloat())
			params = imageView.layoutParams as MarginLayoutParams
			params.topMargin = AndroidUtilities.dp((if (attachMenuBot != null) 11 else 9).toFloat())
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			updateCheckedState(false)
		}

		fun updateCheckedState(animate: Boolean) {
			val newChecked = attachMenuBot != null && -currentUser!!.id == selectedId

			if (checked == newChecked && animate) {
				return
			}

			checked = newChecked

			checkAnimator?.cancel()

			val drawable = imageView.imageReceiver.lottieAnimation

			if (animate) {
				if (checked && drawable != null) {
					drawable.setAutoRepeat(0)
					drawable.setCustomEndFrame(-1)
					drawable.setProgress(0f, false)
					drawable.start()
				}

				checkAnimator = ValueAnimator.ofFloat(if (checked) 0f else 1f, if (checked) 1f else 0f)

				checkAnimator?.addUpdateListener {
					checkedState = it.animatedValue as Float
				}

				checkAnimator?.duration = 200
				checkAnimator?.start()
			}
			else {
				if (drawable != null) {
					drawable.stop()
					drawable.setProgress(0f, false)
				}

				checkedState = if (checked) 1f else 0f
			}
		}

		fun setUser(user: TLRPC.User?) {
			if (user == null) {
				return
			}

			nameTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))

			currentUser = user

			nameTextView.text = formatName(user.firstName, user.lastName)

			avatarDrawable.setInfo(user)

			imageView.setForUserOrChat(user, avatarDrawable)
			imageView.setSize(-1, -1)
			imageView.setColorFilter(null)

			attachMenuBot = null

			selector?.visibility = VISIBLE

			updateMargins()
			checkedState = 0f
			invalidate()
		}

		fun setAttachBot(user: TLRPC.User?, bot: TLRPC.TLAttachMenuBot?) {
			if (user == null || bot == null) {
				return
			}

			nameTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
			currentUser = user
			nameTextView.text = bot.shortName
			avatarDrawable.setInfo(user)

			var animated = true
			var icon = MediaDataController.getAnimatedAttachMenuBotIcon(bot)

			if (icon == null) {
				icon = MediaDataController.getStaticAttachMenuBotIcon(bot)
				animated = false
			}

			if (icon != null) {
				textColor = context.getColor(R.color.brand)
				iconBackgroundColor = context.getColor(R.color.brand)

				for (color in icon.colors) {
					when (color.name) {
						MediaDataController.ATTACH_MENU_BOT_COLOR_LIGHT_ICON -> if (!AndroidUtilities.isDarkTheme()) {
							iconBackgroundColor = color.color
						}

						MediaDataController.ATTACH_MENU_BOT_COLOR_LIGHT_TEXT -> if (!AndroidUtilities.isDarkTheme()) {
							textColor = color.color
						}

						MediaDataController.ATTACH_MENU_BOT_COLOR_DARK_ICON -> if (AndroidUtilities.isDarkTheme()) {
							iconBackgroundColor = color.color
						}

						MediaDataController.ATTACH_MENU_BOT_COLOR_DARK_TEXT -> if (AndroidUtilities.isDarkTheme()) {
							textColor = color.color
						}
					}
				}

				textColor = ColorUtils.setAlphaComponent(textColor, 0xFF)
				iconBackgroundColor = ColorUtils.setAlphaComponent(iconBackgroundColor, 0xFF)

				val iconDoc = icon.icon

				imageView.imageReceiver.setAllowStartLottieAnimation(false)
				imageView.setImage(ImageLocation.getForDocument(iconDoc), bot.botId.toString(), if (animated) "tgs" else "svg", DocumentObject.getSvgThumb(iconDoc, ResourcesCompat.getColor(context.resources, R.color.light_background, null), 1f), bot)
			}

			imageView.setSize(AndroidUtilities.dp(28f), AndroidUtilities.dp(28f))
			imageView.setColorFilter(PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.SRC_IN))

			attachMenuBot = bot

			selector?.visibility = GONE

			updateMargins()
			checkedState = 0f
			invalidate()
		}
	}

	private inner class ButtonsAdapter(private val mContext: Context) : SelectionAdapter() {
		private val attachMenuBots = mutableListOf<TLRPC.TLAttachMenuBot>()
		private var galleryButton = 0
		private var attachBotsStartRow = 0
		private var attachBotsEndRow = 0
		private var documentButton = 0
		private var musicButton = 0
		private var pollButton = 0
		private var contactButton = 0
		private var locationButton = 0

		var buttonsCount = 0
			private set

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = when (viewType) {
				Companion.VIEW_TYPE_BUTTON -> AttachButton(mContext)
				Companion.VIEW_TYPE_BOT_BUTTON -> AttachBotButton(mContext)
				else -> AttachBotButton(mContext)
			}

			view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
			view.isFocusable = true

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			@Suppress("NAME_SHADOWING") var position = position

			when (holder.itemViewType) {
				Companion.VIEW_TYPE_BUTTON -> {
					val attachButton = holder.itemView as AttachButton

					when (position) {
						galleryButton -> {
							attachButton.setTextAndIcon(1, mContext.getString(R.string.ChatGallery), R.drawable.attach_gallery)
							attachButton.tag = 1
						}

						documentButton -> {
							attachButton.setTextAndIcon(4, mContext.getString(R.string.ChatDocument), R.drawable.attach_file)
							attachButton.tag = 4
						}

						locationButton -> {
							attachButton.setTextAndIcon(6, mContext.getString(R.string.ChatLocation), R.drawable.attach_location)
							attachButton.tag = 6
						}

						musicButton -> {
							attachButton.setTextAndIcon(3, mContext.getString(R.string.AttachMusic), R.drawable.attach_music)
							attachButton.tag = 3
						}

						pollButton -> {
							attachButton.setTextAndIcon(9, mContext.getString(R.string.Poll), R.drawable.attach_file)
							attachButton.tag = 9
						}

						contactButton -> {
							attachButton.setTextAndIcon(5, mContext.getString(R.string.AttachContact), R.drawable.attach_contact)
							attachButton.tag = 5
						}
					}
				}

				Companion.VIEW_TYPE_BOT_BUTTON -> {
					val child = holder.itemView as AttachBotButton

					if (position in attachBotsStartRow until attachBotsEndRow) {
						position -= attachBotsStartRow
						child.tag = position
						val bot = attachMenuBots[position]
						child.setAttachBot(MessagesController.getInstance(currentAccount).getUser(bot.botId), bot)
						return
					}

					position -= buttonsCount

					child.tag = position
					child.setUser(MessagesController.getInstance(currentAccount).getUser(MediaDataController.getInstance(currentAccount).inlineBots[position].peer.userId))
				}
			}
		}

		override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
			applyAttachButtonColors(holder.itemView)
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return false
		}

		override fun getItemCount(): Int {
			var count = buttonsCount

			if (editingMessageObject == null && baseFragment is ChatActivity) {
				count += MediaDataController.getInstance(currentAccount).inlineBots.size
			}

			return count
		}

		override fun notifyDataSetChanged() {
			buttonsCount = 0
			galleryButton = -1
			documentButton = -1
			musicButton = -1
			pollButton = -1
			contactButton = -1
			locationButton = -1
			attachBotsStartRow = -1
			attachBotsEndRow = -1

			if (baseFragment is ChatActivity) {
				val user = baseFragment.currentUser

				if (user != null && user.bot) {
					galleryButton = buttonsCount++
					documentButton = buttonsCount++
					return
				}
			}

			if (baseFragment !is ChatActivity) {
				galleryButton = buttonsCount++
				documentButton = buttonsCount++

				if (baseFragment is CreateMediaSaleFragment && mediaEnabled) {
					musicButton = buttonsCount++
				}
			}
			else if (editingMessageObject != null) {
				editingMessageObject?.let { editingMessageObject ->
					if ((editingMessageObject.isMusic || editingMessageObject.isDocument()) && editingMessageObject.hasValidGroupId()) {
						if (editingMessageObject.isMusic) {
							musicButton = buttonsCount++
						}
						else {
							documentButton = buttonsCount++
						}
					}
					else {
						galleryButton = buttonsCount++
						documentButton = buttonsCount++
						musicButton = buttonsCount++
					}
				}
			}
			else {
				if (mediaEnabled) {
					galleryButton = buttonsCount++

					if (!baseFragment.isInScheduleMode && !baseFragment.isSecretChat) {
						attachBotsStartRow = buttonsCount
						attachMenuBots.clear()

						for (bot in MediaDataController.getInstance(currentAccount).attachMenuBots.bots) {
							if (MediaDataController.canShowAttachMenuBot(bot, baseFragment.currentChat ?: baseFragment.currentUser)) {
								attachMenuBots.add(bot)
							}
						}

						buttonsCount += attachMenuBots.size
						attachBotsEndRow = buttonsCount
					}

					documentButton = buttonsCount++
				}

				locationButton = buttonsCount++

				if (pollsEnabled) {
					pollButton = buttonsCount++
				}
				else {
					contactButton = buttonsCount++
				}

				if (mediaEnabled) {
					musicButton = buttonsCount++
				}

				val user = baseFragment.currentUser

				if (user != null && user.bot) {
					contactButton = buttonsCount++
				}
			}

			super.notifyDataSetChanged()
		}

		override fun getItemViewType(position: Int): Int {
			return if (position < buttonsCount) {
				if (position in attachBotsStartRow until attachBotsEndRow) {
					VIEW_TYPE_BOT_BUTTON
				}
				else {
					VIEW_TYPE_BUTTON
				}
			}
			else {
				VIEW_TYPE_BOT_BUTTON
			}
		}
	}

	companion object {
		private const val VIEW_TYPE_BUTTON = 0
		private const val VIEW_TYPE_BOT_BUTTON = 1
		const val REQUEST_CODE_ATTACH_FILE = 21
		const val REQUEST_CODE_LIVE_LOCATION = 30
	}
}
