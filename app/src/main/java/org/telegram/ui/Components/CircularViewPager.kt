package org.telegram.ui.Components

import android.content.Context
import android.util.AttributeSet
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

open class CircularViewPager : ViewPager {
	private var adapter: Adapter? = null

	constructor(context: Context) : super(context)
	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

	init {
		addOnPageChangeListener(object : OnPageChangeListener {
			private var scrollState = 0

			override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
				if (position == currentItem && positionOffset == 0f && scrollState == SCROLL_STATE_DRAGGING) {
					checkCurrentItem()
				}
			}

			override fun onPageSelected(position: Int) {

			}

			override fun onPageScrollStateChanged(state: Int) {
				if (state == SCROLL_STATE_IDLE) {
					checkCurrentItem()
				}

				scrollState = state
			}

			private fun checkCurrentItem() {
				val adapter = adapter ?: return
				val position = currentItem
				val newPosition = adapter.extraCount + adapter.getRealPosition(position)

				if (position != newPosition) {
					setCurrentItem(newPosition, false)
				}
			}
		})
	}

	override fun setAdapter(adapter: PagerAdapter?) {
		if (adapter is Adapter) {
			setAdapter(adapter)
		}
		else {
			throw IllegalArgumentException()
		}
	}

	fun setAdapter(adapter: Adapter?) {
		this.adapter = adapter

		super.setAdapter(adapter)

		if (adapter != null) {
			setCurrentItem(adapter.extraCount, false)
		}
	}

	abstract class Adapter : PagerAdapter() {
		fun getRealPosition(adapterPosition: Int): Int {
			val count = count
			val extraCount = extraCount

			return if (adapterPosition < extraCount) {
				count - extraCount * 2 - (extraCount - adapterPosition - 1) - 1
			}
			else if (adapterPosition >= count - extraCount) {
				adapterPosition - (count - extraCount)
			}
			else {
				adapterPosition - extraCount
			}
		}

		abstract val extraCount: Int
	}
}
