/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components.Reactions

import org.telegram.tgnet.TLRPC
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
		fun fromTLReaction(reaction: TLRPC.Reaction?): VisibleReaction {
			val visibleReaction = VisibleReaction()

			if (reaction is TLRPC.TLReactionEmoji) {
				visibleReaction.emojicon = reaction.emoticon
			}
			else if (reaction is TLRPC.TLReactionCustomEmoji) {
				visibleReaction.documentId = reaction.documentId
			}

			return visibleReaction
		}

		@JvmStatic
		fun fromEmojicon(reaction: TLRPC.TLAvailableReaction): VisibleReaction {
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
