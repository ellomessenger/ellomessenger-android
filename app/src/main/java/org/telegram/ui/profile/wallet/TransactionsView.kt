/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.wallet

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.ui.Components.LayoutHelper
import kotlin.math.abs

class TransactionsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : ConstraintLayout(context, attrs, defStyle) {
	private val columns: List<View>

	init {
		var previousId = LayoutParams.PARENT_ID
		val backgrounds = mutableListOf<View>()

		layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)

		columns = (0 until numberOfColumns).map {
			val background = View(context)
			background.setBackgroundResource(R.drawable.transaction_stats_column_background)
			background.id = View.generateViewId()

			val previousView = backgrounds.lastOrNull()

			backgrounds.add(background)

			addView(background, LayoutHelper.createConstraint(6, 0).apply {
				topToTop = LayoutParams.PARENT_ID
				bottomToBottom = LayoutParams.PARENT_ID
				horizontalChainStyle = LayoutParams.CHAIN_SPREAD

				if (it == 0) {
					startToStart = LayoutParams.PARENT_ID
				}
				else {
					startToEnd = previousId
				}

				if (it == numberOfColumns - 1) {
					endToEnd = LayoutParams.PARENT_ID
				}
			})

			previousView?.updateLayoutParams<LayoutParams> {
				endToStart = background.id
			}

			previousId = background.id

			val foreground = View(context)
			foreground.id = View.generateViewId()
			foreground.setBackgroundResource(R.drawable.transaction_stats_column_foreground_green)

			addView(foreground, LayoutHelper.createConstraint(6, Int.MIN_VALUE).apply {
				bottomToBottom = background.id
				startToStart = background.id
				endToEnd = background.id
			})

			foreground
		}
	}

	fun setTransactions(transactions: List<ElloRpc.TransferStatsData>) {
		val normalizedTransactions = transactions.sortedBy { it.date }.toMutableList<ElloRpc.TransferStatsData?>()

		while (normalizedTransactions.size < numberOfColumns) {
			normalizedTransactions.add(0, null)
		}

		val max = normalizedTransactions.maxOf { abs(it?.amount ?: 0f) }

		columns.forEachIndexed { index, view ->
			val transaction = normalizedTransactions[index]

			if (transaction != null) {
				if (transaction.amount > 0) {
					view.setBackgroundResource(R.drawable.transaction_stats_column_foreground_green)
				}
				else {
					view.setBackgroundResource(R.drawable.transaction_stats_column_foreground_red)
				}

				view.visible()

				val height = (abs(transaction.amount) / max * height).toInt()
				view.updateLayoutParams<LayoutParams> { this.height = height }
			}
			else {
				view.gone()
			}
		}
	}

	companion object {
		const val WIDTH = 6
		const val HEIGHT = 57
		const val numberOfColumns = 21
	}
}
