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
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.URLSpan
import android.util.Base64
import android.util.SparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.util.Consumer
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.BuildVars
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.LocaleInfo
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.MessagesStorage.BooleanCallback
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.OneUIUtilities
import org.telegram.messenger.R
import org.telegram.messenger.SecretChatHelper
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.SvgHelper
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.messenger.Utilities
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.databinding.FileSizeLimitAlertBinding
import org.telegram.messenger.messageobject.GroupedMessages
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.vibrate
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ConnectionsManager.Companion.generateClassGuid
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.EncryptedChat
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.TLRPC.TL_account_changePhone
import org.telegram.tgnet.TLRPC.TL_account_confirmPhone
import org.telegram.tgnet.TLRPC.TL_account_getAuthorizationForm
import org.telegram.tgnet.TLRPC.TL_account_getPassword
import org.telegram.tgnet.TLRPC.TL_account_getTmpPassword
import org.telegram.tgnet.TLRPC.TL_account_reportPeer
import org.telegram.tgnet.TLRPC.TL_account_saveSecureValue
import org.telegram.tgnet.TLRPC.TL_account_sendChangePhoneCode
import org.telegram.tgnet.TLRPC.TL_account_sendConfirmPhoneCode
import org.telegram.tgnet.TLRPC.TL_account_updateProfile
import org.telegram.tgnet.TLRPC.TL_account_verifyEmail
import org.telegram.tgnet.TLRPC.TL_account_verifyPhone
import org.telegram.tgnet.TLRPC.TL_auth_resendCode
import org.telegram.tgnet.TLRPC.TL_channelLocation
import org.telegram.tgnet.TLRPC.TL_channelParticipantAdmin
import org.telegram.tgnet.TLRPC.TL_channelParticipantCreator
import org.telegram.tgnet.TLRPC.TL_channels_channelParticipant
import org.telegram.tgnet.TLRPC.TL_channels_createChannel
import org.telegram.tgnet.TLRPC.TL_channels_editAdmin
import org.telegram.tgnet.TLRPC.TL_channels_getParticipant
import org.telegram.tgnet.TLRPC.TL_channels_inviteToChannel
import org.telegram.tgnet.TLRPC.TL_channels_joinChannel
import org.telegram.tgnet.TLRPC.TL_channels_reportSpam
import org.telegram.tgnet.TLRPC.TL_contacts_blockFromReplies
import org.telegram.tgnet.TLRPC.TL_contacts_importContacts
import org.telegram.tgnet.TLRPC.TL_error
import org.telegram.tgnet.TLRPC.TL_help_getSupport
import org.telegram.tgnet.TLRPC.TL_help_support
import org.telegram.tgnet.TLRPC.TL_inputPeerUser
import org.telegram.tgnet.TLRPC.TL_inputReportReasonChildAbuse
import org.telegram.tgnet.TLRPC.TL_inputReportReasonFake
import org.telegram.tgnet.TLRPC.TL_inputReportReasonIllegalDrugs
import org.telegram.tgnet.TLRPC.TL_inputReportReasonOther
import org.telegram.tgnet.TLRPC.TL_inputReportReasonPersonalDetails
import org.telegram.tgnet.TLRPC.TL_inputReportReasonPornography
import org.telegram.tgnet.TLRPC.TL_inputReportReasonSpam
import org.telegram.tgnet.TLRPC.TL_inputReportReasonViolence
import org.telegram.tgnet.TLRPC.TL_langPackLanguage
import org.telegram.tgnet.TLRPC.TL_messageActionChatAddUser
import org.telegram.tgnet.TLRPC.TL_messageActionChatDeleteUser
import org.telegram.tgnet.TLRPC.TL_messageActionChatJoinedByLink
import org.telegram.tgnet.TLRPC.TL_messageActionContactSignUp
import org.telegram.tgnet.TLRPC.TL_messageActionEmpty
import org.telegram.tgnet.TLRPC.TL_messageActionGeoProximityReached
import org.telegram.tgnet.TLRPC.TL_messageActionPhoneCall
import org.telegram.tgnet.TLRPC.TL_messageActionPinMessage
import org.telegram.tgnet.TLRPC.TL_messageActionSetChatTheme
import org.telegram.tgnet.TLRPC.TL_messageActionUserJoined
import org.telegram.tgnet.TLRPC.TL_messages_addChatUser
import org.telegram.tgnet.TLRPC.TL_messages_checkHistoryImport
import org.telegram.tgnet.TLRPC.TL_messages_checkHistoryImportPeer
import org.telegram.tgnet.TLRPC.TL_messages_createChat
import org.telegram.tgnet.TLRPC.TL_messages_editChatAdmin
import org.telegram.tgnet.TLRPC.TL_messages_editChatDefaultBannedRights
import org.telegram.tgnet.tlrpc.TL_messages_editMessage
import org.telegram.tgnet.TLRPC.TL_messages_forwardMessages
import org.telegram.tgnet.TLRPC.TL_messages_getAttachedStickers
import org.telegram.tgnet.TLRPC.TL_messages_importChatInvite
import org.telegram.tgnet.TLRPC.TL_messages_initHistoryImport
import org.telegram.tgnet.TLRPC.TL_messages_migrateChat
import org.telegram.tgnet.TLRPC.TL_messages_report
import org.telegram.tgnet.TLRPC.TL_messages_sendInlineBotResult
import org.telegram.tgnet.TLRPC.TL_messages_sendMedia
import org.telegram.tgnet.TLRPC.TL_messages_sendMessage
import org.telegram.tgnet.TLRPC.TL_messages_sendMultiMedia
import org.telegram.tgnet.TLRPC.TL_messages_sendScheduledMessages
import org.telegram.tgnet.TLRPC.TL_messages_startBot
import org.telegram.tgnet.TLRPC.TL_messages_startHistoryImport
import org.telegram.tgnet.TLRPC.TL_payments_sendPaymentForm
import org.telegram.tgnet.TLRPC.TL_payments_validateRequestedInfo
import org.telegram.tgnet.TLRPC.TL_peerNotifySettings
import org.telegram.tgnet.TLRPC.TL_phone_inviteToGroupCall
import org.telegram.tgnet.TLRPC.TL_updateUserName
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_channels_editBanned
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.Theme.ThemeAccent
import org.telegram.ui.ActionBar.Theme.ThemeInfo
import org.telegram.ui.CacheControlActivity
import org.telegram.ui.Cells.AccountSelectCell
import org.telegram.ui.Cells.CheckBoxCell
import org.telegram.ui.Cells.RadioColorCell
import org.telegram.ui.Cells.TextColorCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet
import org.telegram.ui.Components.limits.ChannelsLimitReachedBottomSheet
import org.telegram.ui.Components.limits.ChannelsSubscriptionsLimitBottomSheet
import org.telegram.ui.Components.voip.VoIPHelper
import org.telegram.ui.LanguageSelectActivity
import org.telegram.ui.LaunchActivity
import org.telegram.ui.LoginActivity
import org.telegram.ui.NotificationsCustomSettingsActivity
import org.telegram.ui.NotificationsSettingsActivity.NotificationException
import org.telegram.ui.ProfileNotificationsActivity
import org.telegram.ui.ThemePreviewActivity
import org.telegram.ui.TooManyCommunitiesActivity
import java.net.IDN
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.ceil

@SuppressLint("AppCompatCustomView")
object AlertsCreator {
	const val PERMISSIONS_REQUEST_TOP_ICON_SIZE = 72
	const val NEW_DENY_DIALOG_TOP_ICON_SIZE = 52
	const val REPORT_TYPE_SPAM = 0
	const val REPORT_TYPE_VIOLENCE = 1
	const val REPORT_TYPE_CHILD_ABUSE = 2
	const val REPORT_TYPE_ILLEGAL_DRUGS = 3
	const val REPORT_TYPE_PERSONAL_DETAILS = 4
	const val REPORT_TYPE_PORNOGRAPHY = 5
	const val REPORT_TYPE_FAKE_ACCOUNT = 6
	const val REPORT_TYPE_OTHER = 100

	@JvmStatic
	fun createForgotPasscodeDialog(context: Context): Dialog {
		return AlertDialog.Builder(context).setTitle(context.getString(R.string.ForgotPasscode)).setMessage(context.getString(R.string.ForgotPasscodeInfo)).setPositiveButton(context.getString(R.string.Close), null).create()
	}

