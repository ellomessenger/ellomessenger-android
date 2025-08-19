/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.ChatParticipant
import org.telegram.tgnet.isSelf
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AudioPlayerAlert.ClippingTextViewSwitcher
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.sharedmedia.SharedMediaLayout
import org.telegram.ui.Components.sharedmedia.SharedMediaLayout.SharedMediaPreloaderDelegate
import org.telegram.ui.Components.sharedmedia.SharedMediaPreloader

class MediaActivity(args: Bundle?, private var sharedMediaPreloader: SharedMediaPreloader?) : BaseFragment(args), SharedMediaPreloaderDelegate {
	private var currentChatInfo: ChatFull? = null

	var dialogId: Long = 0L
		private set

	private var nameTextView: SimpleTextView? = null
	var avatarImageView: AvatarImageView? = null
	var sharedMediaLayout: SharedMediaLayout? = null
	var mediaCounterTextView: ClippingTextViewSwitcher? = null

	override fun onFragmentCreate(): Boolean {
		dialogId = arguments?.getLong("dialog_id") ?: 0L

		if (sharedMediaPreloader == null) {
			sharedMediaPreloader = SharedMediaPreloader(this)
			sharedMediaPreloader?.addDelegate(this)
		}

		return super.onFragmentCreate()
	}

	override fun createView(context: Context): View {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.castShadows = false
		actionBar?.setAddToContainer(false)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		val avatarContainer = FrameLayout(context)

		val fragmentView = object : SizeNotifierFrameLayout(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				sharedMediaLayout?.updateLayoutParams<LayoutParams> {
					topMargin = ActionBar.getCurrentActionBarHeight() + if (actionBar?.occupyStatusBar == true) AndroidUtilities.statusBarHeight else 0
				}

				avatarContainer.updateLayoutParams<LayoutParams> {
					topMargin = if (actionBar?.occupyStatusBar == true) AndroidUtilities.statusBarHeight else 0
					height = ActionBar.getCurrentActionBarHeight()
				}

				var textTop = (ActionBar.getCurrentActionBarHeight() / 2 - AndroidUtilities.dp(22f)) / 2 + AndroidUtilities.dp(if (!AndroidUtilities.isTablet() && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4f else 5f)

				nameTextView?.updateLayoutParams<LayoutParams> {
					topMargin = textTop
				}

				textTop = ActionBar.getCurrentActionBarHeight() / 2 + (ActionBar.getCurrentActionBarHeight() / 2 - AndroidUtilities.dp(19f)) / 2 - AndroidUtilities.dp(3f)

				mediaCounterTextView?.updateLayoutParams<LayoutParams> {
					topMargin = textTop
				}

				avatarImageView?.updateLayoutParams<LayoutParams> {
					topMargin = (ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(42f)) / 2
				}

				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			}

			override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
				val sharedMediaLayout = sharedMediaLayout ?: return super.dispatchTouchEvent(ev)

				if (sharedMediaLayout.isInFastScroll) {
					return sharedMediaLayout.dispatchFastScrollEvent(ev)
				}

				if (sharedMediaLayout.checkPinchToZoom(ev)) {
					return true
				}

				return super.dispatchTouchEvent(ev)

			}

			override fun drawList(blurCanvas: Canvas, top: Boolean) {
				sharedMediaLayout?.drawListForBlur(blurCanvas)
			}
		}

		fragmentView.needBlur = true

		this.fragmentView = fragmentView

