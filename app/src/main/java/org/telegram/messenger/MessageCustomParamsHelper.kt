/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.messenger

import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.NativeByteBuffer
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.postDeserialize

object MessageCustomParamsHelper {
	@JvmStatic
	fun isEmpty(message: TLRPC.Message?): Boolean {
		if (message == null) {
			return true
		}

		return message.voiceTranscription == null && !message.voiceTranscriptionOpen && !message.voiceTranscriptionFinal && !message.voiceTranscriptionRated && message.voiceTranscriptionId == 0L && !message.premiumEffectWasPlayed
	}

	@JvmStatic
	fun copyParams(fromMessage: TLRPC.Message?, toMessage: TLRPC.Message?) {
		if (toMessage == null || fromMessage == null) {
			return
		}

		toMessage.voiceTranscription = fromMessage.voiceTranscription
		toMessage.voiceTranscriptionOpen = fromMessage.voiceTranscriptionOpen
		toMessage.voiceTranscriptionFinal = fromMessage.voiceTranscriptionFinal
		toMessage.voiceTranscriptionRated = fromMessage.voiceTranscriptionRated
		toMessage.voiceTranscriptionId = fromMessage.voiceTranscriptionId
		toMessage.premiumEffectWasPlayed = fromMessage.premiumEffectWasPlayed
	}

	@JvmStatic
	fun readLocalParams(message: TLRPC.Message?, byteBuffer: NativeByteBuffer?) {
		if (message == null || byteBuffer == null) {
			return
		}

		val version = byteBuffer.readInt32(true)
		val params: TLObject

		when (version) {
			1 -> params = ParamsV1(message)
			else -> throw RuntimeException("can't read params version = $version")
		}

		params.readParams(byteBuffer, true)
		params.postDeserialize()
	}

	@JvmStatic
	fun writeLocalParams(message: TLRPC.Message?): NativeByteBuffer? {
		if (message == null || isEmpty(message)) {
			return null
		}

		val params: TLObject = ParamsV1(message)

		try {
			val nativeByteBuffer = NativeByteBuffer(params.objectSize)
			params.serializeToStream(nativeByteBuffer)
			return nativeByteBuffer
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		return null
	}

	private class ParamsV1(val message: TLRPC.Message) : TLObject() {
		var flags: Int = 0

		init {
			flags += if (message.voiceTranscription != null) 1 else 0
		}

		override fun serializeToStream(stream: AbstractSerializedData?) {
			if (stream == null) {
				return
			}

			stream.writeInt32(VERSION)
			stream.writeInt32(flags)

			if ((flags and 1) != 0) {
				stream.writeString(message.voiceTranscription)
			}

			stream.writeBool(message.voiceTranscriptionOpen)
			stream.writeBool(message.voiceTranscriptionFinal)
			stream.writeBool(message.voiceTranscriptionRated)
			stream.writeInt64(message.voiceTranscriptionId)
			stream.writeBool(message.premiumEffectWasPlayed)
		}

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

			flags = stream.readInt32(true)

			if ((flags and 1) != 0) {
				message.voiceTranscription = stream.readString(exception)
			}

			message.voiceTranscriptionOpen = stream.readBool(exception)
			message.voiceTranscriptionFinal = stream.readBool(exception)
			message.voiceTranscriptionRated = stream.readBool(exception)
			message.voiceTranscriptionId = stream.readInt64(exception)
			message.premiumEffectWasPlayed = stream.readBool(exception)
		}

		companion object {
			private const val VERSION = 1
		}
	}
}
