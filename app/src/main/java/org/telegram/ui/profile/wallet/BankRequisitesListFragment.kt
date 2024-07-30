/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.profile.wallet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import com.google.android.material.checkbox.MaterialCheckBox
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.getCacheDir
import org.telegram.messenger.FileLog
import org.telegram.messenger.R
import org.telegram.messenger.databinding.BankRequisitesCellViewBinding
import org.telegram.messenger.databinding.BankRequisitesListFragmentBinding
import org.telegram.messenger.utils.fromJson
import org.telegram.messenger.utils.toJson
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class BankRequisitesListFragment(args: Bundle) : BaseFragment(args) {
	private var binding: BankRequisitesListFragmentBinding? = null
	private var amount: Float = 0f
	private var walletId: Long = 0L
	private var selectedRequisitesId = 0L
	private var connecting = false
	private var reqId = 0
	private var requisite: ElloRpc.BankRequisite? = null

	override fun onFragmentCreate(): Boolean {
		amount = arguments?.getFloat(WalletFragment.ARG_AMOUNT, 0f) ?: return false
		walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID, 0L) ?: return false

		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.bank_requisites))
		actionBar?.castShadows = true

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					if (!connecting) {
						finishFragment()
					}
				}
			}
		})

		binding = BankRequisitesListFragmentBinding.inflate(LayoutInflater.from(context))

		binding?.transferOutButton?.setOnClickListener {
			val args = Bundle()

			args.putSerializable(EditBankRequisitesFragment.ARG_REQUISITE, requisite)
			args.putFloat(WalletFragment.ARG_AMOUNT, amount)
			args.putLong(WalletFragment.ARG_WALLET_ID, walletId)

			presentFragment(TransferOutResultFragment(args))
		}

		binding?.newMethodButton?.setOnClickListener {
			val args = Bundle()

			args.putFloat(WalletFragment.ARG_AMOUNT, amount)
			args.putLong(WalletFragment.ARG_WALLET_ID, walletId)

			presentFragment(EditBankRequisitesFragment(args))
		}

		fragmentView = binding?.root

		return binding?.root
	}

	override fun onResume() {
		super.onResume()
		loadCache()
		loadRemote()
	}

	private fun loadCache() {
		val file = File(getCacheDir(), CACHE_FILE_NAME)

		if (!file.exists()) {
			return
		}

		try {
			FileInputStream(file).use { fis ->
				ObjectInputStream(fis).use {
					val json = it.readObject() as? String ?: return
					val list = json.fromJson<List<ElloRpc.BankRequisite>>()

					if (list != null) {
						reloadList(list)
					}
				}
			}
		}
		catch (e: Throwable) {
			FileLog.w("Failed to load bank requisites cache: $e")
		}
	}

	private fun saveCacheToJson(requisites: List<ElloRpc.BankRequisite>) {
		val json = requisites.toJson() ?: return

		try {
			FileOutputStream(File(getCacheDir(), CACHE_FILE_NAME)).use { fos ->
				ObjectOutputStream(fos).use {
					it.writeObject(json)
				}
			}
		}
		catch (e: Throwable) {
			FileLog.w("Failed to save bank requisites cache: $e")
		}
	}

	private fun loadRemote() {
		val req = ElloRpc.getBankWithdrawsRequisites()

		reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, _ ->
			if (response is TLRPC.TL_biz_dataRaw) {
				val list = response.readData<ElloRpc.BankRequisiteResponse>()?.data

				if (list != null) {
					AndroidUtilities.runOnUIThread {
						reloadList(list)
					}

					saveCacheToJson(list)
				}
			}

			reqId = 0
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun reloadList(requisites: List<ElloRpc.BankRequisite>) {
		val container = binding?.bankRequisitesContainer ?: return
		container.removeAllViews()

		for (requisite in requisites) {
			val cell = BankRequisitesCellViewBinding.inflate(LayoutInflater.from(context), container, false)

			cell.root.tag = requisite

			cell.root.setOnClickListener {
				selectedRequisitesId = requisite.requisitesId

				container.children.forEach {
					val r = it.tag as? ElloRpc.BankRequisite ?: return@forEach
					it.findViewById<MaterialCheckBox>(R.id.radio_button)?.isChecked = (r.requisitesId == requisite.requisitesId)

					this.requisite = requisite
				}

				if (selectedRequisitesId > 0) {
					binding?.transferOutButton?.isEnabled = true
				}
			}

			val address = "${requisite.addressInfo?.city} St. ${requisite.addressInfo?.street}, ${requisite.addressInfo?.state} ${requisite.addressInfo?.postalCode}"

			cell.nameLabel.text = listOfNotNull(requisite.personInfo?.firstName, requisite.personInfo?.lastName).joinToString(" ")
			cell.bankNameLabel.text = requisite.bankInfo?.name
			cell.addressLabel.text = address

			cell.editButton.setOnClickListener {
				val args = Bundle()
				args.putSerializable(EditBankRequisitesFragment.ARG_REQUISITE, requisite)
				args.putFloat(WalletFragment.ARG_AMOUNT, amount)
				args.putBoolean(EditBankRequisitesFragment.EDIT_REQUISITES, true)

				presentFragment(EditBankRequisitesFragment(args))
			}

			container.addView(cell.root)

			cell.root.updateLayoutParams<LinearLayout.LayoutParams> {
				topMargin = AndroidUtilities.dp(17f)
			}
		}
	}

	override fun canBeginSlide(): Boolean {
		return !connecting
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		binding = null

		if (reqId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, false)
			reqId = 0
		}

		connecting = false
	}

	companion object {
		private const val CACHE_FILE_NAME = "req.json"
	}
}
