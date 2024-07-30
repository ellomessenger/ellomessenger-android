/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Oleksandr Nahornyi, Ello 2023-2024.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.tgnet.AbstractSerializedData

abstract class MessageEntity : TLObject() {
	@JvmField
	var offset = 0

	@JvmField
	var length = 0

	@JvmField
	var url: String? = null

	@JvmField
	var language: String? = null

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): MessageEntity? {
			var result: MessageEntity? = null

			when (constructor) {
				TL_messageEntityTextUrl.CONSTRUCTOR -> result = TL_messageEntityTextUrl()
				TL_messageEntityBotCommand.CONSTRUCTOR -> result = TL_messageEntityBotCommand()
				TL_messageEntityEmail.CONSTRUCTOR -> result = TL_messageEntityEmail()
				TL_messageEntityPre.CONSTRUCTOR -> result = TL_messageEntityPre()
				TL_messageEntityUnknown.CONSTRUCTOR -> result = TL_messageEntityUnknown()
				TL_messageEntityUrl.CONSTRUCTOR -> result = TL_messageEntityUrl()
				TL_messageEntityItalic.CONSTRUCTOR -> result = TL_messageEntityItalic()
				TL_messageEntityMention.CONSTRUCTOR -> result = TL_messageEntityMention()
				TL_messageEntitySpoiler.CONSTRUCTOR -> result = TL_messageEntitySpoiler()
				TL_messageEntityMentionName_layer131.CONSTRUCTOR -> result = TL_messageEntityMentionName_layer131()
				TL_inputMessageEntityMentionName.CONSTRUCTOR -> result = TL_inputMessageEntityMentionName()
				TL_messageEntityAnimatedEmoji.CONSTRUCTOR -> result = TL_messageEntityAnimatedEmoji()
				TL_messageEntityCashtag.CONSTRUCTOR -> result = TL_messageEntityCashtag()
				TL_messageEntityBold.CONSTRUCTOR -> result = TL_messageEntityBold()
				TL_messageEntityHashtag.CONSTRUCTOR -> result = TL_messageEntityHashtag()
				TL_messageEntityCode.CONSTRUCTOR -> result = TL_messageEntityCode()
				TL_messageEntityStrike.CONSTRUCTOR -> result = TL_messageEntityStrike()
				TL_messageEntityBlockquote.CONSTRUCTOR -> result = TL_messageEntityBlockquote()
				TL_messageEntityUnderline.CONSTRUCTOR -> result = TL_messageEntityUnderline()
				TL_messageEntityBankCard.CONSTRUCTOR -> result = TL_messageEntityBankCard()
				TL_messageEntityPhone.CONSTRUCTOR -> result = TL_messageEntityPhone()
				TL_messageEntityMentionName.CONSTRUCTOR -> result = TL_messageEntityMentionName()
				TL_messageEntityCustomEmoji.CONSTRUCTOR -> result = TL_messageEntityCustomEmoji()
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in MessageEntity", constructor))
			}

			result?.readParams(stream, exception)
			return result
		}
	}
}