		nameTextView = SimpleTextView(context)
		nameTextView?.setTextSize(18)
		nameTextView?.setGravity(Gravity.LEFT)
		nameTextView?.setTypeface(Theme.TYPEFACE_BOLD)
		nameTextView?.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f))
		nameTextView?.setScrollNonFitText(true)
		nameTextView?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

		avatarContainer.addView(nameTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 118f, 0f, 56f, 0f))

		avatarImageView = object : AvatarImageView(context) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)

				if (imageReceiver.hasNotThumb()) {
					info.text = context.getString(R.string.AccDescrProfilePicture)
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.Open)))
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, context.getString(R.string.AccDescrOpenInPhotoViewer)))
				}
				else {
					info.isVisibleToUser = false
				}
			}
		}

		avatarImageView?.imageReceiver?.setAllowDecodeSingleFrame(true)
		avatarImageView?.setRoundRadius(AndroidUtilities.dp(21f))
		avatarImageView?.pivotX = 0f
		avatarImageView?.pivotY = 0f

		val avatarDrawable = AvatarDrawable()

		avatarImageView?.setImageDrawable(avatarDrawable)

		avatarContainer.addView(avatarImageView, createFrame(42, 42f, Gravity.TOP or Gravity.LEFT, 64f, 0f, 0f, 0f))

		mediaCounterTextView = object : ClippingTextViewSwitcher(context) {
			override fun createTextView(): TextView {
				val textView = TextView(context)
				textView.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle))
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
				textView.isSingleLine = true
				textView.ellipsize = TextUtils.TruncateAt.END
				textView.gravity = Gravity.LEFT
				return textView
			}
		}

		avatarContainer.addView(mediaCounterTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 118f, 0f, 56f, 0f))

		sharedMediaLayout = object : SharedMediaLayout(context, dialogId, sharedMediaPreloader, 0, null, currentChatInfo, false, this@MediaActivity, object : Delegate {
			override fun scrollToSharedMedia() {
				// unused
			}

			override fun onMemberClick(participant: ChatParticipant?, b: Boolean, resultOnly: Boolean): Boolean {
				return false
			}

			override val currentChat: Chat?
				get() = null

			override val isFragmentOpened: Boolean
				get() = true

			override val listView: RecyclerListView?
				get() = null

			override fun canSearchMembers(): Boolean {
				return false
			}

			override fun updateSelectedMediaTabText() {
				updateMediaCount()
			}
		}, VIEW_TYPE_MEDIA_ACTIVITY) {
			override fun onSelectedTabChanged() {
				updateMediaCount()
			}

			override fun onSearchStateChanged(expanded: Boolean) {
				if (SharedConfig.smoothKeyboard) {
					AndroidUtilities.removeAdjustResize(parentActivity, classGuid)
				}
				AndroidUtilities.updateViewVisibilityAnimated(avatarContainer, !expanded, 0.95f, true)
			}

			override fun drawBackgroundWithBlur(canvas: Canvas, y: Float, rectTmp2: Rect, backgroundPaint: Paint) {
				fragmentView.drawBlurRect(canvas, getY() + y, rectTmp2, backgroundPaint, true)
			}

			override fun invalidateBlur() {
				fragmentView.invalidateBlur()
			}
		}

		sharedMediaLayout?.isPinnedToTop = true
		sharedMediaLayout?.searchItem?.translationY = 0f
		sharedMediaLayout?.photoVideoOptionsItem?.translationY = 0f

		fragmentView.addView(sharedMediaLayout)
		fragmentView.addView(actionBar)
		fragmentView.addView(avatarContainer)
		fragmentView.blurBehindViews.add(sharedMediaLayout)

		var avatarObject: TLObject? = null

		if (DialogObject.isEncryptedDialog(dialogId)) {
			val encryptedChat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))

			if (encryptedChat != null) {
				val user = messagesController.getUser(encryptedChat.userId)

				if (user != null) {
					nameTextView?.setText(ContactsController.formatName(user.firstName, user.lastName))
					avatarDrawable.setInfo(user)
					avatarObject = user
				}
			}
		}
		else if (DialogObject.isUserDialog(dialogId)) {
			val user = MessagesController.getInstance(currentAccount).getUser(dialogId)

			if (user != null) {
				if (user.isSelf) {
					nameTextView?.setText(context.getString(R.string.SavedMessages))
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED
					avatarDrawable.setSmallSize(true)
				}
				else {
					nameTextView?.setText(ContactsController.formatName(user.firstName, user.lastName))
					avatarDrawable.setInfo(user)
					avatarObject = user
				}
			}
		}
		else {
			val chat = MessagesController.getInstance(currentAccount).getChat(-dialogId)

			if (chat != null) {
				nameTextView?.setText(chat.title)
				avatarDrawable.setInfo(chat)
				avatarObject = chat
			}
		}

		val thumbLocation = ImageLocation.getForUserOrChat(avatarObject, ImageLocation.TYPE_SMALL)

		avatarImageView?.setImage(thumbLocation, "50_50", avatarDrawable, avatarObject)

		if (nameTextView?.getText().isNullOrEmpty()) {
			nameTextView?.setText(context.getString(R.string.SharedContentTitle))
		}

		if (sharedMediaLayout?.isSearchItemVisible == true) {
			sharedMediaLayout?.searchItem?.visible()
		}

		if (sharedMediaLayout?.isCalendarItemVisible == true) {
			sharedMediaLayout?.photoVideoOptionsItem?.visible()
		}
		else {
			sharedMediaLayout?.photoVideoOptionsItem?.invisible()
		}

		actionBar?.setDrawBlurBackground(fragmentView)

		AndroidUtilities.updateViewVisibilityAnimated(avatarContainer, true, 1f, false)

		updateMediaCount()

		return fragmentView
	}

	private fun updateMediaCount() {
		val id = sharedMediaLayout?.closestTab ?: return
		val mediaCount = sharedMediaPreloader?.lastMediaCount ?: return

		if (id < 0 || mediaCount[id] < 0) {
			return
		}

		if (id == 0) {
			if (sharedMediaLayout?.getPhotosVideosTypeFilter() == SharedMediaLayout.FILTER_PHOTOS_ONLY) {
				mediaCounterTextView?.setText(LocaleController.formatPluralString("Photos", mediaCount[MediaDataController.MEDIA_PHOTOS_ONLY]))
			}
			else if (sharedMediaLayout?.getPhotosVideosTypeFilter() == SharedMediaLayout.FILTER_VIDEOS_ONLY) {
				mediaCounterTextView?.setText(LocaleController.formatPluralString("Videos", mediaCount[MediaDataController.MEDIA_VIDEOS_ONLY]))
			}
			else {
				mediaCounterTextView?.setText(LocaleController.formatPluralString("Media", mediaCount[MediaDataController.MEDIA_PHOTOVIDEO]))
			}
		}
		else if (id == 1) {
			mediaCounterTextView?.setText(LocaleController.formatPluralString("Files", mediaCount[MediaDataController.MEDIA_FILE]))
		}
		else if (id == 2) {
			mediaCounterTextView?.setText(LocaleController.formatPluralString("Voice", mediaCount[MediaDataController.MEDIA_AUDIO]))
		}
		else if (id == 3) {
			mediaCounterTextView?.setText(LocaleController.formatPluralString("Links", mediaCount[MediaDataController.MEDIA_URL]))
		}
		else if (id == 4) {
			mediaCounterTextView?.setText(LocaleController.formatPluralString("MusicFiles", mediaCount[MediaDataController.MEDIA_MUSIC]))
		}
		else if (id == 5) {
			mediaCounterTextView?.setText(LocaleController.formatPluralString("GIFs", mediaCount[MediaDataController.MEDIA_GIF]))
		}
	}

	fun setChatInfo(currentChatInfo: ChatFull?) {
		this.currentChatInfo = currentChatInfo
	}

	override fun mediaCountUpdated() {
		if (sharedMediaLayout != null && sharedMediaPreloader != null) {
			sharedMediaLayout?.setNewMediaCounts(sharedMediaPreloader?.lastMediaCount)
		}

		updateMediaCount()
	}

	override fun isLightStatusBar(): Boolean {
		return !AndroidUtilities.isDarkTheme()
	}
}
