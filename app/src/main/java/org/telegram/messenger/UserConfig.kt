/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC.InputStorePaymentPurpose
import org.telegram.tgnet.TLRPC.TL_account_tmpPassword
import org.telegram.tgnet.TLRPC.TL_emojiStatus
import org.telegram.tgnet.TLRPC.TL_emojiStatusUntil
import org.telegram.tgnet.TLRPC.TL_help_termsOfService
import org.telegram.tgnet.tlrpc.User
import java.util.Arrays
import kotlin.math.abs

@SuppressLint("ApplySharedPref")
class UserConfig(instance: Int) : BaseController(instance) {
	private val sync = Any()
	private var currentUser: User? = null
	private var lastBroadcastId = -1
	private var hasValidDialogLoadIds = false
	private var lastSendMessageId = -210000
	var contactsSavedCount = 0
	var lastContactsSyncTime = 0

	@Volatile
	private var savedPasswordTime: Long = 0

	@JvmField
	var registeredForPush = false

	@JvmField
	var clientUserId: Long = 0

	@JvmField
	var lastHintsSyncTime = 0

	@JvmField
	var draftsLoaded = false

	@JvmField
	var unreadDialogsLoaded = true

	@JvmField
	var tmpPassword: TL_account_tmpPassword? = null

	@JvmField
	var ratingLoadTime = 0

	@JvmField
	var botRatingLoadTime = 0

	@JvmField
	var migrateOffsetId = -1

	@JvmField
	var migrateOffsetDate = -1

	@JvmField
	var migrateOffsetUserId: Long = -1

	@JvmField
	var migrateOffsetChatId: Long = -1

	@JvmField
	var migrateOffsetChannelId: Long = -1

	@JvmField
	var migrateOffsetAccess: Long = -1

	@JvmField
	var filtersLoaded = false

	@JvmField
	var sharingMyLocationUntil = 0

	@JvmField
	var lastMyLocationShareTime = 0

	@JvmField
	var notificationsSettingsLoaded = false

	@JvmField
	var notificationsSignUpSettingsLoaded = false

	@JvmField
	var suggestContacts = true

	@JvmField
	var hasSecureData = false

	@JvmField
	var loginTime = 0

	@JvmField
	var unacceptedTermsOfService: TL_help_termsOfService? = null

	@JvmField
	var autoDownloadConfigLoadTime: Long = 0

	@JvmField
	var awaitBillingProductIds: List<String> = ArrayList()

	@JvmField
	var billingPaymentPurpose: InputStorePaymentPurpose? = null

	@JvmField
	var premiumGiftsStickerPack: String? = null

	@JvmField
	var genericAnimationsStickerPack: String? = null

	@JvmField
	var lastUpdatedPremiumGiftsStickerPack: Long = 0

	@JvmField
	var lastUpdatedGenericAnimations: Long = 0

	@JvmField
	@Volatile
	var savedPasswordHash: ByteArray? = null

	@JvmField
	@Volatile
	var savedSaltedPassword: ByteArray? = null

	var isConfigLoaded = false
		private set

	val newMessageId: Int
		get() {
			var id: Int

			synchronized(sync) {
				id = lastSendMessageId
				lastSendMessageId--
			}

			return id
		}

