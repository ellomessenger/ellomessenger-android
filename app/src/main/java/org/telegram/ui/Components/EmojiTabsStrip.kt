/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils
import androidx.core.view.children
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TLStickerSet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.EmojiView.EmojiPack
import org.telegram.ui.Components.Premium.PremiumLockIconView
import java.util.Objects
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.withTranslation

open class EmojiTabsStrip(context: Context, includeStandard: Boolean, private val includeAnimated: Boolean, private val onSettingsOpenRunnable: Runnable?) : ScrollableHorizontalScrollView(context) {
	private val recentDrawableId = R.drawable.msg_emoji_recent
	private val removingViews = HashMap<View?, Rect>()
	private val settingsDrawableId = R.drawable.smiles_tab_settings
	private var animatedEmojiCacheType = AnimatedEmojiDrawable.CACHE_TYPE_TAB_STRIP
	private var emojiTabs: EmojiTabsView? = null
	private var recentFirstChange = true
	private var recentIsShown = true
	private var selectAnimationT = 0f
	private var selectAnimator: ValueAnimator? = null
	private var selectT = 0f
	private var selected = 0
	private var settingsTab: EmojiTabButton? = null
	private var wasDrawn = false
	private var wasIndex = 0
	var animateAppear = true
	var contentView: LinearLayout
	var recentTab: EmojiTabButton? = null
	var updateButtonDrawables = true
	var first = true
	private var appearAnimation: ValueAnimator? = null
	private var appearCount = 0
	private var paddingLeftDp = (5 + 6).toFloat()
	private val emojipackTabs = mutableListOf<EmojiTabButton>()

	fun showRecent(show: Boolean) {
		if (recentIsShown == show) {
			return
		}

		recentIsShown = show

		if (recentFirstChange) {
			recentTab?.setAlpha(if (show) 1f else 0f)
		}
		else {
			recentTab?.animate()?.alpha(if (show) 1f else 0f)?.setDuration(200)?.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)?.start()
		}

		if (!show && selected == 0 || show && selected == 1) {
			select(0, !recentFirstChange)
		}

		contentView.requestLayout()

