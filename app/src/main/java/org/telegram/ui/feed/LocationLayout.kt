/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.feed

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.cardview.widget.CardView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.WebFile
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.accessHash
import org.telegram.tgnet.lat
import org.telegram.tgnet.lon
import org.telegram.tgnet.period
import org.telegram.ui.Components.LayoutHelper
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

@SuppressLint("ViewConstructor")
class LocationLayout(context: Context, private val contentWidth: Int) : FrameLayout(context) {
	private val imageView = ImageView(context)
	private var addedForTest = false
	private var currentUrl: String? = null
	private var currentWebFile: WebFile? = null
	private val currentAccount = UserConfig.selectedAccount
	private val photoImage = ImageReceiver(this)
	private var scheduledInvalidate = false
	private var locationExpired = false
	private val invalidateRunnable = Runnable { checkLocationExpired() }

	val isLiveLocation: Boolean
		get() = MessageObject.getMedia(messageObject?.messageOwner) is TLRPC.TLMessageMediaGeoLive

	val expiresIn: Int
		get() {
			val media = MessageObject.getMedia(messageObject?.messageOwner) as? TLRPC.TLMessageMediaGeoLive ?: return 0
			return ((messageObject?.messageOwner?.date ?: 0) + media.period) - ConnectionsManager.getInstance(currentAccount).currentTime
		}

	val lastUpdateDate: Int
		get() = messageObject?.messageOwner?.date ?: 0

	val coordinates: Pair<Double, Double>?
		get() {
			val media = when (val msgMedia = MessageObject.getMedia(messageObject?.messageOwner)) {
				is TLRPC.TLMessageMediaGeoLive -> msgMedia
				is TLRPC.TLMessageMediaGeo -> msgMedia
				else -> null
			} ?: return null

			return (media.geo?.lat ?: 0.0) to (media.geo?.lon ?: 0.0)
		}

