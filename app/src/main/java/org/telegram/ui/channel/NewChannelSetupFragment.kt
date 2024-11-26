/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.channel

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.os.Bundle
import android.text.InputFilter
import android.text.Spannable
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.buildSpannedString
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doOnTextChanged
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SvgHelper
import org.telegram.messenger.databinding.NewMessageGroupTypeViewBinding
import org.telegram.messenger.databinding.SpinnerLayoutBinding
import org.telegram.messenger.utils.ClickableString
import org.telegram.messenger.utils.dp
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.vibrate
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.CountriesDataSource
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.ChannelCategoriesAdapter
import org.telegram.ui.Adapters.CountriesAdapter
import org.telegram.ui.Cells.GroupBioContainer
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.EditTextEmoji
import org.telegram.ui.Components.ImageUpdater
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.TypefaceSpan
import org.telegram.ui.Country
import kotlin.math.min

class NewChannelSetupFragment(args: Bundle) : BaseFragment(args), NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate {
	private var doneButton: View? = null
	private var nameTextView: EditTextEmoji? = null
	private var bioContainer: GroupBioContainer? = null
	private var avatarImage: BackupImageView? = null
	private var avatarOverlay: View? = null
	private var avatarEditor: ImageView? = null
	private var avatarAnimation: AnimatorSet? = null
	private var avatarProgressView: RadialProgressView? = null
	private val avatarDrawable = AvatarDrawable()
	private val imageUpdater = ImageUpdater(false)
	private var avatar: TLRPC.FileLocation? = null
	private var avatarBig: TLRPC.FileLocation? = null
	private var nameToSet: String? = null
	private var linearLayout: LinearLayout? = null
	private var adultBinding: NewMessageGroupTypeViewBinding? = null
	private var kidsBinding: NewMessageGroupTypeViewBinding? = null
	private var helpTextView: TextView? = null
	private var inputPhoto: TLRPC.InputFile? = null
	private var inputVideo: TLRPC.InputFile? = null
	private var inputVideoPath: String? = null
	private var videoTimestamp = 0.0
	private var createAfterUpload = false
	private var donePressed = false
	private var doneRequestId: Int? = null
	private var cancelDialog: AlertDialog? = null
	private val enableDoneLoading = Runnable { updateDoneProgress(true) }
	private var isPublic = false
	private var isPaid = false
	private var isCourse = false
	private var cost = 0.0
	private var startDate = 0L
	private var endDate = 0L
	private var inviteLink: String? = null
	private var users: List<Long>? = null
	private var categoryHeader: TextView? = null
	private var categorySpinnerLayout: SpinnerLayoutBinding? = null
	private var genreHeader: TextView? = null
	private var genreSpinnerLayout: SpinnerLayoutBinding? = null
	private var subgenreHeader: TextView? = null
	private var subgenreSpinnerLayout: SpinnerLayoutBinding? = null
	private var countryHeader: TextView? = null
	private var countrySpinnerLayout: SpinnerLayoutBinding? = null
	private var countries: List<Country>? = null
	private var categories: List<String>? = null
	private var genres: List<String>? = null
	private var policyTextView: TextView? = null

	override fun onFragmentCreate(): Boolean {
		isPublic = arguments?.getBoolean("isPublic") ?: false
		isPaid = arguments?.getBoolean("isPaid") ?: false
		isCourse = arguments?.getBoolean("isCourse") ?: false
		inviteLink = arguments?.getString("inviteLink")
		users = arguments?.getLongArray("users")?.toList()
		cost = arguments?.getDouble("cost") ?: 0.0
		startDate = arguments?.getLong("startDate") ?: 0L
		endDate = arguments?.getLong("endDate") ?: 0L

		notificationCenter.let {
			it.addObserver(this, NotificationCenter.chatDidCreated)
			it.addObserver(this, NotificationCenter.chatDidFailCreate)
		}

		imageUpdater.parentFragment = this
		imageUpdater.setDelegate(this)

		loadCountries()
		loadCategories()
		loadGenres()

		return super.onFragmentCreate()
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		doneRequestId?.let {
			connectionsManager.cancelRequest(it, true)
		}

		doneRequestId = null

		notificationCenter.let {
			it.removeObserver(this, NotificationCenter.chatDidCreated)
			it.removeObserver(this, NotificationCenter.chatDidFailCreate)
		}

		imageUpdater.clear()

		AndroidUtilities.removeAdjustResize(parentActivity, classGuid)

		nameTextView?.onDestroy()

		adultBinding = null
		kidsBinding = null
	}

