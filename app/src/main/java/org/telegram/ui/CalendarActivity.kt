/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.TextPaint
import android.util.SparseArray
import android.util.TypedValue
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.util.size
import androidx.core.util.valueIterator
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DownloadController
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesStorage.BooleanCallback
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.media
import org.telegram.tgnet.thumbs
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.Easings
import org.telegram.ui.Components.HideViewAfterAnimation
import org.telegram.ui.Components.HintView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.sharedmedia.SharedMediaLayout
import java.time.YearMonth
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

class CalendarActivity(args: Bundle?, private val photosVideosTypeFilter: Int, selectedDate: Int) : BaseFragment(args) {
	private val selectOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var blurredView: View? = null
	private var bottomBar: FrameLayout? = null
	private var calendarType = 0
	private var canClearHistory = false
	private var checkEnterItems = false
	private var dateSelectedEnd = 0
	private var dateSelectedStart = 0
	private var dialogId = 0L
	private var inSelectionMode = false
	private var isOpened = false
	private var loading = false
	private var minDate = 0
	private var selectionAnimator: ValueAnimator? = null
	private val activeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val adapter by lazy { CalendarAdapter() }
	private val blackoutPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var callback: Callback? = null
	private var contentView: FrameLayout? = null
	private var endReached = false
	private var lastId = 0
	private val layoutManager by lazy { LinearLayoutManager(context) }
	private var listView: RecyclerListView? = null
	private val messagesByYearMouth: SparseArray<SparseArray<PeriodDay>> = SparseArray()
	private var minMontYear = 0
	private var monthCount = 0
	private var removeDaysButton: TextView? = null
	private var selectDaysButton: TextView? = null
	private var selectDaysHint: HintView? = null
	private var selectedMonth = 0
	private var selectedYear = 0
	private var startFromMonth = 0
	private var startFromYear = 0
	private var startOffset = 0
	private val textPaint2 = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

	override fun onFragmentCreate(): Boolean {
		dialogId = getArguments()?.getLong("dialog_id") ?: return false
		calendarType = getArguments()?.getInt("type") ?: 0
		canClearHistory = dialogId >= 0

		return super.onFragmentCreate()
	}

	override fun createView(context: Context): View? {
		textPaint.textSize = AndroidUtilities.dp(16f).toFloat()
		textPaint.textAlign = Paint.Align.CENTER
		textPaint.setTypeface(Theme.TYPEFACE_DEFAULT)

		textPaint2.textSize = AndroidUtilities.dp(11f).toFloat()
		textPaint2.textAlign = Paint.Align.CENTER
		textPaint2.setTypeface(Theme.TYPEFACE_BOLD)

		activeTextPaint.textSize = AndroidUtilities.dp(16f).toFloat()
		activeTextPaint.setTypeface(Theme.TYPEFACE_BOLD)
		activeTextPaint.textAlign = Paint.Align.CENTER

		contentView = object : FrameLayout(context) {
			var lastSize = 0

			@SuppressLint("NotifyDataSetChanged")
			override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
				super.onLayout(changed, left, top, right, bottom)

				val size = measuredHeight + measuredWidth shl 16

				if (lastSize != size) {
					lastSize = size
					adapter.notifyDataSetChanged()
				}
			}
		}

		createActionBar(context)

		contentView?.addView(actionBar)

		actionBar?.setTitle(context.getString(R.string.Calendar))
		actionBar?.castShadows = false

		listView = object : RecyclerListView(context) {
			override fun dispatchDraw(canvas: Canvas) {
				super.dispatchDraw(canvas)
				checkEnterItems = false
			}
		}

		listView?.setLayoutManager(layoutManager)

		layoutManager.reverseLayout = true

		listView?.setAdapter(adapter)

