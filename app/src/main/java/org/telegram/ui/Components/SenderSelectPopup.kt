/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject.isChannel
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.Peer
import org.telegram.tgnet.channelId
import org.telegram.tgnet.chatId
import org.telegram.tgnet.userId
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.PremiumPreviewFragment
import kotlin.math.min

open class SenderSelectPopup @SuppressLint("WrongConstant") constructor(context: Context, parentFragment: ChatActivity?, messagesController: MessagesController, private val chatFull: ChatFull, private val sendAsPeers: TLRPC.TLChannelsSendAsPeers?, selectCallback: OnSelectCallback) : ActionBarPopupWindow(context) {
	var dimView: View
	var recyclerContainer: LinearLayout
	var headerText: TextView
	protected var runningCustomSprings = false
	private val scrimPopupContainerLayout = BackButtonFrameLayout(context)
	private val headerShadow = View(context)
	private val recyclerView: RecyclerListView
	private val layoutManager: LinearLayoutManager
	private var isHeaderShadowVisible: Boolean? = null
	private var clicked = false
	protected val springAnimations = mutableListOf<SpringAnimation>()
	private var dismissed = false
	private var bulletinContainer: FrameLayout? = null
	private var bulletinHideCallback: Runnable? = null
	private var isDismissingByBulletin = false
	private var popupX = 0
	private var popupY = 0
	private val bulletins = mutableListOf<Bulletin>()

