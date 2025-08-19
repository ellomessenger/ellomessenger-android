/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2025.
 */
package org.telegram.ui.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.EmailSentViewBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView

class EmailSentFragment(args: Bundle) : BaseFragment(args) {

	private var binding: EmailSentViewBinding? = null

	private var loaderAnimationView: RLottieImageView? = null

	private var isAccountDelete = true

	override fun onFragmentCreate(): Boolean {

		isAccountDelete = arguments?.getBoolean("is_delete_account") ?: true

		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(true)
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = EmailSentViewBinding.inflate(LayoutInflater.from(context))

		loaderAnimationView = RLottieImageView(context)

		binding?.openEmailButton?.setOnClickListener {
			val intent = Intent(Intent.ACTION_MAIN)
			intent.addCategory(Intent.CATEGORY_APP_EMAIL)
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

			try {
				context.startActivity(intent)
			} catch (e: ActivityNotFoundException) {
				Toast.makeText(context, context.getString(R.string.email_app_not_found), Toast.LENGTH_SHORT).show()
			}
		}

		if (isAccountDelete) {
			binding?.title?.text = context.getString(R.string.account_deletion_check_inbox)
			binding?.description?.text = context.getString(R.string.account_deletion_email_sent)
		}

		initLoader()

		fragmentView = binding?.root

		return binding?.root
	}

	private fun initLoader() {
		loaderAnimationView?.setAutoRepeat(true)
		loaderAnimationView?.setAnimation(R.raw.ello_loader, 50, 50)

		binding?.loader?.addView(loaderAnimationView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
	}

	private fun playAnimation() {
		loaderAnimationView?.playAnimation()
		loaderAnimationView?.visible()
	}

	fun deleteAccount(hash: String?) {
		if (hash.isNullOrEmpty()) {
			return
		}

		binding?.descriptionContainer?.gone()
		binding?.openEmailButton?.gone()

		binding?.loader?.visible()
		playAnimation()

		val req = ElloRpc.deleteMagicLinkRequest(hash)

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {

				if (error == null && response is TLRPC.TLBizDataRaw) {
					val data = response.readData<ElloRpc.MagickLinkResponse>()

					if (data?.status == true) {
						messagesController.performLogout(1)
					} else {
						context?.let {
							val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.link_verification_failed)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
								finishFragment()
							}.create()

							dialog.setCancelable(false)
							dialog.setCanceledOnTouchOutside(false)
							dialog.show()
						}
					}
				}
				else {
					when (error?.text) {
						"MAGIC_LINK_NOT_EXIST_FOR_AUTH" -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.magic_link_not_exist_for_auth)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
									finishFragment()
								}.create()

								dialog.setCancelable(false)
								dialog.setCanceledOnTouchOutside(false)
								dialog.show()
							}
						}
						"MAGIC_LINK_INTERNAL_ERR" -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.magic_link_internal_err)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
									finishFragment()
								}.create()

								dialog.setCancelable(false)
								dialog.setCanceledOnTouchOutside(false)
								dialog.show()
							}
						}
						"MAGIC_LINK_INVALID_HASH" -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.magic_link_invalid_hash)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
									finishFragment()
								}.create()

								dialog.setCancelable(false)
								dialog.setCanceledOnTouchOutside(false)
								dialog.show()
							}
						}
						"MAGIC_LINK_EXPIRED" -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.magic_link_expired)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
									finishFragment()
								}.create()

								dialog.setCancelable(false)
								dialog.setCanceledOnTouchOutside(false)
								dialog.show()
							}
						}
						else -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.link_verification_failed)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
									finishFragment()
								}.create()

								dialog.setCancelable(false)
								dialog.setCanceledOnTouchOutside(false)
								dialog.show()
							}
						}
					}
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)
	}

	fun changeEmail(hash: String?, email: String) {
		if (hash.isNullOrEmpty()) {
			return
		}

		binding?.descriptionContainer?.gone()
		binding?.openEmailButton?.gone()

		binding?.loader?.visible()
		playAnimation()

		val req = ElloRpc.changeEmailRequest(email, hash)

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {

				if (error == null && response is TLRPC.TLBizDataRaw) {
					val data = response.readData<ElloRpc.MagickLinkResponse>()

					if (data?.status == true) {
						context?.let {
							val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.email_updated_successfully)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }.create()

							dialog.setCancelable(false)
							dialog.setCanceledOnTouchOutside(false)
							dialog.show()
						}
						finishFragment()
					} else {
						context?.let {
							val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.link_verification_failed)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
								finishFragment()
							}.create()

							dialog.setCancelable(false)
							dialog.setCanceledOnTouchOutside(false)
							dialog.show()
						}
					}
				}
				else {
					when (error?.text) {
						"MAGIC_LINK_NOT_EXIST_FOR_AUTH" -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.magic_link_not_exist_for_auth)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
									finishFragment()
								}.create()

								dialog.setCancelable(false)
								dialog.setCanceledOnTouchOutside(false)
								dialog.show()
							}
						}
						"MAGIC_LINK_INTERNAL_ERR" -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.magic_link_internal_err)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
									finishFragment()
								}.create()

								dialog.setCancelable(false)
								dialog.setCanceledOnTouchOutside(false)
								dialog.show()
							}
						}
						"MAGIC_LINK_INVALID_HASH" -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.magic_link_invalid_hash)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
									finishFragment()
								}.create()

								dialog.setCancelable(false)
								dialog.setCanceledOnTouchOutside(false)
								dialog.show()
							}
						}
						"MAGIC_LINK_EXPIRED" -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.magic_link_expired)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
									finishFragment()
								}.create()

								dialog.setCancelable(false)
								dialog.setCanceledOnTouchOutside(false)
								dialog.show()
							}
						}
						else -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.link_verification_failed)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ ->
									finishFragment()
								}.create()

								dialog.setCancelable(false)
								dialog.setCanceledOnTouchOutside(false)
								dialog.show()
							}
						}
					}
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)

	}

}