/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Shamil Afandiyev, Ello 2024-2025.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.tgnet

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import org.telegram.messenger.FileLog
import org.telegram.messenger.utils.formatDate
import org.telegram.messenger.utils.fromJson
import org.telegram.messenger.utils.gson
import org.telegram.tgnet.TLRPC.TLBizDataRaw
import org.telegram.tgnet.TLRPC.TLBizInvokeBizDataRaw
import org.telegram.tgnet.TLRPC.TLUser
import org.telegram.tgnet.TLRPC.User
import org.telegram.ui.LoginActivity
import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ElloRpc {
	private const val authService = 100200
	private const val profileService = 100300
	private const val channelsService = 100400
	private const val feedService = 100100
	private const val walletService = 100500
	private const val paidSubscriptionService = 100600
	private const val aiGenCustomizeService = 100700
	private const val chatsService = 100900
	private const val likesService = 101000
	private const val loyaltyService = 101100
	private const val donate = 101200

	object Gender {
		const val MAN = "man"
		const val WOMAN = "woman"
		const val NON_BINARY = "non-binary"
		const val OTHER = "prefer-not-to-say"
	}

	@Keep
	data class ElloRequest(
			@field:SerializedName("service") val service: Int,
			@field:SerializedName("method") val method: Int,
			@field:SerializedName("data") val data: Map<String, Any>,
	) : Serializable

	@Keep
	data class AuthResponse(
			@field:SerializedName("predicate_name") val predicateName: String,
			@field:SerializedName("user") val authUser: AuthUser?,
	) : Serializable

	@Keep
	data class AuthUser(
			@field:SerializedName("predicate_name") val predicateName: String,
			@field:SerializedName("id") val id: Long,
			@field:SerializedName("self") val isSelf: Boolean,
			@field:SerializedName("contact") val isContact: Boolean,
			@field:SerializedName("mutual_contact") val isMutualContact: Boolean,
			@field:SerializedName("access_hash") val accessHash: AuthHash,
			@field:SerializedName("status") val status: AuthStatus,
	) : Serializable {
		fun toTLRPCUser(): User {
			val user = TLUser()
			user.isSelf = isSelf
			user.id = id
			user.contact = isContact
			user.mutualContact = isMutualContact
			user.accessHash = accessHash.value
			user.status = TLRPC.TLUserStatusRecently()
			return user
		}
	}

	@Keep
	data class MagickLinkResponse(
			@SerializedName("status") val status: Boolean,
			@SerializedName("message") val message: String?
	) : Serializable

	@Keep
	data class AuthHash(@field:SerializedName("value") val value: Long) : Serializable

	@Keep
	data class AuthStatus(
			@field:SerializedName("predicate_name") val predicateName: String,
			@field:SerializedName("was_online") val wasOnline: Long,
			@field:SerializedName("expires") val expires: Long,
	) : Serializable

	@Keep
	data class SignUpResponse(
			@field:SerializedName("email") val email: String?,
			@field:SerializedName("confirmation_expire") val expirationDate: Long,
			@field:SerializedName("hash") val magicLinkHash: String? = null,
	) : Serializable

	@Keep
	data class VerifyResponse(@field:SerializedName("message") val message: String?) : Serializable

	@Keep
	data class RichVerifyResponse(@field:SerializedName("message") val message: String?, @field:SerializedName("status") val status: Boolean) : Serializable

	@Keep
	data class SimpleStatusResponse(@field:SerializedName("status") val status: Boolean) : Serializable

	@Keep
	data class SimpleStringStatusResponse(@field:SerializedName("status") val status: String) : Serializable

	@Keep
	data class ForgotPasswordVerifyResponse(
			@field:SerializedName("status") val status: Boolean,
			@field:SerializedName("message") val message: String?,
			@field:SerializedName("email") val email: String?,
			@field:SerializedName("confirmation_expire") val expirationDate: Long,
	) : Serializable

	@Keep
	data class ChangeEmailResponse(
			@SerializedName("email") val email: String?,
			@SerializedName("confirmation_expire") val confirmationExpire: Long
	) : Serializable

	@Keep
	data class ChannelCategoriesResponse(
			@field:SerializedName("categories") val categories: List<String>,
	) : Serializable

	@Keep
	data class Genre(
			@field:SerializedName("id") val id: Int,
			@field:SerializedName("genre") val name: String,
	) : Serializable

	@Keep
	data class GenresResponse(
			@field:SerializedName("genres") val genres: List<Genre>,
	) : Serializable

	@Keep
	data class FeedSettingsResponse(
			@field:SerializedName("all") val allChannels: List<Long>?,
			@field:SerializedName("hidden") val hiddenChannels: List<Long>?,
			@field:SerializedName("pinned") val pinnedChannels: List<Long>?,
			@field:SerializedName("show_recommended") val showRecommended: Boolean,
			@field:SerializedName("show_only_subs") val showSubscriptionsOnly: Boolean,
			@field:SerializedName("show_adult") val showAdult: Boolean,
	) : Serializable

	@Keep
	data class AvailableAssetsResponse(
			@field:SerializedName("assets") val assets: List<WalletAsset>,
	) : Serializable

	@Keep
	data class WalletAsset(
			@field:SerializedName("id") val id: Int,
			@field:SerializedName("asset_name") val name: String,
			@field:SerializedName("asset_symbol") val symbol: String,
	) : Serializable {
		companion object {
			fun fromMap(map: Map<String, Any>): WalletAsset? {
				return WalletAsset(
						id = (map["id"] as? Number ?: return null).toInt(),
						name = map["asset_name"] as? String ?: return null,
						symbol = map["asset_symbol"] as? String ?: return null,
				)
			}
		}
	}

	@Keep
	data class Wallets(
			@field:SerializedName("wallets") val wallets: List<UserWallet>,
	) : Serializable

	@Keep
	data class UserWallet(@field:SerializedName("id") val id: Long, @field:SerializedName("asset_name") val name: String, @field:SerializedName("asset_symbol") val symbol: String, @field:SerializedName("type") val type: String, @field:SerializedName("amount") val amount: Float, @field:SerializedName("freeze_amount") val freezeAmount: Float, @field:SerializedName("available_amount") val availableAmount: Float) : Serializable {
		override fun equals(other: Any?): Boolean {
			if (other !is UserWallet) {
				return false
			}

			return id == other.id && name == other.name && symbol == other.symbol && type == other.type
		}

		override fun hashCode(): Int {
			var result = id.toInt()
			result = 31 * result + name.hashCode()
			result = 31 * result + symbol.hashCode()
			result = 31 * result + type.hashCode()
			return result
		}

		fun isEmpty(): Boolean {
			return amount == 0f
		}

		companion object {
			fun fromMap(map: Map<String, Any>): UserWallet? {
				return UserWallet(id = (map["id"] as? Number ?: return null).toLong(), name = map["asset_name"] as? String ?: return null, symbol = (map["asset_symbol"] as? String)?.lowercase() ?: return null, type = map["type"] as? String ?: return null, amount = (map["amount"] as? Number ?: return null).toFloat(), availableAmount = (map["freeze_amount"] as? Number ?: return null).toFloat(), freezeAmount = (map["available_amount"] as? Number ?: return null).toFloat())
			}
		}
	}

	@Keep
	data class Earnings(
			@field:SerializedName("available_balance") val availableBalance: Float,
			@field:SerializedName("freeze_balance") val freezeBalance: Float,
			@field:SerializedName("total_balance") val totalBalance: Float,
	) : Serializable {
		fun isEmpty(): Boolean {
			return availableBalance == 0f && freezeBalance == 0f && totalBalance == 0f
		}

		companion object {
			fun fromMap(map: Map<String, Any>): Earnings? {
				return Earnings(
						availableBalance = (map["available_balance"] as? Number ?: return null).toFloat(),
						freezeBalance = (map["freeze_balance"] as? Number ?: return null).toFloat(),
						totalBalance = (map["total_balance"] as? Number ?: return null).toFloat(),
				)
			}
		}
	}

	@Keep
	data class StripePaymentResponse(
			@field:SerializedName("status") val status: String,
			@field:SerializedName("currency") val currency: String,
			@field:SerializedName("amount") val amount: Float,
			@field:SerializedName("link") val link: String,
			@field:SerializedName("payment_id") val paymentId: Long,
	) : Serializable

	@Keep
	data class PayPalPaymentLinkResponse(
			@field:SerializedName("status") val status: String,
			@field:SerializedName("currency") val currency: String,
			@field:SerializedName("amount") val amount: Float,
			@field:SerializedName("link") val link: String,
			@field:SerializedName("payment_id") val paymentId: Long,
	) : Serializable

	@Keep
	data class EarnStatisticsResponse(
			@field:SerializedName("data") val data: List<EarnStatistics>,
	) : Serializable

	@Keep
	data class EarnStatistics(
			@field:SerializedName("date") val date: String,
			@field:SerializedName("amount") val amount: Float,
	) : Serializable {
		fun date(): Date? {
			val items = date.split("-")

			val format = when (items.size) {
				3 -> "yyyy-MM-dd"
				2 -> "yyyy-MM"
				1 -> "yyyy"
				else -> return null
			}

			return try {
				SimpleDateFormat(format, Locale.getDefault()).parse(date)
			}
			catch (e: Exception) {
				null
			}
		}
	}

	@Keep
	data class MyBalanceTransferResponse(
			@field:SerializedName("uuid") val uuid: String,
			@field:SerializedName("status") val status: String,
			@field:SerializedName("amount") val amount: Float,
			@field:SerializedName("fee") val fee: Float,
			@field:SerializedName("currency") val currency: String,
			@field:SerializedName("description") val description: String,
			@field:SerializedName("payment_method") val paymentMethod: String,
	) : Serializable

	@Keep
	data class Transaction(
			@field:SerializedName("id") val id: Long,
			@field:SerializedName("uuid") val uuid: String,
			@field:SerializedName("status") val status: String,
			@field:SerializedName("peer_type") val peerType: Int,
			@field:SerializedName("peer_id") val peerId: Long,
			@field:SerializedName("payer_id") val payerId: Long,
			@field:SerializedName("amount") val amount: Float,
			@field:SerializedName("fee") val fee: Float?,
			@field:SerializedName("currency") val currency: String?,
			@field:SerializedName("description") val description: String?,
			@field:SerializedName("type") val type: String,
			@field:SerializedName("payment_method") val paymentMethod: String,
			@field:SerializedName("payment_system_fee") val paymentServiceFee: Float,
			@field:SerializedName("operation_balance") val operationBalance: Float?,
			@field:SerializedName("created_at") val createdAt: Long,
			@field:SerializedName("created_at_formatted") val createdAtFormatted: String?,
			@field:SerializedName("referral") val referral: TransactionReferral?,
	) : Serializable

	@Keep
	data class TransactionReferral(
			@field:SerializedName("id") val id: Long,
			@field:SerializedName("username") val username: String?,
			@field:SerializedName("first_name") val firstName: String?,
			@field:SerializedName("last_name") val lastName: String?,
			@field:SerializedName("access_hash") val accessHash: String?,
	) : Serializable

	@Keep
	data class Payment(
			@field:SerializedName("id") val id: Long,
			@field:SerializedName("asset_name") val assetName: String,
			@field:SerializedName("asset_symbol") val assetSymbol: String,
			@field:SerializedName("amount") val amount: Float,
			@field:SerializedName("amount_fiat") val amountFiat: Float,
			@field:SerializedName("fee") val fee: Float,
			@field:SerializedName("status") val status: String,
			@field:SerializedName("type") val type: String,
			@field:SerializedName("created_at") val createdAt: Long,
			@field:SerializedName("wallet_id") val walletId: Long,
			@field:SerializedName("currency") val currency: String?,
			@field:SerializedName("payment_method") val paymentMethod: String,
			@field:SerializedName("to") val to: String,
			@field:SerializedName("payment_service_fee") val paymentServiceFee: Float,
	) : Serializable

	@Keep
	data class TransactionHistoryEntry(
			@field:SerializedName("transaction") val transaction: Transaction?,
			@field:SerializedName("service_image") val serviceImage: String?,
			@field:SerializedName("service_name") val serviceName: String?,
			@field:SerializedName("payment") val payment: Payment?,
	) : Serializable {
		enum class PeerType(val value: Int) {
			CHANNEL_SUBSCRIPTION(0), COURSE_CHANNEL(1), TOPUP_OR_WITHDRAW(2), SINGLE_MEDIA_PURCHASE(3), AI_BOT_SUBSCRIPTION(4), AI_BOT_PURCHASE(5), TRANSFER_BETWEEN_WALLETS(6), AI_TEXT_PACK(7), AI_IMAGE_PACK(8), AI_TEXT_SUBSCRIPTION(9), AI_IMAGE_SUBSCRIPTION(10), REFERRAL_COMMISSION(11), REFERRAL_BONUS(12), AI_IMAGE_TEXT_PACK(13), AI_IMAGE_TEXT_SUBSCRIPTION(14);

			companion object {
				fun fromValue(peerType: Int) = PeerType.values().find { it.value == peerType }
			}

			val isInternal: Boolean
				get() = when (this) {
					TRANSFER_BETWEEN_WALLETS, TOPUP_OR_WITHDRAW, AI_BOT_SUBSCRIPTION, AI_BOT_PURCHASE, AI_TEXT_PACK, AI_IMAGE_PACK, AI_TEXT_SUBSCRIPTION, AI_IMAGE_SUBSCRIPTION, AI_IMAGE_TEXT_PACK, AI_IMAGE_TEXT_SUBSCRIPTION -> true
					else -> false
				}
		}

		val totalAmount: Float
			get() {
				return transaction?.amount ?: payment?.amount ?: 0f
//				return transaction?.let {
//					it.amount - (it.fee ?: 0f) - it.paymentServiceFee
//				} ?: payment?.let {
//					it.amount - it.fee - it.paymentServiceFee
//				} ?: 0f
			}
	}

	@Keep
	data class TransactionsHistoryResponse(
			@field:SerializedName("transactions") val transactions: List<TransactionHistoryEntry>?,
	) : Serializable

	@Keep
	data class SubscriptionItem(@field:SerializedName("user_id") val userId: Long, @field:SerializedName("peer_id") val channelId: Long, @field:SerializedName("peer_type") val type: Int, @field:SerializedName("amount") val amount: Float, @field:SerializedName("currency") val currency: String, @field:SerializedName("expire_at") val expireAt: Long, @field:SerializedName("is_active") val isActive: Boolean, @field:SerializedName("cancelled") val isCancelled: Boolean, @field:SerializedName("bot_name") val botName: String, @field:SerializedName("bot_type") val botType: Int) : Serializable

	@Keep
	data class Subscriptions(@field:SerializedName("items") val items: List<SubscriptionItem>) : Serializable

	@Keep
	data class UserDonateResponse(@field:SerializedName("is_active") val isActive: Boolean) : Serializable

	@Keep
	data class WalletPaymentByIdResponse(@field:SerializedName("payment") val payment: WalletPayment?) : Serializable

	@Keep
	data class WalletPayment(
			@field:SerializedName("id") val id: Long,
			@field:SerializedName("status") val status: String,
			@field:SerializedName("amount") val amount: Float,
			@field:SerializedName("payment_method") val paymentMethod: String,
			@field:SerializedName("payment_type") val paymentType: String,
			@field:SerializedName("currency") val currency: String,
			@field:SerializedName("date") val date: String,
			@field:SerializedName("time") val time: String,
			@field:SerializedName("to") val to: String,
	) : Serializable

	@Keep
	data class TransferStatsResponse(
			@field:SerializedName("data") val stats: List<TransferStats>?,
	) : Serializable

	@Keep
	data class TransferStats(
			@field:SerializedName("total") val total: Float,
			@field:SerializedName("period") val period: String,
			@field:SerializedName("data") val data: List<TransferStatsData>?,
	) : Serializable

	@Keep
	data class TransferStatsData(
			@field:SerializedName("date") val date: String,
			@field:SerializedName("type") val type: String,
			@field:SerializedName("amount") val amount: Float,
			@field:SerializedName("period") val period: String,
			@field:SerializedName("date_from") val dateFrom: String?,
			@field:SerializedName("date_to") val dateTo: String?,
	) : Serializable

	@Keep
	data class WithdrawPayment(
			@field:SerializedName("amount") val amount: Float,
			@field:SerializedName("amount_fiat") val amountFiat: Float,
			@field:SerializedName("fee") val fee: Float,
			@field:SerializedName("payment_system_fee") val paymentSystemFee: Float,
			@field:SerializedName("withdraw_max") val withdrawMax: Float,
			@field:SerializedName("withdraw_min") val withdrawMin: Float,
			@field:SerializedName("status") val status: String?,
			@field:SerializedName("paypal_email") val paypalEmail: String?,
			@field:SerializedName("payment_id") val paymentId: String,
	) : Serializable

	@Keep
	data class WithdrawApprovePayment(
			@field:SerializedName("amount") val amount: Float,
			@field:SerializedName("fee") val fee: Float,
			@field:SerializedName("status") val status: String?,
			@field:SerializedName("paypal_email") val paypalEmail: String?,
			@field:SerializedName("payment_id") val paymentId: String?,
	) : Serializable

	@Keep
	data class WithdrawSendApproveCodeResponse(
			@field:SerializedName("status") val status: Boolean,
			@field:SerializedName("message") val message: String?,
			@field:SerializedName("confirmation_expire") val confirmationExpire: Long,
	) : Serializable

	@Keep
	data class BankRequisitePersonInfo(
			@field:SerializedName("first_name") val firstName: String?,
			@field:SerializedName("last_name") val lastName: String?,
			@field:SerializedName("phone_number") val phoneNumber: String?,
			@field:SerializedName("email") val email: String?,
	) : Serializable

	@Keep
	data class BankRequisiteBankInfo(
			@field:SerializedName("country") val country: String?,
			@field:SerializedName("routing_number") val routingNumber: String?,
			@field:SerializedName("name") val name: String?,
			@field:SerializedName("street") val street: String?,
			@field:SerializedName("city") val city: String?,
			@field:SerializedName("state") val state: String?,
			@field:SerializedName("swift") val swift: String?,
			@field:SerializedName("address") val address: String?,
			@field:SerializedName("postal_code") val postalCode: String?,
			@field:SerializedName("zip_code") val zipCode: String?,
			@field:SerializedName("recipient_account_number") val recipientAccountNumber: String?,
	) : Serializable

	@Keep
	data class BankRequisiteAddressInfo(
			@field:SerializedName("address") val address: String?,
			@field:SerializedName("street") val street: String?,
			@field:SerializedName("city") val city: String?,
			@field:SerializedName("state") val state: String?,
			@field:SerializedName("region") val region: String?,
			@field:SerializedName("zip_code") val zipCode: String?,
			@field:SerializedName("postal_code") val postalCode: String?,
	) : Serializable

	@Keep
	data class BankRequisite(
			@field:SerializedName("user_id") val userId: Long,
			@field:SerializedName("requisites_id") val requisitesId: Long,
			@field:SerializedName("requisites_uuid") val requisitesUuid: String,
			@field:SerializedName("status") val status: String,
			@field:SerializedName("person_info") val personInfo: BankRequisitePersonInfo?,
			@field:SerializedName("bank_info") val bankInfo: BankRequisiteBankInfo?,
			@field:SerializedName("address_info") val addressInfo: BankRequisiteAddressInfo?,
			@field:SerializedName("recipient_type") val recipientType: String,
			@field:SerializedName("business_id_number") val businessIdNumber: String?,
			@field:SerializedName("currency") val currency: String?,
	) : Serializable

	@Keep
	data class BankRequisiteResponse(@field:SerializedName("data") val data: List<BankRequisite>?) : Serializable

	@Keep
	data class SubscriptionInfoAiBot(@field:SerializedName("easy_mode") val isEasyMode: Boolean, @field:SerializedName("text_total") val textTotal: Int, @field:SerializedName("text_day_left") val textDayLeft: Int, @field:SerializedName("text_sub_active") val isTextSubscriptionActive: Boolean, @field:SerializedName("text_sub_expired") val textSubExpired: Boolean, @field:SerializedName("text_expire_at") val textExpireAt: Long, @field:SerializedName("text_expiration_date") val textExpirationDate: Long, @field:SerializedName("img_total") val imgTotal: Int, @field:SerializedName("img_day_left") val imgDayLeft: Int, @field:SerializedName("img_sub_active") val isImgSubscriptionActive: Boolean, @field:SerializedName("img_sub_expired") val imgSubExpired: Boolean, @field:SerializedName("img_expire_at") val imgExpireAt: Long, @field:SerializedName("img_expiration_date") val imgExpirationDate: Long, @field:SerializedName("double_day_left") val doubleDayLeft: Int, @field:SerializedName("double_sub_active") val doubleSubActive: Boolean, @field:SerializedName("double_sub_expired") val doubleSubExpired: Boolean, @field:SerializedName("double_expiration_date") val doubleExpirationDate: Long, @field:SerializedName("current_unix") val currentUnix: Long, @field:SerializedName("state") private val state: Int, // 0 - none, 1 - text, 2 - image
									 @field:SerializedName("bot_id") val botId: Int, @field:SerializedName("promo_list_active") val promoListActive: List<Long>, @field:SerializedName("logo") val logo: Int) : Serializable {
		val realState: SubscriptionInfoAiBotState
			get() = SubscriptionInfoAiBotState.entries.find { it.state == state } ?: SubscriptionInfoAiBotState.NONE
	}

	enum class SubscriptionInfoAiBotState(val state: Int) {
		NONE(0), TEXT(1), IMAGE(2)
	}

	@Keep
	data class PriceInfoResponse(@field:SerializedName("text_price_subscription") val textSubscriptionPrice: Float, @field:SerializedName("text_price_per_quantity") val textPackPrice: Float, @field:SerializedName("text_default_quantity") val textDefaultQuantity: Int, @field:SerializedName("img_price_subscription") val imageSubscriptionPrice: Float, @field:SerializedName("img_price_per_quantity") val imagePackPrice: Float, @field:SerializedName("img_default_quantity") val imageDefaultQuantity: Int, @field:SerializedName("double_subscription_price") val doubleSubscriptionPrice: Float, @field:SerializedName("double_pack_price") val doublePackPrice: Float, @field:SerializedName("double_pack_text_quantity") val doublePackTextQuantity: Int, @field:SerializedName("double_pack_image_quantity") val doublePackImageQuantity: Int) : Serializable

	@Keep
	data class SubInfoResponse(@field:SerializedName("text_total") val textTotal: Int, @field:SerializedName("text_day_left") val textDayLeft: Int, @field:SerializedName("text_sub_active") val textSubActive: Boolean, @field:SerializedName("text_sub_expired") val textSubExpired: Boolean, @field:SerializedName("img_total") val imgTotal: Int, @field:SerializedName("img_day_left") val imgDayLeft: Int, @field:SerializedName("img_sub_active") val imgSubActive: Boolean, @field:SerializedName("img_sub_expired") val imgSubExpired: Boolean, @field:SerializedName("double_day_left") val doubleDayLeft: Int, @field:SerializedName("double_sub_active") val doubleSubActive: Boolean, @field:SerializedName("double_sub_expired") val doubleSubExpired: Boolean, @field:SerializedName("state") val state: Int, @field:SerializedName("bot_id") val botId: Long, @field:SerializedName("promo_list_active") val promoListActive: List<Long>) : Serializable

	@Keep
	data class AiSpaceCategoriesResponse(@field:SerializedName("categories") val categories: List<String>) : Serializable

	@Keep
	data class LinkHashResponse(@field:SerializedName("hash") val hash: String) : Serializable

	@Keep
	data class LikesForMessageResponse(@field:SerializedName("user_ids") val userIds: List<Long>) : Serializable

	@Keep
	data class LastMonthActivityGraphicsResponse(@field:SerializedName("amount") val amount: Float?, @SerializedName("data") val data: List<TransferStatsData>)

	@Keep
	data class CalculateTransferFeeResponse(
			@field:SerializedName("amount") val amount: Float,
			@field:SerializedName("amount_fiat") val amountFiat: Float,
			@field:SerializedName("fee") val fee: Float,
			@field:SerializedName("transfer_max") val transferMax: Float,
			@field:SerializedName("transfer_min") val transferMin: Float,
	) : Serializable

	@Keep
	data class LoyaltyCode(
			@field:SerializedName("code") val code: String,
			@field:SerializedName("user_id") val userId: Long,
	) : Serializable

	@Keep
	data class LoyaltyUsersList(
			@field:SerializedName("users") val users: List<LoyaltyUser>,
			@field:SerializedName("total") val total: Long,
	) : Serializable

	@Keep
	data class InnerLoyaltyUser(
			// @field:SerializedName("data2") val userData: LoyaltyUserData,
			@field:SerializedName("id") val id: Long,
			@field:SerializedName("first_name") val firstName: String,
			@field:SerializedName("last_name") val lastName: String,
			@field:SerializedName("username") val username: String,
	) : Serializable {
		fun toTLRPCUser(): User {
			val user = TLUser()
			user.id = id
//			user.access_hash = accessHash
			user.firstName = firstName
			user.lastName = lastName
			user.username = username
			user.status = TLRPC.TLUserStatusRecently()
//			user.is_public = isPublic
			return user
		}
	}

//	@Keep
//	data class LoyaltyUserData(
//			@field:SerializedName("predicate_name") val predicateName: String,
//			@field:SerializedName("user") val loyaltyInnerUserData: LoyaltyInnerUserData?,
//			@field:SerializedName("last_seen_at") val lastSeenAt: Long,
//	) : Serializable
//
//	@Keep
//	data class LoyaltyInnerUserData(
//			@field:SerializedName("predicate_name") val predicateName: String,
//			@field:SerializedName("id") val id: Long,
//			@field:SerializedName("access_hash") val accessHash: Long,
//			@field:SerializedName("user_type") val userType: Int,
//			@field:SerializedName("secret_key_id") val secretKeyId: Long,
//			@field:SerializedName("first_name") val firstName: String,
//			@field:SerializedName("last_name") val lastName: String,
//			@field:SerializedName("username") val username: String,
//			@field:SerializedName("phone") val phone: String,
//			@field:SerializedName("country_code") val country: String,
//			@field:SerializedName("contacts_version") val contactsVersion: Int,
//			@field:SerializedName("privacies_version") val privaciesVersion: Int,
//			@field:SerializedName("public") val isPublic: Boolean,
//	) : Serializable {
//		fun toTLRPCUser(): User {
//			val user = TLUser()
//			user.id = id
//			user.access_hash = accessHash
//			user.first_name = firstName
//			user.last_name = lastName
//			user.username = username
//			user.status = TLRPC.TLUserStatusRecently()
//			user.is_public = isPublic
//			return user
//		}
//	}

	@Keep
	data class LoyaltyUser(
			@field:SerializedName("loyalty") val loyalty: Loyalty,
			@field:SerializedName("user") val user: InnerLoyaltyUser,
			@field:SerializedName("sum") val sum: Float,
			@field:SerializedName("commission") val commission: Float,
	) : Serializable

	@Keep
	data class Loyalty(
			@field:SerializedName("id") val id: Long,
			@field:SerializedName("created_at") val createdAt: Long,
			@field:SerializedName("user_id") val userId: Long,
			@field:SerializedName("loyalty_code_id") val codeId: Long,
	) : Serializable

	@Keep
	data class LoyaltyData(
			@field:SerializedName("id") val id: Long,
			@field:SerializedName("percent") val percent: Float,
			@field:SerializedName("percent_owner") val percentOwner: Float,
			@field:SerializedName("bonus") val bonus: Float,
			@field:SerializedName("is_business") val isBusiness: Boolean,
			@field:SerializedName("is_default") val isDefault: Boolean,
			@field:SerializedName("name") val name: String?,
			@field:SerializedName("desc") val description: String?,
	) : Serializable

	@Keep
	data class LoyaltyDataWithSum(
			@field:SerializedName("loyalty_data") val loyaltyData: LoyaltyData,
			@field:SerializedName("count_users") val usersCount: Long,
			@field:SerializedName("sum") val sum: Float,
	) : Serializable

	@Keep
	data class LeftoverChat(
			@field:SerializedName("id") val id: Long,
			@field:SerializedName("title") val title: String,
			@field:SerializedName("username") val username: String?,
	) : Serializable

	@Keep
	data class LeftoversChats(
			@field:SerializedName("chats") val chats: List<LeftoverChat>?,
	) : Serializable

	@Keep
	data class Leftovers(
			@field:SerializedName("message") val message: String?,
			@field:SerializedName("wallets") val wallets: Wallets?,
			@field:SerializedName("paid_channels_owner") val paidChannelsOwner: LeftoversChats?,
			@field:SerializedName("paid_channels_subscribe") val paidChannelsSubscribe: LeftoversChats?,
			@field:SerializedName("ai_sub_info") val aiSubInfo: SubscriptionInfoAiBot?,
	) : Serializable

	@Keep
	data class AllBotsResponse(@field:SerializedName("bots") var bots: ArrayList<AiSpaceBotsInfo> = arrayListOf()) : Serializable

	@Keep
	data class AdditionalInfoResponse(@SerializedName("text_limit_error") var textLimitError: String?, @SerializedName("image_limit_error") var imageLimitError: String?) : Serializable

	@Keep
	data class AiSpaceBotsInfo(@field:SerializedName("bot_id") var botId: Long?, @field:SerializedName("first_name") var firstName: String?, @field:SerializedName("description") var description: String?, @field:SerializedName("photo_id") var photoId: String?, @field:SerializedName("photo_access_hash") var photoAccessHash: String?) : Serializable

	@Keep
	data class CommissionInfo(
			@field:SerializedName("type") val type: String,
			@field:SerializedName("value") val value: Double,
			@field:SerializedName("on_off_deposit") val onOffDeposit: Boolean?,
			@field:SerializedName("min_deposit") val minDeposit: Double?,
			@field:SerializedName("max_deposit") val maxDeposit: Double?,
			@field:SerializedName("on_off_withdrawals") val onOffWithdrawals: Boolean?,
			@field:SerializedName("min_withdrawals") val minWithdrawals: Double?,
			@field:SerializedName("max_withdrawals") val maxWithdrawals: Double?,
	) : Serializable

	fun getCommissionInfo(): TLBizInvokeBizDataRaw {
		val request = ElloRequest(walletService, 103000, mapOf())
		return createRpcRequest(request)
	}

	fun getLoyaltyBonusDataWithSum(): TLBizInvokeBizDataRaw {
		val request = ElloRequest(loyaltyService, 101100, mapOf())
		return createRpcRequest(request)
	}

	fun getLoyaltyUsersRequest(page: Long, limit: Long): TLBizInvokeBizDataRaw {
		val request = ElloRequest(loyaltyService, 100400, mapOf("pagination" to mapOf("page" to page, "per_page" to limit)))
		return createRpcRequest(request)
	}

	fun getLoyaltyCode(): TLBizInvokeBizDataRaw {
		val request = ElloRequest(loyaltyService, 100300, mapOf())
		return createRpcRequest(request)
	}

	fun authRequest(username: String, password: String, token: String? = null, recaptchaAction: String? = null, platform: Int = 0): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["username"] = username
		data["password"] = password
		token?.let { data["token"] = it }
		recaptchaAction?.let { data["recaptcha_action"] = it }
		data["platform"] = platform //MARK: 0-Android, 1-IOS

		val elloRequest = ElloRequest(authService, 100200, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun authMagicLinkRequest(username: String, token: String? = null, recaptchaAction: String? = null, platform: Int = 0): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["username_or_email"] = username
		token?.let { data["token"] = it }
		recaptchaAction?.let { data["recaptcha_action"] = it }
		data["platform"] = platform //MARK: 0-Android, 1-IOS

		val elloRequest = ElloRequest(authService, 101000, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun sendMagicLinkRequest(usernameOrEmail: String, action: Int): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["username_or_email"] = usernameOrEmail
		data["action"] = action //MARK: 0-MagicLinkSendFor_DELETE_ACCOUNT, 2-MagicLinkSendFor_CHANGE_EMAIL

		val elloRequest = ElloRequest(authService, 101500, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun signupRequest(username: String, password: String, gender: String?, birthday: String?, email: String, phone: String?, country: String?, kind: LoginActivity.AccountType, type: LoginActivity.LoginMode, referralCode: String?, token: String? = null, recaptchaAction: String? = null, platform: Int = 0): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["username"] = username
		data["password"] = password
		data["email"] = email
		data["gender"] = gender ?: ""
		data["date_of_birth"] = birthday ?: Date(0).formatDate() ?: ""
		data["phone"] = phone ?: ""
		data["country_code"] = country ?: ""
		data["kind"] = kind.name.lowercase()
		data["type"] = type.name.lowercase()
		data["code"] = referralCode ?: ""
		token?.let { data["token"] = it }
		recaptchaAction?.let { data["recaptcha_action"] = it }
		data["platform"] = platform //MARK: 0-Android, 1-IOS

		val elloRequest = ElloRequest(authService, 100100, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun forgotPasswordRequest(email: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(authService, 100400, mapOf("email" to email))
		return createRpcRequest(elloRequest)
	}

	fun newPasswordRequest(email: String, code: String, password: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(authService, 100500, mapOf("email" to email, "code" to code, "new_pass" to password))
		return createRpcRequest(elloRequest)
	}

	fun verifySignupRequest(username: String, code: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(authService, 100800, mapOf("username_or_email" to username, "code" to code))
		return createRpcRequest(elloRequest)
	}

	fun verifyRequest(username: String, code: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(authService, 100300, mapOf("username_or_email" to username, "code" to code))
		return createRpcRequest(elloRequest)
	}

	fun verifyLoginRequest(username: String, code: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(authService, 100700, mapOf("username_or_email" to username, "code" to code))
		return createRpcRequest(elloRequest)
	}

	fun verifyHashRequest(username: String, hash: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(authService, 101100, mapOf("username_or_email" to username, "hash" to hash))
		return createRpcRequest(elloRequest)
	}

	fun verifyHashSignupRequest(username: String, email: String, kind: String, type: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(authService, 100900, mapOf("username" to username, "email" to email, "kind" to kind, "type" to type))
		return createRpcRequest(elloRequest)
	}

	fun changePasswordRequest(oldPassword: String, newPassword: String, code: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(profileService, 100200, mapOf("prev_pass" to oldPassword, "new_pass" to newPassword, "code" to code))
		return createRpcRequest(elloRequest)
	}

	fun verificationCodeRequest(email: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(profileService, 100500, mapOf("email" to email))
		return createRpcRequest(elloRequest)
	}

	fun verificationCodeRequest(email: String, password: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(profileService, 100500, mapOf("email" to email, "password" to password))
		return createRpcRequest(elloRequest)
	}

	fun changeEmailRequest(newEmail: String, hash: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(profileService, 101100, mapOf("new_email" to newEmail, "hash" to hash))
		return createRpcRequest(elloRequest)
	}

	fun withdrawSendApproveCode(): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(walletService, 102100, mapOf())
		return createRpcRequest(elloRequest)
	}

	fun deleteAccountVerificationCodeRequest(email: String, password: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(profileService, 100800, mapOf("email" to email, "password" to password))
		return createRpcRequest(elloRequest)
	}

	fun deleteMagicLinkRequest(hash: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(profileService, 101300, mapOf("hash" to hash))
		return createRpcRequest(elloRequest)
	}

	fun deleteAccount(code: String): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(profileService, 100400, mapOf("code" to code, "reason" to "So Long, and Thanks for All the Fish"))
		return createRpcRequest(elloRequest)
	}

	fun changeProfileRequest(firstName: String?, lastName: String?, username: String, bio: String, gender: String?, birthday: String?, country: String?): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["username"] = username
		data["gender"] = gender ?: ""
		data["birthday"] = birthday ?: Date(0).formatDate() ?: ""
		data["country_code"] = country ?: ""
		data["first_name"] = firstName ?: ""
		data["last_name"] = lastName ?: ""
		data["bio"] = bio

		val elloRequest = ElloRequest(profileService, 100300, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun channelCategoriesRequest(): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(channelsService, 100100, mapOf())
		return createRpcRequest(elloRequest)
	}

	fun channelMusicGenresRequest(): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(channelsService, 100200, mapOf())
		return createRpcRequest(elloRequest)
	}

	fun deleteAccountRequest(): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(profileService, 100400, mapOf())
		return createRpcRequest(elloRequest)
	}

	fun feedSettingsRequest(): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(feedService, 100100, mapOf())
		return createRpcRequest(elloRequest)
	}

	fun saveFeedSettingsRequest(hidden: LongArray?, pinned: LongArray?, showRecommended: Boolean?, showSubscriptionsOnly: Boolean?, showAdult: Boolean?): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()

		if (hidden != null) {
			data["hidden"] = hidden
		}

		if (pinned != null) {
			data["pinned"] = pinned
		}

		if (showRecommended != null) {
			data["show_recommended"] = showRecommended
		}

		if (showSubscriptionsOnly != null) {
			data["show_only_subs"] = showSubscriptionsOnly
		}

		if (showAdult != null) {
			data["show_adult"] = showAdult
		}

		val request = ElloRequest(feedService, 100200, data.toMap())
		return createRpcRequest(request)
	}

	fun resendConfirmationCodeRequest(email: String): TLBizInvokeBizDataRaw {
		val data = mapOf("username_or_email" to email)
		val elloRequest = ElloRequest(authService, 100600, data)
		return createRpcRequest(elloRequest)
	}

	fun availableWalletAssetsRequest(): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(walletService, 100300, mapOf())
		return createRpcRequest(elloRequest)
	}

	fun loadWalletRequest(assetId: Int): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(walletService, 100200, mapOf("asset_id" to assetId))
		return createRpcRequest(elloRequest)
	}

	fun createWalletRequest(assetId: Int): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(walletService, 100100, mapOf("asset_id" to assetId))
		return createRpcRequest(elloRequest)
	}

	fun loadEarningsRequest(walletId: Long): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(walletService, 101100, mapOf("wallet_id" to walletId))
		return createRpcRequest(elloRequest)
	}

