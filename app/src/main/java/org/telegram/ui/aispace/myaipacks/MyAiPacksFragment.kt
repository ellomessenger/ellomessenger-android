package org.telegram.ui.aispace.myaipacks

import android.content.Context
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
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.TL_error
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.aibot.AiSubscriptionPlansFragment

class MyAiPacksFragment : BaseFragment() {

	private var binding: FragmentMyAiPacksBinding? = null

	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = MainScope()

	override fun createView(context: Context): View? {
		initActionBar(context)
		initViewBinding(context)

		binding?.buyAiPack?.setOnClickListener {
			presentFragment(AiSubscriptionPlansFragment())
		}

		binding?.back?.background = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(34f), 0, 0x28ffffff)
		binding?.back?.setOnClickListener {
			finishFragment()
		}

		return binding?.root
	}

	private fun initActionBar(context: Context) {
		actionBar?.setAddToContainer(false)
		actionBar?.background = null
		actionBar?.setItemsColor(-0x1, false)
	}

	private fun initViewBinding(context: Context) {
		binding = FragmentMyAiPacksBinding.inflate(LayoutInflater.from(context))
		fragmentView = binding?.root
	}

	private fun getSubscriptionsChatBotRequest() {
		val req = ElloRpc.getSubscriptionsChatBotRequest(0L)

		ioScope.launch {
			val response = connectionsManager.performRequest(req)
			val error = response as? TL_error

			withContext(mainScope.coroutineContext) {
				if (error == null) {
					if (response is TLRPC.TL_biz_dataRaw) {
						val res = response.readData<ElloRpc.SubscriptionInfoAiBot>()

						binding?.textPromptsCount?.text = res?.textTotal.toString()
						binding?.imagePromptsCount?.text = res?.imgTotal.toString()
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