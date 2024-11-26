/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.SystemClock
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.annotation.Keep
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocationController
import org.telegram.messenger.LocationController.SharingLocationInfo
import org.telegram.messenger.MediaController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.voip.StateListener
import org.telegram.messenger.voip.VoIPPreNotificationService
import org.telegram.messenger.voip.VoIPService
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.TL_groupCallDiscarded
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AudioPlayerAlert.ClippingTextViewSwitcher
import org.telegram.ui.Components.voip.CellFlickerDrawable
import org.telegram.ui.Components.voip.VoIPHelper
import org.telegram.ui.DialogsActivity
import org.telegram.ui.LaunchActivity
import org.telegram.ui.LocationActivity
import org.telegram.ui.group.GroupCallActivity
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@SuppressLint("AppCompatCustomView")
open class FragmentContextView(context: Context, private val fragment: BaseFragment, private val applyingView: View?, private val isLocation: Boolean) : FrameLayout(context), NotificationCenterDelegate, StateListener {
	private val account = UserConfig.selectedAccount
	private val playButton: ImageView
	private var playPauseDrawable: PlayPauseDrawable? = null
	private val titleTextView: ClippingTextViewSwitcher
	private val subtitleTextView: ClippingTextViewSwitcher
	private val frameLayout: FrameLayout
	private val shadow = View(context)
	private val selector = View(context)
	private val importingImageView: RLottieImageView
	private val muteDrawable: RLottieDrawable
	private val closeButton: ImageView
	private var speedItems: Array<ActionBarMenuSubItem>? = null
	private val joinButton: TextView
	private val joinButtonFlicker = CellFlickerDrawable()
	private val avatars = AvatarsImageView(context, false)
	private val rect = RectF()
	private var speakerAmplitude = 0f
	private var micAmplitude = 0f
	private var collapseTransition = false
	private var collapseProgress: Float = 0f
	private var drawOverlay: Boolean = false
	private var animatorSet: AnimatorSet? = null
	private val muteButton: RLottieImageView
	private var playbackSpeedButton: ActionBarMenuItem? = null
	private var additionalContextView: FragmentContextView? = null
	private var isMuted = false
	private var currentProgress = -1
	private var lastMessageObject: MessageObject? = null
	private var topPadding = 0f
	private var visible: Boolean
	private var scheduleRunnableScheduled = false
	private var delegate: FragmentContextViewDelegate? = null
	private var firstLocationsLoaded = false
	private var lastLocationSharingCount = -1
	private var animationIndex = -1
	private var checkCallAfterAnimation = false
	private var checkPlayerAfterAnimation = false
	private var checkImportAfterAnimation = false
	private val updateScheduleTimeRunnable: Runnable
	private var lastString: String? = null
	private var isMusic = false
	private var supportsCalls = true
	private var gradientPaint: Paint? = null
	private var linearGradient: LinearGradient? = null
	private var matrix: Matrix? = null
	private var gradientWidth = 0
	private var gradientTextPaint: TextPaint? = null
	private var timeLayout: StaticLayout? = null
	var extraHeight: Float = 0f
	var wasDraw: Boolean = false

	@Style
	private var currentStyle = STYLE_NOT_SET

	private val checkLocationRunnable: Runnable = object : Runnable {
		override fun run() {
			checkLocationString()
			AndroidUtilities.runOnUIThread(this, 1000)
		}
	}

	constructor(context: Context, parentFragment: BaseFragment, location: Boolean) : this(context, parentFragment, null, location)

	init {
		var sizeNotifierFrameLayout: SizeNotifierFrameLayout? = null

		if (fragment.fragmentView is SizeNotifierFrameLayout) {
			sizeNotifierFrameLayout = fragment.fragmentView as SizeNotifierFrameLayout
		}

		visible = true

		if (applyingView == null) {
			(fragment.fragmentView as? ViewGroup)?.clipToPadding = false
		}

		tag = 1

		frameLayout = object : BlurredFrameLayout(context, sizeNotifierFrameLayout) {
			override fun invalidate() {
				super.invalidate()

				if (avatars.visibility == VISIBLE) {
					avatars.invalidate()
				}
			}

			override fun dispatchDraw(canvas: Canvas) {
				super.dispatchDraw(canvas)

				if (currentStyle == STYLE_INACTIVE_GROUP_CALL && timeLayout != null) {
					val width = ceil(timeLayout!!.getLineWidth(0).toDouble()).toInt() + AndroidUtilities.dp(24f)

					if (width != gradientWidth) {
						linearGradient = LinearGradient(0f, 0f, width * 1.7f, 0f, intArrayOf(-0x9b730c, -0x739631, -0x2ba687, -0x2ba687), floatArrayOf(0.0f, 0.294f, 0.588f, 1.0f), Shader.TileMode.CLAMP)
						gradientPaint?.setShader(linearGradient)
						gradientWidth = width
					}

					val call = (fragment as? ChatActivity)?.getGroupCall()
					var moveProgress = 0.0f

					if (call != null && call.isScheduled) {
						val diff = (call.call!!.schedule_date.toLong()) * 1000 - fragment.connectionsManager.currentTimeMillis

						if (diff < 0) {
							moveProgress = 1.0f
						}
						else if (diff < 5000) {
							moveProgress = 1.0f - diff / 5000.0f
						}

						if (diff < 6000) {
							invalidate()
						}
					}

					matrix.reset()
					matrix.postTranslate(-gradientWidth * 0.7f * moveProgress, 0f)

					linearGradient?.setLocalMatrix(matrix)

					val x = measuredWidth - width - AndroidUtilities.dp(10f)
					val y = AndroidUtilities.dp(10f)

					rect.set(0f, 0f, width.toFloat(), AndroidUtilities.dp(28f).toFloat())

					canvas.save()
					canvas.translate(x.toFloat(), y.toFloat())
					canvas.drawRoundRect(rect, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), gradientPaint!!)
					canvas.translate(AndroidUtilities.dp(12f).toFloat(), AndroidUtilities.dp(6f).toFloat())

					timeLayout?.draw(canvas)

					canvas.restore()
				}
			}
		}

		addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f))

		updateScheduleTimeRunnable = object : Runnable {
			override fun run() {
				if (gradientTextPaint == null || fragment !is ChatActivity) {
					scheduleRunnableScheduled = false
					return
				}

				val call = fragment.getGroupCall()

				if (call == null || !call.isScheduled) {
					timeLayout = null
					scheduleRunnableScheduled = false
					return
				}

				val currentTime = fragment.getConnectionsManager().currentTime
				val diff = call.call!!.schedule_date - currentTime

				val str = if (diff >= 24 * 60 * 60) {
					LocaleController.formatPluralString("Days", Math.round(diff / (24 * 60 * 60.0f)))
				}
				else {
					AndroidUtilities.formatFullDuration(call.call!!.schedule_date - currentTime)
				}

				val width = ceil(gradientTextPaint!!.measureText(str).toDouble()).toInt()

				timeLayout = StaticLayout(str, gradientTextPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				AndroidUtilities.runOnUIThread(this, 1000)

				frameLayout.invalidate()
			}
		}

		frameLayout.addView(selector, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		shadow.setBackgroundResource(R.drawable.blockpanel_shadow)

		addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2f, Gravity.LEFT or Gravity.TOP, 0f, 36f, 0f, 0f))

		playButton = ImageView(context)
		playButton.scaleType = ImageView.ScaleType.CENTER
		playButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand, null), PorterDuff.Mode.MULTIPLY)
		playButton.setImageDrawable(PlayPauseDrawable(14).also { playPauseDrawable = it })
		playButton.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.brand, null) and 0x19ffffff, 1, AndroidUtilities.dp(14f))

		addView(playButton, LayoutHelper.createFrame(36, 36, Gravity.TOP or Gravity.LEFT))

		playButton.setOnClickListener {
			if (currentStyle == STYLE_AUDIO_PLAYER) {
				if (MediaController.getInstance().isMessagePaused) {
					MediaController.getInstance().playMessage(MediaController.getInstance().playingMessageObject)
				}
				else {
					MediaController.getInstance().pauseMessage(MediaController.getInstance().playingMessageObject)
				}
			}
		}

		importingImageView = RLottieImageView(context)
		importingImageView.scaleType = ImageView.ScaleType.CENTER
		importingImageView.setAutoRepeat(true)
		importingImageView.setAnimation(R.raw.import_progress, 30, 30)
		importingImageView.background = Theme.createCircleDrawable(AndroidUtilities.dp(22f), ResourcesCompat.getColor(context.resources, R.color.brand, null))

		addView(importingImageView, LayoutHelper.createFrame(22, 22f, Gravity.TOP or Gravity.LEFT, 7f, 7f, 0f, 0f))

		titleTextView = object : ClippingTextViewSwitcher(context) {
			override fun createTextView(): TextView {
				val textView = TextView(context)
				textView.maxLines = 1
				textView.setLines(1)
				textView.isSingleLine = true
				textView.ellipsize = TextUtils.TruncateAt.END
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
				textView.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT

				when (currentStyle) {
					STYLE_AUDIO_PLAYER, STYLE_LIVE_LOCATION -> {
						textView.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
						textView.typeface = Theme.TYPEFACE_DEFAULT
						textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
					}

					STYLE_INACTIVE_GROUP_CALL -> {
						textView.gravity = Gravity.TOP or Gravity.LEFT
						textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.undead_dark, null))
						textView.typeface = Theme.TYPEFACE_BOLD
						textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
					}

					STYLE_CONNECTING_GROUP_CALL, STYLE_ACTIVE_GROUP_CALL -> {
						textView.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
						textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.white, null))
						textView.typeface = Theme.TYPEFACE_BOLD
						textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
					}
				}

				return textView
			}
		}

		addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.LEFT or Gravity.TOP, 35f, 0f, 36f, 0f))

		subtitleTextView = object : ClippingTextViewSwitcher(context) {
			override fun createTextView(): TextView {
				val textView = TextView(context)
				textView.maxLines = 1
				textView.setLines(1)
				textView.isSingleLine = true
				textView.ellipsize = TextUtils.TruncateAt.END
				textView.gravity = Gravity.LEFT
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
				textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.disabled_text, null))
				return textView
			}
		}

		addView(subtitleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.LEFT or Gravity.TOP, 35f, 10f, 36f, 0f))

		joinButtonFlicker.setProgress(2f)
		joinButtonFlicker.repeatEnabled = false

		joinButton = object : TextView(context) {
			override fun draw(canvas: Canvas) {
				super.draw(canvas)

				val halfOutlineWidth = AndroidUtilities.dp(1f)

				AndroidUtilities.rectTmp[halfOutlineWidth.toFloat(), halfOutlineWidth.toFloat(), (width - halfOutlineWidth).toFloat()] = (height - halfOutlineWidth).toFloat()

				joinButtonFlicker.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(16f).toFloat(), this)

				if (joinButtonFlicker.getProgress() < 1f && !joinButtonFlicker.repeatEnabled) {
					invalidate()
				}
			}

			override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
				super.onSizeChanged(w, h, oldw, oldh)

				joinButtonFlicker.setParentWidth(width)
			}
		}

		joinButton.setText(context.getString(R.string.VoipChatJoin))
		joinButton.setTextColor(ResourcesCompat.getColor(context.resources, R.color.white, null))
		joinButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16f), ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.darker_brand, null)))
		joinButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		joinButton.setTypeface(Theme.TYPEFACE_BOLD)
		joinButton.setGravity(Gravity.CENTER)
		joinButton.setPadding(AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f), 0)
		joinButton.setOnClickListener { this@FragmentContextView.callOnClick() }

		addView(joinButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28f, Gravity.TOP or Gravity.RIGHT, 0f, 10f, 14f, 0f))

		if (!isLocation) {
			playbackSpeedButton = ActionBarMenuItem(context, null, 0, context.getColor(R.color.text))
			playbackSpeedButton?.setLongClickEnabled(false)
			playbackSpeedButton?.setShowSubmenuByMove(false)
			playbackSpeedButton?.contentDescription = context.getString(R.string.AccDescrPlayerSpeed)

			playbackSpeedButton?.setDelegate {
				val oldSpeed = MediaController.getInstance().getPlaybackSpeed(isMusic)

				when (it) {
					MENU_SPEED_SLOW -> MediaController.getInstance().setPlaybackSpeed(isMusic, 0.5f)
					MENU_SPEED_NORMAL -> MediaController.getInstance().setPlaybackSpeed(isMusic, 1.0f)
					MENU_SPEED_FAST -> MediaController.getInstance().setPlaybackSpeed(isMusic, 1.5f)
					else -> MediaController.getInstance().setPlaybackSpeed(isMusic, 1.8f)
				}

				val newSpeed = MediaController.getInstance().getPlaybackSpeed(isMusic)

				if (oldSpeed != newSpeed) {
					playbackSpeedChanged(newSpeed)
				}

				updatePlaybackButton()
			}

			speedItems = arrayOf(
					playbackSpeedButton!!.addSubItem(MENU_SPEED_SLOW, R.drawable.msg_speed_0_5, context.getString(R.string.SpeedSlow)),
					playbackSpeedButton!!.addSubItem(MENU_SPEED_NORMAL, R.drawable.msg_speed_1, context.getString(R.string.SpeedNormal)),
					playbackSpeedButton!!.addSubItem(MENU_SPEED_FAST, R.drawable.msg_speed_1_5, context.getString(R.string.SpeedFast)),
					playbackSpeedButton!!.addSubItem(MENU_SPEED_VERY_FAST, R.drawable.msg_speed_2, context.getString(R.string.SpeedVeryFast)),
			)


			if (AndroidUtilities.density >= 3.0f) {
				playbackSpeedButton?.setPadding(0, 1, 0, 0)
			}

			playbackSpeedButton?.setAdditionalXOffset(AndroidUtilities.dp(8f))

			addView(playbackSpeedButton, LayoutHelper.createFrame(36, 36f, Gravity.TOP or Gravity.RIGHT, 0f, 0f, 36f, 0f))

			playbackSpeedButton?.setOnClickListener {
				val currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(isMusic)
				var newSpeed: Float

				if (abs((currentPlaybackSpeed - 1.0f).toDouble()) > 0.001f) {
					MediaController.getInstance().setPlaybackSpeed(isMusic, 1.0f.also { newSpeed = it })
				}
				else {
					MediaController.getInstance().setPlaybackSpeed(isMusic, MediaController.getInstance().getFastPlaybackSpeed(isMusic).also { newSpeed = it })
				}

				playbackSpeedChanged(newSpeed)
			}

			playbackSpeedButton?.setOnLongClickListener {
				playbackSpeedButton?.toggleSubMenu()
				true
			}

			updatePlaybackButton()
		}

		avatars.setDelegate { updateAvatars(true) }
		avatars.visibility = GONE

		addView(avatars, LayoutHelper.createFrame(108, 36, Gravity.LEFT or Gravity.TOP))

		muteDrawable = RLottieDrawable(R.raw.voice_muted, "" + R.raw.voice_muted, AndroidUtilities.dp(16f), AndroidUtilities.dp(20f), true, null)

		muteButton = object : RLottieImageView(context) {
			private val toggleMicRunnable = Runnable {
				if (VoIPService.sharedInstance == null) {
					return@Runnable
				}

				VoIPService.sharedInstance?.setMicMute(mute = false, hold = true, send = false)

				if (muteDrawable.setCustomEndFrame(if (isMuted) 15 else 29)) {
					if (isMuted) {
						muteDrawable.setCurrentFrame(0)
					}
					else {
						muteDrawable.setCurrentFrame(14)
					}
				}

				this.playAnimation()

				Theme.getFragmentContextViewWavesDrawable().updateState(true)
			}

			private var scheduled: Boolean = false
			private var pressed: Boolean = false

			private val pressRunnable = Runnable {
				if (!scheduled || VoIPService.sharedInstance == null) {
					return@Runnable
				}

				scheduled = false
				pressed = true
				isMuted = false

				AndroidUtilities.runOnUIThread(toggleMicRunnable, 90)

				this.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
			}

			override fun onTouchEvent(event: MotionEvent): Boolean {
				if (currentStyle == STYLE_ACTIVE_GROUP_CALL || currentStyle == STYLE_CONNECTING_GROUP_CALL) {
					val service = VoIPService.sharedInstance

					if (service == null) {
						AndroidUtilities.cancelRunOnUIThread(pressRunnable)
						AndroidUtilities.cancelRunOnUIThread(toggleMicRunnable)

						scheduled = false
						pressed = false

						return true
					}

					if (event.action == MotionEvent.ACTION_DOWN && service.isMicMute()) {
						AndroidUtilities.runOnUIThread(pressRunnable, 300)
						scheduled = true
					}
					else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
						AndroidUtilities.cancelRunOnUIThread(toggleMicRunnable)

						if (scheduled) {
							AndroidUtilities.cancelRunOnUIThread(pressRunnable)
							scheduled = false
						}
						else if (pressed) {
							isMuted = true

							if (muteDrawable.setCustomEndFrame(15)) {
								if (isMuted) {
									muteDrawable.setCurrentFrame(0)
								}
								else {
									muteDrawable.setCurrentFrame(14)
								}
							}

							playAnimation()

							service.setMicMute(mute = true, hold = true, send = false)

							performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

							pressed = false

							Theme.getFragmentContextViewWavesDrawable().updateState(true)

							val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)

							super.onTouchEvent(cancel)

							cancel.recycle()

							return true
						}
					}

					return super.onTouchEvent(event)
				}
				else {
					return super.onTouchEvent(event)
				}
			}

			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)
				info.className = Button::class.java.name
				info.text = if (isMuted) context.getString(R.string.VoipUnmute) else context.getString(R.string.VoipMute)
			}
		}

		muteButton.setColorFilter(PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.MULTIPLY))
		muteButton.setBackground(Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.disabled_text, null) and 0x19ffffff, 1, AndroidUtilities.dp(14f)))
		muteButton.setAnimation(muteDrawable)
		muteButton.setScaleType(ImageView.ScaleType.CENTER)
		muteButton.setVisibility(GONE)

		addView(muteButton, LayoutHelper.createFrame(36, 36f, Gravity.RIGHT or Gravity.TOP, 0f, 0f, 2f, 0f))

		muteButton.setOnClickListener {
			val voIPService = VoIPService.sharedInstance ?: return@setOnClickListener

			if (voIPService.groupCall != null) {
				val call = voIPService.groupCall
				val chat = voIPService.getChat()
				val participant = call?.participants?.get(voIPService.getSelfId())

				if (participant != null && !participant.can_self_unmute && participant.muted && !ChatObject.canManageCalls(chat)) {
					return@setOnClickListener
				}
			}

			isMuted = !voIPService.isMicMute()
			voIPService.setMicMute(isMuted, hold = false, send = true)

			if (muteDrawable.setCustomEndFrame(if (isMuted) 15 else 29)) {
				if (isMuted) {
					muteDrawable.setCurrentFrame(0)
				}
				else {
					muteDrawable.setCurrentFrame(14)
				}
			}

			muteButton.playAnimation()

			Theme.getFragmentContextViewWavesDrawable().updateState(true)

			muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
		}

		closeButton = ImageView(context)
		closeButton.setImageResource(R.drawable.miniplayer_close)
		closeButton.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.disabled_text, null), PorterDuff.Mode.MULTIPLY)
		closeButton.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.disabled_text, null) and 0x19ffffff, 1, AndroidUtilities.dp(14f))
		closeButton.scaleType = ImageView.ScaleType.CENTER

		addView(closeButton, LayoutHelper.createFrame(36, 36f, Gravity.RIGHT or Gravity.TOP, 0f, 0f, 2f, 0f))

		closeButton.setOnClickListener {
			if (currentStyle == STYLE_LIVE_LOCATION) {
				val builder = AlertDialog.Builder(context)
				builder.setTitle(context.getString(R.string.StopLiveLocationAlertToTitle))

				if (fragment is DialogsActivity) {
					builder.setMessage(context.getString(R.string.StopLiveLocationAlertAllText))
				}
				else {
					val activity = fragment as? ChatActivity
					val chat = activity?.currentChat
					val user = activity?.currentUser

					if (chat != null) {
						builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("StopLiveLocationAlertToGroupText", R.string.StopLiveLocationAlertToGroupText, chat.title)))
					}
					else if (user != null) {
						builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("StopLiveLocationAlertToUserText", R.string.StopLiveLocationAlertToUserText, UserObject.getFirstName(user))))
					}
					else {
						builder.setMessage(context.getString(R.string.AreYouSure))
					}
				}

				builder.setPositiveButton(context.getString(R.string.Stop)) { _, _ ->
					if (fragment is DialogsActivity) {
						for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
							LocationController.getInstance(a).removeAllLocationShares()
						}
					}
					else {
						LocationController.getInstance(fragment.currentAccount).removeSharingLocation((fragment as? ChatActivity)?.dialogId)
					}
				}

				builder.setNegativeButton(context.getString(R.string.Cancel), null)

				val alertDialog = builder.create()
				builder.show()

				val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
				button?.setTextColor(context.getColor(R.color.purple))
			}
			else {
				MediaController.getInstance().cleanupPlayer(true, true)
			}
		}

		setOnClickListener {
			if (currentStyle == STYLE_AUDIO_PLAYER) {
				val messageObject = MediaController.getInstance().playingMessageObject

				if (messageObject != null) {
					if (messageObject.isMusic) {
						if (getContext() is LaunchActivity) {
							fragment.showDialog(AudioPlayerAlert(getContext()))
						}
					}
					else {
						var dialogId: Long = 0

						if (fragment is ChatActivity) {
							dialogId = fragment.dialogId
						}

						if (messageObject.dialogId == dialogId) {
							(fragment as ChatActivity).scrollToMessageId(messageObject.id, 0, false, 0, true, 0)
						}
						else {
							dialogId = messageObject.dialogId

							val args = Bundle()

							if (DialogObject.isEncryptedDialog(dialogId)) {
								args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId))
							}
							else if (DialogObject.isUserDialog(dialogId)) {
								args.putLong("user_id", dialogId)
							}
							else {
								args.putLong("chat_id", -dialogId)
							}

							args.putInt("message_id", messageObject.id)

							fragment.presentFragment(ChatActivity(args), fragment is ChatActivity)
						}
					}
				}
			}
			else if (currentStyle == STYLE_CONNECTING_GROUP_CALL) {
				val intent = Intent(getContext(), LaunchActivity::class.java).setAction(VoIPPreNotificationService.VOIP_ACTION)
				getContext().startActivity(intent)
			}
			else if (currentStyle == STYLE_LIVE_LOCATION) {
				var did: Long = 0
				var account = UserConfig.selectedAccount

				if (fragment is ChatActivity) {
					did = fragment.dialogId
					account = fragment.getCurrentAccount()
				}
				else if (LocationController.locationsCount == 1) {
					for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
						val arrayList = LocationController.getInstance(a).sharingLocationsUI

						if (arrayList.isNotEmpty()) {
							val info = LocationController.getInstance(a).sharingLocationsUI[0]
							did = info.did
							account = info.messageObject!!.currentAccount
							break
						}
					}
				}

				if (did != 0L) {
					openSharingLocation(LocationController.getInstance(account).getSharingLocationInfo(did))
				}
				else {
					fragment.showDialog(SharingLocationsAlert(getContext()) {
						this.openSharingLocation(it)
					})
				}
			}
			else if (currentStyle == STYLE_ACTIVE_GROUP_CALL) {
				if (VoIPService.sharedInstance != null && getContext() is LaunchActivity) {
					GroupCallActivity.create(getContext() as LaunchActivity, AccountInstance.getInstance(VoIPService.sharedInstance!!.getAccount()), null, null, false, null)
				}
			}
			else if (currentStyle == STYLE_INACTIVE_GROUP_CALL) {
				if (fragment.parentActivity == null) {
					return@setOnClickListener
				}

				val chatActivity = fragment as? ChatActivity
				val call = chatActivity?.getGroupCall() ?: return@setOnClickListener

				VoIPHelper.startCall(chatActivity.messagesController.getChat(call.chatId), null, false, call.call != null && !call.call!!.rtmp_stream, fragment.getParentActivity(), fragment, fragment.accountInstance)
			}
			else if (currentStyle == STYLE_IMPORTING_MESSAGES) {
				val importingHistory = fragment.sendMessagesHelper.getImportingHistory((fragment as ChatActivity).dialogId) ?: return@setOnClickListener

				val importingAlert = ImportingAlert(getContext(), null, fragment as? ChatActivity)
				importingAlert.setOnHideListener { checkImport(false) }

				fragment.showDialog(importingAlert)

				checkImport(false)
			}
		}
	}

	override fun onAudioSettingsChanged() {
		val newMuted = VoIPService.sharedInstance?.isMicMute() == true

		if (isMuted != newMuted) {
			isMuted = newMuted

			muteDrawable.setCustomEndFrame(if (isMuted) 15 else 29)
			muteDrawable.setCurrentFrame(muteDrawable.getCustomEndFrame() - 1, false, true)

			muteButton.invalidate()

			Theme.getFragmentContextViewWavesDrawable().updateState(visible)
		}

		if (isMuted) {
			micAmplitude = 0f

			Theme.getFragmentContextViewWavesDrawable().setAmplitude(0f)
		}
	}

	fun drawOverlayed(): Boolean {
		return currentStyle == STYLE_ACTIVE_GROUP_CALL
	}

	fun setSupportsCalls(value: Boolean) {
		supportsCalls = value
	}

	fun setDelegate(fragmentContextViewDelegate: FragmentContextViewDelegate?) {
		delegate = fragmentContextViewDelegate
	}

	private fun updatePlaybackButton() {
		val playbackSpeedButton = playbackSpeedButton ?: return
		val currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(isMusic)
		val speed = MediaController.getInstance().getFastPlaybackSpeed(isMusic)

		if (abs((speed - 1.8f).toDouble()) < 0.001f) {
			playbackSpeedButton.setIcon(R.drawable.voice_mini_2_0)
		}
		else if (abs((speed - 1.5f).toDouble()) < 0.001f) {
			playbackSpeedButton.setIcon(R.drawable.voice_mini_1_5)
		}
		else {
			playbackSpeedButton.setIcon(R.drawable.voice_mini_0_5)
		}

		updateColors()

		val speedItems = speedItems ?: return

		for (a in speedItems.indices) {
			if (a == 0 && abs((currentPlaybackSpeed - 0.5f).toDouble()) < 0.001f || a == 1 && abs((currentPlaybackSpeed - 1.0f).toDouble()) < 0.001f || a == 2 && abs((currentPlaybackSpeed - 1.5f).toDouble()) < 0.001f || a == 3 && abs((currentPlaybackSpeed - 1.8f).toDouble()) < 0.001f) {
				speedItems[a].setColors(ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.brand, null))
			}
			else {
				speedItems[a].setColors(ResourcesCompat.getColor(context.resources, R.color.text, null), ResourcesCompat.getColor(context.resources, R.color.brand, null))
			}
		}
	}

	fun updateColors() {
		playbackSpeedButton?.let {
			val color = ResourcesCompat.getColor(context.resources, R.color.brand, null)

			it.setIconColor(color)
			it.background = Theme.createSelectorDrawable(color and 0x19ffffff, 1, AndroidUtilities.dp(14f))
		}
	}

	fun setAdditionalContextView(contextView: FragmentContextView?) {
		additionalContextView = contextView
	}

	private fun openSharingLocation(info: SharingLocationInfo?) {
		if (info == null || fragment.parentActivity !is LaunchActivity) {
			return
		}

		val launchActivity = fragment.parentActivity as LaunchActivity
		launchActivity.switchToAccount(info.messageObject!!.currentAccount)

		val locationActivity = LocationActivity(2)
		locationActivity.setMessageObject(info.messageObject)

		locationActivity.setDelegate { location, _, notify, scheduleDate ->
			SendMessagesHelper.getInstance(info.messageObject!!.currentAccount).sendMessage(location, info.messageObject!!.dialogId, null, null, null, null, notify, scheduleDate, false, null)
		}

		launchActivity.presentFragment(locationActivity)
	}

	@Keep
	fun getTopPadding(): Float {
		return topPadding
	}

	@Keep
	fun setTopPadding(value: Float) {
		topPadding = value

		if (parent != null) {
			val view = applyingView ?: fragment.fragmentView
			var additionalPadding = 0

			if (additionalContextView?.visibility == VISIBLE && additionalContextView?.parent != null) {
				additionalPadding = AndroidUtilities.dp(additionalContextView!!.styleHeight.toFloat())
			}

			if (view != null && parent != null) {
				view.setPadding(0, (if (visibility == VISIBLE) topPadding else 0f).toInt() + additionalPadding, 0, 0)
			}
		}
	}

	private fun checkVisibility() {
		var show = false

		if (isLocation) {
			show = if (fragment is DialogsActivity) {
				LocationController.locationsCount != 0
			}
			else {
				LocationController.getInstance(fragment.currentAccount).isSharingLocation((fragment as ChatActivity?)!!.dialogId)
			}
		}
		else {
			if (VoIPService.sharedInstance != null && !VoIPService.sharedInstance!!.isHangingUp() && VoIPService.sharedInstance!!.getCallState() != VoIPService.STATE_WAITING_INCOMING) {
				show = true
				startJoinFlickerAnimation()
			}
			else if ((fragment is ChatActivity) && fragment.getSendMessagesHelper().getImportingHistory(fragment.dialogId) != null && !isPlayingVoice) {
				show = true
			}
			else if ((fragment is ChatActivity) && fragment.getGroupCall() != null && fragment.getGroupCall()!!.shouldShowPanel() && !GroupCallPip.isShowing() && !isPlayingVoice) {
				show = true
				startJoinFlickerAnimation()
			}
			else {
				val messageObject = MediaController.getInstance().playingMessageObject

				if (messageObject != null && messageObject.id != 0) {
					show = true
				}
			}
		}

		visibility = if (show) VISIBLE else GONE
	}

	protected open fun playbackSpeedChanged(value: Float) {
		// stub
	}

	private fun updateStyle(@Style style: Int) {
		if (currentStyle == style) {
			return
		}

		if (currentStyle == STYLE_ACTIVE_GROUP_CALL || currentStyle == STYLE_CONNECTING_GROUP_CALL) {
			Theme.getFragmentContextViewWavesDrawable().removeParent(this)
			VoIPService.sharedInstance?.unregisterStateListener(this)
		}

		currentStyle = style

		frameLayout.setWillNotDraw(currentStyle != STYLE_INACTIVE_GROUP_CALL)

		if (style != STYLE_INACTIVE_GROUP_CALL) {
			timeLayout = null
		}

		avatars.setStyle(currentStyle)
		avatars.layoutParams = LayoutHelper.createFrame(108, styleHeight, Gravity.LEFT or Gravity.TOP)

		frameLayout.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, styleHeight.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f)

		shadow.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2f, Gravity.LEFT or Gravity.TOP, 0f, styleHeight.toFloat(), 0f, 0f)

		if (topPadding > 0 && topPadding != AndroidUtilities.dp2(styleHeight.toFloat()).toFloat()) {
			updatePaddings()
			setTopPadding(AndroidUtilities.dp2(styleHeight.toFloat()).toFloat())
		}

		if (style == STYLE_IMPORTING_MESSAGES) {
			selector.background = Theme.getSelectorDrawable(false)

			frameLayout.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

			for (i in 0..1) {
				val textView = if (i == 0) titleTextView.textView else titleTextView.nextTextView

				if (textView == null) {
					continue
				}

				textView.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
				textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
				textView.typeface = Theme.TYPEFACE_DEFAULT
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			}

			subtitleTextView.visibility = GONE
			joinButton.visibility = GONE
			closeButton.visibility = GONE
			playButton.visibility = GONE
			muteButton.visibility = GONE
			avatars.visibility = GONE

			importingImageView.visibility = VISIBLE
			importingImageView.playAnimation()

			closeButton.contentDescription = context.getString(R.string.AccDescrClosePlayer)

			playbackSpeedButton?.visibility = GONE

			titleTextView.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.LEFT or Gravity.TOP, 35f, 0f, 36f, 0f)
		}
		else if (style == STYLE_AUDIO_PLAYER || style == STYLE_LIVE_LOCATION) {
			selector.background = Theme.getSelectorDrawable(false)

			frameLayout.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

			subtitleTextView.visibility = GONE
			joinButton.visibility = GONE
			closeButton.visibility = VISIBLE
			playButton.visibility = VISIBLE
			muteButton.visibility = GONE

			importingImageView.visibility = GONE
			importingImageView.stopAnimation()

			avatars.visibility = GONE

			for (i in 0..1) {
				val textView = if (i == 0) titleTextView.textView else titleTextView.nextTextView

				if (textView == null) {
					continue
				}

				textView.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
				textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
				textView.typeface = Theme.TYPEFACE_DEFAULT
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			}

			if (style == STYLE_AUDIO_PLAYER) {
				playButton.layoutParams = LayoutHelper.createFrame(36, 36f, Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f)
				titleTextView.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.LEFT or Gravity.TOP, 35f, 0f, 36f, 0f)

				playbackSpeedButton?.visibility = VISIBLE

				closeButton.contentDescription = context.getString(R.string.AccDescrClosePlayer)
			}
			else {
				playButton.layoutParams = LayoutHelper.createFrame(36, 36f, Gravity.TOP or Gravity.LEFT, 8f, 0f, 0f, 0f)
				titleTextView.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.LEFT or Gravity.TOP, (35 + 16).toFloat(), 0f, 36f, 0f)
				closeButton.contentDescription = context.getString(R.string.AccDescrStopLiveLocation)
			}
		}
		else if (style == STYLE_INACTIVE_GROUP_CALL) {
			selector.background = Theme.getSelectorDrawable(false)

			frameLayout.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

			muteButton.visibility = GONE
			subtitleTextView.visibility = VISIBLE

			for (i in 0..1) {
				val textView = if (i == 0) titleTextView.textView else titleTextView.nextTextView

				if (textView == null) {
					continue
				}

				textView.gravity = Gravity.TOP or Gravity.LEFT
				textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.undead_dark, null))
				textView.typeface = Theme.TYPEFACE_BOLD
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			}

			titleTextView.setPadding(0, 0, 0, 0)

			importingImageView.visibility = GONE
			importingImageView.stopAnimation()

			var isRtmpStream = false

			if (fragment is ChatActivity) {
				isRtmpStream = fragment.getGroupCall()?.call?.rtmp_stream == true
			}

			avatars.visibility = if (!isRtmpStream) VISIBLE else GONE

			if (avatars.visibility != GONE) {
				updateAvatars(false)
			}
			else {
				titleTextView.translationX = -AndroidUtilities.dp(36f).toFloat()
				subtitleTextView.translationX = -AndroidUtilities.dp(36f).toFloat()
			}

			closeButton.visibility = GONE
			playButton.visibility = GONE

			playbackSpeedButton?.visibility = GONE
		}
		else if (style == STYLE_CONNECTING_GROUP_CALL || style == STYLE_ACTIVE_GROUP_CALL) {
			selector.background = null

			updateCallTitle()

			val isRtmpStream = VoIPService.hasRtmpStream()

			avatars.visibility = if (!isRtmpStream) VISIBLE else GONE

			if (style == STYLE_ACTIVE_GROUP_CALL) {
				VoIPService.sharedInstance?.registerStateListener(this)
			}

			if (avatars.visibility != GONE) {
				updateAvatars(false)
			}
			else {
				titleTextView.translationX = 0f
				subtitleTextView.translationX = 0f
			}

			muteButton.visibility = if (!isRtmpStream) VISIBLE else GONE
			isMuted = VoIPService.sharedInstance?.isMicMute() == true

			muteDrawable.setCustomEndFrame(if (isMuted) 15 else 29)
			muteDrawable.setCurrentFrame(muteDrawable.getCustomEndFrame() - 1, false, true)

			muteButton.invalidate()

			frameLayout.background = null
			frameLayout.setBackgroundColor(Color.TRANSPARENT)

			importingImageView.visibility = GONE
			importingImageView.stopAnimation()

			Theme.getFragmentContextViewWavesDrawable().addParent(this)

			invalidate()

			for (i in 0..1) {
				val textView = if (i == 0) titleTextView.textView else titleTextView.nextTextView

				if (textView == null) {
					continue
				}
				textView.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
				textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.white, null))
				textView.typeface = Theme.TYPEFACE_BOLD
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			}

			closeButton.visibility = GONE
			playButton.visibility = GONE
			subtitleTextView.visibility = GONE
			joinButton.visibility = GONE

			titleTextView.layoutParams = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, 0f, 0f, 2f)
			titleTextView.setPadding(AndroidUtilities.dp(112f), 0, AndroidUtilities.dp(112f), 0)

			playbackSpeedButton?.visibility = GONE
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()

		animatorSet?.cancel()
		animatorSet = null

		if (scheduleRunnableScheduled) {
			AndroidUtilities.cancelRunOnUIThread(updateScheduleTimeRunnable)
			scheduleRunnableScheduled = false
		}

		visible = false

		NotificationCenter.getInstance(account).onAnimationFinish(animationIndex)

		topPadding = 0f

		if (isLocation) {
			NotificationCenter.globalInstance.let {
				it.removeObserver(this, NotificationCenter.liveLocationsChanged)
				it.removeObserver(this, NotificationCenter.liveLocationsCacheChanged)
			}
		}
		else {
			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				NotificationCenter.getInstance(a).let {
					it.removeObserver(this, NotificationCenter.messagePlayingDidReset)
					it.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
					it.removeObserver(this, NotificationCenter.messagePlayingDidStart)
					it.removeObserver(this, NotificationCenter.groupCallUpdated)
					it.removeObserver(this, NotificationCenter.groupCallTypingsUpdated)
					it.removeObserver(this, NotificationCenter.historyImportProgressChanged)
				}
			}

			NotificationCenter.globalInstance.let {
				it.removeObserver(this, NotificationCenter.messagePlayingSpeedChanged)
				it.removeObserver(this, NotificationCenter.didStartedCall)
				it.removeObserver(this, NotificationCenter.didEndCall)
				it.removeObserver(this, NotificationCenter.webRtcSpeakerAmplitudeEvent)
				it.removeObserver(this, NotificationCenter.webRtcMicAmplitudeEvent)
				it.removeObserver(this, NotificationCenter.groupCallVisibilityChanged)
			}
		}

		if (currentStyle == STYLE_ACTIVE_GROUP_CALL || currentStyle == STYLE_CONNECTING_GROUP_CALL) {
			Theme.getFragmentContextViewWavesDrawable().removeParent(this)
		}

		VoIPService.sharedInstance?.unregisterStateListener(this)

		wasDraw = false
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		if (isLocation) {
			NotificationCenter.globalInstance.let {
				it.addObserver(this, NotificationCenter.liveLocationsChanged)
				it.addObserver(this, NotificationCenter.liveLocationsCacheChanged)
			}

			additionalContextView?.checkVisibility()

			checkLiveLocation(true)
		}
		else {
			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				NotificationCenter.getInstance(a).let {
					it.addObserver(this, NotificationCenter.messagePlayingDidReset)
					it.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
					it.addObserver(this, NotificationCenter.messagePlayingDidStart)
					it.addObserver(this, NotificationCenter.groupCallUpdated)
					it.addObserver(this, NotificationCenter.groupCallTypingsUpdated)
					it.addObserver(this, NotificationCenter.historyImportProgressChanged)
				}
			}

			NotificationCenter.globalInstance.let {
				it.addObserver(this, NotificationCenter.messagePlayingSpeedChanged)
				it.addObserver(this, NotificationCenter.didStartedCall)
				it.addObserver(this, NotificationCenter.didEndCall)
				it.addObserver(this, NotificationCenter.webRtcSpeakerAmplitudeEvent)
				it.addObserver(this, NotificationCenter.webRtcMicAmplitudeEvent)
				it.addObserver(this, NotificationCenter.groupCallVisibilityChanged)
			}

			additionalContextView?.checkVisibility()

			if (VoIPService.sharedInstance != null && !VoIPService.sharedInstance!!.isHangingUp() && VoIPService.sharedInstance!!.getCallState() != VoIPService.STATE_WAITING_INCOMING && !GroupCallPip.isShowing()) {
				checkCall(true)
			}
			else if (fragment is ChatActivity && fragment.getSendMessagesHelper().getImportingHistory(fragment.dialogId) != null && !isPlayingVoice) {
				checkImport(true)
			}
			else if ((fragment is ChatActivity) && fragment.getGroupCall() != null && fragment.getGroupCall()!!.shouldShowPanel() && !GroupCallPip.isShowing() && !isPlayingVoice) {
				checkCall(true)
			}
			else {
				checkCall(true)
				checkPlayer(true)
				updatePlaybackButton()
			}
		}

		if (currentStyle == STYLE_ACTIVE_GROUP_CALL || currentStyle == STYLE_CONNECTING_GROUP_CALL) {
			Theme.getFragmentContextViewWavesDrawable().addParent(this)

			VoIPService.sharedInstance?.registerStateListener(this)

			val newMuted = VoIPService.sharedInstance?.isMicMute() == true

			if (isMuted != newMuted) {
				isMuted = newMuted

				muteDrawable.setCustomEndFrame(if (isMuted) 15 else 29)
				muteDrawable.setCurrentFrame(muteDrawable.getCustomEndFrame() - 1, false, true)

				muteButton.invalidate()
			}
		}
		else if (currentStyle == STYLE_INACTIVE_GROUP_CALL) {
			if (!scheduleRunnableScheduled) {
				scheduleRunnableScheduled = true
				updateScheduleTimeRunnable.run()
			}
		}

		if (visible && topPadding == 0f) {
			updatePaddings()
			setTopPadding(AndroidUtilities.dp2(styleHeight.toFloat()).toFloat())
		}

		speakerAmplitude = 0f
		micAmplitude = 0f
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, AndroidUtilities.dp2((styleHeight + 2).toFloat()))
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.liveLocationsChanged -> {
				checkLiveLocation(false)
			}

			NotificationCenter.liveLocationsCacheChanged -> {
				if (fragment is ChatActivity) {
					val did = args[0] as Long

					if (fragment.dialogId == did) {
						checkLocationString()
					}
				}
			}

			NotificationCenter.messagePlayingDidStart, NotificationCenter.messagePlayingPlayStateChanged, NotificationCenter.messagePlayingDidReset, NotificationCenter.didEndCall -> {
				if (currentStyle == STYLE_CONNECTING_GROUP_CALL || currentStyle == STYLE_ACTIVE_GROUP_CALL || currentStyle == STYLE_INACTIVE_GROUP_CALL) {
					checkCall(false)
				}

				checkPlayer(false)
			}

			NotificationCenter.didStartedCall, NotificationCenter.groupCallUpdated, NotificationCenter.groupCallVisibilityChanged -> {
				checkCall(false)

				if (currentStyle == STYLE_ACTIVE_GROUP_CALL) {
					val sharedInstance = VoIPService.sharedInstance

					if (sharedInstance?.groupCall != null) {
						if (id == NotificationCenter.didStartedCall) {
							sharedInstance.registerStateListener(this)
						}

						val currentCallState = sharedInstance.getCallState()

						if (currentCallState == VoIPService.STATE_WAIT_INIT || currentCallState == VoIPService.STATE_WAIT_INIT_ACK || currentCallState == VoIPService.STATE_CREATING || currentCallState == VoIPService.STATE_RECONNECTING) {
							// unused
						}
						else {
							val participant = sharedInstance.groupCall?.participants?.get(sharedInstance.getSelfId())

							if (participant != null && !participant.can_self_unmute && participant.muted && !ChatObject.canManageCalls(sharedInstance.getChat())) {
								sharedInstance.setMicMute(mute = true, hold = false, send = false)

								val now = SystemClock.uptimeMillis()
								val e = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)

								muteButton.dispatchTouchEvent(e)
							}
						}
					}
				}
			}

			NotificationCenter.groupCallTypingsUpdated -> {
				if (visible && currentStyle == STYLE_INACTIVE_GROUP_CALL) {
					val call = (fragment as? ChatActivity)?.getGroupCall()

					if (call != null) {
						if (call.isScheduled) {
							subtitleTextView.setText(LocaleController.formatStartsTime(call.call!!.schedule_date.toLong(), 4), false)
						}
						else if (call.call?.participants_count == 0) {
							subtitleTextView.setText(context.getString(if (call.call!!.rtmp_stream) R.string.ViewersWatchingNobody else R.string.MembersTalkingNobody), false)
						}
						else {
							subtitleTextView.setText(LocaleController.formatPluralString(if (call.call!!.rtmp_stream) "ViewersWatching" else "Participants", call.call!!.participants_count), false)
						}
					}

					updateAvatars(true)
				}
			}

			NotificationCenter.historyImportProgressChanged -> {
				if (currentStyle == STYLE_CONNECTING_GROUP_CALL || currentStyle == STYLE_ACTIVE_GROUP_CALL || currentStyle == STYLE_INACTIVE_GROUP_CALL) {
					checkCall(false)
				}

				checkImport(false)
			}

			NotificationCenter.messagePlayingSpeedChanged -> {
				updatePlaybackButton()
			}

			NotificationCenter.webRtcMicAmplitudeEvent -> {
				micAmplitude = if (VoIPService.sharedInstance == null || VoIPService.sharedInstance?.isMicMute() == true) {
					0f
				}
				else {
					(min(GroupCallActivity.MAX_AMPLITUDE.toDouble(), ((args[0] as Float) * 4000).toDouble()) / GroupCallActivity.MAX_AMPLITUDE).toFloat()
				}

				if (VoIPService.sharedInstance != null) {
					Theme.getFragmentContextViewWavesDrawable().setAmplitude(max(speakerAmplitude.toDouble(), micAmplitude.toDouble()).toFloat())
				}
			}

			NotificationCenter.webRtcSpeakerAmplitudeEvent -> {
				val a = args[0] as Float * 15f / 80f

				speakerAmplitude = max(0.0, min(a.toDouble(), 1.0)).toFloat()

				if (VoIPService.sharedInstance == null || VoIPService.sharedInstance?.isMicMute() == true) {
					micAmplitude = 0f
				}

				if (VoIPService.sharedInstance != null) {
					Theme.getFragmentContextViewWavesDrawable().setAmplitude(max(speakerAmplitude.toDouble(), micAmplitude.toDouble()).toFloat())
				}

				avatars.invalidate()
			}
		}
	}

	val styleHeight: Int
		get() = if (currentStyle == STYLE_INACTIVE_GROUP_CALL) 48 else 36

	val isCallTypeVisible: Boolean
		get() = (currentStyle == STYLE_CONNECTING_GROUP_CALL || currentStyle == STYLE_ACTIVE_GROUP_CALL) && visible

	private fun checkLiveLocation(create: Boolean) {
		@Suppress("NAME_SHADOWING") var create = create
		val fragmentView = fragment.fragmentView

		if (!create && fragmentView != null) {
			if ((fragmentView.parent as? View)?.visibility != VISIBLE) {
				create = true
			}
		}

		val show = if (fragment is DialogsActivity) {
			LocationController.locationsCount != 0
		}
		else {
			LocationController.getInstance(fragment.currentAccount).isSharingLocation((fragment as ChatActivity?)!!.dialogId)
		}
		if (!show) {
			lastLocationSharingCount = -1

			AndroidUtilities.cancelRunOnUIThread(checkLocationRunnable)

			if (visible) {
				visible = false

				if (create) {
					if (visibility != GONE) {
						visibility = GONE
					}

					setTopPadding(0f)
				}
				else {
					animatorSet?.cancel()
					animatorSet = null

					animatorSet = AnimatorSet()
					animatorSet?.playTogether(ObjectAnimator.ofFloat(this, "topPadding", 0f))
					animatorSet?.setDuration(200)

					animatorSet?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							if (animatorSet != null && animatorSet == animation) {
								visibility = GONE
								animatorSet = null
							}
						}
					})

					animatorSet?.start()
				}
			}
		}
		else {
			updateStyle(STYLE_LIVE_LOCATION)

			playButton.setImageDrawable(ShareLocationDrawable(context, 1))

			if (create && topPadding == 0f) {
				setTopPadding(AndroidUtilities.dp2(styleHeight.toFloat()).toFloat())
			}

			if (!visible) {
				if (!create) {
					animatorSet?.cancel()
					animatorSet = null

					animatorSet = AnimatorSet()
					animatorSet?.playTogether(ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(styleHeight.toFloat()).toFloat()))
					animatorSet?.setDuration(200)

					animatorSet?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							if (animatorSet != null && animatorSet == animation) {
								animatorSet = null
							}
						}
					})

					animatorSet?.start()
				}

				visible = true
				visibility = VISIBLE
			}

			if (fragment is DialogsActivity) {
				val liveLocation = context.getString(R.string.LiveLocationContext)
				val param: String
				val str: String
				val infos = ArrayList<SharingLocationInfo>()

				for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
					infos.addAll(LocationController.getInstance(a).sharingLocationsUI)
				}

				if (infos.size == 1) {
					val info = infos[0]
					val dialogId = info.messageObject?.dialogId ?: 0L

					if (DialogObject.isUserDialog(dialogId)) {
						val user = MessagesController.getInstance(info.messageObject!!.currentAccount).getUser(dialogId)
						param = UserObject.getFirstName(user)
						str = context.getString(R.string.AttachLiveLocationIsSharing)
					}
					else {
						val chat = MessagesController.getInstance(info.messageObject!!.currentAccount).getChat(-dialogId)
						param = chat?.title ?: ""
						str = context.getString(R.string.AttachLiveLocationIsSharingChat)
					}
				}
				else {
					param = LocaleController.formatPluralString("Chats", infos.size)
					str = context.getString(R.string.AttachLiveLocationIsSharingChats)
				}

				val fullString = String.format(str, liveLocation, param)
				val start = fullString.indexOf(liveLocation)
				val stringBuilder = SpannableStringBuilder(fullString)

				for (i in 0..1) {
					val textView = if (i == 0) titleTextView.textView else titleTextView.nextTextView

					if (textView == null) {
						continue
					}

					textView.ellipsize = TextUtils.TruncateAt.END
				}

				val span = TypefaceSpan(Theme.TYPEFACE_BOLD, 0, ResourcesCompat.getColor(context.resources, R.color.undead_dark, null))

				stringBuilder.setSpan(span, start, start + liveLocation.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

				titleTextView.setText(stringBuilder, false)
			}
			else {
				checkLocationRunnable.run()
				checkLocationString()
			}
		}
	}

	private fun checkLocationString() {
		if (fragment !is ChatActivity) {
			return
		}

		val chatActivity = fragment
		val dialogId = chatActivity.dialogId
		val currentAccount = chatActivity.currentAccount
		val messages = LocationController.getInstance(currentAccount).locationsCache[dialogId]

		if (!firstLocationsLoaded) {
			LocationController.getInstance(currentAccount).loadLiveLocations(dialogId)
			firstLocationsLoaded = true
		}

		var locationSharingCount = 0
		var notYouUser: User? = null

		if (messages != null) {
			val currentUserId = UserConfig.getInstance(currentAccount).getClientUserId()
			val date = ConnectionsManager.getInstance(currentAccount).currentTime

			for (a in messages.indices) {
				val message = messages[a]

				if (message.media == null) {
					continue
				}

				if (message.date + message.media!!.period > date) {
					val fromId = MessageObject.getFromChatId(message)

					if (notYouUser == null && fromId != currentUserId) {
						notYouUser = MessagesController.getInstance(currentAccount).getUser(fromId)
					}

					locationSharingCount++
				}
			}
		}

		if (lastLocationSharingCount == locationSharingCount) {
			return
		}

		lastLocationSharingCount = locationSharingCount

		val liveLocation = context.getString(R.string.LiveLocationContext)
		val fullString: String

		if (locationSharingCount == 0) {
			fullString = liveLocation
		}
		else {
			val otherSharingCount = locationSharingCount - 1

			fullString = if (LocationController.getInstance(currentAccount).isSharingLocation(dialogId)) {
				if (otherSharingCount != 0) {
					if (otherSharingCount == 1 && notYouUser != null) {
						String.format("%1\$s - %2\$s", liveLocation, LocaleController.formatString("SharingYouAndOtherName", R.string.SharingYouAndOtherName, UserObject.getFirstName(notYouUser)))
					}
					else {
						String.format("%1\$s - %2\$s %3\$s", liveLocation, context.getString(R.string.ChatYourSelfName), LocaleController.formatPluralString("AndOther", otherSharingCount))
					}
				}
				else {
					String.format("%1\$s - %2\$s", liveLocation, context.getString(R.string.ChatYourSelfName))
				}
			}
			else {
				if (otherSharingCount != 0) {
					String.format("%1\$s - %2\$s %3\$s", liveLocation, UserObject.getFirstName(notYouUser), LocaleController.formatPluralString("AndOther", otherSharingCount))
				}
				else {
					String.format("%1\$s - %2\$s", liveLocation, UserObject.getFirstName(notYouUser))
				}
			}
		}

		if (fullString == lastString) {
			return
		}

		lastString = fullString

		val start = fullString.indexOf(liveLocation)
		val stringBuilder = SpannableStringBuilder(fullString)

		for (i in 0..1) {
			val textView = if (i == 0) titleTextView.textView else titleTextView.nextTextView

			if (textView == null) {
				continue
			}

			textView.ellipsize = TextUtils.TruncateAt.END
		}

		if (start >= 0) {
			val span = TypefaceSpan(Theme.TYPEFACE_BOLD, 0, ResourcesCompat.getColor(context.resources, R.color.undead_dark, null))
			stringBuilder.setSpan(span, start, start + liveLocation.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
		}

		titleTextView.setText(stringBuilder, false)
	}

	private fun checkPlayer(create: Boolean) {
		@Suppress("NAME_SHADOWING") var create = create

		if (visible && (currentStyle == STYLE_CONNECTING_GROUP_CALL || currentStyle == STYLE_ACTIVE_GROUP_CALL || (currentStyle == STYLE_INACTIVE_GROUP_CALL || currentStyle == STYLE_IMPORTING_MESSAGES) && !isPlayingVoice)) {
			return
		}

		val messageObject = MediaController.getInstance().playingMessageObject
		val fragmentView = fragment.fragmentView

		if (!create && fragmentView != null) {
			if ((fragmentView.parent as? View)?.visibility != VISIBLE) {
				create = true
			}
		}

		val wasVisible = visible

		if (messageObject == null || messageObject.id == 0 || messageObject.isVideo) {
			lastMessageObject = null

			var callAvailable = supportsCalls && VoIPService.sharedInstance != null && !VoIPService.sharedInstance!!.isHangingUp() && VoIPService.sharedInstance!!.getCallState() != VoIPService.STATE_WAITING_INCOMING && !GroupCallPip.isShowing()

			if (!isPlayingVoice && !callAvailable && fragment is ChatActivity && !GroupCallPip.isShowing()) {
				val call = fragment.getGroupCall()
				callAvailable = call != null && call.shouldShowPanel()
			}

			if (callAvailable) {
				checkCall(false)
				return
			}

			if (visible) {
				if (playbackSpeedButton?.isSubMenuShowing == true) {
					playbackSpeedButton?.toggleSubMenu()
				}

				visible = false

				if (create) {
					if (visibility != GONE) {
						visibility = GONE
					}

					setTopPadding(0f)
				}
				else {
					animatorSet?.cancel()
					animatorSet = null

					animationIndex = NotificationCenter.getInstance(account).setAnimationInProgress(animationIndex, null)

					animatorSet = AnimatorSet()
					animatorSet?.playTogether(ObjectAnimator.ofFloat(this, "topPadding", 0f))
					animatorSet?.setDuration(200)

					delegate?.onAnimation(start = true, show = false)

					animatorSet?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							NotificationCenter.getInstance(account).onAnimationFinish(animationIndex)

							if (animatorSet != null && animatorSet == animation) {
								visibility = GONE

								delegate?.onAnimation(start = false, show = false)

								animatorSet = null

								if (checkCallAfterAnimation) {
									checkCall(false)
								}
								else if (checkPlayerAfterAnimation) {
									checkPlayer(false)
								}
								else if (checkImportAfterAnimation) {
									checkImport(false)
								}

								checkCallAfterAnimation = false
								checkPlayerAfterAnimation = false
								checkImportAfterAnimation = false
							}
						}
					})

					animatorSet?.start()
				}
			}
			else {
				visibility = GONE
			}
		}
		else {
			if (currentStyle != STYLE_AUDIO_PLAYER && animatorSet != null && !create) {
				checkPlayerAfterAnimation = true
				return
			}

			val prevStyle = currentStyle

			updateStyle(STYLE_AUDIO_PLAYER)

			if (create && topPadding == 0f) {
				updatePaddings()

				setTopPadding(AndroidUtilities.dp2(styleHeight.toFloat()).toFloat())

				delegate?.onAnimation(start = true, show = true)
				delegate?.onAnimation(start = false, show = true)
			}

			if (!visible) {
				if (!create) {
					animatorSet?.cancel()
					animatorSet = null

					animationIndex = NotificationCenter.getInstance(account).setAnimationInProgress(animationIndex, null)

					animatorSet = AnimatorSet()

					if (additionalContextView != null && additionalContextView!!.visibility == VISIBLE) {
						(layoutParams as LayoutParams).topMargin = -AndroidUtilities.dp((styleHeight + additionalContextView!!.styleHeight).toFloat())
					}
					else {
						(layoutParams as LayoutParams).topMargin = -AndroidUtilities.dp(styleHeight.toFloat())
					}

					delegate?.onAnimation(start = true, show = true)

					animatorSet?.playTogether(ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(styleHeight.toFloat()).toFloat()))
					animatorSet?.setDuration(200)

					animatorSet?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							NotificationCenter.getInstance(account).onAnimationFinish(animationIndex)

							if (animatorSet != null && animatorSet == animation) {
								delegate?.onAnimation(start = false, show = true)

								animatorSet = null

								if (checkCallAfterAnimation) {
									checkCall(false)
								}
								else if (checkPlayerAfterAnimation) {
									checkPlayer(false)
								}
								else if (checkImportAfterAnimation) {
									checkImport(false)
								}

								checkCallAfterAnimation = false
								checkPlayerAfterAnimation = false
								checkImportAfterAnimation = false
							}
						}
					})

					animatorSet?.start()
				}

				visible = true
				visibility = VISIBLE
			}

			if (MediaController.getInstance().isMessagePaused) {
				playPauseDrawable?.setPause(false, !create)
				playButton.contentDescription = context.getString(R.string.AccActionPlay)
			}
			else {
				playPauseDrawable?.setPause(true, !create)
				playButton.contentDescription = context.getString(R.string.AccActionPause)
			}

			if (lastMessageObject !== messageObject || prevStyle != STYLE_AUDIO_PLAYER) {
				lastMessageObject = messageObject

				val stringBuilder: SpannableStringBuilder

				if (lastMessageObject!!.isVoice || lastMessageObject!!.isRoundVideo) {
					isMusic = false

					playbackSpeedButton?.alpha = 1.0f
					playbackSpeedButton?.isEnabled = true

					titleTextView.setPadding(0, 0, AndroidUtilities.dp(44f), 0)

					stringBuilder = SpannableStringBuilder(String.format("%s %s", messageObject.musicAuthor, messageObject.musicTitle))

					for (i in 0..1) {
						val textView = if (i == 0) titleTextView.textView else titleTextView.nextTextView

						if (textView == null) {
							continue
						}

						textView.ellipsize = TextUtils.TruncateAt.MIDDLE
					}

					updatePlaybackButton()
				}
				else {
					isMusic = true

					if (playbackSpeedButton != null) {
						if (messageObject.duration >= 10 * 60) {
							playbackSpeedButton?.alpha = 1.0f
							playbackSpeedButton?.isEnabled = true
							titleTextView.setPadding(0, 0, AndroidUtilities.dp(44f), 0)
							updatePlaybackButton()
						}
						else {
							playbackSpeedButton?.alpha = 0.0f
							playbackSpeedButton?.isEnabled = false
							titleTextView.setPadding(0, 0, 0, 0)
						}
					}
					else {
						titleTextView.setPadding(0, 0, 0, 0)
					}

					stringBuilder = SpannableStringBuilder(String.format("%s - %s", messageObject.musicAuthor, messageObject.musicTitle))

					for (i in 0..1) {
						val textView = if (i == 0) titleTextView.textView else titleTextView.nextTextView

						if (textView == null) {
							continue
						}

						textView.ellipsize = TextUtils.TruncateAt.END
					}
				}

				val span = TypefaceSpan(Theme.TYPEFACE_BOLD, 0, ResourcesCompat.getColor(context.resources, R.color.undead_dark, null))

				stringBuilder.setSpan(span, 0, messageObject.musicAuthor!!.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

				titleTextView.setText(stringBuilder, !create && wasVisible && isMusic)
			}
		}
	}

	fun checkImport(create: Boolean) {
		@Suppress("NAME_SHADOWING") var create = create

		if (fragment !is ChatActivity || visible && (currentStyle == STYLE_CONNECTING_GROUP_CALL || currentStyle == STYLE_ACTIVE_GROUP_CALL)) {
			return
		}

		val chatActivity = fragment
		var importingHistory = chatActivity.sendMessagesHelper.getImportingHistory(chatActivity.dialogId)
		val fragmentView = fragment.getFragmentView()

		if (!create && fragmentView != null) {
			if ((fragmentView.parent as? View)?.visibility != VISIBLE) {
				create = true
			}
		}

		val dialog = chatActivity.visibleDialog

		if (((isPlayingVoice || chatActivity.shouldShowImport()) || (dialog is ImportingAlert) && !dialog.isDismissed) && importingHistory != null) {
			importingHistory = null
		}

		if (importingHistory == null) {
			if (visible && (create && currentStyle == STYLE_NOT_SET || currentStyle == STYLE_IMPORTING_MESSAGES)) {
				visible = false

				if (create) {
					if (visibility != GONE) {
						visibility = GONE
					}

					setTopPadding(0f)
				}
				else {
					animatorSet?.cancel()
					animatorSet = null

					val currentAccount = account

					animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null)

					animatorSet = AnimatorSet()
					animatorSet?.playTogether(ObjectAnimator.ofFloat(this, "topPadding", 0f))
					animatorSet?.setDuration(220)
					animatorSet?.interpolator = CubicBezierInterpolator.DEFAULT

					animatorSet?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex)

							if (animatorSet != null && animatorSet == animation) {
								visibility = GONE
								animatorSet = null

								if (checkCallAfterAnimation) {
									checkCall(false)
								}
								else if (checkPlayerAfterAnimation) {
									checkPlayer(false)
								}
								else if (checkImportAfterAnimation) {
									checkImport(false)
								}

								checkCallAfterAnimation = false
								checkPlayerAfterAnimation = false
								checkImportAfterAnimation = false
							}
						}
					})

					animatorSet?.start()
				}
			}
			else if (currentStyle == STYLE_NOT_SET || currentStyle == STYLE_IMPORTING_MESSAGES) {
				visible = false
				visibility = GONE
			}
		}
		else {
			if (currentStyle != STYLE_IMPORTING_MESSAGES && animatorSet != null && !create) {
				checkImportAfterAnimation = true
				return
			}

			updateStyle(STYLE_IMPORTING_MESSAGES)

			if (create && topPadding == 0f) {
				updatePaddings()

				setTopPadding(AndroidUtilities.dp2(styleHeight.toFloat()).toFloat())

				delegate?.onAnimation(start = true, show = true)
				delegate?.onAnimation(start = false, show = true)
			}
			if (!visible) {
				if (!create) {
					animatorSet?.cancel()
					animatorSet = null

					animationIndex = NotificationCenter.getInstance(account).setAnimationInProgress(animationIndex, null)
					animatorSet = AnimatorSet()

					if (additionalContextView?.visibility == VISIBLE) {
						(layoutParams as LayoutParams).topMargin = -AndroidUtilities.dp((styleHeight + additionalContextView!!.styleHeight).toFloat())
					}
					else {
						(layoutParams as LayoutParams).topMargin = -AndroidUtilities.dp(styleHeight.toFloat())
					}

					delegate?.onAnimation(start = true, show = true)

					animatorSet?.playTogether(ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(styleHeight.toFloat()).toFloat()))
					animatorSet?.setDuration(200)

					animatorSet?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							NotificationCenter.getInstance(account).onAnimationFinish(animationIndex)

							if (animatorSet != null && animatorSet == animation) {
								delegate?.onAnimation(start = false, show = true)

								animatorSet = null

								if (checkCallAfterAnimation) {
									checkCall(false)
								}
								else if (checkPlayerAfterAnimation) {
									checkPlayer(false)
								}
								else if (checkImportAfterAnimation) {
									checkImport(false)
								}

								checkCallAfterAnimation = false
								checkPlayerAfterAnimation = false
								checkImportAfterAnimation = false
							}
						}
					})

					animatorSet?.start()
				}

				visible = true
				visibility = VISIBLE
			}

			if (currentProgress != importingHistory.uploadProgress) {
				currentProgress = importingHistory.uploadProgress
				titleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ImportUploading", R.string.ImportUploading, importingHistory.uploadProgress)), false)
			}
		}
	}

	private val isPlayingVoice: Boolean
		get() = MediaController.getInstance().playingMessageObject?.isVoice == true

	fun checkCall(create: Boolean) {
		@Suppress("NAME_SHADOWING") var create = create
		val voIPService = VoIPService.sharedInstance

		if (visible && currentStyle == STYLE_IMPORTING_MESSAGES && (voIPService == null || voIPService.isHangingUp())) {
			return
		}

		val fragmentView = fragment.fragmentView

		if (!create && fragmentView != null) {
			if ((fragmentView.parent as? View)?.visibility != VISIBLE) {
				create = true
			}
		}

		var callAvailable: Boolean
		var groupActive: Boolean

		if (GroupCallPip.isShowing()) {
			callAvailable = false
			groupActive = false
		}
		else {
			callAvailable = !GroupCallActivity.groupCallUiVisible && supportsCalls && voIPService != null && !voIPService.isHangingUp()

			if (voIPService?.groupCall?.call is TL_groupCallDiscarded) {
				callAvailable = false
			}

			groupActive = false

			if (!isPlayingVoice && !GroupCallActivity.groupCallUiVisible && supportsCalls && !callAvailable && fragment is ChatActivity) {
				val call = fragment.getGroupCall()

				if (call != null && call.shouldShowPanel()) {
					callAvailable = true
					groupActive = true
				}
			}
		}

		if (!callAvailable) {
			if (visible && (create && currentStyle == STYLE_NOT_SET || currentStyle == STYLE_INACTIVE_GROUP_CALL || currentStyle == STYLE_ACTIVE_GROUP_CALL || currentStyle == STYLE_CONNECTING_GROUP_CALL)) {
				visible = false

				if (create) {
					if (visibility != GONE) {
						visibility = GONE
					}

					setTopPadding(0f)
				}
				else {
					animatorSet?.cancel()
					animatorSet = null

					val currentAccount = account

					animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null)

					animatorSet = AnimatorSet()
					animatorSet?.playTogether(ObjectAnimator.ofFloat(this, "topPadding", 0f))
					animatorSet?.setDuration(220)
					animatorSet?.interpolator = CubicBezierInterpolator.DEFAULT

					animatorSet?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex)

							if (animatorSet != null && animatorSet == animation) {
								visibility = GONE
								animatorSet = null

								if (checkCallAfterAnimation) {
									checkCall(false)
								}
								else if (checkPlayerAfterAnimation) {
									checkPlayer(false)
								}
								else if (checkImportAfterAnimation) {
									checkImport(false)
								}

								checkCallAfterAnimation = false
								checkPlayerAfterAnimation = false
								checkImportAfterAnimation = false
							}
						}
					})
					animatorSet!!.start()
				}
			}
			else if (visible && (currentStyle == STYLE_NOT_SET || currentStyle == STYLE_INACTIVE_GROUP_CALL || currentStyle == STYLE_ACTIVE_GROUP_CALL || currentStyle == STYLE_CONNECTING_GROUP_CALL)) {
				visible = false
				visibility = GONE
			}

			if (create && fragment is ChatActivity && fragment.openedWithLivestream() && !GroupCallPip.isShowing()) {
				BulletinFactory.of(fragment).createSimpleBulletin(R.raw.linkbroken, context.getString(R.string.InviteExpired)).show()
			}
		}
		else {
			val newStyle = if (groupActive) {
				STYLE_INACTIVE_GROUP_CALL
			}
			else if (voIPService?.groupCall != null) {
				STYLE_ACTIVE_GROUP_CALL
			}
			else {
				STYLE_CONNECTING_GROUP_CALL
			}

			if (newStyle != currentStyle && animatorSet != null && !create) {
				checkCallAfterAnimation = true
				return
			}

			if (newStyle != currentStyle && visible && !create) {
				animatorSet?.cancel()
				animatorSet = null

				val currentAccount = account

				animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null)

				animatorSet = AnimatorSet()
				animatorSet?.playTogether(ObjectAnimator.ofFloat(this, "topPadding", 0f))
				animatorSet?.setDuration(220)
				animatorSet?.interpolator = CubicBezierInterpolator.DEFAULT

				animatorSet?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex)
						if (animatorSet != null && animatorSet == animation) {
							visible = false
							animatorSet = null
							checkCall(false)
						}
					}
				})

				animatorSet?.start()

				return
			}

			if (groupActive) {
				val updateAnimated = currentStyle == STYLE_INACTIVE_GROUP_CALL && visible

				updateStyle(STYLE_INACTIVE_GROUP_CALL)

				val call = (fragment as? ChatActivity)?.getGroupCall()
				val chat = (fragment as? ChatActivity)?.currentChat

				if (call?.isScheduled == true) {
					if (gradientPaint == null) {
						gradientTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
						gradientTextPaint?.color = -0x1
						gradientTextPaint?.textSize = AndroidUtilities.dp(14f).toFloat()
						gradientTextPaint?.setTypeface(Theme.TYPEFACE_BOLD)

						gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
						gradientPaint?.color = -0x1

						matrix = Matrix()
					}

					joinButton.visibility = GONE

					if (!call.call?.title.isNullOrEmpty()) {
						titleTextView.setText(call.call?.title, false)
					}
					else {
						if (ChatObject.isChannelOrGiga(chat)) {
							titleTextView.setText(context.getString(R.string.VoipChannelScheduledVoiceChat), false)
						}
						else {
							titleTextView.setText(context.getString(R.string.VoipGroupScheduledVoiceChat), false)
						}
					}

					subtitleTextView.setText(LocaleController.formatStartsTime(call.call!!.schedule_date.toLong(), 4), false)

					if (!scheduleRunnableScheduled) {
						scheduleRunnableScheduled = true
						updateScheduleTimeRunnable.run()
					}
				}
				else {
					timeLayout = null
					joinButton.visibility = VISIBLE

					if (call?.call?.rtmp_stream == true) {
						titleTextView.setText(context.getString(R.string.VoipChannelVoiceChat), false)
					}
					else if (ChatObject.isChannelOrGiga(chat)) {
						titleTextView.setText(context.getString(R.string.VoipChannelVoiceChat), false)
					}
					else {
						titleTextView.setText(context.getString(R.string.VoipGroupVoiceChat), false)
					}

					if (call?.call?.participants_count == 0) {
						subtitleTextView.setText(context.getString(if (call.call?.rtmp_stream == true) R.string.ViewersWatchingNobody else R.string.MembersTalkingNobody), false)
					}
					else {
						subtitleTextView.setText(LocaleController.formatPluralString(if (call?.call?.rtmp_stream == true) "ViewersWatching" else "Participants", call?.call?.participants_count ?: 0), false)
					}

					frameLayout.invalidate()
				}

				updateAvatars(avatars.avatarsDrawable.wasDraw && updateAnimated)
			}
			else {
				if (voIPService?.groupCall != null) {
					updateAvatars(currentStyle == STYLE_ACTIVE_GROUP_CALL)
					updateStyle(STYLE_ACTIVE_GROUP_CALL)
				}
				else {
					updateAvatars(currentStyle == STYLE_CONNECTING_GROUP_CALL)
					updateStyle(STYLE_CONNECTING_GROUP_CALL)
				}
			}

			if (!visible) {
				if (!create) {
					animatorSet?.cancel()
					animatorSet = null

					animatorSet = AnimatorSet()

					if (additionalContextView != null && additionalContextView!!.visibility == VISIBLE) {
						(layoutParams as LayoutParams).topMargin = -AndroidUtilities.dp((styleHeight + additionalContextView!!.styleHeight).toFloat())
					}
					else {
						(layoutParams as LayoutParams).topMargin = -AndroidUtilities.dp(styleHeight.toFloat())
					}

					val currentAccount = account

					animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, intArrayOf(NotificationCenter.messagesDidLoad))

					animatorSet?.playTogether(ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(styleHeight.toFloat()).toFloat()))
					animatorSet?.setDuration(220)
					animatorSet?.interpolator = CubicBezierInterpolator.DEFAULT

					animatorSet?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex)

							if (animatorSet != null && animatorSet == animation) {
								animatorSet = null
							}

							if (checkCallAfterAnimation) {
								checkCall(false)
							}
							else if (checkPlayerAfterAnimation) {
								checkPlayer(false)
							}
							else if (checkImportAfterAnimation) {
								checkImport(false)
							}

							checkCallAfterAnimation = false
							checkPlayerAfterAnimation = false
							checkImportAfterAnimation = false

							startJoinFlickerAnimation()
						}
					})

					animatorSet?.start()
				}
				else {
					updatePaddings()
					setTopPadding(AndroidUtilities.dp2(styleHeight.toFloat()).toFloat())
					startJoinFlickerAnimation()
				}

				visible = true
				visibility = VISIBLE
			}
		}
	}

	private fun startJoinFlickerAnimation() {
		if (joinButtonFlicker.getProgress() > 1) {
			AndroidUtilities.runOnUIThread({
				joinButtonFlicker.setProgress(0f)
				joinButton.invalidate()
			}, 150)
		}
	}

	private fun updateAvatars(animated: Boolean) {
		if (!animated) {
			avatars.avatarsDrawable.transitionProgressAnimator?.cancel()
			avatars.avatarsDrawable.transitionProgressAnimator = null
		}

		val call: ChatObject.Call?
		val userCall: User?

		if (avatars.avatarsDrawable.transitionProgressAnimator == null) {
			val currentAccount: Int

			if (currentStyle == STYLE_INACTIVE_GROUP_CALL) {
				if (fragment is ChatActivity) {
					val chatActivity = fragment
					call = chatActivity.getGroupCall()
					currentAccount = chatActivity.currentAccount
				}
				else {
					call = null
					currentAccount = account
				}

				userCall = null
			}
			else {
				if (VoIPService.sharedInstance != null) {
					call = VoIPService.sharedInstance?.groupCall
					userCall = if (fragment is ChatActivity) null else VoIPService.sharedInstance?.getUser()
					currentAccount = VoIPService.sharedInstance?.getAccount() ?: 0
				}
				else {
					call = null
					userCall = null
					currentAccount = account
				}
			}

			if (call != null) {
				var a = 0
				val n = call.sortedParticipants.size

				while (a < 3) {
					if (a < n) {
						avatars.setObject(a, currentAccount, call.sortedParticipants[a])
					}
					else {
						avatars.setObject(a, currentAccount, null)
					}

					a++
				}
			}
			else if (userCall != null) {
				avatars.setObject(0, currentAccount, userCall)

				for (a in 1..2) {
					avatars.setObject(a, currentAccount, null)
				}
			}
			else {
				for (a in 0..2) {
					avatars.setObject(a, currentAccount, null)
				}
			}

			avatars.commitTransition(animated)

			if (currentStyle == STYLE_INACTIVE_GROUP_CALL && call != null) {
				val n = if (call.call!!.rtmp_stream) 0 else min(3.0, call.sortedParticipants.size.toDouble()).toInt()
				val x = if (n == 0) 10 else (10 + 24 * (n - 1) + 32 + 10)

				if (animated) {
					val leftMargin = (titleTextView.layoutParams as? LayoutParams)?.leftMargin ?: 0

					if (AndroidUtilities.dp(x.toFloat()) != leftMargin) {
						val dx = titleTextView.translationX + leftMargin - AndroidUtilities.dp(x.toFloat())
						titleTextView.translationX = dx
						subtitleTextView.translationX = dx
						titleTextView.animate().translationX(0f).setDuration(220).setInterpolator(CubicBezierInterpolator.DEFAULT)
						subtitleTextView.animate().translationX(0f).setDuration(220).setInterpolator(CubicBezierInterpolator.DEFAULT)
					}
				}
				else {
					titleTextView.animate()?.cancel()
					subtitleTextView.animate().cancel()
					titleTextView.translationX = 0f
					subtitleTextView.translationX = 0f
				}

				titleTextView.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, Gravity.LEFT or Gravity.TOP, x.toFloat(), 5f, (if (call.isScheduled) 90 else 36).toFloat(), 0f)
				subtitleTextView.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, Gravity.LEFT or Gravity.TOP, x.toFloat(), 25f, (if (call.isScheduled) 90 else 36).toFloat(), 0f)
			}
		}
		else {
			avatars.updateAfterTransitionEnd()
		}
	}

	fun setCollapseTransition(show: Boolean, extraHeight: Float, progress: Float) {
		collapseTransition = show
		this.extraHeight = extraHeight
		this.collapseProgress = progress
	}

	override fun dispatchDraw(canvas: Canvas) {
		if (drawOverlay && visibility != VISIBLE) {
			return
		}

		var clipped = false

		if (currentStyle == STYLE_ACTIVE_GROUP_CALL || currentStyle == STYLE_CONNECTING_GROUP_CALL) {
			// val mutedByAdmin = GroupCallActivity.groupCallInstance == null && Theme.getFragmentContextViewWavesDrawable().state == FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_MUTED_BY_ADMIN
			Theme.getFragmentContextViewWavesDrawable().updateState(wasDraw)

			val progress = topPadding / AndroidUtilities.dp(styleHeight.toFloat())

			if (collapseTransition) {
				Theme.getFragmentContextViewWavesDrawable().draw(0f, AndroidUtilities.dp(styleHeight.toFloat()) - topPadding + extraHeight, measuredWidth.toFloat(), (measuredHeight - AndroidUtilities.dp(2f)).toFloat(), canvas, null, min(progress.toDouble(), (1f - collapseProgress).toDouble()).toFloat())
			}
			else {
				Theme.getFragmentContextViewWavesDrawable().draw(0f, AndroidUtilities.dp(styleHeight.toFloat()) - topPadding, measuredWidth.toFloat(), (measuredHeight - AndroidUtilities.dp(2f)).toFloat(), canvas, this, progress)
			}

			var clipTop = AndroidUtilities.dp(styleHeight.toFloat()) - topPadding

			if (collapseTransition) {
				clipTop += extraHeight
			}

			if (clipTop > measuredHeight) {
				return
			}

			clipped = true

			canvas.save()
			canvas.clipRect(0f, clipTop, measuredWidth.toFloat(), measuredHeight.toFloat())

			invalidate()
		}

		super.dispatchDraw(canvas)

		if (clipped) {
			canvas.restore()
		}

		wasDraw = true
	}

	fun setDrawOverlay(drawOverlay: Boolean) {
		this.drawOverlay = drawOverlay
	}

	override fun invalidate() {
		super.invalidate()

		if (currentStyle == STYLE_ACTIVE_GROUP_CALL || currentStyle == STYLE_CONNECTING_GROUP_CALL) {
			(parent as? View)?.invalidate()
		}
	}

	val isCallStyle: Boolean
		get() = currentStyle == STYLE_ACTIVE_GROUP_CALL || currentStyle == STYLE_CONNECTING_GROUP_CALL

	override fun setVisibility(visibility: Int) {
		super.setVisibility(visibility)

		updatePaddings()
		setTopPadding(topPadding)

		if (visibility == GONE) {
			wasDraw = false
		}
	}

	private fun updatePaddings() {
		var margin = 0

		if (visibility == VISIBLE) {
			margin -= AndroidUtilities.dp(styleHeight.toFloat())
		}

		if (additionalContextView != null && additionalContextView!!.visibility == VISIBLE) {
			margin -= AndroidUtilities.dp(additionalContextView!!.styleHeight.toFloat())
			(layoutParams as LayoutParams).topMargin = margin
			(additionalContextView!!.layoutParams as LayoutParams).topMargin = margin
		}
		else {
			(layoutParams as LayoutParams).topMargin = margin
		}
	}

	override fun onStateChanged(state: Int) {
		updateCallTitle()
	}

	private fun updateCallTitle() {
		val service = VoIPService.sharedInstance

		if (service != null && (currentStyle == STYLE_CONNECTING_GROUP_CALL || currentStyle == STYLE_ACTIVE_GROUP_CALL)) {
			val currentCallState = service.getCallState()

			if (!service.isSwitchingStream() && (currentCallState == VoIPService.STATE_WAIT_INIT || currentCallState == VoIPService.STATE_WAIT_INIT_ACK || currentCallState == VoIPService.STATE_CREATING || currentCallState == VoIPService.STATE_RECONNECTING)) {
				titleTextView.setText(context.getString(R.string.VoipGroupConnecting), false)
			}
			else if (service.getChat() != null) {
				if (!TextUtils.isEmpty(service.groupCall!!.call!!.title)) {
					titleTextView.setText(service.groupCall!!.call!!.title, false)
				}
				else {
					if ((fragment is ChatActivity && fragment.currentChat != null) && fragment.currentChat!!.id == service.getChat()!!.id) {
						val chat = fragment.currentChat

						if (VoIPService.hasRtmpStream()) {
							titleTextView.setText(context.getString(R.string.VoipChannelViewVoiceChat), false)
						}
						else {
							if (ChatObject.isChannelOrGiga(chat)) {
								titleTextView.setText(context.getString(R.string.VoipChannelViewVoiceChat), false)
							}
							else {
								titleTextView.setText(context.getString(R.string.VoipGroupViewVoiceChat), false)
							}
						}
					}
					else {
						titleTextView.setText(service.getChat()?.title, false)
					}
				}
			}
			else if (service.getUser() != null) {
				val user = service.getUser()

				if ((fragment is ChatActivity && fragment.currentUser != null) && fragment.currentUser!!.id == user!!.id) {
					titleTextView.setText(context.getString(R.string.ReturnToCall))
				}
				else {
					titleTextView.setText(ContactsController.formatName(user!!.first_name, user.last_name))
				}
			}
		}
	}

