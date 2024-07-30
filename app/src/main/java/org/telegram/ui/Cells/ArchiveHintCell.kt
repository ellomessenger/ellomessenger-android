/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.database.DataSetObserver
import android.os.Parcelable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.Components.BottomPagesView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class ArchiveHintCell(context: Context) : FrameLayout(context) {
	private lateinit var bottomPages: BottomPagesView
	val viewPager: ViewPager

	init {
		viewPager = object : ViewPager(context) {
			override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
				parent?.requestDisallowInterceptTouchEvent(true)
				return super.onInterceptTouchEvent(ev)
			}

			override fun onAttachedToWindow() {
				super.onAttachedToWindow()
				requestLayout()
			}
		}

		AndroidUtilities.setViewPagerEdgeEffectColor(viewPager, context.getColor(R.color.medium_gray))

		viewPager.setAdapter(Adapter())
		viewPager.setPageMargin(0)
		viewPager.setOffscreenPageLimit(1)

		addView(viewPager, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		viewPager.addOnPageChangeListener(object : OnPageChangeListener {
			override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
				bottomPages.setPageOffset(position, positionOffset)
			}

			override fun onPageSelected(i: Int) {}
			override fun onPageScrollStateChanged(i: Int) {}
		})

		bottomPages = BottomPagesView(context, viewPager, 2)
		bottomPages.setColor(context.getColor(R.color.medium_gray), context.getColor(R.color.brand))

		addView(bottomPages, createFrame(33, 5f, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0f, 0f, 0f, 19f))
	}

	override fun invalidate() {
		super.invalidate()
		bottomPages.invalidate()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(327f), MeasureSpec.EXACTLY))
	}

	private inner class Adapter : PagerAdapter() {
		override fun getCount(): Int {
			return 2
		}

		override fun instantiateItem(container: ViewGroup, position: Int): Any {
			val innerCell = ArchiveHintInnerCell(container.context, position)

			if (innerCell.parent != null) {
				val parent = innerCell.parent as ViewGroup
				parent.removeView(innerCell)
			}

			container.addView(innerCell, 0)

			return innerCell
		}

		override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
			container.removeView(`object` as View)
		}

		override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
			super.setPrimaryItem(container, position, `object`)
			bottomPages.setCurrentPage(position)
		}

		override fun isViewFromObject(view: View, `object`: Any): Boolean {
			return view == `object`
		}
	}
}
