/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.profile.wallet

import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.databinding.WalletTransactionViewBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.TransactionHistoryEntry.PeerType
import org.telegram.tgnet.TLRPC
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper
import kotlin.math.abs

class TransactionViewHolder(private val binding: WalletTransactionViewBinding) : RecyclerView.ViewHolder(binding.root), NotificationCenter.NotificationCenterDelegate {
	private val avatarDrawable = AvatarDrawable().apply { shouldDrawPlaceholder = true }
	private val avatarImageView = AvatarImageView(binding.root.context)
	private val classGuid = ConnectionsManager.generateClassGuid()
	private val messagesController = MessagesController.getInstance(UserConfig.selectedAccount)

	private val amount: Float
		get() = transaction?.totalAmount ?: 0f // transaction?.payment?.amount ?: transaction?.transaction?.amount ?: 0f

	var transaction: ElloRpc.TransactionHistoryEntry? = null
		set(value) {
			field = value

			val t = value?.transaction

			binding.peerAvatarContainer.visible()

			if (t != null) {
				var shouldFillPeer = true
				val peerType = PeerType.fromValue(t.peerType)

				println(t.peerType)

				when (t.paymentMethod) {
					"apple" -> {
						binding.purchaseNameLabel.text = binding.root.context.getString(R.string.apple_pay)
						avatarImageView.setImageResource(R.drawable.wallet_transaction_icon_bank_card)
						shouldFillPeer = false
					}

					"google" -> {
						binding.purchaseNameLabel.text = binding.root.context.getString(R.string.google)
						avatarImageView.setImageResource(R.drawable.wallet_transaction_icon_bank_card)
						shouldFillPeer = false
					}

					"bank" -> {
						binding.purchaseNameLabel.text = binding.root.context.getString(R.string.bank_transfer)
						avatarImageView.setImageResource(R.drawable.wallet_transaction_icon_bank_card)
						shouldFillPeer = false
					}

					"stripe" -> {
						binding.purchaseNameLabel.text = binding.root.context.getString(R.string.bank_card)
						avatarImageView.setImageResource(R.drawable.wallet_transaction_icon_bank_card)
						shouldFillPeer = false
					}

					"paypal" -> {
						binding.purchaseNameLabel.text = binding.root.context.getString(R.string.paypal)
						avatarImageView.setImageResource(R.drawable.wallet_transaction_icon_paypal)
						shouldFillPeer = false
					}

					"ello_card", "ello_earn_card" -> {
						if (amount > 0) {
							binding.purchaseNameLabel.text = binding.root.context.getString(R.string.transfer)
						}
						else {
							binding.purchaseNameLabel.text = binding.root.context.getString(R.string.main_wallet)
						}
						avatarImageView.setImageResource(R.drawable.ello_card_transaction_image)

						if (peerType?.isInternal == true) {
							shouldFillPeer = false
						}
					}
				}

				when (peerType) {
					PeerType.CHANNEL_SUBSCRIPTION -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.subscription_fee)

						if (amount > 0) {
							binding.transactionTypeImage.setImageResource(R.drawable.paid_fee_incoming)
						}
						else {
							binding.transactionTypeImage.setImageResource(R.drawable.paid_fee_icon)
						}
					}

					PeerType.COURSE_CHANNEL -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.course_fee)

