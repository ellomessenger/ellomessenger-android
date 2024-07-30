/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.min

class VideoEncodingService : Service(), NotificationCenter.NotificationCenterDelegate {
	private var path: String? = null
	private var currentProgress = 0
	private var currentAccount = 0
	private var isGif = false

	init {
		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.stopEncodingService)
	}

	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	override fun onDestroy() {
		super.onDestroy()

		runCatching {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				stopForeground(STOP_FOREGROUND_REMOVE)
			}
			else {
				@Suppress("DEPRECATION") stopForeground(true)
			}
		}

		NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.stopEncodingService)
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadProgressChanged)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		@Suppress("NAME_SHADOWING") var account = account

		when (id) {
			NotificationCenter.fileUploadProgressChanged -> {
				val fileName = args[0] as? String

				if (account == currentAccount && path != null && path == fileName) {
					val loadedSize = args[1] as Long
					val totalSize = args[2] as Long
					val progress = min(1.0, (loadedSize / totalSize.toFloat()).toDouble()).toFloat()

					currentProgress = (progress * 100).toInt()

					updateNotification()
				}
			}

			NotificationCenter.stopEncodingService -> {
				val filepath = args[0] as? String

				account = args[1] as Int

				if (account == currentAccount && (filepath == null || filepath == path)) {
					stopSelf()
				}
			}
		}
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
		path = intent.getStringExtra("path")

		val oldAccount = currentAccount

		currentAccount = intent.getIntExtra("currentAccount", UserConfig.selectedAccount)

		if (!UserConfig.isValidAccount(currentAccount)) {
			stopSelf()
			return START_NOT_STICKY
		}

		if (oldAccount != currentAccount) {
			NotificationCenter.getInstance(oldAccount).removeObserver(this, NotificationCenter.fileUploadProgressChanged)
			NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadProgressChanged)
		}

		isGif = intent.getBooleanExtra("gif", false)

		if (path == null) {
			stopSelf()
			return START_NOT_STICKY
		}

		NotificationsController.checkOtherNotificationsChannel()

		currentProgress = 0

		runCatching {
			startForeground()
		}

		return START_NOT_STICKY
	}

	private fun createNotification(): Notification {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val builder = Notification.Builder(this, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL!!)
			builder.setSmallIcon(android.R.drawable.stat_sys_upload)
			builder.setWhen(System.currentTimeMillis())
			builder.setContentTitle(getString(R.string.AppName))
			builder.setProgress(100, currentProgress, currentProgress == 0)
			builder.setCategory(Notification.CATEGORY_PROGRESS)
			builder.setVisibility(Notification.VISIBILITY_PUBLIC)

			if (isGif) {
				builder.setTicker(getString(R.string.SendingGif))
				builder.setContentText(getString(R.string.SendingGif))
			}
			else {
				builder.setTicker(getString(R.string.SendingVideo))
				builder.setContentText(getString(R.string.SendingVideo))
			}

			builder.build()
		}
		else {
			val builder = NotificationCompat.Builder(this, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL!!)
			builder.setSmallIcon(android.R.drawable.stat_sys_upload)
			builder.setWhen(System.currentTimeMillis())
			builder.setContentTitle(getString(R.string.AppName))
			builder.setProgress(100, currentProgress, currentProgress == 0)
			builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			builder.setCategory(NotificationCompat.CATEGORY_PROGRESS)
			builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

			if (isGif) {
				builder.setTicker(getString(R.string.SendingGif))
				builder.setContentText(getString(R.string.SendingGif))
			}
			else {
				builder.setTicker(getString(R.string.SendingVideo))
				builder.setContentText(getString(R.string.SendingVideo))
			}

			builder.build()
		}
	}

	private fun startForeground() {
		val notification = createNotification()

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		}
		else {
			startForeground(NOTIFICATION_ID, notification)
		}

		updateNotification()
	}

	private fun updateNotification() {
		try {
			val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
			notificationManager.notify(NOTIFICATION_ID, createNotification())
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	companion object {
		private const val NOTIFICATION_ID = 4
	}
}
