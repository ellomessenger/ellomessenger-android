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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.collection.LongSparseArray
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withTranslation
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC.TLBotInfo
import org.telegram.ui.ActionBar.MenuDrawable
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter

class BotCommandsMenuView(context: Context) : View(context) {
	private val rectTmp = RectF()
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private var expandProgress = 0f
	private var menuText: String? = context.getString(R.string.BotsMenuTitle)
	private var menuTextLayout: StaticLayout? = null
	private var isOpened = false
	private var isWebViewOpened = false
	private var backgroundDrawable: Drawable
	private var drawBackgroundDrawable = true
	var lastSize = 0

	var expanded = false
		private set

	var isWebView = false
		private set

	private val backDrawable = object : MenuDrawable() {
		override fun invalidateSelf() {
			super.invalidateSelf()
			invalidate()
		}
	}

	private val webViewAnimation = object : RLottieDrawable(R.raw.bot_webview_sheet_to_cross, R.raw.bot_webview_sheet_to_cross.toString() + hashCode(), AndroidUtilities.dp(20f), AndroidUtilities.dp(20f)) {
		override fun invalidateSelf() {
			super.invalidateSelf()
			invalidate()
		}

		override fun invalidateInternal() {
			super.invalidateInternal()
			invalidate()
		}
	}

	fun setDrawBackgroundDrawable(drawBackgroundDrawable: Boolean) {
		this.drawBackgroundDrawable = drawBackgroundDrawable
		invalidate()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		webViewAnimation.setMasterParent(this)
		webViewAnimation.setCurrentParentView(this)
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		webViewAnimation.setMasterParent(this)
	}

	fun setWebView(webView: Boolean) {
		isWebView = webView
		invalidate()
	}

