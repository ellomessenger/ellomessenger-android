/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger

import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.telephony.TelephonyManager
import androidx.multidex.MultiDex
import org.telegram.messenger.voip.VideoCapturerDevice
import org.telegram.tgnet.ConnectionsManager
import org.telegram.ui.Components.ForegroundDetector
import org.telegram.ui.LauncherIconController
import java.io.File

class ApplicationLoader : Application() {
	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(base)
		MultiDex.install(this)
	}

	fun onCreateLocationServiceProvider(): ILocationServiceProvider {
		return GoogleLocationProvider()
	}

	fun onCreateMapsProvider(): IMapsProvider {
		return GoogleMapsProvider()
	}

	fun onCreatePushProvider(): PushListenerController.IPushListenerServiceProvider {
		return PushListenerController.GooglePushListenerServiceProvider.INSTANCE
	}

	fun onGetApplicationId(): String {
		return BuildConfig.APPLICATION_ID
	}

	override fun onCreate() {
		// StrictMode.setThreadPolicy(ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectAll().penaltyLog().build())
		// StrictMode.setVmPolicy(VmPolicy.Builder().detectLeakedSqlLiteObjects().detectLeakedClosableObjects().penaltyLog().build())

		runCatching {
			Companion.applicationContext = applicationContext
		}

		super.onCreate()

		Companion.applicationContext = applicationContext

		NativeLoader.initNativeLibs(Companion.applicationContext)

		ConnectionsManager.native_setJava(false)

		object : ForegroundDetector(this@ApplicationLoader) {
			override fun onActivityStarted(activity: Activity) {
				val wasInBackground = isBackground

				super.onActivityStarted(activity)

				if (wasInBackground) {
					ensureCurrentNetworkGet(true)
				}
			}
		}

		applicationHandler = Handler(Companion.applicationContext.mainLooper)

		AndroidUtilities.runOnUIThread {
			startPushService()
		}

		LauncherIconController.tryFixLauncherIconIfNeeded()
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)

		try {
			LocaleController.getInstance().onDeviceConfigurationChange(newConfig)
			AndroidUtilities.checkDisplaySize(Companion.applicationContext, newConfig)
			VideoCapturerDevice.checkScreenCapturerSize()
			AndroidUtilities.resetTabletFlag()
		}
		catch (e: Exception) {
			e.printStackTrace()
		}
	}

	private fun initPushServices() {
		AndroidUtilities.runOnUIThread({
			if (pushProvider?.hasServices() == true) {
				pushProvider?.onRequestPushToken()
			}
			else {
				SharedConfig.pushStringStatus = "__NO_GOOGLE_PLAY_SERVICES__"
				PushListenerController.sendRegistrationToServer(pushProvider!!.pushType, null)
			}
		}, 1000)
	}

