/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.View.MeasureSpec
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaController.AlbumEntry
import org.telegram.messenger.MediaController.PhotoEntry
import org.telegram.messenger.MediaController.SearchImage
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper.SendingMediaInfo
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject.isUserSelf
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.PhotoPickerAlbumsCell
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.EditTextEmoji
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.PhotoPickerActivity.PhotoPickerActivityDelegate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class PhotoAlbumPickerActivity(private val selectPhotoType: Int, private val allowGifs: Boolean, private val allowCaption: Boolean, private val chatActivity: ChatActivity?, private val isMediaSale: Boolean) : BaseFragment(), NotificationCenterDelegate {
	interface PhotoAlbumPickerActivityDelegate {
		fun didSelectPhotos(photos: List<SendingMediaInfo>?, notify: Boolean, scheduleDate: Int)

		fun startPhotoSelectActivity()
	}

	private var caption: CharSequence? = null
	private val selectedPhotos = HashMap<Any, Any>()
	private val selectedPhotosOrder = ArrayList<Any>()
	private var albumsSorted: ArrayList<AlbumEntry>? = null
	private var loading = false
	private var columnsCount = 2
	private var listView: RecyclerListView? = null
	private var listAdapter: ListAdapter? = null
	private var progressView: FrameLayout? = null
	private var emptyView: TextView? = null
	private var sendPressed = false
	private var allowSearchImages = true
	private var maxSelectedPhotos = 0
	private var allowOrder = true
	private var sendPopupWindow: ActionBarPopupWindow? = null
	private var sendPopupLayout: ActionBarPopupWindowLayout? = null
	private var itemCells: Array<ActionBarMenuSubItem?>? = null
	private var frameLayout2: FrameLayout? = null
	private var commentTextView: EditTextEmoji? = null
	private var writeButtonContainer: FrameLayout? = null
	private var selectedCountView: View? = null
	private var shadow: View? = null
	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val rect = RectF()
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var delegate: PhotoAlbumPickerActivityDelegate? = null

	override fun onFragmentCreate(): Boolean {
		albumsSorted = if (selectPhotoType == SELECT_TYPE_AVATAR || selectPhotoType == SELECT_TYPE_WALLPAPER || selectPhotoType == SELECT_TYPE_QR || !allowSearchImages) {
			MediaController.allPhotoAlbums
		}
		else {
			MediaController.allMediaAlbums
		}

		loading = albumsSorted == null

		MediaController.loadGalleryPhotosAlbums(classGuid)

		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.albumsDidLoad)
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats)

		return super.onFragmentCreate()
	}

	override fun onFragmentDestroy() {
		commentTextView?.onDestroy()

		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.albumsDidLoad)
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats)

		super.onFragmentDestroy()
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.Gallery))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}

					OPEN_EXTERNAL_APP -> {
						if (delegate != null) {
							finishFragment(false)
							delegate?.startPhotoSelectActivity()
						}
					}

					SEARCH_IMAGES -> {
						openPhotoPicker(null, 0)
					}
				}
			}
		})

		val menu = actionBar?.createMenu()

		if (allowSearchImages) {
			menu?.addItem(SEARCH_IMAGES, R.drawable.ic_search_menu)?.contentDescription = context.getString(R.string.Search)
		}

		val menuItem = menu?.addItem(0, R.drawable.overflow_menu)
		menuItem?.contentDescription = context.getString(R.string.AccDescrMoreOptions)
		menuItem?.addSubItem(OPEN_EXTERNAL_APP, R.drawable.msg_openin, context.getString(R.string.OpenInExternalApp))

		val sizeNotifierFrameLayout: SizeNotifierFrameLayout = object : SizeNotifierFrameLayout(context) {
			private var lastNotifyWidth = 0
			private var ignoreLayout = false

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				@Suppress("NAME_SHADOWING") var heightMeasureSpec = heightMeasureSpec
				val widthSize = MeasureSpec.getSize(widthMeasureSpec)
				var heightSize = MeasureSpec.getSize(heightMeasureSpec)

				setMeasuredDimension(widthSize, heightSize)

				val keyboardSize = if (SharedConfig.smoothKeyboard) 0 else measureKeyboardHeight()

				if (keyboardSize <= AndroidUtilities.dp(20f)) {
					if (!AndroidUtilities.isInMultiwindow) {
						heightSize -= (commentTextView?.emojiPadding ?: 0)
						heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY)
					}
				}
				else {
					ignoreLayout = true
					commentTextView!!.hideEmojiView()
					ignoreLayout = false
				}

				val childCount = childCount

				for (i in 0 until childCount) {
					val child = getChildAt(i)

					if (child == null || child.visibility == GONE) {
						continue
					}

					if (commentTextView?.isPopupView(child) == true) {
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
				if (lastNotifyWidth != r - l) {
					lastNotifyWidth = r - l

					if (sendPopupWindow?.isShowing == true) {
						sendPopupWindow?.dismiss()
					}
				}

				val count = childCount
				val keyboardSize = if (SharedConfig.smoothKeyboard) 0 else measureKeyboardHeight()
				val paddingBottom = if (keyboardSize <= AndroidUtilities.dp(20f) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) (commentTextView?.emojiPadding ?: 0) else 0

				setBottomClip(paddingBottom)

				for (i in 0 until count) {
					val child = getChildAt(i)

					if (child.visibility == GONE) {
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
						Gravity.RIGHT -> r - l - width - lp.rightMargin - paddingRight
						Gravity.LEFT -> lp.leftMargin + paddingLeft
						else -> lp.leftMargin + paddingLeft
					}

					childTop = when (verticalGravity) {
						Gravity.TOP -> lp.topMargin + paddingTop
						Gravity.CENTER_VERTICAL -> (b - paddingBottom - t - height) / 2 + lp.topMargin - lp.bottomMargin
						Gravity.BOTTOM -> b - paddingBottom - t - height - lp.bottomMargin
						else -> lp.topMargin
					}

					if (commentTextView != null && commentTextView!!.isPopupView(child)) {
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
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}
		}

		sizeNotifierFrameLayout.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

		fragmentView = sizeNotifierFrameLayout

		listView = RecyclerListView(context)
		listView?.setPadding(AndroidUtilities.dp(6f), AndroidUtilities.dp(4f), AndroidUtilities.dp(6f), AndroidUtilities.dp(54f))
		listView?.clipToPadding = false
		listView?.isHorizontalScrollBarEnabled = false
		listView?.isVerticalScrollBarEnabled = false
		listView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

		sizeNotifierFrameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT or Gravity.TOP))

		listView?.adapter = ListAdapter(context).also { listAdapter = it }
		listView?.setGlowColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))

		emptyView = TextView(context)
		emptyView?.setTextColor(-0x7f7f80)
		emptyView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		emptyView?.gravity = Gravity.CENTER
		emptyView?.visibility = View.GONE
		emptyView?.text = context.getString(R.string.NoPhotos)

		sizeNotifierFrameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 0f, 0f, 48f))

		emptyView?.setOnTouchListener { _, _ -> true }

		progressView = FrameLayout(context)
		progressView?.visibility = View.GONE

		sizeNotifierFrameLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 0f, 0f, 48f))

		val progressBar = RadialProgressView(context)
		progressBar.setProgressColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))

		progressView?.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

		shadow = View(context)
		shadow?.setBackgroundResource(R.drawable.header_shadow_reverse)
		shadow?.translationY = AndroidUtilities.dp(48f).toFloat()

		sizeNotifierFrameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3f, Gravity.BOTTOM or Gravity.LEFT, 0f, 0f, 0f, 48f))

		frameLayout2 = FrameLayout(context)
		frameLayout2?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		frameLayout2?.visibility = View.INVISIBLE
		frameLayout2?.translationY = AndroidUtilities.dp(48f).toFloat()

		sizeNotifierFrameLayout.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.BOTTOM))

		frameLayout2?.setOnTouchListener { _, _ -> true }

		commentTextView?.onDestroy()

		commentTextView = EditTextEmoji(context, sizeNotifierFrameLayout, null, EditTextEmoji.STYLE_DIALOG, false)

		val inputFilters = arrayOf<InputFilter>(LengthFilter(MessagesController.getInstance(UserConfig.selectedAccount).maxCaptionLength))

		commentTextView?.setFilters(inputFilters)
		commentTextView?.setHint(context.getString(R.string.AddCaption))

		val editText = commentTextView?.editText
		editText?.maxLines = 1
		editText?.isSingleLine = true

		frameLayout2?.addView(commentTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 84f, 0f))

		if (caption != null) {
			commentTextView?.text = caption
		}

		writeButtonContainer = object : FrameLayout(context) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)
				info.text = LocaleController.formatPluralString("AccDescrSendPhotos", selectedPhotos.size)
				info.className = Button::class.java.name
				info.isLongClickable = true
				info.isClickable = true
			}
		}

		writeButtonContainer?.isFocusable = true
		writeButtonContainer?.isFocusableInTouchMode = true
		writeButtonContainer?.visibility = View.INVISIBLE
		writeButtonContainer?.scaleX = 0.2f
		writeButtonContainer?.scaleY = 0.2f
		writeButtonContainer?.alpha = 0.0f

		sizeNotifierFrameLayout.addView(writeButtonContainer, LayoutHelper.createFrame(60, 60f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, 12f, 10f))

		val writeButton = ImageView(context)
		val writeButtonDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56f), ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.darker_brand, null))
		writeButton.background = writeButtonDrawable
		writeButton.setImageResource(R.drawable.arrow_up)
		writeButton.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		writeButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.SRC_IN)
		writeButton.scaleType = ImageView.ScaleType.CENTER

		writeButton.outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, AndroidUtilities.dp(56f), AndroidUtilities.dp(56f))
			}
		}

		writeButtonContainer?.addView(writeButton, LayoutHelper.createFrame(56, 56f, Gravity.LEFT or Gravity.TOP, 2f, 0f, 0f, 0f))

		writeButton.setOnClickListener {
			if (chatActivity != null && chatActivity.isInScheduleMode) {
				AlertsCreator.createScheduleDatePickerDialog(parentActivity, chatActivity.dialogId) { notify, scheduleDate ->
					sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, notify, scheduleDate)
					finishFragment()
				}
			}
			else {
				sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, true, 0)
				finishFragment()
			}
		}

		writeButton.setOnLongClickListener { view ->
			if (chatActivity == null || maxSelectedPhotos == 1) {
				return@setOnLongClickListener false
			}

			// val chat = chatActivity.getCurrentChat()
			val user = chatActivity.currentUser

			if (sendPopupLayout == null) {
				sendPopupLayout = ActionBarPopupWindowLayout(parentActivity!!)
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

				sendPopupLayout?.setDispatchKeyEventListener { keyEvent ->
					if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK && keyEvent.repeatCount == 0 && sendPopupWindow?.isShowing == true) {
						sendPopupWindow?.dismiss()
					}
				}

				sendPopupLayout?.setShownFromBottom(false)

				itemCells = arrayOfNulls(2)

				for (a in 0..1) {
					if (a == 0 && !chatActivity.canScheduleMessage() || a == 1 && isUserSelf(user)) {
						continue
					}

					itemCells?.set(a, ActionBarMenuSubItem(parentActivity!!, a == 0, a == 1))

					if (a == 0) {
						if (isUserSelf(user)) {
							itemCells?.get(a)?.setTextAndIcon(context.getString(R.string.SetReminder), R.drawable.msg_calendar2)
						}
						else {
							itemCells?.get(a)?.setTextAndIcon(context.getString(R.string.ScheduleMessage), R.drawable.msg_calendar2)
						}
					}
					else {
						itemCells?.get(a)?.setTextAndIcon(context.getString(R.string.SendWithoutSound), R.drawable.input_notify_off)
					}

					itemCells?.get(a)?.minimumWidth = AndroidUtilities.dp(196f)

					sendPopupLayout?.addView(itemCells?.get(a), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48))

					itemCells?.get(a)?.setOnClickListener {
						if (sendPopupWindow?.isShowing == true) {
							sendPopupWindow?.dismiss()
						}

						if (a == 0) {
							AlertsCreator.createScheduleDatePickerDialog(parentActivity, chatActivity.dialogId) { notify, scheduleDate ->
								sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, notify, scheduleDate)
								finishFragment()
							}
						}
						else {
							sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, true, 0)
							finishFragment()
						}
					}
				}

				sendPopupLayout?.setupRadialSelectors(ResourcesCompat.getColor(context.resources, R.color.light_background, null))

				sendPopupWindow = ActionBarPopupWindow(sendPopupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
				sendPopupWindow?.setAnimationEnabled(false)
				sendPopupWindow?.animationStyle = R.style.PopupContextAnimation2
				sendPopupWindow?.isOutsideTouchable = true
				sendPopupWindow?.isClippingEnabled = true
				sendPopupWindow?.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
				sendPopupWindow?.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
				sendPopupWindow?.contentView?.isFocusableInTouchMode = true
			}

			sendPopupLayout?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST))
			sendPopupWindow?.isFocusable = true

			val location = IntArray(2)

			view.getLocationInWindow(location)

			sendPopupWindow?.showAtLocation(view, Gravity.LEFT or Gravity.TOP, location[0] + view.measuredWidth - sendPopupLayout!!.measuredWidth + AndroidUtilities.dp(8f), location[1] - sendPopupLayout!!.measuredHeight - AndroidUtilities.dp(2f))
			sendPopupWindow?.dimBehind()

			view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

			false
		}

		textPaint.textSize = AndroidUtilities.dp(12f).toFloat()
		textPaint.typeface = Theme.TYPEFACE_BOLD

		selectedCountView = object : View(context) {
			override fun onDraw(canvas: Canvas) {
				val text = String.format("%d", max(1, selectedPhotosOrder.size))
				val textSize = ceil(textPaint.measureText(text).toDouble()).toInt()
				val size = max(AndroidUtilities.dp(16f) + textSize, AndroidUtilities.dp(24f))
				val cx = measuredWidth / 2
				// val cy = measuredHeight / 2
				textPaint.color = Theme.getColor(Theme.key_dialogRoundCheckBoxCheck)
				paint.color = Theme.getColor(Theme.key_dialogBackground)
				rect.set((cx - size / 2).toFloat(), 0f, (cx + size / 2).toFloat(), measuredHeight.toFloat())
				canvas.drawRoundRect(rect, AndroidUtilities.dp(12f).toFloat(), AndroidUtilities.dp(12f).toFloat(), paint)
				paint.color = Theme.getColor(Theme.key_dialogRoundCheckBox)
				rect.set((cx - size / 2 + AndroidUtilities.dp(2f)).toFloat(), AndroidUtilities.dp(2f).toFloat(), (cx + size / 2 - AndroidUtilities.dp(2f)).toFloat(), (measuredHeight - AndroidUtilities.dp(2f)).toFloat())
				canvas.drawRoundRect(rect, AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(), paint)
				canvas.drawText(text, (cx - textSize / 2).toFloat(), AndroidUtilities.dp(16.2f).toFloat(), textPaint)
			}
		}

		selectedCountView?.alpha = 0.0f
		selectedCountView?.scaleX = 0.2f
		selectedCountView?.scaleY = 0.2f

		sizeNotifierFrameLayout.addView(selectedCountView, LayoutHelper.createFrame(42, 24f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, -2f, 9f))

		if (selectPhotoType != SELECT_TYPE_ALL) {
			commentTextView?.visibility = View.GONE
		}

		if (loading && (albumsSorted == null || albumsSorted.isNullOrEmpty())) {
			progressView?.visibility = View.VISIBLE
			listView?.setEmptyView(null)
		}
		else {
			progressView?.visibility = View.GONE
			listView?.setEmptyView(emptyView)
		}

		return fragmentView
	}

	override fun onResume() {
		super.onResume()
		listAdapter?.notifyDataSetChanged()
		commentTextView?.onResume()
		fixLayout()
	}

	override fun onPause() {
		super.onPause()
		commentTextView?.onPause()
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		fixLayout()
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.albumsDidLoad -> {
				val guid = args[0] as Int

				if (classGuid == guid) {
					albumsSorted = if (selectPhotoType == SELECT_TYPE_AVATAR || selectPhotoType == SELECT_TYPE_WALLPAPER || selectPhotoType == SELECT_TYPE_QR || !allowSearchImages) {
						args[2] as List<AlbumEntry>
					}
					else {
						args[1] as List<AlbumEntry>
					}.let {
						ArrayList(it)
					}

					progressView?.visibility = View.GONE

					if (listView != null && listView?.emptyView == null) {
						listView?.setEmptyView(emptyView)
					}

					listAdapter?.notifyDataSetChanged()

					loading = false
				}
			}

			NotificationCenter.closeChats -> {
				removeSelfFromStack()
			}
		}
	}

	override fun onBackPressed(): Boolean {
		if (commentTextView?.isPopupShowing == true) {
			commentTextView?.hidePopup(true)
			return false
		}

		return super.onBackPressed()
	}

	fun setMaxSelectedPhotos(value: Int, order: Boolean) {
		maxSelectedPhotos = value
		allowOrder = order
	}

	fun setAllowSearchImages(value: Boolean) {
		allowSearchImages = value
	}

	fun setDelegate(delegate: PhotoAlbumPickerActivityDelegate?) {
		this.delegate = delegate
	}

	private fun sendSelectedPhotos(photos: HashMap<Any, Any>, order: ArrayList<Any>, notify: Boolean, scheduleDate: Int) {
		if (photos.isEmpty() || delegate == null || sendPressed) {
			return
		}

		sendPressed = true

		val media = ArrayList<SendingMediaInfo>()

		for (a in order.indices) {
			val `object` = photos[order[a]]
			val info = SendingMediaInfo()

			media.add(info)

			if (`object` is PhotoEntry) {
				if (`object`.imagePath != null) {
					info.path = `object`.imagePath
				}
				else {
					info.path = `object`.path
				}

				info.thumbPath = `object`.thumbPath
				info.videoEditedInfo = `object`.editedInfo
				info.isVideo = `object`.isVideo
				info.caption = `object`.caption?.toString()
				info.entities = `object`.entities
				info.masks = `object`.stickers
				info.ttl = `object`.ttl
			}
			else if (`object` is SearchImage) {
				if (`object`.imagePath != null) {
					info.path = `object`.imagePath
				}
				else {
					info.searchImage = `object`
				}

				info.thumbPath = `object`.thumbPath
				info.videoEditedInfo = `object`.editedInfo
				info.caption = `object`.caption?.toString()
				info.entities = `object`.entities
				info.masks = `object`.stickers
				info.ttl = `object`.ttl

				if (`object`.inlineResult != null && `object`.type == 1) {
					info.inlineResult = `object`.inlineResult
					info.params = `object`.params
				}

				`object`.date = (System.currentTimeMillis() / 1000).toInt()
			}
		}

		delegate?.didSelectPhotos(media, notify, scheduleDate)
	}

	private fun fixLayout() {
		if (listView != null) {
			listView?.viewTreeObserver?.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
				override fun onPreDraw(): Boolean {
					fixLayoutInternal()
					listView?.viewTreeObserver?.removeOnPreDrawListener(this)
					return true
				}
			})
		}
	}

