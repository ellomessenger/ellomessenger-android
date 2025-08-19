/*
 * This is the source code of Ello
 *  for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.feed

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FeedChannelViewHolderBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.ElloRpc.readString
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper
import java.util.concurrent.atomic.AtomicIntegerArray

class FeedChannelsFragment(args: Bundle) : BaseFragment(args), NotificationCenterDelegate {
	private var recyclerView: RecyclerView? = null
	private var hiddenIds: LongArray? = null
	private var pinnedIds: LongArray? = null
	private var allIds: LongArray? = null
	private var channels: List<TLRPC.Chat>? = null
	private var loading: AtomicIntegerArray? = null
	private var type = FEED_CHANNELS_HIDDEN

	override fun onFragmentCreate(): Boolean {
		allIds = arguments?.getLongArray(ALL_CHANNELS_IDS)

		if (allIds == null || allIds?.isEmpty() == true) {
			return false
		}

		channels = allIds?.map { messagesController.getChat(it) }?.filterNotNull()

		if (channels == null || channels?.isEmpty() == true) {
			return false
		}

		loading = AtomicIntegerArray(channels?.size ?: 0)

		hiddenIds = arguments?.getLongArray(HIDDEN_CHANNELS_IDS)
		pinnedIds = arguments?.getLongArray(PINNED_CHANNELS_IDS)

		type = arguments?.getInt(FEED_CHANNELS_TYPE) ?: FEED_CHANNELS_HIDDEN

		notificationCenter.addObserver(this, NotificationCenter.chatInfoDidLoad)

		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		notificationCenter.removeObserver(this, NotificationCenter.chatInfoDidLoad)
	}

	override fun createView(context: Context): View {
		actionBar?.setAddToContainer(true)
		actionBar?.backButtonDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_back_arrow, null)

		when (arguments?.getInt(FEED_CHANNELS_TYPE)) {
			FEED_CHANNELS_PINNED -> actionBar?.setTitle(context.getString(R.string.pinned_channels))
			FEED_CHANNELS_HIDDEN -> actionBar?.setTitle(context.getString(R.string.hidden_channels))
			else -> actionBar?.setTitle(context.getString(R.string.feed_settings))
		}

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

		recyclerView = RecyclerView(context)
		recyclerView?.layoutManager = LinearLayoutManager(context)
		recyclerView?.adapter = ChannelsAdapter()

		val frameLayout = FrameLayout(context)
		frameLayout.addView(recyclerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

		fragmentView = frameLayout

		return frameLayout
	}

	private fun saveSettings(newHiddenIds: LongArray, newPinnedIds: LongArray) {
		FileLog.d("Save feed settings: hidden=${newHiddenIds.contentToString()}, pinned=${newPinnedIds.contentToString()}, showRecommended=${arguments?.getBoolean(SHOW_RECOMMENDED)}, showSubscriptionsOnly=${arguments?.getBoolean(SHOW_SUBS_ONLY)}, showAdult=${arguments?.getBoolean(SHOW_ADULT)}")

		val req = ElloRpc.saveFeedSettingsRequest(
				hidden = newHiddenIds,
				pinned = newPinnedIds,
				showRecommended = arguments?.getBoolean(SHOW_RECOMMENDED) ?: false,
				showSubscriptionsOnly = arguments?.getBoolean(SHOW_SUBS_ONLY) ?: false,
				showAdult = arguments?.getBoolean(SHOW_ADULT) ?: false,
		)

		ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				val context = context ?: return@runOnUIThread

				if (error == null && response is TLRPC.TLBizDataRaw) {
					val data = response.readData<ElloRpc.SimpleStatusResponse>()

					FileLog.d("Save feed settings: ${response.readString()}")

					if (data?.status != true) {
						Toast.makeText(parentActivity, context.getString(R.string.unknown_error), Toast.LENGTH_SHORT).show()
					}
					else {
						hiddenIds = newHiddenIds
						pinnedIds = newPinnedIds
					}
				}
				else if (error != null) {
					Toast.makeText(parentActivity, error.text, Toast.LENGTH_SHORT).show()
				}
				else {
					FileLog.e("Unexpected response type: ${response?.javaClass?.simpleName}")
					Toast.makeText(parentActivity, context.getString(R.string.unknown_error), Toast.LENGTH_SHORT).show()
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private inner class ChannelsAdapter : RecyclerView.Adapter<ChannelViewHolder>() {
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
			val binding = FeedChannelViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
			return ChannelViewHolder(binding)
		}

		override fun getItemCount(): Int {
			return channels?.size ?: 0
		}

		override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
			holder.channel = channels?.get(position)
		}
	}

	private inner class ChannelViewHolder(private val binding: FeedChannelViewHolderBinding) : ViewHolder(binding.root) {
		private val avatarDrawable = AvatarDrawable()
		private var avatarImage = AvatarImageView(binding.root.context)

		init {
			avatarImage.setRoundRadius(AndroidUtilities.dp(26f))

			binding.avatarContainer.addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			binding.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
				val id = channel?.id ?: return@setOnCheckedChangeListener
				var newHidden = hiddenIds ?: longArrayOf()
				var newPinned = pinnedIds ?: longArrayOf()

				if (type == FEED_CHANNELS_HIDDEN) {
					newHidden = if (isChecked) {
						newHidden + id
					}
					else {
						newHidden.filterNot { it == id }.toLongArray()
					}
				}
				else if (type == FEED_CHANNELS_PINNED) {
					newPinned = if (isChecked) {
						newPinned + id
					}
					else {
						newPinned.filterNot { it == id }.toLongArray()
					}
				}

				saveSettings(newHiddenIds = newHidden.toSet().toLongArray(), newPinnedIds = newPinned.toSet().toLongArray())
			}
		}

		var channel: TLRPC.Chat? = null
			set(value) {
				field = value

				if (value == null) {
					return
				}

				avatarDrawable.setInfo(value)
				avatarImage.setForUserOrChat(value, avatarDrawable)

				if (value.payType == TLRPC.PAY_TYPE_SUBSCRIBE || value.payType == TLRPC.PAY_TYPE_BASE) {
					binding.paidFeedIcon.visible()
				}
				else {
					binding.paidFeedIcon.gone()
				}

				if (ChatObject.isMasterclass(value)) {
					binding.paidFeedIcon.gone()
					binding.onlineCourseIcon.visible()
				}

				if (value.adult) {
					binding.adultChannelIcon.visible()
				}
				else {
					binding.adultChannelIcon.gone()
				}

				binding.nameLabel.text = value.title

				if (value.verified) {
					binding.verifiedIcon.visible()
				}
				else {
					binding.verifiedIcon.gone()
				}

				binding.enableSwitch.isChecked = if (type == FEED_CHANNELS_PINNED) (pinnedIds?.contains(value.id) ?: false) else (hiddenIds?.contains(value.id) ?: false)

				val channelId = value.id
				val info = messagesController.getChatFull(channelId)

				if (info == null) {
					val index = channels?.indexOf(channel) ?: return

					if (loading?.get(index) == 0) {
						loading?.set(index, 1)
						messagesController.loadFullChat(channelId, 0, true)
					}

					return
				}

				val subscribers = info.participantsCount

				binding.introLabel.text = binding.root.context.resources.getQuantityString(R.plurals.subscribers, subscribers, subscribers)
			}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.chatInfoDidLoad -> {
				val chatFull = args[0] as? ChatFull ?: return
				val channel = channels?.find { it.id == chatFull.id } ?: return
				val index = channels?.indexOf(channel) ?: return

				recyclerView?.adapter?.notifyItemChanged(index)

				loading?.set(index, 0)
			}
		}
	}

	companion object {
		const val HIDDEN_CHANNELS_IDS = "hidden_channels_ids"
		const val PINNED_CHANNELS_IDS = "pinned_channels_ids"
		const val ALL_CHANNELS_IDS = "all_channels_ids"
		const val SHOW_RECOMMENDED = "show_recommended"
		const val SHOW_SUBS_ONLY = "show_subs_only"
		const val SHOW_ADULT = "show_adult"
		const val FEED_CHANNELS_TYPE = "channels_type"
		const val FEED_CHANNELS_PINNED = 1
		const val FEED_CHANNELS_HIDDEN = 2
	}
}
