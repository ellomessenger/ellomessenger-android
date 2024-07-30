/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.aispace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentAiSpaceBinding
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ChatActivity
import org.telegram.ui.aibot.AiSubscriptionPlansFragment

class AiSpaceFragment : BaseFragment() {

	private var binding: FragmentAiSpaceBinding? = null

	private var adapter: AiSpaceAdapter? = null

	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = MainScope()

	private var contactUsItem: ActionBarMenuItem? = null

	override fun createView(context: Context): View? {
		initActionBar(context)
		initViewBinding(context)
		initAdapter(context)
		initListeners()

		getAllBotRequest()

		return binding?.root
	}

	private fun initActionBar(context: Context) {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.ai_space))
		actionBar?.castShadows = true

//		val menu = actionBar?.createMenu()
//		contactUsItem = menu?.addItem(CONTACT_US, context.getString(R.string.contact_us))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when(id) {
					ActionBar.BACK_BUTTON -> finishFragment()
//					CONTACT_US -> contactUs(context)
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

	private fun initListeners() {
		binding?.buyAiPack?.setOnClickListener {
			presentFragment(AiSubscriptionPlansFragment())
		}

		adapter?.setOnClickListener {
			loadBotUser(it) { openBot(it) }
		}
	}

	private fun getSubscriptionsChatBotRequest() {
		val req = ElloRpc.getSubscriptionsChatBotRequest()

		ioScope.launch {
			val response = connectionsManager.performRequest(req)
			val error = response as? TLRPC.TL_error

			withContext(mainScope.coroutineContext) {
				if (error == null) {
					if (response is TLRPC.TL_biz_dataRaw) {
						val res = response.readData<ElloRpc.SubscriptionInfoAiBot>()

						binding?.totalAiChat?.text = res?.textTotal.toString()
						binding?.totalAiPhoto?.text = res?.imgTotal.toString()
					}
				}
			}
		}
	}

	private fun getAllBotRequest() {
		val req = ElloRpc.getAllBots()

		ioScope.launch {
			val response = connectionsManager.performRequest(req)
			val error = response as? TLRPC.TL_error

			withContext(mainScope.coroutineContext) {
				if (error == null) {
					if (response is TLRPC.TL_biz_dataRaw) {
						val res = response.readData<ElloRpc.AllBotsResponse>()

						res?.botIds?.let { adapter?.setBotList(it) }
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

	private fun contactUs(context: Context) {
		val emailIntent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse(context.getString(R.string.contact_us_uri))
		}

		if (emailIntent.resolveActivity(context.packageManager) != null) {
			context.startActivity(emailIntent)
		} else {
			Toast.makeText(context, context.getString(R.string.error_no_suitable_mail_app), Toast.LENGTH_SHORT).show()
		}
	}

	override fun onResume() {
		super.onResume()
		getSubscriptionsChatBotRequest()
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
		const val CONTACT_US = 0
	}

}