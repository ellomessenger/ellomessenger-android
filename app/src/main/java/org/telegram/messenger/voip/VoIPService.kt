/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2016.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger.voip

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.ServiceListener
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.*
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.*
import android.telecom.*
import android.telephony.TelephonyManager
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.LruCache
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import org.telegram.messenger.*
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.vibrate
import org.telegram.messenger.voip.Instance.*
import org.telegram.messenger.voip.NativeInstance.AudioLevelsCallback
import org.telegram.messenger.voip.NativeInstance.SsrcGroup
import org.telegram.messenger.voip.VoIPController.ConnectionStateListener
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.*
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.tgnet.tlrpc.Vector
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.JoinCallAlert
import org.telegram.ui.Components.voip.VoIPHelper
import org.telegram.ui.Components.voip.VoIPHelper.dataSavingDefault
import org.telegram.ui.Components.voip.VoIPHelper.getLogFilePath
import org.telegram.ui.LaunchActivity
import org.telegram.ui.VoIPFeedbackActivity
import org.telegram.ui.VoIPFragment
import org.telegram.ui.VoIPPermissionActivity
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.voiceengine.WebRtcAudioTrack
import java.io.*
import java.math.BigInteger
import java.util.*
import kotlin.experimental.xor
import kotlin.math.max
import kotlin.math.min

@SuppressLint("NewApi")
class VoIPService : Service(), SensorEventListener, OnAudioFocusChangeListener, ConnectionStateListener, NotificationCenterDelegate {
	private var currentAccount = -1
	private var lastNetInfo: NetworkInfo? = null
	private var currentState = 0
	private var wasConnected = false
	private var reconnectScreenCapture = false
	private var chat: Chat? = null
	private var isVideoAvailable = false
	private var notificationsDisabled = false
	private var isMuted = false
	private var switchingCamera = false
	private var isFrontFaceCamera = true
	private var isPrivateScreencast = false
	private var lastError: String? = null
	private var proximityWakelock: PowerManager.WakeLock? = null
	private var cpuWakelock: PowerManager.WakeLock? = null
	private var isProximityNear = false
	private var isHeadsetPlugged = false
	private var previousAudioOutput = -1
	private val stateListeners = ArrayList<StateListener>()
	private var ringtonePlayer: MediaPlayer? = null
	private var vibrator: Vibrator? = null
	private var soundPool: SoundPool? = null
	private var spRingbackID = 0
	private var spFailedID = 0
	private var spEndId = 0
	private var spVoiceChatEndId = 0
	private var spVoiceChatStartId = 0
	private var spVoiceChatConnecting = 0
	private var spBusyId = 0
	private var spConnectingId = 0
	private var spPlayId = 0
	private var spStartRecordId = 0
	private var spAllowTalkId = 0
	private var needPlayEndSound = false
	private var hasAudioFocus = false
	private var micMute = false
	private var unmutedByHold = false
	private var btAdapter: BluetoothAdapter? = null
	private var prevTrafficStats: TrafficStats? = null
	private var isBtHeadsetConnected = false
	private var updateNotificationRunnable: Runnable? = null
	private var onDestroyRunnable: Runnable? = null
	private var switchingStreamTimeoutRunnable: Runnable? = null
	private var playedConnectedSound = false
	private var switchingStream = false
	private var switchingAccount = false

	@JvmField
	var privateCall: PhoneCall? = null

	@JvmField
	var groupCall: ChatObject.Call? = null

	private var currentGroupModeStreaming = false
	private var createGroupCall = false
	private var scheduleDate = 0
	private var groupCallPeer: InputPeer? = null

	@JvmField
	var hasFewPeers = false

	private var joinHash: String? = null
	private var remoteVideoState = VIDEO_STATE_INACTIVE
	private var myParams: TL_dataJSON? = null
	private val mySource = IntArray(2)
	private val tgVoip = arrayOfNulls<NativeInstance>(2)
	private val captureDevice = LongArray(2)
	private val destroyCaptureDevice = booleanArrayOf(true, true)
	private val videoState = intArrayOf(VIDEO_STATE_INACTIVE, VIDEO_STATE_INACTIVE)
	private var callStartTime: Long = 0
	private var playingSound = false
	private var isOutgoing = false
	var videoCall = false
	private var timeoutRunnable: Runnable? = null
	private var mHasEarpiece: Boolean? = null
	private var wasEstablished = false
	private var signalBarCount = 0
	private var remoteAudioState = AUDIO_STATE_ACTIVE
	private var audioConfigured = false
	private var audioRouteToSet = AUDIO_ROUTE_BLUETOOTH
	private var speakerphoneStateToSet = false
	private var systemCallConnection: CallConnection? = null
	private var callDiscardReason = 0
	private var bluetoothScoActive = false
	private var bluetoothScoConnecting = false
	private var needSwitchToBluetoothAfterScoActivates = false
	private var didDeleteConnectionServiceContact = false
	private var connectingSoundRunnable: Runnable? = null

	@JvmField
	var currentBluetoothDeviceName: String? = null

	@JvmField
	val sharedUIParams = SharedUIParams()
	private var user: User? = null
	private var callReqId = 0
	private var g_a: ByteArray? = null
	private var a_or_b: ByteArray? = null
	private var g_a_hash: ByteArray? = null
	private var authKey: ByteArray? = null
	private var keyFingerprint: Long = 0
	private var forceRating = false
	private var needSendDebugLog = false
	private var needRateCall = false
	private var lastTypingTimeSend: Long = 0
	private var endCallAfterRequest = false
	private val pendingUpdates = ArrayList<PhoneCall?>()
	private var delayedStartOutgoingCall: Runnable? = null
	private var startedRinging = false
	private var classGuid = 0
	private val currentStreamRequestTimestamp = HashMap<String, Int>()

	@JvmField
	var micSwitching = false
	private val afterSoundRunnable = Runnable {
		val am = getSystemService(AUDIO_SERVICE) as AudioManager
		am.abandonAudioFocus(this@VoIPService)
		am.unregisterMediaButtonEventReceiver(ComponentName(this@VoIPService, VoIPMediaButtonReceiver::class.java))

		if (!USE_CONNECTION_SERVICE && sharedInstance == null) {
			if (isBtHeadsetConnected) {
				am.stopBluetoothSco()
				am.isBluetoothScoOn = false
				bluetoothScoActive = false
				bluetoothScoConnecting = false
			}

			am.isSpeakerphoneOn = false
		}

		Utilities.globalQueue.postRunnable {
			soundPool?.release()
		}

		Utilities.globalQueue.postRunnable(Runnable label@{
			synchronized(sync) {
				if (setModeRunnable == null) {
					return@label
				}

				setModeRunnable = null
			}

			try {
				am.mode = AudioManager.MODE_NORMAL
			}
			catch (x: SecurityException) {
				FileLog.e("Error setting audio more to normal", x)
			}
		}.also {
			setModeRunnable = it
		})
	}

	var fetchingBluetoothDeviceName = false

