/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.tgnet.tlrpc

class Vector : TLObject() {
    @JvmField
    val objects = ArrayList<Any>()

    companion object {
        private const val CONSTRUCTOR = 0x1cb5c415
    }
}
