package org.telegram.ui.profile.transactions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.R
import org.telegram.messenger.databinding.ItemTransactionsBinding
import org.telegram.tgnet.ElloRpc
import org.telegram.ui.profile.utils.toFormattedDate

class TransactionsAdapter(private val action: (TransactionAction) -> Unit) : ListAdapter<TransactionModel, TransactionsAdapter.TransactionViewHolder>(CardsDiffCallBack()) {
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
		return TransactionViewHolder(ItemTransactionsBinding.inflate(LayoutInflater.from(parent.context), parent, false))
	}

	override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
		val item = currentList[position]
		holder.onBind(item, action)
	}

	class TransactionViewHolder(private val binding: ItemTransactionsBinding) : RecyclerView.ViewHolder(binding.root) {
		fun onBind(transactionModel: TransactionModel, action: (TransactionAction) -> Unit) {
			binding.run {
				when (transactionModel.status) {
					TransactionStatus.Approved -> {
						// unused
					}

					TransactionStatus.Rejected -> {
						statusIcon.setImageResource(R.drawable.ic_rejected)
						transactionStatus.setText(R.string.rejected)
						transactionStatus.setTextColor(ContextCompat.getColor(root.context, R.color.purple))
					}

					TransactionStatus.Pending -> {
						statusIcon.setImageResource(R.drawable.ic_pending)
						transactionStatus.setText(R.string.pending)
						transactionStatus.setTextColor(ContextCompat.getColor(root.context, R.color.orange_darker))
					}
				}

				fullName.text = transactionModel.user
				totalAmountValue.text = transactionModel.totalAmount.toString()
				commissionsValue.text = transactionModel.commissions.toString()
				receivedAmountValue.text = transactionModel.receivedDateMillis.toFormattedDate()
				withdrawalRequestValue.text = transactionModel.withdrawalRequestDateMillis.toFormattedDate()
				paymentMethodsValue.text = transactionModel.paymentDateMillis.toFormattedDate()
				transferValue.text = transactionModel.transferDateMillis.toFormattedDate()
			}
		}
	}

	private class CardsDiffCallBack : DiffUtil.ItemCallback<TransactionModel>() {
		override fun areItemsTheSame(oldItem: TransactionModel, newItem: TransactionModel): Boolean = oldItem == newItem

		override fun areContentsTheSame(oldItem: TransactionModel, newItem: TransactionModel): Boolean = oldItem.id == newItem.id
	}

	sealed class TransactionAction {
		data class Edit(val channel: TransactionModel) : TransactionAction()
		data class Open(val channel: ElloRpc.SubscriptionItem) : TransactionAction()
	}
}