	fun saveConfig(withFile: Boolean) {
		NotificationCenter.getInstance(currentAccount).doOnIdle {
			synchronized(sync) {
				try {
					val editor = preferences.edit()
					editor.putInt("selectedAccount", selectedAccount)
					editor.putBoolean("registeredForPush", registeredForPush)
					editor.putInt("lastSendMessageId", lastSendMessageId)
					editor.putInt("contactsSavedCount", contactsSavedCount)
					editor.putInt("lastBroadcastId", lastBroadcastId)
					editor.putInt("lastContactsSyncTime", lastContactsSyncTime)
					editor.putInt("lastHintsSyncTime", lastHintsSyncTime)
					editor.putBoolean("draftsLoaded", draftsLoaded)
					editor.putBoolean("unreadDialogsLoaded", unreadDialogsLoaded)
					editor.putInt("ratingLoadTime", ratingLoadTime)
					editor.putInt("botRatingLoadTime", botRatingLoadTime)
					editor.putInt("loginTime", loginTime)
					editor.putBoolean("suggestContacts", suggestContacts)
					editor.putBoolean("hasSecureData", hasSecureData)
					editor.putBoolean("notificationsSettingsLoaded3", notificationsSettingsLoaded)
					editor.putBoolean("notificationsSignUpSettingsLoaded", notificationsSignUpSettingsLoaded)
					editor.putLong("autoDownloadConfigLoadTime", autoDownloadConfigLoadTime)
					editor.putBoolean("hasValidDialogLoadIds", hasValidDialogLoadIds)
					editor.putInt("sharingMyLocationUntil", sharingMyLocationUntil)
					editor.putInt("lastMyLocationShareTime", lastMyLocationShareTime)
					editor.putBoolean("filtersLoaded", filtersLoaded)
					editor.putStringSet("awaitBillingProductIds", HashSet(awaitBillingProductIds))

					if (billingPaymentPurpose != null) {
						val data = SerializedData(billingPaymentPurpose!!.objectSize)
						billingPaymentPurpose?.serializeToStream(data)
						editor.putString("billingPaymentPurpose", Base64.encodeToString(data.toByteArray(), Base64.DEFAULT))
						data.cleanup()
					}
					else {
						editor.remove("billingPaymentPurpose")
					}

					editor.putString("premiumGiftsStickerPack", premiumGiftsStickerPack)
					editor.putLong("lastUpdatedPremiumGiftsStickerPack", lastUpdatedPremiumGiftsStickerPack)
					editor.putString("genericAnimationsStickerPack", genericAnimationsStickerPack)
					editor.putLong("lastUpdatedGenericAnimations", lastUpdatedGenericAnimations)
					editor.putInt("6migrateOffsetId", migrateOffsetId)

					if (migrateOffsetId != -1) {
						editor.putInt("6migrateOffsetDate", migrateOffsetDate)
						editor.putLong("6migrateOffsetUserId", migrateOffsetUserId)
						editor.putLong("6migrateOffsetChatId", migrateOffsetChatId)
						editor.putLong("6migrateOffsetChannelId", migrateOffsetChannelId)
						editor.putLong("6migrateOffsetAccess", migrateOffsetAccess)
					}

					if (unacceptedTermsOfService != null) {
						try {
							val data = SerializedData(unacceptedTermsOfService!!.objectSize)
							unacceptedTermsOfService?.serializeToStream(data)
							editor.putString("terms", Base64.encodeToString(data.toByteArray(), Base64.DEFAULT))
							data.cleanup()
						}
						catch (ignore: Exception) {
						}
					}
					else {
						editor.remove("terms")
					}

					SharedConfig.saveConfig()

					if (tmpPassword != null) {
						val data = SerializedData()
						tmpPassword?.serializeToStream(data)
						val string = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT)
						editor.putString("tmpPassword", string)
						data.cleanup()
					}
					else {
						editor.remove("tmpPassword")
					}

					if (currentUser != null) {
						if (withFile) {
							val data = SerializedData()
							currentUser?.serializeToStream(data)
							val string = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT)
							editor.putString("user", string)
							data.cleanup()
						}
					}
					else {
						editor.remove("user")
					}

					editor.commit()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
	}

	val isClientActivated: Boolean
		get() {
			synchronized(sync) {
				return currentUser != null
			}
		}

	fun getClientUserId(): Long {
		synchronized(sync) {
			return currentUser?.id ?: 0
		}
	}

	fun getCurrentUser(): User? {
		synchronized(sync) { return currentUser }
	}

	fun setCurrentUser(user: User) {
		synchronized(sync) {
			val oldUser = currentUser
			currentUser = user
			clientUserId = user.id
			checkPremiumSelf(oldUser, user)
		}
	}

	private fun checkPremiumSelf(oldUser: User?, newUser: User?) {
		if (oldUser == null || newUser != null && oldUser.premium != newUser.premium) {
			AndroidUtilities.runOnUIThread {
				messagesController.updatePremium(newUser!!.premium)
				NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.currentUserPremiumStatusChanged)
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.premiumStatusChangedGlobal)
				mediaDataController.loadPremiumPromo(false)
				mediaDataController.loadReactions(false, true)
			}
		}
	}

	fun loadConfig() {
		synchronized(sync) {
			if (isConfigLoaded) {
				return
			}

			val preferences = preferences

			selectedAccount = preferences.getInt("selectedAccount", 0)
			registeredForPush = preferences.getBoolean("registeredForPush", false)
			lastSendMessageId = preferences.getInt("lastSendMessageId", -210000)
			contactsSavedCount = preferences.getInt("contactsSavedCount", 0)
			lastBroadcastId = preferences.getInt("lastBroadcastId", -1)
			lastContactsSyncTime = preferences.getInt("lastContactsSyncTime", (System.currentTimeMillis() / 1000).toInt() - 23 * 60 * 60)
			lastHintsSyncTime = preferences.getInt("lastHintsSyncTime", (System.currentTimeMillis() / 1000).toInt() - 25 * 60 * 60)
			draftsLoaded = preferences.getBoolean("draftsLoaded", false)
			unreadDialogsLoaded = preferences.getBoolean("unreadDialogsLoaded", false)
			ratingLoadTime = preferences.getInt("ratingLoadTime", 0)
			botRatingLoadTime = preferences.getInt("botRatingLoadTime", 0)
			loginTime = preferences.getInt("loginTime", currentAccount)
			suggestContacts = preferences.getBoolean("suggestContacts", true)
			hasSecureData = preferences.getBoolean("hasSecureData", false)
			notificationsSettingsLoaded = preferences.getBoolean("notificationsSettingsLoaded3", false)
			notificationsSignUpSettingsLoaded = preferences.getBoolean("notificationsSignUpSettingsLoaded", false)
			autoDownloadConfigLoadTime = preferences.getLong("autoDownloadConfigLoadTime", 0)
			hasValidDialogLoadIds = preferences.contains("2dialogsLoadOffsetId") || preferences.getBoolean("hasValidDialogLoadIds", false)
			sharingMyLocationUntil = preferences.getInt("sharingMyLocationUntil", 0)
			lastMyLocationShareTime = preferences.getInt("lastMyLocationShareTime", 0)
			filtersLoaded = preferences.getBoolean("filtersLoaded", false)
			awaitBillingProductIds = ArrayList(preferences.getStringSet("awaitBillingProductIds", emptySet())!!)

			if (preferences.contains("billingPaymentPurpose")) {
				val purpose = preferences.getString("billingPaymentPurpose", null)

				if (purpose != null) {
					val arr = Base64.decode(purpose, Base64.DEFAULT)

					if (arr != null) {
						val data = SerializedData()
						billingPaymentPurpose = InputStorePaymentPurpose.TLdeserialize(data, data.readInt32(false), false)
						data.cleanup()
					}
				}
			}

			premiumGiftsStickerPack = preferences.getString("premiumGiftsStickerPack", null)
			lastUpdatedPremiumGiftsStickerPack = preferences.getLong("lastUpdatedPremiumGiftsStickerPack", 0)
			genericAnimationsStickerPack = preferences.getString("genericAnimationsStickerPack", null)
			lastUpdatedGenericAnimations = preferences.getLong("lastUpdatedGenericAnimations", 0)

			try {
				val terms = preferences.getString("terms", null)

				if (terms != null) {
					val arr = Base64.decode(terms, Base64.DEFAULT)

					if (arr != null) {
						val data = SerializedData(arr)
						unacceptedTermsOfService = TL_help_termsOfService.TLdeserialize(data, data.readInt32(false), false)
						data.cleanup()
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			migrateOffsetId = preferences.getInt("6migrateOffsetId", 0)

			if (migrateOffsetId != -1) {
				migrateOffsetDate = preferences.getInt("6migrateOffsetDate", 0)
				migrateOffsetUserId = AndroidUtilities.getPrefIntOrLong(preferences, "6migrateOffsetUserId", 0)
				migrateOffsetChatId = AndroidUtilities.getPrefIntOrLong(preferences, "6migrateOffsetChatId", 0)
				migrateOffsetChannelId = AndroidUtilities.getPrefIntOrLong(preferences, "6migrateOffsetChannelId", 0)
				migrateOffsetAccess = preferences.getLong("6migrateOffsetAccess", 0)
			}

			var string = preferences.getString("tmpPassword", null)

			if (string != null) {
				val bytes = Base64.decode(string, Base64.DEFAULT)

				if (bytes != null) {
					val data = SerializedData(bytes)
					tmpPassword = TL_account_tmpPassword.TLdeserialize(data, data.readInt32(false), false)
					data.cleanup()
				}
			}

			string = preferences.getString("user", null)

			if (string != null) {
				val bytes = Base64.decode(string, Base64.DEFAULT)

				if (bytes != null) {
					val data = SerializedData(bytes)
					currentUser = User.TLdeserialize(data, data.readInt32(false), false)
					data.cleanup()
				}
			}

			if (currentUser != null) {
				checkPremiumSelf(null, currentUser)
				clientUserId = currentUser!!.id
			}

			isConfigLoaded = true
		}
	}

	fun savePassword(hash: ByteArray?, salted: ByteArray?) {
		savedPasswordTime = SystemClock.elapsedRealtime()
		savedPasswordHash = hash
		savedSaltedPassword = salted
	}

	fun checkSavedPassword() {
		if (savedSaltedPassword == null && savedPasswordHash == null || abs(SystemClock.elapsedRealtime() - savedPasswordTime) < 30 * 60 * 1000) {
			return
		}

		resetSavedPassword()
	}

	fun resetSavedPassword() {
		savedPasswordTime = 0

		if (savedPasswordHash != null) {
			Arrays.fill(savedPasswordHash!!, 0.toByte())
			savedPasswordHash = null
		}

		if (savedSaltedPassword != null) {
			Arrays.fill(savedSaltedPassword!!, 0.toByte())
			savedSaltedPassword = null
		}
	}

	private val preferences: SharedPreferences
		get() = if (currentAccount == 0) {
			ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE)
		}
		else {
			ApplicationLoader.applicationContext.getSharedPreferences("userconfig$currentAccount", Context.MODE_PRIVATE)
		}

	fun clearConfig() {
		preferences.edit().clear().commit()
		sharingMyLocationUntil = 0
		lastMyLocationShareTime = 0
		currentUser = null
		clientUserId = 0
		registeredForPush = false
		contactsSavedCount = 0
		lastSendMessageId = -210000
		lastBroadcastId = -1
		notificationsSettingsLoaded = false
		notificationsSignUpSettingsLoaded = false
		migrateOffsetId = -1
		migrateOffsetDate = -1
		migrateOffsetUserId = -1
		migrateOffsetChatId = -1
		migrateOffsetChannelId = -1
		migrateOffsetAccess = -1
		ratingLoadTime = 0
		botRatingLoadTime = 0
		draftsLoaded = false
		suggestContacts = true
		unreadDialogsLoaded = true
		hasValidDialogLoadIds = true
		unacceptedTermsOfService = null
		filtersLoaded = false
		hasSecureData = false
		loginTime = (System.currentTimeMillis() / 1000).toInt()
		lastContactsSyncTime = (System.currentTimeMillis() / 1000).toInt() - 23 * 60 * 60
		lastHintsSyncTime = (System.currentTimeMillis() / 1000).toInt() - 25 * 60 * 60

		resetSavedPassword()

		var hasActivated = false

		for (a in 0 until MAX_ACCOUNT_COUNT) {
			if (AccountInstance.getInstance(a).userConfig.isClientActivated) {
				hasActivated = true
				break
			}
		}

		if (!hasActivated) {
			SharedConfig.clearConfig()
		}

		saveConfig(true)
	}

	fun isPinnedDialogsLoaded(folderId: Int): Boolean {
		return preferences.getBoolean("2pinnedDialogsLoaded$folderId", false)
	}

	fun setPinnedDialogsLoaded(folderId: Int, loaded: Boolean) {
		preferences.edit().putBoolean("2pinnedDialogsLoaded$folderId", loaded).commit()
	}

	fun getTotalDialogsCount(folderId: Int): Int {
		return preferences.getInt("2totalDialogsLoadCount" + if (folderId == 0) "" else folderId, 0)
	}

	fun setTotalDialogsCount(folderId: Int, totalDialogsLoadCount: Int) {
		preferences.edit().putInt("2totalDialogsLoadCount" + if (folderId == 0) "" else folderId, totalDialogsLoadCount).commit()
	}

	fun getDialogLoadOffsets(folderId: Int): LongArray {
		val preferences = preferences
		val dialogsLoadOffsetId = preferences.getInt("2dialogsLoadOffsetId" + if (folderId == 0) "" else folderId, if (hasValidDialogLoadIds) 0 else -1)
		val dialogsLoadOffsetDate = preferences.getInt("2dialogsLoadOffsetDate" + if (folderId == 0) "" else folderId, if (hasValidDialogLoadIds) 0 else -1)
		val dialogsLoadOffsetUserId = AndroidUtilities.getPrefIntOrLong(preferences, "2dialogsLoadOffsetUserId" + if (folderId == 0) "" else folderId, if (hasValidDialogLoadIds) 0 else -1L)
		val dialogsLoadOffsetChatId = AndroidUtilities.getPrefIntOrLong(preferences, "2dialogsLoadOffsetChatId" + if (folderId == 0) "" else folderId, if (hasValidDialogLoadIds) 0 else -1L)
		val dialogsLoadOffsetChannelId = AndroidUtilities.getPrefIntOrLong(preferences, "2dialogsLoadOffsetChannelId" + if (folderId == 0) "" else folderId, if (hasValidDialogLoadIds) 0 else -1L)
		val dialogsLoadOffsetAccess = preferences.getLong("2dialogsLoadOffsetAccess" + if (folderId == 0) "" else folderId, if (hasValidDialogLoadIds) 0 else -1L)
		return longArrayOf(dialogsLoadOffsetId.toLong(), dialogsLoadOffsetDate.toLong(), dialogsLoadOffsetUserId, dialogsLoadOffsetChatId, dialogsLoadOffsetChannelId, dialogsLoadOffsetAccess)
	}

	fun setDialogsLoadOffset(folderId: Int, dialogsLoadOffsetId: Int, dialogsLoadOffsetDate: Int, dialogsLoadOffsetUserId: Long, dialogsLoadOffsetChatId: Long, dialogsLoadOffsetChannelId: Long, dialogsLoadOffsetAccess: Long) {
		val editor = preferences.edit()
		editor.putInt("2dialogsLoadOffsetId" + if (folderId == 0) "" else folderId, dialogsLoadOffsetId)
		editor.putInt("2dialogsLoadOffsetDate" + if (folderId == 0) "" else folderId, dialogsLoadOffsetDate)
		editor.putLong("2dialogsLoadOffsetUserId" + if (folderId == 0) "" else folderId, dialogsLoadOffsetUserId)
		editor.putLong("2dialogsLoadOffsetChatId" + if (folderId == 0) "" else folderId, dialogsLoadOffsetChatId)
		editor.putLong("2dialogsLoadOffsetChannelId" + if (folderId == 0) "" else folderId, dialogsLoadOffsetChannelId)
		editor.putLong("2dialogsLoadOffsetAccess" + if (folderId == 0) "" else folderId, dialogsLoadOffsetAccess)
		editor.putBoolean("hasValidDialogLoadIds", true)
		editor.commit()
	}

	val isPremium: Boolean
		get() = currentUser?.premium == true

	val emojiStatus: Long?
		get() {
			val currentUser = currentUser ?: return null

			if (currentUser.emoji_status is TL_emojiStatusUntil && (currentUser.emoji_status as TL_emojiStatusUntil).until > (System.currentTimeMillis() / 1000).toInt()) {
				return (currentUser.emoji_status as TL_emojiStatusUntil).document_id
			}

			if (currentUser.emoji_status is TL_emojiStatus) {
				return (currentUser.emoji_status as TL_emojiStatus).document_id
			}

			return null
		}

	companion object {
		const val MAX_ACCOUNT_COUNT = 3
		const val MAX_ACCOUNT_DEFAULT_COUNT = MAX_ACCOUNT_COUNT
		const val i_dialogsLoadOffsetId = 0
		const val i_dialogsLoadOffsetDate = 1
		const val i_dialogsLoadOffsetUserId = 2
		const val i_dialogsLoadOffsetChatId = 3
		const val i_dialogsLoadOffsetChannelId = 4
		const val i_dialogsLoadOffsetAccess = 5

		@JvmField
		var selectedAccount = 0

		private val Instance = arrayOfNulls<UserConfig>(MAX_ACCOUNT_COUNT)

		@JvmStatic
		fun getInstance(num: Int): UserConfig {
			var localInstance = Instance[num]

			if (localInstance == null) {
				synchronized(UserConfig::class.java) {
					localInstance = Instance[num]

					if (localInstance == null) {
						localInstance = UserConfig(num)
						Instance[num] = localInstance
					}
				}
			}

			return localInstance!!
		}

		@JvmStatic
		val activatedAccountsCount: Int
			get() {
				var count = 0
				for (a in 0 until MAX_ACCOUNT_COUNT) {
					if (AccountInstance.getInstance(a).userConfig.isClientActivated) {
						count++
					}
				}
				return count
			}

		@JvmStatic
		fun hasPremiumOnAccounts(): Boolean {
			for (a in 0 until MAX_ACCOUNT_COUNT) {
				if (AccountInstance.getInstance(a).userConfig.isClientActivated && AccountInstance.getInstance(a).userConfig.userConfig.isPremium) {
					return true
				}
			}
			return false
		}

		val isLoggedIn: Boolean
			get() = getInstance(selectedAccount).isClientActivated

		@JvmStatic
		fun isValidAccount(num: Int): Boolean {
			return num in 0 until MAX_ACCOUNT_COUNT && getInstance(num).isClientActivated
		}
	}
}
