/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Afandiyev Shamil, Ello 2024
 */
package org.telegram.ui

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.text.Editable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Emoji
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.vibrate
import org.telegram.tgnet.TLRPC.TL_chatInviteExported
import org.telegram.tgnet.TLRPC.TL_messages_editExportedChatInvite
import org.telegram.tgnet.TLRPC.TL_messages_exportChatInvite
import org.telegram.tgnet.TLRPC.TL_messages_exportedChatInvite
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.SlideChooseView
import kotlin.math.max

@SuppressLint("AppCompatCustomView")
class LinkEditActivity @JvmOverloads constructor(private val type: Int, private val chatId: Long, private val isUser: Boolean = false) : BaseFragment() {
	private val defaultDates = intArrayOf(3600, 3600 * 24, 3600 * 24 * 7)
	private val defaultUses = intArrayOf(1, 10, 100)
	private val displayedDates = ArrayList<Int>()
	private val displayedUses = ArrayList<Int>()
	private var approveCell: TextCheckCell? = null
	private var buttonTextView: TextView? = null
	private var callback: Callback? = null
	private var currentInviteDate = 0
	private var divider: TextInfoPrivacyCell? = null
	private var dividerUses: TextInfoPrivacyCell? = null
	private var finished = false
	private var firstLayout = true
	private var ignoreSet = false
	private var inviteToEdit: TL_chatInviteExported? = null
	private var nameEditText: EditText? = null
	private var scrollView: ScrollView? = null
	private var timeChooseView: SlideChooseView? = null
	private var timeEditText: TextView? = null
	private var usesChooseView: SlideChooseView? = null
	private var usesEditText: EditText? = null
	private var usesHeaderCell: HeaderCell? = null
	var loading = false
	var progressDialog: AlertDialog? = null
	var scrollToEnd = false
	var scrollToStart = false

