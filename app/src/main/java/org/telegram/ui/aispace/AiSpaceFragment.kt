/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024-2025.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.aispace

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.AiPromptsCountLayountBinding
import org.telegram.messenger.databinding.FragmentAiSpaceBinding
import org.telegram.messenger.utils.formatCount
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.aispace.myaipacks.MyAiPacksFragment

class AiSpaceFragment : BaseFragment() {
	private var binding: FragmentAiSpaceBinding? = null
	private var aiPromptsCountBinding: AiPromptsCountLayountBinding? = null
	private var adapter: AiSpaceAdapter? = null
	private var categoriesAdapter: AiSpaceCategoriesAdapter? = null
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	private var loaderAnimationView: RLottieImageView? = null
	private val selectedCategories = mutableListOf<String>()
	private var searchAiBot: String? = null

	override fun createView(context: Context): View? {
		loaderAnimationView = RLottieImageView(context)

		initActionBar(context)
		initViewBinding(context)
		initCategoriesAdapter(context)
		getAllCategories()
		initAdapter(context)
		initListeners()
		initLoader()

		return binding?.root
	}

	private fun initActionBar(context: Context) {
		aiPromptsCountBinding = AiPromptsCountLayountBinding.inflate(LayoutInflater.from(context))

		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.ai_space))
		actionBar?.castShadows = true

		val layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
			gravity = Gravity.END or Gravity.BOTTOM
			bottomMargin = AndroidUtilities.dp(12f)
			marginEnd = AndroidUtilities.dp(4f)
		}
		aiPromptsCountBinding?.root?.layoutParams = layoutParams

		actionBar?.addView(aiPromptsCountBinding?.root)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> finishFragment()
				}
			}
		})
	}

	private fun initViewBinding(context: Context) {
		binding = FragmentAiSpaceBinding.inflate(LayoutInflater.from(context))
		fragmentView = binding?.root
	}

	private fun initAdapter(context: Context) {
		adapter = AiSpaceAdapter()

		binding?.botList?.layoutManager = LinearLayoutManager(context)
		binding?.botList?.adapter = adapter
	}

	private fun initCategoriesAdapter(context: Context) {
		categoriesAdapter = AiSpaceCategoriesAdapter(context)

		binding?.categoriesList?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
		binding?.categoriesList?.adapter = categoriesAdapter
	}

	private fun initListeners() {
		binding?.myAiPackButton?.setOnClickListener {
			presentFragment(MyAiPacksFragment())
		}

		adapter?.setOnClickListener {
			loadBotUser(it) { openBot(it) }
		}

		categoriesAdapter?.setOnClickListener {
			when (it) {
				context?.getString(R.string.all) -> selectedCategories.clear()
				else -> if (!selectedCategories.contains(it)) selectedCategories.add(it) else selectedCategories.remove(it)
			}
			getBotsByParams(selectedCategories, searchAiBot)
		}

		binding?.search?.doAfterTextChanged {
			searchAiBot = it.toString().lowercase()
			getBotsByParams(selectedCategories, searchAiBot)
		}

		binding?.botList?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				super.onScrolled(recyclerView, dx, dy)

				val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
				val totalItemCount = layoutManager.itemCount
				val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

				if (dy > 0 && lastVisibleItem + 1 >= totalItemCount) {
					if ((adapter?.paginatedList?.size ?: 0) < (adapter?.botList?.size ?: 0)) {
						adapter?.loadNextPage()
						recyclerView.postDelayed({
							adapter?.notifyItemRangeInserted(adapter?.paginatedList?.size ?: (0 - adapter?.pageSize!!), adapter?.pageSize ?: 0)
						}, 1000)
					}
				}
			}
		})
	}

	private fun initLoader() {
		loaderAnimationView?.setAutoRepeat(true)
		loaderAnimationView?.setAnimation(R.raw.ello_loader, 50, 50)

		binding?.loader?.addView(loaderAnimationView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
	}

	private fun getSubscriptionsChatBotRequest() {
		ioScope.launch {
			val req = ElloRpc.getSubscriptionsChatBotRequest(0L)
			val response = connectionsManager.performRequest(req)
			val error = response as? TLRPC.TLError

			withContext(mainScope.coroutineContext) {
				if (error == null) {
					if (response is TLRPC.TLBizDataRaw) {
						val res = response.readData<ElloRpc.SubscriptionInfoAiBot>()

						aiPromptsCountBinding?.textPromptCounter?.text = res?.textTotal.formatCount()
						aiPromptsCountBinding?.imagePromptCounter?.text = res?.imgTotal.formatCount()
					}
				}
			}
		}
	}

	private fun getAllBotRequest() {
		playAnimation()

		ioScope.launch {
			val req = ElloRpc.getAllBots()
			val response = connectionsManager.performRequest(req)
			val error = response as? TLRPC.TLError

			withContext(mainScope.coroutineContext) {
				if (error == null) {
					if (response is TLRPC.TLBizDataRaw) {
						val res = response.readData<ElloRpc.AllBotsResponse>()
						val bots = res?.bots

						stopAnimation()

						if (bots != null) {
							adapter?.updateBotList(bots)
							loadUsersInBatches(bots)
						}
					}
				}
			}
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	private fun loadUsersInBatches(bots: List<ElloRpc.AiSpaceBotsInfo>) {
		ioScope.launch {
			val batchSize = 10
			val botBatches = bots.chunked(batchSize)

			for (batch in botBatches) {
				batch.map { bot ->
					async {
						if (messagesController.getUser(bot.botId) == null) {
							messagesController.loadUser(bot.botId ?: 0L, classGuid, true)
							withContext(mainScope.coroutineContext) { adapter?.notifyDataSetChanged() }
						}
					}
				}.awaitAll()
			}
		}
	}

	private fun getAllCategories() {
		ioScope.launch {
			val req = ElloRpc.getAiSpaceCategories()
			val response = connectionsManager.performRequest(req)
			val error = response as? TLRPC.TLError

			withContext(mainScope.coroutineContext) {
				if (error == null) {
					if (response is TLRPC.TLBizDataRaw) {
						val res = response.readData<ElloRpc.AiSpaceCategoriesResponse>()
						res?.categories?.let { categoriesAdapter?.submitList(listOf(context?.getString(R.string.all) ?: "") + it) }
					}
				}
			}
		}
	}

	private fun getBotsByParams(categories: List<String>, search: String? = null) {
		ioScope.launch {
			val req = ElloRpc.getAllBots(categories = categories, search = search)
			val response = connectionsManager.performRequest(req)
			val error = response as? TLRPC.TLError

			withContext(mainScope.coroutineContext) {
				if (error == null) {
					if (response is TLRPC.TLBizDataRaw) {
						val res = response.readData<ElloRpc.AllBotsResponse>()
						res?.bots?.let { adapter?.updateBotList(it) }
					}
				}
			}
		}
	}

	private fun openBot(botId: Long) {
		val args = Bundle()
		args.putInt("dialog_folder_id", 0)
		args.putInt("dialog_filter_id", 0)
		args.putLong("user_id", botId)

		if (!messagesController.checkCanOpenChat(args, this)) {
			return
		}

		presentFragment(ChatActivity(args))
	}

	private fun loadBotUser(botId: Long, onBotReady: () -> Unit) {
		if (messagesController.getUser(botId) != null) {
			onBotReady()
		}
		else {
			ioScope.launch {
				try {
					val user = withTimeout(3_000) {
						messagesController.loadUser(botId, classGuid, true)
					}

					withContext(mainScope.coroutineContext) {
						if (user != null) {
							onBotReady()
						}
						else {
							Toast.makeText(context, R.string.error_bot_not_found, Toast.LENGTH_SHORT).show()
						}
					}
				}
				catch (e: TimeoutCancellationException) {
					withContext(mainScope.coroutineContext) {
						Toast.makeText(context, R.string.error_timeout, Toast.LENGTH_SHORT).show()
					}
				}
			}
		}
	}

	fun playAnimation() {
		loaderAnimationView?.playAnimation()
		loaderAnimationView?.visible()
	}

	fun stopAnimation() {
		loaderAnimationView?.stopAnimation()
		loaderAnimationView?.gone()
	}

	override fun onResume() {
		super.onResume()
		getSubscriptionsChatBotRequest()
		getAllBotRequest()
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
