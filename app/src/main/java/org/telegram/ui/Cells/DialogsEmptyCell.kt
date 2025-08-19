/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Cells

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BlurredRecyclerView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.TextViewSwitcher

class DialogsEmptyCell @JvmOverloads constructor(context: Context, private val onInviteClick: (() -> Unit)? = null, private val onTipsClick: (() -> Unit)? = null, private val onRecommendedClick: (() -> Unit)? = null) : LinearLayout(context) {
	private val imageView: RLottieImageView
	private val titleView: TextView
	private val subtitleView: TextViewSwitcher
	private val currentAccount = UserConfig.selectedAccount
	private val buttonsPanel: LinearLayout

	@EmptyType
	private var currentType = TYPE_UNSPECIFIED

	init {
		gravity = Gravity.CENTER
		orientation = VERTICAL

		setOnTouchListener { _, _ -> true }

		imageView = RLottieImageView(context)
		imageView.setAutoRepeat(true)

		addView(imageView, LayoutHelper.createFrame(ANIMATED_ICON_SIDE, ANIMATED_ICON_SIDE.toFloat(), Gravity.CENTER, 12f, 4f, 12f, 0f))

		imageView.setOnClickListener {
			if (!imageView.isPlaying()) {
				imageView.setProgress(0.0f)
				imageView.playAnimation()
			}
		}

		titleView = TextView(context)
		titleView.setTextColor(context.getColor(R.color.text))
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		titleView.typeface = Theme.TYPEFACE_BOLD
		titleView.gravity = Gravity.CENTER

		addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.LEFT, 16f, 8f, 16f, 0f))

		subtitleView = TextViewSwitcher(context)

		subtitleView.setFactory {
			val tv = TextView(context)
			tv.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			tv.gravity = Gravity.CENTER
			tv.setLineSpacing(0f, 1.3f)
			tv.movementMethod = LinkMovementMethod.getInstance()
			tv
		}

		subtitleView.setInAnimation(context, R.anim.alpha_in)
		subtitleView.setOutAnimation(context, R.anim.alpha_out)

