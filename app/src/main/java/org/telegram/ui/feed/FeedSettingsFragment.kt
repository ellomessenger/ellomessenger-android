/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.feed

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentFeedSettingsBinding
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.ElloRpc.readString
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment

class FeedSettingsFragment : BaseFragment() {
	private var binding: FragmentFeedSettingsBinding? = null
	private var allChannels: LongArray? = null
	private var hiddenChannels: LongArray? = null
	private var pinnedChannels: LongArray? = null
	private var showRecommended = false
	private var showSubscriptionsOnly = false
	private var showAdult = false

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(true)
		actionBar?.backButtonDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_back_arrow, null)
		actionBar?.setTitle(context.getString(R.string.feed_settings))

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

		binding = FragmentFeedSettingsBinding.inflate(LayoutInflater.from(context))

		fragmentView = binding?.root

		return binding?.root
	}

	private fun openChannelsSettings(type: Int) {
		val args = Bundle()
		args.putInt(FeedChannelsFragment.FEED_CHANNELS_TYPE, type)
		args.putLongArray(FeedChannelsFragment.ALL_CHANNELS_IDS, allChannels)
		args.putLongArray(FeedChannelsFragment.PINNED_CHANNELS_IDS, pinnedChannels)
		args.putLongArray(FeedChannelsFragment.HIDDEN_CHANNELS_IDS, hiddenChannels)
		args.putBoolean(FeedChannelsFragment.SHOW_RECOMMENDED, showRecommended)
		args.putBoolean(FeedChannelsFragment.SHOW_SUBS_ONLY, showSubscriptionsOnly)
		args.putBoolean(FeedChannelsFragment.SHOW_ADULT, showAdult)

		presentFragment(FeedChannelsFragment(args))
	}

	private fun setupListeners() {
		binding?.hiddenChannelsLayout?.setOnClickListener {
			if (allChannels == null) {
				// TODO: maybe show toast?
				return@setOnClickListener
			}

			openChannelsSettings(FeedChannelsFragment.FEED_CHANNELS_HIDDEN)
		}

		binding?.pinnedChannelsLayout?.setOnClickListener {
			if (allChannels == null) {
				// TODO: maybe show toast?
				return@setOnClickListener
			}

			openChannelsSettings(FeedChannelsFragment.FEED_CHANNELS_PINNED)
		}

		binding?.recommendationsSwitch?.setOnCheckedChangeListener { _, _ ->
			saveSettings()
		}

		binding?.subscriptionsSwitch?.setOnCheckedChangeListener { _, _ ->
			saveSettings()
		}

		binding?.showAdultSwitch?.setOnCheckedChangeListener { _, _ ->
			saveSettings()
		}
	}

	private fun setControlsEnabled(enabled: Boolean) {
		binding?.hiddenChannelsLayout?.isEnabled = enabled
		binding?.pinnedChannelsLayout?.isEnabled = enabled
		binding?.recommendationsSwitch?.isEnabled = enabled
		binding?.subscriptionsSwitch?.isEnabled = enabled
		binding?.showAdultSwitch?.isEnabled = enabled
	}

	private fun loadData() {
		setControlsEnabled(false)

		val req = ElloRpc.feedSettingsRequest()

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				val context = context ?: return@runOnUIThread

				if (error == null && response is TLRPC.TL_biz_dataRaw) {
					val data = response.readData<ElloRpc.FeedSettingsResponse>()

					allChannels = data?.allChannels?.toLongArray()
					hiddenChannels = data?.hiddenChannels?.toLongArray()
					pinnedChannels = data?.pinnedChannels?.toLongArray()
					showRecommended = data?.showRecommended ?: false
					showSubscriptionsOnly = data?.showSubscriptionsOnly ?: false
					showAdult = data?.showAdult ?: false

					FileLog.d("Feed settings: $data")
					FileLog.d("Feed settings: ${response.readString()}")

					binding?.hiddenChannelsCounter?.text = hiddenChannels?.size?.toString() ?: "0"
					binding?.pinnedChannelsCounter?.text = pinnedChannels?.size?.toString() ?: "0"
					binding?.recommendationsSwitch?.isChecked = showRecommended
					binding?.subscriptionsSwitch?.isChecked = showSubscriptionsOnly
					binding?.showAdultSwitch?.isChecked = showAdult
				}
				else if (error != null) {
					Toast.makeText(parentActivity, error.text, Toast.LENGTH_SHORT).show()
				}
				else {
					FileLog.e("Unexpected response type: ${response?.javaClass?.simpleName}")
					Toast.makeText(parentActivity, context.getString(R.string.unknown_error), Toast.LENGTH_SHORT).show()
				}

				setControlsEnabled(true)

				setupListeners()
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun saveSettings() {
		val req = ElloRpc.saveFeedSettingsRequest(
				hidden = hiddenChannels?.toSet()?.toLongArray(),
				pinned = pinnedChannels?.toSet()?.toLongArray(),
				showRecommended = binding?.recommendationsSwitch?.isChecked ?: false,
				showSubscriptionsOnly = binding?.subscriptionsSwitch?.isChecked ?: false,
				showAdult = binding?.showAdultSwitch?.isChecked ?: false,
		)

		connectionsManager.sendRequest(req, { response, error ->
			val context = context ?: return@sendRequest

			if (error == null && response is TLRPC.TL_biz_dataRaw) {
				val data = response.readData<ElloRpc.SimpleStatusResponse>()

				FileLog.d("Save feed settings: ${response.readString()}")

				AndroidUtilities.runOnUIThread {
					if (data?.status != true) {
						Toast.makeText(parentActivity, context.getString(R.string.unknown_error), Toast.LENGTH_SHORT).show()
					}
					else {
						showRecommended = binding?.recommendationsSwitch?.isChecked ?: showRecommended
						showSubscriptionsOnly = binding?.subscriptionsSwitch?.isChecked ?: showSubscriptionsOnly
						showAdult = binding?.showAdultSwitch?.isChecked ?: showAdult

						notificationCenter.postNotificationName(NotificationCenter.feedSettingsUpdated)
					}
				}
			}
			else if (error != null) {
				AndroidUtilities.runOnUIThread {
					Toast.makeText(parentActivity, error.text, Toast.LENGTH_SHORT).show()
				}
			}
			else {
				FileLog.e("Unexpected response type: ${response?.javaClass?.simpleName}")

				AndroidUtilities.runOnUIThread {
					Toast.makeText(parentActivity, context.getString(R.string.unknown_error), Toast.LENGTH_SHORT).show()
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	override fun onResume() {
		super.onResume()
		loadData()
	}

	override fun onFragmentDestroy() {
		binding = null
		super.onFragmentDestroy()
	}
}
