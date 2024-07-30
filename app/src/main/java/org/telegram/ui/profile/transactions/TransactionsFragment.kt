package org.telegram.ui.profile.transactions

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentTransactionsBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.profile.utils.CustomDividerItemDecoration
import kotlin.random.Random

class TransactionsFragment : BaseFragment() {
	private var binding: FragmentTransactionsBinding? = null
	private var adapter: TransactionsAdapter? = null
	private val totalAmount by lazy { Random.nextInt(3000) }

	override fun createActionBar(context: Context): ActionBar {
		val actionBar = ActionBar(context)
		actionBar.setAddToContainer(true)
		actionBar.backButtonDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_back_arrow, null)
		actionBar.setTitle(context.getString(R.string.transactions))

		actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (parentActivity == null) {
					return
				}

				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}
					filterItem -> {
						presentFragment(FilterTransactionsFragment())
					}
				}
			}
		})

		val menu = actionBar.createMenu()

		menu.addItem(filterItem, R.drawable.filter)

		return actionBar
	}

	override fun createView(context: Context): View? {
		binding = FragmentTransactionsBinding.inflate(LayoutInflater.from(context))

		adapter = TransactionsAdapter {
			// TODO: handle action
		}

		binding?.transactionsList?.adapter = adapter

		adapter?.submitList(provideFakeTransactions())

		val itemDecorator = CustomDividerItemDecoration(context, R.drawable.subscription_item_divider, 81)

		binding?.transactionsList?.addItemDecoration(itemDecorator)
		binding?.transactionsList?.layoutManager = LinearLayoutManager(context)

		binding?.totalAmount?.text = totalAmount.toString()

		fragmentView = binding?.root

		return binding?.root
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}

	companion object {
		private const val filterItem = 0
	}
}
