/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.aispace.myaipacks

import android.content.Context
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
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.databinding.FragmentMyAiPacksBinding
import org.telegram.messenger.utils.formatCount
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TLError
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.aibot.AiPurchaseFragment

class MyAiPacksFragment : BaseFragment() {
	private var binding: FragmentMyAiPacksBinding? = null
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = MainScope()
	private var subsInfo: ElloRpc.SubscriptionInfoAiBot? = null

	override fun createView(context: Context): View? {
		initActionBar()
		initViewBinding(context)

		binding?.buyAiPack?.setOnClickListener {
			val args = Bundle()
			args.putSerializable("subs_info", subsInfo)
			presentFragment(AiPurchaseFragment(args))
		}

		binding?.back?.background = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(34f), 0, 0x28ffffff)

		binding?.back?.setOnClickListener {
			finishFragment()
		}

		return binding?.root
	}

	private fun initActionBar() {
		actionBar?.setAddToContainer(false)
		actionBar?.background = null
		actionBar?.setItemsColor(-0x1, false)
	}

	private fun initViewBinding(context: Context) {
		binding = FragmentMyAiPacksBinding.inflate(LayoutInflater.from(context))
		fragmentView = binding?.root
	}

	private fun getSubscriptionsChatBotRequest() {
		ioScope.launch {
			val req = ElloRpc.getSubscriptionsChatBotRequest(0L)
			val response = connectionsManager.performRequest(req)
			val error = response as? TLError

			withContext(mainScope.coroutineContext) {
				if (error == null) {
					if (response is TLRPC.TLBizDataRaw) {
						val res = response.readData<ElloRpc.SubscriptionInfoAiBot>()
						subsInfo = res

						binding?.textPromptsCount?.text = res?.textTotal.formatCount()
						binding?.imagePromptsCount?.text = res?.imgTotal.formatCount()
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		getSubscriptionsChatBotRequest()
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		binding = null

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}
	}
}
