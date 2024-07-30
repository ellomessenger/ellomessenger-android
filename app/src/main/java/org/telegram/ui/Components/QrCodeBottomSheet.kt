/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Mykhailo Mykytyn, Ello 2023.
 */
package org.telegram.ui.Components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear

open class QrCodeBottomSheet(context: Context, link: String, helpMessage: String?) : BottomSheet(context, false) {
	private val qrCode: Bitmap?
	private val help: TextView
	private val buttonTextView: TextView
	private val iconImage: ImageView
	private var imageSize = 0

	init {
		fixNavigationBar()

		setTitle(context.getString(R.string.InviteByQRCode), true)

		val imageView = @SuppressLint("AppCompatCustomView") object : ImageView(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val size = MeasureSpec.getSize(widthMeasureSpec)
				super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
			}
		}

		imageView.scaleType = ImageView.ScaleType.FIT_XY
		imageView.clipToOutline = true

		imageView.outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setRoundRect(0, 0, view.measuredWidth, view.measuredHeight, AndroidUtilities.dp(12f).toFloat())
			}
		}

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.VERTICAL
		linearLayout.setPadding(0, AndroidUtilities.dp(16f), 0, 0)

		qrCode = createQR(link)

		imageView.setImageBitmap(qrCode)

		iconImage = ImageView(context)
		iconImage.setImageResource(R.drawable.qr_panda)

		val frameLayout = object : FrameLayout(context) {
			var lastX = 0f

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec)

				val x = imageSize / 768f * imageView.measuredHeight

				if (lastX != x) {
					lastX = x
					iconImage.layoutParams.width = x.toInt()
					iconImage.layoutParams.height = iconImage.layoutParams.width

					super.onMeasure(widthMeasureSpec, heightMeasureSpec)
				}
			}
		}

		frameLayout.addView(imageView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		frameLayout.addView(iconImage, createFrame(60, 60, Gravity.CENTER))

		linearLayout.addView(frameLayout, createLinear(220, 220, Gravity.CENTER_HORIZONTAL, 30, 0, 30, 0))

		help = TextView(context)
		help.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		help.text = helpMessage
		help.gravity = Gravity.CENTER_HORIZONTAL

		linearLayout.addView(help, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), 0, 40f, 8f, 40f, 8f))

		buttonTextView = TextView(context)
		buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
		buttonTextView.gravity = Gravity.CENTER
		buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		buttonTextView.typeface = Theme.TYPEFACE_BOLD
		buttonTextView.text = context.getString(R.string.ShareQrCode)

		buttonTextView.setOnClickListener {
			performShare(frameLayout)
//			val uri = AndroidUtilities.getBitmapShareUri(qrCode, "qr_tmp.png", Bitmap.CompressFormat.PNG)
//
//			if (uri != null) {
//				val i = Intent(Intent.ACTION_SEND)
//				i.type = "image/*"
//				i.putExtra(Intent.EXTRA_STREAM, uri)
//
//				try {
//					val inviteByQRCode = Intent.createChooser(i, context.getString(R.string.InviteByQRCode))
//					val resInfoList = context.packageManager.queryIntentActivities(inviteByQRCode, PackageManager.MATCH_DEFAULT_ONLY)
//
//					for (resolveInfo in resInfoList) {
//						val packageName = resolveInfo.activityInfo.packageName
//						context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
//					}
//
//					AndroidUtilities.findActivity(context).startActivityForResult(inviteByQRCode, 500)
//				}
//				catch (ex: ActivityNotFoundException) {
//					FileLog.e(ex)
//				}
//			}
		}

		linearLayout.addView(buttonTextView, createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 15, 16, 16))

		updateColors()

		val scrollView = ScrollView(context)
		scrollView.addView(linearLayout)

		setCustomView(scrollView)
	}

	private fun createQR(key: String): Bitmap? {
		return try {
			val hints = HashMap<EncodeHintType, Any>()
			hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
			hints[EncodeHintType.MARGIN] = 0

			val writer = QRCodeWriter()
			val bitmap = writer.encode(key, 768, 768, hints, null)

			imageSize = writer.imageSize

			bitmap
		}
		catch (e: Exception) {
			FileLog.e(e)
			null
		}
	}

	fun updateColors() {
		buttonTextView.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6f), ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.darker_brand, null))
		buttonTextView.setTextColor(Color.WHITE)

		help.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))

		titleView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))

		setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
	}

	private fun performShare(frameLayout: FrameLayout) {
		val width = frameLayout.measuredWidth
		val height = frameLayout.measuredHeight
		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bitmap)

		frameLayout.draw(canvas)

		canvas.setBitmap(null)

		val uri = AndroidUtilities.getBitmapShareUri(bitmap, "qr_tmp.png", Bitmap.CompressFormat.PNG)

		if (uri != null) {
			val i = Intent(Intent.ACTION_SEND)
			i.type = "image/*"
			i.putExtra(Intent.EXTRA_STREAM, uri)

			try {
				val inviteByQRCode = Intent.createChooser(i, context.getString(R.string.InviteByQRCode))
				val resInfoList = context.packageManager.queryIntentActivities(inviteByQRCode, PackageManager.MATCH_DEFAULT_ONLY)

				for (resolveInfo in resInfoList) {
					val packageName = resolveInfo.activityInfo.packageName
					context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
				}

				AndroidUtilities.findActivity(context).startActivityForResult(inviteByQRCode, 500)
			}
			catch (ex: ActivityNotFoundException) {
				FileLog.e(ex)
			}
		}
	}
}
