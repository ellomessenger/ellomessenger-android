/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.tgnet

import android.util.SparseArray
import org.telegram.messenger.FileLog
import org.telegram.tgnet.TLRPC.TLError
import org.telegram.tgnet.TLRPC.TLNull
import org.telegram.tgnet.TLRPC.TLUpdateShort
import org.telegram.tgnet.TLRPC.TLUpdateShortChatMessage
import org.telegram.tgnet.TLRPC.TLUpdateShortMessage
import org.telegram.tgnet.TLRPC.TLUpdateShortSentMessage
import org.telegram.tgnet.TLRPC.TLUpdates
import org.telegram.tgnet.TLRPC.TLUpdatesCombined
import org.telegram.tgnet.TLRPC.TLUpdatesTooLong

class TLClassStore {
	private val classStore = SparseArray<Class<*>>()

	init {
		classStore.put(TLError.CONSTRUCTOR, TLError::class.java)
		classStore.put(TLNull.CONSTRUCTOR, TLNull::class.java)
		classStore.put(TLUpdateShortChatMessage.CONSTRUCTOR, TLUpdateShortChatMessage::class.java)
		classStore.put(TLUpdates.CONSTRUCTOR, TLUpdates::class.java)
		classStore.put(TLUpdateShortMessage.CONSTRUCTOR, TLUpdateShortMessage::class.java)
		classStore.put(TLUpdateShort.CONSTRUCTOR, TLUpdateShort::class.java)
		classStore.put(TLUpdatesCombined.CONSTRUCTOR, TLUpdatesCombined::class.java)
		classStore.put(TLUpdateShortSentMessage.CONSTRUCTOR, TLUpdateShortSentMessage::class.java)
		classStore.put(TLUpdatesTooLong.CONSTRUCTOR, TLUpdatesTooLong::class.java)
	}

	fun deserialize(stream: NativeByteBuffer?, constructor: Int, exception: Boolean): TLObject? {
		val objClass = classStore[constructor]

		if (objClass != null) {
			val response: TLObject

			try {
				response = objClass.getDeclaredConstructor().newInstance() as TLObject
			}
			catch (e: Throwable) {
				FileLog.e(e)
				return null
			}

			response.readParams(stream, exception)
			response.postDeserialize()

			return response
		}

		return null
	}

	companion object {
		val instance by lazy { TLClassStore() }
	}
}
