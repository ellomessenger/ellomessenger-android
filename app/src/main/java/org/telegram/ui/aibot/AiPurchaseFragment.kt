/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.aibot

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentAiPurchaseBinding
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment

class AiPurchaseFragment(args: Bundle) : BaseFragment(args) {
	private var binding: FragmentAiPurchaseBinding? = null
	private var priceInfo: ElloRpc.PriceInfoResponse? = null
	private var subsInfo: ElloRpc.SubscriptionInfoAiBot? = null
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = MainScope()

	override fun onFragmentCreate(): Boolean {
		subsInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			arguments?.getSerializable("subs_info", ElloRpc.SubscriptionInfoAiBot::class.java)
		}
		else {
			@Suppress("DEPRECATION") arguments?.getSerializable("subs_info") as? ElloRpc.SubscriptionInfoAiBot
		}

		return super.onFragmentCreate()
	}

	override fun createView(context: Context): View? {
		initActionBar(context)
		initViewBinding(context)
		//MARK: uncomment to show active status
//		checkSubsStatus()
		initListeners()

		return binding?.root
	}

	private fun initActionBar(context: Context) {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.ai_buy_fragment_title))
		actionBar?.castShadows = false

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> finishFragment()
				}
			}
		})
	}

	private fun initViewBinding(context: Context) {
		binding = FragmentAiPurchaseBinding.inflate(LayoutInflater.from(context))
		fragmentView = binding?.root
	}

	private fun checkSubsStatus() {
		subsInfo?.let {
			if (!it.textSubExpired) {
				binding?.aiChatActive?.visible()
			}
			if (!it.imgSubExpired) {
				binding?.aiImagesActive?.visible()
			}
			if (!it.doubleSubExpired) {
				binding?.aiChatImagesActive?.visible()
			}
		}
	}

	private fun getPriceInfo() {
		val req = ElloRpc.getPriceInfo()

		ioScope.launch {
			val response = connectionsManager.performRequest(req)
			val error = response as? TLRPC.TLError

			withContext(mainScope.coroutineContext) {
				if (error == null) {
					if (response is TLRPC.TLBizDataRaw) {
						val res = response.readData<ElloRpc.PriceInfoResponse>()
						priceInfo = res
					}
				}
			}
		}
	}

	private fun initListeners() {
		binding?.btBuyAiChat?.setOnClickListener {
			val args = Bundle()
			args.putInt("ai_purchase", VIEW_TEXT)
			args.putSerializable("price_info", priceInfo)
			args.putSerializable("subs_info", subsInfo)
			presentFragment(AiSubscriptionPlansFragment(args))
		}

		binding?.btBuyAiImages?.setOnClickListener {
			val args = Bundle()
			args.putInt("ai_purchase", VIEW_IMAGE)
			args.putSerializable("price_info", priceInfo)
			args.putSerializable("subs_info", subsInfo)
			presentFragment(AiSubscriptionPlansFragment(args))
		}

		binding?.btBuyAiChatAndImages?.setOnClickListener {
			val args = Bundle()
			args.putInt("ai_purchase", VIEW_IMAGE_TEXT)
			args.putSerializable("price_info", priceInfo)
			args.putSerializable("subs_info", subsInfo)
			presentFragment(AiSubscriptionPlansFragment(args))
		}
	}

	override fun onResume() {
		super.onResume()

		getPriceInfo()
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}
	}

	companion object {
		private const val VIEW_TEXT = 0
		private const val VIEW_IMAGE = 1
		private const val VIEW_IMAGE_TEXT = 2
	}

}
