/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.databinding.QrCodeFragmentBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper
import kotlin.math.max
import kotlin.math.min

class QrFragment(args: Bundle?) : BaseFragment(args) {
	private var binding: QrCodeFragmentBinding? = null
	private var userId: Long = 0L
	private var chatId: Long = 0L
	private var isPublic = true
	private var link: String? = null

	override fun onFragmentCreate(): Boolean {
		userId = arguments?.getLong(USER_ID) ?: 0L
		chatId = arguments?.getLong(CHAT_ID) ?: 0L
		link = arguments?.getString(LINK)
		isPublic = arguments?.getBoolean(IS_PUBLIC) ?: true

		if (!isPublic && link.isNullOrEmpty()) {
			return false
		}

		return !(userId == 0L && chatId == 0L)
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(false)
		actionBar?.background = null
		actionBar?.setItemsColor(-0x1, false)

		binding = QrCodeFragmentBinding.inflate(LayoutInflater.from(context))

		binding?.back?.background = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(34f), 0x28000000, 0x28ffffff)

		binding?.back?.setOnClickListener {
			finishFragment()
		}

		var avatarDrawable: AvatarDrawable? = null
		var username: String? = null
		var imageLocationSmall: ImageLocation? = null
		var imageLocation: ImageLocation? = null

		if (userId != 0L) {
			val user = messagesController.getUser(userId)

			if (user != null) {
				username = user.username
				avatarDrawable = AvatarDrawable(user)
				imageLocationSmall = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL)
				imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_BIG)
			}
		}
		else if (chatId != 0L) {
			val chat = messagesController.getChat(chatId)

			if (chat != null) {
				username = chat.username
				avatarDrawable = AvatarDrawable(chat)
				imageLocationSmall = ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL)
				imageLocation = ImageLocation.getForChat(chat, ImageLocation.TYPE_BIG)
			}
		}

		if (isPublic && !username.isNullOrEmpty()) {
			binding?.username?.text = "@$username"
		}
		else {
			binding?.username?.gone()
		}

		val newLink = link ?: ("https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + username)

		if (isPublic) {
			val avatarImageView = BackupImageView(context)
			avatarImageView.setRoundRadius(AndroidUtilities.dp(40f))
			avatarImageView.setSize(AndroidUtilities.dp(80f), AndroidUtilities.dp(80f))
			avatarImageView.setImage(imageLocation, "84_84", imageLocationSmall, "50_50", avatarDrawable, null, null, 0, null)

			binding?.avatarContainer?.addView(avatarImageView, LayoutHelper.createFrame(80, 80f))
		}
		else {
			binding?.avatarContainer?.gone()
		}

		prepareContent(newLink, binding?.qrCodeHolder)

		binding?.shareButton?.setOnClickListener {
			performShare()
		}

		fragmentView = binding?.root

		return binding?.root
	}

	private fun prepareContent(link: String, imageView: ImageView?) {
		var qrBitmap: Bitmap? = null
		val qrBitmapSize: Int = AndroidUtilities.dp(230f)

		val hints = HashMap<EncodeHintType, Any>()
		hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
		hints[EncodeHintType.MARGIN] = 0

		val writer = QRCodeWriter()

		for (version in 3..4) {
			try {
				hints[EncodeHintType.QR_VERSION] = version

				qrBitmap = writer.encode(link, qrBitmapSize, qrBitmapSize, hints, null, 0.45f, Color.TRANSPARENT, Color.WHITE)
			}
			catch (e: Exception) {
				// ignore
			}

			if (qrBitmap != null) {
				break
			}
		}

		if (qrBitmap != null) {
			// imageView?.setImageBitmap(invert(qrBitmap))
			imageView?.setImageBitmap(qrBitmap)
		}
	}

	private fun performShare() {
		val width = min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)
		val height = max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)

		binding?.shareButton?.gone()
		binding?.back?.gone()

		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bitmap)

		fragmentView?.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
		fragmentView?.layout(0, 0, width, height)
		fragmentView?.draw(canvas)

		canvas.setBitmap(null)

		val parent = fragmentView?.parent as? ViewGroup

		fragmentView?.layout(0, 0, parent?.width ?: 0, parent?.height ?: 0)

		binding?.shareButton?.visible()
		binding?.back?.visible()

		val uri = AndroidUtilities.getBitmapShareUri(bitmap, "qr_tmp.jpg", Bitmap.CompressFormat.JPEG)

		if (uri != null) {
			try {
				parentActivity?.let {
					val intent = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, uri)
					val chooserIntent = Intent.createChooser(intent, it.getString(R.string.InviteByQRCode))
					val resInfoList = it.packageManager.queryIntentActivities(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY)

					for (resolveInfo in resInfoList) {
						val packageName = resolveInfo.activityInfo.packageName
						it.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
					}

					it.startActivityForResult(chooserIntent, 500)
				}
			}
			catch (ex: ActivityNotFoundException) {
				FileLog.e(ex)
			}
		}
	}

//	fun invert(src: Bitmap): Bitmap? {
//		val height = src.height
//		val width = src.width
//
//		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//		bitmap.eraseColor(Color.TRANSPARENT)
//
//		val canvas = Canvas(bitmap)
//		val paint = Paint()
//		val matrixGrayscale = ColorMatrix()
//
//		val matrixInvert = ColorMatrix()
//		matrixInvert.set(floatArrayOf(-1.0f, 0.0f, 0.0f, 0.0f, 255.0f, 0.0f, -1.0f, 0.0f, 0.0f, 255.0f, 0.0f, 0.0f, -1.0f, 0.0f, 255.0f, 0.0f, 0.0f, 0.0f, -1.0f, 255.0f))
//		matrixInvert.preConcat(matrixGrayscale)
//
//		val filter = ColorMatrixColorFilter(matrixInvert)
//
//		paint.colorFilter = filter
//
//		canvas.drawBitmap(src, 0f, 0f, paint)
//
//		return bitmap
//	}

	companion object {
		const val USER_ID = "user_id"
		const val CHAT_ID = "chat_id"
		const val LINK = "link"
		const val IS_PUBLIC = "is_public"
	}
}
