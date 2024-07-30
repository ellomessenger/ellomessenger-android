/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.tgnet

import android.app.Activity
import android.os.AsyncTask
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import androidx.annotation.Keep
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BaseController
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.BuildVars
import org.telegram.messenger.EmuDetector
import org.telegram.messenger.FileLog
import org.telegram.messenger.KeepAliveJob
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.PushListenerController
import org.telegram.messenger.PushListenerController.PushType
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.StatsController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.utils.getHostByNameSync
import org.telegram.tgnet.TLRPC.TL_error
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ConnectionsManager(instance: Int) : BaseController(instance) {
	var pauseTime = System.currentTimeMillis()
		private set

	private var appPaused = true
	private var isUpdating = false
	private var connectionState = native_getConnectionState(currentAccount)
	private val lastRequestToken = AtomicInteger(1)
	private var appResumeCount = 0
	private var forceTryIpV6: Boolean

	fun setForceTryIpV6(forceTryIpV6: Boolean) {
		if (this.forceTryIpV6 != forceTryIpV6) {
			this.forceTryIpV6 = forceTryIpV6
			checkConnection()
		}
	}

	data class ResolvedDomain(private val addresses: ArrayList<String>, val ttl: Long) {
		val address: String?
			get() = addresses[Utilities.random.nextInt(addresses.size)]
	}

	private val regId: String?
		get() {
			var pushString = SharedConfig.pushString

			if (pushString.isNullOrEmpty() && !SharedConfig.pushStringStatus.isNullOrEmpty()) {
				pushString = SharedConfig.pushStringStatus
			}

			if (pushString.isNullOrEmpty()) {
				val tag = if (SharedConfig.pushType == PushListenerController.PUSH_TYPE_FIREBASE) "FIREBASE" else ""
				SharedConfig.pushStringStatus = "__" + tag + "_GENERATING_SINCE_" + currentTime + "__"
				pushString = SharedConfig.pushStringStatus
			}

			return pushString
		}

	var isPushConnectionEnabled: Boolean
		get() {
			val preferences = MessagesController.getGlobalNotificationsSettings()

			return if (preferences.contains("pushConnection")) {
				preferences.getBoolean("pushConnection", true)
			}
			else {
				MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("backgroundConnection", false)
			}
		}
		set(value) {
			native_setPushConnectionEnabled(currentAccount, value)
		}

	val currentTimeMillis: Long
		get() = native_getCurrentTimeMillis(currentAccount)

	val currentTime: Int
		get() = native_getCurrentTime(currentAccount)

	val currentDatacenterId: Int
		get() = native_getCurrentDatacenterId(currentAccount)

	val timeDifference: Int
		get() = native_getTimeDifference(currentAccount)

	fun sendRequest(`object`: TLObject, completionBlock: RequestDelegate?, flags: Int): Int {
		return sendRequest(`object`, completionBlock, null, null, null, flags, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true)
	}

	fun sendRequest(`object`: TLObject, completionBlock: RequestDelegate?, flags: Int, connectionType: Int): Int {
		return sendRequest(`object`, completionBlock, null, null, null, flags, DEFAULT_DATACENTER_ID, connectionType, true)
	}

	fun sendRequest(`object`: TLObject, completionBlock: RequestDelegateTimestamp?, flags: Int, connectionType: Int, datacenterId: Int): Int {
		return sendRequest(`object`, null, completionBlock, null, null, flags, datacenterId, connectionType, true)
	}

	fun sendRequest(`object`: TLObject): Int {
		return sendRequest(`object`, null, null)
	}

	fun sendRequest(`object`: TLObject, completionBlock: RequestDelegate?): Int {
		return sendRequest(`object`, completionBlock, null)
	}

	fun sendRequest(`object`: TLObject, completionBlock: RequestDelegate?, quickAckBlock: QuickAckDelegate?): Int {
		return sendRequest(`object`, completionBlock, null, quickAckBlock, null, 0, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true)
	}

	fun sendRequest(`object`: TLObject, completionBlock: RequestDelegate?, quickAckBlock: QuickAckDelegate?, flags: Int): Int {
		return sendRequest(`object`, completionBlock, null, quickAckBlock, null, flags, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true)
	}

	fun sendRequest(`object`: TLObject, onComplete: RequestDelegate?, onQuickAck: QuickAckDelegate?, onWriteToSocket: WriteToSocketDelegate?, flags: Int, datacenterId: Int, connectionType: Int, immediate: Boolean): Int {
		return sendRequest(`object`, onComplete, null, onQuickAck, onWriteToSocket, flags, datacenterId, connectionType, immediate)
	}

	init {
		var deviceModel: String
		var systemLangCode: String
		var langCode: String
		var appVersion: String
		var systemVersion: String
		var config = ApplicationLoader.filesDirFixed

		if (instance != 0) {
			config = File(config, "account$instance")
			config.mkdirs()
		}

		val configPath = config.toString()
		val enablePushConnection = isPushConnectionEnabled

		try {
			systemLangCode = LocaleController.getSystemLocaleStringIso639().lowercase(Locale.getDefault()).trim()
			langCode = LocaleController.getLocaleStringIso639().lowercase(Locale.getDefault())
			deviceModel = (Build.MANUFACTURER + Build.MODEL).trim()

			val pInfo = ApplicationLoader.applicationContext.packageManager.getPackageInfo(ApplicationLoader.applicationContext.packageName, 0)

			appVersion = pInfo.versionName + " (" + pInfo.versionCode + ")"

			if (BuildConfig.DEBUG_PRIVATE_VERSION) {
				appVersion += " pbeta"
			}
			else if (BuildConfig.DEBUG) {
				appVersion += " beta"
			}

			systemVersion = "SDK " + Build.VERSION.SDK_INT
		}
		catch (e: Exception) {
			systemLangCode = "en"
			langCode = ""
			deviceModel = "Android unknown"
			appVersion = "App version unknown"
			systemVersion = "SDK " + Build.VERSION.SDK_INT
		}

		if (systemLangCode.isEmpty()) {
			systemLangCode = "en"
		}

		if (deviceModel.isEmpty()) {
			deviceModel = "Android unknown"
		}

		if (appVersion.isEmpty()) {
			appVersion = "App version unknown"
		}

		if (systemVersion.isEmpty()) {
			systemVersion = "SDK Unknown"
		}

		userConfig.loadConfig()

		val pushString = regId
		val fingerprint = AndroidUtilities.getCertificateSHA256Fingerprint()
		val timezoneOffset = (TimeZone.getDefault().rawOffset + TimeZone.getDefault().dstSavings) / 1000

		val mainPreferences = if (currentAccount == 0) {
			ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
		}
		else {
			ApplicationLoader.applicationContext.getSharedPreferences("mainconfig$currentAccount", Activity.MODE_PRIVATE)
		}

		forceTryIpV6 = mainPreferences.getBoolean("forceTryIpV6", false)

		init(BuildConfig.VERSION_CODE, TLRPC.LAYER, BuildVars.APP_ID, deviceModel, systemVersion, appVersion, langCode, systemLangCode, configPath, FileLog.networkLogPath, pushString, fingerprint, timezoneOffset, userConfig.getClientUserId(), enablePushConnection)
	}

	suspend fun performRequest(`object`: TLObject, flags: Int = RequestFlagFailOnServerErrors): TLObject? = suspendCancellableCoroutine {
		var requestId = -1

		requestId = sendRequest(`object`, { response, error ->
			requestId = -1

			if (response != null) {
				it.resume(response)
			}
			else {
				it.resume(error)
			}
		}, flags)

		it.invokeOnCancellation {
			if (requestId != -1) {
				cancelRequest(requestId, true)
			}
		}
	}

	fun sendRequest(`object`: TLObject, onComplete: RequestDelegate?, onCompleteTimestamp: RequestDelegateTimestamp?, onQuickAck: QuickAckDelegate?, onWriteToSocket: WriteToSocketDelegate?, flags: Int, datacenterId: Int, connectionType: Int, immediate: Boolean): Int {
		val requestToken = lastRequestToken.getAndIncrement()

		Utilities.stageQueue.postRunnable {
			FileLog.d("send request $`object` with token = $requestToken")

			try {
				val buffer = NativeByteBuffer(`object`.objectSize)
				`object`.serializeToStream(buffer)
				`object`.freeResources()

				native_sendRequest(currentAccount, buffer.address, { response, errorCode, errorText, networkType, timestamp ->
					try {
						var resp: TLObject? = null
						var error: TL_error? = null

						if (response != 0L) {
							val buff = NativeByteBuffer.wrap(response)

							if (buff != null) {
								buff.reused = true

								resp = `object`.deserializeResponse(buff, buff.readInt32(true), true)
							}
						}
						else if (errorText != null) {
							error = TL_error()
							error.code = errorCode
							error.text = errorText
							FileLog.e(`object`.toString() + " got error " + error.code + " " + error.text)
						}

						if (BuildConfig.DEBUG_PRIVATE_VERSION && !userConfig.isClientActivated && error?.code == 400 && error.text == "CONNECTION_NOT_INITED") {
							FileLog.d("Cleanup keys for $currentAccount because of CONNECTION_NOT_INITED")
							cleanup(true)
							sendRequest(`object`, onComplete, onCompleteTimestamp, onQuickAck, onWriteToSocket, flags, datacenterId, connectionType, immediate)
							return@native_sendRequest
						}

						resp?.networkType = networkType

						FileLog.d("java received $resp error = $error")

						Utilities.stageQueue.postRunnable {
							onComplete?.run(resp, error) ?: onCompleteTimestamp?.run(resp, error, timestamp)
							resp?.freeResources()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}, onQuickAck, onWriteToSocket, flags, datacenterId, connectionType, immediate, requestToken)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		return requestToken
	}

	fun cancelRequest(token: Int, notifyServer: Boolean) {
		native_cancelRequest(currentAccount, token, notifyServer)
	}

	fun cleanup(resetKeys: Boolean) {
		native_cleanUp(currentAccount, resetKeys)
	}

	fun cancelRequestsForGuid(guid: Int) {
		native_cancelRequestsForGuid(currentAccount, guid)
	}

	fun bindRequestToGuid(requestToken: Int, guid: Int) {
		native_bindRequestToGuid(currentAccount, requestToken, guid)
	}

	fun applyDatacenterAddress(datacenterId: Int, ipAddress: String?, port: Int) {
		native_applyDatacenterAddress(currentAccount, datacenterId, ipAddress, port)
	}

	fun getConnectionState(): Int {
		return if (connectionState == ConnectionStateConnected && isUpdating) {
			ConnectionStateUpdating
		}
		else {
			connectionState
		}
	}

	fun setUserId(id: Long) {
		native_setUserId(currentAccount, id)
	}

	fun checkConnection() {
		native_setIpStrategy(currentAccount, ipStrategy)
		native_setNetworkAvailable(currentAccount, ApplicationLoader.isNetworkOnline, ApplicationLoader.currentNetworkType, ApplicationLoader.isConnectionSlow)
	}

	fun init(version: Int, layer: Int, apiId: Int, deviceModel: String?, systemVersion: String?, appVersion: String?, langCode: String?, systemLangCode: String?, configPath: String?, logPath: String?, regId: String?, cFingerprint: String?, timezoneOffset: Int, userId: Long, enablePushConnection: Boolean) {
		val preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
		val proxyAddress = preferences.getString("proxy_ip", "")
		val proxyUsername = preferences.getString("proxy_user", "")
		val proxyPassword = preferences.getString("proxy_pass", "")
		val proxySecret = preferences.getString("proxy_secret", "")
		val proxyPort = preferences.getInt("proxy_port", 1080)

		if (preferences.getBoolean("proxy_enabled", false) && !proxyAddress.isNullOrEmpty()) {
			native_setProxySettings(currentAccount, proxyAddress, proxyPort, proxyUsername, proxyPassword, proxySecret)
		}

		val installer = runCatching {
			ApplicationLoader.applicationContext.packageManager.getInstallerPackageName(ApplicationLoader.applicationContext.packageName)
		}.getOrNull() ?: ""

		val packageId = runCatching {
			ApplicationLoader.applicationContext.packageName
		}.getOrNull() ?: ""

		native_init(currentAccount, version, layer, apiId, deviceModel, systemVersion, appVersion, langCode, systemLangCode, configPath, logPath, regId, cFingerprint, installer, packageId, timezoneOffset, userId, enablePushConnection, ApplicationLoader.isNetworkOnline, ApplicationLoader.currentNetworkType)

		checkConnection()
	}

	fun resumeNetworkMaybe() {
		native_resumeNetwork(currentAccount, true)
	}

	fun updateDcSettings() {
		native_updateDcSettings(currentAccount)
	}

	fun checkProxy(address: String?, port: Int, username: String?, password: String?, secret: String?, requestTimeDelegate: RequestTimeDelegate?): Long {
		if (address.isNullOrEmpty()) {
			return 0
		}

		return native_checkProxy(currentAccount, address, port, username ?: "", password ?: "", secret ?: "", requestTimeDelegate)
	}

	fun setAppPaused(value: Boolean, byScreenState: Boolean) {
		if (!byScreenState) {
			appPaused = value

			FileLog.d("app paused = $value")

			if (value) {
				appResumeCount--
			}
			else {
				appResumeCount++
			}

			FileLog.d("app resume count $appResumeCount")

			if (appResumeCount < 0) {
				appResumeCount = 0
			}
		}

		if (appResumeCount == 0) {
			if (pauseTime == 0L) {
				pauseTime = System.currentTimeMillis()
			}

			native_pauseNetwork(currentAccount)
		}
		else {
			if (appPaused) {
				return
			}

			FileLog.d("reset app pause time")

			pauseTime = 0

			native_resumeNetwork(currentAccount, false)
		}
	}

	fun setIsUpdating(value: Boolean) {
		AndroidUtilities.runOnUIThread {
			if (isUpdating == value) {
				return@runOnUIThread
			}

			isUpdating = value

			if (connectionState == ConnectionStateConnected) {
				AccountInstance.getInstance(currentAccount).notificationCenter.postNotificationName(NotificationCenter.didUpdateConnectionState)
			}
		}
	}

	private val ipStrategy: Byte
		get() {
			if (BuildConfig.DEBUG) {
				try {
					var networkInterface: NetworkInterface
					val networkInterfaces = NetworkInterface.getNetworkInterfaces()

					while (networkInterfaces.hasMoreElements()) {
						networkInterface = networkInterfaces.nextElement()

						if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.interfaceAddresses.isEmpty()) {
							continue
						}

						FileLog.d("valid interface: $networkInterface")

						val interfaceAddresses = networkInterface.interfaceAddresses

						for (a in interfaceAddresses.indices) {
							val address = interfaceAddresses[a]
							val inetAddress = address.address

							FileLog.d("address: " + inetAddress.hostAddress)

							if (inetAddress.isLinkLocalAddress || inetAddress.isLoopbackAddress || inetAddress.isMulticastAddress) {
								continue
							}

							FileLog.d("address is good")
						}
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}

			try {
				var networkInterface: NetworkInterface
				val networkInterfaces = NetworkInterface.getNetworkInterfaces()
				var hasIpv4 = false
				var hasIpv6 = false
				var hasStrangeIpv4 = false

				while (networkInterfaces.hasMoreElements()) {
					networkInterface = networkInterfaces.nextElement()

					if (!networkInterface.isUp || networkInterface.isLoopback) {
						continue
					}

					val interfaceAddresses = networkInterface.interfaceAddresses

					for (a in interfaceAddresses.indices) {
						val address = interfaceAddresses[a]
						val inetAddress = address.address

						if (inetAddress.isLinkLocalAddress || inetAddress.isLoopbackAddress || inetAddress.isMulticastAddress) {
							continue
						}

						if (inetAddress is Inet6Address) {
							hasIpv6 = true
						}
						else if (inetAddress is Inet4Address) {
							val addr = inetAddress.getHostAddress()

							if (addr?.startsWith("192.0.0.") != true) {
								hasIpv4 = true
							}
							else {
								hasStrangeIpv4 = true
							}
						}
					}
				}

				if (hasIpv6) {
					if (forceTryIpV6) {
						return USE_IPV6_ONLY
					}

					if (hasStrangeIpv4) {
						return USE_IPV4_IPV6_RANDOM
					}

					if (!hasIpv4) {
						return USE_IPV6_ONLY
					}
				}
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}

			return USE_IPV4_ONLY
		}

	class ResolveHostByNameSync(private val currentHostName: String) {
		fun resolve(): ResolvedDomain? {
			val result = resolveImpl()

			if (result != null) {
				dnsCache[currentHostName] = result
			}

			resolvingHostnameTasks.remove(currentHostName)

			return result
		}

		private fun resolveImpl(): ResolvedDomain? {
			var done = false

			try {
				val downloadUrl = URL("https://dns.google/resolve?name=$currentHostName&type=A")

				val httpConnection = downloadUrl.openConnection()
				httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1")
				httpConnection.addRequestProperty("Host", "dns.google")
				httpConnection.connectTimeout = 1000
				httpConnection.readTimeout = 2000
				httpConnection.connect()

				var result: String?

				httpConnection.getInputStream().use { httpConnectionStream ->
					ByteArrayOutputStream().use {
						val data = ByteArray(1024 * 32)

						while (true) {
							val read = httpConnectionStream.read(data)

							if (read > 0) {
								it.write(data, 0, read)
							}
							else if (read == -1) {
								break
							}
							else {
								break
							}
						}

						result = it.toString()
					}
				}

				result?.takeIf { it.isNotEmpty() }?.let {
					val jsonObject = JSONObject(it)

					if (jsonObject.has("Answer")) {
						val array = jsonObject.getJSONArray("Answer")
						val len = array.length()

						if (len > 0) {
							val addresses = ArrayList<String>(len)

							for (a in 0 until len) {
								addresses.add(array.getJSONObject(a).getString("data"))
							}

							return ResolvedDomain(addresses, SystemClock.elapsedRealtime())
						}
					}

					done = true
				}
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}

			if (!done) {
				try {
					val addresses = ArrayList<String>(1)

					InetAddress.getByName(currentHostName).hostAddress?.let {
						addresses.add(it)
					}

					return ResolvedDomain(addresses, SystemClock.elapsedRealtime())
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			return null
		}
	}

	class ResolveHostByNameTask(private val currentHostName: String) : AsyncTask<Void?, Void?, ResolvedDomain?>() {
		private val addresses = ArrayList<Long>()

		fun addAddress(address: Long) {
			if (addresses.contains(address)) {
				return
			}

			addresses.add(address)
		}

		override fun doInBackground(vararg voids: Void?): ResolvedDomain? {
			var done = false

			try {
				val downloadUrl = URL("https://dns.google/resolve?name=$currentHostName&type=A")
				val httpConnection = downloadUrl.openConnection()
				httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1")
				httpConnection.addRequestProperty("Host", "dns.google")
				httpConnection.connectTimeout = 1000
				httpConnection.readTimeout = 2000
				httpConnection.connect()

				var result: String?

				httpConnection.getInputStream().use { httpConnectionStream ->
					ByteArrayOutputStream().use {
						val data = ByteArray(1024 * 32)

						while (true) {
							val read = httpConnectionStream.read(data)

							if (read > 0) {
								it.write(data, 0, read)
							}
							else if (read == -1) {
								break
							}
							else {
								break
							}
						}

						result = it.toString()
					}
				}

				result?.takeIf { it.isNotEmpty() }?.let {
					val jsonObject = JSONObject(it)

					if (jsonObject.has("Answer")) {
						val array = jsonObject.getJSONArray("Answer")
						val len = array.length()

						if (len > 0) {
							val addresses = ArrayList<String>(len)

							for (a in 0 until len) {
								addresses.add(array.getJSONObject(a).getString("data"))
							}

							return ResolvedDomain(addresses, SystemClock.elapsedRealtime())
						}
					}

					done = true
				}
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}

			if (!done) {
				try {
					val addresses = ArrayList<String>(1)

					InetAddress.getByName(currentHostName).hostAddress?.let {
						addresses.add(it)
					}

					return ResolvedDomain(addresses, SystemClock.elapsedRealtime())
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			return null
		}

		override fun onPostExecute(result: ResolvedDomain?) {
			if (result != null) {
				dnsCache[currentHostName] = result

				for (address in addresses) {
					native_onHostNameResolved(currentHostName, address, result.address)
				}
			}
			else {
				for (address in addresses) {
					native_onHostNameResolved(currentHostName, address, "")
				}
			}

			resolvingHostnameTasks.remove(currentHostName)
		}
	}

	private class DnsTxtLoadTask(private val currentAccount: Int) : AsyncTask<Void?, Void?, NativeByteBuffer?>() {
		private var responseDate = 0

		override fun doInBackground(vararg voids: Void?): NativeByteBuffer? {
			try {
				val domain = AccountInstance.getInstance(currentAccount).messagesController.dcDomainName
				var len = Utilities.random.nextInt(116) + 13
				val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
				val padding = StringBuilder(len)

				for (a in 0 until len) {
					padding.append(characters[Utilities.random.nextInt(characters.length)])
				}

				val downloadUrl = URL("https://dns.google/resolve?name=$domain&type=ANY&random_padding=$padding")

				val httpConnection = downloadUrl.openConnection()
				httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1")
				httpConnection.addRequestProperty("Host", "dns.google")
				httpConnection.connectTimeout = 5000
				httpConnection.readTimeout = 5000
				httpConnection.connect()

				var result: String?

				httpConnection.getInputStream().use { httpConnectionStream ->
					responseDate = (httpConnection.date / 1000).toInt()

					ByteArrayOutputStream().use {
						val data = ByteArray(1024 * 32)

						while (true) {
							if (isCancelled) {
								break
							}

							val read = httpConnectionStream.read(data)

							if (read > 0) {
								it.write(data, 0, read)
							}
							else if (read == -1) {
								break
							}
							else {
								break
							}
						}

						result = it.toString()
					}
				}

				result?.takeIf { it.isNotEmpty() }?.let {
					val jsonObject = JSONObject(it)
					val array = jsonObject.getJSONArray("Answer")

					len = array.length()

					val arrayList = ArrayList<String>(len)

					for (a in 0 until len) {
						val `object` = array.getJSONObject(a)
						val type = `object`.getInt("type")

						if (type != 16) {
							continue
						}

						arrayList.add(`object`.getString("data"))
					}

					arrayList.sortWith { o1, o2 ->
						val l1 = o1.length
						val l2 = o2.length

						if (l1 > l2) {
							return@sortWith -1
						}
						else if (l1 < l2) {
							return@sortWith 1
						}

						0
					}

					val builder = StringBuilder()

					for (a in arrayList.indices) {
						builder.append(arrayList[a].replace("\"", ""))
					}

					val bytes = Base64.decode(builder.toString(), Base64.DEFAULT)
					val buffer = NativeByteBuffer(bytes.size)
					buffer.writeBytes(bytes)
					return buffer
				}
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}

			return null
		}

		override fun onPostExecute(result: NativeByteBuffer?) {
			Utilities.stageQueue.postRunnable {
				currentTask = null

				if (result != null) {
					native_applyDnsConfig(currentAccount, result.address, "", responseDate)
				}
				else {
					FileLog.d("failed to get dns txt result")
					FileLog.d("start google task")

					val task = GoogleDnsLoadTask(currentAccount)
					task.executeOnExecutor(THREAD_POOL_EXECUTOR, null, null, null)

					currentTask = task
				}
			}
		}
	}

	private class GoogleDnsLoadTask(private val currentAccount: Int) : AsyncTask<Void?, Void?, NativeByteBuffer?>() {
		private var responseDate = 0

		override fun doInBackground(vararg voids: Void?): NativeByteBuffer? {
			try {
				val domain = AccountInstance.getInstance(currentAccount).messagesController.dcDomainName
				var len = Utilities.random.nextInt(116) + 13
				val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
				val padding = StringBuilder(len)

				for (a in 0 until len) {
					padding.append(characters[Utilities.random.nextInt(characters.length)])
				}

				val downloadUrl = URL("https://dns.google/resolve?name=$domain&type=ANY&random_padding=$padding")

				val httpConnection = downloadUrl.openConnection()
				httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1")
				httpConnection.connectTimeout = 5000
				httpConnection.readTimeout = 5000
				httpConnection.connect()

				var result: String?

				httpConnection.getInputStream().use { httpConnectionStream ->
					responseDate = (httpConnection.date / 1000).toInt()

					ByteArrayOutputStream().use {
						val data = ByteArray(1024 * 32)

						while (true) {
							if (isCancelled) {
								break
							}

							val read = httpConnectionStream.read(data)

							if (read > 0) {
								it.write(data, 0, read)
							}
							else if (read == -1) {
								break
							}
							else {
								break
							}
						}

						result = it.toString()
					}
				}

				result?.takeIf { it.isNotEmpty() }?.let {
					val jsonObject = JSONObject(it)
					val array = jsonObject.getJSONArray("Answer")

					len = array.length()

					val arrayList = ArrayList<String>(len)

					for (a in 0 until len) {
						val `object` = array.getJSONObject(a)
						val type = `object`.getInt("type")

						if (type != 16) {
							continue
						}

						arrayList.add(`object`.getString("data"))
					}

					arrayList.sortWith { o1, o2 ->
						val l1 = o1.length
						val l2 = o2.length

						if (l1 > l2) {
							return@sortWith -1
						}
						else if (l1 < l2) {
							return@sortWith 1
						}

						0
					}

					val builder = StringBuilder()

					for (a in arrayList.indices) {
						builder.append(arrayList[a].replace("\"", ""))
					}

					val bytes = Base64.decode(builder.toString(), Base64.DEFAULT)

					val buffer = NativeByteBuffer(bytes.size)
					buffer.writeBytes(bytes)

					return buffer
				}
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}

			return null
		}

		override fun onPostExecute(result: NativeByteBuffer?) {
			Utilities.stageQueue.postRunnable {
				currentTask = null

				if (result != null) {
					native_applyDnsConfig(currentAccount, result.address, "" /* AccountInstance.getInstance(currentAccount).getUserConfig().getClientPhone() */, responseDate)
				}
				else {
					FileLog.d("failed to get google result")
					FileLog.d("start mozilla task")

					val task = MozillaDnsLoadTask(currentAccount)
					task.executeOnExecutor(THREAD_POOL_EXECUTOR, null, null, null)

					currentTask = task
				}
			}
		}
	}

	private class MozillaDnsLoadTask(private val currentAccount: Int) : AsyncTask<Void?, Void?, NativeByteBuffer?>() {
		private var responseDate = 0

		override fun doInBackground(vararg voids: Void?): NativeByteBuffer? {
			try {
				val domain = AccountInstance.getInstance(currentAccount).messagesController.dcDomainName
				var len = Utilities.random.nextInt(116) + 13
				val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
				val padding = StringBuilder(len)

				for (a in 0 until len) {
					padding.append(characters[Utilities.random.nextInt(characters.length)])
				}

				val downloadUrl = URL("https://mozilla.cloudflare-dns.com/dns-query?name=$domain&type=TXT&random_padding=$padding")

				val httpConnection = downloadUrl.openConnection()
				httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1")
				httpConnection.addRequestProperty("accept", "application/dns-json")
				httpConnection.connectTimeout = 5000
				httpConnection.readTimeout = 5000
				httpConnection.connect()

				var result: String?

				httpConnection.getInputStream().use { httpConnectionStream ->
					responseDate = (httpConnection.date / 1000).toInt()

					ByteArrayOutputStream().use {
						val data = ByteArray(1024 * 32)

						while (true) {
							if (isCancelled) {
								break
							}

							val read = httpConnectionStream.read(data)

							if (read > 0) {
								it.write(data, 0, read)
							}
							else if (read == -1) {
								break
							}
							else {
								break
							}
						}

						result = it.toString()
					}
				}

				result?.takeIf { it.isNotEmpty() }?.let {
					val jsonObject = JSONObject(it)
					val array = jsonObject.getJSONArray("Answer")

					len = array.length()

					val arrayList = ArrayList<String>(len)

					for (a in 0 until len) {
						val `object` = array.getJSONObject(a)
						val type = `object`.getInt("type")

						if (type != 16) {
							continue
						}

						arrayList.add(`object`.getString("data"))
					}

					arrayList.sortWith { o1, o2 ->
						val l1 = o1.length
						val l2 = o2.length

						if (l1 > l2) {
							return@sortWith -1
						}
						else if (l1 < l2) {
							return@sortWith 1
						}

						0
					}

					val builder = StringBuilder()

					for (a in arrayList.indices) {
						builder.append(arrayList[a].replace("\"", ""))
					}

					val bytes = Base64.decode(builder.toString(), Base64.DEFAULT)

					val buffer = NativeByteBuffer(bytes.size)
					buffer.writeBytes(bytes)

					return buffer
				}
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}

			return null
		}

		override fun onPostExecute(result: NativeByteBuffer?) {
			Utilities.stageQueue.postRunnable {
				currentTask = null

				if (result != null) {
					native_applyDnsConfig(currentAccount, result.address, "", responseDate)
				}
				else {
					FileLog.d("failed to get mozilla txt result")
				}
			}
		}
	}

	private class FirebaseTask(private val currentAccount: Int) : AsyncTask<Void?, Void?, NativeByteBuffer?>() {
		override fun doInBackground(vararg voids: Void?): NativeByteBuffer? {
			try {
				val firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

				val currentValue = firebaseRemoteConfig.getString("ipconfigv3")

				FileLog.d("current firebase value = $currentValue")

				firebaseRemoteConfig.fetch(0).addOnCompleteListener { finishedTask ->
					val success = finishedTask.isSuccessful

					Utilities.stageQueue.postRunnable {
						if (success) {
							firebaseRemoteConfig.activate().addOnCompleteListener {
								currentTask = null

								val config = firebaseRemoteConfig.getString("ipconfigv3")

								if (config.isNotEmpty()) {
									val bytes = Base64.decode(config, Base64.DEFAULT)

									try {
										val buffer = NativeByteBuffer(bytes.size)
										buffer.writeBytes(bytes)

										val date = (firebaseRemoteConfig.info.fetchTimeMillis / 1000).toInt()

										native_applyDnsConfig(currentAccount, buffer.address, "", date)
									}
									catch (e: Exception) {
										FileLog.e(e)
									}
								}
								else {
									FileLog.d("failed to get firebase result")
									FileLog.d("start dns txt task")

									val task = DnsTxtLoadTask(currentAccount)
									task.executeOnExecutor(THREAD_POOL_EXECUTOR, null, null, null)

									currentTask = task
								}
							}
						}
					}
				}
			}
			catch (e: Throwable) {
				Utilities.stageQueue.postRunnable {
					FileLog.d("failed to get firebase result")
					FileLog.d("start dns txt task")

					val task = DnsTxtLoadTask(currentAccount)
					task.executeOnExecutor(THREAD_POOL_EXECUTOR, null, null, null)

					currentTask = task
				}

				FileLog.e(e)
			}

			return null
		}

		override fun onPostExecute(result: NativeByteBuffer?) {
			// unused
		}
	}

	companion object {
		@JvmField
		val CPU_COUNT = Runtime.getRuntime().availableProcessors()

		const val ConnectionStateConnected = 3
		const val ConnectionStateConnecting = 1
		const val ConnectionStateConnectingToProxy = 4
		const val ConnectionStateUpdating = 5
		const val ConnectionStateWaitingForNetwork = 2
		const val ConnectionTypeDownload = 2
		const val ConnectionTypeDownload2 = ConnectionTypeDownload or (1 shl 16)
		const val ConnectionTypeGeneric = 1
		const val ConnectionTypePush = 8
		const val ConnectionTypeUpload = 4
		const val DEFAULT_DATACENTER_ID = Int.MAX_VALUE
		const val FileTypeAudio = 0x03000000
		const val FileTypeFile = 0x04000000
		const val FileTypePhoto = 0x01000000
		const val FileTypeVideo = 0x02000000
		const val RequestFlagCanCompress = 4
		const val RequestFlagEnableUnauthorized = 1
		const val RequestFlagFailOnServerErrors = 2
		const val RequestFlagForceDownload = 32
		const val RequestFlagInvokeAfter = 64
		const val RequestFlagNeedQuickAck = 128
		const val RequestFlagTryDifferentDc = 16
		const val RequestFlagWithoutLogin = 8
		const val USE_IPV4_IPV6_RANDOM: Byte = 2
		const val USE_IPV4_ONLY: Byte = 0
		const val USE_IPV6_ONLY: Byte = 1
		private const val KEEP_ALIVE_SECONDS = 30
		private val CORE_POOL_SIZE = max(2, min(CPU_COUNT - 1, 4))
		private val MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1
		private val instances = arrayOfNulls<ConnectionsManager>(UserConfig.MAX_ACCOUNT_COUNT)
		private val sPoolWorkQueue: BlockingQueue<Runnable> = LinkedBlockingQueue(128)
		private var DNS_THREAD_POOL_EXECUTOR: Executor? = null
		private var currentTask: AsyncTask<*, *, *>? = null
		private var lastClassGuid = 1
		private var lastDnsRequestTime: Long = 0
		val dnsCache = HashMap<String, ResolvedDomain>()
		val resolvingHostnameTasks = HashMap<String, ResolveHostByNameTask>()

		private val sThreadFactory = object : ThreadFactory {
			private val mCount = AtomicInteger(1)

			override fun newThread(r: Runnable): Thread {
				return Thread(r, "DnsAsyncTask #" + mCount.getAndIncrement())
			}
		}

		init {
			val threadPoolExecutor = ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS.toLong(), TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory)
			threadPoolExecutor.allowCoreThreadTimeOut(true)
			DNS_THREAD_POOL_EXECUTOR = threadPoolExecutor
		}

		@Synchronized
		@JvmStatic
		fun getInstance(num: Int): ConnectionsManager {
			var localInstance = instances[num]

			if (localInstance == null) {
				synchronized(ConnectionsManager::class.java) {
					localInstance = instances[num]

					if (localInstance == null) {
						localInstance = ConnectionsManager(num)
						instances[num] = localInstance
					}
				}
			}

			return localInstance!!
		}

		@JvmStatic
		fun setLangCode(langCode: String) {
			@Suppress("NAME_SHADOWING") var langCode = langCode

			langCode = langCode.replace('_', '-').lowercase(Locale.getDefault())

			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				native_setLangCode(a, langCode)
			}
		}

		fun setRegId(regId: String?, @PushType type: Int, status: String?) {
			var pushString = regId

			if (pushString.isNullOrEmpty() && !status.isNullOrEmpty()) {
				pushString = status
			}

			if (pushString.isNullOrEmpty()) {
				val tag = if (type == PushListenerController.PUSH_TYPE_FIREBASE) "FIREBASE" else ""
				SharedConfig.pushStringStatus = "__" + tag + "_GENERATING_SINCE_" + getInstance(0).currentTime + "__"
				pushString = SharedConfig.pushStringStatus
			}

			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				native_setRegId(a, pushString)
			}
		}

		@JvmStatic
		fun setSystemLangCode(langCode: String) {
			@Suppress("NAME_SHADOWING") var langCode = langCode

			langCode = langCode.replace('_', '-').lowercase(Locale.getDefault())

			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				native_setSystemLangCode(a, langCode)
			}
		}

		@Keep
		@JvmStatic
		fun onUnparsedMessageReceived(address: Long, currentAccount: Int) {
			try {
				val buff = NativeByteBuffer.wrap(address) ?: return
				buff.reused = true

				val constructor = buff.readInt32(true)
				val message = TLClassStore.Instance().TLdeserialize(buff, constructor, true)

				if (message is Updates) {
					FileLog.d("java received $message")

					KeepAliveJob.finishJob()

					Utilities.stageQueue.postRunnable {
						AccountInstance.getInstance(currentAccount).messagesController.processUpdates(message, false)
					}
				}
				else {
					FileLog.d(String.format("java received unknown constructor 0x%x", constructor))
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		@Keep
		@JvmStatic
		fun onUpdate(currentAccount: Int) {
			Utilities.stageQueue.postRunnable {
				AccountInstance.getInstance(currentAccount).messagesController.updateTimerProc()
			}
		}

		@Keep
		@JvmStatic
		fun onSessionCreated(currentAccount: Int) {
			Utilities.stageQueue.postRunnable {
				AccountInstance.getInstance(currentAccount).messagesController.getDifference()
			}
		}

		@Keep
		@JvmStatic
		fun onConnectionStateChanged(state: Int, currentAccount: Int) {
			AndroidUtilities.runOnUIThread {
				getInstance(currentAccount).connectionState = state
				AccountInstance.getInstance(currentAccount).notificationCenter.postNotificationName(NotificationCenter.didUpdateConnectionState)
			}
		}

		@Keep
		@JvmStatic
		fun onLogout(currentAccount: Int) {
			AndroidUtilities.runOnUIThread {
				val accountInstance = AccountInstance.getInstance(currentAccount)

				if (accountInstance.userConfig.getClientUserId() != 0L) {
					accountInstance.userConfig.clearConfig()
					accountInstance.messagesController.performLogout(0)
				}
			}
		}

		@Keep
		@JvmStatic
		fun getInitFlags(): Int {
			var flags = 0
			val detector = EmuDetector.with(ApplicationLoader.applicationContext)

			if (detector.detect()) {
				FileLog.d("detected emu")

				flags = flags or 1024
			}

			return flags
		}

		@Keep
		@JvmStatic
		fun onBytesSent(amount: Int, networkType: Int, currentAccount: Int) {
			try {
				AccountInstance.getInstance(currentAccount).statsController.incrementSentBytesCount(networkType, StatsController.TYPE_TOTAL, amount.toLong())
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		@Keep
		@JvmStatic
		fun onRequestNewServerIpAndPort(second: Int, currentAccount: Int) {
			Utilities.globalQueue.postRunnable outer@{
				val networkOnline = ApplicationLoader.isNetworkOnline

				Utilities.stageQueue.postRunnable {
					if (currentTask != null || second == 0 && abs(lastDnsRequestTime - System.currentTimeMillis()) < 10000 || !networkOnline) {
						FileLog.d("don't start task, current task = " + currentTask + " next task = " + second + " time diff = " + abs(lastDnsRequestTime - System.currentTimeMillis()) + " network = " + ApplicationLoader.isNetworkOnline)
						return@postRunnable
					}

					lastDnsRequestTime = System.currentTimeMillis()

					when (second) {
						3 -> {
							FileLog.d("start mozilla txt task")
							val task = MozillaDnsLoadTask(currentAccount)
							task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null)
							currentTask = task
						}

						2 -> {
							FileLog.d("start google txt task")
							val task = GoogleDnsLoadTask(currentAccount)
							task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null)
							currentTask = task
						}

						1 -> {
							FileLog.d("start dns txt task")
							val task = DnsTxtLoadTask(currentAccount)
							task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null)
							currentTask = task
						}

						else -> {
							FileLog.d("start firebase task")
							val task = FirebaseTask(currentAccount)
							task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null)
							currentTask = task
						}
					}
				}
			}
		}

		@Keep
		@JvmStatic
		fun onProxyError() {
			AndroidUtilities.runOnUIThread {
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.needShowAlert, 3)
			}
		}

		@Keep
		@JvmStatic
		fun getHostByNameSync(hostName: String): String {
			return hostName.getHostByNameSync()
		}

		@Keep
		@JvmStatic
		fun getHostByName(hostName: String, address: Long) {
			AndroidUtilities.runOnUIThread {
				val resolvedDomain = dnsCache[hostName]

				if (resolvedDomain != null && SystemClock.elapsedRealtime() - resolvedDomain.ttl < 5 * 60 * 1000) {
					native_onHostNameResolved(hostName, address, resolvedDomain.address)
				}
				else {
					var task = resolvingHostnameTasks[hostName]

					if (task == null) {
						task = ResolveHostByNameTask(hostName)

						try {
							task.executeOnExecutor(DNS_THREAD_POOL_EXECUTOR, null, null, null)
						}
						catch (e: Throwable) {
							FileLog.e(e)
							native_onHostNameResolved(hostName, address, "")
							return@runOnUIThread
						}

						resolvingHostnameTasks[hostName] = task
					}

					task.addAddress(address)
				}
			}
		}

		@Keep
		@JvmStatic
		fun onBytesReceived(amount: Int, networkType: Int, currentAccount: Int) {
			try {
				StatsController.getInstance(currentAccount).incrementReceivedBytesCount(networkType, StatsController.TYPE_TOTAL, amount.toLong())
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		@Keep
		@JvmStatic
		fun onUpdateConfig(address: Long, currentAccount: Int) {
			try {
				val buff = NativeByteBuffer.wrap(address) ?: return
				buff.reused = true

				val message = TL_config.TLdeserialize(buff, buff.readInt32(true), true)

				if (message != null) {
					Utilities.stageQueue.postRunnable {
						AccountInstance.getInstance(currentAccount).messagesController.updateConfig(message)
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		@JvmStatic
		fun onInternalPushReceived(@Suppress("unused") currentAccount: Int) {
			KeepAliveJob.startJob()
		}

		@JvmStatic
		fun setProxySettings(enabled: Boolean, address: String?, port: Int, username: String?, password: String?, secret: String?) {
			@Suppress("NAME_SHADOWING") val address = address ?: ""
			@Suppress("NAME_SHADOWING") val username = username ?: ""
			@Suppress("NAME_SHADOWING") val password = password ?: ""
			@Suppress("NAME_SHADOWING") val secret = secret ?: ""

			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				if (enabled && address.isNotEmpty()) {
					native_setProxySettings(a, address, port, username, password, secret)
				}
				else {
					native_setProxySettings(a, "", 1080, "", "", "")
				}

				val accountInstance = AccountInstance.getInstance(a)

				if (accountInstance.userConfig.isClientActivated) {
					accountInstance.messagesController.checkPromoInfo(true)
				}
			}
		}

		@JvmStatic
		@Synchronized
		fun generateClassGuid(): Int {
			return lastClassGuid++
		}

		@JvmStatic
		external fun native_pauseNetwork(currentAccount: Int)

		@JvmStatic
		external fun native_setIpStrategy(currentAccount: Int, value: Byte)

		@JvmStatic
		external fun native_updateDcSettings(currentAccount: Int)

		@JvmStatic
		external fun native_setNetworkAvailable(currentAccount: Int, value: Boolean, networkType: Int, slow: Boolean)

		@JvmStatic
		external fun native_resumeNetwork(currentAccount: Int, partial: Boolean)

		@JvmStatic
		external fun native_getCurrentTimeMillis(currentAccount: Int): Long

		@JvmStatic
		external fun native_getCurrentTime(currentAccount: Int): Int

		@JvmStatic
		external fun native_getCurrentDatacenterId(currentAccount: Int): Int

		@JvmStatic
		external fun native_getTimeDifference(currentAccount: Int): Int

		@JvmStatic
		external fun native_sendRequest(currentAccount: Int, `object`: Long, onComplete: RequestDelegateInternal?, onQuickAck: QuickAckDelegate?, onWriteToSocket: WriteToSocketDelegate?, flags: Int, datacenterId: Int, connetionType: Int, immediate: Boolean, requestToken: Int)

		@JvmStatic
		external fun native_cancelRequest(currentAccount: Int, token: Int, notifyServer: Boolean)

		@JvmStatic
		external fun native_cleanUp(currentAccount: Int, resetKeys: Boolean)

		@JvmStatic
		external fun native_cancelRequestsForGuid(currentAccount: Int, guid: Int)

		@JvmStatic
		external fun native_bindRequestToGuid(currentAccount: Int, requestToken: Int, guid: Int)

		@JvmStatic
		external fun native_applyDatacenterAddress(currentAccount: Int, datacenterId: Int, ipAddress: String?, port: Int)

		@JvmStatic
		external fun native_getConnectionState(currentAccount: Int): Int

		@JvmStatic
		external fun native_setUserId(currentAccount: Int, id: Long)

		@JvmStatic
		external fun native_init(currentAccount: Int, version: Int, layer: Int, apiId: Int, deviceModel: String?, systemVersion: String?, appVersion: String?, langCode: String?, systemLangCode: String?, configPath: String?, logPath: String?, regId: String?, cFingerprint: String?, installer: String?, packageId: String?, timezoneOffset: Int, userId: Long, enablePushConnection: Boolean, hasNetwork: Boolean, networkType: Int)

		@JvmStatic
		external fun native_setProxySettings(currentAccount: Int, address: String?, port: Int, username: String?, password: String?, secret: String?)

		@JvmStatic
		external fun native_setLangCode(currentAccount: Int, langCode: String?)

		@JvmStatic
		external fun native_setRegId(currentAccount: Int, regId: String?)

		@JvmStatic
		external fun native_setSystemLangCode(currentAccount: Int, langCode: String?)

		@JvmStatic
		external fun native_seSystemLangCode(currentAccount: Int, langCode: String?)

		@JvmStatic
		external fun native_setJava(useJavaByteBuffers: Boolean)

		@JvmStatic
		external fun native_setPushConnectionEnabled(currentAccount: Int, value: Boolean)

		@JvmStatic
		external fun native_applyDnsConfig(currentAccount: Int, address: Long, phone: String?, date: Int)

		@JvmStatic
		external fun native_checkProxy(currentAccount: Int, address: String?, port: Int, username: String?, password: String?, secret: String?, requestTimeDelegate: RequestTimeDelegate?): Long

		@JvmStatic
		external fun native_onHostNameResolved(host: String?, address: Long, ip: String?)
	}
}
