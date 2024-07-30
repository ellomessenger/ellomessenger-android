/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.R
import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.TLRPC.EmojiStatus
import org.telegram.tgnet.TLRPC.TL_restrictionReason
import org.telegram.tgnet.TLRPC.TL_user
import org.telegram.tgnet.TLRPC.TL_userContact_old
import org.telegram.tgnet.TLRPC.TL_userContact_old2
import org.telegram.tgnet.TLRPC.TL_userDeleted_old
import org.telegram.tgnet.TLRPC.TL_userDeleted_old2
import org.telegram.tgnet.TLRPC.TL_userEmpty
import org.telegram.tgnet.TLRPC.TL_userEmpty_layer131
import org.telegram.tgnet.TLRPC.TL_userForeign_old
import org.telegram.tgnet.TLRPC.TL_userForeign_old2
import org.telegram.tgnet.TLRPC.TL_userRequest_old
import org.telegram.tgnet.TLRPC.TL_userRequest_old2
import org.telegram.tgnet.TLRPC.TL_userSelf_old
import org.telegram.tgnet.TLRPC.TL_userSelf_old2
import org.telegram.tgnet.TLRPC.TL_userSelf_old3
import org.telegram.tgnet.TLRPC.TL_user_layer104
import org.telegram.tgnet.TLRPC.TL_user_layer131
import org.telegram.tgnet.TLRPC.TL_user_layer144
import org.telegram.tgnet.TLRPC.TL_user_layer65
import org.telegram.tgnet.TLRPC.TL_user_old
import org.telegram.tgnet.TLRPC.UserProfilePhoto
import org.telegram.tgnet.TLRPC.UserStatus

abstract class User : TLObject() {
	@JvmField
	var id: Long = 0

	var first_name: String? = null
		get() {
			if (id == BuildConfig.AI_BOT_ID) {
				return ApplicationLoader.applicationContext.getString(R.string.ai_chat_bot)
			}

			return field
		}

	var last_name: String? = null
		get() {
			if (id == BuildConfig.AI_BOT_ID) {
				return null
			}

			return field
		}

	@JvmField
	var username: String? = null

	@JvmField
	var access_hash: Long = 0

	@JvmField
	protected var phone: String? = null // MARK: replaced public with protected to hide phone

	@JvmField
	var photo: UserProfilePhoto? = null

	@JvmField
	var status: UserStatus? = null

	@JvmField
	var flags = 0

	@JvmField
	var self = false

	@JvmField
	var contact = false

	@JvmField
	var mutual_contact = false

	@JvmField
	var deleted = false

	@JvmField
	var bot = false

	@JvmField
	var bot_chat_history = false

	@JvmField
	var bot_nochats = false

	@JvmField
	var verified = false

	@JvmField
	var restricted = false

	@JvmField
	var min = false

	@JvmField
	var bot_inline_geo = false

	@JvmField
	var support = false

	@JvmField
	var scam = false

	@JvmField
	var apply_min_photo = false

	@JvmField
	var fake = false

	@JvmField
	var premium = false

	@JvmField
	var bot_info_version = 0

	@JvmField
	var bot_inline_placeholder: String? = null

	@JvmField
	var lang_code: String? = null

	@JvmField
	var inactive = false

	@JvmField
	var explicit_content = false

	@JvmField
	var restriction_reason = ArrayList<TL_restrictionReason>()

	@JvmField
	var bot_attach_menu = false

	@JvmField
	var bot_menu_webview = false

	@JvmField
	var attach_menu_enabled = false

	@JvmField
	var emoji_status: EmojiStatus? = null

	@JvmField
	var is_public = false

	@JvmField
	var is_business = false

	@JvmField
	var bot_description: String? = null

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): User? {
			val result = when (constructor) {
				-0x354ca1e8 -> TL_userContact_old2()
				-0xd047ce7 -> TL_userContact_old()
				-0x2c43b486 -> TL_userEmpty()
				0x5d99adee -> TL_user()
				0x3ff6ecb0 -> TL_user_layer144()
				-0x6c7ba73f -> TL_user_layer131()
				0x2e13f4c3 -> TL_user_layer104()
				0x720535ec -> TL_userSelf_old()
				0x1c60e608 -> TL_userSelf_old3()
				-0x29fe9286 -> TL_userDeleted_old2()
				0x200250ba -> TL_userEmpty_layer131()
				0x22e8ceb0 -> TL_userRequest_old()
				0x5214c89d -> TL_userForeign_old()
				0x75cf7a8 -> TL_userForeign_old2()
				-0x26333b11 -> TL_userRequest_old2()
				-0x4d652834 -> TL_userDeleted_old()
				-0x2ef26866 -> TL_user_layer65()
				0x22e49072 -> TL_user_old()
				0x7007b451 -> TL_userSelf_old2()
				else -> null
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in User", constructor))
			}

			result?.readParams(stream, exception)

			return result
		}
	}
}
