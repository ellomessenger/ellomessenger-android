/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.channel

import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.widget.addTextChangedListener
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentNewChannelTypeBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.TextDrawable
import org.telegram.ui.Components.TypefaceSpan
import org.telegram.ui.group.GroupCreateActivity

class ChannelTypeFragment(args: Bundle) : BaseFragment(args) {
	private var onlineCourse: Boolean? = false
	private var binding: FragmentNewChannelTypeBinding? = null
	private var imageView: RLottieImageView? = null
	private var selectedType = PUBLIC
	private var checkReqId = 0
	private var checkRunnable: Runnable? = null
	private var lastCheckName: String? = null
	private var lastNameAvailable = false
	private var canCreatePublic = true

	override fun onFragmentCreate(): Boolean {
		val req = TLRPC.TL_channels_checkUsername()
		req.username = "1"
		req.channel = TLRPC.TL_inputChannelEmpty()

		connectionsManager.sendRequest(req) { _, error ->
			AndroidUtilities.runOnUIThread {
				canCreatePublic = error == null || error.text != "CHANNELS_ADMIN_PUBLIC_TOO_MUCH"
			}
		}

		onlineCourse = arguments?.getBoolean("isCourse", false)

		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)
		actionBar?.setTitle(context.getString(if (onlineCourse == true) R.string.online_course else R.string.channel_type))

		val menu = actionBar?.createMenu()