		listView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				super.onScrolled(recyclerView, dx, dy)
				checkLoadNext()
			}
		})

		val showBottomPanel = calendarType == TYPE_CHAT_ACTIVITY && canClearHistory

		contentView?.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), 0, 0f, 36f, 0f, if (showBottomPanel) 48f else 0f))

		val daysOfWeek = arrayOf(
				context.getString(R.string.CalendarWeekNameShortMonday),
				context.getString(R.string.CalendarWeekNameShortTuesday),
				context.getString(R.string.CalendarWeekNameShortWednesday),
				context.getString(R.string.CalendarWeekNameShortThursday),
				context.getString(R.string.CalendarWeekNameShortFriday),
				context.getString(R.string.CalendarWeekNameShortSaturday),
				context.getString(R.string.CalendarWeekNameShortSunday),
		)

		val headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow)!!.mutate()

		val calendarSignatureView: View = object : View(context) {
			override fun onDraw(canvas: Canvas) {
				super.onDraw(canvas)

				val xStep = measuredWidth / 7f

				for (i in 0..6) {
					val cx = xStep * i + xStep / 2f
					val cy = (measuredHeight - AndroidUtilities.dp(2f)) / 2f

					canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5f), textPaint2)
				}

				headerShadowDrawable.setBounds(0, measuredHeight - AndroidUtilities.dp(3f), measuredWidth, measuredHeight)
				headerShadowDrawable.draw(canvas)
			}
		}

		contentView?.addView(calendarSignatureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38f, 0, 0f, 0f, 0f, 0f))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					if (dateSelectedStart != 0 || dateSelectedEnd != 0 || inSelectionMode) {
						inSelectionMode = false
						dateSelectedStart = 0
						dateSelectedEnd = 0
						updateTitle()
						animateSelection()
					}
					else {
						finishFragment()
					}
				}
			}
		})

		fragmentView = contentView

		val calendar = Calendar.getInstance()

		startFromYear = calendar[Calendar.YEAR]
		startFromMonth = calendar[Calendar.MONTH]

		if (selectedYear != 0) {
			monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1
			layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120f))
		}

		if (monthCount < 3) {
			monthCount = 3
		}

		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		loadNext()

		activeTextPaint.color = Color.WHITE
		textPaint.color = context.getColor(R.color.text)
		textPaint2.color = context.getColor(R.color.text)

		activeTextPaint.color = Color.WHITE

		if (showBottomPanel) {
			bottomBar = object : FrameLayout(context) {
				public override fun onDraw(canvas: Canvas) {
					canvas.drawRect(0f, 0f, measuredWidth.toFloat(), AndroidUtilities.getShadowHeight().toFloat(), Theme.dividerPaint)
				}
			}

			bottomBar?.setWillNotDraw(false)
			bottomBar?.setPadding(0, AndroidUtilities.getShadowHeight(), 0, 0)
			bottomBar?.setClipChildren(false)

			selectDaysButton = TextView(context)
			selectDaysButton?.gravity = Gravity.CENTER
			selectDaysButton?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			selectDaysButton?.setTypeface(Theme.TYPEFACE_BOLD)

			selectDaysButton?.setOnClickListener {
				inSelectionMode = true
				updateTitle()
			}

			selectDaysButton?.text = context.getString(R.string.SelectDays)
			selectDaysButton?.isAllCaps = true

			bottomBar?.addView(selectDaysButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), 0, 0f, 0f, 0f, 0f))

			removeDaysButton = TextView(context)
			removeDaysButton?.gravity = Gravity.CENTER
			removeDaysButton?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			removeDaysButton?.setTypeface(Theme.TYPEFACE_BOLD)

			removeDaysButton?.setOnClickListener {
				if (lastDaysSelected == 0) {
					if (selectDaysHint == null) {
						selectDaysHint = HintView(it.context, 8)
						selectDaysHint?.setExtraTranslationY(AndroidUtilities.dp(24f).toFloat())

						contentView?.addView(selectDaysHint, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 19f, 0f, 19f, 0f))

						selectDaysHint?.setText(context.getString(R.string.SelectDaysTooltip))
					}

					selectDaysHint?.showForView(bottomBar, true)

					return@setOnClickListener
				}

				AlertsCreator.createClearDaysDialogAlert(this, lastDaysSelected, messagesController.getUser(dialogId), null, false, BooleanCallback { forAll ->
					finishFragment()

					if ((parentLayout?.fragmentsStack?.size ?: 0) >= 2) {
						val fragmentsStack = parentLayout?.fragmentsStack ?: return@BooleanCallback
						val fragment = fragmentsStack[fragmentsStack.size - 2]

						if (fragment is ChatActivity) {
							fragment.deleteHistory(dateSelectedStart, dateSelectedEnd + 86400, forAll)
						}
					}
				})
			}

			removeDaysButton?.isAllCaps = true
			removeDaysButton?.gone()

			bottomBar?.addView(removeDaysButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), 0, 0f, 0f, 0f, 0f))
			contentView?.addView(bottomBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.BOTTOM, 0f, 0f, 0f, 0f))

			selectDaysButton?.background = Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(context.getColor(R.color.brand), (0.2f * 255).toInt()), 2)
			removeDaysButton?.background = Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(context.getColor(R.color.purple), (0.2f * 255).toInt()), 2)
			selectDaysButton?.setTextColor(context.getColor(R.color.brand))
			removeDaysButton?.setTextColor(context.getColor(R.color.purple))
		}

		return fragmentView
	}

	private fun loadNext() {
		if (loading || endReached) {
			return
		}

		loading = true

		val req = TLRPC.TLMessagesGetSearchResultsCalendar()

		when (photosVideosTypeFilter) {
			SharedMediaLayout.FILTER_PHOTOS_ONLY -> req.filter = TLRPC.TLInputMessagesFilterPhotos()
			SharedMediaLayout.FILTER_VIDEOS_ONLY -> req.filter = TLRPC.TLInputMessagesFilterVideo()
			else -> req.filter = TLRPC.TLInputMessagesFilterPhotoVideo()
		}

		req.peer = messagesController.getInputPeer(dialogId)
		req.offsetId = lastId

		val calendar = Calendar.getInstance()

		listView?.itemAnimator = null

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is TLRPC.TLMessagesSearchResultsCalendar) {
					for (i in response.periods.indices) {
						val period: TLRPC.TLSearchResultsCalendarPeriod = response.periods[i]

						calendar.timeInMillis = period.date * 1000L

						val month: Int = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH)
						var messagesByDays = messagesByYearMouth.get(month)

						if (messagesByDays == null) {
							messagesByDays = SparseArray()

							messagesByYearMouth.put(month, messagesByDays)
						}

						val periodDay = PeriodDay()
						val messageObject = MessageObject(currentAccount, response.messages[i], generateLayout = false, checkMediaExists = false)

						periodDay.messageObject = messageObject
						periodDay.date = (calendar.timeInMillis / 1000L).toInt()

						startOffset += response.periods[i].count

						periodDay.startOffset = startOffset

						val index: Int = calendar.get(Calendar.DAY_OF_MONTH) - 1

						if (messagesByDays.get(index, null) == null || !messagesByDays.get(index, null)!!.hasImage) {
							messagesByDays.put(index, periodDay)
						}

						if (month < minMontYear || minMontYear == 0) {
							minMontYear = month
						}
					}

					val maxDate = (System.currentTimeMillis() / 1000L).toInt()

					minDate = response.minDate

					var date = response.minDate

					while (date < maxDate) {
						calendar.timeInMillis = date * 1000L
						calendar.set(Calendar.HOUR_OF_DAY, 0)
						calendar.set(Calendar.MINUTE, 0)
						calendar.set(Calendar.SECOND, 0)
						calendar.set(Calendar.MILLISECOND, 0)

						val month: Int = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH)
						var messagesByDays = messagesByYearMouth.get(month)

						if (messagesByDays == null) {
							messagesByDays = SparseArray()
							messagesByYearMouth.put(month, messagesByDays)
						}

						val index: Int = calendar.get(Calendar.DAY_OF_MONTH) - 1

						if (messagesByDays.get(index, null) == null) {
							val periodDay = PeriodDay()
							periodDay.hasImage = false
							periodDay.date = (calendar.timeInMillis / 1000L).toInt()
							messagesByDays.put(index, periodDay)
						}

						date += 86400
					}

					loading = false

					if (response.messages.isNotEmpty()) {
						lastId = response.messages[response.messages.size - 1].id
						endReached = false
						checkLoadNext()
					}
					else {
						endReached = true
					}

					if (isOpened) {
						checkEnterItems = true
					}

					listView?.invalidate()

					val newMonthCount: Int = (((calendar.timeInMillis / 1000) - response.minDate) / 2629800).toInt() + 1

					adapter.notifyItemRangeChanged(0, monthCount)

					if (newMonthCount > monthCount) {
						adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount)
						monthCount = newMonthCount
					}

					if (endReached) {
						resumeDelayedFragmentAnimation()
					}
				}
			}
		}
	}

	private fun checkLoadNext() {
		if (loading || endReached) {
			return
		}

		var listMinMonth = Int.MAX_VALUE

		listView?.children?.forEach { child ->
			if (child is MonthView) {
				val currentMonth = child.currentYear * 100 + child.currentMonthInYear

				if (currentMonth < listMinMonth) {
					listMinMonth = currentMonth
				}
			}
		}

		val min1 = (minMontYear / 100 * 12) + minMontYear % 100
		val min2 = (listMinMonth / 100 * 12) + listMinMonth % 100

		if (min1 + 3 >= min2) {
			loadNext()
		}
	}

	inner class CalendarAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			return RecyclerListView.Holder(MonthView(parent.context))
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val monthView = holder.itemView as MonthView
			var year = startFromYear - position / 12
			var month = startFromMonth - position % 12

			if (month < 0) {
				month += 12
				year--
			}

			monthView.setDate(year, month, messagesByYearMouth[year * 100 + month])
			monthView.startSelectionAnimation(dateSelectedStart, dateSelectedEnd)
			monthView.setSelectionValue(1f)

			updateRowSelections(monthView, false)
		}

		override fun getItemId(position: Int): Long {
			val year = startFromYear - position / 12
			val month = startFromMonth - position % 12
			return year * 100L + month
		}

		override fun getItemCount(): Int {
			return monthCount
		}
	}

	private inner class MonthView(context: Context) : FrameLayout(context) {
		val titleView: SimpleTextView
		var currentYear = 0
		var currentMonthInYear = 0
		var daysInMonth = 0
		var startDayOfWeek = 0
		var cellCount = 0
		var startMonthTime = 0
		var messagesByDays: SparseArray<PeriodDay>? = SparseArray()
		var imagesByDays: SparseArray<ImageReceiver>? = SparseArray()
		var attached = false
		var gestureDetector: GestureDetector

		fun startSelectionAnimation(fromDate: Int, toDate: Int) {
			val messagesByDays = messagesByDays ?: return

			for (i in 0 until daysInMonth) {
				val day = messagesByDays[i, null]

				if (day != null) {
					day.fromSelProgress = day.selectProgress
					day.toSelProgress = if (day.date in fromDate..toDate) 1f else 0f
					day.fromSelSEProgress = day.selectStartEndProgress

					if (day.date == fromDate || day.date == toDate) {
						day.toSelSEProgress = 1f
					}
					else {
						day.toSelSEProgress = 0f
					}
				}
			}
		}

		fun setSelectionValue(f: Float) {
			val messagesByDays = messagesByDays

			if (messagesByDays != null) {
				for (i in 0 until daysInMonth) {
					val day = messagesByDays[i, null]

					if (day != null) {
						day.selectProgress = day.fromSelProgress + (day.toSelProgress - day.fromSelProgress) * f
						day.selectStartEndProgress = day.fromSelSEProgress + (day.toSelSEProgress - day.fromSelSEProgress) * f
					}
				}
			}

			invalidate()
		}

		private val rowAnimators = SparseArray<ValueAnimator>()
		private val rowSelectionPos = SparseArray<RowAnimationValue>()

		init {
			setWillNotDraw(false)

			titleView = SimpleTextView(context)

			if (calendarType == TYPE_CHAT_ACTIVITY && canClearHistory) {
				titleView.setOnLongClickListener {
					if (messagesByDays == null) {
						return@setOnLongClickListener false
					}

					var start = -1
					var end = -1

					for (i in 0 until daysInMonth) {
						val day = messagesByDays?.get(i, null)

						if (day != null) {
							if (start == -1) {
								start = day.date
							}

							end = day.date
						}
					}

					if (start >= 0 && end >= 0) {
						inSelectionMode = true
						dateSelectedStart = start
						dateSelectedEnd = end
						updateTitle()
						animateSelection()
					}

					false
				}

				titleView.setOnClickListener(OnClickListener {
					if (messagesByDays == null) {
						return@OnClickListener
					}

					if (inSelectionMode) {
						var start = -1
						var end = -1

						for (i in 0 until daysInMonth) {
							val day = messagesByDays?.get(i, null)

							if (day != null) {
								if (start == -1) {
									start = day.date
								}

								end = day.date
							}
						}

						if (start >= 0 && end >= 0) {
							dateSelectedStart = start
							dateSelectedEnd = end
							updateTitle()
							animateSelection()
						}
					}
				})
			}

			titleView.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background), 2)
			titleView.setTextSize(15)
			titleView.setTypeface(Theme.TYPEFACE_BOLD)
			titleView.setGravity(Gravity.CENTER)
			titleView.textColor = context.getColor(R.color.text)

			addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28f, 0, 0f, 12f, 0f, 4f))

			gestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
				override fun onDown(e: MotionEvent): Boolean {
					return true
				}

				@SuppressLint("NotifyDataSetChanged")
				override fun onSingleTapUp(e: MotionEvent): Boolean {
					if (calendarType == TYPE_MEDIA_CALENDAR && messagesByDays != null) {
						val day = getDayAtCoord(e.x, e.y)

						if (day?.messageObject != null && callback != null) {
							callback?.onDateSelected(day.messageObject!!.id, day.startOffset)
							finishFragment()
						}
					}

					if (messagesByDays != null) {
						if (inSelectionMode) {
							val day = getDayAtCoord(e.x, e.y)

							if (day != null) {
								selectionAnimator?.cancel()
								selectionAnimator = null

								if (dateSelectedStart != 0 || dateSelectedEnd != 0) {
									if (dateSelectedStart == day.date && dateSelectedEnd == day.date) {
										dateSelectedEnd = 0
										dateSelectedStart = 0
									}
									else if (dateSelectedStart == day.date) {
										dateSelectedStart = dateSelectedEnd
									}
									else if (dateSelectedEnd == day.date) {
										dateSelectedEnd = dateSelectedStart
									}
									else if (dateSelectedStart == dateSelectedEnd) {
										if (day.date > dateSelectedEnd) {
											dateSelectedEnd = day.date
										}
										else {
											dateSelectedStart = day.date
										}
									}
									else {
										dateSelectedEnd = day.date
										dateSelectedStart = dateSelectedEnd
									}
								}
								else {
									dateSelectedEnd = day.date
									dateSelectedStart = dateSelectedEnd
								}

								updateTitle()
								animateSelection()
							}
						}
						else {
							val day = getDayAtCoord(e.x, e.y)
							val fragmentsStack = parentLayout?.fragmentsStack

							if (fragmentsStack != null) {
								if (day != null && fragmentsStack.size >= 2) {
									val fragment = fragmentsStack[fragmentsStack.size - 2]

									if (fragment is ChatActivity) {
										finishFragment()

										fragment.jumpToDate(day.date)
									}
								}
							}
						}
					}

					return false
				}

				private fun getDayAtCoord(pressedX: Float, pressedY: Float): PeriodDay? {
					val messagesByDays = messagesByDays ?: return null
					var currentCell = 0
					var currentColumn = startDayOfWeek
					val xStep = measuredWidth / 7f
					val yStep = AndroidUtilities.dp((44 + 8).toFloat()).toFloat()
					val hrad = AndroidUtilities.dp(44f) / 2

					for (i in 0 until daysInMonth) {
						val cx = xStep * currentColumn + xStep / 2f
						val cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44f)

						if (pressedX >= cx - hrad && pressedX <= cx + hrad && pressedY >= cy - hrad && pressedY <= cy + hrad) {
							val day = messagesByDays[i, null]

							if (day != null) {
								return day
							}
						}

						currentColumn++

						if (currentColumn >= 7) {
							currentColumn = 0
							currentCell++
						}
					}
					return null
				}

				override fun onLongPress(e: MotionEvent) {
					super.onLongPress(e)

					if (calendarType != TYPE_CHAT_ACTIVITY) {
						return
					}

					val periodDay = getDayAtCoord(e.x, e.y)

					if (periodDay != null) {
						performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

						val bundle = Bundle()

						if (dialogId > 0) {
							bundle.putLong("user_id", dialogId)
						}
						else {
							bundle.putLong("chat_id", -dialogId)
						}

						bundle.putInt("start_from_date", periodDay.date)
						bundle.putBoolean("need_remove_previous_same_chat_activity", false)

						val chatActivity = ChatActivity(bundle)

						val previewMenu = ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert)
						previewMenu.setBackgroundColor(context.getColor(R.color.background))

						val cellJump = ActionBarMenuSubItem(context, top = true, bottom = false)
						cellJump.setTextAndIcon(context.getString(R.string.JumpToDate), R.drawable.msg_message)
						cellJump.minimumWidth = 160

						cellJump.setOnClickListener {
							val fragmentsStack = parentLayout?.fragmentsStack

							if (fragmentsStack != null) {
								if (fragmentsStack.size >= 3) {
									val fragment = fragmentsStack[fragmentsStack.size - 3]

									if (fragment is ChatActivity) {
										AndroidUtilities.runOnUIThread({
											finishFragment()
											fragment.jumpToDate(periodDay.date)
										}, 300)
									}
								}
							}

							finishPreviewFragment()
						}

						previewMenu.addView(cellJump)

						if (canClearHistory) {
							val cellSelect = ActionBarMenuSubItem(context, top = false, bottom = false)
							cellSelect.setTextAndIcon(context.getString(R.string.SelectThisDay), R.drawable.msg_select)
							cellSelect.minimumWidth = 160

							cellSelect.setOnClickListener {
								dateSelectedEnd = periodDay.date
								dateSelectedStart = dateSelectedEnd
								inSelectionMode = true

								updateTitle()
								animateSelection()
								finishPreviewFragment()
							}

							previewMenu.addView(cellSelect)

							val cellDelete = ActionBarMenuSubItem(context, top = false, bottom = true)
							cellDelete.setTextAndIcon(context.getString(R.string.ClearHistory), R.drawable.msg_delete)
							cellDelete.minimumWidth = 160

							cellDelete.setOnClickListener {
								val fragmentsStack = parentLayout?.fragmentsStack

								if (fragmentsStack != null) {
									if (fragmentsStack.size >= 3) {
										val fragment = fragmentsStack[fragmentsStack.size - 3]

										if (fragment is ChatActivity) {
											AlertsCreator.createClearDaysDialogAlert(this@CalendarActivity, 1, messagesController.getUser(dialogId), null, false) { forAll ->
												finishFragment()
												fragment.deleteHistory(dateSelectedStart, dateSelectedEnd + 86400, forAll)
											}
										}
									}
								}

								finishPreviewFragment()
							}

							previewMenu.addView(cellDelete)
						}

						previewMenu.setFitItems(true)

						blurredView = object : View(context) {
							override fun setAlpha(alpha: Float) {
								super.setAlpha(alpha)
								fragmentView?.invalidate()
							}
						}

						blurredView?.setOnClickListener {
							finishPreviewFragment()
						}

						blurredView?.gone()
						blurredView?.fitsSystemWindows = true

						parentLayout?.containerView?.addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

						prepareBlurBitmap()

						presentFragmentAsPreviewWithMenu(chatActivity, previewMenu)
					}
				}
			})

			gestureDetector.setIsLongpressEnabled(calendarType == TYPE_CHAT_ACTIVITY)
		}

		fun dismissRowAnimations(animate: Boolean) {
			for (i in 0 until rowSelectionPos.size) {
				animateRow(rowSelectionPos.keyAt(i), 0, 0, false, animate)
			}
		}

		fun animateRow(row: Int, startColumn: Int, endColumn: Int, appear: Boolean, animate: Boolean) {
			val a = rowAnimators[row]
			a?.cancel()

			val xStep = measuredWidth / 7f
			val cxFrom1: Float
			val cxFrom2: Float
			val fromAlpha: Float
			val p = rowSelectionPos[row]

			if (p != null) {
				cxFrom1 = p.startX
				cxFrom2 = p.endX
				fromAlpha = p.alpha
			}
			else {
				cxFrom1 = xStep * startColumn + xStep / 2f
				cxFrom2 = xStep * startColumn + xStep / 2f
				fromAlpha = 0f
			}

			val cxTo1 = if (appear) xStep * startColumn + xStep / 2f else cxFrom1
			val cxTo2 = if (appear) xStep * endColumn + xStep / 2f else cxFrom2
			val toAlpha = (if (appear) 1 else 0).toFloat()
			val pr = RowAnimationValue(cxFrom1, cxFrom2)

			rowSelectionPos.put(row, pr)

			if (animate) {
				val anim = ValueAnimator.ofFloat(0f, 1f).setDuration(300)
				anim.interpolator = Easings.easeInOutQuad

				anim.addUpdateListener {
					val `val` = it.animatedValue as Float
					pr.startX = cxFrom1 + (cxTo1 - cxFrom1) * `val`
					pr.endX = cxFrom2 + (cxTo2 - cxFrom2) * `val`
					pr.alpha = fromAlpha + (toAlpha - fromAlpha) * `val`

					invalidate()
				}

				anim.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationCancel(animation: Animator) {
						pr.startX = cxTo1
						pr.endX = cxTo2
						pr.alpha = toAlpha
						invalidate()
					}

					override fun onAnimationEnd(animation: Animator) {
						rowAnimators.remove(row)

						if (!appear) {
							rowSelectionPos.remove(row)
						}
					}
				})

				anim.start()

				rowAnimators.put(row, anim)
			}
			else {
				pr.startX = cxTo1
				pr.endX = cxTo2
				pr.alpha = toAlpha

				invalidate()
			}
		}

		@SuppressLint("ClickableViewAccessibility")
		override fun onTouchEvent(event: MotionEvent): Boolean {
			return gestureDetector.onTouchEvent(event)
		}

		fun setDate(year: Int, monthInYear: Int, messagesByDays: SparseArray<PeriodDay>?) {
			val dateChanged = year != currentYear || monthInYear != currentMonthInYear

			currentYear = year
			currentMonthInYear = monthInYear

			this.messagesByDays = messagesByDays

			if (dateChanged) {
				imagesByDays?.let {
					for (i in 0 until it.size) {
						it.valueAt(i)?.onDetachedFromWindow()
						it.valueAt(i)?.setParentView(null)
					}
				}

				imagesByDays = null
			}

			if (messagesByDays != null) {
				if (imagesByDays == null) {
					imagesByDays = SparseArray()
				}

				for (i in 0 until messagesByDays.size) {
					val key = messagesByDays.keyAt(i)

					if (imagesByDays!![key, null] != null || !messagesByDays[key]!!.hasImage) {
						continue
					}

					val receiver = ImageReceiver()
					receiver.setParentView(this)
					val periodDay = messagesByDays[key]
					val messageObject = periodDay?.messageObject

					if (messageObject != null) {
						if (messageObject.isVideo) {
							val document = messageObject.document
							val thumb = FileLoader.getClosestPhotoSizeWithSize(document?.thumbs, 50)
							var qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document?.thumbs, 320)

							if (thumb === qualityThumb) {
								qualityThumb = null
							}
							if (thumb != null) {
								if (messageObject.strippedThumb != null) {
									receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", messageObject.strippedThumb, null, messageObject, 0)
								}
								else {
									receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", ImageLocation.getForDocument(thumb, document), "b", null as String?, messageObject, 0)
								}
							}
						}
						else if ((messageObject.messageOwner?.media as? TLRPC.TLMessageMediaPhoto)?.photo != null && !messageObject.photoThumbs.isNullOrEmpty()) {
							var currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50)
							val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false)

							if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
								if (currentPhotoObject === currentPhotoObjectThumb) {
									currentPhotoObjectThumb = null
								}

								if (messageObject.strippedThumb != null) {
									receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", null, null, messageObject.strippedThumb, (currentPhotoObject?.size ?: 0).toLong(), null, messageObject, if (messageObject.shouldEncryptPhotoOrVideo()) 2 else 1)
								}
								else {
									receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", (currentPhotoObject?.size ?: 0).toLong(), null, messageObject, if (messageObject.shouldEncryptPhotoOrVideo()) 2 else 1)
								}
							}
							else {
								if (messageObject.strippedThumb != null) {
									receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0)
								}
								else {
									receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", null as String?, messageObject, 0)
								}
							}
						}

						receiver.setRoundRadius(AndroidUtilities.dp(22f))

						imagesByDays?.put(key, receiver)
					}
				}
			}

			val yearMonthObject = YearMonth.of(year, monthInYear + 1)

			daysInMonth = yearMonthObject.lengthOfMonth()

			val calendar = Calendar.getInstance()
			calendar[year, monthInYear] = 0

			startDayOfWeek = (calendar[Calendar.DAY_OF_WEEK] + 6) % 7
			startMonthTime = (calendar.timeInMillis / 1000L).toInt()

			val totalColumns = daysInMonth + startDayOfWeek

			cellCount = (totalColumns / 7f).toInt() + (if (totalColumns % 7 == 0) 0 else 1)

			calendar[year, monthInYear + 1] = 0

			titleView.setText(LocaleController.formatYearMont(calendar.timeInMillis / 1000, true))

			updateRowSelections(this, false)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp((cellCount * (44 + 8) + 44).toFloat()), MeasureSpec.EXACTLY))
		}

		override fun onDraw(canvas: Canvas) {
			super.onDraw(canvas)

			var currentCell = 0
			var currentColumn = startDayOfWeek
			val xStep = measuredWidth / 7f
			val yStep = AndroidUtilities.dp((44 + 8).toFloat()).toFloat()
			val selSize = AndroidUtilities.dp(44f)
			var row = 0

			while (row < ceil(((startDayOfWeek + daysInMonth) / 7f).toDouble())) {
				val cy = yStep * row + yStep / 2f + AndroidUtilities.dp(44f)
				val v = rowSelectionPos[row]

				if (v != null) {
					selectPaint.color = context.getColor(R.color.brand)
					selectPaint.alpha = (v.alpha * (255 * 0.16f)).toInt()

					AndroidUtilities.rectTmp.set(v.startX - selSize / 2f, cy - selSize / 2f, v.endX + selSize / 2f, cy + selSize / 2f)

					val dp = AndroidUtilities.dp(32f)

					canvas.drawRoundRect(AndroidUtilities.rectTmp, dp.toFloat(), dp.toFloat(), selectPaint)
				}
				row++
			}

			for (i in 0 until daysInMonth) {
				val cx = xStep * currentColumn + xStep / 2f
				val cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44f)
				val nowTime = (System.currentTimeMillis() / 1000L).toInt()

				val day = messagesByDays?.get(i, null)

				if (nowTime < startMonthTime + (i + 1) * 86400 || (minDate > 0 && minDate > startMonthTime + (i + 2) * 86400)) {
					val oldAlpha = textPaint.alpha
					textPaint.alpha = (oldAlpha * 0.3f).toInt()
					canvas.drawText((i + 1).toString(), cx, cy + AndroidUtilities.dp(5f), textPaint)
					textPaint.alpha = oldAlpha
				}
				else if (day != null && day.hasImage) {
					var alpha = 1f

					if (imagesByDays!![i] != null) {
						if (checkEnterItems && !day.wasDrawn) {
							day.enterAlpha = 0f
							day.startEnterDelay = max(0.0, ((cy + y) / listView!!.measuredHeight * 150).toDouble()).toFloat()
						}

						if (day.startEnterDelay > 0) {
							day.startEnterDelay -= 16f

							if (day.startEnterDelay < 0) {
								day.startEnterDelay = 0f
							}
							else {
								invalidate()
							}
						}

						if (day.startEnterDelay >= 0 && day.enterAlpha != 1f) {
							day.enterAlpha += 16 / 220f

							if (day.enterAlpha > 1f) {
								day.enterAlpha = 1f
							}
							else {
								invalidate()
							}
						}

						alpha = day.enterAlpha

						if (alpha != 1f) {
							canvas.save()

							val s = 0.8f + 0.2f * alpha

							canvas.scale(s, s, cx, cy)
						}

						val pad = (AndroidUtilities.dp(7f) * day.selectProgress).toInt()

						if (day.selectStartEndProgress >= 0.01f) {
							selectPaint.color = context.getColor(R.color.background)
							selectPaint.alpha = (day.selectStartEndProgress * 0xFF).toInt()

							canvas.drawCircle(cx, cy, AndroidUtilities.dp(44f) / 2f, selectPaint)

							selectOutlinePaint.color = context.getColor(R.color.brand)

							AndroidUtilities.rectTmp.set(cx - AndroidUtilities.dp(44f) / 2f, cy - AndroidUtilities.dp(44f) / 2f, cx + AndroidUtilities.dp(44f) / 2f, cy + AndroidUtilities.dp(44f) / 2f)

							canvas.drawArc(AndroidUtilities.rectTmp, -90f, day.selectStartEndProgress * 360, false, selectOutlinePaint)
						}

						imagesByDays!![i]!!.alpha = day.enterAlpha
						imagesByDays!![i]!!.setImageCoordinates(cx - (AndroidUtilities.dp(44f) - pad) / 2f, cy - (AndroidUtilities.dp(44f) - pad) / 2f, (AndroidUtilities.dp(44f) - pad).toFloat(), (AndroidUtilities.dp(44f) - pad).toFloat())
						imagesByDays!![i]!!.draw(canvas)

						blackoutPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, (day.enterAlpha * 80).toInt())

						canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44f) - pad) / 2f, blackoutPaint)

						day.wasDrawn = true

						if (alpha != 1f) {
							canvas.restore()
						}
					}

					if (alpha != 1f) {
						var oldAlpha = textPaint.alpha

						textPaint.alpha = (oldAlpha * (1f - alpha)).toInt()

						canvas.drawText((i + 1).toString(), cx, cy + AndroidUtilities.dp(5f), textPaint)

						textPaint.alpha = oldAlpha

						oldAlpha = textPaint.alpha
						activeTextPaint.alpha = (oldAlpha * alpha).toInt()
						canvas.drawText((i + 1).toString(), cx, cy + AndroidUtilities.dp(5f), activeTextPaint)
						activeTextPaint.alpha = oldAlpha
					}
					else {
						canvas.drawText((i + 1).toString(), cx, cy + AndroidUtilities.dp(5f), activeTextPaint)
					}
				}
				else {
					if (day != null && day.selectStartEndProgress >= 0.01f) {
						selectPaint.color = context.getColor(R.color.background)
						selectPaint.alpha = (day.selectStartEndProgress * 0xFF).toInt()

						canvas.drawCircle(cx, cy, AndroidUtilities.dp(44f) / 2f, selectPaint)

						selectOutlinePaint.color = context.getColor(R.color.brand)
						AndroidUtilities.rectTmp.set(cx - AndroidUtilities.dp(44f) / 2f, cy - AndroidUtilities.dp(44f) / 2f, cx + AndroidUtilities.dp(44f) / 2f, cy + AndroidUtilities.dp(44f) / 2f)

						canvas.drawArc(AndroidUtilities.rectTmp, -90f, day.selectStartEndProgress * 360, false, selectOutlinePaint)

						val pad = (AndroidUtilities.dp(7f) * day.selectStartEndProgress).toInt()

						selectPaint.color = context.getColor(R.color.brand)
						selectPaint.alpha = (day.selectStartEndProgress * 0xFF).toInt()

						canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44f) - pad) / 2f, selectPaint)

						val alpha = day.selectStartEndProgress

						if (alpha != 1f) {
							var oldAlpha = textPaint.alpha
							textPaint.alpha = (oldAlpha * (1f - alpha)).toInt()
							canvas.drawText((i + 1).toString(), cx, cy + AndroidUtilities.dp(5f), textPaint)
							textPaint.alpha = oldAlpha

							oldAlpha = textPaint.alpha
							activeTextPaint.alpha = (oldAlpha * alpha).toInt()
							canvas.drawText((i + 1).toString(), cx, cy + AndroidUtilities.dp(5f), activeTextPaint)
							activeTextPaint.alpha = oldAlpha
						}
						else {
							canvas.drawText((i + 1).toString(), cx, cy + AndroidUtilities.dp(5f), activeTextPaint)
						}
					}
					else {
						canvas.drawText((i + 1).toString(), cx, cy + AndroidUtilities.dp(5f), textPaint)
					}
				}

				currentColumn++

				if (currentColumn >= 7) {
					currentColumn = 0
					currentCell++
				}
			}
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()

			attached = true

			imagesByDays?.valueIterator()?.forEach {
				it.onAttachedToWindow()
			}
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()

			attached = false

			imagesByDays?.valueIterator()?.forEach {
				it.onDetachedFromWindow()
			}
		}
	}

	private var lastDaysSelected = 0
	private var lastInSelectionMode = false

	init {
		if (selectedDate != 0) {
			val calendar = Calendar.getInstance()
			calendar.timeInMillis = selectedDate * 1000L

			selectedYear = calendar[Calendar.YEAR]
			selectedMonth = calendar[Calendar.MONTH]
		}

		selectOutlinePaint.style = Paint.Style.STROKE
		selectOutlinePaint.strokeCap = Paint.Cap.ROUND
		selectOutlinePaint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
	}

	private fun updateTitle() {
		if (!canClearHistory) {
			actionBar?.setTitle(context?.getString(R.string.Calendar))
			return
		}

		val daysSelected = if (dateSelectedStart == dateSelectedEnd && dateSelectedStart == 0) {
			0
		}
		else {
			(1 + (abs((dateSelectedStart - dateSelectedEnd).toDouble()) / 86400)).toInt()
		}

		if (daysSelected != lastDaysSelected || lastInSelectionMode != inSelectionMode) {
			val fromBottom = lastDaysSelected > daysSelected
			lastDaysSelected = daysSelected
			lastInSelectionMode = inSelectionMode

			val title = if (daysSelected > 0) {
				LocaleController.formatPluralString("Days", daysSelected)
			}
			else if (inSelectionMode) {
				context?.getString(R.string.SelectDays)
			}
			else {
				context?.getString(R.string.Calendar)
			}

			if (daysSelected > 1) {
				removeDaysButton?.text = LocaleController.formatString("ClearHistoryForTheseDays", R.string.ClearHistoryForTheseDays)
			}
			else if (daysSelected > 0 || inSelectionMode) {
				removeDaysButton?.text = LocaleController.formatString("ClearHistoryForThisDay", R.string.ClearHistoryForThisDay)
			}

			actionBar?.setTitleAnimated(title, fromBottom, 150)

			if (!inSelectionMode || daysSelected > 0) {
				selectDaysHint?.hide()
			}

			if (daysSelected > 0 || inSelectionMode) {
				if (removeDaysButton?.visibility == View.GONE) {
					removeDaysButton?.alpha = 0f
					removeDaysButton?.translationY = -AndroidUtilities.dp(20f).toFloat()
				}

				removeDaysButton?.visibility = View.VISIBLE
				selectDaysButton?.animate()?.setListener(null)?.cancel()
				removeDaysButton?.animate()?.setListener(null)?.cancel()
				selectDaysButton?.animate()?.alpha(0f)?.translationY(AndroidUtilities.dp(20f).toFloat())?.setDuration(150)?.setListener(HideViewAfterAnimation(selectDaysButton!!))?.start()
				removeDaysButton?.animate()?.alpha(if (daysSelected == 0) 0.5f else 1f)?.translationY(0f)?.start()
				selectDaysButton?.isEnabled = false
				removeDaysButton?.isEnabled = true
			}
			else {
				if (selectDaysButton?.visibility == View.GONE) {
					selectDaysButton?.alpha = 0f
					selectDaysButton?.translationY = AndroidUtilities.dp(20f).toFloat()
				}

				selectDaysButton?.visibility = View.VISIBLE
				selectDaysButton?.animate()?.setListener(null)?.cancel()
				removeDaysButton?.animate()?.setListener(null)?.cancel()
				selectDaysButton?.animate()?.alpha(1f)?.translationY(0f)?.start()
				removeDaysButton?.animate()?.alpha(0f)?.translationY(-AndroidUtilities.dp(20f).toFloat())?.setDuration(150)?.setListener(HideViewAfterAnimation(removeDaysButton!!))?.start()
				selectDaysButton?.isEnabled = true
				removeDaysButton?.isEnabled = false
			}
		}
	}

	fun setCallback(callback: Callback?) {
		this.callback = callback
	}

	fun interface Callback {
		fun onDateSelected(messageId: Int, startOffset: Int)
	}

	inner class PeriodDay {
		var messageObject: MessageObject? = null
		var startOffset = 0
		var enterAlpha = 1f
		var startEnterDelay = 1f
		var wasDrawn = false
		var hasImage = true
		var date = 0
		var selectStartEndProgress = 0f
		var fromSelSEProgress = 0f
		var toSelSEProgress = 0f
		var selectProgress = 0f
		var fromSelProgress = 0f
		var toSelProgress = 0f
	}

	override fun needDelayOpenAnimation(): Boolean {
		return true
	}

	override fun onTransitionAnimationStart(isOpen: Boolean, backward: Boolean) {
		super.onTransitionAnimationStart(isOpen, backward)
		isOpened = true
	}

	override fun onTransitionAnimationProgress(isOpen: Boolean, progress: Float) {
		super.onTransitionAnimationProgress(isOpen, progress)

		val blurredView = blurredView ?: return

		if (blurredView.isVisible) {
			if (isOpen) {
				blurredView.alpha = 1.0f - progress
			}
			else {
				blurredView.alpha = progress
			}
		}
	}

	override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
		val blurredView = blurredView ?: return

		if (isOpen && blurredView.isVisible) {
			blurredView.visibility = View.GONE
			blurredView.background = null
		}
	}

	private fun animateSelection() {
		val listView = listView ?: return

		val a = ValueAnimator.ofFloat(0f, 1f).setDuration(300)
		a.interpolator = CubicBezierInterpolator.DEFAULT

		a.addUpdateListener {
			val selectProgress = it.animatedValue as Float

			listView.children.forEach { child ->
				(child as? MonthView)?.setSelectionValue(selectProgress)
			}
		}

		a.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationStart(animation: Animator) {
				listView.children.forEach {
					(it as? MonthView)?.startSelectionAnimation(dateSelectedStart, dateSelectedEnd)
				}
			}
		})

		a.start()

		selectionAnimator = a

		listView.children.forEach {
			(it as? MonthView)?.let { m ->
				updateRowSelections(m, true)
			}
		}

		for (j in 0 until listView.cachedChildCount) {
			val m = listView.getCachedChildAt(j) as MonthView
			updateRowSelections(m, false)
			m.startSelectionAnimation(dateSelectedStart, dateSelectedEnd)
			m.setSelectionValue(1f)
		}

		for (j in 0 until listView.hiddenChildCount) {
			val m = listView.getHiddenChildAt(j) as MonthView
			updateRowSelections(m, false)
			m.startSelectionAnimation(dateSelectedStart, dateSelectedEnd)
			m.setSelectionValue(1f)
		}

		for (j in 0 until listView.attachedScrapChildCount) {
			val m = listView.getAttachedScrapChildAt(j) as MonthView
			updateRowSelections(m, false)
			m.startSelectionAnimation(dateSelectedStart, dateSelectedEnd)
			m.setSelectionValue(1f)
		}
	}

	private fun updateRowSelections(m: MonthView, animate: Boolean) {
		if (dateSelectedStart == 0 || dateSelectedEnd == 0) {
			m.dismissRowAnimations(animate)
		}
		else {
			if (m.messagesByDays == null) {
				return
			}
			if (!animate) {
				m.dismissRowAnimations(false)
			}

			var row = 0
			var dayInRow = m.startDayOfWeek
			var sDay = -1
			var eDay = -1

			for (i in 0 until m.daysInMonth) {
				val day = m.messagesByDays!![i, null]

				if (day != null) {
					if (day.date in dateSelectedStart..dateSelectedEnd) {
						if (sDay == -1) {
							sDay = dayInRow
						}

						eDay = dayInRow
					}
				}

				dayInRow++

				if (dayInRow >= 7) {
					dayInRow = 0

					if (sDay != -1 && eDay != -1) {
						m.animateRow(row, sDay, eDay, true, animate)
					}
					else {
						m.animateRow(row, 0, 0, false, animate)
					}

					row++
					sDay = -1
					eDay = -1
				}
			}

			if (sDay != -1 && eDay != -1) {
				m.animateRow(row, sDay, eDay, true, animate)
			}
			else {
				m.animateRow(row, 0, 0, false, animate)
			}
		}
	}

	private class RowAnimationValue(var startX: Float, var endX: Float) {
		var alpha = 0f
	}

	private fun prepareBlurBitmap() {
		val blurredView = blurredView ?: return
		val parentLayout = parentLayout ?: return
		val w = (parentLayout.measuredWidth / 6.0f).toInt()
		val h = (parentLayout.measuredHeight / 6.0f).toInt()
		val bitmap = createBitmap(w, h)

		val canvas = Canvas(bitmap)
		canvas.scale(1.0f / 6.0f, 1.0f / 6.0f)

		parentLayout.draw(canvas)

		Utilities.stackBlurBitmap(bitmap, max(7.0, (max(w.toDouble(), h.toDouble()) / 180)).toInt())

		blurredView.background = bitmap.toDrawable(blurredView.context.resources)
		blurredView.alpha = 0.0f
		blurredView.visibility = View.VISIBLE
	}

	override fun onBackPressed(): Boolean {
		if (inSelectionMode) {
			inSelectionMode = false
			dateSelectedEnd = 0
			dateSelectedStart = 0

			updateTitle()
			animateSelection()

			return false
		}

		return super.onBackPressed()
	}

	companion object {
		const val TYPE_CHAT_ACTIVITY = 0
		const val TYPE_MEDIA_CALENDAR = 1
	}
}