	override fun createView(context: Context): View {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		if (type == CREATE_TYPE) {
			actionBar?.setTitle(context.getString(R.string.NewLink))
		}
		else if (type == EDIT_TYPE) {
			actionBar?.setTitle(context.getString(R.string.EditLink))
		}

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
					AndroidUtilities.hideKeyboard(usesEditText)
				}
			}
		})

		val createTextView = TextView(context)
		createTextView.ellipsize = TextUtils.TruncateAt.END
		createTextView.gravity = Gravity.CENTER_VERTICAL
		createTextView.setOnClickListener { onCreateClicked() }
		createTextView.setSingleLine()

		if (type == CREATE_TYPE) {
			createTextView.text = context.getString(R.string.CreateLinkHeader)
		}
		else if (type == EDIT_TYPE) {
			createTextView.text = context.getString(R.string.SaveLinkHeader)
		}

		createTextView.setTextColor(context.getColor(R.color.text))
		createTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		createTextView.typeface = Theme.TYPEFACE_BOLD
		createTextView.setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(8f), AndroidUtilities.dp(18f), AndroidUtilities.dp(8f))

		val topSpace = if (actionBar?.occupyStatusBar == true) AndroidUtilities.statusBarHeight / AndroidUtilities.dp(2f) else 0

		actionBar?.addView(createTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.END or Gravity.CENTER_VERTICAL, 0f, topSpace.toFloat(), 0f, 0f))

		scrollView = ScrollView(context)

		val contentView: SizeNotifierFrameLayout = object : SizeNotifierFrameLayout(context) {
			var oldKeyboardHeight = 0

			override fun createAdjustPanLayoutHelper(): AdjustPanLayoutHelper {
				val panLayoutHelper: AdjustPanLayoutHelper = object : AdjustPanLayoutHelper(this) {
					override fun onTransitionStart(keyboardVisible: Boolean, contentHeight: Int) {
						super.onTransitionStart(keyboardVisible, contentHeight)
						scrollView?.layoutParams?.height = contentHeight
					}

					override fun onTransitionEnd() {
						super.onTransitionEnd()
						scrollView?.layoutParams?.height = LinearLayout.LayoutParams.MATCH_PARENT
						scrollView?.requestLayout()
					}

					override fun onPanTranslationUpdate(y: Float, progress: Float, keyboardVisible: Boolean) {
						super.onPanTranslationUpdate(y, progress, keyboardVisible)
						translationY = 0f
					}

					override fun heightAnimationEnabled(): Boolean {
						return !finished
					}
				}

				panLayoutHelper.setCheckHierarchyHeight(true)

				return panLayoutHelper
			}

			override fun onAttachedToWindow() {
				super.onAttachedToWindow()
				adjustPanLayoutHelper?.onAttach()
			}

			override fun onDetachedFromWindow() {
				super.onDetachedFromWindow()
				adjustPanLayoutHelper?.onDetach()
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec)

				measureKeyboardHeight()

				val isNeedScrollToEnd = usesEditText?.isCursorVisible == true || nameEditText?.isCursorVisible == true

				if (oldKeyboardHeight != keyboardHeight && keyboardHeight > AndroidUtilities.dp(20f) && isNeedScrollToEnd) {
					scrollToEnd = true
					invalidate()
				}
				else if (scrollView!!.scrollY == 0 && !isNeedScrollToEnd) {
					scrollToStart = true
					invalidate()
				}

				if (keyboardHeight != 0 && keyboardHeight < AndroidUtilities.dp(20f)) {
					usesEditText?.clearFocus()
					nameEditText?.clearFocus()
				}

				oldKeyboardHeight = keyboardHeight
			}

			override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
				val scrollY = scrollView!!.scrollY
				super.onLayout(changed, l, t, r, b)

				if (scrollY != scrollView?.scrollY && !scrollToEnd) {
					scrollView?.translationY = (scrollView!!.scrollY - scrollY).toFloat()
					scrollView?.animate()?.cancel()
					scrollView?.animate()?.translationY(0f)?.setDuration(AdjustPanLayoutHelper.keyboardDuration)?.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator)?.start()
				}
			}

			override fun dispatchDraw(canvas: Canvas) {
				super.dispatchDraw(canvas)

				if (scrollToEnd) {
					scrollToEnd = false
					scrollView?.smoothScrollTo(0, max(0, scrollView!!.getChildAt(0).measuredHeight - scrollView!!.measuredHeight))
				}
				else if (scrollToStart) {
					scrollToStart = false
					scrollView?.smoothScrollTo(0, 0)
				}
			}
		}

		fragmentView = contentView

		val linearLayout = object : LinearLayout(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec)

				var elementsHeight = 0
				val h = MeasureSpec.getSize(heightMeasureSpec)

				for (i in 0 until childCount) {
					val child = getChildAt(i)

					if (child !== buttonTextView && child.visibility != GONE) {
						elementsHeight += child.measuredHeight
					}
				}

				val topMargin: Int
				val buttonH = AndroidUtilities.dp(48f) + AndroidUtilities.dp(24f) + AndroidUtilities.dp(16f)

				topMargin = if (elementsHeight >= h - buttonH) {
					AndroidUtilities.dp(24f)
				}
				else {
					AndroidUtilities.dp(24f) + (h - buttonH) - elementsHeight
				}

				if ((buttonTextView!!.layoutParams as LayoutParams).topMargin != topMargin) {
					val oldMargin = (buttonTextView!!.layoutParams as LayoutParams).topMargin

					(buttonTextView!!.layoutParams as LayoutParams).topMargin = topMargin

					if (!firstLayout) {
						buttonTextView?.translationY = (oldMargin - topMargin).toFloat()
						buttonTextView?.animate()?.translationY(0f)?.setDuration(AdjustPanLayoutHelper.keyboardDuration)?.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator)?.start()
					}

					super.onMeasure(widthMeasureSpec, heightMeasureSpec)
				}
			}

			override fun dispatchDraw(canvas: Canvas) {
				super.dispatchDraw(canvas)
				firstLayout = false
			}
		}

		val transition = LayoutTransition()
		transition.setDuration(100)

		linearLayout.layoutTransition = transition
		linearLayout.orientation = LinearLayout.VERTICAL

		scrollView?.addView(linearLayout)

		buttonTextView = TextView(context)
		buttonTextView?.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
		buttonTextView?.gravity = Gravity.CENTER
		buttonTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		buttonTextView?.typeface = Theme.TYPEFACE_BOLD

		if (type == CREATE_TYPE) {
			buttonTextView?.text = context.getString(R.string.CreateLink)
		}
		else if (type == EDIT_TYPE) {
			buttonTextView?.text = context.getString(R.string.SaveLink)
		}

		approveCell = object : TextCheckCell(context) {
			override fun onDraw(canvas: Canvas) {
				canvas.save()
				canvas.clipRect(0, 0, width, height)
				super.onDraw(canvas)
				canvas.restore()
			}
		}

		approveCell?.setBackgroundColor(context.getColor(R.color.dark_gray))
		approveCell?.setDrawCheckRipple(true)
		approveCell?.height = 56
		approveCell?.setTextAndCheck(context.getString(R.string.ApproveNewMembers), false, false)
		approveCell?.setTypeface(Theme.TYPEFACE_BOLD)

		approveCell?.setOnClickListener {
			val cell = it as TextCheckCell
			val newIsChecked = !cell.isChecked
			cell.setBackgroundColorAnimated(newIsChecked, if (newIsChecked) context.getColor(R.color.brand_transparent) else context.getColor(R.color.dark_gray))
			cell.isChecked = newIsChecked
			setUsesVisible(!newIsChecked)
			firstLayout = true
		}

		// MARK: uncomment to enable "Approve new members" feature
