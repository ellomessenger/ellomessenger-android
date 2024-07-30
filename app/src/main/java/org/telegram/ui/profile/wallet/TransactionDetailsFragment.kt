/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.profile.wallet

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserObject
import org.telegram.messenger.databinding.TransactionDetailsFragmentBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.toLongDateString
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.TransactionHistoryEntry.PeerType
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.TypefaceSpan
import java.util.Date
import kotlin.math.abs

class TransactionDetailsFragment(args: Bundle) : BaseFragment(args) {
	private var entry: ElloRpc.TransactionHistoryEntry? = null
	private var binding: TransactionDetailsFragmentBinding? = null
	private val paymentSourceAvatarDrawable = AvatarDrawable().apply { shouldDrawPlaceholder = true }
	private var paymentSourceAvatarImageView: AvatarImageView? = null
	private val channelAvatarDrawable = AvatarDrawable().apply { shouldDrawPlaceholder = true }
	private var channelAvatarImageView: AvatarImageView? = null
	private var walletType: String? = null

	private val amount: Float
		get() = entry?.totalAmount ?: 0f // entry?.payment?.amount ?: entry?.transaction?.amount ?: 0f

	override fun onFragmentCreate(): Boolean {
		entry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			arguments?.getSerializable(ARG_TRANSACTION, ElloRpc.TransactionHistoryEntry::class.java)
		}
		else {
			@Suppress("DEPRECATION") arguments?.getSerializable(ARG_TRANSACTION) as ElloRpc.TransactionHistoryEntry
		}

		walletType = arguments?.getString(WALLET_TYPE, null)