		recentFirstChange = false
	}

	private fun isFreeEmojiPack(set: TLStickerSet?, documents: List<TLRPC.Document>?): Boolean {
		if (set == null || documents == null) {
			return false
		}

		for (i in documents.indices) {
			if (!MessageObject.isFreeEmoji(documents[i])) {
				return false
			}
		}
		return true
	}

	private fun getThumbDocument(set: TLStickerSet?, documents: List<TLRPC.Document>?): TLRPC.Document? {
		if (set == null) {
			return null
		}

		if (documents != null) {
			for (i in documents.indices) {
				val d = documents[i]

				if (d.id == set.thumbDocumentId) {
					return d
				}
			}
		}

		return documents?.firstOrNull()
	}

	protected open fun isInstalled(pack: EmojiPack): Boolean {
		return pack.installed
	}

	protected open fun onTabCreate(button: EmojiTabButton) {
		// stub
	}

	fun updateEmojiPacks(emojiPacks: List<EmojiPack>?) {
		if (!includeAnimated) {
			return
		}

		if (first && !MediaDataController.getInstance(UserConfig.selectedAccount).areStickersLoaded(MediaDataController.TYPE_EMOJIPACKS)) {
			return
		}

		first = false

		if (emojiPacks == null) {
			return
		}

		val first = emojipackTabs.size == 0 && emojiPacks.isNotEmpty() && appearCount != emojiPacks.size && wasDrawn
		val doAppearAnimation = false // emojipackTabs.size() == 0 && emojiPacks.size() > 0 && appearCount != emojiPacks.size() && wasDrawn;

		if (appearCount != emojiPacks.size) {
			appearAnimation?.cancel()
			appearAnimation = null
		}

		appearCount = emojiPacks.size

		val isPremium = getInstance(UserConfig.selectedAccount).isPremium

		run {
			var i = 0

			while (i < emojipackTabs.size) {
				val emojipackTab = emojipackTabs.getOrNull(i)
				var pack: EmojiPack? = null

				if (emojipackTab?.id != null) {
					for (j in emojiPacks.indices) {
						val p = emojiPacks[j]
						val id = Objects.hash(p.set.id, p.featured)

						if (id == emojipackTab.id) {
							pack = p
							break
						}
					}
				}

				if (pack == null && emojipackTab != null) {
					val bounds = Rect()
					bounds.set(emojipackTab.left, emojipackTab.top, emojipackTab.right, emojipackTab.bottom)

					removingViews[emojipackTab] = bounds

					val anm = ValueAnimator.ofFloat(emojipackTab.alpha, 0f)

					anm.addUpdateListener {
						val alpha = it.getAnimatedValue() as Float

						emojipackTab.setAlpha(alpha)
						emojipackTab.scaleX = alpha
						emojipackTab.scaleY = alpha

						contentView.invalidate()
					}

					anm.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							removingViews.remove(emojipackTab)
							contentView.invalidate()
						}
					})

					anm.setDuration(200)
					anm.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
					anm.start()

					emojipackTabs.removeAt(i--)
				}

				contentView.removeView(emojipackTab)

				++i
			}
		}

		for (i in emojiPacks.indices) {
			val pack = emojiPacks[i]
			val id = Objects.hash(pack.set.id, pack.featured)
			var emojipackTab: EmojiTabButton? = null

			for (j in emojipackTabs.indices) {
				val tab = emojipackTabs.getOrNull(j)

				if (tab?.id == id) {
					emojipackTab = tab
					break
				}
			}

			val free = isFreeEmojiPack(pack.set, pack.documents)
			var drawable = emojipackTab?.drawable as? AnimatedEmojiDrawable
			val thumbDocument = getThumbDocument(pack.set, pack.documents)

			if (thumbDocument != null && (drawable == null || drawable.documentId != thumbDocument.id)) {
				drawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, animatedEmojiCacheType, thumbDocument)
			}

			if (emojipackTab == null) {
				emojipackTab = EmojiTabButton(context, drawable, free, roundSelector = false, forceSelector = false)
				emojipackTab.id = id

				drawable?.addView(emojipackTab.imageView)

				onTabCreate(emojipackTab)

				emojipackTabs.add(emojipackTab)
			}
			else if (emojipackTab.drawable !== drawable) {
				if (emojipackTab.drawable is AnimatedEmojiDrawable) {
					(emojipackTab.drawable as? AnimatedEmojiDrawable)?.removeView(emojipackTab.imageView)
				}

				emojipackTab.drawable = drawable

				drawable?.addView(emojipackTab.imageView)
			}

			if (!isPremium && !free) {
				emojipackTab.setLock(true)
			}
			else if (!isInstalled(pack)) {
				emojipackTab.setLock(false)
			}
			else {
				emojipackTab.setLock(null)
			}

			if (doAppearAnimation && !first) {
				emojipackTab.newly = false
			}
			if (emojipackTab.parent is ViewGroup) {
				(emojipackTab.parent as ViewGroup).removeView(emojipackTab)
			}

			contentView.addView(emojipackTab)
		}

		settingsTab?.let { settingsTab ->
			settingsTab.bringToFront()

			if (settingsTab.alpha < 1) {
				settingsTab.animate().alpha(1f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start()
			}
		}

		if (doAppearAnimation) {
			for (emojipackTab in emojipackTabs) {
				emojipackTab.scaleX = 0f
				emojipackTab.scaleY = 0f

			}

			appearAnimation = ValueAnimator.ofFloat(0f, 1f)

			val innerInterpolator = OvershootInterpolator(3f)

			appearAnimation?.addUpdateListener {
				if (emojipackTabs.isEmpty()) {
					return@addUpdateListener
				}

				val t = it.getAnimatedValue() as Float
				val count = emojipackTabs.size
				val dur = 1f / count * 4.5f

				for (i in 0 until count) {
					val off = i / count.toFloat() * (1f - dur)
					val scale = innerInterpolator.getInterpolation(MathUtils.clamp((t - off) / dur, 0f, 1f))

					emojipackTabs[i].scaleX = scale
					emojipackTabs[i].scaleY = scale
				}
			}

			appearAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationCancel(animation: Animator) {
					for (emojipackTab in emojipackTabs) {
						emojipackTab.scaleX = 1f
						emojipackTab.scaleY = 1f
					}
				}
			})

			appearAnimation?.setStartDelay(150)
			appearAnimation?.setDuration(emojipackTabs.size * 75L)
			appearAnimation?.interpolator = CubicBezierInterpolator.EASE_OUT
			appearAnimation?.start()
		}

		updateClickListeners()
	}

	private fun updateClickListeners() {
		var i = 0
		var j = 0

		while (i < contentView.childCount) {
			val child = contentView.getChildAt(i)

			if (child is EmojiTabsView) {
				var a = 0

				while (a < (child.contentView?.childCount ?: 0)) {
					val index = j
					child.contentView?.getChildAt(a)?.setOnClickListener { onTabClick(index) }
					++a
					++j
				}

				--j
			}
			else if (child != null) {
				val index = j
				child.setOnClickListener { onTabClick(index) }
			}

			++i
			++j
		}

		settingsTab?.setOnClickListener { onSettingsOpenRunnable?.run() }
	}

	protected open fun onTabClick(index: Int): Boolean {
		return true
	}

	init {
		contentView = object : LinearLayout(context) {
			val lastX = HashMap<Int, Int?>()

			override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
				val cy = (b - t) / 2

				if (includeAnimated) {
					var x = getPaddingLeft() - if (!recentIsShown) AndroidUtilities.dp((30 + 3).toFloat()) else 0

					for (i in 0 until childCount) {
						val child = getChildAt(i)

						if (child === settingsTab || removingViews.containsKey(child)) {
							continue
						}

						if (child != null) {
							child.layout(x, cy - child.measuredHeight / 2, x + child.measuredWidth, cy + child.measuredHeight / 2)

							val id = if (child is EmojiTabButton) child.id else if (child is EmojiTabsView) child.viewId else null

							if (animateAppear && child is EmojiTabButton && child.newly) {
								child.newly = false
								child.setScaleX(0f)
								child.setScaleY(0f)
								child.setAlpha(0f)
								child.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start()
							}

							if (id != null) {
								if (lastX[id] != null && lastX[id] != x) {
									child.translationX = (lastX[id]!! - x).toFloat()
									child.animate().translationX(0f).setDuration(250).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start()
								}

								lastX[id] = x
							}
							x += child.measuredWidth + AndroidUtilities.dp(3f)
						}
					}

					settingsTab?.let { settingsTab ->
						x += if (!recentIsShown) AndroidUtilities.dp((30 + 3).toFloat()) else 0

						val id = settingsTab.id

						if (x + settingsTab.measuredWidth + getPaddingRight() <= this@EmojiTabsStrip.measuredWidth) {
							settingsTab.layout((r - l - getPaddingRight() - settingsTab.measuredWidth).also { x = it }, cy - settingsTab.measuredHeight / 2, r - l - getPaddingRight(), cy + settingsTab.measuredHeight / 2)
						}
						else {
							settingsTab.layout(x, cy - settingsTab.measuredHeight / 2, x + settingsTab.measuredWidth, cy + settingsTab.measuredHeight / 2)
						}

						if (id != null) {
							if (lastX[id] != null && lastX[id] != x) {
								settingsTab.translationX = (lastX[id]!! - x).toFloat()
								settingsTab.animate().translationX(0f).setDuration(350).start()
							}

							lastX[id] = x
						}
					}
				}
				else {
					val childCount = childCount - if (!recentIsShown) 1 else 0
					val margin = ((r - l - getPaddingLeft() - getPaddingRight() - childCount * AndroidUtilities.dp(30f)) / max(1.0, (childCount - 1).toDouble()).toFloat()).toInt()
					var x = getPaddingLeft()

					for (i in 0 until childCount) {
						val child = getChildAt((if (!recentIsShown) 1 else 0) + i)

						if (child != null) {
							child.layout(x, cy - child.measuredHeight / 2, x + child.measuredWidth, cy + child.measuredHeight / 2)
							x += child.measuredWidth + margin
						}
					}
				}
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val atMost = MeasureSpec.makeMeasureSpec(99999999, MeasureSpec.AT_MOST)
				var width = getPaddingLeft() + getPaddingRight() - (if (recentIsShown) 0 else recentTab!!.alpha * AndroidUtilities.dp((30 + 3).toFloat())).toInt()

				for (i in 0 until childCount) {
					val child = getChildAt(i)

					if (child != null) {
						child.measure(atMost, heightMeasureSpec)
						width += child.measuredWidth + if (i + 1 < childCount) AndroidUtilities.dp(3f) else 0
					}
				}

				if (!includeAnimated) {
					setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
					return
				}

				setMeasuredDimension(max(width.toDouble(), MeasureSpec.getSize(widthMeasureSpec).toDouble()).toInt(), MeasureSpec.getSize(heightMeasureSpec))
			}

			private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
			private val from = RectF()
			private val to = RectF()
			private val rect = RectF()
			private val path = Path()

			override fun dispatchDraw(canvas: Canvas) {
				for ((view, bounds) in removingViews) {
					canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
						scale(view!!.scaleX, view.scaleY, bounds.width() / 2f, bounds.height() / 2f)
						view.draw(this)
					}
				}

				val selectFrom = floor(selectT.toDouble()).toInt()
				val selectTo = ceil(selectT.toDouble()).toInt()

				getChildBounds(selectFrom, from)
				getChildBounds(selectTo, to)

				AndroidUtilities.lerp(from, to, selectT - selectFrom, rect)

				val isEmojiTabs: Float = if (emojiTabs == null) 0f else MathUtils.clamp(1f - abs((selectT - 1).toDouble()).toFloat(), 0f, 1f)
				val isMiddle = 4f * selectAnimationT * (1f - selectAnimationT)
				val hw = rect.width() / 2 * (1f + isMiddle * .3f)
				val hh = rect.height() / 2 * (1f - isMiddle * .05f)

				rect.set(rect.centerX() - hw, rect.centerY() - hh, rect.centerX() + hw, rect.centerY() + hh)

				val r = AndroidUtilities.dp(AndroidUtilities.lerp(8f, 16f, isEmojiTabs)).toFloat()

				paint.setColor(selectorColor())

				path.rewind()
				path.addRoundRect(rect, r, r, Path.Direction.CW)

				canvas.drawPath(path, paint)

				emojiTabs?.let { emojiTabs ->
					path.addCircle((emojiTabs.left + AndroidUtilities.dp(15f)).toFloat(), (emojiTabs.top + emojiTabs.bottom) / 2f, AndroidUtilities.dp(15f).toFloat(), Path.Direction.CW)
				}

				super.dispatchDraw(canvas)

				wasDrawn = true
			}

			override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
				if (child === emojiTabs) {
					canvas.save()
					canvas.clipPath(path)

					val res = super.drawChild(canvas, child, drawingTime)

					canvas.restore()

					return res
				}

				return super.drawChild(canvas, child, drawingTime)
			}

			private fun getChildBounds(i: Int, out: RectF) {
				val child = getChildAt(MathUtils.clamp(i, 0, childCount - 1))
				out.set(child.left.toFloat(), child.top.toFloat(), child.right.toFloat(), child.bottom.toFloat())
				out.set(out.centerX() - out.width() / 2f * child.scaleX, out.centerY() - out.height() / 2f * child.scaleY, out.centerX() + out.width() / 2f * child.scaleX, out.centerY() + out.height() / 2f * child.scaleY)
			}
		}

		contentView.clipToPadding = false
		contentView.orientation = LinearLayout.HORIZONTAL

		isVerticalScrollBarEnabled = false
		isHorizontalScrollBarEnabled = false

		addView(contentView)

		contentView.addView(EmojiTabButton(context, recentDrawableId, roundSelector = false, forceSelector = false).also { recentTab = it })

		recentTab?.id = "recent".hashCode()

		if (!includeAnimated) {
			for (i in emojiTabsDrawableIds.indices) {
				contentView.addView(EmojiTabButton(context, emojiTabsDrawableIds[i], false, i == 0))
			}

			updateClickListeners()
		}
		else {
			if (includeStandard) {
				contentView.addView(EmojiTabsView(context).also { emojiTabs = it })
				emojiTabs?.viewId = "tabs".hashCode()
			}

			if (onSettingsOpenRunnable != null) {
				contentView.addView(EmojiTabButton(context, settingsDrawableId, roundSelector = false, forceSelector = true).also { settingsTab = it })

				settingsTab?.id = "settings".hashCode()
				settingsTab?.setAlpha(0f)
			}

			updateClickListeners()
		}
	}

	fun setPaddingLeft(paddingLeftDp: Float) {
		this.paddingLeftDp = paddingLeftDp
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		contentView.setPadding(AndroidUtilities.dp(paddingLeftDp), 0, AndroidUtilities.dp((5 + 6).toFloat()), 0)
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)
	}

	fun updateColors() {
		recentTab?.updateColor()
	}

	@JvmOverloads
	fun select(index: Int, animated: Boolean = true) {
		@Suppress("NAME_SHADOWING") var index = index
		@Suppress("NAME_SHADOWING") var animated = animated

		animated = animated && !first

		if (!recentIsShown) {
			index = max(1.0, index.toDouble()).toInt()
		}

		val wasSelected = selected
		var i = 0
		var j = 0

		while (i < contentView.childCount) {
			val child = contentView.getChildAt(i)
			val from = j

			if (child is EmojiTabsView) {
				var a = 0

				while (a < (child.contentView?.childCount ?: 0)) {
					val child2 = child.contentView?.getChildAt(a)

					if (child2 is EmojiTabButton) {
						child2.updateSelect(index == j, animated)
					}

					++a
					++j
				}

				--j
			}
			else if (child is EmojiTabButton) {
				child.updateSelect(index == j, animated)
			}

			if (index in from..j) {
				selected = i
			}

			++i
			++j
		}

		if (wasSelected != selected) {
			selectAnimator?.cancel()

			val from = selectT
			val to = selected.toFloat()

			if (animated) {
				selectAnimator = ValueAnimator.ofFloat(0f, 1f)

				selectAnimator?.addUpdateListener {
					selectAnimationT = it.getAnimatedValue() as Float
					selectT = AndroidUtilities.lerp(from, to, selectAnimationT)
					contentView.invalidate()
				}

				selectAnimator?.setDuration(350)
				selectAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
				selectAnimator?.start()
			}
			else {
				selectAnimationT = 1f
				selectT = AndroidUtilities.lerp(from, to, selectAnimationT)
				contentView.invalidate()
			}

			emojiTabs?.show(selected == 1, animated)

			val child = contentView.getChildAt(selected)

			if (selected >= 2) {
				scrollToVisible(child.left, child.right)
			}
			else {
				scrollTo(0)
			}
		}

		if (wasIndex != index) {
			emojiTabs?.let { emojiTabs ->
				if (selected == 1 && index >= 1 && index <= 1 + (emojiTabs.contentView?.childCount ?: 0)) {
					emojiTabs.scrollToVisible(AndroidUtilities.dp((36 * (index - 1) - 6).toFloat()), AndroidUtilities.dp((36 * (index - 1) - 6 + 30).toFloat()))
				}
			}

			wasIndex = index
		}
	}

	private fun selectorColor(): Int {
		return 0x2effffff and context.getColor(R.color.dark_gray)
	}

	fun setAnimatedEmojiCacheType(cacheType: Int) {
		animatedEmojiCacheType = cacheType
	}

	open inner class EmojiTabButton : ViewGroup {
		var shown = true
		var id: Int? = null
		var newly = false
		var imageView: ImageView? = null
		private var lottieDrawable: RLottieDrawable? = null
		private var lockView: PremiumLockIconView? = null
		private val round: Boolean
		private val forceSelector: Boolean
		private var wasVisible = false

		constructor(context: Context, drawableId: Int, lottieId: Int, roundSelector: Boolean, forceSelector: Boolean) : super(context) {
			round = roundSelector
			this.forceSelector = forceSelector

			if (round) {
				background = Theme.createCircleSelectorDrawable(selectorColor(), 0, 0)
			}
			else if (forceSelector) {
				background = Theme.createRadSelectorDrawable(selectorColor(), 8, 8)
			}

			lottieDrawable = RLottieDrawable(lottieId, "" + lottieId, AndroidUtilities.dp(24f), AndroidUtilities.dp(24f), false, null)
			lottieDrawable?.setBounds(AndroidUtilities.dp(3f), AndroidUtilities.dp(3f), AndroidUtilities.dp(27f), AndroidUtilities.dp(27f))
			lottieDrawable?.setMasterParent(this)
			lottieDrawable?.setAllowDecodeSingleFrame(true)
			lottieDrawable?.start()

			setColor(context.getColor(R.color.dark_gray))
		}

		constructor(context: Context, drawableId: Int, roundSelector: Boolean, forceSelector: Boolean) : super(context) {
			round = roundSelector

			this.forceSelector = forceSelector

			if (round) {
				background = Theme.createCircleSelectorDrawable(selectorColor(), 0, 0)
			}
			else if (forceSelector) {
				background = Theme.createRadSelectorDrawable(selectorColor(), 8, 8)
			}

			imageView = ImageView(context)
			imageView?.setImageDrawable(ResourcesCompat.getDrawable(resources, drawableId, null)?.mutate())

			setColor(context.getColor(R.color.dark_gray))

			addView(imageView)
		}

		@SuppressLint("AppCompatCustomView")
		constructor(context: Context, drawable: Drawable?, free: Boolean, roundSelector: Boolean, forceSelector: Boolean) : super(context) {
			newly = true
			round = roundSelector

			this.forceSelector = forceSelector

			if (round) {
				background = Theme.createCircleSelectorDrawable(selectorColor(), 0, 0)
			}
			else if (forceSelector) {
				background = Theme.createRadSelectorDrawable(selectorColor(), 8, 8)
			}

			imageView = object : ImageView(context) {
				override fun invalidate() {
					super.invalidate()
					updateLockImageReceiver()
				}

				override fun onDraw(canvas: Canvas) {
					// unused
				}

				override fun dispatchDraw(canvas: Canvas) {
					@Suppress("NAME_SHADOWING") val drawable = getDrawable()

					if (drawable != null) {
						drawable.setBounds(0, 0, measuredWidth, measuredHeight)
						drawable.alpha = 255

						if (drawable is AnimatedEmojiDrawable) {
							drawable.draw(canvas, false)
						}
						else {
							drawable.draw(canvas)
						}
					}
				}
			}

			imageView?.setImageDrawable(drawable)

			addView(imageView)

			lockView = PremiumLockIconView(context, PremiumLockIconView.TYPE_STICKERS_PREMIUM_LOCKED)
			lockView?.setAlpha(0f)
			lockView?.scaleX = 0f
			lockView?.scaleY = 0f

			updateLockImageReceiver()

			addView(lockView)

			setColor(context.getColor(R.color.dark_gray))
		}

		override fun dispatchDraw(canvas: Canvas) {
			super.dispatchDraw(canvas)
			lottieDrawable?.draw(canvas)
		}

		override fun performClick(): Boolean {
//            if (lottieDrawable != null) {
//                lottieDrawable.setProgress(0);
//                AndroidUtilities.runOnUIThread(() -> lottieDrawable.start(), 75);
//            }
			return super.performClick()
		}

		fun updateVisibilityInbounds(visible: Boolean, ignore: Boolean) {
			if (!wasVisible && visible) {
				lottieDrawable?.let { lottieDrawable ->
					if (!lottieDrawable.isRunning() && !ignore) {
						lottieDrawable.setProgress(0f)
						lottieDrawable.start()
					}
				}
			}

			wasVisible = visible
		}

		fun setLock(lock: Boolean?) {
			if (lockView == null) {
				return
			}

			if (lock == null) {
				updateLock(false)
			}
			else {
				updateLock(true)

				if (lock) {
					lockView?.setImageResource(R.drawable.msg_mini_lockedemoji)
				}
				else {
					val addIcon = ResourcesCompat.getDrawable(resources, R.drawable.msg_mini_addemoji, null)?.mutate()
					addIcon?.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY)

					lockView?.setImageDrawable(addIcon)
				}
			}
		}

		private var lockT = 0f
		private var lockAnimator: ValueAnimator? = null

		private fun updateLock(enable: Boolean) {
			lockAnimator?.cancel()

			if (abs((lockT - if (enable) 1f else 0f).toDouble()) < 0.01f) {
				return
			}

			lockView?.setVisibility(VISIBLE)

			lockAnimator = ValueAnimator.ofFloat(lockT, if (enable) 1f else 0f)

			lockAnimator?.addUpdateListener {
				lockT = it.getAnimatedValue() as Float

				lockView?.scaleX = lockT
				lockView?.scaleY = lockT
				lockView?.setAlpha(lockT)
			}

			lockAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (!enable) {
						lockView?.setVisibility(GONE)
					}
				}
			})

			lockAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
			lockAnimator?.setDuration(200)
			lockAnimator?.start()
		}

		fun updateLockImageReceiver() {
			val lockView = lockView ?: return

			if (!lockView.ready() && drawable is AnimatedEmojiDrawable) {
				val imageReceiver = (drawable as? AnimatedEmojiDrawable)?.imageReceiver

				if (imageReceiver != null) {
					lockView.setImageReceiver(imageReceiver)
					lockView.invalidate()
				}
			}
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			setMeasuredDimension(AndroidUtilities.dp(30f), AndroidUtilities.dp(30f))
			imageView?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.EXACTLY))
			lockView?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(12f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(12f), MeasureSpec.EXACTLY))
		}

		override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
			val imageView = imageView
			val lockView = lockView

			if (imageView != null) {
				val cx = (r - l) / 2
				val cy = (b - t) / 2
				imageView.layout(cx - imageView.measuredWidth / 2, cy - imageView.measuredHeight / 2, cx + imageView.measuredWidth / 2, cy + imageView.measuredHeight / 2)
			}

			lockView?.layout(r - l - lockView.measuredWidth, b - t - lockView.measuredHeight, r - l, b - t)
		}

		var drawable: Drawable?
			get() = imageView?.getDrawable()
			set(drawable) {
				if (lockView != null && drawable is AnimatedEmojiDrawable) {
					val imageReceiver = drawable.imageReceiver

					if (imageReceiver != null) {
						lockView?.setImageReceiver(imageReceiver)
					}
				}

				imageView?.setImageDrawable(drawable)
			}

		private var selectT = 0f
		private var selected = false
		private var selectAnimator: ValueAnimator? = null

		fun updateSelect(selected: Boolean, animated: Boolean) {
			if (imageView != null && imageView?.getDrawable() == null) {
				return
			}

			if (this.selected == selected) {
				return
			}

			this.selected = selected

			selectAnimator?.cancel()
			selectAnimator = null

			if (animated) {
				selectAnimator = ValueAnimator.ofFloat(selectT, if (selected) 1f else 0f)

				selectAnimator?.addUpdateListener {
					selectT = it.getAnimatedValue() as Float
					setColor(ColorUtils.blendARGB(context.getColor(R.color.dark_gray), context.getColor(R.color.brand), selectT))
				}

				selectAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (updateButtonDrawables && !round) {
							if (selected || forceSelector) {
								if (background == null) {
									background = Theme.createRadSelectorDrawable(selectorColor(), 8, 8)
								}
							}
							else {
								background = null
							}
						}
					}
				})

				selectAnimator?.setDuration(350)
				selectAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
				selectAnimator?.start()
			}
			else {
				selectT = if (selected) 1f else 0f
				updateColor()
			}
		}

		fun updateColor() {
			Theme.setSelectorDrawableColor(background, selectorColor(), false)
			setColor(ColorUtils.blendARGB(context.getColor(R.color.dark_gray), context.getColor(R.color.brand), selectT))
		}

		private fun setColor(color: Int) {
			val colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)

			imageView?.colorFilter = colorFilter
			imageView?.invalidate()

			if (lottieDrawable != null) {
				lottieDrawable?.setColorFilter(colorFilter)
				invalidate()
			}
		}
	}

	private inner class EmojiTabsView(context: Context) : ScrollableHorizontalScrollView(context) {
		var viewId = 0
		var contentView: LinearLayout? = null
		private var touching = false

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.lerp(AndroidUtilities.dp(30f), maxWidth(), showT), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30f), MeasureSpec.EXACTLY))
		}

		fun maxWidth(): Int {
			return AndroidUtilities.dp(((30 + 2) * min(5.7, (contentView?.childCount?.toDouble() ?: 0.0))).toFloat())
		}

		override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
			super.onScrollChanged(l, t, oldl, oldt)

			if (abs((t - oldt).toDouble()) < 2 || t >= measuredHeight || t == 0) {
				if (!touching) {
					this@EmojiTabsStrip.requestDisallowInterceptTouchEvent(false)
				}
			}

			updateButtonsVisibility()
		}

		private fun updateButtonsVisibility() {
			contentView?.children?.forEach { child ->
				if (child is EmojiTabButton) {
					child.updateVisibilityInbounds(child.getRight() - scrollX > 0 && child.getLeft() - scrollX < measuredWidth, this.isScrolling && showAnimator?.isRunning != true)
				}
			}
		}

		private fun intercept(ev: MotionEvent) {
			if (shown && !this.isScrolling) {
				when (ev.action) {
					MotionEvent.ACTION_UP -> {
						touching = false
					}

					MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
						touching = true

						if (!this.isScrolling) {
							resetScrollTo()
						}

						this@EmojiTabsStrip.requestDisallowInterceptTouchEvent(true)
					}
				}
			}
		}

		override fun onTouchEvent(ev: MotionEvent): Boolean {
			intercept(ev)
			return super.onTouchEvent(ev)
		}

		private var shown = false
		private var showT = 0f
		private var showAnimator: ValueAnimator? = null

		init {
			isSmoothScrollingEnabled = true
			isHorizontalScrollBarEnabled = false
			isVerticalScrollBarEnabled = false
			isNestedScrollingEnabled = true

			contentView = object : LinearLayout(context) {
				override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
					var x = getPaddingLeft()
					val cy = (b - t) / 2

					for (i in 0 until childCount) {
						val child = getChildAt(i)

						if (child === settingsTab) {
							continue
						}

						if (child != null) {
							child.layout(x, cy - child.measuredHeight / 2, x + child.measuredWidth, cy + child.measuredHeight / 2)
							x += child.measuredWidth + AndroidUtilities.dp(2f)
						}
					}
				}

				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					super.onMeasure(max(MeasureSpec.getSize(widthMeasureSpec).toDouble(), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(((30 + 2) * (contentView?.childCount ?: 0)).toFloat()), MeasureSpec.EXACTLY).toDouble()).toInt(), heightMeasureSpec)
				}
			}

			contentView?.orientation = LinearLayout.HORIZONTAL

			addView(contentView, LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT))

			for (i in emojiTabsDrawableIds.indices) {
				contentView?.addView(object : EmojiTabButton(context, emojiTabsDrawableIds[i], emojiTabsAnimatedDrawableIds[i], true, false) {
					override fun onTouchEvent(ev: MotionEvent): Boolean {
						intercept(ev)
						return super.onTouchEvent(ev)
					}
				})
			}
		}

		fun show(show: Boolean, animated: Boolean) {
			if (show == shown) {
				return
			}

			shown = show

			if (!show) {
				scrollTo(0)
			}

			showAnimator?.cancel()

			if (animated) {
				showAnimator = ValueAnimator.ofFloat(showT, if (show) 1f else 0f)

				showAnimator?.addUpdateListener {
					showT = it.getAnimatedValue() as Float
					invalidate()
					requestLayout()
					updateButtonsVisibility()
					this@EmojiTabsStrip.contentView.invalidate()
				}

				showAnimator?.setDuration(475)
				showAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
				showAnimator?.start()
			}
			else {
				showT = if (show) 1f else 0f
				invalidate()
				requestLayout()
				updateButtonsVisibility()
				this@EmojiTabsStrip.contentView.invalidate()
			}
		}
	}

	companion object {
		private val emojiTabsDrawableIds = intArrayOf(R.drawable.msg_emoji_smiles, R.drawable.msg_emoji_cat, R.drawable.msg_emoji_food, R.drawable.msg_emoji_activities, R.drawable.msg_emoji_travel, R.drawable.msg_emoji_objects, R.drawable.msg_emoji_other, R.drawable.msg_emoji_flags)
		private val emojiTabsAnimatedDrawableIds = intArrayOf(R.raw.msg_emoji_smiles, R.raw.msg_emoji_cat, R.raw.msg_emoji_food, R.raw.msg_emoji_activities, R.raw.msg_emoji_travel, R.raw.msg_emoji_objects, R.raw.msg_emoji_other, R.raw.msg_emoji_flags)
	}
}