	@JvmStatic
	fun createLocationRequiredDialog(context: Context, friends: Boolean): Dialog {
		return AlertDialog.Builder(context).setMessage(AndroidUtilities.replaceTags(if (friends) context.getString(R.string.PermissionNoLocationFriends) else context.getString(R.string.PermissionNoLocationPeopleNearby))).setTopAnimation(R.raw.permission_request_location, PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, ResourcesCompat.getColor(context.resources, R.color.brand, null)).setPositiveButton(context.getString(R.string.PermissionOpenSettings)) { _, _ ->
			try {
				val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
				intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.packageName))
				context.startActivity(intent)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}.setNegativeButton(context.getString(R.string.ContactsPermissionAlertNotNow), null).create()
	}

	fun createBackgroundActivityDialog(context: Context): Dialog {
		return AlertDialog.Builder(context).setTitle(context.getString(R.string.AllowBackgroundActivity)).setMessage(AndroidUtilities.replaceTags(context.getString(if (OneUIUtilities.isOneUI()) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.string.AllowBackgroundActivityInfoOneUIAboveS else R.string.AllowBackgroundActivityInfoOneUIBelowS else R.string.AllowBackgroundActivityInfo))).setTopAnimation(R.raw.permission_request_apk, PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, ResourcesCompat.getColor(context.resources, R.color.brand, null)).setPositiveButton(context.getString(R.string.PermissionOpenSettings)) { _, _ ->
			try {
				val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
				intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.packageName))
				context.startActivity(intent)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}.setNegativeButton(context.getString(R.string.ContactsPermissionAlertNotNow), null).create()
	}

	@JvmStatic
	fun createWebViewPermissionsRequestDialog(context: Context, systemPermissions: Array<String>?, @RawRes animationId: Int, title: String?, titleWithHint: String?, callback: Consumer<Boolean>): Dialog {
		var showSettings = false

		if (systemPermissions != null && context is Activity) {
			for (perm in systemPermissions) {
				if (context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED && context.shouldShowRequestPermissionRationale(perm)) {
					showSettings = true
					break
				}
			}
		}

		val gotCallback = AtomicBoolean()
		val finalShowSettings = showSettings

		return AlertDialog.Builder(context).setTopAnimation(animationId, PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, ResourcesCompat.getColor(context.resources, R.color.brand, null)).setMessage(AndroidUtilities.replaceTags(if (showSettings) titleWithHint else title)).setPositiveButton(context.getString(if (showSettings) R.string.PermissionOpenSettings else R.string.BotWebViewRequestAllow)) { _, _ ->
			if (finalShowSettings) {
				try {
					val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
					intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.packageName))
					context.startActivity(intent)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
			else {
				gotCallback.set(true)
				callback.accept(true)
			}
		}.setNegativeButton(context.getString(R.string.BotWebViewRequestDontAllow)) { _, _ ->
			gotCallback.set(true)
			callback.accept(false)
		}.setOnDismissListener {
			if (!gotCallback.get()) {
				callback.accept(false)
			}
		}.create()
	}

	@JvmStatic
	fun processError(currentAccount: Int, error: TL_error?, fragment: BaseFragment?, request: TLObject?, vararg args: Any?): Dialog? {
		if (error == null || error.code == 406 || error.text == null || fragment == null) {
			return null
		}

		val context = ApplicationLoader.applicationContext

		if (request is TL_messages_initHistoryImport || request is TL_messages_checkHistoryImportPeer || request is TL_messages_checkHistoryImport || request is TL_messages_startHistoryImport) {
			val peer = when (request) {
				is TL_messages_initHistoryImport -> request.peer
				is TL_messages_startHistoryImport -> request.peer
				else -> null
			}

			if (error.text.contains("USER_IS_BLOCKED")) {
				showSimpleAlert(fragment, context.getString(R.string.ImportErrorTitle), context.getString(R.string.ImportErrorUserBlocked))
			}
			else if (error.text.contains("USER_NOT_MUTUAL_CONTACT")) {
				showSimpleAlert(fragment, context.getString(R.string.ImportErrorTitle), context.getString(R.string.ImportMutualError))
			}
			else if (error.text.contains("IMPORT_PEER_TYPE_INVALID")) {
				if (peer is TL_inputPeerUser) {
					showSimpleAlert(fragment, context.getString(R.string.ImportErrorTitle), context.getString(R.string.ImportErrorChatInvalidUser))
				}
				else {
					showSimpleAlert(fragment, context.getString(R.string.ImportErrorTitle), context.getString(R.string.ImportErrorChatInvalidGroup))
				}
			}
			else if (error.text.contains("CHAT_ADMIN_REQUIRED")) {
				showSimpleAlert(fragment, context.getString(R.string.ImportErrorTitle), context.getString(R.string.ImportErrorNotAdmin))
			}
			else if (error.text.startsWith("IMPORT_FORMAT")) {
				showSimpleAlert(fragment, context.getString(R.string.ImportErrorTitle), context.getString(R.string.ImportErrorFileFormatInvalid))
			}
			else if (error.text.startsWith("PEER_ID_INVALID")) {
				showSimpleAlert(fragment, context.getString(R.string.ImportErrorTitle), context.getString(R.string.ImportErrorPeerInvalid))
			}
			else if (error.text.contains("IMPORT_LANG_NOT_FOUND")) {
				showSimpleAlert(fragment, context.getString(R.string.ImportErrorTitle), context.getString(R.string.ImportErrorFileLang))
			}
			else if (error.text.contains("IMPORT_UPLOAD_FAILED")) {
				showSimpleAlert(fragment, context.getString(R.string.ImportErrorTitle), context.getString(R.string.ImportFailedToUpload))
			}
			else if (error.text.startsWith("FLOOD_WAIT")) {
				showFloodWaitAlert(error.text, fragment)
			}
			else {
				showSimpleAlert(fragment, context.getString(R.string.ImportErrorTitle), "${context.getString(R.string.ErrorOccurred)}\n${error.text}")
			}
		}
		else if (request is TL_account_saveSecureValue || request is TL_account_getAuthorizationForm) {
			if (error.text.contains("PHONE_NUMBER_INVALID")) {
				showSimpleAlert(fragment, context.getString(R.string.InvalidPhoneNumber))
			}
			else if (error.text.startsWith("FLOOD_WAIT")) {
				showSimpleAlert(fragment, context.getString(R.string.FloodWait))
			}
			else if ("APP_VERSION_OUTDATED" == error.text) {
				showUpdateAppAlert(fragment.getParentActivity(), context.getString(R.string.UpdateAppAlert), true)
			}
			else {
				showSimpleAlert(fragment, "${context.getString(R.string.ErrorOccurred)}\n${error.text}")
			}
		}
		else if (request is TL_channels_joinChannel || request is TL_channels_editAdmin || request is TL_channels_inviteToChannel || request is TL_messages_addChatUser || request is TL_messages_startBot || request is TL_channels_editBanned || request is TL_messages_editChatDefaultBannedRights || request is TL_messages_editChatAdmin || request is TL_messages_migrateChat || request is TL_phone_inviteToGroupCall) {
			if (error.text == "CHANNELS_TOO_MUCH") {
				if (fragment.getParentActivity() != null) {
					fragment.showDialog(ChannelsSubscriptionsLimitBottomSheet(fragment, true, currentAccount))
				}
				else {
					if (request is TL_channels_joinChannel || request is TL_channels_inviteToChannel) {
						fragment.presentFragment(TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_JOIN))
					}
					else {
						fragment.presentFragment(TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_EDIT))
					}
				}

				return null
			}
			else {
				showAddUserAlert(error.text, fragment, (args.firstOrNull() as? Boolean) ?: false, request)
			}
		}
		else if (request is TL_messages_createChat) {
			if (error.text == "CHANNELS_TOO_MUCH") {
				if (fragment.getParentActivity() != null) {
					fragment.showDialog(LimitReachedBottomSheet(fragment, LimitReachedBottomSheet.TYPE_TO_MANY_COMMUNITIES, currentAccount))
				}
				else {
					fragment.presentFragment(TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_CREATE))
				}

				return null
			}
			else if (error.text.startsWith("FLOOD_WAIT")) {
				showFloodWaitAlert(error.text, fragment)
			}
			else {
				showAddUserAlert(error.text, fragment, false, request)
			}
		}
		else if (request is TL_channels_createChannel) {
			if (error.text == "CHANNELS_TOO_MUCH") {
				if (fragment.getParentActivity() != null) {
					fragment.showDialog(ChannelsLimitReachedBottomSheet(fragment, true, currentAccount))
				}
				else {
					fragment.presentFragment(TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_CREATE))
				}
				return null
			}
			else if (error.text.startsWith("FLOOD_WAIT")) {
				showFloodWaitAlert(error.text, fragment)
			}
			else {
				showAddUserAlert(error.text, fragment, false, request)
			}
		}
		else if (request is TL_messages_editMessage) {
			if (error.text != "MESSAGE_NOT_MODIFIED") {
				showSimpleAlert(fragment, context.getString(R.string.EditMessageError))
			}
		}
		else if (request is TL_messages_sendMessage || request is TL_messages_sendMedia || request is TL_messages_sendInlineBotResult || request is TL_messages_forwardMessages || request is TL_messages_sendMultiMedia || request is TL_messages_sendScheduledMessages) {
			when (error.text) {
				"PEER_FLOOD" -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 0)
				"USER_BANNED_IN_CHANNEL" -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 5)
				"SCHEDULE_TOO_MUCH" -> showSimpleToast(fragment, context.getString(R.string.MessageScheduledLimitReached))
			}
		}
		else if (request is TL_messages_importChatInvite) {
			if (error.text.startsWith("FLOOD_WAIT")) {
				showSimpleAlert(fragment, context.getString(R.string.FloodWait))
			}
			else if (error.text == "USERS_TOO_MUCH") {
				showSimpleAlert(fragment, context.getString(R.string.JoinToGroupErrorFull))
			}
			else if (error.text == "CHANNELS_TOO_MUCH") {
				if (fragment.getParentActivity() != null) {
					fragment.showDialog(LimitReachedBottomSheet(fragment, LimitReachedBottomSheet.TYPE_TO_MANY_COMMUNITIES, currentAccount))
				}
				else {
					fragment.presentFragment(TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_JOIN))
				}
			}
			else if (error.text == "INVITE_HASH_EXPIRED") {
				showSimpleAlert(fragment, context.getString(R.string.ExpiredLink), context.getString(R.string.InviteExpired))
			}
			else {
				showSimpleAlert(fragment, context.getString(R.string.JoinToGroupErrorNotExist))
			}
		}
		else if (request is TL_messages_getAttachedStickers) {
			if (fragment.getParentActivity() != null) {
				Toast.makeText(fragment.getParentActivity(), "${context.getString(R.string.ErrorOccurred)}\n${error.text}", Toast.LENGTH_SHORT).show()
			}
		}
		else if (request is TL_account_confirmPhone || request is TL_account_verifyPhone || request is TL_account_verifyEmail) {
			return if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID") || error.text.contains("CODE_INVALID") || error.text.contains("CODE_EMPTY")) {
				showSimpleAlert(fragment, context.getString(R.string.InvalidCode))
			}
			else if (error.text.contains("PHONE_CODE_EXPIRED") || error.text.contains("EMAIL_VERIFY_EXPIRED")) {
				showSimpleAlert(fragment, context.getString(R.string.CodeExpired))
			}
			else if (error.text.startsWith("FLOOD_WAIT")) {
				showSimpleAlert(fragment, context.getString(R.string.FloodWait))
			}
			else {
				showSimpleAlert(fragment, error.text)
			}
		}
		else if (request is TL_auth_resendCode) {
			if (error.text.contains("PHONE_NUMBER_INVALID")) {
				return showSimpleAlert(fragment, context.getString(R.string.InvalidPhoneNumber))
			}
			else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
				return showSimpleAlert(fragment, context.getString(R.string.InvalidCode))
			}
			else if (error.text.contains("PHONE_CODE_EXPIRED")) {
				return showSimpleAlert(fragment, context.getString(R.string.CodeExpired))
			}
			else if (error.text.startsWith("FLOOD_WAIT")) {
				return showSimpleAlert(fragment, context.getString(R.string.FloodWait))
			}
			else if (error.code != -1000) {
				return showSimpleAlert(fragment, "${context.getString(R.string.ErrorOccurred)}\n${error.text}")
			}
		}
		else if (request is TL_account_sendConfirmPhoneCode) {
			return if (error.code == 400) {
				showSimpleAlert(fragment, context.getString(R.string.CancelLinkExpired))
			}
			else {
				if (error.text.startsWith("FLOOD_WAIT")) {
					showSimpleAlert(fragment, context.getString(R.string.FloodWait))
				}
				else {
					showSimpleAlert(fragment, context.getString(R.string.ErrorOccurred))
				}
			}
		}
		else if (request is TL_account_changePhone) {
			if (error.text.contains("PHONE_NUMBER_INVALID")) {
				showSimpleAlert(fragment, context.getString(R.string.InvalidPhoneNumber))
			}
			else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
				showSimpleAlert(fragment, context.getString(R.string.InvalidCode))
			}
			else if (error.text.contains("PHONE_CODE_EXPIRED")) {
				showSimpleAlert(fragment, context.getString(R.string.CodeExpired))
			}
			else if (error.text.startsWith("FLOOD_WAIT")) {
				showSimpleAlert(fragment, context.getString(R.string.FloodWait))
			}
			else if (error.text.contains("FRESH_CHANGE_PHONE_FORBIDDEN")) {
				showSimpleAlert(fragment, context.getString(R.string.FreshChangePhoneForbidden))
			}
			else {
				showSimpleAlert(fragment, error.text)
			}
		}
		else if (request is TL_account_sendChangePhoneCode) {
			if (error.text.contains("PHONE_NUMBER_INVALID")) {
				LoginActivity.needShowInvalidAlert(fragment, (args[0] as String), false)
			}
			else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
				showSimpleAlert(fragment, context.getString(R.string.InvalidCode))
			}
			else if (error.text.contains("PHONE_CODE_EXPIRED")) {
				showSimpleAlert(fragment, context.getString(R.string.CodeExpired))
			}
			else if (error.text.startsWith("FLOOD_WAIT")) {
				showSimpleAlert(fragment, context.getString(R.string.FloodWait))
			}
			else if (error.text.startsWith("PHONE_NUMBER_OCCUPIED")) {
				showSimpleAlert(fragment, LocaleController.formatString("ChangePhoneNumberOccupied", R.string.ChangePhoneNumberOccupied, args[0]))
			}
			else if (error.text.startsWith("PHONE_NUMBER_BANNED")) {
				LoginActivity.needShowInvalidAlert(fragment, (args[0] as String), true)
			}
			else {
				showSimpleAlert(fragment, context.getString(R.string.ErrorOccurred))
			}
		}
		else if (request is TL_updateUserName) {
			when (error.text) {
				"USERNAME_INVALID" -> showSimpleAlert(fragment, context.getString(R.string.UsernameInvalid))
				"USERNAME_OCCUPIED" -> showSimpleAlert(fragment, context.getString(R.string.UsernameInUse))
				else -> showSimpleAlert(fragment, context.getString(R.string.ErrorOccurred))
			}
		}
		else if (request is TL_contacts_importContacts) {
			if (error.text.startsWith("FLOOD_WAIT")) {
				showSimpleAlert(fragment, context.getString(R.string.FloodWait))
			}
			else {
				showSimpleAlert(fragment, "${context.getString(R.string.ErrorOccurred)}\n${error.text}")
			}
		}
		else if (request is TL_account_getPassword || request is TL_account_getTmpPassword) {
			if (error.text.startsWith("FLOOD_WAIT")) {
				showSimpleToast(fragment, getFloodWaitString(error.text))
			}
			else {
				showSimpleToast(fragment, error.text)
			}
		}
		else if (request is TL_payments_sendPaymentForm) {
			when (error.text) {
				"BOT_PRECHECKOUT_FAILED" -> showSimpleToast(fragment, context.getString(R.string.PaymentPrecheckoutFailed))
				"PAYMENT_FAILED" -> showSimpleToast(fragment, context.getString(R.string.PaymentFailed))
				else -> showSimpleToast(fragment, error.text)
			}
		}
		else if (request is TL_payments_validateRequestedInfo) {
			if (error.text == "SHIPPING_NOT_AVAILABLE") {
				showSimpleToast(fragment, context.getString(R.string.PaymentNoShippingMethod))
			}
			else {
				showSimpleToast(fragment, error.text)
			}
		}

		return null
	}

	@JvmStatic
	fun showSimpleToast(baseFragment: BaseFragment?, text: String?): Toast? {
		if (text == null) {
			return null
		}

		val context = if (baseFragment?.getParentActivity() != null) {
			baseFragment.getParentActivity()
		}
		else {
			ApplicationLoader.applicationContext
		}

		val toast = Toast.makeText(context, text, Toast.LENGTH_LONG)
		toast.show()

		return toast
	}

	@JvmStatic
	fun showUpdateAppAlert(context: Context?, text: String?, updateApp: Boolean): AlertDialog? {
		if (context == null || text == null) {
			return null
		}

		val builder = AlertDialog.Builder(context)
		builder.setTitle(context.getString(R.string.AppName))
		builder.setMessage(text)
		builder.setPositiveButton(context.getString(R.string.OK), null)

		if (updateApp) {
			builder.setNegativeButton(context.getString(R.string.UpdateApp)) { _, _ ->
				Browser.openUrl(context, BuildVars.PLAYSTORE_APP_URL)
			}
		}

		return builder.show()
	}

	fun createLanguageAlert(activity: LaunchActivity, language: TL_langPackLanguage?): AlertDialog.Builder? {
		if (language == null) {
			return null
		}

		language.lang_code = language.lang_code.replace('-', '_').lowercase(Locale.getDefault())
		language.plural_code = language.plural_code.replace('-', '_').lowercase(Locale.getDefault())

		if (language.base_lang_code != null) {
			language.base_lang_code = language.base_lang_code.replace('-', '_').lowercase(Locale.getDefault())
		}

		val spanned: SpannableStringBuilder
		val builder = AlertDialog.Builder(activity)
		val currentInfo = LocaleController.getInstance().currentLocaleInfo
		val str: String

		if (currentInfo.shortName == language.lang_code) {
			builder.setTitle(activity.getString(R.string.Language))
			str = LocaleController.formatString("LanguageSame", R.string.LanguageSame, language.name)
			builder.setNegativeButton(activity.getString(R.string.OK), null)

			builder.setNeutralButton(activity.getString(R.string.SETTINGS)) { _, _ ->
				activity.presentFragment(LanguageSelectActivity())
			}
		}
		else {
			if (language.strings_count == 0) {
				builder.setTitle(activity.getString(R.string.LanguageUnknownTitle))
				str = LocaleController.formatString("LanguageUnknownCustomAlert", R.string.LanguageUnknownCustomAlert, language.name)
				builder.setNegativeButton(activity.getString(R.string.OK), null)
			}
			else {
				builder.setTitle(activity.getString(R.string.LanguageTitle))

				str = if (language.official) {
					LocaleController.formatString("LanguageAlert", R.string.LanguageAlert, language.name, ceil((language.translated_count / language.strings_count.toFloat() * 100).toDouble()).toInt())
				}
				else {
					LocaleController.formatString("LanguageCustomAlert", R.string.LanguageCustomAlert, language.name, ceil((language.translated_count / language.strings_count.toFloat() * 100).toDouble()).toInt())
				}

				builder.setPositiveButton(activity.getString(R.string.Change)) { _, _ ->
					val key = if (language.official) {
						"remote_" + language.lang_code
					}
					else {
						"unofficial_" + language.lang_code
					}

					var localeInfo = LocaleController.getInstance().getLanguageFromDict(key)

					if (localeInfo == null) {
						localeInfo = LocaleInfo()
						localeInfo.name = language.native_name
						localeInfo.nameEnglish = language.name
						localeInfo.shortName = language.lang_code
						localeInfo.baseLangCode = language.base_lang_code
						localeInfo.pluralLangCode = language.plural_code
						localeInfo.isRtl = language.rtl

						if (language.official) {
							localeInfo.pathToFile = "remote"
						}
						else {
							localeInfo.pathToFile = "unofficial"
						}
					}

					LocaleController.getInstance().applyLanguage(localeInfo, true, false, false, true, UserConfig.selectedAccount)

					activity.rebuildAllFragments(true)
				}

				builder.setNegativeButton(activity.getString(R.string.Cancel), null)
			}
		}

		spanned = SpannableStringBuilder(AndroidUtilities.replaceTags(str))

		val start = TextUtils.indexOf(spanned, '[')
		val end: Int

		if (start != -1) {
			end = TextUtils.indexOf(spanned, ']', start + 1)

			if (end != -1) {
				spanned.delete(end, end + 1)
				spanned.delete(start, start + 1)
			}
		}
		else {
			end = -1
		}

		if (start != -1 && end != -1) {
			spanned.setSpan(object : URLSpanNoUnderline(language.translations_url) {
				override fun onClick(widget: View) {
					builder.getDismissRunnable().run()
					super.onClick(widget)
				}
			}, start, end - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
		}

		val message = TextView(activity)
		message.text = spanned
		message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		message.setLinkTextColor(activity.getColor(R.color.brand))
		message.highlightColor = activity.getColor(R.color.white)
		message.setPadding(AndroidUtilities.dp(23f), 0, AndroidUtilities.dp(23f), 0)
		message.movementMethod = AndroidUtilities.LinkMovementMethodMy()
		message.setTextColor(activity.getColor(R.color.text))

		builder.setView(message)

		return builder
	}

	fun checkSlowMode(context: Context, currentAccount: Int, did: Long, few: Boolean): Boolean {
		@Suppress("NAME_SHADOWING") var few = few

		if (DialogObject.isChatDialog(did)) {
			val chat = MessagesController.getInstance(currentAccount).getChat(-did)

			if (chat != null && chat.slowmode_enabled && !ChatObject.hasAdminRights(chat)) {
				if (!few) {
					var chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat.id)

					if (chatFull == null) {
						chatFull = MessagesStorage.getInstance(currentAccount).loadChatInfo(chat.id, ChatObject.isChannel(chat), CountDownLatch(1), false, false)
					}
					if (chatFull != null && chatFull.slowmode_next_send_date >= ConnectionsManager.getInstance(currentAccount).currentTime) {
						few = true
					}
				}

				if (few) {
					createSimpleAlert(context, chat.title, context.getString(R.string.SlowmodeSendError))!!.show()
					return true
				}
			}
		}

		return false
	}

	fun createSimpleAlert(context: Context?, text: String?): AlertDialog.Builder? {
		return createSimpleAlert(context, null, text)
	}

	fun createSimpleAlert(context: Context?, @DrawableRes topImage: Int, title: String?, text: String?): AlertDialog.Builder? {
		if (context == null || text == null) {
			return null
		}

		val builder = AlertDialog.Builder(context)
		builder.setTitle(title ?: context.getString(R.string.AppName))
		builder.setMessage(text)
		builder.setPositiveButton(context.getString(R.string.OK), null)

		if (topImage != 0) {
			builder.setTopImage(topImage, Color.TRANSPARENT)
		}

		return builder
	}

	@JvmStatic
	fun createSimpleAlert(context: Context?, title: String?, text: String?): AlertDialog.Builder? {
		return createSimpleAlert(context, 0, title, text)
	}

	fun createSimpleAlert(context: Context, title: String?, text: String, positiveButtonTitle: String, negativeButtonTitle: String, positiveButtonListener: DialogInterface.OnClickListener?, negativeButtonListener: DialogInterface.OnClickListener?): AlertDialog.Builder {
		val builder = AlertDialog.Builder(context)
		builder.setTitle(title)
		builder.setMessage(text)
		builder.setPositiveButton(positiveButtonTitle, positiveButtonListener)
		builder.setNegativeButton(negativeButtonTitle, negativeButtonListener)
		builder.setNegativeButtonColor(ResourcesCompat.getColor(context.resources, R.color.brand_day_night, null))
		builder.setPositiveButtonColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
		return builder
	}

	@JvmStatic
	fun showSimpleAlert(baseFragment: BaseFragment?, text: String?): Dialog? {
		return showSimpleAlert(baseFragment, null, text)
	}

	@JvmStatic
	fun showSimpleAlert(baseFragment: BaseFragment?, title: String?, text: String?): Dialog? {
		if (text == null || baseFragment == null || baseFragment.getParentActivity() == null) {
			return null
		}

		val builder = createSimpleAlert(baseFragment.getParentActivity(), title, text)
		val dialog: Dialog = builder!!.create()

		baseFragment.showDialog(dialog)

		return dialog
	}

	fun showBlockReportSpamReplyAlert(fragment: BaseFragment?, messageObject: MessageObject?, peerId: Long, hideDim: Runnable?) {
		if (fragment?.getParentActivity() == null || messageObject == null) {
			return
		}

		val context: Context? = fragment.getParentActivity()
		val accountInstance = fragment.accountInstance
		val user = if (peerId > 0) accountInstance.messagesController.getUser(peerId) else null
		val chat = if (peerId < 0) accountInstance.messagesController.getChat(-peerId) else null

		if (user == null && chat == null) {
			return
		}

		val builder = AlertDialog.Builder(fragment.getParentActivity()!!)
		builder.setDimEnabled(hideDim == null)

		builder.setOnPreDismissListener {
			hideDim?.run()
		}

		builder.setTitle(context!!.getString(R.string.BlockUser))

		if (user != null) {
			builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BlockUserReplyAlert", R.string.BlockUserReplyAlert, UserObject.getFirstName(user))))
		}
		else {
			builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BlockUserReplyAlert", R.string.BlockUserReplyAlert, chat?.title)))
		}

		val cells = arrayOfNulls<CheckBoxCell>(1)

		val linearLayout = LinearLayout(fragment.getParentActivity())
		linearLayout.orientation = LinearLayout.VERTICAL

		cells[0] = CheckBoxCell(fragment.getParentActivity()!!, 1)
		cells[0]!!.background = Theme.getSelectorDrawable(false)
		cells[0]!!.tag = 0
		cells[0]!!.setText(context.getString(R.string.DeleteReportSpam), "", checked = true, divider = false)
		cells[0]!!.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(16f) else AndroidUtilities.dp(8f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(8f) else AndroidUtilities.dp(16f), 0)

		linearLayout.addView(cells[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		cells[0]!!.setOnClickListener {
			val num = it.tag as Int
			cells[num]!!.setChecked(!cells[num]!!.isChecked, true)
		}

		builder.setCustomViewOffset(12)
		builder.setView(linearLayout)

		builder.setPositiveButton(context.getString(R.string.BlockAndDeleteReplies)) { _, _ ->
			if (fragment is ChatActivity) {
				if (user != null) {
					accountInstance.messagesStorage.deleteUserChatHistory(fragment.dialogId, user.id)
				}
				else {
					accountInstance.messagesStorage.deleteUserChatHistory(fragment.dialogId, -chat!!.id)
				}
			}

			val request = TL_contacts_blockFromReplies()
			request.msg_id = messageObject.id
			request.delete_message = true
			request.delete_history = true

			if (cells[0]!!.isChecked) {
				request.report_spam = true

				if (fragment.getParentActivity() != null) {
					if (fragment is ChatActivity) {
						fragment.undoView!!.showWithAction(0, UndoView.ACTION_REPORT_SENT, null)
					}
					else {
						BulletinFactory.of(fragment).createReportSent().show()
					}
				}
			}

			accountInstance.connectionsManager.sendRequest(request) { response, _ ->
				if (response is Updates) {
					accountInstance.messagesController.processUpdates(response as Updates?, false)
				}
			}
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		val dialog = builder.create()

		fragment.showDialog(dialog)

		val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
		button?.setTextColor(fragment.getParentActivity()!!.getColor(R.color.purple))
	}

	fun showBlockReportSpamAlert(fragment: BaseFragment?, dialogId: Long, currentUser: User?, currentChat: Chat?, encryptedChat: EncryptedChat?, isLocation: Boolean, chatInfo: ChatFull?, callback: MessagesStorage.IntCallback) {
		val context = fragment?.getParentActivity() ?: return
		val accountInstance = fragment.accountInstance
		val builder = AlertDialog.Builder(fragment.getParentActivity()!!)
		val reportText: CharSequence
		val cells: Array<CheckBoxCell?>?
		val preferences = MessagesController.getNotificationsSettings(fragment.currentAccount)
		val showReport = encryptedChat != null || preferences.getBoolean("dialog_bar_report$dialogId", false)

		if (currentUser != null) {
			builder.setTitle(LocaleController.formatString("BlockUserTitle", R.string.BlockUserTitle, UserObject.getFirstName(currentUser)))
			builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BlockUserAlert", R.string.BlockUserAlert, UserObject.getFirstName(currentUser))))

			reportText = context.getString(R.string.BlockContact)

			cells = arrayOfNulls(2)

			val linearLayout = LinearLayout(fragment.getParentActivity())
			linearLayout.orientation = LinearLayout.VERTICAL

			for (a in 0..1) {
				if (a == 0 && !showReport) {
					continue
				}

				cells[a] = CheckBoxCell(fragment.getParentActivity()!!, 1)
				cells[a]!!.background = Theme.getSelectorDrawable(false)
				cells[a]!!.tag = a

				if (a == 0) {
					cells[a]!!.setText(context.getString(R.string.DeleteReportSpam), "", checked = true, divider = false)
				}
				else {
					cells[a]!!.setText(LocaleController.formatString("DeleteThisChat", R.string.DeleteThisChat), "", checked = true, divider = false)
				}

				cells[a]!!.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(16f) else AndroidUtilities.dp(8f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(8f) else AndroidUtilities.dp(16f), 0)

				linearLayout.addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

				cells[a]!!.setOnClickListener { v: View ->
					val num = v.tag as Int
					cells[num]!!.setChecked(!cells[num]!!.isChecked, true)
				}
			}

			builder.setCustomViewOffset(12)
			builder.setView(linearLayout)
		}
		else {
			cells = null

			if (currentChat != null && isLocation) {
				builder.setTitle(context.getString(R.string.ReportUnrelatedGroup))

				if (chatInfo != null && chatInfo.location is TL_channelLocation) {
					val location = chatInfo.location as TL_channelLocation
					builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ReportUnrelatedGroupText", R.string.ReportUnrelatedGroupText, location.address)))
				}
				else {
					builder.setMessage(context.getString(R.string.ReportUnrelatedGroupTextNoAddress))
				}
			}
			else {
				builder.setTitle(context.getString(R.string.ReportSpamTitle))

				if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
					builder.setMessage(context.getString(R.string.ReportSpamAlertChannel))
				}
				else {
					builder.setMessage(context.getString(R.string.ReportSpamAlertGroup))
				}
			}

			reportText = context.getString(R.string.ReportChat)
		}

		builder.setPositiveButton(reportText) { _, _ ->
			if (currentUser != null) {
				accountInstance.messagesController.blockPeer(currentUser.id)
			}

			if (cells == null || cells[0] != null && cells[0]!!.isChecked) {
				accountInstance.messagesController.reportSpam(dialogId, currentUser, currentChat, encryptedChat, currentChat != null && isLocation)
			}

			if (cells == null || cells[1]!!.isChecked) {
				if (currentChat != null) {
					if (ChatObject.isNotInChat(currentChat)) {
						accountInstance.messagesController.deleteDialog(dialogId, 0)
					}
					else {
						accountInstance.messagesController.deleteParticipantFromChat(-dialogId, accountInstance.messagesController.getUser(accountInstance.userConfig.getClientUserId()))
					}
				}
				else {
					accountInstance.messagesController.deleteDialog(dialogId, 0)
				}

				callback.run(1)
			}
			else {
				callback.run(0)
			}
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		val dialog = builder.create()

		fragment.showDialog(dialog)

		val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView

		button?.setTextColor(fragment.getParentActivity()!!.getColor(R.color.purple))
	}

	@JvmOverloads
	@JvmStatic
	fun showCustomNotificationsDialog(parentFragment: BaseFragment?, did: Long, globalType: Int, exceptions: ArrayList<NotificationException>?, currentAccount: Int, callback: MessagesStorage.IntCallback?, resultCallback: MessagesStorage.IntCallback? = null) {
		val context = parentFragment?.getParentActivity() ?: return
		val defaultEnabled = NotificationsController.getInstance(currentAccount).isGlobalNotificationsEnabled(did)
		val descriptions = arrayOf(context.getString(R.string.NotificationsTurnOn), LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 1)), LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Days", 2)), if (did == 0L && parentFragment is NotificationsCustomSettingsActivity) null else context.getString(R.string.NotificationsCustomize), context.getString(R.string.NotificationsTurnOff))
		val icons = intArrayOf(R.drawable.notifications_on, R.drawable.notifications_mute1h, R.drawable.notifications_mute2d, R.drawable.notifications_settings, R.drawable.notifications_off)

		val linearLayout = LinearLayout(parentFragment.getParentActivity())
		linearLayout.orientation = LinearLayout.VERTICAL

		val builder = AlertDialog.Builder(parentFragment.getParentActivity()!!)

		for (a in descriptions.indices) {
			if (descriptions[a] == null) {
				continue
			}

			val textView = TextView(context)
			val drawable = ResourcesCompat.getDrawable(context.resources, icons[a], null)

			if (a == descriptions.size - 1) {
				textView.setTextColor(context.getColor(R.color.purple))
				drawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.purple), PorterDuff.Mode.SRC_IN)
			}
			else {
				textView.setTextColor(context.getColor(R.color.text))
				drawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.text), PorterDuff.Mode.SRC_IN)
			}

			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			textView.setLines(1)
			textView.setMaxLines(1)
			textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
			textView.tag = a
			textView.background = Theme.getSelectorDrawable(false)
			textView.setPadding(AndroidUtilities.dp(24f), 0, AndroidUtilities.dp(24f), 0)
			textView.setSingleLine(true)
			textView.setGravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
			textView.setCompoundDrawablePadding(AndroidUtilities.dp(26f))
			textView.text = descriptions[a]
			linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.TOP))

			textView.setOnClickListener {
				val i = it.tag as Int

				if (i == 0) {
					if (did != 0L) {
						val preferences = MessagesController.getNotificationsSettings(currentAccount)
						val editor = preferences.edit()

						if (defaultEnabled) {
							editor.remove("notify2_$did")
						}
						else {
							editor.putInt("notify2_$did", 0)
						}

						MessagesStorage.getInstance(currentAccount).setDialogFlags(did, 0)

						editor.commit()

						val dialog = MessagesController.getInstance(currentAccount).dialogs_dict[did]

						if (dialog != null) {
							dialog.notify_settings = TL_peerNotifySettings()
						}

						NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(did)

						if (resultCallback != null) {
							if (defaultEnabled) {
								resultCallback.run(0)
							}
							else {
								resultCallback.run(1)
							}
						}
					}
					else {
						NotificationsController.getInstance(currentAccount).setGlobalNotificationsEnabled(globalType, 0)
					}
				}
				else if (i == 3) {
					if (did != 0L) {
						val args = Bundle()
						args.putLong("dialog_id", did)

						parentFragment.presentFragment(ProfileNotificationsActivity(args))
					}
					else {
						parentFragment.presentFragment(NotificationsCustomSettingsActivity(globalType, exceptions))
					}
				}
				else {
					var untilTime = ConnectionsManager.getInstance(currentAccount).currentTime

					when (i) {
						1 -> untilTime += 60 * 60
						2 -> untilTime += 60 * 60 * 48
						4 -> untilTime = Int.MAX_VALUE
					}

					NotificationsController.getInstance(currentAccount).muteUntil(did, untilTime)

					if (did != 0L && resultCallback != null) {
						if (i == 4 && !defaultEnabled) {
							resultCallback.run(0)
						}
						else {
							resultCallback.run(1)
						}
					}

					if (did == 0L) {
						NotificationsController.getInstance(currentAccount).setGlobalNotificationsEnabled(globalType, Int.MAX_VALUE)
					}
				}

				callback?.run(i)

				builder.getDismissRunnable().run()

				var setting = -1

				when (i) {
					0 -> setting = NotificationsController.SETTING_MUTE_UNMUTE
					1 -> setting = NotificationsController.SETTING_MUTE_HOUR
					2 -> setting = NotificationsController.SETTING_MUTE_2_DAYS
					4 -> setting = NotificationsController.SETTING_MUTE_FOREVER
				}

				if (setting >= 0) {
					if (BulletinFactory.canShowBulletin(parentFragment)) {
						BulletinFactory.createMuteBulletin(parentFragment, setting).show()
					}
				}
			}
		}

		builder.setTitle(context.getString(R.string.Notifications))
		builder.setView(linearLayout)

		parentFragment.showDialog(builder.create())
	}

	fun showSecretLocationAlert(context: Context, currentAccount: Int, onSelectRunnable: Runnable?, inChat: Boolean): AlertDialog {
		val labels = mutableListOf<String>()
		val types = mutableListOf<Int>()
		val providers = MessagesController.getInstance(currentAccount).availableMapProviders

		if (providers and 1 != 0) {
			labels.add(context.getString(R.string.MapPreviewProviderTelegram))
			types.add(SharedConfig.MAP_PREVIEW_PROVIDER_ELLO)
		}

		if (providers and 2 != 0) {
			labels.add(context.getString(R.string.MapPreviewProviderGoogle))
			types.add(SharedConfig.MAP_PREVIEW_PROVIDER_GOOGLE)
		}

		if (providers and 4 != 0) {
			labels.add(context.getString(R.string.MapPreviewProviderYandex))
			types.add(SharedConfig.MAP_PREVIEW_PROVIDER_YANDEX)
		}

		labels.add(context.getString(R.string.MapPreviewProviderNobody))

		types.add(SharedConfig.MAP_PREVIEW_PROVIDER_NONE)

		val builder = AlertDialog.Builder(context)
		builder.setTitle(context.getString(R.string.MapPreviewProviderTitle))

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.VERTICAL

		builder.setView(linearLayout)

		for (a in labels.indices) {
			val cell = RadioColorCell(context)
			cell.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
			cell.tag = a
			cell.setCheckColor(context.getColor(R.color.brand), context.getColor(R.color.brand))
			cell.setTextAndValue(labels[a], SharedConfig.mapPreviewType == types[a])

			linearLayout.addView(cell)

			cell.setOnClickListener {
				val which = it.tag as Int

				SharedConfig.setSecretMapPreviewType(types[which])

				onSelectRunnable?.run()

				builder.getDismissRunnable().run()
			}
		}

		if (!inChat) {
			builder.setNegativeButton(context.getString(R.string.Cancel), null)
		}

		val dialog = builder.show()

		if (inChat) {
			dialog.setCanceledOnTouchOutside(false)
		}

		return dialog
	}

	private fun updateDayPicker(dayPicker: NumberPicker, monthPicker: NumberPicker, yearPicker: NumberPicker) {
		val calendar = Calendar.getInstance()
		calendar[Calendar.MONTH] = monthPicker.value
		calendar[Calendar.YEAR] = yearPicker.value

		dayPicker.setMinValue(1)
		dayPicker.setMaxValue(calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
	}

	private fun checkPickerDate(dayPicker: NumberPicker, monthPicker: NumberPicker, yearPicker: NumberPicker) {
		val calendar = Calendar.getInstance()
		calendar.setTimeInMillis(System.currentTimeMillis())

		val currentYear = calendar[Calendar.YEAR]
		val currentMonth = calendar[Calendar.MONTH]
		val currentDay = calendar[Calendar.DAY_OF_MONTH]

		if (currentYear > yearPicker.value) {
			yearPicker.value = currentYear
			//yearPicker.finishScroll();
		}

		if (yearPicker.value == currentYear) {
			if (currentMonth > monthPicker.value) {
				monthPicker.value = currentMonth
				//monthPicker.finishScroll();
			}

			if (currentMonth == monthPicker.value) {
				if (currentDay > dayPicker.value) {
					dayPicker.value = currentDay
					//dayPicker.finishScroll();
				}
			}
		}
	}

	@JvmStatic
	fun showOpenUrlAlert(fragment: BaseFragment?, url: String, punycode: Boolean, ask: Boolean) {
		showOpenUrlAlert(fragment, url, punycode, true, ask)
	}

	fun showOpenUrlAlert(fragment: BaseFragment?, url: String, punycode: Boolean, tryTelegraph: Boolean, ask: Boolean) {
		val context = fragment?.getParentActivity() ?: return

		val inlineReturn = if (fragment is ChatActivity) fragment.inlineReturn else 0

		if (Browser.isInternalUrl(url, null) || !ask) {
			Browser.openUrl(context, url, inlineReturn == 0L, tryTelegraph)
		}
		else {
			val urlFinal = if (punycode) {
				try {
					val uri = Uri.parse(url)
					val host = IDN.toASCII(uri.host, IDN.ALLOW_UNASSIGNED)
					uri.scheme + "://" + host + uri.path
				}
				catch (e: Exception) {
					FileLog.e(e)
					url
				}
			}
			else {
				url
			}

			val builder = AlertDialog.Builder(context)
			builder.setTitle(context.getString(R.string.OpenUrlTitle))

			val format = context.getString(R.string.OpenUrlAlert2)
			val index = format.indexOf("%")
			val stringBuilder = SpannableStringBuilder(String.format(format, urlFinal))

			if (index >= 0) {
				stringBuilder.setSpan(URLSpan(urlFinal), index, index + urlFinal.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			}

			builder.setMessage(stringBuilder)
			builder.setMessageTextViewClickable(false)

			builder.setPositiveButton(context.getString(R.string.Open)) { _, _ ->
				Browser.openUrl(fragment.getParentActivity(), url, inlineReturn == 0L, tryTelegraph)
			}

			builder.setNegativeButton(context.getString(R.string.Cancel), null)

			fragment.showDialog(builder.create())
		}
	}

	fun createSupportAlert(fragment: BaseFragment?): AlertDialog? {
		val context = fragment?.getParentActivity() ?: return null
		val message = TextView(context)
		val spanned: Spannable = SpannableString(Html.fromHtml(context.getString(R.string.AskAQuestionInfo).replace("\n", "<br>")))
		val spans = spanned.getSpans(0, spanned.length, URLSpan::class.java)

		for (urlSpan in spans) {
			var span = urlSpan
			val start = spanned.getSpanStart(span)
			val end = spanned.getSpanEnd(span)

			spanned.removeSpan(span)

			span = object : URLSpanNoUnderline(span.url) {
				override fun onClick(widget: View) {
					fragment.dismissCurrentDialog()
					super.onClick(widget)
				}
			}

			spanned.setSpan(span, start, end, 0)
		}

		message.text = spanned
		message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		message.setLinkTextColor(context.getColor(R.color.brand))
		message.highlightColor = context.getColor(R.color.darker_brand)
		message.setPadding(AndroidUtilities.dp(23f), 0, AndroidUtilities.dp(23f), 0)
		message.movementMethod = AndroidUtilities.LinkMovementMethodMy()
		message.setTextColor(context.getColor(R.color.text))

		val builder1 = AlertDialog.Builder(context)
		builder1.setView(message)
		builder1.setTitle(context.getString(R.string.AskAQuestion))

		builder1.setPositiveButton(context.getString(R.string.AskButton)) { _, _ ->
			performAskAQuestion(fragment)
		}

		builder1.setNegativeButton(context.getString(R.string.Cancel), null)

		return builder1.create()
	}

	private fun performAskAQuestion(fragment: BaseFragment) {
		val currentAccount = fragment.currentAccount
		val preferences = MessagesController.getMainSettings(currentAccount)
		val uid = AndroidUtilities.getPrefIntOrLong(preferences, "support_id2", 0)
		var supportUser: User? = null

		if (uid != 0L) {
			supportUser = MessagesController.getInstance(currentAccount).getUser(uid)

			if (supportUser == null) {
				val userString = preferences.getString("support_user", null)

				if (userString != null) {
					try {
						val datacentersBytes = Base64.decode(userString, Base64.DEFAULT)
						if (datacentersBytes != null) {
							val data = SerializedData(datacentersBytes)

							supportUser = User.TLdeserialize(data, data.readInt32(false), false)

							if (supportUser != null && supportUser.id == 333000L) {
								supportUser = null
							}

							data.cleanup()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
						supportUser = null
					}
				}
			}
		}

		if (supportUser == null) {
			val progressDialog = AlertDialog(fragment.getParentActivity()!!, 3)
			progressDialog.setCanCancel(false)
			progressDialog.show()

			val req = TL_help_getSupport()

			ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
				if (response is TL_help_support) {
					AndroidUtilities.runOnUIThread {
						val editor = preferences.edit()
						editor.putLong("support_id2", response.user.id)

						val data = SerializedData()

						response.user.serializeToStream(data)

						editor.putString("support_user", Base64.encodeToString(data.toByteArray(), Base64.DEFAULT))
						editor.commit()

						data.cleanup()

						try {
							progressDialog.dismiss()
						}
						catch (e: Exception) {
							FileLog.e(e)
						}

						MessagesStorage.getInstance(currentAccount).putUsersAndChats(listOf(response.user), null, true, true)
						MessagesController.getInstance(currentAccount).putUser(response.user, false)

						val args = Bundle()
						args.putLong("user_id", response.user.id)

						fragment.presentFragment(ChatActivity(args))
					}
				}
				else {
					AndroidUtilities.runOnUIThread {
						try {
							progressDialog.dismiss()
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}
			}
		}
		else {
			MessagesController.getInstance(currentAccount).putUser(supportUser, true)

			val args = Bundle()
			args.putLong("user_id", supportUser.id)

			fragment.presentFragment(ChatActivity(args))
		}
	}

	fun createImportDialogAlert(fragment: BaseFragment?, message: String, user: User?, chat: Chat?, onProcessRunnable: Runnable?) {
		val context = fragment?.getParentActivity()

		if (context == null || chat == null && user == null) {
			return
		}

		val account = fragment.currentAccount
		val builder = AlertDialog.Builder(context)
		val selfUserId = UserConfig.getInstance(account).getClientUserId()

		val messageTextView = TextView(context)
		messageTextView.setTextColor(context.getColor(R.color.text))
		messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		messageTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		val frameLayout = FrameLayout(context)

		builder.setView(frameLayout)

		val avatarDrawable = AvatarDrawable()
		avatarDrawable.setTextSize(AndroidUtilities.dp(12f))

		val imageView = BackupImageView(context)
		imageView.setRoundRadius(AndroidUtilities.dp(20f))

		frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 22f, 5f, 22f, 0f))

		val textView = TextView(context)
		textView.setTextColor(context.getColor(R.color.text))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		textView.setTypeface(Theme.TYPEFACE_BOLD)
		textView.setLines(1)
		textView.setMaxLines(1)
		textView.setSingleLine(true)
		textView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL)
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.text = context.getString(R.string.ImportMessages)

		frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 21 else 76).toFloat(), 11f, (if (LocaleController.isRTL) 76 else 21).toFloat(), 0f))
		frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24f, 57f, 24f, 9f))

		if (user != null) {
			if (UserObject.isReplyUser(user)) {
				avatarDrawable.setSmallSize(true)
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_REPLIES

				imageView.setImage(null, null, avatarDrawable, user)
			}
			else if (user.id == selfUserId) {
				avatarDrawable.setSmallSize(true)
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED

				imageView.setImage(null, null, avatarDrawable, user)
			}
			else {
				avatarDrawable.setSmallSize(false)
				avatarDrawable.setInfo(user)

				imageView.setForUserOrChat(user, avatarDrawable)
			}
		}
		else {
			avatarDrawable.setInfo(chat)
			imageView.setForUserOrChat(chat, avatarDrawable)
		}

		messageTextView.text = AndroidUtilities.replaceTags(message)

		/*if (chat != null) {
            if (TextUtils.isEmpty(title)) {
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ImportToChatNoTitle", R.string.ImportToChatNoTitle, chat.title)));
            } else {
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ImportToChat", R.string.ImportToChat, title, chat.title)));
            }
        } else {
            if (TextUtils.isEmpty(title)) {
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ImportToUserNoTitle", R.string.ImportToUserNoTitle, ContactsController.formatName(user.first_name, user.last_name))));
            } else {
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ImportToUser", R.string.ImportToUser, title, ContactsController.formatName(user.first_name, user.last_name))));
            }
        }*/

		builder.setPositiveButton(context.getString(R.string.Import)) { _, _ ->
			onProcessRunnable?.run()
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		val alertDialog = builder.create()

		fragment.showDialog(alertDialog)
	}

	fun createClearOrDeleteDialogAlert(fragment: BaseFragment?, clear: Boolean, chat: Chat?, user: User?, secret: Boolean, canDeleteHistory: Boolean, onProcessRunnable: BooleanCallback?) {
		createClearOrDeleteDialogAlert(fragment, clear, admin = chat != null && chat.creator, second = false, chat = chat, user = user, secret = secret, checkDeleteForAll = false, canDeleteHistory = canDeleteHistory, onProcessRunnable = onProcessRunnable)
	}

	fun createClearOrDeleteDialogAlert(fragment: BaseFragment?, clear: Boolean, chat: Chat?, user: User?, secret: Boolean, checkDeleteForAll: Boolean, canDeleteHistory: Boolean, onProcessRunnable: BooleanCallback?) {
		createClearOrDeleteDialogAlert(fragment, clear, chat != null && chat.creator, false, chat, user, secret, checkDeleteForAll, canDeleteHistory, onProcessRunnable)
	}

	fun createClearOrDeleteDialogAlert(fragment: BaseFragment?, clear: Boolean, admin: Boolean, second: Boolean, chat: Chat?, user: User?, secret: Boolean, checkDeleteForAll: Boolean, canDeleteHistory: Boolean, onProcessRunnable: BooleanCallback?) {
		val context = fragment?.getParentActivity()

		if (context == null || chat == null && user == null) {
			return
		}

		val account = fragment.currentAccount
		val builder = AlertDialog.Builder(context)
		val selfUserId = UserConfig.getInstance(account).getClientUserId()
		val cell = arrayOfNulls<CheckBoxCell>(1)

		val messageTextView = TextView(context)
		messageTextView.setTextColor(context.getColor(R.color.text))
		messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		messageTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		val clearingCache = !canDeleteHistory && ChatObject.isChannel(chat) && !chat.username.isNullOrEmpty()

		val frameLayout: FrameLayout = object : FrameLayout(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec)

				if (cell[0] != null) {
					setMeasuredDimension(measuredWidth, measuredHeight + cell[0]!!.measuredHeight + AndroidUtilities.dp(7f))
				}
			}
		}

		builder.setView(frameLayout)

		val avatarDrawable = AvatarDrawable()
		avatarDrawable.setTextSize(AndroidUtilities.dp(12f))

		val imageView = BackupImageView(context)
		imageView.setRoundRadius(AndroidUtilities.dp(20f))

		frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 22f, 5f, 22f, 0f))

		val textView = TextView(context)
		textView.setTextColor(context.getColor(R.color.text))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		textView.setTypeface(Theme.TYPEFACE_BOLD)
		textView.setLines(1)
		textView.setMaxLines(1)
		textView.setSingleLine(true)
		textView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL)
		textView.ellipsize = TextUtils.TruncateAt.END

		if (clear) {
			if (clearingCache) {
				textView.text = context.getString(R.string.ClearHistoryCache)
			}
			else {
				textView.text = context.getString(R.string.ClearHistory)
			}
		}
		else {
			if (admin) {
				if (ChatObject.isChannel(chat)) {
					if (chat.megagroup) {
						textView.text = context.getString(R.string.DeleteMegaMenu)
					}
					else {
						if (ChatObject.isOnlineCourse(chat)) {
							textView.text = context.getString(R.string.Warning)
						}
						else if (ChatObject.isSubscriptionChannel(chat)) {
							textView.text = context.getString(R.string.attention)
						}
						else {
							textView.text = context.getString(R.string.ChannelDeleteMenu)
						}
					}
				}
				else {
					textView.text = context.getString(R.string.DeleteMegaMenu)
				}
			}
			else {
				if (chat != null) {
					if (ChatObject.isChannel(chat)) {
						if (chat.megagroup) {
							textView.text = context.getString(R.string.LeaveMegaMenu)
						}
						else {
							if (ChatObject.isPaidChannel(chat)) {
								textView.text = context.getString(R.string.LeavePaidChannelMenu)
							}
							else {
								textView.text = context.getString(R.string.LeaveChannelMenu)
							}
						}
					}
					else {
						textView.text = context.getString(R.string.LeaveMegaMenu)
					}
				}
				else {
					textView.text = context.getString(R.string.DeleteChatUser)
				}
			}
		}

		frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 21 else 76).toFloat(), 11f, (if (LocaleController.isRTL) 76 else 21).toFloat(), 0f))
		frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24f, 57f, 24f, 9f))

		val canRevokeInbox = user != null && !user.bot && user.id != selfUserId && MessagesController.getInstance(account).canRevokePmInbox

		val revokeTimeLimit = if (user != null) {
			MessagesController.getInstance(account).revokeTimePmLimit
		}
		else {
			MessagesController.getInstance(account).revokeTimeLimit
		}

		val canDeleteInbox = !secret && user != null && canRevokeInbox && revokeTimeLimit == 0x7fffffff
		val deleteForAll = BooleanArray(1)
		var deleteChatForAll = false
		var lastMessageIsJoined = false
		val dialogMessage = if (user != null) MessagesController.getInstance(account).dialogMessage[user.id] else null

		if (dialogMessage?.messageOwner != null && (dialogMessage.messageOwner?.action is TL_messageActionUserJoined || dialogMessage.messageOwner?.action is TL_messageActionContactSignUp)) {
			lastMessageIsJoined = true
		}

		if (!second && (secret && !clear || canDeleteInbox) && !UserObject.isDeleted(user) && !lastMessageIsJoined || (checkDeleteForAll && !clear && chat != null && chat.creator).also { deleteChatForAll = it }) {
			if (chat?.creator == true) {
				if (ChatObject.isPaidChannel(chat)) {
					deleteForAll[0] = true
				}
				else {
					cell[0] = CheckBoxCell(context, 1)
					cell[0]?.background = Theme.getSelectorDrawable(false)

					if (deleteChatForAll) {
						if (ChatObject.isChannel(chat) && !chat.megagroup) {
							cell[0]?.setText(context.getString(R.string.DeleteChannelForAll), "", checked = false, divider = false)
						}
						else {
							cell[0]?.setText(context.getString(R.string.DeleteGroupForAll), "", checked = false, divider = false)
						}
					}
					else if (clear) {
						cell[0]?.setText(LocaleController.formatString("ClearHistoryOptionAlso", R.string.ClearHistoryOptionAlso, UserObject.getFirstName(user)), "", checked = false, divider = false)
					}
					else {
						cell[0]?.setText(LocaleController.formatString("DeleteMessagesOptionAlso", R.string.DeleteMessagesOptionAlso, UserObject.getFirstName(user)), "", checked = false, divider = false)
					}

					cell[0]?.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(16f) else AndroidUtilities.dp(8f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(8f) else AndroidUtilities.dp(16f), 0)

					frameLayout.addView(cell[0], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.BOTTOM or Gravity.LEFT, 0f, 0f, 0f, 0f))

					cell[0]?.setOnClickListener {
						val cell1 = it as CheckBoxCell
						deleteForAll[0] = !deleteForAll[0]
						cell1.setChecked(deleteForAll[0], true)
					}
				}
			}
		}

		if (user != null) {
			if (UserObject.isReplyUser(user)) {
				avatarDrawable.setSmallSize(true)
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_REPLIES

				imageView.setImage(null, null, avatarDrawable, user)
			}
			else if (user.id == selfUserId) {
				avatarDrawable.setSmallSize(true)
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED

				imageView.setImage(null, null, avatarDrawable, user)
			}
			else {
				avatarDrawable.setSmallSize(false)
				avatarDrawable.setInfo(user)

				imageView.setForUserOrChat(user, avatarDrawable)
			}
		}
		else {
			avatarDrawable.setInfo(chat)
			imageView.setForUserOrChat(chat, avatarDrawable)
		}

		if (second) {
			if (UserObject.isUserSelf(user)) {
				messageTextView.text = AndroidUtilities.replaceTags(context.getString(R.string.DeleteAllMessagesSavedAlert))
			}
			else {
				if (chat != null && ChatObject.isChannelAndNotMegaGroup(chat)) {
					messageTextView.text = AndroidUtilities.replaceTags(context.getString(R.string.DeleteAllMessagesChannelAlert))
				}
				else {
					messageTextView.text = AndroidUtilities.replaceTags(context.getString(R.string.DeleteAllMessagesAlert))
				}
			}
		}
		else {
			if (clear) {
				if (user != null) {
					if (secret) {
						messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithSecretUser", R.string.AreYouSureClearHistoryWithSecretUser, UserObject.getUserName(user)))
					}
					else {
						if (user.id == selfUserId) {
							messageTextView.text = AndroidUtilities.replaceTags(context.getString(R.string.AreYouSureClearHistorySavedMessages))
						}
						else {
							messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithUser", R.string.AreYouSureClearHistoryWithUser, UserObject.getUserName(user)))
						}
					}
				}
				else {
					if (!ChatObject.isChannel(chat) || (chat.megagroup && chat.username.isNullOrEmpty())) {
						messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithChat", R.string.AreYouSureClearHistoryWithChat, chat!!.title))
					}
					else if (chat.megagroup) {
						messageTextView.text = context.getString(R.string.AreYouSureClearHistoryGroup)
					}
					else {
						messageTextView.text = context.getString(R.string.AreYouSureClearHistoryChannel)
					}
				}
			}
			else {
				if (admin) {
					if (ChatObject.isChannel(chat)) {
						if (chat.megagroup) {
							messageTextView.text = context.getString(R.string.AreYouSureDeleteAndExit)
						}
						else {
							if (ChatObject.isOnlineCourse(chat)) {
								messageTextView.text = context.getString(R.string.delete_paid_channel_warning_message)
							}
							else if (ChatObject.isSubscriptionChannel(chat)) {
								messageTextView.text = context.getString(R.string.delete_paid_channel_warning_message)
							}
							else {
								messageTextView.text = context.getString(R.string.AreYouSureDeleteAndExitChannel)
							}
						}
					}
					else {
						messageTextView.text = context.getString(R.string.AreYouSureDeleteAndExit)
					}
				}
				else {
					if (user != null) {
						if (secret) {
							messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureDeleteThisChatWithSecretUser", R.string.AreYouSureDeleteThisChatWithSecretUser, UserObject.getUserName(user)))
						}
						else {
							if (user.id == selfUserId) {
								messageTextView.text = AndroidUtilities.replaceTags(context.getString(R.string.AreYouSureDeleteThisChatSavedMessages))
							}
							else {
								if (user.bot && !user.support) {
									messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureDeleteThisChatWithBot", R.string.AreYouSureDeleteThisChatWithBot, UserObject.getUserName(user)))
								}
								else {
									messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureDeleteThisChatWithUser", R.string.AreYouSureDeleteThisChatWithUser, UserObject.getUserName(user)))
								}
							}
						}
					}
					else if (ChatObject.isChannel(chat)) {
						if (chat.megagroup) {
							messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("MegaLeaveAlertWithName", R.string.MegaLeaveAlertWithName, chat.title))
						}
						else {
							if (ChatObject.isOnlineCourse(chat)) {
								messageTextView.text = context.getString(R.string.unsubscribe_course_description)
							}
							else if (ChatObject.isSubscriptionChannel(chat)) {
								messageTextView.text = context.getString(R.string.unsubscribe_subchannel_description)
							}
							else {
								messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("ChannelLeaveAlertWithName", R.string.ChannelLeaveAlertWithName, chat.title))
							}
						}
					}
					else {
						messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureDeleteAndExitName", R.string.AreYouSureDeleteAndExitName, chat!!.title))
					}
				}
			}
		}

		val actionText = if (second) {
			context.getString(R.string.DeleteAll)
		}
		else {
			if (clear) {
				if (clearingCache) {
					context.getString(R.string.ClearHistoryCache)
				}
				else {
					context.getString(R.string.ClearForMe)
				}
			}
			else {
				if (admin) {
					if (ChatObject.isChannel(chat)) {
						if (chat.megagroup) {
							context.getString(R.string.DeleteMega)
						}
						else {
							context.getString(R.string.ChannelDelete)
						}
					}
					else {
						context.getString(R.string.DeleteMega)
					}
				}
				else {
					if (ChatObject.isChannel(chat)) {
						if (chat.megagroup) {
							context.getString(R.string.LeaveMegaMenu)
						}
						else {
							context.getString(R.string.LeaveChannelMenu)
						}
					}
					else {
						context.getString(R.string.DeleteChatUser)
					}
				}
			}
		}

		builder.setPositiveButton(actionText) { _, _ ->
			if (!clearingCache && !second && !secret) {
				if (UserObject.isUserSelf(user)) {
					createClearOrDeleteDialogAlert(fragment, clear, admin, true, chat, user, false, checkDeleteForAll, canDeleteHistory, onProcessRunnable)
					return@setPositiveButton
				}
				else if (user != null && deleteForAll[0]) {
					MessagesStorage.getInstance(fragment.currentAccount).getMessagesCount(user.id) { count ->
						if (count >= 50) {
							createClearOrDeleteDialogAlert(fragment, clear, admin, true, chat, user, false, checkDeleteForAll, canDeleteHistory, onProcessRunnable)
						}
						else {
							onProcessRunnable?.run(deleteForAll[0])
						}
					}

					return@setPositiveButton
				}
			}

			onProcessRunnable?.run(second || deleteForAll[0])
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		val alertDialog = builder.create()

		fragment.showDialog(alertDialog)

		val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView

		button?.setTextColor(context.getColor(R.color.purple))
	}

	@JvmStatic
	fun createClearDaysDialogAlert(fragment: BaseFragment?, days: Int, user: User?, chat: Chat?, canDeleteHistory: Boolean, onProcessRunnable: BooleanCallback) {
		val context = fragment?.getParentActivity()

		if (context == null || user == null && chat == null) {
			return
		}

		val account = fragment.currentAccount
		val builder = AlertDialog.Builder(context)
		val selfUserId = UserConfig.getInstance(account).getClientUserId()
		val cell = arrayOfNulls<CheckBoxCell>(1)

		val messageTextView = TextView(context)
		messageTextView.setTextColor(context.getColor(R.color.text))
		messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		messageTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		val frameLayout: FrameLayout = object : FrameLayout(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec)

				if (cell[0] != null) {
					setMeasuredDimension(measuredWidth, measuredHeight + cell[0]!!.measuredHeight)
				}
			}
		}

		builder.setView(frameLayout)

		val textView = TextView(context)
		textView.setTextColor(context.getColor(R.color.text))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		textView.setTypeface(Theme.TYPEFACE_BOLD)
		textView.setLines(1)
		textView.setMaxLines(1)
		textView.setSingleLine(true)
		textView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL)
		textView.ellipsize = TextUtils.TruncateAt.END

		frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24f, 11f, 24f, 0f))
		frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24f, 48f, 24f, 18f))

		val dialogMessage = if (user != null) MessagesController.getInstance(account).dialogMessage[user.id] else null

		if (days == -1) {
			textView.text = LocaleController.formatString("ClearHistory", R.string.ClearHistory)

			if (user != null) {
				messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithUser", R.string.AreYouSureClearHistoryWithUser, UserObject.getUserName(user)))
			}
			else {
				if (canDeleteHistory) {
					if (ChatObject.isChannelAndNotMegaGroup(chat)) {
						messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithChannel", R.string.AreYouSureClearHistoryWithChannel, chat.title))
					}
					else {
						messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithChat", R.string.AreYouSureClearHistoryWithChat, chat?.title))
					}
				}
				else if (chat?.megagroup == true) {
					messageTextView.text = context.getString(R.string.AreYouSureClearHistoryGroup)
				}
				else {
					messageTextView.text = context.getString(R.string.AreYouSureClearHistoryChannel)
				}
			}
		}
		else {
			textView.text = LocaleController.formatPluralString("DeleteDays", days)
			messageTextView.text = context.getString(R.string.DeleteHistoryByDaysMessage)
		}

		val deleteForAll = booleanArrayOf(false)

		if (chat != null && canDeleteHistory && !TextUtils.isEmpty(chat.username)) {
			deleteForAll[0] = true
		}

		if (user != null && user.id != selfUserId || chat != null && canDeleteHistory && chat.username.isNullOrEmpty() && !ChatObject.isChannelAndNotMegaGroup(chat)) {
			if (dialogMessage != null && dialogMessage.dialogId != BuildConfig.AI_BOT_ID) {
				cell[0] = CheckBoxCell(context, 1)
				cell[0]?.background = Theme.getSelectorDrawable(false)

				if (chat != null) {
					cell[0]?.setText(context.getString(R.string.DeleteMessagesOptionAlsoChat), "", checked = false, divider = false)
				}
				else {
					cell[0]?.setText(LocaleController.formatString("DeleteMessagesOptionAlso", R.string.DeleteMessagesOptionAlso, UserObject.getFirstName(user)), "", checked = false, divider = false)
				}

				cell[0]?.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(16f) else AndroidUtilities.dp(8f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(8f) else AndroidUtilities.dp(16f), 0)

				frameLayout.addView(cell[0], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.BOTTOM or Gravity.LEFT, 0f, 0f, 0f, 0f))

				cell[0]?.setChecked(checked = false, animated = false)

				cell[0]?.setOnClickListener {
					val cell1 = it as CheckBoxCell
					deleteForAll[0] = !deleteForAll[0]
					cell1.setChecked(deleteForAll[0], true)
				}
			}
		}

		var deleteText = context.getString(R.string.Delete)

		if (chat != null && canDeleteHistory && !chat.username.isNullOrEmpty() && !ChatObject.isChannelAndNotMegaGroup(chat)) {
			deleteText = context.getString(R.string.ClearForAll)
		}

		builder.setPositiveButton(deleteText) { _, _ ->
			onProcessRunnable.run(deleteForAll[0])
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		val alertDialog = builder.create()

		fragment.showDialog(alertDialog)

		val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
		button?.setTextColor(context.getColor(R.color.purple))
	}

	fun createCallDialogAlert(fragment: BaseFragment?, user: User?, videoCall: Boolean) {
		val activity = fragment?.getParentActivity()

		if (activity == null || user == null || UserObject.isDeleted(user) || UserConfig.getInstance(fragment.currentAccount).getClientUserId() == user.id) {
			return
		}

		val frameLayout = FrameLayout(activity)
		val title: String
		val message: String

		if (videoCall) {
			title = activity.getString(R.string.VideoCallAlertTitle)
			message = LocaleController.formatString("VideoCallAlert", R.string.VideoCallAlert, UserObject.getUserName(user))
		}
		else {
			title = activity.getString(R.string.CallAlertTitle)
			message = LocaleController.formatString("CallAlert", R.string.CallAlert, UserObject.getUserName(user))
		}

		val messageTextView = TextView(activity)
		messageTextView.setTextColor(activity.getColor(R.color.text))
		messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		messageTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)
		messageTextView.text = AndroidUtilities.replaceTags(message)

		val avatarDrawable = AvatarDrawable()
		avatarDrawable.setTextSize(AndroidUtilities.dp(12f))
		avatarDrawable.setSmallSize(false)
		avatarDrawable.setInfo(user)

		val imageView = BackupImageView(activity)
		imageView.setRoundRadius(AndroidUtilities.dp(20f))
		imageView.setForUserOrChat(user, avatarDrawable)

		frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 22f, 5f, 22f, 0f))

		val textView = TextView(activity)
		textView.setTextColor(activity.getColor(R.color.text))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		textView.setTypeface(Theme.TYPEFACE_BOLD)
		textView.setLines(1)
		textView.setMaxLines(1)
		textView.setSingleLine(true)
		textView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL)
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.text = title

		frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 21 else 76).toFloat(), 11f, (if (LocaleController.isRTL) 76 else 21).toFloat(), 0f))
		frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24f, 57f, 24f, 9f))

		val dialog = AlertDialog.Builder(activity).setView(frameLayout).setPositiveButton(activity.getString(R.string.Call)) { _, _ ->
			val userFull = fragment.messagesController.getUserFull(user.id)
			VoIPHelper.startCall(user, videoCall, userFull?.video_calls_available == true, activity, userFull, fragment.accountInstance)
		}.setNegativeButton(activity.getString(R.string.Cancel), null).create()

		fragment.showDialog(dialog)
	}

	@JvmStatic
	fun createChangeBioAlert(currentBio: String?, peerId: Long, context: Context, currentAccount: Int) {
		val builder = AlertDialog.Builder(context)
		builder.setTitle(if (peerId > 0) context.getString(R.string.UserBio) else context.getString(R.string.DescriptionPlaceholder))
		builder.setMessage(if (peerId > 0) context.getString(R.string.VoipGroupBioEditAlertText) else context.getString(R.string.DescriptionInfo))

		val dialogView = FrameLayout(context)
		dialogView.setClipChildren(false)

		if (peerId < 0) {
			val chatFull = MessagesController.getInstance(currentAccount).getChatFull(-peerId)

			if (chatFull == null) {
				MessagesController.getInstance(currentAccount).loadFullChat(-peerId, generateClassGuid(), true)
			}
		}

		val checkTextView = NumberTextView(context)

		val editTextView = EditText(context)
		editTextView.setTextColor(context.getColor(R.color.text))
		editTextView.setHint(if (peerId > 0) context.getString(R.string.UserBio) else context.getString(R.string.DescriptionPlaceholder))
		editTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		editTextView.background = Theme.createEditTextDrawable(context, true)
		editTextView.setMaxLines(4)
		editTextView.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
		editTextView.setImeOptions(EditorInfo.IME_ACTION_DONE)

		val inputFilters = arrayOfNulls<InputFilter>(1)
		val maxSymbolsCount = if (peerId > 0) 70 else 255

		inputFilters[0] = object : CodepointsLengthInputFilter(maxSymbolsCount) {
			override fun filter(source: CharSequence?, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence {
				val result = super.filter(source, start, end, dest, dstart, dend)

				if (result != null && source != null && result.length != source.length) {
					context.vibrate()

					AndroidUtilities.shakeView(checkTextView, 2f, 0)
				}

				return result
			}
		}

		editTextView.setFilters(inputFilters)

		checkTextView.setCenterAlign(true)
		checkTextView.setTextSize(15)
		checkTextView.setTextColor(context.getColor(R.color.dark_gray))
		checkTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)

		dialogView.addView(checkTextView, LayoutHelper.createFrame(20, 20f, if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT, 0f, 14f, 21f, 0f))

		editTextView.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(24f) else 0, AndroidUtilities.dp(8f), if (LocaleController.isRTL) 0 else AndroidUtilities.dp(24f), AndroidUtilities.dp(8f))

		editTextView.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
				// unused
			}

			override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
				// unused
			}

			override fun afterTextChanged(s: Editable) {
				val count = maxSymbolsCount - Character.codePointCount(s, 0, s.length)

				if (count < 30) {
					checkTextView.setNumber(count, checkTextView.visibility == View.VISIBLE)

					AndroidUtilities.updateViewVisibilityAnimated(checkTextView, true)
				}
				else {
					AndroidUtilities.updateViewVisibilityAnimated(checkTextView, false)
				}
			}
		})

		AndroidUtilities.updateViewVisibilityAnimated(checkTextView, false, 0f, false)
		editTextView.setText(currentBio)
		editTextView.setSelection(editTextView.getText().toString().length)

		builder.setView(dialogView)

		val onDoneListener = DialogInterface.OnClickListener { dialogInterface, _ ->
			if (peerId > 0) {
				val userFull = MessagesController.getInstance(currentAccount).getUserFull(UserConfig.getInstance(currentAccount).getClientUserId())
				val newName = editTextView.getText().toString().replace("\n", " ").replace(" +".toRegex(), " ").trim()

				if (userFull != null) {
					var currentName = userFull.about

					if (currentName.isNullOrEmpty()) {
						currentName = ""
					}

					if (currentName == newName) {
						AndroidUtilities.hideKeyboard(editTextView)
						dialogInterface.dismiss()
						return@OnClickListener
					}

					userFull.about = newName

					NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, peerId, userFull)
				}

				val req = TL_account_updateProfile()
				req.about = newName
				req.flags = req.flags or 4

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_BIO_CHANGED, peerId)

				ConnectionsManager.getInstance(currentAccount).sendRequest(req, null, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
			else {
				val chatFull = MessagesController.getInstance(currentAccount).getChatFull(-peerId)
				val newAbout = editTextView.getText().toString()

				if (chatFull != null) {
					var currentName = chatFull.about

					if (currentName.isNullOrEmpty()) {
						currentName = ""
					}

					if (currentName == newAbout) {
						AndroidUtilities.hideKeyboard(editTextView)
						dialogInterface.dismiss()
						return@OnClickListener
					}

					chatFull.about = newAbout

					NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false)
				}

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_BIO_CHANGED, peerId)

				MessagesController.getInstance(currentAccount).updateChatAbout(-peerId, newAbout, chatFull)
			}

			dialogInterface.dismiss()
		}

		builder.setPositiveButton(context.getString(R.string.Save), onDoneListener)
		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		builder.setOnPreDismissListener {
			AndroidUtilities.hideKeyboard(editTextView)
		}

		dialogView.addView(editTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), 0, 23f, 12f, 23f, 21f))

		editTextView.requestFocus()

		AndroidUtilities.showKeyboard(editTextView)

		val dialog = builder.create()

		editTextView.setOnEditorActionListener { _, i, keyEvent ->
			if ((i == EditorInfo.IME_ACTION_DONE || peerId > 0 && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) && dialog.isShowing) {
				onDoneListener.onClick(dialog, 0)
				return@setOnEditorActionListener true
			}

			false
		}

		dialog.setBackgroundColor(context.getColor(R.color.background))
		dialog.show()
		dialog.setTextColor(context.getColor(R.color.text))
	}

	@JvmStatic
	fun createChangeNameAlert(peerId: Long, context: Context, currentAccount: Int) {
		val currentName: String?
		var currentLastName: String? = null

		if (DialogObject.isUserDialog(peerId)) {
			val user = MessagesController.getInstance(currentAccount).getUser(peerId)
			currentName = user?.first_name
			currentLastName = user?.last_name
		}
		else {
			val chat = MessagesController.getInstance(currentAccount).getChat(-peerId)
			currentName = chat?.title
		}

		val builder = AlertDialog.Builder(context)
		builder.setTitle(if (peerId > 0) context.getString(R.string.VoipEditName) else context.getString(R.string.VoipEditTitle))

		val dialogView = LinearLayout(context)
		dialogView.orientation = LinearLayout.VERTICAL

		val firstNameEditTextView = EditText(context)
		firstNameEditTextView.setTextColor(context.getColor(R.color.text))
		firstNameEditTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		firstNameEditTextView.setMaxLines(1)
		firstNameEditTextView.setLines(1)
		firstNameEditTextView.setSingleLine(true)
		firstNameEditTextView.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
		firstNameEditTextView.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
		firstNameEditTextView.setImeOptions(if (peerId > 0) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_DONE)
		firstNameEditTextView.setHint(if (peerId > 0) context.getString(R.string.FirstName) else context.getString(R.string.VoipEditTitleHint))
		firstNameEditTextView.background = Theme.createEditTextDrawable(context, true)
		firstNameEditTextView.setPadding(0, AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f))
		firstNameEditTextView.requestFocus()

		var lastNameEditTextView: EditText? = null

		if (peerId > 0) {
			lastNameEditTextView = EditText(context)
			lastNameEditTextView.setTextColor(context.getColor(R.color.text))
			lastNameEditTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			lastNameEditTextView.setMaxLines(1)
			lastNameEditTextView.setLines(1)
			lastNameEditTextView.setSingleLine(true)
			lastNameEditTextView.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
			lastNameEditTextView.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
			lastNameEditTextView.setImeOptions(EditorInfo.IME_ACTION_DONE)
			lastNameEditTextView.setHint(context.getString(R.string.LastName))
			lastNameEditTextView.background = Theme.createEditTextDrawable(context, true)
			lastNameEditTextView.setPadding(0, AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f))
		}

		AndroidUtilities.showKeyboard(firstNameEditTextView)

		dialogView.addView(firstNameEditTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 23, 12, 23, 21))

		if (lastNameEditTextView != null) {
			dialogView.addView(lastNameEditTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 23, 12, 23, 21))
		}

		firstNameEditTextView.setText(currentName)
		firstNameEditTextView.setSelection(firstNameEditTextView.getText().toString().length)

		if (lastNameEditTextView != null) {
			lastNameEditTextView.setText(currentLastName)
			lastNameEditTextView.setSelection(lastNameEditTextView.getText().toString().length)
		}

		builder.setView(dialogView)

		val finalLastNameEditTextView = lastNameEditTextView

		val onDoneListener = DialogInterface.OnClickListener { dialogInterface, _ ->
			if (firstNameEditTextView.getText() == null) {
				return@OnClickListener
			}

			if (peerId > 0) {
				val currentUser = MessagesController.getInstance(currentAccount).getUser(peerId)
				val newFirst = firstNameEditTextView.getText().toString()
				val newLast = finalLastNameEditTextView!!.getText().toString()
				var oldFirst = currentUser?.first_name
				var oldLast = currentUser?.last_name

				if (oldFirst == null) {
					oldFirst = ""
				}

				if (oldLast == null) {
					oldLast = ""
				}

				if (oldFirst == newFirst && oldLast == newLast) {
					dialogInterface.dismiss()
					return@OnClickListener
				}

				val req = TL_account_updateProfile()
				req.flags = 3
				req.first_name = newFirst

				currentUser?.first_name = req.first_name

				req.last_name = newLast

				currentUser?.last_name = req.last_name

				val user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId())

				if (user != null) {
					user.first_name = req.first_name
					user.last_name = req.last_name
				}

				UserConfig.getInstance(currentAccount).saveConfig(true)

				NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged)
				NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME)

				ConnectionsManager.getInstance(currentAccount).sendRequest(req)

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_NAME_CHANGED, peerId)
			}
			else {
				val chat = MessagesController.getInstance(currentAccount).getChat(-peerId)
				val newFirst = firstNameEditTextView.getText().toString()

				if (chat?.title != null && chat.title == newFirst) {
					dialogInterface.dismiss()
					return@OnClickListener
				}

				chat?.title = newFirst

				NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT_NAME)

				MessagesController.getInstance(currentAccount).changeChatTitle(-peerId, newFirst)

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_NAME_CHANGED, peerId)
			}

			dialogInterface.dismiss()
		}

		builder.setPositiveButton(context.getString(R.string.Save), onDoneListener)
		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		builder.setOnPreDismissListener {
			AndroidUtilities.hideKeyboard(firstNameEditTextView)
			AndroidUtilities.hideKeyboard(finalLastNameEditTextView)
		}

		val dialog = builder.create()
		dialog.setBackgroundColor(context.getColor(R.color.background))
		dialog.show()
		dialog.setTextColor(context.getColor(R.color.text))

		val actionListener = OnEditorActionListener { _, i, keyEvent ->
			if ((i == EditorInfo.IME_ACTION_DONE || keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) && dialog.isShowing) {
				onDoneListener.onClick(dialog, 0)
				return@OnEditorActionListener true
			}

			false
		}

		if (lastNameEditTextView != null) {
			lastNameEditTextView.setOnEditorActionListener(actionListener)
		}
		else {
			firstNameEditTextView.setOnEditorActionListener(actionListener)
		}
	}

	fun showChatWithAdmin(fragment: BaseFragment?, chatWithAdmin: String?, isChannel: Boolean, chatWithAdminDate: Int) {
		val context = fragment?.getParentActivity() ?: return

		val builder = BottomSheet.Builder(context)
		builder.setTitle(if (isChannel) context.getString(R.string.ChatWithAdminChannelTitle) else context.getString(R.string.ChatWithAdminGroupTitle), true)

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.VERTICAL

		val messageTextView = TextView(context)

		linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 24, 16, 24, 24))

		messageTextView.setTextColor(context.getColor(R.color.text))
		messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("ChatWithAdminMessage", R.string.ChatWithAdminMessage, chatWithAdmin, LocaleController.formatDateAudio(chatWithAdminDate.toLong(), false)))

		val buttonTextView = TextView(context)
		buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
		buttonTextView.setGravity(Gravity.CENTER)
		buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		buttonTextView.setTypeface(Theme.TYPEFACE_BOLD)
		buttonTextView.text = context.getString(R.string.IUnderstand)
		buttonTextView.setTextColor(context.getColor(R.color.brand))
		buttonTextView.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6f), context.getColor(R.color.brand), context.getColor(R.color.darker_brand))

		linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 24, 15, 16, 24))

		builder.setCustomView(linearLayout)

		val bottomSheet = builder.show()

		buttonTextView.setOnClickListener {
			bottomSheet.dismiss()
		}
	}

	fun createBlockDialogAlert(fragment: BaseFragment?, count: Int, reportSpam: Boolean, user: User?, onProcessRunnable: BlockDialogCallback) {
		val context = fragment?.getParentActivity()

		if (context == null || count == 1 && user == null) {
			return
		}

		val builder = AlertDialog.Builder(context)
		val cell = arrayOfNulls<CheckBoxCell>(2)

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.VERTICAL

		builder.setView(linearLayout)

		val actionText: String

		if (count == 1) {
			val name = ContactsController.formatName(user?.first_name, user?.last_name)
			builder.setTitle(LocaleController.formatString("BlockUserTitle", R.string.BlockUserTitle, name))
			actionText = context.getString(R.string.BlockUser)
			builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BlockUserMessage", R.string.BlockUserMessage, name)))
		}
		else {
			builder.setTitle(LocaleController.formatString("BlockUserTitle", R.string.BlockUserTitle, LocaleController.formatPluralString("UsersCountTitle", count)))
			actionText = context.getString(R.string.BlockUsers)
			builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BlockUsersMessage", R.string.BlockUsersMessage, LocaleController.formatPluralString("UsersCount", count))))
		}

		val checks = booleanArrayOf(true, true)

		for (a in cell.indices) {
			if (a == 0 && !reportSpam) {
				continue
			}

			cell[a] = CheckBoxCell(context, 1)
			cell[a]!!.background = Theme.getSelectorDrawable(false)

			if (a == 0) {
				cell[a]!!.setText(context.getString(R.string.ReportSpamTitle), "", checked = true, divider = false)
			}
			else {
				cell[a]!!.setText(if (count == 1) context.getString(R.string.DeleteThisChatBothSides) else context.getString(R.string.DeleteTheseChatsBothSides), "", checked = true, divider = false)
			}

			cell[a]!!.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(16f) else AndroidUtilities.dp(8f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(8f) else AndroidUtilities.dp(16f), 0)

			linearLayout.addView(cell[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48))

			cell[a]!!.setOnClickListener {
				val cell1 = it as? CheckBoxCell
				checks[a] = !checks[a]
				cell1?.setChecked(checks[a], true)
			}
		}

		builder.setPositiveButton(actionText) { _, _ ->
			onProcessRunnable.run(checks[0], checks[1])
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		val alertDialog = builder.create()

		fragment.showDialog(alertDialog)

		val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView

		button?.setTextColor(context.getColor(R.color.purple))
	}

	@JvmStatic
	fun createDatePickerDialog(context: Context?, minYear: Int, maxYear: Int, currentYearDiff: Int, selectedDay: Int, selectedMonth: Int, selectedYear: Int, title: String?, checkMinDate: Boolean, datePickerDelegate: DatePickerDelegate): AlertDialog.Builder? {
		if (context == null) {
			return null
		}

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.HORIZONTAL
		linearLayout.weightSum = 1.0f

		val monthPicker = NumberPicker(context)
		val dayPicker = NumberPicker(context)
		val yearPicker = NumberPicker(context)

		linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.3f))

		dayPicker.setOnScrollListener { _, scrollState ->
			if (checkMinDate && scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) {
				checkPickerDate(dayPicker, monthPicker, yearPicker)
			}
		}

		monthPicker.setMinValue(0)
		monthPicker.setMaxValue(11)

		linearLayout.addView(monthPicker, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.3f))

		monthPicker.setFormatter {
			val calendar = Calendar.getInstance()
			calendar[Calendar.DAY_OF_MONTH] = 1
			calendar[Calendar.MONTH] = it
			calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
		}

		monthPicker.setOnValueChangedListener { _, _, _ ->
			updateDayPicker(dayPicker, monthPicker, yearPicker)
		}

		monthPicker.setOnScrollListener { _, scrollState ->
			if (checkMinDate && scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) {
				checkPickerDate(dayPicker, monthPicker, yearPicker)
			}
		}

		val calendar = Calendar.getInstance()
		calendar.setTimeInMillis(System.currentTimeMillis())

		val currentYear = calendar[Calendar.YEAR]

		yearPicker.setMinValue(currentYear + minYear)
		yearPicker.setMaxValue(currentYear + maxYear)
		yearPicker.value = currentYear + currentYearDiff

		linearLayout.addView(yearPicker, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.4f))

		yearPicker.setOnValueChangedListener { _, _, _ ->
			updateDayPicker(dayPicker, monthPicker, yearPicker)
		}

		yearPicker.setOnScrollListener { _, scrollState ->
			if (checkMinDate && scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) {
				checkPickerDate(dayPicker, monthPicker, yearPicker)
			}
		}

		updateDayPicker(dayPicker, monthPicker, yearPicker)

		if (checkMinDate) {
			checkPickerDate(dayPicker, monthPicker, yearPicker)
		}

		if (selectedDay != -1) {
			dayPicker.value = selectedDay
			monthPicker.value = selectedMonth
			yearPicker.value = selectedYear
		}

		val builder = AlertDialog.Builder(context)
		builder.setTitle(title)
		builder.setView(linearLayout)

		builder.setPositiveButton(context.getString(R.string.Set)) { _, _ ->
			if (checkMinDate) {
				checkPickerDate(dayPicker, monthPicker, yearPicker)
			}

			datePickerDelegate.didSelectDate(yearPicker.value, monthPicker.value, dayPicker.value)
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		return builder
	}

	fun checkScheduleDate(button: TextView?, infoText: TextView?, type: Int, dayPicker: NumberPicker, hourPicker: NumberPicker, minutePicker: NumberPicker): Boolean {
		return checkScheduleDate(button, infoText, 0, type, dayPicker, hourPicker, minutePicker)
	}

	@JvmStatic
	fun checkScheduleDate(button: TextView?, infoText: TextView?, maxDate: Long, type: Int, dayPicker: NumberPicker, hourPicker: NumberPicker, minutePicker: NumberPicker): Boolean {
		@Suppress("NAME_SHADOWING") var maxDate = maxDate
		var day = dayPicker.value
		var hour = hourPicker.value
		var minute = minutePicker.value
		val calendar = Calendar.getInstance()
		val systemTime = System.currentTimeMillis()

		calendar.setTimeInMillis(systemTime)

		val currentYear = calendar[Calendar.YEAR]
		val currentDay = calendar[Calendar.DAY_OF_YEAR]

		if (maxDate > 0) {
			maxDate *= 1000

			calendar.setTimeInMillis(systemTime + maxDate)

			calendar[Calendar.HOUR_OF_DAY] = 23
			calendar[Calendar.MINUTE] = 59
			calendar[Calendar.SECOND] = 59

			maxDate = calendar.getTimeInMillis()
		}

		calendar.setTimeInMillis(System.currentTimeMillis() + day.toLong() * 24 * 3600 * 1000)
		calendar[Calendar.HOUR_OF_DAY] = hour
		calendar[Calendar.MINUTE] = minute

		val currentTime = calendar.getTimeInMillis()

		if (currentTime <= systemTime + 60000L) {
			calendar.setTimeInMillis(systemTime + 60000L)

			if (currentDay != calendar[Calendar.DAY_OF_YEAR]) {
				dayPicker.value = 1.also { day = it }
			}

			hourPicker.value = calendar[Calendar.HOUR_OF_DAY].also { hour = it }
			minutePicker.value = calendar[Calendar.MINUTE].also { minute = it }
		}
		else if (maxDate in 1..<currentTime) {
			calendar.setTimeInMillis(maxDate)

			dayPicker.value = 7.also { day = it }
			hourPicker.value = calendar[Calendar.HOUR_OF_DAY].also { hour = it }
			minutePicker.value = calendar[Calendar.MINUTE].also { minute = it }
		}

		val selectedYear = calendar[Calendar.YEAR]

		calendar.setTimeInMillis(System.currentTimeMillis() + day.toLong() * 24 * 3600 * 1000)

		calendar[Calendar.HOUR_OF_DAY] = hour
		calendar[Calendar.MINUTE] = minute

		val time = calendar.getTimeInMillis()

		if (button != null) {
			var num = if (day == 0) {
				0
			}
			else if (currentYear == selectedYear) {
				1
			}
			else {
				2
			}

			when (type) {
				1 -> num += 3
				2 -> num += 6
				3 -> num += 9
			}

			button.text = LocaleController.getInstance().formatterScheduleSend[num].format(time)
		}

		if (infoText != null) {
			val diff = ((time - systemTime) / 1000).toInt()

			val t = if (diff > 24 * 60 * 60) {
				LocaleController.formatPluralString("DaysSchedule", Math.round(diff / (24 * 60 * 60.0f)))
			}
			else if (diff >= 60 * 60) {
				LocaleController.formatPluralString("HoursSchedule", Math.round(diff / (60 * 60.0f)))
			}
			else if (diff >= 60) {
				LocaleController.formatPluralString("MinutesSchedule", Math.round(diff / 60.0f))
			}
			else {
				LocaleController.formatPluralString("SecondsSchedule", diff)
			}

			if (infoText.tag != null) {
				infoText.text = LocaleController.formatString("VoipChannelScheduleInfo", R.string.VoipChannelScheduleInfo, t)
			}
			else {
				infoText.text = LocaleController.formatString("VoipGroupScheduleInfo", R.string.VoipGroupScheduleInfo, t)
			}
		}

		return currentTime - systemTime > 60000L
	}

	@JvmStatic
	fun createScheduleDatePickerDialog(context: Context?, dialogId: Long, datePickerDelegate: ScheduleDatePickerDelegate): BottomSheet.Builder? {
		return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, null)
	}

	fun createScheduleDatePickerDialog(context: Context?, dialogId: Long, datePickerDelegate: ScheduleDatePickerDelegate, resourcesProvider: Theme.ResourcesProvider?): BottomSheet.Builder? {
		return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, null, resourcesProvider)
	}

	@JvmStatic
	fun createScheduleDatePickerDialog(context: Context?, dialogId: Long, datePickerDelegate: ScheduleDatePickerDelegate, datePickerColors: ScheduleDatePickerColors): BottomSheet.Builder? {
		return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, null, datePickerColors, null)
	}

	@JvmStatic
	fun createScheduleDatePickerDialog(context: Context?, dialogId: Long, datePickerDelegate: ScheduleDatePickerDelegate, cancelRunnable: Runnable?, resourcesProvider: Theme.ResourcesProvider?): BottomSheet.Builder? {
		return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, cancelRunnable, resourcesProvider)
	}

	fun createScheduleDatePickerDialog(context: Context?, dialogId: Long, currentDate: Long, datePickerDelegate: ScheduleDatePickerDelegate, cancelRunnable: Runnable?, resourcesProvider: Theme.ResourcesProvider?): BottomSheet.Builder? {
		return createScheduleDatePickerDialog(context, dialogId, currentDate, datePickerDelegate, cancelRunnable, ScheduleDatePickerColors(), resourcesProvider)
	}

	@JvmOverloads
	fun createScheduleDatePickerDialog(context: Context?, dialogId: Long, currentDate: Long, datePickerDelegate: ScheduleDatePickerDelegate, cancelRunnable: Runnable?, datePickerColors: ScheduleDatePickerColors = ScheduleDatePickerColors(), resourcesProvider: Theme.ResourcesProvider? = null): BottomSheet.Builder? {
		@Suppress("NAME_SHADOWING") var currentDate = currentDate

		if (context == null) {
			return null
		}

		val selfUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()

		val builder = BottomSheet.Builder(context, false)
		builder.setApplyBottomPadding(false)

		val dayPicker = NumberPicker(context, resourcesProvider)
		dayPicker.setTextColor(datePickerColors.textColor)
		dayPicker.setTextOffset(AndroidUtilities.dp(10f))
		dayPicker.setItemCount(5)

		val hourPicker: NumberPicker = object : NumberPicker(context, resourcesProvider) {
			override fun getContentDescription(value: Int): CharSequence {
				return LocaleController.formatPluralString("Hours", value)
			}
		}

		hourPicker.setItemCount(5)
		hourPicker.setTextColor(datePickerColors.textColor)
		hourPicker.setTextOffset(-AndroidUtilities.dp(10f))

		val minutePicker: NumberPicker = object : NumberPicker(context, resourcesProvider) {
			override fun getContentDescription(value: Int): CharSequence {
				return LocaleController.formatPluralString("Minutes", value)
			}
		}

		minutePicker.setItemCount(5)
		minutePicker.setTextColor(datePickerColors.textColor)
		minutePicker.setTextOffset(-AndroidUtilities.dp(34f))

		val container: LinearLayout = object : LinearLayout(context) {
			var ignoreLayout = false

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				ignoreLayout = true

				val count = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					3
				}
				else {
					5
				}

				dayPicker.setItemCount(count)
				hourPicker.setItemCount(count)
				minutePicker.setItemCount(count)

				dayPicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count
				hourPicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count
				minutePicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count

				ignoreLayout = false

				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}
		}

		container.orientation = LinearLayout.VERTICAL

		val titleLayout = FrameLayout(context)

		container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP, 22, 0, 0, 4))

		val titleView = TextView(context)

		if (dialogId == selfUserId) {
			titleView.text = context.getString(R.string.SetReminder)
		}
		else {
			titleView.text = context.getString(R.string.ScheduleMessage)
		}

		titleView.setTextColor(datePickerColors.textColor)
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		titleView.setTypeface(Theme.TYPEFACE_BOLD)

		titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 12f, 0f, 0f))

		titleView.setOnTouchListener { _, _ -> true }

		if (DialogObject.isUserDialog(dialogId) && dialogId != selfUserId) {
			val user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId)

			if (user != null && !user.bot && user.status != null && (user.status?.expires ?: 0) > 0) {
				var name = UserObject.getFirstName(user)

				if (name.length > 10) {
					name = name.substring(0, 10) + "…"
				}

				val optionsButton = ActionBarMenuItem(context, null, 0, datePickerColors.iconColor, false)
				optionsButton.setLongClickEnabled(false)
				optionsButton.setSubMenuOpenSide(2)
				optionsButton.setIcon(R.drawable.overflow_menu)
				optionsButton.background = Theme.createSelectorDrawable(datePickerColors.iconSelectorColor, 1)

				titleLayout.addView(optionsButton, LayoutHelper.createFrame(40, 40f, Gravity.TOP or Gravity.RIGHT, 0f, 8f, 5f, 0f))

				optionsButton.addSubItem(1, LocaleController.formatString("ScheduleWhenOnline", R.string.ScheduleWhenOnline, name))

				optionsButton.setOnClickListener {
					optionsButton.toggleSubMenu()
					optionsButton.setPopupItemsColor(datePickerColors.subMenuTextColor, false)
					optionsButton.setupPopupRadialSelectors(datePickerColors.subMenuSelectorColor)
					optionsButton.redrawPopup(datePickerColors.subMenuBackgroundColor)
				}

				optionsButton.setDelegate { id ->
					if (id == 1) {
						datePickerDelegate.didSelectDate(true, 0x7ffffffe)
						builder.dismissRunnable.run()
					}
				}

				optionsButton.setContentDescription(context.getString(R.string.AccDescrMoreOptions))
			}
		}

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.HORIZONTAL
		linearLayout.weightSum = 1.0f

		container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12))

		val currentTime = System.currentTimeMillis()

		val calendar = Calendar.getInstance()
		calendar.setTimeInMillis(currentTime)

		val currentYear = calendar[Calendar.YEAR]

		val buttonTextView: TextView = object : TextView(context) {
			override fun getAccessibilityClassName(): CharSequence {
				return Button::class.java.getName()
			}
		}

		linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f))

		dayPicker.setMinValue(0)
		dayPicker.setMaxValue(365)
		dayPicker.setWrapSelectorWheel(false)

		dayPicker.setFormatter { value ->
			if (value == 0) {
				return@setFormatter context.getString(R.string.MessageScheduleToday)
			}
			else {
				val date = currentTime + value.toLong() * 86400000L

				calendar.setTimeInMillis(date)

				val year = calendar[Calendar.YEAR]

				if (year == currentYear) {
					return@setFormatter LocaleController.getInstance().formatterScheduleDay.format(date)
				}
				else {
					return@setFormatter LocaleController.getInstance().formatterScheduleYear.format(date)
				}
			}
		}

		val onValueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
			runCatching {
				container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
			}

			checkScheduleDate(buttonTextView, null, if (selfUserId == dialogId) 1 else 0, dayPicker, hourPicker, minutePicker)
		}

		dayPicker.setOnValueChangedListener(onValueChangeListener)

		hourPicker.setMinValue(0)
		hourPicker.setMaxValue(23)

		linearLayout.addView(hourPicker, LayoutHelper.createLinear(0, 54 * 5, 0.2f))

		hourPicker.setFormatter { String.format("%02d", it) }
		hourPicker.setOnValueChangedListener(onValueChangeListener)

		minutePicker.setMinValue(0)
		minutePicker.setMaxValue(59)
		minutePicker.value = 0
		minutePicker.setFormatter { String.format("%02d", it) }

		linearLayout.addView(minutePicker, LayoutHelper.createLinear(0, 54 * 5, 0.3f))

		minutePicker.setOnValueChangedListener(onValueChangeListener)

		if (currentDate > 0 && currentDate != 0x7FFFFFFEL) {
			currentDate *= 1000

			calendar.setTimeInMillis(System.currentTimeMillis())

			calendar[Calendar.MINUTE] = 0
			calendar[Calendar.SECOND] = 0
			calendar[Calendar.MILLISECOND] = 0
			calendar[Calendar.HOUR_OF_DAY] = 0

			val days = ((currentDate - calendar.getTimeInMillis()) / (24 * 60 * 60 * 1000)).toInt()

			calendar.setTimeInMillis(currentDate)

			if (days >= 0) {
				minutePicker.value = calendar[Calendar.MINUTE]
				hourPicker.value = calendar[Calendar.HOUR_OF_DAY]
				dayPicker.value = days
			}
		}

		val canceled = booleanArrayOf(true)
		checkScheduleDate(buttonTextView, null, if (selfUserId == dialogId) 1 else 0, dayPicker, hourPicker, minutePicker)

		buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
		buttonTextView.setGravity(Gravity.CENTER)
		buttonTextView.setTextColor(datePickerColors.buttonTextColor)
		buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		buttonTextView.setTypeface(Theme.TYPEFACE_BOLD)
		buttonTextView.background = Theme.AdaptiveRipple.filledRect(datePickerColors.buttonBackgroundColor, 4f)

		container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.BOTTOM, 16, 15, 16, 16))

		buttonTextView.setOnClickListener {
			canceled[0] = false

			val setSeconds = checkScheduleDate(null, null, if (selfUserId == dialogId) 1 else 0, dayPicker, hourPicker, minutePicker)

			calendar.setTimeInMillis(System.currentTimeMillis() + dayPicker.value.toLong() * 24 * 3600 * 1000)

			calendar[Calendar.HOUR_OF_DAY] = hourPicker.value
			calendar[Calendar.MINUTE] = minutePicker.value

			if (setSeconds) {
				calendar[Calendar.SECOND] = 0
			}

			datePickerDelegate.didSelectDate(true, (calendar.getTimeInMillis() / 1000).toInt())

			builder.dismissRunnable.run()
		}

		builder.setCustomView(container)

		val bottomSheet = builder.show()

		bottomSheet.setOnDismissListener {
			if (canceled[0]) {
				cancelRunnable?.run()
			}
		}

		bottomSheet.setBackgroundColor(datePickerColors.backgroundColor)
		bottomSheet.fixNavigationBar(datePickerColors.backgroundColor)
		return builder
	}

	fun createDatePickerDialog(context: Context?, currentDate: Long, datePickerDelegate: ScheduleDatePickerDelegate): BottomSheet.Builder? {
		if (context == null) {
			return null
		}

		@Suppress("NAME_SHADOWING") var currentDate = currentDate
		val datePickerColors = ScheduleDatePickerColors()

		val builder = BottomSheet.Builder(context, false)
		builder.setApplyBottomPadding(false)

		val dayPicker = NumberPicker(context)
		dayPicker.setTextColor(datePickerColors.textColor)
		dayPicker.setTextOffset(AndroidUtilities.dp(10f))
		dayPicker.setItemCount(5)

		val hourPicker = object : NumberPicker(context) {
			override fun getContentDescription(value: Int): CharSequence {
				return LocaleController.formatPluralString("Hours", value)
			}
		}

		hourPicker.setItemCount(5)
		hourPicker.setTextColor(datePickerColors.textColor)
		hourPicker.setTextOffset(-AndroidUtilities.dp(10f))

		val minutePicker = object : NumberPicker(context) {
			override fun getContentDescription(value: Int): CharSequence {
				return LocaleController.formatPluralString("Minutes", value)
			}
		}

		minutePicker.setItemCount(5)
		minutePicker.setTextColor(datePickerColors.textColor)
		minutePicker.setTextOffset(-AndroidUtilities.dp(34f))

		val container = object : LinearLayout(context) {
			var ignoreLayout = false

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				ignoreLayout = true

				val count = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					3
				}
				else {
					5
				}

				dayPicker.setItemCount(count)
				hourPicker.setItemCount(count)
				minutePicker.setItemCount(count)

				dayPicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count
				hourPicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count
				minutePicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count

				ignoreLayout = false

				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}
		}

		container.orientation = LinearLayout.VERTICAL

		val titleLayout = FrameLayout(context)

		container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP, 22, 0, 0, 4))

		val titleView = TextView(context)
		titleView.text = context.getString(R.string.ExpireAfter)
		titleView.setTextColor(datePickerColors.textColor)
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		titleView.setTypeface(Theme.TYPEFACE_BOLD)
		titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 12f, 0f, 0f))
		titleView.setOnTouchListener { _, _ -> true }

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.HORIZONTAL
		linearLayout.weightSum = 1.0f

		container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12))

		val currentTime = System.currentTimeMillis()

		val calendar = Calendar.getInstance()
		calendar.setTimeInMillis(currentTime)

		val currentYear = calendar[Calendar.YEAR]

		val buttonTextView: TextView = object : TextView(context) {
			override fun getAccessibilityClassName(): CharSequence {
				return Button::class.java.getName()
			}
		}

		linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f))

		dayPicker.setMinValue(0)
		dayPicker.setMaxValue(365)
		dayPicker.setWrapSelectorWheel(false)

		dayPicker.setFormatter {
			if (it == 0) {
				return@setFormatter context.getString(R.string.MessageScheduleToday)
			}
			else {
				val date = currentTime + it.toLong() * 86400000L

				calendar.setTimeInMillis(date)

				val year = calendar[Calendar.YEAR]

				if (year == currentYear) {
					return@setFormatter LocaleController.getInstance().formatterScheduleDay.format(date)
				}
				else {
					return@setFormatter LocaleController.getInstance().formatterScheduleYear.format(date)
				}
			}
		}

		val onValueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
			runCatching {
				container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
			}

			checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker)
		}

		dayPicker.setOnValueChangedListener(onValueChangeListener)

		hourPicker.setMinValue(0)
		hourPicker.setMaxValue(23)

		linearLayout.addView(hourPicker, LayoutHelper.createLinear(0, 54 * 5, 0.2f))

		hourPicker.setFormatter { String.format("%02d", it) }
		hourPicker.setOnValueChangedListener(onValueChangeListener)

		minutePicker.setMinValue(0)
		minutePicker.setMaxValue(59)
		minutePicker.value = 0
		minutePicker.setFormatter { String.format("%02d", it) }

		linearLayout.addView(minutePicker, LayoutHelper.createLinear(0, 54 * 5, 0.3f))

		minutePicker.setOnValueChangedListener(onValueChangeListener)

		if (currentDate > 0 && currentDate != 0x7FFFFFFEL) {
			currentDate *= 1000

			calendar.setTimeInMillis(System.currentTimeMillis())

			calendar[Calendar.MINUTE] = 0
			calendar[Calendar.SECOND] = 0
			calendar[Calendar.MILLISECOND] = 0
			calendar[Calendar.HOUR_OF_DAY] = 0

			val days = ((currentDate - calendar.getTimeInMillis()) / (24 * 60 * 60 * 1000)).toInt()

			calendar.setTimeInMillis(currentDate)

			if (days >= 0) {
				minutePicker.value = calendar[Calendar.MINUTE]
				hourPicker.value = calendar[Calendar.HOUR_OF_DAY]
				dayPicker.value = days
			}
		}

		checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker)

		buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
		buttonTextView.setGravity(Gravity.CENTER)
		buttonTextView.setTextColor(datePickerColors.buttonTextColor)
		buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		buttonTextView.setTypeface(Theme.TYPEFACE_BOLD)
		buttonTextView.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4f), datePickerColors.buttonBackgroundColor, datePickerColors.buttonBackgroundPressedColor)
		buttonTextView.text = context.getString(R.string.SetTimeLimit)

		container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.BOTTOM, 16, 15, 16, 16))

		buttonTextView.setOnClickListener {
			val setSeconds = checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker)

			calendar.setTimeInMillis(System.currentTimeMillis() + dayPicker.value.toLong() * 24 * 3600 * 1000)

			calendar[Calendar.HOUR_OF_DAY] = hourPicker.value
			calendar[Calendar.MINUTE] = minutePicker.value

			if (setSeconds) {
				calendar[Calendar.SECOND] = 0
			}

			datePickerDelegate.didSelectDate(true, (calendar.getTimeInMillis() / 1000).toInt())

			builder.dismissRunnable.run()
		}

		builder.setCustomView(container)

		val bottomSheet = builder.show()
		bottomSheet.setBackgroundColor(datePickerColors.backgroundColor)
		bottomSheet.fixNavigationBar(datePickerColors.backgroundColor)

		return builder
	}

	@JvmStatic
	fun createStatusUntilDatePickerDialog(context: Context?, currentDate: Long, delegate: StatusUntilDatePickerDelegate): BottomSheet.Builder? {
		if (context == null) {
			return null
		}

		@Suppress("NAME_SHADOWING") var currentDate = currentDate
		val datePickerColors = ScheduleDatePickerColors()

		val builder = BottomSheet.Builder(context, false)
		builder.setApplyBottomPadding(false)

		val dayPicker = NumberPicker(context)
		dayPicker.setTextColor(datePickerColors.textColor)
		dayPicker.setTextOffset(AndroidUtilities.dp(10f))
		dayPicker.setItemCount(5)

		val hourPicker = object : NumberPicker(context) {
			override fun getContentDescription(value: Int): CharSequence {
				return LocaleController.formatPluralString("Hours", value)
			}
		}

		hourPicker.setItemCount(5)
		hourPicker.setTextColor(datePickerColors.textColor)
		hourPicker.setTextOffset(-AndroidUtilities.dp(10f))

		val minutePicker = object : NumberPicker(context) {
			override fun getContentDescription(value: Int): CharSequence {
				return LocaleController.formatPluralString("Minutes", value)
			}
		}

		minutePicker.setItemCount(5)
		minutePicker.setTextColor(datePickerColors.textColor)
		minutePicker.setTextOffset(-AndroidUtilities.dp(34f))

		val container = object : LinearLayout(context) {
			var ignoreLayout = false

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				ignoreLayout = true

				val count = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					3
				}
				else {
					5
				}

				dayPicker.setItemCount(count)
				hourPicker.setItemCount(count)
				minutePicker.setItemCount(count)

				dayPicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count
				hourPicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count
				minutePicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count

				ignoreLayout = false

				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}
		}

		container.orientation = LinearLayout.VERTICAL

		val titleLayout = FrameLayout(context)

		container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP, 22, 0, 0, 4))

		val titleView = TextView(context)
		titleView.text = context.getString(R.string.SetEmojiStatusUntilTitle)
		titleView.setTextColor(datePickerColors.textColor)
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		titleView.setTypeface(Theme.TYPEFACE_BOLD)

		titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 12f, 0f, 0f))

		titleView.setOnTouchListener { _, _ -> true }

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.HORIZONTAL
		linearLayout.weightSum = 1.0f

		container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12))

		val currentTime = System.currentTimeMillis()

		val calendar = Calendar.getInstance()
		calendar.setTimeInMillis(currentTime)

		val currentYear = calendar[Calendar.YEAR]
		val currentDayYear = calendar[Calendar.DAY_OF_YEAR]

		val buttonTextView = object : TextView(context) {
			override fun getAccessibilityClassName(): CharSequence {
				return Button::class.java.getName()
			}
		}

		linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f))

		dayPicker.setMinValue(0)
		dayPicker.setMaxValue(365)
		dayPicker.setWrapSelectorWheel(false)

		dayPicker.setFormatter {
			if (it == 0) {
				return@setFormatter context.getString(R.string.MessageScheduleToday)
			}
			else {
				val date = currentTime + it.toLong() * 86400000L

				calendar.setTimeInMillis(date)

				val year = calendar[Calendar.YEAR]
				val yearDay = calendar[Calendar.DAY_OF_YEAR]

				if (year == currentYear && yearDay < currentDayYear + 7) {
					return@setFormatter LocaleController.getInstance().formatterWeek.format(date) + ", " + LocaleController.getInstance().formatterScheduleDay.format(date)
				}
				else if (year == currentYear) {
					return@setFormatter LocaleController.getInstance().formatterScheduleDay.format(date)
				}
				else {
					return@setFormatter LocaleController.getInstance().formatterScheduleYear.format(date)
				}
			}
		}

		val onValueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
			runCatching {
				container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
			}

			checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker)
		}

		dayPicker.setOnValueChangedListener(onValueChangeListener)

		hourPicker.setMinValue(0)
		hourPicker.setMaxValue(23)

		linearLayout.addView(hourPicker, LayoutHelper.createLinear(0, 54 * 5, 0.2f))

		hourPicker.setFormatter { String.format("%02d", it) }
		hourPicker.setOnValueChangedListener(onValueChangeListener)

		minutePicker.setMinValue(0)
		minutePicker.setMaxValue(59)
		minutePicker.value = 0
		minutePicker.setFormatter { String.format("%02d", it) }

		linearLayout.addView(minutePicker, LayoutHelper.createLinear(0, 54 * 5, 0.3f))

		minutePicker.setOnValueChangedListener(onValueChangeListener)

		if (currentDate > 0 && currentDate != 0x7FFFFFFEL) {
			currentDate *= 1000

			calendar.setTimeInMillis(System.currentTimeMillis())

			calendar[Calendar.MINUTE] = 0
			calendar[Calendar.SECOND] = 0
			calendar[Calendar.MILLISECOND] = 0
			calendar[Calendar.HOUR_OF_DAY] = 0

			val days = ((currentDate - calendar.getTimeInMillis()) / (24 * 60 * 60 * 1000)).toInt()

			calendar.setTimeInMillis(currentDate)

			if (days >= 0) {
				minutePicker.value = calendar[Calendar.MINUTE]
				hourPicker.value = calendar[Calendar.HOUR_OF_DAY]
				dayPicker.value = days
			}
		}

		checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker)

		buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
		buttonTextView.setGravity(Gravity.CENTER)
		buttonTextView.setTextColor(datePickerColors.buttonTextColor)
		buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		buttonTextView.setTypeface(Theme.TYPEFACE_BOLD)
		buttonTextView.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4f), datePickerColors.buttonBackgroundColor, datePickerColors.buttonBackgroundPressedColor)
		buttonTextView.text = context.getString(R.string.SetEmojiStatusUntilButton)

		container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.BOTTOM, 16, 15, 16, 16))

		buttonTextView.setOnClickListener {
			val setSeconds = checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker)

			calendar.setTimeInMillis(System.currentTimeMillis() + dayPicker.value.toLong() * 24 * 3600 * 1000)

			calendar[Calendar.HOUR_OF_DAY] = hourPicker.value
			calendar[Calendar.MINUTE] = minutePicker.value

			if (setSeconds) {
				calendar[Calendar.SECOND] = 0
			}

			delegate.didSelectDate((calendar.getTimeInMillis() / 1000).toInt())

			builder.dismissRunnable.run()
		}

		builder.setCustomView(container)

		val bottomSheet = builder.show()
		bottomSheet.setBackgroundColor(datePickerColors.backgroundColor)
		bottomSheet.fixNavigationBar(datePickerColors.backgroundColor)

		return builder
	}

	@JvmStatic
	fun createAutoDeleteDatePickerDialog(context: Context?, resourcesProvider: Theme.ResourcesProvider?, datePickerDelegate: ScheduleDatePickerDelegate): BottomSheet.Builder? {
		if (context == null) {
			return null
		}

		val datePickerColors = ScheduleDatePickerColors()

		val builder = BottomSheet.Builder(context, false)
		builder.setApplyBottomPadding(false)

		val values = intArrayOf(0, 60 * 24, 2 * 60 * 24, 3 * 60 * 24, 4 * 60 * 24, 5 * 60 * 24, 6 * 60 * 24, 7 * 60 * 24, 2 * 7 * 60 * 24, 3 * 7 * 60 * 24, 31 * 60 * 24, 2 * 31 * 60 * 24, 3 * 31 * 60 * 24, 4 * 31 * 60 * 24, 5 * 31 * 60 * 24, 6 * 31 * 60 * 24, 365 * 60 * 24)

		val numberPicker = object : NumberPicker(context, resourcesProvider) {
			override fun getContentDescription(index: Int): CharSequence {
				return if (values[index] == 0) {
					context.getString(R.string.AutoDeleteNever)
				}
				else if (values[index] < 7 * 60 * 24) {
					LocaleController.formatPluralString("Days", values[index] / (60 * 24))
				}
				else if (values[index] < 31 * 60 * 24) {
					LocaleController.formatPluralString("Weeks", values[index] / (60 * 24))
				}
				else if (values[index] < 365 * 60 * 24) {
					LocaleController.formatPluralString("Months", values[index] / (7 * 60 * 24))
				}
				else {
					LocaleController.formatPluralString("Years", values[index] * 5 / 31 * 60 * 24)
				}
			}
		}

		numberPicker.setMinValue(0)
		numberPicker.setMaxValue(values.size - 1)
		numberPicker.setTextColor(datePickerColors.textColor)
		numberPicker.value = 0

		numberPicker.setFormatter { index ->
			if (values[index] == 0) {
				return@setFormatter context.getString(R.string.AutoDeleteNever)
			}
			else if (values[index] < 7 * 60 * 24) {
				return@setFormatter LocaleController.formatPluralString("Days", values[index] / (60 * 24))
			}
			else if (values[index] < 31 * 60 * 24) {
				return@setFormatter LocaleController.formatPluralString("Weeks", values[index] / (7 * 60 * 24))
			}
			else if (values[index] < 365 * 60 * 24) {
				return@setFormatter LocaleController.formatPluralString("Months", values[index] / (31 * 60 * 24))
			}
			else {
				return@setFormatter LocaleController.formatPluralString("Years", values[index] / (365 * 60 * 24))
			}
		}

		val container = object : LinearLayout(context) {
			var ignoreLayout = false

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				ignoreLayout = true

				val count = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					3
				}
				else {
					5
				}

				numberPicker.setItemCount(count)
				numberPicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count

				ignoreLayout = false

				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}
		}

		container.orientation = LinearLayout.VERTICAL

		val titleLayout = FrameLayout(context)

		container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP, 22, 0, 0, 4))

		val titleView = TextView(context)
		titleView.text = context.getString(R.string.AutoDeleteAfteTitle)
		titleView.setTextColor(datePickerColors.textColor)
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		titleView.setTypeface(Theme.TYPEFACE_BOLD)

		titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 12f, 0f, 0f))

		titleView.setOnTouchListener { _, _ -> true }

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.HORIZONTAL
		linearLayout.weightSum = 1.0f

		container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12))

		linearLayout.addView(numberPicker, LayoutHelper.createLinear(0, 54 * 5, 1f))

		val button: TextView = object : MaterialButton(context, null, R.attr.brandedButtonStyle) {
			override fun getAccessibilityClassName(): CharSequence {
				return Button::class.java.getName()
			}
		}

		button.setGravity(Gravity.CENTER)
		button.text = context.getString(R.string.AutoDeleteConfirm)

		container.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT or Gravity.BOTTOM, 16, 15, 16, 16))

		val onValueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
			runCatching {
				container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
			}
		}

		numberPicker.setOnValueChangedListener(onValueChangeListener)

		button.setOnClickListener {
			val time = values[numberPicker.value]
			datePickerDelegate.didSelectDate(true, time)
			builder.dismissRunnable.run()
		}

		builder.setCustomView(container)

		val bottomSheet = builder.show()
		bottomSheet.setBackgroundColor(datePickerColors.backgroundColor)
		bottomSheet.fixNavigationBar(datePickerColors.backgroundColor)

		return builder
	}

	@JvmOverloads
	@JvmStatic
	fun createSoundFrequencyPickerDialog(context: Context?, notifyMaxCount: Int, notifyDelay: Int, delegate: SoundFrequencyDelegate, resourcesProvider: Theme.ResourcesProvider? = null): BottomSheet.Builder? {
		if (context == null) {
			return null
		}

		val datePickerColors = ScheduleDatePickerColors()

		val builder = BottomSheet.Builder(context, false)
		builder.setApplyBottomPadding(false)

		val times = object : NumberPicker(context, resourcesProvider) {
			override fun getContentDescription(index: Int): CharSequence {
				return LocaleController.formatPluralString("Times", index + 1)
			}
		}

		times.setMinValue(0)
		times.setMaxValue(10)
		times.setTextColor(datePickerColors.textColor)
		times.value = notifyMaxCount - 1
		times.setWrapSelectorWheel(false)
		times.setFormatter { LocaleController.formatPluralString("Times", it + 1) }

		val minutes = object : NumberPicker(context, resourcesProvider) {
			override fun getContentDescription(index: Int): CharSequence {
				return LocaleController.formatPluralString("Times", index + 1)
			}
		}

		minutes.setMinValue(0)
		minutes.setMaxValue(10)
		minutes.setTextColor(datePickerColors.textColor)
		minutes.value = notifyDelay / 60 - 1
		minutes.setWrapSelectorWheel(false)
		minutes.setFormatter { LocaleController.formatPluralString("Minutes", it + 1) }

		val divider = NumberPicker(context, resourcesProvider)
		divider.setMinValue(0)
		divider.setMaxValue(0)
		divider.setTextColor(datePickerColors.textColor)
		divider.value = 0
		divider.setWrapSelectorWheel(false)
		divider.setFormatter { context.getString(R.string.NotificationsFrequencyDivider) }

		val container = object : LinearLayout(context) {
			var ignoreLayout = false

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				ignoreLayout = true

				val count = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					3
				}
				else {
					5
				}

				times.setItemCount(count)
				times.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count

				minutes.setItemCount(count)
				minutes.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count

				divider.setItemCount(count)
				divider.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count

				ignoreLayout = false

				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}
		}

		container.orientation = LinearLayout.VERTICAL

		val titleLayout = FrameLayout(context)

		container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP, 22, 0, 0, 4))

		val titleView = TextView(context)
		titleView.text = context.getString(R.string.NotfificationsFrequencyTitle)
		titleView.setTextColor(datePickerColors.textColor)
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		titleView.setTypeface(Theme.TYPEFACE_BOLD)

		titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 12f, 0f, 0f))

		titleView.setOnTouchListener { _, _ -> true }

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.HORIZONTAL
		linearLayout.weightSum = 1.0f

		container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12))

		val buttonTextView = object : TextView(context) {
			override fun getAccessibilityClassName(): CharSequence {
				return Button::class.java.getName()
			}
		}

		linearLayout.addView(times, LayoutHelper.createLinear(0, 54 * 5, 0.4f))
		linearLayout.addView(divider, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.2f, Gravity.CENTER_VERTICAL))
		linearLayout.addView(minutes, LayoutHelper.createLinear(0, 54 * 5, 0.4f))

		buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
		buttonTextView.setGravity(Gravity.CENTER)
		buttonTextView.setTextColor(datePickerColors.buttonTextColor)
		buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		buttonTextView.setTypeface(Theme.TYPEFACE_BOLD)
		buttonTextView.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4f), datePickerColors.buttonBackgroundColor, datePickerColors.buttonBackgroundPressedColor)
		buttonTextView.text = context.getString(R.string.AutoDeleteConfirm)

		container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.BOTTOM, 16, 15, 16, 16))

		val onValueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
			runCatching {
				container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
			}
		}

		times.setOnValueChangedListener(onValueChangeListener)
		minutes.setOnValueChangedListener(onValueChangeListener)

		buttonTextView.setOnClickListener {
			val time = times.value + 1
			val minute = (minutes.value + 1) * 60
			delegate.didSelectValues(time, minute)
			builder.dismissRunnable.run()
		}

		builder.setCustomView(container)

		val bottomSheet = builder.show()
		bottomSheet.setBackgroundColor(datePickerColors.backgroundColor)
		bottomSheet.fixNavigationBar(datePickerColors.backgroundColor)

		return builder
	}

	fun createMuteForPickerDialog(context: Context?, datePickerDelegate: ScheduleDatePickerDelegate): BottomSheet.Builder? {
		if (context == null) {
			return null
		}

		val datePickerColors = ScheduleDatePickerColors()

		val builder = BottomSheet.Builder(context, false)
		builder.setApplyBottomPadding(false)

		val values = intArrayOf(30, 60, 60 * 2, 60 * 3, 60 * 8, 60 * 24, 2 * 60 * 24, 3 * 60 * 24, 4 * 60 * 24, 5 * 60 * 24, 6 * 60 * 24, 7 * 60 * 24, 2 * 7 * 60 * 24, 3 * 7 * 60 * 24, 31 * 60 * 24, 2 * 31 * 60 * 24, 3 * 31 * 60 * 24, 4 * 31 * 60 * 24, 5 * 31 * 60 * 24, 6 * 31 * 60 * 24, 365 * 60 * 24)

		val numberPicker = object : NumberPicker(context) {
			override fun getContentDescription(index: Int): CharSequence {
				return if (values[index] == 0) {
					context.getString(R.string.MuteNever)
				}
				else if (values[index] < 60) {
					LocaleController.formatPluralString("Minutes", values[index])
				}
				else if (values[index] < 60 * 24) {
					LocaleController.formatPluralString("Hours", values[index] / 60)
				}
				else if (values[index] < 7 * 60 * 24) {
					LocaleController.formatPluralString("Days", values[index] / (60 * 24))
				}
				else if (values[index] < 31 * 60 * 24) {
					LocaleController.formatPluralString("Weeks", values[index] / (7 * 60 * 24))
				}
				else if (values[index] < 365 * 60 * 24) {
					LocaleController.formatPluralString("Months", values[index] / (31 * 60 * 24))
				}
				else {
					LocaleController.formatPluralString("Years", values[index] / (365 * 60 * 24))
				}
			}
		}

		numberPicker.setMinValue(0)
		numberPicker.setMaxValue(values.size - 1)
		numberPicker.setTextColor(datePickerColors.textColor)
		numberPicker.value = 0

		numberPicker.setFormatter { index ->
			if (values[index] == 0) {
				return@setFormatter context.getString(R.string.MuteNever)
			}
			else if (values[index] < 60) {
				return@setFormatter LocaleController.formatPluralString("Minutes", values[index])
			}
			else if (values[index] < 60 * 24) {
				return@setFormatter LocaleController.formatPluralString("Hours", values[index] / 60)
			}
			else if (values[index] < 7 * 60 * 24) {
				return@setFormatter LocaleController.formatPluralString("Days", values[index] / (60 * 24))
			}
			else if (values[index] < 31 * 60 * 24) {
				return@setFormatter LocaleController.formatPluralString("Weeks", values[index] / (7 * 60 * 24))
			}
			else if (values[index] < 365 * 60 * 24) {
				return@setFormatter LocaleController.formatPluralString("Months", values[index] / (31 * 60 * 24))
			}
			else {
				return@setFormatter LocaleController.formatPluralString("Years", values[index] / (365 * 60 * 24))
			}
		}

		val container = object : LinearLayout(context) {
			var ignoreLayout = false

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				ignoreLayout = true

				val count = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					3
				}
				else {
					5
				}

				numberPicker.setItemCount(count)
				numberPicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count

				ignoreLayout = false

				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}
		}

		container.orientation = LinearLayout.VERTICAL

		val titleLayout = FrameLayout(context)

		container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP, 22, 0, 0, 4))

		val titleView = TextView(context)
		titleView.text = context.getString(R.string.MuteForAlert)
		titleView.setTextColor(datePickerColors.textColor)
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		titleView.setTypeface(Theme.TYPEFACE_BOLD)

		titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 12f, 0f, 0f))

		titleView.setOnTouchListener { _, _ -> true }

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.HORIZONTAL
		linearLayout.weightSum = 1.0f

		container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12))

		val buttonTextView = object : TextView(context) {
			override fun getAccessibilityClassName(): CharSequence {
				return Button::class.java.getName()
			}
		}

		linearLayout.addView(numberPicker, LayoutHelper.createLinear(0, 54 * 5, 1f))

		val onValueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
			runCatching {
				container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
			}
		}

		numberPicker.setOnValueChangedListener(onValueChangeListener)

		buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
		buttonTextView.setGravity(Gravity.CENTER)
		buttonTextView.setTextColor(datePickerColors.buttonTextColor)
		buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		buttonTextView.setTypeface(Theme.TYPEFACE_BOLD)
		buttonTextView.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4f), datePickerColors.buttonBackgroundColor, datePickerColors.buttonBackgroundPressedColor)
		buttonTextView.text = context.getString(R.string.AutoDeleteConfirm)

		container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.BOTTOM, 16, 15, 16, 16))

		buttonTextView.setOnClickListener {
			val time = values[numberPicker.value] * 60
			datePickerDelegate.didSelectDate(true, time)
			builder.dismissRunnable.run()
		}

		builder.setCustomView(container)

		val bottomSheet = builder.show()
		bottomSheet.setBackgroundColor(datePickerColors.backgroundColor)
		bottomSheet.fixNavigationBar(datePickerColors.backgroundColor)

		return builder
	}

	private fun checkMuteForButton(dayPicker: NumberPicker, hourPicker: NumberPicker, buttonTextView: TextView, animated: Boolean) {
		val stringBuilder = StringBuilder()
		val context = ApplicationLoader.applicationContext

		if (dayPicker.value != 0) {
			stringBuilder.append(dayPicker.value).append(context.getString(R.string.SecretChatTimerDays))
		}

		if (hourPicker.value != 0) {
			if (stringBuilder.isNotEmpty()) {
				stringBuilder.append(" ")
			}

			stringBuilder.append(hourPicker.value).append(context.getString(R.string.SecretChatTimerHours))
		}

		if (stringBuilder.isEmpty()) {
			buttonTextView.text = context.getString(R.string.ChooseTimeForMute)

			if (buttonTextView.isEnabled) {
				buttonTextView.setEnabled(false)

				if (animated) {
					buttonTextView.animate().alpha(0.5f)
				}
				else {
					buttonTextView.setAlpha(0.5f)
				}
			}
		}
		else {
			buttonTextView.text = LocaleController.formatString("MuteForButton", R.string.MuteForButton, stringBuilder.toString())

			if (!buttonTextView.isEnabled) {
				buttonTextView.setEnabled(true)

				if (animated) {
					buttonTextView.animate().alpha(1f)
				}
				else {
					buttonTextView.setAlpha(1f)
				}
			}
		}
	}

	private fun checkCalendarDate(minDate: Long, dayPicker: NumberPicker, monthPicker: NumberPicker, yearPicker: NumberPicker) {
		var day = dayPicker.value
		var month = monthPicker.value
		var year = yearPicker.value

		val calendar = Calendar.getInstance()
		calendar.setTimeInMillis(minDate)

		val minYear = calendar[Calendar.YEAR]
		val minMonth = calendar[Calendar.MONTH]
		val minDay = calendar[Calendar.DAY_OF_MONTH]

		calendar.setTimeInMillis(System.currentTimeMillis())

		val maxYear = calendar[Calendar.YEAR]
		val maxMonth = calendar[Calendar.MONTH]
		val maxDay = calendar[Calendar.DAY_OF_MONTH]

		if (year > maxYear) {
			yearPicker.value = maxYear.also { year = it }
		}

		if (year == maxYear) {
			if (month > maxMonth) {
				monthPicker.value = maxMonth.also { month = it }
			}

			if (month == maxMonth) {
				if (day > maxDay) {
					dayPicker.value = maxDay.also { day = it }
				}
			}
		}

		if (year < minYear) {
			yearPicker.value = minYear.also { year = it }
		}

		if (year == minYear) {
			if (month < minMonth) {
				monthPicker.value = minMonth.also { month = it }
			}

			if (month == minMonth) {
				if (day < minDay) {
					dayPicker.value = minDay.also { day = it }
				}
			}
		}

		calendar[Calendar.YEAR] = year
		calendar[Calendar.MONTH] = month

		val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

		dayPicker.setMaxValue(daysInMonth)

		if (day > daysInMonth) {
			day = daysInMonth
			dayPicker.value = day
		}
	}

	@JvmStatic
	fun createCalendarPickerDialog(context: Context?, minDate: Long, callback: MessagesStorage.IntCallback, resourcesProvider: Theme.ResourcesProvider?): BottomSheet.Builder? {
		if (context == null) {
			return null
		}

		val builder = BottomSheet.Builder(context, false)
		builder.setApplyBottomPadding(false)

		val dayPicker = NumberPicker(context, resourcesProvider)
		dayPicker.setTextOffset(AndroidUtilities.dp(10f))
		dayPicker.setItemCount(5)

		val monthPicker = NumberPicker(context, resourcesProvider)
		monthPicker.setItemCount(5)
		monthPicker.setTextOffset(-AndroidUtilities.dp(10f))

		val yearPicker = NumberPicker(context, resourcesProvider)
		yearPicker.setItemCount(5)
		yearPicker.setTextOffset(-AndroidUtilities.dp(24f))

		val container: LinearLayout = object : LinearLayout(context) {
			var ignoreLayout = false

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				ignoreLayout = true

				val count = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					3
				}
				else {
					5
				}

				dayPicker.setItemCount(count)
				monthPicker.setItemCount(count)
				yearPicker.setItemCount(count)

				dayPicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count
				monthPicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count
				yearPicker.layoutParams.height = AndroidUtilities.dp(NumberPicker.DEFAULT_SIZE_PER_COUNT.toFloat()) * count

				ignoreLayout = false

				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}
		}

		container.orientation = LinearLayout.VERTICAL

		val titleLayout = FrameLayout(context)

		container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP, 22, 0, 0, 4))

		val titleView = TextView(context)
		titleView.text = context.getString(R.string.ChooseDate)
		titleView.setTextColor(context.getColor(R.color.text))
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		titleView.setTypeface(Theme.TYPEFACE_BOLD)

		titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 12f, 0f, 0f))

		titleView.setOnTouchListener { _, _ -> true }

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.HORIZONTAL
		linearLayout.weightSum = 1.0f

		container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12))

		val buttonTextView: TextView = object : TextView(context) {
			override fun getAccessibilityClassName(): CharSequence {
				return Button::class.java.getName()
			}
		}

		linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.25f))

		dayPicker.setMinValue(1)
		dayPicker.setMaxValue(31)
		dayPicker.setWrapSelectorWheel(false)
		dayPicker.setFormatter { "" + it }

		val onValueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
			runCatching {
				container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
			}

			checkCalendarDate(minDate, dayPicker, monthPicker, yearPicker)
		}

		dayPicker.setOnValueChangedListener(onValueChangeListener)

		monthPicker.setMinValue(0)
		monthPicker.setMaxValue(11)
		monthPicker.setWrapSelectorWheel(false)

		linearLayout.addView(monthPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f))

		monthPicker.setFormatter { value ->
			when (value) {
				0 -> {
					return@setFormatter context.getString(R.string.January)
				}

				1 -> {
					return@setFormatter context.getString(R.string.February)
				}

				2 -> {
					return@setFormatter context.getString(R.string.March)
				}

				3 -> {
					return@setFormatter context.getString(R.string.April)
				}

				4 -> {
					return@setFormatter context.getString(R.string.May)
				}

				5 -> {
					return@setFormatter context.getString(R.string.June)
				}

				6 -> {
					return@setFormatter context.getString(R.string.July)
				}

				7 -> {
					return@setFormatter context.getString(R.string.August)
				}

				8 -> {
					return@setFormatter context.getString(R.string.September)
				}

				9 -> {
					return@setFormatter context.getString(R.string.October)
				}

				10 -> {
					return@setFormatter context.getString(R.string.November)
				}

				11 -> {
					return@setFormatter context.getString(R.string.December)
				}

				else -> {
					return@setFormatter context.getString(R.string.December)
				}
			}
		}

		monthPicker.setOnValueChangedListener(onValueChangeListener)

		val calendar = Calendar.getInstance()
		calendar.setTimeInMillis(minDate)

		val minYear = calendar[Calendar.YEAR]

		calendar.setTimeInMillis(System.currentTimeMillis())

		val maxYear = calendar[Calendar.YEAR]

		yearPicker.setMinValue(minYear)
		yearPicker.setMaxValue(maxYear)
		yearPicker.setWrapSelectorWheel(false)
		yearPicker.setFormatter { String.format("%02d", it) }

		linearLayout.addView(yearPicker, LayoutHelper.createLinear(0, 54 * 5, 0.25f))

		yearPicker.setOnValueChangedListener(onValueChangeListener)

		dayPicker.value = 31
		monthPicker.value = 12
		yearPicker.value = maxYear

		checkCalendarDate(minDate, dayPicker, monthPicker, yearPicker)

		buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
		buttonTextView.setGravity(Gravity.CENTER)
		buttonTextView.setTextColor(context.getColor(R.color.white))
		buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		buttonTextView.setTypeface(Theme.TYPEFACE_BOLD)
		buttonTextView.text = context.getString(R.string.JumpToDate)
		buttonTextView.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4f), context.getColor(R.color.brand), context.getColor(R.color.darker_brand))

		container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.BOTTOM, 16, 15, 16, 16))

		buttonTextView.setOnClickListener {
			checkCalendarDate(minDate, dayPicker, monthPicker, yearPicker)

			calendar[Calendar.YEAR] = yearPicker.value
			calendar[Calendar.MONTH] = monthPicker.value
			calendar[Calendar.DAY_OF_MONTH] = dayPicker.value
			calendar[Calendar.MINUTE] = 0
			calendar[Calendar.HOUR_OF_DAY] = 0
			calendar[Calendar.SECOND] = 0

			callback.run((calendar.getTimeInMillis() / 1000).toInt())

			builder.dismissRunnable.run()
		}

		builder.setCustomView(container)

		return builder
	}

	fun createMuteAlert(fragment: BaseFragment?, dialogId: Long): BottomSheet? {
		val context = fragment?.getParentActivity() ?: return null

		val builder = BottomSheet.Builder(context, false)
		builder.setTitle(context.getString(R.string.Notifications), true)

		val items = arrayOf<CharSequence>(LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 1)), LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 8)), LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Days", 2)), context.getString(R.string.MuteDisable))

		builder.setItems(items) { _, i ->
			val setting = when (i) {
				0 -> NotificationsController.SETTING_MUTE_HOUR
				1 -> NotificationsController.SETTING_MUTE_8_HOURS
				2 -> NotificationsController.SETTING_MUTE_2_DAYS
				else -> NotificationsController.SETTING_MUTE_FOREVER
			}

			NotificationsController.getInstance(UserConfig.selectedAccount).setDialogNotificationsSettings(dialogId, setting)

			if (BulletinFactory.canShowBulletin(fragment)) {
				BulletinFactory.createMuteBulletin(fragment, setting, 0).show()
			}
		}

		return builder.create()
	}

	fun sendReport(peer: InputPeer?, type: Int, message: String?, messages: List<Int>) {
		val request = TL_messages_report()
		request.peer = peer
		request.id.addAll(messages)
		request.message = message

		when (type) {
			REPORT_TYPE_SPAM -> request.reason = TL_inputReportReasonSpam()
			REPORT_TYPE_FAKE_ACCOUNT -> request.reason = TL_inputReportReasonFake()
			REPORT_TYPE_VIOLENCE -> request.reason = TL_inputReportReasonViolence()
			REPORT_TYPE_CHILD_ABUSE -> request.reason = TL_inputReportReasonChildAbuse()
			REPORT_TYPE_PORNOGRAPHY -> request.reason = TL_inputReportReasonPornography()
			REPORT_TYPE_ILLEGAL_DRUGS -> request.reason = TL_inputReportReasonIllegalDrugs()
			REPORT_TYPE_PERSONAL_DETAILS -> request.reason = TL_inputReportReasonPersonalDetails()
			REPORT_TYPE_OTHER -> request.reason = TL_inputReportReasonOther()
		}

		ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request)
	}

	fun createReportAlert(context: Context?, dialogId: Long, messageId: Int, parentFragment: BaseFragment?, hideDim: Runnable?) {
		if (context == null || parentFragment == null) {
			return
		}

		val builder = BottomSheet.Builder(context, true)
		builder.setDimBehind(hideDim == null)

		builder.setOnPreDismissListener {
			hideDim?.run()
		}

		builder.setTitle(context.getString(R.string.ReportChat), true)

		val items: Array<CharSequence>
		val icons: IntArray
		val types: IntArray

		if (messageId != 0) {
			items = arrayOf(context.getString(R.string.ReportChatSpam), context.getString(R.string.ReportChatViolence), context.getString(R.string.ReportChatChild), context.getString(R.string.ReportChatIllegalDrugs), context.getString(R.string.ReportChatPersonalDetails), context.getString(R.string.ReportChatPornography), context.getString(R.string.ReportChatOther))
			icons = intArrayOf(R.drawable.spam, R.drawable.msg_report_violence, R.drawable.child_abuse, R.drawable.msg_report_drugs, R.drawable.msg_report_personal, R.drawable.msg_report_xxx, R.drawable.msg_report_other)
			types = intArrayOf(REPORT_TYPE_SPAM, REPORT_TYPE_VIOLENCE, REPORT_TYPE_CHILD_ABUSE, REPORT_TYPE_ILLEGAL_DRUGS, REPORT_TYPE_PERSONAL_DETAILS, REPORT_TYPE_PORNOGRAPHY, REPORT_TYPE_OTHER)
		}
		else {
			items = arrayOf(context.getString(R.string.ReportChatSpam), context.getString(R.string.ReportChatFakeAccount), context.getString(R.string.ReportChatViolence), context.getString(R.string.ReportChatChild), context.getString(R.string.ReportChatIllegalDrugs), context.getString(R.string.ReportChatPersonalDetails), context.getString(R.string.ReportChatPornography), context.getString(R.string.ReportChatOther))
			icons = intArrayOf(R.drawable.spam, R.drawable.msg_report_fake, R.drawable.msg_report_violence, R.drawable.child_abuse, R.drawable.msg_report_drugs, R.drawable.msg_report_personal, R.drawable.msg_report_xxx, R.drawable.msg_report_other)
			types = intArrayOf(REPORT_TYPE_SPAM, REPORT_TYPE_FAKE_ACCOUNT, REPORT_TYPE_VIOLENCE, REPORT_TYPE_CHILD_ABUSE, REPORT_TYPE_ILLEGAL_DRUGS, REPORT_TYPE_PERSONAL_DETAILS, REPORT_TYPE_PORNOGRAPHY, REPORT_TYPE_OTHER)
		}

		builder.setItems(items, icons) { _, i ->
			val type = types[i]

			if (messageId == 0 && (type == REPORT_TYPE_SPAM || type == REPORT_TYPE_VIOLENCE || type == REPORT_TYPE_CHILD_ABUSE || type == REPORT_TYPE_PORNOGRAPHY || type == REPORT_TYPE_ILLEGAL_DRUGS || type == REPORT_TYPE_PERSONAL_DETAILS) && parentFragment is ChatActivity) {
				parentFragment.openReportChat(type)
				return@setItems
			}
			else if ((messageId == 0 && (type == REPORT_TYPE_OTHER || type == REPORT_TYPE_FAKE_ACCOUNT)) || (messageId != 0 && type == REPORT_TYPE_OTHER)) {
				if (parentFragment is ChatActivity) {
					AndroidUtilities.requestAdjustNothing(parentFragment.getParentActivity(), parentFragment.getClassGuid())
				}

				parentFragment.showDialog(object : ReportAlert(context, type) {
					override fun dismissInternal() {
						super.dismissInternal()

						if (parentFragment is ChatActivity) {
							parentFragment.checkAdjustResize()
						}
					}

					override fun onSend(type: Int, message: String?) {
						val ids = mutableListOf<Int>()

						if (messageId != 0) {
							ids.add(messageId)
						}

						val peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(dialogId)

						sendReport(peer, type, message, ids)

						parentFragment.fragmentView?.postDelayed({
							if (parentFragment is ChatActivity) {
								parentFragment.undoView?.showWithAction(0, UndoView.ACTION_REPORT_SENT, null)
							}
							else {
								BulletinFactory.of(parentFragment).createReportSent().show()
							}
						}, 300L)
					}
				})

				return@setItems
			}

			val req: TLObject
			val peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(dialogId)

			if (messageId != 0) {
				val request = TL_messages_report()
				request.peer = peer
				request.id.add(messageId)
				request.message = ""

				when (type) {
					REPORT_TYPE_SPAM -> request.reason = TL_inputReportReasonSpam()
					REPORT_TYPE_VIOLENCE -> request.reason = TL_inputReportReasonViolence()
					REPORT_TYPE_CHILD_ABUSE -> request.reason = TL_inputReportReasonChildAbuse()
					REPORT_TYPE_PORNOGRAPHY -> request.reason = TL_inputReportReasonPornography()
					REPORT_TYPE_ILLEGAL_DRUGS -> request.reason = TL_inputReportReasonIllegalDrugs()
					REPORT_TYPE_PERSONAL_DETAILS -> request.reason = TL_inputReportReasonPersonalDetails()
				}

				req = request
			}
			else {
				val request = TL_account_reportPeer()
				request.peer = peer
				request.message = ""

				when (type) {
					REPORT_TYPE_SPAM -> request.reason = TL_inputReportReasonSpam()
					REPORT_TYPE_FAKE_ACCOUNT -> request.reason = TL_inputReportReasonFake()
					REPORT_TYPE_VIOLENCE -> request.reason = TL_inputReportReasonViolence()
					REPORT_TYPE_CHILD_ABUSE -> request.reason = TL_inputReportReasonChildAbuse()
					REPORT_TYPE_PORNOGRAPHY -> request.reason = TL_inputReportReasonPornography()
					REPORT_TYPE_ILLEGAL_DRUGS -> request.reason = TL_inputReportReasonIllegalDrugs()
					REPORT_TYPE_PERSONAL_DETAILS -> request.reason = TL_inputReportReasonPersonalDetails()
				}

				req = request
			}

			ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req)

			if (parentFragment is ChatActivity) {
				parentFragment.undoView?.showWithAction(0, UndoView.ACTION_REPORT_SENT, null)
			}
			else {
				BulletinFactory.of(parentFragment).createReportSent().show()
			}
		}

		val sheet = builder.create()

		parentFragment.showDialog(sheet)
	}

	private fun getFloodWaitString(error: String): String {
		val time = Utilities.parseInt(error)

		val timeString = if (time < 60) {
			LocaleController.formatPluralString("Seconds", time)
		}
		else {
			LocaleController.formatPluralString("Minutes", time / 60)
		}

		return LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString)
	}

	private fun showFloodWaitAlert(error: String?, fragment: BaseFragment?) {
		val context = fragment?.getParentActivity() ?: return

		if (error == null || !error.startsWith("FLOOD_WAIT")) {
			return
		}

		val time = Utilities.parseInt(error)

		val timeString = if (time < 60) {
			LocaleController.formatPluralString("Seconds", time)
		}
		else {
			LocaleController.formatPluralString("Minutes", time / 60)
		}

		val builder = AlertDialog.Builder(context)
		builder.setTitle(context.getString(R.string.AppName))
		builder.setMessage(LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString))
		builder.setPositiveButton(context.getString(R.string.OK), null)

		fragment.showDialog(builder.create(), true, null)
	}

	fun showSendMediaAlert(result: Int, fragment: BaseFragment?) {
		if (result == 0) {
			return
		}

		val context = fragment?.getParentActivity() ?: return

		val builder = AlertDialog.Builder(context)
		builder.setTitle(context.getString(R.string.UnableForward))

		when (result) {
			1 -> builder.setMessage(context.getString(R.string.ErrorSendRestrictedStickers))
			2 -> builder.setMessage(context.getString(R.string.ErrorSendRestrictedMedia))
			3 -> builder.setMessage(context.getString(R.string.ErrorSendRestrictedPolls))
			4 -> builder.setMessage(context.getString(R.string.ErrorSendRestrictedStickersAll))
			5 -> builder.setMessage(context.getString(R.string.ErrorSendRestrictedMediaAll))
			6 -> builder.setMessage(context.getString(R.string.ErrorSendRestrictedPollsAll))
			7 -> builder.setMessage(context.getString(R.string.ErrorSendRestrictedPrivacyVoiceMessages))
			8 -> builder.setMessage(context.getString(R.string.ErrorSendRestrictedPrivacyVideoMessages))
		}

		builder.setPositiveButton(context.getString(R.string.OK), null)

		fragment.showDialog(builder.create(), true, null)
	}

	fun showAddUserAlert(error: String?, fragment: BaseFragment?, isChannel: Boolean, request: TLObject?) {
		if (error == null) {
			return
		}

		val context = fragment?.getParentActivity() ?: return

		val builder = AlertDialog.Builder(context)
		builder.setTitle(context.getString(R.string.AppName))

		when (error) {
			"PEER_FLOOD" -> {
				builder.setMessage(context.getString(R.string.NobodyLikesSpam2))

				builder.setNegativeButton(context.getString(R.string.MoreInfo)) { _, _ ->
					MessagesController.getInstance(fragment.currentAccount).openByUserName("spambot", fragment, 1)
				}
			}

			"USER_BLOCKED", "USER_BOT", "USER_ID_INVALID" -> {
				if (isChannel) {
					builder.setMessage(context.getString(R.string.ChannelUserCantAdd))
				}
				else {
					builder.setMessage(context.getString(R.string.GroupUserCantAdd))
				}
			}

			"USERS_TOO_MUCH" -> {
				if (isChannel) {
					builder.setMessage(context.getString(R.string.ChannelUserAddLimit))
				}
				else {
					builder.setMessage(context.getString(R.string.GroupUserAddLimit))
				}
			}

			"USER_NOT_MUTUAL_CONTACT" -> {
				if (isChannel) {
					builder.setMessage(context.getString(R.string.ChannelUserLeftError))
				}
				else {
					builder.setMessage(context.getString(R.string.GroupUserLeftError))
				}
			}

			"ADMINS_TOO_MUCH" -> {
				if (isChannel) {
					builder.setMessage(context.getString(R.string.ChannelUserCantAdmin))
				}
				else {
					builder.setMessage(context.getString(R.string.GroupUserCantAdmin))
				}
			}

			"BOTS_TOO_MUCH" -> {
				if (isChannel) {
					builder.setMessage(context.getString(R.string.ChannelUserCantBot))
				}
				else {
					builder.setMessage(context.getString(R.string.GroupUserCantBot))
				}
			}

			"USER_PRIVACY_RESTRICTED" -> {
				if (isChannel) {
					builder.setMessage(context.getString(R.string.InviteToChannelError))
				}
				else {
					builder.setMessage(context.getString(R.string.InviteToGroupError))
				}
			}

			"USERS_TOO_FEW" -> {
				builder.setMessage(context.getString(R.string.CreateGroupError))
			}

			"USER_RESTRICTED" -> {
				builder.setMessage(context.getString(R.string.UserRestricted))
			}

			"YOU_BLOCKED_USER" -> {
				builder.setMessage(context.getString(R.string.YouBlockedUser))
			}

			"CHAT_ADMIN_BAN_REQUIRED", "USER_KICKED" -> {
				if (request is TL_channels_inviteToChannel) {
					builder.setMessage(context.getString(R.string.AddUserErrorBlacklisted))
				}
				else {
					builder.setMessage(context.getString(R.string.AddAdminErrorBlacklisted))
				}
			}

			"CHAT_ADMIN_INVITE_REQUIRED" -> {
				builder.setMessage(context.getString(R.string.AddAdminErrorNotAMember))
			}

			"USER_ADMIN_INVALID" -> {
				builder.setMessage(context.getString(R.string.AddBannedErrorAdmin))
			}

			"CHANNELS_ADMIN_PUBLIC_TOO_MUCH" -> {
				builder.setMessage(context.getString(R.string.PublicChannelsTooMuch))
			}

			"CHANNELS_ADMIN_LOCATED_TOO_MUCH" -> {
				builder.setMessage(context.getString(R.string.LocatedChannelsTooMuch))
			}

			"CHANNELS_TOO_MUCH" -> {
				builder.setTitle(context.getString(R.string.ChannelTooMuchTitle))

				if (request is TL_channels_createChannel) {
					builder.setMessage(context.getString(R.string.ChannelTooMuch))
				}
				else {
					builder.setMessage(context.getString(R.string.ChannelTooMuchJoin))
				}
			}

			"USER_CHANNELS_TOO_MUCH" -> {
				builder.setTitle(context.getString(R.string.ChannelTooMuchTitle))
				builder.setMessage(context.getString(R.string.UserChannelTooMuchJoin))
			}

			"USER_ALREADY_PARTICIPANT" -> {
				builder.setMessage(context.getString(R.string.VoipGroupInviteAlreadyParticipant))
			}

			else -> {
				builder.setMessage("${context.getString(R.string.ErrorOccurred)}\n$error")
			}
		}

		builder.setPositiveButton(context.getString(R.string.OK), null)

		fragment.showDialog(builder.create(), true, null)
	}

	@JvmStatic
	fun createColorSelectDialog(parentActivity: Activity, dialogId: Long, globalType: Int, onSelect: Runnable?): Dialog {
		val currentColor: Int
		val preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount)

		currentColor = if (dialogId != 0L) {
			if (preferences.contains("color_$dialogId")) {
				preferences.getInt("color_$dialogId", -0xffff01)
			}
			else {
				if (DialogObject.isChatDialog(dialogId)) {
					preferences.getInt("GroupLed", -0xffff01)
				}
				else {
					preferences.getInt("MessagesLed", -0xffff01)
				}
			}
		}
		else if (globalType == NotificationsController.TYPE_PRIVATE) {
			preferences.getInt("MessagesLed", -0xffff01)
		}
		else if (globalType == NotificationsController.TYPE_GROUP) {
			preferences.getInt("GroupLed", -0xffff01)
		}
		else {
			preferences.getInt("ChannelLed", -0xffff01)
		}

		val linearLayout = LinearLayout(parentActivity)
		linearLayout.orientation = LinearLayout.VERTICAL

		val descriptions = arrayOf(parentActivity.getString(R.string.ColorRed), parentActivity.getString(R.string.ColorOrange), parentActivity.getString(R.string.ColorYellow), parentActivity.getString(R.string.ColorGreen), parentActivity.getString(R.string.ColorCyan), parentActivity.getString(R.string.ColorBlue), parentActivity.getString(R.string.ColorViolet), parentActivity.getString(R.string.ColorPink), parentActivity.getString(R.string.ColorWhite))
		val selectedColor = intArrayOf(currentColor)

		for (a in 0..8) {
			val cell = RadioColorCell(parentActivity)
			cell.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
			cell.tag = a
			cell.setCheckColor(TextColorCell.colors[a], TextColorCell.colors[a])
			cell.setTextAndValue(descriptions[a], currentColor == TextColorCell.colorsToSave[a])

			linearLayout.addView(cell)

			cell.setOnClickListener { v: View ->
				val count = linearLayout.childCount

				for (a1 in 0 until count) {
					val cell1 = linearLayout.getChildAt(a1) as RadioColorCell
					cell1.setChecked(cell1 == v, true)
				}

				selectedColor[0] = TextColorCell.colorsToSave[(v.tag as Int)]
			}
		}

		val builder = AlertDialog.Builder(parentActivity)
		builder.setTitle(parentActivity.getString(R.string.LedColor))
		builder.setView(linearLayout)

		builder.setPositiveButton(parentActivity.getString(R.string.Set)) { _, _ ->
			val preferences1 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount)
			val editor = preferences1.edit()

			if (dialogId != 0L) {
				editor.putInt("color_$dialogId", selectedColor[0])
				NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannel(dialogId)
			}
			else {
				when (globalType) {
					NotificationsController.TYPE_PRIVATE -> editor.putInt("MessagesLed", selectedColor[0])
					NotificationsController.TYPE_GROUP -> editor.putInt("GroupLed", selectedColor[0])
					else -> editor.putInt("ChannelLed", selectedColor[0])
				}

				NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannelGlobal(globalType)
			}

			editor.commit()

			onSelect?.run()
		}

		builder.setNeutralButton(parentActivity.getString(R.string.LedDisabled)) { _, _ ->
			val preferences12 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount)
			val editor = preferences12.edit()

			if (dialogId != 0L) {
				editor.putInt("color_$dialogId", 0)
			}
			else if (globalType == NotificationsController.TYPE_PRIVATE) {
				editor.putInt("MessagesLed", 0)
			}
			else if (globalType == NotificationsController.TYPE_GROUP) {
				editor.putInt("GroupLed", 0)
			}
			else {
				editor.putInt("ChannelLed", 0)
			}

			editor.commit()

			onSelect?.run()
		}

		if (dialogId != 0L) {
			builder.setNegativeButton(parentActivity.getString(R.string.Default)) { _, _ ->
				val preferences13 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount)

				val editor = preferences13.edit()
				editor.remove("color_$dialogId")
				editor.commit()

				onSelect?.run()
			}
		}

		return builder.create()
	}

	@JvmStatic
	fun createVibrationSelectDialog(parentActivity: Activity, dialogId: Long, globalGroup: Boolean, onSelect: Runnable?): Dialog {
		val prefix = if (dialogId != 0L) {
			"vibrate_$dialogId"
		}
		else {
			if (globalGroup) "vibrate_group" else "vibrate_messages"
		}

		return createVibrationSelectDialog(parentActivity, dialogId, prefix, onSelect)
	}

	@JvmStatic
	fun createVibrationSelectDialog(parentActivity: Activity, dialogId: Long, prefKeyPrefix: String, onSelect: Runnable?): Dialog {
		val preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount)
		val selected = IntArray(1)
		val descriptions: Array<String>

		if (dialogId != 0L) {
			selected[0] = preferences.getInt(prefKeyPrefix, 0)

			if (selected[0] == 3) {
				selected[0] = 2
			}
			else if (selected[0] == 2) {
				selected[0] = 3
			}

			descriptions = arrayOf(parentActivity.getString(R.string.VibrationDefault), parentActivity.getString(R.string.Short), parentActivity.getString(R.string.Long), parentActivity.getString(R.string.VibrationDisabled))
		}
		else {
			selected[0] = preferences.getInt(prefKeyPrefix, 0)

			if (selected[0] == 0) {
				selected[0] = 1
			}
			else if (selected[0] == 1) {
				selected[0] = 2
			}
			else if (selected[0] == 2) {
				selected[0] = 0
			}

			descriptions = arrayOf(parentActivity.getString(R.string.VibrationDisabled), parentActivity.getString(R.string.VibrationDefault), parentActivity.getString(R.string.Short), parentActivity.getString(R.string.Long), parentActivity.getString(R.string.OnlyIfSilent))
		}

		val linearLayout = LinearLayout(parentActivity)
		linearLayout.orientation = LinearLayout.VERTICAL

		val builder = AlertDialog.Builder(parentActivity)

		for (a in descriptions.indices) {
			val cell = RadioColorCell(parentActivity)
			cell.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
			cell.tag = a
			cell.setCheckColor(parentActivity.getColor(R.color.brand), parentActivity.getColor(R.color.brand))
			cell.setTextAndValue(descriptions[a], selected[0] == a)

			linearLayout.addView(cell)

			cell.setOnClickListener {
				selected[0] = it.tag as Int

				val preferences1 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount)
				val editor = preferences1.edit()

				if (dialogId != 0L) {
					if (selected[0] == 0) {
						editor.putInt(prefKeyPrefix, 0)
					}
					else if (selected[0] == 1) {
						editor.putInt(prefKeyPrefix, 1)
					}
					else if (selected[0] == 2) {
						editor.putInt(prefKeyPrefix, 3)
					}
					else if (selected[0] == 3) {
						editor.putInt(prefKeyPrefix, 2)
					}

					NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannel(dialogId)
				}
				else {
					if (selected[0] == 0) {
						editor.putInt(prefKeyPrefix, 2)
					}
					else if (selected[0] == 1) {
						editor.putInt(prefKeyPrefix, 0)
					}
					else if (selected[0] == 2) {
						editor.putInt(prefKeyPrefix, 1)
					}
					else if (selected[0] == 3) {
						editor.putInt(prefKeyPrefix, 3)
					}
					else if (selected[0] == 4) {
						editor.putInt(prefKeyPrefix, 4)
					}

					when (prefKeyPrefix) {
						"vibrate_channel" -> {
							NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannelGlobal(NotificationsController.TYPE_CHANNEL)
						}

						"vibrate_group" -> {
							NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannelGlobal(NotificationsController.TYPE_GROUP)
						}

						else -> {
							NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannelGlobal(NotificationsController.TYPE_PRIVATE)
						}
					}
				}

				editor.commit()

				builder.getDismissRunnable().run()

				onSelect?.run()
			}
		}

		builder.setTitle(parentActivity.getString(R.string.Vibrate))
		builder.setView(linearLayout)
		builder.setPositiveButton(parentActivity.getString(R.string.Cancel), null)

		return builder.create()
	}

	@JvmStatic
	fun createLocationUpdateDialog(parentActivity: Activity, user: User?, callback: MessagesStorage.IntCallback): Dialog {
		val selected = IntArray(1)
		val descriptions = arrayOf(parentActivity.getString(R.string.SendLiveLocationFor15m), parentActivity.getString(R.string.SendLiveLocationFor1h), parentActivity.getString(R.string.SendLiveLocationFor8h))

		val linearLayout = LinearLayout(parentActivity)
		linearLayout.orientation = LinearLayout.VERTICAL

		val titleTextView = TextView(parentActivity)

		if (user != null) {
			titleTextView.text = LocaleController.formatString("LiveLocationAlertPrivate", R.string.LiveLocationAlertPrivate, UserObject.getFirstName(user))
		}
		else {
			titleTextView.text = parentActivity.getString(R.string.LiveLocationAlertGroup)
		}

		titleTextView.setTextColor(ResourcesCompat.getColor(parentActivity.resources, R.color.text, null))
		titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		titleTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		linearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24, 0, 24, 8))

		for (a in descriptions.indices) {
			val checkbox = MaterialCheckBox(parentActivity)
			checkbox.tag = a
			checkbox.text = descriptions[a]
			checkbox.isChecked = selected[0] == a
			checkbox.setCompoundDrawablePadding(AndroidUtilities.dp(4f))

			linearLayout.addView(checkbox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24, 0, 24, 4))

			checkbox.setOnClickListener {
				val num = it.tag as Int

				selected[0] = num

				val count = linearLayout.childCount

				for (a1 in 0 until count) {
					val child = linearLayout.getChildAt(a1)

					if (child is MaterialCheckBox) {
						child.isChecked = child === it
					}
				}
			}
		}

		val builder = AlertDialog.Builder(parentActivity)

		val topImageColor = ResourcesCompat.getColor(parentActivity.resources, R.color.brand, null)

		builder.setTopImage(ShareLocationDrawable(parentActivity, 0), topImageColor)
		builder.setView(linearLayout)

		builder.setPositiveButton(parentActivity.getString(R.string.ShareFile)) { _, _ ->
			val time = if (selected[0] == 0) {
				15 * 60
			}
			else if (selected[0] == 1) {
				60 * 60
			}
			else {
				8 * 60 * 60
			}

			callback.run(time)
		}

		builder.setNeutralButton(parentActivity.getString(R.string.Cancel), null)

		return builder.create()
	}

	@JvmStatic
	fun createBackgroundLocationPermissionDialog(activity: Activity?, selfUser: User?, cancelRunnable: Runnable): AlertDialog.Builder? {
		if (activity == null || Build.VERSION.SDK_INT < 29) {
			return null
		}

		val builder = AlertDialog.Builder(activity)
		val svg = RLottieDrawable.readRes(null, if (Theme.getCurrentTheme().isDark) R.raw.permission_map_dark else R.raw.permission_map)
		val pinSvg = RLottieDrawable.readRes(null, if (Theme.getCurrentTheme().isDark) R.raw.permission_pin_dark else R.raw.permission_pin)
		val frameLayout = FrameLayout(activity)

		frameLayout.setClipToOutline(true)

		frameLayout.outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setRoundRect(0, 0, view.measuredWidth, view.measuredHeight + AndroidUtilities.dp(6f), AndroidUtilities.dp(6f).toFloat())
			}
		}

		val background = View(activity)
		background.background = SvgHelper.getDrawable(svg)

		frameLayout.addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 0f, 0f, 0f))

		val pin = View(activity)
		pin.background = SvgHelper.getDrawable(pinSvg)

		frameLayout.addView(pin, LayoutHelper.createFrame(60, 82f, Gravity.CENTER, 0f, 0f, 0f, 0f))

		val imageView = BackupImageView(activity)
		imageView.setRoundRadius(AndroidUtilities.dp(26f))
		imageView.setForUserOrChat(selfUser, AvatarDrawable(selfUser))

		frameLayout.addView(imageView, LayoutHelper.createFrame(52, 52f, Gravity.CENTER, 0f, 0f, 0f, 11f))

		builder.setTopView(frameLayout)

		val aspectRatio = 354f / 936f

		builder.setTopViewAspectRatio(aspectRatio)
		builder.setMessage(AndroidUtilities.replaceTags(activity.getString(R.string.PermissionBackgroundLocation)))

		builder.setPositiveButton(activity.getString(R.string.Continue)) { _, _ ->
			if (activity.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 30)
			}
		}

		builder.setNegativeButton(activity.getString(R.string.Cancel)) { _, _ ->
			cancelRunnable.run()
		}

		return builder
	}

	fun createGigagroupConvertAlert(activity: Activity, onProcess: DialogInterface.OnClickListener?, onCancel: DialogInterface.OnClickListener?): AlertDialog.Builder {
		val builder = AlertDialog.Builder(activity)
		val svg = RLottieDrawable.readRes(null, R.raw.gigagroup)

		val frameLayout = FrameLayout(activity)
		frameLayout.setClipToOutline(true)

		frameLayout.outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setRoundRect(0, 0, view.measuredWidth, view.measuredHeight + AndroidUtilities.dp(6f), AndroidUtilities.dp(6f).toFloat())
			}
		}

		val aspectRatio = 372f / 936f

		val background = View(activity)
		background.background = BitmapDrawable(activity.resources, SvgHelper.getBitmap(svg, AndroidUtilities.dp(320f), AndroidUtilities.dp(320 * aspectRatio), false))

		frameLayout.addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), 0, -1f, -1f, -1f, -1f))

		builder.setTopView(frameLayout)
		builder.setTopViewAspectRatio(aspectRatio)
		builder.setTitle(activity.getString(R.string.GigagroupAlertTitle))
		builder.setMessage(AndroidUtilities.replaceTags(activity.getString(R.string.GigagroupAlertText)))
		builder.setPositiveButton(activity.getString(R.string.GigagroupAlertLearnMore), onProcess)
		builder.setNegativeButton(activity.getString(R.string.Cancel), onCancel)

		return builder
	}

	@JvmStatic
	fun createDrawOverlayPermissionDialog(activity: Activity, onCancel: DialogInterface.OnClickListener?): AlertDialog.Builder {
		val builder = AlertDialog.Builder(activity)
		val svg = RLottieDrawable.readRes(null, R.raw.pip_video_request)

		val frameLayout = FrameLayout(activity)
		frameLayout.background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(-0xddc9b1, -0xddad96))
		frameLayout.setClipToOutline(true)

		frameLayout.outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setRoundRect(0, 0, view.measuredWidth, view.measuredHeight + AndroidUtilities.dp(6f), AndroidUtilities.dpf2(6f))
			}
		}

		val aspectRatio = 472f / 936f

		val background = View(activity)
		background.background = BitmapDrawable(activity.resources, SvgHelper.getBitmap(svg, AndroidUtilities.dp(320f), AndroidUtilities.dp(320 * aspectRatio), false))

		frameLayout.addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), 0, -1f, -1f, -1f, -1f))

		builder.setTopView(frameLayout)
		builder.setTitle(activity.getString(R.string.PermissionDrawAboveOtherAppsTitle))
		builder.setMessage(activity.getString(R.string.PermissionDrawAboveOtherApps))

		builder.setPositiveButton(activity.getString(R.string.Enable)) { _, _ ->
			try {
				activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.packageName)))
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		builder.notDrawBackgroundOnTopView(true)
		builder.setNegativeButton(activity.getString(R.string.Cancel), onCancel)
		builder.setTopViewAspectRatio(aspectRatio)

		return builder
	}

	@JvmStatic
	fun createDrawOverlayGroupCallPermissionDialog(context: Context): AlertDialog.Builder {
		val builder = AlertDialog.Builder(context)
		val svg = RLottieDrawable.readRes(null, R.raw.pip_voice_request)

		val button = GroupCallPipButton(context, 0, true)
		button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)

		val frameLayout = object : FrameLayout(context) {
			override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
				super.onLayout(changed, left, top, right, bottom)
				button.translationY = measuredHeight * 0.28f - button.measuredWidth / 2f
				button.translationX = measuredWidth * 0.82f - button.measuredWidth / 2f
			}
		}

		frameLayout.background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(-0xe6d5c3, -0xe6aeb2))
		frameLayout.setClipToOutline(true)

		frameLayout.outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setRoundRect(0, 0, view.measuredWidth, view.measuredHeight + AndroidUtilities.dp(6f), AndroidUtilities.dpf2(6f))
			}
		}

		val aspectRatio = 540f / 936f

		val background = View(context)
		background.background = BitmapDrawable(context.resources, SvgHelper.getBitmap(svg, AndroidUtilities.dp(320f), AndroidUtilities.dp(320 * aspectRatio), false))

		frameLayout.addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), 0, -1f, -1f, -1f, -1f))
		frameLayout.addView(button, LayoutHelper.createFrame(117, 117f))

		builder.setTopView(frameLayout)
		builder.setTitle(context.getString(R.string.PermissionDrawAboveOtherAppsGroupCallTitle))
		builder.setMessage(context.getString(R.string.PermissionDrawAboveOtherAppsGroupCall))

		builder.setPositiveButton(context.getString(R.string.Enable)) { _, _ ->
			try {
				val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
				val activity = AndroidUtilities.findActivity(context)

				if (activity is LaunchActivity) {
					activity.startActivityForResult(intent, 105)
				}
				else {
					context.startActivity(intent)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		builder.notDrawBackgroundOnTopView(true)
		builder.setNegativeButton(context.getString(R.string.Cancel), null)
		builder.setTopViewAspectRatio(aspectRatio)

		return builder
	}

	fun createFreeSpaceDialog(parentActivity: LaunchActivity): Dialog {
		var selected = when (SharedConfig.keepMedia) {
			2 -> 3
			0 -> 1
			1 -> 2
			3 -> 0
			else -> 0
		}

		val descriptions = arrayOf(LocaleController.formatPluralString("Days", 3), LocaleController.formatPluralString("Weeks", 1), LocaleController.formatPluralString("Months", 1), parentActivity.getString(R.string.LowDiskSpaceNeverRemove))

		val linearLayout = LinearLayout(parentActivity)
		linearLayout.orientation = LinearLayout.VERTICAL

		val titleTextView = TextView(parentActivity)
		titleTextView.text = parentActivity.getString(R.string.LowDiskSpaceTitle2)
		titleTextView.setTextColor(parentActivity.getColor(R.color.text))
		titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		titleTextView.setTypeface(Theme.TYPEFACE_BOLD)
		titleTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		linearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24, 0, 24, 8))

		for (a in descriptions.indices) {
			val cell = RadioColorCell(parentActivity)
			cell.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
			cell.tag = a
			cell.setCheckColor(parentActivity.getColor(R.color.brand), parentActivity.getColor(R.color.brand))
			cell.setTextAndValue(descriptions[a], selected == a)

			linearLayout.addView(cell)

			cell.setOnClickListener {
				val num = it.tag as Int

				when (num) {
					0 -> selected = 3
					1 -> selected = 0
					2 -> selected = 1
					3 -> selected = 2
				}

				val count = linearLayout.childCount

				for (a1 in 0 until count) {
					val child = linearLayout.getChildAt(a1)

					if (child is RadioColorCell) {
						child.setChecked(child === it, true)
					}
				}
			}
		}

		val builder = AlertDialog.Builder(parentActivity)
		builder.setTitle(parentActivity.getString(R.string.LowDiskSpaceTitle))
		builder.setMessage(parentActivity.getString(R.string.LowDiskSpaceMessage))
		builder.setView(linearLayout)

		builder.setPositiveButton(parentActivity.getString(R.string.OK)) { _, _ ->
			SharedConfig.setKeepMedia(selected)
		}

		builder.setNeutralButton(parentActivity.getString(R.string.ClearMediaCache)) { _, _ ->
			parentActivity.presentFragment(CacheControlActivity())
		}

		return builder.create()
	}

	@JvmStatic
	fun createPrioritySelectDialog(parentActivity: Activity, dialogId: Long, globalType: Int, onSelect: Runnable?): Dialog {
		val preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount)
		var selected = 0
		val descriptions: Array<String>

		if (dialogId != 0L) {
			selected = preferences.getInt("priority_$dialogId", 3)

			selected = when (selected) {
				3 -> 0
				4 -> 1
				5 -> 2
				0 -> 3
				else -> 4
			}

			descriptions = arrayOf(parentActivity.getString(R.string.NotificationsPrioritySettings), parentActivity.getString(R.string.NotificationsPriorityLow), parentActivity.getString(R.string.NotificationsPriorityMedium), parentActivity.getString(R.string.NotificationsPriorityHigh), parentActivity.getString(R.string.NotificationsPriorityUrgent))
		}
		else {
			when (globalType) {
				NotificationsController.TYPE_PRIVATE -> {
					selected = preferences.getInt("priority_messages", 1)
				}

				NotificationsController.TYPE_GROUP -> {
					selected = preferences.getInt("priority_group", 1)
				}

				NotificationsController.TYPE_CHANNEL -> {
					selected = preferences.getInt("priority_channel", 1)
				}
			}

			selected = when (selected) {
				4 -> 0
				5 -> 1
				0 -> 2
				else -> 3
			}

			descriptions = arrayOf(parentActivity.getString(R.string.NotificationsPriorityLow), parentActivity.getString(R.string.NotificationsPriorityMedium), parentActivity.getString(R.string.NotificationsPriorityHigh), parentActivity.getString(R.string.NotificationsPriorityUrgent))
		}

		val linearLayout = LinearLayout(parentActivity)
		linearLayout.orientation = LinearLayout.VERTICAL

		val builder = AlertDialog.Builder(parentActivity)

		for (a in descriptions.indices) {
			val cell = RadioColorCell(parentActivity)
			cell.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
			cell.tag = a
			cell.setCheckColor(parentActivity.getColor(R.color.brand), parentActivity.getColor(R.color.brand))
			cell.setTextAndValue(descriptions[a], selected == a)

			linearLayout.addView(cell)

			cell.setOnClickListener {
				selected = it.tag as Int
				val preferences1 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount)
				val editor = preferences1.edit()

				if (dialogId != 0L) {
					val option = when (selected) {
						0 -> 3
						1 -> 4
						2 -> 5
						3 -> 0
						else -> 1
					}

					editor.putInt("priority_$dialogId", option)

					NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannel(dialogId)
				}
				else {
					val option = when (selected) {
						0 -> 4
						1 -> 5
						2 -> 0
						else -> 1
					}

					when (globalType) {
						NotificationsController.TYPE_PRIVATE -> {
							editor.putInt("priority_messages", option)
							selected = preferences.getInt("priority_messages", 1)
						}

						NotificationsController.TYPE_GROUP -> {
							editor.putInt("priority_group", option)
							selected = preferences.getInt("priority_group", 1)
						}

						NotificationsController.TYPE_CHANNEL -> {
							editor.putInt("priority_channel", option)
							selected = preferences.getInt("priority_channel", 1)
						}
					}

					NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannelGlobal(globalType)
				}

				editor.commit()

				builder.getDismissRunnable().run()

				onSelect?.run()
			}
		}

		builder.setTitle(parentActivity.getString(R.string.NotificationsImportance))
		builder.setView(linearLayout)
		builder.setPositiveButton(parentActivity.getString(R.string.Cancel), null)

		return builder.create()
	}

	@JvmStatic
	fun createPopupSelectDialog(parentActivity: Activity, globalType: Int, onSelect: Runnable?): Dialog {
		val preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount)

		var selected = when (globalType) {
			NotificationsController.TYPE_PRIVATE -> preferences.getInt("popupAll", 0)
			NotificationsController.TYPE_GROUP -> preferences.getInt("popupGroup", 0)
			else -> preferences.getInt("popupChannel", 0)
		}

		val descriptions = arrayOf(parentActivity.getString(R.string.NoPopup), parentActivity.getString(R.string.OnlyWhenScreenOn), parentActivity.getString(R.string.OnlyWhenScreenOff), parentActivity.getString(R.string.AlwaysShowPopup))

		val linearLayout = LinearLayout(parentActivity)
		linearLayout.orientation = LinearLayout.VERTICAL

		val builder = AlertDialog.Builder(parentActivity)

		for (a in descriptions.indices) {
			val cell = RadioColorCell(parentActivity)
			cell.tag = a
			cell.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
			cell.setCheckColor(parentActivity.getColor(R.color.brand), parentActivity.getColor(R.color.brand))
			cell.setTextAndValue(descriptions[a], selected == a)

			linearLayout.addView(cell)

			cell.setOnClickListener {
				selected = it.tag as Int
				val preferences1 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount)
				val editor = preferences1.edit()

				when (globalType) {
					NotificationsController.TYPE_PRIVATE -> editor.putInt("popupAll", selected)
					NotificationsController.TYPE_GROUP -> editor.putInt("popupGroup", selected)
					else -> editor.putInt("popupChannel", selected)
				}

				editor.commit()

				builder.getDismissRunnable().run()

				onSelect?.run()
			}
		}

		builder.setTitle(parentActivity.getString(R.string.PopupNotification))
		builder.setView(linearLayout)
		builder.setPositiveButton(parentActivity.getString(R.string.Cancel), null)

		return builder.create()
	}

	@JvmStatic
	fun createSingleChoiceDialog(parentActivity: Activity, options: Array<String?>, title: String?, selected: Int, listener: DialogInterface.OnClickListener): Dialog {
		val linearLayout = LinearLayout(parentActivity)
		linearLayout.orientation = LinearLayout.VERTICAL

		val builder = AlertDialog.Builder(parentActivity)

		for (a in options.indices) {
			val cell = RadioColorCell(parentActivity)
			cell.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
			cell.tag = a
			cell.setCheckColor(parentActivity.getColor(R.color.brand), parentActivity.getColor(R.color.brand))
			cell.setTextAndValue(options[a], selected == a)

			linearLayout.addView(cell)

			cell.setOnClickListener {
				val sel = it.tag as Int
				builder.getDismissRunnable().run()
				listener.onClick(null, sel)
			}
		}

		builder.setTitle(title)
		builder.setView(linearLayout)
		builder.setPositiveButton(parentActivity.getString(R.string.Cancel), null)

		return builder.create()
	}

	fun createTTLAlert(context: Context, encryptedChat: EncryptedChat): AlertDialog.Builder {
		val builder = AlertDialog.Builder(context)
		builder.setTitle(context.getString(R.string.MessageLifetime))

		val numberPicker = NumberPicker(context)
		numberPicker.setMinValue(0)
		numberPicker.setMaxValue(20)

		when (encryptedChat.ttl) {
			in 1..15 -> numberPicker.value = encryptedChat.ttl
			30 -> numberPicker.value = 16
			60 -> numberPicker.value = 17
			60 * 60 -> numberPicker.value = 18
			60 * 60 * 24 -> numberPicker.value = 19
			60 * 60 * 24 * 7 -> numberPicker.value = 20
			0 -> numberPicker.value = 0
		}

		numberPicker.setFormatter { value ->
			when (value) {
				0 -> return@setFormatter context.getString(R.string.ShortMessageLifetimeForever)
				in 1..15 -> return@setFormatter LocaleController.formatTTLString(value)
				16 -> return@setFormatter LocaleController.formatTTLString(30)
				17 -> return@setFormatter LocaleController.formatTTLString(60)
				18 -> return@setFormatter LocaleController.formatTTLString(60 * 60)
				19 -> return@setFormatter LocaleController.formatTTLString(60 * 60 * 24)
				20 -> return@setFormatter LocaleController.formatTTLString(60 * 60 * 24 * 7)
				else -> return@setFormatter ""
			}
		}

		builder.setView(numberPicker)

		builder.setNegativeButton(context.getString(R.string.Done)) { _, _ ->
			val oldValue = encryptedChat.ttl

			when (val which = numberPicker.value) {
				in 0..15 -> encryptedChat.ttl = which
				16 -> encryptedChat.ttl = 30
				17 -> encryptedChat.ttl = 60
				18 -> encryptedChat.ttl = 60 * 60
				19 -> encryptedChat.ttl = 60 * 60 * 24
				20 -> encryptedChat.ttl = 60 * 60 * 24 * 7
			}

			if (oldValue != encryptedChat.ttl) {
				SecretChatHelper.getInstance(UserConfig.selectedAccount).sendTTLMessage(encryptedChat, null)
				MessagesStorage.getInstance(UserConfig.selectedAccount).updateEncryptedChatTTL(encryptedChat)
			}
		}

		return builder
	}

	@JvmStatic
	fun createTooLargeFileDialog(parentActivity: Activity, fileSize: Long): AlertDialog {
		val builder = AlertDialog.Builder(parentActivity)
		val alertDialog = arrayOfNulls<AlertDialog>(1)
		val dismissRunnable = builder.getDismissRunnable()

		val binding = FileSizeLimitAlertBinding.inflate(parentActivity.layoutInflater)
		binding.limitLabel.text = parentActivity.getString(R.string.limit_file_size_format, formatFileSize(FileLoader.DEFAULT_MAX_FILE_SIZE))

		if (fileSize > 0) {
			binding.sizeLabel.text = parentActivity.getString(R.string.large_file_error_format, AndroidUtilities.formatFileSize(fileSize))
		}
		else {
			binding.sizeLabel.visibility = View.GONE
		}

		val onClickListener = View.OnClickListener {
			alertDialog[0]?.setOnDismissListener(null)
			dismissRunnable.run()
		}

		binding.okButton.setOnClickListener(onClickListener)
		binding.closeButton.setOnClickListener(onClickListener)

		builder.setView(binding.getRoot())

		alertDialog[0] = builder.create()

		return alertDialog[0]!!
	}

	fun formatFileSize(sizeInBytes: Long): String {
		val kb = 1024L
		val mb = kb * 1024
		val gb = mb * 1024

		return when {
			sizeInBytes >= gb -> "${sizeInBytes / gb} GB"
			sizeInBytes >= mb -> "${sizeInBytes / mb} MB"
			sizeInBytes >= kb -> "${sizeInBytes / kb} KB"
			else -> "$sizeInBytes B"
		}
	}

	@JvmStatic
	fun createAccountSelectDialog(parentActivity: Activity, delegate: AccountSelectDelegate): AlertDialog? {
		if (UserConfig.activatedAccountsCount < 2) {
			return null
		}

		val builder = AlertDialog.Builder(parentActivity)
		val dismissRunnable = builder.getDismissRunnable()
		val alertDialog = arrayOfNulls<AlertDialog>(1)

		val linearLayout = LinearLayout(parentActivity)
		linearLayout.orientation = LinearLayout.VERTICAL

		for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
			val u = UserConfig.getInstance(a).getCurrentUser()

			if (u != null) {
				val cell = AccountSelectCell(parentActivity, false)
				cell.setAccount(a, false)
				cell.setPadding(AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f), 0)
				cell.background = Theme.getSelectorDrawable(false)

				linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50))

				cell.setOnClickListener {
					alertDialog[0]?.setOnDismissListener(null)
					dismissRunnable.run()
					val cell1 = it as AccountSelectCell
					delegate.didSelectAccount(cell1.accountNumber)
				}
			}
		}

		builder.setTitle(parentActivity.getString(R.string.SelectAccount))
		builder.setView(linearLayout)
		builder.setPositiveButton(parentActivity.getString(R.string.Cancel), null)

		return builder.create().also { alertDialog[0] = it }
	}

	fun createDeleteMessagesAlert(fragment: BaseFragment?, user: User?, chat: Chat?, encryptedChat: EncryptedChat?, mergeDialogId: Long, selectedMessage: MessageObject?, selectedMessages: Array<SparseArray<MessageObject>>, selectedGroup: GroupedMessages?, scheduled: Boolean, loadParticipant: Int, onDelete: Runnable?, hideDim: Runnable?) {
		if (fragment == null || user == null && chat == null && encryptedChat == null) {
			return
		}

		val activity = fragment.getParentActivity() ?: return
		val currentAccount = fragment.currentAccount

		val builder = AlertDialog.Builder(activity)
		builder.setDimAlpha(if (hideDim != null) .5f else .6f)

		val count = selectedGroup?.messages?.size ?: if (selectedMessage != null) {
			1
		}
		else {
			selectedMessages[0].size() + selectedMessages[1].size()
		}

		val dialogId = if (encryptedChat != null) {
			DialogObject.makeEncryptedDialogId(encryptedChat.id.toLong())
		}
		else {
			user?.id ?: -chat!!.id
		}

		val currentDate = ConnectionsManager.getInstance(currentAccount).currentTime
		var hasNonDiceMessages = false

		if (selectedMessage != null) {
			hasNonDiceMessages = !selectedMessage.isDice || abs((currentDate - selectedMessage.messageOwner!!.date).toDouble()) > 24 * 60 * 60
		}
		else {
			for (a in 0..1) {
				for (b in 0 until selectedMessages[a].size()) {
					val msg = selectedMessages[a].valueAt(b)

					if (!msg.isDice || abs((currentDate - msg.messageOwner!!.date).toDouble()) > 24 * 60 * 60) {
						hasNonDiceMessages = true
						break
					}
				}
			}
		}

		val checks = BooleanArray(3)
		val deleteForAll = BooleanArray(1)
		var actionUser: User? = null
		var actionChat: Chat? = null
		val canRevokeInbox = user != null && MessagesController.getInstance(currentAccount).canRevokePmInbox

		val revokeTimeLimit = if (user != null) {
			MessagesController.getInstance(currentAccount).revokeTimePmLimit
		}
		else {
			MessagesController.getInstance(currentAccount).revokeTimeLimit
		}

		var hasDeleteForAllCheck = false
		var hasNotOut = false
		var myMessagesCount = 0
		val canDeleteInbox = encryptedChat == null && user != null && canRevokeInbox && revokeTimeLimit == 0x7fffffff

		if (chat != null && chat.megagroup && !scheduled) {
			val canBan = ChatObject.canBlockUsers(chat)

			if (selectedMessage != null) {
				if (selectedMessage.messageOwner?.action == null || selectedMessage.messageOwner?.action is TL_messageActionEmpty || selectedMessage.messageOwner?.action is TL_messageActionChatDeleteUser || selectedMessage.messageOwner?.action is TL_messageActionChatJoinedByLink || selectedMessage.messageOwner?.action is TL_messageActionChatAddUser) {
					if (selectedMessage.messageOwner!!.from_id!!.user_id != 0L) {
						actionUser = MessagesController.getInstance(currentAccount).getUser(selectedMessage.messageOwner?.from_id?.user_id)
					}
					else if (selectedMessage.messageOwner!!.from_id!!.channel_id != 0L) {
						actionChat = MessagesController.getInstance(currentAccount).getChat(selectedMessage.messageOwner?.from_id?.channel_id)
					}
					else if (selectedMessage.messageOwner!!.from_id!!.chat_id != 0L) {
						actionChat = MessagesController.getInstance(currentAccount).getChat(selectedMessage.messageOwner?.from_id?.chat_id)
					}
				}

				val hasOutgoing = !selectedMessage.isSendError && selectedMessage.dialogId == mergeDialogId && (selectedMessage.messageOwner?.action == null || selectedMessage.messageOwner?.action is TL_messageActionEmpty) && selectedMessage.isOut && currentDate - selectedMessage.messageOwner!!.date <= revokeTimeLimit

				if (hasOutgoing) {
					myMessagesCount++
				}
			}
			else {
				var fromId: Long = -1

				for (a in 1 downTo 0) {
					for (b in 0 until selectedMessages[a].size()) {
						val msg = selectedMessages[a].valueAt(b)

						if (fromId == -1L) {
							fromId = msg.fromChatId
						}

						if (fromId < 0 || fromId != msg.senderId) {
							fromId = -2
							break
						}
					}

					if (fromId == -2L) {
						break
					}
				}

				for (a in 1 downTo 0) {
					for (b in 0 until selectedMessages[a].size()) {
						val msg = selectedMessages[a].valueAt(b)

						if (a == 1) {
							if (msg.isOut && msg.messageOwner?.action == null) {
								if (currentDate - msg.messageOwner!!.date <= revokeTimeLimit) {
									myMessagesCount++
								}
							}
						}
					}
				}

				if (fromId != -1L) {
					actionUser = MessagesController.getInstance(currentAccount).getUser(fromId)
				}
			}

			if (actionUser != null && actionUser.id != UserConfig.getInstance(currentAccount).getClientUserId() || actionChat != null && !ChatObject.hasAdminRights(actionChat)) {
				if (loadParticipant == 1 && !chat.creator && actionUser != null) {
					val progressDialog = arrayOf<AlertDialog?>(AlertDialog(activity, 3))

					val req = TL_channels_getParticipant()
					req.channel = MessagesController.getInputChannel(chat)
					req.participant = MessagesController.getInputPeer(actionUser)

					val requestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
						AndroidUtilities.runOnUIThread {
							runCatching {
								progressDialog[0]?.dismiss()
							}

							progressDialog[0] = null

							var loadType = 2

							if (response != null) {
								val participant = response as TL_channels_channelParticipant

								if (!(participant.participant is TL_channelParticipantAdmin || participant.participant is TL_channelParticipantCreator)) {
									loadType = 0
								}
							}
							else if (error != null && "USER_NOT_PARTICIPANT" == error.text) {
								loadType = 0
							}

							createDeleteMessagesAlert(fragment, user, chat, encryptedChat, mergeDialogId, selectedMessage, selectedMessages, selectedGroup, scheduled, loadType, onDelete, hideDim)
						}
					}
					AndroidUtilities.runOnUIThread({
						if (progressDialog[0] == null) {
							return@runOnUIThread
						}

						progressDialog[0]?.setOnCancelListener {
							ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true)
						}

						fragment.showDialog(progressDialog[0])
					}, 1000)

					return
				}

				val frameLayout = FrameLayout(activity)
				var num = 0
				//MARK: The text of the third checkbox is set here
				// val name = if (actionUser != null) ContactsController.formatName(actionUser.first_name, actionUser.last_name) else actionChat?.title

				//If you need three items then change (for (a in 0..1)) with (for (a in 0..2))
				for (a in 0..1) {
					if ((loadParticipant == 2 || !canBan) && a == 0) {
						continue
					}

					val cell = CheckBoxCell(activity, 1)
					cell.background = Theme.getSelectorDrawable(false)
					cell.tag = a

					when (a) {
						0 -> {
							cell.setText(activity.getString(R.string.DeleteBanUser), "", checked = false, divider = false)
						}

						1 -> {
							cell.setText(activity.getString(R.string.DeleteReportSpam), "", checked = false, divider = false)
						}

						else -> {
							//MARK: To display on the UI, uncomment
//							cell.setText(LocaleController.formatString("DeleteAllFrom", R.string.DeleteAllFrom, name), "", checked = false, divider = false)
						}
					}

					cell.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(16f) else AndroidUtilities.dp(8f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(8f) else AndroidUtilities.dp(16f), 0)

					frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.TOP or Gravity.LEFT, 0f, (48 * num).toFloat(), 0f, 0f))

					cell.setOnClickListener {
						if (!it.isEnabled) {
							return@setOnClickListener
						}

						val cell13 = it as CheckBoxCell
						val num1 = cell13.tag as Int
						checks[num1] = !checks[num1]
						cell13.setChecked(checks[num1], true)
					}

					num++
				}

				builder.setView(frameLayout)
			}
			else if (!hasNotOut && myMessagesCount > 0 && hasNonDiceMessages) {
				hasDeleteForAllCheck = true
				val frameLayout = FrameLayout(activity)
				val cell = CheckBoxCell(activity, 1)
				cell.background = Theme.getSelectorDrawable(false)

				if (chat != null && hasNotOut) {
					cell.setText(activity.getString(R.string.DeleteForAll), "", checked = false, divider = false)
				}
				else {
					cell.setText(activity.getString(R.string.DeleteMessagesOption), "", checked = false, divider = false)
				}

				cell.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(16f) else AndroidUtilities.dp(8f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(8f) else AndroidUtilities.dp(16f), 0)

				frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f))

				cell.setOnClickListener {
					val cell12 = it as CheckBoxCell
					deleteForAll[0] = !deleteForAll[0]
					cell12.setChecked(deleteForAll[0], true)
				}

				builder.setView(frameLayout)
				builder.setCustomViewOffset(9)
			}
			else {
				actionUser = null
			}
		}
		else if (!scheduled && !ChatObject.isChannel(chat) && encryptedChat == null) {
			if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId() && (!user.bot || user.support) || chat != null) {
				if (selectedMessage != null) {
					val hasOutgoing = !selectedMessage.isSendError && (selectedMessage.messageOwner?.action == null || selectedMessage.messageOwner?.action is TL_messageActionEmpty || selectedMessage.messageOwner?.action is TL_messageActionPhoneCall || selectedMessage.messageOwner?.action is TL_messageActionPinMessage || selectedMessage.messageOwner?.action is TL_messageActionGeoProximityReached || selectedMessage.messageOwner?.action is TL_messageActionSetChatTheme) && (selectedMessage.isOut || canRevokeInbox || ChatObject.hasAdminRights(chat)) && currentDate - selectedMessage.messageOwner!!.date <= revokeTimeLimit

					if (hasOutgoing) {
						myMessagesCount++
					}

					hasNotOut = !selectedMessage.isOut
				}
				else {
					for (a in 1 downTo 0) {
						for (b in 0 until selectedMessages[a].size()) {
							val msg = selectedMessages[a].valueAt(b)

							if (!(msg.messageOwner?.action == null || msg.messageOwner?.action is TL_messageActionEmpty || msg.messageOwner?.action is TL_messageActionPhoneCall || msg.messageOwner?.action is TL_messageActionPinMessage || msg.messageOwner?.action is TL_messageActionGeoProximityReached)) {
								continue
							}

							if (msg.isOut || canRevokeInbox || chat != null && ChatObject.canBlockUsers(chat)) {
								if (currentDate - msg.messageOwner!!.date <= revokeTimeLimit) {
									myMessagesCount++

									if (!hasNotOut && !msg.isOut) {
										hasNotOut = true
									}
								}
							}
						}
					}
				}
			}

			if (myMessagesCount > 0 && hasNonDiceMessages && (user == null || !UserObject.isDeleted(user))) {
				hasDeleteForAllCheck = true

				val frameLayout = FrameLayout(activity)

				val cell = CheckBoxCell(activity, 1)
				cell.background = Theme.getSelectorDrawable(false)

				if (canDeleteInbox) {
					cell.setText(LocaleController.formatString("DeleteMessagesOptionAlso", R.string.DeleteMessagesOptionAlso, UserObject.getFirstName(user)), "", checked = false, divider = false)
				}
				else if (chat != null && (hasNotOut || myMessagesCount == count)) {
					cell.setText(activity.getString(R.string.DeleteForAll), "", checked = false, divider = false)
				}
				else {
					cell.setText(activity.getString(R.string.DeleteMessagesOption), "", checked = false, divider = false)
				}

				cell.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(16f) else AndroidUtilities.dp(8f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(8f) else AndroidUtilities.dp(16f), 0)

				frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f))

				cell.setOnClickListener {
					val cell1 = it as CheckBoxCell
					deleteForAll[0] = !deleteForAll[0]
					cell1.setChecked(deleteForAll[0], true)
				}

				builder.setView(frameLayout)
				builder.setCustomViewOffset(9)
			}
		}

		val userFinal = actionUser
		val chatFinal = actionChat

		builder.setPositiveButton(activity.getString(R.string.Delete)) { _, _ ->
			var ids: ArrayList<Int>? = null

			if (selectedMessage != null) {
				ids = ArrayList()

				var randomIds: ArrayList<Long>? = null

				if (selectedGroup != null) {
					for (a in selectedGroup.messages.indices) {
						val messageObject = selectedGroup.messages[a]

						ids.add(messageObject.id)

						if (encryptedChat != null && messageObject.messageOwner!!.random_id != 0L && messageObject.type != 10) {
							if (randomIds == null) {
								randomIds = ArrayList()
							}

							randomIds.add(messageObject.messageOwner!!.random_id)
						}
					}
				}
				else {
					ids.add(selectedMessage.id)

					if (encryptedChat != null && selectedMessage.messageOwner!!.random_id != 0L && selectedMessage.type != 10) {
						randomIds = ArrayList()
						randomIds.add(selectedMessage.messageOwner!!.random_id)
					}
				}

				MessagesController.getInstance(currentAccount).deleteMessages(ids, randomIds, encryptedChat, dialogId, deleteForAll[0], scheduled)
			}
			else {
				for (a in 1 downTo 0) {
					ids = ArrayList()

					for (b in 0 until selectedMessages[a].size()) {
						ids.add(selectedMessages[a].keyAt(b))
					}

					var randomIds: ArrayList<Long>? = null

					if (encryptedChat != null) {
						randomIds = ArrayList()

						for (b in 0 until selectedMessages[a].size()) {
							val msg = selectedMessages[a].valueAt(b)

							if (msg.messageOwner!!.random_id != 0L && msg.type != 10) {
								randomIds.add(msg.messageOwner!!.random_id)
							}
						}
					}

					MessagesController.getInstance(currentAccount).deleteMessages(ids, randomIds, encryptedChat, dialogId, deleteForAll[0], scheduled)

					selectedMessages[a].clear()
				}
			}

			if (userFinal != null || chatFinal != null) {
				if (checks[0]) {
					MessagesController.getInstance(currentAccount).deleteParticipantFromChat(chat!!.id, userFinal, chatFinal, forceDelete = false, revoke = false)
				}

				if (checks[1]) {
					val req = TL_channels_reportSpam()
					req.channel = MessagesController.getInputChannel(chat)

					if (userFinal != null) {
						req.participant = MessagesController.getInputPeer(userFinal)
					}
					else {
						req.participant = MessagesController.getInputPeer(chatFinal)
					}

					req.id = ids

					ConnectionsManager.getInstance(currentAccount).sendRequest(req)
				}

				if (checks[2]) {
					MessagesController.getInstance(currentAccount).deleteUserChannelHistory(chat!!, userFinal, chatFinal, 0)
				}
			}

			onDelete?.run()
		}

		if (count == 1) {
			builder.setTitle(activity.getString(R.string.DeleteSingleMessagesTitle))
		}
		else {
			builder.setTitle(LocaleController.formatString("DeleteMessagesTitle", R.string.DeleteMessagesTitle, LocaleController.formatPluralString("messages", count)))
		}

		if (chat != null && hasNotOut) {
			if (hasDeleteForAllCheck && myMessagesCount != count) {
				builder.setMessage(LocaleController.formatString("DeleteMessagesTextGroupPart", R.string.DeleteMessagesTextGroupPart, LocaleController.formatPluralString("messages", myMessagesCount)))
			}
			else if (count == 1) {
				builder.setMessage(activity.getString(R.string.AreYouSureDeleteSingleMessage))
			}
			else {
				builder.setMessage(activity.getString(R.string.AreYouSureDeleteFewMessages))
			}
		}
		else if (hasDeleteForAllCheck && !canDeleteInbox && myMessagesCount != count) {
			if (chat != null) {
				builder.setMessage(LocaleController.formatString("DeleteMessagesTextGroup", R.string.DeleteMessagesTextGroup, LocaleController.formatPluralString("messages", myMessagesCount)))
			}
			else {
				builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("DeleteMessagesText", R.string.DeleteMessagesText, LocaleController.formatPluralString("messages", myMessagesCount), UserObject.getFirstName(user))))
			}
		}
		else {
			if (chat != null && chat.megagroup && !scheduled) {
				if (count == 1) {
					builder.setMessage(activity.getString(R.string.AreYouSureDeleteSingleMessageMega))
				}
				else {
					builder.setMessage(activity.getString(R.string.AreYouSureDeleteFewMessagesMega))
				}
			}
			else {
				if (count == 1) {
					builder.setMessage(activity.getString(R.string.AreYouSureDeleteSingleMessage))
				}
				else {
					builder.setMessage(activity.getString(R.string.AreYouSureDeleteFewMessages))
				}
			}
		}

		builder.setNegativeButton(activity.getString(R.string.Cancel), null)

		builder.setOnPreDismissListener {
			hideDim?.run()
		}

		val dialog = builder.create()

		fragment.showDialog(dialog)

		val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView

		button?.setTextColor(activity.getColor(R.color.purple))
	}

	@JvmStatic
	fun createThemeCreateDialog(fragment: BaseFragment?, type: Int, switchToTheme: ThemeInfo, switchToAccent: ThemeAccent?) {
		val context = fragment?.getParentActivity() ?: return

		val editText = EditTextBoldCursor(context)
		editText.background = null
		editText.setLineColors(context.getColor(R.color.dark_gray), context.getColor(R.color.brand), context.getColor(R.color.purple))

		val builder = AlertDialog.Builder(context)
		builder.setTitle(context.getString(R.string.NewTheme))
		builder.setNegativeButton(context.getString(R.string.Cancel), null)
		builder.setPositiveButton(context.getString(R.string.Create)) { _, _ -> }

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.VERTICAL

		builder.setView(linearLayout)

		val message = TextView(context)

		if (type != 0) {
			message.text = AndroidUtilities.replaceTags(context.getString(R.string.EnterThemeNameEdit))
		}
		else {
			message.text = context.getString(R.string.EnterThemeName)
		}

		message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		message.setPadding(AndroidUtilities.dp(23f), AndroidUtilities.dp(12f), AndroidUtilities.dp(23f), AndroidUtilities.dp(6f))
		message.setTextColor(context.getColor(R.color.text))

		linearLayout.addView(message, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		editText.setTextColor(context.getColor(R.color.text))
		editText.setMaxLines(1)
		editText.setLines(1)
		editText.setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
		editText.setGravity(Gravity.LEFT or Gravity.TOP)
		editText.setSingleLine(true)
		editText.setImeOptions(EditorInfo.IME_ACTION_DONE)
		editText.setCursorColor(context.getColor(R.color.text))
		editText.setCursorSize(AndroidUtilities.dp(20f))
		editText.setCursorWidth(1.5f)
		editText.setPadding(0, AndroidUtilities.dp(4f), 0, 0)

		linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP or Gravity.LEFT, 24, 6, 24, 0))

		editText.setOnEditorActionListener { textView, _, _ ->
			AndroidUtilities.hideKeyboard(textView)
			false
		}

		editText.setText(generateThemeName(switchToAccent))
		editText.setSelection(editText.length())

		val alertDialog = builder.create()

		alertDialog.setOnShowListener {
			AndroidUtilities.runOnUIThread {
				editText.requestFocus()
				AndroidUtilities.showKeyboard(editText)
			}
		}

		fragment.showDialog(alertDialog)

		editText.requestFocus()

		alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
			if (fragment.getParentActivity() == null) {
				return@setOnClickListener
			}

			if (editText.length() == 0) {
				ApplicationLoader.applicationContext.vibrate()
				AndroidUtilities.shakeView(editText, 2f, 0)
				return@setOnClickListener
			}

			if (fragment is ThemePreviewActivity) {
				Theme.applyPreviousTheme()
				fragment.finishFragment()
			}

			if (switchToAccent != null) {
				switchToTheme.setCurrentAccentId(switchToAccent.id)
				Theme.refreshThemeColors()

				Utilities.searchQueue.postRunnable {
					AndroidUtilities.runOnUIThread {
						processCreate(editText, alertDialog, fragment)
					}
				}

				return@setOnClickListener
			}

			processCreate(editText, alertDialog, fragment)
		}
	}

	private fun processCreate(editText: EditTextBoldCursor, alertDialog: AlertDialog, fragment: BaseFragment?) {
		val parentActivity = fragment?.parentActivity ?: return

		AndroidUtilities.hideKeyboard(editText)

		val themeInfo = Theme.createNewTheme(editText.getText().toString())

		NotificationCenter.globalInstance.postNotificationName(NotificationCenter.themeListUpdated)

		val themeEditorView = ThemeEditorView()
		themeEditorView.show(parentActivity, themeInfo)

		alertDialog.dismiss()

		val preferences = MessagesController.getGlobalMainSettings()

		if (preferences.getBoolean("themehint", false)) {
			return
		}

		preferences.edit().putBoolean("themehint", true).commit()

		try {
			Toast.makeText(parentActivity, parentActivity.getString(R.string.CreateNewThemeHelp), Toast.LENGTH_LONG).show()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private fun generateThemeName(accent: ThemeAccent?): String {
		@Suppress("NAME_SHADOWING") var accent = accent
		val adjectives: List<String> = mutableListOf("Ancient", "Antique", "Autumn", "Baby", "Barely", "Baroque", "Blazing", "Blushing", "Bohemian", "Bubbly", "Burning", "Buttered", "Classic", "Clear", "Cool", "Cosmic", "Cotton", "Cozy", "Crystal", "Dark", "Daring", "Darling", "Dawn", "Dazzling", "Deep", "Deepest", "Delicate", "Delightful", "Divine", "Double", "Downtown", "Dreamy", "Dusky", "Dusty", "Electric", "Enchanted", "Endless", "Evening", "Fantastic", "Flirty", "Forever", "Frigid", "Frosty", "Frozen", "Gentle", "Heavenly", "Hyper", "Icy", "Infinite", "Innocent", "Instant", "Luscious", "Lunar", "Lustrous", "Magic", "Majestic", "Mambo", "Midnight", "Millenium", "Morning", "Mystic", "Natural", "Neon", "Night", "Opaque", "Paradise", "Perfect", "Perky", "Polished", "Powerful", "Rich", "Royal", "Sheer", "Simply", "Sizzling", "Solar", "Sparkling", "Splendid", "Spicy", "Spring", "Stellar", "Sugared", "Summer", "Sunny", "Super", "Sweet", "Tender", "Tenacious", "Tidal", "Toasted", "Totally", "Tranquil", "Tropical", "True", "Twilight", "Twinkling", "Ultimate", "Ultra", "Velvety", "Vibrant", "Vintage", "Virtual", "Warm", "Warmest", "Whipped", "Wild", "Winsome")
		val subjectives: List<String> = mutableListOf("Ambrosia", "Attack", "Avalanche", "Blast", "Bliss", "Blossom", "Blush", "Burst", "Butter", "Candy", "Carnival", "Charm", "Chiffon", "Cloud", "Comet", "Delight", "Dream", "Dust", "Fantasy", "Flame", "Flash", "Fire", "Freeze", "Frost", "Glade", "Glaze", "Gleam", "Glimmer", "Glitter", "Glow", "Grande", "Haze", "Highlight", "Ice", "Illusion", "Intrigue", "Jewel", "Jubilee", "Kiss", "Lights", "Lollypop", "Love", "Luster", "Madness", "Matte", "Mirage", "Mist", "Moon", "Muse", "Myth", "Nectar", "Nova", "Parfait", "Passion", "Pop", "Rain", "Reflection", "Rhapsody", "Romance", "Satin", "Sensation", "Silk", "Shine", "Shadow", "Shimmer", "Sky", "Spice", "Star", "Sugar", "Sunrise", "Sunset", "Sun", "Twist", "Unbound", "Velvet", "Vibrant", "Waters", "Wine", "Wink", "Wonder", "Zone")

		val colors = HashMap<Int, String>()
		colors[0x8e0000] = "Berry"
		colors[0xdec196] = "Brandy"
		colors[0x800b47] = "Cherry"
		colors[0xff7f50] = "Coral"
		colors[0xdb5079] = "Cranberry"
		colors[0xdc143c] = "Crimson"
		colors[0xe0b0ff] = "Mauve"
		colors[0xffc0cb] = "Pink"
		colors[0xff0000] = "Red"
		colors[0xff007f] = "Rose"
		colors[0x80461b] = "Russet"
		colors[0xff2400] = "Scarlet"
		colors[0xf1f1f1] = "Seashell"
		colors[0xff3399] = "Strawberry"
		colors[0xffbf00] = "Amber"
		colors[0xeb9373] = "Apricot"
		colors[0xfbe7b2] = "Banana"
		colors[0xa1c50a] = "Citrus"
		colors[0xb06500] = "Ginger"
		colors[0xffd700] = "Gold"
		colors[0xfde910] = "Lemon"
		colors[0xffa500] = "Orange"
		colors[0xffe5b4] = "Peach"
		colors[0xff6b53] = "Persimmon"
		colors[0xe4d422] = "Sunflower"
		colors[0xf28500] = "Tangerine"
		colors[0xffc87c] = "Topaz"
		colors[0xffff00] = "Yellow"
		colors[0x384910] = "Clover"
		colors[0x83aa5d] = "Cucumber"
		colors[0x50c878] = "Emerald"
		colors[0xb5b35c] = "Olive"
		colors[0x00ff00] = "Green"
		colors[0x00a86b] = "Jade"
		colors[0x29ab87] = "Jungle"
		colors[0xbfff00] = "Lime"
		colors[0x0bda51] = "Malachite"
		colors[0x98ff98] = "Mint"
		colors[0xaddfad] = "Moss"
		colors[0x315ba1] = "Azure"
		colors[0x0000ff] = "Blue"
		colors[0x0047ab] = "Cobalt"
		colors[0x4f69c6] = "Indigo"
		colors[0x017987] = "Lagoon"
		colors[0x71d9e2] = "Aquamarine"
		colors[0x120a8f] = "Ultramarine"
		colors[0x000080] = "Navy"
		colors[0x2f519e] = "Sapphire"
		colors[0x76d7ea] = "Sky"
		colors[0x008080] = "Teal"
		colors[0x40e0d0] = "Turquoise"
		colors[0x9966cc] = "Amethyst"
		colors[0x4d0135] = "Blackberry"
		colors[0x614051] = "Eggplant"
		colors[0xc8a2c8] = "Lilac"
		colors[0xb57edc] = "Lavender"
		colors[0xccccff] = "Periwinkle"
		colors[0x843179] = "Plum"
		colors[0x660099] = "Purple"
		colors[0xd8bfd8] = "Thistle"
		colors[0xda70d6] = "Orchid"
		colors[0x240a40] = "Violet"
		colors[0x3f2109] = "Bronze"
		colors[0x370202] = "Chocolate"
		colors[0x7b3f00] = "Cinnamon"
		colors[0x301f1e] = "Cocoa"
		colors[0x706555] = "Coffee"
		colors[0x796989] = "Rum"
		colors[0x4e0606] = "Mahogany"
		colors[0x782d19] = "Mocha"
		colors[0xc2b280] = "Sand"
		colors[0x882d17] = "Sienna"
		colors[0x780109] = "Maple"
		colors[0xf0e68c] = "Khaki"
		colors[0xb87333] = "Copper"
		colors[0xb94e48] = "Chestnut"
		colors[0xeed9c4] = "Almond"
		colors[0xfffdd0] = "Cream"
		colors[0xb9f2ff] = "Diamond"
		colors[0xa98307] = "Honey"
		colors[0xfffff0] = "Ivory"
		colors[0xeae0c8] = "Pearl"
		colors[0xeff2f3] = "Porcelain"
		colors[0xd1bea8] = "Vanilla"
		colors[0xffffff] = "White"
		colors[0x808080] = "Gray"
		colors[0x000000] = "Black"
		colors[0xe8f1d4] = "Chrome"
		colors[0x36454f] = "Charcoal"
		colors[0x0c0b1d] = "Ebony"
		colors[0xc0c0c0] = "Silver"
		colors[0xf5f5f5] = "Smoke"
		colors[0x262335] = "Steel"
		colors[0x4fa83d] = "Apple"
		colors[0x80b3c4] = "Glacier"
		colors[0xfebaad] = "Melon"
		colors[0xc54b8c] = "Mulberry"
		colors[0xa9c6c2] = "Opal"
		colors[0x54a5f8] = "Blue"

		if (accent == null) {
			val themeInfo = Theme.getCurrentTheme()
			accent = themeInfo.getAccent(false)
		}

		val color = if (accent != null && accent.accentColor != 0) {
			accent.accentColor
		}
		else {
			AndroidUtilities.calcDrawableColor(Theme.getCachedWallpaper())[0]
		}

		var minKey: String? = null
		var minValue = Int.MAX_VALUE
		val r1 = Color.red(color)
		val g1 = Color.green(color)
		val b1 = Color.blue(color)

		for ((value, name) in colors) {
			val r2 = Color.red(value)
			val g2 = Color.green(value)
			val b2 = Color.blue(value)
			val rMean = (r1 + r2) / 2
			val r = r1 - r2
			val g = g1 - g2
			val b = b1 - b2
			val d = ((512 + rMean) * r * r shr 8) + 4 * g * g + ((767 - rMean) * b * b shr 8)

			if (d < minValue) {
				minKey = name
				minValue = d
			}
		}

		val result = if (Utilities.random.nextInt() % 2 == 0) {
			adjectives[Utilities.random.nextInt(adjectives.size)] + " " + minKey
		}
		else {
			minKey + " " + subjectives[Utilities.random.nextInt(subjectives.size)]
		}

		return result
	}

	@SuppressLint("ClickableViewAccessibility")
	fun showPopupMenu(popupLayout: ActionBarPopupWindowLayout, anchorView: View?, offsetX: Int, offsetY: Int): ActionBarPopupWindow {
		val rect = Rect()

		val popupWindow = ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
		popupWindow.animationStyle = 0
		popupWindow.setAnimationEnabled(true)
		popupWindow.isOutsideTouchable = true
		popupWindow.isClippingEnabled = true
		popupWindow.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
		popupWindow.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
		popupWindow.isFocusable = true
		popupLayout.setFocusableInTouchMode(true)

		popupLayout.setOnKeyListener { _, keyCode, event ->
			if (keyCode == KeyEvent.KEYCODE_MENU && event.repeatCount == 0 && event.action == KeyEvent.ACTION_UP && popupWindow.isShowing) {
				popupWindow.dismiss()
				return@setOnKeyListener true
			}

			false
		}

		popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - AndroidUtilities.dp(40f), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, View.MeasureSpec.AT_MOST))

		popupWindow.showAsDropDown(anchorView!!, offsetX, offsetY)

		popupLayout.updateRadialSelectors()

		popupWindow.startAnimation()

		popupLayout.setOnTouchListener { v, event ->
			if (event.actionMasked == MotionEvent.ACTION_DOWN) {
				if (popupWindow.isShowing) {
					v.getHitRect(rect)

					if (!rect.contains(event.x.toInt(), event.y.toInt())) {
						popupWindow.dismiss()
					}
				}
			}

			false
		}

		return popupWindow
	}

	fun interface BlockDialogCallback {
		fun run(report: Boolean, delete: Boolean)
	}

	fun interface DatePickerDelegate {
		fun didSelectDate(year: Int, month: Int, dayOfMonth: Int)
	}

	fun interface ScheduleDatePickerDelegate {
		fun didSelectDate(notify: Boolean, scheduleDate: Int)
	}

	fun interface StatusUntilDatePickerDelegate {
		fun didSelectDate(date: Int)
	}

	fun interface AccountSelectDelegate {
		fun didSelectAccount(account: Int)
	}

	fun interface SoundFrequencyDelegate {
		fun didSelectValues(time: Int, minute: Int)
	}

	class ScheduleDatePickerColors @JvmOverloads constructor(val textColor: Int, val backgroundColor: Int, val iconColor: Int, val iconSelectorColor: Int, val subMenuTextColor: Int, val subMenuBackgroundColor: Int, val subMenuSelectorColor: Int, val buttonTextColor: Int = Theme.getColor(Theme.key_featuredStickers_buttonText), val buttonBackgroundColor: Int = Theme.getColor(Theme.key_featuredStickers_addButton), val buttonBackgroundPressedColor: Int = Theme.getColor(Theme.key_featuredStickers_addButtonPressed)) {
		constructor() : this(ApplicationLoader.applicationContext.getColor(R.color.text), ApplicationLoader.applicationContext.getColor(R.color.background), ApplicationLoader.applicationContext.getColor(R.color.dark_gray), ApplicationLoader.applicationContext.getColor(R.color.light_background), ApplicationLoader.applicationContext.getColor(R.color.text), ApplicationLoader.applicationContext.getColor(R.color.background), ApplicationLoader.applicationContext.getColor(R.color.light_background), ApplicationLoader.applicationContext.getColor(R.color.white), ApplicationLoader.applicationContext.getColor(R.color.brand), ApplicationLoader.applicationContext.getColor(R.color.darker_brand))
	}
}
