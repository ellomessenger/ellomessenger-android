/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.channel

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.widget.addTextChangedListener
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.vibrate
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.link
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.AdministeredChannelCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.LoadingCell
import org.telegram.ui.Cells.RadioButtonCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CircularProgressDrawable
import org.telegram.ui.Components.CrossfadeDrawable
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.EditTextEmoji
import org.telegram.ui.Components.ImageUpdater
import org.telegram.ui.Components.ImageUpdater.ImageUpdaterDelegate
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.LinkActionView
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.group.GroupCreateActivity
import kotlin.math.abs
import kotlin.math.min

class ChannelCreateActivity(args: Bundle) : BaseFragment(args), NotificationCenterDelegate, ImageUpdaterDelegate {
	private var doneButton: View? = null
	private var doneButtonDrawable: CrossfadeDrawable? = null
	private var nameTextView: EditTextEmoji? = null
	private var sectionCell: ShadowSectionCell? = null
	private var avatarImage: BackupImageView? = null
	private var avatarOverlay: View? = null
	private var avatarEditor: RLottieImageView? = null
	private var avatarAnimation: AnimatorSet? = null
	private var avatarProgressView: RadialProgressView? = null
	private var avatarDrawable: AvatarDrawable? = null
	private var imageUpdater: ImageUpdater? = null
	private var descriptionTextView: EditTextBoldCursor? = null
	private var avatar: TLRPC.FileLocation? = null
	private var avatarBig: TLRPC.FileLocation? = null
	private var nameToSet: String? = null
	private var linearLayout2: LinearLayout? = null
	private var headerCell2: HeaderCell? = null
	private var editText: EditTextBoldCursor? = null
	private var cameraDrawable: RLottieDrawable? = null
	private var linearLayout: LinearLayout? = null
	private var administeredChannelsLayout: LinearLayout? = null
	private var linkContainer: LinearLayout? = null
	private var publicContainer: LinearLayout? = null
	private var privateContainer: LinearLayout? = null
	private var permanentLinkView: LinkActionView? = null
	private var radioButtonCell1: RadioButtonCell? = null
	private var radioButtonCell2: RadioButtonCell? = null
	private var typeInfoCell: TextInfoPrivacyCell? = null
	private var helpTextView: TextView? = null
	private var checkTextView: TextView? = null
	private var headerCell: HeaderCell? = null
	private var checkReqId = 0
	private var lastCheckName: String? = null
	private var checkRunnable: Runnable? = null
	private var lastNameAvailable = false
	private var isPrivate = false
	private var loadingInvite = false
	private var invite: TLRPC.ExportedChatInvite? = null
	private var loadingAdministeredChannels = false
	private var administeredInfoCell: TextInfoPrivacyCell? = null
	private val administeredChannelCells = ArrayList<AdministeredChannelCell>()
	private var loadingAdministeredCell: LoadingCell? = null
	private val currentStep = args.getInt("step", 0)
	private var chatId: Long = 0
	private var canCreatePublic = true
	private var inputPhoto: TLRPC.InputFile? = null
	private var inputVideo: TLRPC.InputFile? = null
	private var inputVideoPath: String? = null
	private var videoTimestamp = 0.0
	private var createAfterUpload = false
	private var donePressed = false
	private var doneRequestId: Int? = null
	private var cancelDialog: AlertDialog? = null
	private val enableDoneLoading = Runnable { updateDoneProgress(true) }
	private var doneButtonDrawableAnimator: ValueAnimator? = null

	override fun onFragmentCreate(): Boolean {
		NotificationCenter.getInstance(currentAccount).let {
			it.addObserver(this, NotificationCenter.chatDidCreated)
			it.addObserver(this, NotificationCenter.chatDidFailCreate)
		}

		if (currentStep == 1) {
			generateLink()
		}

		imageUpdater?.parentFragment = this
		imageUpdater?.setDelegate(this)

		return super.onFragmentCreate()
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		doneRequestId?.let {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(it, true)
		}

		doneRequestId = null

		NotificationCenter.getInstance(currentAccount).let {
			it.removeObserver(this, NotificationCenter.chatDidCreated)
			it.removeObserver(this, NotificationCenter.chatDidFailCreate)
		}

		imageUpdater?.clear()

		AndroidUtilities.removeAdjustResize(parentActivity, classGuid)

		nameTextView?.onDestroy()
	}

	override fun onResume() {
		super.onResume()
		nameTextView?.onResume()
		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
		imageUpdater?.onResume()
	}

