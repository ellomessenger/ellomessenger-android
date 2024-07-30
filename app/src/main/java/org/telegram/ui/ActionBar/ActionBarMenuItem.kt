/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.ActionBar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.transition.ChangeBounds
import android.transition.Transition
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.transition.TransitionValues
import android.transition.Visibility
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.ActionBarPopupWindow.GapView
import org.telegram.ui.Adapters.FiltersView
import org.telegram.ui.Adapters.FiltersView.MediaFilterData
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CloseProgressDrawable2
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.LayoutHelper.createScroll
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.RLottieImageView
import kotlin.math.max

open class ActionBarMenuItem @JvmOverloads constructor(context: Context, private val parentMenu: ActionBarMenu?, backgroundColor: Int, iconColor: Int, text: Boolean = false) : FrameLayout(context) {
	private val currentSearchFilters = ArrayList<MediaFilterData>()
	private val location = IntArray(2)
	private val rect = Rect()
	private var additionalXOffset = 0
	private var additionalYOffset = 0
	private var allowCloseAnimation = true
	private var animateClear = true
	private var animationEnabled = true
	private var clearButton: ImageView? = null
	private var delegate: ActionBarMenuItemDelegate? = null
	private var forceSmoothKeyboard = false
	private var ignoreOnTextChange = false
	private var layoutInScreen = false
	private var longClickEnabled = false
	private var notificationIndex = -1
	private var onClickListener: OnClickListener? = null
	private var overrideMenuClick = false
	private var popupLayout: ActionBarPopupWindowLayout? = null
	private var popupWindow: ActionBarPopupWindow? = null
	private var processedPopupClick = false
	private var progressDrawable: CloseProgressDrawable2? = null
	private var searchContainerAnimator: AnimatorSet? = null
	private var searchFieldCaption: TextView? = null
	private var searchFilterLayout: LinearLayout? = null
	private var selectedFilterIndex = -1
	private var selectedMenuView: View? = null
	private var showMenuRunnable: Runnable? = null
	private var showSubMenuFrom: View? = null
	private var showSubmenuByMove = true
	private var subMenuDelegate: ActionBarSubMenuItemDelegate? = null
	private var subMenuOpenSide = 0
	private var transitionOffset = 0f
	private var wrappedSearchFrameLayout: FrameLayout? = null
	private var yOffset = 0

	var listener: ActionBarMenuItemSearchListener? = null
		private set

	var isSearchField = false
		private set

	var searchField: EditTextBoldCursor? = null
		private set

	var searchContainer: FrameLayout? = null
		private set

	var iconView: RLottieImageView? = null
		protected set

	var textView: TextView? = null
		protected set