	init {
		paint.color = ResourcesCompat.getColor(resources, R.color.brand, null)

		val textColor = ResourcesCompat.getColor(resources, R.color.white, null)

		backDrawable.setBackColor(textColor)
		backDrawable.setIconColor(textColor)
		backDrawable.setMiniIcon(true)
		backDrawable.setRotateToBack(false)
		backDrawable.setRotation(0f, false)
		backDrawable.callback = this
		backDrawable.setRoundCap()

		textPaint.color = textColor
		textPaint.typeface = Theme.TYPEFACE_BOLD

		backgroundDrawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16f), Color.TRANSPARENT, ResourcesCompat.getColor(resources, R.color.green_transparent, null))
		backgroundDrawable.callback = this

		contentDescription = context.getString(R.string.AccDescrBotMenu)
	}

	fun setMenuBackgroundColor(@ColorRes id: Int) {
		paint.color = ResourcesCompat.getColor(resources, id, null)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val size = MeasureSpec.getSize(widthMeasureSpec) + MeasureSpec.getSize(heightMeasureSpec) shl 16

		if (lastSize != size || menuTextLayout == null) {
			backDrawable.setBounds(0, 0, measuredWidth, measuredHeight)
			textPaint.textSize = AndroidUtilities.dp(15f).toFloat()
			lastSize = size

			val w = textPaint.measureText(menuText).toInt()

			menuTextLayout = StaticLayoutEx.createStaticLayout(menuText, textPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false, TextUtils.TruncateAt.END, w, 1)
		}

//		onTranslationChanged((menuTextLayout!!.width + AndroidUtilities.dp(4f)) * expandProgress)

		var width = AndroidUtilities.dp(40f)

		if (expanded) {
			width += menuTextLayout!!.width + AndroidUtilities.dp(4f)
		}

		super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32f), MeasureSpec.EXACTLY))
	}

	override fun dispatchDraw(canvas: Canvas) {
		menuTextLayout?.let { menuTextLayout ->
			var update = false

			if (expanded && expandProgress != 1f) {
				expandProgress += 16f / 150f

				if (expandProgress > 1) {
					expandProgress = 1f
				}
				else {
					invalidate()
				}

				update = true
			}
			else if (!expanded && expandProgress != 0f) {
				expandProgress -= 16f / 150f

				if (expandProgress < 0) {
					expandProgress = 0f
				}
				else {
					invalidate()
				}

				update = true
			}

			val expandProgress = CubicBezierInterpolator.DEFAULT.getInterpolation(expandProgress)

			if (update && expandProgress > 0) {
				textPaint.alpha = (255 * expandProgress).toInt()
			}

			if (drawBackgroundDrawable) {
				rectTmp.set(0f, 0f, AndroidUtilities.dp(40f) + (menuTextLayout.width + AndroidUtilities.dp(4f)) * expandProgress, measuredHeight.toFloat())

				canvas.drawRoundRect(rectTmp, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), paint)

				backgroundDrawable.setBounds(rectTmp.left.toInt(), rectTmp.top.toInt(), rectTmp.right.toInt(), rectTmp.bottom.toInt())
				backgroundDrawable.draw(canvas)
			}

			if (isWebView) {
				canvas.save()
				canvas.translate(AndroidUtilities.dp(9.5f).toFloat(), AndroidUtilities.dp(6f).toFloat())

				val drawable = webViewAnimation
				drawable.setBounds(0, 0, drawable.width, drawable.height)
				drawable.draw(canvas)

				canvas.restore()

				if (drawable.isRunning()) {
					invalidate()
				}
			}
			else {
				canvas.withTranslation(AndroidUtilities.dp(8f).toFloat(), AndroidUtilities.dp(4f).toFloat()) {
					backDrawable.draw(this)
				}
			}

			if (expandProgress > 0) {
				canvas.withTranslation(AndroidUtilities.dp(34f).toFloat(), (measuredHeight - menuTextLayout.height) / 2f) {
					menuTextLayout.draw(this)
				}
			}
		}

		super.dispatchDraw(canvas)
	}

	fun setMenuText(menuText: String?): Boolean {
		@Suppress("NAME_SHADOWING") val menuText = menuText ?: context.getString(R.string.BotsMenuTitle)
		val changed = this.menuText == null || this.menuText != menuText
		this.menuText = menuText
		menuTextLayout = null
		requestLayout()
		return changed
	}

	fun setExpanded(expanded: Boolean, animated: Boolean) {
		if (this.expanded != expanded) {
			this.expanded = expanded

			if (!animated) {
				expandProgress = if (expanded) 1f else 0f
			}

			requestLayout()
			invalidate()
		}
	}

	fun isOpened(): Boolean {
		return isOpened
	}

	class BotCommandsAdapter : SelectionAdapter() {
		private var newResult = ArrayList<String>()
		private var newResultHelp = ArrayList<String>()

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return true
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = BotCommandView(parent.context)
			view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val view = holder.itemView as BotCommandView
			view.commandTextView.text = newResult[position]
			view.description.text = newResultHelp[position]
			view.command = newResult[position]
		}

		override fun getItemCount(): Int {
			return newResult.size
		}

		fun setBotInfo(botInfo: LongSparseArray<TLBotInfo>) {
			newResult.clear()
			newResultHelp.clear()

			for (b in 0 until botInfo.size()) {
				val info = botInfo.valueAt(b)

				for (a in info.commands.indices) {
					val botCommand = info.commands[a]

					if (botCommand.command != null) {
						newResult.add("/" + botCommand.command)
						newResultHelp.add(botCommand.description ?: "")
					}
				}
			}

			notifyDataSetChanged()
		}
	}

	fun setOpened(opened: Boolean) {
		if (isOpened != opened) {
			isOpened = opened
		}

		if (isWebView) {
			if (isWebViewOpened != opened) {
				val drawable = webViewAnimation
				drawable.stop()
				drawable.setPlayInDirectionOfCustomEndFrame(true)
				drawable.setCustomEndFrame(if (opened) drawable.framesCount else 1)
				drawable.start()

				isWebViewOpened = opened
			}
		}
		else {
			backDrawable.setRotation(if (opened) 1f else 0f, true)
		}
	}

	override fun verifyDrawable(who: Drawable): Boolean {
		return super.verifyDrawable(who) || backgroundDrawable === who
	}

	override fun drawableStateChanged() {
		super.drawableStateChanged()
		backgroundDrawable.state = drawableState
	}

	override fun jumpDrawablesToCurrentState() {
		super.jumpDrawablesToCurrentState()
		backgroundDrawable.jumpToCurrentState()
	}
}
