/*
 * This is the source code of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Telegram, 2024.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger.voip

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Vibrator
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ContactsController
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.XiaomiUtilities
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.PhoneCall
import org.telegram.tgnet.TLRPC.TL_inputPhoneCall
import org.telegram.tgnet.TLRPC.TL_phoneCallDiscardReasonBusy
import org.telegram.tgnet.TLRPC.TL_phoneCallDiscardReasonDisconnect
import org.telegram.tgnet.TLRPC.TL_phoneCallDiscardReasonHangup
import org.telegram.tgnet.TLRPC.TL_phoneCallDiscardReasonMissed
import org.telegram.tgnet.TLRPC.TL_phoneCallDiscarded
import org.telegram.tgnet.TLRPC.TL_phone_discardCall
import org.telegram.tgnet.TLRPC.TL_phone_receivedCall
import org.telegram.tgnet.TLRPC.TL_updates
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.LaunchActivity
import org.telegram.ui.VoIPFragment
import org.telegram.ui.VoIPPermissionActivity

object VoIPPreNotificationService {
	const val VOIP_ACTION = "voip"
	const val VOIP_CHAT_ACTION = "voip_chat"
	const val VOIP_ANSWER_ACTION = "voip_answer"

	private fun makeNotification(context: Context, account: Int, userId: Long, callId: Long, video: Boolean): Notification? {
		if (Build.VERSION.SDK_INT < 33) {
			return null
		}

		val user = MessagesController.getInstance(account).getUser(userId)
		val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		val intent = Intent(context, LaunchActivity::class.java).setAction(VOIP_ACTION)
		val builder = Notification.Builder(context).setContentTitle(context.getString(if (video) R.string.VoipInVideoCallBranding else R.string.VoipInCallBranding)).setSmallIcon(R.drawable.ic_call).setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT))

		val nprefs = MessagesController.getGlobalNotificationsSettings()
		var chanIndex = nprefs.getInt("calls_notification_channel", 0)
		var oldChannel = nm.getNotificationChannel("incoming_calls2$chanIndex")

		if (oldChannel != null) {
			nm.deleteNotificationChannel(oldChannel.id)
		}

		oldChannel = nm.getNotificationChannel("incoming_calls3$chanIndex")

		if (oldChannel != null) {
			nm.deleteNotificationChannel(oldChannel.id)
		}

		val existingChannel = nm.getNotificationChannel("incoming_calls4$chanIndex")
		var needCreate = true

		if (existingChannel != null) {
			if (existingChannel.importance < NotificationManager.IMPORTANCE_HIGH || existingChannel.sound != null) {
				FileLog.d("User messed up the notification channel; deleting it and creating a proper one")

				nm.deleteNotificationChannel("incoming_calls4$chanIndex")

				chanIndex++

				nprefs.edit().putInt("calls_notification_channel", chanIndex).apply()
			}
			else {
				needCreate = false
			}
		}

		if (needCreate) {
			val attrs = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).setLegacyStreamType(AudioManager.STREAM_RING).setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build()
			val chan = NotificationChannel("incoming_calls4$chanIndex", context.getString(R.string.IncomingCallsSystemSetting), NotificationManager.IMPORTANCE_HIGH)

			try {
				chan.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, attrs)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			chan.enableVibration(false)
			chan.enableLights(false)
			chan.setBypassDnd(true)

			try {
				nm.createNotificationChannel(chan)
			}
			catch (e: Exception) {
				FileLog.e(e)
				return null
			}
		}

		builder.setChannelId("incoming_calls4$chanIndex")

		val endIntent = Intent(context, VoIPActionsReceiver::class.java)
		endIntent.setAction(context.packageName + ".DECLINE_CALL")
		endIntent.putExtra("call_id", callId)

		val endPendingIntent = PendingIntent.getBroadcast(context, 0, endIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)

		val answerIntent = Intent(context, VoIPActionsReceiver::class.java)
		answerIntent.setAction(context.packageName + ".ANSWER_CALL")
		answerIntent.putExtra("call_id", callId)

		val answerPendingIntent = PendingIntent.getActivity(context, 0, Intent(context, LaunchActivity::class.java).setAction(VOIP_ANSWER_ACTION), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)

		builder.setPriority(Notification.PRIORITY_MAX)
		builder.setShowWhen(false)
		builder.setColor(-0xd35a20)
		builder.setVibrate(LongArray(0))
		builder.setCategory(Notification.CATEGORY_CALL)
		builder.setFullScreenIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_MUTABLE), true)

		val hideIntent = Intent(ApplicationLoader.applicationContext, VoIPActionsReceiver::class.java)
		hideIntent.setAction(context.packageName + ".HIDE_CALL")

		val hidePendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 0, hideIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
		builder.setDeleteIntent(hidePendingIntent)

		val avatar = VoIPService.getRoundAvatarBitmap(user)
		var personName = ContactsController.formatName(user)

		if (personName.isEmpty()) {
			personName = "___"
		}

		val person = Person.Builder().setName(personName).setIcon(Icon.createWithAdaptiveBitmap(avatar)).build()
		val notificationStyle = Notification.CallStyle.forIncomingCall(person, endPendingIntent, answerPendingIntent)

		builder.setStyle(notificationStyle)

		return builder.build()
	}

	private var ringtonePlayer: MediaPlayer? = null
	private var vibrator: Vibrator? = null

	private fun startRinging(context: Context, account: Int, userId: Long) {
		val prefs = MessagesController.getNotificationsSettings(account)
		val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
		val needRing = am.ringerMode != AudioManager.RINGER_MODE_SILENT
		val isHeadsetPlugged = am.isWiredHeadsetOn

		if (needRing) {
			if (ringtonePlayer != null) {
				return
			}

			synchronized(this) {
				if (ringtonePlayer != null) {
					return
				}

				ringtonePlayer = MediaPlayer()
				ringtonePlayer?.setOnPreparedListener {
					try {
						ringtonePlayer?.start()
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}

				ringtonePlayer?.isLooping = true

				if (isHeadsetPlugged) {
					ringtonePlayer?.setAudioStreamType(AudioManager.STREAM_VOICE_CALL)
				}
				else {
					ringtonePlayer?.setAudioStreamType(AudioManager.STREAM_RING)
				}

				try {
					val notificationUri = if (prefs.getBoolean("custom_$userId", false)) {
						prefs.getString("ringtone_path_$userId", null)
					}
					else {
						prefs.getString("CallsRingtonePath", null)
					}

					val ringtoneUri: Uri
					var isDefaultUri = false

					if (notificationUri == null) {
						ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
						isDefaultUri = true
					}
					else {
						val defaultUri = Settings.System.DEFAULT_RINGTONE_URI

						if (defaultUri != null && notificationUri.equals(defaultUri.path, ignoreCase = true)) {
							ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
							isDefaultUri = true
						}
						else {
							ringtoneUri = Uri.parse(notificationUri)
						}
					}

					FileLog.d("start ringtone with $isDefaultUri $ringtoneUri")

					ringtonePlayer?.setDataSource(context, ringtoneUri)
					ringtonePlayer?.prepareAsync()
				}
				catch (e: Exception) {
					FileLog.e(e)

					ringtonePlayer?.release()
					ringtonePlayer = null
				}

				val vibrate = if (prefs.getBoolean("custom_$userId", false)) {
					prefs.getInt("calls_vibrate_$userId", 0)
				}
				else {
					prefs.getInt("vibrate_calls", 0)
				}

				if ((vibrate != 2 && vibrate != 4 && (am.ringerMode == AudioManager.RINGER_MODE_VIBRATE || am.ringerMode == AudioManager.RINGER_MODE_NORMAL)) || (vibrate == 4 && am.ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
					vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

					var duration: Long = 700

					if (vibrate == 1) {
						duration /= 2
					}
					else if (vibrate == 3) {
						duration *= 2
					}

					vibrator?.vibrate(longArrayOf(0, duration, 500), 0)
				}
			}
		}
	}

	private fun stopRinging() {
		synchronized(this) {
			ringtonePlayer?.stop()
			ringtonePlayer?.release()
			ringtonePlayer = null
		}

		vibrator?.cancel()
		vibrator = null
	}

	private var pendingCall: PhoneCall? = null
	private var pendingVoIP: Intent? = null
	var state: State? = null

	fun show(context: Context, intent: Intent?, call: PhoneCall?) {
		FileLog.d("VoIPPreNotification.show()")

		if (call == null || intent == null) {
			dismiss(context)
			FileLog.d("VoIPPreNotification.show(): call or intent is null")
			return
		}

		if (pendingCall?.id == call.id) {
			return
		}

		dismiss(context)

		pendingVoIP = intent
		pendingCall = call

		val account = intent.getIntExtra("account", UserConfig.selectedAccount)
		val userId = intent.getLongExtra("user_id", 0)
		val video = call.video

		state = State(account, userId, call)

		acknowledge(context, account, call) {
			pendingVoIP = intent
			pendingCall = call

			val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			nm.notify(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION, makeNotification(context, account, userId, call.id, video))
			startRinging(context, account, userId)
		}
	}

	private fun acknowledge(context: Context, currentAccount: Int, call: PhoneCall, whenAcknowledged: Runnable?) {
		if (call is TL_phoneCallDiscarded) {
			FileLog.w("Call " + call.id + " was discarded before the voip pre notification started, stopping")

			pendingVoIP = null
			pendingCall = null

			state?.destroy()

			return
		}

		if (XiaomiUtilities.isMIUI() && !XiaomiUtilities.isCustomPermissionGranted(XiaomiUtilities.OP_SHOW_WHEN_LOCKED)) {
			if ((context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).inKeyguardRestrictedInputMode()) {
				FileLog.e("MIUI: no permission to show when locked but the screen is locked. ¯\\_(ツ)_/¯")

				pendingVoIP = null
				pendingCall = null

				state?.destroy()

				return
			}
		}

		val req = TL_phone_receivedCall()
		req.peer = TL_inputPhoneCall()
		req.peer.id = call.id
		req.peer.access_hash = call.access_hash

		ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				FileLog.w("(VoIPPreNotification) receivedCall response = $response")

				if (error != null) {
					FileLog.e("error on receivedCall: $error")

					pendingVoIP = null
					pendingCall = null

					state?.destroy()

					dismiss(context)
				}
				else {
					whenAcknowledged?.run()
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	fun open(context: Context): Boolean {
		if (VoIPService.sharedInstance != null) {
			return true
		}

		if (pendingVoIP == null || pendingCall == null) {
			return false
		}

		pendingVoIP?.putExtra("openFragment", true)
		pendingVoIP?.putExtra("accept", false)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(pendingVoIP)
		}
		else {
			context.startService(pendingVoIP)
		}

		pendingVoIP = null

		dismiss(context)

		return true
	}

	@JvmStatic
	val isVideo: Boolean
		get() = pendingVoIP?.getBooleanExtra("video", false) == true

	@JvmStatic
	fun answer(context: Context) {
		FileLog.d("VoIPPreNotification.answer()")

		if (pendingVoIP == null) {
			FileLog.d("VoIPPreNotification.answer(): pending intent is not found")
			return
		}

		state = null

		if (VoIPService.sharedInstance != null) {
			VoIPService.sharedInstance?.acceptIncomingCall()
		}
		else {
			pendingVoIP?.putExtra("openFragment", true)

			if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
				try {
					PendingIntent.getActivity(context, 0, Intent(context, VoIPPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT).send()
				}
				catch (x: Exception) {
					FileLog.e("Error starting permission activity", x)
				}

				return
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				context.startForegroundService(pendingVoIP)
			}
			else {
				context.startService(pendingVoIP)
			}

			pendingVoIP = null
		}

		dismiss(context)
	}

	@JvmStatic
	fun decline(context: Context, reason: Int) {
		FileLog.d("VoIPPreNotification.decline($reason)")

		val pendingVoIP = pendingVoIP

		if (pendingVoIP == null || pendingCall == null) {
			FileLog.d("VoIPPreNotification.decline($reason): pending intent or call is not found")
			return
		}

		val account = pendingVoIP.getIntExtra("account", UserConfig.selectedAccount)

		val req = TL_phone_discardCall()
		req.peer = TL_inputPhoneCall()
		req.peer.access_hash = pendingCall!!.access_hash
		req.peer.id = pendingCall!!.id
		req.duration = 0
		req.connection_id = 0

		when (reason) {
			VoIPService.DISCARD_REASON_DISCONNECT -> req.reason = TL_phoneCallDiscardReasonDisconnect()
			VoIPService.DISCARD_REASON_MISSED -> req.reason = TL_phoneCallDiscardReasonMissed()
			VoIPService.DISCARD_REASON_LINE_BUSY -> req.reason = TL_phoneCallDiscardReasonBusy()
			VoIPService.DISCARD_REASON_HANGUP -> req.reason = TL_phoneCallDiscardReasonHangup()
			else -> req.reason = TL_phoneCallDiscardReasonHangup()
		}

		FileLog.e("discardCall " + req.reason)

		ConnectionsManager.getInstance(account).sendRequest(req, { response, error ->
			if (error != null) {
				FileLog.e("(VoIPPreNotification) error on phone.discardCall: $error")
			}
			else {
				if (response is TL_updates) {
					MessagesController.getInstance(account).processUpdates(response, false)
				}

				FileLog.d("(VoIPPreNotification) phone.discardCall $response")
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)

		dismiss(context)
	}

	fun dismiss(context: Context) {
		FileLog.d("VoIPPreNotification.dismiss()")

		pendingVoIP = null
		pendingCall = null

		state?.destroy()

		val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		nm.cancel(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION)

		stopRinging()
	}

	class State(private val currentAccount: Int, private val userId: Long, private val call: PhoneCall) : VoIPServiceState {
		private var destroyed = false

		override fun getUser(): User? {
			return MessagesController.getInstance(currentAccount).getUser(userId)
		}

		override fun isOutgoing(): Boolean {
			return false
		}

		override fun getCallState(): Int {
			return if (destroyed) VoIPService.STATE_ENDED else VoIPService.STATE_WAITING_INCOMING
		}

		override fun getPrivateCall(): PhoneCall {
			return call
		}

		override fun acceptIncomingCall() {
			answer(ApplicationLoader.applicationContext)
		}

		override fun declineIncomingCall() {
			decline(ApplicationLoader.applicationContext, VoIPService.DISCARD_REASON_HANGUP)
		}

		override fun stopRinging() {
			VoIPPreNotificationService.stopRinging()
		}

		fun destroy() {
			if (destroyed) {
				return
			}

			destroyed = true

			VoIPFragment.instance?.onStateChanged(callState)
		}
	}
}
