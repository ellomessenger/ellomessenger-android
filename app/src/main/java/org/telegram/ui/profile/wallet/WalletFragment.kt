/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Shamil Afandiyev, Ello 2024
 */
package org.telegram.ui.profile.wallet

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnScrollChangeListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.databinding.*
import org.telegram.messenger.utils.dp
import org.telegram.messenger.utils.fillElloCoinLogos
import org.telegram.messenger.utils.getDimensionRaw
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.WalletHelper
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.buildDialog
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.BulletSpan
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import java.io.Serializable
import kotlin.math.abs

class WalletFragment : BaseFragment(), WalletHelper.OnWalletChangedListener, WalletTransactionsDataSource.WalletTransactionsListener {
	private var hideAnimatorSet: AnimatorSet? = null
	private var showAnimatorSet: AnimatorSet? = null
	private var selectedWallet: Serializable? = null
	private var recyclerView: RecyclerView? = null
	private var noWalletContainerViewBinding: NoWalletContainerViewBinding? = null
	private var walletContainerViewBinding: WalletContainerViewBinding? = null
	private var emptyWalletContainerViewBinding: EmptyWalletContainerViewBinding? = null
	private val transactionsDataSource = WalletTransactionsDataSource().also { it.listener = this }
	private val searchHeaderHeight by lazy { context!!.resources.getDimensionRaw(R.dimen.common_size_30dp) }
	private val searchHeaderView by lazy { WalletSearchHeaderBinding.inflate(LayoutInflater.from(context)) }
	private val searchHeaderViewFake by lazy { WalletSearchHeaderBinding.inflate(LayoutInflater.from(context)) }
	private val walletsAdapter = WalletsAdapter()
	private var emptyWalletVisited = false
	private var helpButton: ActionBarMenuItem? = null
	private var isSkipped = false

	private fun getNewWallet(position: Int): Serializable? {
		return (walletContainerViewBinding?.walletsViewPager?.adapter as? WalletsAdapter)?.wallets?.getOrNull(position)
	}

