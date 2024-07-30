/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.messenger.FileLog
import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.TLRPC.TL_dcOption

class TL_config : TLObject() {
	var flags = 0
	var phonecalls_enabled = false
	var default_p2p_contacts = false
	var preload_featured_stickers = false
	var ignore_phone_entities = false
	var revoke_pm_inbox = false
	var blocked_mode = false
	var pfs_enabled = false
	var force_try_ipv6 = false
	var date = 0
	var expires = 0
	var test_mode = false
	var this_dc = 0
	val dc_options = mutableListOf<TL_dcOption>()
	var dc_txt_domain_name: String? = null
	var chat_size_max = 0
	var megagroup_size_max = 0
	var forwarded_count_max = 0
	var online_update_period_ms = 0
	var offline_blur_timeout_ms = 0
	var offline_idle_timeout_ms = 0
	var online_cloud_timeout_ms = 0
	var notify_cloud_delay_ms = 0
	var notify_default_delay_ms = 0
	var push_chat_period_ms = 0
	var push_chat_limit = 0
	var saved_gifs_limit = 0
	var edit_time_limit = 0
	var revoke_time_limit = 0
	var revoke_pm_time_limit = 0
	var rating_e_decay = 0
	var stickers_recent_limit = 0
	var stickers_faved_limit = 0
	var channels_read_media_period = 0
	var tmp_sessions = 0
	var pinned_dialogs_count_max = 0
	var pinned_infolder_count_max = 0
	var call_receive_timeout_ms = 0
	var call_ring_timeout_ms = 0
	var call_connect_timeout_ms = 0
	var call_packet_timeout_ms = 0
	var me_url_prefix: String? = null
	var autoupdate_url_prefix: String? = null
	var gif_search_username: String? = null
	var venue_search_username: String? = null
	var img_search_username: String? = null
	var static_maps_provider: String? = null
	var caption_length_max = 0
	var message_length_max = 0
	var webfile_dc_id = 0
	var suggested_lang_code: String? = null
	var lang_pack_version = 0
	var base_lang_pack_version = 0
	var reactions_default: Reaction? = null

	override fun readParams(stream: AbstractSerializedData?, exception: Boolean) {
		if (stream == null) {
			if (exception) {
				throw RuntimeException("Input stream is null")
			}
			else {
				FileLog.e("Input stream is null")
			}

			return
		}

		flags = stream.readInt32(exception)
		phonecalls_enabled = flags and 2 != 0
		default_p2p_contacts = flags and 8 != 0
		preload_featured_stickers = flags and 16 != 0
		ignore_phone_entities = flags and 32 != 0
		revoke_pm_inbox = flags and 64 != 0
		blocked_mode = flags and 256 != 0
		pfs_enabled = flags and 8192 != 0
		force_try_ipv6 = flags and 16384 != 0
		date = stream.readInt32(exception)
		expires = stream.readInt32(exception)
		test_mode = stream.readBool(exception)
		this_dc = stream.readInt32(exception)

		val magic = stream.readInt32(exception)

		if (magic != 0x1cb5c415) {
			if (exception) {
				throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
			}

			return
		}

		val count = stream.readInt32(exception)

		for (a in 0 until count) {
			val `object` = TL_dcOption.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
			dc_options.add(`object`)
		}

		dc_txt_domain_name = stream.readString(exception)
		chat_size_max = stream.readInt32(exception)
		megagroup_size_max = stream.readInt32(exception)
		forwarded_count_max = stream.readInt32(exception)
		online_update_period_ms = stream.readInt32(exception)
		offline_blur_timeout_ms = stream.readInt32(exception)
		offline_idle_timeout_ms = stream.readInt32(exception)
		online_cloud_timeout_ms = stream.readInt32(exception)
		notify_cloud_delay_ms = stream.readInt32(exception)
		notify_default_delay_ms = stream.readInt32(exception)
		push_chat_period_ms = stream.readInt32(exception)
		push_chat_limit = stream.readInt32(exception)
		saved_gifs_limit = stream.readInt32(exception)
		edit_time_limit = stream.readInt32(exception)
		revoke_time_limit = stream.readInt32(exception)
		revoke_pm_time_limit = stream.readInt32(exception)
		rating_e_decay = stream.readInt32(exception)
		stickers_recent_limit = stream.readInt32(exception)
		stickers_faved_limit = stream.readInt32(exception)
		channels_read_media_period = stream.readInt32(exception)

		if (flags and 1 != 0) {
			tmp_sessions = stream.readInt32(exception)
		}

		pinned_dialogs_count_max = stream.readInt32(exception)
		pinned_infolder_count_max = stream.readInt32(exception)
		call_receive_timeout_ms = stream.readInt32(exception)
		call_ring_timeout_ms = stream.readInt32(exception)
		call_connect_timeout_ms = stream.readInt32(exception)
		call_packet_timeout_ms = stream.readInt32(exception)
		me_url_prefix = stream.readString(exception)

		if (flags and 128 != 0) {
			autoupdate_url_prefix = stream.readString(exception)
		}

		if (flags and 512 != 0) {
			gif_search_username = stream.readString(exception)
		}

		if (flags and 1024 != 0) {
			venue_search_username = stream.readString(exception)
		}

		if (flags and 2048 != 0) {
			img_search_username = stream.readString(exception)
		}

		if (flags and 4096 != 0) {
			static_maps_provider = stream.readString(exception)
		}

		caption_length_max = stream.readInt32(exception)
		message_length_max = stream.readInt32(exception)
		webfile_dc_id = stream.readInt32(exception)

		if (flags and 4 != 0) {
			suggested_lang_code = stream.readString(exception)
		}

		if (flags and 4 != 0) {
			lang_pack_version = stream.readInt32(exception)
		}

		if (flags and 4 != 0) {
			base_lang_pack_version = stream.readInt32(exception)
		}

		if (flags and 32768 != 0) {
			reactions_default = Reaction.TLdeserialize(stream, stream.readInt32(exception), exception)
		}
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)

