/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateMargins
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible

class BottomNavigationPanel @JvmOverloads constructor(context: Context, private var defaultItem: Item = Item.CHATS, private val showTitles: Boolean = false) : LinearLayout(context) {
	enum class Item {
		CALLS, CONTACTS, CHATS, FEED, SETTINGS
	}

	private var currentItem: Item = defaultItem
	var listener: BottomNavigationListener? = null
	private val items: List<ConstraintLayout>

	private val unreadBadge = ImageView(context).apply {
		setImageResource(R.drawable.unread_badge)
		gone()
	}

	init {
		orientation = HORIZONTAL

		elevation = AndroidUtilities.dp(4f).toFloat()

		outlineProvider = ViewOutlineProvider.BOUNDS

		setBackgroundResource(R.color.feed_audio_background)

		val images = listOf(R.drawable.chat_calls_voice, R.drawable.ic_contacts, R.drawable.chats_new_tab, R.drawable.ic_feeds, R.drawable.ic_profile)
		val titles = listOf(context.getString(R.string.Calls), context.getString(R.string.Contacts), context.getString(R.string.chats), context.getString(R.string.feed), context.getString(R.string.my_profile))

		this.items = Item.values().mapIndexed { index, i ->
			val button = ConstraintLayout(context)
			button.isClickable = true
			button.isFocusable = true
			button.contentDescription = titles[index]

			val outValue = TypedValue()
			context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
			button.background = ContextCompat.getDrawable(context, outValue.resourceId)

			val image = ImageView(context).apply {
				id = R.id.image

				imageTintList = if (i == defaultItem) {
					ColorStateList.valueOf(ResourcesCompat.getColor(context.resources, R.color.brand, null))
				}
				else {
					ColorStateList.valueOf(ResourcesCompat.getColor(context.resources, R.color.icon_tab_not_active, null))
				}

				setImageResource(images[index])
			}

			val title = TextView(context).apply {
				id = R.id.title

				setTextColor(if (i == defaultItem) {
					ResourcesCompat.getColor(context.resources, R.color.brand, null)
				}
				else {
					ResourcesCompat.getColor(context.resources, R.color.icon_tab_not_active, null)
				})

				text = titles[index]
			}

			button.addView(image, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
				startToStart = ConstraintLayout.LayoutParams.PARENT_ID
				endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
				topToTop = ConstraintLayout.LayoutParams.PARENT_ID
				bottomToTop = R.id.title

				verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
			})

			button.addView(title, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
				startToStart = ConstraintLayout.LayoutParams.PARENT_ID
				endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
				topToBottom = R.id.image
				bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
			})

			if (index == 2) {
				button.addView(unreadBadge, ConstraintLayout.LayoutParams(AndroidUtilities.dp(15f), AndroidUtilities.dp(15f)).apply {
					endToEnd = R.id.image
					topToTop = R.id.image
					marginEnd = -AndroidUtilities.dp(3f)
					topMargin = -AndroidUtilities.dp(3f)
				})
			}

			if (!showTitles) {
				title.gone()
			}

			button.tag = i

			button.setOnClickListener {
				onButtonSelected(button)
			}

			addView(button, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

			button
		}
	}

	private fun onButtonSelected(button: ConstraintLayout, invokeCallback: Boolean = true) {
		val item = button.tag as? Item ?: return

		items.forEach {
			val curItem = it.tag as? Item ?: return@forEach
			val title = it.findViewById<TextView>(R.id.title)
			val image = it.findViewById<ImageView>(R.id.image)

			if (curItem == item) {
				title.setTextColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))
				image.imageTintList = ColorStateList.valueOf(ResourcesCompat.getColor(context.resources, R.color.brand, null))
			}
			else {
				title.setTextColor(ResourcesCompat.getColor(context.resources, R.color.icon_tab_not_active, null))
				image.imageTintList = ColorStateList.valueOf(ResourcesCompat.getColor(context.resources, R.color.icon_tab_not_active, null))
			}
		}

		if (invokeCallback) {
			listener?.onBottomNavigationItemClicked(item)
		}
	}

	@JvmOverloads
	fun reset(newDefaultItem: Item, invokeCallback: Boolean = false) {
		defaultItem = newDefaultItem
		setCurrentItem(defaultItem, invokeCallback = invokeCallback)
	}

	fun setCurrentItem(item: Item, invokeCallback: Boolean = true) {
		currentItem = item
		updateButtons(invokeCallback)
	}

	fun getCurrentItem() = currentItem

	private fun updateButtons(invokeCallback: Boolean) {
		items.find { it.tag == currentItem }?.run {
			onButtonSelected(button = this, invokeCallback = invokeCallback)
		}
	}

	fun setUnreadBadge(count: Int) {
		if (count > 0) {
			unreadBadge.visible()
		}
		else {
			unreadBadge.gone()
		}
	}

	fun interface BottomNavigationListener {
		fun onBottomNavigationItemClicked(item: Item)
	}

	companion object {
		const val height = 56 // dp

		@JvmStatic
		fun createLayoutParams(): FrameLayout.LayoutParams {
			return LayoutHelper.createFrame(LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM).apply {
				updateMargins(0, 0, 0, 0)
			}
		}
	}
}
