/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.group

import android.animation.*
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.text.InputFilter.LengthFilter
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.buildSpannedString
import androidx.core.view.children
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.*
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.vibrate
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.GroupBioContainer
import org.telegram.ui.Cells.GroupCreateUserCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.*
import org.telegram.ui.Components.ImageUpdater.ImageUpdaterDelegate
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.LocationActivity
import java.util.concurrent.CountDownLatch
import kotlin.math.min

class GroupCreateFinalActivity(args: Bundle) : BaseFragment(args), NotificationCenterDelegate, ImageUpdaterDelegate {
	private var helpTextView: TextView? = null
	private var adapter: GroupCreateAdapter? = null
	private var listView: RecyclerListView? = null
	private var editText: EditTextEmoji? = null
	private var bioContainer: GroupBioContainer? = null
	private var avatarImage: BackupImageView? = null
	private var avatarOverlay: View? = null
	private var avatarEditor: ImageView? = null
	private var editItem: ActionBarMenuItem? = null
	private var progressBar: ContentLoadingProgressBar? = null
	private var avatarAnimation: AnimatorSet? = null
	private var avatarProgressView: RadialProgressView? = null
	private val avatarDrawable: AvatarDrawable
	private var editTextContainer: FrameLayout? = null
	private var avatar: TLRPC.FileLocation? = null
	private var avatarBig: TLRPC.FileLocation? = null
	private var inputPhoto: TLRPC.InputFile? = null
	private var inputVideo: TLRPC.InputFile? = null
	private var inputVideoPath: String? = null
	private var videoTimestamp = 0.0
	private var selectedContacts: ArrayList<Long>? = null
	private var createAfterUpload = false
	private var donePressed = false
	private var imageUpdater: ImageUpdater? = null
	private var nameToSet: String?
	private val chatType: Int
	private val forImport: Boolean
	private var currentGroupCreateAddress: String?
	private val currentGroupCreateLocation: Location?
	private var reqId = 0

	interface GroupCreateFinalActivityDelegate {
		fun didStartChatCreation()
		fun didFinishChatCreation(fragment: GroupCreateFinalActivity?, chatId: Long)
		fun didFailChatCreation()
	}

	private var delegate: GroupCreateFinalActivityDelegate? = null

	init {
		chatType = args.getInt("chatType", ChatObject.CHAT_TYPE_CHAT)
		avatarDrawable = AvatarDrawable()
		avatarDrawable.shouldDrawPlaceholder = false
		currentGroupCreateAddress = args.getString("address")

		currentGroupCreateLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			args.getParcelable("location", Location::class.java)
		}
		else {
			@Suppress("DEPRECATION") args.getParcelable("location")
		}