//	private fun checkPlayServices(): Boolean {
//		try {
//			val resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)
//			return resultCode == ConnectionResult.SUCCESS
//		}
//		catch (e: Exception) {
//			FileLog.e(e)
//		}
//
//		return true
//	}

	init {
		applicationLoaderInstance = this
	}

	companion object {
		lateinit var applicationLoaderInstance: ApplicationLoader
		lateinit var applicationContext: Context
		private var connectivityManager: ConnectivityManager? = null
		private var lastNetworkCheckTypeTime: Long = 0
		private var lastKnownNetworkType = -1

		@Volatile
		var currentNetworkInfo: NetworkInfo? = null

		@JvmField
		@Volatile
		var applicationHandler: Handler? = null

		@Volatile
		private var applicationInited = false

		@Volatile
		private var networkCallback: ConnectivityManager.NetworkCallback? = null

		@JvmField
		@Volatile
		var isScreenOn = false

		@JvmField
		@Volatile
		var mainInterfacePaused = true

		@JvmField
		@Volatile
		var mainInterfaceStopped = true

		@JvmField
		@Volatile
		var externalInterfacePaused = true

		@Volatile
		var mainInterfacePausedStageQueue = true

		@JvmField
		var canDrawOverlays = false

		@Volatile
		var mainInterfacePausedStageQueueTime: Long = 0

		var pushProvider: PushListenerController.IPushListenerServiceProvider? = null
			get() {
				if (field == null) {
					field = applicationLoaderInstance.onCreatePushProvider()
				}

				return field
			}
			private set

		@JvmStatic
		val mapsProvider by lazy {
			applicationLoaderInstance.onCreateMapsProvider()
		}

		@JvmStatic
		val locationServiceProvider by lazy {
			applicationLoaderInstance.onCreateLocationServiceProvider().apply {
				init(applicationContext)
			}
		}

		@JvmStatic
		val applicationId by lazy {
			applicationLoaderInstance.onGetApplicationId()
		}

		@JvmStatic
		val filesDirFixed: File
			get() {
				for (a in 0..9) {
					val path = applicationContext.filesDir

					if (path != null) {
						return path
					}
				}

				try {
					val info = applicationContext.applicationInfo
					val path = File(info?.dataDir, "files")
					path.mkdirs()
					return path
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				return applicationContext.filesDir

				// return new File("/data/data/" + ApplicationLoader.getApplicationId() + "/files");
			}

		@JvmStatic
		fun postInitApplication() {
			if (applicationInited) {
				return
			}

			applicationInited = true

			try {
				LocaleController.getInstance() //TODO improve
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			try {
				connectivityManager = applicationContext.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager

				val networkStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
					override fun onReceive(context: Context, intent: Intent) {
						runCatching {
							currentNetworkInfo = connectivityManager?.activeNetworkInfo
						}

						val isSlow = isConnectionSlow

						for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
							ConnectionsManager.getInstance(a).checkConnection()
							FileLoader.getInstance(a).onNetworkChanged(isSlow)
						}
					}
				}

				val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)

				applicationContext.registerReceiver(networkStateReceiver, filter)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			try {
				val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
				filter.addAction(Intent.ACTION_SCREEN_OFF)

				val mReceiver: BroadcastReceiver = ScreenReceiver()

				applicationContext.registerReceiver(mReceiver, filter)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			try {
				val pm = applicationContext.getSystemService(POWER_SERVICE) as? PowerManager
				isScreenOn = pm?.isScreenOn ?: false
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			SharedConfig.loadConfig()
			SharedPrefsHelper.init(applicationContext)

			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) { //TODO improve account
				UserConfig.getInstance(a).loadConfig()

				MessagesController.getInstance(a)

				if (a == 0) {
					SharedConfig.pushStringStatus = "__FIREBASE_GENERATING_SINCE_" + ConnectionsManager.getInstance(a).currentTime + "__"
				}
				else {
					ConnectionsManager.getInstance(a)
				}

				val user = UserConfig.getInstance(a).getCurrentUser()

				if (user != null) {
					MessagesController.getInstance(a).putUser(user, true)
					SendMessagesHelper.getInstance(a).checkUnsentMessages()
				}
			}

			val app = applicationContext as? ApplicationLoader
			app?.initPushServices()

			MediaController.getInstance()

			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) { //TODO improve account
				ContactsController.getInstance(a).checkAppAccount()
				DownloadController.getInstance(a)
			}

			ChatThemeController.init()

			BillingController.getInstance().startConnection()
		}

		@JvmStatic
		fun startPushService() {
			val preferences = MessagesController.getGlobalNotificationsSettings()

			val enabled = if (preferences.contains("pushService")) {
				preferences.getBoolean("pushService", true)
			}
			else {
				MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("keepAliveService", false)
			}

			if (enabled) {
				runCatching {
					applicationContext.startService(Intent(applicationContext, NotificationsService::class.java))
				}
			}
			else {
				applicationContext.stopService(Intent(applicationContext, NotificationsService::class.java))

				val pintent = PendingIntent.getService(applicationContext, 0, Intent(applicationContext, NotificationsService::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

				val alarm = applicationContext.getSystemService(ALARM_SERVICE) as? AlarmManager
				alarm?.cancel(pintent)
			}
		}

		private fun ensureCurrentNetworkGet(force: Boolean) {
			if (force || currentNetworkInfo == null) {
				runCatching {
					if (connectivityManager == null) {
						connectivityManager = applicationContext.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
					}

					currentNetworkInfo = connectivityManager?.activeNetworkInfo

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
						if (networkCallback == null) {
							networkCallback = object : ConnectivityManager.NetworkCallback() {
								override fun onAvailable(network: Network) {
									lastKnownNetworkType = -1
								}

								override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
									lastKnownNetworkType = -1
								}
							}

							connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
						}
					}
				}
			}
		}

		val isRoaming: Boolean
			get() {
				try {
					ensureCurrentNetworkGet(false)
					return currentNetworkInfo?.isRoaming == true
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				return false
			}

		val isConnectedOrConnectingToWiFi: Boolean
			get() {
				try {
					ensureCurrentNetworkGet(false)

					if (currentNetworkInfo?.type == ConnectivityManager.TYPE_WIFI || currentNetworkInfo?.type == ConnectivityManager.TYPE_ETHERNET) {
						val state = currentNetworkInfo?.state

						if (state == NetworkInfo.State.CONNECTED || state == NetworkInfo.State.CONNECTING || state == NetworkInfo.State.SUSPENDED) {
							return true
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				return false
			}

		val isConnectedToWiFi: Boolean
			get() {
				try {
					ensureCurrentNetworkGet(false)

					if ((currentNetworkInfo?.type == ConnectivityManager.TYPE_WIFI || currentNetworkInfo?.type == ConnectivityManager.TYPE_ETHERNET) && currentNetworkInfo?.state == NetworkInfo.State.CONNECTED) {
						return true
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				return false
			}

		@JvmStatic
		val isConnectionSlow: Boolean
			get() {
				runCatching {
					ensureCurrentNetworkGet(false)

					if (currentNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE) {
						when (currentNetworkInfo?.subtype) {
							TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_IDEN -> return true
						}
					}
				}

				return false
			}

		@JvmStatic
		val autodownloadNetworkType: Int
			get() {
				try {
					ensureCurrentNetworkGet(false)

					if (currentNetworkInfo == null) {
						return StatsController.TYPE_MOBILE
					}

					if (currentNetworkInfo?.type == ConnectivityManager.TYPE_WIFI || currentNetworkInfo?.type == ConnectivityManager.TYPE_ETHERNET) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (lastKnownNetworkType == StatsController.TYPE_MOBILE || lastKnownNetworkType == StatsController.TYPE_WIFI) && System.currentTimeMillis() - lastNetworkCheckTypeTime < 5000) {
							return lastKnownNetworkType
						}

						lastKnownNetworkType = if (connectivityManager?.isActiveNetworkMetered == true) {
							StatsController.TYPE_MOBILE
						}
						else {
							StatsController.TYPE_WIFI
						}

						lastNetworkCheckTypeTime = System.currentTimeMillis()

						return lastKnownNetworkType
					}

					if (currentNetworkInfo?.isRoaming == true) {
						return StatsController.TYPE_ROAMING
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				return StatsController.TYPE_MOBILE
			}

		@JvmStatic
		val currentNetworkType: Int
			get() = if (isConnectedOrConnectingToWiFi) {
				StatsController.TYPE_WIFI
			}
			else if (isRoaming) {
				StatsController.TYPE_ROAMING
			}
			else {
				StatsController.TYPE_MOBILE
			}

		val isNetworkOnlineFast: Boolean
			get() {
				try {
					ensureCurrentNetworkGet(false)

					if (currentNetworkInfo == null) {
						return true
					}

					if (currentNetworkInfo?.isConnectedOrConnecting == true || currentNetworkInfo?.isAvailable == true) {
						return true
					}

					var netInfo = connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)

					if (netInfo != null && netInfo.isConnectedOrConnecting) {
						return true
					}
					else {
						netInfo = connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_WIFI)

						if (netInfo != null && netInfo.isConnectedOrConnecting) {
							return true
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
					return true
				}

				return false
			}

		private val isNetworkOnlineRealtime: Boolean
			get() {
				try {
					val connectivityManager = applicationContext.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager

					var netInfo = connectivityManager?.activeNetworkInfo

					if (netInfo != null && (netInfo.isConnectedOrConnecting || netInfo.isAvailable)) {
						return true
					}

					netInfo = connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)

					if (netInfo != null && netInfo.isConnectedOrConnecting) {
						return true
					}
					else {
						netInfo = connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_WIFI)

						if (netInfo != null && netInfo.isConnectedOrConnecting) {
							return true
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
					return true
				}

				return false
			}

		@JvmStatic
		val isNetworkOnline: Boolean
			get() {
				return isNetworkOnlineRealtime
			}
	}
}
