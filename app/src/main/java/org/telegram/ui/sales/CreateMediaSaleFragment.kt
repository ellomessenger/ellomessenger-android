/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.sales

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import com.beint.elloapp.FileHelper
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildVars
import org.telegram.messenger.FileLog
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaController.PhotoEntry
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.Utilities
import org.telegram.messenger.databinding.MediaSaleFragmentBinding
import org.telegram.messenger.databinding.MediaSaleItemViewBinding
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TL_messages_sendMessage
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ChatAttachAlert
import org.telegram.ui.Components.ChatAttachAlert.ChatAttachViewDelegate
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout
import java.net.URLDecoder
import kotlin.math.abs

class CreateMediaSaleFragment(args: Bundle) : BaseFragment(args), ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate, NotificationCenter.NotificationCenterDelegate {
	private var binding: MediaSaleFragmentBinding? = null
	private var dialogId = 0L
	private var chatAttachAlert: ChatAttachAlert? = null
	private var paused = true
	private val mediasPaths = mutableListOf<String>()
	private val mediasOriginalPaths = mutableListOf<String>()
	private val uris = mutableListOf<Uri>()

	val currentChat: TLRPC.Chat?
		get() = messagesController.getChat(abs(dialogId))

	override fun onFragmentCreate(): Boolean {
		dialogId = arguments?.getLong(DIALOG_ID, 0L) ?: 0L
		return dialogId != 0L
	}

	override fun createView(context: Context): View? {
		actionBar?.setTitle(context.getString(R.string.create_media_sale))
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

		binding = MediaSaleFragmentBinding.inflate(LayoutInflater.from(context))

		binding?.uploadFilesButton?.setOnClickListener {
			showChatAttachAlert()
		}

		binding?.startButton?.setOnClickListener {
			startMediaSale()
		}

		binding?.cancelButton?.setOnClickListener {
			finishFragment()
		}

		binding?.titleField?.addTextChangedListener {
			checkData()
		}

		binding?.descriptionField?.addTextChangedListener {
			checkData()
		}

		binding?.priceField?.addTextChangedListener {
			checkData()
		}

		binding?.quantityField?.addTextChangedListener {
			checkData()
		}

		fragmentView = binding?.root

		checkData()

		return binding?.root
	}

	private fun createChatAttachView() {
		val context = context ?: return

		if (chatAttachAlert != null) {
			return
		}

		chatAttachAlert = object : ChatAttachAlert(context, this@CreateMediaSaleFragment, false, false) {
			override fun dismissInternal() {
				if (chatAttachAlert?.isShowing == true) {
					AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
				}

				super.dismissInternal()
			}

			override fun onDismissAnimationStart() {
				chatAttachAlert?.isFocusable = false

				if (chatAttachAlert?.isShowing == true) {
					AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
				}
			}
		}

		chatAttachAlert?.setDelegate(object : ChatAttachViewDelegate {
			override fun openAvatarsSearch() {
				// unused
			}

			override fun didPressedButton(button: Int, arg: Boolean, notify: Boolean, scheduleDate: Int, forceDocument: Boolean) {
				val chatAttachAlert = chatAttachAlert
				val parentActivity = parentActivity

				if (parentActivity == null || chatAttachAlert == null) {
					return
				}

				if (button == 8 || button == 7 || button == 4 && chatAttachAlert.photoLayout.selectedPhotos.isNotEmpty()) {
					if (button != 8) {
						chatAttachAlert.dismiss(true)
					}

					val selectedPhotos = chatAttachAlert.photoLayout.selectedPhotos
					val selectedPhotosOrder = chatAttachAlert.photoLayout.selectedPhotosOrder
					val paths = mutableListOf<String>()

					if (selectedPhotos.isNotEmpty()) {
						for (key in selectedPhotosOrder) {
							val photoEntry = selectedPhotos[key] as PhotoEntry

							if (!photoEntry.isVideo && photoEntry.imagePath != null) {
								paths.add(photoEntry.imagePath)
							}
							else if (photoEntry.path != null) {
								paths.add(photoEntry.path)
							}

							photoEntry.reset()
						}
					}

					addFiles(paths.map { it to it }, null)

					return
				}
				else {
					chatAttachAlert.dismissWithButtonClick(button)
				}
			}

			override fun onCameraOpened() {
				AndroidUtilities.hideKeyboard(binding?.root)
			}

			override fun needEnterComment(): Boolean {
				return false
			}
		})
	}

	private fun showChatAttachAlert() {
		createChatAttachView()

		chatAttachAlert?.photoLayout?.loadGalleryPhotos()
		chatAttachAlert?.setMaxSelectedPhotos(-1, true)
		chatAttachAlert?.init()
		chatAttachAlert?.commentTextView?.text = null
		chatAttachAlert?.setEditingMessageObject(null)

		showDialog(chatAttachAlert)
	}