						if (amount > 0) {
							binding.transactionTypeImage.setImageResource(R.drawable.course_fee_incoming)
						}
						else {
							binding.transactionTypeImage.setImageResource(R.drawable.course_fee_icon)
						}
					}

					PeerType.TOPUP_OR_WITHDRAW -> {
						when (t.type) {
							"deposit" -> {
								binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.deposit)
								binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transaction_topup)
							}

							"transfer" -> {
								binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.transfer)

								if (t.amount >= 0) {
									binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transaction_topup)
								}
								else {
									binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transition_withdraw)
								}
							}

							"withdraw" -> {
								binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.withdrawal)
								binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transition_withdraw)
							}
						}
					}

					PeerType.SINGLE_MEDIA_PURCHASE -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.media_sale)
						binding.transactionTypeImage.setImageResource(R.drawable.media_sale_icon)
					}

					PeerType.AI_BOT_SUBSCRIPTION -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.ai_bot_subscription)

						if (amount > 0) {
							binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
						}
						else {
							binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
						}
					}

					PeerType.AI_BOT_PURCHASE -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.ai_bot_purchase)

						if (amount > 0) {
							binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
						}
						else {
							binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
						}
					}

					PeerType.TRANSFER_BETWEEN_WALLETS -> {
						binding.peerAvatarContainer.gone()

						if (t.amount >= 0) {
							binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transaction_topup)
							binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.deposit)
						}
						else {
							binding.transactionTypeImage.setImageResource(R.drawable.wallet_ello_coin_transition_withdraw)
							binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.withdrawal)
						}
					}

					PeerType.AI_TEXT_PACK -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.individual_prompts)
						binding.purchaseNameLabel.text = binding.root.context?.getString(R.string.ai_buy_plans, binding.root.context?.getString(R.string.ai_buy_text))
						avatarImageView.setImageResource(R.drawable.icon_ai_chat)

						if (amount > 0) {
							binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
						}
						else {
							binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
						}
					}

					PeerType.AI_IMAGE_PACK -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.individual_prompts)
						binding.purchaseNameLabel.text = binding.root.context?.getString(R.string.ai_buy_plans, binding.root.context?.getString(R.string.ai_buy_image))
						avatarImageView.setImageResource(R.drawable.ic_ai_image)

						if (amount > 0) {
							binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
						}
						else {
							binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
						}
					}

					PeerType.AI_IMAGE_TEXT_PACK -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.individual_prompts)
						binding.purchaseNameLabel.text = binding.root.context.getString(R.string.ai_buy_text_pictures)

						avatarImageView.setImageResource(R.drawable.icon_ai_double)

						if (amount > 0) {
							binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
						}
						else {
							binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
						}
					}

					PeerType.AI_TEXT_SUBSCRIPTION -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.unlimited_monthly)
						binding.purchaseNameLabel.text = binding.root.context?.getString(R.string.ai_buy_plans, binding.root.context?.getString(R.string.ai_buy_text))
						avatarImageView.setImageResource(R.drawable.icon_ai_chat)

						if (amount > 0) {
							binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
						}
						else {
							binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
						}
					}

					PeerType.AI_IMAGE_SUBSCRIPTION -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.unlimited_monthly)
						binding.purchaseNameLabel.text = binding.root.context?.getString(R.string.ai_buy_plans, binding.root.context?.getString(R.string.ai_buy_image))
						avatarImageView.setImageResource(R.drawable.ic_ai_image)

						if (amount > 0) {
							binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
						}
						else {
							binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
						}
					}

					PeerType.AI_IMAGE_TEXT_SUBSCRIPTION -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.unlimited_monthly)
						binding.purchaseNameLabel.text = binding.root.context.getString(R.string.ai_buy_chat_picture)
						avatarImageView.setImageResource(R.drawable.icon_ai_double)

						if (amount > 0) {
							binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_incoming)
						}
						else {
							binding.transactionTypeImage.setImageResource(R.drawable.ic_ai_bot_outgoing)
						}
					}

					PeerType.REFERRAL_COMMISSION, PeerType.REFERRAL_BONUS -> {
						if (peerType == PeerType.REFERRAL_COMMISSION) {
							binding.purchaseNameLabel.text = binding.root.context.getString(R.string.commission)
						}
						else {
							binding.purchaseNameLabel.text = binding.root.context.getString(R.string.bonus)
						}

						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.referral_program)
						binding.transactionTypeImage.setImageResource(R.drawable.bonus)
						binding.peerAvatarContainer.gone()

						shouldFillPeer = false
					}

					else -> {
						binding.purchaseTypeLabel.text = binding.root.context.getString(R.string.unsupported)
						binding.transactionTypeImage.setImageResource(R.drawable.ai_bot_outgoing_background)
					}
				}

				if (shouldFillPeer) {
					fillPeerInfo(t.peerId)
				}
			}

			setAmount()
		}

	init {
		binding.peerAvatarContainer.addView(avatarImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		val radius = binding.root.context.resources.getDimensionPixelSize(R.dimen.common_size_10dp)

		avatarImageView.imageReceiver.setAllowDecodeSingleFrame(true)
		avatarImageView.setRoundRadius(radius, radius, radius, radius)

		avatarImageView.setImage(null, null, avatarDrawable, null)
	}

	private fun fillPeerInfo(peerId: Long) {
		val chat = messagesController.getChat(peerId)

		fillPeerInfo(chat)

		if (chat == null) {
			NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.chatInfoDidLoad)
			loadPeerInfo(peerId)
		}
	}

	private fun fillPeerInfo(chat: TLRPC.Chat?) {
		avatarDrawable.setInfo(chat)

		val photo = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL)

		if (photo != null) {
			avatarImageView.setImage(photo, null, avatarDrawable, chat)
		}
		else {
			avatarImageView.setImage(null, null, avatarDrawable, chat)
		}

		binding.purchaseNameLabel.text = chat?.title
	}

	private fun loadPeerInfo(peerId: Long) {
		messagesController.loadFullChat(peerId, classGuid, true)
	}

	private fun setAmount() {
		if (amount < 0) {
			binding.amountLabel.setTextColor(ResourcesCompat.getColor(binding.root.resources, R.color.dark, null))
			binding.amountLabel.text = binding.root.context.getString(R.string.withdraw_amount_format, abs(amount))
		}
		else {
			binding.amountLabel.setTextColor(ResourcesCompat.getColor(binding.root.resources, R.color.dark, null))
			binding.amountLabel.text = binding.root.context.getString(R.string.topup_amount_format, abs(amount))
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.chatInfoDidLoad) {
			val chatFull = args[0] as TLRPC.ChatFull
			val peerId = transaction?.transaction?.peerId ?: return

			if (chatFull.id == peerId) {
				NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad)

				val chat = messagesController.getChat(peerId)

				if (chat != null) {
					fillPeerInfo(chat)
				}
			}
		}
	}
}
