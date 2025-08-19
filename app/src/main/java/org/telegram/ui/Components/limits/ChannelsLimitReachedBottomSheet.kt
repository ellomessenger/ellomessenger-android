/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024-2025.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Components.limits

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.databinding.BottomSheetChannelsLimitReachedBinding
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TLError
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.Components.limits.adapter.ChannelsLimitReachedAdapter
import org.telegram.ui.ProfileActivity

class ChannelsLimitReachedBottomSheet(private val parentFragment: BaseFragment?, needFocus: Boolean, currentAccount: Int) : BottomSheet(parentFragment?.context, needFocus) {
	private var binding: BottomSheetChannelsLimitReachedBinding? = null
	private var adapter: ChannelsLimitReachedAdapter? = null
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = MainScope()

	init {
		val inflater = LayoutInflater.from(context)
		binding = BottomSheetChannelsLimitReachedBinding.inflate(inflater)

		setBackgroundColor(context.getColor(R.color.light_background))

		setCustomView(binding?.root)

		binding?.limit?.text = MessagesController.getInstance(currentAccount).channelsLimitDefault.toString()

		initAdapter()
		initListeners()
		loadData()
	}

	private fun initListeners() {
		binding?.closeButton?.setOnClickListener {
			dismiss()
		}

		binding?.requestLimitIncreaseButton?.setOnClickListener {
			openEmailApp()
		}

		adapter?.setOnClickListener {
			val args = Bundle()
			args.putLong("chat_id", it.id)

			parentFragment?.presentFragment(ProfileActivity(args))
		}
	}

	private fun initAdapter() {
		adapter = ChannelsLimitReachedAdapter()
		binding?.recyclerView?.layoutManager = LinearLayoutManager(context)
		binding?.recyclerView?.adapter = adapter
	}

	private fun loadData() {
		val req = TLRPC.TLChannelsGetAdminedPublicChannels()

		ioScope.launch {
			val response = ConnectionsManager.getInstance(currentAccount).performRequest(req)
			val error = response as? TLError

			if (response != null && error == null) {
				val res = response as TLRPC.TLMessagesChats
				mainScope.launch { adapter?.submitList(res.chats) }
			}
		}
	}

	private fun openEmailApp() {
		val email = context.getString(R.string.info_ello_mail)
		val intent = Intent(Intent.ACTION_SENDTO).apply {
			data = "mailto:".toUri()
			putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
		}

		if (intent.resolveActivity(context.packageManager) != null) {
			context.startActivity(intent)
		}
		else {
			val alternativeIntent = Intent(Intent.ACTION_SEND).apply {
				type = "text/plain"
				putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
			}
			context.startActivity(Intent.createChooser(alternativeIntent, context.getString(R.string.select_your_email_app)))
		}
	}

	override fun dismiss() {
		super.dismiss()
		if (ioScope.isActive) {
			ioScope.cancel()
		}
		if (mainScope.isActive) {
			mainScope.cancel()
		}
	}

}