	override fun onPause() {
		super.onPause()
		paused = true
	}

	override fun onResume() {
		super.onResume()
		paused = false
	}

//	private fun showAttachmentError() {
//		val parentActivity = parentActivity ?: return
//		BulletinFactory.of(this).createErrorBulletin(parentActivity.getString(R.string.UnsupportedAttachment)).show()
//	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}

	fun sendAudio(audios: List<MessageObject>?) {
		audios?.mapNotNull {
			val path = it.messageOwner?.attachPath ?: return@mapNotNull null
			path to path
		}?.let {
			addFiles(it, null)
		}
	}

	override fun didSelectFiles(files: List<String>?, caption: String?, fmessages: List<MessageObject>?, notify: Boolean, scheduleDate: Int) {
		files?.map {
			it to it
		}?.let {
			addFiles(it, null)
		}
	}

	private fun addFiles(paths: List<Pair<String, String>>?, uris: List<Uri>?) {
		if (paths.isNullOrEmpty() && uris.isNullOrEmpty()) {
			return
		}

		var mergedOriginalPaths: List<String>? = null
		var mergedPaths: List<String>? = null
		var mergedUris: List<Uri>? = null
		val extensions = mutableSetOf<String>()

		if (paths != null) {
			mergedOriginalPaths = mediasOriginalPaths.toMutableList()

			mergedPaths = mediasPaths + paths.filter {
				!FileHelper.isArchive(it.first)
			}.map { entry ->
				entry.first.also {
					mergedOriginalPaths.add(entry.second)
				}
			}

			extensions.addAll(mergedPaths.map {
				it.substringAfterLast(".", "").lowercase()
			})
		}

		if (uris != null) {
			mergedUris = this.uris + uris.filter {
				val path = AndroidUtilities.getPath(it) ?: return@filter false
				!FileHelper.isArchive(path)
			}

			extensions.addAll(mergedUris.mapNotNull {
				AndroidUtilities.getPath(it)?.substringAfterLast(".", "")?.lowercase()
			})
		}

		if (extensions.count() > 1) {
			val parentActivity = parentActivity ?: return
			BulletinFactory.of(this).createErrorBulletin(parentActivity.getString(R.string.MediaSaleDifferentFileTypes)).show()
			return
		}

		if (mergedOriginalPaths != null && mergedPaths != null) {
			mediasPaths.clear()
			mediasPaths.addAll(mergedPaths)

			mediasOriginalPaths.clear()
			mediasOriginalPaths.addAll(mergedOriginalPaths)
		}

		if (mergedUris != null) {
			this.uris.clear()
			this.uris.addAll(mergedUris)
		}

		updateMediaList()
	}

	private fun updateMediaList() {
		binding?.mediaContainer?.removeAllViews()

		mediasPaths.forEachIndexed { index, path ->
			val view = MediaSaleItemView(MediaSaleItemViewBinding.inflate(LayoutInflater.from(context))) {
				mediasPaths.removeAt(index)
				mediasOriginalPaths.removeAt(index)
				updateMediaList()
			}

			view.media = path

			binding?.mediaContainer?.addView(view)

			view.updateLayoutParams<LinearLayout.LayoutParams> {
				topMargin = AndroidUtilities.dp(8f)
			}
		}

		uris.forEach { uri ->
			val view = MediaSaleItemView(MediaSaleItemViewBinding.inflate(LayoutInflater.from(context))) {
				uris.remove(uri)
				updateMediaList()
			}

			view.media = AndroidUtilities.getPath(uri)

			binding?.mediaContainer?.addView(view)

			view.updateLayoutParams<LinearLayout.LayoutParams> {
				topMargin = AndroidUtilities.dp(8f)
			}
		}

		if ((binding?.mediaContainer?.childCount ?: 0) > 0) {
			binding?.mediaContainer?.visible()
		}
		else {
			binding?.mediaContainer?.gone()
		}

		checkData()
	}

	private val title: String?
		get() = binding?.titleField?.text?.toString()

	private val description: String?
		get() = binding?.descriptionField?.text?.toString()

	private val price: Int?
		get() = binding?.priceField?.text?.toString()?.toIntOrNull()

	private val quantity: Int?
		get() = binding?.quantityField?.text?.toString()?.toIntOrNull()

	private fun checkData() {
		if (mediasPaths.isEmpty()) {
			binding?.startButton?.isEnabled = false
			return
		}

		if (title.isNullOrEmpty()) {
			binding?.startButton?.isEnabled = false
			return
		}

		if (description.isNullOrEmpty()) {
			binding?.startButton?.isEnabled = false
			return
		}

		val price = price

		if (price == null || price <= 0) {
			binding?.startButton?.isEnabled = false
			return
		}

		val quantity = quantity

		if (quantity == null || quantity <= 0) {
			binding?.startButton?.isEnabled = false
			return
		}

		binding?.startButton?.isEnabled = true
	}

