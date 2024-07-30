/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.ui.ActionBar

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.view.children
import androidx.core.view.isVisible
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Adapters.FiltersView.MediaFilterData
import org.telegram.ui.Components.RLottieDrawable

open class ActionBarMenu(context: Context) : LinearLayout(context) {
	@JvmField
	var drawBlur = true

	@JvmField
	var parentActionBar: ActionBar? = null

	@JvmField
	var isActionMode = false

	constructor(context: Context, layer: ActionBar?) : this(context) {
		orientation = HORIZONTAL
		parentActionBar = layer
	}

	fun updateItemsBackgroundColor() {
		children.forEach {
			(it as? ActionBarMenuItem)?.background = Theme.createSelectorDrawable(if (isActionMode) parentActionBar!!.itemsActionModeBackgroundColor else parentActionBar!!.itemsBackgroundColor)
		}
	}

	fun updateItemsColor() {
		children.forEach {
			(it as? ActionBarMenuItem)?.setIconColor(if (isActionMode) parentActionBar!!.itemsActionModeColor else parentActionBar!!.itemsColor)
		}
	}

	fun addItem(id: Int, drawable: Drawable?): ActionBarMenuItem {
		return addItem(id = id, icon = 0, text = null, backgroundColor = if (isActionMode) parentActionBar!!.itemsActionModeBackgroundColor else parentActionBar!!.itemsBackgroundColor, drawable = drawable, width = AndroidUtilities.dp(48f), title = null)
	}

	fun addItem(id: Int, text: CharSequence?): ActionBarMenuItem {
		return addItem(id = id, icon = 0, text = text, backgroundColor = if (isActionMode) parentActionBar!!.itemsActionModeBackgroundColor else parentActionBar!!.itemsBackgroundColor, drawable = null, width = 0, title = text)
	}

	@JvmOverloads
	fun addItem(id: Int, @DrawableRes icon: Int, backgroundColor: Int = if (isActionMode) parentActionBar!!.itemsActionModeBackgroundColor else parentActionBar!!.itemsBackgroundColor): ActionBarMenuItem {
		return addItem(id = id, icon = icon, text = null, backgroundColor = backgroundColor, drawable = null, width = AndroidUtilities.dp(48f), title = null)
	}

	fun addItemWithWidth(id: Int, @DrawableRes icon: Int, width: Int): ActionBarMenuItem {
		return addItem(id = id, icon = icon, text = null, backgroundColor = if (isActionMode) parentActionBar!!.itemsActionModeBackgroundColor else parentActionBar!!.itemsBackgroundColor, drawable = null, width = width, title = null)
	}

	fun addItemWithWidth(id: Int, drawable: Drawable?, width: Int, title: CharSequence?): ActionBarMenuItem {
		return addItem(id = id, icon = 0, text = null, backgroundColor = if (isActionMode) parentActionBar!!.itemsActionModeBackgroundColor else parentActionBar!!.itemsBackgroundColor, drawable = drawable, width = width, title = title)
	}

	fun addItemWithWidth(id: Int, @DrawableRes icon: Int, width: Int, title: CharSequence?): ActionBarMenuItem {
		return addItem(id = id, icon = icon, text = null, backgroundColor = if (isActionMode) parentActionBar!!.itemsActionModeBackgroundColor else parentActionBar!!.itemsBackgroundColor, drawable = null, width = width, title = title)
	}

	fun addItem(id: Int, @DrawableRes icon: Int, text: CharSequence?, backgroundColor: Int, drawable: Drawable?, width: Int, title: CharSequence?): ActionBarMenuItem {
		if (id == ActionBar.BACK_BUTTON) {
			throw IllegalArgumentException("Can't use ActionBar.BACK_BUTTON as item id")
		}

		val menuItem = ActionBarMenuItem(context, this, backgroundColor, if (isActionMode) parentActionBar!!.itemsActionModeColor else parentActionBar!!.itemsColor, text != null)
		menuItem.tag = id

		if (!text.isNullOrEmpty()) {
			menuItem.textView?.text = text

			val layoutParams = LayoutParams(if (width != 0) width else ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
			layoutParams.rightMargin = AndroidUtilities.dp(14f)
			layoutParams.leftMargin = layoutParams.rightMargin

			addView(menuItem, layoutParams)
		}
		else {
			if (drawable != null) {
				if (drawable is RLottieDrawable) {
					menuItem.iconView?.setAnimation(drawable)
				}
				else {
					menuItem.iconView?.setImageDrawable(drawable)
				}
			}
			else if (icon != 0) {
				menuItem.iconView?.setImageResource(icon)
			}

			addView(menuItem, LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT))
		}

		menuItem.setOnClickListener {
			val item = it as ActionBarMenuItem

			if (item.hasSubMenu()) {
				if (parentActionBar?.actionBarMenuOnItemClick?.canOpenMenu() == true) {
					item.toggleSubMenu()
				}
			}
			else if (item.isSearchField) {
				parentActionBar?.onSearchFieldVisibilityChanged(item.toggleSearch(true))
			}
			else {
				onItemClick(it.getTag() as Int)
			}
		}

		if (!title.isNullOrEmpty()) {
			menuItem.contentDescription = title
		}

		return menuItem
	}

