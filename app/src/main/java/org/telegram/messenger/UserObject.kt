/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger

import org.telegram.tgnet.TLRPC.TL_userContact_old2
import org.telegram.tgnet.TLRPC.TL_userDeleted_old2
import org.telegram.tgnet.TLRPC.TL_userEmpty
import org.telegram.tgnet.tlrpc.TL_userProfilePhotoEmpty
import org.telegram.tgnet.TLRPC.TL_userSelf_old3
import org.telegram.tgnet.TLRPC.UserProfilePhoto
import org.telegram.tgnet.tlrpc.User
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
object UserObject {
	@JvmStatic
	fun isDeleted(user: User?): Boolean {
		return user == null || user is TL_userDeleted_old2 || user is TL_userEmpty || user.deleted
	}

	@JvmStatic
	fun isContact(user: User?): Boolean {
		return user != null && (user is TL_userContact_old2 || user.contact || user.mutual_contact)
	}

	@JvmStatic
	fun isUserSelf(user: User?): Boolean {
		return user != null && (user is TL_userSelf_old3 || user.self)
	}

	@JvmStatic
	fun isReplyUser(user: User?): Boolean {
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
	fun getUserName(user: User?): String {
		if (user == null || isDeleted(user)) {
			return ApplicationLoader.applicationContext.getString(R.string.HiddenName)
		}

		return ContactsController.formatName(user.first_name, user.last_name)
	}

	@JvmStatic
	fun getFirstName(user: User?): String {
		return getFirstName(user, true)
	}

	@JvmStatic
	fun getFirstName(user: User?, allowShort: Boolean): String {
		if (user == null || isDeleted(user)) {
			return "DELETED"
		}

		var name = user.first_name

		if (name.isNullOrEmpty()) {
			name = user.last_name
		}
		else if (!allowShort && name.length <= 2) {
			return ContactsController.formatName(user.first_name, user.last_name)
		}

		return if (!name.isNullOrEmpty()) name else ApplicationLoader.applicationContext.getString(R.string.HiddenName)
	}

	@JvmStatic
	fun hasPhoto(user: User?): Boolean {
		return user?.photo != null && user.photo !is TL_userProfilePhotoEmpty
	}

	@JvmStatic
	fun getPhoto(user: User?): UserProfilePhoto? {
		return if (hasPhoto(user)) user?.photo else null
	}
}
