/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.tgnet

import android.content.SharedPreferences
import androidx.core.content.edit
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BaseController
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.utils.decrypt
import org.telegram.messenger.utils.encrypt
import org.telegram.messenger.utils.fromJson
import org.telegram.messenger.utils.toJson
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.tlrpc.TL_error
import org.telegram.ui.profile.wallet.TransactionsView
import java.lang.ref.WeakReference
import kotlin.coroutines.suspendCoroutine

class WalletHelper private constructor(num: Int) : BaseController(num) {
	private var availableAsset: ElloRpc.WalletAsset? = null
	private var listeners = mutableSetOf<WeakReference<OnWalletChangedListener>>()

	var currentTransferOutPaymentId: String? = null

	var wallet: ElloRpc.UserWallet? = null
		private set

	var earningsWallet: ElloRpc.UserWallet? = null
		private set

	var earnings: ElloRpc.Earnings? = null
		private set

	var earnStats: List<ElloRpc.TransferStatsData>? = null
		private set

	var walletStats: List<ElloRpc.TransferStatsData>? = null
		private set

	private val settings: SharedPreferences
		get() = MessagesController.getMainSettings(currentAccount)

	fun activateWallet() {
		if (!UserConfig.isLoggedIn) {
			return
		}

		val req = ElloRpc.createWalletRequest(assetId = DEFAULT_ASSET_ID)

		connectionsManager.sendRequest(req, { response, _ ->
			if (response is TLRPC.TL_biz_dataRaw) {
				loadWallet()
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	fun addListener(listener: OnWalletChangedListener) {
		listeners.add(WeakReference(listener))
		listener.onWalletChanged(wallet = wallet, earnings = earnings)
	}

	fun removeListener(listener: OnWalletChangedListener) {
		listeners.removeIf { it.get() == listener }
	}

	private fun loadWalletTransactionsStats() {
		if (!UserConfig.isLoggedIn) {
			return
		}

		val walletId = wallet?.id ?: 0L

		if (walletId == 0L) {
			return
		}

		val req = ElloRpc.getLastMonthActivityGraphics(walletId, limit = TransactionsView.numberOfColumns, page = 0)

		connectionsManager.sendRequest(req, { response, _ ->
			if (response is TLRPC.TL_biz_dataRaw) {
				val stats = response.readData<ElloRpc.LastMonthActivityGraphicsResponse>()?.data
				walletStats = stats
				informAboutWalletStats()
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun loadEarnTransactionsStats() {
		if (!UserConfig.isLoggedIn) {
			return
		}

		val earningsWalletId = earningsWallet?.id ?: 0L

		if (earningsWalletId == 0L) {
			return
		}

		val req = ElloRpc.getLastMonthActivityGraphics(earningsWalletId, limit = TransactionsView.numberOfColumns, page = 0)

		connectionsManager.sendRequest(req, { response, _ ->
			if (response is TLRPC.TL_biz_dataRaw) {
				val stats = response.readData<ElloRpc.LastMonthActivityGraphicsResponse>()?.data
				earnStats = stats
				informAboutEarnStats()
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	fun reload() {
		availableAsset = null
		wallet = null
		earningsWallet = null
		earnings = null
		earnStats = null
		walletStats = null

		loadWallet()
	}

	fun loadWallet() {
		if (!UserConfig.isLoggedIn) {
			return
		}

		if (availableAsset == null) {
			loadAvailableAsset {
				if (availableAsset != null) {
					loadWallet()
				}
			}

			return
		}

		val req = ElloRpc.loadWalletRequest(assetId = DEFAULT_ASSET_ID)

		connectionsManager.sendRequest(req, { response, _ ->
			if (response is TLRPC.TL_biz_dataRaw) {
				val allWallets = response.readData<ElloRpc.Wallets>()?.wallets

				allWallets?.find { it.type == DEFAULT_WALLET_TYPE }?.takeIf { it.id != 0L }?.let {
					wallet = it

					FileLog.d("Wallet id is ${it.id}")

					saveWalletsCache()

					loadWalletTransactionsStats()
				}

				allWallets?.find { it.type == EARNING_WALLET_TYPE }?.takeIf { it.id != 0L }?.let {
					earningsWallet = it
					saveWalletsCache()
					loadEarnings()
				}

				informListeners()
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	/**
	 * @param callback callback with (optional) link to payment page and (optional) error
	 * @return request id
	 */
	fun getPayPalPaymentLink(assetId: Int, walletId: Long, currency: String, message: String, amount: Float, callback: ((link: String?, paymentId: Long?, error: TL_error?) -> Unit)?): Int {
		val req = ElloRpc.paypalPaymentRequest(assetId = assetId, walletId = walletId, currency = currency, message = message, coin = amount, amount = null)

		return connectionsManager.sendRequest(req, { response, error ->
			var link: String? = null
			var paymentId: Long? = null

			if (response is TLRPC.TL_biz_dataRaw) {
				val res = response.readData<ElloRpc.PayPalPaymentLinkResponse>()
				link = res?.link?.trim()
				paymentId = res?.paymentId
			}

			AndroidUtilities.runOnUIThread {
				callback?.invoke(link, paymentId, error)
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun informListeners() {
		AndroidUtilities.runOnUIThread {
			synchronized(this) {
				listeners.forEach {
					it.get()?.onWalletChanged(wallet = wallet, earnings = earnings)
				}
			}
		}
	}

	private fun informAboutEarnStats() {
		AndroidUtilities.runOnUIThread {
			synchronized(this) {
				listeners.forEach {
					it.get()?.onEarnStatsChanged(earnStats)
				}
			}
		}
	}

	private fun informAboutWalletStats() {
		AndroidUtilities.runOnUIThread {
			synchronized(this) {
				listeners.forEach {
					it.get()?.onWalletStatsChanged(walletStats)
				}
			}
		}
	}

	private fun loadEarnings() {
		if (!UserConfig.isLoggedIn) {
			return
		}

		val earningsWalletId = earningsWallet?.id ?: 0L

		if (earningsWalletId == 0L) {
			return
		}

		val req = ElloRpc.loadEarningsRequest(walletId = earningsWalletId)

		connectionsManager.sendRequest(req, { response, _ ->
			if (response is TLRPC.TL_biz_dataRaw) {
				val earnings = response.readData<ElloRpc.Earnings>()

				if (earnings != null) {
					this.earnings = earnings
					saveWalletsCache()

					loadEarnTransactionsStats()
				}
			}

			informListeners()
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun loadAvailableAsset(callback: (() -> Unit)? = null) {
		if (!UserConfig.isLoggedIn) {
			return
		}

		val req = ElloRpc.availableWalletAssetsRequest()

		connectionsManager.sendRequest(req, { response, _ ->
			if (response is TLRPC.TL_biz_dataRaw) {
				val data = response.readData<ElloRpc.AvailableAssetsResponse>()
				val asset = data?.assets?.find {
					it.id == DEFAULT_ASSET_ID
				}

				if (asset != null) {
					availableAsset = asset
					settings.edit().putString(availableAssetField, availableAsset.toJson()).commit()
				}
			}

			callback?.invoke()
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private var withdrawPaymentRequestId = 0

	suspend fun calculateTransferFee(fromWalletId: Long, toWalletId: Long, currency: String? = null, message: String? = null, amount: Float): Float {
		return suspendCoroutine {
			val req = ElloRpc.getCalculateTransferFee(fromWalletId = fromWalletId, toWalletId = toWalletId, currency = currency, message = message, amount = amount)

			connectionsManager.sendRequest(req, { response, error ->
				var fee: Float? = null

				if (response is TLRPC.TL_biz_dataRaw) {
					fee = response.readData<ElloRpc.CalculateTransferFeeResponse>()?.fee
				}

				it.resumeWith(Result.success(fee ?: 0f))
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}
	}

	@Synchronized
	fun createWithdrawPaymentRequest(walletId: Long, amount: Float, currency: String = DEFAULT_CURRENCY, paypalEmail: String? = null, paymentId: String? = null, bankWithdrawRequisitesId: Long? = null, withdrawSystem: String? = null, callback: ((paymentId: String?, amount: Float?, amountFiat: Float?, withdrawMax: Float?, withdrawMin: Float?, fee: Float?, paymentSystemFee: Float?, error: TL_error?) -> Unit)? = null): Int {
		if (withdrawPaymentRequestId != 0) {
			connectionsManager.cancelRequest(withdrawPaymentRequestId, true)
			withdrawPaymentRequestId = 0
		}

		val req = ElloRpc.getWithdrawCreatePayment(assetId = DEFAULT_ASSET_ID, walletId = walletId, currency = currency, paypalEmail = paypalEmail, paymentId = paymentId, bankWithdrawRequisitesId = bankWithdrawRequisitesId, amount = amount, withdrawSystem = withdrawSystem)

		withdrawPaymentRequestId = connectionsManager.sendRequest(req, { response, error ->
			var receivedPaymentId: String? = null
			var withdrawMax: Float? = null
			var withdrawMin: Float? = null
			var fee: Float? = null
			var paymentSystemFee: Float? = null
			var receiveAmount: Float? = null
			var receiveAmountFiat: Float? = null

			if (response is TLRPC.TL_biz_dataRaw) {
				val data = response.readData<ElloRpc.WithdrawPayment>()

				receivedPaymentId = data?.paymentId
				receiveAmount = data?.amount
				receiveAmountFiat = data?.amountFiat
				withdrawMax = data?.withdrawMax
				withdrawMin = data?.withdrawMin
				fee = data?.fee
				paymentSystemFee = data?.paymentSystemFee
			}

			AndroidUtilities.runOnUIThread {
				callback?.invoke(receivedPaymentId, receiveAmount, receiveAmountFiat, withdrawMax, withdrawMin, fee, paymentSystemFee, error)
			}

			withdrawPaymentRequestId = 0
		}, ConnectionsManager.RequestFlagFailOnServerErrors)

		return withdrawPaymentRequestId
	}

	fun cleanup() {
		settings.edit {
			remove(availableAssetField)
			remove(userWalletField)
			remove(userEarningsField)
			remove(earningsWalletField)
		}

		wallet = null
		earningsWallet = null
		earnings = null

		listeners.clear()
	}

	@Synchronized
	private fun saveWalletsCache() {
		settings.edit {
			wallet?.toJson()?.encrypt()?.let {
				putString(userWalletField, it)
			}

			earnings?.toJson()?.encrypt()?.let {
				putString(userEarningsField, it)
			}

			earningsWallet?.toJson()?.encrypt()?.let {
				putString(earningsWalletField, it)
			}
		}
	}

	init {
		settings.getString(availableAssetField, null)?.decrypt()?.let {
			it.fromJson<Map<String, Any>>()?.let { assetMap ->
				ElloRpc.WalletAsset.fromMap(assetMap)?.let { walletAsset ->
					availableAsset = walletAsset
				}
			}
		}

		settings.getString(userWalletField, null)?.decrypt()?.let {
			it.fromJson<Map<String, Any>>()?.let { walletMap ->
				ElloRpc.UserWallet.fromMap(walletMap)?.let { userWallet ->
					wallet = userWallet
				}
			}
		}

		settings.getString(userEarningsField, null)?.decrypt()?.let {
			it.fromJson<Map<String, Any>>()?.let { earningsMap ->
				ElloRpc.Earnings.fromMap(earningsMap)?.let { userEarnings ->
					earnings = userEarnings
				}
			}
		}
	}

	fun findWallet(id: Long): ElloRpc.UserWallet? {
		if (wallet?.id == id) {
			return wallet
		}

		if (earningsWallet?.id == id) {
			return earningsWallet
		}

		return null
	}

	interface OnWalletChangedListener {
		fun onWalletChanged(wallet: ElloRpc.UserWallet?, earnings: ElloRpc.Earnings?)
		fun onEarnStatsChanged(stats: List<ElloRpc.TransferStatsData>?) {}
		fun onWalletStatsChanged(stats: List<ElloRpc.TransferStatsData>?) {}
	}

	private val availableAssetField: String
		get() = "available_asset_$currentAccount"

	private val userWalletField: String
		get() = "user_wallet_$currentAccount"

	private val userEarningsField: String
		get() = "user_earnings_$currentAccount"

	private val earningsWalletField: String
		get() = "earnings_wallet_$currentAccount"

	companion object {
		private val instances = arrayOfNulls<WalletHelper>(UserConfig.MAX_ACCOUNT_COUNT)
		private const val DEFAULT_WALLET_TYPE = "main"
		private const val EARNING_WALLET_TYPE = "earning"
		const val DEFAULT_ASSET_ID = 2 // USD
		const val minTopupAmount = 10f // 10 USD

		// const val DEFAULT_CURRENCY_SYMBOL = "$"
		const val DEFAULT_CURRENCY = "usd"

		@JvmStatic
		@Synchronized
		fun getInstance(num: Int): WalletHelper {
			var localInstance = instances[num]

			if (localInstance == null) {
				synchronized(WalletHelper::class.java) {
					localInstance = instances[num]

					if (localInstance == null) {
						localInstance = WalletHelper(num)
						instances[num] = localInstance
					}
				}
			}

			return localInstance!!
		}

//		fun assetSymbolToRealSymbol(symbol: String?) = when (symbol?.lowercase()) {
//			"usd" -> "$"
//			else -> DEFAULT_CURRENCY_SYMBOL
//		}
	}
}