	fun hideAllPopupMenus() {
		children.forEach {
			(it as? ActionBarMenuItem)?.closeSubMenu()
		}
	}

	fun setPopupItemsColor(color: Int, icon: Boolean) {
		children.forEach {
			(it as? ActionBarMenuItem)?.setPopupItemsColor(color, icon)
		}
	}

	fun setPopupItemsSelectorColor(color: Int) {
		children.forEach {
			(it as? ActionBarMenuItem)?.setPopupItemsSelectorColor(color)
		}
	}

	fun redrawPopup(color: Int) {
		children.forEach {
			(it as? ActionBarMenuItem)?.redrawPopup(color)
		}
	}

	fun onItemClick(id: Int) {
		parentActionBar?.actionBarMenuOnItemClick?.onItemClick(id)
	}

	fun clearItems() {
		removeAllViews()
	}

	fun onMenuButtonPressed() {
		for (child in children) {
			if (child is ActionBarMenuItem) {
				if (!child.isVisible) {
					continue
				}

				if (child.hasSubMenu()) {
					child.toggleSubMenu()
					break
				}
				else if (child.getOverrideMenuClick()) {
					onItemClick(child.tag as Int)
					break
				}
			}
		}
	}

	fun closeSearchField(closeKeyboard: Boolean) {
		for (child in children) {
			if (child is ActionBarMenuItem && child.isSearchField && child.isSearchFieldVisible) {
				if (child.listener == null || child.listener?.canCollapseSearch() == true) {
					parentActionBar?.onSearchFieldVisibilityChanged(false)
					child.toggleSearch(closeKeyboard)
				}

				break
			}
		}
	}

	fun setSearchCursorColor(color: Int) {
		for (child in children) {
			if (child is ActionBarMenuItem && child.isSearchField) {
				child.searchField?.setCursorColor(color)
				break
			}
		}
	}

	fun setSearchTextColor(color: Int, placeholder: Boolean) {
		for (child in children) {
			if (child is ActionBarMenuItem && child.isSearchField) {
				if (placeholder) {
					child.searchField?.setHintTextColor(color)
				}
				else {
					child.searchField?.setTextColor(color)
				}

				break
			}
		}
	}

	fun setSearchFieldText(text: String) {
		children.forEach {
			if (it is ActionBarMenuItem && it.isSearchField) {
				it.setSearchFieldText(text, false)
				it.searchField?.setSelection(text.length)
			}
		}
	}

	fun onSearchPressed() {
		children.forEach {
			if (it is ActionBarMenuItem && it.isSearchField) {
				it.onSearchPressed()
			}
		}
	}

	fun openSearchField(toggle: Boolean, showKeyboard: Boolean, text: String, animated: Boolean) {
		for (child in children) {
			if (child is ActionBarMenuItem && child.isSearchField) {
				if (toggle) {
					parentActionBar?.onSearchFieldVisibilityChanged(child.toggleSearch(showKeyboard))
				}

				child.setSearchFieldText(text, animated)
				child.searchField?.setSelection(text.length)

				break
			}
		}
	}

	fun setFilter(filter: MediaFilterData) {
		for (child in children) {
			if (child is ActionBarMenuItem && child.isSearchField) {
				child.addSearchFilter(filter)
				break
			}
		}
	}

	fun getItem(id: Int): ActionBarMenuItem? {
		return findViewWithTag<View>(id) as? ActionBarMenuItem
	}

	override fun setEnabled(enabled: Boolean) {
		super.setEnabled(enabled)

		children.forEach {
			it.isEnabled = enabled
		}
	}

	val itemsMeasuredWidth: Int
		get() = children.sumOf { (it as? ActionBarMenuItem)?.measuredWidth ?: 0 }

	val visibleItemsMeasuredWidth: Int
		get() = children.sumOf { (it as? ActionBarMenuItem)?.let { item -> if (item.isVisible) item.measuredWidth else 0 } ?: 0 }

	fun searchFieldVisible(): Boolean {
		for (child in children) {
			if (child is ActionBarMenuItem && child.searchContainer?.isVisible == true) {
				return true
			}
		}

		return false
	}

	fun translateXItems(offset: Float) {
		children.forEach {
			(it as? ActionBarMenuItem)?.setTransitionOffset(offset)
		}
	}
}