	private fun startMediaSale() {
		binding?.startButton?.isEnabled = false

		val req = TL_messages_sendMessage()
		req.message = description
		req.clear_draft = true
		req.silent = false
		req.peer = messagesController.getInputPeer(dialogId)
		req.random_id = Utilities.random.nextLong()
		req.no_webpage = true
		req.is_media_sale = true
		req.title = title
		req.price = price?.toDouble() ?: 0.0
		req.quantity = quantity ?: 0
		req.noforwards = true

		val peer = TLRPC.TL_peerChannel()
		peer.channel_id = dialogId

		req.send_as = messagesController.getInputPeer(peer)

		connectionsManager.sendRequest(req, { response, error ->
			if (error != null) {
				AndroidUtilities.runOnUIThread {
					processError(error.text)
				}

				return@sendRequest
			}

			var mediaHash: String? = null

			if (response is TLRPC.TL_updates) {
				for (obj in response.updates) {
					if (obj is TLRPC.TL_updateNewMessage) {
						mediaHash = obj.message.mediaHash
						break
					}
				}
			}

			if (mediaHash.isNullOrEmpty()) {
				AndroidUtilities.runOnUIThread {
					processError(null)
				}

				return@sendRequest
			}

			SendMessagesHelper.prepareSendingDocuments(accountInstance, mediasPaths, mediasOriginalPaths, uris, null, null, dialogId, null, null, null, null, true, 0, isMediaSale = true, mediaSaleHash = mediaHash)

			AndroidUtilities.runOnUIThread {
				finishFragment()
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun processError(error: String? = null) {
		val context = context ?: return

		binding?.startButton?.isEnabled = true

		BulletinFactory.of(this).createErrorBulletin(error ?: context.getString(R.string.failed_to_create_media_sale)).show()
	}

	override fun startDocumentSelectActivity() {
		try {
			val photoPickerIntent = Intent(Intent.ACTION_GET_CONTENT)
			photoPickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
			photoPickerIntent.type = "*/*"
			startActivityForResult(photoPickerIntent, 21)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	override fun onActivityResultFragment(requestCode: Int, resultCode: Int, data: Intent?) {
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == ChatAttachAlert.REQUEST_CODE_ATTACH_FILE) {
				if (data == null) {
					return
				}

				val dataData = data.data
				val clipData = data.clipData

				if (dataData != null) {
					sendUrisAsDocuments(listOf(dataData))
				}
				else if (clipData != null) {
					val uris = mutableListOf<Uri>()

					for (i in 0 until clipData.itemCount) {
						uris.add(clipData.getItemAt(i).uri)
					}

					sendUrisAsDocuments(uris)
				}

				chatAttachAlert?.dismiss()
			}
		}
	}

	private fun sendUrisAsDocuments(uris: List<Uri>) {
		val targetPaths = mutableListOf<Pair<String, String>>()
		val targetUris = mutableListOf<Uri>()

		for (uri in uris) {
			@Suppress("NAME_SHADOWING") var uri: Uri = uri
			val extractUriFrom = uri.toString()

			if (extractUriFrom.contains("com.google.android.apps.photos.contentprovider")) {
				runCatching {
					var firstExtraction = extractUriFrom.split("/1/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
					val index = firstExtraction.indexOf("/ACTUAL")

					if (index != -1) {
						firstExtraction = firstExtraction.substring(0, index)
						val secondExtraction = URLDecoder.decode(firstExtraction, "UTF-8")
						uri = Uri.parse(secondExtraction)
					}
				}.onFailure {
					FileLog.e(it)
				}
			}

			var tempPath = AndroidUtilities.getPath(uri)
			var originalPath = tempPath
			var sendAsUri = false

			if (!BuildVars.NO_SCOPED_STORAGE) {
				sendAsUri = true
			}
			else if (tempPath == null) {
				originalPath = uri.toString()
				tempPath = MediaController.copyFileToCache(uri, "file")

				if (tempPath == null) {
					continue
				}
			}

			if (sendAsUri) {
				if (AndroidUtilities.getPath(uri) != null) {
					targetUris.add(uri)
				}
			}
			else {
				if (tempPath != null && originalPath != null) {
					targetPaths.add(tempPath to originalPath)
				}
			}
		}

		addFiles(targetPaths, targetUris)
	}

	companion object {
		const val DIALOG_ID = "dialog_id"
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		FileLog.d("didReceivedNotification $id $account $args")
	}
}