//		if (!isUser) {
//			linearLayout.addView(approveCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56))
//			val hintCell = TextInfoPrivacyCell(context)
//			hintCell.background = Theme.getThemedDrawable(context, R.drawable.greydivider, context.getColor(R.color.shadow))
//			hintCell.setText(context.getString(R.string.ApproveNewMembersDescription))
//			linearLayout.addView(hintCell)
//		}

		val timeHeaderCell = HeaderCell(context)
		timeHeaderCell.setText(context.getString(R.string.LimitByPeriod))

		linearLayout.addView(timeHeaderCell)

		timeChooseView = SlideChooseView(context)

		linearLayout.addView(timeChooseView)

		timeEditText = TextView(context)
		timeEditText?.setPadding(AndroidUtilities.dp(22f), 0, AndroidUtilities.dp(22f), 0)
		timeEditText?.gravity = Gravity.CENTER_VERTICAL
		timeEditText?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		timeEditText?.hint = context.getString(R.string.TimeLimitHint)

		timeEditText?.setOnClickListener {
			AlertsCreator.createDatePickerDialog(context, -1) { _, scheduleDate ->
				chooseDate(scheduleDate)
			}
		}

		timeChooseView?.setCallback(object : SlideChooseView.Callback {
			override fun onTouchEnd() {
				// unused
			}

			override fun onOptionSelected(index: Int) {
				if (index < displayedDates.size) {
					val date = (displayedDates[index] + connectionsManager.currentTime).toLong()
					timeEditText?.text = LocaleController.formatDateAudio(date, false)
				}
				else {
					timeEditText?.text = ""
				}
			}
		})

		resetDates()

		linearLayout.addView(timeEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50))

		divider = TextInfoPrivacyCell(context)
		divider?.setText(context.getString(R.string.TimeLimitHelp))

		linearLayout.addView(divider)

		usesHeaderCell = HeaderCell(context)
		usesHeaderCell?.setText(context.getString(R.string.LimitNumberOfUses))

		linearLayout.addView(usesHeaderCell)

		usesChooseView = SlideChooseView(context)

		usesChooseView?.setCallback(object : SlideChooseView.Callback {
			override fun onTouchEnd() {
				// unused
			}

			override fun onOptionSelected(index: Int) {
				usesEditText?.clearFocus()

				ignoreSet = true

				if (index < displayedUses.size) {
					usesEditText?.setText(displayedUses[index].toString())
				}
				else {
					usesEditText?.setText("")
				}

				ignoreSet = false
			}
		})

		resetUses()

		linearLayout.addView(usesChooseView)

		usesEditText = object : EditText(context) {
			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(event: MotionEvent): Boolean {
				if (event.action == MotionEvent.ACTION_UP) {
					isCursorVisible = true
				}

				return super.onTouchEvent(event)
			}
		}

		usesEditText?.setPadding(AndroidUtilities.dp(22f), 0, AndroidUtilities.dp(22f), 0)
		usesEditText?.gravity = Gravity.CENTER_VERTICAL
		usesEditText?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		usesEditText?.hint = context.getString(R.string.UsesLimitHint)
		usesEditText?.keyListener = DigitsKeyListener.getInstance("0123456789.")
		usesEditText?.inputType = InputType.TYPE_CLASS_NUMBER

		usesEditText?.doAfterTextChanged {
			if (ignoreSet) {
				return@doAfterTextChanged
			}

			if (it?.toString() == "0") {
				usesEditText?.setText("")
				return@doAfterTextChanged
			}

			val customUses = try {
				it?.toString()?.toInt() ?: 0
			}
			catch (exception: NumberFormatException) {
				resetUses()
				return@doAfterTextChanged
			}

			if (customUses > 100000) {
				resetUses()
			}
			else {
				chooseUses(customUses)
			}
		}

		linearLayout.addView(usesEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50))

		dividerUses = TextInfoPrivacyCell(context)
		dividerUses?.setText(context.getString(R.string.UsesLimitHelp))

		linearLayout.addView(dividerUses)

		nameEditText = object : EditText(context) {
			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(event: MotionEvent): Boolean {
				if (event.action == MotionEvent.ACTION_UP) {
					isCursorVisible = true
				}

				return super.onTouchEvent(event)
			}
		}

		nameEditText?.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
				// unused
			}

			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
				// unused
			}

			override fun afterTextChanged(s: Editable) {
				val nameEditText = nameEditText ?: return

				val builder = SpannableStringBuilder(s)

				Emoji.replaceEmoji(builder, nameEditText.paint.fontMetricsInt, false)

				val selection = nameEditText.selectionStart

				nameEditText.removeTextChangedListener(this)
				nameEditText.text = builder

				if (selection >= 0) {
					nameEditText.setSelection(selection)
				}

				nameEditText.addTextChangedListener(this)
			}
		})

		nameEditText?.isCursorVisible = false
		nameEditText?.filters = arrayOf<InputFilter>(LengthFilter(32))
		nameEditText?.gravity = Gravity.CENTER_VERTICAL
		nameEditText?.hint = context.getString(R.string.LinkNameHint)
		nameEditText?.setHintTextColor(context.getColor(R.color.hint))
		nameEditText?.setLines(1)
		nameEditText?.setPadding(AndroidUtilities.dp(22f), 0, AndroidUtilities.dp(22f), 0)
		nameEditText?.setSingleLine()
		nameEditText?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		nameEditText?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)

		linearLayout.addView(nameEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50))

		val dividerName = ShadowSectionCell(context)
		dividerName.background = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, context.getColor(R.color.shadow))
