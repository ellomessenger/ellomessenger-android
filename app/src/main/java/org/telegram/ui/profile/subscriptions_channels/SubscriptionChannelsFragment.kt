package org.telegram.ui.profile.subscriptions_channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentSubscriptionChannelsBinding
import org.telegram.tgnet.ElloRpc
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.profile.utils.CustomDividerItemDecoration

class SubscriptionChannelsFragment : BaseFragment() {
	private var binding: FragmentSubscriptionChannelsBinding? = null
	private var adapter: SubscriptionChannelsAdapter? = null
	private val fakeTotals by lazy { Totals() }
	private val channels: List<ElloRpc.SubscriptionItem> = emptyList()
	//private val channels by lazy { provideFakeChannels() }

	override fun createActionBar(context: Context): ActionBar {
		val actionBar = ActionBar(context)
		actionBar.setAddToContainer(true)
		actionBar.backButtonDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_back_arrow, null)
		actionBar.setTitle(context.getString(R.string.subscription_channels))

		actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (parentActivity == null) {
					return
				}

				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		return actionBar
	}

	override fun createView(context: Context): View? {
		binding = FragmentSubscriptionChannelsBinding.inflate(LayoutInflater.from(context))

		adapter = SubscriptionChannelsAdapter {
			// TODO: handle actions
		}

		binding?.channels?.adapter = adapter

		val itemDecorator = CustomDividerItemDecoration(context, R.drawable.subscription_item_divider, 81)

		binding?.channels?.addItemDecoration(itemDecorator)
		binding?.channels?.layoutManager = LinearLayoutManager(context)

		loadData()

		fragmentView = binding?.root

		return binding?.root
	}

	private fun showNoChannelsView() {
		binding?.totalsLayout?.isVisible = false
		binding?.channels?.isVisible = false
		binding?.noChannelsView?.isVisible = true

		binding?.createChannel?.setOnClickListener {
			showNoChannelsViewInfo()
		}
	}

	private fun showNoChannelsViewInfo() {
		binding?.noChannelsView?.isVisible = false
		binding?.noChannelsViewInfo?.isVisible = true

		binding?.createChannelInfo?.setOnClickListener {
			// TODO: add navigation
			loadData()
		}
	}

	private fun loadData() {
		binding?.availableValue?.text = fakeTotals.available.toString()
		binding?.earnedLastMonthValue?.text = fakeTotals.earnedLastMonth.toString()
		binding?.totalEarnedValue?.text = fakeTotals.totalEarned.toString()

		adapter?.submitList(channels)

		if (channels.isEmpty()) {
			showNoChannelsView()
		}
		else {
			binding?.totalsLayout?.isVisible = true
			binding?.channels?.isVisible = true
			binding?.noChannelsView?.isVisible = false
			binding?.noChannelsViewInfo?.isVisible = false
		}
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}
}