	init {
		if (backgroundColor != 0) {
			background = Theme.createSelectorDrawable(backgroundColor, if (text) 5 else 1)
		}

		if (text) {
			textView = TextView(context)
			textView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			textView?.gravity = Gravity.CENTER
			textView?.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
			textView?.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

			if (iconColor != 0) {
				textView?.setTextColor(iconColor)
			}

			addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat()))
		}
		else {
			iconView = RLottieImageView(context)
			iconView?.scaleType = ImageView.ScaleType.CENTER
			iconView?.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

			addView(iconView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			if (iconColor != 0) {
				iconView?.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
			}
		}
	}

	override fun setTranslationX(translationX: Float) {
		super.setTranslationX(translationX + transitionOffset)
	}

	fun setLongClickEnabled(value: Boolean) {
		longClickEnabled = value
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (event.actionMasked == MotionEvent.ACTION_DOWN) {
			if (longClickEnabled && hasSubMenu() && popupWindow?.isShowing != true) {
				showMenuRunnable = Runnable {
					parent?.requestDisallowInterceptTouchEvent(true)
					toggleSubMenu()
				}

				AndroidUtilities.runOnUIThread(showMenuRunnable, 200)
			}
		}
		else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
			if (showSubmenuByMove && hasSubMenu() && popupWindow?.isShowing != true) {
				if (event.y > height) {
					parent?.requestDisallowInterceptTouchEvent(true)
					toggleSubMenu()
					return true
				}
			}
			else if (showSubmenuByMove && popupWindow != null && popupWindow!!.isShowing) {
				getLocationOnScreen(location)

				var x = event.x + location[0]
				var y = event.y + location[1]

				popupLayout?.getLocationOnScreen(location)

				x -= location[0].toFloat()
				y -= location[1].toFloat()

				selectedMenuView = null

				popupLayout?.children?.forEach { child ->
					child.getHitRect(rect)

					val tag = child.tag

					if (tag is Int && tag < 100) {
						if (!rect.contains(x.toInt(), y.toInt())) {
							child.isPressed = false
							child.isSelected = false
						}
						else {
							child.isPressed = true
							child.isSelected = true

							child.drawableHotspotChanged(x, y - child.top)

							selectedMenuView = child
						}
					}
				}
			}
		}
		else if (popupWindow?.isShowing == true && event.actionMasked == MotionEvent.ACTION_UP) {
			if (selectedMenuView != null) {
				selectedMenuView?.isSelected = false
				parentMenu?.onItemClick((selectedMenuView!!.tag as Int)) ?: delegate?.onItemClick(selectedMenuView!!.tag as Int)
				popupWindow?.dismiss(allowCloseAnimation)
			}
			else if (showSubmenuByMove) {
				popupWindow?.dismiss()
			}
		}
		else {
			selectedMenuView?.isSelected = false
			selectedMenuView = null
		}

		return super.onTouchEvent(event)
	}

	fun setDelegate(actionBarMenuItemDelegate: ActionBarMenuItemDelegate?) {
		delegate = actionBarMenuItemDelegate
	}

	fun setSubMenuDelegate(actionBarSubMenuItemDelegate: ActionBarSubMenuItemDelegate?) {
		subMenuDelegate = actionBarSubMenuItemDelegate
	}

	fun setShowSubmenuByMove(value: Boolean) {
		showSubmenuByMove = value
	}

	fun setIconColor(color: Int) {
		iconView?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
		textView?.setTextColor(color)
		clearButton?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
	}

	fun setSubMenuOpenSide(side: Int) {
		subMenuOpenSide = side
	}

	fun setLayoutInScreen(value: Boolean) {
		layoutInScreen = value
	}

	fun setForceSmoothKeyboard(value: Boolean) {
		forceSmoothKeyboard = value
	}

	private fun createPopupLayout() {
		if (popupLayout != null) {
			return
		}

		popupLayout = ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert2, ActionBarPopupWindowLayout.FLAG_USE_SWIPE_BACK)

		popupLayout?.setOnTouchListener { v, event ->
			if (event.actionMasked == MotionEvent.ACTION_DOWN) {
				if (popupWindow?.isShowing == true) {
					v.getHitRect(rect)

					if (!rect.contains(event.x.toInt(), event.y.toInt())) {
						popupWindow?.dismiss()
					}
				}
			}

			false
		}

		popupLayout?.setDispatchKeyEventListener { keyEvent ->
			if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK && keyEvent.repeatCount == 0 && popupWindow?.isShowing == true) {
				popupWindow?.dismiss()
			}
		}

		popupLayout?.swipeBack?.setOnClickListener {
			popupWindow?.dismiss()
		}
	}

	fun removeAllSubItems() {
		popupLayout?.removeInnerViews()
	}

	fun setShowedFromBottom(value: Boolean) {
		popupLayout?.setShownFromBottom(value)
	}

	fun addSubItem(view: View?, width: Int, height: Int) {
		createPopupLayout()
		popupLayout?.addView(view, LinearLayout.LayoutParams(width, height))
	}

	fun addSubItem(id: Int, view: View, width: Int, height: Int) {
		createPopupLayout()

		view.layoutParams = LinearLayout.LayoutParams(width, height)

		popupLayout?.addView(view)

		view.tag = id

		view.setOnClickListener { view1 ->
			if (popupWindow?.isShowing == true) {
				if (processedPopupClick) {
					return@setOnClickListener
				}

				processedPopupClick = true

				popupWindow?.dismiss(allowCloseAnimation)
			}

			parentMenu?.onItemClick((view1.tag as Int)) ?: delegate?.onItemClick(view1.tag as Int)
		}

		view.background = Theme.getSelectorDrawable(false)
	}

	fun addSubItem(id: Int, text: CharSequence?): TextView {
		createPopupLayout()

		val textView = TextView(context)
		textView.setTextColor(context.getColor(R.color.text))
		textView.background = Theme.getSelectorDrawable(false)

		if (!LocaleController.isRTL) {
			textView.gravity = Gravity.CENTER_VERTICAL
		}
		else {
			textView.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
		}

		textView.setPadding(AndroidUtilities.dp(16f), 0, AndroidUtilities.dp(16f), 0)
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.minWidth = AndroidUtilities.dp(196f)
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.tag = id
		textView.text = text

		popupLayout?.addView(textView)

		val layoutParams = textView.layoutParams as LinearLayout.LayoutParams

		if (LocaleController.isRTL) {
			layoutParams.gravity = Gravity.RIGHT
		}

		layoutParams.width = LayoutHelper.MATCH_PARENT
		layoutParams.height = AndroidUtilities.dp(48f)
		textView.layoutParams = layoutParams

		textView.setOnClickListener {
			if (popupWindow?.isShowing == true) {
				if (processedPopupClick) {
					return@setOnClickListener
				}

				processedPopupClick = true

				if (!allowCloseAnimation) {
					popupWindow?.animationStyle = R.style.PopupAnimation
				}

				popupWindow?.dismiss(allowCloseAnimation)
			}

			parentMenu?.onItemClick((it.tag as Int)) ?: delegate?.onItemClick(it.tag as Int)
		}

		return textView
	}

	fun addSubItem(id: Int, icon: Int, text: CharSequence?): ActionBarMenuSubItem {
		return addSubItem(id, icon, null, text, dismiss = true, needCheck = false)
	}

	fun addSubItem(id: Int, icon: Int, text: CharSequence?, needCheck: Boolean): ActionBarMenuSubItem {
		return addSubItem(id, icon, null, text, true, needCheck)
	}

	fun addGap(id: Int): View {
		createPopupLayout()

		val cell = View(context)
		cell.minimumWidth = AndroidUtilities.dp(196f)
		cell.tag = id
		cell.setTag(R.id.object_tag, 1)

		popupLayout?.addView(cell)

		val layoutParams = cell.layoutParams as LinearLayout.LayoutParams

		if (LocaleController.isRTL) {
			layoutParams.gravity = Gravity.RIGHT
		}

		layoutParams.width = LayoutHelper.MATCH_PARENT
		layoutParams.height = AndroidUtilities.dp(6f)

		cell.layoutParams = layoutParams

		return cell
	}

	fun addSubItem(id: Int, icon: Int, iconDrawable: Drawable?, text: CharSequence?, dismiss: Boolean, needCheck: Boolean): ActionBarMenuSubItem {
		createPopupLayout()

		val cell = ActionBarMenuSubItem(context, needCheck, top = false, bottom = false)
		cell.setTextAndIcon(text, icon, iconDrawable)
		cell.minimumWidth = AndroidUtilities.dp(196f)
		cell.tag = id

		popupLayout?.addView(cell)

		val layoutParams = cell.layoutParams as LinearLayout.LayoutParams

		if (LocaleController.isRTL) {
			layoutParams.gravity = Gravity.RIGHT
		}

		layoutParams.width = LayoutHelper.MATCH_PARENT
		layoutParams.height = AndroidUtilities.dp(48f)

		cell.layoutParams = layoutParams

		cell.setOnClickListener {
			if (popupWindow?.isShowing == true) {
				if (dismiss) {
					if (processedPopupClick) {
						return@setOnClickListener
					}

					processedPopupClick = true

					popupWindow?.dismiss(allowCloseAnimation)
				}
			}

			parentMenu?.onItemClick((it.tag as Int)) ?: delegate?.onItemClick(it.tag as Int)
		}

		return cell
	}

	fun addSwipeBackItem(icon: Int, iconDrawable: Drawable?, text: String?, viewToSwipeBack: View?): ActionBarMenuSubItem {
		createPopupLayout()

		val cell = ActionBarMenuSubItem(context, needCheck = false, top = false, bottom = false)
		cell.setTextAndIcon(text, icon, iconDrawable)
		cell.minimumWidth = AndroidUtilities.dp(196f)
		cell.setRightIcon(R.drawable.msg_arrowright)

		popupLayout?.addView(cell)

		val layoutParams = cell.layoutParams as LinearLayout.LayoutParams

		if (LocaleController.isRTL) {
			layoutParams.gravity = Gravity.RIGHT
		}

		layoutParams.width = LayoutHelper.MATCH_PARENT
		layoutParams.height = AndroidUtilities.dp(48f)

		cell.layoutParams = layoutParams

		val swipeBackIndex = popupLayout?.addViewToSwipeBack(viewToSwipeBack) ?: 0

		cell.openSwipeBackLayout = Runnable {
			popupLayout?.swipeBack?.openForeground(swipeBackIndex)
		}

		cell.setOnClickListener {
			cell.openSwipeBack()
		}

		popupLayout?.swipeBackGravityRight = true

		return cell
	}

	fun addDivider(color: Int): View {
		createPopupLayout()

		val cell = TextView(context)
		cell.setBackgroundColor(color)
		cell.minimumWidth = AndroidUtilities.dp(196f)

		popupLayout?.addView(cell)

		val layoutParams = cell.layoutParams as LinearLayout.LayoutParams
		layoutParams.width = LayoutHelper.MATCH_PARENT
		layoutParams.height = 1
		layoutParams.bottomMargin = AndroidUtilities.dp(3f)
		layoutParams.topMargin = layoutParams.bottomMargin

		cell.layoutParams = layoutParams

		return cell
	}

	fun redrawPopup(color: Int) {
		if (popupLayout != null && popupLayout?.getBackgroundColor() != color) {
			popupLayout?.setBackgroundColor(color)

			if (popupWindow?.isShowing == true) {
				popupLayout?.invalidate()
			}
		}
	}

	fun setPopupItemsColor(color: Int, icon: Boolean) {
		popupLayout?.linearLayout?.children?.forEach { child ->
			if (child is TextView) {
				child.setTextColor(color)
			}
			else if (child is ActionBarMenuSubItem) {
				if (icon) {
					child.setIconColor(color)
				}
				else {
					child.setTextColor(color)
				}
			}
		}
	}

	fun setPopupItemsSelectorColor(color: Int) {
		popupLayout?.linearLayout?.children?.forEach { child ->
			if (child is ActionBarMenuSubItem) {
				child.setSelectorColor(color)
			}
		}
	}

	fun setupPopupRadialSelectors(color: Int) {
		popupLayout?.setupRadialSelectors(color)
	}

	fun hasSubMenu(): Boolean {
		return popupLayout != null
	}

	fun getPopupLayout(): ActionBarPopupWindowLayout? {
		if (popupLayout == null) {
			createPopupLayout()
		}

		return popupLayout
	}

	fun setMenuYOffset(offset: Int) {
		yOffset = offset
	}

	fun toggleSubMenu(topView: View?, fromView: View?) {
		val popupLayout = popupLayout ?: return

		if (parentMenu != null && parentMenu.isActionMode && parentMenu.parentActionBar != null && !parentMenu.parentActionBar!!.isActionModeShowed) {
			return
		}

		if (showMenuRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(showMenuRunnable)
			showMenuRunnable = null
		}

		if (popupWindow?.isShowing == true) {
			popupWindow?.dismiss()
			return
		}

		showSubMenuFrom = fromView

		subMenuDelegate?.onShowSubMenu()

		(popupLayout.parent as? ViewGroup)?.removeView(popupLayout)

		var container: ViewGroup = popupLayout

		if (topView != null) {
			val linearLayout: LinearLayout = object : LinearLayout(context) {
				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					popupLayout.measure(widthMeasureSpec, heightMeasureSpec)

					if (popupLayout.swipeBack != null) {
						topView.layoutParams.width = popupLayout.swipeBack!!.getChildAt(0).measuredWidth
					}
					else {
						topView.layoutParams.width = popupLayout.measuredWidth - AndroidUtilities.dp(16f)
					}

					super.onMeasure(widthMeasureSpec, heightMeasureSpec)
				}
			}

			linearLayout.orientation = LinearLayout.VERTICAL

			val frameLayout = FrameLayout(context)
			frameLayout.alpha = 0f
			frameLayout.animate().alpha(1f).setDuration(100).start()

			val drawable = ResourcesCompat.getDrawable(resources, R.drawable.popup_fixed_alert2, null)?.mutate()
			drawable?.colorFilter = PorterDuffColorFilter(popupLayout.getBackgroundColor(), PorterDuff.Mode.SRC_IN)

			frameLayout.background = drawable
			frameLayout.addView(topView)

			linearLayout.addView(frameLayout, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
			linearLayout.addView(popupLayout, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, -AndroidUtilities.dp(4f), 0, 0))

			container = linearLayout

			popupLayout.setTopView(frameLayout)
		}


		popupWindow = ActionBarPopupWindow(container, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)

		if (animationEnabled) {
			popupWindow?.animationStyle = 0
		}
		else {
			popupWindow?.animationStyle = R.style.PopupAnimation
		}

		if (!animationEnabled) {
			popupWindow?.setAnimationEnabled(false)
		}

		popupWindow?.isOutsideTouchable = true
		popupWindow?.isClippingEnabled = true

		if (layoutInScreen) {
			popupWindow?.setLayoutInScreen()
		}

		popupWindow?.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
		popupWindow?.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED

		container.isFocusableInTouchMode = true

		container.setOnKeyListener { _, keyCode, event ->
			if (keyCode == KeyEvent.KEYCODE_MENU && event.repeatCount == 0 && event.action == KeyEvent.ACTION_UP && popupWindow?.isShowing == true) {
				popupWindow?.dismiss()
				return@setOnKeyListener true
			}

			false
		}

		popupWindow?.setOnDismissListener {
			onDismiss()
			subMenuDelegate?.onHideSubMenu()
		}

		container.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - AndroidUtilities.dp(40f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, MeasureSpec.AT_MOST))

		processedPopupClick = false
		popupWindow?.isFocusable = true

		updateOrShowPopup(true, container.measuredWidth == 0)
		popupLayout.updateRadialSelectors()

		popupLayout.swipeBack?.closeForeground(false)

		popupWindow?.startAnimation()
	}

	open fun toggleSubMenu() {
		toggleSubMenu(null, null)
	}

	fun openSearch(openKeyboard: Boolean) {
		if (searchContainer == null || searchContainer?.visibility == VISIBLE || parentMenu == null) {
			return
		}

		parentMenu.parentActionBar?.onSearchFieldVisibilityChanged(toggleSearch(openKeyboard))
	}

	protected fun onDismiss() {
		// stub
	}

	val isSearchFieldVisible: Boolean
		get() = searchContainer?.visibility == VISIBLE

	fun toggleSearch(openKeyboard: Boolean): Boolean {
		if (searchContainer == null || listener != null && !listener!!.canToggleSearch()) {
			return false
		}

		if (listener != null) {
			val animator = listener?.customToggleTransition

			if (animator != null) {
				animator.start()
				return true
			}
		}

		val menuIcons = ArrayList<View>()

		parentMenu?.children?.forEach { view ->
			if (view is ActionBarMenuItem) {
				val iconView = view.iconView

				if (iconView != null) {
					menuIcons.add(iconView)
				}
			}
		}

		return if (searchContainer?.tag != null) {
			searchContainer?.tag = null

			searchContainerAnimator?.removeAllListeners()
			searchContainerAnimator?.cancel()

			searchContainerAnimator = AnimatorSet()
			searchContainerAnimator?.playTogether(ObjectAnimator.ofFloat(searchContainer, ALPHA, searchContainer!!.alpha, 0f))

			for (i in menuIcons.indices) {
				menuIcons[i].alpha = 0f
				searchContainerAnimator?.playTogether(ObjectAnimator.ofFloat(menuIcons[i], ALPHA, menuIcons[i].alpha, 1f))
			}

			searchContainerAnimator?.duration = 150

			searchContainerAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					searchContainer?.alpha = 0f

					for (i in menuIcons.indices) {
						menuIcons[i].alpha = 1f
					}

					searchContainer?.visibility = GONE
				}
			})

			searchContainerAnimator?.start()
			searchField?.clearFocus()
			visibility = VISIBLE

			if (currentSearchFilters.isNotEmpty()) {
				if (listener != null) {
					for (i in currentSearchFilters.indices) {
						if (currentSearchFilters[i].removable) {
							listener?.onSearchFilterCleared(currentSearchFilters[i])
						}
					}
				}
			}

			listener?.onSearchCollapse()

			if (openKeyboard) {
				AndroidUtilities.hideKeyboard(searchField)
			}

			parentMenu?.requestLayout()
			requestLayout()

			false
		}
		else {
			searchContainer?.visible()
			searchContainer?.alpha = 0f

			searchContainerAnimator?.removeAllListeners()
			searchContainerAnimator?.cancel()

			searchContainerAnimator = AnimatorSet()
			searchContainerAnimator?.playTogether(ObjectAnimator.ofFloat(searchContainer, ALPHA, searchContainer!!.alpha, 1f))

			for (i in menuIcons.indices) {
				searchContainerAnimator?.playTogether(ObjectAnimator.ofFloat(menuIcons[i], ALPHA, menuIcons[i].alpha, 0f))
			}

			searchContainerAnimator?.duration = 150

			searchContainerAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					searchContainer?.alpha = 1f

					for (i in menuIcons.indices) {
						menuIcons[i].alpha = 0f
					}
				}
			})

			searchContainerAnimator?.start()

			visibility = GONE

			clearSearchFilters()

			searchField?.setText("")
			searchField?.requestFocus()

			if (openKeyboard) {
				AndroidUtilities.showKeyboard(searchField)
			}

			listener?.onSearchExpand()
			searchContainer?.tag = 1

			true
		}
	}

	fun removeSearchFilter(filter: MediaFilterData) {
		if (!filter.removable) {
			return
		}

		currentSearchFilters.remove(filter)

		if (selectedFilterIndex < 0 || selectedFilterIndex > currentSearchFilters.size - 1) {
			selectedFilterIndex = currentSearchFilters.size - 1
		}

		onFiltersChanged()

		searchField?.hideActionMode()
	}

	fun addSearchFilter(filter: MediaFilterData) {
		currentSearchFilters.add(filter)

		if (searchContainer?.tag != null) {
			selectedFilterIndex = currentSearchFilters.size - 1
		}

		onFiltersChanged()
	}

	fun clearSearchFilters() {
		var i = 0

		while (i < currentSearchFilters.size) {
			if (currentSearchFilters[i].removable) {
				currentSearchFilters.removeAt(i)
				i--
			}

			i++
		}

		onFiltersChanged()
	}

	private fun onFiltersChanged() {
		val visible = currentSearchFilters.isNotEmpty()
		val localFilters = ArrayList(currentSearchFilters)

		if (searchContainer?.tag != null) {
			val transition = TransitionSet()

			val changeBounds = ChangeBounds()
			changeBounds.duration = 150

			transition.addTransition(object : Visibility() {
				override fun onAppear(sceneRoot: ViewGroup, view: View, startValues: TransitionValues, endValues: TransitionValues): Animator {
					if (view is SearchFilterView) {
						val set = AnimatorSet()
						set.playTogether(ObjectAnimator.ofFloat(view, ALPHA, 0f, 1f), ObjectAnimator.ofFloat(view, SCALE_X, 0.5f, 1f), ObjectAnimator.ofFloat(view, SCALE_Y, 0.5f, 1f))
						set.interpolator = CubicBezierInterpolator.DEFAULT
						return set
					}

					return ObjectAnimator.ofFloat(view, ALPHA, 0f, 1f)
				}

				override fun onDisappear(sceneRoot: ViewGroup, view: View, startValues: TransitionValues, endValues: TransitionValues): Animator {
					if (view is SearchFilterView) {
						val set = AnimatorSet()
						set.playTogether(ObjectAnimator.ofFloat(view, ALPHA, view.getAlpha(), 0f), ObjectAnimator.ofFloat(view, SCALE_X, view.getScaleX(), 0.5f), ObjectAnimator.ofFloat(view, SCALE_Y, view.getScaleX(), 0.5f))
						set.interpolator = CubicBezierInterpolator.DEFAULT
						return set
					}

					return ObjectAnimator.ofFloat(view, ALPHA, 1f, 0f)
				}
			}.setDuration(150)).addTransition(changeBounds)

			transition.ordering = TransitionSet.ORDERING_TOGETHER
			transition.interpolator = CubicBezierInterpolator.EASE_OUT

			val selectedAccount = UserConfig.selectedAccount

			transition.addListener(object : Transition.TransitionListener {
				override fun onTransitionStart(transition: Transition) {
					notificationIndex = NotificationCenter.getInstance(selectedAccount).setAnimationInProgress(notificationIndex, null)
				}

				override fun onTransitionEnd(transition: Transition) {
					NotificationCenter.getInstance(selectedAccount).onAnimationFinish(notificationIndex)
				}

				override fun onTransitionCancel(transition: Transition) {
					NotificationCenter.getInstance(selectedAccount).onAnimationFinish(notificationIndex)
				}

				override fun onTransitionPause(transition: Transition) {
					// unused
				}

				override fun onTransitionResume(transition: Transition) {
					// unused
				}
			})

			TransitionManager.beginDelayedTransition(searchFilterLayout, transition)
		}

		var i = 0

		while (i < searchFilterLayout!!.childCount) {
			val removed = localFilters.remove((searchFilterLayout!!.getChildAt(i) as SearchFilterView).filter)

			if (!removed) {
				searchFilterLayout?.removeViewAt(i)
				i--
			}

			i++
		}

		for (filter in localFilters) {
			val searchFilterView = SearchFilterView(context)
			searchFilterView.setData(filter)

			searchFilterView.setOnClickListener {
				val index = currentSearchFilters.indexOf(searchFilterView.filter)
				if (selectedFilterIndex != index) {
					selectedFilterIndex = index
					onFiltersChanged()
					return@setOnClickListener
				}

				if (searchFilterView.filter?.removable == true) {
					if (!searchFilterView.getSelectedForDelete()) {
						searchFilterView.setSelectedForDelete(true)
					}
					else {
						val filterToRemove: MediaFilterData = searchFilterView.filter!!

						removeSearchFilter(filterToRemove)

						listener?.onSearchFilterCleared(filterToRemove)
						listener?.onTextChanged(searchField!!)
					}
				}
			}

			searchFilterLayout?.addView(searchFilterView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, 0, 0, 6, 0))
		}

		searchFilterLayout?.children?.forEachIndexed { index, view ->
			(view as? SearchFilterView)?.setExpanded(index == selectedFilterIndex)
		}

		searchFilterLayout?.tag = if (visible) 1 else null

		val oldX = searchField?.x ?: 0f

		if (searchContainer?.tag != null) {
			searchField?.viewTreeObserver?.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
				override fun onPreDraw(): Boolean {
					searchField?.viewTreeObserver?.removeOnPreDrawListener(this)

					if (searchField?.x != oldX) {
						searchField?.translationX = oldX - (searchField?.x ?: 0f)
					}

					searchField?.animate()?.translationX(0f)?.setDuration(250)?.setStartDelay(0)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()

					return true
				}
			})
		}

		checkClearButton()
	}

	val isSubMenuShowing: Boolean
		get() = popupWindow?.isShowing == true

	fun closeSubMenu() {
		if (popupWindow?.isShowing == true) {
			popupWindow?.dismiss()
		}
	}

	fun setIcon(drawable: Drawable?) {
		if (iconView == null) {
			return
		}

		if (drawable is RLottieDrawable) {
			iconView?.setAnimation(drawable as RLottieDrawable?)
		}
		else {
			iconView?.setImageDrawable(drawable)
		}
	}

	fun setIcon(resId: Int) {
		iconView?.setImageResource(resId)
	}

	fun setText(text: CharSequence?) {
		textView?.text = text
	}

	val contentView: View?
		get() = iconView ?: textView

	fun setSearchFieldHint(hint: CharSequence?) {
		if (searchFieldCaption == null) {
			return
		}

		searchField?.hint = hint
		contentDescription = hint
	}

	fun setSearchFieldText(text: CharSequence?, animated: Boolean) {
		if (searchFieldCaption == null) {
			return
		}

		animateClear = animated
		searchField?.setText(text)

		if (!text.isNullOrEmpty()) {
			searchField?.setSelection(text.length)
		}
	}

	fun onSearchPressed() {
		listener?.onSearchPressed(searchField!!)
	}

	fun setOverrideMenuClick(value: Boolean): ActionBarMenuItem {
		overrideMenuClick = value
		return this
	}

	fun getOverrideMenuClick(): Boolean {
		return overrideMenuClick
	}

	fun setIsSearchField(value: Boolean): ActionBarMenuItem {
		return setIsSearchField(value, false)
	}

	fun setIsSearchField(value: Boolean, wrapInScrollView: Boolean): ActionBarMenuItem {
		if (parentMenu == null) {
			return this
		}

		if (value && searchContainer == null) {
			searchContainer = object : FrameLayout(context) {
				private var ignoreRequestLayout = false

				override fun setVisibility(visibility: Int) {
					super.setVisibility(visibility)
					clearButton?.visibility = visibility
					wrappedSearchFrameLayout?.visibility = visibility
				}

				override fun setAlpha(alpha: Float) {
					super.setAlpha(alpha)
					if (clearButton?.tag != null) {
						clearButton?.alpha = alpha
						clearButton?.scaleX = alpha
						clearButton?.scaleY = alpha
					}
				}

				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					if (!wrapInScrollView) {
						measureChildWithMargins(clearButton, widthMeasureSpec, 0, heightMeasureSpec, 0)
					}

					val width: Int

					if (!LocaleController.isRTL) {
						width = if (searchFieldCaption?.visibility == VISIBLE) {
							measureChildWithMargins(searchFieldCaption, widthMeasureSpec, MeasureSpec.getSize(widthMeasureSpec) / 2, heightMeasureSpec, 0)
							searchFieldCaption!!.measuredWidth + AndroidUtilities.dp(4f)
						}
						else {
							0
						}

						val minWidth = MeasureSpec.getSize(widthMeasureSpec)

						ignoreRequestLayout = true

						measureChildWithMargins(searchFilterLayout, widthMeasureSpec, width, heightMeasureSpec, 0)

						val filterWidth = if (searchFilterLayout!!.visibility == VISIBLE) searchFilterLayout!!.measuredWidth else 0

						measureChildWithMargins(searchField, widthMeasureSpec, width + filterWidth, heightMeasureSpec, 0)

						ignoreRequestLayout = false

						setMeasuredDimension(max(filterWidth + searchField!!.measuredWidth, minWidth), MeasureSpec.getSize(heightMeasureSpec))
					}
					else {
						width = if (searchFieldCaption!!.visibility == VISIBLE) {
							measureChildWithMargins(searchFieldCaption, widthMeasureSpec, MeasureSpec.getSize(widthMeasureSpec) / 2, heightMeasureSpec, 0)
							searchFieldCaption!!.measuredWidth + AndroidUtilities.dp(4f)
						}
						else {
							0
						}

						val minWidth = MeasureSpec.getSize(widthMeasureSpec)

						ignoreRequestLayout = true

						measureChildWithMargins(searchFilterLayout, widthMeasureSpec, width, heightMeasureSpec, 0)

						val filterWidth = if (searchFilterLayout!!.visibility == VISIBLE) searchFilterLayout!!.measuredWidth else 0

						measureChildWithMargins(searchField, MeasureSpec.makeMeasureSpec(minWidth - AndroidUtilities.dp(12f), MeasureSpec.UNSPECIFIED), width + filterWidth, heightMeasureSpec, 0)

						ignoreRequestLayout = false

						setMeasuredDimension(max(filterWidth + searchField!!.measuredWidth, minWidth), MeasureSpec.getSize(heightMeasureSpec))
					}
				}

				override fun requestLayout() {
					if (ignoreRequestLayout) {
						return
					}

					super.requestLayout()
				}

				override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
					super.onLayout(changed, left, top, right, bottom)

					var x = if (LocaleController.isRTL) {
						0
					}
					else {
						if (searchFieldCaption?.visibility == VISIBLE) {
							searchFieldCaption!!.measuredWidth + AndroidUtilities.dp(4f)
						}
						else {
							0
						}
					}

					if (searchFilterLayout?.visibility == VISIBLE) {
						x += searchFilterLayout!!.measuredWidth
					}

					searchField?.layout(x, searchField!!.top, x + searchField!!.measuredWidth, searchField!!.bottom)
				}
			}

			searchContainer?.clipChildren = false

			wrappedSearchFrameLayout = null

			if (wrapInScrollView) {
				wrappedSearchFrameLayout = FrameLayout(context)

				val horizontalScrollView: HorizontalScrollView = object : HorizontalScrollView(context) {
					var isDragging = false

					override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
						checkDrag(ev)
						return super.onInterceptTouchEvent(ev)
					}

					@SuppressLint("ClickableViewAccessibility")
					override fun onTouchEvent(ev: MotionEvent): Boolean {
						checkDrag(ev)
						return super.onTouchEvent(ev)
					}

					private fun checkDrag(ev: MotionEvent) {
						if (ev.action == MotionEvent.ACTION_DOWN) {
							isDragging = true
						}
						else if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
							isDragging = false
						}
					}

					override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
						if (!isDragging) {
							return
						}

						super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
					}
				}

				horizontalScrollView.addView(searchContainer, createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0))
				horizontalScrollView.isHorizontalScrollBarEnabled = false
				horizontalScrollView.clipChildren = false

				wrappedSearchFrameLayout?.addView(horizontalScrollView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), 0, 0f, 0f, 48f, 0f))

				parentMenu.addView(wrappedSearchFrameLayout, 0, createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 0, 0, 0, 0))
			}
			else {
				parentMenu.addView(searchContainer, 0, createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 6, 0, 0, 0))
			}

			searchContainer?.gone()

			searchFieldCaption = TextView(context)
			searchFieldCaption?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
			searchFieldCaption?.setTextColor(context.getColor(R.color.text))
			searchFieldCaption?.isSingleLine = true
			searchFieldCaption?.ellipsize = TextUtils.TruncateAt.END
			searchFieldCaption?.gone()
			searchFieldCaption?.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

			searchField = object : EditTextBoldCursor(context) {
				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					super.onMeasure(widthMeasureSpec, heightMeasureSpec)
					val minWidth = MeasureSpec.getSize(widthMeasureSpec)
					setMeasuredDimension(max(minWidth, measuredWidth) + AndroidUtilities.dp(3f), measuredHeight)
				}

				override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
					if (keyCode == KeyEvent.KEYCODE_DEL && searchField!!.length() == 0 && (searchFieldCaption!!.visibility == VISIBLE && searchFieldCaption!!.length() > 0 || hasRemovableFilters())) {
						if (hasRemovableFilters()) {
							val filterToRemove = currentSearchFilters[currentSearchFilters.size - 1]
							listener?.onSearchFilterCleared(filterToRemove)
							removeSearchFilter(filterToRemove)
						}
						else {
							clearButton?.callOnClick()
						}

						return true
					}

					return super.onKeyDown(keyCode, event)
				}

				@SuppressLint("ClickableViewAccessibility")
				override fun onTouchEvent(event: MotionEvent): Boolean {
					val result = super.onTouchEvent(event)

					if (event.action == MotionEvent.ACTION_UP) { //hack to fix android bug with not opening keyboard
						if (!AndroidUtilities.showKeyboard(this)) {
							clearFocus()
							requestFocus()
						}
					}

					return result
				}
			}

			searchField?.isScrollContainer = false
			searchField?.setCursorWidth(1.5f)
			searchField?.setCursorColor(context.getColor(R.color.text))
			searchField?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
			searchField?.setHintTextColor(context.getColor(R.color.hint))
			searchField?.setTextColor(context.getColor(R.color.text))
			searchField?.isSingleLine = true
			searchField?.setBackgroundResource(0)
			searchField?.setPadding(0, 0, 0, 0)

			val inputType = searchField!!.inputType or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS

			searchField?.inputType = inputType

			searchField?.setOnEditorActionListener { _, _, event ->
				if (event != null && (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_SEARCH || event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
					AndroidUtilities.hideKeyboard(searchField)
					listener?.onSearchPressed(searchField!!)
				}

				false
			}

			searchField?.addTextChangedListener(object : TextWatcher {
				override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
					// unused
				}

				override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
					if (ignoreOnTextChange) {
						ignoreOnTextChange = false
						return
					}

					listener?.onTextChanged(searchField!!)

					checkClearButton()

					if (currentSearchFilters.isNotEmpty()) {
						if (!searchField?.text.isNullOrEmpty() && selectedFilterIndex >= 0) {
							selectedFilterIndex = -1
							onFiltersChanged()
						}
					}
				}

				override fun afterTextChanged(s: Editable) {
					// unused
				}
			})

			searchField?.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_SEARCH
			searchField?.setTextIsSelectable(false)
			searchField?.highlightColor = context.getColor(R.color.totals_blue_text)
			searchField?.setHandlesColor(context.getColor(R.color.brand))

			searchFilterLayout = LinearLayout(context)
			searchFilterLayout?.orientation = LinearLayout.HORIZONTAL
			searchFilterLayout?.visible()

			if (!LocaleController.isRTL) {
				searchContainer?.addView(searchFieldCaption, createFrame(LayoutHelper.WRAP_CONTENT, 36f, Gravity.CENTER_VERTICAL or Gravity.LEFT, 0f, 5.5f, 0f, 0f))
				searchContainer?.addView(searchField, createFrame(LayoutHelper.WRAP_CONTENT, 36f, Gravity.CENTER_VERTICAL, 6f, 0f, 48f, 0f))
				searchContainer?.addView(searchFilterLayout, createFrame(LayoutHelper.WRAP_CONTENT, 32f, Gravity.CENTER_VERTICAL, 0f, 0f, 48f, 0f))
			}
			else {
				searchContainer?.addView(searchFilterLayout, createFrame(LayoutHelper.WRAP_CONTENT, 32f, Gravity.CENTER_VERTICAL, 0f, 0f, 48f, 0f))
				searchContainer?.addView(searchField, createFrame(LayoutHelper.WRAP_CONTENT, 36f, Gravity.CENTER_VERTICAL, 0f, 0f, (if (wrapInScrollView) 0 else 48).toFloat(), 0f))
				searchContainer?.addView(searchFieldCaption, createFrame(LayoutHelper.WRAP_CONTENT, 36f, Gravity.CENTER_VERTICAL or Gravity.RIGHT, 0f, 5.5f, 48f, 0f))
			}

			searchFilterLayout?.clipChildren = false

			clearButton = object : AppCompatImageView(context) {
				override fun onDetachedFromWindow() {
					super.onDetachedFromWindow()

					clearAnimation()

					if (tag == null) {
						clearButton?.invisible()
						clearButton?.alpha = 0.0f
						clearButton?.rotation = 45f
						clearButton?.scaleX = 0.0f
						clearButton?.scaleY = 0.0f
					}
					else {
						clearButton?.visible() // MARK: check if this is correct - this line was missing originally
						clearButton?.alpha = 1.0f
						clearButton?.rotation = 0f
						clearButton?.scaleX = 1.0f
						clearButton?.scaleY = 1.0f
					}
				}
			}

			clearButton?.setImageDrawable(object : CloseProgressDrawable2() {
				public override fun getCurrentColor(): Int {
					return parentMenu.parentActionBar?.itemsColor ?: 0
				}
			}.also {
				progressDrawable = it
			})

			clearButton?.scaleType = ImageView.ScaleType.CENTER
			clearButton?.alpha = 0.0f
			clearButton?.rotation = 45f
			clearButton?.scaleX = 0.0f
			clearButton?.scaleY = 0.0f

			clearButton?.setOnClickListener {
				if (searchField?.length() != 0) {
					searchField?.setText("")
				}
				else if (hasRemovableFilters()) {
					searchField?.hideActionMode()

					for (i in currentSearchFilters.indices) {
						if (currentSearchFilters[i].removable) {
							listener?.onSearchFilterCleared(currentSearchFilters[i])
						}
					}

					clearSearchFilters()
				}
				else if (searchFieldCaption?.visibility == VISIBLE) {
					searchFieldCaption?.gone()
					listener?.onCaptionCleared()
				}

				searchField?.requestFocus()

				AndroidUtilities.showKeyboard(searchField)
			}

			clearButton?.contentDescription = context.getString(R.string.ClearButton)

			if (wrapInScrollView) {
				wrappedSearchFrameLayout?.addView(clearButton, createFrame(48, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL or Gravity.RIGHT))
			}
			else {
				searchContainer?.addView(clearButton, createFrame(48, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL or Gravity.RIGHT))
			}
		}

		isSearchField = value

		return this
	}

	override fun setOnClickListener(l: OnClickListener?) {
		super.setOnClickListener(l.also { onClickListener = it })
	}

	private fun checkClearButton() {
		if (clearButton != null) {
			if (!hasRemovableFilters() && searchField?.text.isNullOrEmpty() && (listener == null || !listener!!.forceShowClear()) && (searchFieldCaption?.visibility != VISIBLE)) {
				if (clearButton?.tag != null) {
					clearButton?.tag = null
					clearButton?.clearAnimation()

					if (animateClear) {
						clearButton?.animate()?.setInterpolator(DecelerateInterpolator())?.alpha(0.0f)?.setDuration(180)?.scaleY(0.0f)?.scaleX(0.0f)?.rotation(45f)?.withEndAction { clearButton?.invisible() }?.start()
					}
					else {
						clearButton?.alpha = 0.0f
						clearButton?.rotation = 45f
						clearButton?.scaleX = 0.0f
						clearButton?.scaleY = 0.0f
						clearButton?.invisible()

						animateClear = true
					}
				}
			}
			else {
				if (clearButton?.tag == null) {
					clearButton?.tag = 1
					clearButton?.clearAnimation()
					clearButton?.visible()

					if (animateClear) {
						clearButton?.animate()?.setInterpolator(DecelerateInterpolator())?.alpha(1.0f)?.setDuration(180)?.scaleY(1.0f)?.scaleX(1.0f)?.rotation(0f)?.start()
					}
					else {
						clearButton?.alpha = 1.0f
						clearButton?.rotation = 0f
						clearButton?.scaleX = 1.0f
						clearButton?.scaleY = 1.0f

						animateClear = true
					}
				}
			}
		}
	}

	private fun hasRemovableFilters(): Boolean {
		if (currentSearchFilters.isEmpty()) {
			return false
		}

		for (i in currentSearchFilters.indices) {
			if (currentSearchFilters[i].removable) {
				return true
			}
		}

		return false
	}

	fun setShowSearchProgress(show: Boolean) {
		if (show) {
			progressDrawable?.startAnimation()
		}
		else {
			progressDrawable?.stopAnimation()
		}
	}

	fun setSearchFieldCaption(caption: CharSequence?) {
		if (caption.isNullOrEmpty()) {
			searchFieldCaption?.gone()
		}
		else {
			searchFieldCaption?.visible()
			searchFieldCaption?.text = caption
		}
	}

	fun setIgnoreOnTextChange() {
		ignoreOnTextChange = true
	}

	fun clearSearchText() {
		searchField?.setText("")
	}

	fun setActionBarMenuItemSearchListener(actionBarMenuItemSearchListener: ActionBarMenuItemSearchListener?): ActionBarMenuItem {
		listener = actionBarMenuItemSearchListener
		return this
	}

	fun setAllowCloseAnimation(value: Boolean): ActionBarMenuItem {
		allowCloseAnimation = value
		return this
	}

	fun setPopupAnimationEnabled(value: Boolean) {
		popupWindow?.setAnimationEnabled(value)
		animationEnabled = value
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)

		if (popupWindow?.isShowing == true) {
			updateOrShowPopup(show = false, update = true)
		}

		listener?.onLayout(left, top, right, bottom)
	}

	fun setAdditionalYOffset(value: Int) {
		additionalYOffset = value
	}

	fun setAdditionalXOffset(value: Int) {
		additionalXOffset = value
	}

	fun forceUpdatePopupPosition() {
		if (popupWindow?.isShowing != true) {
			return
		}

		popupLayout?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - AndroidUtilities.dp(40f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, MeasureSpec.AT_MOST))

		updateOrShowPopup(show = true, update = true)
	}

	private fun updateOrShowPopup(show: Boolean, update: Boolean) {
		var offsetY = if (parentMenu != null) {
			-parentMenu.parentActionBar!!.measuredHeight + parentMenu.top + parentMenu.paddingTop
		}
		else {
			val scaleY = scaleY
			-(measuredHeight * scaleY - (if (subMenuOpenSide != 2) translationY else 0f) / scaleY).toInt() + additionalYOffset
		}

		offsetY += yOffset

		if (show) {
			popupLayout?.scrollToTop()
		}

		val fromView = showSubMenuFrom ?: this

		if (parentMenu != null) {
			val parent: View = parentMenu.parentActionBar!!

			if (subMenuOpenSide == 0) {
				if (show) {
					popupWindow?.showAsDropDown(parent, fromView.left + parentMenu.left + fromView.measuredWidth - popupWindow!!.contentView.measuredWidth + translationX.toInt(), offsetY)
				}

				if (update) {
					popupWindow?.update(parent, fromView.left + parentMenu.left + fromView.measuredWidth - popupWindow!!.contentView.measuredWidth + translationX.toInt(), offsetY, -1, -1)
				}
			}
			else {
				if (show) {
					if (forceSmoothKeyboard) {
						popupWindow?.showAtLocation(parent, Gravity.LEFT or Gravity.TOP, left - AndroidUtilities.dp(8f) + translationX.toInt(), offsetY)
					}
					else {
						popupWindow?.showAsDropDown(parent, left - AndroidUtilities.dp(8f) + translationX.toInt(), offsetY)
					}
				}

				if (update) {
					popupWindow?.update(parent, left - AndroidUtilities.dp(8f) + translationX.toInt(), offsetY, -1, -1)
				}
			}
		}
		else {
			if (subMenuOpenSide == 0) {
				if (parent != null) {
					val parent = parent as View

					if (show) {
						popupWindow?.showAsDropDown(parent, left + measuredWidth - popupWindow!!.contentView.measuredWidth + additionalXOffset, offsetY)
					}

					if (update) {
						popupWindow?.update(parent, left + measuredWidth - popupWindow!!.contentView.measuredWidth + additionalXOffset, offsetY, -1, -1)
					}
				}
			}
			else if (subMenuOpenSide == 1) {
				if (show) {
					popupWindow?.showAsDropDown(this, -AndroidUtilities.dp(8f) + additionalXOffset, offsetY)
				}

				if (update) {
					popupWindow?.update(this, -AndroidUtilities.dp(8f) + additionalXOffset, offsetY, -1, -1)
				}
			}
			else {
				if (show) {
					popupWindow?.showAsDropDown(this, measuredWidth - popupWindow!!.contentView.measuredWidth + additionalXOffset, offsetY)
				}

				if (update) {
					popupWindow?.update(this, measuredWidth - popupWindow!!.contentView.measuredWidth + additionalXOffset, offsetY, -1, -1)
				}
			}
		}
	}

	fun hideSubItem(id: Int) {
		val view = popupLayout?.findViewWithTag<View>(id)

		if (view != null && view.visibility != GONE) {
			view.gone()
		}
	}

	/**
	 * Hides this menu item if no sub-items are available
	 */
	fun checkHideMenuItem() {
		var isVisible = false

		for (i in 0 until (popupLayout?.itemsCount ?: 0)) {
			if (popupLayout?.getItemAt(i)?.visibility == VISIBLE) {
				isVisible = true
				break
			}
		}

		val v = if (isVisible) VISIBLE else GONE

		if (v != visibility) {
			visibility = v
		}
	}

	fun hideAllSubItems() {
		val popupLayout = popupLayout ?: return
		var a = 0
		val n = popupLayout.itemsCount

		while (a < n) {
			popupLayout.getItemAt(a).gone()
			a++
		}

		checkHideMenuItem()
	}

	fun isSubItemVisible(id: Int): Boolean {
		val popupLayout = popupLayout ?: return false
		val view = popupLayout.findViewWithTag<View>(id)
		return view?.visibility == VISIBLE
	}

	fun showSubItem(id: Int) {
		val popupLayout = popupLayout ?: return
		val view = popupLayout.findViewWithTag<View>(id)

		if (view != null && view.visibility != VISIBLE) {
			view.alpha = 0f
			view.animate().alpha(1f).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(150).start()
			view.visibility = VISIBLE
		}
	}

	fun requestFocusOnSearchView() {
		if (searchContainer?.width != 0 && searchField?.isFocused != true) {
			searchField?.requestFocus()
			AndroidUtilities.showKeyboard(searchField)
		}
	}

	fun clearFocusOnSearchView() {
		searchField?.clearFocus()
		AndroidUtilities.hideKeyboard(searchField)
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		if (iconView != null) {
			info.className = "android.widget.ImageButton"
		}
		else if (textView != null) {
			info.className = "android.widget.Button"

			if (info.text.isNullOrEmpty()) {
				info.text = textView?.text
			}
		}
	}

	fun updateColor() {
		searchFilterLayout?.let {
			for (i in 0 until it.childCount) {
				(it.getChildAt(i) as? SearchFilterView)?.updateColors()
			}
		}

		popupLayout?.let {
			for (i in 0 until it.itemsCount) {
				(it.getItemAt(i) as? ActionBarMenuSubItem)?.setSelectorColor(context.getColor(R.color.light_background))
			}
		}

		searchField?.let {
			it.setCursorColor(context.getColor(R.color.text))
			it.setHintTextColor(context.getColor(R.color.hint))
			it.setTextColor(context.getColor(R.color.text))
			it.highlightColor = context.getColor(R.color.avatar_background)
			it.setHandlesColor(context.getColor(R.color.brand))
		}
	}

	fun collapseSearchFilters() {
		selectedFilterIndex = -1
		onFiltersChanged()
	}

	fun setTransitionOffset(offset: Float) {
		transitionOffset = offset
		translationX = 0f
	}

	fun addColoredGap(): GapView {
		createPopupLayout()
		val gap = GapView(context, context.getColor(R.color.light_background))
		gap.setTag(R.id.fit_width_tag, 1)
		popupLayout?.addView(gap, createLinear(LayoutHelper.MATCH_PARENT, 8))
		return gap
	}

	interface ActionBarSubMenuItemDelegate {
		fun onShowSubMenu()
		fun onHideSubMenu()
	}

	fun interface ActionBarMenuItemDelegate {
		fun onItemClick(id: Int)
	}

	open class ActionBarMenuItemSearchListener {
		open fun onSearchExpand() {}

		open fun canCollapseSearch(): Boolean {
			return true
		}

		open fun onSearchCollapse() {}
		open fun onTextChanged(editText: EditText) {}
		open fun onSearchPressed(editText: EditText) {}
		open fun onCaptionCleared() {}

		open fun forceShowClear(): Boolean {
			return false
		}

		val customToggleTransition: Animator?
			get() = null

		open fun onLayout(l: Int, t: Int, r: Int, b: Int) {}
		open fun onSearchFilterCleared(filterData: MediaFilterData) {}

		open fun canToggleSearch(): Boolean {
			return true
		}
	}

	private class SearchFilterView(context: Context) : FrameLayout(context) {
		private var selectedProgress = 0f
		var avatarImageView: BackupImageView
		var closeIconView: ImageView
		var filter: MediaFilterData? = null
		var selectAnimator: ValueAnimator? = null
		private var selectedForDelete = false
		var shapeDrawable: ShapeDrawable
		var thumbDrawable: Drawable? = null
		var titleView: TextView

		fun updateColors() {
			val defaultBackgroundColor = context.getColor(R.color.dark_background)
			val selectedBackgroundColor = context.getColor(R.color.brand)
			val textDefaultColor = context.getColor(R.color.text)
			val textSelectedColor = context.getColor(R.color.brand)

			shapeDrawable.paint.color = ColorUtils.blendARGB(defaultBackgroundColor, selectedBackgroundColor, selectedProgress)
			titleView.setTextColor(ColorUtils.blendARGB(textDefaultColor, textSelectedColor, selectedProgress))

			closeIconView.setColorFilter(textSelectedColor)
			closeIconView.alpha = selectedProgress
			closeIconView.scaleX = 0.82f * selectedProgress
			closeIconView.scaleY = 0.82f * selectedProgress

			if (thumbDrawable != null) {
				Theme.setCombinedDrawableColor(thumbDrawable, context.getColor(R.color.brand), false)
				Theme.setCombinedDrawableColor(thumbDrawable, context.getColor(R.color.brand), true)
			}

			avatarImageView.alpha = 1f - selectedProgress

			if (filter?.filterType == FiltersView.FILTER_TYPE_ARCHIVE) {
				setData(filter)
			}

			invalidate()
		}

		fun setData(data: MediaFilterData?) {
			filter = data
			titleView.text = data!!.title
			thumbDrawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(32f), data.iconResFilled)

			Theme.setCombinedDrawableColor(thumbDrawable, context.getColor(R.color.brand), false)
			Theme.setCombinedDrawableColor(thumbDrawable, context.getColor(R.color.brand), true)

			if (data.filterType == FiltersView.FILTER_TYPE_CHAT) {
				if (data.chat is User) {
					val user = data.chat as User

					if (getInstance(UserConfig.selectedAccount).getCurrentUser()?.id == user.id) {
						val combinedDrawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(32f), R.drawable.chats_saved)
						combinedDrawable.setIconSize(AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))
						Theme.setCombinedDrawableColor(combinedDrawable, context.getColor(R.color.brand), false)
						Theme.setCombinedDrawableColor(combinedDrawable, context.getColor(R.color.brand), true)
						avatarImageView.setImageDrawable(combinedDrawable)
					}
					else {
						avatarImageView.imageReceiver.setRoundRadius(AndroidUtilities.dp(16f))
						avatarImageView.imageReceiver.setForUserOrChat(user, thumbDrawable)
					}
				}
				else if (data.chat is Chat) {
					val chat = data.chat as Chat
					avatarImageView.imageReceiver.setRoundRadius(AndroidUtilities.dp(16f))
					avatarImageView.imageReceiver.setForUserOrChat(chat, thumbDrawable)
				}
			}
			else if (data.filterType == FiltersView.FILTER_TYPE_ARCHIVE) {
				val combinedDrawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(32f), R.drawable.chats_archive)
				combinedDrawable.setIconSize(AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))
				Theme.setCombinedDrawableColor(combinedDrawable, context.getColor(R.color.dark_gray), false)
				Theme.setCombinedDrawableColor(combinedDrawable, context.getColor(R.color.brand), true)
				avatarImageView.setImageDrawable(combinedDrawable)
			}
			else {
				avatarImageView.setImageDrawable(thumbDrawable)
			}
		}

		fun setExpanded(expanded: Boolean) {
			if (expanded) {
				titleView.visible()
			}
			else {
				titleView.gone()
				setSelectedForDelete(false)
			}
		}

		fun getSelectedForDelete(): Boolean {
			return selectedForDelete
		}

		fun setSelectedForDelete(select: Boolean) {
			if (selectedForDelete == select) {
				return
			}

			AndroidUtilities.cancelRunOnUIThread(removeSelectionRunnable)

			selectedForDelete = select

			selectAnimator?.removeAllListeners()
			selectAnimator?.cancel()

			selectAnimator = ValueAnimator.ofFloat(selectedProgress, if (select) 1f else 0f)

			selectAnimator?.addUpdateListener {
				selectedProgress = it.animatedValue as Float
				updateColors()
			}

			selectAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					selectedProgress = if (select) 1f else 0f
					updateColors()
				}
			})

			selectAnimator?.setDuration(150)?.start()

			if (selectedForDelete) {
				AndroidUtilities.runOnUIThread(removeSelectionRunnable, 2000)
			}
		}

		var removeSelectionRunnable = Runnable {
			if (selectedForDelete) {
				setSelectedForDelete(false)
			}
		}

		init {
			avatarImageView = BackupImageView(context)

			addView(avatarImageView, createFrame(32, 32f))

			closeIconView = ImageView(context)
			closeIconView.setImageResource(R.drawable.ic_close_white)

			addView(closeIconView, createFrame(24, 24f, Gravity.CENTER_VERTICAL, 8f, 0f, 0f, 0f))

			titleView = TextView(context)
			titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)

			addView(titleView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_VERTICAL, 38f, 0f, 16f, 0f))

			shapeDrawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(28f), context.getColor(R.color.avatar_tint)) as ShapeDrawable
			background = shapeDrawable
			updateColors()
		}
	}

	companion object {
		fun checkRtl(string: String?): Boolean {
			if (string.isNullOrEmpty()) {
				return false
			}

			return string[0].code in 0x590..0x6ff
		}

		@JvmStatic
		fun addItem(windowLayout: ActionBarPopupWindowLayout, icon: Int, text: CharSequence?, needCheck: Boolean): ActionBarMenuSubItem {
			return addItem(first = false, last = false, windowLayout = windowLayout, icon = icon, text = text, needCheck = needCheck)
		}

		@JvmStatic
		fun addItem(first: Boolean, last: Boolean, windowLayout: ActionBarPopupWindowLayout, icon: Int, text: CharSequence?, needCheck: Boolean): ActionBarMenuSubItem {
			val cell = ActionBarMenuSubItem(windowLayout.context, needCheck, first, last)
			cell.setTextAndIcon(text, icon)
			cell.minimumWidth = AndroidUtilities.dp(196f)

			windowLayout.addView(cell)

			val layoutParams = cell.layoutParams as LinearLayout.LayoutParams

			if (LocaleController.isRTL) {
				layoutParams.gravity = Gravity.RIGHT
			}

			layoutParams.width = LayoutHelper.MATCH_PARENT
			layoutParams.height = AndroidUtilities.dp(48f)

			cell.layoutParams = layoutParams

			return cell
		}
	}
}