	init {
		scrimPopupContainerLayout.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat()))

		setContentView(scrimPopupContainerLayout)

		width = WindowManager.LayoutParams.WRAP_CONTENT
		height = WindowManager.LayoutParams.WRAP_CONTENT

		setBackgroundDrawable(null)

		var shadowDrawable = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert)?.mutate()
		shadowDrawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.background), PorterDuff.Mode.MULTIPLY)

		scrimPopupContainerLayout.background = shadowDrawable

		val padding = Rect()

		shadowDrawable?.getPadding(padding)

		scrimPopupContainerLayout.setPadding(padding.left, padding.top, padding.right, padding.bottom)

		dimView = View(context)
		dimView.setBackgroundColor(0x33000000)

		val maxHeight = AndroidUtilities.dp(450f)
		val maxWidth = ((parentFragment?.contentView?.width ?: 0) * 0.75f).toInt()

		recyclerContainer = object : LinearLayout(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(MeasureSpec.makeMeasureSpec(min(MeasureSpec.getSize(widthMeasureSpec).toDouble(), maxWidth.toDouble()).toInt(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(min(MeasureSpec.getSize(heightMeasureSpec).toDouble(), maxHeight.toDouble()).toInt(), MeasureSpec.getMode(heightMeasureSpec)))
			}

			override fun getSuggestedMinimumWidth(): Int {
				return AndroidUtilities.dp(260f)
			}
		}

		recyclerContainer.orientation = LinearLayout.VERTICAL

		headerText = TextView(context)
		headerText.setTextColor(context.getColor(R.color.brand))
		headerText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		headerText.text = context.getString(R.string.SendMessageAsTitle)
		headerText.setTypeface(Theme.TYPEFACE_BOLD, Typeface.BOLD)

		val dp = AndroidUtilities.dp(18f)

		headerText.setPadding(dp, AndroidUtilities.dp(12f), dp, AndroidUtilities.dp(12f))

		recyclerContainer.addView(headerText)

		val recyclerFrameLayout = FrameLayout(context)
		val peers = sendAsPeers?.peers ?: listOf()

		layoutManager = LinearLayoutManager(context)

		recyclerView = RecyclerListView(context)
		recyclerView.setLayoutManager(layoutManager)

		recyclerView.setAdapter(object : SelectionAdapter() {
			override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
				return true
			}

			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
				return RecyclerListView.Holder(SenderView(parent.context))
			}

			override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
				val senderView = holder.itemView as SenderView
				val peerObj = peers[position]
				val peer = peerObj.peer
				var peerId: Long = 0

				if (peer.channelId != 0L) {
					peerId = -peer.channelId
				}

				if (peerId == 0L && peer.userId != 0L) {
					peerId = peer.userId
				}

				if (peerId < 0) {
					val chat = messagesController.getChat(-peerId)

					if (chat != null) {
						if (peerObj.premiumRequired) {
							val str = SpannableString(TextUtils.ellipsize(chat.title, senderView.title.paint, (maxWidth - AndroidUtilities.dp(100f)).toFloat(), TextUtils.TruncateAt.END).toString() + " d")

							val span = ColoredImageSpan(R.drawable.msg_mini_premiumlock)
							span.setTopOffset(1)
							span.setSize(AndroidUtilities.dp(14f))
							span.setColorKey(Theme.key_windowBackgroundWhiteGrayText5)

							str.setSpan(span, str.length - 1, str.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

							senderView.title.ellipsize = null
							senderView.title.text = str
						}
						else {
							senderView.title.ellipsize = TextUtils.TruncateAt.END
							senderView.title.text = chat.title
						}

						senderView.subtitle.text = LocaleController.formatPluralString(if (isChannel(chat) && !chat.megagroup) "Subscribers" else "Members", chat.participantsCount)
						senderView.avatar.setAvatar(chat)
					}

					senderView.avatar.setSelected(if (chatFull.defaultSendAs != null) chatFull.defaultSendAs.channelId == peer.channelId else position == 0, false)
				}
				else {
					val user = messagesController.getUser(peerId)

					if (user != null) {
						senderView.title.text = UserObject.getUserName(user)
						senderView.subtitle.text = context.getString(R.string.VoipGroupPersonalAccount)
						senderView.avatar.setAvatar(user)
					}

					senderView.avatar.setSelected(if (chatFull.defaultSendAs != null) chatFull.defaultSendAs.userId == peer.userId else position == 0, false)
				}
			}

			override fun getItemCount(): Int {
				return peers.size
			}
		})

		recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				val show = layoutManager.findFirstCompletelyVisibleItemPosition() != 0

				if (isHeaderShadowVisible == null || show != isHeaderShadowVisible) {
					headerShadow.animate().cancel()
					headerShadow.animate().alpha((if (show) 1 else 0).toFloat()).setDuration(SHADOW_DURATION.toLong()).start()
					isHeaderShadowVisible = show
				}
			}
		})

		recyclerView.setOnItemClickListener { view, position ->
			val peerObj = peers[position]

			if (clicked) {
				return@setOnItemClickListener
			}

			if (peerObj.premiumRequired && !UserConfig.getInstance(UserConfig.selectedAccount).isPremium) {
				runCatching {
					view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
				}

				val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

				if (bulletinContainer == null) {
					bulletinContainer = object : FrameLayout(context) {
						@SuppressLint("ClickableViewAccessibility")

						override fun onTouchEvent(event: MotionEvent): Boolean {
							val contentView = contentView
							val contentXY = IntArray(2)

							contentView.getLocationInWindow(contentXY)

							contentXY[0] += popupX
							contentXY[1] += popupY

							val viewXY = IntArray(2)

							getLocationInWindow(viewXY)

							if (event.action == MotionEvent.ACTION_DOWN && event.x <= contentXY[0] || event.x >= contentXY[0] + contentView.width || event.y <= contentXY[1] || event.y >= contentXY[1] + contentView.height) {
								if (dismissed || isDismissingByBulletin) {
									return true
								}

								isDismissingByBulletin = true

								startDismissAnimation()

								return true
							}

							event.offsetLocation((viewXY[0] - contentXY[0]).toFloat(), (AndroidUtilities.statusBarHeight + viewXY[1] - contentXY[1]).toFloat())

							return contentView.dispatchTouchEvent(event)
						}
					}
				}

				bulletinHideCallback?.let {
					AndroidUtilities.cancelRunOnUIThread(it)
				}

				if (bulletinContainer?.parent == null) {
					val params = WindowManager.LayoutParams()
					params.height = WindowManager.LayoutParams.MATCH_PARENT
					params.width = params.height
					params.format = PixelFormat.TRANSLUCENT
					params.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW
					params.flags = params.flags or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
						params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
					}

					windowManager.addView(bulletinContainer, params)
				}

				val bulletin = Bulletin.make(bulletinContainer!!, SelectSendAsPremiumHintBulletinLayout(context) {
					parentFragment?.presentFragment(PremiumPreviewFragment("select_sender"))
					dismiss()
				}, Bulletin.DURATION_SHORT)

				bulletin.layout?.addCallback(object : Bulletin.Layout.Callback {
					override fun onShow(layout: Bulletin.Layout) {
						bulletins.add(bulletin)
					}

					override fun onHide(layout: Bulletin.Layout) {
						bulletins.remove(bulletin)
					}
				})

				bulletin.show()

				AndroidUtilities.runOnUIThread(Runnable {
					windowManager.removeView(bulletinContainer)
				}.also {
					bulletinHideCallback = it
				}, (Bulletin.DURATION_SHORT + 1000).toLong())

				return@setOnItemClickListener
			}

			clicked = true

			peerObj.peer?.let {
				selectCallback.onPeerSelected(recyclerView, view as SenderView, it)
			}
		}

		recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER)

		recyclerFrameLayout.addView(recyclerView)

		shadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow)
		shadowDrawable?.alpha = 0x99

		headerShadow.background = shadowDrawable
		headerShadow.setAlpha(0f)

		recyclerFrameLayout.addView(headerShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 4f))

		recyclerContainer.addView(recyclerFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

		scrimPopupContainerLayout.addView(recyclerContainer)
	}

	override fun dismiss() {
		if (dismissed) {
			return
		}

		if (bulletinContainer != null && bulletinContainer?.alpha == 1f) {
			val windowManager = bulletinContainer?.context?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

			bulletinContainer?.animate()?.alpha(0f)?.setDuration(150)?.setListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					windowManager?.removeViewImmediate(bulletinContainer)

					bulletinHideCallback?.let {
						AndroidUtilities.cancelRunOnUIThread(it)
					}
				}
			})
		}

		dismissed = true

		super.dismiss()
	}

	override fun showAtLocation(parent: View, gravity: Int, x: Int, y: Int) {
		super.showAtLocation(parent, gravity, x.also { popupX = it }, y.also { popupY = it })
	}

	fun startShowAnimation() {
		for (springAnimation in springAnimations) {
			springAnimation.cancel()
		}

		springAnimations.clear()

		scrimPopupContainerLayout.pivotX = AndroidUtilities.dp(8f).toFloat()
		scrimPopupContainerLayout.pivotY = (scrimPopupContainerLayout.measuredHeight - AndroidUtilities.dp(8f)).toFloat()

		recyclerContainer.pivotX = 0f
		recyclerContainer.pivotY = 0f

		val peers = sendAsPeers?.peers ?: listOf()
		val defPeer = if (chatFull.defaultSendAs != null) chatFull.defaultSendAs else null

		if (defPeer != null) {
			val itemHeight = AndroidUtilities.dp((14 + AVATAR_SIZE_DP).toFloat())
			val totalRecyclerHeight = peers.size * itemHeight

			for (i in peers.indices) {
				val p = peers[i].peer

				if (p.channelId != 0L && p.channelId == defPeer.channelId || p.userId != 0L && p.userId == defPeer.userId || p.chatId != 0L && p.chatId == defPeer.chatId) {
					var off = 0

					if (i != peers.size - 1 && recyclerView.measuredHeight < totalRecyclerHeight) {
						off = recyclerView.measuredHeight % itemHeight
					}

					layoutManager.scrollToPositionWithOffset(i, off + AndroidUtilities.dp(7f) + (totalRecyclerHeight - (peers.size - 2) * itemHeight))

					if (recyclerView.computeVerticalScrollOffset() > 0) {
						headerShadow.animate().cancel()
						headerShadow.animate().alpha(1f).setDuration(SHADOW_DURATION.toLong()).start()
					}

					break
				}
			}
		}

		scrimPopupContainerLayout.scaleX = SCALE_START
		scrimPopupContainerLayout.scaleY = SCALE_START

		recyclerContainer.setAlpha(SCALE_START)

		dimView.setAlpha(0f)

		val newSpringAnimations = listOf(SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.SCALE_X).setSpring(SpringForce(1f).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)).addUpdateListener { _, value, _ ->
			recyclerContainer.scaleX = 1f / value
		}, SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.SCALE_Y).setSpring(SpringForce(1f).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)).addUpdateListener { _, value, _ ->
			recyclerContainer.scaleY = 1f / value
		}, SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.ALPHA).setSpring(SpringForce(1f).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)), SpringAnimation(recyclerContainer, DynamicAnimation.ALPHA).setSpring(SpringForce(1f).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)), SpringAnimation(dimView, DynamicAnimation.ALPHA).setSpring(SpringForce(1f).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)))

		for (animation in newSpringAnimations) {
			springAnimations.add(animation)

			animation.addEndListener { animation1, canceled, _, _ ->
				if (!canceled) {
					springAnimations.remove(animation)
					animation1.cancel()
				}
			}

			animation.start()
		}
	}

	fun startDismissAnimation(vararg animations: SpringAnimation?) {
		for (animation in springAnimations) {
			animation.cancel()
		}

		springAnimations.clear()

		scrimPopupContainerLayout.pivotX = AndroidUtilities.dp(8f).toFloat()
		scrimPopupContainerLayout.pivotY = (scrimPopupContainerLayout.measuredHeight - AndroidUtilities.dp(8f)).toFloat()

		recyclerContainer.pivotX = 0f
		recyclerContainer.pivotY = 0f

		scrimPopupContainerLayout.scaleX = 1f
		scrimPopupContainerLayout.scaleY = 1f

		recyclerContainer.setAlpha(1f)

		dimView.setAlpha(1f)

		val newSpringAnimations = mutableListOf<SpringAnimation>()

		newSpringAnimations.addAll(listOf(SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.SCALE_X).setSpring(SpringForce(SCALE_START).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)).addUpdateListener { _, value, _ ->
			recyclerContainer.scaleX = 1f / value
		}, SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.SCALE_Y).setSpring(SpringForce(SCALE_START).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)).addUpdateListener { _, value, _ ->
			recyclerContainer.scaleY = 1f / value
		}, SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.ALPHA).setSpring(SpringForce(0f).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)), SpringAnimation(recyclerContainer, DynamicAnimation.ALPHA).setSpring(SpringForce(SCALE_START).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)), SpringAnimation(dimView, DynamicAnimation.ALPHA).setSpring(SpringForce(0f).setStiffness(SPRING_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)).addEndListener { _, _, _, _ ->
			(dimView.parent as? ViewGroup)?.removeView(dimView)
			dismiss()
		}))

		newSpringAnimations.addAll(animations.toList().filterNotNull())

		runningCustomSprings = animations.isNotEmpty()

		newSpringAnimations[0].addEndListener { _, _, _, _ ->
			runningCustomSprings = false
		}

		for (springAnimation in newSpringAnimations) {
			springAnimations.add(springAnimation)

			springAnimation.addEndListener { animation, canceled, _, _ ->
				if (!canceled) {
					springAnimations.remove(springAnimation)
					animation.cancel()
				}
			}

			springAnimation.start()
		}
	}

	class SenderView(context: Context) : LinearLayout(context) {
		val avatar: SimpleAvatarView
		val title: TextView
		val subtitle: TextView

		init {
			setLayoutParams(RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

			orientation = HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL

			val padding = AndroidUtilities.dp(14f)

			setPadding(padding, padding / 2, padding, padding / 2)

			avatar = SimpleAvatarView(context)

			addView(avatar, LayoutHelper.createFrame(AVATAR_SIZE_DP, AVATAR_SIZE_DP.toFloat()))

			val textRow = LinearLayout(context)
			textRow.orientation = VERTICAL

			addView(textRow, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 12, 0, 0, 0))

			title = TextView(context)
			title.setTextColor(context.getColor(R.color.text))
			title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			title.tag = title
			title.setMaxLines(1)

			textRow.addView(title)

			subtitle = TextView(context)
			subtitle.setTextColor(ColorUtils.setAlphaComponent(context.getColor(R.color.text), 0x66))
			subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			subtitle.tag = subtitle
			subtitle.setMaxLines(1)
			subtitle.ellipsize = TextUtils.TruncateAt.END

			textRow.addView(subtitle)
		}
	}

	fun interface OnSelectCallback {
		fun onPeerSelected(recyclerView: RecyclerView, senderView: SenderView, peer: Peer)
	}

	private inner class BackButtonFrameLayout(context: Context) : FrameLayout(context) {
		override fun dispatchKeyEvent(event: KeyEvent): Boolean {
			if (event.keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0 && isShowing) {
				dismiss()
			}

			return super.dispatchKeyEvent(event)
		}
	}

	companion object {
		const val SPRING_STIFFNESS = 750f
		const val AVATAR_SIZE_DP = 40
		private const val SHADOW_DURATION = 150
		private const val SCALE_START = 0.25f
	}
}
