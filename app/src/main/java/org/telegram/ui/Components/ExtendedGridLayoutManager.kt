/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, 2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.util.SparseIntArray
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import org.telegram.messenger.AndroidUtilities
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

open class ExtendedGridLayoutManager @JvmOverloads constructor(context: Context, spanCount: Int, private val lastRowFullWidth: Boolean = false, private val firstRowFullWidth: Boolean = false) : GridLayoutManager(context, spanCount) {
	private val itemSpans = SparseIntArray()
	private val itemsToRow = SparseIntArray()
	private var firstRowMax = 0
	private var rowsCount = 0
	private var calculatedWidth = 0

	override fun supportsPredictiveItemAnimations(): Boolean {
		return false
	}

	private fun prepareLayout(viewPortAvailableSize: Float) {
		@Suppress("NAME_SHADOWING") var viewPortAvailableSize = viewPortAvailableSize

		if (viewPortAvailableSize == 0f) {
			viewPortAvailableSize = 100f
		}

		itemSpans.clear()
		itemsToRow.clear()
		rowsCount = 0
		firstRowMax = 0

		val itemsCount = flowItemCount

		if (itemsCount == 0) {
			return
		}

		val preferredRowSize = AndroidUtilities.dp(100f)
		val spanCount = spanCount
		var spanLeft = spanCount
		var currentItemsInRow = 0
		var currentItemsSpanAmount = 0
		var a = 0
		val n = itemsCount + if (lastRowFullWidth) 1 else 0

		while (a < n) {
			if (a == 0 && firstRowFullWidth) {
				itemSpans.put(a, itemSpans[a] + spanCount)
				itemsToRow.put(0, rowsCount)
				rowsCount++

				currentItemsSpanAmount = 0
				currentItemsInRow = 0
				spanLeft = spanCount
				a++

				continue
			}

			val size = if (a < itemsCount) sizeForItem(a) else null
			var requiredSpan: Int
			var moveToNewRow: Boolean

			if (size == null) {
				moveToNewRow = currentItemsInRow != 0
				requiredSpan = spanCount
			}
			else {
				requiredSpan = min(spanCount, floor((spanCount * (size.width / size.height * preferredRowSize / viewPortAvailableSize)).toDouble()).toInt())
				moveToNewRow = spanLeft < requiredSpan || requiredSpan > 33 && spanLeft < requiredSpan - 15
			}

			if (moveToNewRow) {
				if (spanLeft != 0) {
					val spanPerItem = spanLeft / currentItemsInRow
					val start = a - currentItemsInRow
					var b = start

					while (b < start + currentItemsInRow) {
						if (b == start + currentItemsInRow - 1) {
							itemSpans.put(b, itemSpans[b] + spanLeft)
						}
						else {
							itemSpans.put(b, itemSpans[b] + spanPerItem)
						}

						spanLeft -= spanPerItem

						b++
					}

					itemsToRow.put(a - 1, rowsCount)
				}

				if (a == itemsCount) {
					break
				}

				rowsCount++

				currentItemsSpanAmount = 0
				currentItemsInRow = 0
				spanLeft = spanCount
			}
			else {
				if (spanLeft < requiredSpan) {
					requiredSpan = spanLeft
				}
			}

			if (rowsCount == 0) {
				firstRowMax = max(firstRowMax, a)
			}

			if (a == itemsCount - 1 && !lastRowFullWidth) {
				itemsToRow.put(a, rowsCount)
			}

			currentItemsSpanAmount += requiredSpan
			currentItemsInRow++
			spanLeft -= requiredSpan

			itemSpans.put(a, requiredSpan)

			a++
		}

		rowsCount++
	}

	private fun sizeForItem(i: Int): Size? {
		return fixSize(getSizeForItem(i))
	}

	fun fixSize(size: Size?): Size? {
		if (size == null) {
			return null
		}

		if (size.width == 0f) {
			size.width = 100f
		}

		if (size.height == 0f) {
			size.height = 100f
		}

		val aspect = size.width / size.height

		if (aspect > 4.0f || aspect < 0.2f) {
			size.width = max(size.width, size.height)
			size.height = size.width
		}

		return size
	}

	protected open fun getSizeForItem(i: Int): Size? {
		return Size(100f, 100f)
	}

	private fun checkLayout() {
		if (itemSpans.size() != flowItemCount || calculatedWidth != width) {
			calculatedWidth = width
			prepareLayout(width.toFloat())
		}
	}

	fun getSpanSizeForItem(i: Int): Int {
		checkLayout()
		return itemSpans[i]
	}

	fun getRowsCount(width: Int): Int {
		if (rowsCount == 0) {
			prepareLayout(width.toFloat())
		}

		return rowsCount
	}

	fun isLastInRow(i: Int): Boolean {
		checkLayout()
		return itemsToRow[i, Int.MAX_VALUE] != Int.MAX_VALUE
	}

	fun isFirstRow(i: Int): Boolean {
		checkLayout()
		return i <= firstRowMax
	}

	protected open val flowItemCount: Int
		get() = itemCount

	override fun getRowCountForAccessibility(recycler: Recycler, state: RecyclerView.State): Int {
		return state.itemCount
	}

	override fun getColumnCountForAccessibility(recycler: Recycler, state: RecyclerView.State): Int {
		return 1
	}
}
