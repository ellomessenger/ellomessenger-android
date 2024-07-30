/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.Components

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ImageSpan
import android.util.Property
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnDrawListener
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.annotation.Keep
import androidx.appcompat.widget.AppCompatImageView
import androidx.collection.LongSparseArray
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputConnectionCompat.OnCommitContentListener
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.customview.widget.ExploreByTouchHelper
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationUpdateListener
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.ChatListItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.ChatObject
import org.telegram.messenger.DialogObject
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaController.PhotoEntry
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.SendMessagesHelper.SendingMediaInfo
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.SharedPrefsHelper
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.UserObject.getUserName
import org.telegram.messenger.UserObject.isUserSelf
import org.telegram.messenger.Utilities
import org.telegram.messenger.VideoEditedInfo
import org.telegram.messenger.camera.CameraController
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.messageobject.SendAnimationData
import org.telegram.messenger.utils.dp
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.vibrate
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.TL_inputMessageEntityMentionName
import org.telegram.tgnet.tlrpc.TL_message
import org.telegram.tgnet.tlrpc.TL_messageEntityBold
import org.telegram.tgnet.tlrpc.TL_messageEntityCode
import org.telegram.tgnet.tlrpc.TL_messageEntityCustomEmoji
import org.telegram.tgnet.tlrpc.TL_messageEntityItalic
import org.telegram.tgnet.tlrpc.TL_messageEntityMentionName
import org.telegram.tgnet.tlrpc.TL_messageEntityPre
import org.telegram.tgnet.tlrpc.TL_messageEntitySpoiler
import org.telegram.tgnet.tlrpc.TL_messageEntityStrike
import org.telegram.tgnet.tlrpc.TL_messageEntityTextUrl
import org.telegram.tgnet.tlrpc.TL_messageEntityUnderline
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.BasePermissionsActivity
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.BotCommandsMenuView.BotCommandsAdapter
import org.telegram.ui.Components.EmojiView.EmojiViewDelegate
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet
import org.telegram.ui.Components.SenderSelectPopup.OnSelectCallback
import org.telegram.ui.Components.SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate
import org.telegram.ui.Components.StickersAlert.StickersAlertDelegate
import org.telegram.ui.Components.TextStyleSpan.TextStyleRun
import org.telegram.ui.Components.VideoTimelineView.TimeHintView
import org.telegram.ui.ContentPreviewViewer
import org.telegram.ui.DialogsActivity
import org.telegram.ui.LaunchActivity
import org.telegram.ui.PhotoViewer
import org.telegram.ui.PhotoViewer.EmptyPhotoViewerProvider
import org.telegram.ui.PremiumPreviewFragment
import org.telegram.ui.ProfileActivity
import org.telegram.ui.StickersActivity
import org.telegram.ui.aibot.AiSubscriptionPlansFragment
import org.telegram.ui.group.GroupStickersActivity
import org.telegram.ui.sales.CreateMediaSaleFragment
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
open class ChatActivityEnterView(context: Activity, parent: SizeNotifierFrameLayout?, fragment: ChatActivity?, isChat: Boolean, val isBot: Boolean) : BlurredFrameLayout(context, fragment?.contentView), NotificationCenterDelegate, SizeNotifierFrameLayoutDelegate, StickersAlertDelegate {
	private val doneCheckDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.input_done, null)!!.mutate()
	private val captionLimitView = NumberTextView(context)
	private val senderSelectView = SenderSelectView(context)
	private val animationParamsX = HashMap<View, Float>()
	private var attachAndSettingsContainer: View
	val botWebViewButton = ChatActivityBotWebViewButton(context)

	private val mediaMessageButtonsDelegate: AccessibilityDelegate = object : AccessibilityDelegate() {
		override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(host, info)
			info.className = "android.widget.ImageButton"
			info.isClickable = true
			info.isLongClickable = true
		}
	}

	private val slowModeButton = SimpleTextView(context)

	private val sendButton by lazy {
		object : View(context) {
			private var drawableColor = 0
			private var animationProgress = 0f
			private var lastAnimationTime: Long = 0
			private var animationDuration = 0f
			private var prevColorType = 0

			override fun onDraw(canvas: Canvas) {
				val x = (measuredWidth - sendButtonDrawable.intrinsicWidth) / 2
				var y = (measuredHeight - sendButtonDrawable.intrinsicHeight) / 2

				if (isInScheduleMode) {
					y -= AndroidUtilities.dp(1f)
				}

				val color: Int
				var showingPopup: Boolean
				val colorType: Int

				if ((sendPopupWindow != null && sendPopupWindow!!.isShowing).also { showingPopup = it }) {
					color = context.getColor(R.color.white)
					colorType = 1
				}
				else {
					color = context.getColor(R.color.brand)
					colorType = 2
				}

				if (color != drawableColor) {
					lastAnimationTime = SystemClock.elapsedRealtime()

					if (prevColorType != 0 && prevColorType != colorType) {
						animationProgress = 0.0f

						animationDuration = if (showingPopup) {
							200.0f
						}
						else {
							120.0f
						}
					}
					else {
						animationProgress = 1.0f
					}

					prevColorType = colorType
					drawableColor = color
				}

				if (animationProgress < 1.0f) {
					val newTime = SystemClock.elapsedRealtime()
					val dt = newTime - lastAnimationTime

					animationProgress += dt / animationDuration

					if (animationProgress > 1.0f) {
						animationProgress = 1.0f
					}

					lastAnimationTime = newTime

					invalidate()
				}

				if (!showingPopup) {
					if (slowModeTimer == Int.MAX_VALUE && !isInScheduleMode) {
						inactiveSendButtonDrawable.setBounds(x, y, x + sendButtonDrawable.intrinsicWidth, y + sendButtonDrawable.intrinsicHeight)
						inactiveSendButtonDrawable.draw(canvas)
					}
					else {
						sendButtonDrawable.setBounds(x, y, x + sendButtonDrawable.intrinsicWidth, y + sendButtonDrawable.intrinsicHeight)
						sendButtonDrawable.draw(canvas)
					}
				}

				if (showingPopup || animationProgress != 1.0f) {
					Theme.dialogs_onlineCirclePaint.color = context.getColor(R.color.brand)

					var rad = AndroidUtilities.dp(20f)

					if (showingPopup) {
						sendButtonInverseDrawable.alpha = 255

						var p = animationProgress

						if (p <= 0.25f) {
							val progress = p / 0.25f
							rad += (AndroidUtilities.dp(2f) * CubicBezierInterpolator.EASE_IN.getInterpolation(progress)).toInt()
						}
						else {
							p -= 0.25f

							if (p <= 0.5f) {
								val progress = p / 0.5f
								rad += (AndroidUtilities.dp(2f) - AndroidUtilities.dp(3f) * CubicBezierInterpolator.EASE_IN.getInterpolation(progress)).toInt()
							}
							else {
								p -= 0.5f
								val progress = p / 0.25f
								rad += (-AndroidUtilities.dp(1f) + AndroidUtilities.dp(1f) * CubicBezierInterpolator.EASE_IN.getInterpolation(progress)).toInt()
							}
						}
					}
					else {
						val alpha = (255 * (1.0f - animationProgress)).toInt()
						Theme.dialogs_onlineCirclePaint.alpha = alpha
						sendButtonInverseDrawable.alpha = alpha
					}

					canvas.drawCircle((measuredWidth / 2).toFloat(), (measuredHeight / 2).toFloat(), rad.toFloat(), Theme.dialogs_onlineCirclePaint)

					sendButtonInverseDrawable.setBounds(x, y, x + sendButtonDrawable.intrinsicWidth, y + sendButtonDrawable.intrinsicHeight)
					sendButtonInverseDrawable.draw(canvas)
				}
			}

			override fun onTouchEvent(event: MotionEvent): Boolean {
				return if (alpha <= 0f) { // for accessibility
					false
				}
				else {
					super.onTouchEvent(event)
				}
			}
		}
	}

	private val sendButtonDrawable by lazy {
		if (isInScheduleMode) {
			ResourcesCompat.getDrawable(context.resources, R.drawable.input_schedule, null)!!.mutate()
		}
		else {
			ResourcesCompat.getDrawable(context.resources, if (isBot) R.drawable.send_ai_message else R.drawable.send_message, null)!!.mutate()
		}
	}

	private val inactiveSendButtonDrawable by lazy {
		if (isInScheduleMode) {
			ResourcesCompat.getDrawable(context.resources, R.drawable.input_schedule, null)!!.mutate()
		}
		else {
			ResourcesCompat.getDrawable(context.resources, if (isBot) R.drawable.send_ai_message else R.drawable.send_message, null)!!.mutate()
		}
	}

	private val sendButtonInverseDrawable by lazy {
		if (isInScheduleMode) {
			ResourcesCompat.getDrawable(context.resources, R.drawable.input_schedule, null)!!.mutate()
		}
		else {
			ResourcesCompat.getDrawable(context.resources, if (isBot) R.drawable.send_ai_message else R.drawable.send_message, null)!!.mutate()
		}
	}

	private val cancelBotButton = ImageView(context)

	private val expandStickersButton = object : AppCompatImageView(context) {
		override fun onTouchEvent(event: MotionEvent): Boolean {
			return if (alpha <= 0f) { // for accessibility
				false
			}
			else {
				super.onTouchEvent(event)
			}
		}
	}

	private val createMediaButton = ImageView(context)

	private val recordTimerView = TimerView(context)
	private val audioVideoButtonContainer = FrameLayout(context)
	private val audioVideoSendButton = ChatActivityEnterViewAnimatedIconView(context)
	private val recordPanel = FrameLayout(context)

	private val recordedAudioPanel = object : FrameLayout(context) {
		override fun setVisibility(visibility: Int) {
			super.setVisibility(visibility)
			updateSendAsButton()
		}
	}

	private val videoTimelineView = VideoTimelineView(context)
	private val recordDeleteImageView = ImageView(context) // RLottieImageView(context)
	private val recordedAudioSeekBar: SeekBarWaveformView
	private val recordedAudioBackground = View(context)
	private val recordedAudioPlayButton: ImageView
	private val recordedAudioTimeTextView = TextView(context)
	private val slideText = SlideTextView(context)
	private val recordTimeContainer = LinearLayout(context)

	private val recordDot by lazy {
		RecordDot(context)
	}

	val sizeNotifierLayout: SizeNotifierFrameLayout?
	private val textFieldContainer: FrameLayout

	private val sendButtonContainer = object : FrameLayout(context) {
		override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
			return if (child === sendButton && textTransitionIsRunning) {
				true
			}
			else {
				super.drawChild(canvas, child, drawingTime)
			}
		}
	}

	private val doneButtonContainer = FrameLayout(context)
	private val doneButtonImage = ImageView(context)
	private val doneButtonProgress: ContextProgressView
	private val progressDrawable: CloseProgressDrawable2
	private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val playPauseDrawable: MediaActionDrawable?
	private val sendByEnter: Boolean
	private val location = IntArray(2)
	private val parentActivity = context
	private val configAnimationsEnabled: Boolean

	private val roundedTranslationYProperty = object : Property<View, Int>(Int::class.java, "translationY") {
		override fun get(`object`: View): Int {
			return `object`.translationY.roundToInt()
		}

		override fun set(`object`: View, value: Int) {
			`object`.translationY = value.toFloat()
		}
	}

	private val recordCircleScale = object : Property<RecordCircle, Float>(Float::class.java, "scale") {
		override fun get(`object`: RecordCircle): Float {
			return `object`.getScale()
		}

		override fun set(`object`: RecordCircle, value: Float) {
			`object`.setScale(value)
		}
	}

	private val redDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

	private val stickersArrow by lazy {
		AnimatedArrowDrawable(context.getColor(R.color.dark_gray), false)
	}

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val pauseRect = RectF()
	private val sendRect = Rect()
	private val rect = Rect()

	private val emojiButton by lazy {
		object : AppCompatImageView(context) {
			override fun onDraw(canvas: Canvas) {
				super.onDraw(canvas)

				if (tag != null && attachLayout != null && !emojiViewVisible && MediaDataController.getInstance(currentAccount).getUnreadStickerSets().isNotEmpty()) {
					val x = width / 2 + AndroidUtilities.dp((4 + 5).toFloat())
					val y = height / 2 - AndroidUtilities.dp((13 - 5).toFloat())
					canvas.drawCircle(x.toFloat(), y.toFloat(), AndroidUtilities.dp(5f).toFloat(), dotPaint)
				}
			}
		}
	}

	private val topViewUpdateListener: AnimatorUpdateListener

	@JvmField
	var preventInput = false

	@JvmField
	var botCommandsMenuContainer: BotCommandsMenuContainer? = null

	@JvmField
	var currentTopViewAnimation: ValueAnimator? = null

	@JvmField
	var allowBlur = true

	val editField by lazy {
		object : EditTextCaption(context) {
			override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
				super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
				this@ChatActivityEnterView.delegate?.onEditTextScroll()
			}

			override fun onContextMenuOpen() {
				this@ChatActivityEnterView.delegate?.onContextMenuOpen()
			}

			override fun onContextMenuClose() {
				this@ChatActivityEnterView.delegate?.onContextMenuClose()
			}

			private fun send(inputContentInfo: InputContentInfoCompat, notify: Boolean, scheduleDate: Int) {
				val description = inputContentInfo.description

				if (description.hasMimeType("image/gif")) {
					SendMessagesHelper.prepareSendingDocument(accountInstance, null, null, inputContentInfo.contentUri, null, "image/gif", dialog_id, replyingMessageObject, threadMessage, inputContentInfo, null, notify, 0, false, null)
				}
				else {
					SendMessagesHelper.prepareSendingPhoto(accountInstance, null, inputContentInfo.contentUri, dialog_id, replyingMessageObject, threadMessage, null, null, null, inputContentInfo, 0, null, notify, 0, false, null)
				}

				this@ChatActivityEnterView.delegate?.onMessageSend(null, true, scheduleDate)
			}

			override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
				val ic = super.onCreateInputConnection(editorInfo) ?: return null

				try {
					EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf("image/gif", "image/*", "image/jpg", "image/png", "image/webp"))

					val callback = OnCommitContentListener { inputContentInfo, flags, _ ->
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
							try {
								inputContentInfo.requestPermission()
							}
							catch (e: Exception) {
								return@OnCommitContentListener false
							}
						}

						if (inputContentInfo.description.hasMimeType("image/gif") || SendMessagesHelper.shouldSendWebPAsSticker(null, inputContentInfo.contentUri)) {
							if (isInScheduleMode) {
								AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment!!.dialogId) { notify: Boolean, scheduleDate: Int ->
									send(inputContentInfo, notify, scheduleDate)
								}
							}
							else {
								send(inputContentInfo, true, 0)
							}
						}
						else {
							editPhoto(inputContentInfo.contentUri, inputContentInfo.description.getMimeType(0))
						}

						true
					}

					return InputConnectionCompat.createWrapper(ic, editorInfo, callback)
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}

				return ic
			}

			override fun onTouchEvent(event: MotionEvent): Boolean {
				if (stickersDragging || stickersExpansionAnim != null) {
					return false
				}

				if (isPopupShowing && event.action == MotionEvent.ACTION_DOWN) {
					if (searchingType != 0) {
						setSearchingTypeInternal(0, false)
						emojiView?.closeSearch(false)
						requestFocus()
					}

					showPopup(if (AndroidUtilities.usingHardwareInput) 0 else 2, 0)

					if (isStickersExpanded) {
						setStickersExpanded(expanded = false, animated = true, byDrag = false)
						waitingForKeyboardOpenAfterAnimation = true

						AndroidUtilities.runOnUIThread({
							waitingForKeyboardOpenAfterAnimation = false
							openKeyboardInternal()
						}, 200)
					}
					else {
						openKeyboardInternal()
					}

					return true
				}

				try {
					return super.onTouchEvent(event)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				return false
			}

			override fun dispatchKeyEvent(event: KeyEvent): Boolean {
				return if (preventInput) {
					false
				}
				else {
					super.dispatchKeyEvent(event)
				}
			}

			override fun onSelectionChanged(selStart: Int, selEnd: Int) {
				super.onSelectionChanged(selStart, selEnd)
				this@ChatActivityEnterView.delegate?.onTextSelectionChanged(selStart, selEnd)
			}

			override fun extendActionMode(actionMode: ActionMode, menu: Menu) {
				parentFragment?.extendActionMode(menu)
			}

			override fun requestRectangleOnScreen(rectangle: Rect): Boolean {
				rectangle.bottom += AndroidUtilities.dp(1000f)
				return super.requestRectangleOnScreen(rectangle)
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				isInitLineCount = measuredWidth == 0 && measuredHeight == 0

				super.onMeasure(widthMeasureSpec, heightMeasureSpec)

				if (isInitLineCount) {
					this@ChatActivityEnterView.lineCount = lineCount
				}

				isInitLineCount = false
			}

			override fun onTextContextMenuItem(id: Int): Boolean {
				if (id == android.R.id.paste) {
					isPaste = true
				}

				val clipboard = getContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
				val clipData = clipboard.primaryClip

				if (clipData != null) {
					if (clipData.itemCount == 1 && clipData.description.hasMimeType("image/*")) {
						editPhoto(clipData.getItemAt(0).uri, clipData.description.getMimeType(0))
					}
				}

				return super.onTextContextMenuItem(id)
			}

			private fun editPhoto(uri: Uri, mime: String) {
				val file = AndroidUtilities.generatePicturePath(fragment != null && fragment.isSecretChat, MimeTypeMap.getSingleton().getExtensionFromMimeType(mime))

				Utilities.globalQueue.postRunnable {
					try {
						context.contentResolver.openInputStream(uri)?.use { `in` ->
							FileOutputStream(file).use { fos ->
								val buffer = ByteArray(1024)
								var lengthRead: Int

								while (`in`.read(buffer).also { lengthRead = it } > 0) {
									fos.write(buffer, 0, lengthRead)
									fos.flush()
								}
							}
						}

						val photoEntry = PhotoEntry(0, -1, 0, file.absolutePath, 0, false, 0, 0, 0)
						val entries = ArrayList<Any>()

						entries.add(photoEntry)

						AndroidUtilities.runOnUIThread {
							openPhotoViewerForEdit(entries, file)
						}
					}
					catch (e: Throwable) {
						e.printStackTrace()
					}
				}
			}

			private fun openPhotoViewerForEdit(entries: ArrayList<Any>, sourceFile: File) {
				val photoEntry = entries[0] as PhotoEntry

				if (isKeyboardVisible) {
					AndroidUtilities.hideKeyboard(this) // TODO: check if `this` works properly

					AndroidUtilities.runOnUIThread({
						openPhotoViewerForEdit(entries, sourceFile)
					}, 100)

					return
				}

				PhotoViewer.getInstance().setParentActivity(parentFragment)

				PhotoViewer.getInstance().openPhotoForSelect(entries, 0, 2, false, object : EmptyPhotoViewerProvider() {
					var sending = false

					override fun sendButtonPressed(index: Int, videoEditedInfo: VideoEditedInfo?, notify: Boolean, scheduleDate: Int, forceDocument: Boolean) {
						val photos = ArrayList<SendingMediaInfo>()
						val info = SendingMediaInfo()

						if (!photoEntry.isVideo && photoEntry.imagePath != null) {
							info.path = photoEntry.imagePath
						}
						else if (photoEntry.path != null) {
							info.path = photoEntry.path
						}

						info.thumbPath = photoEntry.thumbPath
						info.isVideo = photoEntry.isVideo
						info.caption = photoEntry.caption?.toString()
						info.entities = photoEntry.entities
						info.masks = photoEntry.stickers
						info.ttl = photoEntry.ttl
						info.videoEditedInfo = videoEditedInfo
						info.canDeleteAfter = true

						photos.add(info)

						photoEntry.reset()

						sending = true

						val updateStickersOrder = SendMessagesHelper.checkUpdateStickersOrder(info.caption)

						SendMessagesHelper.prepareSendingMedia(accountInstance, photos, dialog_id, replyingMessageObject, threadMessage, null, forceDocument = false, groupMedia = false, editingMessageObject = editingMessageObject, notify = notify, scheduleDate = scheduleDate, updateStickersOrder = updateStickersOrder, false, null)

						this@ChatActivityEnterView.delegate?.onMessageSend(null, true, scheduleDate)
					}

					override fun willHidePhotoViewer() {
						if (!sending) {
							try {
								sourceFile.delete()
							}
							catch (e: Throwable) {
								// ignored
							}
						}
					}

					override fun canCaptureMorePhotos(): Boolean {
						return false
					}
				}, parentFragment, false)
			}
		}
	}

	@JvmField
	protected var topView: View? = null

	@JvmField
	protected var topLineView: View? = null

	@JvmField
	protected var topViewEnterProgress = 0f

	@JvmField
	var animatedTop = 0

	private var topViewShowed = false

	@JvmField
	protected var shouldAnimateEditTextWithBounds = false

	var messageTransitionIsRunning = false

	var textTransitionIsRunning = false
		set(value) {
			field = value
			sendButtonContainer.invalidate()
		}

	var doneButtonEnabled = true
	private var botCommandLastPosition = -1
	private var botCommandLastTop = 0
	private var currentAccount = UserConfig.selectedAccount
	private var accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount)
	private var seekBarWaveform: SeekBarWaveform? = null
	private var isInitLineCount = false
	private var lineCount = 1
	var adjustPanLayoutHelper: AdjustPanLayoutHelper? = null
	private var showTopViewRunnable: Runnable? = null
	private var setTextFieldRunnable: Runnable? = null
	private var currentLimit = -1
	private var codePointCount = 0
	private var notifySilentDrawable: CrossOutDrawable? = null
	private var moveToSendStateRunnable: Runnable? = null
	private var botMenuButtonType = BotMenuButtonType.NO_BUTTON
	private var botMenuWebViewTitle: String? = null
	private var botMenuWebViewUrl: String? = null
	private var botWebViewMenuContainer: BotWebViewMenuContainer? = null
	private var botCommandsMenuButton: BotCommandsMenuView? = null
	private var botCommandsAdapter: BotCommandsAdapter? = null
	private var senderSelectPopupWindow: SenderSelectPopup? = null
	private var onEmojiSearchClosed: Runnable? = null
	private var popupX = 0
	private var popupY = 0
	private var onKeyboardClosed: Runnable? = null
	private var searchAnimator: ValueAnimator? = null
	private var searchToOpenProgress = 0f
	private var chatSearchExpandOffset = 0f
	private var slowModeTimer = 0
	private var updateSlowModeRunnable: Runnable? = null
	private var sendPopupWindow: ActionBarPopupWindow? = null
	private var sendPopupLayout: ActionBarPopupWindowLayout? = null

	var emojiView: EmojiView? = null
		private set

	private var panelAnimation: AnimatorSet? = null

	private val runEmojiPanelAnimation = Runnable {
		panelAnimation?.let {
			if (!it.isRunning) {
				it.start()
			}
		}
	}

	private var emojiViewVisible = false
	private var botKeyboardViewVisible = false

	var isInVideoMode = false
		private set

	private var originalViewHeight = 0
	private var attachLayout: LinearLayout? = null
	var attachButton: ImageView? = null
	private var botSettingsButton: ImageView? = null
	private var botButton: ImageView? = null
	private var doneButtonAnimation: AnimatorSet? = null
	private var botKeyboardView: BotKeyboardView? = null
	private var notifyButton: ImageView? = null
	private var scheduledButton: ImageView? = null
	private var scheduleButtonHidden = false
	private var scheduledButtonAnimation: AnimatorSet? = null
	val recordCircle = RecordCircle(context)
	private var searchingType = 0
	private var focusRunnable: Runnable? = null
	private var botButtonDrawable: ReplaceableIconDrawable? = null
	private var draftMessage: CharSequence? = null
	private var draftSearchWebpage = false
	private var isPaste = false
	private var destroyed = false

	var editingMessageObject: MessageObject? = null
		private set

	var isEditingCaption = false
		private set

	private var info: TLRPC.ChatFull? = null
	private var hasRecordVideo = false
	private var currentPopupContentType = -1
	private var silent = false
	private var canWriteToChannel = false
	private var smoothKeyboard: Boolean
	private var isPaused = true
	private var recordIsCanceled = false
	private var showKeyboardOnResume = false
	private var botButtonsMessageObject: MessageObject? = null
	private var botReplyMarkup: TLRPC.TL_replyKeyboardMarkup? = null
	private var botCount = 0
	private var hasBotCommands = false
	private var wakeLock: PowerManager.WakeLock? = null
	private var runningAnimation: AnimatorSet? = null
	private var runningAnimation2: AnimatorSet? = null
	private var runningAnimationAudio: AnimatorSet? = null
	private var recordPanelAnimation: AnimatorSet? = null
	private var runningAnimationType = 0
	private var recordInterfaceState = 0
	private var keyboardHeight: Int
	private var keyboardHeightLand: Int

	var isKeyboardVisible = false
		private set

	var emojiPadding = 0
		private set

	private var lastTypingTimeSend: Long = 0
	private var startedDraggingX = -1f
	private var distCanMove = AndroidUtilities.dp(80f).toFloat()
	private var recordingAudioVideo = false
	private var recordingGuid = 0
	private var forceShowSendButton = false
	private var allowAnimatedEmoji = false
	private var allowStickers = false
	private var allowGifs = false
	private var lastSizeChangeValue1 = 0
	private var lastSizeChangeValue2 = false
	val parentFragment = fragment
	private var dialog_id: Long = 0
	private var ignoreTextChange = false
	private var innerTextChange = 0
	private var replyingMessageObject: MessageObject? = null
	private var botMessageObject: MessageObject? = null
	private var messageWebPage: TLRPC.WebPage? = null

	var isMessageWebPageSearchEnabled = true
		private set

	private var delegate: ChatActivityEnterViewDelegate? = null

	private val onFinishInitCameraRunnable = Runnable {
		delegate?.needStartRecordVideo(0, true, 0)
	}

	var trendingStickersAlert: TrendingStickersAlert? = null
		private set

	private var audioToSend: TLRPC.TL_document? = null
	private var audioToSendPath: String? = null
	private var audioToSendMessageObject: MessageObject? = null
	private var videoToSendMessageObject: VideoEditedInfo? = null
	private var needShowTopView = false
	private var allowShowTopView = false
	private var pendingMessageObject: MessageObject? = null
	private var pendingLocationButton: TLRPC.KeyboardButton? = null
	private var waitingForKeyboardOpen = false
	private var waitingForKeyboardOpenAfterAnimation = false
	private var wasSendTyping = false
	private var animatingContentType = -1
	private var clearBotButtonsOnKeyboardOpen = false
	private var doneButtonEnabledProgress = 1f
	private var doneButtonColorAnimator: ValueAnimator? = null
	private var stickersTabOpen = false
	private var emojiTabOpen = false

	var isStickersExpanded = false
		private set

	private var closeAnimationInProgress = false
	private var stickersExpansionAnim: Animator? = null
	private var stickersExpansionProgress = 0f
	private var stickersExpandedHeight = 0
	private var stickersDragging = false
	private var removeEmojiViewAfterAnimation = false
	private var recordAudioVideoRunnableStarted = false
	private var calledRecordRunnable = false

	private val recordAudioVideoRunnable: Runnable = object : Runnable {
		override fun run() {
			val delegate = delegate ?: return
			val parentActivity = parentActivity

			delegate.onPreAudioVideoRecord()

			calledRecordRunnable = true
			recordAudioVideoRunnableStarted = false

			slideText.alpha = 1.0f
			slideText.translationY = 0f

			if (isInVideoMode) {
				val hasAudio = parentActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
				val hasVideo = parentActivity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

				if (!hasAudio || !hasVideo) {
					val permissions = mutableListOf<String>()

					if (!hasAudio) {
						permissions.add(Manifest.permission.RECORD_AUDIO)
					}

					if (!hasVideo) {
						permissions.add(Manifest.permission.CAMERA)
					}

					parentActivity.requestPermissions(permissions.toTypedArray(), BasePermissionsActivity.REQUEST_CODE_VIDEO_MESSAGE)

					return
				}

				if (!CameraController.getInstance().isCameraInitied) {
					CameraController.getInstance().initCamera(onFinishInitCameraRunnable)
				}
				else {
					onFinishInitCameraRunnable.run()
				}

				if (!recordingAudioVideo) {
					recordingAudioVideo = true
					updateRecordInterface(RECORD_STATE_ENTER)
					recordCircle.showWaves(b = false, animated = false)
					recordTimerView.reset()
				}
			}
			else {
				if (parentFragment != null) {
					if (parentActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
						parentActivity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3)
						return
					}
				}

				delegate.needStartRecordAudio(1)

				startedDraggingX = -1f

				MediaController.getInstance().startRecording(currentAccount, dialog_id, replyingMessageObject, threadMessage, recordingGuid)

				recordingAudioVideo = true

				updateRecordInterface(RECORD_STATE_ENTER)

				recordTimerView.start()

				recordDot.enterAnimation = false

				audioVideoButtonContainer.parent?.requestDisallowInterceptTouchEvent(true)

				recordCircle.showWaves(b = true, animated = false)
			}
		}
	}

	private var notificationsIndex = 0

	private val updateExpandabilityRunnable: Runnable = object : Runnable {
		private var lastKnownPage = -1

		override fun run() {
			val emojiView = emojiView ?: return
			val curPage = emojiView.currentPage

			if (curPage != lastKnownPage) {
				lastKnownPage = curPage

				val prevOpen = stickersTabOpen

				stickersTabOpen = curPage == 1 || curPage == 2

				val prevOpen2 = emojiTabOpen

				emojiTabOpen = curPage == 0

				if (isStickersExpanded) {
					if (searchingType != 0) {
						setSearchingTypeInternal(if (curPage == 0) 2 else 1, true)
						checkStickersExpandHeight()
					}
					else if (!stickersTabOpen) {
						setStickersExpanded(expanded = false, animated = true, byDrag = false)
					}
				}

				if (prevOpen != stickersTabOpen || prevOpen2 != emojiTabOpen) {
					checkSendButton(true)
				}
			}
		}
	}

	private var micOutline: Drawable? = null
	private var cameraOutline: Drawable? = null

	private val openKeyboardRunnable: Runnable = object : Runnable {
		override fun run() {
			if (hasBotWebView() && botCommandsMenuIsShowing()) {
				return
			}

			if (!destroyed && waitingForKeyboardOpen && !isKeyboardVisible && !AndroidUtilities.usingHardwareInput && !AndroidUtilities.isInMultiwindow) {
				editField.requestFocus()
				AndroidUtilities.showKeyboard(editField)
				AndroidUtilities.cancelRunOnUIThread(this)
				AndroidUtilities.runOnUIThread(this, 100)
			}
		}
	}

	private var micDrawable: Drawable? = null
	private var cameraDrawable: Drawable? = null
	private var sendDrawable: Drawable? = null
	private var lockShadowDrawable: Drawable? = null
	private var composeShadowAlpha = 1f
	private var hideKeyboardRunnable: Runnable? = null

	init {
		setBackgroundResource(R.color.background)

		drawBlur = false
		smoothKeyboard = isChat && SharedConfig.smoothKeyboard && !AndroidUtilities.isInMultiwindow && (fragment == null || !fragment.isInBubbleMode)

		dotPaint.color = context.getColor(R.color.brand)

		isFocusable = true
		isFocusableInTouchMode = true

		setWillNotDraw(false)

		clipChildren = false

		NotificationCenter.getInstance(currentAccount).let {
			it.addObserver(this, NotificationCenter.recordStarted)
			it.addObserver(this, NotificationCenter.recordStartError)
			it.addObserver(this, NotificationCenter.recordStopped)
			it.addObserver(this, NotificationCenter.recordProgressChanged)
			it.addObserver(this, NotificationCenter.closeChats)
			it.addObserver(this, NotificationCenter.audioDidSent)
			it.addObserver(this, NotificationCenter.audioRouteChanged)
			it.addObserver(this, NotificationCenter.messagePlayingDidReset)
			it.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged)
			it.addObserver(this, NotificationCenter.featuredStickersDidLoad)
			it.addObserver(this, NotificationCenter.messageReceivedByServer)
			it.addObserver(this, NotificationCenter.sendingMessagesChanged)
			it.addObserver(this, NotificationCenter.audioRecordTooShort)
			it.addObserver(this, NotificationCenter.updateBotMenuButton)
			if (isBot) {
				it.addObserver(this, NotificationCenter.aiBotUpdate)
			}
		}

		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.emojiLoaded)

		if (fragment != null) {
			recordingGuid = fragment.classGuid
		}

		sizeNotifierLayout = parent
		sizeNotifierLayout?.setDelegate(this)

		val preferences = MessagesController.getGlobalMainSettings()

		sendByEnter = preferences.getBoolean("send_by_enter", false)
		configAnimationsEnabled = preferences.getBoolean("view_animations", true)

		textFieldContainer = object : FrameLayout(context) {
			override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
				return if (botWebViewButton.visibility == VISIBLE) {
					botWebViewButton.dispatchTouchEvent(ev)
				}
				else {
					super.dispatchTouchEvent(ev)
				}
			}
		}

		textFieldContainer.clipChildren = false
		textFieldContainer.clipToPadding = false

		addView(textFieldContainer, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.BOTTOM, 0f, 1f, 0f, 0f))

		topViewUpdateListener = AnimatorUpdateListener {
			val topView = topView ?: return@AnimatorUpdateListener
			val v = it.animatedValue as Float

			topViewEnterProgress = v

			topView.translationY = animatedTop + (1f - v) * ChatActivityEnterTopView.heightDp

			topLineView?.alpha = v
			topLineView?.translationY = animatedTop.toFloat()

			parentFragment?.mentionContainer?.translationY = (1f - v) * ChatActivityEnterTopView.heightDp
		}

		val frameLayout = object : FrameLayout(context) {
			override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
				super.onLayout(changed, left, top, right, bottom)

				scheduledButton?.let { scheduledButton ->
					val x = measuredWidth - AndroidUtilities.dp(if (botButton?.visibility == VISIBLE) 96f else 48f) - AndroidUtilities.dp(48f)
					scheduledButton.layout(x, scheduledButton.top, x + scheduledButton.measuredWidth, scheduledButton.bottom)
				}

				if (animationParamsX.isNotEmpty()) {
					for (i in 0 until childCount) {
						val child = getChildAt(i)
						val fromX = animationParamsX[child]

						if (fromX != null) {
							child.translationX = fromX - child.left
							child.animate().translationX(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start()
						}
					}

					animationParamsX.clear()
				}
			}

			override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
				if (child === editField) {
					canvas.save()
					canvas.clipRect(0, -top - textFieldContainer.top - this@ChatActivityEnterView.top, measuredWidth, measuredHeight - AndroidUtilities.dp(6f))
					val rez = super.drawChild(canvas, child, drawingTime)
					canvas.restore()
					return rez
				}

				return super.drawChild(canvas, child, drawingTime)
			}
		}

		frameLayout.clipChildren = false
		attachAndSettingsContainer = frameLayout
		textFieldContainer.addView(frameLayout, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM, 0f, 0f, 48f, 0f))

		captionLimitView.visibility = GONE
		captionLimitView.setTextSize(15)
		captionLimitView.setTextColor(context.getColor(R.color.dark_gray))
		captionLimitView.setTypeface(Theme.TYPEFACE_BOLD)
		captionLimitView.setCenterAlign(true)

		addView(captionLimitView, createFrame(48, 20f, Gravity.BOTTOM or Gravity.RIGHT, 3f, 0f, 0f, 48f))

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			editField.isFallbackLineSpacing = false
		}

		editField.delegate = EditTextCaption.EditTextCaptionDelegate {
			editField.invalidateEffects()
			delegate?.onTextSpansChanged(editField.text)
		}

		editField.includeFontPadding = false
		editField.setWindowView(parentActivity.window.decorView)

		val encryptedChat = parentFragment?.currentEncryptedChat

		editField.allowTextEntitiesIntersection = supportsSendingNewEntities()

		updateFieldHint(false)

		var flags = EditorInfo.IME_FLAG_NO_EXTRACT_UI

		if (encryptedChat != null) {
			flags = flags or 0x01000000 //EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
		}

		editField.imeOptions = flags
		editField.inputType = editField.inputType or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
		editField.isSingleLine = false
		editField.maxLines = 6
		editField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
		editField.setLineSpacing(0f, 1f)
		editField.gravity = Gravity.BOTTOM
		editField.setPadding(AndroidUtilities.dp(0f), AndroidUtilities.dp(11f), AndroidUtilities.dp(48f), AndroidUtilities.dp(12f))
		editField.setBackgroundResource(R.color.background)
		editField.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
		editField.setLinkTextColor(ResourcesCompat.getColor(resources, R.color.brand, null))
		editField.highlightColor = ResourcesCompat.getColor(resources, R.color.chat_input_hint, null)
		editField.setHintColor(ResourcesCompat.getColor(resources, R.color.disabled_text, null))
		editField.setHintTextColor(ResourcesCompat.getColor(resources, R.color.disabled_text, null))
		editField.setCursorColor(ResourcesCompat.getColor(resources, R.color.text, null))
		editField.setHandlesColor(ResourcesCompat.getColor(resources, R.color.brand, null))

		frameLayout.addView(editField, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM, 52f, 0f, if (isChat) messageViewRightMargin.toFloat() else 2f, 1.5f))

		editField.setOnKeyListener(object : OnKeyListener {
			var ctrlPressed = false

			override fun onKey(view: View, keyCode: Int, keyEvent: KeyEvent): Boolean {
				if (keyCode == KeyEvent.KEYCODE_BACK && !isKeyboardVisible && isPopupShowing && keyEvent.action == KeyEvent.ACTION_UP) {
					if (ContentPreviewViewer.hasInstance() && ContentPreviewViewer.getInstance().isVisible) {
						ContentPreviewViewer.getInstance().closeWithMenu()
						return true
					}

					if (currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD && botButtonsMessageObject != null) {
						return false
					}

					if (keyEvent.action == 1) {
						if (currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD && botButtonsMessageObject != null) {
							MessagesController.getMainSettings(currentAccount).edit().putInt("hidekeyboard_$dialog_id", botButtonsMessageObject!!.id).commit()
						}

						if (searchingType != 0) {
							setSearchingTypeInternal(0, true)
							emojiView?.closeSearch(true)
							editField.requestFocus()
						}
						else {
							if (isStickersExpanded) {
								setStickersExpanded(expanded = false, animated = true, byDrag = false)
							}
							else {
								if (stickersExpansionAnim == null) {
									if (botButtonsMessageObject != null && currentPopupContentType != POPUP_CONTENT_BOT_KEYBOARD && TextUtils.isEmpty(editField.text)) {
										showPopup(1, POPUP_CONTENT_BOT_KEYBOARD)
									}
									else {
										showPopup(0, 0)
									}
								}
							}
						}
					}

					return true
				}
				else if (keyCode == KeyEvent.KEYCODE_ENTER && (ctrlPressed || sendByEnter) && keyEvent.action == KeyEvent.ACTION_DOWN && editingMessageObject == null) {
					sendMessage()
					return true
				}
				else if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
					ctrlPressed = keyEvent.action == KeyEvent.ACTION_DOWN
					return true
				}

				return false
			}
		})

		editField.setOnEditorActionListener(object : OnEditorActionListener {
			val ctrlPressed = false

			override fun onEditorAction(textView: TextView, i: Int, keyEvent: KeyEvent?): Boolean {
				if (i == EditorInfo.IME_ACTION_SEND) {
					sendMessage()
					return true
				}
				else if (keyEvent != null && i == EditorInfo.IME_NULL) {
					if ((ctrlPressed || sendByEnter) && keyEvent.action == KeyEvent.ACTION_DOWN && editingMessageObject == null) {
						sendMessage()
						return true
					}
				}

				return false
			}
		})

		editField.addTextChangedListener(object : TextWatcher {
			private var processChange = false
			private var nextChangeIsSend = false
			private var prevText: CharSequence? = null
			private var ignorePrevTextChange = false

			override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
				if (ignorePrevTextChange) {
					return
				}

				if (recordingAudioVideo) {
					prevText = charSequence.toString()
				}
			}

			override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
				if (ignorePrevTextChange) {
					return
				}

				var allowChangeToSmile = true
				val currentPage = emojiView?.currentPage ?: MessagesController.getGlobalEmojiSettings().getInt("selected_page", 0)

				if (currentPage == 0 || !allowStickers && !allowGifs) {
					allowChangeToSmile = false
				}

				if ((before == 0 && !TextUtils.isEmpty(charSequence) || before != 0 && TextUtils.isEmpty(charSequence)) && allowChangeToSmile) {
					setEmojiButtonImage(animated = true)
				}

				if (lineCount != editField.lineCount) {
					if (!isInitLineCount && editField.measuredWidth > 0) {
						onLineCountChanged(lineCount, editField.lineCount)
					}

					lineCount = editField.lineCount
				}

				if (innerTextChange == 1) {
					return
				}

				if (sendByEnter && !isPaste && editingMessageObject == null && count > before && charSequence.isNotEmpty() && charSequence.length == start + count && charSequence[charSequence.length - 1] == '\n') {
					nextChangeIsSend = true
				}

				isPaste = false

				checkSendButton(true)

				val message = AndroidUtilities.getTrimmedString(charSequence.toString())

				if (delegate != null) {
					if (!ignoreTextChange) {
						if (before > count + 1 || count - before > 2 || TextUtils.isEmpty(charSequence)) {
							isMessageWebPageSearchEnabled = true
						}

						delegate?.onTextChanged(charSequence, before > count + 1 || count - before > 2)
					}
				}

				if (innerTextChange != 2 && count - before > 1) {
					processChange = true
				}

				if (editingMessageObject == null && !canWriteToChannel && message.isNotEmpty() && lastTypingTimeSend < System.currentTimeMillis() - 5000 && !ignoreTextChange) {
					lastTypingTimeSend = System.currentTimeMillis()
					delegate?.needSendTyping()
				}
			}

			override fun afterTextChanged(editable: Editable) {
				if (ignorePrevTextChange) {
					return
				}

				if (prevText != null) {
					ignorePrevTextChange = true
					editable.replace(0, editable.length, prevText)
					prevText = null
					ignorePrevTextChange = false
					return
				}

				if (innerTextChange == 0) {
					if (nextChangeIsSend) {
						sendMessage()
						nextChangeIsSend = false
					}

					if (processChange) {
						val spans = editable.getSpans(0, editable.length, ImageSpan::class.java)

						for (span in spans) {
							editable.removeSpan(span)
						}

						Emoji.replaceEmoji(editable, editField.paint.fontMetricsInt, false, null)

						processChange = false
					}
				}

				var beforeLimit = 0

				codePointCount = Character.codePointCount(editable, 0, editable.length)

				var doneButtonEnabledLocal = true

				if (currentLimit > 0 && currentLimit - codePointCount.also { beforeLimit = it } <= 100) {
					if (beforeLimit < -9999) {
						beforeLimit = -9999
					}

					captionLimitView.setNumber(beforeLimit, captionLimitView.visibility == VISIBLE)

					if (captionLimitView.visibility != VISIBLE) {
						captionLimitView.visibility = VISIBLE
						captionLimitView.alpha = 0f
						captionLimitView.scaleX = 0.5f
						captionLimitView.scaleY = 0.5f
					}

					captionLimitView.animate().setListener(null).cancel()
					captionLimitView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100).start()

					if (beforeLimit < 0) {
						doneButtonEnabledLocal = false
						captionLimitView.setTextColor(context.getColor(R.color.purple))
					}
					else {
						captionLimitView.setTextColor(context.getColor(R.color.dark_gray))
					}
				}
				else {
					captionLimitView.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(100).setListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							captionLimitView.visibility = GONE
						}
					})
				}

				if (doneButtonEnabled != doneButtonEnabledLocal) {
					doneButtonEnabled = doneButtonEnabledLocal

					doneButtonColorAnimator?.cancel()

					doneButtonColorAnimator = ValueAnimator.ofFloat(if (doneButtonEnabled) 0f else 1f, if (doneButtonEnabled) 1f else 0f)

					doneButtonColorAnimator?.addUpdateListener {
						val color = context.getColor(R.color.white)
						val defaultAlpha = Color.alpha(color)
						doneButtonEnabledProgress = it.animatedValue as Float
						doneCheckDrawable.colorFilter = PorterDuffColorFilter(ColorUtils.setAlphaComponent(color, (defaultAlpha * (0.58f + 0.42f * doneButtonEnabledProgress)).toInt()), PorterDuff.Mode.MULTIPLY)
						doneButtonImage.invalidate()
					}

					doneButtonColorAnimator?.setDuration(150)?.start()
				}

				botCommandsMenuContainer?.dismiss()

				checkBotMenu()
			}
		})

		var padding = AndroidUtilities.dp(9.5f)

		createMediaButton.setImageResource(R.drawable.money)
		createMediaButton.contentDescription = context.getString(R.string.create_media_sale)
		createMediaButton.isFocusable = true
		createMediaButton.colorFilter = PorterDuffColorFilter(context.getColor(R.color.disabled_text), PorterDuff.Mode.SRC_IN)
		createMediaButton.setPadding(padding, padding, padding, padding)
		createMediaButton.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))
		createMediaButton.visibility = if (canWriteToChannel) VISIBLE else GONE

		frameLayout.addView(createMediaButton, createFrame(48, 48f, Gravity.BOTTOM or Gravity.RIGHT, 0f, 0f, 96f, 0f))

		createMediaButton.setOnClickListener {
			val args = Bundle()
			args.putLong(CreateMediaSaleFragment.DIALOG_ID, dialog_id)
			parentFragment?.presentFragment(CreateMediaSaleFragment(args))
		}

		emojiButton.setImageResource(R.drawable.smile)
		emojiButton.contentDescription = context.getString(R.string.Emoji)
		emojiButton.isFocusable = true
		emojiButton.setPadding(padding, padding, padding, padding)
		emojiButton.colorFilter = PorterDuffColorFilter(context.getColor(R.color.disabled_text), PorterDuff.Mode.SRC_IN)
		emojiButton.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))

		emojiButton.setOnClickListener(OnClickListener {
			if (adjustPanLayoutHelper?.animationInProgress() == true) {
				return@OnClickListener
			}

			if (hasBotWebView() && botCommandsMenuIsShowing()) {
				botWebViewMenuContainer?.dismiss {
					it.callOnClick()
				}

				return@OnClickListener
			}

			if (!isPopupShowing || currentPopupContentType != 0) {
				showPopup(1, 0)
				emojiView?.onOpen(editField.length() > 0)
			}
			else {
				if (searchingType != 0) {
					setSearchingTypeInternal(0, true)
					emojiView?.closeSearch(false)
					editField.requestFocus()
				}

				if (isStickersExpanded) {
					setStickersExpanded(expanded = false, animated = true, byDrag = false)

					waitingForKeyboardOpenAfterAnimation = true

					AndroidUtilities.runOnUIThread({
						waitingForKeyboardOpenAfterAnimation = false
						openKeyboardInternal()
					}, 200)
				}
				else {
					openKeyboardInternal()
				}
			}
		})

		frameLayout.addView(emojiButton, createFrame(42, 42f, Gravity.BOTTOM or Gravity.LEFT, 0f, 0f, 0f, 4f))

		setEmojiButtonImage(animated = false)

		if (isChat) {
			if (parentFragment != null) {
				val drawable1 = ResourcesCompat.getDrawable(context.resources, R.drawable.input_calendar1, null)!!.mutate()
				drawable1.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.disabled_text, null), PorterDuff.Mode.SRC_IN)

				val drawable2 = ResourcesCompat.getDrawable(context.resources, R.drawable.input_calendar2, null)!!.mutate()
				drawable2.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.purple, null), PorterDuff.Mode.SRC_IN)

				scheduledButton = ImageView(context).also {
					it.setImageDrawable(CombinedDrawable(drawable1, drawable2))
					it.visibility = GONE
					it.contentDescription = context.getString(R.string.ScheduledMessages)
					it.scaleType = ImageView.ScaleType.CENTER
					it.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))

					frameLayout.addView(it, createFrame(48, 48, Gravity.BOTTOM or Gravity.RIGHT))

					it.setOnClickListener {
						delegate?.openScheduledMessages()
					}
				}
			}

			attachLayout = LinearLayout(context).also {
				it.orientation = LinearLayout.HORIZONTAL
				it.isEnabled = false
				it.pivotX = AndroidUtilities.dp(48f).toFloat()
				it.clipChildren = false

				frameLayout.addView(it, createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.BOTTOM or Gravity.RIGHT))
			}

			botCommandsMenuButton = BotCommandsMenuView(getContext())

			botCommandsMenuButton?.setOnClickListener {
				val open = botCommandsMenuButton?.isOpened()?.not() ?: false

				botCommandsMenuButton?.setOpened(open)

				try {
					performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
				}
				catch (e: Exception) {
					// ignored
				}

				if (hasBotWebView()) {
					if (open) {
						if (emojiViewVisible || botKeyboardViewVisible) {
							AndroidUtilities.runOnUIThread({ openWebViewMenu() }, 275)
							hidePopup(false)
							return@setOnClickListener
						}

						openWebViewMenu()
					}
					else {
						botWebViewMenuContainer?.dismiss()
					}

					return@setOnClickListener
				}

				if (open) {
					botCommandsMenuContainer?.show()
				}
				else {
					botCommandsMenuContainer?.dismiss()
				}
			}

			frameLayout.addView(botCommandsMenuButton, createFrame(LayoutHelper.WRAP_CONTENT, 32f, Gravity.BOTTOM or Gravity.LEFT, 10f, 8f, 10f, 8f))

			AndroidUtilities.updateViewVisibilityAnimated(botCommandsMenuButton, false, 1f, false)

			botCommandsMenuButton?.setExpanded(expanded = true, animated = false)

			val layoutManager = LinearLayoutManager(context)

			botCommandsMenuContainer = object : BotCommandsMenuContainer(context) {
				override fun onDismiss() {
					super.onDismiss()
					botCommandsMenuButton?.setOpened(false)
				}
			}

			botCommandsMenuContainer?.listView?.layoutManager = layoutManager

			botCommandsMenuContainer?.listView?.adapter = BotCommandsAdapter().also {
				botCommandsAdapter = it
			}

			botCommandsMenuContainer?.listView?.setOnItemClickListener { view, _ ->
				if (view is BotCommandView) {
					val command = view.command

					if (command.isNullOrEmpty()) {
						return@setOnItemClickListener
					}

					if (isInScheduleMode) {
						AlertsCreator.createScheduleDatePickerDialog(parentActivity, dialog_id) { notify: Boolean, scheduleDate: Int ->
							SendMessagesHelper.getInstance(currentAccount).sendMessage(command, dialog_id, replyingMessageObject, threadMessage, null, false, null, null, null, notify, scheduleDate, null, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)

							fieldText = ""

							botCommandsMenuContainer?.dismiss()
						}
					}
					else {
						if (fragment != null && fragment.checkSlowMode(view)) {
							return@setOnItemClickListener
						}

						SendMessagesHelper.getInstance(currentAccount).sendMessage(command, dialog_id, replyingMessageObject, threadMessage, null, false, null, null, null, true, 0, null, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)

						fieldText = ""

						botCommandsMenuContainer?.dismiss()
					}

					delegate?.onBotCommandSelected(command + view.description.text)
				}
			}

			botCommandsMenuContainer?.listView?.setOnItemLongClickListener { view, _ ->
				if (view is BotCommandView) {
					val command = view.commandTextView
					fieldText = "$command "
					botCommandsMenuContainer?.dismiss()
					return@setOnItemLongClickListener true
				}

				false
			}

			botCommandsMenuContainer?.clipToPadding = false

			sizeNotifierLayout?.addView(botCommandsMenuContainer, 14, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM))

			botCommandsMenuContainer?.visibility = GONE

			botWebViewMenuContainer = object : BotWebViewMenuContainer(context, this) {
				override fun onDismiss() {
					super.onDismiss()
					botCommandsMenuButton?.setOpened(false)
				}
			}

			sizeNotifierLayout?.addView(botWebViewMenuContainer, 15, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM))

			botWebViewMenuContainer?.visibility = GONE

			botWebViewMenuContainer?.setOnDismissGlobalListener {
				if (botButtonsMessageObject != null && TextUtils.isEmpty(editField.text) && botWebViewMenuContainer?.hasSavedText() == false) {
					showPopup(1, POPUP_CONTENT_BOT_KEYBOARD)
				}
			}

			botButton = ImageView(context)

			botButton?.setImageDrawable(ReplaceableIconDrawable(context).also {
				botButtonDrawable = it
			})

			botButtonDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.disabled_text, null), PorterDuff.Mode.SRC_IN)
			botButtonDrawable?.setIcon(R.drawable.input_bot2, false)

			botButton?.scaleType = ImageView.ScaleType.CENTER
			botButton?.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))
			botButton?.visibility = GONE

			AndroidUtilities.updateViewVisibilityAnimated(botButton, false, 0.1f, false)

			attachLayout?.addView(botButton, createLinear(48, 48))

			botButton?.setOnClickListener { v ->
				if (hasBotWebView() && botCommandsMenuIsShowing()) {
					botWebViewMenuContainer?.dismiss {
						v.callOnClick()
					}

					return@setOnClickListener
				}

				if (searchingType != 0) {
					setSearchingTypeInternal(0, false)
					emojiView?.closeSearch(false)
					editField.requestFocus()
				}

				if (botReplyMarkup != null) {
					if (!isPopupShowing || currentPopupContentType != POPUP_CONTENT_BOT_KEYBOARD) {
						showPopup(1, POPUP_CONTENT_BOT_KEYBOARD)
					}
				}
				else if (hasBotCommands) {
					fieldText = "/"
					editField.requestFocus()
					openKeyboard()
				}

				if (isStickersExpanded) {
					setStickersExpanded(expanded = false, animated = false, byDrag = false)
				}
			}

			notifyButton = ImageView(context)

			notifySilentDrawable = CrossOutDrawable(context, R.drawable.notify_channel, ResourcesCompat.getColor(resources, R.color.disabled_text, null))

			notifyButton?.setImageDrawable(notifySilentDrawable)

			notifySilentDrawable?.setCrossOut(silent, false)

			notifyButton?.contentDescription = if (silent) context.getString(R.string.AccDescrChanSilentOn) else context.getString(R.string.AccDescrChanSilentOff)
			notifyButton?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.hint, null), PorterDuff.Mode.SRC_IN)
			notifyButton?.scaleType = ImageView.ScaleType.CENTER
			notifyButton?.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))
			notifyButton?.visibility = if (canWriteToChannel && (delegate == null || !delegate!!.hasScheduledMessages())) VISIBLE else GONE

			frameLayout.addView(notifyButton!!, createFrame(48, 48f, Gravity.BOTTOM or Gravity.RIGHT, 0f, 0f, 48f, 0f))

			// attachLayout?.addView(notifyButton, createLinear(48, 48))

			notifyButton?.setOnClickListener {
				silent = !silent

				if (notifySilentDrawable == null) {
					notifySilentDrawable = CrossOutDrawable(context, R.drawable.notify_channel, ResourcesCompat.getColor(resources, R.color.hint, null))
				}

				notifySilentDrawable?.setCrossOut(silent, true)

				notifyButton?.setImageDrawable(notifySilentDrawable)

				MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("silent_$dialog_id", silent).commit()

				NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(dialog_id)

				fragment?.undoView?.showWithAction(0, if (!silent) UndoView.ACTION_NOTIFY_ON else UndoView.ACTION_NOTIFY_OFF, null)

				notifyButton?.contentDescription = if (silent) context.getString(R.string.AccDescrChanSilentOn) else context.getString(R.string.AccDescrChanSilentOff)

				updateFieldHint(true)
			}

			attachButton = ImageView(context)
			attachButton?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.disabled_text, null), PorterDuff.Mode.SRC_IN)
			attachButton?.setImageResource(R.drawable.paperclip)
			attachButton?.scaleType = ImageView.ScaleType.CENTER
			attachButton?.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))

			attachLayout?.addView(attachButton, createLinear(48, 48))

			attachButton?.setOnClickListener {
				if (adjustPanLayoutHelper?.animationInProgress() == true) {
					return@setOnClickListener
				}

				delegate?.didPressAttachButton()
			}

			attachButton?.contentDescription = context.getString(R.string.AccDescrAttachButton)

			if (isBot) {
				botSettingsButton = ImageView(context)
				botSettingsButton?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.disabled_text, null), PorterDuff.Mode.SRC_IN)
				botSettingsButton?.setImageResource(R.drawable.ic_settings_menu)
				botSettingsButton?.scaleType = ImageView.ScaleType.CENTER
				botSettingsButton?.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))

				botSettingsButton?.setOnClickListener {
					parentFragment?.presentFragment(AiSubscriptionPlansFragment())
				}

				attachLayout?.addView(botSettingsButton, createLinear(48, 48))

				forceShowSendButton = true
			}
		}

		senderSelectView.setOnClickListener { v ->
			if (translationY != 0f) {
				onEmojiSearchClosed = Runnable { senderSelectView.callOnClick() }
				hidePopup(byBackButton = true, forceAnimate = true)
				return@setOnClickListener
			}

			val delegate = delegate ?: return@setOnClickListener

			if (delegate.measureKeyboardHeight() > AndroidUtilities.dp(20f)) {
				var totalHeight = delegate.contentViewHeight
				val keyboard = delegate.measureKeyboardHeight()

				if (keyboard <= AndroidUtilities.dp(20f)) {
					totalHeight += keyboard
				}

				if (emojiViewVisible) {
					totalHeight -= emojiPadding
				}

				if (totalHeight < AndroidUtilities.dp(200f)) {
					onKeyboardClosed = Runnable { senderSelectView.callOnClick() }
					closeKeyboard()
					return@setOnClickListener
				}
			}

			if (delegate.sendAsPeers != null) {
				try {
					v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
				}
				catch (ignored: Exception) {
				}

				if (senderSelectPopupWindow != null) {
					senderSelectPopupWindow?.setPauseNotifications(false)
					senderSelectPopupWindow?.startDismissAnimation()
					return@setOnClickListener
				}

				val controller = MessagesController.getInstance(currentAccount)
				val chatFull = controller.getChatFull(-dialog_id) ?: return@setOnClickListener
				val fl = parentFragment?.parentLayout

				senderSelectPopupWindow = object : SenderSelectPopup(context, parentFragment, controller, chatFull, delegate.sendAsPeers, OnSelectCallback { recyclerView, senderView, peer ->
					if (senderSelectPopupWindow == null) {
						return@OnSelectCallback
					}

					chatFull.default_send_as = peer
					updateSendAsButton()

					parentFragment?.messagesController?.setDefaultSendAs(dialog_id, if (peer.user_id != 0L) peer.user_id else -peer.channel_id)

					val loc = IntArray(2)
					val wasSelected = senderView.avatar.isSelected

					senderView.avatar.getLocationInWindow(loc)
					senderView.avatar.setSelected(true, true)

					val avatar = SimpleAvatarView(getContext())

					if (peer.channel_id != 0L) {
						val chat = controller.getChat(peer.channel_id)

						if (chat != null) {
							avatar.setAvatar(chat)
						}
					}
					else if (peer.user_id != 0L) {
						val user = controller.getUser(peer.user_id)

						if (user != null) {
							avatar.setAvatar(user)
						}
					}

					recyclerView.children.forEach { ch ->
						if (ch is SenderView && ch !== senderView) {
							ch.avatar.setSelected(false, true)
						}
					}

					AndroidUtilities.runOnUIThread({
						if (senderSelectPopupWindow == null) {
							return@runOnUIThread
						}

						val d = Dialog(getContext(), R.style.TransparentDialogNoAnimation)

						val aFrame = FrameLayout(getContext())
						aFrame.addView(avatar, createFrame(AVATAR_SIZE_DP, AVATAR_SIZE_DP, Gravity.LEFT))

						d.setContentView(aFrame)

						d.window?.let {
							it.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
							it.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
							it.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
							it.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
							it.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
							it.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
							it.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
							it.attributes.windowAnimations = 0
							it.decorView.systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							it.statusBarColor = 0
							it.navigationBarColor = 0
						}

						val color = Theme.getColor(Theme.key_actionBarDefault, null, true)

						AndroidUtilities.setLightStatusBar(d.window, color == Color.WHITE)

						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
							val color2 = Theme.getColor(Theme.key_windowBackgroundGray, null, true)
							val brightness = AndroidUtilities.computePerceivedBrightness(color2)
							AndroidUtilities.setLightNavigationBar(d.window, brightness >= 0.721f)
						}

						val offX = 0f
						val offY = 0f
						val wi = rootWindowInsets

						popupX += wi.systemWindowInsetLeft
						senderSelectView.getLocationInWindow(location)

						val endX = location[0].toFloat()
						val endY = location[1].toFloat()
						val off = AndroidUtilities.dp(5f).toFloat()
						val startX = loc[0] + popupX + off + AndroidUtilities.dp(4f) + offX
						val startY = loc[1] + popupY + off + offY

						avatar.translationX = startX
						avatar.translationY = startY

						val startScale = (AVATAR_SIZE_DP - 10).toFloat() / AVATAR_SIZE_DP
						val endScale = senderSelectView.layoutParams.width / AndroidUtilities.dp(AVATAR_SIZE_DP.toFloat()).toFloat()

						avatar.pivotX = 0f
						avatar.pivotY = 0f
						avatar.scaleX = startScale
						avatar.scaleY = startScale

						avatar.viewTreeObserver.addOnDrawListener(object : OnDrawListener {
							override fun onDraw() {
								avatar.post {
									avatar.viewTreeObserver.removeOnDrawListener(this)
									senderView.avatar.setHideAvatar(true)
								}
							}
						})

						d.show()

						senderSelectView.scaleX = 1f
						senderSelectView.scaleY = 1f
						senderSelectView.alpha = 1f

						val translationStiffness = 700f

						senderSelectPopupWindow?.startDismissAnimation(SpringAnimation(senderSelectView, DynamicAnimation.SCALE_X).setSpring(SpringForce(0.5f).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)), SpringAnimation(senderSelectView, DynamicAnimation.SCALE_Y).setSpring(SpringForce(0.5f).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)), SpringAnimation(senderSelectView, DynamicAnimation.ALPHA).setSpring(SpringForce(0f).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)).addEndListener { _, _, _, _ ->
							if (d.isShowing) {
								avatar.translationX = endX
								avatar.translationY = endY

								senderSelectView.setProgress(0f, false)
								senderSelectView.scaleX = 1f
								senderSelectView.scaleY = 1f
								senderSelectView.alpha = 1f

								senderSelectView.doOnPreDraw {
									senderSelectView.postDelayed({ d.dismiss() }, 100)
								}
							}
						}, SpringAnimation(avatar, DynamicAnimation.TRANSLATION_X).setStartValue(MathUtils.clamp(startX, endX - AndroidUtilities.dp(6f), startX)).setSpring(SpringForce(endX).setStiffness(translationStiffness).setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)).setMinValue(endX - AndroidUtilities.dp(6f)), SpringAnimation(avatar, DynamicAnimation.TRANSLATION_Y).setStartValue(MathUtils.clamp(startY, startY, endY + AndroidUtilities.dp(6f))).setSpring(SpringForce(endY).setStiffness(translationStiffness).setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)).setMaxValue(endY + AndroidUtilities.dp(6f)).addUpdateListener(object : OnAnimationUpdateListener {
							var performedHapticFeedback = false

							override fun onAnimationUpdate(animation: DynamicAnimation<*>?, value: Float, velocity: Float) {
								if (!performedHapticFeedback && value >= endY) {
									performedHapticFeedback = true

									try {
										avatar.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
									}
									catch (e: Exception) {
										// ignored
									}
								}
							}
						}).addEndListener { _, _, _, _ ->
							if (d.isShowing) {
								avatar.translationX = endX
								avatar.translationY = endY

								senderSelectView.setProgress(0f, false)
								senderSelectView.scaleX = 1f
								senderSelectView.scaleY = 1f
								senderSelectView.alpha = 1f

								senderSelectView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
									override fun onPreDraw(): Boolean {
										senderSelectView.viewTreeObserver.removeOnPreDrawListener(this)
										senderSelectView.postDelayed({ d.dismiss() }, 100)
										return true
									}
								})
							}
						}, SpringAnimation(avatar, DynamicAnimation.SCALE_X).setSpring(SpringForce(endScale).setStiffness(1000f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)), SpringAnimation(avatar, DynamicAnimation.SCALE_Y).setSpring(SpringForce(endScale).setStiffness(1000f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)))
					}, if (wasSelected) 0 else SimpleAvatarView.SELECT_ANIMATION_DURATION.toLong())
				}) {
					override fun dismiss() {
						if (senderSelectPopupWindow !== this) {
							fl?.removeView(dimView)
							super.dismiss()
							return
						}

						senderSelectPopupWindow = null

						if (!runningCustomSprings) {
							startDismissAnimation()
							senderSelectView.setProgress(0f, true, true)
						}
						else {
							for (springAnimation in springAnimations) {
								springAnimation.cancel()
							}

							springAnimations.clear()

							super.dismiss()
						}
					}
				}

				senderSelectPopupWindow?.setPauseNotifications(true)
				senderSelectPopupWindow?.setDismissAnimationDuration(220)
				senderSelectPopupWindow?.isOutsideTouchable = true
				senderSelectPopupWindow?.isClippingEnabled = true
				senderSelectPopupWindow?.isFocusable = true
				senderSelectPopupWindow?.contentView?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST))
				senderSelectPopupWindow?.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
				senderSelectPopupWindow?.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
				senderSelectPopupWindow?.contentView?.isFocusableInTouchMode = true
				senderSelectPopupWindow?.setAnimationEnabled(false)

				val pad = -AndroidUtilities.dp(4f)
				val location = IntArray(2)
				var popupX = pad

				if (AndroidUtilities.isTablet()) {
					parentFragment?.fragmentView?.getLocationInWindow(location)
					popupX += location[0]
				}

				var totalHeight = delegate.contentViewHeight
				val height = senderSelectPopupWindow!!.contentView.measuredHeight
				val keyboard = delegate.measureKeyboardHeight()

				if (keyboard <= AndroidUtilities.dp(20f)) {
					totalHeight += keyboard
				}

				if (emojiViewVisible) {
					totalHeight -= emojiPadding
				}

				val shadowPad = AndroidUtilities.dp(1f)
				val popupY: Int

				if (height < totalHeight + pad * 2 - (if (parentFragment?.isInBubbleMode == true) 0 else AndroidUtilities.statusBarHeight) - senderSelectPopupWindow!!.headerText.measuredHeight) {
					getLocationInWindow(location)
					popupY = location[1] - height - pad - AndroidUtilities.dp(2f)
					fl?.addView(senderSelectPopupWindow!!.dimView, LayoutParams(LayoutHelper.MATCH_PARENT, popupY + pad + height + shadowPad + AndroidUtilities.dp(2f)))
				}
				else {
					popupY = if (parentFragment?.isInBubbleMode == true) 0 else AndroidUtilities.statusBarHeight
					val off = AndroidUtilities.dp(14f)
					senderSelectPopupWindow?.recyclerContainer?.layoutParams?.height = totalHeight - popupY - off - heightWithTopView
					fl?.addView(senderSelectPopupWindow!!.dimView, LayoutParams(LayoutHelper.MATCH_PARENT, off + popupY + senderSelectPopupWindow!!.recyclerContainer.layoutParams.height + shadowPad))
				}

				senderSelectPopupWindow?.startShowAnimation()
				senderSelectPopupWindow?.showAtLocation(v, Gravity.LEFT or Gravity.TOP, popupX.also { this.popupX = it }, popupY.also { this.popupY = it })

				senderSelectView.progress = 1f
			}
		}

		senderSelectView.visibility = GONE

		frameLayout.addView(senderSelectView, createFrame(32, 32f, Gravity.BOTTOM or Gravity.LEFT, 10f, 8f, 10f, 8f))

		recordedAudioPanel.visibility = if (audioToSend == null) GONE else VISIBLE
		recordedAudioPanel.isFocusable = true
		recordedAudioPanel.isFocusableInTouchMode = true
		recordedAudioPanel.isClickable = true

		frameLayout.addView(recordedAudioPanel, createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM))

		recordDeleteImageView.scaleType = ImageView.ScaleType.CENTER
		recordDeleteImageView.setImageResource(R.drawable.trash)
		// recordDeleteImageView.setAnimation(R.raw.chat_audio_record_delete_2, 28, 28)
		// recordDeleteImageView.animatedDrawable.setInvalidateOnProgressSet(true)

		// updateRecordedDeleteIconColors()

		recordDeleteImageView.contentDescription = context.getString(R.string.Delete)
		recordDeleteImageView.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))

		recordedAudioPanel.addView(recordDeleteImageView, createFrame(48, 48, Gravity.CENTER_VERTICAL))

		recordDeleteImageView.setOnClickListener {
			if (runningAnimationAudio?.isRunning == true) {
				return@setOnClickListener
			}

			if (videoToSendMessageObject != null) {
				CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable)
				delegate?.needStartRecordVideo(2, true, 0)
			}
			else {
				val playing = MediaController.getInstance().playingMessageObject

				if (playing != null && playing === audioToSendMessageObject) {
					MediaController.getInstance().cleanupPlayer(true, true)
				}
			}

			audioToSendPath?.let {
				File(it).delete()
			}

			hideRecordedAudioPanel(false)

			checkSendButton(true)
		}

		videoTimelineView.setColor(context.getColor(R.color.brand))
		videoTimelineView.setRoundFrames(true)

		videoTimelineView.setDelegate(object : VideoTimelineView.VideoTimelineViewDelegate {
			override fun onLeftProgressChanged(progress: Float) {
				val videoToSendMessageObject = videoToSendMessageObject ?: return
				videoToSendMessageObject.startTime = (progress * videoToSendMessageObject.estimatedDuration).toLong()
				delegate?.needChangeVideoPreviewState(2, progress)
			}

			override fun onRightProgressChanged(progress: Float) {
				val videoToSendMessageObject = videoToSendMessageObject ?: return
				videoToSendMessageObject.endTime = (progress * videoToSendMessageObject.estimatedDuration).toLong()
				delegate?.needChangeVideoPreviewState(2, progress)
			}

			override fun didStartDragging() {
				delegate?.needChangeVideoPreviewState(1, 0f)
			}

			override fun didStopDragging() {
				delegate?.needChangeVideoPreviewState(0, 0f)
			}
		})

		recordedAudioPanel.addView(videoTimelineView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.CENTER_VERTICAL or Gravity.LEFT, 56f, 0f, 8f, 0f))

		val videoTimeHintView = TimeHintView(context)

		videoTimelineView.setTimeHintView(videoTimeHintView)

		sizeNotifierLayout?.addView(videoTimeHintView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM, 0f, 0f, 0f, 52f))

		recordedAudioBackground.background = Theme.createRoundRectDrawable(AndroidUtilities.dp(22f), ResourcesCompat.getColor(resources, R.color.green, null))

		recordedAudioPanel.addView(recordedAudioBackground, createFrame(LayoutHelper.MATCH_PARENT, 44f, Gravity.CENTER_VERTICAL or Gravity.LEFT, 48f, 0f, 0f, 0f))

		recordedAudioSeekBar = SeekBarWaveformView(context)

		val waveFormTimerLayout = LinearLayout(context)
		waveFormTimerLayout.orientation = LinearLayout.HORIZONTAL

		recordedAudioPanel.addView(waveFormTimerLayout, createFrame(LayoutHelper.MATCH_PARENT, 38f, Gravity.CENTER_VERTICAL or Gravity.LEFT, (48 + 44).toFloat(), 0f, 13f, 0f))

		playPauseDrawable = MediaActionDrawable()
		recordedAudioPlayButton = ImageView(context)

		val matrix = Matrix()
		matrix.postScale(0.8f, 0.8f, AndroidUtilities.dpf2(24f), AndroidUtilities.dpf2(24f))

		recordedAudioPlayButton.imageMatrix = matrix
		recordedAudioPlayButton.setImageDrawable(playPauseDrawable)
		recordedAudioPlayButton.scaleType = ImageView.ScaleType.MATRIX
		recordedAudioPlayButton.contentDescription = context.getString(R.string.AccActionPlay)

		recordedAudioPanel.addView(recordedAudioPlayButton, createFrame(48, 48f, Gravity.LEFT or Gravity.BOTTOM, 48f, 0f, 13f, 0f))

		recordedAudioPlayButton.setOnClickListener {
			if (audioToSend == null) {
				return@setOnClickListener
			}

			if (MediaController.getInstance().isPlayingMessage(audioToSendMessageObject) && !MediaController.getInstance().isMessagePaused) {
				MediaController.getInstance().pauseMessage(audioToSendMessageObject)
				playPauseDrawable.setIcon(MediaActionDrawable.ICON_PLAY, true)
				recordedAudioPlayButton.contentDescription = context.getString(R.string.AccActionPlay)
			}
			else {
				playPauseDrawable.setIcon(MediaActionDrawable.ICON_PAUSE, true)
				MediaController.getInstance().playMessage(audioToSendMessageObject)
				recordedAudioPlayButton.contentDescription = context.getString(R.string.AccActionPause)
			}
		}

		recordedAudioTimeTextView.setTextColor(context.getColor(R.color.white))
		recordedAudioTimeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)

		waveFormTimerLayout.addView(recordedAudioSeekBar, createLinear(0, 32, 1f, Gravity.CENTER_VERTICAL, 0, 0, 4, 0))
		waveFormTimerLayout.addView(recordedAudioTimeTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL))

		recordPanel.clipChildren = false
		recordPanel.visibility = GONE

		frameLayout.addView(recordPanel, createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL))

		recordPanel.setOnTouchListener { _, _ -> true }

		recordPanel.addView(slideText, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.NO_GRAVITY, 45f, 0f, 0f, 0f))

		recordTimeContainer.orientation = LinearLayout.HORIZONTAL
		recordTimeContainer.setPadding(AndroidUtilities.dp(13f), 0, 0, 0)
		recordTimeContainer.isFocusable = false

		recordPanel.addView(recordTimeContainer, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL))

		recordTimeContainer.addView(recordDot, createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 0, 0))
		recordTimeContainer.addView(recordTimerView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 6, 0, 0, 0))

		sendButtonContainer.clipChildren = false
		sendButtonContainer.clipToPadding = false

		textFieldContainer.addView(sendButtonContainer, createFrame(48, 48, Gravity.BOTTOM or Gravity.RIGHT))

		audioVideoButtonContainer.isSoundEffectsEnabled = false
		sendButtonContainer.addView(audioVideoButtonContainer, createFrame(48, 48f))

		audioVideoButtonContainer.isFocusable = true
		audioVideoButtonContainer.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

		audioVideoButtonContainer.setOnTouchListener { view, motionEvent ->
			if (motionEvent.action == MotionEvent.ACTION_DOWN) {
				if (recordCircle.isSendButtonVisible) {
					if (!hasRecordVideo || calledRecordRunnable) {
						startedDraggingX = -1f

						if (hasRecordVideo && isInVideoMode) {
							delegate?.needStartRecordVideo(1, true, 0)
						}
						else {
							if (recordingAudioVideo && isInScheduleMode) {
								AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment!!.dialogId, { notify, scheduleDate ->
									MediaController.getInstance().stopRecording(1, notify, scheduleDate)
								}, {
									MediaController.getInstance().stopRecording(0, false, 0)
								}, null)
							}

							MediaController.getInstance().stopRecording(if (isInScheduleMode) 3 else 1, true, 0)

							delegate?.needStartRecordAudio(0)
						}

						recordingAudioVideo = false
						messageTransitionIsRunning = false

						AndroidUtilities.runOnUIThread(Runnable {
							moveToSendStateRunnable = null
							updateRecordInterface(RECORD_STATE_SENDING)
						}.also {
							moveToSendStateRunnable = it
						}, 200)
					}

					return@setOnTouchListener false
				}

				if (parentFragment != null) {
					val chat = parentFragment.currentChat
					val userFull = parentFragment.currentUserInfo

					if (chat != null && !ChatObject.canSendMedia(chat) || userFull != null && userFull.voice_messages_forbidden) {
						delegate?.needShowMediaBanHint()
						return@setOnTouchListener true
					}
				}

				if (hasRecordVideo) {
					calledRecordRunnable = false
					recordAudioVideoRunnableStarted = true
					AndroidUtilities.runOnUIThread(recordAudioVideoRunnable, 150)
				}
				else {
					recordAudioVideoRunnable.run()
				}

				return@setOnTouchListener true
			}
			else if (motionEvent.action == MotionEvent.ACTION_UP || motionEvent.action == MotionEvent.ACTION_CANCEL) {
				if (motionEvent.action == MotionEvent.ACTION_CANCEL && recordingAudioVideo) {
					if (recordCircle.slideToCancelProgress < 0.7f) {
						if (hasRecordVideo && isInVideoMode) {
							CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable)
							delegate?.needStartRecordVideo(2, true, 0)
						}
						else {
							delegate?.needStartRecordAudio(0)
							MediaController.getInstance().stopRecording(0, false, 0)
						}

						recordingAudioVideo = false
						updateRecordInterface(RECORD_STATE_CANCEL_BY_GESTURE)
					}
					else {
						recordCircle.isSendButtonVisible = true
						startLockTransition()
					}

					return@setOnTouchListener false
				}

				if (recordCircle.isSendButtonVisible || recordedAudioPanel.visibility == VISIBLE) {
					if (recordAudioVideoRunnableStarted) {
						AndroidUtilities.cancelRunOnUIThread(recordAudioVideoRunnable)
					}

					return@setOnTouchListener false
				}

				val x = motionEvent.x + audioVideoButtonContainer.x
				val dist = x - startedDraggingX
				val alpha = 1.0f + dist / distCanMove

				if (alpha < 0.45) {
					if (hasRecordVideo && isInVideoMode) {
						CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable)
						delegate?.needStartRecordVideo(2, true, 0)
					}
					else {
						delegate?.needStartRecordAudio(0)
						MediaController.getInstance().stopRecording(0, false, 0)
					}

					recordingAudioVideo = false

					updateRecordInterface(RECORD_STATE_CANCEL_BY_GESTURE)
				}
				else {
					if (recordAudioVideoRunnableStarted) {
						AndroidUtilities.cancelRunOnUIThread(recordAudioVideoRunnable)
						delegate?.onSwitchRecordMode(!isInVideoMode)
						setRecordVideoButtonVisible(!isInVideoMode, true)
						performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
						sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
					}
					else if (!hasRecordVideo || calledRecordRunnable) {
						startedDraggingX = -1f

						if (hasRecordVideo && isInVideoMode) {
							CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable)
							delegate?.needStartRecordVideo(1, true, 0)
						}
						else {
							if (recordingAudioVideo && isInScheduleMode) {
								AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment!!.dialogId, { notify, scheduleDate ->
									MediaController.getInstance().stopRecording(1, notify, scheduleDate)
								}, {
									MediaController.getInstance().stopRecording(0, false, 0)
								}, null)
							}

							delegate?.needStartRecordAudio(0)
							MediaController.getInstance().stopRecording(if (isInScheduleMode) 3 else 1, true, 0)
						}

						recordingAudioVideo = false
						messageTransitionIsRunning = false

						AndroidUtilities.runOnUIThread(Runnable {
							moveToSendStateRunnable = null
							updateRecordInterface(RECORD_STATE_SENDING)
						}.also {
							moveToSendStateRunnable = it
						}, 500)
					}
				}

				return@setOnTouchListener true
			}
			else if (motionEvent.action == MotionEvent.ACTION_MOVE && recordingAudioVideo) {
				var x = motionEvent.x
				val y = motionEvent.y

				if (recordCircle.isSendButtonVisible) {
					return@setOnTouchListener false
				}

				if (recordCircle.setLockTranslation(y) == 2) {
					startLockTransition()
					return@setOnTouchListener false
				}
				else {
					recordCircle.setMovingCords(x, y)
				}

				if (startedDraggingX == -1f) {
					startedDraggingX = x
					distCanMove = (sizeNotifierLayout!!.measuredWidth * 0.35).toFloat()

					if (distCanMove > AndroidUtilities.dp(140f)) {
						distCanMove = AndroidUtilities.dp(140f).toFloat()
					}
				}

				x += audioVideoButtonContainer.x

				val dist = x - startedDraggingX
				var alpha = 1.0f + dist / distCanMove

				if (startedDraggingX != -1f) {
					if (alpha > 1) {
						alpha = 1f
					}
					else if (alpha < 0) {
						alpha = 0f
					}

					slideText.setSlideX(alpha)
					recordCircle.slideToCancelProgress = alpha
				}

				if (alpha == 0f) {
					if (hasRecordVideo && isInVideoMode) {
						CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable)
						delegate?.needStartRecordVideo(2, true, 0)
					}
					else {
						delegate?.needStartRecordAudio(0)
						MediaController.getInstance().stopRecording(0, false, 0)
					}

					recordingAudioVideo = false

					updateRecordInterface(RECORD_STATE_CANCEL_BY_GESTURE)
				}

				return@setOnTouchListener true
			}

			view.onTouchEvent(motionEvent)

			true
		}

		audioVideoSendButton.isFocusable = true
		audioVideoSendButton.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
		audioVideoSendButton.accessibilityDelegate = mediaMessageButtonsDelegate

		padding = AndroidUtilities.dp(9.5f)

		audioVideoSendButton.setPadding(padding, padding, padding, padding)
		audioVideoSendButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.disabled_text, null), PorterDuff.Mode.SRC_IN)

		audioVideoButtonContainer.addView(audioVideoSendButton, createFrame(48, 48f))


		recordCircle.visibility = GONE

		sizeNotifierLayout?.addView(recordCircle, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM, 0f, 0f, 0f, 10f))

		cancelBotButton.visibility = INVISIBLE
		cancelBotButton.scaleType = ImageView.ScaleType.CENTER_INSIDE

		cancelBotButton.setImageDrawable(object : CloseProgressDrawable2() {
			override fun getCurrentColor(): Int {
				return Theme.getColor(Theme.key_chat_messagePanelCancelInlineBot)
			}
		}.also {
			progressDrawable = it
		})

		cancelBotButton.contentDescription = context.getString(R.string.Cancel)
		cancelBotButton.isSoundEffectsEnabled = false
		cancelBotButton.scaleX = 0.1f
		cancelBotButton.scaleY = 0.1f
		cancelBotButton.alpha = 0.0f
		cancelBotButton.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))

		sendButtonContainer.addView(cancelBotButton, createFrame(48, 48f))

		cancelBotButton.setOnClickListener {
			val text = editField.text.toString()
			val idx = text.indexOf(' ')

			fieldText = if (idx == -1 || idx == text.length - 1) {
				""
			}
			else {
				text.substring(0, idx + 1)
			}
		}

		sendButton.visibility = INVISIBLE
		sendButton.contentDescription = context.getString(R.string.Send)
		sendButton.isSoundEffectsEnabled = false
		sendButton.scaleX = 0.1f
		sendButton.scaleY = 0.1f
		sendButton.alpha = 0.0f

		val color = ResourcesCompat.getColor(resources, R.color.brand, null)

		sendButton.foreground = Theme.createSelectorDrawable(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)), 1)

		sendButtonContainer.addView(sendButton, createFrame(48, 48f))

		sendButton.setOnClickListener {
			if (sendPopupWindow?.isShowing == true || runningAnimationAudio?.isRunning == true || moveToSendStateRunnable != null) {
				return@setOnClickListener
			}

			sendMessage()
		}

		sendButton.setOnLongClickListener {
			onSendLongClick(it)
		}

		slowModeButton.setTextSize(18)
		slowModeButton.visibility = INVISIBLE
		slowModeButton.isSoundEffectsEnabled = false
		slowModeButton.scaleX = 0.1f
		slowModeButton.scaleY = 0.1f
		slowModeButton.alpha = 0.0f
		slowModeButton.setPadding(0, 0, AndroidUtilities.dp(13f), 0)
		slowModeButton.setGravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
		slowModeButton.textColor = context.getColor(R.color.dark_gray)

		sendButtonContainer.addView(slowModeButton, createFrame(64, 48, Gravity.RIGHT or Gravity.TOP))

		slowModeButton.setOnClickListener {
			delegate?.onUpdateSlowModeButton(slowModeButton, true, slowModeButton.getText())
		}

		slowModeButton.setOnLongClickListener {
			if (editField.length() == 0) {
				return@setOnLongClickListener false
			}

			onSendLongClick(it)
		}

		expandStickersButton.scaleType = ImageView.ScaleType.CENTER
		expandStickersButton.setImageDrawable(stickersArrow)
		expandStickersButton.visibility = GONE
		expandStickersButton.scaleX = 0.1f
		expandStickersButton.scaleY = 0.1f
		expandStickersButton.alpha = 0.0f
		expandStickersButton.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))

		sendButtonContainer.addView(expandStickersButton, createFrame(48, 48f))

		expandStickersButton.setOnClickListener {
			if (expandStickersButton.visibility != VISIBLE || expandStickersButton.alpha != 1.0f || waitingForKeyboardOpen || isKeyboardVisible && editField.isFocused) {
				return@setOnClickListener
			}

			if (isStickersExpanded) {
				if (searchingType != 0) {
					setSearchingTypeInternal(0, true)

					emojiView?.closeSearch(true)
					emojiView?.hideSearchKeyboard()

					if (emojiTabOpen) {
						checkSendButton(true)
					}
				}
				else if (!stickersDragging) {
					emojiView?.showSearchField(false)
				}
			}
			else if (!stickersDragging) {
				emojiView?.showSearchField(true)
			}

			if (!stickersDragging) {
				setStickersExpanded(!isStickersExpanded, animated = true, byDrag = false)
			}
		}

		expandStickersButton.contentDescription = context.getString(R.string.AccDescrExpandPanel)

		doneButtonContainer.visibility = GONE

		textFieldContainer.addView(doneButtonContainer, createFrame(48, 48, Gravity.BOTTOM or Gravity.RIGHT))

		doneButtonContainer.setOnClickListener {
			doneEditingMessage()
		}

		val doneCircleDrawable: Drawable = Theme.createCircleDrawable(AndroidUtilities.dp(16f), ResourcesCompat.getColor(context.resources, R.color.brand, null))

		doneCheckDrawable.colorFilter = PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_IN)

		val combinedDrawable = CombinedDrawable(doneCircleDrawable, doneCheckDrawable, 0, AndroidUtilities.dp(1f))
		combinedDrawable.setCustomSize(AndroidUtilities.dp(32f), AndroidUtilities.dp(32f))

		doneButtonImage.scaleType = ImageView.ScaleType.CENTER
		doneButtonImage.setImageDrawable(combinedDrawable)
		doneButtonImage.contentDescription = context.getString(R.string.Done)

		doneButtonContainer.addView(doneButtonImage, createFrame(48, 48f))

		doneButtonProgress = ContextProgressView(context, 0)
		doneButtonProgress.visibility = INVISIBLE

		doneButtonContainer.addView(doneButtonProgress, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		val sharedPreferences = MessagesController.getGlobalEmojiSettings()

		keyboardHeight = sharedPreferences.getInt("kbd_height", AndroidUtilities.dp(200f))
		keyboardHeightLand = sharedPreferences.getInt("kbd_height_land3", AndroidUtilities.dp(200f))

		setRecordVideoButtonVisible(visible = false, animated = false)
		checkSendButton(false)
		checkChannelRights()

		botWebViewButton.visibility = GONE
		botWebViewButton.setBotMenuButton(botCommandsMenuButton)

		frameLayout.addView(botWebViewButton, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM))
	}

	private fun openWebViewMenu() {
		val onRequestWebView = Runnable {
			AndroidUtilities.hideKeyboard(this)

			if (AndroidUtilities.isTablet()) {
				val webViewSheet = BotWebViewSheet(context, parentFragment!!.resourceProvider)
				webViewSheet.setParentActivity(parentActivity)
				webViewSheet.requestWebView(currentAccount, dialog_id, dialog_id, botMenuWebViewTitle, botMenuWebViewUrl, BotWebViewSheet.TYPE_BOT_MENU_BUTTON, 0, false)
				webViewSheet.show()

				botCommandsMenuButton?.setOpened(false)
			}
			else {
				botWebViewMenuContainer?.show(currentAccount, dialog_id, botMenuWebViewUrl)
			}
		}

		if (SharedPrefsHelper.isWebViewConfirmShown(currentAccount, dialog_id)) {
			onRequestWebView.run()
		}
		else {
			val context = parentFragment?.parentActivity ?: return

			AlertDialog.Builder(context).setTitle(context.getString(R.string.BotOpenPageTitle)).setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotOpenPageMessage, getUserName(MessagesController.getInstance(currentAccount).getUser(dialog_id))))).setPositiveButton(context.getString(R.string.OK)) { _, _ ->
				onRequestWebView.run()
				SharedPrefsHelper.setWebViewConfirmShown(currentAccount, dialog_id, true)
			}.setNegativeButton(context.getString(R.string.Cancel), null).setOnDismissListener {
				if (!SharedPrefsHelper.isWebViewConfirmShown(currentAccount, dialog_id)) {
					botCommandsMenuButton?.setOpened(false)
				}
			}.show()
		}
	}

	fun setBotWebViewButtonOffsetX(offset: Float) {
		emojiButton.translationX = offset
		editField.translationX = offset
		attachButton?.translationX = offset
		audioVideoSendButton.translationX = offset
		botButton?.translationX = offset
	}

	fun setComposeShadowAlpha(alpha: Float) {
		composeShadowAlpha = alpha
		invalidate()
	}

	private fun checkBotMenu() {
		val botCommandsMenuButton = botCommandsMenuButton ?: return
		val wasExpanded = botCommandsMenuButton.expanded

		botCommandsMenuButton.setExpanded(TextUtils.isEmpty(editField.text) && !(isKeyboardVisible || waitingForKeyboardOpen || isPopupShowing), true)

		if (wasExpanded != botCommandsMenuButton.expanded) {
			beginDelayedTransition()
		}
	}

	fun forceSmoothKeyboard(smoothKeyboard: Boolean) {
		this.smoothKeyboard = smoothKeyboard && SharedConfig.smoothKeyboard && !AndroidUtilities.isInMultiwindow && (parentFragment == null || !parentFragment.isInBubbleMode)
	}

	protected open fun onLineCountChanged(oldLineCount: Int, newLineCount: Int) {
		// stub
	}

	private fun startLockTransition() {
		val animatorSet = AnimatorSet()

		performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

		val translate = ObjectAnimator.ofFloat(recordCircle, "lockAnimatedTranslation", recordCircle.startTranslation)
		translate.startDelay = 100
		translate.duration = 350

		val snap = ObjectAnimator.ofFloat(recordCircle, "snapAnimationProgress", 1f)
		snap.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
		snap.duration = 250

		SharedConfig.removeLockRecordAudioVideoHint()

		animatorSet.playTogether(snap, translate, ObjectAnimator.ofFloat(recordCircle, "slideToCancelProgress", 1f).setDuration(200), ObjectAnimator.ofFloat(slideText, "cancelToProgress", 1f))
		animatorSet.start()
	}

	val backgroundTop: Int
		get() {
			var t = top

			if (topView?.visibility == VISIBLE) {
				t += ChatActivityEnterTopView.heightDp
			}

			return t
		}

	override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
		val clip = child === topView || child === textFieldContainer

		if (clip) {
			canvas.save()

			if (child === textFieldContainer) {
				var top = (animatedTop + AndroidUtilities.dp(2f) + chatSearchExpandOffset).toInt()

				if (topView != null && topView!!.visibility == VISIBLE) {
					top += topView!!.height
				}

				canvas.clipRect(0, top, measuredWidth, measuredHeight)
			}
			else {
				canvas.clipRect(0, animatedTop, measuredWidth, animatedTop + child.layoutParams.height + AndroidUtilities.dp(2f))
			}
		}

		val result = super.drawChild(canvas, child, drawingTime)

		if (clip) {
			canvas.restore()
		}

		return result
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}

	private fun onSendLongClick(view: View): Boolean {
		if (isInScheduleMode) {
			return false
		}

		val self = parentFragment != null && isUserSelf(parentFragment.currentUser)

		if (sendPopupLayout == null) {
			sendPopupLayout = ActionBarPopupWindowLayout(parentActivity)
			sendPopupLayout?.setAnimationEnabled(false)

			sendPopupLayout?.setOnTouchListener(object : OnTouchListener {
				private val popupRect = Rect()

				override fun onTouch(v: View, event: MotionEvent): Boolean {
					if (event.actionMasked == MotionEvent.ACTION_DOWN) {
						if (sendPopupWindow != null && sendPopupWindow!!.isShowing) {
							v.getHitRect(popupRect)

							if (!popupRect.contains(event.x.toInt(), event.y.toInt())) {
								sendPopupWindow!!.dismiss()
							}
						}
					}

					return false
				}
			})

			sendPopupLayout?.setDispatchKeyEventListener { keyEvent ->
				if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK && keyEvent.repeatCount == 0 && sendPopupWindow != null && sendPopupWindow!!.isShowing) {
					sendPopupWindow!!.dismiss()
				}
			}

			sendPopupLayout?.setShownFromBottom(false)

			val scheduleButtonValue = parentFragment != null && parentFragment.canScheduleMessage()
			val sendWithoutSoundButtonValue = !(self || slowModeTimer > 0 && !isInScheduleMode)

			if (scheduleButtonValue) {
				val scheduleButton = ActionBarMenuSubItem(context, true, !sendWithoutSoundButtonValue)

				if (self) {
					scheduleButton.setTextAndIcon(context.getString(R.string.SetReminder), R.drawable.msg_calendar2)
				}
				else {
					scheduleButton.setTextAndIcon(context.getString(R.string.ScheduleMessage), R.drawable.msg_calendar2)
				}

				scheduleButton.minimumWidth = AndroidUtilities.dp(196f)

				scheduleButton.setOnClickListener {
					if (sendPopupWindow?.isShowing == true) {
						sendPopupWindow?.dismiss()
					}

					AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment!!.dialogId) { notify, scheduleDate ->
						sendMessageInternal(notify, scheduleDate)
					}
				}

				sendPopupLayout?.addView(scheduleButton, createLinear(LayoutHelper.MATCH_PARENT, 48))
			}

			if (sendWithoutSoundButtonValue) {
				val sendWithoutSoundButton = ActionBarMenuSubItem(context, !scheduleButtonValue, true)
				sendWithoutSoundButton.setTextAndIcon(context.getString(R.string.SendWithoutSound), R.drawable.input_notify_off)
				sendWithoutSoundButton.minimumWidth = AndroidUtilities.dp(196f)

				sendWithoutSoundButton.setOnClickListener {
					if (sendPopupWindow?.isShowing == true) {
						sendPopupWindow?.dismiss()
					}
					sendMessageInternal(false, 0)
				}

				sendPopupLayout?.addView(sendWithoutSoundButton, createLinear(LayoutHelper.MATCH_PARENT, 48))
			}

			sendPopupLayout?.setupRadialSelectors(context.getColor(R.color.light_background))

			sendPopupWindow = object : ActionBarPopupWindow(sendPopupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
				override fun dismiss() {
					super.dismiss()
					sendButton.invalidate()
				}
			}

			sendPopupWindow?.setAnimationEnabled(false)
			sendPopupWindow?.animationStyle = R.style.PopupContextAnimation2
			sendPopupWindow?.isOutsideTouchable = true
			sendPopupWindow?.isClippingEnabled = true
			sendPopupWindow?.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
			sendPopupWindow?.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
			sendPopupWindow?.contentView?.isFocusableInTouchMode = true

			SharedConfig.removeScheduledOrNoSoundHint()

			delegate?.onSendLongClick()
		}

		sendPopupLayout?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST))
		sendPopupWindow?.isFocusable = true

		view.getLocationInWindow(location)

		val y = if (isKeyboardVisible && this@ChatActivityEnterView.measuredHeight > AndroidUtilities.dp(if (topView?.visibility == VISIBLE) 48f + 58f else 58f)) {
			location[1] + view.measuredHeight
		}
		else {
			location[1] - sendPopupLayout!!.measuredHeight - AndroidUtilities.dp(2f)
		}

		sendPopupWindow?.showAtLocation(view, Gravity.LEFT or Gravity.TOP, location[0] + view.measuredWidth - sendPopupLayout!!.measuredWidth + AndroidUtilities.dp(8f), y)
		sendPopupWindow?.dimBehind()

		sendButton.invalidate()

		try {
			view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
		}
		catch (e: Exception) {
			// ignored
		}

		return false
	}

	val isSendButtonVisible: Boolean
		get() = sendButton.visibility == VISIBLE

	private fun setRecordVideoButtonVisible(visible: Boolean, animated: Boolean) {
		isInVideoMode = visible

		if (animated) {
			val preferences = MessagesController.getGlobalMainSettings()
			var isChannel = false

			if (DialogObject.isChatDialog(dialog_id)) {
				val chat = accountInstance.messagesController.getChat(-dialog_id)
				isChannel = ChatObject.isChannel(chat) && !chat.megagroup
			}

			preferences.edit().putBoolean(if (isChannel) "currentModeVideoChannel" else "currentModeVideo", visible).commit()
		}

		audioVideoSendButton.setState(if (isInVideoMode) ChatActivityEnterViewAnimatedIconView.State.VIDEO else ChatActivityEnterViewAnimatedIconView.State.VOICE, animated)
		audioVideoSendButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
	}

	fun isRecordingAudioVideo(): Boolean {
		return recordingAudioVideo || runningAnimationAudio?.isRunning == true
	}

	val isRecordLocked: Boolean
		get() = recordingAudioVideo && recordCircle.isSendButtonVisible

	fun cancelRecordingAudioVideo() {
		if (hasRecordVideo && isInVideoMode) {
			CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable)
			delegate?.needStartRecordVideo(5, true, 0)
		}
		else {
			delegate?.needStartRecordAudio(0)
			MediaController.getInstance().stopRecording(0, false, 0)
		}

		recordingAudioVideo = false

		updateRecordInterface(RECORD_STATE_CANCEL)
	}

	fun showContextProgress(show: Boolean) {
		if (show) {
			progressDrawable.startAnimation()
		}
		else {
			progressDrawable.stopAnimation()
		}
	}

	fun setCaption(caption: String?) {
		editField.caption = caption
		checkSendButton(true)
	}

	fun getSlowModeTimer(): CharSequence? {
		return if (slowModeTimer > 0) slowModeButton.getText() else null
	}

	fun setSlowModeTimer(time: Int) {
		slowModeTimer = time
		updateSlowModeText()
	}

	private fun updateSlowModeText() {
		val serverTime = ConnectionsManager.getInstance(currentAccount).currentTime

		AndroidUtilities.cancelRunOnUIThread(updateSlowModeRunnable)

		updateSlowModeRunnable = null
		val currentTime: Int
		var isUploading = false

		if (info != null && info!!.slowmode_seconds != 0 && info!!.slowmode_next_send_date <= serverTime && (SendMessagesHelper.getInstance(currentAccount).isUploadingMessageIdDialog(dialog_id).also { isUploading = it } || SendMessagesHelper.getInstance(currentAccount).isSendingMessageIdDialog(dialog_id))) {
			val chat = accountInstance.messagesController.getChat(info!!.id)

			if (!ChatObject.hasAdminRights(chat)) {
				currentTime = info!!.slowmode_seconds
				slowModeTimer = if (isUploading) Int.MAX_VALUE else Int.MAX_VALUE - 1
			}
			else {
				currentTime = 0
			}
		}
		else if (slowModeTimer >= Int.MAX_VALUE - 1) {
			currentTime = 0

			if (info != null) {
				accountInstance.messagesController.loadFullChat(info!!.id, 0, true)
			}
		}
		else {
			currentTime = slowModeTimer - serverTime
		}

		if (slowModeTimer != 0 && currentTime > 0) {
			slowModeButton.setText(AndroidUtilities.formatDurationNoHours(currentTime, false))
			delegate?.onUpdateSlowModeButton(slowModeButton, false, slowModeButton.getText())
			AndroidUtilities.runOnUIThread(Runnable { updateSlowModeText() }.also { updateSlowModeRunnable = it }, 100)
		}
		else {
			slowModeTimer = 0
		}

		if (!isInScheduleMode) {
			checkSendButton(true)
		}
	}

	fun addTopView(view: View?, lineView: View?, height: Int) {
		if (view == null) {
			return
		}

		topLineView = lineView

		if (topLineView != null) {
			topLineView?.visibility = GONE
			topLineView?.alpha = 0.0f
			addView(topLineView, createFrame(LayoutHelper.MATCH_PARENT, 1f, Gravity.TOP or Gravity.LEFT, 0f, (1 + height).toFloat(), 0f, 0f))
		}

		topView = view
		topView?.visibility = GONE

		topViewEnterProgress = 0f

		topView?.translationY = height.toFloat()

		addView(topView, 0, createFrame(LayoutHelper.MATCH_PARENT, height.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f))

		needShowTopView = false
	}

	fun setForceShowSendButton(value: Boolean, animated: Boolean) {
		forceShowSendButton = value
		checkSendButton(animated)
	}

	fun setAllowStickersAndGifs(needAnimatedEmoji: Boolean, needStickers: Boolean, needGifs: Boolean) {
		setAllowStickersAndGifs(needAnimatedEmoji, needStickers, needGifs, false)
	}

	fun setAllowStickersAndGifs(needAnimatedEmoji: Boolean, needStickers: Boolean, needGifs: Boolean, waitingForKeyboardOpen: Boolean) {
		if ((allowStickers != needStickers || allowGifs != needGifs) && emojiView != null) {
			if (emojiViewVisible && !waitingForKeyboardOpen) {
				removeEmojiViewAfterAnimation = true
				hidePopup(false)
			}
			else {
				if (waitingForKeyboardOpen) {
					openKeyboardInternal()
				}
			}
		}

		allowAnimatedEmoji = needAnimatedEmoji
		allowStickers = needStickers
		allowGifs = needGifs

		emojiView?.setAllow(allowStickers, allowGifs, true)

		setEmojiButtonImage(!isPaused)
	}

	fun addEmojiToRecent(code: String?) {
		createEmojiView()
		emojiView?.addEmojiToRecent(code)
	}

	fun setOpenGifsTabFirst() {
		createEmojiView()
		MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, gif = true, cache = true, force = false)
		emojiView?.switchToGifRecent()
	}

	fun showTopView(animated: Boolean, openKeyboard: Boolean) {
		showTopView(animated, openKeyboard, false)
	}

	private fun showTopView(animated: Boolean, openKeyboard: Boolean, skipAwait: Boolean) {
		if (topView == null || topViewShowed || visibility != VISIBLE) {
			if (recordedAudioPanel.visibility != VISIBLE && (!forceShowSendButton || openKeyboard)) {
				openKeyboard()
			}

			return
		}

		val openKeyboardInternal = recordedAudioPanel.visibility != VISIBLE && (!forceShowSendButton || openKeyboard) && (botReplyMarkup == null || editingMessageObject != null)

		if (!skipAwait && animated && openKeyboardInternal && !(isKeyboardVisible || isPopupShowing)) {
			openKeyboard()

			if (showTopViewRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(showTopViewRunnable)
			}

			AndroidUtilities.runOnUIThread(Runnable {
				showTopView(animated = true, openKeyboard = false, skipAwait = true)
				showTopViewRunnable = null
			}.also {
				showTopViewRunnable = it
			}, 200)

			return
		}

		needShowTopView = true
		topViewShowed = true

		if (allowShowTopView) {
			topView?.visibility = VISIBLE
			topLineView?.visibility = VISIBLE

			currentTopViewAnimation?.cancel()
			currentTopViewAnimation = null

			resizeForTopView(true)

			if (animated) {
				currentTopViewAnimation = ValueAnimator.ofFloat(topViewEnterProgress, 1f)
				currentTopViewAnimation?.addUpdateListener(topViewUpdateListener)

				currentTopViewAnimation?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (currentTopViewAnimation != null && currentTopViewAnimation == animation) {
							currentTopViewAnimation = null
						}

						NotificationCenter.getInstance(currentAccount).onAnimationFinish(notificationsIndex)

						parentFragment?.mentionContainer?.translationY = 0f
					}
				})

				currentTopViewAnimation?.duration = ChatListItemAnimator.DEFAULT_DURATION + 20
				currentTopViewAnimation?.interpolator = ChatListItemAnimator.DEFAULT_INTERPOLATOR
				currentTopViewAnimation?.start()

				notificationsIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(notificationsIndex, null)
			}
			else {
				topViewEnterProgress = 1f
				topView?.translationY = 0f
				topLineView?.alpha = 1.0f
			}

			if (openKeyboardInternal) {
				editField.requestFocus()
				openKeyboard()
			}
		}
	}

	fun onEditTimeExpired() {
		doneButtonContainer.visibility = GONE
	}

	fun showEditDoneProgress(show: Boolean, animated: Boolean) {
		doneButtonAnimation?.cancel()

		if (!animated) {
			if (show) {
				doneButtonImage.scaleX = 0.1f
				doneButtonImage.scaleY = 0.1f
				doneButtonImage.alpha = 0.0f
				doneButtonProgress.scaleX = 1.0f
				doneButtonProgress.scaleY = 1.0f
				doneButtonProgress.alpha = 1.0f
				doneButtonImage.visibility = INVISIBLE
				doneButtonProgress.visibility = VISIBLE
				doneButtonContainer.isEnabled = false
			}
			else {
				doneButtonProgress.scaleX = 0.1f
				doneButtonProgress.scaleY = 0.1f
				doneButtonProgress.alpha = 0.0f
				doneButtonImage.scaleX = 1.0f
				doneButtonImage.scaleY = 1.0f
				doneButtonImage.alpha = 1.0f
				doneButtonImage.visibility = VISIBLE
				doneButtonProgress.visibility = INVISIBLE
				doneButtonContainer.isEnabled = true
			}
		}
		else {
			doneButtonAnimation = AnimatorSet()

			if (show) {
				doneButtonProgress.visibility = VISIBLE
				doneButtonContainer.isEnabled = false
				doneButtonAnimation?.playTogether(ObjectAnimator.ofFloat(doneButtonImage, SCALE_X, 0.1f), ObjectAnimator.ofFloat(doneButtonImage, SCALE_Y, 0.1f), ObjectAnimator.ofFloat(doneButtonImage, ALPHA, 0.0f), ObjectAnimator.ofFloat(doneButtonProgress, SCALE_X, 1.0f), ObjectAnimator.ofFloat(doneButtonProgress, SCALE_Y, 1.0f), ObjectAnimator.ofFloat(doneButtonProgress, ALPHA, 1.0f))
			}
			else {
				doneButtonImage.visibility = VISIBLE
				doneButtonContainer.isEnabled = true
				doneButtonAnimation?.playTogether(ObjectAnimator.ofFloat(doneButtonProgress, SCALE_X, 0.1f), ObjectAnimator.ofFloat(doneButtonProgress, SCALE_Y, 0.1f), ObjectAnimator.ofFloat(doneButtonProgress, ALPHA, 0.0f), ObjectAnimator.ofFloat(doneButtonImage, SCALE_X, 1.0f), ObjectAnimator.ofFloat(doneButtonImage, SCALE_Y, 1.0f), ObjectAnimator.ofFloat(doneButtonImage, ALPHA, 1.0f))
			}

			doneButtonAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (doneButtonAnimation != null && doneButtonAnimation == animation) {
						if (!show) {
							doneButtonProgress.visibility = INVISIBLE
						}
						else {
							doneButtonImage.visibility = INVISIBLE
						}
					}
				}

				override fun onAnimationCancel(animation: Animator) {
					if (doneButtonAnimation != null && doneButtonAnimation == animation) {
						doneButtonAnimation = null
					}
				}
			})

			doneButtonAnimation?.duration = 150
			doneButtonAnimation?.start()
		}
	}

	fun hideTopView(animated: Boolean) {
		if (topView == null || !topViewShowed) {
			return
		}

		if (showTopViewRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(showTopViewRunnable)
		}

		topViewShowed = false
		needShowTopView = false

		if (allowShowTopView) {
			currentTopViewAnimation?.cancel()
			currentTopViewAnimation = null

			if (animated) {
				currentTopViewAnimation = ValueAnimator.ofFloat(topViewEnterProgress, 0f)
				currentTopViewAnimation?.addUpdateListener(topViewUpdateListener)

				currentTopViewAnimation?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (currentTopViewAnimation != null && currentTopViewAnimation == animation) {
							topView?.visibility = GONE
							topLineView?.visibility = GONE

							resizeForTopView(false)

							currentTopViewAnimation = null
						}

						parentFragment?.mentionContainer?.translationY = 0f
					}

					override fun onAnimationCancel(animation: Animator) {
						if (currentTopViewAnimation != null && currentTopViewAnimation == animation) {
							currentTopViewAnimation = null
						}
					}
				})

				currentTopViewAnimation?.duration = ChatListItemAnimator.DEFAULT_DURATION
				currentTopViewAnimation?.interpolator = ChatListItemAnimator.DEFAULT_INTERPOLATOR
				currentTopViewAnimation?.start()
			}
			else {
				topViewEnterProgress = 0f
				topView?.visibility = GONE

				topLineView?.visibility = GONE
				topLineView?.alpha = 0.0f

				resizeForTopView(false)

				topView?.translationY = ChatActivityEnterTopView.heightDp.toFloat()
			}
		}
	}

	val isTopViewVisible: Boolean
		get() = topView?.visibility == VISIBLE

	fun onAdjustPanTransitionUpdate(y: Float) {
		botWebViewMenuContainer?.translationY = y
	}

	fun onAdjustPanTransitionEnd() {
		botWebViewMenuContainer?.onPanTransitionEnd()
		onKeyboardClosed?.run()
		onKeyboardClosed = null
	}

	fun onAdjustPanTransitionStart(keyboardVisible: Boolean, contentHeight: Int) {
		botWebViewMenuContainer?.onPanTransitionStart(keyboardVisible, contentHeight)

		if (keyboardVisible && showTopViewRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(showTopViewRunnable)
			showTopViewRunnable?.run()
		}

		if (setTextFieldRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(setTextFieldRunnable)
			setTextFieldRunnable?.run()
		}

		if (keyboardVisible && editField.hasFocus() && hasBotWebView() && botCommandsMenuIsShowing()) {
			botWebViewMenuContainer?.dismiss()
		}
	}

	private fun onWindowSizeChanged() {
		var size = sizeNotifierLayout!!.height

		if (!isKeyboardVisible) {
			size -= emojiPadding
		}

		delegate?.onWindowSizeChanged(size)

		if (topView != null) {
			if (size < AndroidUtilities.dp(72f) + ActionBar.getCurrentActionBarHeight()) {
				if (allowShowTopView) {
					allowShowTopView = false

					if (needShowTopView) {
						topView?.visibility = GONE

						topLineView?.visibility = GONE
						topLineView?.alpha = 0.0f

						resizeForTopView(false)

						topViewEnterProgress = 0f

						topView?.translationY = ChatActivityEnterTopView.heightDp.toFloat()
					}
				}
			}
			else {
				if (!allowShowTopView) {
					allowShowTopView = true

					if (needShowTopView) {
						topView?.visibility = VISIBLE

						topLineView?.visibility = VISIBLE
						topLineView?.alpha = 1.0f

						resizeForTopView(true)

						topViewEnterProgress = 1f

						topView?.translationY = 0f
					}
				}
			}
		}
	}

	private fun resizeForTopView(show: Boolean) {
		textFieldContainer.updateLayoutParams<LayoutParams> {
			topMargin = if (show) ChatActivityEnterTopView.heightDp - AndroidUtilities.dp(2f) else 0
		}

		minimumHeight = AndroidUtilities.dp(Companion.height.toFloat()) + if (show) ChatActivityEnterTopView.heightDp else 0

		if (isStickersExpanded) {
			if (searchingType == 0) {
				setStickersExpanded(expanded = false, animated = true, byDrag = false)
			}
			else {
				checkStickersExpandHeight()
			}
		}
	}

	fun onDestroy() {
		destroyed = true

		NotificationCenter.getInstance(currentAccount).let {
			it.removeObserver(this, NotificationCenter.recordStarted)
			it.removeObserver(this, NotificationCenter.recordStartError)
			it.removeObserver(this, NotificationCenter.recordStopped)
			it.removeObserver(this, NotificationCenter.recordProgressChanged)
			it.removeObserver(this, NotificationCenter.closeChats)
			it.removeObserver(this, NotificationCenter.audioDidSent)
			it.removeObserver(this, NotificationCenter.audioRouteChanged)
			it.removeObserver(this, NotificationCenter.messagePlayingDidReset)
			it.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged)
			it.removeObserver(this, NotificationCenter.featuredStickersDidLoad)
			it.removeObserver(this, NotificationCenter.messageReceivedByServer)
			it.removeObserver(this, NotificationCenter.sendingMessagesChanged)
			it.removeObserver(this, NotificationCenter.audioRecordTooShort)
			it.removeObserver(this, NotificationCenter.updateBotMenuButton)
			if (isBot) {
				it.removeObserver(this, NotificationCenter.aiBotUpdate)
			}
		}

		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.emojiLoaded)

		emojiView?.onDestroy()

		if (updateSlowModeRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(updateSlowModeRunnable)
			updateSlowModeRunnable = null
		}

		try {
			wakeLock?.release()
			wakeLock = null
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		sizeNotifierLayout?.setDelegate(null)

		senderSelectPopupWindow?.setPauseNotifications(false)
		senderSelectPopupWindow?.dismiss()
	}

	fun checkChannelRights() {
		if (parentFragment == null) {
			return
		}

		val chat = parentFragment.currentChat
		val userFull = parentFragment.currentUserInfo

		if (chat != null) {
			audioVideoButtonContainer.alpha = if (ChatObject.canSendMedia(chat)) 1.0f else 0.5f
			emojiView?.setStickersBanned(!ChatObject.canSendStickers(chat), chat.id)
		}
		else if (userFull != null) {
			audioVideoButtonContainer.alpha = if (userFull.voice_messages_forbidden) 0.5f else 1.0f
		}
	}

	fun onBeginHide() {
		if (focusRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(focusRunnable)
			focusRunnable = null
		}
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		senderSelectPopupWindow?.setPauseNotifications(false)
		senderSelectPopupWindow?.dismiss()
	}

	fun onPause() {
		isPaused = true

		senderSelectPopupWindow?.setPauseNotifications(false)
		senderSelectPopupWindow?.dismiss()

		if (isKeyboardVisible) {
			showKeyboardOnResume = true
		}

		AndroidUtilities.runOnUIThread(Runnable {
			if (parentFragment == null || parentFragment.isLastFragment) {
				closeKeyboard()
			}

			hideKeyboardRunnable = null
		}.also {
			hideKeyboardRunnable = it
		}, 500)
	}

	fun onResume() {
		isPaused = false

		if (hideKeyboardRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(hideKeyboardRunnable)
			hideKeyboardRunnable = null
		}

		if (hasBotWebView() && botCommandsMenuIsShowing()) {
			return
		}

		if (showKeyboardOnResume && parentFragment?.isLastFragment == true) {
			showKeyboardOnResume = false

			if (searchingType == 0) {
				editField.requestFocus()
			}

			AndroidUtilities.showKeyboard(editField)

			if (!AndroidUtilities.usingHardwareInput && !isKeyboardVisible && !AndroidUtilities.isInMultiwindow) {
				waitingForKeyboardOpen = true
				AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
				AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100)
			}
		}
	}

	override fun setVisibility(visibility: Int) {
		super.setVisibility(visibility)
		editField.isEnabled = visibility == VISIBLE
	}

	fun setDialogId(id: Long, account: Int) {
		dialog_id = id

		if (currentAccount != account) {
			NotificationCenter.getInstance(currentAccount).let {
				it.onAnimationFinish(notificationsIndex)
				it.removeObserver(this, NotificationCenter.recordStarted)
				it.removeObserver(this, NotificationCenter.recordStartError)
				it.removeObserver(this, NotificationCenter.recordStopped)
				it.removeObserver(this, NotificationCenter.recordProgressChanged)
				it.removeObserver(this, NotificationCenter.closeChats)
				it.removeObserver(this, NotificationCenter.audioDidSent)
				it.removeObserver(this, NotificationCenter.audioRouteChanged)
				it.removeObserver(this, NotificationCenter.messagePlayingDidReset)
				it.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged)
				it.removeObserver(this, NotificationCenter.featuredStickersDidLoad)
				it.removeObserver(this, NotificationCenter.messageReceivedByServer)
				it.removeObserver(this, NotificationCenter.sendingMessagesChanged)
				if (isBot) {
					it.removeObserver(this, NotificationCenter.aiBotUpdate)
				}
			}

			currentAccount = account
			accountInstance = AccountInstance.getInstance(currentAccount)

			NotificationCenter.getInstance(currentAccount).let {
				it.addObserver(this, NotificationCenter.recordStarted)
				it.addObserver(this, NotificationCenter.recordStartError)
				it.addObserver(this, NotificationCenter.recordStopped)
				it.addObserver(this, NotificationCenter.recordProgressChanged)
				it.addObserver(this, NotificationCenter.closeChats)
				it.addObserver(this, NotificationCenter.audioDidSent)
				it.addObserver(this, NotificationCenter.audioRouteChanged)
				it.addObserver(this, NotificationCenter.messagePlayingDidReset)
				it.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged)
				it.addObserver(this, NotificationCenter.featuredStickersDidLoad)
				it.addObserver(this, NotificationCenter.messageReceivedByServer)
				it.addObserver(this, NotificationCenter.sendingMessagesChanged)
				if (isBot) {
					it.addObserver(this, NotificationCenter.aiBotUpdate)
				}
			}
		}

		updateScheduleButton(false)
		checkRoundVideo()
		updateFieldHint(false)
		updateSendAsButton(false)
		updateCreateMediaButton()
	}

	private fun updateCreateMediaButton() {
		// createMediaButton.visibility = if (canWriteToChannel) VISIBLE else GONE
		createMediaButton.gone()
	}

	fun setChatInfo(chatInfo: TLRPC.ChatFull) {
		info = chatInfo
		emojiView?.setChatInfo(info)
		setSlowModeTimer(chatInfo.slowmode_next_send_date)
	}

	fun checkRoundVideo() {
		if (hasRecordVideo) {
			return
		}

		if (attachLayout == null) {
			hasRecordVideo = false
			setRecordVideoButtonVisible(visible = false, animated = false)
			return
		}

		hasRecordVideo = true

		var isChannel = false

		if (DialogObject.isChatDialog(dialog_id)) {
			val chat = accountInstance.messagesController.getChat(-dialog_id)

			isChannel = ChatObject.isChannel(chat) && !chat.megagroup

			if (isChannel && !chat!!.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages)) {
				hasRecordVideo = false
			}
		}

		if (!SharedConfig.inappCamera) {
			hasRecordVideo = false
		}

		if (hasRecordVideo) {
			if (SharedConfig.hasCameraCache) {
				CameraController.getInstance().initCamera(null)
			}

			val preferences = MessagesController.getGlobalMainSettings()
			val currentModeVideo = preferences.getBoolean(if (isChannel) "currentModeVideoChannel" else "currentModeVideo", isChannel)

			setRecordVideoButtonVisible(currentModeVideo, false)
		}
		else {
			setRecordVideoButtonVisible(visible = false, animated = false)
		}
	}

	fun hasRecordVideo(): Boolean {
		return hasRecordVideo
	}

	fun getReplyingMessageObject(): MessageObject? {
		return replyingMessageObject
	}

	fun setReplyingMessageObject(messageObject: MessageObject?) {
		if (messageObject != null) {
			if (botMessageObject == null && botButtonsMessageObject !== replyingMessageObject) {
				botMessageObject = botButtonsMessageObject
			}

			replyingMessageObject = messageObject

			setButtons(replyingMessageObject, true)
		}
		else if (replyingMessageObject === botButtonsMessageObject) {
			replyingMessageObject = null
			setButtons(botMessageObject, false)
			botMessageObject = null
		}
		else {
			replyingMessageObject = null
		}

		MediaController.getInstance().setReplyingMessage(messageObject, threadMessage)

		updateFieldHint(false)
	}

	fun updateFieldHint(animated: Boolean) {
		if (replyingMessageObject != null && replyingMessageObject?.messageOwner?.reply_markup != null && !replyingMessageObject?.messageOwner?.reply_markup?.placeholder.isNullOrEmpty()) {
			editField.setHintText(replyingMessageObject?.messageOwner?.reply_markup?.placeholder, animated)
		}
		else if (editingMessageObject != null) {
			editField.setHintText(if (isEditingCaption) context.getString(R.string.Caption) else context.getString(R.string.TypeMessage))
		}
		else if (botKeyboardViewVisible && botButtonsMessageObject != null && botButtonsMessageObject?.messageOwner?.reply_markup != null && !botButtonsMessageObject?.messageOwner?.reply_markup?.placeholder.isNullOrEmpty()) {
			editField.setHintText(botButtonsMessageObject?.messageOwner?.reply_markup?.placeholder, animated)
		}
		else {
			var isChannel = false
			var anonymously = false

			if (DialogObject.isChatDialog(dialog_id)) {
				val chat = accountInstance.messagesController.getChat(-dialog_id)
				val chatFull = accountInstance.messagesController.getChatFull(-dialog_id)
				isChannel = ChatObject.isChannel(chat) && !chat.megagroup
				anonymously = ChatObject.getSendAsPeerId(chat, chatFull) == chat?.id
			}

			if (anonymously) {
				// editField.setHintText(context.getString(R.string.SendAnonymously))
				editField.setHintText(context.getString(R.string.ChannelBroadcast))
			}
			else {
				if (parentFragment != null && parentFragment.isThreadChat) {
					if (parentFragment.isReplyChatComment) {
						editField.setHintText(context.getString(R.string.Comment))
					}
					else {
						editField.setHintText(context.getString(R.string.Reply))
					}
				}
				else if (isChannel) {
					editField.setHintText(context.getString(R.string.ChannelBroadcast))

//					if (silent) {
//						editField.setHintText(context.getString(R.string.ChannelSilentBroadcast), animated)
//					}
//					else {
//						editField.setHintText(context.getString(R.string.ChannelBroadcast), animated)
//					}
				}
				else {
					if (dialog_id == BuildConfig.AI_BOT_ID || dialog_id == BuildConfig.PHOENIX_BOT_ID || dialog_id == BuildConfig.BUSINESS_BOT_ID || dialog_id == BuildConfig.CANCER_BOT_ID) {
						editField.setHintText(context.getString(R.string.ask_ello_ai_anything))
					}
					else {
						editField.setHintText(context.getString(R.string.TypeMessage))
					}
				}
			}
		}
	}

	fun setWebPage(webPage: TLRPC.WebPage?, searchWebPages: Boolean) {
		messageWebPage = webPage
		isMessageWebPageSearchEnabled = searchWebPages
	}

	private fun hideRecordedAudioPanel(wasSent: Boolean) {
		if (recordPanelAnimation != null && recordPanelAnimation!!.isRunning) {
			return
		}

		audioToSendPath = null
		audioToSend = null
		audioToSendMessageObject = null
		videoToSendMessageObject = null

		videoTimelineView.destroy()

		audioVideoSendButton.visibility = VISIBLE

		if (wasSent) {
			attachButton?.alpha = 0f
			attachButton?.scaleX = 0f
			attachButton?.scaleY = 0f

			emojiButton.alpha = 0f
			emojiButton.scaleX = 0f
			emojiButton.scaleY = 0f

			notifyButton?.alpha = 0f
			notifyButton?.scaleX = 0f
			notifyButton?.scaleY = 0f

			createMediaButton.alpha = 0f
			createMediaButton.scaleX = 0f
			createMediaButton.scaleY = 0f

			recordPanelAnimation = AnimatorSet()

			recordPanelAnimation?.playTogether(
					ObjectAnimator.ofFloat(emojiButton, ALPHA, 1.0f),
					ObjectAnimator.ofFloat(emojiButton, SCALE_X, 1.0f),
					ObjectAnimator.ofFloat(emojiButton, SCALE_Y, 1.0f),
					ObjectAnimator.ofFloat(createMediaButton, ALPHA, 1.0f),
					ObjectAnimator.ofFloat(createMediaButton, SCALE_X, 1.0f),
					ObjectAnimator.ofFloat(createMediaButton, SCALE_Y, 1.0f),
					ObjectAnimator.ofFloat(recordDeleteImageView, ALPHA, 0.0f),
					ObjectAnimator.ofFloat(recordDeleteImageView, SCALE_X, 0.0f),
					ObjectAnimator.ofFloat(recordDeleteImageView, SCALE_Y, 0.0f),
					ObjectAnimator.ofFloat(recordedAudioPanel, ALPHA, 0.0f),
					ObjectAnimator.ofFloat(attachButton, ALPHA, 1.0f),
					ObjectAnimator.ofFloat(attachButton, SCALE_X, 1.0f),
					ObjectAnimator.ofFloat(attachButton, SCALE_Y, 1.0f),
					ObjectAnimator.ofFloat(editField, ALPHA, 1f),
					ObjectAnimator.ofFloat(editField, TRANSLATION_X, 0f),
			)

			notifyButton?.let {
				recordPanelAnimation?.playTogether(
						ObjectAnimator.ofFloat(it, ALPHA, 1f),
						ObjectAnimator.ofFloat(it, SCALE_X, 1f),
						ObjectAnimator.ofFloat(it, SCALE_Y, 1f),
				)
			}

			botCommandsMenuButton?.let {
				it.alpha = 0f
				it.scaleY = 0f
				it.scaleX = 0f
				recordPanelAnimation?.playTogether(ObjectAnimator.ofFloat(it, ALPHA, 1.0f), ObjectAnimator.ofFloat(it, SCALE_X, 1.0f), ObjectAnimator.ofFloat(it, SCALE_Y, 1.0f))
			}

			recordPanelAnimation?.duration = 150

			recordPanelAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					recordedAudioPanel.visibility = GONE
					editField.requestFocus()
				}
			})
		}
		else {
			// recordDeleteImageView.playAnimation()

			val exitAnimation = AnimatorSet()

			if (isInVideoMode) {
				exitAnimation.playTogether(ObjectAnimator.ofFloat(videoTimelineView, ALPHA, 0.0f), ObjectAnimator.ofFloat(videoTimelineView, TRANSLATION_X, -AndroidUtilities.dp(20f).toFloat()), ObjectAnimator.ofFloat(editField, ALPHA, 1f), ObjectAnimator.ofFloat(editField, TRANSLATION_X, 0f))
			}
			else {
				editField.alpha = 1f
				editField.translationX = 0f
				exitAnimation.playTogether(ObjectAnimator.ofFloat(recordedAudioSeekBar, ALPHA, 0.0f), ObjectAnimator.ofFloat(recordedAudioPlayButton, ALPHA, 0.0f), ObjectAnimator.ofFloat(recordedAudioBackground, ALPHA, 0.0f), ObjectAnimator.ofFloat(recordedAudioTimeTextView, ALPHA, 0.0f), ObjectAnimator.ofFloat(recordedAudioSeekBar, TRANSLATION_X, -AndroidUtilities.dp(20f).toFloat()), ObjectAnimator.ofFloat(recordedAudioPlayButton, TRANSLATION_X, -AndroidUtilities.dp(20f).toFloat()), ObjectAnimator.ofFloat(recordedAudioBackground, TRANSLATION_X, -AndroidUtilities.dp(20f).toFloat()), ObjectAnimator.ofFloat(recordedAudioTimeTextView, TRANSLATION_X, -AndroidUtilities.dp(20f).toFloat()))
			}

			exitAnimation.duration = 200

			val attachIconAnimator: AnimatorSet?

			if (attachButton != null) {
				attachButton?.alpha = 0f
				attachButton?.scaleX = 0f
				attachButton?.scaleY = 0f

				attachIconAnimator = AnimatorSet()
				attachIconAnimator.playTogether(ObjectAnimator.ofFloat(attachButton, ALPHA, 1.0f), ObjectAnimator.ofFloat(attachButton, SCALE_X, 1.0f), ObjectAnimator.ofFloat(attachButton, SCALE_Y, 1.0f))
				attachIconAnimator.duration = 150
			}
			else {
				attachIconAnimator = null
			}

			emojiButton.alpha = 0f
			emojiButton.scaleX = 0f
			emojiButton.scaleY = 0f

			notifyButton?.alpha = 0f
			notifyButton?.scaleX = 0f
			notifyButton?.scaleY = 0f

			createMediaButton.alpha = 0f
			createMediaButton.scaleX = 0f
			createMediaButton.scaleY = 0f

			val iconsEndAnimator = AnimatorSet()

			iconsEndAnimator.playTogether(
					ObjectAnimator.ofFloat(recordDeleteImageView, ALPHA, 0.0f),
					ObjectAnimator.ofFloat(recordDeleteImageView, SCALE_X, 0.0f),
					ObjectAnimator.ofFloat(recordDeleteImageView, SCALE_Y, 0.0f),
					ObjectAnimator.ofFloat(recordDeleteImageView, ALPHA, 0.0f),
					ObjectAnimator.ofFloat(emojiButton, ALPHA, 1.0f),
					ObjectAnimator.ofFloat(emojiButton, SCALE_X, 1.0f),
					ObjectAnimator.ofFloat(emojiButton, SCALE_Y, 1.0f),
					ObjectAnimator.ofFloat(createMediaButton, ALPHA, 1.0f),
					ObjectAnimator.ofFloat(createMediaButton, SCALE_X, 1.0f),
					ObjectAnimator.ofFloat(createMediaButton, SCALE_Y, 1.0f),
			)

			notifyButton?.let {
				iconsEndAnimator.playTogether(
						ObjectAnimator.ofFloat(it, ALPHA, 1f),
						ObjectAnimator.ofFloat(it, SCALE_X, 1f),
						ObjectAnimator.ofFloat(it, SCALE_Y, 1f),
				)
			}

			botCommandsMenuButton?.let {
				it.alpha = 0f
				it.scaleY = 0f
				it.scaleX = 0f
				iconsEndAnimator.playTogether(ObjectAnimator.ofFloat(it, ALPHA, 1.0f), ObjectAnimator.ofFloat(it, SCALE_X, 1.0f), ObjectAnimator.ofFloat(it, SCALE_Y, 1.0f))
			}

			iconsEndAnimator.duration = 150
			iconsEndAnimator.startDelay = 600

			recordPanelAnimation = AnimatorSet()

			if (attachIconAnimator != null) {
				recordPanelAnimation?.playTogether(exitAnimation, attachIconAnimator, iconsEndAnimator)
			}
			else {
				recordPanelAnimation?.playTogether(exitAnimation, iconsEndAnimator)
			}

			recordPanelAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					recordedAudioSeekBar.alpha = 1f
					recordedAudioSeekBar.translationX = 0f
					recordedAudioPlayButton.alpha = 1f
					recordedAudioPlayButton.translationX = 0f
					recordedAudioBackground.alpha = 1f
					recordedAudioBackground.translationX = 0f
					recordedAudioTimeTextView.alpha = 1f
					recordedAudioTimeTextView.translationX = 0f
					videoTimelineView.alpha = 1f
					videoTimelineView.translationX = 0f
					editField.alpha = 1f
					editField.translationX = 0f
					editField.requestFocus()
					recordedAudioPanel.visibility = GONE
				}
			})
		}

		recordPanelAnimation?.start()
	}

	private fun sendMessage() {
		if (parentFragment?.isAiPromptsDepleted() == true) {
			return
		}

		if (isInScheduleMode) {
			AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment!!.dialogId) { notify, scheduleDate ->
				sendMessageInternal(notify, scheduleDate)
			}
		}
		else {
			sendMessageInternal(true, 0)
		}
	}

	private fun sendMessageInternal(notify: Boolean, scheduleDate: Int) {
		if (slowModeTimer == Int.MAX_VALUE && !isInScheduleMode) {
			delegate?.scrollToSendingMessage()
			return
		}

		if (parentFragment != null) {
			val chat = parentFragment.currentChat
			val user = parentFragment.currentUser

			if (user != null || ChatObject.isChannel(chat) && chat.megagroup || !ChatObject.isChannel(chat)) {
				MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("silent_$dialog_id", !notify).commit()
			}
		}

		if (isStickersExpanded) {
			setStickersExpanded(expanded = false, animated = true, byDrag = false)

			if (searchingType != 0 && emojiView != null) {
				emojiView?.closeSearch(false)
				emojiView?.hideSearchKeyboard()
			}
		}

		if (videoToSendMessageObject != null) {
			delegate?.needStartRecordVideo(4, notify, scheduleDate)
			hideRecordedAudioPanel(true)
			checkSendButton(true)
			return
		}
		else if (audioToSend != null) {
			val playing = MediaController.getInstance().playingMessageObject

			if (playing != null && playing === audioToSendMessageObject) {
				MediaController.getInstance().cleanupPlayer(true, true)
			}

			SendMessagesHelper.getInstance(currentAccount).sendMessage(audioToSend, null, audioToSendPath, dialog_id, replyingMessageObject, threadMessage, null, null, null, null, notify, scheduleDate, 0, null, null, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)

			delegate?.onMessageSend(null, notify, scheduleDate)

			hideRecordedAudioPanel(true)
			checkSendButton(true)

			return
		}

		val message: CharSequence = editField.text

		if (parentFragment != null) {
			val chat = parentFragment.currentChat

			if (chat != null && chat.slowmode_enabled && !ChatObject.hasAdminRights(chat)) {
				if (message.length > accountInstance.messagesController.maxMessageLength) {
					AlertsCreator.showSimpleAlert(parentFragment, context.getString(R.string.Slowmode), context.getString(R.string.SlowmodeSendErrorTooLong))
					return
				}
				else if (forceShowSendButton && message.isNotEmpty()) {
					AlertsCreator.showSimpleAlert(parentFragment, context.getString(R.string.Slowmode), context.getString(R.string.SlowmodeSendError))
					return
				}
			}
		}

		if (checkPremiumAnimatedEmoji(currentAccount, dialog_id, parentFragment, null, message)) {
			return
		}

		if (processSendingText(message, notify, scheduleDate)) {
			if (delegate?.hasForwardingMessages() == true || scheduleDate != 0 && !isInScheduleMode || isInScheduleMode) {
				editField.setText("")
				delegate?.onMessageSend(message, notify, scheduleDate)
			}
			else {
				messageTransitionIsRunning = false

				AndroidUtilities.runOnUIThread(Runnable {
					moveToSendStateRunnable = null

					hideTopView(true)

					editField.setText("")

					delegate?.onMessageSend(message, notify, scheduleDate)
				}.also { moveToSendStateRunnable = it }, 200)
			}

			lastTypingTimeSend = 0
		}
		else if (forceShowSendButton) {
			delegate?.onMessageSend(null, notify, scheduleDate)
		}
	}

	fun doneEditingMessage() {
		if (editingMessageObject == null) {
			return
		}

		if (currentLimit - codePointCount < 0) {
			AndroidUtilities.shakeView(captionLimitView, 2f, 0)
			captionLimitView.context.vibrate(200)
			return
		}

		if (searchingType != 0) {
			setSearchingTypeInternal(0, true)

			emojiView?.closeSearch(false)

			if (isStickersExpanded) {
				setStickersExpanded(expanded = false, animated = true, byDrag = false)

				waitingForKeyboardOpenAfterAnimation = true

				AndroidUtilities.runOnUIThread({
					waitingForKeyboardOpenAfterAnimation = false
					openKeyboardInternal()
				}, 200)
			}
		}

		var text: CharSequence = editField.text

		if (editingMessageObject == null || editingMessageObject!!.type != MessageObject.TYPE_EMOJIS) {
			text = AndroidUtilities.getTrimmedString(text)
		}

		val entities = MediaDataController.getInstance(currentAccount).getEntities(arrayOf(text), supportsSendingNewEntities())

		if (!TextUtils.equals(text, editingMessageObject?.messageText) || !entities.isNullOrEmpty() || (entities.isNullOrEmpty() && !editingMessageObject?.messageOwner?.entities.isNullOrEmpty()) || editingMessageObject?.messageOwner?.media is TLRPC.TL_messageMediaWebPage) {
			editingMessageObject?.editingMessage = text
			editingMessageObject?.editingMessageEntities = entities
			editingMessageObject?.editingMessageSearchWebPage = isMessageWebPageSearchEnabled
			SendMessagesHelper.getInstance(currentAccount).editMessage(editingMessageObject, null, null, null, null, null, false, null)
		}

		setEditingMessageObject(null, false)
	}

	private fun processSendingText(text: CharSequence, notify: Boolean, scheduleDate: Int): Boolean {
		@Suppress("NAME_SHADOWING") var text = text
		val emojiOnly = IntArray(1)

		Emoji.parseEmojis(text, emojiOnly)

		val hasOnlyEmoji = emojiOnly[0] > 0

		if (!hasOnlyEmoji) {
			text = AndroidUtilities.getTrimmedString(text)
		}

		val supportsNewEntities = supportsSendingNewEntities()
		val maxLength = accountInstance.messagesController.maxMessageLength

		if (text.isNotEmpty()) {
			if (delegate != null && parentFragment != null && scheduleDate != 0 == parentFragment.isInScheduleMode) {
				delegate?.prepareMessageSending()
			}

			var end: Int
			var start = 0

			do {
				var whitespaceIndex = -1
				var dotIndex = -1
				var tabIndex = -1
				var enterIndex = -1

				if (text.length > start + maxLength) {
					var i = start + maxLength - 1
					var k = 0

					while (i > start && k < 300) {
						val c = text[i]
						val c2 = if (i > 0) text[i - 1] else ' '

						if (c == '\n' && c2 == '\n') {
							tabIndex = i
							break
						}
						else if (c == '\n') {
							enterIndex = i
						}
						else if (dotIndex < 0 && Character.isWhitespace(c) && c2 == '.') {
							dotIndex = i
						}
						else if (whitespaceIndex < 0 && Character.isWhitespace(c)) {
							whitespaceIndex = i
						}

						i--
						k++
					}
				}

				end = min(start + maxLength, text.length)

				if (tabIndex > 0) {
					end = tabIndex
				}
				else if (enterIndex > 0) {
					end = enterIndex
				}
				else if (dotIndex > 0) {
					end = dotIndex
				}
				else if (whitespaceIndex > 0) {
					end = whitespaceIndex
				}

				var part = text.subSequence(start, end)

				if (!hasOnlyEmoji) {
					part = AndroidUtilities.getTrimmedString(part)
				}

				val message: Array<CharSequence?> = arrayOf(part)
				val entities = MediaDataController.getInstance(currentAccount).getEntities(message, supportsNewEntities)
				var sendAnimationData: SendAnimationData? = null

				if (delegate?.hasForwardingMessages() == false) {
					sendAnimationData = SendAnimationData()

					sendAnimationData.height = AndroidUtilities.dp(22f).toFloat()
					sendAnimationData.width = sendAnimationData.height

					editField.getLocationInWindow(location)

					sendAnimationData.x = (location[0] + AndroidUtilities.dp(11f)).toFloat()
					sendAnimationData.y = (location[1] + AndroidUtilities.dp((8 + 11).toFloat())).toFloat()
				}

				val updateStickersOrder = SendMessagesHelper.checkUpdateStickersOrder(text)

				SendMessagesHelper.getInstance(currentAccount).sendMessage(message[0].toString(), dialog_id, replyingMessageObject, threadMessage, messageWebPage, isMessageWebPageSearchEnabled, entities, null, null, notify, scheduleDate, sendAnimationData, updateStickersOrder, false, null)

				start = end + 1
			} while (end != text.length)

			return true
		}

		return false
	}

	private fun supportsSendingNewEntities(): Boolean {
		val encryptedChat = parentFragment?.currentEncryptedChat
		return encryptedChat == null || AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 101
	}

	private fun checkSendButton(animated: Boolean) {
		@Suppress("NAME_SHADOWING") var animated = animated

		if (editingMessageObject != null || recordingAudioVideo) {
			return
		}

		if (isPaused) {
			animated = false
		}

		val message = AndroidUtilities.getTrimmedString(editField.text)

		if (isBot) {
			if (message.isNullOrEmpty()) {
				attachAndSettingsContainer.updateLayoutParams<LayoutParams> {
					rightMargin = 0
				}
				sendButtonContainer.gone()
			}
			else {
				attachAndSettingsContainer.updateLayoutParams<LayoutParams> {
					rightMargin = 48.dp
				}
				sendButtonContainer.visible()
				editField.setPadding(editField.paddingLeft, editField.paddingTop, 96.dp, editField.paddingBottom)
			}
		}

		if (slowModeTimer > 0 && slowModeTimer != Int.MAX_VALUE && !isInScheduleMode) {
			if (slowModeButton.visibility != VISIBLE) {
				if (animated) {
					if (runningAnimationType == 5) {
						return
					}

					runningAnimation?.cancel()
					runningAnimation = null

					runningAnimation2?.cancel()
					runningAnimation2 = null

					// START: commented out
					if (attachLayout != null) {
						runningAnimation2 = AnimatorSet()
						val animators = mutableListOf<Animator>()
						// animators.add(ObjectAnimator.ofFloat(attachLayout, View.ALPHA, 0.0f))
						// animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 0.0f))
						scheduleButtonHidden = false
						val hasScheduled = delegate?.hasScheduledMessages() == true

						if (scheduledButton != null) {
							scheduledButton?.scaleY = 1.0f

							if (hasScheduled) {
								scheduledButton?.visibility = VISIBLE
								scheduledButton?.tag = 1
								scheduledButton?.pivotX = AndroidUtilities.dp(48f).toFloat()

								animators.add(ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, AndroidUtilities.dp(if (botButton?.visibility == VISIBLE) 96f else 48f).toFloat()))
								animators.add(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1.0f))
								animators.add(ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, 1.0f))
							}
							else {
								scheduledButton?.translationX = AndroidUtilities.dp(if (botButton?.visibility == VISIBLE) 96f else 48f).toFloat()
								scheduledButton?.alpha = 1.0f
								scheduledButton?.scaleX = 1.0f
							}
						}

						runningAnimation2?.playTogether(animators)
						runningAnimation2?.duration = 100
						runningAnimation2?.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								if (animation == runningAnimation2) {
									// attachLayout?.visibility = GONE
									runningAnimation2 = null
								}
							}

							override fun onAnimationCancel(animation: Animator) {
								if (animation == runningAnimation2) {
									runningAnimation2 = null
								}
							}
						})

						runningAnimation2?.start()

						updateFieldRight(0)

						if (visibility == VISIBLE) {
							delegate?.onAttachButtonHidden()
						}
					}
					// END: commented out

					runningAnimationType = 5
					runningAnimation = AnimatorSet()

					val animators = mutableListOf<Animator>()

					if (audioVideoButtonContainer.visibility == VISIBLE) {
						animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, SCALE_X, 0.1f))
						animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, SCALE_Y, 0.1f))
						animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, ALPHA, 0.0f))
					}

					if (expandStickersButton.visibility == VISIBLE) {
						animators.add(ObjectAnimator.ofFloat(expandStickersButton, SCALE_X, 0.1f))
						animators.add(ObjectAnimator.ofFloat(expandStickersButton, SCALE_Y, 0.1f))
						animators.add(ObjectAnimator.ofFloat(expandStickersButton, ALPHA, 0.0f))
					}

					if (sendButton.visibility == VISIBLE) {
						animators.add(ObjectAnimator.ofFloat(sendButton, SCALE_X, 0.1f))
						animators.add(ObjectAnimator.ofFloat(sendButton, SCALE_Y, 0.1f))
						animators.add(ObjectAnimator.ofFloat(sendButton, ALPHA, 0.0f))
					}

					if (cancelBotButton.visibility == VISIBLE) {
						animators.add(ObjectAnimator.ofFloat(cancelBotButton, SCALE_X, 0.1f))
						animators.add(ObjectAnimator.ofFloat(cancelBotButton, SCALE_Y, 0.1f))
						animators.add(ObjectAnimator.ofFloat(cancelBotButton, ALPHA, 0.0f))
					}

					animators.add(ObjectAnimator.ofFloat(slowModeButton, SCALE_X, 1.0f))
					animators.add(ObjectAnimator.ofFloat(slowModeButton, SCALE_Y, 1.0f))
					animators.add(ObjectAnimator.ofFloat(slowModeButton, ALPHA, 1.0f))

					setSlowModeButtonVisible(true)

					runningAnimation?.playTogether(animators)
					runningAnimation?.duration = 150

					runningAnimation?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							if (animation == runningAnimation) {
								sendButton.visibility = GONE
								cancelBotButton.visibility = GONE
								audioVideoButtonContainer.visibility = GONE
								expandStickersButton.visibility = GONE
								runningAnimation = null
								runningAnimationType = 0
							}
						}

						override fun onAnimationCancel(animation: Animator) {
							if (animation == runningAnimation) {
								runningAnimation = null
							}
						}
					})

					runningAnimation?.start()
				}
				else {
					slowModeButton.scaleX = 1.0f
					slowModeButton.scaleY = 1.0f
					slowModeButton.alpha = 1.0f

					setSlowModeButtonVisible(true)

					audioVideoButtonContainer.scaleX = 0.1f
					audioVideoButtonContainer.scaleY = 0.1f
					audioVideoButtonContainer.alpha = 0.0f
					audioVideoButtonContainer.visibility = GONE

					sendButton.scaleX = 0.1f
					sendButton.scaleY = 0.1f
					sendButton.alpha = 0.0f
					sendButton.visibility = GONE

					cancelBotButton.scaleX = 0.1f
					cancelBotButton.scaleY = 0.1f
					cancelBotButton.alpha = 0.0f
					cancelBotButton.visibility = GONE

					if (expandStickersButton.visibility == VISIBLE) {
						expandStickersButton.scaleX = 0.1f
						expandStickersButton.scaleY = 0.1f
						expandStickersButton.alpha = 0.0f
						expandStickersButton.visibility = GONE
					}

					// START: commented out
					if (attachLayout != null) {
						// attachLayout?.visibility = GONE

						if (visibility == VISIBLE) {
							delegate?.onAttachButtonHidden()
						}

						updateFieldRight(0)
					}
					// END: commented out

					scheduleButtonHidden = false

					if (scheduledButton != null) {
						if (delegate != null && delegate!!.hasScheduledMessages()) {
							scheduledButton?.visibility = VISIBLE
							scheduledButton?.tag = 1
						}

						scheduledButton?.translationX = AndroidUtilities.dp(if (botButton?.visibility == VISIBLE) 96f else 48f).toFloat()
						scheduledButton?.alpha = 1.0f
						scheduledButton?.scaleX = 1.0f
						scheduledButton?.scaleY = 1.0f
					}
				}
			}
		}
		else if (message.isNotEmpty() || forceShowSendButton || audioToSend != null || videoToSendMessageObject != null || slowModeTimer == Int.MAX_VALUE && !isInScheduleMode) {
			val caption = editField.caption
			val showBotButton = caption != null && (sendButton.visibility == VISIBLE || expandStickersButton.visibility == VISIBLE)
			val showSendButton = caption == null && (cancelBotButton.visibility == VISIBLE || expandStickersButton.visibility == VISIBLE)

			val color = if (slowModeTimer == Int.MAX_VALUE && !isInScheduleMode) {
				context.getColor(R.color.dark_gray)
			}
			else {
				context.getColor(R.color.brand)
			}

			Theme.setSelectorDrawableColor(sendButton.background, Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)), true)

			if (audioVideoButtonContainer.visibility == VISIBLE || slowModeButton.visibility == VISIBLE || showBotButton || showSendButton) {
				if (animated) {
					if (runningAnimationType == 1 && editField.caption == null || runningAnimationType == 3 && caption != null) {
						return
					}

					runningAnimation?.cancel()
					runningAnimation = null

					runningAnimation2?.cancel()
					runningAnimation2 = null

					// START: commented out
					if (attachLayout != null) {
						runningAnimation2 = AnimatorSet()
						val animators = mutableListOf<Animator>()
//						animators.add(ObjectAnimator.ofFloat(attachLayout, View.ALPHA, 0.0f))
//						animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 0.0f))

						val hasScheduled = delegate?.hasScheduledMessages() == true

						scheduleButtonHidden = true

						if (scheduledButton != null) {
							scheduledButton?.scaleY = 1.0f

							if (hasScheduled) {
								scheduledButton?.tag = null
								animators.add(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 0.0f))
								animators.add(ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, 0.0f))
								animators.add(ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, AndroidUtilities.dp(if (botButton == null || botButton?.visibility == GONE) 48f else 96f).toFloat()))
							}
							else {
								scheduledButton?.alpha = 0.0f
								scheduledButton?.scaleX = 0.0f
								scheduledButton?.translationX = AndroidUtilities.dp(if (botButton == null || botButton?.visibility == GONE) 48f else 96f).toFloat()
							}
						}

						runningAnimation2?.playTogether(animators)
						runningAnimation2?.duration = 100

						runningAnimation2?.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								if (animation == runningAnimation2) {
									// attachLayout?.visibility = GONE

									if (hasScheduled) {
										scheduledButton?.visibility = GONE
									}

									runningAnimation2 = null
								}
							}

							override fun onAnimationCancel(animation: Animator) {
								if (animation == runningAnimation2) {
									runningAnimation2 = null
								}
							}
						})

						runningAnimation2?.start()

						updateFieldRight(0)

						if (visibility == VISIBLE) {
							delegate?.onAttachButtonHidden()
						}
					}
					// END: commented out

					runningAnimation = AnimatorSet()

					val animators = mutableListOf<Animator>()

					if (audioVideoButtonContainer.visibility == VISIBLE) {
						animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, SCALE_X, 0.1f))
						animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, SCALE_Y, 0.1f))
						animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, ALPHA, 0.0f))
					}

					if (expandStickersButton.visibility == VISIBLE) {
						animators.add(ObjectAnimator.ofFloat(expandStickersButton, SCALE_X, 0.1f))
						animators.add(ObjectAnimator.ofFloat(expandStickersButton, SCALE_Y, 0.1f))
						animators.add(ObjectAnimator.ofFloat(expandStickersButton, ALPHA, 0.0f))
					}

					if (slowModeButton.visibility == VISIBLE) {
						animators.add(ObjectAnimator.ofFloat(slowModeButton, SCALE_X, 0.1f))
						animators.add(ObjectAnimator.ofFloat(slowModeButton, SCALE_Y, 0.1f))
						animators.add(ObjectAnimator.ofFloat(slowModeButton, ALPHA, 0.0f))
					}

					if (showBotButton) {
						animators.add(ObjectAnimator.ofFloat(sendButton, SCALE_X, 0.1f))
						animators.add(ObjectAnimator.ofFloat(sendButton, SCALE_Y, 0.1f))
						animators.add(ObjectAnimator.ofFloat(sendButton, ALPHA, 0.0f))
					}
					else if (showSendButton) {
						animators.add(ObjectAnimator.ofFloat(cancelBotButton, SCALE_X, 0.1f))
						animators.add(ObjectAnimator.ofFloat(cancelBotButton, SCALE_Y, 0.1f))
						animators.add(ObjectAnimator.ofFloat(cancelBotButton, ALPHA, 0.0f))
					}

					if (caption != null) {
						runningAnimationType = 3

						animators.add(ObjectAnimator.ofFloat(cancelBotButton, SCALE_X, 1.0f))
						animators.add(ObjectAnimator.ofFloat(cancelBotButton, SCALE_Y, 1.0f))
						animators.add(ObjectAnimator.ofFloat(cancelBotButton, ALPHA, 1.0f))

						cancelBotButton.visibility = VISIBLE
					}
					else {
						runningAnimationType = 1

						animators.add(ObjectAnimator.ofFloat(sendButton, SCALE_X, 1.0f))
						animators.add(ObjectAnimator.ofFloat(sendButton, SCALE_Y, 1.0f))
						animators.add(ObjectAnimator.ofFloat(sendButton, ALPHA, 1.0f))

						sendButton.visibility = VISIBLE
					}

					runningAnimation?.playTogether(animators)
					runningAnimation?.duration = 150

					runningAnimation?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							if (animation == runningAnimation) {
								if (caption != null) {
									cancelBotButton.visibility = VISIBLE
									sendButton.visibility = GONE
								}
								else {
									sendButton.visibility = VISIBLE
									cancelBotButton.visibility = GONE
								}

								audioVideoButtonContainer.visibility = GONE
								expandStickersButton.visibility = GONE

								setSlowModeButtonVisible(false)

								runningAnimation = null
								runningAnimationType = 0
							}
						}

						override fun onAnimationCancel(animation: Animator) {
							if (animation == runningAnimation) {
								runningAnimation = null
							}
						}
					})

					runningAnimation?.start()
				}
				else {
					audioVideoButtonContainer.scaleX = 0.1f
					audioVideoButtonContainer.scaleY = 0.1f
					audioVideoButtonContainer.alpha = 0.0f
					audioVideoButtonContainer.visibility = GONE

					if (slowModeButton.visibility == VISIBLE) {
						slowModeButton.scaleX = 0.1f
						slowModeButton.scaleY = 0.1f
						slowModeButton.alpha = 0.0f

						setSlowModeButtonVisible(false)
					}

					if (caption != null) {
						sendButton.scaleX = 0.1f
						sendButton.scaleY = 0.1f
						sendButton.alpha = 0.0f
						sendButton.visibility = GONE

						cancelBotButton.scaleX = 1.0f
						cancelBotButton.scaleY = 1.0f
						cancelBotButton.alpha = 1.0f
						cancelBotButton.visibility = VISIBLE
					}
					else {
						cancelBotButton.scaleX = 0.1f
						cancelBotButton.scaleY = 0.1f
						cancelBotButton.alpha = 0.0f

						sendButton.visibility = VISIBLE
						sendButton.scaleX = 1.0f
						sendButton.scaleY = 1.0f
						sendButton.alpha = 1.0f

						cancelBotButton.visibility = GONE
					}

					if (expandStickersButton.visibility == VISIBLE) {
						expandStickersButton.scaleX = 0.1f
						expandStickersButton.scaleY = 0.1f
						expandStickersButton.alpha = 0.0f
						expandStickersButton.visibility = GONE
					}

					// START: commented out
					if (attachLayout != null) {
						// attachLayout?.visibility = GONE

						if (visibility == VISIBLE) {
							delegate?.onAttachButtonHidden()
						}

						updateFieldRight(0)
					}
					// END: commented out

					scheduleButtonHidden = true

					if (scheduledButton != null) {
						if (delegate?.hasScheduledMessages() == true) {
							scheduledButton?.visibility = GONE
							scheduledButton?.tag = null
						}

						scheduledButton?.alpha = 0.0f
						scheduledButton?.scaleX = 0.0f
						scheduledButton?.scaleY = 1.0f
						scheduledButton?.translationX = AndroidUtilities.dp(if (botButton == null || botButton?.visibility == GONE) 48f else 96f).toFloat()
					}
				}
			}
		}
		else if (emojiView != null && emojiViewVisible && (stickersTabOpen || emojiTabOpen && searchingType == 2) && !AndroidUtilities.isInMultiwindow) {
			if (animated) {
				if (runningAnimationType == 4) {
					return
				}

				runningAnimation?.cancel()
				runningAnimation = null

				runningAnimation2?.cancel()
				runningAnimation2 = null

				// START: commented out
				if (attachLayout != null && recordInterfaceState == 0) {
					attachLayout?.visibility = VISIBLE

					runningAnimation2 = AnimatorSet()

					val animators = mutableListOf<Animator>()
					animators.add(ObjectAnimator.ofFloat(attachLayout, View.ALPHA, 1.0f))
					animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 1.0f))

					val hasScheduled = delegate?.hasScheduledMessages() == true

					scheduleButtonHidden = false

					if (scheduledButton != null) {
						scheduledButton?.scaleY = 1.0f

						if (hasScheduled) {
							scheduledButton?.visibility = VISIBLE
							scheduledButton?.tag = 1
							scheduledButton?.pivotX = AndroidUtilities.dp(48f).toFloat()
							animators.add(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1.0f))
							animators.add(ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, 1.0f))
							animators.add(ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, 0f))
						}
						else {
							scheduledButton?.alpha = 1.0f
							scheduledButton?.scaleX = 1.0f
							scheduledButton?.translationX = 0f
						}
					}

					runningAnimation2?.playTogether(animators)
					runningAnimation2?.duration = 100
					runningAnimation2?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							if (animation == runningAnimation2) {
								runningAnimation2 = null
							}
						}

						override fun onAnimationCancel(animation: Animator) {
							if (animation == runningAnimation2) {
								runningAnimation2 = null
							}
						}
					})

					runningAnimation2?.start()

					updateFieldRight(1)

					if (visibility == VISIBLE) {
						delegate?.onAttachButtonShow()
					}
				}
				// END: commented out

				expandStickersButton.visibility = VISIBLE

				runningAnimation = AnimatorSet()

				runningAnimationType = 4

				val animators = mutableListOf<Animator>()

				animators.add(ObjectAnimator.ofFloat(expandStickersButton, SCALE_X, 1.0f))
				animators.add(ObjectAnimator.ofFloat(expandStickersButton, SCALE_Y, 1.0f))
				animators.add(ObjectAnimator.ofFloat(expandStickersButton, ALPHA, 1.0f))

				if (cancelBotButton.visibility == VISIBLE) {
					animators.add(ObjectAnimator.ofFloat(cancelBotButton, SCALE_X, 0.1f))
					animators.add(ObjectAnimator.ofFloat(cancelBotButton, SCALE_Y, 0.1f))
					animators.add(ObjectAnimator.ofFloat(cancelBotButton, ALPHA, 0.0f))
				}
				else if (audioVideoButtonContainer.visibility == VISIBLE) {
					animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, SCALE_X, 0.1f))
					animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, SCALE_Y, 0.1f))
					animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, ALPHA, 0.0f))
				}
				else if (slowModeButton.visibility == VISIBLE) {
					animators.add(ObjectAnimator.ofFloat(slowModeButton, SCALE_X, 0.1f))
					animators.add(ObjectAnimator.ofFloat(slowModeButton, SCALE_Y, 0.1f))
					animators.add(ObjectAnimator.ofFloat(slowModeButton, ALPHA, 0.0f))
				}
				else {
					animators.add(ObjectAnimator.ofFloat(sendButton, SCALE_X, 0.1f))
					animators.add(ObjectAnimator.ofFloat(sendButton, SCALE_Y, 0.1f))
					animators.add(ObjectAnimator.ofFloat(sendButton, ALPHA, 0.0f))
				}

				runningAnimation?.playTogether(animators)
				runningAnimation?.duration = AdjustPanLayoutHelper.keyboardDuration

				runningAnimation?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (animation == runningAnimation) {
							sendButton.visibility = GONE
							cancelBotButton.visibility = GONE

							setSlowModeButtonVisible(false)

							audioVideoButtonContainer.visibility = GONE
							expandStickersButton.visibility = VISIBLE
							runningAnimation = null
							runningAnimationType = 0
						}
					}

					override fun onAnimationCancel(animation: Animator) {
						if (animation == runningAnimation) {
							runningAnimation = null
						}
					}
				})

				runningAnimation?.start()
			}
			else {
				slowModeButton.scaleX = 0.1f
				slowModeButton.scaleY = 0.1f
				slowModeButton.alpha = 0.0f

				setSlowModeButtonVisible(false)

				sendButton.scaleX = 0.1f
				sendButton.scaleY = 0.1f
				sendButton.alpha = 0.0f
				sendButton.visibility = GONE

				cancelBotButton.scaleX = 0.1f
				cancelBotButton.scaleY = 0.1f
				cancelBotButton.alpha = 0.0f
				cancelBotButton.visibility = GONE

				audioVideoButtonContainer.scaleX = 0.1f
				audioVideoButtonContainer.scaleY = 0.1f
				audioVideoButtonContainer.alpha = 0.0f
				audioVideoButtonContainer.visibility = GONE

				expandStickersButton.scaleX = 1.0f
				expandStickersButton.scaleY = 1.0f
				expandStickersButton.alpha = 1.0f
				expandStickersButton.visibility = VISIBLE

				// START: commented out
				if (attachLayout != null) {
					if (visibility == VISIBLE) {
						delegate?.onAttachButtonShow()
					}

					attachLayout?.visibility = VISIBLE
					updateFieldRight(1)
				}
				// END: commented out

				scheduleButtonHidden = false

				if (scheduledButton != null) {
					if (delegate?.hasScheduledMessages() == true) {
						scheduledButton?.visibility = VISIBLE
						scheduledButton?.tag = 1
					}

					scheduledButton?.alpha = 1.0f
					scheduledButton?.scaleX = 1.0f
					scheduledButton?.scaleY = 1.0f
					scheduledButton?.translationX = 0f
				}
			}
		}
		else if (sendButton.visibility == VISIBLE || cancelBotButton.visibility == VISIBLE || expandStickersButton.visibility == VISIBLE || slowModeButton.visibility == VISIBLE) {
			if (animated) {
				if (runningAnimationType == 2) {
					return
				}

				runningAnimation?.cancel()
				runningAnimation = null

				runningAnimation2?.cancel()
				runningAnimation2 = null

				// START: commented out
				if (attachLayout != null) {
					if (attachLayout?.visibility != View.VISIBLE) {
						attachLayout?.visibility = VISIBLE
						attachLayout?.alpha = 0f
						attachLayout?.scaleX = 0f
					}

					runningAnimation2 = AnimatorSet()

					val animators = mutableListOf<Animator>()
					animators.add(ObjectAnimator.ofFloat(attachLayout, View.ALPHA, 1.0f))
					animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 1.0f))

					val hasScheduled = delegate?.hasScheduledMessages() == true

					scheduleButtonHidden = false

					if (scheduledButton != null) {
						if (hasScheduled) {
							scheduledButton?.visibility = VISIBLE
							scheduledButton?.tag = 1
							scheduledButton?.pivotX = AndroidUtilities.dp(48f).toFloat()
							animators.add(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1.0f))
							animators.add(ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, 1.0f))
							animators.add(ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, 0f))
						}
						else {
							scheduledButton?.alpha = 1.0f
							scheduledButton?.scaleX = 1.0f
							scheduledButton?.scaleY = 1.0f
							scheduledButton?.translationX = 0f
						}
					}

					runningAnimation2?.playTogether(animators)
					runningAnimation2?.duration = 100
					runningAnimation2?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							if (animation == runningAnimation2) {
								runningAnimation2 = null
							}
						}

						override fun onAnimationCancel(animation: Animator) {
							if (animation == runningAnimation2) {
								runningAnimation2 = null
							}
						}
					})

					runningAnimation2?.start()

					updateFieldRight(1)

					if (visibility == VISIBLE) {
						delegate?.onAttachButtonShow()
					}
				}
				// END: commented out

				audioVideoButtonContainer.visibility = VISIBLE

				runningAnimation = AnimatorSet()

				runningAnimationType = 2

				val animators = mutableListOf<Animator>()

				animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, SCALE_X, 1.0f))
				animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, SCALE_Y, 1.0f))

				var alpha = 1.0f
				val chat = parentFragment?.currentChat
				val userFull = parentFragment?.currentUserInfo

				if (chat != null) {
					alpha = if (ChatObject.canSendMedia(chat)) 1.0f else 0.5f
				}
				else if (userFull != null) {
					alpha = if (userFull.voice_messages_forbidden) 0.5f else 1.0f
				}

				animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, ALPHA, alpha))

				if (cancelBotButton.visibility == VISIBLE) {
					animators.add(ObjectAnimator.ofFloat(cancelBotButton, SCALE_X, 0.1f))
					animators.add(ObjectAnimator.ofFloat(cancelBotButton, SCALE_Y, 0.1f))
					animators.add(ObjectAnimator.ofFloat(cancelBotButton, ALPHA, 0.0f))
				}
				else if (expandStickersButton.visibility == VISIBLE) {
					animators.add(ObjectAnimator.ofFloat(expandStickersButton, SCALE_X, 0.1f))
					animators.add(ObjectAnimator.ofFloat(expandStickersButton, SCALE_Y, 0.1f))
					animators.add(ObjectAnimator.ofFloat(expandStickersButton, ALPHA, 0.0f))
				}
				else if (slowModeButton.visibility == VISIBLE) {
					animators.add(ObjectAnimator.ofFloat(slowModeButton, SCALE_X, 0.1f))
					animators.add(ObjectAnimator.ofFloat(slowModeButton, SCALE_Y, 0.1f))
					animators.add(ObjectAnimator.ofFloat(slowModeButton, ALPHA, 0.0f))
				}
				else {
					animators.add(ObjectAnimator.ofFloat(sendButton, SCALE_X, 0.1f))
					animators.add(ObjectAnimator.ofFloat(sendButton, SCALE_Y, 0.1f))
					animators.add(ObjectAnimator.ofFloat(sendButton, ALPHA, 0.0f))
				}

				runningAnimation?.playTogether(animators)
				runningAnimation?.duration = 150

				runningAnimation?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (animation == runningAnimation) {
							setSlowModeButtonVisible(false)

							runningAnimation = null
							runningAnimationType = 0
							audioVideoButtonContainer.visibility = VISIBLE
						}
					}

					override fun onAnimationCancel(animation: Animator) {
						if (animation == runningAnimation) {
							runningAnimation = null
						}
					}
				})

				runningAnimation?.start()
			}
			else {
				slowModeButton.scaleX = 0.1f
				slowModeButton.scaleY = 0.1f
				slowModeButton.alpha = 0.0f

				setSlowModeButtonVisible(false)

				sendButton.scaleX = 0.1f
				sendButton.scaleY = 0.1f
				sendButton.alpha = 0.0f
				sendButton.visibility = GONE

				cancelBotButton.scaleX = 0.1f
				cancelBotButton.scaleY = 0.1f
				cancelBotButton.alpha = 0.0f
				cancelBotButton.visibility = GONE

				expandStickersButton.scaleX = 0.1f
				expandStickersButton.scaleY = 0.1f
				expandStickersButton.alpha = 0.0f
				expandStickersButton.visibility = GONE

				audioVideoButtonContainer.scaleX = 1.0f
				audioVideoButtonContainer.scaleY = 1.0f
				audioVideoButtonContainer.alpha = 1.0f
				audioVideoButtonContainer.visibility = VISIBLE

				// START: commented out
				if (attachLayout != null) {
					if (visibility == VISIBLE) {
						delegate?.onAttachButtonShow()
					}

					attachLayout?.alpha = 1.0f
					attachLayout?.scaleX = 1.0f
					attachLayout?.visibility = VISIBLE

					updateFieldRight(1)
				}
				// END: commented out

				scheduleButtonHidden = false

				if (scheduledButton != null) {
					if (delegate?.hasScheduledMessages() == true) {
						scheduledButton?.visibility = VISIBLE
						scheduledButton?.tag = 1
					}

					scheduledButton?.alpha = 1.0f
					scheduledButton?.scaleX = 1.0f
					scheduledButton?.scaleY = 1.0f
					scheduledButton?.translationX = 0f
				}
			}
		}
	}

	private fun setSlowModeButtonVisible(visible: Boolean) {
		slowModeButton.visibility = if (visible) VISIBLE else GONE

		val padding = if (visible) AndroidUtilities.dp(16f) else 0
		val emojiButtonPaddingIncrease = AndroidUtilities.dp(48f)
		val notifyButtonPaddingIncrease = if (notifyButton?.visibility == VISIBLE) AndroidUtilities.dp(48f) else 0
		val createMediaButtonPaddingIncrease = if (createMediaButton.visibility == VISIBLE) AndroidUtilities.dp(48f) else 0

		editField.setPadding(AndroidUtilities.dp(0f), AndroidUtilities.dp(11f), padding + emojiButtonPaddingIncrease + notifyButtonPaddingIncrease + createMediaButtonPaddingIncrease, AndroidUtilities.dp(12f))
	}

	private fun updateFieldRight(attachVisible: Int) {
		if (editingMessageObject != null) {
			return
		}

		editField.updateLayoutParams<LayoutParams> {
			when (attachVisible) {
				1 -> {
					rightMargin = if (botButton != null && botButton?.visibility == VISIBLE && scheduledButton != null && scheduledButton?.visibility == VISIBLE && attachLayout != null && attachLayout?.visibility == VISIBLE) {
						AndroidUtilities.dp(146f)
					}
					else if ((botButton != null && botButton?.visibility == VISIBLE) || scheduledButton != null && scheduledButton?.tag != null) {
						AndroidUtilities.dp(98f)
					}
					else {
						AndroidUtilities.dp(messageViewRightMargin.toFloat())
					}
				}

				2 -> {
					if (rightMargin != AndroidUtilities.dp(2f)) {
						rightMargin = if (botButton != null && botButton?.visibility == VISIBLE && scheduledButton != null && scheduledButton?.visibility == VISIBLE && attachLayout != null && attachLayout?.visibility == VISIBLE) {
							AndroidUtilities.dp(146f)
						}
						else if ((botButton != null && botButton?.visibility == VISIBLE) || scheduledButton != null && scheduledButton?.tag != null) {
							AndroidUtilities.dp(98f)
						}
						else {
							AndroidUtilities.dp(messageViewRightMargin.toFloat())
						}
					}
				}

				else -> {
					rightMargin = if (scheduledButton != null && scheduledButton?.tag != null) {
						AndroidUtilities.dp(messageViewRightMargin.toFloat())
					}
					else {
						AndroidUtilities.dp(2f)
					}
				}
			}
		}
	}

	fun startMessageTransition() {
		if (moveToSendStateRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(moveToSendStateRunnable)

			messageTransitionIsRunning = true

			moveToSendStateRunnable?.run()
			moveToSendStateRunnable = null
		}
	}

	fun canShowMessageTransition(): Boolean {
		return moveToSendStateRunnable != null
	}

	private fun updateRecordInterface(recordState: Int) {
		if (moveToSendStateRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(moveToSendStateRunnable)
			moveToSendStateRunnable = null
		}

		recordCircle.voiceEnterTransitionInProgress = false

		if (recordingAudioVideo) {
			if (recordInterfaceState == 1) {
				return
			}

			recordInterfaceState = 1

			emojiView?.isEnabled = false

			try {
				if (wakeLock == null) {
					val pm = ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
					wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, "elloapp:audio_record_lock")
					wakeLock?.acquire(10_000L)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			AndroidUtilities.lockOrientation(parentActivity)

			delegate?.needStartRecordAudio(0)

			runningAnimationAudio?.cancel()
			recordPanelAnimation?.cancel()

			recordPanel.visibility = VISIBLE

			recordCircle.visibility = VISIBLE
			recordCircle.setAmplitude(0.0)

			recordDot.resetAlpha()

			runningAnimationAudio = AnimatorSet()

			recordDot.scaleX = 0f
			recordDot.scaleY = 0f
			recordDot.enterAnimation = true

			recordTimerView.translationX = AndroidUtilities.dp(20f).toFloat()
			recordTimerView.alpha = 0f

			slideText.translationX = AndroidUtilities.dp(20f).toFloat()
			slideText.alpha = 0f
			slideText.cancelToProgress = 0f
			slideText.setSlideX(1f)

			recordCircle.setLockTranslation(10000f)

			slideText.isEnabled = true

			recordIsCanceled = false

			//ENTER TRANSITION
			val iconChanges = AnimatorSet()

			iconChanges.playTogether(
					ObjectAnimator.ofFloat(emojiButton, SCALE_Y, 0f),
					ObjectAnimator.ofFloat(emojiButton, SCALE_X, 0f),
					ObjectAnimator.ofFloat(emojiButton, ALPHA, 0f),
					ObjectAnimator.ofFloat(recordDot, SCALE_Y, 1f),
					ObjectAnimator.ofFloat(recordDot, SCALE_X, 1f),
					ObjectAnimator.ofFloat(recordTimerView, TRANSLATION_X, 0f),
					ObjectAnimator.ofFloat(recordTimerView, ALPHA, 1f),
					ObjectAnimator.ofFloat(slideText, TRANSLATION_X, 0f),
					ObjectAnimator.ofFloat(slideText, ALPHA, 1f),
					ObjectAnimator.ofFloat(createMediaButton, ALPHA, 0f),
					ObjectAnimator.ofFloat(createMediaButton, SCALE_X, 0f),
					ObjectAnimator.ofFloat(createMediaButton, SCALE_Y, 0f),
			)

			notifyButton?.let {
				iconChanges.playTogether(
						ObjectAnimator.ofFloat(it, SCALE_Y, 0f),
						ObjectAnimator.ofFloat(it, SCALE_X, 0f),
						ObjectAnimator.ofFloat(it, ALPHA, 0f),
				)
			}

			iconChanges.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, ALPHA, 0f))

			if (botCommandsMenuButton != null) {
				iconChanges.playTogether(ObjectAnimator.ofFloat(botCommandsMenuButton, SCALE_Y, 0f), ObjectAnimator.ofFloat(botCommandsMenuButton, SCALE_X, 0f), ObjectAnimator.ofFloat(botCommandsMenuButton, ALPHA, 0f))
			}

			val viewTransition = AnimatorSet()

			viewTransition.playTogether(ObjectAnimator.ofFloat(editField, TRANSLATION_X, AndroidUtilities.dp(20f).toFloat()), ObjectAnimator.ofFloat(editField, ALPHA, 0f), ObjectAnimator.ofFloat(recordedAudioPanel, ALPHA, 1f))

			scheduledButton?.let {
				viewTransition.playTogether(ObjectAnimator.ofFloat(it, TRANSLATION_X, AndroidUtilities.dp(30f).toFloat()), ObjectAnimator.ofFloat(it, ALPHA, 0f))
			}

			attachLayout?.let {
				viewTransition.playTogether(ObjectAnimator.ofFloat(it, TRANSLATION_X, AndroidUtilities.dp(30f).toFloat()), ObjectAnimator.ofFloat(it, ALPHA, 0f))
			}

			runningAnimationAudio?.playTogether(iconChanges.setDuration(150), viewTransition.setDuration(150), ObjectAnimator.ofFloat(recordCircle, recordCircleScale, 1f).setDuration(300))

			runningAnimationAudio?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animator: Animator) {
					if (animator == runningAnimationAudio) {
						runningAnimationAudio = null
					}

					slideText.alpha = 1f
					slideText.translationX = 0f

					recordCircle.showTooltipIfNeed()

					editField.alpha = 0f
				}
			})

			runningAnimationAudio?.interpolator = DecelerateInterpolator()
			runningAnimationAudio?.start()

			recordTimerView.start()
		}
		else {
			if (recordIsCanceled && recordState == RECORD_STATE_PREPARING) {
				return
			}

			try {
				wakeLock?.release()
				wakeLock = null
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			AndroidUtilities.unlockOrientation(parentActivity)

			wasSendTyping = false

			if (recordInterfaceState == 0) {
				return
			}

			accountInstance.messagesController.sendTyping(dialog_id, threadMessageId, 2, 0)

			recordInterfaceState = 0
			emojiView?.isEnabled = true

			var shouldShowFastTransition = false

			if (runningAnimationAudio != null) {
				shouldShowFastTransition = runningAnimationAudio!!.isRunning

				audioVideoSendButton.scaleX = 1f
				audioVideoSendButton.scaleY = 1f

				runningAnimationAudio?.removeAllListeners()
				runningAnimationAudio?.cancel()
			}

			recordPanelAnimation?.cancel()

			editField.visibility = VISIBLE

			runningAnimationAudio = AnimatorSet()

			//EXIT TRANSITION
			if (shouldShowFastTransition || recordState == RECORD_STATE_CANCEL_BY_TIME) {
				audioVideoSendButton.visibility = VISIBLE

				runningAnimationAudio?.playTogether(
						ObjectAnimator.ofFloat(emojiButton, SCALE_Y, 1f),
						ObjectAnimator.ofFloat(emojiButton, SCALE_X, 1f),
						ObjectAnimator.ofFloat(emojiButton, ALPHA, 1f),
						ObjectAnimator.ofFloat(recordDot, SCALE_Y, 0f),
						ObjectAnimator.ofFloat(recordDot, SCALE_X, 0f),
						ObjectAnimator.ofFloat(recordCircle, recordCircleScale, 0.0f),
						ObjectAnimator.ofFloat(audioVideoButtonContainer, ALPHA, 1.0f),
						ObjectAnimator.ofFloat(recordTimerView, ALPHA, 0.0f),
						ObjectAnimator.ofFloat(recordCircle, recordCircleScale, 0.0f),
						ObjectAnimator.ofFloat(audioVideoButtonContainer, ALPHA, 1.0f),
						ObjectAnimator.ofFloat(editField, ALPHA, 1f),
						ObjectAnimator.ofFloat(editField, TRANSLATION_X, 0f),
						ObjectAnimator.ofFloat(recordCircle, "slideToCancelProgress", 1f),
						ObjectAnimator.ofFloat(createMediaButton, SCALE_Y, 1f),
						ObjectAnimator.ofFloat(createMediaButton, SCALE_X, 1f),
						ObjectAnimator.ofFloat(createMediaButton, ALPHA, 1f),
				)

				notifyButton?.let {
					runningAnimationAudio?.playTogether(
							ObjectAnimator.ofFloat(it, SCALE_Y, 1f),
							ObjectAnimator.ofFloat(it, SCALE_X, 1f),
							ObjectAnimator.ofFloat(it, ALPHA, 1f),
					)
				}

				botCommandsMenuButton?.let {
					runningAnimationAudio?.playTogether(ObjectAnimator.ofFloat(it, SCALE_Y, 1f), ObjectAnimator.ofFloat(it, SCALE_X, 1f), ObjectAnimator.ofFloat(it, ALPHA, 1f))
				}

				audioVideoSendButton.scaleX = 1f
				audioVideoSendButton.scaleY = 1f

				runningAnimationAudio?.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, ALPHA, 1f))

				audioVideoSendButton.setState(if (isInVideoMode) ChatActivityEnterViewAnimatedIconView.State.VIDEO else ChatActivityEnterViewAnimatedIconView.State.VOICE, true)

				scheduledButton?.let {
					runningAnimationAudio?.playTogether(ObjectAnimator.ofFloat(it, TRANSLATION_X, 0f), ObjectAnimator.ofFloat(it, ALPHA, 1f))
				}

				attachLayout?.let {
					runningAnimationAudio?.playTogether(ObjectAnimator.ofFloat(it, TRANSLATION_X, 0f), ObjectAnimator.ofFloat(it, ALPHA, 1f))
				}

				recordIsCanceled = true
				runningAnimationAudio?.duration = 150
			}
			else if (recordState == RECORD_STATE_PREPARING) {
				slideText.isEnabled = false

				if (isInVideoMode) {
					recordedAudioBackground.visibility = GONE
					recordedAudioTimeTextView.visibility = GONE
					recordedAudioPlayButton.visibility = GONE
					recordedAudioSeekBar.visibility = GONE
					recordedAudioPanel.alpha = 1.0f
					recordedAudioPanel.visibility = VISIBLE
					// recordDeleteImageView.setProgress(0f)
					// recordDeleteImageView.stopAnimation()
				}
				else {
					videoTimelineView.visibility = GONE
					recordedAudioBackground.visibility = VISIBLE
					recordedAudioTimeTextView.visibility = VISIBLE
					recordedAudioPlayButton.visibility = VISIBLE
					recordedAudioSeekBar.visibility = VISIBLE
					recordedAudioPanel.alpha = 1.0f
					recordedAudioBackground.alpha = 0f
					recordedAudioTimeTextView.alpha = 0f
					recordedAudioPlayButton.alpha = 0f
					recordedAudioSeekBar.alpha = 0f
					recordedAudioPanel.visibility = VISIBLE
				}

				recordDeleteImageView.alpha = 0f
				recordDeleteImageView.scaleX = 0f
				recordDeleteImageView.scaleY = 0f
				// recordDeleteImageView.setProgress(0f)
				// recordDeleteImageView.stopAnimation()

				val transformToSeekbar = ValueAnimator.ofFloat(0f, 1f)

				transformToSeekbar.addUpdateListener {
					val value = it.animatedValue as Float

					if (!isInVideoMode) {
						recordCircle.setTransformToSeekbar(value)
						seekBarWaveform?.setWaveScaling(recordCircle.transformToSeekbarProgressStep3)
						recordedAudioSeekBar.invalidate()
						recordedAudioTimeTextView.alpha = recordCircle.transformToSeekbarProgressStep3
						recordedAudioPlayButton.alpha = recordCircle.transformToSeekbarProgressStep3
						recordedAudioPlayButton.scaleX = recordCircle.transformToSeekbarProgressStep3
						recordedAudioPlayButton.scaleY = recordCircle.transformToSeekbarProgressStep3
						recordedAudioSeekBar.alpha = recordCircle.transformToSeekbarProgressStep3
					}
					else {
						recordCircle.setExitTransition(value)
					}
				}

				var oldLayoutParams: ViewGroup.LayoutParams? = null
				var parent: ViewGroup? = null

				if (!isInVideoMode) {
					parent = recordedAudioPanel.parent as? ViewGroup
					oldLayoutParams = recordedAudioPanel.layoutParams
					parent?.removeView(recordedAudioPanel)

					val newLayoutParams = LayoutParams(parent?.measuredWidth ?: 0, AndroidUtilities.dp(48f))
					newLayoutParams.gravity = Gravity.BOTTOM
					newLayoutParams.bottomMargin = AndroidUtilities.dp(4f)

					sizeNotifierLayout?.addView(recordedAudioPanel, newLayoutParams)

					videoTimelineView.visibility = GONE
				}
				else {
					videoTimelineView.visibility = VISIBLE
				}

				recordDeleteImageView.alpha = 0f
				recordDeleteImageView.scaleX = 0f
				recordDeleteImageView.scaleY = 0f

				val iconsAnimator = AnimatorSet()

				iconsAnimator.playTogether(
						ObjectAnimator.ofFloat(recordDot, SCALE_Y, 0f),
						ObjectAnimator.ofFloat(recordDot, SCALE_X, 0f),
						ObjectAnimator.ofFloat(recordTimerView, ALPHA, 0.0f),
						ObjectAnimator.ofFloat(recordTimerView, TRANSLATION_X, -AndroidUtilities.dp(20f).toFloat()),
						ObjectAnimator.ofFloat(slideText, ALPHA, 0f),
						ObjectAnimator.ofFloat(recordDeleteImageView, ALPHA, 1f),
						ObjectAnimator.ofFloat(recordDeleteImageView, SCALE_Y, 1f),
						ObjectAnimator.ofFloat(recordDeleteImageView, SCALE_X, 1f),
						ObjectAnimator.ofFloat(emojiButton, SCALE_Y, 0f),
						ObjectAnimator.ofFloat(emojiButton, SCALE_X, 0f),
						ObjectAnimator.ofFloat(emojiButton, ALPHA, 0f),
						ObjectAnimator.ofFloat(editField, ALPHA, 0f),
						ObjectAnimator.ofFloat(createMediaButton, ALPHA, 0f),
						ObjectAnimator.ofFloat(createMediaButton, SCALE_X, 0f),
						ObjectAnimator.ofFloat(createMediaButton, SCALE_Y, 0f),
				)

				notifyButton?.let {
					iconsAnimator.playTogether(
							ObjectAnimator.ofFloat(it, SCALE_Y, 0f),
							ObjectAnimator.ofFloat(it, SCALE_X, 0f),
							ObjectAnimator.ofFloat(it, ALPHA, 0f),
					)
				}

				iconsAnimator.playTogether(
						ObjectAnimator.ofFloat(audioVideoSendButton, ALPHA, 1f),
						ObjectAnimator.ofFloat(audioVideoSendButton, SCALE_X, 1f),
						ObjectAnimator.ofFloat(audioVideoSendButton, SCALE_Y, 1f),
				)

				audioVideoSendButton.setState(if (isInVideoMode) ChatActivityEnterViewAnimatedIconView.State.VIDEO else ChatActivityEnterViewAnimatedIconView.State.VOICE, true)

				botCommandsMenuButton?.let {
					iconsAnimator.playTogether(ObjectAnimator.ofFloat(it, ALPHA, 0f), ObjectAnimator.ofFloat(it, SCALE_X, 0f), ObjectAnimator.ofFloat(it, SCALE_Y, 0f))
				}

				iconsAnimator.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						audioVideoSendButton.scaleX = 1f
						audioVideoSendButton.scaleY = 1f
					}
				})

				iconsAnimator.duration = 150
				iconsAnimator.startDelay = 150

				val videoAdditionalAnimations = AnimatorSet()

				if (isInVideoMode) {
					recordedAudioTimeTextView.alpha = 0f
					videoTimelineView.alpha = 0f

					videoAdditionalAnimations.playTogether(ObjectAnimator.ofFloat(recordedAudioTimeTextView, ALPHA, 1f), ObjectAnimator.ofFloat(videoTimelineView, ALPHA, 1f))
					videoAdditionalAnimations.duration = 150
					videoAdditionalAnimations.startDelay = 430
				}

				transformToSeekbar.duration = if (isInVideoMode) 490 else 580.toLong()

				runningAnimationAudio?.playTogether(iconsAnimator, transformToSeekbar, videoAdditionalAnimations)

				val finalParent = parent
				val finalOldLayoutParams = oldLayoutParams

				runningAnimationAudio?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (finalParent != null) {
							sizeNotifierLayout?.removeView(recordedAudioPanel)
							finalParent.addView(recordedAudioPanel, finalOldLayoutParams)
						}

						recordedAudioPanel.alpha = 1.0f
						recordedAudioBackground.alpha = 1f
						recordedAudioTimeTextView.alpha = 1f
						recordedAudioPlayButton.alpha = 1f
						recordedAudioPlayButton.scaleY = 1f
						recordedAudioPlayButton.scaleX = 1f
						recordedAudioSeekBar.alpha = 1f

						emojiButton.scaleY = 0f
						emojiButton.scaleX = 0f
						emojiButton.alpha = 0f

						botCommandsMenuButton?.alpha = 0f
						botCommandsMenuButton?.scaleX = 0f
						botCommandsMenuButton?.scaleY = 0f
					}
				})
			}
			else if (recordState == RECORD_STATE_CANCEL || recordState == RECORD_STATE_CANCEL_BY_GESTURE) {
				audioVideoSendButton.visibility = VISIBLE
				recordIsCanceled = true

				val iconsAnimator = AnimatorSet()

				iconsAnimator.playTogether(
						ObjectAnimator.ofFloat(emojiButton, SCALE_Y, 1f),
						ObjectAnimator.ofFloat(emojiButton, SCALE_X, 1f),
						ObjectAnimator.ofFloat(emojiButton, ALPHA, 1f),
						ObjectAnimator.ofFloat(recordDot, SCALE_Y, 0f),
						ObjectAnimator.ofFloat(recordDot, SCALE_X, 0f),
						ObjectAnimator.ofFloat(createMediaButton, ALPHA, 1f),
						ObjectAnimator.ofFloat(createMediaButton, SCALE_X, 1f),
						ObjectAnimator.ofFloat(createMediaButton, SCALE_Y, 1f),
				)

				notifyButton?.let {
					iconsAnimator.playTogether(
							ObjectAnimator.ofFloat(it, SCALE_Y, 1f),
							ObjectAnimator.ofFloat(it, SCALE_X, 1f),
							ObjectAnimator.ofFloat(it, ALPHA, 1f),
					)
				}

				botCommandsMenuButton?.let {
					iconsAnimator.playTogether(ObjectAnimator.ofFloat(it, SCALE_Y, 1f), ObjectAnimator.ofFloat(it, SCALE_X, 1f), ObjectAnimator.ofFloat(it, ALPHA, 1f))
				}

				val recordTimer = AnimatorSet()

				recordTimer.playTogether(ObjectAnimator.ofFloat(recordTimerView, ALPHA, 0.0f), ObjectAnimator.ofFloat(recordTimerView, TRANSLATION_X, -AndroidUtilities.dp(20f).toFloat()), ObjectAnimator.ofFloat(slideText, ALPHA, 0.0f), ObjectAnimator.ofFloat(slideText, TRANSLATION_X, -AndroidUtilities.dp(20f).toFloat()))

				if (recordState != RECORD_STATE_CANCEL_BY_GESTURE) {
					audioVideoButtonContainer.scaleX = 0f
					audioVideoButtonContainer.scaleY = 0f

					if (attachButton?.visibility == VISIBLE) {
						attachButton?.scaleX = 0f
						attachButton?.scaleY = 0f
					}

					if (botButton?.visibility == VISIBLE) {
						botButton?.scaleX = 0f
						botButton?.scaleY = 0f
					}

					iconsAnimator.playTogether(ObjectAnimator.ofFloat(recordCircle, "slideToCancelProgress", 1f), ObjectAnimator.ofFloat(audioVideoButtonContainer, SCALE_X, 1f), ObjectAnimator.ofFloat(audioVideoButtonContainer, SCALE_Y, 1f), ObjectAnimator.ofFloat(audioVideoButtonContainer, ALPHA, 1f))

					attachLayout?.let {
						iconsAnimator.playTogether(ObjectAnimator.ofFloat(it, ALPHA, 1f), ObjectAnimator.ofFloat(it, TRANSLATION_X, 0f))
					}

					attachButton?.let {
						iconsAnimator.playTogether(ObjectAnimator.ofFloat(it, SCALE_X, 1f), ObjectAnimator.ofFloat(it, SCALE_Y, 1f))
					}

					botButton?.let {
						iconsAnimator.playTogether(ObjectAnimator.ofFloat(it, SCALE_X, 1f), ObjectAnimator.ofFloat(it, SCALE_Y, 1f))
					}

					iconsAnimator.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, ALPHA, 1f))
					iconsAnimator.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, SCALE_X, 1f))
					iconsAnimator.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, SCALE_Y, 1f))

					audioVideoSendButton.setState(if (isInVideoMode) ChatActivityEnterViewAnimatedIconView.State.VIDEO else ChatActivityEnterViewAnimatedIconView.State.VOICE, true)

					scheduledButton?.let {
						iconsAnimator.playTogether(ObjectAnimator.ofFloat(it, ALPHA, 1f), ObjectAnimator.ofFloat(it, TRANSLATION_X, 0f))
					}
				}
				else {
					val icons2 = AnimatorSet()

					icons2.playTogether(ObjectAnimator.ofFloat(audioVideoButtonContainer, ALPHA, 1.0f))

					attachLayout?.let {
						icons2.playTogether(ObjectAnimator.ofFloat(it, TRANSLATION_X, 0f), ObjectAnimator.ofFloat(it, ALPHA, 1f))
					}

					scheduledButton?.let {
						icons2.playTogether(ObjectAnimator.ofFloat(it, ALPHA, 1f), ObjectAnimator.ofFloat(it, TRANSLATION_X, 0f))
					}

					icons2.duration = 150
					icons2.startDelay = 110

					icons2.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							super.onAnimationEnd(animation)
							audioVideoSendButton.alpha = 1f
						}
					})

					runningAnimationAudio?.playTogether(icons2)
				}

				iconsAnimator.duration = 150
				iconsAnimator.startDelay = 700

				recordTimer.duration = 200
				recordTimer.startDelay = 200

				editField.translationX = 0f

				val messageEditTextAnimator = ObjectAnimator.ofFloat(editField, ALPHA, 1f)
				messageEditTextAnimator.startDelay = 300
				messageEditTextAnimator.duration = 200

				runningAnimationAudio?.playTogether(iconsAnimator, recordTimer, messageEditTextAnimator, ObjectAnimator.ofFloat(recordCircle, "lockAnimatedTranslation", recordCircle.startTranslation).setDuration(200))

				if (recordState == RECORD_STATE_CANCEL_BY_GESTURE) {
					recordCircle.canceledByGesture()

					val cancel = ObjectAnimator.ofFloat(recordCircle, "slideToCancelProgress", 1f).setDuration(200)
					cancel.interpolator = CubicBezierInterpolator.EASE_BOTH

					runningAnimationAudio?.playTogether(cancel)
				}
				else {
					val recordCircleAnimator: Animator = ObjectAnimator.ofFloat(recordCircle, "exitTransition", 1.0f)
					recordCircleAnimator.duration = 360
					recordCircleAnimator.startDelay = 490
					runningAnimationAudio?.playTogether(recordCircleAnimator)
				}

				recordDot.playDeleteAnimation()
			}
			else {
				audioVideoSendButton.visibility = VISIBLE

				val iconsAnimator = AnimatorSet()

				iconsAnimator.playTogether(
						ObjectAnimator.ofFloat(emojiButton, SCALE_Y, 1f),
						ObjectAnimator.ofFloat(emojiButton, SCALE_X, 1f),
						ObjectAnimator.ofFloat(emojiButton, ALPHA, 1f),
						ObjectAnimator.ofFloat(recordDot, SCALE_Y, 0f),
						ObjectAnimator.ofFloat(recordDot, SCALE_X, 0f),
						ObjectAnimator.ofFloat(audioVideoButtonContainer, ALPHA, 1.0f),
						ObjectAnimator.ofFloat(createMediaButton, ALPHA, 1f),
						ObjectAnimator.ofFloat(createMediaButton, SCALE_Y, 1f),
						ObjectAnimator.ofFloat(createMediaButton, SCALE_X, 1f),
				)

				notifyButton?.let {
					iconsAnimator.playTogether(
							ObjectAnimator.ofFloat(it, SCALE_Y, 1f),
							ObjectAnimator.ofFloat(it, SCALE_X, 1f),
							ObjectAnimator.ofFloat(it, ALPHA, 1f),
					)
				}

				botCommandsMenuButton?.let {
					iconsAnimator.playTogether(ObjectAnimator.ofFloat(it, SCALE_Y, 1f), ObjectAnimator.ofFloat(it, SCALE_X, 1f), ObjectAnimator.ofFloat(it, ALPHA, 1f))
				}

				audioVideoSendButton.scaleX = 1f
				audioVideoSendButton.scaleY = 1f

				iconsAnimator.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, ALPHA, 1f))

				audioVideoSendButton.setState(if (isInVideoMode) ChatActivityEnterViewAnimatedIconView.State.VIDEO else ChatActivityEnterViewAnimatedIconView.State.VOICE, true)

				attachLayout?.let {
					it.translationX = 0f
					iconsAnimator.playTogether(ObjectAnimator.ofFloat(it, ALPHA, 1f))
				}

				scheduledButton?.let {
					it.translationX = 0f
					iconsAnimator.playTogether(ObjectAnimator.ofFloat(it, ALPHA, 1f))
				}

				iconsAnimator.duration = 150
				iconsAnimator.startDelay = 200

				val recordTimer = AnimatorSet()
				recordTimer.playTogether(ObjectAnimator.ofFloat(recordTimerView, ALPHA, 0.0f), ObjectAnimator.ofFloat(recordTimerView, TRANSLATION_X, AndroidUtilities.dp(40f).toFloat()), ObjectAnimator.ofFloat(slideText, ALPHA, 0.0f), ObjectAnimator.ofFloat(slideText, TRANSLATION_X, AndroidUtilities.dp(40f).toFloat()))
				recordTimer.duration = 150

				val recordCircleAnimator = ObjectAnimator.ofFloat(recordCircle, "exitTransition", 1.0f)
				recordCircleAnimator.duration = if (messageTransitionIsRunning) 220 else 360.toLong()

				editField.translationX = 0f

				val messageEditTextAnimator = ObjectAnimator.ofFloat(editField, ALPHA, 1f)

				messageEditTextAnimator.startDelay = 150
				messageEditTextAnimator.duration = 200

				runningAnimationAudio?.playTogether(iconsAnimator, recordTimer, messageEditTextAnimator, recordCircleAnimator)
			}

			runningAnimationAudio?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animator: Animator) {
					if (animator == runningAnimationAudio) {
						recordPanel.visibility = GONE

						recordCircle.visibility = GONE
						recordCircle.setSendButtonInvisible()

						runningAnimationAudio = null

						if (recordState != RECORD_STATE_PREPARING) {
							editField.requestFocus()
						}

						recordedAudioBackground.alpha = 1f
						attachLayout?.translationX = 0f
						slideText.cancelToProgress = 0f
						delegate?.onAudioVideoInterfaceUpdated()
						updateSendAsButton()
					}
				}
			})

			runningAnimationAudio?.start()
			recordTimerView.stop()
		}

		delegate?.onAudioVideoInterfaceUpdated()

		updateSendAsButton()
	}

	override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
		if (recordingAudioVideo) {
			parent.requestDisallowInterceptTouchEvent(true)
		}

		return super.onInterceptTouchEvent(ev)
	}

	fun setDelegate(chatActivityEnterViewDelegate: ChatActivityEnterViewDelegate?) {
		delegate = chatActivityEnterViewDelegate
	}

	fun setCommand(messageObject: MessageObject?, command: String?, longPress: Boolean, username: Boolean) {
		if (command == null || visibility != VISIBLE) {
			return
		}

		if (longPress) {
			var text = editField.text.toString()
			val user = if (messageObject != null && DialogObject.isChatDialog(dialog_id)) accountInstance.messagesController.getUser(messageObject.messageOwner?.from_id?.user_id) else null

			text = if ((botCount != 1 || username) && user != null && user.bot && !command.contains("@")) {
				String.format(Locale.US, "%s@%s", command, user.username) + " " + text.replaceFirst("^/[a-zA-Z@\\d_]{1,255}(\\s|$)".toRegex(), "")
			}
			else {
				command + " " + text.replaceFirst("^/[a-zA-Z@\\d_]{1,255}(\\s|$)".toRegex(), "")
			}

			ignoreTextChange = true

			editField.setText(text)
			editField.setSelection(editField.text.length)

			ignoreTextChange = false

			delegate?.onTextChanged(editField.text, true)

			if (!isKeyboardVisible && currentPopupContentType == -1) {
				openKeyboard()
			}
		}
		else {
			if (slowModeTimer > 0 && !isInScheduleMode) {
				delegate?.onUpdateSlowModeButton(slowModeButton, true, slowModeButton.getText())
				return
			}

			val user = if (messageObject != null && DialogObject.isChatDialog(dialog_id)) accountInstance.messagesController.getUser(messageObject.messageOwner?.from_id?.user_id) else null

			if ((botCount != 1 || username) && user != null && user.bot && !command.contains("@")) {
				SendMessagesHelper.getInstance(currentAccount).sendMessage(String.format(Locale.US, "%s@%s", command, user.username), dialog_id, replyingMessageObject, threadMessage, null, false, null, null, null, true, 0, null, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)
			}
			else {
				SendMessagesHelper.getInstance(currentAccount).sendMessage(command, dialog_id, replyingMessageObject, threadMessage, null, false, null, null, null, true, 0, null, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)
			}
		}
	}

	fun setEditingMessageObject(messageObject: MessageObject?, caption: Boolean) {
		if (audioToSend != null || videoToSendMessageObject != null || editingMessageObject === messageObject) {
			return
		}

		val hadEditingMessage = editingMessageObject != null

		editingMessageObject = messageObject
		isEditingCaption = caption

		val textToSetWithKeyboard: CharSequence

		if (editingMessageObject != null) {
			doneButtonAnimation?.cancel()
			doneButtonAnimation = null

			doneButtonContainer.visibility = VISIBLE

			doneButtonImage.scaleX = 0.1f
			doneButtonImage.scaleY = 0.1f
			doneButtonImage.alpha = 0.0f
			doneButtonImage.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start()

			val editingText = if (caption) {
				currentLimit = accountInstance.messagesController.maxCaptionLength
				editingMessageObject?.caption
			}
			else {
				currentLimit = accountInstance.messagesController.maxMessageLength
				editingMessageObject?.messageText
			}

			if (editingText != null) {
				val entities = editingMessageObject?.messageOwner?.entities

				MediaDataController.sortEntities(entities)

				val stringBuilder = SpannableStringBuilder(editingText)
				val spansToRemove = stringBuilder.getSpans(0, stringBuilder.length, Any::class.java)

				if (spansToRemove != null && spansToRemove.isNotEmpty()) {
					for (o in spansToRemove) {
						stringBuilder.removeSpan(o)
					}
				}

				if (entities != null) {
					try {
						for (a in entities.indices) {
							val entity = entities[a]

							if (entity.offset + entity.length > stringBuilder.length) {
								continue
							}

							when (entity) {
								is TL_inputMessageEntityMentionName -> {
									if (entity.offset + entity.length < stringBuilder.length && stringBuilder[entity.offset + entity.length] == ' ') {
										entity.length++
									}

									stringBuilder.setSpan(URLSpanUserMention("" + entity.userId?.user_id, 3), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
								}

								is TL_messageEntityMentionName -> {
									if (entity.offset + entity.length < stringBuilder.length && stringBuilder[entity.offset + entity.length] == ' ') {
										entity.length++
									}

									stringBuilder.setSpan(URLSpanUserMention("" + entity.userId, 3), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
								}

								is TL_messageEntityCode, is TL_messageEntityPre -> {
									val run = TextStyleRun()
									run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_MONO
									MediaDataController.addStyleToText(TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true)
								}

								is TL_messageEntityBold -> {
									val run = TextStyleRun()
									run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_BOLD
									MediaDataController.addStyleToText(TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true)
								}

								is TL_messageEntityItalic -> {
									val run = TextStyleRun()
									run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_ITALIC
									MediaDataController.addStyleToText(TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true)
								}

								is TL_messageEntityStrike -> {
									val run = TextStyleRun()
									run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_STRIKE
									MediaDataController.addStyleToText(TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true)
								}

								is TL_messageEntityUnderline -> {
									val run = TextStyleRun()
									run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_UNDERLINE
									MediaDataController.addStyleToText(TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true)
								}

								is TL_messageEntityTextUrl -> {
									stringBuilder.setSpan(URLSpanReplacement(entity.url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
								}

								is TL_messageEntitySpoiler -> {
									val run = TextStyleRun()
									run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_SPOILER
									MediaDataController.addStyleToText(TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true)
								}

								is TL_messageEntityCustomEmoji -> {
									val span = if (entity.document != null) {
										AnimatedEmojiSpan(entity.document!!, editField.paint.fontMetricsInt)
									}
									else {
										AnimatedEmojiSpan(entity.documentId, editField.paint.fontMetricsInt)
									}

									stringBuilder.setSpan(span, entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
								}
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				val spannableStringBuilder = SpannableStringBuilder(stringBuilder)

				textToSetWithKeyboard = Emoji.replaceEmoji(spannableStringBuilder, editField.paint.fontMetricsInt, false, null) ?: spannableStringBuilder
			}
			else {
				textToSetWithKeyboard = ""
			}

			if (draftMessage == null && !hadEditingMessage) {
				draftMessage = if (editField.length() > 0) editField.text else null
				draftSearchWebpage = isMessageWebPageSearchEnabled
			}

			isMessageWebPageSearchEnabled = editingMessageObject?.messageOwner?.media is TLRPC.TL_messageMediaWebPage

			if (!isKeyboardVisible) {
				AndroidUtilities.runOnUIThread(Runnable {
					fieldText = textToSetWithKeyboard
					setTextFieldRunnable = null
				}.also {
					setTextFieldRunnable = it
				}, 200)
			}
			else {
				if (setTextFieldRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(setTextFieldRunnable)
					setTextFieldRunnable = null
				}

				fieldText = textToSetWithKeyboard
			}

			editField.requestFocus()
			openKeyboard()

			editField.updateLayoutParams<LayoutParams> {
				rightMargin = AndroidUtilities.dp(4f)
			}

			sendButton.gone()

			setSlowModeButtonVisible(false)

			cancelBotButton.gone()
			audioVideoButtonContainer.gone()
			attachLayout?.gone()
			sendButtonContainer.gone()
			scheduledButton?.gone()
		}
		else {
			if (setTextFieldRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(setTextFieldRunnable)
				setTextFieldRunnable = null
			}

			doneButtonContainer.gone()

			currentLimit = -1

			delegate?.onMessageEditEnd(false)

			sendButtonContainer.visible()

			cancelBotButton.scaleX = 0.1f
			cancelBotButton.scaleY = 0.1f
			cancelBotButton.alpha = 0.0f
			cancelBotButton.gone()

			if (slowModeTimer > 0 && !isInScheduleMode) {
				if (slowModeTimer == Int.MAX_VALUE) {
					sendButton.scaleX = 1.0f
					sendButton.scaleY = 1.0f
					sendButton.alpha = 1.0f
					sendButton.visibility = VISIBLE

					slowModeButton.scaleX = 0.1f
					slowModeButton.scaleY = 0.1f
					slowModeButton.alpha = 0.0f

					setSlowModeButtonVisible(false)
				}
				else {
					sendButton.scaleX = 0.1f
					sendButton.scaleY = 0.1f
					sendButton.alpha = 0.0f
					sendButton.visibility = GONE

					slowModeButton.scaleX = 1.0f
					slowModeButton.scaleY = 1.0f
					slowModeButton.alpha = 1.0f

					setSlowModeButtonVisible(true)
				}

				// attachLayout?.scaleX = 0.01f
				// attachLayout?.alpha = 0.0f
				// attachLayout?.visibility = GONE

				audioVideoButtonContainer.scaleX = 0.1f
				audioVideoButtonContainer.scaleY = 0.1f
				audioVideoButtonContainer.alpha = 0.0f
				audioVideoButtonContainer.visibility = GONE
			}
			else {
				sendButton.scaleX = 0.1f
				sendButton.scaleY = 0.1f
				sendButton.alpha = 0.0f
				sendButton.visibility = GONE

				slowModeButton.scaleX = 0.1f
				slowModeButton.scaleY = 0.1f
				slowModeButton.alpha = 0.0f

				setSlowModeButtonVisible(false)

				attachLayout?.scaleX = 1.0f
				attachLayout?.alpha = 1.0f
				attachLayout?.visibility = VISIBLE

				audioVideoButtonContainer.scaleX = 1.0f
				audioVideoButtonContainer.scaleY = 1.0f
				audioVideoButtonContainer.alpha = 1.0f
				audioVideoButtonContainer.visibility = VISIBLE
			}

			if (scheduledButton?.tag != null) {
				scheduledButton?.scaleX = 1.0f
				scheduledButton?.scaleY = 1.0f
				scheduledButton?.alpha = 1.0f
				scheduledButton?.visibility = VISIBLE
			}

			editField.setText(draftMessage)

			draftMessage = null

			isMessageWebPageSearchEnabled = draftSearchWebpage

			editField.setSelection(editField.length())

			if (visibility == VISIBLE) {
				delegate?.onAttachButtonShow()
			}

			updateFieldRight(1)
		}

		updateFieldHint(true)
		updateSendAsButton(true)
	}

	fun getSendButton(): View {
		return if (sendButton.visibility == VISIBLE) sendButton else audioVideoButtonContainer
	}

	fun getAudioVideoButtonContainer(): View {
		return audioVideoButtonContainer
	}

	fun getEmojiButton(): View {
		return emojiButton
	}

	fun updateColors() {
		sendPopupLayout?.let {
			it.children.forEach { view ->
				if (view is ActionBarMenuSubItem) {
					view.setColors(context.getColor(R.color.dark_gray), context.getColor(R.color.dark_gray))
					view.setSelectorColor(context.getColor(R.color.light_background))
				}
			}
		}

		// updateRecordedDeleteIconColors()

		recordCircle.updateColors()
		recordDot.updateColors()
		slideText.updateColors()
		// recordTimerView.updateColors()
		videoTimelineView.updateColors()

		if (codePointCount - currentLimit < 0) {
			captionLimitView.setTextColor(context.getColor(R.color.purple))
		}
		else {
			captionLimitView.setTextColor(context.getColor(R.color.dark_gray))
		}

		val color = context.getColor(R.color.white)
		val defaultAlpha = Color.alpha(color)

		doneCheckDrawable.colorFilter = PorterDuffColorFilter(ColorUtils.setAlphaComponent(color, (defaultAlpha * (0.58f + 0.42f * doneButtonEnabledProgress)).toInt()), PorterDuff.Mode.MULTIPLY)

		botCommandsMenuContainer?.updateColors()
		botKeyboardView?.updateColors()

		audioVideoSendButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.hint, null), PorterDuff.Mode.SRC_IN)
		emojiButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.hint, null), PorterDuff.Mode.SRC_IN)
		emojiButton.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))
	}

	fun setFieldText(text: CharSequence?, ignoreChange: Boolean) {
		ignoreTextChange = ignoreChange

		editField.setText(text)
		editField.setSelection(editField.text.length)

		ignoreTextChange = false

		if (ignoreChange) {
			delegate?.onTextChanged(editField.text, true)
		}
	}

	fun setSelection(start: Int) {
		editField.setSelection(start, editField.length())
	}

	val cursorPosition: Int
		get() = editField.selectionStart

	val selectionLength: Int
		get() {
			return try {
				editField.selectionEnd - editField.selectionStart
			}
			catch (e: Exception) {
				FileLog.e(e)
				0
			}
		}

	fun replaceWithText(start: Int, len: Int, text: CharSequence?, parseEmoji: Boolean) {
		try {
			val builder = SpannableStringBuilder(editField.text)
			builder.replace(start, start + len, text ?: "")

			if (parseEmoji) {
				Emoji.replaceEmoji(builder, editField.paint.fontMetricsInt, false)
			}

			editField.text = builder
			editField.setSelection(start + (text?.length ?: 0))
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	fun setFieldFocused() {
		val am = parentActivity.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

		if (!am.isTouchExplorationEnabled) {
			try {
				editField.requestFocus()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun setFieldFocused(focus: Boolean) {
		val am = parentActivity.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

		if (am.isTouchExplorationEnabled) {
			return
		}

		if (focus) {
			if (searchingType == 0 && !editField.isFocused) {
				AndroidUtilities.runOnUIThread(Runnable {
					focusRunnable = null

					val allowFocus = if (AndroidUtilities.isTablet()) {
						if (parentActivity is LaunchActivity) {
							val layout: View? = parentActivity.layersActionBarLayout
							layout == null || layout.visibility != VISIBLE
						}
						else {
							true
						}
					}
					else {
						true
					}

					if (!isPaused && allowFocus) {
						try {
							editField.requestFocus()
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}.also {
					focusRunnable = it
				}, 600)
			}
		}
		else {
			if (editField.isFocused && (!isKeyboardVisible || isPaused)) {
				editField.clearFocus()
			}
		}
	}

	fun hasText(): Boolean {
		return editField.length() > 0
	}

	fun getDraftMessage(): CharSequence? {
		if (editingMessageObject != null) {
			return if (TextUtils.isEmpty(draftMessage)) null else draftMessage
		}

		return if (hasText()) {
			editField.text
		}
		else {
			null
		}
	}

	var fieldText: CharSequence?
		get() = editField.text?.takeIf { it.isNotEmpty() }
		set(text) {
			setFieldText(text, true)
		}

	fun updateScheduleButton(animated: Boolean) {
		var notifyVisible = false

		if (DialogObject.isChatDialog(dialog_id)) {
			val currentChat = accountInstance.messagesController.getChat(-dialog_id)

			silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_$dialog_id", false)
			canWriteToChannel = ChatObject.isChannel(currentChat) && (currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.post_messages) && !currentChat.megagroup

			if (notifyButton != null) {
				notifyVisible = canWriteToChannel

				if (notifySilentDrawable == null) {
					notifySilentDrawable = CrossOutDrawable(context, R.drawable.notify_channel, ResourcesCompat.getColor(resources, R.color.hint, null))
				}

				notifySilentDrawable?.setCrossOut(silent, false)
				notifyButton?.setImageDrawable(notifySilentDrawable)
			}

			if (attachLayout != null) {
				updateFieldRight(if (attachLayout?.visibility == VISIBLE) 1 else 0)
			}
		}

		val hasScheduled = delegate != null && !isInScheduleMode && delegate!!.hasScheduledMessages()
		val visible = hasScheduled && !scheduleButtonHidden && !recordingAudioVideo

		if (scheduledButton != null) {
			if (scheduledButton?.tag != null && visible || scheduledButton?.tag == null && !visible) {
				if (notifyButton != null) {
					val newVisibility = if (!hasScheduled && notifyVisible && scheduledButton?.visibility != VISIBLE) VISIBLE else GONE

					if (newVisibility != notifyButton?.visibility) {
						notifyButton?.visibility = newVisibility
						if (notifyButton?.isVisible == true) {
							editField.setPadding(AndroidUtilities.dp(0f), AndroidUtilities.dp(11f), AndroidUtilities.dp(96f), AndroidUtilities.dp(12f))
						}

						if (attachLayout != null) {
							attachLayout?.pivotX = AndroidUtilities.dp(if ((botButton == null || botButton?.visibility == GONE) && (notifyButton == null || notifyButton?.visibility == GONE)) 48f else 96f).toFloat()
						}
					}
				}
				return
			}

			scheduledButton?.tag = if (visible) 1 else null
		}

		scheduledButtonAnimation?.cancel()
		scheduledButtonAnimation = null

		if (!animated || notifyVisible) {
			if (scheduledButton != null) {
				scheduledButton?.visibility = if (visible) VISIBLE else GONE
				scheduledButton?.alpha = if (visible) 1.0f else 0.0f
				scheduledButton?.scaleX = if (visible) 1.0f else 0.1f
				scheduledButton?.scaleY = if (visible) 1.0f else 0.1f

				if (notifyButton != null) {
					notifyButton?.visibility = if (notifyVisible && scheduledButton?.visibility != VISIBLE) VISIBLE else GONE
				}
			}
		}
		else if (scheduledButton != null) {
			if (visible) {
				scheduledButton?.visibility = VISIBLE
			}

			scheduledButton?.pivotX = AndroidUtilities.dp(24f).toFloat()

			scheduledButtonAnimation = AnimatorSet()
			scheduledButtonAnimation?.playTogether(ObjectAnimator.ofFloat(scheduledButton, ALPHA, if (visible) 1.0f else 0.0f), ObjectAnimator.ofFloat(scheduledButton, SCALE_X, if (visible) 1.0f else 0.1f), ObjectAnimator.ofFloat(scheduledButton, SCALE_Y, if (visible) 1.0f else 0.1f))
			scheduledButtonAnimation?.duration = 180

			scheduledButtonAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					scheduledButtonAnimation = null

					if (!visible) {
						scheduledButton?.visibility = GONE
					}
				}
			})

			scheduledButtonAnimation?.start()
		}

		attachLayout?.pivotX = AndroidUtilities.dp(if ((botButton == null || botButton?.visibility == GONE) && (notifyButton == null || notifyButton?.visibility == GONE)) 48f else 96f).toFloat()
	}

	@JvmOverloads
	fun updateSendAsButton(animated: Boolean = true) {
		if (parentFragment == null || delegate == null) {
			return
		}

		val full = parentFragment.messagesController.getChatFull(-dialog_id)

		var defPeer = full?.default_send_as

		if (defPeer == null && delegate?.sendAsPeers != null && !delegate?.sendAsPeers?.peers.isNullOrEmpty()) {
			defPeer = delegate?.sendAsPeers?.peers?.firstOrNull()?.peer
		}

		if (defPeer != null) {
			if (defPeer.channel_id != 0L) {
				val ch = MessagesController.getInstance(currentAccount).getChat(defPeer.channel_id)

				if (ch != null) {
					senderSelectView.setAvatar(ch)
				}
			}
			else {
				val user = MessagesController.getInstance(currentAccount).getUser(defPeer.user_id)

				if (user != null) {
					senderSelectView.setAvatar(user)
				}
			}
		}

		val wasVisible = senderSelectView.visibility == VISIBLE
		val isVisible = defPeer != null && (delegate!!.sendAsPeers == null || delegate!!.sendAsPeers!!.peers.size > 1) && !isEditingMessage && !isRecordingAudioVideo() && recordedAudioPanel.visibility != VISIBLE
		val pad = AndroidUtilities.dp(2f)
		val params = senderSelectView.layoutParams as MarginLayoutParams
		val startAlpha: Float = if (isVisible) 0f else 1f
		val startX = if (isVisible) (-senderSelectView.layoutParams.width - params.leftMargin - pad).toFloat() else 0.toFloat()
		val endAlpha: Float = if (isVisible) 1f else 0f
		val endX: Float = if (isVisible) 0f else -senderSelectView.layoutParams.width - params.leftMargin - pad.toFloat()

		if (wasVisible != isVisible) {
			val animator = senderSelectView.tag as? ValueAnimator

			if (animator != null) {
				animator.cancel()
				senderSelectView.tag = null
			}

			if (parentFragment.otherSameChatsDiff == 0 && parentFragment.fragmentOpened && animated) {
				val anim = ValueAnimator.ofFloat(0f, 1f).setDuration(150)

				senderSelectView.translationX = startX
				editField.translationX = senderSelectView.translationX

				anim.addUpdateListener {
					val `val` = it.animatedValue as Float
					senderSelectView.alpha = startAlpha + (endAlpha - startAlpha) * `val`
					senderSelectView.translationX = startX + (endX - startX) * `val`
					emojiButton.translationX = senderSelectView.translationX
					editField.translationX = senderSelectView.translationX
				}

				anim.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationStart(animation: Animator) {
						if (isVisible) {
							senderSelectView.visibility = VISIBLE
						}

						senderSelectView.alpha = startAlpha
						senderSelectView.translationX = startX
						emojiButton.translationX = senderSelectView.translationX
						editField.translationX = senderSelectView.translationX

						if (botCommandsMenuButton?.tag == null) {
							animationParamsX.clear()
						}
					}

					override fun onAnimationEnd(animation: Animator) {
						if (!isVisible) {
							senderSelectView.visibility = GONE
							emojiButton.translationX = 0f
							editField.translationX = 0f
						}
					}

					override fun onAnimationCancel(animation: Animator) {
						if (isVisible) {
							senderSelectView.visibility = VISIBLE
						}
						else {
							senderSelectView.visibility = GONE
						}

						senderSelectView.alpha = endAlpha
						senderSelectView.translationX = endX

						emojiButton.translationX = senderSelectView.translationX
						editField.translationX = senderSelectView.translationX

						requestLayout()
					}
				})

				anim.start()
				senderSelectView.tag = anim
			}
			else {
				senderSelectView.visibility = if (isVisible) VISIBLE else GONE
				senderSelectView.translationX = endX

				val translationX = if (isVisible) endX else 0f

				emojiButton.translationX = translationX
				editField.translationX = translationX
				senderSelectView.alpha = endAlpha
				senderSelectView.tag = null
			}
		}
	}

	fun onBotWebViewBackPressed(): Boolean {
		return botWebViewMenuContainer?.onBackPressed() ?: false
	}

	fun hasBotWebView(): Boolean {
		return botMenuButtonType == BotMenuButtonType.WEB_VIEW
	}

	private fun updateBotButton(animated: Boolean) {
		@Suppress("NAME_SHADOWING") var animated = animated

		if (botButton == null) {
			return
		}

		if (parentFragment?.openAnimationEnded == true) {
			animated = false
		}

		val hasBotWebView = hasBotWebView()
		val canShowBotsMenu = botMenuButtonType != BotMenuButtonType.NO_BUTTON && dialog_id > 0
		val wasVisible = botButton?.visibility == VISIBLE

		if (hasBotWebView || hasBotCommands || botReplyMarkup != null) {
			if (botReplyMarkup != null) {
				if (isPopupShowing && currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD) {
					if (botButton?.visibility != GONE) {
						botButton?.visibility = GONE
					}
				}
				else {
					if (botButton?.visibility != VISIBLE) {
						botButton?.visibility = VISIBLE
					}

					botButtonDrawable?.setIcon(R.drawable.input_bot2, true)
					botButton?.contentDescription = context.getString(R.string.AccDescrBotKeyboard)
				}
			}
			else {
				if (!canShowBotsMenu) {
					botButtonDrawable?.setIcon(R.drawable.input_bot1, true)
					botButton?.contentDescription = context.getString(R.string.AccDescrBotCommands)
					botButton?.visibility = VISIBLE
				}
				else {
					botButton?.visibility = GONE
				}
			}
		}
		else {
			botButton?.visibility = GONE
		}

		val wasWebView = botCommandsMenuButton!!.isWebView

		botCommandsMenuButton?.setWebView(botMenuButtonType == BotMenuButtonType.WEB_VIEW)

		val textChanged = botCommandsMenuButton!!.setMenuText(if (botMenuButtonType == BotMenuButtonType.COMMANDS) context.getString(R.string.BotsMenuTitle) else botMenuWebViewTitle)

		AndroidUtilities.updateViewVisibilityAnimated(botCommandsMenuButton, canShowBotsMenu, 0.5f, animated)

		val changed = botButton?.visibility == VISIBLE != wasVisible || textChanged || wasWebView != botCommandsMenuButton!!.isWebView

		if (changed && animated) {
			beginDelayedTransition()
			val show = botButton?.visibility == VISIBLE

			if (show != wasVisible) {
				botButton?.visibility = VISIBLE

				if (show) {
					botButton?.alpha = 0f
					botButton?.scaleX = 0.1f
					botButton?.scaleY = 0.1f
				}
				else {
					botButton?.alpha = 1f
					botButton?.scaleX = 1f
					botButton?.scaleY = 1f
				}

				AndroidUtilities.updateViewVisibilityAnimated(botButton, show, 0.1f, true)
			}
		}

		updateFieldRight(2)

		attachLayout?.pivotX = AndroidUtilities.dp(if ((botButton == null || botButton?.visibility == GONE) && (notifyButton == null || notifyButton?.visibility == GONE)) 48f else 96f).toFloat()
	}

	fun updateBotWebView(animated: Boolean) {
		botCommandsMenuButton?.setWebView(hasBotWebView())
		updateBotButton(animated)
	}

	fun setBotsCount(count: Int, hasCommands: Boolean, animated: Boolean) {
		botCount = count

		if (hasBotCommands != hasCommands) {
			hasBotCommands = hasCommands
			updateBotButton(animated)
		}
	}

	fun setButtons(messageObject: MessageObject?) {
		setButtons(messageObject, true)
	}

	fun setButtons(messageObject: MessageObject?, openKeyboard: Boolean) {
		if (replyingMessageObject != null && replyingMessageObject === botButtonsMessageObject && replyingMessageObject !== messageObject) {
			botMessageObject = messageObject
			return
		}

		if (botButton == null || botButtonsMessageObject != null && botButtonsMessageObject === messageObject || botButtonsMessageObject == null && messageObject == null) {
			return
		}

		if (botKeyboardView == null) {
			botKeyboardView = object : BotKeyboardView(parentActivity, null) {
				override fun setTranslationY(translationY: Float) {
					super.setTranslationY(translationY)

					if (panelAnimation != null && animatingContentType == 1) {
						delegate?.bottomPanelTranslationYChanged(translationY)
					}
				}
			}

			botKeyboardView?.visibility = GONE

			botKeyboardViewVisible = false

			botKeyboardView?.setDelegate { button ->
				val `object` = replyingMessageObject ?: (if (DialogObject.isChatDialog(dialog_id)) botButtonsMessageObject else null)
				val open = didPressedBotButton(button, `object`, if (replyingMessageObject != null) replyingMessageObject else botButtonsMessageObject)

				if (replyingMessageObject != null) {
					openKeyboardInternal()
					setButtons(botMessageObject, false)
				}
				else if (botButtonsMessageObject?.messageOwner?.reply_markup?.single_use == true) {
					if (open) {
						openKeyboardInternal()
					}
					else {
						showPopup(0, 0)
					}

					val preferences = MessagesController.getMainSettings(currentAccount)
					preferences.edit().putInt("answered_$dialog_id", botButtonsMessageObject!!.id).commit()
				}

				delegate?.onMessageSend(null, true, 0)
			}

			sizeNotifierLayout?.addView(botKeyboardView, sizeNotifierLayout.childCount - 1)
		}

		botButtonsMessageObject = messageObject

		botReplyMarkup = messageObject?.messageOwner?.reply_markup as? TLRPC.TL_replyKeyboardMarkup

		botKeyboardView?.setPanelHeight(if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) keyboardHeightLand else keyboardHeight)

		if (botReplyMarkup != null) {
			val preferences = MessagesController.getMainSettings(currentAccount)
			var showPopup = true

			if (botButtonsMessageObject !== replyingMessageObject && botReplyMarkup?.single_use == true) {
				if (preferences.getInt("answered_$dialog_id", 0) == messageObject?.id) {
					showPopup = false
				}
			}

			if (showPopup && editField.length() == 0 && !isPopupShowing) {
				showPopup(1, 1)
			}

			botKeyboardView?.setButtons(botReplyMarkup)
		}
		else {
			if (isPopupShowing && currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD) {
				if (openKeyboard) {
					clearBotButtonsOnKeyboardOpen = true
					openKeyboardInternal()
				}
				else {
					showPopup(0, 1)
				}
			}
		}

		updateBotButton(true)
	}

	fun didPressedBotButton(button: TLRPC.KeyboardButton?, replyMessageObject: MessageObject?, messageObject: MessageObject?): Boolean {
		if (parentFragment?.isAiPromptsDepleted() == true) {
			return false
		}
		if (button == null || messageObject == null) {
			return false
		}
		if (button is TLRPC.TL_keyboardButton) {
			SendMessagesHelper.getInstance(currentAccount).sendMessage(button.text, dialog_id, replyMessageObject, threadMessage, null, false, null, null, null, true, 0, null, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)
		}
		else if (button is TLRPC.TL_keyboardButtonUrl) {
			AlertsCreator.showOpenUrlAlert(parentFragment, button.url, punycode = false, ask = true)
		}
		else if (button is TLRPC.TL_keyboardButtonRequestPhone) {
			parentFragment?.shareMyContact(2, messageObject)
		}
		else if (button is TLRPC.TL_keyboardButtonRequestPoll) {
			parentFragment?.openPollCreate(if (button.flags and 1 != 0) button.quiz else null)
			return false
		}
		else if (button is TLRPC.TL_keyboardButtonWebView || button is TLRPC.TL_keyboardButtonSimpleWebView) {
			val botId = if (messageObject.messageOwner?.via_bot_id != 0L) messageObject.messageOwner?.via_bot_id else (messageObject.messageOwner?.from_id?.user_id ?: 0L)
			val user = MessagesController.getInstance(currentAccount).getUser(botId)

			val onRequestWebView: Runnable = object : Runnable {
				override fun run() {
					if (sizeNotifierLayout!!.measureKeyboardHeight() > AndroidUtilities.dp(20f)) {
						AndroidUtilities.hideKeyboard(this@ChatActivityEnterView)
						AndroidUtilities.runOnUIThread(this, 150)
						return
					}

					val webViewSheet = BotWebViewSheet(context, null)
					webViewSheet.setParentActivity(parentActivity)
					webViewSheet.requestWebView(currentAccount, messageObject.messageOwner?.dialog_id ?: 0, botId ?: 0, button.text, button.url, if (button is TLRPC.TL_keyboardButtonSimpleWebView) BotWebViewSheet.TYPE_SIMPLE_WEB_VIEW_BUTTON else BotWebViewSheet.TYPE_WEB_VIEW_BUTTON, replyMessageObject?.messageOwner?.id ?: 0, false)
					webViewSheet.show()
				}
			}

			if (SharedPrefsHelper.isWebViewConfirmShown(currentAccount, botId ?: 0L)) {
				onRequestWebView.run()
			}
			else {
				parentFragment?.parentActivity?.let {
					AlertDialog.Builder(it).setTitle(context.getString(R.string.BotOpenPageTitle)).setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BotOpenPageMessage", R.string.BotOpenPageMessage, getUserName(user)))).setPositiveButton(context.getString(R.string.OK)) { _, _ ->
						onRequestWebView.run()
						SharedPrefsHelper.setWebViewConfirmShown(currentAccount, botId ?: 0L, true)
					}.setNegativeButton(context.getString(R.string.Cancel), null).show()
				}
			}
		}
		else if (button is TLRPC.TL_keyboardButtonRequestGeoLocation) {
			val builder = AlertDialog.Builder(parentActivity)
			builder.setTitle(context.getString(R.string.ShareYouLocationTitle))
			builder.setMessage(context.getString(R.string.ShareYouLocationInfo))

			builder.setPositiveButton(context.getString(R.string.OK)) { _, _ ->
				if (parentActivity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
					parentActivity.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 2)
					pendingMessageObject = messageObject
					pendingLocationButton = button
					return@setPositiveButton
				}

				SendMessagesHelper.getInstance(currentAccount).sendCurrentLocation(messageObject, button)
			}

			builder.setNegativeButton(context.getString(R.string.Cancel), null)

			parentFragment?.showDialog(builder.create())
		}
		else if (button is TLRPC.TL_keyboardButtonCallback || button is TLRPC.TL_keyboardButtonGame || button is TLRPC.TL_keyboardButtonBuy || button is TLRPC.TL_keyboardButtonUrlAuth) {
			SendMessagesHelper.getInstance(currentAccount).sendCallback(true, messageObject, button, parentFragment)
		}
		else if (button is TLRPC.TL_keyboardButtonSwitchInline) {
			if (parentFragment?.processSwitchButton(button) == true) {
				return true
			}

			if (button.same_peer) {
				var uid = messageObject.messageOwner?.from_id?.user_id ?: 0L

				if (messageObject.messageOwner?.via_bot_id != 0L) {
					uid = messageObject.messageOwner?.via_bot_id ?: 0L
				}

				val user = accountInstance.messagesController.getUser(uid) ?: return true

				fieldText = "@" + user.username + " " + button.query
			}
			else {
				val args = Bundle()
				args.putBoolean("onlySelect", true)
				args.putInt("dialogsType", 1)

				val fragment = DialogsActivity(args)

				fragment.setDelegate { fragment1, dids, _, _ ->
					var uid = messageObject.messageOwner?.from_id?.user_id ?: 0L

					if (messageObject.messageOwner?.via_bot_id != 0L) {
						uid = messageObject.messageOwner?.via_bot_id ?: 0L
					}

					val user = accountInstance.messagesController.getUser(uid)

					if (user == null) {
						fragment1?.finishFragment()
						return@setDelegate
					}

					val did = dids[0]

					MediaDataController.getInstance(currentAccount).saveDraft(did, 0, "@" + user.username + " " + button.query, null, null, true)

					if (did != dialog_id) {
						if (!DialogObject.isEncryptedDialog(did)) {
							val args1 = Bundle()

							if (DialogObject.isUserDialog(did)) {
								args1.putLong("user_id", did)
							}
							else {
								args1.putLong("chat_id", -did)
							}

							if (!accountInstance.messagesController.checkCanOpenChat(args1, fragment1)) {
								return@setDelegate
							}

							val chatActivity = ChatActivity(args1)

							if (parentFragment?.presentFragment(chatActivity, true) == true) {
								if (!AndroidUtilities.isTablet()) {
									parentFragment.removeSelfFromStack()
								}
							}
							else {
								fragment1?.finishFragment()
							}
						}
						else {
							fragment1?.finishFragment()
						}
					}
					else {
						fragment1?.finishFragment()
					}
				}

				parentFragment?.presentFragment(fragment)
			}
		}
		else if (button is TLRPC.TL_keyboardButtonUserProfile) {
			if (MessagesController.getInstance(currentAccount).getUser(button.user_id) != null) {
				val args = Bundle()
				args.putLong("user_id", button.user_id)
				val fragment = ProfileActivity(args)
				parentFragment?.presentFragment(fragment)
			}
		}

		return true
	}

	fun isPopupView(view: View): Boolean {
		return view === botKeyboardView || view === emojiView
	}

	fun isRecordCircle(view: View): Boolean {
		return view === recordCircle
	}

	private fun createEmojiView() {
		if (emojiView != null && emojiView?.currentAccount != UserConfig.selectedAccount) {
			sizeNotifierLayout?.removeView(emojiView)
			emojiView = null
		}

		if (emojiView != null) {
			return
		}

		emojiView = object : EmojiView(parentFragment, allowAnimatedEmoji, true, true, context, true, info, sizeNotifierLayout) {
			override fun setTranslationY(translationY: Float) {
				super.setTranslationY(translationY)

				if (panelAnimation != null && animatingContentType == 0) {
					delegate?.bottomPanelTranslationYChanged(translationY)
				}
			}
		}

		emojiView?.setAllow(allowStickers, allowGifs, true)
		emojiView?.visibility = GONE
		emojiView?.setShowing(false)

		emojiView?.setDelegate(object : EmojiViewDelegate {
			override fun isUserSelf(): Boolean {
				return dialog_id == getInstance(currentAccount).getClientUserId()
			}

			override fun onBackspace(): Boolean {
				if (editField.length() == 0) {
					return false
				}

				editField.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))

				return true
			}

			override fun onEmojiSelected(symbol: String) {
				var i = editField.selectionEnd

				if (i < 0) {
					i = 0
				}

				try {
					innerTextChange = 2

					val localCharSequence = Emoji.replaceEmoji(symbol, editField.paint.fontMetricsInt, false) ?: symbol
					editField.text = editField.text.insert(i, localCharSequence)

					val j = i + localCharSequence.length
					editField.setSelection(j, j)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
				finally {
					innerTextChange = 0
				}
			}

			override fun onCustomEmojiSelected(documentId: Long, document: TLRPC.Document?, emoticon: String, isRecent: Boolean) {
				AndroidUtilities.runOnUIThread {
					var i = editField.selectionEnd

					if (i < 0) {
						i = 0
					}
					try {
						innerTextChange = 2

						val emoji = SpannableString(emoticon)

						val span = if (document != null) {
							AnimatedEmojiSpan(document, editField.paint.fontMetricsInt)
						}
						else {
							AnimatedEmojiSpan(documentId, editField.paint.fontMetricsInt)
						}

						if (!isRecent) {
							span.fromEmojiKeyboard = true
						}

						span.cacheType = AnimatedEmojiDrawable.getCacheTypeForEnterView()

						emoji.setSpan(span, 0, emoji.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

						editField.text = editField.text.insert(i, emoji)
						editField.setSelection(i + emoji.length, i + emoji.length)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
					finally {
						innerTextChange = 0
					}
				}
			}

			override fun onAnimatedEmojiUnlockClick() {
				val alert = PremiumFeatureBottomSheet(parentFragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false)

				if (parentFragment != null) {
					parentFragment.showDialog(alert)
				}
				else {
					alert.show()
				}
			}

			override fun onStickerSelected(view: View, sticker: TLRPC.Document, query: String, parent: Any, sendAnimationData: SendAnimationData, notify: Boolean, scheduleDate: Int) {
				trendingStickersAlert?.dismiss()
				trendingStickersAlert = null

				if (slowModeTimer > 0 && !isInScheduleMode) {
					delegate?.onUpdateSlowModeButton(view, true, slowModeButton.getText())
					return
				}

				if (isStickersExpanded) {
					if (searchingType != 0) {
						setSearchingTypeInternal(0, true)
						emojiView?.closeSearch(true, MessageObject.getStickerSetId(sticker))
						emojiView?.hideSearchKeyboard()
					}

					setStickersExpanded(expanded = false, animated = true, byDrag = false)
				}

				this@ChatActivityEnterView.onStickerSelected(sticker, query, parent, sendAnimationData, false, notify, scheduleDate)

				if (DialogObject.isEncryptedDialog(dialog_id) && MessageObject.isGifDocument(sticker)) {
					accountInstance.messagesController.saveGif(parent, sticker)
				}
			}

			override fun onStickersSettingsClick() {
				parentFragment?.presentFragment(StickersActivity(MediaDataController.TYPE_IMAGE, null))
			}

			override fun onEmojiSettingsClick(frozenEmojiPacks: ArrayList<TLRPC.TL_messages_stickerSet>) {
				parentFragment?.presentFragment(StickersActivity(MediaDataController.TYPE_EMOJIPACKS, frozenEmojiPacks))
			}

			override fun onGifSelected(view: View, gif: Any, query: String, parent: Any, notify: Boolean, scheduleDate: Int) {
				if (isInScheduleMode && scheduleDate == 0) {
					AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment?.dialogId ?: 0) { n, s -> onGifSelected(view, gif, query, parent, n, s) }
				}
				else {
					if (slowModeTimer > 0 && !isInScheduleMode) {
						delegate?.onUpdateSlowModeButton(view, true, slowModeButton.getText())
						return
					}

					if (isStickersExpanded) {
						if (searchingType != 0) {
							emojiView?.hideSearchKeyboard()
						}

						setStickersExpanded(expanded = false, animated = true, byDrag = false)
					}
					if (gif is TLRPC.Document) {
						SendMessagesHelper.getInstance(currentAccount).sendSticker(gif, query, dialog_id, replyingMessageObject, threadMessage, parent, null, notify, scheduleDate, false)
						MediaDataController.getInstance(currentAccount).addRecentGif(gif, (System.currentTimeMillis() / 1000).toInt(), true)

						if (DialogObject.isEncryptedDialog(dialog_id)) {
							accountInstance.messagesController.saveGif(parent, gif)
						}
					}
					else if (gif is TLRPC.BotInlineResult) {
						if (gif.document != null) {
							MediaDataController.getInstance(currentAccount).addRecentGif(gif.document, (System.currentTimeMillis() / 1000).toInt(), false)
							if (DialogObject.isEncryptedDialog(dialog_id)) {
								accountInstance.messagesController.saveGif(parent, gif.document)
							}
						}

						val params = HashMap<String, String>()
						params["id"] = gif.id
						params["query_id"] = "" + gif.query_id
						params["force_gif"] = "1"

						SendMessagesHelper.prepareSendingBotContextResult(parentFragment, accountInstance, gif, params, dialog_id, replyingMessageObject, threadMessage, notify, scheduleDate)

						if (searchingType != 0) {
							setSearchingTypeInternal(0, true)
							emojiView?.closeSearch(true)
							emojiView?.hideSearchKeyboard()
						}
					}

					delegate?.onMessageSend(null, notify, scheduleDate)
				}
			}

			override fun onTabOpened(type: Int) {
				delegate?.onStickersTab(type == 3)
				post(updateExpandabilityRunnable)
			}

			override fun onClearEmojiRecent() {
				if (parentFragment == null) {
					return
				}

				val builder = AlertDialog.Builder(parentActivity)
				builder.setTitle(context.getString(R.string.ClearRecentEmojiTitle))
				builder.setMessage(context.getString(R.string.ClearRecentEmojiText))
				builder.setPositiveButton(context.getString(R.string.ClearForAll).uppercase()) { _, _ -> emojiView?.clearRecentEmoji() }
				builder.setNegativeButton(context.getString(R.string.Cancel), null)

				parentFragment.showDialog(builder.create())
			}

			override fun onShowStickerSet(stickerSet: TLRPC.StickerSet?, inputStickerSet: TLRPC.InputStickerSet) {
				@Suppress("NAME_SHADOWING") var inputStickerSet = inputStickerSet

				if (trendingStickersAlert?.isDismissed == false) {
					trendingStickersAlert?.layout?.showStickerSet(stickerSet, inputStickerSet)
					return
				}

				if (parentFragment == null) {
					return
				}

				if (stickerSet != null) {
					inputStickerSet = TLRPC.TL_inputStickerSetID()
					inputStickerSet.access_hash = stickerSet.access_hash
					inputStickerSet.id = stickerSet.id
				}

				parentFragment.showDialog(StickersAlert(parentActivity, parentFragment, inputStickerSet, null, this@ChatActivityEnterView))
			}

			override fun onStickerSetAdd(stickerSet: TLRPC.StickerSetCovered) {
				MediaDataController.getInstance(currentAccount).toggleStickerSet(parentActivity, stickerSet, 2, parentFragment, showSettings = false, showTooltip = false)
			}

			override fun onStickerSetRemove(stickerSet: TLRPC.StickerSetCovered) {
				MediaDataController.getInstance(currentAccount).toggleStickerSet(parentActivity, stickerSet, 0, parentFragment, showSettings = false, showTooltip = false)
			}

			override fun onStickersGroupClick(chatId: Long) {
				if (parentFragment != null) {
					if (AndroidUtilities.isTablet()) {
						hidePopup(false)
					}

					val fragment = GroupStickersActivity(chatId)
					fragment.setInfo(info)

					parentFragment.presentFragment(fragment)
				}
			}

			override fun onSearchOpenClose(type: Int) {
				setSearchingTypeInternal(type, true)

				if (type != 0) {
					setStickersExpanded(expanded = true, animated = true, byDrag = false, stopAllHeavy = type == 1)
				}

				if (emojiTabOpen && searchingType == 2) {
					checkStickersExpandHeight()
				}
			}

			override fun isSearchOpened(): Boolean {
				return searchingType != 0
			}

			override fun isExpanded(): Boolean {
				return isStickersExpanded
			}

			override fun canSchedule(): Boolean {
				return parentFragment != null && parentFragment.canScheduleMessage()
			}

			override fun isInScheduleMode(): Boolean {
				return parentFragment != null && parentFragment.isInScheduleMode
			}

			override fun getDialogId(): Long {
				return dialog_id
			}

			override fun getThreadId(): Int {
				return threadMessageId
			}

			override fun showTrendingStickersAlert(layout: TrendingStickersLayout) {
				if (parentFragment != null) {
					trendingStickersAlert = object : TrendingStickersAlert(parentActivity, parentFragment, layout) {
						override fun dismiss() {
							super.dismiss()

							if (trendingStickersAlert === this) {
								trendingStickersAlert = null
							}

							this@ChatActivityEnterView.delegate?.onTrendingStickersShowed(false)
						}
					}

					delegate?.onTrendingStickersShowed(true)

					trendingStickersAlert?.show()
				}
			}

			override fun invalidateEnterView() {
				invalidate()
			}

			override fun getProgressToSearchOpened(): Float {
				return searchToOpenProgress
			}
		})

		emojiView?.setDragListener(object : EmojiView.DragListener {
			var wasExpanded = false
			var initialOffset = 0

			override fun onDragStart() {
				if (!allowDragging()) {
					return
				}

				stickersExpansionAnim?.cancel()
				stickersDragging = true
				wasExpanded = isStickersExpanded
				isStickersExpanded = true

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.stopAllHeavyOperations, 1)

				stickersExpandedHeight = sizeNotifierLayout!!.height - AndroidUtilities.statusBarHeight - ActionBar.getCurrentActionBarHeight() - height + Theme.chat_composeShadowDrawable.intrinsicHeight

				if (searchingType == 2) {
					stickersExpandedHeight = min(stickersExpandedHeight, AndroidUtilities.dp(120f) + if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) keyboardHeightLand else keyboardHeight)
				}

				emojiView?.layoutParams?.height = stickersExpandedHeight
				emojiView?.setLayerType(LAYER_TYPE_HARDWARE, null)

				sizeNotifierLayout.requestLayout()
				sizeNotifierLayout.foreground = ScrimDrawable()

				initialOffset = translationY.toInt()

				delegate?.onStickersExpandedChange()
			}

			override fun onDragEnd(velocity: Float) {
				if (!allowDragging()) {
					return
				}

				stickersDragging = false

				if (wasExpanded && velocity >= AndroidUtilities.dp(200f) || !wasExpanded && velocity <= AndroidUtilities.dp(-200f) || wasExpanded && stickersExpansionProgress <= 0.6f || !wasExpanded && stickersExpansionProgress >= 0.4f) {
					setStickersExpanded(!wasExpanded, animated = true, byDrag = true)
				}
				else {
					setStickersExpanded(wasExpanded, animated = true, byDrag = true)
				}
			}

			override fun onDragCancel() {
				if (!stickersTabOpen) {
					return
				}

				stickersDragging = false

				setStickersExpanded(wasExpanded, animated = true, byDrag = false)
			}

			override fun onDrag(offset: Int) {
				@Suppress("NAME_SHADOWING") var offset = offset

				if (!allowDragging()) {
					return
				}

				val origHeight = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) keyboardHeightLand else keyboardHeight

				offset += initialOffset
				offset = max(min(offset, 0), -(stickersExpandedHeight - origHeight))

				emojiView?.translationY = offset.toFloat()

				translationY = offset.toFloat()
				stickersExpansionProgress = offset.toFloat() / -(stickersExpandedHeight - origHeight)
				sizeNotifierLayout!!.invalidate()
			}

			private fun allowDragging(): Boolean {
				return stickersTabOpen && !(!isStickersExpanded && editField.length() > 0) && emojiView?.areThereAnyStickers() == true && !waitingForKeyboardOpen
			}
		})

		sizeNotifierLayout?.addView(emojiView, sizeNotifierLayout.childCount - 5)

		checkChannelRights()
	}

	override fun onStickerSelected(sticker: TLRPC.Document, query: String, parent: Any, sendAnimationData: SendAnimationData?, clearsInputField: Boolean, notify: Boolean, scheduleDate: Int) {
		if (isInScheduleMode && scheduleDate == 0) {
			AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment!!.dialogId) { n: Boolean, s: Int ->
				onStickerSelected(sticker, query, parent, sendAnimationData, clearsInputField, n, s)
			}
		}
		else {
			if (slowModeTimer > 0 && !isInScheduleMode) {
				delegate?.onUpdateSlowModeButton(slowModeButton, true, slowModeButton.getText())
				return
			}

			if (searchingType != 0) {
				setSearchingTypeInternal(0, true)
				emojiView?.closeSearch(true)
				emojiView?.hideSearchKeyboard()
			}

			setStickersExpanded(expanded = false, animated = true, byDrag = false)

			SendMessagesHelper.getInstance(currentAccount).sendSticker(sticker, query, dialog_id, replyingMessageObject, threadMessage, parent, sendAnimationData, notify, scheduleDate, parent is TLRPC.TL_messages_stickerSet)

			delegate?.onMessageSend(null, true, scheduleDate)

			if (clearsInputField) {
				fieldText = ""
			}

			MediaDataController.getInstance(currentAccount).addRecentSticker(MediaDataController.TYPE_IMAGE, parent, sticker, (System.currentTimeMillis() / 1000).toInt(), false)
		}
	}

	override fun canSchedule(): Boolean {
		return parentFragment != null && parentFragment.canScheduleMessage()
	}

	override fun isInScheduleMode(): Boolean {
		return parentFragment != null && parentFragment.isInScheduleMode
	}

	fun addStickerToRecent(sticker: TLRPC.Document?) {
		createEmojiView()
		emojiView?.addRecentSticker(sticker)
	}

	fun hideEmojiView() {
		if (!emojiViewVisible && emojiView != null && emojiView?.visibility != GONE) {
			sizeNotifierLayout?.removeView(emojiView)
			emojiView?.visibility = GONE
			emojiView?.setShowing(false)
		}
	}

	fun showEmojiView() {
		showPopup(1, 0)
	}

	private fun showPopup(show: Int, contentType: Int, allowAnimation: Boolean = true) {
		if (show == 2) {
			return
		}

		if (show == 1) {
			if (contentType == 0) {
				createEmojiView()
			}

			var currentView: View? = null
			var previousHeight = 0

			if (contentType == 0) {
				emojiView?.let {
					if (it.parent == null) {
						sizeNotifierLayout?.addView(it, sizeNotifierLayout.childCount - 5)
					}
				}

				emojiView?.visibility = VISIBLE

				emojiViewVisible = true

				if (botKeyboardView != null && botKeyboardView?.visibility != GONE) {
					botKeyboardView?.visibility = GONE
					botKeyboardViewVisible = false
					previousHeight = botKeyboardView?.measuredHeight ?: 0
				}

				emojiView?.setShowing(true)

				currentView = emojiView
				animatingContentType = 0
			}
			else if (contentType == POPUP_CONTENT_BOT_KEYBOARD) {
				botKeyboardViewVisible = true

				if (emojiView != null && emojiView?.visibility != GONE) {
					sizeNotifierLayout?.removeView(emojiView)
					emojiView?.visibility = GONE
					emojiView?.setShowing(false)
					emojiViewVisible = false
					previousHeight = emojiView?.measuredHeight ?: 0
				}

				botKeyboardView?.visibility = VISIBLE
				currentView = botKeyboardView
				animatingContentType = POPUP_CONTENT_BOT_KEYBOARD
			}

			currentPopupContentType = contentType

			if (keyboardHeight <= 0) {
				keyboardHeight = MessagesController.getGlobalEmojiSettings().getInt("kbd_height", AndroidUtilities.dp(200f))
			}

			if (keyboardHeightLand <= 0) {
				keyboardHeightLand = MessagesController.getGlobalEmojiSettings().getInt("kbd_height_land3", AndroidUtilities.dp(200f))
			}

			var currentHeight = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) keyboardHeightLand else keyboardHeight

			if (contentType == POPUP_CONTENT_BOT_KEYBOARD) {
				currentHeight = min(botKeyboardView!!.keyboardHeight, currentHeight)
			}

			botKeyboardView?.setPanelHeight(currentHeight)

			currentView?.updateLayoutParams<LayoutParams> {
				height = currentHeight
			}

			if (!AndroidUtilities.isInMultiwindow) {
				AndroidUtilities.hideKeyboard(editField)
			}

			if (sizeNotifierLayout != null) {
				emojiPadding = currentHeight

				sizeNotifierLayout.requestLayout()
				setEmojiButtonImage(animated = true)
				updateBotButton(true)
				onWindowSizeChanged()

				if (smoothKeyboard && !isKeyboardVisible && currentHeight != previousHeight && allowAnimation) {
					panelAnimation = AnimatorSet()
					currentView?.translationY = (currentHeight - previousHeight).toFloat()
					panelAnimation?.playTogether(ObjectAnimator.ofFloat(currentView, TRANSLATION_Y, (currentHeight - previousHeight).toFloat(), 0f))
					panelAnimation?.interpolator = AdjustPanLayoutHelper.keyboardInterpolator
					panelAnimation?.duration = AdjustPanLayoutHelper.keyboardDuration

					panelAnimation?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							panelAnimation = null
							delegate?.bottomPanelTranslationYChanged(0f)
							NotificationCenter.getInstance(currentAccount).onAnimationFinish(notificationsIndex)
							requestLayout()
						}
					})

					AndroidUtilities.runOnUIThread(runEmojiPanelAnimation, 50)
					notificationsIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(notificationsIndex, null)
					requestLayout()
				}
			}
		}
		else {
			setEmojiButtonImage(animated = true)

			currentPopupContentType = -1

			if (emojiView != null) {
				if (show != 2 || AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow) {
					if (smoothKeyboard && !isKeyboardVisible && !isStickersExpanded) {
						if (true.also { emojiViewVisible = true }) {
							animatingContentType = 0
						}

						emojiView?.setShowing(false)

						panelAnimation = AnimatorSet()
						panelAnimation?.playTogether(ObjectAnimator.ofFloat(emojiView, TRANSLATION_Y, emojiView!!.measuredHeight.toFloat()))
						panelAnimation?.interpolator = AdjustPanLayoutHelper.keyboardInterpolator
						panelAnimation?.duration = AdjustPanLayoutHelper.keyboardDuration

						panelAnimation?.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								if (show == 0) {
									emojiPadding = 0
								}

								panelAnimation = null

								if (emojiView != null) {
									emojiView?.translationY = 0f
									emojiView?.visibility = GONE

									sizeNotifierLayout?.removeView(emojiView)

									if (removeEmojiViewAfterAnimation) {
										removeEmojiViewAfterAnimation = false
										emojiView = null
									}
								}

								delegate?.bottomPanelTranslationYChanged(0f)
								NotificationCenter.getInstance(currentAccount).onAnimationFinish(notificationsIndex)
								requestLayout()
							}
						})

						notificationsIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(notificationsIndex, null)
						AndroidUtilities.runOnUIThread(runEmojiPanelAnimation, 50)
						requestLayout()
					}
					else {
						delegate?.bottomPanelTranslationYChanged(0f)
						emojiPadding = 0
						sizeNotifierLayout?.removeView(emojiView)
						emojiView?.visibility = GONE
						emojiView?.setShowing(false)
					}
				}
				else {
					removeEmojiViewAfterAnimation = false
					delegate?.bottomPanelTranslationYChanged(0f)
					sizeNotifierLayout?.removeView(emojiView)
					emojiView = null
				}

				emojiViewVisible = false
			}

			if (botKeyboardView != null && botKeyboardView!!.visibility == VISIBLE) {
				if (show != 2 || AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow) {
					if (smoothKeyboard && !isKeyboardVisible) {
						if (botKeyboardViewVisible) {
							animatingContentType = 1
						}

						panelAnimation = AnimatorSet()
						panelAnimation?.playTogether(ObjectAnimator.ofFloat(botKeyboardView, TRANSLATION_Y, botKeyboardView!!.measuredHeight.toFloat()))
						panelAnimation?.interpolator = AdjustPanLayoutHelper.keyboardInterpolator
						panelAnimation?.duration = AdjustPanLayoutHelper.keyboardDuration

						panelAnimation?.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								if (show == 0) {
									emojiPadding = 0
								}

								panelAnimation = null

								botKeyboardView?.translationY = 0f
								botKeyboardView?.visibility = GONE

								NotificationCenter.getInstance(currentAccount).onAnimationFinish(notificationsIndex)
								delegate?.bottomPanelTranslationYChanged(0f)
								requestLayout()
							}
						})

						notificationsIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(notificationsIndex, null)
						AndroidUtilities.runOnUIThread(runEmojiPanelAnimation, 50)
						requestLayout()
					}
					else {
						if (!waitingForKeyboardOpen) {
							botKeyboardView?.visibility = GONE
						}
					}
				}

				botKeyboardViewVisible = false
			}

			if (sizeNotifierLayout != null) {
				if (!SharedConfig.smoothKeyboard && show == 0) {
					emojiPadding = 0
					sizeNotifierLayout.requestLayout()
					onWindowSizeChanged()
				}
			}

			updateBotButton(true)
		}

		if (stickersTabOpen || emojiTabOpen) {
			checkSendButton(true)
		}

		if (isStickersExpanded && show != 1) {
			setStickersExpanded(expanded = false, animated = false, byDrag = false)
		}

		updateFieldHint(false)
		checkBotMenu()
	}

	private fun setEmojiButtonImage(animated: Boolean) {
		// @Suppress("NAME_SHADOWING") var animated = animated

		val showingRecordInterface = recordInterfaceState == 1 && recordedAudioPanel.visibility == VISIBLE

		if (showingRecordInterface) {
			emojiButton.scaleX = 0f
			emojiButton.scaleY = 0f
			emojiButton.alpha = 0f
			// animated = false
		}

		//		ChatActivityEnterViewAnimatedIconView.State nextIcon;
//		if (byOpen && currentPopupContentType == 0) {
//			nextIcon = ChatActivityEnterViewAnimatedIconView.State.KEYBOARD;
//		}
//		else {
//			int currentPage;
//			if (emojiView == null) {
//				currentPage = MessagesController.getGlobalEmojiSettings().getInt("selected_page", 0);
//			}
//			else {
//				currentPage = emojiView.getCurrentPage();
//			}
//			if (currentPage == 0 || !allowStickers && !allowGifs) {
//				nextIcon = ChatActivityEnterViewAnimatedIconView.State.SMILE;
//			}
//			else if (messageEditText != null && !TextUtils.isEmpty(messageEditText.getText())) {
//				nextIcon = ChatActivityEnterViewAnimatedIconView.State.SMILE;
//			}
//			else {
//				if (currentPage == 1) {
//					nextIcon = ChatActivityEnterViewAnimatedIconView.State.STICKER;
//				}
//				else {
//					nextIcon = ChatActivityEnterViewAnimatedIconView.State.GIF;
//				}
//			}
//		}
//
//		emojiButton.setState(nextIcon, animated);
//		onEmojiIconChanged(nextIcon);
	}

	@JvmOverloads
	fun hidePopup(byBackButton: Boolean, forceAnimate: Boolean = false): Boolean {
		if (isPopupShowing) {
			if (currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD && byBackButton && botButtonsMessageObject != null) {
				return false
			}

			if (byBackButton && searchingType != 0 || forceAnimate) {
				setSearchingTypeInternal(0, true)
				emojiView?.closeSearch(true)
				editField.requestFocus()
				setStickersExpanded(expanded = false, animated = true, byDrag = false)

				if (emojiTabOpen) {
					checkSendButton(true)
				}
			}
			else {
				if (searchingType != 0) {
					setSearchingTypeInternal(0, false)
					emojiView?.closeSearch(false)
					editField.requestFocus()
				}

				showPopup(0, 0)
			}

			return true
		}

		return false
	}

	private fun setSearchingTypeInternal(searchingType: Int, animated: Boolean) {
		val showSearchingNew = searchingType != 0
		val showSearchingOld = this.searchingType != 0

		if (showSearchingNew != showSearchingOld) {
			searchAnimator?.removeAllListeners()
			searchAnimator?.cancel()

			if (!animated) {
				searchToOpenProgress = if (showSearchingNew) 1f else 0f
				emojiView?.searchProgressChanged()
			}
			else {
				searchAnimator = ValueAnimator.ofFloat(searchToOpenProgress, if (showSearchingNew) 1f else 0f)

				searchAnimator?.addUpdateListener {
					searchToOpenProgress = it.animatedValue as Float
					emojiView?.searchProgressChanged()
				}

				searchAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						searchToOpenProgress = if (showSearchingNew) 1f else 0f
						emojiView?.searchProgressChanged()
					}
				})

				searchAnimator?.duration = 220
				searchAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
				searchAnimator?.start()
			}
		}

		this.searchingType = searchingType
	}

	private fun openKeyboardInternal() {
		if (hasBotWebView() && botCommandsMenuIsShowing()) {
			return
		}

		showPopup(if (AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow || parentFragment != null && parentFragment.isInBubbleMode || isPaused) 0 else 2, 0)

		editField.requestFocus()

		AndroidUtilities.showKeyboard(editField)

		if (isPaused) {
			showKeyboardOnResume = true
		}
		else if (!AndroidUtilities.usingHardwareInput && !isKeyboardVisible && !AndroidUtilities.isInMultiwindow && (parentFragment == null || !parentFragment.isInBubbleMode)) {
			waitingForKeyboardOpen = true
			emojiView?.onTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0f, 0f, 0))
			AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
			AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100)
		}
	}

	val isEditingMessage: Boolean
		get() = editingMessageObject != null

	fun hasAudioToSend(): Boolean {
		return audioToSendMessageObject != null || videoToSendMessageObject != null
	}

	fun openKeyboard() {
		if (hasBotWebView() && botCommandsMenuIsShowing()) {
			return
		}

		if (!AndroidUtilities.showKeyboard(editField)) {
			editField.clearFocus()
			editField.requestFocus()
		}
	}

	fun closeKeyboard() {
		AndroidUtilities.hideKeyboard(editField)
	}

	val isPopupShowing: Boolean
		get() = emojiViewVisible || botKeyboardViewVisible

	fun addRecentGif(searchImage: TLRPC.Document?) {
		MediaDataController.getInstance(currentAccount).addRecentGif(searchImage, (System.currentTimeMillis() / 1000).toInt(), true)
		emojiView?.addRecentGif(searchImage)
	}

	//	protected void onEmojiIconChanged(ChatActivityEnterViewAnimatedIconView.State currentIcon) {
	//		if (currentIcon == ChatActivityEnterViewAnimatedIconView.State.GIF && emojiView == null) {
	//			MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, true, true, false);
	//			final ArrayList<String> gifSearchEmojies = MessagesController.getInstance(currentAccount).gifSearchEmojies;
	//			for (int i = 0, N = Math.min(10, gifSearchEmojies.size()); i < N; i++) {
	//				Emoji.preloadEmoji(gifSearchEmojies.get(i));
	//			}
	//		}
	//	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)

		if (w != oldw && isStickersExpanded) {
			setSearchingTypeInternal(0, false)
			emojiView?.closeSearch(false)
			setStickersExpanded(expanded = false, animated = false, byDrag = false)
		}

		videoTimelineView.clearFrames()
	}

	override fun onSizeChanged(height: Int, isWidthGreater: Boolean) {
		if (searchingType != 0) {
			lastSizeChangeValue1 = height
			lastSizeChangeValue2 = isWidthGreater
			isKeyboardVisible = height > 0
			checkBotMenu()
			return
		}

		if (height > AndroidUtilities.dp(50f) && isKeyboardVisible && !AndroidUtilities.isInMultiwindow) {
			if (isWidthGreater) {
				keyboardHeightLand = height
				MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height_land3", keyboardHeightLand).commit()
			}
			else {
				keyboardHeight = height
				MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height", keyboardHeight).commit()
			}
		}

		if (isKeyboardVisible) {
			if (emojiViewVisible && emojiView == null) {
				emojiViewVisible = false
			}
		}

		if (isPopupShowing) {
			var newHeight = if (isWidthGreater) keyboardHeightLand else keyboardHeight

			if (currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD && botKeyboardView?.isFullSize == false) {
				newHeight = min(botKeyboardView?.keyboardHeight ?: 0, newHeight)
			}

			var currentView: View? = null

			if (currentPopupContentType == 0) {
				currentView = emojiView
			}
			else if (currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD) {
				currentView = botKeyboardView
			}

			botKeyboardView?.setPanelHeight(newHeight)

			if (currentView != null) {
				val layoutParams = currentView.layoutParams as LayoutParams

				if (!closeAnimationInProgress && (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) && !isStickersExpanded) {
					layoutParams.width = AndroidUtilities.displaySize.x
					layoutParams.height = newHeight

					currentView.layoutParams = layoutParams

					if (sizeNotifierLayout != null) {
						val oldHeight = emojiPadding
						emojiPadding = layoutParams.height

						sizeNotifierLayout.requestLayout()

						onWindowSizeChanged()

						if (smoothKeyboard && !isKeyboardVisible && oldHeight != emojiPadding && panelAnimationEnabled()) {
							panelAnimation = AnimatorSet()
							panelAnimation?.playTogether(ObjectAnimator.ofFloat(currentView, TRANSLATION_Y, (emojiPadding - oldHeight).toFloat(), 0f))
							panelAnimation?.interpolator = AdjustPanLayoutHelper.keyboardInterpolator
							panelAnimation?.duration = AdjustPanLayoutHelper.keyboardDuration

							panelAnimation?.addListener(object : AnimatorListenerAdapter() {
								override fun onAnimationEnd(animation: Animator) {
									panelAnimation = null
									delegate?.bottomPanelTranslationYChanged(0f)
									requestLayout()
									NotificationCenter.getInstance(currentAccount).onAnimationFinish(notificationsIndex)
								}
							})

							AndroidUtilities.runOnUIThread(runEmojiPanelAnimation, 50)

							notificationsIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(notificationsIndex, null)

							requestLayout()
						}
					}
				}
			}
		}

		if (lastSizeChangeValue1 == height && lastSizeChangeValue2 == isWidthGreater) {
			onWindowSizeChanged()
			return
		}

		lastSizeChangeValue1 = height
		lastSizeChangeValue2 = isWidthGreater

		val oldValue = isKeyboardVisible

		isKeyboardVisible = height > 0

		checkBotMenu()

		if (isKeyboardVisible && isPopupShowing && stickersExpansionAnim == null) {
			showPopup(0, currentPopupContentType)
		}
		else if (!isKeyboardVisible && !isPopupShowing && botButtonsMessageObject != null && replyingMessageObject !== botButtonsMessageObject && (!hasBotWebView() || !botCommandsMenuIsShowing()) && TextUtils.isEmpty(editField.text)) {
			if (sizeNotifierLayout?.adjustPanLayoutHelper?.animationInProgress() == true) {
				sizeNotifierLayout.adjustPanLayoutHelper?.stopTransition()
			}
			else {
				sizeNotifierLayout?.adjustPanLayoutHelper?.ignoreOnce()
			}

			showPopup(1, POPUP_CONTENT_BOT_KEYBOARD, false)
		}

		if (emojiPadding != 0 && !isKeyboardVisible && isKeyboardVisible != oldValue && !isPopupShowing) {
			emojiPadding = 0
			sizeNotifierLayout!!.requestLayout()
		}

		if (isKeyboardVisible && waitingForKeyboardOpen) {
			waitingForKeyboardOpen = false

			if (clearBotButtonsOnKeyboardOpen) {
				clearBotButtonsOnKeyboardOpen = false
				botKeyboardView?.setButtons(botReplyMarkup)
			}

			AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
		}

		onWindowSizeChanged()
	}

	val visibleEmojiPadding: Int
		get() = if (emojiViewVisible) emojiPadding else 0

	private val threadMessage: MessageObject?
		get() = parentFragment?.threadMessage

	private val threadMessageId: Int
		get() = parentFragment?.threadMessage?.id ?: 0

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.emojiLoaded) {
			emojiView?.invalidateViews()
			botKeyboardView?.invalidateViews()

			editField.postInvalidate()
			editField.invalidateForce()
		}
		else if (id == NotificationCenter.recordProgressChanged) {
			val guid = args[0] as Int

			if (guid != recordingGuid) {
				return
			}

			if (recordInterfaceState != 0 && !wasSendTyping && !isInScheduleMode) {
				wasSendTyping = true
				accountInstance.messagesController.sendTyping(dialog_id, threadMessageId, if (isInVideoMode) 7 else 1, 0)
			}

			recordCircle.setAmplitude(args[1] as Double)
		}
		else if (id == NotificationCenter.closeChats) {
			if (editField.isFocused) {
				AndroidUtilities.hideKeyboard(editField)
			}
		}
		else if (id == NotificationCenter.recordStartError || id == NotificationCenter.recordStopped) {
			val guid = args[0] as Int

			if (guid != recordingGuid) {
				return
			}

			if (recordingAudioVideo) {
				recordingAudioVideo = false

				if (id == NotificationCenter.recordStopped) {
					val reason = args[1] as Int

					val state = if (reason == 4) {
						RECORD_STATE_CANCEL_BY_TIME
					}
					else if (isInVideoMode && reason == 5) {
						RECORD_STATE_SENDING
					}
					else {
						when (reason) {
							0 -> RECORD_STATE_CANCEL_BY_GESTURE
							6 -> RECORD_STATE_CANCEL
							else -> RECORD_STATE_PREPARING
						}
					}

					if (state != RECORD_STATE_PREPARING) {
						updateRecordInterface(state)
					}
				}
				else {
					updateRecordInterface(RECORD_STATE_CANCEL)
				}
			}

//			if (id == NotificationCenter.recordStopped) {
//				val reason = args[1] as Int
//			}
		}
		else if (id == NotificationCenter.recordStarted) {
			val guid = args[0] as Int

			if (guid != recordingGuid) {
				return
			}

			val audio = args[1] as Boolean
			isInVideoMode = !audio

			audioVideoSendButton.setState(if (audio) ChatActivityEnterViewAnimatedIconView.State.VOICE else ChatActivityEnterViewAnimatedIconView.State.VIDEO, true)

			if (!recordingAudioVideo) {
				recordingAudioVideo = true
				updateRecordInterface(RECORD_STATE_ENTER)
			}
			else {
				recordCircle.showWaves(b = true, animated = true)
			}

			recordTimerView.start()
			recordDot.enterAnimation = false
		}
		else if (id == NotificationCenter.audioDidSent) {
			val guid = args[0] as Int

			if (guid != recordingGuid) {
				return
			}

			val audio = args[1]

			if (audio is VideoEditedInfo) {
				videoToSendMessageObject = audio
				audioToSendPath = args[2] as String

				val keyframes = args[3] as List<Bitmap>

				videoTimelineView.setVideoPath(audioToSendPath)
				videoTimelineView.setKeyframes(keyframes)
				videoTimelineView.visibility = VISIBLE
				videoTimelineView.setMinProgressDiff(1000.0f / videoToSendMessageObject!!.estimatedDuration)

				updateRecordInterface(RECORD_STATE_PREPARING)

				checkSendButton(false)
			}
			else {
				audioToSend = args[1] as? TLRPC.TL_document
				audioToSendPath = args[2] as? String

				if (audioToSend != null) {
					val message = TL_message()
					message.out = true
					message.id = 0
					message.peer_id = TLRPC.TL_peerUser()
					message.from_id = TLRPC.TL_peerUser()
					message.from_id?.user_id = getInstance(currentAccount).getClientUserId()
					message.peer_id?.user_id = message.from_id?.user_id
					message.date = (System.currentTimeMillis() / 1000).toInt()
					message.message = ""
					message.attachPath = audioToSendPath
					message.media = TLRPC.TL_messageMediaDocument()
					message.media?.flags = message.media!!.flags or 3
					message.media?.document = audioToSend
					message.flags = message.flags or (TLRPC.MESSAGE_FLAG_HAS_MEDIA or TLRPC.MESSAGE_FLAG_HAS_FROM_ID)

					audioToSendMessageObject = MessageObject(UserConfig.selectedAccount, message, generateLayout = false, checkMediaExists = true)

					recordedAudioPanel.alpha = 1.0f
					recordedAudioPanel.visibility = VISIBLE

					recordDeleteImageView.visibility = VISIBLE
					recordDeleteImageView.alpha = 0f
					recordDeleteImageView.scaleY = 0f
					recordDeleteImageView.scaleX = 0f

					val duration = audioToSend?.attributes?.firstOrNull { it is TLRPC.TL_documentAttributeAudio }?.duration ?: 0

					audioToSend?.attributes?.firstOrNull { it is TLRPC.TL_documentAttributeAudio }?.let {
						if (it.waveform == null || it.waveform.isEmpty()) {
							it.waveform = MediaController.getInstance().getWaveform(audioToSendPath)
						}

						recordedAudioSeekBar.setWaveform(it.waveform)
					}

					recordedAudioTimeTextView.text = AndroidUtilities.formatShortDuration(duration)
					checkSendButton(false)
					updateRecordInterface(RECORD_STATE_PREPARING)
				}
				else {
					delegate?.onMessageSend(null, true, 0)
				}
			}
		}
		else if (id == NotificationCenter.audioRouteChanged) {
			val frontSpeaker = args[0] as Boolean
			parentActivity.volumeControlStream = if (frontSpeaker) AudioManager.STREAM_VOICE_CALL else AudioManager.USE_DEFAULT_STREAM_TYPE
		}
		else if (id == NotificationCenter.messagePlayingDidReset) {
			if (audioToSendMessageObject != null && !MediaController.getInstance().isPlayingMessage(audioToSendMessageObject)) {
				playPauseDrawable!!.setIcon(MediaActionDrawable.ICON_PLAY, true)
				recordedAudioPlayButton.contentDescription = context.getString(R.string.AccActionPlay)
				recordedAudioSeekBar.setProgress(0f)
			}
		}
		else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
			// val mid = args[0] as Int

			if (audioToSendMessageObject != null && MediaController.getInstance().isPlayingMessage(audioToSendMessageObject)) {
				val player = MediaController.getInstance().playingMessageObject

				audioToSendMessageObject?.audioProgress = player?.audioProgress ?: 0f
				audioToSendMessageObject?.audioProgressSec = player?.audioProgressSec ?: 0

				if (!recordedAudioSeekBar.isDragging) {
					recordedAudioSeekBar.setProgress(audioToSendMessageObject?.audioProgress ?: 0f)
				}
			}
		}
		else if (id == NotificationCenter.featuredStickersDidLoad) {
			emojiButton.invalidate()
		}
		else if (id == NotificationCenter.messageReceivedByServer) {
			val scheduled = args[6] as Boolean

			if (scheduled) {
				return
			}

			val did = args[3] as Long

			if (did == dialog_id && info != null && info!!.slowmode_seconds != 0) {
				val chat = accountInstance.messagesController.getChat(info!!.id)

				if (chat != null && !ChatObject.hasAdminRights(chat)) {
					info?.slowmode_next_send_date = ConnectionsManager.getInstance(currentAccount).currentTime + info!!.slowmode_seconds
					info?.flags = info!!.flags or 262144
					setSlowModeTimer(info!!.slowmode_next_send_date)
				}
			}
		}
		else if (id == NotificationCenter.sendingMessagesChanged) {
			if (info != null) {
				updateSlowModeText()
			}
		}
		else if (id == NotificationCenter.audioRecordTooShort) {
			updateRecordInterface(RECORD_STATE_CANCEL_BY_TIME)
		}
		else if (id == NotificationCenter.updateBotMenuButton) {
			val botId = args[0] as Long
			val botMenuButton = args[1] as TLRPC.BotMenuButton

			if (botId == dialog_id) {
				if (botMenuButton is TLRPC.TL_botMenuButton) {
					botMenuWebViewTitle = botMenuButton.text
					botMenuWebViewUrl = botMenuButton.url
					botMenuButtonType = BotMenuButtonType.WEB_VIEW
				}
				else if (hasBotCommands) {
					botMenuButtonType = BotMenuButtonType.COMMANDS
				}
				else {
					botMenuButtonType = BotMenuButtonType.NO_BUTTON
				}

				updateBotButton(false)
			}
		}
		else if (id == NotificationCenter.aiBotUpdate) {
			val currentMode = if (args[0] == true) "easy" else "advanced"
			FileLog.d("aibot changedMode: $currentMode")
		}
	}

	fun onRequestPermissionsResultFragment(requestCode: Int, grantResults: IntArray?) {
		if (requestCode == 2) {
			if (pendingLocationButton != null) {
				if (grantResults?.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
					SendMessagesHelper.getInstance(currentAccount).sendCurrentLocation(pendingMessageObject, pendingLocationButton)
				}

				pendingLocationButton = null
				pendingMessageObject = null
			}
		}
	}

	private fun checkStickersExpandHeight() {
		val emojiView = emojiView ?: return
		val origHeight = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) keyboardHeightLand else keyboardHeight
		var newHeight = originalViewHeight - AndroidUtilities.statusBarHeight - ActionBar.getCurrentActionBarHeight() - height + Theme.chat_composeShadowDrawable.intrinsicHeight

		if (searchingType == 2) {
			newHeight = min(newHeight, AndroidUtilities.dp(120f) + origHeight)
		}

		val currentHeight = emojiView.layoutParams.height

		if (currentHeight == newHeight) {
			return
		}

		stickersExpansionAnim?.cancel()
		stickersExpansionAnim = null

		stickersExpandedHeight = newHeight
		if (currentHeight > newHeight) {
			val animations = AnimatorSet()
			animations.playTogether(ObjectAnimator.ofInt(this, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)), ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)))

			(animations.childAnimations[0] as ObjectAnimator).addUpdateListener {
				sizeNotifierLayout?.invalidate()
			}

			animations.duration = 300
			animations.interpolator = CubicBezierInterpolator.DEFAULT

			animations.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					stickersExpansionAnim = null
					emojiView.layoutParams.height = stickersExpandedHeight
					emojiView.setLayerType(LAYER_TYPE_NONE, null)
				}
			})

			stickersExpansionAnim = animations

			emojiView.setLayerType(LAYER_TYPE_HARDWARE, null)

			animations.start()
		}
		else {
			emojiView.layoutParams.height = stickersExpandedHeight
			sizeNotifierLayout?.requestLayout()

			val start = editField.selectionStart
			val end = editField.selectionEnd

			editField.text = editField.text // dismiss action mode, if any
			editField.setSelection(start, end)

			val animations = AnimatorSet()
			animations.playTogether(ObjectAnimator.ofInt(this, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)), ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)))

			(animations.childAnimations[0] as ObjectAnimator).addUpdateListener {
				sizeNotifierLayout?.invalidate()
			}

			animations.duration = 300
			animations.interpolator = CubicBezierInterpolator.DEFAULT

			animations.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					stickersExpansionAnim = null
					emojiView.setLayerType(LAYER_TYPE_NONE, null)
				}
			})

			stickersExpansionAnim = animations
			emojiView.setLayerType(LAYER_TYPE_HARDWARE, null)
			animations.start()
		}
	}

	fun setStickersExpanded(expanded: Boolean, animated: Boolean, byDrag: Boolean) {
		setStickersExpanded(expanded, animated, byDrag, true)
	}

	fun setStickersExpanded(expanded: Boolean, animated: Boolean, byDrag: Boolean, stopAllHeavy: Boolean) {
		if (adjustPanLayoutHelper != null && adjustPanLayoutHelper!!.animationInProgress() || waitingForKeyboardOpenAfterAnimation) {
			return
		}

		if (emojiView == null || !byDrag && isStickersExpanded == expanded) {
			return
		}

		isStickersExpanded = expanded

		delegate?.onStickersExpandedChange()

		val origHeight = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) keyboardHeightLand else keyboardHeight

		stickersExpansionAnim?.cancel()
		stickersExpansionAnim = null

		if (isStickersExpanded) {
			if (stopAllHeavy) {
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.stopAllHeavyOperations, 1)
			}

			originalViewHeight = sizeNotifierLayout?.height ?: 0
			stickersExpandedHeight = originalViewHeight - AndroidUtilities.statusBarHeight - ActionBar.getCurrentActionBarHeight() - height + Theme.chat_composeShadowDrawable.intrinsicHeight

			if (searchingType == 2) {
				stickersExpandedHeight = min(stickersExpandedHeight, AndroidUtilities.dp(120f) + origHeight)
			}

			emojiView?.layoutParams?.height = stickersExpandedHeight

			sizeNotifierLayout?.requestLayout()
			sizeNotifierLayout?.foreground = ScrimDrawable()

			val start = editField.selectionStart
			val end = editField.selectionEnd

			editField.text = editField.text // dismiss action mode, if any
			editField.setSelection(start, end)

			if (animated) {
				val animations = AnimatorSet()
				animations.playTogether(ObjectAnimator.ofInt(this, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)), ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)), ObjectAnimator.ofFloat(stickersArrow, "animationProgress", 1f))
				animations.duration = 300
				animations.interpolator = CubicBezierInterpolator.DEFAULT

				(animations.childAnimations[0] as ObjectAnimator).addUpdateListener {
					stickersExpansionProgress = abs(translationY / -(stickersExpandedHeight - origHeight))
					sizeNotifierLayout?.invalidate()
				}

				animations.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						stickersExpansionAnim = null
						emojiView?.setLayerType(LAYER_TYPE_NONE, null)
						NotificationCenter.getInstance(currentAccount).onAnimationFinish(notificationsIndex)
					}
				})

				stickersExpansionAnim = animations
				emojiView?.setLayerType(LAYER_TYPE_HARDWARE, null)
				notificationsIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(notificationsIndex, null)
				stickersExpansionProgress = 0f
				sizeNotifierLayout?.invalidate()
				animations.start()
			}
			else {
				stickersExpansionProgress = 1f
				translationY = -(stickersExpandedHeight - origHeight).toFloat()
				emojiView?.translationY = -(stickersExpandedHeight - origHeight).toFloat()
				stickersArrow.animationProgress = 1f
			}
		}
		else {
			if (stopAllHeavy) {
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.startAllHeavyOperations, 1)
			}

			if (animated) {
				closeAnimationInProgress = true

				val anims = AnimatorSet()
				anims.playTogether(ObjectAnimator.ofInt(this, roundedTranslationYProperty, 0), ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, 0), ObjectAnimator.ofFloat(stickersArrow, "animationProgress", 0f))
				anims.duration = 300
				anims.interpolator = CubicBezierInterpolator.DEFAULT

				(anims.childAnimations[0] as ObjectAnimator).addUpdateListener {
					stickersExpansionProgress = translationY / -(stickersExpandedHeight - origHeight)
					sizeNotifierLayout?.invalidate()
				}

				anims.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						closeAnimationInProgress = false
						stickersExpansionAnim = null
						emojiView?.layoutParams?.height = origHeight
						emojiView?.setLayerType(LAYER_TYPE_NONE, null)

						if (sizeNotifierLayout != null) {
							sizeNotifierLayout.requestLayout()
							sizeNotifierLayout.foreground = null
							sizeNotifierLayout.setWillNotDraw(false)
						}

						if (isKeyboardVisible && isPopupShowing) {
							showPopup(0, currentPopupContentType)
						}

						onEmojiSearchClosed?.run()
						onEmojiSearchClosed = null

						NotificationCenter.getInstance(currentAccount).onAnimationFinish(notificationsIndex)
					}
				})

				stickersExpansionProgress = 1f
				sizeNotifierLayout?.invalidate()
				stickersExpansionAnim = anims
				emojiView?.setLayerType(LAYER_TYPE_HARDWARE, null)
				notificationsIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(notificationsIndex, null)
				anims.start()
			}
			else {
				stickersExpansionProgress = 0f
				translationY = 0f
				emojiView?.translationY = 0f
				emojiView?.layoutParams?.height = origHeight
				sizeNotifierLayout?.requestLayout()
				sizeNotifierLayout?.foreground = null
				sizeNotifierLayout?.setWillNotDraw(false)
				stickersArrow.animationProgress = 0f
			}
		}

		if (expanded) {
			expandStickersButton.contentDescription = context.getString(R.string.AccDescrCollapsePanel)
		}
		else {
			expandStickersButton.contentDescription = context.getString(R.string.AccDescrExpandPanel)
		}
	}

	fun swipeToBackEnabled(): Boolean {
		if (recordingAudioVideo) {
			return false
		}

		return if (isInVideoMode && recordedAudioPanel.visibility == VISIBLE) {
			false
		}
		else {
			!hasBotWebView() || !botCommandsMenuButton!!.isOpened()
		}
	}

	val heightWithTopView: Int
		get() {
			var h = measuredHeight

			if (topView != null && topView!!.visibility == VISIBLE) {
				h -= ((1f - topViewEnterProgress) * ChatActivityEnterTopView.heightDp).toInt()
			}

			return h
		}

	fun panelAnimationInProgress(): Boolean {
		return panelAnimation != null
	}

	val topViewTranslation: Float
		get() = if (topView == null || topView?.visibility == GONE) {
			0f
		}
		else {
			topView?.translationY ?: 0f
		}

	open fun checkAnimation() {
		// stub
	}

	protected open fun panelAnimationEnabled(): Boolean {
		return true
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (botCommandsMenuButton?.tag != null) {
			botCommandsMenuButton?.measure(widthMeasureSpec, heightMeasureSpec)
			(editField.layoutParams as MarginLayoutParams).leftMargin = botCommandsMenuButton!!.measuredWidth + AndroidUtilities.dp(20f)
		}
		else if (senderSelectView.visibility == VISIBLE) {
			val width = senderSelectView.layoutParams.width
			val height = senderSelectView.layoutParams.height
			senderSelectView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
			(editField.layoutParams as MarginLayoutParams).leftMargin = AndroidUtilities.dp(63f) + width
		}
		else {
			(editField.layoutParams as MarginLayoutParams).leftMargin = AndroidUtilities.dp(50f)
		}

		if (botCommandsMenuContainer != null) {
			val padding = if ((botCommandsAdapter?.itemCount ?: 0) > 4) {
				max(0, sizeNotifierLayout!!.measuredHeight - AndroidUtilities.dp(8 + 36 * 4.3f))
			}
			else {
				max(0, sizeNotifierLayout!!.measuredHeight - AndroidUtilities.dp((8 + 36 * max(1, min(4, botCommandsAdapter!!.itemCount))).toFloat()))
			}

			if (botCommandsMenuContainer?.listView?.paddingTop != padding) {
				botCommandsMenuContainer?.listView?.topGlowOffset = padding

				if (botCommandLastPosition == -1 && botCommandsMenuContainer?.visibility == VISIBLE && botCommandsMenuContainer?.listView?.layoutManager != null) {
					val layoutManager = botCommandsMenuContainer?.listView?.layoutManager as? LinearLayoutManager
					val p = layoutManager?.findFirstVisibleItemPosition() ?: -1

					if (p >= 0) {
						val view = layoutManager?.findViewByPosition(p)

						if (view != null) {
							botCommandLastPosition = p
							botCommandLastTop = view.top - (botCommandsMenuContainer?.listView?.paddingTop ?: 0)
						}
					}
				}

				botCommandsMenuContainer?.listView?.setPadding(0, padding, 0, AndroidUtilities.dp(8f))
			}
		}

		super.onMeasure(widthMeasureSpec, heightMeasureSpec)

		botCommandsMenuButton?.let {
			botWebViewButton.setMeasuredButtonWidth(it.measuredWidth)
		}

		botWebViewButton.layoutParams.height = measuredHeight - AndroidUtilities.dp(2f)

		measureChild(botWebViewButton, widthMeasureSpec, heightMeasureSpec)

		botWebViewMenuContainer?.let {
			it.updateLayoutParams<MarginLayoutParams> {
				bottomMargin = editField.measuredHeight
			}

			measureChild(it, widthMeasureSpec, heightMeasureSpec)
		}
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)

		if (botCommandLastPosition != -1) {
			val layoutManager = botCommandsMenuContainer?.listView?.layoutManager as? LinearLayoutManager
			layoutManager?.scrollToPositionWithOffset(botCommandLastPosition, botCommandLastTop)
			botCommandLastPosition = -1
		}
	}

	private fun beginDelayedTransition() {
		animationParamsX[emojiButton] = emojiButton.x
		animationParamsX[editField] = editField.x
	}

	fun setBotInfo(botInfo: LongSparseArray<TLRPC.BotInfo>) {
		setBotInfo(botInfo, true)
	}

	fun setBotInfo(botInfo: LongSparseArray<TLRPC.BotInfo>, animate: Boolean) {
		if (botInfo.size() == 1 && botInfo.valueAt(0).user_id == dialog_id) {
			val info = botInfo.valueAt(0)
			val menuButton = info.menu_button

			if (menuButton is TLRPC.TL_botMenuButton) {
				botMenuWebViewTitle = menuButton.text
				botMenuWebViewUrl = menuButton.url
				botMenuButtonType = BotMenuButtonType.WEB_VIEW
			}
			else if (info.commands.isNotEmpty()) {
				botMenuButtonType = BotMenuButtonType.COMMANDS
			}
			else {
				botMenuButtonType = BotMenuButtonType.NO_BUTTON
			}
		}
		else {
			botMenuButtonType = BotMenuButtonType.NO_BUTTON
		}

		botCommandsAdapter?.setBotInfo(botInfo)

		updateBotButton(animate)
	}

	fun botCommandsMenuIsShowing(): Boolean {
		return botCommandsMenuButton?.isOpened() == true
	}

	fun hideBotCommands() {
		botCommandsMenuButton?.setOpened(false)

		if (hasBotWebView()) {
			botWebViewMenuContainer?.dismiss()
		}
		else {
			botCommandsMenuContainer?.dismiss()
		}
	}

	val topViewHeight: Float
		get() = if (topView?.visibility == VISIBLE) {
			ChatActivityEnterTopView.heightDp.toFloat()
		}
		else {
			0f
		}

	fun runEmojiPanelAnimation() {
		AndroidUtilities.cancelRunOnUIThread(runEmojiPanelAnimation)
		runEmojiPanelAnimation.run()
	}

	override fun dispatchDraw(canvas: Canvas) {
		if (emojiView == null || emojiView?.visibility != VISIBLE || emojiView?.stickersExpandOffset == 0f) {
			super.dispatchDraw(canvas)
		}
		else {
			canvas.save()
			canvas.clipRect(0, AndroidUtilities.dp(2f), measuredWidth, measuredHeight)
			canvas.translate(0f, -(emojiView?.stickersExpandOffset ?: 0f))
			super.dispatchDraw(canvas)
			canvas.restore()
		}
	}

	fun setChatSearchExpandOffset(chatSearchExpandOffset: Float) {
		this.chatSearchExpandOffset = chatSearchExpandOffset
		invalidate()
	}

	enum class BotMenuButtonType {
		NO_BUTTON, COMMANDS, WEB_VIEW
	}

	interface ChatActivityEnterViewDelegate {
		fun onBotCommandSelected(command: String) {}
		fun onEditTextScroll() {}
		fun onContextMenuOpen() {}
		fun onContextMenuClose() {}
		fun onMessageSend(message: CharSequence?, notify: Boolean, scheduleDate: Int)
		fun needSendTyping()
		fun onTextChanged(text: CharSequence?, bigChange: Boolean)
		fun onTextSelectionChanged(start: Int, end: Int)
		fun onTextSpansChanged(text: CharSequence?)
		fun onAttachButtonHidden()
		fun onAttachButtonShow()
		fun onWindowSizeChanged(size: Int)
		fun onStickersTab(opened: Boolean)
		fun onMessageEditEnd(loading: Boolean)
		fun didPressAttachButton()
		fun needStartRecordVideo(state: Int, notify: Boolean, scheduleDate: Int)
		fun needChangeVideoPreviewState(state: Int, seekProgress: Float)
		fun onSwitchRecordMode(video: Boolean)
		fun onPreAudioVideoRecord()
		fun needStartRecordAudio(state: Int)
		fun needShowMediaBanHint()
		fun onStickersExpandedChange()
		fun onUpdateSlowModeButton(button: View?, show: Boolean, time: CharSequence?)
		fun scrollToSendingMessage() {}
		fun openScheduledMessages() {}

		fun hasScheduledMessages(): Boolean {
			return true
		}

		fun onSendLongClick()
		fun onAudioVideoInterfaceUpdated()
		fun bottomPanelTranslationYChanged(translation: Float) {}
		fun prepareMessageSending() {}
		fun onTrendingStickersShowed(show: Boolean) {}
		fun hasForwardingMessages(): Boolean {
			return false
		}

		/**
		 * @return Height of the content view
		 */
		val contentViewHeight: Int
			get() = 0

		/**
		 * @return Measured keyboard height
		 */
		fun measureKeyboardHeight(): Int {
			return 0
		}

		/**
		 * @return A list of available peers to send messages as
		 */
		val sendAsPeers: TLRPC.TL_channels_sendAsPeers?
			get() = null
	}

	private inner class SeekBarWaveformView(context: Context?) : View(context) {
		init {
			seekBarWaveform = SeekBarWaveform()

			seekBarWaveform?.setDelegate(object : SeekBar.SeekBarDelegate {
				override fun onSeekBarDrag(progress: Float) {
					audioToSendMessageObject?.let {
						it.audioProgress = progress
						MediaController.getInstance().seekToProgress(it, progress)
					}
				}
			})
		}

		fun setWaveform(waveform: ByteArray?) {
			seekBarWaveform?.setWaveform(waveform)
			invalidate()
		}

		fun setProgress(progress: Float) {
			seekBarWaveform?.setProgress(progress)
			invalidate()
		}

		val isDragging: Boolean
			get() = seekBarWaveform?.isDragging ?: false

		override fun onTouchEvent(event: MotionEvent): Boolean {
			val result = seekBarWaveform?.onTouch(event.action, event.x, event.y) ?: false

			if (result) {
				if (event.action == MotionEvent.ACTION_DOWN) {
					requestDisallowInterceptTouchEvent(true)
				}

				invalidate()
			}

			return result || super.onTouchEvent(event)
		}

		override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
			super.onLayout(changed, left, top, right, bottom)
			seekBarWaveform?.setSize(right - left, bottom - top)
		}

		override fun onDraw(canvas: Canvas) {
			super.onDraw(canvas)
			val fillColor = ColorUtils.setAlphaComponent(ResourcesCompat.getColor(resources, R.color.white, null), 127)
			seekBarWaveform?.setColors(fillColor, ResourcesCompat.getColor(resources, R.color.white, null), ResourcesCompat.getColor(resources, R.color.white, null))
			seekBarWaveform?.draw(canvas, this)
		}
	}

	private inner class RecordDot(context: Context) : View(context) {
		var attachedToWindow = false
		var playing = false
		private val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.trash, null)!!.mutate()
		private var lastUpdateTime: Long = 0
		private var isIncr = false
		var enterAnimation = false

		init {
			// drawable.setCurrentParentView(this)
			// drawable.setInvalidateOnProgressSet(true)
			updateColors()
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			attachedToWindow = true

//			if (playing) {
//				drawable.start()
//			}

//			drawable.setMasterParent(this)
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()
			attachedToWindow = false
// 			drawable.stop()
// 			drawable.setMasterParent(null)
		}

		fun updateColors() {
			val dotColor = ResourcesCompat.getColor(resources, R.color.purple, null)
// 			val background = ResourcesCompat.getColor(resources, R.color.light_background, null)

			redDotPaint.color = dotColor

//			drawable.beginApplyLayerColors()
//			drawable.setLayerColor("Cup Red.**", dotColor)
//			drawable.setLayerColor("Box.**", dotColor)
//			drawable.setLayerColor("Line 1.**", background)
//			drawable.setLayerColor("Line 2.**", background)
//			drawable.setLayerColor("Line 3.**", background)
//			drawable.commitApplyLayerColors()

			playPauseDrawable?.color = ResourcesCompat.getColor(resources, R.color.white, null)
		}

		fun resetAlpha() {
			alpha = 1.0f
			lastUpdateTime = System.currentTimeMillis()
			isIncr = false
			playing = false
//			drawable.stop()
			invalidate()
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			drawable.setBounds(0, 0, measuredWidth, measuredHeight)
		}

		override fun onDraw(canvas: Canvas) {
			if (playing) {
				drawable.alpha = (255 * alpha).toInt()
			}

			redDotPaint.alpha = (255 * alpha).toInt()

			val dt = System.currentTimeMillis() - lastUpdateTime

			if (enterAnimation) {
				alpha = 1f
			}
			else {
				if (!isIncr && !playing) {
					alpha -= dt / 600.0f

					if (alpha <= 0) {
						alpha = 0f
						isIncr = true
					}
				}
				else {
					alpha += dt / 600.0f

					if (alpha >= 1) {
						alpha = 1f
						isIncr = false
					}
				}
			}

			lastUpdateTime = System.currentTimeMillis()
			if (playing) {
				drawable.draw(canvas)
			}

			if (!playing) { // || !drawable.hasBitmap()) {
				canvas.drawCircle((this.measuredWidth shr 1).toFloat(), (this.measuredHeight shr 1).toFloat(), AndroidUtilities.dp(5f).toFloat(), redDotPaint)
			}

			invalidate()
		}

		fun playDeleteAnimation() {
			playing = true
//			drawable.setProgress(0f)

//			if (attachedToWindow) {
//				drawable.start()
//			}
		}
	}

	inner class RecordCircle(context: Context) : View(context) {
		private val tooltipBackgroundArrow: Drawable?
		private val tooltipMessage: String
		private val tooltipPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		private val circleRadius = AndroidUtilities.dpf2(41f)
		private val circleRadiusAmplitude = AndroidUtilities.dp(30f).toFloat()
		private val virtualViewHelper: VirtualViewHelper
		private val p = Paint(Paint.ANTI_ALIAS_FLAG)
		private var iconScale: Float
		var drawingCx = 0f
		var drawingCy = 0f

		@JvmField
		var drawingCircleRadius = 0f

		var voiceEnterTransitionInProgress = false
		var skipDraw = false
		var tinyWaveDrawable = BlobDrawable(11)
		var bigWaveDrawable = BlobDrawable(12)
		private var lockBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private var lockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private var lockOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
		var rectF = RectF()
		var path = Path()
		private var idleProgress = 0f
		private var incIdle = false
		private var scale = 0f
		private var amplitude = 0f
		private var animateToAmplitude = 0f
		private var animateAmplitudeDiff = 0f
		private var lastUpdateTime: Long = 0
		private var lockAnimatedTranslation = 0f
		private var snapAnimationProgress = 0f
		var startTranslation = 0f
		var isSendButtonVisible = false
		private var innerPressed = false
		private var transformToSeekbar = 0f
		private var exitTransition = 0f

		var transformToSeekbarProgressStep3 = 0f
			private set

		private var progressToSendButton = 0f
		private var tooltipBackground: Drawable
		private var tooltipLayout: StaticLayout? = null
		private var tooltipWidth = 0f
		private var tooltipAlpha = 0f
		private var showTooltip = false
		private var showTooltipStartTime: Long = 0
		private var paintAlpha = 0
		private var touchSlop: Float

		@get:Keep
		@set:Keep
		var slideToCancelProgress = 0f
			set(value) {
				field = value

				var distance = measuredWidth * 0.35f

				if (distance > AndroidUtilities.dp(140f)) {
					distance = AndroidUtilities.dp(140f).toFloat()
				}

				slideDelta = (-distance * (1f - value)).toInt()

				invalidate()
			}

		private var slideToCancelLockProgress = 0f
		private var slideDelta = 0
		private var canceledByGesture = false
		private var lastMovingX = 0f
		private var lastMovingY = 0f
		private var wavesEnterAnimation = 0f
		private var showWaves = true
		private var lastSize = 0

		init {
			micDrawable = ResourcesCompat.getDrawable(resources, R.drawable.input_mic_pressed, null)?.mutate()
			micDrawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_IN)

			cameraDrawable = ResourcesCompat.getDrawable(resources, R.drawable.input_video_pressed, null)?.mutate()
			cameraDrawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_IN)

			sendDrawable = ResourcesCompat.getDrawable(resources, R.drawable.send_message, null)?.mutate()
			// sendDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));

			micOutline = ResourcesCompat.getDrawable(resources, R.drawable.input_mic, null)?.mutate()
			micOutline?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.disabled_text), PorterDuff.Mode.SRC_IN)

			cameraOutline = ResourcesCompat.getDrawable(resources, R.drawable.input_video, null)?.mutate()
			cameraOutline?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.disabled_text), PorterDuff.Mode.SRC_IN)

			virtualViewHelper = VirtualViewHelper(this)

			ViewCompat.setAccessibilityDelegate(this, virtualViewHelper)

			tinyWaveDrawable.minRadius = AndroidUtilities.dp(47f).toFloat()
			tinyWaveDrawable.maxRadius = AndroidUtilities.dp(55f).toFloat()
			tinyWaveDrawable.generateBlob()

			bigWaveDrawable.minRadius = AndroidUtilities.dp(47f).toFloat()
			bigWaveDrawable.maxRadius = AndroidUtilities.dp(55f).toFloat()
			bigWaveDrawable.generateBlob()

			lockOutlinePaint.style = Paint.Style.STROKE
			lockOutlinePaint.strokeCap = Paint.Cap.ROUND
			lockOutlinePaint.strokeWidth = AndroidUtilities.dpf2(1.7f)

			lockShadowDrawable = ResourcesCompat.getDrawable(resources, R.drawable.lock_round_shadow, null)
			lockShadowDrawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.text), PorterDuff.Mode.SRC_IN)

			tooltipBackground = Theme.createRoundRectDrawable(AndroidUtilities.dp(5f), context.getColor(R.color.light_background))
			tooltipPaint.textSize = AndroidUtilities.dp(14f).toFloat()
			tooltipBackgroundArrow = ContextCompat.getDrawable(context, R.drawable.tooltip_arrow)
			tooltipMessage = context.getString(R.string.SlideUpToLock)

			iconScale = 1f

			val vc = ViewConfiguration.get(context)

			touchSlop = vc.scaledTouchSlop.toFloat()
			touchSlop *= touchSlop

			updateColors()
		}

		fun setAmplitude(value: Double) {
			bigWaveDrawable.setValue((min(WaveDrawable.MAX_AMPLITUDE.toDouble(), value) / WaveDrawable.MAX_AMPLITUDE).toFloat(), true)
			tinyWaveDrawable.setValue((min(WaveDrawable.MAX_AMPLITUDE.toDouble(), value) / WaveDrawable.MAX_AMPLITUDE).toFloat(), false)
			animateToAmplitude = (min(WaveDrawable.MAX_AMPLITUDE.toDouble(), value) / WaveDrawable.MAX_AMPLITUDE).toFloat()
			animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100 + 500.0f * WaveDrawable.animationSpeedCircle)
			invalidate()
		}

		fun getScale(): Float {
			return scale
		}

		@Keep
		fun setScale(value: Float) {
			scale = value
			invalidate()
		}

		@Keep
		fun setSnapAnimationProgress(snapAnimationProgress: Float) {
			this.snapAnimationProgress = snapAnimationProgress
			invalidate()
		}

		@Keep
		fun getLockAnimatedTranslation(): Float {
			return lockAnimatedTranslation
		}

		@Keep
		fun setLockAnimatedTranslation(value: Float) {
			lockAnimatedTranslation = value
			invalidate()
		}

		@Keep
		fun setSendButtonInvisible() {
			this.isSendButtonVisible = false
			invalidate()
		}

		fun setLockTranslation(value: Float): Int {
			if (value == 10000f) {
				this.isSendButtonVisible = false
				lockAnimatedTranslation = -1f
				startTranslation = -1f
				invalidate()
				snapAnimationProgress = 0f
				transformToSeekbar = 0f
				exitTransition = 0f
				iconScale = 1f
				scale = 0f
				tooltipAlpha = 0f
				showTooltip = false
				progressToSendButton = 0f
				slideToCancelProgress = 1f
				slideToCancelLockProgress = 1f
				canceledByGesture = false
				return 0
			}
			else {
				if (this.isSendButtonVisible) {
					return 2
				}

				if (lockAnimatedTranslation == -1f) {
					startTranslation = value
				}

				lockAnimatedTranslation = value

				invalidate()

				if (canceledByGesture || slideToCancelProgress < 0.7f) {
					return 1
				}

				if (startTranslation - lockAnimatedTranslation >= AndroidUtilities.dp(57f)) {
					this.isSendButtonVisible = true
					return 2
				}
			}

			return 1
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (this.isSendButtonVisible) {
				val x = event.x.toInt()
				val y = event.y.toInt()

				if (event.action == MotionEvent.ACTION_DOWN) {
					return pauseRect.contains(x.toFloat(), y.toFloat()).also { innerPressed = it }
				}
				else if (innerPressed) {
					if (event.action == MotionEvent.ACTION_UP) {
						if (pauseRect.contains(x.toFloat(), y.toFloat())) {
							if (isInVideoMode) {
								delegate?.needStartRecordVideo(3, true, 0)
							}
							else {
								MediaController.getInstance().stopRecording(2, true, 0)
								delegate?.needStartRecordAudio(0)
							}

							slideText.isEnabled = false
						}
					}

					return true
				}
			}

			return false
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val currentSize = MeasureSpec.getSize(widthMeasureSpec)
			var h = AndroidUtilities.dp(194f)

			if (lastSize != currentSize) {
				lastSize = currentSize
				tooltipLayout = StaticLayout(tooltipMessage, tooltipPaint, AndroidUtilities.dp(220f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true)

				val n = tooltipLayout?.lineCount ?: 0

				tooltipWidth = 0f

				for (i in 0 until n) {
					val w = tooltipLayout?.getLineWidth(i) ?: 0f

					if (w > tooltipWidth) {
						tooltipWidth = w
					}
				}
			}

			tooltipLayout?.let {
				if (it.lineCount > 1) {
					h += it.height - it.getLineBottom(0)
				}
			}

			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))

			var distance = measuredWidth * 0.35f

			if (distance > AndroidUtilities.dp(140f)) {
				distance = AndroidUtilities.dp(140f).toFloat()
			}

			slideDelta = (-distance * (1f - slideToCancelProgress)).toInt()
		}

		override fun onDraw(canvas: Canvas) {
			if (skipDraw) {
				return
			}

			var multilineTooltipOffset = 0f

			tooltipLayout?.let {
				if (it.lineCount > 1) {
					multilineTooltipOffset = (it.height - it.getLineBottom(0)).toFloat()
				}
			}

			val cx = measuredWidth - AndroidUtilities.dp2(26f)
			val cy = (AndroidUtilities.dp(170f) + multilineTooltipOffset).toInt()
			var yAdd = 0f

			drawingCx = (cx + slideDelta).toFloat()
			drawingCy = cy.toFloat()

			if (lockAnimatedTranslation != 10000f) {
				yAdd = max(0, (startTranslation - lockAnimatedTranslation).toInt()).toFloat()

				if (yAdd > AndroidUtilities.dp(57f)) {
					yAdd = AndroidUtilities.dp(57f).toFloat()
				}
			}

			var circleAlpha = 1f

			val sc = if (scale <= 0.5f) {
				scale / 0.5f
			}
			else if (scale <= 0.75f) {
				1.0f - (scale - 0.5f) / 0.25f * 0.1f
			}
			else {
				0.9f + (scale - 0.75f) / 0.25f * 0.1f
			}

			val dt = System.currentTimeMillis() - lastUpdateTime

			if (animateToAmplitude != amplitude) {
				amplitude += animateAmplitudeDiff * dt

				if (animateAmplitudeDiff > 0) {
					if (amplitude > animateToAmplitude) {
						amplitude = animateToAmplitude
					}
				}
				else {
					if (amplitude < animateToAmplitude) {
						amplitude = animateToAmplitude
					}
				}

				invalidate()
			}

			val slideToCancelScale = if (canceledByGesture) {
				0.7f * CubicBezierInterpolator.EASE_OUT.getInterpolation(1f - slideToCancelProgress)
			}
			else {
				0.7f + slideToCancelProgress * 0.3f
			}

			var radius = (circleRadius + circleRadiusAmplitude * amplitude) * sc * slideToCancelScale

			transformToSeekbarProgressStep3 = 0f

			var progressToSeekbarStep1 = 0f
			var progressToSeekbarStep2 = 0f
			var exitProgress2 = 0f

			if (transformToSeekbar != 0f) {
				val step1Time = 0.38f
				val step2Time = 0.25f
				val step3Time = 1f - step1Time - step2Time

				progressToSeekbarStep1 = if (transformToSeekbar > step1Time) 1f else transformToSeekbar / step1Time
				progressToSeekbarStep2 = if (transformToSeekbar > step1Time + step2Time) 1f else max(0f, (transformToSeekbar - step1Time) / step2Time)

				transformToSeekbarProgressStep3 = max(0f, (transformToSeekbar - step1Time - step2Time) / step3Time)

				progressToSeekbarStep1 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep1)
				progressToSeekbarStep2 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep2)

				transformToSeekbarProgressStep3 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(transformToSeekbarProgressStep3)

				radius += AndroidUtilities.dp(16f) * progressToSeekbarStep1

				val toRadius = recordedAudioBackground.measuredHeight / 2f

				radius = toRadius + (radius - toRadius) * (1f - progressToSeekbarStep2)
			}
			else if (exitTransition != 0f) {
				val step1Time = 0.6f
				val step2Time = 0.4f

				progressToSeekbarStep1 = if (exitTransition > step1Time) 1f else exitTransition / step1Time
				exitProgress2 = if (messageTransitionIsRunning) exitTransition else max(0f, (exitTransition - step1Time) / step2Time)
				progressToSeekbarStep1 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep1)
				exitProgress2 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(exitProgress2)
				radius += AndroidUtilities.dp(16f) * progressToSeekbarStep1
				radius *= 1f - exitProgress2

				if (configAnimationsEnabled && exitTransition > 0.6f) {
					circleAlpha = max(0f, 1f - (exitTransition - 0.6f) / 0.4f)
				}
			}

			if (canceledByGesture && slideToCancelProgress > 0.7f) {
				circleAlpha *= 1f - (slideToCancelProgress - 0.7f) / 0.3f
			}

			if (transformToSeekbarProgressStep3 > 0) {
				paint.color = ColorUtils.blendARGB(ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.avatar_tint, null), transformToSeekbarProgressStep3)
			}
			else {
				paint.color = ResourcesCompat.getColor(context.resources, R.color.brand, null)
			}

			val drawable: Drawable?
			var replaceDrawable: Drawable? = null

			if (this.isSendButtonVisible) {
				if (progressToSendButton != 1f) {
					progressToSendButton += dt / 150f

					if (progressToSendButton > 1f) {
						progressToSendButton = 1f
					}

					replaceDrawable = if (isInVideoMode) cameraDrawable else micDrawable
				}

				drawable = sendDrawable
			}
			else {
				drawable = if (isInVideoMode) cameraDrawable else micDrawable
			}

			sendRect[cx - drawable!!.intrinsicWidth / 2, cy - drawable.intrinsicHeight / 2, cx + drawable.intrinsicWidth / 2] = cy + drawable.intrinsicHeight / 2

			drawable.bounds = sendRect
			replaceDrawable?.setBounds(cx - replaceDrawable.intrinsicWidth / 2, cy - replaceDrawable.intrinsicHeight / 2, cx + replaceDrawable.intrinsicWidth / 2, cy + replaceDrawable.intrinsicHeight / 2)

			val moveProgress = 1.0f - yAdd / AndroidUtilities.dp(57f)
			val lockSize: Float
			val lockY: Float
			val lockTopY: Float
			val lockMiddleY: Float
			val lockRotation: Float
			var transformToPauseProgress = 0f

			if (incIdle) {
				idleProgress += 0.01f

				if (idleProgress > 1f) {
					incIdle = false
					idleProgress = 1f
				}
			}
			else {
				idleProgress -= 0.01f

				if (idleProgress < 0) {
					incIdle = true
					idleProgress = 0f
				}
			}

			if (configAnimationsEnabled) {
				tinyWaveDrawable.minRadius = AndroidUtilities.dp(47f).toFloat()
				tinyWaveDrawable.maxRadius = AndroidUtilities.dp(47f) + AndroidUtilities.dp(15f) * BlobDrawable.FORM_SMALL_MAX

				bigWaveDrawable.minRadius = AndroidUtilities.dp(50f).toFloat()
				bigWaveDrawable.maxRadius = AndroidUtilities.dp(50f) + AndroidUtilities.dp(12f) * BlobDrawable.FORM_BIG_MAX
				bigWaveDrawable.updateAmplitude(dt)
				bigWaveDrawable.update(bigWaveDrawable.amplitude, 1.01f)

				tinyWaveDrawable.updateAmplitude(dt)
				tinyWaveDrawable.update(tinyWaveDrawable.amplitude, 1.02f)

//                bigWaveDrawable.tick(radius);
//                tinyWaveDrawable.tick(radius);
			}

			lastUpdateTime = System.currentTimeMillis()

			val slideToCancelProgress1 = if (slideToCancelProgress > 0.7f) 1f else slideToCancelProgress / 0.7f

			if (configAnimationsEnabled && progressToSeekbarStep2 != 1f && exitProgress2 < 0.4f && slideToCancelProgress1 > 0 && !canceledByGesture) {
				if (showWaves && wavesEnterAnimation != 1f) {
					wavesEnterAnimation += 0.04f

					if (wavesEnterAnimation > 1f) {
						wavesEnterAnimation = 1f
					}
				}

				if (!voiceEnterTransitionInProgress) {
					val enter = CubicBezierInterpolator.EASE_OUT.getInterpolation(wavesEnterAnimation)
					canvas.save()
					var s = scale * (1f - progressToSeekbarStep1) * slideToCancelProgress1 * enter * (BlobDrawable.SCALE_BIG_MIN + 1.4f * bigWaveDrawable.amplitude)
					canvas.scale(s, s, (cx + slideDelta).toFloat(), cy.toFloat())
					bigWaveDrawable.draw((cx + slideDelta).toFloat(), cy.toFloat(), canvas, bigWaveDrawable.paint)
					canvas.restore()
					s = scale * (1f - progressToSeekbarStep1) * slideToCancelProgress1 * enter * (BlobDrawable.SCALE_SMALL_MIN + 1.4f * tinyWaveDrawable.amplitude)
					canvas.save()
					canvas.scale(s, s, (cx + slideDelta).toFloat(), cy.toFloat())
					tinyWaveDrawable.draw((cx + slideDelta).toFloat(), cy.toFloat(), canvas, tinyWaveDrawable.paint)
					canvas.restore()
				}
			}

			if (!voiceEnterTransitionInProgress) {
				paint.alpha = (paintAlpha * circleAlpha).toInt()

				if (scale == 1f) {
					if (transformToSeekbar != 0f) {
						if (transformToSeekbarProgressStep3 > 0) {
							val circleB = cy + radius
							val circleT = cy - radius
							val circleR = cx + slideDelta + radius
							val circleL = cx + slideDelta - radius
							var topOffset = 0
							var leftOffset = 0
							val transformToView = recordedAudioBackground
							var v = transformToView.parent as View

							while (v !== parent) {
								topOffset += v.top
								leftOffset += v.left
								v = v.parent as View
							}

							val seekbarT = transformToView.top + topOffset - top
							val seekbarB = transformToView.bottom + topOffset - top
							val seekbarR = transformToView.right + leftOffset - left
							val seekbarL = transformToView.left + leftOffset - left
							val toRadius: Float = if (isInVideoMode) 0f else transformToView.measuredHeight / 2f
							val top = seekbarT + (circleT - seekbarT) * (1f - transformToSeekbarProgressStep3)
							val bottom = seekbarB + (circleB - seekbarB) * (1f - transformToSeekbarProgressStep3)
							val left = seekbarL + (circleL - seekbarL) * (1f - transformToSeekbarProgressStep3)
							val right = seekbarR + (circleR - seekbarR) * (1f - transformToSeekbarProgressStep3)
							val transformRadius = toRadius + (radius - toRadius) * (1f - transformToSeekbarProgressStep3)
							rectF.set(left, top, right, bottom)
							canvas.drawRoundRect(rectF, transformRadius, transformRadius, paint)
						}
						else {
							canvas.drawCircle((cx + slideDelta).toFloat(), cy.toFloat(), radius, paint)
						}
					}
					else {
						canvas.drawCircle((cx + slideDelta).toFloat(), cy.toFloat(), radius, paint)
					}

					canvas.save()

					val a = 1f - exitProgress2
					canvas.translate(slideDelta.toFloat(), 0f)
					drawIconInternal(canvas, drawable, replaceDrawable, progressToSendButton, ((1f - progressToSeekbarStep2) * a * 255).toInt())
					canvas.restore()
				}
			}
			if (this.isSendButtonVisible) {
				lockSize = AndroidUtilities.dp(36f).toFloat()
				lockY = AndroidUtilities.dp(60f) + multilineTooltipOffset + AndroidUtilities.dpf2(30f) * (1.0f - sc) - yAdd + AndroidUtilities.dpf2(14f) * moveProgress
				lockMiddleY = lockY + lockSize / 2f - AndroidUtilities.dpf2(8f) + AndroidUtilities.dpf2(2f)
				lockTopY = lockY + lockSize / 2f - AndroidUtilities.dpf2(16f) + AndroidUtilities.dpf2(2f)
				val snapRotateBackProgress = if (moveProgress > 0.4f) 1f else moveProgress / 0.4f
				lockRotation = 9 * (1f - moveProgress) * (1f - snapAnimationProgress) - 15 * snapAnimationProgress * (1f - snapRotateBackProgress)
				transformToPauseProgress = moveProgress
			}
			else {
				lockSize = (AndroidUtilities.dp(36f) + (AndroidUtilities.dp(14f) * moveProgress).toInt()).toFloat()
				lockY = AndroidUtilities.dp(60f) + multilineTooltipOffset + (AndroidUtilities.dp(30f) * (1.0f - sc)).toInt() - yAdd.toInt() + moveProgress * idleProgress * -AndroidUtilities.dp(8f)
				lockMiddleY = lockY + lockSize / 2f - AndroidUtilities.dpf2(8f) + AndroidUtilities.dpf2(2f) + AndroidUtilities.dpf2(2f) * moveProgress
				lockTopY = lockY + lockSize / 2f - AndroidUtilities.dpf2(16f) + AndroidUtilities.dpf2(2f) + AndroidUtilities.dpf2(2f) * moveProgress
				lockRotation = 9 * (1f - moveProgress)
				snapAnimationProgress = 0f
			}

			if (showTooltip && System.currentTimeMillis() - showTooltipStartTime > 200 || tooltipAlpha != 0f) {
				if (moveProgress < 0.8f || this.isSendButtonVisible || exitTransition != 0f || transformToSeekbar != 0f) {
					showTooltip = false
				}

				if (showTooltip) {
					if (tooltipAlpha != 1f) {
						tooltipAlpha += dt / 150f

						if (tooltipAlpha >= 1f) {
							tooltipAlpha = 1f
							SharedConfig.increaseLockRecordAudioVideoHintShowed()
						}
					}
				}
				else {
					tooltipAlpha -= dt / 150f

					if (tooltipAlpha < 0) {
						tooltipAlpha = 0f
					}
				}

				val alphaInt = (tooltipAlpha * 255).toInt()
				tooltipBackground.alpha = alphaInt
				tooltipBackgroundArrow?.alpha = alphaInt
				tooltipPaint.alpha = alphaInt

				if (tooltipLayout != null) {
					canvas.save()
					rectF.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
					canvas.translate(measuredWidth - tooltipWidth - AndroidUtilities.dp(44f), AndroidUtilities.dpf2(16f))
					tooltipBackground.setBounds(-AndroidUtilities.dp(8f), -AndroidUtilities.dp(2f), (tooltipWidth + AndroidUtilities.dp(36f)).toInt(), (tooltipLayout!!.height + AndroidUtilities.dpf2(4f)).toInt())
					tooltipBackground.draw(canvas)
					tooltipLayout!!.draw(canvas)
					canvas.restore()
					canvas.save()
					canvas.translate(cx.toFloat(), AndroidUtilities.dpf2(17f) + tooltipLayout!!.height / 2f - idleProgress * AndroidUtilities.dpf2(3f))
					path.reset()
					path.setLastPoint(-AndroidUtilities.dpf2(5f), AndroidUtilities.dpf2(4f))
					path.lineTo(0f, 0f)
					path.lineTo(AndroidUtilities.dpf2(5f), AndroidUtilities.dpf2(4f))
					p.color = Color.WHITE
					p.alpha = alphaInt
					p.style = Paint.Style.STROKE
					p.strokeCap = Paint.Cap.ROUND
					p.strokeJoin = Paint.Join.ROUND
					p.strokeWidth = AndroidUtilities.dpf2(1.5f)
					canvas.drawPath(path, p)
					canvas.restore()
					canvas.save()
					tooltipBackgroundArrow?.setBounds(cx - tooltipBackgroundArrow.intrinsicWidth / 2, (tooltipLayout!!.height + AndroidUtilities.dpf2(20f)).toInt(), cx + tooltipBackgroundArrow.intrinsicWidth / 2, (tooltipLayout!!.height + AndroidUtilities.dpf2(20f)).toInt() + tooltipBackgroundArrow.intrinsicHeight)
					tooltipBackgroundArrow?.draw(canvas)
					canvas.restore()
				}
			}

			canvas.save()
			canvas.clipRect(0, 0, measuredWidth, measuredHeight - textFieldContainer.measuredHeight)

			var translation = 0f

			if (1f - scale != 0f) {
				translation = 1f - scale
			}
			else if (progressToSeekbarStep2 != 0f) {
				translation = progressToSeekbarStep2
			}
			else if (exitProgress2 != 0f) {
				translation = exitProgress2
			}

			if (slideToCancelProgress < 0.7f || canceledByGesture) {
				showTooltip = false

				if (slideToCancelLockProgress != 0f) {
					slideToCancelLockProgress -= 0.12f

					if (slideToCancelLockProgress < 0) {
						slideToCancelLockProgress = 0f
					}
				}
			}
			else {
				if (slideToCancelLockProgress != 1f) {
					slideToCancelLockProgress += 0.12f

					if (slideToCancelLockProgress > 1f) {
						slideToCancelLockProgress = 1f
					}
				}
			}

			val maxTranslationDy = AndroidUtilities.dpf2(72f)
			var dy = maxTranslationDy * translation - AndroidUtilities.dpf2(20f) * progressToSeekbarStep1 * (1f - translation) + maxTranslationDy * (1f - slideToCancelLockProgress)

			if (dy > maxTranslationDy) {
				dy = maxTranslationDy
			}

			canvas.translate(0f, dy)

			val s = scale * (1f - progressToSeekbarStep2) * (1f - exitProgress2) * slideToCancelLockProgress

			canvas.scale(s, s, cx.toFloat(), lockMiddleY)

			rectF.set(cx - AndroidUtilities.dpf2(18f), lockY, cx + AndroidUtilities.dpf2(18f), lockY + lockSize)
			lockShadowDrawable?.setBounds((rectF.left - AndroidUtilities.dpf2(3f)).toInt(), (rectF.top - AndroidUtilities.dpf2(3f)).toInt(), (rectF.right + AndroidUtilities.dpf2(3f)).toInt(), (rectF.bottom + AndroidUtilities.dpf2(3f)).toInt())
			lockShadowDrawable?.draw(canvas)

			canvas.drawRoundRect(rectF, AndroidUtilities.dpf2(18f), AndroidUtilities.dpf2(18f), lockBackgroundPaint)

			pauseRect.set(rectF)
			rectF.set(cx - AndroidUtilities.dpf2(6f) - AndroidUtilities.dpf2(2f) * (1f - transformToPauseProgress), lockMiddleY - AndroidUtilities.dpf2(2f) * (1f - transformToPauseProgress), cx + AndroidUtilities.dp(6f) + AndroidUtilities.dpf2(2f) * (1f - transformToPauseProgress), lockMiddleY + AndroidUtilities.dp(12f) + AndroidUtilities.dpf2(2f) * (1f - transformToPauseProgress))

			val lockBottom = rectF.bottom
			val locCx = rectF.centerX()
			val locCy = rectF.centerY()

			canvas.save()
			canvas.translate(0f, AndroidUtilities.dpf2(2f) * (1f - moveProgress))
			canvas.rotate(lockRotation, locCx, locCy)
			canvas.drawRoundRect(rectF, AndroidUtilities.dpf2(3f), AndroidUtilities.dpf2(3f), lockPaint)

			if (transformToPauseProgress != 1f) {
				canvas.drawCircle(locCx, locCy, AndroidUtilities.dpf2(2f) * (1f - transformToPauseProgress), lockBackgroundPaint)
			}

			if (transformToPauseProgress != 1f) {
				rectF.set(0f, 0f, AndroidUtilities.dpf2(8f), AndroidUtilities.dpf2(8f))

				canvas.save()
				canvas.clipRect(0f, 0f, measuredWidth.toFloat(), dy + lockBottom + AndroidUtilities.dpf2(2f) * (1f - moveProgress))
				canvas.translate(cx - AndroidUtilities.dpf2(4f), lockTopY - AndroidUtilities.dpf2(1.5f) * (1f - idleProgress) * moveProgress - AndroidUtilities.dpf2(2f) * (1f - moveProgress) + AndroidUtilities.dpf2(12f) * transformToPauseProgress + AndroidUtilities.dpf2(2f) * snapAnimationProgress)

				if (lockRotation > 0) {
					canvas.rotate(lockRotation, AndroidUtilities.dp(8f).toFloat(), AndroidUtilities.dp(8f).toFloat())
				}

				canvas.drawLine(AndroidUtilities.dpf2(8f), AndroidUtilities.dpf2(4f), AndroidUtilities.dpf2(8f), AndroidUtilities.dpf2(6f) + AndroidUtilities.dpf2(4f) * (1f - transformToPauseProgress), lockOutlinePaint)
				canvas.drawArc(rectF, 0f, -180f, false, lockOutlinePaint)
				canvas.drawLine(0f, AndroidUtilities.dpf2(4f), 0f, AndroidUtilities.dpf2(4f) + AndroidUtilities.dpf2(4f) * idleProgress * moveProgress * if (this.isSendButtonVisible) 0f else 1f + AndroidUtilities.dpf2(4f) * snapAnimationProgress * (1f - moveProgress), lockOutlinePaint)
				canvas.restore()
			}

			canvas.restore()
			canvas.restore()

			if (scale != 1f) {
				canvas.drawCircle((cx + slideDelta).toFloat(), cy.toFloat(), radius, paint)

				val a = if (canceledByGesture) 1f - slideToCancelProgress else 1f

				canvas.save()
				canvas.translate(slideDelta.toFloat(), 0f)
				drawIconInternal(canvas, drawable, replaceDrawable, progressToSendButton, (255 * a).toInt())
				canvas.restore()
			}

			drawingCircleRadius = radius
		}

		fun drawIcon(canvas: Canvas, cx: Int, cy: Int, alpha: Float) {
			val drawable: Drawable?
			var replaceDrawable: Drawable? = null

			if (this.isSendButtonVisible) {
				if (progressToSendButton != 1f) {
					replaceDrawable = if (isInVideoMode) cameraDrawable else micDrawable
				}

				drawable = sendDrawable
			}
			else {
				drawable = if (isInVideoMode) cameraDrawable else micDrawable
			}

			sendRect.set(cx - drawable!!.intrinsicWidth / 2, cy - drawable.intrinsicHeight / 2, cx + drawable.intrinsicWidth / 2, cy + drawable.intrinsicHeight / 2)

			drawable.bounds = sendRect

			replaceDrawable?.setBounds(cx - replaceDrawable.intrinsicWidth / 2, cy - replaceDrawable.intrinsicHeight / 2, cx + replaceDrawable.intrinsicWidth / 2, cy + replaceDrawable.intrinsicHeight / 2)
			drawIconInternal(canvas, drawable, replaceDrawable, progressToSendButton, (255 * alpha).toInt())
		}

		private fun drawIconInternal(canvas: Canvas, drawable: Drawable?, replaceDrawable: Drawable?, progressToSendButton: Float, alpha: Int) {
			if (progressToSendButton == 0f || progressToSendButton == 1f || replaceDrawable == null) {
				if (canceledByGesture && slideToCancelProgress == 1f) {
					val v = audioVideoSendButton
					v.alpha = 1f
					visibility = GONE
					return
				}

				if (canceledByGesture && slideToCancelProgress < 1f) {
					val outlineDrawable = if (isInVideoMode) cameraOutline else micOutline
					outlineDrawable?.bounds = drawable?.bounds ?: return

					val a = (if (slideToCancelProgress < 0.93f) 0f else (slideToCancelProgress - 0.93f) / 0.07f * 255).toInt()

					outlineDrawable?.alpha = a
					outlineDrawable?.draw(canvas)
					outlineDrawable?.alpha = 255

					drawable.alpha = 255 - a
					drawable.draw(canvas)
				}
				else if (!canceledByGesture) {
					drawable?.alpha = alpha
					drawable?.draw(canvas)
				}
			}
			else {
				if (drawable == null) {
					return
				}

				canvas.save()
				canvas.scale(progressToSendButton, progressToSendButton, drawable.bounds.centerX().toFloat(), drawable.bounds.centerY().toFloat())

				drawable.alpha = (alpha * progressToSendButton).toInt()
				drawable.draw(canvas)

				canvas.restore()
				canvas.save()
				canvas.scale(1f - progressToSendButton, 1f - progressToSendButton, drawable.bounds.centerX().toFloat(), drawable.bounds.centerY().toFloat())

				replaceDrawable.alpha = (alpha * (1f - progressToSendButton)).toInt()
				replaceDrawable.draw(canvas)

				canvas.restore()
			}
		}

		override fun dispatchHoverEvent(event: MotionEvent): Boolean {
			return super.dispatchHoverEvent(event) || virtualViewHelper.dispatchHoverEvent(event)
		}

		fun setTransformToSeekbar(value: Float) {
			transformToSeekbar = value
			invalidate()
		}

		@Keep
		fun getExitTransition(): Float {
			return exitTransition
		}

		@Keep
		fun setExitTransition(exitTransition: Float) {
			this.exitTransition = exitTransition
			invalidate()
		}

		fun updateColors() {
			paint.color = ResourcesCompat.getColor(context.resources, R.color.darker_brand, null)
			tinyWaveDrawable.paint.color = ColorUtils.setAlphaComponent(ResourcesCompat.getColor(context.resources, R.color.avatar_background, null), (255 * WaveDrawable.CIRCLE_ALPHA_2).toInt())
			bigWaveDrawable.paint.color = ColorUtils.setAlphaComponent(ResourcesCompat.getColor(context.resources, R.color.avatar_tint, null), (255 * WaveDrawable.CIRCLE_ALPHA_1).toInt())
			tooltipPaint.color = ResourcesCompat.getColor(context.resources, R.color.text, null)
			tooltipBackground = Theme.createRoundRectDrawable(AndroidUtilities.dp(5f), ResourcesCompat.getColor(context.resources, R.color.bulletin_background, null))
			tooltipBackgroundArrow?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.text, null), PorterDuff.Mode.SRC_IN)
			lockBackgroundPaint.color = ResourcesCompat.getColor(context.resources, R.color.background, null)
			lockPaint.color = ResourcesCompat.getColor(context.resources, R.color.text, null)
			lockOutlinePaint.color = ResourcesCompat.getColor(context.resources, R.color.text, null)
			paintAlpha = paint.alpha
		}

		fun showTooltipIfNeed() {
			if (SharedConfig.lockRecordAudioVideoHint < 3) {
				showTooltip = true
				showTooltipStartTime = System.currentTimeMillis()
			}
		}

		fun canceledByGesture() {
			canceledByGesture = true
		}

		fun setMovingCords(x: Float, y: Float) {
			val delta = (x - lastMovingX) * (x - lastMovingX) + (y - lastMovingY) * (y - lastMovingY)

			lastMovingY = y
			lastMovingX = x

			if (showTooltip && tooltipAlpha == 0f && delta > touchSlop) {
				showTooltipStartTime = System.currentTimeMillis()
			}
		}

		fun showWaves(b: Boolean, animated: Boolean) {
			if (!animated) {
				wavesEnterAnimation = if (b) 1f else 0.5f
			}

			showWaves = b
		}

		fun drawWaves(canvas: Canvas, cx: Float, cy: Float, additionalScale: Float) {
			val enter = CubicBezierInterpolator.EASE_OUT.getInterpolation(wavesEnterAnimation)
			val slideToCancelProgress1 = if (slideToCancelProgress > 0.7f) 1f else slideToCancelProgress / 0.7f

			canvas.save()

			var s = scale * slideToCancelProgress1 * enter * (BlobDrawable.SCALE_BIG_MIN + 1.4f * bigWaveDrawable.amplitude) * additionalScale

			canvas.scale(s, s, cx, cy)

			bigWaveDrawable.draw(cx, cy, canvas, bigWaveDrawable.paint)

			canvas.restore()

			s = scale * slideToCancelProgress1 * enter * (BlobDrawable.SCALE_SMALL_MIN + 1.4f * tinyWaveDrawable.amplitude) * additionalScale

			canvas.save()
			canvas.scale(s, s, cx, cy)

			tinyWaveDrawable.draw(cx, cy, canvas, tinyWaveDrawable.paint)

			canvas.restore()
		}

		private inner class VirtualViewHelper(host: View) : ExploreByTouchHelper(host) {
			private val coordinates = IntArray(2)

			override fun getVirtualViewAt(x: Float, y: Float): Int {
				if (isSendButtonVisible) {
					if (sendRect.contains(x.toInt(), y.toInt())) {
						return 1
					}
					else if (pauseRect.contains(x, y)) {
						return 2
					}
					else if (slideText.cancelRect != null) {
						slideText.cancelRect?.let {
							AndroidUtilities.rectTmp.set(it)
						}

						slideText.getLocationOnScreen(coordinates)

						AndroidUtilities.rectTmp.offset(coordinates[0].toFloat(), coordinates[1].toFloat())

						recordCircle.getLocationOnScreen(coordinates)

						AndroidUtilities.rectTmp.offset(-coordinates[0].toFloat(), -coordinates[1].toFloat())

						if (AndroidUtilities.rectTmp.contains(x, y)) {
							return 3
						}
					}
				}

				return HOST_ID
			}

			override fun getVisibleVirtualViews(list: MutableList<Int>) {
				if (isSendButtonVisible) {
					list.add(1)
					list.add(2)
					list.add(3)
				}
			}

			override fun onPopulateNodeForVirtualView(id: Int, info: AccessibilityNodeInfoCompat) {
				when (id) {
					1 -> {
						info.setBoundsInParent(sendRect)
						info.text = context.getString(R.string.Send)
					}

					2 -> {
						rect.set(pauseRect.left.toInt(), pauseRect.top.toInt(), pauseRect.right.toInt(), pauseRect.bottom.toInt())
						info.setBoundsInParent(rect)
						info.text = context.getString(R.string.Stop)
					}

					3 -> {
						slideText.cancelRect?.let {
							AndroidUtilities.rectTmp2.set(it)
							slideText.getLocationOnScreen(coordinates)
							AndroidUtilities.rectTmp2.offset(coordinates[0], coordinates[1])
							recordCircle.getLocationOnScreen(coordinates)
							AndroidUtilities.rectTmp2.offset(-coordinates[0], -coordinates[1])
							info.setBoundsInParent(AndroidUtilities.rectTmp2)
						}

						info.text = context.getString(R.string.Cancel)
					}
				}
			}

			override fun onPerformActionForVirtualView(id: Int, action: Int, args: Bundle?): Boolean {
				return true
			}
		}
	}

	private inner class ScrimDrawable : Drawable() {
		private val paint: Paint = Paint().apply { color = 0 }

		override fun draw(canvas: Canvas) {
			val emojiView = emojiView ?: return
			paint.alpha = (102 * stickersExpansionProgress).roundToInt()
			canvas.drawRect(0f, 0f, width.toFloat(), emojiView.y - height + Theme.chat_composeShadowDrawable.intrinsicHeight, paint)
		}

		override fun setAlpha(alpha: Int) {
			// stub
		}

		override fun setColorFilter(colorFilter: ColorFilter?) {
			// stub
		}

		@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
		override fun getOpacity(): Int {
			return PixelFormat.TRANSPARENT
		}
	}

	private inner class SlideTextView(context: Context) : View(context) {
		var cancelRect: Rect? = Rect()
		var grayPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		var bluePaint: TextPaint
		var arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		var slideToCancelString: String
		var cancelString: String

		@get:Keep
		@set:Keep
		var slideToCancelWidth = 0f

		var cancelWidth = 0f

		@get:Keep
		@set:Keep
		var cancelToProgress = 0f

		var slideProgress = 0f
		var slideToAlpha = 0f
		var cancelAlpha = 0f
		var xOffset = 0f
		var moveForward = false
		var lastUpdateTime: Long = 0
		var cancelCharOffset: Int
		var arrowPath = Path()
		var slideToLayout: StaticLayout? = null
		var cancelLayout: StaticLayout? = null
		var selectableBackground: Drawable? = null
		var smallSize = AndroidUtilities.displaySize.x <= AndroidUtilities.dp(320f)
		private var innerPressed = false
		private var lastSize = 0

		init {
			grayPaint.textSize = AndroidUtilities.dp(if (smallSize) 13f else 15f).toFloat()

			bluePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
			bluePaint.textSize = AndroidUtilities.dp(15f).toFloat()
			bluePaint.typeface = Theme.TYPEFACE_BOLD

			arrowPaint.color = context.getColor(R.color.dark_gray)
			arrowPaint.style = Paint.Style.STROKE
			arrowPaint.strokeWidth = AndroidUtilities.dpf2(if (smallSize) 1f else 1.6f)
			arrowPaint.strokeCap = Paint.Cap.ROUND
			arrowPaint.strokeJoin = Paint.Join.ROUND

			slideToCancelString = context.getString(R.string.SlideToCancel)
			slideToCancelString = slideToCancelString[0].toString() + slideToCancelString.substring(1).lowercase(Locale.getDefault())

			cancelString = context.getString(R.string.Cancel)

			cancelCharOffset = slideToCancelString.indexOf(cancelString)

			updateColors()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP) {
				isPressed = false
			}

			if (cancelToProgress == 0f || !isEnabled) {
				return false
			}

			val x = event.x.toInt()
			val y = event.y.toInt()

			if (event.action == MotionEvent.ACTION_DOWN) {
				innerPressed = cancelRect?.contains(x, y) ?: false

				if (innerPressed) {
					selectableBackground?.setHotspot(x.toFloat(), y.toFloat())
					isPressed = true
				}

				return innerPressed
			}
			else if (innerPressed) {
				if (event.action == MotionEvent.ACTION_MOVE && cancelRect?.contains(x, y) == false) {
					isPressed = false
					return false
				}

				if (event.action == MotionEvent.ACTION_UP && cancelRect?.contains(x, y) == true) {
					onCancelButtonPressed()
				}

				return true
			}

			return innerPressed
		}

		fun onCancelButtonPressed() {
			if (hasRecordVideo && isInVideoMode) {
				CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable)
				delegate?.needStartRecordVideo(5, true, 0)
			}
			else {
				delegate?.needStartRecordAudio(0)
				MediaController.getInstance().stopRecording(0, false, 0)
			}

			recordingAudioVideo = false

			updateRecordInterface(RECORD_STATE_CANCEL)
		}

		fun updateColors() {
			grayPaint.color = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
			bluePaint.color = ResourcesCompat.getColor(resources, R.color.brand, null)
			slideToAlpha = grayPaint.alpha.toFloat()
			cancelAlpha = bluePaint.alpha.toFloat()

			selectableBackground = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(60f), 0, ColorUtils.setAlphaComponent(ResourcesCompat.getColor(resources, R.color.brand, null), 26))
			selectableBackground?.callback = this
		}

		override fun drawableStateChanged() {
			super.drawableStateChanged()
			selectableBackground!!.state = drawableState
		}

		public override fun verifyDrawable(drawable: Drawable): Boolean {
			return selectableBackground === drawable || super.verifyDrawable(drawable)
		}

		override fun jumpDrawablesToCurrentState() {
			super.jumpDrawablesToCurrentState()
			selectableBackground?.jumpToCurrentState()
		}

		@SuppressLint("DrawAllocation")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)

			val currentSize = measuredHeight + (measuredWidth shl 16)

			if (lastSize != currentSize) {
				lastSize = currentSize
				slideToCancelWidth = grayPaint.measureText(slideToCancelString)
				cancelWidth = bluePaint.measureText(cancelString)
				lastUpdateTime = System.currentTimeMillis()

				val heightHalf = measuredHeight shr 1

				arrowPath.reset()

				if (smallSize) {
					arrowPath.setLastPoint(AndroidUtilities.dpf2(2.5f), heightHalf - AndroidUtilities.dpf2(3.12f))
					arrowPath.lineTo(0f, heightHalf.toFloat())
					arrowPath.lineTo(AndroidUtilities.dpf2(2.5f), heightHalf + AndroidUtilities.dpf2(3.12f))
				}
				else {
					arrowPath.setLastPoint(AndroidUtilities.dpf2(4f), heightHalf - AndroidUtilities.dpf2(5f))
					arrowPath.lineTo(0f, heightHalf.toFloat())
					arrowPath.lineTo(AndroidUtilities.dpf2(4f), heightHalf + AndroidUtilities.dpf2(5f))
				}

				slideToLayout = StaticLayout(slideToCancelString, grayPaint, slideToCancelWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				cancelLayout = StaticLayout(cancelString, bluePaint, cancelWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}
		}

		override fun onDraw(canvas: Canvas) {
			val slideToLayout = slideToLayout ?: return
			val cancelLayout = cancelLayout ?: return
			val w = cancelLayout.width + AndroidUtilities.dp(16f)

			grayPaint.color = context.getColor(R.color.dark_gray)
			grayPaint.alpha = (slideToAlpha * (1f - cancelToProgress) * slideProgress).toInt()
			bluePaint.alpha = (cancelAlpha * cancelToProgress).toInt()
			arrowPaint.color = grayPaint.color

			if (smallSize) {
				xOffset = AndroidUtilities.dp(16f).toFloat()
			}
			else {
				val dt = System.currentTimeMillis() - lastUpdateTime
				lastUpdateTime = System.currentTimeMillis()

				if (cancelToProgress == 0f && slideProgress > 0.8f) {
					if (moveForward) {
						xOffset += AndroidUtilities.dp(3f) / 250f * dt

						if (xOffset > AndroidUtilities.dp(6f)) {
							xOffset = AndroidUtilities.dp(6f).toFloat()
							moveForward = false
						}
					}
					else {
						xOffset -= AndroidUtilities.dp(3f) / 250f * dt

						if (xOffset < -AndroidUtilities.dp(6f)) {
							xOffset = -AndroidUtilities.dp(6f).toFloat()
							moveForward = true
						}
					}
				}
			}

			val enableTransition = cancelCharOffset >= 0
			val slideX = ((measuredWidth - slideToCancelWidth) / 2).toInt() + AndroidUtilities.dp(5f)
			val cancelX = ((measuredWidth - cancelWidth) / 2).toInt()
			val offset = if (enableTransition) slideToLayout.getPrimaryHorizontal(cancelCharOffset) else 0f
			val cancelDiff = if (enableTransition) slideX + offset - cancelX else 0f
			val x = slideX + xOffset * (1f - cancelToProgress) * slideProgress - cancelDiff * cancelToProgress + AndroidUtilities.dp(16f)
			val offsetY = if (enableTransition) 0f else cancelToProgress * AndroidUtilities.dp(12f)

			if (cancelToProgress != 1f) {
				val slideDelta = (-measuredWidth / 4 * (1f - slideProgress)).toInt()

				canvas.save()
				canvas.clipRect(recordTimerView.leftProperty + AndroidUtilities.dp(4f), 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
				canvas.save()
				canvas.translate((x.toInt() - (if (smallSize) AndroidUtilities.dp(7f) else AndroidUtilities.dp(10f)) + slideDelta).toFloat(), offsetY)
				canvas.drawPath(arrowPath, arrowPaint)
				canvas.restore()
				canvas.save()
				canvas.translate((x.toInt() + slideDelta).toFloat(), (measuredHeight - slideToLayout.height) / 2f + offsetY)

				slideToLayout.draw(canvas)

				canvas.restore()
				canvas.restore()
			}
			var yi = (measuredHeight - cancelLayout.height) / 2f

			if (!enableTransition) {
				yi -= AndroidUtilities.dp(12f) - offsetY
			}

			val xi = if (enableTransition) {
				x + offset
			}
			else {
				cancelX.toFloat()
			}

			cancelRect?.set(xi.toInt(), yi.toInt(), (xi + cancelLayout.width).toInt(), (yi + cancelLayout.height).toInt())
			cancelRect?.inset(-AndroidUtilities.dp(16f), -AndroidUtilities.dp(16f))

			if (cancelToProgress > 0) {
				selectableBackground?.setBounds(measuredWidth / 2 - w, measuredHeight / 2 - w, measuredWidth / 2 + w, measuredHeight / 2 + w)
				selectableBackground?.draw(canvas)
				canvas.save()
				canvas.translate(xi, yi)
				cancelLayout.draw(canvas)
				canvas.restore()
			}
			else {
				isPressed = false
			}

			if (cancelToProgress != 1f) {
				invalidate()
			}
		}

		fun setSlideX(v: Float) {
			slideProgress = v
		}
	}

	inner class TimerView(context: Context) : View(context) {
		val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		private val replaceDistance = AndroidUtilities.dp(15f).toFloat()
		var isRunning = false
		private var stoppedInternal = false
		private var oldString: String? = null
		var startTime = 0L
		private var stopTime = 0L
		private var lastSendTypingTime = 0L
		private val replaceIn = SpannableStringBuilder()
		private val replaceOut = SpannableStringBuilder()
		private var replaceStable: SpannableStringBuilder? = SpannableStringBuilder()
		var inLayout: StaticLayout? = null
		private var outLayout: StaticLayout? = null
		private var replaceTransition = 0f
		var leftProperty = 0f

		init {
			textPaint.color = ResourcesCompat.getColor(context.resources, R.color.purple, null)
			textPaint.textSize = AndroidUtilities.dp(15f).toFloat()
			textPaint.typeface = Theme.TYPEFACE_BOLD
		}

		fun start() {
			isRunning = true
			startTime = System.currentTimeMillis()
			lastSendTypingTime = startTime
			invalidate()
		}

		fun stop() {
			if (isRunning) {
				isRunning = false

				if (startTime > 0) {
					stopTime = System.currentTimeMillis()
				}

				invalidate()
			}

			lastSendTypingTime = 0
		}

		@SuppressLint("DrawAllocation")
		override fun onDraw(canvas: Canvas) {
			val currentTimeMillis = System.currentTimeMillis()
			val t = if (isRunning) (currentTimeMillis - startTime) else (stopTime - startTime)
			val time = t / 1000
			// val ms = (t % 1000L).toInt() / 10

			if (isInVideoMode) {
				if (t >= 59500 && !stoppedInternal) {
					startedDraggingX = -1f
					delegate?.needStartRecordVideo(3, true, 0)
					stoppedInternal = true
				}
			}

			if (isRunning && currentTimeMillis > lastSendTypingTime + 5000) {
				lastSendTypingTime = currentTimeMillis
				MessagesController.getInstance(currentAccount).sendTyping(dialog_id, threadMessageId, if (isInVideoMode) 7 else 1, 0)
			}

			FileLog.d("TimerView: onDraw: time = $time")

			val newString = if (time / 60 >= 60) {
				// String.format(Locale.US, "%01d:%02d:%02d,%d", time / 60 / 60, time / 60 % 60, time % 60, ms / 10)
				String.format(Locale.getDefault(), "%01d:%02d:%02d", time / 60 / 60, time / 60 % 60, time % 60)
			}
			else {
				// String.format(Locale.US, "%01d:%02d,%d", time / 60, time % 60, ms / 10)
				String.format(Locale.getDefault(), "%01d:%02d", time / 60, time % 60)
			}

			// if (newString.length >= 3 && oldString != null && oldString!!.length >= 3 && newString.length == oldString!!.length && newString[newString.length - 3] != oldString!![newString.length - 3]) {
			if (oldString != null && newString.length == oldString?.length) {
				val n = newString.length

				replaceIn.clear()
				replaceOut.clear()
				replaceStable?.clear()
				replaceIn.append(newString)
				replaceOut.append(oldString)
				replaceStable?.append(newString)

				var inLast = -1
				var inCount = 0
				var outLast = -1
				var outCount = 0

				for (i in 0 until n - 1) {
					if (oldString!![i] != newString[i]) {
						if (outCount == 0) {
							outLast = i
						}

						outCount++

						if (inCount != 0) {
							val span = EmptyStubSpan()

							if (i == n - 2) {
								inCount++
							}

							replaceIn.setSpan(span, inLast, inLast + inCount, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
							replaceOut.setSpan(span, inLast, inLast + inCount, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

							inCount = 0
						}
					}
					else {
						if (inCount == 0) {
							inLast = i
						}

						inCount++

						if (outCount != 0) {
							replaceStable?.setSpan(EmptyStubSpan(), outLast, outLast + outCount, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
							outCount = 0
						}
					}
				}

				if (inCount != 0) {
					val span = EmptyStubSpan()
					replaceIn.setSpan(span, inLast, inLast + inCount + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					replaceOut.setSpan(span, inLast, inLast + inCount + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
				}

				if (outCount != 0) {
					replaceStable?.setSpan(EmptyStubSpan(), outLast, outLast + outCount, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
				}

				inLayout = StaticLayout(replaceIn, textPaint, measuredWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				outLayout = StaticLayout(replaceOut, textPaint, measuredWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				replaceTransition = 1f
			}
			else {
				val replaceStable = replaceStable ?: SpannableStringBuilder(newString).also { this.replaceStable = it}

				if (replaceStable.isEmpty() || replaceStable.length != newString.length) {
					replaceStable.clear()
					replaceStable.append(newString)
				}
				else {
					replaceStable.replace(replaceStable.length - 1, replaceStable.length, newString, newString.length - 1 - (newString.length - replaceStable.length), newString.length)
				}
			}

			if (replaceTransition != 0f) {
				replaceTransition -= 0.15f

				if (replaceTransition < 0f) {
					replaceTransition = 0f
				}
			}

			val y = (measuredHeight / 2).toFloat()
			val x = 0f

			if (replaceTransition == 0f) {
				replaceStable?.clearSpans()

				val staticLayout = StaticLayout(replaceStable, textPaint, measuredWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				canvas.save()
				canvas.translate(x, y - staticLayout.height / 2f)

				staticLayout.draw(canvas)

				canvas.restore()

				leftProperty = x + staticLayout.getLineWidth(0)
			}
			else {
				if (inLayout != null) {
					canvas.save()

					textPaint.alpha = (255 * (1f - replaceTransition)).toInt()

					canvas.translate(x, y - inLayout!!.height / 2f - replaceDistance * replaceTransition)

					inLayout?.draw(canvas)

					canvas.restore()
				}

				if (outLayout != null) {
					canvas.save()

					textPaint.alpha = (255 * replaceTransition).toInt()

					canvas.translate(x, y - outLayout!!.height / 2f + replaceDistance * (1f - replaceTransition))


					outLayout?.draw(canvas)
					canvas.restore()
				}

				canvas.save()

				textPaint.alpha = 255

				val staticLayout = StaticLayout(replaceStable, textPaint, measuredWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				canvas.translate(x, y - staticLayout.height / 2f)

				staticLayout.draw(canvas)

				canvas.restore()

				leftProperty = x + staticLayout.getLineWidth(0)
			}

			oldString = newString

			if (isRunning || replaceTransition != 0f) {
				invalidate()
			}
		}

		fun reset() {
			isRunning = false
			startTime = 0
			stopTime = 0
			stoppedInternal = false
		}
	}

	companion object {
		private const val messageViewRightMargin = 0
		private const val RECORD_STATE_ENTER = 0
		private const val RECORD_STATE_SENDING = 1
		private const val RECORD_STATE_CANCEL = 2
		private const val RECORD_STATE_PREPARING = 3
		private const val RECORD_STATE_CANCEL_BY_TIME = 4
		private const val RECORD_STATE_CANCEL_BY_GESTURE = 5
		private const val POPUP_CONTENT_BOT_KEYBOARD = 1
		const val height = 46

		@JvmStatic
		fun checkPremiumAnimatedEmoji(currentAccount: Int, dialogId: Long, parentFragment: BaseFragment?, container: FrameLayout?, message: CharSequence?): Boolean {
			@Suppress("NAME_SHADOWING") var container = container

			if (message == null || parentFragment == null) {
				return false
			}

			if (container == null) {
				container = parentFragment.layoutContainer
			}

			val isPremium = getInstance(currentAccount).isPremium

			if (!isPremium && getInstance(currentAccount).getClientUserId() != dialogId && message is Spanned) {
				val animatedEmojis = message.getSpans(0, message.length, AnimatedEmojiSpan::class.java)

				if (animatedEmojis != null) {
					for (animatedEmoji in animatedEmojis) {
						if (animatedEmoji != null) {
							var emoji = animatedEmoji.document

							if (emoji == null) {
								emoji = AnimatedEmojiDrawable.findDocument(currentAccount, animatedEmoji.getDocumentId())
							}

							if (emoji != null && !MessageObject.isFreeEmoji(emoji)) {
								parentFragment.context?.let { context ->
									container?.let {
										BulletinFactory.of(it).createEmojiBulletin(emoji, AndroidUtilities.replaceTags(context.getString(R.string.UnlockPremiumEmojiHint)), context.getString(R.string.PremiumMore)) {
											PremiumFeatureBottomSheet(parentFragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false).show()
										}.show()
									}
								}

								return true
							}
						}
					}
				}
			}

			return false
		}
	}
}
