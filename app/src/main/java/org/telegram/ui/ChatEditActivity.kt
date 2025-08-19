/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.vibrate
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.bannedCount
import org.telegram.tgnet.dcId
import org.telegram.tgnet.participants
import org.telegram.tgnet.photoBig
import org.telegram.tgnet.photoSmall
import org.telegram.tgnet.videoSizes
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.TextDetailCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.EditTextEmoji
import org.telegram.ui.Components.ImageUpdater
import org.telegram.ui.Components.ImageUpdater.ImageUpdaterDelegate
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.UndoView
import java.util.Locale
import java.util.concurrent.CountDownLatch
import kotlin.math.max
import kotlin.math.min

class ChatEditActivity(args: Bundle) : BaseFragment(args), ImageUpdaterDelegate, NotificationCenterDelegate {
	var cameraDrawable: RLottieDrawable? = null
	private var doneButton: View? = null
	private var progressDialog: AlertDialog? = null
	private var undoView: UndoView? = null
	private var avatarContainer: LinearLayout? = null
	private var avatarImage: BackupImageView? = null
	private var avatarOverlay: View? = null
	private var avatarAnimation: AnimatorSet? = null
	private var avatarProgressView: RadialProgressView? = null
	private val avatarDrawable = AvatarDrawable()
	private val imageUpdater = ImageUpdater(false)
	private var nameTextView: EditTextEmoji? = null
	private var settingsContainer: LinearLayout? = null
	private var descriptionTextView: EditTextBoldCursor? = null
	private var typeEditContainer: LinearLayout? = null
	private var settingsTopSectionCell: ShadowSectionCell? = null
	private var locationCell: TextDetailCell? = null
	private var typeCell: TextDetailCell? = null
	private var linkedCell: TextDetailCell? = null
	private var historyCell: TextDetailCell? = null
	private var reactionsCell: TextCell? = null
	private var settingsSectionCell: ShadowSectionCell? = null

	// private var signCell: TextCheckCell? = null
	// private var stickersContainer: FrameLayout? = null
	private var stickersCell: TextCell? = null
	private var stickersInfoCell: TextInfoPrivacyCell? = null
	private var infoContainer: LinearLayout? = null
	private var membersCell: TextCell? = null
	private var memberRequestsCell: TextCell? = null
	private var inviteLinksCell: TextCell? = null
	private var adminCell: TextCell? = null
	private var blockCell: TextCell? = null
	private var removedUsersCell: TextCell? = null
	private var logCell: TextCell? = null
	private var setAvatarCell: TextCell? = null
	private var infoSectionCell: ShadowSectionCell? = null
	private var deleteContainer: FrameLayout? = null
	private var deleteCell: TextSettingsCell? = null
	private var deleteInfoCell: ShadowSectionCell? = null
	private var avatar: TLRPC.FileLocation? = null
	private var currentChat: TLRPC.Chat? = null
	private var info: TLRPC.ChatFull? = null
	private var chatId = 0L

	// private var signMessages = false
	private var isChannel = false
	private var showHistory = false
	private var availableReactions: TLRPC.ChatReactions? = null
	private var createAfterUpload = false
	private var donePressed = false
	private var changeBigAvatarCallback: EditProfileFragment.ChangeBigAvatarCallback? = null

	init {
		chatId = args.getLong("chat_id", 0L)
	}

	private val provider = object : PhotoViewer.EmptyPhotoViewerProvider() {
		override fun getPlaceForPhoto(messageObject: MessageObject?, fileLocation: TLRPC.FileLocation?, index: Int, needPreview: Boolean): PhotoViewer.PlaceProviderObject? {
			if (fileLocation == null) {
				return null
			}

			val chat = messagesController.getChat(chatId)
			val photoBig = chat?.photo?.photoBig

			if (photoBig != null && photoBig.localId == fileLocation.localId && photoBig.volumeId == fileLocation.volumeId && photoBig.dcId == fileLocation.dcId) {
				val coordinates = IntArray(2)

				avatarImage?.getLocationInWindow(coordinates)

				val `object` = PhotoViewer.PlaceProviderObject()
				`object`.viewX = coordinates[0]
				`object`.viewY = coordinates[1]
				`object`.parentView = avatarImage
				`object`.imageReceiver = avatarImage!!.imageReceiver
				`object`.dialogId = -chatId
				`object`.thumb = `object`.imageReceiver.bitmapSafe
				`object`.size = -1
				`object`.radius = avatarImage!!.imageReceiver.getRoundRadius()
				`object`.scale = avatarContainer!!.scaleX
				`object`.canEdit = true

				return `object`
			}

			return null
		}

		override fun willHidePhotoViewer() {
			avatarImage?.imageReceiver?.setVisible(value = true, invalidate = true)
		}

		override fun openPhotoForEdit(file: String, thumb: String, isVideo: Boolean) {
			imageUpdater.openPhotoForEdit(file, thumb, 0, isVideo)
		}
	}

	override fun onFragmentCreate(): Boolean {
		currentChat = messagesController.getChat(chatId)

		if (currentChat == null) {
			currentChat = MessagesStorage.getInstance(currentAccount).getChatSync(chatId)

			if (currentChat != null) {
				messagesController.putChat(currentChat, true)
			}
			else {
				return false
			}

			if (info == null) {
				info = MessagesStorage.getInstance(currentAccount).loadChatInfo(chatId, ChatObject.isChannel(currentChat), CountDownLatch(1), false, false)

				if (info == null) {
					return false
				}
			}
		}

		avatarDrawable.setInfo(currentChat!!.title, null)
		isChannel = ChatObject.isChannel(currentChat) && !currentChat!!.megagroup

		imageUpdater.parentFragment = this
		imageUpdater.setDelegate(this)

		// signMessages = currentChat!!.signatures

		NotificationCenter.getInstance(currentAccount).let {
			it.addObserver(this, NotificationCenter.chatInfoDidLoad)
			it.addObserver(this, NotificationCenter.updateInterfaces)
			it.addObserver(this, NotificationCenter.chatAvailableReactionsUpdated)
		}

		if (info != null) {
			loadLinksCount()
		}

		showHistory = currentChat?.showHistory == true

		return super.onFragmentCreate()
	}

