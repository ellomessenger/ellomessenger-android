/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Components.Reactions

import org.telegram.tgnet.tlrpc.Reaction
import org.telegram.tgnet.tlrpc.TL_availableReaction
import org.telegram.tgnet.tlrpc.TL_reactionCustomEmoji
import org.telegram.tgnet.tlrpc.TL_reactionEmoji
import java.util.Objects

class VisibleReaction {
	@JvmField
	var emojicon: String? = null

	@JvmField
	var documentId: Long = 0

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}

		if (other == null || javaClass != other.javaClass) {
			return false
		}

		val that = other as VisibleReaction

		return documentId == that.documentId && emojicon == that.emojicon
	}

	override fun hashCode(): Int {
		return Objects.hash(emojicon, documentId)
	}

	companion object {
		@JvmStatic
		fun fromTLReaction(reaction: Reaction?): VisibleReaction {
			val visibleReaction = VisibleReaction()

			if (reaction is TL_reactionEmoji) {
				visibleReaction.emojicon = reaction.emoticon
			}
			else if (reaction is TL_reactionCustomEmoji) {
				visibleReaction.documentId = reaction.documentId
			}

			return visibleReaction
		}

		@JvmStatic
		fun fromEmojicon(reaction: TL_availableReaction): VisibleReaction {
			val visibleReaction = VisibleReaction()
			visibleReaction.emojicon = reaction.reaction
			return visibleReaction
		}

		@JvmStatic
		fun fromEmojicon(reaction: String): VisibleReaction {
			val visibleReaction = VisibleReaction()

			if (reaction.startsWith("animated_")) {
				try {
					visibleReaction.documentId = reaction.substring(9).toLong()
				}
				catch (ignore: Exception) {
					visibleReaction.emojicon = reaction
				}
			}
			else {
				visibleReaction.emojicon = reaction
			}

			return visibleReaction
		}

		@JvmStatic
		fun fromCustomEmoji(documentId: Long): VisibleReaction {
			val visibleReaction = VisibleReaction()
			visibleReaction.documentId = documentId
			return visibleReaction
		}
	}
}