	override fun onPause() {
		super.onPause()
		nameTextView?.onPause()
		imageUpdater?.onPause()
	}

	override fun dismissCurrentDialog() {
		if (imageUpdater?.dismissCurrentDialog(visibleDialog) == true) {
			return
		}

		super.dismissCurrentDialog()
	}

	override fun dismissDialogOnPause(dialog: Dialog): Boolean {
		return (imageUpdater == null || imageUpdater?.dismissDialogOnPause(dialog) == true) && super.dismissDialogOnPause(dialog)
	}

	override fun onRequestPermissionsResultFragment(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		imageUpdater?.onRequestPermissionsResultFragment(requestCode, permissions, grantResults)
	}

	override fun onBackPressed(): Boolean {
		if (nameTextView?.isPopupShowing == true) {
			nameTextView?.hidePopup(true)
			return false
		}

		return true
	}

	private fun showDoneCancelDialog() {
		if (cancelDialog != null) {
			return
		}

		val parentActivity = parentActivity ?: return

		val builder = AlertDialog.Builder(parentActivity)
		builder.setTitle(parentActivity.getString(R.string.AppName))
		builder.setMessage(parentActivity.getString(R.string.StopLoading))
		builder.setPositiveButton(parentActivity.getString(R.string.WaitMore), null)

		builder.setNegativeButton(parentActivity.getString(R.string.Stop)) { dialogInterface, _ ->
			donePressed = false
			createAfterUpload = false

			doneRequestId?.let {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(it, true)
			}

			doneRequestId = null
			updateDoneProgress(false)
			dialogInterface.dismiss()
		}

		cancelDialog = builder.show()
	}

	init {

		if (currentStep == 0) {
			avatarDrawable = AvatarDrawable()
			imageUpdater = ImageUpdater(false)

			val req = TLRPC.TLChannelsCheckUsername()
			req.username = "1"
			req.channel = TLRPC.TLInputChannelEmpty()

			ConnectionsManager.getInstance(currentAccount).sendRequest(req) { _, error ->
				AndroidUtilities.runOnUIThread {
					canCreatePublic = error == null || error.text != "CHANNELS_ADMIN_PUBLIC_TOO_MUCH"
				}
			}
		}
		else {
			if (currentStep == 1) {
				canCreatePublic = args.getBoolean("canCreatePublic", true)
				isPrivate = !canCreatePublic

				if (!canCreatePublic) {
					loadAdministeredChannels()
				}
			}

			chatId = args.getLong("chat_id", 0)
		}
	}

	private fun updateDoneProgress(loading: Boolean) {
		if (!loading) {
			AndroidUtilities.cancelRunOnUIThread(enableDoneLoading)
		}

		doneButtonDrawable?.let { doneButtonDrawable ->
			doneButtonDrawableAnimator?.cancel()

			doneButtonDrawableAnimator = ValueAnimator.ofFloat(doneButtonDrawable.progress, if (loading) 1f else 0f)

			doneButtonDrawableAnimator?.addUpdateListener {
				doneButtonDrawable.progress = it.animatedValue as Float
				doneButtonDrawable.invalidateSelf()
			}

			doneButtonDrawableAnimator?.duration = (200 * abs(doneButtonDrawable.progress - if (loading) 1f else 0f)).toLong()
			doneButtonDrawableAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
			doneButtonDrawableAnimator?.start()
		}
	}

