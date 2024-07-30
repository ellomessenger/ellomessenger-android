/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Components.Reactions

import android.text.TextUtils
import androidx.core.text.buildSpannedString
import org.telegram.tgnet.tlrpc.Reaction
import org.telegram.tgnet.tlrpc.TL_reactionCustomEmoji
import org.telegram.tgnet.tlrpc.TL_reactionEmoji
import org.telegram.ui.Components.AnimatedEmojiSpan

object ReactionsUtils {
	const val APPEAR_ANIMATION_FILTER = "30_30_nolimit_pcache"
	const val SELECT_ANIMATION_FILTER = "60_60_pcache"
	const val ACTIVATE_ANIMATION_FILTER = "30_30_pcache"

	@JvmStatic
	fun compare(reaction: Reaction?, visibleReaction: VisibleReaction?): Boolean {
		if (reaction is TL_reactionEmoji && visibleReaction?.documentId == 0L && TextUtils.equals(reaction.emoticon, visibleReaction.emojicon)) {
			return true
		}

		return reaction is TL_reactionCustomEmoji && visibleReaction?.documentId != 0L && reaction.documentId == visibleReaction?.documentId
	}

	fun toTLReaction(visibleReaction: VisibleReaction): Reaction {
		return if (visibleReaction.emojicon != null) {
			val emoji = TL_reactionEmoji()
			emoji.emoticon = visibleReaction.emojicon
			emoji
		}
		else {
			val emoji = TL_reactionCustomEmoji()
			emoji.documentId = visibleReaction.documentId
			emoji
		}
	}

	@JvmStatic
	fun reactionToCharSequence(reaction: Reaction?): CharSequence {
		return when (reaction) {
			is TL_reactionEmoji -> {
				reaction.emoticon ?: ""
			}

			is TL_reactionCustomEmoji -> {
				buildSpannedString {
					append("d")
					setSpan(AnimatedEmojiSpan(reaction.documentId, null), 0, 1, 0)
				}
			}

			else -> {
				""
			}
		}
	}
}