	private fun createNoWalletContainerView() {
		val binding = NoWalletContainerViewBinding.inflate(LayoutInflater.from(parentActivity))

		binding.activateWalletButton.setOnClickListener {
			walletHelper.activateWallet()
		}

		binding.walletIntroInfoLabel.text = context?.resources?.getStringArray(R.array.wallet_intro_info)?.fold(SpannableStringBuilder()) { builder, row ->
			builder.append(row, BulletSpan(11.dp, context?.getColor(R.color.dark) ?: 0, 2.dp), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append('\n')
		}

		noWalletContainerViewBinding = binding
	}

	private fun createWalletContainerView() {
		val binding = WalletContainerViewBinding.inflate(LayoutInflater.from(parentActivity), recyclerView, false)

		binding.topupButton.setOnClickListener {
			val walletId = when (selectedWallet) {
				is ElloRpc.UserWallet -> walletHelper.wallet?.id
				is ElloRpc.Earnings -> walletHelper.earningsWallet?.id
				else -> null
			} ?: return@setOnClickListener

			val args = Bundle()
			args.putLong(ARG_WALLET_ID, walletId)
			args.putBoolean(ARG_IS_TOPUP, true)
			args.putBoolean(PaymentMethodsFragment.ARG_SHOW_ELLO_CARD, false)

			presentFragment(PaymentMethodsFragment(args))
		}

		binding.transferOutButton.setOnClickListener {
			var showElloCard = false

			val walletId = when (selectedWallet) {
				is ElloRpc.UserWallet -> {
					walletHelper.wallet?.id
				}

				is ElloRpc.Earnings -> {
					showElloCard = true
					walletHelper.earningsWallet?.id
				}

				else -> {
					null
				}
			} ?: return@setOnClickListener

			val args = Bundle()
			args.putLong(ARG_WALLET_ID, walletId)
			args.putBoolean(ARG_IS_TOPUP, false)
			args.putBoolean(PaymentMethodsFragment.ARG_SHOW_ELLO_CARD, showElloCard)

			presentFragment(PaymentMethodsFragment(args))
		}

		binding.helpEarningsButton.setOnClickListener {
			val builder = AlertsCreator.createSimpleAlert(it.context, it.context.getString(R.string.note), it.context.getString(R.string.earnings_payout_info))
			val dialog = builder?.create()

			showDialog(dialog)
		}

		binding.detailedHistoryButton.setOnClickListener {
			val walletId = when (selectedWallet) {
				is ElloRpc.UserWallet -> walletHelper.wallet?.id
				is ElloRpc.Earnings -> walletHelper.earningsWallet?.id
				else -> null
			} ?: return@setOnClickListener

			val walletType = when (selectedWallet) {
				is ElloRpc.UserWallet -> context?.getString(R.string.main_wallet)
				is ElloRpc.Earnings -> context?.getString(R.string.business_wallet)
				else -> null
			} ?: return@setOnClickListener

			val args = Bundle()
			args.putLong(ARG_WALLET_ID, walletId)
			args.putString(WALLET_TYPE, walletType)

			presentFragment(TransactionsHistoryFragment(args))
		}

		binding.walletsViewPager.registerOnPageChangeCallback(onPageChangeCallback)
		binding.walletsViewPager.adapter = walletsAdapter

		walletContainerViewBinding = binding
	}

	private fun createEmptyWalletContainerView() {
		val binding = EmptyWalletContainerViewBinding.inflate(LayoutInflater.from(parentActivity))

		binding.topUpWalletButton.setOnClickListener {
			val walletId = walletHelper.wallet?.id ?: return@setOnClickListener

			val args = Bundle()
			args.putLong(ARG_WALLET_ID, walletId)
			args.putBoolean(ARG_IS_TOPUP, true)
			args.putBoolean(PaymentMethodsFragment.ARG_SHOW_ELLO_CARD, false)

			presentFragment(PaymentMethodsFragment(args))
		}

		binding.skipEmptyWalletButton.setOnClickListener {
			isSkipped = true
			reloadState()
		}

		binding.emptyWalletInfoLabel.text = context?.resources?.getStringArray(R.array.wallet_intro_info)?.fold(SpannableStringBuilder().apply {
			append(context?.getString(R.string.wallet_intro_title_info), StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append('\n').append('\n')
		}) { builder, row ->
			builder.append(row, BulletSpan(11.dp, context?.getColor(R.color.dark) ?: 0, 2.dp), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append('\n')
		}

		emptyWalletContainerViewBinding = binding
	}

	private fun updateWalletButtonsVisibility(position: Int) {
		val newWallet = getNewWallet(position)

		if (newWallet == selectedWallet) {
			return
		}

		selectedWallet = newWallet

		val binding = walletContainerViewBinding ?: return

		binding.topupButton.gone()
		binding.transferOutButton.gone()
		binding.helpEarningsButton.gone()

		when (newWallet) {
			is ElloRpc.UserWallet -> {
				binding.topupButton.visible()
				// binding.transferOutButton.visible() // MARK: uncomment to enable
				binding.helpEarningsButton.gone()
				// binding.donateStatsButton.visible() // MARK: uncomment to enable
				binding.donateStatsButton.gone()
				binding.earnWalletNotice.gone()
			}

			is ElloRpc.Earnings -> {
				binding.topupButton.gone()
				binding.transferOutButton.visible()
				binding.helpEarningsButton.visible()
				binding.donateStatsButton.gone()
				binding.earnWalletNotice.visible()
			}

			else -> {
				throw IllegalStateException("Unknown wallet type")
			}
		}
	}

	private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
		override fun onPageSelected(position: Int) {
			val binding = walletContainerViewBinding ?: return

			binding.pageIndicatorContainer.children.forEach {
				it.isSelected = (it.tag == position)
			}

			if (getNewWallet(position) == selectedWallet) {
				return
			}

			hideAnimatorSet?.cancel()
			hideAnimatorSet = null

			showAnimatorSet?.cancel()
			showAnimatorSet = null

			val hideAnimators = mutableListOf<Animator>()
			hideAnimators.add(ObjectAnimator.ofFloat(binding.topupButton, View.ALPHA, 1f, 0f))
			hideAnimators.add(ObjectAnimator.ofFloat(binding.transferOutButton, View.ALPHA, 1f, 0f))
			hideAnimators.add(ObjectAnimator.ofFloat(binding.helpEarningsButton, View.ALPHA, 1f, 0f))

			hideAnimatorSet = AnimatorSet()
			hideAnimatorSet?.playTogether(hideAnimators)

			hideAnimatorSet?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					updateWalletButtonsVisibility(position)

					reloadStats()

					val showAnimators = mutableListOf<Animator>()
					showAnimators.add(ObjectAnimator.ofFloat(binding.topupButton, View.ALPHA, 0f, 1f))
					showAnimators.add(ObjectAnimator.ofFloat(binding.transferOutButton, View.ALPHA, 0f, 1f))
					showAnimators.add(ObjectAnimator.ofFloat(binding.helpEarningsButton, View.ALPHA, 0f, 1f))

					showAnimatorSet = AnimatorSet()
					showAnimatorSet?.playTogether(showAnimators)

					showAnimatorSet?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							hideAnimatorSet = null
							showAnimatorSet = null

							updateWalletButtonsVisibility(position)
						}
					})

					showAnimatorSet?.start()
				}
			})

			hideAnimatorSet?.start()
		}
	}

	override fun createView(context: Context): View? {
		actionBar?.setTitle(context.getString(R.string.wallet))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.castShadows = false

		val menu = actionBar?.createMenu()
		helpButton = menu?.addItem(id = BUTTON_HELP, icon = R.drawable.support)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						if (emptyWalletVisited) {
							val mainSettings = MessagesController.getMainSettings(currentAccount)
							mainSettings.edit().putBoolean("emptyWalletScreen_$currentAccount", true).commit()
						}

						finishFragment()
					}

					BUTTON_HELP -> {
						val (title, text) = when (selectedWallet ?: return) {
							is ElloRpc.UserWallet -> {
								context.getString(R.string.main_wallet) to context.getString(R.string.balance_description)
							}

							is ElloRpc.Earnings -> {
								context.getString(R.string.business_wallet) to context.getString(R.string.earnings_description)
							}

							else -> {
								return
							}
						}

						buildDialog(context) {
							setTitle(title)
							setMessage(text)
							setPositiveButton(context.getString(R.string.OK), null)
						}.show()
					}
				}
			}
		})

		recyclerView = RecyclerView(context)
		recyclerView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
		recyclerView?.itemAnimator = null

		recyclerView?.setOnScrollChangeListener(object : OnScrollChangeListener {
			private var offset = 0

			override fun onScrollChange(v: View, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
				offset += oldScrollY

				v.postDelayed({
					checkSearchHeaderLocation(offset)
				}, 10)
			}
		})

		recyclerView?.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			private val viewTypeWallet = 0
			private val viewTypeTransaction = 1
			private val viewTypeEmptyWallet = 2
			private val viewTypeSearchHeader = 3
			private val viewTypeTransactionGroupDateHeader = 4
			private val viewTypeStub = 5

			override fun getItemViewType(position: Int): Int {
				return when (position) {
					0 -> {
						viewTypeWallet
					}

					1 -> {
						viewTypeSearchHeader
					}

					else -> {
						val transactions = transactionsDataSource.getTransactionsList()

						if (transactions.isEmpty()) {
							viewTypeEmptyWallet
						}
						else {
							if (position - 2 in transactions.indices) {
								val entry = transactions[position - 2]

								if (entry is ElloRpc.TransactionHistoryEntry) {
									viewTypeTransaction
								}
								else {
									viewTypeTransactionGroupDateHeader
								}
							}
							else {
								viewTypeStub
							}
						}
					}
				}
			}

			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
				return when (viewType) {
					viewTypeWallet -> {
						object : RecyclerView.ViewHolder(FrameLayout(context).apply {
							layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat())
						}) {}
					}

					viewTypeSearchHeader -> {
						(object : RecyclerView.ViewHolder(FrameLayout(context).apply {
							layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, searchHeaderHeight)
							addView(searchHeaderViewFake.root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, searchHeaderHeight))
						}) {}).also {
							it.itemView.setBackgroundResource(R.color.light_background)
						}
					}

					viewTypeTransaction -> {
						TransactionViewHolder(binding = WalletTransactionViewBinding.inflate(LayoutInflater.from(context), parent, false)).also {
							it.itemView.setOnClickListener { _ ->
								it.transaction?.let { transaction ->
									val args = Bundle()
									args.putSerializable(TransactionDetailsFragment.ARG_TRANSACTION, transaction)

									val walletType = when (selectedWallet) {
										is ElloRpc.UserWallet -> context.getString(R.string.main_wallet)
										is ElloRpc.Earnings -> context.getString(R.string.business_wallet)
										else -> null
									} ?: return@setOnClickListener

									args.putString(WALLET_TYPE, walletType)

									presentFragment(TransactionDetailsFragment(args))
								}
							}
						}
					}

					viewTypeTransactionGroupDateHeader -> {
						TransactionsDateHeaderViewHolder(binding = WalletTransactionDateHeaderBinding.inflate(LayoutInflater.from(context), parent, false))
					}

					viewTypeEmptyWallet -> {
						object : RecyclerView.ViewHolder(LinearLayout(context).apply {
							orientation = LinearLayout.VERTICAL
							setVerticalGravity(Gravity.CENTER)
							setHorizontalGravity(Gravity.CENTER)

							layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat())

							val emptyImage = RLottieImageView(context)
							emptyImage.setAutoRepeat(true)
							emptyImage.setAnimation(R.raw.panda_chat_list_no_results, 160, 160)
							emptyImage.playAnimation()

							addView(emptyImage, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

							val textView = TextView(context)
							textView.gravity = Gravity.CENTER
							textView.text = context.getString(R.string.no_transactions_yet)
							textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.disabled_text, null))

							addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24f, 6f, 24f, 0f))
						}) {}
					}

					viewTypeStub -> {
						object : RecyclerView.ViewHolder(FrameLayout(context).apply {
							layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, stubHeight(transactionsDataSource.getTransactionsList()).toFloat())
						}) {}
					}

					else -> {
						throw IllegalStateException("Unknown viewType")
					}
				}
			}

			private fun getTransactionsHeight(transactions: List<Serializable>): Int {
				return transactions.sumOf {
					if (it is ElloRpc.TransactionHistoryEntry) {
						74
					}
					else {
						28
					}.toInt()
				}
			}

			private fun stubHeight(transactions: List<Serializable>): Int {
				val transactionsHeight = getTransactionsHeight(transactions)
				return AndroidUtilities.px((recyclerView?.measuredHeight ?: 0).toFloat()) - transactionsHeight - searchHeaderHeight.toInt()
			}

			private fun shouldAddStub(transactions: List<Serializable>): Boolean {
				val transactionsHeight = getTransactionsHeight(transactions)
				val totalHeightPx = AndroidUtilities.dp(transactionsHeight + searchHeaderHeight)
				return totalHeightPx < (recyclerView?.measuredHeight ?: 0)
			}

			override fun getItemCount(): Int {
				if (walletContainerViewBinding == null) {
					return 1
				}

				var count = 2
				val transactions = transactionsDataSource.getTransactionsList()

				if (transactions.isEmpty()) {
					count += 1
				}
				else {
					count += transactions.size

					if (shouldAddStub(transactions)) {
						count += 1
					}
				}

				return count
			}

			override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
				when (holder.itemViewType) {
					viewTypeWallet -> {
						val root = holder.itemView as FrameLayout
						root.removeAllViews()

						val view = walletContainerViewBinding?.root ?: noWalletContainerViewBinding?.root ?: emptyWalletContainerViewBinding?.root ?: return

						(view.parent as? ViewGroup)?.removeView(view)
						root.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
					}

					viewTypeSearchHeader -> {
						// unused
					}

					viewTypeTransaction -> {
						val transaction = (transactionsDataSource.getTransactionsList()[position - 2] as? ElloRpc.TransactionHistoryEntry)
						(holder as TransactionViewHolder).transaction = transaction

						if (transaction == transactionsDataSource.getTransactionsList().lastOrNull()) {
							loadNextTransactionsPage(force = false)
						}
					}

					viewTypeTransactionGroupDateHeader -> {
						(holder as TransactionsDateHeaderViewHolder).date = (transactionsDataSource.getTransactionsList()[position - 2] as? String)
					}

					viewTypeEmptyWallet -> {
						// unused
					}

					viewTypeStub -> {
						loadNextTransactionsPage(force = false)
					}
				}
			}
		}

		searchHeaderView.root.gone()

		val frameLayout = FrameLayout(context)

		frameLayout.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		frameLayout.addView(searchHeaderView.root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, searchHeaderHeight.toInt(), Gravity.TOP))

		fragmentView = frameLayout

		return fragmentView
	}

	private fun checkSearchHeaderLocation(offset: Int) {
		if (walletContainerViewBinding == null) {
			searchHeaderView.root.gone()
			return
		}

		val newValue = offset < 0

		if (newValue != actionBar?.castShadows) {
			actionBar?.castShadows = newValue
			actionBar?.requestLayout()
		}

		if (offset <= -(walletContainerViewBinding?.root?.height ?: 0)) {
			searchHeaderView.root.visible()
		}
		else {
			searchHeaderView.root.gone()
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	private fun reloadState() {
		val wallet = walletHelper.wallet
		val earnings = walletHelper.earnings
		val mainSettings = MessagesController.getMainSettings(currentAccount)

		if (wallet == null) {
			helpButton?.gone()

			if (noWalletContainerViewBinding == null) {
				createNoWalletContainerView()
			}

			emptyWalletContainerViewBinding?.let {
				(it.root.parent as? ViewGroup)?.removeView(it.root)
			}

			emptyWalletContainerViewBinding = null

			walletContainerViewBinding?.let {
				(it.root.parent as? ViewGroup)?.removeView(it.root)
			}

			walletContainerViewBinding = null
		}
		else if (!isSkipped && wallet.isEmpty() && (earnings == null || earnings.isEmpty()) && !mainSettings.getBoolean("emptyWalletScreen_$currentAccount", false)) {
			helpButton?.gone()

			if (emptyWalletContainerViewBinding == null) {
				createEmptyWalletContainerView()
			}

			noWalletContainerViewBinding?.let {
				(it.root.parent as? ViewGroup)?.removeView(it.root)
			}

			noWalletContainerViewBinding = null

			walletContainerViewBinding?.let {
				(it.root.parent as? ViewGroup)?.removeView(it.root)
			}

			walletContainerViewBinding = null

			emptyWalletVisited = true
		}
		else {
			helpButton?.visible()

			if (walletContainerViewBinding == null) {
				createWalletContainerView()
			}

			noWalletContainerViewBinding?.let {
				(it.root.parent as? ViewGroup)?.removeView(it.root)
			}

			noWalletContainerViewBinding = null

			emptyWalletContainerViewBinding?.let {
				(it.root.parent as? ViewGroup)?.removeView(it.root)
			}

			emptyWalletContainerViewBinding = null

			reloadCards(wallet = wallet, earnings = walletHelper.earnings)
		}

		recyclerView?.adapter?.notifyDataSetChanged()
	}

	@Synchronized
	private fun reloadStats() {
		val context = context ?: return
		val walletBinding = walletContainerViewBinding ?: return
		val selectedWallet = selectedWallet ?: return

		val stats = when (selectedWallet) {
			is ElloRpc.UserWallet -> walletHelper.walletStats
			is ElloRpc.Earnings -> walletHelper.earnStats
			else -> null
		} ?: emptyList()

		val realStats = stats.groupBy { it.date }.map { dateEntries ->
			val amount = dateEntries.value.sumOf {
				if (it.type == "deposit") {
					it.amount.toDouble()
				}
				else {
					-it.amount.toDouble()
				}
			}

			ElloRpc.TransferStatsData(date = dateEntries.key, type = "deposit", amount = amount.toFloat(), period = "", dateFrom = null, dateTo = null)
		}

		val total = realStats.sumOf { it.amount.toDouble() }
		val sign = if (total >= 0) "" else "-"

		walletBinding.monthlyAmountLabel.text = context.getString(R.string.monthly_stats_amount_format, sign, abs(total)).fillElloCoinLogos(tintColor = context.getColor(R.color.disabled_text))
		walletBinding.transactionsView.setTransactions(realStats)

		loadNextTransactionsPage(force = true)
	}

	private fun loadNextTransactionsPage(force: Boolean = false) {
		val walletId = when (selectedWallet) {
			is ElloRpc.UserWallet -> walletHelper.wallet?.id
			is ElloRpc.Earnings -> walletHelper.earningsWallet?.id
			else -> null
		} ?: return

		val limit = if (transactionsDataSource.getTransactionsList().isEmpty()) {
			WalletTransactionsDataSource.PAGE_LIMIT
		}
		else {
			if (force) {
				transactionsDataSource.getTransactionsList().size
			}
			else {
				WalletTransactionsDataSource.PAGE_LIMIT
			}
		}

		transactionsDataSource.getPayments(force = force, assetId = WalletHelper.DEFAULT_ASSET_ID, walletId = walletId, paymentType = WalletTransactionsDataSource.PaymentType.ALL, pageLimit = limit)
	}

	@Synchronized
	private fun reloadCards(wallet: ElloRpc.UserWallet?, earnings: ElloRpc.Earnings?) {
		val wallets = listOfNotNull(wallet, earnings)

		setupMediaPageIndicator(wallets)

		if (wallets.isNotEmpty()) {
			walletsAdapter.wallets = wallets
		}

		updateWalletButtonsVisibility(walletContainerViewBinding?.walletsViewPager?.currentItem ?: 0)
	}

	private fun setupMediaPageIndicator(wallets: List<Serializable>) {
		walletContainerViewBinding?.pageIndicatorContainer?.removeAllViews()

		val itemCount = wallets.size

		if (itemCount > 1) {
			val selectedIndex = wallets.indexOf(selectedWallet)

			for (i in 0 until itemCount) {
				val view = View(context)
				view.setBackgroundResource(R.drawable.page_indicator)
				view.tag = i

				if (i == selectedIndex) {
					view.isSelected = true
				}

				walletContainerViewBinding?.pageIndicatorContainer?.addView(view, LayoutHelper.createLinear(5, 5))
			}

			walletContainerViewBinding?.pageIndicatorContainer?.visible()
		}
		else {
			walletContainerViewBinding?.pageIndicatorContainer?.gone()
		}
	}

	override fun onPause() {
		super.onPause()
		walletHelper.removeListener(this)
	}

	override fun onResume() {
		super.onResume()
		walletHelper.addListener(this)
		walletHelper.loadWallet()
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		walletHelper.removeListener(this)

		selectedWallet = null

		walletContainerViewBinding?.walletsViewPager?.unregisterOnPageChangeCallback(onPageChangeCallback)
		walletContainerViewBinding = null

		emptyWalletContainerViewBinding = null

		noWalletContainerViewBinding = null

		transactionsDataSource.listener = null
	}

	override fun onWalletChanged(wallet: ElloRpc.UserWallet?, earnings: ElloRpc.Earnings?) {
		reloadState()
	}

	override fun onEarnStatsChanged(stats: List<ElloRpc.TransferStatsData>?) {
		reloadStats()
	}

	override fun onWalletStatsChanged(stats: List<ElloRpc.TransferStatsData>?) {
		reloadStats()
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun onTransactionsLoaded() {
		recyclerView?.adapter?.notifyDataSetChanged()
	}

	override fun onTransactionsLoadError(error: TLRPC.TL_error?) {
		val context = context ?: return
		val message = context.getString(R.string.failed_to_load_transactions_history_format, error?.text ?: context.getString(R.string.unknown_error_occurred))
		Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
	}

	companion object {
		private const val BUTTON_HELP = 1

		// These constants are used by child fragments
		const val ARG_MODE = "mode"
		const val PAYPAL = 0
		const val CARD = 1
		const val MY_BALANCE = 2
		const val BANK = 3
		const val ARG_AMOUNT = "amount"
		const val ARG_CURRENCY = "currency"
		const val ARG_WALLET_ID = "wallet_id"
		const val ARG_CARD_NUMBER = "card_number"
		const val ARG_CARD_EXPIRY_MONTH = "card_expiry_month"
		const val ARG_CARD_EXPIRY_YEAR = "card_expiry_year"
		const val ARG_CARD_CVC = "card_cvc"
		const val ARG_PAYPAL_LINK = "paypal_link"
		const val ARG_WALLET_PAYMENT_ID = "wallet_payment_id"
		const val ARG_VERIFICATION_CODE = "verification_code"
		const val ARG_IS_TOPUP = "is_topup"

		// const val ARG_TRANSFER_OUT_PAYMENT_ID = "transfer_out_payment_id"
		const val ARG_BANK_WITHDRAW_REQUISITES_ID = "bank_withdraw_requisites_id"
		const val ARG_PAYPAL_EMAIL = "email"

		const val WALLET_TYPE = "walletType"
	}
}