	var messageObject: MessageObject? = null
		set(value) {
			field = value

			photoImage.cancelLoadImage()

			imageView.setImageDrawable(null)

			if (scheduledInvalidate) {
				AndroidUtilities.cancelRunOnUIThread(invalidateRunnable)
				scheduledInvalidate = false
			}

			scheduledInvalidate = false
			locationExpired = false

			if (addedForTest && currentUrl != null && currentWebFile != null) {
				ImageLoader.getInstance().removeTestWebFile(currentUrl)
			}

			currentWebFile = null
			currentUrl = null

			addedForTest = false

			if (value == null) {
				return
			}

			val point = MessageObject.getMedia(value.messageOwner)?.geo ?: return

			var lat = point.lat
			val lon = point.lon

			val provider = if (value.dialogId.toInt() == 0) {
				when (SharedConfig.mapPreviewType) {
					SharedConfig.MAP_PREVIEW_PROVIDER_ELLO -> MessagesController.MAP_PROVIDER_UNDEFINED
					SharedConfig.MAP_PREVIEW_PROVIDER_GOOGLE -> MessagesController.MAP_PROVIDER_GOOGLE
					SharedConfig.MAP_PREVIEW_PROVIDER_YANDEX -> MessagesController.MAP_PROVIDER_YANDEX_NO_ARGS
					else -> MessagesController.MAP_PROVIDER_UNDEFINED
				}
			}
			else {
				MessagesController.getInstance(currentAccount).mapProvider
			}

			if (isLiveLocation) {
				val offset = 268_435_456
				val rad = offset / Math.PI
				val y = ((offset - rad * ln((1 + sin(lat * Math.PI / 180.0)) / (1 - sin(lat * Math.PI / 180.0))) / 2).roundToLong() - (AndroidUtilities.dp(10.3f) shl 21 - 15)).toDouble()

				lat = (Math.PI / 2.0 - 2 * atan(exp((y - offset) / rad))) * 180.0 / Math.PI

				currentUrl = AndroidUtilities.formatMapUrl(currentAccount, lat, lon, (contentWidth / AndroidUtilities.density).toInt(), (contentWidth / AndroidUtilities.density).toInt(), false, 15, provider)

				currentWebFile = WebFile.createWithGeoPoint(lat, lon, point.accessHash, (contentWidth / AndroidUtilities.density).toInt(), (contentWidth / AndroidUtilities.density).toInt(), 15, min(2, ceil(AndroidUtilities.density.toDouble()).toInt()))

				if (!isCurrentLocationTimeExpired().also { locationExpired = it }) {
					AndroidUtilities.runOnUIThread(invalidateRunnable, 1_000)
					scheduledInvalidate = true
				}
			}
			else {
				currentUrl = AndroidUtilities.formatMapUrl(currentAccount, lat, lon, (contentWidth / AndroidUtilities.density).toInt(), (contentWidth / AndroidUtilities.density).toInt(), true, 15, provider)
				currentWebFile = WebFile.createWithGeoPoint(point, (contentWidth / AndroidUtilities.density).toInt(), (contentWidth / AndroidUtilities.density).toInt(), 15, min(2, ceil(AndroidUtilities.density.toDouble()).toInt()))
			}

			if (provider == MessagesController.MAP_PROVIDER_UNDEFINED) {
				photoImage.setImage(null, null, null, null, value, 0)
			}
			else if (provider == MessagesController.MAP_PROVIDER_ELLO) {
				if (currentWebFile != null) {
					photoImage.setImage(ImageLocation.getForWebFile(currentWebFile), null, null, null, null as Drawable?, value, 0)
				}
			}
			else {
				if (provider == MessagesController.MAP_PROVIDER_YANDEX_WITH_ARGS || provider == MessagesController.MAP_PROVIDER_GOOGLE) {
					ImageLoader.getInstance().addTestWebFile(currentUrl, currentWebFile)
					addedForTest = true
				}

				if (currentUrl != null) {
					photoImage.setImage(currentUrl, null, null, null, 0)
				}
			}
		}

	private fun isCurrentLocationTimeExpired(): Boolean {
		val messageObject = messageObject ?: return false
		val media = MessageObject.getMedia(messageObject.messageOwner) ?: return false
		val diff = abs(ConnectionsManager.getInstance(currentAccount).currentTime - messageObject.messageOwner!!.date)

		return if (media.period % 60 == 0) {
			diff > media.period
		}
		else {
			diff > media.period - 5
		}
	}

	private fun checkLocationExpired() {
		val currentMessageObject = messageObject ?: return
		val newExpired = isCurrentLocationTimeExpired()

		if (newExpired != locationExpired) {
			locationExpired = newExpired

			if (!locationExpired) {
				AndroidUtilities.runOnUIThread(invalidateRunnable, 1_000)
				scheduledInvalidate = true
			}
			else {
				messageObject = null
				messageObject = currentMessageObject
			}
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()

		if (scheduledInvalidate) {
			AndroidUtilities.cancelRunOnUIThread(invalidateRunnable)
			scheduledInvalidate = false
		}

		val currentUrl = currentUrl
		val currentWebFile = currentWebFile

		if (addedForTest && currentUrl != null && currentWebFile != null) {
			ImageLoader.getInstance().removeTestWebFile(currentUrl)
			addedForTest = false
		}
	}

	init {
		val cardView = CardView(context)
		cardView.radius = AndroidUtilities.dp(12f).toFloat()
		cardView.cardElevation = 0f

		addView(cardView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.px(contentWidth.toFloat()).toFloat()))

		imageView.scaleType = ImageView.ScaleType.CENTER_CROP
		imageView.setBackgroundResource(R.color.light_background)

		cardView.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		photoImage.setDelegate(object : ImageReceiver.ImageReceiverDelegate {
			override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
				imageView.setImageDrawable(imageReceiver.drawable)
			}
		})
	}
}