		return entry != null
	}

	override fun createView(context: Context): View? {
		val entry = entry ?: return null

		actionBar?.setTitle(context.getString(R.string.transaction_num_format, entry.transaction?.id ?: 0))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}
				}
			}
		})

		binding = TransactionDetailsFragmentBinding.inflate(LayoutInflater.from(context))

		binding?.amountLabel?.typeface = Theme.TYPEFACE_LIGHT

		val radius = context.resources.getDimensionPixelSize(R.dimen.common_size_10dp)

		paymentSourceAvatarImageView = AvatarImageView(binding!!.root.context)
		paymentSourceAvatarImageView?.imageReceiver?.setAllowDecodeSingleFrame(true)
		paymentSourceAvatarImageView?.setRoundRadius(radius, radius, radius, radius)

		binding?.paymentAvatarContainer?.addView(paymentSourceAvatarImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		channelAvatarImageView = AvatarImageView(binding!!.root.context)
		channelAvatarImageView?.imageReceiver?.setAllowDecodeSingleFrame(true)
		channelAvatarImageView?.setRoundRadius(radius, radius, radius, radius)

		binding?.channelAvatarContainer?.addView(channelAvatarImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		fillTransaction()

		fragmentView = binding?.root

		return binding?.root
	}

	private val isReferralTransaction: Boolean
		get() = entry?.transaction?.peerType == PeerType.REFERRAL_COMMISSION.value || entry?.transaction?.peerType == PeerType.REFERRAL_BONUS.value

	private fun fillTransaction() {
		val binding = binding ?: return
		val t = entry?.transaction
		val p = entry?.payment

		if (t != null) {
			var shouldFillPeer = true
			val peerType = PeerType.fromValue(t.peerType)

			when (t.paymentMethod) {
				"apple" -> {
					binding.paymentSourceLabel.typeface = Theme.TYPEFACE_BOLD
					binding.paymentSourceLabel.text = binding.root.context.getString(R.string.apple_pay)
					paymentSourceAvatarImageView?.setImageResource(R.drawable.wallet_transaction_icon_bank_card)
					shouldFillPeer = false
				}

				"bank" -> {
					binding.paymentSourceLabel.typeface = Theme.TYPEFACE_BOLD
					binding.paymentSourceLabel.text = binding.root.context.getString(R.string.bank_transfer)
					paymentSourceAvatarImageView?.setImageResource(R.drawable.wallet_transaction_icon_bank_card)
					shouldFillPeer = false
				}

				"stripe" -> {
					binding.paymentSourceLabel.typeface = Theme.TYPEFACE_BOLD
					binding.paymentSourceLabel.text = binding.root.context.getString(R.string.bank_card)
					paymentSourceAvatarImageView?.setImageResource(R.drawable.wallet_transaction_icon_bank_card)
					shouldFillPeer = false
				}

				"paypal" -> {
					binding.paymentSourceLabel.text = binding.root.context.getString(R.string.paypal)
					binding.paymentSourceLabel.typeface = Theme.TYPEFACE_BOLD
					paymentSourceAvatarImageView?.setImageResource(R.drawable.wallet_transaction_icon_paypal)
					shouldFillPeer = false
				}

				"ello_card", "ello_earn_card" -> {
					if (amount > 0) {
						binding.paymentSourceLabel.text = binding.root.context.getString(R.string.transfer_to_business_wallet)
					}
					else {
						binding.paymentSourceLabel.text = binding.root.context.getString(R.string.transfer_to_main_wallet)
					}
					binding.paymentSourceLabel.typeface = Theme.TYPEFACE_BOLD
					paymentSourceAvatarImageView?.setImageResource(R.drawable.ello_card_transaction_image)

					if (peerType?.isInternal == true) {
						shouldFillPeer = false
					}
				}
			}

			when (peerType) {
				PeerType.CHANNEL_SUBSCRIPTION -> {
					binding.transactionTypeLabel.text = binding.root.context.getString(R.string.subscription_fee)

					if (amount > 0) {
						binding.transactionTypeLabel.setBackgroundResource(R.drawable.topup_label_background)
						binding.transactionTypeImage.setImageResource(R.drawable.paid_fee_incoming)
					}
					else {
						binding.transactionTypeLabel.setBackgroundResource(R.drawable.transfer_out_label_background)
						binding.transactionTypeImage.setImageResource(R.drawable.paid_fee_icon)
					}
				}

				PeerType.COURSE_CHANNEL -> {
					binding.transactionTypeLabel.text = binding.root.context.getString(R.string.course_fee)

					if (amount > 0) {
						binding.transactionTypeLabel.setBackgroundResource(R.drawable.topup_label_background)
						binding.transactionTypeImage.setImageResource(R.drawable.course_fee_incoming)
					}
					else {
						binding.transactionTypeLabel.setBackgroundResource(R.drawable.transfer_out_label_background)
						binding.transactionTypeImage.setImageResource(R.drawable.course_fee_icon)
					}
				}

				PeerType.TOPUP_OR_WITHDRAW -> {
					when (t.type) {
						"deposit" -> {
							binding.transactionTypeLabel.text = binding.root.context.getString(R.string.deposit)
							binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transaction_topup)
							binding.transactionTypeLabel.setBackgroundResource(R.drawable.topup_label_background)
						}

						"transfer" -> {
							binding.transactionTypeLabel.text = binding.root.context.getString(R.string.transfer)

							if (t.amount >= 0) {
								binding.transactionTypeLabel.setBackgroundResource(R.drawable.topup_label_background)
								binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transaction_topup)
							}
							else {
								binding.transactionTypeLabel.setBackgroundResource(R.drawable.transfer_out_label_background)
								binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transition_withdraw)
							}
						}

						"withdraw" -> {
							binding.transactionTypeLabel.setBackgroundResource(R.drawable.transfer_out_label_background)
							binding.transactionTypeLabel.text = binding.root.context.getString(R.string.withdrawal)
							binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transition_withdraw)
						}
					}
				}

				PeerType.SINGLE_MEDIA_PURCHASE -> {
					binding.transactionTypeLabel.text = binding.root.context.getString(R.string.media_sale)
					binding.transactionTypeImage.setImageResource(R.drawable.media_sale_icon)

					if (t.amount >= 0) {
						binding.transactionTypeLabel.setBackgroundResource(R.drawable.topup_label_background)
					}
					else {
						binding.transactionTypeLabel.setBackgroundResource(R.drawable.transfer_out_label_background)
					}
				}

				PeerType.AI_BOT_SUBSCRIPTION -> {
					binding.transactionTypeLabel.text = binding.root.context.getString(R.string.ai_bot_subscription)

					if (amount > 0) {
						binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
					}
					else {
						binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
					}
				}

				PeerType.AI_BOT_PURCHASE -> {
					binding.transactionTypeLabel.text = binding.root.context.getString(R.string.ai_bot_purchase)

					if (amount > 0) {
						binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
					}
					else {
						binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
					}
				}

				PeerType.TRANSFER_BETWEEN_WALLETS -> {
					binding.paymentAvatarContainer.gone()

					if (t.amount >= 0) {
						binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transaction_topup)
						binding.transactionTypeLabel.setBackgroundResource(R.drawable.topup_label_background)
						binding.transactionTypeLabel.text = binding.root.context.getString(R.string.deposit)
					}
					else {
						binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transition_withdraw)
						binding.transactionTypeLabel.setBackgroundResource(R.drawable.transfer_out_label_background)
						binding.transactionTypeLabel.text = binding.root.context.getString(R.string.withdrawal)
					}
				}

				PeerType.AI_TEXT_PACK -> {
					binding.transactionTypeLabel.gone()
					binding.aiBotTransactionTypeLabel.visible()

					binding.paymentSourceLabel.text = binding.root.context.getString(R.string.ai_text)
					paymentSourceAvatarImageView?.setImageResource(R.drawable.ic_ai_text)

					if (amount > 0) {
						binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
					}
					else {
						binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
					}
				}

				PeerType.AI_IMAGE_PACK -> {
					binding.transactionTypeLabel.gone()
					binding.aiBotTransactionTypeLabel.visible()

					binding.paymentSourceLabel.text = binding.root.context.getString(R.string.ai_image)
					paymentSourceAvatarImageView?.setImageResource(R.drawable.ic_ai_image)

					if (amount > 0) {
						binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
					}
					else {
						binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
					}
				}

				PeerType.AI_IMAGE_TEXT_PACK -> {
					binding.transactionTypeLabel.gone()
					binding.aiBotTransactionTypeLabel.visible()

					binding.paymentSourceLabel.text = binding.root.context.getString(R.string.ai_buy_text_pictures)
					paymentSourceAvatarImageView?.setImageResource(R.drawable.ic_ai_image_text)

					if (amount > 0) {
						binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
					}
					else {
						binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
					}
				}

				PeerType.AI_TEXT_SUBSCRIPTION -> {
					binding.transactionTypeLabel.text = binding.root.context.getString(R.string.ai_text_subscription)

					if (amount > 0) {
						binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
					}
					else {
						binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
					}
				}

				PeerType.AI_IMAGE_SUBSCRIPTION -> {
					binding.transactionTypeLabel.text = binding.root.context.getString(R.string.ai_image_subscription)

					if (amount > 0) {
						binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
					}
					else {
						binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
					}
				}

				PeerType.REFERRAL_COMMISSION, PeerType.REFERRAL_BONUS -> {
					if (peerType == PeerType.REFERRAL_COMMISSION) {
						binding.paymentSourceLabel.text = binding.root.context.getString(R.string.commission)
					}
					else {
						binding.paymentSourceLabel.text = binding.root.context.getString(R.string.bonus)
					}

					binding.paymentSourceLabel.typeface = Theme.TYPEFACE_BOLD
					binding.transactionTypeLabel.text = binding.root.context.getString(R.string.referral_program)
					binding.transactionTypeImage.setImageResource(R.drawable.bonus)
					binding.paymentAvatarContainer.gone()

					(paymentSourceAvatarImageView?.parent as? ViewGroup)?.removeView(paymentSourceAvatarImageView)

					binding.referralAvatarContainer.addView(paymentSourceAvatarImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

					binding.referralContainer.visible()
				}

				else -> {
					binding.transactionTypeLabel.text = binding.root.context.getString(R.string.unsupported)
					binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_outgoing_background)
				}
			}

			if (shouldFillPeer) {
				if (isReferralTransaction) {
					if (t.referral?.id == 0L || t.referral?.id == null) {
						binding.referralContainer.gone()
					}
					else {
						fillSourcePeerInfo(t.referral.id)
					}
				}
				else {
					fillSourcePeerInfo(t.payerId)
				}

				fillTargetPeerInfo(t.peerId)
			}
		}

		val balance = t?.operationBalance

		if (balance != null) {
			binding.balanceLabel.text = binding.root.context.getString(R.string.simple_money_format, balance).replace("+", "")
			binding.balanceContainer.visible()
		}
		else {
			binding.balanceContainer.gone()
		}

		binding.dateLabel.text = t?.createdAt?.let { Date(it * 1000L) }?.toLongDateString()

		when (t?.status?.lowercase()) {
			"completed" -> {
				binding.statusLabel.text = binding.root.context.getString(R.string.approved)
				binding.statusLabel.setCompoundDrawablesWithIntrinsicBounds(R.drawable.approved_icon, 0, 0, 0)
				binding.statusLabel.setTextColor(ResourcesCompat.getColor(binding.root.context.resources, R.color.online, null))
			}

			"processing", "admin_check" -> {
				binding.statusLabel.text = binding.root.context.getString(R.string.pending)
				binding.statusLabel.setCompoundDrawablesWithIntrinsicBounds(R.drawable.pending_icon, 0, 0, 0)
				binding.statusLabel.setTextColor(ResourcesCompat.getColor(binding.root.context.resources, R.color.yellow, null))
			}

			"canceled", "error" -> {
				binding.statusLabel.text = binding.root.context.getString(R.string.rejected)
				binding.statusLabel.setCompoundDrawablesWithIntrinsicBounds(R.drawable.rejected_icon, 0, 0, 0)
				binding.statusLabel.setTextColor(ResourcesCompat.getColor(binding.root.context.resources, R.color.purple, null))
				binding.errorLayout.visible()
			}
		}

		val paymentMethod = p?.paymentMethod ?: t?.paymentMethod

		if (paymentMethod.isNullOrEmpty()) {
			binding.paymentMethodContainer.gone()
		}
		else {
			when (paymentMethod) {
				"apple" -> {
					binding.paymentMethodLabel.text = walletType
				}

				"paypal" -> {
					binding.paymentMethodLabel.text = walletType
				}

				"bank" -> {
					binding.paymentMethodLabel.text = walletType
				}

				"stripe" -> {
					binding.paymentMethodLabel.text = walletType
				}

				"ello_card" -> {
					binding.paymentMethodLabel.text = walletType
				}

				"ello_earn_card" -> {
					binding.paymentMethodLabel.text = walletType
				}
			}

			binding.paymentMethodContainer.visible()
		}

		val fee = p?.fee ?: t?.fee ?: 0f

		if (fee > 0f) {
			binding.feeAmountLabel.text = binding.root.context.getString(R.string.simple_money_format, fee).replace("+", "")
			binding.feeContainer.visible()
		}
		else {
			binding.feeContainer.gone()
		}

		val serviceFee = p?.paymentServiceFee ?: t?.paymentServiceFee ?: 0f

		if (serviceFee > 0f) {
			val info = when (paymentMethod) {
				"apple" -> {
					binding.root.context.getString(R.string.apple_pay)
				}

				"paypal" -> {
					binding.root.context.getString(R.string.paypal)
				}

				"bank" -> {
					binding.root.context.getString(R.string.stripe)
				}

				"stripe" -> {
					binding.root.context.getString(R.string.stripe)
				}

				"ello_card" -> {
					binding.root.context.getString(R.string.ello)
				}

				"ello_earn_card" -> {
					binding.root.context.getString(R.string.ello)
				}

				else -> {
					binding.root.context.getString(R.string.stripe)
				}
			}

			binding.serviceFeeHeaderLabel.text = binding.root.context.getString(R.string.service_fee, info)
			binding.serviceFeeIcon.contentDescription = info

			if (paymentMethod == "stripe") {
				binding.serviceFeeIcon.setImageResource(R.drawable.stripe_commission)
			}

			if (paymentMethod == "paypal") {
				binding.serviceFeeIcon.setImageResource(R.drawable.paypal_commission)
			}

			binding.serviceFeeAmountLabel.text = binding.root.context.getString(R.string.simple_money_format, serviceFee).replace("+", "")
			binding.serviceFeeContainer.visible()
		}
		else {
			binding.serviceFeeContainer.gone()
		}

		// TODO: process purchases

		fillAmount()
	}

	private fun fillTargetPeerInfo(peerId: Long) {
		val chat = messagesController.getChat(peerId)

		fillTargetPeerInfo(chat)

		if (chat == null) {
			loadChannelPeerInfo(peerId)
		}
	}

	private fun fillSourcePeerInfo(userId: Long) {
		if (userId == 0L) {
			return
		}

		val user = messagesController.getUser(userId)

		fillSourcePeerInfo(user)

		if (user == null) {
			loadUserPeerInfo(userId)
		}
	}

	private fun fillTargetPeerInfo(chat: TLRPC.Chat?) {
		if (chat != null) {
			if (amount < 0f) {
				// this is outgoing payment
				fillSourcePeerInfo(chat)
			}
			else {
				channelAvatarDrawable.setInfo(chat)

				val photo = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL)

				if (photo != null) {
					channelAvatarImageView?.setImage(photo, null, channelAvatarDrawable, chat)
				}
				else {
					channelAvatarImageView?.setImage(null, null, channelAvatarDrawable, chat)
				}

				binding?.channelHeaderLabel?.setText(if (ChatObject.isOnlineCourse(chat)) {
					R.string.online_course
				}
				else {
					R.string.channel
				})

				binding?.channelNameLabel?.text = chat.title
				binding?.channelContainer?.visible()
			}
		}
		else {
			binding?.channelContainer?.gone()
		}
	}

	private fun fillSourcePeerInfo(peer: TLObject?) {
		if (peer != null) {
			paymentSourceAvatarDrawable.setInfo(peer)

			val photo = ImageLocation.getForUserOrChat(peer, ImageLocation.TYPE_SMALL)

			if (photo != null) {
				paymentSourceAvatarImageView?.setImage(photo, null, paymentSourceAvatarDrawable, peer)
			}
			else {
				paymentSourceAvatarImageView?.setImage(null, null, paymentSourceAvatarDrawable, peer)
			}

			when (peer) {
				is TLRPC.Chat -> {
					binding?.paymentSourceLabel?.typeface = Theme.TYPEFACE_BOLD
					binding?.paymentSourceLabel?.text = peer.title
				}

				is User -> {
					if (!isReferralTransaction) {
						binding?.paymentSourceLabel?.typeface = Theme.TYPEFACE_DEFAULT
					}

					val username = UserObject.getUserName(peer)

					if (username.isNotEmpty()) {
						val resourceId = if (amount > 0) R.string.transaction_from_format else R.string.transaction_to_format
						val fromName = binding?.root?.context?.getString(resourceId, username)

						if (!fromName.isNullOrEmpty()) {
							if (isReferralTransaction) {
								binding?.referralNameLabel?.text = username
							}
							else {
								val sp = SpannableString(fromName)
								val start = fromName.indexOf(username)
								val end = start + username.length

								sp.setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

								binding?.paymentSourceLabel?.text = sp
							}
						}
						else {
							if (isReferralTransaction) {
								binding?.referralNameLabel?.text = null
							}
							else {
								binding?.paymentSourceLabel?.text = null
							}
						}
					}
					else {
						if (isReferralTransaction) {
							binding?.referralNameLabel?.text = null
						}
						else {
							binding?.paymentSourceLabel?.text = null
						}
					}
				}
			}
		}
	}

	private fun loadChannelPeerInfo(peerId: Long) {
		messagesController.loadFullChat(peerId, classGuid, true)
	}

	private fun loadUserPeerInfo(peerId: Long) {
		val user = TLRPC.TL_userEmpty().apply { id = peerId }
		messagesController.loadFullUser(user, classGuid, true)
	}

	private fun fillAmount() {
		val binding = binding ?: return

		if (amount < 0) {
			binding.amountLabel.text = binding.root.context.getString(R.string.withdraw_amount_format, abs(amount))
		}
		else {
			binding.amountLabel.text = binding.root.context.getString(R.string.simple_money_format, abs(amount))
		}
	}

	override fun onResume() {
		super.onResume()

		notificationCenter.addObserver(object : NotificationCenterDelegate {
			override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
				val chatFull = args[0] as TLRPC.ChatFull
				val peerId = entry?.transaction?.peerId ?: return

				if (chatFull.id == peerId) {
					val chat = messagesController.getChat(peerId)
					fillTargetPeerInfo(chat)
				}

				notificationCenter.removeObserver(this, id)
			}
		}, NotificationCenter.chatInfoDidLoad)

		notificationCenter.addObserver(object : NotificationCenterDelegate {
			override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
				val userId = args[0] as Long

				val targetUserId = if (isReferralTransaction) {
					entry?.transaction?.referral?.id
				}
				else {
					entry?.transaction?.payerId
				}

				if (userId > 0 && userId == targetUserId) {
					val user = messagesController.getUser(userId)

					if (user != null) {
						fillSourcePeerInfo(user)
					}
				}

				notificationCenter.removeObserver(this, id)
			}
		}, NotificationCenter.userInfoDidLoad)
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}

	companion object {
		const val ARG_TRANSACTION = "transaction_id"
		const val WALLET_TYPE = "walletType"
	}
}