	private fun loadLinksCount() {
		val req = TLRPC.TLMessagesGetExportedChatInvites()
		req.peer = messagesController.getInputPeer(-chatId)
		req.adminId = messagesController.getInputUser(userConfig.getCurrentUser())
		req.limit = 0

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is TLRPC.TLMessagesExportedChatInvites) {
					info?.invitesCount = response.count
					messagesStorage.saveChatLinksCount(chatId, response.count)
					updateFields(false)
				}
			}
		}
	}

	fun setChangeBigAvatarCallback(callback: EditProfileFragment.ChangeBigAvatarCallback) {
		this.changeBigAvatarCallback = callback
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		imageUpdater.clear()

		NotificationCenter.getInstance(currentAccount).let {
			it.removeObserver(this, NotificationCenter.chatInfoDidLoad)
			it.removeObserver(this, NotificationCenter.updateInterfaces)
			it.removeObserver(this, NotificationCenter.chatAvailableReactionsUpdated)
		}

		nameTextView?.onDestroy()
		changeBigAvatarCallback = null
	}

	override fun onResume() {
		super.onResume()

		nameTextView?.onResume()
		nameTextView?.editText?.requestFocus()

		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)

		updateFields(true)

		imageUpdater.onResume()
	}

	override fun onPause() {
		super.onPause()
		nameTextView?.onPause()
		undoView?.hide(true, 0)
		imageUpdater.onPause()
	}

	override fun onBecomeFullyHidden() {
		undoView?.hide(true, 0)
	}

	override fun dismissCurrentDialog() {
		if (imageUpdater.dismissCurrentDialog(visibleDialog)) {
			return
		}

		super.dismissCurrentDialog()
	}

	override fun dismissDialogOnPause(dialog: Dialog): Boolean {
		return imageUpdater.dismissDialogOnPause(dialog) && super.dismissDialogOnPause(dialog)
	}

	override fun onRequestPermissionsResultFragment(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		imageUpdater.onRequestPermissionsResultFragment(requestCode, permissions, grantResults)
	}

	override fun onBackPressed(): Boolean {
		if (nameTextView?.isPopupShowing == true) {
			nameTextView?.hidePopup(true)
			return false
		}

		return checkDiscard()
	}

	override fun createView(context: Context): View? {
		nameTextView?.onDestroy()

		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						if (checkDiscard()) {
							finishFragment()
						}
					}

					DONE_BUTTON -> {
						processDone()
					}
				}
			}
		})

		val sizeNotifierFrameLayout = object : SizeNotifierFrameLayout(context) {
			private var ignoreLayout = false

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val widthSize = MeasureSpec.getSize(widthMeasureSpec)
				var heightSize = MeasureSpec.getSize(heightMeasureSpec)

				setMeasuredDimension(widthSize, heightSize)
				heightSize -= paddingTop
				measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0)

				val keyboardSize = measureKeyboardHeight()

				if (keyboardSize > AndroidUtilities.dp(20f)) {
					ignoreLayout = true
					nameTextView?.hideEmojiView()
					ignoreLayout = false
				}

				val childCount = childCount

				for (i in 0 until childCount) {
					val child = getChildAt(i)

					if (child == null || child.isGone || child === actionBar) {
						continue
					}

					if (nameTextView != null && nameTextView!!.isPopupView(child)) {
						if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
							if (AndroidUtilities.isTablet()) {
								child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(min(AndroidUtilities.dp(if (AndroidUtilities.isTablet()) 200f else 320f), heightSize - AndroidUtilities.statusBarHeight + paddingTop), MeasureSpec.EXACTLY))
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
				val paddingBottom = if (keyboardSize <= AndroidUtilities.dp(20f) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) nameTextView!!.emojiPadding else 0

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
						Gravity.RIGHT -> r - width - lp.rightMargin
						Gravity.LEFT -> lp.leftMargin
						else -> lp.leftMargin
					}

					childTop = when (verticalGravity) {
						Gravity.TOP -> lp.topMargin + paddingTop
						Gravity.CENTER_VERTICAL -> (b - paddingBottom - t - height) / 2 + lp.topMargin - lp.bottomMargin
						Gravity.BOTTOM -> b - paddingBottom - t - height - lp.bottomMargin
						else -> lp.topMargin
					}

					if (nameTextView != null && nameTextView!!.isPopupView(child)) {
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

		sizeNotifierFrameLayout.setOnTouchListener { _, _ -> true }

		fragmentView = sizeNotifierFrameLayout
		fragmentView?.setBackgroundResource(R.color.light_background)

		val scrollView = ScrollView(context)
		scrollView.isFillViewport = true

		sizeNotifierFrameLayout.addView(scrollView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		val linearLayout1 = LinearLayout(context)

		scrollView.addView(linearLayout1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

		linearLayout1.orientation = LinearLayout.VERTICAL

		actionBar?.setTitle(context.getString(R.string.ChannelEdit))

		avatarContainer = LinearLayout(context)
		avatarContainer?.orientation = LinearLayout.VERTICAL
		avatarContainer?.setBackgroundResource(R.color.background)

		linearLayout1.addView(avatarContainer, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		val frameLayout = FrameLayout(context)

		avatarContainer?.addView(frameLayout, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		avatarImage = object : BackupImageView(context) {
			override fun invalidate() {
				avatarOverlay?.invalidate()
				super.invalidate()
			}

			@Deprecated("Deprecated in Java")
			override fun invalidate(l: Int, t: Int, r: Int, b: Int) {
				avatarOverlay?.invalidate()
				super.invalidate()
			}
		}

		avatarImage?.setRoundRadius(AndroidUtilities.dp(32f))

		if (ChatObject.canChangeChatInfo(currentChat)) {
			frameLayout.addView(avatarImage, createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 12f, if (LocaleController.isRTL) 16f else 0f, 8f))

			val paint = Paint(Paint.ANTI_ALIAS_FLAG)
			paint.color = 0x55000000

			avatarOverlay = object : View(context) {
				override fun onDraw(canvas: Canvas) {
					if (avatarImage?.imageReceiver?.hasNotThumb() == true) {
						paint.alpha = (0x55 * avatarImage!!.imageReceiver.currentAlpha).toInt()
						canvas.drawCircle(measuredWidth / 2.0f, measuredHeight / 2.0f, measuredWidth / 2.0f, paint)
					}
				}
			}

			frameLayout.addView(avatarOverlay, createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 12f, if (LocaleController.isRTL) 16f else 0f, 8f))

			avatarProgressView = RadialProgressView(context)
			avatarProgressView?.setSize(AndroidUtilities.dp(30f))
			avatarProgressView?.setProgressColor(-0x1)
			avatarProgressView?.setNoProgress(false)

			frameLayout.addView(avatarProgressView, createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 12f, if (LocaleController.isRTL) 16f else 0f, 8f))

			showAvatarProgress(show = false, animated = false)

			avatarContainer?.setOnClickListener {
				if (imageUpdater.isUploadingImage) {
					return@setOnClickListener
				}

				val chat = messagesController.getChat(chatId)

				if (chat?.photo?.photoBig != null) {
					PhotoViewer.getInstance().setParentActivity(this@ChatEditActivity)

					if (chat.photo?.dcId != 0) {
						chat.photo?.photoBig?.dcId = chat.photo?.dcId ?: 0
					}

					val videoLocation = ImageLocation.getForPhoto(info?.chatPhoto?.videoSizes?.firstOrNull(), info?.chatPhoto)

					PhotoViewer.getInstance().openPhotoWithVideo(chat.photo.photoBig, videoLocation, provider)
				}
			}
		}
		else {
			frameLayout.addView(avatarImage, createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 12f, if (LocaleController.isRTL) 16f else 0f, 12f))
		}

		nameTextView = EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT, false, emojiButtonIsRight = true)

		if (isChannel) {
			nameTextView?.setHint(context.getString(R.string.EnterChannelName))
		}
		else {
			nameTextView?.setHint(context.getString(R.string.GroupName))
		}

		nameTextView?.isEnabled = ChatObject.canChangeChatInfo(currentChat)
		nameTextView?.isFocusable = nameTextView!!.isEnabled

		nameTextView?.editText?.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
				// unused
			}

			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
				// unused
			}

			override fun afterTextChanged(s: Editable) {
				avatarDrawable.setInfo(nameTextView?.text?.toString(), null)
				avatarImage?.invalidate()
			}
		})

		nameTextView?.setFilters(arrayOf(LengthFilter(128)))

		frameLayout.addView(nameTextView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) 5f else 96f, 0f, if (LocaleController.isRTL) 96f else 5f, 0f))

		settingsContainer = LinearLayout(context)
		settingsContainer?.orientation = LinearLayout.VERTICAL
		settingsContainer?.setBackgroundColor(context.getColor(R.color.background))

		linearLayout1.addView(settingsContainer, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		if (ChatObject.canChangeChatInfo(currentChat)) {
			setAvatarCell = object : TextCell(context) {
				override fun onDraw(canvas: Canvas) {
					canvas.drawLine(if (LocaleController.isRTL) 0f else AndroidUtilities.dp(20f).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(20f) else 0).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
				}
			}

			setAvatarCell?.background = Theme.getSelectorDrawable(false)
			// FIXME: set proper colors
			// setAvatarCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);

			setAvatarCell?.setOnClickListener {
				imageUpdater.openMenu(avatar != null, {
					avatar = null

					MessagesController.getInstance(currentAccount).changeChatAvatar(chatId, null, null, null, 0.0, null, null, null, null)
					showAvatarProgress(show = false, animated = true)

					avatarImage?.setImage(null, null, avatarDrawable, currentChat)

					cameraDrawable?.currentFrame = 0

					setAvatarCell?.imageView?.playAnimation()
				}) {
					if (!imageUpdater.isUploadingImage) {
						cameraDrawable?.customEndFrame = 86
						setAvatarCell?.imageView?.playAnimation()
					}
					else {
						cameraDrawable?.setCurrentFrame(0, false)
					}
				}

				cameraDrawable?.currentFrame = 0
				cameraDrawable?.customEndFrame = 43

				setAvatarCell?.imageView?.playAnimation()
			}

			settingsContainer?.addView(setAvatarCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		descriptionTextView = EditTextBoldCursor(context)
		descriptionTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		descriptionTextView?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
		descriptionTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		descriptionTextView?.setPadding(0, 0, 0, AndroidUtilities.dp(6f))
		descriptionTextView?.background = null
		descriptionTextView?.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		descriptionTextView?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
		descriptionTextView?.imeOptions = EditorInfo.IME_ACTION_DONE
		descriptionTextView?.isEnabled = ChatObject.canChangeChatInfo(currentChat)
		descriptionTextView?.isFocusable = descriptionTextView!!.isEnabled
		descriptionTextView?.filters = arrayOf(LengthFilter(255))
		descriptionTextView?.hint = context.getString(R.string.DescriptionOptionalPlaceholder)
		descriptionTextView?.setCursorColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		descriptionTextView?.setCursorSize(AndroidUtilities.dp(20f))
		descriptionTextView?.setCursorWidth(1.5f)

		if (descriptionTextView?.isEnabled == true) {
			settingsContainer?.addView(descriptionTextView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 23f, 15f, 23f, 9f))
		}
		else {
			settingsContainer?.addView(descriptionTextView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 23f, 12f, 23f, 6f))
		}

		descriptionTextView?.setOnEditorActionListener { _, i, _ ->
			if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
				doneButton?.performClick()
				return@setOnEditorActionListener true
			}

			false
		}

		settingsTopSectionCell = ShadowSectionCell(context)

		linearLayout1.addView(settingsTopSectionCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		typeEditContainer = LinearLayout(context)
		typeEditContainer?.orientation = LinearLayout.VERTICAL
		typeEditContainer?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

		linearLayout1.addView(typeEditContainer, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

//		if (currentChat?.megagroup == true && (info == null || info?.can_set_location == true)) {
//			locationCell = TextDetailCell(context)
//			locationCell?.background = Theme.getSelectorDrawable(false)
//
//			typeEditContainer?.addView(locationCell, createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
//
//			locationCell?.setOnClickListener {
//				if (!AndroidUtilities.isMapsInstalled(this@ChatEditActivity)) {
//					return@setOnClickListener
//				}
//
//				val fragment = LocationActivity(LocationActivity.LOCATION_TYPE_GROUP)
//				fragment.setDialogId(-chatId)
//
//				(info?.location as? TLRPC.TL_channelLocation)?.let {
//					fragment.setInitialLocation(it)
//				}
//
//				fragment.setDelegate { location, _, _, _ ->
//					val channelLocation = TLRPC.TL_channelLocation()
//					channelLocation.address = location.address
//					channelLocation.geo_point = location.geo
//
//					info?.location = channelLocation
//					info?.flags = info!!.flags or 32768
//
//					updateFields(false)
//
//					messagesController.loadFullChat(chatId, 0, true)
//				}
//
//				presentFragment(fragment)
//			}
//		}

		if (currentChat?.creator == true && (info == null || info?.canSetUsername == true)) {
			typeCell = TextDetailCell(context)
			typeCell?.background = Theme.getSelectorDrawable(false)

			typeEditContainer?.addView(typeCell, createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

			typeCell?.setOnClickListener {
				val fragment = ChatEditTypeActivity(chatId, locationCell?.visibility == View.VISIBLE)
				fragment.setInfo(info)
				presentFragment(fragment)
			}
		}

		if (!isChannel && ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_PIN)) {
			linkedCell = TextDetailCell(context)
			linkedCell?.background = Theme.getSelectorDrawable(false)

			typeEditContainer?.addView(linkedCell, createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

			linkedCell?.setOnClickListener {
				val chatId = this.chatId
				val info = this.info

				val fragment = ChatLinkActivity(chatId)
				fragment.setInfo(info)
				presentFragment(fragment)
			}
		}

//	MARK: The Hidden and Visible functionality has been removed as unnecessary, if you need it in the future, just uncomment this part:
//		if (!isChannel && ChatObject.canBlockUsers(currentChat) && (ChatObject.isChannel(currentChat) || currentChat!!.creator)) {
//			historyCell = TextDetailCell(context)
//			historyCell?.background = Theme.getSelectorDrawable(false)
//
//			typeEditContainer?.addView(historyCell, createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
//
//			historyCell?.setOnClickListener {
//				val builder = BottomSheet.Builder(context)
//				builder.setApplyTopPadding(false)
//
//				val linearLayout = LinearLayout(context)
//				linearLayout.orientation = LinearLayout.VERTICAL
//
//				val headerCell = HeaderCell(context, 23, 15, 15, false)
//				headerCell.height = 47
//				headerCell.setText(context.getString(R.string.ChatHistory))
//
//				linearLayout.addView(headerCell)
//
//				val linearLayoutInviteContainer = LinearLayout(context)
//				linearLayoutInviteContainer.orientation = LinearLayout.VERTICAL
//				linearLayout.addView(linearLayoutInviteContainer, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
//
//				var buttons: List<RadioButtonCell>? = null
//
//				buttons = (0..1).map { a ->
//					val cell = RadioButtonCell(context)
//					cell.tag = a
//					cell.background = Theme.getSelectorDrawable(false)
//
//					if (a == 0) {
//						cell.setTextAndValue(context.getString(R.string.ChatHistoryVisibleInfo), context.getString(R.string.ChatHistoryVisible), true, showHistory)
//					}
//					else {
//						if (ChatObject.isChannel(currentChat)) {
//							cell.setTextAndValue(context.getString(R.string.ChatHistoryHiddenInfo), context.getString(R.string.ChatHistoryHidden), false, !showHistory)
//						}
//						else {
//							cell.setTextAndValue(context.getString(R.string.ChatHistoryHiddenInfo2), context.getString(R.string.ChatHistoryHidden), false, !showHistory)
//						}
//					}
//
//					linearLayoutInviteContainer.addView(cell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
//
//					cell.setOnClickListener {
//						val tag = it.tag as Int
//
//						buttons?.getOrNull(0)?.setChecked(tag == 0)
//						buttons?.getOrNull(1)?.setChecked(tag == 1)
//
//						showHistory = (tag == 0)
//
//						builder.dismissRunnable.run()
//
//						updateFields(true)
//					}
//
//					cell
//				}
//
//				builder.customView = linearLayout
//				showDialog(builder.create())
//			}
//		}

//		if (isChannel) {
//			signCell = TextCheckCell(context)
//			signCell?.background = Theme.getSelectorDrawable(false)
//			signCell?.setTextAndValueAndCheck(context.getString(R.string.ChannelSignMessages), context.getString(R.string.ChannelSignMessagesInfo), signMessages, true, false)
//
//			typeEditContainer?.addView(signCell, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
//
//			signCell?.setOnClickListener {
//				signMessages = !signMessages
//				(it as TextCheckCell).isChecked = signMessages
//			}
//		}

		val menu = actionBar?.createMenu()

		if (ChatObject.canChangeChatInfo(currentChat) /*|| signCell != null*/ || historyCell != null) {
			doneButton = menu?.addItemWithWidth(DONE_BUTTON, R.drawable.ic_ab_done, AndroidUtilities.dp(56f))
			doneButton?.contentDescription = context.getString(R.string.Done)
		}

		if (locationCell != null /*|| signCell != null*/ || historyCell != null || typeCell != null || linkedCell != null) {
			settingsSectionCell = ShadowSectionCell(context)
			linearLayout1.addView(settingsSectionCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		infoContainer = LinearLayout(context)
		infoContainer?.orientation = LinearLayout.VERTICAL
		infoContainer?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

		linearLayout1.addView(infoContainer, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		blockCell = TextCell(context)
		blockCell?.background = Theme.getSelectorDrawable(false)
		blockCell?.visibility = if (ChatObject.isChannel(currentChat) || currentChat!!.creator || ChatObject.hasAdminRights(currentChat) && ChatObject.canChangeChatInfo(currentChat)) View.VISIBLE else View.GONE

		blockCell?.setOnClickListener {
			val args = Bundle()
			args.putLong("chat_id", chatId)
			args.putInt("type", if (!isChannel && !currentChat!!.gigagroup) ChatUsersActivity.TYPE_KICKED else ChatUsersActivity.TYPE_BANNED)

			val fragment = ChatUsersActivity(args)
			fragment.setInfo(info)

			presentFragment(fragment)
		}

		inviteLinksCell = TextCell(context)
		inviteLinksCell?.background = Theme.getSelectorDrawable(false)

		inviteLinksCell?.setOnClickListener {
			val fragment = ManageLinksActivity(chatId, 0, 0)
			fragment.setInfo(info, info?.exportedInvite)
			presentFragment(fragment)
		}

		reactionsCell = TextCell(context)
		reactionsCell?.background = Theme.getSelectorDrawable(false)

		reactionsCell?.setOnClickListener {
			val args = Bundle()
			args.putLong(ChatReactionsEditActivity.KEY_CHAT_ID, chatId)

			val reactionsEditActivity = ChatReactionsEditActivity(args)
			reactionsEditActivity.setInfo(info)

			presentFragment(reactionsEditActivity)
		}

		adminCell = TextCell(context)
		adminCell?.background = Theme.getSelectorDrawable(false)

		adminCell?.setOnClickListener {
			val args = Bundle()
			args.putLong("chat_id", chatId)
			args.putInt("type", ChatUsersActivity.TYPE_ADMIN)

			val fragment = ChatUsersActivity(args)
			fragment.setInfo(info)

			presentFragment(fragment)
		}

		membersCell = TextCell(context)
		membersCell?.background = Theme.getSelectorDrawable(false)

		membersCell?.setOnClickListener {
			val args = Bundle()
			args.putLong("chat_id", chatId)
			args.putInt("type", ChatUsersActivity.TYPE_USERS)

			val fragment = ChatUsersActivity(args)
			fragment.setInfo(info)

			presentFragment(fragment)
		}

		removedUsersCell = TextCell(context)
		removedUsersCell?.background = Theme.getSelectorDrawable(false)

		removedUsersCell?.setOnClickListener {
			val args = Bundle()
			args.putLong("chat_id", chatId)
			args.putInt("type", ChatUsersActivity.TYPE_BANNED)

			val fragment = ChatUsersActivity(args)
			fragment.setInfo(info)

			presentFragment(fragment)
		}

		if (!ChatObject.isChannelAndNotMegaGroup(currentChat)) {
			memberRequestsCell = TextCell(context)
			memberRequestsCell?.background = Theme.getSelectorDrawable(false)

			memberRequestsCell?.setOnClickListener {
				val activity = MemberRequestsActivity(chatId)
				presentFragment(activity)
			}
		}

		// MARK: uncomment to enable admin log screen
//		if (ChatObject.isChannel(currentChat) || currentChat!!.gigagroup) {
//			logCell = TextCell(context)
//			logCell?.setTextAndIcon(context.getString(R.string.EventLog), R.drawable.msg_log, false)
//			logCell?.background = Theme.getSelectorDrawable(false)
//
//			logCell?.setOnClickListener {
//				presentFragment(ChannelAdminLogActivity(currentChat))
//			}
//		}

		infoContainer?.addView(reactionsCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		if (!isChannel && currentChat?.gigagroup != true) {
			infoContainer?.addView(blockCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		if (!isChannel) {
			infoContainer?.addView(inviteLinksCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		infoContainer?.addView(adminCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		infoContainer?.addView(membersCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		if (!isChannel && currentChat?.gigagroup != true && currentChat?.megagroup != true) {
			infoContainer?.addView(removedUsersCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		if (memberRequestsCell != null && info != null && info!!.requestsPending > 0) {
			infoContainer?.addView(memberRequestsCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		if (isChannel) {
			infoContainer?.addView(inviteLinksCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		if (isChannel || currentChat!!.gigagroup) {
			infoContainer?.addView(blockCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		// MARK: uncomment if you need to implement stickers in the future
//		if (!isChannel && info != null && info!!.can_set_stickers) {
//			stickersContainer = FrameLayout(context)
//			stickersContainer?.setBackgroundColor(context.getColor(R.color.background))
//
//			linearLayout1.addView(stickersContainer, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

//			stickersCell = TextCell(context)
//			stickersCell?.background = Theme.getSelectorDrawable(false)
//
//			stickersCell?.setOnClickListener {
//				presentFragment(ChannelAdminLogActivity(currentChat))
//			}
//
//			stickersCell?.setPrioritizeTitleOverValue(true)
//
//			stickersContainer?.addView(stickersCell, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

//			stickersCell?.setOnClickListener {
//				val groupStickersActivity = GroupStickersActivity(currentChat!!.id)
//				groupStickersActivity.setInfo(info)
//				presentFragment(groupStickersActivity)
//			}
//		}
		else if (logCell != null) {
			infoContainer?.addView(logCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		if (!ChatObject.hasAdminRights(currentChat)) {
			infoContainer?.visibility = View.GONE
			settingsTopSectionCell?.visibility = View.GONE
		}

		if (stickersCell == null) {
			infoSectionCell = ShadowSectionCell(context)
			linearLayout1.addView(infoSectionCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		// MARK: uncomment if you need to implement stickers in the future
//		if (!isChannel && info != null && info!!.can_set_stickers) {
//			stickersInfoCell = TextInfoPrivacyCell(context)
//			stickersInfoCell?.setText(context.getString(R.string.GroupStickersInfo))
//
//			linearLayout1.addView(stickersInfoCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
//		}

		if (currentChat?.creator == true) {
			deleteContainer = FrameLayout(context)
			deleteContainer?.setBackgroundResource(R.color.background)

			linearLayout1.addView(deleteContainer, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			deleteCell = TextSettingsCell(context, padding = 21)
			deleteCell?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			deleteCell?.setIcon(R.drawable.msg_delete, R.color.purple)

			deleteCell?.background = Theme.getSelectorDrawable(false)

			if (isChannel) {
				deleteCell?.setText(context.getString(R.string.ChannelDelete), false)
			}
			else {
				deleteCell?.setText(context.getString(R.string.delete_group), false)
			}

			deleteContainer?.addView(deleteCell, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

			deleteCell?.setOnClickListener {
				AlertsCreator.createClearOrDeleteDialogAlert(this@ChatEditActivity, clear = false, admin = true, second = false, chat = currentChat, user = null, secret = false, checkDeleteForAll = true, canDeleteHistory = false) { param: Boolean ->
					if (AndroidUtilities.isTablet()) {
						notificationCenter.postNotificationName(NotificationCenter.closeChats, -chatId)
					}
					else {
						notificationCenter.postNotificationName(NotificationCenter.closeChats)
					}

					finishFragment()

					notificationCenter.postNotificationName(NotificationCenter.needDeleteDialog, -currentChat!!.id, null, currentChat, param)
				}
			}

			deleteInfoCell = ShadowSectionCell(context)
			deleteInfoCell?.background = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)

			linearLayout1.addView(deleteInfoCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		if (stickersInfoCell != null) {
			if (deleteInfoCell == null) {
				stickersInfoCell?.background = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)
			}
			else {
				stickersInfoCell?.background = Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow)
			}
		}

		undoView = UndoView(context)

		sizeNotifierFrameLayout.addView(undoView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM or Gravity.LEFT, 8f, 0f, 8f, 8f))

		nameTextView?.text = currentChat?.title
		nameTextView?.setSelection(nameTextView?.length() ?: 0)

		if (info != null) {
			descriptionTextView?.setText(info?.about)
		}

		setAvatar()

		updateFields(true)

		return fragmentView
	}

	private fun setAvatar() {
		if (avatarImage == null) {
			return
		}

		val chat = messagesController.getChat(chatId) ?: return

		currentChat = chat

		val hasPhoto: Boolean

		if (currentChat?.photo != null) {
			avatar = currentChat?.photo?.photoSmall
			val location = ImageLocation.getForUserOrChat(currentChat, ImageLocation.TYPE_SMALL)
			avatarImage?.setForUserOrChat(currentChat, avatarDrawable)
			hasPhoto = location != null
		}
		else {
			avatarImage?.setImageDrawable(avatarDrawable)
			hasPhoto = false
		}

		if (setAvatarCell != null) {
			context?.let { context ->
				if (hasPhoto || imageUpdater.isUploadingImage) {
					setAvatarCell?.setTextAndIcon(context.getString(R.string.ChangePhoto), R.drawable.add_photo, true)
				}
				else {
					setAvatarCell?.setTextAndIcon(context.getString(R.string.ChoosePhoto), R.drawable.add_photo, true)
				}

				if (cameraDrawable == null) {
					cameraDrawable = RLottieDrawable(R.raw.camera_outline, "" + R.raw.camera_outline, AndroidUtilities.dp(50f), AndroidUtilities.dp(50f), false, null)
				}

				setAvatarCell?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))
				setAvatarCell?.setTextSize(15)
				setAvatarCell?.textView?.setTypeface(Theme.TYPEFACE_DEFAULT)

				setAvatarCell?.imageView?.translationY = -AndroidUtilities.dp(9f).toFloat()
				setAvatarCell?.imageView?.translationX = -AndroidUtilities.dp(8f).toFloat()
				setAvatarCell?.imageView?.setAnimation(cameraDrawable!!)
			}
		}

		if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
			PhotoViewer.getInstance().checkCurrentImageVisibility()
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.chatInfoDidLoad -> {
				val chatFull = args[0] as TLRPC.ChatFull

				if (chatFull.id == chatId) {
					if (info == null && descriptionTextView != null) {
						descriptionTextView!!.setText(chatFull.about)
					}

					val infoWasEmpty = info == null

					info = chatFull

					updateFields(false)

					if (infoWasEmpty) {
						loadLinksCount()
					}
				}
			}

			NotificationCenter.updateInterfaces -> {
				val mask = args[0] as Int

				if (mask and MessagesController.UPDATE_MASK_CHAT != 0) {
					val chat = messagesController.getChat(currentChat?.id)
					if (chat != null) {
						currentChat = chat
					}
					updateFields(false)
				}
				if (mask and MessagesController.UPDATE_MASK_AVATAR != 0) {
					setAvatar()
				}
			}

			NotificationCenter.chatAvailableReactionsUpdated -> {
				val chatId = args[0] as Long

				if (chatId == this.chatId) {
					info = messagesController.getChatFull(chatId)

					if (info != null) {
						availableReactions = info?.availableReactions
					}

					updateReactionsCell()
				}
			}
		}
	}

	override fun onUploadProgressChanged(progress: Float) {
		avatarProgressView?.setProgress(progress)
	}

	override fun didStartUpload(isVideo: Boolean) {
		avatarProgressView?.setProgress(0.0f)
	}

	override fun didUploadPhoto(photo: TLRPC.InputFile?, video: TLRPC.InputFile?, videoStartTimestamp: Double, videoPath: String?, bigSize: TLRPC.PhotoSize?, smallSize: TLRPC.PhotoSize?) {
		AndroidUtilities.runOnUIThread {
			avatar = smallSize?.location

			if (photo != null || video != null) {
				messagesController.changeChatAvatar(chatId, null, photo, video, videoStartTimestamp, videoPath, smallSize?.location, bigSize?.location, null)

				if (createAfterUpload) {
					try {
						if (progressDialog?.isShowing == true) {
							progressDialog?.dismiss()
							progressDialog = null
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					donePressed = false

					doneButton?.performClick()
				}

				showAvatarProgress(show = false, animated = true)
			}
			else {
				avatarImage?.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, currentChat)
				changeBigAvatarCallback?.changeBigAvatar()
				setAvatarCell?.setTextAndIcon(context?.getString(R.string.ChangePhoto), R.drawable.msg_addphoto, true)

				if (cameraDrawable == null) {
					cameraDrawable = RLottieDrawable(R.raw.camera_outline, "" + R.raw.camera_outline, AndroidUtilities.dp(50f), AndroidUtilities.dp(50f), false, null)
				}

				setAvatarCell?.imageView?.translationY = -AndroidUtilities.dp(9f).toFloat()
				setAvatarCell?.imageView?.translationX = -AndroidUtilities.dp(8f).toFloat()
				setAvatarCell?.imageView?.setAnimation(cameraDrawable!!)

				showAvatarProgress(show = true, animated = false)
			}
		}
	}

	override fun getInitialSearchString(): String? {
		return nameTextView?.text?.toString()
	}

	fun showConvertTooltip() {
		undoView?.showWithAction(0, UndoView.ACTION_GIGAGROUP_SUCCESS, null)
	}

	private fun checkDiscard(): Boolean {
		val parentActivity = parentActivity ?: return false
		val context = context ?: return false
		val about = info?.about ?: ""

		if (info != null && ChatObject.isChannel(currentChat) && currentChat?.showHistory != showHistory || nameTextView != null && currentChat?.title != nameTextView?.text?.toString() || descriptionTextView != null && about != descriptionTextView?.text?.toString() /*|| signMessages != currentChat?.signatures*/) {
			val builder = AlertDialog.Builder(parentActivity)
			builder.setTitle(context.getString(R.string.UserRestrictionsApplyChanges))

			if (isChannel) {
				builder.setMessage(context.getString(R.string.ChannelSettingsChangedAlert))
			}
			else {
				builder.setMessage(context.getString(R.string.GroupSettingsChangedAlert))
			}

			builder.setPositiveButton(context.getString(R.string.ApplyTheme)) { _, _ ->
				processDone()
			}

			builder.setNegativeButton(context.getString(R.string.PassportDiscard)) { _, _ ->
				finishFragment()
			}

			showDialog(builder.create())

			return false
		}

		return true
	}

	private val adminCount: Int
		get() {
			return info?.participants?.participants?.count {
				it is TLRPC.TLChatParticipantAdmin || it is TLRPC.TLChatParticipantCreator
			} ?: 1
		}

	private fun processDone() {
		val nameTextView = nameTextView

		if (donePressed || nameTextView == null) {
			return
		}

		if (nameTextView.length() == 0) {
			parentActivity?.vibrate()
			AndroidUtilities.shakeView(nameTextView, 2f, 0)
			return
		}

		donePressed = true

		if (currentChat?.showHistory != showHistory) {
			messagesController.toggleGroupInvitesHistory(chatId, showHistory)
		}

		if (imageUpdater.isUploadingImage) {
			val parentActivity = parentActivity ?: return

			createAfterUpload = true

			progressDialog = AlertDialog(parentActivity, 3)

			progressDialog?.setOnCancelListener {
				createAfterUpload = false
				progressDialog = null
				donePressed = false
			}

			progressDialog?.show()

			return
		}

		var needTitleCheck = false
		if (currentChat?.title != nameTextView.text?.toString()) {
			needTitleCheck = true
			messagesController.changeChatTitle(chatId, nameTextView.text?.toString()) { _, error ->
				AndroidUtilities.runOnUIThread {
					if (error == null) {
						finishFragment()
					}
					else {
						if (context != null) {
							AlertDialog.Builder(context!!).setTitle(context?.getString(R.string.cont_desc_error)).setMessage(error.text).setPositiveButton(context?.getString(R.string.OK), null).create().show()
						}

						donePressed = false
					}
				}
			}
		}

		val about = info?.about ?: ""

		if (descriptionTextView != null && about != descriptionTextView?.text?.toString()) {
			messagesController.updateChatAbout(chatId, descriptionTextView?.text?.toString(), info)
		}

//		if (signMessages != currentChat?.signatures) {
//			currentChat?.signatures = true
//			messagesController.toggleChannelSignatures(chatId, signMessages)
//		}

		if (!needTitleCheck) {
			finishFragment()
		}
	}

	private fun showAvatarProgress(show: Boolean, animated: Boolean) {
		if (avatarProgressView == null) {
			return
		}

		avatarAnimation?.cancel()
		avatarAnimation = null

		if (animated) {
			avatarAnimation = AnimatorSet()

			if (show) {
				avatarProgressView?.visibility = View.VISIBLE
				avatarOverlay?.visibility = View.VISIBLE
				avatarAnimation?.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f), ObjectAnimator.ofFloat(avatarOverlay, View.ALPHA, 1.0f))
			}
			else {
				avatarAnimation?.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f), ObjectAnimator.ofFloat(avatarOverlay, View.ALPHA, 0.0f))
			}

			avatarAnimation?.duration = 180

			avatarAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (avatarAnimation == null || avatarProgressView == null) {
						return
					}

					if (!show) {
						avatarProgressView?.visibility = View.INVISIBLE
						avatarOverlay?.visibility = View.INVISIBLE
					}

					avatarAnimation = null
				}

				override fun onAnimationCancel(animation: Animator) {
					avatarAnimation = null
				}
			})

			avatarAnimation?.start()
		}
		else {
			if (show) {
				avatarProgressView?.alpha = 1.0f
				avatarProgressView?.visibility = View.VISIBLE
				avatarOverlay?.alpha = 1.0f
				avatarOverlay?.visibility = View.VISIBLE
			}
			else {
				avatarProgressView?.alpha = 0.0f
				avatarProgressView?.visibility = View.INVISIBLE
				avatarOverlay?.alpha = 0.0f
				avatarOverlay?.visibility = View.INVISIBLE
			}
		}
	}

	override fun onActivityResultFragment(requestCode: Int, resultCode: Int, data: Intent?) {
		imageUpdater.onActivityResult(requestCode, resultCode, data)
	}

	override fun saveSelfArgs(args: Bundle) {
		if (imageUpdater.currentPicturePath != null) {
			args.putString("path", imageUpdater.currentPicturePath)
		}

		val text = nameTextView?.text?.toString()

		if (!text.isNullOrEmpty()) {
			args.putString("nameTextView", text)
		}
	}

	override fun restoreSelfArgs(args: Bundle) {
		imageUpdater.currentPicturePath = args.getString("path")
	}

	fun setInfo(chatFull: TLRPC.ChatFull?) {
		info = chatFull

		if (chatFull != null) {
			if (currentChat == null) {
				currentChat = messagesController.getChat(chatId)
			}

			availableReactions = info?.availableReactions
		}
	}

	private fun updateFields(updateChat: Boolean) {
		if (updateChat) {
			val chat = messagesController.getChat(chatId)

			if (chat != null) {
				currentChat = chat
			}
		}

		val isPrivate = currentChat?.username.isNullOrEmpty()

		if (historyCell != null) {
			if (info?.location is TLRPC.TLChannelLocation) {
				historyCell?.gone()
			}
			else {
				historyCell?.visibility = if (isPrivate && (info == null || info?.linkedChatId == 0L)) View.VISIBLE else View.GONE
			}
		}

		settingsSectionCell?.visibility = if (/*signCell == null &&*/ typeCell == null && (linkedCell == null || linkedCell?.visibility != View.VISIBLE) && (historyCell == null || historyCell?.visibility != View.VISIBLE) && (locationCell == null || locationCell?.visibility != View.VISIBLE)) View.GONE else View.VISIBLE

		logCell?.visibility = if ((currentChat?.megagroup != true || currentChat?.gigagroup == true || info != null) && ((info?.participantsCount ?: 0) > 200)) View.VISIBLE else View.GONE

		if (linkedCell != null) {
			if (info == null || !isChannel && info?.linkedChatId == 0L) {
				linkedCell?.gone()
			}
			else {
				linkedCell?.visible()

				if (info?.linkedChatId == 0L) {
					linkedCell?.setTextAndValue(context?.getString(R.string.Discussion), context?.getString(R.string.DiscussionInfo), true)
				}
				else {
					val chat = messagesController.getChat(info!!.linkedChatId)

					if (chat == null) {
						linkedCell?.gone()
					}
					else {
						if (isChannel) {
							if (chat.username.isNullOrEmpty()) {
								linkedCell?.setTextAndValue(context?.getString(R.string.Discussion), chat.title, true)
							}
							else {
								linkedCell?.setTextAndValue(context?.getString(R.string.Discussion), "@" + chat.username, true)
							}
						}
						else {
							if (chat.username.isNullOrEmpty()) {
								linkedCell?.setTextAndValue(context?.getString(R.string.LinkedChannel), chat.title, false)
							}
							else {
								linkedCell?.setTextAndValue(context?.getString(R.string.LinkedChannel), "@" + chat.username, false)
							}
						}
					}
				}
			}
		}

//		if (locationCell != null) {
//			if (info?.can_set_location == true) {
//				locationCell?.visible()
//
//				val location = info?.location as? TLRPC.TL_channelLocation
//
//				if (location != null) {
//					locationCell?.setTextAndValue(context?.getString(R.string.AttachLocation), location.address, true)
//				}
//				else {
//					locationCell?.setTextAndValue(context?.getString(R.string.AttachLocation), context?.getString(R.string.unknown_address), true)
//				}
//			}
//			else {
//				locationCell?.gone()
//			}
//		}

		if (typeCell != null) {
			if (info?.location is TLRPC.TLChannelLocation) {
				val link = if (isPrivate) {
					context?.getString(R.string.TypeLocationGroupEdit)
				}
				else {
					String.format(Locale.getDefault(), "https://" + messagesController.linkPrefix + "/%s", currentChat!!.username)
				}

				typeCell?.setTextAndValue(context?.getString(R.string.TypeLocationGroup), link, historyCell?.visibility == View.VISIBLE || linkedCell?.visibility == View.VISIBLE)
			}
			else {
				val type: String?
				val isRestricted = currentChat?.noforwards ?: false

				type = if (isChannel) {
					if (isPrivate) {
						if (isRestricted) {
							context?.getString(R.string.TypePrivateRestrictedForwards)
						}
						else {
							context?.getString(R.string.TypePrivate)
						}
					}
					else if (ChatObject.isSubscriptionChannel(currentChat)) {
						context?.getString(R.string.paid)
					}
					else if (ChatObject.isMasterclass(currentChat)) {
						context?.getString(R.string.masterclass)
					}
					else {
						context?.getString(R.string.TypePublic)
					}
				}
				else {
					if (isPrivate) {
						if (isRestricted) {
							context?.getString(R.string.TypePrivateGroupRestrictedForwards)
						}
						else {
							context?.getString(R.string.TypePrivateGroup)
						}
					}
					else {
						context?.getString(R.string.TypePublicGroup)
					}
				}

				if (isChannel) {
					typeCell?.setTextAndValue(context?.getString(R.string.ChannelType), type, historyCell != null && historyCell!!.isVisible || linkedCell != null && linkedCell!!.isVisible)
				}
				else {
					typeCell?.setTextAndValue(context?.getString(R.string.GroupType), type, historyCell != null && historyCell!!.isVisible || linkedCell != null && linkedCell!!.isVisible)
				}
			}
		}

		if (historyCell != null) {
			val type = if (!showHistory) context?.getString(R.string.ChatHistoryHidden) else context?.getString(R.string.ChatHistoryVisible)
			historyCell?.setTextAndValue(type, context?.getString(R.string.ChatHistory), false)
		}

		if (membersCell != null) {
			if (info != null) {
				if (memberRequestsCell != null) {
					if (memberRequestsCell?.parent == null) {
						val position = infoContainer!!.indexOfChild(membersCell) + 1
						infoContainer?.addView(memberRequestsCell, position, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
					}

					memberRequestsCell?.visibility = if (info!!.requestsPending > 0) View.VISIBLE else View.GONE
				}

				if (isChannel) {
					membersCell?.setTextAndValueAndIcon(context?.getString(R.string.ChannelSubscribers), String.format(Locale.getDefault(), "%d", info!!.participantsCount), R.drawable.subscribers, true)
					blockCell?.setTextAndValueAndIcon(context?.getString(R.string.ChannelBlacklist), String.format(Locale.getDefault(), "%d", max(info!!.bannedCount, info!!.kickedCount)), R.drawable.ic_removed_users, logCell != null && logCell!!.isVisible)
				}
				else {
					if (ChatObject.isChannel(currentChat)) {
						membersCell?.setTextAndValueAndIcon(context?.getString(R.string.ChannelMembers), String.format(Locale.getDefault(), "%d", info!!.participantsCount), R.drawable.subscribers, removedUsersCell!!.isVisible)
					}
					else {
						membersCell?.setTextAndValueAndIcon(context?.getString(R.string.ChannelMembers), String.format(Locale.getDefault(), "%d", info!!.participants.participants?.size ?: 0), R.drawable.subscribers, memberRequestsCell!!.isVisible)
					}

					if (currentChat?.gigagroup == true) {
						blockCell?.setTextAndValueAndIcon(context?.getString(R.string.ChannelBlacklist), String.format(Locale.getDefault(), "%d", max(info!!.bannedCount, info!!.kickedCount)), R.drawable.ic_removed_users, logCell != null && logCell!!.isVisible)
					}
					else {
						var count = 0
						val bannedRights = currentChat?.defaultBannedRights

						if (bannedRights != null) {
							if (!bannedRights.sendStickers) {
								count++
							}

							if (!bannedRights.sendMedia) {
								count++
							}

							if (!bannedRights.embedLinks) {
								count++
							}

							if (!bannedRights.sendMessages) {
								count++
							}

							if (!bannedRights.pinMessages) {
								count++
							}

							if (!bannedRights.sendPolls) {
								count++
							}

							if (!bannedRights.inviteUsers) {
								count++
							}

							if (!bannedRights.changeInfo) {
								count++
							}
						}
						else {
							count = 8
						}

						blockCell?.setTextAndValueAndIcon(context?.getString(R.string.ChannelPermissions), String.format(Locale.getDefault(), "%d/%d", count, 8), R.drawable.msg_permissions, true)
						removedUsersCell?.setTextAndValueAndIcon(context?.getString(R.string.ChannelBlacklist), String.format(Locale.getDefault(), "%d", max(info!!.bannedCount, info!!.kickedCount)), R.drawable.ic_removed_users, false)
					}

					memberRequestsCell?.setTextAndValueAndIcon(context?.getString(R.string.MemberRequests), String.format(Locale.getDefault(), "%d", info!!.requestsPending), R.drawable.msg_requests, logCell != null && logCell!!.isVisible)
				}

				adminCell?.setTextAndValueAndIcon(context?.getString(R.string.ChannelAdministrators), String.format(Locale.getDefault(), "%d", if (ChatObject.isChannel(currentChat)) info!!.adminsCount else adminCount), R.drawable.msg_admins, true)
			}
			else {
				if (isChannel) {
					membersCell?.setTextAndIcon(context?.getString(R.string.ChannelSubscribers), R.drawable.subscribers, true)
					blockCell?.setTextAndIcon(context?.getString(R.string.ChannelBlacklist), R.drawable.msg_chats_remove, logCell != null && logCell!!.isVisible)
				}
				else {
					membersCell?.setTextAndIcon(context?.getString(R.string.ChannelMembers), R.drawable.subscribers, logCell != null && logCell!!.isVisible)

					if (currentChat?.gigagroup == true) {
						blockCell?.setTextAndIcon(context?.getString(R.string.ChannelBlacklist), R.drawable.msg_chats_remove, logCell != null && logCell!!.isVisible)
					}
					else {
						blockCell?.setTextAndIcon(context?.getString(R.string.ChannelPermissions), R.drawable.msg_permissions, true)
					}
				}

				adminCell?.setTextAndIcon(context?.getString(R.string.ChannelAdministrators), R.drawable.msg_admins, true)
			}

			reactionsCell?.visibility = if (ChatObject.canChangeChatInfo(currentChat)) View.VISIBLE else View.GONE

			updateReactionsCell()

			if (info == null || !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE) || !isPrivate && currentChat!!.creator) {
				inviteLinksCell?.gone()
			}
			else {
				if (info!!.invitesCount > 0) {
					inviteLinksCell?.setTextAndValueAndIcon(context?.getString(R.string.InviteLinks), info!!.invitesCount.toString(), R.drawable.msg_link2, true)
				}
				else {
					inviteLinksCell?.setTextAndValueAndIcon(context?.getString(R.string.InviteLinks), "1", R.drawable.msg_link2, true)
				}
			}
		}

//		if (stickersCell != null && info != null) {
//			stickersCell?.setTextAndValueAndIcon(context?.getString(R.string.GroupStickers), info?.stickerset?.title ?: context?.getString(R.string.Add), R.drawable.msg_sticker, false)
//		}
	}

	private fun updateReactionsCell() {
		val finalString = if (availableReactions == null || availableReactions is TLRPC.TLChatReactionsNone) {
			context?.getString(R.string.ReactionsOff)
		}
		else if (availableReactions is TLRPC.TLChatReactionsSome) {
			val someReactions = availableReactions as TLRPC.TLChatReactionsSome
			var count = 0

			for (i in someReactions.reactions.indices) {
				val someReaction = someReactions.reactions[i]

				if (someReaction is TLRPC.TLReactionEmoji) {
					val reaction = mediaDataController.reactionsMap[someReaction.emoticon]

					if (reaction != null && !reaction.inactive) {
						count++
					}
				}
			}

			val reacts = min(mediaDataController.enabledReactionsList.size, count)

			if (reacts == 0) context?.getString(R.string.ReactionsOff) else LocaleController.formatString("ReactionsCount", R.string.ReactionsCount, reacts, mediaDataController.enabledReactionsList.size)
		}
		else {
			context?.getString(R.string.ReactionsAll)
		}

		reactionsCell?.setTextAndValueAndIcon(context?.getString(R.string.Reactions), finalString, R.drawable.msg_reactions2, true)
	}

	companion object {
		private const val DONE_BUTTON = 1
	}
}