		menu?.addItem(BUTTON_DONE, buildSpannedString {
			val buttonText = context.getString(R.string.Create)
			append(buttonText)
			setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), 0, buttonText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.brand)), 0, buttonText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
		})

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}

					BUTTON_DONE -> {
						nextStep()
					}
				}
			}
		})

		binding = FragmentNewChannelTypeBinding.inflate(LayoutInflater.from(context))

		val titles = listOf(R.string.public_channel, R.string.private_channel, R.string.subscription_channel)
		val types = listOf(PUBLIC, PRIVATE, SUBSCRIPTION)

		listOf(binding?.publicChannelButton, binding?.privateChannelButton, binding?.subscriptionChannelButton).forEachIndexed { index, b ->
			b?.root?.setOnClickListener {
				channelTypeSelected(types[index])
			}

			b?.checkbox?.text = context.getString(titles[index])
		}

		binding?.linkLayoutEditText?.setCompoundDrawablesWithIntrinsicBounds(TextDrawable(context).apply {
			textSize = 16f
			text = "@"
		}, null, null, null)

		binding?.linkLayoutEditText?.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
			source.toString().filter { ('0'..'9').contains(it) || ('a'..'z').contains(it) }
		}, InputFilter.LengthFilter(MAX_LINK_LENGTH))

		binding?.linkLayoutEditText?.addTextChangedListener {
			cancelLinkValidation()

			val link = it?.toString()

			if (link.isNullOrEmpty()) {
				binding?.errorLabel?.gone()
				return@addTextChangedListener
			}

			checkUserName(link)
		}

		imageView = RLottieImageView(context)
		imageView?.setAutoRepeat(true)

		imageView?.setOnClickListener {
			if (imageView?.isPlaying() != true) {
				imageView?.setProgress(0.0f)
				imageView?.playAnimation()
			}
		}

		binding?.channelTypeImage?.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		fragmentView = binding?.root

		if (onlineCourse == true) {
			binding?.privateChannelButton?.root?.gone()
			binding?.publicChannelButton?.root?.gone()
			binding?.subscriptionChannelButton?.checkbox?.text = context.getString(R.string.online_course)
			selectedType = SUBSCRIPTION
			binding?.description?.visible()
		}

		return binding?.root
	}

	private fun checkUserName(name: String?): Boolean {
		binding?.errorLabel?.gone()

		if (checkRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(checkRunnable)

			checkRunnable = null
			lastCheckName = null

			if (checkReqId != 0) {
				connectionsManager.cancelRequest(checkReqId, true)
			}
		}

		lastNameAvailable = false

		if (name != null) {
			if (name.startsWith("_") || name.endsWith("_") || name.contains("__")) {
				binding?.errorLabel?.setText(R.string.LinkInvalid)
				binding?.errorLabel?.visible()
				return false
			}

			for (a in name.indices) {
				val ch = name[a]

				if (a == 0 && ch >= '0' && ch <= '9') {
					binding?.errorLabel?.setText(R.string.LinkInvalidStartNumber)
					binding?.errorLabel?.visible()
					return false
				}

				if (!(ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z' || ch == '_')) {
					binding?.errorLabel?.setText(R.string.LinkInvalid)
					binding?.errorLabel?.visible()
					return false
				}
			}
		}

		if (name == null || name.length < MIN_LINK_LENGTH) {
			binding?.errorLabel?.setText(R.string.LinkInvalidShort)
			binding?.errorLabel?.visible()
			return false
		}

		if (name.length > MAX_LINK_LENGTH) {
			binding?.errorLabel?.setText(R.string.LinkInvalidLong)
			binding?.errorLabel?.visible()
			return false
		}

		binding?.errorLabel?.gone()

		lastCheckName = name

		checkRunnable = Runnable {
			val req = TLRPC.TL_channels_checkUsername()
			req.username = name
			req.channel = messagesController.getInputChannel(0)

			checkReqId = connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					checkReqId = 0

					if (lastCheckName != null && lastCheckName == name) {
						if (error == null && response is TLRPC.TL_boolTrue) {
							binding?.errorLabel?.visible()
							binding?.errorLabel?.text = buildSpannedString {
								inSpans(ForegroundColorSpan(ResourcesCompat.getColor(context?.resources!!, R.color.green, null))) {
									append(context?.getString(R.string.LinkAvailable))
								}
							}
							lastNameAvailable = true
						}
						else {
							if (error != null && error.text == "CHANNELS_ADMIN_PUBLIC_TOO_MUCH") {
								binding?.errorLabel?.setText(R.string.too_many_public_links)
								binding?.errorLabel?.visible()
								canCreatePublic = false
							}
							else {
								binding?.errorLabel?.setText(R.string.LinkInUse)
								binding?.errorLabel?.visible()
							}

							lastNameAvailable = false
						}
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}.also {
			AndroidUtilities.runOnUIThread(it, 300)
		}

		return true
	}

	private fun channelTypeSelected(type: Int) {
		val context = context ?: return

		if ((type == PUBLIC || type == SUBSCRIPTION) && !canCreatePublic) {
			needShowAlert(context.getString(R.string.AppName), context.getString(R.string.too_many_public_links))
			return
		}

		selectedType = type

		var description: String? = null
		var linkHeader: String? = null

		when (selectedType) {
			PUBLIC -> {
				binding?.publicChannelButton?.checkbox?.isChecked = true
				binding?.privateChannelButton?.checkbox?.isChecked = false
				binding?.subscriptionChannelButton?.checkbox?.isChecked = false
				binding?.linkContainer?.visible()
				binding?.linkHeader?.visible()
				binding?.description?.visible()
				binding?.description?.text = context.getString(R.string.public_channel_create_description)

				description = context.getString(R.string.public_channel_description)
				linkHeader = context.getString(R.string.public_link)

				binding?.linkLayoutEditText?.hint = context.getString(R.string.link_hint)

				imageView?.setAnimation(R.raw.public_channel, ANIMATION_ICON_SIDE, ANIMATION_ICON_SIDE)
				imageView?.playAnimation()

				binding?.channelTypeTitle?.setText(R.string.what_is_public_channel)
				binding?.channelTypeDescription?.setText(R.string.what_is_public_channel_description)
			}

			PRIVATE -> {
				binding?.publicChannelButton?.checkbox?.isChecked = false
				binding?.privateChannelButton?.checkbox?.isChecked = true
				binding?.subscriptionChannelButton?.checkbox?.isChecked = false
				binding?.linkContainer?.gone()
				binding?.linkHeader?.gone()
				binding?.description?.gone()

				description = context.getString(R.string.private_channel_description)
//				linkHeader = context.getString(R.string.invitation_link)

				imageView?.setAnimation(R.raw.private_channel, ANIMATION_ICON_SIDE, ANIMATION_ICON_SIDE)
				imageView?.playAnimation()

				binding?.channelTypeTitle?.setText(R.string.what_is_private_channel)
				binding?.channelTypeDescription?.setText(R.string.what_is_private_channel_description)

			}

			SUBSCRIPTION -> {
				binding?.publicChannelButton?.checkbox?.isChecked = false
				binding?.privateChannelButton?.checkbox?.isChecked = false
				binding?.subscriptionChannelButton?.checkbox?.isChecked = true
				binding?.linkContainer?.visible()
				binding?.linkHeader?.visible()

				description = context.getString(R.string.public_channel_description)
				linkHeader = context.getString(R.string.public_link)

				if (true == onlineCourse) {
					imageView?.setAnimation(R.raw.online_course, ANIMATION_ICON_SIDE, ANIMATION_ICON_SIDE)
					imageView?.playAnimation()

					binding?.description?.visible()
					binding?.description?.text = context.getString(R.string.course_create_description)

					binding?.linkLayoutEditText?.hint = context.getString(R.string.course_link_hint)

					binding?.channelTypeTitle?.setText(R.string.what_is_online_course_channel)
					binding?.channelTypeDescription?.setText(R.string.what_is_online_course_description)
				}
				else {
					imageView?.setAnimation(R.raw.subscription_channel, ANIMATION_ICON_SIDE, ANIMATION_ICON_SIDE)
					imageView?.playAnimation()

					binding?.description?.visible()
					binding?.description?.text = context.getString(R.string.paid_channel_create_description)

					binding?.linkLayoutEditText?.hint = context.getString(R.string.link_hint)

					binding?.channelTypeTitle?.setText(R.string.what_is_subscription_channel)
					binding?.channelTypeDescription?.setText(R.string.what_is_subscription_channel_description)
				}
			}
		}

		binding?.descriptionLabel?.text = description
		binding?.linkHeader?.text = linkHeader
	}

	private fun getLink(showEmptyInfo: Boolean = true, makeAbsolute: Boolean = true): String? {
		val context = context ?: return null
		val link = binding?.linkLayoutEditText?.text?.toString()?.trim()?.trim('/')?.takeIf { it.isNotEmpty() }

		if (link.isNullOrEmpty() && showEmptyInfo) {
			binding?.errorLabel?.setText(R.string.LinkInvalid)
			binding?.errorLabel?.visible()
		}

		return link?.let {
			if (makeAbsolute) {
				"${context.getString(R.string.domain)}/$it"
			}
			else {
				it
			}
		}
	}

	override fun onResume() {
		super.onResume()
		channelTypeSelected(selectedType)
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		cancelLinkValidation()
		binding = null
	}

	private fun cancelLinkValidation() {
		if (checkReqId != 0) {
			connectionsManager.cancelRequest(checkReqId, true)
		}
	}

	private fun nextStep() {
		var link: String? = null

		if (selectedType != PRIVATE) {
			if (!lastNameAvailable) {
				if (binding?.linkLayoutEditText?.text?.toString()?.trim().isNullOrEmpty()) {
					binding?.errorLabel?.setText(R.string.LinkInvalid)
					binding?.errorLabel?.visible()
				}

				return
			}

			link = getLink(makeAbsolute = false, showEmptyInfo = true) ?: return
		}

		val args = Bundle()
		args.putBoolean("isPublic", selectedType == PUBLIC || selectedType == SUBSCRIPTION)
		args.putBoolean("isPaid", selectedType == SUBSCRIPTION)
		args.putBoolean("isCourse", onlineCourse == true)

		if (!link.isNullOrEmpty()) {
			args.putString("inviteLink", link)
		}

		args.putInt("chatType", ChatObject.CHAT_TYPE_CHANNEL)

		if (selectedType == SUBSCRIPTION) {
			presentFragment(ChannelPriceFragment(args))
		}
		else {
			presentFragment(GroupCreateActivity(args), false)
		}
	}

	companion object {
		private const val PUBLIC = 0
		private const val PRIVATE = 1
		private const val SUBSCRIPTION = 2
		private const val BUTTON_DONE = 1
		private const val MIN_LINK_LENGTH = 5
		private const val MAX_LINK_LENGTH = 32
		private const val ANIMATION_ICON_SIDE = 200
	}
}
