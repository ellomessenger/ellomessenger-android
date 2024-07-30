package org.telegram.ui.profile.utils

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration

class CustomDividerItemDecoration : ItemDecoration {
	private var divider: Drawable?
	private var paddingLeft: Float = 0f

	/**
	 * Default divider will be used
	 */
	constructor(context: Context) {
		val styledAttributes: TypedArray = context.obtainStyledAttributes(ATTRS)
		divider = styledAttributes.getDrawable(0)
		styledAttributes.recycle()
	}

	/**
	 * Custom divider will be used
	 */
	constructor(context: Context, @DrawableRes resId: Int, leftPaddingDp: Int) {
		divider = ContextCompat.getDrawable(context, resId)
		paddingLeft = leftPaddingDp.toPx
	}

	override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
		val left = parent.paddingLeft + paddingLeft
		val right = parent.width - parent.paddingRight
		val childCount = parent.childCount
		for (i in 0 until childCount) {
			val child: View = parent.getChildAt(i)
			val params = child.layoutParams as RecyclerView.LayoutParams
			val top: Int = child.bottom + params.bottomMargin
			val bottom = top + divider!!.intrinsicHeight

			divider!!.setBounds(left.toInt(), top, right, bottom)
			divider!!.draw(c)
		}
	}

	companion object {
		private val ATTRS = intArrayOf(android.R.attr.listDivider)
	}
}
