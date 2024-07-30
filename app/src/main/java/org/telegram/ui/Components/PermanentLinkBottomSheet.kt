/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ChatObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.TL_chatInviteExported
import org.telegram.tgnet.TLRPC.TL_messages_exportChatInvite
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ManageLinksActivity
import org.telegram.ui.QrFragment
import java.util.Locale

class PermanentLinkBottomSheet(context: Context, needFocus: Boolean, fragment: BaseFragment, private val info: ChatFull?, private val chatId: Long, isChannel: Boolean) : BottomSheet(context, needFocus) {
	private val linkIcon: RLottieDrawable
	private val manage: TextView
	private val imageView: RLottieImageView
	private val linkActionView: LinkActionView
	private val fragment: BaseFragment? = null
	private var linkGenerating = false
	private var invite: TL_chatInviteExported? = null

	init {
		setAllowNestedScroll(true)
		setApplyBottomPadding(false)

		linkActionView = LinkActionView(context, fragment, this, true)
		linkActionView.permanent = true

		imageView = RLottieImageView(context)

		linkIcon = RLottieDrawable(R.raw.shared_link_enter, "" + R.raw.shared_link_enter, AndroidUtilities.dp(90f), AndroidUtilities.dp(90f), false, null)
		linkIcon.setCustomEndFrame(42)

		imageView.setAnimation(linkIcon)
		linkActionView.setUsers(0, null)
		linkActionView.hideRevokeOption(true)

		linkActionView.delegate = object : LinkActionView.Delegate {
			override fun showQr() {
				val args = Bundle()
				args.putLong(QrFragment.CHAT_ID, chatId)

				invite?.link?.let {
					args.putString(QrFragment.LINK, it)
				}

				val isPrivate = info?.let {
					MessagesController.getInstance(currentAccount).getChat(it.id)?.let { chat ->
						if (chat.megagroup) {
							chat.username.isNullOrEmpty()
						}
						else {
							chat.flags and TLRPC.CHAT_FLAG_IS_PUBLIC == 0
						}
					}
				} ?: false

				args.putBoolean(QrFragment.IS_PUBLIC, !isPrivate)

				fragment.presentFragment(QrFragment(args))
			}

			override fun revokeLink() {
				generateLink(true)
			}
		}

		val titleView = TextView(context)
		titleView.text = context.getString(R.string.InviteLink)
		titleView.textSize = 24f
		titleView.gravity = Gravity.CENTER_HORIZONTAL
		titleView.setTextColor(context.getColor(R.color.text))

		val subtitle = TextView(context)
		subtitle.text = if (isChannel) context.getString(R.string.LinkInfoChannel) else context.getString(R.string.LinkInfo)
		subtitle.textSize = 14f
		subtitle.gravity = Gravity.CENTER_HORIZONTAL
		subtitle.setTextColor(context.getColor(R.color.dark_gray))

		manage = TextView(context)
		manage.text = context.getString(R.string.ManageInviteLinks)
		manage.textSize = 14f
		manage.setTextColor(context.getColor(R.color.brand))
		manage.background = Theme.createRadSelectorDrawable(ColorUtils.setAlphaComponent(context.getColor(R.color.brand), (255 * 0.3f).toInt()), AndroidUtilities.dp(4f), AndroidUtilities.dp(4f))
		manage.setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(4f), AndroidUtilities.dp(12f), AndroidUtilities.dp(4f))

		manage.setOnClickListener {
			if (info != null) {
				val manageFragment = ManageLinksActivity(info.id, 0, 0)
				manageFragment.setInfo(info, info.exported_invite)

				fragment.presentFragment(manageFragment)
			}

			dismiss()
		}

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.VERTICAL
		linearLayout.addView(imageView, LayoutHelper.createLinear(90, 90, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0))
		linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 60, 16, 60, 0))
		linearLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 60, 16, 60, 0))
		linearLayout.addView(linkActionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		linearLayout.addView(manage, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 60, 26, 60, 26))

		val scrollView = NestedScrollView(context)
		scrollView.isVerticalScrollBarEnabled = false
		scrollView.addView(linearLayout)
		setCustomView(scrollView)

		val chat = MessagesController.getInstance(currentAccount).getChat(chatId)

		if (!chat?.username.isNullOrEmpty()) {
			linkActionView.setLink(String.format(Locale.getDefault(), "https://%s/%s", ApplicationLoader.applicationContext.getString(R.string.domain), chat?.username))
			manage.visibility = View.GONE
		}
		else if (info?.exported_invite != null) {
			linkActionView.setLink(info.exported_invite?.link)
		}
		else {
			generateLink(false)
		}

		imageView.background = Theme.createCircleDrawable(AndroidUtilities.dp(90f), context.getColor(R.color.brand))
		manage.background = Theme.createRadSelectorDrawable(ColorUtils.setAlphaComponent(context.getColor(R.color.brand), (255 * 0.3f).toInt()), AndroidUtilities.dp(4f), AndroidUtilities.dp(4f))

		val color = context.getColor(R.color.white)

		linkIcon.setLayerColor("Top.**", color)
		linkIcon.setLayerColor("Bottom.**", color)
		linkIcon.setLayerColor("Center.**", color)

		setBackgroundColor(context.getColor(R.color.background))
	}

	private fun generateLink(showDialog: Boolean) {
		if (linkGenerating) {
			return
		}

		linkGenerating = true

		val req = TL_messages_exportChatInvite()
		req.legacy_revoke_permanent = true
		req.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId)

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
			AndroidUtilities.runOnUIThread {
				if (error == null) {
					invite = response as TL_chatInviteExported?

					val chatInfo = MessagesController.getInstance(currentAccount).getChatFull(chatId)

					if (chatInfo != null) {
						chatInfo.exported_invite = invite
					}

					linkActionView.setLink(invite!!.link)

					if (showDialog && fragment != null) {
						val builder = AlertDialog.Builder(context)
						builder.setMessage(context.getString(R.string.RevokeAlertNewLink))
						builder.setTitle(context.getString(R.string.RevokeLink))
						builder.setNegativeButton(context.getString(R.string.OK), null)

						fragment.showDialog(builder.create())
					}
				}

				linkGenerating = false
			}
		}
	}

	override fun show() {
		super.show()

		AndroidUtilities.runOnUIThread({
			linkIcon.start()
		}, 50)
	}
}
