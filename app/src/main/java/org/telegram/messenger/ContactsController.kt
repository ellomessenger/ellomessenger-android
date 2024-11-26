/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger

import android.accounts.Account
import android.accounts.AccountManager
import androidx.collection.LongSparseArray
import org.telegram.messenger.UserObject.getFirstName
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.PrivacyRule
import org.telegram.tgnet.TLRPC.TL_accountDaysTTL
import org.telegram.tgnet.TLRPC.TL_account_getAccountTTL
import org.telegram.tgnet.TLRPC.TL_account_getGlobalPrivacySettings
import org.telegram.tgnet.TLRPC.TL_account_getPrivacy
import org.telegram.tgnet.TLRPC.TL_account_privacyRules
import org.telegram.tgnet.TLRPC.TL_contact
import org.telegram.tgnet.TLRPC.TL_contactStatus
import org.telegram.tgnet.TLRPC.TL_contacts_addContact
import org.telegram.tgnet.TLRPC.TL_contacts_contactsNotModified
import org.telegram.tgnet.TLRPC.TL_contacts_deleteContacts
import org.telegram.tgnet.TLRPC.TL_contacts_getStatuses
import org.telegram.tgnet.TLRPC.TL_contacts_resetSaved
import org.telegram.tgnet.TLRPC.TL_globalPrivacySettings
import org.telegram.tgnet.TLRPC.TL_help_getInviteText
import org.telegram.tgnet.TLRPC.TL_help_inviteText
import org.telegram.tgnet.TLRPC.TL_inputPrivacyKeyAddedByPhone
import org.telegram.tgnet.TLRPC.TL_inputPrivacyKeyChatInvite
import org.telegram.tgnet.TLRPC.TL_inputPrivacyKeyForwards
import org.telegram.tgnet.TLRPC.TL_inputPrivacyKeyPhoneCall
import org.telegram.tgnet.TLRPC.TL_inputPrivacyKeyPhoneNumber
import org.telegram.tgnet.TLRPC.TL_inputPrivacyKeyPhoneP2P
import org.telegram.tgnet.TLRPC.TL_inputPrivacyKeyProfilePhoto
import org.telegram.tgnet.TLRPC.TL_inputPrivacyKeyStatusTimestamp
import org.telegram.tgnet.TLRPC.TL_inputPrivacyKeyVoiceMessages
import org.telegram.tgnet.TLRPC.TL_user
import org.telegram.tgnet.TLRPC.TL_userStatusLastMonth
import org.telegram.tgnet.TLRPC.TL_userStatusLastWeek
import org.telegram.tgnet.TLRPC.TL_userStatusRecently
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.tgnet.TLRPC.contacts_Contacts
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_contacts_getContacts
import org.telegram.tgnet.tlrpc.User
import org.telegram.tgnet.tlrpc.Vector
import org.telegram.ui.Components.Bulletin
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class ContactsController(instance: Int) : BaseController(instance) {
	private var systemAccount: Account? = null
	private val loadingContacts = AtomicBoolean(false)
	private var contactsSyncInProgress = false
	private var contactsBookLoaded = false
	private var migratingContacts = false
	private var lastContactsVersions = ""
	private val delayedContactsUpdate = ArrayList<Long>()
	private var inviteLink: String? = null
	private var updatingInviteLink = false
	private val sectionsToReplace = HashMap<String, String>()
	private var loadingGlobalSettings = 0
	private var loadingDeleteInfo = 0
	private val loadingPrivacyInfo = IntArray(PRIVACY_RULES_TYPE_COUNT)
	private var lastSeenPrivacyRules: ArrayList<PrivacyRule>? = null
	private var groupPrivacyRules: ArrayList<PrivacyRule>? = null
	private var callPrivacyRules: ArrayList<PrivacyRule>? = null
	private var p2pPrivacyRules: ArrayList<PrivacyRule>? = null
	private var profilePhotoPrivacyRules: ArrayList<PrivacyRule>? = null
	private var forwardsPrivacyRules: ArrayList<PrivacyRule>? = null
	private var phonePrivacyRules: ArrayList<PrivacyRule>? = null
	private var addedByPhonePrivacyRules: ArrayList<PrivacyRule>? = null
	private var voiceMessagesRules: ArrayList<PrivacyRule>? = null
	var deleteAccountTTL = 0

	@JvmField
	var contactsLoaded = false

	@JvmField
	var doneLoadingContacts = false

	var globalPrivacySettings: TL_globalPrivacySettings? = null
		private set

	class Contact {
		var provider: String? = null

		@JvmField
		var contactId = 0

		@JvmField
		var key: String? = null

		@JvmField
		var firstName: String? = null

		@JvmField
		var lastName: String? = null

		@JvmField
		var imported = 0

		@JvmField
		var user: User? = null

		val letter: String
			get() = getLetter(firstName, lastName)

		companion object {
			fun getLetter(firstName: String?, lastName: String?): String {
				firstName?.trim()?.takeIf { it.isNotEmpty() }?.substring(0, 1)?.let { return it }
				lastName?.trim()?.takeIf { it.isNotEmpty() }?.substring(0, 1)?.let { return it }
				return "#"
			}
		}
	}

	@JvmField
	var contacts = ArrayList<TL_contact>()

	@JvmField
	var contactsDict = ConcurrentHashMap<Long, TL_contact>(20, 1.0f, 2)

	var usersSectionsDict = HashMap<String, ArrayList<TL_contact>>()
	var sortedUsersSectionsArray = ArrayList<String>()
	var usersMutualSectionsDict = HashMap<String, ArrayList<TL_contact>>()
	var sortedUsersMutualSectionsArray = ArrayList<String>()
	private var completedRequestsCount = 0

	init {
		val preferences = MessagesController.getMainSettings(currentAccount)

		if (preferences.getBoolean("needGetStatuses", false)) {
			reloadContactsStatuses()
		}

		sectionsToReplace["À"] = "A"
		sectionsToReplace["Á"] = "A"
		sectionsToReplace["Ä"] = "A"
		sectionsToReplace["Ù"] = "U"
		sectionsToReplace["Ú"] = "U"
		sectionsToReplace["Ü"] = "U"
		sectionsToReplace["Ì"] = "I"
		sectionsToReplace["Í"] = "I"
		sectionsToReplace["Ï"] = "I"
		sectionsToReplace["È"] = "E"
		sectionsToReplace["É"] = "E"
		sectionsToReplace["Ê"] = "E"
		sectionsToReplace["Ë"] = "E"
		sectionsToReplace["Ò"] = "O"
		sectionsToReplace["Ó"] = "O"
		sectionsToReplace["Ö"] = "O"
		sectionsToReplace["Ç"] = "C"
		sectionsToReplace["Ñ"] = "N"
		sectionsToReplace["Ÿ"] = "Y"
		sectionsToReplace["Ý"] = "Y"
		sectionsToReplace["Ţ"] = "Y"
	}

	fun cleanup() {
		contacts.clear()
		contactsDict.clear()
		usersSectionsDict.clear()
		usersMutualSectionsDict.clear()
		sortedUsersSectionsArray.clear()
		sortedUsersMutualSectionsArray.clear()
		delayedContactsUpdate.clear()
		loadingContacts.set(false)
		contactsSyncInProgress = false
		doneLoadingContacts = false
		contactsLoaded = false
		contactsBookLoaded = false
		lastContactsVersions = ""
		loadingGlobalSettings = 0
		loadingDeleteInfo = 0
		deleteAccountTTL = 0
		Arrays.fill(loadingPrivacyInfo, 0)
		lastSeenPrivacyRules = null
		groupPrivacyRules = null
		callPrivacyRules = null
		p2pPrivacyRules = null
		profilePhotoPrivacyRules = null
		forwardsPrivacyRules = null
		phonePrivacyRules = null

		Utilities.globalQueue.postRunnable {
			migratingContacts = false
			completedRequestsCount = 0
		}
	}

	fun checkInviteText() {
		val preferences = MessagesController.getMainSettings(currentAccount)

		inviteLink = preferences.getString("invitelink", null)

		val time = preferences.getInt("invitelinktime", 0)

		if (!updatingInviteLink && (inviteLink == null || abs(System.currentTimeMillis() / 1000 - time) >= 86400)) {
			updatingInviteLink = true

			val req = TL_help_getInviteText()

			connectionsManager.sendRequest(req, { response, _ ->
				if (response != null) {
					val res = response as TL_help_inviteText

					if (res.message.isNotEmpty()) {
						AndroidUtilities.runOnUIThread {
							updatingInviteLink = false
							val preferences1 = MessagesController.getMainSettings(currentAccount)
							val editor = preferences1.edit()
							editor.putString("invitelink", res.message.also { inviteLink = it })
							editor.putInt("invitelinktime", (System.currentTimeMillis() / 1000).toInt())
							editor.commit()
						}
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}
	}

	fun getInviteText(contacts: Int): String {
		val link = inviteLink ?: "https://ello.team/dl"

		return if (contacts <= 1) {
			LocaleController.formatString("InviteText2", R.string.InviteText2, link)
		}
		else {
			try {
				String.format(LocaleController.getPluralString("InviteTextNum", contacts), contacts, link)
			}
			catch (e: Exception) {
				LocaleController.formatString("InviteText2", R.string.InviteText2, link)
			}
		}
	}

	fun checkAppAccount() {
		val am = AccountManager.get(ApplicationLoader.applicationContext)

		runCatching {
			val accounts = am.getAccountsByType(BuildConfig.APPLICATION_ID)

			systemAccount = null

			for (acc in accounts) {
				var found = false

				for (b in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
					val user = UserConfig.getInstance(b).getCurrentUser()

					if (user != null) {
						if (acc.name == "" + user.id) {
							if (b == currentAccount) {
								systemAccount = acc
							}

							found = true

							break
						}
					}
				}

				if (!found) {
					runCatching {
						am.removeAccount(acc, null, null, null)
					}
				}
			}
		}

		if (userConfig.isClientActivated) {
			readContacts()

			if (systemAccount == null) {
				runCatching {
					systemAccount = Account("" + userConfig.getClientUserId(), BuildConfig.APPLICATION_ID)
					am.addAccountExplicitly(systemAccount, "", null)
				}
			}
		}
	}

	fun deleteUnknownAppAccounts() {
		runCatching {
			systemAccount = null

			val am = AccountManager.get(ApplicationLoader.applicationContext)
			val accounts = am.getAccountsByType(BuildConfig.APPLICATION_ID)

			for (acc in accounts) {
				var found = false

				for (b in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
					val user = UserConfig.getInstance(b).getCurrentUser()

					if (user != null) {
						if (acc.name == "" + user.id) {
							found = true
							break
						}
					}
				}

				if (!found) {
					runCatching {
						am.removeAccount(acc, null, null, null)
					}
				}
			}
		}
	}

	fun deleteAllContacts(runnable: Runnable) {
		resetImportedContacts()
		val req = TL_contacts_deleteContacts()
		var a = 0
		val size = contacts.size

		while (a < size) {
			val contact = contacts[a]
			req.id.add(messagesController.getInputUser(contact.user_id))
			a++
		}

		connectionsManager.sendRequest(req) { _, error ->
			if (error == null) {
				completedRequestsCount = 0
				migratingContacts = false
				contactsSyncInProgress = false
				contactsLoaded = false
				loadingContacts.set(false)
				contactsBookLoaded = false
				lastContactsVersions = ""

				AndroidUtilities.runOnUIThread {
					val am = AccountManager.get(ApplicationLoader.applicationContext)

					runCatching {
						val accounts = am.getAccountsByType(BuildConfig.APPLICATION_ID)

						systemAccount = null

						for (acc in accounts) {
							for (b in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
								val user = UserConfig.getInstance(b).getCurrentUser()

								if (user != null) {
									if (acc.name == "" + user.id) {
										runCatching {
											am.removeAccount(acc, null, null, null)
										}

										break
									}
								}
							}
						}
					}

					runCatching {
						systemAccount = Account("" + userConfig.getClientUserId(), BuildConfig.APPLICATION_ID)
						am.addAccountExplicitly(systemAccount, "", null)
					}

					messagesStorage.putCachedPhoneBook(HashMap(), false, true)
					messagesStorage.putContacts(ArrayList(), true)

					contacts.clear()
					contactsDict.clear()
					usersSectionsDict.clear()
					usersMutualSectionsDict.clear()
					sortedUsersSectionsArray.clear()
					delayedContactsUpdate.clear()
					sortedUsersMutualSectionsArray.clear()

					notificationCenter.postNotificationName(NotificationCenter.contactsDidLoad)

					loadContacts(false, 0)

					runnable.run()
				}
			}
			else {
				AndroidUtilities.runOnUIThread(runnable)
			}
		}
	}

	fun resetImportedContacts() {
		val req = TL_contacts_resetSaved()
		connectionsManager.sendRequest(req)
	}

	private fun readContacts() {
		if (!loadingContacts.compareAndSet(false, true)) {
			return
		}

		Utilities.stageQueue.postRunnable {
			if (contacts.isNotEmpty() || contactsLoaded) {
				loadingContacts.set(false)
				return@postRunnable
			}

			loadContacts(true, 0)
		}
	}

//	private fun isNotValidNameString(src: String?): Boolean {
//		return (src?.map { if (it in '0'..'9') 1 else 0 }?.sum() ?: Int.MAX_VALUE) > 3
//	}

	fun isLoadingContacts(): Boolean {
		return loadingContacts.get()
	}

	private fun getContactsHash(contacts: ArrayList<TL_contact>): Long {
		return contacts.sortedBy { it.user_id }.fold(MediaDataController.calcHash(0L, userConfig.contactsSavedCount.toLong())) { acc, contact ->
			MediaDataController.calcHash(acc, contact.user_id)
		}
	}

	fun loadContacts(fromCache: Boolean, hash: Long) {
		loadingContacts.set(true)

		if (fromCache) {
			messagesStorage.getContacts()
		}
		else {
			val req = TL_contacts_getContacts()
			req.hash = hash

			connectionsManager.sendRequest(req) { response, error ->
				if (error == null) {
					val res = response as contacts_Contacts

					if (hash != 0L && res is TL_contacts_contactsNotModified) {
						contactsLoaded = true

						if (delayedContactsUpdate.isNotEmpty() && contactsBookLoaded) {
							applyContactsUpdates(delayedContactsUpdate, null, null, null)
							delayedContactsUpdate.clear()
						}

						userConfig.lastContactsSyncTime = (System.currentTimeMillis() / 1000).toInt()
						userConfig.saveConfig(false)

						AndroidUtilities.runOnUIThread {
							loadingContacts.set(false)
							notificationCenter.postNotificationName(NotificationCenter.contactsDidLoad)
						}

						return@sendRequest
					}
					else {
						userConfig.contactsSavedCount = res.saved_count
						userConfig.saveConfig(false)
					}

					processLoadedContacts(res.contacts, res.users, 0)
				}
			}
		}
	}

	fun processLoadedContacts(contactsArr: ArrayList<TL_contact>, usersArr: ArrayList<User>?, from: Int) {
		//from: 0 - from server, 1 - from db, 2 - from imported contacts
		AndroidUtilities.runOnUIThread {
			messagesController.putUsers(usersArr, from == 1)
			val usersDict = LongSparseArray<User>()
			val isEmpty = contactsArr.isEmpty()

			if (from == 2 && contacts.isNotEmpty()) {
				var a = 0

				while (a < contactsArr.size) {
					val contact = contactsArr[a]

					if (contactsDict[contact.user_id] != null) {
						contactsArr.removeAt(a)
						a--
					}

					a++
				}

				contactsArr.addAll(contacts)
			}

			for (a in contactsArr.indices) {
				val user = messagesController.getUser(contactsArr[a].user_id)

				if (user != null) {
					usersDict.put(user.id, user)
				}
			}

			Utilities.stageQueue.postRunnable {
				FileLog.d("done loading contacts")

				if (from == 1 && (contactsArr.isEmpty() || abs(System.currentTimeMillis() / 1000 - userConfig.lastContactsSyncTime) >= 24 * 60 * 60)) {
					loadContacts(false, getContactsHash(contactsArr))

					if (contactsArr.isEmpty()) {
						AndroidUtilities.runOnUIThread {
							doneLoadingContacts = true
							notificationCenter.postNotificationName(NotificationCenter.contactsDidLoad)
						}

						return@postRunnable
					}
				}

				if (from == 0) {
					userConfig.lastContactsSyncTime = (System.currentTimeMillis() / 1000).toInt()
					userConfig.saveConfig(false)
				}

				for (a in contactsArr.indices) {
					val contact = contactsArr[a]

					if (usersDict[contact.user_id] == null && contact.user_id != userConfig.getClientUserId()) {
						loadContacts(false, 0)

						FileLog.d("contacts are broken, load from server")

						AndroidUtilities.runOnUIThread {
							doneLoadingContacts = true
							notificationCenter.postNotificationName(NotificationCenter.contactsDidLoad)
						}

						return@postRunnable
					}
				}

				if (from != 1) {
					messagesStorage.putUsersAndChats(usersArr, null, true, true)
					messagesStorage.putContacts(contactsArr, from != 2)
				}

				contactsArr.sortBy { getFirstName(usersDict[it.user_id]) }

				val contactsDictionary = ConcurrentHashMap<Long, TL_contact>(20, 1.0f, 2)
				val sectionsDict = HashMap<String, ArrayList<TL_contact>>()
				val sectionsDictMutual = HashMap<String, ArrayList<TL_contact>>()
				val sortedSectionsArray = ArrayList<String>()
				val sortedSectionsArrayMutual = ArrayList<String>()

				for (a in contactsArr.indices) {
					val value = contactsArr[a]
					val user = usersDict[value.user_id] ?: continue

					contactsDictionary[value.user_id] = value

					var key = getFirstName(user)

					if (key.length > 1) {
						key = key.substring(0, 1)
					}

					key = if (key.isEmpty()) {
						"#"
					}
					else {
						key.uppercase()
					}

					val replace = sectionsToReplace[key]

					if (replace != null) {
						key = replace
					}

					var arr = sectionsDict[key]

					if (arr == null) {
						arr = ArrayList()
						sectionsDict[key] = arr
						sortedSectionsArray.add(key)
					}

					arr.add(value)

					if (user.mutual_contact) {
						arr = sectionsDictMutual[key]

						if (arr == null) {
							arr = ArrayList()
							sectionsDictMutual[key] = arr
							sortedSectionsArrayMutual.add(key)
						}

						arr.add(value)
					}
				}

				sortedSectionsArray.sortWith { s, s2 ->
					val cv1 = s[0]
					val cv2 = s2[0]

					if (cv1 == '#') {
						return@sortWith 1
					}
					else if (cv2 == '#') {
						return@sortWith -1
					}

					s.compareTo(s2)
				}

				sortedSectionsArrayMutual.sortWith { s, s2 ->
					val cv1 = s[0]
					val cv2 = s2[0]

					if (cv1 == '#') {
						return@sortWith 1
					}
					else if (cv2 == '#') {
						return@sortWith -1
					}

					s.compareTo(s2)
				}

				AndroidUtilities.runOnUIThread {
					contacts = contactsArr
					contactsDict = contactsDictionary
					usersSectionsDict = sectionsDict
					usersMutualSectionsDict = sectionsDictMutual
					sortedUsersSectionsArray = sortedSectionsArray
					sortedUsersMutualSectionsArray = sortedSectionsArrayMutual
					doneLoadingContacts = true

					if (from != 2) {
						loadingContacts.set(false)
					}

					notificationCenter.postNotificationName(NotificationCenter.contactsDidLoad)

					if (from != 1 && !isEmpty) {
						saveContactsLoadTime()
					}
					else {
						reloadContactsStatusesMaybe()
					}
				}

				if (delayedContactsUpdate.isNotEmpty() && contactsLoaded && contactsBookLoaded) {
					applyContactsUpdates(delayedContactsUpdate, null, null, null)
					delayedContactsUpdate.clear()
				}

				contactsLoaded = true
			}
		}
	}

	fun isContact(userId: Long): Boolean {
		return contactsDict[userId] != null
	}

	fun reloadContactsStatusesMaybe() {
		runCatching {
			val preferences = MessagesController.getMainSettings(currentAccount)
			val lastReloadStatusTime = preferences.getLong("lastReloadStatusTime", 0)

			if (lastReloadStatusTime < System.currentTimeMillis() - 1000 * 60 * 60 * 3) {
				reloadContactsStatuses()
			}
		}
	}

	private fun saveContactsLoadTime() {
		runCatching {
			val preferences = MessagesController.getMainSettings(currentAccount)
			preferences.edit().putLong("lastReloadStatusTime", System.currentTimeMillis()).commit()
		}
	}

	private fun buildContactsSectionsArrays(sort: Boolean) {
		if (sort) {
			contacts.sortWith { contact1, contact2 ->
				val user1 = messagesController.getUser(contact1.user_id)
				val user2 = messagesController.getUser(contact2.user_id)
				val name1 = getFirstName(user1)
				val name2 = getFirstName(user2)

				name1.compareTo(name2)
			}
		}

		val sectionsDict = HashMap<String, ArrayList<TL_contact>>()
		val sortedSectionsArray = ArrayList<String>()

		for (a in contacts.indices) {
			val value = contacts[a]
			val user = messagesController.getUser(value.user_id) ?: continue
			var key = getFirstName(user)

			if (key.length > 1) {
				key = key.substring(0, 1)
			}

			key = if (key.isEmpty()) {
				"#"
			}
			else {
				key.uppercase()
			}

			val replace = sectionsToReplace[key]

			if (replace != null) {
				key = replace
			}

			var arr = sectionsDict[key]

			if (arr == null) {
				arr = ArrayList()
				sectionsDict[key] = arr
				sortedSectionsArray.add(key)
			}

			arr.add(value)
		}

		sortedSectionsArray.sortWith { s, s2 ->
			val cv1 = s[0]
			val cv2 = s2[0]

			if (cv1 == '#') {
				return@sortWith 1
			}
			else if (cv2 == '#') {
				return@sortWith -1
			}

			s.compareTo(s2)
		}

		usersSectionsDict = sectionsDict
		sortedUsersSectionsArray = sortedSectionsArray
	}

	private fun applyContactsUpdates(ids: List<Long>, userDict: ConcurrentHashMap<Long, User>?, newC: List<TL_contact>?, contactsTD: List<Long>?) {
		@Suppress("NAME_SHADOWING") var newC = newC
		@Suppress("NAME_SHADOWING") var contactsTD = contactsTD

		if (newC == null || contactsTD == null) {
			newC = ArrayList()
			contactsTD = ArrayList()

			for (a in ids.indices) {
				val uid = ids[a]

				if (uid > 0) {
					val contact = TL_contact()
					contact.user_id = uid
					newC.add(contact)
				}
				else if (uid < 0) {
					contactsTD.add(-uid)
				}
			}
		}

		FileLog.d("process update - contacts add = " + newC.size + " delete = " + contactsTD.size)

		var reloadContacts = false

		for (a in newC.indices) {
			val newContact = newC[a]
			var user: User? = null

			if (userDict != null) {
				user = userDict[newContact.user_id]
			}

			if (user == null) {
				user = messagesController.getUser(newContact.user_id)
			}
			else {
				messagesController.putUser(user, true)
			}

			if (user == null) {
				reloadContacts = true
				continue
			}
		}

		for (a in contactsTD.indices) {
			val uid = contactsTD[a]
			var user: User? = null

			if (userDict != null) {
				user = userDict[uid]
			}

			if (user == null) {
				user = messagesController.getUser(uid)
			}
			else {
				messagesController.putUser(user, true)
			}

			if (user == null) {
				reloadContacts = true
				continue
			}
		}

		if (reloadContacts) {
			Utilities.stageQueue.postRunnable {
				loadContacts(false, 0)
			}
		}
		else {
			val newContacts: List<TL_contact> = newC
			val contactsToDelete: List<Long> = contactsTD

			AndroidUtilities.runOnUIThread {
				for (a in newContacts.indices) {
					val contact = newContacts[a]

					if (contactsDict[contact.user_id] == null) {
						if (contact.user_id == userConfig.getClientUserId()) {
							continue
						}

						contacts.add(contact)
						contactsDict[contact.user_id] = contact
					}
				}

				for (a in contactsToDelete.indices) {
					val uid = contactsToDelete[a]
					val contact = contactsDict[uid]

					if (contact != null) {
						contacts.remove(contact)
						contactsDict.remove(uid)
					}
				}

				buildContactsSectionsArrays(newContacts.isNotEmpty())

				notificationCenter.postNotificationName(NotificationCenter.contactsDidLoad)
			}
		}
	}

	fun processContactsUpdates(ids: List<Long>, userDict: ConcurrentHashMap<Long, User>?) {
		val newContacts = mutableListOf<TL_contact>()
		val contactsToDelete = mutableListOf<Long>()

		for (uid in ids) {
			if (uid > 0) {
				if (uid == userConfig.getClientUserId()) {
					continue
				}

				val contact = TL_contact()
				contact.user_id = uid

				newContacts.add(contact)

				if (delayedContactsUpdate.isNotEmpty()) {
					val idx = delayedContactsUpdate.indexOf(-uid)

					if (idx != -1) {
						delayedContactsUpdate.removeAt(idx)
					}
				}
			}
			else if (uid < 0) {
				contactsToDelete.add(-uid)

				if (delayedContactsUpdate.isNotEmpty()) {
					val idx = delayedContactsUpdate.indexOf(-uid)

					if (idx != -1) {
						delayedContactsUpdate.removeAt(idx)
					}
				}
			}
		}

		if (contactsToDelete.isNotEmpty()) {
			messagesStorage.deleteContacts(contactsToDelete)
		}

		if (newContacts.isNotEmpty()) {
			messagesStorage.putContacts(newContacts, false)
		}

		if (!contactsLoaded || !contactsBookLoaded) {
			delayedContactsUpdate.addAll(ids)
			FileLog.d("delay update - contacts add = " + newContacts.size + " delete = " + contactsToDelete.size)
		}
		else {
			applyContactsUpdates(ids, userDict, newContacts, contactsToDelete)
		}
	}

	fun addContact(user: User?, exception: Boolean) {
		if (user == null) {
			return
		}

		val req = TL_contacts_addContact()
		req.id = messagesController.getInputUser(user)
		req.first_name = user.first_name
		req.last_name = user.last_name
		req.phone = "" // user.phone
		req.add_phone_privacy_exception = exception

		connectionsManager.sendRequest(req, { response, error ->
			if (error != null) {
				return@sendRequest
			}

			val res = response as Updates
			messagesController.processUpdates(res, false)

			for (a in res.users.indices) {
				val u = res.users[a]

				if (u.id != user.id) {
					continue
				}

				val newContact = TL_contact()
				newContact.user_id = u.id

				val arrayList = ArrayList<TL_contact>()
				arrayList.add(newContact)

				messagesStorage.putContacts(arrayList, false)
			}

			AndroidUtilities.runOnUIThread {
				for (a in res.users.indices) {
					val u = res.users[a]

					if (!u.contact || contactsDict[u.id] != null) {
						continue
					}

					if (u.id == userConfig.getClientUserId()) {
						continue
					}

					val newContact = TL_contact()
					newContact.user_id = u.id

					contacts.add(newContact)
					contactsDict[newContact.user_id] = newContact
				}

				buildContactsSectionsArrays(true)

				notificationCenter.postNotificationName(NotificationCenter.contactsDidLoad)
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagCanCompress)
	}

	fun deleteContact(users: ArrayList<User>?, showBulletin: Boolean) {
		if (users.isNullOrEmpty()) {
			return
		}

		val req = TL_contacts_deleteContacts()
		val uids = mutableListOf<Long>()
		var a = 0
		val n = users.size

		while (a < n) {
			val user = users[a]
			val inputUser = messagesController.getInputUser(user)

			if (inputUser is TLRPC.TL_inputUserEmpty) {
				a++
				continue
			}

			user.contact = false
			uids.add(user.id)

			req.id.add(inputUser)

			a++
		}

		val userName = users[0].first_name

		connectionsManager.sendRequest(req) { response, error ->
			if (error != null) {
				return@sendRequest
			}

			messagesController.processUpdates(response as Updates?, false)
			messagesStorage.deleteContacts(uids)

			AndroidUtilities.runOnUIThread {
				var remove = false

				for (user in users) {
					val contact = contactsDict[user.id]

					if (contact != null) {
						remove = true
						contacts.remove(contact)
						contactsDict.remove(user.id)
					}
				}

				if (remove) {
					buildContactsSectionsArrays(false)
				}

				notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME)
				notificationCenter.postNotificationName(NotificationCenter.contactsDidLoad)

				if (showBulletin) {
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, LocaleController.formatString("DeletedFromYourContacts", R.string.DeletedFromYourContacts, userName))
				}
			}
		}
	}

	private fun reloadContactsStatuses() {
		saveContactsLoadTime()

		messagesController.clearFullUsers()

		val preferences = MessagesController.getMainSettings(currentAccount)
		val editor = preferences.edit()
		editor.putBoolean("needGetStatuses", true).commit()

		val req = TL_contacts_getStatuses()

		connectionsManager.sendRequest(req) { response, error ->
			if (error == null) {
				AndroidUtilities.runOnUIThread {
					editor.remove("needGetStatuses").commit()

					val vector = response as Vector

					if (vector.objects.isNotEmpty()) {
						val dbUsersStatus = ArrayList<User>()
						for (`object` in vector.objects) {
							val toDbUser: User = TL_user()
							val status = `object` as? TL_contactStatus ?: continue

							when (status.status) {
								is TL_userStatusRecently -> {
									status.status.expires = -100
								}

								is TL_userStatusLastWeek -> {
									status.status.expires = -101
								}

								is TL_userStatusLastMonth -> {
									status.status.expires = -102
								}
							}

							val user = messagesController.getUser(status.user_id)

							if (user != null) {
								user.status = status.status
							}

							toDbUser.status = status.status
							dbUsersStatus.add(toDbUser)
						}

						messagesStorage.updateUsers(dbUsersStatus, true, true, true)
					}

					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_STATUS)
				}
			}
		}
	}

	fun loadPrivacySettings() {
		if (loadingDeleteInfo == 0) {
			loadingDeleteInfo = 1

			val req = TL_account_getAccountTTL()

			connectionsManager.sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					if (error == null) {
						val ttl = response as TL_accountDaysTTL
						deleteAccountTTL = ttl.days
						loadingDeleteInfo = 2
					}
					else {
						loadingDeleteInfo = 0
					}

					notificationCenter.postNotificationName(NotificationCenter.privacyRulesUpdated)
				}
			}
		}

		if (loadingGlobalSettings == 0) {
			loadingGlobalSettings = 1

			val req = TL_account_getGlobalPrivacySettings()

			connectionsManager.sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					if (error == null) {
						globalPrivacySettings = response as? TL_globalPrivacySettings
						loadingGlobalSettings = 2
					}
					else {
						loadingGlobalSettings = 0
					}

					notificationCenter.postNotificationName(NotificationCenter.privacyRulesUpdated)
				}
			}
		}

		for (a in loadingPrivacyInfo.indices) {
			if (loadingPrivacyInfo[a] != 0) {
				continue
			}

			loadingPrivacyInfo[a] = 1

			val req = TL_account_getPrivacy()

			when (a) {
				PRIVACY_RULES_TYPE_LAST_SEEN -> req.key = TL_inputPrivacyKeyStatusTimestamp()
				PRIVACY_RULES_TYPE_INVITE -> req.key = TL_inputPrivacyKeyChatInvite()
				PRIVACY_RULES_TYPE_CALLS -> req.key = TL_inputPrivacyKeyPhoneCall()
				PRIVACY_RULES_TYPE_P2P -> req.key = TL_inputPrivacyKeyPhoneP2P()
				PRIVACY_RULES_TYPE_PHOTO -> req.key = TL_inputPrivacyKeyProfilePhoto()
				PRIVACY_RULES_TYPE_FORWARDS -> req.key = TL_inputPrivacyKeyForwards()
				PRIVACY_RULES_TYPE_PHONE -> req.key = TL_inputPrivacyKeyPhoneNumber()
				PRIVACY_RULES_TYPE_VOICE_MESSAGES -> req.key = TL_inputPrivacyKeyVoiceMessages()
				PRIVACY_RULES_TYPE_ADDED_BY_PHONE -> req.key = TL_inputPrivacyKeyAddedByPhone()
				else -> req.key = TL_inputPrivacyKeyAddedByPhone()
			}

			connectionsManager.sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					if (error == null) {
						val rules = response as TL_account_privacyRules

						messagesController.putUsers(rules.users, false)
						messagesController.putChats(rules.chats, false)

						when (a) {
							PRIVACY_RULES_TYPE_LAST_SEEN -> lastSeenPrivacyRules = rules.rules
							PRIVACY_RULES_TYPE_INVITE -> groupPrivacyRules = rules.rules
							PRIVACY_RULES_TYPE_CALLS -> callPrivacyRules = rules.rules
							PRIVACY_RULES_TYPE_P2P -> p2pPrivacyRules = rules.rules
							PRIVACY_RULES_TYPE_PHOTO -> profilePhotoPrivacyRules = rules.rules
							PRIVACY_RULES_TYPE_FORWARDS -> forwardsPrivacyRules = rules.rules
							PRIVACY_RULES_TYPE_PHONE -> phonePrivacyRules = rules.rules
							PRIVACY_RULES_TYPE_VOICE_MESSAGES -> voiceMessagesRules = rules.rules
							PRIVACY_RULES_TYPE_ADDED_BY_PHONE -> addedByPhonePrivacyRules = rules.rules
							else -> addedByPhonePrivacyRules = rules.rules
						}

						loadingPrivacyInfo[a] = 2
					}
					else {
						loadingPrivacyInfo[a] = 0
					}

					notificationCenter.postNotificationName(NotificationCenter.privacyRulesUpdated)
				}
			}
		}

		notificationCenter.postNotificationName(NotificationCenter.privacyRulesUpdated)
	}

	fun getLoadingDeleteInfo(): Boolean {
		return loadingDeleteInfo != 2
	}

	fun getLoadingGlobalSettings(): Boolean {
		return loadingGlobalSettings != 2
	}

	fun getLoadingPrivacyInfo(type: Int): Boolean {
		return loadingPrivacyInfo[type] != 2
	}

	fun getPrivacyRules(type: Int): ArrayList<PrivacyRule>? {
		return when (type) {
			PRIVACY_RULES_TYPE_LAST_SEEN -> lastSeenPrivacyRules
			PRIVACY_RULES_TYPE_INVITE -> groupPrivacyRules
			PRIVACY_RULES_TYPE_CALLS -> callPrivacyRules
			PRIVACY_RULES_TYPE_P2P -> p2pPrivacyRules
			PRIVACY_RULES_TYPE_PHOTO -> profilePhotoPrivacyRules
			PRIVACY_RULES_TYPE_FORWARDS -> forwardsPrivacyRules
			PRIVACY_RULES_TYPE_PHONE -> phonePrivacyRules
			PRIVACY_RULES_TYPE_ADDED_BY_PHONE -> addedByPhonePrivacyRules
			PRIVACY_RULES_TYPE_VOICE_MESSAGES -> voiceMessagesRules
			else -> null
		}
	}

	fun setPrivacyRules(rules: ArrayList<PrivacyRule>?, type: Int) {
		when (type) {
			PRIVACY_RULES_TYPE_LAST_SEEN -> lastSeenPrivacyRules = rules
			PRIVACY_RULES_TYPE_INVITE -> groupPrivacyRules = rules
			PRIVACY_RULES_TYPE_CALLS -> callPrivacyRules = rules
			PRIVACY_RULES_TYPE_P2P -> p2pPrivacyRules = rules
			PRIVACY_RULES_TYPE_PHOTO -> profilePhotoPrivacyRules = rules
			PRIVACY_RULES_TYPE_FORWARDS -> forwardsPrivacyRules = rules
			PRIVACY_RULES_TYPE_PHONE -> phonePrivacyRules = rules
			PRIVACY_RULES_TYPE_ADDED_BY_PHONE -> addedByPhonePrivacyRules = rules
			PRIVACY_RULES_TYPE_VOICE_MESSAGES -> voiceMessagesRules = rules
		}

		notificationCenter.postNotificationName(NotificationCenter.privacyRulesUpdated)

		reloadContactsStatuses()
	}

	companion object {
		const val PRIVACY_RULES_TYPE_LAST_SEEN = 0
		const val PRIVACY_RULES_TYPE_INVITE = 1
		const val PRIVACY_RULES_TYPE_CALLS = 2
		const val PRIVACY_RULES_TYPE_P2P = 3
		const val PRIVACY_RULES_TYPE_PHOTO = 4
		const val PRIVACY_RULES_TYPE_FORWARDS = 5
		const val PRIVACY_RULES_TYPE_PHONE = 6
		const val PRIVACY_RULES_TYPE_ADDED_BY_PHONE = 7
		const val PRIVACY_RULES_TYPE_VOICE_MESSAGES = 8
		const val PRIVACY_RULES_TYPE_COUNT = 9
		private val Instance = arrayOfNulls<ContactsController>(UserConfig.MAX_ACCOUNT_COUNT)

		@JvmStatic
		fun getInstance(num: Int): ContactsController {
			var localInstance = Instance[num]

			if (localInstance == null) {
				synchronized(ContactsController::class.java) {
					localInstance = Instance[num]

					if (localInstance == null) {
						localInstance = ContactsController(num)
						Instance[num] = localInstance
					}
				}
			}

			return localInstance!!
		}

		fun formatName(`object`: TLObject?): String? {
			return when (`object`) {
				is User -> formatName(`object`)
				is Chat -> `object`.title
				else -> "DELETED"
			}
		}

		@JvmStatic
		fun formatName(user: User?): String {
			if (user == null) {
				return ""
			}

			return formatName(user.first_name, user.last_name, 0)
		}

		@JvmStatic
		@JvmOverloads
		fun formatName(firstName: String?, lastName: String?, maxLength: Int = 0): String {
			@Suppress("NAME_SHADOWING") var firstName = firstName
			@Suppress("NAME_SHADOWING") var lastName = lastName

			if (!firstName.isNullOrEmpty()) {
				firstName = firstName.trim { it <= ' ' }
			}

			if (!lastName.isNullOrEmpty()) {
				lastName = lastName.trim { it <= ' ' }
			}

			val result = StringBuilder((firstName?.length ?: 0) + (lastName?.length ?: 0) + 1)

			if (LocaleController.nameDisplayOrder == 1) {
				if (!firstName.isNullOrEmpty()) {
					if (maxLength > 0 && firstName.length > maxLength + 2) {
						return firstName.substring(0, maxLength)
					}

					result.append(firstName)

					if (!lastName.isNullOrEmpty()) {
						result.append(" ")

						if (maxLength > 0 && result.length + lastName.length > maxLength) {
							result.append(lastName[0])
						}
						else {
							result.append(lastName)
						}
					}
				}
				else if (!lastName.isNullOrEmpty()) {
					if (maxLength > 0 && lastName.length > maxLength + 2) {
						return lastName.substring(0, maxLength)
					}

					result.append(lastName)
				}
			}
			else {
				if (!lastName.isNullOrEmpty()) {
					if (maxLength > 0 && lastName.length > maxLength + 2) {
						return lastName.substring(0, maxLength)
					}

					result.append(lastName)

					if (!firstName.isNullOrEmpty()) {
						result.append(" ")

						if (maxLength > 0 && result.length + firstName.length > maxLength) {
							result.append(firstName[0])
						}
						else {
							result.append(firstName)
						}
					}
				}
				else if (!firstName.isNullOrEmpty()) {
					if (maxLength > 0 && firstName.length > maxLength + 2) {
						return firstName.substring(0, maxLength)
					}

					result.append(firstName)
				}
			}

			return result.toString()
		}
	}
}