	override fun createView(context: Context): View? {
		nameTextView?.onDestroy()

		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				val parentActivity = parentActivity ?: return

				if (id == ActionBar.BACK_BUTTON) {
					if (donePressed) {
						showDoneCancelDialog()
						return
					}

					finishFragment()
				}
				else if (id == BUTTON_DONE) {
					if (currentStep == 0) {
						if (donePressed) {
							showDoneCancelDialog()
							return
						}

						if (nameTextView?.length() == 0) {
							parentActivity.vibrate()
							AndroidUtilities.shakeView(nameTextView, 2f, 0)
							return
						}

						donePressed = true

						AndroidUtilities.runOnUIThread(enableDoneLoading, 200)

						if (imageUpdater?.isUploadingImage == true) {
							createAfterUpload = true
							return
						}

						doneRequestId = MessagesController.getInstance(currentAccount).createChat(nameTextView?.text?.toString() ?: "", ArrayList(), descriptionTextView?.text?.toString(), ChatObject.CHAT_TYPE_CHANNEL, false, null, null, this@ChannelCreateActivity, false, TLRPC.PAY_TYPE_NONE, null, 0.0, null, invite?.link)
					}
					else if (currentStep == 1) {
						if (!isPrivate) {
							if (descriptionTextView?.length() == 0) {
								val builder = AlertDialog.Builder(parentActivity)
								builder.setTitle(context.getString(R.string.ChannelPublicEmptyUsernameTitle))
								builder.setMessage(context.getString(R.string.ChannelPublicEmptyUsername))
								builder.setPositiveButton(context.getString(R.string.Close), null)
								showDialog(builder.create())
								return
							}
							else {
								val lastCheckName = lastCheckName

								if (!lastNameAvailable || lastCheckName.isNullOrEmpty()) {
									parentActivity.vibrate()
									AndroidUtilities.shakeView(checkTextView, 2f, 0)
									return
								}
								else {
									MessagesController.getInstance(currentAccount).updateChannelUserName(chatId, lastCheckName)
								}
							}
						}

						val args = Bundle()
						args.putInt("step", 2)
						args.putLong("chatId", chatId)
						args.putInt("chatType", ChatObject.CHAT_TYPE_CHANNEL)
						presentFragment(GroupCreateActivity(args), true)
					}
				}
			}
		})

		val menu = actionBar?.createMenu()

		val checkmark = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_ab_done, null)!!.mutate()
		checkmark.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY)

		doneButtonDrawable = CrossfadeDrawable(checkmark, CircularProgressDrawable(ResourcesCompat.getColor(context.resources, R.color.brand, null)))

		doneButton = menu?.addItemWithWidth(BUTTON_DONE, doneButtonDrawable, AndroidUtilities.dp(56f), context.getString(R.string.Done))

		if (currentStep == 0) {
			actionBar?.setTitle(context.getString(R.string.NewChannel))

			val sizeNotifierFrameLayout: SizeNotifierFrameLayout = object : SizeNotifierFrameLayout(context) {
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

						if (nameTextView?.isPopupView(child) == true) {
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
					val paddingBottom = if (keyboardSize <= AndroidUtilities.dp(20f) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) (nameTextView?.emojiPadding ?: 0) else 0

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

						if (nameTextView?.isPopupView(child) == true) {
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
			fragmentView?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

			linearLayout = LinearLayout(context)
			linearLayout?.orientation = LinearLayout.VERTICAL

			sizeNotifierFrameLayout.addView(linearLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

			val frameLayout = FrameLayout(context)

			linearLayout?.addView(frameLayout, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			avatarImage = object : BackupImageView(context) {
				override fun invalidate() {
					avatarOverlay?.invalidate()
					super.invalidate()
				}

				@Deprecated("Deprecated in Java")
				override fun invalidate(l: Int, t: Int, r: Int, b: Int) {
					avatarOverlay?.invalidate()
					super.invalidate(l, t, r, b)
				}
			}

			avatarImage?.setRoundRadius(AndroidUtilities.dp(32f))

			avatarDrawable?.setInfo(null, null)

			avatarImage?.setImageDrawable(avatarDrawable)

			frameLayout.addView(avatarImage, createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 12f, if (LocaleController.isRTL) 16f else 0f, 12f))

			val paint = Paint(Paint.ANTI_ALIAS_FLAG)
			paint.color = 0x55000000

			avatarOverlay = object : View(context) {
				override fun onDraw(canvas: Canvas) {
					avatarImage?.imageReceiver?.let {
						if (it.hasNotThumb()) {
							paint.alpha = (0x55 * it.currentAlpha).toInt()
							canvas.drawCircle(measuredWidth / 2.0f, measuredHeight / 2.0f, measuredWidth / 2.0f, paint)
						}
					}
				}
			}

			avatarOverlay?.contentDescription = context.getString(R.string.ChatSetPhotoOrVideo)

			frameLayout.addView(avatarOverlay, createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 12f, if (LocaleController.isRTL) 16f else 0f, 12f))

			avatarOverlay?.setOnClickListener {
				imageUpdater?.openMenu(avatar != null, {
					avatar = null
					avatarBig = null
					inputPhoto = null
					inputVideo = null
					inputVideoPath = null
					videoTimestamp = 0.0

					showAvatarProgress(show = false, animated = true)

					avatarImage?.setImage(null, null, avatarDrawable, null)
					avatarEditor?.setAnimation(cameraDrawable!!)
					cameraDrawable?.currentFrame = 0
				}) {
					if (imageUpdater?.isUploadingImage != true) {
						cameraDrawable?.customEndFrame = 86
						avatarEditor?.playAnimation()
					}
					else {
						cameraDrawable?.setCurrentFrame(0, false)
					}
				}

				cameraDrawable?.currentFrame = 0
				cameraDrawable?.customEndFrame = 43

				avatarEditor?.playAnimation()
			}

			cameraDrawable = RLottieDrawable(R.raw.camera, "" + R.raw.camera, AndroidUtilities.dp(60f), AndroidUtilities.dp(60f), false, null)

			avatarEditor = object : RLottieImageView(context) {
				@Deprecated("Deprecated in Java")
				override fun invalidate(l: Int, t: Int, r: Int, b: Int) {
					super.invalidate(l, t, r, b)
					avatarOverlay?.invalidate()
				}

				override fun invalidate() {
					super.invalidate()
					avatarOverlay?.invalidate()
				}
			}

			avatarEditor?.scaleType = ImageView.ScaleType.CENTER
			avatarEditor?.setAnimation(cameraDrawable!!)
			avatarEditor?.isEnabled = false
			avatarEditor?.isClickable = false
			avatarEditor?.setPadding(AndroidUtilities.dp(0f), 0, 0, AndroidUtilities.dp(1f))

			frameLayout.addView(avatarEditor, createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 15f, 12f, if (LocaleController.isRTL) 15f else 0f, 12f))

			avatarProgressView = RadialProgressView(context)
			avatarProgressView?.setSize(AndroidUtilities.dp(30f))
			avatarProgressView?.setProgressColor(-0x1)
			avatarProgressView?.setNoProgress(false)

			frameLayout.addView(avatarProgressView, createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 12f, if (LocaleController.isRTL) 16f else 0f, 12f))

			showAvatarProgress(show = false, animated = false)

			nameTextView = EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT, false)

			nameTextView?.setHint(context.getString(R.string.EnterChannelName))

			if (nameToSet != null) {
				nameTextView?.text = nameToSet
				nameToSet = null
			}

			nameTextView?.setFilters(arrayOf(LengthFilter(100)))
			nameTextView?.editText?.isSingleLine = true
			nameTextView?.editText?.imeOptions = EditorInfo.IME_ACTION_NEXT

			nameTextView?.editText?.setOnEditorActionListener { _, i, _ ->
				if (i == EditorInfo.IME_ACTION_NEXT && !nameTextView?.editText?.text.isNullOrEmpty()) {
					descriptionTextView?.requestFocus()
					return@setOnEditorActionListener true
				}

				false
			}

			frameLayout.addView(nameTextView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) 5f else 96f, 0f, if (LocaleController.isRTL) 96f else 5f, 0f))

			descriptionTextView = EditTextBoldCursor(context)
			descriptionTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
			descriptionTextView?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
			descriptionTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			descriptionTextView?.background = null
			descriptionTextView?.setLineColors(ResourcesCompat.getColor(context.resources, R.color.hint, null), ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))
			descriptionTextView?.setPadding(0, 0, 0, AndroidUtilities.dp(6f))
			descriptionTextView?.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
			descriptionTextView?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
			descriptionTextView?.imeOptions = EditorInfo.IME_ACTION_DONE
			descriptionTextView?.filters = arrayOf(LengthFilter(120))
			descriptionTextView?.hint = context.getString(R.string.DescriptionPlaceholder)
			descriptionTextView?.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
			descriptionTextView?.setCursorSize(AndroidUtilities.dp(20f))
			descriptionTextView?.setCursorWidth(1.5f)

			linearLayout?.addView(descriptionTextView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24f, 18f, 24f, 0f))

			descriptionTextView?.setOnEditorActionListener { _, i, _ ->
				if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
					doneButton?.performClick()
					return@setOnEditorActionListener true
				}

				false
			}

			helpTextView = TextView(context)
			helpTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			helpTextView?.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8))
			helpTextView?.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
			helpTextView?.text = context.getString(R.string.DescriptionInfo)

			linearLayout?.addView(helpTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 24, 10, 24, 20))
		}
		else if (currentStep == 1) {
			fragmentView = ScrollView(context)

			val scrollView = fragmentView as ScrollView
			scrollView.isFillViewport = true

			linearLayout = LinearLayout(context)
			linearLayout?.orientation = LinearLayout.VERTICAL

			scrollView.addView(linearLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

			actionBar?.setTitle(context.getString(R.string.ChannelSettingsTitle))

			fragmentView?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

			headerCell2 = HeaderCell(context, 23)
			headerCell2?.height = 46
			headerCell2?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
			headerCell2?.setText(context.getString(R.string.ChannelTypeHeader))

			linearLayout?.addView(headerCell2)

			linearLayout2 = LinearLayout(context)
			linearLayout2?.orientation = LinearLayout.VERTICAL
			linearLayout2?.setBackgroundColor(context.getColor(R.color.background))

			linearLayout?.addView(linearLayout2, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			radioButtonCell1 = RadioButtonCell(context)
			radioButtonCell1?.background = Theme.getSelectorDrawable(false)
			radioButtonCell1?.setTextAndValue(context.getString(R.string.ChannelPublic), context.getString(R.string.ChannelPublicInfo), false, !isPrivate)

			linearLayout2?.addView(radioButtonCell1, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			radioButtonCell1?.setOnClickListener {
				if (!canCreatePublic) {
					showPremiumIncreaseLimitDialog()
					return@setOnClickListener
				}

				if (!isPrivate) {
					return@setOnClickListener
				}

				isPrivate = false

				updatePrivatePublic()
			}

			radioButtonCell2 = RadioButtonCell(context)
			radioButtonCell2?.background = Theme.getSelectorDrawable(false)
			radioButtonCell2!!.setTextAndValue(context.getString(R.string.ChannelPrivate), context.getString(R.string.ChannelPrivateInfo), false, isPrivate)

			linearLayout2?.addView(radioButtonCell2, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			radioButtonCell2?.setOnClickListener {
				if (isPrivate) {
					return@setOnClickListener
				}

				isPrivate = true

				updatePrivatePublic()
			}

			sectionCell = ShadowSectionCell(context)

			linearLayout?.addView(sectionCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			linkContainer = LinearLayout(context)
			linkContainer?.orientation = LinearLayout.VERTICAL
			linkContainer?.setBackgroundColor(context.getColor(R.color.background))

			linearLayout?.addView(linkContainer, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			headerCell = HeaderCell(context)

			linkContainer?.addView(headerCell)

			publicContainer = LinearLayout(context)
			publicContainer?.orientation = LinearLayout.HORIZONTAL

			linkContainer?.addView(publicContainer, createLinear(LayoutHelper.MATCH_PARENT, 36, 21f, 7f, 21f, 0f))

			editText = EditTextBoldCursor(context)
			editText?.setText(MessagesController.getInstance(currentAccount).linkPrefix + "/")
			editText?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
			editText?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
			editText?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			editText?.maxLines = 1
			editText?.setLines(1)
			editText?.isEnabled = false
			editText?.background = null
			editText?.setPadding(0, 0, 0, 0)
			editText?.isSingleLine = true
			editText?.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
			editText?.imeOptions = EditorInfo.IME_ACTION_DONE

			publicContainer?.addView(editText, createLinear(LayoutHelper.WRAP_CONTENT, 36))

			descriptionTextView = EditTextBoldCursor(context)
			descriptionTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
			descriptionTextView?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
			descriptionTextView!!.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			descriptionTextView?.maxLines = 1
			descriptionTextView?.setLines(1)
			descriptionTextView?.background = null
			descriptionTextView?.setPadding(0, 0, 0, 0)
			descriptionTextView?.isSingleLine = true
			descriptionTextView?.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
			descriptionTextView?.imeOptions = EditorInfo.IME_ACTION_DONE
			descriptionTextView?.hint = context.getString(R.string.ChannelUsernamePlaceholder)
			descriptionTextView?.setCursorColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			descriptionTextView?.setCursorSize(AndroidUtilities.dp(20f))
			descriptionTextView?.setCursorWidth(1.5f)

			publicContainer?.addView(descriptionTextView, createLinear(LayoutHelper.MATCH_PARENT, 36))

			descriptionTextView?.addTextChangedListener {
				checkUserName(descriptionTextView?.text.toString())
			}

			privateContainer = LinearLayout(context)
			privateContainer?.orientation = LinearLayout.VERTICAL

			linkContainer?.addView(privateContainer, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

//			val linkType = if (ChatObject.isChannel(messagesController.getChat(chatId))) {
//				LinkActionView.LinkActionType.CHANNEL
//			}
//			else {
//				LinkActionView.LinkActionType.GROUP
//			}

			permanentLinkView = LinkActionView(context, this, null, true)
			permanentLinkView?.hideRevokeOption(true)
			permanentLinkView?.setUsers(0, null)

			privateContainer?.addView(permanentLinkView)

			checkTextView = TextView(context)
			checkTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			checkTextView?.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
			checkTextView?.visibility = View.GONE

			linkContainer?.addView(checkTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 17, 3, 17, 7))

			typeInfoCell = TextInfoPrivacyCell(context)
			typeInfoCell?.background = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)

			linearLayout?.addView(typeInfoCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			loadingAdministeredCell = LoadingCell(context)

			linearLayout?.addView(loadingAdministeredCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			administeredChannelsLayout = LinearLayout(context)
			administeredChannelsLayout?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
			administeredChannelsLayout?.orientation = LinearLayout.VERTICAL

			linearLayout?.addView(administeredChannelsLayout, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			administeredInfoCell = TextInfoPrivacyCell(context)
			administeredInfoCell?.background = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)

			linearLayout?.addView(administeredInfoCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			updatePrivatePublic()
		}

		return fragmentView
	}

	private fun generateLink() {
		if (loadingInvite || invite != null) {
			return
		}

		val chatFull = messagesController.getChatFull(chatId)

		if (chatFull != null) {
			invite = chatFull.exportedInvite
		}

		if (invite != null) {
			return
		}

		loadingInvite = true

		val req = TLRPC.TLMessagesGetExportedChatInvites()
		req.peer = messagesController.getInputPeer(-chatId)
		req.adminId = messagesController.getInputUser(userConfig.getCurrentUser())
		req.limit = 1

		connectionsManager.sendRequest(req) { response, error ->
			AndroidUtilities.runOnUIThread {
				if (error == null) {
					val invites = response as TLRPC.TLMessagesExportedChatInvites
					invite = invites.invites[0] as TLRPC.TLChatInviteExported
				}

				loadingInvite = false

				permanentLinkView?.setLink(invite?.link)
			}
		}
	}

	private fun updatePrivatePublic() {
		val context = context ?: return

		if (sectionCell == null) {
			return
		}

		if (!isPrivate && !canCreatePublic) {
			typeInfoCell?.setText(context.getString(R.string.ChangePublicLimitReached))
			typeInfoCell?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))

			linkContainer?.gone()
			sectionCell?.gone()

			if (loadingAdministeredChannels) {
				loadingAdministeredCell?.visible()
				administeredChannelsLayout?.gone()
				typeInfoCell?.background = Theme.getThemedDrawable(typeInfoCell!!.context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)
				administeredInfoCell?.gone()
			}
			else {
				typeInfoCell?.background = Theme.getThemedDrawable(typeInfoCell!!.context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow)
				loadingAdministeredCell?.gone()
				administeredChannelsLayout?.visible()
				administeredInfoCell?.visible()
			}
		}
		else {
			typeInfoCell?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
			sectionCell?.visible()
			administeredInfoCell?.gone()
			administeredChannelsLayout?.gone()
			typeInfoCell?.background = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)
			linkContainer?.visible()
			loadingAdministeredCell?.gone()
			typeInfoCell?.setText(if (isPrivate) context.getString(R.string.ChannelPrivateLinkHelp) else context.getString(R.string.ChannelUsernameHelp))
			headerCell?.setText(if (isPrivate) context.getString(R.string.ChannelInviteLinkTitle) else context.getString(R.string.ChannelLinkTitle))
			publicContainer?.visibility = if (isPrivate) View.GONE else View.VISIBLE
			privateContainer?.visibility = if (isPrivate) View.VISIBLE else View.GONE
			linkContainer?.setPadding(0, 0, 0, if (isPrivate) 0 else AndroidUtilities.dp(7f))
			permanentLinkView?.setLink(if (invite != null) invite!!.link else null)
			checkTextView?.visibility = if (!isPrivate && checkTextView?.length() != 0) View.VISIBLE else View.GONE
		}

		radioButtonCell1?.setChecked(!isPrivate)
		radioButtonCell2?.setChecked(isPrivate)

		descriptionTextView?.clearFocus()

		AndroidUtilities.hideKeyboard(descriptionTextView)
	}

	override fun onUploadProgressChanged(progress: Float) {
		avatarProgressView?.setProgress(progress)
	}

	override fun didStartUpload(isVideo: Boolean) {
		avatarProgressView?.setProgress(0.0f)
	}

	override fun didUploadPhoto(photo: TLRPC.InputFile?, video: TLRPC.InputFile?, videoStartTimestamp: Double, videoPath: String?, bigSize: TLRPC.PhotoSize?, smallSize: TLRPC.PhotoSize?) {
		AndroidUtilities.runOnUIThread {
			if (photo != null || video != null) {
				inputPhoto = photo
				inputVideo = video
				inputVideoPath = videoPath
				videoTimestamp = videoStartTimestamp

				if (createAfterUpload) {
					if (cancelDialog != null) {
						try {
							cancelDialog?.dismiss()
							cancelDialog = null
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					updateDoneProgress(false)

					donePressed = false

					doneButton?.performClick()
				}

				showAvatarProgress(show = false, animated = true)
			}
			else {
				avatar = smallSize?.location
				avatarBig = bigSize?.location
				avatarImage?.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, null)
				showAvatarProgress(show = true, animated = false)
			}
		}
	}

	override fun getInitialSearchString(): String? {
		return nameTextView?.text?.toString()
	}

	private fun showAvatarProgress(show: Boolean, animated: Boolean) {
		if (avatarEditor == null) {
			return
		}

		avatarAnimation?.removeAllListeners()
		avatarAnimation?.cancel()
		avatarAnimation = null

		if (animated) {
			avatarAnimation = AnimatorSet()
			if (show) {
				avatarProgressView?.visible()
				avatarAnimation?.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 0.0f), ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f))
			}
			else {
				if (avatarEditor?.visibility != View.VISIBLE) {
					avatarEditor?.alpha = 0f
				}

				avatarEditor?.visible()

				avatarAnimation?.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 1.0f), ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f))
			}

			avatarAnimation?.duration = 180

			avatarAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (avatarAnimation == null || avatarEditor == null) {
						return
					}

					if (show) {
						avatarEditor?.invisible()
					}
					else {
						avatarProgressView?.invisible()
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
				avatarEditor?.alpha = 1.0f
				avatarEditor?.invisible()

				avatarProgressView?.alpha = 1.0f
				avatarProgressView?.visible()
			}
			else {
				avatarEditor?.alpha = 1.0f
				avatarEditor?.visible()

				avatarProgressView?.alpha = 0.0f
				avatarProgressView?.invisible()
			}
		}
	}

	override fun onActivityResultFragment(requestCode: Int, resultCode: Int, data: Intent?) {
		imageUpdater?.onActivityResult(requestCode, resultCode, data)
	}

	override fun saveSelfArgs(args: Bundle) {
		if (currentStep == 0) {
			imageUpdater?.currentPicturePath?.let {
				args.putString("path", it)
			}

			nameTextView?.text?.toString()?.let {
				args.putString("nameTextView", it)
			}
		}
	}

	override fun restoreSelfArgs(args: Bundle) {
		if (currentStep == 0) {
			imageUpdater?.currentPicturePath = args.getString("path")

			val text = args.getString("nameTextView")

			if (text != null) {
				if (nameTextView != null) {
					nameTextView?.text = text
				}
				else {
					nameToSet = text
				}
			}
		}
	}

	public override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
		if (isOpen && currentStep != 1) {
			nameTextView?.requestFocus()
			nameTextView?.openKeyboard()
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.chatDidFailCreate -> {
				try {
					cancelDialog?.dismiss()
					cancelDialog = null
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				updateDoneProgress(false)
				donePressed = false
			}

			NotificationCenter.chatDidCreated -> {
				try {
					cancelDialog?.dismiss()
					cancelDialog = null
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				val chatId = args[0] as Long

				val bundle = Bundle()
				bundle.putInt("step", 1)
				bundle.putLong("chat_id", chatId)
				bundle.putBoolean("canCreatePublic", canCreatePublic)

				if (inputPhoto != null || inputVideo != null) {
					MessagesController.getInstance(currentAccount).changeChatAvatar(chatId, null, inputPhoto, inputVideo, videoTimestamp, inputVideoPath, avatar, avatarBig, null)
				}

				presentFragment(ChannelCreateActivity(bundle), true)
			}
		}
	}

	private fun loadAdministeredChannels() {
		if (loadingAdministeredChannels) {
			return
		}

		loadingAdministeredChannels = true

		updatePrivatePublic()

		val req = TLRPC.TLChannelsGetAdminedPublicChannels()

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				loadingAdministeredChannels = false

				if (response != null) {
					val parentActivity = parentActivity ?: return@runOnUIThread

					administeredChannelCells.forEach {
						linearLayout?.removeView(it)
					}

					administeredChannelCells.clear()

					val res = response as TLRPC.TLMessagesChats

					for (a in res.chats.indices) {
						val administeredChannelCell = AdministeredChannelCell(parentActivity, false, 0) { view ->
							val cell = view.parent as AdministeredChannelCell
							val channel = cell.currentChannel

							val builder = AlertDialog.Builder(parentActivity)
							builder.setTitle(parentActivity.getString(R.string.AppName))

							if (channel?.megagroup == true) {
								builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlert", R.string.RevokeLinkAlert, MessagesController.getInstance(currentAccount).linkPrefix + "/" + channel.username, channel.title)))
							}
							else {
								builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlertChannel", R.string.RevokeLinkAlertChannel, MessagesController.getInstance(currentAccount).linkPrefix + "/" + channel?.username, channel?.title)))
							}

							builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

							builder.setPositiveButton(parentActivity.getString(R.string.RevokeButton)) { _, _ ->
								val req1 = TLRPC.TLChannelsUpdateUsername()
								req1.channel = MessagesController.getInputChannel(channel)
								req1.username = ""

								ConnectionsManager.getInstance(currentAccount).sendRequest(req1, { response1, _ ->
									if (response1 is TLRPC.TLBoolTrue) {
										AndroidUtilities.runOnUIThread {
											canCreatePublic = true

											if ((descriptionTextView?.length() ?: 0) > 0) {
												checkUserName(descriptionTextView?.text?.toString())
											}

											updatePrivatePublic()
										}
									}
								}, ConnectionsManager.RequestFlagInvokeAfter)
							}

							showDialog(builder.create())
						}

						administeredChannelCell.setChannel(res.chats[a], a == res.chats.size - 1)

						administeredChannelCells.add(administeredChannelCell)

						administeredChannelsLayout?.addView(administeredChannelCell, createLinear(LayoutHelper.MATCH_PARENT, 72))
					}
					updatePrivatePublic()
				}
			}
		}
	}

	private fun checkUserName(name: String?): Boolean {
		val context = context ?: return false

		if (name.isNullOrEmpty()) {
			checkTextView?.gone()
		}
		else {
			checkTextView?.visible()
		}

		if (checkRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(checkRunnable)

			checkRunnable = null
			lastCheckName = null

			if (checkReqId != 0) {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(checkReqId, true)
			}
		}

		lastNameAvailable = false

		if (name != null) {
			if (name.startsWith("_") || name.endsWith("_")) {
				checkTextView?.text = context.getString(R.string.LinkInvalid)
				checkTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
				return false
			}

			for (a in name.indices) {
				val ch = name[a]

				if (a == 0 && ch >= '0' && ch <= '9') {
					checkTextView?.text = context.getString(R.string.LinkInvalidStartNumber)
					checkTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
					return false
				}

				if (!(ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z' || ch != '_')) {
					checkTextView?.text = context.getString(R.string.LinkInvalid)
					checkTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
					return false
				}
			}
		}

		if (name == null || name.length < 5) {
			checkTextView?.text = context.getString(R.string.LinkInvalidShort)
			checkTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
			return false
		}

		if (name.length > 32) {
			checkTextView?.text = context.getString(R.string.LinkInvalidLong)
			checkTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
			return false
		}

		checkTextView?.text = context.getString(R.string.LinkChecking)
		checkTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))

		lastCheckName = name

		checkRunnable = Runnable {
			val req = TLRPC.TLChannelsCheckUsername()
			req.username = name
			req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chatId)

			checkReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					checkReqId = 0

					if (lastCheckName != null && lastCheckName == name) {
						if (error == null && response is TLRPC.TLBoolTrue) {
							checkTextView?.text = LocaleController.formatString("LinkAvailable", R.string.LinkAvailable)
							checkTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.green, null))
							lastNameAvailable = true
						}
						else {
							if (error != null && error.text == "CHANNELS_ADMIN_PUBLIC_TOO_MUCH") {
								canCreatePublic = false
								showPremiumIncreaseLimitDialog()
							}
							else {
								checkTextView?.text = context.getString(R.string.LinkInUse)
							}

							checkTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))

							lastNameAvailable = false
						}
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}

		AndroidUtilities.runOnUIThread(checkRunnable, 300)

		return true
	}

	private fun showPremiumIncreaseLimitDialog() {
		if (parentActivity == null) {
			return
		}

		val limitReachedBottomSheet = LimitReachedBottomSheet(this, LimitReachedBottomSheet.TYPE_PUBLIC_LINKS, currentAccount)
		limitReachedBottomSheet.parentIsChannel = true

		limitReachedBottomSheet.onSuccessRunnable = Runnable {
			canCreatePublic = true
			updatePrivatePublic()
		}

		showDialog(limitReachedBottomSheet)
	}

	companion object {
		private const val BUTTON_DONE = 1
	}
}
