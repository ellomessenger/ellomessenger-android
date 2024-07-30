/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Components.Reactions

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.util.Consumer
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.TL_channelParticipantsRecent
import org.telegram.tgnet.TLRPC.TL_channels_getParticipants
import org.telegram.tgnet.TLRPC.TL_messageActionChatJoinedByRequest
import org.telegram.tgnet.TLRPC.TL_messages_getFullChat
import org.telegram.tgnet.TLRPC.TL_messages_getMessageReactionsList
import org.telegram.tgnet.TLRPC.TL_messages_getMessageReadParticipants
import org.telegram.tgnet.TLRPC.TL_messages_messageReactionsList
import org.telegram.tgnet.tlrpc.TL_channels_channelParticipants
import org.telegram.tgnet.tlrpc.TL_messages_chatFull
import org.telegram.tgnet.tlrpc.User
import org.telegram.tgnet.tlrpc.Vector
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarsDrawable
import org.telegram.ui.Components.AvatarsImageView
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.HideViewAfterAnimation
import org.telegram.ui.Components.LayoutHelper

class ReactedHeaderView(context: Context, private val currentAccount: Int, private val message: MessageObject) : FrameLayout(context) {
	private val flickerLoadingView = FlickerLoadingView(context)
	private val titleView = TextView(context)
	private val avatarsImageView = AvatarsImageView(context, false)
	private val iconView = ImageView(context)
	private val reactView = BackupImageView(context)
	private var ignoreLayout = false
	private val seenUsers = mutableListOf<User>()
	private val users = mutableListOf<User>()
	private var fixedWidth = 0
	private val isLoaded = false
	private var seenCallback: Consumer<List<User>>? = null

	init {
		flickerLoadingView.setColors(context.getColor(R.color.background), context.getColor(R.color.light_background), 0)
		flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_SEEN_TYPE)
		flickerLoadingView.setIsSingleCell(false)

		addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat()))

		titleView.setTextColor(context.getColor(R.color.text))
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		titleView.setLines(1)
		titleView.ellipsize = TextUtils.TruncateAt.END

		addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 40f, 0f, 62f, 0f))

		avatarsImageView.setStyle(AvatarsDrawable.STYLE_MESSAGE_SEEN)

		addView(avatarsImageView, LayoutHelper.createFrameRelatively((24 + 12 + 12 + 8).toFloat(), LayoutHelper.MATCH_PARENT.toFloat(), Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 0f, 0f))

		addView(iconView, LayoutHelper.createFrameRelatively(24f, 24f, Gravity.START or Gravity.CENTER_VERTICAL, 11f, 0f, 0f, 0f))

		val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.msg_reactions, null)?.mutate()
		drawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.SRC_IN)

		iconView.setImageDrawable(drawable)
		iconView.gone()

		addView(reactView, LayoutHelper.createFrameRelatively(24f, 24f, Gravity.START or Gravity.CENTER_VERTICAL, 11f, 0f, 0f, 0f))

		titleView.alpha = 0f
		avatarsImageView.alpha = 0f

		background = Theme.getSelectorDrawable(false)
	}

	fun setSeenCallback(seenCallback: Consumer<List<User>>?) {
		this.seenCallback = seenCallback
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		if (!isLoaded) {
			val ctrl = MessagesController.getInstance(currentAccount)
			val chat = ctrl.getChat(message.chatId)
			val chatInfo = ctrl.getChatFull(message.chatId)
			val showSeen = chat != null && message.isOutOwner && message.isSent && !message.isEditing && !message.isSending && !message.isSendError && !message.isContentUnread && !message.isUnread && ConnectionsManager.getInstance(currentAccount).currentTime - message.messageOwner!!.date < 7 * 86400 && (ChatObject.isMegagroup(chat) || !ChatObject.isChannel(chat)) && chatInfo != null && chatInfo.participants_count <= MessagesController.getInstance(currentAccount).chatReadMarkSizeThreshold && message.messageOwner?.action !is TL_messageActionChatJoinedByRequest

			if (showSeen) {
				val req = TL_messages_getMessageReadParticipants()
				req.msg_id = message.id
				req.peer = MessagesController.getInstance(currentAccount).getInputPeer(message.dialogId)

				val fromId = message.messageOwner?.from_id?.user_id ?: 0

				ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, _ ->
					if (response is Vector) {
						val usersToRequest: MutableList<Long> = ArrayList()

						for (obj in response.objects) {
							if (obj is Long) {
								if (fromId != obj) {
									usersToRequest.add(obj)
								}
							}
						}

						usersToRequest.add(fromId)

						val usersRes = mutableListOf<User>()

						val callback = Runnable {
							seenUsers.addAll(usersRes)

							for (u in usersRes) {
								var hasSame = false

								for (i in users.indices) {
									if (users[i].id == u.id) {
										hasSame = true
										break
									}
								}

								if (!hasSame) {
									users.add(u)
								}
							}

							seenCallback?.accept(usersRes)

							loadReactions()
						}

						if (ChatObject.isChannel(chat)) {
							val usersReq = TL_channels_getParticipants()
							usersReq.limit = MessagesController.getInstance(currentAccount).chatReadMarkSizeThreshold
							usersReq.offset = 0
							usersReq.filter = TL_channelParticipantsRecent()
							usersReq.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat.id)

							ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq) { response1, _ ->
								AndroidUtilities.runOnUIThread {
									if (response1 != null) {
										val users = response1 as TL_channels_channelParticipants

										for (i in users.users.indices) {
											val user = users.users[i]

											MessagesController.getInstance(currentAccount).putUser(user, false)

											if (!user.self && usersToRequest.contains(user.id)) {
												usersRes.add(user)
											}
										}
									}

									callback.run()
								}
							}
						}
						else {
							val usersReq = TL_messages_getFullChat()
							usersReq.chat_id = chat!!.id

							ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq) { response1, _ ->
								AndroidUtilities.runOnUIThread {
									if (response1 != null) {
										val chatFull = response1 as TL_messages_chatFull

										for (i in chatFull.users.indices) {
											val user = chatFull.users[i]

											MessagesController.getInstance(currentAccount).putUser(user, false)

											if (!user.self && usersToRequest.contains(user.id)) {
												usersRes.add(user)
											}
										}
									}

									callback.run()
								}
							}
						}
					}
				}, ConnectionsManager.RequestFlagInvokeAfter)
			}
			else {
				loadReactions()
			}
		}
	}

	private fun loadReactions() {
		val ctrl = MessagesController.getInstance(currentAccount)

		val getList = TL_messages_getMessageReactionsList()
		getList.peer = ctrl.getInputPeer(message.dialogId)
		getList.id = message.id
		getList.limit = 3
		getList.reaction = null
		getList.offset = null

		ConnectionsManager.getInstance(currentAccount).sendRequest(getList, { response, _ ->
			if (response is TL_messages_messageReactionsList) {
				val c = response.count

				post {
					val str = if (seenUsers.isEmpty() || seenUsers.size < c) {
						LocaleController.formatPluralString("ReactionsCount", c)
					}
					else {
						val countStr = if (c == seenUsers.size) {
							c.toString()
						}
						else {
							c.toString() + "/" + seenUsers.size
						}

						String.format(LocaleController.getPluralString("Reacted", c), countStr)
					}

					if (measuredWidth > 0) {
						fixedWidth = measuredWidth
					}

					titleView.text = str

					val showIcon = true

//					if (message.messageOwner.reactions != null && message.messageOwner.reactions.results.size == 1 && response.reactions.isNotEmpty()) {
//						for (r in MediaDataController.getInstance(currentAccount).reactionsList) {
//							if (r.reaction == response.reactions[0].reaction) {
//								reactView.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_lastframe", "webp", null, r)
//								reactView.visibility = VISIBLE
//								reactView.alpha = 0f
//								reactView.animate().alpha(1f).start()
//								iconView.visibility = GONE
//								showIcon = false
//								break
//							}
//						}
//					}

					if (showIcon) {
						iconView.visible()
						iconView.alpha = 0f
						iconView.animate().alpha(1f).start()
					}

					for (u in response.users) {
						if (message.messageOwner?.from_id != null && u.id != message.messageOwner?.from_id?.user_id) {
							var hasSame = false

							for (i in users.indices) {
								if (users[i].id == u.id) {
									hasSame = true
									break
								}
							}

							if (!hasSame) {
								users.add(u)
							}
						}
					}

					updateView()
				}
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun getSeenUsers(): List<User> {
		return seenUsers
	}

	private fun updateView() {
		isEnabled = users.size > 0

		for (i in 0..2) {
			if (i < users.size) {
				avatarsImageView.setObject(i, currentAccount, users[i])
			}
			else {
				avatarsImageView.setObject(i, currentAccount, null)
			}
		}

		val tX = when (users.size) {
			1 -> AndroidUtilities.dp(24f).toFloat()
			2 -> AndroidUtilities.dp(12f).toFloat()
			else -> 0f
		}

		avatarsImageView.translationX = if (LocaleController.isRTL) AndroidUtilities.dp(12f).toFloat() else tX
		avatarsImageView.commitTransition(false)

		titleView.animate().alpha(1f).setDuration(220).start()

		avatarsImageView.animate().alpha(1f).setDuration(220).start()
		flickerLoadingView.animate().alpha(0f).setDuration(220).setListener(HideViewAfterAnimation(flickerLoadingView)).start()
	}

	override fun requestLayout() {
		if (ignoreLayout) {
			return
		}

		super.requestLayout()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		@Suppress("NAME_SHADOWING") var widthMeasureSpec = widthMeasureSpec

		if (fixedWidth > 0) {
			widthMeasureSpec = MeasureSpec.makeMeasureSpec(fixedWidth, MeasureSpec.EXACTLY)
		}

		if (flickerLoadingView.visibility == VISIBLE) {
			// Idk what is happening here, but this class is a clone of MessageSeenView, so this might help with something?
			ignoreLayout = true
			flickerLoadingView.gone()

			super.onMeasure(widthMeasureSpec, heightMeasureSpec)

			flickerLoadingView.layoutParams.width = measuredWidth
			flickerLoadingView.visibility = VISIBLE

			ignoreLayout = false

			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
		else {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
	}
}
