/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.*
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.SystemClock
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.util.SparseIntArray
import android.util.StateSet
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.util.Consumer
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.*
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

open class RecyclerListView(context: Context) : RecyclerView(context) {
	@Retention(AnnotationRetention.SOURCE)
	@IntDef(SECTIONS_TYPE_SIMPLE, SECTIONS_TYPE_STICKY_HEADERS, SECTIONS_TYPE_DATE, SECTIONS_TYPE_FAST_SCROLL_ONLY)
	annotation class SectionsType

	var onItemClickListener: OnItemClickListener? = null
		private set

	private var onItemClickListenerExtended: OnItemClickListenerExtended? = null
	private var onItemLongClickListener: OnItemLongClickListener? = null
	private var onItemLongClickListenerExtended: OnItemLongClickListenerExtended? = null
	private var longPressCalled = false
	private var onScrollListener: OnScrollListener? = null
	private var onInterceptTouchListener: OnInterceptTouchListener? = null

	fun setOnItemClickListener(listener: OnItemClickListener?) {
		onItemClickListener = listener
	}

	fun setOnItemClickListener(listener: OnItemClickListenerExtended?) {
		onItemClickListenerExtended = listener
	}

	var emptyView: View? = null
		private set

	private var overlayContainer: FrameLayout? = null
	private var selectChildRunnable: Runnable? = null

	var fastScroll: FastScroll? = null
		private set

	private var sectionsAdapter: SectionsAdapter? = null
	private var isHidden = false
	private var disableHighlightState = false
	private var allowItemsInteractionDuringAnimation = true
	private var pinnedHeaderShadowDrawable: Drawable? = null
	private var pinnedHeaderShadowAlpha = 0f
	private var pinnedHeaderShadowTargetAlpha = 0f
	private var lastAlphaAnimationTime: Long = 0

	var headers: ArrayList<View?>? = null
		private set

	var headersCache: ArrayList<View?>? = null
		private set

	var pinnedHeader: View? = null
		private set

	private var currentFirst = -1
	private var currentVisible = -1
	private var startSection = 0
	private var sectionsCount = 0
	private var sectionOffset = 0

	@SectionsType
	private var sectionsType = 0

	private var hideIfEmpty = true
	private var drawSelectorBehind = false
	private var selectorType = 2

	var selectorDrawable: Drawable? = Theme.getSelectorDrawable(ResourcesCompat.getColor(resources, R.color.light_background, null), false)
		protected set

	@JvmField
	protected var selectorPosition = 0

	var selectorRect = Rect()
		protected set

	private var isChildViewEnabled = false
	private var selfOnLayout = false

	@JvmField
	var scrollingByUser = false

	@JvmField
	var scrolledByUserOnce = false

	private var gestureDetector: GestureDetectorFixDoubleTap? = null

	var pressedChildView: View? = null
		private set

	private var currentChildPosition = 0
	private var interceptedByChild = false
	private val wasPressed = false
	private var disallowInterceptTouchEvents = false
	private var instantClick = false
	private var clickRunnable: Runnable? = null
	private val ignoreOnScroll = false
	private var scrollEnabled = true
	private var pendingHighlightPosition: IntReturnCallback? = null
	private var removeHighlightSelectionRunnable: Runnable? = null
	private var hiddenByEmptyView = false
	var isFastScrollAnimationRunning = false
	private var animateEmptyView = false
	private var emptyViewAnimationType = 0
	private var selectorRadius = 0
	private var topBottomSelectorRadius = 0
	private var touchSlop = 0
	private var useRelativePositions = false
	var isMultiselect = false
	private var multiSelectionGestureStarted = false
	private var startSelectionFrom = 0
	private var currentSelectedPosition = 0
	var multiSelectionListener: OnMultiSelectionChanged? = null
	var multiselectScrollRunning = false
	var multiselectScrollToTop = false
	var lastX = Float.MAX_VALUE
	var lastY = Float.MAX_VALUE
	var listPaddings = IntArray(2)
	private var selectedPositions: HashSet<Int>? = null
	private var itemsEnterAnimator: RecyclerItemsEnterAnimator? = null
	var selectorTransformer: Consumer<Canvas>? = null
	private var accessibilityEnabled = true