		forImport = args.getBoolean("forImport", false)
		nameToSet = args.getString("title", null)
	}

	override fun onFragmentCreate(): Boolean {
		notificationCenter.let {
			it.addObserver(this, NotificationCenter.updateInterfaces)
			it.addObserver(this, NotificationCenter.chatDidCreated)
			it.addObserver(this, NotificationCenter.chatInfoDidLoad)
			it.addObserver(this, NotificationCenter.chatDidFailCreate)
		}

		imageUpdater = ImageUpdater(false).also {
			it.parentFragment = this
			it.setDelegate(this)
		}

		val contacts = getArguments()?.getLongArray("result")

		if (contacts != null) {
			selectedContacts = ArrayList(contacts.toList())
		}

		val usersToLoad = selectedContacts?.mapNotNull {
			val user = messagesController.getUser(it)
			if (user == null || user.username.isNullOrEmpty()) it else null
		}

		if (!usersToLoad.isNullOrEmpty()) {
			val countDownLatch = CountDownLatch(1)
			val users = ArrayList<User>()

			messagesStorage.storageQueue.postRunnable {
				val loadedUsers = messagesStorage.getUsers(ArrayList(usersToLoad))

				val emptyUsers = loadedUsers.filter { it.username.isNullOrEmpty() }.toSet()
				loadedUsers.removeAll(emptyUsers)

				val filledUsers = emptyUsers.mapNotNull {
					messagesController.getUserFull(it.id)?.user
				}

				loadedUsers.addAll(filledUsers)

				users.addAll(loadedUsers)

				countDownLatch.countDown()
			}

			try {
				countDownLatch.await()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			if (usersToLoad.size != users.size) {
				return false
			}

			if (users.isNotEmpty()) {
				for (user in users) {
					messagesController.putUser(user, true)
				}
			}
			else {
				return false
			}
		}

		return super.onFragmentCreate()
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		notificationCenter.let {
			it.removeObserver(this, NotificationCenter.updateInterfaces)
			it.removeObserver(this, NotificationCenter.chatDidCreated)
			it.removeObserver(this, NotificationCenter.chatDidFailCreate)
		}

		imageUpdater?.clear()

		if (reqId != 0) {
			connectionsManager.cancelRequest(reqId, true)
		}

		editText?.onDestroy()

		AndroidUtilities.removeAdjustResize(parentActivity, classGuid)
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun onResume() {
		super.onResume()
		editText?.onResume()
		adapter?.notifyDataSetChanged()
		imageUpdater?.onResume()
		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
	}

	override fun onPause() {
		super.onPause()
		editText?.onPause()
		imageUpdater?.onPause()
	}

	override fun dismissCurrentDialog() {
		if (imageUpdater?.dismissCurrentDialog(visibleDialog) == true) {
			return
		}

		super.dismissCurrentDialog()
	}

	override fun dismissDialogOnPause(dialog: Dialog): Boolean {
		return imageUpdater?.dismissDialogOnPause(dialog) == true && super.dismissDialogOnPause(dialog)
	}

	override fun onRequestPermissionsResultFragment(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		imageUpdater?.onRequestPermissionsResultFragment(requestCode, permissions, grantResults)
	}

	override fun onBackPressed(): Boolean {
		if (editText?.isPopupShowing == true) {
			editText?.hidePopup(true)
			return false
		}

		return true
	}

	override fun hideKeyboardOnShow(): Boolean {
		return false
	}

	override fun createView(context: Context): View? {
		editText?.onDestroy()

		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)
		actionBar?.setTitle(context.getString(R.string.NewGroup))

		val menu = actionBar?.createMenu()

		editItem = menu?.addItem(BUTTON_DONE, buildSpannedString {
			val buttonText = context.getString(R.string.Create)
			append(buttonText)
			setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), 0, buttonText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.brand)), 0, buttonText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
		})

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}

					BUTTON_DONE -> {
						if (donePressed) {
							return
						}

						val title = editText?.text?.toString()

						if (title.isNullOrEmpty()) {
							parentActivity?.vibrate()
							AndroidUtilities.shakeView(editText, 2f, 0)
							return
						}

						donePressed = true

						AndroidUtilities.hideKeyboard(editText)

						editText?.isEnabled = false

						if (imageUpdater?.isUploadingImage == true) {
							createAfterUpload = true
						}
						else {
							needShowProgress(true)
							delegate?.didStartChatCreation()
							reqId = messagesController.createChat(title, selectedContacts ?: emptyList(), bioContainer?.text, chatType, forImport, currentGroupCreateLocation, currentGroupCreateAddress, this@GroupCreateFinalActivity, false, TLRPC.Chat.PAY_TYPE_NONE, null, 0.0, null, null)
						}
					}
				}
			}
		})

		val sizeNotifierFrameLayout = object : SizeNotifierFrameLayout(context) {
			private var ignoreLayout = false

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val widthSize = MeasureSpec.getSize(widthMeasureSpec)
				var heightSize = MeasureSpec.getSize(heightMeasureSpec)

				setMeasuredDimension(widthSize, heightSize)
				heightSize -= paddingTop
				measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0)

				val keyboardSize = measureKeyboardHeight()

				if (keyboardSize > AndroidUtilities.dp(20f) && editText?.isPopupShowing != true) {
					ignoreLayout = true
					editText?.hideEmojiView()
					ignoreLayout = false
				}

				val childCount = childCount

				for (i in 0 until childCount) {
					val child = getChildAt(i)

					if (child == null || child.visibility == GONE || child === actionBar) {
						continue
					}

					if (editText?.isPopupView(child) == true) {
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
				val paddingBottom = if (keyboardSize <= AndroidUtilities.dp(20f) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) (editText?.emojiPadding ?: 0) else 0

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

					if (editText?.isPopupView(child) == true) {
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

		fragmentView = sizeNotifierFrameLayout
		fragmentView?.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
		fragmentView?.setOnTouchListener { _, _ -> true }

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.VERTICAL

		sizeNotifierFrameLayout.addView(linearLayout, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		editTextContainer = FrameLayout(context)

		linearLayout.addView(editTextContainer, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		avatarImage = object : BackupImageView(context) {
			override fun invalidate() {
				avatarOverlay?.invalidate()
				super.invalidate()
			}

			@Deprecated("Deprecated in Java", ReplaceWith("invalidate()"))
			override fun invalidate(l: Int, t: Int, r: Int, b: Int) {
				invalidate()
			}
		}

		avatarImage?.setRoundRadius(AndroidUtilities.dp(32f))

		avatarDrawable.setInfo(firstName = null, lastName = null, fillColor = ResourcesCompat.getColor(context.resources, R.color.color_toggle_active, null))

		avatarImage?.setImageDrawable(avatarDrawable)
		avatarImage?.contentDescription = context.getString(R.string.ChoosePhoto)

		editTextContainer?.addView(avatarImage, createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 16f, if (LocaleController.isRTL) 16f else 0f, 0f))

		val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		paint.color = 0x55000000

		avatarOverlay = object : View(context) {
			override fun onDraw(canvas: Canvas) {
				val avatarImage = avatarImage ?: return
				val avatarProgressView = avatarProgressView ?: return

				if (avatarProgressView.visibility == VISIBLE) {
					paint.alpha = (0x55 * avatarImage.imageReceiver.currentAlpha * avatarProgressView.alpha).toInt()
					canvas.drawCircle(measuredWidth / 2.0f, measuredHeight / 2.0f, measuredWidth / 2.0f, paint)
				}
			}
		}

		editTextContainer?.addView(avatarOverlay, createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 16f, if (LocaleController.isRTL) 16f else 0f, 16f))

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


		editTextContainer?.addView(avatarEditor, createFrame(60, 60f, Gravity.CENTER_VERTICAL or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 0f, if (LocaleController.isRTL) 38f else 0f, 0f))

		avatarProgressView = object : RadialProgressView(context) {
			override fun setAlpha(alpha: Float) {
				super.setAlpha(alpha)
				avatarOverlay?.invalidate()
			}
		}

		avatarProgressView?.setSize(AndroidUtilities.dp(30f))
		avatarProgressView?.setProgressColor(-0x1)
		avatarProgressView?.setNoProgress(false)

		editTextContainer?.addView(avatarProgressView, createFrame(64, 64f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, if (LocaleController.isRTL) 0f else 16f, 16f, if (LocaleController.isRTL) 16f else 0f, 0f))

		showAvatarProgress(show = false, animated = false)

		editText = EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT, false, withLineColors = true)
		editText?.setHint(if (chatType == ChatObject.CHAT_TYPE_CHAT || chatType == ChatObject.CHAT_TYPE_MEGAGROUP) context.getString(R.string.EnterGroupNamePlaceholder) else context.getString(R.string.EnterListName))

		if (nameToSet != null) {
			editText?.text = nameToSet
			nameToSet = null
		}

		editText?.setFilters(arrayOf(LengthFilter(100)))

		editTextContainer?.addView(editText, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) 5f else 96f, 0f, if (LocaleController.isRTL) 96f else 5f, 0f))

		val spacerView = View(context)
		spacerView.setBackgroundResource(R.color.light_background)

		linearLayout.addView(spacerView, createLinear(LayoutHelper.MATCH_PARENT, 24))
		spacerView.gone()

		bioContainer = GroupBioContainer(context)

		linearLayout.addView(bioContainer, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		helpTextView = TextView(context)
		helpTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		helpTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		helpTextView?.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		helpTextView?.text = context.getString(R.string.DescriptionNewGroup)

		linearLayout.addView(helpTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 16, 0, 16, 4))

		listView = RecyclerListView(context)
		listView?.adapter = GroupCreateAdapter(context).also { adapter = it }
		listView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
		listView?.isVerticalScrollBarEnabled = false
		listView?.verticalScrollbarPosition = if (LocaleController.isRTL) View.SCROLLBAR_POSITION_LEFT else View.SCROLLBAR_POSITION_RIGHT

		val decoration = GroupCreateDividerItemDecoration()
		decoration.setSkipRows(if (currentGroupCreateAddress != null) 4 else 1)

		listView?.addItemDecoration(decoration)

		linearLayout.addView(listView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))

		listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					AndroidUtilities.hideKeyboard(editText)
				}
			}
		})

		listView?.setOnItemClickListener { view, _ ->
			if (view is TextSettingsCell) {
				if (!AndroidUtilities.isMapsInstalled(this@GroupCreateFinalActivity)) {
					return@setOnItemClickListener
				}

				val fragment = LocationActivity(LocationActivity.LOCATION_TYPE_GROUP)

				fragment.setDialogId(0)

				fragment.setDelegate { location, _, _, _ ->
					currentGroupCreateLocation?.latitude = location.geo.lat
					currentGroupCreateLocation?.longitude = location.geo._long

					currentGroupCreateAddress = location.address
				}

				presentFragment(fragment)
			}
		}

		progressBar = ContentLoadingProgressBar(context)
		progressBar?.gone()
		progressBar?.isIndeterminate = true

		sizeNotifierFrameLayout.addView(progressBar, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP))

		return fragmentView
	}

	override fun onUploadProgressChanged(progress: Float) {
		avatarProgressView?.setProgress(progress)
	}

	override fun didStartUpload(isVideo: Boolean) {
		avatarProgressView?.setProgress(0f)
	}

	override fun didUploadPhoto(photo: TLRPC.InputFile?, video: TLRPC.InputFile?, videoStartTimestamp: Double, videoPath: String?, bigSize: TLRPC.PhotoSize?, smallSize: TLRPC.PhotoSize?) {
		AndroidUtilities.runOnUIThread {
			if (photo != null || video != null) {
				inputPhoto = photo
				inputVideo = video
				inputVideoPath = videoPath
				videoTimestamp = videoStartTimestamp

				if (createAfterUpload) {
					delegate?.didStartChatCreation()
					reqId = messagesController.createChat(editText!!.text.toString(), selectedContacts ?: emptyList(), bioContainer?.text, chatType, forImport, currentGroupCreateLocation, currentGroupCreateAddress, this@GroupCreateFinalActivity, false, TLRPC.Chat.PAY_TYPE_NONE, null, 0.0, null, null)
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
		return editText?.text?.toString()
	}

	fun setDelegate(groupCreateFinalActivityDelegate: GroupCreateFinalActivityDelegate?) {
		delegate = groupCreateFinalActivityDelegate
	}

	private fun showAvatarProgress(show: Boolean, animated: Boolean) {
		if (avatarEditor == null) {
			return
		}

		avatarAnimation?.cancel()
		avatarAnimation = null

		if (animated) {
			avatarAnimation = AnimatorSet()

			if (show) {
				avatarProgressView?.visibility = View.VISIBLE
				avatarAnimation?.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 0.0f), ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f))
			}
			else {
				avatarEditor?.visibility = View.VISIBLE
				avatarAnimation?.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 1.0f), ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f))
			}

			avatarAnimation?.duration = 180

			avatarAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (avatarAnimation == null || avatarEditor == null) {
						return
					}

					if (show) {
						avatarEditor?.visibility = View.INVISIBLE
					}
					else {
						avatarProgressView?.visibility = View.INVISIBLE
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
				avatarEditor?.visibility = View.INVISIBLE
				avatarProgressView?.alpha = 1.0f
				avatarProgressView?.visibility = View.VISIBLE
			}
			else {
				avatarEditor?.alpha = 1.0f
				avatarEditor?.visibility = View.VISIBLE
				avatarProgressView?.alpha = 0.0f
				avatarProgressView?.visibility = View.INVISIBLE
			}
		}
	}

	override fun onActivityResultFragment(requestCode: Int, resultCode: Int, data: Intent?) {
		imageUpdater?.onActivityResult(requestCode, resultCode, data)
	}

	override fun saveSelfArgs(args: Bundle) {
		imageUpdater?.currentPicturePath?.let {
			args.putString("path", it)
		}

		editText?.text?.toString()?.takeIf { it.isNotEmpty() }?.let {
			args.putString("nameTextView", it)
		}
	}

	override fun restoreSelfArgs(args: Bundle) {
		imageUpdater?.currentPicturePath = args.getString("path")

		val text = args.getString("nameTextView")

		if (text != null) {
			if (editText != null) {
				editText?.text = text
			}
			else {
				nameToSet = text
			}
		}
	}

	public override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
		if (isOpen) {
			editText?.openKeyboard()
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.updateInterfaces -> {
				val listView = listView ?: return
				val mask = args[0] as Int

				if (mask and MessagesController.UPDATE_MASK_AVATAR != 0 || mask and MessagesController.UPDATE_MASK_NAME != 0 || mask and MessagesController.UPDATE_MASK_STATUS != 0) {
					listView.children.filterIsInstance<GroupCreateUserCell>().forEach { it.update(mask) }
				}
			}

			NotificationCenter.chatDidFailCreate -> {
				reqId = 0
				donePressed = false
				needShowProgress(false)
				editText?.isEnabled = true
				delegate?.didFailChatCreation()
			}

			NotificationCenter.chatDidCreated -> {
				reqId = 0

				val chatId = args[0] as Long

				delegate?.didFinishChatCreation(this, chatId) ?: run {
					notificationCenter.postNotificationName(NotificationCenter.closeChats)
					val args2 = Bundle()
					args2.putLong("chat_id", chatId)
					presentFragment(ChatActivity(args2), true)
				}

				if (inputPhoto != null || inputVideo != null) {
					messagesController.changeChatAvatar(chatId, null, inputPhoto, inputVideo, videoTimestamp, inputVideoPath, avatar, avatarBig, null)
				}

				if (chatType != ChatObject.CHAT_TYPE_MEGAGROUP) {
					val about = bioContainer?.text

					if (!about.isNullOrEmpty()) {
						messagesController.updateChatAbout(chatId, about, null)
					}
				}
			}
		}
	}

	inner class GroupCreateAdapter(private val context: Context) : SelectionAdapter() {
		private var usersStartRow = 0

		override fun getItemCount(): Int {
			var count = 1 + (selectedContacts?.size ?: 0)

			if (currentGroupCreateAddress != null) {
				count += 2
			}

			return count
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType == TYPE_SETTINGS
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = when (viewType) {
				TYPE_HEADER -> {
					HeaderCell(context).also {
						it.height = 46
					}
				}

				TYPE_USER -> {
					GroupCreateUserCell(context, 0, 3, false)
				}

				TYPE_SETTINGS -> {
					TextSettingsCell(context)
				}

				else -> {
					TextSettingsCell(context)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				TYPE_HEADER -> {
					val cell = holder.itemView as HeaderCell

					if (currentGroupCreateAddress != null && position == 0) {
						cell.setText(context.getString(R.string.AttachLocation))
					}
					else {
						cell.setText(LocaleController.formatPluralString("Members", selectedContacts!!.size))
					}
				}

				TYPE_USER -> {
					val cell = holder.itemView as GroupCreateUserCell
					var user = messagesController.getUser(selectedContacts!![position - usersStartRow])

					if (user == null || user.username.isNullOrEmpty()) {
						user = messagesController.getUserFull(selectedContacts!![position - usersStartRow])?.user
					}

					cell.setObject(user, null, null)
				}

				TYPE_SETTINGS -> {
					val cell = holder.itemView as TextSettingsCell
					cell.setText(currentGroupCreateAddress, false)
				}
			}
		}

		override fun getItemViewType(position: Int): Int {
			@Suppress("NAME_SHADOWING") var position = position

			if (currentGroupCreateAddress != null) {
				position -= when (position) {
					0 -> return TYPE_HEADER
					1 -> return TYPE_SETTINGS
					else -> 1
				}

				usersStartRow = 4
			}
			else {
				usersStartRow = 1
			}

			return when (position) {
				0 -> TYPE_HEADER
				1 -> TYPE_USER
				else -> TYPE_USER
			}
		}

		override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
			if (holder.itemViewType == TYPE_USER) {
				(holder.itemView as GroupCreateUserCell).recycle()
			}
		}
	}

	private fun needShowProgress(show: Boolean) {
		editItem?.isEnabled = show
		progressBar?.visibility = if (show) View.VISIBLE else View.GONE
	}

	companion object {
		private const val BUTTON_DONE = 1
		private const val TYPE_HEADER = 1
		private const val TYPE_USER = 2
		private const val TYPE_SETTINGS = 3
	}
}
