/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject.isChannel
import org.telegram.messenger.ChatObject.isChannelAndNotMegaGroup
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatInvite
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.tgnet.sizes
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.JoinSheetUserCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.Bulletin.TwoLineLottieLayout
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import kotlin.math.max

class JoinGroupAlert(context: Context, obj: TLObject?, private val hash: String, private val fragment: BaseFragment?) : BottomSheet(context, false) {
	private val chatInvite: ChatInvite?
	private val currentChat: Chat?
	private var requestTextView: TextView? = null
	private var requestProgressView: RadialProgressView? = null

	init {
		setApplyBottomPadding(false)
		setApplyTopPadding(false)

		fixNavigationBar(ResourcesCompat.getColor(context.resources, R.color.background, null))

		when (obj) {
			is ChatInvite -> {
				chatInvite = obj
				currentChat = null
			}

			is Chat -> {
				chatInvite = null
				currentChat = obj
			}

			else -> {
				chatInvite = null
				currentChat = null
			}
		}

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.VERTICAL
		linearLayout.isClickable = true

		val frameLayout = FrameLayout(context)
		frameLayout.addView(linearLayout)

		val scrollView = NestedScrollView(context)
		scrollView.addView(frameLayout)

		setCustomView(scrollView)

		val closeView = ImageView(context)
		closeView.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.light_background, null))
		closeView.setColorFilter(ResourcesCompat.getColor(context.resources, R.color.text, null))
		closeView.setImageResource(R.drawable.ic_layer_close)

		closeView.setOnClickListener {
			dismiss()
		}

		val closeViewPadding = AndroidUtilities.dp(8f)
		closeView.setPadding(closeViewPadding, closeViewPadding, closeViewPadding, closeViewPadding)

		frameLayout.addView(closeView, LayoutHelper.createFrame(36, 36f, Gravity.TOP or Gravity.END, 6f, 8f, 6f, 0f))

		var title: String? = null
		var about: String? = null
		val avatarDrawable: AvatarDrawable
		var participantsCount = 0

		val avatarImageView = BackupImageView(context)
		avatarImageView.setRoundRadius(AndroidUtilities.dp(35f))

		linearLayout.addView(avatarImageView, LayoutHelper.createLinear(70, 70, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 29, 0, 0))

		if (chatInvite != null) {
			if (chatInvite.chat != null) {
				avatarDrawable = AvatarDrawable(chatInvite.chat)
				title = chatInvite.chat?.title
				participantsCount = chatInvite.chat?.participantsCount ?: 0
				avatarImageView.setForUserOrChat(chatInvite.chat, avatarDrawable, chatInvite)
			}
			else {
				avatarDrawable = AvatarDrawable()
				avatarDrawable.setInfo(chatInvite.title, null)

				title = chatInvite.title
				participantsCount = chatInvite.participantsCount

				val size = FileLoader.getClosestPhotoSizeWithSize(chatInvite.photo?.sizes, 50)

				avatarImageView.setImage(ImageLocation.getForPhoto(size, chatInvite.photo), "50_50", avatarDrawable, chatInvite)
			}

			about = chatInvite.about
		}
		else if (currentChat != null) {
			avatarDrawable = AvatarDrawable(currentChat)

			title = currentChat.title

			val chatFull = MessagesController.getInstance(currentAccount).getChatFull(currentChat.id)

			about = chatFull?.about

			participantsCount = max(currentChat.participantsCount, chatFull?.participantsCount ?: 0)

			avatarImageView.setForUserOrChat(currentChat, avatarDrawable, currentChat)
		}

		var textView = TextView(context)
		textView.typeface = Theme.TYPEFACE_BOLD
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
		textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		textView.text = title
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END

		linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 10, 9, 10, if (participantsCount > 0) 0 else 20))

		val isChannel = chatInvite != null && (chatInvite.channel && !chatInvite.megagroup || isChannelAndNotMegaGroup(chatInvite.chat)) || isChannel(currentChat) && !currentChat.megagroup
		val hasAbout = !about.isNullOrEmpty()

		if (participantsCount > 0) {
			textView = TextView(context)
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
			textView.isSingleLine = true
			textView.ellipsize = TextUtils.TruncateAt.END

			if (isChannel) {
				textView.text = LocaleController.formatPluralString("Subscribers", participantsCount)
			}
			else {
				textView.text = LocaleController.formatPluralString("Members", participantsCount)
			}

			linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 10, 3, 10, if (hasAbout) 0 else 20))
		}

		if (hasAbout) {
			val aboutTextView = TextView(context)
			aboutTextView.gravity = Gravity.CENTER
			aboutTextView.text = about
			aboutTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			aboutTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			linearLayout.addView(aboutTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 10, 24, 20))
		}

		if (chatInvite == null || chatInvite.requestNeeded) {
			val requestFrameLayout = FrameLayout(context)
			linearLayout.addView(requestFrameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			requestProgressView = RadialProgressView(context)
			requestProgressView?.setProgressColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))
			requestProgressView?.setSize(AndroidUtilities.dp(32f))
			requestProgressView?.visibility = View.INVISIBLE

			requestFrameLayout.addView(requestProgressView, LayoutHelper.createFrame(48, 48, Gravity.CENTER))

			requestTextView = TextView(context)
			requestTextView?.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6f), ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.darker_brand, null))
			requestTextView?.ellipsize = TextUtils.TruncateAt.END
			requestTextView?.gravity = Gravity.CENTER
			requestTextView?.isSingleLine = true
			requestTextView?.text = if (isChannel) context.getString(R.string.RequestToJoinChannel) else context.getString(R.string.RequestToJoinGroup)
			requestTextView?.setTextColor(context.getColor(R.color.white))
			requestTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			requestTextView?.typeface = Theme.TYPEFACE_BOLD

			requestTextView?.setOnClickListener {
				AndroidUtilities.runOnUIThread({
					if (!isDismissed) {
						requestTextView?.invisible()
						requestProgressView?.visible()
					}
				}, 400)

				if (chatInvite == null && currentChat != null) {
					MessagesController.getInstance(currentAccount).addUserToChat(currentChat.id, getInstance(currentAccount).getCurrentUser(), 0, null, null, true, { dismiss() }) { err ->
						if (err != null && "INVITE_REQUEST_SENT" == err.text) {
							setOnDismissListener {
								showBulletin(context, fragment, isChannel)
							}
						}

						dismiss()

						false
					}
				}
				else {
					val request = TLRPC.TLMessagesImportChatInvite()
					request.hash = hash

					ConnectionsManager.getInstance(currentAccount).sendRequest(request, { _, error ->
						AndroidUtilities.runOnUIThread {
							if (fragment == null || fragment.parentActivity == null) {
								return@runOnUIThread
							}

							if (error != null) {
								if ("INVITE_REQUEST_SENT" == error.text) {
									setOnDismissListener {
										showBulletin(context, fragment, isChannel)
									}
								}
								else {
									AlertsCreator.processError(currentAccount, error, fragment, request)
								}
							}

							dismiss()
						}
					}, ConnectionsManager.RequestFlagFailOnServerErrors)
				}
			}

			requestFrameLayout.addView(requestTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 0, 16, 0))

			val descriptionTextView = TextView(context)
			descriptionTextView.gravity = Gravity.CENTER
			descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			descriptionTextView.text = if (isChannel) context.getString(R.string.RequestToJoinChannelDescription) else context.getString(R.string.RequestToJoinGroupDescription)
			descriptionTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
			linearLayout.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 17, 24, 15))
		}
		else {
			if (chatInvite.participants.isNotEmpty()) {
				val listView = RecyclerListView(context)
				listView.setPadding(0, 0, 0, AndroidUtilities.dp(8f))
				listView.isNestedScrollingEnabled = false
				listView.clipToPadding = false
				listView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
				listView.isHorizontalScrollBarEnabled = false
				listView.isVerticalScrollBarEnabled = false
				listView.adapter = UsersAdapter(context)
				listView.setGlowColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))

				linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 90, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 0, 0, 7))
			}

			val shadow = View(context)
			shadow.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.shadow, null))

			linearLayout.addView(shadow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.getShadowHeight()))

			val pickerBottomLayout = PickerBottomLayout(context)
			linearLayout.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.BOTTOM))

			pickerBottomLayout.cancelButton.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
			pickerBottomLayout.cancelButton.setTextColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))
			pickerBottomLayout.cancelButton.text = context.getString(R.string.Cancel).uppercase()
			pickerBottomLayout.cancelButton.setOnClickListener { dismiss() }
			pickerBottomLayout.doneButton.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
			pickerBottomLayout.doneButton.visibility = View.VISIBLE
			pickerBottomLayout.doneButtonBadgeTextView.visibility = View.GONE
			pickerBottomLayout.doneButtonTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))

			if (chatInvite.channel && !chatInvite.megagroup || isChannel(chatInvite.chat) && chatInvite.chat?.megagroup != true) {
				pickerBottomLayout.doneButtonTextView.text = context.getString(R.string.ProfileJoinChannel).uppercase()
			}
			else {
				pickerBottomLayout.doneButtonTextView.text = context.getString(R.string.JoinGroup)
			}

			pickerBottomLayout.doneButton.setOnClickListener {
				dismiss()

				val req = TLRPC.TLMessagesImportChatInvite()
				req.hash = hash

				ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
					if (error == null) {
						val updates = response as Updates?
						MessagesController.getInstance(currentAccount).processUpdates(updates, false)
					}
					AndroidUtilities.runOnUIThread {
						if (fragment == null || fragment.parentActivity == null) {
							return@runOnUIThread
						}

						if (error == null) {
							val updates = response as? Updates ?: return@runOnUIThread

							if (updates.chats.isNotEmpty()) {
								val chat = updates.chats[0]
								chat.left = false
								// chat.kicked = false

								MessagesController.getInstance(currentAccount).putUsers(updates.users, false)
								MessagesController.getInstance(currentAccount).putChats(updates.chats, false)

								val args = Bundle()
								args.putLong("chat_id", chat.id)

								if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, fragment)) {
									val chatActivity = ChatActivity(args)
									fragment.presentFragment(chatActivity, fragment is ChatActivity)
								}
							}
						}
						else {
							AlertsCreator.processError(currentAccount, error, fragment, req)
						}
					}
				}, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
		}
	}

	private inner class UsersAdapter(private val context: Context) : SelectionAdapter() {
		override fun getItemCount(): Int {
			var count = chatInvite?.participants?.size ?: 0
			val participantsCount = chatInvite?.chat?.participantsCount ?: chatInvite?.participantsCount ?: 0

			if (count != participantsCount) {
				count++
			}

			return count
		}

		override fun getItemId(i: Int): Long {
			return i.toLong()
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return false
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = JoinSheetUserCell(context)
			view.layoutParams = RecyclerView.LayoutParams(AndroidUtilities.dp(100f), AndroidUtilities.dp(90f))
			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			val chatInvite = chatInvite ?: return
			val cell = holder.itemView as JoinSheetUserCell

			if (position < chatInvite.participants.size) {
				cell.setUser(chatInvite.participants[position])
			}
			else {
				val participantsCount = chatInvite.chat?.participantsCount ?: chatInvite.participantsCount
				cell.setCount(participantsCount - chatInvite.participants.size)
			}
		}

		override fun getItemViewType(i: Int): Int {
			return 0
		}
	}

	companion object {
		fun showBulletin(context: Context, fragment: BaseFragment?, isChannel: Boolean) {
			if (fragment == null) {
				return
			}

			val layout = TwoLineLottieLayout(context)
			layout.imageView.setAnimation(R.raw.timer_3, 28, 28)
			layout.titleTextView.text = context.getString(R.string.RequestToJoinSent)
			val subTitle = if (isChannel) context.getString(R.string.RequestToJoinChannelSentDescription) else context.getString(R.string.RequestToJoinGroupSentDescription)
			layout.subtitleTextView.text = subTitle
			Bulletin.make(fragment, layout, Bulletin.DURATION_LONG).show()
		}
	}
}
