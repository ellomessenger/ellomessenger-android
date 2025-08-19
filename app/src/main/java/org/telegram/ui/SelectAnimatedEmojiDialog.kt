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
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.LongSparseArray
import android.util.SparseArray
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScrollerCustom
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DocumentObject.getSvgThumb
import org.telegram.messenger.Emoji
import org.telegram.messenger.Emoji.fullyConsistsOfEmojis
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.ImageReceiver.BackgroundThreadDrawHolder
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.*
import org.telegram.tgnet.TLRPC.TLAccountClearRecentEmojiStatuses
import org.telegram.tgnet.TLRPC.TLEmojiStatus
import org.telegram.tgnet.TLRPC.TLEmojiStatusUntil
import org.telegram.tgnet.TLRPC.TLInputStickerSetEmojiDefaultStatuses
import org.telegram.tgnet.TLRPC.TLStickerSetFullCovered
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.FixedHeightEmptyCell
import org.telegram.ui.Components.AlertsCreator.createStatusUntilDatePickerDialog
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AnimatedEmojiSpan.InvalidateHolder
import org.telegram.ui.Components.AnimatedTextView
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CloseProgressDrawable2
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.DrawingInBackgroundThreadDrawable
import org.telegram.ui.Components.EditTextCaption
import org.telegram.ui.Components.EmojiPacksAlert
import org.telegram.ui.Components.EmojiTabsStrip
import org.telegram.ui.Components.EmojiView.EmojiPack
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Premium.PremiumButtonView
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet
import org.telegram.ui.Components.Premium.PremiumLockIconView
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.Reactions.ReactionsUtils
import org.telegram.ui.Components.Reactions.VisibleReaction
import org.telegram.ui.Components.RecyclerAnimationScrollHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.OnItemLongClickListenerExtended
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import java.lang.reflect.Field
import java.util.Objects
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.graphics.withSave
import androidx.core.view.isVisible
import androidx.core.util.size
import androidx.core.view.isNotEmpty
import androidx.core.graphics.withTranslation
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withScale

open class SelectAnimatedEmojiDialog @JvmOverloads constructor(private val baseFragment: BaseFragment, context: Context, private val includeEmpty: Boolean, private val emojiX: Int? = null, private val type: Int = TYPE_EMOJI_STATUS, topPaddingDp: Int = 16) : FrameLayout(context), NotificationCenterDelegate {
	private val durationScale = 1f
	private val showDuration = (800 * durationScale).toLong()
	private val maxDim = .25f
	private val currentAccount = UserConfig.selectedAccount
	private val contentView: FrameLayout
	private var emojiTabs: EmojiTabsStrip? = null
	private val emojiTabsShadow: View
	private val searchBox = SearchBox(context)
	private val emojiSearchEmptyViewImageView: BackupImageView?
	private val topGradientView: View
	private val bottomGradientView: View
	private val adapter = Adapter()
	private val searchAdapter = SearchAdapter()
	private var layoutManager: GridLayoutManager? = null
	private var searchLayoutManager: GridLayoutManager? = null
	private val scrollHelper: RecyclerAnimationScrollHelper
	private val contentViewForeground: View
	private val rowHashCodes = mutableListOf<Int>()
	private val positionToSection = SparseIntArray()
	private val sectionToPosition = SparseIntArray()
	private val positionToExpand = SparseIntArray()
	private val positionToButton = SparseIntArray()
	private val expandedEmojiSets = mutableListOf<Long>()
	private val installedEmojiSets = mutableListOf<Long>()
	private val recent = mutableListOf<AnimatedEmojiSpan>()
	private val topReactions = mutableListOf<VisibleReaction>()
	private val recentReactions = mutableListOf<VisibleReaction>()
	private val defaultStatuses = mutableListOf<AnimatedEmojiSpan>()
	private val packs = mutableListOf<EmojiPack>()
	private val premiumStarColorFilter: ColorFilter
	private val overshootInterpolator = OvershootInterpolator(2f)
	private val emptyViewEmojis = ArrayList<String>(4)
	private var bigReactionListener: OnLongPressedListener? = null
	private var onRecentClearedListener: OnRecentClearedListener? = null
	val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	val selectorAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	val gridViewContainer: FrameLayout
	var emojiGridView: EmojiListView? = null
	val emojiSearchGridView: EmojiListView
	val emojiSearchEmptyView: FrameLayout
	var cancelPressed = false
	var searching = false
	var searched = false
	private var selectedReactions = HashSet<VisibleReaction>()
	private val selectedDocumentIds = mutableSetOf<Long?>()
	var selectedReactionView: ImageViewEmoji? = null
	var pressedProgress = 0f
	val bigReactionImageReceiver = ImageReceiver()
	private var bigReactionAnimatedEmoji: AnimatedEmojiDrawable? = null
	val paint = Paint()
	private var searchRow = 0
	private var recentReactionsStartRow = 0
	private var recentReactionsEndRow = 0
	private var topReactionsStartRow = 0
	private var topReactionsEndRow = 0
	private var recentReactionsSectionRow = 0
	private var popularSectionRow = 0
	private var longtapHintRow = 0
	private var recentExpandButton: EmojiPackExpand? = null
	private var isAttached = false
	private var bubble1View: View? = null
	private var bubble2View: View? = null
	private var totalCount = 0
	private var recentExpanded = false
	private var includeHint = false
	private var hintExpireDate: Int? = null
	private var drawBackground = true
	private var recentReactionsToSet: List<VisibleReaction>? = null
	private var selectStatusDateDialog: SelectStatusDurationDialog? = null
	private var scaleX = 0f
	private var scaleY = 0f
	private var topMarginDp = 0
	private var dimAnimator: ValueAnimator? = null
	private var premiumStar: Drawable? = null
	private var scrimAlpha = 1f
	private var scrimColor = 0
	private var scrimDrawable: SwapAnimatedEmojiDrawable? = null
	private val drawableToBounds by lazy { Rect() }
	private var scrimDrawableParent: View? = null
	private var emojiSelectAlpha = 1f
	private var emojiSelectView: ImageViewEmoji? = null
	private var emojiSelectRect: Rect? = null
	private var emojiSelectAnimator: ValueAnimator? = null
	private var bottomGradientShown = false
	private var smoothScrolling = false
	private var lastQuery: String? = null
	private var searchResult: ArrayList<VisibleReaction>? = null
	private var gridSwitchAnimator: ValueAnimator? = null
	private var gridSearch = false
	private var searchEmptyViewVisible = false
	private var searchEmptyViewAnimator: ValueAnimator? = null
	private var clearSearchRunnable: Runnable? = null
	private var searchRunnable: Runnable? = null
	private var animateExpandFromButton: View? = null
	private var animateExpandFromButtonTranslate = 0f
	private var animateExpandFromPosition = -1
	private var animateExpandToPosition = -1
	private var animateExpandStartTime: Long = -1
	private var defaultSetLoading = false
	private var dismiss: Runnable? = null
	private var showAnimator: ValueAnimator? = null
	private var hideAnimator: ValueAnimator? = null
	private var listStateId: Int? = null

	init {
		emptyViewEmojis.add("\uD83D\uDE16")
		emptyViewEmojis.add("\uD83D\uDE2B")
		emptyViewEmojis.add("\uD83E\uDEE0")
		emptyViewEmojis.add("\uD83D\uDE28")
		emptyViewEmojis.add("❓")
	}

	init {
		includeHint = MessagesController.getGlobalMainSettings().getInt("emoji" + (if (type == TYPE_EMOJI_STATUS) "status" else "reaction") + "usehint", 0) < 3
		selectorPaint.setColor(context.getColor(R.color.light_background))
		selectorAccentPaint.setColor(ColorUtils.setAlphaComponent(context.getColor(R.color.brand), 30))
		premiumStarColorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.MULTIPLY)

		val bubbleX = if (emojiX == null) null else MathUtils.clamp(emojiX, AndroidUtilities.dp(26f), AndroidUtilities.dp((340 - 48).toFloat()))
		val bubbleRight = bubbleX != null && bubbleX > AndroidUtilities.dp(170f)

		setFocusableInTouchMode(true)

		if (type == TYPE_EMOJI_STATUS || type == TYPE_SET_DEFAULT_REACTION) {
			topMarginDp = topPaddingDp

			setPadding(AndroidUtilities.dp(4f), AndroidUtilities.dp(4f), AndroidUtilities.dp(4f), AndroidUtilities.dp(4f))

			setOnTouchListener { _, e ->
				if (e.action == MotionEvent.ACTION_DOWN && dismiss != null) {
					dismiss?.run()
					return@setOnTouchListener true
				}

				false
			}
		}