//	private val titleTextColor: Int
//		get() {
//			if (currentStyle == STYLE_INACTIVE_GROUP_CALL) {
//				return ResourcesCompat.getColor(context.resources, R.color.undead_dark, null)
//			}
//			else if (currentStyle == STYLE_CONNECTING_GROUP_CALL || currentStyle == STYLE_ACTIVE_GROUP_CALL) {
//				return ResourcesCompat.getColor(context.resources, R.color.white, null)
//			}
//
//			return ResourcesCompat.getColor(context.resources, R.color.text, null)
//		}

	@Retention(AnnotationRetention.SOURCE)
	@IntDef(STYLE_NOT_SET, STYLE_AUDIO_PLAYER, STYLE_CONNECTING_GROUP_CALL, STYLE_LIVE_LOCATION, STYLE_ACTIVE_GROUP_CALL, STYLE_INACTIVE_GROUP_CALL, STYLE_IMPORTING_MESSAGES)
	annotation class Style

	fun interface FragmentContextViewDelegate {
		fun onAnimation(start: Boolean, show: Boolean)
	}

	companion object {
		const val STYLE_NOT_SET: Int = -1
		const val STYLE_AUDIO_PLAYER: Int = 0
		const val STYLE_CONNECTING_GROUP_CALL: Int = 1
		const val STYLE_LIVE_LOCATION: Int = 2
		const val STYLE_ACTIVE_GROUP_CALL: Int = 3
		const val STYLE_INACTIVE_GROUP_CALL: Int = 4
		const val STYLE_IMPORTING_MESSAGES: Int = 5
		private const val MENU_SPEED_SLOW = 1
		private const val MENU_SPEED_NORMAL = 2
		private const val MENU_SPEED_FAST = 3
		private const val MENU_SPEED_VERY_FAST = 4
	}
}