	override fun onResume() {
		super.onResume()
		nameTextView?.onResume()
		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
		imageUpdater.onResume()
	}

	override fun onPause() {
		super.onPause()
		nameTextView?.onPause()
		imageUpdater.onPause()
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
				connectionsManager.cancelRequest(it, true)
			}

			doneRequestId = null
			updateDoneProgress(false)
			dialogInterface.dismiss()
		}

		cancelDialog = builder.show()
	}

	private fun updateDoneProgress(loading: Boolean) {
		doneButton?.isEnabled = !loading

		if (!loading) {
			AndroidUtilities.cancelRunOnUIThread(enableDoneLoading)
		}
	}

	override fun createView(context: Context): View? {
		nameTextView?.onDestroy()

		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						if (donePressed) {
							showDoneCancelDialog()
							return
						}

						finishFragment()
					}

					BUTTON_DONE -> {
						if (parentActivity == null) {
							return
						}

						if (doneRequestId != null) {
							return
						}

						messagesController.loadAppConfig()

						val payType = if (isCourse) TLRPC.Chat.PAY_TYPE_BASE else if (isPaid) TLRPC.Chat.PAY_TYPE_SUBSCRIBE else TLRPC.Chat.PAY_TYPE_NONE
						val adult = adultBinding?.checkbox?.isChecked ?: true

						val country = (countrySpinnerLayout?.spinner?.adapter as? CountriesAdapter)?.countries?.find {
							it.name == countrySpinnerLayout?.spinner?.text?.toString()?.trim()
						}

						val category = (categorySpinnerLayout?.spinner?.adapter as? ChannelCategoriesAdapter)?.categories?.find {
							it == categorySpinnerLayout?.spinner?.text?.toString()?.trim()
						}

						val name = nameTextView?.text?.toString()?.trim()

						if (country?.code == null) {
							countrySpinnerLayout?.spinnerLayout?.error = context.getString(R.string.country_is_empty)
						}

						if (category.isNullOrEmpty()) {
							categorySpinnerLayout?.spinnerLayout?.error = context.getString(R.string.category_is_empty)
						}

						if (name.isNullOrEmpty()) {
							parentActivity?.vibrate()
							AndroidUtilities.shakeView(nameTextView, 2f, 0)
						}

						if (country?.code == null || category.isNullOrEmpty() || name.isNullOrEmpty()) {
							return
						}

						var genre: String? = null
						var subgenre: String? = null

						if (category.lowercase() == "music") {
							genre = genres?.find { genreSpinnerLayout?.spinner?.text?.toString()?.trim() == it }
							subgenre = genres?.find { subgenreSpinnerLayout?.spinner?.text?.toString()?.trim() == it }
						}

						if (donePressed) {
							showDoneCancelDialog()
							return
						}

						donePressed = true

						AndroidUtilities.runOnUIThread(enableDoneLoading, 200)

						if (imageUpdater.isUploadingImage) {
							createAfterUpload = true
							return
						}

						doneRequestId = messagesController.createChat(name, listOf(), bioContainer?.text, ChatObject.CHAT_TYPE_CHANNEL, false, null, null, this@NewChannelSetupFragment, adult, payType, country.code, cost, category, startDate, endDate, genre, subgenre, inviteLink)
					}
				}
			}
		})

		val menu = actionBar?.createMenu()

		val checkmark = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_ab_done, null)!!.mutate()
		checkmark.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand_day_night, null), PorterDuff.Mode.SRC_IN)

		doneButton = menu?.addItem(BUTTON_DONE, buildSpannedString {
			val buttonText = context.getString(R.string.Create)
			append(buttonText)
			setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), 0, buttonText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.brand)), 0, buttonText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
		})

		val channelTypeString = context.getString(if (isCourse) R.string.online_course else if (isPaid) R.string.subscription_channel else if (isPublic) R.string.public_channel else R.string.private_channel)
		actionBar?.setTitle(channelTypeString)

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

					if (child == null || child.visibility == GONE || child === actionBar) {
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

					if (child.visibility == GONE) {
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

		val scrollView = ScrollView(context)
		scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT))

		sizeNotifierFrameLayout.addView(scrollView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

		val frameLayout = FrameLayout(context)

		linearLayout?.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		avatarImage = object : BackupImageView(context) {
			override fun invalidate() {
				avatarOverlay?.invalidate()
				super.invalidate()
			}

			@Deprecated("Deprecated in Java")
			override fun invalidate(l: Int, t: Int, r: Int, b: Int) {
				avatarOverlay?.invalidate()
				@Suppress("DEPRECATION") super.invalidate(l, t, r, b)
			}
		}

		avatarImage?.setRoundRadius(AndroidUtilities.dp(32f))

		avatarDrawable.setInfo(firstName = null, lastName = null, fillColor = ResourcesCompat.getColor(context.resources, R.color.color_toggle_active, null))
		avatarDrawable.shouldDrawPlaceholder = false

		avatarImage?.setImageDrawable(avatarDrawable)
		avatarImage?.contentDescription = context.getString(R.string.ChoosePhoto)

		frameLayout.addView(avatarImage, LayoutHelper.createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 12f, if (LocaleController.isRTL) 16f else 0f, 12f))

		val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		paint.color = 0x55000000

		val overlaySize = 24.dp
		val overlayImage = SvgHelper.getBitmap(R.raw.camera_avatar, overlaySize, overlaySize, Color.WHITE)
		val overlayDrawRegion = RectF()
		avatarOverlay = object : View(context) {
			override fun onDraw(canvas: Canvas) {
				avatarImage?.imageReceiver?.let {
					if (it.hasNotThumb()) {
						paint.alpha = (0x00 * it.currentAlpha).toInt()
						canvas.drawCircle(measuredWidth / 2.0f, measuredHeight / 2.0f, measuredWidth / 2.0f, paint)
					}
					val offset = it.drawRegion.width().div(2) - overlaySize / 2
					overlayDrawRegion.set(it.drawRegion.left + offset, it.drawRegion.top + offset, it.drawRegion.right - offset, it.drawRegion.bottom - offset)
					canvas.drawBitmap(overlayImage, null, overlayDrawRegion, null)
				}
			}
		}

		avatarOverlay?.contentDescription = context.getString(R.string.ChatSetPhotoOrVideo)

		frameLayout.addView(avatarOverlay, LayoutHelper.createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 12f, if (LocaleController.isRTL) 16f else 0f, 12f))

		avatarOverlay?.setOnClickListener {
			imageUpdater.openMenu(avatar != null, {
				avatar = null
				avatarBig = null
				inputPhoto = null
				inputVideo = null
				inputVideoPath = null
				videoTimestamp = 0.0

				showAvatarProgress(show = false, animated = true)

				avatarImage?.setImage(null, null, avatarDrawable, null)
			}) {
				// unused
			}
		}

		avatarEditor = ImageView(context)
		avatarEditor?.isEnabled = false
		avatarEditor?.isClickable = false
		avatarEditor?.pivotX = 0f
		avatarEditor?.pivotY = 0f
		avatarEditor?.setImageResource(R.drawable.avatar_upload_placeholder)


		frameLayout.addView(avatarEditor, LayoutHelper.createFrame(60, 60f, Gravity.CENTER_VERTICAL or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 0f, if (LocaleController.isRTL) 38f else 0f, 0f))

		avatarProgressView = RadialProgressView(context)
		avatarProgressView?.setSize(AndroidUtilities.dp(30f))
		avatarProgressView?.setProgressColor(-0x1)
		avatarProgressView?.setNoProgress(false)

		frameLayout.addView(avatarProgressView, LayoutHelper.createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 12f, if (LocaleController.isRTL) 16f else 0f, 12f))

		showAvatarProgress(show = false, animated = false)

		nameTextView = EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT, false, withLineColors = true, emojiButtonIsRight = true)

		val channelNameText = context.getString(if (isCourse) R.string.online_course_name else R.string.channel_name)
		nameTextView?.setHint(channelNameText)

		if (nameToSet != null) {
			nameTextView?.text = nameToSet
			nameToSet = null
		}

		nameTextView?.setFilters(arrayOf(InputFilter.LengthFilter(100)))
		nameTextView?.editText?.isSingleLine = true
		nameTextView?.editText?.imeOptions = EditorInfo.IME_ACTION_NEXT

		nameTextView?.editText?.setOnEditorActionListener { _, i, _ ->
			if (i == EditorInfo.IME_ACTION_NEXT && !nameTextView?.editText?.text.isNullOrEmpty()) {
				bioContainer?.requestFocus()
				return@setOnEditorActionListener true
			}

			false
		}

		frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) 5f else 96f, 0f, if (LocaleController.isRTL) 96f else 5f, 0f))

		val spacerView = View(context)
		spacerView.setBackgroundResource(R.color.light_background)

		linearLayout?.addView(spacerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 24))
		spacerView.gone()

		bioContainer = GroupBioContainer(context)

		linearLayout?.addView(bioContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		helpTextView = TextView(context)
		helpTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		helpTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		helpTextView?.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		helpTextView?.text = context.getString(if (isCourse) R.string.DescriptionOlineCourseInfo else R.string.DescriptionInfo)

		linearLayout?.addView(helpTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 16, 0, 16, 4))

		adultBinding = NewMessageGroupTypeViewBinding.inflate(LayoutInflater.from(context))
		adultBinding?.checkbox?.maxLines = Int.MAX_VALUE
		adultBinding?.checkbox?.text = context.getString(R.string.not_for_kids_channel)
		adultBinding?.checkbox?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		adultBinding?.divider?.gone()

		adultBinding?.root?.setOnClickListener {
			adultBinding?.checkbox?.isChecked = true
			kidsBinding?.checkbox?.isChecked = false
		}

		linearLayout?.addView(adultBinding?.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 2, 0, 2, 0))

		kidsBinding = NewMessageGroupTypeViewBinding.inflate(LayoutInflater.from(context))
		kidsBinding?.checkbox?.maxLines = Int.MAX_VALUE
		kidsBinding?.checkbox?.text = context.getString(R.string.for_kids_channel)
		kidsBinding?.checkbox?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		kidsBinding?.divider?.gone()
		kidsBinding?.checkbox?.isChecked = true

		kidsBinding?.root?.setOnClickListener {
			adultBinding?.checkbox?.isChecked = false
			kidsBinding?.checkbox?.isChecked = true
		}

		linearLayout?.addView(kidsBinding?.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 2, 0, 2, 0))

		policyTextView = TextView(context)
		policyTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		policyTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))

		val ageTermsLink = ClickableString(source = context.getString(R.string.click_here), url = "age_terms", underline = false) {
			presentFragment(AgeRestrictionPolicyFragment())
		}

		val ageText = TextUtils.expandTemplate(context.getString(R.string.about_age_restriction_policy), ageTermsLink)

		policyTextView?.apply {
			text = ageText
			movementMethod = LinkMovementMethod.getInstance()
		}

		linearLayout?.addView(policyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 16, 16, 16, 0))

		categoryHeader = TextView(context)
		categoryHeader?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		categoryHeader?.setText(R.string.select_category)
		categoryHeader?.typeface = Theme.TYPEFACE_BOLD

		linearLayout?.addView(categoryHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 16, 16, 16, 0))

		categorySpinnerLayout = SpinnerLayoutBinding.inflate(LayoutInflater.from(context))

		categorySpinnerLayout?.spinner?.setOnItemClickListener { _, _, _, _ ->
			setCategoriesAdapter()
		}

		categorySpinnerLayout?.spinner?.doOnTextChanged { text, _, _, _ ->
			if (!text.isNullOrEmpty()) {
				categorySpinnerLayout?.spinnerLayout?.error = null
			}
		}

		linearLayout?.addView(categorySpinnerLayout?.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16f, 4f, 16f, 0f))

		setCategoriesAdapter()

		genreHeader = TextView(context)
		genreHeader?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		genreHeader?.setText(R.string.select_genre)
		genreHeader?.typeface = Theme.TYPEFACE_BOLD
		genreHeader?.gone()

		linearLayout?.addView(genreHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 16, 16, 16, 0))

		genreSpinnerLayout = SpinnerLayoutBinding.inflate(LayoutInflater.from(context))
		genreSpinnerLayout?.root?.gone()

		genreSpinnerLayout?.spinner?.setOnItemClickListener { _, _, _, _ ->
			setGenresAdapters()
		}

		linearLayout?.addView(genreSpinnerLayout?.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16f, 4f, 16f, 0f))

		subgenreHeader = TextView(context)
		subgenreHeader?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		subgenreHeader?.setText(R.string.select_sub_genre)
		subgenreHeader?.typeface = Theme.TYPEFACE_BOLD
		subgenreHeader?.gone()

		linearLayout?.addView(subgenreHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 16, 16, 16, 0))

		subgenreSpinnerLayout = SpinnerLayoutBinding.inflate(LayoutInflater.from(context))
		subgenreSpinnerLayout?.root?.gone()

		subgenreSpinnerLayout?.spinner?.setOnItemClickListener { _, _, _, _ ->
			setSubgenresAdapter()
		}

		linearLayout?.addView(subgenreSpinnerLayout?.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16f, 4f, 16f, 0f))

		setGenresAdapters()

		countryHeader = TextView(context)
		countryHeader?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		countryHeader?.setText(R.string.channel_country)
		countryHeader?.typeface = Theme.TYPEFACE_BOLD

		linearLayout?.addView(countryHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 16, 16, 16, 0))

		countrySpinnerLayout = SpinnerLayoutBinding.inflate(LayoutInflater.from(context))

		countrySpinnerLayout?.spinner?.setOnItemClickListener { _, _, _, _ ->
			setCountriesAdapter()
		}

		countrySpinnerLayout?.spinner?.doOnTextChanged { text, _, _, _ ->
			if (!text.isNullOrEmpty()) {
				countrySpinnerLayout?.spinnerLayout?.error = null
			}
		}

		linearLayout?.addView(countrySpinnerLayout?.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16f, 4f, 16f, 16f))

		setCountriesAdapter()

		categorySpinnerLayout?.spinner?.addTextChangedListener {
			val category = it?.toString()?.trim()?.lowercase()

			if (category == "music") {
				genreHeader?.visible()
				genreSpinnerLayout?.root?.visible()
				subgenreHeader?.visible()
				subgenreSpinnerLayout?.root?.visible()
			}
			else {
				genreHeader?.gone()
				genreSpinnerLayout?.root?.gone()
				subgenreHeader?.gone()
				subgenreSpinnerLayout?.root?.gone()
			}
		}

		return fragmentView
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
				avatarEditor?.setImageDrawable(null)
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
		imageUpdater.onActivityResult(requestCode, resultCode, data)
	}

	override fun saveSelfArgs(args: Bundle) {
		imageUpdater.currentPicturePath?.let {
			args.putString("path", it)
		}

		nameTextView?.text?.toString()?.let {
			args.putString("nameTextView", it)
		}
	}

	override fun restoreSelfArgs(args: Bundle) {
		imageUpdater.currentPicturePath = args.getString("path")

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

				if (inputPhoto != null || inputVideo != null) {
					messagesController.changeChatAvatar(chatId, null, inputPhoto, inputVideo, videoTimestamp, inputVideoPath, avatar, avatarBig, null)
				}

				val tlUsers = users?.takeIf { it.isNotEmpty() }?.map {
					messagesController.getInputUser(messagesController.getUser(it))
				}

				if (!tlUsers.isNullOrEmpty()) {
					messagesController.addUsersToChannel(chatId, ArrayList(tlUsers), null)
				}

				val req = TLRPC.TL_messages_getExportedChatInvites()
				req.peer = messagesController.getInputPeer(-chatId)
				req.admin_id = messagesController.getInputUser(userConfig.getCurrentUser())
				req.limit = 1

				connectionsManager.sendRequest(req)

				notificationCenter.postNotificationName(NotificationCenter.closeChats)

				inviteLink?.let {
					messagesController.updateChannelUserName(chatId, it)
				}

				val parentLayout = parentLayout

				val args2 = Bundle()
				args2.putLong("chat_id", chatId)

				presentFragment(ChatActivity(args2), false)

				parentLayout?.postDelayed({
					parentLayout.removeFragmentsBetween(0, (parentLayout.fragmentsStack?.size ?: 0) - 1)
				}, 300)
			}
		}
	}

	private fun setCountriesAdapter() {
		val countries = countries ?: return
		val context = context ?: return
		val countriesAdapter = CountriesAdapter(context, R.layout.country_view_holder, countries)
		countrySpinnerLayout?.spinner?.setAdapter(countriesAdapter)
	}

	private fun setCategoriesAdapter() {
		val categories = categories ?: return
		val context = context ?: return
		val categoriesAdapter = ChannelCategoriesAdapter(context, R.layout.country_view_holder, categories)
		categorySpinnerLayout?.spinner?.setAdapter(categoriesAdapter)
	}

	private fun setGenresAdapters() {
		val genres = genres ?: return
		val context = context ?: return
		val genresAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, genres)
		genreSpinnerLayout?.spinner?.setAdapter(genresAdapter)
	}

	private fun setSubgenresAdapter() {
		val genres = genres ?: return
		val context = context ?: return
		val subgenresAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, genres)
		subgenreSpinnerLayout?.spinner?.setAdapter(subgenresAdapter)
	}

	private fun loadCountries() {
		CountriesDataSource.instance.loadCountries { countries, error ->
			if (error == null && countries != null) {
				this.countries = countries
				setCountriesAdapter()
			}
			else {
				Toast.makeText(context, R.string.failed_to_load_countries, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun loadCategories() {
		val req = ElloRpc.channelCategoriesRequest()

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				if (error == null) {
					if (response is TLRPC.TL_biz_dataRaw) {
						val res = response.readData<ElloRpc.ChannelCategoriesResponse>()

						if (res?.categories != null) {
							categories = res.categories.sorted()
							setCategoriesAdapter()
						}
					}
				}
				else {
					Toast.makeText(context, R.string.failed_to_load_categories, Toast.LENGTH_SHORT).show()
				}
			}
		}, ConnectionsManager.RequestFlagWithoutLogin or ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun loadGenres() {
		val req = ElloRpc.channelMusicGenresRequest()

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				if (error == null) {
					if (response is TLRPC.TL_biz_dataRaw) {
						val res = response.readData<ElloRpc.GenresResponse>()

						if (res?.genres != null) {
							genres = res.genres.map { it.name }.sorted()
							setGenresAdapters()
							setSubgenresAdapter()
						}
					}
				}
				else {
					Toast.makeText(context, R.string.failed_to_load_genres, Toast.LENGTH_SHORT).show()
				}
			}
		}, ConnectionsManager.RequestFlagWithoutLogin or ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	companion object {
		private const val BUTTON_DONE = 1
	}
}