//	fun stripePaymentRequest(assetId: Int, walletId: Long, currency: String, message: String?, cardNumber: String, expirationMonth: Int, expirationYear: Int, cvc: Int, amount: Float): TLBizInvokeBizDataRaw {
//		val data = mutableMapOf<String, Any>()
//		data["payment_system"] = "stripe"
//		data["asset_id"] = assetId
//		data["wallet_id"] = walletId
//		data["currency"] = currency
//		data["message"] = message ?: ""
//		data["number"] = cardNumber
//		data["exp_month"] = expirationMonth
//		data["exp_year"] = expirationYear
//		data["csv"] = cvc
//		data["amount"] = amount
//
//		val elloRequest = ElloRequest(walletService, 100500, data.toMap())
//		return createRpcRequest(elloRequest)
//	}

	fun paypalPaymentRequest(assetId: Int, walletId: Long, currency: String, message: String?, amount: Float?, coin: Float?): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["asset_id"] = assetId
		data["wallet_id"] = walletId
		data["currency"] = currency
		data["message"] = message ?: ""

		amount?.let { data["amount"] = it }
		coin?.let { data["coins"] = it }

		val elloRequest = ElloRequest(walletService, 100700, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun stripePaymentRequest(assetId: Int, walletId: Long, currency: String, message: String?, amount: Float?, coin: Float?): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["asset_id"] = assetId
		data["wallet_id"] = walletId
		data["currency"] = currency
		data["message"] = message ?: ""
		amount?.let { data["amount"] = it }
		coin?.let { data["coins"] = it }

		val elloRequest = ElloRequest(walletService, 102500, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun transactionsStatsRequest(walletId: Long): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["period"] = "day"
		data["wallet_id"] = walletId

		val elloRequest = ElloRequest(walletService, 101200, data.toMap())
		return createRpcRequest(elloRequest)
	}

	/**
	 * @param peerId peer ID (user, chat, channel)
	 * @param type 2 - user, 3 - chat, 4 - channel (default value 4)
	 */
	@JvmOverloads
	fun subscribeRequest(peerId: Long, type: Int = 4): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["peer_id"] = peerId
		data["peer_type"] = type

		val elloRequest = ElloRequest(paidSubscriptionService, 100200, data.toMap())
		return createRpcRequest(elloRequest)
	}

	const val PEER_TYPE_USER = 2
	const val PEER_TYPE_GROUP = 3
	const val PEER_TYPE_CHANNEL = 4

	/**
	 * @param peerId peer ID (user, chat, channel)
	 * @param type 2 - user, 3 - chat, 4 - channel (default value 4)
	 */
	fun unsubscribeRequest(peerId: Long, type: Int = 4): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["peer_id"] = peerId
		data["peer_type"] = type

		val elloRequest = ElloRequest(paidSubscriptionService, 100300, data.toMap())
		return createRpcRequest(elloRequest)
	}

	enum class SubscriptionType {
		ACTIVE_CHANNELS, PAST_CHANNELS
	}

	fun getSubscriptionsRequest(subscriptionType: SubscriptionType): TLBizInvokeBizDataRaw {
		val type = when (subscriptionType) {
			SubscriptionType.ACTIVE_CHANNELS -> 0
			SubscriptionType.PAST_CHANNELS -> 1
		}

		val elloRequest = ElloRequest(paidSubscriptionService, 100100, mapOf("filter_type" to type))
		return createRpcRequest(elloRequest)
	}

	fun unsubscribePurchaseRequest(subType: Int): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(aiGenCustomizeService, 100600, mapOf("sub_type" to subType))
		return createRpcRequest(elloRequest)
	}

	/**
	 * @param paymentType all, deposit, transfer, withdraw
	 * @param dateFrom format yyyy-MM-dd
	 * @param dateTo format yyyy-MM-dd
	 */
	fun getTransactionsHistoryRequest(assetId: Int, walletId: Long, paymentType: String, query: String?, dateFrom: String?, dateTo: String?, limit: Int, offset: Int): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["asset_id"] = assetId
		data["wallet_id"] = walletId
		data["payment_type"] = paymentType

		query?.let { data["search"] = query }
		dateFrom?.let { data["date_from"] = dateFrom }
		dateTo?.let { data["date_to"] = dateTo }

		data["limit"] = limit
		data["offset"] = offset

		val elloRequest = ElloRequest(walletService, 101900, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun getWalletPaymentById(walletId: Long, paymentId: Long): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["wallet_id"] = walletId
		data["payment_id"] = paymentId

		val elloRequest = ElloRequest(walletService, 102000, data.toMap())
		return createRpcRequest(elloRequest)
	}

	/**
	 * @param walletId wallet identifier
	 * @param period week, month, year
	 * @param type deposit, withdraw
	 * @param limit count of items
	 * @param offset offset
	 */
	fun getTransferStatisticGraphic(walletId: Long, period: String, type: String, limit: Int, offset: Int): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["wallet_id"] = walletId
		data["period"] = period
		data["type"] = type
		data["limit"] = limit
		data["offset"] = offset

		val elloRequest = ElloRequest(walletService, 101600, data.toMap())
		return createRpcRequest(elloRequest)
	}

	// Get transfer page month activity statistic page data
	fun getLastMonthActivityGraphics(walletId: Long, limit: Int, page: Int): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["wallet_id"] = walletId
		data["limit"] = limit
		data["page"] = page

		val elloRequest = ElloRequest(walletService, 101700, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun getCalculateTransferFee(fromWalletId: Long, toWalletId: Long, currency: String? = null, message: String? = null, amount: Float): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["from_wallet_id"] = fromWalletId
		data["to_wallet_id"] = toWalletId
		data["amount"] = amount

		currency?.let { data["currency"] = currency }
		message?.let { data["message"] = message }

		val elloRequest = ElloRequest(walletService, 102800, data.toMap())
		return createRpcRequest(elloRequest)
	}

	/**
	 * @param paypalEmail email of paypal account (optional)
	 * @param paymentId existing payment id (optional, used to recalculate fees)
	 * @param bankWithdrawRequisitesId id of previously saved bank requisites (optional)
	 */
	fun getWithdrawCreatePayment(assetId: Int, walletId: Long, currency: String, paypalEmail: String?, paymentId: String?, bankWithdrawRequisitesId: Long?, amount: Float, withdrawSystem: String? = null): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["asset_id"] = assetId
		data["wallet_id"] = walletId
		data["currency"] = currency
		data["amount"] = amount

		paypalEmail?.let { data["paypal_email"] = paypalEmail }
		paymentId?.let { data["payment_id"] = paymentId }
		bankWithdrawRequisitesId?.let { data["bank_withdraw_requisites_id"] = bankWithdrawRequisitesId }
		withdrawSystem?.let { data["withdraw_system"] = withdrawSystem }

		val elloRequest = ElloRequest(walletService, 100800, data.toMap())
		return createRpcRequest(elloRequest)
	}

	/**
	 * @param paypalEmail email of paypal account (optional)
	 * @param paymentId existing payment id
	 * @param bankWithdrawRequisitesId id of previously saved bank requisites (optional)
	 */
	fun getWithdrawApprovePayment(walletId: Long, paymentId: String, approveCode: String?, paypalEmail: String?, bankWithdrawRequisitesId: Long?): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["wallet_id"] = walletId
		data["payment_id"] = paymentId

		approveCode?.let { data["approve_code"] = approveCode }
		paypalEmail?.let { data["paypal_email"] = paypalEmail }
		bankWithdrawRequisitesId?.let { data["bank_withdraw_requisites_id"] = bankWithdrawRequisitesId }

		val elloRequest = ElloRequest(walletService, 101000, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun getBankWithdrawsRequisites(limit: Int = Int.MAX_VALUE, offset: Int = 0, isTemplate: Boolean = true): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(walletService, 101500, mapOf("limit" to limit, "offset" to offset, "is_template" to isTemplate))
		return createRpcRequest(elloRequest)
	}

	fun createBankWithdrawRequisites(isTemplate: Boolean, recipientType: String?, businessIdNumber: String?, personalFirstName: String, personalLastName: String, personalPhoneNumber: String, personalEmail: String, bankCountry: String, bankRoutingNumber: String?, bankName: String, bankStreet: String, bankCity: String, bankState: String?, bankSwift: String?, bankRecipientAccountNumber: String, userAddressAddress: String, userAddressStreet: String, userAddressCity: String, userAddressState: String, userAddressZipCode: String, userAddressPostalCode: String, currency: String?): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["is_template"] = isTemplate
		recipientType?.let { data["recipient_type"] = it }
		businessIdNumber?.let { data["business_id_number"] = it }
		data["personal_first_name"] = personalFirstName
		data["personal_last_name"] = personalLastName
		data["personal_phone_number"] = personalPhoneNumber
		data["personal_email"] = personalEmail
		currency?.let { data["currency"] = it }
		data["bank_country"] = bankCountry
		bankRoutingNumber?.let { data["bank_routing_number"] = it }
		data["bank_name"] = bankName
		data["bank_street"] = bankStreet
		data["bank_city"] = bankCity
		bankState?.let { data["bank_state"] = it }
		bankSwift?.let { data["bank_swift"] = it }
		data["bank_recipient_account_number"] = bankRecipientAccountNumber
		data["user_address_address"] = userAddressAddress
		data["user_address_street"] = userAddressStreet
		data["user_address_city"] = userAddressCity
		data["user_address_state"] = userAddressState
		data["user_address_zip_code"] = userAddressZipCode
		data["user_address_postal_code"] = userAddressPostalCode

		val elloRequest = ElloRequest(walletService, 101300, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun editBankWithdrawRequisites(templateId: Long, recipientType: String?, businessIdNumber: String?, personalFirstName: String, personalLastName: String, personalPhoneNumber: String, personalEmail: String, bankCountry: String, bankRoutingNumber: String?, bankName: String, bankStreet: String, bankCity: String, bankState: String?, bankSwift: String?, bankRecipientAccountNumber: String, userAddressAddress: String, userAddressStreet: String, userAddressCity: String, userAddressState: String, userAddressZipCode: String, userAddressPostalCode: String, currency: String?): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["template_id"] = templateId
		recipientType?.let { data["recipient_type"] = it }
		businessIdNumber?.let { data["business_id_number"] = it }
		data["personal_first_name"] = personalFirstName
		data["personal_last_name"] = personalLastName
		data["personal_phone_number"] = personalPhoneNumber
		data["personal_email"] = personalEmail
		currency?.let { data["currency"] = it }
		data["bank_country"] = bankCountry
		bankRoutingNumber?.let { data["bank_routing_number"] = it }
		data["bank_name"] = bankName
		data["bank_street"] = bankStreet
		data["bank_city"] = bankCity
		bankState?.let { data["bank_state"] = it }
		bankSwift?.let { data["bank_swift"] = it }
		data["bank_recipient_account_number"] = bankRecipientAccountNumber
		data["user_address_address"] = userAddressAddress
		data["user_address_street"] = userAddressStreet
		data["user_address_city"] = userAddressCity
		data["user_address_state"] = userAddressState
		data["user_address_zip_code"] = userAddressZipCode
		data["user_address_postal_code"] = userAddressPostalCode

		val elloRequest = ElloRequest(walletService, 102900, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun processGoogleTopupPayment(amount: Float, packageName: String, productId: String, purchaseToken: String): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["amount"] = amount
		data["package"] = packageName
		data["product_id"] = productId
		data["purchase_token"] = purchaseToken

		val elloRequest = ElloRequest(walletService, 103100, data.toMap())
		return createRpcRequest(elloRequest)
	}

	data class GoogleTopupPaymentResponse(
			@field:SerializedName("status") val status: String,
			@field:SerializedName("currency") val currency: String,
			@field:SerializedName("amount") val amount: Float,
			@field:SerializedName("payment_id") val paymentId: Long,
	) : Serializable

	fun startChatBot(botId: Long): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(aiGenCustomizeService, 100200, mapOf("bot_id" to botId))
		return createRpcRequest(elloRequest)
	}

	fun stopChatBot(): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(aiGenCustomizeService, 100300, mapOf())
		return createRpcRequest(elloRequest)
	}

	/**
	 * @param subType 1: text_subscription 2: image_subscription 3: text_pack 4: image_pack
	 * @optional quantity for now is disabled and use default value (200 for text and 120 for image)
	 */
	fun subscribeChatBotRequest(@androidx.annotation.IntRange(from = 1, to = 7) subType: Int, quantity: Int = 0) = createRpcRequest(ElloRequest(aiGenCustomizeService, 100500, mapOf("sub_type" to subType, "quantity" to quantity)))

	/**
	 * @param subType 1: text_subscription 2: image_subscription
	 */
	fun unSubscribeChatBotRequest(@androidx.annotation.IntRange(from = 1, to = 2) subType: Int) = createRpcRequest(ElloRequest(aiGenCustomizeService, 100600, mapOf("sub_type" to subType)))

	/**
	 * @param easyMode true for easy
	 */
	fun changeModeChatBotRequest(easyMode: Boolean) = createRpcRequest(ElloRequest(aiGenCustomizeService, 100400, mapOf("easy_mode" to easyMode)))

	/**
	 * Fetch subscription information for the current user.
	 * @param botId bot id; if 0 is passed then default AI bot is returned
	 */
	fun getSubscriptionsChatBotRequest(botId: Long): TLBizInvokeBizDataRaw {
		return createRpcRequest(ElloRequest(aiGenCustomizeService, 100100, mapOf("bot_id" to botId)))
	}

	/**
	 * get ai space categories list.
	 */
	fun getAiSpaceCategories() = createRpcRequest(ElloRequest(aiGenCustomizeService, 101300, mapOf()))

	/**
	 * request to get all bots.
	 */
	fun getAllBots(categories: List<String>? = null, search: String? = null): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()

		categories?.let { data["categories"] = it }
		search?.let { data["q"] = it }

		return createRpcRequest(ElloRequest(aiGenCustomizeService, 100800, data.toMap()))
	}

	/**
	 * request to get additional info.
	 */
	fun getAdditionalInfo(botId: Long) = createRpcRequest(ElloRequest(aiGenCustomizeService, 101100, mapOf("bot_id" to botId)))

	/**
	 * request to get price info.
	 */
	fun getPriceInfo() = createRpcRequest(ElloRequest(aiGenCustomizeService, 101200, mapOf()))

	/**
	 * @param from Source wallet ID
	 * @param to Destination wallet ID
	 * @param amount Amount to transfer
	 * @param currency Currency of transfer
	 */
	fun transferBetweenWalletsRequest(from: Long, to: Long, amount: Float, currency: String = WalletHelper.DEFAULT_CURRENCY): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["from_wallet_id"] = from
		data["to_wallet_id"] = to
		data["amount"] = amount
		data["currency"] = currency

		val elloRequest = ElloRequest(walletService, 102300, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun generateShareHash(peerId: Long, peerType: Int, msgId: Int): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["peer_id"] = peerId
		data["peer_type"] = peerType
		data["msg_id"] = msgId

		val elloRequest = ElloRequest(chatsService, 100100, data.toMap())
		return createRpcRequest(elloRequest)
	}

	fun userCheckHasDialog(userId: Long): TLBizInvokeBizDataRaw {
		val elloRequest = ElloRequest(profileService, 100900, mapOf("user_id" to userId))
		return createRpcRequest(elloRequest)
	}

	fun getLikesForMessage(messageId: Int, userId: Long, channelId: Long): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["user_id"] = userId
		data["msg_id"] = messageId
		data["fromId"] = mapOf("peer_type" to 4, "peer_id" to channelId)

		val elloRequest = ElloRequest(likesService, 100100, data)
		return createRpcRequest(elloRequest)
	}

	fun likeMessage(messageId: Int, userId: Long, channelId: Long): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["user_id"] = userId
		data["msg_id"] = messageId
		data["fromId"] = mapOf("peer_type" to 4, "peer_id" to channelId)

		val elloRequest = ElloRequest(likesService, 100200, data)
		return createRpcRequest(elloRequest)
	}

	fun revokeLikeFromMessage(messageId: Int, userId: Long, channelId: Long): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["user_id"] = userId
		data["msg_id"] = messageId
		data["fromId"] = mapOf("peer_type" to 4, "peer_id" to channelId)

		val elloRequest = ElloRequest(likesService, 100300, data)
		return createRpcRequest(elloRequest)
	}

	fun accountDeletionLeftoversRequest(): TLBizInvokeBizDataRaw {
		return createRpcRequest(ElloRequest(profileService, 101000, mapOf()))
	}

	fun verifyGoogleRequest(mainPackage: String, subscriptionId: String, purchaseToken: String): TLBizInvokeBizDataRaw {
		val data = mutableMapOf<String, Any>()
		data["package"] = mainPackage
		data["subscription_id"] = subscriptionId
		data["purchase_token"] = purchaseToken

		val elloRequest = ElloRequest(donate, 100200, data)
		return createRpcRequest(elloRequest)
	}

	private fun createRpcRequest(request: ElloRequest): TLBizInvokeBizDataRaw {
		val req = TLBizInvokeBizDataRaw()
		req.bizData = TLBizDataRaw()
		req.bizData?.data = gson.toJson(request).toByteArray(StandardCharsets.UTF_8)
		return req
	}

	fun TLBizDataRaw.readString(): String? {
		val data = data ?: return null

		return try {
			String(data, StandardCharsets.UTF_8)
		}
		catch (e: Exception) {
			FileLog.e(e)
			null
		}
	}

	inline fun <reified T> TLBizDataRaw.readData(): T? {
		return readString()?.fromJson()
	}
}
