package org.telegram.ui.profile.earnings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentEarningsBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.profile.subscriptions_channels.SubscriptionChannelsFragment
import org.telegram.ui.profile.transactions.TransactionsFragment

class EarningsFragment : BaseFragment() {
	private var binding: FragmentEarningsBinding? = null

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(true)
		actionBar?.backButtonDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_back_arrow, null)
		actionBar?.setTitle(context.getString(R.string.earnings))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (parentActivity == null) {
					return
				}

				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = FragmentEarningsBinding.inflate(LayoutInflater.from(context))

		binding?.subscriptionChannels?.setOnClickListener {
			presentFragment(SubscriptionChannelsFragment())
		}

		binding?.totalBalance?.setOnClickListener {
			presentFragment(TransactionsFragment())
		}

		binding?.transferOut?.setOnClickListener {
			// TODO: open transfer out fragment
			Toast.makeText(it.context, "TODO: Transfer out", Toast.LENGTH_SHORT).show()
		}

		binding?.paymentsOptions?.setOnClickListener {
			// TODO: open payments options fragment
			Toast.makeText(it.context, "TODO: Payments options", Toast.LENGTH_SHORT).show()
		}

		fragmentView = binding?.root

		return binding?.root
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}
}