//	private fun applyCaption() {
//		val commentTextView = commentTextView ?: return
//
//		if (commentTextView.length() <= 0) {
//			return
//		}
//
//		val imageId = selectedPhotosOrder[0] as Int
//		val entry = selectedPhotos[imageId]
//
//		if (entry is PhotoEntry) {
//			entry.caption = commentTextView.text?.toString()
//		}
//		else if (entry is SearchImage) {
//			entry.caption = commentTextView.text?.toString()
//		}
//	}

	private fun fixLayoutInternal() {
		if (parentActivity == null) {
			return
		}

		val manager = ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE) as WindowManager
		val rotation = manager.defaultDisplay.rotation

		columnsCount = 2

		if (!AndroidUtilities.isTablet() && (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90)) {
			columnsCount = 4
		}

		listAdapter?.notifyDataSetChanged()
	}

	private fun showCommentTextView(show: Boolean): Boolean {
		if (show == (frameLayout2?.tag != null)) {
			return false
		}

		frameLayout2?.tag = if (show) 1 else null

		if (commentTextView?.editText?.isFocused == true) {
			AndroidUtilities.hideKeyboard(commentTextView?.editText)
		}

		commentTextView?.hidePopup(true)

		if (show) {
			frameLayout2?.visibility = View.VISIBLE
			writeButtonContainer?.visibility = View.VISIBLE
		}
		else {
			frameLayout2?.visibility = View.INVISIBLE
			writeButtonContainer?.visibility = View.INVISIBLE
		}

		writeButtonContainer?.scaleX = if (show) 1.0f else 0.2f
		writeButtonContainer?.scaleY = if (show) 1.0f else 0.2f
		writeButtonContainer?.alpha = if (show) 1.0f else 0.0f

		selectedCountView?.scaleX = if (show) 1.0f else 0.2f
		selectedCountView?.scaleY = if (show) 1.0f else 0.2f
		selectedCountView?.alpha = if (show) 1.0f else 0.0f

		frameLayout2?.translationY = (if (show) 0 else AndroidUtilities.dp(48f)).toFloat()

		shadow?.translationY = (if (show) 0 else AndroidUtilities.dp(48f)).toFloat()

		return true
	}

	private fun updatePhotosButton() {
		val count = selectedPhotos.size

		if (count == 0) {
			selectedCountView?.pivotX = 0f
			selectedCountView?.pivotY = 0f

			showCommentTextView(false)
		}
		else {
			selectedCountView?.invalidate()

			showCommentTextView(true)
		}
	}

	private fun openPhotoPicker(albumEntry: AlbumEntry?, type: Int) {
		if (albumEntry != null) {
			val fragment = PhotoPickerActivity(type, albumEntry, selectedPhotos, selectedPhotosOrder, selectPhotoType, allowCaption, chatActivity, false, isMediaSale)
			fragment.setCaption(commentTextView?.text?.also { caption = it })

			fragment.setDelegate(object : PhotoPickerActivityDelegate {
				override fun selectedPhotosChanged() {
					updatePhotosButton()
				}

				override fun actionButtonPressed(canceled: Boolean, notify: Boolean, scheduleDate: Int) {
					removeSelfFromStack()

					if (!canceled) {
						sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, notify, scheduleDate)
					}
				}

				override fun onCaptionChanged(text: CharSequence) {
					caption = text
					commentTextView?.text = caption
				}
			})

			fragment.setMaxSelectedPhotos(maxSelectedPhotos, allowOrder)

			presentFragment(fragment)
		}
		else {
			val photos = HashMap<Any, Any>()
			val order = ArrayList<Any>()

			if (allowGifs) {
				val fragment = PhotoPickerSearchActivity(photos, order, selectPhotoType, allowCaption, chatActivity)
				fragment.setCaption(commentTextView?.text?.also { caption = it })

				fragment.setDelegate(object : PhotoPickerActivityDelegate {
					override fun selectedPhotosChanged() {
						// unused
					}

					override fun actionButtonPressed(canceled: Boolean, notify: Boolean, scheduleDate: Int) {
						removeSelfFromStack()

						if (!canceled) {
							sendSelectedPhotos(photos, order, notify, scheduleDate)
						}
					}

					override fun onCaptionChanged(text: CharSequence) {
						caption = text
						commentTextView?.text = caption
					}
				})

				fragment.setMaxSelectedPhotos(maxSelectedPhotos, allowOrder)

				presentFragment(fragment)
			}
			else {
				val fragment = PhotoPickerActivity(0, albumEntry, photos, order, selectPhotoType, allowCaption, chatActivity, false, isMediaSale)
				fragment.setCaption(commentTextView?.text?.also { caption = it })

				fragment.setDelegate(object : PhotoPickerActivityDelegate {
					override fun selectedPhotosChanged() {
						// unused
					}

					override fun actionButtonPressed(canceled: Boolean, notify: Boolean, scheduleDate: Int) {
						removeSelfFromStack()

						if (!canceled) {
							sendSelectedPhotos(photos, order, notify, scheduleDate)
						}
					}

					override fun onCaptionChanged(text: CharSequence) {
						caption = text
						commentTextView?.text = caption
					}
				})

				fragment.setMaxSelectedPhotos(maxSelectedPhotos, allowOrder)

				presentFragment(fragment)
			}
		}
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return true
		}

		override fun getItemCount(): Int {
			return ceil(((albumsSorted?.size ?: 0) / columnsCount.toFloat()).toDouble()).toInt()
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val cell = PhotoPickerAlbumsCell(mContext)

			cell.setDelegate {
				openPhotoPicker(it, 0)
			}

			return RecyclerListView.Holder(cell)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val photoPickerAlbumsCell = holder.itemView as PhotoPickerAlbumsCell
			photoPickerAlbumsCell.setAlbumsCount(columnsCount)

			for (a in 0 until columnsCount) {
				val index = position * columnsCount + a

				if (index < (albumsSorted?.size ?: 0)) {
					val albumEntry = albumsSorted?.get(index)
					photoPickerAlbumsCell.setAlbum(a, albumEntry)
				}
				else {
					photoPickerAlbumsCell.setAlbum(a, null)
				}
			}

			photoPickerAlbumsCell.requestLayout()
		}

		override fun getItemViewType(i: Int): Int {
			return 0
		}
	}

	companion object {
		@JvmField
		var SELECT_TYPE_ALL = 0

		@JvmField
		var SELECT_TYPE_AVATAR = 1

		@JvmField
		var SELECT_TYPE_WALLPAPER = 2

		@JvmField
		var SELECT_TYPE_AVATAR_VIDEO = 3

		@JvmField
		var SELECT_TYPE_QR = 10

		private const val OPEN_EXTERNAL_APP = 1
		private const val SEARCH_IMAGES = 2
	}
}
