/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject.isChannel
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC.TLMessageMediaPoll
import org.telegram.tgnet.TLRPC.TLPoll
import org.telegram.tgnet.TLRPC.TLPollAnswer
import org.telegram.tgnet.TLRPC.TLPollResults
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.PollEditTextCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatAttachAlert.AttachAlertLayout
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import java.util.Arrays
import kotlin.math.max
import kotlin.math.min

class ChatAttachAlertPollLayout(alert: ChatAttachAlert, context: Context) : AttachAlertLayout(alert, context) {
	private val listAdapter = ListAdapter(context)

	private val listView = object : RecyclerListView(context) {
		override fun requestChildOnScreen(child: View, focused: View?) {
			if (child !is PollEditTextCell) {
				return
			}

			super.requestChildOnScreen(child, focused)
		}

		override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
			rectangle.bottom += AndroidUtilities.dp(60f)
			return super.requestChildRectangleOnScreen(child, rectangle, immediate)
		}
	}

	private val itemAnimator = object : DefaultItemAnimator() {
		override fun onMoveAnimationUpdate(holder: RecyclerView.ViewHolder) {
			if (holder.adapterPosition == 0) {
				parentAlert.updateLayout(this@ChatAttachAlertPollLayout, true, 0)
			}
		}
	}

	private val layoutManager = object : FillLastLinearLayoutManager(context, VERTICAL, false, AndroidUtilities.dp(53f), listView) {
		override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
			val linearSmoothScroller: LinearSmoothScroller = object : LinearSmoothScroller(recyclerView.context) {
				override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
					var dy = super.calculateDyToMakeVisible(view, snapPreference)
					dy -= listTopPadding - AndroidUtilities.dp(7f)
					return dy
				}

				override fun calculateTimeForDeceleration(dx: Int): Int {
					return super.calculateTimeForDeceleration(dx) * 2
				}
			}
			linearSmoothScroller.targetPosition = position
			startSmoothScroll(linearSmoothScroller)
		}

		override fun getChildRectangleOnScreenScrollAmount(child: View, rect: Rect): IntArray {
			val out = IntArray(2)
			val parentTop = 0
			val parentBottom = height - paddingBottom
			val childTop = child.top + rect.top - child.scrollY
			val childBottom = childTop + rect.height()
			val offScreenTop = min(0, childTop - parentTop)
			val offScreenBottom = max(0, childBottom - parentBottom)
			val dy = if (offScreenTop != 0) offScreenTop else min(childTop - parentTop, offScreenBottom)
			out[0] = 0
			out[1] = dy
			return out
		}
	}

	private val hintView = HintView(context, 4)
	private val answers = arrayOfNulls<String>(10)
	private val answersChecks = BooleanArray(10)
	private var answersCount = 1
	private var questionString: String? = null
	private var solutionString: CharSequence? = null
	private var anonymousPoll = true
	private var multipleChoice = false
	private var quizPoll = false
	private var hintShowed = false
	private val quizOnly = 0
	private var allowNesterScroll = false
	private var ignoreLayout = false
	private var delegate: PollCreateActivityDelegate? = null
	private var requestFieldFocusAtPosition = -1
	private var paddingRow = 0
	private var questionHeaderRow = 0
	private var questionRow = 0
	private var solutionRow = 0
	private var solutionInfoRow = 0
	private var questionSectionRow = 0
	private var answerHeaderRow = 0
	private var answerStartRow = 0
	private var addAnswerRow = 0
	private var answerSectionRow = 0
	private var settingsHeaderRow = 0
	private var anonymousRow = 0
	private var multipleRow = 0
	private var quizRow = 0
	private var settingsSectionRow = 0
	private var emptyRow = 0
	private var rowCount = 0

	override var listTopPadding = 0
		private set

	init {
		updateRows()

		/*if (quiz != null) {
			quizPoll = quiz;
			quizOnly = quizPoll ? 1 : 2;
		}*/

		listView.itemAnimator = itemAnimator
		listView.clipToPadding = false
		listView.isVerticalScrollBarEnabled = false
		(listView.itemAnimator as? DefaultItemAnimator)?.setDelayAnimations(false)
		listView.layoutManager = layoutManager

		layoutManager.setSkipFirstItem()

		val itemTouchHelper = ItemTouchHelper(TouchHelperCallback())
		itemTouchHelper.attachToRecyclerView(listView)

		addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

		listView.preserveFocusAfterLayout = true
		listView.adapter = listAdapter

		listView.setOnItemClickListener { view, position ->
			if (position == addAnswerRow) {
				addNewField()
			}
			else if (view is TextCheckCell) {
				val checked: Boolean
				val wasChecksBefore = quizPoll

				if (position == anonymousRow) {
					anonymousPoll = !anonymousPoll
					checked = anonymousPoll
				}
				else if (position == multipleRow) {
					multipleChoice = !multipleChoice

					checked = multipleChoice

					if (multipleChoice && quizPoll) {
						val prevSolutionRow = solutionRow

						quizPoll = false

						updateRows()

						listView.itemAnimator = itemAnimator

						val holder = listView.findViewHolderForAdapterPosition(quizRow)

						if (holder != null) {
							(holder.itemView as? TextCheckCell)?.isChecked = false
						}
						else {
							listAdapter.notifyItemChanged(quizRow)
						}

						listAdapter.notifyItemRangeRemoved(prevSolutionRow, 2)
						listAdapter.notifyItemChanged(emptyRow)
					}
				}
				else {
					if (quizOnly != 0) {
						return@setOnItemClickListener
					}

					listView.itemAnimator = itemAnimator

					quizPoll = !quizPoll

					checked = quizPoll

					val prevSolutionRow = solutionRow

					updateRows()

					if (quizPoll) {
						listAdapter.notifyItemRangeInserted(solutionRow, 2)
					}
					else {
						listAdapter.notifyItemRangeRemoved(prevSolutionRow, 2)
					}

					listAdapter.notifyItemChanged(emptyRow)

					if (quizPoll && multipleChoice) {
						multipleChoice = false

						val holder = listView.findViewHolderForAdapterPosition(multipleRow)

						if (holder != null) {
							(holder.itemView as? TextCheckCell)?.isChecked = false
						}
						else {
							listAdapter.notifyItemChanged(multipleRow)
						}
					}

					if (quizPoll) {
						var was = false

						for (a in answersChecks.indices) {
							if (was) {
								answersChecks[a] = false
							}
							else if (answersChecks[a]) {
								was = true
							}
						}
					}
				}
				if (hintShowed && !quizPoll) {
					hintView.hide()
				}

				for (a in answerStartRow until answerStartRow + answersCount) {
					val holder = listView.findViewHolderForAdapterPosition(a)

					if (holder != null && holder.itemView is PollEditTextCell) {
						val pollEditTextCell = holder.itemView
						pollEditTextCell.setShowCheckBox(quizPoll, true)
						pollEditTextCell.setChecked(answersChecks[a - answerStartRow], wasChecksBefore)

						if (pollEditTextCell.top > AndroidUtilities.dp(40f) && position == quizRow && !hintShowed) {
							hintView.showForView(pollEditTextCell.checkBox, true)
							hintShowed = true
						}
					}
				}

				view.isChecked = checked

				checkDoneButton()
			}
		}

		listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				parentAlert.updateLayout(this@ChatAttachAlertPollLayout, true, dy)
				if (dy != 0) {
					hintView.hide()
				}
			}

			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				if (newState == RecyclerView.SCROLL_STATE_IDLE) {
					val offset = AndroidUtilities.dp(13f)
					val backgroundPaddingTop = parentAlert.backgroundPaddingTop
					val top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset

					if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
						val holder = listView.findViewHolderForAdapterPosition(1) as RecyclerListView.Holder?

						if (holder != null && holder.itemView.top > AndroidUtilities.dp(53f)) {
							listView.smoothScrollBy(0, holder.itemView.top - AndroidUtilities.dp(53f))
						}
					}
				}
			}
		})

		hintView.setText(context.getString(R.string.PollTapToSelect))
		hintView.alpha = 0.0f
		hintView.visibility = INVISIBLE

		addView(hintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 19f, 0f, 19f, 0f))

		checkDoneButton()
	}

	override fun needsActionBar(): Int {
		return 1
	}

	override fun onResume() {
		listAdapter.notifyDataSetChanged()
	}

	override fun onHideShowProgress(progress: Float) {
		parentAlert.doneItem.alpha = (if (parentAlert.doneItem.isEnabled) 1.0f else 0.5f) * progress
	}

	override fun onMenuItemClick(id: Int) {
		if (id == DONE_BUTTON) {
			if (quizPoll && parentAlert.doneItem.alpha != 1.0f) {
				var checksCount = 0

				for (a in answersChecks.indices) {
					if (!getFixedString(answers[a]).isNullOrEmpty() && answersChecks[a]) {
						checksCount++
					}
				}

				if (checksCount <= 0) {
					showQuizHint()
				}

				return
			}

			val poll = TLMessageMediaPoll()

			poll.poll = TLPoll().also {
				it.multipleChoice = multipleChoice
				it.quiz = quizPoll
				it.publicVoters = !anonymousPoll
				it.question = getFixedString(questionString)?.toString()
			}

			val serializedData = SerializedData(10)

			for (a in answers.indices) {
				if (getFixedString(answers[a]).isNullOrEmpty()) {
					continue
				}

				val answer = TLPollAnswer()
				answer.text = getFixedString(answers[a])?.toString()
				answer.option = ByteArray(1).also {
					it[0] = (48 + poll.poll!!.answers.size).toByte()
				}

				poll.poll?.answers?.add(answer)

				if ((multipleChoice || quizPoll) && answersChecks[a]) {
					serializedData.writeByte(answer.option!![0])
				}
			}

			val params = HashMap<String, String>()
			params["answers"] = Utilities.bytesToHex(serializedData.toByteArray())

			poll.results = TLPollResults()

			val solution = getFixedString(solutionString)

			if (solution != null) {
				poll.results?.solution = solution.toString()

				val message: Array<CharSequence?> = arrayOf(solution)
				val entities = MediaDataController.getInstance(parentAlert.currentAccount).getEntities(message, true)

				if (!entities.isNullOrEmpty()) {
					poll.results?.solutionEntities?.addAll(entities)
				}

				if (!poll.results?.solution.isNullOrEmpty()) {
					poll.results?.flags = poll.results!!.flags or 16
				}
			}

			val chatActivity = parentAlert.baseFragment as? ChatActivity

			if (chatActivity?.isInScheduleMode == true) {
				AlertsCreator.createScheduleDatePickerDialog(chatActivity.parentActivity, chatActivity.dialogId) { notify, scheduleDate ->
					delegate?.sendPoll(poll, params, notify, scheduleDate)
					parentAlert.dismiss(true)
				}
			}
			else {
				delegate?.sendPoll(poll, params, true, 0)
				parentAlert.dismiss(true)
			}
		}
	}

	override var currentItemTop: Int
		get() {
			if (listView.childCount <= 1) {
				return Int.MAX_VALUE
			}

			val child = listView.getChildAt(1) ?: return Int.MAX_VALUE
			val holder = listView.findContainingViewHolder(child) as? RecyclerListView.Holder
			val top = child.y.toInt() - AndroidUtilities.dp(8f)
			var newOffset = if (top > 0 && holder != null && holder.adapterPosition == 1) top else 0

			if (top >= 0 && holder != null && holder.adapterPosition == 1) {
				newOffset = top
			}

			return newOffset + AndroidUtilities.dp(25f)
		}
		set(currentItemTop) {
			super.currentItemTop = currentItemTop
		}

	override val firstOffset: Int
		get() = listTopPadding + AndroidUtilities.dp(17f)

	override fun setTranslationY(translationY: Float) {
		super.setTranslationY(translationY)
		parentAlert.sheetContainer.invalidate()
	}

	override fun onPreMeasure(availableWidth: Int, availableHeight: Int) {
		var padding: Int

		if (parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20f)) {
			padding = AndroidUtilities.dp(52f)
			parentAlert.setAllowNestedScroll(false)
		}
		else {
			padding = if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
				(availableHeight / 3.5f).toInt()
			}
			else {
				availableHeight / 5 * 2
			}

			padding -= AndroidUtilities.dp(13f)

			if (padding < 0) {
				padding = 0
			}

			parentAlert.setAllowNestedScroll(allowNesterScroll)
		}

		ignoreLayout = true

		if (listTopPadding != padding) {
			listTopPadding = padding
			listView.itemAnimator = null
			listAdapter.notifyItemChanged(paddingRow)
		}

		ignoreLayout = false
	}

	override val buttonsHideOffset: Int
		get() = AndroidUtilities.dp(70f)

	override fun requestLayout() {
		if (ignoreLayout) {
			return
		}

		super.requestLayout()
	}

	override fun scrollToTop() {
		listView.smoothScrollToPosition(1)
	}

	private fun showQuizHint() {
		for (a in answerStartRow until answerStartRow + answersCount) {
			val holder = listView.findViewHolderForAdapterPosition(a)

			if (holder != null && holder.itemView is PollEditTextCell) {
				val pollEditTextCell = holder.itemView

				if (pollEditTextCell.top > AndroidUtilities.dp(40f)) {
					hintView.showForView(pollEditTextCell.checkBox, true)
					break
				}
			}
		}
	}

	private fun checkDoneButton() {
		var enabled = true
		var checksCount = 0

		if (quizPoll) {
			for (a in answersChecks.indices) {
				if (!getFixedString(answers[a]).isNullOrEmpty() && answersChecks[a]) {
					checksCount++
				}
			}
		}

		var count = 0

		if (!getFixedString(solutionString).isNullOrEmpty() && (solutionString?.length ?: 0) > MAX_SOLUTION_LENGTH) {
			enabled = false
		}
		else if (getFixedString(questionString).isNullOrEmpty() || (questionString?.length ?: 0) > MAX_QUESTION_LENGTH) {
			enabled = false
		}

		var hasAnswers = false

		for (a in answers.indices) {
			if (!getFixedString(answers[a]).isNullOrEmpty()) {
				hasAnswers = true

				if ((answers[a]?.length ?: 0) > MAX_ANSWER_LENGTH) {
					count = 0
					break
				}

				count++
			}
		}

		if (count < 2 || quizPoll && checksCount < 1) {
			enabled = false
		}

		allowNesterScroll = !(!solutionString.isNullOrEmpty() || !questionString.isNullOrEmpty() || hasAnswers)

		parentAlert.setAllowNestedScroll(allowNesterScroll)
		parentAlert.doneItem.isEnabled = quizPoll && checksCount == 0 || enabled
		parentAlert.doneItem.alpha = if (enabled) 1.0f else 0.5f
	}

	private fun updateRows() {
		rowCount = 0
		paddingRow = rowCount++
		questionHeaderRow = rowCount++
		questionRow = rowCount++
		questionSectionRow = rowCount++
		answerHeaderRow = rowCount++

		if (answersCount != 0) {
			answerStartRow = rowCount
			rowCount += answersCount
		}
		else {
			answerStartRow = -1
		}

		addAnswerRow = if (answersCount != answers.size) {
			rowCount++
		}
		else {
			-1
		}

		answerSectionRow = rowCount++
		settingsHeaderRow = rowCount++

		val chat = (parentAlert.baseFragment as? ChatActivity)?.currentChat

		anonymousRow = if (!isChannel(chat) || chat.megagroup) {
			rowCount++
		}
		else {
			-1
		}

		multipleRow = if (quizOnly != 1) {
			rowCount++
		}
		else {
			-1
		}

		quizRow = if (quizOnly == 0) {
			rowCount++
		}
		else {
			-1
		}

		settingsSectionRow = rowCount++

		if (quizPoll) {
			solutionRow = rowCount++
			solutionInfoRow = rowCount++
		}
		else {
			solutionRow = -1
			solutionInfoRow = -1
		}

		emptyRow = rowCount++
	}

	override fun onShow(previousLayout: AttachAlertLayout?) {
		if (quizOnly == 1) {
			parentAlert.actionBar.setTitle(context.getString(R.string.NewQuiz))
		}
		else {
			parentAlert.actionBar.setTitle(context.getString(R.string.NewPoll))
		}

		parentAlert.doneItem.visibility = VISIBLE

		layoutManager.scrollToPositionWithOffset(0, 0)
	}

	override fun onHidden() {
		parentAlert.doneItem.visibility = INVISIBLE
	}

	override fun onBackPressed(): Boolean {
		return if (!checkDiscard()) {
			true
		}
		else {
			super.onBackPressed()
		}
	}

	private fun checkDiscard(): Boolean {
		var allowDiscard = getFixedString(questionString).isNullOrEmpty()

		if (allowDiscard) {
			for (a in 0 until answersCount) {
				allowDiscard = getFixedString(answers[a]).isNullOrEmpty()

				if (!allowDiscard) {
					break
				}
			}
		}

		if (!allowDiscard) {
			val activity = parentAlert.baseFragment.parentActivity

			if (activity != null) {
				val builder = AlertDialog.Builder(activity)
				builder.setTitle(context.getString(R.string.CancelPollAlertTitle))
				builder.setMessage(context.getString(R.string.CancelPollAlertText))

				builder.setPositiveButton(context.getString(R.string.PassportDiscard)) { _, _ ->
					parentAlert.dismiss()
				}

				builder.setNegativeButton(context.getString(R.string.Cancel), null)
				builder.show()
			}
		}

		return allowDiscard
	}

	fun setDelegate(pollCreateActivityDelegate: PollCreateActivityDelegate?) {
		delegate = pollCreateActivityDelegate
	}

	private fun setTextLeft(cell: View, index: Int) {
		@Suppress("NAME_SHADOWING") var index = index

		if (cell !is PollEditTextCell) {
			return
		}

		val max: Int
		val left: Int

		if (index == questionRow) {
			max = MAX_QUESTION_LENGTH
			left = MAX_QUESTION_LENGTH - if (questionString != null) questionString!!.length else 0
		}
		else if (index == solutionRow) {
			max = MAX_SOLUTION_LENGTH
			left = MAX_SOLUTION_LENGTH - if (solutionString != null) solutionString!!.length else 0
		}
		else if (index >= answerStartRow && index < answerStartRow + answersCount) {
			index -= answerStartRow
			max = MAX_ANSWER_LENGTH
			left = MAX_ANSWER_LENGTH - if (answers[index] != null) answers[index]!!.length else 0
		}
		else {
			return
		}

		if (left <= max - max * 0.7f) {
			cell.setText2(String.format("%d", left))
			val textView = cell.textView2
			textView.textColor = ResourcesCompat.getColor(resources, if (left < 0) R.color.purple else R.color.dark_gray, null)
		}
		else {
			cell.setText2("")
		}
	}

	private fun addNewField() {
		listView.itemAnimator = itemAnimator
		answersChecks[answersCount] = false
		answersCount++

		if (answersCount == answers.size) {
			listAdapter.notifyItemRemoved(addAnswerRow)
		}

		listAdapter.notifyItemInserted(addAnswerRow)

		updateRows()

		requestFieldFocusAtPosition = answerStartRow + answersCount - 1

		listAdapter.notifyItemChanged(answerSectionRow)
		listAdapter.notifyItemChanged(emptyRow)
	}

	fun interface PollCreateActivityDelegate {
		fun sendPoll(poll: TLMessageMediaPoll, params: HashMap<String, String>, notify: Boolean, scheduleDate: Int)
	}

	private class EmptyView(context: Context) : View(context)

	inner class TouchHelperCallback : ItemTouchHelper.Callback() {
		override fun isLongPressDragEnabled(): Boolean {
			return true
		}

		override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
			return if (viewHolder.itemViewType != 5) {
				makeMovementFlags(0, 0)
			}
			else {
				makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
			}
		}

		override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
			if (source.itemViewType != target.itemViewType) {
				return false
			}

			listAdapter.swapElements(source.adapterPosition, target.adapterPosition)

			return true
		}

		override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
			if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
				listView.itemAnimator = itemAnimator
				listView.cancelClickRunnables(false)
				viewHolder?.itemView?.isPressed = true
				viewHolder?.itemView?.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.background, null))
			}

			super.onSelectedChanged(viewHolder, actionState)
		}

		override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
			// unused
		}

		override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
			super.clearView(recyclerView, viewHolder)
			viewHolder.itemView.isPressed = false
			viewHolder.itemView.background = null
		}
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun getItemCount(): Int {
			return rowCount
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				0 -> {
					val cell = holder.itemView as HeaderCell

					if (position == questionHeaderRow) {
						cell.textView.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
						cell.setText(mContext.getString(R.string.PollQuestion))
					}
					else {
						cell.textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL

						if (position == answerHeaderRow) {
							if (quizOnly == 1) {
								cell.setText(mContext.getString(R.string.QuizAnswers))
							}
							else {
								cell.setText(mContext.getString(R.string.AnswerOptions))
							}
						}
						else if (position == settingsHeaderRow) {
							cell.setText(mContext.getString(R.string.Settings))
						}
					}
				}

				2 -> {
					val cell = holder.itemView as TextInfoPrivacyCell
					val drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)

					val combinedDrawable = CombinedDrawable(ResourcesCompat.getColor(resources, R.color.light_background, null).toDrawable(), drawable)
					combinedDrawable.setFullSize(true)

					cell.background = combinedDrawable

					if (position == solutionInfoRow) {
						cell.setText(mContext.getString(R.string.AddAnExplanationInfo))
					}
					else if (position == settingsSectionRow) {
						if (quizOnly != 0) {
							cell.setText(null)
						}
						else {
							cell.setText(mContext.getString(R.string.QuizInfo))
						}
					}
					else if (10 - answersCount <= 0) {
						cell.setText(mContext.getString(R.string.AddAnOptionInfoMax))
					}
					else {
						cell.setText(LocaleController.formatString("AddAnOptionInfo", R.string.AddAnOptionInfo, LocaleController.formatPluralString("Option", 10 - answersCount)))
					}
				}

				3 -> {
					val textCell = holder.itemView as TextCell
					textCell.setColors(null, ResourcesCompat.getColor(mContext.resources, R.color.brand, null))

					val drawable1 = ResourcesCompat.getDrawable(mContext.resources, R.drawable.poll_add_circle, null)
					val drawable2 = ResourcesCompat.getDrawable(mContext.resources, R.drawable.poll_add_plus, null)

					drawable1?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(mContext.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)
					drawable2?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(mContext.resources, R.color.white, null), PorterDuff.Mode.SRC_IN)

					val combinedDrawable = CombinedDrawable(drawable1, drawable2)

					textCell.setTextAndIcon(mContext.getString(R.string.AddAnOption), combinedDrawable, false)
				}

				6 -> {
					val checkCell = holder.itemView as TextCheckCell

					when (position) {
						anonymousRow -> {
							checkCell.setTextAndCheck(context.getString(R.string.PollAnonymous), anonymousPoll, multipleRow != -1 || quizRow != -1)
							checkCell.setEnabled(true, null)
						}

						multipleRow -> {
							checkCell.setTextAndCheck(context.getString(R.string.PollMultiple), multipleChoice, quizRow != -1)
							checkCell.setEnabled(true, null)
						}

						quizRow -> {
							checkCell.setTextAndCheck(context.getString(R.string.PollQuiz), quizPoll, false)
							checkCell.setEnabled(quizOnly == 0, null)
						}
					}

					holder.itemView.requestLayout()
				}

				9 -> {
					holder.itemView.requestLayout()
				}
			}
		}

		override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
			when (holder.itemViewType) {
				4 -> {
					val textCell = holder.itemView as PollEditTextCell
					textCell.tag = 1
					textCell.setTextAndHint(if (questionString != null) questionString else "", context.getString(R.string.QuestionHint), false)
					textCell.tag = null

					setTextLeft(holder.itemView, holder.adapterPosition)
				}

				5 -> {
					val position = holder.adapterPosition

					val textCell = holder.itemView as PollEditTextCell
					textCell.tag = 1

					val index = position - answerStartRow

					textCell.setTextAndHint(answers[index], context.getString(R.string.OptionHint), true)
					textCell.tag = null

					if (requestFieldFocusAtPosition == position) {
						val editText = textCell.textView
						editText.requestFocus()
						AndroidUtilities.showKeyboard(editText)
						requestFieldFocusAtPosition = -1
					}

					setTextLeft(holder.itemView, position)
				}

				7 -> {
					val textCell = holder.itemView as PollEditTextCell
					textCell.tag = 1
					textCell.setTextAndHint(if (solutionString != null) solutionString else "", context.getString(R.string.AddAnExplanation), false)
					textCell.tag = null

					setTextLeft(holder.itemView, holder.adapterPosition)
				}
			}
		}

		override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
			if (holder.itemViewType == 4) {
				val editTextCell = holder.itemView as PollEditTextCell
				val editText = editTextCell.textView

				if (editText.isFocused) {
					editText.clearFocus()
					AndroidUtilities.hideKeyboard(editText)
				}
			}
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val position = holder.adapterPosition
			return position == addAnswerRow || position == anonymousRow || position == multipleRow || quizOnly == 0 && position == quizRow
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = HeaderCell(mContext, 21, 15, 15, false)
				}

				1 -> {
					view = ShadowSectionCell(mContext)

					val drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow)

					val combinedDrawable = CombinedDrawable(ResourcesCompat.getColor(mContext.resources, R.color.light_background, null).toDrawable(), drawable)
					combinedDrawable.setFullSize(true)

					view.setBackground(combinedDrawable)
				}

				2 -> {
					view = TextInfoPrivacyCell(mContext)
				}

				3 -> {
					view = TextCell(mContext)
				}

				4 -> {
					val cell: PollEditTextCell = object : PollEditTextCell(mContext, null) {
						override fun onFieldTouchUp(editText: EditTextBoldCursor) {
							parentAlert.makeFocusable(editText, true)
						}
					}

					cell.createErrorTextView()

					cell.addTextWatcher(object : TextWatcher {
						override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
						}

						override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
						}

						override fun afterTextChanged(s: Editable) {
							if (cell.tag != null) {
								return
							}

							questionString = s.toString()

							val holder = listView.findViewHolderForAdapterPosition(questionRow)

							if (holder != null) {
								setTextLeft(holder.itemView, questionRow)
							}

							checkDoneButton()
						}
					})

					view = cell
				}

				6 -> {
					view = TextCheckCell(mContext)
				}

				7 -> {
					val cell = object : PollEditTextCell(mContext, true, null) {
						override fun onFieldTouchUp(editText: EditTextBoldCursor) {
							parentAlert.makeFocusable(editText, true)
						}

						override fun onActionModeStart(editText: EditTextBoldCursor, actionMode: ActionMode) {
							if (editText.isFocused && editText.hasSelection()) {
								val menu = actionMode.menu

								if (menu.findItem(android.R.id.copy) == null) {
									return
								}

								(parentAlert.baseFragment as? ChatActivity)?.fillActionModeMenu(menu)
							}
						}
					}

					cell.createErrorTextView()

					cell.addTextWatcher(object : TextWatcher {
						override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
						}

						override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
						}

						override fun afterTextChanged(s: Editable) {
							if (cell.tag != null) {
								return
							}

							solutionString = s

							val holder = listView.findViewHolderForAdapterPosition(solutionRow)

							if (holder != null) {
								setTextLeft(holder.itemView, solutionRow)
							}

							checkDoneButton()
						}
					})

					view = cell
				}

				8 -> {
					view = EmptyView(mContext)
					view.setBackgroundColor(ResourcesCompat.getColor(mContext.resources, R.color.light_background, null))
				}

				9 -> {
					view = object : View(mContext) {
						override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
							setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), listTopPadding)
						}
					}
				}

				else -> {
					val cell: PollEditTextCell = object : PollEditTextCell(mContext, OnClickListener {
						if (it.tag != null) {
							return@OnClickListener
						}

						it.tag = 1

						val p = it.parent as PollEditTextCell
						var holder = listView.findContainingViewHolder(p)

						if (holder != null) {
							val position = holder.adapterPosition

							if (position != RecyclerView.NO_POSITION) {
								listView.itemAnimator = itemAnimator

								val index = position - answerStartRow

								listAdapter.notifyItemRemoved(position)

								System.arraycopy(answers, index + 1, answers, index, answers.size - 1 - index)
								System.arraycopy(answersChecks, index + 1, answersChecks, index, answersChecks.size - 1 - index)

								answers[answers.size - 1] = null
								answersChecks[answersChecks.size - 1] = false

								answersCount--

								if (answersCount == answers.size - 1) {
									listAdapter.notifyItemInserted(answerStartRow + answers.size - 1)
								}

								holder = listView.findViewHolderForAdapterPosition(position - 1)

								val editText = p.textView

								if (holder != null && holder.itemView is PollEditTextCell) {
									val editTextCell = holder.itemView as PollEditTextCell
									editTextCell.textView.requestFocus()
								}
								else if (editText.isFocused) {
									AndroidUtilities.hideKeyboard(editText)
								}

								editText.clearFocus()
								checkDoneButton()
								updateRows()

								listAdapter.notifyItemChanged(answerSectionRow)
								listAdapter.notifyItemChanged(emptyRow)
							}
						}
					}) {
						override fun drawDivider(): Boolean {
							val holder = listView.findContainingViewHolder(this)

							if (holder != null) {
								val position = holder.adapterPosition

								if (answersCount == 10 && position == answerStartRow + answersCount - 1) {
									return false
								}
							}
							return true
						}

						override fun shouldShowCheckBox(): Boolean {
							return quizPoll
						}

						override fun onFieldTouchUp(editText: EditTextBoldCursor) {
							parentAlert.makeFocusable(editText, true)
						}

						override fun onCheckBoxClick(editText: PollEditTextCell, checked: Boolean) {
							if (checked && quizPoll) {
								Arrays.fill(answersChecks, false)
								var a = answerStartRow

								while (a < answerStartRow + answersCount) {
									val holder = listView.findViewHolderForAdapterPosition(a)

									if (holder != null && holder.itemView is PollEditTextCell) {
										holder.itemView.setChecked(false, true)
									}

									a++
								}
							}

							super.onCheckBoxClick(editText, checked)

							val holder = listView.findContainingViewHolder(editText)

							if (holder != null) {
								val position = holder.adapterPosition

								if (position != RecyclerView.NO_POSITION) {
									val index = position - answerStartRow
									answersChecks[index] = checked
								}
							}

							checkDoneButton()
						}

						override fun isChecked(editText: PollEditTextCell): Boolean {
							val holder = listView.findContainingViewHolder(editText)

							if (holder != null) {
								val position = holder.adapterPosition

								if (position != RecyclerView.NO_POSITION) {
									val index = position - answerStartRow
									return answersChecks[index]
								}
							}

							return false
						}
					}

					cell.addTextWatcher(object : TextWatcher {
						override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
						}

						override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
						}

						override fun afterTextChanged(s: Editable) {
							val holder = listView.findContainingViewHolder(cell)

							if (holder != null) {
								val position = holder.adapterPosition
								val index = position - answerStartRow

								if (index < 0 || index >= answers.size) {
									return
								}

								answers[index] = s.toString()

								setTextLeft(cell, position)

								checkDoneButton()
							}
						}
					})

					cell.setShowNextButton(true)

					val editText = cell.textView
					editText.imeOptions = editText.imeOptions or EditorInfo.IME_ACTION_NEXT

					editText.setOnEditorActionListener { _, actionId, _ ->
						if (actionId == EditorInfo.IME_ACTION_NEXT) {
							var holder = listView.findContainingViewHolder(cell)

							if (holder != null) {
								val position = holder?.adapterPosition ?: RecyclerView.NO_POSITION

								if (position != RecyclerView.NO_POSITION) {
									val index = position - answerStartRow

									if (index == answersCount - 1 && answersCount < 10) {
										addNewField()
									}
									else {
										if (index == answersCount - 1) {
											AndroidUtilities.hideKeyboard(cell.textView)
										}
										else {
											holder = listView.findViewHolderForAdapterPosition(position + 1)

											if (holder != null && holder!!.itemView is PollEditTextCell) {
												val editTextCell = holder!!.itemView as PollEditTextCell
												editTextCell.textView.requestFocus()
											}
										}
									}
								}
							}

							return@setOnEditorActionListener true
						}

						false
					}

					editText.setOnKeyListener { v, keyCode, event ->
						val field = v as EditTextBoldCursor

						if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN && field.length() == 0) {
							cell.callOnDelete()
							return@setOnKeyListener true
						}

						false
					}

					view = cell
				}
			}

			view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)

			return RecyclerListView.Holder(view)
		}

		override fun getItemViewType(position: Int): Int {
			return when (position) {
				questionHeaderRow, answerHeaderRow, settingsHeaderRow -> 0
				questionSectionRow -> 1
				answerSectionRow, settingsSectionRow, solutionInfoRow -> 2
				addAnswerRow -> 3
				questionRow -> 4
				solutionRow -> 7
				anonymousRow, multipleRow, quizRow -> 6
				emptyRow -> 8
				paddingRow -> 9
				else -> 5
			}
		}

		fun swapElements(fromIndex: Int, toIndex: Int) {
			val idx1 = fromIndex - answerStartRow
			val idx2 = toIndex - answerStartRow

			if (idx1 < 0 || idx2 < 0 || idx1 >= answersCount || idx2 >= answersCount) {
				return
			}

			val from = answers[idx1]

			answers[idx1] = answers[idx2]
			answers[idx2] = from

			val temp = answersChecks[idx1]

			answersChecks[idx1] = answersChecks[idx2]
			answersChecks[idx2] = temp

			notifyItemMoved(fromIndex, toIndex)
		}
	}

	companion object {
		const val MAX_QUESTION_LENGTH = 255
		const val MAX_ANSWER_LENGTH = 100
		const val MAX_SOLUTION_LENGTH = 200
		private const val DONE_BUTTON = 40

		@JvmStatic
		fun getFixedString(text: CharSequence?): CharSequence? {
			@Suppress("NAME_SHADOWING") var text = text

			if (text.isNullOrEmpty()) {
				return text
			}

			text = AndroidUtilities.getTrimmedString(text)

			while (TextUtils.indexOf(text, "\n\n\n") >= 0) {
				text = TextUtils.replace(text, arrayOf("\n\n\n"), arrayOf<CharSequence>("\n\n"))
			}

			while (TextUtils.indexOf(text, "\n\n\n") == 0) {
				text = TextUtils.replace(text, arrayOf("\n\n\n"), arrayOf<CharSequence>("\n\n"))
			}

			return text
		}
	}
}
