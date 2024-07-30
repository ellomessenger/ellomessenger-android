/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.BotInfo
import org.telegram.tgnet.TLRPC.PeerNotifySettings
import org.telegram.tgnet.TLRPC.TL_chatAdminRights
import org.telegram.tgnet.TLRPC.TL_contacts_link_layer101
import org.telegram.tgnet.TLRPC.TL_peerSettings
import org.telegram.tgnet.TLRPC.TL_premiumGiftOption

abstract class UserFull : TLObject() {
	@JvmField
	var flags = 0

	var blocked = false
	var phone_calls_available = false
	var phone_calls_private = false

	var can_pin_message = false
		get() = false // MARK: remove to enable messages pinning

	var has_scheduled = false

	@JvmField
	var video_calls_available = false

	var voice_messages_forbidden = false

	@JvmField
	var user: User? = null

	@JvmField
	var about: String? = null

	var link: TL_contacts_link_layer101? = null

	@JvmField
	var profile_photo: TLRPC.Photo? = null

	var notify_settings: PeerNotifySettings? = null

	@JvmField
	var bot_info: BotInfo? = null

	@JvmField
	var pinned_msg_id = 0

	var common_chats_count = 0

	@JvmField
	var folder_id = 0

	@JvmField
	var ttl_period = 0

	var settings: TL_peerSettings? = null
	var theme_emoticon: String? = null

	@JvmField
	var id = 0L

	var private_forward_name: String? = null
	var bot_group_admin_rights: TL_chatAdminRights? = null
	var bot_broadcast_admin_rights: TL_chatAdminRights? = null

	@JvmField
	var premium_gifts = ArrayList<TL_premiumGiftOption>()

	var is_public = false
	var is_business = false
	var email: String? = null
	var country: String? = null
	var gender: String? = null
	var date_of_birth = 0

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): UserFull? {
			var result: UserFull? = null

			when (constructor) {
				-0x3b4e03c1 -> result = TL_userFull()
				-0x738d157f -> result = TL_userFull_layer143()
				-0x30c99adf -> result = TL_userFull_layer139()
				-0x296800fb -> result = TL_userFull_layer134()
				0x139a9a77 -> result = TL_userFull_layer131()
				0x745559cc -> result = TL_userFull_layer101()
				-0x715b577f -> result = TL_userFull_layer98()
				-0x120e83ee -> result = TL_userFull_layer123()
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in UserFull", constructor))
			}

			result?.readParams(stream, exception)

			return result
		}
	}
}