		flags = if (phonecalls_enabled) flags or 2 else flags and 2.inv()
		flags = if (default_p2p_contacts) flags or 8 else flags and 8.inv()
		flags = if (preload_featured_stickers) flags or 16 else flags and 16.inv()
		flags = if (ignore_phone_entities) flags or 32 else flags and 32.inv()
		flags = if (revoke_pm_inbox) flags or 64 else flags and 64.inv()
		flags = if (blocked_mode) flags or 256 else flags and 256.inv()
		flags = if (pfs_enabled) flags or 8192 else flags and 8192.inv()
		flags = if (force_try_ipv6) flags or 16384 else flags and 16384.inv()

		stream.writeInt32(flags)
		stream.writeInt32(date)
		stream.writeInt32(expires)
		stream.writeBool(test_mode)
		stream.writeInt32(this_dc)
		stream.writeInt32(0x1cb5c415)

		val count = dc_options.size

		stream.writeInt32(count)

		for (a in 0 until count) {
			dc_options[a].serializeToStream(stream)
		}

		stream.writeString(dc_txt_domain_name)
		stream.writeInt32(chat_size_max)
		stream.writeInt32(megagroup_size_max)
		stream.writeInt32(forwarded_count_max)
		stream.writeInt32(online_update_period_ms)
		stream.writeInt32(offline_blur_timeout_ms)
		stream.writeInt32(offline_idle_timeout_ms)
		stream.writeInt32(online_cloud_timeout_ms)
		stream.writeInt32(notify_cloud_delay_ms)
		stream.writeInt32(notify_default_delay_ms)
		stream.writeInt32(push_chat_period_ms)
		stream.writeInt32(push_chat_limit)
		stream.writeInt32(saved_gifs_limit)
		stream.writeInt32(edit_time_limit)
		stream.writeInt32(revoke_time_limit)
		stream.writeInt32(revoke_pm_time_limit)
		stream.writeInt32(rating_e_decay)
		stream.writeInt32(stickers_recent_limit)
		stream.writeInt32(stickers_faved_limit)
		stream.writeInt32(channels_read_media_period)

		if (flags and 1 != 0) {
			stream.writeInt32(tmp_sessions)
		}

		stream.writeInt32(pinned_dialogs_count_max)
		stream.writeInt32(pinned_infolder_count_max)
		stream.writeInt32(call_receive_timeout_ms)
		stream.writeInt32(call_ring_timeout_ms)
		stream.writeInt32(call_connect_timeout_ms)
		stream.writeInt32(call_packet_timeout_ms)
		stream.writeString(me_url_prefix)

		if (flags and 128 != 0) {
			stream.writeString(autoupdate_url_prefix)
		}

		if (flags and 512 != 0) {
			stream.writeString(gif_search_username)
		}

		if (flags and 1024 != 0) {
			stream.writeString(venue_search_username)
		}

		if (flags and 2048 != 0) {
			stream.writeString(img_search_username)
		}

		if (flags and 4096 != 0) {
			stream.writeString(static_maps_provider)
		}

		stream.writeInt32(caption_length_max)
		stream.writeInt32(message_length_max)
		stream.writeInt32(webfile_dc_id)

		if (flags and 4 != 0) {
			stream.writeString(suggested_lang_code)
		}

		if (flags and 4 != 0) {
			stream.writeInt32(lang_pack_version)
		}

		if (flags and 4 != 0) {
			stream.writeInt32(base_lang_pack_version)
		}

		if (flags and 32768 != 0) {
			reactions_default?.serializeToStream(stream)
		}
	}

	companion object {
		const val constructor = 0x232566ac

		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TL_config? {
			if (Companion.constructor != constructor) {
				return if (exception) {
					throw RuntimeException(String.format("can't parse magic %x in TL_config", constructor))
				}
				else {
					null
				}
			}

			val result = TL_config()
			result.readParams(stream, exception)
			return result
		}
	}
}
