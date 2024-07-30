/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.wallet

import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.beint.elloapp.allCornersProvider
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.TransactionsViewLayoutBinding
import org.telegram.tgnet.ElloRpc
import org.telegram.ui.Components.LayoutHelper
import kotlin.math.abs

class TransactionsStatsViewHolder(private val binding: TransactionsViewLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
	private var stats: ElloRpc.TransferStats? = null
	private var columns = mutableListOf<View>()
	private var period = TransactionsHistoryStatsDataSource.Period.WEEK
	private var type = TransactionsHistoryStatsDataSource.Type.DEPOSIT
	private val graphLinearContainer = LinearLayout(binding.root.context)

	init {
		graphLinearContainer.orientation = LinearLayout.HORIZONTAL
		graphLinearContainer.setHorizontalGravity(Gravity.CENTER_HORIZONTAL)
		graphLinearContainer.setVerticalGravity(Gravity.BOTTOM)

		binding.incomingTransactionsButton.isSelected = true

		binding.incomingTransactionsButton.clipToOutline = true
		binding.incomingTransactionsButton.outlineProvider = allCornersProvider(AndroidUtilities.dp(8f).toFloat())

		binding.outgoingTransactionsButton.clipToOutline = true
		binding.outgoingTransactionsButton.outlineProvider = allCornersProvider(AndroidUtilities.dp(8f).toFloat())

		var previousId = 0
		var previousLine: View? = null

		for (i in 0 until 7) {
			val line = View(binding.root.context)
			line.id = View.generateViewId()

			line.layoutParams = LayoutHelper.createConstraint(0, 0).also {
				it.width = 0
				it.height = AndroidUtilities.dp(0.32f)

				if (i == 0) {
					it.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
				}
				else {
					if (i != 6) {
						it.topToBottom = previousId
					}
				}

				if (i == 6) {
					it.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
				}

				it.verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_SPREAD
			}

			line.setBackgroundResource(R.color.gray_border)

			binding.graphContainer.addView(line)

			if (i > 1) {
				previousLine?.updateLayoutParams<ConstraintLayout.LayoutParams> {
					bottomToTop = line.id
				}
			}

			previousId = line.id
			previousLine = line
		}

		binding.graphContainer.addView(graphLinearContainer, LayoutHelper.createConstraint(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))

		binding.incomingTransactionsButton.setOnClickListener {
			binding.incomingTransactionsButton.isSelected = true
			binding.outgoingTransactionsButton.isSelected = false

			type = TransactionsHistoryStatsDataSource.Type.DEPOSIT

			reload()
		}

		binding.outgoingTransactionsButton.setOnClickListener {
			binding.incomingTransactionsButton.isSelected = false
			binding.outgoingTransactionsButton.isSelected = true

			type = TransactionsHistoryStatsDataSource.Type.WITHDRAW

			reload()
		}
	}

	fun bind(stats: ElloRpc.TransferStats?, period: TransactionsHistoryStatsDataSource.Period) {
		if (stats == null) {
			return
		}

		this.stats = stats
		this.period = period

		reload()
	}

	private fun reload() {
		val stats = stats ?: return

		val transactions = when (type) {
			TransactionsHistoryStatsDataSource.Type.DEPOSIT -> stats.data?.filter { it.type == "deposit" } ?: emptyList()
			TransactionsHistoryStatsDataSource.Type.WITHDRAW -> stats.data?.filter { it.type == "withdraw" } ?: emptyList()
			else -> emptyList()
		}.sortedBy {
			it.date
		}

		val amount = transactions.sumOf { abs(it.amount.toDouble()) }.toFloat()

		setAmount(amount)

		columns.forEach {
			(it.parent as? ViewGroup)?.removeView(it)
		}

		columns.clear()

		binding.xLabelsContainer.removeAllViews()
		binding.yLabelsContainer.removeAllViews()

		graphLinearContainer.removeAllViews()

		val maxAmount = abs(transactions.maxOfOrNull { abs(it.amount) } ?: 0f)

		val zeroAmountLabel = TextView(binding.root.context).also {
			it.text = binding.root.context.getString(R.string.simple_money_int_format, 0f)
			it.setTextColor(ResourcesCompat.getColor(binding.root.resources, R.color.disabled_text, null))
			it.gravity = Gravity.BOTTOM

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				it.setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, TypedValue.COMPLEX_UNIT_SP)
			}
			else {
				it.textSize = 11f
			}

			it.maxLines = 1
			it.ellipsize = TextUtils.TruncateAt.END
		}

		if (maxAmount == 0f) {
			binding.yLabelsContainer.addView(zeroAmountLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))
			return
		}

		val halfAmount = maxAmount / 2f

		val maxAmountLabel = TextView(binding.root.context).also {
			it.text = binding.root.context.getString(R.string.simple_money_int_format, maxAmount)
			it.setTextColor(ResourcesCompat.getColor(binding.root.resources, R.color.disabled_text, null))
			it.gravity = Gravity.TOP

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				it.setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, TypedValue.COMPLEX_UNIT_SP)
			}
			else {
				it.textSize = 11f
			}

			it.maxLines = 1
			it.ellipsize = TextUtils.TruncateAt.END
		}

		val halfAmountLabel = TextView(binding.root.context).also {
			it.text = binding.root.context.getString(R.string.simple_money_int_format, halfAmount)
			it.setTextColor(ResourcesCompat.getColor(binding.root.resources, R.color.disabled_text, null))
			it.gravity = Gravity.CENTER_VERTICAL

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				it.setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, TypedValue.COMPLEX_UNIT_SP)
			}
			else {
				it.textSize = 11f
			}

			it.maxLines = 1
			it.ellipsize = TextUtils.TruncateAt.END
		}

		binding.yLabelsContainer.addView(maxAmountLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))
		binding.yLabelsContainer.addView(halfAmountLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))
		binding.yLabelsContainer.addView(zeroAmountLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))

		transactions.forEach { transaction ->
			val xLabel = TextView(binding.root.context).also {
				it.text = transaction.period
				it.setTextColor(ResourcesCompat.getColor(binding.root.resources, R.color.disabled_text, null))
				it.gravity = Gravity.CENTER_HORIZONTAL
				it.textSize = 11f
			}

			binding.xLabelsContainer.addView(xLabel, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f))

			val column = View(binding.root.context).also {
				val resId = when (type) {
					TransactionsHistoryStatsDataSource.Type.DEPOSIT -> R.drawable.large_transaction_stats_column_foreground_green
					TransactionsHistoryStatsDataSource.Type.WITHDRAW -> R.drawable.large_transaction_stats_column_foreground_red
					else -> throw IllegalStateException("Unsupported type")
				}

				it.setBackgroundResource(resId)
			}

			val height = GRAPH_HEIGHT * abs(transaction.amount) / maxAmount

			val columnWrapper = FrameLayout(binding.root.context).also {
				it.addView(column, LayoutHelper.createFrame(18, height.toInt(), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM))
			}

			graphLinearContainer.addView(columnWrapper, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f))
		}
	}

	private fun setAmount(amount: Float) {
		if (amount < 0 || type == TransactionsHistoryStatsDataSource.Type.WITHDRAW) {
			// binding.amountLabel.setTextColor(ResourcesCompat.getColor(binding.root.resources, R.color.purple, null))
			binding.amountLabel.text = binding.root.context.getString(R.string.withdraw_amount_format, abs(amount))
		}
		else {
			// binding.amountLabel.setTextColor(ResourcesCompat.getColor(binding.root.resources, R.color.text, null))
			binding.amountLabel.text = binding.root.context.getString(R.string.simple_money_format, abs(amount))
		}
	}

	companion object {
		private const val GRAPH_HEIGHT = 130f
	}
}
