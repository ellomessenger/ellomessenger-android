/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Components

import android.view.animation.Interpolator

class BounceInterpolator : Interpolator {
    override fun getInterpolation(input: Float): Float {
        var t = input

        return if (t < 0.33f) {
            0.1f * (t / 0.33f)
        }
        else {
            t -= 0.33f

            if (t < 0.33f) {
                0.1f - 0.15f * (t / 0.34f)
            }
            else {
                t -= 0.34f
                -0.05f + 0.05f * (t / 0.33f)
            }
        }
    }
}
