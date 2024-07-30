/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.messenger.messageobject

import org.telegram.ui.Cells.ChatMessageCell

class TransitionParams {
    @JvmField
    var left = 0

    @JvmField
    var top = 0

    @JvmField
    var right = 0

    @JvmField
    var bottom = 0

    @JvmField
    var offsetLeft = 0f

    @JvmField
    var offsetTop = 0f

    @JvmField
    var offsetRight = 0f

    @JvmField
    var offsetBottom = 0f

    @JvmField
    var drawBackgroundForDeletedItems = false

    @JvmField
    var backgroundChangeBounds = false

    @JvmField
    var pinnedTop = false

    @JvmField
    var pinnedBottom = false

    @JvmField
    var cell: ChatMessageCell? = null

    @JvmField
    var captionEnterProgress = 1f

    @JvmField
    var drawCaptionLayout = false

    @JvmField
    var isNewGroup = false

    fun reset() {
        captionEnterProgress = 1f
        offsetBottom = 0f
        offsetTop = 0f
        offsetRight = 0f
        offsetLeft = 0f
        backgroundChangeBounds = false
    }
}
