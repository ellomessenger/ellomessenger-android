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
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.Interpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isGone
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.bot
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScrollSlidingTextTabStrip
import org.telegram.ui.DialogsActivity.DialogsActivityDelegate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DialogOrContactPickerActivity : BaseFragment() {
	private open class ViewPage(context: Context) : FrameLayout(context) {
		var parentFragment: BaseFragment? = null
		var fragmentView: FrameLayout? = null
		var actionBar: ActionBar? = null
		var listView: RecyclerListView? = null
		var listView2: RecyclerListView? = null
		var selectedType = 0
	}

	private val dialogsActivity: DialogsActivity
	private val contactsActivity: ContactsActivity
	private var searchItem: ActionBarMenuItem? = null
	private val backgroundPaint = Paint()
	private var scrollSlidingTextTabStrip: ScrollSlidingTextTabStrip? = null
	private val viewPages = arrayOfNulls<ViewPage>(2)
	private var tabsAnimation: AnimatorSet? = null
	private var tabsAnimationInProgress = false
	private var animatingForward = false
	private var backAnimation = false
	private var maximumVelocity = 0
	private var swipeBackEnabled = true

	init {
		var args = Bundle()
		args.putBoolean("onlySelect", true)
		args.putBoolean("checkCanWrite", false)
		args.putBoolean("resetDelegate", false)
		args.putInt("dialogsType", 9)

		dialogsActivity = DialogsActivity(args)

		dialogsActivity.setDelegate(DialogsActivityDelegate { _, dids, _, _ ->
			if (dids.isEmpty()) {
				return@DialogsActivityDelegate
			}

			val did = dids[0]

			if (!DialogObject.isUserDialog(did)) {
				return@DialogsActivityDelegate
			}

			val user = messagesController.getUser(did)

			showBlockAlert(user)
		})

		dialogsActivity.onFragmentCreate()

		args = Bundle()
		args.putBoolean("onlyUsers", true)
		args.putBoolean("destroyAfterSelect", true)
		args.putBoolean("returnAsResult", true)
		args.putBoolean("disableSections", true)
		args.putBoolean("needFinishFragment", false)
		args.putBoolean("resetDelegate", false)
		args.putBoolean("allowSelf", false)

		contactsActivity = ContactsActivity(args)

		contactsActivity.setDelegate { user, _, _ ->
			showBlockAlert(user)
		}

		contactsActivity.onFragmentCreate()
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.BlockUserMultiTitle))

		if (AndroidUtilities.isTablet()) {
			actionBar?.occupyStatusBar = false
		}

		actionBar?.setExtraHeight(AndroidUtilities.dp(44f))
		actionBar?.setAllowOverlayTitle(false)
		actionBar?.setAddToContainer(false)
		actionBar?.setClipContent(true)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		hasOwnBackground = true

		val menu = actionBar?.createMenu()

		searchItem = menu?.addItem(SEARCH_BUTTON, R.drawable.ic_search_menu)?.setIsSearchField(true)?.setActionBarMenuItemSearchListener(object : ActionBarMenuItemSearchListener() {
			override fun onSearchExpand() {
				dialogsActivity.actionBar?.openSearchField("", false)
				contactsActivity.actionBar?.openSearchField("", false)
				searchItem?.searchField?.requestFocus()
			}

			override fun onSearchCollapse() {
				dialogsActivity.actionBar?.closeSearchField(false)
				contactsActivity.actionBar?.closeSearchField(false)
			}

			override fun onTextChanged(editText: EditText) {
				dialogsActivity.actionBar?.setSearchFieldText(editText.text.toString())
				contactsActivity.actionBar?.setSearchFieldText(editText.text.toString())
			}
		})

		searchItem?.setSearchFieldHint(context.getString(R.string.Search))

		scrollSlidingTextTabStrip = ScrollSlidingTextTabStrip(context)
		scrollSlidingTextTabStrip?.setUseSameWidth(true)

		actionBar?.addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT or Gravity.BOTTOM))

		scrollSlidingTextTabStrip?.setDelegate(object : ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate {
			override fun onSamePageSelected() {
				// unused
			}

			override fun onPageSelected(page: Int, forward: Boolean) {
				if (viewPages[0]!!.selectedType == page) {
					return
				}

				swipeBackEnabled = (page == scrollSlidingTextTabStrip?.firstTabId)

				viewPages[1]?.selectedType = page
				viewPages[1]?.visible()

				switchToCurrentSelectedMode(true)

				animatingForward = forward
			}

			override fun onPageScrolled(progress: Float) {
				if (progress == 1f && viewPages[1]?.visibility != View.VISIBLE) {
					return
				}

				if (animatingForward) {
					viewPages[0]?.translationX = -progress * viewPages[0]!!.measuredWidth
					viewPages[1]?.translationX = viewPages[0]!!.measuredWidth - progress * viewPages[0]!!.measuredWidth
				}
				else {
					viewPages[0]?.translationX = progress * viewPages[0]!!.measuredWidth
					viewPages[1]?.translationX = progress * viewPages[0]!!.measuredWidth - viewPages[0]!!.measuredWidth
				}

				if (progress == 1f) {
					val tempPage = viewPages[0]

					viewPages[0] = viewPages[1]

					viewPages[1] = tempPage
					viewPages[1]?.gone()
				}
			}
		})

		val configuration = ViewConfiguration.get(context)

		maximumVelocity = configuration.scaledMaximumFlingVelocity

		val frameLayout = object : FrameLayout(context) {
			private var startedTrackingPointerId = 0
			private var startedTracking = false
			private var maybeStartTracking = false
			private var startedTrackingX = 0
			private var startedTrackingY = 0
			private var velocityTracker: VelocityTracker? = null
			private var globalIgnoreLayout = false

			private fun prepareForMoving(ev: MotionEvent, forward: Boolean): Boolean {
				val id = scrollSlidingTextTabStrip!!.getNextPageId(forward)

				if (id < 0) {
					return false
				}

				parent.requestDisallowInterceptTouchEvent(true)

				maybeStartTracking = false
				startedTracking = true
				startedTrackingX = ev.x.toInt()
				actionBar?.isEnabled = false
				scrollSlidingTextTabStrip?.isEnabled = false

				viewPages[1]!!.selectedType = id
				viewPages[1]!!.visibility = VISIBLE

				animatingForward = forward

				switchToCurrentSelectedMode(true)

				if (forward) {
					viewPages[1]!!.translationX = viewPages[0]!!.measuredWidth.toFloat()
				}
				else {
					viewPages[1]!!.translationX = -viewPages[0]!!.measuredWidth.toFloat()
				}

				return true
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val widthSize = MeasureSpec.getSize(widthMeasureSpec)
				val heightSize = MeasureSpec.getSize(heightMeasureSpec)

				setMeasuredDimension(widthSize, heightSize)
				measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0)

				val actionBarHeight = actionBar!!.measuredHeight

				globalIgnoreLayout = true

				for (a in viewPages.indices) {
					if (viewPages[a] == null) {
						continue
					}

					if (viewPages[a]!!.listView != null) {
						viewPages[a]!!.listView!!.setPadding(0, actionBarHeight, 0, 0)
					}

					if (viewPages[a]!!.listView2 != null) {
						viewPages[a]!!.listView2!!.setPadding(0, actionBarHeight, 0, 0)
					}
				}

				globalIgnoreLayout = false

				val childCount = childCount

				for (i in 0 until childCount) {
					val child = getChildAt(i)

					if (child == null || child.isGone || child === actionBar) {
						continue
					}

					measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
				}
			}

			override fun dispatchDraw(canvas: Canvas) {
				super.dispatchDraw(canvas)
				parentLayout?.drawHeaderShadow(canvas, actionBar!!.measuredHeight + actionBar!!.translationY.toInt())
			}

			override fun requestLayout() {
				if (globalIgnoreLayout) {
					return
				}

				super.requestLayout()
			}

			fun checkTabsAnimationInProgress(): Boolean {
				if (tabsAnimationInProgress) {
					var cancel = false

					if (backAnimation) {
						if (abs(viewPages[0]!!.translationX) < 1) {
							viewPages[0]!!.translationX = 0f
							viewPages[1]!!.translationX = (viewPages[0]!!.measuredWidth * if (animatingForward) 1 else -1).toFloat()

							cancel = true
						}
					}
					else if (abs(viewPages[1]!!.translationX) < 1) {
						viewPages[0]!!.translationX = (viewPages[0]!!.measuredWidth * if (animatingForward) -1 else 1).toFloat()
						viewPages[1]!!.translationX = 0f

						cancel = true
					}

					if (cancel) {
						tabsAnimation?.cancel()
						tabsAnimation = null
						tabsAnimationInProgress = false
					}

					return tabsAnimationInProgress
				}

				return false
			}

			override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
				return checkTabsAnimationInProgress() || scrollSlidingTextTabStrip!!.isAnimatingIndicator || onTouchEvent(ev)
			}

			override fun onDraw(canvas: Canvas) {
				backgroundPaint.color = context.getColor(R.color.background)
				canvas.drawRect(0f, actionBar!!.measuredHeight + actionBar!!.translationY, measuredWidth.toFloat(), measuredHeight.toFloat(), backgroundPaint)
			}

			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(ev: MotionEvent?): Boolean {
				if (!parentLayout!!.checkTransitionAnimation() && !checkTabsAnimationInProgress()) {
					if (ev != null) {
						if (velocityTracker == null) {
							velocityTracker = VelocityTracker.obtain()
						}

						velocityTracker?.addMovement(ev)
					}

					if (ev != null && ev.action == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
						startedTrackingPointerId = ev.getPointerId(0)
						maybeStartTracking = true
						startedTrackingX = ev.x.toInt()
						startedTrackingY = ev.y.toInt()
						velocityTracker?.clear()
					}
					else if (ev != null && ev.action == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
						val dx = (ev.x - startedTrackingX).toInt()
						val dy = abs(ev.y.toInt() - startedTrackingY)

						if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
							if (!prepareForMoving(ev, dx < 0)) {
								maybeStartTracking = true
								startedTracking = false

								viewPages[0]!!.translationX = 0f
								viewPages[1]!!.translationX = (if (animatingForward) viewPages[0]!!.measuredWidth else -viewPages[0]!!.measuredWidth).toFloat()

								scrollSlidingTextTabStrip?.selectTabWithId(viewPages[1]!!.selectedType, 0f)
							}
						}

						if (maybeStartTracking && !startedTracking) {
							val touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true)

							if (abs(dx) >= touchSlop && abs(dx) > dy) {
								prepareForMoving(ev, dx < 0)
							}
						}
						else if (startedTracking) {
							viewPages[0]!!.translationX = dx.toFloat()

							if (animatingForward) {
								viewPages[1]!!.translationX = (viewPages[0]!!.measuredWidth + dx).toFloat()
							}
							else {
								viewPages[1]!!.translationX = (dx - viewPages[0]!!.measuredWidth).toFloat()
							}

							val scrollProgress = abs(dx) / viewPages[0]!!.measuredWidth.toFloat()

							scrollSlidingTextTabStrip?.selectTabWithId(viewPages[1]!!.selectedType, scrollProgress)
						}
					}
					else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.action == MotionEvent.ACTION_CANCEL || ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_POINTER_UP)) {
						velocityTracker?.computeCurrentVelocity(1000, maximumVelocity.toFloat())

						var velX: Float
						val velY: Float

						if (ev != null && ev.action != MotionEvent.ACTION_CANCEL) {
							velX = velocityTracker!!.xVelocity
							velY = velocityTracker!!.yVelocity

							if (!startedTracking) {
								if (abs(velX) >= 3000 && abs(velX) > abs(velY)) {
									prepareForMoving(ev, velX < 0)
								}
							}
						}
						else {
							velX = 0f
							velY = 0f
						}

						if (startedTracking) {
							val x = viewPages[0]!!.x

							tabsAnimation = AnimatorSet()
							backAnimation = abs(x) < viewPages[0]!!.measuredWidth / 3.0f && (abs(velX) < 3500 || abs(velX) < abs(velY))

							val dx: Float

							if (backAnimation) {
								dx = abs(x)

								if (animatingForward) {
									tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages[0], TRANSLATION_X, 0f), ObjectAnimator.ofFloat(viewPages[1], TRANSLATION_X, viewPages[1]!!.measuredWidth.toFloat()))
								}
								else {
									tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages[0], TRANSLATION_X, 0f), ObjectAnimator.ofFloat(viewPages[1], TRANSLATION_X, -viewPages[1]!!.measuredWidth.toFloat()))
								}
							}
							else {
								dx = viewPages[0]!!.measuredWidth - abs(x)

								if (animatingForward) {
									tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages[0], TRANSLATION_X, -viewPages[0]!!.measuredWidth.toFloat()), ObjectAnimator.ofFloat(viewPages[1], TRANSLATION_X, 0f))
								}
								else {
									tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages[0], TRANSLATION_X, viewPages[0]!!.measuredWidth.toFloat()), ObjectAnimator.ofFloat(viewPages[1], TRANSLATION_X, 0f))
								}
							}

							tabsAnimation?.interpolator = interpolator

							val width = measuredWidth
							val halfWidth = width / 2
							val distanceRatio = min(1.0f, 1.0f * dx / width.toFloat())
							val distance = halfWidth.toFloat() + halfWidth.toFloat() * AndroidUtilities.distanceInfluenceForSnapDuration(distanceRatio)

							velX = abs(velX)

							var duration = if (velX > 0) {
								4 * (1000.0f * abs(distance / velX)).roundToInt()
							}
							else {
								val pageDelta = dx / measuredWidth
								((pageDelta + 1.0f) * 100.0f).toInt()
							}

							duration = max(150, min(duration, 600))

							tabsAnimation?.duration = duration.toLong()

							tabsAnimation?.addListener(object : AnimatorListenerAdapter() {
								override fun onAnimationEnd(animator: Animator) {
									tabsAnimation = null

									if (backAnimation) {
										viewPages[1]!!.visibility = GONE
									}
									else {
										val tempPage = viewPages[0]

										viewPages[0] = viewPages[1]
										viewPages[1] = tempPage
										viewPages[1]!!.visibility = GONE

										swipeBackEnabled = viewPages[0]!!.selectedType == scrollSlidingTextTabStrip!!.firstTabId

										scrollSlidingTextTabStrip?.selectTabWithId(viewPages[0]!!.selectedType, 1.0f)
									}

									tabsAnimationInProgress = false
									maybeStartTracking = false
									startedTracking = false
									actionBar?.isEnabled = true
									scrollSlidingTextTabStrip?.isEnabled = true
								}
							})

							tabsAnimation?.start()

							tabsAnimationInProgress = true
							startedTracking = false
						}
						else {
							maybeStartTracking = false
							actionBar?.isEnabled = true
							scrollSlidingTextTabStrip?.isEnabled = true
						}

						velocityTracker?.recycle()
						velocityTracker = null
					}

					return startedTracking
				}

				return false
			}
		}

		fragmentView = frameLayout

		frameLayout.setWillNotDraw(false)

		dialogsActivity.setParentFragment(this)
		contactsActivity.setParentFragment(this)

		for (a in viewPages.indices) {
			viewPages[a] = object : ViewPage(context) {
				override fun setTranslationX(translationX: Float) {
					super.setTranslationX(translationX)

					if (tabsAnimationInProgress) {
						if (viewPages[0] === this) {
							val scrollProgress = abs(viewPages[0]!!.translationX) / viewPages[0]!!.measuredWidth.toFloat()
							scrollSlidingTextTabStrip!!.selectTabWithId(viewPages[1]!!.selectedType, scrollProgress)
						}
					}
				}
			}

			frameLayout.addView(viewPages[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			if (a == 0) {
				viewPages[a]!!.parentFragment = dialogsActivity
				viewPages[a]!!.listView = dialogsActivity.listView
				viewPages[a]!!.listView2 = dialogsActivity.searchListView
			}
			else if (a == 1) {
				viewPages[a]!!.parentFragment = contactsActivity
				viewPages[a]!!.listView = contactsActivity.listView
				viewPages[a]!!.visibility = View.GONE
			}

			viewPages[a]!!.listView!!.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING)
			viewPages[a]!!.fragmentView = viewPages[a]!!.parentFragment!!.fragmentView as FrameLayout?
			viewPages[a]!!.actionBar = viewPages[a]!!.parentFragment!!.actionBar
			viewPages[a]!!.addView(viewPages[a]!!.fragmentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
			viewPages[a]!!.addView(viewPages[a]!!.actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
			viewPages[a]!!.actionBar!!.visibility = View.GONE

			for (i in 0..1) {
				val listView = (if (i == 0) viewPages[a]?.listView else viewPages[a]?.listView2) ?: continue
				listView.clipToPadding = false

				val onScrollListener = listView.getOnScrollListener()

				listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
					override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
						onScrollListener!!.onScrollStateChanged(recyclerView, newState)

						if (newState != RecyclerView.SCROLL_STATE_DRAGGING) {
							val scrollY = -actionBar!!.translationY.toInt()
							val actionBarHeight = ActionBar.getCurrentActionBarHeight()

							if (scrollY != 0 && scrollY != actionBarHeight) {
								if (scrollY < actionBarHeight / 2) {
									viewPages[0]!!.listView!!.smoothScrollBy(0, -scrollY)

									if (viewPages[0]!!.listView2 != null) {
										viewPages[0]!!.listView2!!.smoothScrollBy(0, -scrollY)
									}
								}
								else {
									viewPages[0]!!.listView!!.smoothScrollBy(0, actionBarHeight - scrollY)
									if (viewPages[0]!!.listView2 != null) {
										viewPages[0]!!.listView2!!.smoothScrollBy(0, actionBarHeight - scrollY)
									}
								}
							}
						}
					}

					override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
						onScrollListener?.onScrolled(recyclerView, dx, dy)

						if (recyclerView === viewPages[0]?.listView || recyclerView === viewPages[0]?.listView2) {
							val currentTranslation = actionBar!!.translationY
							var newTranslation = currentTranslation - dy

							if (newTranslation < -ActionBar.getCurrentActionBarHeight()) {
								newTranslation = -ActionBar.getCurrentActionBarHeight().toFloat()
							}
							else if (newTranslation > 0) {
								newTranslation = 0f
							}

							if (newTranslation != currentTranslation) {
								setScrollY(newTranslation)
							}
						}
					}
				})
			}
		}

		frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

		updateTabs()
		switchToCurrentSelectedMode(false)

		swipeBackEnabled = (scrollSlidingTextTabStrip?.currentTabId == scrollSlidingTextTabStrip?.firstTabId)

		return fragmentView
	}

	override fun onResume() {
		super.onResume()
		dialogsActivity.onResume()
		contactsActivity.onResume()
	}

	override fun onPause() {
		super.onPause()
		dialogsActivity.onPause()
		contactsActivity.onPause()
	}

	override fun isSwipeBackEnabled(event: MotionEvent): Boolean {
		return swipeBackEnabled
	}

	override fun onFragmentDestroy() {
		dialogsActivity.onFragmentDestroy()
		contactsActivity.onFragmentDestroy()
		super.onFragmentDestroy()
	}

	private fun setScrollY(value: Float) {
		actionBar?.translationY = value

		for (a in viewPages.indices) {
			viewPages[a]?.listView?.setPinnedSectionOffsetY(value.toInt())
			viewPages[a]?.listView2?.setPinnedSectionOffsetY(value.toInt())
		}

		fragmentView?.invalidate()
	}

	private fun showBlockAlert(user: User?) {
		val parentActivity = parentActivity ?: return

		if (user == null) {
			return
		}

		if (user.bot) {
			Toast.makeText(context, context?.getString(R.string.system_bots_block_alert), Toast.LENGTH_SHORT).show()
			return
		}

		val builder = AlertDialog.Builder(parentActivity)
		builder.setTitle(parentActivity.getString(R.string.BlockUser))
		builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureBlockContact2", R.string.AreYouSureBlockContact2, ContactsController.formatName(user.firstName, user.lastName))))

		builder.setPositiveButton(parentActivity.getString(R.string.BlockContact)) { _, _ ->
			if (MessagesController.isSupportUser(user)) {
				AlertsCreator.showSimpleToast(this@DialogOrContactPickerActivity, parentActivity.getString(R.string.ErrorOccurred))
			}
			else {
				MessagesController.getInstance(currentAccount).blockPeer(user.id)
				AlertsCreator.showSimpleToast(this@DialogOrContactPickerActivity, parentActivity.getString(R.string.UserBlocked))
			}

			finishFragment()
		}

		builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

		val dialog = builder.create()
		showDialog(dialog)

		val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
		button?.setTextColor(parentActivity.getColor(R.color.purple))
	}

	private fun updateTabs() {
		val scrollSlidingTextTabStrip = scrollSlidingTextTabStrip ?: return
		val context = context ?: return

		scrollSlidingTextTabStrip.addTextTab(0, context.getString(R.string.BlockUserChatsTitle))
		scrollSlidingTextTabStrip.addTextTab(1, context.getString(R.string.BlockUserContactsTitle))
		scrollSlidingTextTabStrip.visible()

		actionBar?.setExtraHeight(AndroidUtilities.dp(44f))

		val id = scrollSlidingTextTabStrip.currentTabId

		if (id >= 0) {
			viewPages[0]?.selectedType = id
		}

		scrollSlidingTextTabStrip.finishAddingTabs()
	}

	private fun switchToCurrentSelectedMode(animated: Boolean) {
		for (a in viewPages.indices) {
			viewPages[a]!!.listView!!.stopScroll()

			if (viewPages[a]!!.listView2 != null) {
				viewPages[a]!!.listView2!!.stopScroll()
			}
		}

		val a = if (animated) 1 else 0

		for (i in 0..1) {
			val listView = (if (i == 0) viewPages[a]!!.listView else viewPages[a]!!.listView2) ?: continue
			listView.setPinnedHeaderShadowDrawable(null)

			if (actionBar?.translationY != 0f) {
				val layoutManager = listView.layoutManager as? LinearLayoutManager
				layoutManager?.scrollToPositionWithOffset(0, actionBar!!.translationY.toInt())
			}
		}
	}

	companion object {
		private const val SEARCH_BUTTON = 0

		private val interpolator = Interpolator {
			var t = it
			--t
			t * t * t * t * t + 1.0f
		}
	}
}