//		dividerName.setText(context.getString(R.string.LinkNameHelp))

		linearLayout.addView(dividerName)

		if (type == EDIT_TYPE) {
			val revokeLink = TextSettingsCell(context)
			revokeLink.setBackgroundColor(context.getColor(R.color.background))
			revokeLink.setText(context.getString(R.string.ResetLink), false)
			revokeLink.setTextColor(context.getColor(R.color.purple))

			revokeLink.setOnClickListener {
				val parentActivity = parentActivity ?: return@setOnClickListener

				val builder2 = AlertDialog.Builder(parentActivity)
				builder2.setMessage(context.getString(R.string.RevokeAlert))
				builder2.setTitle(context.getString(R.string.RevokeLink))

				builder2.setPositiveButton(context.getString(R.string.RevokeButton)) { _, _ ->
					callback?.revokeLink(inviteToEdit)
					finishFragment()
				}

				builder2.setNegativeButton(context.getString(R.string.Cancel), null)

				showDialog(builder2.create())
			}

			linearLayout.addView(revokeLink)
		}

		contentView.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		linearLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.BOTTOM, 16f, 15f, 16f, 16f))

		buttonTextView?.gone()

		timeHeaderCell.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		timeChooseView?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		timeEditText?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		usesHeaderCell?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		usesChooseView?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		usesEditText?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		nameEditText?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		contentView.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))
		buttonTextView?.setOnClickListener { onCreateClicked() }
		buttonTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		dividerUses?.background = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, context.getColor(R.color.shadow))
		divider?.background = Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow)
		buttonTextView?.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6f), ResourcesCompat.getColor(context.resources, R.color.light_background, null), ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		usesEditText?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		usesEditText?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
		timeEditText?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		timeEditText?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
		usesEditText?.isCursorVisible = false
		setInviteToEdit(inviteToEdit)
		contentView.clipChildren = false
		scrollView?.clipChildren = false
		linearLayout.clipChildren = false

		return contentView
	}

	private fun onCreateClicked() {
		if (loading) {
			return
		}

		val parentActivity = parentActivity ?: return

		val timeIndex = timeChooseView!!.selectedIndex

		if (timeIndex < displayedDates.size && displayedDates[timeIndex] < 0) {
			AndroidUtilities.shakeView(timeEditText, 2f, 0)
			timeEditText?.context?.vibrate()
			return
		}

		if (type == CREATE_TYPE) {
			progressDialog?.dismiss()

			loading = true

			progressDialog = AlertDialog(parentActivity, 3)
			progressDialog?.showDelayed(500)

			val req = TL_messages_exportChatInvite()
			req.peer = messagesController.getInputPeer(-chatId)
			req.legacy_revoke_permanent = false

			var i = timeChooseView!!.selectedIndex
			req.flags = req.flags or 1

			if (i < displayedDates.size) {
				req.expire_date = displayedDates[i] + connectionsManager.currentTime
			}
			else {
				req.expire_date = 0
			}

			i = usesChooseView!!.selectedIndex

			req.flags = req.flags or 2

			if (i < displayedUses.size) {
				req.usage_limit = displayedUses[i]
			}
			else {
				req.usage_limit = 0
			}

			req.request_needed = approveCell!!.isChecked

			if (req.request_needed) {
				req.usage_limit = 0
			}

			req.title = nameEditText!!.text.toString()

			if (!req.title.isNullOrEmpty()) {
				req.flags = req.flags or 16
			}

			connectionsManager.sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					loading = false
					progressDialog?.dismiss()

					if (error == null) {
						callback?.onLinkCreated(response)
						finishFragment()
					}
					else {
						AlertsCreator.showSimpleAlert(this@LinkEditActivity, error.text)
					}
				}
			}
		}
		else if (type == EDIT_TYPE) {
			progressDialog?.dismiss()

			val req = TL_messages_editExportedChatInvite()
			req.link = inviteToEdit!!.link
			req.revoked = false
			req.peer = messagesController.getInputPeer(-chatId)

			var edited = false
			var i = timeChooseView!!.selectedIndex

			if (i < displayedDates.size) {
				if (currentInviteDate != displayedDates[i]) {
					req.flags = req.flags or 1
					req.expire_date = displayedDates[i] + connectionsManager.currentTime
					edited = true
				}
			}
			else {
				if (currentInviteDate != 0) {
					req.flags = req.flags or 1
					req.expire_date = 0
					edited = true
				}
			}

			i = usesChooseView!!.selectedIndex

			if (i < displayedUses.size) {
				val newLimit = displayedUses[i]

				if (inviteToEdit!!.usage_limit != newLimit) {
					req.flags = req.flags or 2
					req.usage_limit = newLimit
					edited = true
				}
			}
			else {
				if (inviteToEdit!!.usage_limit != 0) {
					req.flags = req.flags or 2
					req.usage_limit = 0
					edited = true
				}
			}

			if (inviteToEdit!!.request_needed != approveCell!!.isChecked) {
				req.flags = req.flags or 8
				req.request_needed = approveCell!!.isChecked
				if (req.request_needed) {
					req.flags = req.flags or 2
					req.usage_limit = 0
				}
				edited = true
			}

			val newTitle = nameEditText!!.text.toString()

			if (!TextUtils.equals(inviteToEdit!!.title, newTitle)) {
				req.title = newTitle
				req.flags = req.flags or 16
				edited = true
			}

			if (edited) {
				loading = true

				progressDialog = AlertDialog(parentActivity, 3)
				progressDialog?.showDelayed(500)

				connectionsManager.sendRequest(req) { response, error ->
					AndroidUtilities.runOnUIThread {
						loading = false
						progressDialog?.dismiss()

						if (error == null) {
							if (response is TL_messages_exportedChatInvite) {
								inviteToEdit = response.invite as TL_chatInviteExported
							}

							callback?.onLinkEdited(inviteToEdit, response)

							finishFragment()
						}
						else {
							AlertsCreator.showSimpleAlert(this@LinkEditActivity, error.text)
						}
					}
				}
			}
			else {
				finishFragment()
			}
		}
	}

	private fun chooseUses(customUses: Int) {
		val context = context ?: return
		var position = 0
		var added = false

		displayedUses.clear()

		for (i in defaultUses.indices) {
			if (!added && customUses <= defaultUses[i]) {
				if (customUses != defaultUses[i]) {
					displayedUses.add(customUses)
				}

				position = i
				added = true
			}

			displayedUses.add(defaultUses[i])
		}

		if (!added) {
			displayedUses.add(customUses)
			position = defaultUses.size
		}

		val options = arrayOfNulls<String>(displayedUses.size + 1)

		for (i in options.indices) {
			if (i == options.size - 1) {
				options[i] = context.getString(R.string.NoLimit)
			}
			else {
				options[i] = displayedUses[i].toString()
			}
		}

		usesChooseView?.setOptions(position, *options.filterNotNull().toTypedArray())
	}

	private fun chooseDate(selectedDate: Int) {
		val context = context ?: return
		@Suppress("NAME_SHADOWING") var selectedDate = selectedDate

		timeEditText?.text = LocaleController.formatDateAudio(selectedDate.toLong(), false)

		val originDate = selectedDate

		selectedDate -= connectionsManager.currentTime

		var position = 0
		var added = false

		displayedDates.clear()

		for (i in defaultDates.indices) {
			if (!added && selectedDate < defaultDates[i]) {
				displayedDates.add(selectedDate)
				position = i
				added = true
			}

			displayedDates.add(defaultDates[i])
		}

		if (!added) {
			displayedDates.add(selectedDate)
			position = defaultDates.size
		}

		val options = arrayOfNulls<String>(displayedDates.size + 1)

		for (i in options.indices) {
			if (i == options.size - 1) {
				options[i] = context.getString(R.string.NoLimit)
			}
			else {
				if (displayedDates[i] == defaultDates[0]) {
					options[i] = LocaleController.formatPluralString("Hours", 1)
				}
				else if (displayedDates[i] == defaultDates[1]) {
					options[i] = LocaleController.formatPluralString("Days", 1)
				}
				else if (displayedDates[i] == defaultDates[2]) {
					options[i] = LocaleController.formatPluralString("Weeks", 1)
				}
				else {
					if (selectedDate < 86400L) {
						options[i] = context.getString(R.string.MessageScheduleToday)
					}
					else if (selectedDate < 364 * 86400L) {
						options[i] = LocaleController.getInstance().formatterScheduleDay.format(originDate * 1000L)
					}
					else {
						options[i] = LocaleController.getInstance().formatterYear.format(originDate * 1000L)
					}
				}
			}
		}

		timeChooseView?.setOptions(position, *options.filterNotNull().toTypedArray())
	}

	private fun resetDates() {
		val context = context ?: return

		displayedDates.clear()

		for (i in defaultDates.indices) {
			displayedDates.add(defaultDates[i])
		}

		val options = arrayOf(LocaleController.formatPluralString("Hours", 1), LocaleController.formatPluralString("Days", 1), LocaleController.formatPluralString("Weeks", 1), context.getString(R.string.NoLimit))

		timeChooseView?.setOptions(options.size - 1, *options)
	}

	fun setCallback(callback: Callback?) {
		this.callback = callback
	}

	private fun resetUses() {
		val context = context ?: return
		displayedUses.clear()

		for (i in defaultUses.indices) {
			displayedUses.add(defaultUses[i])
		}

		val options = arrayOf("1", "10", "100", context.getString(R.string.NoLimit))

		usesChooseView!!.setOptions(options.size - 1, *options)
	}

	fun setInviteToEdit(invite: TL_chatInviteExported?) {
		val context = context ?: return
		inviteToEdit = invite

		if (fragmentView != null && invite != null) {
			currentInviteDate = if (invite.expire_date > 0) {
				chooseDate(invite.expire_date)
				displayedDates[timeChooseView!!.selectedIndex]
			}
			else {
				0
			}

			if (invite.usage_limit > 0) {
				chooseUses(invite.usage_limit)
				usesEditText?.setText(invite.usage_limit.toString())
			}

			approveCell?.setBackgroundColor(if (invite.request_needed) context.getColor(R.color.brand_transparent) else context.getColor(R.color.dark_gray))
			approveCell?.isChecked = invite.request_needed

			setUsesVisible(!invite.request_needed)

			if (!invite.title.isNullOrEmpty()) {
				val builder = SpannableStringBuilder(invite.title)
				Emoji.replaceEmoji(builder, nameEditText!!.paint.fontMetricsInt, false)
				nameEditText?.text = builder
			}
		}
	}

	private fun setUsesVisible(isVisible: Boolean) {
		val context = context ?: return
		usesHeaderCell?.visibility = if (isVisible) View.VISIBLE else View.GONE
		usesChooseView?.visibility = if (isVisible) View.VISIBLE else View.GONE
		usesEditText?.visibility = if (isVisible) View.VISIBLE else View.GONE
		dividerUses?.visibility = if (isVisible) View.VISIBLE else View.GONE
		divider?.background = Theme.getThemedDrawable(parentActivity, if (isVisible) R.drawable.greydivider else R.drawable.greydivider_bottom, context.getColor(R.color.shadow))
	}

	interface Callback {
		fun onLinkCreated(response: TLObject?)
		fun onLinkEdited(inviteToEdit: TL_chatInviteExported?, response: TLObject?)
		fun onLinkRemoved(removedInvite: TL_chatInviteExported?)
		fun revokeLink(inviteFinal: TL_chatInviteExported?)
	}

	override fun finishFragment() {
		scrollView?.layoutParams?.height = scrollView?.height ?: 0
		finished = true
		super.finishFragment()
	}

	companion object {
		const val CREATE_TYPE = 0
		const val EDIT_TYPE = 1
	}
}