//		addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.LEFT, 16f, 8f, 16f, 0f))

		buttonsPanel = LinearLayout(context)

		addView(buttonsPanel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.CENTER, 16f, 0f, 16f, 0f))

		val inviteButton = object : MaterialButton(context, null, R.attr.brandedWhiteButtonSmallRadiusStyle) {
			override fun getAccessibilityClassName(): CharSequence {
				return Button::class.java.name
			}
		}

		inviteButton.text = context.getString(R.string.InviteFriends)

		inviteButton.setOnClickListener {
			onInviteClick?.invoke()
		}
		//MARK: Hidden invite button
		inviteButton.gone()

		buttonsPanel.addView(inviteButton, LayoutHelper.createLinear(0, 68, Gravity.CENTER_VERTICAL, 0, 0, 4, 0))

		val tipsButton = object : MaterialButton(context, null, R.attr.brandedWhiteButtonSmallRadiusStyle) {
			override fun getAccessibilityClassName(): CharSequence {
				return Button::class.java.name
			}
		}

		tipsButton.text = context.getString(R.string.ello_tips)

		tipsButton.setOnClickListener {
			onTipsClick?.invoke()
		}
		//MARK: Hidden tips button
		tipsButton.gone()

		buttonsPanel.addView(tipsButton, LayoutHelper.createLinear(0, 68, Gravity.CENTER_VERTICAL, 4, 0, 0, 0))

		val exploreButton = LayoutInflater.from(context).inflate(R.layout.explore_button, buttonsPanel, false) as MaterialButton

		exploreButton.setOnClickListener {
			onRecommendedClick?.invoke()
		}

		buttonsPanel.addView(exploreButton)

		exploreButton.updateLayoutParams<LayoutParams> {
			this.weight = 1f
		}

		inviteButton.updateLayoutParams<LayoutParams> {
			this.weight = 1f
		}

		tipsButton.updateLayoutParams<LayoutParams> {
			this.weight = 1f
		}
	}

	@SuppressLint("ResourceType")
	fun setType(@EmptyType value: Int) {
		if (currentType == value) {
			return
		}

		currentType = value

		val help: String
		var animation = false
		var icon = 0

		when (currentType) {
			TYPE_WELCOME_WITH_CONTACTS, TYPE_WELCOME_NO_CONTACTS -> {
				animation = true
				icon = R.raw.panda_empty_chats
				help = context.getString(R.string.NoChatsHelp)
				titleView.text = context.getString(R.string.NoChats)
			}

			TYPE_FILTER_NO_CHATS_TO_DISPLAY -> {
				icon = R.raw.filter_no_chats
				help = context.getString(R.string.FilterNoChatsToDisplayInfo)
				titleView.text = context.getString(R.string.FilterNoChatsToDisplay)
			}

			TYPE_UNSPECIFIED, TYPE_FILTER_ADDING_CHATS -> {
				// TODO: set proper icon
				// icon = R.raw.filter_new;
				help = context.getString(R.string.FilterAddingChatsInfo)
				titleView.text = context.getString(R.string.FilterAddingChats)
			}

			else -> {
				help = context.getString(R.string.FilterAddingChatsInfo)
				titleView.text = context.getString(R.string.FilterAddingChats)
			}
		}

		if (icon != 0) {
			imageView.visible()

			if (animation) {
				imageView.setAnimation(icon, ANIMATED_ICON_SIDE, ANIMATED_ICON_SIDE)
				imageView.playAnimation()
			}
			else {
				imageView.setImageResource(icon)
			}

			if (currentType == TYPE_WELCOME_WITH_CONTACTS) {
				var noChatsContactsHelp = context.getString(R.string.NoChatsContactsHelp)

				if (AndroidUtilities.isTablet() && !AndroidUtilities.isSmallTablet()) {
					noChatsContactsHelp = noChatsContactsHelp.replace('\n', ' ')
				}

				subtitleView.setText(noChatsContactsHelp, true)

				requestLayout()
			}
		}
		else {
			imageView.gone()
		}

		subtitleView.setText(SpannableStringBuilder(help), false)
	}

	override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
		super.onLayout(changed, l, t, r, b)
		updateLayout()
	}

	override fun offsetTopAndBottom(offset: Int) {
		super.offsetTopAndBottom(offset)
		updateLayout()
	}

	fun updateLayout() {
		var offset = 0

		if (parent is View && (currentType == TYPE_FILTER_NO_CHATS_TO_DISPLAY || currentType == TYPE_FILTER_ADDING_CHATS)) {
			val view = parent as View

			val paddingTop = view.paddingTop

			if (paddingTop != 0) {
				offset -= top / 2
			}
		}

		if (currentType == TYPE_WELCOME_NO_CONTACTS || currentType == TYPE_WELCOME_WITH_CONTACTS) {
			offset -= (ActionBar.getCurrentActionBarHeight() / 2f).toInt()
		}

		imageView.translationY = offset.toFloat()
		titleView.translationY = offset.toFloat()
		subtitleView.translationY = offset.toFloat()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		when (currentType) {
			TYPE_WELCOME_NO_CONTACTS, TYPE_WELCOME_WITH_CONTACTS -> {
				super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightMeasureSpec, MeasureSpec.EXACTLY))
			}

			TYPE_FILTER_NO_CHATS_TO_DISPLAY, TYPE_FILTER_ADDING_CHATS -> {
				var totalHeight: Int

				if (parent is View) {
					val view = parent as View

					totalHeight = view.measuredHeight

					if (view.paddingTop != 0) {
						totalHeight -= AndroidUtilities.statusBarHeight
					}
				}
				else {
					totalHeight = MeasureSpec.getSize(heightMeasureSpec)
				}

				if (totalHeight == 0) {
					totalHeight = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight
				}

				if (parent is BlurredRecyclerView) {
					totalHeight -= (parent as BlurredRecyclerView).blurTopPadding
				}

				val arrayList = MessagesController.getInstance(currentAccount).hintDialogs

				if (arrayList.isNotEmpty()) {
					totalHeight -= AndroidUtilities.dp(72f) * arrayList.size + arrayList.size - 1 + AndroidUtilities.dp((12 + 38).toFloat())
				}

				super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY))
			}

			else -> {
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(166f), MeasureSpec.EXACTLY))
			}
		}
	}

	@Retention(AnnotationRetention.SOURCE)
	@IntDef(TYPE_UNSPECIFIED, TYPE_WELCOME_NO_CONTACTS, TYPE_WELCOME_WITH_CONTACTS, TYPE_FILTER_NO_CHATS_TO_DISPLAY, TYPE_FILTER_ADDING_CHATS)
	annotation class EmptyType

	companion object {
		const val TYPE_WELCOME_NO_CONTACTS = 0
		const val TYPE_WELCOME_WITH_CONTACTS = 1
		const val TYPE_FILTER_NO_CHATS_TO_DISPLAY = 2
		const val TYPE_FILTER_ADDING_CHATS = 3
		private const val TYPE_UNSPECIFIED = -1
		const val ANIMATED_ICON_SIDE = 160
	}
}
