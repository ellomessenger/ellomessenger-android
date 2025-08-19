/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.profile.wallet

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.TransactionsStatsContainerViewBinding
import org.telegram.messenger.databinding.WalletSearchHeaderBinding
import org.telegram.messenger.databinding.WalletTransactionDateHeaderBinding
import org.telegram.messenger.databinding.WalletTransactionViewBinding
import org.telegram.messenger.utils.getDimensionRaw
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.WalletHelper
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import java.io.Serializable

class TransactionsHistoryFragment(args: Bundle) : BaseFragment(args), WalletTransactionsDataSource.WalletTransactionsListener, TransactionsHistoryStatsDataSource.Listener {
	private var recyclerView: RecyclerView? = null
	private val transactionsDataSource = WalletTransactionsDataSource().also { it.listener = this }
	private var transactionsStatsContainerViewBinding: TransactionsStatsContainerViewBinding? = null
	private val searchHeaderHeight by lazy { context!!.resources.getDimensionRaw(R.dimen.common_size_30dp) }
	private val searchHeaderView by lazy { WalletSearchHeaderBinding.inflate(LayoutInflater.from(context)) }
	private val searchHeaderViewFake by lazy { WalletSearchHeaderBinding.inflate(LayoutInflater.from(context)) }
	private var walletId = 0L
	private val transactionsAdapter = TransactionsAdapter()
	private val historyDataSource by lazy { TransactionsHistoryStatsDataSource(walletId) }
	private var period = TransactionsHistoryStatsDataSource.Period.WEEK
	private var walletType: String? = null

	private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
		override fun onPageSelected(position: Int) {
			val stats = transactionsAdapter.stats?.getOrNull(position)

			if (stats == null) {
				transactionsStatsContainerViewBinding?.periodLabel?.text = null
				return
			}

			transactionsStatsContainerViewBinding?.periodLabel?.text = stats.period
		}
	}

	override fun onFragmentCreate(): Boolean {
		walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID) ?: return false
		historyDataSource.listener = this
		walletType = arguments?.getString(WalletFragment.WALLET_TYPE, null)
		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setTitle(context.getString(R.string.fin_activity))
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

		transactionsStatsContainerViewBinding = TransactionsStatsContainerViewBinding.inflate(LayoutInflater.from(context), null, false)

		transactionsStatsContainerViewBinding?.modeSegmentControl?.addOnButtonCheckedListener { _, checkedId, isChecked ->
			if (!isChecked) {
				return@addOnButtonCheckedListener
			}

			period = when (checkedId) {
				R.id.segment_button_week -> TransactionsHistoryStatsDataSource.Period.WEEK
				R.id.segment_button_month -> TransactionsHistoryStatsDataSource.Period.MONTH
				R.id.segment_button_year -> TransactionsHistoryStatsDataSource.Period.YEAR
				else -> return@addOnButtonCheckedListener
			}

			transactionsAdapter.setStats(null, period)

			transactionsStatsContainerViewBinding?.periodLabel?.text = null

			historyDataSource.load(period = period, type = TransactionsHistoryStatsDataSource.Type.ALL, forced = true)
		}

		transactionsStatsContainerViewBinding?.walletsViewPager?.let {
			it.adapter = transactionsAdapter
			it.offscreenPageLimit = 2
			it.setPageTransformer(MarginPageTransformer(AndroidUtilities.dp(20f)))
			it.registerOnPageChangeCallback(onPageChangeCallback)
			(it.children.find { child -> child is RecyclerView } as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER
		}

		recyclerView = RecyclerView(context)
		recyclerView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
		recyclerView?.itemAnimator = null

		recyclerView?.setOnScrollChangeListener(object : View.OnScrollChangeListener {
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
									args.putString(WalletFragment.WALLET_TYPE, walletType)
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

						val view = transactionsStatsContainerViewBinding?.root ?: return

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

		loadNextTransactionsPage(force = true)

		historyDataSource.load(period = period, type = TransactionsHistoryStatsDataSource.Type.ALL, forced = true)

		return fragmentView
	}

	private fun checkSearchHeaderLocation(offset: Int) {
		if (offset <= -(transactionsStatsContainerViewBinding?.root?.height ?: 0)) {
			searchHeaderView.root.visible()
		}
		else {
			searchHeaderView.root.gone()
		}
	}

	private fun loadNextTransactionsPage(force: Boolean = false) {
		transactionsDataSource.getPayments(force = force, assetId = WalletHelper.DEFAULT_ASSET_ID, walletId = walletId, paymentType = WalletTransactionsDataSource.PaymentType.ALL)
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun onTransactionsLoaded() {
		recyclerView?.adapter?.notifyDataSetChanged()
	}

	override fun onTransactionsLoadError(error: TLRPC.TLError?) {
		val context = context ?: return
		val message = context.getString(R.string.failed_to_load_transactions_history_format, error?.text ?: context.getString(R.string.unknown_error_occurred))
		Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun onStatsLoaded() {
		transactionsAdapter.setStats(stats = historyDataSource.stats, period = period)

		if (historyDataSource.isFirstPage) {
			transactionsStatsContainerViewBinding?.walletsViewPager?.setCurrentItem(historyDataSource.stats.size - 1, false)
		}
	}

	override fun onStatsLoadFailed(message: String?) {
		val context = context ?: return
		val msg = context.getString(R.string.failed_to_load_transactions_history_format, message ?: context.getString(R.string.unknown_error_occurred))
		Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		transactionsStatsContainerViewBinding?.walletsViewPager?.unregisterOnPageChangeCallback(onPageChangeCallback)
		transactionsStatsContainerViewBinding = null

		historyDataSource.listener = null

		transactionsDataSource.listener = null
	}
}