		if (bubbleX != null) {
			bubble1View = View(context)
			val bubble1Drawable = ResourcesCompat.getDrawable(resources, R.drawable.shadowed_bubble1, null)?.mutate()
			bubble1Drawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.background), PorterDuff.Mode.MULTIPLY)

			bubble1View?.background = bubble1Drawable

			addView(bubble1View, LayoutHelper.createFrame(10, 10f, Gravity.TOP or Gravity.LEFT, bubbleX / AndroidUtilities.density + if (bubbleRight) -12 else 4, topMarginDp.toFloat(), 0f, 0f))
		}

		contentView = object : FrameLayout(context) {
			private val path = Path()
			private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

			override fun dispatchDraw(canvas: Canvas) {
				if (!drawBackground) {
					super.dispatchDraw(canvas)
					return
				}

				canvas.withSave {
					paint.setShadowLayer(AndroidUtilities.dp(2f).toFloat(), 0f, AndroidUtilities.dp(-0.66f).toFloat(), 0x1e000000)
					paint.setColor(context.getColor(R.color.background))
					paint.setAlpha((255 * alpha).toInt())

					val px: Float = ((bubbleX ?: width) / 2f) + AndroidUtilities.dp(20f)
					val w = (width - getPaddingLeft() - getPaddingRight()).toFloat()
					val h = (height - paddingBottom - paddingTop).toFloat()

					AndroidUtilities.rectTmp[getPaddingLeft() + (px - px * scaleX), paddingTop.toFloat(), getPaddingLeft() + px + (w - px) * scaleX] = paddingTop + h * scaleY

					path.rewind()
					path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12f).toFloat(), AndroidUtilities.dp(12f).toFloat(), Path.Direction.CW)

					drawPath(path, paint)
					clipPath(path)

					super.dispatchDraw(this)
				}
			}
		}

		if (type == TYPE_EMOJI_STATUS || type == TYPE_SET_DEFAULT_REACTION) {
			contentView.setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))
		}

		addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.FILL, 0f, (if (type == TYPE_EMOJI_STATUS || type == TYPE_SET_DEFAULT_REACTION) 6 + topMarginDp else 0).toFloat(), 0f, 0f))

		if (bubbleX != null) {
			bubble2View = object : View(context) {
				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					super.onMeasure(widthMeasureSpec, heightMeasureSpec)
					pivotX = (measuredWidth / 2).toFloat()
					pivotY = measuredHeight.toFloat()
				}
			}

			val bubble2Drawable = ResourcesCompat.getDrawable(resources, R.drawable.shadowed_bubble2_half, null)
			bubble2Drawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.background), PorterDuff.Mode.MULTIPLY)

			bubble2View?.background = bubble2Drawable

			addView(bubble2View, LayoutHelper.createFrame(17, 9f, Gravity.TOP or Gravity.LEFT, bubbleX / AndroidUtilities.density + if (bubbleRight) -25 else 10, (6 + 8 - 9 + topMarginDp).toFloat(), 0f, 0f))
		}

		emojiTabs = object : EmojiTabsStrip(context, false, true, null) {
			override fun onTabClick(index: Int): Boolean {
				if (smoothScrolling) {
					return false
				}

				var position = if (searchRow == -1) 1 else 0

				if (index > 0 && sectionToPosition.indexOfKey(index - 1) >= 0) {
					position = sectionToPosition[index - 1]
				}

				scrollToPosition(position, AndroidUtilities.dp(-2f))

				emojiTabs?.select(index)
				emojiGridView?.scrolledByUserOnce = true

				return true
			}

			override fun onTabCreate(button: EmojiTabButton) {
				if (showAnimator == null || showAnimator?.isRunning == true) {
					button.scaleX = 0f
					button.scaleY = 0f
				}
			}
		}

		emojiTabs?.recentTab?.setOnLongClickListener {
			onRecentLongClick()

			runCatching {
				performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
			}

			true
		}

		emojiTabs?.updateButtonDrawables = false
		emojiTabs?.setAnimatedEmojiCacheType(if (type == TYPE_EMOJI_STATUS || type == TYPE_SET_DEFAULT_REACTION) AnimatedEmojiDrawable.CACHE_TYPE_TAB_STRIP else AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_TAB_STRIP)
		emojiTabs?.animateAppear = bubbleX == null
		emojiTabs?.setPaddingLeft(5f)

		contentView.addView(emojiTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36f))

		emojiTabsShadow = object : View(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec)

				if (bubbleX != null) {
					pivotX = bubbleX.toFloat()
				}
			}
		}

		emojiTabsShadow.setBackgroundColor(context.getColor(R.color.divider))

		contentView.addView(emojiTabsShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.TOP, 0f, 36f, 0f, 0f))

		AndroidUtilities.updateViewVisibilityAnimated(emojiTabsShadow, true, 1f, false)

		emojiGridView = object : EmojiListView(context) {
			override fun onScrolled(dx: Int, dy: Int) {
				super.onScrolled(dx, dy)

				checkScroll()

				if (!smoothScrolling) {
					updateTabsPosition((layoutManager as? LinearLayoutManager)?.findFirstCompletelyVisibleItemPosition() ?: 0)
				}

				updateSearchBox()

				AndroidUtilities.updateViewVisibilityAnimated(emojiTabsShadow, emojiGridView!!.computeVerticalScrollOffset() != 0, 1f, true)
			}

			override fun onScrollStateChanged(state: Int) {
				if (state == SCROLL_STATE_IDLE) {
					smoothScrolling = false

					if (searchRow != -1 && searchBox.isVisible && searchBox.translationY > -AndroidUtilities.dp(51f)) {
						this@SelectAnimatedEmojiDialog.scrollToPosition(if (searchBox.translationY > -AndroidUtilities.dp(16f)) 0 else 1, 0)
					}
				}

				super.onScrollStateChanged(state)
			}
		}

		val emojiItemAnimator = object : DefaultItemAnimator() {
			override fun animateByScale(view: View): Float {
				return if (view is EmojiPackExpand) .6f else 0f
			}
		}

		emojiItemAnimator.addDuration = 220
		emojiItemAnimator.moveDuration = 260
		emojiItemAnimator.setChangeDuration(160)
		emojiItemAnimator.supportsChangeAnimations = false
		emojiItemAnimator.moveInterpolator = CubicBezierInterpolator.EASE_OUT_QUINT
		emojiItemAnimator.setDelayAnimations(false)

		emojiGridView?.setItemAnimator(emojiItemAnimator)
		emojiGridView?.setPadding(AndroidUtilities.dp(5f), AndroidUtilities.dp(2f), AndroidUtilities.dp(5f), AndroidUtilities.dp((2 + 36).toFloat()))
		emojiGridView?.setAdapter(adapter)

		emojiGridView?.setLayoutManager(object : GridLayoutManager(context, 8) {
			override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
				try {
					val linearSmoothScroller: LinearSmoothScrollerCustom = object : LinearSmoothScrollerCustom(recyclerView.context, POSITION_TOP) {
						override fun onEnd() {
							smoothScrolling = false
						}
					}

					linearSmoothScroller.targetPosition = position
					startSmoothScroll(linearSmoothScroller)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}.also { layoutManager = it })

		layoutManager?.spanSizeLookup = object : SpanSizeLookup() {
			override fun getSpanSize(position: Int): Int {
				return if (positionToSection.indexOfKey(position) >= 0 || positionToButton.indexOfKey(position) >= 0 || position == recentReactionsSectionRow || position == popularSectionRow || position == longtapHintRow || position == searchRow) layoutManager!!.spanCount else 1
			}
		}

		gridViewContainer = object : FrameLayout(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) + AndroidUtilities.dp(36f), MeasureSpec.EXACTLY))
			}
		}

		gridViewContainer.addView(emojiGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.FILL, 0f, 0f, 0f, 0f))

		emojiSearchGridView = EmojiListView(context)

		emojiSearchGridView.itemAnimator?.setDurations(180)
		emojiSearchGridView.itemAnimator?.moveInterpolator = CubicBezierInterpolator.EASE_OUT_QUINT

		val emptyViewText = TextView(context)
		emptyViewText.text = if (type == TYPE_EMOJI_STATUS) context.getString(R.string.NoEmojiFound) else context.getString(R.string.NoReactionsFound)
		emptyViewText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		emptyViewText.setTextColor(context.getColor(R.color.dark_gray))

		emojiSearchEmptyViewImageView = BackupImageView(context)

		emojiSearchEmptyView = FrameLayout(context)
		emojiSearchEmptyView.addView(emojiSearchEmptyViewImageView, LayoutHelper.createFrame(36, 36f, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0f, 16f, 0f, 0f))
		emojiSearchEmptyView.addView(emptyViewText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0f, (16 + 36 + 8).toFloat(), 0f, 0f))
		emojiSearchEmptyView.gone()
		emojiSearchEmptyView.setAlpha(0f)

		gridViewContainer.addView(emojiSearchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_VERTICAL, 0f, 0f, 0f, 0f))

		emojiSearchGridView.setPadding(AndroidUtilities.dp(5f), AndroidUtilities.dp((52 + 2).toFloat()), AndroidUtilities.dp(5f), AndroidUtilities.dp(2f))
		emojiSearchGridView.setAdapter(searchAdapter)

		emojiSearchGridView.setLayoutManager(object : GridLayoutManager(context, 8) {
			override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
				try {
					val linearSmoothScroller: LinearSmoothScrollerCustom = object : LinearSmoothScrollerCustom(recyclerView.context, POSITION_TOP) {
						override fun onEnd() {
							smoothScrolling = false
						}
					}

					linearSmoothScroller.targetPosition = position
					startSmoothScroll(linearSmoothScroller)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}.also { searchLayoutManager = it })

		emojiSearchGridView.setVisibility(GONE)

		gridViewContainer.addView(emojiSearchGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.FILL, 0f, 0f, 0f, 0f))

		contentView.addView(gridViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP, 0f, 36 + 1 / AndroidUtilities.density, 0f, 0f))

		scrollHelper = RecyclerAnimationScrollHelper(emojiGridView, layoutManager)

		scrollHelper.setAnimationCallback(object : RecyclerAnimationScrollHelper.AnimationCallback() {
			override fun onPreAnimation() {
				smoothScrolling = true
			}

			override fun onEndAnimation() {
				smoothScrolling = false
			}
		})

		val onItemLongClick = object : OnItemLongClickListenerExtended {
			override fun onMove(dx: Float, dy: Float) {
				// unused
			}

			override fun onItemClick(view: View, position: Int, x: Float, y: Float): Boolean {
				if (view is ImageViewEmoji && type == TYPE_REACTIONS) {
					incrementHintUse()
					performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

					if (!view.isDefaultReaction && !UserConfig.getInstance(currentAccount).isPremium) {
						var document = view.span?.document

						if (document == null) {
							document = AnimatedEmojiDrawable.findDocument(currentAccount, view.span?.documentId ?: -1)
						}

						onEmojiSelected(view, view.span?.documentId, document, null)

						return true
					}

					selectedReactionView = view
					pressedProgress = 0f
					cancelPressed = false

					if (selectedReactionView?.isDefaultReaction == true) {
						val reaction = MediaDataController.getInstance(currentAccount).reactionsMap[selectedReactionView?.reaction?.emojicon]

						if (reaction != null) {
							bigReactionImageReceiver.setImage(ImageLocation.getForDocument(reaction.selectAnimation), ReactionsUtils.SELECT_ANIMATION_FILTER, null, null, null, 0, "tgs", selectedReactionView?.reaction, 0)
						}
					}
					else {
						setBigReactionAnimatedEmoji(AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, currentAccount, selectedReactionView?.span?.documentId ?: -1))
					}

					emojiGridView?.invalidate()

					return true
				}

				if (view is ImageViewEmoji && view.span != null && type == TYPE_EMOJI_STATUS) {
					selectStatusDateDialog = object : SelectStatusDurationDialog(context, dismiss, this@SelectAnimatedEmojiDialog, view) {
						override fun getOutBounds(rect: Rect): Boolean {
							if (scrimDrawable != null && emojiX != null) {
								rect.set(drawableToBounds)
								return true
							}

							return false
						}

						override fun onEndPartly(date: Int?) {
							incrementHintUse()

							val status = TLEmojiStatus()
							status.documentId = view.span!!.documentId

							onEmojiSelected(view, status.documentId, view.span?.document, date)

							MediaDataController.getInstance(currentAccount).pushRecentEmojiStatus(status)
						}

						override fun onEnd(date: Int?) {
							if (date != null) {
								dismiss?.run()
							}
						}

						override fun dismiss() {
							super.dismiss()
							selectStatusDateDialog = null
						}
					}

					selectStatusDateDialog?.show()

					runCatching {
						view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
					}

					return true
				}

				return false
			}

			override fun onLongClickRelease() {
				if (selectedReactionView != null) {
					cancelPressed = true

					val cancelProgressAnimator = ValueAnimator.ofFloat(pressedProgress, 0f)

					cancelProgressAnimator.addUpdateListener {
						pressedProgress = it.getAnimatedValue() as Float
					}

					cancelProgressAnimator.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							selectedReactionView?.bigReactionSelectedProgress = 0f
							selectedReactionView = null
							emojiGridView?.invalidate()
						}
					})

					cancelProgressAnimator.setDuration(150)
					cancelProgressAnimator.interpolator = CubicBezierInterpolator.DEFAULT
					cancelProgressAnimator.start()
				}
			}
		}

		emojiGridView?.setOnItemLongClickListener(onItemLongClick, (ViewConfiguration.getLongPressTimeout() * 0.25f).toLong())

		emojiSearchGridView.setOnItemLongClickListener(onItemLongClick, (ViewConfiguration.getLongPressTimeout() * 0.25f).toLong())

		val onItemClick = RecyclerListView.OnItemClickListener { view, position ->
			if (view is ImageViewEmoji) {
				if (view.isDefaultReaction) {
					incrementHintUse()
					onReactionClick(view, view.reaction)
				}
				else {
					onEmojiClick(view, view.span)
				}

				if (type != TYPE_REACTIONS) {
					runCatching {
						performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
					}
				}
			}
			else if (view is ImageView) {
				onEmojiClick(view, null)

				if (type != TYPE_REACTIONS) {
					runCatching {
						performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
					}
				}
			}
			else if (view is EmojiPackExpand) {
				expand(position, view)

				if (type != TYPE_REACTIONS) {
					runCatching {
						performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
					}
				}
			}
			else {
				view.callOnClick()
			}
		}

		emojiGridView?.setOnItemClickListener(onItemClick)

		emojiSearchGridView.setOnItemClickListener(onItemClick)

		searchBox.translationY = -AndroidUtilities.dp((4 + 52).toFloat()).toFloat()
		searchBox.invisible()

		gridViewContainer.addView(searchBox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52f, Gravity.TOP, 0f, 0f, 0f, 0f))

		topGradientView = object : View(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
				if (bubbleX != null) {
					pivotX = bubbleX.toFloat()
				}
			}
		}

		val topGradient = ResourcesCompat.getDrawable(resources, R.drawable.gradient_top, null)
		topGradient?.colorFilter = PorterDuffColorFilter(AndroidUtilities.multiplyAlphaComponent(context.getColor(R.color.background), .8f), PorterDuff.Mode.SRC_IN)

		topGradientView.setBackground(topGradient)
		topGradientView.setAlpha(0f)

		contentView.addView(topGradientView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, Gravity.TOP or Gravity.FILL_HORIZONTAL, 0f, 36 + 1f / AndroidUtilities.density, 0f, 0f))

		bottomGradientView = View(context)

		val bottomGradient = ResourcesCompat.getDrawable(resources, R.drawable.gradient_bottom, null)
		bottomGradient?.colorFilter = PorterDuffColorFilter(AndroidUtilities.multiplyAlphaComponent(context.getColor(R.color.background), .8f), PorterDuff.Mode.SRC_IN)

		bottomGradientView.background = bottomGradient
		bottomGradientView.setAlpha(0f)

		contentView.addView(bottomGradientView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL))

		contentViewForeground = View(context)
		contentViewForeground.setAlpha(0f)
		contentViewForeground.setBackgroundColor(-0x1000000)

		contentView.addView(contentViewForeground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		preload(currentAccount)

		bigReactionImageReceiver.setLayerNum(7)

		updateRows(false)
	}

	fun putAnimatedEmojiToCache(animatedEmojiDrawable: AnimatedEmojiDrawable) {
		emojiGridView?.animatedEmojiDrawables?.put(animatedEmojiDrawable.documentId, animatedEmojiDrawable)
	}

	fun setSelectedReactions(selectedReactions: HashSet<VisibleReaction>?) {
		this.selectedReactions = selectedReactions ?: HashSet()

		selectedDocumentIds.clear()

		val arrayList = ArrayList(this.selectedReactions)

		for (i in arrayList.indices) {
			if (arrayList[i].documentId != 0L) {
				selectedDocumentIds.add(arrayList[i].documentId)
			}
		}
	}

	fun setExpireDateHint(date: Int) {
		includeHint = true
		hintExpireDate = date
		updateRows(false)
	}

	private fun setBigReactionAnimatedEmoji(animatedEmojiDrawable: AnimatedEmojiDrawable?) {
		if (!isAttached) {
			return
		}

		if (bigReactionAnimatedEmoji === animatedEmojiDrawable) {
			return
		}

		bigReactionAnimatedEmoji?.removeView(this)
		bigReactionAnimatedEmoji = animatedEmojiDrawable
		bigReactionAnimatedEmoji?.addView(this)
	}

	private fun onRecentLongClick() {
		val builder = AlertDialog.Builder(context)
		builder.setTitle(context.getString(R.string.ClearRecentEmojiStatusesTitle))
		builder.setMessage(context.getString(R.string.ClearRecentEmojiStatusesText))

		builder.setPositiveButton(context.getString(R.string.Clear).uppercase()) { _, _ ->
			ConnectionsManager.getInstance(currentAccount).sendRequest(TLAccountClearRecentEmojiStatuses(), null)
			MediaDataController.getInstance(currentAccount).clearRecentEmojiStatuses()
			updateRows(true)
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)
		builder.setDimEnabled(false)

		builder.setOnDismissListener {
			setDim(0f, true)
		}

		builder.show()

		setDim(1f, true)
	}

	private fun setDim(dim: Float, animated: Boolean) {
		dimAnimator?.cancel()
		dimAnimator = null

		if (animated) {
			dimAnimator = ValueAnimator.ofFloat(contentViewForeground.alpha, dim * maxDim)

			dimAnimator?.addUpdateListener {
				contentViewForeground.setAlpha(it.getAnimatedValue() as Float)
				val bubbleColor = Theme.blendOver(context.getColor(R.color.background), ColorUtils.setAlphaComponent(-0x1000000, (255 * it.getAnimatedValue() as Float).toInt()))

				bubble1View?.background?.colorFilter = PorterDuffColorFilter(bubbleColor, PorterDuff.Mode.MULTIPLY)
				bubble2View?.background?.colorFilter = PorterDuffColorFilter(bubbleColor, PorterDuff.Mode.MULTIPLY)
			}

			dimAnimator?.setDuration(200)
			dimAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
			dimAnimator?.start()
		}
		else {
			contentViewForeground.setAlpha(dim * maxDim)

			val bubbleColor = Theme.blendOver(context.getColor(R.color.background), ColorUtils.setAlphaComponent(-0x1000000, (255 * dim * maxDim).toInt()))

			bubble1View?.background?.colorFilter = PorterDuffColorFilter(bubbleColor, PorterDuff.Mode.MULTIPLY)
			bubble2View?.background?.colorFilter = PorterDuffColorFilter(bubbleColor, PorterDuff.Mode.MULTIPLY)
		}
	}

	private fun updateTabsPosition(position: Int) {
		if (position != RecyclerView.NO_POSITION) {
			val layoutManager = layoutManager ?: return
			val recentMaxLen = layoutManager.spanCount * RECENT_MAX_LINES
			val recentSize = if (recent.size > recentMaxLen && !recentExpanded) recentMaxLen else recent.size + if (includeEmpty) 1 else 0

			if (position <= recentSize || position <= recentReactions.size) {
				emojiTabs?.select(0) // recent
			}
			else {
				val maxLen = layoutManager.spanCount * EXPAND_MAX_LINES

				for (i in 0 until positionToSection.size) {
					val startPosition = positionToSection.keyAt(i)
					val index = i - if (defaultStatuses.isEmpty()) 0 else 1
					val pack = (if (index >= 0) packs[index] else null) ?: continue
					val count = if (pack.expanded) pack.documents.size else min(maxLen.toDouble(), pack.documents.size.toDouble()).toInt()

					if (position > startPosition && position <= startPosition + 1 + count) {
						emojiTabs?.select(1 + i)
						return
					}
				}
			}
		}
	}

	private fun updateSearchBox() {
		if (searched) {
			searchBox.clearAnimation()
			searchBox.visible()
			searchBox.animate().translationY(-AndroidUtilities.dp(4f).toFloat()).start()
		}
		else {
			if (emojiGridView!!.isNotEmpty()) {
				val first = emojiGridView!!.getChildAt(0)

				if (emojiGridView!!.getChildAdapterPosition(first) == searchRow && "searchbox" == first.tag) {
					searchBox.visible()
					searchBox.translationY = first.y - AndroidUtilities.dp(4f)
				}
				else {
					searchBox.translationY = -AndroidUtilities.dp((4 + 52).toFloat()).toFloat()
				}
			}
			else {
				searchBox.translationY = -AndroidUtilities.dp((4 + 52).toFloat()).toFloat()
			}
		}
	}

	private fun getPremiumStar(): Drawable {
		if (premiumStar == null) {
			// premiumStar = PremiumGradient.getInstance().premiumStarMenuDrawableGray;
			premiumStar = ResourcesCompat.getDrawable(resources, R.drawable.msg_settings_premium, null)?.mutate()
			// premiumStar.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider), PorterDuff.Mode.MULTIPLY));
			premiumStar?.colorFilter = premiumStarColorFilter
		}

		return premiumStar!!
	}

	override fun dispatchDraw(canvas: Canvas) {
		val scrimDrawable = scrimDrawable

		if (scrimDrawable != null && emojiX != null) {
			val bounds = scrimDrawable.getBounds()
			val scale = if (scrimDrawableParent == null) 1f else scrimDrawableParent!!.scaleY
			val wasAlpha = scrimDrawable.alpha
			val h = if (scrimDrawableParent == null) bounds.height() else scrimDrawableParent!!.height

			canvas.withTranslation(0f, -translationY) {
				scrimDrawable.alpha = (wasAlpha * contentView.alpha.pow(.25f) * scrimAlpha).toInt()

				drawableToBounds[(bounds.centerX() - bounds.width() / 2f * scale - bounds.centerX() + emojiX + if (scale > 1f && scale < 1.5f) 2 else 0).toInt(), ((h - (h - bounds.bottom)) * scale - (if (scale > 1.5f) bounds.height() * .81f + 1 else 0f) - bounds.top - bounds.height() / 2f + AndroidUtilities.dp(topMarginDp.toFloat()) - bounds.height() * scale).toInt(), (bounds.centerX() + bounds.width() / 2f * scale - bounds.centerX() + emojiX + if (scale > 1f && scale < 1.5f) 2 else 0).toInt()] = ((h - (h - bounds.bottom)) * scale - (if (scale > 1.5f) bounds.height() * .81f + 1 else 0f) - bounds.top - bounds.height() / 2f + AndroidUtilities.dp(topMarginDp.toFloat())).toInt()

				scrimDrawable.setBounds(drawableToBounds.left, drawableToBounds.top, (drawableToBounds.left + drawableToBounds.width() / scale).toInt(), (drawableToBounds.top + drawableToBounds.height() / scale).toInt())

				scale(scale, scale, drawableToBounds.left.toFloat(), drawableToBounds.top.toFloat())

				scrimDrawable.draw(this)
    			scrimDrawable.alpha = wasAlpha
    			scrimDrawable.bounds = bounds
			}
		}

		super.dispatchDraw(canvas)

		if (emojiSelectView != null && emojiSelectRect != null && emojiSelectView?.drawable != null) {
			canvas.withTranslation(0f, -translationY) {
				emojiSelectView?.drawable?.alpha = (255 * emojiSelectAlpha).toInt()
				emojiSelectView?.drawable?.bounds = emojiSelectRect!!
				emojiSelectView?.drawable?.colorFilter = PorterDuffColorFilter(ColorUtils.blendARGB(context.getColor(R.color.brand), scrimColor, 1f - scrimAlpha), PorterDuff.Mode.MULTIPLY)
    			emojiSelectView?.drawable?.draw(this)
			}
		}
	}

	private fun animateEmojiSelect(view: ImageViewEmoji, onDone: Runnable) {
		if (emojiSelectAnimator != null || scrimDrawable == null) {
			onDone.run()
			return
		}

		view.notDraw = true

		val from = Rect()
		from[contentView.left + emojiGridView!!.left + view.left, contentView.top + emojiGridView!!.top + view.top, contentView.left + emojiGridView!!.left + view.right] = contentView.top + emojiGridView!!.top + view.bottom

		val statusDrawable = if (view.drawable is AnimatedEmojiDrawable) AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS, (view.drawable as AnimatedEmojiDrawable?)!!.documentId) else null

		emojiSelectView = view

		emojiSelectRect = Rect()
		emojiSelectRect?.set(from)

		val done = BooleanArray(1)

		emojiSelectAnimator = ValueAnimator.ofFloat(0f, 1f)

		emojiSelectAnimator?.addUpdateListener {
			val t = it.getAnimatedValue() as Float

			scrimAlpha = 1f - t * t * t
			emojiSelectAlpha = 1f - t.pow(10.0f)

			AndroidUtilities.lerp(from, drawableToBounds, t, emojiSelectRect)

			val scale = (max(1.0, overshootInterpolator.getInterpolation(MathUtils.clamp(3 * t - (3 - 1), 0f, 1f)).toDouble()) * view.scaleX).toFloat()

			emojiSelectRect!![(emojiSelectRect!!.centerX() - emojiSelectRect!!.width() / 2f * scale).toInt(), (emojiSelectRect!!.centerY() - emojiSelectRect!!.height() / 2f * scale).toInt(), (emojiSelectRect!!.centerX() + emojiSelectRect!!.width() / 2f * scale).toInt()] = (emojiSelectRect!!.centerY() + emojiSelectRect!!.height() / 2f * scale).toInt()

			invalidate()
			if (t > .85f && !done[0]) {
				done[0] = true

				onDone.run()

				if (statusDrawable != null) {
					scrimDrawable?.play()
				}
			}
		}

		emojiSelectAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				emojiSelectView = null

				invalidate()

				if (!done[0]) {
					done[0] = true
					onDone.run()
				}
			}
		})

		emojiSelectAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
		emojiSelectAnimator?.setDuration(260)
		emojiSelectAnimator?.start()
	}

	private fun checkScroll() {
		val bottom = emojiGridView?.canScrollVertically(1) ?: false

		if (bottom != bottomGradientShown) {
			bottomGradientShown = bottom
			bottomGradientView.animate().alpha(if (bottom) 1f else 0f).setDuration(200).start()
		}
	}

	private fun scrollToPosition(p: Int, offset: Int) {
		val layoutManager = layoutManager ?: return

		val view = layoutManager.findViewByPosition(p)
		val firstPosition = layoutManager.findFirstVisibleItemPosition()

		if (view == null && abs((p - firstPosition).toDouble()) > layoutManager.spanCount * 9f || !SharedConfig.animationsEnabled()) {
			scrollHelper.scrollDirection = if (layoutManager.findFirstVisibleItemPosition() < p) RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN else RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP
			scrollHelper.scrollToPosition(p, offset, false, true)
		}
		else {
			val linearSmoothScroller = object : LinearSmoothScrollerCustom(emojiGridView!!.context, POSITION_TOP) {
				override fun onEnd() {
					smoothScrolling = false
				}

				override fun onStart() {
					smoothScrolling = true
				}
			}

			linearSmoothScroller.targetPosition = p
			linearSmoothScroller.setOffset(offset)

			layoutManager.startSmoothScroll(linearSmoothScroller)
		}
	}

	private fun switchGrids(search: Boolean) {
		if (gridSearch == search) {
			return
		}

		gridSearch = search

		emojiGridView?.setVisibility(VISIBLE)
		emojiSearchGridView.setVisibility(VISIBLE)

		gridSwitchAnimator?.cancel()

		searchEmptyViewAnimator?.cancel()
		searchEmptyViewAnimator = null

		gridSwitchAnimator = ValueAnimator.ofFloat(0f, 1f)

		gridSwitchAnimator?.addUpdateListener {
			var t = it.getAnimatedValue() as Float

			if (!search) {
				t = 1f - t
			}

			emojiGridView?.setAlpha(1f - t)
			emojiSearchGridView.setAlpha(t)
			emojiSearchEmptyView.setAlpha(emojiSearchGridView.alpha * t)
		}

		gridSwitchAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				emojiSearchGridView.setVisibility(if (search) VISIBLE else GONE)
				emojiGridView?.setVisibility(if (search) GONE else VISIBLE)

				gridSwitchAnimator = null

				if (!search && searchResult != null) {
					searchResult?.clear()
					searchAdapter.updateRows(false)
				}
			}
		})

		gridSwitchAnimator?.setDuration(280)
		gridSwitchAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
		gridSwitchAnimator?.start()

		(emojiGridView?.parent as? View)?.animate()?.translationY((if (gridSearch) -AndroidUtilities.dp(36f) else 0).toFloat())?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.setDuration(160)?.start()
	}

	private fun updateSearchEmptyViewImage() {
		if (emojiSearchEmptyViewImageView == null) {
			return
		}

		var emoji: TLRPC.Document? = null
		val featuredSets = MediaDataController.getInstance(currentAccount).featuredEmojiSets
		val shuffledFeaturedSets = ArrayList(featuredSets)

		shuffledFeaturedSets.shuffle()

		for (i in shuffledFeaturedSets.indices) {
			val d = (shuffledFeaturedSets[i] as? TLStickerSetFullCovered)?.documents

			if (shuffledFeaturedSets[i] is TLStickerSetFullCovered && d != null) {
				val documents = ArrayList(d)

				documents.shuffle()

				for (j in documents.indices) {
					val document = documents[j]

					if (document != null && emptyViewEmojis.contains(MessageObject.findAnimatedEmojiEmoticon(document, null))) {
						emoji = document
						break
					}
				}
			}

			if (emoji != null) {
				break
			}
		}

		if (emoji == null) {
			val sets = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_EMOJIPACKS)
			val shuffledSets = ArrayList(sets)

			shuffledSets.shuffle()

			for (i in shuffledSets.indices) {
				val d = shuffledSets[i].documents

				if (shuffledSets[i] != null) {
					val documents = ArrayList(d)

					documents.shuffle()

					for (j in documents.indices) {
						val document = documents[j]

						if (document != null && emptyViewEmojis.contains(MessageObject.findAnimatedEmojiEmoticon(document, null))) {
							emoji = document
							break
						}
					}
				}

				if (emoji != null) {
					break
				}
			}
		}

		if (emoji != null) {
			val document: TLRPC.Document = emoji
			val filter = "36_36"
			val mediaLocation: ImageLocation?
			val mediaFilter: String
			val thumbDrawable = getSvgThumb(document.thumbs, ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), 0.2f)
			val thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)

			if ("video/webm" == document.mimeType) {
				mediaLocation = ImageLocation.getForDocument(document)
				mediaFilter = filter + "_" + ImageLoader.AUTOPLAY_FILTER

				thumbDrawable?.overrideWidthAndHeight(512, 512)
			}
			else {
				if (thumbDrawable != null && MessageObject.isAnimatedStickerDocument(document, false)) {
					thumbDrawable.overrideWidthAndHeight(512, 512)
				}

				mediaLocation = ImageLocation.getForDocument(document)
				mediaFilter = filter
			}

			emojiSearchEmptyViewImageView.setLayerNum(7)
			emojiSearchEmptyViewImageView.setRoundRadius(AndroidUtilities.dp(4f))
			emojiSearchEmptyViewImageView.setImage(mediaLocation, mediaFilter, ImageLocation.getForDocument(thumb, document), "36_36", thumbDrawable, document)
		}
	}

	fun switchSearchEmptyView(empty: Boolean) {
		if (searchEmptyViewVisible == empty) {
			return
		}

		searchEmptyViewVisible = empty

		searchEmptyViewAnimator?.cancel()

		searchEmptyViewAnimator = ValueAnimator.ofFloat(0f, 1f)

		searchEmptyViewAnimator?.addUpdateListener {
			var t = it.getAnimatedValue() as Float
			if (!empty) {
				t = 1f - t
			}
			emojiSearchEmptyView.setAlpha(emojiSearchGridView.alpha * t)
		}

		searchEmptyViewAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				emojiSearchEmptyView.visibility = if (empty && emojiSearchGridView.isVisible) VISIBLE else GONE
				searchEmptyViewAnimator = null
			}
		})

		searchEmptyViewAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
		searchEmptyViewAnimator?.setDuration(100)
		searchEmptyViewAnimator?.start()

		if (empty) {
			updateSearchEmptyViewImage()
		}
	}

	fun search(query: String?) {
		if (clearSearchRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(clearSearchRunnable)
			clearSearchRunnable = null
		}

		if (searchRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(searchRunnable)
			searchRunnable = null
		}

		if (query == null) {
			searching = false
			searched = false

			switchGrids(false)

			searchBox.clearDrawable.stopAnimation()

			searchAdapter.updateRows(true)

			lastQuery = null
		}
		else {
			val firstSearch = !searching

			searching = true
			searched = false

			searchBox.clearDrawable.startAnimation()

			if (firstSearch) {
				searchResult?.clear()
				searchAdapter.updateRows(false)
			}
			else if (query != lastQuery) {
				AndroidUtilities.runOnUIThread(Runnable {
					searchResult?.clear()
					searchAdapter.updateRows(true)
				}.also { clearSearchRunnable = it }, 120)
			}

			lastQuery = query

			val newLanguage = AndroidUtilities.getCurrentKeyboardLanguage()

			if (!newLanguage.contentEquals(lastSearchKeyboardLanguage)) {
				MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(newLanguage)
			}

			lastSearchKeyboardLanguage = newLanguage

			AndroidUtilities.runOnUIThread(Runnable {
				MediaDataController.getInstance(currentAccount).getAnimatedEmojiByKeywords(query) {
					val documentIds = it?.toMutableList() ?: mutableListOf()
					val availableReactions = MediaDataController.getInstance(currentAccount).reactionsMap

					if (fullyConsistsOfEmojis(query)) {
						val stickerSets = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_EMOJIPACKS)
						var emoticon: String?

						for (i in stickerSets.indices) {
							val documents = stickerSets[i].documents

							for (j in documents.indices) {
								emoticon = MessageObject.findAnimatedEmojiEmoticon(documents[j], null)

								val id = documents[j].id

								if (emoticon != null && !documentIds.contains(id) && query.contains(emoticon.lowercase())) {
									documentIds.add(id)
								}
							}
						}

						val featuredStickerSets = MediaDataController.getInstance(currentAccount).featuredEmojiSets

						for (i in featuredStickerSets.indices) {
							if (!(featuredStickerSets[i] as? TLStickerSetFullCovered)?.keywords.isNullOrEmpty()) {
								val documents = (featuredStickerSets[i] as? TLStickerSetFullCovered)?.documents

								if (documents != null) {
									for (j in documents.indices) {
										emoticon = MessageObject.findAnimatedEmojiEmoticon(documents[j], null)

										val id = documents[j].id

										if (emoticon != null && !documentIds.contains(id) && query.contains(emoticon)) {
											documentIds.add(id)
										}
									}
								}
							}
						}

						AndroidUtilities.runOnUIThread {
							if (clearSearchRunnable != null) {
								AndroidUtilities.cancelRunOnUIThread(clearSearchRunnable)
								clearSearchRunnable = null
							}

							if (query !== lastQuery) {
								return@runOnUIThread
							}

							searched = true

							switchGrids(true)

							searchBox.clearDrawable.stopAnimation()

							if (searchResult == null) {
								searchResult = ArrayList()
							}
							else {
								searchResult?.clear()
							}

							emojiSearchGridView.scrollToPosition(0)
							searched = true

							if (type == TYPE_REACTIONS || type == TYPE_SET_DEFAULT_REACTION) {
								val reaction = availableReactions[query]

								if (reaction != null) {
									searchResult?.add(VisibleReaction.fromEmojicon(reaction))
								}
							}

							for (i in documentIds.indices) {
								searchResult?.add(VisibleReaction.fromCustomEmoji(documentIds[i]))
							}

							searchAdapter.updateRows(!firstSearch)
						}
					}
					else {
						MediaDataController.getInstance(currentAccount).getEmojiSuggestions(lastSearchKeyboardLanguage, query, false, { result, _ ->
							if (clearSearchRunnable != null) {
								AndroidUtilities.cancelRunOnUIThread(clearSearchRunnable)
								clearSearchRunnable = null
							}

							if (query !== lastQuery) {
								return@getEmojiSuggestions
							}

							searched = true

							switchGrids(true)

							searchBox.clearDrawable.stopAnimation()

							if (searchResult == null) {
								searchResult = ArrayList()
							}
							else {
								searchResult?.clear()
							}

							if (result != null) {
								for (i in result.indices) {
									try {
										if (result[i].emoji?.startsWith("animated_") == true) {
											documentIds.add(result[i].emoji?.substring(9)?.toLong() ?: 0L)
										}
										else {
											if (type == TYPE_REACTIONS || type == TYPE_SET_DEFAULT_REACTION) {
												val reaction = availableReactions[result[i].emoji]

												if (reaction != null) {
													searchResult?.add(VisibleReaction.fromEmojicon(reaction))
												}
											}
										}
									}
									catch (e: Exception) {
										// ignored
									}
								}
							}

							emojiSearchGridView.scrollToPosition(0)

							searched = true

							for (documentId in documentIds) {
								searchResult?.add(VisibleReaction.fromCustomEmoji(documentId))
							}

							searchAdapter.updateRows(!firstSearch)
						}, null, true, 30)
					}
				}
			}.also { searchRunnable = it }, 425)
		}

		updateSearchBox()

		val showed = searchBox.clear.alpha != 0f

		if (searching != showed) {
			searchBox.clear.animate().alpha(if (searching) 1.0f else 0.0f).setDuration(150).scaleX(if (searching) 1.0f else 0.1f).scaleY(if (searching) 1.0f else 0.1f).start()
		}
	}

	private fun clearRecent() {
		if (type == TYPE_REACTIONS) {
			onRecentClearedListener?.onRecentCleared()
		}
	}

	fun animateExpandDuration(): Long {
		return animateExpandAppearDuration() + animateExpandCrossfadeDuration() + 16
	}

	fun animateExpandAppearDuration(): Long {
		val count = animateExpandToPosition - animateExpandFromPosition
		return max(450.0, (min(55.0, count.toDouble()) * 30L)).toLong()
	}

	private fun animateExpandCrossfadeDuration(): Long {
		val count = animateExpandToPosition - animateExpandFromPosition
		return max(300.0, (min(45.0, count.toDouble()) * 25L)).toLong()
	}

	private fun onEmojiClick(view: View, span: AnimatedEmojiSpan?) {
		incrementHintUse()

		if (span == null) {
			onEmojiSelected(view, null, null, null)
		}
		else {
			val status = TLEmojiStatus()
			status.documentId = span.getDocumentId()

			val document = if (span.document == null) AnimatedEmojiDrawable.findDocument(currentAccount, span.documentId) else span.document

			if (view is ImageViewEmoji) {
				if (type == TYPE_EMOJI_STATUS) {
					MediaDataController.getInstance(currentAccount).pushRecentEmojiStatus(status)
				}

				if (type == TYPE_EMOJI_STATUS || type == TYPE_SET_DEFAULT_REACTION) {
					animateEmojiSelect(view) { onEmojiSelected(view, span.documentId, document, null) }
				}
				else {
					onEmojiSelected(view, span.documentId, document, null)
				}
			}
			else {
				onEmojiSelected(view, span.documentId, document, null)
			}
		}
	}

	private fun incrementHintUse() {
		if (type == TYPE_SET_DEFAULT_REACTION) {
			return
		}

		val key = "emoji" + (if (type == TYPE_EMOJI_STATUS) "status" else "reaction") + "usehint"
		val value = MessagesController.getGlobalMainSettings().getInt(key, 0)

		if (value <= 3) {
			MessagesController.getGlobalMainSettings().edit {putInt(key, value + 1) }
		}
	}

	protected open fun onReactionClick(emoji: ImageViewEmoji?, reaction: VisibleReaction?) {
		// stub
	}

	protected open fun onEmojiSelected(view: View, documentId: Long?, document: TLRPC.Document?, until: Int?) {
		// stub
	}

	private fun updateRows(diff: Boolean) {
		val mediaDataController = MediaDataController.getInstance(UserConfig.selectedAccount)
		val installedEmojiPacks = ArrayList(mediaDataController.getStickerSets(MediaDataController.TYPE_EMOJIPACKS))
		val featuredEmojiPacks = ArrayList(mediaDataController.featuredEmojiSets)
		val prevRowHashCodes = ArrayList(rowHashCodes)

		totalCount = 0
		recentReactionsSectionRow = -1
		recentReactionsStartRow = -1
		recentReactionsEndRow = -1
		popularSectionRow = -1
		longtapHintRow = -1
		recent.clear()
		defaultStatuses.clear()
		topReactions.clear()
		recentReactions.clear()
		packs.clear()
		positionToSection.clear()
		sectionToPosition.clear()
		positionToExpand.clear()
		rowHashCodes.clear()
		positionToButton.clear()

		searchRow = if (installedEmojiPacks.isNotEmpty()) {
			totalCount++
		}
		else {
			-1
		}

		// MARK: uncomment to put back the hint
//		if (includeHint && type != TYPE_SET_DEFAULT_REACTION) {
//			longtapHintRow = totalCount++
//			rowHashCodes.add(6)
//		}

		if (recentReactionsToSet != null) {
			topReactionsStartRow = totalCount

			val tmp = ArrayList<VisibleReaction>()
			tmp.addAll(recentReactionsToSet!!)

			for (i in 0..15) {
				if (tmp.isNotEmpty()) {
					topReactions.add(tmp.removeAt(0))
				}
			}
			for (i in topReactions.indices) {
				rowHashCodes.add(Objects.hash(-5632, topReactions[i].hashCode()))
			}

			totalCount += topReactions.size
			topReactionsEndRow = totalCount

			if (tmp.isNotEmpty()) {
				var allRecentReactionsIsDefault = true

				for (i in tmp.indices) {
					if (tmp[i].documentId != 0L) {
						allRecentReactionsIsDefault = false
						break
					}
				}

				if (allRecentReactionsIsDefault) {
					if (UserConfig.getInstance(currentAccount).isPremium) {
						popularSectionRow = totalCount++
						rowHashCodes.add(5)
					}
				}
				else {
					recentReactionsSectionRow = totalCount++
					rowHashCodes.add(4)
				}

				recentReactionsStartRow = totalCount

				recentReactions.addAll(tmp)

				for (i in recentReactions.indices) {
					rowHashCodes.add(Objects.hash(if (allRecentReactionsIsDefault) 4235 else -3142, recentReactions[i].hashCode()))
				}

				totalCount += recentReactions.size
				recentReactionsEndRow = totalCount
			}
		}
		else if (type == TYPE_EMOJI_STATUS) {
			val recentEmojiStatuses = MediaDataController.getInstance(currentAccount).recentEmojiStatuses
			val defaultSet = MediaDataController.getInstance(currentAccount).getStickerSet(TLInputStickerSetEmojiDefaultStatuses(), false)

			if (defaultSet == null) {
				defaultSetLoading = true
			}
			else {
				if (includeEmpty) {
					totalCount++
					rowHashCodes.add(2)
				}

				val defaultEmojiStatuses = MediaDataController.getInstance(currentAccount).defaultEmojiStatuses
				val maxRecentLen = layoutManager!!.spanCount * (RECENT_MAX_LINES + 8)

				if (defaultSet.documents.isNotEmpty()) {
					for (i in 0 until min((layoutManager!!.spanCount - 1).toDouble(), defaultSet.documents.size.toDouble()).toInt()) {
						recent.add(AnimatedEmojiSpan(defaultSet.documents[i], null))

						if (recent.size + (if (includeEmpty) 1 else 0) >= maxRecentLen) {
							break
						}
					}
				}

				if (!recentEmojiStatuses.isNullOrEmpty()) {
					for (emojiStatus in recentEmojiStatuses) {
						val did = if (emojiStatus is TLEmojiStatus) {
							emojiStatus.documentId
						}
						else if (emojiStatus is TLEmojiStatusUntil && emojiStatus.until > (System.currentTimeMillis() / 1000).toInt()) {
							emojiStatus.documentId
						}
						else {
							continue
						}

						var foundDuplicate = false

						for (i in recent.indices) {
							if (recent[i].getDocumentId() == did) {
								foundDuplicate = true
								break
							}
						}

						if (foundDuplicate) {
							continue
						}

						recent.add(AnimatedEmojiSpan(did, null))

						if (recent.size + (if (includeEmpty) 1 else 0) >= maxRecentLen) {
							break
						}
					}
				}

				if (!defaultEmojiStatuses.isNullOrEmpty()) {
					for (emojiStatus in defaultEmojiStatuses) {
						val did = if (emojiStatus is TLEmojiStatus) {
							emojiStatus.documentId
						}
						else if (emojiStatus is TLEmojiStatusUntil && emojiStatus.until > (System.currentTimeMillis() / 1000).toInt()) {
							emojiStatus.documentId
						}
						else {
							continue
						}

						var foundDuplicate = false

						for (i in recent.indices) {
							if (recent[i].getDocumentId() == did) {
								foundDuplicate = true
								break
							}
						}

						if (!foundDuplicate) {
							recent.add(AnimatedEmojiSpan(did, null))

							if (recent.size + (if (includeEmpty) 1 else 0) >= maxRecentLen) {
								break
							}
						}
					}
				}

				val maxLen = layoutManager!!.spanCount * RECENT_MAX_LINES
				val len = maxLen - if (includeEmpty) 1 else 0

				if (recent.size > len && !recentExpanded) {
					for (i in 0 until len - 1) {
						rowHashCodes.add(Objects.hash(43223, recent[i].getDocumentId()))
						totalCount++
					}

					rowHashCodes.add(Objects.hash(-5531, -1, recent.size - maxLen + (if (includeEmpty) 1 else 0) + 1))

					recentExpandButton?.textView?.text = "+" + (recent.size - maxLen + (if (includeEmpty) 1 else 0) + 1)

					positionToExpand.put(totalCount, -1)
					totalCount++
				}
				else {
					for (i in recent.indices) {
						rowHashCodes.add(Objects.hash(43223, recent[i].getDocumentId()))
						totalCount++
					}
				}
			}
		}

		var i = 0
		var j = 0

		while (i < installedEmojiPacks.size) {
			val set = installedEmojiPacks[i]

			if (set?.set != null && set.set!!.emojis && !installedEmojiSets.contains(set.set!!.id)) {
				positionToSection.put(totalCount, packs.size)
				sectionToPosition.put(packs.size, totalCount)

				totalCount++

				rowHashCodes.add(Objects.hash(9211, set.set!!.id))

				val pack = EmojiPack()
				pack.installed = true
				pack.featured = false
				pack.expanded = true
				pack.free = !MessageObject.isPremiumEmojiPack(set)
				pack.set = set.set
				pack.documents = set.documents
				pack.index = packs.size

				packs.add(pack)

				totalCount += pack.documents.size

				for (k in pack.documents.indices) {
					rowHashCodes.add(Objects.hash(3212, pack.documents[k].id))
				}

				j++
			}

			++i
		}

		val maxlen = layoutManager!!.spanCount * EXPAND_MAX_LINES

		for (i in featuredEmojiPacks.indices) {
			val set1 = featuredEmojiPacks[i]

			if (set1 is TLStickerSetFullCovered) {
				var foundDuplicate = false

				for (j in packs.indices) {
					if (packs[j].set.id == set1.set?.id) {
						foundDuplicate = true
						break
					}
				}

				if (foundDuplicate) {
					continue
				}

				positionToSection.put(totalCount, packs.size)
				sectionToPosition.put(packs.size, totalCount)

				totalCount++

				rowHashCodes.add(Objects.hash(9211, set1.set?.id))

				val pack = EmojiPack()
				pack.installed = installedEmojiSets.contains(set1.set?.id)
				pack.featured = true
				pack.free = !MessageObject.isPremiumEmojiPack(set1)
				pack.set = set1.set
				pack.documents = set1.documents
				pack.index = packs.size
				pack.expanded = expandedEmojiSets.contains(pack.set.id)

				if (pack.documents.size > maxlen && !pack.expanded) {
					totalCount += maxlen

					for (k in 0 until maxlen - 1) {
						rowHashCodes.add(Objects.hash(3212, pack.documents[k].id))
					}

					rowHashCodes.add(Objects.hash(-5531, set1.set?.id, pack.documents.size - maxlen + 1))
					positionToExpand.put(totalCount - 1, packs.size)
				}
				else {
					totalCount += pack.documents.size

					for (k in pack.documents.indices) {
						rowHashCodes.add(Objects.hash(3212, pack.documents[k].id))
					}
				}

				if (!pack.installed) {
					positionToButton.put(totalCount, packs.size)
					totalCount++
					rowHashCodes.add(Objects.hash(3321, set1.set?.id))
				}

				packs.add(pack)
			}
		}

		post {
			emojiTabs?.updateEmojiPacks(packs)
		}

		if (diff) {
			DiffUtil.calculateDiff(object : DiffUtil.Callback() {
				override fun getOldListSize(): Int {
					return prevRowHashCodes.size
				}

				override fun getNewListSize(): Int {
					return rowHashCodes.size
				}

				override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
					return prevRowHashCodes[oldItemPosition] == rowHashCodes[newItemPosition]
				}

				override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
					return true
				}
			}, false).dispatchUpdatesTo(adapter)
		}
		else {
			adapter.notifyDataSetChanged()
		}

		if (!emojiGridView!!.scrolledByUserOnce) {
			emojiGridView!!.scrollToPosition(1)
		}
	}

	fun expand(position: Int, expandButton: View?) {
		val index = positionToExpand[position]
		var from: Int? = null
		var count: Int? = null
		val last: Boolean
		val maxLen: Int
		val fromCount: Int
		val start: Int
		val toCount: Int

		animateExpandFromButtonTranslate = 0f

		if (index >= 0 && index < packs.size) {
			maxLen = layoutManager!!.spanCount * EXPAND_MAX_LINES

			val pack = packs[index]

			if (pack.expanded) {
				return
			}

			last = index + 1 == packs.size
			start = sectionToPosition[index]

			expandedEmojiSets.add(pack.set.id)

			fromCount = if (pack.expanded) pack.documents.size else min(maxLen.toDouble(), pack.documents.size.toDouble()).toInt()

			if (pack.documents.size > maxLen) {
				from = start + 1 + fromCount
			}

			pack.expanded = true
			toCount = pack.documents.size
		}
		else if (index == -1) {
			maxLen = layoutManager!!.spanCount * RECENT_MAX_LINES

			if (recentExpanded) {
				return
			}

			last = false
			start = (if (searchRow != -1) 1 else 0) + (if (includeHint) 1 else 0) + if (includeEmpty) 1 else 0
			fromCount = if (recentExpanded) recent.size else min((maxLen - (if (includeEmpty) 1 else 0) - 2).toDouble(), recent.size.toDouble()).toInt()
			toCount = recent.size
			recentExpanded = true
			animateExpandFromButtonTranslate = AndroidUtilities.dp(8f).toFloat()
		}
		else {
			return
		}

		if (toCount > fromCount) {
			from = start + 1 + fromCount
			count = toCount - fromCount
		}

		updateRows(true)

		if (from != null && count != null) {
			animateExpandFromButton = expandButton
			animateExpandFromPosition = from
			animateExpandToPosition = from + count
			animateExpandStartTime = SystemClock.elapsedRealtime()

			if (last) {
				val scrollTo: Int = from
				val durationMultiplier = if (count > maxLen / 2) 1.5f else 3.5f

				post {
					try {
						val linearSmoothScroller = LinearSmoothScrollerCustom(emojiGridView!!.context, LinearSmoothScrollerCustom.POSITION_MIDDLE, durationMultiplier)
						linearSmoothScroller.targetPosition = scrollTo
						layoutManager?.startSmoothScroll(linearSmoothScroller)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (drawBackground) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(min(AndroidUtilities.dp((340 - 16).toFloat()).toDouble(), (AndroidUtilities.displaySize.x * .95f).toDouble()).toInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(min(AndroidUtilities.dp((410 - 16 - 64).toFloat()).toDouble(), (AndroidUtilities.displaySize.y * .75f).toDouble()).toInt(), MeasureSpec.AT_MOST))
		}
		else {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
	}

	private val cacheType: Int
		get() = if (type == TYPE_EMOJI_STATUS || type == TYPE_SET_DEFAULT_REACTION) AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD else AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		isAttached = true

		NotificationCenter.getInstance(currentAccount).let {
			it.addObserver(this, NotificationCenter.featuredEmojiDidLoad)
			it.addObserver(this, NotificationCenter.stickersDidLoad)
			it.addObserver(this, NotificationCenter.recentEmojiStatusesUpdate)
			it.addObserver(this, NotificationCenter.groupStickersDidLoad)
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()

		setBigReactionAnimatedEmoji(null)

		isAttached = false

		NotificationCenter.getInstance(currentAccount).let {
			it.removeObserver(this, NotificationCenter.featuredEmojiDidLoad)
			it.removeObserver(this, NotificationCenter.stickersDidLoad)
			it.removeObserver(this, NotificationCenter.recentEmojiStatusesUpdate)
			it.removeObserver(this, NotificationCenter.groupStickersDidLoad)
		}

		scrimDrawable?.removeParentView(this)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.stickersDidLoad -> {
				if (args[0] as Int == MediaDataController.TYPE_EMOJIPACKS) {
					updateRows(true)
				}
			}

			NotificationCenter.featuredEmojiDidLoad -> {
				updateRows(true)
			}

			NotificationCenter.recentEmojiStatusesUpdate -> {
				updateRows(true)
			}

			NotificationCenter.groupStickersDidLoad -> {
				if (defaultSetLoading) {
					updateRows(true)
					defaultSetLoading = false
				}
			}
		}
	}

	fun onShow(dismiss: Runnable?) {
		this.dismiss = dismiss

		if (!drawBackground) {
			checkScroll()

			for (i in 0 until emojiGridView!!.childCount) {
				val child = emojiGridView!!.getChildAt(i)
				child.scaleX = 1f
				child.scaleY = 1f
			}

			return
		}

		showAnimator?.cancel()
		showAnimator = null

		hideAnimator?.cancel()
		hideAnimator = null

		showAnimator = ValueAnimator.ofFloat(0f, 1f)

		showAnimator?.addUpdateListener {
			val t = it.getAnimatedValue() as Float
			updateShow(t)
		}

		showAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				checkScroll()
				updateShow(1f)

				for (i in 0 until emojiGridView!!.childCount) {
					val child = emojiGridView!!.getChildAt(i)
					child.scaleX = 1f
					child.scaleY = 1f
				}

				for (i in 0 until emojiTabs!!.contentView.childCount) {
					val child = emojiTabs!!.contentView.getChildAt(i)
					child.scaleX = 1f
					child.scaleY = 1f
				}

				emojiTabs!!.contentView.invalidate()

				emojiGridView!!.updateEmojiDrawables()
			}
		})

		updateShow(0f)

		showAnimator?.setDuration(showDuration)
		showAnimator?.start()
	}

	protected open fun onInputFocus() {
		// stub
	}

	private fun updateShow(t: Float) {
		if (bubble1View != null) {
			var bubble1t = MathUtils.clamp((t * showDuration - 0) / 120 / durationScale, 0f, 1f)
			bubble1t = CubicBezierInterpolator.EASE_OUT.getInterpolation(bubble1t)

			bubble1View?.setAlpha(bubble1t)
			bubble1View?.scaleX = bubble1t
			bubble1View?.scaleY = bubble1t
		}

		if (bubble2View != null) {
			val bubble2t = MathUtils.clamp((t * showDuration - 30) / 120 / durationScale, 0f, 1f)
			// bubble2t = CubicBezierInterpolator.EASE_OUT.getInterpolation(bubble2t);

			bubble2View?.setAlpha(bubble2t)
			bubble2View?.scaleX = bubble2t
			bubble2View?.scaleY = bubble2t
		}

		var containerx = MathUtils.clamp((t * showDuration - 40) / 700, 0f, 1f)
		var containery = MathUtils.clamp((t * showDuration - 80) / 700, 0f, 1f)
		val containeritemst = MathUtils.clamp((t * showDuration - 40) / 750, 0f, 1f)
		val containeralphat = MathUtils.clamp((t * showDuration - 30) / 120, 0f, 1f)

		containerx = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(containerx)
		containery = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(containery)
		// containeritemst = endslow.getInterpolation(containeritemst);
		// containeralphat = CubicBezierInterpolator.EASE_OUT.getInterpolation(containeralphat);

		contentView.setAlpha(containeralphat)

		if (scrimDrawable != null) {
			invalidate()
		}

		contentView.translationY = AndroidUtilities.dp(-5f) * (1f - containeralphat)

		bubble2View?.translationY = AndroidUtilities.dp(-5f) * (1f - containeralphat)

		scaleX = .15f + .85f * containerx
		scaleY = .075f + .925f * containery

		bubble2View?.setAlpha(containeralphat)

		contentView.invalidate()

		emojiTabsShadow.setAlpha(containeralphat)
		emojiTabsShadow.scaleX = min(scaleX.toDouble(), 1.0).toFloat()

		val px = emojiTabsShadow.pivotX
		val fullr = sqrt(max(px * px + contentView.height.toFloat().pow(2.0f), (contentView.width - px).pow(2.0f) + contentView.height.toFloat().pow(2.0f)))

		for (i in 0 until emojiTabs!!.contentView.childCount) {
			val child = emojiTabs!!.contentView.getChildAt(i)
			val ccx = child.left + child.width / 2f
			val ccy = child.top + child.height / 2f
			val distance = sqrt(((ccx - px) * (ccx - px) + ccy * ccy * .4f).toDouble()).toFloat()
			var scale = AndroidUtilities.cascade(containeritemst, distance, fullr, child.height * 1.75f)

			if (java.lang.Float.isNaN(scale)) {
				scale = 0f
			}

			child.scaleX = scale
			child.scaleY = scale
		}

		emojiTabs?.contentView?.invalidate()

		for (i in 0 until emojiGridView!!.childCount) {
			val child = emojiGridView!!.getChildAt(i)
			val cx = child.left + child.width / 2f
			val cy = child.top + child.height / 2f
			val distance = sqrt(((cx - px) * (cx - px) + cy * cy * .2f).toDouble()).toFloat()
			var scale = AndroidUtilities.cascade(containeritemst, distance, fullr, child.height * 1.75f)

			if (java.lang.Float.isNaN(scale)) {
				scale = 0f
			}

			child.scaleX = scale
			child.scaleY = scale
		}

		emojiGridView?.invalidate()
	}

	fun onDismiss(dismiss: Runnable) {
		hideAnimator?.cancel()

		hideAnimator = ValueAnimator.ofFloat(0f, 1f)

		hideAnimator?.addUpdateListener {
			val t = 1f - it.getAnimatedValue() as Float

			translationY = AndroidUtilities.dp(8f) * (1f - t)

			bubble1View?.setAlpha(t)
			bubble2View?.setAlpha(t * t)

			contentView.setAlpha(t)
			contentView.invalidate()

			invalidate()
		}

		hideAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				dismiss.run()

				selectStatusDateDialog?.dismiss()
				selectStatusDateDialog = null
			}
		})

		hideAnimator?.setDuration(200)
		hideAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
		hideAnimator?.start()

		AndroidUtilities.hideKeyboard(searchBox.input)
	}

	fun setDrawBackground(drawBackground: Boolean) {
		this.drawBackground = drawBackground
	}

	fun setRecentReactions(reactions: List<VisibleReaction>?) {
		recentReactionsToSet = reactions
		updateRows(true)
	}

	fun resetBackgroundBitmaps() {
		for (i in emojiGridView!!.lineDrawables.indices) {
			val line = emojiGridView!!.lineDrawables[i]

			for (j in line!!.imageViewEmojis!!.indices) {
				if (line.imageViewEmojis!![j].notDraw) {
					line.imageViewEmojis!![j].notDraw = false
					line.imageViewEmojis!![j].invalidate()
					line.reset()
				}
			}
		}

		emojiGridView!!.invalidate()

		for (i in emojiSearchGridView.lineDrawables.indices) {
			val line = emojiSearchGridView.lineDrawables[i]

			for (j in line!!.imageViewEmojis!!.indices) {
				if (line.imageViewEmojis!![j].notDraw) {
					line.imageViewEmojis!![j].notDraw = false
					line.imageViewEmojis!![j].invalidate()
					line.reset()
				}
			}
		}

		emojiSearchGridView.invalidate()
	}

	fun setSelected(documentId: Long?) {
		selectedDocumentIds.clear()

		if (documentId != null) {
			selectedDocumentIds.add(documentId)
		}
	}

	fun setScrimDrawable(scrimDrawable: SwapAnimatedEmojiDrawable?, drawableParent: View?) {
		scrimColor = if (scrimDrawable == null) 0 else scrimDrawable.color
		this.scrimDrawable = scrimDrawable
		scrimDrawableParent = drawableParent
		scrimDrawable?.addParentView(this)
		invalidate()
	}

	fun drawBigReaction(canvas: Canvas, view: View) {
		if (selectedReactionView == null) {
			return
		}

		bigReactionImageReceiver.setParentView(view)

		if (selectedReactionView != null) {
			if (pressedProgress != 1f && !cancelPressed) {
				pressedProgress += 16f / 1500f

				if (pressedProgress >= 1f) {
					pressedProgress = 1f
					bigReactionListener?.onLongPressed(selectedReactionView!!)
				}

				selectedReactionView?.bigReactionSelectedProgress = pressedProgress
			}

			val pressedViewScale = 1 + 2 * pressedProgress

			canvas.withTranslation(emojiGridView!!.x + selectedReactionView!!.x, emojiGridView!!.y + selectedReactionView!!.y) {
				paint.setColor(context.getColor(R.color.background))

				drawRect(0f, 0f, selectedReactionView!!.measuredWidth.toFloat(), selectedReactionView!!.measuredHeight.toFloat(), paint)
				scale(pressedViewScale, pressedViewScale, selectedReactionView!!.measuredWidth / 2f, selectedReactionView!!.measuredHeight.toFloat())

				var imageReceiver = if (selectedReactionView!!.isDefaultReaction) bigReactionImageReceiver else selectedReactionView!!.imageReceiverToDraw

				if (bigReactionAnimatedEmoji != null && bigReactionAnimatedEmoji!!.imageReceiver != null && bigReactionAnimatedEmoji!!.imageReceiver.hasBitmapImage()) {
					imageReceiver = bigReactionAnimatedEmoji!!.imageReceiver
				}

				if (imageReceiver != null) {
					imageReceiver.setImageCoordinates(0f, 0f, selectedReactionView!!.measuredWidth.toFloat(), selectedReactionView!!.measuredHeight.toFloat())
					imageReceiver.draw(this)
				}
			}

			view.invalidate()
		}
	}

	fun setSaveState(saveId: Int) {
		listStateId = saveId
	}

	fun setOnLongPressedListener(l: OnLongPressedListener?) {
		bigReactionListener = l
	}

	fun setOnRecentClearedListener(onRecentClearedListener: OnRecentClearedListener?) {
		this.onRecentClearedListener = onRecentClearedListener
	}

	fun interface OnLongPressedListener {
		fun onLongPressed(view: ImageViewEmoji)
	}

	fun interface OnRecentClearedListener {
		fun onRecentCleared()
	}

	open class SelectAnimatedEmojiDialogWindow : PopupWindow {
		private var mSuperScrollListener: OnScrollChangedListener? = null
		private var mViewTreeObserver: ViewTreeObserver? = null

		constructor(anchor: View?) : super(anchor) {
			init()
		}

		constructor(anchor: View?, width: Int, height: Int) : super(anchor, width, height) {
			init()
		}

		private fun init() {
			isFocusable = true
			animationStyle = 0
			isOutsideTouchable = true
			isClippingEnabled = true
			inputMethodMode = INPUT_METHOD_FROM_FOCUSABLE
			softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE

			if (superListenerField != null) {
				try {
					mSuperScrollListener = superListenerField[this] as OnScrollChangedListener
					superListenerField[this] = NOP
				}
				catch (e: Exception) {
					mSuperScrollListener = null
				}
			}
		}

		private fun unregisterListener() {
			if (mSuperScrollListener != null && mViewTreeObserver != null) {
				if (mViewTreeObserver?.isAlive == true) {
					mViewTreeObserver?.removeOnScrollChangedListener(mSuperScrollListener)
				}

				mViewTreeObserver = null
			}
		}

		private fun registerListener(anchor: View) {
			(contentView as? SelectAnimatedEmojiDialog)?.onShow { dismiss() }

			if (mSuperScrollListener != null) {
				val vto = if (anchor.windowToken != null) anchor.getViewTreeObserver() else null

				if (vto != mViewTreeObserver) {
					if (mViewTreeObserver?.isAlive == true) {
						mViewTreeObserver?.removeOnScrollChangedListener(mSuperScrollListener)
					}

					if (vto.also { mViewTreeObserver = it } != null) {
						vto?.addOnScrollChangedListener(mSuperScrollListener)
					}
				}
			}
		}

		fun dimBehind() {
			val container = contentView.getRootView()
			val context = contentView.context
			val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

			val p = container.layoutParams as WindowManager.LayoutParams
			p.flags = p.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
			p.dimAmount = 0.2f

			wm.updateViewLayout(container, p)
		}

		private fun dismissDim() {
			val container = contentView.getRootView()
			val context = contentView.context
			val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

			if (container.layoutParams == null || container.layoutParams !is WindowManager.LayoutParams) {
				return
			}

			val p = container.layoutParams as WindowManager.LayoutParams

			try {
				if (p.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
					p.flags = p.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
					p.dimAmount = 0.0f
					wm.updateViewLayout(container, p)
				}
			}
			catch (e: Exception) {
				// ignored
			}
		}

		override fun showAsDropDown(anchor: View) {
			super.showAsDropDown(anchor)
			registerListener(anchor)
		}

		override fun showAsDropDown(anchor: View, xoff: Int, yoff: Int) {
			super.showAsDropDown(anchor, xoff, yoff)
			registerListener(anchor)
		}

		override fun showAsDropDown(anchor: View, xoff: Int, yoff: Int, gravity: Int) {
			super.showAsDropDown(anchor, xoff, yoff, gravity)
			registerListener(anchor)
		}

		override fun showAtLocation(parent: View, gravity: Int, x: Int, y: Int) {
			super.showAtLocation(parent, gravity, x, y)
			unregisterListener()
		}

		override fun dismiss() {
			if (contentView is SelectAnimatedEmojiDialog) {
				(contentView as SelectAnimatedEmojiDialog).onDismiss { super.dismiss() }
				dismissDim()
			}
			else {
				super.dismiss()
			}
		}

		@SuppressLint("SoonBlockedPrivateApi")
		companion object {
			private val superListenerField: Field?
			private val NOP = OnScrollChangedListener {}

			init {
				var f: Field? = null

				runCatching {
					f = PopupWindow::class.java.getDeclaredField("mOnScrollChangedListener")
					f?.isAccessible = true
				}

				superListenerField = f
			}
		}
	}

	class EmojiPackExpand(context: Context) : FrameLayout(context) {
		val textView = TextView(context)

		init {
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
			textView.setTextColor(-0x1)
			textView.background = Theme.createRoundRectDrawable(AndroidUtilities.dp(11f), ColorUtils.setAlphaComponent(context.getColor(R.color.dark_gray), 99))
			textView.setTypeface(Theme.TYPEFACE_BOLD)
			textView.setPadding(AndroidUtilities.dp(4f), AndroidUtilities.dp(1.66f), AndroidUtilities.dp(4f), AndroidUtilities.dp(2f))
			addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))
		}
	}

	private inner class SearchAdapter : SelectionAdapter() {
		private val rowHashCodes = ArrayList<Int>()
		var VIEW_TYPE_SEARCH = 7
		var VIEW_TYPE_EMOJI = 3
		var VIEW_TYPE_REACTION = 4
		private var count = 1

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType == VIEW_TYPE_EMOJI || holder.itemViewType == VIEW_TYPE_REACTION
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			if (viewType == VIEW_TYPE_SEARCH) {
				view = object : View(context) {
					override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
						super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(52f), MeasureSpec.EXACTLY))
					}
				}

				view.setTag("searchbox")
			}
			else {
				view = ImageViewEmoji(context)
			}

			if (showAnimator != null && showAnimator!!.isRunning) {
				view.scaleX = 0f
				view.scaleY = 0f
			}

			return RecyclerListView.Holder(view)
		}

		override fun getItemViewType(position: Int): Int {
			return if (searchResult == null || position < 0 || position >= searchResult!!.size || searchResult!![position].emojicon == null) {
				VIEW_TYPE_EMOJI
			}
			else {
				VIEW_TYPE_REACTION
			}
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			if (holder.itemViewType == VIEW_TYPE_REACTION) {
				val imageView = holder.itemView as ImageViewEmoji
				imageView.position = position

				if (searchResult == null || position < 0 || position >= searchResult!!.size) {
					return
				}

				val currentReaction = searchResult!![position]

				if (imageView.imageReceiver == null) {
					imageView.imageReceiver = ImageReceiver()
					imageView.imageReceiver!!.setLayerNum(7)
					imageView.imageReceiver!!.onAttachedToWindow()
				}

				imageView.imageReceiver!!.setParentView(emojiSearchGridView)
				imageView.reaction = currentReaction
				imageView.setViewSelected(selectedReactions.contains(currentReaction))

				if (currentReaction.emojicon != null) {
					imageView.isDefaultReaction = true

					var fallbackToEmoji = true
					val reaction = MediaDataController.getInstance(currentAccount).reactionsMap[currentReaction.emojicon]

					if (reaction != null) {
						val svgThumb = getSvgThumb(reaction.activateAnimation, ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), 0.2f)

						if (svgThumb != null) {
							val image = ImageLocation.getForDocument(reaction.selectAnimation)

							if (image != null) {
								fallbackToEmoji = false
								imageView.imageReceiver?.setImage(image, ReactionsUtils.SELECT_ANIMATION_FILTER, null, null, svgThumb, 0, "tgs", currentReaction, 0)
							}
						}
					}

					if (fallbackToEmoji) {
						val bitmap = Emoji.getEmojiDrawableForEmojicon(currentReaction.emojicon)

						if (bitmap != null) {
							imageView.imageReceiver?.setImageBitmap(bitmap)
						}
						else {
							imageView.imageReceiver?.clearImage()
						}
					}

					imageView.span = null
					imageView.document = null
					imageView.drawable = null
					imageView.premiumLockIconView?.setVisibility(GONE)
					imageView.premiumLockIconView?.setImageReceiver(null)
				}
				else {
					imageView.isDefaultReaction = false
					imageView.span = AnimatedEmojiSpan(currentReaction.documentId, null)
					imageView.document = null
					imageView.imageReceiver?.clearImage()

					var drawable = emojiSearchGridView.animatedEmojiDrawables!![imageView.span!!.getDocumentId()]

					if (drawable == null) {
						drawable = AnimatedEmojiDrawable.make(currentAccount, cacheType, imageView.span!!.getDocumentId())
						drawable.addView(emojiSearchGridView)
						emojiSearchGridView.animatedEmojiDrawables!!.put(imageView.span!!.getDocumentId(), drawable)
					}

					imageView.drawable = drawable

					if (!UserConfig.getInstance(currentAccount).isPremium) {
						if (imageView.premiumLockIconView == null) {
							imageView.premiumLockIconView = PremiumLockIconView(context, PremiumLockIconView.TYPE_STICKERS_PREMIUM_LOCKED)

							imageView.addView(imageView.premiumLockIconView, LayoutHelper.createFrame(12, 12, Gravity.RIGHT or Gravity.BOTTOM))
						}

						imageView.premiumLockIconView?.visible()
					}
				}
			}
			else if (holder.itemViewType == VIEW_TYPE_EMOJI) {
				val imageView = holder.itemView as ImageViewEmoji
				imageView.empty = false
				imageView.position = position
				imageView.setPadding(AndroidUtilities.dp(1f), AndroidUtilities.dp(1f), AndroidUtilities.dp(1f), AndroidUtilities.dp(1f))

				var selected = false

				imageView.drawable = null

				if (searchResult != null && position >= 0 && position < searchResult!!.size) {
					val documentId = searchResult!![position].documentId

					imageView.span = AnimatedEmojiSpan(documentId, null)
					imageView.document = imageView.span!!.document

					selected = selectedDocumentIds.contains(documentId)

					var drawable = emojiSearchGridView.animatedEmojiDrawables!![imageView.span!!.getDocumentId()]

					if (drawable == null) {
						drawable = AnimatedEmojiDrawable.make(currentAccount, cacheType, imageView.span!!.getDocumentId())
						drawable.addView(emojiSearchGridView)

						emojiSearchGridView.animatedEmojiDrawables!!.put(imageView.span!!.getDocumentId(), drawable)
					}

					imageView.drawable = drawable
				}

				imageView.setViewSelected(selected)
			}
		}

		override fun getItemCount(): Int {
			return count
		}

		fun updateRows(diff: Boolean) {
			val prevRowHashCodes = ArrayList(rowHashCodes)

			count = 0
			rowHashCodes.clear()

			searchResult?.let {
				for (i in it.indices) {
					count++
					rowHashCodes.add(Objects.hash(-4342, it[i]))
				}
			}

			if (diff) {
				DiffUtil.calculateDiff(object : DiffUtil.Callback() {
					override fun getOldListSize(): Int {
						return prevRowHashCodes.size
					}

					override fun getNewListSize(): Int {
						return rowHashCodes.size
					}

					override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
						return prevRowHashCodes[oldItemPosition] == rowHashCodes[newItemPosition]
					}

					override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
						return true
					}
				}, false).dispatchUpdatesTo(this@SearchAdapter)
			}
			else {
				notifyDataSetChanged()
			}

			switchSearchEmptyView(searched && count == 0)
		}
	}

	private inner class Adapter : SelectionAdapter() {
		private val VIEW_TYPE_HEADER = 0
		private val VIEW_TYPE_REACTION = 1
		private val VIEW_TYPE_IMAGE = 2
		private val VIEW_TYPE_EMOJI = 3
		private val VIEW_TYPE_EXPAND = 4
		private val VIEW_TYPE_BUTTON = 5
		private val VIEW_TYPE_HINT = 6
		private val VIEW_TYPE_SEARCH = 7

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val viewType = holder.itemViewType
			return viewType == VIEW_TYPE_IMAGE || viewType == VIEW_TYPE_REACTION || viewType == VIEW_TYPE_EMOJI
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				VIEW_TYPE_HEADER -> {
					view = HeaderView(context)
				}

				VIEW_TYPE_IMAGE -> {
					view = ImageView(context)
				}

				VIEW_TYPE_EMOJI, VIEW_TYPE_REACTION -> {
					view = ImageViewEmoji(context)
				}

				VIEW_TYPE_EXPAND -> {
					view = EmojiPackExpand(context)
				}

				VIEW_TYPE_BUTTON -> {
					view = EmojiPackButton(context)
				}

				VIEW_TYPE_HINT -> {
					val textView = @SuppressLint("AppCompatCustomView") object : TextView(context) {
						override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
							super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(AndroidUtilities.dp(26f)), MeasureSpec.EXACTLY))
						}
					}

					textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)

					if (type == TYPE_EMOJI_STATUS) {
						textView.text = context.getString(R.string.EmojiLongtapHint)
					}
					else {
						textView.text = context.getString(R.string.ReactionsLongtapHint)
					}

					textView.setGravity(Gravity.CENTER)
					textView.setTextColor(context.getColor(R.color.dark_gray))

					view = textView
				}

				VIEW_TYPE_SEARCH -> {
					view = FixedHeightEmptyCell(context, 52)
					view.setTag("searchbox")
				}

				else -> {
					view = ImageViewEmoji(context)
				}
			}

			if (showAnimator?.isRunning == true) {
				view.scaleX = 0f
				view.scaleY = 0f
			}

			return RecyclerListView.Holder(view)
		}

		override fun getItemViewType(position: Int): Int {
			return if (position == searchRow) {
				VIEW_TYPE_SEARCH
			}
			else if (position in recentReactionsStartRow..<recentReactionsEndRow || position in topReactionsStartRow..<topReactionsEndRow) {
				VIEW_TYPE_REACTION
			}
			else if (positionToExpand.indexOfKey(position) >= 0) {
				VIEW_TYPE_EXPAND
			}
			else if (positionToButton.indexOfKey(position) >= 0) {
				VIEW_TYPE_BUTTON
			}
			else if (position == longtapHintRow) {
				VIEW_TYPE_HINT
			}
			else if (positionToSection.indexOfKey(position) >= 0 || position == recentReactionsSectionRow || position == popularSectionRow) {
				VIEW_TYPE_HEADER
			}
			else {
				VIEW_TYPE_EMOJI
			}
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val viewType = holder.itemViewType

			if (showAnimator == null || !showAnimator!!.isRunning) {
				holder.itemView.scaleX = 1f
				holder.itemView.scaleY = 1f
			}

			if (viewType == VIEW_TYPE_HINT) {
				val textView = holder.itemView as TextView

				if (hintExpireDate != null) {
					textView.text = LocaleController.formatString("EmojiStatusExpireHint", R.string.EmojiStatusExpireHint, LocaleController.formatStatusExpireDateTime(hintExpireDate!!.toLong()))
				}
			}
			else if (viewType == VIEW_TYPE_HEADER) {
				val header = holder.itemView as HeaderView

				if (position == recentReactionsSectionRow) {
					header.setText(context.getString(R.string.RecentlyUsed), false)
					header.closeIcon.setVisibility(VISIBLE)
					header.closeIcon.setOnClickListener { clearRecent() }
					return
				}

				header.closeIcon.setVisibility(GONE)

				if (position == popularSectionRow) {
					header.setText(context.getString(R.string.PopularReactions), false)
					return
				}

				val index = positionToSection[position]

				if (index >= 0) {
					val pack = packs[index]
					header.setText(pack.set.title, !pack.free && !UserConfig.getInstance(currentAccount).isPremium)
				}
				else {
					header.setText(null, false)
				}
			}
			else if (viewType == VIEW_TYPE_REACTION) {
				val imageView = holder.itemView as ImageViewEmoji
				imageView.position = position

				val currentReaction = if (position in recentReactionsStartRow..<recentReactionsEndRow) {
					val index = position - recentReactionsStartRow
					recentReactions[index]
				}
				else {
					val index = position - topReactionsStartRow
					topReactions[index]
				}

				if (imageView.imageReceiver == null) {
					imageView.imageReceiver = ImageReceiver()
					imageView.imageReceiver?.setLayerNum(7)
					imageView.imageReceiver?.onAttachedToWindow()
				}

				imageView.reaction = currentReaction
				imageView.setViewSelected(selectedReactions.contains(currentReaction))

				if (currentReaction.emojicon != null) {
					imageView.isDefaultReaction = true

					var fallbackToEmoji = true
					val reaction = MediaDataController.getInstance(currentAccount).reactionsMap[currentReaction.emojicon]

					if (reaction != null) {
						val svgThumb = getSvgThumb(reaction.activateAnimation, ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), 0.2f)

						if (svgThumb != null) {
							val image = ImageLocation.getForDocument(reaction.selectAnimation)

							if (image != null) {
								fallbackToEmoji = false
								imageView.imageReceiver?.setImage(image, ReactionsUtils.SELECT_ANIMATION_FILTER, null, null, svgThumb, 0, "tgs", currentReaction, 0)
							}
						}
					}

					if (fallbackToEmoji) {
						val bitmap = Emoji.getEmojiDrawableForEmojicon(currentReaction.emojicon)

						if (bitmap != null) {
							imageView.imageReceiver?.setImageBitmap(bitmap)
						}
						else {
							imageView.imageReceiver?.clearImage()
						}
					}

					imageView.span = null
					imageView.document = null
					imageView.drawable = null
					imageView.premiumLockIconView?.setVisibility(GONE)
					imageView.premiumLockIconView?.setImageReceiver(null)
				}
				else {
					imageView.isDefaultReaction = false
					imageView.span = AnimatedEmojiSpan(currentReaction.documentId, null)
					imageView.document = null
					imageView.imageReceiver!!.clearImage()

					var drawable: Drawable? = emojiGridView!!.animatedEmojiDrawables!![imageView.span!!.getDocumentId()]

					if (drawable == null) {
						drawable = AnimatedEmojiDrawable.make(currentAccount, cacheType, imageView.span!!.getDocumentId())
						emojiGridView?.animatedEmojiDrawables?.put(imageView.span!!.getDocumentId(), drawable)
					}

					imageView.drawable = drawable

					if (!UserConfig.getInstance(currentAccount).isPremium) {
						if (imageView.premiumLockIconView == null) {
							imageView.premiumLockIconView = PremiumLockIconView(context, PremiumLockIconView.TYPE_STICKERS_PREMIUM_LOCKED)

							imageView.addView(imageView.premiumLockIconView, LayoutHelper.createFrame(12, 12, Gravity.RIGHT or Gravity.BOTTOM))
						}

						imageView.premiumLockIconView?.setVisibility(VISIBLE)
					}
				}
			}
			else if (viewType == VIEW_TYPE_EXPAND) {
				val button = holder.itemView as EmojiPackExpand
				val i = positionToExpand[position]
				val pack = if (i >= 0 && i < packs.size) packs[i] else null

				if (i == -1) {
					recentExpandButton = button

					val maxLen = layoutManager!!.spanCount * RECENT_MAX_LINES

					button.textView.text = "+" + (recent.size - maxLen + (if (includeEmpty) 1 else 0) + 1)
				}
				else if (pack != null) {
					if (recentExpandButton === button) {
						recentExpandButton = null
					}

					val maxLen = layoutManager!!.spanCount * EXPAND_MAX_LINES

					button.textView.text = "+" + (pack.documents.size - maxLen + 1)
				}
				else {
					if (recentExpandButton === button) {
						recentExpandButton = null
					}
				}
			}
			else if (viewType == VIEW_TYPE_BUTTON) {
				val button = holder.itemView as EmojiPackButton
				val packIndex = positionToButton[position]

				val pack = packs.getOrNull(packIndex)

				if (pack != null) {
					button.set(pack.set.title, !pack.free && !UserConfig.getInstance(currentAccount).isPremium, pack.installed) {
						if (!pack.free && !UserConfig.getInstance(currentAccount).isPremium) {
							PremiumFeatureBottomSheet(baseFragment, context, currentAccount, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false).show()
							return@set
						}

						var p: Int? = null
						var expandButton: View? = null

						for (i in 0 until emojiGridView!!.childCount) {
							if (emojiGridView!!.getChildAt(i) is EmojiPackExpand) {
								val child = emojiGridView!!.getChildAt(i)
								val j = emojiGridView!!.getChildAdapterPosition(child)

								if (j >= 0 && positionToExpand[j] == packIndex) {
									p = j
									expandButton = child
									break
								}
							}
						}

						p?.let { expand(it, expandButton) }

						EmojiPacksAlert.installSet(null, pack.set, false)

						installedEmojiSets.add(pack.set.id)

						updateRows(true)
					}
				}
			}
			else if (viewType == VIEW_TYPE_SEARCH) {
				// unused
			}
			else {
				val imageView = holder.itemView as ImageViewEmoji
				imageView.empty = false
				imageView.position = position
				imageView.setPadding(AndroidUtilities.dp(1f), AndroidUtilities.dp(1f), AndroidUtilities.dp(1f), AndroidUtilities.dp(1f))

				val recentMaxLen = layoutManager!!.spanCount * RECENT_MAX_LINES
				val maxLen = layoutManager!!.spanCount * EXPAND_MAX_LINES
				val recentSize = if (recent.size > recentMaxLen && !recentExpanded) recentMaxLen else recent.size + if (includeEmpty) 1 else 0

				val selected: Boolean

				imageView.drawable = null

				if (includeEmpty && position == (if (searchRow != -1) 1 else 0) + (if (includeHint) 1 else 0)) {
					selected = selectedDocumentIds.contains(null as Long?)

					imageView.empty = true
					imageView.setPadding(AndroidUtilities.dp(5f), AndroidUtilities.dp(5f), AndroidUtilities.dp(5f), AndroidUtilities.dp(5f))
					imageView.span = null
					imageView.document = null
				}
				else if (position - (if (searchRow != -1) 1 else 0) - (if (includeHint) 1 else 0) < recentSize) {
					imageView.span = recent[position - (if (searchRow != -1) 1 else 0) - (if (includeHint) 1 else 0) - (if (includeEmpty) 1 else 0)]
					imageView.document = if (imageView.span == null) null else imageView.span!!.document

					selected = imageView.span != null && selectedDocumentIds.contains(imageView.span!!.getDocumentId())
				}
				else if (defaultStatuses.isNotEmpty() && position - (if (searchRow != -1) 1 else 0) - (if (includeHint) 1 else 0) - recentSize - 1 >= 0 && position - (if (searchRow != -1) 1 else 0) - (if (includeHint) 1 else 0) - recentSize - 1 < defaultStatuses.size) {
					val index = position - (if (searchRow != -1) 1 else 0) - (if (includeHint) 1 else 0) - recentSize - 1
					imageView.span = defaultStatuses[index]
					imageView.document = if (imageView.span == null) null else imageView.span!!.document

					selected = imageView.span != null && selectedDocumentIds.contains(imageView.span!!.getDocumentId())
				}
				else {
					for (i in 0 until positionToSection.size) {
						val startPosition = positionToSection.keyAt(i)
						val index = i - if (defaultStatuses.isEmpty()) 0 else 1
						val pack = (if (index >= 0) packs[index] else null) ?: continue
						val count = if (pack.expanded) pack.documents.size else min(pack.documents.size.toDouble(), maxLen.toDouble()).toInt()

						if (position > startPosition && position <= startPosition + 1 + count) {
							val document = pack.documents[position - startPosition - 1]

							if (document != null) {
								imageView.span = AnimatedEmojiSpan(document, null)
								imageView.document = document
							}
						}
					}

					selected = imageView.span != null && selectedDocumentIds.contains(imageView.span!!.getDocumentId())
				}

				if (imageView.span != null) {
					var drawable = emojiGridView!!.animatedEmojiDrawables!![imageView.span!!.getDocumentId()]

					if (drawable == null) {
						drawable = AnimatedEmojiDrawable.make(currentAccount, cacheType, imageView.span!!.getDocumentId())
						emojiGridView!!.animatedEmojiDrawables!!.put(imageView.span!!.getDocumentId(), drawable)
					}

					imageView.drawable = drawable
				}
				else {
					imageView.drawable = null
				}

				imageView.setViewSelected(selected)
			}
		}

		override fun getItemCount(): Int {
			return totalCount
		}
	}

	private inner class HeaderView(context: Context) : FrameLayout(context) {
		private val layoutView = LinearLayout(context)
		private val textView = TextView(context)
		private val lockView = RLottieImageView(context)
		private var lockT = 0f
		private var lockAnimator: ValueAnimator? = null
		val closeIcon = ImageView(context)

		init {
			layoutView.orientation = LinearLayout.HORIZONTAL

			addView(layoutView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

			lockView.setAnimation(R.raw.unlock_icon, 20, 20)
			lockView.setColorFilter(context.getColor(R.color.dark_gray))

			layoutView.addView(lockView, LayoutHelper.createLinear(20, 20))

			textView.setTextColor(context.getColor(R.color.dark_gray))
			textView.setTypeface(Theme.TYPEFACE_BOLD)
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			textView.ellipsize = TextUtils.TruncateAt.END
			textView.setLines(1)
			textView.setMaxLines(1)
			textView.setSingleLine(true)

			layoutView.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

			closeIcon.setImageResource(R.drawable.msg_close)
			closeIcon.setScaleType(ImageView.ScaleType.CENTER)
			closeIcon.colorFilter = PorterDuffColorFilter(context.getColor(R.color.dark_gray), PorterDuff.Mode.MULTIPLY)

			addView(closeIcon, LayoutHelper.createFrame(24, 24, Gravity.RIGHT or Gravity.CENTER_VERTICAL))
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30f), MeasureSpec.EXACTLY))
		}

		fun setText(text: String?, lock: Boolean) {
			textView.text = text
			updateLock(lock, false)
		}

		fun updateLock(lock: Boolean, animated: Boolean) {
			lockAnimator?.cancel()
			lockAnimator = null

			if (animated) {
				lockAnimator = ValueAnimator.ofFloat(lockT, if (lock) 1f else 0f)

				lockAnimator?.addUpdateListener {
					lockT = it.getAnimatedValue() as Float

					lockView.translationX = AndroidUtilities.dp(-8f) * (1f - lockT)
					textView.translationX = AndroidUtilities.dp(-8f) * (1f - lockT)

					lockView.setAlpha(lockT)
				}

				lockAnimator?.setDuration(200)
				lockAnimator?.interpolator = CubicBezierInterpolator.EASE_BOTH
				lockAnimator?.start()
			}
			else {
				lockT = if (lock) 1f else 0f

				lockView.translationX = AndroidUtilities.dp(-8f) * (1f - lockT)
				textView.translationX = AndroidUtilities.dp(-8f) * (1f - lockT)

				lockView.setAlpha(lockT)
			}
		}
	}

	private inner class EmojiPackButton(context: Context) : FrameLayout(context) {
		val addButtonView = FrameLayout(context)
		val addButtonTextView = AnimatedTextView(context)
		val premiumButtonView = PremiumButtonView(context, false)
		private var lastTitle: String? = null
		private var installFadeAway: ValueAnimator? = null
		private var lockT = 0f
		private var lockShow: Boolean? = null
		private var lockAnimator: ValueAnimator? = null

		init {
			addButtonTextView.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT)
			addButtonTextView.setTextSize(AndroidUtilities.dp(14f).toFloat())
			addButtonTextView.setTypeface(Theme.TYPEFACE_BOLD)
			addButtonTextView.setTextColor(context.getColor(R.color.white))
			addButtonTextView.setGravity(Gravity.CENTER)

			addButtonView.background = Theme.AdaptiveRipple.filledRect(context.getColor(R.color.brand), 8f)
			addButtonView.addView(addButtonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

			addView(addButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			premiumButtonView.setIcon(R.raw.unlock_icon)

			addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		}

		operator fun set(title: String?, unlock: Boolean, installed: Boolean, onClickListener: OnClickListener?) {
			lastTitle = title

			if (unlock) {
				addButtonView.gone()
				premiumButtonView.visible()
				premiumButtonView.setButton(LocaleController.formatString("UnlockPremiumEmojiPack", R.string.UnlockPremiumEmojiPack, title), onClickListener)
			}
			else {
				premiumButtonView.gone()
				addButtonView.visible()
				addButtonView.setOnClickListener(onClickListener)
			}

			updateInstall(installed, false)

			updateLock(unlock, false)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			setPadding(AndroidUtilities.dp(5f), AndroidUtilities.dp(8f), AndroidUtilities.dp(5f), AndroidUtilities.dp(8f))
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44f) + paddingTop + paddingBottom, MeasureSpec.EXACTLY))
		}

		fun updateInstall(installed: Boolean, animated: Boolean) {
			val text: CharSequence = if (installed) context.getString(R.string.Added) else LocaleController.formatString("AddStickersCount", R.string.AddStickersCount, lastTitle)

			addButtonTextView.setText(text, animated)

			installFadeAway?.cancel()
			installFadeAway = null

			addButtonView.setEnabled(!installed)

			if (animated) {
				installFadeAway = ValueAnimator.ofFloat(addButtonView.alpha, if (installed) .6f else 1f)

				addButtonView.setAlpha(addButtonView.alpha)

				installFadeAway?.addUpdateListener { addButtonView.setAlpha(it.getAnimatedValue() as Float) }
				installFadeAway?.setDuration(450)
				installFadeAway?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
				installFadeAway?.start()
			}
			else {
				addButtonView.setAlpha(if (installed) .6f else 1f)
			}
		}

		private fun updateLock(show: Boolean, animated: Boolean) {
			lockAnimator?.cancel()
			lockAnimator = null

			if (lockShow != null && lockShow == show) {
				return
			}

			lockShow = show

			if (animated) {
				premiumButtonView.visible()

				lockAnimator = ValueAnimator.ofFloat(lockT, if (show) 1f else 0f)

				lockAnimator?.addUpdateListener {
					lockT = it.getAnimatedValue() as Float
					addButtonView.setAlpha(1f - lockT)
					premiumButtonView.setAlpha(lockT)
				}

				lockAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (!show) {
							premiumButtonView.gone()
						}
					}
				})

				lockAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
				lockAnimator?.setDuration(350)
				lockAnimator?.start()
			}
			else {
				lockT = (if (lockShow == true) 1 else 0).toFloat()

				addButtonView.setAlpha(1f - lockT)

				premiumButtonView.setAlpha(lockT)
				premiumButtonView.scaleX = lockT
				premiumButtonView.scaleY = lockT
				premiumButtonView.visibility = if (lockShow == true) VISIBLE else GONE
			}
		}
	}

	inner class ImageViewEmoji(context: Context) : FrameLayout(context) {
		private val invalidateHolder = InvalidateHolder {
			emojiGridView?.invalidate()
		}

		private var selected = false
		var empty = false
		var notDraw = false
		var position = 0
		var document: TLRPC.Document? = null
		var span: AnimatedEmojiSpan? = null
		var backgroundThreadDrawHolder: BackgroundThreadDrawHolder? = null
		var imageReceiver: ImageReceiver? = null
		var imageReceiverToDraw: ImageReceiver? = null
		var isDefaultReaction = false
		var reaction: VisibleReaction? = null
		var drawableBounds: Rect? = null
		var bigReactionSelectedProgress = 0f
		var attached = false
		var skewAlpha = 0f
		var skewIndex = 0
		var backAnimator: ValueAnimator? = null
		var premiumLockIconView: PremiumLockIconView? = null
		var pressedProgress = 0f

		var drawable: Drawable? = null
			set(value) {
				if (field !== value) {
					if (field != null && field is AnimatedEmojiDrawable) {
						(field as? AnimatedEmojiDrawable)?.removeView(invalidateHolder)
					}

					field = value

					if (attached && value is AnimatedEmojiDrawable) {
						value.addView(invalidateHolder)
					}
				}
			}

		public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY))
		}

		override fun setPressed(pressed: Boolean) {
			if (isPressed != pressed) {
				super.setPressed(pressed)

				invalidate()

				if (pressed) {
					backAnimator?.removeAllListeners()
					backAnimator?.cancel()
				}

				if (!pressed && pressedProgress != 0f) {
					backAnimator = ValueAnimator.ofFloat(pressedProgress, 0f)

					backAnimator?.addUpdateListener {
						pressedProgress = it.getAnimatedValue() as Float
						emojiGridView?.invalidate()
					}

					backAnimator?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							super.onAnimationEnd(animation)
							backAnimator = null
						}
					})

					backAnimator?.interpolator = OvershootInterpolator(5.0f)
					backAnimator?.setDuration(350)
					backAnimator?.start()
				}
			}
		}

		fun updatePressedProgress() {
			if (isPressed && pressedProgress != 1f) {
				pressedProgress = Utilities.clamp(pressedProgress + 16f / 100f, 1f, 0f)
				invalidate()
			}
		}

		fun update(time: Long) {
			imageReceiverToDraw?.lottieAnimation?.updateCurrentFrame(time, true)
			imageReceiverToDraw?.animation?.updateCurrentFrame(time, true)
		}

		fun setViewSelected(selected: Boolean) {
			if (this.selected != selected) {
				this.selected = selected
			}
		}

		fun getViewSelected(): Boolean {
			return this.selected
		}

		fun drawSelected(canvas: Canvas) {
			if (selected && !notDraw) {
				AndroidUtilities.rectTmp.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
				AndroidUtilities.rectTmp.inset(AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat())

				val paint = if (empty || drawable is AnimatedEmojiDrawable && (drawable as AnimatedEmojiDrawable).canOverrideColor()) selectorAccentPaint else selectorPaint
				val wasAlpha = paint.alpha

				paint.setAlpha((wasAlpha * alpha).toInt())

				canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)

				paint.setAlpha(wasAlpha)
			}
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			attached = true
			(drawable as? AnimatedEmojiDrawable)?.addView(invalidateHolder)
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()
			attached = false
			(drawable as? AnimatedEmojiDrawable)?.removeView(invalidateHolder)
		}
	}

	open inner class EmojiListView(context: Context) : RecyclerListView(context) {
		private val viewsGroupedByLines = SparseArray<ArrayList<ImageViewEmoji>?>()
		private val unusedArrays = ArrayList<ArrayList<ImageViewEmoji>?>()
		private val unusedLineDrawables = ArrayList<DrawingInBackgroundLine?>()
		val lineDrawables = ArrayList<DrawingInBackgroundLine?>()
		private val lineDrawablesTmp = ArrayList<DrawingInBackgroundLine?>()
		var animatedEmojiDrawables: LongSparseArray<AnimatedEmojiDrawable>? = LongSparseArray()
		private var lastChildCount = -1

		init {
			setDrawSelectorBehind(true)
			setClipToPadding(false)
			setSelectorRadius(AndroidUtilities.dp(4f))
			setSelectorDrawableColor(context.getColor(R.color.light_background))
		}

		private val animatedEmojiSpans: Array<AnimatedEmojiSpan?>
			get() {
				val spans = arrayOfNulls<AnimatedEmojiSpan>(childCount)

				for (i in 0 until childCount) {
					val child = getChildAt(i)

					if (child is ImageViewEmoji) {
						spans[i] = child.span
					}
				}

				return spans
			}

		fun updateEmojiDrawables() {
			animatedEmojiDrawables = AnimatedEmojiSpan.update(cacheType, this, animatedEmojiSpans, animatedEmojiDrawables)
		}

		override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
			return super.drawChild(canvas, child, drawingTime)
		}

		override fun canHighlightChildAt(child: View?, x: Float, y: Float): Boolean {
			if (child is ImageViewEmoji && (child.empty || (child.drawable as? AnimatedEmojiDrawable)?.canOverrideColor() == true)) {
				setSelectorDrawableColor(ColorUtils.setAlphaComponent(context.getColor(R.color.brand), 30))
			}
			else {
				setSelectorDrawableColor(context.getColor(R.color.light_background))
			}

			return super.canHighlightChildAt(child, x, y)
		}

		override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
			super.onLayout(changed, l, t, r, b)

			if (showAnimator == null || showAnimator?.isRunning != true) {
				updateEmojiDrawables()
				lastChildCount = childCount
			}
		}

		public override fun dispatchDraw(canvas: Canvas) {
			if (visibility != VISIBLE) {
				return
			}

			val restoreTo = canvas.saveCount

			if (lastChildCount != childCount && showAnimator != null && showAnimator?.isRunning != true) {
				updateEmojiDrawables()
				lastChildCount = childCount
			}

			if (!selectorRect.isEmpty) {
				selectorDrawable?.bounds = selectorRect

				canvas.withSave {
					selectorTransformer?.accept(this)
					selectorDrawable?.draw(this)
				}
			}

			for (i in 0 until viewsGroupedByLines.size) {
				val arrayList = viewsGroupedByLines.valueAt(i)
				arrayList?.clear()
				unusedArrays.add(arrayList)
			}

			viewsGroupedByLines.clear()

			val animatedExpandIn = animateExpandStartTime > 0 && SystemClock.elapsedRealtime() - animateExpandStartTime < animateExpandDuration()
			val drawButton = animatedExpandIn && animateExpandFromButton != null && animateExpandFromPosition >= 0

			if (animatedEmojiDrawables != null) {
				for (i in 0 until childCount) {
					val child = getChildAt(i)

					if (child is ImageViewEmoji) {
						child.updatePressedProgress()

						val top = if (smoothScrolling) child.getY().toInt() else child.getTop()
						var arrayList = viewsGroupedByLines[top]

						canvas.withTranslation(child.x, child.y) {
							child.drawSelected(this)
						}

						if (child.background != null) {
							child.background.setBounds(child.x.toInt(), child.y.toInt(), child.x.toInt() + child.width, child.y.toInt() + child.height)

							val wasAlpha = 255

							child.background.alpha = (wasAlpha * child.alpha).toInt()
							child.background.draw(canvas)
							child.background.alpha = wasAlpha
						}

						if (arrayList == null) {
							arrayList = if (unusedArrays.isNotEmpty()) {
								unusedArrays.removeAt(unusedArrays.size - 1)
							}
							else {
								ArrayList()
							}

							viewsGroupedByLines.put(top, arrayList)
						}

						arrayList?.add(child)

						if (child.premiumLockIconView?.visibility == VISIBLE) {
							if (child.premiumLockIconView?.imageReceiver == null && child.imageReceiverToDraw != null) {
								child.premiumLockIconView?.setImageReceiver(child.imageReceiverToDraw)
							}
						}
					}

					if (drawButton && child != null) {
						val position = getChildAdapterPosition(child)

						if (position == animateExpandFromPosition - (if (animateExpandFromButtonTranslate > 0) 0 else 1)) {
							val t = CubicBezierInterpolator.EASE_OUT.getInterpolation(MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime) / 200f, 0f, 1f))

							if (t < 1) {
								canvas.saveLayerAlpha(child.left.toFloat(), child.top.toFloat(), child.right.toFloat(), child.bottom.toFloat(), (255 * (1f - t)).toInt())
								canvas.translate(child.left.toFloat(), child.top + animateExpandFromButtonTranslate)

								val scale = .5f + .5f * (1f - t)

								canvas.scale(scale, scale, child.width / 2f, child.height / 2f)

								animateExpandFromButton?.draw(canvas)

								canvas.restore()
							}
						}
					}
				}
			}

			lineDrawablesTmp.clear()
			lineDrawablesTmp.addAll(lineDrawables)
			lineDrawables.clear()

			val time = System.currentTimeMillis()

			for (i in 0 until viewsGroupedByLines.size) {
				val arrayList = viewsGroupedByLines.valueAt(i)
				val firstView = arrayList!![0]
				val position = getChildAdapterPosition(firstView)
				var drawable: DrawingInBackgroundLine? = null

				for (k in lineDrawablesTmp.indices) {
					if (lineDrawablesTmp[k]!!.position == position) {
						drawable = lineDrawablesTmp[k]
						lineDrawablesTmp.removeAt(k)
						break
					}
				}

				if (drawable == null) {
					drawable = if (unusedLineDrawables.isNotEmpty()) {
						unusedLineDrawables.removeAt(unusedLineDrawables.size - 1)
					}
					else {
						DrawingInBackgroundLine()
					}

					drawable?.position = position
					drawable?.onAttachToWindow()
				}

				lineDrawables.add(drawable)

				drawable?.imageViewEmojis = arrayList

				canvas.withTranslation(firstView.left.toFloat(), firstView.y /* + firstView.getPaddingTop()*/) {
					drawable?.startOffset = firstView.left

					val w = measuredWidth - firstView.left * 2
					val h = firstView.measuredHeight

					if (w > 0 && h > 0) {
						drawable?.draw(this, time, w, h, 1f)
					}
				}
			}

			for (i in lineDrawablesTmp.indices) {
				if (unusedLineDrawables.size < 3) {
					unusedLineDrawables.add(lineDrawablesTmp[i])

					lineDrawablesTmp[i]?.imageViewEmojis = null
					lineDrawablesTmp[i]?.reset()
				}
				else {
					lineDrawablesTmp[i]?.onDetachFromWindow()
				}
			}

			lineDrawablesTmp.clear()

			for (i in 0 until childCount) {
				val child = getChildAt(i)

				if (child is ImageViewEmoji) {
					child.premiumLockIconView?.let {
						canvas.withTranslation((child.x + it.x).toInt().toFloat(), (child.y + it.y).toInt().toFloat()) {
							it.draw(this)
						}
					}
				}
				else if (child != null && child !== animateExpandFromButton) {
					canvas.withTranslation(child.x.toInt().toFloat(), child.y.toInt().toFloat()) {
    					child.draw(this)
					}
				}
			}

			canvas.restoreToCount(restoreTo)
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()

			if (this === emojiGridView) {
				bigReactionImageReceiver.onAttachedToWindow()
			}
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()

			if (this === emojiGridView) {
				bigReactionImageReceiver.onDetachedFromWindow()
			}
		}

		inner class DrawingInBackgroundLine : DrawingInBackgroundThreadDrawable() {
			private val appearScaleInterpolator = OvershootInterpolator(3f)
			var position = 0
			var startOffset = 0
			var imageViewEmojis: ArrayList<ImageViewEmoji>? = null
			private val drawInBackgroundViews = ArrayList<ImageViewEmoji>()
			private var skewAlpha = 1f
			private var skewBelow = false

			override fun draw(canvas: Canvas, time: Long, w: Int, h: Int, alpha: Float) {
				val imageViewEmojis = imageViewEmojis ?: return

				skewAlpha = 1f
				skewBelow = false

				if (imageViewEmojis.isNotEmpty()) {
					val firstView: View = imageViewEmojis[0]

					if (firstView.y > height - paddingBottom - firstView.height) {
						skewAlpha = MathUtils.clamp(-(firstView.y - height + paddingBottom) / firstView.height, 0f, 1f)
						skewAlpha = .25f + .75f * skewAlpha
					}
				}

				var drawInUi = skewAlpha < 1 || isAnimating || imageViewEmojis.size <= 4 || SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW || showAnimator?.isRunning == true

				if (!drawInUi) {
					val animatedExpandIn = animateExpandStartTime > 0 && SystemClock.elapsedRealtime() - animateExpandStartTime < animateExpandDuration()

					for (i in imageViewEmojis.indices) {
						val img = imageViewEmojis[i]

						if (img.pressedProgress != 0f || img.backAnimator != null || img.translationX != 0f || img.translationY != 0f || img.alpha != 1f || animatedExpandIn && img.position > animateExpandFromPosition && img.position < animateExpandToPosition) {
							drawInUi = true
							break
						}
					}
				}

				if (drawInUi) {
					prepareDraw(System.currentTimeMillis())
					drawInUiThread(canvas, alpha)
					reset()
				}
				else {
					super.draw(canvas, time, w, h, alpha)
				}
			}

			public override fun drawBitmap(canvas: Canvas, bitmap: Bitmap, paint: Paint) {
				canvas.drawBitmap(bitmap, 0f, 0f, paint)
			}

			override fun prepareDraw(time: Long) {
				drawInBackgroundViews.clear()

				val imageViewEmojis = imageViewEmojis ?: return

				for (i in imageViewEmojis.indices) {
					val imageView = imageViewEmojis[i]

					if (imageView.notDraw) {
						continue
					}

					var imageReceiver: ImageReceiver?

					if (imageView.empty) {
						val drawable = getPremiumStar()
						var scale = 1f

						if (imageView.pressedProgress != 0f || imageView.getViewSelected()) {
							scale *= 0.8f + 0.2f * (1f - if (imageView.getViewSelected()) .7f else imageView.pressedProgress)
						}

						drawable.alpha = 255

						val topOffset = 0
						val w = imageView.width - imageView.getPaddingLeft() - imageView.getPaddingRight()
						val h = imageView.height - imageView.paddingTop - imageView.paddingBottom

						AndroidUtilities.rectTmp2.set((imageView.width / 2f - w / 2f * imageView.scaleX * scale).toInt(), (imageView.height / 2f - h / 2f * imageView.scaleY * scale).toInt(), (imageView.width / 2f + w / 2f * imageView.scaleX * scale).toInt(), (imageView.height / 2f + h / 2f * imageView.scaleY * scale).toInt())
						AndroidUtilities.rectTmp2.offset(imageView.left - startOffset, topOffset)

						if (imageView.drawableBounds == null) {
							imageView.drawableBounds = Rect()
						}

						imageView.drawableBounds?.set(AndroidUtilities.rectTmp2)
						imageView.drawable = drawable

						drawInBackgroundViews.add(imageView)
					}
					else {
						var scale = 1f
						var alpha = 1f

						if (imageView.pressedProgress != 0f || imageView.getViewSelected()) {
							scale *= 0.8f + 0.2f * (1f - if (imageView.getViewSelected()) .7f else imageView.pressedProgress)
						}

						val animatedExpandIn = animateExpandStartTime > 0 && SystemClock.elapsedRealtime() - animateExpandStartTime < animateExpandDuration()

						if (animatedExpandIn && animateExpandFromPosition >= 0 && animateExpandToPosition >= 0 && animateExpandStartTime > 0) {
							val position = getChildAdapterPosition(imageView)
							val pos = position - animateExpandFromPosition
							val count = animateExpandToPosition - animateExpandFromPosition

							if (pos in 0..<count) {
								val appearDuration = animateExpandAppearDuration().toFloat()
								val appearT = MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime) / appearDuration, 0f, 1f)
								val alphaT = AndroidUtilities.cascade(appearT, pos.toFloat(), count.toFloat(), count / 4f)
								val scaleT = AndroidUtilities.cascade(appearT, pos.toFloat(), count.toFloat(), count / 4f)

								scale *= .5f + appearScaleInterpolator.getInterpolation(scaleT) * .5f
								alpha *= alphaT
							}
						}
						else {
							alpha = imageView.alpha
						}

						if (!imageView.isDefaultReaction) {
							if (imageView.span == null) {
								continue
							}

							var drawable: AnimatedEmojiDrawable? = null

							if (imageView.drawable is AnimatedEmojiDrawable) {
								drawable = imageView.drawable as AnimatedEmojiDrawable?
							}

							if (drawable?.imageReceiver == null) {
								continue
							}

							imageReceiver = drawable.imageReceiver

							drawable.setAlpha((255 * alpha).toInt())

							imageView.drawable = drawable

							imageView.drawable?.colorFilter = premiumStarColorFilter
						}
						else {
							imageReceiver = imageView.imageReceiver
							imageReceiver?.alpha = alpha
						}

						if (imageReceiver == null) {
							continue
						}

						if (imageView.getViewSelected()) {
							imageReceiver.setRoundRadius(AndroidUtilities.dp(4f))
						}
						else {
							imageReceiver.setRoundRadius(0)
						}

						imageView.backgroundThreadDrawHolder = imageReceiver.setDrawInBackgroundThread(imageView.backgroundThreadDrawHolder)
						imageView.backgroundThreadDrawHolder?.time = time
						imageView.imageReceiverToDraw = imageReceiver
						imageView.update(time)

						val topOffset = 0

						AndroidUtilities.rectTmp2.set(imageView.getPaddingLeft(), imageView.paddingTop, imageView.width - imageView.getPaddingRight(), imageView.height - imageView.paddingBottom)

						if (imageView.getViewSelected()) {
							AndroidUtilities.rectTmp2.set(Math.round(AndroidUtilities.rectTmp2.centerX() - AndroidUtilities.rectTmp2.width() / 2f * 0.86f), Math.round(AndroidUtilities.rectTmp2.centerY() - AndroidUtilities.rectTmp2.height() / 2f * 0.86f), Math.round(AndroidUtilities.rectTmp2.centerX() + AndroidUtilities.rectTmp2.width() / 2f * 0.86f), Math.round(AndroidUtilities.rectTmp2.centerY() + AndroidUtilities.rectTmp2.height() / 2f * 0.86f))
						}

						AndroidUtilities.rectTmp2.offset(imageView.left + imageView.translationX.toInt() - startOffset, topOffset)

						imageView.backgroundThreadDrawHolder?.setBounds(AndroidUtilities.rectTmp2)
						imageView.skewAlpha = 1f
						imageView.skewIndex = i

						drawInBackgroundViews.add(imageView)
					}
				}
			}

			override fun drawInBackground(canvas: Canvas) {
				for (i in drawInBackgroundViews.indices) {
					val imageView = drawInBackgroundViews[i]

					if (!imageView.notDraw) {
						if (imageView.empty) {
							imageView.drawable?.bounds = imageView.drawableBounds ?: Rect()

							if (imageView.drawable is AnimatedEmojiDrawable) {
								(imageView.drawable as? AnimatedEmojiDrawable)?.draw(canvas, false)
							}
							else {
								imageView.drawable?.draw(canvas)
							}
						}
						else if (imageView.imageReceiverToDraw != null) {
							imageView.imageReceiverToDraw?.draw(canvas, imageView.backgroundThreadDrawHolder)
						}
					}
				}
			}

			override fun drawInUiThread(canvas: Canvas, alpha: Float) {
				val imageViewEmojis = imageViewEmojis ?: return
				@Suppress("NAME_SHADOWING") var alpha = alpha

				canvas.save()
				canvas.translate(-startOffset.toFloat(), 0f)

				for (i in imageViewEmojis.indices) {
					val imageView = imageViewEmojis[i]

					if (imageView.notDraw) {
						continue
					}

					var scale = imageView.scaleX

					if (imageView.pressedProgress != 0f || imageView.getViewSelected()) {
						scale *= 0.8f + 0.2f * (1f - if (imageView.getViewSelected()) 0.7f else imageView.pressedProgress)
					}

					val animatedExpandIn = animateExpandStartTime > 0 && SystemClock.elapsedRealtime() - animateExpandStartTime < animateExpandDuration()
					val animatedExpandInLocal = animatedExpandIn && animateExpandFromPosition >= 0 && animateExpandToPosition >= 0 && animateExpandStartTime > 0

					if (animatedExpandInLocal) {
						val position = getChildAdapterPosition(imageView)
						val pos = position - animateExpandFromPosition
						val count = animateExpandToPosition - animateExpandFromPosition

						if (pos in 0..<count) {
							val appearDuration = animateExpandAppearDuration().toFloat()
							val appearT = MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime) / appearDuration, 0f, 1f)
							val alphaT = AndroidUtilities.cascade(appearT, pos.toFloat(), count.toFloat(), count / 4f)
							val scaleT = AndroidUtilities.cascade(appearT, pos.toFloat(), count.toFloat(), count / 4f)

							scale *= .5f + appearScaleInterpolator.getInterpolation(scaleT) * .5f
							alpha = alphaT
						}
					}
					else {
						alpha = imageView.alpha
					}

					AndroidUtilities.rectTmp2.set(imageView.x.toInt() + imageView.getPaddingLeft(), imageView.paddingTop, imageView.x.toInt() + imageView.width - imageView.getPaddingRight(), imageView.height - imageView.paddingBottom)

					if (!smoothScrolling && !animatedExpandIn) {
						AndroidUtilities.rectTmp2.offset(0, imageView.translationY.toInt())
					}

					var drawable: Drawable? = null

					if (imageView.empty) {
						drawable = getPremiumStar()
						drawable.bounds = AndroidUtilities.rectTmp2
						drawable.alpha = 255
					}
					else if (!imageView.isDefaultReaction) {
						val span = imageView.span

						if (span == null || imageView.notDraw) {
							continue
						}

						drawable = imageView.drawable

						if (drawable == null) {
							continue
						}

						drawable.alpha = 255
						drawable.bounds = AndroidUtilities.rectTmp2
					}
					else if (imageView.imageReceiver != null) {
						imageView.imageReceiver?.setImageCoordinates(AndroidUtilities.rectTmp2)
					}

					if (imageView.drawable is AnimatedEmojiDrawable) {
						(imageView.drawable as? AnimatedEmojiDrawable)?.colorFilter = premiumStarColorFilter
					}

					imageView.skewAlpha = skewAlpha
					imageView.skewIndex = i

					if (scale != 1f || skewAlpha < 1) {
						canvas.withScale(scale, scale, AndroidUtilities.rectTmp2.centerX().toFloat(), AndroidUtilities.rectTmp2.centerY().toFloat()) {
							skew(this, i, imageView.height)
							drawImage(this, drawable, imageView, alpha)
						}
					}
					else {
						drawImage(canvas, drawable, imageView, alpha)
					}
				}
				canvas.restore()
			}

			private fun skew(canvas: Canvas, i: Int, h: Int) {
				if (skewAlpha < 1) {
					if (skewBelow) {
						canvas.translate(0f, h.toFloat())
						canvas.skew((1f - 2f * i / imageViewEmojis!!.size) * -(1f - skewAlpha), 0f)
						canvas.translate(0f, -h.toFloat())
					}
					else {
						canvas.scale(1f, skewAlpha, 0f, 0f)
						canvas.skew((1f - 2f * i / imageViewEmojis!!.size) * (1f - skewAlpha), 0f)
					}
				}
			}

			private fun drawImage(canvas: Canvas, drawable: Drawable?, imageView: ImageViewEmoji, alpha: Float) {
				if (drawable != null) {
					drawable.colorFilter = premiumStarColorFilter
					drawable.alpha = (255 * alpha).toInt()

					if (drawable is AnimatedEmojiDrawable) {
						drawable.draw(canvas, false)
					}
					else {
						drawable.draw(canvas)
					}
				}
				else if (imageView.isDefaultReaction) {
					imageView.imageReceiver?.alpha = alpha
					imageView.imageReceiver?.draw(canvas)
				}
			}

			override fun onFrameReady() {
				super.onFrameReady()

				for (i in drawInBackgroundViews.indices) {
					val imageView = drawInBackgroundViews[i]
					imageView.backgroundThreadDrawHolder?.release()
				}

				emojiGridView?.invalidate()
			}
		}
	}

	private inner class SearchBox(context: Context) : FrameLayout(context) {
		private val box: FrameLayout
		private val search: ImageView
		val clear: ImageView
		val clearDrawable: CloseProgressDrawable2
		var input: EditTextCaption? = null

		init {
			setBackgroundColor(context.getColor(R.color.background))

			box = FrameLayout(context)
			box.background = Theme.createRoundRectDrawable(AndroidUtilities.dp(18f), context.getColor(R.color.background))
			box.setClipToOutline(true)

			box.outlineProvider = object : ViewOutlineProvider() {
				override fun getOutline(view: View, outline: Outline) {
					outline.setRoundRect(0, 0, view.width, view.height, AndroidUtilities.dp(18f).toFloat())
				}
			}

			addView(box, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.TOP or Gravity.FILL_HORIZONTAL, 8f, (4 + 8).toFloat(), 8f, 8f))

			search = ImageView(context)
			search.setScaleType(ImageView.ScaleType.CENTER)
			search.setImageResource(R.drawable.smiles_inputsearch)
			search.colorFilter = PorterDuffColorFilter(context.getColor(R.color.dark_gray), PorterDuff.Mode.MULTIPLY)

			box.addView(search, LayoutHelper.createFrame(36, 36, Gravity.LEFT or Gravity.TOP))

			input = object : EditTextCaption(context) {
				override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
					if (focused) {
						onInputFocus()
						AndroidUtilities.runOnUIThread({ AndroidUtilities.showKeyboard(input) }, 200)
					}
					super.onFocusChanged(focused, direction, previouslyFocusedRect)
				}
			}

			input?.doAfterTextChanged {
				search(if (it == null || AndroidUtilities.trim(it, null).isEmpty()) null else it.toString())
			}

			input?.background = null
			input?.setPadding(0, 0, AndroidUtilities.dp(4f), 0)
			input?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			input?.setHint(if (type == TYPE_EMOJI_STATUS) context.getString(R.string.SearchEmojiHint) else context.getString(R.string.SearchReactionsHint))
			input?.setHintTextColor(context.getColor(R.color.hint))
			input?.setTextColor(context.getColor(R.color.text))
			input?.setImeOptions(EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI)
			input?.setCursorColor(context.getColor(R.color.text))
			input?.setCursorSize(AndroidUtilities.dp(20f))
			input?.setGravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
			input?.setCursorWidth(1.5f)
			input?.setMaxLines(1)

			box.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.FILL, 36f, -1f, 32f, 0f))

			clear = ImageView(context)
			clear.setScaleType(ImageView.ScaleType.CENTER)

			clear.setImageDrawable(object : CloseProgressDrawable2(1.25f) {
				override fun getCurrentColor(): Int {
					return context.getColor(R.color.dark_gray)
				}
			}.also { clearDrawable = it })

			clearDrawable.setSide(AndroidUtilities.dp(7f))

			clear.scaleX = 0.1f
			clear.scaleY = 0.1f
			clear.setAlpha(0.0f)

			box.addView(clear, LayoutHelper.createFrame(36, 36, Gravity.RIGHT or Gravity.TOP))

			clear.setOnClickListener {
				input?.setText("")
				search(null)
			}

			setOnClickListener {
				onInputFocus()
				input?.requestFocus()
				scrollToPosition(0, 0)
			}
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp((4 + 8 + 36 + 8).toFloat()), MeasureSpec.EXACTLY))
		}
	}

	private open inner class SelectStatusDurationDialog(context: Context, private val parentDialogDismiss: Runnable?, private val parentDialogView: View?, private val imageViewEmoji: ImageViewEmoji?) : Dialog(context) {
		private val imageReceiver = ImageReceiver()
		private val from = Rect()
		private val to = Rect()
		private val current = Rect()
		private val contentView = ContentView(context)
		private val linearLayoutView: LinearLayout
		private val emojiPreviewView: View
		private val menuView: ActionBarPopupWindowLayout
		private val parentDialogX: Int
		private var parentDialogY = 0
		private val clipBottom: Int
		private val tempLocation = IntArray(2)
		private var blurBitmap: Bitmap? = null
		private var blurBitmapPaint: Paint? = null
		private var lastInsets: WindowInsets? = null
		private var dateBottomSheet: BottomSheet? = null
		private var changeToScrimColor = false
		private var done = false
		private var showT = 0f
		private var showing = false
		private var showAnimator: ValueAnimator? = null
		private var showMenuT = 0f
		private var showingMenu = false
		private var showMenuAnimator: ValueAnimator? = null
		private var dismissed = false

		init {
			setContentView(contentView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

			linearLayoutView = LinearLayout(context)
			linearLayoutView.orientation = LinearLayout.VERTICAL

			emojiPreviewView = object : View(context) {
				override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
					super.onLayout(changed, left, top, right, bottom)
					getLocationOnScreen(tempLocation)
					to[tempLocation[0], tempLocation[1], tempLocation[0] + width] = tempLocation[1] + height
					AndroidUtilities.lerp(from, to, showT, current)
				}
			}

			linearLayoutView.addView(emojiPreviewView, LayoutHelper.createLinear(160, 160, Gravity.CENTER, 0, 0, 0, 16))
			menuView = ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert2)
			linearLayoutView.addView(menuView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0))

			ActionBarMenuItem.addItem(first = true, last = false, windowLayout = menuView, icon = 0, text = context.getString(R.string.SetEmojiStatusUntil1Hour), needCheck = false).setOnClickListener { done((System.currentTimeMillis() / 1000 + 60 * 60).toInt()) }
			ActionBarMenuItem.addItem(first = false, last = false, windowLayout = menuView, icon = 0, text = context.getString(R.string.SetEmojiStatusUntil2Hours), needCheck = false).setOnClickListener { done((System.currentTimeMillis() / 1000 + 2 * 60 * 60).toInt()) }
			ActionBarMenuItem.addItem(first = false, last = false, windowLayout = menuView, icon = 0, text = context.getString(R.string.SetEmojiStatusUntil8Hours), needCheck = false).setOnClickListener { done((System.currentTimeMillis() / 1000 + 8 * 60 * 60).toInt()) }
			ActionBarMenuItem.addItem(first = false, last = false, windowLayout = menuView, icon = 0, text = context.getString(R.string.SetEmojiStatusUntil2Days), needCheck = false).setOnClickListener { done((System.currentTimeMillis() / 1000 + 2 * 24 * 60 * 60).toInt()) }

			ActionBarMenuItem.addItem(first = false, last = true, windowLayout = menuView, icon = 0, text = context.getString(R.string.SetEmojiStatusUntilOther), needCheck = false).setOnClickListener {
				if (dateBottomSheet != null) {
					return@setOnClickListener
				}

				var selected = false

				val builder = createStatusUntilDatePickerDialog(context, System.currentTimeMillis() / 1000) { date ->
					selected = true
					done(date)
				}

				builder?.setOnPreDismissListener {
					if (!selected) {
						animateMenuShow(true, null)
					}

					dateBottomSheet = null
				}

				dateBottomSheet = builder?.show()

				animateMenuShow(false, null)
			}

			contentView.addView(linearLayoutView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

			val window = window

			if (window != null) {
				window.setWindowAnimations(R.style.DialogNoAnimation)
				window.setBackgroundDrawable(null)

				val params = window.attributes

				params.width = ViewGroup.LayoutParams.MATCH_PARENT
				params.gravity = Gravity.TOP or Gravity.LEFT
				params.dimAmount = 0f
				params.flags = params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
				params.flags = params.flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
				params.flags = params.flags or (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

				contentView.setOnApplyWindowInsetsListener { v, insets ->
					lastInsets = insets

					v.requestLayout()

					if (Build.VERSION.SDK_INT >= 30) {
						WindowInsets.CONSUMED
					}
					else {
						@Suppress("DEPRECATION") insets.consumeSystemWindowInsets()
					}
				}

				params.flags = params.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN

				contentView.fitsSystemWindows = true
				contentView.systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_FULLSCREEN

				params.height = ViewGroup.LayoutParams.MATCH_PARENT

				if (Build.VERSION.SDK_INT >= 28) {
					params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
				}

				window.setAttributes(params)
			}


			imageViewEmoji?.notDraw = true

			prepareBlurBitmap()
			imageReceiver.setParentView(contentView)
			imageReceiver.setLayerNum(7)

			var document = imageViewEmoji?.document

			if (document == null && imageViewEmoji?.drawable is AnimatedEmojiDrawable) {
				document = (imageViewEmoji.drawable as? AnimatedEmojiDrawable)?.document
			}

			if (document != null) {
				val filter = "160_160"
				val mediaLocation: ImageLocation?
				val mediaFilter: String
				val thumbDrawable = getSvgThumb(document.thumbs, ResourcesCompat.getColor(getContext().resources, R.color.dark_gray, null), 0.2f)
				val thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)

				if ("video/webm" == document.mimeType) {
					mediaLocation = ImageLocation.getForDocument(document)
					mediaFilter = filter + "_" + ImageLoader.AUTOPLAY_FILTER
					thumbDrawable?.overrideWidthAndHeight(512, 512)
				}
				else {
					if (thumbDrawable != null && MessageObject.isAnimatedStickerDocument(document, false)) {
						thumbDrawable.overrideWidthAndHeight(512, 512)
					}

					mediaLocation = ImageLocation.getForDocument(document)
					mediaFilter = filter
				}

				imageReceiver.setImage(mediaLocation, mediaFilter, ImageLocation.getForDocument(thumb, document), filter, null, null, thumbDrawable, document.size, null, document, 1)

				if ((imageViewEmoji?.drawable as? AnimatedEmojiDrawable)?.canOverrideColor() == true) {
					imageReceiver.setColorFilter(premiumStarColorFilter)
				}
			}

			imageViewEmoji?.let {
				it.getLocationOnScreen(tempLocation)

				from.left = tempLocation[0] + it.getPaddingLeft()
				from.top = tempLocation[1] + it.paddingTop
				from.right = tempLocation[0] + it.width - it.getPaddingRight()
				from.bottom = tempLocation[1] + it.height - it.paddingBottom
			}

			AndroidUtilities.lerp(from, to, showT, current)

			parentDialogView?.getLocationOnScreen(tempLocation)

			parentDialogX = tempLocation[0]

			clipBottom = tempLocation[1].also { parentDialogY = it } + (parentDialogView?.height ?: 0)
		}

		private fun done(date: Int?) {
			if (done) {
				return
			}

			done = true

			var showback: Boolean

			if ((date != null && getOutBounds(from)).also { changeToScrimColor = it }.also { showback = it }) {
				parentDialogView?.getLocationOnScreen(tempLocation)
				from.offset(tempLocation[0], tempLocation[1])
			}
			else {
				imageViewEmoji?.let {
					it.getLocationOnScreen(tempLocation)

					from.left = tempLocation[0] + it.getPaddingLeft()
					from.top = tempLocation[1] + it.paddingTop
					from.right = tempLocation[0] + it.width - it.getPaddingRight()
					from.bottom = tempLocation[1] + it.height - it.paddingBottom
				}
			}

			if (date != null) {
				parentDialogDismiss?.run()
			}

			animateShow(false, {
				onEnd(date)
				super.dismiss()
			}, {
				if (date != null) {
					runCatching {
						performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
					}

					onEndPartly(date)
				}
			}, !showback)

			animateMenuShow(false, null)
		}

		protected open fun getOutBounds(rect: Rect): Boolean {
			return false
		}

		protected open fun onEnd(date: Int?) {
			// stub
		}

		protected open fun onEndPartly(date: Int?) {
			// stub
		}

		private val parentActivity: Activity?
			get() {
				var currentContext: Context? = context

				while (currentContext is ContextWrapper) {
					if (currentContext is Activity) {
						return currentContext
					}

					currentContext = currentContext.baseContext
				}

				return null
			}

		private fun prepareBlurBitmap() {
			val parentActivity = parentActivity ?: return
			val parentView = parentActivity.window.decorView
			val w = (parentView.measuredWidth / 12.0f).toInt()
			val h = (parentView.measuredHeight / 12.0f).toInt()
			val bitmap = createBitmap(w, h)

			val canvas = Canvas(bitmap)
			canvas.scale(1.0f / 12.0f, 1.0f / 12.0f)
			canvas.drawColor(parentActivity.getColor(R.color.background))

			parentView.draw(canvas)

			if (parentActivity is LaunchActivity) {
				parentActivity.actionBarLayout?.getLastFragment()?.visibleDialog?.window?.decorView?.draw(canvas)
			}

			if (parentDialogView != null) {
				parentDialogView.getLocationOnScreen(tempLocation)

				canvas.withTranslation(tempLocation[0].toFloat(), tempLocation[1].toFloat()) {
					parentDialogView.draw(this)
				}
			}

			Utilities.stackBlurBitmap(bitmap, max(10.0, (max(w.toDouble(), h.toDouble()) / 180)).toInt())

			blurBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
			blurBitmap = bitmap
		}

		private fun animateShow(show: Boolean, onDone: Runnable?, onPartly: Runnable?, showback: Boolean) {
			if (imageViewEmoji == null) {
				onDone?.run()
				return
			}

			if (showAnimator != null) {
				if (showing == show) {
					return
				}

				showAnimator?.cancel()
			}

			showing = show

			if (show) {
				imageViewEmoji.notDraw = true
			}

			var partlyDone = false

			showAnimator = ValueAnimator.ofFloat(showT, if (show) 1f else 0f)

			showAnimator?.addUpdateListener {
				showT = it.getAnimatedValue() as Float

				AndroidUtilities.lerp(from, to, showT, current)

				contentView.invalidate()

				if (!show) {
					menuView.setAlpha(showT)
				}

				if (showT < 0.025f && !show) {
					if (showback) {
						imageViewEmoji.notDraw = false
						emojiGridView?.invalidate()
					}

					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.startAllHeavyOperations, 4)
				}

				if (showT < .5f && !show && onPartly != null && !partlyDone) {
					partlyDone = true
					onPartly.run()
				}
			}

			showAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					showT = if (show) 1f else 0f

					AndroidUtilities.lerp(from, to, showT, current)

					contentView.invalidate()

					if (!show) {
						menuView.setAlpha(showT)
					}

					if (showT < .5f && !show && onPartly != null && !partlyDone) {
						partlyDone = true
						onPartly.run()
					}

					if (!show) {
						if (showback) {
							imageViewEmoji.notDraw = false
							emojiGridView?.invalidate()
						}

						NotificationCenter.globalInstance.postNotificationName(NotificationCenter.startAllHeavyOperations, 4)
					}

					showAnimator = null

					contentView.invalidate()

					onDone?.run()
				}
			})

			showAnimator?.setDuration(420)
			showAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
			showAnimator?.start()
		}

		private fun animateMenuShow(show: Boolean, onDone: Runnable?) {
			if (showMenuAnimator != null) {
				if (showingMenu == show) {
					return
				}
				showMenuAnimator?.cancel()
			}

			showingMenu = show

			showMenuAnimator = ValueAnimator.ofFloat(showMenuT, if (show) 1f else 0f)

			showMenuAnimator?.addUpdateListener {
				showMenuT = it.getAnimatedValue() as Float

				menuView.setBackScaleY(showMenuT)
				menuView.setAlpha(CubicBezierInterpolator.EASE_OUT.getInterpolation(showMenuT))

				val count = menuView.itemsCount

				for (i in 0 until count) {
					val at = AndroidUtilities.cascade(showMenuT, i.toFloat(), count.toFloat(), 4f)
					menuView.getItemAt(i).translationY = (1f - at) * AndroidUtilities.dp(-12f)
					menuView.getItemAt(i).setAlpha(at)
				}
			}

			showMenuAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					showMenuT = if (show) 1f else 0f

					menuView.setBackScaleY(showMenuT)
					menuView.setAlpha(CubicBezierInterpolator.EASE_OUT.getInterpolation(showMenuT))

					val count = menuView.itemsCount

					for (i in 0 until count) {
						val at = AndroidUtilities.cascade(showMenuT, i.toFloat(), count.toFloat(), 4f)
						menuView.getItemAt(i).translationY = (1f - at) * AndroidUtilities.dp(-12f)
						menuView.getItemAt(i).setAlpha(at)
					}

					showMenuAnimator = null

					onDone?.run()
				}
			})

			if (show) {
				showMenuAnimator?.setDuration(360)
				showMenuAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
			}
			else {
				showMenuAnimator?.setDuration(240)
				showMenuAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT
			}

			showMenuAnimator?.start()
		}

		override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
			val res = super.dispatchTouchEvent(ev)

			if (!res && ev.action == MotionEvent.ACTION_DOWN) {
				dismiss()
				return false
			}

			return res
		}

		override fun show() {
			super.show()
			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.stopAllHeavyOperations, 4)
			animateShow(true, null, null, true)
			animateMenuShow(true, null)
		}

		override fun dismiss() {
			if (dismissed) {
				return
			}

			done(null)

			dismissed = true
		}

		private inner class ContentView(context: Context) : FrameLayout(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY))
			}

			override fun dispatchDraw(canvas: Canvas) {
				if (blurBitmap != null && blurBitmapPaint != null) {
					canvas.withScale(12f, 12f) {
						blurBitmapPaint?.setAlpha((255 * showT).toInt())

						drawBitmap(blurBitmap!!, 0f, 0f, blurBitmapPaint)
					}
				}

				super.dispatchDraw(canvas)

				if (imageViewEmoji != null) {
					val drawable = imageViewEmoji.drawable

					if (drawable != null) {
						if (changeToScrimColor) {
							drawable.colorFilter = PorterDuffColorFilter(ColorUtils.blendARGB(scrimColor, context.getColor(R.color.brand), showT), PorterDuff.Mode.MULTIPLY)
						}
						else {
							drawable.colorFilter = premiumStarColorFilter
						}

						drawable.alpha = (255 * (1f - showT)).toInt()

						AndroidUtilities.rectTmp.set(current)

						var scale = 1f

						if (imageViewEmoji.pressedProgress != 0f || imageViewEmoji.getViewSelected()) {
							scale *= 0.8f + 0.2f * (1f - if (imageViewEmoji.getViewSelected()) .7f else imageViewEmoji.pressedProgress)
						}

						AndroidUtilities.rectTmp2.set((AndroidUtilities.rectTmp.centerX() - AndroidUtilities.rectTmp.width() / 2 * scale).toInt(), (AndroidUtilities.rectTmp.centerY() - AndroidUtilities.rectTmp.height() / 2 * scale).toInt(), (AndroidUtilities.rectTmp.centerX() + AndroidUtilities.rectTmp.width() / 2 * scale).toInt(), (AndroidUtilities.rectTmp.centerY() + AndroidUtilities.rectTmp.height() / 2 * scale).toInt())

						val skew = 1f - (1f - imageViewEmoji.skewAlpha) * (1f - showT)

						canvas.save()

						if (skew < 1) {
							canvas.translate(AndroidUtilities.rectTmp2.left.toFloat(), AndroidUtilities.rectTmp2.top.toFloat())
							canvas.scale(1f, skew, 0f, 0f)
							canvas.skew((1f - 2f * imageViewEmoji.skewIndex / layoutManager!!.spanCount) * (1f - skew), 0f)
							canvas.translate(-AndroidUtilities.rectTmp2.left.toFloat(), -AndroidUtilities.rectTmp2.top.toFloat())
						}

						canvas.clipRect(0f, 0f, width.toFloat(), clipBottom + showT * AndroidUtilities.dp(45f))

						drawable.bounds = AndroidUtilities.rectTmp2
						drawable.draw(canvas)

						canvas.restore()

						when (imageViewEmoji.skewIndex) {
							0 -> AndroidUtilities.rectTmp2.offset(AndroidUtilities.dp(8 * skew), 0)
							1 -> AndroidUtilities.rectTmp2.offset(AndroidUtilities.dp(4 * skew), 0)
							layoutManager!!.spanCount - 2 -> AndroidUtilities.rectTmp2.offset(-AndroidUtilities.dp(-4 * skew), 0)
							layoutManager!!.spanCount - 1 -> AndroidUtilities.rectTmp2.offset(AndroidUtilities.dp(-8 * skew), 0)
						}

						canvas.saveLayerAlpha(AndroidUtilities.rectTmp2.left.toFloat(), AndroidUtilities.rectTmp2.top.toFloat(), AndroidUtilities.rectTmp2.right.toFloat(), AndroidUtilities.rectTmp2.bottom.toFloat(), (255 * (1f - showT)).toInt())
						canvas.clipRect(AndroidUtilities.rectTmp2)
						canvas.translate((bottomGradientView.x + this@SelectAnimatedEmojiDialog.contentView.x + parentDialogX).toInt().toFloat(), bottomGradientView.y.toInt() + this@SelectAnimatedEmojiDialog.contentView.y + parentDialogY)

						bottomGradientView.draw(canvas)

						canvas.restore()
					}
					else if (imageViewEmoji.isDefaultReaction && imageViewEmoji.imageReceiver != null) {
						imageViewEmoji.imageReceiver?.alpha = 1f - showT
						imageViewEmoji.imageReceiver?.setImageCoordinates(current)
						imageViewEmoji.imageReceiver?.draw(canvas)
					}
				}

				imageReceiver.alpha = showT
				imageReceiver.setImageCoordinates(current)
				imageReceiver.draw(canvas)
			}

			override fun onConfigurationChanged(newConfig: Configuration) {
				lastInsets = null
			}

			override fun onAttachedToWindow() {
				super.onAttachedToWindow()
				imageReceiver.onAttachedToWindow()
			}

			override fun onDetachedFromWindow() {
				super.onDetachedFromWindow()
				imageReceiver.onDetachedFromWindow()
			}

			override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
				super.onLayout(changed, left, top, right, bottom)

				val parentActivity: Activity = parentActivity ?: return
				val parentView = parentActivity.window.decorView

				if (blurBitmap == null || blurBitmap?.getWidth() != parentView.measuredWidth || blurBitmap?.getHeight() != parentView.measuredHeight) {
					prepareBlurBitmap()
				}
			}
		}
	}

	companion object {
		const val TYPE_EMOJI_STATUS = 0
		const val TYPE_REACTIONS = 1
		const val TYPE_SET_DEFAULT_REACTION = 2
		private const val RECENT_MAX_LINES = 5
		private const val EXPAND_MAX_LINES = 3
		private var lastSearchKeyboardLanguage: Array<String>? = null

		fun preload(account: Int) {
			MediaDataController.getInstance(account).let {
				it.checkStickers(MediaDataController.TYPE_EMOJIPACKS)
				it.fetchEmojiStatuses(0, true)
				it.checkReactions()
				it.getStickerSet(TLInputStickerSetEmojiDefaultStatuses(), false)
			}
		}
	}
}
