/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.messenger

import org.telegram.tgnet.TLRPC
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
object UserObject {
	@JvmStatic
	fun isDeleted(user: TLRPC.User?): Boolean {
		return user == null || user is TLRPC.TLUserEmpty || (user as? TLRPC.TLUser)?.deleted == true
	}

	@JvmStatic
	fun isContact(user: TLRPC.User?): Boolean {
		@Suppress("NAME_SHADOWING") val user = user as? TLRPC.TLUser ?: return false
		return user.contact || user.mutualContact
	}

	@JvmStatic
	fun isUserSelf(user: TLRPC.User?): Boolean {
		@Suppress("NAME_SHADOWING") val user = user as? TLRPC.TLUser ?: return false
		return user.isSelf
	}

	@JvmStatic
	fun isReplyUser(user: TLRPC.User?): Boolean {
		contract {
			returns(true) implies (user != null)
		}

		return user != null && (user.id == 708513L || user.id == 1271266957L)
	}

	@JvmStatic
	fun isReplyUser(did: Long): Boolean {
		return did == 708513L || did == 1271266957L
	}

	@JvmStatic
	fun getUserName(user: TLRPC.User?): String {
		if (user == null || isDeleted(user)) {
			return ApplicationLoader.applicationContext.getString(R.string.HiddenName)
		}

		return ContactsController.formatName(user.firstName, user.lastName)
	}

	@JvmStatic
	fun getFirstName(user: TLRPC.User?): String {
		return getFirstName(user, true)
	}

	@JvmStatic
	fun getFirstName(user: TLRPC.User?, allowShort: Boolean): String {
		if (user == null || isDeleted(user)) {
			return "DELETED"
		}

		var name = user.firstName

		if (name.isNullOrEmpty()) {
			name = user.lastName
		}
		else if (!allowShort && name.length <= 2) {
			return ContactsController.formatName(user.firstName, user.lastName)
		}

		return if (!name.isNullOrEmpty()) name else ApplicationLoader.applicationContext.getString(R.string.HiddenName)
	}

	@JvmStatic
	fun hasPhoto(user: TLRPC.User?): Boolean {
		@Suppress("NAME_SHADOWING") val user = user as? TLRPC.TLUser ?: return false
		return user.photo != null && user.photo !is TLRPC.TLUserProfilePhotoEmpty
	}

	@JvmStatic
	fun getPhoto(user: TLRPC.User?): TLRPC.UserProfilePhoto? {
		@Suppress("NAME_SHADOWING") val user = user as? TLRPC.TLUser ?: return null
		return if (hasPhoto(user)) user.photo else null
	}
}
