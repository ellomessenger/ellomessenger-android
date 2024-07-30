/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.CheckResult
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DialogObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.Bulletin.ButtonLayout
import org.telegram.ui.Components.Bulletin.EmptyBulletin
import org.telegram.ui.Components.Bulletin.LottieLayout
import org.telegram.ui.Components.Bulletin.TwoLineLottieLayout
import org.telegram.ui.Components.Bulletin.UndoButton
import org.telegram.ui.PremiumPreviewFragment
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class BulletinFactory {
	private val fragment: BaseFragment?
	private val containerLayout: FrameLayout?

	private constructor(fragment: BaseFragment) {
		this.fragment = fragment
		containerLayout = null
	}

	private constructor(containerLayout: FrameLayout) {
		this.containerLayout = containerLayout
		fragment = null
	}

	fun createSimpleBulletin(iconRawId: Int, text: String?): Bulletin {
		val layout = LottieLayout(context)
		layout.setAnimation(iconRawId, 36, 36)
		layout.textView.text = text
		layout.textView.isSingleLine = false
		layout.textView.maxLines = 2
		return create(layout, Bulletin.DURATION_SHORT)
	}

	fun createSimpleBulletin(iconRawId: Int, text: CharSequence?, subtext: CharSequence?): Bulletin {
		val layout = TwoLineLottieLayout(context)
		layout.setAnimation(iconRawId, 36, 36)
		layout.titleTextView.text = text
		layout.subtitleTextView.text = subtext
		return create(layout, Bulletin.DURATION_SHORT)
	}

	fun createSimpleBulletin(iconRawId: Int, text: CharSequence?, button: CharSequence?, onButtonClick: Runnable?): Bulletin {
		val layout = LottieLayout(context)
		layout.setAnimation(iconRawId, 36, 36)
		layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		layout.textView.isSingleLine = false
		layout.textView.maxLines = 3
		layout.textView.text = text
		layout.setButton(UndoButton(context, true).setText(button).setUndoAction(onButtonClick))
		return create(layout, Bulletin.DURATION_SHORT)
	}

	fun createSimpleBulletin(drawable: Drawable?, text: CharSequence?, button: String?, onButtonClick: Runnable?): Bulletin {
		val layout = LottieLayout(context)
		layout.imageView.setImageDrawable(drawable)
		layout.textView.text = text
		layout.textView.isSingleLine = false
		layout.textView.maxLines = 2
		layout.setButton(UndoButton(context, true).setText(button).setUndoAction(onButtonClick))
		return create(layout, Bulletin.DURATION_LONG)
	}

	fun createEmojiBulletin(emoji: String?, text: String?): Bulletin {
		val layout = LottieLayout(context)
		layout.setAnimation(MediaDataController.getInstance(UserConfig.selectedAccount).getEmojiAnimatedSticker(emoji), 36, 36)
		layout.textView.text = text
		layout.textView.isSingleLine = false
		layout.textView.maxLines = 2
		return create(layout, Bulletin.DURATION_LONG)
	}

	fun createEmojiBulletin(emoji: String?, text: String?, button: String?, onButtonClick: Runnable?): Bulletin {
		return createEmojiBulletin(MediaDataController.getInstance(UserConfig.selectedAccount).getEmojiAnimatedSticker(emoji), text, button, onButtonClick)
	}

	fun createEmojiBulletin(document: TLRPC.Document?, text: CharSequence?, button: CharSequence?, onButtonClick: Runnable?): Bulletin {
		val layout = LottieLayout(context)
		layout.setAnimation(document, 36, 36)
		layout.textView.text = text
		layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		layout.textView.isSingleLine = false
		layout.textView.maxLines = 3
		layout.setButton(UndoButton(context, true).setText(button).setUndoAction(onButtonClick))
		return create(layout, Bulletin.DURATION_LONG)
	}

	@CheckResult
	fun createDownloadBulletin(fileType: FileType): Bulletin {
		return createDownloadBulletin(fileType, 1)
	}

	@CheckResult
	fun createDownloadBulletin(fileType: FileType, filesAmount: Int): Bulletin {
		return createDownloadBulletin(fileType, filesAmount, 0, 0)
	}

	fun createReportSent(): Bulletin {
		val layout = LottieLayout(context)
		layout.setAnimation(R.raw.chats_infotip)
		layout.textView.text = context.getString(R.string.ReportChatSent)
		return create(layout, Bulletin.DURATION_SHORT)
	}

	@CheckResult
	fun createDownloadBulletin(fileType: FileType, filesAmount: Int, backgroundColor: Int, textColor: Int): Bulletin {
		val layout = if (backgroundColor != 0 && textColor != 0) {
			LottieLayout(context, backgroundColor, textColor)
		}
		else {
			LottieLayout(context)
		}

		layout.setAnimation(fileType.icon.resId, *fileType.icon.layers)
		layout.textView.text = fileType.getText(filesAmount)

		if (fileType.icon.paddingBottom != 0) {
			layout.setIconPaddingBottom(fileType.icon.paddingBottom)
		}

		return create(layout, Bulletin.DURATION_SHORT)
	}

	@JvmOverloads
	fun createErrorBulletin(errorMessage: CharSequence?, duration: Int = Bulletin.DURATION_SHORT): Bulletin {
		val layout = LottieLayout(context)
		layout.setAnimation(R.raw.chats_infotip)
		layout.textView.text = errorMessage
		layout.textView.isSingleLine = false
		layout.textView.maxLines = 4

		return create(layout, duration)
	}

	fun createRestrictVoiceMessagesPremiumBulletin(): Bulletin {
		val layout = LottieLayout(context)
		layout.setAnimation(R.raw.voip_muted)

		val str = context.getString(R.string.PrivacyVoiceMessagesPremiumOnly)
		val spannable = SpannableStringBuilder(str)
		val indexStart = str.indexOf('*')
		val indexEnd = str.lastIndexOf('*')

		spannable.replace(indexStart, indexEnd + 1, str.substring(indexStart + 1, indexEnd))

		spannable.setSpan(object : ClickableSpan() {
			override fun onClick(widget: View) {
				fragment?.presentFragment(PremiumPreviewFragment("settings"))
			}

			override fun updateDrawState(ds: TextPaint) {
				super.updateDrawState(ds)
				ds.isUnderlineText = false
			}
		}, indexStart, indexEnd - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

		layout.textView.text = spannable
		layout.textView.isSingleLine = false
		layout.textView.maxLines = 2

		return create(layout, Bulletin.DURATION_LONG)
	}

	fun createErrorBulletinSubtitle(errorMessage: CharSequence?, errorDescription: CharSequence?): Bulletin {
		val layout = TwoLineLottieLayout(context)
		layout.setAnimation(R.raw.chats_infotip)
		layout.titleTextView.text = errorMessage
		layout.subtitleTextView.text = errorDescription

		return create(layout, Bulletin.DURATION_SHORT)
	}

	@CheckResult
	fun createCopyLinkBulletin(): Bulletin {
		return createCopyLinkBulletin(false)
	}

	@CheckResult
	fun createCopyBulletin(message: String?): Bulletin {
		if (!AndroidUtilities.shouldShowClipboardToast()) {
			return EmptyBulletin()
		}

		val layout = LottieLayout(context)
		layout.setAnimation(R.raw.copy, 36, 36, "NULL ROTATION", "Back", "Front")
		layout.textView.text = message

		return create(layout, Bulletin.DURATION_SHORT)
	}

	@CheckResult
	fun createCopyLinkBulletin(isPrivate: Boolean): Bulletin {
		if (!AndroidUtilities.shouldShowClipboardToast()) {
			return EmptyBulletin()
		}

		return if (isPrivate) {
			val layout = TwoLineLottieLayout(context)
			layout.setAnimation(R.raw.voip_invite, 36, 36, "Wibe", "Circle")
			layout.titleTextView.text = context.getString(R.string.LinkCopied)
			layout.subtitleTextView.text = context.getString(R.string.LinkCopiedPrivateInfo)
			create(layout, Bulletin.DURATION_LONG)
		}
		else {
			val layout = LottieLayout(context)
			layout.setAnimation(R.raw.voip_invite, 36, 36, "Wibe", "Circle")
			layout.textView.text = context.getString(R.string.LinkCopied)
			create(layout, Bulletin.DURATION_SHORT)
		}
	}

	@CheckResult
	fun createCopyLinkBulletin(text: String?): Bulletin {
		if (!AndroidUtilities.shouldShowClipboardToast()) {
			return EmptyBulletin()
		}

		val layout = LottieLayout(context)
		layout.setAnimation(R.raw.voip_invite, 36, 36, "Wibe", "Circle")
		layout.textView.text = text

		return create(layout, Bulletin.DURATION_SHORT)
	}

	private fun create(layout: Bulletin.Layout, duration: Int): Bulletin {
		return if (fragment != null) {
			Bulletin.make(fragment, layout, duration)
		}
		else if (containerLayout != null) {
			Bulletin.make(containerLayout, layout, duration)
		}
		else {
			throw IllegalStateException("BulletinFactory is not initialized")
		}
	}

	private val context: Context
		get() = fragment?.parentActivity ?: containerLayout?.context ?: ApplicationLoader.applicationContext

	enum class FileType {
		PHOTO("PhotoSavedHint", R.string.PhotoSavedHint, Icon.SAVED_TO_GALLERY), PHOTOS("PhotosSavedHint", Icon.SAVED_TO_GALLERY), VIDEO("VideoSavedHint", R.string.VideoSavedHint, Icon.SAVED_TO_GALLERY), VIDEOS("VideosSavedHint", Icon.SAVED_TO_GALLERY), MEDIA("MediaSavedHint", Icon.SAVED_TO_GALLERY), PHOTO_TO_DOWNLOADS("PhotoSavedToDownloadsHint", R.string.PhotoSavedToDownloadsHint, Icon.SAVED_TO_DOWNLOADS), VIDEO_TO_DOWNLOADS("VideoSavedToDownloadsHint", R.string.VideoSavedToDownloadsHint, Icon.SAVED_TO_DOWNLOADS), GIF("GifSavedHint", R.string.GifSavedHint, Icon.SAVED_TO_GIFS), GIF_TO_DOWNLOADS("GifSavedToDownloadsHint", R.string.GifSavedToDownloadsHint, Icon.SAVED_TO_DOWNLOADS), AUDIO("AudioSavedHint", R.string.AudioSavedHint, Icon.SAVED_TO_MUSIC), AUDIOS("AudiosSavedHint", Icon.SAVED_TO_MUSIC), UNKNOWN("FileSavedHint", R.string.FileSavedHint, Icon.SAVED_TO_DOWNLOADS), UNKNOWNS("FilesSavedHint", Icon.SAVED_TO_DOWNLOADS);

		private val localeKey: String
		private val localeRes: Int
		val plural: Boolean
		val icon: Icon

		constructor(localeKey: String, localeRes: Int, icon: Icon) {
			this.localeKey = localeKey
			this.localeRes = localeRes
			this.icon = icon
			plural = false
		}

		constructor(localeKey: String, icon: Icon) {
			this.localeKey = localeKey
			this.icon = icon
			localeRes = 0
			plural = true
		}

		fun getText(amount: Int): String {
			return if (plural) {
				LocaleController.formatPluralString(localeKey, amount)
			}
			else {
				ApplicationLoader.applicationContext.getString(localeRes)
			}
		}

		enum class Icon(val resId: Int, val paddingBottom: Int, vararg layers: String) {
			SAVED_TO_DOWNLOADS(R.raw.ic_download, 2, "Box", "Arrow"), SAVED_TO_GALLERY(R.raw.ic_save_to_gallery, 0, "Box", "Arrow", "Mask", "Arrow 2", "Splash"), SAVED_TO_MUSIC(R.raw.ic_save_to_music, 2, "Box", "Arrow"), SAVED_TO_GIFS(R.raw.ic_save_to_gifs, 0, "gif");

			val layers: Array<String>

			init {
				this.layers = layers.asList().toTypedArray()
			}
		}
	}

	companion object {
		// const val ICON_TYPE_NOT_FOUND = 0
		// const val ICON_TYPE_WARNING = 1

		@JvmStatic
		fun of(fragment: BaseFragment): BulletinFactory {
			return BulletinFactory(fragment)
		}

		@JvmStatic
		fun of(containerLayout: FrameLayout): BulletinFactory {
			return BulletinFactory(containerLayout)
		}

		@OptIn(ExperimentalContracts::class)
		@JvmStatic
		fun canShowBulletin(fragment: BaseFragment?): Boolean {
			contract {
				returns(true) implies (fragment != null)
			}

			return fragment?.parentActivity != null && fragment.layoutContainer != null
		}

		@JvmStatic
		@CheckResult
		fun createMuteBulletin(fragment: BaseFragment, setting: Int): Bulletin {
			return createMuteBulletin(fragment, setting, 0)
		}

		@JvmStatic
		@CheckResult
		fun createMuteBulletin(fragment: BaseFragment, setting: Int, timeInSeconds: Int): Bulletin {
			val layout = LottieLayout(fragment.parentActivity!!)
			val text: String
			val mute: Boolean
			var muteFor = false

			when (setting) {
				NotificationsController.SETTING_MUTE_CUSTOM -> {
					text = LocaleController.formatString("NotificationsMutedForHint", R.string.NotificationsMutedForHint, LocaleController.formatTTLString(timeInSeconds))
					mute = true
					muteFor = true
				}

				NotificationsController.SETTING_MUTE_HOUR -> {
					text = LocaleController.formatString("NotificationsMutedForHint", R.string.NotificationsMutedForHint, LocaleController.formatPluralString("Hours", 1))
					mute = true
				}

				NotificationsController.SETTING_MUTE_8_HOURS -> {
					text = LocaleController.formatString("NotificationsMutedForHint", R.string.NotificationsMutedForHint, LocaleController.formatPluralString("Hours", 8))
					mute = true
				}

				NotificationsController.SETTING_MUTE_2_DAYS -> {
					text = LocaleController.formatString("NotificationsMutedForHint", R.string.NotificationsMutedForHint, LocaleController.formatPluralString("Days", 2))
					mute = true
				}

				NotificationsController.SETTING_MUTE_FOREVER -> {
					text = fragment.context!!.getString(R.string.NotificationsMutedHint)
					mute = true
				}

				NotificationsController.SETTING_MUTE_UNMUTE -> {
					text = fragment.context!!.getString(R.string.NotificationsUnmutedHint)
					mute = false
				}

				else -> {
					throw IllegalArgumentException()
				}
			}

			if (muteFor) {
				layout.setAnimation(R.raw.mute_for)
			}
			else if (mute) {
				layout.setAnimation(R.raw.ic_mute, "Body Main", "Body Top", "Line", "Curve Big", "Curve Small")
			}
			else {
				layout.setAnimation(R.raw.ic_unmute, "BODY", "Wibe Big", "Wibe Big 3", "Wibe Small")
			}

			layout.textView.text = text

			return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT)
		}

		@JvmStatic
		@CheckResult
		fun createMuteBulletin(fragment: BaseFragment, muted: Boolean): Bulletin {
			return createMuteBulletin(fragment, if (muted) NotificationsController.SETTING_MUTE_FOREVER else NotificationsController.SETTING_MUTE_UNMUTE, 0)
		}

		@CheckResult
		fun createDeleteMessagesBulletin(fragment: BaseFragment, count: Int): Bulletin {
			val layout = LottieLayout(fragment.parentActivity!!)
			layout.setAnimation(R.raw.ic_delete, "Envelope", "Cover", "Bucket")
			layout.textView.text = LocaleController.formatPluralString("MessagesDeletedHint", count)

			return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT)
		}

		@CheckResult
		fun createUnpinAllMessagesBulletin(fragment: BaseFragment, count: Int, hide: Boolean, undoAction: Runnable?, delayedAction: Runnable?): Bulletin? {
			val activity = fragment.parentActivity

			if (activity == null) {
				delayedAction?.run()
				return null
			}

			val buttonLayout: ButtonLayout

			if (hide) {
				val layout = TwoLineLottieLayout(activity)
				layout.setAnimation(R.raw.ic_unpin, 28, 28, "Pin", "Line")
				layout.titleTextView.text = activity.getString(R.string.PinnedMessagesHidden)
				layout.subtitleTextView.text = activity.getString(R.string.PinnedMessagesHiddenInfo)

				buttonLayout = layout
			}
			else {
				val layout = LottieLayout(activity)
				layout.setAnimation(R.raw.ic_unpin, 28, 28, "Pin", "Line")
				layout.textView.text = LocaleController.formatPluralString("MessagesUnpinned", count)

				buttonLayout = layout
			}

			buttonLayout.setButton(UndoButton(activity, true).setUndoAction(undoAction).setDelayedAction(delayedAction))

			return Bulletin.make(fragment, buttonLayout, 5000)
		}

		@CheckResult
		fun createSaveToGalleryBulletin(fragment: BaseFragment, video: Boolean): Bulletin {
			return of(fragment).createDownloadBulletin(if (video) FileType.VIDEO else FileType.PHOTO)
		}

		@JvmStatic
		@CheckResult
		fun createSaveToGalleryBulletin(containerLayout: FrameLayout, video: Boolean, backgroundColor: Int, textColor: Int): Bulletin {
			return of(containerLayout).createDownloadBulletin(if (video) FileType.VIDEO else FileType.PHOTO, 1, backgroundColor, textColor)
		}

		@CheckResult
		fun createPromoteToAdminBulletin(fragment: BaseFragment, userFirstName: String?): Bulletin {
			val layout = LottieLayout(fragment.parentActivity!!)
			layout.setAnimation(R.raw.ic_admin, "Shield")
			layout.textView.text = AndroidUtilities.replaceTags(LocaleController.formatString("UserSetAsAdminHint", R.string.UserSetAsAdminHint, userFirstName))
			return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT)
		}

		@CheckResult
		fun createAddedAsAdminBulletin(fragment: BaseFragment, userFirstName: String?): Bulletin {
			val layout = LottieLayout(fragment.parentActivity!!)
			layout.setAnimation(R.raw.ic_admin, "Shield")
			layout.textView.text = AndroidUtilities.replaceTags(LocaleController.formatString("UserAddedAsAdminHint", R.string.UserAddedAsAdminHint, userFirstName))
			return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT)
		}

		@CheckResult
		fun createInviteSentBulletin(context: Context, containerLayout: FrameLayout, dialogsCount: Int, did: Long, backgroundColor: Int, textColor: Int): Bulletin {
			val layout = LottieLayout(context, backgroundColor, textColor)
			val text: CharSequence
			var hapticDelay = -1

			if (dialogsCount <= 1) {
				if (did == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
					text = AndroidUtilities.replaceTags(context.getString(R.string.InvLinkToSavedMessages))
					layout.setAnimation(R.raw.saved_messages, 30, 30)
				}
				else {
					text = if (DialogObject.isChatDialog(did)) {
						val chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-did)
						AndroidUtilities.replaceTags(LocaleController.formatString("InvLinkToGroup", R.string.InvLinkToGroup, chat?.title ?: ""))
					}
					else {
						val user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(did)
						AndroidUtilities.replaceTags(LocaleController.formatString("InvLinkToUser", R.string.InvLinkToUser, UserObject.getFirstName(user)))
					}

					layout.setAnimation(R.raw.forward, 30, 30)

					hapticDelay = 300
				}
			}
			else {
				text = AndroidUtilities.replaceTags(LocaleController.formatString("InvLinkToChats", R.string.InvLinkToChats, LocaleController.formatPluralString("Chats", dialogsCount)))
				layout.setAnimation(R.raw.forward, 30, 30)
				hapticDelay = 300
			}

			layout.textView.text = text

			if (hapticDelay > 0) {
				layout.postDelayed({
					layout.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
				}, hapticDelay.toLong())
			}

			return Bulletin.make(containerLayout, layout, Bulletin.DURATION_SHORT)
		}

		@JvmStatic
		@CheckResult
		fun createForwardedBulletin(context: Context, containerLayout: FrameLayout, dialogsCount: Int, did: Long, messagesCount: Int, backgroundColor: Int, textColor: Int): Bulletin {
			val layout = LottieLayout(context, backgroundColor, textColor)
			val text: CharSequence
			var hapticDelay = -1

			if (dialogsCount <= 1) {
				if (did == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
					text = if (messagesCount <= 1) {
						AndroidUtilities.replaceTags(context.getString(R.string.FwdMessageToSavedMessages))
					}
					else {
						AndroidUtilities.replaceTags(context.getString(R.string.FwdMessagesToSavedMessages))
					}

					layout.setAnimation(R.raw.saved_messages, 30, 30)
				}
				else {
					text = if (DialogObject.isChatDialog(did)) {
						val chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-did)

						if (messagesCount <= 1) {
							AndroidUtilities.replaceTags(LocaleController.formatString("FwdMessageToGroup", R.string.FwdMessageToGroup, chat?.title ?: ""))
						}
						else {
							AndroidUtilities.replaceTags(LocaleController.formatString("FwdMessagesToGroup", R.string.FwdMessagesToGroup, chat?.title ?: ""))
						}
					}
					else {
						val user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(did)

						if (messagesCount <= 1) {
							AndroidUtilities.replaceTags(LocaleController.formatString("FwdMessageToUser", R.string.FwdMessageToUser, UserObject.getFirstName(user)))
						}
						else {
							AndroidUtilities.replaceTags(LocaleController.formatString("FwdMessagesToUser", R.string.FwdMessagesToUser, UserObject.getFirstName(user)))
						}
					}

					layout.setAnimation(R.raw.forward, 30, 30)

					hapticDelay = 300
				}
			}
			else {
				text = if (messagesCount <= 1) {
					AndroidUtilities.replaceTags(LocaleController.formatString("FwdMessageToChats", R.string.FwdMessageToChats, LocaleController.formatPluralString("Chats", dialogsCount)))
				}
				else {
					AndroidUtilities.replaceTags(LocaleController.formatString("FwdMessagesToChats", R.string.FwdMessagesToChats, LocaleController.formatPluralString("Chats", dialogsCount)))
				}

				layout.setAnimation(R.raw.forward, 30, 30)

				hapticDelay = 300
			}

			layout.textView.text = text

			if (hapticDelay > 0) {
				layout.postDelayed({
					layout.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
				}, hapticDelay.toLong())
			}

			return Bulletin.make(containerLayout, layout, Bulletin.DURATION_SHORT)
		}

		@CheckResult
		fun createRemoveFromChatBulletin(fragment: BaseFragment, user: User, chatName: String?): Bulletin {
			val layout = LottieLayout(fragment.parentActivity!!)
			layout.setAnimation(R.raw.ic_ban, "Hand")

			val name = if (user.deleted) {
				LocaleController.formatString("HiddenName", R.string.HiddenName)
			}
			else {
				user.first_name
			}

			layout.textView.text = AndroidUtilities.replaceTags(LocaleController.formatString("UserRemovedFromChatHint", R.string.UserRemovedFromChatHint, name, chatName))

			return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT)
		}

		@CheckResult
		fun createBanBulletin(fragment: BaseFragment, banned: Boolean): Bulletin {
			val layout = LottieLayout(fragment.parentActivity!!)

			val text = if (banned) {
				layout.setAnimation(R.raw.ic_ban, "Hand")
				fragment.parentActivity!!.getString(R.string.UserBlocked)
			}
			else {
				layout.setAnimation(R.raw.ic_unban, "Main", "Finger 1", "Finger 2", "Finger 3", "Finger 4")
				fragment.parentActivity!!.getString(R.string.UserUnblocked)
			}

			layout.textView.text = AndroidUtilities.replaceTags(text)

			return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT)
		}

		@JvmStatic
		@CheckResult
		fun createCopyLinkBulletin(fragment: BaseFragment): Bulletin {
			return of(fragment).createCopyLinkBulletin()
		}

		@CheckResult
		fun createCopyLinkBulletin(containerView: FrameLayout): Bulletin {
			return of(containerView).createCopyLinkBulletin()
		}

		@CheckResult
		fun createPinMessageBulletin(fragment: BaseFragment): Bulletin {
			return createPinMessageBulletin(fragment, true, null, null)
		}

		@CheckResult
		fun createUnpinMessageBulletin(fragment: BaseFragment, undoAction: Runnable?, delayedAction: Runnable?): Bulletin {
			return createPinMessageBulletin(fragment, false, undoAction, delayedAction)
		}

		@CheckResult
		private fun createPinMessageBulletin(fragment: BaseFragment, pinned: Boolean, undoAction: Runnable?, delayedAction: Runnable?): Bulletin {
			val layout = LottieLayout(fragment.parentActivity!!)
			layout.setAnimation(if (pinned) R.raw.ic_pin else R.raw.ic_unpin, 28, 28, "Pin", "Line")
			layout.textView.text = fragment.parentActivity!!.getString(if (pinned) R.string.MessagePinnedHint else R.string.MessageUnpinnedHint)

			if (!pinned) {
				layout.setButton(UndoButton(fragment.parentActivity!!, true).setUndoAction(undoAction).setDelayedAction(delayedAction))
			}

			return Bulletin.make(fragment, layout, if (pinned) Bulletin.DURATION_SHORT else 5000)
		}

		@JvmStatic
		@CheckResult
		fun createSoundEnabledBulletin(fragment: BaseFragment, setting: Int): Bulletin {
			val layout = LottieLayout(fragment.parentActivity!!)
			val text: String
			val soundOn: Boolean

			when (setting) {
				NotificationsController.SETTING_SOUND_ON -> {
					text = fragment.parentActivity!!.getString(R.string.SoundOnHint)
					soundOn = true
				}

				NotificationsController.SETTING_SOUND_OFF -> {
					text = fragment.parentActivity!!.getString(R.string.SoundOffHint)
					soundOn = false
				}

				else -> {
					throw IllegalArgumentException()
				}
			}

			if (soundOn) {
				layout.setAnimation(R.raw.sound_on)
			}
			else {
				layout.setAnimation(R.raw.sound_off)
			}

			layout.textView.text = text

			return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT)
		}
	}
}