	override fun getAccessibilityDelegate(): AccessibilityDelegate {
		return object : AccessibilityDelegate() {
			override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(host, info)

				if (host.isEnabled) {
					info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
				}
			}
		}
	}

	fun interface OnItemClickListener {
		fun onItemClick(view: View, position: Int)
	}

	interface OnItemClickListenerExtended {
		fun hasDoubleTap(view: View, position: Int): Boolean {
			return false
		}

		fun onDoubleTap(view: View, position: Int, x: Float, y: Float) {}

		fun onItemClick(view: View, position: Int, x: Float, y: Float)
	}

	fun interface OnItemLongClickListener {
		fun onItemClick(view: View, position: Int): Boolean
	}

	interface OnItemLongClickListenerExtended {
		fun onItemClick(view: View, position: Int, x: Float, y: Float): Boolean
		fun onMove(dx: Float, dy: Float) {}
		fun onLongClickRelease() {}
	}

	fun interface OnInterceptTouchListener {
		fun onInterceptTouchEvent(event: MotionEvent?): Boolean
	}

	abstract class SelectionAdapter : Adapter<ViewHolder>() {
		abstract fun isEnabled(holder: ViewHolder): Boolean

		fun getSelectionBottomPadding(view: View?): Int {
			return 0
		}
	}

	abstract class FastScrollAdapter : SelectionAdapter() {
		abstract fun getLetter(position: Int): String?
		abstract fun getPositionForScrollProgress(listView: RecyclerListView, progress: Float, position: IntArray)
		open fun onStartFastScroll() {}
		open fun onFinishFastScroll(listView: RecyclerListView?) {}

		open val totalItemsCount: Int
			get() = itemCount

		open fun getScrollProgress(listView: RecyclerListView): Float {
			return listView.computeVerticalScrollOffset() / (totalItemsCount.toFloat() * listView.getChildAt(0).measuredHeight - listView.measuredHeight)
		}

		open fun fastScrollIsVisible(listView: RecyclerListView?): Boolean {
			return true
		}

		open fun onFastScrollSingleTap() {}
	}

	fun interface IntReturnCallback {
		fun run(): Int
	}

	abstract class SectionsAdapter : FastScrollAdapter() {
		private val sectionPositionCache by lazy { SparseIntArray() }
		private val sectionCache by lazy { SparseIntArray() }
		private val sectionCountCache by lazy { SparseIntArray() }

		private var sectionCount = 0
		private var count = 0

		private fun cleanupCache() {
			sectionCache.clear()
			sectionPositionCache.clear()
			sectionCountCache.clear()
			count = -1
			sectionCount = -1
		}

		fun notifySectionsChanged() {
			cleanupCache()
		}

		init {
			cleanupCache()
		}

		override fun notifyDataSetChanged() {
			cleanupCache()
			super.notifyDataSetChanged()
		}

		override fun isEnabled(holder: ViewHolder): Boolean {
			val position = holder.adapterPosition
			return isEnabled(holder, getSectionForPosition(position), getPositionInSectionForPosition(position))
		}

		override fun getItemCount(): Int {
			if (count >= 0) {
				return count
			}

			count = 0

			for (i in 0 until internalGetSectionCount()) {
				count += internalGetCountForSection(i)
			}

			return count
		}

		fun getItem(position: Int): Any? {
			return getItem(getSectionForPosition(position), getPositionInSectionForPosition(position))
		}

		override fun getItemViewType(position: Int): Int {
			return getItemViewType(getSectionForPosition(position), getPositionInSectionForPosition(position))
		}

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			onBindViewHolder(getSectionForPosition(position), getPositionInSectionForPosition(position), holder)
		}

		private fun internalGetCountForSection(section: Int): Int {
			val cachedSectionCount = sectionCountCache[section, Int.MAX_VALUE]

			if (cachedSectionCount != Int.MAX_VALUE) {
				return cachedSectionCount
			}

			val sectionCount = getCountForSection(section)
			sectionCountCache.put(section, sectionCount)
			return sectionCount
		}

		private fun internalGetSectionCount(): Int {
			if (sectionCount >= 0) {
				return sectionCount
			}

			sectionCount = getSectionCount()

			return sectionCount
		}

		fun getSectionForPosition(position: Int): Int {
			val cachedSection = sectionCache[position, Int.MAX_VALUE]

			if (cachedSection != Int.MAX_VALUE) {
				return cachedSection
			}

			var sectionStart = 0

			var i = 0
			val n = internalGetSectionCount()

			while (i < n) {
				val sectionCount = internalGetCountForSection(i)
				val sectionEnd = sectionStart + sectionCount

				if (position in sectionStart until sectionEnd) {
					sectionCache.put(position, i)
					return i
				}

				sectionStart = sectionEnd

				i++
			}

			return -1
		}

		fun getPositionInSectionForPosition(position: Int): Int {
			val cachedPosition = sectionPositionCache[position, Int.MAX_VALUE]

			if (cachedPosition != Int.MAX_VALUE) {
				return cachedPosition
			}

			var sectionStart = 0
			var i = 0
			val n = internalGetSectionCount()

			while (i < n) {
				val sectionCount = internalGetCountForSection(i)
				val sectionEnd = sectionStart + sectionCount

				if (position in sectionStart until sectionEnd) {
					val positionInSection = position - sectionStart
					sectionPositionCache.put(position, positionInSection)
					return positionInSection
				}

				sectionStart = sectionEnd

				i++
			}

			return -1
		}

		abstract fun getSectionCount(): Int
		abstract fun getCountForSection(section: Int): Int
		abstract fun isEnabled(holder: ViewHolder, section: Int, row: Int): Boolean
		abstract fun getItemViewType(section: Int, position: Int): Int
		abstract fun getItem(section: Int, position: Int): Any?
		abstract fun onBindViewHolder(section: Int, position: Int, holder: ViewHolder)
		abstract fun getSectionHeaderView(section: Int, view: View?): View?
	}

	class Holder(itemView: View) : ViewHolder(itemView)

	inner class FastScroll(context: Context, private val type: Int) : View(context) {
		private val rect = RectF()
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val paint2 = Paint(Paint.ANTI_ALIAS_FLAG)
		private var progress = 0f
		private var lastY = 0f
		private var startDy = 0f
		var internalPressed = false
		private var letterLayout: StaticLayout? = null
		private var oldLetterLayout: StaticLayout? = null
		private var outLetterLayout: StaticLayout? = null
		private var inLetterLayout: StaticLayout? = null
		private var stableLetterLayout: StaticLayout? = null
		private var replaceLayoutProgress = 1f
		private var fromTop = false
		private var lastLetterY = 0f
		private var fromWidth = 0f
		private val letterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		private var currentLetter: String? = null
		private val path = Path()
		private val arrowPath = Path()
		private val radii = FloatArray(8)
		private var textX = 0f
		private var textY = 0f
		private var bubbleProgress = 0f
		private var lastUpdateTime: Long = 0
		private val internalScrollX: Int
		private var inactiveColor = 0
		private var activeColor = 0
		private var floatingDateVisible = false
		private var floatingDateProgress = 0f
		private val positionWithOffset = IntArray(2)
		var isVisible = false
		var touchSlop: Float
		private var fastScrollShadowDrawable: Drawable?
		private var fastScrollBackgroundDrawable: Drawable? = null
		var isRtl = false

		private var hideFloatingDateRunnable = object : Runnable {
			override fun run() {
				if (internalPressed) {
					AndroidUtilities.cancelRunOnUIThread(this)
					AndroidUtilities.runOnUIThread(this, 4000)
				}
				else {
					floatingDateVisible = false
					invalidate()
				}
			}
		}

		fun updateColors() {
			inactiveColor = if (type == Companion.LETTER_TYPE) Theme.getColor(Theme.key_fastScrollInactive) else ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.4f).toInt())
			activeColor = Theme.getColor(Theme.key_fastScrollActive)
			paint.color = inactiveColor

			if (type == Companion.LETTER_TYPE) {
				letterPaint.color = Theme.getColor(Theme.key_fastScrollText)
			}
			else {
				letterPaint.color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
			}

			invalidate()
		}

		var startY = 0f
		var isMoving = false
		var startTime: Long = 0
		private var visibilityAlpha = 0f
		private var viewAlpha = 0f

		init {
			if (type == Companion.LETTER_TYPE) {
				letterPaint.textSize = AndroidUtilities.dp(45f).toFloat()
				isRtl = LocaleController.isRTL
			}
			else {
				isRtl = false
				letterPaint.textSize = AndroidUtilities.dp(13f).toFloat()
				letterPaint.typeface = Theme.TYPEFACE_BOLD
				paint2.color = context.getColor(R.color.background)
				fastScrollBackgroundDrawable = ContextCompat.getDrawable(context, R.drawable.calendar_date)!!.mutate()
				fastScrollBackgroundDrawable!!.colorFilter = PorterDuffColorFilter(ColorUtils.blendARGB(context.getColor(R.color.background), Color.WHITE, 0.1f), PorterDuff.Mode.MULTIPLY)
			}

			for (a in 0..7) {
				radii[a] = AndroidUtilities.dp(44f).toFloat()
			}

			internalScrollX = if (isRtl) AndroidUtilities.dp(10f) else AndroidUtilities.dp(((if (type == Companion.LETTER_TYPE) 132 else 240) - 15).toFloat())

			updateColors()

			isFocusableInTouchMode = true

			val vc = ViewConfiguration.get(context)
			touchSlop = vc.scaledTouchSlop.toFloat()
			fastScrollShadowDrawable = ContextCompat.getDrawable(context, R.drawable.fast_scroll_shadow)
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (!isVisible) {
				internalPressed = false
				return false
			}

			when (event.action) {
				MotionEvent.ACTION_DOWN -> {
					val x = event.x

					run {
						lastY = event.y
						startY = lastY
					}

					val currentY = ceil(((measuredHeight - AndroidUtilities.dp((24 + 30).toFloat())) * progress).toDouble()).toFloat() + AndroidUtilities.dp(12f)

					if (isRtl && x > AndroidUtilities.dp(25f) || !isRtl && x < AndroidUtilities.dp(107f) || lastY < currentY || lastY > currentY + AndroidUtilities.dp(30f)) {
						return false
					}

					if (type == Companion.DATE_TYPE && !floatingDateVisible) {
						if (isRtl && x > AndroidUtilities.dp(25f) || !isRtl && x < measuredWidth - AndroidUtilities.dp(25f) || lastY < currentY || lastY > currentY + AndroidUtilities.dp(30f)) {
							return false
						}
					}

					startDy = lastY - currentY
					startTime = System.currentTimeMillis()
					internalPressed = true
					isMoving = false
					lastUpdateTime = System.currentTimeMillis()

					invalidate()

					val adapter = adapter

					showFloatingDate()

					if (adapter is FastScrollAdapter) {
						adapter.onStartFastScroll()
					}

					return true
				}

				MotionEvent.ACTION_MOVE -> {
					if (!internalPressed) {
						return true
					}

					if (abs(event.y - startY) > touchSlop) {
						isMoving = true
					}

					if (isMoving) {
						var newY = event.y
						val minY = AndroidUtilities.dp(12f) + startDy
						val maxY = measuredHeight - AndroidUtilities.dp((12 + 30).toFloat()) + startDy

						if (newY < minY) {
							newY = minY
						}
						else if (newY > maxY) {
							newY = maxY
						}

						val dy = newY - lastY

						lastY = newY
						progress += dy / (measuredHeight - AndroidUtilities.dp((24 + 30).toFloat()))

						if (progress < 0) {
							progress = 0f
						}
						else if (progress > 1) {
							progress = 1f
						}

						getCurrentLetter(true)

						invalidate()
					}

					return true
				}

				MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
					adapter = adapter

					if (internalPressed && !isMoving && System.currentTimeMillis() - startTime < 150) {
						if (adapter is FastScrollAdapter) {
							(adapter as FastScrollAdapter).onFastScrollSingleTap()
						}
					}

					isMoving = false
					internalPressed = false
					lastUpdateTime = System.currentTimeMillis()

					invalidate()

					if (adapter is FastScrollAdapter) {
						(adapter as FastScrollAdapter).onFinishFastScroll(this@RecyclerListView)
					}

					showFloatingDate()

					return true
				}
			}

			return internalPressed
		}

		fun getCurrentLetter(updatePosition: Boolean) {
			val layoutManager = layoutManager

			if (layoutManager is LinearLayoutManager) {
				if (layoutManager.orientation == LinearLayoutManager.VERTICAL) {
					val adapter = adapter

					if (adapter is FastScrollAdapter) {
						adapter.getPositionForScrollProgress(this@RecyclerListView, progress, positionWithOffset)

						if (updatePosition) {
							layoutManager.scrollToPositionWithOffset(positionWithOffset[0], -positionWithOffset[1] + sectionOffset)
						}

						val newLetter = adapter.getLetter(positionWithOffset[0])

						if (newLetter == null) {
							if (letterLayout != null) {
								oldLetterLayout = letterLayout
							}
							letterLayout = null
						}
						else if (newLetter != currentLetter) {
							currentLetter = newLetter

							if (type == Companion.LETTER_TYPE) {
								letterLayout = StaticLayout(newLetter, letterPaint, 1000, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
							}
							else {
								outLetterLayout = letterLayout

								val w = letterPaint.measureText(newLetter).toInt() + 1

								letterLayout = StaticLayout(newLetter, letterPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

								if (outLetterLayout != null) {
									val newSplits = newLetter.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
									val oldSplits = outLetterLayout?.text?.toString()?.split(" ".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()

									if (oldSplits != null && newSplits.size == 2 && oldSplits.size == 2 && newSplits[1] == oldSplits[1]) {
										val oldText = outLetterLayout!!.text.toString()

										var spannableStringBuilder = SpannableStringBuilder(oldText)
										spannableStringBuilder.setSpan(EmptyStubSpan(), oldSplits[0].length, oldText.length, 0)

										val oldW = letterPaint.measureText(oldText).toInt() + 1

										outLetterLayout = StaticLayout(spannableStringBuilder, letterPaint, oldW, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

										spannableStringBuilder = SpannableStringBuilder(newLetter)
										spannableStringBuilder.setSpan(EmptyStubSpan(), newSplits[0].length, newLetter.length, 0)

										inLetterLayout = StaticLayout(spannableStringBuilder, letterPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

										spannableStringBuilder = SpannableStringBuilder(newLetter)
										spannableStringBuilder.setSpan(EmptyStubSpan(), 0, newSplits[0].length, 0)

										stableLetterLayout = StaticLayout(spannableStringBuilder, letterPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
									}
									else {
										inLetterLayout = letterLayout
										stableLetterLayout = null
									}

									fromWidth = outLetterLayout!!.width.toFloat()
									replaceLayoutProgress = 0f
									fromTop = getProgress() > lastLetterY
								}

								lastLetterY = getProgress()
							}

							oldLetterLayout = null

							if (letterLayout!!.lineCount > 0) {
								textX = if (isRtl) {
									AndroidUtilities.dp(10f) + (AndroidUtilities.dp(88f) - letterLayout!!.getLineWidth(0)) / 2 - letterLayout!!.getLineLeft(0)
								}
								else {
									(AndroidUtilities.dp(88f) - letterLayout!!.getLineWidth(0)) / 2 - letterLayout!!.getLineLeft(0)
								}

								textY = ((AndroidUtilities.dp(88f) - letterLayout!!.height) / 2).toFloat()
							}
						}
					}
				}
			}
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			setMeasuredDimension(AndroidUtilities.dp(if (type == Companion.LETTER_TYPE) 132f else 240f), MeasureSpec.getSize(heightMeasureSpec))
			arrowPath.reset()
			arrowPath.setLastPoint(0f, 0f)
			arrowPath.lineTo(AndroidUtilities.dp(4f).toFloat(), -AndroidUtilities.dp(4f).toFloat())
			arrowPath.lineTo(-AndroidUtilities.dp(4f).toFloat(), -AndroidUtilities.dp(4f).toFloat())
			arrowPath.close()
		}

		override fun onDraw(canvas: Canvas) {
			var y = paddingTop + ceil(((measuredHeight - paddingTop - AndroidUtilities.dp((24 + 30).toFloat())) * progress).toDouble()).toInt()

			rect[internalScrollX.toFloat(), (AndroidUtilities.dp(12f) + y).toFloat(), (internalScrollX + AndroidUtilities.dp(5f)).toFloat()] = (AndroidUtilities.dp((12 + 30).toFloat()) + y).toFloat()

			if (type == Companion.LETTER_TYPE) {
				paint.color = ColorUtils.blendARGB(inactiveColor, activeColor, bubbleProgress)

				canvas.drawRoundRect(rect, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), paint)
			}
			else {
				paint.color = ColorUtils.blendARGB(context.getColor(R.color.background), Color.WHITE, 0.1f)

				val cy = (y + AndroidUtilities.dp((12 + 15).toFloat())).toFloat()

				fastScrollShadowDrawable?.setBounds(measuredWidth - fastScrollShadowDrawable!!.intrinsicWidth, (cy - fastScrollShadowDrawable!!.intrinsicHeight / 2).toInt(), measuredWidth, (cy + fastScrollShadowDrawable!!.intrinsicHeight / 2).toInt())
				fastScrollShadowDrawable?.draw(canvas)

				canvas.drawCircle((internalScrollX + AndroidUtilities.dp(8f)).toFloat(), (y + AndroidUtilities.dp((12 + 15).toFloat())).toFloat(), AndroidUtilities.dp(24f).toFloat(), paint)

				paint.color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)

				canvas.save()
				canvas.translate((internalScrollX + AndroidUtilities.dp(4f)).toFloat(), y + AndroidUtilities.dp((12 + 15 + 2 + 5).toFloat()) + AndroidUtilities.dp(2f) * bubbleProgress)
				canvas.drawPath(arrowPath, paint)
				canvas.restore()
				canvas.save()
				canvas.translate((internalScrollX + AndroidUtilities.dp(4f)).toFloat(), y + AndroidUtilities.dp((12 + 15 + 2 - 5).toFloat()) - AndroidUtilities.dp(2f) * bubbleProgress)
				canvas.rotate(180f, 0f, -AndroidUtilities.dp(2f).toFloat())
				canvas.drawPath(arrowPath, paint)
				canvas.restore()
			}

			if (type == Companion.LETTER_TYPE) {
				if (isMoving || bubbleProgress != 0f) {
					paint.alpha = (255 * bubbleProgress).toInt()

					val progressY = y + AndroidUtilities.dp(30f)

					y -= AndroidUtilities.dp(46f)

					var diff = 0f

					if (y <= AndroidUtilities.dp(12f)) {
						diff = (AndroidUtilities.dp(12f) - y).toFloat()
						y = AndroidUtilities.dp(12f)
					}

					val radiusTop: Float
					val radiusBottom: Float

					canvas.translate(AndroidUtilities.dp(10f).toFloat(), y.toFloat())

					if (diff <= AndroidUtilities.dp(29f)) {
						radiusTop = AndroidUtilities.dp(44f).toFloat()
						radiusBottom = AndroidUtilities.dp(4f) + diff / AndroidUtilities.dp(29f) * AndroidUtilities.dp(40f)
					}
					else {
						diff -= AndroidUtilities.dp(29f).toFloat()
						radiusBottom = AndroidUtilities.dp(44f).toFloat()
						radiusTop = AndroidUtilities.dp(4f) + (1.0f - diff / AndroidUtilities.dp(29f)) * AndroidUtilities.dp(40f)
					}
					if (isRtl && (radii[0] != radiusTop || radii[6] != radiusBottom) || !isRtl && (radii[2] != radiusTop || radii[4] != radiusBottom)) {
						if (isRtl) {
							radii[1] = radiusTop
							radii[0] = radii[1]
							radii[7] = radiusBottom
							radii[6] = radii[7]
						}
						else {
							radii[3] = radiusTop
							radii[2] = radii[3]
							radii[5] = radiusBottom
							radii[4] = radii[5]
						}

						path.reset()

						rect.set(if (isRtl) AndroidUtilities.dp(10f).toFloat() else 0f, 0f, AndroidUtilities.dp(if (isRtl) 98f else 88f).toFloat(), AndroidUtilities.dp(88f).toFloat())

						path.addRoundRect(rect, radii, Path.Direction.CW)
						path.close()
					}

					val layoutToDraw = letterLayout ?: oldLetterLayout

					if (layoutToDraw != null) {
						canvas.save()
						canvas.scale(bubbleProgress, bubbleProgress, internalScrollX.toFloat(), (progressY - y).toFloat())
						canvas.drawPath(path, paint)
						canvas.translate(textX, textY)
						layoutToDraw.draw(canvas)
						canvas.restore()
					}
				}
			}
			else if (type == Companion.DATE_TYPE) {
				if (letterLayout != null && floatingDateProgress != 0f) {
					canvas.save()

					val s = 0.7f + 0.3f * floatingDateProgress

					canvas.scale(s, s, rect.right - AndroidUtilities.dp(12f), rect.centerY())

					val cy = rect.centerY()
					val x = rect.left - AndroidUtilities.dp(30f) * bubbleProgress - AndroidUtilities.dp(8f)
					// val r = letterLayout!!.height / 2f + AndroidUtilities.dp(6f)

					val width = replaceLayoutProgress * letterLayout!!.width + fromWidth * (1f - replaceLayoutProgress)

					rect[x - width - AndroidUtilities.dp(36f), cy - letterLayout!!.height / 2f - AndroidUtilities.dp(8f), x - AndroidUtilities.dp(12f)] = cy + letterLayout!!.height / 2f + AndroidUtilities.dp(8f)

					val oldAlpha1 = paint2.alpha
					val oldAlpha2 = letterPaint.alpha

					paint2.alpha = (oldAlpha1 * floatingDateProgress).toInt()

					fastScrollBackgroundDrawable?.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
					fastScrollBackgroundDrawable?.alpha = (255 * floatingDateProgress).toInt()
					fastScrollBackgroundDrawable?.draw(canvas)

					if (replaceLayoutProgress != 1f) {
						replaceLayoutProgress += 16f / 150f

						if (replaceLayoutProgress > 1f) {
							replaceLayoutProgress = 1f
						}
						else {
							invalidate()
						}
					}

					if (replaceLayoutProgress != 1f) {
						canvas.save()

						rect.inset(AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(2f).toFloat())

						canvas.clipRect(rect)

						if (outLetterLayout != null) {
							letterPaint.alpha = (oldAlpha2 * floatingDateProgress * (1f - replaceLayoutProgress)).toInt()
							canvas.save()
							canvas.translate(x - outLetterLayout!!.width - AndroidUtilities.dp(24f), cy - outLetterLayout!!.height / 2f + (if (fromTop) -1 else 1) * AndroidUtilities.dp(15f) * replaceLayoutProgress)
							outLetterLayout?.draw(canvas)
							canvas.restore()
						}

						if (inLetterLayout != null) {
							letterPaint.alpha = (oldAlpha2 * floatingDateProgress * replaceLayoutProgress).toInt()
							canvas.save()
							canvas.translate(x - inLetterLayout!!.width - AndroidUtilities.dp(24f), cy - inLetterLayout!!.height / 2f + (if (fromTop) 1 else -1) * AndroidUtilities.dp(15f) * (1f - replaceLayoutProgress))
							inLetterLayout?.draw(canvas)
							canvas.restore()
						}

						if (stableLetterLayout != null) {
							letterPaint.alpha = (oldAlpha2 * floatingDateProgress).toInt()
							canvas.save()
							canvas.translate(x - stableLetterLayout!!.width - AndroidUtilities.dp(24f), cy - stableLetterLayout!!.height / 2f)
							stableLetterLayout?.draw(canvas)
							canvas.restore()
						}

						canvas.restore()
					}
					else {
						letterPaint.alpha = (oldAlpha2 * floatingDateProgress).toInt()
						canvas.save()
						canvas.translate(x - letterLayout!!.width - AndroidUtilities.dp(24f), cy - letterLayout!!.height / 2f + AndroidUtilities.dp(15f) * (1f - replaceLayoutProgress))
						letterLayout?.draw(canvas)
						canvas.restore()
					}

					paint2.alpha = oldAlpha1
					letterPaint.alpha = oldAlpha2
					canvas.restore()
				}
			}

			val newTime = System.currentTimeMillis()
			var dt = newTime - lastUpdateTime

			if (dt < 0 || dt > 17) {
				dt = 17
			}

			if (isMoving && letterLayout != null && bubbleProgress < 1.0f || (!isMoving || letterLayout == null) && bubbleProgress > 0.0f) {
				lastUpdateTime = newTime

				invalidate()

				if (isMoving && letterLayout != null) {
					bubbleProgress += dt / 120.0f

					if (bubbleProgress > 1.0f) {
						bubbleProgress = 1.0f
					}
				}
				else {
					bubbleProgress -= dt / 120.0f

					if (bubbleProgress < 0.0f) {
						bubbleProgress = 0.0f
					}
				}
			}

			if (floatingDateVisible && floatingDateProgress != 1f) {
				floatingDateProgress += dt / 120.0f

				if (floatingDateProgress > 1.0f) {
					floatingDateProgress = 1.0f
				}

				invalidate()
			}
			else if (!floatingDateVisible && floatingDateProgress != 0f) {
				floatingDateProgress -= dt / 120.0f

				if (floatingDateProgress < 0.0f) {
					floatingDateProgress = 0.0f
				}

				invalidate()
			}
		}

		override fun layout(l: Int, t: Int, r: Int, b: Int) {
			if (!selfOnLayout) {
				return
			}

			super.layout(l, t, r, b)
		}

		fun setProgress(value: Float) {
			progress = value
			invalidate()
		}

		override fun isPressed(): Boolean {
			return internalPressed
		}

		fun showFloatingDate() {
			if (type != DATE_TYPE) {
				return
			}

			if (!floatingDateVisible) {
				floatingDateVisible = true
				invalidate()
			}

			AndroidUtilities.cancelRunOnUIThread(hideFloatingDateRunnable)
			AndroidUtilities.runOnUIThread(hideFloatingDateRunnable, 2000)
		}

		fun setIsVisible(visible: Boolean) {
			if (isVisible != visible) {
				isVisible = visible
				visibilityAlpha = if (visible) 1f else 0f
				super.setAlpha(viewAlpha * visibilityAlpha)
			}
		}

		fun setVisibilityAlpha(v: Float) {
			if (visibilityAlpha != v) {
				visibilityAlpha = v
				super.setAlpha(viewAlpha * visibilityAlpha)
			}
		}

		override fun setAlpha(alpha: Float) {
			if (viewAlpha != alpha) {
				viewAlpha = alpha
				super.setAlpha(viewAlpha * visibilityAlpha)
			}
		}

		override fun getAlpha(): Float {
			return viewAlpha
		}

		val scrollBarY: Int
			get() = ceil(((measuredHeight - AndroidUtilities.dp((24 + 30).toFloat())) * progress).toDouble()).toInt() + AndroidUtilities.dp(17f)

		fun getProgress(): Float {
			return progress
		}
	}

	private inner class RecyclerListViewItemClickListener(context: Context?) : OnItemTouchListener {
		init {
			gestureDetector = GestureDetectorFixDoubleTap(context, object : GestureDetectorFixDoubleTap.OnGestureListener() {
				private var doubleTapView: View? = null

				override fun onSingleTapUp(e: MotionEvent): Boolean {
					if (pressedChildView != null) {
						if (onItemClickListenerExtended != null && onItemClickListenerExtended!!.hasDoubleTap(pressedChildView!!, currentChildPosition)) {
							doubleTapView = pressedChildView
						}
						else {
							onPressItem(pressedChildView, e)
							return false
						}
					}

					return false
				}

				override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
					if (doubleTapView != null && onItemClickListenerExtended != null) {
						if (onItemClickListenerExtended!!.hasDoubleTap(doubleTapView!!, currentChildPosition)) {
							onPressItem(doubleTapView, e)
							doubleTapView = null
							return true
						}
					}

					return false
				}

				override fun onDoubleTap(e: MotionEvent): Boolean {
					if (doubleTapView != null && onItemClickListenerExtended != null && onItemClickListenerExtended!!.hasDoubleTap(doubleTapView!!, currentChildPosition)) {
						onItemClickListenerExtended?.onDoubleTap(doubleTapView!!, currentChildPosition, e.x, e.y)
						doubleTapView = null
						return true
					}

					return false
				}

				private fun onPressItem(cv: View?, e: MotionEvent) {
					if (cv != null && (onItemClickListener != null || onItemClickListenerExtended != null)) {
						val x = e.x
						val y = e.y

						onChildPressed(cv, x, y, true)

						val position = currentChildPosition

						if (instantClick && position != -1) {
							cv.playSoundEffect(SoundEffectConstants.CLICK)
							cv.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)

							onItemClickListener?.onItemClick(cv, position) ?: onItemClickListenerExtended?.onItemClick(cv, position, x - cv.x, y - cv.y)
						}

						AndroidUtilities.runOnUIThread(object : Runnable {
							override fun run() {
								if (this == clickRunnable) {
									clickRunnable = null
								}

								onChildPressed(cv, 0f, 0f, false)

								if (!instantClick) {
									cv.playSoundEffect(SoundEffectConstants.CLICK)
									cv.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)

									if (position != -1) {
										onItemClickListener?.onItemClick(cv, position) ?: onItemClickListenerExtended?.onItemClick(cv, position, x - cv.x, y - cv.y)
									}
								}
							}
						}.also {
							clickRunnable = it
						}, ViewConfiguration.getPressedStateDuration().toLong())

						if (selectChildRunnable != null) {
							AndroidUtilities.cancelRunOnUIThread(selectChildRunnable)
							selectChildRunnable = null
							pressedChildView = null
							interceptedByChild = false
							removeSelection(cv, e)
						}
					}
				}

				override fun onLongPress(event: MotionEvent) {
					if (pressedChildView == null || currentChildPosition == -1 || onItemLongClickListener == null && onItemLongClickListenerExtended == null) {
						return
					}

					val child = pressedChildView

					if (onItemLongClickListener != null) {
						if (onItemLongClickListener!!.onItemClick(pressedChildView!!, currentChildPosition)) {
							child?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
							child?.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
						}
					}
					else {
						if (onItemLongClickListenerExtended!!.onItemClick(pressedChildView!!, currentChildPosition, event.x - pressedChildView!!.x, event.y - pressedChildView!!.y)) {
							child?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
							child?.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
							longPressCalled = true
						}
					}
				}

				override fun onDown(e: MotionEvent): Boolean {
					return false
				}

				override fun hasDoubleTap(): Boolean {
					return onItemLongClickListenerExtended != null
				}
			})

			gestureDetector?.setIsLongpressEnabled(false)
		}

		override fun onInterceptTouchEvent(view: RecyclerView, event: MotionEvent): Boolean {
			val action = event.actionMasked
			val isScrollIdle = this@RecyclerListView.scrollState == SCROLL_STATE_IDLE

			if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && pressedChildView == null && isScrollIdle) {
				val ex = event.x
				val ey = event.y

				longPressCalled = false

				val animator = itemAnimator

				if ((allowItemsInteractionDuringAnimation || animator == null || !animator.isRunning) && allowSelectChildAtPosition(ex, ey)) {
					val v = findChildViewUnder(ex, ey)
					if (v != null && allowSelectChildAtPosition(v)) {
						pressedChildView = v
					}
				}

				if (pressedChildView is ViewGroup) {
					val x = event.x - pressedChildView!!.left
					val y = event.y - pressedChildView!!.top
					val viewGroup = pressedChildView as ViewGroup
					val count = viewGroup.childCount

					for (i in count - 1 downTo 0) {
						val child = viewGroup.getChildAt(i)

						if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
							if (child.isClickable) {
								pressedChildView = null
								break
							}
						}
					}
				}

				currentChildPosition = -1

				if (pressedChildView != null) {
					currentChildPosition = view.getChildPosition(pressedChildView!!)

					val childEvent = MotionEvent.obtain(0, 0, event.actionMasked, event.x - pressedChildView!!.left, event.y - pressedChildView!!.top, 0)

					if (pressedChildView?.onTouchEvent(childEvent) == true) {
						interceptedByChild = true
					}

					childEvent.recycle()
				}
			}

			if (pressedChildView != null && !interceptedByChild) {
				try {
					gestureDetector?.onTouchEvent(event)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
				if (!interceptedByChild && pressedChildView != null) {
					val x = event.x
					val y = event.y

					selectChildRunnable = Runnable {
						if (selectChildRunnable != null && pressedChildView != null) {
							onChildPressed(pressedChildView, x, y, true)
							selectChildRunnable = null
						}
					}

					AndroidUtilities.runOnUIThread(selectChildRunnable, ViewConfiguration.getTapTimeout().toLong())

					if (pressedChildView!!.isEnabled && canHighlightChildAt(pressedChildView, x - pressedChildView!!.x, y - pressedChildView!!.y)) {
						positionSelector(currentChildPosition, pressedChildView!!)

						if (selectorDrawable != null) {
							val d = selectorDrawable!!.current

							if (d is TransitionDrawable) {
								if (onItemLongClickListener != null || onItemClickListenerExtended != null) {
									d.startTransition(ViewConfiguration.getLongPressTimeout())
								}
								else {
									d.resetTransition()
								}
							}

							selectorDrawable?.setHotspot(event.x, event.y)
						}

						updateSelectorState()
					}
					else {
						selectorRect.setEmpty()
					}
				}
				else {
					selectorRect.setEmpty()
				}
			}
			else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL || !isScrollIdle) {
				if (pressedChildView != null) {
					if (selectChildRunnable != null) {
						AndroidUtilities.cancelRunOnUIThread(selectChildRunnable)
						selectChildRunnable = null
					}

					val pressedChild = pressedChildView

					onChildPressed(pressedChildView, 0f, 0f, false)

					pressedChildView = null
					interceptedByChild = false

					removeSelection(pressedChild, event)

					if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL && onItemLongClickListenerExtended != null && longPressCalled) {
						onItemLongClickListenerExtended?.onLongClickRelease()
						longPressCalled = false
					}
				}
			}

			return false
		}

		override fun onTouchEvent(view: RecyclerView, event: MotionEvent) {
			// unused
		}

		override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
			cancelClickRunnables(true)
		}
	}

	override fun findChildViewUnder(x: Float, y: Float): View? {
		val count = childCount

		for (a in 0..1) {
			for (i in count - 1 downTo 0) {
				val child = getChildAt(i)
				val translationX: Float = if (a == 0) child.translationX else 0f
				val translationY: Float = if (a == 0) child.translationY else 0f

				if (x >= child.left + translationX && x <= child.right + translationX && y >= child.top + translationY && y <= child.bottom + translationY) {
					return child
				}
			}
		}

		return null
	}

	protected open fun canHighlightChildAt(child: View?, x: Float, y: Float): Boolean {
		return true
	}

	fun setDisableHighlightState(value: Boolean) {
		disableHighlightState = value
	}

	protected open fun onChildPressed(child: View?, x: Float, y: Float, pressed: Boolean) {
		if (disableHighlightState || child == null) {
			return
		}

		child.isPressed = pressed
	}

	protected open fun allowSelectChildAtPosition(x: Float, y: Float): Boolean {
		return true
	}

	protected open fun allowSelectChildAtPosition(child: View?): Boolean {
		return true
	}

	private fun removeSelection(pressedChild: View?, event: MotionEvent?) {
		if (pressedChild == null || selectorRect.isEmpty) {
			return
		}

		if (pressedChild.isEnabled) {
			positionSelector(currentChildPosition, pressedChild)

			if (selectorDrawable != null) {
				val d = selectorDrawable!!.current

				if (d is TransitionDrawable) {
					d.resetTransition()
				}

				if (event != null) {
					selectorDrawable?.setHotspot(event.x, event.y)
				}
			}
		}
		else {
			selectorRect.setEmpty()
		}
		updateSelectorState()

	}

	fun cancelClickRunnables(uncheck: Boolean) {
		if (selectChildRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(selectChildRunnable)
			selectChildRunnable = null
		}

		if (pressedChildView != null) {
			val child = pressedChildView!!

			if (uncheck) {
				onChildPressed(pressedChildView, 0f, 0f, false)
			}

			pressedChildView = null

			removeSelection(child, null)
		}

		selectorRect.setEmpty()

		if (clickRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(clickRunnable)
			clickRunnable = null
		}

		interceptedByChild = false
	}

	private var resetSelectorOnChanged = true

	fun setResetSelectorOnChanged(value: Boolean) {
		resetSelectorOnChanged = value
	}

	private val observer: AdapterDataObserver = object : AdapterDataObserver() {
		override fun onChanged() {
			checkIfEmpty(true)

			if (resetSelectorOnChanged) {
				currentFirst = -1

				if (removeHighlightSelectionRunnable == null) {
					selectorRect.setEmpty()
				}
			}

			invalidate()
		}

		override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
			checkIfEmpty(true)

			if (pinnedHeader != null && pinnedHeader!!.alpha == 0f) {
				currentFirst = -1
				invalidateViews()
			}
		}

		override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
			checkIfEmpty(true)
		}
	}

	private fun getResourceDeclareStyleableIntArray(packageName: String, name: String): IntArray? {
		try {
			val f = Class.forName("$packageName.R\$styleable").getField(name)
			return f[null] as? IntArray
		}
		catch (t: Throwable) {
			//ignore
		}

		return null
	}

	override fun setVerticalScrollBarEnabled(verticalScrollBarEnabled: Boolean) {
		if (attributes != null) {
			super.setVerticalScrollBarEnabled(verticalScrollBarEnabled)
		}
	}

	override fun onMeasure(widthSpec: Int, heightSpec: Int) {
		super.onMeasure(widthSpec, heightSpec)

		if (fastScroll != null) {
			val height = measuredHeight - paddingTop - paddingBottom
			fastScroll?.layoutParams?.height = height
			fastScroll?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(132f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
		}

		touchSlop = ViewConfiguration.get(context).scaledTouchSlop
	}

	override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
		@Suppress("NAME_SHADOWING") var t = t

		super.onLayout(changed, l, t, r, b)

		fastScroll?.let { fastScroll ->
			selfOnLayout = true

			t += paddingTop

			if (fastScroll.isRtl) {
				fastScroll.layout(0, t, fastScroll.measuredWidth, t + fastScroll.measuredHeight)
			}
			else {
				val x = measuredWidth - fastScroll.measuredWidth
				fastScroll.layout(x, t, x + fastScroll.measuredWidth, t + fastScroll.measuredHeight)
			}

			selfOnLayout = false
		}

		checkSection(false)

		if (pendingHighlightPosition != null) {
			highlightRowInternal(pendingHighlightPosition!!, false)
		}
	}

	fun setSelectorType(type: Int) {
		selectorType = type
	}

	fun setSelectorRadius(radius: Int) {
		selectorRadius = radius
	}

	fun setTopBottomSelectorRadius(radius: Int) {
		topBottomSelectorRadius = radius
	}

	fun setDrawSelectorBehind(value: Boolean) {
		drawSelectorBehind = value
	}

	fun setSelectorDrawableColor(color: Int) {
		selectorDrawable?.callback = null

		selectorDrawable = if (selectorType == 8) {
			Theme.createRadSelectorDrawable(color, selectorRadius, 0)
		}
		else if (topBottomSelectorRadius > 0) {
			Theme.createRadSelectorDrawable(color, topBottomSelectorRadius, topBottomSelectorRadius)
		}
		else if (selectorRadius > 0) {
			Theme.createSimpleSelectorRoundRectDrawable(selectorRadius, 0, color, -0x1000000)
		}
		else if (selectorType == 2) {
			Theme.getSelectorDrawable(color, false)
		}
		else {
			Theme.createSelectorDrawable(color, selectorType)
		}

		selectorDrawable?.callback = this
	}

	fun checkSection(force: Boolean) {
		if ((scrollingByUser || force) && fastScroll != null || sectionsType != SECTIONS_TYPE_SIMPLE && sectionsAdapter != null) {
			val layoutManager = layoutManager

			if (layoutManager is LinearLayoutManager) {
				if (layoutManager.orientation == LinearLayoutManager.VERTICAL) {
					if (sectionsAdapter != null) {
						val paddingTop = paddingTop

						if (sectionsType == SECTIONS_TYPE_STICKY_HEADERS || sectionsType == SECTIONS_TYPE_FAST_SCROLL_ONLY) {
							val childCount = childCount
							var maxBottom = 0
							var minBottom = Int.MAX_VALUE
							var minChild: View? = null
							var minBottomSection = Int.MAX_VALUE

							for (a in 0 until childCount) {
								val child = getChildAt(a)
								val bottom = child.bottom

								if (bottom <= sectionOffset + paddingTop) {
									continue
								}

								if (bottom < minBottom) {
									minBottom = bottom
									minChild = child
								}

								maxBottom = max(maxBottom, bottom)

								if (bottom < sectionOffset + paddingTop + AndroidUtilities.dp(32f)) {
									continue
								}

								if (bottom < minBottomSection) {
									minBottomSection = bottom
								}
							}

							if (minChild == null) {
								return
							}

							val holder = getChildViewHolder(minChild) ?: return
							val firstVisibleItem = holder.adapterPosition
							val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
							val visibleItemCount = abs(lastVisibleItem - firstVisibleItem) + 1

							if ((scrollingByUser || force) && fastScroll != null && !fastScroll!!.isPressed) {
								val adapter = adapter

								if (adapter is FastScrollAdapter) {
									fastScroll?.setProgress(min(1.0f, firstVisibleItem / (sectionsAdapter!!.totalItemsCount - visibleItemCount + 1).toFloat()))
								}
							}

							headersCache?.addAll(headers!!)
							headers?.clear()

							if (sectionsAdapter!!.itemCount == 0) {
								return
							}

							if (currentFirst != firstVisibleItem || currentVisible != visibleItemCount) {
								currentFirst = firstVisibleItem
								currentVisible = visibleItemCount
								sectionsCount = 1
								startSection = sectionsAdapter!!.getSectionForPosition(firstVisibleItem)

								var itemNum = firstVisibleItem + sectionsAdapter!!.getCountForSection(startSection) - sectionsAdapter!!.getPositionInSectionForPosition(firstVisibleItem)

								while (itemNum < firstVisibleItem + visibleItemCount) {
									itemNum += sectionsAdapter!!.getCountForSection(startSection + sectionsCount)
									sectionsCount++
								}
							}

							if (sectionsType != SECTIONS_TYPE_FAST_SCROLL_ONLY) {
								var itemNum = firstVisibleItem

								for (a in startSection until startSection + sectionsCount) {
									var header: View? = null

									if (headersCache!!.isNotEmpty()) {
										header = headersCache!![0]
										headersCache!!.removeAt(0)
									}

									header = getSectionHeaderView(a, header)

									headers?.add(header)

									val count = sectionsAdapter!!.getCountForSection(a)

									if (a == startSection) {
										when (sectionsAdapter?.getPositionInSectionForPosition(itemNum)) {
											count - 1 -> {
												header?.tag = -(header?.height ?: 0) + paddingTop
											}

											count - 2 -> {
												val child = getChildAt(itemNum - firstVisibleItem)

												val headerTop = if (child != null) {
													child.top + paddingTop
												}
												else {
													-AndroidUtilities.dp(100f)
												}

												header?.tag = min(headerTop, 0)
											}

											else -> {
												header?.tag = 0
											}
										}

										itemNum += count - sectionsAdapter!!.getPositionInSectionForPosition(firstVisibleItem)
									}
									else {
										val child = getChildAt(itemNum - firstVisibleItem)

										if (child != null) {
											header?.tag = child.top + paddingTop
										}
										else {
											header?.tag = -AndroidUtilities.dp(100f)
										}

										itemNum += count
									}
								}
							}
						}
						else if (sectionsType == SECTIONS_TYPE_DATE) {
							pinnedHeaderShadowTargetAlpha = 0.0f

							if (sectionsAdapter!!.itemCount == 0) {
								return
							}

							val childCount = childCount
							var maxBottom = 0
							var minBottom = Int.MAX_VALUE
							var minChild: View? = null
							var minBottomSection = Int.MAX_VALUE
							var minChildSection: View? = null

							for (a in 0 until childCount) {
								val child = getChildAt(a)
								val bottom = child.bottom

								if (bottom <= sectionOffset + paddingTop) {
									continue
								}

								if (bottom < minBottom) {
									minBottom = bottom
									minChild = child
								}

								maxBottom = max(maxBottom, bottom)

								if (bottom < sectionOffset + paddingTop + AndroidUtilities.dp(32f)) {
									continue
								}

								if (bottom < minBottomSection) {
									minBottomSection = bottom
									minChildSection = child
								}
							}

							if (minChild == null) {
								return
							}

							val holder = getChildViewHolder(minChild) ?: return
							val firstVisibleItem = holder.adapterPosition
							val startSection = sectionsAdapter!!.getSectionForPosition(firstVisibleItem)

							if (startSection < 0) {
								return
							}

							if (currentFirst != startSection || pinnedHeader == null) {
								pinnedHeader = getSectionHeaderView(startSection, pinnedHeader)
								pinnedHeader?.measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.UNSPECIFIED))
								pinnedHeader?.layout(0, 0, pinnedHeader!!.measuredWidth, pinnedHeader!!.measuredHeight)
								currentFirst = startSection
							}

							if (pinnedHeader != null && minChildSection != null && minChildSection.javaClass != pinnedHeader!!.javaClass) {
								pinnedHeaderShadowTargetAlpha = 1.0f
							}

							val count = sectionsAdapter!!.getCountForSection(startSection)
							val pos = sectionsAdapter!!.getPositionInSectionForPosition(firstVisibleItem)
							val sectionOffsetY = if (maxBottom != 0 && maxBottom < measuredHeight - paddingBottom) 0 else sectionOffset

							if (pos == count - 1) {
								val headerHeight = pinnedHeader!!.height
								var headerTop = paddingTop
								val available = minChild.top - paddingTop - sectionOffset + minChild.height

								if (available < headerHeight) {
									headerTop = available - headerHeight
								}

								if (headerTop < 0) {
									pinnedHeader?.tag = paddingTop + sectionOffsetY + headerTop
								}
								else {
									pinnedHeader?.tag = paddingTop + sectionOffsetY
								}
							}
							else {
								pinnedHeader?.tag = paddingTop + sectionOffsetY
							}

							invalidate()
						}
					}
					else {
						val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
						// val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

						if (firstVisibleItem == NO_POSITION) {
							return
						}

						if ((scrollingByUser || force) && fastScroll != null && !fastScroll!!.isPressed) {
							val adapter = adapter

							if (adapter is FastScrollAdapter) {
								val p = adapter.getScrollProgress(this@RecyclerListView)
								val visible = adapter.fastScrollIsVisible(this@RecyclerListView)

								fastScroll?.setIsVisible(visible)
								fastScroll?.setProgress(min(1.0f, p))
								fastScroll?.getCurrentLetter(false)
							}
						}
					}
				}
			}
		}
	}

	fun setListSelectorColor(color: Int) {
		Theme.setSelectorDrawableColor(selectorDrawable, color, true)
	}

	fun setOnItemLongClickListener(listener: OnItemLongClickListener?) {
		setOnItemLongClickListener(listener, ViewConfiguration.getLongPressTimeout().toLong())
	}

	fun setOnItemLongClickListener(listener: OnItemLongClickListener?, duration: Long) {
		onItemLongClickListener = listener
		gestureDetector?.setIsLongpressEnabled(listener != null)
		gestureDetector?.setLongpressDuration(duration)
	}

	fun setOnItemLongClickListener(listener: OnItemLongClickListenerExtended?) {
		setOnItemLongClickListener(listener, ViewConfiguration.getLongPressTimeout().toLong())
	}

	fun setOnItemLongClickListener(listener: OnItemLongClickListenerExtended?, duration: Long) {
		onItemLongClickListenerExtended = listener
		gestureDetector?.setIsLongpressEnabled(listener != null)
		gestureDetector?.setLongpressDuration(duration)
	}

	fun setEmptyView(view: View?) {
		if (emptyView === view) {
			return
		}

		emptyView?.animate()?.setListener(null)?.cancel()

		emptyView = view

		if (animateEmptyView && emptyView != null) {
			emptyView?.gone()
		}

		if (isHidden) {
			if (emptyView != null) {
				emptyViewAnimateToVisibility = GONE
				emptyView?.gone()
			}
		}
		else {
			emptyViewAnimateToVisibility = -1
			checkIfEmpty(updateEmptyViewAnimated())
		}
	}

	protected open fun updateEmptyViewAnimated(): Boolean {
		return isAttachedToWindow
	}

	fun invalidateViews() {
		children.forEach {
			it.invalidate()
		}
	}

	fun updateFastScrollColors() {
		fastScroll?.updateColors()
	}

	fun setPinnedHeaderShadowDrawable(drawable: Drawable?) {
		pinnedHeaderShadowDrawable = drawable
	}

	override fun canScrollVertically(direction: Int): Boolean {
		return scrollEnabled && super.canScrollVertically(direction)
	}

	fun setScrollEnabled(value: Boolean) {
		scrollEnabled = value
	}

	fun highlightRow(callback: IntReturnCallback) {
		highlightRowInternal(callback, true)
	}

	private fun highlightRowInternal(callback: IntReturnCallback, canHighlightLater: Boolean) {
		if (removeHighlightSelectionRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(removeHighlightSelectionRunnable)
			removeHighlightSelectionRunnable = null
		}

		val holder = findViewHolderForAdapterPosition(callback.run())

		if (holder != null) {
			positionSelector(holder.layoutPosition, holder.itemView)

			if (selectorDrawable != null) {
				val d = selectorDrawable!!.current

				if (d is TransitionDrawable) {
					if (onItemLongClickListener != null || onItemClickListenerExtended != null) {
						d.startTransition(ViewConfiguration.getLongPressTimeout())
					}
					else {
						d.resetTransition()
					}
				}

				selectorDrawable?.setHotspot((holder.itemView.measuredWidth / 2).toFloat(), (holder.itemView.measuredHeight / 2).toFloat())
			}

			if (selectorDrawable != null && selectorDrawable!!.isStateful) {
				if (selectorDrawable!!.setState(drawableStateForSelector)) {
					invalidateDrawable(selectorDrawable!!)
				}
			}

			AndroidUtilities.runOnUIThread(Runnable {
				removeHighlightSelectionRunnable = null
				pendingHighlightPosition = null

				if (selectorDrawable != null) {
					val d = selectorDrawable?.current

					if (d is TransitionDrawable) {
						d.resetTransition()
					}
				}

				if (selectorDrawable?.isStateful == true) {
					selectorDrawable?.state = StateSet.NOTHING
				}
			}.also {
				removeHighlightSelectionRunnable = it
			}, 700)
		}
		else if (canHighlightLater) {
			pendingHighlightPosition = callback
		}
	}

	override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
		if (!isEnabled) {
			return false
		}

		if (disallowInterceptTouchEvents) {
			requestDisallowInterceptTouchEvent(true)
		}

		return onInterceptTouchListener != null && onInterceptTouchListener!!.onInterceptTouchEvent(e) || super.onInterceptTouchEvent(e)
	}

	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		val fastScroll = fastScroll

		if (fastScroll != null && fastScroll.isVisible && fastScroll.isMoving && ev.actionMasked != MotionEvent.ACTION_UP && ev.actionMasked != MotionEvent.ACTION_CANCEL) {
			return true
		}

		return if (sectionsAdapter != null && pinnedHeader != null && pinnedHeader!!.alpha != 0f && pinnedHeader!!.dispatchTouchEvent(ev)) {
			true
		}
		else {
			super.dispatchTouchEvent(ev)
		}
	}

	private var emptyViewAnimateToVisibility = 0

	private fun checkIfEmpty(animated: Boolean) {
		@Suppress("NAME_SHADOWING") var animated = animated

		if (isHidden) {
			return
		}

		if (adapter == null || emptyView == null) {
			if (hiddenByEmptyView && visibility != VISIBLE) {
				visibility = VISIBLE
				hiddenByEmptyView = false
			}

			return
		}

		val emptyViewVisible = emptyViewIsVisible()
		var newVisibility = if (emptyViewVisible) VISIBLE else GONE

		if (!animateEmptyView || !SharedConfig.animationsEnabled()) {
			animated = false
		}

		if (animated) {
			if (emptyViewAnimateToVisibility != newVisibility) {
				emptyViewAnimateToVisibility = newVisibility

				if (newVisibility == VISIBLE) {
					emptyView?.animate()?.setListener(null)?.cancel()

					if (emptyView?.visibility == GONE) {
						emptyView?.visible()
						emptyView?.alpha = 0f

						if (emptyViewAnimationType == 1) {
							emptyView?.scaleX = 0.7f
							emptyView?.scaleY = 0.7f
						}
					}

					emptyView?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)?.setDuration(150)?.start()
				}
				else {
					if (emptyView?.visibility != GONE) {
						val animator = emptyView?.animate()?.alpha(0f)

						if (emptyViewAnimationType == 1) {
							animator?.scaleY(0.7f)?.scaleX(0.7f)
						}

						animator?.setDuration(150)?.setListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								emptyView?.gone()
								emptyView?.scaleX = 1f
								emptyView?.scaleY = 1f
								emptyView?.alpha = 1f
							}
						})

						animator?.start()
					}
				}
			}
		}
		else {
			emptyViewAnimateToVisibility = newVisibility
			emptyView?.visibility = newVisibility
			emptyView?.alpha = 1f
		}

		if (hideIfEmpty) {
			newVisibility = if (emptyViewVisible) INVISIBLE else VISIBLE

			if (visibility != newVisibility) {
				visibility = newVisibility
			}

			hiddenByEmptyView = true
		}
	}

	open fun emptyViewIsVisible(): Boolean {
		return if (adapter == null || isFastScrollAnimationRunning) {
			false
		}
		else {
			adapter?.itemCount == 0
		}
	}

	fun hide() {
		if (isHidden) {
			return
		}

		isHidden = true

		if (visibility != GONE) {
			visibility = GONE
		}

		emptyView?.visibility = GONE
	}

	fun show() {
		if (!isHidden) {
			return
		}

		isHidden = false

		checkIfEmpty(false)
	}

	override fun setVisibility(visibility: Int) {
		super.setVisibility(visibility)

		if (visibility != VISIBLE) {
			hiddenByEmptyView = false
		}
	}

	@Deprecated("Deprecated in Java")
	override fun setOnScrollListener(listener: OnScrollListener?) {
		onScrollListener = listener
	}

	fun setHideIfEmpty(value: Boolean) {
		hideIfEmpty = value
	}

	fun getOnScrollListener(): OnScrollListener? {
		return onScrollListener
	}

	fun setOnInterceptTouchListener(listener: OnInterceptTouchListener?) {
		onInterceptTouchListener = listener
	}

	fun setInstantClick(value: Boolean) {
		instantClick = value
	}

	fun setDisallowInterceptTouchEvents(value: Boolean) {
		disallowInterceptTouchEvents = value
	}

	fun setFastScrollEnabled(type: Int) {
		fastScroll = FastScroll(context, type)
		(parent as? ViewGroup)?.addView(fastScroll)
	}

	fun setFastScrollVisible(value: Boolean) {
		fastScroll?.visibility = if (value) VISIBLE else GONE
		fastScroll?.isVisible = value
	}

	fun setSectionsType(@SectionsType type: Int) {
		sectionsType = type

		if (sectionsType == SECTIONS_TYPE_STICKY_HEADERS || sectionsType == SECTIONS_TYPE_FAST_SCROLL_ONLY) {
			headers = ArrayList()
			headersCache = ArrayList()
		}
	}

	fun setPinnedSectionOffsetY(offset: Int) {
		sectionOffset = offset
		invalidate()
	}

	private fun positionSelector(position: Int, sel: View, manageHotspot: Boolean = false, x: Float = -1f, y: Float = -1f) {
		if (removeHighlightSelectionRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(removeHighlightSelectionRunnable)
			removeHighlightSelectionRunnable = null
			pendingHighlightPosition = null
		}

		if (selectorDrawable == null) {
			return
		}

		val positionChanged = position != selectorPosition

		val bottomPadding = if (adapter is SelectionAdapter) {
			(adapter as SelectionAdapter?)!!.getSelectionBottomPadding(sel)
		}
		else {
			0
		}

		if (position != NO_POSITION) {
			selectorPosition = position
		}

		if (selectorType == 8) {
			Theme.setMaskDrawableRad(selectorDrawable, selectorRadius, 0)
		}
		else if (topBottomSelectorRadius > 0 && adapter != null) {
			Theme.setMaskDrawableRad(selectorDrawable, if (position == 0) topBottomSelectorRadius else 0, if (position == adapter!!.itemCount - 2) topBottomSelectorRadius else 0)
		}

		selectorRect.set(sel.left, sel.top, sel.right, sel.bottom - bottomPadding)
		selectorRect.offset(sel.translationX.toInt(), sel.translationY.toInt())

		val enabled = sel.isEnabled

		if (isChildViewEnabled != enabled) {
			isChildViewEnabled = enabled
		}

		if (positionChanged) {
			selectorDrawable?.setVisible(false, false)
			selectorDrawable?.state = StateSet.NOTHING
		}

		selectorDrawable?.bounds = selectorRect

		if (positionChanged) {
			if (visibility == VISIBLE) {
				selectorDrawable?.setVisible(true, false)
			}
		}

		if (manageHotspot) {
			selectorDrawable?.setHotspot(x, y)
		}
	}

	fun setAllowItemsInteractionDuringAnimation(value: Boolean) {
		allowItemsInteractionDuringAnimation = value
	}

	fun hideSelector(animated: Boolean) {
		if (pressedChildView != null) {
			val child = pressedChildView!!

			onChildPressed(pressedChildView, 0f, 0f, false)

			pressedChildView = null

			if (animated) {
				removeSelection(child, null)
			}
		}

		if (!animated) {
			selectorDrawable?.state = StateSet.NOTHING
			selectorRect.setEmpty()
		}
	}

	private fun updateSelectorState() {
		if (selectorDrawable != null && selectorDrawable!!.isStateful) {
			if (pressedChildView != null) {
				if (selectorDrawable!!.setState(drawableStateForSelector)) {
					invalidateDrawable(selectorDrawable!!)
				}
			}
			else if (removeHighlightSelectionRunnable == null) {
				selectorDrawable?.state = StateSet.NOTHING
			}
		}
	}

	private val drawableStateForSelector: IntArray
		get() {
			val state = onCreateDrawableState(1)
			state[state.size - 1] = android.R.attr.state_pressed
			return state
		}

	override fun onChildAttachedToWindow(child: View) {
		if (adapter is SelectionAdapter) {
			val holder = findContainingViewHolder(child)

			if (holder != null) {
				child.isEnabled = (adapter as SelectionAdapter?)!!.isEnabled(holder)

				if (accessibilityEnabled) {
					child.accessibilityDelegate = accessibilityDelegate
				}
			}
		}
		else {
			child.isEnabled = false
			child.accessibilityDelegate = null
		}

		super.onChildAttachedToWindow(child)
	}

	override fun drawableStateChanged() {
		super.drawableStateChanged()
		updateSelectorState()
	}

	public override fun verifyDrawable(drawable: Drawable): Boolean {
		return selectorDrawable === drawable || super.verifyDrawable(drawable)
	}

	override fun jumpDrawablesToCurrentState() {
		super.jumpDrawablesToCurrentState()
		selectorDrawable?.jumpToCurrentState()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		if (fastScroll != null && fastScroll!!.parent !== parent) {
			var parent = fastScroll?.parent as? ViewGroup
			parent?.removeView(fastScroll)
			parent = getParent() as? ViewGroup
			parent?.addView(fastScroll)
		}
	}

	override fun setAdapter(adapter: Adapter<*>?) {
		val oldAdapter = getAdapter()
		oldAdapter?.unregisterAdapterDataObserver(observer)

		if (headers != null) {
			headers?.clear()
			headersCache?.clear()
		}

		currentFirst = -1
		selectorPosition = NO_POSITION
		selectorRect.setEmpty()
		pinnedHeader = null

		sectionsAdapter = if (adapter is SectionsAdapter) {
			adapter
		}
		else {
			null
		}

		super.setAdapter(adapter)

		adapter?.registerAdapterDataObserver(observer)
		checkIfEmpty(false)
	}

	override fun stopScroll() {
		try {
			super.stopScroll()
		}
		catch (ignore: NullPointerException) {
		}
	}

	override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int): Boolean {
		if (longPressCalled) {
			onItemLongClickListenerExtended?.onMove(dx.toFloat(), dy.toFloat())

			consumed?.set(0, dx)
			consumed?.set(1, dy)

			return true
		}

		return super.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}

	private fun getSectionHeaderView(section: Int, oldView: View?): View? {
		val shouldLayout = oldView == null
		val view = sectionsAdapter?.getSectionHeaderView(section, oldView)

		if (shouldLayout) {
			ensurePinnedHeaderLayout(view, false)
		}

		return view
	}

	private fun ensurePinnedHeaderLayout(header: View?, forceLayout: Boolean) {
		if (header == null) {
			return
		}

		if (header.isLayoutRequested || forceLayout) {
			if (sectionsType == SECTIONS_TYPE_STICKY_HEADERS) {
				val layoutParams = header.layoutParams
				val heightSpec = MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY)
				val widthSpec = MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY)

				try {
					header.measure(widthSpec, heightSpec)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
			else if (sectionsType == SECTIONS_TYPE_DATE) {
				val widthSpec = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
				val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

				try {
					header.measure(widthSpec, heightSpec)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			header.layout(0, 0, header.measuredWidth, header.measuredHeight)
		}
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)

		overlayContainer?.requestLayout()

		if (sectionsType == SECTIONS_TYPE_STICKY_HEADERS) {
			if (sectionsAdapter == null || headers.isNullOrEmpty()) {
				return
			}

			for (a in headers!!.indices) {
				val header = headers!![a]
				ensurePinnedHeaderLayout(header, true)
			}
		}
		else if (sectionsType == SECTIONS_TYPE_DATE) {
			if (sectionsAdapter == null || pinnedHeader == null) {
				return
			}

			ensurePinnedHeaderLayout(pinnedHeader, true)
		}
	}

	override fun dispatchDraw(canvas: Canvas) {
		itemsEnterAnimator?.dispatchDraw()

		if (drawSelectorBehind && !selectorRect.isEmpty) {
			selectorDrawable?.bounds = selectorRect
			canvas.save()
			selectorTransformer?.accept(canvas)
			selectorDrawable?.draw(canvas)
			canvas.restore()
		}

		super.dispatchDraw(canvas)

		if (!drawSelectorBehind && !selectorRect.isEmpty) {
			selectorDrawable?.bounds = selectorRect
			canvas.save()
			selectorTransformer?.accept(canvas)
			selectorDrawable?.draw(canvas)
			canvas.restore()
		}

		overlayContainer?.draw(canvas)

		if (sectionsType == SECTIONS_TYPE_STICKY_HEADERS) {
			if (sectionsAdapter != null) {
				headers?.forEach {
					val header = it ?: return@forEach
					val saveCount = canvas.save()
					val top = header.tag as Int
					canvas.translate(if (LocaleController.isRTL) (width - header.width).toFloat() else 0f, top.toFloat())
					canvas.clipRect(0, 0, width, header.measuredHeight)
					header.draw(canvas)
					canvas.restoreToCount(saveCount)
				}
			}
		}
		else if (sectionsType == SECTIONS_TYPE_DATE) {
			if (sectionsAdapter != null && pinnedHeader != null && pinnedHeader!!.alpha != 0f) {
				val saveCount = canvas.save()
				val top = pinnedHeader!!.tag as Int
				canvas.translate(if (LocaleController.isRTL) (width - pinnedHeader!!.width).toFloat() else 0f, top.toFloat())

				if (pinnedHeaderShadowDrawable != null) {
					pinnedHeaderShadowDrawable?.setBounds(0, pinnedHeader!!.measuredHeight, width, pinnedHeader!!.measuredHeight + pinnedHeaderShadowDrawable!!.intrinsicHeight)
					pinnedHeaderShadowDrawable?.alpha = (255 * pinnedHeaderShadowAlpha).toInt()
					pinnedHeaderShadowDrawable?.draw(canvas)

					val newTime = SystemClock.elapsedRealtime()
					val dt = min(20, newTime - lastAlphaAnimationTime)

					lastAlphaAnimationTime = newTime

					if (pinnedHeaderShadowAlpha < pinnedHeaderShadowTargetAlpha) {
						pinnedHeaderShadowAlpha += dt / 180.0f

						if (pinnedHeaderShadowAlpha > pinnedHeaderShadowTargetAlpha) {
							pinnedHeaderShadowAlpha = pinnedHeaderShadowTargetAlpha
						}

						invalidate()
					}
					else if (pinnedHeaderShadowAlpha > pinnedHeaderShadowTargetAlpha) {
						pinnedHeaderShadowAlpha -= dt / 180.0f

						if (pinnedHeaderShadowAlpha < pinnedHeaderShadowTargetAlpha) {
							pinnedHeaderShadowAlpha = pinnedHeaderShadowTargetAlpha
						}

						invalidate()
					}
				}

				canvas.clipRect(0, 0, width, pinnedHeader!!.measuredHeight)

				pinnedHeader?.draw(canvas)

				canvas.restoreToCount(saveCount)
			}
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		selectorPosition = NO_POSITION
		selectorRect.setEmpty()
		itemsEnterAnimator?.onDetached()
	}

	fun addOverlayView(view: View?, layoutParams: FrameLayout.LayoutParams?) {
		if (overlayContainer == null) {
			overlayContainer = object : FrameLayout(context) {
				override fun requestLayout() {
					super.requestLayout()

					try {
						val measuredWidth = this@RecyclerListView.measuredWidth
						val measuredHeight = this@RecyclerListView.measuredHeight
						measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
						layout(0, 0, overlayContainer!!.measuredWidth, overlayContainer!!.measuredHeight)
					}
					catch (ignored: Exception) {
					}
				}
			}
		}

		overlayContainer?.addView(view, layoutParams)
	}

	fun removeOverlayView(view: View?) {
		overlayContainer?.removeView(view)
	}

	override fun requestLayout() {
		if (isFastScrollAnimationRunning) {
			return
		}

		super.requestLayout()
	}

	fun setAnimateEmptyView(animate: Boolean, emptyViewAnimationType: Int) {
		animateEmptyView = animate
		this.emptyViewAnimationType = emptyViewAnimationType
	}

	class FocusableOnTouchListener : OnTouchListener {
		private var x = 0f
		private var y = 0f
		private var onFocus = false

		override fun onTouch(v: View, event: MotionEvent): Boolean {
			val parent = v.parent ?: return false

			if (event.action == MotionEvent.ACTION_DOWN) {
				x = event.x
				y = event.y
				onFocus = true
				parent.requestDisallowInterceptTouchEvent(true)
			}

			if (event.action == MotionEvent.ACTION_MOVE) {
				val dx = x - event.x
				val dy = y - event.y
				val touchSlop = ViewConfiguration.get(v.context).scaledTouchSlop.toFloat()

				if (onFocus && sqrt((dx * dx + dy * dy).toDouble()) > touchSlop) {
					onFocus = false
					parent.requestDisallowInterceptTouchEvent(false)
				}
			}
			else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
				onFocus = false
				parent.requestDisallowInterceptTouchEvent(false)
			}

			return false
		}
	}

	override fun setTranslationY(translationY: Float) {
		super.setTranslationY(translationY)
		fastScroll?.translationY = translationY
	}

	fun startMultiselect(positionFrom: Int, useRelativePositions: Boolean, multiSelectionListener: OnMultiSelectionChanged?) {
		if (!isMultiselect) {
			listPaddings = IntArray(2)
			selectedPositions = HashSet()
			parent.requestDisallowInterceptTouchEvent(true)
			this.multiSelectionListener = multiSelectionListener
			isMultiselect = true
			currentSelectedPosition = positionFrom
			startSelectionFrom = currentSelectedPosition
		}

		this.useRelativePositions = useRelativePositions
	}

	override fun onTouchEvent(e: MotionEvent): Boolean {
		if (fastScroll != null && fastScroll!!.internalPressed) {
			return false
		}

		if (isMultiselect && e.action != MotionEvent.ACTION_DOWN && e.action != MotionEvent.ACTION_UP && e.action != MotionEvent.ACTION_CANCEL) {
			if (lastX == Float.MAX_VALUE && lastY == Float.MAX_VALUE) {
				lastX = e.x
				lastY = e.y
			}

			if (!multiSelectionGestureStarted && abs(e.y - lastY) > touchSlop) {
				multiSelectionGestureStarted = true
				parent.requestDisallowInterceptTouchEvent(true)
			}

			if (multiSelectionGestureStarted) {
				checkMultiselect(e.x, e.y)

				multiSelectionListener!!.getPaddings(listPaddings)

				if (e.y > measuredHeight - AndroidUtilities.dp(56f) - listPaddings[1] && !(currentSelectedPosition < startSelectionFrom && multiSelectionListener!!.limitReached())) {
					startMultiselectScroll(false)
				}
				else if (e.y < AndroidUtilities.dp(56f) + listPaddings[0] && !(currentSelectedPosition > startSelectionFrom && multiSelectionListener!!.limitReached())) {
					startMultiselectScroll(true)
				}
				else {
					cancelMultiselectScroll()
				}
			}

			return true
		}

		lastX = Float.MAX_VALUE
		lastY = Float.MAX_VALUE
		isMultiselect = false
		multiSelectionGestureStarted = false

		parent.requestDisallowInterceptTouchEvent(false)

		cancelMultiselectScroll()

		return super.onTouchEvent(e)
	}

	private fun checkMultiselect(x: Float, y: Float): Boolean {
		@Suppress("NAME_SHADOWING") var x = x
		@Suppress("NAME_SHADOWING") var y = y

		y = min((measuredHeight - listPaddings[1]).toFloat(), max(y, listPaddings[0].toFloat()))
		x = min(measuredWidth.toFloat(), max(x, 0f))

		for (i in 0 until childCount) {
			multiSelectionListener?.getPaddings(listPaddings)

			if (!useRelativePositions) {
				val child = getChildAt(i)

				AndroidUtilities.rectTmp[child.left.toFloat(), child.top.toFloat(), (child.left + child.measuredWidth).toFloat()] = (child.top + child.measuredHeight).toFloat()

				if (AndroidUtilities.rectTmp.contains(x, y)) {
					var position = getChildLayoutPosition(child)

					if (currentSelectedPosition != position) {
						val selectionFromTop = currentSelectedPosition > startSelectionFrom || position > startSelectionFrom

						position = multiSelectionListener!!.checkPosition(position, selectionFromTop)

						if (selectionFromTop) {
							if (position > currentSelectedPosition) {
								if (!multiSelectionListener!!.limitReached()) {
									for (k in currentSelectedPosition + 1..position) {
										if (k == startSelectionFrom) {
											continue
										}

										if (multiSelectionListener!!.canSelect(k)) {
											multiSelectionListener!!.onSelectionChanged(k, true, x, y)
										}
									}
								}
							}
							else {
								for (k in currentSelectedPosition downTo position + 1) {
									if (k == startSelectionFrom) {
										continue
									}

									if (multiSelectionListener!!.canSelect(k)) {
										multiSelectionListener!!.onSelectionChanged(k, false, x, y)
									}
								}
							}
						}
						else {
							if (position > currentSelectedPosition) {
								for (k in currentSelectedPosition until position) {
									if (k == startSelectionFrom) {
										continue
									}

									if (multiSelectionListener!!.canSelect(k)) {
										multiSelectionListener!!.onSelectionChanged(k, false, x, y)
									}
								}
							}
							else {
								if (!multiSelectionListener!!.limitReached()) {
									for (k in currentSelectedPosition - 1 downTo position) {
										if (k == startSelectionFrom) {
											continue
										}

										if (multiSelectionListener!!.canSelect(k)) {
											multiSelectionListener!!.onSelectionChanged(k, true, x, y)
										}
									}
								}
							}
						}
					}

					if (!multiSelectionListener!!.limitReached()) {
						currentSelectedPosition = position
					}

					break
				}
			}
		}

		return true
	}

	private fun cancelMultiselectScroll() {
		multiselectScrollRunning = false
		AndroidUtilities.cancelRunOnUIThread(scroller)
	}

	var scroller: Runnable = object : Runnable {
		override fun run() {
			val dy: Int

			multiSelectionListener?.getPaddings(listPaddings)

			if (multiselectScrollToTop) {
				dy = -AndroidUtilities.dp(12f)
				checkMultiselect(0f, listPaddings[0].toFloat())
			}
			else {
				dy = AndroidUtilities.dp(12f)
				checkMultiselect(0f, (measuredHeight - listPaddings[1]).toFloat())
			}

			multiSelectionListener?.scrollBy(dy)

			if (multiselectScrollRunning) {
				AndroidUtilities.runOnUIThread(this)
			}
		}
	}

	private val defaultOnScrollListener = object : OnScrollListener() {
		override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
			if (newState != SCROLL_STATE_IDLE && pressedChildView != null) {
				if (selectChildRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(selectChildRunnable)
					selectChildRunnable = null
				}

				val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)

				try {
					gestureDetector?.onTouchEvent(event)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				pressedChildView?.onTouchEvent(event)

				event.recycle()

				val child = pressedChildView
				onChildPressed(pressedChildView, 0f, 0f, false)
				pressedChildView = null
				removeSelection(child, null)
				interceptedByChild = false
			}

			onScrollListener?.onScrollStateChanged(recyclerView, newState)

			scrollingByUser = newState == SCROLL_STATE_DRAGGING || newState == SCROLL_STATE_SETTLING

			if (scrollingByUser) {
				scrolledByUserOnce = true
			}
		}

		override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
			onScrollListener?.onScrolled(recyclerView, dx, dy)

			if (selectorPosition != NO_POSITION) {
				selectorRect.offset(-dx, -dy)
				selectorDrawable?.bounds = selectorRect
				invalidate()
			}
			else {
				selectorRect.setEmpty()
			}

			checkSection(false)

			if (dy != 0) {
				fastScroll?.showFloatingDate()
			}
		}
	}

	init {
		setGlowColor(getThemedColor(Theme.key_actionBarDefault))

		selectorDrawable?.callback = this

		try {
			if (!gotAttributes) {
				attributes = getResourceDeclareStyleableIntArray("com.android.internal", "View")

				if (attributes == null) {
					attributes = IntArray(0)
				}

				gotAttributes = true
			}

			val a = context.theme.obtainStyledAttributes(attributes!!)
			@SuppressLint("DiscouragedPrivateApi") val initializeScrollbars = View::class.java.getDeclaredMethod("initializeScrollbars", TypedArray::class.java)
			initializeScrollbars.invoke(this, a)
			a.recycle()
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}

		super.addOnScrollListener(defaultOnScrollListener)

		addOnItemTouchListener(RecyclerListViewItemClickListener(context))
	}

	private fun startMultiselectScroll(top: Boolean) {
		multiselectScrollToTop = top

		if (!multiselectScrollRunning) {
			multiselectScrollRunning = true
			AndroidUtilities.cancelRunOnUIThread(scroller)
			AndroidUtilities.runOnUIThread(scroller)
		}
	}

	protected fun getThemedColor(key: String?): Int {
		return Theme.getColor(key)
	}

	protected fun getThemedDrawable(key: String?): Drawable {
		return Theme.getThemeDrawable(key)
	}

	protected fun getThemedPaint(paintKey: String?): Paint {
		return Theme.getThemePaint(paintKey)
	}

	interface OnMultiSelectionChanged {
		fun onSelectionChanged(position: Int, selected: Boolean, x: Float, y: Float)
		fun canSelect(position: Int): Boolean
		fun checkPosition(position: Int, selectionFromTop: Boolean): Int
		fun limitReached(): Boolean
		fun getPaddings(paddings: IntArray?)
		fun scrollBy(dy: Int)
	}

	fun setItemsEnterAnimator(itemsEnterAnimator: RecyclerItemsEnterAnimator?) {
		this.itemsEnterAnimator = itemsEnterAnimator
	}

	fun setAccessibilityEnabled(accessibilityEnabled: Boolean) {
		this.accessibilityEnabled = accessibilityEnabled
	}

	companion object {
		const val SECTIONS_TYPE_SIMPLE = 0
		const val SECTIONS_TYPE_STICKY_HEADERS = 1
		const val SECTIONS_TYPE_DATE = 2
		const val SECTIONS_TYPE_FAST_SCROLL_ONLY = 3
		private var attributes: IntArray? = null
		private var gotAttributes = false
		const val LETTER_TYPE = 0
		const val DATE_TYPE = 1
	}
}
