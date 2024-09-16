/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.profile.referral

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.beint.elloapp.allCornersProvider
import com.beint.elloapp.topCornersProvider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.R
import org.telegram.messenger.UserObject
import org.telegram.messenger.databinding.ReferralProgressFragmentBinding
import org.telegram.messenger.databinding.ReferralProgressUserBinding
import org.telegram.messenger.utils.fillElloCoinLogos
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC.TL_biz_dataRaw
import org.telegram.tgnet.TLRPC.TL_error
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView

class ReferralProgressFragment(args: Bundle? = null) : BaseFragment(args) {
	private var referralProgressFragmentBinding: ReferralProgressFragmentBinding? = null
	private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var referralsRecyclerView: RecyclerView? = null
	private val referralsAdapter by lazy { ReferralsAdapter() }
	private var viewPager: ViewPager2? = null
	private var tabLayoutMediator: TabLayoutMediator? = null
	private var loyaltyCode: String? = null
	private var linearLayout: LinearLayout? = null
	private var isLastPage = false
	private var currentPage = 1L // please ask server guys why
	private var loadingReferralsJob: Job? = null

	private val referralLink: String?
		get() {
			if (loyaltyCode.isNullOrEmpty()) {
				return null
			}

			val context = context ?: return null

			return "https://${context.getString(R.string.domain)}/invite_referral?code=$loyaltyCode"
		}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.referral_program))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (parentActivity == null) {
					return
				}

				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		referralProgressFragmentBinding = ReferralProgressFragmentBinding.inflate(LayoutInflater.from(context))

		referralProgressFragmentBinding?.cardBackground?.outlineProvider = allCornersProvider(AndroidUtilities.dp(12f).toFloat())
		referralProgressFragmentBinding?.inviteBackgroundHeader?.outlineProvider = topCornersProvider(AndroidUtilities.dp(12f).toFloat())
		referralProgressFragmentBinding?.referralCodeLabel?.text = context.getString(R.string.Loading)

		referralProgressFragmentBinding?.amountLabel?.text = context.getString(R.string.simple_coin_format, 0.0).fillElloCoinLogos(size = 30f, tintColor = context.getColor(R.color.white))
		referralProgressFragmentBinding?.referralsCounterLabel?.text = context.getString(R.string.referrals_count_format, 0)

		referralProgressFragmentBinding?.copyLinkButton?.setOnClickListener {
			val link = referralLink ?: return@setOnClickListener
			AndroidUtilities.addToClipboard(link)
			Toast.makeText(it.context, R.string.referral_link_copied, Toast.LENGTH_SHORT).show()
		}

		referralProgressFragmentBinding?.copyCodeButton?.setOnClickListener {
			val code = loyaltyCode ?: return@setOnClickListener
			AndroidUtilities.addToClipboard(code)
			Toast.makeText(it.context, R.string.referral_code_copied, Toast.LENGTH_SHORT).show()
		}

		referralProgressFragmentBinding?.shareButton?.setOnClickListener {
			val link = referralLink ?: return@setOnClickListener

			val intent = Intent(Intent.ACTION_SEND)
			intent.putExtra(Intent.EXTRA_TEXT, link)
			intent.type = "text/plain"

			val shareIntent = Intent.createChooser(intent, it.context.getString(R.string.share_referral_link))

			runCatching {
				parentActivity?.startActivity(shareIntent)
			}
		}

		linearLayout = LinearLayout(context)
		linearLayout?.orientation = LinearLayout.VERTICAL

		fragmentView = linearLayout

		loadData()

		return fragmentView
	}

	private fun createPagerAdapter(): RecyclerView.Adapter<RecyclerView.ViewHolder> {
		return object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			private val viewTypeProgress = 0
			private val viewTypeList = 1

			override fun getItemCount(): Int {
				return 2
			}

			override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
				// unused
			}

			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
				return when (viewType) {
					viewTypeProgress -> {
						(referralProgressFragmentBinding?.root?.parent as? ViewGroup)?.removeView(referralProgressFragmentBinding?.root)
						referralProgressFragmentBinding?.root?.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT)
						object : RecyclerView.ViewHolder(referralProgressFragmentBinding!!.root) {}
					}

					viewTypeList -> {
						(referralsRecyclerView?.parent as? ViewGroup)?.removeView(referralsRecyclerView)
						referralsRecyclerView?.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT)
						object : RecyclerView.ViewHolder(referralsRecyclerView!!) {}
					}

					else -> {
						throw RuntimeException("Unsupported section")
					}
				}
			}

			override fun getItemViewType(position: Int): Int {
				return when (position) {
					0 -> viewTypeProgress
					1 -> viewTypeList
					else -> throw RuntimeException("Unsupported section")
				}
			}
		}
	}

	private fun reloadData(loyaltyData: ElloRpc.LoyaltyData, usersCount: Long, sum: Float) {
		val context = context ?: return
		val isPartnerMode = loyaltyData.isBusiness

		linearLayout?.removeAllViews()

		if (isPartnerMode) {
			actionBar?.castShadows = false

			referralsAdapter.totalIncome = sum

			referralsRecyclerView = RecyclerView(context)
			referralsRecyclerView?.layoutManager = LinearLayoutManager(context).apply { orientation = LinearLayoutManager.VERTICAL }
			referralsRecyclerView?.adapter = referralsAdapter

			val tabLayout = TabLayout(context)
			tabLayout.id = View.generateViewId()

			ViewCompat.setElevation(tabLayout, AndroidUtilities.dp(4f).toFloat())

			viewPager = ViewPager2(context)
			viewPager?.adapter = createPagerAdapter()

			tabLayoutMediator = TabLayoutMediator(tabLayout, viewPager!!) { tab, position ->
				when (position) {
					0 -> tab.text = context.getString(R.string.my_progress)
					1 -> tab.text = context.getString(R.string.my_referrals)
				}
			}

			tabLayoutMediator?.attach()

			linearLayout?.addView(tabLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
			linearLayout?.addView(viewPager, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))

			loadReferralUsers()
		}
		else {
			referralProgressFragmentBinding?.totalRevenue?.setText(R.string.earned_revenue)
			referralProgressFragmentBinding?.shareButton?.setText(R.string.share)
			linearLayout?.addView(referralProgressFragmentBinding?.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
		}

		referralProgressFragmentBinding?.amountLabel?.text = context.getString(R.string.simple_coin_format, sum).fillElloCoinLogos(size = 30f, tintColor = context.getColor(R.color.white))
		referralProgressFragmentBinding?.referralsCounterLabel?.text = context.getString(R.string.referrals_count_format, usersCount)
		referralProgressFragmentBinding?.referralNameLabel?.text = loyaltyData.name?.trim()
		referralProgressFragmentBinding?.referralDescriptionLabel?.text = loyaltyData.description?.trim()
	}

	private fun loadData() {
		val context = context ?: return

		val progressBar = ContentLoadingProgressBar(context)
		progressBar.isIndeterminate = true

		linearLayout?.addView(progressBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

		progressBar.show()

		ioScope.launch {
			val req = ElloRpc.getLoyaltyBonusDataWithSum()

			when (val res = connectionsManager.performRequest(req, ConnectionsManager.RequestFlagFailOnServerErrors)) {
				is TL_biz_dataRaw -> {
					val loyaltyDataWithSum = res.readData<ElloRpc.LoyaltyDataWithSum>()
					val loyaltyData = loyaltyDataWithSum?.loyaltyData

					if (loyaltyData == null) {
						withContext(mainScope.coroutineContext) {
							Toast.makeText(parentActivity, R.string.failed_to_load_loyalty_data, Toast.LENGTH_SHORT).show()
							finishFragment()
						}

						return@launch
					}

					val usersCount = loyaltyDataWithSum.usersCount
					val sum = loyaltyDataWithSum.sum

					withContext(mainScope.coroutineContext) {
						progressBar.hide()
						reloadData(loyaltyData, usersCount, sum)
					}
				}

				is TL_error -> {
					withContext(mainScope.coroutineContext) {
						Toast.makeText(parentActivity, res.text ?: parentActivity?.getString(R.string.failed_to_load_loyalty_data), Toast.LENGTH_SHORT).show()
						finishFragment()
					}
				}

				else -> {
					withContext(mainScope.coroutineContext) {
						Toast.makeText(parentActivity, parentActivity?.getString(R.string.failed_to_load_loyalty_data), Toast.LENGTH_SHORT).show()
						finishFragment()
					}
				}
			}
		}

		ioScope.launch {
			val req = ElloRpc.getLoyaltyCode()
			val res = connectionsManager.performRequest(req, ConnectionsManager.RequestFlagFailOnServerErrors)

			if (res is TL_biz_dataRaw) {
				val code = res.readData<ElloRpc.LoyaltyCode>()

				loyaltyCode = code?.code?.trim()

				withContext(mainScope.coroutineContext) {
					referralProgressFragmentBinding?.referralCodeLabel?.text = loyaltyCode
				}
			}
			else if (res is TL_error) {
				withContext(mainScope.coroutineContext) {
					Toast.makeText(context, res.text, Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	private fun loadReferralUsers() {
		if (loadingReferralsJob?.isActive == true) {
			return
		}

		loadingReferralsJob = ioScope.launch {
			val req = ElloRpc.getLoyaltyUsersRequest(currentPage, 10)
			val res = connectionsManager.performRequest(req, ConnectionsManager.RequestFlagFailOnServerErrors)

			if (res is TL_biz_dataRaw) {
				val users = res.readData<ElloRpc.LoyaltyUsersList>()
				val usersList = users?.users

				withContext(mainScope.coroutineContext) {
					referralsAdapter.setUsers(usersList, currentPage != 0L)

					if (referralsAdapter.usersCount.toLong() == users?.total) {
						isLastPage = true
					}
					else {
						isLastPage = false
						currentPage += 1
					}
				}
			}
			else if (res is TL_error) {
				withContext(mainScope.coroutineContext) {
					Toast.makeText(context, res.text, Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		referralProgressFragmentBinding = null

		if (loadingReferralsJob?.isActive == true) {
			loadingReferralsJob?.cancel()
		}

		loadingReferralsJob = null

		if (mainScope.isActive) {
			mainScope.cancel()
		}

		if (ioScope.isActive) {
			ioScope.cancel()
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	private inner class ReferralsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
		private var users: List<ElloRpc.LoyaltyUser>? = null

		val usersCount: Int
			get() = users?.size ?: 0

		var totalIncome = 0f
			set(value) {
				field = value
				notifyDataSetChanged()
			}

		fun setUsers(users: List<ElloRpc.LoyaltyUser>?, append: Boolean) {
			if (append) {
				val currentUsers = this.users?.toMutableList() ?: mutableListOf()
				currentUsers.addAll(users ?: return)
				this.users = currentUsers
			}
			else {
				this.users = users
			}

			notifyDataSetChanged()
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			when (viewType) {
				VIEW_TYPE_HEADER -> {
					val view = LayoutInflater.from(parent.context).inflate(R.layout.comission_from_referrals_view_holder, parent, false)
					view.findViewById<ImageView>(R.id.card_background)?.outlineProvider = allCornersProvider(AndroidUtilities.dp(12f).toFloat())
					return object : RecyclerView.ViewHolder(view) {}
				}

				VIEW_TYPE_HAND -> {
					val view = LayoutInflater.from(parent.context).inflate(R.layout.wallet_search_header, parent, false)
					return object : RecyclerView.ViewHolder(view) {}
				}

				VIEW_TYPE_USER -> {
					return ReferralProgressUserViewHolder(ReferralProgressUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))
				}

				VIEW_TYPE_EMPTY -> {
					return object : RecyclerView.ViewHolder(LinearLayout(parent.context).apply {
						orientation = LinearLayout.VERTICAL
						setVerticalGravity(Gravity.CENTER or Gravity.TOP)
						setHorizontalGravity(Gravity.CENTER or Gravity.TOP)

						layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()).apply {
							topMargin = AndroidUtilities.dp(100f)
						}

						val emptyImage = RLottieImageView(context)
						emptyImage.setAutoRepeat(true)
						emptyImage.setAnimation(R.raw.panda_chat_list_no_results, 160, 160)
						emptyImage.playAnimation()

						addView(emptyImage, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

						val textView = TextView(context)
						textView.gravity = Gravity.CENTER
						textView.text = context.getString(R.string.no_referrals_yet)
						textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.disabled_text, null))

						addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24f, 6f, 24f, 0f))
					}) {}
				}

				else -> {
					throw RuntimeException("Unsupported view type")
				}
			}
		}

		override fun getItemCount(): Int {
			return 2 + (users?.size ?: 1)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				VIEW_TYPE_HEADER -> {
					val amountLabel = holder.itemView.findViewById<TextView>(R.id.amount_label)
					amountLabel?.text = holder.itemView.context.getString(R.string.simple_coin_format, totalIncome).fillElloCoinLogos(size = 30f, tintColor = Color.WHITE)
				}

				VIEW_TYPE_USER -> {
					val user = users?.get(position - 2)
					(holder as? ReferralProgressUserViewHolder)?.user = user

					if (position == itemCount - 1 && !isLastPage) {
						loadReferralUsers()
					}
				}
			}
		}

		override fun getItemViewType(position: Int): Int {
			return when (position) {
				0 -> {
					VIEW_TYPE_HEADER
				}

				1 -> {
					VIEW_TYPE_HAND
				}

				else -> {
					if (users.isNullOrEmpty()) {
						VIEW_TYPE_EMPTY
					}
					else {
						VIEW_TYPE_USER
					}
				}
			}
		}
	}

	private class ReferralProgressUserViewHolder(private val binding: ReferralProgressUserBinding) : RecyclerView.ViewHolder(binding.root) {
		private val avatarDrawable = AvatarDrawable().apply { shouldDrawPlaceholder = true }
		private val avatarImageView = AvatarImageView(binding.root.context)

		var user: ElloRpc.LoyaltyUser? = null
			set(value) {
				field = value

				val context = binding.root.context

				val tlrpcUser = value?.user?.toTLRPCUser()

				avatarDrawable.setInfo(tlrpcUser)

				val photo = ImageLocation.getForUserOrChat(tlrpcUser, ImageLocation.TYPE_SMALL)

				if (photo != null) {
					avatarImageView.setImage(photo, null, avatarDrawable, tlrpcUser)
				}
				else {
					avatarImageView.setImage(null, null, avatarDrawable, tlrpcUser)
				}

				binding.nameLabel.text = UserObject.getUserName(tlrpcUser)

				binding.totalValueLabel.text = context.getString(R.string.simple_coin_format, value?.sum ?: 0f).fillElloCoinLogos(size = 13f, tintColor = context.getColor(R.color.text))
				binding.percentValueLabel.text = context.getString(R.string.simple_coin_format, value?.commission ?: 0f).fillElloCoinLogos(size = 13f, tintColor = context.getColor(R.color.text))
			}

		init {
			binding.avatarContainer.addView(avatarImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		}
	}

	companion object {
		private const val VIEW_TYPE_HEADER = 0
		private const val VIEW_TYPE_HAND = 1
		private const val VIEW_TYPE_USER = 2
		private const val VIEW_TYPE_EMPTY = 3
	}
}
