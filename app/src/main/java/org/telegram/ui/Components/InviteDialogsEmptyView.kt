/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2025.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Components

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import org.telegram.messenger.R
import org.telegram.messenger.databinding.InviteDialogsEmptyViewBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC.TLUserFull

class InviteDialogsEmptyView @JvmOverloads constructor(context: Context, inviteLink: String, userInfo: TLUserFull?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
	private val binding = InviteDialogsEmptyViewBinding.inflate(LayoutInflater.from(context), this, true)
	private var inviteAnimationView: RLottieImageView? = null

	var onQrCodeClick: (() -> Unit)? = null
	var onDiscoverChannelsClick: (() -> Unit)? = null
	var onInviteClick: (() -> Unit)? = null

	init {
		inviteAnimationView = RLottieImageView(context)

		inviteAnimationView?.setAutoRepeat(true)

		if (userInfo?.isPublic == true) {
			binding.privateContainer.gone()
			binding.publicContainer.visible()

			inviteAnimationView?.setAnimation(R.raw.invite_empty_view, 150, 150)
			binding.invite.addView(inviteAnimationView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

			binding.inviteLinkValue.text = inviteLink

			binding.qrCodeButton.setOnClickListener {
				onQrCodeClick?.invoke()
			}

			binding.share.setOnClickListener {
				val intent = Intent(Intent.ACTION_SEND)
				intent.type = "text/plain"
				intent.putExtra(Intent.EXTRA_TEXT, inviteLink)

				context.startActivity(Intent.createChooser(intent, context.getString(R.string.BotShare)), null)
			}

			binding.discoverChannels.setOnClickListener {
				onDiscoverChannelsClick?.invoke()
			}
		}
		else {
			inviteAnimationView?.setAnimation(R.raw.private_channel, 150, 150)
			binding.privateInvite.addView(inviteAnimationView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

			binding.privateInviteButton.setOnClickListener {
				onInviteClick?.invoke()
			}

			binding.privateDiscoverChannels.setOnClickListener {
				onDiscoverChannelsClick?.invoke()
			}
		}

		inviteAnimationView?.playAnimation()
	}
}
