/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.ui.profile

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.databinding.LeftoversMyChannelViewHolderBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.bot
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView

@SuppressLint("NotifyDataSetChanged")
class DeleteAccountMyChannels(args: Bundle?) : BaseFragment(args), NotificationCenter.NotificationCenterDelegate {
	private var recyclerView: RecyclerView? = null
	private var progressBar: ContentLoadingProgressBar? = null
	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private val ownChats = mutableListOf<TLRPC.Chat>()
	private var ids: LongArray? = null
	private var emptyView: LinearLayout? = null

	override fun onFragmentCreate(): Boolean {
		ids = arguments?.getLongArray(EXTRA_CHATS) ?: return false
		notificationCenter.addObserver(this, NotificationCenter.needDeleteDialog)
		return true
	}

	override fun createView(context: Context): View {
		actionBar?.setAddToContainer(true)
		actionBar?.setTitle(context.getString(R.string.my_paid_channels))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		val frameLayout = FrameLayout(context)

		recyclerView = RecyclerView(context)
		recyclerView?.id = View.generateViewId()
		recyclerView?.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat())
		recyclerView?.layoutManager = LinearLayoutManager(context).apply { orientation = LinearLayoutManager.VERTICAL }
		recyclerView?.adapter = MyChannelsAdapter()
		recyclerView?.gone()

		frameLayout.addView(recyclerView)

		progressBar = ContentLoadingProgressBar(context)
		progressBar?.id = View.generateViewId()
		progressBar?.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat())
		progressBar?.isIndeterminate = true
		progressBar?.indeterminateTintList = context.resources.getColorStateList(R.color.brand, null)
		progressBar?.hide()

		frameLayout.addView(progressBar)

		emptyView = LinearLayout(context)
		emptyView?.id = View.generateViewId()
		emptyView?.gravity = Gravity.CENTER
		emptyView?.setPadding(AndroidUtilities.dp(12f))
		emptyView?.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat())
		emptyView?.orientation = LinearLayout.VERTICAL

		val emptyImage = RLottieImageView(context)
		emptyImage.setAutoRepeat(true)
		emptyImage.setAnimation(R.raw.panda_chat_list_no_results, 160, 160)
		emptyImage.playAnimation()

		emptyView?.addView(emptyImage, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

		val textView = TextView(context)
		textView.gravity = Gravity.CENTER
		textView.text = context.getString(R.string.no_own_channels)
		textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.disabled_text, null))

		emptyView?.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24f, 6f, 24f, 0f))

		emptyView?.gone()

		frameLayout.addView(emptyView)

		fragmentView = frameLayout

		return frameLayout
	}

	override fun onResume() {
		super.onResume()

		ioScope.launch {
			delay(300)
			loadData()
		}
	}

	private suspend fun loadData() {
		withContext(mainScope.coroutineContext) {
			progressBar?.show()
		}

		ownChats.clear()

		ids?.let {
			for (id in it) {
				val remoteChat = messagesController.loadChat(id, 0, true)

				if (remoteChat != null) {
					ownChats.add(remoteChat)
				}
			}
		}

		withContext(mainScope.coroutineContext) {
			reload()
		}
	}

	private fun reload() {
		recyclerView?.adapter?.notifyDataSetChanged()

		if (ownChats.isEmpty()) {
			recyclerView?.gone()
			emptyView?.visible()
		}
		else {
			emptyView?.gone()
			recyclerView?.visible()
		}

		progressBar?.hide()
	}

	override fun onPause() {
		super.onPause()
		ioScope.coroutineContext.cancelChildren()
		mainScope.coroutineContext.cancelChildren()
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		notificationCenter.removeObserver(this, NotificationCenter.needDeleteDialog)

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}
	}

	private inner class MyChannelsAdapter : RecyclerView.Adapter<MyChannelViewHolder>() {
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyChannelViewHolder {
			return MyChannelViewHolder(LeftoversMyChannelViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
		}

		override fun getItemCount(): Int {
			return ownChats.size
		}

		override fun onBindViewHolder(holder: MyChannelViewHolder, position: Int) {
			val chat = ownChats[position]

			holder.bind(chat)

			holder.setOnClickListener {
				val args = Bundle()
				args.putInt("dialog_folder_id", 0)
				args.putInt("dialog_filter_id", 0)
				args.putLong("chat_id", chat.id)

				if (!messagesController.checkCanOpenChat(args, this@DeleteAccountMyChannels)) {
					return@setOnClickListener
				}

				presentFragment(ChatActivity(args))
			}
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.needDeleteDialog -> {
				if (fragmentView == null || isPaused) {
					return
				}

				val dialogId = args[0] as Long
				val user = args[1] as? User
				val chat = args[2] as? TLRPC.Chat
				val revoke = (args[3] as? Boolean) ?: false

				ids?.let { ids ->
					if (-dialogId in ids) {
						ownChats.removeIf {
							it.id == -dialogId
						}

						reload()
					}
				}

				if (chat != null) {
					if (ChatObject.isNotInChat(chat)) {
						messagesController.deleteDialog(dialogId, 0, revoke)
					}
					else {
						messagesController.deleteParticipantFromChat(-dialogId, messagesController.getUser(userConfig.getClientUserId()), null, revoke, revoke)
					}

					if (ChatObject.isSubscriptionChannel(chat) && !chat.creator) {
						val request = ElloRpc.unsubscribeRequest(chat.id, ElloRpc.PEER_TYPE_CHANNEL)

						connectionsManager.sendRequest(request) { _, error ->
							AndroidUtilities.runOnUIThread {
								if (error != null) {
									FileLog.e("unsubscribe(" + chat.id + ") error(" + error.code + "): " + error.text)
								}
								else {
									FileLog.d("unsubscribe(success)")
								}
							}
						}
					}
				}
				else {
					messagesController.deleteDialog(dialogId, 0, revoke)

					if (user?.bot == true) {
						messagesController.blockPeer(user.id)
					}
				}
			}
		}
	}

	companion object {
		const val EXTRA_CHATS = "chats"
	}
}