open class ScrollableHorizontalScrollView(context: Context?) : HorizontalScrollView(context) {
	var isScrolling = false
		protected set

	fun scrollToVisible(left: Int, right: Int): Boolean {
		if (childCount <= 0) {
			return false
		}

		val padding = AndroidUtilities.dp(50f)

		val to = if (left < scrollX + padding) {
			left - padding
		}
		else if (right > scrollX + (measuredWidth - padding)) {
			right - measuredWidth + padding
		}
		else {
			return false
		}

		scrollTo(MathUtils.clamp(to, 0, getChildAt(0).measuredWidth - measuredWidth))

		return true
	}

	private var scrollingTo = -1
	private var scrollAnimator: ValueAnimator? = null

	fun scrollTo(x: Int) {
		if (scrollingTo == x) {
			return
		}

		scrollingTo = x

		scrollAnimator?.cancel()

		if (this.scrollX == x) {
			return
		}

		scrollAnimator = ValueAnimator.ofFloat(this.scrollX.toFloat(), x.toFloat())
		scrollAnimator?.addUpdateListener { this.scrollX = (it.getAnimatedValue() as Float).toInt() }
		scrollAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
		scrollAnimator?.setDuration(250)

		scrollAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				isScrolling = false
			}

			override fun onAnimationStart(animation: Animator) {
				isScrolling = true

				(parent as? HorizontalScrollView)?.requestDisallowInterceptTouchEvent(false)
			}
		})

		scrollAnimator?.start()
	}

	fun resetScrollTo() {
		scrollingTo = -1
	}
}