	private val serviceListener: ServiceListener = object : ServiceListener {
		override fun onServiceDisconnected(profile: Int) {
			// unused
		}

		override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
			try {
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
					for (device in proxy.connectedDevices) {
						if (proxy.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
							continue
						}

						currentBluetoothDeviceName = try {
							device.name
						}
						catch (e: SecurityException) {
							null
						}

						break
					}
				}

				BluetoothAdapter.getDefaultAdapter().closeProfileProxy(profile, proxy)

				fetchingBluetoothDeviceName = false
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}
	}

	private val receiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			if (ACTION_HEADSET_PLUG == intent.action) {
				isHeadsetPlugged = intent.getIntExtra("state", 0) == 1

				if (isHeadsetPlugged && proximityWakelock != null && proximityWakelock!!.isHeld) {
					proximityWakelock?.release()
				}

				if (isHeadsetPlugged) {
					val am = getSystemService(AUDIO_SERVICE) as AudioManager

					previousAudioOutput = if (am.isSpeakerphoneOn) {
						0
					}
					else if (am.isBluetoothScoOn) {
						2
					}
					else {
						1
					}

					setAudioOutput(1)
				}
				else {
					if (previousAudioOutput >= 0) {
						setAudioOutput(previousAudioOutput)
						previousAudioOutput = -1
					}
				}

				isProximityNear = false

				updateOutputGainControlState()
			}
			else if (ConnectivityManager.CONNECTIVITY_ACTION == intent.action) {
				updateNetworkType()
			}
			else if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED == intent.action) {
				FileLog.e("bt headset state = " + intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0))
				updateBluetoothHeadsetState(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED) == BluetoothProfile.STATE_CONNECTED)
			}
			else if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED == intent.action) {
				val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)

				FileLog.e("Bluetooth SCO state updated: $state")

				if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED && isBtHeadsetConnected) {
					if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
						return
					}

					if (btAdapter?.isEnabled == false || btAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET) != BluetoothAdapter.STATE_CONNECTED) {
						updateBluetoothHeadsetState(false)
						return
					}
				}

				bluetoothScoConnecting = state == AudioManager.SCO_AUDIO_STATE_CONNECTING
				bluetoothScoActive = state == AudioManager.SCO_AUDIO_STATE_CONNECTED

				if (bluetoothScoActive) {
					fetchBluetoothDeviceName()

					if (needSwitchToBluetoothAfterScoActivates) {
						needSwitchToBluetoothAfterScoActivates = false

						val am = getSystemService(AUDIO_SERVICE) as AudioManager
						am.isSpeakerphoneOn = false
						am.isBluetoothScoOn = true
					}
				}

				for (l in stateListeners) {
					l.onAudioSettingsChanged()
				}
			}
			else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED == intent.action) {
				val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

				if (TelephonyManager.EXTRA_STATE_OFFHOOK == state) {
					hangUp()
				}
			}
			else if (Intent.ACTION_SCREEN_ON == intent.action) {
				stateListeners.forEach {
					it.onScreenOnChange(true)
				}
			}
			else if (Intent.ACTION_SCREEN_OFF == intent.action) {
				stateListeners.forEach {
					it.onScreenOnChange(false)
				}
			}
		}
	}

	fun isFrontFaceCamera(): Boolean {
		return isFrontFaceCamera
	}

	fun isScreencast(): Boolean {
		return isPrivateScreencast
	}

	fun setMicMute(mute: Boolean, hold: Boolean, send: Boolean) {
		@Suppress("NAME_SHADOWING") var send = send

		if (micMute == mute || micSwitching) {
			return
		}

		micMute = mute

		if (groupCall != null) {
			if (!send) {
				val self = groupCall?.participants?.get(getSelfId())

				if (self != null && self.muted && !self.can_self_unmute) {
					send = true
				}
			}

			if (send) {
				editCallMember(UserConfig.getInstance(currentAccount).getCurrentUser(), mute, null, null, null, null)

				Utilities.globalQueue.postRunnable(Runnable label@{
					if (updateNotificationRunnable == null) {
						return@label
					}

					updateNotificationRunnable = null

					showNotification(chat?.title ?: "", getRoundAvatarBitmap(chat))
				}.also {
					updateNotificationRunnable = it
				})
			}
		}

		unmutedByHold = !micMute && hold

		tgVoip[CAPTURE_DEVICE_CAMERA]?.setMuteMicrophone(mute)

		stateListeners.forEach {
			it.onAudioSettingsChanged()
		}
	}

	fun mutedByAdmin(): Boolean {
		val call = groupCall ?: return false

		val selfId = getSelfId()
		val participant = call.participants[selfId]
		return participant != null && !participant.can_self_unmute && participant.muted && !ChatObject.canManageCalls(chat)
	}

	private val waitingFrameParticipant = mutableMapOf<String, TL_groupCallParticipant>()

	private val proxyVideoSinkLruCache = object : LruCache<String, ProxyVideoSink>(6) {
		override fun entryRemoved(evicted: Boolean, key: String?, oldValue: ProxyVideoSink, newValue: ProxyVideoSink) {
			super.entryRemoved(evicted, key, oldValue, newValue)
			tgVoip[CAPTURE_DEVICE_CAMERA]?.removeIncomingVideoOutput(oldValue.nativeInstance)
		}
	}

	fun hasVideoCapturer(): Boolean {
		return captureDevice[CAPTURE_DEVICE_CAMERA] != 0L
	}

	fun checkVideoFrame(participant: TL_groupCallParticipant, screencast: Boolean) {
		val endpointId = (if (screencast) participant.presentationEndpoint else participant.videoEndpoint) ?: return

		if (screencast && participant.hasPresentationFrame != ChatObject.VIDEO_FRAME_NO_FRAME || !screencast && participant.hasCameraFrame != ChatObject.VIDEO_FRAME_NO_FRAME) {
			return
		}

		if (proxyVideoSinkLruCache[endpointId] != null || remoteSinks[endpointId] != null && waitingFrameParticipant[endpointId] == null) {
			if (screencast) {
				participant.hasPresentationFrame = ChatObject.VIDEO_FRAME_HAS_FRAME
			}
			else {
				participant.hasCameraFrame = ChatObject.VIDEO_FRAME_HAS_FRAME
			}

			return
		}

		if (waitingFrameParticipant.containsKey(endpointId)) {
			waitingFrameParticipant[endpointId] = participant

			if (screencast) {
				participant.hasPresentationFrame = ChatObject.VIDEO_FRAME_REQUESTING
			}
			else {
				participant.hasCameraFrame = ChatObject.VIDEO_FRAME_REQUESTING
			}

			return
		}

		if (screencast) {
			participant.hasPresentationFrame = ChatObject.VIDEO_FRAME_REQUESTING
		}
		else {
			participant.hasCameraFrame = ChatObject.VIDEO_FRAME_REQUESTING
		}

		waitingFrameParticipant[endpointId] = participant

		addRemoteSink(participant, screencast, object : VideoSink {
			override fun onFrame(frame: VideoFrame?) {
				val thisSink: VideoSink = this

				if (frame != null && frame.buffer.height != 0 && frame.buffer.width != 0) {
					AndroidUtilities.runOnUIThread {
						val currentParticipant = waitingFrameParticipant.remove(endpointId)
						val proxyVideoSink = remoteSinks[endpointId]

						if (proxyVideoSink != null && proxyVideoSink.target === thisSink) {
							proxyVideoSinkLruCache.put(endpointId, proxyVideoSink)
							remoteSinks.remove(endpointId)
							proxyVideoSink.target = null
						}

						if (currentParticipant != null) {
							if (screencast) {
								currentParticipant.hasPresentationFrame = ChatObject.VIDEO_FRAME_HAS_FRAME
							}
							else {
								currentParticipant.hasCameraFrame = ChatObject.VIDEO_FRAME_HAS_FRAME
							}
						}

						groupCall?.updateVisibleParticipants()
					}
				}
			}
		}, null)
	}

	fun clearRemoteSinks() {
		proxyVideoSinkLruCache.evictAll()
	}

	fun setAudioRoute(route: Int) {
		when (route) {
			AUDIO_ROUTE_SPEAKER -> setAudioOutput(0)
			AUDIO_ROUTE_EARPIECE -> setAudioOutput(1)
			AUDIO_ROUTE_BLUETOOTH -> setAudioOutput(2)
		}
	}

	class ProxyVideoSink : VideoSink {
		var target: VideoSink? = null
			set(value) {
				synchronized(this) {
					if (field !== value) {
						field?.setParentSink(null)
						field = value
						value?.setParentSink(this)
					}
				}
			}

		private var background: VideoSink? = null
		var nativeInstance: Long = 0

		@Synchronized
		override fun onFrame(frame: VideoFrame) {
			target?.onFrame(frame)
			background?.onFrame(frame)
		}

		@Synchronized
		fun setBackground(newBackground: VideoSink?) {
			background?.setParentSink(null)

			background = newBackground.also {
				it?.setParentSink(this)
			}
		}

		@Synchronized
		fun removeTarget(target: VideoSink) {
			if (this.target === target) {
				this.target = null
			}
		}

		@Synchronized
		fun removeBackground(background: VideoSink) {
			if (this.background === background) {
				this.background = null
			}
		}

		@Synchronized
		fun swap() {
			if (target != null && background != null) {
				target = background
				background = null
			}
		}
	}

	private val localSink = arrayOfNulls<ProxyVideoSink>(2)
	private val remoteSink = arrayOfNulls<ProxyVideoSink>(2)
	private val currentBackgroundSink = arrayOfNulls<ProxyVideoSink>(2)
	private val currentBackgroundEndpointId = arrayOfNulls<String>(2)
	private val remoteSinks = HashMap<String, ProxyVideoSink?>()

	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	@SuppressLint("MissingPermission", "InlinedApi")
	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
		if (sharedInstance != null) {
			FileLog.e("Tried to start the VoIP service when it's already started")
			return START_NOT_STICKY
		}

		currentAccount = intent.getIntExtra("account", -1)

		check(currentAccount != -1) { "No account specified when starting VoIP service" }

		classGuid = ConnectionsManager.generateClassGuid()

		val userID = intent.getLongExtra("user_id", 0)
		val chatID = intent.getLongExtra("chat_id", 0)

		createGroupCall = intent.getBooleanExtra("createGroupCall", false)
		hasFewPeers = intent.getBooleanExtra("hasFewPeers", false)
		joinHash = intent.getStringExtra("hash")

		val peerChannelId = intent.getLongExtra("peerChannelId", 0)
		val peerChatId = intent.getLongExtra("peerChatId", 0)
		val peerUserId = intent.getLongExtra("peerUserId", 0)

		if (peerChatId != 0L) {
			groupCallPeer = TL_inputPeerChat()
			groupCallPeer?.chat_id = peerChatId
			groupCallPeer?.access_hash = intent.getLongExtra("peerAccessHash", 0)
		}
		else if (peerChannelId != 0L) {
			groupCallPeer = TL_inputPeerChannel()
			groupCallPeer?.channel_id = peerChannelId
			groupCallPeer?.access_hash = intent.getLongExtra("peerAccessHash", 0)
		}
		else if (peerUserId != 0L) {
			groupCallPeer = TL_inputPeerUser()
			groupCallPeer?.user_id = peerUserId
			groupCallPeer?.access_hash = intent.getLongExtra("peerAccessHash", 0)
		}

		scheduleDate = intent.getIntExtra("scheduleDate", 0)
		isOutgoing = intent.getBooleanExtra("is_outgoing", false)
		videoCall = intent.getBooleanExtra("video_call", false)
		isVideoAvailable = intent.getBooleanExtra("can_video_call", false)
		notificationsDisabled = intent.getBooleanExtra("notifications_disabled", false)
		isMuted = intent.getBooleanExtra("is_muted", false)

		if (userID != 0L) {
			user = MessagesController.getInstance(currentAccount).getUser(userID)
		}

		if (chatID != 0L) {
			chat = MessagesController.getInstance(currentAccount).getChat(chatID)

			if (ChatObject.isChannel(chat)) {
				MessagesController.getInstance(currentAccount).startShortPoll(chat, classGuid, false)
			}
		}

		loadResources()

		for (a in localSink.indices) {
			localSink[a] = ProxyVideoSink()
			remoteSink[a] = ProxyVideoSink()
		}

		try {
			val am = getSystemService(AUDIO_SERVICE) as AudioManager
			isHeadsetPlugged = am.isWiredHeadsetOn
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		if (chat != null && !createGroupCall) {
			val call = MessagesController.getInstance(currentAccount).getGroupCall(chat!!.id, false)

			if (call == null) {
				FileLog.w("VoIPService: trying to open group call without call " + chat!!.id)
				stopSelf()
				return START_NOT_STICKY
			}
		}

		if (videoCall) {
			if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
				captureDevice[CAPTURE_DEVICE_CAMERA] = NativeInstance.createVideoCapturer(localSink[CAPTURE_DEVICE_CAMERA], if (isFrontFaceCamera) 1 else 0)

				if (chatID != 0L) {
					videoState[CAPTURE_DEVICE_CAMERA] = VIDEO_STATE_PAUSED
				}
				else {
					videoState[CAPTURE_DEVICE_CAMERA] = VIDEO_STATE_ACTIVE
				}
			}
			else {
				videoState[CAPTURE_DEVICE_CAMERA] = VIDEO_STATE_PAUSED
			}

			if (!isBtHeadsetConnected && !isHeadsetPlugged) {
				setAudioOutput(0)
			}
		}

		if (user == null && chat == null) {
			FileLog.w("VoIPService: user == null AND chat == null")

			stopSelf()

			return START_NOT_STICKY
		}

		sharedInstance = this

		synchronized(sync) {
			if (setModeRunnable != null) {
				Utilities.globalQueue.cancelRunnable(setModeRunnable)
				setModeRunnable = null
			}
		}

		if (isOutgoing) {
			if (user != null) {
				dispatchStateChanged(STATE_REQUESTING)

				if (USE_CONNECTION_SERVICE) {
					val tm = getSystemService(TELECOM_SERVICE) as TelecomManager
					val extras = Bundle()
					val myExtras = Bundle()
					extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, addAccountToTelecomManager())
					myExtras.putInt("call_type", 1)
					extras.putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, myExtras)
					// ContactsController.getInstance(currentAccount).createOrUpdateConnectionServiceContact(user!!.id, user!!.first_name, user!!.last_name)
					tm.placeCall(Uri.fromParts("tel", "+99084" + user!!.id, null), extras)
				}
				else {
					delayedStartOutgoingCall = Runnable {
						delayedStartOutgoingCall = null
						startOutgoingCall()
					}

					AndroidUtilities.runOnUIThread(delayedStartOutgoingCall, 2000)
				}
			}
			else {
				micMute = true

				startGroupCall(0, null, false)

				if (!isBtHeadsetConnected && !isHeadsetPlugged) {
					setAudioOutput(0)
				}
			}

			if (intent.getBooleanExtra("start_incall_activity", false)) {
				val intent1 = Intent(this, LaunchActivity::class.java).setAction(if (user != null) "voip" else "voip_chat").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

				if (chat != null) {
					intent1.putExtra("currentAccount", currentAccount)
				}

				startActivity(intent1)
			}
		}
		else {
			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.closeInCallActivity)

			privateCall = callIShouldHavePutIntoIntent
			videoCall = privateCall?.video == true

			if (videoCall) {
				isVideoAvailable = true
			}

			if (videoCall && !isBtHeadsetConnected && !isHeadsetPlugged) {
				setAudioOutput(0)
			}

			callIShouldHavePutIntoIntent = null

			if (USE_CONNECTION_SERVICE) {
				acknowledgeCall(false)
				showNotification()
			}
			else {
				acknowledgeCall(true)
			}
		}

		initializeAccountRelatedThings()

		AndroidUtilities.runOnUIThread {
			NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.voipServiceCreated)
		}

		return START_NOT_STICKY
	}

	fun getUser(): User? {
		return user
	}

	fun getChat(): Chat? {
		return chat
	}

	fun setNoiseSupressionEnabled(enabled: Boolean) {
		tgVoip[CAPTURE_DEVICE_CAMERA]?.setNoiseSuppressionEnabled(enabled)
	}

	fun setGroupCallHash(hash: String) {
		if (!currentGroupModeStreaming || TextUtils.isEmpty(hash) || hash == joinHash) {
			return
		}

		joinHash = hash

		createGroupInstance(CAPTURE_DEVICE_CAMERA, false)
	}

	fun getCallerId(): Long {
		return if (user != null) {
			user!!.id
		}
		else {
			-chat!!.id
		}
	}

	@JvmOverloads
	fun hangUp(discard: Int = 0, onDone: Runnable? = null) {
		declineIncomingCall(if (currentState == STATE_RINGING || currentState == STATE_WAITING && isOutgoing) DISCARD_REASON_MISSED else DISCARD_REASON_HANGUP, onDone)

		if (groupCall != null) {
			if (discard == 2) {
				return
			}

			if (discard == 1) {
				val chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat!!.id)

				if (chatFull != null) {
					chatFull.flags = chatFull.flags and 2097152.inv()
					chatFull.call = null

					NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.groupCallUpdated, chat!!.id, groupCall!!.call!!.id, false)
				}

				val req = TL_phone_discardGroupCall()
				req.call = groupCall!!.inputGroupCall

				ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
					if (response is TL_updates) {
						MessagesController.getInstance(currentAccount).processUpdates(response, false)
					}
				}
			}
			else {
				val req = TL_phone_leaveGroupCall()
				req.call = groupCall!!.inputGroupCall
				req.source = mySource[CAPTURE_DEVICE_CAMERA]

				ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
					if (response is TL_updates) {
						MessagesController.getInstance(currentAccount).processUpdates(response, false)
					}
				}
			}
		}
	}

	private fun startOutgoingCall() {
		if (USE_CONNECTION_SERVICE) {
			systemCallConnection?.setDialing()
		}

		configureDeviceForCall()
		showNotification()
		startConnectingSound()
		dispatchStateChanged(STATE_REQUESTING)

		AndroidUtilities.runOnUIThread {
			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.didStartedCall)
		}

		val salt = ByteArray(256)

		Utilities.random.nextBytes(salt)

		val req = TL_messages_getDhConfig()
		req.random_length = 256

		val messagesStorage = MessagesStorage.getInstance(currentAccount)

		req.version = messagesStorage.lastSecretVersion

		callReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
			callReqId = 0

			if (endCallAfterRequest) {
				callEnded()
				return@sendRequest
			}

			if (error == null) {
				val res = response as messages_DhConfig

				if (response is TL_messages_dhConfig) {
					if (!Utilities.isGoodPrime(res.p, res.g)) {
						callFailed()
						return@sendRequest
					}

					messagesStorage.secretPBytes = res.p
					messagesStorage.secretG = res.g
					messagesStorage.lastSecretVersion = res.version
					messagesStorage.saveSecretParams(messagesStorage.lastSecretVersion, messagesStorage.secretG, messagesStorage.secretPBytes)
				}

				val salt1 = ByteArray(256)

				for (a in 0..255) {
					salt1[a] = ((Utilities.random.nextDouble() * 256).toInt().toByte() xor res.random[a].toInt().toByte())
				}

				var i_g_a = BigInteger.valueOf(messagesStorage.secretG.toLong())
				i_g_a = i_g_a.modPow(BigInteger(1, salt1), BigInteger(1, messagesStorage.secretPBytes))

				var g_a = i_g_a.toByteArray()

				if (g_a.size > 256) {
					val correctedAuth = ByteArray(256)
					System.arraycopy(g_a, 1, correctedAuth, 0, 256)
					g_a = correctedAuth
				}

				val reqCall = TL_phone_requestCall()
				reqCall.user_id = MessagesController.getInstance(currentAccount).getInputUser(user)
				reqCall.protocol = TL_phoneCallProtocol()
				reqCall.video = videoCall
				reqCall.protocol.udp_p2p = true
				reqCall.protocol.udp_reflector = true
				reqCall.protocol.min_layer = CALL_MIN_LAYER
				reqCall.protocol.max_layer = getConnectionMaxLayer()
				reqCall.protocol.library_versions.addAll(AVAILABLE_VERSIONS)

				this@VoIPService.g_a = g_a

				reqCall.g_a_hash = Utilities.computeSHA256(g_a, 0, g_a.size.toLong())
				reqCall.random_id = Utilities.random.nextInt()

				ConnectionsManager.getInstance(currentAccount).sendRequest(reqCall, { response12, error12 ->
					AndroidUtilities.runOnUIThread {
						if (error12 == null) {
							privateCall = (response12 as TL_phone_phoneCall).phone_call
							a_or_b = salt1

							dispatchStateChanged(STATE_WAITING)

							if (endCallAfterRequest) {
								hangUp()
								return@runOnUIThread
							}

							if (pendingUpdates.size > 0 && privateCall != null) {
								for (call in pendingUpdates) {
									onCallUpdated(call)
								}

								pendingUpdates.clear()
							}

							timeoutRunnable = Runnable {
								timeoutRunnable = null

								val req1 = TL_phone_discardCall()
								req1.peer = TL_inputPhoneCall()
								req1.peer.access_hash = privateCall!!.access_hash
								req1.peer.id = privateCall!!.id
								req1.reason = TL_phoneCallDiscardReasonMissed()

								ConnectionsManager.getInstance(currentAccount).sendRequest(req1, { response1, error1 ->
									if (error1 != null) {
										FileLog.e("error on phone.discardCall: $error1")
									}
									else {
										FileLog.d("phone.discardCall $response1")
									}

									AndroidUtilities.runOnUIThread {
										callFailed()
									}
								}, ConnectionsManager.RequestFlagFailOnServerErrors)
							}

							AndroidUtilities.runOnUIThread(timeoutRunnable, MessagesController.getInstance(currentAccount).callReceiveTimeout.toLong())
						}
						else {
							if (error12.code == 400 && "PARTICIPANT_VERSION_OUTDATED" == error12.text) {
								callFailed(ERROR_PEER_OUTDATED)
							}
							else if (error12.code == 403) {
								callFailed(ERROR_PRIVACY)
							}
							else if (error12.code == 406) {
								callFailed(ERROR_LOCALIZED)
							}
							else {
								FileLog.e("Error on phone.requestCall: $error12")
								callFailed()
							}
						}
					}
				}, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
			else {
				FileLog.e("Error on getDhConfig $error")
				callFailed()
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun acknowledgeCall(startRinging: Boolean) {
		if (privateCall is TL_phoneCallDiscarded) {
			FileLog.w("Call " + privateCall?.id + " was discarded before the service started, stopping")
			stopSelf()
			return
		}

		if (XiaomiUtilities.isMIUI() && !XiaomiUtilities.isCustomPermissionGranted(XiaomiUtilities.OP_SHOW_WHEN_LOCKED)) {
			if ((getSystemService(KEYGUARD_SERVICE) as KeyguardManager).inKeyguardRestrictedInputMode()) {
				FileLog.e("MIUI: no permission to show when locked but the screen is locked. ¯\\_(ツ)_/¯")
				stopSelf()
				return
			}
		}

		val req = TL_phone_receivedCall()
		req.peer = TL_inputPhoneCall()
		req.peer.id = privateCall!!.id
		req.peer.access_hash = privateCall!!.access_hash

		ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				if (sharedInstance == null) {
					return@runOnUIThread
				}

				FileLog.w("receivedCall response = $response")

				if (error != null) {
					FileLog.e("error on receivedCall: $error")
					stopSelf()
				}
				else {
					if (USE_CONNECTION_SERVICE) {
						// ContactsController.getInstance(currentAccount).createOrUpdateConnectionServiceContact(user!!.id, user!!.first_name, user!!.last_name)
						val tm = getSystemService(TELECOM_SERVICE) as TelecomManager
						val extras = Bundle()
						extras.putInt("call_type", 1)
						tm.addNewIncomingCall(addAccountToTelecomManager(), extras)
					}

					if (startRinging) {
						startRinging()
					}
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun isRinging(): Boolean {
		return currentState == STATE_WAITING_INCOMING
	}

	fun isJoined(): Boolean {
		return currentState != STATE_WAIT_INIT && currentState != STATE_CREATING
	}

	fun requestVideoCall(screencast: Boolean) {
		if (tgVoip[CAPTURE_DEVICE_CAMERA] == null) {
			return
		}

		if (!screencast && captureDevice[CAPTURE_DEVICE_CAMERA] != 0L) {
			tgVoip[CAPTURE_DEVICE_CAMERA]!!.setupOutgoingVideoCreated(captureDevice[CAPTURE_DEVICE_CAMERA])
			destroyCaptureDevice[CAPTURE_DEVICE_CAMERA] = false
		}
		else {
			tgVoip[CAPTURE_DEVICE_CAMERA]!!.setupOutgoingVideo(localSink[CAPTURE_DEVICE_CAMERA], if (screencast) 2 else if (isFrontFaceCamera) 1 else 0)
		}

		isPrivateScreencast = screencast
	}

	fun switchCamera() {
		if (tgVoip[CAPTURE_DEVICE_CAMERA] == null || !tgVoip[CAPTURE_DEVICE_CAMERA]!!.hasVideoCapturer() || switchingCamera) {
			if (captureDevice[CAPTURE_DEVICE_CAMERA] != 0L && !switchingCamera) {
				NativeInstance.switchCameraCapturer(captureDevice[CAPTURE_DEVICE_CAMERA], !isFrontFaceCamera)
			}

			return
		}

		switchingCamera = true

		tgVoip[CAPTURE_DEVICE_CAMERA]?.switchCamera(!isFrontFaceCamera)
	}

	fun createCaptureDevice(screencast: Boolean) {
		val index = if (screencast) CAPTURE_DEVICE_SCREEN else CAPTURE_DEVICE_CAMERA

		val deviceType: Int = if (screencast) {
			2
		}
		else {
			if (isFrontFaceCamera) 1 else 0
		}

		if (groupCall == null) {
			if (!isPrivateScreencast && screencast) {
				setVideoState(false, VIDEO_STATE_INACTIVE)
			}

			isPrivateScreencast = screencast

			tgVoip[CAPTURE_DEVICE_CAMERA]?.clearVideoCapturer()
		}
		if (index == CAPTURE_DEVICE_SCREEN) {
			if (groupCall != null) {
				if (captureDevice[index] != 0L) {
					return
				}

				captureDevice[index] = NativeInstance.createVideoCapturer(localSink[index], deviceType)

				createGroupInstance(CAPTURE_DEVICE_SCREEN, false)
				setVideoState(true, VIDEO_STATE_ACTIVE)
				AccountInstance.getInstance(currentAccount).notificationCenter.postNotificationName(NotificationCenter.groupCallScreencastStateChanged)
			}
			else {
				requestVideoCall(true)
				setVideoState(true, VIDEO_STATE_ACTIVE)
				VoIPFragment.instance?.onScreenCastStart()
			}
		}
		else {
			if (captureDevice[index] != 0L || tgVoip[index] == null) {
				if (tgVoip[index] != null && captureDevice[index] != 0L) {
					tgVoip[index]?.activateVideoCapturer(captureDevice[index])
				}

				if (captureDevice[index] != 0L) {
					return
				}
			}

			captureDevice[index] = NativeInstance.createVideoCapturer(localSink[index], deviceType)
		}
	}

	fun setupCaptureDevice(screencast: Boolean, micEnabled: Boolean) {
		if (!screencast) {
			val index = CAPTURE_DEVICE_CAMERA
			if (captureDevice[index] == 0L || tgVoip[index] == null) {
				return
			}

			tgVoip[index]?.setupOutgoingVideoCreated(captureDevice[index])
			destroyCaptureDevice[index] = false
			videoState[index] = VIDEO_STATE_ACTIVE
		}

		if (micMute == micEnabled) {
			setMicMute(!micEnabled, hold = false, send = false)
			micSwitching = true
		}

		if (groupCall != null) {
			editCallMember(UserConfig.getInstance(currentAccount).getCurrentUser(), !micEnabled, videoState[CAPTURE_DEVICE_CAMERA] != VIDEO_STATE_ACTIVE, null, null) { micSwitching = false }
		}
	}

	fun clearCamera() {
		tgVoip[CAPTURE_DEVICE_CAMERA]?.clearVideoCapturer()
	}

	fun setVideoState(screencast: Boolean, state: Int) {
		val index = if (screencast) CAPTURE_DEVICE_SCREEN else CAPTURE_DEVICE_CAMERA
		val trueIndex = if (groupCall != null) index else CAPTURE_DEVICE_CAMERA

		if (tgVoip[trueIndex] == null) {
			if (captureDevice[index] != 0L) {
				videoState[trueIndex] = state
				NativeInstance.setVideoStateCapturer(captureDevice[index], videoState[trueIndex])
			}
			else if (state == VIDEO_STATE_ACTIVE && currentState != STATE_BUSY && currentState != STATE_ENDED) {
				captureDevice[index] = NativeInstance.createVideoCapturer(localSink[trueIndex], if (isFrontFaceCamera) 1 else 0)
				videoState[trueIndex] = VIDEO_STATE_ACTIVE
			}

			return
		}

		videoState[trueIndex] = state
		tgVoip[trueIndex]?.setVideoState(videoState[trueIndex])

		if (captureDevice[index] != 0L) {
			NativeInstance.setVideoStateCapturer(captureDevice[index], videoState[trueIndex])
		}

		if (!screencast) {
			if (groupCall != null) {
				editCallMember(UserConfig.getInstance(currentAccount).getCurrentUser(), null, videoState[CAPTURE_DEVICE_CAMERA] != VIDEO_STATE_ACTIVE, null, null, null)
			}

			checkIsNear()
		}
	}

	fun stopScreenCapture() {
		if (groupCall == null || videoState[CAPTURE_DEVICE_SCREEN] != VIDEO_STATE_ACTIVE) {
			return
		}

		val req = TL_phone_leaveGroupCallPresentation()
		req.call = groupCall!!.inputGroupCall

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
			if (response != null) {
				val updates = response as Updates
				MessagesController.getInstance(currentAccount).processUpdates(updates, false)
			}
		}

		val instance = tgVoip[CAPTURE_DEVICE_SCREEN]

		if (instance != null) {
			Utilities.globalQueue.postRunnable {
				instance.stopGroup()
			}
		}

		mySource[CAPTURE_DEVICE_SCREEN] = 0
		tgVoip[CAPTURE_DEVICE_SCREEN] = null
		destroyCaptureDevice[CAPTURE_DEVICE_SCREEN] = true
		captureDevice[CAPTURE_DEVICE_SCREEN] = 0
		videoState[CAPTURE_DEVICE_SCREEN] = VIDEO_STATE_INACTIVE

		AccountInstance.getInstance(currentAccount).notificationCenter.postNotificationName(NotificationCenter.groupCallScreencastStateChanged)
	}

	fun getVideoState(screencast: Boolean): Int {
		return videoState[if (screencast) CAPTURE_DEVICE_SCREEN else CAPTURE_DEVICE_CAMERA]
	}

	fun setSinks(local: VideoSink?, remote: VideoSink?) {
		setSinks(local, false, remote)
	}

	fun setSinks(local: VideoSink?, screencast: Boolean, remote: VideoSink?) {
		localSink[if (screencast) CAPTURE_DEVICE_SCREEN else CAPTURE_DEVICE_CAMERA]?.target = local
		remoteSink[if (screencast) CAPTURE_DEVICE_SCREEN else CAPTURE_DEVICE_CAMERA]?.target = remote
	}

	fun setLocalSink(local: VideoSink?, screencast: Boolean) {
		if (!screencast) {
			localSink[CAPTURE_DEVICE_CAMERA]?.target = local
		}
	}

	fun setRemoteSink(remote: VideoSink?, screencast: Boolean) {
		remoteSink[if (screencast) CAPTURE_DEVICE_SCREEN else CAPTURE_DEVICE_CAMERA]?.target = remote
	}

	fun addRemoteSink(participant: TL_groupCallParticipant, screencast: Boolean, remote: VideoSink?, background: VideoSink?): ProxyVideoSink? {
		if (tgVoip[CAPTURE_DEVICE_CAMERA] == null) {
			return null
		}

		val endpointId = (if (screencast) participant.presentationEndpoint else participant.videoEndpoint) ?: return null
		var sink = remoteSinks[endpointId]

		if (sink != null && sink.target === remote) {
			return sink
		}

		if (sink == null) {
			sink = proxyVideoSinkLruCache.remove(endpointId)
		}

		if (sink == null) {
			sink = ProxyVideoSink()
		}

		if (remote != null) {
			sink.target = remote
		}

		if (background != null) {
			sink.setBackground(background)
		}

		remoteSinks[endpointId] = sink

		sink.nativeInstance = tgVoip[CAPTURE_DEVICE_CAMERA]?.addIncomingVideoOutput(QUALITY_MEDIUM, endpointId, createSsrcGroups(if (screencast) participant.presentation else participant.video), sink) ?: 0L

		return sink
	}

	private fun createSsrcGroups(video: TL_groupCallParticipantVideo): Array<SsrcGroup>? {
		if (video.source_groups.isEmpty()) {
			return null
		}

		return video.source_groups.map {
			SsrcGroup().apply {
				semantics = it.semantics
				ssrcs = it.sources.toIntArray()
			}
		}.toTypedArray()

//		val result = arrayOfNulls<SsrcGroup>(video.source_groups.size)
//
//		for (a in result.indices) {
//			result[a] = SsrcGroup()
//
//			val group = video.source_groups[a]
//			result[a]?.semantics = group.semantics
//			result[a]?.ssrcs = IntArray(group.sources.size)
//
//			for (b in result[a]!!.ssrcs.indices) {
//				result[a]!!.ssrcs[b] = group.sources[b]
//			}
//		}
//
//		return result
	}

	fun requestFullScreen(participant: TL_groupCallParticipant, full: Boolean, screencast: Boolean) {
		val endpointId = (if (screencast) participant.presentationEndpoint else participant.videoEndpoint) ?: return

		if (full) {
			tgVoip[CAPTURE_DEVICE_CAMERA]?.setVideoEndpointQuality(endpointId, QUALITY_FULL)
		}
		else {
			tgVoip[CAPTURE_DEVICE_CAMERA]?.setVideoEndpointQuality(endpointId, QUALITY_MEDIUM)
		}
	}

	fun removeRemoteSink(participant: TL_groupCallParticipant, presentation: Boolean) {
		val sink = if (presentation) {
			remoteSinks.remove(participant.presentationEndpoint)
		}
		else {
			remoteSinks.remove(participant.videoEndpoint)
		}

		if (sink != null) {
			tgVoip[CAPTURE_DEVICE_CAMERA]?.removeIncomingVideoOutput(sink.nativeInstance)
		}
	}

	fun isFullscreen(participant: TL_groupCallParticipant, screencast: Boolean): Boolean {
		return currentBackgroundSink[if (screencast) CAPTURE_DEVICE_SCREEN else CAPTURE_DEVICE_CAMERA] != null && TextUtils.equals(currentBackgroundEndpointId[if (screencast) CAPTURE_DEVICE_SCREEN else CAPTURE_DEVICE_CAMERA], if (screencast) participant.presentationEndpoint else participant.videoEndpoint)
	}

	fun setBackgroundSinks(local: VideoSink?, remote: VideoSink?) {
		localSink[CAPTURE_DEVICE_CAMERA]?.setBackground(local)
		remoteSink[CAPTURE_DEVICE_CAMERA]?.setBackground(remote)
	}

	fun swapSinks() {
		localSink[CAPTURE_DEVICE_CAMERA]?.swap()
		remoteSink[CAPTURE_DEVICE_CAMERA]?.swap()
	}

	fun isHangingUp(): Boolean {
		return currentState == STATE_HANGING_UP
	}

	fun onSignalingData(data: TL_updatePhoneCallSignalingData) {
		if (user == null || tgVoip[CAPTURE_DEVICE_CAMERA] == null || tgVoip[CAPTURE_DEVICE_CAMERA]!!.isGroup || getCallID() != data.phone_call_id) {
			return
		}

		tgVoip[CAPTURE_DEVICE_CAMERA]?.onSignalingDataReceive(data.data)
	}

	fun getSelfId(): Long {
		val groupCallPeer = groupCallPeer ?: return UserConfig.getInstance(currentAccount).clientUserId

		return when (groupCallPeer) {
			is TL_inputPeerUser -> {
				groupCallPeer.user_id
			}

			is TL_inputPeerChannel -> {
				-groupCallPeer.channel_id
			}

			else -> {
				-groupCallPeer.chat_id
			}
		}
	}

	fun onGroupCallParticipantsUpdate(update: TL_updateGroupCallParticipants) {
		if (chat == null || groupCall == null || groupCall?.call?.id != update.call.id) {
			return
		}

		val selfId = getSelfId()

		update.participants.forEach { participant ->
			if (participant.left) {
				if (participant.source != 0) {
					if (participant.source == mySource[CAPTURE_DEVICE_CAMERA]) {
						var selfCount = 0

						update.participants.forEach { p ->
							if (p.self || p.source == mySource[CAPTURE_DEVICE_CAMERA]) {
								selfCount++
							}
						}

						if (selfCount > 1) {
							hangUp(2)
							return
						}
					}
				}
			}
			else if (MessageObject.getPeerId(participant.peer) == selfId) {
				if (participant.source != mySource[CAPTURE_DEVICE_CAMERA] && mySource[CAPTURE_DEVICE_CAMERA] != 0 && participant.source != 0) {
					FileLog.d("source mismatch my = " + mySource[CAPTURE_DEVICE_CAMERA] + " psrc = " + participant.source)
					hangUp(2)
					return
				}
				else if (ChatObject.isChannel(chat) && currentGroupModeStreaming && participant.can_self_unmute) {
					switchingStream = true
					createGroupInstance(CAPTURE_DEVICE_CAMERA, false)
				}

				if (participant.muted) {
					setMicMute(mute = true, hold = false, send = false)
				}
			}
		}
	}

	fun onGroupCallUpdated(call: GroupCall) {
		if (chat == null) {
			return
		}

		if (groupCall?.call?.id != call.id) {
			return
		}

		if (groupCall?.call is TL_groupCallDiscarded) {
			hangUp(2)
			return
		}

		var newModeStreaming = false

		if (myParams != null) {
			try {
				val `object` = JSONObject(myParams!!.data)
				newModeStreaming = `object`.optBoolean("stream")
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		if ((currentState == STATE_WAIT_INIT || newModeStreaming != currentGroupModeStreaming) && myParams != null) {
			if (playedConnectedSound && newModeStreaming != currentGroupModeStreaming) {
				switchingStream = true
			}

			currentGroupModeStreaming = newModeStreaming

			try {
				if (newModeStreaming) {
					tgVoip[CAPTURE_DEVICE_CAMERA]?.prepareForStream(groupCall?.call != null && groupCall?.call?.rtmp_stream == true)
				}
				else {
					tgVoip[CAPTURE_DEVICE_CAMERA]?.setJoinResponsePayload(myParams!!.data)
				}

				dispatchStateChanged(STATE_WAIT_INIT_ACK)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun onCallUpdated(phoneCall: PhoneCall?) {
		if (user == null) {
			return
		}

		if (privateCall == null) {
			pendingUpdates.add(phoneCall)
			return
		}

		if (phoneCall == null) {
			return
		}

		if (phoneCall.id != privateCall?.id) {
			FileLog.w("onCallUpdated called with wrong call id (got " + phoneCall.id + ", expected " + privateCall!!.id + ")")
			return
		}

		if (phoneCall.access_hash == 0L) {
			phoneCall.access_hash = privateCall?.access_hash ?: 0L
		}

		FileLog.d("Call updated: $phoneCall")

		privateCall = phoneCall

		if (phoneCall is TL_phoneCallDiscarded) {
			needSendDebugLog = phoneCall.need_debug
			needRateCall = phoneCall.need_rating

			FileLog.d("call discarded, stopping service")

			if (phoneCall.reason is TL_phoneCallDiscardReasonBusy) {
				dispatchStateChanged(STATE_BUSY)

				playingSound = true

				Utilities.globalQueue.postRunnable {
					soundPool?.play(spBusyId, 1f, 1f, 0, -1, 1f)
				}

				AndroidUtilities.runOnUIThread(afterSoundRunnable, 1500)
				endConnectionServiceCall(1500)
				stopSelf()
			}
			else {
				callEnded()
			}
		}
		else if (phoneCall is TL_phoneCall && authKey == null) {
			if (phoneCall.g_a_or_b == null) {
				FileLog.w("stopping VoIP service, Ga == null")
				callFailed()
				return
			}

			if (!g_a_hash.contentEquals(Utilities.computeSHA256(phoneCall.g_a_or_b, 0, phoneCall.g_a_or_b.size.toLong()))) {
				FileLog.w("stopping VoIP service, Ga hash doesn't match")
				callFailed()
				return
			}

			g_a = phoneCall.g_a_or_b

			var g_a = BigInteger(1, phoneCall.g_a_or_b)
			val p = BigInteger(1, MessagesStorage.getInstance(currentAccount).secretPBytes)

			if (!Utilities.isGoodGaAndGb(g_a, p)) {
				FileLog.w("stopping VoIP service, bad Ga and Gb (accepting)")
				callFailed()
				return
			}

			g_a = g_a.modPow(BigInteger(1, a_or_b), p)

			var authKey = g_a.toByteArray()

			if (authKey.size > 256) {
				val correctedAuth = ByteArray(256)
				System.arraycopy(authKey, authKey.size - 256, correctedAuth, 0, 256)
				authKey = correctedAuth
			}
			else if (authKey.size < 256) {
				val correctedAuth = ByteArray(256)

				System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.size, authKey.size)

				for (a in 0 until 256 - authKey.size) {
					correctedAuth[a] = 0
				}

				authKey = correctedAuth
			}

			val authKeyHash = Utilities.computeSHA1(authKey)
			val authKeyId = ByteArray(8)

			System.arraycopy(authKeyHash, authKeyHash.size - 8, authKeyId, 0, 8)

			this@VoIPService.authKey = authKey

			keyFingerprint = Utilities.bytesToLong(authKeyId)

			if (keyFingerprint != phoneCall.key_fingerprint) {
				FileLog.w("key fingerprints don't match")
				callFailed()
				return
			}

			initiateActualEncryptedCall()
		}
		else if (phoneCall is TL_phoneCallAccepted && authKey == null) {
			processAcceptedCall()
		}
		else {
			if (currentState == STATE_WAITING && phoneCall.receive_date != 0) {
				dispatchStateChanged(STATE_RINGING)

				FileLog.d("!!!!!! CALL RECEIVED")

				if (connectingSoundRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(connectingSoundRunnable)
					connectingSoundRunnable = null
				}

				Utilities.globalQueue.postRunnable {
					if (spPlayId != 0) {
						soundPool?.stop(spPlayId)
					}

					spPlayId = soundPool?.play(spRingbackID, 1f, 1f, 0, -1, 1f) ?: 0
				}

				if (timeoutRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(timeoutRunnable)
					timeoutRunnable = null
				}

				timeoutRunnable = Runnable {
					timeoutRunnable = null
					declineIncomingCall(DISCARD_REASON_MISSED, null)
				}

				AndroidUtilities.runOnUIThread(timeoutRunnable, MessagesController.getInstance(currentAccount).callRingTimeout.toLong())
			}
		}
	}

	private fun startRatingActivity() {
		try {
			PendingIntent.getActivity(this@VoIPService, 0, Intent(this@VoIPService, VoIPFeedbackActivity::class.java).putExtra("call_id", privateCall!!.id).putExtra("call_access_hash", privateCall!!.access_hash).putExtra("call_video", privateCall!!.video).putExtra("account", currentAccount).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE).send()
		}
		catch (x: Exception) {
			FileLog.e("Error starting incall activity", x)
		}
	}

	fun getEncryptionKey(): ByteArray? {
		return authKey
	}

	private fun processAcceptedCall() {
		dispatchStateChanged(STATE_EXCHANGING_KEYS)

		val p = BigInteger(1, MessagesStorage.getInstance(currentAccount).secretPBytes)
		var i_authKey = BigInteger(1, privateCall!!.g_b)

		if (!Utilities.isGoodGaAndGb(i_authKey, p)) {
			FileLog.w("stopping VoIP service, bad Ga and Gb")
			callFailed()
			return
		}

		i_authKey = i_authKey.modPow(BigInteger(1, a_or_b), p)

		var authKey = i_authKey.toByteArray()

		if (authKey.size > 256) {
			val correctedAuth = ByteArray(256)
			System.arraycopy(authKey, authKey.size - 256, correctedAuth, 0, 256)
			authKey = correctedAuth
		}
		else if (authKey.size < 256) {
			val correctedAuth = ByteArray(256)

			System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.size, authKey.size)

			for (a in 0 until 256 - authKey.size) {
				correctedAuth[a] = 0
			}

			authKey = correctedAuth
		}

		val authKeyHash = Utilities.computeSHA1(authKey)
		val authKeyId = ByteArray(8)

		System.arraycopy(authKeyHash, authKeyHash.size - 8, authKeyId, 0, 8)

		val fingerprint = Utilities.bytesToLong(authKeyId)
		this.authKey = authKey
		keyFingerprint = fingerprint

		val req = TL_phone_confirmCall()
		req.g_a = g_a
		req.key_fingerprint = fingerprint
		req.peer = TL_inputPhoneCall()
		req.peer.id = privateCall!!.id
		req.peer.access_hash = privateCall!!.access_hash
		req.protocol = TL_phoneCallProtocol()
		req.protocol.max_layer = getConnectionMaxLayer()
		req.protocol.min_layer = CALL_MIN_LAYER
		req.protocol.udp_reflector = true
		req.protocol.udp_p2p = req.protocol.udp_reflector
		req.protocol.library_versions.addAll(AVAILABLE_VERSIONS)

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
			AndroidUtilities.runOnUIThread {
				if (error != null) {
					callFailed()
				}
				else {
					privateCall = (response as TL_phone_phoneCall).phone_call
					initiateActualEncryptedCall()
				}
			}
		}
	}

	private fun convertDataSavingMode(mode: Int): Int {
		if (mode != DATA_SAVING_ROAMING) {
			return mode
		}

		return if (ApplicationLoader.isRoaming) DATA_SAVING_MOBILE else DATA_SAVING_NEVER
	}

	fun migrateToChat(newChat: Chat?) {
		chat = newChat
	}

	fun setGroupCallPeer(peer: InputPeer?) {
		if (groupCall == null) {
			return
		}

		groupCallPeer = peer
		groupCall?.setSelfPeer(groupCallPeer)

		val chatFull = MessagesController.getInstance(currentAccount).getChatFull(groupCall!!.chatId)

		if (chatFull != null) {
			chatFull.groupcall_default_join_as = groupCall!!.selfPeer

			if (chatFull.groupcall_default_join_as != null) {
				if (chatFull is TL_chatFull) {
					chatFull.flags = chatFull.flags or 32768
				}
				else {
					chatFull.flags = chatFull.flags or 67108864
				}
			}
			else {
				if (chatFull is TL_chatFull) {
					chatFull.flags = chatFull.flags and 32768.inv()
				}
				else {
					chatFull.flags = chatFull.flags and 67108864.inv()
				}
			}
		}

		createGroupInstance(CAPTURE_DEVICE_CAMERA, true)

		if (videoState[CAPTURE_DEVICE_SCREEN] == VIDEO_STATE_ACTIVE) {
			createGroupInstance(CAPTURE_DEVICE_SCREEN, true)
		}
	}

	private fun startGroupCall(ssrc: Int, json: String?, create: Boolean) {
		if (sharedInstance !== this) {
			return
		}

		if (createGroupCall) {
			groupCall = ChatObject.Call()
			groupCall?.call = TL_groupCall()
			groupCall?.call?.participants_count = 0
			groupCall?.call?.version = 1
			groupCall?.call?.can_start_video = true
			groupCall?.call?.can_change_join_muted = true
			groupCall?.chatId = chat!!.id
			groupCall?.currentAccount = AccountInstance.getInstance(currentAccount)
			groupCall?.setSelfPeer(groupCallPeer)
			groupCall?.createNoVideoParticipant()

			dispatchStateChanged(STATE_CREATING)

			val req = TL_phone_createGroupCall()
			req.peer = MessagesController.getInputPeer(chat)
			req.random_id = Utilities.random.nextInt()

			if (scheduleDate != 0) {
				req.schedule_date = scheduleDate
				req.flags = req.flags or 2
			}

			ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
				if (response != null) {
					val updates = response as Updates

					for (a in updates.updates.indices) {
						val update = updates.updates[a]

						if (update is TL_updateGroupCall) {
							AndroidUtilities.runOnUIThread {
								if (sharedInstance == null) {
									return@runOnUIThread
								}

								groupCall?.call?.access_hash = update.call.access_hash
								groupCall?.call?.id = update.call.id

								groupCall?.let {
									MessagesController.getInstance(currentAccount).putGroupCall(it.chatId, it)
								}

								startGroupCall(0, null, false)
							}

							break
						}
					}

					MessagesController.getInstance(currentAccount).processUpdates(updates, false)
				}
				else {
					AndroidUtilities.runOnUIThread {
						NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 6, error?.text)
						hangUp(0)
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)

			createGroupCall = false

			return
		}

		if (json == null) {
			if (groupCall == null) {
				groupCall = MessagesController.getInstance(currentAccount).getGroupCall(chat!!.id, false)
				groupCall?.setSelfPeer(groupCallPeer)
			}

			configureDeviceForCall()
			showNotification()

			AndroidUtilities.runOnUIThread {
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.didStartedCall)
			}

			createGroupInstance(CAPTURE_DEVICE_CAMERA, false)
		}
		else {
			if (sharedInstance == null || groupCall == null) {
				return
			}

			dispatchStateChanged(STATE_WAIT_INIT)

			FileLog.d("initial source = $ssrc")

			val req = TL_phone_joinGroupCall()
			req.muted = true
			req.video_stopped = videoState[CAPTURE_DEVICE_CAMERA] != VIDEO_STATE_ACTIVE
			req.call = groupCall!!.inputGroupCall
			req.params = TL_dataJSON()
			req.params.data = json

			if (!TextUtils.isEmpty(joinHash)) {
				req.invite_hash = joinHash
				req.flags = req.flags or 2
			}

			if (groupCallPeer != null) {
				req.join_as = groupCallPeer
			}
			else {
				req.join_as = TL_inputPeerUser()
				req.join_as.user_id = AccountInstance.getInstance(currentAccount).userConfig.getClientUserId()
			}

			ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
				if (response != null) {
					AndroidUtilities.runOnUIThread {
						mySource[CAPTURE_DEVICE_CAMERA] = ssrc
					}

					val updates = response as Updates
					val selfId = getSelfId()
					var a = 0
					val N = updates.updates.size

					updates.updates.forEach { update ->
						if (update is TL_updateGroupCallParticipants) {
							for (participant in update.participants) {
								if (MessageObject.getPeerId(participant.peer) == selfId) {
									AndroidUtilities.runOnUIThread {
										mySource[CAPTURE_DEVICE_CAMERA] = participant.source
									}

									FileLog.d("join source = " + participant.source)

									break
								}
							}
						}
						else if (update is TL_updateGroupCallConnection) {
							if (!update.presentation) {
								myParams = update.params
							}
						}
					}

					MessagesController.getInstance(currentAccount).processUpdates(updates, false)

					AndroidUtilities.runOnUIThread {
						groupCall?.loadMembers(create)
					}

					startGroupCheckShortpoll()
				}
				else {
					AndroidUtilities.runOnUIThread {
						when (error?.text) {
							"JOIN_AS_PEER_INVALID" -> {
								val chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat!!.id)

								if (chatFull != null) {
									if (chatFull is TL_chatFull) {
										chatFull.flags = chatFull.flags and 32768.inv()
									}
									else {
										chatFull.flags = chatFull.flags and 67108864.inv()
									}

									chatFull.groupcall_default_join_as = null

									JoinCallAlert.resetCache()
								}

								hangUp(2)
							}

							"GROUPCALL_SSRC_DUPLICATE_MUCH" -> {
								createGroupInstance(CAPTURE_DEVICE_CAMERA, false)
							}

							else -> {
								if ("GROUPCALL_INVALID" == error?.text) {
									MessagesController.getInstance(currentAccount).loadFullChat(chat!!.id, 0, true)
								}

								NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 6, error?.text)

								hangUp(0)
							}
						}
					}
				}
			}
		}
	}

	private fun startScreenCapture(ssrc: Int, json: String?) {
		if (sharedInstance == null || groupCall == null) {
			return
		}

		mySource[CAPTURE_DEVICE_SCREEN] = 0

		val req = TL_phone_joinGroupCallPresentation()

		req.call = groupCall!!.inputGroupCall
		req.params = TL_dataJSON()
		req.params.data = json

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
			if (response != null) {
				AndroidUtilities.runOnUIThread {
					mySource[CAPTURE_DEVICE_SCREEN] = ssrc
				}

				val updates = response as Updates

				AndroidUtilities.runOnUIThread {
					if (tgVoip[CAPTURE_DEVICE_SCREEN] != null) {
						val selfId = getSelfId()
						var a = 0
						val N = updates.updates.size

						while (a < N) {
							val update = updates.updates[a]

							if (update is TL_updateGroupCallConnection) {
								if (update.presentation) {
									tgVoip[CAPTURE_DEVICE_SCREEN]!!.setJoinResponsePayload(update.params.data)
								}
							}
							else if (update is TL_updateGroupCallParticipants) {
								var b = 0
								val N2 = update.participants.size

								while (b < N2) {
									val participant = update.participants[b]

									if (MessageObject.getPeerId(participant.peer) == selfId) {
										if (participant.presentation != null) {
											if (participant.presentation.flags and 2 != 0) {
												mySource[CAPTURE_DEVICE_SCREEN] = participant.presentation.audio_source
											}
											else {
												var c = 0
												val N3 = participant.presentation.source_groups.size

												while (c < N3) {
													val sourceGroup = participant.presentation.source_groups[c]

													if (sourceGroup.sources.size > 0) {
														mySource[CAPTURE_DEVICE_SCREEN] = sourceGroup.sources[0]
													}

													c++
												}
											}
										}

										break
									}

									b++
								}
							}

							a++
						}
					}
				}

				MessagesController.getInstance(currentAccount).processUpdates(updates, false)

				startGroupCheckShortpoll()
			}
			else {
				AndroidUtilities.runOnUIThread {
					if ("GROUPCALL_VIDEO_TOO_MUCH" == error?.text) {
						groupCall!!.reloadGroupCall()
					}
					else if ("JOIN_AS_PEER_INVALID" == error?.text) {
						val chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat!!.id)

						if (chatFull != null) {
							if (chatFull is TL_chatFull) {
								chatFull.flags = chatFull.flags and 32768.inv()
							}
							else {
								chatFull.flags = chatFull.flags and 67108864.inv()
							}

							chatFull.groupcall_default_join_as = null

							JoinCallAlert.resetCache()
						}

						hangUp(2)
					}
					else if ("GROUPCALL_SSRC_DUPLICATE_MUCH" == error?.text) {
						createGroupInstance(CAPTURE_DEVICE_SCREEN, false)
					}
					else {
						if ("GROUPCALL_INVALID" == error?.text) {
							MessagesController.getInstance(currentAccount).loadFullChat(chat!!.id, 0, true)
						}
					}
				}
			}
		}
	}

	private var shortPollRunnable: Runnable? = null
	private var checkRequestId = 0

	private fun startGroupCheckShortpoll() {
		if (shortPollRunnable != null || sharedInstance == null || groupCall == null || mySource[CAPTURE_DEVICE_CAMERA] == 0 && mySource[CAPTURE_DEVICE_SCREEN] == 0 && !(groupCall?.call != null && groupCall?.call?.rtmp_stream == true)) {
			return
		}

		AndroidUtilities.runOnUIThread(Runnable label@{
			if (shortPollRunnable == null || sharedInstance == null || groupCall == null || mySource[CAPTURE_DEVICE_CAMERA] == 0 && mySource[CAPTURE_DEVICE_SCREEN] == 0 && !(groupCall?.call != null && groupCall?.call?.rtmp_stream == true)) {
				return@label
			}

			val req = TL_phone_checkGroupCall()
			req.call = groupCall!!.inputGroupCall

			for (i in mySource) {
				if (i != 0) {
					req.sources.add(i)
				}
			}

			checkRequestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					if (shortPollRunnable == null || sharedInstance == null || groupCall == null) {
						return@runOnUIThread
					}

					shortPollRunnable = null
					checkRequestId = 0

					var recreateCamera = false
					var recreateScreenCapture = false

					if (response is Vector) {
						if (mySource[CAPTURE_DEVICE_CAMERA] != 0 && req.sources.contains(mySource[CAPTURE_DEVICE_CAMERA])) {
							if (!response.objects.contains(mySource[CAPTURE_DEVICE_CAMERA])) {
								recreateCamera = true
							}
						}

						if (mySource[CAPTURE_DEVICE_SCREEN] != 0 && req.sources.contains(mySource[CAPTURE_DEVICE_SCREEN])) {
							if (!response.objects.contains(mySource[CAPTURE_DEVICE_SCREEN])) {
								recreateScreenCapture = true
							}
						}
					}
					else if (error != null && error.code == 400) {
						recreateCamera = true

						if (mySource[CAPTURE_DEVICE_SCREEN] != 0 && req.sources.contains(mySource[CAPTURE_DEVICE_SCREEN])) {
							recreateScreenCapture = true
						}
					}

					if (recreateCamera) {
						createGroupInstance(CAPTURE_DEVICE_CAMERA, false)
					}

					if (recreateScreenCapture) {
						createGroupInstance(CAPTURE_DEVICE_SCREEN, false)
					}

					if (mySource[CAPTURE_DEVICE_SCREEN] != 0 || mySource[CAPTURE_DEVICE_CAMERA] != 0 || groupCall?.call != null && groupCall?.call?.rtmp_stream == true) {
						startGroupCheckShortpoll()
					}
				}
			}
		}.also {
			shortPollRunnable = it
		}, 4000)
	}

	private fun cancelGroupCheckShortPoll() {
		if (mySource[CAPTURE_DEVICE_SCREEN] != 0 || mySource[CAPTURE_DEVICE_CAMERA] != 0) {
			return
		}

		if (checkRequestId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(checkRequestId, false)
			checkRequestId = 0
		}

		if (shortPollRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(shortPollRunnable)
			shortPollRunnable = null
		}
	}

	private class RequestedParticipant(var participant: TL_groupCallParticipant, var audioSsrc: Int)

	private fun broadcastUnknownParticipants(taskPtr: Long, unknown: IntArray) {
		if (groupCall == null || tgVoip[CAPTURE_DEVICE_CAMERA] == null) {
			return
		}

		val selfId = getSelfId()
		var participants: ArrayList<RequestedParticipant>? = null

		for (i in unknown) {
			var p = groupCall?.participantsBySources?.get(i)

			if (p == null) {
				p = groupCall?.participantsByVideoSources?.get(i)

				if (p == null) {
					p = groupCall?.participantsByPresentationSources?.get(i)
				}
			}

			if (p == null || MessageObject.getPeerId(p.peer) == selfId || p.source == 0) {
				continue
			}

			if (participants == null) {
				participants = ArrayList()
			}

			participants.add(RequestedParticipant(p, i))
		}

		if (participants != null) {
			val ssrcs = IntArray(participants.size)

			var a = 0
			var n = participants.size

			while (a < n) {
				val p = participants[a]
				ssrcs[a] = p.audioSsrc
				a++
			}

			tgVoip[CAPTURE_DEVICE_CAMERA]?.onMediaDescriptionAvailable(taskPtr, ssrcs)

			a = 0
			n = participants.size

			while (a < n) {
				val p = participants[a]

				if (p.participant.muted_by_you) {
					tgVoip[CAPTURE_DEVICE_CAMERA]?.setVolume(p.audioSsrc, 0.0)
				}
				else {
					tgVoip[CAPTURE_DEVICE_CAMERA]?.setVolume(p.audioSsrc, ChatObject.getParticipantVolume(p.participant) / 10000.0)
				}

				a++
			}
		}
	}

	private fun createGroupInstance(type: Int, switchAccount: Boolean) {
		if (switchAccount) {
			mySource[type] = 0

			if (type == CAPTURE_DEVICE_CAMERA) {
				switchingAccount = true
			}
		}

		cancelGroupCheckShortPoll()

		if (type == CAPTURE_DEVICE_CAMERA) {
			wasConnected = false
		}
		else if (!wasConnected) {
			reconnectScreenCapture = true
			return
		}

		var created = false

		if (tgVoip[type] == null) {
			created = true

			val logFilePath = if (BuildConfig.DEBUG) getLogFilePath("voip_" + type + "_" + groupCall?.call?.id) else getLogFilePath(groupCall!!.call!!.id, false)

			tgVoip[type] = NativeInstance.makeGroup(logFilePath, captureDevice[type], type == CAPTURE_DEVICE_SCREEN, type == CAPTURE_DEVICE_CAMERA && SharedConfig.noiseSupression, { ssrc: Int, json: String? ->
				if (type == CAPTURE_DEVICE_CAMERA) {
					startGroupCall(ssrc, json, true)
				}
				else {
					startScreenCapture(ssrc, json)
				}
			}, { uids, levels, voice ->
				if (sharedInstance == null || groupCall == null || type != CAPTURE_DEVICE_CAMERA) {
					return@makeGroup
				}

				groupCall?.processVoiceLevelsUpdate(uids, levels, voice)

				var maxAmplitude = 0f
				var hasOther = false

				for (a in uids.indices) {
					if (uids[a] == 0) {
						if (lastTypingTimeSend < SystemClock.uptimeMillis() - 5000 && levels[a] > 0.1f && voice[a]) {
							lastTypingTimeSend = SystemClock.uptimeMillis()

							val req = TL_messages_setTyping()
							req.action = TL_speakingInGroupCallAction()
							req.peer = MessagesController.getInputPeer(chat)

							ConnectionsManager.getInstance(currentAccount).sendRequest(req) { _, _ ->
								// unused
							}
						}

						NotificationCenter.globalInstance.postNotificationName(NotificationCenter.webRtcMicAmplitudeEvent, levels[a])

						continue
					}

					hasOther = true

					maxAmplitude = max(maxAmplitude, levels[a])
				}

				if (hasOther) {
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.webRtcSpeakerAmplitudeEvent, maxAmplitude)

					audioLevelsCallback?.run(uids, levels, voice)
				}
			}, { taskPtr, unknown ->
				if (sharedInstance == null || groupCall == null || type != CAPTURE_DEVICE_CAMERA) {
					return@makeGroup
				}

				groupCall?.processUnknownVideoParticipants(unknown) {
					if (sharedInstance == null || groupCall == null) {
						return@processUnknownVideoParticipants
					}

					broadcastUnknownParticipants(taskPtr, unknown)
				}
			}, { timestamp, duration, videoChannel, quality ->
				if (type != CAPTURE_DEVICE_CAMERA) {
					return@makeGroup
				}

				val req = TL_upload_getFile()
				req.limit = 128 * 1024

				val inputGroupCallStream = TL_inputGroupCallStream()
				inputGroupCallStream.call = groupCall!!.inputGroupCall
				inputGroupCallStream.time_ms = timestamp

				if (duration == 500L) {
					inputGroupCallStream.scale = 1
				}

				if (videoChannel != 0) {
					inputGroupCallStream.flags = inputGroupCallStream.flags or 1
					inputGroupCallStream.video_channel = videoChannel
					inputGroupCallStream.video_quality = quality
				}

				req.location = inputGroupCallStream

				val key = if (videoChannel == 0) "" + timestamp else videoChannel.toString() + "_" + timestamp + "_" + quality

				val reqId = AccountInstance.getInstance(currentAccount).connectionsManager.sendRequest(req, { response, error, responseTime ->
					AndroidUtilities.runOnUIThread {
						currentStreamRequestTimestamp.remove(key)
					}

					if (tgVoip[type] == null) {
						return@sendRequest
					}

					if (response != null) {
						val res = response as TL_upload_file
						tgVoip[type]?.onStreamPartAvailable(timestamp, res.bytes.buffer, res.bytes.limit(), responseTime, videoChannel, quality)
					}
					else {
						if ("GROUPCALL_JOIN_MISSING" == error.text) {
							AndroidUtilities.runOnUIThread {
								createGroupInstance(type, false)
							}
						}
						else {
							val status: Int = if ("TIME_TOO_BIG" == error.text || error.text.startsWith("FLOOD_WAIT")) {
								0
							}
							else {
								-1
							}

							tgVoip[type]?.onStreamPartAvailable(timestamp, null, status, responseTime, videoChannel, quality)
						}
					}
				}, ConnectionsManager.RequestFlagFailOnServerErrors, ConnectionsManager.ConnectionTypeDownload, groupCall!!.call!!.stream_dc_id)

				AndroidUtilities.runOnUIThread {
					currentStreamRequestTimestamp[key] = reqId
				}
			}, { timestamp, _, videoChannel, quality ->
				if (type != CAPTURE_DEVICE_CAMERA) {
					return@makeGroup
				}

				AndroidUtilities.runOnUIThread {
					val key = if (videoChannel == 0) "" + timestamp else videoChannel.toString() + "_" + timestamp + "_" + quality
					val reqId = currentStreamRequestTimestamp[key]

					if (reqId != null) {
						AccountInstance.getInstance(currentAccount).connectionsManager.cancelRequest(reqId, true)
						currentStreamRequestTimestamp.remove(key)
					}
				}
			}) { taskPtr ->
				if (groupCall != null && groupCall!!.call != null && groupCall!!.call!!.rtmp_stream) {
					val req = TL_phone_getGroupCallStreamChannels()
					req.call = groupCall!!.inputGroupCall

					if (groupCall == null || groupCall!!.call == null || tgVoip[type] == null) {
						tgVoip[type]?.onRequestTimeComplete(taskPtr, 0)
						return@makeGroup
					}

					ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error, _ ->
						var currentTime = 0L

						if (error == null) {
							val res = response as TL_phone_groupCallStreamChannels

							if (res.channels.isNotEmpty()) {
								currentTime = res.channels[0].last_timestamp_ms
							}

							if (!groupCall!!.loadedRtmpStreamParticipant) {
								groupCall?.createRtmpStreamParticipant(res.channels)
								groupCall?.loadedRtmpStreamParticipant = true
							}
						}

						tgVoip[type]?.onRequestTimeComplete(taskPtr, currentTime)
					}, ConnectionsManager.RequestFlagFailOnServerErrors, ConnectionsManager.ConnectionTypeDownload, groupCall!!.call!!.stream_dc_id)
				}
				else {
					tgVoip[type]?.onRequestTimeComplete(taskPtr, ConnectionsManager.getInstance(currentAccount).currentTimeMillis)
				}
			}

			tgVoip[type]?.setOnStateUpdatedListener { state, inTransition ->
				updateConnectionState(type, state, inTransition)
			}
		}

		tgVoip[type]?.resetGroupInstance(!created, false)

		if (captureDevice[type] != 0L) {
			destroyCaptureDevice[type] = false
		}

		if (type == CAPTURE_DEVICE_CAMERA) {
			dispatchStateChanged(STATE_WAIT_INIT)
		}
	}

	private fun updateConnectionState(type: Int, state: Int, inTransition: Boolean) {
		if (type != CAPTURE_DEVICE_CAMERA) {
			return
		}

		dispatchStateChanged(if (state == 1 || switchingStream) STATE_ESTABLISHED else STATE_RECONNECTING)

		if (switchingStream && (state == 0 || state == 1 && inTransition)) {
			AndroidUtilities.runOnUIThread(Runnable label@{
				if (switchingStreamTimeoutRunnable == null) {
					return@label
				}

				switchingStream = false

				updateConnectionState(type, 0, true)

				switchingStreamTimeoutRunnable = null
			}.also {
				switchingStreamTimeoutRunnable = it
			}, 3000)
		}

		if (state == 0) {
			startGroupCheckShortpoll()

			if (playedConnectedSound && spPlayId == 0 && !switchingStream && !switchingAccount) {
				Utilities.globalQueue.postRunnable {
					if (spPlayId != 0) {
						soundPool?.stop(spPlayId)
					}

					spPlayId = soundPool?.play(spVoiceChatConnecting, 1.0f, 1.0f, 0, -1, 1f) ?: 0
				}
			}
		}
		else {
			cancelGroupCheckShortPoll()

			if (!inTransition) {
				switchingStream = false
				switchingAccount = false
			}

			if (switchingStreamTimeoutRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(switchingStreamTimeoutRunnable)
				switchingStreamTimeoutRunnable = null
			}

			if (playedConnectedSound) {
				Utilities.globalQueue.postRunnable {
					if (spPlayId != 0) {
						soundPool?.stop(spPlayId)
						spPlayId = 0
					}
				}

				if (connectingSoundRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(connectingSoundRunnable)
					connectingSoundRunnable = null
				}
			}
			else {
				playConnectedSound()
			}

			if (!wasConnected) {
				wasConnected = true

				if (reconnectScreenCapture) {
					createGroupInstance(CAPTURE_DEVICE_SCREEN, false)
					reconnectScreenCapture = false
				}

				val instance = tgVoip[CAPTURE_DEVICE_CAMERA]

				if (instance != null) {
					if (!micMute) {
						instance.setMuteMicrophone(false)
					}
				}

				setParticipantsVolume()
			}
		}
	}

	fun setParticipantsVolume() {
		val instance = tgVoip[CAPTURE_DEVICE_CAMERA] ?: return

		var a = 0
		val N = groupCall!!.participants.size()

		while (a < N) {
			val participant = groupCall!!.participants.valueAt(a)

			if (participant!!.self || participant.source == 0 || !participant.can_self_unmute && participant.muted) {
				a++
				continue
			}

			if (participant.muted_by_you) {
				setParticipantVolume(participant, 0)
			}
			else {
				setParticipantVolume(participant, ChatObject.getParticipantVolume(participant))
			}

			a++
		}
	}

	fun setParticipantVolume(participant: TL_groupCallParticipant, volume: Int) {
		tgVoip[CAPTURE_DEVICE_CAMERA]?.setVolume(participant.source, volume / 10000.0)

		if (participant.presentation != null && participant.presentation.audio_source != 0) {
			tgVoip[CAPTURE_DEVICE_CAMERA]?.setVolume(participant.presentation.audio_source, volume / 10000.0)
		}
	}

	fun isSwitchingStream(): Boolean {
		return switchingStream
	}

	private fun initiateActualEncryptedCall() {
		if (timeoutRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(timeoutRunnable)
			timeoutRunnable = null
		}

		try {
			FileLog.d("InitCall: keyID=$keyFingerprint")

			val nprefs = MessagesController.getNotificationsSettings(currentAccount)
			val set = nprefs.getStringSet("calls_access_hashes", null)

			val hashes = if (set != null) {
				HashSet(set)
			}
			else {
				HashSet()
			}

			hashes.add(privateCall!!.id.toString() + " " + privateCall!!.access_hash + " " + System.currentTimeMillis())

			while (hashes.size > 20) {
				var oldest: String? = null
				var oldestTime = Long.MAX_VALUE
				val itr = hashes.iterator()

				while (itr.hasNext()) {
					val item = itr.next()
					val s = item.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

					if (s.size < 2) {
						itr.remove()
					}
					else {
						try {
							val t = s[2].toLong()

							if (t < oldestTime) {
								oldestTime = t
								oldest = item
							}
						}
						catch (x: Exception) {
							itr.remove()
						}
					}
				}

				if (oldest != null) {
					hashes.remove(oldest)
				}
			}

			nprefs.edit().putStringSet("calls_access_hashes", hashes).commit()

			var sysAecAvailable = false
			var sysNsAvailable = false

			try {
				sysAecAvailable = AcousticEchoCanceler.isAvailable()
			}
			catch (e: Exception) {
				// ignored
			}

			try {
				sysNsAvailable = NoiseSuppressor.isAvailable()
			}
			catch (e: Exception) {
				// ignored
			}

			val preferences = MessagesController.getGlobalMainSettings()

			// config
			val messagesController = MessagesController.getInstance(currentAccount)
			val initializationTimeout = messagesController.callConnectTimeout / 1000.0
			val receiveTimeout = messagesController.callPacketTimeout / 1000.0
			val voipDataSaving = convertDataSavingMode(preferences.getInt("VoipDataSaving", dataSavingDefault))
			val serverConfig = getGlobalServerConfig()
			val enableAec = !(sysAecAvailable && serverConfig.useSystemAec)
			val enableNs = !(sysNsAvailable && serverConfig.useSystemNs)
			val logFilePath = if (BuildConfig.DEBUG) getLogFilePath("voip" + privateCall!!.id) else getLogFilePath(privateCall!!.id, false)
			val statsLogFilePath = getLogFilePath(privateCall!!.id, true)
			val config = Config(initializationTimeout, receiveTimeout, voipDataSaving, privateCall!!.p2p_allowed, enableAec, enableNs, true, false, serverConfig.enableStunMarking, logFilePath, statsLogFilePath, privateCall!!.protocol.max_layer)

			// persistent state
			val persistentStateFilePath = File(ApplicationLoader.applicationContext.cacheDir, "voip_persistent_state.json").absolutePath

			// endpoints
			val forceTcp = preferences.getBoolean("dbg_force_tcp_in_calls", false)
			val endpointType = if (forceTcp) ENDPOINT_TYPE_TCP_RELAY else ENDPOINT_TYPE_UDP_RELAY
			val endpoints = arrayOfNulls<Endpoint>(privateCall!!.connections.size)

			for (i in endpoints.indices) {
				val connection = privateCall!!.connections[i]
				endpoints[i] = Endpoint(connection is TL_phoneConnectionWebrtc, connection.id, connection.ip, connection.ipv6, connection.port, endpointType, connection.peer_tag, connection.turn, connection.stun, connection.username, connection.password, connection.tcp)
			}

			if (forceTcp) {
				AndroidUtilities.runOnUIThread {
					Toast.makeText(this@VoIPService, "This call uses TCP which will degrade its quality.", Toast.LENGTH_SHORT).show()
				}
			}

			// proxy
			var proxy: Proxy? = null

			if (preferences.getBoolean("proxy_enabled", false) && preferences.getBoolean("proxy_enabled_calls", false)) {
				val server = preferences.getString("proxy_ip", null)
				val secret = preferences.getString("proxy_secret", null)

				if (!TextUtils.isEmpty(server) && TextUtils.isEmpty(secret)) {
					proxy = Proxy(server, preferences.getInt("proxy_port", 0), preferences.getString("proxy_user", null), preferences.getString("proxy_pass", null))
				}
			}

			// encryption key
			val encryptionKey = EncryptionKey(authKey, isOutgoing)
			val newAvailable = "2.7.7" <= privateCall!!.protocol.library_versions[0]

			if (captureDevice[CAPTURE_DEVICE_CAMERA] != 0L && !newAvailable) {
				NativeInstance.destroyVideoCapturer(captureDevice[CAPTURE_DEVICE_CAMERA])
				captureDevice[CAPTURE_DEVICE_CAMERA] = 0
				videoState[CAPTURE_DEVICE_CAMERA] = VIDEO_STATE_INACTIVE
			}

			if (!isOutgoing) {
				if (videoCall && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
					captureDevice[CAPTURE_DEVICE_CAMERA] = NativeInstance.createVideoCapturer(localSink[CAPTURE_DEVICE_CAMERA], if (isFrontFaceCamera) 1 else 0)
					videoState[CAPTURE_DEVICE_CAMERA] = VIDEO_STATE_ACTIVE
				}
				else {
					videoState[CAPTURE_DEVICE_CAMERA] = VIDEO_STATE_INACTIVE
				}
			}

			// init
			tgVoip[CAPTURE_DEVICE_CAMERA] = makeInstance(privateCall!!.protocol.library_versions[0], config, persistentStateFilePath, endpoints, proxy, getNetworkType(), encryptionKey, remoteSink[CAPTURE_DEVICE_CAMERA], captureDevice[CAPTURE_DEVICE_CAMERA]) { _, levels, _ ->
				if (sharedInstance == null || privateCall == null) {
					return@makeInstance
				}

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.webRtcMicAmplitudeEvent, levels[0])
			}

			tgVoip[CAPTURE_DEVICE_CAMERA]?.setOnStateUpdatedListener { newState, inTransition ->
				onConnectionStateChanged(newState, inTransition)
			}

			tgVoip[CAPTURE_DEVICE_CAMERA]?.setOnSignalBarsUpdatedListener { newCount ->
				onSignalBarCountChanged(newCount)
			}

			tgVoip[CAPTURE_DEVICE_CAMERA]?.setOnSignalDataListener { data ->
				this.onSignalingData(data)
			}

			tgVoip[CAPTURE_DEVICE_CAMERA]?.setOnRemoteMediaStateUpdatedListener { audioState, videoState ->
				AndroidUtilities.runOnUIThread {
					remoteAudioState = audioState
					remoteVideoState = videoState

					checkIsNear()

					stateListeners.forEach {
						it.onMediaStateUpdated(audioState, videoState)
					}
				}
			}

			tgVoip[CAPTURE_DEVICE_CAMERA]?.setMuteMicrophone(micMute)

			if (newAvailable != isVideoAvailable) {
				isVideoAvailable = newAvailable

				stateListeners.forEach {
					it.onVideoAvailableChange(isVideoAvailable)
				}
			}

			destroyCaptureDevice[CAPTURE_DEVICE_CAMERA] = false

			AndroidUtilities.runOnUIThread(object : Runnable {
				override fun run() {
					if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
						updateTrafficStats(tgVoip[CAPTURE_DEVICE_CAMERA], null)
						AndroidUtilities.runOnUIThread(this, 5000)
					}
				}
			}, 5000)
		}
		catch (x: Exception) {
			FileLog.e("error starting call", x)
			callFailed()
		}
	}

	fun playConnectedSound() {
		Utilities.globalQueue.postRunnable {
			soundPool?.play(spVoiceChatStartId, 1.0f, 1.0f, 0, 0, 1f)
		}

		playedConnectedSound = true
	}

	private fun startConnectingSound() {
		Utilities.globalQueue.postRunnable {
			if (spPlayId != 0) {
				soundPool?.stop(spPlayId)
			}

			spPlayId = soundPool?.play(spConnectingId, 1f, 1f, 0, -1, 1f) ?: 0

			if (spPlayId == 0) {
				AndroidUtilities.runOnUIThread(object : Runnable {
					override fun run() {
						if (sharedInstance == null) {
							return
						}

						Utilities.globalQueue.postRunnable {
							if (spPlayId == 0) {
								spPlayId = soundPool?.play(spConnectingId, 1f, 1f, 0, -1, 1f) ?: 0
							}

							if (spPlayId == 0) {
								AndroidUtilities.runOnUIThread(this, 100)
							}
							else {
								connectingSoundRunnable = null
							}
						}
					}
				}.also {
					connectingSoundRunnable = it
				}, 100)
			}
		}
	}

	fun onSignalingData(data: ByteArray?) {
		val privateCall = privateCall ?: return

		val req = TL_phone_sendSignalingData()
		req.peer = TL_inputPhoneCall()
		req.peer.access_hash = privateCall.access_hash
		req.peer.id = privateCall.id
		req.data = data

		ConnectionsManager.getInstance(currentAccount).sendRequest(req)
	}

	fun isVideoAvailable(): Boolean {
		return false
		// MARK: uncomment to enable video calls
		// return isVideoAvailable
	}

	fun onMediaButtonEvent(ev: KeyEvent?) {
		if (ev == null) {
			return
		}

		if (ev.keyCode == KeyEvent.KEYCODE_HEADSETHOOK || ev.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || ev.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
			if (ev.action == KeyEvent.ACTION_UP) {
				if (currentState == STATE_WAITING_INCOMING) {
					acceptIncomingCall()
				}
				else {
					setMicMute(!isMicMute(), hold = false, send = true)
				}
			}
		}
	}

	fun getGA(): ByteArray {
		return g_a!!
	}

	fun forceRating() {
		forceRating = true
	}

	private fun getEmoji(): Array<String> {
		val os = ByteArrayOutputStream()

		try {
			os.write(authKey)
			os.write(g_a)
		}
		catch (e: IOException) {
			// ignored
		}

		return EncryptionKeyEmojifier.emojifyForCall(Utilities.computeSHA256(os.toByteArray(), 0, os.size().toLong()))
	}

	fun hasEarpiece(): Boolean {
		if (USE_CONNECTION_SERVICE) {
			if (systemCallConnection != null && systemCallConnection!!.callAudioState != null) {
				val routeMask = systemCallConnection!!.callAudioState.supportedRouteMask
				return routeMask and (CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_WIRED_HEADSET) != 0
			}
		}

		if ((getSystemService(TELEPHONY_SERVICE) as TelephonyManager).phoneType != TelephonyManager.PHONE_TYPE_NONE) {
			return true
		}

		if (mHasEarpiece != null) {
			return mHasEarpiece!!
		}

		// not calculated yet, do it now
		return try {
			val am = getSystemService(AUDIO_SERVICE) as AudioManager
			val method = AudioManager::class.java.getMethod("getDevicesForStream", Integer.TYPE)
			val field = AudioManager::class.java.getField("DEVICE_OUT_EARPIECE")
			val earpieceFlag = field.getInt(null)
			val bitmaskResult = method.invoke(am, AudioManager.STREAM_VOICE_CALL) as Int

			// check if masked by the earpiece flag
			if (bitmaskResult and earpieceFlag == earpieceFlag) {
				java.lang.Boolean.TRUE
			}
			else {
				java.lang.Boolean.FALSE
			}
		}
		catch (error: Throwable) {
			FileLog.e("Error while checking earpiece! ", error)
			java.lang.Boolean.TRUE
		}.also {
			mHasEarpiece = it
		}
	}

	private fun getStatsNetworkType(): Int {
		var netType = StatsController.TYPE_WIFI

		if (lastNetInfo?.type == ConnectivityManager.TYPE_MOBILE) {
			netType = if (lastNetInfo?.isRoaming == true) StatsController.TYPE_ROAMING else StatsController.TYPE_MOBILE
		}

		return netType
	}

	fun setSwitchingCamera(switching: Boolean, isFrontFace: Boolean) {
		switchingCamera = switching

		if (!switching) {
			isFrontFaceCamera = isFrontFace

			stateListeners.forEach {
				it.onCameraSwitch(isFrontFaceCamera)
			}
		}
	}

	fun onCameraFirstFrameAvailable() {
		stateListeners.forEach {
			it.onCameraFirstFrameAvailable()
		}
	}

	fun registerStateListener(l: StateListener) {
		if (stateListeners.contains(l)) {
			return
		}

		stateListeners.add(l)

		if (currentState != 0) {
			l.onStateChanged(currentState)
		}

		if (signalBarCount != 0) {
			l.onSignalBarsCountChanged(signalBarCount)
		}
	}

	fun unregisterStateListener(l: StateListener) {
		stateListeners.remove(l)
	}

	fun editCallMember(`object`: TLObject?, mute: Boolean?, muteVideo: Boolean?, volume: Int?, raiseHand: Boolean?, onComplete: Runnable?) {
		if (`object` == null || groupCall == null) {
			return
		}

		val req = TL_phone_editGroupCallParticipant()
		req.call = groupCall!!.inputGroupCall

		if (`object` is User) {
			if (UserObject.isUserSelf(`object`) && groupCallPeer != null) {
				req.participant = groupCallPeer
			}
			else {
				req.participant = MessagesController.getInputPeer(`object`)

				FileLog.d("edit group call part id = " + req.participant.user_id + " access_hash = " + req.participant.user_id)
			}
		}
		else if (`object` is Chat) {
			req.participant = MessagesController.getInputPeer(`object`)

			FileLog.d("edit group call part id = " + (if (req.participant.chat_id != 0L) req.participant.chat_id else req.participant.channel_id) + " access_hash = " + req.participant.access_hash)
		}

		if (mute != null) {
			req.muted = mute
			req.flags = req.flags or 1
		}

		if (volume != null) {
			req.volume = volume
			req.flags = req.flags or 2
		}

		if (raiseHand != null) {
			req.raise_hand = raiseHand
			req.flags = req.flags or 4
		}

		if (muteVideo != null) {
			req.video_stopped = muteVideo
			req.flags = req.flags or 8
		}

		FileLog.d("edit group call flags = " + req.flags)

		val account = currentAccount

		AccountInstance.getInstance(account).connectionsManager.sendRequest(req) { response, error ->
			if (response != null) {
				AccountInstance.getInstance(account).messagesController.processUpdates(response as Updates?, false)
			}
			else if (error != null) {
				if ("GROUPCALL_VIDEO_TOO_MUCH" == error.text) {
					groupCall?.reloadGroupCall()
				}
			}

			if (onComplete != null) {
				AndroidUtilities.runOnUIThread(onComplete)
			}
		}
	}

	fun isMicMute(): Boolean {
		return micMute
	}

	fun toggleSpeakerphoneOrShowRouteSheet(context: Context?, fromOverlayWindow: Boolean) {
		if (isBluetoothHeadsetConnected() && hasEarpiece()) {
			if (context != null) {
				val builder = BottomSheet.Builder(context).setTitle(context.getString(R.string.VoipOutputDevices), true).setItems(arrayOf<CharSequence>(context.getString(R.string.VoipAudioRoutingSpeaker), if (isHeadsetPlugged) context.getString(R.string.VoipAudioRoutingHeadset) else context.getString(R.string.VoipAudioRoutingEarpiece), currentBluetoothDeviceName ?: context.getString(R.string.VoipAudioRoutingBluetooth)), intArrayOf(R.drawable.calls_menu_speaker, if (isHeadsetPlugged) R.drawable.calls_menu_headset else R.drawable.calls_menu_phone, R.drawable.calls_menu_bluetooth)) { _, which ->
					if (sharedInstance == null) {
						return@setItems
					}

					setAudioOutput(which)
				}

				val bottomSheet = builder.create()

				if (fromOverlayWindow) {
					if (Build.VERSION.SDK_INT >= 26) {
						bottomSheet.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
					}
					else {
						@Suppress("DEPRECATION") bottomSheet.window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
					}
				}

				builder.show()
			}

			return
		}

		if (USE_CONNECTION_SERVICE && systemCallConnection?.callAudioState != null) {
			if (hasEarpiece()) {
				systemCallConnection?.setAudioRoute(if (systemCallConnection?.callAudioState?.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
			}
			else {
				systemCallConnection?.setAudioRoute(if (systemCallConnection?.callAudioState?.route == CallAudioState.ROUTE_BLUETOOTH) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_BLUETOOTH)
			}
		}
		else if (audioConfigured && !USE_CONNECTION_SERVICE) {
			val am = getSystemService(AUDIO_SERVICE) as AudioManager

			if (hasEarpiece()) {
				am.isSpeakerphoneOn = !am.isSpeakerphoneOn
			}
			else {
				am.isBluetoothScoOn = !am.isBluetoothScoOn
			}

			updateOutputGainControlState()
		}
		else {
			speakerphoneStateToSet = !speakerphoneStateToSet
		}

		stateListeners.forEach {
			it.onAudioSettingsChanged()
		}
	}

	fun setAudioOutput(which: Int) {
		FileLog.d("setAudioOutput $which")

		val am = getSystemService(AUDIO_SERVICE) as AudioManager

		if (USE_CONNECTION_SERVICE && systemCallConnection != null) {
			when (which) {
				2 -> systemCallConnection?.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
				1 -> systemCallConnection?.setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE)
				0 -> systemCallConnection?.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
			}
		}
		else if (audioConfigured && !USE_CONNECTION_SERVICE) {
			when (which) {
				2 -> {
					if (!bluetoothScoActive) {
						needSwitchToBluetoothAfterScoActivates = true

						try {
							am.startBluetoothSco()
						}
						catch (e: Throwable) {
							FileLog.e(e)
						}
					}
					else {
						am.isBluetoothScoOn = true
						am.isSpeakerphoneOn = false
					}

					audioRouteToSet = AUDIO_ROUTE_BLUETOOTH
				}

				1 -> {
					needSwitchToBluetoothAfterScoActivates = false

					if (bluetoothScoActive || bluetoothScoConnecting) {
						am.stopBluetoothSco()

						bluetoothScoActive = false
						bluetoothScoConnecting = false
					}

					am.isSpeakerphoneOn = false
					am.isBluetoothScoOn = false

					audioRouteToSet = AUDIO_ROUTE_EARPIECE
				}

				0 -> {
					needSwitchToBluetoothAfterScoActivates = false

					if (bluetoothScoActive || bluetoothScoConnecting) {
						am.stopBluetoothSco()

						bluetoothScoActive = false
						bluetoothScoConnecting = false
					}

					am.isBluetoothScoOn = false
					am.isSpeakerphoneOn = true

					audioRouteToSet = AUDIO_ROUTE_SPEAKER
				}
			}

			updateOutputGainControlState()
		}
		else {
			when (which) {
				2 -> {
					audioRouteToSet = AUDIO_ROUTE_BLUETOOTH
					speakerphoneStateToSet = false
				}

				1 -> {
					audioRouteToSet = AUDIO_ROUTE_EARPIECE
					speakerphoneStateToSet = false
				}

				0 -> {
					audioRouteToSet = AUDIO_ROUTE_SPEAKER
					speakerphoneStateToSet = true
				}
			}
		}

		stateListeners.forEach {
			it.onAudioSettingsChanged()
		}
	}

	fun isSpeakerphoneOn(): Boolean {
		if (USE_CONNECTION_SERVICE && systemCallConnection != null && systemCallConnection!!.callAudioState != null) {
			val route = systemCallConnection?.callAudioState?.route
			return if (hasEarpiece()) route == CallAudioState.ROUTE_SPEAKER else route == CallAudioState.ROUTE_BLUETOOTH
		}
		else if (audioConfigured && !USE_CONNECTION_SERVICE) {
			val am = getSystemService(AUDIO_SERVICE) as AudioManager
			return if (hasEarpiece()) am.isSpeakerphoneOn else am.isBluetoothScoOn
		}

		return speakerphoneStateToSet
	}

	fun getCurrentAudioRoute(): Int {
		if (USE_CONNECTION_SERVICE) {
			return when (systemCallConnection?.callAudioState?.route) {
				CallAudioState.ROUTE_BLUETOOTH -> AUDIO_ROUTE_BLUETOOTH
				CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_WIRED_HEADSET -> AUDIO_ROUTE_EARPIECE
				CallAudioState.ROUTE_SPEAKER -> AUDIO_ROUTE_SPEAKER
				else -> audioRouteToSet
			}
		}
		if (audioConfigured) {
			val am = getSystemService(AUDIO_SERVICE) as AudioManager

			return when {
				am.isBluetoothScoOn -> AUDIO_ROUTE_BLUETOOTH
				am.isSpeakerphoneOn -> AUDIO_ROUTE_SPEAKER
				else -> AUDIO_ROUTE_EARPIECE
			}
		}

		return audioRouteToSet
	}

	fun getDebugString(): String {
		return tgVoip[CAPTURE_DEVICE_CAMERA]?.debugInfo ?: ""
	}

	fun getCallDuration(): Long {
		return if (callStartTime == 0L) {
			0
		}
		else {
			SystemClock.elapsedRealtime() - callStartTime
		}
	}

	fun stopRinging() {
		ringtonePlayer?.stop()
		ringtonePlayer?.release()
		ringtonePlayer = null

		vibrator?.cancel()
		vibrator = null
	}

	private fun showNotification(name: String, photo: Bitmap?) {
		val intent = Intent(this, LaunchActivity::class.java).setAction(if (groupCall != null) "voip_chat" else "voip")

		if (groupCall != null) {
			intent.putExtra("currentAccount", currentAccount)
		}

		val builder = Notification.Builder(this, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL).setContentText(name).setContentIntent(PendingIntent.getActivity(this, 50, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

		if (groupCall != null) {
			builder.setContentTitle(if (ChatObject.isChannelOrGiga(chat)) getString(R.string.VoipLiveStream) else getString(R.string.VoipVoiceChat))
			builder.setSmallIcon(if (isMicMute()) R.drawable.voicechat_muted else R.drawable.voicechat_active)
		}
		else {
			builder.setContentTitle(getString(R.string.VoipOutgoingCall))
			builder.setSmallIcon(R.drawable.notification)
		}

		val endIntent = Intent(this, VoIPActionsReceiver::class.java)
		endIntent.action = "$packageName.END_CALL"

		if (groupCall != null) {
			builder.addAction(R.drawable.ic_call_end_white_24dp, if (ChatObject.isChannelOrGiga(chat)) getString(R.string.VoipChannelLeaveAlertTitle) else getString(R.string.VoipGroupLeaveAlertTitle), PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
		}
		else {
			builder.addAction(R.drawable.ic_call_end_white_24dp, getString(R.string.VoipEndCall), PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
		}

		builder.setPriority(Notification.PRIORITY_MAX)
		builder.setShowWhen(false)

		if (Build.VERSION.SDK_INT >= 26) {
			NotificationsController.checkOtherNotificationsChannel()

			builder.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
		}

		if (photo != null) {
			builder.setLargeIcon(photo)
		}

		try {
			startForeground(ID_ONGOING_CALL_NOTIFICATION, builder.build())
		}
		catch (e: Exception) {
			if (photo != null && e is IllegalArgumentException) {
				showNotification(name, null)
			}
		}
	}

	private fun startRingtoneAndVibration(chatID: Long) {
		val prefs = MessagesController.getNotificationsSettings(currentAccount)
		val am = getSystemService(AUDIO_SERVICE) as AudioManager
		val needRing = am.ringerMode != AudioManager.RINGER_MODE_SILENT

		if (needRing) {
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

				if (!USE_CONNECTION_SERVICE) {
					am.requestAudioFocus(this, AudioManager.STREAM_RING, AudioManager.AUDIOFOCUS_GAIN)
				}
			}
			try {
				val notificationUri = if (prefs.getBoolean("custom_$chatID", false)) {
					prefs.getString("ringtone_path_$chatID", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString())
				}
				else {
					prefs.getString("CallsRingtonePath", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString())
				}

				ringtonePlayer?.setDataSource(this, Uri.parse(notificationUri))
				ringtonePlayer?.prepareAsync()
			}
			catch (e: Exception) {
				FileLog.e(e)

				ringtonePlayer?.release()
				ringtonePlayer = null
			}

			val vibrate = if (prefs.getBoolean("custom_$chatID", false)) {
				prefs.getInt("calls_vibrate_$chatID", 0)
			}
			else {
				prefs.getInt("vibrate_calls", 0)
			}

			if (vibrate != 2 && vibrate != 4 && (am.ringerMode == AudioManager.RINGER_MODE_VIBRATE || am.ringerMode == AudioManager.RINGER_MODE_NORMAL) || vibrate == 4 && am.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
				vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

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

	override fun onTaskRemoved(rootIntent: Intent?) {
		super.onTaskRemoved(rootIntent)
		hangUp()
	}

	override fun onDestroy() {
		FileLog.d("=============== VoIPService STOPPING ===============")

		stopForeground(STOP_FOREGROUND_REMOVE)

		stopRinging()

		if (currentAccount >= 0) {
			if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
				MessagesController.getInstance(currentAccount).ignoreSetOnline = false
			}

			NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appDidLogout)
		}

		val sm = getSystemService(SENSOR_SERVICE) as SensorManager
		val proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)

		if (proximity != null) {
			sm.unregisterListener(this)
		}

		if (proximityWakelock?.isHeld == true) {
			proximityWakelock?.release()
		}

		if (updateNotificationRunnable != null) {
			Utilities.globalQueue.cancelRunnable(updateNotificationRunnable)
			updateNotificationRunnable = null
		}

		if (switchingStreamTimeoutRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(switchingStreamTimeoutRunnable)
			switchingStreamTimeoutRunnable = null
		}

		unregisterReceiver(receiver)

		if (timeoutRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(timeoutRunnable)
			timeoutRunnable = null
		}

		callIShouldHavePutIntoIntent = null

		super.onDestroy()

		sharedInstance = null

		Arrays.fill(mySource, 0)

		cancelGroupCheckShortPoll()

		AndroidUtilities.runOnUIThread {
			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.didEndCall)
		}

		if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
			StatsController.getInstance(currentAccount).incrementTotalCallsTime(getStatsNetworkType(), (getCallDuration() / 1000).toInt() % 5)

			onTgVoipPreStop()

			if (tgVoip[CAPTURE_DEVICE_CAMERA]!!.isGroup) {
				val instance = tgVoip[CAPTURE_DEVICE_CAMERA]

				Utilities.globalQueue.postRunnable { instance!!.stopGroup() }

				for ((_, value) in currentStreamRequestTimestamp) {
					AccountInstance.getInstance(currentAccount).connectionsManager.cancelRequest(value, true)
				}

				currentStreamRequestTimestamp.clear()
			}
			else {
				val state = tgVoip[CAPTURE_DEVICE_CAMERA]!!.stop()
				updateTrafficStats(tgVoip[CAPTURE_DEVICE_CAMERA], state.trafficStats)
				onTgVoipStop(state)
			}

			prevTrafficStats = null
			callStartTime = 0
			tgVoip[CAPTURE_DEVICE_CAMERA] = null

			destroyInstance()
		}

		if (tgVoip[CAPTURE_DEVICE_SCREEN] != null) {
			val instance = tgVoip[CAPTURE_DEVICE_SCREEN]

			Utilities.globalQueue.postRunnable {
				instance?.stopGroup()
			}

			tgVoip[CAPTURE_DEVICE_SCREEN] = null
		}

		for (a in captureDevice.indices) {
			if (captureDevice[a] != 0L) {
				if (destroyCaptureDevice[a]) {
					NativeInstance.destroyVideoCapturer(captureDevice[a])
				}

				captureDevice[a] = 0
			}
		}

		cpuWakelock?.release()

		if (!playingSound) {
			val am = getSystemService(AUDIO_SERVICE) as AudioManager

			if (!USE_CONNECTION_SERVICE) {
				if (isBtHeadsetConnected || bluetoothScoActive || bluetoothScoConnecting) {
					am.stopBluetoothSco()
					am.isBluetoothScoOn = false
					am.isSpeakerphoneOn = false

					bluetoothScoActive = false
					bluetoothScoConnecting = false
				}

				if (onDestroyRunnable == null) {
					Utilities.globalQueue.postRunnable(Runnable label@{
						synchronized(sync) {
							if (setModeRunnable == null) {
								return@label
							}

							setModeRunnable = null
						}

						try {
							am.mode = AudioManager.MODE_NORMAL
						}
						catch (x: SecurityException) {
							FileLog.e("Error setting audio more to normal", x)
						}
					}.also {
						setModeRunnable = it
					})
				}

				am.abandonAudioFocus(this)
			}

			am.unregisterMediaButtonEventReceiver(ComponentName(this, VoIPMediaButtonReceiver::class.java))

			if (hasAudioFocus) {
				am.abandonAudioFocus(this)
			}

			Utilities.globalQueue.postRunnable {
				soundPool?.release()
			}
		}

		if (USE_CONNECTION_SERVICE) {
			if (!didDeleteConnectionServiceContact) {
				// ContactsController.getInstance(currentAccount).deleteConnectionServiceContact()
			}

			if (!playingSound) {
				systemCallConnection?.destroy()
			}
		}

		VoIPHelper.lastCallTime = SystemClock.elapsedRealtime()
		setSinks(null, null)

		onDestroyRunnable?.run()

		if (currentAccount >= 0) {
			ConnectionsManager.getInstance(currentAccount).setAppPaused(value = true, byScreenState = false)

			if (ChatObject.isChannel(chat)) {
				MessagesController.getInstance(currentAccount).startShortPoll(chat, classGuid, true)
			}
		}
	}

	private fun getCallID(): Long {
		return privateCall?.id ?: 0
	}

	fun hangUp(onDone: Runnable?) {
		hangUp(0, onDone)
	}

	fun acceptIncomingCall() {
		MessagesController.getInstance(currentAccount).ignoreSetOnline = false
		stopRinging()
		showNotification()
		configureDeviceForCall()
		startConnectingSound()
		dispatchStateChanged(STATE_EXCHANGING_KEYS)

		AndroidUtilities.runOnUIThread {
			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.didStartedCall)
		}

		val messagesStorage = MessagesStorage.getInstance(currentAccount)

		val req = TL_messages_getDhConfig()
		req.random_length = 256
		req.version = messagesStorage.lastSecretVersion

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
			if (error == null) {
				val res = response as messages_DhConfig

				if (response is TL_messages_dhConfig) {
					if (!Utilities.isGoodPrime(res.p, res.g)) {
						FileLog.e("stopping VoIP service, bad prime")
						callFailed()
						return@sendRequest
					}

					messagesStorage.secretPBytes = res.p
					messagesStorage.secretG = res.g
					messagesStorage.lastSecretVersion = res.version
					MessagesStorage.getInstance(currentAccount).saveSecretParams(messagesStorage.lastSecretVersion, messagesStorage.secretG, messagesStorage.secretPBytes)
				}

				val salt = ByteArray(256)

				for (a in 0..255) {
					salt[a] = ((Utilities.random.nextDouble() * 256).toInt().toByte() xor res.random[a].toInt().toByte())
				}

				if (privateCall == null) {
					FileLog.e("call is null")
					callFailed()
					return@sendRequest
				}

				a_or_b = salt

				var g_b = BigInteger.valueOf(messagesStorage.secretG.toLong())
				val p = BigInteger(1, messagesStorage.secretPBytes)

				g_b = g_b.modPow(BigInteger(1, salt), p)

				g_a_hash = privateCall!!.g_a_hash

				var g_b_bytes = g_b.toByteArray()

				if (g_b_bytes.size > 256) {
					val correctedAuth = ByteArray(256)
					System.arraycopy(g_b_bytes, 1, correctedAuth, 0, 256)
					g_b_bytes = correctedAuth
				}

				val req1 = TL_phone_acceptCall()
				req1.g_b = g_b_bytes
				req1.peer = TL_inputPhoneCall()
				req1.peer.id = privateCall!!.id
				req1.peer.access_hash = privateCall!!.access_hash
				req1.protocol = TL_phoneCallProtocol()
				req1.protocol.udp_reflector = true
				req1.protocol.udp_p2p = req1.protocol.udp_reflector
				req1.protocol.min_layer = CALL_MIN_LAYER
				req1.protocol.max_layer = getConnectionMaxLayer()
				req1.protocol.library_versions.addAll(AVAILABLE_VERSIONS)

				ConnectionsManager.getInstance(currentAccount).sendRequest(req1, { response1, error1 ->
					AndroidUtilities.runOnUIThread {
						if (error1 == null) {
							FileLog.w("accept call ok! $response1")

							privateCall = (response1 as TL_phone_phoneCall).phone_call

							if (privateCall is TL_phoneCallDiscarded) {
								onCallUpdated(privateCall)
							}
						}
						else {
							FileLog.e("Error on phone.acceptCall: $error1")

							callFailed()
						}
					}
				}, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
			else {
				callFailed()
			}
		}
	}

	@JvmOverloads
	fun declineIncomingCall(reason: Int = DISCARD_REASON_HANGUP, onDone: Runnable? = null) {
		stopRinging()

		callDiscardReason = reason

		if (currentState == STATE_REQUESTING) {
			if (delayedStartOutgoingCall != null) {
				AndroidUtilities.cancelRunOnUIThread(delayedStartOutgoingCall)
				callEnded()
			}
			else {
				dispatchStateChanged(STATE_HANGING_UP)

				endCallAfterRequest = true

				AndroidUtilities.runOnUIThread({
					if (currentState == STATE_HANGING_UP) {
						callEnded()
					}
				}, 5000)
			}

			return
		}

		if (currentState == STATE_HANGING_UP || currentState == STATE_ENDED) {
			return
		}

		dispatchStateChanged(STATE_HANGING_UP)

		if (privateCall == null) {
			onDestroyRunnable = onDone

			callEnded()

			if (callReqId != 0) {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(callReqId, false)
				callReqId = 0
			}
			return
		}

		val req = TL_phone_discardCall()
		req.peer = TL_inputPhoneCall()
		req.peer.access_hash = privateCall!!.access_hash
		req.peer.id = privateCall!!.id
		req.duration = (getCallDuration() / 1000).toInt()
		req.connection_id = if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) tgVoip[CAPTURE_DEVICE_CAMERA]!!.preferredRelayId else 0

		when (reason) {
			DISCARD_REASON_DISCONNECT -> req.reason = TL_phoneCallDiscardReasonDisconnect()
			DISCARD_REASON_MISSED -> req.reason = TL_phoneCallDiscardReasonMissed()
			DISCARD_REASON_LINE_BUSY -> req.reason = TL_phoneCallDiscardReasonBusy()
			DISCARD_REASON_HANGUP -> req.reason = TL_phoneCallDiscardReasonHangup()
			else -> req.reason = TL_phoneCallDiscardReasonHangup()
		}

		ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
			if (error != null) {
				FileLog.e("error on phone.discardCall: $error")
			}
			else {
				if (response is TL_updates) {
					MessagesController.getInstance(currentAccount).processUpdates(response, false)
				}

				FileLog.d("phone.discardCall $response")
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)

		onDestroyRunnable = onDone

		callEnded()
	}

	private fun getUIActivityClass(): Class<out Activity?> {
		return LaunchActivity::class.java
	}

	@TargetApi(Build.VERSION_CODES.O)
	fun getConnectionAndStartCall(): CallConnection {
		return systemCallConnection ?: CallConnection().apply {
			FileLog.d("creating call connection")

			setInitializing()

			if (isOutgoing) {
				delayedStartOutgoingCall = Runnable {
					delayedStartOutgoingCall = null
					startOutgoingCall()
				}

				AndroidUtilities.runOnUIThread(delayedStartOutgoingCall, 2000)
			}

			setAddress(Uri.fromParts("tel", "+99084" + user!!.id, null), TelecomManager.PRESENTATION_ALLOWED)
			setCallerDisplayName(ContactsController.formatName(user!!.first_name, user!!.last_name), TelecomManager.PRESENTATION_ALLOWED)
		}.also {
			systemCallConnection = it
		}
	}

	private fun startRinging() {
		if (currentState == STATE_WAITING_INCOMING) {
			return
		}

		if (USE_CONNECTION_SERVICE) {
			systemCallConnection?.setRinging()
		}

		FileLog.d("starting ringing for call " + privateCall!!.id)

		dispatchStateChanged(STATE_WAITING_INCOMING)

		if (notificationsDisabled) {
			if (!isMuted) {
				startRingtoneAndVibration(user!!.id)

				FileLog.d("Starting incall activity for incoming call")

				try {
					PendingIntent.getActivity(this@VoIPService, 12345, Intent(this@VoIPService, LaunchActivity::class.java).setAction("voip"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE).send()
				}
				catch (x: Exception) {
					FileLog.e("Error starting incall activity", x)
				}
			}
		}
		else {
			showIncomingNotification(ContactsController.formatName(user!!.first_name, user!!.last_name), user, privateCall!!.video)

			FileLog.d("Showing incoming call notification")
		}
	}

	fun startRingtoneAndVibration() {
		if (!startedRinging) {
			startRingtoneAndVibration(user!!.id)
			startedRinging = true
		}
	}

	private fun updateServerConfig() {
		val preferences = MessagesController.getMainSettings(currentAccount)

		setGlobalServerConfig(preferences.getString("voip_server_config", "{}"))

		ConnectionsManager.getInstance(currentAccount).sendRequest(TL_phone_getCallConfig()) { response, error ->
			if (error == null) {
				val data = (response as TL_dataJSON).data
				setGlobalServerConfig(data)
				preferences.edit().putString("voip_server_config", data).commit()
			}
		}
	}

	private fun showNotification() {
		if (user != null) {
			showNotification(ContactsController.formatName(user!!.first_name, user!!.last_name), getRoundAvatarBitmap(user))
		}
		else {
			showNotification(chat!!.title, getRoundAvatarBitmap(chat))
		}
	}

	private fun onTgVoipPreStop() {        /*if(BuildConfig.DEBUG){
			String debugLog=controller.getDebugLog();
			TLRPC.TL_phone_saveCallDebug req=new TLRPC.TL_phone_saveCallDebug();
			req.debug=new TLRPC.TL_dataJSON();
			req.debug.data=debugLog;
			req.peer=new TLRPC.TL_inputPhoneCall();
			req.peer.access_hash=call.access_hash;
			req.peer.id=call.id;
			ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate(){
				@Override
				public void run(TLObject response, TLRPC.TL_error error){
					FileLog.d("Sent debug logs, response=" + response);
				}
			});
		}*/
	}

	private fun onTgVoipStop(finalState: FinalState) {
		if (user == null) {
			return
		}

		if (TextUtils.isEmpty(finalState.debugLog)) {
			try {
				finalState.debugLog = getStringFromFile(getLogFilePath(privateCall!!.id, true))
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		if (needRateCall || forceRating || finalState.isRatingSuggested) {
			startRatingActivity()
			needRateCall = false
		}

		if (needSendDebugLog && finalState.debugLog != null) {
			val req = TL_phone_saveCallDebug()
			req.debug = TL_dataJSON()
			req.debug.data = finalState.debugLog
			req.peer = TL_inputPhoneCall()
			req.peer.access_hash = privateCall!!.access_hash
			req.peer.id = privateCall!!.id

			ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
				FileLog.d("Sent debug logs, response = $response")
			}

			needSendDebugLog = false
		}
	}

	private fun initializeAccountRelatedThings() {
		updateServerConfig()
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appDidLogout)
		ConnectionsManager.getInstance(currentAccount).setAppPaused(value = false, byScreenState = false)
	}

	@SuppressLint("InvalidWakeLockTag")
	override fun onCreate() {
		super.onCreate()

		FileLog.d("=============== VoIPService STARTING ===============")

		try {
			val am = getSystemService(AUDIO_SERVICE) as AudioManager

			if (am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) != null) {
				val outFramesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER).toInt()
				setBufferSize(outFramesPerBuffer)
			}
			else {
				setBufferSize(AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) / 2)
			}

			cpuWakelock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "elloapp-voip")
			cpuWakelock?.acquire()

			btAdapter = if (am.isBluetoothScoAvailableOffCall) BluetoothAdapter.getDefaultAdapter() else null

			val filter = IntentFilter()
			filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)

			if (!USE_CONNECTION_SERVICE) {
				filter.addAction(ACTION_HEADSET_PLUG)

				if (btAdapter != null) {
					filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
					filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
				}

				filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
				filter.addAction(Intent.ACTION_SCREEN_ON)
				filter.addAction(Intent.ACTION_SCREEN_OFF)
			}

			registerReceiver(receiver, filter)

			fetchBluetoothDeviceName()

			am.registerMediaButtonEventReceiver(ComponentName(this, VoIPMediaButtonReceiver::class.java))

			if (!USE_CONNECTION_SERVICE && btAdapter != null && btAdapter?.isEnabled == true) {
				try {
					val mr = getSystemService(MEDIA_ROUTER_SERVICE) as MediaRouter

					if (Build.VERSION.SDK_INT < 24) {
						if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
							val headsetState = btAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET)

							updateBluetoothHeadsetState(headsetState == BluetoothProfile.STATE_CONNECTED)

							stateListeners.forEach {
								it.onAudioSettingsChanged()
							}
						}
					}
					else {
						val ri = mr.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)

						if (ri.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH) {
							val headsetState = btAdapter!!.getProfileConnectionState(BluetoothProfile.HEADSET)
							updateBluetoothHeadsetState(headsetState == BluetoothProfile.STATE_CONNECTED)

							stateListeners.forEach {
								it.onAudioSettingsChanged()
							}
						}
						else {
							updateBluetoothHeadsetState(false)
						}
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}
		}
		catch (x: Exception) {
			FileLog.e("error initializing voip controller", x)
			callFailed()
		}

		if (callIShouldHavePutIntoIntent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationsController.checkOtherNotificationsChannel()

			val bldr = Notification.Builder(this, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL).setContentTitle(getString(R.string.VoipOutgoingCall)).setShowWhen(false)

			if (groupCall != null) {
				bldr.setSmallIcon(if (isMicMute()) R.drawable.voicechat_muted else R.drawable.voicechat_active)
			}
			else {
				bldr.setSmallIcon(R.drawable.notification)
			}

			startForeground(ID_ONGOING_CALL_NOTIFICATION, bldr.build())
		}
	}

	private fun loadResources() {
		WebRtcAudioTrack.setAudioTrackUsageAttribute(AudioAttributes.USAGE_VOICE_COMMUNICATION)

		Utilities.globalQueue.postRunnable {
			soundPool = SoundPool(1, AudioManager.STREAM_VOICE_CALL, 0)
			spConnectingId = soundPool!!.load(this, R.raw.voip_connecting, 1)
			spRingbackID = soundPool!!.load(this, R.raw.voip_ringback, 1)
			spFailedID = soundPool!!.load(this, R.raw.voip_failed, 1)
			spEndId = soundPool!!.load(this, R.raw.voip_end, 1)
			spBusyId = soundPool!!.load(this, R.raw.voip_busy, 1)
			spVoiceChatEndId = soundPool!!.load(this, R.raw.voicechat_leave, 1)
			spVoiceChatStartId = soundPool!!.load(this, R.raw.voicechat_join, 1)
			spVoiceChatConnecting = soundPool!!.load(this, R.raw.voicechat_connecting, 1)
			spAllowTalkId = soundPool!!.load(this, R.raw.voip_onallowtalk, 1)
			spStartRecordId = soundPool!!.load(this, R.raw.voip_recordstart, 1)
		}
	}

	private fun dispatchStateChanged(state: Int) {
		FileLog.d("== Call " + getCallID() + " state changed to " + state + " ==")

		currentState = state

		if (USE_CONNECTION_SERVICE && state == STATE_ESTABLISHED && systemCallConnection != null) {
			systemCallConnection!!.setActive()
		}

		stateListeners.forEach {
			it.onStateChanged(state)
		}
	}

	private fun updateTrafficStats(instance: NativeInstance?, trafficStats: TrafficStats?) {
		@Suppress("NAME_SHADOWING") var trafficStats = trafficStats

		if (trafficStats == null) {
			trafficStats = instance?.trafficStats ?: return
		}

		val wifiSentDiff = trafficStats.bytesSentWifi - if (prevTrafficStats != null) prevTrafficStats!!.bytesSentWifi else 0
		val wifiRecvdDiff = trafficStats.bytesReceivedWifi - if (prevTrafficStats != null) prevTrafficStats!!.bytesReceivedWifi else 0
		val mobileSentDiff = trafficStats.bytesSentMobile - if (prevTrafficStats != null) prevTrafficStats!!.bytesSentMobile else 0
		val mobileRecvdDiff = trafficStats.bytesReceivedMobile - if (prevTrafficStats != null) prevTrafficStats!!.bytesReceivedMobile else 0

		prevTrafficStats = trafficStats

		if (wifiSentDiff > 0) {
			StatsController.getInstance(currentAccount).incrementSentBytesCount(StatsController.TYPE_WIFI, StatsController.TYPE_CALLS, wifiSentDiff)
		}

		if (wifiRecvdDiff > 0) {
			StatsController.getInstance(currentAccount).incrementReceivedBytesCount(StatsController.TYPE_WIFI, StatsController.TYPE_CALLS, wifiRecvdDiff)
		}

		if (mobileSentDiff > 0) {
			StatsController.getInstance(currentAccount).incrementSentBytesCount(if (lastNetInfo != null && lastNetInfo!!.isRoaming) StatsController.TYPE_ROAMING else StatsController.TYPE_MOBILE, StatsController.TYPE_CALLS, mobileSentDiff)
		}

		if (mobileRecvdDiff > 0) {
			StatsController.getInstance(currentAccount).incrementReceivedBytesCount(if (lastNetInfo != null && lastNetInfo!!.isRoaming) StatsController.TYPE_ROAMING else StatsController.TYPE_MOBILE, StatsController.TYPE_CALLS, mobileRecvdDiff)
		}
	}

	@SuppressLint("InvalidWakeLockTag")
	private fun configureDeviceForCall() {
		FileLog.d("configureDeviceForCall, route to set = $audioRouteToSet")

		WebRtcAudioTrack.setAudioTrackUsageAttribute(if (hasRtmpStream()) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION)
		WebRtcAudioTrack.setAudioStreamType(if (hasRtmpStream()) AudioManager.USE_DEFAULT_STREAM_TYPE else AudioManager.STREAM_VOICE_CALL)

		needPlayEndSound = true

		val am = getSystemService(AUDIO_SERVICE) as AudioManager

		if (!USE_CONNECTION_SERVICE) {
			Utilities.globalQueue.postRunnable {
				try {
					if (hasRtmpStream()) {
						am.mode = AudioManager.MODE_NORMAL
						am.isBluetoothScoOn = false

						AndroidUtilities.runOnUIThread {
							if (!MediaController.getInstance().isMessagePaused) {
								MediaController.getInstance().pauseMessage(MediaController.getInstance().playingMessageObject)
							}
						}

						return@postRunnable
					}

					am.mode = AudioManager.MODE_IN_COMMUNICATION
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				AndroidUtilities.runOnUIThread {
					am.requestAudioFocus(this@VoIPService, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)

					if (isBluetoothHeadsetConnected() && hasEarpiece()) {
						when (audioRouteToSet) {
							AUDIO_ROUTE_BLUETOOTH -> if (!bluetoothScoActive) {
								needSwitchToBluetoothAfterScoActivates = true

								try {
									am.startBluetoothSco()
								}
								catch (e: Throwable) {
									FileLog.e(e)
								}
							}
							else {
								am.isBluetoothScoOn = true
								am.isSpeakerphoneOn = false
							}

							AUDIO_ROUTE_EARPIECE -> {
								am.isBluetoothScoOn = false
								am.isSpeakerphoneOn = false
							}

							AUDIO_ROUTE_SPEAKER -> {
								am.isBluetoothScoOn = false
								am.isSpeakerphoneOn = true
							}
						}
					}
					else if (isBluetoothHeadsetConnected()) {
						am.isBluetoothScoOn = speakerphoneStateToSet
					}
					else {
						am.isSpeakerphoneOn = speakerphoneStateToSet

						audioRouteToSet = if (speakerphoneStateToSet) {
							AUDIO_ROUTE_SPEAKER
						}
						else {
							AUDIO_ROUTE_EARPIECE
						}
					}

					updateOutputGainControlState()

					audioConfigured = true
				}
			}
		}

		val sm = getSystemService(SENSOR_SERVICE) as SensorManager
		val proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)

		try {
			if (proximity != null) {
				proximityWakelock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "elloapp-voip-prx")
				sm.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL)
			}
		}
		catch (x: Exception) {
			FileLog.e("Error initializing proximity sensor", x)
		}
	}

	private fun fetchBluetoothDeviceName() {
		if (fetchingBluetoothDeviceName) {
			return
		}

		try {
			currentBluetoothDeviceName = null
			fetchingBluetoothDeviceName = true
			BluetoothAdapter.getDefaultAdapter().getProfileProxy(this, serviceListener, BluetoothProfile.HEADSET)
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}
	}

	@SuppressLint("NewApi")
	override fun onSensorChanged(event: SensorEvent) {
		if (unmutedByHold || remoteVideoState == VIDEO_STATE_ACTIVE || videoState[CAPTURE_DEVICE_CAMERA] == VIDEO_STATE_ACTIVE) {
			return
		}

		if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
			val am = getSystemService(AUDIO_SERVICE) as AudioManager

			if (audioRouteToSet != AUDIO_ROUTE_EARPIECE || isHeadsetPlugged || am.isSpeakerphoneOn || isBluetoothHeadsetConnected() && am.isBluetoothScoOn) {
				return
			}

			val newIsNear = event.values[0] < min(event.sensor.maximumRange, 3f)

			checkIsNear(newIsNear)
		}
	}

	private fun checkIsNear() {
		if (remoteVideoState == VIDEO_STATE_ACTIVE || videoState[CAPTURE_DEVICE_CAMERA] == VIDEO_STATE_ACTIVE) {
			checkIsNear(false)
		}
	}

	private fun checkIsNear(newIsNear: Boolean) {
		if (newIsNear != isProximityNear) {
			FileLog.d("proximity $newIsNear")

			isProximityNear = newIsNear

			try {
				if (isProximityNear) {
					proximityWakelock?.acquire()
				}
				else {
					proximityWakelock?.release(1) // this is non-public API before L
				}
			}
			catch (x: Exception) {
				FileLog.e(x)
			}
		}
	}

	override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
		// unused
	}

	fun isBluetoothHeadsetConnected(): Boolean {
		return if (USE_CONNECTION_SERVICE && systemCallConnection?.callAudioState != null) {
			systemCallConnection!!.callAudioState.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0
		}
		else {
			isBtHeadsetConnected
		}
	}

	override fun onAudioFocusChange(focusChange: Int) {
		hasAudioFocus = focusChange == AudioManager.AUDIOFOCUS_GAIN
	}

	private fun updateBluetoothHeadsetState(connected: Boolean) {
		if (connected == isBtHeadsetConnected) {
			return
		}

		FileLog.d("updateBluetoothHeadsetState: $connected")

		isBtHeadsetConnected = connected

		val am = getSystemService(AUDIO_SERVICE) as AudioManager

		if (connected && !isRinging() && currentState != 0) {
			if (bluetoothScoActive) {
				FileLog.d("SCO already active, setting audio routing")

				if (!hasRtmpStream()) {
					am.isSpeakerphoneOn = false
					am.isBluetoothScoOn = true
				}
			}
			else {
				FileLog.d("startBluetoothSco")

				if (!hasRtmpStream()) {
					needSwitchToBluetoothAfterScoActivates = true

					AndroidUtilities.runOnUIThread({
						try {
							am.startBluetoothSco()
						}
						catch (e: Throwable) {
							// ignored
						}
					}, 500)
				}
			}
		}
		else {
			bluetoothScoActive = false
			bluetoothScoConnecting = false
		}

		stateListeners.forEach {
			it.onAudioSettingsChanged()
		}
	}

	fun getLastError(): String? {
		return lastError
	}

	fun getCallState(): Int {
		return currentState
	}

	fun getGroupCallPeer(): InputPeer? {
		return groupCallPeer
	}

	private fun updateNetworkType() {
		if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
			if (tgVoip[CAPTURE_DEVICE_CAMERA]?.isGroup == false) {
				tgVoip[CAPTURE_DEVICE_CAMERA]?.setNetworkType(getNetworkType())
			}
		}
		else {
			lastNetInfo = getActiveNetworkInfo()
		}
	}

	private fun getNetworkType(): Int {
		lastNetInfo = getActiveNetworkInfo()

		val info = lastNetInfo
		var type = NET_TYPE_UNKNOWN

		if (info != null) {
			when (info.type) {
				ConnectivityManager.TYPE_MOBILE -> type = when (info.subtype) {
					TelephonyManager.NETWORK_TYPE_GPRS -> NET_TYPE_GPRS
					TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_1xRTT -> NET_TYPE_EDGE
					TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0 -> NET_TYPE_3G
					TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B -> NET_TYPE_HSPA
					TelephonyManager.NETWORK_TYPE_LTE -> NET_TYPE_LTE
					else -> NET_TYPE_OTHER_MOBILE
				}

				ConnectivityManager.TYPE_WIFI -> type = NET_TYPE_WIFI
				ConnectivityManager.TYPE_ETHERNET -> type = NET_TYPE_ETHERNET
			}
		}

		return type
	}

	private fun getActiveNetworkInfo(): NetworkInfo? {
		return (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo
	}

	private fun getRoundAvatarBitmap(userOrChat: TLObject?): Bitmap {
		var bitmap: Bitmap? = null

		try {
			if (userOrChat is User) {
				if (userOrChat.photo?.photo_small != null) {
					val img = ImageLoader.instance.getImageFromMemory(userOrChat.photo?.photo_small, null, "50_50")

					if (img != null) {
						bitmap = img.bitmap.copy(Bitmap.Config.ARGB_8888, true)
					}
					else {
						try {
							val opts = BitmapFactory.Options()
							opts.inMutable = true
							bitmap = BitmapFactory.decodeFile(FileLoader.getInstance(currentAccount).getPathToAttach(userOrChat.photo?.photo_small, true).toString(), opts)
						}
						catch (e: Throwable) {
							FileLog.e(e)
						}
					}
				}
			}
			else {
				val chat = userOrChat as Chat?

				if (chat?.photo?.photo_small != null) {
					val img = ImageLoader.instance.getImageFromMemory(chat.photo.photo_small, null, "50_50")

					if (img != null) {
						bitmap = img.bitmap.copy(Bitmap.Config.ARGB_8888, true)
					}
					else {
						try {
							val opts = BitmapFactory.Options()
							opts.inMutable = true
							bitmap = BitmapFactory.decodeFile(FileLoader.getInstance(currentAccount).getPathToAttach(chat.photo.photo_small, true).toString(), opts)
						}
						catch (e: Throwable) {
							FileLog.e(e)
						}
					}
				}
			}
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}

		if (bitmap == null) {
			Theme.createDialogsResources(this)

			val placeholder: AvatarDrawable = if (userOrChat is User) {
				AvatarDrawable(userOrChat as User?)
			}
			else {
				AvatarDrawable(userOrChat as Chat?)
			}

			bitmap = Bitmap.createBitmap(AndroidUtilities.dp(42f), AndroidUtilities.dp(42f), Bitmap.Config.ARGB_8888)

			placeholder.setBounds(0, 0, bitmap.width, bitmap.height)
			placeholder.draw(Canvas(bitmap))
		}

		val canvas = Canvas(bitmap!!)

		val circlePath = Path()
		circlePath.addCircle((bitmap.width / 2).toFloat(), (bitmap.height / 2).toFloat(), (bitmap.width / 2).toFloat(), Path.Direction.CW)
		circlePath.toggleInverseFillType()

		val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

		canvas.drawPath(circlePath, paint)

		return bitmap
	}

	private fun showIncomingNotification(name: String, userOrChat: TLObject?, video: Boolean) {
		val intent = Intent(this, LaunchActivity::class.java)
		intent.action = "voip"

		val builder: Notification.Builder = Notification.Builder(this).setContentTitle(if (video) getString(R.string.VoipInVideoCallBranding) else getString(R.string.VoipInCallBranding)).setSmallIcon(R.drawable.ic_call).setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE))

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val nprefs = MessagesController.getGlobalNotificationsSettings()
			var chanIndex = nprefs.getInt("calls_notification_channel", 0)
			val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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

					nprefs.edit().putInt("calls_notification_channel", chanIndex).commit()
				}
				else {
					needCreate = false
				}
			}

			if (needCreate) {
				val attrs: AudioAttributes = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).setLegacyStreamType(AudioManager.STREAM_RING).setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build()
				val chan = NotificationChannel("incoming_calls4$chanIndex", getString(R.string.IncomingCalls), NotificationManager.IMPORTANCE_HIGH)

				try {
					chan.setSound(null, attrs)
				}
				catch (e: java.lang.Exception) {
					FileLog.e(e)
				}

				chan.description = getString(R.string.incoming_calls_settings)
				chan.enableVibration(false)
				chan.enableLights(false)
				chan.setBypassDnd(true)

				try {
					nm.createNotificationChannel(chan)
				}
				catch (e: Exception) {
					FileLog.e(e)
					this.stopSelf()
					return
				}
			}

			builder.setChannelId("incoming_calls4$chanIndex")
		}
		else {
			builder.setSound(null)
		}

		val endIntent = Intent(this, VoIPActionsReceiver::class.java)
		endIntent.action = "$packageName.DECLINE_CALL"
		endIntent.putExtra("call_id", getCallID())

		var endTitle: CharSequence = getString(R.string.VoipDeclineCall)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			endTitle = SpannableString(endTitle)
			endTitle.setSpan(ForegroundColorSpan(-0xbbcca), 0, endTitle.length, 0)
		}

		val endPendingIntent = PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)

		val answerIntent = Intent(this, VoIPActionsReceiver::class.java)
		answerIntent.action = "$packageName.ANSWER_CALL"
		answerIntent.putExtra("call_id", getCallID())

		var answerTitle: CharSequence = getString(R.string.VoipAnswerCall)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			answerTitle = SpannableString(answerTitle)
			answerTitle.setSpan(ForegroundColorSpan(-0xff5600), 0, answerTitle.length, 0)
		}

		val answerPendingIntent = PendingIntent.getBroadcast(this, 0, answerIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)

		builder.setPriority(Notification.PRIORITY_MAX)

		builder.setShowWhen(false)
		builder.setColor(-0xd35a20)
		builder.setVibrate(LongArray(0))
		builder.setCategory(Notification.CATEGORY_CALL)
		builder.setFullScreenIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE), true)

		val incomingNotification: Notification

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			val avatar = getRoundAvatarBitmap(userOrChat)
			var personName = ContactsController.formatName(userOrChat)

			if (personName.isNullOrEmpty()) {
				personName = "___"
			}

			val person = Person.Builder().setName(personName).setIcon(Icon.createWithAdaptiveBitmap(avatar)).build()
			val notificationStyle = Notification.CallStyle.forIncomingCall(person, endPendingIntent, answerPendingIntent)

			builder.style = notificationStyle

			incomingNotification = builder.build()
		}
		else {
			builder.addAction(R.drawable.ic_call_end_white_24dp, endTitle, endPendingIntent)
			builder.addAction(R.drawable.ic_call, answerTitle, answerPendingIntent)

			builder.setContentText(name)

			val customView = RemoteViews(packageName, if (LocaleController.isRTL) R.layout.call_notification_rtl else R.layout.call_notification)
			customView.setTextViewText(R.id.name, name)
			customView.setViewVisibility(R.id.subtitle, View.GONE)

			if (UserConfig.activatedAccountsCount > 1) {
				val self = UserConfig.getInstance(currentAccount).getCurrentUser()
				customView.setTextViewText(android.R.id.title, if (video) LocaleController.formatString("VoipInVideoCallBrandingWithName", R.string.VoipInVideoCallBrandingWithName, ContactsController.formatName(self?.first_name, self?.last_name)) else LocaleController.formatString("VoipInCallBrandingWithName", R.string.VoipInCallBrandingWithName, ContactsController.formatName(self?.first_name, self?.last_name)))
			}
			else {
				customView.setTextViewText(android.R.id.title, if (video) getString(R.string.VoipInVideoCallBranding) else getString(R.string.VoipInCallBranding))
			}

			val avatar = getRoundAvatarBitmap(userOrChat)

			customView.setTextViewText(R.id.answer_text, getString(R.string.VoipAnswerCall))
			customView.setTextViewText(R.id.decline_text, getString(R.string.VoipDeclineCall))
			customView.setImageViewBitmap(R.id.photo, avatar)
			customView.setOnClickPendingIntent(R.id.answer_btn, answerPendingIntent)
			customView.setOnClickPendingIntent(R.id.decline_btn, endPendingIntent)

			builder.setLargeIcon(avatar)

			incomingNotification = builder.notification
			incomingNotification.bigContentView = customView
			incomingNotification.headsUpContentView = incomingNotification.bigContentView
		}

		startForeground(ID_INCOMING_CALL_NOTIFICATION, incomingNotification)

		startRingtoneAndVibration()
	}

	private fun callFailed(error: String = if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) tgVoip[CAPTURE_DEVICE_CAMERA]!!.lastError else ERROR_UNKNOWN) {
		if (privateCall != null) {
			FileLog.d("Discarding failed call")

			val req = TL_phone_discardCall()
			req.peer = TL_inputPhoneCall()
			req.peer.access_hash = privateCall!!.access_hash
			req.peer.id = privateCall!!.id
			req.duration = (getCallDuration() / 1000).toInt()
			req.connection_id = if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) tgVoip[CAPTURE_DEVICE_CAMERA]!!.preferredRelayId else 0
			req.reason = TL_phoneCallDiscardReasonDisconnect()

			ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error1 ->
				if (error1 != null) {
					FileLog.e("error on phone.discardCall: $error1")
				}
				else {
					FileLog.d("phone.discardCall $response")
				}
			}
		}

		try {
			throw Exception("Call " + getCallID() + " failed with error: " + error)
		}
		catch (x: Exception) {
			FileLog.e(x)
		}

		lastError = error

		AndroidUtilities.runOnUIThread {
			dispatchStateChanged(STATE_FAILED)
		}

		if (TextUtils.equals(error, ERROR_LOCALIZED) && soundPool != null) {
			playingSound = true

			Utilities.globalQueue.postRunnable {
				soundPool?.play(spFailedID, 1f, 1f, 0, 0, 1f)
			}

			AndroidUtilities.runOnUIThread(afterSoundRunnable, 1000)
		}

		if (USE_CONNECTION_SERVICE) {
			systemCallConnection?.setDisconnected(DisconnectCause(DisconnectCause.ERROR))
			systemCallConnection?.destroy()
			systemCallConnection = null
		}

		stopSelf()
	}

	fun callFailedFromConnectionService() {
		if (isOutgoing) {
			callFailed(ERROR_CONNECTION_SERVICE)
		}
		else {
			hangUp()
		}
	}

	override fun onConnectionStateChanged(newState: Int, inTransition: Boolean) {
		AndroidUtilities.runOnUIThread {
			if (newState == STATE_ESTABLISHED) {
				if (callStartTime == 0L) {
					callStartTime = SystemClock.elapsedRealtime()
				}
				//peerCapabilities = tgVoip.getPeerCapabilities();
			}

			if (newState == STATE_FAILED) {
				callFailed()
				return@runOnUIThread
			}

			if (newState == STATE_ESTABLISHED) {
				if (connectingSoundRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(connectingSoundRunnable)
					connectingSoundRunnable = null
				}

				Utilities.globalQueue.postRunnable {
					if (spPlayId != 0) {
						soundPool?.stop(spPlayId)
						spPlayId = 0
					}
				}

				if (groupCall == null && !wasEstablished) {
					wasEstablished = true

					if (!isProximityNear && !privateCall!!.video) {
						vibrate(duration = 100L)
					}

					AndroidUtilities.runOnUIThread(object : Runnable {
						override fun run() {
							if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
								StatsController.getInstance(currentAccount).incrementTotalCallsTime(getStatsNetworkType(), 5)
								AndroidUtilities.runOnUIThread(this, 5000)
							}
						}
					}, 5000)

					if (isOutgoing) {
						StatsController.getInstance(currentAccount).incrementSentItemsCount(getStatsNetworkType(), StatsController.TYPE_CALLS, 1)
					}
					else {
						StatsController.getInstance(currentAccount).incrementReceivedItemsCount(getStatsNetworkType(), StatsController.TYPE_CALLS, 1)
					}
				}
			}

			if (newState == STATE_RECONNECTING) {
				Utilities.globalQueue.postRunnable {
					if (spPlayId != 0) {
						soundPool?.stop(spPlayId)
					}

					spPlayId = soundPool?.play(if (groupCall != null) spVoiceChatConnecting else spConnectingId, 1f, 1f, 0, -1, 1f) ?: 0
				}
			}

			dispatchStateChanged(newState)
		}
	}

	fun playStartRecordSound() {
		Utilities.globalQueue.postRunnable {
			soundPool?.play(spStartRecordId, 0.5f, 0.5f, 0, 0, 1f)
		}
	}

	fun playAllowTalkSound() {
		Utilities.globalQueue.postRunnable {
			soundPool?.play(spAllowTalkId, 0.5f, 0.5f, 0, 0, 1f)
		}
	}

	override fun onSignalBarCountChanged(newCount: Int) {
		AndroidUtilities.runOnUIThread {
			signalBarCount = newCount

			stateListeners.forEach {
				it.onSignalBarsCountChanged(newCount)
			}
		}
	}

	fun isBluetoothOn(): Boolean {
		val am = getSystemService(AUDIO_SERVICE) as AudioManager
		return am.isBluetoothScoOn
	}

	fun isBluetoothWillOn(): Boolean {
		return needSwitchToBluetoothAfterScoActivates
	}

	fun isHeadsetPlugged(): Boolean {
		return isHeadsetPlugged
	}

	private fun callEnded() {
		FileLog.d("Call " + getCallID() + " ended")

		if (groupCall != null && (!playedConnectedSound || onDestroyRunnable != null)) {
			needPlayEndSound = false
		}

		AndroidUtilities.runOnUIThread {
			dispatchStateChanged(STATE_ENDED)
		}

		var delay = 700

		Utilities.globalQueue.postRunnable {
			if (spPlayId != 0) {
				soundPool?.stop(spPlayId)
				spPlayId = 0
			}
		}

		if (connectingSoundRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(connectingSoundRunnable)
			connectingSoundRunnable = null
		}

		if (needPlayEndSound) {
			playingSound = true

			if (groupCall == null) {
				Utilities.globalQueue.postRunnable {
					soundPool?.play(spEndId, 1f, 1f, 0, 0, 1f)
				}
			}
			else {
				Utilities.globalQueue.postRunnable({
					soundPool?.play(spVoiceChatEndId, 1.0f, 1.0f, 0, 0, 1f)
				}, 100)

				delay = 500
			}

			AndroidUtilities.runOnUIThread(afterSoundRunnable, delay.toLong())
		}

		if (timeoutRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(timeoutRunnable)
			timeoutRunnable = null
		}

		endConnectionServiceCall(if (needPlayEndSound) delay.toLong() else 0L)

		stopSelf()
	}

	private fun endConnectionServiceCall(delay: Long) {
		if (USE_CONNECTION_SERVICE) {
			val r = Runnable {
				if (systemCallConnection != null) {
					when (callDiscardReason) {
						DISCARD_REASON_HANGUP -> systemCallConnection!!.setDisconnected(DisconnectCause(if (isOutgoing) DisconnectCause.LOCAL else DisconnectCause.REJECTED))
						DISCARD_REASON_DISCONNECT -> systemCallConnection!!.setDisconnected(DisconnectCause(DisconnectCause.ERROR))
						DISCARD_REASON_LINE_BUSY -> systemCallConnection!!.setDisconnected(DisconnectCause(DisconnectCause.BUSY))
						DISCARD_REASON_MISSED -> systemCallConnection!!.setDisconnected(DisconnectCause(if (isOutgoing) DisconnectCause.CANCELED else DisconnectCause.MISSED))
						else -> systemCallConnection!!.setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
					}

					systemCallConnection?.destroy()
					systemCallConnection = null
				}
			}

			if (delay > 0) {
				AndroidUtilities.runOnUIThread(r, delay)
			}
			else {
				r.run()
			}
		}
	}

	fun isOutgoing(): Boolean {
		return isOutgoing
	}

	fun handleNotificationAction(intent: Intent) {
		when (intent.action) {
			"$packageName.END_CALL" -> {
				stopForeground(STOP_FOREGROUND_REMOVE)
				hangUp()
			}

			"$packageName.DECLINE_CALL" -> {
				stopForeground(STOP_FOREGROUND_REMOVE)
				declineIncomingCall(DISCARD_REASON_LINE_BUSY, null)
			}

			"$packageName.ANSWER_CALL" -> {
				acceptIncomingCallFromNotification()
			}
		}
	}

	private fun acceptIncomingCallFromNotification() {
		showNotification()

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || privateCall!!.video && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
			try {
				//intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
				PendingIntent.getActivity(this@VoIPService, 0, Intent(this@VoIPService, VoIPPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE).send()
			}
			catch (x: Exception) {
				FileLog.e("Error starting permission activity", x)
			}

			return
		}

		acceptIncomingCall()

		try {
			PendingIntent.getActivity(this@VoIPService, 0, Intent(this@VoIPService, getUIActivityClass()).setAction("voip"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE).send()
		}
		catch (x: Exception) {
			FileLog.e("Error starting incall activity", x)
		}
	}

	fun updateOutputGainControlState() {
		if (hasRtmpStream()) {
			return
		}

		if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
			if (!USE_CONNECTION_SERVICE) {
				val am = getSystemService(AUDIO_SERVICE) as AudioManager

				tgVoip[CAPTURE_DEVICE_CAMERA]?.setAudioOutputGainControlEnabled(hasEarpiece() && !am.isSpeakerphoneOn && !am.isBluetoothScoOn && !isHeadsetPlugged)
				tgVoip[CAPTURE_DEVICE_CAMERA]?.setEchoCancellationStrength(if (isHeadsetPlugged || hasEarpiece() && !am.isSpeakerphoneOn && !am.isBluetoothScoOn && !isHeadsetPlugged) 0 else 1)
			}
			else {
				val isEarpiece = systemCallConnection?.callAudioState?.route == CallAudioState.ROUTE_EARPIECE

				tgVoip[CAPTURE_DEVICE_CAMERA]?.setAudioOutputGainControlEnabled(isEarpiece)
				tgVoip[CAPTURE_DEVICE_CAMERA]?.setEchoCancellationStrength(if (isEarpiece) 0 else 1)
			}
		}
	}

	fun getAccount(): Int {
		return currentAccount
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.appDidLogout) {
			callEnded()
		}
	}

	private fun isFinished(): Boolean {
		return currentState == STATE_ENDED || currentState == STATE_FAILED
	}

	fun getRemoteAudioState(): Int {
		return remoteAudioState
	}

	fun getRemoteVideoState(): Int {
		return remoteVideoState
	}

	@TargetApi(Build.VERSION_CODES.O)
	private fun addAccountToTelecomManager(): PhoneAccountHandle {
		val tm = getSystemService(TELECOM_SERVICE) as TelecomManager
		val self = UserConfig.getInstance(currentAccount).getCurrentUser()
		val handle = PhoneAccountHandle(ComponentName(this, ElloConnectionService::class.java), "" + self!!.id)
		val account = PhoneAccount.Builder(handle, ContactsController.formatName(self.first_name, self.last_name)).setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED).setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher)).setHighlightColor(-0xd35a20).addSupportedUriScheme("sip").build()
		tm.registerPhoneAccount(account)
		return handle
	}

	inner class CallConnection : Connection() {
		init {
			connectionProperties = PROPERTY_SELF_MANAGED
			audioModeIsVoip = true
		}

		override fun onCallAudioStateChanged(state: CallAudioState) {
			FileLog.d("ConnectionService call audio state changed: $state")

			for (l in stateListeners) {
				l.onAudioSettingsChanged()
			}
		}

		override fun onDisconnect() {
			FileLog.d("ConnectionService onDisconnect")

			setDisconnected(DisconnectCause(DisconnectCause.LOCAL))

			destroy()

			systemCallConnection = null

			hangUp()
		}

		override fun onAnswer() {
			acceptIncomingCallFromNotification()
		}

		override fun onReject() {
			needPlayEndSound = false
			declineIncomingCall(DISCARD_REASON_HANGUP, null)
		}

		override fun onShowIncomingCallUi() {
			startRinging()
		}

		override fun onStateChanged(state: Int) {
			super.onStateChanged(state)

			FileLog.d("ConnectionService onStateChanged " + stateToString(state))

			if (state == STATE_ACTIVE) {
				// ContactsController.getInstance(currentAccount).deleteConnectionServiceContact()
				didDeleteConnectionServiceContact = true
			}
		}

		override fun onCallEvent(event: String, extras: Bundle) {
			super.onCallEvent(event, extras)

			FileLog.d("ConnectionService onCallEvent $event")
		}

		//undocumented API
		override fun onSilence() {
			FileLog.d("onSilence")
			stopRinging()
		}
	}

	class SharedUIParams {
		var tapToVideoTooltipWasShowed = false
	}

	companion object {
		const val CALL_MIN_LAYER = 65
		const val STATE_CREATING = 6
		const val STATE_HANGING_UP = 10
		const val STATE_ENDED = 11
		const val STATE_EXCHANGING_KEYS = 12
		const val STATE_WAITING = 13
		const val STATE_REQUESTING = 14
		const val STATE_WAITING_INCOMING = 15
		const val STATE_RINGING = 16
		const val STATE_BUSY = 17
		const val STATE_WAIT_INIT = Instance.STATE_WAIT_INIT
		const val STATE_WAIT_INIT_ACK = Instance.STATE_WAIT_INIT_ACK
		const val STATE_ESTABLISHED = Instance.STATE_ESTABLISHED
		const val STATE_FAILED = Instance.STATE_FAILED
		const val STATE_RECONNECTING = Instance.STATE_RECONNECTING
		const val ACTION_HEADSET_PLUG = "android.intent.action.HEADSET_PLUG"
		private const val ID_ONGOING_CALL_NOTIFICATION = 201
		private const val ID_INCOMING_CALL_NOTIFICATION = 202
		const val QUALITY_SMALL = 0
		const val QUALITY_MEDIUM = 1
		const val QUALITY_FULL = 2
		const val CAPTURE_DEVICE_CAMERA = 0
		const val CAPTURE_DEVICE_SCREEN = 1
		const val DISCARD_REASON_HANGUP = 1
		const val DISCARD_REASON_DISCONNECT = 2
		const val DISCARD_REASON_MISSED = 3
		const val DISCARD_REASON_LINE_BUSY = 4
		const val AUDIO_ROUTE_EARPIECE = 0
		const val AUDIO_ROUTE_SPEAKER = 1
		const val AUDIO_ROUTE_BLUETOOTH = 2
		private val USE_CONNECTION_SERVICE = isDeviceCompatibleWithConnectionServiceAPI
		private const val PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32

		@JvmStatic
		var sharedInstance: VoIPService? = null
			private set

		private var setModeRunnable: Runnable? = null
		private val sync = Any()

		@JvmField
		var callIShouldHavePutIntoIntent: PhoneCall? = null

		@JvmField
		var audioLevelsCallback: AudioLevelsCallback? = null

		@JvmStatic
		fun hasRtmpStream(): Boolean {
			return sharedInstance != null && sharedInstance!!.groupCall != null && sharedInstance!!.groupCall!!.call!!.rtmp_stream
		}

		@Throws(Exception::class)
		fun convertStreamToString(`is`: InputStream?): String {
			val reader = BufferedReader(InputStreamReader(`is`))
			val sb = StringBuilder()
			var line: String?

			while (reader.readLine().also { line = it } != null) {
				sb.append(line).append("\n")
			}

			reader.close()

			return sb.toString()
		}

		@Throws(Exception::class)
		fun getStringFromFile(filePath: String): String {
			return FileInputStream(File(filePath)).use {
				convertStreamToString(it)
			}
		}

		val isAnyKindOfCallActive: Boolean
			get() = if (sharedInstance != null) {
				sharedInstance?.getCallState() != STATE_WAITING_INCOMING
			}
			else {
				false
			}
		// some non-Google devices don't implement the ConnectionService API correctly so, sadly,
		// we'll have to whitelist only a handful of known-compatible devices for now
		// /*"angler".equals(Build.PRODUCT)            // Nexus 6P
		// || "bullhead".equals(Build.PRODUCT)        // Nexus 5X
		// || "sailfish".equals(Build.PRODUCT)        // Pixel
		// || "marlin".equals(Build.PRODUCT)        // Pixel XL
		// || "walleye".equals(Build.PRODUCT)        // Pixel 2
		// || "taimen".equals(Build.PRODUCT)        // Pixel 2 XL
		// || "blueline".equals(Build.PRODUCT)        // Pixel 3
		// || "crosshatch".equals(Build.PRODUCT)    // Pixel 3 XL
		// || MessagesController.getGlobalMainSettings().getBoolean("dbg_force_connection_service", false);

		private val isDeviceCompatibleWithConnectionServiceAPI: Boolean
			get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
				false
			}
			else {
				false
			}
		// some non-Google devices don't implement the ConnectionService API correctly so, sadly,
		// we'll have to whitelist only a handful of known-compatible devices for now
		/*"angler".equals(Build.PRODUCT)            // Nexus 6P
               || "bullhead".equals(Build.PRODUCT)        // Nexus 5X
               || "sailfish".equals(Build.PRODUCT)        // Pixel
               || "marlin".equals(Build.PRODUCT)        // Pixel XL
               || "walleye".equals(Build.PRODUCT)        // Pixel 2
               || "taimen".equals(Build.PRODUCT)        // Pixel 2 XL
               || "blueline".equals(Build.PRODUCT)        // Pixel 3
               || "crosshatch".equals(Build.PRODUCT)    // Pixel 3 XL
               || MessagesController.getGlobalMainSettings().getBoolean("dbg_force_connection_service", false);*/
	}
}
