/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components.voip

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildVars
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DownloadController
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.UserConfig
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.voip.Instance
import org.telegram.messenger.voip.VoIPPreNotificationService
import org.telegram.messenger.voip.VoIPService
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.chatId
import org.telegram.tgnet.rtmpStream
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.CheckBoxCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.BetterRatingView
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.JoinCallAlert
import org.telegram.ui.Components.JoinCallByUrlAlert
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.LaunchActivity
import org.telegram.ui.group.GroupCallActivity
import java.io.File
import java.util.Calendar
import java.util.Locale
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
object VoIPHelper {
	const val REQUEST_CODE_RECORD_AUDIO = 101
	const val REQUEST_CODE_CAMERA = 102
	const val REQUEST_CODE_MERGED = 103
	private const val VOIP_SUPPORT_ID = 4244000
	var lastCallTime: Long = 0

	@JvmStatic
	fun startCall(user: TLRPC.User?, videoCall: Boolean, canVideoCall: Boolean, activity: Activity?, userFull: TLRPC.TLUserFull?, accountInstance: AccountInstance) {
		if (activity == null) {
			return
		}

		if (userFull?.phoneCallsPrivate == true) {
			AlertDialog.Builder(activity).setTitle(activity.getString(R.string.VoipFailed)).setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("CallNotAvailable", R.string.CallNotAvailable, ContactsController.formatName(user?.firstName, user?.lastName)))).setPositiveButton(activity.getString(R.string.OK), null).show()
			return
		}

		if (ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState() != ConnectionsManager.ConnectionStateConnected) {
			val isAirplaneMode = Settings.System.getInt(activity.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
			val bldr = AlertDialog.Builder(activity).setTitle(if (isAirplaneMode) activity.getString(R.string.VoipOfflineAirplaneTitle) else activity.getString(R.string.VoipOfflineTitle)).setMessage(if (isAirplaneMode) activity.getString(R.string.VoipOfflineAirplane) else activity.getString(R.string.VoipOffline)).setPositiveButton(activity.getString(R.string.OK), null)

			if (isAirplaneMode) {
				val settingsIntent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)

				if (settingsIntent.resolveActivity(activity.packageManager) != null) {
					bldr.setNeutralButton(activity.getString(R.string.VoipOfflineOpenSettings)) { _, _ ->
						activity.startActivity(settingsIntent)
					}
				}
			}

			try {
				bldr.show()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			return
		}

		val permissions = mutableListOf<String>()

		if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.RECORD_AUDIO)
		}

		if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.READ_PHONE_STATE)
		}

		if (videoCall && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.CAMERA)
		}

		if (permissions.isEmpty()) {
			initiateCall(user, null, null, videoCall, canVideoCall, false, null, activity, null, accountInstance)
		}
		else {
			activity.requestPermissions(permissions.toTypedArray(), if (videoCall) REQUEST_CODE_CAMERA else REQUEST_CODE_RECORD_AUDIO)
		}
	}

	fun startCall(chat: Chat?, hash: String?, createCall: Boolean, activity: Activity?, fragment: BaseFragment?, accountInstance: AccountInstance) {
		startCall(chat, hash, createCall, null, activity, fragment, accountInstance)
	}

	@JvmStatic
	fun startCall(chat: Chat?, hash: String?, createCall: Boolean, checkJoiner: Boolean?, activity: Activity?, fragment: BaseFragment?, accountInstance: AccountInstance) {
		if (activity == null) {
			return
		}

		if (ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState() != ConnectionsManager.ConnectionStateConnected) {
			val isAirplaneMode = Settings.System.getInt(activity.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
			val bldr = AlertDialog.Builder(activity).setTitle(if (isAirplaneMode) activity.getString(R.string.VoipOfflineAirplaneTitle) else activity.getString(R.string.VoipOfflineTitle)).setMessage(if (isAirplaneMode) activity.getString(R.string.VoipGroupOfflineAirplane) else activity.getString(R.string.VoipGroupOffline)).setPositiveButton(activity.getString(R.string.OK), null)

			if (isAirplaneMode) {
				val settingsIntent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)

				if (settingsIntent.resolveActivity(activity.packageManager) != null) {
					bldr.setNeutralButton(activity.getString(R.string.VoipOfflineOpenSettings)) { _, _ ->
						activity.startActivity(settingsIntent)
					}
				}
			}

			try {
				bldr.show()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			return
		}

		val permissions = mutableListOf<String>()
		val call = accountInstance.messagesController.getGroupCall(chat?.id, false)

		if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && !(call != null && call.call?.rtmpStream == true)) {
			permissions.add(Manifest.permission.RECORD_AUDIO)
		}

		if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.READ_PHONE_STATE)
		}

		if (permissions.isEmpty()) {
			initiateCall(null, chat, hash, videoCall = false, canVideoCall = false, createCall, checkJoiner, activity, fragment, accountInstance)
		}
		else {
			activity.requestPermissions(permissions.toTypedArray(), REQUEST_CODE_MERGED)
		}
	}

	private fun initiateCall(user: TLRPC.User?, chat: Chat?, hash: String?, videoCall: Boolean, canVideoCall: Boolean, createCall: Boolean, checkJoiner: Boolean?, activity: Activity?, fragment: BaseFragment?, accountInstance: AccountInstance) {
		if (activity == null || (user == null && chat == null)) {
			return
		}

		val voIPService = VoIPService.sharedInstance

		if (voIPService != null) {
			val newId = user?.id ?: -chat!!.id
			val callerId = VoIPService.sharedInstance?.getCallerId() ?: 0

			if (callerId != newId || voIPService.getAccount() != accountInstance.currentAccount) {
				val oldName: String
				val key1: String
				val key2: Int

				if (callerId > 0) {
					val callUser = voIPService.user!!

					oldName = ContactsController.formatName(callUser.firstName, callUser.lastName)

					if (newId > 0) {
						key1 = "VoipOngoingAlert"
						key2 = R.string.VoipOngoingAlert
					}
					else {
						key1 = "VoipOngoingAlert2"
						key2 = R.string.VoipOngoingAlert2
					}
				}
				else {
					val callChat = voIPService.getChat()!!
					oldName = callChat.title ?: ""

					if (newId > 0) {
						key1 = "VoipOngoingChatAlert2"
						key2 = R.string.VoipOngoingChatAlert2
					}
					else {
						key1 = "VoipOngoingChatAlert"
						key2 = R.string.VoipOngoingChatAlert
					}
				}

				val newName = if (user != null) {
					ContactsController.formatName(user.firstName, user.lastName)
				}
				else {
					chat?.title
				}

				AlertDialog.Builder(activity).setTitle(if (callerId < 0) activity.getString(R.string.VoipOngoingChatAlertTitle) else activity.getString(R.string.VoipOngoingAlertTitle)).setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(key1, key2, oldName, newName))).setPositiveButton(activity.getString(R.string.OK)) { _, _ ->
					if (VoIPService.sharedInstance != null) {
						VoIPService.sharedInstance?.hangUp {
							lastCallTime = 0
							doInitiateCall(user, chat, hash, null, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, checkJoiner = true, checkAnonymous = true)
						}
					}
					else {
						doInitiateCall(user, chat, hash, null, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, checkJoiner = true, checkAnonymous = true)
					}
				}.setNegativeButton(activity.getString(R.string.Cancel), null).show()
			}
			else {
				if (user != null || activity !is LaunchActivity) {
					activity.startActivity(Intent(activity, LaunchActivity::class.java).setAction(if (user != null) VoIPPreNotificationService.VOIP_ACTION else VoIPPreNotificationService.VOIP_CHAT_ACTION))
				}
				else {
					if (!hash.isNullOrEmpty()) {
						voIPService.setGroupCallHash(hash)
					}

					GroupCallActivity.create(activity as LaunchActivity?, AccountInstance.getInstance(UserConfig.selectedAccount), null, null, false, null)
				}
			}
		}
		else if (VoIPService.callIShouldHavePutIntoIntent == null) {
			doInitiateCall(user, chat, hash, null, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, checkJoiner ?: true, true)
		}
	}

	private fun doInitiateCall(user: TLRPC.User?, chat: Chat?, hash: String?, peer: InputPeer?, hasFewPeers: Boolean, videoCall: Boolean, canVideoCall: Boolean, createCall: Boolean, activity: Activity?, fragment: BaseFragment?, accountInstance: AccountInstance, checkJoiner: Boolean, checkAnonymous: Boolean) {
		if (activity == null || (user == null && chat == null)) {
			return
		}

		if (SystemClock.elapsedRealtime() - lastCallTime < (if (chat != null) 200 else 2000)) {
			return
		}

		if (checkJoiner && chat != null && !createCall) {
			val chatFull = accountInstance.messagesController.getChatFull(chat.id)

			if (chatFull?.groupcallDefaultJoinAs != null) {
				val did = MessageObject.getPeerId(chatFull.groupcallDefaultJoinAs)
				val inputPeer = accountInstance.messagesController.getInputPeer(did)

				JoinCallAlert.checkFewUsers(activity, -chat.id, accountInstance) { param ->
					if (!param && hash != null) {
						val alert = object : JoinCallByUrlAlert(activity, chat) {
							override fun onJoin() {
								doInitiateCall(user, chat, hash, inputPeer, true, videoCall, canVideoCall, false, activity, fragment, accountInstance, checkJoiner = false, checkAnonymous = false)
							}
						}

						fragment?.showDialog(alert)
					}
					else {
						doInitiateCall(user, chat, hash, inputPeer, !param, videoCall, canVideoCall, false, activity, fragment, accountInstance, checkJoiner = false, checkAnonymous = false)
					}
				}

				return
			}
		}

		if (checkJoiner && chat != null) {
			JoinCallAlert.open(activity, -chat.id, accountInstance, fragment, if (createCall) JoinCallAlert.TYPE_CREATE else JoinCallAlert.TYPE_JOIN, null) { selectedPeer, hasFew, schedule ->
				if (createCall && schedule) {
					GroupCallActivity.create(activity as LaunchActivity?, accountInstance, chat, selectedPeer, hasFew, hash)
				}
				else if (!hasFew && hash != null) {
					val alert = object : JoinCallByUrlAlert(activity, chat) {
						override fun onJoin() {
							doInitiateCall(user, chat, hash, selectedPeer, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, checkJoiner = false, checkAnonymous = true)
						}
					}

					fragment?.showDialog(alert)
				}
				else {
					doInitiateCall(user, chat, hash, selectedPeer, hasFew, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, checkJoiner = false, checkAnonymous = true)
				}
			}

			return
		}

		if (checkAnonymous && !hasFewPeers && peer is TLRPC.TLInputPeerUser && ChatObject.shouldSendAnonymously(chat) && (!ChatObject.isChannel(chat) || chat.megagroup)) {
			AlertDialog.Builder(activity).setTitle(if (ChatObject.isChannelOrGiga(chat)) activity.getString(R.string.VoipChannelVoiceChat) else activity.getString(R.string.VoipGroupVoiceChat)).setMessage(if (ChatObject.isChannelOrGiga(chat)) activity.getString(R.string.VoipChannelJoinAnonymouseAlert) else activity.getString(R.string.VoipGroupJoinAnonymouseAlert)).setPositiveButton(activity.getString(R.string.VoipChatJoin)) { _, _ ->
				doInitiateCall(user, chat, hash, peer, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, checkJoiner = false, checkAnonymous = false)
			}.setNegativeButton(activity.getString(R.string.Cancel), null).show()

			return
		}

		if (chat != null && peer != null) {
			val chatFull = accountInstance.messagesController.getChatFull(chat.id)

			if (chatFull != null) {
				when (peer) {
					is TLRPC.TLInputPeerUser -> {
						chatFull.groupcallDefaultJoinAs = TLRPC.TLPeerUser().also { it.userId = peer.userId }
					}

					is TLRPC.TLInputPeerChat -> {
						chatFull.groupcallDefaultJoinAs = TLRPC.TLPeerChat().also { it.chatId = peer.chatId }
					}

					is TLRPC.TLInputPeerChannel -> {
						chatFull.groupcallDefaultJoinAs = TLRPC.TLPeerChannel().also { it.channelId = peer.channelId }
					}
				}

				if (chatFull is TLRPC.TLChatFull) {
					chatFull.flags = chatFull.flags or 32768
				}
				else {
					chatFull.flags = chatFull.flags or 67108864
				}
			}
		}

		if (chat != null && !createCall) {
			val call = accountInstance.messagesController.getGroupCall(chat.id, false)

			if (call != null && call.isScheduled) {
				GroupCallActivity.create(activity as LaunchActivity?, accountInstance, chat, peer, hasFewPeers, hash)
				return
			}
		}

		lastCallTime = SystemClock.elapsedRealtime()

		val intent = Intent(activity, VoIPService::class.java)

		if (user != null) {
			intent.putExtra("user_id", user.id)
		}
		else {
			intent.putExtra("chat_id", chat!!.id)
			intent.putExtra("createGroupCall", createCall)
			intent.putExtra("hasFewPeers", hasFewPeers)
			intent.putExtra("hash", hash)

			if (peer != null) {
				intent.putExtra("peerChannelId", peer.channelId)
				intent.putExtra("peerChatId", peer.chatId)
				intent.putExtra("peerUserId", peer.userId)
				intent.putExtra("peerAccessHash", peer.accessHash)
			}
		}

		intent.putExtra("is_outgoing", true)
		intent.putExtra("start_incall_activity", true)
		intent.putExtra("video_call", videoCall)
		intent.putExtra("can_video_call", canVideoCall)
		intent.putExtra("account", UserConfig.selectedAccount)

		try {
			activity.startService(intent)
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}
	}

	@JvmStatic
	fun permissionDenied(activity: Activity?, onFinish: Runnable?, code: Int) {
		if (activity == null) {
			return
		}

		if ((activity as? LaunchActivity)?.visibleDialog != null) {
			return
		}

		val mergedRequest = (code == REQUEST_CODE_CAMERA)

		if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) || mergedRequest && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
			val dlg = AlertDialog.Builder(activity).setMessage(AndroidUtilities.replaceTags(if (mergedRequest) activity.getString(R.string.VoipNeedMicCameraPermissionWithHint) else activity.getString(R.string.VoipNeedMicPermissionWithHint))).setPositiveButton(activity.getString(R.string.Settings)) { _, _ ->
				val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
				val uri = Uri.fromParts("package", activity.packageName, null)
				intent.data = uri
				activity.startActivity(intent)
			}.setNegativeButton(activity.getString(R.string.ContactsPermissionAlertNotNow), null).setOnDismissListener {
				onFinish?.run()
			}.setTopAnimation(if (mergedRequest) R.raw.permission_request_camera else R.raw.permission_request_microphone, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, ResourcesCompat.getColor(activity.resources, R.color.brand, null))

			dlg.show()
		}
	}

	@JvmStatic
	val logsDir: File
		get() {
			val logsDir = File(ApplicationLoader.applicationContext.cacheDir, "voip_logs")

			if (!logsDir.exists()) {
				logsDir.mkdirs()
			}

			return logsDir
		}

	fun canRateCall(call: TLRPC.TLMessageActionPhoneCall?): Boolean {
		contract {
			returns(true) implies (call != null)
		}

		if (call == null) {
			return false
		}

		if (call.reason !is TLRPC.TLPhoneCallDiscardReasonBusy && call.reason !is TLRPC.TLPhoneCallDiscardReasonMissed) {
			val prefs = MessagesController.getNotificationsSettings(UserConfig.selectedAccount) // always called from chat UI
			val hashes = prefs.getStringSet("calls_access_hashes", setOf()) ?: setOf()

			for (hash in hashes) {
				val d = hash.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

				if (d.size < 2) {
					continue
				}

				if (d[0] == call.callId.toString() + "") {
					return true
				}
			}
		}

		return false
	}

	fun showRateAlert(context: Context, call: TLRPC.TLMessageActionPhoneCall?) {
		if (call == null) {
			return
		}

		val prefs = MessagesController.getNotificationsSettings(UserConfig.selectedAccount) // always called from chat UI
		val hashes = prefs.getStringSet("calls_access_hashes", setOf()) ?: setOf()

		for (hash in hashes) {
			val d = hash.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

			if (d.size < 2) {
				continue
			}

			if (d[0] == call.callId.toString() + "") {
				try {
					val accessHash = d[1].toLong()
					showRateAlert(context, null, call.video, call.callId, accessHash, UserConfig.selectedAccount, true)
				}
				catch (e: Exception) {
					// ignored
				}

				return
			}
		}
	}

	@JvmStatic
	fun showRateAlert(context: Context, onDismiss: Runnable?, isVideo: Boolean, callID: Long, accessHash: Long, account: Int, userInitiative: Boolean) {
		val log = getLogFile(callID)
		val page = intArrayOf(0)

		val alertView = LinearLayout(context)
		alertView.orientation = LinearLayout.VERTICAL

		val pad = AndroidUtilities.dp(16f)

		alertView.setPadding(pad, pad, pad, 0)

		val text = TextView(context)
		text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
		text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
		text.gravity = Gravity.CENTER
		text.text = context.getString(R.string.VoipRateCallAlert)

		alertView.addView(text)

		val bar = BetterRatingView(context)

		alertView.addView(bar, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0))

		val problemsWrap = LinearLayout(context)
		problemsWrap.orientation = LinearLayout.VERTICAL

		val problemCheckboxClickListener = View.OnClickListener {
			val check = it as? CheckBoxCell
			check?.setChecked(!check.isChecked, true)
		}

		val problems = arrayOf(if (isVideo) "distorted_video" else null, if (isVideo) "pixelated_video" else null, "echo", "noise", "interruptions", "distorted_speech", "silent_local", "silent_remote", "dropped")

		for (i in problems.indices) {
			if (problems[i] == null) {
				continue
			}

			val check = CheckBoxCell(context, 1)
			check.clipToPadding = false
			check.tag = problems[i]

			val label = when (i) {
				0 -> context.getString(R.string.RateCallVideoDistorted)
				1 -> context.getString(R.string.RateCallVideoPixelated)
				2 -> context.getString(R.string.RateCallEcho)
				3 -> context.getString(R.string.RateCallNoise)
				4 -> context.getString(R.string.RateCallInterruptions)
				5 -> context.getString(R.string.RateCallDistorted)
				6 -> context.getString(R.string.RateCallSilentLocal)
				7 -> context.getString(R.string.RateCallSilentRemote)
				8 -> context.getString(R.string.RateCallDropped)
				else -> null
			}

			check.setText(label, null, checked = false, false)
			check.setOnClickListener(problemCheckboxClickListener)
			check.tag = problems[i]

			problemsWrap.addView(check)
		}

		alertView.addView(problemsWrap, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -8f, 0f, -8f, 0f))

		problemsWrap.visibility = View.GONE

		val commentBox = EditTextBoldCursor(context)
		commentBox.hint = context.getString(R.string.VoipFeedbackCommentHint)
		commentBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
		commentBox.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
		commentBox.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint))
		commentBox.background = null
		commentBox.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_dialogTextRed2))
		commentBox.setPadding(0, AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f))
		commentBox.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
		commentBox.visibility = View.GONE

		alertView.addView(commentBox, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8f, 8f, 8f, 0f))

		val includeLogs = booleanArrayOf(true)
		val checkbox = CheckBoxCell(context, 1)

		val checkClickListener = View.OnClickListener {
			includeLogs[0] = !includeLogs[0]
			checkbox.setChecked(includeLogs[0], true)
		}

		checkbox.setText(context.getString(R.string.CallReportIncludeLogs), null, checked = true, divider = false)
		checkbox.clipToPadding = false
		checkbox.setOnClickListener(checkClickListener)

		alertView.addView(checkbox, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -8f, 0f, -8f, 0f))

		val logsText = TextView(context)
		logsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
		logsText.setTextColor(Theme.getColor(Theme.key_dialogTextGray3))
		logsText.text = context.getString(R.string.CallReportLogsExplain)
		logsText.setPadding(AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f), 0)
		logsText.setOnClickListener(checkClickListener)

		alertView.addView(logsText)

		checkbox.visibility = View.GONE
		logsText.visibility = View.GONE

		if (!log.exists()) {
			includeLogs[0] = false
		}

		val alert = AlertDialog.Builder(context).setTitle(context.getString(R.string.CallMessageReportProblem)).setView(alertView).setPositiveButton(context.getString(R.string.Send)) { _, _ ->
			// unused
		}.setNegativeButton(context.getString(R.string.Cancel), null).setOnDismissListener {
			onDismiss?.run()
		}.create()

		if (BuildVars.logsEnabled && log.exists()) {
			alert.setNeutralButton("Send log") { _, _ ->
				val intent = Intent(context, LaunchActivity::class.java)
				intent.action = Intent.ACTION_SEND
				intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(log))
				context.startActivity(intent)
			}
		}

		alert.show()
		alert.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

		val btn = alert.getButton(DialogInterface.BUTTON_POSITIVE)
		btn?.isEnabled = false

		bar.setOnRatingChangeListener { rating ->
			btn?.isEnabled = rating > 0

//			commentBox.setHint(rating<4 ? LocaleController.getString("CallReportHint", R.string.CallReportHint) : LocaleController.getString("VoipFeedbackCommentHint", R.string.VoipFeedbackCommentHint));
//			commentBox.setVisibility(rating < 5 && rating > 0 ? View.VISIBLE : View.GONE);
//			if (commentBox.getVisibility() == View.GONE) {
//				((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(commentBox.getWindowToken(), 0);
//			}

			(btn as? TextView)?.text = (if (rating < 4) context.getString(R.string.Next) else context.getString(R.string.Send)).uppercase()
		}

		btn?.setOnClickListener {
			val rating = bar.rating

			if (rating >= 4 || page[0] == 1) {
				val currentAccount = UserConfig.selectedAccount

				val req = TLRPC.TLPhoneSetCallRating()
				req.rating = bar.rating

				val problemTags = ArrayList<String?>()

				for (i in 0 until problemsWrap.childCount) {
					val check = problemsWrap.getChildAt(i) as CheckBoxCell

					if (check.isChecked) {
						problemTags.add("#" + check.tag)
					}
				}

				if (req.rating < 5) {
					req.comment = commentBox.text.toString()
				}
				else {
					req.comment = ""
				}

				if (problemTags.isNotEmpty() && !includeLogs[0]) {
					req.comment += " " + TextUtils.join(" ", problemTags)
				}

				req.peer = TLRPC.TLInputPhoneCall()
				req.peer?.accessHash = accessHash
				req.peer?.id = callID
				req.userInitiative = userInitiative

				ConnectionsManager.getInstance(account).sendRequest(req) { response, _ ->
					if (response is TLRPC.TLUpdates) {
						MessagesController.getInstance(currentAccount).processUpdates(response, false)
					}

					if (includeLogs[0] && log.exists() && req.rating < 4) {
						val accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount)
						SendMessagesHelper.prepareSendingDocument(accountInstance, log.absolutePath, log.absolutePath, null, TextUtils.join(" ", problemTags), "text/plain", VOIP_SUPPORT_ID.toLong(), null, null, null, null, true, 0)
						Toast.makeText(context, R.string.CallReportSent, Toast.LENGTH_LONG).show()
					}
				}

				alert.dismiss()
			}
			else {
				page[0] = 1

				bar.visibility = View.GONE
				//text.setText(LocaleController.getString("CallReportHint", R.string.CallReportHint));
				text.visibility = View.GONE
				alert.setTitle(context.getString(R.string.CallReportHint))
				commentBox.visibility = View.VISIBLE

				if (log.exists()) {
					checkbox.visibility = View.VISIBLE
					logsText.visibility = View.VISIBLE
				}

				problemsWrap.visibility = View.VISIBLE

				(btn as TextView).text = context.getString(R.string.Send).uppercase()
			}
		}
	}

	private fun getLogFile(callID: Long): File {
		if (BuildVars.logsEnabled) {
			val debugLogsDir = File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "logs")
			val logs = debugLogsDir.list()

			if (logs != null) {
				for (log in logs) {
					if (log.endsWith("voip$callID.txt")) {
						return File(debugLogsDir, log)
					}
				}
			}
		}

		return File(logsDir, "$callID.log")
	}

	fun showCallDebugSettings(context: Context) {
		val preferences = MessagesController.getGlobalMainSettings()

		val ll = LinearLayout(context)
		ll.orientation = LinearLayout.VERTICAL

		val warning = TextView(context)
		warning.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		warning.text = context.getString(R.string.if_you_know_what_they_do)
		warning.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))

		ll.addView(warning, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16f, 8f, 16f, 8f))

		val tcpCell = TextCheckCell(context)
		tcpCell.setTextAndCheck("Force TCP", preferences.getBoolean("dbg_force_tcp_in_calls", false), false)

		tcpCell.setOnClickListener {
			val force = preferences.getBoolean("dbg_force_tcp_in_calls", false)
			preferences.edit { putBoolean("dbg_force_tcp_in_calls", !force) }
			tcpCell.isChecked = !force
		}

		ll.addView(tcpCell)

		if (BuildVars.logsEnabled) {
			val dumpCell = TextCheckCell(context)
			dumpCell.setTextAndCheck("Dump detailed stats", preferences.getBoolean("dbg_dump_call_stats", false), false)

			dumpCell.setOnClickListener {
				val force = preferences.getBoolean("dbg_dump_call_stats", false)
				preferences.edit { putBoolean("dbg_dump_call_stats", !force) }
				dumpCell.isChecked = !force
			}

			ll.addView(dumpCell)
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val connectionServiceCell = TextCheckCell(context)
			connectionServiceCell.setTextAndCheck("Enable ConnectionService", preferences.getBoolean("dbg_force_connection_service", false), false)

			connectionServiceCell.setOnClickListener {
				val force = preferences.getBoolean("dbg_force_connection_service", false)
				preferences.edit { putBoolean("dbg_force_connection_service", !force) }
				connectionServiceCell.isChecked = !force
			}

			ll.addView(connectionServiceCell)
		}

		AlertDialog.Builder(context).setTitle(context.getString(R.string.DebugMenuCallSettings)).setView(ll).show()
	}

	@JvmStatic
	val dataSavingDefault: Int
		get() {
			val low = DownloadController.getInstance(0).lowPreset.lessCallData
			val medium = DownloadController.getInstance(0).mediumPreset.lessCallData
			val high = DownloadController.getInstance(0).highPreset.lessCallData

			if (!low && !medium && !high) {
				return Instance.DATA_SAVING_NEVER
			}
			else if (low && !medium && !high) {
				return Instance.DATA_SAVING_ROAMING
			}
			else if (low && medium && !high) {
				return Instance.DATA_SAVING_MOBILE
			}
			else if (low && medium && high) {
				return Instance.DATA_SAVING_ALWAYS
			}

			FileLog.w("Invalid call data saving preset configuration: $low/$medium/$high")

			return Instance.DATA_SAVING_NEVER
		}

	fun getLogFilePath(name: String?): String {
		val c = Calendar.getInstance()
		val externalFilesDir = ApplicationLoader.applicationContext.getExternalFilesDir(null)
		return File(externalFilesDir, String.format(Locale.US, "logs/%02d_%02d_%04d_%02d_%02d_%02d_%s.txt", c[Calendar.DATE], c[Calendar.MONTH] + 1, c[Calendar.YEAR], c[Calendar.HOUR_OF_DAY], c[Calendar.MINUTE], c[Calendar.SECOND], name)).absolutePath
	}

	fun getLogFilePath(callId: Long, stats: Boolean): String {
		val logsDir = logsDir

		if (!BuildVars.logsEnabled) {
			val allLogs = logsDir.listFiles()

			if (allLogs != null) {
				val logs = ArrayList(listOf(*allLogs))

				while (logs.size > 20) {
					var oldest = logs[0]

					for (file in logs) {
						if (file.name.endsWith(".log") && file.lastModified() < oldest.lastModified()) {
							oldest = file
						}
					}

					oldest.delete()
					logs.remove(oldest)
				}
			}
		}

		return if (stats) {
			File(logsDir, callId.toString() + "_stats.log").absolutePath
		}
		else {
			File(logsDir, "$callId.log").absolutePath
		}
	}

	fun showGroupCallAlert(fragment: BaseFragment?, currentChat: Chat, accountInstance: AccountInstance) {
		if (fragment == null || fragment.parentActivity == null) {
			return
		}

		JoinCallAlert.checkFewUsers(fragment.parentActivity, -currentChat.id, accountInstance) {
			startCall(currentChat, null, true, fragment.parentActivity, fragment, accountInstance)
		}
	}
}
